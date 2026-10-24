package me.damon.glasshome.ui

import android.os.Looper
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RemoteLongPressTest
{
    private var fired = 0
    private val detector = RemoteLongPress { fired++ }

    private fun event(action: Int, keyCode: Int = KeyEvent.KEYCODE_DPAD_CENTER) =
        KeyEvent(action, keyCode)

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `holding the select key long enough picks up once`()
    {
        assertFalse(detector.handle(event(KeyEvent.ACTION_DOWN)))
        advance(700)

        assertEquals(1, fired)
    }

    @Test
    fun `the release after a long press is swallowed so the app does not open`()
    {
        detector.handle(event(KeyEvent.ACTION_DOWN))
        advance(700)

        assertTrue(detector.handle(event(KeyEvent.ACTION_UP)))
        assertEquals(1, fired)
    }

    @Test
    fun `a short tap is passed through untouched`()
    {
        detector.handle(event(KeyEvent.ACTION_DOWN))
        advance(100)

        assertFalse(detector.handle(event(KeyEvent.ACTION_UP)))
        assertEquals(0, fired)
    }

    @Test
    fun `a second hold fires again`()
    {
        detector.handle(event(KeyEvent.ACTION_DOWN))
        advance(700)
        detector.handle(event(KeyEvent.ACTION_UP))
        detector.handle(event(KeyEvent.ACTION_DOWN))
        advance(700)

        assertEquals(2, fired)
    }

    @Test
    fun `other keys are ignored`()
    {
        assertFalse(detector.handle(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        advance(700)

        assertEquals(0, fired)
    }
}
