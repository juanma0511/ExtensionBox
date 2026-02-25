package com.extensionbox.app.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap

class ConnectionModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var connType = "None"
    private var wifiSsid = "—"
    private var wifiRssi = "—"
    private var wifiLinkSpeed = 0
    private var wifiSpeed = "—"
    private var wifiFreq = "—"
    private var carrier = "—"
    private var netType = "—"
    private var vpnActive = false

    override fun key(): String = "connection"
    override fun name(): String = "Connection Info"
    override fun emoji(): String = "📡"
    override fun description(): String = "WiFi, cellular, VPN status"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 90

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "con_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            if (net == null) {
                connType = "None"
                return
            }

            val caps = cm.getNetworkCapabilities(net)
            if (caps == null) {
                connType = "None"
                return
            }

            vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    connType = "WiFi"
                    readWifi()
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    connType = "Mobile"
                    readCell()
                }
                else -> {
                    connType = "Other"
                }
            }
        } catch (e: Exception) {
            connType = "Error"
        }
    }

    private fun readWifi() {
        val c = ctx ?: return
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            val wi = caps?.transportInfo as? android.net.wifi.WifiInfo
            
            if (wi != null) {
                val ssid = wi.ssid
                wifiSsid = if (ssid != null && ssid != "<unknown ssid>") {
                    ssid.replace("\"", "")
                } else {
                    "Hidden"
                }
                wifiRssi = "${wi.rssi} dBm"
                wifiLinkSpeed = wi.linkSpeed
                wifiSpeed = "$wifiLinkSpeed Mbps"
                val freq = wi.frequency
                wifiFreq = if (freq > 4900) "5 GHz" else "2.4 GHz"
            }
        } catch (e: Exception) {
            wifiSsid = "Error"
        }
    }

    private fun readCell() {
        val c = ctx ?: return
        try {
            val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            carrier = tm.networkOperatorName.takeIf { it.isNotEmpty() } ?: "Unknown"
            val type = try { tm.dataNetworkType } catch (e: SecurityException) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
            netType = when (type) {
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            carrier = "—"
            netType = "—"
        }
    }

    override fun compact(): String {
        return when (connType) {
            "WiFi" -> "WiFi $wifiRssi"
            "Mobile" -> "$carrier $netType"
            else -> "📡 $connType"
        }
    }

    override fun detail(): String {
        val sb = StringBuilder()
        when (connType) {
            "WiFi" -> {
                sb.append("📡 WiFi: $wifiSsid ($wifiRssi)\n")
                sb.append("   $wifiSpeed • $wifiFreq")
            }
            "Mobile" -> {
                sb.append("📡 Mobile: $carrier $netType")
            }
            else -> {
                sb.append("📡 $connType")
            }
        }
        if (vpnActive) sb.append("\n   VPN: Active")
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        d["conn.type"] = connType
        if ("WiFi" == connType) {
            d["conn.ssid"] = wifiSsid
            d["conn.rssi"] = wifiRssi
            d["conn.speed"] = wifiSpeed
            d["conn.freq"] = wifiFreq
        } else if ("Mobile" == connType) {
            d["conn.carrier"] = carrier
            d["conn.network"] = netType
        }
        d["conn.vpn"] = if (vpnActive) "Active" else "None"
        return d
    }

    override fun checkAlerts(ctx: Context) {}
}
