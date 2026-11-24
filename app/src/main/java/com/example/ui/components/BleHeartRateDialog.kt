package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import com.example.services.HeartRateBleManager
import com.example.services.HrConnectionState
import com.example.services.WearableDeviceCategory
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleHeartRatePairingBottomSheet(
    bleManager: HeartRateBleManager,
    onDismiss: () -> Unit
) {
    val ptBr = remember { Locale("pt", "BR") }
    val hrConnectionState by bleManager.connectionState.collectAsState()
    val currentBpm by bleManager.currentBpm.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val isScanningBle by bleManager.isScanning.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()

    val isSmartwatchBridgeActive by bleManager.isSmartwatchBridgeActive.collectAsState()
    val linkedSmartwatchName by bleManager.linkedSmartwatchName.collectAsState()
    val linkedSmartwatchAddress by bleManager.linkedSmartwatchAddress.collectAsState()
    val dailyMetrics by bleManager.smartwatchDailyMetrics.collectAsState()
    val gattStatusMessage by bleManager.gattStatusMessage.collectAsState()
    val activeGattTargetAddress by bleManager.activeGattTargetAddress.collectAsState()
    val isUuid180dClosedOnWatch by bleManager.isUuid180dClosedOnWatch.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        bleManager.syncSmartwatchHealthConnectMetrics()
    }

    // Ao abrir o modal, consulta imediatamente bluetoothAdapter.bondedDevices e GATT conectados
    LaunchedEffect(Unit) {
        bleManager.refreshBondedAndSystemConnectedDevices()
    }

    ModalBottomSheet(
        onDismissRequest = {
            bleManager.stopScan()
            onDismiss()
        },
        containerColor = Color(0xFF131720),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF64748B)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sensores BLE & Smartwatch",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Modo Duplo: Cinta Peitoral Ao Vivo (0x180D) + Ponte Mi Fitness / Health Connect",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            // =========================================================================
            // 1. CARD DE TELEMETRIA AO VIVO (CINTA PEITORAL BLE OU SMARTWATCH GATT 0x180D)
            // =========================================================================
            if (hrConnectionState == HrConnectionState.CONNECTED) {
                Surface(
                    color = Color(0xFF1A202C),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF4D4D),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "SENSOR TITULAR DE TEMPO REAL (GATT 0x180D)",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (currentBpm > 0) "$currentBpm BPM" else "-- BPM",
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (currentBpm > 0) {
                                "Sinal Ativo: $currentBpm bpm (${connectedDeviceName ?: "Sensor BLE"})"
                            } else {
                                gattStatusMessage ?: "Conectado a ${connectedDeviceName ?: "Sensor BLE"} • Aguardando pulso..."
                            },
                            color = Color(0xFF00E676),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { bleManager.disconnect() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DESCONECTAR CANAL GATT", fontWeight = FontWeight.Bold, color = Color(0xFFFF5252), fontSize = 11.sp)
                        }
                    }
                }
            } else if (hrConnectionState == HrConnectionState.CONNECTING) {
                Surface(
                    color = Color(0xFF1A202C),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = gattStatusMessage ?: "Conectando GATT a ${connectedDeviceName ?: "Sensor"}...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // =========================================================================
            // 2. PONTE SMARTWATCH (REDMI WATCH 5 / HAYLOU • MI FITNESS / HEALTH CONNECT)
            // =========================================================================
            Surface(
                color = Color(0xFF181E29),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSmartwatchBridgeActive) Color(0xFF00E676).copy(alpha = 0.55f) else Color(0xFF00B0FF).copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smartwatch_bridge_status_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSmartwatchBridgeActive) Color(0xFF00E676).copy(alpha = 0.16f)
                                        else Color(0xFF00B0FF).copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = if (isSmartwatchBridgeActive) Color(0xFF00E676) else Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isSmartwatchBridgeActive) {
                                        "${linkedSmartwatchName ?: "Redmi Watch 5"} • Vinculado"
                                    } else {
                                        "Smartwatch • Ponte Mi Fitness / Health Connect"
                                    },
                                    color = if (isSmartwatchBridgeActive) Color(0xFF00E676) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = when {
                                        currentBpm > 0 -> "Sinal Ativo: $currentBpm bpm (GATT 0x180D)"
                                        hrConnectionState == HrConnectionState.CONNECTING -> gattStatusMessage ?: "Conectando GATT..."
                                        !gattStatusMessage.isNullOrBlank() -> gattStatusMessage!!
                                        isSmartwatchBridgeActive -> "Aguardando transmissão 180D do relógio"
                                        else -> "Conexão direta por MAC Address sem depender de scanner BLE"
                                    },
                                    color = when {
                                        currentBpm > 0 -> Color(0xFF00E676)
                                        isUuid180dClosedOnWatch -> Color(0xFFFFD600)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = if (currentBpm > 0 || isUuid180dClosedOnWatch) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        if (isSmartwatchBridgeActive) {
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "ATIVA",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    if (isSmartwatchBridgeActive) {
                        // Grade 2x2 de Telemetria Diária Sincronizada (Passos, FC Repouso, Sono, Calorias Basais)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF10141D), RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PASSOS HOJE", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (dailyMetrics.steps > 0) String.format(ptBr, "%,d", dailyMetrics.steps) else "--",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FC MÉDIA", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (dailyMetrics.averageHeartRate > 0) "${dailyMetrics.averageHeartRate} bpm" else "--",
                                    color = Color(0xFF00E676),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SONO (24H)", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (dailyMetrics.sleepingHours > 0f) String.format(ptBr, "%.1f h", dailyMetrics.sleepingHours) else "--",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("CAL. ATIVAS", color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (dailyMetrics.activeCalories > 0) "${dailyMetrics.activeCalories} kcal" else "--",
                                    color = Color(0xFFFFB300),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Aviso quando o smartwatch está pareado mas sem transmissão BLE 0x180D ao vivo
                        if (currentBpm <= 0) {
                            Surface(
                                color = Color(0xFFFFB300).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isUuid180dClosedOnWatch && !gattStatusMessage.isNullOrBlank()) {
                                        gattStatusMessage!!
                                    } else {
                                        "Dica Redmi/Haylou: Ative 'Compartilhar FC / Transmitir FC' nas configurações do relógio e toque em CONECTAR AGORA abaixo."
                                    },
                                    color = Color(0xFFFFE082),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                bleManager.connectDirectByMac(
                                    macAddress = linkedSmartwatchAddress ?: "",
                                    deviceName = linkedSmartwatchName ?: "Redmi Watch 5",
                                    isSmartwatch = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_connect_linked_watch_now")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    hrConnectionState == HrConnectionState.CONNECTING -> "CONECTANDO GATT DIRETO..."
                                    currentBpm > 0 -> "SINAL ATIVO ($currentBpm BPM) • RECONECTAR GATT"
                                    else -> "CONECTAR AGORA (GATT DIRETO)"
                                },
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        runCatching {
                                            if (!bleManager.isHealthConnectAvailable()) {
                                                bleManager.syncSmartwatchHealthConnectMetrics()
                                                return@launch
                                            }
                                            if (bleManager.hasHealthConnectPermissions()) {
                                                bleManager.syncSmartwatchHealthConnectMetrics()
                                            } else {
                                                hcPermissionLauncher.launch(bleManager.getHealthConnectRequiredPermissions())
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("SINCRONIZAR HC", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { bleManager.unlinkSmartwatchBridge() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF64748B).copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("DESVINCULAR", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                bleManager.linkSmartwatchBridge(
                                    deviceName = "Redmi Watch 5",
                                    address = "MI-FITNESS-HC",
                                    tryGattHr = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_link_redmi_watch_5")
                        ) {
                            Icon(Icons.Default.Watch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "VINCULAR E CONECTAR SMARTWATCH PAREADO",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 3. BUSCA DE DISPOSITIVOS (PAREADOS NO SISTEMA + SCANNER BLE ABERTO)
            // =========================================================================
            if (isScanningBle) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF1E293B)
                )
            }

            Button(
                onClick = { bleManager.startScan() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BluetoothSearching, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isScanningBle) "BUSCANDO CINTAS E SMARTWATCHES..." else "BUSCAR SENSORES BLE & PAREADOS",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            if (discoveredDevices.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DISPOSITIVOS DETECTADOS (${discoveredDevices.size})",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    discoveredDevices.forEach { device ->
                        val isWatch = device.category == WearableDeviceCategory.SMARTWATCH_BRIDGE
                        val isAlreadyLinkedWatch = isWatch && isSmartwatchBridgeActive && (
                            linkedSmartwatchName.equals(device.name, ignoreCase = true) ||
                            linkedSmartwatchAddress.equals(device.address, ignoreCase = true)
                        )
                        val isTargetDevice = activeGattTargetAddress?.equals(device.address, ignoreCase = true) == true ||
                            connectedDeviceName?.equals(device.name, ignoreCase = true) == true
                        val isThisConnecting = hrConnectionState == HrConnectionState.CONNECTING && isTargetDevice
                        val isThisConnected = hrConnectionState == HrConnectionState.CONNECTED && isTargetDevice

                        val liveDeviceStatusText = when {
                            isThisConnected && currentBpm > 0 -> "Sinal Ativo: $currentBpm bpm"
                            isThisConnecting -> gattStatusMessage ?: "Conectando GATT..."
                            isTargetDevice && isUuid180dClosedOnWatch ->
                                gattStatusMessage ?: "Conectado via Bluetooth, aguardando você ativar 'Compartilhar FC' no relógio (UUID 180D fechado)"
                            isTargetDevice && !gattStatusMessage.isNullOrBlank() -> gattStatusMessage!!
                            isAlreadyLinkedWatch && currentBpm > 0 -> "Sinal Ativo: $currentBpm bpm"
                            isAlreadyLinkedWatch -> "Aguardando transmissão 180D do relógio"
                            device.isBondedOrSystemConnected -> "${device.address} • Pareado/Conectado no Sistema Android"
                            else -> "${device.address} • Sinal BLE (${device.rssi} dBm)"
                        }

                        val liveDeviceStatusColor = when {
                            (isThisConnected || isAlreadyLinkedWatch) && currentBpm > 0 -> Color(0xFF00E676)
                            isThisConnecting -> Color(0xFF00E5FF)
                            isTargetDevice && isUuid180dClosedOnWatch -> Color(0xFFFFD600)
                            else -> Color(0xFF94A3B8)
                        }

                        Surface(
                            onClick = { bleManager.connectToDevice(device) },
                            color = Color(0xFF1B202C),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isThisConnected && currentBpm > 0 -> Color(0xFF00E676)
                                    isThisConnecting -> Color(0xFF00E5FF)
                                    isAlreadyLinkedWatch -> Color(0xFF00E676).copy(alpha = 0.5f)
                                    isWatch -> Color(0xFF00E5FF).copy(alpha = 0.35f)
                                    else -> Color.White.copy(alpha = 0.10f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Pílulas SMARTWATCH / CINTA BLE + VINCULADO em linha horizontal limpa acima do título
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = if (isWatch) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color(0xFF00E676).copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isWatch) "SMARTWATCH" else "CINTA BLE",
                                                    color = if (isWatch) Color(0xFF00E5FF) else Color(0xFF00E676),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    softWrap = false,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (isAlreadyLinkedWatch) {
                                                Surface(
                                                    color = Color(0xFF00E676).copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "VINCULADO",
                                                        color = Color(0xFF00E676),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        softWrap = false,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = device.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = liveDeviceStatusText,
                                            color = liveDeviceStatusColor,
                                            fontSize = 11.sp,
                                            fontWeight = if ((isThisConnected && currentBpm > 0) || isThisConnecting || (isTargetDevice && isUuid180dClosedOnWatch)) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        onClick = { bleManager.connectToDevice(device) },
                                        color = when {
                                            isThisConnected && currentBpm > 0 -> Color(0xFF00E676).copy(alpha = 0.22f)
                                            isThisConnecting -> Color(0xFF00E5FF).copy(alpha = 0.22f)
                                            else -> Color(0xFF00B0FF).copy(alpha = 0.22f)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isThisConnected && currentBpm > 0 -> Color(0xFF00E676)
                                                else -> Color(0xFF00E5FF).copy(alpha = 0.65f)
                                            }
                                        )
                                    ) {
                                        Text(
                                            text = when {
                                                isThisConnecting -> "CONECTANDO..."
                                                isThisConnected && currentBpm > 0 -> "$currentBpm BPM"
                                                isAlreadyLinkedWatch || device.isBondedOrSystemConnected -> "CONECTAR AGORA"
                                                else -> "CONECTAR"
                                            },
                                            color = if (isThisConnected && currentBpm > 0) Color(0xFF00E676) else Color(0xFF00E5FF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!isScanningBle) {
                Text(
                    text = "Toque em Buscar para listar cintas BLE próximas e smartwatches pareados no Android.",
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (com.example.BuildConfig.DEBUG) {
                TextButton(
                    onClick = {
                        bleManager.startSimulation()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Simular Cinta GEHOD H500 (Modo Teste)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                }
            }

            Text(
                text = com.example.utils.AppBuildInfo.compactSignature,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

