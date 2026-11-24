package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProtocolExercise
import com.example.data.model.WorkoutProtocol
import com.example.data.repository.WorkoutProtocolsRepository
import com.example.ui.adaptive.ResponsiveDialog
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.Primary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceDim
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth

/**
 * Tela de Lista dos Protocolos Estruturados de Treino (Full Body A-D + PPL)
 */
@Composable
fun WorkoutProtocolsListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val protocols = remember { WorkoutProtocolsRepository.ALL_PROTOCOLS }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedProtocolForDetail by remember { mutableStateOf<WorkoutProtocol?>(null) }

    val filteredProtocols = remember(selectedCategoryFilter, protocols) {
        if (selectedCategoryFilter == null) {
            protocols
        } else {
            protocols.filter { it.category == selectedCategoryFilter }
        }
    }

    val activeProtocolId by viewModel.preferredProtocolId.collectAsState()

    if (selectedProtocolForDetail != null) {
        val proto = selectedProtocolForDetail!!
        val isProtoActive = activeProtocolId == proto.id || (proto.category == "FULL_BODY" && activeProtocolId == "FULL_BODY")
        WorkoutProtocolDetailModal(
            protocol = proto,
            isActive = isProtoActive,
            onDismiss = { selectedProtocolForDetail = null },
            onStartProtocol = { p ->
                selectedProtocolForDetail = null
                viewModel.saveAndStartProtocol(p)
            },
            onSetAsActive = { p, hybridWithRunning ->
                viewModel.setPreferredProtocol(p.id, hybridWithRunning)
                AudioSynth.playChimeSound()
                selectedProtocolForDetail = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Título da Seção
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Protocolos Estruturados",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Fichas validadas para ganho de massa e força",
                    color = OnSurfaceDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Chips de Filtro
        val mobilityCount = protocols.count { it.category == "MOBILIDADE" }
        val filterOptions = listOf(
            null to "Todos (${protocols.size})",
            "FULL_BODY" to "Full Body (${protocols.count { it.category == "FULL_BODY" }})",
            "PPL" to "PPL (${protocols.count { it.category == "PPL" }})",
            "MOBILIDADE" to "Mobilidade ($mobilityCount)"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions.size) { index ->
                val (cat, label) = filterOptions[index]
                val isSelected = selectedCategoryFilter == cat
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCategoryFilter = cat
                        AudioSynth.playTickSound()
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceDim,
                        labelColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) Primary else Color.White.copy(alpha = 0.1f))
                )
            }
        }

        // Lista de Cards de Protocolo
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(filteredProtocols, key = { it.id }) { protocol ->
                val isCardActive = activeProtocolId == protocol.id || (protocol.category == "FULL_BODY" && activeProtocolId == "FULL_BODY")
                ProtocolCard(
                    protocol = protocol,
                    isActive = isCardActive,
                    onClick = {
                        selectedProtocolForDetail = protocol
                        AudioSynth.playTickSound()
                    }
                )
            }
        }
    }
}

/**
 * Card individual de um Protocolo na lista
 */
