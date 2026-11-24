package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RunningSession
import com.example.data.model.deduplicatedRunningSessions
import com.example.data.repository.GymHistoryRepository
import com.example.domain.analysis.AerobicEfficiencyStatus
import com.example.domain.analysis.MacroPeriodWindow
import com.example.domain.analysis.PeriodMacroAnalysisEngine
import com.example.domain.analysis.PeriodMacroDossier
import com.example.domain.recomp.BodyRecompEngine
import com.example.utils.AudioSynth
import kotlinx.coroutines.delay
import java.util.Locale

private val MacroBgDark = Color(0xFF0D0F14)
private val MacroCardSurface = Color(0xFF151922)
private val MacroCardInner = Color(0xFF10141C)
private val MacroBorderSubtle = Color(0xFF232A38)
private val MacroBlue = Color(0xFF00B0FF)
private val MacroCyan = Color(0xFF00E5FF)
private val MacroGreen = Color(0xFF00E676)
private val MacroCoral = Color(0xFFFF5252)
private val MacroTextDim = Color(0xFF94A3B8)

/**
 * Card Compacto e Retrátil de Entrada ("DOSSIÊ DE PERÍODO • MACRO-ANÁLISE IA") para ser inserido
 * em `RunningModuleScreen.kt`, `RunningHistoryScreen.kt` e `BodyRecompScreen.kt`.
 * Inicia recolhido por padrão (exibindo 1 linha compacta com resumo rápido) e expande sob demanda.
 */
