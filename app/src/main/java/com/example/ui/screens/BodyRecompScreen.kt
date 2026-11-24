package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BioimpedanceIndicatorAssessment
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.IndicatorStatus
import com.example.data.model.PhysiologicalGoalProgress
import com.example.data.model.RecompClassification
import com.example.data.model.RecompDeltaItem
import com.example.data.model.RecompStepCardSummary
import com.example.data.model.UserProfileData
import com.example.data.model.calculateAutoBmiOrNull
import com.example.data.model.resolveValidHeightCmOrNull
import com.example.domain.recomp.BioimpedanceDetectedTemplate
import com.example.domain.recomp.BioimpedanceImageParser
import com.example.domain.recomp.BioimpedanceReferences
import com.example.domain.recomp.BodyRecompEngine
import com.example.utils.AudioSynth
import com.example.utils.BioimpedancePdfExporter
import com.example.utils.WeightCalculator
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val RecompBgDark = Color(0xFF0D0F14)
private val RecompCardBg = Color(0xFF151922)
private val RecompCardElevated = Color(0xFF1A202C)
private val RecompCardBorder = Color(0xFF262C3A)
private val RecompBlue = Color(0xFF00B0FF)
private val RecompCyan = Color(0xFF00E5FF)
private val RecompGreen = Color(0xFF00E676)
private val RecompAmber = Color(0xFFFFB300)
private val RecompRed = Color(0xFFFF5252)
private val RecompPrimaryText = Color(0xFFF8FAFC)
private val RecompSecondaryText = Color(0xFF94A3B8)

/**
 * 1. CARD DE ENTRADA NA ABA PERFIL (`BodyRecompProfileEntryCard`):
 * Posicionado em destaque na aba Perfil com título "MAX RECOMP • BIOIMPEDÂNCIA",
 * subtítulo "Análise Passo a Passo (Fitdays / Relaxmedic)", resumo ao vivo da última pesagem
 * (`84,7 kg • 23,0% BF • 61,8 kg Músculo`) e sincronização imediata de peso/TMB com o perfil global.
 */
