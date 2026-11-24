package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.WeeklyGoalSelector
import com.example.utils.AudioSynth

/**
 * Tela de primeiro cadastro real exibida quando o banco Room ainda não possui perfil salvo.
 * Mantém um espaço/slot reservado ao final (`footerContent`) para futuras ações auxiliares
 * (restauração de backup e atalhos DEBUG).
 */
@Composable
fun ProfileSetupScreen(
    onCompleteSetup: (
        name: String,
        gender: String,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        weeklyGoal: Int
    ) -> Unit,
    modifier: Modifier = Modifier,
    footerContent: @Composable ColumnScope.() -> Unit = {}
) {
    var nameInput by rememberSaveable { mutableStateOf("") }
    var ageInput by rememberSaveable { mutableStateOf("") }
    var selectedGender by rememberSaveable { mutableStateOf("") }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var weeklyGoal by rememberSaveable { mutableIntStateOf(4) }
    var isSaving by remember { mutableStateOf(false) }

    val trimmedName = nameInput.trim()
    val parsedAge = ageInput.trim().toIntOrNull()
    val parsedWeight = weightInput.trim().replace(",", ".").toFloatOrNull()
    val parsedHeight = heightInput.trim().toIntOrNull()

    val isNameValid = trimmedName.isNotEmpty()
    val isAgeValid = parsedAge != null && parsedAge in 12..100
    val isGenderValid = selectedGender.isNotBlank()
    val isWeightValid = parsedWeight != null && parsedWeight in 25f..300f
    val isHeightValid = parsedHeight != null && parsedHeight in 100..250
    val isGoalValid = weeklyGoal in 1..7

    val isFormValid = isNameValid && isAgeValid && isGenderValid && isWeightValid && isHeightValid && isGoalValid

    val ageHasError = ageInput.isNotEmpty() && !isAgeValid
    val weightHasError = weightInput.isNotEmpty() && !isWeightValid
    val heightHasError = heightInput.isNotEmpty() && !isHeightValid

    val colorScheme = MaterialTheme.colorScheme
    val primaryGlow = colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.08f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("profile_setup_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cabeçalho de boas-vindas
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "MAX Performance",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Text(
                    text = "MAX PERFORMANCE",
                    color = colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )

                Text(
                    text = "Configure seu perfil",
                    color = colorScheme.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )

                Text(
                    text = "Informe seus dados básicos para calibrar seus treinos, métricas fisiológicas e metas semanais.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            // Cartão do formulário essencial
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedLabelColor = colorScheme.primary,
                        unfocusedLabelColor = colorScheme.onSurfaceVariant,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        cursorColor = colorScheme.primary
                    )

                    // Nome (obrigatório)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nome") },
                        placeholder = { Text("Como devemos te chamar?", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words
                        ),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_name_input")
                    )

                    // Sexo / Gênero (compatível com UserProfileData.sexo)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sexo / Gênero",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val genderOptions = listOf("Masculino", "Feminino", "Outro")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genderOptions.forEach { option ->
                                val isSelected = selectedGender == option
                                Surface(
                                    selected = isSelected,
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        selectedGender = option
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) colorScheme.primary else colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .semantics {
                                            role = Role.RadioButton
                                            contentDescription = "Sexo $option"
                                        }
                                        .testTag("setup_gender_${option.lowercase()}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = option,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Idade (anos)
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { input ->
                            ageInput = input.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("Idade (anos)") },
                        placeholder = { Text("Ex: 30", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        isError = ageHasError,
                        supportingText = if (ageHasError) {
                            { Text("Informe uma idade entre 12 e 100 anos.") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_age_input")
                    )

                    // Peso (kg) e Altura (cm)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }
                                if (filtered.count { it == '.' || it == ',' } <= 1) {
                                    weightInput = filtered.take(6)
                                }
                            },
                            label = { Text("Peso (kg)") },
                            placeholder = { Text("Ex: 75.0", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            isError = weightHasError,
                            supportingText = if (weightHasError) {
                                { Text("25 a 300 kg") }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_weight_input")
                        )

                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { input ->
                                heightInput = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Altura (cm)") },
                            placeholder = { Text("Ex: 175", color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            isError = heightHasError,
                            supportingText = if (heightHasError) {
                                { Text("100 a 250 cm") }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_height_input")
                        )
                    }

                    // Meta semanal reutilizando WeeklyGoalSelector (1 a 7 dias)
                    WeeklyGoalSelector(
                        selectedDays = weeklyGoal,
                        onSelectDays = { weeklyGoal = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Botão primário "Começar"
            Button(
                onClick = {
                    if (isFormValid && !isSaving && parsedAge != null && parsedHeight != null && parsedWeight != null) {
                        isSaving = true
                        onCompleteSetup(
                            trimmedName,
                            selectedGender,
                            parsedAge,
                            parsedHeight,
                            parsedWeight,
                            weeklyGoal
                        )
                    }
                },
                enabled = isFormValid && !isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    disabledContainerColor = colorScheme.surfaceVariant,
                    disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("setup_start_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = "Começar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Área reservada para ações auxiliares futuras (ex.: restauração de backup e atalhos DEBUG)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_setup_footer_slot"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = footerContent
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
