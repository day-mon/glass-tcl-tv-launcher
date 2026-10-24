package me.damon.glasshome.data

import android.content.Context
import android.content.SharedPreferences
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial

/** Where the appearance choices are stored, next to the saved app order. */
class HomeSettings(context: Context)
{
    private val prefs: SharedPreferences =
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): Appearance = Appearance(
        layout = enum(KEY_LAYOUT, LayoutMode.LIST),
        material = enum(KEY_MATERIAL, PillMaterial.GLASS),
        icons = enum(KEY_ICONS, IconStyle.GLYPHS)
    )

    fun write(appearance: Appearance)
    {
        prefs.edit()
            .putString(KEY_LAYOUT, appearance.layout.name)
            .putString(KEY_MATERIAL, appearance.material.name)
            .putString(KEY_ICONS, appearance.icons.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enum(key: String, fallback: T): T =
        prefs.getString(key, null)
            ?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } }
            ?: fallback

    private companion object
    {
        private const val KEY_LAYOUT = "layout_mode"
        private const val KEY_MATERIAL = "pill_material"
        private const val KEY_ICONS = "icon_style"
    }
}
