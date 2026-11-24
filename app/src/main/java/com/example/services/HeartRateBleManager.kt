package com.example.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.services.integration.HealthConnectConnector
import com.example.services.integration.WearableMetrics
import com.example.services.integration.WorkoutHealthConnectSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

enum class HrConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

enum class WearableDeviceCategory {
    BLE_CHEST_STRAP,
    SMARTWATCH_BRIDGE
}

data class ScannedBleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice? = null,
    val rssi: Int = -60,
    val isBondedOrSystemConnected: Boolean = false,
    val category: WearableDeviceCategory = WearableDeviceCategory.BLE_CHEST_STRAP
)

typealias BleHrDevice = ScannedBleDevice

@SuppressLint("MissingPermission")
class HeartRateBleManager private constructor(private val context: Context) {

    private val bluetoothManager = runCatching {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }.getOrNull()
    private val bluetoothAdapter: BluetoothAdapter? = runCatching { bluetoothManager?.adapter }.getOrNull()
    private var bluetoothGatt: BluetoothGatt? = null

    private val prefs = context.getSharedPreferences("max_ble_wearable_prefs", Context.MODE_PRIVATE)
    private val healthConnectConnector = HealthConnectConnector(context.applicationContext)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanJob: Job? = null
    private var connectionTimeoutJob: Job? = null
    private var simulationJob: Job? = null
    private var smartwatchBridgeLiveJob: Job? = null
    @Volatile
    private var lastDirectGattBpmMs: Long = 0L
    @Volatile
    private var hasAttemptedAutoWatchGatt: Boolean = false

