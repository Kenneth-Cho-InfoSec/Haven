package com.arcturus.haven.launcher.core.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerCategoriesTest {
    @Test
    fun identifiesLauncherOwnedActivitiesForExclusionFromItsAppPage() {
        assertTrue(DrawerCategories.belongsToPackage("com.teres.launcher/com.arcturus.haven.launcher.launcher.HomeActivity", "com.teres.launcher"))
        assertFalse(DrawerCategories.belongsToPackage("com.android.settings/com.android.settings.Settings", "com.teres.launcher"))
    }

    @Test
    fun usesDeclaredAndroidCategories() {
        assertEquals("Games", DrawerCategories.labelFor(ApplicationInfo.CATEGORY_GAME))
        assertEquals("Social", DrawerCategories.labelFor(ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(DrawerCategories.OTHER, DrawerCategories.labelFor(ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test
    fun hiddenAndCategoryFiltersDoNotLeakAppsAcrossTabs() {
        val entries = listOf(
            DrawerCategoryEntry("chat/Main", "Social"),
            DrawerCategoryEntry("game/Main", "Games"),
            DrawerCategoryEntry("private/Main", "Social"),
        )
        val hidden = setOf("private/Main")

        assertEquals(listOf("chat/Main", "game/Main"), DrawerCategories.visible(entries, DrawerCategories.ALL, hidden).map { it.componentKey })
        assertEquals(listOf("game/Main"), DrawerCategories.visible(entries, "Games", hidden).map { it.componentKey })
        assertEquals(listOf("private/Main"), DrawerCategories.visible(entries, DrawerCategories.HIDDEN, hidden).map { it.componentKey })
    }
}
