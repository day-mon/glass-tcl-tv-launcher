package me.damon.glasshome.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSettingsTest
{
    private lateinit var context: Context

    @Before
    fun setUp()
    {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `defaults are the list layout, glass pill and glyph icons`()
    {
        assertEquals(
            Appearance(LayoutMode.LIST, PillMaterial.GLASS, IconStyle.GLYPHS),
            HomeSettings(context).read()
        )
    }

    @Test
    fun `a written appearance comes back`()
    {
        HomeSettings(context).write(Appearance(LayoutMode.DOCK, PillMaterial.CLEAR, IconStyle.NAMES))

        assertEquals(
            Appearance(LayoutMode.DOCK, PillMaterial.CLEAR, IconStyle.NAMES),
            HomeSettings(context).read()
        )
    }

    @Test
    fun `an unknown saved value falls back to the default`()
    {
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("layout_mode", "SIDEWAYS").commit()

        assertEquals(LayoutMode.LIST, HomeSettings(context).read().layout)
    }
}
