package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RunningSession
import com.example.data.model.UpcomingRaceEvent
import com.example.data.model.UserProfileData
import com.example.data.model.Workout
import com.example.data.model.WorkoutHistoryEntry
import com.example.data.model.WorkoutSessionEntity
import com.example.data.model.toUpcomingRaceEvents
import com.example.data.model.toUpcomingRacesJson
import com.example.data.repository.GymHistoryRepository
import com.example.domain.recomp.BodyRecompEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppBackupPayload(
    val backupVersion: Int = AppBackupManager.CURRENT_BACKUP_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val userProfile: UserProfileData? = null,
    val runningSessions: List<RunningSession> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val workoutHistory: List<WorkoutHistoryEntry> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val gymSessions: List<GymWorkoutSession> = emptyList(),
    val bioimpedanceRecords: List<BioimpedanceRecord> = emptyList(),
    val upcomingRaces: List<UpcomingRaceEvent> = emptyList()
)

data class BackupSummary(
    val hasUserProfile: Boolean,
    val runningSessionsCount: Int,
    val workoutsCount: Int,
    val workoutHistoryCount: Int,
    val workoutSessionsCount: Int,
    val gymSessionsCount: Int,
    val bioimpedanceRecordsCount: Int,
    val upcomingRacesCount: Int
) {
    val totalItems: Int
        get() = (if (hasUserProfile) 1 else 0) +
            runningSessionsCount +
            workoutsCount +
            workoutHistoryCount +
            workoutSessionsCount +
            gymSessionsCount +
            bioimpedanceRecordsCount +
            upcomingRacesCount

    fun toHumanReadableSummary(): String {
        val parts = mutableListOf<String>()
        if (hasUserProfile) parts.add("Perfil")
        parts.add("$runningSessionsCount corrida(s)")
        val gymTotal = workoutsCount + workoutHistoryCount + workoutSessionsCount + gymSessionsCount
        parts.add("$gymTotal registro(s) de musculação")
        parts.add("$bioimpedanceRecordsCount bioimpedância(s)")
        parts.add("$upcomingRacesCount prova(s) futura(s)")
        return parts.joinToString(" • ")
    }
}

/**
 * Gerenciador completo de Exportação e Importação de Backup (.json) via Storage Access Framework (SAF).
 * Suporta serialização/desserialização pura em JVM (Moshi) e restauração idempotente sem apagar dados existentes.
 */
object AppBackupManager {

    const val CURRENT_BACKUP_VERSION = 1
    private const val MAX_PERFORMANCE_PREFS = "max_performance_prefs"
    private const val KEY_UPCOMING_RACES_JSON = "upcoming_races_json_v1"

    private val KNOWN_SECTION_KEYS = listOf(
        "\"backupVersion\"",
        "\"userProfile\"",
        "\"runningSessions\"",
        "\"workouts\"",
        "\"workoutHistory\"",
        "\"workoutSessions\"",
        "\"gymSessions\"",
        "\"bioimpedanceRecords\"",
        "\"upcomingRaces\""
    )

    private val backupAdapter by lazy {
        JsonUtils.moshi.adapter(AppBackupPayload::class.java).indent("  ")
    }

