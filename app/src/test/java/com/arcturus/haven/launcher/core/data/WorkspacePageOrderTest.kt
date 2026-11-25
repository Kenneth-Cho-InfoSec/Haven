package com.arcturus.haven.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspacePageOrderTest {
    @Test
    fun displayIndexMirrorsSavedPagesWhenReversed() {
        assertEquals(0, WorkspacePageOrder.displayIndex(0, 4, false))
        assertEquals(3, WorkspacePageOrder.displayIndex(0, 4, true))
        assertEquals(0, WorkspacePageOrder.displayIndex(3, 4, true))
        assertEquals(1, WorkspacePageOrder.displayIndex(2, 4, true))
    }

    @Test
    fun horizontalSwipeMovesToAdjacentPageInDisplayedDirection() {
        assertEquals(2, WorkspacePageOrder.pageAfterHorizontalSwipe(1, 1, 4, false))
        assertEquals(0, WorkspacePageOrder.pageAfterHorizontalSwipe(1, -1, 4, false))
        assertEquals(0, WorkspacePageOrder.pageAfterHorizontalSwipe(1, 1, 4, true))
        assertEquals(2, WorkspacePageOrder.pageAfterHorizontalSwipe(1, -1, 4, true))
    }

    @Test
    fun horizontalSwipeClampsAtDisplayedEdges() {
        assertEquals(0, WorkspacePageOrder.pageAfterHorizontalSwipe(0, -1, 3, false))
        assertEquals(2, WorkspacePageOrder.pageAfterHorizontalSwipe(2, 1, 3, false))
        assertEquals(2, WorkspacePageOrder.pageAfterHorizontalSwipe(2, -1, 3, true))
        assertEquals(0, WorkspacePageOrder.pageAfterHorizontalSwipe(0, 1, 3, true))
    }

    @Test
    fun onePageAlwaysMapsToZero() {
        assertEquals(0, WorkspacePageOrder.displayIndex(0, 1, true))
        assertEquals(0, WorkspacePageOrder.pageAfterHorizontalSwipe(0, 1, 1, true))
    }
}
