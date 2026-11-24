package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_users")
data class AppUser(
    @PrimaryKey val id: String,
    val email: String,
    val cpf: String,
    val nome: String,
    val nivelAcesso: String, // "BASICO", "INTERMEDIARIO", "FULL", "SUPERUSER"
    val status: String, // "PENDENTE", "ATIVO", "BLOQUEADO", "REJEITADO"
    val createdAt: Long,
    val lastLogin: Long,
    val motivoBloqueio: String = "",
    val motivoRejeicao: String = ""
)

@Entity(tableName = "access_requests")
data class AccessRequest(
    @PrimaryKey val id: String,
    val userId: String,
    val nome: String,
    val email: String,
    val cpf: String,
    val tipo: String, // "REGISTRO", "UPGRADE"
    val nivelSolicitado: String, // "BASICO", "INTERMEDIARIO", "FULL"
    val createdAt: Long,
    val status: String, // "PENDENTE", "APROVADO", "REJEITADO"
    val observacoes: String = "",
    val motivoRejeicao: String = ""
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val superuserEmail: String,
    val acao: String,
    val usuarioAfetado: String,
    val detalhes: String
)
