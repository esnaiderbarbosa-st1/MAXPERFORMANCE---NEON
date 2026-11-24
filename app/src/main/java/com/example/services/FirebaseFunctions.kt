package com.example.services

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.AuditLog
import java.util.UUID

object FirebaseFunctions {
    private const val TAG = "FirebaseFunctions"

    // Simulates an automated email being sent by a server-side process/Cloud Function
    private fun simulateEmailSending(recipientEmail: String, subject: String, body: String) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, " [CLOUD FUNCTION: SMTP EMAIL SERVICE TRIGGERED]")
        Log.i(TAG, " To: $recipientEmail")
        Log.i(TAG, " Subject: $subject")
        Log.i(TAG, " Body: $body")
        Log.i(TAG, "=========================================================")
    }

    // Cloud Function: Approve User Registration
    suspend fun approveRegistration(
        adminEmail: String,
        requestId: String,
        assignedLevel: String,
        notes: String,
        context: Context
    ): Result<Boolean> {
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val req = dao.getRequestById(requestId) ?: return Result.failure(Exception("Solicitação não encontrada."))
            val user = dao.getUserById(req.userId) ?: return Result.failure(Exception("Usuário correspondente não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'approveRegistration' for Request ID: $requestId")

            // 1. Update request status
            val updatedReq = req.copy(status = "APROVADO", observacoes = notes)
            dao.insertRequest(updatedReq)

            // 2. Update user access level and status
            val updatedUser = user.copy(nivelAcesso = assignedLevel, status = "ATIVO")
            dao.insertUser(updatedUser)

            // 3. Register superuser audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "APROVAR_CADASTRO",
                usuarioAfetado = user.nome,
                detalhes = "Aprovou o cadastro de ${user.email} como nível $assignedLevel. Observações: $notes"
            )
            dao.insertAuditLog(auditLog)

            // 4. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Seu cadastro no MAX PERFORMANCE foi Aprovado!",
                "Olá, ${user.nome}.\n\nSeu acesso foi liberado com o nível: $assignedLevel.\nAproveite todos os seus treinos e recursos!\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Reject User Registration
    suspend fun rejectRegistration(
        adminEmail: String,
        requestId: String,
        reason: String,
        context: Context
    ): Result<Boolean> {
        if (reason.isBlank()) return Result.failure(Exception("Informe o motivo da rejeição."))

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val req = dao.getRequestById(requestId) ?: return Result.failure(Exception("Solicitação não encontrada."))
            val user = dao.getUserById(req.userId) ?: return Result.failure(Exception("Usuário correspondente não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'rejectRegistration' for Request ID: $requestId")

            // 1. Update request status
            val updatedReq = req.copy(status = "REJEITADO", motivoRejeicao = reason)
            dao.insertRequest(updatedReq)

            // 2. Update user status
            val updatedUser = user.copy(status = "REJEITADO", motivoRejeicao = reason)
            dao.insertUser(updatedUser)

            // 3. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "REJEITAR_CADASTRO",
                usuarioAfetado = user.nome,
                detalhes = "Rejeitou o cadastro de ${user.email}. Motivo: $reason"
            )
            dao.insertAuditLog(auditLog)

            // 4. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Seu cadastro no MAX PERFORMANCE - Solicitação Rejeitada",
                "Olá, ${user.nome}.\n\nSua solicitação de acesso foi analisada e rejeitada pelo administrador.\nMotivo: $reason\n\nCaso tenha alguma dúvida, responda a este e-mail.\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Approve User Access Upgrade
    suspend fun approveUpgrade(
        adminEmail: String,
        requestId: String,
        context: Context
    ): Result<Boolean> {
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val req = dao.getRequestById(requestId) ?: return Result.failure(Exception("Solicitação não encontrada."))
            val user = dao.getUserById(req.userId) ?: return Result.failure(Exception("Usuário não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'approveUpgrade' for Request ID: $requestId")

            // 1. Update request status
            val updatedReq = req.copy(status = "APROVADO")
            dao.insertRequest(updatedReq)

            // 2. Update user access level
            val targetLevel = req.nivelSolicitado
            val updatedUser = user.copy(nivelAcesso = targetLevel)
            dao.insertUser(updatedUser)

            // 3. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "APROVAR_UPGRADE",
                usuarioAfetado = user.nome,
                detalhes = "Aprovou upgrade de ${user.email} para nível $targetLevel"
            )
            dao.insertAuditLog(auditLog)

            // 4. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Seu Upgrade de Acesso foi Aprovado! [MAX PERFORMANCE]",
                "Olá, ${user.nome}.\n\nTemos boas notícias! Sua solicitação de upgrade foi aprovada.\nSeu novo nível de acesso é: $targetLevel.\n\nTodos os recursos avançados deste nível já estão liberados para você.\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Reject User Access Upgrade
    suspend fun rejectUpgrade(
        adminEmail: String,
        requestId: String,
        reason: String,
        context: Context
    ): Result<Boolean> {
        if (reason.isBlank()) return Result.failure(Exception("Informe o motivo da rejeição."))

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val req = dao.getRequestById(requestId) ?: return Result.failure(Exception("Solicitação não encontrada."))
            val user = dao.getUserById(req.userId) ?: return Result.failure(Exception("Usuário não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'rejectUpgrade' for Request ID: $requestId")

            // 1. Update request status
            val updatedReq = req.copy(status = "REJEITADO", motivoRejeicao = reason)
            dao.insertRequest(updatedReq)

            // 2. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "REJEITAR_UPGRADE",
                usuarioAfetado = user.nome,
                detalhes = "Rejeitou upgrade de ${user.email} para nível ${req.nivelSolicitado}. Motivo: $reason"
            )
            dao.insertAuditLog(auditLog)

            // 3. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Sua solicitação de Upgrade de Acesso - MAX PERFORMANCE",
                "Olá, ${user.nome}.\n\nInfelizmente sua solicitação de upgrade para o nível ${req.nivelSolicitado} não pôde ser atendida neste momento.\nMotivo: $reason\n\nCaso tenha alguma dúvida, entre em contato conosco.\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Suspend / Block User
    suspend fun blockUser(
        adminEmail: String,
        userId: String,
        reason: String,
        context: Context
    ): Result<Boolean> {
        if (reason.isBlank()) return Result.failure(Exception("Informe a justificativa do bloqueio."))

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val user = dao.getUserById(userId) ?: return Result.failure(Exception("Usuário não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'blockUser' for User ID: $userId")

            // 1. Update user status
            val updatedUser = user.copy(status = "BLOQUEADO", motivoBloqueio = reason)
            dao.insertUser(updatedUser)

            // 2. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "BLOQUEAR_USUARIO",
                usuarioAfetado = user.nome,
                detalhes = "Bloqueou o acesso do usuário ${user.email}. Motivo: $reason"
            )
            dao.insertAuditLog(auditLog)

            // 3. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Sua conta do MAX PERFORMANCE foi Suspensa",
                "Olá, ${user.nome}.\n\nInformamos que o seu acesso ao aplicativo MAX PERFORMANCE foi suspenso pelo administrador.\nMotivo: $reason\n\nPara esclarecimentos, responda a este e-mail.\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Unblock / Reactivate User
    suspend fun unblockUser(
        adminEmail: String,
        userId: String,
        context: Context
    ): Result<Boolean> {
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val user = dao.getUserById(userId) ?: return Result.failure(Exception("Usuário não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'unblockUser' for User ID: $userId")

            // 1. Update user status
            val updatedUser = user.copy(status = "ATIVO", motivoBloqueio = "")
            dao.insertUser(updatedUser)

            // 2. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "REATIVAR_USUARIO",
                usuarioAfetado = user.nome,
                detalhes = "Reativou o acesso do usuário ${user.email}"
            )
            dao.insertAuditLog(auditLog)

            // 3. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Seu acesso ao MAX PERFORMANCE foi Reativado!",
                "Olá, ${user.nome}.\n\nBoas notícias! Sua conta foi reativada pelo administrador e você já pode fazer login novamente.\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Change User access level manually
    suspend fun changeUserLevel(
        adminEmail: String,
        userId: String,
        newLevel: String,
        justification: String,
        context: Context
    ): Result<Boolean> {
        if (justification.isBlank()) return Result.failure(Exception("Informe a justificativa da mudança."))

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val user = dao.getUserById(userId) ?: return Result.failure(Exception("Usuário não encontrado."))
            val oldLevel = user.nivelAcesso

            Log.i(TAG, "Invoking Cloud Function 'changeUserLevel' for User ID: $userId")

            // 1. Update user level
            val updatedUser = user.copy(nivelAcesso = newLevel)
            dao.insertUser(updatedUser)

            // 2. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "ALTERAR_NIVEL",
                usuarioAfetado = user.nome,
                detalhes = "Alterou o nível de ${user.email} de $oldLevel para $newLevel. Justificativa: $justification"
            )
            dao.insertAuditLog(auditLog)

            // 3. Trigger email simulation
            simulateEmailSending(
                user.email,
                "Seu nível de acesso foi alterado [MAX PERFORMANCE]",
                "Olá, ${user.nome}.\n\nSeu nível de acesso foi alterado de $oldLevel para $newLevel.\nMotivo: $justification\n\nAtenciosamente,\nEquipe MAX PERFORMANCE."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Cloud Function: Permanent Exclusion
    suspend fun deleteUserPermanently(
        adminEmail: String,
        userId: String,
        context: Context
    ): Result<Boolean> {
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val user = dao.getUserById(userId) ?: return Result.failure(Exception("Usuário não encontrado."))

            Log.i(TAG, "Invoking Cloud Function 'deleteUserPermanently' for User ID: $userId")

            // 1. Delete user from local database
            dao.deleteUser(user)

            // 2. Register audit log
            val auditLog = AuditLog(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                timestamp = System.currentTimeMillis(),
                superuserEmail = adminEmail,
                acao = "EXCLUIR_USUARIO",
                usuarioAfetado = user.nome,
                detalhes = "Excluiu permanentemente a conta de ${user.email}"
            )
            dao.insertAuditLog(auditLog)

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
