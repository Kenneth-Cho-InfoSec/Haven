package com.arcturus.haven.launcher.core.settings

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.arcturus.haven.launcher.core.model.WorkspaceLayout

object LauncherSettings {
    const val PREFERENCES = "ui_settings"
    const val KEY_EXPERIENCE_MODE = "haven_experience_mode"
    const val KEY_WELCOME_COMPLETED = "haven_welcome_completed"
    const val KEY_DIM_WALLPAPER = "dim_wallpaper"
    const val KEY_WALLPAPER_SCROLLABLE = "wallpaper_scrollable"
    const val KEY_WALLPAPER_PARALLAX = "wallpaper_parallax"
    const val KEY_DARKEN_EXTRA_HOME_PAGES = "darken_extra_home_pages"
    const val KEY_REVERSE_HOME_PAGE_ORDER = "reverse_home_page_order"
    const val KEY_BOTTOM_BUTTONS = "bottom_buttons"
    const val KEY_FEED_LAYOUT = "feed_layout"
    const val KEY_FEED_LANGUAGE = "feed_language"
    const val KEY_GRID_COLUMNS = "grid_columns"
    const val KEY_GRID_ROWS = "grid_rows"
    const val KEY_HOME_COLUMN_SPACING = "home_column_spacing"
    const val HOME_COLUMN_SPACING_DEFAULT_DP = 8
    const val KEY_HOME_SHOW_LABELS = "home_show_labels"
    const val KEY_HOME_ICON_SIZE = "home_icon_size"
    const val HOME_ICON_SIZE_DEFAULT_DP = 68
    const val KEY_HOME_ICON_SIZE_MIGRATION_VERSION = "home_icon_size_migration_version"
    const val HOME_ICON_SIZE_MIGRATION_VERSION = 1
    const val HOME_ICON_SIZE_LEGACY_DEFAULT_DP = 66
    const val HOME_ICON_SIZE_MIN_DP = 48
    const val HOME_ICON_SIZE_MAX_DP = 88
    const val KEY_HOME_PAGE_INDICATOR = "home_page_indicator"
    const val KEY_AUTO_ADD_NEW_APPS = "auto_add_new_apps"
    const val KEY_KNOWN_LAUNCHER_APP_PACKAGES = "known_launcher_app_packages"
    const val KEY_HOME_GRID_LAYOUT_VERSION = "home_grid_layout_version"
    const val HOME_GRID_LAYOUT_VERSION = 2
    const val KEY_WEATHER_ENABLED = "weather_enabled"
    const val KEY_CLOCK_VISIBLE = "clock_visible"
    const val KEY_CLOCK_SHOW_TIME = "clock_show_time"
    const val KEY_CLOCK_SHOW_DATE = "clock_show_date"
    const val KEY_CLOCK_SHOW_WEATHER = "clock_show_weather"
    const val KEY_CLOCK_TEXT_SIZE = "clock_text_size"
    const val KEY_CLOCK_TEXT_COLOR = "clock_text_color"
    const val KEY_CLOCK_BACKGROUND = "clock_background"
    const val KEY_CLOCK_VERTICAL_OFFSET = "clock_vertical_offset"
    const val KEY_BATTERY_VISIBLE = "battery_visible"
    const val KEY_BATTERY_SHOW_LABEL = "battery_show_label"
    const val KEY_BATTERY_BACKGROUND = "battery_background"
    const val KEY_BATTERY_ALIGNMENT = "battery_alignment"
    const val KEY_SHOW_CATEGORIES = "drawer_show_categories"
    const val KEY_HIDDEN_APPS = "hidden_app_components"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_THEME_ACCENT = "theme_accent"
    const val KEY_DRAWER_COLUMNS = "drawer_columns"
    const val KEY_DRAWER_ICON_SIZE = "drawer_icon_size"
    const val KEY_DRAWER_ROW_SPACING = "drawer_row_spacing"
    const val KEY_DRAWER_SHOW_LABELS = "drawer_show_labels"
    const val KEY_DRAWER_TWO_LINE_LABELS = "drawer_two_line_labels"
    const val KEY_DRAWER_BLUR = "drawer_blur"
    const val KEY_DRAWER_LAUNCH_VIEW = "drawer_launch_view"
    const val KEY_DRAWER_SEARCH_BY_INDEX = "drawer_search_by_index"
    const val KEY_DRAWER_HIDE_PLAY_STORE = "drawer_hide_play_store"
    const val KEY_DRAWER_STACK_FROM_BOTTOM = "drawer_stack_from_bottom"
    const val KEY_DRAWER_CLOSE_AFTER_LAUNCH = "drawer_close_after_launch"
    const val KEY_DRAWER_KEEP_LAST_POSITION = "drawer_keep_last_position"
    const val KEY_DRAWER_LAST_SCROLL_POSITION = "drawer_last_scroll_position"
    const val KEY_DRAWER_SORT_ORDER = "drawer_sort_order"
    const val KEY_DRAWER_USER_ORDER = "drawer_user_order"
    const val KEY_DRAWER_USAGE_COUNTS = "drawer_usage_counts"
    const val KEY_HOME_ANIMATION = "home_animation"
    const val KEY_FONT_FAMILY = "font_family"
    const val KEY_FOLDER_PREVIEW_ICON_SIZE = "folder_preview_icon_size"
    const val FOLDER_PREVIEW_ICON_SIZE_DEFAULT_DP = 26
    const val FOLDER_PREVIEW_ICON_SIZE_MIN_DP = 18
    const val FOLDER_PREVIEW_ICON_SIZE_MAX_DP = 26
    const val KEY_FOLDER_PREVIEW_STYLE = "folder_preview_style"
    const val KEY_FOLDER_BACKGROUND = "folder_background"
    const val KEY_FOLDER_SHOW_LABELS = "folder_show_labels"
    const val KEY_ICON_APPEARANCE_SCOPE = "icon_appearance_scope"
    const val KEY_ICON_SHAPE_HOME = "icon_shape_home"
    const val KEY_ICON_SHAPE_DRAWER = "icon_shape_drawer"
    const val KEY_ICON_ADAPTIVE_HOME = "icon_adaptive_home"
    const val KEY_ICON_ADAPTIVE_DRAWER = "icon_adaptive_drawer"
    const val KEY_ICON_DOUBLE_TAP_BADGES = "icon_double_tap_badges"
    const val KEY_ICON_PACK_HOME = "icon_pack_home"
    const val KEY_ICON_PACK_DRAWER = "icon_pack_drawer"
    const val KEY_HIGHLIGHT_NOTIFICATIONS = "highlight_notifications"
    const val KEY_NOTIFICATION_PACKAGES = "notification_packages"
    const val KEY_WORKSPACE_PAGE_COUNT = "workspace_page_count"
    const val KEY_OPEN_PAGE_INDEX = "open_page_index"
    const val KEY_SETTINGS_PAGE_ORDER = "settings_page_order"
    const val KEY_WORKSPACE_LOCKED = "workspace_locked"
    const val KEY_GESTURE_SWIPE_UP = "gesture_swipe_up"
    const val KEY_GESTURE_SWIPE_DOWN = "gesture_swipe_down"
    const val KEY_GESTURE_PAGE_SWIPE = "gesture_page_swipe"
    const val KEY_WORKSPACE_GESTURES_ENABLED = "workspace_gestures_enabled"
    const val KEY_WORKSPACE_GESTURE_VIBRATION = "workspace_gesture_vibration"
    const val KEY_WORKSPACE_GESTURE_ACTION_PREFIX = "workspace_gesture_action_"
    const val KEY_WORKSPACE_GESTURE_PACKAGE_PREFIX = "workspace_gesture_package_"
    const val KEY_WORKSPACE_GESTURE_CLASS_PREFIX = "workspace_gesture_class_"
    const val KEY_WORKSPACE_GESTURE_LABEL_PREFIX = "workspace_gesture_label_"
    const val KEY_WORKSPACE_GESTURE_SHORTCUT_ID_PREFIX = "workspace_gesture_shortcut_id_"
    const val KEY_DOCK_VISIBLE = "dock_visible"
    const val KEY_DOCK_APP_PACKAGES = "dock_app_packages"
    const val KEY_SEARCH_VISIBLE_HOME = "search_visible_home"
    const val KEY_HOME_HIDE_STATUS_BAR = "home_hide_status_bar"
    const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap"
    const val KEY_GESTURE_DOUBLE_TAP_PACKAGE = "gesture_double_tap_package"
    const val KEY_GESTURE_DOUBLE_TAP_CLASS = "gesture_double_tap_class"
    const val KEY_GESTURE_DOUBLE_TAP_LABEL = "gesture_double_tap_label"
    const val KEY_GESTURE_DOUBLE_TAP_SHORTCUT_ID = "gesture_double_tap_shortcut_id"
    const val KEY_GESTURE_DOUBLE_TAP_PAGE = "gesture_double_tap_page"
    const val KEY_GESTURE_DOUBLE_TAP_URL = "gesture_double_tap_url"
    const val KEY_SEARCH_SHOW_HIDDEN_APPS = "search_show_hidden_apps"
    const val KEY_SEARCH_CONTACTS = "search_contacts"
    const val KEY_SEARCH_MEDIA = "search_media"
    const val KEY_SEARCH_CALENDAR = "search_calendar"
    const val KEY_SEARCH_SHORTCUTS = "search_shortcuts"
    const val KEY_SEARCH_WEB = "search_web_suggestions"
    const val KEY_SEARCH_PLAY_STORE = "search_play_store"
    const val KEY_SEARCH_PROVIDER = "search_provider"
    const val KEY_SEARCH_START_CONTACTS_COUNT = "search_start_contacts_count"
    const val KEY_SEARCH_START_APPS_COUNT = "search_start_apps_count"
    const val KEY_SEARCH_START_APP_HISTORY = "search_start_app_history"
    const val KEY_DRAWER_SHOW_VAULT_SWITCHER = "drawer_show_vault_switcher"
    const val KEY_VAULT_SHOW_FROZEN_APPS = "vault_show_frozen_apps"
    const val KEY_SEARCH_VAULT_APPS = "search_vault_apps"
    const val GESTURE_ACTION_NONE = "none"
    const val GESTURE_ACTION_SEARCH = "search"
    const val GESTURE_ACTION_DRAWER = "drawer"
    const val GESTURE_ACTION_QUICK_SETTINGS = "quick_settings"
    const val GESTURE_ACTION_LOCK_SCREEN = "lock_screen"
    const val GESTURE_ACTION_NOTIFICATIONS = "notifications"
    const val GESTURE_ACTION_RECENTS = "recents"
    const val GESTURE_ACTION_ASSIST = "assist"
    const val GESTURE_ACTION_TOGGLE_DOCK = "toggle_dock"
    const val GESTURE_ACTION_SETTINGS = "settings"
    const val GESTURE_ACTION_APP_INDEX = "app_index"
    const val GESTURE_ACTION_GO_TO_PAGE = "go_to_page"
    const val GESTURE_ACTION_WEB_PAGE = "web_page"
    const val GESTURE_ACTION_APP = "app"
    const val GESTURE_ACTION_SHORTCUT = "shortcut"
    const val GESTURE_ACTION_OPEN_VAULT = "open_vault"
    const val GESTURE_ACTION_FREEZE_ALL_WORK = "freeze_all_work"
    const val GESTURE_ACTION_TOGGLE_WORK_PROFILE = "toggle_work_profile"
    const val MAX_WORKSPACE_PAGES = 20
}

