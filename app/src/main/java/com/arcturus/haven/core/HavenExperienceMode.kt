package com.arcturus.haven.core

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.arcturus.haven.launcher.core.settings.LauncherSettings

enum class HavenExperienceMode(
    val storageValue: String,
    val launcherEnabled: Boolean,
    val isolationEnabled: Boolean,
) {
    BOTH("both", launcherEnabled = true, isolationEnabled = true),
    LAUNCHER_ONLY("launcher_only", launcherEnabled = true, isolationEnabled = false),
    ISOLATION_ONLY("isolation_only", launcherEnabled = false, isolationEnabled = true),
    ;

    companion object {
        @JvmStatic
        fun fromStorage(value: String?): HavenExperienceMode =
            entries.firstOrNull { it.storageValue == value } ?: BOTH
    }
}

object HavenModePreferences {
    private const val HOME_ACTIVITY_CLASS = "com.arcturus.haven.launcher.launcher.HomeActivity"

    @JvmStatic
    fun isWelcomeCompleted(context: Context): Boolean =
        prefs(context).getBoolean(LauncherSettings.KEY_WELCOME_COMPLETED, false)

    @JvmStatic
    fun getMode(context: Context): HavenExperienceMode =
        HavenExperienceMode.fromStorage(prefs(context).getString(LauncherSettings.KEY_EXPERIENCE_MODE, null))

    @JvmStatic
    fun isLauncherEnabled(context: Context): Boolean = getMode(context).launcherEnabled

    @JvmStatic
    fun isIsolationEnabled(context: Context): Boolean = getMode(context).isolationEnabled

    @JvmStatic
    fun setMode(context: Context, mode: HavenExperienceMode) {
        val appContext = context.applicationContext
        prefs(appContext).edit {
            putString(LauncherSettings.KEY_EXPERIENCE_MODE, mode.storageValue)
            putBoolean(LauncherSettings.KEY_WELCOME_COMPLETED, true)
        }
        syncHomeComponentState(appContext, mode.launcherEnabled)
    }

    @JvmStatic
    fun syncHomeComponentState(context: Context, launcherEnabled: Boolean) {
        if (context.getSystemService(DevicePolicyManager::class.java)?.isProfileOwnerApp(context.packageName) == true) return
        val desiredState = if (launcherEnabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val homeComponent = ComponentName(context, HOME_ACTIVITY_CLASS)
        context.packageManager.takeIf { it.getComponentEnabledSetting(homeComponent) != desiredState }
            ?.setComponentEnabledSetting(homeComponent, desiredState, PackageManager.DONT_KILL_APP)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(LauncherSettings.PREFERENCES, Context.MODE_PRIVATE)
}
