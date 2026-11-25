package com.arcturus.haven.launcher.core.model

internal enum class DrawerLaunchView(val preferenceValue: String, val label: String) {
    ALL_APPS("all", "All apps"),
    SEARCH("search", "Search"),
    APP_INDEX("index", "Search apps by index");

    companion object {
        fun fromPreference(value: String?): DrawerLaunchView =
            entries.firstOrNull { it.preferenceValue == value } ?: ALL_APPS
    }
}