internal fun Context.launcherPrefs(): SharedPreferences =
    getSharedPreferences(LauncherSettings.PREFERENCES, Context.MODE_PRIVATE)

internal fun SharedPreferences.drawerColumns(): Int =
    getInt(LauncherSettings.KEY_DRAWER_COLUMNS, 4).coerceIn(4, 6)

internal fun SharedPreferences.drawerIconSize(): Int =
    getInt(LauncherSettings.KEY_DRAWER_ICON_SIZE, 68).coerceIn(40, 72)

internal fun SharedPreferences.drawerCellHeightDp(categorized: Boolean, labelsVisible: Boolean = getBoolean(LauncherSettings.KEY_DRAWER_SHOW_LABELS, true)): Int {
    if (!labelsVisible) return (drawerIconSize() + 8).coerceAtLeast(48)
    return maxOf(if (categorized) 100 else 112, drawerIconSize() + if (categorized) 32 else 44)
}

internal fun SharedPreferences.drawerRowSpacingDp(
    categorized: Boolean = getBoolean(LauncherSettings.KEY_SHOW_CATEGORIES, false),
): Int {
    val configured = getInt(LauncherSettings.KEY_DRAWER_ROW_SPACING, -1)
    if (configured >= 0) return configured.coerceIn(0, 24)
    val labelsVisible = getBoolean(LauncherSettings.KEY_DRAWER_SHOW_LABELS, true)
    if (!labelsVisible) return 4
    return ((if (categorized) 108 else 126) - drawerCellHeightDp(categorized, labelsVisible = true)).coerceAtLeast(0)
}

internal fun SharedPreferences.workspacePageCount(layout: WorkspaceLayout): Int = maxOf(
    1,
    getInt(LauncherSettings.KEY_WORKSPACE_PAGE_COUNT, 1),
    (layout.items.maxOfOrNull { it.page } ?: 0) + 1,
).coerceAtMost(LauncherSettings.MAX_WORKSPACE_PAGES)

internal fun mediaSearchPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun Context.hasMediaSearchPermission(): Boolean = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    else -> ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
}
