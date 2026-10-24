package me.damon.glasshome.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * One launchable app on the TV. The icon is fetched on first use: loading icons for every installed
 * app up front cost 160 ms per scan, while only the handful of visible rows ever need one.
 */
data class AppEntry(
    val label: String,
    val component: ComponentName,
    private val iconLoader: () -> Drawable?
)
{
    constructor(label: String, component: ComponentName, icon: Drawable?) : this(label, component, { icon })

    val packageName: String = component.packageName

    val icon: Drawable? by lazy(LazyThreadSafetyMode.NONE) { iconLoader() }
}
