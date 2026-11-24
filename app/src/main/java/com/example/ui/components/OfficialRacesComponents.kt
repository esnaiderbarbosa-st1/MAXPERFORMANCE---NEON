package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.OfficialRaceComparisonResult
import com.example.data.model.OfficialRaceRecord
import com.example.data.model.RunningSession
import com.example.data.model.RunningShoe
import com.example.data.model.UserProfileData
import com.example.data.model.buildOfficialRaceRecordFromSession
import com.example.data.model.compareOfficialRaces
import com.example.data.model.computeOfficialRacePrs
import com.example.data.model.formatPaceMinSecQuote
import com.example.engine.RunningProgramManager
import com.example.ui.theme.RunColors
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private val GoldAccent = Color(0xFFFFD600)
private val EmeraldDelta = Color(0xFF00E676)
private val CoralDelta = Color(0xFFFF5252)
private val CyanBrand = Color(0xFF00E5FF)
private val DarkVaultBg = Color(0xFF0D0F14)
private val DarkCardBg = Color(0xFF151922)
private val DarkCardBorder = Color(0xFF262D3D)

fun formatRaceDurationMmSs(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatSignedDeltaDuration(deltaSeconds: Int, suffixPositiveMeaning: String): String {
    val sign = if (deltaSeconds < 0) "-" else if (deltaSeconds > 0) "+" else ""
    val absSec = abs(deltaSeconds)
    val m = absSec / 60
    val s = absSec % 60
    val base = String.format(Locale.US, "%s%02d:%02d", sign, m, s)
    return if (suffixPositiveMeaning.isNotBlank()) "$base ($suffixPositiveMeaning)" else base
}

private fun parseMmSsInputToSeconds(raw: String, fallbackSec: Int): Int {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return fallbackSec
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600) + (parts[1] * 60) + parts[2]
            2 -> (parts[0] * 60) + parts[1]
            else -> fallbackSec
        }.coerceAtLeast(0)
    }
    val asMinutesFloat = trimmed.replace(',', '.').toDoubleOrNull()
    return if (asMinutesFloat != null && asMinutesFloat > 0) {
        (asMinutesFloat * 60.0).roundToInt()
    } else {
        fallbackSec
    }
}

private fun parsePaceInputToSecPerKm(raw: String, fallbackSec: Int): Int {
    val cleaned = raw.trim()
        .replace("/km", "", ignoreCase = true)
        .replace("\"", "")
        .replace("'", ":")
        .trim()
    if (cleaned.isBlank()) return fallbackSec
    if (cleaned.contains(":")) {
        val parts = cleaned.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size >= 2) {
            return (parts[0] * 60 + parts[1]).coerceIn(120, 1200)
        }
    }
    val floatMin = cleaned.replace(',', '.').toDoubleOrNull()
    return if (floatMin != null && floatMin > 1.5) {
        (floatMin * 60.0).roundToInt().coerceIn(120, 1200)
    } else {
        fallbackSec
    }
}

/**
 * Tela Dedicada da Aba "Planejamento" (Modo Corrida):
 * Sub-aba 0: Programas & Protocolos de Treino
 * Sub-aba 1: Provas & Evolução (Recordes Pessoais, Comparativo VS e Histórico de Provas)
 * Zero emojis — 100% ícones vetoriais Material 3 e dimensionado para telas compactas (360dp / Motorola G84).
 */
