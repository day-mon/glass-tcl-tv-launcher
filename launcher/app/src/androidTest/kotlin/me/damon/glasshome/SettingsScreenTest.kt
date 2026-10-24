package me.damon.glasshome

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import me.damon.glasshome.data.AppOrder
import me.damon.glasshome.ui.home.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the settings screen the way the remote does and checks the choice is remembered. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest
{
    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp()
    {
        prefs().edit().clear().commit()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.waitForIdle()
    }

    @After
    fun tearDown()
    {
        scenario.close()
        prefs().edit().clear().commit()
    }

    @Test
    fun theCornerLinkOpensTheSettingsScreen()
    {
        openSettings()

        assertTrue("settings screen did not open", device.wait(Until.hasObject(By.text("Layout")), WAIT_MS))
        assertTrue(device.hasObject(By.text("Material")))
        assertTrue(device.hasObject(By.text("Icons")))
    }

    @Test
    fun choosingTheDockLayoutIsSaved()
    {
        openSettings()
        device.wait(Until.hasObject(By.text("List")), WAIT_MS)

        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()

        assertEquals("DOCK", prefs().getString("layout_mode", null))
    }

    @Test
    fun choosingNamesInsteadOfGlyphsIsSaved()
    {
        openSettings()
        device.wait(Until.hasObject(By.text("Glyphs")), WAIT_MS)

        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()

        assertEquals("NAMES", prefs().getString("icon_style", null))
    }

    @Test
    fun backReturnsToTheHomeScreen()
    {
        openSettings()
        device.wait(Until.hasObject(By.text("Layout")), WAIT_MS)

        device.pressKeyCode(KeyEvent.KEYCODE_BACK)
        device.waitForIdle()

        assertTrue(device.wait(Until.hasObject(By.text("INPUTS")), WAIT_MS))
    }

    /** The SETTINGS link is the third entry in the corner stack. */
    private fun openSettings()
    {
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()
    }

    private fun press(keyCode: Int, times: Int)
    {
        repeat(times)
        {
            device.pressKeyCode(keyCode)
            device.waitForIdle()
        }
    }

    private fun prefs() =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)

    private companion object
    {
        private const val WAIT_MS = 5_000L
    }
}