@Composable
fun PeriodMacroAnalysisEntryCard(
    modifier: Modifier = Modifier,
    runningSessions: List<RunningSession>? = null,
    gymSessions: List<GymWorkoutSession>? = null,
    recompRecords: List<BioimpedanceRecord>? = null,
    userAgeYears: Int?,
    initialWindow: MacroPeriodWindow = MacroPeriodWindow.DAYS_7,
    initiallyExpanded: Boolean = false
) {
    val context = LocalContext.current
    val ptBr = remember { Locale("pt", "BR") }

    // Caso a tela chamadora não passe alguma das 3 bases em memória, busca automaticamente do banco/repositório local
    val dbRunningFlow = remember(context) {
        runCatching { AppDatabase.getDatabase(context).runningSessionDao().getAllRunningSessions() }.getOrNull()
    }
    val dbRunningSessions by (dbRunningFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

    LaunchedEffect(context) {
        runCatching { GymHistoryRepository.init(context) }
    }
    val repoGymSessions by GymHistoryRepository.sessions.collectAsState()

    val effectiveRuns = remember(runningSessions, dbRunningSessions) {
        (runningSessions ?: dbRunningSessions).deduplicatedRunningSessions()
    }
    val effectiveGyms = gymSessions ?: repoGymSessions
    val effectiveRecomp = remember(recompRecords, context) {
        recompRecords ?: runCatching { BodyRecompEngine.loadRecords(context) }.getOrDefault(emptyList())
    }

    var selectedWindow by remember { mutableStateOf(initialWindow) }
    var isDossierExpanded by remember { mutableStateOf(initiallyExpanded) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val previewDossier = remember(selectedWindow, effectiveRuns, effectiveGyms, effectiveRecomp, userAgeYears) {
        PeriodMacroAnalysisEngine.analyzePeriod(
            window = selectedWindow,
            allRunningSessions = effectiveRuns,
            allGymSessions = effectiveGyms,
            allRecompRecords = effectiveRecomp,
            userAgeYears = userAgeYears
        )
    }

    val compactWindowTag = when (selectedWindow) {
        MacroPeriodWindow.DAYS_7 -> "7d"
        MacroPeriodWindow.DAYS_15 -> "15d"
        MacroPeriodWindow.DAYS_30 -> "30d"
        MacroPeriodWindow.ALL_CYCLE -> "Ciclo"
    }
    val compactSummaryBadge = String.format(
        ptBr,
        "%s: %.1fkm • %dx Gym",
        compactWindowTag,
        previewDossier.pillar1Volume.totalDistanceKm,
        previewDossier.pillar3Hybrid.totalGymSessions
    )

    Surface(
        color = MacroCardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MacroBlue.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("period_macro_analysis_entry_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isDossierExpanded) 12.dp else 10.dp)
        ) {
            // Linha 1: Barra Compacta / Cabeçalho Acordeão (Toque para expandir ou recolher)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        AudioSynth.playTickSound()
                        isDossierExpanded = !isDossierExpanded
                    }
                    .testTag("period_macro_analysis_accordion_header"),
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
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MacroBlue.copy(alpha = 0.16f))
                            .border(1.dp, MacroCyan.copy(alpha = 0.4f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Macro-Análise IA",
                            tint = MacroCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDossierExpanded) "DOSSIÊ DE PERÍODO • MACRO-ANÁLISE IA" else "Dossiê de Período • Macro-Análise IA",
                            color = MacroCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDossierExpanded) {
                            Text(
                                text = "Cruzamento MAX RUN × MAX GYM × MAX RECOMP",
                                color = MacroTextDim,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MacroBlue.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MacroBlue.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = compactSummaryBadge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isDossierExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isDossierExpanded) "Recolher dossiê" else "Expandir dossiê",
                        tint = MacroCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Conteúdo Expansível (7 DIAS / 15 DIAS / 30 DIAS / CICLO TAF + Resumo + Botão Abrir Análise)
            AnimatedVisibility(visible = isDossierExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Linha 2: Seletor Rápido de Pílulas (7D • 15D • 30D • CICLO)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MacroPeriodWindow.entries.forEach { win ->
                            val isSelected = win == selectedWindow
                            Surface(
                                onClick = {
                                    selectedWindow = win
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) MacroBlue.copy(alpha = 0.22f) else MacroCardInner,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MacroCyan else MacroBorderSubtle
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .testTag("entry_period_pill_${win.name.lowercase()}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = win.pillLabel,
                                        color = if (isSelected) Color.White else MacroTextDim,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Linha 3: Mini-resumo das 3 bases para a janela selecionada
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MacroCardInner)
                            .border(1.dp, MacroBorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniPeriodMetricColumn(
                            label = "CORRIDA (${selectedWindow.pillLabel})",
                            value = String.format(ptBr, "%.1f km • %s", previewDossier.pillar1Volume.totalDistanceKm, previewDossier.pillar1Volume.avgSprintPaceFormatted),
                            valueColor = MacroCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MiniPeriodMetricColumn(
                            label = "FORÇA (GYM)",
                            value = "${previewDossier.pillar3Hybrid.totalGymSessions}x • ${PeriodMacroAnalysisEngine.formatTonnage(previewDossier.pillar3Hybrid.totalTonnageKg)}",
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        val recompColor = if (previewDossier.pillar4Recomp.estimatedPaceGainSecPerKm <= 0f) MacroGreen else MacroCoral
                        MiniPeriodMetricColumn(
                            label = "RECOMP PACE",
                            value = previewDossier.pillar4Recomp.estimatedPaceGainFormatted,
                            valueColor = recompColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Linha 4: Botão de Abertura do Dossiê Completo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(MacroBlue, MacroCyan)
                                )
                            )
                            .clickable {
                                AudioSynth.playTickSound()
                                showBottomSheet = true
                            }
                            .testTag("open_period_macro_analysis_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = Color(0xFF06101E),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ABRIR ANÁLISE DO PERÍODO",
                                color = Color(0xFF06101E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        PeriodMacroAnalysisBottomSheet(
            selectedWindow = selectedWindow,
            onSelectWindow = { selectedWindow = it },
            runningSessions = effectiveRuns,
            gymSessions = effectiveGyms,
            recompRecords = effectiveRecomp,
            userAgeYears = userAgeYears,
            onDismiss = { showBottomSheet = false }
        )
    }
}

@Composable
private fun MiniPeriodMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            color = MacroTextDim,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Modal BottomSheet Completo da Análise Longitudinal de Período (Macro-Análise Híbrida IA).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodMacroAnalysisBottomSheet(
    selectedWindow: MacroPeriodWindow,
    onSelectWindow: (MacroPeriodWindow) -> Unit,
    runningSessions: List<RunningSession>,
    gymSessions: List<GymWorkoutSession>,
    recompRecords: List<BioimpedanceRecord>,
    userAgeYears: Int?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ptBr = remember { Locale("pt", "BR") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(2500)
            copiedFeedback = false
        }
    }

    val dossier: PeriodMacroDossier = remember(
        selectedWindow,
        runningSessions,
        gymSessions,
        recompRecords,
        userAgeYears
    ) {
        PeriodMacroAnalysisEngine.analyzePeriod(
            window = selectedWindow,
            allRunningSessions = runningSessions,
            allGymSessions = gymSessions,
            allRecompRecords = recompRecords,
            userAgeYears = userAgeYears
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MacroBgDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MacroTextDim.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabeçalho do Modal
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
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MacroCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "MACRO-ANÁLISE HÍBRIDA IA",
                            color = MacroCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = dossier.window.fullTitle,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Recorte: ${dossier.periodRangeLabel}",
                        color = MacroTextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MacroCardSurface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar Dossiê",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. Seletor de Período em Pílulas no Topo (7 DIAS, 15 DIAS, 30 DIAS, CICLO TAF)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MacroPeriodWindow.entries.forEach { win ->
                    val isSelected = win == selectedWindow
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MacroBlue else MacroCardSurface,
                        label = "period_pill_bg"
                    )
                    Surface(
                        onClick = {
                            onSelectWindow(win)
                            AudioSynth.playTickSound()
                        },
                        color = bgColor,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MacroCyan else MacroBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag("sheet_period_pill_${win.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = win.pillLabel,
                                color = if (isSelected) Color(0xFF06101E) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. Grid de Resumo Executivo 2x2
            val p1 = dossier.pillar1Volume
            val p2 = dossier.pillar2Aerobic
            val p3 = dossier.pillar3Hybrid
            val p4 = dossier.pillar4Recomp

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExecutiveSummaryGridTile(
                        icon = Icons.Default.DirectionsRun,
                        title = "DISTÂNCIA & TIROS",
                        primaryValue = String.format(ptBr, "%.2f km", p1.totalDistanceKm),
                        secondaryValue = "Sprint: ${p1.avgSprintPaceFormatted}/km (${p1.totalSessions}x)",
                        accentColor = MacroCyan,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ExecutiveSummaryGridTile(
                        icon = Icons.Default.Favorite,
                        title = "EFICIÊNCIA CARDÍACA",
                        primaryValue = if (p2.sessionsWithHrCount > 0) "${p2.avgHrBpm} bpm" else "Sem BLE",
                        secondaryValue = if (p2.sessionsWithHrCount > 0) {
                            String.format(ptBr, "EF %.2f m/bpm • Pico %d", p2.avgEfficiencyFactor, p2.peakHrBpm)
                        } else {
                            "Conecte a cinta ECG"
                        },
                        accentColor = Color(p2.status.colorHex),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExecutiveSummaryGridTile(
                        icon = Icons.Default.FitnessCenter,
                        title = "TONELAGEM HÍBRIDA GYM",
                        primaryValue = PeriodMacroAnalysisEngine.formatTonnage(p3.totalTonnageKg),
                        secondaryValue = "${p3.totalGymSessions} treinos • Pernas ${p3.lowerBodyPercent}%",
                        accentColor = if (p3.isConcurrentOverload) MacroCoral else MacroBlue,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ExecutiveSummaryGridTile(
                        icon = Icons.Default.MonitorWeight,
                        title = "IMPACTO RECOMP NO PACE",
                        primaryValue = p4.estimatedPaceGainFormatted,
                        secondaryValue = "Gordura ${PeriodMacroAnalysisEngine.formatSignedKg(p4.fatMassDeltaKg)} | Músculo ${PeriodMacroAnalysisEngine.formatSignedKg(p4.muscleMassDeltaKg)}",
                        accentColor = if (p4.estimatedPaceGainSecPerKm <= 0f) MacroGreen else MacroCoral,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 3. Diretriz Fisiológica para a Próxima Semana (Destaque Executivo)
            val directiveColor = Color(dossier.nextWeekDirective.colorHex)
            Surface(
                color = MacroCardSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, directiveColor.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIRETRIZ FISIOLÓGICA • PRÓXIMA SEMANA",
                            color = MacroTextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            color = directiveColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, directiveColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = dossier.nextWeekDirective.label,
                                color = directiveColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = dossier.coachExecutiveVerdict,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // 4. Cards Detalhados dos 4 Pilares
            PillarDetailCard(
                pillarNumber = "PILAR 1",
                title = "Volume & Consistência de Corrida (MAX RUN)",
                badgeText = p1.sprintPaceEvolutionLabel,
                badgeColor = if (p1.isEvolutionPositive) MacroGreen else MacroCoral,
                progress = p1.consistencyProgress,
                progressColor = MacroCyan,
                metricsRow = listOf(
                    "Sessões" to "${p1.totalSessions} treinos",
                    "Tempo Ativo" to p1.totalDurationFormatted,
                    "Sprint Pace" to "${p1.avgSprintPaceFormatted}/km",
                    "Melhor Tiro" to "${p1.bestSprintPaceFormatted}/km"
                ),
                narrative = p1.narrative
            )

            PillarDetailCard(
                pillarNumber = "PILAR 2",
                title = "Eficiência Aeróbica & Evolução Cardíaca (Pace × BPM)",
                badgeText = p2.status.badgeLabel,
                badgeColor = Color(p2.status.colorHex),
                progress = p2.efficiencyProgress,
                progressColor = Color(p2.status.colorHex),
                metricsRow = listOf(
                    "FC Média" to if (p2.sessionsWithHrCount > 0) "${p2.avgHrBpm} bpm" else "--",
                    "FC Pico Real" to if (p2.sessionsWithHrCount > 0) "${p2.peakHrBpm} bpm" else "--",
                    "FCmáx Calib." to (p2.calibratedMaxHrBpm?.let { "$it bpm" } ?: "idade não informada"),
                    "Fator EF" to if (p2.sessionsWithHrCount > 0) String.format(ptBr, "%.2f m/bpm", p2.avgEfficiencyFactor) else "--"
                ),
                narrative = p2.narrative
            )

            PillarDetailCard(
                pillarNumber = "PILAR 3",
                title = "Balanço de Carga Híbrida & Fadiga Concorrente",
                badgeText = p3.concurrentStatusLabel,
                badgeColor = if (p3.isConcurrentOverload) MacroCoral else MacroBlue,
                progress = p3.balanceProgress,
                progressColor = if (p3.isConcurrentOverload) MacroCoral else MacroBlue,
                metricsRow = listOf(
                    "Treinos GYM" to "${p3.totalGymSessions}x",
                    "Pernas" to "${PeriodMacroAnalysisEngine.formatTonnage(p3.lowerBodyTonnageKg)} (${p3.lowerBodyPercent}%)",
                    "Sup./Tronco" to "${PeriodMacroAnalysisEngine.formatTonnage(p3.upperAndCoreTonnageKg)} (${p3.upperAndCorePercent}%)",
                    "Cadência Run" to "${p3.avgCadenceSpm} spm"
                ),
                narrative = p3.narrative
            )

            val recompBadgeColor = if (p4.estimatedPaceGainSecPerKm <= 0f) MacroGreen else MacroCoral
            PillarDetailCard(
                pillarNumber = "PILAR 4",
                title = "Recomposição Corporal & Impacto Termodinâmico no Pace",
                badgeText = "ECONOMIA: ${p4.estimatedPaceGainFormatted.uppercase(ptBr)}",
                badgeColor = recompBadgeColor,
                progress = p4.recompProgress,
                progressColor = recompBadgeColor,
                metricsRow = listOf(
                    "Peso" to "${String.format(ptBr, "%.1f", p4.endWeightKg)} kg (${PeriodMacroAnalysisEngine.formatSignedKg(p4.weightDeltaKg)})",
                    "Massa Gorda" to "${String.format(ptBr, "%.1f", p4.endFatMassKg)} kg (${PeriodMacroAnalysisEngine.formatSignedKg(p4.fatMassDeltaKg)})",
                    "Gordura %" to "${String.format(ptBr, "%.1f%%", p4.endFatPercent)}",
                    "Músculo" to "${String.format(ptBr, "%.1f", p4.endMuscleMassKg)} kg (${PeriodMacroAnalysisEngine.formatSignedKg(p4.muscleMassDeltaKg)})"
                ),
                narrative = p4.narrative
            )

            // 5. Botão de Ação Principal: COPIAR RELATÓRIO DO PERÍODO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (copiedFeedback) {
                            Brush.horizontalGradient(listOf(MacroGreen, MacroGreen))
                        } else {
                            Brush.horizontalGradient(listOf(MacroBlue, MacroCyan))
                        }
                    )
                    .clickable {
                        AudioSynth.playTickSound()
                        copyPeriodReportToClipboard(context, dossier)
                        copiedFeedback = true
                    }
                    .testTag("copy_period_macro_report_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (copiedFeedback) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color(0xFF06101E),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (copiedFeedback) "RELATÓRIO COPIADO!" else "COPIAR RELATÓRIO DO PERÍODO",
                        color = Color(0xFF06101E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutiveSummaryGridTile(
    icon: ImageVector,
    title: String,
    primaryValue: String,
    secondaryValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MacroCardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = title,
                    color = MacroTextDim,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = primaryValue,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = secondaryValue,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PillarDetailCard(
    pillarNumber: String,
    title: String,
    badgeText: String,
    badgeColor: Color,
    progress: Float,
    progressColor: Color,
    metricsRow: List<Pair<String, String>>,
    narrative: String
) {
    Surface(
        color = MacroCardSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MacroBorderSubtle),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = pillarNumber,
                    color = MacroCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )

                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = progressColor,
                trackColor = MacroCardInner,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            // Métricas horizontais alinhadas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MacroCardInner)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                metricsRow.forEach { (mLabel, mVal) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mLabel,
                            color = MacroTextDim,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = mVal,
                            color = Color.White,
                            fontSize = if (mVal.length > 14) 9.sp else 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = if (mVal.length > 14) 2 else 1,
                            softWrap = mVal.length > 14,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Parecer Técnico do Coach Fisiologista
            Text(
                text = narrative,
                color = Color(0xFFD1D5DB),
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp
            )
        }
    }
}

private fun copyPeriodReportToClipboard(
    context: Context,
    dossier: PeriodMacroDossier
) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(
            "Dossiê de Período (${dossier.window.pillLabel})",
            dossier.formattedReportText
        )
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(
            context,
            "Relatório copiado! (${dossier.window.fullTitle})",
            Toast.LENGTH_SHORT
        ).show()
    }
}
