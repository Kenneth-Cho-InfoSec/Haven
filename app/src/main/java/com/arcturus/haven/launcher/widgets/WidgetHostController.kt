package com.arcturus.haven.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.UserManager

class WidgetHostController(context: Context) {
    private val context = context.applicationContext
    val manager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(this.context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> = loadProviders(context, manager)

    fun delete(widgetId: Int) = host.deleteAppWidgetId(widgetId)

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun startListening() {
        runCatching { host.startListening() }
    }

    companion object {
        private const val HOST_ID = 0x54455245

        internal fun loadProviders(
            context: Context,
            manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        ): List<AppWidgetProviderInfo> {
            val profiles = runCatching { context.getSystemService(UserManager::class.java)?.userProfiles }.getOrNull().orEmpty()
            val all = if (profiles.isEmpty()) {
                manager.installedProviders
            } else {
                profiles.flatMap { profile ->
                    runCatching { manager.getInstalledProvidersForProfile(profile) }.getOrDefault(emptyList())
                }
            }
            return all.sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
        }
    }
}
