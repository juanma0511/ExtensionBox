package com.extensionbox.app.modules

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.*
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.max

class SleepModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var elapsedStart: Long = 0
    private var uptimeStart: Long = 0

    override fun key(): String = "sleep"
    override fun name(): String = "Deep Sleep"
    override fun emoji(): String = "😴"
    override fun description(): String = "CPU sleep vs awake ratio"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 30

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "slp_interval", 30000) } ?: 30000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        elapsedStart = SystemClock.elapsedRealtime()
        uptimeStart = SystemClock.uptimeMillis()
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {}

    private fun deepPct(): Float {
        val el = SystemClock.elapsedRealtime() - elapsedStart
        val up = SystemClock.uptimeMillis() - uptimeStart
        val ds = max(0, el - up)
        return if (el > 0) ds * 100f / el else 0f
    }

    override fun compact(): String = "Sleep:${deepPct().toInt()}%"

    override fun detail(): String {
        val el = SystemClock.elapsedRealtime() - elapsedStart
        val up = SystemClock.uptimeMillis() - uptimeStart
        val ds = max(0, el - up)
        val pct = deepPct()
        return "😴 Deep Sleep: ${Fmt.duration(ds)} (${String.format(Locale.US, "%.1f%%", pct)})\n" +
               "   Awake: ${Fmt.duration(up)} (${String.format(Locale.US, "%.1f%%", 100 - pct)})"
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val el = SystemClock.elapsedRealtime() - elapsedStart
        val up = SystemClock.uptimeMillis() - uptimeStart
        val ds = max(0, el - up)
        val pct = deepPct()
        val d = LinkedHashMap<String, String>()
        d["sleep.deep_time"] = Fmt.duration(ds)
        d["sleep.deep_percentage"] = Fmt.percentage(pct)
        d["sleep.awake_time"] = Fmt.duration(up)
        d["sleep.awake_percentage"] = Fmt.percentage(100 - pct)
        return d
    }

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { 
            mutableStateOf(Prefs.getInt(ctx, "slp_interval", 30000).toFloat()) 
        }
        SettingSlider(
            label = "Update Interval",
            value = interval,
            onValueChange = {
                interval = it
                Prefs.setInt(ctx, "slp_interval", it.toInt())
            },
            valueRange = 5000f..300000f,
            formatter = { if (it >= 60000f) "${it.toInt() / 60000}m" else "${it.toInt() / 1000}s" }
        )
    }

    override fun checkAlerts(ctx: Context) {}
}
