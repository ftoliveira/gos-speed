package com.gos.speed.data

data class GpsData(
    val speed: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val satellites: Int = 0,
    val bearing: Float = 0f,
    val timestamp: Long = 0L,
    val isActive: Boolean = false
) {
    val speedKmh: Float get() = speed * 3.6f
    val speedMph: Float get() = speed * 2.23694f
}
