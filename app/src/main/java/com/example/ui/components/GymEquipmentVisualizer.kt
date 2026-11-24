package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.BarbellVisual
import com.example.ui.theme.Primary

/**
 * Visualizador contextual de peso de acordo com o tipo real de equipamento:
 * 1. Torre de Placas / Tijolinhos (Cabo, Polia, Máquina de Placas)
 * 2. Par de Halteres em Quilos Inteiros (Halteres)
 * 3. Manga Olímpica com Anilhas por lado (Barra livre / Smith)
 */
@Composable
fun GymEquipmentVisualizer(
    weight: Float,
    equipment: String,
    exerciseName: String = "",
    modifier: Modifier = Modifier
) {
    val inferred = remember(exerciseName, equipment) {
        com.example.utils.WeightCalculator.inferEquipment(exerciseName, equipment)
    }
    val eqLower = (equipment + " " + inferred + " " + exerciseName).lowercase()

    val isDumbbell = eqLower.contains("halter") || eqLower.contains("dumbbell")

    val isPlateLoadedOrBarbell = eqLower.contains("barra") || eqLower.contains("smith") ||
            eqLower.contains("articulad") || eqLower.contains("leg press") ||
            eqLower.contains("hack") || eqLower.contains("elevação pélvica") ||
            eqLower.contains("elevacao pelvica") || eqLower.contains("hip thrust")

    val isCableOrStack = !isDumbbell && !isPlateLoadedOrBarbell && (
            eqLower.contains("cabo") || eqLower.contains("polia") ||
            eqLower.contains("cable") || eqLower.contains("placa") ||
            eqLower.contains("pilha") || eqLower.contains("stack") ||
            eqLower.contains("crossover") || eqLower.contains("pulley") ||
            eqLower.contains("puxada") || eqLower.contains("extensora") ||
            eqLower.contains("flexora") || eqLower.contains("abdutora") ||
            eqLower.contains("adutora") || eqLower.contains("voador") ||
            eqLower.contains("pec deck") || eqLower.contains("máquina") ||
            eqLower.contains("maquina")
    )

    val isBodyweight = eqLower.contains("peso corporal") || eqLower.contains("bodyweight")

    Box(
        modifier = modifier.height(38.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isDumbbell -> {
                DumbbellPairVisual(weight = weight)
            }
            isCableOrStack -> {
                WeightStackTowerVisual(weight = weight)
            }
            isBodyweight -> {
                BodyweightVisual(additionalWeight = weight)
            }
            else -> {
                // Barra / Articulado / Smith — Barra com Anilhas redondas
                BarbellVisual(weight = weight.coerceAtLeast(20f))
            }
        }
    }
}

/**
 * Ilustração compacta da Torre de Placas Retangulares (tijolinhos) com pino seletor.
 */
@Composable
fun WeightStackTowerVisual(
    weight: Float,
    modifier: Modifier = Modifier
) {
    val plateWeight = 5f
    val platesCount = if (weight > 0f) (weight / plateWeight).toInt().coerceIn(1, 15) else 1

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Torre com 5 tijolinhos retangulares empilhados
        Column(
            modifier = Modifier
                .width(24.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF222631))
                .border(1.dp, Color(0xFF384152), RoundedCornerShape(3.dp))
                .padding(1.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val visiblePlates = 5
            for (p in 0 until visiblePlates) {
                val isSelected = p >= (visiblePlates - (platesCount.coerceAtMost(visiblePlates)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isSelected) Color(0xFF00B0FF) else Color(0xFF475569)),
                    contentAlignment = Alignment.Center
                ) {
                    if (p == visiblePlates - 1) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFACC15))
                        )
                    }
                }
            }
        }

        Column {
            Text(
                text = "${weight.toInt()} kg",
                color = Color(0xFFF8FAFC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "$platesCount ${if (platesCount == 1) "placa" else "placas"}",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Ilustração compacta de Par de Halteres (2 halteres pareados com peso estritamente inteiro).
 */
@Composable
fun DumbbellPairVisual(
    weight: Float,
    modifier: Modifier = Modifier
) {
    val validDumbbellWeight = com.example.utils.WeightCalculator.roundToAvailableWeight(
        weight = weight.coerceAtLeast(1f),
        equipment = "Halteres"
    ).toInt()

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Desenho visual de PAR DE HALTERES (2 halteres metálicos empilhados)
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Cabeça esquerda dupla do halter
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFF00B0FF))
                    )
                    // Pegada recartilhada central
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(2.5.dp)
                            .background(Color(0xFFCBD5E1))
                    )
                    // Cabeça direita dupla do halter
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color(0xFF00B0FF))
                    )
                }
            }
        }

        // Texto informativo: "14 kg • par"
        Column {
            Text(
                text = "$validDumbbellWeight kg",
                color = Color(0xFFF8FAFC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "par halteres",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Visual para exercícios de peso corporal.
 */
@Composable
fun BodyweightVisual(
    additionalWeight: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (additionalWeight > 0f) "+${additionalWeight.toInt()}k" else "Livre",
            color = Color(0xFFF8FAFC),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
