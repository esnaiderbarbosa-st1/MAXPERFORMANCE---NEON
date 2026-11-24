package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GymWorkoutSession
import com.example.data.repository.GymHistoryRepository
import com.example.utils.AudioSynth
import com.example.utils.GymShareCardGenerator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSessionDetailDialog(
    session: GymWorkoutSession,
    athleteName: String = "Atleta",
    athleteWeightKg: Float? = null,
    gymName: String = "",
    userProfile: com.example.data.model.UserProfileData? = null,
    allGymSessions: List<GymWorkoutSession> = emptyList(),
    recentRunningSessions: List<com.example.data.model.RunningSession> = emptyList(),
    onApplyLoadAdjustments: ((List<com.example.services.ai.GymLoadAdjustment>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLightMode by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var currentSessionState by remember(session) { mutableStateOf(session) }
    val repoGymSessions by GymHistoryRepository.sessions.collectAsState()
    val effectiveAllGymSessions = if (allGymSessions.isNotEmpty()) allGymSessions else repoGymSessions

    val bgDark = if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0D0F14)
    val cardBg = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF161922)
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF262B38)
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val secondaryText = if (isLightMode) Color(0xFF64748B) else Color(0xFF94A3B8)
    val brandAccent = Color(0xFF0EA5E9) // Ciano
    val goldAccent = Color(0xFFFACC15)

    val ptBr = remember { Locale("pt", "BR") }
    val dateFmt = remember { SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy • HH:mm", ptBr) }
    val dateText = remember(session.date) {
        dateFmt.format(Date(session.date)).replaceFirstChar { it.uppercase() }
    }

    val executedExercises = remember(session.exercises) {
        session.exercises.filter { ex -> ex.sets.any { it.completed } }
    }

    val maxLoadHighlights = remember(executedExercises) {
        executedExercises.mapNotNull { ex ->
            val maxSet = ex.sets.filter { it.completed }.maxByOrNull { it.weight }
            if (maxSet != null && maxSet.weight > 0f) {
                Triple(ex.name, maxSet.weight, maxSet.reps)
            } else null
        }.sortedByDescending { it.second }.take(4)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = bgDark,
            modifier = Modifier.fillMaxSize()
        ) {
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    AudioSynth.playTickSound()
                                    onDismiss()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar",
                                        tint = primaryText
                                    )
                                }
                                Text(
                                    text = "Share Card & Relatório MAX GYM",
                                    color = primaryText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onDelete != null) {
                                    IconButton(onClick = {
                                        AudioSynth.playTickSound()
                                        showDeleteConfirmDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Excluir Treino",
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }

                                // Toggle Sol / Lua (Modo Claro / Escuro)
                                IconButton(onClick = {
                                    AudioSynth.playTickSound()
                                    isLightMode = !isLightMode
                                }) {
                                    Icon(
                                        imageVector = if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = "Alternar Modo",
                                        tint = if (isLightMode) Color(0xFF6366F1) else Color(0xFFFACC15)
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = bgDark,
                bottomBar = {
                    Surface(
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botão 1: COMPARTILHAR SHARE CARD MAX GYM
                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    coroutineScope.launch {
                                        GymShareCardGenerator.shareGymReport(
                                            context = context,
                                            session = session,
                                            athleteName = athleteName,
                                            isLightMode = isLightMode
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "COMPARTILHAR SHARE CARD MAX GYM",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            // Botões 2 e 3 lado a lado: [ Copiar Relatório .TXT ] e [ Exportar .TXT ]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        val reportText = GymHistoryRepository.exportToTextReport(
                                            session = currentSessionState,
                                            athleteName = athleteName,
                                            athleteWeightKg = athleteWeightKg,
                                            gymName = gymName
                                        )
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Relatório MAX GYM", reportText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Relatório .TXT copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                    },
                                    border = BorderStroke(1.dp, brandAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = brandAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Copiar Relatório .TXT",
                                        color = brandAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        val reportText = GymHistoryRepository.exportToTextReport(
                                            session = currentSessionState,
                                            athleteName = athleteName,
                                            athleteWeightKg = athleteWeightKg,
                                            gymName = gymName
                                        )
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, reportText)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Exportar Relatório .TXT")
                                        context.startActivity(shareIntent)
                                    },
                                    border = BorderStroke(1.dp, cardBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = primaryText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Exportar .TXT",
                                        color = primaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                ) {
                    // Header Card com Volume, Duração e Séries
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(20.dp)
                                                .background(brandAccent, RoundedCornerShape(2.dp))
                                        )
                                        Text(
                                            text = session.workoutName.uppercase(),
                                            color = primaryText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }

                                    Surface(
                                        color = brandAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, brandAccent)
                                    ) {
                                        Text(
                                            text = "MAX GYM",
                                            color = brandAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Atleta: $athleteName • $dateText",
                                    color = secondaryText,
                                    fontSize = 12.sp
                                )

                                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                                // 3 Métricas Hero
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Volume (kg)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "VOLUME (KG)",
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val volStr = String.format(ptBr, "%.0f kg", session.totalVolumeKg)
                                        Text(
                                            text = volStr,
                                            color = primaryText,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Duração
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "DURAÇÃO",
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val m = session.durationSeconds / 60
                                        val s = session.durationSeconds % 60
                                        Text(
                                            text = String.format(ptBr, "%02d:%02d", m, s),
                                            color = primaryText,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    // Séries
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "SÉRIES",
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${session.completedSets}/${session.totalSets}",
                                            color = primaryText,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Destaques de Cargas Máximas
                    if (maxLoadHighlights.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, goldAccent.copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = goldAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "DESTAQUES DE CARGAS MÁXIMAS",
                                            color = goldAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }

                                    maxLoadHighlights.forEach { (exName, maxW, reps) ->
                                        val wStr = if (maxW % 1f == 0f) "${maxW.toInt()}" else String.format(ptBr, "%.1f", maxW)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = exName,
                                                color = primaryText,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "$wStr kg × $reps reps",
                                                color = goldAccent,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                fontStyle = FontStyle.Italic,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card Treinador Fisiologista IA Pós-Treino (MAX GYM + Cruzamento 48h)
                    item {
                        AiPhysiologistCard(
                            sessionId = currentSessionState.id,
                            isRunningMode = false,
                            initialCachedReport = currentSessionState.aiCoachReport,
                            initialAdjustmentsJson = currentSessionState.aiLoadAdjustmentsJson,
                            isLightMode = isLightMode,
                            onGenerateAnalysis = { checkIn, forceCloud ->
                                val result = com.example.services.ai.AiPhysiologistAnalyzer.analyzeGymSession(
                                    context = context,
                                    session = currentSessionState,
                                    allGymSessions = effectiveAllGymSessions,
                                    recentRunningSessions = recentRunningSessions,
                                    userProfile = userProfile,
                                    checkIn = checkIn,
                                    forceCloudRegenerate = forceCloud
                                )
                                val adjJson = com.example.services.ai.GymLoadAdjustment.listToJson(result.loadAdjustments)
                                val updated = currentSessionState.copy(
                                    aiCoachReport = result.reportText,
                                    aiLoadAdjustmentsJson = adjJson
                                )
                                currentSessionState = updated
                                GymHistoryRepository.saveSession(context, updated)
                                result
                            },
                            onApplyLoadAdjustments = { adjustments ->
                                if (onApplyLoadAdjustments != null) {
                                    onApplyLoadAdjustments(adjustments)
                                } else {
                                    adjustments.forEach { adj ->
                                        com.example.data.repository.ExerciseHistoryRepository.setAiLoadOverride(
                                            exerciseName = adj.exercicio,
                                            weightKg = adj.novaCargaSugeridaKg,
                                            targetReps = adj.metaReps
                                        )
                                    }
                                    com.example.data.repository.ExerciseHistoryRepository.clearCache()
                                }
                            }
                        )
                    }

                    // Título da Seção dos Exercícios
                    item {
                        Text(
                            text = "APARELHOS, SÉRIES E FEEDBACKS DE CAMPO",
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Lista dos Exercícios Efetivamente Executados (completedSets > 0)
                    itemsIndexed(executedExercises) { index, ex ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(brandAccent.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = brandAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = ex.name,
                                            color = primaryText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (ex.equipment.isNotBlank()) {
                                            Text(
                                                text = ex.equipment,
                                                color = secondaryText,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Tabela de Séries Efetivamente Concluídas deste Aparelho
                                ex.sets.filter { it.completed }.forEach { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Série ${s.setIndex}",
                                            color = secondaryText,
                                            fontSize = 12.sp
                                        )
                                        val isBw = com.example.utils.WeightCalculator.isBodyweightExercise(context, ex.name, ex.equipment)
                                        val isPerDumbbell = com.example.utils.WeightCalculator.isPerDumbbellWeight(context, ex.name, ex.equipment)
                                        val isPerSide = com.example.utils.WeightCalculator.isEachSideReps(context, ex.name, ex.equipment)
                                        val setLabelRight = if (isBw && s.weight <= 0.05f) {
                                            "Peso Corporal x ${s.reps} reps"
                                        } else {
                                            val wStr = com.example.utils.WeightCalculator.formatStepNumber(s.weight)
                                            val wUnit = if (isPerDumbbell) "kg (cada halter)" else "kg"
                                            val rUnit = if (isPerSide) "reps (cada lado)" else "reps"
                                            "$wStr $wUnit x ${s.reps} $rUnit"
                                        }
                                        Text(
                                            text = setLabelRight,
                                            color = primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                }

                                // Anotações do Exercício se houver
                                if (ex.notes.isNotBlank()) {
                                    Surface(
                                        color = if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF1E2330),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = brandAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = ex.notes,
                                                color = primaryText,
                                                fontSize = 11.sp
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

        if (showDeleteConfirmDialog && onDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text("Excluir este treino?", color = primaryText, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "Esta ação removerá permanentemente este registro de treino de musculação do histórico.",
                        color = secondaryText,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete()
                        }
                    ) {
                        Text("EXCLUIR", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("CANCELAR", color = secondaryText)
                    }
                },
                containerColor = cardBg
            )
        }
    }
}
