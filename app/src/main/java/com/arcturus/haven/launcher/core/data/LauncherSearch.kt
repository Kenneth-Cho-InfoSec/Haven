package com.arcturus.haven.launcher.core.data

import com.arcturus.haven.launcher.core.model.LauncherAppEntry
import java.text.Normalizer
import java.util.Locale

object LauncherSearch {
    private val diacriticMarks = Regex("\\p{Mn}+")

    fun filter(apps: List<LauncherAppEntry>, query: String): List<LauncherAppEntry> =
        filterPreservingOrder(apps, query).sortedWith(compareBy<LauncherAppEntry> { normalize(it.label) }.thenBy { it.label })

    fun filterPreservingOrder(apps: List<LauncherAppEntry>, query: String): List<LauncherAppEntry> {
        if (query.isBlank()) return apps
        val normalizedQuery = normalize(query.trim()).ifEmpty { return apps }
        return apps.filter { normalize(it.label).contains(normalizedQuery) }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(diacriticMarks, "")
            .lowercase(Locale.ROOT)
}
