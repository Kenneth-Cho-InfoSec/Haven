package com.arcturus.haven.launcher.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.Switch
import androidx.core.graphics.withClip
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.arcturus.haven.launcher.core.model.IconShapes

internal class CheckerboardDrawable(
    private val cellSizePx: Float,
    private val firstColor: Int = 0xFF2F385B.toInt(),
    private val secondColor: Int = 0xFF485174.toInt(),
) : Drawable() {
    private val paint = Paint().apply { isAntiAlias = false }
    private var screenOriginY = 0f

    fun alignToScreenY(originY: Float) {
        if (screenOriginY == originY) return
        screenOriginY = originY
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val cell = cellSizePx.coerceAtLeast(1f)
        val left = (bounds.left / cell).toInt() * cell
        val firstScreenY = ((bounds.top + screenOriginY) / cell).toInt() * cell
        var y = firstScreenY - screenOriginY
        var row = (firstScreenY / cell).toInt()
        while (y < bounds.bottom) {
            var x = left
            var column = (left / cell).toInt()
            while (x < bounds.right) {
                paint.color = if ((row + column) and 1 == 0) firstColor else secondColor
                canvas.drawRect(x, y, x + cell, y + cell, paint)
                x += cell
                column++
            }
            y += cell
            row++
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** The reference page keeps its checkerboard header full bleed, with a white settings surface below it. */
internal class IconAppearanceBackdropDrawable(
    private val checkerHeightPx: Float,
    cellSizePx: Float,
) : Drawable() {
    private val checkerboard = CheckerboardDrawable(cellSizePx)
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    fun alignToScreenY(originY: Float) {
        checkerboard.alignToScreenY(originY)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        checkerboard.bounds = bounds
        val split = (bounds.top + checkerHeightPx).coerceAtMost(bounds.bottom.toFloat())
        canvas.withClip(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), split) {
            checkerboard.draw(canvas)
        }
        if (split < bounds.bottom) {
            canvas.drawRect(bounds.left.toFloat(), split, bounds.right.toFloat(), bounds.bottom.toFloat(), whitePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        checkerboard.alpha = alpha
        whitePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        checkerboard.colorFilter = colorFilter
        whitePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

internal class IconShapePreviewView @JvmOverloads constructor(
    context: Context,
    private val shapeKey: String = "circle",
    private var isSelected: Boolean = false,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tile = RectF()
    private val shape = RectF()

    fun setShapeSelected(selected: Boolean) {
        if (isSelected == selected) return
        isSelected = selected
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (59 * density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = 59f * density
        val inset = (width - side) / 2f
        tile.set(inset, inset, inset + side, inset + side)
        tilePaint.color = if (isSelected) 0xFFE1ECFF.toInt() else 0xFFF3F5F8.toInt()
        canvas.drawRoundRect(tile, 11f * density, 11f * density, tilePaint)
        val shapeInset = if (shapeKey == "circle" || shapeKey == "soft_circle") 14f * density else 11f * density
        shape.set(tile.left + shapeInset, tile.top + shapeInset, tile.right - shapeInset, tile.bottom - shapeInset)
        shapePaint.color = if (isSelected) 0xFF202124.toInt() else 0xFFA9ACAE.toInt()
        canvas.drawPath(IconShapes.path(shapeKey, shape), shapePaint)
    }
}

/** A launcher-owned switch gives this page the same saturated track and compact white thumb as Smart. */
internal class IconAppearanceSwitch @JvmOverloads constructor(
    context: Context,
    checkedInitially: Boolean = false,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var changeListener: ((Boolean) -> Unit)? = null
    private var thumbProgress = if (checkedInitially) 1f else 0f
    private var thumbAnimator: ValueAnimator? = null

    var isChecked: Boolean = checkedInitially
        set(value) {
            if (field == value) return
            field = value
            thumbAnimator?.cancel()
            thumbAnimator = ValueAnimator.ofFloat(thumbProgress, if (value) 1f else 0f).apply {
                duration = 160L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    thumbProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            changeListener?.invoke(value)
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumWidth = (48 * density).toInt()
        minimumHeight = (48 * density).toInt()
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        changeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (48 * density).toInt()
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackWidth = 36f * density
        val trackHeight = 16f * density
        val left = (width - trackWidth) / 2f
        val top = (height - trackHeight) / 2f
        val right = left + trackWidth
        val bottom = top + trackHeight
        trackPaint.color = if (isChecked) 0xFF0066FF.toInt() else 0xFFDADCE0.toInt()
        canvas.drawRoundRect(left, top, right, bottom, trackHeight / 2f, trackHeight / 2f, trackPaint)

        val radius = 10f * density
        val thumbCenterX = left + radius + (right - left - radius * 2f) * thumbProgress
        thumbPaint.setShadowLayer(1.1f * density, 0f, 1f * density, 0x33000000)
        canvas.drawCircle(thumbCenterX, height / 2f, radius, thumbPaint)
        thumbPaint.clearShadowLayer()
    }

    override fun performClick(): Boolean {
        isChecked = !isChecked
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Switch::class.java.name
        info.isCheckable = true
        AccessibilityNodeInfoCompat.wrap(info).setChecked(
            if (isChecked) AccessibilityNodeInfoCompat.CHECKED_STATE_TRUE else AccessibilityNodeInfoCompat.CHECKED_STATE_FALSE,
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }
}
