package com.arcturus.haven.launcher.core.model

enum class WorkspaceGestureActionGroup {
    SUGGESTED,
    SYSTEM,
    NAVIGATION,
    APPS,
    SHORTCUTS,
}

/** Actions assignable independently to each workspace icon gesture. */
enum class WorkspaceIconGestureAction(
    val storageKey: String,
    val label: String,
    val menuDescription: String,
    val group: WorkspaceGestureActionGroup,
    internal val gestureActionText: String,
) {
    NONE("none", "None", "nothing", WorkspaceGestureActionGroup.SUGGESTED, "do nothing"),
    OPEN_APP("open_app", "Open this app", "this app", WorkspaceGestureActionGroup.SUGGESTED, "open app"),
    POPUP_WIDGET("popup_widget", "Popup widget", "a popup widget", WorkspaceGestureActionGroup.SUGGESTED, "show a popup widget"),
    APP_DRAWER("app_drawer", "App page", "the app drawer", WorkspaceGestureActionGroup.NAVIGATION, "open app drawer"),
    TOGGLE_DOCK("toggle_dock", "Show or hide dock", "the dock", WorkspaceGestureActionGroup.NAVIGATION, "show or hide dock"),
    SEARCH("search", "Search page", "Haven Search", WorkspaceGestureActionGroup.NAVIGATION, "open Haven Search"),
    ASSISTANT("assistant", "Google Now/Discovery", "the assistant", WorkspaceGestureActionGroup.NAVIGATION, "open assistant"),
    SETTINGS("settings", "Preferences", "launcher settings", WorkspaceGestureActionGroup.NAVIGATION, "open launcher settings"),
    OPEN_VAULT("open_vault", "Haven Vault", "Haven Vault", WorkspaceGestureActionGroup.NAVIGATION, "open Haven Vault"),
    APP_INDEX("app_index", "Search apps by index", "the app index", WorkspaceGestureActionGroup.NAVIGATION, "search apps by index"),
    NEXT_HOME_PAGE("next_home_page", "Next Home screen", "the next Home screen", WorkspaceGestureActionGroup.NAVIGATION, "go to the next Home screen"),
    PREVIOUS_HOME_PAGE("previous_home_page", "Previous Home screen", "the previous Home screen", WorkspaceGestureActionGroup.NAVIGATION, "go to the previous Home screen"),
    NOTIFICATIONS("notifications", "Show notifications panel", "the notification shade", WorkspaceGestureActionGroup.SYSTEM, "show notifications"),
    RECENTS("recents", "Show recent apps", "recent apps", WorkspaceGestureActionGroup.SYSTEM, "show recent apps"),
    QUICK_SETTINGS("quick_settings", "Show toggle panel", "Quick Settings", WorkspaceGestureActionGroup.SYSTEM, "open Quick Settings"),
    LOCK_SCREEN("lock_screen", "Turn off the screen", "the screen", WorkspaceGestureActionGroup.SYSTEM, "turn off the screen"),
    FREEZE_ALL_WORK("freeze_all_work", "Freeze isolated apps", "Freeze all isolated apps", WorkspaceGestureActionGroup.SYSTEM, "freeze isolated apps"),
    TOGGLE_WORK_PROFILE("toggle_work_profile", "Pause / resume work profile", "work profile state", WorkspaceGestureActionGroup.SYSTEM, "pause or resume work profile"),
    LAUNCH_APP("launch_app", "Open app", "another app", WorkspaceGestureActionGroup.APPS, "open another app"),
    LAUNCH_SHORTCUT("launch_shortcut", "Open shortcut", "an app shortcut", WorkspaceGestureActionGroup.SHORTCUTS, "open an app shortcut");

    companion object {
        val selectable: List<WorkspaceIconGestureAction> = entries

        fun fromStorage(value: String?): WorkspaceIconGestureAction =
            entries.firstOrNull { it.storageKey == value } ?: NONE
    }
}
