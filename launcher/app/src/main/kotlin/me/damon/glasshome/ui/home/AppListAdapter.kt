package me.damon.glasshome.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Trace
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap
import me.damon.glasshome.R
import me.damon.glasshome.ext.move
import me.damon.glasshome.model.AppEntry
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.ui.GlyphIcons
import me.damon.glasshome.ui.IconLibrary

/** Rows without focus sit back so the focused one reads instantly from across the room. */
private const val ALPHA_LABEL_IDLE = 0.40f
private const val ALPHA_ICON_IDLE = 0.50f

/**
 * Renders the entries the repository hands over. It never reads storage itself: appearance and the
 * list arrive from the activity, which collects them from the view model.
 */
class AppListAdapter(
    private val context: Context,
    private val listener: Listener
) : RecyclerView.Adapter<AppListAdapter.Holder>()
{
    interface Listener
    {
        fun onRowFocused(position: Int, content: View)

        fun onRowHorizontalKey(position: Int, keyCode: Int): Boolean

        fun onRowPickedUp(position: Int)

        fun onRowMoved(position: Int)

        fun onRowDropped(entries: List<AppEntry>)
    }

    private var entries: List<AppEntry> = emptyList()
    private var appearance = Appearance()
    /** Filled on a background thread by [warm], read on the main thread while binding. */
    private val iconCache = ConcurrentHashMap<String, Drawable?>()
    private val iconSize: Int = context.resources.getDimensionPixelSize(R.dimen.icon_size)

    var moving: Boolean = false
        private set

    fun submit(list: List<AppEntry>)
    {
        if (entries == list) return
        entries = list
        notifyDataSetChanged()
    }

    fun applyLook(value: Appearance)
    {
        if (appearance == value) return
        // A material change is drawn by the pill alone; rebinding rows for it would be wasted work.
        val relayout = appearance.layout != value.layout || appearance.icons != value.icons
        appearance = value
        if (!relayout) return
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = appearance.layout.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder
    {
        val layout = if (LayoutMode.entries[viewType] == LayoutMode.DOCK) R.layout.item_dock else R.layout.item_app
        return Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false), this)
    }

    override fun onBindViewHolder(holder: Holder, position: Int)
    {
        Trace.beginSection("glasshome:bind")
        val entry = entries[position]
        holder.label.text = entry.label
        val showIcon = appearance.icons == IconStyle.GLYPHS
        holder.icon.visibility = if (showIcon) View.VISIBLE else View.GONE
        holder.icon.setImageDrawable(if (showIcon) iconOf(entry) else null)
        holder.setFocused(holder.content.hasFocus())

        holder.content.setOnClickListener {
            if (moving) drop() else launch(entry)
        }
        Trace.endSection()
    }

    override fun getItemCount(): Int = entries.size

    fun pickUp(position: Int)
    {
        if (position == RecyclerView.NO_POSITION || moving) return
        moving = true
        listener.onRowPickedUp(position)
    }

    fun drop()
    {
        if (!moving) return
        moving = false
        listener.onRowDropped(entries)
    }

    fun move(position: Int, direction: Int)
    {
        val list = entries.toMutableList()
        val target = (position + direction).coerceIn(0, list.lastIndex)
        if (!list.move(position, target)) return

        entries = list
        notifyItemMoved(position, target)
        listener.onRowMoved(target)
    }

    /** Maps the focused [content] view back to its adapter position. */
    fun positionOf(view: View?): Int
    {
        if (view == null) return RecyclerView.NO_POSITION
        val row = view.parent as? View ?: return RecyclerView.NO_POSITION
        val list = row.parent as? RecyclerView ?: return RecyclerView.NO_POSITION
        return list.getChildViewHolder(row).bindingAdapterPosition
    }

    private fun iconOf(entry: AppEntry): Drawable?
    {
        iconCache[entry.packageName]?.let { return it }
        IconLibrary.forPackage(entry.packageName)?.let {
            val drawable = context.getDrawable(it)
            iconCache[entry.packageName] = drawable
            return drawable
        }
        val icon = entry.icon ?: return null
        return GlyphIcons.of(icon, iconSize).also { iconCache[entry.packageName] = it }
    }

    /**
     * Loads and masks every icon ahead of time. Called off the main thread once the list is known,
     * so binding a row only ever reads the cache.
     */
    fun warm(entries: List<AppEntry>)
    {
        if (appearance.icons != IconStyle.GLYPHS) return
        entries.forEach { entry ->
            if (!iconCache.containsKey(entry.packageName)) iconCache[entry.packageName] = loadIcon(entry)
        }
    }

    /** Rebinds the rows so freshly warmed icons appear, without resetting the list. */
    fun refreshIcons()
    {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    private fun loadIcon(entry: AppEntry): Drawable?
    {
        IconLibrary.forPackage(entry.packageName)?.let { return context.getDrawable(it) }
        val icon = entry.icon ?: return null
        return GlyphIcons.of(icon, iconSize)
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
        private val adapter: AppListAdapter
    ) : RecyclerView.ViewHolder(view)
    {
        val content: View = view.findViewById(R.id.content)
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)

        init
        {
            content.setOnKeyListener { _, keyCode, event -> handleKey(keyCode, event) }
            content.setOnFocusChangeListener { focusedView, focused ->
                setFocused(focused)
                if (!focused) return@setOnFocusChangeListener
                // Focus can arrive before the row has been measured; report it once the size is known.
                focusedView.post {
                    if (!focusedView.hasFocus()) return@post
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) adapter.listener.onRowFocused(position, focusedView)
                }
            }
        }

        fun setFocused(focused: Boolean)
        {
            label.animate().alpha(if (focused) 1f else ALPHA_LABEL_IDLE).setDuration(160).start()
            icon.animate().alpha(if (focused) 1f else ALPHA_ICON_IDLE).setDuration(160).start()
        }

        private fun handleKey(keyCode: Int, event: KeyEvent): Boolean
        {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return false
            val down = event.action == KeyEvent.ACTION_DOWN

            if (adapter.moving)
            {
                return when (keyCode)
                {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT ->
                    {
                        if (down) adapter.move(position, -1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT ->
                    {
                        if (down) adapter.move(position, 1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    {
                        // Drop on release and swallow both halves, so finishing a move does not open the app.
                        if (!down) adapter.drop()
                        true
                    }
                    else -> false
                }
            }

            // The dock owns Left/Right; only the list hands them to the corner links.
            if (adapter.appearance.layout != LayoutMode.LIST) return false
            if (!down) return false
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false
            return adapter.listener.onRowHorizontalKey(position, keyCode)
        }
    }
}
