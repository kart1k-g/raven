package com.kart1kg.raven.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kart1kg.raven.engine.ConnectionInfo
import com.kart1kg.raven.engine.ConnectionStatus
import com.kart1kg.raven.ui.theme.RavenCard
import com.kart1kg.raven.ui.theme.RavenError
import com.kart1kg.raven.ui.theme.RavenOnSurfaceDim
import com.kart1kg.raven.ui.theme.RavenOutline
import com.kart1kg.raven.ui.theme.RavenPrimary
import com.kart1kg.raven.ui.theme.RavenSecondary
import com.kart1kg.raven.ui.theme.RavenSuccess
import com.kart1kg.raven.ui.theme.RavenWarning

@Composable
fun ProxyScreen(viewModel: ProxyViewModel, modifier: Modifier = Modifier) {
    val serverState by viewModel.serverState.collectAsState()
    val portText by viewModel.portText.collectAsState()
    val hotspotIp by viewModel.hotspotIp.collectAsState()

    LaunchedEffect(serverState.isRunning) {
        viewModel.refreshHotspotIp()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Header
        item { HeaderSection() }

        // Power Button & Status
        item {
            PowerSection(
                isRunning = serverState.isRunning,
                activeConnections = serverState.activeConnections,
                onToggle = { viewModel.toggleProxy() }
            )
        }

        // Port configuration
        item {
            PortConfigCard(
                portText = portText,
                onPortChange = { viewModel.updatePort(it) },
                isRunning = serverState.isRunning
            )
        }

        // Connection info / instructions
        item {
            ConnectionInfoCard(
                isRunning = serverState.isRunning,
                hotspotIp = hotspotIp,
                port = serverState.port
            )
        }

        // Stats
        if (serverState.isRunning || serverState.totalConnections > 0) {
            item {
                StatsCard(
                    activeConnections = serverState.activeConnections,
                    totalConnections = serverState.totalConnections
                )
            }
        }

        // Recent connections
        if (serverState.recentConnections.isNotEmpty()) {
            item {
                Text(
                    "Recent Connections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = serverState.recentConnections,
                key = { it.id }
            ) { conn ->
                ConnectionItem(conn)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Security,
            contentDescription = null,
            tint = RavenPrimary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                "RAVEN",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                "SOCKS5 Proxy Server",
                style = MaterialTheme.typography.bodySmall,
                color = RavenOnSurfaceDim,
                letterSpacing = 2.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Power Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PowerSection(
    isRunning: Boolean,
    activeConnections: Int,
    onToggle: () -> Unit
) {
    val glowColor by animateColorAsState(
        targetValue = if (isRunning) RavenPrimary.copy(alpha = 0.3f)
        else Color.Transparent,
        animationSpec = tween(600),
        label = "glow"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Power button with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .then(
                    if (isRunning) Modifier.drawBehind {
                        drawCircle(
                            color = glowColor,
                            radius = size.minDimension / 1.5f,
                            alpha = pulseAlpha * 0.5f
                        )
                    } else Modifier
                )
        ) {
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) RavenPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isRunning) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (isRunning) "Stop proxy" else "Start proxy",
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) RavenSuccess.copy(alpha = pulseAlpha)
                        else RavenOnSurfaceDim
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "ACTIVE" else "INACTIVE",
                style = MaterialTheme.typography.labelLarge,
                color = if (isRunning) RavenSuccess else RavenOnSurfaceDim,
                letterSpacing = 3.sp
            )
        }

        AnimatedVisibility(
            visible = isRunning && activeConnections > 0,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Text(
                text = "$activeConnections active connection(s)",
                style = MaterialTheme.typography.bodySmall,
                color = RavenPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Port Config
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PortConfigCard(
    portText: String,
    onPortChange: (String) -> Unit,
    isRunning: Boolean
) {
    GlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = RavenPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Listening Port",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "SOCKS5 server port",
                    style = MaterialTheme.typography.bodySmall,
                    color = RavenOnSurfaceDim
                )
            }
            OutlinedTextField(
                value = portText,
                onValueChange = onPortChange,
                enabled = !isRunning,
                modifier = Modifier.width(100.dp),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RavenPrimary,
                    unfocusedBorderColor = RavenOutline,
                    disabledBorderColor = RavenOutline.copy(alpha = 0.5f),
                    disabledTextColor = RavenOnSurfaceDim
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connection Info / Instructions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionInfoCard(
    isRunning: Boolean,
    hotspotIp: String?,
    port: Int
) {
    val clipboardManager = LocalClipboardManager.current
    val proxyAddress = "${hotspotIp ?: "<hotspot IP>"}:$port"

    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = if (isRunning) RavenPrimary else RavenOnSurfaceDim,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Proxy Address",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address display with copy button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, RavenOutline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = proxyAddress,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (isRunning) RavenPrimary else RavenOnSurfaceDim,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(proxyAddress))
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = RavenPrimary.copy(alpha = 0.15f),
                        contentColor = RavenPrimary
                    )
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Copy address",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Setup instructions
            Text(
                "Setup Instructions",
                style = MaterialTheme.typography.labelMedium,
                color = RavenOnSurfaceDim,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            InstructionStep("1", "Enable hotspot on this phone")
            InstructionStep("2", "Connect your laptop to the hotspot")
            InstructionStep("3", "Set SOCKS5 proxy on laptop to the address above")
            InstructionStep("4", "Tap the power button to start the proxy")
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(RavenPrimary.copy(alpha = 0.15f))
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelSmall,
                color = RavenPrimary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(activeConnections: Int, totalConnections: Long) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Active", activeConnections.toString(), RavenPrimary)
            StatItem("Total", totalConnections.toString(), RavenSecondary)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = RavenOnSurfaceDim,
            letterSpacing = 2.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connection Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectionItem(conn: ConnectionInfo) {
    val statusColor = when (conn.status) {
        ConnectionStatus.CONNECTING -> RavenWarning
        ConnectionStatus.RELAYING -> RavenSuccess
        ConnectionStatus.CLOSED -> RavenOnSurfaceDim
        ConnectionStatus.ERROR -> RavenError
    }

    GlassCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (conn.destinationHost.isNotEmpty())
                        "${conn.destinationHost}:${conn.destinationPort}"
                    else conn.clientAddress,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = conn.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            if (conn.bytesUploaded > 0 || conn.bytesDownloaded > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = RavenOutline, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = null,
                        tint = RavenOnSurfaceDim,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "↑ ${formatBytes(conn.bytesUploaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RavenOnSurfaceDim
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "↓ ${formatBytes(conn.bytesDownloaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RavenOnSurfaceDim
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RavenCard),
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}
