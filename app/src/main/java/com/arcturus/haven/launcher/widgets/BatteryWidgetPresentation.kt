package com.arcturus.haven.launcher.widgets

internal enum class BatteryWidgetAxis {
    HORIZONTAL,
    VERTICAL,
}

internal data class BatteryWidgetPresentation(
    val percent: Int,
    val axis: BatteryWidgetAxis,
    val valueLabel: String,
) {
    companion object {
        fun forBounds(percent: Int, widthDp: Int, heightDp: Int): BatteryWidgetPresentation {
            val level = percent.coerceIn(0, 100)
            val axis = if (widthDp >= heightDp * 1.2f) BatteryWidgetAxis.HORIZONTAL else BatteryWidgetAxis.VERTICAL
            return BatteryWidgetPresentation(
                percent = level,
                axis = axis,
                valueLabel = if (axis == BatteryWidgetAxis.HORIZONTAL) "$level%" else level.toString(),
            )
        }
    }
}
