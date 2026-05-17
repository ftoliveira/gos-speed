package com.gos.speed.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.UUID

// UWB Service UUID (custom)
private val SERVICE_UUID       = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_HOST_ADDR     = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_CHANNEL       = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_PREAMBLE      = UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_SESSION_ID    = UUID.fromString("6E400005-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_SESSION_KEY   = UUID.fromString("6E400006-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_PEER_ADDR     = UUID.fromString("6E400007-B5A3-F393-E0A9-E50E24DCCA9E")

sealed class BleEvent {
    data class FoundDevice(val address: String, val name: String) : BleEvent()
    data class PeerAddressReceived(val peerAddress: ByteArray) : BleEvent()
    data class HostParamsReceived(
        val hostAddress: ByteArray,
        val channel: Int,
        val preambleIndex: Int,
        val sessionId: Int,
        val sessionKey: ByteArray,
        val myAddressWritten: Boolean = false
    ) : BleEvent()
    data class Error(val message: String) : BleEvent()
    object Advertising : BleEvent()
    object Scanning : BleEvent()
}

@SuppressLint("MissingPermission")
class BleSessionManager(private val context: Context) {

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter = btManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var connectedGatt: BluetoothGatt? = null

    // Host params stored here for the GATT server to serve
    private var hostAddress: ByteArray = ByteArray(2)
    private var channelByte: Byte = 0
    private var preambleByte: Byte = 0
    private var sessionIdBytes: ByteArray = ByteArray(4)
    private var sessionKeyBytes: ByteArray = ByteArray(8)

    fun startHostMode(
        hostAddr: ByteArray,
        channel: Int,
        preamble: Int,
        sessionId: Int,
        sessionKey: ByteArray
    ): Flow<BleEvent> = callbackFlow {

        hostAddress = hostAddr
        channelByte = channel.toByte()
        preambleByte = preamble.toByte()
        sessionIdBytes = byteArrayOf(
            (sessionId shr 24).toByte(),
            (sessionId shr 16).toByte(),
            (sessionId shr 8).toByte(),
            sessionId.toByte()
        )
        sessionKeyBytes = sessionKey

        val gattCallback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice, requestId: Int, offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = when (characteristic.uuid) {
                    CHAR_HOST_ADDR   -> hostAddress
                    CHAR_CHANNEL     -> byteArrayOf(channelByte)
                    CHAR_PREAMBLE    -> byteArrayOf(preambleByte)
                    CHAR_SESSION_ID  -> sessionIdBytes
                    CHAR_SESSION_KEY -> sessionKeyBytes
                    else             -> ByteArray(0)
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice, requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean, responseNeeded: Boolean,
                offset: Int, value: ByteArray
            ) {
                if (characteristic.uuid == CHAR_PEER_ADDR) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                    trySend(BleEvent.PeerAddressReceived(value))
                }
            }
        }

        gattServer = btManager.openGattServer(context, gattCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        fun readable(uuid: UUID) = BluetoothGattCharacteristic(uuid,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        fun writable(uuid: UUID) = BluetoothGattCharacteristic(uuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE)
        service.addCharacteristic(readable(CHAR_HOST_ADDR))
        service.addCharacteristic(readable(CHAR_CHANNEL))
        service.addCharacteristic(readable(CHAR_PREAMBLE))
        service.addCharacteristic(readable(CHAR_SESSION_ID))
        service.addCharacteristic(readable(CHAR_SESSION_KEY))
        service.addCharacteristic(writable(CHAR_PEER_ADDR))
        gattServer?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()
        val advCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                trySend(BleEvent.Advertising)
            }
            override fun onStartFailure(errorCode: Int) {
                trySend(BleEvent.Error("Advertise failed: $errorCode"))
            }
        }
        advertiser = btAdapter.bluetoothLeAdvertiser
        advertiser?.startAdvertising(settings, data, advCallback)

        awaitClose {
            advertiser?.stopAdvertising(advCallback)
            gattServer?.clearServices()
            gattServer?.close()
        }
    }

    fun startGuestMode(myUwbAddress: ByteArray): Flow<BleEvent> = callbackFlow {
        val foundDevices = mutableSetOf<String>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val addr = result.device.address
                if (addr !in foundDevices && result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true) {
                    foundDevices.add(addr)
                    val name = result.device.name ?: "GPS Speed (${addr.takeLast(5)})"
                    trySend(BleEvent.FoundDevice(addr, name))
                }
            }
            override fun onScanFailed(errorCode: Int) {
                trySend(BleEvent.Error("Scan failed: $errorCode"))
            }
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner = btAdapter.bluetoothLeScanner
        scanner?.startScan(listOf(filter), settings, scanCallback)
        trySend(BleEvent.Scanning)

        awaitClose { scanner?.stopScan(scanCallback) }
    }

    fun connectToHost(hostMacAddress: String, myUwbAddress: ByteArray): Flow<BleEvent> = callbackFlow {
        var hostAddr: ByteArray? = null
        var channel: Int? = null
        var preamble: Int? = null
        var sessionId: Int? = null
        var sessionKey: ByteArray? = null

        val charsToRead = mutableListOf(CHAR_HOST_ADDR, CHAR_CHANNEL, CHAR_PREAMBLE, CHAR_SESSION_ID, CHAR_SESSION_KEY)

        fun tryEmitParams(addressWritten: Boolean = false) {
            val ha = hostAddr ?: return
            val ch = channel ?: return
            val pr = preamble ?: return
            val si = sessionId ?: return
            val sk = sessionKey ?: return
            trySend(BleEvent.HostParamsReceived(ha, ch, pr, si, sk, addressWritten))
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        trySend(BleEvent.Error("Disconnected (status=$status)"))
                    }
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service == null) { trySend(BleEvent.Error("Service not found")); return }
                    val nextChar = charsToRead.removeFirstOrNull()
                    if (nextChar != null) gatt.readCharacteristic(service.getCharacteristic(nextChar))
                }
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) { trySend(BleEvent.Error("Read failed")); return }
                val value = characteristic.value ?: return
                when (characteristic.uuid) {
                    CHAR_HOST_ADDR   -> hostAddr = value
                    CHAR_CHANNEL     -> channel = value[0].toInt() and 0xFF
                    CHAR_PREAMBLE    -> preamble = value[0].toInt() and 0xFF
                    CHAR_SESSION_ID  -> sessionId = ((value[0].toInt() and 0xFF) shl 24) or
                                                    ((value[1].toInt() and 0xFF) shl 16) or
                                                    ((value[2].toInt() and 0xFF) shl 8) or
                                                    (value[3].toInt() and 0xFF)
                    CHAR_SESSION_KEY -> sessionKey = value
                }
                val nextChar = charsToRead.removeFirstOrNull()
                if (nextChar != null) {
                    val service = gatt.getService(SERVICE_UUID)
                    gatt.readCharacteristic(service?.getCharacteristic(nextChar))
                } else {
                    // All read — write our address
                    val service = gatt.getService(SERVICE_UUID)
                    val peerChar = service?.getCharacteristic(CHAR_PEER_ADDR)
                    if (peerChar != null) {
                        peerChar.value = myUwbAddress
                        gatt.writeCharacteristic(peerChar)
                    } else {
                        tryEmitParams()
                    }
                }
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                tryEmitParams(addressWritten = true)
            }
        }

        val device = btAdapter.getRemoteDevice(hostMacAddress)
        connectedGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        awaitClose { connectedGatt?.disconnect(); connectedGatt?.close() }
    }

    fun stop() {
        scanner?.stopScan(object : ScanCallback() {})
        advertiser?.stopAdvertising(object : AdvertiseCallback() {})
        gattServer?.clearServices()
        gattServer?.close()
        connectedGatt?.disconnect()
        connectedGatt?.close()
    }
}
