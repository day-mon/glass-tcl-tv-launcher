package me.damon.glasshome.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import me.damon.glasshome.R
import me.damon.glasshome.ext.move
import me.damon.glasshome.model.AppEntry
import me.damon.glasshome.model.AppOrder
import me.damon.glasshome.model.AppSorter

/** Rows without focus sit back so the focused one reads instantly from across the room. */
private const val ALPHA_LABEL_IDLE = 0.40f
private const val ALPHA_ICON_IDLE = 0.50f

/**
 * The app list behind the home screen. Seed order is pinned-then-alphabetical; once the owner
 * rearranges it by long-pressing a row, [AppOrder] takes over.
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

        /** Long press started rearranging. */
        fun onRowPickedUp(position: Int)

        /** A row moved while rearranging; focus needs following it. */
        fun onRowMoved(position: Int)

        /** Rearranging finished and the order has been saved. */
        fun onRowDropped()
    }

    /** Pinned order (res/values/config.xml, home_apps). */
    private val pinned: List<String> = context.resources.getStringArray(R.array.home_apps).toList()

    /** Never listed (res/values/config.xml, hidden_apps). */
    private val hidden: Set<String> = context.resources.getStringArray(R.array.hidden_apps).toSet()

    private val glassIcons: Boolean = context.resources.getBoolean(R.bool.glass_icons)
    private val showIcons: Boolean = context.resources.getBoolean(R.bool.show_icons)
    private val iconSize: Int = context.resources.getDimensionPixelSize(R.dimen.icon_size)

    private val order = AppOrder(context)
    private val glyphs = mutableMapOf<String, Drawable?>()
    private val items = mutableListOf<AppEntry>()

    var moving: Boolean = false
        private set

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

        val fresh = byPackage.values.map {
            AppEntry(
                label = it.loadLabel(manager).toString(),
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                icon = it.loadIcon(manager)
            )
        }
        items.clear()
        items.addAll(AppSorter.sort(fresh, pinned, order.load()))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false),
        this
    )

    override fun onBindViewHolder(holder: Holder, position: Int)
    {
        val entry = items[position]
        holder.label.text = entry.label
        holder.icon.visibility = if (showIcons) View.VISIBLE else View.GONE
        holder.icon.setImageDrawable(if (showIcons) iconOf(entry) else null)
        holder.setFocused(holder.content.hasFocus())
    }

    override fun getItemCount(): Int = items.size

    private fun iconOf(entry: AppEntry): Drawable?
    {
        IconLibrary.forPackage(entry.packageName)?.let { return context.getDrawable(it) }
        if (!glassIcons) return entry.icon
        val icon = entry.icon ?: return null
        return glyphs.getOrPut(entry.packageName) { GlyphIcons.of(icon, iconSize) }
    }

    fun launch(position: Int)
    {
        if (position == RecyclerView.NO_POSITION) return
        val entry = items[position]
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
        order.save(items.map { it.packageName })
        listener.onRowDropped()
    }

    fun move(position: Int, direction: Int)
    {
        val target = (position + direction).coerceIn(0, items.lastIndex)
        if (!items.move(position, target)) return
        notifyItemMoved(position, target)
        listener.onRowMoved(target)
    }

    fun positionOf(view: View?): Int
    {
        if (view == null) return RecyclerView.NO_POSITION
        return (view.parent as? View)?.let { row ->
            (row.parent as? RecyclerView)?.getChildViewHolder(row)?.bindingAdapterPosition
        } ?: RecyclerView.NO_POSITION
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
            content.setOnClickListener { if (adapter.moving) adapter.drop() else adapter.launch(bindingAdapterPosition) }
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
                    KeyEvent.KEYCODE_DPAD_UP ->
                    {
                        if (down) adapter.move(position, -1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN ->
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
                    else -> down && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            }

            if (!down) return false
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false
            return adapter.listener.onRowHorizontalKey(position, keyCode)
        }
    }
}
