package me.damon.glasshome.model

/** One reading of what the TV is doing right now. */
data class SystemStats(
    val storageUsedBytes: Long = 0,
    val storageTotalBytes: Long = 0,
    val memoryUsedBytes: Long = 0,
    val memoryTotalBytes: Long = 0,
    val appMemoryBytes: Long = 0,
    /** This app's CPU time as a share of one core, 0..100. */
    val cpuPercent: Float = 0f,
    /** One-minute load average, or null on a TV that keeps it from apps. */
    val loadAverage: Float? = null,
    val uptimeMillis: Long = 0
)
{
    val storageFreeBytes: Long get() = (storageTotalBytes - storageUsedBytes).coerceAtLeast(0)

    val memoryFreeBytes: Long get() = (memoryTotalBytes - memoryUsedBytes).coerceAtLeast(0)

    val storageUsedFraction: Float
        get() = if (storageTotalBytes > 0) storageUsedBytes.toFloat() / storageTotalBytes else 0f

    val memoryUsedFraction: Float
        get() = if (memoryTotalBytes > 0) memoryUsedBytes.toFloat() / memoryTotalBytes else 0f
}
