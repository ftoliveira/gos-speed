package com.gos.speed.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gos.speed.bluetooth.BleEvent
import com.gos.speed.bluetooth.BleSessionManager
import com.gos.speed.data.*
import com.gos.speed.firebase.FirebaseSessionManager
import com.gos.speed.uwb.UwbRangingManager
import com.gos.speed.uwb.UwbRangeResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class UwbViewModel(application: Application) : AndroidViewModel(application) {

    private val uwbManager = UwbRangingManager(application)
    private val bleManager = BleSessionManager(application)
    private val firebaseManager = FirebaseSessionManager()

    private val _state = MutableStateFlow(UwbScreenState())
    val state: StateFlow<UwbScreenState> = _state.asStateFlow()

    private val _foundDevices = MutableStateFlow<List<UwbPeer>>(emptyList())
    val foundDevices: StateFlow<List<UwbPeer>> = _foundDevices.asStateFlow()

    private var bleJob: Job? = null
    private var uwbJob: Job? = null
    private var controllerParams: com.gos.speed.uwb.UwbControllerParams? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(supportStatus = uwbManager.checkSupport()) }
        }
    }

    fun setConnectionMethod(method: ConnectionMethod) {
        _state.update { it.copy(connectionMethod = method) }
    }

    // ── Firebase ──────────────────────────────────────────────────────────────

    fun startHostFirebase() {
        cancelAll()
        val code = firebaseManager.generateCode()
        _state.update { it.copy(
            role = UwbRole.CONTROLLER,
            connectionStatus = UwbConnectionStatus.ADVERTISING,
            connectionMethod = ConnectionMethod.FIREBASE,
            sessionCode = code,
            error = null
        )}

        bleJob = viewModelScope.launch {
            val params = uwbManager.createControllerParams()
            if (params == null) {
                _state.update { it.copy(error = "Falha ao criar sessão UWB", connectionStatus = UwbConnectionStatus.ERROR) }
                return@launch
            }
            controllerParams = params

            firebaseManager.hostSession(code, params)
                .catch { e -> _state.update { it.copy(error = e.message, connectionStatus = UwbConnectionStatus.ERROR) } }
                .collect { peerAddress ->
                    _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                    startRanging(isController = true, peerAddress = peerAddress)
                }
        }
    }

    fun joinSessionFirebase(code: String) {
        cancelAll()
        _state.update { it.copy(
            role = UwbRole.CONTROLEE,
            connectionStatus = UwbConnectionStatus.CONNECTING,
            connectionMethod = ConnectionMethod.FIREBASE,
            sessionCode = code,
            error = null
        )}

        bleJob = viewModelScope.launch {
            val myAddress = uwbManager.createControllerParams()?.localAddress ?: ByteArray(2)
            val result = firebaseManager.joinSession(code.uppercase().trim(), myAddress)
            result.fold(
                onSuccess = { hostParams ->
                    _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                    startRanging(
                        isController = false,
                        hostAddress = hostParams.localAddress,
                        channel = hostParams.channel,
                        preambleIndex = hostParams.preambleIndex,
                        sessionId = hostParams.sessionId,
                        sessionKey = hostParams.sessionKey
                    )
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message, connectionStatus = UwbConnectionStatus.ERROR) }
                }
            )
        }
    }

    // ── BLE ───────────────────────────────────────────────────────────────────

    fun startHostBle() {
        cancelAll()
        _state.update { it.copy(
            role = UwbRole.CONTROLLER,
            connectionStatus = UwbConnectionStatus.ADVERTISING,
            connectionMethod = ConnectionMethod.BLE,
            sessionCode = "",
            error = null
        )}

        bleJob = viewModelScope.launch {
            val params = uwbManager.createControllerParams()
            if (params == null) {
                _state.update { it.copy(error = "Falha ao criar sessão UWB", connectionStatus = UwbConnectionStatus.ERROR) }
                return@launch
            }
            controllerParams = params

            bleManager.startHostMode(params.localAddress, params.channel, params.preambleIndex, params.sessionId, params.sessionKey)
                .collect { event ->
                    when (event) {
                        is BleEvent.Advertising -> _state.update { it.copy(connectionStatus = UwbConnectionStatus.ADVERTISING) }
                        is BleEvent.PeerAddressReceived -> {
                            _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                            startRanging(isController = true, peerAddress = event.peerAddress)
                        }
                        is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                        else -> {}
                    }
                }
        }
    }

    fun startGuestBle() {
        cancelAll()
        _foundDevices.value = emptyList()
        _state.update { it.copy(
            role = UwbRole.CONTROLEE,
            connectionStatus = UwbConnectionStatus.SCANNING,
            connectionMethod = ConnectionMethod.BLE,
            sessionCode = "",
            error = null
        )}

        bleJob = viewModelScope.launch {
            bleManager.startGuestMode(ByteArray(2)).collect { event ->
                when (event) {
                    is BleEvent.FoundDevice -> _foundDevices.update { list ->
                        if (list.none { it.address == event.address })
                            list + UwbPeer(address = event.address, name = event.name)
                        else list
                    }
                    is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                    else -> {}
                }
            }
        }
    }

    fun connectToBlePeer(address: String) {
        bleJob?.cancel()
        _state.update { it.copy(connectionStatus = UwbConnectionStatus.CONNECTING, error = null) }

        bleJob = viewModelScope.launch {
            val myAddress = uwbManager.createControllerParams()?.localAddress ?: ByteArray(2)
            bleManager.connectToHost(address, myAddress).collect { event ->
                when (event) {
                    is BleEvent.HostParamsReceived -> {
                        _state.update { it.copy(connectionStatus = UwbConnectionStatus.RANGING) }
                        startRanging(
                            isController = false,
                            hostAddress = event.hostAddress,
                            channel = event.channel,
                            preambleIndex = event.preambleIndex,
                            sessionId = event.sessionId,
                            sessionKey = event.sessionKey
                        )
                    }
                    is BleEvent.Error -> _state.update { it.copy(error = event.message, connectionStatus = UwbConnectionStatus.ERROR) }
                    else -> {}
                }
            }
        }
    }

    // ── Ranging (shared) ──────────────────────────────────────────────────────

    private fun startRanging(
        isController: Boolean,
        peerAddress: ByteArray = ByteArray(2),
        hostAddress: ByteArray = ByteArray(2),
        channel: Int = 0,
        preambleIndex: Int = 0,
        sessionId: Int = 0,
        sessionKey: ByteArray = ByteArray(8)
    ) {
        uwbJob = viewModelScope.launch {
            val flow = if (isController) {
                val params = controllerParams ?: return@launch
                uwbManager.startControllerRanging(params, peerAddress)
            } else {
                uwbManager.startControleeRanging(hostAddress, channel, preambleIndex, sessionId, sessionKey)
            }

            flow.catch { e ->
                _state.update { it.copy(error = e.message ?: "Ranging error", connectionStatus = UwbConnectionStatus.ERROR) }
            }.collect { result ->
                handleRangingResult(result)
            }
        }
    }

    private fun handleRangingResult(result: UwbRangeResult) {
        if (!result.isConnected) {
            _state.update { it.copy(connectionStatus = UwbConnectionStatus.DISCONNECTED, peer = null) }
            return
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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun stop() {
        val code = _state.value.sessionCode
        if (_state.value.connectionMethod == ConnectionMethod.FIREBASE && code.isNotEmpty()) {
            firebaseManager.cleanupSession(code)
        }
        cancelAll()
        bleManager.stop()
        _state.update { it.copy(
            role = UwbRole.NONE,
            connectionStatus = UwbConnectionStatus.IDLE,
            sessionCode = "",
            peer = null,
            error = null
        )}
        _foundDevices.value = emptyList()
    }

    private fun cancelAll() {
        bleJob?.cancel()
        uwbJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stop()
    }
}
