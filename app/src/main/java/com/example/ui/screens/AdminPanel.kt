package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUser
import com.example.data.model.AccessRequest
import com.example.data.model.AuditLog
import com.example.ui.adaptive.ResponsiveDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.services.FirebaseFunctions
import com.example.utils.AccessControl
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adminUser by viewModel.currentUser.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val allRequests by viewModel.allRequests.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    val activeTab by viewModel.adminSelectedTab.collectAsState()
    val tabs = listOf("Solicitações", "Ativos", "Bloqueados", "Auditoria")

    // Modals states
    var selectedRequestForAction by remember { mutableStateOf<AccessRequest?>(null) }
    var showApproveModal by remember { mutableStateOf(false) }
    var showRejectModal by remember { mutableStateOf(false) }

    var selectedActiveUserForAction by remember { mutableStateOf<AppUser?>(null) }
    var showChangeLevelModal by remember { mutableStateOf(false) }
    var showBlockModal by remember { mutableStateOf(false) }

    // Input fields for modals
    var notesInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var selectedLevelForApproval by remember { mutableStateOf("INTERMEDIARIO") }
    var searchQuery by remember { mutableStateOf("") }

    val adminEmail = adminUser?.email ?: "admin@email.com"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title Block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PAINEL ADMINISTRATIVO",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Controle de Acesso Granular - Superusuário",
                    color = OnSurfaceDim,
                    fontSize = 12.sp
                )
            }
            
            Button(
                onClick = { com.example.services.UserManager.logout(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Error.copy(alpha = 0.2f), contentColor = Error),
                border = BorderStroke(1.dp, Error.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SAIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = Primary,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { viewModel.setAdminSelectedTab(index) },
                    text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    selectedContentColor = Primary,
                    unselectedContentColor = OnSurfaceDim
                )
            }
        }

        // Search bar (only for actives and logs)
        if (activeTab == 1 || activeTab == 3) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Pesquisar...", color = OnSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceDim) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Content Area based on Tab
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> {
                    // Requests Tab
                    val pendingRequests = allRequests.filter { it.status == "PENDENTE" }
                    if (pendingRequests.isEmpty()) {
                        EmptyStateBlock("Nenhuma solicitação pendente", Icons.Default.CheckCircle, "Todas as contas estão em dia.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(pendingRequests) { req ->
                                RequestItemCard(
                                    request = req,
                                    onApprove = {
                                        selectedRequestForAction = req
                                        selectedLevelForApproval = req.nivelSolicitado
                                        notesInput = ""
                                        showApproveModal = true
                                    },
                                    onReject = {
                                        selectedRequestForAction = req
                                        reasonInput = ""
                                        showRejectModal = true
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Active Users Tab
                    val activeUsersList = allUsers.filter {
                        it.status == "ATIVO" && it.nivelAcesso != "SUPERUSER" &&
                                (searchQuery.isEmpty() || it.nome.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true))
                    }
                    if (activeUsersList.isEmpty()) {
                        EmptyStateBlock("Nenhum usuário ativo encontrado", Icons.Default.Group, "Tente ajustar sua busca.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(activeUsersList) { usr ->
                                ActiveUserItemCard(
                                    user = usr,
                                    onChangeLevel = {
                                        selectedActiveUserForAction = usr
                                        selectedLevelForApproval = usr.nivelAcesso
                                        notesInput = ""
                                        showChangeLevelModal = true
                                    },
                                    onBlock = {
                                        selectedActiveUserForAction = usr
                                        reasonInput = ""
                                        showBlockModal = true
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Blocked Users Tab
                    val blockedUsersList = allUsers.filter { it.status == "BLOQUEADO" || it.status == "REJEITADO" }
                    if (blockedUsersList.isEmpty()) {
                        EmptyStateBlock("Nenhum usuário bloqueado", Icons.Default.Check, "Tudo limpo por aqui!")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(blockedUsersList) { usr ->
                                BlockedUserItemCard(
                                    user = usr,
                                    onReactivate = {
                                        coroutineScope.launch {
                                            FirebaseFunctions.unblockUser(adminEmail, usr.id, context)
                                        }
                                    },
                                    onDelete = {
                                        coroutineScope.launch {
                                            FirebaseFunctions.deleteUserPermanently(adminEmail, usr.id, context)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    // Audit Logs Tab
                    val filteredLogs = auditLogs.filter {
                        searchQuery.isEmpty() || it.superuserEmail.contains(searchQuery, ignoreCase = true) ||
                                it.usuarioAfetado.contains(searchQuery, ignoreCase = true) ||
                                it.detalhes.contains(searchQuery, ignoreCase = true) ||
                                it.acao.contains(searchQuery, ignoreCase = true)
                    }
                    if (filteredLogs.isEmpty()) {
                        EmptyStateBlock("Nenhum registro de auditoria", Icons.Default.ListAlt, "As ações do admin aparecerão aqui.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredLogs) { log ->
                                AuditLogItemRow(log = log)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Approve Request
    ResponsiveDialog(
        visible = showApproveModal,
        onDismissRequest = { showApproveModal = false }
    ) {
        selectedRequestForAction?.let { req ->
            Text(
                "Aprovar Solicitação",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Defina o nível de acesso para ${req.nome}:",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Access level dropdown selection
            val levels = listOf("BASICO", "INTERMEDIARIO", "FULL")
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.forEach { level ->
                    val isSelected = selectedLevelForApproval == level
                    val color = Color(AccessControl.getNivelColorHex(level))
                    Surface(
                        onClick = { selectedLevelForApproval = level },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) color.copy(alpha = 0.15f) else Surface,
                        border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                level,
                                color = if (isSelected) color else OnSurfaceDim,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Observações (Opcional)", color = OnSurfaceDim) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        val result = if (req.tipo == "REGISTRO") {
                            FirebaseFunctions.approveRegistration(adminEmail, req.id, selectedLevelForApproval, notesInput, context)
                        } else {
                            FirebaseFunctions.approveUpgrade(adminEmail, req.id, context)
                        }
                        if (result.isSuccess) {
                            showApproveModal = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Success),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APROVAR E NOTIFICAR USUÁRIO", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Modal: Reject Request
    ResponsiveDialog(
        visible = showRejectModal,
        onDismissRequest = { showRejectModal = false }
    ) {
        selectedRequestForAction?.let { req ->
            Text(
                "Rejeitar Solicitação",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Informe o motivo da rejeição para enviar a ${req.nome}:",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = reasonInput,
                onValueChange = { reasonInput = it },
                label = { Text("Motivo da Rejeição (Obrigatório)", color = Error) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Error,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (reasonInput.isNotBlank()) {
                            val result = if (req.tipo == "REGISTRO") {
                                FirebaseFunctions.rejectRegistration(adminEmail, req.id, reasonInput, context)
                            } else {
                                FirebaseFunctions.rejectUpgrade(adminEmail, req.id, reasonInput, context)
                            }
                            if (result.isSuccess) {
                                showRejectModal = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                enabled = reasonInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("REJEITAR E ENVIAR E-MAIL", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Modal: Change Access Level manually
    ResponsiveDialog(
        visible = showChangeLevelModal,
        onDismissRequest = { showChangeLevelModal = false }
    ) {
        selectedActiveUserForAction?.let { usr ->
            Text(
                "Alterar Nível de Acesso",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Altere o nível de acesso do usuário ${usr.nome}:",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val levels = listOf("BASICO", "INTERMEDIARIO", "FULL")
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.forEach { level ->
                    val isSelected = selectedLevelForApproval == level
                    val color = Color(AccessControl.getNivelColorHex(level))
                    Surface(
                        onClick = { selectedLevelForApproval = level },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) color.copy(alpha = 0.15f) else Surface,
                        border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.05f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                level,
                                color = if (isSelected) color else OnSurfaceDim,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Justificativa (Obrigatório)", color = OnSurfaceDim) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (notesInput.isNotBlank()) {
                            val result = FirebaseFunctions.changeUserLevel(adminEmail, usr.id, selectedLevelForApproval, notesInput, context)
                            if (result.isSuccess) {
                                showChangeLevelModal = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = notesInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ATUALIZAR NÍVEL E NOTIFICAR", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }

    // Modal: Block User
    ResponsiveDialog(
        visible = showBlockModal,
        onDismissRequest = { showBlockModal = false }
    ) {
        selectedActiveUserForAction?.let { usr ->
            Text(
                "Bloquear Usuário",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Suspenda o acesso do usuário ${usr.nome}:",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = reasonInput,
                onValueChange = { reasonInput = it },
                label = { Text("Justificativa / Motivo do Bloqueio (Obrigatório)", color = Error) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Error,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (reasonInput.isNotBlank()) {
                            val result = FirebaseFunctions.blockUser(adminEmail, usr.id, reasonInput, context)
                            if (result.isSuccess) {
                                showBlockModal = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                enabled = reasonInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BLOQUEAR CONTA IMEDIATAMENTE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Subcomponents

@Composable
fun RequestItemCard(
    request: AccessRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(request.createdAt))
    val levelColor = Color(AccessControl.getNivelColorHex(request.nivelSolicitado))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.nome,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "E-mail: ${request.email}",
                        color = OnSurfaceDim,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "CPF: ${request.cpf}",
                        color = OnSurfaceDim,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = levelColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, levelColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (request.tipo == "UPGRADE") "UPGRADE: ${request.nivelSolicitado}" else "REGISTRO",
                        color = levelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Solicitado em: $dateStr",
                    color = OnSurfaceVariant,
                    fontSize = 10.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        border = BorderStroke(1.dp, Error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Rejeitar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Success),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Aprovar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveUserItemCard(
    user: AppUser,
    onChangeLevel: () -> Unit,
    onBlock: () -> Unit
) {
    val levelColor = Color(AccessControl.getNivelColorHex(user.nivelAcesso))
    val loginStr = if (user.lastLogin > 0) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(user.lastLogin))
    } else {
        "Nunca logou"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.nome,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email,
                        color = OnSurfaceDim,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = levelColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, levelColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = AccessControl.getNivelDisplayName(user.nivelAcesso),
                        color = levelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Último login: $loginStr",
                color = OnSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBlock,
                    border = BorderStroke(1.dp, Error.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bloquear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onChangeLevel,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(12.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alterar Nível", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun BlockedUserItemCard(
    user: AppUser,
    onReactivate: () -> Unit,
    onDelete: () -> Unit
) {
    val isRejectedOnly = user.status == "REJEITADO"
    val motive = if (isRejectedOnly) user.motivoRejeicao else user.motivoBloqueio

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Error.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.nome,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email,
                        color = OnSurfaceDim,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = Error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isRejectedOnly) "REJEITADO" else "BLOQUEADO",
                        color = Error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Motivo: ${motive.ifBlank { "Sem justificativa informada." }}",
                    color = OnSurfaceDim,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, "Excluir permanentemente", tint = Error)
                }

                Button(
                    onClick = onReactivate,
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reativar Conta", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AuditLogItemRow(log: AuditLog) {
    val dateStr = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.02f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = log.acao,
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Afetado: ${log.usuarioAfetado}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.detalhes,
                    color = OnSurfaceDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Por: ${log.superuserEmail}",
                    color = OnSurfaceVariant,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = dateStr,
                color = OnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyStateBlock(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = OnSurfaceDim.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = desc,
            color = OnSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
