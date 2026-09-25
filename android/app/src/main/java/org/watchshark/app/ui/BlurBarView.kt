package org.watchshark.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout

/**
 * Top/bottom bar with a live frosted-glass backdrop: samples the content
 * scrolling underneath, blurs it on the CPU at tiny scale (cheap), and
 * lays translucent AMOLED black over it. Toggleable via [blurEnabled].
 *
 * The backdrop stays dynamic two ways: a lightweight refresh loop
 * re-draws at [CAPTURE_MIN_MS] intervals while attached, and a scroll
 * listener on the target's view tree invalidates immediately on scroll.
 * (Without these, onDraw only runs once and the blur freezes into a
 * static snapshot of whatever was behind the bar at first draw.)
 */
class BlurBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    companion object {
        @Volatile
        var blurEnabled: Boolean = true
            set(value) {
                field = value
                // Apply the toggle to every visible bar right away —
                // no relaunch needed.
                synchronized(live) { live.toList().forEach { it.onBlurToggled() } }
            }
        private const val DOWNSCALE = 10
        private const val CAPTURE_MIN_MS = 150L
        // AMOLED-friendly scrim: translucent black over the blur so the
        // frosted content stays visible instead of drowning in black.
        private const val SCRIM = 0x80000000

        private val live = mutableSetOf<BlurBarView>()
    }

    /** Content view sampled from behind this bar (the fragment container). */
    var target: View? = null
        set(value) {
            if (field === value) return
            unregisterTargetScroll()
            field = value
            registerTargetScroll()
            invalidate()
        }

    private var cached: Bitmap? = null
    private var lastCapture = 0L
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = SCRIM.toInt() }

    /** Re-draw on an interval so video frames / list changes show through. */
    private val refresher = object : Runnable {
        override fun run() {
            if (isAttachedToWindow && blurEnabled) {
                invalidate()
                postDelayed(this, CAPTURE_MIN_MS)
            }
        }
    }

    /** Instant refresh when anything in the target's tree scrolls. */
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        if (blurEnabled && isAttachedToWindow) invalidate()
    }
    private var observedVto: ViewTreeObserver? = null

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        synchronized(live) { live.add(this) }
        registerTargetScroll()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        unregisterTargetScroll()
        synchronized(live) { live.remove(this) }
        cached?.recycle()
        cached = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        drawBlurBehind(canvas)
        super.onDraw(canvas)
    }

    private fun onBlurToggled() {
        if (!isAttachedToWindow) return
        if (blurEnabled) {
            registerTargetScroll()
            startLoop()
        } else {
            stopLoop()
            cached?.recycle()
            cached = null
        }
        invalidate()
    }

    private fun startLoop() {
        removeCallbacks(refresher)
        if (blurEnabled) postDelayed(refresher, CAPTURE_MIN_MS)
    }

    private fun stopLoop() {
        removeCallbacks(refresher)
    }

    private fun registerTargetScroll() {
        val t = target ?: return
        if (!isAttachedToWindow) return
        val vto = t.viewTreeObserver
        if (vto !== observedVto) {
            unregisterTargetScroll()
            observedVto = vto
            runCatching { vto.addOnScrollChangedListener(scrollListener) }
        }
    }

    private fun unregisterTargetScroll() {
        observedVto?.let { vto ->
            runCatching { vto.removeOnScrollChangedListener(scrollListener) }
        }
        observedVto = null
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
            val selfLoc = IntArray(2)
            getLocationOnScreen(selfLoc)
            val targetLoc = IntArray(2)
            t.getLocationOnScreen(targetLoc)
            val w = (width / DOWNSCALE).coerceAtLeast(1)
            val h = (height / DOWNSCALE).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(1f / DOWNSCALE, 1f / DOWNSCALE)
            c.translate(
                -(selfLoc[0] - targetLoc[0]).toFloat(),
                -(selfLoc[1] - targetLoc[1]).toFloat()
            )
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
