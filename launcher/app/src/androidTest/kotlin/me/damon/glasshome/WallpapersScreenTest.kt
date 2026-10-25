package me.damon.glasshome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import me.damon.glasshome.data.AppOrder
import me.damon.glasshome.data.WallpaperStore
import me.damon.glasshome.ui.wallpapers.WallpapersActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The photo picker, driven the way the remote drives it. The placement test exists because a focused
 * tile used to be pushed past the edge of the grid, which cut its focus ring in half.
 */
@RunWith(AndroidJUnit4::class)
class WallpapersScreenTest
{
    private lateinit var device: UiDevice
    private lateinit var context: Context
    private lateinit var folder: File
    private var scenario: ActivityScenario<WallpapersActivity>? = null

    @Before
    fun setUp()
    {
        context = ApplicationProvider.getApplicationContext()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        prefs().edit().clear().commit()

        folder = WallpaperStore.folderFor(context)
        folder.deleteRecursively()
        folder.mkdirs()
        repeat(PHOTO_COUNT) { index -> writePhoto(index) }
    }

    @After
    fun tearDown()
    {
        scenario?.close()
        folder.deleteRecursively()
        prefs().edit().clear().commit()
    }

    @Test
    fun everyPhotoIsOfferedAfterTheBuiltInOne()
    {
        val grid = open()

        assertEquals(PHOTO_COUNT + 1, grid.adapter?.itemCount)
        assertTrue(device.hasObject(By.text("Built-in")))
    }

    @Test
    fun choosingAPhotoRemembersIt()
    {
        open()
        // The picker ignores input for a moment after opening, so the key that opened it cannot
        // choose anything by itself.
        Thread.sleep(IGNORE_INPUT_MS)

        val tile = device.wait(Until.findObject(By.text("photo-1")), WAIT_MS) ?: error("no tile to click")
        tile.click()
        device.waitForIdle()

        assertEquals("photo-1.jpg", prefs().getString(KEY_CURRENT, null))
        // Choosing by hand means "this one", so a changing background is switched off with it.
        assertEquals(false, prefs().getBoolean(KEY_SHUFFLE, false))
    }

    @Test
    fun aTileHasRoomForItsFocusPop()
    {
        val grid = open()
        // A tile only exists once the grid has laid out, which is after the first frame.
        device.wait(Until.hasObject(By.text("Built-in")), WAIT_MS) ?: error("no tiles laid out")

        var tile: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            grid.getChildAt(0)?.let {
                it.requestFocus()
                tile = it
            }
        }
        device.waitForIdle()
        val focused = tile ?: error("no tile to focus")

        // The tile is a direct child of the grid, so its bounds are already in the grid's space.
        // A focused tile used to sit past the grid's edge, which cut its focus ring in half.
        assertTrue("tile starts at ${focused.left}", focused.left >= 0)
        assertTrue("tile starts at ${focused.top}", focused.top >= 0)
        assertTrue("tile ends at ${focused.right} of ${grid.width}", focused.right <= grid.width)

        // Focusing scales the tile, and that growth has to land inside its own margin, or the ring
        // is clipped exactly the way it was before.
        val margins = focused.layoutParams as ViewGroup.MarginLayoutParams
        val growthPx = focused.width * FOCUS_GROWTH_PER_SIDE
        assertTrue("grows ${growthPx}px into a ${margins.leftMargin}px margin", growthPx <= margins.leftMargin)
    }

    @Test
    fun shuffleIsOffUntilItIsSwitchedOn()
    {
        open()

        val switch = device.wait(Until.findObject(By.text("Shuffle: off")), WAIT_MS)
            ?: error("shuffle switch missing")
        switch.click()

        assertTrue(device.wait(Until.hasObject(By.text("Shuffle: on")), WAIT_MS))
        assertEquals(true, prefs().getBoolean(KEY_SHUFFLE, false))
    }

    private fun open(): RecyclerView
    {
        val launched = ActivityScenario.launch(WallpapersActivity::class.java)
        scenario = launched
        device.waitForIdle()

        var grid: RecyclerView? = null
        launched.onActivity { grid = it.findViewById(R.id.photos) }
        device.waitForIdle()
        return grid ?: error("no grid")
    }

    private fun writePhoto(index: Int)
    {
        val bitmap = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(20 + index * 20, 60, 120))
        File(folder, "photo-$index.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
    }

    private fun prefs() = context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)

    private companion object
    {
        private const val PHOTO_COUNT = 5
        private const val WAIT_MS = 4_000L

        /** Matches the picker's own guard against the key press that opened it. */
        private const val IGNORE_INPUT_MS = 500L

        private const val KEY_CURRENT = "wallpaper_current"
        private const val KEY_SHUFFLE = "wallpaper_shuffle"

        /** Half of the tile's 1.03 focus scale: what each side grows by when focused. */
        private const val FOCUS_GROWTH_PER_SIDE = 0.015f
    }
}
