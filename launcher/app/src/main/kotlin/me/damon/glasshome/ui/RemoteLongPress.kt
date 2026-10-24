package me.damon.glasshome.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Long-press on a TV remote.
 *
 * View.OnLongClickListener only fires for touch: a held D-pad centre produces key repeats, never a
 * long click. This watches the select key and runs [onLongPress] once it has been held long enough,
 * then swallows the click that would otherwise open the app.
 */
class RemoteLongPress(
    private val onLongPress: () -> Unit
)
{
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false

    private val pressed = Runnable { fire() }

    private fun fire()
    {
        if (fired) return
        fired = true
        handler.removeCallbacks(pressed)
        onLongPress()
    }

    /** Returns true when the event was consumed. */
    fun handle(event: KeyEvent): Boolean
    {
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && event.keyCode != KeyEvent.KEYCODE_ENTER) return false

        when (event.action)
        {
            KeyEvent.ACTION_DOWN ->
            {
                // The platform marks a held key and then repeats it; a plain remote hold repeats too.
                if (event.isLongPress || event.repeatCount > 0) fire()
                else if (event.repeatCount == 0)
                {
                    fired = false
                    handler.postDelayed(pressed, HOLD_MS)
                }
            }

            KeyEvent.ACTION_UP ->
            {
                handler.removeCallbacks(pressed)
                if (!fired) return false
                fired = false
                return true
            }
        }
        return false
    }

    private companion object
    {
        private const val HOLD_MS = 450L
    }
}
