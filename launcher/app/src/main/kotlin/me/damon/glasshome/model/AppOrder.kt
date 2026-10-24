package me.damon.glasshome.model

import android.content.Context
import android.content.SharedPreferences

/**
 * The order the owner dragged the list into. Empty means "use the seed order" from config.xml,
 * which is pinned apps first and everything else alphabetical.
 */
class AppOrder(context: Context)
{
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY_ORDER, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun save(packages: List<String>)
    {
        prefs.edit().putString(KEY_ORDER, packages.joinToString(SEPARATOR)).apply()
    }

    fun clear()
    {
        prefs.edit().remove(KEY_ORDER).apply()
    }

    companion object
    {
        const val PREFS_NAME = "glasshome"

        private const val KEY_ORDER = "app_order"
        private const val SEPARATOR = "\n"
    }
}