    fun suggestedBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
        return "MAXPerformanceRun_Backup_${fmt.format(Date(nowMillis))}.json"
    }

    /**
     * Serializa o payload completo de backup para JSON UTF-8 formatado (puro JVM).
     */
    fun serializeBackup(payload: AppBackupPayload): String {
        return backupAdapter.toJson(payload)
    }

    /**
     * Faz parse e validação defensiva do arquivo JSON de backup (puro JVM).
     * Nunca lança exceção em JSON malformado, truncado ou de outro aplicativo.
     */
    fun parseBackup(rawJson: String): Result<AppBackupPayload> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Arquivo de backup vazio."))
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return Result.failure(IllegalArgumentException("Formato inválido: o arquivo não é um objeto JSON."))
        }
        val hasKnownKey = KNOWN_SECTION_KEYS.any { trimmed.contains(it) }
        if (!hasKnownKey) {
            return Result.failure(
                IllegalArgumentException("Arquivo JSON não corresponde a um backup do MAX Performance.")
            )
        }
        return runCatching {
            val parsed = backupAdapter.fromJson(trimmed)
                ?: throw IllegalArgumentException("Não foi possível ler os dados do backup JSON.")
            if (parsed.backupVersion <= 0) {
                throw IllegalArgumentException("Versão de backup inválida (${parsed.backupVersion}).")
            }
            parsed
        }
    }

    fun summarize(payload: AppBackupPayload): BackupSummary {
        return BackupSummary(
            hasUserProfile = payload.userProfile != null,
            runningSessionsCount = payload.runningSessions.size,
            workoutsCount = payload.workouts.size,
            workoutHistoryCount = payload.workoutHistory.size,
            workoutSessionsCount = payload.workoutSessions.size,
            gymSessionsCount = payload.gymSessions.size,
            bioimpedanceRecordsCount = payload.bioimpedanceRecords.size,
            upcomingRacesCount = payload.upcomingRaces.size
        )
    }

    suspend fun buildBackupPayload(context: Context): AppBackupPayload = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val db = AppDatabase.getDatabase(appContext)

        val userProfile = db.userDao().getUserProfileSync()
        val runningSessions = db.runningSessionDao().getAllRunningSessions().first()
        val workouts = db.workoutDao().getAllWorkouts().first()
        val workoutHistory = db.historyDao().getAllHistory().first()
        val workoutSessions = db.workoutSessionDao().getAllSessions()

        GymHistoryRepository.init(appContext)
        val gymSessions = GymHistoryRepository.sessions.value

        val bioimpedanceRecords = BodyRecompEngine.loadRecords(appContext, userProfile)

        val mainPrefs = appContext.getSharedPreferences(MAX_PERFORMANCE_PREFS, Context.MODE_PRIVATE)
        val upcomingRaces = runCatching {
            mainPrefs.getString(KEY_UPCOMING_RACES_JSON, null).toUpcomingRaceEvents()
        }.getOrDefault(emptyList())

        val versionLabel = runCatching {
            "${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})"
        }.getOrDefault("")

        AppBackupPayload(
            backupVersion = CURRENT_BACKUP_VERSION,
            exportedAtMillis = System.currentTimeMillis(),
            appVersion = versionLabel,
            userProfile = userProfile,
            runningSessions = runningSessions,
            workouts = workouts,
            workoutHistory = workoutHistory,
            workoutSessions = workoutSessions,
            gymSessions = gymSessions,
            bioimpedanceRecords = bioimpedanceRecords,
            upcomingRaces = upcomingRaces
        )
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildBackupPayload(context)
            val json = serializeBackup(payload)
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Não foi possível abrir o destino para gravar o backup.")
            outputStream.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            summarize(payload)
        }
    }

    /**
     * Importa e restaura o backup de forma idempotente (upsert por ID sem apagar dados locais existentes).
     */
    suspend fun importBackupFromUri(
        context: Context,
        uri: Uri,
        onUpcomingRacesRestored: () -> Unit = {}
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        val rawJson = runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val payload = parseBackup(rawJson).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        runCatching {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)

            // 1. Perfil do usuário
            payload.userProfile?.let { profile ->
                db.userDao().insertProfile(profile)
                WeightCalculator.activeUserProfile = profile
            }

            // 2. Corridas (Room - REPLACE por id)
            payload.runningSessions.forEach { session ->
                db.runningSessionDao().insertRunningSession(session)
            }

            // 3. Treinos / histórico / sessões de musculação (Room - REPLACE por id)
            payload.workouts.forEach { workout ->
                db.workoutDao().insertWorkout(workout)
            }
            payload.workoutHistory.forEach { entry ->
                db.historyDao().insertHistoryEntry(entry)
            }
            payload.workoutSessions.forEach { sessionEntity ->
                db.workoutSessionDao().insertOrUpdate(sessionEntity)
            }

            // 4. Histórico detalhado MAX GYM (SharedPreferences - upsert por id)
            if (payload.gymSessions.isNotEmpty()) {
                GymHistoryRepository.init(appContext)
                payload.gymSessions.forEach { gymSession ->
                    GymHistoryRepository.saveSession(appContext, gymSession)
                }
            }

            // 5. Bioimpedância (SharedPreferences - merge idempotente por id sem apagar registros existentes)
            if (payload.bioimpedanceRecords.isNotEmpty()) {
                val activeProfile = payload.userProfile ?: db.userDao().getUserProfileSync()
                val existingById = BodyRecompEngine.loadRecords(appContext, activeProfile)
                    .associateBy { it.id }
                    .toMutableMap()
                payload.bioimpedanceRecords.forEach { rec ->
                    existingById[rec.id] = rec
                }
                BodyRecompEngine.saveRecords(
                    appContext,
                    existingById.values.sortedBy { it.timestampMillis }
                )
            }

            // 6. Provas futuras (SharedPreferences - merge idempotente por id)
            if (payload.upcomingRaces.isNotEmpty()) {
                val mainPrefs = appContext.getSharedPreferences(MAX_PERFORMANCE_PREFS, Context.MODE_PRIVATE)
                val existingRaces = runCatching {
                    mainPrefs.getString(KEY_UPCOMING_RACES_JSON, null).toUpcomingRaceEvents()
                }.getOrDefault(emptyList())
                val mergedRacesById = existingRaces.associateBy { it.id }.toMutableMap()
                payload.upcomingRaces.forEach { race ->
                    mergedRacesById[race.id] = race
                }
                val mergedSorted = mergedRacesById.values.sortedBy { it.raceDateMillis }
                mainPrefs.edit()
                    .putString(KEY_UPCOMING_RACES_JSON, mergedSorted.toUpcomingRacesJson())
                    .apply()
                withContext(Dispatchers.Main) {
                    onUpcomingRacesRestored()
                }
            }

            summarize(payload)
        }
    }
}
