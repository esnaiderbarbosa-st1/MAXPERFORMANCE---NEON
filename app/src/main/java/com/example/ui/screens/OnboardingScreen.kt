package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlowScreen(viewModel: MainViewModel) {
    val currentUserState by viewModel.currentUser.collectAsState()
    val registeredName = currentUserState?.nome ?: ""
    
    if (com.example.utils.DeveloperMode.isEnabled) {
        var devName by remember { mutableStateOf("") }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F14))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Logo/Monograma no topo centralizado (usando o "M" estilizado do app)
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(Color(0xFF161922), shape = RoundedCornerShape(24.dp))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF00B0FF).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "MAX PERFORMANCE Logo",
                        modifier = Modifier.size(72.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Nome do app
                Text(
                    text = "MAX PERFORMANCE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Tagline (2 linhas curtas, cinza claro #94A3B8, 14sp)
                Text(
                    text = "Treine com inteligência.\nEvolua com dados reais.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                // 4. Campo de input ("Seu nome" com borda azul oficial sutil, fundo escuro)
                OutlinedTextField(
                    value = devName,
                    onValueChange = { devName = it },
                    label = { Text("Seu nome") },
                    placeholder = { Text("Digite seu nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF161922),
                        unfocusedContainerColor = Color(0xFF161922),
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color(0xFF00B0FF).copy(alpha = 0.35f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00B0FF),
                        unfocusedLabelColor = Color(0xFF94A3B8),
                        cursorColor = Color(0xFF00B0FF)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Botão "ENTRAR NO APP" azul oficial (#00B0FF), largura total, 56dp de altura
                Button(
                    onClick = {
                        viewModel.saveDeveloperOnboardingProfile(devName.trim())
                    },
                    enabled = devName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B0FF),
                        disabledContainerColor = Color(0xFF00B0FF).copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "ENTRAR NO APP",
                        color = if (devName.isNotBlank()) Color(0xFF06101E) else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    } else {
        var step by remember { mutableStateOf(1) }
        
        // Form States
        var ageStr by remember { mutableStateOf("") }
        var weightStr by remember { mutableStateOf("") }
        var heightStr by remember { mutableStateOf("") }
        var selectedLevel by remember { mutableStateOf("") }
        var selectedGoal by remember { mutableStateOf("") }

        // Custom background using the beautiful Graphite Theme gradient
        Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackgroundStart, BackgroundEnd)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        when (step) {
            1 -> {
                // Step 1: Welcome
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "MAX PERFORMANCE LOGO",
                            tint = Primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = "BEM-VINDO AO\nMAX PERFORMANCE!",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Vamos te conhecer melhor para personalizar seus treinos e impulsionar seus resultados na academia.",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { step = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "COMEÇAR",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
            2 -> {
                // Step 2: Profile Registration Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "CADASTRO DE PERFIL",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                    
                    Text(
                        text = "Fale mais sobre você",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )
                    
                    Text(
                        text = "Esses dados são obrigatórios para calcularmos seu IMC e ajustarmos sua planilha de treinos.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it.filter { char -> char.isDigit() } },
                            label = { Text("Idade") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = { heightStr = it.filter { char -> char.isDigit() } },
                            label = { Text("Altura (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Primary,
                                unfocusedLabelColor = TextSecondary
                            )
                        )
                    }

                    OutlinedTextField(
                        value = weightStr,
                        onValueChange = { weightStr = it },
                        label = { Text("Peso Atual (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    // Experience Level
                    Text(
                        text = "Nível de Experiência:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val levels = listOf("Iniciante", "Intermediário", "Avançado")
                        levels.forEach { level ->
                            val isSelected = selectedLevel == level
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLevel = level },
                                label = { Text(level, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    // Main Goal
                    Text(
                        text = "Objetivo Principal:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        val goals = listOf("Hipertrofia", "Força", "Definição", "Resistência")
                        goals.forEach { goal ->
                            val isSelected = selectedGoal == goal
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedGoal = goal },
                                label = { Text(goal, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val isFormValid = registeredName.isNotBlank() && 
                            ageStr.isNotBlank() && 
                            weightStr.toFloatOrNull() != null && 
                            heightStr.toIntOrNull() != null && 
                            selectedLevel.isNotBlank() && 
                            selectedGoal.isNotBlank()

                    Button(
                        onClick = {
                            if (isFormValid) {
                                viewModel.saveOnboardingProfile(
                                    name = registeredName.trim(),
                                    age = ageStr.toInt(),
                                    weight = weightStr.toFloat(),
                                    height = heightStr.toInt(),
                                    experience = selectedLevel,
                                    goal = selectedGoal
                                )
                                step = 3
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "SALVAR E CONTINUAR",
                            color = if (isFormValid) Color.Black else TextDisabled,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            3 -> {
                // Step 3: Suggestion of First Workout
                val parsedWeight = weightStr.toFloatOrNull() ?: 70f
                val parsedHeight = heightStr.toIntOrNull() ?: 175
                val heightM = parsedHeight / 100f
                val calculatedImc = if (heightM > 0) parsedWeight / (heightM * heightM) else 0f
                val imcText = String.format(Locale.getDefault(), "%.1f", calculatedImc)

                val suggestion = when (selectedLevel) {
                    "Iniciante" -> "Full Body - 3x na semana"
                    "Intermediário" -> "Upper/Lower - 4x na semana"
                    "Avançado" -> "ABCDE - 5x na semana"
                    else -> "Full Body - 3x na semana"
                }

                val explanation = when (selectedLevel) {
                    "Iniciante" -> "Excelente escolha para quem está começando. Focar no corpo todo 3 vezes por semana permite excelente recuperação e rápida adaptação neural aos exercícios básicos."
                    "Intermediário" -> "Divisão ideal para equilibrar intensidade e volume. Treinar membros superiores e inferiores alternadamente garante alta frequência de estímulos musculares."
                    "Avançado" -> "Planilha avançada focada em alto volume por grupamento muscular. Cada dia é dedicado a uma região específica do corpo para máxima hipertrofia e quebra de platôs."
                    else -> ""
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Success.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success Icon",
                            tint = Success,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Text(
                        text = "Seu primeiro treino está pronto",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Geramos um treino baseado nas informações fornecidas.",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // Profile Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "RESUMO DO SEU PERFIL",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Atleta", color = TextSecondary, fontSize = 13.sp)
                                Text(registeredName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("IMC", color = TextSecondary, fontSize = 13.sp)
                                Text("$imcText (Classificado)", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Nível", color = TextSecondary, fontSize = 13.sp)
                                Text(selectedLevel, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Objetivo", color = TextSecondary, fontSize = 13.sp)
                                Text(selectedGoal, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Treinos da Semana Section
                    val templates = com.example.constants.TemplatesAndExercises.PRE_MADE_TEMPLATES
                    val weeklyWorkouts = remember {
                        listOfNotNull(
                            templates.find { it.id == "template_push" },
                            templates.find { it.id == "template_pull" },
                            templates.find { it.id == "template_legs" },
                            templates.find { it.id == "template_upper" },
                            templates.find { it.id == "template_lower" }
                        )
                    }

                    val suggestedWorkoutId = when (selectedLevel) {
                        "Iniciante" -> "template_fullbody"
                        "Intermediário" -> "template_push"
                        "Avançado" -> "template_legs"
                        else -> "template_fullbody"
                    }

                    // Add template_fullbody to weeklyWorkouts if beginner
                    val finalWeeklyWorkouts = remember(selectedLevel) {
                        if (selectedLevel == "Iniciante") {
                            val fb = templates.find { it.id == "template_fullbody" }
                            if (fb != null) {
                                listOf(fb) + weeklyWorkouts.filter { it.id != "template_push" }.take(4)
                            } else {
                                weeklyWorkouts
                            }
                        } else {
                            weeklyWorkouts
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SUGESTÃO: TREINOS DA SEMANA",
                            color = Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Text(
                            text = "Toque em qualquer treino para começar. O sistema sugere o treino lógico, mas você tem total liberdade para escolher por onde iniciar.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        finalWeeklyWorkouts.forEachIndexed { index, w ->
                            val letter = when (index) {
                                0 -> "A"
                                1 -> "B"
                                2 -> "C"
                                3 -> "D"
                                4 -> "E"
                                else -> ('A' + index).toString()
                            }
                            
                            val isSuggested = w.id == suggestedWorkoutId || (selectedLevel == "Iniciante" && w.id == "template_fullbody")
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setFirstLaunch(false)
                                        viewModel.startWorkoutSession(w, forceStart = true)
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSuggested) Primary.copy(alpha = 0.08f) else GraphiteSurface
                                ),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isSuggested) Primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Letter Badge
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isSuggested) Primary else Color.White.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                color = if (isSuggested) Color.Black else Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = w.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = w.description,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (isSuggested) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "SUGERIDO",
                                                color = Primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "Começar",
                                            tint = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.setFirstLaunch(false)
                            },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(
                                text = "PULAR E IR PARA HOME",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
}
