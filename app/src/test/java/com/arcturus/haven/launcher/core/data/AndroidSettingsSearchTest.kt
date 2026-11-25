package com.arcturus.haven.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSettingsSearchTest {
    @Test
    fun findsWifiSettingsAndAcceptsHyphenatedQuery() {
        assertEquals(listOf("WiFi"), AndroidSettingsSearch.filter("wifi", 36).map { it.title })
        assertEquals(listOf("WiFi"), AndroidSettingsSearch.filter("wi-fi", 36).map { it.title })
    }

    @Test
    fun aliasesFindRelevantDestinations() {
        assertEquals(listOf("Network and Internet"), AndroidSettingsSearch.filter("wireless", 36).map { it.title })
        assertEquals(listOf("Hotspot and Tethering"), AndroidSettingsSearch.filter("tether", 36).map { it.title })
        assertEquals(listOf("Default Apps"), AndroidSettingsSearch.filter("default launcher", 36).map { it.title })
    }

    @Test
    fun apiLevelFiltersNewerDestinations() {
        assertEquals(emptyList<AndroidSettingsSearchEntry>(), AndroidSettingsSearch.filter("night light", 25))
        assertEquals(listOf("Night Light"), AndroidSettingsSearch.filter("night light", 26).map { it.title })
        assertEquals(emptyList<AndroidSettingsSearchEntry>(), AndroidSettingsSearch.filter("data usage", 27))
        assertEquals(listOf("Data Usage"), AndroidSettingsSearch.filter("data usage", 28).map { it.title })
    }

    @Test
    fun blankOrUnknownQueriesReturnNoSettings() {
        assertEquals(emptyList<AndroidSettingsSearchEntry>(), AndroidSettingsSearch.filter("", 36))
        assertEquals(emptyList<AndroidSettingsSearchEntry>(), AndroidSettingsSearch.filter("unrelated", 36))
    }
}
