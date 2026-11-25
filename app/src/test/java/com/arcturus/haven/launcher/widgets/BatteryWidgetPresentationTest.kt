package com.arcturus.haven.launcher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryWidgetPresentationTest {
    @Test
    fun wideBoundsUseHorizontalFillAndPercentLabel() {
        val presentation = BatteryWidgetPresentation.forBounds(63, widthDp = 240, heightDp = 80)

        assertEquals(BatteryWidgetAxis.HORIZONTAL, presentation.axis)
        assertEquals("63%", presentation.valueLabel)
    }

    @Test
    fun portraitAndSquareBoundsUseVerticalFillAndCompactLabel() {
        val portrait = BatteryWidgetPresentation.forBounds(50, widthDp = 80, heightDp = 240)
        val square = BatteryWidgetPresentation.forBounds(50, widthDp = 180, heightDp = 180)

        assertEquals(BatteryWidgetAxis.VERTICAL, portrait.axis)
        assertEquals("50", portrait.valueLabel)
        assertEquals(BatteryWidgetAxis.VERTICAL, square.axis)
    }

    @Test
    fun percentageIsClampedToBatteryRange() {
        assertEquals(0, BatteryWidgetPresentation.forBounds(-12, 240, 80).percent)
        assertEquals(100, BatteryWidgetPresentation.forBounds(124, 240, 80).percent)
    }
}
