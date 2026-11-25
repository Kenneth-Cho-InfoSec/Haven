package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceIconGestureTest {
    @Test
    fun allSmartStyleIconGesturesHaveStableStorageKeys() {
        assertEquals(listOf("none", "up", "down", "left", "right", "double"), WorkspaceIconGesture.selectable.map { it.storageKey })
        WorkspaceIconGesture.selectable.forEach { assertEquals(it, WorkspaceIconGesture.fromStorage(it.storageKey)) }
        assertEquals(WorkspaceIconGesture.NONE, WorkspaceIconGesture.fromStorage("unknown"))
        assertEquals(WorkspaceIconGesture.NONE, WorkspaceIconGesture.fromStorage(null))
    }

    @Test
    fun swipeAssignmentsMatchDirectionAndRequireThreshold() {
        val threshold = 40f
        assertTrue(WorkspaceIconGesture.SWIPE_UP.matchesSwipe(0f, -41f, threshold))
        assertTrue(WorkspaceIconGesture.SWIPE_DOWN.matchesSwipe(0f, 41f, threshold))
        assertTrue(WorkspaceIconGesture.SWIPE_LEFT.matchesSwipe(-41f, 0f, threshold))
        assertTrue(WorkspaceIconGesture.SWIPE_RIGHT.matchesSwipe(41f, 0f, threshold))

        assertFalse(WorkspaceIconGesture.SWIPE_UP.matchesSwipe(0f, -40f, threshold))
        assertFalse(WorkspaceIconGesture.SWIPE_LEFT.matchesSwipe(-41f, 50f, threshold))
        assertFalse(WorkspaceIconGesture.SWIPE_RIGHT.matchesSwipe(-41f, 0f, threshold))
        assertFalse(WorkspaceIconGesture.DOUBLE_TAP.matchesSwipe(100f, 0f, threshold))
        assertFalse(WorkspaceIconGesture.NONE.matchesSwipe(0f, -100f, threshold))
    }

    @Test
    fun menuSubtitleReflectsAssignedGesture() {
        assertEquals("Swipe left opens Camera", WorkspaceIconGesture.SWIPE_LEFT.menuSubtitle("Camera"))
        assertEquals("Double tap opens Calendar", WorkspaceIconGesture.DOUBLE_TAP.menuSubtitle("Calendar"))
        assertEquals("Swipe right opens Haven Search", WorkspaceIconGesture.SWIPE_RIGHT.menuSubtitle("Camera", WorkspaceIconGestureAction.SEARCH))
        assertEquals("Double tap opens the app drawer", WorkspaceIconGesture.DOUBLE_TAP.menuSubtitle("Camera", WorkspaceIconGestureAction.APP_DRAWER))
        assertEquals("Swipe up opens Calendar", WorkspaceIconGesture.SWIPE_UP.menuSubtitle("Camera", WorkspaceIconGestureAction.LAUNCH_APP, "Calendar"))
        assertEquals("Swipe up opens Battery popup", WorkspaceIconGesture.SWIPE_UP.menuSubtitle("Camera", WorkspaceIconGestureAction.POPUP_WIDGET, "Battery"))
        assertEquals("Not assigned", WorkspaceIconGesture.NONE.menuSubtitle("Camera"))
    }

    @Test
    fun gesturePickerLabelsReflectTheSelectedAction() {
        assertEquals("Swipe left to open app", WorkspaceIconGesture.SWIPE_LEFT.labelFor())
        assertEquals("Swipe right to open app drawer", WorkspaceIconGesture.SWIPE_RIGHT.labelFor(WorkspaceIconGestureAction.APP_DRAWER))
        assertEquals("Swipe up to open another app", WorkspaceIconGesture.SWIPE_UP.labelFor(WorkspaceIconGestureAction.LAUNCH_APP))
        assertEquals("Swipe down to show a popup widget", WorkspaceIconGesture.SWIPE_DOWN.labelFor(WorkspaceIconGestureAction.POPUP_WIDGET))
        assertEquals("Not assigned", WorkspaceIconGesture.NONE.labelFor(WorkspaceIconGestureAction.SEARCH))
    }
}
