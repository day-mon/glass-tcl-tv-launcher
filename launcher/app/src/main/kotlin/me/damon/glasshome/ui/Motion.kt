package me.damon.glasshome.ui

import android.content.Context
import android.provider.Settings

/**
 * All motion goes through here so it honours the system animation scale: "animations off" means
 * instant, which matters on a TV where people turn them off to feel faster.
 */
object Motion
{
    private const val DEFAULT = 1f

    fun enabled(context: Context): Boolean = scale(context) > 0f

    fun duration(context: Context, millis: Long): Long =
        (millis * scale(context)).toLong().coerceAtLeast(0L)

    private fun scale(context: Context): Float = try
    {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT
        )
    }
    catch (ignored: Exception)
    {
        DEFAULT
    }
}
