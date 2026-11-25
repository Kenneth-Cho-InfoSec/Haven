package com.arcturus.haven.launcher.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Small vector-like line icons used by the launcher menus. */
class SettingsGlyphView @JvmOverloads constructor(
    context: Context,
    private val glyph: String = "",
    private val tint: Int = 0,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaintObj = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val auxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusablePath = Path()
    private val reusableRect = RectF()
    private val fontTypeface = Typeface.create("sans-serif", Typeface.BOLD)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.9f
        fillPaintObj.color = tint
        val scale = min(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        when (glyph) {
            "close" -> {
                canvas.drawCircle(12f, 12f, 8f, paint)
                canvas.drawLine(9f, 9f, 15f, 15f, paint)
                canvas.drawLine(15f, 9f, 9f, 15f, paint)
            }
            "notifications" -> {
                canvas.drawRoundRect(4f, 6f, 18f, 19f, 3f, 3f, paint)
                canvas.drawCircle(18f, 6f, 3f, fillPaintObj)
                canvas.drawLine(8f, 21f, 14f, 21f, paint)
            }
            "wallpaper" -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 2f, 2f, paint)
                canvas.drawCircle(15.5f, 8f, 1.5f, paint)
                path(canvas, 5f, 18f, 10f, 12f, 13f, 15f, 16f, 12f, 20f, 18f)
            }
            "appearance" -> {
                reusableRect.set(3f, 4f, 21f, 20f)
                canvas.drawOval(reusableRect, paint)
                canvas.drawCircle(8f, 9f, 1.1f, fillPaintObj)
                canvas.drawCircle(12f, 7.5f, 1.1f, fillPaintObj)
                canvas.drawCircle(16f, 9f, 1.1f, fillPaintObj)
                canvas.drawCircle(8f, 14f, 1.1f, fillPaintObj)
            }
            "messages" -> {
                canvas.drawRoundRect(3f, 4f, 21f, 18f, 6f, 6f, paint)
                path(canvas, 8f, 17f, 7f, 21f, 12f, 18f)
                for (x in 8..16 step 4) canvas.drawCircle(x.toFloat(), 11f, 0.8f, fillPaintObj)
            }
            "gestures" -> {
                path(canvas, 7f, 11f, 7f, 4f, 4f, 7f)
                path(canvas, 7f, 4f, 7f, 11f, 10f, 8f)
                path(canvas, 17f, 13f, 17f, 20f, 14f, 17f)
                path(canvas, 17f, 20f, 17f, 13f, 20f, 16f)
                canvas.drawCircle(7f, 17f, 1.5f, paint)
                canvas.drawCircle(17f, 7f, 1.5f, paint)
            }
            "gesture" -> {
                path(canvas, 8f, 20f, 8f, 9f, 10f, 7f, 12f, 9f, 12f, 14f)
                path(canvas, 12f, 12f, 14f, 10f, 16f, 12f, 16f, 15f)
                path(canvas, 16f, 13f, 18f, 11f, 20f, 13f, 20f, 17f, 17f, 20f, 10f, 20f)
            }
            "swipe_up", "swipe_down", "swipe_left", "swipe_right" -> {
                val angle = when (glyph) {
                    "swipe_down" -> 180f
                    "swipe_left" -> -90f
                    "swipe_right" -> 90f
                    else -> 0f
                }
                canvas.save()
                if (angle != 0f) canvas.rotate(angle, 12f, 12f)
                canvas.drawCircle(12f, 18f, 3.2f, paint)
                canvas.drawLine(12f, 13f, 12f, 4f, paint)
                path(canvas, 8f, 8f, 12f, 4f, 16f, 8f)
                canvas.restore()
            }
            "double_tap" -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(12f, 12f, 4.2f, paint)
                canvas.drawCircle(12f, 12f, 1.5f, fillPaintObj)
            }
            "keyboard" -> {
                canvas.drawRoundRect(2.5f, 5f, 21.5f, 19f, 2f, 2f, paint)
                for (y in 9..13 step 4) for (x in 6..18 step 4) {
                    canvas.drawRoundRect(x - 1f, y - 1f, x + 1f, y + 1f, 0.6f, 0.6f, paint)
                }
                canvas.drawLine(8f, 16.5f, 16f, 16.5f, paint)
            }
            "screen_off" -> {
                canvas.drawRoundRect(4f, 3f, 15f, 21f, 2f, 2f, paint)
                canvas.drawLine(7f, 6f, 12f, 6f, paint)
                canvas.drawCircle(9.5f, 18f, 0.8f, fillPaintObj)
                path(canvas, 18f, 6f, 18.8f, 8f, 21f, 8.8f, 18.8f, 9.5f, 18f, 12f, 17.2f, 9.5f, 15f, 8.8f, 17.2f, 8f, 18f, 6f)
            }
            "two_fingers" -> {
                path(canvas, 4f, 20f, 4f, 13f, 5f, 11f, 7f, 12f, 7f, 8f, 8f, 6f, 10f, 7f, 10f, 12f)
                path(canvas, 10f, 12f, 10f, 5f, 11f, 3f, 13f, 4f, 13f, 12f)
                path(canvas, 13f, 12f, 13f, 7f, 14f, 5f, 16f, 6f, 16f, 13f)
                path(canvas, 16f, 12f, 18f, 10f, 20f, 11f, 20f, 16f, 17f, 21f, 8f, 21f, 4f, 20f)
            }
            "tune" -> {
                canvas.drawLine(4f, 6f, 20f, 6f, paint)
                canvas.drawLine(4f, 12f, 20f, 12f, paint)
                canvas.drawLine(4f, 18f, 20f, 18f, paint)
                canvas.drawCircle(9f, 6f, 2f, fillPaintObj)
                canvas.drawCircle(15f, 12f, 2f, fillPaintObj)
                canvas.drawCircle(8f, 18f, 2f, fillPaintObj)
            }
            "edit", "edit_icon" -> {
                path(canvas, 4f, 17f, 3f, 21f, 7f, 20f, 19f, 8f, 16f, 5f, 4f, 17f)
                path(canvas, 14f, 7f, 17f, 4f, 20f, 7f, 17f, 10f)
            }
            "position" -> {
                canvas.drawRoundRect(4f, 4f, 20f, 20f, 1f, 1f, paint)
                for (c in intArrayOf(9, 15)) {
                    canvas.drawLine(c.toFloat(), 4f, c.toFloat(), 20f, paint)
                    canvas.drawLine(4f, c.toFloat(), 20f, c.toFloat(), paint)
                }
            }
            "backup" -> {
                canvas.drawRoundRect(4f, 3f, 20f, 21f, 2f, 2f, paint)
                canvas.drawRect(8f, 4f, 16f, 9f, paint)
                canvas.drawRoundRect(8f, 13f, 16f, 18f, 1f, 1f, paint)
            }
            "share" -> {
                canvas.drawLine(7f, 12f, 17f, 6f, paint)
                canvas.drawLine(7f, 12f, 17f, 18f, paint)
                canvas.drawCircle(6f, 12f, 3f, paint)
                canvas.drawCircle(18f, 5f, 3f, paint)
                canvas.drawCircle(18f, 19f, 3f, paint)
            }
            "game" -> {
                path(canvas, 7f, 8f, 17f, 8f, 20f, 10f, 21f, 16f, 19f, 19f, 16f, 18f, 14f, 15f, 10f, 15f, 8f, 18f, 5f, 19f, 3f, 16f, 4f, 10f, 7f, 8f)
                canvas.drawLine(7f, 10.5f, 7f, 14.5f, paint)
                canvas.drawLine(5f, 12.5f, 9f, 12.5f, paint)
                canvas.drawCircle(16f, 11f, 1f, fillPaintObj)
                canvas.drawCircle(18f, 14f, 1f, fillPaintObj)
            }
            "media" -> {
                canvas.drawRoundRect(3f, 3f, 18f, 21f, 2f, 2f, paint)
                canvas.drawCircle(13f, 8f, 1.4f, paint)
                path(canvas, 5f, 17f, 9f, 12f, 11f, 14f, 14f, 10f, 18f, 15f)
                path(canvas, 19f, 7f, 21f, 7f, 21f, 15f)
                canvas.drawLine(19f, 7f, 19f, 16f, paint)
                canvas.drawCircle(17.5f, 17f, 1.5f, paint)
                canvas.drawCircle(20f, 15.5f, 1.5f, paint)
            }
            "shapes" -> {
                path(canvas, 12f, 3f, 17f, 11f, 7f, 11f, 12f, 3f)
                canvas.drawRect(4f, 14f, 10f, 20f, paint)
                canvas.drawCircle(17f, 17f, 3.2f, paint)
            }
            "home" -> {
                path(canvas, 3f, 11f, 12f, 4f, 21f, 11f, 19f, 11f, 19f, 20f, 5f, 20f, 5f, 11f, 3f, 11f)
                canvas.drawRect(10f, 14f, 14f, 20f, paint)
            }
            "apps" -> for (y in 5..17 step 6) for (x in 5..17 step 6) {
                canvas.drawRoundRect(x.toFloat(), y.toFloat(), x + 3f, y + 3f, 0.8f, 0.8f, paint)
            }
            "pages" -> for (y in 4..14 step 10) for (x in 4..14 step 10) {
                canvas.drawRoundRect(x.toFloat(), y.toFloat(), x + 6f, y + 6f, 1f, 1f, paint)
            }
            "dock" -> {
                canvas.drawRoundRect(3f, 7f, 21f, 18f, 3f, 3f, paint)
                for (x in 7..17 step 5) canvas.drawCircle(x.toFloat(), 12.5f, 1f, paint)
                canvas.drawLine(7f, 21f, 17f, 21f, paint)
            }
            "recents" -> {
                reusableRect.set(4f, 4f, 20f, 20f)
                canvas.drawArc(reusableRect, -50f, 285f, false, paint)
                path(canvas, 17f, 3f, 20f, 4f, 20f, 8f)
                canvas.drawLine(12f, 7f, 12f, 12f, paint)
                canvas.drawLine(12f, 12f, 16f, 14f, paint)
            }
            "security" -> {
                path(canvas, 12f, 3f, 20f, 6f, 19f, 14f, 16f, 19f, 12f, 21f, 8f, 19f, 5f, 14f, 4f, 6f, 12f, 3f)
                path(canvas, 8f, 12f, 11f, 15f, 16f, 9f)
            }
            "warning" -> {
                path(canvas, 12f, 2.5f, 22f, 20.5f, 2f, 20.5f, close = true, strokeOrFill = fillPaintObj)
                auxPaint.apply {
                    color = 0xFF25212A.toInt()
                    strokeWidth = 2.1f
                    strokeCap = Paint.Cap.ROUND
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(12f, 8f, 12f, 14f, auxPaint)
                auxPaint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 17f, 1f, auxPaint)
            }
            "search" -> {
                canvas.drawCircle(10.5f, 10.5f, 6.5f, paint)
                canvas.drawLine(15.5f, 15.5f, 21f, 21f, paint)
            }
            "assistant" -> {
                path(canvas, 12f, 3f, 14f, 9f, 20f, 11f, 14f, 13f, 12f, 20f, 10f, 13f, 4f, 11f, 10f, 9f, 12f, 3f)
                canvas.drawCircle(19f, 5f, 1f, fillPaintObj)
                canvas.drawCircle(5f, 18f, 1f, fillPaintObj)
            }
            "store" -> {
                canvas.drawRoundRect(4f, 8f, 20f, 21f, 2f, 2f, paint)
                path(canvas, 8f, 8f, 8f, 5f, 10f, 3f, 14f, 3f, 16f, 5f, 16f, 8f)
                canvas.drawLine(9f, 12f, 9f, 14f, paint)
                canvas.drawLine(15f, 12f, 15f, 14f, paint)
            }
            "more" -> for (y in 5..19 step 7) canvas.drawCircle(12f, y.toFloat(), 1.5f, fillPaintObj)
            "icon_random_shape" -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 4f, 4f, paint)
                canvas.drawCircle(7f, 17f, 1.15f, fillPaintObj)
                canvas.drawCircle(12f, 12f, 1.15f, fillPaintObj)
                canvas.drawCircle(17f, 7f, 1.15f, fillPaintObj)
            }
            "android" -> {
                auxPaint.color = Color.WHITE
                auxPaint.style = Paint.Style.FILL
                reusablePath.apply {
                    reset()
                    moveTo(4f, 11f)
                    cubicTo(4f, 5.5f, 7.5f, 3f, 12f, 3f)
                    cubicTo(16.5f, 3f, 20f, 5.5f, 20f, 11f)
                    lineTo(20f, 18f)
                    cubicTo(20f, 20f, 18.5f, 21f, 17f, 21f)
                    lineTo(7f, 21f)
                    cubicTo(5.5f, 21f, 4f, 20f, 4f, 18f)
                    close()
                }
                canvas.drawPath(reusablePath, fillPaintObj)
                canvas.drawLine(7f, 5f, 5f, 2.5f, paint)
                canvas.drawLine(17f, 5f, 19f, 2.5f, paint)
                canvas.drawCircle(9f, 9f, 0.9f, auxPaint)
                canvas.drawCircle(15f, 9f, 0.9f, auxPaint)
                canvas.drawLine(2.5f, 11f, 2.5f, 17f, paint)
                canvas.drawLine(21.5f, 11f, 21.5f, 17f, paint)
                canvas.drawLine(8f, 19f, 8f, 22f, paint)
                canvas.drawLine(16f, 19f, 16f, 22f, paint)
            }
            "android_legacy" -> {
                canvas.save()
                canvas.translate(0f, -1f)
                paint.strokeWidth = 0.75f
                canvas.drawLine(7f, 4f, 5f, 1f, paint)
                canvas.drawLine(17f, 4f, 19f, 1f, paint)
                reusablePath.apply {
                    reset()
                    moveTo(4f, 12f)
                    cubicTo(4f, 6.2f, 7f, 3f, 12f, 3f)
                    cubicTo(17f, 3f, 20f, 6.2f, 20f, 12f)
                    close()
                }
                canvas.drawPath(reusablePath, fillPaintObj)
                canvas.drawRect(2.5f, 11.5f, 21.5f, 21.5f, fillPaintObj)
                auxPaint.color = 0x223F7D20
                auxPaint.style = Paint.Style.FILL
                path(canvas, 2.5f, 11.5f, 21.5f, 21.5f, 2.5f, 21.5f, close = true, strokeOrFill = auxPaint)
                auxPaint.color = Color.WHITE
                canvas.drawCircle(8.5f, 9f, 0.9f, auxPaint)
                canvas.drawCircle(15.5f, 9f, 0.9f, auxPaint)
                canvas.restore()
            }
            "settings" -> {
                canvas.drawCircle(12f, 12f, 6.5f, paint)
                canvas.drawCircle(12f, 12f, 2.5f, paint)
                for (index in 0 until 8) {
                    val angle = Math.toRadians(index * 45.0)
                    val cx = cos(angle).toFloat()
                    val cy = sin(angle).toFloat()
                    canvas.drawLine(12f + 7.2f * cx, 12f + 7.2f * cy, 12f + 9.5f * cx, 12f + 9.5f * cy, paint)
                }
            }
            "info" -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawCircle(12f, 7.2f, 1f, fillPaintObj)
                canvas.drawRoundRect(11f, 10f, 13f, 18f, 1f, 1f, fillPaintObj)
            }
            "reset" -> {
                reusableRect.set(4f, 4f, 20f, 20f)
                canvas.drawArc(reusableRect, -45f, 300f, false, paint)
                path(canvas, 17f, 3f, 20f, 4f, 20f, 8f)
            }
            "font" -> {
                paint.style = Paint.Style.FILL
                paint.textSize = 15f
                paint.typeface = fontTypeface
                canvas.drawText("Aa", 1f, 18f, paint)
                paint.style = Paint.Style.STROKE
            }
            "icon_appearance" -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 3f, 3f, paint)
                canvas.drawCircle(10f, 14f, 4.2f, paint)
                canvas.drawLine(13f, 11f, 18f, 6f, paint)
            }
            "widgets" -> {
                canvas.drawRoundRect(3f, 3f, 10f, 10f, 2f, 2f, paint)
                canvas.drawRoundRect(14f, 3f, 21f, 10f, 2f, 2f, paint)
                canvas.drawRoundRect(3f, 14f, 10f, 21f, 2f, 2f, paint)
                path(canvas, 14f, 17f, 17.5f, 14f, 21f, 17f, 17.5f, 21f, 14f, 17f)
            }
            "folder" -> path(canvas, 3f, 7f, 3f, 5f, 10f, 5f, 12f, 8f, 21f, 8f, 20f, 19f, 4f, 19f, 3f, 7f)
            "animation" -> {
                canvas.drawRoundRect(9f, 4f, 20f, 20f, 2f, 2f, paint)
                canvas.drawLine(3f, 8f, 6f, 8f, paint)
                canvas.drawLine(2f, 12f, 6f, 12f, paint)
                canvas.drawLine(3f, 16f, 6f, 16f, paint)
            }
            "back" -> path(canvas, 15f, 4f, 7f, 12f, 15f, 20f)
            "arrow_back" -> {
                canvas.drawLine(20f, 12f, 5f, 12f, paint)
                path(canvas, 12f, 5f, 5f, 12f, 12f, 19f)
            }
            "next" -> path(canvas, 9f, 4f, 17f, 12f, 9f, 20f)
            "handle" -> for (y in 7..17 step 5) for (x in 8..16 step 8) {
                canvas.drawCircle(x.toFloat(), y.toFloat(), 1.2f, fillPaintObj)
            }
            "lock", "unlock" -> {
                canvas.drawRoundRect(4f, 10f, 20f, 21f, 2f, 2f, paint)
                if (glyph == "lock") {
                    path(canvas, 7f, 10f, 7f, 7f, 9f, 4f, 15f, 4f, 17f, 7f, 17f, 10f)
                } else {
                    path(canvas, 7f, 10f, 7f, 7f, 9f, 4f, 14f, 4f, 16f, 6f)
                }
                canvas.drawCircle(12f, 15f, 1f, paint)
                canvas.drawLine(12f, 16f, 12f, 18f, paint)
            }
            "add", "widget", "remove" -> {
                canvas.drawRoundRect(3f, 3f, 21f, 21f, 3f, 3f, paint)
                canvas.drawLine(7f, 12f, 17f, 12f, paint)
                if (glyph != "remove") canvas.drawLine(12f, 7f, 12f, 17f, paint)
                if (glyph == "widget") canvas.drawLine(3f, 9f, 8f, 9f, paint)
            }
            "add_plain" -> {
                canvas.drawLine(12f, 4f, 12f, 20f, paint)
                canvas.drawLine(4f, 12f, 20f, 12f, paint)
            }
            "folder_add" -> {
                path(canvas, 3f, 7f, 3f, 5f, 10f, 5f, 12f, 8f, 21f, 8f, 20f, 19f, 4f, 19f, 3f, 7f)
                canvas.drawLine(12f, 10f, 12f, 17f, paint)
                canvas.drawLine(8.5f, 13.5f, 15.5f, 13.5f, paint)
            }
            "page_add" -> {
                path(canvas, 6f, 3f, 15f, 3f, 19f, 7f, 19f, 21f, 6f, 21f, 6f, 3f)
                path(canvas, 15f, 3f, 15f, 7f, 19f, 7f)
                canvas.drawLine(12f, 11f, 12f, 18f, paint)
                canvas.drawLine(8.5f, 14.5f, 15.5f, 14.5f, paint)
            }
            "up" -> path(canvas, 5f, 15f, 12f, 8f, 19f, 15f)
            "chevron_up" -> path(canvas, 6f, 15f, 12f, 9f, 18f, 15f)
            "chevron_down" -> path(canvas, 6f, 9f, 12f, 15f, 18f, 9f)
            "resize" -> {
                canvas.drawLine(6f, 18f, 18f, 6f, paint)
                path(canvas, 12f, 6f, 18f, 6f, 18f, 12f)
                path(canvas, 6f, 12f, 6f, 18f, 12f, 18f)
            }
        }
        canvas.restore()
    }

    private fun path(
        canvas: Canvas,
        vararg points: Float,
        close: Boolean = false,
        strokeOrFill: Paint = paint,
    ) {
        if (points.size < 4) return
        reusablePath.reset()
        reusablePath.moveTo(points[0], points[1])
        var index = 2
        while (index + 1 < points.size) {
            reusablePath.lineTo(points[index], points[index + 1])
            index += 2
        }
        if (close) reusablePath.close()
        canvas.drawPath(reusablePath, strokeOrFill)
    }
}
