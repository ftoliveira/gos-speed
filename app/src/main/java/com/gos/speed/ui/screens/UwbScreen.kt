package com.gos.speed.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.gos.speed.data.*
import com.gos.speed.session.SavedSession
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
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var firebaseGuestCode by remember { mutableStateOf("") }
    var showFirebaseGuestForm by remember { mutableStateOf(false) }

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

    val isIdle = state.connectionStatus == UwbConnectionStatus.IDLE ||
                 state.connectionStatus == UwbConnectionStatus.ERROR

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
                    if (!isIdle) {
                        TextButton(onClick = {
                            uwbViewModel.stop()
                            showFirebaseGuestForm = false
                            firebaseGuestCode = ""
                        }) { Text("Parar") }
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

            // UWB support banner
            item { UwbSupportBanner(state.supportStatus) }

            if (state.supportStatus == UwbSupportStatus.NOT_SUPPORTED) {
                item { UwbNotSupportedCard() }
                return@LazyColumn
            }

            // Error
            if (state.error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Text(state.error!!, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // IDLE: method selector + role cards
            if (isIdle) {

                // Resume card — shown when a previous session was saved
                if (state.savedSession != null) {
                    item {
                        ResumeSessionCard(
                            saved = state.savedSession,
                            onResume = { uwbViewModel.resumeSession() },
                            onEnd = { uwbViewModel.endSession() }
                        )
                    }
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.connectionMethod == ConnectionMethod.FIREBASE,
                            onClick = {
                                uwbViewModel.setConnectionMethod(ConnectionMethod.FIREBASE)
                                showFirebaseGuestForm = false
                            },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) {
                            Icon(Icons.Rounded.Cloud, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Internet")
                        }
                        SegmentedButton(
                            selected = state.connectionMethod == ConnectionMethod.BLE,
                            onClick = {
                                uwbViewModel.setConnectionMethod(ConnectionMethod.BLE)
                                showFirebaseGuestForm = false
                            },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) {
                            Icon(Icons.Rounded.Bluetooth, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bluetooth")
                        }
                    }
                }

                item {
                    Text(
                        text = if (state.connectionMethod == ConnectionMethod.FIREBASE)
                            "Os aparelhos trocam um código via internet. Funcionam em redes diferentes, mas ambos precisam estar a até ~50m um do outro para o UWB funcionar."
                        else
                            "Os aparelhos se descobrem via Bluetooth (< 30m). Não precisa de internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (state.connectionMethod == ConnectionMethod.BLE && !btPermissions.allPermissionsGranted) {
                    item { BluetoothPermissionCard { btPermissions.launchMultiplePermissionRequest() } }
                }

                if (state.connectionMethod == ConnectionMethod.FIREBASE && showFirebaseGuestForm) {
                    item {
                        FirebaseGuestForm(
                            code = firebaseGuestCode,
                            onCodeChange = { firebaseGuestCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                            onConnect = { focusManager.clearFocus(); uwbViewModel.joinSessionFirebase(firebaseGuestCode) },
                            onCancel = { showFirebaseGuestForm = false; firebaseGuestCode = "" }
                        )
                    }
                } else {
                    item {
                        Text("Escolha o papel deste dispositivo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoleCard(
                                icon = Icons.Rounded.Router,
                                title = "Host",
                                description = if (state.connectionMethod == ConnectionMethod.FIREBASE)
                                    "Gera um código de 6 dígitos para compartilhar"
                                else "Anuncia presença via Bluetooth",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (state.connectionMethod == ConnectionMethod.FIREBASE) uwbViewModel.startHostFirebase()
                                    else if (btPermissions.allPermissionsGranted) uwbViewModel.startHostBle()
                                    else btPermissions.launchMultiplePermissionRequest()
                                }
                            )
                            RoleCard(
                                icon = Icons.Rounded.PhoneAndroid,
                                title = "Convidado",
                                description = if (state.connectionMethod == ConnectionMethod.FIREBASE)
                                    "Digita o código gerado pelo Host"
                                else "Busca dispositivos Host por perto",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (state.connectionMethod == ConnectionMethod.FIREBASE) showFirebaseGuestForm = true
                                    else if (btPermissions.allPermissionsGranted) uwbViewModel.startGuestBle()
                                    else btPermissions.launchMultiplePermissionRequest()
                                }
                            )
                        }
                    }
                }
            }

            // ADVERTISING
            if (state.connectionStatus == UwbConnectionStatus.ADVERTISING) {
                item {
                    if (state.connectionMethod == ConnectionMethod.FIREBASE && state.sessionCode.isNotEmpty())
                        FirebaseHostCard(code = state.sessionCode, context = context)
                    else
                        BleAdvertisingCard()
                }
            }

            // SCANNING (BLE)
            if (state.connectionStatus == UwbConnectionStatus.SCANNING) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Buscando dispositivos Host...",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (foundDevices.isEmpty()) {
                    item {
                        Text("Nenhum dispositivo encontrado. Certifique-se que o outro celular está como Host.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                items(foundDevices) { peer ->
                    Card(onClick = { uwbViewModel.connectToBlePeer(peer.address) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(peer.name, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(peer.address, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Icon(Icons.Rounded.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            // CONNECTING
            if (state.connectionStatus == UwbConnectionStatus.CONNECTING) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Column {
                                Text("Conectando...", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (state.connectionMethod == ConnectionMethod.FIREBASE)
                                        "Buscando código \"${state.sessionCode}\" no Firebase..."
                                    else "Trocando parâmetros UWB via Bluetooth...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // RANGING
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
                        GpsInfoCard(icon = Icons.Rounded.Straighten, label = "DISTÂNCIA",
                            value = state.peer?.distanceMeters?.let { "%.1f m".format(it) } ?: "—",
                            modifier = Modifier.weight(1f))
                        GpsInfoCard(icon = Icons.Rounded.Explore, label = "AZIMUTE",
                            value = state.peer?.azimuthDegrees?.let { "%.0f°".format(it) } ?: "N/D",
                            subtitle = if (!state.supportsAzimuth) "hw não suporta" else null,
                            modifier = Modifier.weight(1f))
                        GpsInfoCard(icon = Icons.Rounded.Height, label = "ELEVAÇÃO",
                            value = state.peer?.elevationDegrees?.let { "%.0f°".format(it) } ?: "N/D",
                            subtitle = if (!state.supportsAzimuth) "hw não suporta" else null,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            // DISCONNECTED
            if (state.connectionStatus == UwbConnectionStatus.DISCONNECTED) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.LinkOff, null, modifier = Modifier.size(40.dp))
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
private fun ResumeSessionCard(
    saved: SavedSession,
    onResume: () -> Unit,
    onEnd: () -> Unit
) {
    val roleLabel = if (saved.role == UwbRole.CONTROLLER) "Host" else "Convidado"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.History, null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp))
                Text("Sessão anterior", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Code tiles
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    saved.code.forEach { char ->
                        Surface(shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(char.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                ) {
                    Text(roleLabel, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text(
                "Os endereços UWB serão atualizados automaticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f)) {
                    Text("Encerrar")
                }
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retomar")
                }
            }
        }
    }
}

@Composable
private fun UwbSupportBanner(status: UwbSupportStatus) {
    val containerColor = when (status) {
        UwbSupportStatus.CHECKING -> MaterialTheme.colorScheme.surfaceVariant
        UwbSupportStatus.SUPPORTED -> MaterialTheme.colorScheme.primaryContainer
        UwbSupportStatus.NOT_SUPPORTED -> MaterialTheme.colorScheme.errorContainer
    }
    val icon = if (status == UwbSupportStatus.NOT_SUPPORTED) Icons.Rounded.PortableWifiOff else Icons.Rounded.Radar
    val text = when (status) {
        UwbSupportStatus.CHECKING -> "Verificando suporte a UWB..."
        UwbSupportStatus.SUPPORTED -> "UWB suportado neste dispositivo"
        UwbSupportStatus.NOT_SUPPORTED -> "UWB não suportado neste dispositivo"
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (status == UwbSupportStatus.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun UwbNotSupportedCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.PortableWifiOff, null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text("Dispositivo sem hardware UWB", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("UWB está disponível apenas em dispositivos com chip dedicado (ex: Pixel 6 Pro+, Samsung S21 Ultra+).",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun FirebaseHostCard(code: String, context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Compartilhe este código", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                code.forEach { char ->
                    Surface(shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(char.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Código UWB", code))
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copiar código")
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Text("Aguardando convidado...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun BleAdvertisingCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("Aguardando conexão via Bluetooth...", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center)
            Text("Abra o app no outro dispositivo, selecione Bluetooth → Convidado.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FirebaseGuestForm(
    code: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    onCancel: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Entrar como Convidado", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Digite o código de 6 dígitos gerado no dispositivo Host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Código") },
                placeholder = { Text("Ex: A3F7K2") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onConnect() })
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                Button(onClick = onConnect, enabled = code.length == 6,
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Login, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Conectar")
                }
            }
        }
    }
}

@Composable
private fun BluetoothPermissionCard(onRequest: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissão Bluetooth necessária", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRequest) { Text("Permitir Bluetooth") }
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
