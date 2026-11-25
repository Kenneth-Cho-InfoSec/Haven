package com.arcturus.haven.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.edit
import com.arcturus.haven.R
import com.arcturus.haven.launcher.bridge.HavenProfileSession
import com.arcturus.haven.launcher.launcher.HomeActivity
import com.arcturus.haven.ui.DummyActivity
import com.arcturus.haven.util.LocalStorageManager
import com.arcturus.haven.util.Utility

/** Home widget showing Haven Work Profile state with one-tap Vault & Freeze All actions. */
class VaultControlWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        private const val PREF_WIDGET = "haven_vault_widget"
        private const val KEY_ACTIVE_COUNT = "active_count"
        private const val KEY_FROZEN_COUNT = "frozen_count"

        fun updateStats(context: Context, activeCount: Int, frozenCount: Int) {
            context.getSharedPreferences(PREF_WIDGET, Context.MODE_PRIVATE).edit {
                putInt(KEY_ACTIVE_COUNT, activeCount)
                putInt(KEY_FROZEN_COUNT, frozenCount)
            }
            updateAll(context)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, VaultControlWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREF_WIDGET, Context.MODE_PRIVATE)
            val active = prefs.getInt(KEY_ACTIVE_COUNT, -1)
            val frozen = prefs.getInt(KEY_FROZEN_COUNT, -1)
            val available = runCatching { Utility.isWorkProfileAvailable(context) }.getOrDefault(false)
            val paused = available && HavenProfileSession.isWorkProfilePaused(context)
            val autoFreezeCount = LocalStorageManager.getInstance()
                .getStringList(LocalStorageManager.PREF_AUTO_FREEZE_LIST_WORK_PROFILE)
                .count { it.isNotBlank() }

            val statusText = when {
                !available -> "Not set up · Tap Vault"
                paused -> "Work profile paused"
                active >= 0 && frozen >= 0 -> "Active · $active active · $frozen frozen"
                autoFreezeCount > 0 -> "Active · $autoFreezeCount auto-freeze"
                else -> "Work profile active"
            }

            val openIntent = pendingActivity(
                context,
                appWidgetId * 2,
                Intent(context, HomeActivity::class.java).apply {
                    putExtra(HomeActivity.EXTRA_OPEN_VAULT, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
            val freezeIntent = pendingActivity(
                context,
                appWidgetId * 2 + 1,
                Intent(DummyActivity.PUBLIC_FREEZE_ALL).apply {
                    component = ComponentName(context, DummyActivity::class.java)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )

            val views = RemoteViews(context.packageName, R.layout.widget_vault_control).apply {
                setTextViewText(R.id.vault_widget_status, statusText)
                setOnClickPendingIntent(R.id.vault_widget_open_btn, openIntent)
                setOnClickPendingIntent(R.id.vault_widget_root, openIntent)
                setOnClickPendingIntent(R.id.vault_widget_freeze_btn, freezeIntent)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun pendingActivity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
