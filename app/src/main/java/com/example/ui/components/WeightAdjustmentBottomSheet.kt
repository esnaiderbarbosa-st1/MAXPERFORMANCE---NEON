@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale

// Sealed class for load estimation state
sealed class LoadableWeight {
    data class Exact(val total: Double, val plates: List<Double>) : LoadableWeight()
    data class Approximate(
        val target: Double,
        val actual: Double,
        val deviation: Double,
        val plates: List<Double>,
        val message: String
    ) : LoadableWeight()
}

// Rounding strategy
enum class RoundingStrategy {
    NEAREST,
    UP,
    DOWN
}

// Plate selection algorithms
fun greedyPlateSelection(target: Double, availablePlates: List<Double>): List<Double> {
    if (target <= 0.0) return emptyList()
    val sortedPlates = availablePlates.sortedDescending()
    val result = mutableListOf<Double>()
    var remaining = target
    for (plate in sortedPlates) {
        while (remaining >= plate - 0.001) {
            result.add(plate)
            remaining -= plate
        }
    }
    return result
}

fun calculateLoadableWeight(
    targetWeight: Double,
    barWeight: Double = 20.0,
    availablePlates: List<Double> = listOf(20.0, 10.0, 5.0, 2.5),
    strategy: RoundingStrategy = RoundingStrategy.NEAREST
): LoadableWeight {
    val targetPerSide = (targetWeight - barWeight) / 2.0
    val plates = greedyPlateSelection(targetPerSide, availablePlates)
    val actualPerSide = plates.sum()
    val actualTotal = barWeight + (actualPerSide * 2)
    
    return if (Math.abs(actualTotal - targetWeight) < 0.01) {
        LoadableWeight.Exact(actualTotal, plates)
    } else {
        LoadableWeight.Approximate(
            target = targetWeight,
            actual = actualTotal,
            deviation = actualTotal - targetWeight,
            plates = plates,
            message = "Peso não exato. O peso alvo ${targetWeight.toString().removeSuffix(".0")} kg não pode ser atingido com as anilhas disponíveis. O peso mais próximo é ${actualTotal.toString().removeSuffix(".0")} kg (${actualPerSide.toString().removeSuffix(".0")} kg por lado)."
        )
    }
}

fun getPlateColor(weight: Double): Color {
    return when (weight) {
        25.0 -> Color(0xFFD32F2F) // Vermelho
        20.0 -> Color(0xFF2196F3)  // Azul
        15.0 -> Color(0xFF7B1FA2) // Roxo
        10.0 -> Color(0xFF4CAF50)  // Verde
        5.0 -> Color(0xFFCDDC39)   // Amarelo/Limão
        2.5 -> Color(0xFF9E9E9E)   // Cinza
        1.25 -> Color(0xFF212121)  // Preto
        else -> Color(0xFF7E7E7E)  // Cinza escuro / padrão
    }
}