@Composable
fun BodyRecompProfileEntryCard(
    userProfile: UserProfileData? = WeightCalculator.activeUserProfile,
    onWeightSyncedToProfile: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val activeProfile = userProfile ?: WeightCalculator.activeUserProfile
    var records by remember(activeProfile?.nome) { mutableStateOf(BodyRecompEngine.loadRecords(context, activeProfile)) }
    var showFullDossierModal by remember { mutableStateOf(false) }

    // buildRecompTimelineCards retorna em ordem reversa (mais recente no índice 0, Marco Zero no final)
    val timelineCards = remember(records, activeProfile) { BodyRecompEngine.buildRecompTimelineCards(records, activeProfile) }
    val latestSummary = timelineCards.firstOrNull()
    val latestRecord = latestSummary?.current ?: BodyRecompEngine.defaultFallbackRecord(activeProfile)
    val latestClassification = latestSummary?.recompClassification ?: RecompClassification.OURO_RECOMPOSICAO_PURA

    // Sincroniza automaticamente o peso atual com o perfil global apenas quando houver medição salva válida
    LaunchedEffect(records.size, latestRecord.weightKg) {
        if (records.isNotEmpty() && latestRecord.weightKg > 0f) {
            onWeightSyncedToProfile?.invoke(latestRecord.weightKg)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("body_recomp_profile_entry_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.45f))
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = RecompBlue.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MonitorWeight,
                                contentDescription = "Bioimpedância",
                                tint = RecompCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "MAX RECOMP • BIOIMPEDÂNCIA",
                            color = RecompCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Análise Passo a Passo (Fitdays / Relaxmedic)",
                            color = RecompSecondaryText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                RecompBadgePill(
                    text = latestClassification.badgeLabel,
                    accentColor = Color(latestClassification.accentColorHex)
                )
            }

            // Resumo ao vivo da última pesagem ("84,7 kg • 23,0% BF • 61,8 kg Músculo")
            Surface(
                color = RecompBgDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RecompCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (records.isEmpty()) {
                                if (latestRecord.weightKg > 0f) {
                                    String.format(ptBr, "%.1f kg • Sem bioimpedância registrada", latestRecord.weightKg)
                                } else {
                                    "Sem bioimpedância registrada"
                                }
                            } else {
                                String.format(
                                    ptBr,
                                    "%.1f kg • %.1f%% BF • %.1f kg Músculo",
                                    latestRecord.weightKg,
                                    latestRecord.bodyFatPercent,
                                    latestRecord.muscleMassKg
                                )
                            },
                            color = RecompPrimaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = RecompSecondaryText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (records.isEmpty()) "Última: --" else "Última: ${latestRecord.dateTimeLabel}",
                                color = RecompSecondaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (records.isEmpty() || latestRecord.bodyFatPercent <= 0f) {
                                "Massa Gorda: --"
                            } else {
                                String.format(
                                    ptBr,
                                    "Massa Gorda: %.1f kg",
                                    latestRecord.fatMassKg
                                )
                            },
                            color = RecompBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Button(
                onClick = {
                    AudioSynth.playTickSound()
                    records = BodyRecompEngine.loadRecords(context, activeProfile)
                    showFullDossierModal = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RecompBlue,
                    contentColor = Color(0xFF06101E)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_open_recomp_dossier")
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ABRIR DOSSIÊ DE RECOMPOSIÇÃO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    if (showFullDossierModal) {
        Dialog(
            onDismissRequest = {
                records = BodyRecompEngine.loadRecords(context, activeProfile)
                showFullDossierModal = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BodyRecompScreen(
                userProfile = userProfile,
                onBack = {
                    records = BodyRecompEngine.loadRecords(context, activeProfile)
                    showFullDossierModal = false
                },
                onRecordsUpdated = { updatedList ->
                    records = updatedList
                    updatedList.maxByOrNull { it.timestampMillis }?.let { newest ->
                        onWeightSyncedToProfile?.invoke(newest.weightKg)
                    }
                }
            )
        }
    }
}

/**
 * 2. TELA PRINCIPAL `BodyRecompScreen`:
 * Contém:
 * A. Cabeçalho Mestre + Botão "+ Check-in Semanal (Foto / Print Fitdays)" + Edição de Leitura
 * B. Triplo Destaque Superior + Sub-cards "Massa Gorda", "Músculo Esq." e "Relação M/G"
 * C. Seção "Linha do Tempo Passo a Passo (GANHOS / PRESERV. vs REDUÇÕES / QUEIMA)"
 * D. Seção "Metas Fisiológicas de Recomposição" (2 linhas limpas por meta acima da barra)
 * E. Tabela Oficial dos 14 Indicadores (coluna INDICADOR expandida sem truncamento)
 * F. Card de Macro-Análise Híbrida IA ao final da tela
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyRecompScreen(
    onBack: () -> Unit,
    userProfile: UserProfileData? = WeightCalculator.activeUserProfile,
    onRecordsUpdated: ((List<BioimpedanceRecord>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val activeProfile = userProfile ?: WeightCalculator.activeUserProfile
    val activeProfileHeightCm = resolveValidHeightCmOrNull(activeProfile)
    var records by remember(activeProfile?.nome) { mutableStateOf(BodyRecompEngine.loadRecords(context, activeProfile)) }
    // timelineCards está ordenado do mais recente (índice 0) até o Marco Zero #1 (último índice)
    val timelineCards = remember(records, activeProfile) {
        BodyRecompEngine.buildRecompTimelineCards(records, activeProfile)
    }
    var selectedCardIndex by remember(timelineCards.size) { mutableIntStateOf(0) }
    var compareWithMarcoZero by remember { mutableStateOf(false) }
    var showCheckInModal by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<BioimpedanceRecord?>(null) }

    val activeTimelineCard = timelineCards.getOrNull(selectedCardIndex)
        ?: timelineCards.firstOrNull()
    val activeRecord = activeTimelineCard?.current
        ?: BodyRecompEngine.defaultFallbackRecord(activeProfile)
    val baselineRecord = timelineCards.lastOrNull()?.current

    val goalBars = remember(activeRecord, activeProfile, baselineRecord) {
        BodyRecompEngine.buildPhysiologicalGoalBars(
            current = activeRecord,
            profile = activeProfile,
            baseline = baselineRecord
        )
    }
    val evaluations = remember(activeRecord, activeProfile) {
        BodyRecompEngine.evaluateIndicators(activeRecord, activeProfile)
    }
    val hydrationWarning = remember(activeRecord, activeProfile) {
        BodyRecompEngine.buildHydrationConductivityWarning(activeRecord, activeProfile)
    }

    val currentDensity = LocalDensity.current
    val safeDensity = remember(currentDensity.density, currentDensity.fontScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceAtMost(1.0f)
        )
    }

    CompositionLocalProvider(LocalDensity provides safeDensity) {
        Scaffold(
            topBar = {
                Surface(
                    color = RecompBgDark,
                    border = BorderStroke(0.5.dp, RecompCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onBack()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_back_body_recomp")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar ao Perfil",
                                    tint = RecompPrimaryText
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "MAX RECOMP",
                                    color = RecompPrimaryText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Dossiê de Bioimpedância",
                                    color = RecompSecondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val topBadgeText = if (hydrationWarning != null) {
                            "HIDRATAÇÃO"
                        } else {
                            activeTimelineCard?.recompClassification?.badgeLabel ?: "RECOMP"
                        }
                        val topBadgeColor = if (hydrationWarning != null) {
                            RecompRed
                        } else {
                            Color(activeTimelineCard?.recompClassification?.accentColorHex ?: 0xFF00E676)
                        }
                        RecompBadgePill(
                            text = topBadgeText,
                            accentColor = topBadgeColor
                        )
                    }
                }
            },
            containerColor = RecompBgDark,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                // =========================================================================
                // A. CABEÇALHO MESTRE + BOTÃO "+ CHECK-IN SEMANAL" + EXPORTAR LAUDO PDF A4
                // =========================================================================
                item {
                    MasterRecompHeaderCard(
                        activeRecord = activeRecord,
                        timelineCards = timelineCards,
                        selectedCardIndex = selectedCardIndex,
                        onSelectCardIndex = { idx ->
                            AudioSynth.playTickSound()
                            selectedCardIndex = idx
                        },
                        onOpenCheckInModal = {
                            AudioSynth.playTickSound()
                            editingRecord = null
                            showCheckInModal = true
                        },
                        onEditActiveRecord = {
                            AudioSynth.playTickSound()
                            editingRecord = activeRecord
                            showCheckInModal = true
                        },
                        onExportClinicalPdf = {
                            AudioSynth.playTickSound()
                            val ptBr = Locale.forLanguageTag("pt-BR")
                            val waterLabel = activeProfile?.metaAguaLitros?.takeIf { it > 0f }
                                ?.let { String.format(ptBr, "%.1f L/dia", it) }
                                ?: "Não definida"
                            BioimpedancePdfExporter.exportAndShareClinicalPdf(
                                context = context,
                                activeRecord = activeRecord,
                                allRecords = records,
                                athleteName = activeProfile?.nome?.ifBlank { "Atleta" } ?: "Atleta",
                                athleteAge = activeProfile?.idade?.takeIf { it in 10..100 },
                                heightCm = activeProfileHeightCm,
                                waterGoalLabel = waterLabel
                            )
                        }
                    )
                }

            // =========================================================================
            // B. TRIPLO DESTAQUE SUPERIOR + MÉTRICAS DERIVADAS ("Massa Gorda", "Músculo Esq.", "Relação M/G")
            // =========================================================================
            item {
                TripleHighlightAndDerivedCard(
                    record = activeRecord,
                    userProfile = activeProfile
                )
            }

            // =========================================================================
            // C. SEÇÃO "LINHA DO TEMPO PASSO A PASSO (GANHOS / PRESERV. vs REDUÇÕES / QUEIMA)"
            // =========================================================================
            item {
                RecompTimelineStepSection(
                    timelineCards = timelineCards,
                    selectedCardIndex = selectedCardIndex,
                    compareWithMarcoZero = compareWithMarcoZero,
                    onToggleCompareMode = {
                        AudioSynth.playTickSound()
                        compareWithMarcoZero = !compareWithMarcoZero
                    },
                    hydrationWarning = hydrationWarning,
                    onEditStepRecord = { recToEdit ->
                        AudioSynth.playTickSound()
                        editingRecord = recToEdit
                        showCheckInModal = true
                    },
                    onDeleteCurrentRecord = { recordId ->
                        AudioSynth.playTickSound()
                        val updated = BodyRecompEngine.deleteRecord(context, recordId, activeProfile)
                        records = updated
                        selectedCardIndex = 0
                        onRecordsUpdated?.invoke(updated)
                        Toast.makeText(
                            context,
                            "Leitura removida. Retornando ao histórico anterior.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenNewCheckIn = {
                        AudioSynth.playTickSound()
                        editingRecord = null
                        showCheckInModal = true
                    }
                )
            }

            // =========================================================================
            // D. CARD DE METAS FISIOLÓGICAS (4 BARRAS DE PROGRESSO EM 2 LINHAS LIMPAS)
            // =========================================================================
            item {
                PhysiologicalGoalsCard(goals = goalBars)
            }

            // =========================================================================
            // E. TABELA OFICIAL DOS 14 INDICADORES FITDAYS / RELAXMEDIC
            // =========================================================================
            item {
                OfficialFitdays14TableCard(
                    record = activeRecord,
                    evaluations = evaluations
                )
            }

                // =========================================================================
                // F. DOSSIÊ DE PERÍODO • MACRO-ANÁLISE HÍBRIDA IA (FINAL DA TELA)
                // =========================================================================
                item {
                    com.example.ui.components.PeriodMacroAnalysisEntryCard(
                        recompRecords = records,
                        userAgeYears = activeProfile?.idade?.takeIf { it in 10..100 }
                    )
                }

                // Espaçamento protetor para a barra de navegação de 3 botões do Android (Motorola)
                item {
                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(48.dp)
                    )
                }
            }
        }
    }

    // =========================================================================
    // 3. MODAL DE CHECK-IN SEMANAL / EDIÇÃO (EXTRATOR AUTOMÁTICO FITDAYS & RELAXMEDIC)
    // =========================================================================
    if (showCheckInModal) {
        val newestRecord = timelineCards.firstOrNull()?.current
            ?: BodyRecompEngine.defaultFallbackRecord(activeProfile)
        WeeklyBioimpedanceCheckInDialog(
            latestRecord = newestRecord,
            existingRecords = records,
            editingRecord = editingRecord,
            userProfile = activeProfile,
            profileHeightCm = activeProfileHeightCm,
            onDismiss = {
                editingRecord = null
                showCheckInModal = false
            },
            onSaveRecord = { savedRecord ->
                val wasEditing = editingRecord != null
                val updated = BodyRecompEngine.upsertRecord(context, savedRecord)
                records = updated
                val newCards = BodyRecompEngine.buildRecompTimelineCards(updated, activeProfile)
                val targetIdx = newCards.indexOfFirst { it.current.id == savedRecord.id }
                selectedCardIndex = if (targetIdx >= 0) targetIdx else 0
                editingRecord = null
                showCheckInModal = false
                onRecordsUpdated?.invoke(updated)
                Toast.makeText(
                    context,
                    if (wasEditing) "Check-in atualizado com sucesso!" else "Check-in registrado no MAX RECOMP!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun MasterRecompHeaderCard(
    activeRecord: BioimpedanceRecord,
    timelineCards: List<RecompStepCardSummary>,
    selectedCardIndex: Int,
    onSelectCardIndex: (Int) -> Unit,
    onOpenCheckInModal: () -> Unit,
    onEditActiveRecord: () -> Unit,
    onExportClinicalPdf: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val activeSummary = timelineCards.getOrNull(selectedCardIndex)
    val isBaselineSelected = activeSummary?.isBaseline == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompCardBorder)
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = RecompCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "LEITURA ATIVA",
                            color = RecompCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = activeRecord.dateTimeLabel,
                        color = RecompPrimaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeRecord.athleteName} • ${activeRecord.sourceApp}",
                        color = RecompSecondaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecompBadgePill(
                        text = if (isBaselineSelected) "MARCO ZERO" else "CHECK-IN #${activeSummary?.stepIndex ?: 1}",
                        accentColor = if (isBaselineSelected) RecompBlue else RecompGreen
                    )
                    IconButton(
                        onClick = onEditActiveRecord,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_edit_active_checkin")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar check-in selecionado",
                            tint = RecompCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Resumo Segmentar (Limb Dados Fitdays / Relaxmedic) quando disponível
            if (activeRecord.segmentalSummary.isNotBlank()) {
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = RecompCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeRecord.segmentalSummary,
                            color = RecompSecondaryText,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Seletor horizontal de leituras: "#5 • 03/11" na 1ª linha e "85,4 kg" na 2ª linha
            if (timelineCards.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timelineCards.take(4).forEachIndexed { idx, summary ->
                        val isSelected = idx == selectedCardIndex
                        val shortDate = summary.current.dateTimeLabel.take(5)
                        val pillTitle = "#${summary.stepIndex} • $shortDate"
                        Surface(
                            onClick = { onSelectCardIndex(idx) },
                            color = if (isSelected) RecompBlue.copy(alpha = 0.2f) else RecompBgDark,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) RecompBlue else RecompCardBorder
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = pillTitle,
                                    color = if (isSelected) RecompCyan else RecompPrimaryText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(ptBr, "%.1f kg", summary.current.weightKg),
                                    color = RecompSecondaryText,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onOpenCheckInModal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RecompBlue,
                    contentColor = Color(0xFF06101E)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_open_weekly_checkin")
            ) {
                Text(
                    text = "📷 + Importar Bioimpedância (Foto / PDF)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onExportClinicalPdf,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = RecompCyan.copy(alpha = 0.12f),
                    contentColor = RecompCyan
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_export_clinical_pdf")
            ) {
                Text(
                    text = "📄 Exportar Laudo PDF (Nutricionista)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TripleHighlightAndDerivedCard(
    record: BioimpedanceRecord,
    userProfile: UserProfileData? = WeightCalculator.activeUserProfile
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val hasPersonalGoals = BioimpedanceReferences.hasPersonalGoals(userProfile)
    val healthyWeightRange = BioimpedanceReferences.healthyWeightRangeKg(resolveValidHeightCmOrNull(userProfile))
    val weightBadgeText = when {
        record.weightKg <= 0f || healthyWeightRange == null -> "—"
        record.weightKg < healthyWeightRange.start -> "Baixo"
        record.weightKg <= healthyWeightRange.endInclusive -> "Saudável"
        else -> "Alto"
    }
    val weightBadgeColor = when {
        record.weightKg <= 0f || healthyWeightRange == null -> RecompSecondaryText
        record.weightKg in healthyWeightRange -> RecompGreen
        else -> RecompAmber
    }
    val bmiStatus = if (record.bmi > 0f) BioimpedanceReferences.classifyBmi(record.bmi) else IndicatorStatus.NEUTRO
    val bmiBadgeText = if (record.bmi > 0f) BioimpedanceReferences.bmiBandLabel(record.bmi) else "—"
    val bfBadgeText = when {
        !hasPersonalGoals || record.bodyFatPercent <= 0f -> "—"
        record.bodyFatPercent >= 21f -> "Alto"
        else -> "Médio"
    }
    val bfColor = when {
        !hasPersonalGoals || record.bodyFatPercent <= 0f -> RecompSecondaryText
        record.bodyFatPercent >= 21f -> RecompRed
        else -> RecompGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompCardBorder)
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
                        .background(RecompCyan, RoundedCornerShape(2.dp))
                )
                Text(
                    text = "TRIPLO DESTAQUE & COMPOSIÇÃO REAL",
                    color = RecompPrimaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            // Linha Superior: Peso | IMC | Gordura Corporal (%)
            Surface(
                color = RecompBgDark,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RecompCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopMetricColumn(
                        label = "PESO CORPORAL",
                        value = if (record.weightKg > 0f) String.format(ptBr, "%.1f kg", record.weightKg) else "--",
                        badgeText = weightBadgeText,
                        badgeColor = weightBadgeColor,
                        valueColor = RecompPrimaryText
                    )

                    VerticalMetricDivider()

                    TopMetricColumn(
                        label = "IMC ATUAL",
                        value = if (record.bmi > 0f) String.format(ptBr, "%.1f", record.bmi) else "--",
                        badgeText = bmiBadgeText,
                        badgeColor = Color(bmiStatus.badgeColorHex),
                        valueColor = RecompCyan
                    )

                    VerticalMetricDivider()

                    TopMetricColumn(
                        label = "GORDURA (BF%)",
                        value = if (record.bodyFatPercent > 0f) String.format(ptBr, "%.1f%%", record.bodyFatPercent) else "--",
                        badgeText = bfBadgeText,
                        badgeColor = bfColor,
                        valueColor = if (record.bodyFatPercent > 0f) bfColor else RecompPrimaryText
                    )
                }
            }

            // Segunda Linha: Sub-cards com títulos curtos sem reticências ("Massa Gorda", "Músculo Esq.", "Relação M/G")
            val hasFat = record.bodyFatPercent > 0f && record.weightKg > 0f
            val hasSkeletal = record.skeletalMusclePercent > 0f && record.weightKg > 0f
            val hasRatio = hasFat && record.muscleMassKg > 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DerivedMetricMiniBox(
                    title = "Massa Gorda",
                    value = if (hasFat) String.format(ptBr, "%.1f kg", record.fatMassKg) else "--",
                    subtitle = if (hasFat) String.format(ptBr, "%.1f kg lipídios", record.fatMassKg) else "Sem medição",
                    accentColor = RecompBlue,
                    modifier = Modifier.weight(1f)
                )
                DerivedMetricMiniBox(
                    title = "Músculo Esq.",
                    value = if (hasSkeletal) String.format(ptBr, "%.1f kg", record.skeletalMuscleKg) else "--",
                    subtitle = if (hasSkeletal) String.format(ptBr, "%.1f%% contrátil", record.skeletalMusclePercent) else "Sem medição",
                    accentColor = RecompGreen,
                    modifier = Modifier.weight(1f)
                )
                DerivedMetricMiniBox(
                    title = "Relação M/G",
                    value = if (hasRatio) String.format(ptBr, "%.2fx", record.leanToFatRatio) else "--",
                    subtitle = if (hasPersonalGoals) "Meta >= 3.20x" else "Sem referência",
                    accentColor = RecompCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TopMetricColumn(
    label: String,
    value: String,
    badgeText: String,
    badgeColor: Color,
    valueColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = RecompSecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        RecompStatusPill(text = badgeText, color = badgeColor)
    }
}

@Composable
private fun DerivedMetricMiniBox(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = RecompCardElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = RecompSecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = subtitle,
                color = RecompSecondaryText.copy(alpha = 0.85f),
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VerticalMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(RecompCardBorder)
    )
}

/**
 * C. SEÇÃO "LINHA DO TEMPO PASSO A PASSO (GANHOS / PRESERV. vs REDUÇÕES / QUEIMA)"
 */
@Composable
private fun RecompTimelineStepSection(
    timelineCards: List<RecompStepCardSummary>,
    selectedCardIndex: Int,
    compareWithMarcoZero: Boolean,
    onToggleCompareMode: () -> Unit,
    hydrationWarning: String?,
    onEditStepRecord: (BioimpedanceRecord) -> Unit,
    onDeleteCurrentRecord: (String) -> Unit,
    onOpenNewCheckIn: () -> Unit
) {
    val selectedSummary = timelineCards.getOrNull(selectedCardIndex) ?: timelineCards.firstOrNull()

    // Quando há 2+ leituras e o usuário alterna para "Vs. Marco Zero", recalculamos o degrau contra `baseline`
    val effectiveCard: RecompStepCardSummary? = remember(
        selectedSummary,
        compareWithMarcoZero
    ) {
        if (selectedSummary != null && !selectedSummary.isBaseline && compareWithMarcoZero) {
            BodyRecompEngine.buildStepSummary(
                stepIndex = selectedSummary.stepIndex,
                current = selectedSummary.current,
                previous = selectedSummary.baseline,
                baseline = selectedSummary.baseline
            )
        } else {
            selectedSummary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = RecompCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "LINHA DO TEMPO PASSO A PASSO",
                            color = RecompPrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Ganhos / Preserv. vs Reduções / Queima",
                            color = RecompSecondaryText,
                            fontSize = 11.sp
                        )
                    }
                }

                if (selectedSummary != null && !selectedSummary.isBaseline) {
                    Surface(
                        onClick = onToggleCompareMode,
                        color = RecompCardElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (compareWithMarcoZero) "Vs. Marco Zero" else "Vs. Anterior",
                            color = RecompCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (effectiveCard != null) {
                if (effectiveCard.isBaseline) {
                    BaselineDiagnosticSubCard(
                        card = effectiveCard,
                        onEditRecord = { onEditStepRecord(effectiveCard.current) },
                        onOpenNewCheckIn = onOpenNewCheckIn
                    )
                } else {
                    ComparativeGainsLossesSubCard(
                        card = effectiveCard,
                        compareWithMarcoZero = compareWithMarcoZero,
                        canDelete = !effectiveCard.isBaseline,
                        onEdit = { onEditStepRecord(effectiveCard.current) },
                        onDelete = { onDeleteCurrentRecord(effectiveCard.current.id) }
                    )
                }

                // Parecer do Coach Fisiologista
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = RecompCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PARECER DO COACH FISIOLOGISTA",
                                color = RecompCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            )
                        }
                        Text(
                            text = effectiveCard.coachPhysiologyVerdict,
                            color = RecompPrimaryText,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Alerta de Condutividade Hídrica (< 55% de água corporal)
            if (hydrationWarning != null) {
                Surface(
                    color = RecompAmber.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RecompAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = "Alerta de Hidratação",
                            tint = RecompAmber,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ALERTA DE CONDUTIVIDADE HÍDRICA (BIA)",
                                color = RecompAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = hydrationWarning,
                                color = RecompPrimaryText,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BaselineDiagnosticSubCard(
    card: RecompStepCardSummary,
    onEditRecord: () -> Unit,
    onOpenNewCheckIn: () -> Unit
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val rec = card.current
    Surface(
        color = RecompCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = card.recompClassification.title,
                        color = RecompPrimaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ponto de Partida Oficial • ${rec.dateTimeLabel}",
                        color = RecompSecondaryText,
                        fontSize = 11.sp
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecompBadgePill(
                        text = card.recompClassification.badgeLabel,
                        accentColor = Color(card.recompClassification.accentColorHex)
                    )
                    IconButton(
                        onClick = onEditRecord,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Marco Zero",
                            tint = RecompCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📈 GANHOS / PRESERV.",
                            color = RecompGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        BaselineKeyValueRow("Massa Muscular", String.format(ptBr, "%.1f kg", rec.muscleMassKg), RecompGreen)
                        BaselineKeyValueRow("Músculo Esq.", String.format(ptBr, "%.1f kg", rec.skeletalMuscleKg), RecompCyan)
                        BaselineKeyValueRow("Massa Livre", String.format(ptBr, "%.1f kg", rec.fatFreeMassKg), RecompPrimaryText)
                        BaselineKeyValueRow("TMB Basal", "${rec.bmrKcal} kcal", RecompPrimaryText)
                    }
                }

                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📉 REDUÇÕES / QUEIMA",
                            color = RecompBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        BaselineKeyValueRow("Gordura Real", String.format(ptBr, "%.1f kg", rec.fatMassKg), RecompAmber)
                        BaselineKeyValueRow("Gordura (BF%)", String.format(ptBr, "%.1f%%", rec.bodyFatPercent), RecompRed)
                        BaselineKeyValueRow("Gord. Visceral", String.format(ptBr, "%.0f", rec.visceralFatIndex), RecompRed)
                        BaselineKeyValueRow("Id. Metabólica", "${rec.metabolicAge} anos", RecompAmber)
                    }
                }
            }

            Button(
                onClick = onOpenNewCheckIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RecompGreen,
                    contentColor = Color(0xFF051B11)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Registrar Nova Bioimpedância",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun BaselineKeyValueRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = RecompSecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ComparativeGainsLossesSubCard(
    card: RecompStepCardSummary,
    compareWithMarcoZero: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = Color(card.recompClassification.accentColorHex)
    val prevDate = (card.previous?.dateTimeLabel ?: card.baseline.dateTimeLabel).take(10)
    val currDate = card.current.dateTimeLabel.take(10)
    val periodSubtitle = if (compareWithMarcoZero) {
        "$currDate vs. Marco Zero ($prevDate)"
    } else {
        "$currDate vs. Anterior ($prevDate)"
    }

    Surface(
        color = RecompCardElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Check-in #${card.stepIndex} • ${card.current.sourceApp}",
                        color = RecompPrimaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = periodSubtitle,
                        color = RecompSecondaryText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecompBadgePill(
                        text = card.recompClassification.badgeLabel,
                        accentColor = accent
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_edit_timeline_step")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar check-in",
                            tint = RecompCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_delete_timeline_step")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remover leitura",
                                tint = RecompSecondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Duas colunas lado a lado: GANHOS / PRESERV. vs. REDUÇÕES / QUEIMA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Coluna da Esquerda — GANHOS / PRESERV. (Verde #00E676 / Ciano #00E5FF)
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompGreen.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📈 GANHOS / PRESERV.",
                            color = RecompGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )

                        if (card.gainsList.isEmpty()) {
                            Text(
                                text = "Estável nesta leitura",
                                color = RecompSecondaryText,
                                fontSize = 11.sp
                            )
                        } else {
                            card.gainsList.forEach { delta ->
                                DeltaMetricRow(delta = delta)
                            }
                        }
                    }
                }

                // Coluna da Direita — REDUÇÕES / QUEIMA (Azul #00B0FF)
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📉 REDUÇÕES / QUEIMA",
                            color = RecompBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )

                        if (card.lossesList.isEmpty()) {
                            Text(
                                text = "Sem reduções registradas",
                                color = RecompSecondaryText,
                                fontSize = 11.sp
                            )
                        } else {
                            card.lossesList.forEach { delta ->
                                DeltaMetricRow(delta = delta)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empilha ou espaça o nome curto da métrica e o delta numérico para impedir
 * qualquer truncamento do tipo `"Mús... 0,0 kg"` ou `"Gor... 0,0 kg"`.
 */
@Composable
private fun DeltaMetricRow(delta: RecompDeltaItem) {
    val tint = Color(delta.highlightColorHex)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = delta.metricLabel,
            color = RecompSecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = delta.formattedDelta,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * D. CARD DE METAS FISIOLÓGICAS (4 BARRAS DE PROGRESSO ESTRUTURADAS EM 2 LINHAS LIMPAS)
 */
@Composable
private fun PhysiologicalGoalsCard(goals: List<PhysiologicalGoalProgress>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompCardBorder)
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
                        .background(RecompGreen, RoundedCornerShape(2.dp))
                )
                Column {
                    Text(
                        text = "METAS FISIOLÓGICAS DE RECOMPOSIÇÃO",
                        color = RecompPrimaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Progresso rumo aos alvos clínicos de longevidade e performance",
                        color = RecompSecondaryText,
                        fontSize = 11.sp
                    )
                }
            }

            if (goals.isEmpty()) {
                Surface(
                    color = RecompBgDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RecompCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Metas ainda não definidas",
                        color = RecompSecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                }
            } else {
                goals.forEach { goal ->
                val barColor = Color(goal.status.badgeColorHex)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    // Linha 1: Nome da métrica à esquerda em Bold e Status/Falta à direita
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = goal.title,
                            color = RecompPrimaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = goal.deltaRemainingText,
                            color = RecompSecondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Linha 2: "Atual: 23,0% (19,5 kg)" à esquerda e "Meta: < 18,0%" à direita
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Atual: ${goal.currentFormatted}",
                            color = barColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Meta: ${goal.targetFormatted}",
                            color = RecompCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Barra de progresso customizada
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RecompBgDark)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = goal.progressFraction.coerceIn(0.06f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * E. TABELA OFICIAL DOS 14 INDICADORES FITDAYS / RELAXMEDIC
 * Sem pílula "14 MÉTRICAS" espremida no topo e com largura generosa para a coluna `INDICADOR`.
 */
@Composable
private fun OfficialFitdays14TableCard(
    record: BioimpedanceRecord,
    evaluations: List<BioimpedanceIndicatorAssessment>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fitdays_14_clinical_table_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RecompCardBg),
        border = BorderStroke(1.dp, RecompCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .background(RecompBlue, RoundedCornerShape(2.dp))
                )
                Column {
                    Text(
                        text = "RELATÓRIO OFICIAL (14 INDICADORES)",
                        color = RecompPrimaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${record.sourceApp} • ${record.dateTimeLabel}",
                        color = RecompSecondaryText,
                        fontSize = 11.sp
                    )
                }
            }

            // Cabeçalho da Tabela Clínica (Indicador | Valor | Status)
            Surface(
                color = RecompBgDark,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INDICADOR",
                        color = RecompSecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.35f)
                    )
                    Text(
                        text = "VALOR",
                        color = RecompSecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(82.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = "STATUS",
                        color = RecompSecondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(96.dp)
                    )
                }
            }

            // Tabela Clínica de Linhas Horizontais com cores alternadas (#151922 e #1A1F2B) e divisória 0.5.dp (#262D3D)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151922))
            ) {
                evaluations.forEachIndexed { index, item ->
                    val rowBgColor = if (index % 2 == 0) Color(0xFF151922) else Color(0xFF1A1F2B)
                    val pillColor = Color(item.status.badgeColorHex)
                    val normalizedStatusLabel = when {
                        item.status.labelPt.equals("Risco Médio", ignoreCase = true) -> "Médio Risco"
                        else -> item.status.labelPt
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBgColor)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Coluna 1 (Indicador — weight(1.35f)): Nome completo (13.sp, Bold) + Referência curta abaixo (10.sp)
                        Column(
                            modifier = Modifier
                                .weight(1.35f)
                                .padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = item.titlePt,
                                color = RecompPrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.referenceTargetText,
                                color = RecompSecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Coluna 2 (Valor — width(86.dp), alinhado à direita com padding(end = 10.dp)): 14.sp, Bold, sem quebra
                        Text(
                            text = item.formattedValue,
                            color = RecompPrimaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .width(86.dp)
                                .padding(end = 10.dp)
                        )

                        // Coluna 3 (Box de Status Padronizado — width(96.dp), centralizado)
                        Box(
                            modifier = Modifier.width(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.status == IndicatorStatus.NEUTRO) {
                                Text(
                                    text = normalizedStatusLabel,
                                    color = RecompSecondaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            } else {
                                RecompStatusPill(
                                    text = normalizedStatusLabel,
                                    color = pillColor,
                                    fixedWidthDp = 96
                                )
                            }
                        }
                    }

                    if (index < evaluations.lastIndex) {
                        HorizontalDivider(
                            color = Color(0xFF262D3D),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. MODAL DE CHECK-IN SEMANAL / EDIÇÃO (`Fitdays` & `Relaxmedic` Smart Parser + Conversão `kg -> %`)
 */
@Composable
private fun WeeklyBioimpedanceCheckInDialog(
    latestRecord: BioimpedanceRecord,
    existingRecords: List<BioimpedanceRecord>,
    editingRecord: BioimpedanceRecord? = null,
    userProfile: UserProfileData? = WeightCalculator.activeUserProfile,
    profileHeightCm: Int? = resolveValidHeightCmOrNull(userProfile),
    onDismiss: () -> Unit,
    onSaveRecord: (BioimpedanceRecord) -> Unit
) {
    val context = LocalContext.current
    val initialDraft = remember(latestRecord, editingRecord, userProfile) {
        editingRecord ?: BodyRecompEngine.createWeeklyCheckInDraft(latestRecord, profile = userProfile)
    }

    val availableTemplates = remember(userProfile) {
        BioimpedanceImageParser.availableTemplatesForProfile(userProfile)
    }

    var selectedTemplate by remember(availableTemplates, editingRecord) {
        mutableStateOf(
            when {
                availableTemplates.isNotEmpty() &&
                    editingRecord?.sourceApp?.contains("Relaxmedic", ignoreCase = true) == true ->
                    BioimpedanceDetectedTemplate.RELAXMEDIC_13_10
                availableTemplates.isNotEmpty() &&
                    editingRecord?.sourceApp?.contains("Fitdays", ignoreCase = true) == true ->
                    BioimpedanceDetectedTemplate.FITDAYS_29_10
                else -> BioimpedanceDetectedTemplate.MANUAL_ENTRY
            }
        )
    }
    var parserHeadline by remember { mutableStateOf<String?>(null) }
    var parserConversionNote by remember { mutableStateOf<String?>(null) }
    var lastParsedIndicatorsCount by remember { mutableStateOf<Int?>(null) }
    var lastParseWasSavedTemplate by remember { mutableStateOf(false) }

    fun fmtOptFloat(v: Float, decimals: Int = 1): String =
        if (v > 0f) String.format(Locale.US, "%.${decimals}f", v) else ""
    fun fmtOptInt(v: Int): String = if (v > 0) v.toString() else ""

    var dateTimeLabel by remember { mutableStateOf(initialDraft.dateTimeLabel) }
    var sourceAppText by remember { mutableStateOf(initialDraft.sourceApp) }
    var segmentalSummaryText by remember { mutableStateOf(initialDraft.segmentalSummary) }
    var weightText by remember { mutableStateOf(fmtOptFloat(initialDraft.weightKg)) }
    var bmiText by remember { mutableStateOf(fmtOptFloat(initialDraft.bmi)) }
    var bfText by remember { mutableStateOf(fmtOptFloat(initialDraft.bodyFatPercent)) }
    var muscleRateText by remember { mutableStateOf(fmtOptFloat(initialDraft.muscleRatePercent)) }
    var ffmText by remember { mutableStateOf(fmtOptFloat(initialDraft.fatFreeMassKg)) }
    var subFatText by remember { mutableStateOf(fmtOptFloat(initialDraft.subcutaneousFatPercent)) }
    var visceralText by remember { mutableStateOf(fmtOptFloat(initialDraft.visceralFatIndex, 0)) }
    var waterText by remember { mutableStateOf(fmtOptFloat(initialDraft.bodyWaterPercent)) }
    var skeletalPctText by remember { mutableStateOf(fmtOptFloat(initialDraft.skeletalMusclePercent)) }
    var muscleMassKgText by remember { mutableStateOf(fmtOptFloat(initialDraft.muscleMassKg)) }
    var boneMassText by remember { mutableStateOf(fmtOptFloat(initialDraft.boneMassKg)) }
    var proteinText by remember { mutableStateOf(fmtOptFloat(initialDraft.proteinPercent)) }
    var bmrText by remember { mutableStateOf(fmtOptInt(initialDraft.bmrKcal)) }
    var metAgeText by remember { mutableStateOf(fmtOptInt(initialDraft.metabolicAge)) }
    var parsedExplicitFatMassKg by remember { mutableStateOf<Float?>(initialDraft.explicitFatMassKg) }

    val coroutineScope = rememberCoroutineScope()
    var rawOcrPasteText by remember { mutableStateOf("") }
    var showRawTextExtractor by remember { mutableStateOf(false) }
    var isExtractingReport by remember { mutableStateOf(false) }
    var selectedImageUri by remember {
        mutableStateOf<Uri?>(editingRecord?.photoUri?.let { runCatching { Uri.parse(it) }.getOrNull() })
    }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    fun applyParsedRecord(parsed: BioimpedanceRecord) {
        dateTimeLabel = parsed.dateTimeLabel
        sourceAppText = parsed.sourceApp
        segmentalSummaryText = parsed.segmentalSummary
        weightText = fmtOptFloat(parsed.weightKg)
        bmiText = fmtOptFloat(parsed.bmi)
        bfText = fmtOptFloat(parsed.bodyFatPercent)
        muscleRateText = fmtOptFloat(parsed.muscleRatePercent)
        ffmText = fmtOptFloat(parsed.fatFreeMassKg)
        subFatText = fmtOptFloat(parsed.subcutaneousFatPercent)
        visceralText = fmtOptFloat(parsed.visceralFatIndex, 0)
        waterText = fmtOptFloat(parsed.bodyWaterPercent)
        skeletalPctText = fmtOptFloat(parsed.skeletalMusclePercent)
        muscleMassKgText = fmtOptFloat(parsed.muscleMassKg)
        boneMassText = fmtOptFloat(parsed.boneMassKg)
        proteinText = fmtOptFloat(parsed.proteinPercent)
        bmrText = fmtOptInt(parsed.bmrKcal)
        metAgeText = fmtOptInt(parsed.metabolicAge)
        parsedExplicitFatMassKg = parsed.explicitFatMassKg
    }

    fun handlePickedReportUri(uri: Uri?) {
        if (uri == null) return
        selectedImageUri = uri
        isExtractingReport = true
        lastParsedIndicatorsCount = null
        lastParseWasSavedTemplate = false
        parserHeadline = "Lendo relatório Relaxmedic / Fitdays via OCR..."
        parserConversionNote = "Reconstruindo as 2 colunas e extraindo os indicadores..."
        coroutineScope.launch {
            val analysis = BioimpedanceImageParser.extractFromUri(
                context = context,
                uri = uri,
                existingRecords = existingRecords,
                fallbackBase = initialDraft,
                profileHeightCm = profileHeightCm?.toFloat(),
                profile = userProfile
            )
            isExtractingReport = false
            previewBitmap = analysis.renderedBitmap
            selectedTemplate = analysis.detectedTemplate
            parserHeadline = analysis.statusHeadline
            parserConversionNote = analysis.conversionDetails
            lastParsedIndicatorsCount = analysis.filledIndicatorsCount
            lastParseWasSavedTemplate = false
            if (analysis.reconstructedOcrText.isNotBlank()) {
                rawOcrPasteText = analysis.reconstructedOcrText
            }
            applyParsedRecord(analysis.record)

            Toast.makeText(
                context,
                analysis.statusHeadline,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        handlePickedReportUri(uri)
    }

    val documentOrPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        handlePickedReportUri(uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = RecompBgDark,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, RecompBlue.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar do Modal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RecompCardBg)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (editingRecord != null) Icons.Default.Edit else Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = RecompCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (editingRecord != null) {
                                    "EDITAR CHECK-IN DE BIOIMPEDÂNCIA"
                                } else {
                                    "CHECK-IN SEMANAL (FITDAYS & RELAXMEDIC)"
                                },
                                color = RecompPrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Extração automática de imagem ou edição livre dos 14 indicadores",
                                color = RecompSecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = RecompSecondaryText
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    // Importação explícita de leitura salva (visível apenas para perfis com metas pessoais)
                    if (availableTemplates.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "IMPORTAR LEITURA SALVA:",
                                    color = RecompCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.4.sp
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    availableTemplates.forEach { template ->
                                        val isSelected = selectedTemplate == template
                                        Surface(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                selectedTemplate = template
                                                val built = BioimpedanceImageParser.buildResultForTemplate(
                                                    template = template,
                                                    photoUri = selectedImageUri?.toString(),
                                                    fallbackBase = initialDraft,
                                                    profile = userProfile
                                                )
                                                parserHeadline = built.statusHeadline
                                                parserConversionNote = built.conversionDetails
                                                lastParsedIndicatorsCount = built.filledIndicatorsCount
                                                lastParseWasSavedTemplate =
                                                    template != BioimpedanceDetectedTemplate.MANUAL_ENTRY
                                                applyParsedRecord(built.record)
                                            },
                                            color = if (isSelected) RecompBlue.copy(alpha = 0.22f) else RecompCardBg,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) RecompCyan else RecompCardBorder
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = template.chipLabel,
                                                color = if (isSelected) RecompCyan else RecompPrimaryText,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Seletor de Imagem / Print da Galeria + Banner de Conversão Automática
                    item {
                        Surface(
                            color = RecompCardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            documentOrPdfPickerLauncher.launch(
                                                arrayOf("image/*", "application/pdf")
                                            )
                                        },
                                        enabled = !isExtractingReport,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RecompBlue,
                                            contentColor = Color(0xFF06101E)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("btn_pick_bioimpedance_image")
                                    ) {
                                        if (isExtractingReport) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF06101E),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isExtractingReport) {
                                                "Lendo Relatório..."
                                            } else {
                                                "Importar PDF ou Imagem (Relaxmedic/Fitdays)"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isExtractingReport,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Galeria",
                                            tint = RecompCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            showRawTextExtractor = !showRawTextExtractor
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, RecompCyan.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            tint = RecompCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "OCR",
                                            color = RecompCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // Banner de status da extração OCR (sucesso verde apenas quando todos os 14 indicadores foram lidos)
                                if (parserHeadline != null) {
                                    val isFullOcrSuccess = !lastParseWasSavedTemplate &&
                                        lastParsedIndicatorsCount == BioimpedanceImageParser.TOTAL_EXPECTED_INDICATORS
                                    val bannerAccent = when {
                                        isExtractingReport || lastParseWasSavedTemplate -> RecompCyan
                                        isFullOcrSuccess -> RecompGreen
                                        else -> RecompAmber
                                    }
                                    Surface(
                                        color = bannerAccent.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, bannerAccent.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = parserHeadline ?: "",
                                                color = bannerAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            if (!parserConversionNote.isNullOrBlank()) {
                                                Text(
                                                    text = parserConversionNote ?: "",
                                                    color = RecompPrimaryText,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Miniatura do print ou PDF anexado para conferência lado a lado
                                previewBitmap?.let { bmp ->
                                    Surface(
                                        color = RecompBgDark,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, RecompCardBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Print ou PDF anexado de Bioimpedância",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showRawTextExtractor) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = rawOcrPasteText,
                                            onValueChange = { rawOcrPasteText = it },
                                            label = {
                                                Text(
                                                    "Cole o texto do Fitdays ou da Relaxmedic (converte kg -> % automaticamente)",
                                                    fontSize = 10.sp
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            maxLines = 5
                                        )
                                        Button(
                                            onClick = {
                                                val parsedRes = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
                                                    reconstructedText = rawOcrPasteText,
                                                    fallbackBase = initialDraft,
                                                    photoUri = selectedImageUri?.toString(),
                                                    profileHeightCm = profileHeightCm?.toFloat()
                                                )
                                                selectedTemplate = parsedRes.detectedTemplate
                                                parserHeadline = parsedRes.statusHeadline
                                                parserConversionNote = parsedRes.conversionDetails
                                                lastParsedIndicatorsCount = parsedRes.filledIndicatorsCount
                                                lastParseWasSavedTemplate = false
                                                applyParsedRecord(parsedRes.record)
                                                showRawTextExtractor = false
                                                Toast.makeText(
                                                    context,
                                                    parsedRes.statusHeadline,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = RecompGreen),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "EXTRAIR 14 MÉTRICAS DO TEXTO",
                                                color = Color(0xFF06150E),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Data / Hora da Leitura + Origem
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecompFieldInput(
                                label = "Data e Hora (dd/MM/yyyy - HH:mm)",
                                value = dateTimeLabel,
                                onValueChange = { dateTimeLabel = it },
                                isNumeric = false,
                                modifier = Modifier.weight(1.2f)
                            )
                            RecompFieldInput(
                                label = "Balança / Origem",
                                value = sourceAppText,
                                onValueChange = { sourceAppText = it },
                                isNumeric = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Grade de 14 Campos (2 por linha) totalmente editáveis
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "14 INDICADORES OFICIAIS (EDITÁVEIS ANTES DE SALVAR)",
                                color = RecompCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("1. Peso (kg)", weightText, {
                                    weightText = it
                                    val w = it.replace(",", ".").toFloatOrNull()
                                    val autoBmi = calculateAutoBmiOrNull(w, profileHeightCm)
                                    if (autoBmi != null) {
                                        bmiText = String.format(Locale.US, "%.1f", autoBmi)
                                    }
                                }, modifier = Modifier.weight(1f))
                                RecompFieldInput("2. IMC", bmiText, { bmiText = it }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("3. Gordura Corporal (%)", bfText, { bfText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("4. Taxa Muscular (%)", muscleRateText, { muscleRateText = it }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("5. Massa Livre Gord. (kg)", ffmText, { ffmText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("6. Gord. Subcutânea (%)", subFatText, { subFatText = it }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("7. Gordura Visceral", visceralText, { visceralText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("8. Água Corporal (%)", waterText, {
                                    // Se o usuário digitar o valor em kg da Relaxmedic (< 48), converte ou permite editar
                                    waterText = it
                                }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("9. Músculo Esquelético (%)", skeletalPctText, { skeletalPctText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("10. Massa Muscular (kg)", muscleMassKgText, { muscleMassKgText = it }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("11. Massa Óssea (kg)", boneMassText, { boneMassText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("12. Proteína (%)", proteinText, { proteinText = it }, modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecompFieldInput("13. TMB (kcal)", bmrText, { bmrText = it }, modifier = Modifier.weight(1f))
                                RecompFieldInput("14. Idade Metabólica", metAgeText, { metAgeText = it }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Rodapé do Modal (Salvar / Atualizar Check-in)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RecompCardBg)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RecompCardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "CANCELAR",
                            color = RecompSecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            AudioSynth.playTickSound()
                            fun parseOptionalFloat(txt: String): Float =
                                txt.replace(",", ".").trim().toFloatOrNull() ?: 0f
                            fun parseOptionalInt(txt: String): Int =
                                txt.trim().toIntOrNull() ?: 0

                            val parsedWeight = parseOptionalFloat(weightText)
                            // Se o usuário digitou Água, Proteína ou Músculo Esquelético em kg (padrão Relaxmedic),
                            // converte automaticamente para % ao salvar!
                            val rawWater = parseOptionalFloat(waterText)
                            val effectiveWaterPct = if (rawWater in 25f..47.5f && parsedWeight > 50f) {
                                BioimpedanceImageParser.convertKgMetricToPercent(rawWater, parsedWeight)
                            } else rawWater

                            val rawProtein = parseOptionalFloat(proteinText)
                            val effectiveProteinPct = if (rawProtein in 10f..18.2f && sourceAppText.contains("Relax", ignoreCase = true)) {
                                BioimpedanceImageParser.convertKgMetricToPercent(rawProtein, parsedWeight)
                            } else rawProtein

                            val rawSkeletal = parseOptionalFloat(skeletalPctText)
                            val effectiveSkeletalPct = if (rawSkeletal in 20f..37.5f && parsedWeight > 50f) {
                                BioimpedanceImageParser.convertKgMetricToPercent(rawSkeletal, parsedWeight)
                            } else rawSkeletal

                            val finalDateLabel = dateTimeLabel.ifBlank { initialDraft.dateTimeLabel }
                            val resolvedTimestamp = BioimpedanceImageParser.parseDateTimeMillis(
                                dateTimeLabel = finalDateLabel,
                                fallbackMillis = editingRecord?.timestampMillis ?: System.currentTimeMillis()
                            )

                            val saved = BioimpedanceRecord(
                                id = editingRecord?.id ?: "recomp_${UUID.randomUUID()}",
                                timestampMillis = resolvedTimestamp,
                                dateTimeLabel = finalDateLabel,
                                athleteName = initialDraft.athleteName,
                                sourceApp = sourceAppText.ifBlank { "Entrada Manual" },
                                weightKg = parsedWeight,
                                bmi = parseOptionalFloat(bmiText),
                                bodyFatPercent = parseOptionalFloat(bfText),
                                muscleRatePercent = parseOptionalFloat(muscleRateText),
                                fatFreeMassKg = parseOptionalFloat(ffmText),
                                subcutaneousFatPercent = parseOptionalFloat(subFatText),
                                visceralFatIndex = parseOptionalFloat(visceralText),
                                bodyWaterPercent = effectiveWaterPct,
                                skeletalMusclePercent = effectiveSkeletalPct,
                                muscleMassKg = parseOptionalFloat(muscleMassKgText),
                                boneMassKg = parseOptionalFloat(boneMassText),
                                proteinPercent = effectiveProteinPct,
                                bmrKcal = parseOptionalInt(bmrText),
                                metabolicAge = parseOptionalInt(metAgeText),
                                photoUri = selectedImageUri?.toString() ?: editingRecord?.photoUri,
                                segmentalSummary = segmentalSummaryText,
                                explicitFatMassKg = parsedExplicitFatMassKg
                            )
                            onSaveRecord(saved)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecompBlue,
                            contentColor = Color(0xFF06101E)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("btn_save_recomp_checkin")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (editingRecord != null) "ATUALIZAR CHECK-IN" else "SALVAR CHECK-IN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
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
private fun RecompFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = true,
        keyboardOptions = if (isNumeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RecompCyan,
            unfocusedBorderColor = RecompCardBorder,
            focusedLabelColor = RecompCyan,
            unfocusedLabelColor = RecompSecondaryText,
            focusedTextColor = RecompPrimaryText,
            unfocusedTextColor = RecompPrimaryText
        ),
        modifier = modifier
    )
}

/**
 * Pílula de status blindada (`maxLines = 1, softWrap = false`) para nunca quebrar palavras ao meio.
 */
@Composable
private fun RecompStatusPill(
    text: String,
    color: Color,
    fixedWidthDp: Int? = null
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(6.dp),
        modifier = if (fixedWidthDp != null) Modifier.width(fixedWidthDp.dp) else Modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecompBadgePill(
    text: String,
    accentColor: Color
) {
    Surface(
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