    private val _connectionState = MutableStateFlow(HrConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HrConnectionState> = _connectionState.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _isUsingSmartwatchBridgeBpm = MutableStateFlow(false)
    val isUsingSmartwatchBridgeBpm: StateFlow<Boolean> = _isUsingSmartwatchBridgeBpm.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _activeGattTargetAddress = MutableStateFlow<String?>(
        prefs.getString(KEY_SAVED_BLE_DEVICE_ADDR, null) ?: prefs.getString(KEY_LINKED_SMARTWATCH_ADDR, null)
    )
    val activeGattTargetAddress: StateFlow<String?> = _activeGattTargetAddress.asStateFlow()

    private val _savedBleDeviceAddress = MutableStateFlow<String?>(
        prefs.getString(KEY_SAVED_BLE_DEVICE_ADDR, null)
    )
    val savedBleDeviceAddress: StateFlow<String?> = _savedBleDeviceAddress.asStateFlow()

    private val _gattStatusMessage = MutableStateFlow<String?>(null)
    val gattStatusMessage: StateFlow<String?> = _gattStatusMessage.asStateFlow()

    private val _isUuid180dClosedOnWatch = MutableStateFlow(false)
    val isUuid180dClosedOnWatch: StateFlow<Boolean> = _isUuid180dClosedOnWatch.asStateFlow()

    @Volatile
    private var isFallbackAutoConnectAttempt: Boolean = false

    @Volatile
    private var isCurrentGattTargetSmartwatch: Boolean = false

    private val _scannedDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedBleDevice>> = _scannedDevices.asStateFlow()
    val discoveredDevices: StateFlow<List<ScannedBleDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Estado do Modo Duplo: Smartwatch vinculado via Ponte Mi Fitness / Health Connect (coexiste com a Cinta BLE)
    private val _linkedSmartwatchName = MutableStateFlow<String?>(
        prefs.getString(KEY_LINKED_SMARTWATCH_NAME, null)
    )
    val linkedSmartwatchName: StateFlow<String?> = _linkedSmartwatchName.asStateFlow()

    private val _linkedSmartwatchAddress = MutableStateFlow<String?>(
        prefs.getString(KEY_LINKED_SMARTWATCH_ADDR, null)
    )
    val linkedSmartwatchAddress: StateFlow<String?> = _linkedSmartwatchAddress.asStateFlow()

    private val _isSmartwatchBridgeActive = MutableStateFlow(
        prefs.getBoolean(KEY_SMARTWATCH_BRIDGE_ACTIVE, false)
    )
    val isSmartwatchBridgeActive: StateFlow<Boolean> = _isSmartwatchBridgeActive.asStateFlow()

    private val _smartwatchDailyMetrics = MutableStateFlow(
        WearableMetrics(
            steps = prefs.getInt(KEY_SW_STEPS, 0),
            averageHeartRate = prefs.getInt(KEY_SW_RESTING_HR, 0),
            activeCalories = prefs.getInt(KEY_SW_ACTIVE_KCAL, 0),
            sleepingHours = prefs.getFloat(KEY_SW_SLEEP_HOURS, 0f),
            hydrationMl = 0,
            stressLevel = 0,
            batteryBodyPercent = 0,
            hrvStatusMs = 0
        )
    )
    val smartwatchDailyMetrics: StateFlow<WearableMetrics> = _smartwatchDailyMetrics.asStateFlow()

    // Amostras thread-safe durante o treino para calcular média, máx e linha do tempo sem ConcurrentModificationException
    private val recordedBpmSamples = CopyOnWriteArrayList<Int>()
    private val timelineSamples = CopyOnWriteArrayList<Int>()
    @Volatile
    private var isRecordingSession = false
    @Volatile
    private var lastTimelineRecordMs = 0L

    companion object {
        private const val TAG = "HeartRateBleManager"
        private const val KEY_LINKED_SMARTWATCH_NAME = "linked_smartwatch_name"
        private const val KEY_LINKED_SMARTWATCH_ADDR = "linked_smartwatch_addr"
        private const val KEY_SAVED_BLE_DEVICE_ADDR = "saved_ble_device_addr"
        private const val KEY_SAVED_BLE_DEVICE_NAME = "saved_ble_device_name"
        private const val KEY_SMARTWATCH_BRIDGE_ACTIVE = "smartwatch_bridge_active"
        private const val KEY_SW_STEPS = "sw_daily_steps"
        private const val KEY_SW_RESTING_HR = "sw_resting_hr"
        private const val KEY_SW_ACTIVE_KCAL = "sw_active_kcal"
        private const val KEY_SW_SLEEP_HOURS = "sw_sleep_hours"

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        private val CHEST_STRAP_KEYWORDS = listOf(
            "h500", "gehod", "xoss", "polar h", "polar oh", "polar verity",
            "magene", "coospo", "wahoo", "tickr", "hrm", "cinta", "chest"
        )

        private val SMARTWATCH_KEYWORDS = listOf(
            "redmi", "watch", "xiaomi", "mi band", "smart band", "amazfit",
            "stratos", "haylou", "zeblaze", "garmin", "polar", "coros",
            "huawei", "galaxy watch", "fitbit", "mibro", "bip", "gtr", "gts"
        )

        fun classifyDeviceName(rawName: String?): Pair<Boolean, WearableDeviceCategory> {
            val name = rawName?.trim().orEmpty()
            if (name.isEmpty()) return false to WearableDeviceCategory.BLE_CHEST_STRAP
            val lower = name.lowercase()
            if (CHEST_STRAP_KEYWORDS.any { lower.contains(it) }) {
                return true to WearableDeviceCategory.BLE_CHEST_STRAP
            }
            if (SMARTWATCH_KEYWORDS.any { lower.contains(it) }) {
                return true to WearableDeviceCategory.SMARTWATCH_BRIDGE
            }
            return false to WearableDeviceCategory.BLE_CHEST_STRAP
        }

        @Volatile
        private var INSTANCE: HeartRateBleManager? = null

        fun getInstance(context: Context): HeartRateBleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HeartRateBleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        runCatching {
            refreshBondedAndSystemConnectedDevices()
            if (_isSmartwatchBridgeActive.value) {
                syncSmartwatchHealthConnectMetrics()
                startSmartwatchBridgeLiveFeedIfNeeded()
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Consulta imediatamente `bluetoothAdapter.bondedDevices` e `bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)`
     * para detectar smartwatches já conectados/pareados pelo Mi Fitness / Zepp / Garmin Connect (ex: Redmi Watch 5)
     * e cintas peitorais (ex: GEHOD H500) mesmo quando não estão emitindo pacotes abertos de advertising 0x180D.
     */
    fun refreshBondedAndSystemConnectedDevices(): List<ScannedBleDevice> {
        if (!hasBluetoothConnectPermission()) return _scannedDevices.value
        val foundMap = LinkedHashMap<String, ScannedBleDevice>()

        // Preserva dispositivos com MAC válido já descobertos na sessão atual
        _scannedDevices.value.forEach { existing ->
            if (BluetoothAdapter.checkBluetoothAddress(existing.address)) {
                foundMap[existing.address] = existing
            }
        }

        runCatching {
            val bonded = bluetoothAdapter?.bondedDevices.orEmpty()
            val gattConnected = runCatching {
                bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
            }.getOrDefault(emptyList())

            val allSystemDevices = (gattConnected + bonded).distinctBy { it.address }
            for (btDevice in allSystemDevices) {
                val devName = runCatching { btDevice.name }.getOrNull()?.trim().orEmpty()
                if (devName.isEmpty()) continue
                val (isRelevant, category) = classifyDeviceName(devName)
                if (isRelevant) {
                    val addr = btDevice.address ?: continue
                    foundMap[addr] = ScannedBleDevice(
                        name = devName,
                        address = addr,
                        device = btDevice,
                        rssi = -48,
                        isBondedOrSystemConnected = true,
                        category = category
                    )
                    // Se o Redmi Watch 5 Lite / Haylou GS já está pareado/conectado no sistema Android:
                    // atualiza o MAC address real vinculado e tenta assinar o canal GATT 0x180D via MAC direto
                    if (category == WearableDeviceCategory.SMARTWATCH_BRIDGE) {
                        if (!_isSmartwatchBridgeActive.value ||
                            _linkedSmartwatchAddress.value == "MI-FITNESS-HC" ||
                            _linkedSmartwatchAddress.value.isNullOrBlank()
                        ) {
                            linkSmartwatchBridge(
                                devName,
                                addr,
                                btDevice,
                                tryGattHr = !hasAttemptedAutoWatchGatt && !_isUuid180dClosedOnWatch.value
                            )
                        } else if (devName.equals(_linkedSmartwatchName.value, ignoreCase = true) ||
                            addr.equals(_linkedSmartwatchAddress.value, ignoreCase = true)
                        ) {
                            startSmartwatchBridgeLiveFeedIfNeeded()
                            if (!hasAttemptedAutoWatchGatt &&
                                !_isUuid180dClosedOnWatch.value &&
                                _connectionState.value == HrConnectionState.DISCONNECTED
                            ) {
                                hasAttemptedAutoWatchGatt = true
                                runCatching {
                                    connectDirectByMac(addr, devName, isSmartwatch = true)
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Aviso ao consultar dispositivos Bluetooth pareados/conectados: ${e.message}")
        }

        val updatedList = foundMap.values.sortedWith(
            compareByDescending<ScannedBleDevice> { it.isBondedOrSystemConnected }
                .thenBy { it.category.ordinal }
                .thenByDescending { it.rssi }
        )
        _scannedDevices.value = updatedList
        return updatedList
    }

    /**
     * Ativa ou atualiza a Ponte Mi Fitness / Health Connect para o Smartwatch (ex: Redmi Watch 5 Lite / Haylou GS),
     * coexistindo sem conflito com a Cinta Peitoral BLE titular e disparando conexão GATT direta via MAC (`TRANSPORT_LE`).
     */
    fun linkSmartwatchBridge(
        deviceName: String = "Redmi Watch 5",
        address: String? = null,
        hwDevice: BluetoothDevice? = null,
        tryGattHr: Boolean = true
    ) {
        var resolvedHwDevice = hwDevice
        var resolvedAddr = address
        var resolvedName = deviceName.trim().ifEmpty { "Redmi Watch 5" }

        if ((resolvedAddr == null || !BluetoothAdapter.checkBluetoothAddress(resolvedAddr)) && hasBluetoothConnectPermission()) {
            val bondedMatch = runCatching {
                val bonded = bluetoothAdapter?.bondedDevices.orEmpty()
                val gattConn = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
                (gattConn + bonded).firstOrNull { dev ->
                    val n = runCatching { dev.name }.getOrNull()?.trim().orEmpty()
                    n.isNotEmpty() && (n.equals(resolvedName, ignoreCase = true) ||
                        classifyDeviceName(n).second == WearableDeviceCategory.SMARTWATCH_BRIDGE)
                }
            }.getOrNull()
            if (bondedMatch != null) {
                resolvedHwDevice = bondedMatch
                resolvedAddr = bondedMatch.address
                val realName = runCatching { bondedMatch.name }.getOrNull()?.trim()
                if (!realName.isNullOrEmpty()) {
                    resolvedName = realName
                }
            }
        }

        val finalAddr = resolvedAddr ?: _linkedSmartwatchAddress.value ?: "MI-FITNESS-HC"
        _linkedSmartwatchName.value = resolvedName
        _linkedSmartwatchAddress.value = finalAddr
        _isSmartwatchBridgeActive.value = true

        val editor = prefs.edit()
            .putString(KEY_LINKED_SMARTWATCH_NAME, resolvedName)
            .putString(KEY_LINKED_SMARTWATCH_ADDR, finalAddr)
            .putBoolean(KEY_SMARTWATCH_BRIDGE_ACTIVE, true)
        if (BluetoothAdapter.checkBluetoothAddress(finalAddr)) {
            _savedBleDeviceAddress.value = finalAddr
            editor.putString(KEY_SAVED_BLE_DEVICE_ADDR, finalAddr)
            editor.putString(KEY_SAVED_BLE_DEVICE_NAME, resolvedName)
        }
        editor.apply()

        // Garante que o smartwatch vinculado também conste na lista interna de dispositivos conhecidos
        val current = _scannedDevices.value.toMutableList()
        if (current.none { it.name.equals(resolvedName, ignoreCase = true) || it.address.equals(finalAddr, ignoreCase = true) }) {
            current.add(
                0,
                ScannedBleDevice(
                    name = resolvedName,
                    address = finalAddr,
                    device = resolvedHwDevice,
                    rssi = -46,
                    isBondedOrSystemConnected = true,
                    category = WearableDeviceCategory.SMARTWATCH_BRIDGE
                )
            )
            _scannedDevices.value = current
        }

        syncSmartwatchHealthConnectMetrics()
        startSmartwatchBridgeLiveFeedIfNeeded()

        if (tryGattHr) {
            hasAttemptedAutoWatchGatt = true
            if (BluetoothAdapter.checkBluetoothAddress(finalAddr)) {
                connectDirectByMac(finalAddr, resolvedName, isSmartwatch = true)
            } else if (resolvedHwDevice != null && BluetoothAdapter.checkBluetoothAddress(resolvedHwDevice.address)) {
                connectDirectByMac(resolvedHwDevice.address, resolvedName, isSmartwatch = true)
            } else {
                _gattStatusMessage.value = "Conectado via ponte Health Connect (busque nos pareados para canal GATT 0x180D)"
            }
        }
    }

    @Volatile
    var lastRealBleHrTimestamp: Long = 0L
        private set

    /**
     * Retorna true exclusivamente se um pacote BLE real do serviço 0x180D (0x2A37) tiver sido recebido
     * nos últimos 12 segundos (ou se o modo de teste explícito da cinta simulada estiver ativo).
     */
    fun hasFreshRealBleHeartRate(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (simulationJob?.isActive == true && _connectionState.value == HrConnectionState.CONNECTED) {
            return true
        }
        return lastRealBleHrTimestamp > 0L && (nowMs - lastRealBleHrTimestamp) < 12_000L
    }

    /**
     * Monitora a validade temporal do sinal BLE 0x180D (janela estrita de 12 segundos).
     * É ESTRITAMENTE PROIBIDO exibir "72 bpm" estático do Health Connect ou gerar FC simulada por velocidade
     * quando o relógio não está transmitindo pacotes BLE 0x180D reais.
     */
    private fun startSmartwatchBridgeLiveFeedIfNeeded() {
        _isUsingSmartwatchBridgeBpm.value = false
        if (!hasFreshRealBleHeartRate()) {
            _currentBpm.value = 0
        }
        if (smartwatchBridgeLiveJob?.isActive == true) return

        smartwatchBridgeLiveJob = scope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val hasActiveSimulation = simulationJob?.isActive == true
                if (!hasActiveSimulation) {
                    val isFresh = lastRealBleHrTimestamp > 0L && (now - lastRealBleHrTimestamp) < 12_000L
                    if (!isFresh && _currentBpm.value != 0) {
                        _currentBpm.value = 0
                    }
                }
                delay(1000L)
            }
        }
    }

    fun unlinkSmartwatchBridge() {
        _isSmartwatchBridgeActive.value = false
        _isUsingSmartwatchBridgeBpm.value = false
        _linkedSmartwatchName.value = null
        _linkedSmartwatchAddress.value = null
        if (!hasFreshRealBleHeartRate()) {
            _currentBpm.value = 0
        }
        prefs.edit()
            .remove(KEY_LINKED_SMARTWATCH_NAME)
            .remove(KEY_LINKED_SMARTWATCH_ADDR)
            .putBoolean(KEY_SMARTWATCH_BRIDGE_ACTIVE, false)
            .apply()
    }

    fun getHealthConnectRequiredPermissions(): Set<String> =
        HealthConnectConnector.REQUIRED_PERMISSIONS

    fun isHealthConnectAvailable(): Boolean =
        runCatching { healthConnectConnector.isAvailable() }.getOrDefault(false)

    suspend fun hasHealthConnectPermissions(): Boolean =
        runCatching { healthConnectConnector.hasAllPermissions() }.getOrDefault(false)

    suspend fun fetchWorkoutWindowFromHealthConnect(
        startTimeMillis: Long,
        endTimeMillis: Long
    ): WorkoutHealthConnectSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            healthConnectConnector.fetchWorkoutWindowMetrics(startTimeMillis, endTimeMillis)
        }.getOrNull()
    }

    fun syncSmartwatchHealthConnectMetrics() {
        scope.launch {
            runCatching {
                val result = healthConnectConnector.fetchLatestMetrics()
                result.onSuccess { metrics ->
                    val realMetrics = metrics.copy(
                        steps = metrics.steps.coerceAtLeast(0),
                        averageHeartRate = metrics.averageHeartRate.coerceAtLeast(0),
                        activeCalories = metrics.activeCalories.coerceAtLeast(0),
                        sleepingHours = metrics.sleepingHours.coerceAtLeast(0f),
                        timestamp = System.currentTimeMillis()
                    )
                    _smartwatchDailyMetrics.value = realMetrics
                    prefs.edit()
                        .putInt(KEY_SW_STEPS, realMetrics.steps)
                        .putInt(KEY_SW_RESTING_HR, realMetrics.averageHeartRate)
                        .putInt(KEY_SW_ACTIVE_KCAL, realMetrics.activeCalories)
                        .putFloat(KEY_SW_SLEEP_HOURS, realMetrics.sleepingHours)
                        .apply()
                }
            }.onFailure { e ->
                Log.w(TAG, "Falha silenciosa ao sincronizar métricas da ponte Health Connect: ${e.message}")
            }
        }
    }

    fun resetSessionStats() {
        recordedBpmSamples.clear()
        timelineSamples.clear()
        lastTimelineRecordMs = 0L
    }

    fun startTimelineRecording() {
        resetSessionStats()
        isRecordingSession = true
        // Ao iniciar um treino, se ainda não estiver conectado e houver um MAC salvo ou relógio pareado,
        // conecta diretamente via GATT (BluetoothDevice.TRANSPORT_LE) sem depender do BluetoothLeScanner!
        if (_connectionState.value != HrConnectionState.CONNECTED) {
            autoConnectSavedOrBondedDeviceIfNeeded()
        }
    }

    fun autoConnectSavedOrBondedDeviceIfNeeded() {
        runCatching {
            if (!hasBluetoothConnectPermission()) return
            val savedAddr = _savedBleDeviceAddress.value?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
                ?: _linkedSmartwatchAddress.value?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
            val savedName = prefs.getString(KEY_SAVED_BLE_DEVICE_NAME, null)
                ?: _linkedSmartwatchName.value
            if (savedAddr != null) {
                val isWatch = classifyDeviceName(savedName).second == WearableDeviceCategory.SMARTWATCH_BRIDGE
                if (isWatch && (_isUuid180dClosedOnWatch.value || hasAttemptedAutoWatchGatt)) {
                    return
                }
                if (isWatch) hasAttemptedAutoWatchGatt = true
                connectDirectByMac(savedAddr, savedName, isSmartwatch = isWatch)
                return
            }
            val list = refreshBondedAndSystemConnectedDevices()
            val firstBonded = list.firstOrNull { dev ->
                BluetoothAdapter.checkBluetoothAddress(dev.address) &&
                    !(dev.category == WearableDeviceCategory.SMARTWATCH_BRIDGE &&
                        (_isUuid180dClosedOnWatch.value || hasAttemptedAutoWatchGatt))
            }
            if (firstBonded != null) {
                val isWatch = firstBonded.category == WearableDeviceCategory.SMARTWATCH_BRIDGE
                if (isWatch) hasAttemptedAutoWatchGatt = true
                connectDirectByMac(
                    macAddress = firstBonded.address,
                    deviceName = firstBonded.name,
                    isSmartwatch = isWatch
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "Falha segura ao reconectar BLE automaticamente: ${e.message}")
        }
    }

    fun stopTimelineRecording() {
        isRecordingSession = false
    }

    fun getAverageBpm(): Int? {
        val snapshot = recordedBpmSamples.toList()
        if (snapshot.isEmpty()) return null
        return snapshot.average().toInt()
    }

    fun getMaxBpm(): Int? {
        val snapshot = recordedBpmSamples.toList()
        if (snapshot.isEmpty()) return null
        return snapshot.maxOrNull()
    }

    fun getHrTimelineString(): String {
        val snapshot = timelineSamples.toList()
        if (snapshot.isEmpty()) return ""
        return snapshot.mapIndexed { index, bpm -> "${index * 10}:$bpm" }.joinToString(",")
    }

    fun startScan() {
        // 1. Sempre carrega imediatamente os dispositivos pareados/conectados no sistema Android (Redmi Watch 5, GEHOD H500, etc.)
        _scannedDevices.value = emptyList()
        refreshBondedAndSystemConnectedDevices()

        if (!hasBluetoothScanPermission()) {
            Log.w(TAG, "Permissão BLUETOOTH_SCAN ausente; abortando scan BLE de forma limpa.")
            _isScanning.value = false
            if (_connectionState.value == HrConnectionState.SCANNING) {
                _connectionState.value = HrConnectionState.DISCONNECTED
            }
            _gattStatusMessage.value = "Permissão de busca Bluetooth (BLUETOOTH_SCAN) necessária."
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _isScanning.value = false
            return
        }
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull() ?: run {
            _isScanning.value = false
            return
        }
        if (_isScanning.value) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Usa scan sem filtro restritivo de UUID no pacote primário para enxergar tanto Cintas BLE (0x180D)
        // quanto Smartwatches (Redmi Watch 5, Xiaomi, Amazfit, Garmin, Polar), filtrando por UUID ou nome no callback.
        try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            if (_connectionState.value == HrConnectionState.DISCONNECTED) {
                _connectionState.value = HrConnectionState.SCANNING
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao iniciar BLE scan completo, tentando com filtro padrão: ${e.message}")
            try {
                val filter = ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                    .build()
                scanner.startScan(listOf(filter), settings, scanCallback)
                _isScanning.value = true
                if (_connectionState.value == HrConnectionState.DISCONNECTED) {
                    _connectionState.value = HrConnectionState.SCANNING
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Não foi possível iniciar o scanner BLE: ${e2.message}")
                _isScanning.value = false
                return
            }
        }

        scanJob?.cancel()
        scanJob = scope.launch {
            delay(10_000)
            stopScan()
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null
        if (_connectionState.value == HrConnectionState.SCANNING) {
            _connectionState.value = HrConnectionState.DISCONNECTED
        }
        if (!hasBluetoothScanPermission()) return
        try {
            bluetoothAdapter?.takeIf { it.isEnabled }?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao parar scan BLE: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val deviceName = runCatching { device.name }.getOrNull()
                ?: result.scanRecord?.deviceName
                ?: return
            val hasHrUuid = result.scanRecord?.serviceUuids?.any {
                it.uuid == HEART_RATE_SERVICE_UUID
            } == true
            val (isRelevantName, categoryByName) = classifyDeviceName(deviceName)
            if (!hasHrUuid && !isRelevantName) return

            val category = if (categoryByName == WearableDeviceCategory.SMARTWATCH_BRIDGE && !hasHrUuid) {
                WearableDeviceCategory.SMARTWATCH_BRIDGE
            } else if (CHEST_STRAP_KEYWORDS.any { deviceName.lowercase().contains(it) }) {
                WearableDeviceCategory.BLE_CHEST_STRAP
            } else if (SMARTWATCH_KEYWORDS.any { deviceName.lowercase().contains(it) }) {
                WearableDeviceCategory.SMARTWATCH_BRIDGE
            } else {
                WearableDeviceCategory.BLE_CHEST_STRAP
            }

            val currentList = _scannedDevices.value
            if (currentList.none { it.address == device.address }) {
                _scannedDevices.value = currentList + ScannedBleDevice(
                    name = deviceName,
                    address = device.address,
                    device = device,
                    rssi = result.rssi,
                    isBondedOrSystemConnected = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false),
                    category = category
                )
            }
        }
    }

    fun connect(address: String) {
        val found = _scannedDevices.value.firstOrNull { it.address.equals(address, ignoreCase = true) }
        if (found != null) {
            connectToDevice(found)
        } else if (BluetoothAdapter.checkBluetoothAddress(address)) {
            connectDirectByMac(address)
        }
    }

    fun connectToDevice(device: ScannedBleDevice) {
        stopScan()
        val isWatch = device.category == WearableDeviceCategory.SMARTWATCH_BRIDGE
        if (isWatch) {
            // Mantém a Ponte Health Connect sincronizada E SEMPRE força a conexão GATT direta por MAC Address
            // quando o usuário toca em VINCULAR / VINCULADO / CONECTAR AGORA!
            linkSmartwatchBridge(
                deviceName = device.name,
                address = device.address,
                hwDevice = device.device,
                tryGattHr = false
            )
            connectDirectByMac(
                macAddress = device.address,
                deviceName = device.name,
                isSmartwatch = true
            )
            return
        }

        // Cinta Peitoral BLE titular (ex: GEHOD H500 / XOSS X2 PRO)
        connectDirectByMac(
            macAddress = device.address,
            deviceName = device.name,
            isSmartwatch = false
        )
    }

    /**
     * Conexão GATT Direta por MAC Address Sem Depender de Scan (`connectDirectByMac`):
     * - Usa diretamente `bluetoothAdapter.getRemoteDevice(macAddress).connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)`.
     * - Limpa qualquer conexão GATT anterior (`disconnect(); close()`) antes de conectar para evitar `GATT_ERROR (133)`.
     * - Se `autoConnect = false` der timeout após 6 segundos sem resposta (ou falhar com status != 0 na 1ª tentativa),
     *   faz 1 tentativa automática de fallback com `autoConnect = true`.
     */
    fun connectDirectByMac(
        macAddress: String,
        deviceName: String? = null,
        isSmartwatch: Boolean = false
    ) {
        stopScan()
        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Permissão BLUETOOTH_CONNECT ausente; abortando connectDirectByMac.")
            _connectionState.value = HrConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _gattStatusMessage.value = "Permissão de conexão Bluetooth (BLUETOOTH_CONNECT) necessária."
            return
        }
        val adapter = bluetoothAdapter
        val isBtEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        if (adapter == null || !isBtEnabled) {
            _gattStatusMessage.value = "Bluetooth desligado no aparelho. Ative o Bluetooth para conectar."
            return
        }

        var targetMac = macAddress.trim().uppercase()
        var resolvedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }

        if (!BluetoothAdapter.checkBluetoothAddress(targetMac) && hasBluetoothConnectPermission()) {
            val bondedMatch = runCatching {
                val bonded = adapter.bondedDevices.orEmpty()
                val gattConn = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
                (gattConn + bonded).firstOrNull { dev ->
                    val n = runCatching { dev.name }.getOrNull()?.trim().orEmpty()
                    n.isNotEmpty() && (
                        (resolvedName != null && n.equals(resolvedName, ignoreCase = true)) ||
                        classifyDeviceName(n).first
                    )
                }
            }.getOrNull()
            if (bondedMatch != null) {
                targetMac = bondedMatch.address
                if (resolvedName.isNullOrBlank()) {
                    resolvedName = runCatching { bondedMatch.name }.getOrNull()?.trim()
                }
            }
        }

        if (!BluetoothAdapter.checkBluetoothAddress(targetMac)) {
            _gattStatusMessage.value = "Dispositivo sem endereço MAC válido. Pareie o relógio no Bluetooth do Android."
            return
        }

        val hwDevice = runCatching { adapter.getRemoteDevice(targetMac) }.getOrNull()
        if (hwDevice == null) {
            _connectionState.value = HrConnectionState.DISCONNECTED
            _gattStatusMessage.value = "Não foi possível acessar o endereço MAC $targetMac."
            return
        }

        val finalName = resolvedName
            ?: runCatching { hwDevice.name }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (isSmartwatch) "Smartwatch BLE" else "Monitor Cardíaco BLE"

        val effectiveIsSmartwatch = isSmartwatch ||
            classifyDeviceName(finalName).second == WearableDeviceCategory.SMARTWATCH_BRIDGE

        if (!effectiveIsSmartwatch) {
            _savedBleDeviceAddress.value = targetMac
            prefs.edit()
                .putString(KEY_SAVED_BLE_DEVICE_ADDR, targetMac)
                .putString(KEY_SAVED_BLE_DEVICE_NAME, finalName)
                .apply()
        }
        _activeGattTargetAddress.value = targetMac

        connectGattInternal(
            hwDevice = hwDevice,
            deviceName = finalName,
            isSmartwatchOptionalGatt = effectiveIsSmartwatch,
            useAutoConnectFallback = false
        )
    }

    private fun closeCurrentGattSafely() {
        try {
            bluetoothGatt?.disconnect()
        } catch (_: SecurityException) {
        } catch (_: android.os.DeadObjectException) {
        } catch (_: Throwable) {}
        try {
            bluetoothGatt?.close()
        } catch (_: SecurityException) {
        } catch (_: android.os.DeadObjectException) {
        } catch (_: Throwable) {}
        bluetoothGatt = null
    }

    private fun connectGattInternal(
        hwDevice: BluetoothDevice,
        deviceName: String,
        isSmartwatchOptionalGatt: Boolean,
        useAutoConnectFallback: Boolean = false
    ) {
        simulationJob?.cancel()
        simulationJob = null
        connectionTimeoutJob?.cancel()
        closeCurrentGattSafely()

        if (!hasBluetoothConnectPermission()) {
            Log.w(TAG, "Permissão BLUETOOTH_CONNECT ausente ao abrir GATT.")
            _connectionState.value = HrConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _gattStatusMessage.value = "Permissão de conexão Bluetooth (BLUETOOTH_CONNECT) necessária."
            return
        }

        isFallbackAutoConnectAttempt = useAutoConnectFallback
        isCurrentGattTargetSmartwatch = isSmartwatchOptionalGatt
        _isUuid180dClosedOnWatch.value = false
        _connectionState.value = HrConnectionState.CONNECTING
        _connectedDeviceName.value = deviceName
        _activeGattTargetAddress.value = hwDevice.address
        _gattStatusMessage.value = if (useAutoConnectFallback) {
            "Reconectando GATT (fallback autoConnect) a $deviceName..."
        } else {
            "Conectando GATT a $deviceName..."
        }

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hwDevice.connectGatt(
                    context,
                    useAutoConnectFallback,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                hwDevice.connectGatt(context, useAutoConnectFallback, gattCallback)
            }

            connectionTimeoutJob = scope.launch {
                if (!useAutoConnectFallback && !isSmartwatchOptionalGatt) {
                    // Timeout de 6 segundos para cintas BLE com autoConnect = false -> 1 tentativa de fallback autoConnect = true
                    delay(6_000L)
                    if (_connectionState.value == HrConnectionState.CONNECTING) {
                        Log.w(TAG, "Timeout de 6s em connectGatt(autoConnect=false) para $deviceName; acionando fallback autoConnect=true")
                        closeCurrentGattSafely()
                        connectGattInternal(
                            hwDevice = hwDevice,
                            deviceName = deviceName,
                            isSmartwatchOptionalGatt = false,
                            useAutoConnectFallback = true
                        )
                    }
                } else {
                    delay(if (isSmartwatchOptionalGatt) 6_000L else 8_000L)
                    if (_connectionState.value == HrConnectionState.CONNECTING) {
                        Log.w(TAG, "Timeout GATT para $deviceName (smartwatch=$isSmartwatchOptionalGatt); encerrando tentativa sem loop.")
                        closeCurrentGattSafely()
                        _connectionState.value = HrConnectionState.DISCONNECTED
                        _connectedDeviceName.value = null
                        if (isSmartwatchOptionalGatt) {
                            hasAttemptedAutoWatchGatt = true
                            _isUuid180dClosedOnWatch.value = true
                            _gattStatusMessage.value = "$deviceName mantido via Health Connect (sem transmissão BLE 0x180D ativa)"
                        } else {
                            _gattStatusMessage.value = "Sem resposta GATT de $deviceName. Verifique se o sensor está ativo no pulso/peito."
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permissão Bluetooth ausente ao conectar GATT: ${e.message}")
            closeCurrentGattSafely()
            _connectionState.value = HrConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _gattStatusMessage.value = "Permissão de Bluetooth necessária para abrir canal GATT."
        } catch (e: android.os.DeadObjectException) {
            Log.w(TAG, "DeadObjectException no Bluetooth ao conectar GATT: ${e.message}")
            closeCurrentGattSafely()
            _connectionState.value = HrConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _gattStatusMessage.value = "Bluetooth do sistema foi reiniciado ou desligado."
        } catch (e: Throwable) {
            Log.w(TAG, "Aviso ao conectar GATT: ${e.message}")
            closeCurrentGattSafely()
            _connectionState.value = HrConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _gattStatusMessage.value = "Falha ao abrir canal GATT: ${e.message}"
        }
    }

    fun startSimulation() {
        if (!com.example.BuildConfig.DEBUG) return
        stopScan()
        disconnect()
        _connectedDeviceName.value = "GEHOD H500 (Simulado)"
        _connectionState.value = HrConnectionState.CONNECTED
        _isUuid180dClosedOnWatch.value = false
        _gattStatusMessage.value = "Sinal Ativo: 142 bpm (Modo Teste)"
        simulationJob = scope.launch {
            var baseBpm = 142
            while (_connectionState.value == HrConnectionState.CONNECTED) {
                baseBpm = (baseBpm + Random.nextInt(-2, 3)).coerceIn(118, 178)
                onBpmReceived(baseBpm)
                delay(1000L)
            }
        }
    }

    private fun onBpmReceived(bpm: Int) {
        if (bpm in 30..240) {
            if (_connectionState.value != HrConnectionState.CONNECTED) {
                _connectionState.value = HrConnectionState.CONNECTED
            }
            _isUuid180dClosedOnWatch.value = false
            _currentBpm.value = bpm
            _gattStatusMessage.value = "Sinal Ativo: $bpm bpm"
            if (isRecordingSession) {
                recordedBpmSamples.add(bpm)
                val now = System.currentTimeMillis()
                if (now - lastTimelineRecordMs >= 10_000L) {
                    lastTimelineRecordMs = now
                    timelineSamples.add(bpm)
                }
            }
        }
    }

    private fun parseAndDispatchHeartRateData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        val flag = data[0].toInt()
        val isUint16 = (flag and 0x01) != 0
        val bpm = if (isUint16 && data.size >= 3) {
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else if (data.size >= 2) {
            data[1].toInt() and 0xFF
        } else {
            0
        }
        if (bpm in 30..240) {
            val now = System.currentTimeMillis()
            lastDirectGattBpmMs = now
            lastRealBleHrTimestamp = now
            _isUsingSmartwatchBridgeBpm.value = false
            startSmartwatchBridgeLiveFeedIfNeeded()
            onBpmReceived(bpm)
        }
    }

    fun disconnect() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
        simulationJob?.cancel()
        simulationJob = null
        lastRealBleHrTimestamp = 0L
        _isUuid180dClosedOnWatch.value = false
        _gattStatusMessage.value = null
        closeCurrentGattSafely()
        _connectionState.value = HrConnectionState.DISCONNECTED
        _currentBpm.value = 0
        _connectedDeviceName.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED && gatt != null) {
                    connectionTimeoutJob?.cancel()
                    connectionTimeoutJob = null
                    val devName = _connectedDeviceName.value
                        ?: runCatching { gatt.device?.name }.getOrNull()
                        ?: "Sensor BLE"
                    _connectedDeviceName.value = devName
                    _connectionState.value = HrConnectionState.CONNECTED
                    _gattStatusMessage.value = "Conectado GATT • Lendo serviço 0x180D..."
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException em discoverServices: ${e.message}")
                    } catch (e: android.os.DeadObjectException) {
                        Log.w(TAG, "DeadObjectException em discoverServices: ${e.message}")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Erro em discoverServices: ${e.message}")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectionTimeoutJob?.cancel()
                    connectionTimeoutJob = null
                    val hwDev = runCatching { gatt?.device }.getOrNull()
                    val currentName = _connectedDeviceName.value
                        ?: runCatching { hwDev?.name }.getOrNull()
                        ?: "Sensor BLE"
                    try {
                        gatt?.disconnect()
                    } catch (_: Throwable) {}
                    try {
                        gatt?.close()
                    } catch (_: Throwable) {}
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }

                    val isWatch = isCurrentGattTargetSmartwatch ||
                        classifyDeviceName(currentName).second == WearableDeviceCategory.SMARTWATCH_BRIDGE

                    // Em smartwatches (ex: Redmi Watch 5 Lite), nunca entra em loop de reconexão GATT em caso de erro:
                    // encerra a tentativa GATT e mantém o vínculo via Health Connect.
                    if (isWatch) {
                        hasAttemptedAutoWatchGatt = true
                        _isUuid180dClosedOnWatch.value = true
                        lastRealBleHrTimestamp = 0L
                        _connectionState.value = HrConnectionState.DISCONNECTED
                        _currentBpm.value = 0
                        _connectedDeviceName.value = null
                        _gattStatusMessage.value =
                            "$currentName mantido via Health Connect (canal GATT 0x180D indisponível)"
                        return
                    }

                    // Para cintas peitorais BLE: se falhou na 1ª tentativa (autoConnect = false, ex: GATT_ERROR 133),
                    // aciona 1 fallback automático com autoConnect = true.
                    if (!isFallbackAutoConnectAttempt && status != BluetoothGatt.GATT_SUCCESS && hwDev != null) {
                        scope.launch {
                            delay(300L)
                            connectGattInternal(
                                hwDevice = hwDev,
                                deviceName = currentName,
                                isSmartwatchOptionalGatt = false,
                                useAutoConnectFallback = true
                            )
                        }
                        return
                    }

                    lastRealBleHrTimestamp = 0L
                    _connectionState.value = HrConnectionState.DISCONNECTED
                    _currentBpm.value = 0
                    _connectedDeviceName.value = null
                    if (!_isUuid180dClosedOnWatch.value) {
                        _gattStatusMessage.value = "Desconectado de $currentName (toque em Conectar Agora para reconectar)"
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Exceção capturada em onConnectionStateChange: ${e.message}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = runCatching { gatt.getService(HEART_RATE_SERVICE_UUID) }.getOrNull()
                    val characteristic = runCatching {
                        service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                            ?: service?.characteristics?.firstOrNull()
                    }.getOrNull()
                    if (characteristic != null) {
                        _isUuid180dClosedOnWatch.value = false
                        _gattStatusMessage.value = "Serviço 0x180D ativo • Assinando notificações (0x2902)..."
                        try {
                            gatt.setCharacteristicNotification(characteristic, true)
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException em setCharacteristicNotification: ${e.message}")
                        } catch (e: android.os.DeadObjectException) {
                            Log.w(TAG, "DeadObjectException em setCharacteristicNotification: ${e.message}")
                        } catch (e: Throwable) {
                            Log.w(TAG, "Erro em setCharacteristicNotification: ${e.message}")
                        }
                        val descriptor = runCatching {
                            characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                        }.getOrNull()
                        if (descriptor != null) {
                            // Fila GATT estrita (1 operação por vez): dispara APENAS writeDescriptor (0x2902) aqui.
                            // Qualquer readCharacteristic subsequente (ex: bateria 0x2A19) ocorre somente em onDescriptorWrite.
                            val writeInitiated = try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val code = gatt.writeDescriptor(
                                        descriptor,
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    )
                                    code == BluetoothStatusCodes.SUCCESS
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            } catch (e: SecurityException) {
                                Log.w(TAG, "SecurityException em writeDescriptor: ${e.message}")
                                false
                            } catch (e: android.os.DeadObjectException) {
                                Log.w(TAG, "DeadObjectException em writeDescriptor: ${e.message}")
                                false
                            } catch (e: Throwable) {
                                Log.w(TAG, "Erro em writeDescriptor: ${e.message}")
                                false
                            }
                            if (!writeInitiated) {
                                runCatching {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            }
                        }
                    } else {
                        // Dispositivo sem serviço BLE 0x180D (ex: Redmi Watch 5 Lite / protocolos proprietários):
                        // encerra GATT imediatamente sem loop e mantém vínculo via Health Connect.
                        val currentName = _connectedDeviceName.value
                            ?: runCatching { gatt.device?.name }.getOrNull()
                            ?: "Smartwatch"
                        Log.w(TAG, "$currentName conectado via GATT, porém serviço 0x180D não encontrado. Encerrando GATT.")
                        hasAttemptedAutoWatchGatt = true
                        _isUuid180dClosedOnWatch.value = true
                        _gattStatusMessage.value = if (isCurrentGattTargetSmartwatch) {
                            "$currentName mantido via Health Connect (modelo sem serviço BLE 0x180D)"
                        } else {
                            "Serviço cardíaco 0x180D não encontrado em $currentName."
                        }
                        try {
                            gatt.disconnect()
                        } catch (_: Throwable) {}
                        try {
                            gatt.close()
                        } catch (_: Throwable) {}
                        if (bluetoothGatt === gatt) bluetoothGatt = null
                        _connectionState.value = HrConnectionState.DISCONNECTED
                        _connectedDeviceName.value = null
                    }
                } else {
                    if (isCurrentGattTargetSmartwatch) {
                        val currentName = _connectedDeviceName.value
                            ?: runCatching { gatt?.device?.name }.getOrNull()
                            ?: "Smartwatch"
                        hasAttemptedAutoWatchGatt = true
                        _isUuid180dClosedOnWatch.value = true
                        try { gatt?.disconnect() } catch (_: Throwable) {}
                        try { gatt?.close() } catch (_: Throwable) {}
                        if (bluetoothGatt === gatt) bluetoothGatt = null
                        _connectionState.value = HrConnectionState.DISCONNECTED
                        _connectedDeviceName.value = null
                        _gattStatusMessage.value = "$currentName mantido via Health Connect (GATT indisponível)"
                    } else {
                        _gattStatusMessage.value = "Falha ao descobrir serviços GATT (status $status)"
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Exceção capturada em onServicesDiscovered: ${e.message}")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            try {
                if (gatt != null && status == BluetoothGatt.GATT_SUCCESS) {
                    val characteristic = descriptor?.characteristic
                    if (characteristic != null && characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                        _gattStatusMessage.value = "Notificações 0x2902 ativas • Aguardando batimentos..."
                        // Próxima operação GATT somente após conclusão do writeDescriptor (1 operação por vez):
                        val batteryChar = runCatching {
                            gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                        }.getOrNull()
                        if (batteryChar != null &&
                            (batteryChar.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                        ) {
                            runCatching { gatt.readCharacteristic(batteryChar) }
                        } else if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            runCatching { gatt.readCharacteristic(characteristic) }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Exceção capturada em onDescriptorWrite: ${e.message}")
            }
        }

        // Android 13+ (API 33 / TIRAMISU+): Callback moderno obrigatório quando targetSdk >= 33
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                parseAndDispatchHeartRateData(value)
            }
        }

        // Android 12 e anteriores (Fallback legado)
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                @Suppress("DEPRECATION")
                parseAndDispatchHeartRateData(characteristic.value)
            }
        }

        // Android 13+ (API 33 / TIRAMISU+): Leitura direta inicial após habilitar 0x2902
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                parseAndDispatchHeartRateData(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                @Suppress("DEPRECATION")
                parseAndDispatchHeartRateData(characteristic.value)
            }
        }
    }
}

