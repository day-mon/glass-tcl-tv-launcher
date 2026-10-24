package me.damon.glasshome.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import me.damon.glasshome.ext.dp
import me.damon.glasshome.model.PillMaterial
import kotlin.math.min

/** Sizes, moves and fades the glass pill behind whichever row has focus. */
class GlassPillController(
    context: Context,
    private val root: ViewGroup,
    private val pill: GlassPillView,
    list: RecyclerView
)
{
    private val padding = context.dp(PADDING_DP)
    private val radiusCap = context.dp(RADIUS_DP)

    private val springX = SpringAnimation(pill, DynamicAnimation.TRANSLATION_X)
    private val springY = SpringAnimation(pill, DynamicAnimation.TRANSLATION_Y)

    private var targetY = 0f
    private var shown = false

    init
    {
        for (spring in listOf(springX, springY))
        {
            spring.spring = SpringForce().setStiffness(280f).setDampingRatio(0.72f)
        }

        // Keep the pill glued to its row while the list scrolls under it.
        list.addOnScrollListener(object : RecyclerView.OnScrollListener()
        {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int)
            {
                if (!shown) return
                targetY -= dy
                if (springY.isRunning) springY.animateToFinalPosition(targetY)
                else pill.translationY = targetY
            }
        })
    }

    fun show(content: View)
    {
        val rect = Rect(0, 0, content.width, content.height)
        root.offsetDescendantRectToMyCoords(content, rect)

        val inset = pill.inset
        val width = content.width + 2 * (padding + inset)
        val height = content.height + 2 * (padding + inset)
        val x = rect.exactCenterX() - width / 2f
        val y = rect.exactCenterY() - height / 2f

        resize(width, height, inset)
        targetY = y

        if (shown)
        {
            springX.animateToFinalPosition(x)
            springY.animateToFinalPosition(y)
            return
        }

        springX.cancel()
        springY.cancel()
        pill.translationX = x
        pill.translationY = y
        pill.animate().alpha(1f).setDuration(200).start()
        shown = true
    }

    fun setMaterial(material: PillMaterial)
    {
        pill.setMaterial(material)
    }

    fun hide()
    {
        if (!shown) return
        shown = false
        pill.animate().alpha(0f).setDuration(160).start()
    }

    private fun resize(width: Float, height: Float, inset: Float)
    {
        val params = pill.layoutParams
        if (params.width == width.toInt() && params.height == height.toInt()) return

        params.width = width.toInt()
        params.height = height.toInt()
        pill.layoutParams = params
        // Rounded rectangle, not a stadium: cap the corners like the reference.
        pill.setRadius(min((height - 2 * inset) / 2f, radiusCap))
    }

    private companion object
    {
        /** How far the pill extends past the row's icon-and-name group. */
        private const val PADDING_DP = 8f
        private const val RADIUS_DP = 14f
    }
}