@Composable
fun RunningPlanningScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val selectedSubTab by viewModel.runningPlanningSubTab.collectAsState()
    val selectedProgramId by viewModel.selectedRunningProgramId.collectAsState()
    val officialRaces by viewModel.officialRaces.collectAsState()
    val upcomingRaces by viewModel.upcomingRaces.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val gymSessions by viewModel.gymWorkoutSessions.collectAsState()
    val activePreRunCheckIn by viewModel.activePreRunCheckIn.collectAsState()
    val runningShoes by viewModel.runningShoes.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    val currentProgramInfo = remember(selectedProgramId) {
        RunningProgramManager.getProgramById(selectedProgramId)
    }

    ProvideRunFontScale {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RunColors.bg)
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            // Cabeçalho Superior Compacto (Título + Fonte A-/A+)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
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
                        text = "Planejamento & Provas",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Programas de treino, provas oficiais e comparativo VS",
                        color = RunColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                RunFontScaleToggleButton(compact = true)
            }

            // Seletor Segmentado de Sub-abas: [ Programas ] | [ Provas & Evolução ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141821), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = {
                        viewModel.runningPlanningSubTab.value = 0
                        AudioSynth.playTickSound()
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selectedSubTab == 0) RunColors.brand.copy(alpha = 0.24f) else Color.Transparent,
                    border = if (selectedSubTab == 0) BorderStroke(1.dp, RunColors.brand) else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("planning_subtab_programs")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = if (selectedSubTab == 0) RunColors.brandText else Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Programas",
                            color = if (selectedSubTab == 0) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = {
                        viewModel.runningPlanningSubTab.value = 1
                        AudioSynth.playTickSound()
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selectedSubTab == 1) GoldAccent.copy(alpha = 0.20f) else Color.Transparent,
                    border = if (selectedSubTab == 1) BorderStroke(1.dp, GoldAccent) else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("planning_subtab_official_races")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = if (selectedSubTab == 1) GoldAccent else Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Provas & Evolução",
                            color = if (selectedSubTab == 1) GoldAccent else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedSubTab == 0) {
                // SUB-ABA 0: CATÁLOGO DEDICADO DE PROGRAMAS E PROTOCOLOS DE CORRIDA
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp)
                ) {
                    item(key = "active_program_banner") {
                        val autoProgress = remember(runningSessions) {
                            RunningProgramManager.calculateCurrentProgress(runningSessions)
                        }
                        val manualPlanOverride by viewModel.manualPlanDayOverride.collectAsState()
                        val activePlanW = manualPlanOverride?.first ?: autoProgress.weekNumber
                        val activePlanD = manualPlanOverride?.second ?: autoProgress.dayNumber

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                            border = BorderStroke(1.2.dp, RunColors.brand.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                            text = "PROTOCOLO ATIVO NO INÍCIO",
                                            color = RunColors.brandText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentProgramInfo.title,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            viewModel.activeScreen.value = AppScreen.HOME
                                        },
                                        color = RunColors.brand.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, RunColors.brand)
                                    ) {
                                        Text(
                                            text = "Ir ao Início",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                // Controle rápido para alternar ou voltar o dia atual da planilha (< W4 D3 >)
                                Surface(
                                    color = Color(0xFF0D0F14),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val (prevW, prevD) = if (activePlanD > 1) {
                                                    Pair(activePlanW, activePlanD - 1)
                                                } else if (activePlanW > 1) {
                                                    Pair(activePlanW - 1, 3)
                                                } else {
                                                    Pair(1, 1)
                                                }
                                                viewModel.setManualPlanDayOverride(prevW, prevD)
                                                AudioSynth.playTickSound()
                                            },
                                            enabled = activePlanW > 1 || activePlanD > 1,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                contentDescription = "Voltar 1 sessão da planilha",
                                                tint = if (activePlanW > 1 || activePlanD > 1) Color.White else Color.DarkGray
                                            )
                                        }

                                        Text(
                                            text = "Sessão Atual: Semana $activePlanW · Dia $activePlanD (W$activePlanW D$activePlanD)",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        IconButton(
                                            onClick = {
                                                val (nextW, nextD) = if (activePlanD < 3) {
                                                    Pair(activePlanW, activePlanD + 1)
                                                } else if (activePlanW < 10) {
                                                    Pair(activePlanW + 1, 1)
                                                } else {
                                                    Pair(10, 3)
                                                }
                                                viewModel.setManualPlanDayOverride(nextW, nextD)
                                                AudioSynth.playTickSound()
                                            },
                                            enabled = activePlanW < 10 || activePlanD < 3,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Avançar 1 sessão da planilha",
                                                tint = if (activePlanW < 10 || activePlanD < 3) Color.White else Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = RunningProgramManager.ALL_RUNNING_PROGRAMS,
                        key = { it.id }
                    ) { prog ->
                        val isSelected = prog.id == selectedProgramId
                        Surface(
                            onClick = {
                                viewModel.selectRunningProgram(prog.id)
                                AudioSynth.playTickSound()
                                Toast.makeText(
                                    context,
                                    "Programa selecionado: ${prog.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            color = if (isSelected) RunColors.brand.copy(alpha = 0.14f) else Color(0xFF14161D),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) RunColors.brand else RunColors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("running_program_item_${prog.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = RunColors.brand.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = prog.badge,
                                            color = RunColors.brandText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = prog.durationLabel,
                                            color = RunColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selecionado",
                                                tint = EmeraldDelta,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = prog.title,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = prog.subtitle,
                                    color = RunColors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // SUB-ABA 1: PROVAS & EVOLUÇÃO (VS) INTEGRADO NA ABA PLANEJAMENTO
                OfficialRacesPlanningSubTabContent(
                    officialRaces = officialRaces,
                    runningSessions = runningSessions,
                    gymSessions = gymSessions,
                    activePreRunCheckIn = activePreRunCheckIn,
                    runningShoes = runningShoes,
                    userProfile = userProfile,
                    upcomingRaces = upcomingRaces,
                    onSaveRace = { record -> viewModel.saveOfficialRaceRecord(record) },
                    onDeleteRace = { raceId -> viewModel.deleteOfficialRaceRecord(raceId) },
                    onSaveUpcomingRace = { event -> viewModel.saveUpcomingRaceEvent(event) },
                    onDeleteUpcomingRace = { eventId -> viewModel.deleteUpcomingRaceEvent(eventId) },
                    onOpenLinkedSession = { targetSession ->
                        viewModel.selectedDetailSession.value = targetSession
                        viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Card de entrada para "Provas & Evolução" dimensionado para telas compactas (Motorola G84 / 360dp),
 * usando exclusivamente ícones vetoriais Material 3 (zero emojis).
 */
@Composable
fun OfficialRacesEntryBannerCard(
    officialRaces: List<OfficialRaceRecord>,
    onOpenVault: () -> Unit,
    onAddManualRace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val prSummary = remember(officialRaces) { computeOfficialRacePrs(officialRaces) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                AudioSynth.playTickSound()
                onOpenVault()
            }
            .testTag("official_races_entry_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.2.dp, GoldAccent.copy(alpha = 0.48f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Provas e Evolução",
                            tint = GoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PROVAS & EVOLUÇÃO",
                            color = GoldAccent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (officialRaces.isEmpty()) {
                                "5K • 10K • TAF • Comparativo VS"
                            } else {
                                "${officialRaces.size} ${if (officialRaces.size == 1) "prova oficial" else "provas oficiais"} • Modo VS"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = {
                        AudioSynth.playTickSound()
                        onAddManualRace()
                    },
                    color = GoldAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Nova Prova",
                            tint = GoldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Prova",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (officialRaces.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkVaultBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MELHOR 5K",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = prSummary.best5kRace?.let { formatRaceDurationMmSs(it.officialTimeSec) } ?: "--:--",
                            color = GoldAccent,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DarkCardBorder)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PACE CORR.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = prSummary.bestRunningPaceRace?.let { "${formatPaceMinSecQuote(it.runningPaceSecPerKm)}/km" } ?: "--'--\"",
                            color = CyanBrand,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(DarkCardBorder)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CONSTÂNCIA",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = prSummary.bestConstancyRace?.let {
                                String.format(ptBr, "%.0f%% corr.", it.constancyRunningPct)
                            } ?: "--%",
                            color = EmeraldDelta,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (officialRaces.isNotEmpty()) {
                        "Última: ${officialRaces.first().raceName} (${String.format(ptBr, "%.2f km", officialRaces.first().actualDistanceKm)})"
                    } else {
                        "Converta corridas em 1 toque ou cadastre provas"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "Abrir Painel",
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Conteúdo Rolável Dedicado de "Provas & Evolução" (usado tanto na sub-aba de Planejamento
 * quanto no modal fullscreen caso acionado externamente).
 */
@Composable
fun OfficialRacesPlanningSubTabContent(
    officialRaces: List<OfficialRaceRecord>,
    runningSessions: List<RunningSession>,
    gymSessions: List<com.example.data.model.GymWorkoutSession> = emptyList(),
    activePreRunCheckIn: com.example.data.model.PreRunCheckIn? = null,
    runningShoes: List<RunningShoe>,
    userProfile: UserProfileData?,
    upcomingRaces: List<com.example.data.model.UpcomingRaceEvent> = emptyList(),
    onSaveRace: (OfficialRaceRecord) -> Unit,
    onDeleteRace: (String) -> Unit,
    onSaveUpcomingRace: ((com.example.data.model.UpcomingRaceEvent) -> Unit)? = null,
    onDeleteUpcomingRace: ((String) -> Unit)? = null,
    onOpenLinkedSession: ((RunningSession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", ptBr) }

    var upcomingRaceToEdit by remember { mutableStateOf<com.example.data.model.UpcomingRaceEvent?>(null) }
    var showAddUpcomingModal by remember { mutableStateOf(false) }

    val sortedRaces = remember(officialRaces) { officialRaces.sortedByDescending { it.dateMillis } }
    val prSummary = remember(sortedRaces) { computeOfficialRacePrs(sortedRaces) }

    var selectedNewerRaceId by remember(sortedRaces) {
        mutableStateOf(sortedRaces.firstOrNull()?.id)
    }
    var selectedOlderRaceId by remember(sortedRaces) {
        val newest = sortedRaces.firstOrNull()
        val sameCatOlder = if (newest != null) {
            sortedRaces.drop(1).firstOrNull {
                abs(it.categoryDistanceKm - newest.categoryDistanceKm) < 0.5
            } ?: sortedRaces.getOrNull(1)
        } else null
        mutableStateOf(sameCatOlder?.id)
    }

    var previewSimulatedTargetVs by remember { mutableStateOf(false) }
    var raceToEdit by remember { mutableStateOf<OfficialRaceRecord?>(null) }
    var sessionToConvert by remember { mutableStateOf<RunningSession?>(null) }
    var showManualCreateModal by remember { mutableStateOf(false) }
    var raceToDeleteConfirm by remember { mutableStateOf<OfficialRaceRecord?>(null) }

    val newerRace = remember(sortedRaces, selectedNewerRaceId, previewSimulatedTargetVs) {
        val base = sortedRaces.find { it.id == selectedNewerRaceId } ?: sortedRaces.firstOrNull()
        if (previewSimulatedTargetVs && sortedRaces.size == 1 && base != null) {
            val projectedOfficialSec = (base.officialTimeSec - 195).coerceAtLeast(1200)
            val projectedWalkSec = (base.walkingTimeSec / 2).coerceAtLeast(0)
            val projectedRunSec = (projectedOfficialSec - projectedWalkSec).coerceAtLeast(600)
            val projectedAvgPace = (base.avgPaceSecPerKm - 36).coerceAtLeast(210)
            val projectedRunPace = (base.runningPaceSecPerKm - 22).coerceAtLeast(195)
            val projectedBestSplit = (base.bestSplitPaceSecPerKm - 18).coerceAtLeast(185)
            base.copy(
                id = "simulated_next_race_target",
                raceName = "Meta Próxima Prova (4ª Corrida BPTRAN)",
                dateMillis = base.dateMillis + 30L * 86_400_000L,
                actualDistanceKm = base.categoryDistanceKm.takeIf { it > 0.0 } ?: 5.0,
                officialTimeSec = projectedOfficialSec,
                runningTimeSec = projectedRunSec,
                walkingTimeSec = projectedWalkSec,
                avgPaceSecPerKm = projectedAvgPace,
                runningPaceSecPerKm = projectedRunPace,
                bestSplitPaceSecPerKm = projectedBestSplit,
                avgHeartRate = (base.avgHeartRate - 2).coerceAtLeast(140),
                maxHeartRate = base.maxHeartRate.coerceAtLeast(172),
                avgCadenceSpm = (base.avgCadenceSpm + 6).coerceAtMost(182),
                maxCadenceSpm = (base.maxCadenceSpm + 6).coerceAtMost(192),
                shoeUsed = "Asics Novablast 5 (43 BR)"
            )
        } else {
            base
        }
    }

    val olderRace = remember(sortedRaces, selectedOlderRaceId, previewSimulatedTargetVs) {
        if (previewSimulatedTargetVs && sortedRaces.size == 1) {
            sortedRaces.firstOrNull()
        } else {
            sortedRaces.find { it.id == selectedOlderRaceId && it.id != selectedNewerRaceId }
                ?: sortedRaces.firstOrNull { it.id != selectedNewerRaceId }
        }
    }

    val comparisonResult: OfficialRaceComparisonResult? = remember(olderRace, newerRace) {
        if (olderRace != null && newerRace != null && olderRace.id != newerRace.id) {
            compareOfficialRaces(olderRace = olderRace, newerRace = newerRace)
        } else null
    }

    val unconvertedSessions = remember(runningSessions, sortedRaces) {
        val linkedIds = sortedRaces.mapNotNull { it.linkedRunSessionId }.toSet()
        runningSessions
            .filter { it.id !in linkedIds && it.distanceMeters >= 1500 }
            .sortedByDescending { it.date }
            .take(5)
    }

    val hybridCoachSummary = remember(runningSessions, gymSessions, upcomingRaces, activePreRunCheckIn) {
        com.example.engine.HybridLoadCoachEngine.evaluateHybridState(
            runningSessions = runningSessions,
            gymSessions = gymSessions,
            upcomingRaces = upcomingRaces,
            activePreRunCheckIn = activePreRunCheckIn
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =========================================================================
        // SEÇÃO HÍBRIDA PRÉ-TAF: CONSOLIDAÇÃO DE CARGA HÍBRIDA & COACH TAF
        // =========================================================================
        HybridLoadCoachTafCard(
            summary = hybridCoachSummary,
            onQuickSetTafDay = { dayOfNov ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = hybridCoachSummary.targetRaceEvent.raceDateMillis
                    set(java.util.Calendar.YEAR, 2026)
                    set(java.util.Calendar.MONTH, java.util.Calendar.NOVEMBER)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfNov)
                }
                val updatedEvent = hybridCoachSummary.targetRaceEvent.copy(
                    raceDateMillis = cal.timeInMillis
                )
                onSaveUpcomingRace?.invoke(updatedEvent)
                Toast.makeText(
                    context,
                    "Data do ${updatedEvent.raceName} ajustada para $dayOfNov/11/2026!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onEditTargetRace = {
                AudioSynth.playTickSound()
                upcomingRaceToEdit = hybridCoachSummary.targetRaceEvent
                showAddUpcomingModal = true
            }
        )

        // =========================================================================
        // SEÇÃO 0: AGENDA DE PRÓXIMAS PROVAS INSCRITAS + CONTAGEM REGRESSIVA
        // =========================================================================
        UpcomingRacesAgendaSection(
            upcomingRaces = upcomingRaces,
            onAddClick = {
                AudioSynth.playTickSound()
                upcomingRaceToEdit = null
                showAddUpcomingModal = true
            },
            onEditRace = { race ->
                AudioSynth.playTickSound()
                upcomingRaceToEdit = race
                showAddUpcomingModal = true
            },
            onDeleteRace = { id ->
                AudioSynth.playTickSound()
                onDeleteUpcomingRace?.invoke(id)
            },
            onLinkResult = { race ->
                AudioSynth.playTickSound()
                // Abre cadastro de prova oficial preenchido com os dados da prova agendada
                showManualCreateModal = true
            }
        )

        Button(
            onClick = {
                AudioSynth.playTickSound()
                showManualCreateModal = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_add_official_race"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldAccent,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CADASTRAR PROVA OFICIAL",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }

        // =========================================================================
        // SEÇÃO 1: CARDS DE RECORDES PESSOAIS (PRs) — LAYOUT 2 + 1 ANTI-ESMAGAMENTO
        // =========================================================================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "RECORDES PESSOAIS EM PROVAS (PRs)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OfficialPrHighlightCard(
                    title = "MELHOR TEMPO 5K",
                    mainValue = prSummary.best5kRace?.let { formatRaceDurationMmSs(it.officialTimeSec) } ?: "--:--",
                    subValue = prSummary.best5kRace?.let {
                        "${it.raceName} • Pace ${formatPaceMinSecQuote(it.avgPaceSecPerKm)}"
                    } ?: "Registre sua 1ª prova 5K",
                    accentColor = GoldAccent,
                    modifier = Modifier.weight(1f)
                )

                OfficialPrHighlightCard(
                    title = "PACE CORRENDO",
                    mainValue = prSummary.bestRunningPaceRace?.let {
                        "${formatPaceMinSecQuote(it.runningPaceSecPerKm)}/km"
                    } ?: "--'--\"",
                    subValue = prSummary.bestRunningPaceRace?.let {
                        "Split PR: ${formatPaceMinSecQuote(it.bestSplitPaceSecPerKm)}/km"
                    } ?: "Extraído dos sprints",
                    accentColor = CyanBrand,
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, EmeraldDelta.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            text = "MAIOR CONSTÂNCIA (% SEM CAMINHAR)",
                            color = EmeraldDelta,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = prSummary.bestConstancyRace?.let {
                                "Correndo: ${formatRaceDurationMmSs(it.runningTimeSec)} • Caminhada: ${formatRaceDurationMmSs(it.walkingTimeSec)}"
                            } ?: "Mede a evolução da resistência contínua",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = prSummary.bestConstancyRace?.let {
                            String.format(ptBr, "%.1f%%", it.constancyRunningPct)
                        } ?: "--%",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // =========================================================================
        // SEÇÃO 2: MOTOR DE COMPARAÇÃO DIRETA ENTRE DUAS PROVAS (MODO "VS")
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            border = BorderStroke(1.2.dp, CyanBrand.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = CyanBrand,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "COMPARATIVO DIRETO (MODO VS)",
                                color = CyanBrand,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Prova Base (A) vs. Prova Atual (B)",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (sortedRaces.size == 1) {
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                previewSimulatedTargetVs = !previewSimulatedTargetVs
                            },
                            color = if (previewSimulatedTargetVs) GoldAccent.copy(alpha = 0.2f) else CyanBrand.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (previewSimulatedTargetVs) GoldAccent else CyanBrand.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = if (previewSimulatedTargetVs) "Ocultar Meta" else "Simular 5K",
                                color = if (previewSimulatedTargetVs) GoldAccent else CyanBrand,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (sortedRaces.size >= 2) {
                    RaceVsSelectorRow(
                        label = "PROVA A (BASE / ANTERIOR):",
                        races = sortedRaces,
                        selectedRaceId = olderRace?.id,
                        onSelectRace = { id ->
                            selectedOlderRaceId = id
                            if (selectedNewerRaceId == id) {
                                selectedNewerRaceId = sortedRaces.firstOrNull { it.id != id }?.id
                            }
                            AudioSynth.playTickSound()
                        }
                    )
                    RaceVsSelectorRow(
                        label = "PROVA B (COMPARADA / MAIS RECENTE):",
                        races = sortedRaces,
                        selectedRaceId = newerRace?.id,
                        onSelectRace = { id ->
                            selectedNewerRaceId = id
                            if (selectedOlderRaceId == id) {
                                selectedOlderRaceId = sortedRaces.firstOrNull { it.id != id }?.id
                            }
                            AudioSynth.playTickSound()
                        }
                    )
                }

                if (comparisonResult != null) {
                    OfficialRaceVsComparisonContent(
                        comparison = comparisonResult,
                        dateFormat = dateFormat
                    )
                } else {
                    Surface(
                        color = DarkVaultBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (sortedRaces.isEmpty()) {
                                    "Salve sua 1ª prova oficial (ex: Prova 5K de 5,33 km em 40:18) para inaugurar sua linha de base!"
                                } else {
                                    "Sua 1ª prova (${sortedRaces.first().raceName}) já está salva como base! Quando correr a próxima (ex: 4ª Corrida BPTRAN ou V Corrida Anime Club), salve-a para ver os deltas ou toque em 'Simular 5K' acima."
                                },
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // SEÇÃO 2.5: CONVERSÃO EM 1 TOQUE A PARTIR DO HISTÓRICO GPS RECENTE
        // =========================================================================
        if (unconvertedSessions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                border = BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CONVERTER DO HISTÓRICO GPS EM 1 TOQUE",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Toque em uma sessão abaixo para pré-preencher automaticamente:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        unconvertedSessions.forEach { sess ->
                            val distKm = (sess.mainRunDistanceMeters?.takeIf { it > 0 } ?: sess.distanceMeters) / 1000.0
                            val durSec = sess.mainRunDurationSec?.takeIf { it > 0 } ?: sess.durationSec
                            val dtStr = dateFormat.format(Date(sess.date))
                            Surface(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    sessionToConvert = sess
                                },
                                color = DarkVaultBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = String.format(ptBr, "%.2f km • %s", distKm, formatRaceDurationMmSs(durSec)),
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Text(
                                        text = "$dtStr • Salvar em Provas",
                                        color = GoldAccent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // SEÇÃO 3: LISTA CRONOLÓGICA DE PROVAS OFICIAIS SALVAS
        // =========================================================================
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HISTÓRICO DE PROVAS (${sortedRaces.size})",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (sortedRaces.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    border = BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Nenhuma prova oficial salva ainda",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Use o botão 'Prova' nas suas corridas ou toque em 'Cadastrar Prova Oficial' acima.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                sortedRaces.forEachIndexed { index, race ->
                    val linkedSession = remember(race.linkedRunSessionId, runningSessions) {
                        runningSessions.firstOrNull { it.id == race.linkedRunSessionId }
                    }
                    OfficialRaceChronologicalCard(
                        race = race,
                        rankOrderNumber = sortedRaces.size - index,
                        isSelectedInVs = (race.id == olderRace?.id || race.id == newerRace?.id) && sortedRaces.size >= 2,
                        hasLinkedGpsSession = linkedSession != null && onOpenLinkedSession != null,
                        onSelectForVs = {
                            AudioSynth.playTickSound()
                            if (selectedNewerRaceId != race.id) {
                                selectedOlderRaceId = selectedNewerRaceId
                                selectedNewerRaceId = race.id
                            }
                            Toast.makeText(
                                context,
                                "Selecionada no Comparativo VS: ${race.raceName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onEdit = {
                            AudioSynth.playTickSound()
                            raceToEdit = race
                        },
                        onDelete = {
                            AudioSynth.playTickSound()
                            raceToDeleteConfirm = race
                        },
                        onOpenLinkedSession = {
                            if (linkedSession != null && onOpenLinkedSession != null) {
                                AudioSynth.playTickSound()
                                onOpenLinkedSession(linkedSession)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showManualCreateModal || raceToEdit != null || sessionToConvert != null) {
        SaveOfficialRaceDialog(
            initialSession = sessionToConvert,
            existingRecord = raceToEdit,
            userProfile = userProfile,
            availableShoes = runningShoes,
            onSave = { savedRecord ->
                onSaveRace(savedRecord)
                showManualCreateModal = false
                raceToEdit = null
                sessionToConvert = null
                previewSimulatedTargetVs = false
                Toast.makeText(
                    context,
                    "Prova oficial salva: ${savedRecord.raceName}!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showManualCreateModal = false
                raceToEdit = null
                sessionToConvert = null
            }
        )
    }

    if (raceToDeleteConfirm != null) {
        val target = raceToDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { raceToDeleteConfirm = null },
            containerColor = DarkCardBg,
            title = {
                Text(
                    text = "Excluir Prova Oficial?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja remover '${target.raceName}' da lista de Provas & Evolução? (O treino no histórico geral de corridas não será apagado).",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AudioSynth.playTickSound()
                        onDeleteRace(target.id)
                        raceToDeleteConfirm = null
                    }
                ) {
                    Text("Excluir", color = CoralDelta, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { raceToDeleteConfirm = null }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    if (showAddUpcomingModal) {
        AddUpcomingRaceDialog(
            initialRace = upcomingRaceToEdit,
            availableShoes = runningShoes,
            onSave = { event ->
                onSaveUpcomingRace?.invoke(event)
                showAddUpcomingModal = false
                upcomingRaceToEdit = null
                android.widget.Toast.makeText(
                    context,
                    "Prova '${event.raceName}' agendada! Lembretes ativados.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showAddUpcomingModal = false
                upcomingRaceToEdit = null
            }
        )
    }
}

/**
 * Modal Fullscreen de "Provas & Evolução" (mantido para acesso rápido a partir do Histórico/Detalhes).
 */
@Composable
fun OfficialRacesVaultDialog(
    officialRaces: List<OfficialRaceRecord>,
    runningSessions: List<RunningSession>,
    runningShoes: List<RunningShoe>,
    userProfile: UserProfileData?,
    onSaveRace: (OfficialRaceRecord) -> Unit,
    onDeleteRace: (String) -> Unit,
    onOpenLinkedSession: ((RunningSession) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkVaultBg)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = DarkVaultBg
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = DarkCardBg,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = GoldAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PROVAS & EVOLUÇÃO",
                                        color = GoldAccent,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "5K • 10K • TAF • Comparativo VS",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RunFontScaleToggleButton(compact = true)
                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    OfficialRacesPlanningSubTabContent(
                        officialRaces = officialRaces,
                        runningSessions = runningSessions,
                        runningShoes = runningShoes,
                        userProfile = userProfile,
                        onSaveRace = onSaveRace,
                        onDeleteRace = onDeleteRace,
                        onOpenLinkedSession = onOpenLinkedSession,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialPrHighlightCard(
    title: String,
    mainValue: String,
    subValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = accentColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mainValue,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RaceVsSelectorRow(
    label: String,
    races: List<OfficialRaceRecord>,
    selectedRaceId: String?,
    onSelectRace: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.forLanguageTag("pt-BR")) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            races.forEach { r ->
                val isSelected = r.id == selectedRaceId
                Surface(
                    onClick = { onSelectRace(r.id) },
                    color = if (isSelected) CyanBrand.copy(alpha = 0.22f) else DarkVaultBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) CyanBrand else DarkCardBorder
                    )
                ) {
                    Text(
                        text = "${r.raceName} (${dateFormat.format(Date(r.dateMillis))} • ${formatRaceDurationMmSs(r.officialTimeSec)})",
                        color = if (isSelected) CyanBrand else Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialRaceVsComparisonContent(
    comparison: OfficialRaceComparisonResult,
    dateFormat: SimpleDateFormat
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val older = comparison.olderRace
    val newer = comparison.newerRace

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkVaultBg, RoundedCornerShape(12.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PROVA A (BASE)",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = older.raceName,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${dateFormat.format(Date(older.dateMillis))} • ${String.format(ptBr, "%.2f km", older.actualDistanceKm)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Surface(
                color = GoldAccent.copy(alpha = 0.18f),
                shape = CircleShape,
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Text(
                    text = "VS",
                    color = GoldAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "PROVA B (ATUAL)",
                    color = CyanBrand,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = newer.raceName,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "${dateFormat.format(Date(newer.dateMillis))} • ${String.format(ptBr, "%.2f km", newer.actualDistanceKm)}",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End
                )
            }
        }

        val timeFaster = comparison.totalTimeDeltaSec <= 0
        val timeDeltaBadgeText = when {
            comparison.totalTimeDeltaSec < 0 ->
                formatSignedDeltaDuration(comparison.totalTimeDeltaSec, "Mais rápido!")
            comparison.totalTimeDeltaSec == 0 ->
                "00:00 (Mesmo tempo oficial)"
            else ->
                formatSignedDeltaDuration(comparison.totalTimeDeltaSec, "Mais lento")
        }
        VsMetricDeltaRow(
            metricTitle = "Δ Tempo Total Oficial",
            valuePairText = "${formatRaceDurationMmSs(older.officialTimeSec)} -> ${formatRaceDurationMmSs(newer.officialTimeSec)}",
            deltaBadgeText = timeDeltaBadgeText,
            isPositiveEvolution = timeFaster
        )

        val runPaceFaster = comparison.runningPaceDeltaSec <= 0
        val avgSign = if (comparison.avgPaceDeltaSec > 0) "+${comparison.avgPaceDeltaSec}s/km" else "${comparison.avgPaceDeltaSec}s/km"
        val runSign = if (comparison.runningPaceDeltaSec > 0) "+${comparison.runningPaceDeltaSec}s/km" else "${comparison.runningPaceDeltaSec}s/km"
        VsMetricDeltaRow(
            metricTitle = "Δ Pace Médio & Pace Correndo",
            valuePairText = "Global: ${formatPaceMinSecQuote(older.avgPaceSecPerKm)} -> ${formatPaceMinSecQuote(newer.avgPaceSecPerKm)} | Correndo: ${formatPaceMinSecQuote(older.runningPaceSecPerKm)} -> ${formatPaceMinSecQuote(newer.runningPaceSecPerKm)}",
            deltaBadgeText = "Global $avgSign • Correndo $runSign",
            isPositiveEvolution = runPaceFaster
        )

        val walkedLess = comparison.walkingTimeDeltaSec <= 0
        val walkDeltaText = when {
            comparison.walkingTimeDeltaSec < 0 ->
                "${formatSignedDeltaDuration(comparison.walkingTimeDeltaSec, "")} caminhando (Mais contínuo!)"
            comparison.walkingTimeDeltaSec == 0 ->
                "00:00 variação (${String.format(ptBr, "%.0f%%", newer.constancyRunningPct)} correndo)"
            else ->
                "${formatSignedDeltaDuration(comparison.walkingTimeDeltaSec, "")} de caminhada"
        }
        VsMetricDeltaRow(
            metricTitle = "Δ Constância (Tempo Caminhando)",
            valuePairText = "Caminhada: ${formatRaceDurationMmSs(older.walkingTimeSec)} (${String.format(ptBr, "%.0f%%", older.constancyRunningPct)} corr.) -> ${formatRaceDurationMmSs(newer.walkingTimeSec)} (${String.format(ptBr, "%.0f%%", newer.constancyRunningPct)} corr.)",
            deltaBadgeText = walkDeltaText,
            isPositiveEvolution = walkedLess
        )

        val hrDeltaSign = if (comparison.avgHrDeltaBpm >= 0) "+${comparison.avgHrDeltaBpm}" else "${comparison.avgHrDeltaBpm}"
        VsMetricDeltaRow(
            metricTitle = "Δ Eficiência Cardíaca (FC Média / Máx)",
            valuePairText = "${older.avgHeartRate}/${older.maxHeartRate} bpm -> ${newer.avgHeartRate}/${newer.maxHeartRate} bpm ($hrDeltaSign bpm)\n${comparison.cardiacEfficiencyVerdict}",
            deltaBadgeText = if (comparison.cardiacEfficiencyIsPositive) "Eficiência Cardíaca Positiva" else "Maior Custo Cardíaco",
            isPositiveEvolution = comparison.cardiacEfficiencyIsPositive
        )

        val cadSign = if (comparison.avgCadenceDeltaSpm >= 0) "+${comparison.avgCadenceDeltaSpm}" else "${comparison.avgCadenceDeltaSpm}"
        VsMetricDeltaRow(
            metricTitle = "Δ Cadência & Calçado",
            valuePairText = "${older.avgCadenceSpm} ppm (${older.shoeUsed}) -> ${newer.avgCadenceSpm} ppm (${newer.shoeUsed})\n${comparison.shoeMechanicalVerdict}",
            deltaBadgeText = "$cadSign ppm • Pico ${newer.maxCadenceSpm} ppm",
            isPositiveEvolution = comparison.shoeMechanicalIsPositive
        )
    }
}

@Composable
private fun VsMetricDeltaRow(
    metricTitle: String,
    valuePairText: String,
    deltaBadgeText: String,
    isPositiveEvolution: Boolean
) {
    val badgeColor = if (isPositiveEvolution) EmeraldDelta else CoralDelta
    Surface(
        color = DarkVaultBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = metricTitle,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = deltaBadgeText,
                    color = badgeColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(
                text = valuePairText,
                color = Color(0xFFCBD5E1),
                fontSize = 10.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfficialRaceChronologicalCard(
    race: OfficialRaceRecord,
    rankOrderNumber: Int,
    isSelectedInVs: Boolean,
    hasLinkedGpsSession: Boolean,
    onSelectForVs: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenLinkedSession: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateStr = remember(race.dateMillis) {
        SimpleDateFormat("dd/MM/yy", ptBr).format(Date(race.dateMillis))
    }
    val runFraction = (race.constancyRunningPct / 100.0).toFloat().coerceIn(0.05f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(
            width = if (isSelectedInVs) 1.5.dp else 1.dp,
            color = if (isSelectedInVs) CyanBrand else GoldAccent.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Surface(
                        color = GoldAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#$rankOrderNumber • ${race.categoryBadgeLabel}",
                            color = GoldAccent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = race.raceName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = dateStr,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Grade 2x2 Compacta (100% imune a corte ou esmagamento no Motorola G84)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkVaultBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TEMPO OFICIAL",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = formatRaceDurationMmSs(race.officialTimeSec),
                            color = GoldAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "DISTÂNCIA REAL",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = String.format(ptBr, "%.2f km", race.actualDistanceKm),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                HorizontalDivider(color = DarkCardBorder, thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PACE GLOBAL",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${formatPaceMinSecQuote(race.avgPaceSecPerKm)}/km",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "PACE CORRENDO",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "${formatPaceMinSecQuote(race.runningPaceSecPerKm)}/km",
                            color = CyanBrand,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Barra de Proporção Correndo vs. Caminhando com Ícones Vetoriais (Zero Emojis)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = EmeraldDelta,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Corr: ${formatRaceDurationMmSs(race.runningTimeSec)} (${String.format(ptBr, "%.0f%%", race.constancyRunningPct)})",
                            color = EmeraldDelta,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Cam: ${formatRaceDurationMmSs(race.walkingTimeSec)} (${String.format(ptBr, "%.0f%%", 100.0 - race.constancyRunningPct)})",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(runFraction)
                            .background(EmeraldDelta)
                    )
                    if (runFraction < 0.995f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((1f - runFraction).coerceAtLeast(0.01f))
                                .background(Color(0xFF38BDF8))
                        )
                    }
                }
            }

            // Chips Fisiológicos & Mecânicos com Ícones Vetoriais (Zero Emojis)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Split PR: ${formatPaceMinSecQuote(race.bestSplitPaceSecPerKm)}/km",
                        color = GoldAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF7043),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (race.avgHeartRate > 0) "FC: ${race.avgHeartRate}/${race.maxHeartRate} bpm" else "FC: -- bpm",
                        color = Color(0xFFFF7043),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Cad: ${race.avgCadenceSpm}/${race.maxCadenceSpm} ppm",
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Calçado + Observações (Zero Emojis)
            Surface(
                color = DarkVaultBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Tênis: ${race.shoeUsed}",
                        color = CyanBrand,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (race.placementNotes.isNotBlank()) {
                        Text(
                            text = "Obs: ${race.placementNotes}",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Ações do Card: Comparar VS | Ver GPS | Editar | Excluir
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = onSelectForVs,
                        color = if (isSelectedInVs) CyanBrand.copy(alpha = 0.22f) else DarkVaultBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = null,
                                tint = CyanBrand,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isSelectedInVs) "No Modo VS" else "Comparar VS",
                                color = CyanBrand,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (hasLinkedGpsSession) {
                        Surface(
                            onClick = onOpenLinkedSession,
                            color = DarkVaultBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Text(
                                text = "Ver GPS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkVaultBg, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Prova",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(30.dp)
                            .background(DarkVaultBg, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir Prova",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modal de Cadastro Manual ou Conversão em 1 Toque ("Salvar em Provas Oficiais")
 * dimensionado em grades de no máximo 2 colunas para telas compactas (Motorola G84).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveOfficialRaceDialog(
    initialSession: RunningSession? = null,
    existingRecord: OfficialRaceRecord? = null,
    userProfile: UserProfileData? = null,
    availableShoes: List<RunningShoe> = emptyList(),
    onSave: (OfficialRaceRecord) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", ptBr) }

    val seedRecord = remember(initialSession, existingRecord, userProfile) {
        existingRecord ?: initialSession?.let {
            buildOfficialRaceRecordFromSession(
                session = it,
                userProfile = userProfile
            )
        }
    }

    var raceNameInput by remember(seedRecord) {
        mutableStateOf(seedRecord?.raceName ?: "4ª Corrida BPTRAN")
    }
    var selectedCategoryKm by remember(seedRecord) {
        mutableDoubleStateOf(seedRecord?.categoryDistanceKm ?: 5.0)
    }
    var actualDistanceKmInput by remember(seedRecord) {
        mutableStateOf(
            String.format(Locale.US, "%.2f", seedRecord?.actualDistanceKm ?: 5.0)
        )
    }
    var dateStrInput by remember(seedRecord) {
        mutableStateOf(
            dateFormat.format(Date(seedRecord?.dateMillis ?: System.currentTimeMillis()))
        )
    }
    var officialTimeInput by remember(seedRecord) {
        mutableStateOf(formatRaceDurationMmSs(seedRecord?.officialTimeSec ?: 2418))
    }
    var runningTimeInput by remember(seedRecord) {
        mutableStateOf(formatRaceDurationMmSs(seedRecord?.runningTimeSec ?: 1860))
    }
    var walkingTimeInput by remember(seedRecord) {
        mutableStateOf(formatRaceDurationMmSs(seedRecord?.walkingTimeSec ?: 558))
    }
    var runningPaceInput by remember(seedRecord) {
        mutableStateOf(
            seedRecord?.let { formatPaceMinSecQuote(it.runningPaceSecPerKm) } ?: "6'37\""
        )
    }
    var bestSplitPaceInput by remember(seedRecord) {
        mutableStateOf(
            seedRecord?.let { formatPaceMinSecQuote(it.bestSplitPaceSecPerKm) } ?: "6'10\""
        )
    }
    var avgHrInput by remember(seedRecord) {
        mutableStateOf((seedRecord?.avgHeartRate?.takeIf { it > 0 } ?: 161).toString())
    }
    var maxHrInput by remember(seedRecord) {
        mutableStateOf((seedRecord?.maxHeartRate?.takeIf { it > 0 } ?: 176).toString())
    }
    var avgCadenceInput by remember(seedRecord) {
        mutableStateOf((seedRecord?.avgCadenceSpm?.takeIf { it > 0 } ?: 160).toString())
    }
    var maxCadenceInput by remember(seedRecord) {
        mutableStateOf((seedRecord?.maxCadenceSpm?.takeIf { it > 0 } ?: 172).toString())
    }
    var shoeUsedInput by remember(seedRecord) {
        mutableStateOf(seedRecord?.shoeUsed ?: "Olympikus Corre 4 (42 BR)")
    }
    var placementNotesInput by remember(seedRecord) {
        mutableStateOf(seedRecord?.placementNotes ?: "")
    }

    val quickRaceNames = listOf(
        "4ª Corrida BPTRAN",
        "V Corrida Anime Club",
        "Prova 5K Rio Branco",
        "Simulado TAF 2.4K"
    )

    val categoryOptions = listOf(
        5.0 to "5K",
        10.0 to "10K",
        2.4 to "TAF 2.4K",
        21.1 to "21K",
        0.0 to "Livre"
    )

    val shoeQuickOptions = remember(availableShoes) {
        val core = mutableListOf(
            "Asics Novablast 5 (43 BR)",
            "Olympikus Corre 4 (42 BR)"
        )
        availableShoes.filter { !it.isRetired }.forEach { s ->
            val display = s.fullDisplayName()
            if (core.none { it.contains(s.name, ignoreCase = true) }) {
                core.add(display)
            }
        }
        core
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = 10.dp),
        containerColor = DarkCardBg,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when {
                            existingRecord != null -> "Editar Prova Oficial"
                            initialSession != null -> "Salvar em Provas Oficiais"
                            else -> "Cadastrar Prova Oficial"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialSession != null) {
                    Surface(
                        color = EmeraldDelta.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldDelta.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldDelta,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Métricas reais extraídas do GPS e cinta cardíaca. Confirme o nome da prova, categoria e tênis.",
                                color = EmeraldDelta,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = "Nome da Prova Oficial:",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickRaceNames.forEach { presetName ->
                        val selected = raceNameInput.equals(presetName, ignoreCase = true)
                        Surface(
                            onClick = {
                                raceNameInput = presetName
                                AudioSynth.playTickSound()
                            },
                            color = if (selected) GoldAccent.copy(alpha = 0.22f) else DarkVaultBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) GoldAccent else DarkCardBorder)
                        ) {
                            Text(
                                text = presetName,
                                color = if (selected) GoldAccent else Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = raceNameInput,
                    onValueChange = { raceNameInput = it },
                    label = { Text("Nome da Prova", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Text(
                    text = "Categoria da Prova:",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryOptions.forEach { (kmVal, label) ->
                        val isSelected = abs(selectedCategoryKm - kmVal) < 0.15
                        Surface(
                            onClick = {
                                selectedCategoryKm = kmVal
                                if (initialSession == null && existingRecord == null && kmVal > 0.0) {
                                    actualDistanceKmInput = String.format(Locale.US, "%.2f", kmVal)
                                }
                                AudioSynth.playTickSound()
                            },
                            color = if (isSelected) CyanBrand.copy(alpha = 0.22f) else DarkVaultBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) CyanBrand else DarkCardBorder
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CyanBrand else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Tênis Utilizado na Prova:",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shoeQuickOptions.forEach { shoeLabel ->
                        val isSelected = shoeUsedInput.equals(shoeLabel, ignoreCase = true)
                        Surface(
                            onClick = {
                                shoeUsedInput = shoeLabel
                                AudioSynth.playTickSound()
                            },
                            color = if (isSelected) GoldAccent.copy(alpha = 0.22f) else DarkVaultBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) GoldAccent else DarkCardBorder)
                        ) {
                            Text(
                                text = shoeLabel,
                                color = if (isSelected) GoldAccent else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = shoeUsedInput,
                    onValueChange = { shoeUsedInput = it },
                    label = { Text("Calçado (editável)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = actualDistanceKmInput,
                        onValueChange = { actualDistanceKmInput = it },
                        label = { Text("Dist. Real (km)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = officialTimeInput,
                        onValueChange = { officialTimeInput = it },
                        label = { Text("Tempo (MM:SS)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = runningTimeInput,
                        onValueChange = { runningTimeInput = it },
                        label = { Text("Correndo (MM:SS)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldDelta,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = walkingTimeInput,
                        onValueChange = { walkingTimeInput = it },
                        label = { Text("Caminhando (MM:SS)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = runningPaceInput,
                        onValueChange = { runningPaceInput = it },
                        label = { Text("Pace Correndo", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = bestSplitPaceInput,
                        onValueChange = { bestSplitPaceInput = it },
                        label = { Text("Melhor Parcial", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = avgHrInput,
                        onValueChange = { avgHrInput = it },
                        label = { Text("FC Média (bpm)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = maxHrInput,
                        onValueChange = { maxHrInput = it },
                        label = { Text("FC Máxima (bpm)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = avgCadenceInput,
                        onValueChange = { avgCadenceInput = it },
                        label = { Text("Cadência Méd (ppm)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = maxCadenceInput,
                        onValueChange = { maxCadenceInput = it },
                        label = { Text("Cadência Máx (ppm)", color = Color(0xFF94A3B8), maxLines = 1) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                OutlinedTextField(
                    value = dateStrInput,
                    onValueChange = { dateStrInput = it },
                    label = { Text("Data da Prova (dd/mm/aaaa)", color = Color(0xFF94A3B8), maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = placementNotesInput,
                    onValueChange = { placementNotesInput = it },
                    label = { Text("Colocação / Clima / Observações", color = Color(0xFF94A3B8), maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingRecord != null && onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            onDelete(existingRecord.id)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text(
                            text = "Desmarcar",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Text(
                            text = "Cancelar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        val actualKm = actualDistanceKmInput.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.1)
                            ?: seedRecord?.actualDistanceKm
                            ?: 5.0
                        val officialSec = parseMmSsInputToSeconds(
                            officialTimeInput,
                            seedRecord?.officialTimeSec ?: 2418
                        ).coerceAtLeast(1)
                        val runSecRaw = parseMmSsInputToSeconds(
                            runningTimeInput,
                            seedRecord?.runningTimeSec ?: officialSec
                        ).coerceIn(0, officialSec)
                        val walkSecRaw = parseMmSsInputToSeconds(
                            walkingTimeInput,
                            (officialSec - runSecRaw).coerceAtLeast(0)
                        ).coerceAtLeast(0)

                        val computedAvgPace = if (actualKm > 0.0) {
                            (officialSec / actualKm).roundToInt()
                        } else {
                            seedRecord?.avgPaceSecPerKm ?: 450
                        }
                        val runPaceSec = parsePaceInputToSecPerKm(
                            runningPaceInput,
                            seedRecord?.runningPaceSecPerKm ?: computedAvgPace
                        )
                        val bestSplitSec = parsePaceInputToSecPerKm(
                            bestSplitPaceInput,
                            seedRecord?.bestSplitPaceSecPerKm ?: runPaceSec
                        )
                        val avgHr = avgHrInput.trim().toIntOrNull()?.coerceIn(0, 240) ?: (seedRecord?.avgHeartRate ?: 0)
                        val maxHr = maxHrInput.trim().toIntOrNull()?.coerceIn(0, 245) ?: (seedRecord?.maxHeartRate ?: avgHr)
                        val avgCad = avgCadenceInput.trim().toIntOrNull()?.coerceIn(80, 220) ?: (seedRecord?.avgCadenceSpm ?: 160)
                        val maxCad = maxCadenceInput.trim().toIntOrNull()?.coerceIn(80, 235) ?: (seedRecord?.maxCadenceSpm ?: (avgCad + 12))

                        val parsedDateMillis = runCatching {
                            dateFormat.parse(dateStrInput.trim())?.time
                        }.getOrNull() ?: seedRecord?.dateMillis ?: System.currentTimeMillis()

                        val finalRecord = OfficialRaceRecord(
                            id = existingRecord?.id ?: seedRecord?.id ?: UUID.randomUUID().toString(),
                            linkedRunSessionId = existingRecord?.linkedRunSessionId ?: initialSession?.id ?: seedRecord?.linkedRunSessionId,
                            raceName = raceNameInput.trim().ifBlank { "Prova Oficial 5K" },
                            dateMillis = parsedDateMillis,
                            categoryDistanceKm = selectedCategoryKm,
                            actualDistanceKm = actualKm,
                            officialTimeSec = officialSec,
                            runningTimeSec = runSecRaw,
                            walkingTimeSec = walkSecRaw,
                            avgPaceSecPerKm = computedAvgPace,
                            runningPaceSecPerKm = runPaceSec,
                            bestSplitPaceSecPerKm = bestSplitSec,
                            avgHeartRate = avgHr,
                            maxHeartRate = maxHr,
                            avgCadenceSpm = avgCad,
                            maxCadenceSpm = maxCad,
                            shoeUsed = shoeUsedInput.trim().ifBlank { "Olympikus Corre 4 (42 BR)" },
                            placementNotes = placementNotesInput.trim()
                        )
                        onSave(finalRecord)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_save_official_race"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SALVAR PROVA",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    )
}

// ================================================================================================
// SEÇÃO: AGENDA DE PRÓXIMAS PROVAS + CONTAGEM REGRESSIVA + LEMBRETES AUTOMÁTICOS
// ================================================================================================

@Composable
fun UpcomingRacesAgendaSection(
    upcomingRaces: List<com.example.data.model.UpcomingRaceEvent>,
    onAddClick: () -> Unit,
    onEditRace: (com.example.data.model.UpcomingRaceEvent) -> Unit,
    onDeleteRace: (String) -> Unit,
    onLinkResult: (com.example.data.model.UpcomingRaceEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = CyanBrand,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "AGENDA DE PRÓXIMAS PROVAS",
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                onClick = onAddClick,
                color = CyanBrand.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CyanBrand.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("btn_schedule_upcoming_race")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CyanBrand,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "+ Agendar Prova",
                        color = CyanBrand,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (upcomingRaces.isEmpty()) {
            Surface(
                color = DarkCardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = CyanBrand,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nenhuma prova agendada",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Agende suas corridas oficiais futuras para ativar contagem regressiva e lembretes automáticos.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcomingRaces.forEach { race ->
                    UpcomingRaceCardItem(
                        race = race,
                        onEdit = { onEditRace(race) },
                        onDelete = { onDeleteRace(race.id) },
                        onLinkResult = { onLinkResult(race) }
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingRaceCardItem(
    race: com.example.data.model.UpcomingRaceEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLinkResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { SimpleDateFormat("dd 'de' MMMM 'de' yyyy", ptBr) }
    val dateStr = remember(race.raceDateMillis) { dateFormat.format(Date(race.raceDateMillis)) }
    val days = race.daysRemaining

    val countdownColor = when {
        days == 0 -> Color(0xFF00E676)
        days in 1..2 -> Color(0xFFFF5252)
        days in 3..14 -> GoldAccent
        else -> CyanBrand
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Linha 1: Título da Prova + Categoria Badge + Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        color = GoldAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, GoldAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = race.distanceCategory,
                            color = GoldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = race.raceName,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Prova Agendada",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Excluir da Agenda",
                            tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Linha 2: Data e Badge Dinâmico de Contagem Regressiva
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = dateStr,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = countdownColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, countdownColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = if (days == 0) "HOJE É O DIA!" else "FALTAM $days DIAS",
                        color = countdownColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Linha 3: Card de Status Fisiológico da Fase
            Surface(
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, countdownColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = countdownColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = race.physiologicalPhaseText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Linha 4: Tênis planejado + Meta de Pace + Botão Concluir/Vincular
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "Tênis: ${race.plannedShoe} • Meta: ${race.targetPace}",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (race.notifyOneWeekBefore || race.notifyOneDayBefore) {
                        Text(
                            text = "Lembretes automáticos ativados (7 dias / véspera)",
                            color = CyanBrand.copy(alpha = 0.85f),
                            fontSize = 9.5.sp
                        )
                    }
                }

                Surface(
                    onClick = onLinkResult,
                    color = GoldAccent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Concluir / Vincular",
                            color = GoldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddUpcomingRaceDialog(
    initialRace: com.example.data.model.UpcomingRaceEvent? = null,
    availableShoes: List<RunningShoe> = emptyList(),
    onSave: (com.example.data.model.UpcomingRaceEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", ptBr) }

    var raceNameInput by remember(initialRace) {
        mutableStateOf(initialRace?.raceName ?: "4ª Corrida BPTRAN")
    }
    var selectedCategory by remember(initialRace) {
        mutableStateOf(initialRace?.distanceCategory ?: "5K")
    }
    var targetPaceInput by remember(initialRace) {
        mutableStateOf(initialRace?.targetPace ?: "5'10\"/km")
    }
    var plannedShoeInput by remember(initialRace, availableShoes) {
        val fallback = availableShoes.firstOrNull { it.isDefault }?.fullDisplayName()
            ?: availableShoes.firstOrNull()?.fullDisplayName()
            ?: "Olympikus Corre 4 (42 BR)"
        mutableStateOf(initialRace?.plannedShoe ?: fallback)
    }
    var dateInput by remember(initialRace) {
        val initialDate = initialRace?.raceDateMillis ?: (System.currentTimeMillis() + 18L * 86_400_000L)
        mutableStateOf(dateFormat.format(Date(initialDate)))
    }
    var notesInput by remember(initialRace) {
        mutableStateOf(initialRace?.notes ?: "")
    }
    var notify7Days by remember(initialRace) {
        mutableStateOf(initialRace?.notifyOneWeekBefore ?: true)
    }
    var notify1Day by remember(initialRace) {
        mutableStateOf(initialRace?.notifyOneDayBefore ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCardBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = CyanBrand,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (initialRace != null) "Editar Prova Agendada" else "Agendar Próxima Prova",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = raceNameInput,
                    onValueChange = { raceNameInput = it },
                    label = { Text("Nome da Prova Oficial", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Categoria de Distância
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("TAF", "5K", "10K", "21K").forEach { cat ->
                        val selected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            color = if (selected) GoldAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) GoldAccent else DarkCardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = cat,
                                color = if (selected) GoldAccent else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Data da Prova (DD/MM/AAAA)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetPaceInput,
                        onValueChange = { targetPaceInput = it },
                        label = { Text("Meta de Pace", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = plannedShoeInput,
                        onValueChange = { plannedShoeInput = it },
                        label = { Text("Tênis Planejado", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanBrand,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Observações / Estratégia de Prova", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanBrand,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Toggles de Notificações
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lembrete 1 semana antes (08:00)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Checkbox(
                            checked = notify7Days,
                            onCheckedChange = { notify7Days = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyanBrand,
                                checkmarkColor = Color.Black
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lembrete véspera da prova (18:00)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Checkbox(
                            checked = notify1Day,
                            onCheckedChange = { notify1Day = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CyanBrand,
                                checkmarkColor = Color.Black
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancelar", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val parsedMillis = runCatching {
                            dateFormat.parse(dateInput.trim())?.time
                        }.getOrNull() ?: (System.currentTimeMillis() + 18L * 86_400_000L)

                        val event = com.example.data.model.UpcomingRaceEvent(
                            id = initialRace?.id ?: UUID.randomUUID().toString(),
                            raceName = raceNameInput.trim().ifBlank { "Prova Oficial $selectedCategory" },
                            raceDateMillis = parsedMillis,
                            distanceCategory = selectedCategory,
                            targetPace = targetPaceInput.trim().ifBlank { "5'10\"/km" },
                            plannedShoe = plannedShoeInput.trim().ifBlank { "Olympikus Corre 4 (42 BR)" },
                            notes = notesInput.trim(),
                            notifyOneWeekBefore = notify7Days,
                            notifyOneDayBefore = notify1Day
                        )
                        onSave(event)
                    },
                    modifier = Modifier.weight(1.3f).heightIn(min = 46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanBrand,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (initialRace != null) "ATUALIZAR" else "AGENDAR",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    )
}

// ================================================================================================
// CARD: CONSOLIDAÇÃO DE CARGA HÍBRIDA & COACH TAF (MODO BLINDADO PRÉ-TAF — 100% READ-ONLY)
// ================================================================================================

@Composable
fun HybridLoadCoachTafCard(
    summary: com.example.engine.HybridLoadCoachSummary,
    onQuickSetTafDay: ((Int) -> Unit)? = null,
    onEditTargetRace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", ptBr) }
    val targetDateStr = remember(summary.targetRaceEvent.raceDateMillis) {
        dateFormat.format(Date(summary.targetRaceEvent.raceDateMillis))
    }
    val currentDayOfMonth = remember(summary.targetRaceEvent.raceDateMillis) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = summary.targetRaceEvent.raceDateMillis
        }
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    }

    val readinessColor = when (summary.lowerBodyReadinessLevel) {
        com.example.engine.LowerBodyReadinessLevel.ACUTE_FATIGUE -> CoralDelta
        com.example.engine.LowerBodyReadinessLevel.RECOVERING_24_48H -> GoldAccent
        com.example.engine.LowerBodyReadinessLevel.RECOVERED_OVER_48H -> EmeraldDelta
    }

    val phaseAccentColor = when (summary.tafPhase) {
        com.example.engine.TafCountdownPhase.RACE_DAY -> EmeraldDelta
        com.example.engine.TafCountdownPhase.SUPERCOMPENSATION_1_3D -> CoralDelta
        com.example.engine.TafCountdownPhase.TAPERING_4_7D -> GoldAccent
        com.example.engine.TafCountdownPhase.FINAL_SPECIFICITY_8_12D -> CyanBrand
        com.example.engine.TafCountdownPhase.BASE_BUILDING -> CyanBrand
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_hybrid_load_coach_taf"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.2.dp, phaseAccentColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabeçalho: Título + Badge de Contagem Regressiva TAF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(phaseAccentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = phaseAccentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CARGA HÍBRIDA & COACH TAF",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${summary.targetRaceEvent.raceName} • $targetDateStr",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = phaseAccentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, phaseAccentColor.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = if (summary.daysUntilTargetRace == 0) "HOJE!" else "FALTAM ${summary.daysUntilTargetRace} DIAS",
                        color = phaseAccentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Seletor rápido de 1 toque para data do TAF (22/11 vs 23/11) + Editar
            if (onQuickSetTafDay != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Data TAF:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(22, 23).forEach { dayNum ->
                        val isSelectedDay = currentDayOfMonth == dayNum
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                onQuickSetTafDay(dayNum)
                            },
                            color = if (isSelectedDay) CyanBrand.copy(alpha = 0.22f) else DarkVaultBg,
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelectedDay) CyanBrand else DarkCardBorder
                            ),
                            modifier = Modifier.testTag("btn_quick_taf_day_$dayNum")
                        ) {
                            Text(
                                text = "$dayNum/11",
                                color = if (isSelectedDay) Color.White else Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onEditTargetRace != null) {
                        Surface(
                            onClick = onEditTargetRace,
                            color = DarkVaultBg,
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, DarkCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar Meta TAF",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Editar",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 1. Barra Visual Comparativa: Carga Corrida (X UA) vs. Carga Musculação (Y UA) = Total Semanal: Z UA
            Surface(
                color = DarkVaultBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CARGA SEMANAL HÍBRIDA (7 DIAS)",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Total Semanal: ${summary.totalHybridLoadUa} UA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Barra proporcional Corrida vs Musculação
                    val runWeight = summary.runningSharePct.coerceIn(8, 92) / 100f
                    val gymWeight = (1f - runWeight).coerceIn(0.08f, 0.92f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E2433)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(runWeight)
                                .fillMaxHeight()
                                .background(CyanBrand)
                        )
                        Box(
                            modifier = Modifier
                                .weight(gymWeight)
                                .fillMaxHeight()
                                .background(GoldAccent)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(CyanBrand)
                            )
                            Text(
                                text = "Carga Corrida (${summary.runningWeeklyLoadUa} UA • ${summary.runningSharePct}%)",
                                color = CyanBrand,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(GoldAccent)
                            )
                            Text(
                                text = "Musculação (${summary.gymWeeklyLoadUa} UA • ${summary.gymSharePct}%)",
                                color = GoldAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 2. Status de Recuperação de Membros Inferiores (Janela 72h + Check-in de Dor Localizada)
            Surface(
                color = readinessColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, readinessColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = readinessColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "PRONTIDÃO DE MEMBROS INFERIORES (72H)",
                                color = readinessColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = if (summary.hoursSinceLastLowerBodyGym != null) {
                                "Há ${summary.hoursSinceLastLowerBodyGym}h (${summary.lastLowerBodySetsCount} séries)"
                            } else {
                                ">72h sem estímulo"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = summary.lowerBodyReadinessStatusText,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!summary.localizedPainImpactNote.isNullOrBlank()) {
                        Text(
                            text = "Alerta de Dor Localizada: ${summary.localizedPainImpactNote}",
                            color = CoralDelta,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Protocolo da Fase TAF & Diretriz do Coach para Hoje
            Surface(
                color = DarkVaultBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, phaseAccentColor.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = phaseAccentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = summary.tafPhase.badgeLabel,
                            color = phaseAccentColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = summary.tafProtocolText,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(color = DarkCardBorder, thickness = 0.8.dp)
                    Text(
                        text = "DIRETRIZ DO COACH PARA HOJE:",
                        color = CyanBrand,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = summary.todayCoachDirective,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        lineHeight = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun HybridCoachModalDialog(
    summary: com.example.engine.HybridLoadCoachSummary,
    coachVoiceEnabled: Boolean,
    onToggleCoachVoice: (Boolean) -> Unit,
    onQuickSetTafDay: (Int) -> Unit,
    onOpenPlanningTafTab: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkVaultBg,
            border = BorderStroke(1.2.dp, CyanBrand.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Topo do Modal: Título + Botão Fechar
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
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CyanBrand,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Coach Híbrido & Polimento TAF",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Consolidação Corrida + Musculação e Áudio",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Coach",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Card Principal: Consolidação de Carga Híbrida & Coach TAF
                HybridLoadCoachTafCard(
                    summary = summary,
                    onQuickSetTafDay = onQuickSetTafDay,
                    onEditTargetRace = {
                        onDismiss()
                        onOpenPlanningTafTab()
                    }
                )

                // Controle de Orientação de Voz do Coach durante a Corrida
                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Orientação de Voz durante a Corrida",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (coachVoiceEnabled) {
                                    "Ativada — avisos de blocos, pace e parciais"
                                } else {
                                    "Silenciada — apenas alertas visuais na tela"
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = coachVoiceEnabled,
                            onCheckedChange = onToggleCoachVoice
                        )
                    }
                }

                // Botões de Rodapé: Abrir Aba Planos > Provas & Evolução | Fechar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenPlanningTafTab()
                        },
                        modifier = Modifier.weight(1.2f).heightIn(min = 46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ver Agenda TAF",
                            color = GoldAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f).heightIn(min = 46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanBrand,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "FECHAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}


