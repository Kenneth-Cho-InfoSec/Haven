package com.arcturus.haven.launcher.core.permissions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Provides the small set of Android global actions offered by the launcher.
 * This service does not inspect windows or consume accessibility events.
 */
class LauncherActionAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0
            packageNames = null
        }
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeService === this) activeService = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var activeService: LauncherActionAccessibilityService? = null

        fun perform(action: Int): Boolean = activeService?.performGlobalAction(action) == true

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val expected = ComponentName(context, LauncherActionAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
