package org.watchshark.app.ui

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView

/** ImageView that always measures 16:9 (same as web mobile thumbnails). */
class SixteenNineImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ShapeableImageView(context, attrs, defStyle) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (w * 9f / 16f).toInt())
    }
}
