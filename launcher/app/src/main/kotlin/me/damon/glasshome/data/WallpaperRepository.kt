package me.damon.glasshome.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.damon.glasshome.model.WallpaperPhoto
import me.damon.glasshome.model.WallpaperState

/**
 * The album and which photo is showing. The home screen and the picker both read this, so a photo
 * chosen in one is already the photo the other draws.
 */
class WallpaperRepository(context: Context)
{
    private val store = WallpaperStore(WallpaperStore.folderFor(context), WallpaperStore.preferences(context))

    private val _state = MutableStateFlow(read())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    /** Re-reads the folder: photos may have been pushed to the TV while the screen was away. */
    fun refresh()
    {
        _state.value = read()
    }

    fun choose(photo: WallpaperPhoto?)
    {
        store.choose(photo)
        store.setShuffle(false)
        refresh()
    }

    fun setShuffle(on: Boolean)
    {
        store.setShuffle(on)
        refresh()
    }

    /** Moves on to the next photo. Does nothing unless the owner asked for a changing background. */
    fun advance()
    {
        val current = _state.value
        if (!current.shuffle) return
        val next = store.next(current.photos, current.current) ?: return
        store.choose(next)
        refresh()
    }

    private fun read(): WallpaperState
    {
        val photos = store.photos()
        return WallpaperState(photos = photos, current = store.chosen(photos), shuffle = store.shuffle())
    }
}
