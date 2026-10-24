package me.damon.glasshome.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import me.damon.glasshome.R
import me.damon.glasshome.model.AppEntry

/** Rows without focus sit back so the focused one reads instantly from across the room. */
private const val ALPHA_LABEL_IDLE = 0.40f
private const val ALPHA_ICON_IDLE = 0.50f

/**
 * The app list behind the home screen: pinned apps first, then everything else alphabetically.
 * Focus is shown with opacity; the glass pill is drawn by the activity.
 */
class AppListAdapter(
    private val context: Context,
    private val listener: Listener
) : RecyclerView.Adapter<AppListAdapter.Holder>()
{
    interface Listener
    {
        /** A row took focus; [content] is the icon-and-name group the pill should cover. */
        fun onRowFocused(position: Int, content: View)

        /** D-pad Left/Right on a row. Return true when handled. */
        fun onRowHorizontalKey(position: Int, keyCode: Int): Boolean
    }

    /** Pinned order (res/values/config.xml, home_apps). */
    private val pinned: List<String> = context.resources.getStringArray(R.array.home_apps).toList()

    /** Never listed (res/values/config.xml, hidden_apps). */
    private val hidden: Set<String> = context.resources.getStringArray(R.array.hidden_apps).toSet()

    private val glassIcons: Boolean = context.resources.getBoolean(R.bool.glass_icons)
    private val showIcons: Boolean = context.resources.getBoolean(R.bool.show_icons)
    private val iconSize: Int = context.resources.getDimensionPixelSize(R.dimen.icon_size)

    /** Glass glyphs are derived once per app and reused on every bind. */
    private val glyphs = mutableMapOf<String, Drawable?>()

    private val items = mutableListOf<AppEntry>()

    fun reload()
    {
        val manager = context.packageManager
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

        val order = compareBy<AppEntry>(
            { indexOrLast(it) },
            { it.label.lowercase() }
        )
        val fresh = byPackage.values.map {
            AppEntry(
                label = it.loadLabel(manager).toString(),
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                icon = it.loadIcon(manager)
            )
        }.sortedWith(order)

        items.clear()
        items.addAll(fresh)
        notifyDataSetChanged()
    }

    private fun indexOrLast(entry: AppEntry): Int = pinned.indexOf(entry.packageName).takeIf { it >= 0 } ?: Int.MAX_VALUE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false),
        listener
    )

    override fun onBindViewHolder(holder: Holder, position: Int)
    {
        val entry = items[position]
        holder.label.text = entry.label
        // Most TV app icons are a white logo on a coloured tile: with the colour removed they are just
        // grey rectangles, so the clean look is names alone unless you ask for icons.
        holder.icon.visibility = if (showIcons) View.VISIBLE else View.GONE
        holder.icon.setImageDrawable(if (showIcons) iconOf(entry) else null)
        holder.setFocused(holder.content.hasFocus())

        holder.content.setOnClickListener { launch(entry) }
        holder.content.setOnLongClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try
            {
                context.startActivity(intent)
            }
            catch (ignored: Exception)
            {
            }
            true
        }
    }

    override fun getItemCount(): Int = items.size

    private fun iconOf(entry: AppEntry): Drawable? = when
    {
        !glassIcons -> entry.icon
        entry.icon == null -> null
        else -> glyphs.getOrPut(entry.packageName) { GlyphIcons.of(entry.icon, iconSize) }
    }

    private fun launch(entry: AppEntry)
    {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try
        {
            context.startActivity(intent)
        }
        catch (error: Exception)
        {
            Toast.makeText(context, "Can't open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    class Holder(
        view: View,
        private val listener: Listener?
    ) : RecyclerView.ViewHolder(view)
    {
        val content: View = view.findViewById(R.id.content)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)

        init
        {
            content.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT)
                    return@setOnKeyListener false
                val position = bindingAdapterPosition
                listener != null && position != RecyclerView.NO_POSITION &&
                        listener.onRowHorizontalKey(position, keyCode)
            }

            content.setOnFocusChangeListener { focusedView, focused ->
                setFocused(focused)
                if (!focused || listener == null) return@setOnFocusChangeListener
                // Focus can arrive before the row has been measured; report it once the size is known.
                focusedView.post {
                    if (!focusedView.hasFocus()) return@post
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) listener.onRowFocused(position, focusedView)
                }
            }
        }

        fun setFocused(focused: Boolean)
        {
            label.animate().alpha(if (focused) 1f else ALPHA_LABEL_IDLE).setDuration(160).start()
            icon.animate().alpha(if (focused) 1f else ALPHA_ICON_IDLE).setDuration(160).start()
        }
    }
}
