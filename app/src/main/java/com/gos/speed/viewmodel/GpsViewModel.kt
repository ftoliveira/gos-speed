package com.gos.speed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gos.speed.data.GpsData
import com.gos.speed.data.RoutePoint
import com.gos.speed.location.GpsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

class GpsViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsManager = GpsManager(application)

    val gpsData: StateFlow<GpsData> = gpsManager.gpsData

    private val _routeHistory = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routeHistory: StateFlow<List<RoutePoint>> = _routeHistory.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _maxSpeed = MutableStateFlow(0f)
    val maxSpeed: StateFlow<Float> = _maxSpeed.asStateFlow()

    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance: StateFlow<Double> = _totalDistance.asStateFlow()

    init {
        viewModelScope.launch {
            gpsData.collect { data ->
                if (data.isActive && _isTracking.value) {
                    val point = RoutePoint(
                        latitude = data.latitude,
                        longitude = data.longitude,
                        altitude = data.altitude,
                        speed = data.speed,
                        timestamp = data.timestamp
                    )
                    val history = _routeHistory.value
                    if (history.isNotEmpty()) {
                        val last = history.last()
                        _totalDistance.value += haversineKm(
                            last.latitude, last.longitude,
                            data.latitude, data.longitude
                        )
                    }
                    _routeHistory.value = history + point
                    if (data.speedKmh > _maxSpeed.value) {
                        _maxSpeed.value = data.speedKmh
                    }
                }
            }
        }
    }

    fun startGps() = gpsManager.startUpdates()
    fun stopGps() = gpsManager.stopUpdates()

    fun startTracking() {
        _isTracking.value = true
        _routeHistory.value = emptyList()
        _maxSpeed.value = 0f
        _totalDistance.value = 0.0
    }

    fun stopTracking() {
        _isTracking.value = false
    }

    fun clearHistory() {
        _routeHistory.value = emptyList()
        _maxSpeed.value = 0f
        _totalDistance.value = 0.0
    }

    override fun onCleared() {
        super.onCleared()
        gpsManager.stopUpdates()
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
