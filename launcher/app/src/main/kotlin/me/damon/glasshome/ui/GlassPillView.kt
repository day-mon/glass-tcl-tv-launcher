package me.damon.glasshome.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import me.damon.glasshome.ext.dp

/**
 * A translucent smoked-glass slab: dark tint, faint sheen, bright rim and a soft outer glow.
 * One instance floats behind the app list and springs between rows.
 */
class GlassPillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs)
{
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specular = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    /** Room left inside the view for the glow stroke. */
    val inset = context.dp(6f)

    private var radius = context.dp(20f)

    init
    {
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = context.dp(1.2f)
        rim.color = 0x66FFFFFF
        glow.style = Paint.Style.STROKE
    }

    fun setRadius(value: Float)
    {
        radius = value
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int)
    {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x80000000.toInt(), 0x4D000000, Shader.TileMode.CLAMP)
        specular.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas)
    {
        rect.set(inset, inset, width - inset, height - inset)

        // Outer glow: three widening, fading strokes.
        for (i in 1..3)
        {
            glow.strokeWidth = i * 2f * inset / 6f
            glow.color = (0x1C / i shl 24) or 0xFFFFFF
            val offset = i * inset / 6f
            canvas.drawRoundRect(
                rect.left - offset, rect.top - offset, rect.right + offset, rect.bottom + offset,
                radius + offset, radius + offset, glow
            )
        }

        canvas.drawRoundRect(rect, radius, radius, fill)

        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f, specular)
        canvas.restore()

        canvas.drawRoundRect(rect, radius, radius, rim)
    }
}
