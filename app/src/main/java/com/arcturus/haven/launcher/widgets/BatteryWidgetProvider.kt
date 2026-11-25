package com.arcturus.haven.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.arcturus.haven.R
import kotlin.math.roundToInt

/** Responsive, live Battery widget using Android's public AppWidgetHost lifecycle. */
class BatteryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, manager, appWidgetId, newOptions)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BatteryWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        internal fun renderGaugeBitmap(context: Context, percent: Int, widthDp: Int, heightDp: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            val width = (widthDp.coerceAtLeast(1) * density).roundToInt().coerceIn(64, 1024)
            val height = (heightDp.coerceAtLeast(1) * density).roundToInt().coerceIn(64, 1024)
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            val stroke = (2f * density).coerceAtLeast(1f)
            val bounds = RectF(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f)
            val radius = minOf(bounds.width(), bounds.height()) * 0.22f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25282E.toInt() }
            canvas.drawRoundRect(bounds, radius, radius, paint)

            val clipPath = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
            val presentation = BatteryWidgetPresentation.forBounds(percent, widthDp, heightDp)
            val fillFraction = presentation.percent / 100f
            paint.color = 0xFF00D184.toInt()
            canvas.withClip(clipPath) {
                when (presentation.axis) {
                    BatteryWidgetAxis.HORIZONTAL -> drawRect(bounds.left, bounds.top, bounds.left + bounds.width() * fillFraction, bounds.bottom, paint)
                    BatteryWidgetAxis.VERTICAL -> drawRect(bounds.left, bounds.bottom - bounds.height() * fillFraction, bounds.right, bounds.bottom, paint)
                }
            }
            paint.apply {
                color = 0xFF484B50.toInt()
                style = Paint.Style.STROKE
                strokeWidth = stroke
            }
            canvas.drawRoundRect(bounds, radius, radius, paint)
            return bitmap
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle = manager.getAppWidgetOptions(appWidgetId),
        ) {
            val sticky = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            val level = sticky?.getIntExtra("level", -1) ?: -1
            val scale = sticky?.getIntExtra("scale", -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt().coerceIn(0, 100) else 0
            val provider = manager.getAppWidgetInfo(appWidgetId)
            fun dim(minKey: String, maxKey: String, fallback: Int) =
                maxOf(options.getInt(minKey, fallback), options.getInt(maxKey, fallback)).coerceAtLeast(1)
            val widthDp = dim(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, provider?.minWidth?.coerceAtLeast(120) ?: 120)
            val heightDp = dim(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, provider?.minHeight?.coerceAtLeast(60) ?: 60)
            val presentation = BatteryWidgetPresentation.forBounds(percent, widthDp, heightDp)
            val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
                setImageViewBitmap(R.id.battery_gauge, renderGaugeBitmap(context, percent, widthDp, heightDp))
                setTextViewText(R.id.battery_value, presentation.valueLabel)
                setTextViewTextSize(R.id.battery_value, TypedValue.COMPLEX_UNIT_SP, (minOf(widthDp, heightDp) * 0.30f).coerceIn(20f, 38f))
                setContentDescription(R.id.battery_widget_root, "Battery level $percent percent")
                setOnClickPendingIntent(
                    R.id.battery_widget_root,
                    PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
