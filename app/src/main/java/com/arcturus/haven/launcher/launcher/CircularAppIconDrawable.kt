package com.arcturus.haven.launcher.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.arcturus.haven.launcher.core.model.IconShapes
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal const val HOME_ICON_OPTICAL_SCALE = 0.90f

/** Normalizes square and irregular artwork while leaving icons that already have a circular silhouette alone. */
internal open class PreRenderedIconDrawable(private val bitmap: Bitmap) : Drawable() {
    private var paint: Paint = CircularAppIconDrawable.sharedIconPaint

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty || bitmap.isRecycled) return
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        if (paint === CircularAppIconDrawable.sharedIconPaint) {
            paint = Paint(CircularAppIconDrawable.sharedIconPaint)
        }
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (paint === CircularAppIconDrawable.sharedIconPaint) {
            paint = Paint(CircularAppIconDrawable.sharedIconPaint)
        }
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = CircularAppIconDrawable.ICON_BITMAP_SIZE
    override fun getIntrinsicHeight(): Int = CircularAppIconDrawable.ICON_BITMAP_SIZE
}

internal class CircularAppIconDrawable(
    artwork: Drawable,
    fallbackColor: Int,
    overrideBackgroundColor: Int? = null,
    shape: String = "circle",
    analysisCacheKey: String? = null,
) : PreRenderedIconDrawable(
    renderAndCache(artwork, analysisCacheKey, fallbackColor, overrideBackgroundColor, shape),
) {
    private data class AnalyzedArtwork(
        val bitmap: Bitmap,
        val alreadyCircular: Boolean,
        val squareArtwork: Boolean,
        val adaptiveArtwork: Boolean,
        val sampledAccent: Int?,
    )

    companion object {
        private val renderCacheBudgetKb =
            (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceIn(24 * 1024, 64 * 1024)

        private val analysisCache = object : LruCache<String, AnalyzedArtwork>(16 * 1024) {
            override fun sizeOf(key: String, value: AnalyzedArtwork): Int =
                (value.bitmap.byteCount / 1024).coerceAtLeast(1)
        }
        private val renderCache = object : LruCache<String, Bitmap>(renderCacheBudgetKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
        }

        internal val sharedIconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        const val ICON_BITMAP_SIZE = 256
        private const val MAX_ADAPTIVE_RASTER_SIZE = 384
        private const val ARTWORK_SCALE = 0.90f
        private const val MIN_CROP_ALPHA = 16
        private const val CROP_PADDING_PX = 2
        private const val FILLED_CORNER_THRESHOLD = 0.65f
        private const val SILHOUETTE_RAYS = 48
        private const val SILHOUETTE_ALPHA = 128
        private const val MIN_CIRCLE_RADIUS = 0.36f
        private const val MIN_VALID_RAY_FRACTION = 0.82f
        private const val MAX_CIRCLE_RADIUS_VARIATION = 0.045f
        private const val HUE_BUCKETS = 18
        private const val HUE_BUCKET_DEGREES = 20f
        private const val MIN_SAMPLE_ALPHA = 200
        private const val MIN_SAMPLE_SATURATION = 0.24f
        private const val MIN_SAMPLE_BRIGHTNESS = 0.18f
        private const val SAMPLE_STEP = 3
        private const val BACKGROUND_SATURATION = 0.52f
        private const val BACKGROUND_BRIGHTNESS = 0.72f

        private val COS_TABLE = FloatArray(SILHOUETTE_RAYS) { ray ->
            cos((2.0 * Math.PI * ray / SILHOUETTE_RAYS).toFloat())
        }
        private val SIN_TABLE = FloatArray(SILHOUETTE_RAYS) { ray ->
            sin((2.0 * Math.PI * ray / SILHOUETTE_RAYS).toFloat())
        }

        internal fun clearAnalysisCache() {
            analysisCache.evictAll()
            renderCache.evictAll()
        }

        internal fun activityCacheKey(component: ComponentName): String =
            "activity:${component.flattenToString()}"

        internal fun getCachedBitmap(cacheKey: String, color: Int, shape: String): Bitmap? =
            renderCache.get(renderCacheKey(cacheKey, color, shape))

        internal fun fromBitmap(bitmap: Bitmap): Drawable = PreRenderedIconDrawable(bitmap)

        internal fun prewarm(artwork: Drawable, cacheKey: String, accent: Int, shape: String) {
            renderAndCache(artwork, cacheKey, accent, accent, shape).prepareToDraw()
        }

        internal fun renderAndCache(
            artwork: Drawable,
            cacheKey: String?,
            fallbackColor: Int,
            overrideBackgroundColor: Int?,
            shape: String,
        ): Bitmap {
            if (cacheKey != null && overrideBackgroundColor != null) {
                renderCache.get(renderCacheKey(cacheKey, overrideBackgroundColor, shape))?.let { return it }
            }
            val analyzed = cachedArtwork(artwork, cacheKey, overrideBackgroundColor == null)
            val circleColor = overrideBackgroundColor ?: analyzed.sampledAccent ?: fallbackColor
            val fullKey = cacheKey?.let { renderCacheKey(it, circleColor, shape) }
            if (fullKey != null) renderCache.get(fullKey)?.let { return it }
            return renderIconBitmap(analyzed, circleColor, shape).also { rendered ->
                rendered.prepareToDraw()
                if (fullKey != null) renderCache.put(fullKey, rendered)
            }
        }

        private fun renderCacheKey(cacheKey: String, color: Int, shape: String) = "$cacheKey|$shape|$color"

        private fun centeredSquare(centerX: Float, centerY: Float, size: Float): RectF {
            val half = size / 2f
            return RectF(centerX - half, centerY - half, centerX + half, centerY + half)
        }

        private fun renderIconBitmap(analyzed: AnalyzedArtwork, circleColor: Int, shape: String): Bitmap {
            val bitmap = createBitmap(ICON_BITMAP_SIZE, ICON_BITMAP_SIZE)
            val canvas = Canvas(bitmap)
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val diameter = ICON_BITMAP_SIZE.toFloat()
            val center = diameter / 2f
            val maskBounds = centeredSquare(center, center, diameter * HOME_ICON_OPTICAL_SCALE)
            val clipPath = IconShapes.path(shape, maskBounds)
            canvas.withClip(clipPath) {
                if ((analyzed.alreadyCircular && shape == "circle") || analyzed.squareArtwork || analyzed.adaptiveArtwork) {
                    drawBitmap(analyzed.bitmap, null, maskBounds, imagePaint)
                } else {
                    drawPath(clipPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = circleColor })
                    drawBitmap(analyzed.bitmap, null, centeredSquare(center, center, diameter * ARTWORK_SCALE), imagePaint)
                }
            }
            return bitmap
        }

        private fun cachedArtwork(source: Drawable, cacheKey: String?, needSampledAccent: Boolean): AnalyzedArtwork {
            if (cacheKey.isNullOrBlank()) return analyzeArtwork(source, needSampledAccent)
            analysisCache.get(cacheKey)?.let { existing ->
                if (!needSampledAccent || existing.sampledAccent != null) return existing
            }
            return analyzeArtwork(source, needSampledAccent).also { analysisCache.put(cacheKey, it) }
        }

        private fun analyzeArtwork(source: Drawable, needSampledAccent: Boolean): AnalyzedArtwork {
            val bitmap = rasterize(source)
            var pixels = bitmap.copyPixels()
            val filledCorners = hasFilledCorners(bitmap.width, bitmap.height, pixels)
            val circular = !filledCorners && hasCircularSilhouette(bitmap.width, bitmap.height, pixels)
            val adaptive = containsAdaptiveIcon(source)
            val normalized = if (circular || filledCorners || adaptive) bitmap else trimTransparentPadding(bitmap, pixels)
            if (normalized !== bitmap) pixels = normalized.copyPixels()
            return AnalyzedArtwork(
                bitmap = normalized,
                alreadyCircular = circular,
                squareArtwork = filledCorners || hasFilledCorners(normalized.width, normalized.height, pixels),
                adaptiveArtwork = adaptive,
                sampledAccent = if (needSampledAccent) sampledAccent(normalized.width, normalized.height, pixels) else null,
            )
        }

        private fun rasterize(source: Drawable): Bitmap {
            val clone = source.constantState?.newDrawable()?.mutate()
            val drawable = clone ?: source
            val rasterSize = if (containsAdaptiveIcon(drawable)) {
                maxOf(ICON_BITMAP_SIZE, drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtMost(MAX_ADAPTIVE_RASTER_SIZE)
            } else {
                ICON_BITMAP_SIZE
            }
            val raster = createBitmap(rasterSize, rasterSize)
            if (clone != null) {
                drawable.setBounds(0, 0, rasterSize, rasterSize)
                drawable.draw(Canvas(raster))
            } else {
                synchronized(source) {
                    val previousBounds = Rect(source.bounds)
                    source.setBounds(0, 0, rasterSize, rasterSize)
                    source.draw(Canvas(raster))
                    source.bounds = previousBounds
                }
            }
            if (rasterSize == ICON_BITMAP_SIZE) return raster
            val bitmap = createBitmap(ICON_BITMAP_SIZE, ICON_BITMAP_SIZE)
            Canvas(bitmap).drawBitmap(
                raster,
                null,
                Rect(0, 0, ICON_BITMAP_SIZE, ICON_BITMAP_SIZE),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            raster.recycle()
            return bitmap
        }

        private fun Bitmap.copyPixels(): IntArray = IntArray(width * height).also {
            getPixels(it, 0, width, 0, 0, width, height)
        }

        private fun hasFilledCorners(width: Int, height: Int, pixels: IntArray): Boolean {
            val cornerSize = width / 8
            if (cornerSize <= 0) return false
            var alphaTotal = 0L
            var samples = 0
            val rightStart = width - cornerSize
            val bottomStart = height - cornerSize
            for (corner in 0 until 4) {
                val left = if (corner == 0 || corner == 2) 0 else rightStart
                val top = if (corner < 2) 0 else bottomStart
                for (y in top until top + cornerSize) {
                    val rowOffset = y * width
                    for (x in left until left + cornerSize) {
                        alphaTotal += pixels[rowOffset + x] ushr 24
                        samples++
                    }
                }
            }
            return samples > 0 && alphaTotal / (samples * 255f) >= FILLED_CORNER_THRESHOLD
        }

        private fun trimTransparentPadding(bitmap: Bitmap, pixels: IntArray): Bitmap {
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            for (y in 0 until bitmap.height) {
                val rowOffset = y * bitmap.width
                for (x in 0 until bitmap.width) {
                    if (pixels[rowOffset + x] ushr 24 < MIN_CROP_ALPHA) continue
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            if (right < left || bottom < top) return bitmap
            val cropLeft = (left - CROP_PADDING_PX).coerceAtLeast(0)
            val cropTop = (top - CROP_PADDING_PX).coerceAtLeast(0)
            val cropRight = (right + CROP_PADDING_PX + 1).coerceAtMost(bitmap.width)
            val cropBottom = (bottom + CROP_PADDING_PX + 1).coerceAtMost(bitmap.height)
            if (cropLeft == 0 && cropTop == 0 && cropRight == bitmap.width && cropBottom == bitmap.height) return bitmap
            return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
                .also { bitmap.recycle() }
        }

        private fun containsAdaptiveIcon(drawable: Drawable): Boolean = when (drawable) {
            is AdaptiveIconDrawable -> true
            is InsetDrawable -> drawable.drawable?.let(::containsAdaptiveIcon) == true
            is LayerDrawable -> (0 until drawable.numberOfLayers).any { containsAdaptiveIcon(drawable.getDrawable(it)) }
            else -> false
        }

        private fun hasCircularSilhouette(width: Int, height: Int, pixels: IntArray): Boolean {
            val centerX = (width - 1) / 2f
            val centerY = (height - 1) / 2f
            val maxRadius = minOf(width, height) * 0.53f
            val step = 0.75f
            val radii = FloatArray(SILHOUETTE_RAYS)
            var validCount = 0
            for (ray in 0 until SILHOUETTE_RAYS) {
                val dx = COS_TABLE[ray]
                val dy = SIN_TABLE[ray]
                var lastOpaqueRadius = 0f
                var radius = 0f
                while (radius <= maxRadius) {
                    val x = (centerX + dx * radius).toInt().coerceIn(0, width - 1)
                    val y = (centerY + dy * radius).toInt().coerceIn(0, height - 1)
                    if (pixels[y * width + x] ushr 24 >= SILHOUETTE_ALPHA) lastOpaqueRadius = radius
                    radius += step
                }
                if (lastOpaqueRadius >= width * MIN_CIRCLE_RADIUS) radii[validCount++] = lastOpaqueRadius
            }
            if (validCount < SILHOUETTE_RAYS * MIN_VALID_RAY_FRACTION) return false
            radii.sort(0, validCount)
            val lower = radii[(validCount * 0.10f).toInt()]
            val upper = radii[(validCount * 0.90f).toInt().coerceAtMost(validCount - 1)]
            return upper - lower <= width * MAX_CIRCLE_RADIUS_VARIATION
        }

        private fun sampledAccent(width: Int, height: Int, pixels: IntArray): Int? {
            val hueCounts = IntArray(HUE_BUCKETS)
            val hsv = FloatArray(3)
            for (y in 0 until height step SAMPLE_STEP) {
                val rowOffset = y * width
                for (x in 0 until width step SAMPLE_STEP) {
                    val pixel = pixels[rowOffset + x]
                    if (Color.alpha(pixel) < MIN_SAMPLE_ALPHA) continue
                    Color.colorToHSV(pixel, hsv)
                    if (hsv[1] < MIN_SAMPLE_SATURATION || hsv[2] < MIN_SAMPLE_BRIGHTNESS) continue
                    val bucket = (floor(hsv[0] / HUE_BUCKET_DEGREES).toInt()).coerceIn(0, HUE_BUCKETS - 1)
                    hueCounts[bucket]++
                }
            }
            val dominant = hueCounts.indices.maxByOrNull(hueCounts::get)?.takeIf { hueCounts[it] > 0 } ?: return null
            val hue = dominant * HUE_BUCKET_DEGREES + HUE_BUCKET_DEGREES / 2f
            return Color.HSVToColor(floatArrayOf(hue, BACKGROUND_SATURATION, BACKGROUND_BRIGHTNESS))
        }
    }
}
