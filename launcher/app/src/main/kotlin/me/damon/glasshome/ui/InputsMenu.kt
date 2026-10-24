package me.damon.glasshome.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import me.damon.glasshome.R
import me.damon.glasshome.ext.dp
import kotlin.math.max

/** The Inputs dropdown: HDMI ports with their connection state, then AV inputs and Live TV. */
class InputsMenu(
    private val activity: Activity,
    private val root: ViewGroup
)
{
    fun show(anchor: View)
    {
        val panel = activity.layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = buildPopup(anchor, panel, rows)
        addInputs(rows, popup)
        addLiveTv(rows, popup)

        if (rows.childCount == 0)
        {
            Toast.makeText(activity, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            popup.dismiss()
            return
        }

        unfold(anchor, panel, popup)
        rows.getChildAt(0).requestFocus()
    }

    private fun buildPopup(anchor: View, panel: View, rows: LinearLayout): PopupWindow
    {
        val popup = PopupWindow(
            panel, max(anchor.width, activity.dp(150f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT, true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))   // needed for Back to dismiss
        popup.isOutsideTouchable = true
        popup.animationStyle = 0
        popup.elevation = 0f
        popup.setOnDismissListener { anchor.requestFocus() }
        return popup
    }

    private fun addInputs(rows: LinearLayout, popup: PopupWindow)
    {
        val manager = activity.getSystemService(Activity.TV_INPUT_SERVICE) as? TvInputManager ?: return
        val inputs = manager.tvInputList
            .filter { it.isPassthroughInput }
            .sortedWith(compareBy({ it.type != TvInputInfo.TYPE_HDMI }, { it.loadLabel(activity).toString() }))

        var sawNonHdmi = false
        for (input in inputs)
        {
            val hdmi = input.type == TvInputInfo.TYPE_HDMI
            if (!hdmi && rows.childCount > 0 && !sawNonHdmi) addSeparator(rows)
            if (!hdmi) sawNonHdmi = true

            val label = input.loadLabel(activity)?.toString() ?: input.id
            val title = if (hdmi && !label.uppercase().contains("HDMI")) "HDMI $label" else label
            addRow(rows, popup, if (hdmi) R.drawable.ic_input else R.drawable.ic_av, title, manager.getInputState(input.id)) {
                open(
                    Intent(Intent.ACTION_VIEW).setDataAndType(
                        TvContract.buildChannelUriForPassthroughInput(input.id),
                        "vnd.android.cursor.item/channel"
                    )
                )
            }
        }
    }

    private fun addLiveTv(rows: LinearLayout, popup: PopupWindow)
    {
        val packageName = activity.getString(R.string.live_tv_package).trim()
        if (packageName.isEmpty()) return
        val intent = activity.packageManager.getLeanbackLaunchIntentForPackage(packageName) ?: return

        if (rows.childCount > 0) addSeparator(rows)
        addRow(rows, popup, R.drawable.ic_livetv, activity.getString(R.string.live_tv), -1) { open(intent) }
    }

    private fun unfold(anchor: View, panel: View, popup: PopupWindow)
    {
        panel.alpha = 0f
        panel.pivotY = 0f
        panel.scaleY = 0.92f
        panel.translationY = -activity.dp(8f)
        popup.showAsDropDown(anchor, -activity.dp(40f).toInt(), activity.dp(10f).toInt(), Gravity.START)
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
    }

    private fun addSeparator(rows: LinearLayout)
    {
        val line = View(activity)
        line.setBackgroundColor(0x1FFFFFFF)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(1f).toInt())
        val vertical = activity.dp(6f).toInt()
        val horizontal = activity.dp(12f).toInt()
        params.setMargins(horizontal, vertical, horizontal, vertical)
        rows.addView(line, params)
    }

    private fun addRow(
        rows: LinearLayout,
        popup: PopupWindow,
        iconRes: Int,
        title: String,
        state: Int,
        action: () -> Unit
    )
    {
        val row = activity.layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).text = title
        showState(row, state)
        row.setOnClickListener {
            popup.dismiss()
            action()
        }
        rows.addView(row)
    }

    private fun showState(row: View, state: Int)
    {
        val status = row.findViewById<TextView>(R.id.status)
        val dot = row.findViewById<View>(R.id.dot)
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        when (state)
        {
            TvInputManager.INPUT_STATE_CONNECTED ->
            {
                status.setText(R.string.connected)
                dot.visibility = View.VISIBLE
            }
            TvInputManager.INPUT_STATE_DISCONNECTED ->
            {
                status.setText(R.string.not_connected)
                dot.visibility = View.GONE
            }
            else ->
            {
                status.visibility = View.GONE
                dot.visibility = View.GONE
            }
        }
    }

    private fun open(intent: Intent)
    {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try
        {
            activity.startActivity(intent)
        }
        catch (error: ActivityNotFoundException)
        {
            Toast.makeText(activity, "Not available on this TV", Toast.LENGTH_SHORT).show()
        }
    }
}
