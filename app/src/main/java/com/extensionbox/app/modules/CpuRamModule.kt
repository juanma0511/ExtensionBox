package com.extensionbox.app.modules

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap
import java.util.Locale

class CpuRamModule : Module {
    private var ctx: Context? = null
    private var sys: SystemAccess? = null
    private var running = false

    private var prevCpuTimes: LongArray? = null
    private var cpuUsage = -1f
    private var cpuTemp = Float.NaN
    private var ramUsed: Long = 0
    private var ramTotal: Long = 0
    private var ramAvail: Long = 0
    
    // Advanced data
    private var coreFreqs = listOf<Long>()
    private var coreGovs = listOf<String>()
    private var gpuLoad = -1
    private var gpuFreq = 0L

    override fun key(): String = "cpu_ram"
    override fun name(): String = "CPU & RAM"
    override fun emoji(): String = "🧠"
    override fun description(): String = "CPU usage, temperature, memory status"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 15

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "cpu_interval", 5000) } ?: 5000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        val a = readCpuTimes()
        if (a != null) {
            SystemClock.sleep(250)
            val b = readCpuTimes()
            val u = calcCpuUsage(a, b)
            if (u >= 0f) cpuUsage = u
            prevCpuTimes = b ?: a
        } else {
            prevCpuTimes = null
            sys.readCpuUsageFallback().let { if (it >= 0f) cpuUsage = it }
        }

        cpuTemp = sys.readCpuTemp()
        running = true
    }

    override fun stop() {
        running = false
        sys = null
    }

    override fun tick() {
        val current = readCpuTimes()
        val u = calcCpuUsage(prevCpuTimes, current)
        if (u >= 0f) {
            cpuUsage = u
        } else {
            sys?.readCpuUsageFallback()?.let { if (it >= 0f) cpuUsage = it }
        }

        if (current != null) prevCpuTimes = current
        sys?.let { s ->
            s.readCpuTemp().let { cpuTemp = it }
            coreFreqs = s.getCpuCoreFrequencies()
            coreGovs = s.getCpuGovernors()
            val gpu = s.getGpuData()
            gpuLoad = gpu.first
            gpuFreq = gpu.second
        }

        try {
            val am = ctx?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramTotal = mi.totalMem
            ramAvail = mi.availMem
            ramUsed = ramTotal - ramAvail
        } catch (ignored: Exception) {
        }
    }

    private fun calcCpuUsage(prev: LongArray?, curr: LongArray?): Float {
        if (prev == null || curr == null) return -1f
        if (prev.size < 5 || curr.size < 5) return -1f

        val prevIdle = prev[3] + prev[4]
        val currIdle = curr[3] + curr[4]

        var prevTotal = 0L
        for (v in prev) prevTotal += v
        var currTotal = 0L
        for (v in curr) currTotal += v

        val totalDiff = currTotal - prevTotal
        val idleDiff = currIdle - prevIdle

        if (totalDiff <= 0) return -1f

        val usage = (totalDiff - idleDiff) * 100f / totalDiff.toFloat()
        return usage.coerceIn(0f, 100f)
    }

    private fun readCpuTimes(): LongArray? {
        val s = sys ?: return null
        return try {
            val line = s.readSysFile("/proc/stat")
            if (line != null && line.startsWith("cpu ")) {
                val parts = line.substring(4).trim().split(Regex("\\s+"))
                val times = LongArray(parts.size.coerceAtMost(7))
                for (i in times.indices) {
                    times[i] = parts[i].toLong()
                }
                times
            } else null
        } catch (ignored: Exception) {
            null
        }
    }

    override fun compact(): String {
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        val cpuStr = if (cpuUsage < 0f) "--" else "${cpuUsage.toInt()}%"
        val tempStr = if (!cpuTemp.isNaN()) " ${Fmt.temp(cpuTemp)}" else ""
        return "CPU:$cpuStr$tempStr RAM:${ramPct.toInt()}%"
    }

    override fun detail(): String {
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        val sb = StringBuilder()
        val cpuStr = if (cpuUsage < 0f) "--" else String.format(Locale.US, "%.1f%%", cpuUsage)
        sb.append("🧠 CPU: $cpuStr")
        if (!cpuTemp.isNaN()) {
            sb.append(" • ${Fmt.temp(cpuTemp)}")
        }
        sb.append("\n   RAM: ${Fmt.bytes(ramUsed)} / ${Fmt.bytes(ramTotal)} (${ramPct.toInt()}%)\n")
        
        if (gpuLoad >= 0) {
            sb.append("   GPU: $gpuLoad%")
            if (gpuFreq > 0) sb.append(" • ${gpuFreq / 1_000_000}MHz")
            sb.append("\n")
        }

        if (coreFreqs.isNotEmpty()) {
            sb.append("   Cores: ")
            coreFreqs.forEachIndexed { i, f ->
                if (f > 0) sb.append("${f/1000} ")
            }
            sb.append("\n")
        }

        sb.append("   Available RAM: ${Fmt.bytes(ramAvail)}")
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f
        d["cpu.usage"] = if (cpuUsage < 0f) "N/A" else String.format(Locale.US, "%.1f%%", cpuUsage)
        d["cpu.temp"] = if (!cpuTemp.isNaN()) Fmt.temp(cpuTemp) else "—"
        d["ram.used"] = Fmt.bytes(ramUsed)
        d["ram.total"] = Fmt.bytes(ramTotal)
        d["ram.available"] = Fmt.bytes(ramAvail)
        d["ram.pct"] = "${ramPct.toInt()}%"

        if (gpuLoad >= 0) {
            d["gpu.load"] = "$gpuLoad%"
            if (gpuFreq > 0) d["gpu.freq"] = "${gpuFreq / 1_000_000}MHz"
        }

        coreFreqs.forEachIndexed { i, f ->
            if (f > 0) d["cpu.core$i"] = "${f/1000}MHz"
        }
        
        return d
    }

    override fun checkAlerts(ctx: Context) {
        val alertOn = Prefs.getBool(ctx, "cpu_ram_alert", false)
        val thresh = Prefs.getInt(ctx, "cpu_ram_thresh", 90)
        val fired = Prefs.getBool(ctx, "cpu_ram_alert_fired", false)
        val ramPct = if (ramTotal > 0) ramUsed * 100f / ramTotal else 0f

        if (alertOn && ramPct >= thresh && !fired) {
            try {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(2003, NotificationCompat.Builder(ctx, "ebox_alerts")
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("🔴 High RAM Usage")
                    .setContentText("RAM at ${ramPct.toInt()}%")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build())
            } catch (ignored: Exception) {
            }
            Prefs.setBool(ctx, "cpu_ram_alert_fired", true)
        }
        if (fired && ramPct < thresh - 5) {
            Prefs.setBool(ctx, "cpu_ram_alert_fired", false)
        }
    }
}
