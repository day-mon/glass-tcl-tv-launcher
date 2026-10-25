package me.damon.glasshome.ui.wallpapers

import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import me.damon.glasshome.GlassHomeApplication
import me.damon.glasshome.R
import me.damon.glasshome.model.WallpaperPhoto
import me.damon.glasshome.model.WallpaperState
import me.damon.glasshome.ui.WallpaperBackdrop

/**
 * Picks the photograph behind the home screen, with the remote: arrows to move around the grid, OK
 * to choose, and one switch for a background that changes by itself.
 */
class WallpapersActivity : ComponentActivity()
{
    private val repository
        get() = (application as GlassHomeApplication).container.wallpaperRepository

    private lateinit var adapter: WallpaperAdapter

    /** The key that opened this screen can still be down when the grid appears; ignore it. */
    private val openedAt = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpapers)

        adapter = WallpaperAdapter(this) { photo -> choose(photo) }
        findViewById<RecyclerView>(R.id.photos).apply {
            layoutManager = GridLayoutManager(this@WallpapersActivity, COLUMNS)
            adapter = this@WallpapersActivity.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }

        val backdrop = WallpaperBackdrop(this, findViewById(R.id.wallpaperBackdrop), restingAlpha = 0.35f)

        val shuffle = findViewById<TextView>(R.id.shuffle)
        shuffle.setOnClickListener {
            repository.setShuffle(!repository.state.value.shuffle)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.state.collect {
                    render(it, shuffle)
                    backdrop.show(it)
                }
            }
        }
    }

    override fun onResume()
    {
        super.onResume()
        // Photos may have been pushed to the TV while this screen was away.
        repository.refresh()
    }

    private fun render(state: WallpaperState, shuffle: TextView)
    {
        shuffle.isSelected = state.shuffle
        shuffle.text = getString(if (state.shuffle) R.string.wallpaper_shuffle_on else R.string.wallpaper_shuffle_off)
        findViewById<TextView>(R.id.wallpaperEmpty).visibility =
            if (state.photos.isEmpty()) TextView.VISIBLE else TextView.GONE
        adapter.submit(state)
    }

    private fun choose(photo: WallpaperPhoto?)
    {
        if (SystemClock.elapsedRealtime() - openedAt < IGNORE_INPUT_MS) return
        repository.choose(photo)
        finish()
    }

    private companion object
    {
        private const val COLUMNS = 4
        private const val IGNORE_INPUT_MS = 350L
    }
}
