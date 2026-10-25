package me.damon.glasshome.ui

import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.damon.glasshome.R
import me.damon.glasshome.ext.decodePhoto
import me.damon.glasshome.model.WallpaperState

/**
 * Draws the chosen photo behind a screen, and the bundled photograph when the album is empty.
 * Decoding happens off the main thread and only when the photo actually changes, so returning to
 * the home screen costs no decode at all.
 */
class WallpaperBackdrop(
    private val activity: ComponentActivity,
    private val view: ImageView,
    private val restingAlpha: Float = 1f
)
{
    private var showing: String? = null

    fun show(state: WallpaperState)
    {
        val photo = state.current
        val key = photo?.file?.absolutePath ?: BUILT_IN
        if (key == showing) return
        showing = key

        if (photo == null)
        {
            bundled()
            return
        }

        activity.lifecycleScope.launch {
            val metrics = activity.resources.displayMetrics
            val decoded = withContext(Dispatchers.IO)
            {
                decodePhoto(photo.file, metrics.widthPixels, metrics.heightPixels)
            }
            // A different photo may have been chosen while this one was decoding.
            if (showing != key) return@launch
            if (decoded == null)
            {
                bundled()
                return@launch
            }

            view.setImageBitmap(decoded)
            view.alpha = 0f
            view.animate().alpha(restingAlpha).setDuration(FADE_MS).start()
        }
    }

    private fun bundled()
    {
        view.setImageResource(R.drawable.wallpaper_alpine_small)
        view.alpha = restingAlpha
    }

    private companion object
    {
        private const val BUILT_IN = "bundled"
        private const val FADE_MS = 420L
    }
}
