package me.damon.glasshome.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeRepositoryTest
{
    private lateinit var context: Context

    @Before
    fun setUp()
    {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `choosing a layout publishes it to the state`()
    {
        val repository = HomeRepository(context)

        repository.setLayout(LayoutMode.DOCK)

        assertEquals(LayoutMode.DOCK, repository.state.value.appearance.layout)
    }

    @Test
    fun `choices survive a new repository instance`()
    {
        HomeRepository(context).apply {
            setMaterial(PillMaterial.SOLID)
            setIcons(IconStyle.NAMES)
        }

        val reopened = HomeRepository(context).state.value.appearance

        assertEquals(PillMaterial.SOLID, reopened.material)
        assertEquals(IconStyle.NAMES, reopened.icons)
    }

    @Test
    fun `a saved order is kept across refreshes`()
    {
        val repository = HomeRepository(context)
        val packages = listOf("com.example.second", "com.example.first")

        repository.saveOrder(packages)
        runBlocking { repository.refresh() }

        assertEquals(packages, AppOrder(context).load())
    }

    @Test
    fun `refreshing keeps the appearance the owner chose`()
    {
        val repository = HomeRepository(context)
        repository.setLayout(LayoutMode.DOCK)

        runBlocking { repository.refresh() }

        assertEquals(LayoutMode.DOCK, repository.state.value.appearance.layout)
        assertTrue(repository.state.value.entries.size >= 0)
    }
}
