package com.arcturus.haven.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.arcturus.haven.R
import com.arcturus.haven.launcher.core.data.IconGroupLayout
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.launcher.CircularAppIconDrawable
import org.json.JSONArray

class IconGroupWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit {
            appWidgetIds.forEach { id ->
                remove(appsKey(id))
                remove(layoutKey(id))
            }
        }
    }

    companion object {
        const val PREFERENCES = "icon_group_widgets"
        private const val KEY_APPS_PREFIX = "apps_"
        private const val KEY_LAYOUT_PREFIX = "layout_"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        fun saveConfiguration(context: Context, widgetId: Int, components: List<String>, layout: IconGroupLayout) {
            prefs(context).edit {
                putString(appsKey(widgetId), JSONArray(components.take(IconGroupLayout.MAX_APPS)).toString())
                putString(layoutKey(widgetId), layout.storageKey)
            }
        }

        fun selectedComponents(context: Context, widgetId: Int): List<String> = runCatching {
            val array = JSONArray(prefs(context).getString(appsKey(widgetId), "[]"))
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())

        fun selectedLayout(context: Context, widgetId: Int): IconGroupLayout =
            IconGroupLayout.fromStorageKey(prefs(context).getString(layoutKey(widgetId), null))

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, IconGroupWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, widgetId: Int) {
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val selected = selectedComponents(context, widgetId).take(IconGroupLayout.MAX_APPS)
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val installed = runCatching {
                launcherApps.getActivityList(null, Process.myUserHandle()).associateBy { it.componentName.flattenToString() }
            }.getOrDefault(emptyMap())
            val layout = RemoteViews(context.packageName, R.layout.widget_icon_group)
            val orderedCells = selectedLayout(context, widgetId).cellOrder()
            val resolved = selected.mapNotNull(installed::get)
            layout.setViewVisibility(R.id.icon_group_empty_hint, if (resolved.isEmpty()) View.VISIBLE else View.GONE)
            val iconTint = if (resolved.isNotEmpty()) LauncherAppearance.resolve(context).accent else 0
            val iconSizePx = (56f * context.resources.displayMetrics.density).toInt()
            orderedCells.forEachIndexed { appIndex, slot ->
                val viewId = ICON_IDS[slot]
                if (appIndex < resolved.size) {
                    val app = resolved[appIndex]
                    val drawable = runCatching { app.getIcon(0) }.getOrElse { context.packageManager.defaultActivityIcon }
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = app.componentName
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    layout.setImageViewBitmap(viewId, renderCircularIcon(drawable, iconTint, iconSizePx))
                    layout.setViewVisibility(viewId, View.VISIBLE)
                    layout.setContentDescription(viewId, app.label.toString())
                    layout.setOnClickPendingIntent(
                        viewId,
                        PendingIntent.getActivity(
                            context,
                            widgetId * IconGroupLayout.MAX_APPS + appIndex,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                } else {
                    layout.setViewVisibility(viewId, View.GONE)
                }
            }
            manager.updateAppWidget(widgetId, layout)
        }

        private fun renderCircularIcon(drawable: Drawable, tint: Int, sizePx: Int): Bitmap =
            createBitmap(sizePx, sizePx).also { bitmap ->
                CircularAppIconDrawable(drawable, tint).apply {
                    setBounds(0, 0, sizePx, sizePx)
                    draw(Canvas(bitmap))
                }
            }

        private fun appsKey(widgetId: Int) = "$KEY_APPS_PREFIX$widgetId"
        private fun layoutKey(widgetId: Int) = "$KEY_LAYOUT_PREFIX$widgetId"

        private val ICON_IDS = intArrayOf(
            R.id.icon_0, R.id.icon_1, R.id.icon_2, R.id.icon_3,
            R.id.icon_4, R.id.icon_5, R.id.icon_6, R.id.icon_7,
            R.id.icon_8, R.id.icon_9, R.id.icon_10, R.id.icon_11,
            R.id.icon_12, R.id.icon_13, R.id.icon_14, R.id.icon_15,
        )
    }
}
