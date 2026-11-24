package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.WorkoutMetricsResult
import java.util.Locale

@Composable
fun WorkoutMetricsAndSplitsSection(
    metrics: WorkoutMetricsResult,
    isOutdoorGps: Boolean,
    selectedSplitInterval: Float,
    onSplitIntervalChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOutdoorGps) return

    val ptBrLocale = remember { Locale("pt", "BR") }

    val formatPace = remember {
        { paceSec: Int? ->
            if (paceSec != null && paceSec > 0) {
                val m = paceSec / 60
                val s = paceSec % 60
                String.format(Locale.US, "%d'%02d\" /km", m, s)
            } else {
                "--'--\" /km"
            }
        }
    }

    val formatTime = remember {
        { totalSec: Int ->
            val m = totalSec / 60
            val s = totalSec % 60
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Três Cards Compactos: EVOLUÇÃO | CARGA | RECUPERAÇÃO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // EVOLUÇÃO
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF262630)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("EVOLUÇÃO", color = Color(0xFFFFD740), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(metrics.evolutionDurationSec), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(ptBrLocale, "%.2f km", metrics.evolutionDistanceMeters / 1000.0), color = Color(0xFF9E9EA8), fontSize = 12.sp)
                    Text(formatPace(metrics.evolutionAvgPaceSecPerKm), color = Color.White, fontSize = 12.sp)
                }
            }

            // CARGA
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF262630)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("CARGA", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(metrics.cargaDurationSec), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(ptBrLocale, "%.2f km", metrics.cargaDistanceMeters / 1000.0), color = Color(0xFF9E9EA8), fontSize = 12.sp)
                    Text("${metrics.cargaCaloriesKcal} kcal", color = Color.White, fontSize = 12.sp)
                }
            }

            // RECUPERAÇÃO
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF262630)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("RECUPERAÇÃO", color = Color(0xFF69F0AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Caminhadas", color = Color(0xFF9E9EA8), fontSize = 12.sp)
                    Text(formatPace(metrics.recuperationAvgPaceSecPerKm), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Seção Parciais
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF33333F))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Parciais", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                // Seletor Segmentado (0,25 | 0,5 | 1 | Bloco) >= 48dp touch targets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val intervals = listOf(
                        0.25f to "0,25 km",
                        0.5f to "0,5 km",
                        1.0f to "1 km",
                        -1.0f to "Bloco"
                    )
                    intervals.forEach { (value, label) ->
                        val isSelected = selectedSplitInterval == value
                        OutlinedButton(
                            onClick = { onSplitIntervalChanged(value) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) Color(0xFFFFD740).copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isSelected) Color(0xFFFFD740) else Color(0xFF9E9EA8)
                            ),
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) Color(0xFFFFD740) else Color(0xFF444452)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.semantics {
                                    selected = isSelected
                                }
                            )
                        }
                    }
                }

                if (metrics.partialSplits.isEmpty()) {
                    Text("Sem parciais disponíveis", color = Color(0xFF9E9EA8), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    // Tabela de Parciais
                    val fastestPace = metrics.partialSplits.mapNotNull { it.paceSecPerKm }.minOrNull() ?: 1.0

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.partialSplits.forEachIndexed { index, split ->
                            val isFastest = split.paceSecPerKm != null && split.paceSecPerKm == fastestPace
                            val rowBg = if (isFastest) Color(0xFFFFD740).copy(alpha = 0.12f) else Color(0xFF262630).copy(alpha = 0.5f)
                            
                            // Barra proporcional de velocidade
                            val speedRatio = if (split.paceSecPerKm != null && split.paceSecPerKm > 0) {
                                (fastestPace.toFloat() / split.paceSecPerKm.toFloat()).coerceIn(0.1f, 1.0f)
                            } else {
                                0.3f
                            }

                            val desc = "Parcial ${index + 1}, pace ${split.paceSecPerKm?.let { formatPace(it) } ?: "desconhecido"}"

                            Surface(
                                color = rowBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = desc }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = split.title,
                                            color = if (isFastest) Color(0xFFFFD740) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                        Text(
                                            text = formatTime(split.durationSec),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatPace(split.paceSecPerKm),
                                            color = if (isFastest) Color(0xFFFFD740) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1.4f)
                                        )
                                    }

                                    // Barra proporcional de velocidade/ritmo
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(Color(0xFF33333F), RoundedCornerShape(2.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(speedRatio)
                                                .fillMaxHeight()
                                                .background(
                                                    if (isFastest) Color(0xFFFFD740) else Color(0xFF9E9EA8),
                                                    RoundedCornerShape(2.dp)
                                                )
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
