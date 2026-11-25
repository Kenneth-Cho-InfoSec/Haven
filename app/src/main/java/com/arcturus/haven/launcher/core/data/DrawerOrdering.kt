package com.arcturus.haven.launcher.core.data

import java.text.Collator
import java.util.Collections
import java.util.Locale

enum class DrawerSortMode(val preferenceValue: String, val label: String) {
    NAME("name", "By name"),
    USAGE("usage", "By usage"),
    INSTALL_TIME("install_time", "By install time"),
    USER("user", "By user"),
    ICON_COLOR("icon_color", "By icon color");

    companion object {
        fun fromPreference(value: String?): DrawerSortMode =
            entries.firstOrNull { it.preferenceValue == value } ?: NAME
    }
}

data class DrawerOrderEntry<T>(
    val value: T,
    val componentKey: String,
    val label: String,
    val firstInstallTime: Long = 0L,
    val usageCount: Long = 0L,
    val dominantIconColor: Int? = null,
)

/** Stable app-drawer sort modes corresponding to Smart Launcher's observed All apps sorting options. */
object DrawerOrdering {
    fun <T> sort(
        entries: List<DrawerOrderEntry<T>>,
        mode: DrawerSortMode,
        userOrder: List<String> = emptyList(),
        locale: Locale = Locale.getDefault(),
    ): List<T> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val userPositions = userOrder.withIndex().associate { it.value to it.index }
        return entries.sortedWith { first, second ->
            val primary = when (mode) {
                DrawerSortMode.NAME -> collator.compare(first.label, second.label)
                DrawerSortMode.USAGE -> second.usageCount.compareTo(first.usageCount)
                DrawerSortMode.INSTALL_TIME -> second.firstInstallTime.compareTo(first.firstInstallTime)
                DrawerSortMode.USER -> userPositions.getOrDefault(first.componentKey, Int.MAX_VALUE)
                    .compareTo(userPositions.getOrDefault(second.componentKey, Int.MAX_VALUE))
                DrawerSortMode.ICON_COLOR -> iconColorOrderScore(first.dominantIconColor)
                    .compareTo(iconColorOrderScore(second.dominantIconColor))
            }
            primary.takeIf { it != 0 }
                ?: collator.compare(first.label, second.label).takeIf { it != 0 }
                ?: first.componentKey.compareTo(second.componentKey)
        }.map { it.value }
    }

    /** Swaps the visible positions while leaving filtered-out apps in their existing slots. */
    fun moveWithinSubset(
        fullOrder: List<String>,
        visibleOrder: List<String>,
        sourceKey: String,
        targetKey: String,
    ): List<String> {
        val source = visibleOrder.indexOf(sourceKey)
        val target = visibleOrder.indexOf(targetKey)
        if (source < 0 || target < 0 || source == target) return fullOrder

        val reorderedVisible = visibleOrder.toMutableList().also { Collections.swap(it, source, target) }
        val visibleKeys = visibleOrder.toHashSet()
        val visibleSlots = fullOrder.indices.filter { fullOrder[it] in visibleKeys }
        if (visibleSlots.size != reorderedVisible.size) return fullOrder

        return fullOrder.toMutableList().apply {
            visibleSlots.forEachIndexed { index, slot -> this[slot] = reorderedVisible[index] }
        }
    }

    /** Mirrors Smart Launcher's hue/value weighting for its dominant-icon-color ordering. */
    fun iconColorOrderScore(color: Int?): Double {
        if (color == null) return Double.POSITIVE_INFINITY
        val red = ((color shr 16) and 0xFF) / 255.0
        val green = ((color shr 8) and 0xFF) / 255.0
        val blue = (color and 0xFF) / 255.0
        val maximum = maxOf(red, green, blue)
        val delta = maximum - minOf(red, green, blue)
        val saturation = if (maximum == 0.0) 0.0 else delta / maximum
        val hue = if (maximum < 0.4 || saturation < 0.1) {
            360.0
        } else {
            val rawHue = when (maximum) {
                red -> 60.0 * (((green - blue) / delta) % 6.0)
                green -> 60.0 * ((blue - red) / delta + 2.0)
                else -> 60.0 * ((red - green) / delta + 4.0)
            }
            ((rawHue + 410.0) % 360.0 + 360.0) % 360.0
        }
        return (1.0 - maximum) + hue * 100.0
    }
}