@Composable
fun WeightAdjustmentBottomSheet(
    targetWeight: Double,
    equipment: String,
    exerciseName: String = "",
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("gym_weights_library", android.content.Context.MODE_PRIVATE)
    }
    
    var availablePlatesState by remember {
        val saved = sharedPrefs.getString("available_plates_list", "20.0,10.0,5.0,2.5") ?: "20.0,10.0,5.0,2.5"
        mutableStateOf(saved.split(",").mapNotNull { it.toDoubleOrNull() }.sortedDescending())
    }
    
    fun saveAvailablePlates(newPlates: List<Double>) {
        availablePlatesState = newPlates.sortedDescending()
        sharedPrefs.edit()
            .putString("available_plates_list", newPlates.joinToString(","))
            .apply()
    }

    val effectiveEquipment = remember(equipment, exerciseName) {
        com.example.utils.WeightCalculator.inferEquipment(exerciseName, equipment)
    }
    val eq = (equipment + " " + effectiveEquipment + " " + exerciseName).lowercase()
    val isDumbbell = eq.contains("halter") || eq.contains("dumbbell")
    val isBarbell = !isDumbbell && (eq.contains("barra") || eq.contains("smith") || eq.contains("barbell") || eq.contains("articulad") || eq.contains("leg press") || eq.contains("hack"))
    val isMachine = !isDumbbell && !isBarbell && (eq.contains("máquina") || eq.contains("maquina") || eq.contains("polia") || eq.contains("cabo") || eq.contains("cable") || eq.contains("machine") || eq.contains("placa") || eq.contains("pilha") || eq.contains("crossover") || eq.contains("pulley") || eq.contains("puxada") || eq.contains("extensora") || eq.contains("flexora") || eq.contains("abdutora") || eq.contains("adutora") || eq.contains("voador") || eq.contains("pec deck"))
    val isBodyweight = eq.contains("peso corporal") || eq.contains("bodyweight")
    
    val barWeight = if (isBarbell) 20.0 else 0.0
    
    var currentPlates by remember { mutableStateOf(emptyList<Double>()) }
    var selectedDumbbellWeight by remember(targetWeight) {
        mutableStateOf(
            com.example.utils.WeightCalculator.roundToAvailableWeight(
                targetWeight.toFloat().coerceAtLeast(1f),
                "Halteres"
            ).toDouble()
        )
    }
    var showEditPlates by remember { mutableStateOf(false) }
    
    // Calculate initial plate assembly based on targetWeight
    LaunchedEffect(targetWeight, availablePlatesState, effectiveEquipment, isDumbbell) {
        if (isDumbbell) {
            selectedDumbbellWeight = com.example.utils.WeightCalculator.roundToAvailableWeight(
                targetWeight.toFloat().coerceAtLeast(1f),
                "Halteres"
            ).toDouble()
            currentPlates = listOf(selectedDumbbellWeight)
        } else {
            val safeTarget = if (isBarbell && targetWeight < barWeight && targetWeight > 0.0) {
                if (targetWeight <= 10.0) barWeight + (targetWeight * 2.0) else barWeight
            } else {
                targetWeight
            }
            val calculated = if (isBarbell) {
                val targetPerSide = (safeTarget - barWeight) / 2.0
                greedyPlateSelection(targetPerSide, availablePlatesState)
            } else {
                greedyPlateSelection(safeTarget, availablePlatesState)
            }
            currentPlates = calculated
        }
    }
    
    val currentTotal = if (isDumbbell) {
        selectedDumbbellWeight
    } else {
        barWeight + (if (isBarbell) currentPlates.sum() * 2.0 else currentPlates.sum())
    }
    val currentPerSide = if (isBarbell) currentPlates.sum() else currentTotal
    val isExact = if (isDumbbell) true else Math.abs(currentTotal - targetWeight) < 0.01
    
    val MAX_PER_SIDE = 8

    // Custom dialog/overlay mapped to BottomSheet visual style
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() }
                .testTag("weight_sheet_backdrop"),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .clickable(enabled = false) {}
                    .testTag("weight_sheet_card"),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 48.dp)
                ) {
                    // Bottom Sheet Handle / Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header: Total and Weight per side / dumbbell
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${if (currentTotal % 1.0 == 0.0) currentTotal.toInt().toString() else String.format(Locale.US, "%.1f", currentTotal)} kg",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isBarbell) {
                                    "${if (currentPerSide % 1.0 == 0.0) currentPerSide.toInt().toString() else String.format(Locale.US, "%.1f", currentPerSide)} kg por lado (+ ${barWeight.toInt()}kg barra)"
                                } else if (isDumbbell) {
                                    "Carga por halter (1 em cada mão)"
                                } else if (isMachine) {
                                    if (eq.contains("polia") || eq.contains("cabo")) "Carga na polia / cabo" else "Pilha de peso (Máquina)"
                                } else if (isBodyweight) {
                                    if (currentTotal <= 0) "Apenas peso do corpo" else "Carga adicional (Cinto / Anilha)"
                                } else {
                                    "Carga do exercício"
                                },
                                color = Primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Dynamic Visualizer Component based on Equipment Type
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBarbell) {
                                    BarbellVisualization(
                                        platesPerSide = currentPlates,
                                        barWeight = barWeight,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (isMachine) {
                                    MachineStackVisualization(
                                        plates = currentPlates,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (isBodyweight) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Peso Corporal + ${currentTotal.toInt()} kg",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    DumbbellVisualization(
                                        weight = currentTotal,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Initial Context & Bar Weight Info Table
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Peso sugerido", color = TextSecondary, fontSize = 13.sp)
                            Text("${if (targetWeight % 1.0 == 0.0) targetWeight.toInt().toString() else String.format(Locale.US, "%.1f", targetWeight)} kg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        if (isBarbell) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FitnessCenter, null, tint = Primary, modifier = Modifier.size(16.dp))
                                    Text("Barra olímpica", modifier = Modifier.padding(start = 6.dp), color = TextSecondary, fontSize = 13.sp)
                                }
                                Text("${barWeight.toInt()} kg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        
                        // Warning Alert Box for Non-Exact Loads
                        if (!isExact) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().testTag("approximate_weight_alert")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                                        Text(
                                            "Peso não exato",
                                            color = Color(0xFFFF9F0A),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "O peso alvo ${if (targetWeight % 1.0 == 0.0) targetWeight.toInt().toString() else String.format(Locale.US, "%.1f", targetWeight)} kg não pode ser atingido exatamente. O peso mais próximo possível é ${if (currentTotal % 1.0 == 0.0) currentTotal.toInt().toString() else String.format(Locale.US, "%.1f", currentTotal)} kg.",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isDumbbell) {
                            Text(
                                text = "PAR DE HALTERES (1 A 10 KG DE 1 EM 1 • ACIMA DE 10 KG EM PARES)",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val commercialDumbbells = remember {
                                (1..10).map { it.toDouble() } + (12..40 step 2).map { it.toDouble() }
                            }
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                commercialDumbbells.forEach { dbWeight ->
                                    val isSel = selectedDumbbellWeight.toInt() == dbWeight.toInt()
                                    Surface(
                                        onClick = {
                                            com.example.utils.AudioSynth.playTickSound()
                                            selectedDumbbellWeight = dbWeight
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) Primary else Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, if (isSel) Primary else Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Text(
                                            text = "${dbWeight.toInt()} kg",
                                            color = if (isSel) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Current plate composition ("Por lado" / "Carregado")
                            Text(
                                text = if (isBarbell) "POR LADO (TOQUE PARA REMOVER)" else "CARGA ATUAL (TOQUE PARA REMOVER)",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            if (currentPlates.isEmpty()) {
                                Text(
                                    "Nenhuma anilha carregada",
                                    color = TextTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    currentPlates.groupBy { it }.forEach { (weight, platesOfWeight) ->
                                        PlateChip(
                                            weight = weight,
                                            count = platesOfWeight.size,
                                            isSelected = true,
                                            onTap = {
                                                com.example.utils.AudioSynth.playTickSound()
                                                currentPlates = currentPlates.toMutableList().apply {
                                                    remove(weight)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Available Weights library header with "Editar" option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PESOS DISPONÍVEIS",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                TextButton(
                                    onClick = {
                                        showEditPlates = true
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Editar", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                availablePlatesState.forEach { plate ->
                                    val canAdd = currentPlates.size < MAX_PER_SIDE
                                    PlateChip(
                                        weight = plate,
                                        count = 1,
                                        isSelected = false,
                                        isEnabled = canAdd,
                                        onTap = {
                                            if (canAdd) {
                                                com.example.utils.AudioSynth.playTickSound()
                                                currentPlates = currentPlates + plate
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Bottom buttons: Cancel and Confirm - Pinned with extra safe clearance
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                com.example.utils.AudioSynth.playTickSound()
                                onDismiss()
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        
                        Button(
                            onClick = {
                                com.example.utils.AudioSynth.playTickSound()
                                onConfirm(currentTotal)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("weight_sheet_confirm"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirmar", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    
    if (showEditPlates) {
        EditPlatesDialog(
            currentAvailable = availablePlatesState,
            onSave = { updatedList ->
                saveAvailablePlates(updatedList)
                showEditPlates = false
            },
            onDismiss = {
                showEditPlates = false
            }
        )
    }
}

@Composable
fun BarbellVisualization(
    platesPerSide: List<Double>,
    barWeight: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Bar shaft (represented by a background continuous steel rod)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .background(Color(0xFF7E7E7E), shape = RoundedCornerShape(3.dp))
        )
        
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left plates sleeve (outermost left on the far left, loading inwards to the right stopper)
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                platesPerSide.reversed().forEach { plate ->
                    BarbellPlate(plate)
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
            
            // Left Collar / Stopper Ring (holds the plates on the sleeve)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(Color(0xFFB0B0B0), shape = RoundedCornerShape(1.dp))
            )
            
            // Bar Center Grip Sleeve (thick textured area where hand grips)
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(12.dp)
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(1.dp))
                    .background(Color(0xFF3A3A3A), shape = RoundedCornerShape(1.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${barWeight.toInt()}kg",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Right Collar / Stopper Ring
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(Color(0xFFB0B0B0), shape = RoundedCornerShape(1.dp))
            )
            
            // Right plates sleeve (loading outwards to the right, innermost right is directly next to the stopper)
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                platesPerSide.forEach { plate ->
                    Spacer(modifier = Modifier.width(2.dp))
                    BarbellPlate(plate)
                }
            }
        }
    }
}

@Composable
fun BarbellPlate(weight: Double) {
    val height = when (weight) {
        25.0 -> 52.dp
        20.0 -> 46.dp
        15.0 -> 40.dp
        10.0 -> 34.dp
        5.0 -> 28.dp
        2.5 -> 22.dp
        1.25 -> 16.dp
        else -> 12.dp
    }
    val width = when (weight) {
        25.0 -> 14.dp
        20.0 -> 12.dp
        15.0 -> 10.dp
        10.0 -> 8.dp
        5.0 -> 7.dp
        2.5 -> 6.dp
        1.25 -> 5.dp
        else -> 4.dp
    }
    val color = getPlateColor(weight)
    
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(color, shape = RoundedCornerShape(2.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.Black.copy(alpha = 0.2f))
        )
    }
}

@Composable
fun MachineStackVisualization(
    plates: List<Double>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (plates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(4.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Sem carga", color = TextTertiary, fontSize = 9.sp)
            }
        } else {
            // Stack up to 6 plates visually
            plates.take(6).reversed().forEachIndexed { index, weight ->
                val widthPercent = 0.45f + (index * 0.04f)
                val color = getPlateColor(weight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthPercent.coerceAtMost(0.85f))
                        .height(11.dp)
                        .background(color, shape = RoundedCornerShape(3.dp))
                        .border(1.dp, Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${weight.toString().removeSuffix(".0")} kg",
                        color = if (weight == 5.0 || weight == 2.5) Color.Black else Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Base plate anchor
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(4.dp)
                .background(Color.Gray, shape = RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun DumbbellVisualization(
    weight: Double,
    modifier: Modifier = Modifier
) {
    val intWeight = com.example.utils.WeightCalculator.roundToAvailableWeight(
        weight.toFloat().coerceAtLeast(1f),
        "Halteres"
    ).toInt()

    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = Primary

        // Renderiza o PAR DE HALTERES (2 halteres lado a lado)
        repeat(2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Cabeça esquerda do halter
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(44.dp)
                        .background(color, shape = RoundedCornerShape(6.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${intWeight}",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Barra de pegada recartilhada
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(8.dp)
                        .background(Color(0xFFCBD5E1))
                )

                // Cabeça direita do halter
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(44.dp)
                        .background(color, shape = RoundedCornerShape(6.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "kg",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PlateChip(
    weight: Double,
    count: Int,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onTap: () -> Unit
) {
    val color = getPlateColor(weight)
    
    val background = if (isSelected) color else color.copy(alpha = 0.15f)
    val contentColor = if (isSelected) {
        if (weight == 5.0 || weight == 2.5) Color.Black else Color.White
    } else {
        color
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier
            .alpha(if (isEnabled) 1f else 0.35f)
            .clickable(enabled = isEnabled, onClick = onTap)
            .testTag("plate_chip_${weight.toString().replace(".", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isSelected) (if (weight == 5.0 || weight == 2.5) Color.Black else Color.White) else color, CircleShape)
            )
            Text(
                text = if (isSelected) "$count x ${weight.toString().removeSuffix(".0")} kg" else "${weight.toString().removeSuffix(".0")} kg",
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    }
}

@Composable
fun EditPlatesDialog(
    currentAvailable: List<Double>,
    onSave: (List<Double>) -> Unit,
    onDismiss: () -> Unit
) {
    val standardPlates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 1.0)
    var selectedPlates by remember { mutableStateOf(currentAvailable.toSet()) }
    var customPlateText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anilhas Disponíveis", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Selecione as anilhas que existem na sua academia para o cálculo de montagem:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    standardPlates.forEach { plate ->
                        val isSelected = selectedPlates.contains(plate)
                        val color = getPlateColor(plate)
                        Card(
                            modifier = Modifier
                                .clickable {
                                    com.example.utils.AudioSynth.playTickSound()
                                    selectedPlates = if (isSelected) {
                                        selectedPlates - plate
                                    } else {
                                        selectedPlates + plate
                                    }
                                }
                                .testTag("toggle_plate_${plate.toString().replace(".", "_")}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) color else Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = "${plate.toString().removeSuffix(".0")} kg",
                                color = if (isSelected) (if (plate == 5.0 || plate == 2.5) Color.Black else Color.White) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = customPlateText,
                    onValueChange = { customPlateText = it },
                    label = { Text("Adicionar outro peso (kg)") },
                    placeholder = { Text("ex: 0.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_plate_input")
                )
                
                if (customPlateText.toDoubleOrNull() != null) {
                    val customVal = customPlateText.toDouble()
                    Button(
                        onClick = {
                            com.example.utils.AudioSynth.playTickSound()
                            selectedPlates = selectedPlates + customVal
                            customPlateText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("add_custom_plate_btn")
                    ) {
                        Text("Adicionar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    com.example.utils.AudioSynth.playTickSound()
                    onSave(selectedPlates.toList().sortedDescending())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.testTag("save_plates_btn")
            ) {
                Text("SALVAR", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    com.example.utils.AudioSynth.playTickSound()
                    onDismiss()
                },
                modifier = Modifier.testTag("cancel_plates_btn")
            ) {
                Text("CANCELAR", color = Color.White)
            }
        },
        containerColor = Surface
    )
}
