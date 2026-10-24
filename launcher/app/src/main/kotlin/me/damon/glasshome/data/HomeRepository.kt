package me.damon.glasshome.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Trace
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.damon.glasshome.BuildConfig
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
    private companion object
    {
        private const val TAG = "GlassHome"
    }

    private val settings = HomeSettings(context)
    private val order = AppOrder(context)

    // The appearance is cheap to read; the app scan is not, so it happens off the main thread once
    // the screen is already up.
    private val _state = MutableStateFlow(HomeState(appearance = settings.read()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Re-reads installed apps, keeping the appearance and saved order. */
    suspend fun refresh()
    {
        val started = System.nanoTime()
        val entries = withContext(Dispatchers.Default)
        {
            Trace.beginSection("glasshome:scan-query")
            val scanned = try
            {
                query()
            }
            finally
            {
                Trace.endSection()
            }

            Trace.beginSection("glasshome:scan")
            try
            {
                AppSorter.sort(scanned, pinned(), order.load())
            }
            finally
            {
                Trace.endSection()
            }
        }
        // Appearance is re-read too: storage is the truth, in case anything changed it elsewhere.
        _state.value = HomeState(appearance = settings.read(), entries = entries)

        if (BuildConfig.DEBUG)
        {
            val millis = (System.nanoTime() - started) / 1_000_000
            Log.d(TAG, "scan: ${entries.size} apps in $millis ms")
        }
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

    private fun pinned(): List<String> =
        context.resources.getStringArray(R.array.home_apps).toList()

    private fun hidden(): Set<String> =
        context.resources.getStringArray(R.array.hidden_apps).toSet()

    private fun query(): List<AppEntry>
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

        Trace.beginSection("glasshome:scan-labels")
        val labels = byPackage.values.map { it.loadLabel(manager).toString() }
        Trace.endSection()

        Trace.beginSection("glasshome:scan-build")
        val entries = byPackage.values.mapIndexed { index, info ->
            AppEntry(
                label = labels[index],
                component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                iconLoader = { info.loadIcon(manager) }
            )
        }
        Trace.endSection()

        return entries
    }
}
