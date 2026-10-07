package watchshark.duckdns.org.ui
import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView
/** ImageView that always measures 1:1 (square album-art style). */
class SquareImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ShapeableImageView(context, attrs, defStyle) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, widthSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }
}