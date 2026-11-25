package com.arcturus.haven.launcher.core.model

import android.graphics.Path
import android.graphics.RectF

data class IconShapeOption(val key: String, val label: String)

object IconShapes {
    val options = listOf(
        IconShapeOption("square", "Square"),
        IconShapeOption("round", "Rounded square"),
        IconShapeOption("squircle", "Squircle"),
        IconShapeOption("soft_circle", "Soft circle"),
        IconShapeOption("circle", "Circle"),
        IconShapeOption("pentagon", "Pentagon"),
        IconShapeOption("teardrop", "Teardrop"),
        IconShapeOption("scoop", "Scoop"),
        IconShapeOption("leaf", "Leaf"),
        IconShapeOption("blob", "Organic"),
        IconShapeOption("tilted_oval", "Tilted oval"),
        IconShapeOption("hexagon", "Hexagon"),
        IconShapeOption("rough_squircle", "Organic square"),
        IconShapeOption("cut_square", "Cut corner"),
        IconShapeOption("soft_square", "Soft square"),
        IconShapeOption("wave", "Wave"),
        IconShapeOption("round_rect_custom", "Rounded rectangle"),
        IconShapeOption("corner_sweep", "Swept corner"),
    )

    fun path(key: String, bounds: RectF): Path = Path().apply {
        val l = bounds.left
        val t = bounds.top
        val r = bounds.right
        val b = bounds.bottom
        val w = bounds.width()
        val h = bounds.height()
        fun round(factor: Float) = addRoundRect(bounds, w * factor, h * factor, Path.Direction.CW)

        when (key) {
            "square" -> round(0.07f)
            "round" -> round(0.29f)
            "squircle", "soft_square" -> round(0.34f)
            "soft_circle" -> round(0.46f)
            "oval" -> addOval(RectF(l + w * 0.08f, t, r - w * 0.08f, b), Path.Direction.CW)
            "tilted_oval" -> {
                moveTo(l + w * 0.31f, b - h * 0.03f)
                cubicTo(l + w * 0.08f, b - h * 0.1f, l + w * 0.18f, t + h * 0.54f, l + w * 0.43f, t + h * 0.2f)
                cubicTo(l + w * 0.66f, t - h * 0.06f, r - w * 0.02f, t + h * 0.1f, r - w * 0.01f, t + h * 0.34f)
                cubicTo(r, t + h * 0.58f, l + w * 0.58f, b + h * 0.03f, l + w * 0.31f, b - h * 0.03f)
                close()
            }
            "hexagon", "rounded_hexagon" -> {
                val inset = if (key == "rounded_hexagon") 0.22f else 0.08f
                moveTo(l + w * inset, t)
                lineTo(r - w * inset, t)
                lineTo(r, t + h * 0.5f)
                lineTo(r - w * inset, b)
                lineTo(l + w * inset, b)
                lineTo(l, t + h * 0.5f)
                close()
            }
            "pentagon" -> {
                moveTo(l + w * 0.5f, t)
                lineTo(r, t + h * 0.38f)
                lineTo(r - w * 0.18f, b)
                lineTo(l + w * 0.18f, b)
                lineTo(l, t + h * 0.38f)
                close()
            }
            "teardrop", "scoop" -> {
                fun edgeX(offsetFactor: Float) = if (key == "teardrop") l + w * offsetFactor else r - w * offsetFactor
                moveTo(edgeX(0.5f), t)
                cubicTo(edgeX(0.98f), t, edgeX(1f), t + h * 0.22f, edgeX(1f), t + h * 0.48f)
                lineTo(edgeX(1f), b - h * 0.05f)
                cubicTo(edgeX(1f), b, edgeX(0.8f), b, edgeX(0.48f), b)
                cubicTo(edgeX(0.1f), b, edgeX(0f), b - h * 0.2f, edgeX(0f), t + h * 0.48f)
                cubicTo(edgeX(0f), t + h * 0.22f, edgeX(0.02f), t, edgeX(0.5f), t)
                close()
            }
            "leaf" -> {
                moveTo(l + w * 0.5f, t)
                cubicTo(r - w * 0.04f, t + h * 0.1f, r, t + h * 0.36f, r, t + h * 0.58f)
                cubicTo(r, b - h * 0.07f, r - w * 0.18f, b, l + w * 0.44f, b)
                cubicTo(l + w * 0.08f, b, l, b - h * 0.2f, l, t + h * 0.45f)
                cubicTo(l, t + h * 0.18f, l + w * 0.17f, t + h * 0.03f, l + w * 0.5f, t)
                close()
            }
            "blob" -> {
                moveTo(l + w * 0.5f, t + h * 0.02f)
                cubicTo(r - w * 0.06f, t - h * 0.01f, r + w * 0.03f, t + h * 0.29f, r - w * 0.02f, t + h * 0.52f)
                cubicTo(r + w * 0.04f, b - h * 0.06f, r - w * 0.24f, b + h * 0.02f, l + w * 0.52f, b)
                cubicTo(l + w * 0.12f, b + h * 0.02f, l - w * 0.02f, b - h * 0.18f, l + w * 0.02f, t + h * 0.48f)
                cubicTo(l - w * 0.02f, t + h * 0.16f, l + w * 0.18f, t + h * 0.03f, l + w * 0.5f, t + h * 0.02f)
                close()
            }
            "rough_squircle" -> {
                moveTo(l + w * 0.23f, t + h * 0.03f)
                cubicTo(l + w * 0.43f, t - h * 0.02f, r - w * 0.18f, t + h * 0.01f, r - w * 0.08f, t + h * 0.2f)
                cubicTo(r + w * 0.02f, t + h * 0.44f, r, b - h * 0.2f, r - w * 0.17f, b - h * 0.08f)
                cubicTo(r - w * 0.36f, b + h * 0.02f, l + w * 0.13f, b, l + w * 0.05f, b - h * 0.2f)
                cubicTo(l - w * 0.02f, b - h * 0.42f, l + w * 0.04f, t + h * 0.1f, l + w * 0.23f, t + h * 0.03f)
                close()
            }
            "round_rect_custom" -> {
                moveTo(l + w * 0.13f, t + h * 0.04f)
                cubicTo(l + w * 0.32f, t + h * 0.11f, r - w * 0.28f, t - h * 0.04f, r - w * 0.1f, t + h * 0.04f)
                cubicTo(r + w * 0.02f, t + h * 0.16f, r, b - h * 0.18f, r - w * 0.1f, b - h * 0.02f)
                cubicTo(r - w * 0.25f, b + h * 0.02f, l + w * 0.29f, b - h * 0.12f, l + w * 0.1f, b - h * 0.02f)
                cubicTo(l - w * 0.02f, b - h * 0.16f, l, t + h * 0.18f, l + w * 0.13f, t + h * 0.04f)
                close()
            }
            "corner_sweep" -> {
                moveTo(l + w * 0.08f, t)
                lineTo(r - w * 0.35f, t)
                cubicTo(r - w * 0.13f, t, r, t + h * 0.22f, r, t + h * 0.42f)
                lineTo(r, b)
                lineTo(l + w * 0.08f, b)
                cubicTo(l, b, l, b - h * 0.12f, l, b - h * 0.25f)
                lineTo(l, t + h * 0.12f)
                cubicTo(l, t + h * 0.04f, l + w * 0.02f, t, l + w * 0.08f, t)
                close()
            }
            "rounded_diamond" -> {
                moveTo(l + w * 0.5f, t)
                cubicTo(l + w * 0.64f, t, r, t + h * 0.36f, r, t + h * 0.5f)
                cubicTo(r, t + h * 0.64f, l + w * 0.64f, b, l + w * 0.5f, b)
                cubicTo(l + w * 0.36f, b, l, t + h * 0.64f, l, t + h * 0.5f)
                cubicTo(l, t + h * 0.36f, l + w * 0.36f, t, l + w * 0.5f, t)
                close()
            }
            "squircle_tall" -> addRoundRect(RectF(l + w * 0.08f, t, r - w * 0.08f, b), w * 0.3f, w * 0.3f, Path.Direction.CW)
            "cut_square" -> {
                moveTo(l + w * 0.18f, t)
                lineTo(r - w * 0.1f, t)
                cubicTo(r, t, r, t + h * 0.1f, r, t + h * 0.22f)
                lineTo(r, b - h * 0.18f)
                cubicTo(r, b - h * 0.04f, r - w * 0.1f, b, r - w * 0.22f, b)
                lineTo(l + w * 0.16f, b)
                cubicTo(l, b, l, b - h * 0.12f, l, b - h * 0.24f)
                lineTo(l, t + h * 0.16f)
                cubicTo(l, t + h * 0.04f, l + w * 0.08f, t, l + w * 0.18f, t)
                close()
            }
            "wave" -> {
                moveTo(l + w * 0.12f, t + h * 0.06f)
                cubicTo(l + w * 0.33f, t + h * 0.1f, r - w * 0.32f, t - h * 0.02f, r - w * 0.12f, t + h * 0.06f)
                cubicTo(r - w * 0.02f, t + h * 0.1f, r, t + h * 0.2f, r, t + h * 0.3f)
                lineTo(r, b - h * 0.28f)
                cubicTo(r, b - h * 0.08f, r - w * 0.12f, b + h * 0.01f, r - w * 0.3f, b)
                cubicTo(r - w * 0.48f, b - h * 0.03f, l + w * 0.36f, b - h * 0.02f, l + w * 0.18f, b)
                cubicTo(l + w * 0.05f, b - h * 0.07f, l, b - h * 0.18f, l, b - h * 0.3f)
                lineTo(l, t + h * 0.26f)
                cubicTo(l, t + h * 0.12f, l + w * 0.03f, t + h * 0.07f, l + w * 0.12f, t + h * 0.06f)
                close()
            }
            else -> addOval(bounds, Path.Direction.CW)
        }
    }
}
