package com.arcturus.haven.launcher.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import com.arcturus.haven.launcher.core.data.DrawerSortMode
import com.arcturus.haven.launcher.core.data.SearchStartAppHistory
import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class LaunchableApp(
    val entry: LauncherAppEntry,
    val activity: LauncherActivityInfo,
    val icon: Drawable?,
    val firstInstallTime: Long = 0L,
    val dominantIconColor: Int? = null,
) {
    val componentName: ComponentName get() = activity.componentName
}

data class SearchableShortcut(
    val shortcut: ShortcutInfo,
    val label: String,
    val appLabel: String,
    val icon: Drawable?,
)

enum class WorkProfileAppLoadState {
    LOADING,
    READY,
    NO_PROFILE,
    PROFILE_PAUSED,
    NO_APPS,
    UNSUPPORTED,
    UNAVAILABLE,
}

data class WorkProfileAppLoadResult(
    val apps: List<LaunchableApp>,
    val state: WorkProfileAppLoadState,
)

class LauncherCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)

    fun hasShortcutHostPermission(): Boolean =
        runCatching { launcherApps?.hasShortcutHostPermission() == true }.getOrDefault(false)

    fun load(): List<LaunchableApp> {
        val installTimes = ConcurrentHashMap<String, Long>()
        val drawerAccent = LauncherAppearance.resolve(appContext).accent
        val drawerShape = "circle"
        val iconDensityDpi = appContext.resources.displayMetrics.densityDpi
        val includeDominantColors = shouldSampleDominantColors()
        val rawActivities = launcherApps?.getActivityList(null, Process.myUserHandle()).orEmpty()
        val apps = mapActivitiesParallel(rawActivities) { activity ->
            val component = activity.componentName
            val packageName = component.packageName
            val label = activity.label?.toString().orEmpty().ifBlank { packageName }
            val icon = runCatching { activity.getIcon(iconDensityDpi) }.getOrNull()
            LaunchableApp(
                LauncherAppEntry(packageName, component.className, label),
                activity,
                icon,
                firstInstallTime = installTimes.getOrPut(packageName) {
                    runCatching { appContext.packageManager.getPackageInfo(packageName, 0).firstInstallTime }
                        .getOrDefault(0L)
                },
                dominantIconColor = if (includeDominantColors) sampleDominantColor(icon) else null,
            )
        }
            .distinctBy { it.entry.packageName to it.entry.className }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.label })

        // Parallel-prewarm the initial drawer viewport synchronously on workers so the very first
        // frame renders at 256x256 without stalls, then prewarm all remaining apps asynchronously.
        val generation = DrawerIconPipeline.currentGeneration()
        val initialApps = apps.take(INITIAL_DRAWER_ICON_PREWARM_COUNT)
        val prewarmLatch = CountDownLatch(initialApps.size)
        initialApps.forEach { app ->
            executor.execute {
                try {
                    app.icon?.let {
                        CircularAppIconDrawable.prewarm(
                            it,
                            "${CircularAppIconDrawable.activityCacheKey(app.componentName)}|density:$iconDensityDpi",
                            drawerAccent,
                            drawerShape,
                        )
                    }
                } finally {
                    prewarmLatch.countDown()
                }
            }
        }
        runCatching { prewarmLatch.await() }
        val remaining = apps.drop(INITIAL_DRAWER_ICON_PREWARM_COUNT)
        if (remaining.isNotEmpty()) {
            DrawerIconPipeline.prewarmAllAsync(remaining, iconDensityDpi, drawerAccent, drawerShape, generation)
        }
        return apps
    }

    private fun shouldSampleDominantColors(): Boolean =
        DrawerSortMode.fromPreference(appContext.launcherPrefs().getString(LauncherSettings.KEY_DRAWER_SORT_ORDER, null)) ==
            DrawerSortMode.ICON_COLOR

    private fun mapActivitiesParallel(
        activities: List<LauncherActivityInfo>,
        mapper: (LauncherActivityInfo) -> LaunchableApp,
    ): List<LaunchableApp> {
        if (activities.size <= 16) return activities.map(mapper)
        val results = arrayOfNulls<LaunchableApp>(activities.size)
        val nextIndex = AtomicInteger(0)
        val workersToUse = minOf(workerCount, activities.size)
        val latch = CountDownLatch(workersToUse)
        repeat(workersToUse) {
            executor.execute {
                try {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= activities.size) break
                        results[index] = mapper(activities[index])
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        return results.filterNotNull()
    }

    private fun sampleDominantColor(drawable: Drawable?): Int? {
        if (drawable == null) return null
        val size = 24
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val sampled = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        val oldBounds = Rect(sampled.bounds)
        return try {
            sampled.setBounds(0, 0, size, size)
            sampled.draw(canvas)
            val buckets = HashMap<Int, IntArray>()
            for (y in 0 until size) for (x in 0 until size) {
                val color = bitmap[x, y]
                if (Color.alpha(color) < 128) continue
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val totals = buckets.getOrPut((red shr 5 shl 6) or (green shr 5 shl 3) or (blue shr 5)) { IntArray(4) }
                totals[0] += red
                totals[1] += green
                totals[2] += blue
                totals[3] += 1
            }
            val dominant = buckets.values.maxByOrNull { it[3] } ?: return null
            Color.rgb(dominant[0] / dominant[3], dominant[1] / dominant[3], dominant[2] / dominant[3])
        } finally {
            sampled.bounds = oldBounds
            bitmap.recycle()
        }
    }

    fun loadAsync(onLoaded: (List<LaunchableApp>) -> Unit) =
        runAsync({ runCatching { load() }.getOrDefault(emptyList()) }, onLoaded)

    fun loadManagedWorkProfileAppsAsync(onLoaded: (WorkProfileAppLoadResult) -> Unit) =
        runAsync(::loadManagedWorkProfileApps, onLoaded)

    private fun loadManagedWorkProfileApps(): WorkProfileAppLoadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.UNSUPPORTED)
        }
        val service = launcherApps ?: return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.UNAVAILABLE)
        val profiles = runCatching { service.profiles }
            .getOrElse { return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.UNAVAILABLE) }
        val workProfile = profiles.firstOrNull { profile ->
            profile != Process.myUserHandle() && runCatching {
                service.getLauncherUserInfo(profile)?.userType == UserManager.USER_TYPE_PROFILE_MANAGED
            }.getOrDefault(false)
        } ?: return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.NO_PROFILE)

        val userManager = appContext.getSystemService(UserManager::class.java)
        if (runCatching { userManager?.isQuietModeEnabled(workProfile) == true }.getOrDefault(false)) {
            return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.PROFILE_PAUSED)
        }

        val activities = runCatching { service.getActivityList(null, workProfile) }
            .getOrElse { return WorkProfileAppLoadResult(emptyList(), WorkProfileAppLoadState.UNAVAILABLE) }
        val density = appContext.resources.displayMetrics.densityDpi
        val includeDominantColors = shouldSampleDominantColors()
        val apps = mapActivitiesParallel(activities) { activity ->
            val component = activity.componentName
            val label = activity.label?.toString().orEmpty().ifBlank { component.packageName }
            val icon = runCatching { activity.getBadgedIcon(density) }.recoverCatching { activity.getIcon(density) }.getOrNull()
            LaunchableApp(
                entry = LauncherAppEntry(component.packageName, component.className, label),
                activity = activity,
                icon = icon,
                firstInstallTime = activity.firstInstallTime,
                dominantIconColor = if (includeDominantColors) sampleDominantColor(icon) else null,
            )
        }
            .distinctBy { it.entry.packageName to it.entry.className }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.label })
        return WorkProfileAppLoadResult(
            apps = apps,
            state = if (apps.isEmpty()) WorkProfileAppLoadState.NO_APPS else WorkProfileAppLoadState.READY,
        )
    }

    fun start(app: LaunchableApp) {
        launcherApps?.startMainActivity(app.componentName, app.activity.user, null, null)
        val preferences = appContext.launcherPrefs()
        val history = SearchStartAppHistory.parse(preferences.getString(LauncherSettings.KEY_SEARCH_START_APP_HISTORY, "[]"))
        preferences.edit {
            putString(
                LauncherSettings.KEY_SEARCH_START_APP_HISTORY,
                JSONArray(SearchStartAppHistory.record(history, app.entry.componentKey)).toString(),
            )
        }
    }

    fun loadShortcutsAsync(app: LaunchableApp, onLoaded: (List<ShortcutInfo>) -> Unit) = runAsync({
        runCatching {
            val service = shortcutService() ?: return@runCatching emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.entry.packageName)
                .setActivity(app.componentName)
                .setQueryFlags(SHORTCUT_MATCH_FLAGS)
            service.getShortcuts(query, app.activity.user).orEmpty()
                .filter { it.isEnabled }
                .distinctBy { it.id }
                .sortedBy { it.shortLabel?.toString()?.lowercase().orEmpty() }
        }.getOrDefault(emptyList())
    }, onLoaded)

    fun loadSearchShortcutsAsync(queryText: String, onLoaded: (List<SearchableShortcut>) -> Unit) = runAsync({
        runCatching {
            val service = shortcutService() ?: return@runCatching emptyList()
            val query = LauncherApps.ShortcutQuery().setQueryFlags(SHORTCUT_MATCH_FLAGS)
            val needle = queryText.trim().lowercase()
            val self = Process.myUserHandle()
            val densityDpi = appContext.resources.displayMetrics.densityDpi
            service.getShortcuts(query, self).orEmpty()
                .filter { it.isEnabled }
                .map { shortcut ->
                    val label = shortcut.shortLabel?.toString()?.takeIf(String::isNotBlank)
                        ?: shortcut.longLabel?.toString()
                        ?: shortcut.id
                    val activity = service.getActivityList(shortcut.`package`, self).firstOrNull()
                    val appLabel = activity?.label?.toString().orEmpty().ifBlank { shortcut.`package` }
                    val icon = runCatching {
                        service.getShortcutIconDrawable(shortcut, densityDpi) ?: activity?.getIcon(0)
                    }.getOrNull()
                    SearchableShortcut(shortcut, label, appLabel, icon)
                }
                .filter { needle.isEmpty() || it.label.lowercase().contains(needle) || it.appLabel.lowercase().contains(needle) }
                .distinctBy { it.shortcut.`package` to it.shortcut.id }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }.getOrDefault(emptyList())
    }, onLoaded)

    fun start(shortcut: ShortcutInfo, sourceBounds: Rect?) {
        launcherApps?.startShortcut(shortcut, sourceBounds, null)
    }

    private fun shortcutService(): LauncherApps? = launcherApps?.takeIf { it.hasShortcutHostPermission() }

    private inline fun <T> runAsync(crossinline work: () -> T, crossinline onLoaded: (T) -> Unit) {
        executor.execute {
            val result = work()
            mainHandler.post { onLoaded(result) }
        }
    }

    private companion object {
        const val INITIAL_DRAWER_ICON_PREWARM_COUNT = 30
        const val SHORTCUT_MATCH_FLAGS =
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        private val threadIndex = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(workerCount) { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "Teres-catalog-${threadIndex.incrementAndGet()}").apply { isDaemon = true }
        }
        val mainHandler = Handler(Looper.getMainLooper())
    }
}
