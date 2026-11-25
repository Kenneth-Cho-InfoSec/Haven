package com.arcturus.haven.launcher.core.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerOrderingTest {
    private val entries = listOf(
        DrawerOrderEntry("z", "pkg.z/Main", "Zoom", firstInstallTime = 30, usageCount = 12, dominantIconColor = 0xFF0000FF.toInt()),
        DrawerOrderEntry("a", "pkg.a/Main", "Album", firstInstallTime = 10, usageCount = 8, dominantIconColor = 0xFFFF0000.toInt()),
        DrawerOrderEntry("m", "pkg.m/Main", "Maps", firstInstallTime = 20, usageCount = 4, dominantIconColor = 0xFF808080.toInt()),
    )

    @Test
    fun sortsByNameUsageAndInstallTime() {
        assertEquals(listOf("a", "m", "z"), DrawerOrdering.sort(entries, DrawerSortMode.NAME, locale = Locale.US))
        assertEquals(listOf("z", "a", "m"), DrawerOrdering.sort(entries, DrawerSortMode.USAGE, locale = Locale.US))
        assertEquals(listOf("z", "m", "a"), DrawerOrdering.sort(entries, DrawerSortMode.INSTALL_TIME, locale = Locale.US))
    }

    @Test
    fun userModeUsesPersistedComponentOrderThenNameForUnlistedApps() {
        assertEquals(listOf("m", "z", "a"), DrawerOrdering.sort(entries, DrawerSortMode.USER, listOf("pkg.m/Main", "pkg.z/Main"), Locale.US))
    }

    @Test
    fun iconColorModeUsesHueAndPlacesNeutralColorsLast() {
        assertEquals(listOf("a", "z", "m"), DrawerOrdering.sort(entries, DrawerSortMode.ICON_COLOR, locale = Locale.US))
    }

    @Test
    fun swappingVisibleAppsPreservesFilteredSlots() {
        assertEquals(
            listOf("three", "hidden", "two", "one"),
            DrawerOrdering.moveWithinSubset(
                fullOrder = listOf("one", "hidden", "two", "three"),
                visibleOrder = listOf("one", "two", "three"),
                sourceKey = "one",
                targetKey = "three",
            ),
        )
    }
}
