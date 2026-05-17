package com.gos.speed.uwb

import android.content.Context
import androidx.core.uwb.*
import com.gos.speed.data.UwbSupportStatus
import kotlinx.coroutines.flow.*

data class UwbRangeResult(
    val distanceMeters: Float?,
    val azimuthDegrees: Float?,
    val elevationDegrees: Float?,
    val isConnected: Boolean = true
)

data class UwbControllerParams(
    val localAddress: ByteArray,
    val channel: Int,
    val preambleIndex: Int,
    val sessionId: Int,
    val sessionKey: ByteArray
)

class UwbRangingManager(private val context: Context) {

    suspend fun checkSupport(): UwbSupportStatus {
        return try {
            val uwbManager = UwbManager.createInstance(context)
            uwbManager.controllerSessionScope()
            UwbSupportStatus.SUPPORTED
        } catch (e: Exception) {
            UwbSupportStatus.NOT_SUPPORTED
        }
    }

    suspend fun createControllerParams(): UwbControllerParams? {
        return try {
            val uwbManager = UwbManager.createInstance(context)
            val scope = uwbManager.controllerSessionScope()
            val sessionId = (Math.random() * Int.MAX_VALUE).toInt()
            val sessionKey = ByteArray(8).also { java.security.SecureRandom().nextBytes(it) }
            UwbControllerParams(
                localAddress = scope.localAddress.address,
                channel = scope.uwbComplexChannel.channel,
                preambleIndex = scope.uwbComplexChannel.preambleIndex,
                sessionId = sessionId,
                sessionKey = sessionKey
            )
        } catch (e: Exception) {
            null
        }
    }

    fun startControllerRanging(
        params: UwbControllerParams,
        peerAddress: ByteArray
    ): Flow<UwbRangeResult> = flow {
        val uwbManager = UwbManager.createInstance(context)
        val scope = uwbManager.controllerSessionScope()
        val rangingParams = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = params.sessionId,
            subSessionId = 0,
            sessionKeyInfo = params.sessionKey,
            subSessionKeyInfo = null,
            complexChannel = scope.uwbComplexChannel,
            peerDevices = listOf(UwbDevice.createForAddress(peerAddress)),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
        )
        scope.prepareSession(rangingParams).collect { result ->
            when (result) {
                is RangingResult.RangingResultPosition -> emit(
                    UwbRangeResult(
                        distanceMeters = result.position.distance?.value,
                        azimuthDegrees = result.position.azimuth?.value,
                        elevationDegrees = result.position.elevation?.value
                    )
                )
                is RangingResult.RangingResultPeerDisconnected -> emit(
                    UwbRangeResult(null, null, null, isConnected = false)
                )
            }
        }
    }

    fun startControleeRanging(
        hostAddress: ByteArray,
        channel: Int,
        preambleIndex: Int,
        sessionId: Int,
        sessionKey: ByteArray
    ): Flow<UwbRangeResult> = flow {
        val uwbManager = UwbManager.createInstance(context)
        val scope = uwbManager.controleeSessionScope()
        val rangingParams = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = sessionId,
            subSessionId = 0,
            sessionKeyInfo = sessionKey,
            subSessionKeyInfo = null,
            complexChannel = UwbComplexChannel(channel, preambleIndex),
            peerDevices = listOf(UwbDevice.createForAddress(hostAddress)),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
        )
        scope.prepareSession(rangingParams).collect { result ->
            when (result) {
                is RangingResult.RangingResultPosition -> emit(
                    UwbRangeResult(
                        distanceMeters = result.position.distance?.value,
                        azimuthDegrees = result.position.azimuth?.value,
                        elevationDegrees = result.position.elevation?.value
                    )
                )
                is RangingResult.RangingResultPeerDisconnected -> emit(
                    UwbRangeResult(null, null, null, isConnected = false)
                )
            }
        }
    }
}
