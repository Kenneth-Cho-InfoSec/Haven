package com.arcturus.haven.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconGroupLayoutTest {
    @Test
    fun widgetUsesTheReferenceFourByFourGrid() {
        assertEquals(4, IconGroupLayout.COLUMN_COUNT)
        assertEquals(4, IconGroupLayout.ROW_COUNT)
        assertEquals(16, IconGroupLayout.MAX_APPS)
    }

    @Test
    fun everyLayoutUsesEachOfTheSixteenCellsOnce() {
        IconGroupLayout.entries.forEach { layout ->
            assertEquals(IconGroupLayout.CELL_COUNT, layout.cellOrder().size)
            assertEquals((0 until IconGroupLayout.CELL_COUNT).toSet(), layout.cellOrder().toSet())
        }
    }

    @Test
    fun layoutsBeginInDifferentRegionsToFormTheirNamedArrangement() {
        assertEquals(listOf(2, 4, 7, 8, 11, 13), IconGroupLayout.FLOWER.cellOrder().take(6))
        assertEquals(listOf(0, 1, 2, 3), IconGroupLayout.GRID.cellOrder().take(4))
        assertEquals(listOf(12, 13, 14, 15), IconGroupLayout.ARCH.cellOrder().take(4))
        assertNotEquals(IconGroupLayout.FLOWER.cellOrder().toList(), IconGroupLayout.HONEYCOMB.cellOrder().toList())
    }

    @Test
    fun unknownLayoutKeyFallsBackToFlower() {
        assertEquals(IconGroupLayout.FLOWER, IconGroupLayout.fromStorageKey("missing"))
        assertEquals(IconGroupLayout.GRID, IconGroupLayout.fromStorageKey("grid"))
    }
}
