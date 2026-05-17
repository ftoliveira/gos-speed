package com.gos.speed.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.gos.speed.data.*
import com.gos.speed.ui.components.GpsInfoCard
import com.gos.speed.ui.components.RadarView
import com.gos.speed.viewmodel.UwbViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun UwbScreen(
    onNavigateBack: () -> Unit,
    uwbViewModel: UwbViewModel = viewModel()
) {
    val state by uwbViewModel.state.collectAsStateWithLifecycle()
    val foundDevices by uwbViewModel.foundDevices.collectAsStateWithLifecycle()

    // Request Bluetooth permissions
    val btPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        rememberMultiplePermissionsState(listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ))
    } else {
        rememberMultiplePermissionsState(listOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Localização UWB", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { uwbViewModel.stop(); onNavigateBack() }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (state.connectionStatus != UwbConnectionStatus.IDLE) {
                        TextButton(onClick = { uwbViewModel.stop() }) {
                            Text("Parar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Support status banner
            item {
                UwbSupportBanner(state.supportStatus)
            }

            // Bluetooth permission required
            if (!btPermissions.allPermissionsGranted) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Permissão Bluetooth necessária", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Para descobrir outros dispositivos via Bluetooth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                            Button(onClick = { btPermissions.launchMultiplePermissionRequest() }) {
                                Text("Permitir Bluetooth")
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            if (state.supportStatus == UwbSupportStatus.NOT_SUPPORTED) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.PortableWifiOff, contentDescription = null, modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text("Dispositivo sem hardware UWB", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text("UWB está disponível apenas em dispositivos com chip UWB dedicado (ex: Pixel 6 Pro+, Samsung S21 Ultra+).",
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
                return@LazyColumn
            }

            // Error
            if (state.error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(state.error!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // IDLE: role picker
            if (state.connectionStatus == UwbConnectionStatus.IDLE || state.connectionStatus == UwbConnectionStatus.ERROR) {
                item {
                    Text("Escolha o papel deste dispositivo", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoleCard(
                            icon = Icons.Rounded.Router,
                            title = "Host",
                            description = "Anuncia presença e aguarda conexão de outro dispositivo",
                            modifier = Modifier.weight(1f),
                            onClick = { if (btPermissions.allPermissionsGranted) uwbViewModel.startHost() else btPermissions.launchMultiplePermissionRequest() }
                        )
                        RoleCard(
                            icon = Icons.Rounded.PhoneAndroid,
                            title = "Convidado",
                            description = "Busca dispositivos Host por perto para se conectar",
                            modifier = Modifier.weight(1f),
                            onClick = { if (btPermissions.allPermissionsGranted) uwbViewModel.startGuest() else btPermissions.launchMultiplePermissionRequest() }
                        )
                    }
                }
            }

            // HOST advertising
            if (state.connectionStatus == UwbConnectionStatus.ADVERTISING) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Aguardando conexão...", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Abra o app no outro dispositivo e selecione \"Convidado\"",
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // GUEST scanning — device list
            if (state.connectionStatus == UwbConnectionStatus.SCANNING) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Buscando dispositivos Host...", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                if (foundDevices.isEmpty()) {
                    item {
                        Text("Nenhum dispositivo encontrado ainda. Certifique-se que o outro celular está com o app aberto como Host.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                items(foundDevices) { peer ->
                    Card(
                        onClick = { uwbViewModel.connectToDevice(peer.address) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(peer.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(peer.address, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            // CONNECTING
            if (state.connectionStatus == UwbConnectionStatus.CONNECTING) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Conectando e trocando parâmetros UWB...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // RANGING — radar + data cards
            if (state.connectionStatus == UwbConnectionStatus.RANGING) {
                item {
                    RadarView(
                        distanceMeters = state.peer?.distanceMeters,
                        azimuthDegrees = state.peer?.azimuthDegrees,
                        maxRangeMeters = 15f,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GpsInfoCard(
                            icon = Icons.Rounded.Straighten,
                            label = "DISTÂNCIA",
                            value = state.peer?.distanceMeters?.let { "%.1f m".format(it) } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                        GpsInfoCard(
                            icon = Icons.Rounded.Explore,
                            label = "AZIMUTE",
                            value = state.peer?.azimuthDegrees?.let { "%.0f°".format(it) } ?: "N/D",
                            subtitle = if (!state.supportsAzimuth) "hw não suporta" else null,
                            modifier = Modifier.weight(1f)
                        )
                        GpsInfoCard(
                            icon = Icons.Rounded.Height,
                            label = "ELEVAÇÃO",
                            value = state.peer?.elevationDegrees?.let { "%.0f°".format(it) } ?: "N/D",
                            subtitle = if (!state.supportsAzimuth) "hw não suporta" else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // DISCONNECTED
            if (state.connectionStatus == UwbConnectionStatus.DISCONNECTED) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(40.dp))
                            Text("Dispositivo desconectado", fontWeight = FontWeight.SemiBold)
                            Button(onClick = { uwbViewModel.stop() }) { Text("Voltar ao início") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UwbSupportBanner(status: UwbSupportStatus) {
    val (color, icon, text) = when (status) {
        UwbSupportStatus.CHECKING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Rounded.Radar,
            "Verificando suporte a UWB..."
        )
        UwbSupportStatus.SUPPORTED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Rounded.Radar,
            "UWB suportado neste dispositivo"
        )
        UwbSupportStatus.NOT_SUPPORTED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Rounded.PortableWifiOff,
            "UWB não suportado neste dispositivo"
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (status == UwbSupportStatus.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
