package com.arcturus.haven.launcher.core.data

import android.content.pm.ApplicationInfo

data class DrawerCategoryEntry(
    val componentKey: String,
    val category: String,
)

object DrawerCategories {
    const val ALL = "All apps"
    const val HIDDEN = "Hidden"
    const val OTHER = "Other"

    fun belongsToPackage(componentKey: String, packageName: String): Boolean =
        componentKey.substringBefore('/') == packageName

    fun labelFor(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Audio"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Images"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
        else -> OTHER
    }

    fun visible(
        entries: List<DrawerCategoryEntry>,
        selected: String,
        hiddenComponents: Set<String>,
    ): List<DrawerCategoryEntry> = entries.filter { entry ->
        val isHidden = entry.componentKey in hiddenComponents
        when {
            selected == HIDDEN -> isHidden
            isHidden -> false
            selected == ALL -> true
            else -> entry.category == selected
        }
    }
}
