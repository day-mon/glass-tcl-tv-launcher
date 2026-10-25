package me.damon.glasshome

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every bundled glyph must be loadable. Android's PathParser is stricter than SVG, so a logo that
 * renders fine in a browser can still throw when inflated — which on a TV means a launcher crash.
 */
@RunWith(AndroidJUnit4::class)
class GlyphIntegrityTest
{
    @Test
    fun everyBundledGlyphInflates()
    {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failures = mutableListOf<String>()

        R.drawable::class.java.declaredFields
            .filter { it.name.startsWith("glyph_") }
            .sortedBy { it.name }
            .forEach { field ->
                field.isAccessible = true
                val id = field.getInt(null)
                try
                {
                    if (context.getDrawable(id) == null) failures += "${field.name}: null drawable"
                }
                catch (error: Exception)
                {
                    failures += "${field.name}: ${error.message?.take(120)}"
                }
            }

        assertTrue("glyphs failed to inflate:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
