package me.damon.glasshome.ext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The largest power-of-two subsample whose result still covers [target] pixels. A 24 MP photo
 * decoded whole costs about 96 MB, which a television has no reason to spare.
 */
fun sampleSizeFor(width: Int, height: Int, target: Int): Int
{
    var sample = 1
    while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
    return sample
}

/**
 * Decodes [file] so it covers [targetWidth] x [targetHeight] and no more. Returns null when the
 * file is not an image this device can read, or when there is no heap left to hold it.
 */
fun decodePhoto(file: File, targetWidth: Int, targetHeight: Int): Bitmap?
{
    if (targetWidth <= 0 || targetHeight <= 0) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, max(targetWidth, targetHeight))
    }
    val decoded = try
    {
        BitmapFactory.decodeFile(file.absolutePath, options)
    }
    catch (error: OutOfMemoryError)
    {
        // A photo big enough to exhaust the heap must cost one row, not the launcher.
        null
    }
    ?: return null

    // A power-of-two subsample can still land well above the screen, so shrink it to cover exactly.
    val scale = max(targetWidth.toFloat() / decoded.width, targetHeight.toFloat() / decoded.height)
    if (scale >= 1f) return decoded

    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).roundToInt().coerceAtLeast(1),
        (decoded.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}
