package org.watchshark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * Bottom bar with a live frosted-glass backdrop: samples the content
 * scrolling underneath, blurs it on the CPU at tiny scale (cheap), and
 * lays translucent AMOLED black over it. Toggleable via [blurEnabled].
 */
class BlurBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    companion object {
        @Volatile
        var blurEnabled: Boolean = true
        private const val DOWNSCALE = 10
        private const val CAPTURE_MIN_MS = 150L
        // AMOLED-friendly scrim: translucent black over the blur so the
        // frosted content stays visible instead of drowning in black.
        private const val SCRIM = 0x80000000
    }

    /** Content view sampled from behind this bar (the fragment container). */
    var target: View? = null

    private var cached: Bitmap? = null
    private var lastCapture = 0L
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = SCRIM.toInt() }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        drawBlurBehind(canvas)
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        cached?.recycle()
        cached = null
        super.onDetachedFromWindow()
    }

    private fun drawBlurBehind(canvas: Canvas) {
        val t = target
        if (!blurEnabled || t == null || t.width <= 0 || width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        var bmp = cached
        if (bmp == null || bmp.isRecycled || now - lastCapture > CAPTURE_MIN_MS) {
            bmp = renderBlurred(t)
            cached?.recycle()
            cached = bmp
            lastCapture = now
        }
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(
                bmp, null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()), bitmapPaint
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        }
    }

    private fun renderBlurred(t: View): Bitmap? {
        return try {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val w = (width / DOWNSCALE).coerceAtLeast(1)
            val h = (height / DOWNSCALE).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(1f / DOWNSCALE, 1f / DOWNSCALE)
            c.translate(-loc[0].toFloat(), -loc[1].toFloat())
            t.draw(c)
            boxBlur(bmp, 3)
            boxBlur(bmp, 3)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** Two separable box-blur passes stand in for gaussian at this scale. */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        if (w <= 1 || h <= 1) return
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val div = radius * 2 + 1
        // Horizontal pass.
        for (y in 0 until h) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (x in -radius..radius) {
                val p = src[y * w + x.coerceIn(0, w - 1)]
                a += p ushr 24; r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (x in 0 until w) {
                tmp[y * w + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
                val xOut = (x - radius).coerceIn(0, w - 1)
                val xIn = (x + radius + 1).coerceIn(0, w - 1)
                val po = src[y * w + xOut]
                val pi = src[y * w + xIn]
                a += (pi ushr 24) - (po ushr 24)
                r += ((pi shr 16) and 0xFF) - ((po shr 16) and 0xFF)
                g += ((pi shr 8) and 0xFF) - ((po shr 8) and 0xFF)
                b += (pi and 0xFF) - (po and 0xFF)
            }
        }
        // Vertical pass.
        for (x in 0 until w) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (y in -radius..radius) {
                val p = tmp[y.coerceIn(0, h - 1) * w + x]
                a += p ushr 24; r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (y in 0 until h) {
                src[y * w + x] = (a / div shl 24) or (r / div shl 16) or (g / div shl 8) or (b / div)
                val yOut = (y - radius).coerceIn(0, h - 1)
                val yIn = (y + radius + 1).coerceIn(0, h - 1)
                val po = tmp[yOut * w + x]
                val pi = tmp[yIn * w + x]
                a += (pi ushr 24) - (po ushr 24)
                r += ((pi shr 16) and 0xFF) - ((po shr 16) and 0xFF)
                g += ((pi shr 8) and 0xFF) - ((po shr 8) and 0xFF)
                b += (pi and 0xFF) - (po and 0xFF)
            }
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }
}
