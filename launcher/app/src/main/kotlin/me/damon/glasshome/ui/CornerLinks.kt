package me.damon.glasshome.ui

import android.view.KeyEvent
import android.view.View

/** D-pad behaviour for the bottom-right stack: walk the stack, Left returns to the app list. */
class CornerLinks(
    private val links: List<View>,
    private val onReturnToList: () -> Unit
)
{
    fun attach()
    {
        for ((index, link) in links.withIndex())
        {
            link.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode)
                {
                    KeyEvent.KEYCODE_DPAD_UP -> index == 0
                    KeyEvent.KEYCODE_DPAD_DOWN -> index == links.lastIndex
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    KeyEvent.KEYCODE_DPAD_LEFT ->
                    {
                        onReturnToList()
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
