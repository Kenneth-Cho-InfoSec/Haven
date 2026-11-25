package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import org.json.JSONArray

/** Keeps blank-query Search suggestions in recent launch order. */
object SearchStartAppHistory {
    const val MAX_HISTORY = 20

    fun record(history: List<String>, componentKey: String): List<String> =
        normalizeHistory(if (componentKey.isBlank()) history else listOf(componentKey) + history)

    fun order(entries: List<LauncherAppEntry>, history: List<String>): List<LauncherAppEntry> {
        val ranks = normalizeHistory(history).withIndex().associate { it.value to it.index }
        return entries.sortedWith(
            compareBy<LauncherAppEntry> { ranks[it.componentKey] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() }
                .thenBy { it.label },
        )
    }

    fun parse(serialized: String?): List<String> = runCatching {
        val array = JSONArray(serialized ?: "[]")
        normalizeHistory(List(array.length(), array::optString))
    }.getOrDefault(emptyList())

    private fun normalizeHistory(history: List<String>): List<String> =
        history.asSequence().filter(String::isNotBlank).distinct().take(MAX_HISTORY).toList()
}
