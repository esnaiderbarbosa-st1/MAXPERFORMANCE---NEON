package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RunningShoe
import com.example.engine.PaceCalibrationAnalysis
import com.example.engine.RunningProgramManager
import com.example.ui.theme.RunColors
import com.example.utils.AudioSynth
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningShoesBottomSheet(
    shoes: List<RunningShoe>,
    onAddShoe: (RunningShoe) -> Unit,
    onUpdateShoe: ((RunningShoe) -> Unit)? = null,
    onSetDefault: (String) -> Unit,
    onRetireShoe: (String) -> Unit,
    onDeleteShoe: (String) -> Unit,
    onDismiss: () -> Unit,
    selectionTargetSession: com.example.data.model.RunningSession? = null,
    onSelectShoeForSession: ((String) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShoe by remember { mutableStateOf<RunningShoe?>(null) }
    var newShoeName by remember { mutableStateOf("") }
    var newShoeBrand by remember { mutableStateOf("") }
    var newShoeInitialKm by remember { mutableStateOf("0") }
    var newShoeMaxKm by remember { mutableStateOf("600") }
    var isNewDefault by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RunColors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectionTargetSession != null) "Minha Garagem • Selecionar Tênis" else "Revezamento de Tênis",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (selectionTargetSession != null) "Toque em um tênis para vinculá-lo a esta corrida ou gerencie sua frota." else "Alterne entre pares para evitar lesões e acompanhar a vida útil.",
                        color = RunColors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ NOVO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = RunColors.textSecondary
                        )
                    }
                }
            }

            if (selectionTargetSession != null && onSelectShoeForSession != null) {
                Surface(
                    color = RunColors.brand.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RunColors.brand.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = RunColors.brand,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Toque em um tênis abaixo para vinculá-lo a esta corrida.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (shoes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A22)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhum tênis cadastrado",
                                    color = RunColors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    items(shoes) { shoe ->
                        val progress = (shoe.totalDistanceKm / shoe.maxDistanceKm).coerceIn(0f, 1f)
                        val isNearLimit = shoe.totalDistanceKm >= shoe.maxDistanceKm * 0.9f
                        val isExceeded = shoe.totalDistanceKm >= shoe.maxDistanceKm
                        val isCurrentShoeForSession = selectionTargetSession != null &&
                            selectionTargetSession.shoeName.equals(shoe.fullDisplayName(), ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (selectionTargetSession != null && onSelectShoeForSession != null) {
                                        Modifier.clickable {
                                            AudioSynth.playTickSound()
                                            onSelectShoeForSession(shoe.fullDisplayName())
                                            onDismiss()
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1A1A22)
                            ),
                            border = if (isCurrentShoeForSession) BorderStroke(1.5.dp, Color(0xFF00E676)) else null,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Line 1: Icon + Shoe Name + Tag (PADRÃO / APOSENTADO)
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
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = null,
                                            tint = if (shoe.isRetired) RunColors.textSecondary else RunColors.brand,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "${shoe.brand} ${shoe.name}",
                                            color = if (shoe.isRetired) RunColors.textSecondary else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (shoe.isDefault && !shoe.isRetired) {
                                        Surface(
                                            color = RunColors.brand.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "PADRÃO",
                                                color = RunColors.brandText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else if (shoe.isRetired) {
                                        Surface(
                                            color = RunColors.border.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "APOSENTADO",
                                                color = RunColors.textSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Line 2: Mileage Progress Bar
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Rodagem: ${String.format(Locale.US, "%.1f", shoe.totalDistanceKm)} km",
                                            color = RunColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Limite: ${String.format(Locale.US, "%.0f", shoe.maxDistanceKm)} km",
                                            color = RunColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = if (isExceeded) RunColors.alert else if (isNearLimit) RunColors.phaseWarmup else RunColors.brand,
                                        trackColor = RunColors.border.copy(alpha = 0.5f)
                                    )

                                    if (isNearLimit && !shoe.isRetired) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (isExceeded) RunColors.alert else RunColors.phaseWarmup,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = if (isExceeded) "Limite excedido! Recomendado substituir o par." else "Próximo da troca (acima de 90%).",
                                                color = if (isExceeded) RunColors.alert else RunColors.phaseWarmup,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Line 3: Footer Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionTargetSession != null && onSelectShoeForSession != null) {
                                        val isCurrentShoe = selectionTargetSession.shoeName.equals(shoe.fullDisplayName(), ignoreCase = true) == true
                                        Button(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                onSelectShoeForSession(shoe.fullDisplayName())
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrentShoe) Color(0xFF00E676) else RunColors.brand
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isCurrentShoe) "✓ VINCULADO" else "VINCULAR A ESTA CORRIDA",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (!shoe.isRetired) {
                                            if (!shoe.isDefault) {
                                                TextButton(
                                                    onClick = { onSetDefault(shoe.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Definir Padrão", color = RunColors.brandText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            TextButton(
                                                onClick = { onRetireShoe(shoe.id) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Aposentar", color = RunColors.phaseWarmup, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (onUpdateShoe != null) {
                                            IconButton(
                                                onClick = { editingShoe = shoe },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDeleteShoe(shoe.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Excluir",
                                                tint = RunColors.alert,
                                                modifier = Modifier.size(16.dp)
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

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Cadastrar Novo Tênis", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newShoeBrand,
                        onValueChange = { newShoeBrand = it },
                        label = { Text("Marca", color = RunColors.textSecondary) },
                        placeholder = { Text("Ex.: Nike", color = RunColors.textSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = newShoeName,
                        onValueChange = { newShoeName = it },
                        label = { Text("Nome / Modelo do tênis", color = RunColors.textSecondary) },
                        placeholder = { Text("Ex.: Pegasus 40", color = RunColors.textSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = newShoeInitialKm,
                        onValueChange = { newShoeInitialKm = it },
                        label = { Text("Rodagem atual / inicial (km)", color = RunColors.textSecondary) },
                        placeholder = { Text("Ex.: 0 ou 150", color = RunColors.textSecondary.copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = newShoeMaxKm,
                        onValueChange = { newShoeMaxKm = it },
                        label = { Text("Vida útil máxima (km)", color = RunColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isNewDefault,
                            onCheckedChange = { isNewDefault = it },
                            colors = CheckboxDefaults.colors(checkedColor = RunColors.brand)
                        )
                        Text("Definir como tênis padrão para treinos", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newShoeName.isNotBlank() && newShoeBrand.isNotBlank()) {
                            val maxKm = newShoeMaxKm.toFloatOrNull() ?: 600f
                            val initialKm = newShoeInitialKm.toFloatOrNull() ?: 0f
                            onAddShoe(
                                RunningShoe(
                                    name = newShoeName.trim(),
                                    brand = newShoeBrand.trim(),
                                    initialDistanceKm = initialKm,
                                    totalDistanceKm = initialKm,
                                    maxDistanceKm = maxKm,
                                    isDefault = isNewDefault
                                )
                            )
                            showAddDialog = false
                            newShoeName = ""
                            newShoeBrand = ""
                            newShoeInitialKm = "0"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand)
                ) {
                    Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = RunColors.surface
        )
    }

    editingShoe?.let { shoeToEdit ->
        var editBrand by remember(shoeToEdit) { mutableStateOf(shoeToEdit.brand) }
        var editName by remember(shoeToEdit) { mutableStateOf(shoeToEdit.name) }
        var editInitialKm by remember(shoeToEdit) { mutableStateOf(shoeToEdit.initialDistanceKm.toString()) }
        var editMaxKm by remember(shoeToEdit) { mutableStateOf(shoeToEdit.maxDistanceKm.toString()) }
        var editIsDefault by remember(shoeToEdit) { mutableStateOf(shoeToEdit.isDefault) }

        AlertDialog(
            onDismissRequest = { editingShoe = null },
            title = { Text("Editar Tênis", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editBrand,
                        onValueChange = { editBrand = it },
                        label = { Text("Marca", color = RunColors.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nome / Modelo", color = RunColors.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = editInitialKm,
                        onValueChange = { editInitialKm = it },
                        label = { Text("Quilometragem Inicial (km)", color = RunColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = editMaxKm,
                        onValueChange = { editMaxKm = it },
                        label = { Text("Vida útil máxima (km)", color = RunColors.textSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = editIsDefault,
                            onCheckedChange = { editIsDefault = it },
                            colors = CheckboxDefaults.colors(checkedColor = RunColors.brand)
                        )
                        Text("Tênis padrão para treinos", color = Color.White, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank() && editBrand.isNotBlank()) {
                            val updatedInitialKm = editInitialKm.toFloatOrNull() ?: shoeToEdit.initialDistanceKm
                            val updatedMaxKm = editMaxKm.toFloatOrNull() ?: shoeToEdit.maxDistanceKm
                            onUpdateShoe?.invoke(
                                shoeToEdit.copy(
                                    brand = editBrand.trim(),
                                    name = editName.trim(),
                                    initialDistanceKm = updatedInitialKm,
                                    maxDistanceKm = updatedMaxKm,
                                    isDefault = editIsDefault
                                )
                            )
                            editingShoe = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand)
                ) {
                    Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingShoe = null }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = RunColors.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningPaceOnboardingBottomSheet(
    onConfirm: (baselinePaceMinPerKm: Float) -> Unit,
    onDismiss: () -> Unit,
    currentBaselinePaceMinPerKm: Float? = 7.0f,
    paceAnalysis: PaceCalibrationAnalysis? = null,
    isAutoCalibrationEnabled: Boolean = true,
    onToggleAutoCalibration: (Boolean) -> Unit = {}
) {
    val initialPaceSec = remember(currentBaselinePaceMinPerKm) {
        ((currentBaselinePaceMinPerKm ?: 7.0f) * 60f).roundToInt().coerceIn(210, 900)
    }
    var manualPaceSecPerKm by remember(initialPaceSec) { mutableIntStateOf(initialPaceSec) }
    var paceInputText by remember(initialPaceSec) {
        mutableStateOf(String.format(Locale.US, "%d:%02d", initialPaceSec / 60, initialPaceSec % 60))
    }
    var time5kInputText by remember(initialPaceSec) {
        val total5k = initialPaceSec * 5
        mutableStateOf(String.format(Locale.US, "%02d:%02d", total5k / 60, total5k % 60))
    }
    var auto14dState by remember(isAutoCalibrationEnabled) { mutableStateOf(isAutoCalibrationEnabled) }
    var showCalculatedResultCard by remember(paceAnalysis) {
        mutableStateOf(paceAnalysis?.shouldSuggestUpdate == true)
    }

    fun syncFromPaceSeconds(newPaceSec: Int) {
        val clamped = newPaceSec.coerceIn(210, 900)
        manualPaceSecPerKm = clamped
        paceInputText = String.format(Locale.US, "%d:%02d", clamped / 60, clamped % 60)
        val total5k = clamped * 5
        time5kInputText = String.format(Locale.US, "%02d:%02d", total5k / 60, total5k % 60)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151922)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CALIBRAÇÃO DE RITMO (5 KM)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        text = "Autocalibração periódica IA (14 dias) ou ajuste fino manual",
                        color = RunColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = Color(0xFF00B0FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = RunningProgramManager.formatPaceSecPerKm(manualPaceSecPerKm),
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 1. CHAVE (SWITCH): Recálculo Automático a cada 14 dias (IA)
            Surface(
                color = Color(0xFF0D0F14),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (auto14dState) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF262D3D)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Recálculo Automático a cada 14 dias (IA)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (auto14dState) {
                                paceAnalysis?.cycleStatusLine
                                    ?: "Ativo • Analisa corridas reais e FC média a cada 14 dias"
                            } else {
                                "Modo Manual • O pace só muda quando você ajustar"
                            },
                            color = if (auto14dState) Color(0xFF00E5FF) else RunColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = auto14dState,
                        onCheckedChange = { enabled ->
                            AudioSynth.playTickSound()
                            auto14dState = enabled
                            onToggleAutoCalibration(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF0D0F14),
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E2433)
                        ),
                        modifier = Modifier.testTag("switch_auto_calibration_14d")
                    )
                }
            }

            // 2. BOTÃO: ⚡ Calcular Agora pelo Meu Histórico
            OutlinedButton(
                onClick = {
                    AudioSynth.playTickSound()
                    showCalculatedResultCard = true
                    paceAnalysis?.calculatedEffectivePaceSecPerKm?.let { calcSec ->
                        syncFromPaceSeconds(calcSec)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF00E676)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF00E676).copy(alpha = 0.10f),
                    contentColor = Color(0xFF00E676)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_calculate_pace_from_history")
            ) {
                Text(
                    text = "⚡ Calcular Agora pelo Meu Histórico",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            // Card Comparativo de Diagnóstico IA (Pace Sugerido vs Chute Manual Atual)
            if (showCalculatedResultCard) {
                Surface(
                    color = Color(0xFF0D0F14),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (paceAnalysis?.hasEnoughData == true) Color(0xFF00E676).copy(alpha = 0.6f)
                        else Color(0xFFFFB300).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (paceAnalysis != null && paceAnalysis.hasEnoughData && paceAnalysis.calculatedEffectivePaceSecPerKm != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "ATUAL / MANUAL",
                                        color = RunColors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = paceAnalysis.formattedCurrentPace,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "➔",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "PACE REAL SUGERIDO (IA)",
                                        color = Color(0xFF00E676),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${paceAnalysis.formattedCalculatedPace} (5K ~${paceAnalysis.estimated5kTimeFormatted})",
                                        color = Color(0xFF00E676),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Text(
                                text = paceAnalysis.hrAdjustmentSummary,
                                color = RunColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    val calcMin = paceAnalysis.calculatedEffectivePaceMinPerKm
                                        ?: (paceAnalysis.calculatedEffectivePaceSecPerKm / 60f)
                                    onConfirm(calcMin)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("btn_apply_suggested_pace_1tap")
                            ) {
                                Text(
                                    text = "APLICAR PACE SUGERIDO (${paceAnalysis.formattedCalculatedPace})",
                                    color = Color(0xFF0D0F14),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            Text(
                                text = "Nenhuma corrida válida encontrada no histórico ainda.",
                                color = Color(0xFFFFD600),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Conclua ao menos 1 treino de corrida no MAX RUN para que a IA meça seu Pace Real descontando caminhadas (>9'30\"/km) e calibrando pela sua FC.",
                                color = RunColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // 3. CONTROLE DE AJUSTE MANUAL (-5s / +5s ou Minutos:Segundos)
            Surface(
                color = Color(0xFF0D0F14),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF262D3D)),
                modifier = Modifier.fillMaxWidth()
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
                        Text(
                            text = "AJUSTE MANUAL FINO (PACE / 5 KM)",
                            color = RunColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "5K equiv.: ${time5kInputText}",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                syncFromPaceSeconds(manualPaceSecPerKm - 5)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF00B0FF)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF151922),
                                contentColor = Color(0xFF00E5FF)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_pace_minus_5s")
                        ) {
                            Text("-5s", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }

                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF262D3D)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = RunningProgramManager.formatPaceSecPerKm(manualPaceSecPerKm),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                syncFromPaceSeconds(manualPaceSecPerKm + 5)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF00B0FF)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF151922),
                                contentColor = Color(0xFF00E5FF)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_pace_plus_5s")
                        ) {
                            Text("+5s", fontWeight = FontWeight.Black, fontSize = 14.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = paceInputText,
                            onValueChange = { raw ->
                                paceInputText = raw
                                val parts = raw.trim().split(":", "'")
                                if (parts.size == 2) {
                                    val m = parts[0].toIntOrNull()
                                    val s = parts[1].replace("\"", "").toIntOrNull()
                                    if (m != null && s != null && s in 0..59) {
                                        val sec = (m * 60 + s).coerceIn(210, 900)
                                        manualPaceSecPerKm = sec
                                        val total5k = sec * 5
                                        time5kInputText = String.format(Locale.US, "%02d:%02d", total5k / 60, total5k % 60)
                                    }
                                }
                            },
                            label = { Text("Pace / km (M:SS)", color = RunColors.textSecondary, fontSize = 11.sp) },
                            placeholder = { Text("Ex: 6:15", color = RunColors.textSecondary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_manual_pace_mmss"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RunColors.brand,
                                unfocusedBorderColor = RunColors.border,
                                focusedLabelColor = RunColors.brand,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = time5kInputText,
                            onValueChange = { raw ->
                                time5kInputText = raw
                                val parts = raw.trim().split(":")
                                val totalSeconds = if (parts.size == 2) {
                                    val m = parts[0].toIntOrNull()
                                    val s = parts[1].toIntOrNull()
                                    if (m != null && s != null && s in 0..59) m * 60 + s else null
                                } else {
                                    raw.replace(',', '.').toFloatOrNull()?.let { (it * 60f).roundToInt() }
                                }
                                if (totalSeconds != null && totalSeconds in 1050..4500) {
                                    val secPerKm = (totalSeconds / 5.0).roundToInt().coerceIn(210, 900)
                                    manualPaceSecPerKm = secPerKm
                                    paceInputText = String.format(Locale.US, "%d:%02d", secPerKm / 60, secPerKm % 60)
                                }
                            },
                            label = { Text("Tempo 5 km (MM:SS)", color = RunColors.textSecondary, fontSize = 11.sp) },
                            placeholder = { Text("Ex: 35:00", color = RunColors.textSecondary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_manual_5k_time"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RunColors.brand,
                                unfocusedBorderColor = RunColors.border,
                                focusedLabelColor = RunColors.brand,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val baselinePace = (manualPaceSecPerKm / 60f).coerceIn(3.5f, 15f)
                    AudioSynth.playTickSound()
                    onConfirm(baselinePace)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_confirm_pace_calibration"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "CONFIRMAR E SALVAR (${RunningProgramManager.formatPaceSecPerKm(manualPaceSecPerKm)})",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
