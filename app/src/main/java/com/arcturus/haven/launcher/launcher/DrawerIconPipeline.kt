package com.arcturus.haven.launcher.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-threaded background rasterization and prewarming pipeline for full-resolution (256x256)
 * All Apps drawer icons. Keeps RecyclerView scroll binding free of bitmap rasterization and analysis.
 */
internal object DrawerIconPipeline {
    private val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    private val threadCounter = AtomicInteger(0)
    private val visibleWorkers = workerPool(2, Process.THREAD_PRIORITY_DISPLAY, "Teres-icon-vis")
    private val prewarmWorkers = workerPool(workerCount, Process.THREAD_PRIORITY_BACKGROUND, "Teres-icon-bg")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generationCounter = AtomicLong(1L)
    private val inFlight = ConcurrentHashMap<String, MutableList<Pair<Long, (Bitmap) -> Unit>>>()

    private fun workerPool(size: Int, priority: Int, prefix: String) = Executors.newFixedThreadPool(size) { runnable ->
        Thread({
            Process.setThreadPriority(priority)
            runnable.run()
        }, "$prefix-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
    }

    fun currentGeneration(): Long = generationCounter.get()

    fun invalidateGeneration(): Long = generationCounter.incrementAndGet()

    fun getCachedBitmap(cacheKey: String, accentColor: Int, shape: String = "circle"): Bitmap? =
        CircularAppIconDrawable.getCachedBitmap(cacheKey, accentColor, shape)

    fun enqueueRender(
        expectedGeneration: Long,
        cacheKey: String,
        accentColor: Int,
        shape: String = "circle",
        artworkProvider: () -> Drawable?,
        onReady: (Bitmap) -> Unit,
    ) {
        getCachedBitmap(cacheKey, accentColor, shape)?.let { onReady(it); return }
        val requestKey = "$cacheKey|$shape|$accentColor"
        val shouldSubmit = synchronized(inFlight) {
            val waiters = inFlight[requestKey]
            if (waiters != null) {
                waiters.add(expectedGeneration to onReady)
                false
            } else {
                inFlight[requestKey] = mutableListOf(expectedGeneration to onReady)
                true
            }
        }
        if (!shouldSubmit) return
        visibleWorkers.execute {
            val rendered = if (generationCounter.get() == expectedGeneration) {
                runCatching {
                    val artwork = artworkProvider() ?: return@runCatching null
                    CircularAppIconDrawable.renderAndCache(artwork, cacheKey, accentColor, accentColor, shape)
                }.getOrNull()
            } else null

            val waiters = synchronized(inFlight) { inFlight.remove(requestKey) }.orEmpty()
            if (rendered != null && waiters.isNotEmpty()) {
                mainHandler.post {
                    val activeGen = generationCounter.get()
                    waiters.forEach { (gen, callback) -> if (gen == activeGen) callback(rendered) }
                }
            }
        }
    }

    fun prewarmAllAsync(
        apps: List<LaunchableApp>,
        iconDensityDpi: Int,
        accent: Int,
        shape: String = "circle",
        expectedGeneration: Long = generationCounter.get(),
        packResolver: ((ComponentName) -> Drawable?)? = null,
    ) {
        if (apps.isEmpty()) return
        apps.forEach { app ->
            val component = app.componentName
            val packIcon = packResolver?.invoke(component)
            val cacheKey = "${CircularAppIconDrawable.activityCacheKey(component)}|density:$iconDensityDpi"
            if (packIcon == null && getCachedBitmap(cacheKey, accent, shape) != null) return@forEach
            val artwork = packIcon ?: app.icon ?: return@forEach
            prewarmWorkers.execute {
                if (generationCounter.get() != expectedGeneration) return@execute
                runCatching { CircularAppIconDrawable.prewarm(artwork, cacheKey, accent, shape) }
            }
        }
    }
}
