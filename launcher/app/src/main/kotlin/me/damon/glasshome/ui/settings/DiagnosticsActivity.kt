package me.damon.glasshome.ui.settings

import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.damon.glasshome.GlassHomeApplication
import me.damon.glasshome.R
import me.damon.glasshome.ext.dp
import me.damon.glasshome.model.SystemStats

/**
 * What the TV is doing right now: storage, memory and CPU, read once a second while this screen is
 * open. Useful on a set whose CPU is shared with services the remote cannot switch off.
 */
class DiagnosticsActivity : ComponentActivity()
{
    private val reader
        get() = (application as GlassHomeApplication).container.systemStatsReader
    private val viewModel: DiagnosticsViewModel by viewModels { DiagnosticsViewModel.factory(reader) }

    private lateinit var storage: StatRow
    private lateinit var memory: StatRow
    private lateinit var cpu: StatRow
    private lateinit var launcher: StatRow
    private lateinit var uptime: StatRow

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        val container = findViewById<LinearLayout>(R.id.rows)
        storage = addRow(container, R.string.diagnostics_storage, withBar = true)
        memory = addRow(container, R.string.diagnostics_memory, withBar = true)
        cpu = addRow(container, R.string.diagnostics_cpu, withBar = false)
        launcher = addRow(container, R.string.diagnostics_launcher, withBar = false)
        uptime = addRow(container, R.string.diagnostics_uptime, withBar = false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stats.collect { render(it) }
            }
        }
    }

    private fun render(stats: SystemStats)
    {
        storage.show(
            value = getString(R.string.diagnostics_used_of, size(stats.storageUsedBytes), size(stats.storageTotalBytes)),
            detail = getString(R.string.diagnostics_free, size(stats.storageFreeBytes)),
            fraction = stats.storageUsedFraction
        )
        memory.show(
            value = getString(R.string.diagnostics_used_of, size(stats.memoryUsedBytes), size(stats.memoryTotalBytes)),
            detail = getString(R.string.diagnostics_free, size(stats.memoryFreeBytes)),
            fraction = stats.memoryUsedFraction
        )
        cpu.show(
            value = getString(R.string.diagnostics_percent, stats.cpuPercent.toInt()),
            detail = stats.loadAverage?.let { getString(R.string.diagnostics_load, it) }
                ?: getString(R.string.diagnostics_load_hidden)
        )
        launcher.show(
            value = size(stats.appMemoryBytes),
            detail = getString(R.string.diagnostics_launcher_detail)
        )
        uptime.show(value = duration(stats.uptimeMillis), detail = getString(R.string.diagnostics_uptime_detail))
    }

    // 1024-based, so the numbers match what df and top report on the TV itself.
    private fun size(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    private fun duration(millis: Long): String
    {
        val minutes = millis / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when
        {
            days > 0 -> getString(R.string.diagnostics_days, days, hours % 24)
            hours > 0 -> getString(R.string.diagnostics_hours, hours, minutes % 60)
            else -> getString(R.string.diagnostics_minutes, minutes)
        }
    }

    private fun addRow(container: LinearLayout, titleRes: Int, withBar: Boolean): StatRow
    {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(ROW_GAP_DP).toInt())
        }
        block.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 13f
            letterSpacing = 0.16f
            setTextColor(0x8CFFFFFF.toInt())
            typeface = resources.getFont(R.font.outfit)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(dp(LABEL_WIDTH_DP).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(this).apply {
            textSize = 24f
            setTextColor(0xF2FFFFFF.toInt())
            typeface = resources.getFont(R.font.outfit)
            fontVariationSettings = "'wght' 350"
            includeFontPadding = false
            fontFeatureSettings = "tnum"
        }
        val detail = TextView(this).apply {
            textSize = 13f
            setTextColor(0x99FFFFFF.toInt())
            typeface = resources.getFont(R.font.outfit)
            includeFontPadding = false
            setPadding(0, dp(4f).toInt(), 0, 0)
        }
        column.addView(value)
        column.addView(detail)

        val bar = if (!withBar) null else ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xE6FFFFFF.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3f).toInt()
            ).apply { topMargin = dp(9f).toInt() }
        }
        if (bar != null) column.addView(bar)

        block.addView(column)
        container.addView(block)
        return StatRow(value, detail, bar)
    }

    private class StatRow(
        private val value: TextView,
        private val detail: TextView,
        private val bar: ProgressBar?
    )
    {
        fun show(value: String, detail: String, fraction: Float = 0f)
        {
            // Re-setting identical text costs a measure pass on every tick, and CPU load changes by
            // the percent, so most ticks have nothing to say.
            if (this.value.text != value) this.value.text = value
            if (this.detail.text != detail) this.detail.text = detail
            val progress = (fraction * 1000).toInt().coerceIn(0, 1000)
            if (bar != null && bar.progress != progress) bar.progress = progress
        }
    }

    private companion object
    {
        private const val ROW_GAP_DP = 16f
        private const val LABEL_WIDTH_DP = 200f
    }
}
