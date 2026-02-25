package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.*
import com.extensionbox.app.ui.viewmodel.DashboardViewModel

@Composable
fun ModuleDetailScreen(
    moduleKey: String,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val dashData = viewModel.dashData.collectAsState().value
    val historyData = viewModel.historyData.collectAsState().value
    val sysAccess = viewModel.sysAccess.collectAsState().value

    val data = dashData[moduleKey] ?: emptyMap()
    val history = historyData[moduleKey] ?: emptyList()
    val module = com.extensionbox.app.ui.ModuleRegistry.getModule(moduleKey)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (history.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("History (15m)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        Sparkline(
                            points = extractPoints(moduleKey, history),
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            fillGradient = true,
                            animate = true
                        )
                    }
                }
            }
        }

        if (data.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Real-time Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val items = data.toList()
                    items.chunked(2).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { pair ->
                                val label = pair.first.substringAfterLast('.').replace("_", " ").replaceFirstChar { it.uppercase() }
                                StatItem(label = label, value = pair.second, modifier = Modifier.weight(1f))
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    if (module != null && sysAccess != null) {
                        module.composableContent(context, sysAccess)
                    }
                }
            }
        }

        if (module != null && sysAccess != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Extension Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Common Refresh Interval
                    val intervalKey = when(moduleKey) {
                        "battery" -> "bat_interval"
                        "cpu_ram" -> "cpu_interval"
                        "screen" -> "scr_interval"
                        "sleep" -> "slp_interval"
                        "network" -> "net_interval"
                        "data" -> "dat_interval"
                        "unlock" -> "ulk_interval"
                        "storage" -> "sto_interval"
                        "connection" -> "con_interval"
                        "uptime" -> "upt_interval"
                        "steps" -> "stp_interval"
                        "speedtest" -> "spd_interval"
                        "fap" -> "fap_interval"
                        else -> null
                    }

                    intervalKey?.let { prefKey ->
                        var interval by remember { mutableStateOf(Prefs.getInt(context, prefKey, 5000).toFloat()) }
                        SettingSlider(
                            label = "Update Interval",
                            value = interval,
                            valueRange = if (moduleKey == "storage" || moduleKey == "data") 10000f..600000f else 1000f..60000f,
                            onValueChange = {
                                interval = it
                                Prefs.setInt(context, prefKey, it.toInt())
                            },
                            formatter = { 
                                if (it >= 60000f) "${it.toInt() / 60000}m"
                                else "${it.toInt() / 1000}s" 
                            }
                        )
                    }

                    // Module Specific Settings from original ExtensionsScreen
                    when(moduleKey) {
                        "battery" -> {
                            var lowAlert by remember { mutableStateOf(Prefs.getBool(context, "bat_low_alert", true)) }
                            SettingSwitch(
                                label = "Low Battery Alert",
                                checked = lowAlert,
                                onCheckedChange = {
                                    lowAlert = it
                                    Prefs.setBool(context, "bat_low_alert", it)
                                }
                            )
                            if (lowAlert) {
                                var lowThresh by remember { mutableStateOf(Prefs.getInt(context, "bat_low_thresh", 15).toFloat()) }
                                SettingSlider(
                                    label = "Low Alert Threshold",
                                    value = lowThresh,
                                    valueRange = 5f..50f,
                                    onValueChange = {
                                        lowThresh = it
                                        Prefs.setInt(context, "bat_low_thresh", it.toInt())
                                    },
                                    formatter = { "${it.toInt()}%" }
                                )
                            }
                            var tempAlert by remember { mutableStateOf(Prefs.getBool(context, "bat_temp_alert", true)) }
                            SettingSwitch(
                                label = "High Temp Alert",
                                checked = tempAlert,
                                onCheckedChange = {
                                    tempAlert = it
                                    Prefs.setBool(context, "bat_temp_alert", it)
                                }
                            )
                            if (tempAlert) {
                                var tempThresh by remember { mutableStateOf(Prefs.getInt(context, "bat_temp_thresh", 42).toFloat()) }
                                SettingSlider(
                                    label = "Temp Threshold",
                                    value = tempThresh,
                                    valueRange = 30f..60f,
                                    onValueChange = {
                                        tempThresh = it
                                        Prefs.setInt(context, "bat_temp_thresh", it.toInt())
                                    },
                                    formatter = { "${it.toInt()}°C" }
                                )
                            }
                        }
                        "cpu_ram" -> {
                            var ramAlert by remember { mutableStateOf(Prefs.getBool(context, "cpu_ram_alert", false)) }
                            SettingSwitch(
                                label = "High RAM Alert",
                                checked = ramAlert,
                                onCheckedChange = {
                                    ramAlert = it
                                    Prefs.setBool(context, "cpu_ram_alert", it)
                                }
                            )
                            if (ramAlert) {
                                var ramThresh by remember { mutableStateOf(Prefs.getInt(context, "cpu_ram_thresh", 90).toFloat()) }
                                SettingSlider(
                                    label = "RAM Alert Threshold",
                                    value = ramThresh,
                                    valueRange = 50f..98f,
                                    onValueChange = {
                                        ramThresh = it
                                        Prefs.setInt(context, "cpu_ram_thresh", it.toInt())
                                    },
                                    formatter = { "${it.toInt()}%" }
                                )
                            }
                        }
                        "screen" -> {
                            var showDrain by remember { mutableStateOf(Prefs.getBool(context, "scr_show_drain", true)) }
                            SettingSwitch(
                                label = "Show Drain Rates",
                                checked = showDrain,
                                onCheckedChange = {
                                    showDrain = it
                                    Prefs.setBool(context, "scr_show_drain", it)
                                }
                            )
                            var showYesterday by remember { mutableStateOf(Prefs.getBool(context, "scr_show_yesterday", true)) }
                            SettingSwitch(
                                label = "Show Yesterday",
                                checked = showYesterday,
                                onCheckedChange = {
                                    showYesterday = it
                                    Prefs.setBool(context, "scr_show_yesterday", it)
                                }
                            )
                            var timeLimit by remember { mutableStateOf(Prefs.getInt(context, "scr_time_limit", 0).toFloat()) }
                            SettingSlider(
                                label = "Daily Screen Time Limit",
                                value = timeLimit,
                                valueRange = 0f..600f,
                                steps = 12,
                                onValueChange = {
                                    timeLimit = it
                                    Prefs.setInt(context, "scr_time_limit", it.toInt())
                                },
                                formatter = { if (it == 0f) "Disabled" else "${it.toInt()}m" }
                            )
                        }
                        "steps" -> {
                            var goal by remember { mutableStateOf(Prefs.getInt(context, "stp_goal", 10000).toFloat()) }
                            SettingSlider(
                                label = "Daily Goal",
                                value = goal,
                                valueRange = 0f..30000f,
                                onValueChange = {
                                    goal = it
                                    Prefs.setInt(context, "stp_goal", it.toInt())
                                },
                                formatter = { if (it == 0f) "No Goal" else "${it.toInt()} steps" }
                            )
                            var stride by remember { mutableStateOf(Prefs.getInt(context, "stp_stride_cm", 75).toFloat()) }
                            SettingSlider(
                                label = "Step Length",
                                value = stride,
                                valueRange = 30f..120f,
                                onValueChange = {
                                    stride = it
                                    Prefs.setInt(context, "stp_stride_cm", it.toInt())
                                },
                                formatter = { "${it.toInt()} cm" }
                            )
                            var showDistance by remember { mutableStateOf(Prefs.getBool(context, "stp_show_distance", true)) }
                            SettingSwitch(
                                label = "Show Distance",
                                checked = showDistance,
                                onCheckedChange = {
                                    showDistance = it
                                    Prefs.setBool(context, "stp_show_distance", it)
                                }
                            )
                            var showGoal by remember { mutableStateOf(Prefs.getBool(context, "stp_show_goal", true)) }
                            SettingSwitch(
                                label = "Show Goal",
                                checked = showGoal,
                                onCheckedChange = {
                                    showGoal = it
                                    Prefs.setBool(context, "stp_show_goal", it)
                                }
                            )
                            var showYesterday by remember { mutableStateOf(Prefs.getBool(context, "stp_show_yesterday", true)) }
                            SettingSwitch(
                                label = "Show Yesterday",
                                checked = showYesterday,
                                onCheckedChange = {
                                    showYesterday = it
                                    Prefs.setBool(context, "stp_show_yesterday", it)
                                }
                            )
                        }
                        "data" -> {
                            var split by remember { mutableStateOf(Prefs.getBool(context, "dat_show_breakdown", true)) }
                            SettingSwitch(
                                label = "WiFi/Mobile Split",
                                checked = split,
                                onCheckedChange = {
                                    split = it
                                    Prefs.setBool(context, "dat_show_breakdown", it)
                                }
                            )
                            var planLimit by remember { mutableStateOf(Prefs.getInt(context, "dat_plan_limit", 0).toFloat()) }
                            SettingSlider(
                                label = "Monthly Plan Limit",
                                value = planLimit,
                                valueRange = 0f..10000f,
                                onValueChange = {
                                    planLimit = it
                                    Prefs.setInt(context, "dat_plan_limit", it.toInt())
                                },
                                formatter = { if (it == 0f) "No Limit" else "${it.toInt()} MB" }
                            )
                            if (planLimit > 0) {
                                var alertPct by remember { mutableStateOf(Prefs.getInt(context, "dat_plan_alert_pct", 90).toFloat()) }
                                SettingSlider(
                                    label = "Plan Alert Percentage",
                                    value = alertPct,
                                    valueRange = 50f..100f,
                                    onValueChange = {
                                        alertPct = it
                                        Prefs.setInt(context, "dat_plan_alert_pct", it.toInt())
                                    },
                                    formatter = { "${it.toInt()}%" }
                                )
                            }
                            var billingDay by remember { mutableStateOf(Prefs.getInt(context, "dat_billing_day", 1).toFloat()) }
                            SettingSlider(
                                label = "Billing Day",
                                value = billingDay,
                                valueRange = 1f..31f,
                                onValueChange = {
                                    billingDay = it
                                    Prefs.setInt(context, "dat_billing_day", it.toInt())
                                },
                                formatter = { "${it.toInt()}" }
                            )
                        }
                        "unlock" -> {
                            var ulLimit by remember { mutableStateOf(Prefs.getInt(context, "ulk_daily_limit", 0).toFloat()) }
                            SettingSlider(
                                label = "Daily Unlock Limit",
                                value = ulLimit,
                                valueRange = 0f..200f,
                                onValueChange = {
                                    ulLimit = it
                                    Prefs.setInt(context, "ulk_daily_limit", it.toInt())
                                },
                                formatter = { if (it == 0f) "No Limit" else "${it.toInt()} times" }
                            )
                            if (ulLimit > 0) {
                                var limitAlert by remember { mutableStateOf(Prefs.getBool(context, "ulk_limit_alert", true)) }
                                SettingSwitch(
                                    label = "Alert on Limit",
                                    checked = limitAlert,
                                    onCheckedChange = {
                                        limitAlert = it
                                        Prefs.setBool(context, "ulk_limit_alert", it)
                                    }
                                )
                            }
                            var debounce by remember { mutableStateOf(Prefs.getInt(context, "ulk_debounce", 5000).toFloat()) }
                            SettingSlider(
                                label = "Debounce",
                                value = debounce,
                                valueRange = 0f..30000f,
                                onValueChange = {
                                    debounce = it
                                    Prefs.setInt(context, "ulk_debounce", it.toInt())
                                },
                                formatter = { "${it.toInt()} ms" }
                            )
                        }
                        "storage" -> {
                            var stoAlert by remember { mutableStateOf(Prefs.getBool(context, "sto_low_alert", true)) }
                            SettingSwitch(
                                label = "Low Storage Alert",
                                checked = stoAlert,
                                onCheckedChange = {
                                    stoAlert = it
                                    Prefs.setBool(context, "sto_low_alert", it)
                                }
                            )
                            if (stoAlert) {
                                var stoThresh by remember { mutableStateOf(Prefs.getInt(context, "sto_low_thresh_mb", 1000).toFloat()) }
                                SettingSlider(
                                    label = "Low Alert Threshold",
                                    value = stoThresh,
                                    valueRange = 100f..5000f,
                                    onValueChange = {
                                        stoThresh = it
                                        Prefs.setInt(context, "sto_low_thresh_mb", it.toInt())
                                    },
                                    formatter = { "${it.toInt()} MB" }
                                )
                            }
                        }
                        "speedtest" -> {
                            var autoTest by remember { mutableStateOf(Prefs.getBool(context, "spd_auto_test", true)) }
                            SettingSwitch(
                                label = "Auto Test",
                                checked = autoTest,
                                onCheckedChange = {
                                    autoTest = it
                                    Prefs.setBool(context, "spd_auto_test", it)
                                }
                            )
                            if (autoTest) {
                                var freq by remember { mutableStateOf(Prefs.getInt(context, "spd_test_freq", 60).toFloat()) }
                                SettingSlider(
                                    label = "Test Frequency",
                                    value = freq,
                                    valueRange = 15f..240f,
                                    steps = 15,
                                    onValueChange = {
                                        freq = it
                                        Prefs.setInt(context, "spd_test_freq", it.toInt())
                                    },
                                    formatter = { "${it.toInt()}m" }
                                )
                            }
                            var dailyLimit by remember { mutableStateOf(Prefs.getInt(context, "spd_daily_limit", 10).toFloat()) }
                            SettingSlider(
                                label = "Daily Test Limit",
                                value = dailyLimit,
                                valueRange = 1f..100f,
                                onValueChange = {
                                    dailyLimit = it
                                    Prefs.setInt(context, "spd_daily_limit", it.toInt())
                                },
                                formatter = { "${it.toInt()} tests" }
                            )
                            var wifiOnly by remember { mutableStateOf(Prefs.getBool(context, "spd_wifi_only", true)) }
                            SettingSwitch(
                                label = "WiFi Only",
                                checked = wifiOnly,
                                onCheckedChange = {
                                    wifiOnly = it
                                    Prefs.setBool(context, "spd_wifi_only", it)
                                }
                            )
                            var showPing by remember { mutableStateOf(Prefs.getBool(context, "spd_show_ping", true)) }
                            SettingSwitch(
                                label = "Show Ping",
                                checked = showPing,
                                onCheckedChange = {
                                    showPing = it
                                    Prefs.setBool(context, "spd_show_ping", it)
                                }
                            )
                        }
                    }

                    module.settingsContent(context, sysAccess)
                    
                    if (moduleKey == "fap") {
                        Button(
                            onClick = {
                                val intent = Intent(context, MonitorService::class.java)
                                    .setAction("com.extensionbox.app.FAP_INCREMENT")
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Log Action")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}
