package me.damon.glasshome.ext

import android.content.Context

/** Density-independent pixels for this screen. */
fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
