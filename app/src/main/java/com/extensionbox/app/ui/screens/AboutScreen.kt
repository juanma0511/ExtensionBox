package com.extensionbox.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extensionbox.app.BuildConfig
import com.extensionbox.app.R
import com.extensionbox.app.network.UpdateChecker
import com.extensionbox.app.network.Release
import com.extensionbox.app.ui.components.AppCard
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Refresh
import com.extensionbox.app.SystemAccess
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    
    // System Access State
    var sysAccess by remember { mutableStateOf(SystemAccess(context)) }
    var accessTier by remember { mutableStateOf(sysAccess.tier) }
    var rootProvider by remember { mutableStateOf(sysAccess.rootProvider) }

    var updateStatus by remember { mutableStateOf("Check for Updates") }
    var isChecking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Hero Section ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Extension Box",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        // --- Description Card ---
        AppCard {
            Text(
                text = "A modern, lightweight system monitoring tool with a modular architecture built for Android enthusiasts and power users.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- System Access Status (Interactive) ---
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (sysAccess.isEnhanced()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (sysAccess.isEnhanced()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = accessTier,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (rootProvider != SystemAccess.RootProvider.NONE) {
                        Text(
                            text = "via ${rootProvider.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = {
                    // Re-instantiate to trigger checks again
                    sysAccess = SystemAccess(context)
                    accessTier = sysAccess.tier
                    rootProvider = sysAccess.rootProvider
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Access")
                }
            }
        }

        // --- Update Section ---
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Software Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = updateStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = {
                            isChecking = true
                            updateStatus = "Checking GitHub..."
                            coroutineScope.launch {
                                try {
                                    val service = UpdateChecker.getGitHubService()
                                    val releases = service.getReleases("suvojeet-sengupta", "ExtensionBox")
                                    if (releases.isNotEmpty()) {
                                        val latest = releases[0].tagName
                                        updateStatus = if (latest.contains(BuildConfig.VERSION_NAME)) {
                                            "You're using the latest version ✨"
                                        } else {
                                            "New version available: $latest"
                                        }
                                    }
                                } catch (e: Exception) {
                                    updateStatus = "Update check failed"
                                } finally {
                                    isChecking = false
                                }
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Update, null)
                    }
                }
            }
        }

        // --- Team Section ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Engineering Team",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            AppCard {
                DeveloperItem(
                    name = "Suvojeet Sengupta",
                    role = "Lead Developer • Kotlin & Shizuku",
                    github = "https://github.com/suvojeet-sengupta",
                    onCli = { uriHandler.openUri(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                DeveloperItem(
                    name = "Omer",
                    role = "Contributor",
                    github = "https://github.com/omersusin",
                    onCli = { uriHandler.openUri(it) }


                              HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                )
                DeveloperItem(
                    name = stringResource(id = R.string.pankaj_meharchandani),
                    role = stringResource(id = R.string.developer),
                    github = "https://github.com/Pankaj-Meharchandani",
                    onCli = { uriHandler.openUri(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                DeveloperItem(
                    name = stringResource(id = R.string.Juanma),
                    role = stringResource(id = R.string.developer),
                    github = "https://github.com/juanma0511",
                    onCli = { uriHandler.openUri(it) }
                )
            }
        }
            
    

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun DeveloperItem(name: String, role: String, github: String, onCli: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCli(github) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
