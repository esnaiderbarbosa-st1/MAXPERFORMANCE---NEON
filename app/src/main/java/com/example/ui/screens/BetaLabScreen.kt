package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.utils.customViewForScrollCapture
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.data.model.BetaEvaluationEntity
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.Primary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceDim
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BetaLabScreen(viewModel: MainViewModel) {
    val evaluations by viewModel.betaEvaluations.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var selectedEvaluationForReport by remember { mutableStateOf<BetaEvaluationEntity?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val appVersion = BuildConfig.VERSION_NAME
    val buildNumber = "#${BuildConfig.VERSION_CODE}"
    val buildDate = "24/07/2026"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // TOP NAV BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.activeScreen.value = AppScreen.PROFILE },
                        modifier = Modifier
                            .size(38.dp)
                            .background(SurfaceDim, CircleShape)
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar ao Perfil",
                            tint = Color.White
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "LABORATÓRIO BETA",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Avalie esta versão do aplicativo após seus treinos.",
                            color = OnSurfaceDim,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .customViewForScrollCapture(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
            ) {
                // INFO CARD DO APK / BUILD
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                Column {
                                    Text(
                                        text = "DETALHES DA BUILD ATUAL",
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Versão $appVersion ($buildNumber)",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "BETA LAB",
                                        color = Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Data da Build", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text(buildDate, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Avaliações Enviadas", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text("${evaluations.size} enviadas", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Status", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text("Ativo (Offline)", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Button(
                                onClick = { showForm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.AddCircle, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "NOVA AVALIAÇÃO",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // ================= SPRINT UX-002: COBERTURA DA BIBLIOTECA VISUAL =================
                item {
                    val sessionFallbacks by com.example.utils.IllustrationMetricsManager.sessionFallbackCount.collectAsState()
                    val missingLogs by com.example.utils.IllustrationMetricsManager.missingLogs.collectAsState()
                    val availableCount by com.example.utils.IllustrationMetricsManager.availableIllustrationsCount.collectAsState()
                    val totalNeeded = com.example.utils.IllustrationMetricsManager.totalNeededIllustrations
                    val coveragePct = com.example.utils.IllustrationMetricsManager.getCoveragePercentage()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "BIBLIOTECA VISUAL & ILUSTRAÇÕES",
                                        color = Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Text(
                                    text = String.format(Locale.US, "%.1f%% Cobertura", coveragePct),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Necessárias", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text("$totalNeeded", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Disponíveis", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text("$availableCount", color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Fallbacks Sessão", color = OnSurfaceDim, fontSize = 10.sp)
                                    Text(
                                        text = "$sessionFallbacks",
                                        color = if (sessionFallbacks > 0) Color(0xFFFFB74D) else Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            if (missingLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "REGISTROS [ILLUSTRATION MISSING] (${missingLogs.size})",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    missingLogs.take(5).forEach { log ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF1B1B1E), RoundedCornerShape(8.dp))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = log.exerciseName,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = log.equipment,
                                                        color = Primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                                Text(
                                                    text = "id: ${log.illustrationId}",
                                                    color = OnSurfaceDim,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            val allLogsText = missingLogs.joinToString("\n\n") { it.logOutput }
                                            clipboardManager.setText(AnnotatedString(allLogsText))
                                        }
                                    ) {
                                        Text("Copiar Logs", color = Primary, fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            com.example.utils.IllustrationMetricsManager.clearSessionLogs()
                                        }
                                    ) {
                                        Text("Limpar", color = OnSurfaceDim, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ESTATÍSTICAS PANEL
                item {
                    val totalBugs = evaluations.count { it.bugsText.isNotBlank() }
                    val avgRating = if (evaluations.isNotEmpty()) {
                        evaluations.map { it.ratingOverallQuality }.average()
                    } else 0.0

                    // Count checked checklist items
                    val checklistCounts = mutableMapOf<String, Int>()
                    evaluations.forEach { eval ->
                        if (eval.checkLoadProgression) checklistCounts["Progressão de Carga"] = (checklistCounts["Progressão de Carga"] ?: 0) + 1
                        if (eval.checkImages) checklistCounts["Imagens"] = (checklistCounts["Imagens"] ?: 0) + 1
                        if (eval.checkGifs) checklistCounts["GIFs"] = (checklistCounts["GIFs"] ?: 0) + 1
                        if (eval.checkTimer) checklistCounts["Timer"] = (checklistCounts["Timer"] ?: 0) + 1
                        if (eval.checkSound) checklistCounts["Som"] = (checklistCounts["Som"] ?: 0) + 1
                        if (eval.checkFullNames) checklistCounts["Nome Completo"] = (checklistCounts["Nome Completo"] ?: 0) + 1
                        if (eval.checkCorrectExercises) checklistCounts["Exercícios"] = (checklistCounts["Exercícios"] ?: 0) + 1
                        if (eval.checkWarmup) checklistCounts["Aquecimento"] = (checklistCounts["Aquecimento"] ?: 0) + 1
                        if (eval.checkDragDrop) checklistCounts["Drag & Drop"] = (checklistCounts["Drag & Drop"] ?: 0) + 1
                        if (eval.checkReplacement) checklistCounts["Substituição"] = (checklistCounts["Substituição"] ?: 0) + 1
                        if (eval.checkTodaySessionScreen) checklistCounts["Sessão de Hoje"] = (checklistCounts["Sessão de Hoje"] ?: 0) + 1
                        if (eval.checkExecutionScreen) checklistCounts["Execução"] = (checklistCounts["Execução"] ?: 0) + 1
                        if (eval.checkFinalScreen) checklistCounts["Tela Final"] = (checklistCounts["Tela Final"] ?: 0) + 1
                    }

                    val topMarked = checklistCounts.entries.sortedByDescending { it.value }.take(3)
                    val versionCounts = evaluations.groupBy { it.appVersion }.mapValues { it.value.size }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Assessment, null, tint = Primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "PAINEL DE ESTATÍSTICAS BETA",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Total Avaliações", color = OnSurfaceDim, fontSize = 10.sp)
                                        Text("${evaluations.size}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Bugs Relatados", color = OnSurfaceDim, fontSize = 10.sp)
                                        Text("$totalBugs", color = if (totalBugs > 0) Color(0xFFFF5252) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Média Geral", color = OnSurfaceDim, fontSize = 10.sp)
                                        Text(
                                            text = if (evaluations.isNotEmpty()) "%.1f ★".format(avgRating) else "-",
                                            color = Primary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            if (topMarked.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Recursos Mais Confirmados (Checklist):", color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        topMarked.forEach { (feature, count) ->
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "✓ $feature ($count)",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (versionCounts.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Histórico por Versão:", color = OnSurfaceDim, fontSize = 11.sp)
                                    Text(
                                        text = versionCounts.entries.joinToString(" • ") { "${it.key}: ${it.value}" },
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION TITLE FOR HISTÓRICO
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "HISTÓRICO DE AVALIAÇÕES",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "${evaluations.size} salvas",
                            color = OnSurfaceDim,
                            fontSize = 11.sp
                        )
                    }
                }

                // HISTÓRICO LIST ITEMS
                if (evaluations.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.RateReview, null, tint = OnSurfaceDim, modifier = Modifier.size(32.dp))
                                Text(
                                    text = "Nenhuma avaliação registrada ainda.",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Toque em 'NOVA AVALIAÇÃO' para enviar o seu primeiro relatório de experiência do aplicativo.",
                                    color = OnSurfaceDim,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(evaluations, key = { it.id }) { item ->
                        val dateFormatted = remember(item.timestamp) {
                            SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                        }

                        val (priorityBg, priorityFg) = when (item.priority) {
                            "Crítica" -> Pair(Color(0xFFFF3333), Color.White)
                            "Alta" -> Pair(Color(0xFFFF9100), Color.Black)
                            "Média" -> Pair(Color(0xFFFFD600), Color.Black)
                            else -> Pair(Color(0xFF00E676), Color.Black)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEvaluationForReport = item },
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(priorityBg, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = item.priority.uppercase(),
                                                color = priorityFg,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Text(
                                            text = dateFormatted,
                                            color = OnSurfaceDim,
                                            fontSize = 11.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteBetaEvaluation(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Excluir Avaliação",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Treino: ${item.workoutType} (${item.sessionTimeMinutes} min)",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (item.bugsText.isNotBlank()) {
                                            Text(
                                                text = "Bug: ${item.bugsText}",
                                                color = Color(0xFFFF8A80),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "★ ${item.ratingOverallQuality}/5",
                                            color = Primary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "Tocar para abrir relatório Markdown ➔",
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // DIALOG DO FORMULÁRIO DE AVALIAÇÃO
        if (showForm) {
            BetaEvaluationFormDialog(
                appVersion = appVersion,
                buildNumber = buildNumber,
                buildDate = buildDate,
                onDismiss = { showForm = false },
                onSubmitted = { evaluation ->
                    viewModel.saveBetaEvaluation(evaluation)
                    showForm = false
                    selectedEvaluationForReport = evaluation
                }
            )
        }

        // DIALOG DO RELATÓRIO MARKDOWN
        if (selectedEvaluationForReport != null) {
            val eval = selectedEvaluationForReport!!
            val markdownReport = remember(eval) { eval.toMarkdownReport() }

            Dialog(onDismissRequest = { selectedEvaluationForReport = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Description, null, tint = Primary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "RELATÓRIO MARKDOWN",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            IconButton(onClick = { selectedEvaluationForReport = null }) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }

                        // MARKDOWN CONTENT PREVIEW
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = markdownReport,
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // ACTION BUTTONS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, markdownReport)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Compartilhar Avaliação Beta")
                                    context.startActivity(shareIntent)
                                },
                                border = BorderStroke(1.dp, Primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Share, null, tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compartilhar", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(markdownReport))
                                    Toast.makeText(context, "Markdown copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDim),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar MD", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { selectedEvaluationForReport = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Concluir", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BetaEvaluationFormDialog(
    appVersion: String,
    buildNumber: String,
    buildDate: String,
    onDismiss: () -> Unit,
    onSubmitted: (BetaEvaluationEntity) -> Unit
) {
    // Form fields state
    var workoutType by remember { mutableStateOf("Peito") }
    var sessionTimeMinutes by remember { mutableStateOf("45") }

    // 8 Rating Stars (1 to 5)
    var ratingExCount by remember { mutableIntStateOf(5) }
    var ratingOrderQuality by remember { mutableIntStateOf(5) }
    var ratingVariety by remember { mutableIntStateOf(5) }
    var ratingLoadProgression by remember { mutableIntStateOf(5) }
    var ratingEstimatedTime by remember { mutableIntStateOf(5) }
    var ratingRecommendation by remember { mutableIntStateOf(5) }
    var ratingEaseOfUse by remember { mutableIntStateOf(5) }
    var ratingOverallQuality by remember { mutableIntStateOf(5) }

    // 13 Checklist booleans
    var checkLoadProgression by remember { mutableStateOf(true) }
    var checkImages by remember { mutableStateOf(true) }
    var checkGifs by remember { mutableStateOf(true) }
    var checkTimer by remember { mutableStateOf(true) }
    var checkSound by remember { mutableStateOf(true) }
    var checkFullNames by remember { mutableStateOf(true) }
    var checkCorrectExercises by remember { mutableStateOf(true) }
    var checkWarmup by remember { mutableStateOf(true) }
    var checkDragDrop by remember { mutableStateOf(true) }
    var checkReplacement by remember { mutableStateOf(true) }
    var checkTodaySessionScreen by remember { mutableStateOf(true) }
    var checkExecutionScreen by remember { mutableStateOf(true) }
    var checkFinalScreen by remember { mutableStateOf(true) }

    // Bugs & Suggestions
    var bugsText by remember { mutableStateOf("") }
    var suggestionsText by remember { mutableStateOf("") }

    // Priority
    var priority by remember { mutableStateOf("Média") }

    val workoutOptions = listOf("Peito", "Costas", "Pernas", "Ombros", "Braços", "Full Body", "Cardio")
    val timeOptions = listOf("30", "45", "60", "90+")
    val priorityOptions = listOf("Crítica", "Alta", "Média", "Baixa")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(2.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // DIALOG HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.RateReview, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "AVALIAÇÃO DE TREINO BETA",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SEÇÃO 1: TREINO REALIZADO & TEMPO DA SESSÃO
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "SEÇÃO 1: TREINO REALIZADO & TEMPO",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Text("Treino realizado:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(workoutOptions) { option ->
                                    FilterChip(
                                        selected = workoutType == option,
                                        onClick = { workoutType = option },
                                        label = { Text(option, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Text("Tempo da sessão (minutos):", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                timeOptions.forEach { option ->
                                    FilterChip(
                                        selected = sessionTimeMinutes == option,
                                        onClick = { sessionTimeMinutes = option },
                                        label = { Text("$option min", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Primary,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // SEÇÃO 2: EXPERIÊNCIA (STARS 1-5)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "SEÇÃO 2: EXPERIÊNCIA",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            RatingRow("Quantidade de exercícios", ratingExCount) { ratingExCount = it }
                            RatingRow("Qualidade da ordem", ratingOrderQuality) { ratingOrderQuality = it }
                            RatingRow("Variedade", ratingVariety) { ratingVariety = it }
                            RatingRow("Progressão de carga", ratingLoadProgression) { ratingLoadProgression = it }
                            RatingRow("Tempo estimado", ratingEstimatedTime) { ratingEstimatedTime = it }
                            RatingRow("Recomendação dos exercícios", ratingRecommendation) { ratingRecommendation = it }
                            RatingRow("Facilidade de uso", ratingEaseOfUse) { ratingEaseOfUse = it }
                            RatingRow("Qualidade geral", ratingOverallQuality) { ratingOverallQuality = it }
                        }
                    }

                    // SEÇÃO 3: CHECKLIST
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SEÇÃO 3: CHECKLIST (MARCAÇÃO RÁPIDA)",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            ChecklistRow("Progressão de carga correta", checkLoadProgression) { checkLoadProgression = it }
                            ChecklistRow("Imagens corretas", checkImages) { checkImages = it }
                            ChecklistRow("GIF funcionando", checkGifs) { checkGifs = it }
                            ChecklistRow("Timer correto", checkTimer) { checkTimer = it }
                            ChecklistRow("Som correto", checkSound) { checkSound = it }
                            ChecklistRow("Nome completo dos exercícios", checkFullNames) { checkFullNames = it }
                            ChecklistRow("Exercícios corretos", checkCorrectExercises) { checkCorrectExercises = it }
                            ChecklistRow("Aquecimento correto", checkWarmup) { checkWarmup = it }
                            ChecklistRow("Drag & Drop funcionando", checkDragDrop) { checkDragDrop = it }
                            ChecklistRow("Substituição funcionando", checkReplacement) { checkReplacement = it }
                            ChecklistRow("Tela Sessão de Hoje correta", checkTodaySessionScreen) { checkTodaySessionScreen = it }
                            ChecklistRow("Tela Execução correta", checkExecutionScreen) { checkExecutionScreen = it }
                            ChecklistRow("Tela Final correta", checkFinalScreen) { checkFinalScreen = it }
                        }
                    }

                    // SEÇÃO 4: BUGS
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SEÇÃO 4: BUGS",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Text("Encontrou algum bug?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = bugsText,
                                onValueChange = { bugsText = it },
                                placeholder = {
                                    Text(
                                        "Ex: O primeiro exercício apareceu como Em execução antes do treino iniciar.",
                                        color = OnSurfaceDim,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // SEÇÃO 5: SUGESTÕES
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SEÇÃO 6: SUGESTÕES",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Text("Sugestões de melhoria:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = suggestionsText,
                                onValueChange = { suggestionsText = it },
                                placeholder = {
                                    Text(
                                        "Ex: O botão substituir deveria ficar no card.",
                                        color = OnSurfaceDim,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // SEÇÃO 6: PRIORIDADE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SEÇÃO 6: PRIORIDADE",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                priorityOptions.forEach { option ->
                                    val isSel = priority == option
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { priority = option },
                                        label = { Text(option, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = when (option) {
                                                "Crítica" -> Color(0xFFFF3333)
                                                "Alta" -> Color(0xFFFF9100)
                                                "Média" -> Color(0xFFFFD600)
                                                else -> Color(0xFF00E676)
                                            },
                                            selectedLabelColor = if (option == "Crítica") Color.White else Color.Black,
                                            containerColor = Color.White.copy(alpha = 0.05f),
                                            labelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SUBMIT BUTTON
                Button(
                    onClick = {
                        val newEvaluation = BetaEvaluationEntity(
                            appVersion = appVersion,
                            buildNumber = buildNumber,
                            buildDate = buildDate,
                            workoutType = workoutType,
                            sessionTimeMinutes = sessionTimeMinutes,
                            ratingExCount = ratingExCount,
                            ratingOrderQuality = ratingOrderQuality,
                            ratingVariety = ratingVariety,
                            ratingLoadProgression = ratingLoadProgression,
                            ratingEstimatedTime = ratingEstimatedTime,
                            ratingRecommendation = ratingRecommendation,
                            ratingEaseOfUse = ratingEaseOfUse,
                            ratingOverallQuality = ratingOverallQuality,
                            checkLoadProgression = checkLoadProgression,
                            checkImages = checkImages,
                            checkGifs = checkGifs,
                            checkTimer = checkTimer,
                            checkSound = checkSound,
                            checkFullNames = checkFullNames,
                            checkCorrectExercises = checkCorrectExercises,
                            checkWarmup = checkWarmup,
                            checkDragDrop = checkDragDrop,
                            checkReplacement = checkReplacement,
                            checkTodaySessionScreen = checkTodaySessionScreen,
                            checkExecutionScreen = checkExecutionScreen,
                            checkFinalScreen = checkFinalScreen,
                            bugsText = bugsText.trim(),
                            suggestionsText = suggestionsText.trim(),
                            priority = priority
                        )
                        onSubmitted(newEvaluation)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FINALIZAR E GERAR RELATÓRIO",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    label: String,
    currentRating: Int,
    onRatingSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..5).forEach { star ->
                val isFilled = star <= currentRating
                Icon(
                    imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "$star estrelas",
                    tint = if (isFilled) Primary else OnSurfaceDim,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onRatingSelected(star) }
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Primary,
                uncheckedColor = OnSurfaceDim,
                checkmarkColor = Color.Black
            )
        )

        Text(
            text = label,
            color = if (checked) Color.White else OnSurfaceDim,
            fontSize = 12.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
        )
    }
}
