package com.arcturus.haven.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceIconGestureAssignmentTest {
    private val baseItem = WorkspaceItem(0, 3, WorkspaceShortcut("com.example.camera", "CameraActivity", "Camera"))

    @Test
    fun independentGestureAssignmentsCanCoexist() {
        val withTwo = baseItem
            .withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGestureAction.SEARCH))
            .withGestureAssignment(WorkspaceIconGestureAssignment(
                WorkspaceIconGesture.SWIPE_LEFT,
                WorkspaceIconGestureAction.LAUNCH_APP,
                targetPackage = "com.example.clock",
                targetClass = "ClockActivity",
                targetLabel = "Clock",
            ))

        assertEquals(listOf(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGesture.SWIPE_LEFT), withTwo.iconGestureAssignments.map { it.gesture })
        assertEquals("2 gestures assigned", withTwo.gestureSummary("Camera"))
    }

    @Test
    fun changingOneGesturePreservesOthersAndNoneClearsOnlyThatGesture() {
        val initial = baseItem
            .withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGestureAction.SEARCH))
            .withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.DOUBLE_TAP, WorkspaceIconGestureAction.APP_DRAWER))
        val changed = initial.withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGestureAction.SETTINGS))
        val cleared = changed.withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGestureAction.NONE))

        assertEquals(2, changed.iconGestureAssignments.size)
        assertEquals(WorkspaceIconGestureAction.SETTINGS, changed.iconGestureAssignments.first().action)
        assertEquals(listOf(WorkspaceIconGesture.DOUBLE_TAP), cleared.iconGestureAssignments.map { it.gesture })
        assertEquals("Double tap opens the app drawer", cleared.gestureSummary("Camera"))
    }

    @Test
    fun doubleTapBadgeRequiresThePreferenceAndAConfiguredDoubleTapAction() {
        val swipeOnly = baseItem.withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.SWIPE_UP, WorkspaceIconGestureAction.SEARCH))
        val withDoubleTap = swipeOnly.withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.DOUBLE_TAP, WorkspaceIconGestureAction.APP_DRAWER))

        assertNull(swipeOnly.doubleTapBadgeAssignment(enabled = true))
        assertNull(withDoubleTap.doubleTapBadgeAssignment(enabled = false))
        assertEquals(WorkspaceIconGestureAction.APP_DRAWER, withDoubleTap.doubleTapBadgeAssignment(enabled = true)?.action)
    }

    @Test
    fun folderCanPersistItsOwnGestureAndDoubleTapBadgeAssignment() {
        val folder = WorkspaceItem(
            page = 0,
            cell = 6,
            folderLabel = "Tools",
            folder = listOf(
                WorkspaceShortcut("com.example.camera", "CameraActivity", "Camera"),
                WorkspaceShortcut("com.example.clock", "ClockActivity", "Clock"),
            ),
        ).withGestureAssignment(WorkspaceIconGestureAssignment(WorkspaceIconGesture.DOUBLE_TAP, WorkspaceIconGestureAction.APP_DRAWER))

        assertTrue(folder.isFolder)
        assertEquals(WorkspaceIconGestureAction.APP_DRAWER, folder.doubleTapBadgeAssignment(enabled = true)?.action)
        assertEquals("Double tap opens the app drawer", folder.gestureSummary("Tools"))
    }
}
