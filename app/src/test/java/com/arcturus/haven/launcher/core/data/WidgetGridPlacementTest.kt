package com.arcturus.haven.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetGridPlacementTest {
    @Test
    fun spanRoundsUpAndFitsAvailableColumns() {
        assertEquals(WidgetGridSpan(3, 3), WidgetGridPlacement.spanFor(170, 210, 90, 80, 4))
        assertEquals(WidgetGridSpan(4, 1), WidgetGridPlacement.spanFor(500, 40, 90, 80, 4))
    }

    @Test
    fun nearestCellSnapsToRequestedPositionWhenFree() {
        val occupied = mapOf(0 to WidgetGridSpan(2, 1))
        assertEquals(2, WidgetGridPlacement.nearestAvailable(2, 0, WidgetGridSpan(1, 1), 4, occupied))
    }

    @Test
    fun nearestCellMovesWidgetAwayFromOverlappingPlacement() {
        val occupied = mapOf(0 to WidgetGridSpan(2, 2))
        val placed = WidgetGridPlacement.nearestAvailable(1, 1, WidgetGridSpan(1, 1), 4, occupied)
        assertNotNull(placed)
        val (column, row) = WidgetGridPlacement.cell(placed!!, 4)
        assertFalse(WidgetGridPlacement.overlaps(column, row, WidgetGridSpan(1, 1), 4, occupied))
    }

    @Test
    fun placementReturnsNullWhenThereIsNoRoom() {
        val occupied = mapOf(0 to WidgetGridSpan(2, 1), 2 to WidgetGridSpan(2, 1))
        assertNull(WidgetGridPlacement.nearestAvailable(0, 0, WidgetGridSpan(4, 1), 4, occupied, maxRows = 1))
    }
}
