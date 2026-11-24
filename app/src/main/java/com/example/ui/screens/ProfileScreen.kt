package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.constants.TemplatesAndExercises
import com.example.data.model.Exercise
import com.example.data.model.GymProfile
import com.example.data.model.RunningShoe
import com.example.ui.components.WeeklyGoalSelector
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Aba Perfil do Atleta — Fisiologia e Equipamentos.
 * Mantém exclusivamente:
 * 1. Dados Biométricos unificados (sem duplicação de cards e sem seletor de teste Esnaider/Alyne)
 * 2. Calibração de Corrida (MAX RUN)
 * 3. Garagem de Tênis (com barra de progresso da quilometragem acumulada)
 * 4. Minhas Academias (com gerenciamento de aparelhos)
 *
 * O ícone de engrenagem no topo abre a tela dedicada de Configurações do Sistema (AppSettingsScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val runningShoes by viewModel.runningShoes.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val paceCalibrationMode by viewModel.paceCalibrationMode.collectAsState()
    val lastPaceCalibrationTimestamp by viewModel.lastPaceCalibrationTimestamp.collectAsState()
    val runHudFaceStyle by viewModel.runHudFaceStyle.collectAsState()
    val gymProfiles by viewModel.gymProfiles.collectAsState()
    val customExercises by viewModel.customExercises.collectAsState()
    val forceMessage by viewModel.forceDiagnosticMessage.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddShoeDialog by remember { mutableStateOf(false) }
    var shoeToEditInProfile by remember { mutableStateOf<RunningShoe?>(null) }
    var showAddGymDialog by remember { mutableStateOf(false) }
    var profileToManageAparelhos by remember { mutableStateOf<GymProfile?>(null) }
    var showPaceOnboardingSheet by remember { mutableStateOf(false) }
    var showRunnerAnamnesisWizard by remember { mutableStateOf(false) }
    var anamnesisVersion by remember { mutableIntStateOf(0) }
    val anamnesisProfile = remember(context, anamnesisVersion, profile.baselinePaceMinPerKm) {
        com.example.ui.components.RunnerAnamnesisManager.loadProfile(context, profile.baselinePaceMinPerKm)
    }
    val runnerCalibration = remember(anamnesisProfile, profile.idade, runningSessions, profile.baselinePaceMinPerKm) {
        com.example.ui.components.RunnerAnamnesisManager.calculateCalibration(
            profile = anamnesisProfile,
            athleteAge = profile.idade,
            history = runningSessions,
            baselinePaceMinPerKm = profile.baselinePaceMinPerKm
        )
    }

    val paceAnalysis = remember(
        runningSessions,
        profile.idade,
        profile.baselinePaceMinPerKm,
        lastPaceCalibrationTimestamp,
        paceCalibrationMode
    ) {
        com.example.engine.RunningProgramManager.analyzeRealPaceCalibration(
            history = runningSessions,
            userAge = profile.idade,
            currentBaselinePaceMinPerKm = profile.baselinePaceMinPerKm,
            lastCalibrationTimestamp = lastPaceCalibrationTimestamp,
            calibrationMode = paceCalibrationMode
        )
    }

    val combinedExercises = remember(customExercises) {
        val defaultList = TemplatesAndExercises.ALL_EXERCISES
        val customList = customExercises.map { entity ->
            Exercise(
                id = entity.id,
                name = entity.name,
                category = entity.category,
                description = entity.description,
                isCustom = true
            )
        }
        defaultList + customList
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Perfil do Atleta",
                            color = primaryText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            text = "Fisiologia e Equipamentos",
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                showEditProfileDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_edit_biometrics")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Dados Biométricos",
                                tint = brandAccent
                            )
                        }

                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                viewModel.activeScreen.value = AppScreen.APP_SETTINGS
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_open_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações do Sistema",
                                tint = primaryText
                            )
                        }
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {

            // =========================================================================
            // 1. DADOS BIOMÉTRICOS UNIFICADOS (Card Único — Sem Duplicação)
            // =========================================================================
            item {
                val calculatedImc: Float? = if (profile.peso > 0f && profile.altura > 0) {
                    val heightMeters = profile.altura / 100f
                    profile.peso / (heightMeters * heightMeters)
                } else {
                    null
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_biometrics_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Cabeçalho da Seção + Nome do Atleta + Badge de Nível
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                    Text(
                                        text = "DADOS BIOMÉTRICOS",
                                        color = secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = profile.nome.ifBlank { "Atleta" },
                                    color = primaryText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            Surface(
                                color = brandAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, brandAccent),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = profile.nivelExperiencia.uppercase(),
                                    color = brandAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Grade Unificada de Biometria (Peso, Altura, Idade, IMC, Meta)
                        Surface(
                            color = Color(0xFF181C26),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BiometricStatItem(
                                    label = "PESO",
                                    value = if (profile.peso > 0f) String.format(Locale.US, "%.1f kg", profile.peso) else "--",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(cardBorder)
                                )
                                BiometricStatItem(
                                    label = "ALTURA",
                                    value = if (profile.altura > 0) "${profile.altura} cm" else "--",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(cardBorder)
                                )
                                BiometricStatItem(
                                    label = "IDADE",
                                    value = if (profile.idade > 0) "${profile.idade} anos" else "--",
                                    primaryText = primaryText,
                                    secondaryText = secondaryText
                                )
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(cardBorder)
                                )
                                BiometricStatItem(
                                    label = "IMC",
                                    value = calculatedImc?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                                    primaryText = brandAccent,
                                    secondaryText = secondaryText
                                )
                            }
                        }

                        // Resumo complementar (Meta Semanal e Composição se disponível)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Meta Semanal: ${profile.metaSemanal} dias/sem",
                                color = secondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    showEditProfileDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = brandAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "EDITAR BIOMETRIA",
                                    color = brandAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Seletor Horizontal de Nível de Experiência
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "NÍVEL DE CONDICIONAMENTO",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            val levels = listOf("Iniciante", "Intermediário", "Avançado")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                levels.forEach { lv ->
                                    val isSelected = profile.nivelExperiencia.equals(lv, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            viewModel.updateProfileData(
                                                profile.nome, profile.sexo, profile.idade, profile.altura,
                                                profile.peso, lv, profile.localTreino, profile.metaSemanal, profile.iconStyle
                                            )
                                            AudioSynth.playTickSound()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) brandAccent else Color(0xFF1E2330),
                                        border = BorderStroke(1.dp, if (isSelected) brandAccent else cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = lv.uppercase(),
                                                color = if (isSelected) Color.White else primaryText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Botão Sugerir Nível Recomendado
                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                viewModel.runManualLevelSuggestion()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "SUGERIR NÍVEL RECOMENDADO",
                                color = primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (forceMessage != null) {
                            Surface(
                                color = Color(0xFF1E2330),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(forceMessage ?: "", color = primaryText, fontSize = 12.sp)
                                    TextButton(
                                        onClick = { viewModel.forceDiagnosticMessage.value = null },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("FECHAR", color = brandAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 1.5. MAX RECOMP • BIOIMPEDÂNCIA (Fitdays / RelaxFit Passo a Passo)
            // =========================================================================
            item {
                BodyRecompProfileEntryCard(
                    userProfile = profile,
                    onWeightSyncedToProfile = { updatedWeightKg ->
                        viewModel.updateProfileData(
                            profile.nome,
                            profile.sexo,
                            profile.idade,
                            profile.altura,
                            updatedWeightKg,
                            profile.nivelExperiencia,
                            profile.localTreino,
                            profile.metaSemanal,
                            profile.iconStyle
                        )
                    }
                )
            }

            // =========================================================================
            // 2. CALIBRAÇÃO DE CORRIDA (MAX RUN)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_running_calibration_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
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
                                Text(
                                    text = "CALIBRAÇÃO DE CORRIDA",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                            Text(
                                text = "MAX RUN",
                                color = brandAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val paceFormatted = com.example.engine.RunningProgramManager.formatPaceMinPerKm(
                            profile.baselinePaceMinPerKm
                        )

                        Surface(
                            color = Color(0xFF181C26),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, cardBorder),
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
                                    Column {
                                        Text("Ritmo Base (5 km)", color = secondaryText, fontSize = 11.sp)
                                        Text(
                                            text = paceFormatted,
                                            color = primaryText,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Frequência Cardíaca Calibrada", color = secondaryText, fontSize = 11.sp)
                                        Text(
                                            text = "FC Máx: ${paceAnalysis.effectiveMaxHr} bpm",
                                            color = primaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (paceCalibrationMode == com.example.engine.PaceCalibrationMode.AUTO_14_DAYS)
                                                    Color(0xFF00E5FF)
                                                else Color(0xFF94A3B8),
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = paceAnalysis.cycleStatusLine,
                                        color = if (paceCalibrationMode == com.example.engine.PaceCalibrationMode.AUTO_14_DAYS)
                                            Color(0xFF00E5FF)
                                        else secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.testTag("profile_pace_cycle_status")
                                    )
                                }
                            }
                        }

                        // Sub-card / Banner destacado em Ciano/Verde quando Pace Real difere > 5s/km ou completa ciclo 14d
                        if (paceAnalysis.hasEnoughData &&
                            paceAnalysis.calculatedEffectivePaceMinPerKm != null &&
                            (paceAnalysis.deltaSecPerKm > 5 || paceAnalysis.isCycleComplete)
                        ) {
                            Surface(
                                color = Color(0xFF00E676).copy(alpha = 0.10f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("banner_real_pace_detected")
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
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "⚡ Pace Real Detectado: ${paceAnalysis.formattedCalculatedPace} (baseado nas últimas corridas)",
                                                color = Color(0xFF00E676),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = paceAnalysis.hrAdjustmentSummary,
                                                color = secondaryText,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                viewModel.saveRunnerBaselinePace(paceAnalysis.calculatedEffectivePaceMinPerKm)
                                                Toast.makeText(
                                                    context,
                                                    "Pace atualizado para ${paceAnalysis.formattedCalculatedPace}!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag("btn_quick_update_detected_pace")
                                        ) {
                                            Text(
                                                text = "Atualizar Pace",
                                                color = Color(0xFF0D0F14),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Projeção de Prova (5K / 10K / 21,1K) + VAM / LT2 + Botão de Anamnese de Corrida
                        com.example.ui.components.ProfileRunningPredictorAndZonesSection(
                            anamnesisProfile = anamnesisProfile,
                            calibration = runnerCalibration,
                            onOpenAnamnesisWizard = {
                                showRunnerAnamnesisWizard = true
                            }
                        )

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                showPaceOnboardingSheet = true
                            },
                            border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_calibrate_running_pace")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = brandAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "AJUSTE RÁPIDO DE RITMO (5 KM)",
                                color = primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2.5. ESTILO DO MOSTRADOR DE CORRIDA (HUD WATCHFACES)
            // =========================================================================
            item {
                RunHudWatchfaceSelectorCard(
                    selectedStyle = runHudFaceStyle,
                    onSelectStyle = { newStyle ->
                        viewModel.setRunHudFaceStyle(newStyle)
                    }
                )
            }

            // =========================================================================
            // 3. GARAGEM DE TÊNIS (Com Barra de Progresso da Quilometragem Acumulada)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_shoe_garage_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
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
                                Text(
                                    text = "GARAGEM DE TÊNIS",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    showAddShoeDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, brandAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_add_shoe_profile")
                            ) {
                                Text("+ NOVO TÊNIS", color = brandAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (runningShoes.isEmpty()) {
                            Text(
                                text = "Nenhum tênis cadastrado na Garagem. Adicione seu calçado para monitorar a quilometragem acumulada e vida útil.",
                                color = secondaryText,
                                fontSize = 11.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                runningShoes.forEach { shoe ->
                                    val safeMaxKm = shoe.maxDistanceKm.coerceAtLeast(1f)
                                    val wearRatio = (shoe.totalDistanceKm / safeMaxKm).coerceIn(0f, 1f)
                                    val wearPercent = (wearRatio * 100f).roundToInt()
                                    val barColor = when {
                                        wearRatio >= 0.9f -> Color(0xFFEF4444)
                                        wearRatio >= 0.75f -> Color(0xFFF59E0B)
                                        else -> brandAccent
                                    }

                                    Surface(
                                        color = Color(0xFF181C26),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (shoe.isDefault) brandAccent else cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = shoe.name,
                                                            color = primaryText,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontStyle = FontStyle.Italic
                                                        )
                                                        if (shoe.isDefault) {
                                                            Surface(
                                                                color = brandAccent.copy(alpha = 0.2f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "PADRÃO",
                                                                    color = brandAccent,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = "${shoe.brand} • Inicial: ${String.format(Locale.US, "%.1f km", shoe.initialDistanceKm)}",
                                                        color = secondaryText,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.1f / %.0f km", shoe.totalDistanceKm, shoe.maxDistanceKm),
                                                        color = primaryText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                    Text(
                                                        text = "$wearPercent% da vida útil",
                                                        color = barColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            // Barra de Progresso da Quilometragem Acumulada
                                            LinearProgressIndicator(
                                                progress = { wearRatio },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = barColor,
                                                trackColor = Color(0xFF242A38)
                                            )

                                            // Opções do Tênis
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!shoe.isDefault) {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.setDefaultRunningShoe(shoe.id)
                                                            AudioSynth.playTickSound()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                                    ) {
                                                        Text("Definir Padrão", color = brandAccent, fontSize = 10.sp)
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        shoeToEditInProfile = shoe
                                                        AudioSynth.playTickSound()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Editar Tênis",
                                                        tint = secondaryText,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteRunningShoe(shoe.id)
                                                        AudioSynth.playTickSound()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Excluir Tênis",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. MINHAS ACADEMIAS (Com Gestão de Aparelhos)
            // =========================================================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_gyms_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
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
                                Text(
                                    text = "MINHAS ACADEMIAS",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    showAddGymDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, brandAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("btn_add_gym_profile")
                            ) {
                                Text("+ NOVA", color = brandAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Local Preferencial: ${profile.localTreino}",
                            color = secondaryText,
                            fontSize = 12.sp
                        )

                        if (gymProfiles.isEmpty()) {
                            Text(
                                text = "Nenhuma academia cadastrada. Cadastre sua academia para filtrar aparelhos disponíveis nos seus treinos.",
                                color = secondaryText,
                                fontSize = 11.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                gymProfiles.forEach { gProfile ->
                                    Surface(
                                        color = Color(0xFF181C26),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = gProfile.name,
                                                    color = primaryText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { viewModel.deleteGymProfile(gProfile.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Excluir Academia",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val avail = gProfile.availableExercisesIds.size
                                                Text(
                                                    text = "Aparelhos: $avail cadastrados",
                                                    color = secondaryText,
                                                    fontSize = 11.sp
                                                )

                                                Button(
                                                    onClick = { profileToManageAparelhos = gProfile },
                                                    colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(
                                                        text = "GERENCIAR APARELHOS",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Versão real do aplicativo
            item {
                Text(
                    text = "Versão ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    color = secondaryText.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                        .testTag("profile_app_version_text")
                )
            }
        }
    }

    // Modal Editar Dados Biométricos
    if (showEditProfileDialog) {
        var nameEdit by remember(profile) { mutableStateOf(profile.nome) }
        var ageEdit by remember(profile) { mutableStateOf("${profile.idade}") }
        var heightEdit by remember(profile) { mutableStateOf("${profile.altura}") }
        var weightEdit by remember(profile) { mutableStateOf(String.format(Locale.US, "%.1f", profile.peso)) }
        var goalEdit by remember(profile) { mutableIntStateOf(profile.metaSemanal.coerceIn(1, 7)) }
        var locationEdit by remember(profile) { mutableStateOf(profile.localTreino) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text("Editar Dados Biométricos", color = primaryText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = nameEdit,
                        onValueChange = { nameEdit = it },
                        label = { Text("Nome do Atleta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ageEdit,
                            onValueChange = { ageEdit = it },
                            label = { Text("Idade (Anos)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightEdit,
                            onValueChange = { heightEdit = it },
                            label = { Text("Altura (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = weightEdit,
                        onValueChange = { weightEdit = it },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    WeeklyGoalSelector(
                        selectedDays = goalEdit,
                        onSelectDays = { goalEdit = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = locationEdit,
                        onValueChange = { locationEdit = it },
                        label = { Text("Local Preferencial (Academia, Casa, Parque)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedWeight = weightEdit.replace(",", ".").toFloatOrNull() ?: profile.peso
                        viewModel.updateProfileData(
                            nameEdit.trim().ifBlank { profile.nome },
                            profile.sexo,
                            ageEdit.toIntOrNull() ?: profile.idade,
                            heightEdit.toIntOrNull() ?: profile.altura,
                            parsedWeight,
                            profile.nivelExperiencia,
                            locationEdit.trim().ifBlank { profile.localTreino },
                            goalEdit,
                            profile.iconStyle
                        )
                        showEditProfileDialog = false
                        Toast.makeText(context, "Dados biométricos atualizados!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("SALVAR", color = brandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // BottomSheet de Calibração de Corrida (5 km)
    if (showPaceOnboardingSheet) {
        RunningPaceOnboardingBottomSheet(
            onConfirm = { baselinePace ->
                viewModel.saveRunnerBaselinePace(baselinePace)
                showPaceOnboardingSheet = false
                Toast.makeText(context, "Ritmo de 5 km calibrado com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPaceOnboardingSheet = false },
            currentBaselinePaceMinPerKm = profile.baselinePaceMinPerKm,
            paceAnalysis = paceAnalysis,
            isAutoCalibrationEnabled = paceCalibrationMode == com.example.engine.PaceCalibrationMode.AUTO_14_DAYS,
            onToggleAutoCalibration = { enabled ->
                viewModel.setPaceAutoCalibrationEnabled(enabled)
            }
        )
    }

    // Wizard de 3 Etapas: Anamnese e Calibração Fisiológica de Corrida (Runna Style)
    if (showRunnerAnamnesisWizard) {
        com.example.ui.components.RunnerAnamnesisModal(
            initialProfile = anamnesisProfile,
            athleteAge = profile.idade,
            runningHistory = runningSessions,
            baselinePaceMinPerKm = profile.baselinePaceMinPerKm,
            onSaveProfile = { savedProfile, newBaselinePaceMin, recommendedProgramId ->
                com.example.ui.components.RunnerAnamnesisManager.saveProfile(context, savedProfile)
                viewModel.saveRunnerBaselinePace(newBaselinePaceMin)
                if (savedProfile.goal == com.example.ui.components.RunnerGoalOption.GOAL_MAINTAIN) {
                    viewModel.selectRunningProgram("run_to_maintain")
                } else {
                    viewModel.selectRunningProgram(recommendedProgramId)
                }
                anamnesisVersion++
                showRunnerAnamnesisWizard = false
                Toast.makeText(
                    context,
                    "Perfil e Anamnese de Corrida calibrados com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showRunnerAnamnesisWizard = false
            }
        )
    }

    // Modal Adicionar Novo Tênis à Garagem
    if (showAddShoeDialog) {
        var shoeNameInput by remember { mutableStateOf("") }
        var shoeBrandInput by remember { mutableStateOf("") }
        var shoeInitialKmInput by remember { mutableStateOf("0") }
        var shoeMaxKmInput by remember { mutableStateOf("600") }
        var isDefaultInput by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddShoeDialog = false },
            title = {
                Text("Adicionar Tênis à Garagem", color = primaryText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shoeNameInput,
                        onValueChange = { shoeNameInput = it },
                        label = { Text("Modelo do Tênis (ex: Novablast 5 Verde)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shoeBrandInput,
                        onValueChange = { shoeBrandInput = it },
                        label = { Text("Marca (ex: Asics, Nike, Olympikus)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shoeInitialKmInput,
                        onValueChange = { shoeInitialKmInput = it },
                        label = { Text("Quilometragem Inicial (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shoeMaxKmInput,
                        onValueChange = { shoeMaxKmInput = it },
                        label = { Text("Vida Útil Recomendada (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isDefaultInput,
                            onCheckedChange = { isDefaultInput = it },
                            colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                        )
                        Text("Definir como tênis padrão para treinos", color = primaryText, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (shoeNameInput.isNotBlank()) {
                            val initialKm = shoeInitialKmInput.replace(",", ".").toFloatOrNull() ?: 0f
                            val maxKm = shoeMaxKmInput.replace(",", ".").toFloatOrNull() ?: 600f
                            val newShoe = RunningShoe(
                                name = shoeNameInput.trim(),
                                brand = shoeBrandInput.ifBlank { "Corrida" }.trim(),
                                initialDistanceKm = initialKm,
                                totalDistanceKm = initialKm,
                                maxDistanceKm = maxKm,
                                isDefault = isDefaultInput
                            )
                            viewModel.addRunningShoe(newShoe)
                            showAddShoeDialog = false
                            Toast.makeText(context, "Tênis ${newShoe.name} adicionado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("CADASTRAR", color = brandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddShoeDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // Modal Editar Tênis da Garagem
    shoeToEditInProfile?.let { shoeToEdit ->
        var editBrandInput by remember(shoeToEdit) { mutableStateOf(shoeToEdit.brand) }
        var editNameInput by remember(shoeToEdit) { mutableStateOf(shoeToEdit.name) }
        var editInitialKmInput by remember(shoeToEdit) { mutableStateOf(shoeToEdit.initialDistanceKm.toString()) }
        var editMaxKmInput by remember(shoeToEdit) { mutableStateOf(shoeToEdit.maxDistanceKm.toString()) }
        var editIsDefaultInput by remember(shoeToEdit) { mutableStateOf(shoeToEdit.isDefault) }

        AlertDialog(
            onDismissRequest = { shoeToEditInProfile = null },
            title = {
                Text("Editar Tênis da Garagem", color = primaryText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editBrandInput,
                        onValueChange = { editBrandInput = it },
                        label = { Text("Marca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Nome / Modelo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editInitialKmInput,
                        onValueChange = { editInitialKmInput = it },
                        label = { Text("Quilometragem Inicial (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editMaxKmInput,
                        onValueChange = { editMaxKmInput = it },
                        label = { Text("Vida Útil Recomendada (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = editIsDefaultInput,
                            onCheckedChange = { editIsDefaultInput = it },
                            colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                        )
                        Text("Definir como tênis padrão para treinos", color = primaryText, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            val updatedInitialKm = editInitialKmInput.replace(",", ".").toFloatOrNull() ?: shoeToEdit.initialDistanceKm
                            val updatedMaxKm = editMaxKmInput.replace(",", ".").toFloatOrNull() ?: shoeToEdit.maxDistanceKm
                            val updated = shoeToEdit.copy(
                                brand = editBrandInput.trim(),
                                name = editNameInput.trim(),
                                initialDistanceKm = updatedInitialKm,
                                maxDistanceKm = updatedMaxKm,
                                isDefault = editIsDefaultInput
                            )
                            viewModel.updateRunningShoe(updated)
                            shoeToEditInProfile = null
                            Toast.makeText(context, "Tênis ${updated.name} atualizado!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("SALVAR", color = brandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { shoeToEditInProfile = null }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // Modal Cadastrar Academia
    if (showAddGymDialog) {
        var gymNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddGymDialog = false },
            title = {
                Text("Cadastrar Academia", color = primaryText, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gymNameInput,
                        onValueChange = { gymNameInput = it },
                        label = { Text("Nome da Academia (ex: SmartFit Centro)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (gymNameInput.isNotBlank()) {
                            viewModel.updateGymProfile(
                                GymProfile(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = gymNameInput.trim(),
                                    latitude = null,
                                    longitude = null,
                                    availableExercisesIds = combinedExercises.map { it.id },
                                    unavailableExercisesIds = emptyList()
                                )
                            )
                            showAddGymDialog = false
                            Toast.makeText(context, "Academia salva!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("SALVAR", color = brandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGymDialog = false }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }

    // Modal Gerenciar Aparelhos da Academia
    profileToManageAparelhos?.let { gymProfile ->
        var currentAvailable by remember(gymProfile.id) {
            mutableStateOf(gymProfile.availableExercisesIds.toSet())
        }
        var currentUnavailable by remember(gymProfile.id) {
            mutableStateOf(gymProfile.unavailableExercisesIds.toSet())
        }

        AlertDialog(
            onDismissRequest = { profileToManageAparelhos = null },
            title = {
                Text(
                    text = "Aparelhos em ${gymProfile.name}",
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Marque ou desmarque os aparelhos disponíveis nesta unidade:",
                        color = secondaryText,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = Color(0xFF181C26),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(combinedExercises) { exercise ->
                                val isChecked = currentAvailable.contains(exercise.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isChecked) {
                                                currentAvailable = currentAvailable - exercise.id
                                                currentUnavailable = currentUnavailable + exercise.id
                                            } else {
                                                currentAvailable = currentAvailable + exercise.id
                                                currentUnavailable = currentUnavailable - exercise.id
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                currentAvailable = currentAvailable + exercise.id
                                                currentUnavailable = currentUnavailable - exercise.id
                                            } else {
                                                currentAvailable = currentAvailable - exercise.id
                                                currentUnavailable = currentUnavailable + exercise.id
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                                    )
                                    Column {
                                        Text(exercise.name, color = primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(exercise.category, color = secondaryText, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGymProfile(
                            gymProfile.copy(
                                availableExercisesIds = currentAvailable.toList(),
                                unavailableExercisesIds = currentUnavailable.toList()
                            )
                        )
                        profileToManageAparelhos = null
                        Toast.makeText(context, "Aparelhos atualizados!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("SALVAR", color = brandAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToManageAparelhos = null }) {
                    Text("CANCELAR", color = secondaryText)
                }
            },
            containerColor = cardBg
        )
    }
}

@Composable
private fun BiometricStatItem(
    label: String,
    value: String,
    primaryText: Color,
    secondaryText: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = secondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        )
    }
}
