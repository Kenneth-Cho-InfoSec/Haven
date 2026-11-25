package com.arcturus.haven.launcher.core.data

import java.text.Normalizer
import java.util.Locale

/** Searchable Android Settings destinations mirrored from Smart Launcher's Settings index. */
data class AndroidSettingsSearchEntry(
    val title: String,
    val intentAction: String,
    val keywords: List<String> = emptyList(),
    val minimumApi: Int = 1,
)

object AndroidSettingsSearch {
    private val diacriticMarks = Regex("\\p{Mn}+")

    val entries: List<AndroidSettingsSearchEntry> = listOf(
        AndroidSettingsSearchEntry("Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("screen reader")),
        AndroidSettingsSearchEntry("Airplane Mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight mode")),
        AndroidSettingsSearchEntry("Manage Apps", "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS", listOf("applications")),
        AndroidSettingsSearchEntry("Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("power usage")),
        AndroidSettingsSearchEntry("Battery Saver", "android.settings.BATTERY_SAVER_SETTINGS", listOf("power saving")),
        AndroidSettingsSearchEntry("Bluetooth", "android.settings.BLUETOOTH_SETTINGS"),
        AndroidSettingsSearchEntry("Cast settings", "android.settings.CAST_SETTINGS", minimumApi = 21),
        AndroidSettingsSearchEntry("Date and Time", "android.settings.DATE_SETTINGS", listOf("clock")),
        AndroidSettingsSearchEntry("Device Info", "android.settings.DEVICE_INFO_SETTINGS", listOf("about phone", "device information")),
        AndroidSettingsSearchEntry("Display", "android.settings.DISPLAY_SETTINGS", listOf("screen")),
        AndroidSettingsSearchEntry("On-screen Keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("keyboard", "input method")),
        AndroidSettingsSearchEntry("Internal Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("device storage")),
        AndroidSettingsSearchEntry("Languages", "android.settings.LOCALE_SETTINGS", listOf("language", "locale")),
        AndroidSettingsSearchEntry("Storage", "android.settings.MEMORY_CARD_SETTINGS", listOf("memory")),
        AndroidSettingsSearchEntry("Network and Internet", "android.settings.WIRELESS_SETTINGS", listOf("network", "internet", "wireless")),
        AndroidSettingsSearchEntry("NFC", "android.settings.NFC_SETTINGS", minimumApi = 16),
        AndroidSettingsSearchEntry("NFC Payments", "android.settings.NFC_PAYMENT_SETTINGS", listOf("tap to pay"), minimumApi = 19),
        AndroidSettingsSearchEntry("Notifications", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", listOf("notification access"), minimumApi = 19),
        AndroidSettingsSearchEntry("Overlay Permission", "android.settings.action.MANAGE_OVERLAY_PERMISSION", listOf("display over other apps"), minimumApi = 23),
        AndroidSettingsSearchEntry("Privacy", "android.settings.PRIVACY_SETTINGS", minimumApi = 5),
        AndroidSettingsSearchEntry("Security", "android.settings.SECURITY_SETTINGS"),
        AndroidSettingsSearchEntry("Sound", "android.settings.SOUND_SETTINGS", listOf("volume")),
        AndroidSettingsSearchEntry("Usage Access", "android.settings.USAGE_ACCESS_SETTINGS", listOf("app usage"), minimumApi = 21),
        AndroidSettingsSearchEntry("WiFi", "android.settings.WIFI_SETTINGS", listOf("wi-fi")),
        AndroidSettingsSearchEntry("Default Apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("default home", "default launcher"), minimumApi = 21),
        AndroidSettingsSearchEntry("VPN", "android.settings.VPN_SETTINGS"),
        // Smart targets a private Settings component for tethering; wireless settings is the public fallback.
        AndroidSettingsSearchEntry("Hotspot and Tethering", "android.settings.WIRELESS_SETTINGS", listOf("hotspot", "tether")),
        AndroidSettingsSearchEntry("Night Light", "android.settings.NIGHT_DISPLAY_SETTINGS", listOf("night display"), minimumApi = 26),
        AndroidSettingsSearchEntry("Data Usage", "android.settings.DATA_USAGE_SETTINGS", listOf("mobile data"), minimumApi = 28),
    )

    fun filter(query: String, apiLevel: Int): List<AndroidSettingsSearchEntry> {
        val normalizedQuery = normalize(query).ifEmpty { return emptyList() }
        return entries.filter { entry ->
            entry.minimumApi <= apiLevel &&
                (sequenceOf(entry.title) + entry.keywords.asSequence()).any { normalize(it).contains(normalizedQuery) }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(diacriticMarks, "")
        .filter(Char::isLetterOrDigit)
        .lowercase(Locale.ROOT)
}
