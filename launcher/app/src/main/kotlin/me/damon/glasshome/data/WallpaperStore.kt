package me.damon.glasshome.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import me.damon.glasshome.model.WallpaperPhoto

/**
 * The photo album: images in this app's own folder on the TV. That folder needs no storage
 * permission, and keeping the album on the device is what stops personal photos reaching the
 * repository. `./gradlew tvWallpapers -Pphotos=<folder>` fills it.
 */
class WallpaperStore(
    private val folder: File,
    private val prefs: SharedPreferences
)
{
    init
    {
        folder.mkdirs()
    }

    /** Every image in the album, in a stable order so "the next photo" means something. */
    fun photos(): List<WallpaperPhoto> = folder.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
        ?.sortedBy { it.name.lowercase() }
        ?.map { WallpaperPhoto(it, it.nameWithoutExtension) }
        ?.toList()
        ?: emptyList()

    /**
     * The chosen photo. Nothing stored means an album that has never been touched, so the first
     * photo shows. [BUILT_IN] is an answer in itself: the owner asked for the bundled photograph.
     */
    fun chosen(photos: List<WallpaperPhoto>): WallpaperPhoto?
    {
        val name = prefs.getString(KEY_CURRENT, null) ?: return photos.firstOrNull()
        if (name == BUILT_IN) return null
        return photos.firstOrNull { it.file.name == name } ?: photos.firstOrNull()
    }

    /** The photo after [current], wrapping around; the first one when nothing was chosen yet. */
    fun next(photos: List<WallpaperPhoto>, current: WallpaperPhoto?): WallpaperPhoto?
    {
        if (photos.isEmpty()) return null
        val index = photos.indexOfFirst { it.file.name == current?.file?.name }
        return photos[(index + 1).mod(photos.size)]
    }

    fun choose(photo: WallpaperPhoto?)
    {
        // Stored as a value rather than removed: choosing the bundled photograph has to survive an
        // album that is not empty, or the choice reads as "never chosen" and the first photo wins.
        prefs.edit().putString(KEY_CURRENT, photo?.file?.name ?: BUILT_IN).apply()
    }

    fun shuffle(): Boolean = prefs.getBoolean(KEY_SHUFFLE, false)

    fun setShuffle(on: Boolean)
    {
        prefs.edit().putBoolean(KEY_SHUFFLE, on).apply()
    }

    companion object
    {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
        private const val KEY_CURRENT = "wallpaper_current"

        /** Marks "the bundled photograph" as a choice, distinct from "nothing chosen yet". */
        const val BUILT_IN = "built-in"
        private const val KEY_SHUFFLE = "wallpaper_shuffle"

        fun folderFor(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "wallpapers")

        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)
    }
}
