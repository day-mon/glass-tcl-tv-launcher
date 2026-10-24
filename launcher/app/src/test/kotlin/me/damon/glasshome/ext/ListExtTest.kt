package me.damon.glasshome.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListExtTest
{
    @Test
    fun `moving down shifts the rest up`()
    {
        val items = mutableListOf("a", "b", "c", "d")

        assertTrue(items.move(0, 1))

        assertEquals(listOf("b", "a", "c", "d"), items)
    }

    @Test
    fun `moving up shifts the rest down`()
    {
        val items = mutableListOf("a", "b", "c", "d")

        assertTrue(items.move(3, 1))

        assertEquals(listOf("a", "d", "b", "c"), items)
    }

    @Test
    fun `moving onto itself changes nothing`()
    {
        val items = mutableListOf("a", "b")

        assertFalse(items.move(1, 1))

        assertEquals(listOf("a", "b"), items)
    }

    @Test
    fun `out of range moves are refused`()
    {
        val items = mutableListOf("a", "b")

        assertFalse(items.move(0, 5))
        assertFalse(items.move(-1, 0))

        assertEquals(listOf("a", "b"), items)
    }
}
