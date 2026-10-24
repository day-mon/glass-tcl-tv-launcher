package me.damon.glasshome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import me.damon.glasshome.R
import me.damon.glasshome.model.AppEntry
import me.damon.glasshome.model.AppSorter
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.HomeState
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for the home screen: which apps are installed, in what order, and how
 * they are drawn. Both the home screen and the settings screen talk to this, so a change made in one
 * is immediately the state the other renders.
 */
class HomeRepository(private val context: Context)
{
    private val settings = HomeSettings(context)
    private val order = AppOrder(context)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Re-reads installed apps, keeping the appearance and saved order. */
    fun refresh()
    {
        _state.value = read()
    }

    fun setLayout(mode: LayoutMode) = update { it.copy(layout = mode) }

    fun setMaterial(material: PillMaterial) = update { it.copy(material = material) }

    fun setIcons(style: IconStyle) = update { it.copy(icons = style) }

    fun saveOrder(packages: List<String>)
    {
        order.save(packages)
    }

    private fun update(change: (Appearance) -> Appearance)
    {
        val appearance = change(_state.value.appearance)
        settings.write(appearance)
        _state.value = _state.value.copy(appearance = appearance)
    }

    private fun read(): HomeState
    {
        val appearance = settings.read()
        return HomeState(appearance = appearance, entries = AppSorter.sort(scan(), pinned(), order.load()))
    }

    private fun pinned(): List<String> =
        context.resources.getStringArray(R.array.home_apps).toList()

    private fun hidden(): Set<String> =
        context.resources.getStringArray(R.array.hidden_apps).toSet()

    private fun scan(): List<AppEntry>
    {
        val manager = context.packageManager
        val hidden = hidden()
        // TV apps register LEANBACK_LAUNCHER; a few only register the phone LAUNCHER category.
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER))
        {
            val probe = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in manager.queryIntentActivities(probe, 0))
            {
                val activity = info.activityInfo ?: continue
                val name = activity.packageName
                if (name == context.packageName || name in hidden) continue
                byPackage.putIfAbsent(name, info)
            }
        }

        return byPackage.values.map {
            AppEntry(
                label = it.loadLabel(manager).toString(),
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                icon = it.loadIcon(manager)
            )
        }
    }
}
