package me.damon.glasshome

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import me.damon.glasshome.data.AppOrder
import me.damon.glasshome.ui.home.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the launcher the way the remote does. App-independent: it reads whatever apps the device
 * has rather than assuming Netflix is installed, so it runs on an emulator as well as the TV.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest
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
        device.wait(Until.hasObject(By.focused(true)), WAIT_MS)
    }

    @After
    fun tearDown()
    {
        scenario.close()
        prefs().edit().clear().commit()
    }

    @Test
    fun aRowTakesFocusOnLaunch()
    {
        assertNotNull("no row had focus after launch", focused())
        assertTrue("focused row has no label", !focusedLabel().isNullOrEmpty())
    }

    @Test
    fun downMovesFocusToAnotherRow()
    {
        val first = focusedLabel()

        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.waitForIdle()

        val second = focusedLabel()
        assertNotNull(second)
        assertNotEquals("Down did not move focus", first, second)
    }

    @Test
    fun rightMovesFocusToTheCornerLinks()
    {
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.waitForIdle()

        val focused = focused()
        assertNotNull(focused)
        assertEquals("INPUTS", focused!!.text)
    }

    @Test
    fun leftFromTheCornerLinksReturnsToTheList()
    {
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
        device.waitForIdle()

        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
        device.waitForIdle()

        val label = focusedLabel()
        assertNotNull(label)
        assertNotEquals("INPUTS", label)
    }

    @Test
    fun longPressThenDownReordersAndPersists()
    {
        pickUp()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.waitForIdle()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()

        val movedDown = AppOrder(ApplicationProvider.getApplicationContext()).load()
        assertTrue("order was not saved", movedDown.size >= 2)

        pickUp()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
        device.waitForIdle()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()

        val movedBack = AppOrder(ApplicationProvider.getApplicationContext()).load()
        assertNotEquals("moving down then up left the order unchanged", movedDown, movedBack)
        assertEquals(movedDown.first(), movedBack[1])
    }

    @Test
    fun savedOrderSurvivesARelaunch()
    {
        pickUp()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
        device.waitForIdle()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()
        val saved = AppOrder(ApplicationProvider.getApplicationContext()).load()

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.waitForIdle()
        device.wait(Until.hasObject(By.focused(true)), WAIT_MS)

        assertEquals(saved, AppOrder(ApplicationProvider.getApplicationContext()).load())
    }

    @Test
    fun aShortSelectPressDoesNotStartRearranging()
    {
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
        device.waitForIdle()

        assertTrue("a tap should not save an order", prefs().all.isEmpty())
    }

    /** Holds the select key long enough for RemoteLongPress to fire. */
    private fun pickUp()
    {
        device.executeShellCommand("input keyevent --longpress ${KeyEvent.KEYCODE_DPAD_CENTER}")
        device.waitForIdle()
    }

    private fun focused(): UiObject2? = device.findObject(By.focused(true))

    /** The focused node is the row container; its label lives on a child TextView. */
    private fun focusedLabel(): String?
    {
        val focused = focused() ?: return null
        return focused.text ?: focused.findObject(By.clazz("android.widget.TextView"))?.text
    }

    private fun prefs() =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)

    private companion object
    {
        private const val WAIT_MS = 5_000L
    }
}
