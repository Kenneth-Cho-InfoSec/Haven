package com.arcturus.haven.launcher.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.WallpaperManager
import android.accessibilityservice.AccessibilityService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.util.SizeF
import android.util.LruCache
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arcturus.haven.R
import com.arcturus.haven.core.HavenModePreferences
import com.arcturus.haven.launcher.core.data.LauncherSearch
import com.arcturus.haven.launcher.core.data.DrawerCategories
import com.arcturus.haven.launcher.core.data.DrawerCategoryEntry
import com.arcturus.haven.launcher.core.data.WeatherRepository
import com.arcturus.haven.launcher.core.data.WorkspacePlacement
import com.arcturus.haven.launcher.core.data.WorkspaceGridSpec
import com.arcturus.haven.launcher.core.data.WorkspacePageOrder
import com.arcturus.haven.launcher.core.data.WorkspaceRepository
import com.arcturus.haven.launcher.core.data.DrawerOrderEntry
import com.arcturus.haven.launcher.core.data.DrawerOrdering
import com.arcturus.haven.launcher.core.data.DrawerSortMode
import com.arcturus.haven.launcher.core.data.WidgetGridPlacement
import com.arcturus.haven.launcher.core.data.WidgetGridSpan
import com.arcturus.haven.launcher.core.data.WidgetResizeBounds
import com.arcturus.haven.launcher.core.data.WidgetResizeRules
import com.arcturus.haven.launcher.core.data.WallpaperOffsets
import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import com.arcturus.haven.launcher.core.model.DrawerLaunchView
import com.arcturus.haven.launcher.core.model.WorkspaceIconGesture
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAction
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAssignment
import com.arcturus.haven.launcher.core.model.WorkspaceGestureClassifier
import com.arcturus.haven.launcher.core.model.WorkspaceIconSource
import com.arcturus.haven.launcher.core.model.WorkspaceItem
import com.arcturus.haven.launcher.core.model.WorkspaceLayout
import com.arcturus.haven.launcher.core.model.WorkspaceSurfaceGesture
import com.arcturus.haven.launcher.core.model.WorkspaceGestureRouting
import com.arcturus.haven.launcher.core.model.WorkspaceShortcut
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetSize
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetStack
import com.arcturus.haven.launcher.core.model.WorkspaceWidgetStackOrientation
import com.arcturus.haven.launcher.core.model.doubleTapBadgeAssignment
import com.arcturus.haven.launcher.core.model.gestureSummary
import com.arcturus.haven.launcher.core.model.withGestureAssignment
import com.arcturus.haven.launcher.core.permissions.LauncherActionAccessibilityService
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.settings.drawerCellHeightDp
import com.arcturus.haven.launcher.core.settings.drawerColumns
import com.arcturus.haven.launcher.core.settings.drawerIconSize
import com.arcturus.haven.launcher.core.settings.drawerRowSpacingDp
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import com.arcturus.haven.launcher.core.settings.workspacePageCount
import com.arcturus.haven.launcher.core.ui.chooseClockPosition
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.showFullscreen
import com.arcturus.haven.launcher.core.ui.toast
import com.arcturus.haven.launcher.settings.LauncherSettingsActivity
import com.arcturus.haven.launcher.settings.SettingsGlyphView
import com.arcturus.haven.launcher.search.LauncherSearchActivity
import com.arcturus.haven.launcher.widgets.WidgetHostController
import com.arcturus.haven.launcher.widgets.IconGroupWidgetProvider
import com.arcturus.haven.launcher.widgets.WidgetPickerActivity
import com.arcturus.haven.launcher.widgets.BatteryWidgetProvider
import com.arcturus.haven.launcher.widgets.VaultControlWidgetProvider
import com.arcturus.haven.launcher.bridge.HavenProfileSession
import com.arcturus.haven.launcher.bridge.VaultIconCache
import com.arcturus.haven.launcher.bridge.VaultProfileState
import com.arcturus.haven.services.IAppInstallCallback
import com.arcturus.haven.services.IGetAppsCallback
import com.arcturus.haven.services.IHavenService
import com.arcturus.haven.services.ILoadIconCallback
import com.arcturus.haven.services.HavenService
import com.arcturus.haven.ui.DummyActivity
import com.arcturus.haven.ui.MainActivity
import com.arcturus.haven.ui.ManagedRestrictionsDialog
import com.arcturus.haven.ui.SetupWizardActivity
import com.arcturus.haven.ui.WelcomeModeActivity
import com.arcturus.haven.util.ApplicationInfoWrapper
import com.arcturus.haven.util.LocalStorageManager
import com.arcturus.haven.util.SettingsManager
import com.arcturus.haven.util.UriForwardProxy
import com.arcturus.haven.util.Utility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.floor

class HomeActivity : ComponentActivity() {
    private data class DrawerRenderConfig(
        val iconSizePx: Int = 0,
        val cellHeightPx: Int = 0,
        val labelsVisible: Boolean = true,
        val twoLineLabels: Boolean = true,
        val accentColor: Int = Color.WHITE,
        val iconPack: String = IconPackRepository.DEFAULT_PACK,
        val highlightNotifications: Boolean = true,
        val densityDpi: Int = 0,
        val labelTypeface: Typeface = Typeface.DEFAULT,
        val iconGeneration: Long = 0L,
    )

    private sealed interface DrawerListItem {
        val stableKey: String
        val label: String
        val iconToken: String

        data class AppItem(
            val app: LaunchableApp,
            override val stableKey: String,
            override val label: String,
            override val iconToken: String,
        ) : DrawerListItem

        data class VaultItem(
            val vault: VaultAppItem,
            override val stableKey: String,
            override val label: String,
            override val iconToken: String,
        ) : DrawerListItem
    }

    private class DrawerAppViewHolder(
        val cell: LinearLayout,
        val iconContainer: FrameLayout,
        val icon: ImageView,
        val frostBadge: TextView,
        val label: TextView,
        var boundApp: LaunchableApp? = null,
        var boundVaultItem: VaultAppItem? = null,
        var boundIconKey: String? = null,
        var appliedIconSizePx: Int = -1,
        var appliedCellHeightPx: Int = -1,
        var appliedTwoLine: Boolean? = null,
        var appliedTypeface: Typeface? = null,
    ) : RecyclerView.ViewHolder(cell)

