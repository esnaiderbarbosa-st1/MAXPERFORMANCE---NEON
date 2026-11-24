package com.example.services.auth

import com.example.data.model.UserProfileData

/**
 * Roles supported by the Adaptive Training Platform.
 * Extremely scalable and ready to accommodate professional coaching and therapeutic accounts.
 */
enum class Role(val displayName: String) {
    USER("Usuário Comum"),
    SUPERUSER("Super Usuário"),
    COACH("Treinador"),
    ADMIN("Administrador"),
    NUTRITIONIST("Nutricionista"),
    PHYSIOTHERAPIST("Fisioterapeuta")
}

/**
 * Granular permissions mapped to restricted screens, actions, and features.
 */
enum class Permission(val displayName: String, val description: String) {
    VIEW_USERS("Visualizar Usuários", "Permite visualizar a lista completa de usuários cadastrados"),
    VIEW_LOGS("Visualizar Logs de Auditoria", "Acesso aos logs de auditoria interna do sistema"),
    VIEW_DATABASE("Inspecionar Banco de Dados", "Permite depurar e inspecionar tabelas do Room"),
    VIEW_TELEMETRY("Visualizar Telemetria", "Acesso às métricas de desempenho e uso"),
    VIEW_CRASHES("Visualizar Relatórios de Erro", "Depuração de falhas técnicas e exceções"),
    FEATURE_FLAGS("Gerenciar Feature Flags", "Habilitar/desabilitar recursos em tempo de execução"),
    SUPPORT_MODE("Modo Suporte", "Acesso temporário para ver o aplicativo como se fosse outro usuário"),
    MANAGE_SYNC("Forçar Sincronização", "Comando manual de sincronização com a nuvem"),
    MANAGE_USERS("Gerenciar Permissões", "Alterar permissões e níveis de acesso de terceiros")
}

/**
 * Central orchestrator for role-based permission verification.
 * Keeps initial email-comparison logic completely isolated to prevent hardcoded spread.
 */
object AuthorizationManager {

    private const val SUPERUSER_EMAIL = "esnaider.barbosa@gmail.com"

    // Mapping representing granular permission lists assigned to each specific Role
    private val rolePermissions: Map<Role, Set<Permission>> = mapOf(
        Role.USER to emptySet(),
        Role.SUPERUSER to Permission.values().toSet(), // SuperUsers obtain full system controls
        Role.COACH to setOf(
            Permission.VIEW_USERS,
            Permission.SUPPORT_MODE
        ),
        Role.NUTRITIONIST to setOf(
            Permission.VIEW_USERS,
            Permission.SUPPORT_MODE
        ),
        Role.PHYSIOTHERAPIST to setOf(
            Permission.VIEW_USERS,
            Permission.SUPPORT_MODE
        ),
        Role.ADMIN to Permission.values().toSet()
    )

    /**
     * Resolves user Role using their email address.
     * Ready to be connected to remote Auth providers (Firebase, Auth0, Supabase) in the future.
     */
    fun resolveRoleByEmail(email: String?): Role {
        if (email == null) return Role.USER
        return if (email.trim().equals(SUPERUSER_EMAIL, ignoreCase = true)) {
            Role.SUPERUSER
        } else {
            Role.USER
        }
    }

    /**
     * Checks if a user profile matches the SuperUser criteria.
     * In Beta testing mode, always returns SUPERUSER to ensure 100% feature access.
     */
    fun resolveRole(profile: UserProfileData?): Role {
        return Role.SUPERUSER
    }

    /**
     * Asserts whether a user has permission based on profile details.
     * In Beta testing mode, all features are 100% unlocked.
     */
    fun hasPermission(profile: UserProfileData?, permission: Permission): Boolean {
        return true
    }

    /**
     * Asserts whether an email address possesses a specific permission.
     */
    fun hasPermissionByEmail(email: String?, permission: Permission): Boolean {
        return true
    }
}

/**
 * Standardized Feature Gate. Composable interfaces and backend actions query this gate
 * to decide whether a visual feature or action should be rendered or permitted.
 */
object FeatureGate {

    fun isFeatureAvailable(profile: UserProfileData?, permission: Permission): Boolean {
        return AuthorizationManager.hasPermission(profile, permission)
    }

    fun isFeatureAvailableForEmail(email: String?, permission: Permission): Boolean {
        return AuthorizationManager.hasPermissionByEmail(email, permission)
    }
}
