package me.damon.glasshome.model

import java.io.File

/**
 * A photo the owner put on the TV. The album lives on the device: trips are personal, so they are
 * pushed to the television rather than carried in the repository.
 */
data class WallpaperPhoto(
    val file: File,
    val label: String
)

/** What the home screen draws behind the app list. */
data class WallpaperState(
    val photos: List<WallpaperPhoto> = emptyList(),
    val current: WallpaperPhoto? = null,
    val shuffle: Boolean = false
)
