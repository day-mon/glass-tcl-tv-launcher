package me.damon.glasshome.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** One launchable app on the TV. */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Drawable?
)
{
    val packageName: String = component.packageName
}
