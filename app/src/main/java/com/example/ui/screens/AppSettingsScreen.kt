package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.example.data.model.UserProfileData
import com.example.services.HeartRateBleManager
import com.example.services.HrConnectionState
import com.example.services.RunningDailyReminderManager
import com.example.ui.components.BleHeartRatePairingBottomSheet
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AccessControl
import com.example.utils.AppBackupManager
import com.example.utils.AppBuildInfo
import com.example.utils.AudioSynth
import com.example.utils.LongScreenshotHelper
import com.example.utils.SessionDiagnosticLogger
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Tela Dedicada de Configurações do Sistema (Acessada pelo ícone de engrenagem no cabeçalho do Perfil).
 * Reúne exclusivamente as configurações técnicas do sistema:
 * 1. Ajustes de Áudio, Voz e Volume do Coach
 * 2. Pareamento de Sensores / Smartwatch (BLE)
 * 3. Conta e Logout
 * 4. Ferramentas do Desenvolvedor (incluindo troca de perfil de teste Esnaider / Alyne)
 * 5. Área de Risco (Limpar Histórico / Reset)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUpgradeModal by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showSecondClearConfirmDialog by remember { mutableStateOf(false) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var showHrPairingSheet by remember { mutableStateOf(false) }
    var isDevAdvancedExpanded by remember { mutableStateOf(false) }

    val profile by viewModel.userProfile.collectAsState()
    val coachVoiceEnabled by viewModel.coachVoiceEnabled.collectAsState()
    val restSoundEnabledVal by viewModel.restSoundEnabled.collectAsState()
    val restSoundOptionVal by viewModel.restSoundOption.collectAsState()
    val alertVolumeVal by viewModel.alertVolume.collectAsState()
    val userSession by viewModel.currentUser.collectAsState()

    val bleManager = remember { HeartRateBleManager.getInstance(context) }
    val hrConnectionState by bleManager.connectionState.collectAsState()
    val currentBpm by bleManager.currentBpm.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()
    val isSmartwatchBridgeActive by bleManager.isSmartwatchBridgeActive.collectAsState()
    val linkedSmartwatchName by bleManager.linkedSmartwatchName.collectAsState()
    val smartwatchDailyMetrics by bleManager.smartwatchDailyMetrics.collectAsState()
    val hcCoroutineScope = rememberCoroutineScope()
    var hcStatusNotice by remember { mutableStateOf<String?>(null) }
    var isBackupBusy by remember { mutableStateOf(false) }
    var backupStatusNotice by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBackupBusy = true
        backupStatusNotice = "Exportando backup..."
        backupStatusIsError = false
        hcCoroutineScope.launch {
            val result = AppBackupManager.exportBackupToUri(context, uri)
            isBackupBusy = false
            result.onSuccess { summary ->
                val msg = "Backup exportado: ${summary.toHumanReadableSummary()}"
                backupStatusNotice = msg
                backupStatusIsError = false
                Toast.makeText(context, "Backup (.json) exportado com sucesso!", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                val msg = "Falha ao exportar backup: ${err.message ?: "erro desconhecido"}"
                backupStatusNotice = msg
                backupStatusIsError = true
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBackupBusy = true
        backupStatusNotice = "Validando e importando backup..."
        backupStatusIsError = false
        hcCoroutineScope.launch {
            val result = AppBackupManager.importBackupFromUri(
                context = context,
                uri = uri,
                onUpcomingRacesRestored = { viewModel.reloadUpcomingRacesFromPrefs() }
            )
            isBackupBusy = false
            result.onSuccess { summary ->
                val msg = "Backup restaurado: ${summary.toHumanReadableSummary()}"
                backupStatusNotice = msg
                backupStatusIsError = false
                Toast.makeText(context, "Backup restaurado com sucesso!", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                val msg = "Falha ao importar backup: ${err.message ?: "arquivo inválido"}"
                backupStatusNotice = msg
                backupStatusIsError = true
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        bleManager.syncSmartwatchHealthConnectMetrics()
        hcStatusNotice = "Sincronização com o Health Connect atualizada."
    }

    LaunchedEffect(Unit) {
        bleManager.refreshBondedAndSystemConnectedDevices()
    }

    var dailyReminderEnabled by remember {
        mutableStateOf(RunningDailyReminderManager.isReminderEnabled(context))
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            showHrPairingSheet = true
            bleManager.startScan()
        } else {
            Toast.makeText(
                context,
                "Permissões de Bluetooth necessárias para parear sensores e smartwatch",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestBlePermissionsAndOpenSheet() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val needsRequest = perms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            blePermissionLauncher.launch(perms.toTypedArray())
        } else {
            showHrPairingSheet = true
            bleManager.startScan()
        }
    }

    BackHandler {
        viewModel.activeScreen.value = AppScreen.PROFILE
    }

    if (showUpgradeModal) {
        UpgradePlanScreen(
            viewModel = viewModel,
            onBack = { showUpgradeModal = false }
        )
        return
    }

    val bgDark = Color(0xFF0D0F14)
    val cardBg = Color(0xFF161922)
    val cardBorder = Color(0xFF262B38)
    val primaryText = Color(0xFFF8FAFC)
    val secondaryText = Color(0xFF94A3B8)
    val brandAccent = Color(0xFF0EA5E9)

    Scaffold(
        topBar = {
            Surface(
                color = bgDark,
                border = BorderStroke(0.5.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            viewModel.activeScreen.value = AppScreen.PROFILE
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar ao Perfil",
                            tint = primaryText
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Configurações do Sistema",
                            color = primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            text = "Áudio, Sensores, Conta e Ferramentas",
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        containerColor = bgDark,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            // =========================================================================
            // 0. TREINADOR FISIOLOGISTA IA (GEMINI 2.5 FLASH + MODO ECONÔMICO 1X/DIA)
            // =========================================================================
            item {
                var apiKeyInput by remember {
                    mutableStateOf(com.example.services.ai.AiPhysiologistAnalyzer.getSavedUserApiKey(context))
                }
                var isEcoMode by remember {
                    mutableStateOf(com.example.services.ai.AiPhysiologistAnalyzer.isEcoModeEnabled(context))
                }
                var remainingQuota by remember {
                    mutableStateOf(com.example.services.ai.AiPhysiologistAnalyzer.getRemainingCloudQuotaToday(context))
                }
                var usedToday by remember {
                    mutableStateOf(com.example.services.ai.AiPhysiologistAnalyzer.getCloudUsedToday(context))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_ai_physiologist_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.2.dp, brandAccent.copy(alpha = 0.65f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(16.dp)
                                        .background(brandAccent, RoundedCornerShape(2.dp))
                                )
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = brandAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "TREINADOR FISIOLOGISTA IA",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            Surface(
                                color = brandAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (isEcoMode) "Cota hoje: $remainingQuota disp." else "Ilimitado",
                                    color = brandAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Análise pós-treino unificada para MAX RUN e MAX GYM via Gemini 2.5 Flash com motor fisiológico local de fallback instantâneo.",
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Campo para colar/salvar Gemini API Key Gratuita
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = {
                                Text("Gemini API Key (Gratuita • Google AI Studio)", fontSize = 11.sp)
                            },
                            placeholder = {
                                Text("Cole sua chave AIzaSy... aqui", fontSize = 11.sp, color = secondaryText)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = primaryText,
                                unfocusedTextColor = primaryText,
                                focusedBorderColor = brandAccent,
                                unfocusedBorderColor = cardBorder,
                                focusedLabelColor = brandAccent,
                                unfocusedLabelColor = secondaryText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_gemini_api_key")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    com.example.services.ai.AiPhysiologistAnalyzer.setSavedUserApiKey(
                                        context,
                                        apiKeyInput.trim()
                                    )
                                    remainingQuota = com.example.services.ai.AiPhysiologistAnalyzer.getRemainingCloudQuotaToday(context)
                                    AudioSynth.playTickSound()
                                    Toast.makeText(
                                        context,
                                        if (apiKeyInput.isNotBlank()) "Chave Gemini API salva com sucesso!"
                                        else "Chave removida (usando Motor Fisiológico Local).",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("btn_save_gemini_api_key")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Salvar API Key",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    remainingQuota = com.example.services.ai.AiPhysiologistAnalyzer.unlockExtraAnalysisToday(context)
                                    usedToday = com.example.services.ai.AiPhysiologistAnalyzer.getCloudUsedToday(context)
                                    AudioSynth.playTickSound()
                                    Toast.makeText(
                                        context,
                                        "+1 análise extra liberada para hoje! ($remainingQuota disponíveis)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(42.dp)
                                    .testTag("btn_unlock_extra_ai_analysis")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Liberar análise extra hoje",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Switch Modo Econômico (1 análise por dia)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Modo Econômico (1 análise por dia)",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Protege a cota gratuita da API e salva cache permanente por treino (usadas hoje: $usedToday)",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isEcoMode,
                                onCheckedChange = { enabled ->
                                    isEcoMode = enabled
                                    com.example.services.ai.AiPhysiologistAnalyzer.setEcoModeEnabled(context, enabled)
                                    remainingQuota = com.example.services.ai.AiPhysiologistAnalyzer.getRemainingCloudQuotaToday(context)
                                    AudioSynth.playTickSound()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = primaryText,
                                    checkedTrackColor = brandAccent,
                                    uncheckedTrackColor = Color(0xFF242A38)
                                ),
                                modifier = Modifier.testTag("switch_ai_eco_mode")
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 1. AJUSTES DE ÁUDIO, VOZ E VOLUME DO COACH
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_audio_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(brandAccent, RoundedCornerShape(2.dp))
                            )
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ÁUDIO, VOZ E VOLUME DO COACH",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Toggle Voz do Coach na Corrida
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voz do Coach na Corrida (MAX RUN)",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Guia de voz por bloco, pace, contagens regressivas e parciais de km",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = coachVoiceEnabled,
                                onCheckedChange = {
                                    viewModel.setCoachVoiceEnabled(it)
                                    AudioSynth.playTickSound()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = primaryText,
                                    checkedTrackColor = brandAccent,
                                    uncheckedTrackColor = Color(0xFF242A38)
                                ),
                                modifier = Modifier.testTag("switch_coach_voice")
                            )
                        }

                        // Slider Volume do Coach e Alertas
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Volume do Coach e Alertas",
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(alertVolumeVal * 100).toInt()}%",
                                    color = brandAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            Slider(
                                value = alertVolumeVal,
                                onValueChange = { viewModel.setAlertVolume(it) },
                                valueRange = 0.0f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = brandAccent,
                                    activeTrackColor = brandAccent,
                                    inactiveTrackColor = Color(0xFF242A38)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("slider_alert_volume")
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Toggle Alerta Sonoro de Descanso (Musculação)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alerta Sonoro de Descanso / Transição",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Aviso sonoro ao finalizar o tempo de descanso entre séries",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = restSoundEnabledVal,
                                onCheckedChange = {
                                    viewModel.setRestSoundEnabled(it)
                                    AudioSynth.playTickSound()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = primaryText,
                                    checkedTrackColor = brandAccent,
                                    uncheckedTrackColor = Color(0xFF242A38)
                                )
                            )
                        }

                        // Seletor Tipo de Alerta + Botão de Teste da Sequência Completa (3, 2, 1 + Sino Duplo)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Timbre do Alerta (Sino de Boxe Duplo em 0s + TIC em 3, 2, 1)",
                                color = secondaryText,
                                fontSize = 11.sp
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val soundChoices = listOf(
                                    "default" to "Sino Duplo (Padrão)",
                                    "sino" to "Gongo de Ringue",
                                    "bipe" to "Bipe Triplo",
                                    "vibrate" to "Vibração"
                                )
                                soundChoices.forEach { (key, label) ->
                                    val isSelected = restSoundOptionVal == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setRestSoundOption(key) },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = brandAccent,
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFF1E2330),
                                            labelColor = secondaryText
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = if (isSelected) brandAccent else cardBorder,
                                            enabled = true,
                                            selected = isSelected
                                        )
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    com.example.utils.MaxSoundManager.playTestCountdownAndDoubleBellSequence(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("btn_test_countdown_bell_sequence"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.10f),
                                    contentColor = Color(0xFF00E5FF)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "🔔 Testar Sequência (3, 2, 1 + Sino Duplo)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Lembrete Diário de Corrida
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lembrete Diário de Corrida",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Notificação diária caso ainda não tenha registrado corrida hoje",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = dailyReminderEnabled,
                                onCheckedChange = { enabled ->
                                    dailyReminderEnabled = enabled
                                    val (hour, minute) = RunningDailyReminderManager.getReminderTime(context)
                                    RunningDailyReminderManager.setReminderConfig(context, enabled, hour, minute)
                                    AudioSynth.playTickSound()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = primaryText,
                                    checkedTrackColor = brandAccent,
                                    uncheckedTrackColor = Color(0xFF242A38)
                                )
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. PAREAMENTO DE SENSORES E SMARTWATCH (BLE)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sensors_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(brandAccent, RoundedCornerShape(2.dp))
                            )
                            Icon(
                                imageVector = Icons.Default.BluetoothConnected,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SENSORES E SMARTWATCH",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        val isBleConnected = hrConnectionState == HrConnectionState.CONNECTED
                        val sensorSubtitle = when (hrConnectionState) {
                            HrConnectionState.CONNECTED ->
                                "Conectado a ${connectedDeviceName ?: "Sensor BLE"} • ${if (currentBpm > 0) "$currentBpm BPM" else "Aguardando pulso"}"
                            HrConnectionState.CONNECTING ->
                                "Conectando ao sensor ${connectedDeviceName ?: "BLE"}..."
                            else ->
                                "Cinta Peitoral BLE (GEHOD H500, XOSS, Polar) + Smartwatch"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cinta Cardíaca BLE (Tempo Real 0x180D)",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = sensorSubtitle,
                                    color = if (isBleConnected) Color(0xFF10B981) else secondaryText,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    requestBlePermissionsAndOpenSheet()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isBleConnected) Color(0xFF10B981) else brandAccent),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_settings_pair_ble")
                            ) {
                                Text(
                                    text = if (isBleConnected) "GERENCIAR" else "PAREAR SENSOR",
                                    color = if (isBleConnected) Color(0xFF10B981) else brandAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Card Modo Duplo: Redmi Watch 5 • Vinculado (Ponte Mi Fitness / Health Connect Ativa)
                        Surface(
                            color = Color(0xFF141923),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSmartwatchBridgeActive) Color(0xFF10B981).copy(alpha = 0.55f) else Color(0xFF242A38)
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isSmartwatchBridgeActive) {
                                                "${linkedSmartwatchName ?: "Redmi Watch 5"} • Vinculado (Ponte Mi Fitness / Health Connect Ativa)"
                                            } else {
                                                "Redmi Watch 5 • Ponte Mi Fitness / Health Connect"
                                            },
                                            color = if (isSmartwatchBridgeActive) Color(0xFF10B981) else primaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isSmartwatchBridgeActive) {
                                                "Sincronização diária ativa • Coexiste sem conflito com a Cinta GEHOD H500"
                                            } else {
                                                "Vincule seu Redmi Watch 5 conectado ao Mi Fitness / Health Connect"
                                            },
                                            color = secondaryText,
                                            fontSize = 10.5.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isSmartwatchBridgeActive) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFF10B981))
                                        ) {
                                            Text(
                                                text = "PONTE ATIVA",
                                                color = Color(0xFF10B981),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                bleManager.linkSmartwatchBridge("Redmi Watch 5", "MI-FITNESS-HC")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, brandAccent),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "VINCULAR",
                                                color = brandAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (isSmartwatchBridgeActive) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0E121A), RoundedCornerShape(8.dp))
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Passos Diários", color = secondaryText, fontSize = 9.5.sp)
                                            Text(
                                                text = if (smartwatchDailyMetrics.steps > 0) "${smartwatchDailyMetrics.steps}" else "--",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("FC Média", color = secondaryText, fontSize = 9.5.sp)
                                            Text(
                                                text = if (smartwatchDailyMetrics.averageHeartRate > 0) "${smartwatchDailyMetrics.averageHeartRate} bpm" else "--",
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Sono", color = secondaryText, fontSize = 9.5.sp)
                                            Text(
                                                text = if (smartwatchDailyMetrics.sleepingHours > 0f) String.format(Locale("pt", "BR"), "%.1fh", smartwatchDailyMetrics.sleepingHours) else "--",
                                                color = primaryText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Calorias Ativas", color = secondaryText, fontSize = 9.5.sp)
                                            Text(
                                                text = if (smartwatchDailyMetrics.activeCalories > 0) "${smartwatchDailyMetrics.activeCalories} kcal" else "--",
                                                color = Color(0xFFFFB300),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        hcCoroutineScope.launch {
                                            runCatching {
                                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                                    hcStatusNotice = "Health Connect indisponível neste Android (requer Android 8.0+)."
                                                    return@launch
                                                }
                                                if (!bleManager.isHealthConnectAvailable()) {
                                                    val sdkStatus = runCatching {
                                                        HealthConnectClient.getSdkStatus(context)
                                                    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)
                                                    hcStatusNotice = when (sdkStatus) {
                                                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                                            "Health Connect requer instalação/atualização (status SDK: $sdkStatus)."
                                                        HealthConnectClient.SDK_UNAVAILABLE ->
                                                            "Health Connect indisponível neste aparelho (status SDK: $sdkStatus)."
                                                        else ->
                                                            "Health Connect indisponível (status SDK: $sdkStatus)."
                                                    }
                                                    return@launch
                                                }
                                                if (bleManager.hasHealthConnectPermissions()) {
                                                    hcStatusNotice = "Sincronizando dados reais do Health Connect..."
                                                    bleManager.syncSmartwatchHealthConnectMetrics()
                                                } else {
                                                    hcStatusNotice = "Solicitando permissões oficiais do Health Connect..."
                                                    hcPermissionLauncher.launch(bleManager.getHealthConnectRequiredPermissions())
                                                }
                                            }.onFailure { e ->
                                                hcStatusNotice = "Falha ao acessar Health Connect: ${e.message ?: "erro desconhecido"}"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_settings_authorize_sync_hc")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Autorizar ou sincronizar Health Connect",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AUTORIZAR / SINCRONIZAR HEALTH CONNECT",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                if (!hcStatusNotice.isNullOrBlank()) {
                                    Text(
                                        text = hcStatusNotice!!,
                                        color = Color(0xFF80DEEA),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Status do Sistema
                        Surface(
                            color = Color(0xFF181C26),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF242A38)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Execução em Segundo Plano:", color = secondaryText, fontSize = 11.sp)
                                    Text("ATIVO (GPS Prontidão)", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Watchdog de Audio Ducking (500ms):", color = secondaryText, fontSize = 11.sp)
                                    Text("ATIVO", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. CONTA E LOGOUT
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_account_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(brandAccent, RoundedCornerShape(2.dp))
                            )
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "CONTA E LOGOUT",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nível de Acesso da Conta:", color = secondaryText, fontSize = 12.sp)

                            val accessTier = userSession?.nivelAcesso ?: "INTERMEDIÁRIO"
                            val tierColor = Color(AccessControl.getNivelColorHex(accessTier))

                            Surface(
                                color = tierColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, tierColor),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = accessTier.uppercase(),
                                    color = tierColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                AudioSynth.playTickSound()
                                showUpgradeModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardMembership,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GERENCIAR PLANO / UPGRADE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                com.example.services.UserManager.logout(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_settings_logout")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SAIR DA CONTA / LOGOUT",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 3B. BACKUP E RESTAURAÇÃO DE DADOS (.JSON — DISPONÍVEL PARA TODOS OS USUÁRIOS)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_backup_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(brandAccent, RoundedCornerShape(2.dp))
                            )
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "BACKUP E RESTAURAÇÃO DE DADOS",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        Text(
                            text = "Exporte ou restaure seu perfil, corridas, treinos de musculação, bioimpedâncias e provas futuras em um arquivo .json seguro.",
                            color = secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = {
                                AudioSynth.playTickSound()
                                exportBackupLauncher.launch(AppBackupManager.suggestedBackupFileName())
                            },
                            enabled = !isBackupBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_settings_export_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Exportar Backup em JSON",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exportar Backup (.json)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            enabled = !isBackupBusy,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_settings_import_backup")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Importar Backup em JSON",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Importar Backup (.json)",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        if (!backupStatusNotice.isNullOrBlank()) {
                            Text(
                                text = backupStatusNotice!!,
                                color = if (backupStatusIsError) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("text_settings_backup_status")
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 4. FERRAMENTAS DO DESENVOLVEDOR (Incluindo Troca de Perfil de Teste)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_devtools_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .background(brandAccent, RoundedCornerShape(2.dp))
                            )
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "FERRAMENTAS DO DESENVOLVEDOR",
                                color = primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = (-0.5).sp
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        if (com.example.BuildConfig.DEBUG) {
                            // Troca de Perfil de Teste (Esnaider / Alyne)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Troca de Perfil de Teste (Pré-definidos)",
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Alterne rapidamente os dados fisiológicos de teste sem poluir a tela principal de Perfil:",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val isEsnaider = profile.nome.contains("Esnaider", ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            viewModel.loadPresetProfile(UserProfileData.PRESET_ESNAIDER)
                                            Toast.makeText(context, "Perfil de teste alterado para Esnaider", Toast.LENGTH_SHORT).show()
                                        },
                                        color = if (isEsnaider) brandAccent.copy(alpha = 0.2f) else Color(0xFF181C26),
                                        border = BorderStroke(1.dp, if (isEsnaider) brandAccent else cardBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_preset_esnaider")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Esnaider",
                                                color = if (isEsnaider) brandAccent else primaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "M • 50a • 85kg • 1,87m",
                                                color = secondaryText,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "AVANÇADO",
                                                color = brandAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    val isAlyne = profile.nome.contains("Alyne", ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            viewModel.loadPresetProfile(UserProfileData.PRESET_ALYNE)
                                            Toast.makeText(context, "Perfil de teste alterado para Alyne", Toast.LENGTH_SHORT).show()
                                        },
                                        color = if (isAlyne) brandAccent.copy(alpha = 0.2f) else Color(0xFF181C26),
                                        border = BorderStroke(1.dp, if (isAlyne) brandAccent else cardBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_preset_alyne")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "Alyne",
                                                color = if (isAlyne) brandAccent else primaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "F • 42a • 72kg • 1,55m",
                                                color = secondaryText,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = "AVANÇADO",
                                                color = brandAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = cardBorder, thickness = 1.dp)

                            // Laboratório Beta
                            Surface(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    viewModel.activeScreen.value = AppScreen.BETA_LAB
                                },
                                color = Color(0xFF181C26),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF242A38)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Science, contentDescription = null, tint = brandAccent)
                                        Column {
                                            Text("Laboratório Beta", color = primaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Avalie novas funcionalidades e módulos experimentais", color = secondaryText, fontSize = 11.sp)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = secondaryText)
                                }
                            }
                        }

                        // Logs de Diagnóstico
                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                SessionDiagnosticLogger.copyLogsToClipboard(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("COPIAR LOG DE DIAGNÓSTICO", color = primaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Painel Expansível de Simuladores de Desenvolvimento
                        Surface(
                            onClick = { isDevAdvancedExpanded = !isDevAdvancedExpanded },
                            color = Color(0xFF181C26),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Simuladores e Sementes de Dados (DEV)",
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (isDevAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expandir Ferramentas DEV",
                                    tint = brandAccent
                                )
                            }
                        }

                        if (isDevAdvancedExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val activity = context as? android.app.Activity
                                            ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                        if (activity != null) {
                                            LongScreenshotHelper.captureAndShare(context, activity)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, brandAccent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = brandAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("CAPTURAR TELA COMPLETA", color = brandAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.devSeedExercises() },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Seed Exercícios", color = primaryText, fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.devGenerateHistory(12) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Gerar 12m Hist.", color = primaryText, fontSize = 10.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.devInsertFakeCardio() },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Gerar 5x Cardio", color = primaryText, fontSize = 10.sp)
                                    }
                                    if (com.example.BuildConfig.DEBUG) {
                                        OutlinedButton(
                                            onClick = { viewModel.devSimulateLowRecovery() },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, cardBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Simular Fadiga", color = primaryText, fontSize = 10.sp)
                                        }
                                    }
                                }

                                if (com.example.BuildConfig.DEBUG) {
                                    var devSeedStatusMessage by remember { mutableStateOf<String?>(null) }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.devImportAuthorHistory { inserted ->
                                                val msg = when (inserted) {
                                                    0 -> "Histórico do autor já importado"
                                                    1 -> "1 sessão importada"
                                                    else -> "$inserted sessões importadas"
                                                }
                                                devSeedStatusMessage = msg
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_dev_import_author_history")
                                    ) {
                                        Text(
                                            "Importar histórico do autor (30–31/10)",
                                            color = primaryText,
                                            fontSize = 10.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val added = viewModel.devAddDefaultTafUpcomingEvent()
                                            val msg = if (added) {
                                                "TAF PMAC (22/11) adicionado"
                                            } else {
                                                "TAF PMAC (22/11) já adicionado"
                                            }
                                            devSeedStatusMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("btn_dev_add_taf_pmac")
                                    ) {
                                        Text(
                                            "Adicionar TAF PMAC (22/11)",
                                            color = primaryText,
                                            fontSize = 10.sp
                                        )
                                    }

                                    devSeedStatusMessage?.let { statusMsg ->
                                        Text(
                                            text = statusMsg,
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            modifier = Modifier.testTag("text_dev_seed_status")
                                        )
                                    }
                                }

                                var selectedRoutineType by remember { mutableStateOf("ABCDE") }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    val routineTypes = listOf("ABCDE", "PushPullLegs", "FullBody", "UpperLower")
                                    routineTypes.forEach { type ->
                                        val isSelected = selectedRoutineType == type
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedRoutineType = type },
                                            label = { Text(type, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = brandAccent,
                                                selectedLabelColor = Color.White,
                                                containerColor = Color(0xFF1E2330),
                                                labelColor = secondaryText
                                            )
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.devCreateWorkoutRoutine(selectedRoutineType) },
                                    colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("GERAR PLANILHA ($selectedRoutineType)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 5. ÁREA DE RISCO (Limpar Histórico / Reset)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_danger_zone_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1315)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ÁREA DE RISCO (LIMPAR HISTÓRICO / RESET)",
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        Text(
                            text = "Ações irreversíveis para limpeza do histórico de corridas, musculação ou redefinição completa do ambiente local.",
                            color = secondaryText,
                            fontSize = 11.sp
                        )

                        Button(
                            onClick = {
                                AudioSynth.playTickSound()
                                showClearHistoryDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_clear_all_history")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LIMPAR TODO O HISTÓRICO",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                showFactoryResetDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_factory_reset")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "RESETAR AMBIENTE E DADOS LOCAIS",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Rodapé Técnico
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = AppBuildInfo.APP_NAME,
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Versão ${AppBuildInfo.VERSION_NAME} • Build #${AppBuildInfo.BUILD_NUMBER}",
                        color = secondaryText.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    // BottomSheet de Pareamento BLE (Cinta Cardíaca / Smartwatch)
    if (showHrPairingSheet) {
        BleHeartRatePairingBottomSheet(
            bleManager = bleManager,
            onDismiss = { showHrPairingSheet = false }
        )
    }

    // Modal Primeira Confirmação de Limpeza de Histórico
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text("Limpar Todo o Histórico?", color = primaryText, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Esta ação apagará permanentemente todas as sessões salvas de corrida e musculação. Deseja continuar?",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        showSecondClearConfirmDialog = true
                    }
                ) {
                    Text("CONTINUAR", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // Modal Segunda Confirmação de Limpeza de Histórico
    if (showSecondClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSecondClearConfirmDialog = false },
            title = {
                Text("Confirmação Definitiva", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza absoluta? Os registros não poderão ser recuperados.",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAndClearAllHistory()
                        showSecondClearConfirmDialog = false
                        Toast.makeText(context, "Histórico limpo com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("SIM, APAGAR TUDO", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecondClearConfirmDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // Modal Confirmação de Reset de Fábrica / Ambiente
    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            title = {
                Text("Resetar Ambiente Completo?", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Isso restaurará as planilhas padrão, limpará o histórico de corridas e musculação e redefinirá o estado local.",
                    color = secondaryText,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.devResetEnvironment()
                        viewModel.resetAndClearAllHistory()
                        showFactoryResetDialog = false
                        Toast.makeText(context, "Ambiente resetado para os padrões!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("CONFIRMAR RESET", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }
}
