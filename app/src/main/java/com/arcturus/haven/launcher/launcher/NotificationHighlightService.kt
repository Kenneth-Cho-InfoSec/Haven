package com.arcturus.haven.launcher.launcher

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.edit
import com.arcturus.haven.launcher.core.settings.LauncherSettings

/** Stores only package names so the launcher can mark apps with current notifications. */
class NotificationHighlightService : NotificationListenerService() {
    override fun onListenerConnected() = refreshPackages()

    override fun onNotificationPosted(sbn: StatusBarNotification) = refreshPackages()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refreshPackages()

    private fun refreshPackages() {
        val packages = runCatching {
            getActiveNotifications().asSequence()
                .map { it.packageName }
                .filter { it != packageName }
                .toSet()
        }.getOrDefault(emptySet())
        getSharedPreferences(LauncherSettings.PREFERENCES, MODE_PRIVATE).edit {
            putStringSet(LauncherSettings.KEY_NOTIFICATION_PACKAGES, packages)
        }
        sendBroadcast(Intent(ACTION_CHANGED).setPackage(packageName))
    }

    companion object {
        const val ACTION_CHANGED = "com.arcturus.haven.launcher.NOTIFICATION_PACKAGES_CHANGED"
    }
}
