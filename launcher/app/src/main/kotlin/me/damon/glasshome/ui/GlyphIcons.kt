package me.damon.glasshome.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Trace
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a coloured app icon into a white glass glyph.
 *
 * TV icons put a logo on a coloured tile in one of two arrangements: a white logo on a coloured
 * tile (Prime Video, Disney+), or a coloured logo on a white tile (Netflix, YouTube). Dropping the
 * colour turns the first into a grey box and the second into a white box, so the shape is recovered
 * from whichever of the two covers *less* of the icon, and painted white.
 */
object GlyphIcons
{
    /** Kept just under opaque so the wallpaper tints the glyph. */
    private const val MAX_ALPHA = 235

    /** Above this share of light pixels the icon is treated as a coloured logo on a white tile. */
    private const val INVERT_ABOVE = 0.45f

    fun of(icon: Drawable, size: Int): Drawable
    {
        Trace.beginSection("glasshome:glyph")
        try
        {
            return bitmapOf(icon, size)
        }
        finally
        {
            Trace.endSection()
        }
    }

    private fun bitmapOf(icon: Drawable, size: Int): Drawable
    {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val copy = icon.constantState?.newDrawable()?.mutate() ?: icon
        copy.setBounds(0, 0, size, size)
        copy.draw(Canvas(bitmap))
        mask(bitmap)
        return BitmapDrawable(null, bitmap)
    }

    private fun mask(bitmap: Bitmap)
    {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val light = FloatArray(pixels.size)
        val dark = FloatArray(pixels.size)
        var lightTotal = 0f

        for (index in pixels.indices)
        {
            val pixel = pixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha == 0) continue

            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val high = max(red, max(green, blue))
            if (high == 0) continue

            val value = high / 255f * (alpha / 255f)
            val saturation = (high - min(red, min(green, blue))) / high.toFloat()
            light[index] = value * (1f - saturation)
            // Anything that is not white: how far the pixel sits from a white background.
            val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
            dark[index] = (1f - luminance) * (alpha / 255f)
            lightTotal += light[index]
        }

        val invert = lightTotal / pixels.size > INVERT_ABOVE

        for (index in pixels.indices)
        {
            val alpha = if (invert)
                (normalise(dark[index], DARK_FLOOR, DARK_RANGE) * MAX_ALPHA).toInt()
            else
                (normalise(light[index], LIGHT_FLOOR, LIGHT_RANGE) * MAX_ALPHA).toInt()
            // White only: the glyph takes the pill's light rather than the brand colour.
            pixels[index] = (alpha shl 24) or 0x00FFFFFF
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun normalise(value: Float, floor: Float, range: Float): Float =
        ((value - floor) / range).coerceIn(0f, 1f)

    private const val LIGHT_FLOOR = 0.72f
    private const val LIGHT_RANGE = 0.25f
    private const val DARK_FLOOR = 0.25f
    private const val DARK_RANGE = 0.45f
}
