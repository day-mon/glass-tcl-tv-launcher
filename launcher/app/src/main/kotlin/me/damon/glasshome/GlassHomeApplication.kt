package me.damon.glasshome

import android.app.Application
import android.content.Context
import me.damon.glasshome.data.HomeRepository

/** Holds the objects that outlive a single screen. */
class AppContainer(context: Context)
{
    private val appContext = context.applicationContext
    val homeRepository: HomeRepository by lazy { HomeRepository(appContext) }
}

class GlassHomeApplication : Application()
{
    val container: AppContainer by lazy { AppContainer(this) }
}
