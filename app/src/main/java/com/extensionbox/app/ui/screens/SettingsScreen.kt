package com.extensionbox.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ThemeHelper
import com.extensionbox.app.ui.components.AppCard
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.lifecycle.viewmodel.compose.viewModel
import com.extensionbox.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeIndex by viewModel.themeIndex.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val isShizukuRunning by viewModel.isShizukuRunning.collectAsState()
    val shizukuPermissionGranted by viewModel.shizukuPermissionGranted.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuState()
                
                if (isShizukuRunning && !shizukuPermissionGranted) {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (_: Exception) {}
                }
            }
        }
        
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            viewModel.setShizukuPermission(grantResult == PackageManager.PERMISSION_GRANTED)
        }
        
        val binderListener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                viewModel.refreshShizukuState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        }
    }

    // Reactive System Access
    var sysAccess by remember { mutableStateOf<SystemAccess?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        val newAccess = withContext(Dispatchers.IO) {
            SystemAccess(context)
        }
        sysAccess = newAccess
    }

    // Refresh function
    fun refreshAccess() {
        sysAccess = null // Show loading
        scope.launch {
            val newAccess = withContext(Dispatchers.IO) {
                SystemAccess(context)
            }
            sysAccess = newAccess
            viewModel.refreshShizukuState()
        }
    }

    // Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val jsonObject = JSONObject()
                Prefs.getAll(context).forEach { (k, v) -> jsonObject.put(k, v) }
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(jsonObject.toString(4).toByteArray())
                }
                Toast.makeText(context, "Settings exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { `is` ->
                    val json = BufferedReader(InputStreamReader(`is`)).readText()
                    Prefs.importJson(context, json)
                    Toast.makeText(context, "Settings imported", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Permissions Section ---
        SettingsGroup(title = "Permissions", icon = Icons.Default.Security) {
            AppCard {
                val currentAccess = sysAccess
                
                if (currentAccess == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking system access...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    SettingsItem(
                        title = "System Access",
                        summary = "Status: ${currentAccess.tier}\nTap to refresh",
                        icon = if (currentAccess.isEnhanced()) Icons.Default.Verified else Icons.Default.AdminPanelSettings,
                        color = if (currentAccess.isEnhanced()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        trailing = {
                            IconButton(onClick = { refreshAccess() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        },
                        onClick = { refreshAccess() }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = "Shizuku Service",
                    summary = if (isShizukuRunning) "Service is active" else "Service not found",
                    icon = Icons.Default.Terminal,
                    onClick = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) context.startActivity(intent)
                            else Toast.makeText(context, "Shizuku not found", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { /* ignore */ }
                    }
                )

                if (isShizukuRunning && !shizukuPermissionGranted) {
                    SettingsItem(
                        title = "Grant Permission",
                        summary = "Allow system file access via Shizuku",
                        icon = Icons.Default.VpnKey,
                        onClick = { Shizuku.requestPermission(1001) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = "Battery Optimization",
                    summary = "Allow background monitoring",
                    icon = Icons.Default.BatterySaver,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                )
            }
        }

        // --- Notification Section ---
        SettingsGroup(title = "Notification", icon = Icons.Default.Notifications) {
            AppCard {
                var refreshRate by remember { mutableStateOf(Prefs.getLong(context, "notif_refresh_ms", 10000L).toFloat()) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Refresh Rate", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${refreshRate.toInt() / 1000}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = refreshRate,
                        onValueChange = { 
                            refreshRate = it
                            Prefs.setLong(context, "notif_refresh_ms", it.toLong())
                        },
                        valueRange = 1000f..60000f,
                        steps = 58
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var dismissible by remember { mutableStateOf(Prefs.getBool(context, "notif_dismissible", false)) }
                SettingsToggle(
                    title = "Dismissible",
                    summary = "Allow swiping away to stop service",
                    icon = Icons.Default.Swipe,
                    checked = dismissible,
                    onCheckedChange = {
                        dismissible = it
                        Prefs.setBool(context, "notif_dismissible", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var layoutExpanded by remember { mutableStateOf(false) }
                var layoutStyle by remember { mutableStateOf(Prefs.getString(context, "notif_layout_style", "LIST") ?: "LIST") }
                
                SettingsItem(
                    title = "Layout Style",
                    summary = layoutStyle,
                    icon = Icons.Default.Dashboard,
                    onClick = { layoutExpanded = true }
                )
                
                DropdownMenu(expanded = layoutExpanded, onDismissRequest = { layoutExpanded = false }) {
                    listOf("LIST", "GRID", "COMPACT").forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style) },
                            onClick = {
                                layoutStyle = style
                                Prefs.setString(context, "notif_layout_style", style)
                                layoutExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // --- Appearance Section ---
        SettingsGroup(title = "Appearance", icon = Icons.Default.Palette) {
            AppCard {
                SettingsItem(
                    title = "App Theme",
                    summary = ThemeHelper.NAMES[themeIndex.coerceIn(0, ThemeHelper.NAMES.size - 1)],
                    icon = Icons.Default.ColorLens,
                    onClick = { expanded = true }
                )
                
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ThemeHelper.NAMES.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.updateTheme(index)
                                expanded = false
                            }
                        )
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    (themeIndex == ThemeHelper.MONET || themeIndex == ThemeHelper.AMOLED)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var dynamicColor by remember { mutableStateOf(Prefs.getBool(context, "dynamic_color", true)) }
                    SettingsToggle(
                        title = "Dynamic Color",
                        summary = "Use system wallpaper colors",
                        icon = Icons.Default.InvertColors,
                        checked = dynamicColor,
                        onCheckedChange = {
                            dynamicColor = it
                            Prefs.setBool(context, "dynamic_color", it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var expandCards by remember { mutableStateOf(Prefs.getBool(context, "dash_expand_cards", true)) }
                SettingsToggle(
                    title = "Expandable Cards",
                    summary = "Show expansion toggle on dashboard",
                    icon = Icons.Default.ViewStream,
                    checked = expandCards,
                    onCheckedChange = {
                        expandCards = it
                        Prefs.setBool(context, "dash_expand_cards", it)
                    }
                )
            }
        }

        // --- Monitoring Section ---
        SettingsGroup(title = "Monitoring", icon = Icons.Default.Analytics) {
            AppCard {
                var resetFull by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_full", true)) }
                SettingsToggle(
                    title = "Reset on Full Charge",
                    summary = "Clear stats when battery reaches 100%",
                    icon = Icons.Default.BatteryChargingFull,
                    checked = resetFull,
                    onCheckedChange = {
                        resetFull = it
                        Prefs.setBool(context, "scr_reset_full", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var resetPlugged by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_plugged", false)) }
                SettingsToggle(
                    title = "Reset on Plugged",
                    summary = "Clear stats when device is plugged in",
                    icon = Icons.Default.Power,
                    checked = resetPlugged,
                    onCheckedChange = {
                        resetPlugged = it
                        Prefs.setBool(context, "scr_reset_plugged", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var resetBoot by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_boot", true)) }
                SettingsToggle(
                    title = "Reset on Reboot",
                    summary = "Clear stats after system restart",
                    icon = Icons.Default.RestartAlt,
                    checked = resetBoot,
                    onCheckedChange = {
                        resetBoot = it
                        Prefs.setBool(context, "scr_reset_boot", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var contextAware by remember { mutableStateOf(Prefs.getBool(context, "notif_context_aware", true)) }
                SettingsToggle(
                    title = "Context Aware",
                    summary = "Dynamic titles based on battery",
                    icon = Icons.Default.NotificationAdd,
                    checked = contextAware,
                    onCheckedChange = {
                        contextAware = it
                        Prefs.setBool(context, "notif_context_aware", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var notifCompact by remember { mutableStateOf(Prefs.getBool(context, "notif_compact_style", true)) }
                SettingsToggle(
                    title = "Compact Style",
                    summary = "Simplified list in notification",
                    icon = Icons.Default.Compress,
                    checked = notifCompact,
                    onCheckedChange = {
                        notifCompact = it
                        Prefs.setBool(context, "notif_compact_style", it)
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var compactItems by remember { mutableStateOf(Prefs.getInt(context, "notif_compact_items", 4).toFloat()) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Notification Items: ${compactItems.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = compactItems,
                        onValueChange = { 
                            compactItems = it
                            Prefs.setInt(context, "notif_compact_items", it.toInt())
                        },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }
            }
        }

        // --- Data Section ---
        SettingsGroup(title = "Data & Backup", icon = Icons.Default.Storage) {
            AppCard {
                var retentionDays by remember { mutableStateOf(Prefs.getDataRetentionDays(context).toFloat()) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Data Retention", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${retentionDays.toInt()} Days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = retentionDays,
                        onValueChange = { 
                            retentionDays = it
                            Prefs.setDataRetentionDays(context, it.toInt())
                        },
                        valueRange = 1f..30f,
                        steps = 29
                    )
                    Text(
                        text = "History older than this will be deleted daily.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = "Export Settings",
                    summary = "Save config to JSON",
                    icon = Icons.Default.UploadFile,
                    onClick = { exportLauncher.launch("extensionbox_config.json") }
                )
                SettingsItem(
                    title = "Import Settings",
                    summary = "Restore from JSON",
                    icon = Icons.Default.FileDownload,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = "Reset All Data",
                    summary = "Clear all stats and preferences",
                    icon = Icons.Default.DeleteSweep,
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        Prefs.clearAll(context)
                        Toast.makeText(context, "Data cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        title = title,
        summary = summary,
        icon = icon,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
