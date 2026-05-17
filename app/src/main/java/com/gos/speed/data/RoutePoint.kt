package com.gos.speed.data

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val timestamp: Long
) {
    val speedKmh: Float get() = speed * 3.6f
}
