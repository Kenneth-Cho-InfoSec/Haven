package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSearchTest {
    private val apps = listOf(
        LauncherAppEntry("pkg.z", "Z", "Zoom"),
        LauncherAppEntry("pkg.m", "M", "Maps"),
        LauncherAppEntry("pkg.a", "A", "Álbum"),
    )

    @Test
    fun emptyQueryReturnsAlphabeticalList() {
        assertEquals(listOf("Álbum", "Maps", "Zoom"), LauncherSearch.filter(apps, "").map { it.label })
    }

    @Test
    fun preservingFilterKeepsCallerOrderForSortedDrawers() {
        assertEquals(listOf("Zoom", "Maps", "Álbum"), LauncherSearch.filterPreservingOrder(apps, "m").map { it.label })
    }

    @Test
    fun queryIgnoresCaseAndDiacritics() {
        assertEquals(listOf("Álbum"), LauncherSearch.filter(apps, "album").map { it.label })
    }

    @Test
    fun queryMatchesNoRowsWhenUnknown() {
        assertEquals(emptyList<LauncherAppEntry>(), LauncherSearch.filter(apps, "missing"))
    }
}