    private class DrawerGridSpacingDecoration(private var verticalSpacingPx: Int) : RecyclerView.ItemDecoration() {
        fun updateSpacing(spacingPx: Int): Boolean {
            if (verticalSpacingPx == spacingPx) return false
            verticalSpacingPx = spacingPx
            return true
        }

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: 1
            val position = parent.getChildAdapterPosition(view)
            outRect.top = if (position >= spanCount) verticalSpacingPx else 0
            outRect.bottom = 0
            outRect.left = 0
            outRect.right = 0
        }
    }

    private val drawerStableIdMap = HashMap<String, Long>(128)
    private var nextDrawerStableId = 1L
    private fun drawerStableId(key: String): Long =
        drawerStableIdMap.getOrPut(key) { nextDrawerStableId++ }

    private inner class DrawerGridAdapter : ListAdapter<DrawerListItem, DrawerAppViewHolder>(
        object : DiffUtil.ItemCallback<DrawerListItem>() {
            override fun areItemsTheSame(oldItem: DrawerListItem, newItem: DrawerListItem): Boolean =
                oldItem.stableKey == newItem.stableKey

            override fun areContentsTheSame(oldItem: DrawerListItem, newItem: DrawerListItem): Boolean =
                oldItem.label == newItem.label && oldItem.iconToken == newItem.iconToken
        },
    ) {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = drawerStableId(getItem(position).stableKey)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerAppViewHolder {
            val cfg = drawerRenderConfig
            val cell = createDrawerAppCell(cfg)
            cell.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cfg.cellHeightPx,
            )
            val iconContainer = cell.getChildAt(0) as FrameLayout
            val holder = DrawerAppViewHolder(
                cell = cell,
                iconContainer = iconContainer,
                icon = iconContainer.getChildAt(0) as ImageView,
                frostBadge = iconContainer.getChildAt(1) as TextView,
                label = cell.getChildAt(1) as TextView,
                appliedIconSizePx = cfg.iconSizePx,
                appliedCellHeightPx = cfg.cellHeightPx,
                appliedTwoLine = cfg.twoLineLabels,
                appliedTypeface = cfg.labelTypeface,
            )
            cell.setOnClickListener {
                val vault = holder.boundVaultItem
                if (vault != null) {
                    launchVaultItem(vault)
                } else {
                    holder.boundApp?.let(::launchApp)
                }
            }
            cell.setOnLongClickListener { view ->
                val vault = holder.boundVaultItem
                if (vault != null) {
                    showVaultAppMenu(vault, view)
                    true
                } else {
                    val boundApp = holder.boundApp ?: return@setOnLongClickListener false
                    if (drawerReorderMode) {
                        val payload = DrawerDragPayload(boundApp.entry.componentKey)
                        view.startDragAndDrop(
                            ClipData.newPlainText("Haven app", boundApp.entry.componentKey),
                            View.DragShadowBuilder(view),
                            payload,
                            0,
                        )
                    } else {
                        showDrawerAppMenu(boundApp, view)
                    }
                    true
                }
            }
            return holder
        }

        override fun onBindViewHolder(holder: DrawerAppViewHolder, position: Int) {
            val cfg = drawerRenderConfig
            if (holder.appliedIconSizePx != cfg.iconSizePx) {
                holder.iconContainer.layoutParams = holder.iconContainer.layoutParams.apply {
                    width = cfg.iconSizePx
                    height = cfg.iconSizePx
                }
                holder.appliedIconSizePx = cfg.iconSizePx
            }
            if (holder.appliedCellHeightPx != cfg.cellHeightPx) {
                holder.cell.minimumHeight = cfg.cellHeightPx
                holder.cell.layoutParams = holder.cell.layoutParams.apply { height = cfg.cellHeightPx }
                holder.appliedCellHeightPx = cfg.cellHeightPx
            }
            if (holder.appliedTwoLine != cfg.twoLineLabels) {
                holder.label.maxLines = if (cfg.twoLineLabels) 2 else 1
                holder.appliedTwoLine = cfg.twoLineLabels
            }
            if (holder.appliedTypeface !== cfg.labelTypeface) {
                holder.label.typeface = cfg.labelTypeface
                holder.appliedTypeface = cfg.labelTypeface
            }

            when (val entry = getItem(position)) {
                is DrawerListItem.VaultItem -> {
                    val item = entry.vault
                    holder.boundApp = item.launchableApp
                    holder.boundVaultItem = item
                    bindDrawerVaultIcon(holder, item, cfg)
                    if (item.isFrozen) {
                        holder.icon.colorFilter = frozenColorFilter
                        holder.icon.alpha = 0.62f
                        holder.frostBadge.visibility = View.VISIBLE
                        holder.frostBadge.text = "❄"
                        holder.frostBadge.background = sharedFrozenBadgeBackground()
                    } else if (item.isAutoFreeze) {
                        holder.icon.colorFilter = null
                        holder.icon.alpha = 1f
                        holder.frostBadge.visibility = View.VISIBLE
                        holder.frostBadge.text = "⏱"
                        holder.frostBadge.background = sharedAutoFreezeBadgeBackground(cfg.accentColor)
                    } else {
                        holder.icon.colorFilter = null
                        holder.icon.alpha = 1f
                        holder.frostBadge.visibility = View.GONE
                    }
                    holder.icon.contentDescription = if (item.isFrozen) "${item.label} (Frozen)" else item.label
                    holder.label.text = item.label
                    holder.label.visibility = if (cfg.labelsVisible) View.VISIBLE else View.GONE
                    holder.cell.contentDescription = if (item.isFrozen) "${item.label}, frozen" else item.label
                }
                is DrawerListItem.AppItem -> {
                    val app = entry.app
                    holder.boundApp = app
                    holder.boundVaultItem = null
                    holder.icon.colorFilter = null
                    holder.icon.alpha = 1f
                    holder.frostBadge.visibility = View.GONE
                    bindDrawerPersonalIcon(holder, app, cfg)
                    holder.icon.contentDescription = app.entry.label
                    holder.label.text = app.entry.label
                    holder.label.visibility = if (cfg.labelsVisible) View.VISIBLE else View.GONE
                    holder.cell.contentDescription = app.entry.label
                }
            }
        }

        override fun onViewRecycled(holder: DrawerAppViewHolder) {
            holder.boundIconKey = null
            super.onViewRecycled(holder)
        }
    }

    private data class VaultAppItem(
        val packageName: String,
        val className: String?,
        val label: String,
        val icon: Drawable?,
        val isFrozen: Boolean,
        val isAutoFreeze: Boolean,
        val isSystem: Boolean,
        val launchableApp: LaunchableApp? = null,
        val wrapper: ApplicationInfoWrapper? = null,
    )

    private val prefs by lazy { launcherPrefs() }
    private val catalog by lazy { LauncherCatalog(this) }
    private val workspaceStore by lazy { WorkspaceRepository(this) }
    private val widgetHost by lazy { WidgetHostController(this) }
    private val weatherRepository by lazy { WeatherRepository(this) }
    private val iconPackRepository by lazy { IconPackRepository(this) }

    private lateinit var root: GestureFrameLayout
    private lateinit var homeLayer: LinearLayout
    private lateinit var homeGrid: GridLayout
    private lateinit var scrollViewport: FrameLayout
    private lateinit var workspaceScroll: ScrollView
    private lateinit var widgetColumn: FrameLayout
    private lateinit var homeDock: LinearLayout
    private lateinit var feedSideButton: FrameLayout
    private lateinit var batteryTile: TextView
    private lateinit var clockHeader: LinearLayout
    private lateinit var searchButton: TextView
    private lateinit var clockView: TextClock
    private lateinit var dateView: TextView
    private lateinit var drawerLayer: LinearLayout
    private lateinit var drawerGrid: RecyclerView
    private lateinit var drawerLayoutManager: GridLayoutManager
    private lateinit var drawerSpacingDecoration: DrawerGridSpacingDecoration
    private lateinit var drawerIndexGrid: GridLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerModeSwitcher: LinearLayout
    private lateinit var personalTabButton: TextView
    private lateinit var vaultTabButton: TextView
    private lateinit var vaultHeaderPanel: LinearLayout
    private lateinit var vaultStatusDot: View
    private lateinit var vaultStatusTitle: TextView
    private lateinit var vaultStatusSubtitle: TextView
    private lateinit var vaultStatusAction: TextView
    private lateinit var vaultActionScroll: HorizontalScrollView
    private lateinit var vaultActionRow: LinearLayout
    private lateinit var drawerCategoryScroll: HorizontalScrollView
    private lateinit var drawerCategories: LinearLayout
    private lateinit var drawerEmpty: TextView
    private lateinit var drawerTitle: TextView
    private lateinit var drawerSearchButton: View
    private lateinit var drawerStoreButton: View
    private lateinit var pageIndicator: TextView
    private lateinit var weatherContainer: LinearLayout
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherValue: TextView
    private var weatherIsDaytime = false
    private var widgetSelectionFrame: View? = null
    private lateinit var drawerAdapter: DrawerGridAdapter
    private val drawerIconDrawableCache = LruCache<String, android.graphics.drawable.Drawable>(256)
    private var drawerIconGeneration = 0L
    private var drawerAdapterRenderSignature: String? = null
    private var drawerRenderConfig = DrawerRenderConfig()
    private val frozenColorFilter by lazy { ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
    private var cachedFrozenBadgeBg: GradientDrawable? = null
    private var cachedAutoFreezeBadgeBg: GradientDrawable? = null
    private var cachedAutoFreezeAccent: Int = Int.MIN_VALUE
    private var cachedNotificationDotDrawable: GradientDrawable? = null
    private var apps: List<LaunchableApp> = emptyList()
    private var workProfileApps: List<LaunchableApp> = emptyList()
    private var vaultItems: List<VaultAppItem> = emptyList()
    private var visibleVaultItems: List<VaultAppItem> = emptyList()
    private var visibleDrawerApps: List<LaunchableApp> = emptyList()
    private val vaultIconCache by lazy { VaultIconCache(this) }
    private val vaultIconBitmaps = mutableMapOf<String, Drawable>()
    private var vaultCrossProfileWidgets = mutableSetOf<String>()
    private var vaultCrossProfilePackages = mutableSetOf<String>()
    private var vaultSessionAcquired = false
    private var pendingInstallApkIntoVault = false
    private var hiddenAppComponents: Set<String> = emptySet()
    private var activeNotificationPackages: Set<String> = emptySet()
    private var layout = WorkspaceLayout()
    private var currentPage = 0
    private var drawerOpen = false
    private var drawerHasBeenOpened = false
    private var drawerShowingWorkAppsPage = false
    private var workProfileAppsState = WorkProfileAppLoadState.NO_PROFILE
    private var workProfileAppsLoadVersion = 0
    private var drawerSearchVisible = false
    private var drawerIndexMode = false
    private var drawerIndexFilter: String? = null
    private var drawerLabelsVisible = true
    private var drawerIndexLetters: List<String> = emptyList()
    private var renderedDrawerIndexLetters: List<String> = emptyList()
    private var drawerReorderMode = false
    private var activeFolderDialogCount = 0
    private var selectedDrawerCategory = DrawerCategories.ALL
    private var appRefreshVersion = 0
    private var appCatalogLoaded = false
    private var appCatalogRefreshInFlight = false
    private var appCatalogRefreshQueued = false
    private var appCatalogPackageSequence: Int? = null
    private var palette: LauncherPalette? = null
    private var pendingWidgetId: Int? = null
    private var pendingIconGalleryItem: WorkspaceItem? = null
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingWidgetStackId: Int? = null
    private var editingWidgetId: Int? = null
    private var workspaceGestureEditor: WorkspaceGestureEditorDialog? = null
    private val widgetStackViews = mutableMapOf<Int, WidgetStackSwitcher>()
    private val widgetStackRotationHandler = Handler(Looper.getMainLooper())
    private val widgetStackRotationCallbacks = mutableMapOf<Int, Runnable>()
    private val importedIconCache = object : LruCache<String, Bitmap>(2 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val gridColumns: Int
        get() = WorkspaceGridSpec.columns(prefs.getInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.COLUMNS))
    private val gridRows: Int
        get() = WorkspaceGridSpec.ROWS
    private val gridColumnSpacingDp: Int
        get() = prefs.getInt(LauncherSettings.KEY_HOME_COLUMN_SPACING, LauncherSettings.HOME_COLUMN_SPACING_DEFAULT_DP).coerceIn(0, 32)
    private val wallpaperManager by lazy { WallpaperManager.getInstance(this) }
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val wallpaperColorsListener = WallpaperManager.OnColorsChangedListener { _, _ -> refreshAppearance() }
    private var wallpaperTiltX = 0f
    private var lastWallpaperMotionUpdateAt = 0L
    private val wallpaperTiltListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            if (!prefs.getBoolean(LauncherSettings.KEY_WALLPAPER_PARALLAX, false)) return
            val horizontalTilt = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-0.6f, 0.6f)
            wallpaperTiltX = wallpaperTiltX * 0.82f + horizontalTilt * 0.18f
            val now = SystemClock.uptimeMillis()
            if (now - lastWallpaperMotionUpdateAt < WALLPAPER_MOTION_FRAME_MS) return
            lastWallpaperMotionUpdateAt = now
            updateWallpaperOffset(workspacePageCount())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshNotificationHighlights()
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryTile(intent)
            context?.let { BatteryWidgetProvider.updateAll(it) }
        }
    }

    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widgetId = pendingWidgetId ?: return@registerForActivityResult
        val info = pendingWidgetInfo ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) configureOrAddWidget(widgetId, info)
        else abandonWidget(widgetId)
    }

    private val widgetConfigureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widgetId = pendingWidgetId ?: return@registerForActivityResult
        val info = pendingWidgetInfo ?: return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) addWidget(widgetId, info) else abandonWidget(widgetId)
    }

    private val widgetEditConfigureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        rebuildWidgetViews()
    }

    private val widgetPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            pendingWidgetStackId = null
            return@registerForActivityResult
        }
        if (data.getBooleanExtra(WidgetPickerActivity.EXTRA_CREATE_STACK, false)) {
            pendingWidgetStackId = null
            createEmptyWidgetStack()
            return@registerForActivityResult
        }
        val provider = data.getStringExtra(WidgetPickerActivity.EXTRA_PROVIDER)?.let(ComponentName::unflattenFromString)
        val providerUser = IntentCompat.getParcelableExtra(data, WidgetPickerActivity.EXTRA_PROVIDER_USER, UserHandle::class.java)
            ?: Process.myUserHandle()
        val info = provider?.let { component ->
            widgetHost.providers().firstOrNull { it.provider == component && it.profile == providerUser }
                ?: widgetHost.providers().firstOrNull { it.provider == component }
        }
        if (info == null) {
            pendingWidgetStackId = null
            toast("That widget is no longer available")
        } else beginAddWidget(info, pendingWidgetStackId)
    }

    private val tryStartWorkServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        HavenProfileSession.onTryStartResult(result)
    }

    private val bindWorkServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        HavenProfileSession.onBindWorkResult(result)
    }

    private val setupWizardLauncher = registerForActivityResult(SetupWizardActivity.SetupWizardContract()) { succeeded ->
        if (succeeded) {
            VaultControlWidgetProvider.updateAll(this)
            openDrawerToVault()
        } else {
            if (drawerShowingWorkAppsPage) renderVaultHeader()
        }
    }

    private val installApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !pendingInstallApkIntoVault) {
            pendingInstallApkIntoVault = false
            return@registerForActivityResult
        }
        pendingInstallApkIntoVault = false
        installApkUriIntoVault(uri)
    }

    private val galleryIconLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val item = pendingIconGalleryItem
        pendingIconGalleryItem = null
        if (uri != null && item != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            updateWorkspaceItem(item) {
                it.copy(
                    iconSource = WorkspaceIconSource.GALLERY.storageKey,
                    iconUri = uri.toString(),
                    iconPackPackageName = null,
                )
            }
        }
    }

    private val weatherPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            prefs.edit { putBoolean(LauncherSettings.KEY_WEATHER_ENABLED, true) }
            refreshWeather()
        } else {
            weatherValue.text = getString(R.string.home_weather_prompt)
            toast("Allow approximate location to show local weather")
        }
    }

    private val launcherCallback = object : android.content.pm.LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshAppsAfterPackageChange()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshAppsAfterPackageChange()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshAppsAfterPackageChange()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshAppsAfterPackageChange()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshAppsAfterPackageChange()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = refreshAppsAfterPackageChange()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = refreshAppsAfterPackageChange()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Utility.isProfileOwner(this)) {
            finish()
            return
        }
        if (!HavenModePreferences.isWelcomeCompleted(this)) {
            startActivity(Intent(this, WelcomeModeActivity::class.java))
            finish()
            return
        }
        if (!HavenModePreferences.isLauncherEnabled(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        migrateHomeIconSizePreference()
        palette = LauncherAppearance.resolve(this)
        applyPaletteTheme(currentPalette())
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        layout = workspaceStore.load()
        migrateWorkspaceToFixedIconGrid()
        buildInterface()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editingWidgetId != null) {
                    editingWidgetId = null
                    rebuildWidgetViews()
                } else if (drawerOpen && drawerShowingWorkAppsPage) {
                    showAllAppsPage()
                } else if (drawerOpen && drawerIndexFilter != null && drawerIndexSearchEnabled()) {
                    setDrawerIndexState(true, null)
                    renderDrawerCategories()
                    renderDrawerApps("")
                } else if (drawerOpen) closeDrawer() else moveToPage(0)
            }
        })
        root.post { handleLaunchIntent(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun migrateHomeIconSizePreference() {
        if (prefs.getInt(LauncherSettings.KEY_HOME_ICON_SIZE_MIGRATION_VERSION, 0) >= LauncherSettings.HOME_ICON_SIZE_MIGRATION_VERSION) return
        val hasStoredSize = prefs.contains(LauncherSettings.KEY_HOME_ICON_SIZE)
        val storedSize = prefs.getInt(LauncherSettings.KEY_HOME_ICON_SIZE, LauncherSettings.HOME_ICON_SIZE_LEGACY_DEFAULT_DP)
        prefs.edit {
            if (!hasStoredSize || storedSize == LauncherSettings.HOME_ICON_SIZE_LEGACY_DEFAULT_DP) {
                putInt(LauncherSettings.KEY_HOME_ICON_SIZE, LauncherSettings.HOME_ICON_SIZE_DEFAULT_DP)
            }
            putInt(LauncherSettings.KEY_HOME_ICON_SIZE_MIGRATION_VERSION, LauncherSettings.HOME_ICON_SIZE_MIGRATION_VERSION)
        }
    }

    private fun migrateWorkspaceToFixedIconGrid() {
        val legacyGridChanged = prefs.getInt(LauncherSettings.KEY_GRID_COLUMNS, gridColumns) != gridColumns ||
            prefs.getInt(LauncherSettings.KEY_GRID_ROWS, gridRows) != gridRows
        val capacity = gridColumns * gridRows
        val hasOutOfRangeCells = layout.items.any { it.cell !in 0 until capacity }
        if (legacyGridChanged || hasOutOfRangeCells) {
            layout = WorkspacePlacement.reflow(layout, gridColumns, gridRows)
            workspaceStore.save(layout)
        }
        val gridLayoutVersion = prefs.getInt(LauncherSettings.KEY_HOME_GRID_LAYOUT_VERSION, 1)
        prefs.edit {
            if (gridLayoutVersion < LauncherSettings.HOME_GRID_LAYOUT_VERSION) {
                val migration = WorkspacePlacement.migrateForDockClearance(
                    layout = layout,
                    pageCount = prefs.workspacePageCount(layout),
                    columns = gridColumns,
                    rows = gridRows,
                    maxPages = LauncherSettings.MAX_WORKSPACE_PAGES,
                )
                if (migration.layout != layout) {
                    layout = migration.layout
                    workspaceStore.save(layout)
                }
                putInt(LauncherSettings.KEY_HOME_GRID_LAYOUT_VERSION, LauncherSettings.HOME_GRID_LAYOUT_VERSION)
                putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, migration.pageCount)
            }
            putInt(LauncherSettings.KEY_GRID_COLUMNS, gridColumns)
            putInt(LauncherSettings.KEY_GRID_ROWS, WorkspaceGridSpec.ROWS)
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService(android.content.pm.LauncherApps::class.java)?.registerCallback(launcherCallback)
        ContextCompat.registerReceiver(
            this,
            notificationReceiver,
            IntentFilter(NotificationHighlightService.ACTION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        wallpaperManager.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper()))
    }

    override fun onResume() {
        super.onResume()
        if (!HavenModePreferences.isLauncherEnabled(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val isolationEnabled = isIsolationEnabled()
        if (isolationEnabled) {
            HavenProfileSession.registerHost(this, tryStartWorkServiceLauncher, bindWorkServiceLauncher)
        } else {
            HavenProfileSession.unregisterHost(this)
            if (drawerShowingWorkAppsPage) showAllAppsPage(render = false)
        }
        LauncherTypography.applyTheme(this)
        applyHomeSystemBarsPreference()
        if (::homeLayer.isInitialized) {
            layout = workspaceStore.load()
            val resetStacks = layout.widgetStacks.map { stack ->
                if (stack.resetOnReturn) stack.copy(currentIndex = 0) else stack
            }
            if (resetStacks != layout.widgetStacks) {
                layout = layout.copy(widgetStacks = resetStacks)
                workspaceStore.save(layout)
            }
            migrateWorkspaceToFixedIconGrid()
            val requestedPage = prefs.getInt(LauncherSettings.KEY_OPEN_PAGE_INDEX, -1)
            currentPage = if (requestedPage >= 0) {
                prefs.edit { remove(LauncherSettings.KEY_OPEN_PAGE_INDEX) }
                requestedPage.coerceIn(0, workspacePageCount() - 1)
            } else currentPage.coerceAtMost(workspacePageCount() - 1)
            applyWallpaperDim()
            if (::homeDock.isInitialized) renderHomeDock()
            if (::searchButton.isInitialized) applyHomeSearchVisibility()
            if (::feedSideButton.isInitialized) renderFeedSideButton()
            updateDrawerModeSwitcher()
            rebuildWorkspace()
            if (::root.isInitialized) LauncherTypography.applyToViewTree(this, root)
        }
        refreshNotificationHighlights()
        IconGroupWidgetProvider.updateAll(this)
        BatteryWidgetProvider.updateAll(this)
        if (isolationEnabled) VaultControlWidgetProvider.updateAll(this)
        refreshAppearance()
        updateDrawerHeaderActions()
        refreshAppsIfPackageStateChanged()
        if (drawerOpen && drawerShowingWorkAppsPage && isolationEnabled) {
            loadWorkProfileApps()
        }
        if (::weatherContainer.isInitialized) {
            val enabled = prefs.getBoolean(LauncherSettings.KEY_WEATHER_ENABLED, true)
            applyClockPreferences()
            applyBatteryPreferences()
            if (enabled && prefs.getBoolean(LauncherSettings.KEY_CLOCK_VISIBLE, false) &&
                prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_WEATHER, true) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) refreshWeather()
        }
        updateWallpaperParallaxListener()
        updateWallpaperOffset(workspacePageCount())
        scheduleWidgetStackRotations()
    }

    override fun onPause() {
        widgetStackRotationCallbacks.values.forEach(widgetStackRotationHandler::removeCallbacks)
        widgetStackRotationCallbacks.clear()
        super.onPause()
    }

    private fun scheduleWidgetStackRotations() {
        widgetStackRotationCallbacks.values.forEach(widgetStackRotationHandler::removeCallbacks)
        widgetStackRotationCallbacks.clear()
        layout.widgetStacks.filter { it.autoRotate && it.widgetIds.size > 1 }.forEach { stack ->
            val callback = object : Runnable {
                override fun run() {
                    val current = layout.widgetStacks.firstOrNull { it.stackId == stack.stackId }
                        ?.takeIf { it.autoRotate && it.widgetIds.size > 1 }
                        ?: return
                    val nextIndex = (current.currentIndex + 1) % current.widgetIds.size
                    layout = layout.copy(widgetStacks = layout.widgetStacks.map {
                        if (it.stackId == current.stackId) it.copy(currentIndex = nextIndex) else it
                    })
                    workspaceStore.save(layout)
                    widgetStackViews[current.stackId]?.showIndex(nextIndex, animate = true)
                    widgetStackRotationHandler.postDelayed(this, current.rotateIntervalSeconds * 1_000L)
                }
            }
            widgetStackRotationCallbacks[stack.stackId] = callback
            widgetStackRotationHandler.postDelayed(callback, stack.rotateIntervalSeconds * 1_000L)
        }
    }

    override fun onStop() {
        sensorManager.unregisterListener(wallpaperTiltListener)
        wallpaperTiltX = 0f
        lastWallpaperMotionUpdateAt = 0L
        getSystemService(android.content.pm.LauncherApps::class.java)?.unregisterCallback(launcherCallback)
        runCatching { unregisterReceiver(notificationReceiver) }
        runCatching { unregisterReceiver(batteryReceiver) }
        wallpaperManager.removeOnColorsChangedListener(wallpaperColorsListener)
        widgetHost.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        if (vaultSessionAcquired) {
            vaultSessionAcquired = false
            HavenProfileSession.release("vault_drawer")
        }
        HavenProfileSession.unregisterHost(this)
        widgetHost.stopListening()
        super.onDestroy()
    }

    private fun buildInterface() {
        root = GestureFrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            onDoubleTap = { x, y -> handleWorkspaceDoubleTap(x, y) }
            onLongPress = { x, y ->
                if (!drawerOpen && !isOccupiedWorkspacePoint(x, y)) openLauncherMenu()
            }
            onSwipe = ::performWorkspaceSwipe
            shouldTrackSwipe = { x, y -> drawerOpen || !isOccupiedWorkspacePoint(x, y) }
            shouldPrioritizeSwipeOverScrollable = { x, y -> !drawerOpen && isPointInsideHomeGrid(x, y) }
            onMultiGesture = ::performWorkspaceGesture
            shouldTrackMultiGesture = { x1, y1, x2, y2 ->
                !drawerOpen && !isOccupiedWorkspacePoint(x1, y1) && !isOccupiedWorkspacePoint(x2, y2)
            }
        }
        homeLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(28), dp(116), dp(28), dp(36))
        }
        applyWallpaperDim()
        root.addView(homeLayer, FrameLayout.LayoutParams(-1, -1))

        clockHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(18), 0, 0)
            setOnLongClickListener { showClockWidgetMenu(clockHeader); true }
        }
        clockView = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 66f
            letterSpacing = 0.015f
            gravity = Gravity.CENTER
            contentDescription = "Current time"
            translationY = -dp(13).toFloat()
            setOnLongClickListener { showClockWidgetMenu(clockHeader); true }
        }
        dateView = TextView(this).apply {
            text = SimpleDateFormat("EEE, MMMM d", Locale.getDefault()).format(Date())
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 19.5f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setOnLongClickListener { showClockWidgetMenu(clockHeader); true }
        }
        val dateAndWeather = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        dateAndWeather.addView(dateView, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
        dateAndWeather.translationY = -dp(48).toFloat()
        weatherContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = "Current local weather. Tap to load forecast. Weather data by Open-Meteo."
            isFocusable = true
            background = selectableBackground()
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setOnClickListener { enableWeatherFromUserAction() }
            setOnLongClickListener { showClockWidgetMenu(clockHeader); true }
        }
        weatherIcon = ImageView(this).apply {
            setImageResource(com.arcturus.haven.R.drawable.ic_weather_moon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Weather conditions"
        }
        weatherContainer.addView(weatherIcon, LinearLayout.LayoutParams(dp(26), dp(24)).apply { marginEnd = dp(4) })
        weatherValue = TextView(this).apply {
            text = getString(R.string.home_weather_prompt)
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        weatherContainer.addView(weatherValue, LinearLayout.LayoutParams(-2, -2))
        dateAndWeather.addView(weatherContainer, LinearLayout.LayoutParams(-2, -2))
        clockHeader.addView(clockView, LinearLayout.LayoutParams(-1, -2))
        clockHeader.addView(dateAndWeather, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(4) })
        homeLayer.addView(clockHeader)

        batteryTile = TextView(this).apply {
            text = "—%"
            textSize = 24f
            letterSpacing = 0.05f
            setTypeface(LauncherTypography.typeface(this@HomeActivity, 700))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            contentDescription = "Battery level"
            isFocusable = true
            background = LayerDrawable(arrayOf(roundedDrawable(0xBB55596E.toInt(), dp(30)), selectableBackground()))
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    .onFailure { toast("Android Settings could not be opened") }
            }
            setOnLongClickListener { showBatteryWidgetMenu(this); true }
        }
        homeLayer.addView(batteryTile, LinearLayout.LayoutParams(dp(160), dp(64)).apply {
            topMargin = dp(45)
        })
        applyClockPreferences()
        applyBatteryPreferences()
        updateBatteryTile()

        val widgetScrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        homeGrid = GridLayout(this).apply {
            columnCount = gridColumns
            rowCount = gridRows
            isScrollContainer = false
            overScrollMode = View.OVER_SCROLL_NEVER
            useDefaultMargins = false
            clipChildren = false
            clipToPadding = false
        }
        widgetColumn = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(14), 0, dp(14), 0)
        }
        widgetScrollContent.addView(widgetColumn, LinearLayout.LayoutParams(-1, -2))
        workspaceScroll = WorkspaceWidgetScrollView(this).apply {
            // The icon canvas is a fixed 4 × 6 surface. Let blank cells pass gestures through
            // instead of allowing the widget list underneath to scroll from that area.
            shouldYieldToWorkspaceArea = ::isInsideHomeGridScreenPoint
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(widgetScrollContent)
        }
        scrollViewport = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
        }
        scrollViewport.addView(
            workspaceScroll,
            FrameLayout.LayoutParams(-1, 0, Gravity.TOP)
                .apply { topMargin = dp(APP_GRID_TOP_SPACER_DP) },
        )
        homeLayer.addView(scrollViewport, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        scrollViewport.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                if (bottom > top) scrollViewport.post {
                    if (!isFinishing && !isDestroyed) {
                        rebuildWorkspace()
                        renderHomeDock()
                    }
                }
            }
        }

        // Keep the six-row app canvas fixed over the wallpaper. Smart places the icon rows
        // relative to the dock, so they must not be compressed into the remaining widget viewport.
        root.addView(
            homeGrid,
            0,
            FrameLayout.LayoutParams(
                resources.displayMetrics.widthPixels - dp(28),
                dp(gridRows * HOME_GRID_ROW_HEIGHT_DP),
                Gravity.TOP or Gravity.START,
            ).apply { leftMargin = dp(28) },
        )

        pageIndicator = TextView(this).apply {
            setTextColor(0xDDFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            contentDescription = "Workspace page"
        }
        root.addView(
            pageIndicator,
            FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM).apply { bottomMargin = dp(18) },
        )

        homeDock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        homeDock.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                root.post {
                    if (!isFinishing && !isDestroyed && ::homeGrid.isInitialized) positionWorkspaceSections()
                }
            }
        }
        homeLayer.addView(
            homeDock,
            LinearLayout.LayoutParams(
                resources.displayMetrics.widthPixels - dp(28),
                dp(maxOf(HOME_DOCK_MIN_HEIGHT_DP, homeIconSizeDp())),
            ).apply {
                topMargin = dp(7)
            },
        )
        homeDock.translationX = -dp(14).toFloat()
        // Place the dock row on Smart's measured baseline while keeping the search pill anchored.
        homeDock.translationY = -dp(8).toFloat()
        homeDock.visibility = if (prefs
                .getBoolean(LauncherSettings.KEY_DOCK_VISIBLE, true)) View.VISIBLE else View.GONE

        searchButton = TextView(this).apply {
            text = android.text.SpannableString("Haven search").apply {
                setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0,
                    5,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Haven Search"
            isFocusable = true
            // The 68 dp capsule follows Smart's measured Home bounds and shares the same
            // bottom baseline as the reference surface on gesture-navigation displays.
            translationY = -dp(8).toFloat()
            // Match the measured opaque dark capsule used by Smart search on the Home screen.
            background = LayerDrawable(arrayOf(roundedDrawable(0xFF343848.toInt(), dp(29)), selectableBackground()))
            setOnClickListener { openTeresSearch() }
            setOnLongClickListener { showSearchButtonMenu(this); true }
        }
        homeLayer.addView(searchButton, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(25) })
        applyHomeSearchVisibility()

        feedSideButton = FrameLayout(this).apply {
            contentDescription = "Open Your Feed side panel"
            isFocusable = true
            background = LayerDrawable(arrayOf(roundedDrawable(0xAA34394B.toInt(), dp(28)), selectableBackground()))
            setOnClickListener {
                runCatching { startActivity(Intent(this@HomeActivity, FeedActivity::class.java)) }
                    .onFailure { toast("Your Feed could not be opened") }
            }
            addView(ImageView(this@HomeActivity).apply {
                setImageResource(R.drawable.ic_feed)
                setColorFilter(Color.WHITE)
                contentDescription = null
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }
        root.addView(
            feedSideButton,
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(4)
                bottomMargin = dp(9)
            },
        )
        renderFeedSideButton()

        drawerLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(60), dp(14), dp(24))
            background = drawerBackdrop()
            visibility = View.GONE
        }
        root.addView(drawerLayer, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        buildDrawer()
        LauncherTypography.applyToViewTree(this, root)
    }

    private fun buildDrawer() {
        drawerLayer.removeAllViews()
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        drawerTitle = TextView(this).apply {
            text = getString(R.string.home_drawer_title)
            textSize = 27f
            typeface = LauncherTypography.typeface(this@HomeActivity, 700)
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                if (drawerShowingWorkAppsPage) {
                    showAllAppsPage()
                } else if (drawerIndexFilter != null) {
                    setDrawerIndexState(true, null)
                    renderDrawerApps("")
                }
            }
        }
        top.addView(drawerTitle, LinearLayout.LayoutParams(0, -2, 1f))
        drawerSearchButton = drawerAction("search", "Search apps") { handleDrawerSearchAction() }
        top.addView(drawerSearchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        drawerStoreButton = drawerAction("store", "Find apps") { openAppStore() }
        top.addView(drawerStoreButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(drawerAction("more", "App page menu") { showDrawerMenu(this) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        drawerLayer.addView(top)
        updateDrawerHeaderActions()

        drawerModeSwitcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedDrawable(0x441B2034, dp(22))
        }
        personalTabButton = drawerTabButton(R.string.home_drawer_tab_personal, "Personal apps tab") { showAllAppsPage() }
        vaultTabButton = drawerTabButton(R.string.home_drawer_tab_vault, "Haven Vault isolated apps tab") { openWorkAppsPage() }
        drawerModeSwitcher.addView(personalTabButton, LinearLayout.LayoutParams(0, dp(36), 1f))
        drawerModeSwitcher.addView(vaultTabButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(4) })
        drawerLayer.addView(drawerModeSwitcher, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(10)
            bottomMargin = dp(2)
        })
        updateDrawerModeSwitcher()

        vaultHeaderPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(4))
        }
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(0x66222B3E, dp(18))
            setPadding(dp(14), dp(12), dp(12), dp(12))
        }
        vaultStatusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF4CACA5.toInt())
            }
        }
        statusCard.addView(vaultStatusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
        val statusTextCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        vaultStatusTitle = TextView(this).apply {
            textSize = 14f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            setTextColor(Color.WHITE)
        }
        vaultStatusSubtitle = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFD0D9D7.toInt())
            maxLines = 2
        }
        statusTextCol.addView(vaultStatusTitle)
        statusTextCol.addView(vaultStatusSubtitle)
        statusCard.addView(statusTextCol, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        vaultStatusAction = TextView(this).apply {
            textSize = 13f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER
            minHeight = dp(36)
            setPadding(dp(14), 0, dp(14), 0)
            isFocusable = true
        }
        statusCard.addView(vaultStatusAction, LinearLayout.LayoutParams(-2, dp(36)))
        vaultHeaderPanel.addView(statusCard, LinearLayout.LayoutParams(-1, -2))

        vaultActionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(2))
        }
        vaultActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        vaultActionScroll.addView(vaultActionRow)
        vaultHeaderPanel.addView(vaultActionScroll, LinearLayout.LayoutParams(-1, -2))
        drawerLayer.addView(vaultHeaderPanel)

        drawerSearch = EditText(this).apply {
            hint = "Search installed apps"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFCBC5D0.toInt())
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedDrawable(0x55231D2B, dp(24))
            contentDescription = "Search installed apps"
            visibility = View.GONE
        }
        drawerLayer.addView(drawerSearch, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        drawerCategoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(2))
            visibility = View.GONE
        }
        drawerCategories = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        drawerCategoryScroll.addView(drawerCategories)
        drawerLayer.addView(drawerCategoryScroll, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(4) })

        drawerIndexGrid = GridLayout(this).apply {
            columnCount = 5
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            clipToPadding = false
            setPadding(dp(4), dp(10), dp(4), dp(8))
            contentDescription = "Search apps by index"
            visibility = View.GONE
        }
        drawerLayer.addView(drawerIndexGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })

        val initialColumns = drawerColumns()
        drawerLayoutManager = GridLayoutManager(this, initialColumns).apply {
            isItemPrefetchEnabled = true
            initialPrefetchItemCount = initialColumns * 3
        }
        drawerSpacingDecoration = DrawerGridSpacingDecoration(dp(drawerVerticalSpacingDp()))
        drawerAdapter = DrawerGridAdapter()
        drawerGrid = RecyclerView(this).apply {
            layoutManager = drawerLayoutManager
            adapter = drawerAdapter
            addItemDecoration(drawerSpacingDecoration)
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            recycledViewPool.setMaxRecycledViews(0, 48)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            setPadding(0, dp(6), 0, dp(8))
            contentDescription = "Installed applications"
        }
        drawerEmpty = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(currentPalette().textSecondary)
            setPadding(dp(24), dp(20), dp(24), dp(20))
            visibility = View.GONE
        }
        drawerGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val settings = prefs
                if (prefs.getBoolean(LauncherSettings.KEY_DRAWER_KEEP_LAST_POSITION, false)) {
                    val first = drawerLayoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
                    prefs.edit {
                        putInt(LauncherSettings.KEY_DRAWER_LAST_SCROLL_POSITION, first)
                    }
                }
            }
        })
        drawerGrid.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> drawerReorderMode && event.localState is DrawerDragPayload
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_EXITED -> true
                DragEvent.ACTION_DROP -> {
                    val payload = event.localState as? DrawerDragPayload ?: return@setOnDragListener false
                    val recycler = view as RecyclerView
                    val child = recycler.findChildViewUnder(event.x, event.y) ?: return@setOnDragListener false
                    val targetPosition = recycler.getChildAdapterPosition(child)
                    if (targetPosition !in visibleDrawerApps.indices) return@setOnDragListener false
                    val visibleOrder = visibleDrawerApps.map { it.entry.componentKey }
                    val updated = DrawerOrdering.moveWithinSubset(
                        fullOrder = drawerUserOrder(),
                        visibleOrder = visibleOrder,
                        sourceKey = payload.componentKey,
                        targetKey = visibleOrder[targetPosition],
                    )
                    saveDrawerUserOrder(updated)
                    renderDrawerApps(drawerSearch.text.toString())
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    drawerAdapterRenderSignature = null
                    renderDrawerApps(drawerSearch.text.toString())
                    true
                }
                else -> false
            }
        }
        drawerLayer.addView(drawerGrid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(2) })
        drawerLayer.addView(drawerEmpty, LinearLayout.LayoutParams(-1, 0, 1f))

        drawerSearch.doOnTextChanged { s, _, _, _ ->
            if (drawerOpen) renderDrawerApps(s?.toString().orEmpty())
        }
        renderDrawerCategories()
        renderDrawerApps("")
    }

    private fun handleWorkspaceDoubleTap(rawX: Float, rawY: Float) {
        if (!::homeGrid.isInitialized || drawerOpen) return
        val x = rawX.toInt()
        val y = rawY.toInt()
        if (!homeGrid.containsScreenPoint(x.toFloat(), y.toFloat())) return

        val cellIndex = (0 until homeGrid.childCount).firstNotNullOfOrNull { index ->
            val cell = homeGrid.getChildAt(index)
            if (cell.containsScreenPoint(x.toFloat(), y.toFloat())) cell.tag as? Int else null
        } ?: return
        if (layout.items.any { it.page == currentPage && it.cell == cellIndex }) return

        when (prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, LauncherSettings.GESTURE_ACTION_NONE)) {
            LauncherSettings.GESTURE_ACTION_SEARCH -> openTeresSearch()
            LauncherSettings.GESTURE_ACTION_DRAWER -> openDrawer()
            LauncherSettings.GESTURE_ACTION_QUICK_SETTINGS -> performSystemAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            LauncherSettings.GESTURE_ACTION_LOCK_SCREEN -> performSystemAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            LauncherSettings.GESTURE_ACTION_NOTIFICATIONS -> performSystemAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            LauncherSettings.GESTURE_ACTION_RECENTS -> performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            LauncherSettings.GESTURE_ACTION_ASSIST -> {
                val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(assist) }.onFailure { toast("No digital assistant is available") }
            }
            LauncherSettings.GESTURE_ACTION_TOGGLE_DOCK -> {
                val visible = !prefs.getBoolean(LauncherSettings.KEY_DOCK_VISIBLE, true)
                prefs.edit { putBoolean(LauncherSettings.KEY_DOCK_VISIBLE, visible) }
                homeDock.visibility = if (visible) View.VISIBLE else View.GONE
                toast(if (visible) "Dock shown" else "Dock hidden")
            }
            LauncherSettings.GESTURE_ACTION_SETTINGS -> openSettings()
            LauncherSettings.GESTURE_ACTION_OPEN_VAULT -> openDrawerToVault()
            LauncherSettings.GESTURE_ACTION_FREEZE_ALL_WORK -> freezeAllAutoFreezeApps()
            LauncherSettings.GESTURE_ACTION_TOGGLE_WORK_PROFILE -> toggleWorkProfilePaused()
            LauncherSettings.GESTURE_ACTION_APP_INDEX -> openDrawer(focusSearch = true)
            LauncherSettings.GESTURE_ACTION_GO_TO_PAGE -> moveToPage(prefs.getInt(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PAGE, 0))
            LauncherSettings.GESTURE_ACTION_WEB_PAGE -> {
                val uri = runCatching { prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_URL, null)?.toUri() }.getOrNull()
                if (uri == null || uri.scheme !in setOf("https", "http")) {
                    toast("Choose a valid web address in Gestures settings")
                } else {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        .onFailure { toast("No browser can open this address") }
                }
            }
            LauncherSettings.GESTURE_ACTION_APP -> {
                val packageName = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PACKAGE, null)
                val className = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_CLASS, null)
                if (packageName.isNullOrBlank() || className.isNullOrBlank()) {
                    toast("Choose an app for the double-tap action in Gestures settings")
                    return
                }

                fun launchFrom(candidates: List<LaunchableApp>): Boolean {
                    val selected = candidates.firstOrNull {
                        it.entry.packageName == packageName && it.entry.className == className
                    } ?: return false
                    val launched = runCatching { catalog.start(selected) }.isSuccess
                    if (launched) recordAppLaunch(selected)
                    return launched
                }

                if (!launchFrom(apps)) {
                    catalog.loadAsync { refreshed ->
                        if (isFinishing || isDestroyed) return@loadAsync
                        if (!launchFrom(refreshed)) {
                            val label = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, "The selected app")
                            toast("$label is no longer available")
                        }
                    }
                }
            }
            LauncherSettings.GESTURE_ACTION_SHORTCUT -> {
                val packageName = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PACKAGE, null)
                val className = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_CLASS, null)
                val shortcutId = prefs.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_SHORTCUT_ID, null)
                if (packageName.isNullOrBlank() || className.isNullOrBlank() || shortcutId.isNullOrBlank()) {
                    toast("Choose a shortcut for the double-tap action in Gestures settings")
                    return
                }
                launchShortcutTarget(packageName, className, shortcutId)
            }
        }
    }

    private fun performSystemAction(action: Int) {
        if (!LauncherActionAccessibilityService.isEnabled(this)) {
            toast("Enable Teres Launcher system actions in Accessibility settings first")
            return
        }
        if (!LauncherActionAccessibilityService.perform(action)) {
            toast("Android could not perform that system action")
        }
    }

    private fun launchShortcutTarget(packageName: String, className: String, shortcutId: String) {
        fun openFrom(candidates: List<LaunchableApp>) {
            val app = candidates.firstOrNull {
                it.entry.packageName == packageName && it.entry.className == className
            }
            if (app == null) {
                toast("The selected shortcut app is no longer installed")
                return
            }
            catalog.loadShortcutsAsync(app) { shortcuts ->
                if (isFinishing || isDestroyed) return@loadShortcutsAsync
                val selected = shortcuts.firstOrNull { it.id == shortcutId }
                if (selected == null) {
                    toast("The selected shortcut is unavailable. Check Home shortcut access in Android Settings.")
                } else {
                    runCatching {
                        catalog.start(selected, null)
                        apps.firstOrNull { it.entry.packageName == packageName && it.entry.className == className }
                            ?.let(::recordAppLaunch)
                    }
                        .onFailure { toast("This shortcut could not be opened") }
                }
            }
        }

        val app = apps.firstOrNull {
            it.entry.packageName == packageName && it.entry.className == className
        }
        if (app != null) {
            openFrom(listOf(app))
        } else {
            catalog.loadAsync { refreshed ->
                if (isFinishing || isDestroyed) return@loadAsync
                openFrom(refreshed)
            }
        }
    }

    private fun openDrawer(focusSearch: Boolean = false) {
        val keepLastPosition = prefs.getBoolean(LauncherSettings.KEY_DRAWER_KEEP_LAST_POSITION, false)
        val refreshCategoryChips = prefs.getBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, false) ||
            (::drawerCategoryScroll.isInitialized && drawerCategoryScroll.isVisible)
        val indexSearchEnabled = prefs.getBoolean(LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)
        val savedLaunchView = DrawerLaunchView.fromPreference(prefs.getString(LauncherSettings.KEY_DRAWER_LAUNCH_VIEW, null))
        val launchView = if (savedLaunchView == DrawerLaunchView.APP_INDEX && !indexSearchEnabled) {
            DrawerLaunchView.ALL_APPS
        } else savedLaunchView
        val openIndexForSearch = focusSearch && indexSearchEnabled
        val shouldApplyLaunchView = focusSearch || !keepLastPosition || !drawerHasBeenOpened
        if (!focusSearch && shouldApplyLaunchView && launchView == DrawerLaunchView.SEARCH) {
            openTeresSearch()
            return
        }
        if (shouldApplyLaunchView) {
            setDrawerIndexState(openIndexForSearch || (!focusSearch && launchView == DrawerLaunchView.APP_INDEX), null)
        } else if (!indexSearchEnabled && drawerIndexMode) {
            setDrawerIndexState(false, null)
        }
        if (!keepLastPosition) {
            drawerSearchVisible = false
            drawerSearch.text?.clear()
            drawerSearch.visibility = View.GONE
            selectedDrawerCategory = DrawerCategories.ALL
        }
        if (openIndexForSearch) {
            drawerSearchVisible = false
            drawerSearch.text?.clear()
            drawerSearch.visibility = View.GONE
        }
        drawerHasBeenOpened = true
        drawerOpen = true
        if (!keepLastPosition && refreshCategoryChips) renderDrawerCategories()
        listOf(homeLayer, homeGrid, pageIndicator).forEach { view ->
            view.animate().cancel()
            view.alpha = 1f
        }
        homeLayer.translationY = 0f
        homeLayer.visibility = View.INVISIBLE
        homeGrid.visibility = View.INVISIBLE
        pageIndicator.visibility = View.INVISIBLE
        drawerLayer.visibility = View.VISIBLE
        applyDrawerBlur(true)
        updateSystemBarAppearance()
        val animation = prefs.getString(LauncherSettings.KEY_HOME_ANIMATION, "slide")
        drawerLayer.alpha = if (animation == "fade") 0f else 1f
        drawerLayer.translationY = if (animation == "slide") dp(18).toFloat() else 0f
        drawerLayer.scaleX = if (animation == "zoom") 0.96f else 1f
        drawerLayer.scaleY = if (animation == "zoom") 0.96f else 1f
        drawerLayer.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(if (animation == "none") 0 else 190)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withLayer()
            .start()
        renderDrawerApps(drawerSearch.text.toString())
        if (focusSearch && !openIndexForSearch) {
            setDrawerSearchVisible(true)
        }
        drawerGrid.post {
            if (!drawerOpen) return@post
            val firstVisibleItem = if (keepLastPosition) prefs.getInt(LauncherSettings.KEY_DRAWER_LAST_SCROLL_POSITION, 0) else 0
            val maxIdx = (drawerAdapter.itemCount - 1).coerceAtLeast(0)
            drawerLayoutManager.scrollToPositionWithOffset(firstVisibleItem.coerceIn(0, maxIdx), 0)
        }
    }

    private fun openTeresSearch() {
        startActivity(Intent(this, LauncherSearchActivity::class.java))
    }

    private fun closeDrawer() {
        drawerOpen = false
        if (drawerShowingWorkAppsPage) showAllAppsPage(render = false)
        if (vaultSessionAcquired) {
            vaultSessionAcquired = false
            HavenProfileSession.release("vault_drawer")
        }
        val keepLastPosition = prefs.getBoolean(LauncherSettings.KEY_DRAWER_KEEP_LAST_POSITION, false)
        if (keepLastPosition) {
            prefs.edit { putInt(LauncherSettings.KEY_DRAWER_LAST_SCROLL_POSITION, drawerLayoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)) }
        } else {
            prefs.edit { putInt(LauncherSettings.KEY_DRAWER_LAST_SCROLL_POSITION, 0) }
            drawerSearchVisible = false
            drawerSearch.text?.clear()
            drawerSearch.visibility = View.GONE
            selectedDrawerCategory = DrawerCategories.ALL
        }
        drawerReorderMode = false
        applyDrawerBlur(false)
        updateSystemBarAppearance()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(drawerSearch.windowToken, 0)
        val animation = prefs.getString(LauncherSettings.KEY_HOME_ANIMATION, "slide")
        val duration = if (animation == "none") 0L else 170L
        listOf(homeLayer, homeGrid, pageIndicator).forEach { view ->
            view.animate().cancel()
            view.visibility = View.VISIBLE
            view.alpha = 0f
        }
        homeLayer.translationY = if (animation == "slide") -dp(8).toFloat() else 0f
        homeLayer.animate().alpha(1f).translationY(0f).setDuration(duration).setInterpolator(DecelerateInterpolator(1.5f)).start()
        homeGrid.animate().alpha(1f).setDuration(duration).setInterpolator(DecelerateInterpolator(1.5f)).start()
        pageIndicator.animate().alpha(1f).setDuration(duration).setInterpolator(DecelerateInterpolator(1.5f)).start()
        drawerLayer.animate()
            .alpha(if (animation == "none") 1f else 0f)
            .translationY(if (animation == "slide") dp(10).toFloat() else 0f)
            .scaleX(if (animation == "zoom") 0.96f else 1f)
            .scaleY(if (animation == "zoom") 0.96f else 1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .withLayer()
            .withEndAction {
                drawerLayer.visibility = View.GONE
                drawerLayer.translationY = 0f
                homeLayer.visibility = View.VISIBLE
                homeGrid.visibility = View.VISIBLE
                updatePageIndicator()
            }
            .start()
    }

    private fun renderDrawerCategories() {
        if (!::drawerCategories.isInitialized) return
        if (drawerShowingWorkAppsPage) {
            drawerCategoryScroll.visibility = View.GONE
            drawerCategories.removeAllViews()
            if (::vaultHeaderPanel.isInitialized) vaultHeaderPanel.visibility = if (drawerIndexMode) View.GONE else View.VISIBLE
            updateDrawerModeSwitcher()
            updateDrawerGridGeometry()
            updateDrawerTitle()
            return
        }
        if (::vaultHeaderPanel.isInitialized) vaultHeaderPanel.visibility = View.GONE
        updateDrawerModeSwitcher()
        val showCategories = prefs.getBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, false)
        drawerCategoryScroll.visibility = if (!drawerIndexMode && drawerIndexFilter == null &&
            (showCategories || selectedDrawerCategory == DrawerCategories.HIDDEN)
        ) View.VISIBLE else View.GONE
        if (!showCategories && selectedDrawerCategory != DrawerCategories.HIDDEN) {
            selectedDrawerCategory = DrawerCategories.ALL
        }
        updateDrawerGridGeometry()
        val categories = apps.asSequence()
            .filterNot { DrawerCategories.belongsToPackage(it.entry.componentKey, packageName) }
            .filterNot { it.entry.componentKey in hiddenAppComponents }
            .map { DrawerCategories.labelFor(it.activity.applicationInfo.category) }
            .distinct()
            .filterNot { it == DrawerCategories.OTHER }
            .sorted()
            .toList()
        val available = buildList {
            add(DrawerCategories.ALL)
            addAll(categories)
            if (hiddenAppComponents.isNotEmpty()) add(DrawerCategories.HIDDEN)
        }
        if (selectedDrawerCategory !in available) selectedDrawerCategory = DrawerCategories.ALL
        updateDrawerTitle()
        drawerCategories.removeAllViews()
        available.forEach { category ->
            val selected = category == selectedDrawerCategory
            drawerCategories.addView(drawerPillChip(
                label = category,
                textSize = 13f,
                minHeightDp = 44,
                horizontalPadDp = 15,
                bgColor = if (selected) currentPalette().accent else currentPalette().surfaceVariant,
                textColor = if (selected) currentPalette().onAccent else currentPalette().textPrimary,
                contentDesc = "$category${if (selected) ", selected" else ""}",
            ) {
                selectedDrawerCategory = category
                renderDrawerCategories()
                renderDrawerApps(drawerSearch.text.toString())
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) })
        }
        LauncherTypography.applyToViewTree(this, drawerCategories)
    }

    private fun renderDrawerApps(query: String) {
        if (!::drawerGrid.isInitialized || !::drawerLayoutManager.isInitialized) return
        drawerLabelsVisible = prefs.getBoolean(LauncherSettings.KEY_DRAWER_SHOW_LABELS, true)
        val twoLineLabels = prefs.getBoolean(LauncherSettings.KEY_DRAWER_TWO_LINE_LABELS, true)
        val desiredColumns = if (drawerIndexMode) 5 else drawerColumns()
        if (drawerLayoutManager.spanCount != desiredColumns) {
            drawerLayoutManager.spanCount = desiredColumns
            drawerLayoutManager.initialPrefetchItemCount = desiredColumns * 3
        }
        val stackFromBottom = prefs.getBoolean(LauncherSettings.KEY_DRAWER_STACK_FROM_BOTTOM, false)
        val useBottomStack = stackFromBottom && !drawerIndexMode
        updateDrawerGridGeometry()
        val orderedApps = if (drawerShowingWorkAppsPage) emptyList() else orderedDrawerApps()
        val categoryEntries = orderedApps.map {
            DrawerCategoryEntry(it.entry.componentKey, DrawerCategories.labelFor(it.activity.applicationInfo.category))
        }
        val allowed = if (drawerShowingWorkAppsPage) null else {
            DrawerCategories.visible(categoryEntries, selectedDrawerCategory, hiddenAppComponents)
                .mapTo(hashSetOf()) { it.componentKey }
        }
        val byKey = orderedApps.associateBy { it.entry.componentKey }
        val eligible = if (allowed == null) orderedApps else orderedApps.filter { it.entry.componentKey in allowed }
        val matches = LauncherSearch.filterPreservingOrder(eligible.map { it.entry }, query)
        val matchingApps = matches.mapNotNull { byKey[it.componentKey] }
        val filteredApps = if (drawerIndexFilter != null && query.isBlank()) {
            matchingApps.filter { it.entry.label.firstOrNull()?.uppercaseChar()?.toString() == drawerIndexFilter }
        } else matchingApps
        visibleDrawerApps = if (useBottomStack) filteredApps.asReversed() else filteredApps
        visibleVaultItems = if (drawerShowingWorkAppsPage) {
            val showFrozen = prefs.getBoolean(LauncherSettings.KEY_VAULT_SHOW_FROZEN_APPS, true)
            val filtered = vaultItems.filter { item ->
                (showFrozen || !item.isFrozen) &&
                    (query.isBlank() || item.label.contains(query, ignoreCase = true) || item.packageName.contains(query, ignoreCase = true)) &&
                    (drawerIndexFilter == null || query.isNotBlank() || item.label.firstOrNull()?.uppercaseChar()?.toString() == drawerIndexFilter)
            }
            if (useBottomStack) filtered.asReversed() else filtered
        } else emptyList()
        if (drawerIndexMode) {
            val sourceLabels = if (drawerShowingWorkAppsPage) visibleVaultItems.map { it.label } else visibleDrawerApps.map { it.entry.label }
            drawerIndexLetters = sourceLabels.mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.distinct().sorted()
            renderDrawerIndexLetters()
        } else {
            drawerIndexLetters = emptyList()
        }
        updateDrawerTitle()
        if (drawerShowingWorkAppsPage) renderVaultHeader()
        updateDrawerHeaderActions()

        val activePalette = currentPalette()
        val pack = prefs.getString(LauncherSettings.KEY_ICON_PACK_DRAWER, IconPackRepository.DEFAULT_PACK)
            ?: IconPackRepository.DEFAULT_PACK
        val highlightNotifications = prefs.getBoolean(LauncherSettings.KEY_HIGHLIGHT_NOTIFICATIONS, true)
        val nextConfig = DrawerRenderConfig(
            iconSizePx = dp(drawerIconSize()),
            cellHeightPx = dp(drawerCellHeightDp()),
            labelsVisible = drawerLabelsVisible,
            twoLineLabels = twoLineLabels,
            accentColor = activePalette.accent,
            iconPack = pack,
            highlightNotifications = highlightNotifications,
            densityDpi = resources.displayMetrics.densityDpi,
            labelTypeface = LauncherTypography.typeface(this, 500),
            iconGeneration = drawerIconGeneration,
        )
        val configChanged = nextConfig != drawerRenderConfig
        drawerRenderConfig = nextConfig

        val renderSignature = drawerGridRenderSignature()
        if (!drawerIndexMode && (renderSignature != drawerAdapterRenderSignature || configChanged)) {
            val items: List<DrawerListItem> = if (drawerShowingWorkAppsPage) {
                visibleVaultItems.map { item ->
                    val token = "${nextConfig.iconGeneration}|${nextConfig.accentColor}|${nextConfig.labelsVisible}|" +
                        "${nextConfig.twoLineLabels}|${nextConfig.iconSizePx}|${nextConfig.cellHeightPx}|" +
                        "${nextConfig.iconPack}|${item.isFrozen}|${item.isAutoFreeze}|${item.icon != null}|${item.packageName in vaultIconBitmaps}"
                    DrawerListItem.VaultItem(
                        vault = item,
                        stableKey = "vault:${item.packageName}",
                        label = item.label,
                        iconToken = token,
                    )
                }
            } else {
                val myUser = Process.myUserHandle()
                visibleDrawerApps.map { app ->
                    val isPersonal = app.activity.user == myUser
                    val highlighted = isPersonal && nextConfig.highlightNotifications && app.entry.packageName in activeNotificationPackages
                    val token = "${nextConfig.iconGeneration}|${nextConfig.accentColor}|${nextConfig.labelsVisible}|" +
                        "${nextConfig.twoLineLabels}|${nextConfig.iconSizePx}|${nextConfig.cellHeightPx}|" +
                        "${nextConfig.iconPack}|$highlighted"
                    DrawerListItem.AppItem(
                        app = app,
                        stableKey = "app:${app.activity.user.hashCode()}:${app.entry.componentKey}",
                        label = app.entry.label,
                        iconToken = token,
                    )
                }
            }
            drawerAdapter.submitList(items)
            drawerAdapterRenderSignature = renderSignature
        }
        drawerGrid.post {
            if (!::drawerLayer.isInitialized || !::drawerGrid.isInitialized) return@post
            // Smart shifts the entire app-page stack by one row pitch when bottom stacking is enabled.
            val rowPitch = if (drawerShowsCategories()) DRAWER_CATEGORIZED_ROW_PITCH_DP else DRAWER_ALL_APPS_ROW_PITCH_DP
            val stackOffset = if (useBottomStack) dp(rowPitch) else 0
            (drawerGrid.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                if (params.bottomMargin != stackOffset) {
                    params.bottomMargin = stackOffset
                    drawerGrid.layoutParams = params
                }
            }
            for (index in 0 until drawerLayer.childCount) {
                val child = drawerLayer.getChildAt(index)
                if (child !== drawerGrid && child !== drawerEmpty && child.translationY != stackOffset.toFloat()) {
                    child.translationY = stackOffset.toFloat()
                }
            }
            if (drawerGrid.translationY != stackOffset.toFloat()) {
                drawerGrid.translationY = stackOffset.toFloat()
            }
        }
        drawerEmpty.text = if (drawerShowingWorkAppsPage) {
            val sessionState = HavenProfileSession.inspectState(this)
            when {
                sessionState == VaultProfileState.UNPROVISIONED -> "Haven Work Profile is not set up yet. Tap Set up above."
                sessionState == VaultProfileState.SETUP_PENDING -> "Tap the system provisioning notification to finish setup."
                sessionState == VaultProfileState.PROFILE_PAUSED || workProfileAppsState == WorkProfileAppLoadState.PROFILE_PAUSED ->
                    "Work profile is paused. Tap Resume above to wake isolated apps."
                workProfileAppsState == WorkProfileAppLoadState.LOADING && visibleVaultItems.isEmpty() -> "Loading Haven Vault apps…"
                visibleVaultItems.isEmpty() && query.isNotBlank() -> "No isolated apps match this search."
                visibleVaultItems.isEmpty() -> "No isolated apps installed yet. Long-press any Personal app to clone it into Haven."
                else -> ""
            }
        } else when {
            selectedDrawerCategory == DrawerCategories.HIDDEN && hiddenAppComponents.isEmpty() ->
                "No hidden apps. Long-press an app to hide it."
            visibleDrawerApps.isEmpty() && query.isNotBlank() -> "No apps match this search."
            visibleDrawerApps.isEmpty() -> "No launchable apps in this category."
            else -> ""
        }
        val isEmptyContent = if (drawerShowingWorkAppsPage) visibleVaultItems.isEmpty() else visibleDrawerApps.isEmpty()
        drawerIndexGrid.visibility = if (drawerIndexMode) View.VISIBLE else View.GONE
        drawerGrid.visibility = if (drawerIndexMode || isEmptyContent) View.GONE else View.VISIBLE
        drawerEmpty.visibility = if ((drawerIndexMode && (!drawerShowingWorkAppsPage || visibleVaultItems.isNotEmpty())) || !isEmptyContent) {
            View.GONE
        } else View.VISIBLE
    }

    private fun renderDrawerIndexLetters() {
        if (!::drawerIndexGrid.isInitialized || !drawerIndexMode) return
        if (drawerIndexLetters == renderedDrawerIndexLetters) return
        drawerIndexGrid.removeAllViews()
        drawerIndexLetters.forEachIndexed { index, letter ->
            val label = TextView(this).apply {
                text = letter
                textSize = 22f
                typeface = LauncherTypography.typeface(this@HomeActivity, 400)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedDrawable(0xCC495066.toInt(), dp(15))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val cell = FrameLayout(this).apply {
                contentDescription = "Apps starting with $letter"
                isFocusable = true
                isClickable = true
                setOnClickListener { selectDrawerIndexLetter(letter) }
                addView(label, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
            }
            val row = index / 5
            val column = index % 5
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1, GridLayout.FILL),
                GridLayout.spec(column, 1, GridLayout.FILL, 1f),
            ).apply {
                width = 0
                height = dp(60)
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            drawerIndexGrid.addView(cell, params)
        }
        renderedDrawerIndexLetters = drawerIndexLetters.toList()
    }

    private fun orderedDrawerApps(): List<LaunchableApp> {
        val userOrder = drawerUserOrder()
        val mode = DrawerSortMode.fromPreference(prefs.getString(LauncherSettings.KEY_DRAWER_SORT_ORDER, null))
        val usageCounts = runCatching { JSONObject(prefs.getString(LauncherSettings.KEY_DRAWER_USAGE_COUNTS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        return DrawerOrdering.sort(
            entries = apps.filterNot {
                DrawerCategories.belongsToPackage(it.entry.componentKey, packageName)
            }.map { app ->
                DrawerOrderEntry(
                    value = app,
                    componentKey = app.entry.componentKey,
                    label = app.entry.label,
                    firstInstallTime = app.firstInstallTime,
                    usageCount = usageCounts.optLong(app.entry.componentKey, 0L),
                    dominantIconColor = app.dominantIconColor,
                )
            },
            mode = mode,
            userOrder = userOrder,
        )
    }

    private fun drawerUserOrder(): List<String> {
        val saved = runCatching {
            val array = JSONArray(prefs.getString(LauncherSettings.KEY_DRAWER_USER_ORDER, "[]") ?: "[]")
            List(array.length()) { index -> array.optString(index) }.filter(String::isNotBlank)
        }.getOrDefault(emptyList())
        val available = apps.asSequence()
            .filterNot { DrawerCategories.belongsToPackage(it.entry.componentKey, packageName) }
            .mapTo(linkedSetOf()) { it.entry.componentKey }
        val normalized = saved.filter { it in available }.distinct().toMutableList()
        available.forEach { if (it !in normalized) normalized += it }
        return normalized
    }

    private fun saveDrawerUserOrder(order: List<String>) {
        prefs.edit { putString(LauncherSettings.KEY_DRAWER_USER_ORDER, JSONArray(order).toString()) }
    }

    private fun launchApp(app: LaunchableApp) {
        runCatching {
            if (prefs.getBoolean(LauncherSettings.KEY_DRAWER_CLOSE_AFTER_LAUNCH, false)) closeDrawer()
            catalog.start(app)
            recordAppLaunch(app)
        }.onFailure { toast("This app could not be opened") }
    }

    private fun recordAppLaunch(app: LaunchableApp) {
        val usage = runCatching { JSONObject(prefs.getString(LauncherSettings.KEY_DRAWER_USAGE_COUNTS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        usage.put(app.entry.componentKey, usage.optLong(app.entry.componentKey, 0L) + 1L)
        prefs.edit { putString(LauncherSettings.KEY_DRAWER_USAGE_COUNTS, usage.toString()) }
    }

    private fun showDrawerSortingDialog() {
        val modes = DrawerSortMode.entries
        val selected = modes.indexOf(DrawerSortMode.fromPreference(prefs.getString(LauncherSettings.KEY_DRAWER_SORT_ORDER, null))).coerceAtLeast(0)
        val previousMode = modes[selected]
        showSingleChoiceDialog("Sorting", modes.map { it.label }.toTypedArray(), selected, "Close") { index ->
            val mode = modes[index]
            prefs.edit { putString(LauncherSettings.KEY_DRAWER_SORT_ORDER, mode.preferenceValue) }
            if (mode == DrawerSortMode.USER) saveDrawerUserOrder(drawerUserOrder()) else drawerReorderMode = false
            if (mode == DrawerSortMode.ICON_COLOR && previousMode != DrawerSortMode.ICON_COLOR) refreshApps()
            else renderDrawerApps(drawerSearch.text.toString())
        }
    }

    private fun createDrawerAppCell(config: DrawerRenderConfig): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(6))
            minimumHeight = config.cellHeightPx.coerceAtLeast(dp(66))
            isFocusable = true
            background = selectableBackground()
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val frostBadge = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val iconContainer = FrameLayout(this).apply {
            addView(icon, FrameLayout.LayoutParams(-1, -1))
            addView(frostBadge, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.BOTTOM or Gravity.END))
        }
        cell.addView(iconContainer, LinearLayout.LayoutParams(config.iconSizePx, config.iconSizePx))
        cell.addView(TextView(this).apply {
            typeface = config.labelTypeface
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = if (config.twoLineLabels) 2 else 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = if (config.labelsVisible) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        return cell
    }

    private fun workspaceIconCell(
        label: String,
        iconDrawable: Drawable,
        workspaceItem: WorkspaceItem,
        app: LaunchableApp? = null,
        overlayBadgeText: String? = null,
    ): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            contentDescription = label
            isFocusable = true
            background = selectableBackground()
        }
        val iconSize = workspaceIconSizeDp()
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = label
            setImageDrawable(iconDrawable)
        }
        val iconContainer = FrameLayout(this).apply {
            addView(icon, FrameLayout.LayoutParams(-1, -1))
            if (overlayBadgeText != null) {
                addView(TextView(this@HomeActivity).apply {
                    text = overlayBadgeText
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = roundedDrawable(0xEE1E2D33.toInt(), dp(9))
                }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.BOTTOM or Gravity.END))
            }
        }
        if (app != null) attachDoubleTapBadge(iconContainer, cell, label, app, workspaceItem, iconSize, 2)
        cell.addView(iconContainer, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        cell.addView(TextView(this).apply {
            text = label
            typeface = LauncherTypography.typeface(this@HomeActivity, 500)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            visibility = if (prefs.getBoolean(LauncherSettings.KEY_HOME_SHOW_LABELS, false)) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        return cell
    }

    private fun attachDoubleTapBadge(
        container: FrameLayout,
        cell: LinearLayout,
        baseLabel: String,
        app: LaunchableApp?,
        item: WorkspaceItem,
        iconSizeDp: Int,
        marginDp: Int,
    ) {
        val assignment = item.doubleTapBadgeAssignment(prefs.getBoolean(LauncherSettings.KEY_ICON_DOUBLE_TAP_BADGES, false)) ?: return
        val badge = doubleTapActionBadge(assignment, app, item, iconSizeDp) ?: return
        val side = dp((24f / 56f * iconSizeDp).roundToInt())
        container.addView(badge, FrameLayout.LayoutParams(side, side, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(marginDp), 0, 0, dp(marginDp))
        })
        cell.contentDescription = "$baseLabel, ${WorkspaceIconGesture.DOUBLE_TAP.labelFor(assignment.action)}"
    }

    /** Renders Smart Launcher’s optional lower-left icon for a configured secondary tap action. */
    private fun doubleTapActionBadge(
        assignment: WorkspaceIconGestureAssignment,
        app: LaunchableApp?,
        item: WorkspaceItem,
        iconSizeDp: Int,
    ): View? {
        val targetApp = when (assignment.action) {
            WorkspaceIconGestureAction.OPEN_APP -> app ?: item.folder.firstNotNullOfOrNull(::findApp)
            WorkspaceIconGestureAction.LAUNCH_APP,
            WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
            -> apps.firstOrNull {
                it.entry.packageName == assignment.targetPackage &&
                    (assignment.targetClass.isNullOrBlank() || it.entry.className == assignment.targetClass)
            }
            else -> null
        }
        val appBadge = targetApp?.let { target ->
            val sourceItem = if (target == app) item else null
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageDrawable(
                    appIconDrawable(
                        target.icon ?: packageManager.defaultActivityIcon,
                        highlight = false,
                        iconScope = "home",
                        component = target.componentName,
                        workspaceItem = sourceItem,
                    ),
                )
            }
        }
        val glyph = when (assignment.action) {
            WorkspaceIconGestureAction.NONE -> return null
            WorkspaceIconGestureAction.OPEN_APP,
            WorkspaceIconGestureAction.LAUNCH_APP,
            WorkspaceIconGestureAction.LAUNCH_SHORTCUT,
            -> null
            WorkspaceIconGestureAction.APP_DRAWER -> "apps"
            WorkspaceIconGestureAction.TOGGLE_DOCK -> "dock"
            WorkspaceIconGestureAction.SEARCH,
            WorkspaceIconGestureAction.APP_INDEX,
            -> "search"
            WorkspaceIconGestureAction.POPUP_WIDGET -> "widgets"
            WorkspaceIconGestureAction.ASSISTANT -> "assistant"
            WorkspaceIconGestureAction.SETTINGS -> "settings"
            WorkspaceIconGestureAction.OPEN_VAULT -> "security"
            WorkspaceIconGestureAction.FREEZE_ALL_WORK,
            WorkspaceIconGestureAction.TOGGLE_WORK_PROFILE -> "lock"
            WorkspaceIconGestureAction.NEXT_HOME_PAGE,
            WorkspaceIconGestureAction.PREVIOUS_HOME_PAGE,
            -> "pages"
            WorkspaceIconGestureAction.NOTIFICATIONS -> "notifications"
            WorkspaceIconGestureAction.RECENTS -> "recents"
            WorkspaceIconGestureAction.QUICK_SETTINGS -> "tune"
            WorkspaceIconGestureAction.LOCK_SCREEN -> "lock"
        }
        if (appBadge == null && glyph == null) return null

        val side = dp((24f / 56f * iconSizeDp).roundToInt())
        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFF1F2F7.toInt())
                setStroke(dp(1), 0x55342F3D)
            }
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val content = appBadge ?: SettingsGlyphView(this, glyph!!, 0xFF25232C.toInt()).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val iconSide = if (appBadge == null) side * 3 / 4 else side - dp(4)
        container.addView(content, FrameLayout.LayoutParams(iconSide, iconSide, Gravity.CENTER))
        return container
    }

    private fun drawerAction(glyph: String, description: String, action: View.() -> Unit): View = FrameLayout(this).apply {
        contentDescription = description
        isFocusable = true
        background = selectableBackground()
        addView(SettingsGlyphView(this@HomeActivity, glyph, Color.WHITE), FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
        setOnClickListener { action(this) }
    }

    private fun selectDrawerIndexLetter(letter: String) {
        setDrawerIndexState(false, letter)
        renderDrawerApps("")
    }

    private fun updateDrawerHeaderActions() {
        if (!::drawerStoreButton.isInitialized || !::drawerSearchButton.isInitialized) return
        val hideStore = prefs.getBoolean(LauncherSettings.KEY_DRAWER_HIDE_PLAY_STORE, false)
        val indexSearchEnabled = prefs.getBoolean(LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)
        drawerSearchButton.contentDescription = when {
            indexSearchEnabled -> "Search apps by index"
            drawerShowingWorkAppsPage -> "Search work apps"
            else -> "Search apps"
        }
        drawerSearchButton.visibility = if (drawerIndexMode) View.GONE else View.VISIBLE
        drawerStoreButton.visibility = if (drawerIndexMode || hideStore) View.GONE else View.VISIBLE
    }

    private fun drawerIndexSearchEnabled(): Boolean =
        prefs
            .getBoolean(LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)

    private fun handleDrawerSearchAction() {
        if (!drawerIndexSearchEnabled()) {
            setDrawerSearchVisible(!drawerSearchVisible)
            return
        }
        drawerSearchVisible = false
        drawerSearch.text?.clear()
        drawerSearch.visibility = View.GONE
        setDrawerIndexState(true, null)
        renderDrawerCategories()
        renderDrawerApps("")
    }

    private fun isIsolationEnabled(): Boolean = HavenModePreferences.isIsolationEnabled(this)

    private fun openWorkAppsPage() {
        if (!isIsolationEnabled()) {
            toast(getString(R.string.mode_isolation_disabled_hint))
            return
        }
        drawerShowingWorkAppsPage = true
        drawerReorderMode = false
        selectedDrawerCategory = DrawerCategories.ALL
        setDrawerIndexState(false, null)
        drawerSearch.hint = "Search Haven Vault"
        drawerSearch.contentDescription = "Search Haven Vault"
        drawerSearch.text?.clear()
        workProfileApps = emptyList()
        workProfileAppsState = WorkProfileAppLoadState.LOADING
        updateDrawerModeSwitcher()
        updateDrawerHeaderActions()
        renderDrawerCategories()
        renderVaultHeader()
        renderDrawerApps("")
        loadWorkProfileApps()
    }

    private fun showAllAppsPage(render: Boolean = true) {
        if (!drawerShowingWorkAppsPage) return
        drawerShowingWorkAppsPage = false
        workProfileAppsLoadVersion++
        workProfileApps = emptyList()
        vaultItems = emptyList()
        visibleVaultItems = emptyList()
        workProfileAppsState = WorkProfileAppLoadState.NO_PROFILE
        if (vaultSessionAcquired) {
            vaultSessionAcquired = false
            HavenProfileSession.release("vault_drawer")
        }
        drawerReorderMode = false
        selectedDrawerCategory = DrawerCategories.ALL
        setDrawerIndexState(false, null)
        drawerSearch.hint = "Search installed apps"
        drawerSearch.contentDescription = "Search installed apps"
        drawerSearch.text?.clear()
        if (render) {
            updateDrawerModeSwitcher()
            updateDrawerHeaderActions()
            renderDrawerCategories()
            renderDrawerApps("")
            drawerLayoutManager.scrollToPositionWithOffset(0, 0)
        }
    }

    private fun updateDrawerModeSwitcher() {
        if (!::personalTabButton.isInitialized || !::vaultTabButton.isInitialized) return
        val pal = currentPalette()
        val personalSelected = !drawerShowingWorkAppsPage
        personalTabButton.setTextColor(if (personalSelected) pal.onAccent else 0xFFD0D9D7.toInt())
        personalTabButton.background = roundedDrawable(if (personalSelected) pal.accent else Color.TRANSPARENT, dp(18))
        vaultTabButton.setTextColor(if (!personalSelected) pal.onAccent else 0xFFD0D9D7.toInt())
        vaultTabButton.background = roundedDrawable(if (!personalSelected) pal.accent else Color.TRANSPARENT, dp(18))
        if (::drawerModeSwitcher.isInitialized) {
            val showSwitcher = prefs.getBoolean(LauncherSettings.KEY_DRAWER_SHOW_VAULT_SWITCHER, false)
            drawerModeSwitcher.visibility =
                if (!drawerIndexMode && isIsolationEnabled() && showSwitcher) View.VISIBLE else View.GONE
        }
    }

    private fun renderVaultHeader() {
        if (!::vaultHeaderPanel.isInitialized) return
        val state = HavenProfileSession.inspectState(this)
        val pal = currentPalette()
        fun applyStatus(
            dotColor: Int,
            titleText: CharSequence,
            subtitleText: CharSequence,
            actionText: CharSequence,
            actionTextColor: Int,
            actionBg: Drawable,
            showActions: Boolean,
            onClick: () -> Unit,
        ) {
            (vaultStatusDot.background as? GradientDrawable)?.setColor(dotColor)
            vaultStatusTitle.text = titleText
            vaultStatusSubtitle.text = subtitleText
            vaultStatusAction.text = actionText
            vaultStatusAction.setTextColor(actionTextColor)
            vaultStatusAction.background = actionBg
            vaultStatusAction.visibility = View.VISIBLE
            vaultStatusAction.setOnClickListener { onClick() }
            vaultActionScroll.visibility = if (showActions) View.VISIBLE else View.GONE
        }

        when (state) {
            VaultProfileState.UNPROVISIONED -> applyStatus(
                0xFFFFB879.toInt(),
                getString(R.string.vault_state_unprovisioned_title),
                getString(R.string.vault_state_unprovisioned_subtitle),
                getString(R.string.vault_action_setup),
                pal.onAccent,
                roundedDrawable(pal.accent, dp(18)),
                showActions = false,
            ) { setupWizardLauncher.launch(null) }
            VaultProfileState.SETUP_PENDING -> applyStatus(
                0xFFFFB879.toInt(),
                getString(R.string.vault_state_pending_title),
                getString(R.string.vault_state_pending_subtitle),
                getString(R.string.vault_action_resume),
                pal.onAccent,
                roundedDrawable(pal.accent, dp(18)),
                showActions = false,
            ) {
                startActivity(Intent(this, SetupWizardActivity::class.java).apply {
                    action = SetupWizardActivity.ACTION_RESUME_SETUP
                })
            }
            VaultProfileState.PROFILE_PAUSED -> applyStatus(
                0xFF9AA0A6.toInt(),
                getString(R.string.vault_state_paused_title),
                getString(R.string.vault_state_paused_subtitle),
                getString(R.string.vault_action_resume),
                pal.onAccent,
                roundedDrawable(pal.accent, dp(18)),
                showActions = false,
            ) {
                HavenProfileSession.setWorkProfilePaused(this, false)
                loadWorkProfileApps()
            }
            else -> {
                val activeCount = vaultItems.count { !it.isFrozen }
                val frozenCount = vaultItems.count { it.isFrozen }
                val autoFreezeCount = vaultItems.count { it.isAutoFreeze }
                val subtitle = if (vaultItems.isEmpty() && workProfileAppsState == WorkProfileAppLoadState.LOADING) {
                    "Syncing isolated apps…"
                } else {
                    "$activeCount active · $frozenCount frozen · $autoFreezeCount auto-freeze"
                }
                applyStatus(
                    0xFF73DCA8.toInt(),
                    if (state == VaultProfileState.CONNECTING) "Connecting Vault…" else "Work profile active",
                    subtitle,
                    getString(R.string.vault_action_pause),
                    Color.WHITE,
                    roundedDrawable(0x44FFFFFF, dp(18)),
                    showActions = true,
                ) { toggleWorkProfilePaused() }
            }
        }

        vaultActionRow.removeAllViews()
        listOf<Pair<String, () -> Unit>>(
            "Freeze all" to { freezeAllAutoFreezeApps() },
            "Install APK" to {
                pendingInstallApkIntoVault = true
                installApkLauncher.launch(arrayOf("application/vnd.android.package-archive"))
            },
            "File Shuttle" to { openFileShuttle() },
            "Manager" to {
                runCatching { startActivity(Intent(this, MainActivity::class.java)) }
                    .onFailure { toast("Isolation Manager unavailable") }
            },
            "Settings" to { openSettings("isolation") },
        ).forEachIndexed { idx, (label, onClick) ->
            vaultActionRow.addView(drawerPillChip(
                label = label,
                textSize = 12f,
                minHeightDp = 36,
                horizontalPadDp = 14,
                bgColor = 0x55343D56,
                textColor = Color.WHITE,
                boldWeight = 600,
                contentDesc = label,
                onClick = onClick,
            ), LinearLayout.LayoutParams(-2, dp(36)).apply { if (idx > 0) marginStart = dp(8) })
        }
    }

    private fun openDrawerToVault() {
        if (!isIsolationEnabled()) {
            toast(getString(R.string.mode_isolation_disabled_hint))
            return
        }
        if (!drawerOpen) openDrawer()
        openWorkAppsPage()
    }

    private fun loadWorkProfileApps() {
        val request = ++workProfileAppsLoadVersion
        val sessionState = HavenProfileSession.inspectState(this)
        if (sessionState == VaultProfileState.UNPROVISIONED) {
            workProfileApps = emptyList()
            vaultItems = emptyList()
            workProfileAppsState = WorkProfileAppLoadState.NO_PROFILE
            renderVaultHeader()
            renderDrawerApps(drawerSearch.text?.toString().orEmpty())
            VaultControlWidgetProvider.updateAll(this)
            return
        }
        if (sessionState == VaultProfileState.PROFILE_PAUSED) {
            workProfileApps = emptyList()
            vaultItems = emptyList()
            workProfileAppsState = WorkProfileAppLoadState.PROFILE_PAUSED
            renderVaultHeader()
            renderDrawerApps(drawerSearch.text?.toString().orEmpty())
            VaultControlWidgetProvider.updateAll(this)
            return
        }

        // Fast path on API 35+: render active LauncherApps immediately while binding HavenService for frozen apps.
        catalog.loadManagedWorkProfileAppsAsync { result ->
            if (isFinishing || isDestroyed || !drawerShowingWorkAppsPage || request != workProfileAppsLoadVersion) {
                return@loadManagedWorkProfileAppsAsync
            }
            workProfileApps = result.apps
            if (vaultItems.isEmpty() && result.apps.isNotEmpty()) {
                val autoFreeze = LocalStorageManager.getInstance()
                    .getStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE).toSet()
                vaultItems = result.apps.map { app ->
                    VaultAppItem(
                        packageName = app.entry.packageName,
                        className = app.entry.className,
                        label = app.entry.label,
                        icon = app.icon,
                        isFrozen = false,
                        isAutoFreeze = app.entry.packageName in autoFreeze,
                        isSystem = false,
                        launchableApp = app,
                        wrapper = null,
                    )
                }
                workProfileAppsState = WorkProfileAppLoadState.READY
                renderVaultHeader()
                renderDrawerApps(drawerSearch.text?.toString().orEmpty())
            }
        }

        vaultSessionAcquired = true
        HavenProfileSession.acquire(
            host = this,
            consumerTag = "vault_drawer",
            onReady = { _, workService ->
                if (isFinishing || isDestroyed || !drawerShowingWorkAppsPage || request != workProfileAppsLoadVersion) {
                    return@acquire
                }
                try {
                    workService.getApps(object : IGetAppsCallback.Stub() {
                        override fun callback(appsList: MutableList<ApplicationInfoWrapper>?) {
                            val list = appsList.orEmpty()
                            val widgets = runCatching { workService.crossProfileWidgetProviders }.getOrDefault(emptyList()).toMutableSet()
                            val crossPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching { workService.crossProfilePackages }.getOrDefault(emptyList()).toMutableSet()
                            } else mutableSetOf()
                            val autoFreezeSet = LocalStorageManager.getInstance()
                                .getStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE).toSet()
                            Utility.deleteMissingApps(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, list)

                            runOnUiThread {
                                if (isFinishing || isDestroyed || !drawerShowingWorkAppsPage || request != workProfileAppsLoadVersion) {
                                    return@runOnUiThread
                                }
                                vaultCrossProfileWidgets = widgets
                                vaultCrossProfilePackages = crossPkgs
                                val launchablesByPkg = workProfileApps.associateBy { it.entry.packageName }
                                val merged = list.map { wrapper ->
                                    val launchable = launchablesByPkg[wrapper.packageName]
                                    VaultAppItem(
                                        packageName = wrapper.packageName,
                                        className = launchable?.entry?.className,
                                        label = wrapper.label ?: launchable?.entry?.label ?: wrapper.packageName,
                                        icon = launchable?.icon ?: vaultIconBitmaps[wrapper.packageName] ?: vaultIconCache.load(wrapper.packageName),
                                        isFrozen = wrapper.isHidden,
                                        isAutoFreeze = wrapper.packageName in autoFreezeSet,
                                        isSystem = wrapper.isSystem,
                                        launchableApp = launchable,
                                        wrapper = wrapper,
                                    )
                                }.sortedWith(compareBy<VaultAppItem> { it.isFrozen }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })

                                vaultItems = merged
                                workProfileAppsState = if (merged.isEmpty()) WorkProfileAppLoadState.NO_APPS else WorkProfileAppLoadState.READY
                                renderVaultHeader()
                                renderDrawerApps(drawerSearch.text?.toString().orEmpty())
                                VaultControlWidgetProvider.updateStats(
                                    this@HomeActivity,
                                    merged.count { !it.isFrozen },
                                    merged.count { it.isFrozen },
                                )
                                loadMissingVaultIcons(workService, merged, request)
                            }
                        }
                    }, false)
                } catch (_: RemoteException) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && drawerShowingWorkAppsPage) {
                            workProfileAppsState = WorkProfileAppLoadState.UNAVAILABLE
                            renderVaultHeader()
                            renderDrawerApps(drawerSearch.text?.toString().orEmpty())
                        }
                    }
                }
            },
            onUnavailable = { failedState ->
                if (isFinishing || isDestroyed || !drawerShowingWorkAppsPage || request != workProfileAppsLoadVersion) {
                    return@acquire
                }
                workProfileAppsState = when (failedState) {
                    VaultProfileState.PROFILE_PAUSED -> WorkProfileAppLoadState.PROFILE_PAUSED
                    VaultProfileState.UNPROVISIONED, VaultProfileState.SETUP_PENDING -> WorkProfileAppLoadState.NO_PROFILE
                    else -> WorkProfileAppLoadState.UNAVAILABLE
                }
                renderVaultHeader()
                renderDrawerApps(drawerSearch.text?.toString().orEmpty())
                VaultControlWidgetProvider.updateAll(this)
            },
        )
    }

    private fun loadMissingVaultIcons(workService: IHavenService, items: List<VaultAppItem>, request: Int) {
        items.filter { it.icon == null && it.wrapper != null }.take(40).forEach { item ->
            val wrapper = item.wrapper ?: return@forEach
            try {
                workService.loadIcon(wrapper, object : ILoadIconCallback.Stub() {
                    override fun callback(bitmap: Bitmap?) {
                        if (bitmap == null) return
                        vaultIconCache.save(item.packageName, bitmap)
                        val drawable = bitmap.toDrawable(resources)
                        runOnUiThread {
                            if (isFinishing || isDestroyed || !drawerShowingWorkAppsPage || request != workProfileAppsLoadVersion) return@runOnUiThread
                            vaultIconBitmaps[item.packageName] = drawable
                            vaultItems = vaultItems.map { current ->
                                if (current.packageName == item.packageName && current.icon == null) current.copy(icon = drawable)
                                else current
                            }
                            renderDrawerApps(drawerSearch.text?.toString().orEmpty())
                        }
                    }
                })
            } catch (_: RemoteException) {
            }
        }
    }

    private fun setDrawerIndexState(indexMode: Boolean, filter: String?) {
        val changed = drawerIndexMode != indexMode || drawerIndexFilter != filter
        drawerIndexMode = indexMode
        drawerIndexFilter = filter
        if (changed) drawerAdapterRenderSignature = null
    }

    private fun setDrawerSearchVisible(visible: Boolean) {
        drawerSearchVisible = visible
        if (visible) {
            setDrawerIndexState(false, null)
            renderDrawerApps(drawerSearch.text.toString())
        }
        drawerSearch.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            drawerSearch.requestFocus()
            drawerSearch.post {
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(drawerSearch, 0)
            }
        } else {
            drawerSearch.text?.clear()
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(drawerSearch.windowToken, 0)
        }
    }

    private fun showDrawerMenu(anchor: View) {
        val showCategories = prefs.getBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, false)
        val showLabels = prefs.getBoolean(LauncherSettings.KEY_DRAWER_SHOW_LABELS, true)
        val sortMode = DrawerSortMode.fromPreference(prefs.getString(LauncherSettings.KEY_DRAWER_SORT_ORDER, null))
        val menuItems = buildList<Pair<String, () -> Unit>> {
            add((if (showCategories) "Hide categories" else "Show categories") to {
                prefs.edit { putBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, !showCategories) }
                setDrawerIndexState(false, null)
                selectedDrawerCategory = DrawerCategories.ALL
                renderDrawerCategories()
            })
            add((if (showLabels) "Hide app names" else "Show app names") to {
                prefs.edit { putBoolean(LauncherSettings.KEY_DRAWER_SHOW_LABELS, !showLabels) }
                renderDrawerApps(drawerSearch.text.toString())
            })
            if (!drawerShowingWorkAppsPage && hiddenAppComponents.isNotEmpty()) {
                add((if (selectedDrawerCategory == DrawerCategories.HIDDEN) "Show all apps" else "Show hidden apps") to {
                    selectedDrawerCategory = if (selectedDrawerCategory == DrawerCategories.HIDDEN) DrawerCategories.ALL else DrawerCategories.HIDDEN
                    renderDrawerCategories()
                    renderDrawerApps(drawerSearch.text.toString())
                })
            }
            add("Sorting · ${sortMode.label.removePrefix("By ")}" to { showDrawerSortingDialog() })
            if (!drawerShowingWorkAppsPage && sortMode == DrawerSortMode.USER) {
                add((if (drawerReorderMode) "Finish arranging" else "Arrange apps") to {
                    drawerReorderMode = !drawerReorderMode
                    renderDrawerCategories()
                    renderDrawerApps(drawerSearch.text.toString())
                    toast(if (drawerReorderMode) "Long-press and drag apps to reorder them" else "App order saved")
                })
            }
            add("App page settings" to { openSettings("app_page") })
            add("Manage installed apps" to {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
                    .onFailure { toast("App settings are unavailable") }
            })
            add("Launcher settings" to { openSettings() })
            if (isIsolationEnabled()) {
                add((if (drawerShowingWorkAppsPage) "All apps" else "Work profile") to {
                    if (drawerShowingWorkAppsPage) showAllAppsPage() else openWorkAppsPage()
                })
            }
        }
        showMaterialDrawerPopup(anchor, menuItems)
    }

    /** Material 3 menu surface (extra-small shape) with readable, full-size touch rows. */
    private fun showMaterialDrawerPopup(anchor: View, items: List<Pair<String, () -> Unit>>) {
        if (items.isEmpty()) return

        val palette = currentPalette()
        val radius = dp(4).toFloat()
        val menuTypeface = LauncherTypography.typeface(this, 500)
        val horizontalPadding = dp(16)
        val textPaint = TextView(this).paint.apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
            typeface = menuTypeface
        }
        val availableWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(196))
        val width = items.maxOf { textPaint.measureText(it.first).roundToInt() + horizontalPadding * 2 }
            .coerceIn(dp(196), minOf(dp(320), availableWidth))

        val menuContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            background = GradientDrawable().apply {
                setColor(palette.surfaceVariant)
                cornerRadius = radius
            }
            clipToOutline = true
            elevation = dp(3).toFloat()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val popup = PopupWindow(menuContent, width, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            elevation = dp(3).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        }
        items.forEach { (label, onSelected) ->
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = radius
            }
            val row = TextView(this).apply {
                text = label
                textSize = 16f
                typeface = menuTypeface
                setTextColor(palette.textPrimary)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(horizontalPadding, 0, horizontalPadding, 0)
                minHeight = dp(48)
                isSingleLine = true
                contentDescription = label
                isClickable = true
                isFocusable = true
                background = RippleDrawable(ColorStateList.valueOf(withAlpha(palette.accent, 36)), null, mask)
                setOnClickListener {
                    popup.dismiss()
                    onSelected()
                }
            }
            menuContent.addView(row, LinearLayout.LayoutParams(width, dp(48)))
        }
        popup.showAsDropDown(anchor, 0, dp(8), Gravity.END)
    }

    private fun openAppStore() {
        val market = Intent(Intent.ACTION_VIEW, "market://search?q=apps".toUri())
        runCatching { startActivity(market) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
                .onFailure { toast("App store is unavailable") }
        }
    }

    private fun drawerColumns() = prefs.drawerColumns()

    private fun drawerIconSize() = prefs.drawerIconSize()

    private fun drawerShowsCategories(): Boolean = !drawerShowingWorkAppsPage && (
        prefs.getBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, false) ||
            selectedDrawerCategory == DrawerCategories.HIDDEN
        )

    private fun drawerCellHeightDp(): Int =
        if (drawerIndexMode) DRAWER_INDEX_CELL_HEIGHT_DP else prefs.drawerCellHeightDp(drawerShowsCategories())

    private fun drawerVerticalSpacingDp(): Int =
        if (drawerIndexMode) DRAWER_INDEX_VERTICAL_SPACING_DP else prefs.drawerRowSpacingDp(drawerShowsCategories())

    private fun updateDrawerGridGeometry() {
        if (!::drawerGrid.isInitialized || !::drawerSpacingDecoration.isInitialized) return
        val spacing = dp(drawerVerticalSpacingDp())
        if (drawerSpacingDecoration.updateSpacing(spacing)) {
            drawerGrid.invalidateItemDecorations()
        }
        val topPadding = dp(if (drawerIndexMode) 10 else if (drawerShowsCategories()) 13 else 6)
        val bottomPadding = dp(8)
        if (drawerGrid.paddingTop != topPadding || drawerGrid.paddingBottom != bottomPadding ||
            drawerGrid.paddingLeft != 0 || drawerGrid.paddingRight != 0
        ) {
            drawerGrid.setPadding(0, topPadding, 0, bottomPadding)
        }
    }

    private fun drawerBackdrop() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xE61E263F.toInt(), 0xE620273E.toInt(), 0xE61D2337.toInt(), 0xE6171C2A.toInt()),
    )

    private fun applyDrawerBlur(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val useBlur = enabled && prefs
            .getBoolean(LauncherSettings.KEY_DRAWER_BLUR, true)
        val attributes = window.attributes
        if (useBlur) {
            attributes.flags = attributes.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            attributes.blurBehindRadius = dp(24)
        } else {
            attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            attributes.blurBehindRadius = 0
        }
        window.attributes = attributes
    }

    private fun appIconDrawable(
        icon: android.graphics.drawable.Drawable,
        highlight: Boolean,
        iconBackgroundColor: Int = currentPalette().accent,
        iconScope: String = "home",
        component: ComponentName? = null,
        workspaceItem: WorkspaceItem? = null,
        profileCacheIdentity: String? = null,
    ): android.graphics.drawable.Drawable {
        val iconDensityDpi = resources.displayMetrics.densityDpi
        val enabled = highlight && prefs.getBoolean(LauncherSettings.KEY_HIGHLIGHT_NOTIFICATIONS, true)
        val drawer = iconScope == "drawer"
        val packKey = if (drawer) LauncherSettings.KEY_ICON_PACK_DRAWER else LauncherSettings.KEY_ICON_PACK_HOME
        val adaptiveKey = if (drawer) LauncherSettings.KEY_ICON_ADAPTIVE_DRAWER else LauncherSettings.KEY_ICON_ADAPTIVE_HOME
        val shapeKey = if (drawer) LauncherSettings.KEY_ICON_SHAPE_DRAWER else LauncherSettings.KEY_ICON_SHAPE_HOME
        val source = WorkspaceIconSource.fromStorage(workspaceItem?.iconSource)
        val globalPack = prefs.getString(packKey, IconPackRepository.DEFAULT_PACK)
        val packIcon = component?.let { target ->
            when (source) {
                WorkspaceIconSource.DEFAULT -> iconPackRepository.iconFor(globalPack, target)
                WorkspaceIconSource.ICON_PACK -> iconPackRepository.iconFor(workspaceItem?.iconPackPackageName, target)
                else -> null
            }
        }
        val galleryIcon = if (source == WorkspaceIconSource.GALLERY) {
            workspaceItem?.iconUri?.let(::galleryIconDrawable)
        } else null
        val teresIcon = if (source == WorkspaceIconSource.TERES) {
            runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
        } else null
        val nonAdaptiveIcon = if (source == WorkspaceIconSource.NOT_ADAPTIVE && component != null) {
            runCatching { packageManager.getApplicationIcon(component.packageName) }.getOrNull()
        } else null
        val sourceArtwork = galleryIcon ?: packIcon ?: teresIcon ?: nonAdaptiveIcon ?: icon
        val effectivePack = if (source == WorkspaceIconSource.ICON_PACK) {
            workspaceItem?.iconPackPackageName
        } else globalPack
        val profileCacheSuffix = profileCacheIdentity?.let { "|profile:$it" }.orEmpty()
        val analysisCacheKey = when {
            galleryIcon != null -> "gallery:${workspaceItem?.iconUri}"
            packIcon != null -> "pack:$effectivePack:${component.flattenToString()}"
            teresIcon != null -> "teres:$packageName"
            nonAdaptiveIcon != null -> "nonadaptive:${component?.packageName}"
            component != null -> CircularAppIconDrawable.activityCacheKey(component)
            else -> null
        }.let { it?.plus(profileCacheSuffix)?.plus("|density:$iconDensityDpi") }
        // The App page keeps circular artwork intact and clips every other source to a circle,
        // regardless of the optional Home-screen mask preference.
        val iconShape = if (drawer) "circle" else if (prefs.getBoolean(adaptiveKey, true)) {
            prefs.getString(shapeKey, "circle") ?: "circle"
        } else "circle"
        val circleIcon = CircularAppIconDrawable(sourceArtwork, currentPalette().accent, iconBackgroundColor, iconShape, analysisCacheKey)
        return if (enabled) decorateWithNotificationDot(circleIcon) else circleIcon
    }

    private fun sharedFrozenBadgeBackground(): GradientDrawable =
        cachedFrozenBadgeBg ?: roundedDrawable(0xEE1E2D33.toInt(), dp(9)).also { cachedFrozenBadgeBg = it }

    private fun sharedAutoFreezeBadgeBackground(accentColor: Int): GradientDrawable {
        val current = cachedAutoFreezeBadgeBg
        if (current != null && cachedAutoFreezeAccent == accentColor) return current
        return roundedDrawable(accentColor, dp(9)).also {
            cachedAutoFreezeBadgeBg = it
            cachedAutoFreezeAccent = accentColor
        }
    }

    private fun sharedNotificationDotDrawable(): GradientDrawable =
        cachedNotificationDotDrawable ?: GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFF5364.toInt())
            setStroke(dp(1), 0xFF211A23.toInt())
        }.also { cachedNotificationDotDrawable = it }

    private fun decorateWithNotificationDot(base: Drawable): Drawable {
        val dot = sharedNotificationDotDrawable()
        return LayerDrawable(arrayOf(base, dot)).apply {
            setLayerGravity(1, Gravity.TOP or Gravity.END)
            setLayerWidth(1, dp(9))
            setLayerHeight(1, dp(9))
            setLayerInset(1, dp(1), dp(1), dp(1), dp(1))
        }
    }

    private fun bindDrawerPersonalIcon(
        holder: DrawerAppViewHolder,
        app: LaunchableApp,
        config: DrawerRenderConfig,
    ) {
        val myUser = Process.myUserHandle()
        val isPersonalProfileApp = app.activity.user == myUser
        val notification = isPersonalProfileApp && config.highlightNotifications &&
            app.entry.packageName in activeNotificationPackages
        val component = app.componentName
        val drawableCacheKey = "${app.activity.user.hashCode()}|${app.entry.componentKey}|${config.iconPack}|${config.accentColor}|$notification|${config.densityDpi}"
        holder.boundIconKey = drawableCacheKey
        drawerIconDrawableCache.get(drawableCacheKey)?.let { cached ->
            holder.icon.setImageDrawable(cached)
            return
        }

        val pack = config.iconPack
        val packIcon = if (pack != IconPackRepository.DEFAULT_PACK) iconPackRepository.iconFor(pack, component) else null
        val profileCacheSuffix = app.activity.user
            .takeIf { it != myUser }
            ?.hashCode()
            ?.let { "|profile:$it" }
            .orEmpty()
        val baseCacheKey = if (packIcon != null) {
            "pack:$pack:${component.flattenToString()}"
        } else {
            CircularAppIconDrawable.activityCacheKey(component)
        }
        val analysisKey = "$baseCacheKey$profileCacheSuffix|density:${config.densityDpi}"
        val bitmap = DrawerIconPipeline.getCachedBitmap(analysisKey, config.accentColor, "circle")
        if (bitmap != null) {
            val baseDrawable = CircularAppIconDrawable.fromBitmap(bitmap)
            val finalDrawable = if (notification) decorateWithNotificationDot(baseDrawable) else baseDrawable
            drawerIconDrawableCache.put(drawableCacheKey, finalDrawable)
            holder.icon.setImageDrawable(finalDrawable)
            return
        }

        val placeholder = packIcon ?: app.icon ?: packageManager.defaultActivityIcon
        holder.icon.setImageDrawable(placeholder)
        val expectedGeneration = DrawerIconPipeline.currentGeneration()
        DrawerIconPipeline.enqueueRender(
            expectedGeneration = expectedGeneration,
            cacheKey = analysisKey,
            accentColor = config.accentColor,
            shape = "circle",
            artworkProvider = { packIcon ?: app.icon ?: packageManager.defaultActivityIcon },
        ) { renderedBitmap ->
            val baseDrawable = CircularAppIconDrawable.fromBitmap(renderedBitmap)
            val finalDrawable = if (notification) decorateWithNotificationDot(baseDrawable) else baseDrawable
            drawerIconDrawableCache.put(drawableCacheKey, finalDrawable)
            if (holder.boundIconKey == drawableCacheKey) {
                holder.icon.setImageDrawable(finalDrawable)
            }
        }
    }

    private fun bindDrawerVaultIcon(
        holder: DrawerAppViewHolder,
        item: VaultAppItem,
        config: DrawerRenderConfig,
    ) {
        val drawableCacheKey = "vault|${item.packageName}|${item.className.orEmpty()}|${config.iconPack}|${config.accentColor}|${item.isFrozen}|${config.densityDpi}|${item.icon != null}|${item.packageName in vaultIconBitmaps}"
        holder.boundIconKey = drawableCacheKey
        drawerIconDrawableCache.get(drawableCacheKey)?.let { cached ->
            holder.icon.setImageDrawable(cached)
            return
        }

        val component = item.className?.let { ComponentName(item.packageName, it) }
        val pack = config.iconPack
        val packIcon = if (component != null && pack != IconPackRepository.DEFAULT_PACK) iconPackRepository.iconFor(pack, component) else null
        val baseCacheKey = when {
            packIcon != null && component != null -> "pack:$pack:${component.flattenToString()}"
            component != null -> CircularAppIconDrawable.activityCacheKey(component)
            else -> "vault:${item.packageName}"
        }
        val analysisKey = "$baseCacheKey|profile:work|${item.isFrozen}|density:${config.densityDpi}"
        val bitmap = DrawerIconPipeline.getCachedBitmap(analysisKey, config.accentColor, "circle")
        if (bitmap != null) {
            val baseDrawable = CircularAppIconDrawable.fromBitmap(bitmap)
            drawerIconDrawableCache.put(drawableCacheKey, baseDrawable)
            holder.icon.setImageDrawable(baseDrawable)
            return
        }

        val immediate = packIcon
            ?: item.icon
            ?: vaultIconBitmaps[item.packageName]
            ?: AppCompatResources.getDrawable(this@HomeActivity, R.drawable.ic_vault_shield)
            ?: packageManager.defaultActivityIcon
        holder.icon.setImageDrawable(immediate)
        val expectedGeneration = DrawerIconPipeline.currentGeneration()
        DrawerIconPipeline.enqueueRender(
            expectedGeneration = expectedGeneration,
            cacheKey = analysisKey,
            accentColor = config.accentColor,
            shape = "circle",
            artworkProvider = {
                packIcon
                    ?: item.icon
                    ?: vaultIconBitmaps[item.packageName]
                    ?: vaultIconCache.load(item.packageName)
                    ?: AppCompatResources.getDrawable(this@HomeActivity, R.drawable.ic_vault_shield)
                    ?: packageManager.defaultActivityIcon
            },
        ) { renderedBitmap ->
            val baseDrawable = CircularAppIconDrawable.fromBitmap(renderedBitmap)
            drawerIconDrawableCache.put(drawableCacheKey, baseDrawable)
            if (holder.boundIconKey == drawableCacheKey) {
                holder.icon.setImageDrawable(baseDrawable)
            }
        }
    }

    private fun drawerGridRenderSignature(): String {
        return buildString {
            append(drawerIconGeneration).append('|')
            append(appCatalogPackageSequence).append('|')
            append(drawerShowingWorkAppsPage).append('|')
            append(drawerIndexMode).append('|')
            append(drawerLabelsVisible).append('|')
            append(drawerLayoutManager.spanCount).append('|')
            append(drawerIconSize()).append('|')
            append(drawerCellHeightDp()).append('|')
            append(currentPalette().accent).append('|')
            append(currentPalette().textPrimary).append('|')
            append(prefs.getString(LauncherSettings.KEY_ICON_PACK_DRAWER, IconPackRepository.DEFAULT_PACK)).append('|')
            append(prefs.getBoolean(LauncherSettings.KEY_HIGHLIGHT_NOTIFICATIONS, true)).append('|')
            activeNotificationPackages.sorted().forEach { append(it).append(',') }
            visibleDrawerApps.forEach { app ->
                append(';').append(app.activity.user.hashCode())
                    .append(':').append(app.entry.componentKey)
                    .append(':').append(app.entry.label)
            }
            if (drawerShowingWorkAppsPage) {
                visibleVaultItems.forEach { item ->
                    append(';').append(item.packageName)
                        .append(':').append(item.isFrozen)
                        .append(':').append(item.isAutoFreeze)
                        .append(':').append(item.icon != null)
                        .append(':').append(item.label)
                }
            }
        }
    }

    private fun galleryIconDrawable(uriValue: String): BitmapDrawable? {
        val cached = importedIconCache.get(uriValue)
        if (cached != null) return cached.toDrawable(resources)
        val uri = uriValue.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 384) sampleSize *= 2
        val bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        }.getOrNull() ?: return null
        val side = minOf(bitmap.width, bitmap.height)
        val cropLeft = (bitmap.width - side) / 2
        val cropTop = (bitmap.height - side) / 2
        val crop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, side, side)
        val normalized = crop.scale(128, 128)
        if (bitmap !== normalized && !bitmap.isRecycled) bitmap.recycle()
        if (crop !== bitmap && crop !== normalized && !crop.isRecycled) crop.recycle()
        importedIconCache.put(uriValue, normalized)
        return normalized.toDrawable(resources)
    }

    private fun refreshNotificationHighlights() {
        activeNotificationPackages = if (prefs.getBoolean(LauncherSettings.KEY_HIGHLIGHT_NOTIFICATIONS, true)) {
            prefs.getStringSet(LauncherSettings.KEY_NOTIFICATION_PACKAGES, emptySet()).orEmpty().toSet()
        } else emptySet()
        if (::homeGrid.isInitialized) rebuildWorkspace()
        if (::drawerGrid.isInitialized && drawerOpen) renderDrawerApps(drawerSearch.text.toString())
    }

    private fun rebuildWorkspace() {
        if (!::homeGrid.isInitialized) return
        applyWallpaperDim()
        positionWorkspaceSections()
        homeGrid.columnCount = gridColumns
        val visibleRows = gridRows
        homeGrid.rowCount = visibleRows
        homeGrid.translationX = -dp(14).toFloat()
        homeGrid.removeAllViews()
        val pageItems = layout.items.filter { it.page == currentPage }.associateBy { it.cell }
        val iconSidePx = dp(workspaceIconSizeDp())
        for (cellIndex in 0 until gridColumns * visibleRows) {
            val cell = FrameLayout(this).apply {
                setPadding(dp(2), 0, dp(2), 0)
                clipChildren = false
                clipToPadding = false
                tag = cellIndex
                setOnDragListener { view, event -> handleCellDrag(view as ViewGroup, cellIndex, event) }
            }
            fun attachCellView(view: View, item: WorkspaceItem, app: LaunchableApp? = null, withGestures: Boolean = false, onClick: () -> Unit) {
                view.setOnClickListener { onClick() }
                if (withGestures) {
                    var menu: WorkspaceItemPopup? = null
                    bindWorkspaceItemTouch(
                        view,
                        item,
                        onLongPress = {
                            menu = if (app != null) showWorkspaceAppMenu(view, item, app)
                            else showWorkspaceFolderMenu(view, item)
                        },
                        onSwipe = { gesture -> performWorkspaceIconGesture(item, app, gesture) },
                        onDoubleTap = { performWorkspaceIconGesture(item, app, WorkspaceIconGesture.DOUBLE_TAP) },
                        showMenuWhilePressed = true,
                        onLongPressMovedToDrag = { menu?.dismiss(); menu = null },
                    )
                } else {
                    bindWorkspaceItemTouch(view, item, onLongPress = { showItemMenu(item) })
                }
                cell.addView(view, FrameLayout.LayoutParams(iconSidePx, iconSidePx, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            }
            val item = pageItems[cellIndex]
            if (item != null) {
                val shortcut = item.shortcut
                if (shortcut != null) {
                    if (shortcut.actionType == WorkspaceShortcut.ACTION_FREEZE_ALL) {
                        val icon = workspaceIconCell(
                            label = shortcut.label,
                            iconDrawable = appIconDrawable(
                                AppCompatResources.getDrawable(this, R.mipmap.ic_freeze) ?: packageManager.defaultActivityIcon,
                                highlight = false,
                                iconBackgroundColor = item.iconBackgroundColor ?: currentPalette().accent,
                                workspaceItem = item,
                                profileCacheIdentity = "work_shortcut",
                            ),
                            workspaceItem = item,
                        )
                        attachCellView(icon, item) { freezeAllAutoFreezeApps() }
                    } else if (shortcut.profile == WorkspaceShortcut.PROFILE_WORK) {
                        val activeWorkApp = workProfileApps.firstOrNull { it.entry.packageName == shortcut.packageName }
                        val cachedDrawable = activeWorkApp?.icon
                            ?: vaultIconBitmaps[shortcut.packageName]
                            ?: vaultIconCache.load(shortcut.packageName)
                            ?: AppCompatResources.getDrawable(this, R.drawable.ic_vault_shield)
                            ?: packageManager.defaultActivityIcon
                        val icon = workspaceIconCell(
                            label = shortcut.label,
                            iconDrawable = appIconDrawable(
                                cachedDrawable,
                                highlight = false,
                                iconBackgroundColor = item.iconBackgroundColor ?: currentPalette().accent,
                                workspaceItem = item,
                                profileCacheIdentity = "work_shortcut",
                            ),
                            workspaceItem = item,
                            overlayBadgeText = "❄",
                        )
                        attachCellView(icon, item) { launchUnfreezeShortcut(shortcut.packageName, shortcut.linkedPackages) }
                    } else {
                        val app = findApp(shortcut)
                        if (app != null) {
                            val icon = workspaceIconCell(
                                label = app.entry.label,
                                iconDrawable = appIconDrawable(
                                    app.icon ?: packageManager.defaultActivityIcon,
                                    app.entry.packageName in activeNotificationPackages,
                                    item.iconBackgroundColor ?: currentPalette().accent,
                                    component = ComponentName(app.entry.packageName, app.entry.className),
                                    workspaceItem = item,
                                ),
                                workspaceItem = item,
                                app = app,
                            )
                            attachCellView(icon, item, app = app, withGestures = true) { launchApp(app) }
                        } else {
                            val missing = missingShortcutCell(shortcut)
                            bindWorkspaceItemTouch(missing, item, onLongPress = { showItemMenu(item) })
                            cell.addView(missing)
                        }
                    }
                } else {
                    attachCellView(folderCell(item), item, withGestures = true) { openFolder(cell.getChildAt(0), item) }
                }
            }
            cell.background = Color.TRANSPARENT.toDrawable()
            val columnGap = dp(gridColumnSpacingDp) / 2
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(cellIndex / gridColumns),
                GridLayout.spec(cellIndex % gridColumns, 1f),
            ).apply {
                width = 0
                height = dp(HOME_GRID_ROW_HEIGHT_DP)
                leftMargin = columnGap
                rightMargin = columnGap
            }
            homeGrid.addView(cell, lp)
        }
        rebuildWidgetViews()
        updatePageIndicator()
        LauncherTypography.applyToViewTree(this, root)
    }

    private fun missingShortcutCell(shortcut: WorkspaceShortcut): View = TextView(this).apply {
        text = shortcut.label
        typeface = LauncherTypography.typeface(this@HomeActivity, 500)
        setTextColor(0xCCFFFFFF.toInt())
        gravity = Gravity.CENTER
    }

    private fun bindWorkspaceItemTouch(
        view: View,
        item: WorkspaceItem,
        onLongPress: () -> Unit,
        onSwipe: ((WorkspaceIconGesture) -> Unit)? = null,
        onDoubleTap: (() -> Unit)? = null,
        showMenuWhilePressed: Boolean = false,
        onLongPressMovedToDrag: () -> Unit = {},
    ) {
        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var tracking = false
        var armed = false
        var moved = false
        var dragStarted = false
        var swipeTriggered = false
        var menuShown = false
        val assignedGestures = item.iconGestureAssignments.map { it.gesture }.toSet()
        val doubleTapDetector = if (WorkspaceIconGesture.DOUBLE_TAP in assignedGestures) {
            GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onDoubleTap(event: MotionEvent): Boolean {
                    if (!armed && !moved) onDoubleTap?.invoke()
                    return true
                }

                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    if (!armed && !moved) view.performClick()
                    return true
                }
            }).apply { setIsLongpressEnabled(false) }
        } else null
        var downX = 0f
        var downY = 0f
        fun finishGesture() {
            handler.removeCallbacksAndMessages(null)
            view.isPressed = false
            view.parent?.requestDisallowInterceptTouchEvent(false)
            tracking = false
        }
        val armDrag = Runnable {
            if (tracking && !moved) {
                armed = true
                view.isPressed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                if (showMenuWhilePressed) {
                    menuShown = true
                    onLongPress()
                }
            }
        }
        view.isClickable = true
        view.isFocusable = true
        view.isLongClickable = false
        view.setOnTouchListener { touched, event ->
            doubleTapDetector?.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacksAndMessages(null)
                    tracking = true
                    armed = false
                    moved = false
                    dragStarted = false
                    swipeTriggered = false
                    menuShown = false
                    downX = event.rawX
                    downY = event.rawY
                    if (assignedGestures.any { it.isDirectionalSwipe }) {
                        // Keep the launcher-wide page gesture from stealing a configured icon swipe.
                        touched.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    touched.isPressed = true
                    handler.postDelayed(armDrag, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@setOnTouchListener true
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val swipeThreshold = dp(38)
                    val matchingGesture = if (onSwipe != null) {
                        assignedGestures.firstOrNull { it.matchesSwipe(dx, dy, swipeThreshold.toFloat()) }
                    } else null
                    if (!swipeTriggered && matchingGesture != null) {
                        swipeTriggered = true
                        moved = true
                        handler.removeCallbacks(armDrag)
                        onSwipe?.invoke(matchingGesture)
                    } else if (armed && !dragStarted && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        moved = true
                        if (menuShown) {
                            onLongPressMovedToDrag()
                            menuShown = false
                        }
                        dragStarted = startWorkspaceItemDrag(touched, item)
                    } else if (!armed && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        moved = true
                        handler.removeCallbacks(armDrag)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val showMenu = armed && !dragStarted && !swipeTriggered && !menuShown
                    val performClick = doubleTapDetector == null && !armed && !moved && !swipeTriggered
                    finishGesture()
                    when {
                        showMenu -> onLongPress()
                        performClick -> touched.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishGesture()
                    true
                }
                else -> true
            }
        }
    }

    private fun folderCell(item: WorkspaceItem): LinearLayout {
        val folderArtworkSize = homeIconArtworkSizeDp()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            contentDescription = item.folderLabel
            isFocusable = true
            background = selectableBackground()
        }
        val previewStyle = prefs.getString(LauncherSettings.KEY_FOLDER_PREVIEW_STYLE, "contained")
        val preview = GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            useDefaultMargins = false
            if (prefs.getBoolean(LauncherSettings.KEY_FOLDER_BACKGROUND, true)) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (item.folder.isEmpty()) {
                        // Empty folders need a visible edge against wallpaper, like Smart's empty tiles.
                        setColor(0x80FFFFFF.toInt())
                        setStroke(dp(1), 0xB3FFFFFF.toInt())
                    } else {
                        setColor(0x33FFFFFF)
                    }
                }
            }
            clipChildren = previewStyle != "unclipped"
            clipToPadding = previewStyle != "unclipped"
            clipToOutline = previewStyle == "clipped"
        }
        val previewIconSize = prefs.getInt(
            LauncherSettings.KEY_FOLDER_PREVIEW_ICON_SIZE,
            LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_DEFAULT_DP,
        ).coerceIn(
            LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_MIN_DP,
            LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_MAX_DP,
        )
        val previewSlotSize = folderArtworkSize / 2
        val previewApps = item.folder.asSequence().mapNotNull(::findApp).take(4).toList()
        fun previewIcon(app: LaunchableApp) = ImageView(this).apply {
            setImageDrawable(appIconDrawable(
                app.icon ?: packageManager.defaultActivityIcon,
                app.entry.packageName in activeNotificationPackages,
                iconScope = "home",
                component = ComponentName(app.entry.packageName, app.entry.className),
            ))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        if (previewApps.size == 1) {
            val slot = FrameLayout(this).apply {
                addView(previewIcon(previewApps.single()).apply {
                    translationX = dp(2).toFloat()
                }, FrameLayout.LayoutParams(
                    dp(previewIconSize),
                    dp(previewIconSize),
                    Gravity.CENTER,
                ))
            }
            preview.addView(slot, GridLayout.LayoutParams(
                GridLayout.spec(0, 2),
                GridLayout.spec(0, 1),
            ).apply {
                // Smart keeps a lone folder item in the left half, centered vertically.
                width = dp(previewSlotSize)
                height = dp(folderArtworkSize)
            })
        } else {
            repeat(4) { index ->
                val slot = FrameLayout(this)
                previewApps.getOrNull(index)?.let { app ->
                    slot.addView(previewIcon(app), FrameLayout.LayoutParams(
                        dp(previewIconSize),
                        dp(previewIconSize),
                        Gravity.CENTER,
                    ))
                }
                preview.addView(slot, GridLayout.LayoutParams().apply {
                    rowSpec = GridLayout.spec(index / 2)
                    columnSpec = GridLayout.spec(index % 2)
                    width = dp(previewSlotSize)
                    height = dp(previewSlotSize)
                })
            }
        }
        val previewFrame = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }
        attachDoubleTapBadge(previewFrame, layout, item.folderLabel, null, item, 56, 1)
        layout.addView(previewFrame, LinearLayout.LayoutParams(dp(folderArtworkSize), dp(folderArtworkSize)))
        layout.addView(TextView(this).apply {
            text = item.folderLabel
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = LauncherTypography.typeface(this@HomeActivity)
            gravity = Gravity.CENTER
            visibility = if (prefs.getBoolean(LauncherSettings.KEY_FOLDER_SHOW_LABELS, true)) View.VISIBLE else View.GONE
        })
        return layout
    }

    private fun startWorkspaceItemDrag(view: View, item: WorkspaceItem): Boolean {
        if (!ensureWorkspaceUnlocked()) return false
        val payload = DragPayload(item.page, item.cell)
        val label = item.shortcut?.packageName ?: item.folderLabel
        val clip = ClipData.newPlainText("workspace-item", label)
        return view.startDragAndDrop(clip, View.DragShadowBuilder(view), payload, 0)
    }

    private fun handleCellDrag(view: ViewGroup, targetCell: Int, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return !isWorkspaceLocked() && event.localState is DragPayload
            DragEvent.ACTION_DRAG_ENTERED -> view.background = roundedDrawable(withAlpha(currentPalette().accent, 0x66), dp(16))
            DragEvent.ACTION_DRAG_EXITED -> view.background = Color.TRANSPARENT.toDrawable()
            DragEvent.ACTION_DROP -> {
                view.background = Color.TRANSPARENT.toDrawable()
                val payload = event.localState as? DragPayload ?: return false
                applyDrop(payload, targetCell)
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> view.background = Color.TRANSPARENT.toDrawable()
        }
        return true
    }

    private fun applyDrop(payload: DragPayload, targetCell: Int) {
        if (!ensureWorkspaceUnlocked()) return
        if (payload.sourcePage != currentPage || payload.sourceCell == targetCell) return
        val items = layout.items.toMutableList()
        val sourceIndex = items.indexOfFirst { it.page == payload.sourcePage && it.cell == payload.sourceCell }
        if (sourceIndex < 0) return
        val targetIndex = items.indexOfFirst { it.page == currentPage && it.cell == targetCell }
        val source = items[sourceIndex]
        val target = items.getOrNull(targetIndex)
        if (target != null && target.shortcut != null && source.shortcut != null) {
            val folder = WorkspaceItem(currentPage, targetCell, folder = listOf(target.shortcut, source.shortcut))
            items.removeAt(maxOf(sourceIndex, targetIndex))
            items.removeAt(minOf(sourceIndex, targetIndex))
            items.add(folder)
        } else if (target != null && target.folder.isNotEmpty() && source.shortcut != null) {
            items.removeAt(sourceIndex)
            val freshTargetIndex = items.indexOfFirst { it.page == currentPage && it.cell == targetCell }
            if (freshTargetIndex >= 0) {
                val freshTarget = items[freshTargetIndex]
                items[freshTargetIndex] = freshTarget.copy(folder = freshTarget.folder + source.shortcut)
            }
        } else if (target != null) {
            items[sourceIndex] = source.copy(page = currentPage, cell = targetCell)
            items[targetIndex] = target.copy(page = source.page, cell = source.cell)
        } else {
            items[sourceIndex] = source.copy(page = currentPage, cell = targetCell)
        }
        layout = layout.copy(items = items)
        saveAndRender()
    }

    private fun openFolder(anchor: View, item: WorkspaceItem) {
        val dialog = Dialog(this, R.style.Theme_Teres_FullscreenDialog)
        val overlay = FrameLayout(this).apply {
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }
        overlay.addView(View(this).apply { setBackgroundColor(0xB31C2742.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), 0, dp(28), dp(if (item.folder.isEmpty()) 36 else 12))
            isClickable = true
            setOnClickListener { }
            clipChildren = false
            clipToPadding = false
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = item.folderLabel
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = "Rename ${item.folderLabel}"
            setOnClickListener { showRenameFolderDialog(item) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        val addButton = FrameLayout(this).apply {
            contentDescription = "Add apps to ${item.folderLabel}"
            isFocusable = true
            isClickable = true
            background = selectableBackground()
            addView(SettingsGlyphView(this@HomeActivity, "add_plain", Color.WHITE), FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            setOnClickListener {
                showFolderAddPicker(item) { updated ->
                    dialog.dismiss()
                    val updatedAnchor = (homeGrid.getChildAt(updated.cell) as? ViewGroup)?.getChildAt(0) ?: anchor
                    openFolder(updatedAnchor, updated)
                }
            }
        }
        header.addView(addButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        if (item.folder.isEmpty()) {
            panel.addView(TextView(this).apply {
                text = getString(R.string.folder_empty_title)
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = LauncherTypography.typeface(this@HomeActivity, 600)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(25)).apply { topMargin = dp(35) })
            panel.addView(TextView(this).apply {
                text = getString(R.string.folder_empty_subtitle)
                setTextColor(0xE6FFFFFF.toInt())
                textSize = 14f
                typeface = LauncherTypography.typeface(this@HomeActivity, 400)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(6) })
        } else {
            val columns = 4
            val grid = GridLayout(this).apply {
                columnCount = columns
                rowCount = (item.folder.size + columns - 1) / columns
                clipChildren = false
                clipToPadding = false
            }
            item.folder.mapNotNull(::findApp).forEachIndexed { index, app ->
                val appCell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    background = selectableBackground()
                    contentDescription = app.entry.label
                    val icon = ImageView(this@HomeActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(appIconDrawable(
                            app.icon ?: packageManager.defaultActivityIcon,
                            highlight = false,
                            iconScope = "home",
                            component = app.componentName,
                        ))
                        contentDescription = app.entry.label
                    }
                    addView(icon, LinearLayout.LayoutParams(dp(76), dp(76)))
                    addView(TextView(this@HomeActivity).apply {
                        text = app.entry.label
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        typeface = LauncherTypography.typeface(this@HomeActivity, 500)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }, LinearLayout.LayoutParams(-1, dp(22)).apply { topMargin = dp(4) })
                    setOnClickListener {
                        dialog.dismiss()
                        runCatching { catalog.start(app); recordAppLaunch(app) }
                            .onFailure { toast("This app could not be opened") }
                    }
                    setOnLongClickListener {
                        confirmRemoval(app.entry.label, "Remove this app from ${item.folderLabel}?", requireUnlocked = false) {
                            val updated = updateWorkspaceItem(item) { current ->
                                current.copy(folder = current.folder.filterNot {
                                    it.packageName == app.entry.packageName && it.className == app.entry.className
                                })
                            }
                            dialog.dismiss()
                            if (updated != null) openFolder((homeGrid.getChildAt(updated.cell) as? ViewGroup)?.getChildAt(0) ?: anchor, updated)
                        }
                        true
                    }
                }
                grid.addView(appCell, GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns),
                ).apply {
                    width = (resources.displayMetrics.widthPixels - dp(10)) / columns
                    height = dp(112)
                })
            }
            grid.setPadding(dp(10), 0, 0, 0)
            panel.addView(grid, LinearLayout.LayoutParams(resources.displayMetrics.widthPixels, -2).apply {
                topMargin = dp(8)
                leftMargin = -dp(28)
                rightMargin = -dp(28)
            })
        }

        val panelParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        overlay.addView(panel, panelParams)
        activeFolderDialogCount += 1
        root.alpha = 0f
        dialog.setOnDismissListener {
            activeFolderDialogCount = (activeFolderDialogCount - 1).coerceAtLeast(0)
            root.alpha = if (activeFolderDialogCount == 0) 1f else 0f
        }
        dialog.setContentView(overlay)
        dialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                WindowCompat.setDecorFitsSystemWindows(this, false)
                attributes = attributes.apply {
                    dimAmount = 0.18f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        blurBehindRadius = dp(22)
                    }
                }
                WindowInsetsControllerCompat(this, decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            overlay.post {
                if (dialog.isShowing) {
                    val anchorLocation = IntArray(2)
                    val overlayLocation = IntArray(2)
                    anchor.getLocationOnScreen(anchorLocation)
                    overlay.getLocationOnScreen(overlayLocation)
                    val panelHeight = panel.height.takeIf { it > 0 } ?: dp(168)
                    val preferredTop = anchorLocation[1] - overlayLocation[1] - dp(116)
                    panelParams.topMargin = preferredTop.coerceIn(
                        dp(24),
                        (overlay.height - panelHeight - dp(24)).coerceAtLeast(dp(24)),
                    )
                    panel.layoutParams = panelParams
                }
            }
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    private fun showFolderAddPicker(item: WorkspaceItem, onAdded: (WorkspaceItem) -> Unit) {
        if (!ensureWorkspaceUnlocked()) return
        val existing = item.folder.mapTo(mutableSetOf()) { "${it.packageName}/${it.className}" }
        val available = apps.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
            .filterNot { it.entry.componentKey in existing }
        if (available.isEmpty()) {
            toast("All available apps are already in this folder")
            return
        }

        val dialog = Dialog(this, R.style.Theme_Teres_FullscreenDialog)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(0, dp(56), 0, dp(40))
        }
        val back = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Back to ${item.folderLabel}"
            addView(SettingsGlyphView(this@HomeActivity, "arrow_back", 0xFF202127.toInt()), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(back, LinearLayout.LayoutParams(dp(56), dp(48)).apply { leftMargin = dp(12) })
        root.addView(TextView(this).apply {
            text = getString(R.string.folder_add_icon_title)
            setTextColor(0xFF17181C.toInt())
            textSize = 26f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { leftMargin = dp(24); rightMargin = dp(24); topMargin = dp(10) })

        val search = EditText(this).apply {
            hint = "Search"
            setSingleLine(true)
            setTextColor(0xFF202127.toInt())
            setHintTextColor(0xFF777B84.toInt())
            textSize = 16f
            typeface = LauncherTypography.typeface(this@HomeActivity, 400)
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedDrawable(0xFFF3F5F8.toInt(), dp(28))
            contentDescription = "Search apps to add to ${item.folderLabel}"
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { leftMargin = dp(24); rightMargin = dp(24); topMargin = dp(12) })
        val appsTab = TextView(this).apply {
            text = getString(R.string.apps_title)
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER
            background = roundedDrawable(0xFF0866FF.toInt(), dp(28))
        }
        root.addView(appsTab, LinearLayout.LayoutParams(dp(70), dp(40)).apply { leftMargin = dp(24); topMargin = dp(14) })
        root.addView(TextView(this).apply {
            text = getString(R.string.folder_add_all_apps)
            setTextColor(0xFF8B8F98.toInt())
            textSize = 13f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { leftMargin = dp(24); topMargin = dp(18) })

        val selected = linkedSetOf<String>()
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(rows)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val add = TextView(this).apply {
            text = getString(R.string.action_add)
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            gravity = Gravity.CENTER
            background = roundedDrawable(0xFF0866FF.toInt(), dp(28))
            visibility = View.GONE
            isClickable = true
        }
        root.addView(add, LinearLayout.LayoutParams(-1, dp(48)).apply { leftMargin = dp(24); rightMargin = dp(24); topMargin = dp(8) })

        fun renderRows(query: String) {
            rows.removeAllViews()
            available.filter { it.entry.label.contains(query, ignoreCase = true) }.forEach { app ->
                val selectedNow = app.entry.componentKey in selected
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(24), 0, dp(20), 0)
                    isClickable = true
                    isFocusable = true
                    background = selectableBackground()
                    contentDescription = "${app.entry.label}, ${if (selectedNow) "selected" else "not selected"}"
                    val icon = ImageView(this@HomeActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(appIconDrawable(
                            app.icon ?: packageManager.defaultActivityIcon,
                            highlight = false,
                            iconScope = "home",
                            component = app.componentName,
                        ))
                    }
                    addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(TextView(this@HomeActivity).apply {
                        text = app.entry.label
                        setTextColor(0xFF191A1E.toInt())
                        textSize = 16f
                        typeface = LauncherTypography.typeface(this@HomeActivity, 500)
                        gravity = Gravity.CENTER_VERTICAL
                    }, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(20) })
                    addView(android.widget.CheckBox(this@HomeActivity).apply {
                        isChecked = selectedNow
                        buttonTintList = ColorStateList.valueOf(0xFF0866FF.toInt())
                        isClickable = false
                        isFocusable = false
                    }, LinearLayout.LayoutParams(dp(48), dp(48)))
                    setOnClickListener {
                        if (selectedNow) selected.remove(app.entry.componentKey) else selected.add(app.entry.componentKey)
                        add.visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
                        renderRows(search.text?.toString().orEmpty())
                    }
                }
                rows.addView(row, LinearLayout.LayoutParams(-1, dp(64)))
            }
        }
        add.setOnClickListener {
            val selectedApps = available.filter { it.entry.componentKey in selected }
            if (selectedApps.isEmpty()) return@setOnClickListener
            val updated = updateWorkspaceItem(item) { current ->
                current.copy(folder = (current.folder + selectedApps.map { it.entry.toShortcut() }).distinctBy {
                    it.packageName to it.className
                })
            } ?: return@setOnClickListener
            dialog.dismiss()
            onAdded(updated)
        }
        renderRows("")
        search.doOnTextChanged { s, _, _, _ -> renderRows(s?.toString().orEmpty()) }

        dialog.setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, maxOf(dp(56), bars.top + dp(12)), 0, maxOf(dp(40), bars.bottom, ime.bottom))
            insets
        }
        dialog.showFullscreen(Color.WHITE, lightSystemBars = true)
    }

    private fun showItemMenu(item: WorkspaceItem) {
        AlertDialog.Builder(this)
            .setTitle(item.shortcut?.label ?: getString(R.string.folder_label))
            .setItems(arrayOf("Remove from Home", "App info")) { _, which ->
                if (which == 0) {
                    if (!ensureWorkspaceUnlocked()) return@setItems
                    layout = layout.copy(items = layout.items - item)
                    saveAndRender()
                } else item.shortcut?.let { showAppInfo(it.packageName) }
            }.show()
    }

    private fun showWorkspaceFolderMenu(anchor: View, item: WorkspaceItem): WorkspaceItemPopup {
        return WorkspaceItemPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            heading = item.folderLabel,
            actions = listOf(
                WorkspacePopupAction(
                    label = "Gestures",
                    glyph = "gesture",
                    subtitle = item.gestureSummary(item.folderLabel),
                ) { showWorkspaceGestureEditor(item) },
                WorkspacePopupAction("Rename folder", "edit") { showRenameFolderDialog(item) },
                WorkspacePopupAction("Remove folder", "remove", destructive = true) {
                    confirmRemoveWorkspaceItem(item, "folder")
                },
            ),
        ).show()
    }

    private fun showWorkspaceAppMenu(anchor: View, item: WorkspaceItem, app: LaunchableApp): WorkspaceItemPopup? {
        val shortcut = item.shortcut ?: return null
        return WorkspaceItemPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            heading = shortcut.label,
            subtitle = "Home shortcut",
            actions = listOf(
                WorkspacePopupAction(
                    label = "Gestures",
                    glyph = "gesture",
                    subtitle = item.gestureSummary(shortcut.label),
                ) { showWorkspaceGestureEditor(item) },
                WorkspacePopupAction("Edit icon", "edit_icon") { editWorkspaceIcon(item, app) },
                WorkspacePopupAction("Rename", "edit") { showRenameShortcutDialog(item) },
                WorkspacePopupAction("Remove from Home", "remove", destructive = true) { confirmRemoveWorkspaceItem(item, "icon") },
            ),
            onInfo = { showAppInfo(shortcut.packageName) },
        ).show()
    }

    private fun showWorkspaceGestureEditor(item: WorkspaceItem) {
        if (!ensureWorkspaceUnlocked()) return
        currentGestureItem = item
        val label = item.shortcut?.label ?: item.folderLabel.takeIf { item.isFolder } ?: "App"
        val editor = WorkspaceGestureEditorDialog(
            activity = this,
            palette = currentPalette(),
            iconLabel = label,
            initialAssignments = item.iconGestureAssignments,
            onChooseAction = { gesture, current ->
                showWorkspaceGestureActionPicker(gesture, current)
            },
            onDismiss = {
                workspaceGestureEditor = null
                currentGestureItem = null
            },
        )
        workspaceGestureEditor = editor
        editor.show()
    }

    private fun showWorkspaceGestureActionPicker(
        gesture: WorkspaceIconGesture,
        current: WorkspaceIconGestureAssignment?,
    ) {
        WorkspaceGestureActionPickerDialog(
            activity = this,
            palette = currentPalette(),
            catalog = catalog,
            gesture = gesture,
            appsInitiallyLoaded = apps,
            current = current,
            onSelect = { assignment -> updateWorkspaceGestureAssignment(gesture, assignment) },
        ).show()
    }

    private fun updateWorkspaceGestureAssignment(
        gesture: WorkspaceIconGesture,
        assignment: WorkspaceIconGestureAssignment,
    ) {
        if (!ensureWorkspaceUnlocked()) return
        // The selected icon may have no assignment yet, so use the item that opened this editor.
        val selectedItem = currentGestureItem ?: run {
            toast("This Home icon is no longer available")
            return
        }
        val updated = updateWorkspaceItem(selectedItem) { it.withGestureAssignment(assignment) } ?: return
        workspaceGestureEditor?.refresh(updated.iconGestureAssignments)
        if (assignment.action == WorkspaceIconGestureAction.NONE) toast("${gesture.label} action cleared")
    }

    private var currentGestureItem: WorkspaceItem? = null

    private fun performWorkspaceIconGesture(
        item: WorkspaceItem,
        app: LaunchableApp?,
        gesture: WorkspaceIconGesture,
    ) {
        val assignment = item.iconGestureAssignments.firstOrNull { it.gesture == gesture } ?: return
        val targetApp = app ?: item.folder.firstNotNullOfOrNull(::findApp)
        performGestureAssignment(assignment, targetApp)
    }

    private fun performGestureAssignment(
        assignment: WorkspaceIconGestureAssignment,
        app: LaunchableApp? = null,
    ) {
        when (assignment.action) {
            WorkspaceIconGestureAction.NONE -> Unit
            WorkspaceIconGestureAction.POPUP_WIDGET -> showBatteryPopup()
            WorkspaceIconGestureAction.OPEN_APP -> app?.let {
                runCatching { catalog.start(it) }.onFailure { toast("This app could not be opened") }
            }
            WorkspaceIconGestureAction.APP_DRAWER -> openDrawer()
            WorkspaceIconGestureAction.TOGGLE_DOCK -> {
                val settings = prefs
                val visible = !prefs.getBoolean(LauncherSettings.KEY_DOCK_VISIBLE, true)
                prefs.edit { putBoolean(LauncherSettings.KEY_DOCK_VISIBLE, visible) }
                homeDock.visibility = if (visible) View.VISIBLE else View.GONE
            }
            WorkspaceIconGestureAction.SEARCH -> openTeresSearch()
            WorkspaceIconGestureAction.ASSISTANT -> {
                val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(assist) }.onFailure { toast("No digital assistant is available") }
            }
            WorkspaceIconGestureAction.SETTINGS -> openSettings()
            WorkspaceIconGestureAction.OPEN_VAULT -> openDrawerToVault()
            WorkspaceIconGestureAction.FREEZE_ALL_WORK -> freezeAllAutoFreezeApps()
            WorkspaceIconGestureAction.TOGGLE_WORK_PROFILE -> toggleWorkProfilePaused()
            WorkspaceIconGestureAction.APP_INDEX -> openDrawer(focusSearch = true)
            WorkspaceIconGestureAction.NEXT_HOME_PAGE -> moveToPage(currentPage + 1)
            WorkspaceIconGestureAction.PREVIOUS_HOME_PAGE -> moveToPage(currentPage - 1)
            WorkspaceIconGestureAction.NOTIFICATIONS ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            WorkspaceIconGestureAction.RECENTS ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            WorkspaceIconGestureAction.QUICK_SETTINGS ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            WorkspaceIconGestureAction.LOCK_SCREEN ->
                performSystemAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            WorkspaceIconGestureAction.LAUNCH_APP -> launchGestureTargetApp(assignment)
            WorkspaceIconGestureAction.LAUNCH_SHORTCUT -> launchGestureTargetShortcut(assignment)
        }
    }

    private fun launchGestureTargetApp(assignment: WorkspaceIconGestureAssignment) {
        val targetPackage = assignment.targetPackage
        val targetClass = assignment.targetClass
        if (targetPackage.isNullOrBlank() || targetClass.isNullOrBlank()) {
            toast("Choose an app for this gesture")
            return
        }
        fun launchFrom(candidates: List<LaunchableApp>): Boolean {
            val target = candidates.firstOrNull {
                it.entry.packageName == targetPackage && it.entry.className == targetClass
            } ?: return false
            val launched = runCatching { catalog.start(target) }.isSuccess
            if (launched) recordAppLaunch(target)
            return launched
        }
        if (!launchFrom(apps)) {
            catalog.loadAsync { refreshed ->
                if (isFinishing || isDestroyed) return@loadAsync
                apps = refreshed
                if (!launchFrom(refreshed)) toast("${assignment.targetLabel ?: "The selected app"} is no longer available")
            }
        }
    }

    private fun launchGestureTargetShortcut(assignment: WorkspaceIconGestureAssignment) {
        if (!catalog.hasShortcutHostPermission()) {
            toast("Select Teres Launcher as Home to launch app shortcuts")
            return
        }
        val packageName = assignment.targetPackage ?: return
        val shortcutId = assignment.targetShortcutId ?: return
        catalog.loadSearchShortcutsAsync("") { entries ->
            if (isFinishing || isDestroyed) return@loadSearchShortcutsAsync
            val target = entries.firstOrNull {
                it.shortcut.`package` == packageName && it.shortcut.id == shortcutId
            }
            if (target == null) toast("${assignment.targetLabel ?: "The shortcut"} is no longer available")
            else runCatching {
                catalog.start(target.shortcut, null)
                apps.firstOrNull { it.entry.packageName == packageName }?.let(::recordAppLaunch)
            }
                .onFailure { toast("The shortcut could not be opened") }
        }
    }

    private fun chooseIconBackground(item: WorkspaceItem) {
        if (!ensureWorkspaceUnlocked()) return
        val colors = arrayOf(
            "Automatic" to null,
            "Teal" to 0xFF3CA89B.toInt(),
            "Blue" to 0xFF4385F5.toInt(),
            "Violet" to 0xFF8756C5.toInt(),
            "Coral" to 0xFFE56D5B.toInt(),
            "Amber" to 0xFFD59A25.toInt(),
        )
        val current = colors.indexOfFirst { it.second == item.iconBackgroundColor }.coerceAtLeast(0)
        showSingleChoiceDialog("Icon background", colors.map { it.first }.toTypedArray(), current) { index ->
            updateWorkspaceItem(item) { it.copy(iconBackgroundColor = colors[index].second) }
            if (item.iconBackgroundColor == null) toast("Existing circular icons keep their original artwork")
        }
    }

    private fun editWorkspaceIcon(item: WorkspaceItem, app: LaunchableApp) {
        if (!ensureWorkspaceUnlocked()) return
        val component = ComponentName(app.entry.packageName, app.entry.className)
        val preview = appIconDrawable(
            app.icon ?: packageManager.defaultActivityIcon,
            highlight = false,
            iconBackgroundColor = item.iconBackgroundColor ?: currentPalette().accent,
            component = component,
            workspaceItem = item,
        )
        val teresArtwork = runCatching { packageManager.getApplicationIcon(packageName) }
            .getOrDefault(packageManager.defaultActivityIcon)
        WorkspaceIconEditorDialog(
            activity = this,
            label = item.shortcut?.label ?: app.entry.label,
            preview = preview,
            teresArtwork = teresArtwork,
            onSelect = { source ->
                if (source != WorkspaceIconSource.GALLERY && source != WorkspaceIconSource.ICON_PACK) {
                    updateWorkspaceItem(item) {
                        it.copy(
                            iconSource = source.storageKey,
                            iconUri = null,
                            iconPackPackageName = null,
                        )
                    }
                }
            },
            onGallery = {
                if (ensureWorkspaceUnlocked()) {
                    pendingIconGalleryItem = item
                    galleryIconLauncher.launch(arrayOf("image/*"))
                }
            },
            onInstalledPacks = { chooseIconPackForWorkspaceItem(item) },
            onBackgroundColor = { chooseIconBackground(item) },
            onIconPackStudio = { launchIconPackStudio() },
        ).show()
    }

    private fun chooseIconPackForWorkspaceItem(item: WorkspaceItem) {
        if (!ensureWorkspaceUnlocked()) return
        val packs = listOf(IconPackRepository.DEFAULT_PACK to "Default") + iconPackRepository.availablePacks()
        val selectedPackage = item.iconPackPackageName ?: IconPackRepository.DEFAULT_PACK
        val selected = packs.indexOfFirst { it.first == selectedPackage }.coerceAtLeast(0)
        showSingleChoiceDialog("Icon pack for ${item.shortcut?.label ?: "app"}", packs.map { it.second }.toTypedArray(), selected) { index ->
            val packageName = packs[index].first
            updateWorkspaceItem(item) {
                it.copy(
                    iconSource = if (packageName == IconPackRepository.DEFAULT_PACK) WorkspaceIconSource.DEFAULT.storageKey else WorkspaceIconSource.ICON_PACK.storageKey,
                    iconUri = null,
                    iconPackPackageName = packageName.takeUnless { it == IconPackRepository.DEFAULT_PACK },
                )
            }
        }
    }

    private fun launchIconPackStudio() {
        val studio = apps.firstOrNull { it.entry.label.contains("icon pack studio", ignoreCase = true) }
        if (studio == null) {
            toast("Icon Pack Studio is not installed")
            return
        }
        runCatching { catalog.start(studio) }
            .onFailure { toast("Icon Pack Studio could not be opened") }
    }

    private fun showRenameShortcutDialog(item: WorkspaceItem) {
        val shortcut = item.shortcut ?: return
        showRenameDialog("Rename icon", shortcut.label, "Icon name") { entered ->
            val label = entered ?: shortcut.label
            updateWorkspaceItem(item) { it.copy(shortcut = shortcut.copy(label = label)) }
        }
    }

    private fun confirmRemoveWorkspaceItem(item: WorkspaceItem, label: String) {
        confirmRemoval("Remove $label", "Remove ${item.shortcut?.label ?: item.folderLabel} from Home?") {
            val updated = layout.copy(items = layout.items.filterNot { it.page == item.page && it.cell == item.cell })
            if (workspaceStore.saveImmediately(updated)) {
                layout = updated
                rebuildWorkspace()
            } else {
                toast("The Home icon could not be removed. Please try again.")
            }
        }
    }

    private fun updateWorkspaceItem(item: WorkspaceItem, update: (WorkspaceItem) -> WorkspaceItem): WorkspaceItem? {
        if (!ensureWorkspaceUnlocked()) return null
        var updatedItem: WorkspaceItem? = null
        layout = layout.copy(items = layout.items.map { current ->
            if (current.page == item.page && current.cell == item.cell) update(current).also { updatedItem = it } else current
        })
        saveAndRender()
        return updatedItem
    }

    private fun showRenameFolderDialog(item: WorkspaceItem) {
        showRenameDialog("Rename folder", item.folderLabel, "Folder name") { label ->
            if (label == null) toast("Enter a folder name")
            else updateWorkspaceItem(item) { current -> if (current.isFolder) current.copy(folderLabel = label) else current }
        }
    }

    private fun addToWorkspace(app: LaunchableApp) {
        if (!ensureWorkspaceUnlocked()) return
        val cell = firstEmptyWorkspaceCell()
        if (cell == null) {
            toast("This 4 × 6 icon area is full. Add a Home page or remove an icon.")
            closeDrawer()
            return
        }
        layout = layout.copy(items = layout.items + WorkspaceItem(currentPage, cell, app.entry.toShortcut()))
        saveAndRender()
        closeDrawer()
        toast("Added ${app.entry.label} to Home")
    }

    private fun refreshApps() {
        if (isFinishing || isDestroyed) return
        if (appCatalogRefreshInFlight) {
            appCatalogRefreshQueued = true
            return
        }
        appCatalogRefreshInFlight = true
        val requestVersion = ++appRefreshVersion
        val packageSequenceAtRefreshStart = currentPackageChangeSequence()
        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed || requestVersion != appRefreshVersion) {
                appCatalogRefreshInFlight = false
                return@loadAsync
            }
            appCatalogRefreshInFlight = false
            apps = loaded
            appCatalogLoaded = true
            val completedPackageSequence = currentPackageChangeSequence()
            appCatalogPackageSequence = completedPackageSequence
            if (packageSequenceAtRefreshStart != null && completedPackageSequence != null &&
                packageSequenceAtRefreshStart != completedPackageSequence
            ) {
                // A package changed while getActivityList/getIcon was running; make one clean pass.
                appCatalogRefreshQueued = true
            }
            val launcherPreferences = prefs
            hiddenAppComponents = launcherPreferences
                .getStringSet(LauncherSettings.KEY_HIDDEN_APPS, emptySet())
                .orEmpty()
                .toSet()
            // Fresh installs start with the simplified home workspace (dock + search only, no pre-seeded grid icons).
            if (!workspaceStore.hasSavedLayout()) {
                workspaceStore.save(layout)
            }
            addNewlyInstalledApps(loaded, launcherPreferences)
            if (::homeGrid.isInitialized) {
                renderDrawerCategories()
                rebuildWorkspace()
                renderHomeDock()
                val pack = launcherPreferences.getString(
                    LauncherSettings.KEY_ICON_PACK_DRAWER,
                    IconPackRepository.DEFAULT_PACK,
                ) ?: IconPackRepository.DEFAULT_PACK
                if (pack != IconPackRepository.DEFAULT_PACK && loaded.isNotEmpty()) {
                    DrawerIconPipeline.prewarmAllAsync(
                        apps = loaded,
                        iconDensityDpi = resources.displayMetrics.densityDpi,
                        accent = currentPalette().accent,
                        expectedGeneration = DrawerIconPipeline.currentGeneration(),
                        packResolver = { component -> iconPackRepository.iconFor(pack, component) },
                    )
                }
                if (drawerOpen) {
                    if (drawerShowingWorkAppsPage && isIsolationEnabled()) loadWorkProfileApps()
                    else renderDrawerApps(drawerSearch.text.toString())
                }
            }
            if (appCatalogRefreshQueued) {
                appCatalogRefreshQueued = false
                refreshApps()
            }
        }
    }

    /**
     * Package callbacks keep the catalog current while this launcher is started. Android does
     * not deliver those callbacks while the activity is stopped, so reconcile the package
     * sequence on return instead of reloading and rasterizing every app icon on every resume.
     */
    private fun refreshAppsIfPackageStateChanged() {
        if (!appCatalogLoaded) {
            refreshApps()
            return
        }
        if (appCatalogRefreshInFlight) return
        val previousSequence = appCatalogPackageSequence
        if (previousSequence == null) {
            refreshApps()
            return
        }
        val changedPackages = runCatching {
            packageManager.getChangedPackages(previousSequence)
        }.getOrElse {
            refreshApps()
            return
        }
        if (changedPackages != null) {
            clearDrawerIconCaches()
            refreshApps()
        }
    }

    private fun currentPackageChangeSequence(): Int? = runCatching {
        packageManager.getChangedPackages(0)?.sequenceNumber ?: 0
    }.getOrNull()

    private fun refreshAppsAfterPackageChange() {
        clearDrawerIconCaches()
        refreshApps()
    }

    private fun clearDrawerIconCaches() {
        DrawerIconPipeline.invalidateGeneration()
        drawerIconDrawableCache.evictAll()
        CircularAppIconDrawable.clearAnalysisCache()
        drawerIconGeneration++
    }

    private fun addNewlyInstalledApps(
        loadedApps: List<LaunchableApp>,
        preferences: android.content.SharedPreferences,
    ) {
        val installedPackages = loadedApps.mapTo(mutableSetOf()) { it.entry.packageName }
        val previousPackages = prefs.getStringSet(
            LauncherSettings.KEY_KNOWN_LAUNCHER_APP_PACKAGES,
            null,
        )
        if (previousPackages != null && prefs.getBoolean(LauncherSettings.KEY_AUTO_ADD_NEW_APPS, false)) {
            val dockPackages = homeDockApps().mapTo(mutableSetOf()) { it.entry.packageName }
            val newlyInstalledApps = loadedApps
                .filter { it.entry.packageName !in previousPackages }
                .distinctBy { it.entry.packageName }
                .filterNot { it.entry.packageName in dockPackages }
            var updatedLayout = layout
            var updatedPageCount = workspacePageCount()
            newlyInstalledApps.forEach { app ->
                val placement = WorkspacePlacement.placeNewShortcut(
                    layout = updatedLayout,
                    shortcut = app.entry.toShortcut(),
                    pageCount = updatedPageCount,
                    columns = gridColumns,
                    rows = gridRows,
                    maxPages = LauncherSettings.MAX_WORKSPACE_PAGES,
                    excludedPackages = dockPackages,
                ) ?: return@forEach
                updatedLayout = placement.layout
                updatedPageCount = placement.pageCount
            }
            if (updatedLayout != layout) {
                layout = updatedLayout
                workspaceStore.save(layout)
                prefs.edit { putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, updatedPageCount) }
            }
        }
        prefs.edit { putStringSet(LauncherSettings.KEY_KNOWN_LAUNCHER_APP_PACKAGES, installedPackages) }
    }

    private fun homeDockApps(): List<LaunchableApp> {
        val configuredPackages = prefs.getString(LauncherSettings.KEY_DOCK_APP_PACKAGES, null)
        val byPackage = apps.groupBy { it.entry.packageName }
        if (configuredPackages != null) {
            return configuredPackages.split(DOCK_PACKAGE_SEPARATOR)
                .filter(String::isNotBlank)
                .distinct()
                .mapNotNull { byPackage[it]?.firstOrNull() }
                .take(4)
        }

        val packageOrder = listOf(
            "com.google.android.dialer", "com.android.dialer",
            "com.google.android.apps.youtube.music",
            "com.android.chrome", "com.google.android.apps.photos",
            CAMERA_PACKAGE_ANDROID, CAMERA_PACKAGE_GOOGLE,
        )
        val preferred = packageOrder.mapNotNull { byPackage[it]?.firstOrNull() }
            .distinctBy { it.entry.packageName }.take(4)
        val defaults = (preferred + apps.filterNot { app ->
            preferred.any { it.entry.packageName == app.entry.packageName }
        }).distinctBy { it.entry.packageName }.take(4)
        if (defaults.isNotEmpty()) {
            prefs.edit { putString(LauncherSettings.KEY_DOCK_APP_PACKAGES, defaults.joinToString(DOCK_PACKAGE_SEPARATOR) { it.entry.packageName }) }
        }
        return defaults
    }

    private fun renderHomeDock() {
        if (!::homeDock.isInitialized) return
        homeDock.visibility = if (prefs.getBoolean(LauncherSettings.KEY_DOCK_VISIBLE, true)) View.VISIBLE else View.GONE
        val iconSizeDp = homeIconSizeDp()
        val dockHeightDp = maxOf(HOME_DOCK_MIN_HEIGHT_DP, iconSizeDp)
        (homeDock.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != dp(dockHeightDp)) {
                params.height = dp(dockHeightDp)
                homeDock.layoutParams = params
            }
        }
        homeDock.removeAllViews()
        homeDockApps().forEach { app ->
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(appIconDrawable(
                    app.icon ?: packageManager.defaultActivityIcon,
                    app.entry.packageName in activeNotificationPackages,
                    iconScope = "home",
                    component = ComponentName(app.entry.packageName, app.entry.className),
                ))
                contentDescription = app.entry.label
                isFocusable = true
                background = selectableBackground()
                setOnClickListener {
                    launchApp(app)
                }
                setOnLongClickListener { showDockAppMenu(this, app); true }
            }
            val slot = FrameLayout(this).apply {
                clipChildren = false
                clipToPadding = false
                val iconSize = dp(iconSizeDp)
                addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            }
            homeDock.addView(slot, LinearLayout.LayoutParams(0, dp(dockHeightDp), 1f))
        }
    }

    private fun showDockAppMenu(anchor: View, app: LaunchableApp) {
        if (!ensureWorkspaceUnlocked()) return
        WorkspaceItemPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            heading = app.entry.label,
            subtitle = "Dock shortcut",
            artwork = appIconDrawable(
                app.icon ?: packageManager.defaultActivityIcon,
                app.entry.packageName in activeNotificationPackages,
                iconScope = "home",
                component = ComponentName(app.entry.packageName, app.entry.className),
            ),
            actions = listOf(
                WorkspacePopupAction("Remove from dock", "remove", destructive = true) {
                    confirmRemoveDockApp(app)
                },
            ),
            onInfo = { showAppInfo(app.entry.packageName) },
        ).show()
    }

    private fun confirmRemoveDockApp(app: LaunchableApp) {
        confirmRemoval("Remove from dock", "Remove ${app.entry.label} from the dock? The app will remain available on the app page.") {
            val remainingPackages = homeDockApps().map { it.entry.packageName }.filterNot { it == app.entry.packageName }
            val saved = prefs.edit().putString(LauncherSettings.KEY_DOCK_APP_PACKAGES, remainingPackages.joinToString(DOCK_PACKAGE_SEPARATOR)).commit()
            if (saved) renderHomeDock() else toast("The dock change could not be saved")
        }
    }

    private fun applyHomeSearchVisibility() {
        if (!::searchButton.isInitialized) return
        searchButton.visibility = if (prefs.getBoolean(LauncherSettings.KEY_SEARCH_VISIBLE_HOME, true)) View.VISIBLE else View.GONE
    }

    private fun showSearchButtonMenu(anchor: View) {
        if (!ensureWorkspaceUnlocked()) return
        WorkspaceItemPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            heading = "Haven Search",
            subtitle = "Search apps, contacts, media, calendar, shortcuts, and web.",
            actions = listOf(
                WorkspacePopupAction("Remove Search", "remove", destructive = true) { confirmRemoveSearchButton() },
            ),
        ).show()
    }

    private fun confirmRemoveSearchButton() {
        confirmRemoval("Remove Teres Search", "Remove the Search button from Home? You can show it again in Teres Search settings.") {
            prefs.edit(commit = true) { putBoolean(LauncherSettings.KEY_SEARCH_VISIBLE_HOME, false) }
            applyHomeSearchVisibility()
        }
    }

    private fun renderFeedSideButton() {
        if (!::feedSideButton.isInitialized) return
        feedSideButton.visibility = if (prefs.getBoolean(LauncherSettings.KEY_BOTTOM_BUTTONS, false)) View.VISIBLE else View.GONE
    }

    private fun launchVaultItem(item: VaultAppItem) {
        if (prefs.getBoolean(LauncherSettings.KEY_DRAWER_CLOSE_AFTER_LAUNCH, false)) closeDrawer()
        if (item.isFrozen || item.isAutoFreeze || item.launchableApp == null) {
            launchUnfreezeShortcut(item.packageName)
        } else {
            runCatching { catalog.start(item.launchableApp) }
                .onFailure { launchUnfreezeShortcut(item.packageName) }
        }
    }

    private fun launchUnfreezeShortcut(packageName: String, linkedPackages: List<String> = emptyList()) {
        val intent = Intent(DummyActivity.UNFREEZE_AND_LAUNCH).apply {
            component = ComponentName(this@HomeActivity, DummyActivity::class.java)
            putExtra("packageName", packageName)
            if (linkedPackages.isNotEmpty()) {
                putExtra("linkedPackages", linkedPackages.joinToString(","))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        DummyActivity.registerSameProcessRequest(intent)
        runCatching { startActivity(intent) }
            .onFailure { toast("Could not launch $packageName in Haven") }
    }

    private fun freezeAllAutoFreezeApps() {
        if (!isIsolationEnabled()) {
            toast(getString(R.string.mode_isolation_disabled_hint))
            return
        }
        val state = HavenProfileSession.inspectState(this)
        if (state == VaultProfileState.UNPROVISIONED || state == VaultProfileState.SETUP_PENDING) {
            toast("Set up Haven Work Profile first")
            return
        }
        if (state == VaultProfileState.PROFILE_PAUSED) {
            toast("Work profile is paused")
            return
        }
        val intent = Intent(DummyActivity.PUBLIC_FREEZE_ALL).apply {
            component = ComponentName(this@HomeActivity, DummyActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast("Could not freeze isolated apps") }
        root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                if (drawerShowingWorkAppsPage) loadWorkProfileApps()
                VaultControlWidgetProvider.updateAll(this)
            }
        }, 700L)
    }

    private fun toggleWorkProfilePaused() {
        if (!isIsolationEnabled()) {
            toast(getString(R.string.mode_isolation_disabled_hint))
            return
        }
        val state = HavenProfileSession.inspectState(this)
        if (state == VaultProfileState.UNPROVISIONED || state == VaultProfileState.SETUP_PENDING) {
            setupWizardLauncher.launch(null)
            return
        }
        val willPause = state != VaultProfileState.PROFILE_PAUSED
        val changed = HavenProfileSession.setWorkProfilePaused(this, willPause)
        if (!changed) {
            toast("Could not change work profile quiet mode")
        } else {
            toast(if (willPause) "Work profile paused" else "Work profile resumed")
        }
        if (drawerShowingWorkAppsPage) {
            if (willPause) {
                workProfileApps = emptyList()
                vaultItems = emptyList()
                workProfileAppsState = WorkProfileAppLoadState.PROFILE_PAUSED
                renderVaultHeader()
                renderDrawerApps(drawerSearch.text?.toString().orEmpty())
            } else {
                root.postDelayed({ if (!isFinishing && !isDestroyed && drawerShowingWorkAppsPage) loadWorkProfileApps() }, 500L)
            }
        }
        VaultControlWidgetProvider.updateAll(this)
    }

    private fun openFileShuttle() {
        if (!SettingsManager.getInstance().crossProfileFileChooserEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable File Shuttle")
                .setMessage("File Shuttle is currently off. Open Work Profile & Isolation settings to grant access and enable cross-profile Documents UI?")
                .setPositiveButton("Open settings") { _, _ -> openSettings("isolation") }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val documentsUiIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(null, "vnd.android.document/root")
        }
        runCatching { startActivity(documentsUiIntent) }
            .onFailure { toast("Documents UI is unavailable") }
    }

    private fun withHavenProfileSession(
        tagPrefix: String,
        unavailableMessage: String,
        useMainService: Boolean = false,
        releaseImmediately: Boolean = true,
        onReady: (service: IHavenService, release: () -> Unit) -> Unit,
    ) {
        val tag = "${tagPrefix}_${System.nanoTime()}"
        val release = { HavenProfileSession.release(tag) }
        HavenProfileSession.acquire(
            host = this,
            consumerTag = tag,
            onReady = { mainService, workService ->
                val target = if (useMainService) mainService else workService
                if (releaseImmediately) {
                    try {
                        onReady(target, release)
                    } finally {
                        release()
                    }
                } else {
                    onReady(target, release)
                }
            },
            onUnavailable = {
                release()
                toast(unavailableMessage)
            },
        )
    }

    private fun runHavenInstallOp(
        tagPrefix: String,
        unavailableMessage: String,
        errorMessage: String,
        useMainService: Boolean = false,
        invoke: (IHavenService, IAppInstallCallback.Stub) -> Unit,
        onResult: (Int) -> Unit,
    ) {
        withHavenProfileSession(tagPrefix, unavailableMessage, useMainService, releaseImmediately = false) { service, release ->
            try {
                invoke(service, object : IAppInstallCallback.Stub() {
                    override fun callback(result: Int) {
                        runOnUiThread {
                            release()
                            onResult(result)
                        }
                    }
                })
            } catch (_: RemoteException) {
                release()
                toast(errorMessage)
            }
        }
    }

    private fun installApkUriIntoVault(uri: Uri) {
        runHavenInstallOp(
            tagPrefix = "op_apk",
            unavailableMessage = "Wake the work profile before installing an APK",
            errorMessage = "APK installation failed",
            invoke = { workService, callback -> workService.installApk(UriForwardProxy(applicationContext, uri), callback) },
        ) { result ->
            if (result == Activity.RESULT_OK) {
                toast(getString(R.string.install_app_to_profile_success))
                if (drawerShowingWorkAppsPage) loadWorkProfileApps()
            } else if (result == Activity.RESULT_CANCELED) {
                toast(getString(R.string.app_operation_cancelled))
            }
        }
    }

    private fun cloneAppToWorkProfile(packageName: String, label: String) {
        val state = HavenProfileSession.inspectState(this)
        if (state == VaultProfileState.UNPROVISIONED || state == VaultProfileState.SETUP_PENDING) {
            AlertDialog.Builder(this)
                .setTitle("Set up Haven Work Profile")
                .setMessage("Set up Haven's isolated Work Profile before cloning $label.")
                .setPositiveButton("Set up") { _, _ -> setupWizardLauncher.launch(null) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val appInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: run {
            toast("App info unavailable for $label")
            return
        }
        val wrapper = ApplicationInfoWrapper(appInfo).loadLabel(packageManager)
        val performClone = {
            runHavenInstallOp(
                tagPrefix = "op_clone",
                unavailableMessage = "Wake the work profile to clone $label",
                errorMessage = "Could not clone $label",
                invoke = { workService, callback -> workService.installApp(wrapper, callback) },
            ) { result ->
                when (result) {
                    Activity.RESULT_OK -> {
                        toast(getString(R.string.clone_success, label))
                        if (drawerShowingWorkAppsPage) loadWorkProfileApps()
                        VaultControlWidgetProvider.updateAll(this@HomeActivity)
                    }
                    HavenService.RESULT_CANNOT_INSTALL_SYSTEM_APP -> toast(getString(R.string.clone_fail_system_app))
                    else -> toast(getString(R.string.app_operation_cancelled))
                }
            }
        }
        if (Utility.isMIUI() && !wrapper.isSystem) {
            AlertDialog.Builder(this)
                .setMessage(R.string.miui_cannot_clone)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.continue_anyway) { _, _ -> performClone() }
                .show()
        } else {
            performClone()
        }
    }

    private fun showVaultAppMenu(item: VaultAppItem, anchor: View) {
        val baseIcon = item.icon
            ?: vaultIconBitmaps[item.packageName]
            ?: vaultIconCache.load(item.packageName)
            ?: AppCompatResources.getDrawable(this, R.drawable.ic_vault_shield)
            ?: packageManager.defaultActivityIcon

        val openManageDialog = {
            val actions = buildList<Pair<String, () -> Unit>> {
                if (item.isFrozen) {
                    add(getString(R.string.unfreeze_and_launch) to { launchVaultItem(item) })
                    add(getString(R.string.unfreeze_app) to { setVaultAppFrozen(item, false) })
                } else {
                    add(getString(R.string.launch) to { launchVaultItem(item) })
                    add(getString(R.string.freeze_app) to { setVaultAppFrozen(item, true) })
                }
                add("${if (item.isAutoFreeze) "✓ " else ""}${getString(R.string.auto_freeze)}" to { toggleAutoFreeze(item) })
                add("Add to Home" to { addVaultShortcutToWorkspace(item) })
                add("${if (item.packageName in vaultCrossProfileWidgets) "✓ " else ""}${getString(R.string.allow_cross_profile_widgets)}" to { toggleCrossProfileWidget(item) })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add("${if (item.packageName in vaultCrossProfilePackages) "✓ " else ""}${getString(R.string.allow_cross_profile_interaction)}" to { toggleCrossProfilePackage(item) })
                }
                if (!item.isFrozen) add(getString(R.string.manage_app_restrictions) to { openManagedRestrictions(item) })
                if (!item.isSystem) add(getString(R.string.clone_to_main_profile) to { cloneVaultAppToPersonal(item) })
                item.launchableApp?.let { add("App info" to { showWorkProfileAppInfo(it, anchor) }) }
            }
            showActionListDialog(item.label, actions)
        }

        val popup = AppShortcutPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            title = item.label,
            appIcon = appIconDrawable(
                baseIcon,
                highlight = false,
                iconScope = "drawer",
                component = item.className?.let { ComponentName(item.packageName, it) },
                profileCacheIdentity = "work",
            ),
            onManage = openManageDialog,
            onUninstall = if (item.isSystem) null else ({ uninstallVaultApp(item) }),
        )
        popup.show()
        popup.updateShortcuts(listOf(
            ShortcutPopupItem(if (item.isFrozen) "Unfreeze & Launch" else "Freeze now", null) {
                if (item.isFrozen) launchVaultItem(item) else setVaultAppFrozen(item, true)
            },
            ShortcutPopupItem(if (item.isAutoFreeze) "Auto-Freeze: On" else "Auto-Freeze: Off", null) { toggleAutoFreeze(item) },
            ShortcutPopupItem("Add to Home", null) { addVaultShortcutToWorkspace(item) },
        ))
    }

    private fun setVaultAppFrozen(item: VaultAppItem, frozen: Boolean) {
        val wrapper = item.wrapper ?: run {
            toast("Reconnect Haven Vault to change freeze state")
            loadWorkProfileApps()
            return
        }
        withHavenProfileSession("op_freeze", "Wake work profile first") { workService, _ ->
            try {
                if (frozen) workService.freezeApp(wrapper) else workService.unfreezeApp(wrapper)
                toast(getString(if (frozen) R.string.freeze_success else R.string.unfreeze_success, item.label))
                loadWorkProfileApps()
            } catch (_: Exception) {
                toast("Failed to update ${item.label}")
            }
        }
    }

    private fun toggleAutoFreeze(item: VaultAppItem) {
        val storage = LocalStorageManager.getInstance()
        val currently = storage.stringListContains(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, item.packageName)
        if (currently) storage.removeFromStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, item.packageName)
        else storage.appendStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE, item.packageName)
        vaultItems = vaultItems.map { if (it.packageName == item.packageName) it.copy(isAutoFreeze = !currently) else it }
        renderVaultHeader()
        renderDrawerApps(drawerSearch.text?.toString().orEmpty())
        toast(if (!currently) "${item.label} added to Auto-Freeze" else "${item.label} removed from Auto-Freeze")
    }

    private fun toggleCrossProfileWidget(item: VaultAppItem) {
        val newState = item.packageName !in vaultCrossProfileWidgets
        withHavenProfileSession("op_widget", "Wake work profile first") { workService, _ ->
            try {
                if (workService.setCrossProfileWidgetProviderEnabled(item.packageName, newState)) {
                    if (newState) vaultCrossProfileWidgets.add(item.packageName) else vaultCrossProfileWidgets.remove(item.packageName)
                    toast(if (newState) "Widgets from ${item.label} allowed on Home" else "Widgets from ${item.label} disallowed")
                }
            } catch (_: Exception) {
                toast("Could not update widget access")
            }
        }
    }

    private fun toggleCrossProfilePackage(item: VaultAppItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val newState = item.packageName !in vaultCrossProfilePackages
        val updated = vaultCrossProfilePackages.toMutableSet().apply {
            if (newState) add(item.packageName) else remove(item.packageName)
        }
        withHavenProfileSession("op_crosspkg", "Wake work profile first") { workService, _ ->
            try {
                workService.setCrossProfilePackages(updated.toList())
                vaultCrossProfilePackages = updated
                toast(if (newState) "Connected app requests allowed for ${item.label}" else "Connected app requests blocked for ${item.label}")
            } catch (_: Exception) {
                toast("Could not update connected app access")
            }
        }
    }

    private fun openManagedRestrictions(item: VaultAppItem) {
        withHavenProfileSession("op_restrict", "Wake work profile first") { workService, _ ->
            ManagedRestrictionsDialog.show(this, workService, item.packageName, item.label, useMaterialTheme = false)
        }
    }

    private fun cloneVaultAppToPersonal(item: VaultAppItem) {
        val wrapper = item.wrapper ?: return toast("App info still loading")
        runHavenInstallOp(
            tagPrefix = "op_clone_personal",
            unavailableMessage = "Could not connect to main service",
            errorMessage = "Could not clone ${item.label}",
            useMainService = true,
            invoke = { mainService, callback -> mainService.installApp(wrapper, callback) },
        ) { result ->
            if (result == Activity.RESULT_OK) {
                toast(getString(R.string.clone_success, item.label))
                refreshApps()
            } else {
                toast(getString(R.string.app_operation_cancelled))
            }
        }
    }

    private fun uninstallVaultApp(item: VaultAppItem) {
        val wrapper = item.wrapper ?: return toast("App info still loading")
        runHavenInstallOp(
            tagPrefix = "op_uninstall",
            unavailableMessage = "Wake work profile first",
            errorMessage = "Could not uninstall ${item.label}",
            invoke = { workService, callback -> workService.uninstallApp(wrapper, callback) },
        ) { result ->
            if (result == Activity.RESULT_OK) {
                vaultIconCache.delete(item.packageName)
                toast(getString(R.string.uninstall_success, item.label))
                loadWorkProfileApps()
            } else {
                toast(getString(R.string.app_operation_cancelled))
            }
        }
    }

    private fun addVaultShortcutToWorkspace(item: VaultAppItem) {
        if (!ensureWorkspaceUnlocked()) return
        val cell = firstEmptyWorkspaceCell()
        if (cell == null) {
            toast("This 4 × 6 icon area is full. Add a Home page or remove an icon.")
            closeDrawer()
            return
        }
        val shortcut = WorkspaceShortcut(
            packageName = item.packageName,
            className = item.className.orEmpty(),
            label = item.label,
            profile = WorkspaceShortcut.PROFILE_WORK,
            actionType = WorkspaceShortcut.ACTION_UNFREEZE_LAUNCH,
        )
        layout = layout.copy(items = layout.items + WorkspaceItem(currentPage, cell, shortcut))
        saveAndRender()
        closeDrawer()
        toast("Added ${item.label} (Haven) to Home")
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        if (launchIntent == null) return
        if (launchIntent.action == LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) {
            handlePinShortcutIntent(launchIntent)
            return
        }
        if (launchIntent.getBooleanExtra(EXTRA_OPEN_VAULT, false)) {
            launchIntent.removeExtra(EXTRA_OPEN_VAULT)
            openDrawerToVault()
        }
    }

    private fun handlePinShortcutIntent(pinIntent: Intent) {
        val launcherApps = getSystemService(LauncherApps::class.java) ?: return
        val request = launcherApps.getPinItemRequest(pinIntent) ?: return
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return
        val info = request.shortcutInfo ?: return
        if (!ensureWorkspaceUnlocked()) return
        val cell = firstEmptyWorkspaceCell() ?: return toast("Home page is full; could not pin shortcut")
        val intent = info.intent
        val shortcut = when (intent?.action) {
            DummyActivity.PUBLIC_FREEZE_ALL -> WorkspaceShortcut(
                packageName = packageName,
                className = DummyActivity::class.java.name,
                label = info.shortLabel?.toString() ?: "Freeze all",
                profile = WorkspaceShortcut.PROFILE_WORK,
                actionType = WorkspaceShortcut.ACTION_FREEZE_ALL,
            )
            DummyActivity.PUBLIC_UNFREEZE_AND_LAUNCH -> {
                val targetPkg = intent.getStringExtra("packageName") ?: packageName
                val linked = intent.getStringExtra("linkedPackages")?.split(",")?.filter { it.isNotBlank() }.orEmpty()
                WorkspaceShortcut(
                    packageName = targetPkg,
                    className = "",
                    label = info.shortLabel?.toString() ?: targetPkg,
                    profile = WorkspaceShortcut.PROFILE_WORK,
                    actionType = WorkspaceShortcut.ACTION_UNFREEZE_LAUNCH,
                    linkedPackages = linked,
                )
            }
            else -> WorkspaceShortcut(
                packageName = intent?.component?.packageName ?: info.`package`,
                className = intent?.component?.className.orEmpty(),
                label = info.shortLabel?.toString() ?: info.`package`,
            )
        }
        if (request.accept()) {
            layout = layout.copy(items = layout.items + WorkspaceItem(currentPage, cell, shortcut))
            saveAndRender()
            toast("Pinned ${shortcut.label} to Home")
        }
    }

    private fun updateBatteryTile(intent: Intent? = null) {
        if (!::batteryTile.isInitialized) return
        val sticky = intent ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
            val showLabel = prefs
                .getBoolean(LauncherSettings.KEY_BATTERY_SHOW_LABEL, false)
            batteryTile.text = if (showLabel) "Battery  $percent%" else "$percent%"
            batteryTile.contentDescription = "Battery level $percent percent. Tap to open Android Settings."
        } else {
            batteryTile.text = getString(R.string.home_battery_label)
            batteryTile.contentDescription = "Open battery settings"
        }
    }

    private fun showBatteryPopup() {
        val sticky = runCatching {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt().coerceIn(0, 100) else null
        BatteryPopupDialog(this, currentPalette(), percent).show()
    }

    private fun applyClockPreferences() {
        if (!::clockHeader.isInitialized) return
        val visible = prefs.getBoolean(LauncherSettings.KEY_CLOCK_VISIBLE, false)
        val showTime = prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_TIME, true)
        val showDate = prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_DATE, true)
        val showWeather = prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_WEATHER, true) &&
            prefs.getBoolean(LauncherSettings.KEY_WEATHER_ENABLED, true)
        clockHeader.visibility = if (visible && (showTime || showDate || showWeather)) View.VISIBLE else View.GONE
        clockView.visibility = if (showTime) View.VISIBLE else View.GONE
        dateView.visibility = if (showDate) View.VISIBLE else View.GONE
        weatherContainer.visibility = if (showWeather) View.VISIBLE else View.GONE
        clockView.textSize = prefs.getInt(LauncherSettings.KEY_CLOCK_TEXT_SIZE, 66).coerceIn(42, 76).toFloat()
        val color = prefs.getInt(LauncherSettings.KEY_CLOCK_TEXT_COLOR, Color.WHITE)
        val font = LauncherTypography.typeface(this)
        // Smart's Home clock uses a lighter rounded face than the launcher labels. Keep the
        // selected family (Poppins by default), but use a medium weight for this compact line.
        clockView.typeface = Typeface.create(font, HOME_CLOCK_WEIGHT, false)
        dateView.typeface = Typeface.create(font, HOME_CLOCK_WEIGHT, false)
        weatherValue.typeface = Typeface.create(font, HOME_CLOCK_WEIGHT, false)
        clockView.setTextColor(color)
        dateView.setTextColor((color and 0x00FFFFFF) or 0xE6000000.toInt())
        weatherValue.setTextColor((color and 0x00FFFFFF) or 0xE6000000.toInt())
        weatherIcon.setColorFilter(if (weatherIsDaytime) SUNNY_WEATHER_TINT else color)
        clockHeader.translationY = dp(prefs.getInt(LauncherSettings.KEY_CLOCK_VERTICAL_OFFSET, 0)).toFloat()
        clockHeader.background = if (prefs.getBoolean(LauncherSettings.KEY_CLOCK_BACKGROUND, false)) {
            roundedDrawable(0x55343A47, dp(20))
        } else Color.TRANSPARENT.toDrawable()
    }

    private fun applyBatteryPreferences() {
        if (!::batteryTile.isInitialized) return
        batteryTile.visibility = if (prefs.getBoolean(LauncherSettings.KEY_BATTERY_VISIBLE, false)) View.VISIBLE else View.GONE
        batteryTile.background = LayerDrawable(arrayOf(
            if (prefs.getBoolean(LauncherSettings.KEY_BATTERY_BACKGROUND, true)) roundedDrawable(0xBB55596E.toInt(), dp(30))
            else Color.TRANSPARENT.toDrawable(),
            selectableBackground(),
        ))
        (batteryTile.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val alignment = prefs.getString(LauncherSettings.KEY_BATTERY_ALIGNMENT, "start")
            params.gravity = alignmentGravity(alignment, defaultStart = true)
            params.marginStart = if (alignment == "start") dp(2) else 0
            batteryTile.layoutParams = params
        }
    }

    private fun showClockWidgetMenu(anchor: View) {
        showWidgetPopup(anchor, "Widget", null, null, 72, listOf(
            WorkspacePopupAction("Configure", "tune") { showClockConfiguration() },
            WorkspacePopupAction("Fine positioning", "resize") { chooseClockPosition { applyClockPreferences() } },
            WorkspacePopupAction("Remove widget", "remove", destructive = true) { removeClockWidget() },
        ))
    }

    private fun showClockConfiguration() {
        val options = arrayOf("Show time", "Show date", "Show weather")
        val values = booleanArrayOf(
            prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_TIME, true),
            prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_DATE, true),
            prefs.getBoolean(LauncherSettings.KEY_CLOCK_SHOW_WEATHER, true),
        )
        AlertDialog.Builder(this)
            .setTitle("Clock")
            .setMultiChoiceItems(options, values) { _, index, checked -> values[index] = checked }
            .setNeutralButton("Text style") { _, _ -> showClockStyleMenu() }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit {
                    putBoolean(LauncherSettings.KEY_CLOCK_SHOW_TIME, values[0])
                    putBoolean(LauncherSettings.KEY_CLOCK_SHOW_DATE, values[1])
                    putBoolean(LauncherSettings.KEY_CLOCK_SHOW_WEATHER, values[2])
                    putBoolean(LauncherSettings.KEY_CLOCK_VISIBLE, true)
                }
                applyClockPreferences()
            }
            .show()
    }

    private fun showClockStyleMenu() {
        showActionListDialog("Clock appearance", listOf(
            "Text size" to { chooseClockTextSize() },
            "Text color" to { chooseClockTextColor() },
            "Background" to {
                val enabled = !prefs.getBoolean(LauncherSettings.KEY_CLOCK_BACKGROUND, false)
                prefs.edit { putBoolean(LauncherSettings.KEY_CLOCK_BACKGROUND, enabled) }
                applyClockPreferences()
                toast(if (enabled) "Clock background enabled" else "Clock background removed")
            },
        ), showCancel = true)
    }

    private fun chooseClockTextSize() {
        val sizes = intArrayOf(48, 66, 76)
        showSingleChoiceDialog("Clock text size", arrayOf("Compact", "Default", "Large"), sizes.indexOf(prefs.getInt(LauncherSettings.KEY_CLOCK_TEXT_SIZE, 66)).coerceAtLeast(0)) { which ->
            prefs.edit { putInt(LauncherSettings.KEY_CLOCK_TEXT_SIZE, sizes[which]) }
            applyClockPreferences()
        }
    }

    private fun chooseClockTextColor() {
        val colors = arrayOf("White" to Color.WHITE, "Soft white" to 0xFFE8E5DF.toInt(), "Teres teal" to 0xFF78D5CA.toInt(), "Sky blue" to 0xFF9EC7FF.toInt())
        val selected = colors.indexOfFirst { it.second == prefs.getInt(LauncherSettings.KEY_CLOCK_TEXT_COLOR, Color.WHITE) }.coerceAtLeast(0)
        showSingleChoiceDialog("Clock text color", colors.map { it.first }.toTypedArray(), selected) { which ->
            prefs.edit { putInt(LauncherSettings.KEY_CLOCK_TEXT_COLOR, colors[which].second) }
            applyClockPreferences()
        }
    }

    private fun removeClockWidget() {
        confirmRemoval("Remove clock widget", "Remove the clock, date and weather block from Home?") {
            prefs.edit { putBoolean(LauncherSettings.KEY_CLOCK_VISIBLE, false) }
            applyClockPreferences()
        }
    }

    private fun showBatteryWidgetMenu(anchor: View) {
        showWidgetPopup(anchor, "Widget", "Battery", null, 60, listOf(
            WorkspacePopupAction("Configure", "tune") { showBatteryConfiguration() },
            WorkspacePopupAction("Fine positioning", "resize") {
                showAlignmentPicker("Battery widget position", LauncherSettings.KEY_BATTERY_ALIGNMENT, "start") { applyBatteryPreferences() }
            },
            WorkspacePopupAction("Remove widget", "remove", destructive = true) { removeBatteryWidget() },
        ))
    }

    private fun showWidgetSelectionFrame(anchor: View): View? {
        if (!::root.isInitialized || !anchor.isAttachedToWindow) return null
        widgetSelectionFrame?.let(::removeWidgetSelectionFrame)
        val frame = WidgetSelectionFrame(anchor)
        widgetSelectionFrame = frame
        root.addView(frame, FrameLayout.LayoutParams(-1, -1))
        frame.bringToFront()
        return frame
    }

    private fun removeWidgetSelectionFrame(frame: View) {
        if (widgetSelectionFrame === frame) widgetSelectionFrame = null
        (frame.parent as? ViewGroup)?.removeView(frame)
    }

    /** Draws Smart-style selection rails around the currently edited launcher widget. */
    private inner class WidgetSelectionFrame(private val anchor: View) : View(this@HomeActivity) {
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val frameRect = RectF()
        private val anchorPosition = IntArray(2)
        private val rootPosition = IntArray(2)

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!anchor.isAttachedToWindow) return
            anchor.getLocationOnScreen(anchorPosition)
            root.getLocationOnScreen(rootPosition)
            frameRect.set(
                (anchorPosition[0] - rootPosition[0] - dp(8)).toFloat(),
                (anchorPosition[1] - rootPosition[1] - dp(60)).toFloat(),
                (anchorPosition[0] - rootPosition[0] + anchor.width + dp(8)).toFloat(),
                (anchorPosition[1] - rootPosition[1] + anchor.height + dp(60)).toFloat(),
            )
            val radius = dp(30).toFloat()
            canvas.drawRoundRect(frameRect, radius, radius, outlinePaint)
            val halfW = dp(110) / 2f
            val halfT = dp(4) / 2f
            val r = halfT * 2
            val cx = frameRect.centerX()
            val cy = frameRect.centerY()
            fun drawBar(l: Float, t: Float, right: Float, b: Float) = canvas.drawRoundRect(l, t, right, b, r, r, handlePaint)
            drawBar(cx - halfW, frameRect.top - halfT, cx + halfW, frameRect.top + halfT)
            drawBar(cx - halfW, frameRect.bottom - halfT, cx + halfW, frameRect.bottom + halfT)
            drawBar(frameRect.left - halfT, cy - halfW, frameRect.left + halfT, cy + halfW)
            drawBar(frameRect.right - halfT, cy - halfW, frameRect.right + halfT, cy + halfW)
        }
    }

    private fun showBatteryConfiguration() {
        val values = booleanArrayOf(
            prefs.getBoolean(LauncherSettings.KEY_BATTERY_SHOW_LABEL, false),
            prefs.getBoolean(LauncherSettings.KEY_BATTERY_BACKGROUND, true),
        )
        AlertDialog.Builder(this)
            .setTitle("Battery widget")
            .setMultiChoiceItems(arrayOf("Show Battery label", "Show background"), values) { _, index, checked -> values[index] = checked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit {
                    putBoolean(LauncherSettings.KEY_BATTERY_SHOW_LABEL, values[0])
                    putBoolean(LauncherSettings.KEY_BATTERY_BACKGROUND, values[1])
                    putBoolean(LauncherSettings.KEY_BATTERY_VISIBLE, true)
                }
                applyBatteryPreferences()
                updateBatteryTile()
            }
            .show()
    }

    private fun removeBatteryWidget() {
        confirmRemoval("Remove battery widget", "Remove the battery tile from Home?") {
            prefs.edit { putBoolean(LauncherSettings.KEY_BATTERY_VISIBLE, false) }
            applyBatteryPreferences()
        }
    }

    private fun enableWeatherFromUserAction() {
        prefs.edit {
            putBoolean(LauncherSettings.KEY_WEATHER_ENABLED, true)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            refreshWeather()
        } else {
            weatherPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun refreshWeather() {
        if (!::weatherValue.isInitialized || isFinishing || isDestroyed) return
        weatherRepository.load { weather ->
            if (isFinishing || isDestroyed || !::weatherValue.isInitialized) return@load
            if (weather == null) {
                weatherValue.text = getString(R.string.home_weather_prompt)
                weatherIcon.setImageResource(com.arcturus.haven.R.drawable.ic_weather_moon)
                weatherContainer.contentDescription =
                    "Weather is unavailable. Tap to retry location and forecast access. Weather data by Open-Meteo."
            } else {
                weatherValue.text = getString(R.string.home_weather_temperature_f, weather.temperatureFahrenheit)
                weatherIcon.setImageResource(
                    if (weather.isDay) com.arcturus.haven.R.drawable.ic_weather_sun
                    else com.arcturus.haven.R.drawable.ic_weather_moon,
                )
                weatherIsDaytime = weather.isDay
                val clockColor = prefs
                    .getInt(LauncherSettings.KEY_CLOCK_TEXT_COLOR, Color.WHITE)
                weatherIcon.setColorFilter(if (weather.isDay) SUNNY_WEATHER_TINT else clockColor)
                weatherContainer.contentDescription =
                    "${weather.condition}, ${weather.temperatureFahrenheit} degrees Fahrenheit. Weather data by Open-Meteo."
            }
        }
    }

    private fun showDrawerAppMenu(app: LaunchableApp, anchor: View) {
        val isWorkProfileApp = app.activity.user != Process.myUserHandle()
        val isHidden = !isWorkProfileApp && app.entry.componentKey in hiddenAppComponents
        val popup = AppShortcutPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            title = app.entry.label,
            appIcon = appIconDrawable(
                app.icon ?: packageManager.defaultActivityIcon,
                app.entry.packageName in activeNotificationPackages,
                iconScope = "drawer",
                component = ComponentName(app.entry.packageName, app.entry.className),
            ),
            onManage = {
                val actions = if (isWorkProfileApp) {
                    listOf("App info" to { showWorkProfileAppInfo(app, anchor) }, "Open" to { launchApp(app) })
                } else {
                    buildList {
                        add("Add to Home" to { addToWorkspace(app) })
                        if (isIsolationEnabled()) add("Clone to Haven" to { cloneAppToWorkProfile(app.entry.packageName, app.entry.label) })
                        add("App info" to { showAppInfo(app.entry.packageName) })
                        add((if (isHidden) "Show in drawer" else "Hide from drawer") to { setAppHidden(app, !isHidden) })
                        add("Open" to { launchApp(app) })
                    }
                }
                showActionListDialog(app.entry.label, actions)
            },
            onUninstall = if (isWorkProfileApp) null else ({ requestAppUninstall(app.entry.packageName) }),
        )
        popup.show()
        catalog.loadShortcutsAsync(app) { shortcuts ->
            if (isFinishing || isDestroyed) return@loadShortcutsAsync
            val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java)
            popup.updateShortcuts(shortcuts.map { shortcut ->
                val label = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
                    ?: app.entry.label
                val icon = runCatching {
                    launcherApps?.getShortcutIconDrawable(shortcut, resources.displayMetrics.densityDpi)
                }.getOrNull()
                ShortcutPopupItem(label, icon) {
                    val bounds = android.graphics.Rect().also { anchor.getGlobalVisibleRect(it) }
                    runCatching {
                        catalog.start(shortcut, bounds)
                        recordAppLaunch(app)
                    }
                        .onFailure { toast("This shortcut is no longer available") }
                }
            })
        }
    }

    private fun showWorkProfileAppInfo(app: LaunchableApp, anchor: View) {
        val bounds = android.graphics.Rect().also { anchor.getGlobalVisibleRect(it) }
        runCatching {
            getSystemService(android.content.pm.LauncherApps::class.java)
                ?.startAppDetailsActivity(app.componentName, app.activity.user, bounds, null)
                ?: error("Launcher apps service is unavailable")
        }.onFailure { toast("App details are unavailable") }
    }

    private fun setAppHidden(app: LaunchableApp, hidden: Boolean) {
        hiddenAppComponents = hiddenAppComponents.toMutableSet().apply {
            if (hidden) add(app.entry.componentKey) else remove(app.entry.componentKey)
        }
        prefs.edit {
            putStringSet(LauncherSettings.KEY_HIDDEN_APPS, hiddenAppComponents.toSet())
        }
        renderDrawerCategories()
        renderDrawerApps(drawerSearch.text.toString())
        toast(if (hidden) "${app.entry.label} hidden from the drawer" else "${app.entry.label} shown in the drawer")
    }

    private fun openLauncherMenu() {
        QuickSettingsSheet(
            context = this,
            palette = currentPalette(),
            isLocked = isWorkspaceLocked(),
            canRemovePage = canRemoveCurrentPage(),
            actions = QuickSettingsSheet.Actions(
                onShowAllSettings = { openSettings() },
                onToggleLock = { toggleWorkspaceLock() },
                onNotifications = { openSettings("root") },
                onAddIcon = { showAddIconPicker() },
                onAddFolder = { showAddFolderPicker() },
                onAddWidget = { showWidgetPicker() },
                onAddPage = { addWorkspacePage() },
                onRemovePage = { removeCurrentPage() },
                onOpenVault = { openDrawerToVault() },
                onFreezeAll = { freezeAllAutoFreezeApps() },
                onWallpaper = { openSettings("wallpaper") },
                onAppearance = { openSettings("appearance") },
                onHomeSettings = { openSettings("home") },
                onIsolationSettings = { openSettings("isolation") },
            ),
            isolationEnabled = isIsolationEnabled(),
        ).show()
    }

    private fun showAddIconPicker() {
        if (!ensureWorkspaceUnlocked()) return
        val available = apps.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
        if (available.isEmpty()) {
            toast("Installed apps are still loading")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Add icon")
            .setItems(available.map { it.entry.label }.toTypedArray()) { _, which -> addToWorkspace(available[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddFolderPicker() {
        if (!ensureWorkspaceUnlocked()) return
        val available = apps.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
        if (available.size < 2) {
            toast("At least two apps are needed to create a folder")
            return
        }
        val checked = BooleanArray(available.size)
        AlertDialog.Builder(this)
            .setTitle("Choose apps for folder")
            .setMultiChoiceItems(available.map { it.entry.label }.toTypedArray(), checked) { _, which, selected ->
                checked[which] = selected
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create folder") { _, _ ->
                if (!ensureWorkspaceUnlocked()) return@setPositiveButton
                val shortcuts = available.mapIndexedNotNull { index, app -> app.entry.toShortcut().takeIf { checked[index] } }
                if (shortcuts.size < 2) {
                    toast("Choose at least two apps")
                    return@setPositiveButton
                }
                val cell = firstEmptyWorkspaceCell()
                if (cell == null) {
                    toast("This 4 × 6 icon area is full. Add a Home page or remove an icon.")
                    return@setPositiveButton
                }
                layout = layout.copy(items = layout.items + WorkspaceItem(currentPage, cell, folder = shortcuts))
                saveAndRender()
                toast("Folder added to Home")
            }
            .show()
    }

    private fun addWorkspacePage() {
        if (!ensureWorkspaceUnlocked()) return
        val count = workspacePageCount()
        if (count >= LauncherSettings.MAX_WORKSPACE_PAGES) {
            toast("You have reached the maximum number of pages")
            return
        }
        prefs.edit { putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, count + 1) }
        currentPage = count
        rebuildWorkspace()
        toast("Home page added")
    }

    private fun removeCurrentPage() {
        if (!ensureWorkspaceUnlocked()) return
        val count = workspacePageCount()
        if (count <= 1) {
            toast("At least one Home page must remain")
            return
        }
        if (layout.items.any { it.page == currentPage }) {
            toast("Move or remove this page's icons before removing it")
            return
        }
        val removedPage = currentPage
        layout = layout.copy(items = layout.items.map { item -> if (item.page > removedPage) item.copy(page = item.page - 1) else item })
        prefs.edit { putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, count - 1) }
        if (currentPage == count - 1) currentPage--
        currentPage = currentPage.coerceAtLeast(0)
        saveAndRender()
        toast("Home page removed")
    }

    private fun canRemoveCurrentPage(): Boolean = workspacePageCount() > 1 &&
        layout.items.none { it.page == currentPage }

    private fun toggleWorkspaceLock() {
        val locked = !prefs.getBoolean(LauncherSettings.KEY_WORKSPACE_LOCKED, false)
        prefs.edit { putBoolean(LauncherSettings.KEY_WORKSPACE_LOCKED, locked) }
        toast(if (locked) "Home screen locked" else "Home screen unlocked")
    }

    private fun isWorkspaceLocked(): Boolean = prefs.getBoolean(LauncherSettings.KEY_WORKSPACE_LOCKED, false)

    private fun ensureWorkspaceUnlocked(): Boolean {
        if (!isWorkspaceLocked()) return true
        toast("Home screen is locked. Unlock it to make changes.")
        return false
    }

    private fun showWidgetPicker(stackId: Int? = null) {
        if (!ensureWorkspaceUnlocked()) return
        val providers = runCatching { widgetHost.providers() }.getOrDefault(emptyList())
        if (providers.isEmpty()) {
            toast("No widgets are available on this device")
            return
        }
        pendingWidgetStackId = stackId
        widgetPickerLauncher.launch(Intent(this, WidgetPickerActivity::class.java).apply {
            putExtra(WidgetPickerActivity.EXTRA_STACK_TARGET_PICKER, stackId != null)
        })
    }

    private fun createEmptyWidgetStack() {
        if (!ensureWorkspaceUnlocked()) return
        val stackId = (layout.widgetStacks.minOfOrNull { it.stackId } ?: 0) - 1
        val stack = WorkspaceWidgetStack(stackId = stackId)
        layout = normalizeWidgetPositions(layout.copy(
            widgetStacks = layout.widgetStacks + stack,
            widgetCells = layout.widgetCells + (stackId to 0),
            widgetGridColumns = gridColumns,
        ))
        workspaceStore.save(layout)
        rebuildWorkspace()
        toast("Added Widget Stack")
    }

    private fun beginAddWidget(info: AppWidgetProviderInfo, targetStackId: Int? = null) {
        if (!ensureWorkspaceUnlocked()) return
        val widgetId = widgetHost.host.allocateAppWidgetId()
        pendingWidgetId = widgetId
        pendingWidgetInfo = info
        pendingWidgetStackId = targetStackId
        val bound = runCatching {
            widgetHost.manager.bindAppWidgetIdIfAllowed(widgetId, info.profile, info.provider, null)
        }.getOrDefault(false)
        if (bound) {
            configureOrAddWidget(widgetId, info)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            }
            widgetBindLauncher.launch(bindIntent)
        }
    }

    private fun configureOrAddWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val configureActivity = info.configure
        if (configureActivity != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configureActivity
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            widgetConfigureLauncher.launch(intent)
        } else addWidget(widgetId, info)
    }

    private fun addWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        if (!ensureWorkspaceUnlocked()) {
            abandonWidget(widgetId)
            return
        }
        val memberSize = defaultWidgetSize(info)
        val targetStackId = pendingWidgetStackId
        val targetStack = targetStackId?.let { id -> layout.widgetStacks.firstOrNull { it.stackId == id } }
        layout = layout.copy(
            widgetIds = (layout.widgetIds + widgetId).distinct(),
            widgetSizes = layout.widgetSizes + (widgetId to memberSize),
            widgetStacks = if (targetStack != null) {
                layout.widgetStacks.map { if (it.stackId == targetStack.stackId) it.copy(widgetIds = it.widgetIds + widgetId) else it }
            } else layout.widgetStacks,
        )
        layout = normalizeWidgetPositions(layout)
        workspaceStore.save(layout)
        pendingWidgetId = null
        pendingWidgetInfo = null
        pendingWidgetStackId = null
        rebuildWorkspace()
        toast(if (targetStack != null) "Added ${info.loadLabel(packageManager)} to stack" else "Added ${info.loadLabel(packageManager)}")
    }

    private fun abandonWidget(widgetId: Int) {
        widgetHost.delete(widgetId)
        pendingWidgetId = null
        pendingWidgetInfo = null
        pendingWidgetStackId = null
    }

    private fun rebuildWidgetViews() {
        if (!::widgetColumn.isInitialized) return
        val normalized = normalizeWidgetPositions(layout)
        if (normalized != layout) {
            layout = normalized
            workspaceStore.save(layout)
        }
        widgetColumn.removeAllViews()
        widgetStackViews.clear()
        widgetColumn.setPadding(dp(14), 0, dp(14), 0)
        widgetColumn.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> return@setOnDragListener !isWorkspaceLocked() && event.localState is WidgetDragPayload
                DragEvent.ACTION_DRAG_ENTERED -> view.background = roundedDrawable(withAlpha(currentPalette().accent, 0x22), dp(18))
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> view.background = Color.TRANSPARENT.toDrawable()
                DragEvent.ACTION_DROP -> {
                    view.background = Color.TRANSPARENT.toDrawable()
                    val payload = event.localState as? WidgetDragPayload ?: return@setOnDragListener false
                    dropWidgetAt(payload, event.x, event.y)
                    return@setOnDragListener true
                }
            }
            true
        }
        val cellWidth = widgetCellWidthPx()
        val cellHeight = widgetCellHeightPx()
        val spans = widgetGridSpans(layout)
        val visibleIds = visibleWidgetIds(layout)
        val rowsNeeded = visibleIds.maxOfOrNull { widgetId ->
            val cell = layout.widgetCells[widgetId] ?: 0
            WidgetGridPlacement.cell(cell, gridColumns).second + (spans[widgetId]?.rows ?: 1)
        } ?: 0
        val rows = if (visibleIds.isEmpty()) 0 else rowsNeeded + 1
        (widgetColumn.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = dp(rows * cellHeightDp())
            widgetColumn.layoutParams = params
        }
        visibleIds.forEach { widgetId ->
            val stack = layout.widgetStacks.firstOrNull { it.stackId == widgetId }
            val span = spans[widgetId] ?: return@forEach
            val (column, row) = WidgetGridPlacement.cell(layout.widgetCells[widgetId] ?: 0, gridColumns)
            if (stack != null) {
                addWidgetStackView(stack, cellWidth, cellHeight, column, row)
                return@forEach
            }
            val (info, hostView) = createHostedWidgetView(widgetId) ?: return@forEach
            val size = layout.widgetSizes[widgetId]?.let { clampWidgetSize(it, info) } ?: defaultWidgetSize(info)
            val widgetWidth = dp(size.widthDp)
            val widgetHeight = dp(size.heightDp)
            lateinit var widgetContainer: WidgetTouchFrameLayout
            widgetContainer = WidgetTouchFrameLayout(
                widgetId = widgetId,
                preserveLongPressForClickableChildren = info.provider.className == IconGroupWidgetProvider::class.java.name,
            ) {
                showWidgetMenu(widgetId, info, widgetContainer)
            }.apply {
                clipChildren = false
                clipToPadding = false
            }
            updateHostedWidgetSize(hostView, size)
            val horizontalGravity = alignmentGravity(prefs.getString(widgetAlignmentKey(widgetId), "center"))
            val surface = FrameLayout(this).apply {
                background = if (info.provider.className == IconGroupWidgetProvider::class.java.name) {
                    Color.TRANSPARENT.toDrawable()
                } else {
                    roundedDrawable(0xAA101719.toInt(), dp(20))
                }
                clipChildren = false
                clipToPadding = false
            }
            surface.addView(hostView, FrameLayout.LayoutParams(widgetWidth, widgetHeight, Gravity.CENTER))
            widgetContainer.addView(surface, FrameLayout.LayoutParams(widgetWidth + dp(16), widgetHeight + dp(16), horizontalGravity or Gravity.CENTER_VERTICAL))
            if (editingWidgetId == widgetId) {
                widgetContainer.addView(widgetEditToolbar(info), FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
                surface.addView(createResizeHandle(widgetId, info, surface, hostView),
                    FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END))
            }
            widgetColumn.addView(widgetContainer, FrameLayout.LayoutParams(
                cellWidth * span.columns,
                cellHeight * span.rows,
            ).apply {
                leftMargin = column * cellWidth
                topMargin = row * cellHeight
            })
        }
        widgetHost.startListening()
    }

    private fun addWidgetStackView(
        stack: WorkspaceWidgetStack,
        cellWidth: Int,
        cellHeight: Int,
        column: Int,
        row: Int,
    ) {
        val memberInfos = stack.widgetIds.mapNotNull { id ->
            val (info, hostView) = createHostedWidgetView(id) ?: return@mapNotNull null
            updateHostedWidgetSize(hostView, stack.size)
            val surface = FrameLayout(this).apply {
                background = roundedDrawable(Color.TRANSPARENT, dp(24))
                clipChildren = false
                clipToPadding = false
                clipToOutline = true
                setPadding(dp(STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP), dp(STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP),
                    dp(STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP), dp(STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP))
                val hostWidth = dp(stack.size.widthDp)
                val hostContentWidth = (hostWidth - dp(STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP * 2)).coerceAtLeast(1)
                val targetContentWidth = (hostWidth + dp(STACK_WIDGET_FRAME_INSET_DP * 2) - dp(2)).coerceAtLeast(hostContentWidth)
                hostView.scaleX = targetContentWidth.toFloat() / hostContentWidth
                addView(
                    hostView,
                    FrameLayout.LayoutParams(-1, -1),
                )
            }
            Triple(id, info, surface)
        }
        val switcher = WidgetStackSwitcher(stack.orientation, stack.currentIndex) { delta ->
            advanceWidgetStack(stack.stackId, delta)
        }
        memberInfos.forEach { (_, _, surface) -> switcher.addView(surface, FrameLayout.LayoutParams(-1, -1)) }
        if (memberInfos.isEmpty()) {
            switcher.addView(TextView(this).apply {
                text = getString(R.string.widget_stack_empty)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedDrawable(0xAA303640.toInt(), dp(20))
                isClickable = true
                isFocusable = true
                setOnClickListener { showWidgetStackConfiguration(stack.stackId) }
            }, FrameLayout.LayoutParams(-1, -1))
        }
        switcher.showIndex(stack.currentIndex, animate = false)
        widgetStackViews[stack.stackId] = switcher
        val widgetWidth = dp(stack.size.widthDp)
        val widgetHeight = dp(stack.size.heightDp)
        val horizontalGravity = alignmentGravity(prefs.getString(widgetAlignmentKey(stack.stackId), "center"))
        lateinit var widgetContainer: WidgetTouchFrameLayout
        widgetContainer = WidgetTouchFrameLayout(stack.stackId) {
            showWidgetStackMenu(stack.stackId, widgetContainer)
        }.apply {
            clipChildren = false
            clipToPadding = false
        }
        widgetContainer.addView(switcher, FrameLayout.LayoutParams(
            widgetWidth + dp(16),
            widgetHeight + dp(16),
            horizontalGravity or Gravity.CENTER_VERTICAL,
        ))
        val columnParams = FrameLayout.LayoutParams(
            widgetWidth + dp(16),
            widgetHeight + dp(16),
            Gravity.TOP or Gravity.START,
        ).apply {
            leftMargin = column * cellWidth - widgetColumn.paddingLeft
            topMargin = row * cellHeight - dp(21)
        }
        widgetColumn.addView(widgetContainer, columnParams)
        memberInfos.forEach { (widgetId, _, surface) ->
            val hostView = surface.getChildAt(0) as? android.appwidget.AppWidgetHostView ?: return@forEach
            hostView.post {
                if (hostView.isAttachedToWindow && widgetHost.manager.getAppWidgetInfo(widgetId) != null) {
                    updateHostedWidgetSize(
                        hostView,
                        WorkspaceWidgetSize(
                            stack.size.widthDp + STACK_WIDGET_FRAME_INSET_DP * 2,
                            stack.size.heightDp + STACK_WIDGET_FRAME_INSET_DP * 2,
                        ),
                    )
                }
            }
        }
    }

    private fun advanceWidgetStack(stackId: Int, delta: Int) {
        val stack = layout.widgetStacks.firstOrNull { it.stackId == stackId } ?: return
        if (stack.widgetIds.size < 2) return
        val nextIndex = (stack.currentIndex + delta).mod(stack.widgetIds.size)
        layout = layout.copy(widgetStacks = layout.widgetStacks.map {
            if (it.stackId == stackId) it.copy(currentIndex = nextIndex) else it
        })
        workspaceStore.save(layout)
        widgetStackViews[stackId]?.showIndex(nextIndex, animate = true)
    }

    private inner class WidgetStackSwitcher(
        private val orientation: WorkspaceWidgetStackOrientation,
        initialIndex: Int,
        private val onSwipe: (Int) -> Unit,
    ) : FrameLayout(this@HomeActivity) {
        private val touchSlop = ViewConfiguration.get(this@HomeActivity).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var swipeAxis: Float? = null
        private var swipeIntercepted = false

        init {
            clipChildren = true
            clipToPadding = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Widget stack"
            showIndex(initialIndex, animate = false)
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    swipeAxis = null
                    swipeIntercepted = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val primary = if (orientation == WorkspaceWidgetStackOrientation.HORIZONTAL) dx else dy
                    val cross = if (orientation == WorkspaceWidgetStackOrientation.HORIZONTAL) dy else dx
                    if (kotlin.math.abs(primary) > touchSlop && kotlin.math.abs(primary) > kotlin.math.abs(cross)) {
                        swipeAxis = primary
                        swipeIntercepted = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            return swipeIntercepted
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val value = if (orientation == WorkspaceWidgetStackOrientation.HORIZONTAL) event.x - downX else event.y - downY
                    if (kotlin.math.abs(value) > touchSlop) swipeAxis = value
                }
                MotionEvent.ACTION_UP -> {
                    swipeAxis?.let { onSwipe(if (it < 0) 1 else -1) }
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        fun showIndex(index: Int, animate: Boolean) {
            if (isEmpty()) return
            val target = index.coerceIn(0, childCount - 1)
            for (childIndex in 0 until childCount) getChildAt(childIndex).visibility =
                if (childIndex == target) View.VISIBLE else View.GONE
            if (animate) {
                getChildAt(target).alpha = 0f
                getChildAt(target).animate().alpha(1f).setDuration(180L).start()
            }
            contentDescription = if (childCount > 1) "Widget ${target + 1} of $childCount" else "Widget stack"
        }
    }

    private inner class WidgetTouchFrameLayout(
        private val widgetId: Int,
        private val preserveLongPressForClickableChildren: Boolean = false,
        private val onWidgetLongPress: () -> Unit,
    ) : FrameLayout(this@HomeActivity) {
        private val touchSlop = ViewConfiguration.get(this@HomeActivity).scaledTouchSlop
        private val gestureHandler = Handler(Looper.getMainLooper())
        private var trackingGesture = false
        private var longPressTriggered = false
        private var moved = false
        private var dragStarted = false
        private var downX = 0f
        private var downY = 0f
        private val longPressCheck = Runnable {
            if (trackingGesture && editingWidgetId != widgetId) {
                longPressTriggered = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (preserveLongPressForClickableChildren && disallowIntercept) return
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureHandler.removeCallbacks(longPressCheck)
                    trackingGesture = true
                    longPressTriggered = false
                    moved = false
                    dragStarted = false
                    downX = event.x
                    downY = event.y
                    if (editingWidgetId != widgetId) {
                        gestureHandler.postDelayed(longPressCheck, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val crossedSlop = kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop
                    if (longPressTriggered && crossedSlop && !dragStarted) {
                        moved = true
                        dragStarted = startWidgetDrag(this, widgetId)
                    } else if (!longPressTriggered && crossedSlop) {
                        moved = true
                        trackingGesture = false
                        gestureHandler.removeCallbacks(longPressCheck)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val showMenu = longPressTriggered && !moved && !dragStarted
                    trackingGesture = false
                    gestureHandler.removeCallbacks(longPressCheck)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (showMenu) onWidgetLongPress()
                    return showMenu || dragStarted
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingGesture = false
                    gestureHandler.removeCallbacks(longPressCheck)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return dragStarted
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP && dragStarted) performClick()
            return dragStarted
        }

        override fun performClick(): Boolean = super.performClick()
    }

    private fun widgetCellWidthPx(): Int {
        val width = widgetColumn.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val contentWidth = width - widgetColumn.paddingLeft - widgetColumn.paddingRight
        return (contentWidth / gridColumns).coerceAtLeast(1)
    }

    private fun widgetCellHeightPx(): Int = dp(cellHeightDp()).coerceAtLeast(1)

    private fun widgetGridSpan(size: WorkspaceWidgetSize): WidgetGridSpan {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        return WidgetGridPlacement.spanFor(
            widthDp = size.widthDp,
            heightDp = size.heightDp,
            cellWidthDp = (widgetCellWidthPx() / density).roundToInt().coerceAtLeast(1),
            cellHeightDp = cellHeightDp(),
            columns = gridColumns,
        )
    }

    private fun visibleWidgetIds(source: WorkspaceLayout): List<Int> {
        val stackedIds = source.widgetStacks.flatMapTo(mutableSetOf()) { it.widgetIds }
        return (source.widgetIds.filterNot { it in stackedIds } + source.widgetStacks.map { it.stackId }).distinct()
    }

    private fun widgetGridSpans(source: WorkspaceLayout): Map<Int, WidgetGridSpan> =
        visibleWidgetIds(source).associateWith { widgetId ->
            val stack = source.widgetStacks.firstOrNull { it.stackId == widgetId }
            val size = if (stack != null) {
                stack.size
            } else {
                val info = runCatching { widgetHost.manager.getAppWidgetInfo(widgetId) }.getOrNull()
                source.widgetSizes[widgetId]?.let { value -> info?.let { clampWidgetSize(value, it) } ?: value }
                    ?: info?.let(::defaultWidgetSize)
                    ?: WorkspaceWidgetSize(160, 100)
            }
            widgetGridSpan(size)
        }

    private fun normalizeWidgetPositions(source: WorkspaceLayout): WorkspaceLayout {
        val spans = widgetGridSpans(source)
        val occupied = linkedMapOf<Int, WidgetGridSpan>()
        val cells = linkedMapOf<Int, Int>()
        val previousColumns = source.widgetGridColumns.coerceIn(1, 6)
        visibleWidgetIds(source).forEach { widgetId ->
            val span = spans[widgetId] ?: return@forEach
            val requested = source.widgetCells[widgetId]?.let { WidgetGridPlacement.cell(it, previousColumns) }
            val cell = if (requested == null) {
                WidgetGridPlacement.firstAvailable(span, gridColumns, occupied)
            } else {
                WidgetGridPlacement.nearestAvailable(requested.first, requested.second, span, gridColumns, occupied)
            }
            if (cell != null) {
                cells[widgetId] = cell
                occupied[cell] = span
            }
        }
        return source.copy(widgetCells = cells, widgetGridColumns = gridColumns)
    }

    private fun startWidgetDrag(view: View, widgetId: Int): Boolean {
        if (!ensureWorkspaceUnlocked()) return false
        val clip = ClipData.newPlainText("workspace-widget", widgetId.toString())
        return view.startDragAndDrop(clip, View.DragShadowBuilder(view), WidgetDragPayload(widgetId), 0)
    }

    private fun dropWidgetAt(payload: WidgetDragPayload, x: Float, y: Float) {
        if (!ensureWorkspaceUnlocked() || payload.widgetId !in visibleWidgetIds(layout)) return
        val spans = widgetGridSpans(layout)
        val span = spans[payload.widgetId] ?: return
        val cellWidth = widgetCellWidthPx().coerceAtLeast(1)
        val cellHeight = widgetCellHeightPx().coerceAtLeast(1)
        val requestedColumn = floor((x - widgetColumn.paddingLeft - cellWidth * span.columns / 2f) / cellWidth).toInt()
        val requestedRow = floor((y - cellHeight * span.rows / 2f) / cellHeight).toInt().coerceAtLeast(0)
        val occupied = buildMap {
            visibleWidgetIds(layout).filter { it != payload.widgetId }.forEach { widgetId ->
                val cell = layout.widgetCells[widgetId] ?: return@forEach
                spans[widgetId]?.let { put(cell, it) }
            }
        }
        val cell = WidgetGridPlacement.nearestAvailable(requestedColumn, requestedRow, span, gridColumns, occupied)
        if (cell == null) {
            toast("There is no room for this widget")
            return
        }
        layout = layout.copy(widgetCells = layout.widgetCells + (payload.widgetId to cell))
        saveAndRender()
    }

    private fun showWidgetMenu(widgetId: Int, info: AppWidgetProviderInfo, anchor: View) {
        if (!ensureWorkspaceUnlocked()) return
        val actions = buildList {
            if (info.configure != null) add(WorkspacePopupAction("Configure", "tune") { configureExistingWidget(widgetId, info) })
            add(WorkspacePopupAction("Fine positioning", "position") {
                showAlignmentPicker("${info.loadLabel(packageManager)} position", widgetAlignmentKey(widgetId), "center") { rebuildWidgetViews() }
            })
            if (info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE) add(WorkspacePopupAction("Resize", "resize") {
                editingWidgetId = widgetId
                rebuildWidgetViews()
                toast("Drag the corner handle to resize this widget")
            })
            add(WorkspacePopupAction("Remove widget", "remove", destructive = true) { confirmRemoveWidget(widgetId, info) })
        }
        showWidgetPopup(anchor, "Widget", info.loadLabel(packageManager).toString(),
            runCatching { packageManager.getApplicationIcon(info.provider.packageName) }.getOrNull(), 60, actions)
    }

    private fun showWidgetStackMenu(stackId: Int, anchor: View) {
        if (!ensureWorkspaceUnlocked()) return
        val stack = layout.widgetStacks.firstOrNull { it.stackId == stackId } ?: return
        showWidgetPopup(anchor, "Widget Stack", "${stack.widgetIds.size} widget${if (stack.widgetIds.size == 1) "" else "s"}", null, 60, listOf(
            WorkspacePopupAction("Add widget", "add") { showWidgetPicker(stackId) },
            WorkspacePopupAction("Configure stack", "tune") { showWidgetStackConfiguration(stackId) },
            WorkspacePopupAction("Fine positioning", "position") {
                showAlignmentPicker("Widget Stack position", widgetAlignmentKey(stackId), "center") { rebuildWidgetViews() }
            },
            WorkspacePopupAction("Remove widget", "remove", destructive = true) { confirmRemoveWidgetStack(stackId) },
        ))
    }

    private fun showWidgetStackConfiguration(stackId: Int) {
        val current = layout.widgetStacks.firstOrNull { it.stackId == stackId } ?: return
        val memberIds = current.widgetIds.toMutableList()
        var orientation = current.orientation
        var rotateIntervalSeconds = current.rotateIntervalSeconds
        val root = LinearLayout(this).apply {
            this.orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(4))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.widget_stack_intro)
            textSize = 14f
            setTextColor(currentPalette().textSecondary)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        root.addView(TextView(this).apply {
            text = getString(R.string.widget_stack_scroll_orientation)
            textSize = 14f
            setTextColor(currentPalette().textPrimary)
        })
        val orientationGroup = android.widget.RadioGroup(this).apply {
            this.orientation = android.widget.RadioGroup.HORIZONTAL
        }
        val horizontal = android.widget.RadioButton(this).apply {
            text = getString(R.string.widget_stack_orientation_horizontal)
            isChecked = orientation == WorkspaceWidgetStackOrientation.HORIZONTAL
        }
        val vertical = android.widget.RadioButton(this).apply {
            text = getString(R.string.widget_stack_orientation_vertical)
            isChecked = orientation == WorkspaceWidgetStackOrientation.VERTICAL
        }
        orientationGroup.addView(horizontal)
        orientationGroup.addView(vertical)
        orientationGroup.setOnCheckedChangeListener { _, checkedId ->
            orientation = if (checkedId == vertical.id) WorkspaceWidgetStackOrientation.VERTICAL
                else WorkspaceWidgetStackOrientation.HORIZONTAL
        }
        root.addView(orientationGroup, LinearLayout.LayoutParams(-1, dp(48)))

        val autoRotateSwitch = SwitchCompat(this).apply {
            text = getString(R.string.widget_stack_auto_rotate)
            isChecked = current.autoRotate
            setTextColor(currentPalette().textPrimary)
        }
        root.addView(autoRotateSwitch, LinearLayout.LayoutParams(-1, dp(52)))
        val intervalRow = TextView(this).apply {
            text = getString(R.string.widget_stack_switch_interval, rotateIntervalSeconds)
            textSize = 14f
            setTextColor(currentPalette().textSecondary)
            isClickable = true
            isFocusable = true
            background = selectableBackground()
            setPadding(dp(12), 0, dp(12), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        intervalRow.setOnClickListener {
            val options = intArrayOf(5, 15, 30, 60)
            showSingleChoiceDialog("Switch interval", options.map { "$it seconds" }.toTypedArray(), options.indexOf(rotateIntervalSeconds).coerceAtLeast(0)) { which ->
                rotateIntervalSeconds = options[which]
                intervalRow.text = getString(R.string.widget_stack_switch_interval, rotateIntervalSeconds)
            }
        }
        root.addView(intervalRow, LinearLayout.LayoutParams(-1, dp(48)))

        val resetSwitch = SwitchCompat(this).apply {
            text = getString(R.string.widget_stack_reset_on_return)
            isChecked = current.resetOnReturn
            setTextColor(currentPalette().textPrimary)
        }
        root.addView(resetSwitch, LinearLayout.LayoutParams(-1, dp(52)))

        root.addView(TextView(this).apply {
            text = getString(R.string.widget_stack_widgets_header)
            textSize = 14f
            setTextColor(currentPalette().textPrimary)
            typeface = LauncherTypography.typeface(this@HomeActivity, 600)
            setPadding(0, dp(10), 0, dp(6))
        })
        val memberRows = LinearLayout(this).apply { this.orientation = LinearLayout.VERTICAL }
        root.addView(memberRows, LinearLayout.LayoutParams(-1, -2))

        lateinit var renderMembers: () -> Unit
        renderMembers = {
            memberRows.removeAllViews()
            if (memberIds.isEmpty()) {
                memberRows.addView(TextView(this).apply {
                    text = getString(R.string.widget_stack_no_widgets)
                    textSize = 13f
                    setTextColor(currentPalette().textSecondary)
                    setPadding(0, dp(8), 0, dp(12))
                })
            }
            memberIds.forEach { widgetId ->
                val info = runCatching { widgetHost.manager.getAppWidgetInfo(widgetId) }.getOrNull()
                val label = info?.loadLabel(packageManager) ?: "Unavailable widget"
                val row = LinearLayout(this).apply {
                    this.orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(4), 0)
                    background = roundedDrawable(currentPalette().surfaceVariant, dp(14))
                    contentDescription = "$label. Press and drag to reorder."
                    isFocusable = true
                    isLongClickable = true
                }
                row.addView(SettingsGlyphView(this, "drag", currentPalette().textSecondary),
                    LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
                row.addView(ImageView(this).apply {
                    setImageDrawable(info?.let {
                        runCatching { it.loadIcon(this@HomeActivity, resources.displayMetrics.densityDpi) }.getOrNull()
                    })
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
                row.addView(TextView(this).apply {
                    text = label
                    textSize = 14f
                    setTextColor(currentPalette().textPrimary)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val remove = TextView(this).apply {
                    text = "×"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    contentDescription = "Remove $label from stack"
                    background = selectableBackground()
                    isFocusable = true
                    isClickable = true
                    setOnClickListener { memberIds.remove(widgetId); renderMembers() }
                }
                row.addView(remove, LinearLayout.LayoutParams(dp(44), dp(44)))
                row.setOnLongClickListener {
                    val drag = WidgetStackMemberDrag(stackId, widgetId)
                    row.startDragAndDrop(
                        ClipData.newPlainText("widget-stack-member", widgetId.toString()),
                        View.DragShadowBuilder(row),
                        drag,
                        0,
                    )
                }
                row.setOnDragListener { view, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> event.localState is WidgetStackMemberDrag &&
                            (event.localState as WidgetStackMemberDrag).stackId == stackId
                        DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.72f; true }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1f; true }
                        DragEvent.ACTION_DROP -> {
                            view.alpha = 1f
                            val drag = event.localState as? WidgetStackMemberDrag ?: return@setOnDragListener false
                            val from = memberIds.indexOf(drag.widgetId)
                            val to = memberIds.indexOf(widgetId)
                            if (from >= 0 && to >= 0 && from != to) {
                                val moved = memberIds.removeAt(from)
                                memberIds.add(to, moved)
                                renderMembers()
                            }
                            true
                        }
                        else -> true
                    }
                }
                memberRows.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(6) })
            }
        }
        renderMembers()

        fun saveConfiguration() {
            val latest = layout.widgetStacks.firstOrNull { it.stackId == stackId } ?: return
            val removedIds = latest.widgetIds.filterNot { it in memberIds }
            removedIds.forEach(widgetHost::delete)
            val previousActive = latest.widgetIds.getOrNull(latest.currentIndex)
            val indexAfterReorder = previousActive?.let(memberIds::indexOf)?.takeIf { it >= 0 } ?: 0
            val updatedStack = latest.copy(
                widgetIds = memberIds.toList(),
                orientation = orientation,
                autoRotate = autoRotateSwitch.isChecked && memberIds.size > 1,
                rotateIntervalSeconds = rotateIntervalSeconds,
                resetOnReturn = resetSwitch.isChecked,
                currentIndex = indexAfterReorder,
            ).normalized()
            layout = layout.copy(
                widgetIds = layout.widgetIds.filterNot { it in removedIds },
                widgetSizes = layout.widgetSizes - removedIds.toSet(),
                widgetStacks = layout.widgetStacks.map { if (it.stackId == stackId) updatedStack else it },
            )
            workspaceStore.save(layout)
            rebuildWorkspace()
            scheduleWidgetStackRotations()
        }

        AlertDialog.Builder(this)
            .setTitle("Widget Stack")
            .setView(root)
            .setNeutralButton("Add widget") { _, _ ->
                saveConfiguration()
                showWidgetPicker(stackId)
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done") { _, _ -> saveConfiguration() }
            .show()
    }

    private fun confirmRemoveWidgetStack(stackId: Int) {
        val stack = layout.widgetStacks.firstOrNull { it.stackId == stackId } ?: return
        confirmRemoval("Widget Stack", "Remove this stack and its ${stack.widgetIds.size} hosted widget${if (stack.widgetIds.size == 1) "" else "s"} from Home?", requireUnlocked = false) {
            stack.widgetIds.forEach(widgetHost::delete)
            layout = layout.copy(
                widgetIds = layout.widgetIds.filterNot { it in stack.widgetIds },
                widgetSizes = layout.widgetSizes - stack.widgetIds.toSet(),
                widgetCells = layout.widgetCells - stackId,
                widgetStacks = layout.widgetStacks.filterNot { it.stackId == stackId },
            )
            workspaceStore.save(layout)
            rebuildWorkspace()
            scheduleWidgetStackRotations()
        }
    }

    private fun configureExistingWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        val component = info.configure ?: return
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            this.component = component
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        runCatching { widgetEditConfigureLauncher.launch(intent) }
            .onFailure { toast("This widget has no available configuration screen") }
    }

    private fun confirmRemoveWidget(widgetId: Int, info: AppWidgetProviderInfo) {
        confirmRemoval(info.loadLabel(packageManager).toString(), "Remove this widget from Home?", requireUnlocked = false) {
            widgetHost.delete(widgetId)
            editingWidgetId = null
            layout = layout.copy(
                widgetIds = layout.widgetIds - widgetId,
                widgetSizes = layout.widgetSizes - widgetId,
                widgetCells = layout.widgetCells - widgetId,
            )
            saveAndRender()
        }
    }

    private fun widgetEditToolbar(info: AppWidgetProviderInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(6), 0)
        background = roundedDrawable(currentPalette().surfaceVariant, dp(18))
        addView(SettingsGlyphView(this@HomeActivity, "resize", currentPalette().accent),
            LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        addView(TextView(this@HomeActivity).apply {
            text = getString(R.string.widget_resize_title, info.loadLabel(packageManager))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(currentPalette().textPrimary)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(TextView(this@HomeActivity).apply {
            text = getString(R.string.action_done)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(currentPalette().onAccent)
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = dp(56)
            minHeight = dp(48)
            isFocusable = true
            background = roundedDrawable(currentPalette().accent, dp(24))
            contentDescription = "Finish resizing widget"
            setOnClickListener {
                editingWidgetId = null
                rebuildWidgetViews()
            }
        }, LinearLayout.LayoutParams(-2, dp(48)))
    }

    private fun createResizeHandle(
        widgetId: Int,
        info: AppWidgetProviderInfo,
        container: FrameLayout,
        hostView: android.appwidget.AppWidgetHostView,
    ): View = FrameLayout(this).apply {
        background = roundedDrawable(currentPalette().accent, dp(24))
        elevation = dp(3).toFloat()
        isFocusable = true
        contentDescription = "Resize ${info.loadLabel(packageManager)}"
        addView(SettingsGlyphView(this@HomeActivity, "resize", currentPalette().onAccent),
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))

        val bounds = widgetResizeBounds(info)
        val initialSize = layout.widgetSizes[widgetId] ?: defaultWidgetSize(info)
        var startX = 0f
        var startY = 0f
        var previewSize = initialSize
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    previewSize = layout.widgetSizes[widgetId] ?: defaultWidgetSize(info)
                    container.parent?.requestDisallowInterceptTouchEvent(true)
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val density = resources.displayMetrics.density
                    previewSize = WidgetResizeRules.resize(
                        initial = layout.widgetSizes[widgetId] ?: initialSize,
                        deltaWidthDp = ((event.rawX - startX) / density).roundToInt(),
                        deltaHeightDp = ((event.rawY - startY) / density).roundToInt(),
                        bounds = bounds,
                    )
                    applyWidgetSizePreview(container, hostView, previewSize)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    layout = normalizeWidgetPositions(layout.copy(widgetSizes = layout.widgetSizes + (widgetId to previewSize)))
                    workspaceStore.save(layout)
                    updateHostedWidgetSize(hostView, previewSize)
                    view.post { rebuildWorkspace() }
                    performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    applyWidgetSizePreview(container, hostView, layout.widgetSizes[widgetId] ?: initialSize)
                    true
                }
                else -> false
            }
        }
    }

    private fun applyWidgetSizePreview(
        container: FrameLayout,
        hostView: android.appwidget.AppWidgetHostView,
        size: WorkspaceWidgetSize,
    ) {
        val width = dp(size.widthDp)
        val height = dp(size.heightDp)
        container.layoutParams = (container.layoutParams as? FrameLayout.LayoutParams)?.apply {
            this.width = width + dp(16)
            this.height = height + dp(16)
        }
        hostView.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
        container.requestLayout()
    }

    private fun defaultWidgetSize(info: AppWidgetProviderInfo): WorkspaceWidgetSize {
        val density = resources.displayMetrics.density
        val maxWidth = ((resources.displayMetrics.widthPixels - dp(48)) / density).roundToInt().coerceAtLeast(1)
        val maxHeight = ((resources.displayMetrics.heightPixels * 0.72f) / density).roundToInt().coerceAtLeast(1)
        val defaultSize = WorkspaceWidgetSize(
            widthDp = (info.minWidth / density).roundToInt().coerceAtLeast(1).coerceAtMost(maxWidth),
            heightDp = (info.minHeight / density).roundToInt().coerceAtLeast(1).coerceAtMost(maxHeight),
        )
        return clampWidgetSize(defaultSize, info)
    }

    private fun widgetAlignmentKey(widgetId: Int) = "widget_alignment_$widgetId"

    private fun clampWidgetSize(size: WorkspaceWidgetSize, info: AppWidgetProviderInfo): WorkspaceWidgetSize {
        val bounds = widgetResizeBounds(info)
        return WorkspaceWidgetSize(
            widthDp = size.widthDp.coerceIn(bounds.minWidthDp, bounds.maxWidthDp),
            heightDp = size.heightDp.coerceIn(bounds.minHeightDp, bounds.maxHeightDp),
        )
    }

    private fun widgetResizeBounds(info: AppWidgetProviderInfo): WidgetResizeBounds {
        val density = resources.displayMetrics.density
        fun toDp(px: Int) = (px / density).roundToInt().coerceAtLeast(1)
        val minWidth = toDp(info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth)
        val minHeight = toDp(info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight)
        val screenMaxWidth = toDp(resources.displayMetrics.widthPixels - dp(48))
        val screenMaxHeight = toDp((resources.displayMetrics.heightPixels * 0.72f).roundToInt())
        val providerMaxWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeWidth > info.minWidth) {
            toDp(info.maxResizeWidth)
        } else screenMaxWidth
        val providerMaxHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > info.minHeight) {
            toDp(info.maxResizeHeight)
        } else screenMaxHeight
        val maxWidth = minOf(screenMaxWidth, providerMaxWidth).coerceAtLeast(minWidth)
        val maxHeight = minOf(screenMaxHeight, providerMaxHeight).coerceAtLeast(minHeight)
        return WidgetResizeBounds(
            minWidthDp = minWidth,
            minHeightDp = minHeight,
            maxWidthDp = maxWidth,
            maxHeightDp = maxHeight,
            canResizeHorizontally = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            canResizeVertically = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
    }

    private fun updateHostedWidgetSize(hostView: android.appwidget.AppWidgetHostView, size: WorkspaceWidgetSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hostView.updateAppWidgetSize(Bundle(), mutableListOf(SizeF(size.widthDp.toFloat(), size.heightDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            hostView.updateAppWidgetSize(Bundle(), size.widthDp, size.heightDp, size.widthDp, size.heightDp)
        }
    }

    private fun openSettings(page: String? = null) {
        startActivity(Intent(this, LauncherSettingsActivity::class.java).apply {
            page?.let { putExtra(LauncherSettingsActivity.EXTRA_PAGE, it) }
        })
    }

    private fun performWorkspaceSwipe(dx: Float, dy: Float) {
        if (drawerOpen) {
            if (dy > 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                closeDrawer()
            } else if (isIsolationEnabled() && !drawerReorderMode && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f && kotlin.math.abs(dx) > dp(42)) {
                if (dx < 0 && !drawerShowingWorkAppsPage) openWorkAppsPage()
                else if (dx > 0 && drawerShowingWorkAppsPage) showAllAppsPage()
            }
            return
        }
        // GestureFrameLayout has already waited for touch slop before invoking this callback.
        val gesture = WorkspaceGestureClassifier.oneFinger(dx, dy, threshold = 1f) ?: return
        performWorkspaceGesture(gesture)
    }

    private fun performWorkspaceGesture(gesture: WorkspaceSurfaceGesture) {
        val suffix = gesture.storageKey
        val actionKey = LauncherSettings.KEY_WORKSPACE_GESTURE_ACTION_PREFIX + suffix
        val assignedGesturesEnabled = prefs.getBoolean(LauncherSettings.KEY_WORKSPACE_GESTURES_ENABLED, false)
        val hasAssignment = prefs.contains(actionKey)
        if (WorkspaceGestureRouting.shouldRunAssignedAction(hasAssignment, assignedGesturesEnabled)) {
            // Default single-finger navigation remains available when assigned actions are disabled.
            if (prefs.getBoolean(LauncherSettings.KEY_WORKSPACE_GESTURE_VIBRATION, false)) {
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            val assignment = WorkspaceIconGestureAssignment(
                gesture = gesture.pickerGesture,
                action = WorkspaceIconGestureAction.fromStorage(prefs.getString(actionKey, null)),
                targetPackage = prefs.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_PACKAGE_PREFIX + suffix, null),
                targetClass = prefs.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_CLASS_PREFIX + suffix, null),
                targetLabel = prefs.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_LABEL_PREFIX + suffix, null),
                targetShortcutId = prefs.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_SHORTCUT_ID_PREFIX + suffix, null),
            )
            performGestureAssignment(assignment)
            return
        }
        if (!WorkspaceGestureRouting.shouldRunLegacyNavigation(gesture, hasAssignment, assignedGesturesEnabled)) return

        // Preserve the earlier Teres defaults for users who have not explicitly assigned a gesture.
        when (gesture) {
            WorkspaceSurfaceGesture.SWIPE_UP -> if (prefs.getBoolean(LauncherSettings.KEY_GESTURE_SWIPE_UP, true)) openDrawer()
            WorkspaceSurfaceGesture.SWIPE_DOWN -> if (prefs.getBoolean(LauncherSettings.KEY_GESTURE_SWIPE_DOWN, false)) openTeresSearch()
            WorkspaceSurfaceGesture.SWIPE_LEFT -> if (prefs.getBoolean(LauncherSettings.KEY_GESTURE_PAGE_SWIPE, true)) {
                moveToPage(WorkspacePageOrder.pageAfterHorizontalSwipe(
                    currentPage,
                    delta = 1,
                    pageCount = workspacePageCount(),
                    reversed = isHomePageOrderReversed(),
                ))
            }
            WorkspaceSurfaceGesture.SWIPE_RIGHT -> if (prefs.getBoolean(LauncherSettings.KEY_GESTURE_PAGE_SWIPE, true)) {
                moveToPage(WorkspacePageOrder.pageAfterHorizontalSwipe(
                    currentPage,
                    delta = -1,
                    pageCount = workspacePageCount(),
                    reversed = isHomePageOrderReversed(),
                ))
            }
            else -> Unit
        }
    }

    private fun View.containsScreenPoint(screenX: Float, screenY: Float): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return screenX >= loc[0] && screenX < loc[0] + width && screenY >= loc[1] && screenY < loc[1] + height
    }

    private fun isOccupiedWorkspacePoint(x: Float, y: Float): Boolean {
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val screenX = rootLocation[0] + x
        val screenY = rootLocation[1] + y

        val controls = listOfNotNull(
            clockHeader.takeIf { ::clockHeader.isInitialized },
            batteryTile.takeIf { ::batteryTile.isInitialized },
            homeDock.takeIf { ::homeDock.isInitialized },
            searchButton.takeIf { ::searchButton.isInitialized },
        )
        if (controls.any { it.isVisible && it.containsScreenPoint(screenX, screenY) }) return true

        if (::homeGrid.isInitialized && homeGrid.containsScreenPoint(screenX, screenY)) {
            for (index in 0 until homeGrid.childCount) {
                val cell = homeGrid.getChildAt(index)
                if (cell.containsScreenPoint(screenX, screenY)) {
                    val cellIndex = cell.tag as? Int ?: index
                    if (layout.items.any { it.page == currentPage && it.cell == cellIndex }) return true
                }
            }
        }

        if (::widgetColumn.isInitialized && visibleWidgetIds(layout).isNotEmpty()) {
            for (index in 0 until widgetColumn.childCount) {
                val widget = widgetColumn.getChildAt(index)
                if (widget.isVisible && widget.containsScreenPoint(screenX, screenY)) return true
            }
        }
        return false
    }

    private fun isInsideHomeGridScreenPoint(screenX: Float, screenY: Float): Boolean =
        ::homeGrid.isInitialized && homeGrid.isVisible && homeGrid.containsScreenPoint(screenX, screenY)

    private inner class WorkspaceWidgetScrollView(context: Context) : ScrollView(context) {
        var shouldYieldToWorkspaceArea: ((screenX: Float, screenY: Float) -> Boolean)? = null
        private var yieldTouchStreamToWorkspace = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                yieldTouchStreamToWorkspace = shouldYieldToWorkspaceArea?.invoke(event.rawX, event.rawY) == true
            }
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                yieldTouchStreamToWorkspace = false
            }
            return handled
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
            if (yieldTouchStreamToWorkspace) false else super.onInterceptTouchEvent(event)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (yieldTouchStreamToWorkspace) return false
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean = super.performClick()
    }

    private fun isPointInsideHomeGrid(x: Float, y: Float): Boolean {
        if (!::homeGrid.isInitialized || !::root.isInitialized) return false
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        return homeGrid.containsScreenPoint(rootLocation[0] + x, rootLocation[1] + y)
    }

    private fun requestAppUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        runCatching { startActivity(intent) }
            .onFailure { toast("Uninstall is unavailable for this app") }
    }

    private fun showAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        runCatching { startActivity(intent) }.onFailure { toast("App details are unavailable") }
    }

    private fun moveToPage(page: Int) {
        currentPage = page.coerceIn(0, workspacePageCount() - 1)
        rebuildWorkspace()
    }

    private fun updatePageIndicator() {
        if (!::pageIndicator.isInitialized) return
        val pages = workspacePageCount()
        val showIndicator = prefs.getBoolean(LauncherSettings.KEY_HOME_PAGE_INDICATOR, true)
        val displayedPage = WorkspacePageOrder.displayIndex(currentPage, pages, isHomePageOrderReversed())
        pageIndicator.visibility = if (pages == 1 || !showIndicator) View.GONE else View.VISIBLE
        pageIndicator.text = (0 until pages).joinToString("   ") { if (it == displayedPage) "●" else "○" }
        pageIndicator.contentDescription = "Page ${displayedPage + 1} of $pages"
        updateWallpaperOffset(pages)
    }

    private fun updateWallpaperOffset(pages: Int) {
        if (!::root.isInitialized) return
        root.post {
            val windowToken = root.rootView.windowToken ?: return@post
            val parallaxOffset = if (prefs.getBoolean(LauncherSettings.KEY_WALLPAPER_PARALLAX, false)) {
                -wallpaperTiltX * WALLPAPER_PARALLAX_FACTOR
            } else 0f
            val offset = WallpaperOffsets.forPage(
                page = currentPage,
                pageCount = pages,
                reversed = isHomePageOrderReversed(),
                scrollable = prefs.getBoolean(LauncherSettings.KEY_WALLPAPER_SCROLLABLE, true),
                parallaxOffsetX = parallaxOffset,
            )
            runCatching {
                wallpaperManager.setWallpaperOffsetSteps(offset.stepX, 1f)
                wallpaperManager.setWallpaperOffsets(windowToken, offset.offsetX, 0.5f)
            }
        }
    }

    private fun updateWallpaperParallaxListener() {
        sensorManager.unregisterListener(wallpaperTiltListener)
        wallpaperTiltX = 0f
        if (!prefs.getBoolean(LauncherSettings.KEY_WALLPAPER_PARALLAX, false)) return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(wallpaperTiltListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun workspacePageCount(): Int = prefs.workspacePageCount(layout)

    private fun saveAndRender() {
        workspaceStore.save(layout)
        rebuildWorkspace()
    }

    private fun findApp(shortcut: WorkspaceShortcut): LaunchableApp? = apps.firstOrNull {
        it.entry.packageName == shortcut.packageName && it.entry.className == shortcut.className
    }

    private fun LauncherAppEntry.toShortcut() = WorkspaceShortcut(packageName, className, label)

    private fun firstEmptyWorkspaceCell(): Int? {
        val occupied = layout.items.filter { it.page == currentPage }.mapTo(mutableSetOf()) { it.cell }
        return WorkspacePlacement.firstEmptyCellFromBottom(occupied, gridColumns, gridRows)
    }

    private fun drawerTabButton(labelRes: Int, description: String, onClick: () -> Unit) = TextView(this).apply {
        text = getString(labelRes)
        textSize = 13f
        gravity = Gravity.CENTER
        typeface = LauncherTypography.typeface(this@HomeActivity, 600)
        isFocusable = true
        contentDescription = description
        setOnClickListener { onClick() }
    }

    private fun drawerPillChip(
        label: String,
        textSize: Float,
        minHeightDp: Int,
        horizontalPadDp: Int,
        bgColor: Int,
        textColor: Int,
        boldWeight: Int = 500,
        contentDesc: String,
        onClick: () -> Unit,
    ) = TextView(this).apply {
        text = label
        this.textSize = textSize
        typeface = LauncherTypography.typeface(this@HomeActivity, boldWeight)
        setTextColor(textColor)
        gravity = Gravity.CENTER
        minHeight = dp(minHeightDp)
        setPadding(dp(horizontalPadDp), 0, dp(horizontalPadDp), 0)
        background = roundedDrawable(bgColor, dp(22))
        isFocusable = true
        contentDescription = contentDesc
        setOnClickListener { onClick() }
    }

    private fun updateDrawerTitle() {
        drawerTitle.text = when {
            drawerReorderMode -> "Arrange apps"
            drawerIndexMode -> "Search apps by index"
            drawerIndexFilter != null -> drawerIndexFilter
            drawerShowingWorkAppsPage -> "Haven Vault"
            selectedDrawerCategory == DrawerCategories.ALL -> "All apps"
            else -> selectedDrawerCategory
        }
    }

    private fun showSingleChoiceDialog(
        title: String,
        labels: Array<String>,
        selected: Int,
        negativeLabel: String = "Cancel",
        onSelect: (Int) -> Unit,
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                onSelect(which)
            }
            .setNegativeButton(negativeLabel, null)
            .show()
    }

    private fun showAlignmentPicker(title: String, prefKey: String, defaultValue: String, onSaved: () -> Unit) {
        val values = arrayOf("start", "center", "end")
        val selected = values.indexOf(prefs.getString(prefKey, defaultValue)).coerceAtLeast(0)
        showSingleChoiceDialog(title, arrayOf("Left", "Center", "Right"), selected) { which ->
            prefs.edit { putString(prefKey, values[which]) }
            onSaved()
        }
    }

    private fun alignmentGravity(alignment: String?, defaultStart: Boolean = false): Int = when (alignment) {
        "center" -> Gravity.CENTER_HORIZONTAL
        "end" -> Gravity.END
        "start" -> Gravity.START
        else -> if (defaultStart) Gravity.START else Gravity.CENTER_HORIZONTAL
    }

    private fun confirmRemoval(title: String, message: String, requireUnlocked: Boolean = true, onConfirm: () -> Unit) {
        if (requireUnlocked && !ensureWorkspaceUnlocked()) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Keep", null)
            .setPositiveButton("Remove") { _, _ -> onConfirm() }
            .show()
    }

    private fun showRenameDialog(title: String, initialText: String, hintText: String, onSave: (String?) -> Unit) {
        if (!ensureWorkspaceUnlocked()) return
        val nameField = EditText(this).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initialText)
            setSelection(text.length)
            hint = hintText
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(nameField)
            .setPositiveButton("Save") { _, _ -> onSave(nameField.text.toString().trim().takeIf(String::isNotBlank)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showActionListDialog(title: String, actions: List<Pair<String, () -> Unit>>, showCancel: Boolean = false) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .apply { if (showCancel) setNegativeButton("Cancel", null) }
            .show()
    }

    private fun showWidgetPopup(
        anchor: View,
        heading: String,
        subtitle: String?,
        artwork: Drawable?,
        anchorBottomOffsetDp: Int,
        actions: List<WorkspacePopupAction>,
    ) {
        val selection = showWidgetSelectionFrame(anchor)
        WorkspaceItemPopup(
            activity = this,
            palette = currentPalette(),
            anchor = anchor,
            heading = heading,
            subtitle = subtitle,
            artwork = artwork,
            actions = actions,
            onDismiss = { selection?.let(::removeWidgetSelectionFrame) },
            anchorBottomOffsetDp = anchorBottomOffsetDp,
        ).show()
    }

    private fun createHostedWidgetView(widgetId: Int): Pair<AppWidgetProviderInfo, android.appwidget.AppWidgetHostView>? {
        val info = runCatching { widgetHost.manager.getAppWidgetInfo(widgetId) }.getOrNull() ?: return null
        val hostView = runCatching { widgetHost.host.createView(this, widgetId, info) }.getOrNull() ?: return null
        hostView.setAppWidget(widgetId, info)
        return info to hostView
    }

    private fun applyWallpaperDim() {
        val dim = prefs.getBoolean(LauncherSettings.KEY_DIM_WALLPAPER, false)
        val darkenExtraPages = prefs.getBoolean(LauncherSettings.KEY_DARKEN_EXTRA_HOME_PAGES, false)
        val overlay = when {
            currentPage > 0 && darkenExtraPages -> 0x66000000
            dim -> 0x44000000
            else -> Color.TRANSPARENT
        }
        homeLayer.setBackgroundColor(overlay)
    }

    private fun isHomePageOrderReversed(): Boolean = prefs.getBoolean(LauncherSettings.KEY_REVERSE_HOME_PAGE_ORDER, false)

    private fun refreshAppearance() {
        LauncherAppearance.load(this) { resolved ->
            if (isFinishing || isDestroyed) return@load
            palette = resolved
            applyPaletteTheme(resolved)
            if (::drawerLayer.isInitialized) {
                drawerLayer.background = drawerBackdrop()
                if (::drawerTitle.isInitialized) drawerTitle.setTextColor(Color.WHITE)
                if (::drawerSearch.isInitialized) {
                    drawerSearch.setTextColor(Color.WHITE)
                    drawerSearch.setHintTextColor(0xFFCBC5D0.toInt())
                    drawerSearch.background = roundedDrawable(0x55231D2B, dp(24))
                }
                if (::drawerCategories.isInitialized) renderDrawerCategories()
                if (::drawerGrid.isInitialized && drawerOpen) renderDrawerApps(drawerSearch.text.toString())
            }
            updateSystemBarAppearance()
            if (::homeLayer.isInitialized) applyWallpaperDim()
        }
    }

    private fun updateSystemBarAppearance() {
        if (!::root.isInitialized) return
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun applyHomeSystemBarsPreference() {
        val hideStatusBar = prefs.getBoolean(LauncherSettings.KEY_HOME_HIDE_STATUS_BAR, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = if (hideStatusBar) {
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            if (hideStatusBar) hide(WindowInsetsCompat.Type.statusBars())
            else show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun applyPaletteTheme(palette: LauncherPalette) {
        setTheme(if (palette.isDark) R.style.Theme_Teres else R.style.Theme_Teres_Light)
        LauncherTypography.applyTheme(this)
    }

    private fun currentPalette() = palette ?: LauncherAppearance.resolve(this)

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun cellHeightDp(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return ((widthDp / gridColumns) * 0.86f).roundToInt().coerceIn(72, 108)
    }

    private fun homeIconSizeDp(): Int {
        val configuredSize = prefs.getInt(LauncherSettings.KEY_HOME_ICON_SIZE, LauncherSettings.HOME_ICON_SIZE_DEFAULT_DP)
            .coerceIn(LauncherSettings.HOME_ICON_SIZE_MIN_DP, LauncherSettings.HOME_ICON_SIZE_MAX_DP)
        val showLabels = prefs.getBoolean(LauncherSettings.KEY_HOME_SHOW_LABELS, false)
        return if (showLabels) minOf(configuredSize, (HOME_GRID_ROW_HEIGHT_DP - 24).coerceAtLeast(24)) else configuredSize
    }

    private fun workspaceIconSizeDp(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val availableCellWidth = (widthDp - 36f) / gridColumns - gridColumnSpacingDp
        return minOf(homeIconSizeDp(), availableCellWidth.toInt().coerceAtLeast(24))
    }

    private fun homeIconArtworkSizeDp(): Int {
        // Folder discs share the same 0.90 optical scale as app icons. Keeping the folder
        // background at the full touch-cell size made folder previews visibly larger than apps.
        return (homeIconSizeDp() * HOME_ICON_OPTICAL_SCALE).roundToInt().coerceAtLeast(1)
    }

    private fun positionWorkspaceSections() {
        if (!::homeGrid.isInitialized || !::root.isInitialized) return
        val dockAnchor = when {
            ::homeDock.isInitialized && homeDock.isVisible && homeDock.height > 0 -> homeDock
            ::searchButton.isInitialized && searchButton.height > 0 -> searchButton
            else -> null
        }
        if (dockAnchor != null && root.height > 0) {
            val anchorLocation = IntArray(2)
            val rootLocation = IntArray(2)
            dockAnchor.getLocationOnScreen(anchorLocation)
            root.getLocationOnScreen(rootLocation)
            val anchorTop = anchorLocation[1] - rootLocation[1]
            val lastRowTop = anchorTop - dp(HOME_GRID_ROW_HEIGHT_DP)
            val params = homeGrid.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(
                    resources.displayMetrics.widthPixels - dp(28),
                    dp(gridRows * HOME_GRID_ROW_HEIGHT_DP),
                    Gravity.TOP or Gravity.START,
                ).apply { leftMargin = dp(28) }
            params.width = resources.displayMetrics.widthPixels - dp(28)
            params.height = dp(gridRows * HOME_GRID_ROW_HEIGHT_DP)
            params.topMargin = (lastRowTop - dp((gridRows - 1) * HOME_GRID_ROW_HEIGHT_DP)).coerceAtLeast(0)
            homeGrid.layoutParams = params
        }
        if (!::scrollViewport.isInitialized || !::workspaceScroll.isInitialized || scrollViewport.height <= 0) return
        val topInsetPx = dp(APP_GRID_TOP_SPACER_DP)
        (workspaceScroll.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.width = -1
            params.topMargin = topInsetPx
            params.height = (scrollViewport.height - params.topMargin).coerceAtLeast(0)
            workspaceScroll.layoutParams = params
            workspaceScroll.visibility = if (visibleWidgetIds(layout).isNotEmpty() && params.height > 0) View.VISIBLE else View.GONE
        }
    }

    private data class DragPayload(val sourcePage: Int, val sourceCell: Int)
    private data class DrawerDragPayload(val componentKey: String)
    private data class WidgetDragPayload(val widgetId: Int)
    private data class WidgetStackMemberDrag(val stackId: Int, val widgetId: Int)

    companion object {
        const val EXTRA_OPEN_VAULT = "com.arcturus.haven.extra.OPEN_VAULT"
        private const val DOCK_PACKAGE_SEPARATOR = "\n"
        private const val CAMERA_PACKAGE_ANDROID = "com.android.camera2"
        private const val CAMERA_PACKAGE_GOOGLE = "com.google.android.camera2"
        private const val STACK_WIDGET_DEFAULT_HORIZONTAL_INSET_DP = 8
        private const val STACK_WIDGET_FRAME_INSET_DP = 8
        private const val APP_GRID_TOP_SPACER_DP = 14
        private const val DRAWER_ALL_APPS_ROW_PITCH_DP = 126
        private const val DRAWER_CATEGORIZED_ROW_PITCH_DP = 108
        private const val DRAWER_INDEX_CELL_HEIGHT_DP = 60
        private const val DRAWER_INDEX_VERTICAL_SPACING_DP = 12
        private const val HOME_GRID_ROW_HEIGHT_DP = 92
        private const val HOME_DOCK_MIN_HEIGHT_DP = 68
        private const val HOME_CLOCK_WEIGHT = 500
        private const val SUNNY_WEATHER_TINT = 0xFFFFC642.toInt()
        private const val WALLPAPER_PARALLAX_FACTOR = 0.05f
        private const val WALLPAPER_MOTION_FRAME_MS = 33L
    }
}
