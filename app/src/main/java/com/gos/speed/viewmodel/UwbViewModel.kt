package com.gos.speed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gos.speed.bluetooth.BleEvent
import com.gos.speed.bluetooth.BleSessionManager
import com.gos.speed.data.*
import com.gos.speed.uwb.UwbRangingManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class UwbViewModel(application: Application) : AndroidViewModel(application) {

    private val uwbManager = UwbRangingManager(application)
    private val bleManager = BleSessionManager(application)

    private val _state = MutableStateFlow(UwbScreenState())
    val state: StateFlow<UwbScreenState> = _state.asStateFlow()

    private val _foundDevices = MutableStateFlow<List<UwbPeer>>(emptyList())
    val foundDevices: StateFlow<List<UwbPeer>> = _foundDevices.asStateFlow()

    private var bleJob: Job? = null
    private var uwbJob: Job? = null
    private var controllerParams: com.gos.speed.uwb.UwbControllerParams? = null

    init {
        viewModelScope.launch {
            val support = uwbManager.checkSupport()
            _state.update { it.copy(supportStatus = support) }
        }
    }

    fun startHost() {
        bleJob?.cancel()
        uwbJob?.cancel()
        _state.update { it.copy(role = UwbRole.CONTROLLER, connectionStatus = UwbConnectionStatus.ADVERTISING, error = null) }

        bleJob = viewModelScope.launch {
            val params = uwbManager.createControllerParams()
            if (params == null) {
                _state.update { it.copy(error = "Falha ao iniciar sessão UWB", connectionStatus = UwbConnectionStatus.ERROR) }
                return@launch
            }
            controllerParams = params

            bleManager.startHostMode(
                hostAddr = params.localAddress,
                channel = params.channel,
                preamble = params.preambleIndex,
                sessionId = params.sessionId,
                sessionKey = params.sessionKey
            ).collect { event ->
                when (event) {
                    is BleEvent.Advertising -> _state.update { it.copy(connectionStatus = UwbConnectionStatus.ADVERTISING) }
                    is BleEvent.PeerAddressReceived -> {
                        _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                        startControllerRanging(event.peerAddress)
                    }
                    is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                    else -> {}
                }
            }
        }
    }

    fun startGuest() {
        bleJob?.cancel()
        _foundDevices.value = emptyList()
        _state.update { it.copy(role = UwbRole.CONTROLEE, connectionStatus = UwbConnectionStatus.SCANNING, error = null) }

        bleJob = viewModelScope.launch {
            // Get local UWB address for later
            bleManager.startGuestMode(ByteArray(2)).collect { event ->
                when (event) {
                    is BleEvent.FoundDevice -> {
                        val peer = UwbPeer(address = event.address, name = event.name)
                        _foundDevices.update { list ->
                            if (list.none { it.address == event.address }) list + peer else list
                        }
                    }
                    is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                    else -> {}
                }
            }
        }
    }

    fun connectToDevice(address: String) {
        bleJob?.cancel()
        _state.update { it.copy(connectionStatus = UwbConnectionStatus.CONNECTING, error = null) }

        bleJob = viewModelScope.launch {
            // Get our UWB address
            val myParams = uwbManager.createControllerParams() // just to get a local address
            val myAddress = myParams?.localAddress ?: ByteArray(2)

            bleManager.connectToHost(address, myAddress).collect { event ->
                when (event) {
                    is BleEvent.HostParamsReceived -> {
                        _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                        startControleeRanging(event, myAddress)
                    }
                    is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                    else -> {}
                }
            }
        }
    }

    private fun startControllerRanging(peerAddress: ByteArray) {
        val params = controllerParams ?: return
        uwbJob = viewModelScope.launch {
            uwbManager.startControllerRanging(params, peerAddress).catch { e ->
                _state.update { it.copy(error = e.message ?: "Ranging error", connectionStatus = UwbConnectionStatus.ERROR) }
            }.collect { result ->
                if (!result.isConnected) {
                    _state.update { it.copy(connectionStatus = UwbConnectionStatus.DISCONNECTED, peer = null) }
                    return@collect
                }
                _state.update { s ->
                    s.copy(
                        supportsAzimuth = result.azimuthDegrees != null,
                        peer = (s.peer ?: UwbPeer()).copy(
                            distanceMeters = result.distanceMeters,
                            azimuthDegrees = result.azimuthDegrees,
                            elevationDegrees = result.elevationDegrees
                        )
                    )
                }
            }
        }
    }

    private fun startControleeRanging(event: BleEvent.HostParamsReceived, myAddress: ByteArray) {
        uwbJob = viewModelScope.launch {
            uwbManager.startControleeRanging(
                hostAddress = event.hostAddress,
                channel = event.channel,
                preambleIndex = event.preambleIndex,
                sessionId = event.sessionId,
                sessionKey = event.sessionKey
            ).catch { e ->
                _state.update { it.copy(error = e.message ?: "Ranging error", connectionStatus = UwbConnectionStatus.ERROR) }
            }.collect { result ->
                if (!result.isConnected) {
                    _state.update { it.copy(connectionStatus = UwbConnectionStatus.DISCONNECTED, peer = null) }
                    return@collect
                }
                _state.update { s ->
                    s.copy(
                        supportsAzimuth = result.azimuthDegrees != null,
                        peer = (s.peer ?: UwbPeer()).copy(
                            distanceMeters = result.distanceMeters,
                            azimuthDegrees = result.azimuthDegrees,
                            elevationDegrees = result.elevationDegrees
                        )
                    )
                }
            }
        }
    }

    fun stop() {
        bleJob?.cancel()
        uwbJob?.cancel()
        bleManager.stop()
        _state.update { it.copy(role = UwbRole.NONE, connectionStatus = UwbConnectionStatus.IDLE, peer = null, error = null) }
        _foundDevices.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stop()
    }
}
