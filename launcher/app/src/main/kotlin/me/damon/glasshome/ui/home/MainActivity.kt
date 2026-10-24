package me.damon.glasshome.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Trace
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.damon.glasshome.GlassHomeApplication
import me.damon.glasshome.R
import me.damon.glasshome.ext.dp
import me.damon.glasshome.model.AppEntry
import me.damon.glasshome.model.HomeState
import me.damon.glasshome.model.LayoutMode
import me.damon.glasshome.ui.ConnectionLabel
import me.damon.glasshome.ui.CornerLinks
import me.damon.glasshome.ui.GlassPillController
import me.damon.glasshome.ui.InputsMenu
import me.damon.glasshome.ui.Motion
import me.damon.glasshome.ui.RemoteLongPress
import me.damon.glasshome.ui.settings.SettingsActivity
import kotlin.math.max
import kotlin.math.min

/**
 * CleanFrame-style home: a photograph, the time, and a list of apps. Everything shown here comes
 * from [HomeViewModel]; this class wires the views, forwards input and renders state.
 */
class MainActivity : ComponentActivity(), AppListAdapter.Listener
{
    private val repository
        get() = (application as GlassHomeApplication).container.homeRepository
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.factory(repository) }

    private lateinit var adapter: AppListAdapter
    private lateinit var list: RecyclerView
    private lateinit var pill: GlassPillController
    private lateinit var inputs: InputsMenu
    private lateinit var btnWifi: TextView
    private lateinit var sideLinks: View
    private lateinit var listFade: View

    private var renderedLayout: LayoutMode? = null
    private var entrancePlayed = false
    private var lastFocusedRow = 0
    private var focusPlaced = false

    private val longPress = RemoteLongPress { pickUpFocusedRow() }

    private val handler = Handler(Looper.getMainLooper())
    private val minuteTick = object : Runnable
    {
        override fun run()
        {
            refreshConnectionLabel()
            handler.postDelayed(this, MINUTE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        Trace.beginSection("glasshome:onCreate")
        setContentView(R.layout.activity_main)

        val root = findViewById<FrameLayout>(R.id.root)
        btnWifi = findViewById(R.id.btnWifi)
        sideLinks = findViewById(R.id.sideLinks)
        listFade = findViewById(R.id.listFade)
        inputs = InputsMenu(this, root)

        list = findViewById(R.id.appList)
        list.itemAnimator = null
        list.setHasFixedSize(true)
        adapter = AppListAdapter(this, this)
        list.adapter = adapter

        pill = GlassPillController(this, root, findViewById(R.id.glass), list)

        findViewById<View>(R.id.btnInputs).setOnClickListener { inputs.show(it) }
        btnWifi.setOnClickListener { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        CornerLinks(links = cornerStack(), onReturnToList = { focusRow(lastFocusedRow) }).attach()

        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus?.id != R.id.content) pill.hide()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
        Trace.endSection()
    }

    override fun onResume()
    {
        super.onResume()
        // Undo the launch feedback in case the app we opened did not cover the screen.
        resetRowTransforms()
        viewModel.reload()
        refreshConnectionLabel()
        handler.postDelayed(minuteTick, MINUTE_MS)
    }

    override fun onPause()
    {
        super.onPause()
        handler.removeCallbacks(minuteTick)
    }

    private fun refreshConnectionLabel()
    {
        btnWifi.text = ConnectionLabel.of(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean
    {
        if (longPress.handle(event)) return true
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Home screen: Back only finishes rearranging.")
    override fun onBackPressed()
    {
        if (adapter.moving) adapter.drop()
    }

    private fun render(state: HomeState)
    {
        Trace.beginSection("glasshome:render")
        try
        {
            renderState(state)
        }
        finally
        {
            Trace.endSection()
        }
    }

    private fun renderState(state: HomeState)
    {
        val layout = state.appearance.layout
        if (renderedLayout != layout)
        {
            renderedLayout = layout
            applyLayout(layout)
        }

        pill.setMaterial(state.appearance.material)
        adapter.applyLook(state.appearance)
        adapter.submit(state.entries)

        // Icons are decoded and masked off the main thread so binding a row stays cheap. Focus is
        // placed afterwards, so the remote never lands on a row that is about to be rebound.
        lifecycleScope.launch(Dispatchers.Default) {
            adapter.warm(state.entries)
            withContext(Dispatchers.Main)
            {
                adapter.refreshIcons()
                // Focus is placed exactly once, after the rebind. Nothing here may move it later:
                // doing so steals focus from the corner links or the row the remote is on.
                if (!focusPlaced && state.entries.isNotEmpty())
                {
                    focusPlaced = true
                    playEntrance()
                    focusRow(lastFocusedRow)
                }
            }
        }
    }

    /** The list runs down the left; the dock sits along the bottom and scrolls sideways. */
    private fun applyLayout(mode: LayoutMode)
    {
        val params = list.layoutParams as FrameLayout.LayoutParams
        val links = sideLinks.layoutParams as FrameLayout.LayoutParams
        listFade.visibility = if (mode == LayoutMode.LIST) View.VISIBLE else View.GONE

        if (mode == LayoutMode.DOCK)
        {
            list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            list.setPadding(
                dp(40f).toInt(), dp(10f).toInt(), dp(40f).toInt(), dp(30f).toInt()
            )
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.BOTTOM

            // The dock owns the bottom edge, so the links move up under the clock.
            links.gravity = Gravity.TOP or Gravity.END
            links.topMargin = dp(96f).toInt()
            links.bottomMargin = 0
        }
        else
        {
            list.layoutManager = LinearLayoutManager(this)
            list.setPadding(
                dp(42f).toInt(), dp(92f).toInt(), 0, dp(24f).toInt()
            )
            params.width = dp(660f).toInt()
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START

            links.gravity = Gravity.BOTTOM or Gravity.END
            links.topMargin = 0
            links.bottomMargin = dp(38f).toInt()
        }
        list.layoutParams = params
        sideLinks.layoutParams = links
    }

    override fun onRowFocused(position: Int, content: View)
    {
        lastFocusedRow = position
        pill.show(content)
    }

    override fun onRowHorizontalKey(position: Int, keyCode: Int): Boolean
    {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) findViewById<View>(R.id.btnInputs).requestFocus()
        return true
    }

    override fun onRowPickedUp(position: Int)
    {
        toast(R.string.rearranging)
    }

    override fun onRowMoved(position: Int)
    {
        focusRow(position)
    }

    override fun onRowDropped(entries: List<AppEntry>)
    {
        viewModel.saveOrder(entries)
        toast(R.string.order_saved)
    }

    override fun onAppSelected(entry: AppEntry, content: View)
    {
        if (!Motion.enabled(this))
        {
            adapter.launch(entry)
            return
        }

        // The tile presses in and the list steps back, so the intent has something to land on.
        val press = Motion.duration(this, 120)
        content.animate().scaleX(0.94f).scaleY(0.94f).setDuration(press).start()
        list.animate().alpha(0.82f).setDuration(press).start()
        list.postDelayed({ adapter.launch(entry) }, press)
    }

    private fun playEntrance()
    {
        if (entrancePlayed || !Motion.enabled(this)) return
        entrancePlayed = true
        list.layoutAnimation = LayoutAnimationController(
            AnimationUtils.loadAnimation(this, R.anim.row_enter), 0.05f
        )
        list.scheduleLayoutAnimation()
    }

    private fun resetRowTransforms()
    {
        list.animate().cancel()
        list.alpha = 1f
        for (index in 0 until list.childCount)
        {
            list.getChildAt(index).findViewById<View>(R.id.content)?.apply {
                animate().cancel()
                scaleX = 1f
                scaleY = 1f
            }
        }
    }

    private fun pickUpFocusedRow()
    {
        val position = adapter.positionOf(currentFocus)
        if (position != RecyclerView.NO_POSITION) adapter.pickUp(position)
    }

    private fun cornerStack(): List<View> = listOf(
        findViewById(R.id.btnInputs),
        btnWifi,
        findViewById(R.id.btnSettings)
    )

    private fun focusRow(position: Int)
    {
        if (adapter.itemCount == 0) return
        val target = max(0, min(position, adapter.itemCount - 1))
        list.scrollToPosition(target)
        list.post {
            list.findViewHolderForAdapterPosition(target)
                ?.itemView
                ?.findViewById<View>(R.id.content)
                ?.requestFocus()
        }
    }

    private fun toast(message: Int)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object
    {
        private const val MINUTE_MS = 60_000L
    }

    private fun open(intent: Intent)
    {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try
        {
            startActivity(intent)
        }
        catch (error: ActivityNotFoundException)
        {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show()
        }
    }
}
