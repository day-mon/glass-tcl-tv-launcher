package me.damon.glasshome.data

import android.content.Context
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.lang.management.ManagementFactory
import me.damon.glasshome.model.SystemStats

/**
 * Reads what Android lets an ordinary app know about the television it is running on, which is less
 * than it looks: this set denies /proc/stat and /proc/loadavg to apps (checked on the device), so the
 * system-wide CPU figure a shell can see is not available here at all.
 *
 * Storage comes from StatFs, memory from /proc/meminfo and /proc/self/statm, and CPU is this app's
 * own share from Process.getElapsedCpuTime. Everything is a file read or a syscall: the convenient
 * Android equivalents (ActivityManager.getMemoryInfo, Debug.getMemoryInfo) are binder round trips
 * that measured 90 ms per reading on this TV, against 2 ms for these.
 */
class SystemStatsReader(private val context: Context)
{
    private val storage by lazy { StatFs(Environment.getDataDirectory().absolutePath) }
    private val pageSize by lazy { Os.sysconf(OsConstants._SC_PAGESIZE).toInt() }

    private var previousCpuTime: Long = -1
    private var previousWallTime: Long = -1
    private var loadAverage: Float? = null
    private var loadAverageChecked = false

    fun read(): SystemStats
    {
        val now = SystemClock.elapsedRealtime()
        val cpuTime = Process.getElapsedCpuTime()

        storage.restat(Environment.getDataDirectory().absolutePath)
        val memory = memorySample()

        val cpuPercent = if (previousCpuTime < 0 || previousWallTime < 0)
        {
            0f
        }
        else
        {
            val wallDelta = now - previousWallTime
            val cpuDelta = cpuTime - previousCpuTime
            if (wallDelta <= 0) 0f else (cpuDelta.toFloat() / wallDelta * 100f).coerceIn(0f, 100f)
        }
        previousCpuTime = cpuTime
        previousWallTime = now

        return SystemStats(
            storageUsedBytes = storage.totalBytes - storage.availableBytes,
            storageTotalBytes = storage.totalBytes,
            memoryUsedBytes = if (memory == null) 0L else memory.first - memory.second,
            memoryTotalBytes = if (memory == null) 0L else memory.first,
            appMemoryBytes = residentBytes(),
            cpuPercent = cpuPercent,
            loadAverage = loadAverage(),
            uptimeMillis = now
        )
    }

    /** MemTotal and MemAvailable, in bytes. */
    private fun memorySample(): Pair<Long, Long>?
    {
        var total = -1L
        var available = -1L
        try
        {
            File("/proc/meminfo").useLines { lines ->
                for (line in lines)
                {
                    when
                    {
                        line.startsWith("MemTotal:") -> total = kilobytes(line)
                        line.startsWith("MemAvailable:") -> available = kilobytes(line)
                    }
                    if (total >= 0 && available >= 0) break
                }
            }
        }
        catch (error: Exception)
        {
            return null
        }
        return if (total > 0 && available >= 0) total to available else null
    }

    /** This process's resident set, from /proc/self/statm: the second field is resident pages. */
    private fun residentBytes(): Long = try
    {
        val statm = File("/proc/self/statm").readText()
        val residentPages = statm.split(' ').getOrNull(1)?.toLongOrNull() ?: 0L
        residentPages * pageSize
    }
    catch (error: Exception)
    {
        0L
    }

    /**
     * The one-minute load average, or null where the platform will not say. Read once and remembered:
     * this is the only reading here that might throw, and it will not start working later.
     */
    private fun loadAverage(): Float?
    {
        if (loadAverageChecked) return loadAverage
        loadAverageChecked = true

        loadAverage = try
        {
            ManagementFactory.getOperatingSystemMXBean().systemLoadAverage.toFloat().takeIf { it >= 0f }
        }
        catch (error: Throwable)
        {
            null
        }
        if (loadAverage == null) loadAverage = procLoadAverage()
        return loadAverage
    }

    private fun procLoadAverage(): Float? = try
    {
        File("/proc/loadavg").readText().substringBefore(' ').toFloatOrNull()
    }
    catch (error: Exception)
    {
        null
    }

    /** "MemTotal:  1903564 kB" — the number before the unit, converted to bytes. */
    private fun kilobytes(line: String): Long
    {
        var index = 0
        while (index < line.length && !line[index].isDigit()) index++
        val start = index
        while (index < line.length && line[index].isDigit()) index++
        return if (index == start) -1L else line.substring(start, index).toLongOrNull()?.times(1024) ?: -1L
    }
}
