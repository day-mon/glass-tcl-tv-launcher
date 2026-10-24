package me.damon.glasshome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.damon.glasshome.ui.AppListAdapter
import me.damon.glasshome.ui.ConnectionLabel
import me.damon.glasshome.ui.CornerLinks
import me.damon.glasshome.ui.GlassPillController
import me.damon.glasshome.ui.InputsMenu
import kotlin.math.max
import kotlin.math.min

/**
 * CleanFrame-style home: a photograph, the time, and a left-aligned list of apps. The glass pill
 * lives in [GlassPillController], the corner stack in [CornerLinks], the Inputs dropdown in
 * [InputsMenu]; this class only wires them to the screen.
 */
class MainActivity : Activity(), AppListAdapter.Listener
{
    private lateinit var adapter: AppListAdapter
    private lateinit var list: RecyclerView
    private lateinit var pill: GlassPillController
    private lateinit var inputs: InputsMenu
    private lateinit var btnWifi: TextView

    private var lastFocusedRow = 0

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
        setContentView(R.layout.activity_main)

        val root = findViewById<FrameLayout>(R.id.root)
        btnWifi = findViewById(R.id.btnWifi)
        inputs = InputsMenu(this, root)

        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        list.setHasFixedSize(true)
        list.itemAnimator = null
        adapter = AppListAdapter(this, this)
        list.adapter = adapter

        pill = GlassPillController(this, root, findViewById(R.id.glass), list)

        findViewById<View>(R.id.btnInputs).setOnClickListener { inputs.show(it) }
        btnWifi.setOnClickListener { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { open(Intent(Settings.ACTION_SETTINGS)) }

        CornerLinks(links = cornerStack(), onReturnToList = { focusRow(lastFocusedRow) }).attach()

        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus?.id != R.id.content) pill.hide()
        }
    }

    override fun onResume()
    {
        super.onResume()
        adapter.reload()
        refreshConnectionLabel()
        handler.postDelayed(minuteTick, MINUTE_MS)

        // Come back to the row we left from, or the top of the list.
        list.post {
            if (currentFocus?.id == R.id.content) return@post
            focusRow(lastFocusedRow)
        }
    }

    override fun onPause()
    {
        super.onPause()
        handler.removeCallbacks(minuteTick)
    }

    @Deprecated("Home screen: Back goes nowhere.")
    override fun onBackPressed()
    {
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

    private fun refreshConnectionLabel()
    {
        btnWifi.text = ConnectionLabel.of(this)
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

    private companion object
    {
        private const val MINUTE_MS = 60_000L
    }
}
