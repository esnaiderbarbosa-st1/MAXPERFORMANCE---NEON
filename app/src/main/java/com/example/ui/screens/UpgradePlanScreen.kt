package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.UserManager
import com.example.ui.adaptive.ResponsiveDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AccessControl
import kotlinx.coroutines.launch

@Composable
fun UpgradePlanScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsState()

    val currentLevel = user?.nivelAcesso ?: "BASICO"
    val levelColor = Color(AccessControl.getNivelColorHex(currentLevel))

    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Back Button & Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "MEU PLANO & NÍVEL DE ACESSO",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Gerencie seus benefícios e nível de acesso",
                    color = OnSurfaceDim,
                    fontSize = 11.sp
                )
            }
        }

        // Current Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, levelColor.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Nível de Acesso Atual",
                    color = OnSurfaceDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = AccessControl.getNivelDisplayName(currentLevel),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Surface(
                        color = levelColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, levelColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = currentLevel,
                            color = levelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Seu nível é verificado localmente para uso offline e sincronizado com os servidores em nuvem do Firebase.",
                    color = OnSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // Toggle Card: Ativar Modo Full
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = BorderStroke(1.dp, if (currentLevel == "FULL") Primary else Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ativar Modo Full",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (currentLevel == "FULL") "Modo Full ativado — todas as funcionalidades estão liberadas!" else "Ative para desbloquear todos os treinos, IA, bioimpedância OCR e relatórios.",
                            color = OnSurfaceDim,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = currentLevel == "FULL",
                        onCheckedChange = { isChecked ->
                            val targetLevel = if (isChecked) "FULL" else "BASICO"
                            coroutineScope.launch {
                                val res = UserManager.setUserLevel(context, targetLevel)
                                if (res.isSuccess) {
                                    successMessage = if (isChecked) "Modo Full Ativado com sucesso!" else "Nível alterado para Básico."
                                    errorMessage = null
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = OnSurfaceVariant,
                            uncheckedTrackColor = SurfaceDim
                        )
                    )
                }
            }
        }

        // Quick Level Selector
        Text(
            text = "Alternar Nível de Acesso:",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("BASICO", "INTERMEDIARIO", "FULL").forEach { lvl ->
                val isSelected = currentLevel == lvl
                val color = Color(AccessControl.getNivelColorHex(lvl))
                Surface(
                    onClick = {
                        if (!isSelected) {
                            coroutineScope.launch {
                                val res = UserManager.setUserLevel(context, lvl)
                                if (res.isSuccess) {
                                    successMessage = "Nível ${AccessControl.getNivelDisplayName(lvl)} ativado com sucesso!"
                                    errorMessage = null
                                } else {
                                    errorMessage = res.exceptionOrNull()?.message
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) color.copy(alpha = 0.2f) else Surface,
                    border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when(lvl) {
                                "BASICO" -> "Básico"
                                "INTERMEDIARIO" -> "Intermediário"
                                else -> "Modo Full"
                            },
                            color = if (isSelected) Color.White else OnSurfaceDim,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        if (isSelected) {
                            Text(
                                text = "ATIVO",
                                color = color,
                                fontWeight = FontWeight.Black,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Success / Error Feedback
        successMessage?.let {
            Text(
                text = it,
                color = Success,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(Success.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = Error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(Error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        // Tiers comparison
        Text(
            text = "Compare as Funcionalidades:",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Tier 1: BASICO
        TierBenefitCard(
            title = "Nível Básico",
            color = Color(AccessControl.getNivelColorHex("BASICO")),
            benefits = listOf(
                "Acesso a até 4 treinos pré-definidos",
                "Módulo de execução de treino em tempo real",
                "Histórico simplificado de treinos (últimos 7 dias)"
            ),
            lockedBenefits = listOf(
                "Módulo de Bioimpedância com OCR ou imagem",
                "Sugestão de treinos baseada em IA (Gemini)",
                "Histórico completo de 30 dias com gráficos",
                "Módulo de nutrição e exportação de relatórios"
            ),
            isActive = currentLevel == "BASICO",
            onSelect = {
                coroutineScope.launch {
                    val res = UserManager.setUserLevel(context, "BASICO")
                    if (res.isSuccess) {
                        successMessage = "Nível Básico ativado com sucesso!"
                        errorMessage = null
                    } else {
                        errorMessage = res.exceptionOrNull()?.message
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tier 2: INTERMEDIARIO
        TierBenefitCard(
            title = "Nível Intermediário",
            color = Color(AccessControl.getNivelColorHex("INTERMEDIARIO")),
            benefits = listOf(
                "Acesso a até 10 treinos variados",
                "Módulo de Bioimpedância com registro manual",
                "Módulo de Bioimpedância com leitura de relatório",
                "IA para sugestões de treinos baseado em desempenho",
                "Histórico completo de 30 dias com gráficos de progresso"
            ),
            lockedBenefits = listOf(
                "Protocolos avançados e técnicas de intensidade",
                "IA completa com análise preditiva",
                "Bioimpedância avançada com OCR de imagem",
                "Exportação de dados e relatórios em PDF/CSV",
                "Módulo de nutrição básica"
            ),
            isActive = currentLevel == "INTERMEDIARIO",
            onSelect = {
                coroutineScope.launch {
                    val res = UserManager.setUserLevel(context, "INTERMEDIARIO")
                    if (res.isSuccess) {
                        successMessage = "Nível Intermediário ativado com sucesso!"
                        errorMessage = null
                    } else {
                        errorMessage = res.exceptionOrNull()?.message
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tier 3: FULL
        TierBenefitCard(
            title = "Nível Full (Completo)",
            color = Color(AccessControl.getNivelColorHex("FULL")),
            benefits = listOf(
                "Acesso a TODOS os treinos, protocolos e intensidades",
                "Módulo de Bioimpedância completo com histórico",
                "Bioimpedância avançada com leitura via OCR",
                "IA completa com análise preditiva e recomendações",
                "Módulo de nutrição básica estruturada",
                "Dashboard completo com métricas detalhadas",
                "Exportação de dados e relatórios para arquivos externos"
            ),
            lockedBenefits = emptyList(),
            isActive = currentLevel == "FULL",
            onSelect = {
                coroutineScope.launch {
                    val res = UserManager.setUserLevel(context, "FULL")
                    if (res.isSuccess) {
                        successMessage = "Modo Full ativado com sucesso!"
                        errorMessage = null
                    } else {
                        errorMessage = res.exceptionOrNull()?.message
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun TierBenefitCard(
    title: String,
    color: Color,
    benefits: List<String>,
    lockedBenefits: List<String>,
    isActive: Boolean,
    onSelect: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, if (isActive) color else Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isActive) color else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isActive) {
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ATIVO",
                            color = color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (onSelect != null) {
                    OutlinedButton(
                        onClick = onSelect,
                        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("ATIVAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Included benefits
            benefits.forEach { b ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(b, color = OnSurfaceDim, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            // Locked benefits
            if (lockedBenefits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                lockedBenefits.forEach { lb ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Error.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(lb, color = OnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}
