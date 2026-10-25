package me.damon.glasshome.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import me.damon.glasshome.model.WallpaperPhoto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WallpaperStoreTest
{
    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var store: WallpaperStore

    @Before
    fun setUp()
    {
        context = ApplicationProvider.getApplicationContext()
        folder = File(context.cacheDir, "wallpapers").apply { deleteRecursively(); mkdirs() }
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = WallpaperStore(folder, WallpaperStore.preferences(context))
    }

    @After
    fun tearDown()
    {
        folder.deleteRecursively()
    }

    private fun photo(name: String): File = File(folder, name).apply { writeText(name) }

    @Test
    fun `only images are offered, in a stable order`()
    {
        photo("b.JPG")
        photo("a.jpg")
        photo("c.png")
        photo("notes.txt")
        File(folder, "nested").mkdirs()

        assertEquals(listOf("a", "b", "c"), store.photos().map { it.label })
    }

    @Test
    fun `nothing chosen yet falls back to the first photo`()
    {
        photo("a.jpg")
        photo("b.jpg")

        assertEquals("a.jpg", store.chosen(store.photos())?.file?.name)
    }

    @Test
    fun `an empty album has nothing to show`()
    {
        assertTrue(store.photos().isEmpty())
        assertNull(store.chosen(store.photos()))
        assertNull(store.next(emptyList(), null))
    }

    @Test
    fun `a chosen photo comes back and survives the choice disappearing`()
    {
        photo("a.jpg")
        photo("b.jpg")
        val photos = store.photos()

        store.choose(photos[1])
        assertEquals("b.jpg", store.chosen(photos)?.file?.name)

        // The owner deleted the photo they had chosen: show the album rather than nothing.
        File(folder, "b.jpg").delete()
        assertEquals("a.jpg", store.chosen(store.photos())?.file?.name)
    }

    @Test
    fun `next wraps around the album`()
    {
        photo("a.jpg")
        photo("b.jpg")
        photo("c.jpg")
        val photos = store.photos()

        assertEquals("b.jpg", store.next(photos, photos[0])?.file?.name)
        assertEquals("c.jpg", store.next(photos, photos[1])?.file?.name)
        assertEquals("a.jpg", store.next(photos, photos[2])?.file?.name)
        assertEquals("a.jpg", store.next(photos, null)?.file?.name)
    }

    @Test
    fun `shuffle is off until it is asked for, and is remembered`()
    {
        assertFalse(store.shuffle())

        store.setShuffle(true)
        assertTrue(WallpaperStore(folder, WallpaperStore.preferences(context)).shuffle())
    }

    @Test
    fun `a missing album folder is created`()
    {
        val missing = File(context.cacheDir, "not-there-yet")
        missing.deleteRecursively()

        WallpaperStore(missing, WallpaperStore.preferences(context))

        assertTrue(missing.isDirectory)
    }

    @Test
    fun `photos without an extension are ignored`()
    {
        photo("no-extension")
        photo("real.jpg")

        assertEquals(listOf<WallpaperPhoto>(WallpaperPhoto(File(folder, "real.jpg"), "real")), store.photos())
    }
}
