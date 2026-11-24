package com.example.services

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.AppUser
import com.example.data.model.AccessRequest
import com.example.data.model.AuditLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object UserManager {
    private const val TAG = "UserManager"
    private const val PREFS_NAME = "max_user_prefs"
    private const val KEY_LOGGED_IN_USER_ID = "logged_in_user_id"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    private const val KEY_TESTING_MODE = "testing_mode_enabled"

    fun isTestingMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TESTING_MODE, true)
    }

    fun setTestingMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_TESTING_MODE, enabled)
            .apply()
    }

    private val defaultActiveUser = AppUser(
        id = "user_demo_2",
        email = "esnaider.barbosa@gmail.com",
        cpf = "987.654.321-01",
        nome = "Esnaider Barbosa",
        nivelAcesso = "INTERMEDIARIO",
        status = "ATIVO",
        createdAt = System.currentTimeMillis() - 86400000L * 10,
        lastLogin = System.currentTimeMillis()
    )

    private val _currentUser = MutableStateFlow<AppUser?>(defaultActiveUser)
    val currentUser: StateFlow<AppUser?> = _currentUser

    // Initialize session and seed the database with the Superuser admin account and mock users
    suspend fun init(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        // 1. Seed Superuser only in DEBUG builds
        if (com.example.BuildConfig.DEBUG) {
            val superuserEmail = "admin@email.com"
            val existingAdmin = dao.getUserByEmail(superuserEmail)
            if (existingAdmin == null) {
                val adminUser = AppUser(
                    id = "superuser_admin_id",
                    email = superuserEmail,
                    cpf = "000.000.000-00",
                    nome = "Superusuário Admin",
                    nivelAcesso = "SUPERUSER",
                    status = "ATIVO",
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis()
                )
                dao.insertUser(adminUser)
                Log.d(TAG, "Superuser seed success")
            }
        }

        // 2. Seed some demo users and requests for the admin panel preview
        val demoUsers = listOf(
            AppUser("user_demo_1", "joao.silva@email.com", "123.456.789-09", "João da Silva", "BASICO", "ATIVO", System.currentTimeMillis() - 86400000 * 3, System.currentTimeMillis() - 3600000),
            AppUser("user_demo_2", "maria.oliveira@email.com", "987.654.321-01", "Maria Oliveira", "INTERMEDIARIO", "ATIVO", System.currentTimeMillis() - 86400000 * 10, System.currentTimeMillis() - 12000000),
            AppUser("user_demo_3", "carlos.souza@email.com", "111.222.333-44", "Carlos Souza", "BASICO", "PENDENTE", System.currentTimeMillis() - 3600000 * 2, 0),
            AppUser("user_demo_4", "ana.santos@email.com", "555.666.777-88", "Ana Santos", "INTERMEDIARIO", "BLOQUEADO", System.currentTimeMillis() - 86400000 * 5, System.currentTimeMillis() - 86400000, motivoBloqueio = "Inadimplência de mensalidade")
        )

        for (u in demoUsers) {
            if (dao.getUserById(u.id) == null) {
                dao.insertUser(u)
                if (u.status == "PENDENTE") {
                    dao.insertRequest(
                        AccessRequest(
                            id = UUID.randomUUID().toString(),
                            userId = u.id,
                            nome = u.nome,
                            email = u.email,
                            cpf = u.cpf,
                            tipo = "REGISTRO",
                            nivelSolicitado = "BASICO",
                            createdAt = u.createdAt,
                            status = "PENDENTE",
                            observacoes = "Novo cadastro via aplicativo"
                        )
                    )
                }
            }
        }

        // 3. Restore session or automatically login active demo user to bypass login screen
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var savedUserId = sharedPrefs.getString(KEY_LOGGED_IN_USER_ID, null)
        if (savedUserId == null) {
            savedUserId = "user_demo_2" // Auto-login "Maria Oliveira" (INTERMEDIARIO, ATIVO)
            sharedPrefs.edit().putString(KEY_LOGGED_IN_USER_ID, savedUserId).apply()
        }
        val user = dao.getUserById(savedUserId)
        if (user != null && user.status == "ATIVO") {
            _currentUser.value = user
            dao.insertUser(user.copy(lastLogin = System.currentTimeMillis()))
        } else {
            // Fallback to active demo user if user is null or blocked
            val fallbackUser = dao.getUserById("user_demo_2")
            if (fallbackUser != null) {
                _currentUser.value = fallbackUser
                sharedPrefs.edit().putString(KEY_LOGGED_IN_USER_ID, "user_demo_2").apply()
            }
        }
    }

    // Brazilian CPF Validation Digits Algorithm
    fun validateCpf(cpf: String): Boolean {
        val cleanCpf = cpf.replace(Regex("[^0-9]"), "")
        if (cleanCpf.length != 11) return false
        if (cleanCpf.all { it == cleanCpf[0] }) return false

        try {
            val numbers = cleanCpf.map { it.toString().toInt() }
            
            // First digit validation
            var sum = 0
            for (i in 0..8) {
                sum += numbers[i] * (10 - i)
            }
            var firstDigit = 11 - (sum % 11)
            if (firstDigit >= 10) firstDigit = 0
            if (numbers[9] != firstDigit) return false

            // Second digit validation
            sum = 0
            for (i in 0..9) {
                sum += numbers[i] * (11 - i)
            }
            var secondDigit = 11 - (sum % 11)
            if (secondDigit >= 10) secondDigit = 0
            if (numbers[10] != secondDigit) return false

            return true
        } catch (e: Exception) {
            return false
        }
    }

    // Strong Password Verification
    fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) return "A senha deve ter no mínimo 8 caracteres."
        if (!password.any { it.isUpperCase() }) return "A senha deve conter pelo menos uma letra maiúscula."
        if (!password.any { it.isLowerCase() }) return "A senha deve conter pelo menos uma letra minúscula."
        if (!password.any { it.isDigit() }) return "A senha deve conter pelo menos um número."
        val specialCharacters = "!@#$%^&*()_+-=[]{}|;':\",./<>?\\"
        if (!password.any { specialCharacters.contains(it) }) return "A senha deve conter pelo menos um caractere especial (!@#$ etc)."
        return null
    }

    // Get lockout remaining minutes if user is locked
    fun getRemainingLockoutTimeMinutes(context: Context): Int {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = sharedPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            return (((lockoutUntil - now) / 60000) + 1).toInt()
        }
        return 0
    }

    // Log a failed login attempt and apply progressive locks
    fun registerFailedAttempt(context: Context) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentFailed = sharedPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = sharedPrefs.edit()
        
        editor.putInt(KEY_FAILED_ATTEMPTS, currentFailed)

        if (currentFailed >= 5) {
            // Progressive lockout timeline in minutes: 1st lock (5 fails) = 1min, 2nd (6 fails) = 5min, 3rd = 15min, 4th+ = 60min
            val lockMinutes = when (currentFailed) {
                5 -> 1
                6 -> 5
                7 -> 15
                else -> 60
            }
            val lockoutUntil = System.currentTimeMillis() + (lockMinutes * 60 * 1000)
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
            Log.w(TAG, "User locked out for $lockMinutes minutes due to $currentFailed failed attempts.")
        }
        editor.apply()
    }

    fun resetFailedAttempts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    // Sign Up user
    suspend fun signUp(context: Context, nome: String, email: String, cpf: String, pass: String): Result<AppUser> {
        if (nome.isBlank() || email.isBlank() || cpf.isBlank() || pass.isBlank()) {
            return Result.failure(Exception("Preencha todos os campos obrigatórios."))
        }

        if (!validateCpf(cpf)) {
            return Result.failure(Exception("CPF inválido. Verifique os dígitos."))
        }

        val passError = validatePasswordStrength(pass)
        if (passError != null) {
            return Result.failure(Exception(passError))
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        // Uniqueness check
        if (dao.getUserByEmail(email) != null) {
            return Result.failure(Exception("Este e-mail já está cadastrado."))
        }
        if (dao.getUserByCpf(cpf) != null) {
            return Result.failure(Exception("Este CPF já está cadastrado em outra conta."))
        }

        try {
            val userId = "user_${UUID.randomUUID().toString().take(8)}"
            // Standard Firebase Authentication SDK Simulation Log
            Log.i(TAG, "Connecting to Firebase Auth API to create user: $userId")
            Log.i(TAG, "Syncing Firebase Firestore profile database...")
            // Automatically approve in testing mode to facilitate local developer/testing setup (Correção 1)
            val isTestingMode = isTestingMode(context)
            val level = if (isTestingMode) "INTERMEDIARIO" else "PENDENTE"
            val status = if (isTestingMode) "ATIVO" else "PENDENTE"

            val newUser = AppUser(
                id = userId,
                email = email,
                cpf = cpf,
                nome = nome,
                nivelAcesso = level,
                status = status,
                createdAt = System.currentTimeMillis(),
                lastLogin = 0
            )
            
            // Insert user
            dao.insertUser(newUser)

            // Create request
            val req = AccessRequest(
                id = "req_${UUID.randomUUID().toString().take(8)}",
                userId = userId,
                nome = nome,
                email = email,
                cpf = cpf,
                tipo = "REGISTRO",
                nivelSolicitado = "BASICO",
                createdAt = System.currentTimeMillis(),
                status = if (isTestingMode) "APROVADO" else "PENDENTE",
                observacoes = if (isTestingMode) "Aprovado automaticamente (Modo de Teste)" else "Solicitação de cadastro inicial pendente de aprovação"
            )
            dao.insertRequest(req)

            // Push Notification to superuser
            NotificationsManager.dispatchNotification(
                context,
                "Nova solicitação de acesso",
                "O usuário $nome se registrou e aguarda aprovação de nível."
            )

            return Result.success(newUser)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Log In user
    suspend fun login(context: Context, email: String, pass: String): Result<AppUser> {
        val lockoutMinutes = getRemainingLockoutTimeMinutes(context)
        if (lockoutMinutes > 0) {
            return Result.failure(Exception("Muitas tentativas falhas. Conta bloqueada temporariamente. Tente novamente em $lockoutMinutes minuto(s)."))
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        val user = dao.getUserByEmail(email.trim().lowercase())
        if (user == null) {
            registerFailedAttempt(context)
            return Result.failure(Exception("Usuário não cadastrado."))
        }

        // Simulating matching password for simple demonstration; in full integration, Firebase Auth validates password
        if (pass.length < 8) {
            registerFailedAttempt(context)
            return Result.failure(Exception("Senha inválida (mínimo 8 caracteres)."))
        }

        resetFailedAttempts(context)

        // Status checks
        if (user.status == "BLOQUEADO") {
            return Result.failure(Exception("Esta conta está bloqueada pelo administrador. Motivo: ${user.motivoBloqueio}"))
        }
        if (user.status == "REJEITADO") {
            return Result.failure(Exception("Seu cadastro foi rejeitado pelo administrador. Motivo: ${user.motivoRejeicao}"))
        }
        if (user.status == "PENDENTE" || user.nivelAcesso.uppercase() == "PENDENTE") {
            _currentUser.value = user // Allow viewing the status page
            return Result.success(user)
        }

        // Successful login
        val updatedUser = user.copy(lastLogin = System.currentTimeMillis())
        dao.insertUser(updatedUser)
        _currentUser.value = updatedUser

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LOGGED_IN_USER_ID, updatedUser.id)
            .apply()

        Log.i(TAG, "Login successful: ${user.id} (Level: ${user.nivelAcesso})")
        return Result.success(updatedUser)
    }

    // Refresh Current User Session
    suspend fun refreshSession(context: Context) {
        val user = _currentUser.value ?: return
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()
        val latestUser = dao.getUserById(user.id)
        if (latestUser != null) {
            _currentUser.value = latestUser
        }
    }

    // Log Out user
    fun logout(context: Context) {
        _currentUser.value = null
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LOGGED_IN_USER_ID)
            .apply()
        Log.i(TAG, "User logged out successfully")
    }

    // Direct User Level Update (Modo Full toggle)
    suspend fun setUserLevel(context: Context, newLevel: String): Result<Boolean> {
        val user = _currentUser.value ?: return Result.failure(Exception("Nenhum usuário logado."))
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val updatedUser = user.copy(nivelAcesso = newLevel)
            dao.insertUser(updatedUser)
            _currentUser.value = updatedUser
            Log.i(TAG, "User level updated to $newLevel successfully")
            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // Send Upgrade Request
    suspend fun requestUpgrade(context: Context, nivelSolicitado: String, observacoes: String = ""): Result<Boolean> {
        val user = _currentUser.value ?: return Result.failure(Exception("Nenhum usuário logado."))
        val db = AppDatabase.getDatabase(context)
        val dao = db.accessControlDao()

        try {
            val req = AccessRequest(
                id = "req_${UUID.randomUUID().toString().take(8)}",
                userId = user.id,
                nome = user.nome,
                email = user.email,
                cpf = user.cpf,
                tipo = "UPGRADE",
                nivelSolicitado = nivelSolicitado,
                createdAt = System.currentTimeMillis(),
                status = "PENDENTE",
                observacoes = observacoes
            )
            dao.insertRequest(req)

            // Notify Admin
            NotificationsManager.dispatchNotification(
                context,
                "Solicitação de Upgrade de Acesso",
                "O usuário ${user.nome} solicitou o nível $nivelSolicitado."
            )

            return Result.success(true)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
