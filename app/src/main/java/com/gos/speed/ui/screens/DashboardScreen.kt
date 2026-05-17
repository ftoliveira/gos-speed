package com.gos.speed.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import com.google.accompanist.permissions.*
import com.gos.speed.ui.components.GpsInfoCard
import com.gos.speed.ui.components.SpeedometerGauge
import com.gos.speed.viewmodel.GpsViewModel
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(
    viewModel: GpsViewModel,
    onNavigateToHistory: () -> Unit
) {
    val locationPermission = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val gpsData by viewModel.gpsData.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val routeHistory by viewModel.routeHistory.collectAsStateWithLifecycle()

    LaunchedEffect(locationPermission.allPermissionsGranted) {
        if (locationPermission.allPermissionsGranted) {
            viewModel.startGps()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GPS Speed",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Rounded.History, contentDescription = "Histórico")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (!locationPermission.allPermissionsGranted) {
            PermissionRequest(
                onRequest = { locationPermission.launchMultiplePermissionRequest() },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Speedometer
            SpeedometerGauge(
                speedKmh = gpsData.speedKmh,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Info cards grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.Speed,
                        label = "VELOCIDADE",
                        value = "%.0f km/h".format(gpsData.speedKmh),
                        subtitle = "%.1f mph".format(gpsData.speedMph)
                    )
                }
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.Terrain,
                        label = "ALTITUDE",
                        value = "%.0f m".format(gpsData.altitude),
                        subtitle = "acima do nível do mar"
                    )
                }
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.MyLocation,
                        label = "LATITUDE",
                        value = formatCoord(gpsData.latitude, 'N', 'S'),
                        subtitle = "%.6f°".format(gpsData.latitude)
                    )
                }
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.MyLocation,
                        label = "LONGITUDE",
                        value = formatCoord(gpsData.longitude, 'E', 'W'),
                        subtitle = "%.6f°".format(gpsData.longitude)
                    )
                }
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.GpsFixed,
                        label = "PRECISÃO",
                        value = if (gpsData.accuracy > 0) "±%.0f m".format(gpsData.accuracy) else "—",
                        subtitle = if (gpsData.accuracy <= 5) "excelente" else if (gpsData.accuracy <= 20) "boa" else "baixa"
                    )
                }
                item {
                    GpsInfoCard(
                        icon = Icons.Rounded.Satellite,
                        label = "SATÉLITES",
                        value = "${gpsData.satellites}",
                        subtitle = if (gpsData.satellites >= 4) "sinal OK" else "sinal fraco"
                    )
                }
            }

            // Tracking controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isTracking) viewModel.stopTracking()
                        else viewModel.startTracking()
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (isTracking) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isTracking) "Parar" else "Gravar rota")
                }

                if (routeHistory.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${routeHistory.size} pts")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Permissão de localização necessária",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "O app precisa de acesso ao GPS para mostrar sua velocidade e localização.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Permitir acesso")
        }
    }
}

private fun formatCoord(value: Double, pos: Char, neg: Char): String {
    val hemisphere = if (value >= 0) pos else neg
    val abs = abs(value)
    val degrees = abs.toInt()
    val minutes = ((abs - degrees) * 60).toInt()
    val seconds = ((abs - degrees - minutes / 60.0) * 3600)
    return "%d°%d'%.1f\"%c".format(degrees, minutes, seconds, hemisphere)
}
