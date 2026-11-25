package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchStartAppHistoryTest {
    @Test
    fun recordingPromotesComponentAndRemovesDuplicates() {
        assertEquals(
            listOf("camera/Main", "settings/Main", "calendar/Main"),
            SearchStartAppHistory.record(
                listOf("settings/Main", "camera/Main", "calendar/Main", "camera/Main"),
                "camera/Main",
            ),
        )
    }

    @Test
    fun historyCapsAtTwentyEntries() {
        val history = (0 until 25).map { "app$it/Main" }
        val updated = SearchStartAppHistory.record(history, "new/Main")

        assertEquals(20, updated.size)
        assertEquals("new/Main", updated.first())
        assertEquals("app18/Main", updated.last())
    }

    @Test
    fun orderingUsesRecentHistoryThenAlphabeticalFallback() {
        val entries = listOf(
            LauncherAppEntry("z", "Main", "Zebra"),
            LauncherAppEntry("c", "Main", "Camera"),
            LauncherAppEntry("a", "Main", "Alpha"),
            LauncherAppEntry("s", "Main", "Settings"),
        )

        assertEquals(
            listOf("c/Main", "s/Main", "a/Main", "z/Main"),
            SearchStartAppHistory.order(entries, listOf("c/Main", "missing/Main", "s/Main"))
                .map(LauncherAppEntry::componentKey),
        )
    }

    @Test
    fun malformedHistoryIsIgnored() {
        assertEquals(emptyList<String>(), SearchStartAppHistory.parse("not-json"))
        assertEquals(emptyList<String>(), SearchStartAppHistory.parse(null))
    }
}
