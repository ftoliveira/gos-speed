package com.gos.speed.data

enum class UwbSupportStatus { CHECKING, SUPPORTED, NOT_SUPPORTED }
enum class UwbRole { NONE, CONTROLLER, CONTROLEE }
enum class UwbConnectionStatus { IDLE, ADVERTISING, SCANNING, CONNECTING, RANGING, DISCONNECTED, ERROR }

data class UwbPeer(
    val address: String = "",
    val name: String = "",
    val distanceMeters: Float? = null,
    val azimuthDegrees: Float? = null,
    val elevationDegrees: Float? = null
)

data class UwbScreenState(
    val supportStatus: UwbSupportStatus = UwbSupportStatus.CHECKING,
    val role: UwbRole = UwbRole.NONE,
    val connectionStatus: UwbConnectionStatus = UwbConnectionStatus.IDLE,
    val peer: UwbPeer? = null,
    val error: String? = null,
    val supportsAzimuth: Boolean = false
)
