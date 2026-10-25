package me.damon.glasshome

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.damon.glasshome.data.SystemStatsReader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The diagnostics screen polls the TV twice a second while it is open. On this set the CPU is shared
 * with things the remote cannot switch off, so a reading has to stay cheap: this fails the build if
 * it ever stops being cheap.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsPerfTest
{
    @Test
    fun aReadingIsCheapEnoughToPoll()
    {
        val reader = SystemStatsReader(ApplicationProvider.getApplicationContext<Context>())
        reader.read()

        val runs = 40
        val started = System.nanoTime()
        repeat(runs) { reader.read() }
        val averageMs = (System.nanoTime() - started) / 1_000_000.0 / runs

        Log.i(TAG, "SystemStatsReader.read(): ${"%.2f".format(averageMs)} ms per reading")
        assertTrue("a reading took ${"%.2f".format(averageMs)} ms", averageMs < BUDGET_MS)
    }

    private companion object
    {
        /** Two polls a second can afford this many milliseconds and still be invisible. */
        private const val BUDGET_MS = 8.0
        private const val TAG = "DiagnosticsPerf"
    }
}
