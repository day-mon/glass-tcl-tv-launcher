package me.damon.glasshome

import android.app.Application
import android.content.Context
import me.damon.glasshome.data.HomeRepository
import me.damon.glasshome.data.SystemStatsReader
import me.damon.glasshome.data.WallpaperRepository

/** Holds the objects that outlive a single screen. */
class AppContainer(context: Context)
{
    private val appContext = context.applicationContext
    val homeRepository: HomeRepository by lazy { HomeRepository(appContext) }
    val wallpaperRepository: WallpaperRepository by lazy { WallpaperRepository(appContext) }
    val systemStatsReader: SystemStatsReader by lazy { SystemStatsReader(appContext) }
}

class GlassHomeApplication : Application()
{
    val container: AppContainer by lazy { AppContainer(this) }
}
