package me.damon.glasshome.ui

import androidx.annotation.DrawableRes
import me.damon.glasshome.R

/**
 * Brand glyphs shipped with the app, so the common streaming apps look like the reference design
 * instead of whatever tile the store gave them. Apps without one fall back to [GlyphIcons], and
 * then to a monogram.
 */
object IconLibrary
{
    private val glyphs: Map<String, Int> = mapOf(
        "com.netflix.ninja" to R.drawable.glyph_netflix,
        "com.amazon.amazonvideo.livingroom" to R.drawable.glyph_primevideo,
        "com.google.android.youtube.tv" to R.drawable.glyph_youtube,
        "org.smarttube.stable" to R.drawable.glyph_youtube,
        "com.disney.disneyplus" to R.drawable.glyph_disneyplus,
        "com.wbd.stream" to R.drawable.glyph_hbomax,
        "com.hulu.livingroomplus" to R.drawable.glyph_hulu,
        "com.stremio.one" to R.drawable.glyph_stremio,
        "com.spotify.tv.android" to R.drawable.glyph_spotify,
        "org.jellyfin.androidtv" to R.drawable.glyph_jellyfin,
        "com.cbs.ott" to R.drawable.glyph_paramountplus,
        "net.mullvad.mullvadvpn" to R.drawable.glyph_mullvad,
        "com.plexapp.android" to R.drawable.glyph_plex,
        "com.tubi.tv" to R.drawable.glyph_tubi
    )

    /** Wordmarks are wide and short: they need a wider box than the square marks. */
    private val wordmarks: Set<String> = setOf(
        "com.amazon.amazonvideo.livingroom",
        "com.wbd.stream",
        "com.hulu.livingroomplus",
        "com.cbs.ott",
        "com.tubi.tv"
    )

    @DrawableRes
    fun forPackage(packageName: String): Int? = glyphs[packageName]

    fun isWordmark(packageName: String): Boolean = packageName in wordmarks
}
