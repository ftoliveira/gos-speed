package com.gos.speed.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.gos.speed.data.RoutePoint
import com.gos.speed.viewmodel.GpsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: GpsViewModel,
    onNavigateBack: () -> Unit
) {
    val history by viewModel.routeHistory.collectAsStateWithLifecycle()
    val maxSpeed by viewModel.maxSpeed.collectAsStateWithLifecycle()
    val totalDistance by viewModel.totalDistance.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de rota", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Limpar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            EmptyHistory(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatCard(
                        icon = Icons.Rounded.Speed,
                        label = "Vel. máxima",
                        value = "%.0f km/h".format(maxSpeed),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Rounded.Route,
                        label = "Distância",
                        value = if (totalDistance < 1.0) "%.0f m".format(totalDistance * 1000)
                                else "%.2f km".format(totalDistance),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Rounded.LocationOn,
                        label = "Pontos",
                        value = "${history.size}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Text(
                    text = "Pontos registrados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            itemsIndexed(history.reversed()) { index, point ->
                RoutePointItem(
                    point = point,
                    index = history.size - index
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RoutePointItem(point: RoutePoint, index: Int) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%.6f, %.6f".format(point.latitude, point.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Alt: %.0fm  •  %s".format(point.altitude, fmt.format(Date(point.timestamp))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "%.0f\nkm/h".format(point.speedKmh),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Route,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Nenhuma rota gravada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Toque em \"Gravar rota\" na tela principal para começar a registrar seu percurso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
