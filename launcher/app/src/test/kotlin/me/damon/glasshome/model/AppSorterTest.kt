package me.damon.glasshome.model

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSorterTest
{
    private fun app(packageName: String, label: String) = AppEntry(
        label = label,
        component = ComponentName(packageName, "$packageName.Main"),
        icon = null
    )

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val prime = app("com.amazon.amazonvideo.livingroom", "Prime Video")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val aerial = app("com.neilturner.aerialviews", "Aerial Views")
    private val hulu = app("com.hulu.livingroomplus", "Hulu")
    private val pinned = listOf(netflix.packageName, prime.packageName, youtube.packageName)

    @Test
    fun `seed order is pinned first then alphabetical`()
    {
        val sorted = AppSorter.sort(listOf(hulu, youtube, aerial, prime, netflix), pinned, emptyList())

        assertEquals(listOf("Netflix", "Prime Video", "YouTube", "Aerial Views", "Hulu"), sorted.map { it.label })
    }

    @Test
    fun `saved order wins over the seed order`()
    {
        val saved = listOf(youtube.packageName, netflix.packageName, prime.packageName, hulu.packageName, aerial.packageName)

        val sorted = AppSorter.sort(listOf(netflix, prime, youtube, aerial, hulu), pinned, saved)

        assertEquals(listOf("YouTube", "Netflix", "Prime Video", "Hulu", "Aerial Views"), sorted.map { it.label })
    }

    @Test
    fun `apps missing from the saved order land last, alphabetically`()
    {
        val saved = listOf(netflix.packageName)

        val sorted = AppSorter.sort(listOf(aerial, hulu, netflix, youtube), pinned, saved)

        assertEquals(listOf("Netflix", "Aerial Views", "Hulu", "YouTube"), sorted.map { it.label })
    }

    @Test
    fun `a newly installed app appears once, at the end`()
    {
        val saved = listOf(netflix.packageName, prime.packageName)
        val newcomer = app("com.example.new", "Zed")

        val sorted = AppSorter.sort(listOf(netflix, prime, newcomer, youtube), pinned, saved)

        assertEquals(4, sorted.size)
        assertEquals(4, sorted.map { it.packageName }.distinct().size)
        assertEquals("Zed", sorted.last().label)
    }
}
