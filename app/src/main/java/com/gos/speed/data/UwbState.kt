package com.gos.speed.data

import com.gos.speed.session.SavedSession

enum class UwbSupportStatus { CHECKING, SUPPORTED, NOT_SUPPORTED }
enum class UwbRole { NONE, CONTROLLER, CONTROLEE }
enum class UwbConnectionStatus { IDLE, ADVERTISING, SCANNING, CONNECTING, RANGING, DISCONNECTED, ERROR }
enum class ConnectionMethod { FIREBASE, BLE }

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
    val connectionMethod: ConnectionMethod = ConnectionMethod.FIREBASE,
    val sessionCode: String = "",
    val savedSession: SavedSession? = null,
    val peer: UwbPeer? = null,
    val error: String? = null,
    val supportsAzimuth: Boolean = false
)