@Composable
fun ProtocolCard(
    protocol: WorkoutProtocol,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val isPpl = protocol.category == "PPL"
    val isMobility = protocol.category == "MOBILIDADE" || protocol.id.contains("mobility")
    val icon = when {
        isMobility -> Icons.Default.AccessibilityNew
        isPpl -> Icons.Default.CalendarToday
        else -> Icons.Default.FitnessCenter
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("protocol_card_${protocol.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isMobility) Color(0xFF141F18) else Surface),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            when {
                isActive -> Primary
                isMobility -> Color(0xFF10B981).copy(alpha = 0.4f)
                else -> Primary.copy(alpha = 0.25f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header do Card: Tag + Ícone + Qtd Exercícios
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMobility) Color(0xFF10B981).copy(alpha = 0.15f) else Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isMobility) Color(0xFF34D399) else Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMobility) Color(0xFF10B981).copy(alpha = 0.2f) else Primary.copy(alpha = 0.2f))
                            .border(0.5.dp, if (isMobility) Color(0xFF10B981).copy(alpha = 0.5f) else Primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when {
                                isMobility -> "MOBILIDADE"
                                isPpl -> "PPL SUP/INF"
                                else -> "FULL BODY"
                            },
                            color = if (isMobility) Color(0xFF34D399) else Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (isActive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Primary.copy(alpha = 0.2f))
                                .border(0.5.dp, Primary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "PLANO ATIVO",
                                color = Primary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${protocol.exercises.size} EXERCÍCIOS",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Nome e Descrição
            Column {
                Text(
                    text = protocol.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = protocol.description,
                    color = OnSurfaceDim,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            // Rodapé do Card: Frequência + Seta Acessar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = protocol.frequency,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Ver detalhes",
                        color = Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modal/Diálogo Responsivo de Detalhes do Protocolo com lista de exercícios, dicas e botão INICIAR PROTOCOLO.
 */
@Composable
fun WorkoutProtocolDetailModal(
    protocol: WorkoutProtocol,
    isActive: Boolean = false,
    onDismiss: () -> Unit,
    onStartProtocol: (WorkoutProtocol) -> Unit,
    onSetAsActive: (WorkoutProtocol, Boolean) -> Unit = { _, _ -> }
) {
    var hybridWithRunning by remember { mutableStateOf(true) }

    ResponsiveDialog(
        visible = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Text(
                        text = protocol.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, "Fechar", tint = OnSurfaceDim)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Conteúdo Rolável
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                // Header Informativo do Protocolo
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Primary.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = protocol.category,
                                        color = Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Text(
                                    text = protocol.frequency,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = protocol.description,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Título da Lista de Exercícios
                item {
                    Text(
                        text = "EXERCÍCIOS DO PROTOCOLO (${protocol.exercises.size})",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // Cards dos Exercícios do Protocolo
                itemsIndexed(protocol.exercises) { index, exercise ->
                    ProtocolExerciseCard(
                        index = index + 1,
                        exercise = exercise
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switch de Integração Híbrida com Corrida (1 dia Musculação, 1 dia Corrida)
            if (protocol.category == "FULL_BODY" || protocol.id.startsWith("protocol_fb")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { hybridWithRunning = !hybridWithRunning }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Intercalar com Corrida no Cronograma",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1 dia Full Body, 1 dia Módulo de Corrida alternado",
                                color = OnSurfaceDim,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Switch(
                        checked = hybridWithRunning,
                        onCheckedChange = { hybridWithRunning = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00B0FF),
                            checkedTrackColor = Color(0xFF00B0FF).copy(alpha = 0.35f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Botão Secundário: DEFINIR COMO PLANO ATIVO (FIXAR NO HERO CARD)
            OutlinedButton(
                onClick = { onSetAsActive(protocol, hybridWithRunning) },
                border = BorderStroke(1.5.dp, if (isActive) Primary else Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isActive) Primary.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isActive) Primary else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_fixar_protocolo_${protocol.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        tint = if (isActive) Primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "PLANO JÁ FIXADO NO HERO CARD" else "DEFINIR COMO MEU PLANO ATIVO (HERO CARD)",
                        color = if (isActive) Primary else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botão Primário: "INICIAR PROTOCOLO AGORA"
            Button(
                onClick = { onStartProtocol(protocol) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF06101E)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                        )
                    )
                    .testTag("btn_iniciar_protocolo_${protocol.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF06101E),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INICIAR PROTOCOLO AGORA",
                        color = Color(0xFF06101E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * Card individual com detalhes do exercício, séries, repetições e dicas visíveis.
 */
@Composable
fun ProtocolExerciseCard(
    index: Int,
    exercise: ProtocolExercise
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Linha Superior: Número + Nome + Grupo Muscular
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$index",
                            color = Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val dynamicFontSize = if (exercise.name.length > 26) 13.sp else 15.sp
                        val dynamicLineHeight = if (exercise.name.length > 26) 16.sp else 18.sp
                        Text(
                            text = exercise.name,
                            color = Color.White,
                            fontSize = dynamicFontSize,
                            lineHeight = dynamicLineHeight,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!exercise.alternatives.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = exercise.alternatives!!,
                                color = OnSurfaceDim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = exercise.muscleGroup,
                        color = Primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tag de Séries e Repetições
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${exercise.sets} SÉRIES  •  ${exercise.reps} REPETIÇÕES",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Dica de Execução (Destacada e perfeitamente legível)
            Surface(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, Primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Dica",
                        tint = Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = exercise.tip,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
