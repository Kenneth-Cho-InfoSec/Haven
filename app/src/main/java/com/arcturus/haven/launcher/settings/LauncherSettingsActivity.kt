package com.arcturus.haven.launcher.settings

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isEmpty
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doOnTextChanged
import com.arcturus.haven.R
import com.arcturus.haven.core.HavenExperienceMode
import com.arcturus.haven.core.HavenModePreferences
import com.arcturus.haven.launcher.bridge.HavenProfileSession
import com.arcturus.haven.launcher.bridge.VaultProfileState
import com.arcturus.haven.launcher.core.data.DrawerSortMode
import com.arcturus.haven.launcher.core.data.WorkspaceGridSpec
import com.arcturus.haven.launcher.core.data.WorkspacePlacement
import com.arcturus.haven.launcher.core.data.WorkspaceRepository
import com.arcturus.haven.launcher.core.model.DrawerLaunchView
import com.arcturus.haven.launcher.core.model.IconShapes
import com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAction
import com.arcturus.haven.launcher.core.model.WorkspaceSurfaceGesture
import com.arcturus.haven.launcher.core.permissions.LauncherActionAccessibilityService
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherPalette
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.settings.drawerColumns
import com.arcturus.haven.launcher.core.settings.drawerIconSize
import com.arcturus.haven.launcher.core.settings.drawerRowSpacingDp
import com.arcturus.haven.launcher.core.settings.hasMediaSearchPermission
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import com.arcturus.haven.launcher.core.settings.mediaSearchPermissions
import com.arcturus.haven.launcher.core.settings.workspacePageCount
import com.arcturus.haven.launcher.core.ui.chooseClockPosition
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.showFullscreen
import com.arcturus.haven.launcher.core.ui.toast
import com.arcturus.haven.launcher.launcher.CircularAppIconDrawable
import com.arcturus.haven.launcher.launcher.HomeRoleManager
import com.arcturus.haven.launcher.launcher.IconPackRepository
import com.arcturus.haven.launcher.launcher.LaunchableApp
import com.arcturus.haven.launcher.launcher.LauncherCatalog
import com.arcturus.haven.launcher.launcher.NotificationHighlightService
import com.arcturus.haven.launcher.launcher.WorkspaceGestureActionPickerDialog
import com.arcturus.haven.services.IHavenService
import com.arcturus.haven.ui.MainActivity
import com.arcturus.haven.ui.SetupWizardActivity
import com.arcturus.haven.ui.WelcomeModeActivity
import com.arcturus.haven.util.SettingsManager
import com.arcturus.haven.util.Utility
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class LauncherSettingsActivity : ComponentActivity() {
    private data class GesturePickerOption(
        val id: String,
        val title: String,
        val group: String,
        val glyph: String,
        val summary: String,
        val requiresAccessibility: Boolean = false,
    )

    private data class StringBackupSpec(val key: String, val default: String, val allowed: Set<String>? = null)
    private data class IntBackupSpec(val key: String, val default: Int, val range: IntRange? = null, val clampOnExport: Boolean = false)

    private val roleManager by lazy { HomeRoleManager(this) }
    private val catalog by lazy { LauncherCatalog(this) }
    private val workspace by lazy { WorkspaceRepository(this) }
    private val preferences by lazy { launcherPrefs() }
    private val fileWorker = Executors.newSingleThreadExecutor { Thread(it, "Teres-settings-files").apply { isDaemon = true } }
    private val pageHistory = mutableListOf<String>()
    private var page = "root"
    private var iconAppearanceScope = "home"
    private var homeAdvancedSettingsExpanded = false
    private var pendingShortcutPicker = false
    private var pendingSearchPermissionKey: String? = null
    private val iconPreviewArtwork = linkedMapOf<ImageView, Drawable>()
    private val shapeRows = mutableListOf<LinearLayout>()
    private var homeGridPreviewIcons: List<ImageView> = emptyList()
    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var titleView: TextView

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val isDefaultHome = roleManager.isDefaultHome()
        if (pendingShortcutPicker) {
            pendingShortcutPicker = false
            if (isDefaultHome) chooseDoubleTapShortcut()
            else toast("Shortcut browsing needs Teres Launcher to be the selected Home app")
        } else {
            toast(if (isDefaultHome) "Teres Launcher is your home app" else "Choose Teres Launcher to open Home")
        }
    }
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }
    private val wallpaperCropResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) toast("Wallpaper updated")
    }
    private val wallpaperPhotoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val cropIntent = runCatching { WallpaperManager.getInstance(this).getCropAndSetWallpaperIntent(uri) }
            .getOrElse {
                toast("A wallpaper crop screen is unavailable")
                return@registerForActivityResult
            }
        cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        cropIntent.clipData = ClipData.newUri(contentResolver, "Wallpaper image", uri)
        runCatching { wallpaperCropResult.launch(cropIntent) }.onFailure { toast("The selected photo could not be opened") }
    }
    private val tryStartWorkServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        HavenProfileSession.onTryStartResult(it)
    }
    private val bindWorkServiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        HavenProfileSession.onBindWorkResult(it)
    }
    private val setupWizardLauncher = registerForActivityResult(SetupWizardActivity.SetupWizardContract()) {
        if (page == "isolation") renderPage()
    }
    private val contactsPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handleSearchPermissionResult(LauncherSettings.KEY_SEARCH_CONTACTS, granted, false, "Contact search enabled", "Contact permission was not granted")
    }
    private val calendarPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        handleSearchPermissionResult(LauncherSettings.KEY_SEARCH_CALENDAR, granted, true, "Calendar search enabled", "Calendar permission was not granted")
    }
    private val mediaPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        handleSearchPermissionResult(LauncherSettings.KEY_SEARCH_MEDIA, hasMediaSearchPermission(), true, "Media search enabled", "Media permission was not granted")
    }

    private fun handleSearchPermissionResult(
        key: String,
        granted: Boolean,
        writeFalseOnDenial: Boolean,
        enabledMessage: String,
        deniedMessage: String,
    ) {
        if (pendingSearchPermissionKey == key) {
            if (granted) {
                preferences.edit { putBoolean(key, true) }
                toast(enabledMessage)
            } else {
                if (writeFalseOnDenial) preferences.edit { putBoolean(key, false) }
                toast(deniedMessage)
            }
            pendingSearchPermissionKey = null
        }
        if (page == "search") renderPage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setTheme(if (LauncherAppearance.resolve(this).isDark) R.style.Theme_Teres else R.style.Theme_Teres_Light)
        super.onCreate(savedInstanceState)
        LauncherTypography.applyTheme(this)
        page = intent.getStringExtra(EXTRA_PAGE) ?: "root"
        iconAppearanceScope = preferences.getString(LauncherSettings.KEY_ICON_APPEARANCE_SCOPE, "home")
            .takeIf { it == "drawer" } ?: "home"
        renderPage()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageHistory.isNotEmpty()) {
                    page = pageHistory.removeAt(pageHistory.lastIndex)
                    renderPage()
                } else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        HavenProfileSession.registerHost(this, tryStartWorkServiceLauncher, bindWorkServiceLauncher)
        LauncherTypography.applyTheme(this)
        if (::root.isInitialized) LauncherTypography.applyToViewTree(this, root)
        if (page in RESUME_REFRESH_PAGES && ::root.isInitialized) renderPage()
    }

    override fun onDestroy() {
        HavenProfileSession.unregisterHost(this)
        super.onDestroy()
    }

    private fun renderPage() {
        val iconAppearancePage = page == "icon_appearance"
        val followsLightTheme = !LauncherAppearance.resolve(this).isDark
        val rootUsesLightSurface = page == "root" && followsLightTheme
        val lightAppearance = page == "appearance" || iconAppearancePage
        val lightSurface = !iconAppearancePage && followsLightTheme
        val largePageHeader = page in LARGE_HEADER_PAGES
        val pageSurface = if (lightSurface) Color.WHITE else Color.BLACK
        val primaryText = if (lightSurface && !iconAppearancePage) INK_PRIMARY else Color.WHITE
        val barStyle = if (lightSurface) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(
            statusBarStyle = if (iconAppearancePage) SystemBarStyle.dark(Color.TRANSPARENT) else barStyle,
            navigationBarStyle = barStyle,
        )
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageSurface)
            if (iconAppearancePage) background = checkerboardBackground()
        }
        val toolbarHorizontal = when {
            iconAppearancePage || lightAppearance -> dp(12)
            rootUsesLightSurface -> dp(8)
            lightSurface -> dp(12)
            else -> dp(22)
        }
        val contentInset = when {
            iconAppearancePage -> dp(24)
            rootUsesLightSurface -> dp(16)
            lightSurface -> dp(24)
            else -> dp(22)
        }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(if (iconAppearancePage) dp(12) else toolbarHorizontal, dp(7), if (iconAppearancePage) dp(8) else toolbarHorizontal, dp(7))
            if (rootUsesLightSurface) minimumHeight = dp(56)
            if (iconAppearancePage) background = checkerboardBackground()
        }
        if (page == "root") {
            toolbar.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_teres_mark)
                contentDescription = "Haven"
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(if (rootUsesLightSurface) 48 else 24), dp(if (rootUsesLightSurface) 48 else 24)).apply {
                marginEnd = dp(if (rootUsesLightSurface) 16 else 24)
            })
        } else {
            val backButton = if (iconAppearancePage) {
                toolbarGlyphButton("arrow_back", primaryText, "Back", 22, selectableBackground()) {
                    onBackPressedDispatcher.onBackPressed()
                }
            } else {
                SettingsGlyphView(this, "back", primaryText).apply {
                    contentDescription = "Back"
                    isClickable = true
                    isFocusable = true
                    background = selectableBackground()
                    setOnClickListener { onBackPressedDispatcher.onBackPressed() }
                }
            }
            toolbar.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(if (iconAppearancePage) 13 else 12)
            })
        }
        titleView = TextView(this).apply {
            text = if (page == "appearance" || largePageHeader) "" else PAGE_TITLES[page] ?: "Settings"
            textSize = if (iconAppearancePage) 20f else if (page == "root") 23f else 21f
            if (iconAppearancePage) {
                typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                translationY = dp(4).toFloat()
            } else {
                setTypeface(typeface, Typeface.BOLD)
            }
            setTextColor(primaryText)
            if (page == "appearance" || largePageHeader) visibility = View.INVISIBLE
        }
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        if (page == "appearance") {
            val bgColor = if (lightSurface) 0xFFF4F5F8.toInt() else 0xFF292B31.toInt()
            toolbar.addView(toolbarGlyphButton("reset", primaryText, "Reset appearance settings", 24, roundedDrawable(bgColor, dp(22))) {
                confirmResetAppearance()
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        } else if (iconAppearancePage && iconAppearanceScope != "drawer") {
            toolbar.addView(toolbarGlyphButton("icon_random_shape", Color.WHITE, "Choose a random icon shape", 25, selectableBackground()) {
                chooseRandomIconShape()
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        if (page == "appearance") {
            root.addView(TextView(this).apply {
                text = getString(R.string.settings_global_appearance)
                textSize = 26f
                includeFontPadding = false
                typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 700)
                setTextColor(primaryText)
                textScaleX = 1.13f
                translationY = -dp(2).toFloat()
            }, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(24), dp(5), dp(24), 0)
            })
        }

        scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            if (iconAppearancePage) {
                val backdrop = IconAppearanceBackdropDrawable(dp(167).toFloat(), dp(8).toFloat())
                background = backdrop
                alignScreenYOnLayout(this) { backdrop.alignToScreenY(it) }
            }
        }
        val initialContentTop = when {
            iconAppearancePage -> 0
            rootUsesLightSurface -> dp(7)
            page == "root" -> dp(12)
            lightSurface -> dp(16)
            else -> dp(8)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentInset, initialContentTop, contentInset, dp(28))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            if (page == "appearance") topMargin = dp(14)
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val listenerToolbarHorizontal = when {
                iconAppearancePage || lightSurface -> dp(if (rootUsesLightSurface) 8 else 12)
                else -> dp(22)
            }
            val listenerContentTop = when {
                iconAppearancePage -> 0
                rootUsesLightSurface -> dp(7)
                page == "root" -> dp(12)
                lightAppearance -> dp(16)
                else -> dp(8)
            }
            val toolbarTop = (if (iconAppearancePage) 0 else if (lightSurface || rootUsesLightSurface) dp(4) else dp(7)) +
                bars.top + if (page == "root" && !rootUsesLightSurface) dp(30) else 0
            toolbar.setPadding(
                if (iconAppearancePage) dp(12) else listenerToolbarHorizontal,
                toolbarTop,
                if (iconAppearancePage) dp(8) else listenerToolbarHorizontal,
                if (iconAppearancePage) dp(8) else dp(7),
            )
            content.setPadding(contentInset, listenerContentTop, contentInset, dp(28))
            scroll.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        renderContent()
        // Content is built programmatically after setContentView; apply the active family
        // after each page render so every settings label follows the selected font.
        LauncherTypography.applyToViewTree(this, root)
    }

    private fun toolbarGlyphButton(
        glyph: String,
        tint: Int,
        description: String,
        glyphSizeDp: Int,
        bg: Drawable,
        onClick: () -> Unit,
    ) = FrameLayout(this).apply {
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = bg
        addView(SettingsGlyphView(this@LauncherSettingsActivity, glyph, tint), FrameLayout.LayoutParams(dp(glyphSizeDp), dp(glyphSizeDp), Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    private fun alignScreenYOnLayout(view: View, applyY: (Float) -> Unit) {
        val loc = IntArray(2)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.getLocationOnScreen(loc)
            applyY(loc[1].toFloat())
        }
    }

    private fun renderContent() {
        val isolationEnabled = HavenModePreferences.isIsolationEnabled(this)
        when (page) {
            "root" -> renderRoot()
            "wallpaper" -> renderWallpaperSettings()
            "appearance" -> renderAppearance()
            "icon_appearance" -> renderIconAppearance()
            "folder_icons" -> renderFolderIcons()
            "gestures" -> renderGestures()
            "backup" -> renderBackup()
            "home" -> renderHomeScreen()
            "home_grid" -> renderHomeGridSettings()
            "home_dock" -> renderHomeDockSettings()
            "app_page" -> renderAppPage()
            "vault" -> if (isolationEnabled) renderVaultPage() else renderRoot()
            "isolation" -> if (isolationEnabled) renderIsolation() else renderRoot()
            "search" -> renderSearchPage()
            "pages" -> renderPages()
            "hot_keys" -> renderHotKeys()
            "gesture_single" -> renderGestureMap("Single finger", SINGLE_FINGER_GESTURES)
            "gesture_double" -> renderGestureMap("Double finger", DOUBLE_FINGER_GESTURES)
            "smart_display" -> renderSmartDisplayOffInfo()
            "privacy" -> renderPrivacy()
            else -> renderRoot()
        }
    }

    private fun renderRoot() {
        val isolationEnabled = HavenModePreferences.isIsolationEnabled(this)
        notificationRow()
        menuRow("appearance", "${getString(R.string.settings_experience_mode_title)} · ${experienceModeLabel()}", MINT) {
            startActivity(Intent(this, WelcomeModeActivity::class.java).putExtra(WelcomeModeActivity.EXTRA_RECONFIGURE, true))
        }
        if (isolationEnabled) menuRow("security", "Work Profile & Isolation", MINT) { navigate("isolation") }
        menuRow("wallpaper", "Wallpaper", CYAN) { navigate("wallpaper") }
        menuRow("appearance", "Global appearance", PEACH) { navigate("appearance") }
        menuRow("gestures", "Gestures and hot keys", PINK) { navigate("gestures") }
        menuRow("backup", "Backup", CYAN) { navigate("backup") }
        menuRow("share", "Share your home screen", LAVENDER) { shareWorkspace() }

        separator(top = if (usesLightSettingsSurface()) 16 else 12, bottom = 16)
        sectionLabel("PAGES")
        val pageRows = buildMap<String, () -> Unit> {
            put("home") { menuRow("home", "Home screen", BLUE, trailing = "handle", reorderId = "home") { navigate("home") } }
            put("app_page") { menuRow("apps", "App page", BLUE, trailing = "handle", reorderId = "app_page") { navigate("app_page") } }
            if (isolationEnabled) put("vault") { menuRow("security", "Haven Vault", BLUE, trailing = "handle", reorderId = "vault") { navigate("vault") } }
            put("search") { menuRow("search", "Haven Search", BLUE, trailing = "handle", reorderId = "search") { navigate("search") } }
        }
        val storedOrder = preferences.getString(LauncherSettings.KEY_SETTINGS_PAGE_ORDER, "home,app_page,vault,search")
            .orEmpty().split(',')
        (storedOrder.filter { it in pageRows }.distinct() + pageRows.keys.filterNot { it in storedOrder })
            .forEach { pageRows[it]?.invoke() }
        menuRow("pages", "Page manager", MUTED) { navigate("pages") }

        separator(top = 12, bottom = 16)
        sectionLabel("OTHER")
        menuRow("security", "Security and privacy", MUTED) { navigate("privacy") }
        menuRow("apps", "About Haven", MUTED) { showAbout() }
    }

    private fun experienceModeLabel(): String = getString(
        when (HavenModePreferences.getMode(this)) {
            HavenExperienceMode.BOTH -> R.string.welcome_mode_both_title
            HavenExperienceMode.LAUNCHER_ONLY -> R.string.welcome_mode_launcher_title
            HavenExperienceMode.ISOLATION_ONLY -> R.string.welcome_mode_isolation_title
        },
    )

    private fun renderWallpaperSettings() {
        largeSettingsTitle("Wallpaper")
        pageIntro("Choose a wallpaper for Home and control how it moves between pages.")
        sectionLabel("WALLPAPER MOVEMENT")
        switchRow("3D perspective", "Move the wallpaper gently as you tilt the device.", LauncherSettings.KEY_WALLPAPER_PARALLAX, false)
        switchRow("Scrollable wallpaper", "Pan the wallpaper as you move between Home pages.", LauncherSettings.KEY_WALLPAPER_SCROLLABLE, true)
        separator(top = 10, bottom = 10)
        sectionLabel("WALLPAPER SOURCE")
        settingRow("Choose wallpaper", "Open Android's wallpaper picker.", "Open") {
            runSystemIntent(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper settings are unavailable")
        }
        settingRow("Choose a photo", "Select and crop a picture for your wallpaper.", "Choose") {
            wallpaperPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        infoCard("Photo selection uses Android's system Photo Picker. Teres receives access only to the image you choose.")
    }

    private fun notificationRow() {
        val lightRoot = usesLightSettingsSurface()
        val highlightColor = if (lightRoot) 0xFF0866FF.toInt() else Color.WHITE
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (lightRoot) 14 else 16), dp(if (lightRoot) 6 else 9), dp(14), dp(if (lightRoot) 6 else 9))
            minimumHeight = dp(if (lightRoot) 48 else 54)
            background = roundedDrawable(if (lightRoot) 0xFFE5EFFF.toInt() else 0xFF202020.toInt(), dp(if (lightRoot) 14 else 10))
            isFocusable = true
            contentDescription = "Highlight apps that have active notifications"
            setOnClickListener { openNotificationAccess() }
        }
        card.addView(SettingsGlyphView(this, "notifications", highlightColor), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(18) })
        card.addView(TextView(this).apply {
            text = getString(R.string.quick_settings_highlight_notifications)
            textSize = if (lightRoot) 14f else 15f
            typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
            setTextColor(highlightColor)
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(SettingsGlyphView(this, "next", highlightColor), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(8) })
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(if (lightRoot) 16 else 8) })
    }

    private fun usesLightSettingsSurface(): Boolean =
        page != "icon_appearance" && !LauncherAppearance.resolve(this).isDark

    private fun renderAppearance() {
        val lightSurface = usesLightSettingsSurface()
        val primaryText = if (lightSurface) INK_PRIMARY else 0xFFF4F5F7.toInt()
        val cardSurface = if (lightSurface) Color.WHITE else 0xFF202127.toInt()
        content.addView(buildAppearancePreview(), LinearLayout.LayoutParams(-1, -2))

        val editTheme = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFFF5471.toInt(), 0xFFFFA52E.toInt(), 0xFFED269A.toInt(), 0xFF5634E8.toInt()),
            ).apply { cornerRadius = dp(36).toFloat() }
            isFocusable = true
            contentDescription = "Edit theme"
            setOnClickListener { showEditThemeDialog() }
            addView(View(this@LauncherSettingsActivity).apply {
                background = roundedDrawable(cardSurface, dp(34))
                isClickable = false
            }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = getString(R.string.settings_edit_theme)
                textSize = 18f
                typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                gravity = Gravity.CENTER
                setTextColor(primaryText)
                isClickable = false
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        content.addView(editTheme, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(24)
            bottomMargin = dp(16)
        })

        appearanceRow("icon_appearance", "Icon appearance", null) { navigate("icon_appearance") }
        appearanceRow("widgets", "Widget", null) { navigate("home") }
        appearanceRow("font", "Font", LauncherTypography.labelFor(LauncherTypography.selectedFamily(this))) { showFontOptions() }
        appearanceRow("folder", "Folder", null) { showFolderOptions() }
        appearanceRow(
            "animation",
            "Home animation",
            if (preferences.getBoolean(LauncherSettings.KEY_DRAWER_BLUR, true)) "Blur effect" else "No blur effect",
        ) { showHomeAnimationOptions() }
    }

    private fun buildAppearancePreview(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.BLACK, dp(26))
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "Launcher appearance preview. Tap to edit the theme."
            setOnClickListener { showEditThemeDialog() }
        }
        val upper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF394876.toInt(), 0xFF293252.toInt()))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextClock(this@LauncherSettingsActivity).apply {
                format12Hour = "h:mm"
                format24Hour = "HH:mm"
                textSize = 22f
                typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 700)
                setTextColor(Color.WHITE)
                contentDescription = "Preview time"
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            addView(View(this@LauncherSettingsActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(FrameLayout(this@LauncherSettingsActivity).apply {
                    addView(ImageView(this@LauncherSettingsActivity).apply {
                        setImageResource(R.drawable.ic_weather_sun)
                        setColorFilter(0xFFFFD33D.toInt())
                        contentDescription = null
                    }, FrameLayout.LayoutParams(dp(28), dp(28)))
                    addView(View(this@LauncherSettingsActivity).apply {
                        background = roundedDrawable(Color.WHITE, dp(5))
                    }, FrameLayout.LayoutParams(dp(15), dp(7), Gravity.BOTTOM or Gravity.END).apply {
                        setMargins(0, 0, dp(1), dp(2))
                    })
                }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(6) })
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = getString(R.string.settings_weather_preview)
                    textSize = 20f
                    typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 700)
                    setTextColor(Color.WHITE)
                })
            })
        }
        upper.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        val previewApps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val battery = FrameLayout(this).apply {
            background = roundedDrawable(0xFF202842.toInt(), dp(28))
            clipToOutline = true
            contentDescription = "Preview battery tile, 50 percent"
            val radius = dp(28).toFloat()
            addView(View(this@LauncherSettingsActivity).apply {
                background = GradientDrawable().apply {
                    setColor(0xFF424C68.toInt())
                    cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                }
            }, FrameLayout.LayoutParams(dp(78), -1, Gravity.START))
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = getString(R.string.widget_picker_sample_battery_percent)
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(dp(78), -1, Gravity.START))
        }
        upper.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(previewApps, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(battery, LinearLayout.LayoutParams(dp(158), dp(52)))
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })

        val pageDock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedDrawable(0x443F4A67, dp(18))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            listOf("messages", "share", "game", "media", "shapes", "settings").forEach { glyph ->
                addView(SettingsGlyphView(this@LauncherSettingsActivity, glyph, Color.WHITE),
                    LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
        }
        upper.addView(pageDock, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        card.addView(upper, LinearLayout.LayoutParams(-1, dp(228)))

        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(13), dp(20), dp(11))
            isFocusable = true
            background = selectableBackground()
            setOnClickListener { chooseLauncherTheme() }
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = getString(R.string.settings_launcher_theme_label)
                textSize = 12f
                setTextColor(0xFFB9B9B9.toInt())
            })
            addView(LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = appearanceThemeLabel()
                    textSize = 18f
                    includeFontPadding = false
                    typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(SettingsGlyphView(this@LauncherSettingsActivity, "down", Color.WHITE), LinearLayout.LayoutParams(dp(22), dp(22)))
            }, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, dp(66)))

        // Android exposes wallpaper colors to ordinary apps, but not the wallpaper image itself.
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(22), 0)
            background = selectableBackground()
            isFocusable = true
            contentDescription = "Wallpaper not accessible. Tap to fix."
            setOnClickListener { navigate("wallpaper") }
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = contentDescription
                textSize = 13f
                setTextColor(0xFFF1F1F1.toInt())
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(SettingsGlyphView(this@LauncherSettingsActivity, "warning", 0xFFFF8C86.toInt()), LinearLayout.LayoutParams(dp(24), dp(24)))
        }, LinearLayout.LayoutParams(-1, dp(44)))

        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed || !previewApps.isAttachedToWindow) return@loadAsync
            previewApps.removeAllViews()
            listOf("com.android.settings", "com.android.chrome")
                .mapNotNull { pkg -> loaded.firstOrNull { it.entry.packageName == pkg } }
                .ifEmpty { loaded.take(2) }
                .forEach { app ->
                    previewApps.addView(ImageView(this).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(CircularAppIconDrawable(app.icon ?: packageManager.defaultActivityIcon, currentAccent()))
                        contentDescription = app.entry.label
                    }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(26) })
                }
        }
        return card
    }

    private fun renderIconAppearance() {
        content.setPadding(dp(24), 0, dp(24), dp(26))
        content.addView(buildIconShapeHero(), LinearLayout.LayoutParams(-1, dp(167)).apply {
            leftMargin = -dp(24)
            rightMargin = -dp(24)
        })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedDrawable(0xFFF5F7FA.toInt(), dp(12))
            contentDescription = "Choose where icon appearance applies"
        }
        listOf("home" to "Home screen", "drawer" to "App page").forEach { (scope, title) ->
            val selected = iconAppearanceScope == scope
            tabs.addView(FrameLayout(this).apply {
                isFocusable = true
                contentDescription = if (selected) "$title, selected" else title
                background = if (selected) roundedDrawable(0xFF0066FF.toInt(), dp(9)) else selectableBackground()
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = title
                    textSize = 16f
                    typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                    gravity = Gravity.CENTER
                    setTextColor(if (selected) Color.WHITE else 0xFF202124.toInt())
                    isClickable = false
                }, FrameLayout.LayoutParams(-1, -1))
                setOnClickListener {
                    iconAppearanceScope = scope
                    preferences.edit {
                        putString(LauncherSettings.KEY_ICON_APPEARANCE_SCOPE, scope)
                        if (scope == "drawer") putString(LauncherSettings.KEY_ICON_SHAPE_DRAWER, "circle")
                    }
                    renderPage()
                }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        content.addView(tabs, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(24)
            bottomMargin = dp(31)
        })

        buildIconPackRow()
        separator(top = 12, bottom = 10, color = 0xFFE8EBEF.toInt())

        if (iconAppearanceScope == "drawer") {
            shapeRows.clear()
            infoCard("App page icons always use circles. Existing circular artwork keeps its shape; other icons are clipped to a circle, and irregular transparent artwork gets a sampled-color circle behind it.")
        } else {
            val adaptiveSwitch = IconAppearanceSwitch(this, preferences.getBoolean(adaptivePreferenceKey(), true)).apply {
                contentDescription = "Apply adaptive icon shape"
                setOnCheckedChangeListener { checked ->
                    preferences.edit { putBoolean(adaptivePreferenceKey(), checked) }
                    setShapeRowsEnabled(checked)
                    refreshIconShapePreview()
                }
            }
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(dp(16), dp(4), dp(10), dp(4))
                addView(LinearLayout(this@LauncherSettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@LauncherSettingsActivity).apply {
                        text = getString(R.string.settings_adaptive_icons_title)
                        textSize = 18f
                        typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                        setTextColor(0xFF202124.toInt())
                    })
                    addView(TextView(this@LauncherSettingsActivity).apply {
                        text = getString(R.string.settings_adaptive_icons_subtitle)
                        textSize = 14f
                        setTextColor(0xFF818387.toInt())
                        setPadding(0, dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(adaptiveSwitch, LinearLayout.LayoutParams(dp(52), dp(48)))
            }, LinearLayout.LayoutParams(-1, dp(70)).apply {
                leftMargin = -dp(16)
                rightMargin = -dp(16)
            })

            content.addView(TextView(this).apply {
                text = getString(R.string.settings_shape_heading)
                textSize = 19f
                typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                setTextColor(0xFF202124.toInt())
            }, LinearLayout.LayoutParams(-1, dp(21)).apply {
                topMargin = dp(17)
                bottomMargin = dp(17)
            })
            shapeRows.clear()
            val shapeTiles = mutableListOf<Pair<IconShapePreviewView, String>>()
            val selectedShape = preferences.getString(shapePreferenceKey(), "circle") ?: "circle"
            IconShapes.options.chunked(6).forEachIndexed { rowIndex, rowOptions ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(24), 0, 0, 0)
                }
                rowOptions.forEachIndexed { column, option ->
                    val tile = IconShapePreviewView(this, option.key, option.key == selectedShape).apply {
                        contentDescription = "${option.label} icon shape${if (option.key == selectedShape) ", selected" else ""}"
                        isFocusable = true
                        isClickable = true
                        foreground = selectableBackground()
                        setOnClickListener {
                            if (!preferences.getBoolean(adaptivePreferenceKey(), true)) return@setOnClickListener
                            preferences.edit { putString(shapePreferenceKey(), option.key) }
                            shapeTiles.forEach { (view, label) ->
                                val isCurrent = view === this
                                view.setShapeSelected(isCurrent)
                                view.contentDescription = "$label icon shape${if (isCurrent) ", selected" else ""}"
                            }
                            refreshIconShapePreview()
                        }
                    }
                    shapeTiles += tile to option.label
                    row.addView(tile, LinearLayout.LayoutParams(dp(59), dp(59)).apply {
                        if (column > 0) marginStart = dp(if (column == 5) 17 else 8)
                    })
                }
                content.addView(row, LinearLayout.LayoutParams(-1, dp(59)).apply {
                    leftMargin = -dp(24)
                    rightMargin = -dp(24)
                    if (rowIndex > 0) topMargin = dp(8)
                })
                shapeRows += row
            }
            setShapeRowsEnabled(preferences.getBoolean(adaptivePreferenceKey(), true))
        }

        separator(top = 10, bottom = 25, color = 0xFFE8EBEF.toInt())
        buildIconSwitchRow("Double tap icons", LauncherSettings.KEY_ICON_DOUBLE_TAP_BADGES, false, "Show a badge for icons with a secondary tap action")
        separator(top = 8, bottom = 4, color = 0xFFE8EBEF.toInt())
        val folderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(16), 0, dp(4), 0)
            isFocusable = true
            contentDescription = "Folder icons"
            background = selectableBackground()
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = getString(R.string.settings_folder_icons)
                textSize = 18f
                setTextColor(0xFF202124.toInt())
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(FrameLayout(this@LauncherSettingsActivity).apply {
                addView(SettingsGlyphView(this@LauncherSettingsActivity, "next", 0xFFA0A2A5.toInt()),
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            setOnClickListener { navigate("folder_icons") }
        }
        content.addView(folderRow, LinearLayout.LayoutParams(-1, dp(58)).apply {
            leftMargin = -dp(16)
            rightMargin = -dp(16)
        })
    }

    private fun setShapeRowsEnabled(enabled: Boolean) {
        shapeRows.forEach { row ->
            row.alpha = if (enabled) 1f else 0.45f
            row.children.forEach { child ->
                child.isEnabled = enabled
                child.isClickable = enabled
            }
        }
    }

    private fun buildIconShapeHero(): View {
        iconPreviewArtwork.clear()
        val checkerboard = checkerboardBackground()
        val background = FrameLayout(this).apply {
            this.background = checkerboard
            contentDescription = "Icon appearance preview"
            alignScreenYOnLayout(this) { checkerboard.alignToScreenY(it) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val previews = List(3) {
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Sample app icon"
            }
        }
        previews.forEachIndexed { index, image ->
            row.addView(image, LinearLayout.LayoutParams(dp(62), dp(62)).apply { if (index > 0) marginStart = dp(29) })
        }
        val miniIcons = mutableListOf<ImageView>()
        val folderPreview = FrameLayout(this).apply {
            this.background = roundedDrawable(0x55FFFFFF, dp(14))
            contentDescription = "Folder icon sample"
            clipToOutline = true
            val miniGrid = LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            repeat(2) { r ->
                val miniRow = LinearLayout(this@LauncherSettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                repeat(2) { c ->
                    val cell = ImageView(this@LauncherSettingsActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    miniIcons += cell
                    miniRow.addView(cell, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                        if (c > 0) marginStart = dp(5)
                        if (r > 0) topMargin = dp(5)
                    })
                }
                miniGrid.addView(miniRow)
            }
            addView(miniGrid, FrameLayout.LayoutParams(-1, -1))
        }
        row.addView(folderPreview, LinearLayout.LayoutParams(dp(62), dp(62)).apply { marginStart = dp(30) })
        background.addView(row, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(41) })
        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed || !background.isAttachedToWindow) return@loadAsync
            val samples = listOf("com.google.android.apps.docs", "com.google.android.apps.youtube.music", "com.google.android.dialer", "com.google.android.googlequicksearchbox", "com.android.settings", "com.google.android.chrome")
                .mapNotNull { pkg -> loaded.firstOrNull { it.entry.packageName == pkg } }
                .plus(loaded)
                .distinctBy { it.entry.componentKey }
                .take(7)
            fun bindSample(image: ImageView, index: Int, defaultDesc: String?) {
                val app = samples.getOrNull(index)
                val artwork = app?.icon ?: packageManager.defaultActivityIcon
                iconPreviewArtwork[image] = artwork
                image.setImageDrawable(iconPreviewDrawable(artwork))
                image.contentDescription = app?.entry?.label ?: defaultDesc
            }
            previews.forEachIndexed { index, image -> bindSample(image, index, "App icon preview") }
            miniIcons.forEachIndexed { index, cell -> bindSample(cell, index, null) }
        }
        return background
    }

    private fun iconPreviewDrawable(artwork: Drawable): Drawable {
        val shape = preferences.getString(shapePreferenceKey(), "circle") ?: "circle"
        val effectiveShape = if (iconAppearanceScope != "drawer" && preferences.getBoolean(adaptivePreferenceKey(), true)) shape else "circle"
        return CircularAppIconDrawable(artwork, currentAccent(), shape = effectiveShape)
    }

    private fun refreshIconShapePreview() {
        iconPreviewArtwork.forEach { (image, artwork) ->
            if (image.isAttachedToWindow) image.setImageDrawable(iconPreviewDrawable(artwork))
        }
    }

    private fun buildIconPackRow() {
        val packageName = preferences.getString(iconPackPreferenceKey(), "default").orEmpty()
        val packageLabel = if (packageName == "default") "Default" else runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault("Default")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(16), dp(4), dp(10), dp(4))
            isFocusable = true
            contentDescription = "Icon pack. $packageLabel"
            background = selectableBackground()
            addView(SettingsGlyphView(this@LauncherSettingsActivity, "android_legacy", 0xFF92C84E.toInt()),
                LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })
            addView(LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = getString(R.string.settings_icon_pack_title)
                    textSize = 16f
                    typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 500)
                    setTextColor(0xFF242528.toInt())
                })
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = packageLabel
                    textSize = 14f
                    setTextColor(0xFF888A8D.toInt())
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { showIconPackPicker() }
        }
        content.addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply {
            leftMargin = -dp(16)
            rightMargin = -dp(16)
        })
    }

    private fun showIconPackPicker() {
        val entries = listOf("default" to "Default") + IconPackRepository(this).availablePacks()
        val selectedPackage = preferences.getString(iconPackPreferenceKey(), "default") ?: "default"
        showSingleChoiceDialog(
            title = "Icon pack",
            options = entries.map { it.second to it.first },
            selected = selectedPackage,
        ) { chosen ->
            preferences.edit { putString(iconPackPreferenceKey(), chosen) }
            renderPage()
            if (chosen != "default") toast("Icon pack saved. App filters are used when the pack provides them.")
        }
    }

    private fun buildIconSwitchRow(title: String, key: String, defaultValue: Boolean, description: String) {
        val toggle = IconAppearanceSwitch(this, preferences.getBoolean(key, defaultValue)).apply {
            contentDescription = description
            setOnCheckedChangeListener { checked -> preferences.edit { putBoolean(key, checked) } }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(16), 0, dp(8), 0)
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = title
                textSize = 18f
                setTextColor(0xFF202124.toInt())
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(toggle, LinearLayout.LayoutParams(dp(48), dp(48)))
            setOnClickListener { toggle.isChecked = !toggle.isChecked }
        }
        content.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply {
            leftMargin = -dp(16)
            rightMargin = -dp(14)
        })
    }

    private fun adaptivePreferenceKey() = if (iconAppearanceScope == "drawer") LauncherSettings.KEY_ICON_ADAPTIVE_DRAWER else LauncherSettings.KEY_ICON_ADAPTIVE_HOME
    private fun shapePreferenceKey() = if (iconAppearanceScope == "drawer") LauncherSettings.KEY_ICON_SHAPE_DRAWER else LauncherSettings.KEY_ICON_SHAPE_HOME
    private fun iconPackPreferenceKey() = if (iconAppearanceScope == "drawer") LauncherSettings.KEY_ICON_PACK_DRAWER else LauncherSettings.KEY_ICON_PACK_HOME

    private fun chooseRandomIconShape() {
        val current = preferences.getString(shapePreferenceKey(), "circle")
        val option = IconShapes.options.filterNot { it.key == current }.randomOrNull() ?: IconShapes.options.first()
        preferences.edit { putString(shapePreferenceKey(), option.key) }
        toast("Icon shape: ${option.label}")
        renderPage()
    }

    private fun renderFolderIcons() {
        pageIntro("Choose how apps appear inside folders and folder previews.")
        settingRow("Preview style", "Choose how app icons are arranged in a folder tile.", folderPreviewStyleLabel()) { chooseFolderPreviewStyle() }
        settingRow(
            "Folder icon size",
            "Adjust the app icons shown in the folder preview.",
            "${preferences.getInt(LauncherSettings.KEY_FOLDER_PREVIEW_ICON_SIZE, LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_DEFAULT_DP)} dp",
        ) { showFolderOptions() }
        switchRow("Folder background", "Draw a soft surface behind folder previews.", LauncherSettings.KEY_FOLDER_BACKGROUND, true)
        switchRow("Folder labels", "Show names below folders on the Home screen.", LauncherSettings.KEY_FOLDER_SHOW_LABELS, true)
    }

    private fun folderPreviewStyleLabel() = FOLDER_STYLES.firstOrNull {
        it.second == preferences.getString(LauncherSettings.KEY_FOLDER_PREVIEW_STYLE, "contained")
    }?.first ?: "Contained"

    private fun chooseFolderPreviewStyle() {
        chooseStringPref("Preview style", LauncherSettings.KEY_FOLDER_PREVIEW_STYLE, "contained", FOLDER_STYLES) {
            toast("Folder preview style updated")
        }
    }

    private fun checkerboardBackground() = CheckerboardDrawable(dp(8).toFloat())

    private fun appearanceRow(glyph: String, label: String, subtitle: String?, action: () -> Unit) {
        val lightSurface = usesLightSettingsSurface()
        val primaryText = if (lightSurface) INK_PRIMARY else 0xFFF4F5F7.toInt()
        val secondaryText = if (lightSurface) 0xFF777777.toInt() else 0xFFB3B5BC.toInt()
        val trailingText = if (lightSurface) 0xFF9A9A9A.toInt() else 0xFFBDBDBD.toInt()
        val rowHeight = if (subtitle == null) 48 else 66
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(rowHeight)
            setPadding(dp(16), 0, dp(8), 0)
            isFocusable = true
            contentDescription = if (subtitle == null) label else "$label. $subtitle"
            background = selectableBackground()
            setOnClickListener { action() }
        }
        row.addView(SettingsGlyphView(this, glyph, primaryText), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
        row.addView(labelsColumn(label, subtitle, 16f, primaryText, 13f, secondaryText, 3, 0, null), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(SettingsGlyphView(this, "next", trailingText), LinearLayout.LayoutParams(dp(20), dp(20)))
        content.addView(row, LinearLayout.LayoutParams(-1, dp(rowHeight)).apply {
            leftMargin = -dp(16)
            rightMargin = -dp(16)
            bottomMargin = dp(8)
        })
    }

    private fun appearanceThemeLabel() = THEME_OPTIONS.firstOrNull {
        it.second == preferences.getString(LauncherSettings.KEY_THEME_MODE, LauncherAppearance.MODE_SYSTEM)
    }?.first ?: "Ambient theme"

    private fun showEditThemeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Edit theme")
            .setItems(arrayOf("Launcher theme", "Accent color", "Wallpaper", "Reset appearance")) { _, which ->
                when (which) {
                    0 -> chooseLauncherTheme()
                    1 -> chooseAccent()
                    2 -> navigate("wallpaper")
                    else -> confirmResetAppearance()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseLauncherTheme() {
        chooseStringPref("Launcher theme", LauncherSettings.KEY_THEME_MODE, LauncherAppearance.MODE_SYSTEM, THEME_OPTIONS)
    }

    private fun confirmResetAppearance() {
        AlertDialog.Builder(this)
            .setTitle("Reset appearance")
            .setMessage("Restore the ambient theme, wallpaper accent, and default app-page effects?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                preferences.edit {
                    putString(LauncherSettings.KEY_THEME_MODE, LauncherAppearance.MODE_SYSTEM)
                    putString(LauncherSettings.KEY_THEME_ACCENT, LauncherAppearance.ACCENT_WALLPAPER)
                    putBoolean(LauncherSettings.KEY_DRAWER_BLUR, true)
                    putBoolean(LauncherSettings.KEY_DIM_WALLPAPER, false)
                }
                renderPage()
            }
            .show()
    }

    private fun showFontOptions() {
        val values = LauncherTypography.selectableFamilies
        AlertDialog.Builder(this)
            .setTitle("Font for launcher text")
            .setAdapter(FontOptionAdapter(values, LauncherTypography.selectedFamily(this))) { dialog, which ->
                preferences.edit { putString(LauncherSettings.KEY_FONT_FAMILY, values[which]) }
                dialog.dismiss()
                LauncherTypography.applyTheme(this)
                renderPage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private inner class FontOptionAdapter(
        private val families: List<String>,
        private val selectedFamily: String,
    ) : BaseAdapter() {
        override fun getCount(): Int = families.size
        override fun getItem(position: Int): String = families[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val family = getItem(position)
            return LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(dp(20), dp(7), dp(20), dp(7))
                contentDescription = "${LauncherTypography.labelFor(family)} font preview${if (family == selectedFamily) ", selected" else ""}"
                addView(LinearLayout(this@LauncherSettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(this@LauncherSettingsActivity).apply {
                        text = LauncherTypography.labelFor(family)
                        textSize = 15f
                        typeface = LauncherTypography.typefaceForFamily(this@LauncherSettingsActivity, family, 500)
                        setTextColor(INK_PRIMARY)
                        includeFontPadding = false
                    })
                    addView(TextView(this@LauncherSettingsActivity).apply {
                        text = getString(R.string.settings_font_preview_sample)
                        textSize = 12f
                        typeface = LauncherTypography.typefaceForFamily(this@LauncherSettingsActivity, family, 400)
                        setTextColor(0xFF646873.toInt())
                        includeFontPadding = false
                    }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(RadioButton(this@LauncherSettingsActivity).apply {
                    isChecked = family == selectedFamily
                    isClickable = false
                    isFocusable = false
                    contentDescription = if (isChecked) "Selected" else "Not selected"
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
            }
        }
    }

    private fun showFolderOptions() {
        chooseIntPref(
            title = "Folder appearance",
            key = LauncherSettings.KEY_FOLDER_PREVIEW_ICON_SIZE,
            defaultValue = LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_DEFAULT_DP,
            options = FOLDER_ICON_SIZE_OPTIONS,
            negativeLabel = "Close",
            refresh = false,
        )
    }

    private fun showHomeAnimationOptions() {
        val current = preferences.getString(LauncherSettings.KEY_HOME_ANIMATION, "slide") ?: "slide"
        val blurEnabled = preferences.getBoolean(LauncherSettings.KEY_DRAWER_BLUR, true)
        val options = listOf(
            "Slide" to "slide",
            "Fade" to "fade",
            "Zoom" to "zoom",
            "None" to "none",
            "Blur effect: ${if (blurEnabled) "On" else "Off"}" to "blur",
        )
        showSingleChoiceDialog("Home animation", options, current, negativeLabel = "Close") { choice ->
            if (choice == "blur") preferences.edit { putBoolean(LauncherSettings.KEY_DRAWER_BLUR, !blurEnabled) }
            else preferences.edit { putString(LauncherSettings.KEY_HOME_ANIMATION, choice) }
            renderPage()
        }
    }

    private fun renderHomeScreen() {
        val isDefaultHome = roleManager.isDefaultHome()
        val pageCount = preferences.workspacePageCount(workspace.load())
        val gridColumns = homeGridColumns()
        pageIntro("Arrange the home workspace and wallpaper.")
        settingRow("Icon appearance", "Choose icon packs and masks for Home icons.", "Configure") { navigate("icon_appearance") }
        settingRow("Grid properties", "Set Home columns, spacing, icon size, and labels.", "${homeIconSize()} dp") { navigate("home_grid") }
        settingRow("Dock", "Show or hide the bottom row of app shortcuts.", if (preferences.getBoolean(LauncherSettings.KEY_DOCK_VISIBLE, true)) "Enabled" else "Disabled") { navigate("home_dock") }
        switchRow("Hide status bar", "Hide the status bar on Home. Swipe down from the top edge to reveal it temporarily.", LauncherSettings.KEY_HOME_HIDE_STATUS_BAR, false)
        switchRow("Show indicator", "Show which Home page is active when you have multiple pages.", LauncherSettings.KEY_HOME_PAGE_INDICATOR, true)
        switchRow("Add new apps", "Automatically add newly installed apps to the Home grid.", LauncherSettings.KEY_AUTO_ADD_NEW_APPS, false)
        collapsibleSectionLabel("Advanced settings", homeAdvancedSettingsExpanded) {
            homeAdvancedSettingsExpanded = !homeAdvancedSettingsExpanded
            renderPage()
        }
        if (homeAdvancedSettingsExpanded) {
            switchRow("Reverse page order", "Show Home pages in reverse left-to-right order.", LauncherSettings.KEY_REVERSE_HOME_PAGE_ORDER, false)
            switchRow("Improve readability", "Add a semi-opaque overlay to improve readability on light wallpapers.", LauncherSettings.KEY_DIM_WALLPAPER, false)
            switchRow("Make extra pages darker", "Use a darker background on Home pages after the first one.", LauncherSettings.KEY_DARKEN_EXTRA_HOME_PAGES, false)
            switchRow("Bottom buttons", "Show the Your Feed side-panel button on Home.", LauncherSettings.KEY_BOTTOM_BUTTONS, false)
        }
        settingRow(
            "Default home app",
            if (isDefaultHome) "Teres Launcher is your default Home app" else "Select Teres Launcher as your default Home app",
            if (isDefaultHome) "Change" else "Choose",
        ) { requestHomeRole() }
        infoCard("Home icons use a $gridColumns × ${WorkspaceGridSpec.ROWS} grid. The icon area stays fixed while widgets scroll separately below it.")
        settingRow("Workspace pages", "Add pages for more apps and widgets.", "$pageCount page${if (pageCount == 1) "" else "s"}") { navigate("pages") }
        switchRow("Show clock widget", "Show the time, date, and optional weather block on Home.", LauncherSettings.KEY_CLOCK_VISIBLE, false)
        settingRow("Clock appearance", "Choose displayed details, text size, color, and position.", "Configure") { showClockAppearanceOptions() }
        switchRow("Show battery widget", "Show the battery percentage tile on Home.", LauncherSettings.KEY_BATTERY_VISIBLE, false)
        switchRow("Show weather on Home", "Use approximate location for a current forecast when you tap the weather area.", LauncherSettings.KEY_WEATHER_ENABLED, true)
        menuRow("wallpaper", "Change wallpaper", MINT) { navigate("wallpaper") }
    }

    private fun renderHomeGridSettings() {
        val spacing = homeColumnSpacing()
        val columns = homeGridColumns()
        val currentSize = homeIconSize()
        content.addView(buildHomeGridPreview(), LinearLayout.LayoutParams(-1, dp(298)).apply {
            setMargins(-dp(22), -dp(8), -dp(22), dp(14))
        })
        pageIntro("Set the Home grid, icon scale, and labels.")
        infoCard("Choose 4 to 8 columns and adjust the gap between them. Widgets use their own area.")
        settingRow("Columns", "Choose how many icons fit across Home.", "$columns columns") {
            chooseNumber("Home columns", (WorkspaceGridSpec.MIN_COLUMNS..WorkspaceGridSpec.MAX_COLUMNS).toList().toIntArray(), columns) {
                preferences.edit { putInt(LauncherSettings.KEY_GRID_COLUMNS, it) }
                renderPage()
            }
        }
        settingRow("Column spacing", "Set the horizontal gap between Home icons.", "$spacing dp") {
            chooseNumber("Home column spacing", intArrayOf(0, 4, 8, 12, 16, 20, 24, 32), spacing) {
                preferences.edit { putInt(LauncherSettings.KEY_HOME_COLUMN_SPACING, it) }
                renderPage()
            }
        }

        val sizeValue = TextView(this).apply {
            text = getString(R.string.settings_dp_value, currentSize)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(currentAccent())
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.settings_icon_size_cd, currentSize)
        }
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(10))
            addView(LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@LauncherSettingsActivity).apply {
                    setText(R.string.settings_icon_size_label)
                    textSize = 15f
                    setTextColor(Color.WHITE)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(sizeValue)
            }, LinearLayout.LayoutParams(-1, dp(34)))
            addView(TextView(this@LauncherSettingsActivity).apply {
                setText(R.string.settings_icon_size_summary)
                textSize = 12f
                setTextColor(0xFF999999.toInt())
                setPadding(0, dp(2), 0, dp(4))
            }, LinearLayout.LayoutParams(-1, -2))
            addView(SeekBar(this@LauncherSettingsActivity).apply {
                min = LauncherSettings.HOME_ICON_SIZE_MIN_DP
                max = LauncherSettings.HOME_ICON_SIZE_MAX_DP
                progress = currentSize
                contentDescription = "Home icon size slider"
                progressTintList = ColorStateList.valueOf(currentAccent())
                thumbTintList = ColorStateList.valueOf(currentAccent())
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val size = progress.coerceIn(LauncherSettings.HOME_ICON_SIZE_MIN_DP, LauncherSettings.HOME_ICON_SIZE_MAX_DP)
                        preferences.edit { putInt(LauncherSettings.KEY_HOME_ICON_SIZE, size) }
                        sizeValue.text = getString(R.string.settings_dp_value, size)
                        sizeValue.contentDescription = "Icon size $size dp"
                        homeGridPreviewIcons.forEach { image ->
                            image.updateLayoutParams<FrameLayout.LayoutParams> {
                                width = dp(size)
                                height = dp(size)
                            }
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        content.addView(sizeRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        switchRow("Icons label visibility", "Show app names below Home icons.", LauncherSettings.KEY_HOME_SHOW_LABELS, false)
    }

    private fun buildHomeGridPreview(): View {
        val checkerboard = checkerboardBackground()
        val previewColumns = homeGridColumns()
        val preview = FrameLayout(this).apply {
            background = checkerboard
            contentDescription = "Live preview of installed Home icons in a $previewColumns-column grid"
            alignScreenYOnLayout(this) { checkerboard.alignToScreenY(it) }
        }
        val grid = GridLayout(this).apply {
            columnCount = previewColumns
            rowCount = 3
            clipChildren = false
            clipToPadding = false
        }
        val spacing = homeColumnSpacing()
        val previewWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density - 48f
        val iconSize = dp(minOf(homeIconSize(), ((previewWidthDp - spacing) / previewColumns).toInt().coerceAtLeast(24)))
        val halfGap = dp(spacing) / 2
        val previewImages = List(previewColumns * 3) { index ->
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Home icon preview ${index + 1}"
                setImageDrawable(CircularAppIconDrawable(packageManager.defaultActivityIcon, currentAccent(), shape = homePreviewIconShape()))
            }
            val cell = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), 0x33777777)
                }
                addView(image, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            }
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(index / previewColumns),
                GridLayout.spec(index % previewColumns, 1f),
            ).apply {
                width = 0
                height = dp(96)
                leftMargin = halfGap
                rightMargin = halfGap
            })
            image
        }
        homeGridPreviewIcons = previewImages
        preview.addView(grid, FrameLayout.LayoutParams(-1, dp(288), Gravity.CENTER))
        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed || !preview.isAttachedToWindow) return@loadAsync
            val samples = loaded.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }.take(previewImages.size)
            previewImages.forEachIndexed { index, image ->
                val app = samples.getOrNull(index) ?: return@forEachIndexed
                image.setImageDrawable(CircularAppIconDrawable(app.icon ?: packageManager.defaultActivityIcon, currentAccent(), shape = homePreviewIconShape()))
                image.contentDescription = app.entry.label
            }
        }
        return preview
    }

    private fun homePreviewIconShape(): String = if (preferences.getBoolean(LauncherSettings.KEY_ICON_ADAPTIVE_HOME, true)) {
        preferences.getString(LauncherSettings.KEY_ICON_SHAPE_HOME, "circle") ?: "circle"
    } else "circle"

    private fun renderHomeDockSettings() {
        pageIntro("Choose whether the bottom app shortcut row is shown on Home.")
        switchRow("Enable", "Show the four-app dock below the workspace grid.", LauncherSettings.KEY_DOCK_VISIBLE, true)
        settingRow("Reset dock apps", "Restore the default dock shortcuts, including any removed icons.", "Reset") {
            preferences.edit(commit = true) { remove(LauncherSettings.KEY_DOCK_APP_PACKAGES) }
            toast("Default dock apps will be restored")
        }
    }

    private fun homeIconSize() = preferences.getInt(LauncherSettings.KEY_HOME_ICON_SIZE, LauncherSettings.HOME_ICON_SIZE_DEFAULT_DP)
        .coerceIn(LauncherSettings.HOME_ICON_SIZE_MIN_DP, LauncherSettings.HOME_ICON_SIZE_MAX_DP)

    private fun homeColumnSpacing() = preferences.getInt(LauncherSettings.KEY_HOME_COLUMN_SPACING, LauncherSettings.HOME_COLUMN_SPACING_DEFAULT_DP)
        .coerceIn(0, 32)

    private fun homeGridColumns() = WorkspaceGridSpec.columns(preferences.getInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.COLUMNS))

    private fun showClockAppearanceOptions() {
        AlertDialog.Builder(this)
            .setTitle("Clock appearance")
            .setItems(arrayOf("Time, date and weather", "Text size", "Text color", "Background", "Position")) { _, which ->
                when (which) {
                    0 -> chooseClockDetails()
                    1 -> chooseIntPref("Clock text size", LauncherSettings.KEY_CLOCK_TEXT_SIZE, 66, CLOCK_SIZE_OPTIONS, refresh = false)
                    2 -> chooseIntPref("Clock text color", LauncherSettings.KEY_CLOCK_TEXT_COLOR, Color.WHITE, CLOCK_COLOR_OPTIONS, refresh = false)
                    3 -> {
                        val enabled = !preferences.getBoolean(LauncherSettings.KEY_CLOCK_BACKGROUND, false)
                        preferences.edit { putBoolean(LauncherSettings.KEY_CLOCK_BACKGROUND, enabled) }
                        toast(if (enabled) "Clock background enabled" else "Clock background removed")
                    }
                    else -> chooseClockPosition()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseClockDetails() {
        val keys = arrayOf(LauncherSettings.KEY_CLOCK_SHOW_TIME, LauncherSettings.KEY_CLOCK_SHOW_DATE, LauncherSettings.KEY_CLOCK_SHOW_WEATHER)
        val labels = arrayOf("Show time", "Show date", "Show weather")
        val checked = BooleanArray(keys.size) { preferences.getBoolean(keys[it], true) }
        AlertDialog.Builder(this)
            .setTitle("Clock details")
            .setMultiChoiceItems(labels, checked) { _, index, selected -> checked[index] = selected }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                preferences.edit {
                    keys.indices.forEach { putBoolean(keys[it], checked[it]) }
                    putBoolean(LauncherSettings.KEY_CLOCK_VISIBLE, true)
                }
            }
            .show()
    }

    private fun renderAppPage() {
        pageIntro("Choose how apps appear in the wallpaper-backed app page.")
        settingRow("Launch view", "Choose what opens when you show the app page.", drawerLaunchView().label) { chooseDrawerLaunchView() }
        settingRow("Columns", "Set the number of icons across the app page.", "${preferences.drawerColumns()} columns") { chooseDrawerColumns() }
        settingRow("Icon size", "Adjust icon size without changing the home grid.", "${preferences.drawerIconSize()} dp") { chooseIconSize() }
        settingRow(
            "Row spacing",
            "Set the gap between app rows. Hidden app names use a tighter automatic gap until you choose a value.",
            preferences.getInt(LauncherSettings.KEY_DRAWER_ROW_SPACING, -1).takeIf { it >= 0 }?.let { "$it dp" }
                ?: "Auto · ${preferences.drawerRowSpacingDp()} dp",
        ) { chooseDrawerRowSpacing() }
        switchRow("Show app labels", "Place app names below their icons.", LauncherSettings.KEY_DRAWER_SHOW_LABELS, true)
        switchRow("Allow two-line labels", "Let longer app names wrap when the grid has room.", LauncherSettings.KEY_DRAWER_TWO_LINE_LABELS, true)
        switchRow("Show categories", "Add category filters below the search field.", LauncherSettings.KEY_SHOW_CATEGORIES, false)
        if (HavenModePreferences.isIsolationEnabled(this)) {
            switchRow(
                "Show Personal / Haven Vault bar",
                "Show the tab switcher bar at the top of the app drawer. You can still swipe or use the drawer menu when hidden.",
                LauncherSettings.KEY_DRAWER_SHOW_VAULT_SWITCHER,
                false,
            )
        }
        separator(top = 8, bottom = 8)
        sectionLabel("ADVANCED SETTINGS")
        switchRow("Search apps by index", "Open an alphabetical index from the app page search button.", LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)
        switchRow("Hide Play Store icon", "Remove the app-store shortcut from the app page header.", LauncherSettings.KEY_DRAWER_HIDE_PLAY_STORE, false)
        switchRow("Stack apps from bottom", "Start from the end of the app list, showing the last apps first.", LauncherSettings.KEY_DRAWER_STACK_FROM_BOTTOM, false)
        switchRow("Close App page after launching", "Return to Home after opening an app from the app page.", LauncherSettings.KEY_DRAWER_CLOSE_AFTER_LAUNCH, false)
        switchRow("Keep last position", "Keep the current app page mode and scroll position after leaving it.", LauncherSettings.KEY_DRAWER_KEEP_LAST_POSITION, false)
        menuRow("security", "Hidden apps", CYAN) { openHiddenApps() }
        settingRow("Notification access", "Allow Teres to mark apps with active notifications.", if (notificationAccessGranted()) "On" else "Off") { openNotificationAccess() }
        settingRow("Haven Search", "Choose app, contact, media, calendar, shortcut, and web results.", "Options") { navigate("search") }
    }

    private fun renderSearchPage() {
        pageIntro("Choose which results Teres can search on this device.")
        switchRow("Show Teres Search on Home", "Show the Search button below the dock. You can also remove it with a long-press.", LauncherSettings.KEY_SEARCH_VISIBLE_HOME, true)
        separator(top = 8, bottom = 8)
        val provider = preferences.getString(LauncherSettings.KEY_SEARCH_PROVIDER, "google") ?: "google"
        settingRow("Search provider", "Choose the provider used for web queries.", SEARCH_PROVIDERS.firstOrNull { it.second == provider }?.first ?: "Google") {
            chooseStringPref("Search provider", LauncherSettings.KEY_SEARCH_PROVIDER, "google", SEARCH_PROVIDERS)
        }
        switchRow("Show hidden apps in search results", "Include apps hidden from the app page.", LauncherSettings.KEY_SEARCH_SHOW_HIDDEN_APPS, false)
        separator(top = 8, bottom = 8)
        sectionLabel("START SCREEN")
        settingRow("Contacts", "Number of contacts shown before you type.", preferences.getInt(LauncherSettings.KEY_SEARCH_START_CONTACTS_COUNT, 2).toString()) {
            chooseSearchCount("Contacts on start screen", LauncherSettings.KEY_SEARCH_START_CONTACTS_COUNT, 2, 0..8)
        }
        settingRow("Apps", "Number of apps shown before you type.", preferences.getInt(LauncherSettings.KEY_SEARCH_START_APPS_COUNT, 4).toString()) {
            chooseSearchCount("Apps on start screen", LauncherSettings.KEY_SEARCH_START_APPS_COUNT, 4, 0..12)
        }
        separator(top = 8, bottom = 8)
        sectionLabel("SEARCH RESULTS")
        permissionSearchSwitch("Contacts", "Find names in your address book. Requires Contacts permission.", LauncherSettings.KEY_SEARCH_CONTACTS, { hasPermission(Manifest.permission.READ_CONTACTS) }) {
            pendingSearchPermissionKey = LauncherSettings.KEY_SEARCH_CONTACTS
            contactsPermissionRequest.launch(Manifest.permission.READ_CONTACTS)
        }
        switchRow("Shortcuts", "Search Android-published app shortcuts when Teres has Home access.", LauncherSettings.KEY_SEARCH_SHORTCUTS, true)
        switchRow("Use web suggestions", "Show a web search for queries that are not app names.", LauncherSettings.KEY_SEARCH_WEB, true)
        switchRow("Search apps on Play Store", "Offer a Play Store search for the current query.", LauncherSettings.KEY_SEARCH_PLAY_STORE, true)
        permissionSearchSwitch("Media", "Find photos, videos, and audio on your device. Requires media access.", LauncherSettings.KEY_SEARCH_MEDIA, ::hasMediaSearchPermission) {
            pendingSearchPermissionKey = LauncherSettings.KEY_SEARCH_MEDIA
            mediaPermissionRequest.launch(mediaSearchPermissions())
        }
        permissionSearchSwitch("Calendar", "Find events in your calendar. Requires Calendar permission.", LauncherSettings.KEY_SEARCH_CALENDAR, { hasPermission(Manifest.permission.READ_CALENDAR) }) {
            pendingSearchPermissionKey = LauncherSettings.KEY_SEARCH_CALENDAR
            calendarPermissionRequest.launch(Manifest.permission.READ_CALENDAR)
        }
        infoCard("Contacts, media, and calendar data are searched on this device only. Teres does not upload this information.")
    }

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionSearchSwitch(
        title: String,
        summary: String,
        key: String,
        hasPermission: () -> Boolean,
        requestPermission: () -> Unit,
    ) {
        addSwitchRow(
            title = title,
            summary = summary,
            checked = preferences.getBoolean(key, false) && hasPermission(),
            useLightColors = false,
            switchDescription = "Search $title",
        ) { checked ->
            if (!checked || hasPermission()) preferences.edit { putBoolean(key, checked) }
            else requestPermission()
        }
    }

    private fun chooseSearchCount(title: String, key: String, defaultValue: Int, range: IntRange) {
        val current = preferences.getInt(key, defaultValue).coerceIn(range)
        showSingleChoiceDialog(title, range.map { it.toString() to it }, current) { chosen ->
            preferences.edit { putInt(key, chosen) }
            renderPage()
        }
    }

    private fun renderGestures() {
        largeSettingsTitle("Gestures and hot keys")
        gestureRow("double_tap", "Double tap", selectedDoubleTapLabel(), trailing = null, titleSize = 18f, minHeightDp = 66, padHorizontalDp = 2, padVerticalDp = 5, contentDescriptionText = "Double tap, ${selectedDoubleTapLabel()}") {
            chooseDoubleTapAction()
        }
        gestureRow("keyboard", "Hot keys", null, trailing = "next", titleSize = 18f, minHeightDp = 49, padHorizontalDp = 2, padVerticalDp = 5, contentDescriptionText = "Hot keys") { navigate("hot_keys") }
        gestureRow("screen_off", "Smart Display Off", null, trailing = "next", titleSize = 18f, minHeightDp = 49, padHorizontalDp = 2, padVerticalDp = 5, contentDescriptionText = "Smart Display Off") { navigate("smart_display") }
        separator(top = 8, bottom = 8)
        val gesturesEnabled = preferences.getBoolean(LauncherSettings.KEY_WORKSPACE_GESTURES_ENABLED, false)
        addSwitchRow(
            title = "Enable swipe gestures",
            summary = null,
            checked = gesturesEnabled,
            minHeightDp = 48,
            padVerticalDp = 0,
            bottomMarginDp = 8,
            titleSize = 18f,
            rowSelectable = false,
            onToggle = { checked ->
                preferences.edit { putBoolean(LauncherSettings.KEY_WORKSPACE_GESTURES_ENABLED, checked) }
                renderPage()
            },
        )
        if (gesturesEnabled) {
            addSwitchRow(
                title = "Enable vibration",
                summary = null,
                checked = preferences.getBoolean(LauncherSettings.KEY_WORKSPACE_GESTURE_VIBRATION, false),
                minHeightDp = 48,
                padVerticalDp = 0,
                bottomMarginDp = 8,
                titleSize = 18f,
                rowSelectable = false,
                onToggle = { checked ->
                    preferences.edit { putBoolean(LauncherSettings.KEY_WORKSPACE_GESTURE_VIBRATION, checked) }
                    renderPage()
                },
            )
        }
        gestureRow("gesture", "Single finger", null, trailing = "next", enabled = gesturesEnabled, titleSize = 18f, minHeightDp = 49, padHorizontalDp = 2, padVerticalDp = 5, contentDescriptionText = "Single finger") {
            navigate("gesture_single")
        }
        gestureRow("two_fingers", "Double finger", null, trailing = "next", enabled = gesturesEnabled, titleSize = 18f, minHeightDp = 49, padHorizontalDp = 2, padVerticalDp = 5, contentDescriptionText = "Double finger") {
            navigate("gesture_double")
        }
    }

    private fun renderHotKeys() {
        largeSettingsTitle("Hot keys")
        pageIntro("Android controls navigation-key delivery. Teres can request the Home role, but cannot replace system Home or Back key handling.")
        sectionLabel("HOME")
        settingRow("Home (Once)", "Choose Teres as the Home app in Android's role selector.", if (roleManager.isDefaultHome()) "Teres" else "System") { requestHomeRole() }
        settingRow("Home (Double tap)", "Android does not deliver a second Home-key event to ordinary launchers.", "Unavailable", enabled = false) {}
        sectionLabel("BACK")
        settingRow("Back (Long press)", "Android navigation owns the Back gesture and key.", "Unavailable", enabled = false) {}
        infoCard("Smart Launcher exposes these mappings, but Android does not provide ordinary launchers a public API to intercept Home and Back key events.")
    }

    private fun renderSmartDisplayOffInfo() {
        largeSettingsTitle("Smart Display Off")
        settingRow("Smart Display Off", "Automatically turns off the display when the device is placed on a flat surface.", "Unavailable", enabled = false) {}
        infoCard("Teres can lock the screen through its optional system-actions accessibility service, but it does not monitor device placement in the background. Android does not expose that Smart Launcher behavior as a standard launcher API.")
        menuRow("security", "Set up system actions", currentAccent()) { openAccessibilitySettings() }
    }

    private fun renderGestureMap(title: String, gestures: List<WorkspaceSurfaceGesture>) {
        val enabled = preferences.getBoolean(LauncherSettings.KEY_WORKSPACE_GESTURES_ENABLED, false)
        largeSettingsTitle(title)
        gestures.forEachIndexed { index, gesture ->
            if (title == "Double finger" && index == 4) separator(top = 8, bottom = 8)
            val summary = workspaceGestureSummary(gesture)
            gestureRow(
                glyph = gestureGlyph(gesture),
                title = gesture.label,
                summary = summary,
                trailing = null,
                enabled = enabled,
                titleSize = 16f,
                minHeightDp = 66,
                padHorizontalDp = 0,
                padVerticalDp = 8,
                contentDescriptionText = "${gesture.label}, $summary${if (enabled) "" else ", swipe gestures are disabled"}",
            ) { chooseWorkspaceGestureAction(gesture) }
        }
        if (!enabled) infoCard("Turn on Enable swipe gestures to assign workspace actions.")
    }

    private fun largeSettingsTitle(title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 28f
            includeFontPadding = false
            typeface = LauncherTypography.typeface(this@LauncherSettingsActivity, 700)
            setTextColor(if (usesLightSettingsSurface()) INK_PRIMARY else Color.WHITE)
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun gestureRow(
        glyph: String,
        title: String,
        summary: String?,
        trailing: String?,
        enabled: Boolean = true,
        titleSize: Float,
        minHeightDp: Int,
        padHorizontalDp: Int,
        padVerticalDp: Int,
        contentDescriptionText: String,
        action: () -> Unit,
    ) {
        val lightSurface = usesLightSettingsSurface()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(minHeightDp)
            setPadding(dp(padHorizontalDp), dp(padVerticalDp), dp(padHorizontalDp), dp(padVerticalDp))
            isFocusable = enabled
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.48f
            contentDescription = contentDescriptionText
            background = selectableBackground()
            if (enabled) setOnClickListener { action() }
        }
        row.addView(SettingsGlyphView(this, glyph, if (lightSurface) 0xFF292C31.toInt() else Color.WHITE),
            LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
        row.addView(labelsColumn(
            title = title,
            summary = summary,
            titleSize = titleSize,
            titleColor = if (lightSurface) INK_PRIMARY else Color.WHITE,
            summarySize = 13f,
            summaryColor = if (lightSurface) INK_MUTED_LIGHT else 0xFF999999.toInt(),
            summaryTopPadDp = 2,
            summaryEndPadDp = 0,
            summaryMaxLines = null,
        ), LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) {
            row.addView(SettingsGlyphView(this, trailing, if (lightSurface) 0xFF9A9DA3.toInt() else 0xFFBDBDBD.toInt()),
                LinearLayout.LayoutParams(dp(23), dp(23)))
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun gestureGlyph(gesture: WorkspaceSurfaceGesture): String = when (gesture) {
        WorkspaceSurfaceGesture.SWIPE_LEFT, WorkspaceSurfaceGesture.DUAL_SWIPE_LEFT -> "swipe_left"
        WorkspaceSurfaceGesture.SWIPE_RIGHT, WorkspaceSurfaceGesture.DUAL_SWIPE_RIGHT -> "swipe_right"
        WorkspaceSurfaceGesture.SWIPE_UP, WorkspaceSurfaceGesture.DUAL_SWIPE_UP -> "swipe_up"
        WorkspaceSurfaceGesture.SWIPE_DOWN, WorkspaceSurfaceGesture.DUAL_SWIPE_DOWN -> "swipe_down"
        WorkspaceSurfaceGesture.PINCH_IN, WorkspaceSurfaceGesture.PINCH_OUT -> "gesture"
        WorkspaceSurfaceGesture.ROTATE_RIGHT, WorkspaceSurfaceGesture.ROTATE_LEFT -> "animation"
    }

    private fun workspaceGestureSummary(gesture: WorkspaceSurfaceGesture): String {
        val stored = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_ACTION_PREFIX + gesture.storageKey, null)
        if (stored != null) {
            val action = WorkspaceIconGestureAction.fromStorage(stored)
            val savedLabel = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_LABEL_PREFIX + gesture.storageKey, null)
                ?.takeIf(String::isNotBlank)
            return when (action) {
                WorkspaceIconGestureAction.POPUP_WIDGET -> "${savedLabel ?: "Battery"} popup"
                WorkspaceIconGestureAction.LAUNCH_APP, WorkspaceIconGestureAction.LAUNCH_SHORTCUT -> savedLabel ?: action.label
                else -> action.label
            }
        }
        return when (gesture) {
            WorkspaceSurfaceGesture.SWIPE_UP -> if (preferences.getBoolean(LauncherSettings.KEY_GESTURE_SWIPE_UP, true)) "App page" else "None"
            WorkspaceSurfaceGesture.SWIPE_DOWN -> if (preferences.getBoolean(LauncherSettings.KEY_GESTURE_SWIPE_DOWN, false)) "Search page" else "None"
            WorkspaceSurfaceGesture.SWIPE_LEFT,
            WorkspaceSurfaceGesture.SWIPE_RIGHT -> if (preferences.getBoolean(LauncherSettings.KEY_GESTURE_PAGE_SWIPE, true)) "Change Home screen" else "None"
            else -> "None"
        }
    }

    private fun chooseWorkspaceGestureAction(gesture: WorkspaceSurfaceGesture) {
        catalog.loadAsync { installedApps ->
            if (isFinishing || isDestroyed) return@loadAsync
            val suffix = gesture.storageKey
            val actionKey = LauncherSettings.KEY_WORKSPACE_GESTURE_ACTION_PREFIX + suffix
            val current = preferences.getString(actionKey, null)?.let { stored ->
                com.arcturus.haven.launcher.core.model.WorkspaceIconGestureAssignment(
                    gesture = gesture.pickerGesture,
                    action = WorkspaceIconGestureAction.fromStorage(stored),
                    targetPackage = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_PACKAGE_PREFIX + suffix, null),
                    targetClass = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_CLASS_PREFIX + suffix, null),
                    targetLabel = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_LABEL_PREFIX + suffix, null),
                    targetShortcutId = preferences.getString(LauncherSettings.KEY_WORKSPACE_GESTURE_SHORTCUT_ID_PREFIX + suffix, null),
                )
            }
            WorkspaceGestureActionPickerDialog(
                activity = this,
                palette = LauncherAppearance.resolve(this),
                catalog = catalog,
                gesture = gesture.pickerGesture,
                appsInitiallyLoaded = installedApps,
                current = current,
                gestureLabel = gesture.label,
                workspaceGesture = true,
                onSelect = { assignment ->
                    preferences.edit {
                        putString(actionKey, assignment.action.storageKey)
                        listOf(
                            LauncherSettings.KEY_WORKSPACE_GESTURE_PACKAGE_PREFIX to assignment.targetPackage,
                            LauncherSettings.KEY_WORKSPACE_GESTURE_CLASS_PREFIX to assignment.targetClass,
                            LauncherSettings.KEY_WORKSPACE_GESTURE_LABEL_PREFIX to assignment.targetLabel,
                            LauncherSettings.KEY_WORKSPACE_GESTURE_SHORTCUT_ID_PREFIX to assignment.targetShortcutId,
                        ).forEach { (prefix, value) ->
                            val fullKey = prefix + suffix
                            if (value != null) putString(fullKey, value) else remove(fullKey)
                        }
                    }
                    renderPage()
                    if (assignment.action in ACCESSIBILITY_REQUIRED_ICON_ACTIONS) {
                        promptAccessibilityIfNeeded("Android requires the Teres system-actions service for this action. It does not read window content or accessibility events.")
                    }
                },
            ).show()
        }
    }

    private fun renderBackup() {
        pageIntro("Save your shortcut layout and launcher preferences to a JSON file.")
        menuRow("backup", "Create backup", CYAN) { createBackup.launch("haven-launcher-backup.json") }
        menuRow("wallpaper", "Restore backup", MINT) { openBackup.launch(arrayOf("application/json", "text/*")) }
        sectionLabel("BACKUP DETAILS")
        infoCard("App shortcuts and Teres settings are included. Android widget IDs are tied to the current installation, so restore keeps the widgets already on this device.")
    }

    private fun renderPages() {
        pageIntro("Manage home workspace pages. Empty pages can be added and removed.")
        val layout = workspace.load()
        val count = preferences.workspacePageCount(layout)
        repeat(count) { index ->
            val items = layout.items.count { it.page == index }
            settingRow("Home screen ${index + 1}", "$items shortcut${if (items == 1) "" else "s"}${if (index == count - 1 && items == 0) " · empty" else ""}", "Open") {
                preferences.edit { putInt(LauncherSettings.KEY_OPEN_PAGE_INDEX, index) }
                finish()
            }
        }
        menuRow("pages", if (count >= LauncherSettings.MAX_WORKSPACE_PAGES) "Maximum pages reached" else "Add empty page", currentAccent()) {
            if (count < LauncherSettings.MAX_WORKSPACE_PAGES) {
                preferences.edit { putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, count + 1) }
                renderPage()
            }
        }
        val lastOccupied = layout.items.any { it.page == count - 1 }
        settingRow("Remove last empty page", if (lastOccupied) "Move or remove its shortcuts first." else "Remove page $count", if (lastOccupied || count <= 1) "Unavailable" else "Remove") {
            if (!lastOccupied && count > 1) {
                preferences.edit { putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, count - 1) }
                renderPage()
            } else toast("Only an empty page can be removed")
        }
    }

    private fun renderVaultPage() {
        pageIntro("Configure the isolated Haven Vault drawer page and Search behavior.")
        switchRow("Show Personal / Haven Vault bar", "Show the tab switcher bar at the top of the app drawer. You can still swipe or use the drawer menu when hidden.", LauncherSettings.KEY_DRAWER_SHOW_VAULT_SWITCHER, false)
        switchRow("Show frozen apps in Vault drawer", "Keep frozen work-profile apps visible with a frost badge for one-tap unfreeze and launch.", LauncherSettings.KEY_VAULT_SHOW_FROZEN_APPS, true)
        switchRow("Include Vault apps in Search", "Show matching Haven Vault apps in Haven Search when the work profile is active.", LauncherSettings.KEY_SEARCH_VAULT_APPS, false)
        separator(top = 8, bottom = 8)
        menuRow("security", "Work Profile & Isolation settings", MINT) { navigate("isolation") }
        menuRow("apps", "Open full Isolation Manager", CYAN) { openIsolationManager() }
    }

    private fun renderIsolation() {
        pageIntro("Manage Android Work Profile provisioning, Auto-Freeze, File Shuttle, and cross-profile isolation policies.")
        val profileState = HavenProfileSession.inspectState(this)
        val hasProfile = profileState != VaultProfileState.UNPROVISIONED && profileState != VaultProfileState.SETUP_PENDING
        val statusLabel = when (profileState) {
            VaultProfileState.UNPROVISIONED -> "Not set up"
            VaultProfileState.SETUP_PENDING -> "Setup pending"
            VaultProfileState.PROFILE_PAUSED -> "Paused (Quiet Mode)"
            VaultProfileState.CONNECTED -> "Active · Connected"
            VaultProfileState.IDLE_DISCONNECTED, VaultProfileState.CONNECTING -> "Active"
            VaultProfileState.ERROR -> "Unavailable"
        }
        sectionLabel("WORK PROFILE")
        settingRow(
            "Profile status",
            when (profileState) {
                VaultProfileState.UNPROVISIONED -> "Provision an isolated Android Work Profile to clone and freeze apps."
                VaultProfileState.SETUP_PENDING -> "Tap the system provisioning notification to finalize Work Profile setup."
                VaultProfileState.PROFILE_PAUSED -> "Work profile apps and background services are paused."
                else -> "Haven owns and manages your isolated Work Profile."
            },
            statusLabel,
        ) {
            when (profileState) {
                VaultProfileState.UNPROVISIONED, VaultProfileState.SETUP_PENDING -> setupWizardLauncher.launch(null)
                VaultProfileState.PROFILE_PAUSED -> {
                    HavenProfileSession.setWorkProfilePaused(this, false)
                    renderPage()
                }
                else -> openIsolationManager()
            }
        }
        if (hasProfile) {
            val isPaused = profileState == VaultProfileState.PROFILE_PAUSED
            settingRow(if (isPaused) "Resume work profile" else "Pause work profile", "Toggle Android Quiet Mode for all isolated apps.", if (isPaused) "Resume" else "Pause") {
                HavenProfileSession.setWorkProfilePaused(this, !isPaused)
                renderPage()
            }
            menuRow("apps", "Open full Isolation Manager", CYAN) { openIsolationManager() }
        }

        val manager = SettingsManager.getInstance()
        separator(top = 8, bottom = 8)
        sectionLabel("AUTO-FREEZE")
        isolationSwitchRow("Auto Freeze Service", "Automatically freeze apps launched via Unfreeze & Launch when the screen locks.", manager.autoFreezeServiceEnabled, hasProfile) { enabled ->
            manager.autoFreezeServiceEnabled = enabled
            renderPage()
        }
        settingRow("Auto Freeze Delay", "Wait time after screen lock before freezing pending apps.", "${manager.autoFreezeDelay / 60} min", enabled = hasProfile && manager.autoFreezeServiceEnabled) {
            val delays = intArrayOf(0, 60, 120, 300)
            AlertDialog.Builder(this)
                .setTitle("Auto Freeze Delay")
                .setSingleChoiceItems(delays.map { "${it / 60} min" }.toTypedArray(), delays.indexOf(manager.autoFreezeDelay).coerceAtLeast(0)) { dialog, which ->
                    withWorkService {
                        manager.autoFreezeDelay = delays[which]
                        dialog.dismiss()
                        renderPage()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        isolationSwitchRow("Skip Foreground Apps", "Do not freeze apps visible in foreground when locking the screen.", manager.skipForegroundEnabled, hasProfile && manager.autoFreezeServiceEnabled) { enabled ->
            withWorkService { work ->
                if (!enabled || hasWorkPermission({ work.hasUsageStatsPermission() && Utility.checkUsageStatsPermission(this) }, R.string.request_usage_stats, Settings.ACTION_USAGE_ACCESS_SETTINGS)) {
                    manager.skipForegroundEnabled = enabled
                }
                renderPage()
            }
        }

        separator(top = 8, bottom = 8)
        sectionLabel("CROSS-PROFILE INTERACTION")
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val fileShuttleSupported = Build.VERSION.SDK_INT != Build.VERSION_CODES.Q && !am.isLowRamDevice
        isolationSwitchRow("File Shuttle", "Browse and copy files across profiles through Android Documents UI.", manager.crossProfileFileChooserEnabled, hasProfile && fileShuttleSupported) { enabled ->
            withWorkService { work ->
                if (!enabled) {
                    manager.crossProfileFileChooserEnabled = false
                    renderPage()
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                    (hasWorkPermission({ work.hasAllFileAccessPermission() && Utility.checkAllFileAccessPermission() }, R.string.request_storage_manager, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) &&
                        hasWorkPermission({ work.hasSystemAlertPermission() && Utility.checkSystemAlertPermission(this) }, R.string.request_system_alert, Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                ) {
                    manager.crossProfileFileChooserEnabled = true
                    renderPage()
                } else {
                    renderPage()
                }
            }
        }
        isolationSwitchRow("Allow file picking between profiles", "Let apps request files from the other profile via Android system pickers.", manager.crossProfilePickerEnabled, hasProfile) { enabled ->
            withWorkService {
                manager.crossProfilePickerEnabled = enabled
                renderPage()
            }
        }
        isolationSwitchRow("Block contacts searching", "Deny access from main profile to contacts inside the work profile.", manager.blockContactsSearchingEnabled, hasProfile) { enabled ->
            withWorkService {
                manager.blockContactsSearchingEnabled = enabled
                renderPage()
            }
        }
        isolationSwitchRow("Payment Service Stub", "Enable a main-profile NFC stub so contactless payment settings allow work-profile payment apps.", manager.paymentStubEnabled, true) { enabled ->
            manager.paymentStubEnabled = enabled
            renderPage()
        }
    }

    private inline fun hasWorkPermission(check: () -> Boolean, messageRes: Int, action: String): Boolean {
        if (runCatching(check).getOrDefault(false)) return true
        promptSpecialPermission(getString(messageRes), action)
        return false
    }

    private fun isolationSwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
        addSwitchRow(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            padVerticalDp = 8,
            titleSize = 16f,
            summarySize = 13f,
            summaryTopPadDp = 2,
            summaryEndPadDp = 12,
            summaryMaxLines = null,
            rowSelectable = false,
            guardChanged = true,
            onToggle = onToggle,
        )
    }

    private fun withWorkService(block: (IHavenService) -> Unit) {
        val tag = "op_settings_${System.nanoTime()}"
        HavenProfileSession.acquire(
            host = this,
            consumerTag = tag,
            onReady = { _, work ->
                try {
                    block(work)
                } finally {
                    HavenProfileSession.release(tag)
                }
            },
            onUnavailable = {
                HavenProfileSession.release(tag)
                toast("Wake the work profile first to change this setting")
            },
        )
    }

    private fun promptSpecialPermission(message: String, action: String) {
        AlertDialog.Builder(this)
            .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(android.R.string.ok) { _, _ -> runSystemIntent(Intent(action), "Settings screen unavailable") }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openIsolationManager() = runSystemIntent(Intent(this, MainActivity::class.java), "Isolation Manager could not be opened")

    private fun renderPrivacy() {
        pageIntro("Manage notification access, hidden apps, and Android app permissions.")
        menuRow("notifications", "Notification access", PEACH) { openNotificationAccess() }
        menuRow("security", "Manage hidden apps", CYAN) { openHiddenApps() }
        menuRow("security", "Android app permissions", MINT) {
            runSystemIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()), "App settings are unavailable")
        }
        menuRow("security", "Privacy settings", LAVENDER) {
            runSystemIntent(Intent(Settings.ACTION_PRIVACY_SETTINGS), "Privacy settings are unavailable")
        }
        sectionLabel("DATA")
        infoCard("Teres stores workspace layout and appearance preferences on this device. Hidden apps remain installed and can be restored from App page settings.")
    }

    private fun pageIntro(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (usesLightSettingsSurface()) 0xFF6F737A.toInt() else 0xFFAAAAAA.toInt())
            setPadding(dp(2), dp(7), dp(4), dp(15))
        })
    }

    private fun sectionLabel(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 11f
            letterSpacing = 0.15f
            setTextColor(if (usesLightSettingsSurface()) 0xFF9A9DA3.toInt() else 0xFF858585.toInt())
            setPadding(dp(2), dp(15), dp(2), dp(7))
        })
    }

    private fun collapsibleSectionLabel(text: String, expanded: Boolean, onClick: () -> Unit) {
        val color = if (usesLightSettingsSurface()) 0xFF9A9DA3.toInt() else 0xFF858585.toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(2), dp(10), dp(2), dp(4))
            isFocusable = true
            background = selectableBackground()
            contentDescription = "$text, ${if (expanded) "expanded" else "collapsed"}"
            setOnClickListener { onClick() }
            addView(TextView(this@LauncherSettingsActivity).apply {
                this.text = text.uppercase(Locale.getDefault())
                textSize = 11f
                letterSpacing = 0.15f
                setTextColor(color)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(SettingsGlyphView(this@LauncherSettingsActivity, if (expanded) "chevron_up" else "chevron_down", color),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(5) })
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun menuRow(
        glyph: String,
        label: String,
        color: Int,
        trailing: String? = null,
        reorderId: String? = null,
        action: () -> Unit,
    ) {
        val lightRoot = usesLightSettingsSurface()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (lightRoot) 56 else 58)
            setPadding(dp(3), dp(4), dp(3), dp(4))
            isFocusable = true
            background = selectableBackground()
            contentDescription = if (reorderId == null) label else "$label. Hold and drag to reorder"
            setOnClickListener { action() }
        }
        if (reorderId != null) {
            row.setOnLongClickListener {
                row.startDragAndDrop(ClipData.newPlainText("settings-page", reorderId), View.DragShadowBuilder(row), reorderId, 0)
            }
            row.setOnDragListener { target, event ->
                val source = event.localState as? String
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> source in REORDER_PAGE_IDS
                    DragEvent.ACTION_DRAG_ENTERED -> { target.alpha = 0.62f; true }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { target.alpha = 1f; true }
                    DragEvent.ACTION_DROP -> {
                        if (source != null && source != reorderId) {
                            val current = preferences.getString(LauncherSettings.KEY_SETTINGS_PAGE_ORDER, "home,app_page,vault,search")
                                .orEmpty().split(',').filter { it in REORDER_PAGE_IDS }.distinct().toMutableList()
                            REORDER_PAGE_IDS.filterNot { it in current }.forEach(current::add)
                            current.remove(source)
                            current.add(current.indexOf(reorderId).coerceAtLeast(0), source)
                            preferences.edit { putString(LauncherSettings.KEY_SETTINGS_PAGE_ORDER, current.joinToString(",")) }
                            renderPage()
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        row.addView(SettingsGlyphView(this, glyph, color), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(28) })
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(if (lightRoot) INK_PRIMARY else Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) {
            row.addView(SettingsGlyphView(this, trailing, if (trailing == "handle") 0xFF777777.toInt() else 0xFFBDBDBD.toInt()),
                LinearLayout.LayoutParams(dp(23), dp(23)))
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun settingRow(
        title: String,
        summary: String,
        value: String? = null,
        enabled: Boolean = true,
        action: () -> Unit,
    ) {
        val lightSurface = usesLightSettingsSurface()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(2), dp(8), dp(2), dp(8))
            isFocusable = enabled
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.48f
            background = selectableBackground()
            contentDescription = "$title. $summary"
            if (enabled) setOnClickListener { action() }
        }
        row.addView(labelsColumn(
            title = title,
            summary = summary,
            titleSize = 15f,
            titleColor = if (lightSurface) INK_PRIMARY else Color.WHITE,
            summarySize = 12f,
            summaryColor = if (lightSurface) 0xFF6F737A.toInt() else 0xFF999999.toInt(),
            summaryTopPadDp = 3,
            summaryEndPadDp = 7,
            summaryMaxLines = 2,
        ), LinearLayout.LayoutParams(0, -2, 1f))
        if (value != null) {
            row.addView(TextView(this).apply {
                text = value
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (enabled) currentAccent() else if (lightSurface) 0xFF8C9096.toInt() else 0xFF777777.toInt())
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(7), 0, dp(7), 0)
            })
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun switchRow(title: String, summary: String, key: String, defaultValue: Boolean) {
        addSwitchRow(title, summary, preferences.getBoolean(key, defaultValue)) { checked ->
            preferences.edit { putBoolean(key, checked) }
        }
    }

    private fun addSwitchRow(
        title: String,
        summary: String?,
        checked: Boolean,
        enabled: Boolean = true,
        minHeightDp: Int = 68,
        padVerticalDp: Int = 7,
        bottomMarginDp: Int = 0,
        titleSize: Float = 15f,
        summarySize: Float = 12f,
        summaryTopPadDp: Int = 3,
        summaryEndPadDp: Int = 8,
        summaryMaxLines: Int? = 2,
        useLightColors: Boolean = true,
        rowSelectable: Boolean = true,
        switchDescription: String = title,
        guardChanged: Boolean = false,
        onToggle: (Boolean) -> Unit,
    ) {
        val lightSurface = useLightColors && usesLightSettingsSurface()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(minHeightDp)
            setPadding(dp(2), dp(padVerticalDp), 0, dp(padVerticalDp))
            if (!enabled) alpha = 0.45f
            isEnabled = enabled
            isFocusable = rowSelectable
            if (rowSelectable) background = selectableBackground()
            contentDescription = if (summary != null && useLightColors && rowSelectable && !guardChanged) "$title. $summary"
            else if (summary != null && !useLightColors) "$title. $summary"
            else title
        }
        val titleColor = if (lightSurface) INK_PRIMARY else Color.WHITE
        if (summary != null) {
            row.addView(labelsColumn(
                title = title,
                summary = summary,
                titleSize = titleSize,
                titleColor = titleColor,
                summarySize = summarySize,
                summaryColor = if (lightSurface) INK_MUTED_LIGHT else 0xFF999999.toInt(),
                summaryTopPadDp = summaryTopPadDp,
                summaryEndPadDp = summaryEndPadDp,
                summaryMaxLines = summaryMaxLines,
            ), LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            row.addView(TextView(this).apply {
                text = title
                textSize = titleSize
                setTextColor(titleColor)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val control = SwitchCompat(this).apply {
            isChecked = checked
            isEnabled = enabled
            contentDescription = switchDescription
            thumbTintList = ColorStateList.valueOf(currentAccent())
            trackTintList = ColorStateList.valueOf(0x6675D9C8)
            setOnCheckedChangeListener { _, newChecked ->
                if (!guardChanged || newChecked != checked) onToggle(newChecked)
            }
        }
        row.addView(control, LinearLayout.LayoutParams(-2, dp(48)))
        if (enabled) row.setOnClickListener { control.isChecked = !control.isChecked }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (bottomMarginDp > 0) bottomMargin = dp(bottomMarginDp) })
    }

    private fun labelsColumn(
        title: String,
        summary: String?,
        titleSize: Float,
        titleColor: Int,
        summarySize: Float,
        summaryColor: Int,
        summaryTopPadDp: Int,
        summaryEndPadDp: Int,
        summaryMaxLines: Int?,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@LauncherSettingsActivity).apply {
            text = title
            textSize = titleSize
            setTextColor(titleColor)
        })
        if (summary != null) {
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = summary
                textSize = summarySize
                setTextColor(summaryColor)
                setPadding(0, dp(summaryTopPadDp), dp(summaryEndPadDp), 0)
                if (summaryMaxLines != null) maxLines = summaryMaxLines
            })
        }
    }

    private fun separator(top: Int = 8, bottom: Int = 8, color: Int = if (usesLightSettingsSurface()) 0xFFE8EBEF.toInt() else 0xFF282828.toInt()) {
        content.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        })
    }

    private fun infoCard(message: String) {
        val lightSurface = usesLightSettingsSurface()
        content.addView(TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(if (lightSurface) 0xFF5D626A.toInt() else 0xFFB8B8B8.toInt())
            background = roundedDrawable(if (lightSurface) 0xFFF1F3F6.toInt() else 0xFF1B1B1B.toInt(), dp(10))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(10) })
    }

    private fun selectedDoubleTapLabel(): String {
        val action = preferences.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, LauncherSettings.GESTURE_ACTION_NONE)
        return when (action) {
            LauncherSettings.GESTURE_ACTION_GO_TO_PAGE -> "Go to page ${preferences.getInt(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PAGE, 0) + 1}"
            LauncherSettings.GESTURE_ACTION_APP,
            LauncherSettings.GESTURE_ACTION_SHORTCUT -> preferences.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, "Launch an app")
                ?.takeIf(String::isNotBlank) ?: "Launch an app"
            else -> DOUBLE_TAP_LABELS[action] ?: "None"
        }
    }

    private fun chooseDoubleTapAction() {
        val isolationEnabled = HavenModePreferences.isIsolationEnabled(this)
        val options = DOUBLE_TAP_OPTIONS.filter { isolationEnabled || it.id !in ISOLATION_GESTURE_ACTIONS }
        val palette = LauncherAppearance.resolve(this)
        val dialog = Dialog(this, R.style.Theme_Teres_FullscreenDialog)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val search = EditText(this).apply {
            hint = "Search"
            setSingleLine(true)
            textSize = 16f
            setTextColor(palette.textPrimary)
            setHintTextColor(palette.textSecondary)
            background = roundedDrawable(palette.surfaceVariant, dp(28))
            setPadding(dp(18), dp(12), dp(18), dp(12))
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            contentDescription = "Search actions, apps, and shortcuts"
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(palette.textPrimary)
                contentDescription = "Back"
                background = selectableBackground()
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(dp(48), dp(56)))
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = getString(R.string.settings_double_tap_title)
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.textPrimary)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val pickerRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
            addView(header)
            addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
            addView(tabs, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(8) })
            addView(ScrollView(this@LauncherSettingsActivity).apply {
                isFillViewport = false
                clipToPadding = false
                addView(results)
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
        }

        var tab = "Actions"
        var appsLoaded = false
        var loadingApps = false
        var availableApps = emptyList<LaunchableApp>()
        lateinit var renderResults: () -> Unit

        fun addGroup(label: String) {
            results.addView(TextView(this).apply {
                text = label
                textSize = 11f
                letterSpacing = 0.12f
                setTextColor(palette.textSecondary)
                setPadding(0, dp(16), 0, dp(10))
            }, LinearLayout.LayoutParams(-1, -2))
        }

        fun loadApps() {
            if (appsLoaded || loadingApps) return
            loadingApps = true
            catalog.loadAsync { loaded ->
                if (isFinishing || isDestroyed) return@loadAsync
                availableApps = loaded.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
                appsLoaded = true
                loadingApps = false
                if (tab != "Actions") renderResults()
            }
        }

        fun actionRow(option: GesturePickerOption) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(72)
                setPadding(dp(4), dp(7), dp(4), dp(7))
                background = selectableBackground()
                isFocusable = true
                contentDescription = "${option.title}. ${option.summary}"
                setOnClickListener { dialog.dismiss(); chooseGestureOption(option) }
                addView(FrameLayout(this@LauncherSettingsActivity).apply {
                    background = roundedDrawable(palette.surfaceVariant, dp(14))
                    addView(SettingsGlyphView(this@LauncherSettingsActivity, option.glyph, palette.textPrimary),
                        FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(18) })
                addView(labelsColumn(option.title, option.summary, 15f, palette.textPrimary, 12f, palette.textSecondary, 2, 0, 2),
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            results.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        fun appRow(app: LaunchableApp) {
            val label = app.entry.label
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(dp(4), dp(7), dp(4), dp(7))
                background = selectableBackground()
                isFocusable = true
                contentDescription = label
                setOnClickListener {
                    preferences.edit {
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, LauncherSettings.GESTURE_ACTION_APP)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PACKAGE, app.entry.packageName)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_CLASS, app.entry.className)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, label)
                    }
                    dialog.dismiss()
                    renderPage()
                }
                addView(ImageView(this@LauncherSettingsActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(CircularAppIconDrawable(app.icon ?: packageManager.defaultActivityIcon, palette.accent))
                    contentDescription = label
                }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(22) })
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = label
                    textSize = 15f
                    setTextColor(palette.textPrimary)
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            results.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        renderResults = {
            results.removeAllViews()
            val query = search.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
            when (tab) {
                "Actions" -> {
                    options.filter { option ->
                        query.isBlank() || listOf(option.title, option.summary, option.group).any { it.lowercase(Locale.getDefault()).contains(query) }
                    }.groupBy { it.group }.forEach { (group, groupOptions) ->
                        addGroup(group)
                        groupOptions.forEach(::actionRow)
                    }
                    if (results.isEmpty()) addGroup("No matching actions")
                }
                "Apps" -> if (!appsLoaded) {
                    addGroup(if (loadingApps) "Loading apps…" else "Loading apps")
                    loadApps()
                } else {
                    availableApps.filter { query.isBlank() || it.entry.label.lowercase(Locale.getDefault()).contains(query) }.forEach(::appRow)
                }
                else -> {
                    addGroup("ANDROID APP SHORTCUTS")
                    infoCardInPicker(results, palette,
                        if (catalog.hasShortcutHostPermission()) "Choose an app to see its published shortcuts." else "Android exposes app shortcuts to the selected Home app. Teres must be selected as Home before it can browse them.")
                    results.addView(TextView(this).apply {
                        text = if (catalog.hasShortcutHostPermission()) "Choose an app" else "Choose Teres as Home and browse shortcuts"
                        textSize = 15f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.onAccent)
                        gravity = Gravity.CENTER
                        background = roundedDrawable(palette.accent, dp(24))
                        minHeight = dp(52)
                        setOnClickListener { dialog.dismiss(); chooseDoubleTapShortcut() }
                    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(16) })
                }
            }
        }

        fun rebuildTabs() {
            tabs.removeAllViews()
            listOf("Actions", "Apps", "Shortcuts").forEachIndexed { index, name ->
                val selected = tab == name
                tabs.addView(TextView(this).apply {
                    text = name
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (selected) palette.onAccent else palette.textPrimary)
                    gravity = Gravity.CENTER
                    background = roundedDrawable(if (selected) palette.accent else palette.surfaceVariant, dp(28))
                    isFocusable = true
                    contentDescription = "$name tab${if (selected) ", selected" else ""}"
                    setOnClickListener {
                        tab = name
                        rebuildTabs()
                        if (name != "Actions") loadApps()
                        renderResults()
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (index > 0) marginStart = dp(8) })
            }
        }

        search.doOnTextChanged { _, _, _, _ -> renderResults() }
        rebuildTabs()
        renderResults()
        dialog.setContentView(pickerRoot)
        ViewCompat.setOnApplyWindowInsetsListener(pickerRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(22), bars.top + dp(8), dp(22), maxOf(bars.bottom, ime.bottom) + dp(10))
            insets
        }
        dialog.showFullscreen(palette.surface, !palette.isDark)
    }

    private fun infoCardInPicker(parent: LinearLayout, palette: LauncherPalette, message: String) {
        parent.addView(TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(palette.textSecondary)
            background = roundedDrawable(palette.surfaceVariant, dp(18))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun chooseGestureOption(option: GesturePickerOption) {
        when (option.id) {
            LauncherSettings.GESTURE_ACTION_GO_TO_PAGE -> {
                val pages = (0 until preferences.workspacePageCount(workspace.load())).map { "Home screen ${it + 1}" to it }
                val selected = preferences.getInt(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PAGE, 0).coerceIn(pages.indices)
                showSingleChoiceDialog("Go to page", pages, selected) { which ->
                    preferences.edit {
                        putInt(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PAGE, which)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, option.id)
                    }
                    renderPage()
                }
                return
            }
            LauncherSettings.GESTURE_ACTION_WEB_PAGE -> {
                val address = EditText(this).apply {
                    hint = "https://example.com"
                    setSingleLine(true)
                    setText(preferences.getString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_URL, ""))
                    setSelection(text.length)
                }
                AlertDialog.Builder(this)
                    .setTitle("Open web page")
                    .setMessage("Enter the address to open when you double tap an empty Home cell.")
                    .setView(address)
                    .setPositiveButton("Save") { _, _ ->
                        var value = address.text.toString().trim()
                        if (value.isNotBlank() && !value.startsWith("http://", true) && !value.startsWith("https://", true)) value = "https://$value"
                        val uri = runCatching { value.toUri() }.getOrNull()
                        if (uri == null || uri.scheme !in HTTP_SCHEMES || uri.host.isNullOrBlank()) {
                            toast("Enter a valid web address")
                        } else {
                            preferences.edit {
                                putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_URL, uri.toString())
                                putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, option.id)
                                putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, option.title)
                            }
                            renderPage()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }
        preferences.edit {
            putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, option.id)
            putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, option.title)
        }
        renderPage()
        if (option.requiresAccessibility) {
            promptAccessibilityIfNeeded("Android requires you to enable the Teres system-actions service before it can open system panels or lock the screen. Teres does not read window content or accessibility events.")
        }
    }

    private fun promptAccessibilityIfNeeded(message: String) {
        if (LauncherActionAccessibilityService.isEnabled(this)) return
        AlertDialog.Builder(this)
            .setTitle("Enable Teres system actions")
            .setMessage(message)
            .setPositiveButton("Open Accessibility settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openAccessibilitySettings() = runSystemIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Android Accessibility settings are unavailable")

    private fun chooseDoubleTapShortcut() {
        if (!catalog.hasShortcutHostPermission()) {
            if (roleManager.isDefaultHome()) {
                toast("Android has not granted shortcut access to the selected Home app")
                return
            }
            AlertDialog.Builder(this)
                .setTitle("Home access required")
                .setMessage("Android lets the selected Home app browse shortcuts published by installed apps. Choose Teres Launcher as Home to continue.")
                .setPositiveButton("Choose Teres as Home") { _, _ ->
                    pendingShortcutPicker = true
                    val request = roleManager.requestIntent()
                    if (request == null) {
                        pendingShortcutPicker = false
                        toast("Home app settings are unavailable")
                    } else roleRequest.launch(request)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed) return@loadAsync
            val installedApps = loaded.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
            if (installedApps.isEmpty()) {
                toast("No launchable apps were found")
                return@loadAsync
            }
            AlertDialog.Builder(this)
                .setTitle("Choose an app")
                .setItems(installedApps.map { it.entry.label }.toTypedArray()) { _, which -> chooseShortcutForApp(installedApps[which]) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun chooseShortcutForApp(app: LaunchableApp) {
        catalog.loadShortcutsAsync(app) { shortcuts ->
            if (isFinishing || isDestroyed) return@loadShortcutsAsync
            if (shortcuts.isEmpty()) {
                toast("${app.entry.label} has no available app shortcuts")
                return@loadShortcutsAsync
            }
            val labels = shortcuts.map { shortcut ->
                shortcut.shortLabel?.toString()?.takeIf(String::isNotBlank)
                    ?: shortcut.longLabel?.toString()?.takeIf(String::isNotBlank)
                    ?: shortcut.id
            }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Choose a shortcut")
                .setItems(labels) { _, which ->
                    val selected = shortcuts[which]
                    preferences.edit {
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, LauncherSettings.GESTURE_ACTION_SHORTCUT)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PACKAGE, app.entry.packageName)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_CLASS, app.entry.className)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_SHORTCUT_ID, selected.id)
                        putString(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, labels[which])
                    }
                    renderPage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun chooseAccent() = chooseStringPref("Accent color", LauncherSettings.KEY_THEME_ACCENT, LauncherAppearance.ACCENT_WALLPAPER, ACCENT_OPTIONS)

    private fun chooseDrawerColumns() = chooseNumber("App page columns", intArrayOf(4, 5, 6), preferences.drawerColumns()) {
        preferences.edit { putInt(LauncherSettings.KEY_DRAWER_COLUMNS, it) }
        renderPage()
    }

    private fun drawerLaunchView() = DrawerLaunchView.fromPreference(preferences.getString(LauncherSettings.KEY_DRAWER_LAUNCH_VIEW, null))
        .let { if (it == DrawerLaunchView.APP_INDEX && !preferences.getBoolean(LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)) DrawerLaunchView.ALL_APPS else it }

    private fun chooseDrawerLaunchView() {
        val canSearchByIndex = preferences.getBoolean(LauncherSettings.KEY_DRAWER_SEARCH_BY_INDEX, false)
        val options = DrawerLaunchView.entries.filter { canSearchByIndex || it != DrawerLaunchView.APP_INDEX }
        showSingleChoiceDialog("Launch view", options.map { it.label to it }, drawerLaunchView()) { chosen ->
            preferences.edit { putString(LauncherSettings.KEY_DRAWER_LAUNCH_VIEW, chosen.preferenceValue) }
            renderPage()
        }
    }

    private fun chooseIconSize() = chooseNumber("App icon size", intArrayOf(48, 56, 64, 68), preferences.drawerIconSize()) {
        preferences.edit { putInt(LauncherSettings.KEY_DRAWER_ICON_SIZE, it) }
        renderPage()
    }

    private fun chooseDrawerRowSpacing() {
        val values = intArrayOf(0, 2, 4, 6, 8, 12, 16, 20, 24)
        val configured = preferences.getInt(LauncherSettings.KEY_DRAWER_ROW_SPACING, -1)
        val options = listOf("Automatic · tighter when names are hidden" to -1) + values.map { "$it dp" to it }
        showSingleChoiceDialog("App row spacing", options, configured.takeIf { it >= 0 } ?: -1, negativeLabel = "Close") { chosen ->
            preferences.edit {
                if (chosen < 0) remove(LauncherSettings.KEY_DRAWER_ROW_SPACING)
                else putInt(LauncherSettings.KEY_DRAWER_ROW_SPACING, chosen)
            }
            renderPage()
        }
    }

    private fun chooseNumber(title: String, values: IntArray, current: Int, selected: (Int) -> Unit) {
        val suffix = if (title.contains("columns")) " columns" else " dp"
        showSingleChoiceDialog(title, values.map { "$it$suffix" to it }, current, onSelect = selected)
    }

    private fun chooseStringPref(
        title: String,
        key: String,
        defaultValue: String,
        options: List<Pair<String, String>>,
        afterSave: () -> Unit = {},
    ) {
        val current = preferences.getString(key, defaultValue) ?: defaultValue
        showSingleChoiceDialog(title, options, current) { chosen ->
            preferences.edit { putString(key, chosen) }
            renderPage()
            afterSave()
        }
    }

    private fun chooseIntPref(
        title: String,
        key: String,
        defaultValue: Int,
        options: List<Pair<String, Int>>,
        negativeLabel: String = "Cancel",
        refresh: Boolean = true,
    ) {
        showSingleChoiceDialog(title, options, preferences.getInt(key, defaultValue), negativeLabel) { chosen ->
            preferences.edit { putInt(key, chosen) }
            if (refresh) renderPage()
        }
    }

    private fun <T> showSingleChoiceDialog(
        title: String,
        options: List<Pair<String, T>>,
        selected: T,
        negativeLabel: String = "Cancel",
        onSelect: (T) -> Unit,
    ) {
        val selectedIndex = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.map { it.first }.toTypedArray(), selectedIndex) { dialog, which ->
                dialog.dismiss()
                onSelect(options[which].second)
            }
            .setNegativeButton(negativeLabel, null)
            .show()
    }

    private fun openHiddenApps() {
        catalog.loadAsync { apps ->
            if (isFinishing || isDestroyed) return@loadAsync
            if (apps.isEmpty()) {
                toast("Installed apps are still loading. Try again in a moment.")
                return@loadAsync
            }
            val sorted = apps.sortedBy { it.entry.label.lowercase(Locale.getDefault()) }
            val hidden = preferences.getStringSet(LauncherSettings.KEY_HIDDEN_APPS, emptySet()).orEmpty().toMutableSet()
            val checked = BooleanArray(sorted.size) { sorted[it].entry.componentKey in hidden }
            AlertDialog.Builder(this)
                .setTitle("Hidden apps")
                .setMultiChoiceItems(sorted.map { it.entry.label }.toTypedArray(), checked) { _, index, selected ->
                    val key = sorted[index].entry.componentKey
                    if (selected) hidden.add(key) else hidden.remove(key)
                }
                .setPositiveButton("Save") { _, _ ->
                    preferences.edit { putStringSet(LauncherSettings.KEY_HIDDEN_APPS, hidden.toSet()) }
                    renderPage()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openNotificationAccess() {
        preferences.edit { putBoolean(LauncherSettings.KEY_HIGHLIGHT_NOTIFICATIONS, true) }
        if (notificationAccessGranted()) {
            toast("Notification highlights are enabled")
            return
        }
        runSystemIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), "Notification access settings are unavailable")
    }

    private fun notificationAccessGranted(): Boolean = runCatching {
        getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, NotificationHighlightService::class.java))
    }.getOrDefault(false)

    private fun requestHomeRole() {
        val request = roleManager.requestIntent()
        if (request == null) toast(if (roleManager.isDefaultHome()) "Already the default home app" else "Home app settings are unavailable")
        else roleRequest.launch(request)
    }

    private fun shareWorkspace() {
        val layout = workspace.load()
        val pages = preferences.workspacePageCount(layout)
        val text = "Teres Launcher home screen\n$pages page${if (pages == 1) "" else "s"}, ${layout.items.size} shortcut${if (layout.items.size == 1) "" else "s"}, ${layout.widgetIds.size} widget${if (layout.widgetIds.size == 1) "" else "s"}."
        runSystemIntent(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your home screen"), "Sharing is unavailable")
    }

    private fun showAbout() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0.0"
        AlertDialog.Builder(this).setTitle("Haven")
            .setMessage("Version $version\nApplication ID: $packageName\n\nAn independent Android home screen.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun exportPayload(): String {
        val currentLayout = workspace.load()
        val exportedLayout = currentLayout.copy(
            widgetIds = emptyList(),
            widgetSizes = emptyMap(),
            widgetCells = emptyMap(),
            widgetGridColumns = homeGridColumns(),
            widgetStacks = emptyList(),
        )
        val settings = JSONObject()
            .put(LauncherSettings.KEY_GRID_COLUMNS, homeGridColumns())
            .put(LauncherSettings.KEY_GRID_ROWS, WorkspaceGridSpec.ROWS)
            .put(LauncherSettings.KEY_HOME_GRID_LAYOUT_VERSION, LauncherSettings.HOME_GRID_LAYOUT_VERSION)
            .put(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, preferences.workspacePageCount(currentLayout))
            .put("hidden_apps", JSONArray(preferences.getStringSet(LauncherSettings.KEY_HIDDEN_APPS, emptySet()).orEmpty().toList()))
        BACKUP_BOOLEANS.forEach { (key, default) -> settings.put(key, preferences.getBoolean(key, default)) }
        BACKUP_STRINGS.forEach { spec -> settings.put(spec.key, preferences.getString(spec.key, spec.default)) }
        BACKUP_INTS.forEach { spec ->
            val raw = preferences.getInt(spec.key, spec.default)
            settings.put(spec.key, if (spec.clampOnExport && spec.range != null) raw.coerceIn(spec.range) else raw)
        }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("workspace", JSONObject(workspace.encode(exportedLayout)))
            .put("settings", settings)
            .toString(2)
    }

    private fun writeBackup(uri: Uri) {
        val backup = runCatching { exportPayload() }.getOrElse {
            toast("Could not prepare the backup")
            return
        }
        fileWorker.execute {
            val result = runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup) } ?: error("No output stream") }
            runOnUiThread { toast(if (result.isSuccess) "Backup saved" else "Could not save backup") }
        }
    }

    private fun readBackup(uri: Uri) {
        fileWorker.execute {
            val result = runCatching {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("No input stream")
                val root = JSONObject(raw)
                require(root.optInt("schemaVersion") == 1) { "Unsupported backup version" }
                val settings = root.getJSONObject("settings")
                val restoredLayout = workspace.decode(root.getJSONObject("workspace").toString())
                val restoredColumns = WorkspaceGridSpec.columns(settings.optInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.COLUMNS))
                val legacyGridChanged = settings.optInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.COLUMNS) != restoredColumns ||
                    settings.optInt(LauncherSettings.KEY_GRID_ROWS, WorkspaceGridSpec.ROWS) != WorkspaceGridSpec.ROWS
                val normalizedLayout = if (legacyGridChanged || restoredLayout.items.any { it.cell !in 0 until restoredColumns * WorkspaceGridSpec.ROWS }) {
                    WorkspacePlacement.reflow(restoredLayout, restoredColumns, WorkspaceGridSpec.ROWS)
                } else restoredLayout
                normalizedLayout to settings
            }
            runOnUiThread {
                result.onSuccess { (layout, settings) ->
                    val currentLayout = workspace.load()
                    workspace.save(layout.copy(
                        widgetIds = currentLayout.widgetIds,
                        widgetSizes = currentLayout.widgetSizes,
                        widgetCells = currentLayout.widgetCells,
                        widgetGridColumns = currentLayout.widgetGridColumns,
                        widgetStacks = currentLayout.widgetStacks,
                    ))
                    restoreSettings(settings)
                    toast("Backup restored. Existing widgets were kept.")
                }.onFailure { toast("This backup could not be restored") }
            }
        }
    }

    private fun restoreSettings(settings: JSONObject) {
        val hidden = settings.optJSONArray("hidden_apps").toStringSet()
        preferences.edit {
            putInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.columns(settings.optInt(LauncherSettings.KEY_GRID_COLUMNS, WorkspaceGridSpec.COLUMNS)))
            putInt(LauncherSettings.KEY_GRID_ROWS, WorkspaceGridSpec.ROWS)
            putInt(LauncherSettings.KEY_HOME_GRID_LAYOUT_VERSION, settings.optInt(LauncherSettings.KEY_HOME_GRID_LAYOUT_VERSION, 1).coerceIn(1, LauncherSettings.HOME_GRID_LAYOUT_VERSION))
            putInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, settings.optInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, 1).coerceAtLeast(1))
            BACKUP_BOOLEANS.forEach { (key, default) -> putBoolean(key, settings.optBoolean(key, default)) }
            BACKUP_STRINGS.forEach { spec ->
                val value = settings.optString(spec.key, spec.default)
                putString(spec.key, spec.allowed?.let { value.takeIf { v -> v in it } ?: spec.default } ?: value)
            }
            BACKUP_INTS.forEach { spec ->
                val value = settings.optInt(spec.key, spec.default)
                putInt(spec.key, spec.range?.let { value.coerceIn(it) } ?: value)
            }
            putStringSet(LauncherSettings.KEY_HIDDEN_APPS, hidden)
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val array = this@toStringSet ?: return@buildSet
        for (i in 0 until array.length()) array.optString(i).takeIf(String::isNotBlank)?.let(::add)
    }

    private fun runSystemIntent(intent: Intent, failure: String) {
        runCatching { startActivity(intent) }.onFailure { toast(failure) }
    }

    private fun currentAccent() = LauncherAppearance.resolve(this).accent
    private fun navigate(target: String) { pageHistory += page; page = target; renderPage() }

    companion object {
        const val EXTRA_PAGE = "settings_page"
        private const val MINT = 0xFF73DCA8.toInt()
        private const val PEACH = 0xFFFFB879.toInt()
        private const val PINK = 0xFFE88FA4.toInt()
        private const val CYAN = 0xFF79DCE0.toInt()
        private const val LAVENDER = 0xFFAE91FF.toInt()
        private const val BLUE = 0xFF74BEFF.toInt()
        private const val MUTED = 0xFF9A9A9A.toInt()
        private const val INK_PRIMARY = 0xFF171717.toInt()
        private const val INK_MUTED_LIGHT = 0xFF777B82.toInt()

        private val RESUME_REFRESH_PAGES = setOf("root", "gestures", "isolation")
        private val LARGE_HEADER_PAGES = setOf("wallpaper", "gestures", "hot_keys", "gesture_single", "gesture_double", "smart_display")
        private val REORDER_PAGE_IDS = setOf("home", "app_page", "vault", "search")
        private val HTTP_SCHEMES = setOf("http", "https")
        private val ACCESSIBILITY_REQUIRED_ICON_ACTIONS = setOf(
            WorkspaceIconGestureAction.LOCK_SCREEN,
            WorkspaceIconGestureAction.NOTIFICATIONS,
            WorkspaceIconGestureAction.RECENTS,
            WorkspaceIconGestureAction.QUICK_SETTINGS,
        )
        private val SINGLE_FINGER_GESTURES = listOf(
            WorkspaceSurfaceGesture.SWIPE_LEFT,
            WorkspaceSurfaceGesture.SWIPE_RIGHT,
            WorkspaceSurfaceGesture.SWIPE_UP,
            WorkspaceSurfaceGesture.SWIPE_DOWN,
        )
        private val DOUBLE_FINGER_GESTURES = listOf(
            WorkspaceSurfaceGesture.DUAL_SWIPE_LEFT,
            WorkspaceSurfaceGesture.DUAL_SWIPE_RIGHT,
            WorkspaceSurfaceGesture.DUAL_SWIPE_UP,
            WorkspaceSurfaceGesture.DUAL_SWIPE_DOWN,
            WorkspaceSurfaceGesture.PINCH_IN,
            WorkspaceSurfaceGesture.PINCH_OUT,
            WorkspaceSurfaceGesture.ROTATE_RIGHT,
            WorkspaceSurfaceGesture.ROTATE_LEFT,
        )
        private val PAGE_TITLES = mapOf(
            "appearance" to "Global appearance",
            "wallpaper" to "Wallpaper",
            "icon_appearance" to "Icon appearance",
            "folder_icons" to "Folder icons",
            "gestures" to "Gestures and hot keys",
            "hot_keys" to "Hot keys",
            "gesture_single" to "Single finger",
            "gesture_double" to "Double finger",
            "smart_display" to "Smart Display Off",
            "backup" to "Backup",
            "home" to "Home screen",
            "home_grid" to "Grid properties",
            "home_dock" to "Dock",
            "app_page" to "App page",
            "vault" to "Haven Vault",
            "isolation" to "Work Profile & Isolation",
            "search" to "Haven Search",
            "pages" to "Page manager",
            "privacy" to "Security and privacy",
        )
        private val THEME_OPTIONS = listOf("Ambient theme" to LauncherAppearance.MODE_SYSTEM, "Light theme" to LauncherAppearance.MODE_LIGHT, "Dark theme" to LauncherAppearance.MODE_DARK)
        private val ACCENT_OPTIONS = listOf(
            "Wallpaper" to LauncherAppearance.ACCENT_WALLPAPER,
            "Teal" to LauncherAppearance.ACCENT_TEAL,
            "Blue" to LauncherAppearance.ACCENT_BLUE,
            "Violet" to LauncherAppearance.ACCENT_VIOLET,
            "Coral" to LauncherAppearance.ACCENT_CORAL,
        )
        private val FOLDER_STYLES = listOf("Contained" to "contained", "Clipped" to "clipped", "Unclipped" to "unclipped")
        private val FOLDER_ICON_SIZE_OPTIONS = listOf("Compact preview" to 18, "Default preview" to 22, "Large preview" to 26)
        private val CLOCK_SIZE_OPTIONS = listOf("Compact" to 48, "Default" to 66, "Large" to 76)
        private val CLOCK_COLOR_OPTIONS = listOf("White" to Color.WHITE, "Soft white" to 0xFFE8E5DF.toInt(), "Teres teal" to 0xFF78D5CA.toInt(), "Sky blue" to 0xFF9EC7FF.toInt())
        private val SEARCH_PROVIDERS = listOf("Google" to "google", "DuckDuckGo" to "duckduckgo", "Bing" to "bing")
        private val ISOLATION_GESTURE_ACTIONS = setOf(
            LauncherSettings.GESTURE_ACTION_OPEN_VAULT,
            LauncherSettings.GESTURE_ACTION_FREEZE_ALL_WORK,
            LauncherSettings.GESTURE_ACTION_TOGGLE_WORK_PROFILE,
        )
        private val DOUBLE_TAP_OPTIONS = listOf(
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_NONE, "None", "GENERAL", "close", "Do nothing."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_LOCK_SCREEN, "Turn off the screen", "SYSTEM", "security", "Lock the device. Requires Teres system actions access.", true),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_NOTIFICATIONS, "Show notifications panel", "SYSTEM", "notifications", "Open the Android notification shade.", true),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_RECENTS, "Show recent apps", "SYSTEM", "apps", "Show Android's recent apps overview.", true),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_QUICK_SETTINGS, "Show toggle panel", "SYSTEM", "tune", "Open Android Quick Settings.", true),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_DRAWER, "App page", "NAVIGATION", "apps", "Open All apps."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_TOGGLE_DOCK, "Show or hide dock", "NAVIGATION", "home", "Toggle the Home app dock."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_SEARCH, "Search page", "NAVIGATION", "search", "Open Teres Search."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_ASSIST, "Google Now/Discovery", "NAVIGATION", "search", "Open the Android digital assistant, if available."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_SETTINGS, "Preferences", "NAVIGATION", "appearance", "Open Haven settings."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_OPEN_VAULT, "Haven Vault", "NAVIGATION", "security", "Open the Haven Vault drawer page."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_FREEZE_ALL_WORK, "Freeze isolated apps", "SYSTEM", "lock", "Batch freeze all apps on the Auto-Freeze list."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_TOGGLE_WORK_PROFILE, "Pause / resume work profile", "SYSTEM", "lock", "Toggle Android Quiet Mode for the work profile."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_APP_INDEX, "Search apps by index", "NAVIGATION", "search", "Open All apps with its search field ready."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_WEB_PAGE, "Open web page", "NAVIGATION", "share", "Open a saved address in the browser."),
            GesturePickerOption(LauncherSettings.GESTURE_ACTION_GO_TO_PAGE, "Go to page", "NAVIGATION", "pages", "Open a selected Home screen."),
        )
        private val DOUBLE_TAP_LABELS = mapOf(
            LauncherSettings.GESTURE_ACTION_SEARCH to "Search page",
            LauncherSettings.GESTURE_ACTION_DRAWER to "App page",
            LauncherSettings.GESTURE_ACTION_QUICK_SETTINGS to "Show toggle panel",
            LauncherSettings.GESTURE_ACTION_LOCK_SCREEN to "Turn off the screen",
            LauncherSettings.GESTURE_ACTION_NOTIFICATIONS to "Show notifications panel",
            LauncherSettings.GESTURE_ACTION_RECENTS to "Show recent apps",
            LauncherSettings.GESTURE_ACTION_ASSIST to "Google Now/Discovery",
            LauncherSettings.GESTURE_ACTION_TOGGLE_DOCK to "Show or hide dock",
            LauncherSettings.GESTURE_ACTION_SETTINGS to "Preferences",
            LauncherSettings.GESTURE_ACTION_OPEN_VAULT to "Haven Vault",
            LauncherSettings.GESTURE_ACTION_FREEZE_ALL_WORK to "Freeze isolated apps",
            LauncherSettings.GESTURE_ACTION_TOGGLE_WORK_PROFILE to "Pause / resume work profile",
            LauncherSettings.GESTURE_ACTION_APP_INDEX to "Search apps by index",
            LauncherSettings.GESTURE_ACTION_WEB_PAGE to "Open web page",
        )
        private val SORT_MODES = DrawerSortMode.entries.map { it.preferenceValue }.toSet()
        private val ANIMATIONS = setOf("slide", "fade", "zoom", "none")
        private val FONT_FAMILIES = setOf("poppins", "system", "sans", "serif", "mono")
        private val FOLDER_STYLE_KEYS = setOf("contained", "clipped", "unclipped")
        private val ICON_SHAPE_KEYS = IconShapes.options.map { it.key }.toSet()
        private val BATTERY_ALIGNMENTS = setOf("start", "center", "end")
        private val DOUBLE_TAP_ALLOWED = DOUBLE_TAP_LABELS.keys + setOf(
            LauncherSettings.GESTURE_ACTION_NONE,
            LauncherSettings.GESTURE_ACTION_GO_TO_PAGE,
            LauncherSettings.GESTURE_ACTION_APP,
            LauncherSettings.GESTURE_ACTION_SHORTCUT,
        )
        private val BACKUP_BOOLEANS = listOf(
            LauncherSettings.KEY_DRAWER_SHOW_LABELS to true,
            LauncherSettings.KEY_DRAWER_TWO_LINE_LABELS to true,
            LauncherSettings.KEY_SHOW_CATEGORIES to false,
            LauncherSettings.KEY_DRAWER_SHOW_VAULT_SWITCHER to false,
            LauncherSettings.KEY_DIM_WALLPAPER to false,
            LauncherSettings.KEY_WALLPAPER_SCROLLABLE to true,
            LauncherSettings.KEY_WALLPAPER_PARALLAX to false,
            LauncherSettings.KEY_DARKEN_EXTRA_HOME_PAGES to false,
            LauncherSettings.KEY_REVERSE_HOME_PAGE_ORDER to false,
            LauncherSettings.KEY_BOTTOM_BUTTONS to false,
            LauncherSettings.KEY_DRAWER_BLUR to true,
            LauncherSettings.KEY_FOLDER_BACKGROUND to true,
            LauncherSettings.KEY_FOLDER_SHOW_LABELS to true,
            LauncherSettings.KEY_ICON_ADAPTIVE_HOME to true,
            LauncherSettings.KEY_ICON_ADAPTIVE_DRAWER to true,
            LauncherSettings.KEY_ICON_DOUBLE_TAP_BADGES to false,
            LauncherSettings.KEY_DOCK_VISIBLE to true,
            LauncherSettings.KEY_HOME_PAGE_INDICATOR to true,
            LauncherSettings.KEY_AUTO_ADD_NEW_APPS to false,
            LauncherSettings.KEY_HOME_SHOW_LABELS to false,
            LauncherSettings.KEY_SEARCH_SHOW_HIDDEN_APPS to false,
            LauncherSettings.KEY_SEARCH_CONTACTS to false,
            LauncherSettings.KEY_SEARCH_MEDIA to false,
            LauncherSettings.KEY_SEARCH_CALENDAR to false,
            LauncherSettings.KEY_SEARCH_SHORTCUTS to true,
            LauncherSettings.KEY_SEARCH_WEB to true,
            LauncherSettings.KEY_SEARCH_PLAY_STORE to true,
            LauncherSettings.KEY_CLOCK_VISIBLE to false,
            LauncherSettings.KEY_CLOCK_SHOW_TIME to true,
            LauncherSettings.KEY_CLOCK_SHOW_DATE to true,
            LauncherSettings.KEY_CLOCK_SHOW_WEATHER to true,
            LauncherSettings.KEY_CLOCK_BACKGROUND to false,
            LauncherSettings.KEY_BATTERY_VISIBLE to false,
            LauncherSettings.KEY_BATTERY_SHOW_LABEL to false,
            LauncherSettings.KEY_BATTERY_BACKGROUND to true,
        )
        private val BACKUP_STRINGS = listOf(
            StringBackupSpec(LauncherSettings.KEY_DRAWER_SORT_ORDER, DrawerSortMode.NAME.preferenceValue, SORT_MODES),
            StringBackupSpec(LauncherSettings.KEY_DRAWER_USER_ORDER, "[]"),
            StringBackupSpec(LauncherSettings.KEY_DRAWER_USAGE_COUNTS, "{}"),
            StringBackupSpec(LauncherSettings.KEY_HOME_ANIMATION, "slide", ANIMATIONS),
            StringBackupSpec(LauncherSettings.KEY_FONT_FAMILY, LauncherTypography.DEFAULT_FAMILY, FONT_FAMILIES),
            StringBackupSpec(LauncherSettings.KEY_FOLDER_PREVIEW_STYLE, "contained", FOLDER_STYLE_KEYS),
            StringBackupSpec(LauncherSettings.KEY_ICON_SHAPE_HOME, "circle", ICON_SHAPE_KEYS),
            StringBackupSpec(LauncherSettings.KEY_ICON_SHAPE_DRAWER, "circle", ICON_SHAPE_KEYS),
            StringBackupSpec(LauncherSettings.KEY_ICON_PACK_HOME, "default"),
            StringBackupSpec(LauncherSettings.KEY_ICON_PACK_DRAWER, "default"),
            StringBackupSpec(LauncherSettings.KEY_THEME_MODE, LauncherAppearance.MODE_SYSTEM),
            StringBackupSpec(LauncherSettings.KEY_THEME_ACCENT, LauncherAppearance.ACCENT_WALLPAPER),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP, LauncherSettings.GESTURE_ACTION_NONE, DOUBLE_TAP_ALLOWED),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PACKAGE, ""),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_CLASS, ""),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_LABEL, ""),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_SHORTCUT_ID, ""),
            StringBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_URL, ""),
            StringBackupSpec(LauncherSettings.KEY_SEARCH_PROVIDER, "google"),
            StringBackupSpec(LauncherSettings.KEY_BATTERY_ALIGNMENT, "start", BATTERY_ALIGNMENTS),
        )
        private val BACKUP_INTS = listOf(
            IntBackupSpec(LauncherSettings.KEY_HOME_COLUMN_SPACING, LauncherSettings.HOME_COLUMN_SPACING_DEFAULT_DP, 0..32, clampOnExport = true),
            IntBackupSpec(LauncherSettings.KEY_DRAWER_COLUMNS, 4, 4..6, clampOnExport = true),
            IntBackupSpec(LauncherSettings.KEY_DRAWER_ICON_SIZE, 68, 40..72, clampOnExport = true),
            IntBackupSpec(LauncherSettings.KEY_DRAWER_ROW_SPACING, -1, -1..24),
            IntBackupSpec(LauncherSettings.KEY_FOLDER_PREVIEW_ICON_SIZE, LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_DEFAULT_DP, LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_MIN_DP..LauncherSettings.FOLDER_PREVIEW_ICON_SIZE_MAX_DP),
            IntBackupSpec(LauncherSettings.KEY_GESTURE_DOUBLE_TAP_PAGE, 0, 0 until LauncherSettings.MAX_WORKSPACE_PAGES),
            IntBackupSpec(LauncherSettings.KEY_HOME_ICON_SIZE, LauncherSettings.HOME_ICON_SIZE_DEFAULT_DP, LauncherSettings.HOME_ICON_SIZE_MIN_DP..LauncherSettings.HOME_ICON_SIZE_MAX_DP, clampOnExport = true),
            IntBackupSpec(LauncherSettings.KEY_SEARCH_START_CONTACTS_COUNT, 2, 0..8),
            IntBackupSpec(LauncherSettings.KEY_SEARCH_START_APPS_COUNT, 4, 0..12),
            IntBackupSpec(LauncherSettings.KEY_CLOCK_TEXT_SIZE, 66, 42..76),
            IntBackupSpec(LauncherSettings.KEY_CLOCK_TEXT_COLOR, Color.WHITE),
            IntBackupSpec(LauncherSettings.KEY_CLOCK_VERTICAL_OFFSET, 0, -60..60),
        )
    }
}
