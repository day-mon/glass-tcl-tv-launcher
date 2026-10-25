package me.damon.glasshome.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
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
import me.damon.glasshome.model.Appearance
import me.damon.glasshome.model.IconStyle
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.model.PillMaterial
import me.damon.glasshome.ui.Motion
import me.damon.glasshome.ui.WallpaperBackdrop
import me.damon.glasshome.ui.wallpapers.WallpapersActivity

/**
 * Appearance options, chosen with the remote: Up/Down between rows, Left/Right between the choices
 * in a row, OK to apply. Choices are written to the repository, so the home screen behind this one
 * already agrees when you press Back.
 */
class SettingsActivity : ComponentActivity()
{
    private val repository
        get() = (application as GlassHomeApplication).container.homeRepository
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(repository) }
    private val wallpapers
        get() = (application as GlassHomeApplication).container.wallpaperRepository

    private val chipRows = mutableListOf<ChipRow>()
    private var firstChip: View? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val container = findViewById<LinearLayout>(R.id.rows)
        addRow(container, R.string.settings_layout, LayoutMode.entries, { it.layout }) { viewModel.setLayout(it) }
        addRow(container, R.string.settings_material, PillMaterial.entries, { it.material }) { viewModel.setMaterial(it) }
        addRow(container, R.string.settings_icons, IconStyle.entries, { it.icons }) { viewModel.setIcons(it) }
        addActionRow(container, R.string.settings_background, R.string.settings_photos) {
            startActivity(Intent(this, WallpapersActivity::class.java))
        }
        addActionRow(container, R.string.settings_diagnostics, R.string.settings_view) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        addActionRow(container, R.string.settings_system, R.string.settings_open) {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        val backdrop = WallpaperBackdrop(this, findViewById(R.id.settingsWallpaper), restingAlpha = 0.55f)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                wallpapers.state.collect { backdrop.show(it) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appearance.collect { render(it) }
            }
        }

        container.post { firstChip?.requestFocus() }
    }

    private fun render(appearance: Appearance)
    {
        chipRows.forEach { row ->
            val current = row.current(appearance)
            row.chips.forEach { (value, chip) -> chip.isSelected = value == current }
        }
    }

    private fun <T : Enum<T>> addRow(
        container: LinearLayout,
        titleRes: Int,
        options: List<T>,
        current: (Appearance) -> T,
        onChoose: (T) -> Unit
    )
    {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(ROW_GAP_DP).toInt())
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        block.addView(label(getString(titleRes), 13f, 0.16f, 0x8CFFFFFF.toInt()).also { view ->
            view.layoutParams = LinearLayout.LayoutParams(dp(180f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Room for the focus pop, which would otherwise be clipped at the top.
            setPadding(0, dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt())
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val chips = LinkedHashMap<T, TextView>()
        options.forEach { option ->
            val chip = chip(prettify(option.name))
            chip.setOnClickListener { onChoose(option) }
            chips[option] = chip
            strip.addView(chip)
        }

        chipRows += ChipRow({ current(it) }, chips.entries.map { it.key as Enum<*> to it.value })
        firstChip = firstChip ?: chips.values.firstOrNull()
        block.addView(strip)
        container.addView(block)
    }

    /** A row that opens something else, rather than choosing a value in place. */
    private fun addActionRow(container: LinearLayout, titleRes: Int, chipRes: Int, onClick: () -> Unit)
    {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        block.addView(label(getString(titleRes), 13f, 0.16f, 0x8CFFFFFF.toInt()).also { view ->
            view.layoutParams = LinearLayout.LayoutParams(dp(180f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8f).toInt(), 0, dp(8f).toInt())
            clipChildren = false
            clipToPadding = false
        }
        strip.addView(chip(getString(chipRes)).also { view ->
            view.setOnClickListener { onClick() }
        })
        block.addView(strip)
        container.addView(block)
    }

    private fun label(text: String, sizeSp: Float, spacing: Float, colour: Int) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        letterSpacing = spacing
        setTextColor(colour)
        typeface = resources.getFont(R.font.outfit)
        includeFontPadding = false
    }

    private fun chip(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        typeface = resources.getFont(R.font.outfit)
        setTextColor(getColorStateList(R.color.chip_text))
        setBackgroundResource(R.drawable.chip_bg)
        isFocusable = true
        isClickable = true
        setPadding(dp(20f).toInt(), dp(11f).toInt(), dp(20f).toInt(), dp(11f).toInt())
        setOnFocusChangeListener { view, focused ->
            val scale = if (focused) 1.04f else 1f
            view.animate().scaleX(scale).scaleY(scale)
                .setDuration(Motion.duration(this@SettingsActivity, 140)).start()
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = dp(12f).toInt()
        layoutParams = params
    }

    private fun prettify(name: String): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private companion object
    {
        private const val ROW_GAP_DP = 13f
    }

    private class ChipRow(
        val current: (Appearance) -> Enum<*>,
        val chips: List<Pair<Enum<*>, TextView>>
    )
}
