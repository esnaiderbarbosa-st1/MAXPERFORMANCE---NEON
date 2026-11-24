package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.services.UserManager
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText

class CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 11) text.text.substring(0, 11) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (i == 2 || i == 5) out += "."
            if (i == 8) out += "-"
        }

        val numberOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 5) return offset + 1
                if (offset <= 8) return offset + 2
                return offset + 3
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                if (offset <= 6) return offset - 1
                if (offset <= 10) return offset - 2
                return offset - 3
            }
        }

        return TransformedText(AnnotatedString(out), numberOffsetTranslator)
    }
}

enum class AuthMode {
    LOGIN, REGISTER
}

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Check for lockout
    var lockoutRemaining by remember { mutableStateOf(0) }
    var isTestingMode by remember { mutableStateOf(UserManager.isTestingMode(context)) }
    LaunchedEffect(authMode) {
        lockoutRemaining = UserManager.getRemainingLockoutTimeMinutes(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.BackgroundStart)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Logo
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 8.dp),
                tint = Primary
            )

            Text(
                text = "MAX PERFORMANCE",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "High-Performance Access Control",
                color = OnSurfaceDim,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Form container
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (authMode == AuthMode.LOGIN) "Acesse sua Conta" else "Crie sua Conta",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Error & Success Feedback
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .padding(bottom = 16.dp)
                        )
                    }

                    successMessage?.let {
                        Text(
                            text = it,
                            color = Success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Success.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .padding(bottom = 16.dp)
                        )
                    }

                    if (authMode == AuthMode.REGISTER) {
                        // Full Name
                        OutlinedTextField(
                            value = nome,
                            onValueChange = { nome = it },
                            label = { Text("Nome Completo") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("register_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // CPF Field
                        val isCpfValid = nome.isEmpty() || UserManager.validateCpf(cpf)
                        OutlinedTextField(
                            value = cpf,
                            onValueChange = { input ->
                                val clean = input.replace(Regex("[^0-9]"), "")
                                if (clean.length <= 11) {
                                    cpf = clean
                                }
                            },
                            visualTransformation = CpfVisualTransformation(),
                            label = { Text("CPF") },
                            isError = !isCpfValid,
                            supportingText = {
                                if (!isCpfValid) {
                                    Text("CPF Inválido (requer algoritmo de verificação)", color = Error)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("register_cpf"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        placeholder = { Text("seu@email.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_email"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha") },
                        placeholder = { Text("••••••••") },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = OnSurfaceDim
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("auth_password"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Password Strength Criteria Indicator (for register)
                    if (authMode == AuthMode.REGISTER) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            val passStrengthError = UserManager.validatePasswordStrength(password)
                            Text(
                                text = "Requisitos de senha forte:",
                                fontSize = 10.sp,
                                color = OnSurfaceDim,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val requirements = listOf(
                                "Mínimo 8 caracteres" to (password.length >= 8),
                                "Letra maiúscula" to password.any { it.isUpperCase() },
                                "Letra minúscula" to password.any { it.isLowerCase() },
                                "Número" to password.any { it.isDigit() },
                                "Caractere especial" to password.any { "!@#$%^&*()_+-=[]{}|;':\",./<>?\\".contains(it) }
                            )
                            requirements.forEach { (text, met) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                ) {
                                    Icon(
                                        imageVector = if (met) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (met) Success else Error,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text, fontSize = 9.sp, color = if (met) Success else OnSurfaceVariant)
                                }
                            }
                        }

                        // Confirm Password
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirmar Senha") },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = OnSurfaceDim
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                                .testTag("register_confirm_password"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modo de Teste (Auto-Aprovação)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Novos cadastros serão criados como ATIVOS para facilidade de testes.",
                                color = OnSurfaceDim,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isTestingMode,
                            onCheckedChange = {
                                isTestingMode = it
                                UserManager.setTestingMode(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Primary,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button
                    Button(
                        onClick = {
                            errorMessage = null
                            successMessage = null
                            lockoutRemaining = UserManager.getRemainingLockoutTimeMinutes(context)
                            if (lockoutRemaining > 0) {
                                errorMessage = "Conta bloqueada. Tente novamente em $lockoutRemaining minuto(s)."
                                return@Button
                            }

                            if (authMode == AuthMode.LOGIN) {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Preencha todos os campos."
                                    return@Button
                                }
                                isLoading = true
                                coroutineScope.launch {
                                    val result = UserManager.login(context, email, password)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        onAuthSuccess()
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message
                                        lockoutRemaining = UserManager.getRemainingLockoutTimeMinutes(context)
                                    }
                                }
                            } else {
                                if (password != confirmPassword) {
                                    errorMessage = "As senhas não coincidem."
                                    return@Button
                                }
                                isLoading = true
                                coroutineScope.launch {
                                    val formattedCpf = if (cpf.length == 11) {
                                        "${cpf.substring(0, 3)}.${cpf.substring(3, 6)}.${cpf.substring(6, 9)}-${cpf.substring(9, 11)}"
                                    } else {
                                        cpf
                                    }
                                    val result = UserManager.signUp(context, nome, email, formattedCpf, password)
                                    isLoading = false
                                    if (result.isSuccess) {
                                        successMessage = "Cadastro solicitado! Aguardando aprovação do superusuário."
                                        authMode = AuthMode.LOGIN
                                        password = ""
                                        confirmPassword = ""
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button"),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (authMode == AuthMode.LOGIN) "ENTRAR" else "CADASTRAR",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    if (com.example.BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                isLoading = true
                                coroutineScope.launch {
                                    val result = UserManager.login(context, "maria.oliveira@email.com", "senha_generica_sucesso")
                                    isLoading = false
                                    if (result.isSuccess) {
                                        onAuthSuccess()
                                    } else {
                                        val sharedPrefs = context.getSharedPreferences("max_user_prefs", android.content.Context.MODE_PRIVATE)
                                        sharedPrefs.edit().putString("logged_in_user_id", "user_demo_2").apply()
                                        UserManager.init(context)
                                        onAuthSuccess()
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auth_bypass_button"),
                            enabled = !isLoading
                        ) {
                            Text(
                                text = "ENTRAR SEM LOGIN",
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Secondary toggle link
            TextButton(
                onClick = {
                    authMode = if (authMode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                    errorMessage = null
                    successMessage = null
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = if (authMode == AuthMode.LOGIN) "Não tem conta? Cadastre-se aqui" else "Já possui uma conta? Faça login",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Full screen displaying Status when the account is Pending, Rejected, or Blocked
@Composable
fun AccountStatusScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsState()

    val status = user?.status ?: "PENDENTE"
    val motivo = if (status == "BLOQUEADO") user?.motivoBloqueio else user?.motivoRejeicao

    val title = when (status) {
        "PENDENTE" -> "Cadastro em Análise"
        "BLOQUEADO" -> "Acesso Suspenso"
        "REJEITADO" -> "Solicitação Rejeitada"
        else -> "Verificando Credenciais"
    }

    val desc = when (status) {
        "PENDENTE" -> "Sua solicitação de acesso foi enviada com sucesso e está sendo analisada pelo superusuário. Aguarde aprovação de nível."
        "BLOQUEADO" -> "Esta conta foi suspensa temporariamente do aplicativo pelo administrador."
        "REJEITADO" -> "Infelizmente, sua solicitação de cadastro inicial foi rejeitada pelo administrador do sistema."
        else -> ""
    }

    val statusColor = when (status) {
        "PENDENTE" -> Color(0xFFFFB74D) // Yellow/Orange
        "BLOQUEADO", "REJEITADO" -> Error
        else -> OnSurfaceDim
    }

    val icon = when (status) {
        "PENDENTE" -> Icons.Default.HourglassEmpty
        "BLOQUEADO" -> Icons.Default.Block
        "REJEITADO" -> Icons.Default.Cancel
        else -> Icons.Default.HourglassEmpty
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundStart)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp),
                tint = statusColor
            )

            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = desc,
                color = OnSurfaceDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            motivo?.let {
                if (it.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Motivo Informado:",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Check Status Sync Button
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            UserManager.refreshSession(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Atualizar Status", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Sign out & use another account
                Button(
                    onClick = { UserManager.logout(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Outra Conta", color = OnSurfaceDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
