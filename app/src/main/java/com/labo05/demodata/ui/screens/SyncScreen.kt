package com.labo05.demodata.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.labo05.demodata.DemoData
import com.labo05.demodata.data.remote.model.GeoEventResponse
import com.labo05.demodata.ui.viewmodel.SyncViewModel

@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as DemoData

    val vm: SyncViewModel = viewModel(
        factory = SyncViewModel.Factory(
            gps = app.gpsRepository,
            media = app.mediaRepository,
            audio = app.audioRepository,
            sessionManager = app.sessionManager
        )
    )

    val counts by vm.counts.collectAsStateWithLifecycle()
    val isSyncing by vm.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    val syncProgress by vm.syncProgress.collectAsStateWithLifecycle()

    val cloudRecords by vm.cloudRecords.collectAsStateWithLifecycle()
    val isLoadingCloud by vm.isLoadingCloud.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshCloudData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Sync Center", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Inventario de registros locales pendientes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                vm.sync { success ->
                    val message = if (success) {
                        "Sincronización finalizada"
                    } else {
                        "No se pudo sincronizar"
                    }

                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !isSyncing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSyncing) "Sincronizando..." else "Sincronizar ahora")
        }

        if (isSyncing) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { syncProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (syncMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = syncMessage ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = if ((syncMessage ?: "").contains("Error"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Total de registros locales",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Suma de todas las categorías",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    "${counts.total}",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Desglose por tipo", style = MaterialTheme.typography.titleSmall)

        Spacer(modifier = Modifier.height(8.dp))

        CategoryRow(Icons.Default.LocationOn, "GNSS Google FLP", counts.gpsGoogle)
        Spacer(modifier = Modifier.height(8.dp))

        CategoryRow(Icons.Default.Sensors, "GNSS Sensores HW", counts.gpsSensors)
        Spacer(modifier = Modifier.height(8.dp))

        CategoryRow(Icons.Default.PhotoCamera, "Fotos", counts.photos)
        Spacer(modifier = Modifier.height(8.dp))

        CategoryRow(Icons.Default.Videocam, "Videos", counts.videos)
        Spacer(modifier = Modifier.height(8.dp))

        CategoryRow(Icons.Default.AudioFile, "Audios", counts.audios)

        Spacer(modifier = Modifier.height(24.dp))

        CloudSection(
            isLoadingCloud = isLoadingCloud,
            cloudRecords = cloudRecords,
            onRefresh = { vm.refreshCloudData() }
        )
    }
}

@Composable
private fun CloudSection(
    isLoadingCloud: Boolean,
    cloudRecords: List<GeoEventResponse>,
    onRefresh: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                "Datos en la nube",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Últimos registros sincronizados con el servidor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onRefresh) {
            Text("Actualizar")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (isLoadingCloud) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (cloudRecords.isEmpty() && !isLoadingCloud) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "No hay datos GPS en la nube para este usuario.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        cloudRecords.forEach { record ->
            CloudRecordCard(record)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CloudRecordCard(record: GeoEventResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ID ${record.id} • ${record.eventType ?: "GPS"}",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "Lat: ${record.latitude}, Lon: ${record.longitude}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Registrado: ${record.recordedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, label: String, count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Text(
                "$count",
                style = MaterialTheme.typography.titleLarge,
                color = if (count > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
        }
    }
}