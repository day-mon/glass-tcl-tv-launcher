package me.damon.glasshome.ui.wallpapers

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.damon.glasshome.R
import me.damon.glasshome.ext.decodePhoto
import me.damon.glasshome.model.WallpaperPhoto
import me.damon.glasshome.model.WallpaperState

/**
 * The photo grid: the bundled photograph first, then everything in the album. Thumbnails are decoded
 * small and kept in a small cache, because the originals are far larger than a tile.
 */
class WallpaperAdapter(
    private val activity: ComponentActivity,
    private val onPick: (WallpaperPhoto?) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.Holder>()
{
    private var photos: List<WallpaperPhoto> = emptyList()
    private var current: String? = null
    private val thumbnails = LruCache<String, Bitmap>(THUMBNAIL_CACHE)

    fun submit(state: WallpaperState)
    {
        photos = state.photos
        current = state.current?.file?.name
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = photos.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper, parent, false), this)

    override fun onBindViewHolder(holder: Holder, position: Int)
    {
        // The bundled photograph is offered first so the album can be switched off again.
        val photo = photos.getOrNull(position - 1)
        holder.label.text = photo?.label ?: activity.getString(R.string.wallpaper_built_in)
        holder.itemView.isSelected = if (photo == null) current == null else photo.file.name == current
        holder.itemView.setOnClickListener { onPick(photo) }
        holder.itemView.setOnFocusChangeListener { view, focused ->
            val scale = if (focused) 1.03f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_MS).start()
        }
        holder.showThumbnail(photo, thumbnails)
    }

    class Holder(
        view: View,
        private val adapter: WallpaperAdapter
    ) : RecyclerView.ViewHolder(view)
    {
        val thumbnail: ImageView = view.findViewById(R.id.thumbnail)
        val label: TextView = view.findViewById(R.id.wallpaperLabel)

        fun showThumbnail(photo: WallpaperPhoto?, cache: LruCache<String, Bitmap>)
        {
            val path = photo?.file?.absolutePath
            thumbnail.tag = path
            if (photo == null)
            {
                thumbnail.setImageResource(R.drawable.wallpaper_alpine_small)
                return
            }

            cache.get(path)?.let { cached ->
                thumbnail.setImageBitmap(cached)
                return
            }

            thumbnail.setImageDrawable(null)
            adapter.activity.lifecycleScope.launch {
                val decoded = withContext(Dispatchers.IO) { decodePhoto(photo.file, THUMB_W, THUMB_H) }
                // The row may have been recycled onto another photo while this one decoded.
                if (thumbnail.tag != path || decoded == null) return@launch
                cache.put(path, decoded)
                thumbnail.setImageBitmap(decoded)
            }
        }
    }

    private companion object
    {
        private const val THUMB_W = 480
        private const val THUMB_H = 270
        private const val THUMBNAIL_CACHE = 24
        private const val FOCUS_MS = 140L
    }
}
