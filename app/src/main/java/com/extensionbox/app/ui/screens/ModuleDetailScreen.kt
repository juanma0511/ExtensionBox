package com.extensionbox.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.extensionbox.app.MonitorService
import com.extensionbox.app.ui.ModuleRegistry
import com.extensionbox.app.ui.components.Sparkline
import com.extensionbox.app.ui.components.StatItem
import com.extensionbox.app.ui.components.extractPoints
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
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
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

        if (data.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Real-time Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
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
                }
            }
        }

        if (module != null && sysAccess != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    module.composableContent(context, sysAccess)
                    
                    if (moduleKey == "fap") {
                        Spacer(Modifier.height(16.dp))
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