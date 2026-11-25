package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceWidgetStackTest {
    @Test
    fun orientationStorageDefaultsToHorizontalForUnknownValues() {
        assertEquals(WorkspaceWidgetStackOrientation.HORIZONTAL, WorkspaceWidgetStackOrientation.fromStorage("unknown"))
        assertEquals(WorkspaceWidgetStackOrientation.VERTICAL, WorkspaceWidgetStackOrientation.fromStorage("vertical"))
    }

    @Test
    fun normalizedStackDropsInvalidAndDuplicateWidgetIds() {
        val stack = WorkspaceWidgetStack(-4, listOf(12, -1, 12, 18, 0), currentIndex = 99).normalized()

        assertEquals(listOf(12, 18), stack.widgetIds)
        assertEquals(1, stack.currentIndex)
    }

    @Test
    fun normalizedStackClampsIntervalAndSize() {
        val stack = WorkspaceWidgetStack(-2, rotateIntervalSeconds = 1, size = WorkspaceWidgetSize(0, -10)).normalized()

        assertEquals(5, stack.rotateIntervalSeconds)
        assertEquals(WorkspaceWidgetSize(1, 1), stack.size)
    }

    @Test
    fun emptyStackAlwaysUsesFirstIndex() {
        val stack = WorkspaceWidgetStack(stackId = -1, currentIndex = 4).normalized()

        assertEquals(0, stack.currentIndex)
    }
}
