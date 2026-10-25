package me.damon.glasshome.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapTest
{
    @Test
    fun `a photo the size of the screen is not subsampled`()
    {
        assertEquals(1, sampleSizeFor(1920, 1080, 1080))
    }

    @Test
    fun `a photo below the screen size is never enlarged`()
    {
        assertEquals(1, sampleSizeFor(640, 480, 1080))
    }

    @Test
    fun `a screen-sized photo is halved once`()
    {
        assertEquals(2, sampleSizeFor(3840, 2160, 1080))
    }

    @Test
    fun `a camera photo is shrunk until it only just covers the screen`()
    {
        // 24 MP: a quarter leaves 3000x2000, which still covers 1920x1080; an eighth would not.
        assertEquals(2, sampleSizeFor(6000, 4000, 1080))
    }

    @Test
    fun `the result always covers the target`()
    {
        val targets = listOf(720, 1080, 2160)
        val sizes = listOf(640 to 480, 1920 to 1080, 4032 to 3024, 6000 to 4000, 12000 to 3000)

        sizes.forEach { (width, height) ->
            targets.forEach { target ->
                val sample = sampleSizeFor(width, height, target)
                assertTrue("$width x $height at $target", sample >= 1)
                // A further halving would drop below the target, which is what "largest" means.
                assertTrue(
                    "$width x $height at $target chose $sample",
                    width / (sample * 2) < target || height / (sample * 2) < target
                )
            }
        }
    }
}
