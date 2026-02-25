package com.extensionbox.app.modules

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.extensionbox.app.Fmt
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.components.SettingSlider
import java.util.*

class AppUsageModule : Module {
    private var ctx: Context? = null
    private var running = false
    private var usageMap = mutableMapOf<String, Long>()

    override fun key(): String = "app_usage"
    override fun name(): String = "App Usage"
    override fun emoji(): String = "📱"
    override fun description(): String = "Time spent in each application"
    override fun defaultEnabled(): Boolean = true
    override fun alive(): Boolean = running
    override fun priority(): Int = 22

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "usage_interval", 30000) } ?: 30000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
        tick()
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, now)
        if (stats != null) {
            val newMap = mutableMapOf<String, Long>()
            for (s in stats) {
                if (s.totalTimeInForeground > 0) {
                    val name = s.packageName.substringAfterLast('.')
                    newMap[name] = (newMap[name] ?: 0L) + s.totalTimeInForeground
                }
            }
            usageMap = newMap.toList().sortedByDescending { it.second }.take(10).toMap().toMutableMap()
        }
    }

    override fun compact(): String {
        val top = usageMap.maxByOrNull { it.value }
        return if (top != null) "Top: ${top.key} (${Fmt.duration(top.value)})" else "No usage data"
    }

    override fun detail(): String {
        val sb = StringBuilder()
        sb.append("📱 Today's App Usage:\n")
        if (usageMap.isEmpty()) {
            sb.append("   No data. Ensure Usage Access is granted.\n")
        } else {
            usageMap.toList().sortedByDescending { it.second }.forEach { (name, time) ->
                sb.append("   • ${name.padEnd(16)} ${Fmt.duration(time)}\n")
            }
        }
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val d = LinkedHashMap<String, String>()
        usageMap.forEach { (name, time) ->
            d["usage.$name"] = Fmt.duration(time)
        }
        return d
    }

    override fun checkAlerts(ctx: Context) {}

    @androidx.compose.runtime.Composable
    override fun settingsContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        var interval by remember { mutableStateOf(Prefs.getInt(ctx, "usage_interval", 30000).toFloat()) }
        SettingSlider(
            label = "Update Interval",
            value = interval,
            onValueChange = {
                interval = it
                Prefs.setInt(ctx, "usage_interval", it.toInt())
            },
            valueRange = 10000f..300000f,
            formatter = { if (it >= 60000f) "${it.toInt() / 60000}m" else "${it.toInt() / 1000}s" }
        )
    }

    @androidx.compose.runtime.Composable
    override fun composableContent(ctx: android.content.Context, sys: com.extensionbox.app.SystemAccess) {
        val hasPermission = remember { mutableStateOf(checkPermission(ctx)) }
        
        if (!hasPermission.value) {
            androidx.compose.material3.Button(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text("Grant Usage Access")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                usageMap.toList().sortedByDescending { it.second }.take(5).forEach { (name, time) ->
                    Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        androidx.compose.material3.Text(name, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = androidx.compose.ui.Modifier.weight(1f))
                        androidx.compose.material3.Text(Fmt.duration(time), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }

    private fun checkPermission(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
}
