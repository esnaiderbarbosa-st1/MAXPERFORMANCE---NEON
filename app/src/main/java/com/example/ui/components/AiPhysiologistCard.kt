package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.ai.AiPhysiologistAnalyzer
import com.example.services.ai.EffortPerception
import com.example.services.ai.GymLoadAdjustment
import com.example.services.ai.HrSourceType
import com.example.services.ai.PhysiologistAnalysisResult
import com.example.services.ai.PreAnalysisCheckIn
import com.example.utils.AudioSynth
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AiPhysiologistCard(
    sessionId: String,
    isRunningMode: Boolean,
    initialCachedReport: String? = null,
    initialAdjustmentsJson: String? = null,
    defaultHasHrSensor: Boolean = true,
    isLightMode: Boolean = false,
    onGenerateAnalysis: suspend (checkIn: PreAnalysisCheckIn, forceCloud: Boolean) -> PhysiologistAnalysisResult,
    onApplyLoadAdjustments: ((List<GymLoadAdjustment>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ptBr = remember { Locale("pt", "BR") }

    val bleManager = remember { com.example.services.HeartRateBleManager.getInstance(context) }
    val detectedSmartwatchName = remember(sessionId) {
        val connName = bleManager.connectedDeviceName.value?.trim().orEmpty()
        val linkedName = bleManager.linkedSmartwatchName.value?.trim().orEmpty()
        val candidate = connName.ifBlank { linkedName }
        val isChestStrap = candidate.isNotBlank() && listOf(
            "polar h", "h10", "h9", "hrm", "magene", "coospo", "geoid", "gehod", "h500", "wahoo", "tickr", "cinta"
        ).any { candidate.contains(it, ignoreCase = true) }
        val isWatch = candidate.isNotBlank() && !isChestStrap && listOf(
            "watch", "redmi", "xiaomi", "band", "amazfit", "galaxy", "garmin", "apple", "haylou", "mi ", "coros", "huawei", "fitbit", "zeblaze"
        ).any { candidate.contains(it, ignoreCase = true) }
        if (isWatch) candidate else if (!isChestStrap && bleManager.isSmartwatchBridgeActive.value && linkedName.isNotBlank()) linkedName else null
    }

    var selectedEffort by remember(sessionId) {
        mutableStateOf(EffortPerception.ESFORCO_CONTROLADO)
    }
    var selectedHrSource by remember(sessionId, defaultHasHrSensor, detectedSmartwatchName) {
        mutableStateOf(
            when {
                !defaultHasHrSensor -> HrSourceType.SEM_SENSOR
                detectedSmartwatchName != null -> HrSourceType.SMARTWATCH
                else -> HrSourceType.CINTA_PEITORAL
            }
        )
    }
    var customNote by remember(sessionId) { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var adjustmentsApplied by remember(sessionId) { mutableStateOf(false) }
    var showCheckInControls by remember(sessionId) { mutableStateOf(true) }

    var analysisResult by remember(sessionId, initialCachedReport, initialAdjustmentsJson) {
        mutableStateOf(
            AiPhysiologistAnalyzer.getCachedAnalysisForSession(
                context = context,
                sessionId = sessionId,
                fallbackReportText = initialCachedReport,
                fallbackAdjustmentsJson = initialAdjustmentsJson
            )
        )
    }

    LaunchedEffect(analysisResult != null) {
        if (analysisResult != null) {
            showCheckInControls = false
        }
    }

    LaunchedEffect(sessionId, defaultHasHrSensor, detectedSmartwatchName, analysisResult?.reportText) {
        val currentReport = analysisResult?.reportText ?: return@LaunchedEffect
        val hasStaleTelemetryFormat = isRunningMode && (
            (defaultHasHrSensor && (
                currentReport.contains("Fonte de FC: Sem sensor", ignoreCase = true) ||
                    currentReport.contains("BPM médio/máximo e distribuição em zonas: sem dados", ignoreCase = true)
                )) ||
                (detectedSmartwatchName != null && currentReport.contains("Fonte de FC: Cinta Peitoral", ignoreCase = true)) ||
                currentReport.contains("queda média de +", ignoreCase = true) ||
                (currentReport.contains("Distância total:") && !currentReport.contains("Treino Principal (Estímulos + Recuperações):"))
            )
        if (hasStaleTelemetryFormat && !isGenerating) {
            val effectiveHrSource = when {
                detectedSmartwatchName != null -> HrSourceType.SMARTWATCH
                selectedHrSource == HrSourceType.SEM_SENSOR && defaultHasHrSensor -> HrSourceType.CINTA_PEITORAL
                else -> selectedHrSource
            }
            selectedHrSource = effectiveHrSource
            val refreshed = onGenerateAnalysis(
                PreAnalysisCheckIn(
                    effortPerception = selectedEffort,
                    hrSource = effectiveHrSource,
                    customNote = customNote.trim()
                ),
                false
            )
            analysisResult = refreshed
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val cardBg = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF131824)
    val innerBg = if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF1A2130)
    val cardBorder = if (isLightMode) Color(0xFFCBD5E1) else Color(0xFF283246)
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val secondaryText = if (isLightMode) Color(0xFF475569) else Color(0xFF94A3B8)
    val accentCyan = Color(0xFF0EA5E9)
    val accentEmerald = Color(0xFF10B981)
    val accentAmber = Color(0xFFF59E0B)

    val hasApiKey = remember(analysisResult) { AiPhysiologistAnalyzer.hasValidApiKey(context) }
    val remainingQuota = remember(analysisResult) { AiPhysiologistAnalyzer.getRemainingCloudQuotaToday(context) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_physiologist_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.2.dp, accentCyan.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- HEADER ---
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accentCyan, Color(0xFF6366F1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "TREINADOR FISIOLOGISTA IA",
                            color = primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = (-0.3).sp
                        )
                        val subtitleText = when {
                            analysisResult != null -> analysisResult!!.sourceLabel
                            hasApiKey && remainingQuota > 0 -> "Gemini 2.5 Flash • Cota hoje: $remainingQuota disponível"
                            hasApiKey -> "Cota Cloud 1x/dia usada • Motor Fisiológico Local pronto"
                            else -> "Motor Fisiológico Pós-Treino • Híbrido (Gym + Run)"
                        }
                        Text(
                            text = subtitleText,
                            color = accentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (analysisResult != null) {
                    Surface(
                        onClick = {
                            AudioSynth.playTickSound()
                            showCheckInControls = !showCheckInControls
                        },
                        color = innerBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Editar Check-in",
                                tint = secondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (showCheckInControls) "Ocultar" else "Check-in",
                                color = secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- MICRO-CHECK-IN PRÉ-ANÁLISE ---
            AnimatedVisibility(visible = showCheckInControls || analysisResult == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(innerBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "MICRO-CHECK-IN PÓS-TREINO (1 TOQUE OPCIONAL)",
                        color = secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )

                    // 1. Sensação Final
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Sensação Final:",
                            color = primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EffortPerception.entries.forEach { option ->
                                val isSelected = selectedEffort == option
                                val chipColor = Color(option.badgeColorHex)
                                Surface(
                                    onClick = {
                                        selectedEffort = option
                                        AudioSynth.playTickSound()
                                    },
                                    color = if (isSelected) chipColor.copy(alpha = 0.22f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) chipColor else cardBorder
                                    )
                                ) {
                                    Text(
                                        text = option.label,
                                        color = if (isSelected) chipColor else secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Fonte de FC (Apenas na Corrida)
                    if (isRunningMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Fonte de Frequência Cardíaca:",
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                HrSourceType.entries.forEach { source ->
                                    val isSelected = selectedHrSource == source
                                    Surface(
                                        onClick = {
                                            selectedHrSource = source
                                            AudioSynth.playTickSound()
                                        },
                                        color = if (isSelected) accentCyan.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) accentCyan else cardBorder
                                        )
                                    ) {
                                        Text(
                                            text = source.label,
                                            color = if (isSelected) accentCyan else secondaryText,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Observação Curta Opcional
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { if (it.length <= 140) customNote = it },
                        label = {
                            Text(
                                if (isRunningMode) "Observação curta (ex: Cinta bem ajustada, vento forte)"
                                else "Observação curta (ex: Dor leve no ombro, pegada firme)",
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = primaryText,
                            unfocusedTextColor = primaryText,
                            focusedBorderColor = accentCyan,
                            unfocusedBorderColor = cardBorder,
                            focusedLabelColor = accentCyan,
                            unfocusedLabelColor = secondaryText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_physiologist_note")
                    )
                }
            }

            // --- BOTÃO PRINCIPAL DE GERAR ANÁLISE ---
            if (analysisResult == null || showCheckInControls) {
                Button(
                    onClick = {
                        if (isGenerating) return@Button
                        AudioSynth.playTickSound()
                        isGenerating = true
                        coroutineScope.launch {
                            try {
                                val checkIn = PreAnalysisCheckIn(
                                    effortPerception = selectedEffort,
                                    hrSource = selectedHrSource,
                                    customNote = customNote.trim()
                                )
                                val result = onGenerateAnalysis(checkIn, false)
                                analysisResult = result
                                showCheckInControls = false
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = accentCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_generate_physiologist_analysis")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analisando Fisiologia e Histórico 48h...",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "✨ Gerar Análise do Fisiologista",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // --- EXIBIÇÃO DO RELATÓRIO DO FISIOLOGISTA ---
            analysisResult?.let { result ->
                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Renderiza os parágrafos/seções numeradas com destaque visual limpo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(innerBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lines = remember(result.reportText) {
                        result.reportText.lines().filter { it.isNotBlank() }
                    }
                    lines.forEach { line ->
                        val trimmed = line.trim().replace("**", "")
                        val isSectionHeader = trimmed.matches(Regex("^[1-6]\\..*")) ||
                            trimmed.startsWith("#")
                        if (isSectionHeader) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = trimmed.removePrefix("###").removePrefix("##").removePrefix("#").trim(),
                                color = accentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.2.sp
                            )
                        } else {
                            Text(
                                text = trimmed,
                                color = primaryText,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // --- SEÇÃO DE AJUSTES DE CARGA ACIONÁVEIS (EXCLUSIVO MAX GYM) ---
                if (!isRunningMode && result.loadAdjustments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerBg)
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
                                tint = accentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PRESCRIÇÃO DE CARGA PARA O PRÓXIMO TREINO",
                                color = accentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        result.loadAdjustments.forEach { adj ->
                            val curStr = if (adj.cargaAtualKg % 1f == 0f) "${adj.cargaAtualKg.toInt()}" else String.format(ptBr, "%.1f", adj.cargaAtualKg)
                            val newStr = if (adj.novaCargaSugeridaKg % 1f == 0f) "${adj.novaCargaSugeridaKg.toInt()}" else String.format(ptBr, "%.1f", adj.novaCargaSugeridaKg)
                            val isIncrease = adj.novaCargaSugeridaKg > adj.cargaAtualKg

                            Surface(
                                color = cardBg,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.8.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = adj.exercicio,
                                        color = primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "$curStr kg → $newStr kg × ${adj.metaReps} reps",
                                        color = if (isIncrease) accentEmerald else accentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = adj.motivoCurto,
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        if (onApplyLoadAdjustments != null) {
                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onApplyLoadAdjustments(result.loadAdjustments)
                                    adjustmentsApplied = true
                                    Toast.makeText(
                                        context,
                                        "Cargas e metas de repetições aplicadas para o próximo treino!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (adjustmentsApplied) accentEmerald else accentAmber
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("btn_apply_ai_load_adjustments")
                            ) {
                                Text(
                                    text = if (adjustmentsApplied) {
                                        "✓ Ajustes Aplicados no Próximo Treino"
                                    } else {
                                        "⚡ Aplicar Ajustes no Próximo Treino"
                                    },
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // --- BARRA DE AÇÕES SECUNDÁRIAS (COPIAR PARECER & REGERAR CLOUD) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Parecer Fisiologista MAX", result.reportText)
                            )
                            Toast.makeText(
                                context,
                                "Parecer do Fisiologista copiado!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        border = BorderStroke(1.dp, cardBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = primaryText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copiar Parecer",
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!result.isFromCloud && AiPhysiologistAnalyzer.hasValidApiKey(context)) {
                        OutlinedButton(
                            onClick = {
                                if (isGenerating) return@OutlinedButton
                                AudioSynth.playTickSound()
                                isGenerating = true
                                coroutineScope.launch {
                                    try {
                                        val checkIn = PreAnalysisCheckIn(
                                            effortPerception = selectedEffort,
                                            hrSource = selectedHrSource,
                                            customNote = customNote.trim()
                                        )
                                        analysisResult = onGenerateAnalysis(checkIn, true)
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, accentCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = accentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Regerar Cloud",
                                color = accentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
