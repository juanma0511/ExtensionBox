package com.extensionbox.app.modules

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap
import java.util.Locale

class NetworkModule : Module {

    private var ctx: Context? = null
    private var running = false

    private var prevRx = 0L
    private var prevTx = 0L
    private var prevTime = 0L
    private var dlSpeed = 0L
    private var ulSpeed = 0L

    private var prevDlSpeed = 0L
    private var prevUlSpeed = 0L

    // Interface stats
    private var interfaceStats = mapOf<String, Pair<Long, Long>>()
    private var prevInterfaceStats = mapOf<String, Pair<Long, Long>>()
    private var sys: SystemAccess? = null

    override fun key(): String = "network"
    override fun name(): String = "Network Speed"
    override fun emoji(): String = "📶"
    override fun description(): String = "Real-time download and upload speed"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 40

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "net_interval", 3000) } ?: 3000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        this.sys = sys
        prevTime = SystemClock.elapsedRealtime()

        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()

        prevRx = if (rx != TrafficStats.UNSUPPORTED.toLong()) rx else 0
        prevTx = if (tx != TrafficStats.UNSUPPORTED.toLong()) tx else 0
        
        if (sys.isEnhanced()) {
            prevInterfaceStats = sys.getNetworkInterfaceStats()
        }

        dlSpeed = 0
        ulSpeed = 0
        prevDlSpeed = 0
        prevUlSpeed = 0
        running = true
    }

    override fun stop() {
        running = false
        dlSpeed = 0
        ulSpeed = 0
        sys = null
    }

    override fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dtMs = now - prevTime
        if (dtMs <= 0) return

        val s = sys
        if (s != null && s.isEnhanced()) {
            val currentStats = s.getNetworkInterfaceStats()
            var totalDl = 0L
            var totalUl = 0L
            
            currentStats.forEach { (iface, stats) ->
                val prev = prevInterfaceStats[iface]
                if (prev != null) {
                    val dl = (stats.first - prev.first).coerceAtLeast(0)
                    val ul = (stats.second - prev.second).coerceAtLeast(0)
                    totalDl += dl
                    totalUl += ul
                }
            }
            
            val rawDl = totalDl * 1000 / dtMs
            val rawUl = totalUl * 1000 / dtMs
            
            dlSpeed = (rawDl * 6 + prevDlSpeed * 4) / 10
            ulSpeed = (rawUl * 6 + prevUlSpeed * 4) / 10
            
            prevInterfaceStats = currentStats
            interfaceStats = currentStats
        } else {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()

            if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
                var rxDelta = rx - prevRx
                var txDelta = tx - prevTx

                if (rxDelta < 0) rxDelta = 0
                if (txDelta < 0) txDelta = 0

                val rawDl = rxDelta * 1000 / dtMs
                val rawUl = txDelta * 1000 / dtMs

                dlSpeed = (rawDl * 6 + prevDlSpeed * 4) / 10
                ulSpeed = (rawUl * 6 + prevUlSpeed * 4) / 10

                prevRx = rx
                prevTx = tx
            }
        }

        prevDlSpeed = dlSpeed
        prevUlSpeed = ulSpeed
        prevTime = now
    }

    override fun compact(): String = "↓${Fmt.speed(dlSpeed)} ↑${Fmt.speed(ulSpeed)}"

    override fun detail(): String {
        val sb = StringBuilder()
        sb.append("📶 Download: ${Fmt.speed(dlSpeed)}\n   Upload: ${Fmt.speed(ulSpeed)}\n")
        if (interfaceStats.isNotEmpty()) {
            sb.append("   Interfaces:\n")
            interfaceStats.forEach { (name, _) ->
                if (name != "lo") {
                    sb.append("   • $name\n")
                }
            }
        }
        return sb.toString().trim()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["net.download"] = Fmt.speed(dlSpeed)
        d["net.upload"] = Fmt.speed(ulSpeed)
        interfaceStats.forEach { (name, stats) ->
            d["net.iface.$name.rx"] = Fmt.bytes(stats.first)
            d["net.iface.$name.tx"] = Fmt.bytes(stats.second)
        }
        return d
    }

    override fun checkAlerts(ctx: Context) {}
}
