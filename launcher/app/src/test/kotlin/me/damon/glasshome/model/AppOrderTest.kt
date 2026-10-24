package me.damon.glasshome.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppOrderTest
{
    private lateinit var order: AppOrder

    @Before
    fun setUp()
    {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences(AppOrder.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        order = AppOrder(context)
    }

    @Test
    fun `starts empty so the seed order applies`()
    {
        assertTrue(order.load().isEmpty())
    }

    @Test
    fun `saves and reloads a rearranged list`()
    {
        val packages = listOf("com.netflix.ninja", "com.hulu.livingroomplus", "com.wbd.stream")

        order.save(packages)

        assertEquals(packages, AppOrder(ApplicationProvider.getApplicationContext()).load())
    }

    @Test
    fun `clearing returns to the seed order`()
    {
        order.save(listOf("com.netflix.ninja"))

        order.clear()

        assertTrue(order.load().isEmpty())
    }

    @Test
    fun `blank entries are ignored`()
    {
        order.save(listOf("com.netflix.ninja", "", "com.wbd.stream"))

        assertEquals(listOf("com.netflix.ninja", "com.wbd.stream"), order.load())
    }
}
