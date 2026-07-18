package com.labo05.demodata.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.labo05.demodata.data.remote.NetworkConstants
import com.labo05.demodata.data.remote.RetrofitClient
import com.labo05.demodata.data.remote.model.GeoEventRequest
import com.labo05.demodata.data.remote.model.GeoEventResponse
import com.labo05.demodata.data.repository.AudioRepository
import com.labo05.demodata.data.repository.GpsRepository
import com.labo05.demodata.data.repository.MediaRepository
import com.labo05.demodata.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import java.util.TimeZone

data class SyncCounts(
    val gpsGoogle: Int = 0,
    val gpsSensors: Int = 0,
    val photos: Int = 0,
    val videos: Int = 0,
    val audios: Int = 0
) {
    val total: Int get() = gpsGoogle + gpsSensors + photos + videos + audios
}

private fun timestampToIsoUtc(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}
class SyncViewModel(
    private val gpsRepository: GpsRepository,
    private val mediaRepository: MediaRepository,
    private val audioRepository: AudioRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val counts = combine(
        gpsRepository.googleCount,
        gpsRepository.sensorsCount,
        mediaRepository.photoCount,
        mediaRepository.videoCount,
        audioRepository.count
    ) { g, s, p, v, a ->
        SyncCounts(g, s, p, v, a)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SyncCounts()
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress = _syncProgress.asStateFlow()

    private val _cloudRecords = MutableStateFlow<List<GeoEventResponse>>(emptyList())
    val cloudRecords = _cloudRecords.asStateFlow()

    private val _isLoadingCloud = MutableStateFlow(false)
    val isLoadingCloud = _isLoadingCloud.asStateFlow()

    fun refreshCloudData() {
        viewModelScope.launch {
            _isLoadingCloud.value = true

            try {
                val userId = sessionManager.userId.first()
                val token = sessionManager.accessToken.first()
                val authHeader = if (token != null) "Bearer $token" else null

                val response = RetrofitClient.apiService.listGeoEventsORM(
                    projectSlug = NetworkConstants.PROJECT_SLUG,
                    token = authHeader,
                    userId = userId,
                    limit = 10,
                    offset = 0
                )

                if (response.isSuccessful) {
                    _cloudRecords.value = response.body() ?: emptyList()
                }

            } catch (e: Exception) {
                _cloudRecords.value = emptyList()
            } finally {
                _isLoadingCloud.value = false
            }
        }
    }

    fun sync(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0f
            _syncMessage.value = "Iniciando sincronización..."

            try {
                val googlePoints = gpsRepository.googlePoints.first()
                val sensorsPoints = gpsRepository.sensorsPoints.first()

                val deviceId = sessionManager.getDeviceId()
                val userId = sessionManager.userId.first()
                val token = sessionManager.accessToken.first()
                val authHeader = if (token != null) "Bearer $token" else null

                if (userId == null) {
                    _syncMessage.value = "Error: No se encontró el ID de usuario. Cierra sesión e inicia sesión de nuevo."
                    _isSyncing.value = false
                    onResult(false)
                    return@launch
                }

                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

                var successCount = 0
                val totalToSync = googlePoints.size + sensorsPoints.size

                if (totalToSync == 0) {
                    _syncMessage.value = "No hay datos GPS para sincronizar"
                    _syncProgress.value = 1f
                    _isSyncing.value = false
                    refreshCloudData()
                    onResult(true)
                    return@launch
                }

                var currentItem = 0

                googlePoints.forEach { point ->
                    if (point.latitude != null && point.longitude != null) {
                        val request = GeoEventRequest(
                            userId = userId,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            accuracy = point.accuracy?.toDouble(),
                            speed = null,
                            heading = null,
                            eventType = "gps_google",
                            deviceId = deviceId,
                            appVersion = "1.0.0",
                            deviceModel = deviceModel,
                            recordedAt = timestampToIsoUtc(point.timestamp)
                        )

                        val response = RetrofitClient.apiService.createGeoEventORM(
                            projectSlug = NetworkConstants.PROJECT_SLUG,
                            token = authHeader,
                            request = request
                        )

                        if (response.isSuccessful) {
                            successCount++
                        }
                    }

                    currentItem++
                    _syncProgress.value = currentItem.toFloat() / totalToSync
                }

                sensorsPoints.forEach { point ->
                    if (point.latitude != null && point.longitude != null) {
                        val request = GeoEventRequest(
                            userId = userId,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            altitude = point.altitude,
                            eventType = "gps_sensors",
                            deviceId = deviceId,
                            appVersion = "1.0.0",
                            deviceModel = deviceModel,
                            recordedAt = timestampToIsoUtc(point.timestamp)

                        )

                        val response = RetrofitClient.apiService.createGeoEventORM(
                            projectSlug = NetworkConstants.PROJECT_SLUG,
                            token = authHeader,
                            request = request
                        )

                        if (response.isSuccessful) {
                            successCount++
                        }
                    }

                    currentItem++
                    _syncProgress.value = currentItem.toFloat() / totalToSync
                }

                if (successCount > 0) {
                    gpsRepository.clearAll()
                    _syncMessage.value = "Sincronizados $successCount registros con éxito"

                    // Parte 3: después de subir, refresca lo que está en la nube
                    refreshCloudData()
                } else {
                    _syncMessage.value = "Error al sincronizar con el servidor"
                }

                onResult(successCount > 0)

            } catch (e: Exception) {
                _syncMessage.value = "Error: ${e.localizedMessage}"
                onResult(false)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    class Factory(
        private val gps: GpsRepository,
        private val media: MediaRepository,
        private val audio: AudioRepository,
        private val sessionManager: SessionManager
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SyncViewModel(gps, media, audio, sessionManager) as T
        }
    }
}