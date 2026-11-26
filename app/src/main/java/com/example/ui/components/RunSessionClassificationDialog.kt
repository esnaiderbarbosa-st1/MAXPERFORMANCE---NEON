package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.RunningSession
import com.example.data.model.customRaceName
import com.example.data.model.isClassifiedAsRace
import com.example.engine.RunningProgramManager
import com.example.utils.AudioSynth

private val ObsidianBg = Color(0xFF070B14)
private val ObsidianSurface = Color(0xFF0F1626)
private val ObsidianBorder = Color(0xFF1E293B)
private val NeonCyan = Color(0xFF00D4FF)
private val RaceAmber = Color(0xFFF59E0B)
private val RaceAmberText = Color(0xFFFBBF24)
private val SecondaryText = Color(0xFF94A3B8)

/**
 * Modal "Classificar Sessão" (padrão MAX V3 Neon, fundo obsidian, alto contraste, zero emojis).
 * Permite alternar qualquer corrida já salva entre Treino e Prova, com nome opcional da prova.
 */
@Composable
fun RunSessionClassificationDialog(
    session: RunningSession,
    isLinkedOfficialRace: Boolean = false,
    linkedOfficialRaceName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (isRace: Boolean, raceName: String?) -> Unit
) {
    var selectedIsRace by remember(session.id, isLinkedOfficialRace) {
        mutableStateOf(session.isClassifiedAsRace(isLinkedOfficialRace))
    }
    var raceNameInput by remember(session.id, isLinkedOfficialRace, linkedOfficialRaceName) {
        mutableStateOf(
            session.customRaceName(isLinkedOfficialRace)
                ?: if (session.isClassifiedAsRace(isLinkedOfficialRace)) linkedOfficialRaceName.orEmpty() else ""
        )
    }
    val baseOriginalTitle = remember(session.id) {
        RunningProgramManager.getDisplayTitleForSession(
            session.copy(
                advancedMetrics = (session.advancedMetrics ?: com.example.data.model.AdvancedRunningMetrics()).copy(
                    isRaceOverride = false,
                    raceName = null
                )
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ProvideRunFontScale {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxModalHeight = (maxHeight * 0.88f).coerceIn(320.dp, 560.dp)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = ObsidianBg,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedIsRace) RaceAmber.copy(alpha = 0.55f) else ObsidianBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .heightIn(max = maxModalHeight)
                        .testTag("dialog_classify_running_session")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Topo fixo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Classificar Sessão",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = baseOriginalTitle,
                                    color = SecondaryText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_close_classify_session_dialog")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = SecondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

                        // Miolo rolável
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "TIPO DE SESSÃO",
                                color = SecondaryText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )

                            // Seletor de duas opções: Treino | Prova
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        selectedIsRace = false
                                        AudioSynth.playTickSound()
                                    },
                                    color = if (!selectedIsRace) NeonCyan.copy(alpha = 0.18f) else ObsidianSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        width = if (!selectedIsRace) 1.5.dp else 1.dp,
                                        color = if (!selectedIsRace) NeonCyan else ObsidianBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("option_classify_training")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                            contentDescription = null,
                                            tint = if (!selectedIsRace) NeonCyan else SecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Treino",
                                            color = if (!selectedIsRace) Color.White else SecondaryText,
                                            fontSize = 14.sp,
                                            fontWeight = if (!selectedIsRace) FontWeight.Black else FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        selectedIsRace = true
                                        AudioSynth.playTickSound()
                                    },
                                    color = if (selectedIsRace) RaceAmber.copy(alpha = 0.20f) else ObsidianSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        width = if (selectedIsRace) 1.5.dp else 1.dp,
                                        color = if (selectedIsRace) RaceAmber else ObsidianBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("option_classify_race")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = if (selectedIsRace) RaceAmberText else SecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Prova",
                                            color = if (selectedIsRace) RaceAmberText else SecondaryText,
                                            fontSize = 14.sp,
                                            fontWeight = if (selectedIsRace) FontWeight.Black else FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            if (selectedIsRace) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = raceNameInput,
                                        onValueChange = { if (it.length <= 60) raceNameInput = it },
                                        label = {
                                            Text(
                                                text = "Nome da Prova (opcional)",
                                                fontSize = 12.5.sp
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Ex.: Circuito 5K",
                                                color = SecondaryText.copy(alpha = 0.65f)
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ObsidianSurface,
                                            unfocusedContainerColor = ObsidianSurface,
                                            focusedBorderColor = RaceAmber,
                                            unfocusedBorderColor = ObsidianBorder,
                                            focusedLabelColor = RaceAmberText,
                                            unfocusedLabelColor = SecondaryText,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = RaceAmberText
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_race_name_optional")
                                    )

                                    Text(
                                        text = "Se ficar em branco, o título original da sessão será mantido com o selo PROVA.",
                                        color = SecondaryText,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Surface(
                                    color = ObsidianSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "A sessão será exibida normalmente como treino no histórico, relatórios e exportações.",
                                        color = SecondaryText,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

                        // Rodapé fixo fora do scroll: Cancelar | Salvar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_cancel_session_classification")
                            ) {
                                Text(
                                    text = "Cancelar",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    val cleanName = if (selectedIsRace) {
                                        raceNameInput.trim().takeIf { it.isNotBlank() }
                                    } else {
                                        null
                                    }
                                    onConfirm(selectedIsRace, cleanName)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedIsRace) RaceAmber else NeonCyan,
                                    contentColor = Color(0xFF040812)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_save_session_classification")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Salvar",
                                        fontSize = 14.sp,
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
        }
    }
}
