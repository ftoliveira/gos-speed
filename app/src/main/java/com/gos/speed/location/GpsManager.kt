package com.gos.speed.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.*
import com.gos.speed.data.GpsData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()

    private var satelliteCount = 0

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
        .setWaitForAccurateLocation(false)
        .setMinUpdateIntervalMillis(500L)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { updateGpsData(it) }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satelliteCount = (0 until status.satelliteCount).count { i -> status.usedInFix(i) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        try {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {}
        _gpsData.value = _gpsData.value.copy(isActive = true)
    }

    fun stopUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
        try {
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: Exception) {}
        _gpsData.value = _gpsData.value.copy(isActive = false)
    }

    private fun updateGpsData(location: Location) {
        _gpsData.value = GpsData(
            speed = if (location.hasSpeed()) location.speed else 0f,
            altitude = location.altitude,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            satellites = satelliteCount,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            timestamp = location.time,
            isActive = true
        )
    }
}
