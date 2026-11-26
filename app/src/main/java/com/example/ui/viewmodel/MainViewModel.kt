package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.constants.TemplatesAndExercises
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FitnessRepository
import com.example.services.ai.GeminiService
import com.example.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.location.Location
import android.annotation.SuppressLint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.example.engine.QuickWorkoutProvider
import com.example.data.model.BetaEvaluationEntity
import com.example.engine.WorkoutEngine


enum class AppScreen {
    HOME, WORKOUTS, PLAYER, LIBRARY, PROFILE, CARDIO, SOCIAL, CLASSROOM, CREATOR, CONCLUIDO, DIARIO, BETA_LAB, RUNNING_HISTORY, SHARE_CARD, RUNNING_DETAIL, GYM, APP_SETTINGS
}

sealed interface WorkoutFlowState {
    data object Idle : WorkoutFlowState
    data object Running : WorkoutFlowState
    data object Finished : WorkoutFlowState
    data object Abandoned : WorkoutFlowState
}

sealed interface ProfileGate {
    data object Loading : ProfileGate
    data object SetupNeeded : ProfileGate
    data object Ready : ProfileGate
}

fun resolveProfileGate(profile: UserProfileData?): ProfileGate {
    return if (profile != null && profile.nome.isNotBlank()) {
        ProfileGate.Ready
    } else {
        ProfileGate.SetupNeeded
    }
}

fun normalizeWeeklyGoalDays(days: Int): Int = days.coerceIn(1, 7)

fun buildInitialUserProfile(
    name: String,
    gender: String,
    age: Int,
    heightCm: Int,
    weightKg: Float,
    weeklyGoal: Int
): UserProfileData? {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return null
    val safeAge = age.coerceIn(12, 100)
    val safeHeight = heightCm.coerceIn(100, 250)
    val safeWeight = weightKg.coerceIn(25f, 300f)
    val safeGoal = normalizeWeeklyGoalDays(weeklyGoal)
    val heightMeters = safeHeight / 100f
    val calculatedImc = if (heightMeters > 0f) safeWeight / (heightMeters * heightMeters) else 0f

    return UserProfileData(
        id = "1",
        nome = cleanName,
        sexo = gender.trim(),
        idade = safeAge,
        altura = safeHeight,
        heightCm = safeHeight,
        peso = safeWeight,
        weightKg = safeWeight,
        nivelExperiencia = "Iniciante",
        localTreino = "Academia",
        metaSemanal = safeGoal,
        imc = calculatedImc
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = FitnessRepository(db)
    private val sharedPrefs = application.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)

    // --- REPOSITORIES & CORE FLOWS ---
    val profileGate: StateFlow<ProfileGate> = repository.rawUserProfileFlow
        .map(::resolveProfileGate)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileGate.Loading)

    val userProfile = repository.userProfileFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserProfileData())
    val workouts = repository.workoutsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repository.historyFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customExercises = repository.customExercisesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cardioSessions = repository.cardioSessionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runningSessions = repository.runningSessionsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun savePersistedRunningShoes(shoes: List<com.example.data.model.RunningShoe>) {
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.RunningShoe::class.java)
            val adapter = com.example.utils.JsonUtils.moshi.adapter<List<com.example.data.model.RunningShoe>>(type)
            val json = adapter.toJson(shoes)
            sharedPrefs.edit()
                .putBoolean("running_shoes_initialized", true)
                .putString("running_shoes_json", json)
                .apply()
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.savePersistedRunningShoes")
        }
    }

    private fun loadPersistedRunningShoes(): List<com.example.data.model.RunningShoe> {
        return try {
            val defaultList = listOf(
                com.example.data.model.RunningShoe(id = "shoe_corre_4", name = "Corre 4", brand = "Olympikus", initialDistanceKm = 0f, totalDistanceKm = 0f, maxDistanceKm = 600f, isDefault = true),
                com.example.data.model.RunningShoe(id = "shoe_pegasus_40", name = "Pegasus 40", brand = "Nike", initialDistanceKm = 0f, totalDistanceKm = 0f, maxDistanceKm = 700f, isDefault = false),
                com.example.data.model.RunningShoe(id = "shoe_on_cloud", name = "Cloudmonster", brand = "On", initialDistanceKm = 0f, totalDistanceKm = 0f, maxDistanceKm = 600f, isDefault = false)
            )
            val isInitialized = sharedPrefs.getBoolean("running_shoes_initialized", false)
            if (!isInitialized) {
                savePersistedRunningShoes(defaultList)
                defaultList
            } else {
                val json = sharedPrefs.getString("running_shoes_json", "[]") ?: "[]"
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.RunningShoe::class.java)
                val adapter = com.example.utils.JsonUtils.moshi.adapter<List<com.example.data.model.RunningShoe>>(type)
                adapter.fromJson(json) ?: emptyList()
            }
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.loadPersistedRunningShoes")
            emptyList()
        }
    }

    private val _runningShoes = MutableStateFlow<List<com.example.data.model.RunningShoe>>(loadPersistedRunningShoes())
    val runningShoes: StateFlow<List<com.example.data.model.RunningShoe>> = _runningShoes.asStateFlow()

    private val officialRacesVaultPrefs = application.getSharedPreferences("official_races_vault_prefs", Context.MODE_PRIVATE)
    private val _officialRaces = MutableStateFlow<List<com.example.data.model.OfficialRaceRecord>>(loadPersistedOfficialRaces())
    val officialRaces: StateFlow<List<com.example.data.model.OfficialRaceRecord>> = _officialRaces.asStateFlow()

    private fun loadPersistedOfficialRaces(): List<com.example.data.model.OfficialRaceRecord> {
        return try {
            val vaultJson = officialRacesVaultPrefs.getString("official_races_json_v1", null)
            val mainJson = sharedPrefs.getString("official_races_json_v1", null)
            val rawJson = if (!vaultJson.isNullOrBlank() && vaultJson != "[]") vaultJson else mainJson
            val parsed = rawJson.toOfficialRaceRecords().filterNot {
                it.id.startsWith("race_from_") && it.raceName.contains("Semana 4", ignoreCase = true)
            }
            if (parsed.isNotEmpty() && (vaultJson.isNullOrBlank() || vaultJson == "[]")) {
                officialRacesVaultPrefs.edit().putString("official_races_json_v1", parsed.toOfficialRacesJson()).apply()
            }
            parsed
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.loadPersistedOfficialRaces")
            emptyList()
        }
    }

    private fun savePersistedOfficialRaces(list: List<com.example.data.model.OfficialRaceRecord>) {
        try {
            val sorted = list.sortedByDescending { it.dateMillis }
            val json = sorted.toOfficialRacesJson()
            officialRacesVaultPrefs.edit().putString("official_races_json_v1", json).apply()
            sharedPrefs.edit().putString("official_races_json_v1", json).apply()
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.savePersistedOfficialRaces")
        }
    }

    fun saveOfficialRaceRecord(record: com.example.data.model.OfficialRaceRecord) {
        val current = _officialRaces.value
        val updated = (current.filter {
            it.id != record.id &&
                (record.linkedRunSessionId.isNullOrBlank() || it.linkedRunSessionId != record.linkedRunSessionId)
        } + record).sortedByDescending { it.dateMillis }
        _officialRaces.value = updated
        savePersistedOfficialRaces(updated)
    }

    fun deleteOfficialRaceRecord(raceId: String) {
        val updated = _officialRaces.value.filter { it.id != raceId }.sortedByDescending { it.dateMillis }
        _officialRaces.value = updated
        savePersistedOfficialRaces(updated)
        officialRacesVaultPrefs.edit().putBoolean("deleted_race_$raceId", true).apply()
    }

    private val _upcomingRaces = MutableStateFlow<List<com.example.data.model.UpcomingRaceEvent>>(loadPersistedUpcomingRaces())
    val upcomingRaces: StateFlow<List<com.example.data.model.UpcomingRaceEvent>> = _upcomingRaces.asStateFlow()

    private fun loadPersistedUpcomingRaces(): List<com.example.data.model.UpcomingRaceEvent> {
        return try {
            val json = sharedPrefs.getString("upcoming_races_json_v1", null)
            json.toUpcomingRaceEvents().sortedBy { it.raceDateMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePersistedUpcomingRaces(list: List<com.example.data.model.UpcomingRaceEvent>) {
        try {
            val sorted = list.sortedBy { it.raceDateMillis }
            val json = sorted.toUpcomingRacesJson()
            sharedPrefs.edit().putString("upcoming_races_json_v1", json).apply()
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.savePersistedUpcomingRaces")
        }
    }

    fun reloadUpcomingRacesFromPrefs() {
        _upcomingRaces.value = loadPersistedUpcomingRaces()
    }

    fun saveUpcomingRaceEvent(event: com.example.data.model.UpcomingRaceEvent) {
        val current = _upcomingRaces.value
        val updated = (current.filter { it.id != event.id } + event).sortedBy { it.raceDateMillis }
        _upcomingRaces.value = updated
        savePersistedUpcomingRaces(updated)
        // Agendamento das notificações de 7 dias e 1 dia no sistema
        com.example.services.UpcomingRaceReminderScheduler.scheduleAlarmsForRace(getApplication(), event)
    }

    fun deleteUpcomingRaceEvent(eventId: String) {
        val target = _upcomingRaces.value.find { it.id == eventId }
        val updated = _upcomingRaces.value.filter { it.id != eventId }.sortedBy { it.raceDateMillis }
        _upcomingRaces.value = updated
        savePersistedUpcomingRaces(updated)
        if (target != null) {
            com.example.services.UpcomingRaceReminderScheduler.cancelAlarmsForRace(getApplication(), target)
        }
    }

    val selectedShareSession = MutableStateFlow<RunningSession?>(null)
    val selectedDetailSession = MutableStateFlow<RunningSession?>(null)
    val selectedRunningProgramId = MutableStateFlow(sharedPrefs.getString("selected_running_program_id", "run_30m") ?: "run_30m")
    val showRunningProgramsCatalog = MutableStateFlow(false)
    val runningPlanningSubTab = MutableStateFlow(0) // 0 = Programas, 1 = Provas & Evolução
    val activePreRunCheckIn = MutableStateFlow<com.example.data.model.PreRunCheckIn?>(null)

    private fun loadPersistedPlanDayOverride(): Pair<Int, Int>? {
        val w = sharedPrefs.getInt("manual_plan_week_override", -1)
        val d = sharedPrefs.getInt("manual_plan_day_override", -1)
        return if (w in 1..10 && d in 1..3) Pair(w, d) else null
    }

    private val _manualPlanDayOverride = MutableStateFlow<Pair<Int, Int>?>(loadPersistedPlanDayOverride())
    val manualPlanDayOverride: StateFlow<Pair<Int, Int>?> = _manualPlanDayOverride.asStateFlow()

    fun setManualPlanDayOverride(weekNumber: Int, dayNumber: Int) {
        val safeW = weekNumber.coerceIn(1, 10)
        val safeD = dayNumber.coerceIn(1, 3)
        _manualPlanDayOverride.value = Pair(safeW, safeD)
        sharedPrefs.edit()
            .putInt("manual_plan_week_override", safeW)
            .putInt("manual_plan_day_override", safeD)
            .apply()
    }

    fun clearManualPlanDayOverride() {
        _manualPlanDayOverride.value = null
        sharedPrefs.edit()
            .remove("manual_plan_week_override")
            .remove("manual_plan_day_override")
            .apply()
    }

    fun setActivePreRunCheckIn(checkIn: com.example.data.model.PreRunCheckIn?) {
        activePreRunCheckIn.value = checkIn
    }

    fun openRunningPlanningTab(subTab: Int = 0) {
        runningPlanningSubTab.value = subTab.coerceIn(0, 1)
        showRunningProgramsCatalog.value = false
        navigateToScreen(AppScreen.GYM)
    }

    val coachVoiceEnabled = MutableStateFlow(sharedPrefs.getBoolean("coach_voice_enabled", true))
    val paceCalibrationMode = MutableStateFlow(
        if (sharedPrefs.getBoolean("pace_auto_calibration_14d_enabled", true)) {
            com.example.engine.PaceCalibrationMode.AUTO_14_DAYS
        } else {
            com.example.engine.PaceCalibrationMode.MANUAL
        }
    )
    val lastPaceCalibrationTimestamp = MutableStateFlow(
        sharedPrefs.getLong("last_pace_calibration_timestamp", 0L)
    )
    val runHudFaceStyle = MutableStateFlow(
        com.example.data.model.RunHudFaceStyle.fromName(
            sharedPrefs.getString("pref_run_hud_face_style", com.example.data.model.RunHudFaceStyle.CLEAN_PRO.name)
        )
    )

    fun setRunHudFaceStyle(style: com.example.data.model.RunHudFaceStyle) {
        runHudFaceStyle.value = style
        sharedPrefs.edit().putString("pref_run_hud_face_style", style.name).apply()
    }

    fun setPaceAutoCalibrationEnabled(enabled: Boolean) {
        val mode = if (enabled) com.example.engine.PaceCalibrationMode.AUTO_14_DAYS else com.example.engine.PaceCalibrationMode.MANUAL
        paceCalibrationMode.value = mode
        sharedPrefs.edit().putBoolean("pace_auto_calibration_14d_enabled", enabled).apply()
    }

    fun setCoachVoiceEnabled(enabled: Boolean) {
        coachVoiceEnabled.value = enabled
        sharedPrefs.edit().putBoolean("coach_voice_enabled", enabled).apply()
    }

    fun selectRunningProgram(programId: String) {
        selectedRunningProgramId.value = programId
        sharedPrefs.edit().putString("selected_running_program_id", programId).apply()
    }

    fun updateSessionShoe(sessionId: String, newShoeName: String?) {
        viewModelScope.launch {
            val currentSession = runningSessions.value.find { it.id == sessionId } ?: return@launch
            val oldShoe = currentSession.shoeName
            val trimmedNewShoe = newShoeName?.trim()?.ifBlank { null }
            if (oldShoe == trimmedNewShoe) return@launch

            val updatedSession = currentSession.copy(shoeName = trimmedNewShoe)
            repository.saveRunningSession(updatedSession)
            if (selectedDetailSession.value?.id == sessionId) {
                selectedDetailSession.value = updatedSession
            }
            if (selectedShareSession.value?.id == sessionId) {
                selectedShareSession.value = updatedSession
            }

            if (!trimmedNewShoe.isNullOrBlank()) {
                val exists = _runningShoes.value.any { shoeMatches(it, trimmedNewShoe) }
                if (!exists) {
                    val newShoe = com.example.data.model.RunningShoe(
                        name = trimmedNewShoe,
                        brand = "Corrida",
                        initialDistanceKm = 0f,
                        totalDistanceKm = 0f,
                        maxDistanceKm = 600f,
                        isDefault = false
                    )
                    _runningShoes.value = _runningShoes.value + newShoe
                }
            }

            val updatedSessions = runningSessions.value.map { if (it.id == sessionId) updatedSession else it }
            recalculateAndPersistShoeMileage(updatedSessions)
        }
    }

    fun updateRunningSessionClassification(
        sessionId: String,
        isRace: Boolean,
        raceName: String?
    ) {
        viewModelScope.launch {
            val currentSession = runningSessions.value.find { it.id == sessionId }
                ?: selectedDetailSession.value?.takeIf { it.id == sessionId }
                ?: return@launch
            val updatedSession = currentSession.withRaceClassification(
                isRace = isRace,
                raceName = raceName
            )
            repository.saveRunningSession(updatedSession)
            if (selectedDetailSession.value?.id == sessionId) {
                selectedDetailSession.value = updatedSession
            }
            if (selectedShareSession.value?.id == sessionId) {
                selectedShareSession.value = updatedSession
            }
        }
    }

    fun getSessionById(sessionId: String): RunningSession? {
        return runningSessions.value.find { it.id == sessionId }
    }

    fun selectRunningDetailSession(session: RunningSession) {
        selectedDetailSession.value = session
    }

    fun getGpsPointsForSession(sessionId: String): Flow<List<com.example.data.model.GpsTrackPoint>> {
        return db.gpsTrackPointDao().getForSession(sessionId)
    }

    suspend fun getGpsPointsForSessionOnce(sessionId: String): List<com.example.data.model.GpsTrackPoint> {
        return db.gpsTrackPointDao().getForSessionOnce(sessionId)
    }

    fun saveRunningSessionAiReport(sessionId: String, reportText: String) {
        viewModelScope.launch {
            val currentSession = runningSessions.value.find { it.id == sessionId }
                ?: selectedDetailSession.value?.takeIf { it.id == sessionId }
                ?: return@launch
            val updated = currentSession.copy(aiCoachReport = reportText)
            repository.saveRunningSession(updated)
            if (selectedDetailSession.value?.id == sessionId) {
                selectedDetailSession.value = updated
            }
            if (selectedShareSession.value?.id == sessionId) {
                selectedShareSession.value = updated
            }
        }
    }

    /**
     * Sincroniza e atualiza as métricas oficiais do relógio (Mi Fitness / Smartwatch) para uma sessão de corrida:
     * - Atualiza distância oficial, FC média/máxima, passos, cadência, passada e calorias ativas/totais;
     * - Marca hrProvenance = OPTICAL_PPG_WATCH;
     * - Recalcula o pace médio (ex: 6'45"/km) e o Fator de Eficiência (EF = m/min ÷ FC média);
     * - Mantém o mapa GPS e os pontos de rota 100% intactos.
     */
    fun syncWatchMetricsForRunningSession(
        sessionId: String,
        officialDistanceKm: Float,
        avgHrBpm: Int?,
        maxHrBpm: Int?,
        totalSteps: Int?,
        avgCadenceSpm: Int?,
        avgStrideCm: Int?,
        activeCaloriesKcal: Int?,
        totalCaloriesKcal: Int?
    ) {
        viewModelScope.launch {
            val currentSession = runningSessions.value.find { it.id == sessionId }
                ?: selectedDetailSession.value?.takeIf { it.id == sessionId }
                ?: return@launch

            val hasMainRunIsolation = currentSession.mainRunDistanceMeters != null &&
                currentSession.mainRunDistanceMeters > 0 &&
                currentSession.mainRunDurationSec != null &&
                currentSession.mainRunDurationSec > 0 &&
                (currentSession.mainRunDurationSec < currentSession.durationSec || currentSession.mainRunDistanceMeters < currentSession.distanceMeters)

            val effectiveDurationSec = (if (hasMainRunIsolation) currentSession.mainRunDurationSec!! else currentSession.durationSec).coerceAtLeast(1)
            val newEffectiveDistMeters = (officialDistanceKm.coerceAtLeast(0.05f) * 1000f).toInt().coerceAtLeast(50)

            val warmupExtraMeters = if (hasMainRunIsolation) {
                (currentSession.distanceMeters - (currentSession.mainRunDistanceMeters ?: currentSession.distanceMeters)).coerceAtLeast(0)
            } else 0
            val newTotalDistanceMeters = if (hasMainRunIsolation) {
                newEffectiveDistMeters + warmupExtraMeters
            } else {
                newEffectiveDistMeters
            }

            val newAvgPaceSecPerKm = ((effectiveDurationSec * 1000.0) / newEffectiveDistMeters.toDouble()).toInt().coerceIn(140, 1200)
            val cleanAvgHr = avgHrBpm?.takeIf { it in 45..230 } ?: currentSession.avgHr
            val cleanMaxHr = maxHrBpm?.takeIf { it in 50..240 }?.let { maxOf(it, cleanAvgHr ?: 0) } ?: currentSession.maxHr

            // Recalcula Fator de Eficiência (EF = velocidade em m/min ÷ FC média)
            val speedMetersPerMin = newEffectiveDistMeters.toDouble() / (effectiveDurationSec.toDouble() / 60.0)
            val recalculatedEf = if (cleanAvgHr != null && cleanAvgHr > 0) {
                kotlin.math.round((speedMetersPerMin / cleanAvgHr.toDouble()) * 100.0) / 100.0
            } else {
                currentSession.advancedMetrics?.efficiencyFactorMeterPerBeat
            }

            val resolvedSteps = totalSteps?.takeIf { it > 0 }
                ?: if (avgCadenceSpm != null && avgCadenceSpm > 0) {
                    kotlin.math.round(avgCadenceSpm * (effectiveDurationSec / 60f)).toInt()
                } else null

            val resolvedCadence = avgCadenceSpm?.takeIf { it in 80..230 }
                ?: if (resolvedSteps != null && effectiveDurationSec > 0) {
                    kotlin.math.round(resolvedSteps / (effectiveDurationSec / 60f)).toInt()
                } else null

            val resolvedStrideCm = avgStrideCm?.takeIf { it in 40..220 }
                ?: if (resolvedSteps != null && resolvedSteps > 0) {
                    kotlin.math.round((newEffectiveDistMeters * 100.0) / resolvedSteps.toDouble()).toInt()
                } else null

            val existingAdv = currentSession.advancedMetrics ?: com.example.data.model.AdvancedRunningMetrics()
            val updatedAdv = existingAdv.copy(
                efficiencyFactorMeterPerBeat = recalculatedEf,
                watchSyncedSteps = resolvedSteps ?: existingAdv.watchSyncedSteps,
                watchSyncedCadenceSpm = resolvedCadence ?: existingAdv.watchSyncedCadenceSpm,
                watchSyncedStrideCm = resolvedStrideCm ?: existingAdv.watchSyncedStrideCm,
                watchSyncedActiveKcal = activeCaloriesKcal?.takeIf { it > 0 } ?: existingAdv.watchSyncedActiveKcal,
                watchSyncedTotalKcal = totalCaloriesKcal?.takeIf { it > 0 } ?: existingAdv.watchSyncedTotalKcal
            )

            val updatedProvenance = if (cleanAvgHr != null && cleanAvgHr > 0) {
                com.example.data.model.MetricProvenance(
                    source = com.example.data.model.DataSourceType.OPTICAL_PPG_WATCH,
                    deviceName = "Smartwatch (Mi Fitness)",
                    confidence = com.example.data.model.DataConfidenceLevel.MODERATE
                )
            } else {
                currentSession.hrProvenance
            }

            val updatedSession = currentSession.copy(
                distanceMeters = newTotalDistanceMeters,
                mainRunDistanceMeters = if (hasMainRunIsolation) newEffectiveDistMeters else currentSession.mainRunDistanceMeters,
                avgPaceSecPerKm = newAvgPaceSecPerKm,
                mainRunAvgPaceSecPerKm = if (hasMainRunIsolation) newAvgPaceSecPerKm else currentSession.mainRunAvgPaceSecPerKm,
                avgHr = cleanAvgHr,
                maxHr = cleanMaxHr,
                hrProvenance = updatedProvenance,
                advancedMetrics = updatedAdv
            )

            repository.saveRunningSession(updatedSession)
            if (selectedDetailSession.value?.id == sessionId) {
                selectedDetailSession.value = updatedSession
            }
            if (selectedShareSession.value?.id == sessionId) {
                selectedShareSession.value = updatedSession
            }

            val updatedList = runningSessions.value.map { if (it.id == sessionId) updatedSession else it }
            recalculateAndPersistShoeMileage(updatedList)
        }
    }

    fun saveGymSessionAiReport(
        session: com.example.data.model.GymWorkoutSession,
        reportText: String,
        adjustmentsJson: String
    ) {
        viewModelScope.launch {
            val updated = session.copy(
                aiCoachReport = reportText,
                aiLoadAdjustmentsJson = adjustmentsJson
            )
            com.example.data.repository.GymHistoryRepository.saveSession(getApplication(), updated)
        }
    }

    fun applyAiLoadAdjustmentsToNextWorkout(
        workoutName: String,
        adjustments: List<com.example.services.ai.GymLoadAdjustment>
    ) {
        if (adjustments.isEmpty()) return
        viewModelScope.launch {
            adjustments.forEach { adj ->
                com.example.data.repository.ExerciseHistoryRepository.setAiLoadOverride(
                    exerciseName = adj.exercicio,
                    weightKg = adj.novaCargaSugeridaKg,
                    targetReps = adj.metaReps
                )
            }
            com.example.data.repository.ExerciseHistoryRepository.clearCache()

            // Atualiza também as fichas salvas no banco (Workout) que possuam esses exercícios
            val currentWorkouts = workouts.value
            for (workout in currentWorkouts) {
                val parsedExs = try {
                    JsonUtils.parseWorkoutExercises(workout.exercisesJson)
                } catch (_: Exception) {
                    emptyList()
                }
                if (parsedExs.isEmpty()) continue

                var modified = false
                val updatedExs = parsedExs.map { ex ->
                    val matchAdj = adjustments.find { adj ->
                        adj.exercicio.trim().equals(ex.name.trim(), ignoreCase = true) ||
                            ex.name.contains(adj.exercicio, ignoreCase = true) ||
                            adj.exercicio.contains(ex.name, ignoreCase = true)
                    }
                    if (matchAdj != null && matchAdj.novaCargaSugeridaKg > 0f) {
                        modified = true
                        val updatedSets = ex.sets.map { s ->
                            s.copy(
                                weight = matchAdj.novaCargaSugeridaKg,
                                reps = if (matchAdj.metaReps > 0) matchAdj.metaReps else s.reps
                            )
                        }
                        ex.copy(sets = updatedSets)
                    } else {
                        ex
                    }
                }
                if (modified) {
                    val updatedWorkout = workout.copy(
                        exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExs)
                    )
                    repository.createWorkout(updatedWorkout)
                }
            }
            homeRecommendationVersion.value++
        }
    }
    val pendingSyncActions = repository.pendingSyncActionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val betaEvaluations = repository.betaEvaluationsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- GYM PROFILE CONTEXT (Sprint UX-006) ---
    val gymProfiles = db.gymProfileDao().getAllGymProfiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    val activeGymProfile = MutableStateFlow<GymProfile?>(null)

    // --- UI STATES & OTHER STATEFLOWS ---
    val sessionPrepStatus = MutableStateFlow(com.example.services.telemetry.SessionPrepStepState())
    val activeScreen = MutableStateFlow(AppScreen.HOME)
    val previousScreenStack = MutableStateFlow<List<AppScreen>>(emptyList())

    fun navigateToScreen(target: AppScreen) {
        val current = activeScreen.value
        if (current != target) {
            val updatedStack = previousScreenStack.value + current
            previousScreenStack.value = updatedStack.takeLast(10)
            activeScreen.value = target
        }
    }

    fun popBackScreen(fallback: AppScreen = AppScreen.HOME) {
        val stack = previousScreenStack.value
        if (stack.isNotEmpty()) {
            val last = stack.last()
            previousScreenStack.value = stack.dropLast(1)
            activeScreen.value = last
        } else {
            activeScreen.value = fallback
        }
    }
    val lastCompletedWorkoutSummary = MutableStateFlow<CompletedWorkoutSummary?>(null)
    val workoutFlowState = MutableStateFlow<WorkoutFlowState>(WorkoutFlowState.Idle)
    val gymWorkoutSessions: StateFlow<List<com.example.data.model.GymWorkoutSession>> = combine(
        com.example.data.repository.GymHistoryRepository.sessions,
        history
    ) { repoSessions, roomHistory ->
        fun cleanGymTitle(raw: String): String =
            com.example.services.ai.AiPhysiologistAnalyzer.normalizeGymWorkoutName(raw)

        val merged = repoSessions.map { s ->
            val cleaned = cleanGymTitle(s.workoutName)
            if (cleaned != s.workoutName) s.copy(workoutName = cleaned) else s
        }.toMutableList()

        for (entry in roomHistory) {
            val cleanedEntryName = cleanGymTitle(entry.workoutName)
            val alreadyExists = merged.any { s ->
                val cleanedExistingName = cleanGymTitle(s.workoutName)
                s.id == entry.id ||
                    (cleanedExistingName.equals(cleanedEntryName, ignoreCase = true) &&
                        kotlin.math.abs(s.date - entry.date) < 6 * 3600_000L)
            }
            if (!alreadyExists) {
                val parsedExs = try {
                    JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                } catch (_: Exception) {
                    emptyList()
                }
                val actualCompletedSets = parsedExs.sumOf { ex -> ex.sets.count { it.completed } }
                val totalPlannedSets = parsedExs.sumOf { it.sets.size }.coerceAtLeast(actualCompletedSets.coerceAtLeast(entry.exercisesCompletedCount))
                merged.add(
                    com.example.data.model.GymWorkoutSession(
                        id = entry.id,
                        date = entry.date,
                        workoutName = cleanedEntryName,
                        durationSeconds = entry.durationSeconds.toInt(),
                        totalVolumeKg = entry.totalWeight,
                        totalSets = totalPlannedSets,
                        completedSets = if (actualCompletedSets > 0) actualCompletedSets else entry.exercisesCompletedCount.coerceAtLeast(1),
                        exercises = parsedExs.map { ex ->
                            com.example.data.model.GymExerciseRecord(
                                name = ex.name,
                                equipment = ex.equipment,
                                category = ex.category,
                                sets = ex.sets.mapIndexed { sIdx, s ->
                                    com.example.data.model.GymSetRecord(
                                        setIndex = sIdx + 1,
                                        weight = s.weight,
                                        reps = s.reps,
                                        completed = s.completed
                                    )
                                },
                                notes = ex.notes
                            )
                        }
                    )
                )
            }
        }
        merged
            .sortedByDescending { it.date }
            .distinctBy { s ->
                "${cleanGymTitle(s.workoutName).lowercase(java.util.Locale.ROOT)}|${s.date / 3600_000L}"
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.repository.GymHistoryRepository.sessions.value)
    val showGymSavePrompt = MutableStateFlow(false)
    val showGymRegistrationFromWorkout = MutableStateFlow(false)
    val exercisesToSaveInGym = MutableStateFlow<List<String>>(emptyList())
    val firstLaunch = MutableStateFlow(sharedPrefs.getBoolean("first_launch", false))
    val workoutsSubTab = MutableStateFlow(0) // 0 = Treinos, 1 = Cardio, 2 = Biblioteca
    val historyTab = MutableStateFlow(0) // 0 = Corridas, 1 = Musculação

    val appMode = MutableStateFlow<com.example.data.model.AppMode>(
        try {
            com.example.data.model.AppMode.valueOf(
                sharedPrefs.getString("app_mode", com.example.data.model.AppMode.BODYBUILDING.name) ?: com.example.data.model.AppMode.BODYBUILDING.name
            )
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.init.appMode")
            com.example.data.model.AppMode.BODYBUILDING
        }
    )
    val isRunningHudActive = MutableStateFlow(false)

    // --- USER MANAGEMENT & ACCESS CONTROL ---
    val currentUser = com.example.services.UserManager.currentUser
    val allUsers = db.accessControlDao().getAllUsersFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRequests = db.accessControlDao().getAllRequestsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs = db.accessControlDao().getAllAuditLogsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- DERIVED RECOVERY & TROPHIES ---
    val recoveryList: StateFlow<List<MuscleRecovery>> = history.map { hist ->
        RecoveryAndTrophies.calculateRecovery(hist)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trophiesList: StateFlow<List<Trophy>> = combine(history, cardioSessions, workouts) { hist, card, wrk ->
        val customCount = wrk.filter { it.templateId == null }.size
        RecoveryAndTrophies.evaluateTrophies(hist, card, emptyList(), customCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- ACTIVE WORKOUT PLAYER STATE & PREFERRED PROTOCOL PERSISTENCE ---
    val homeRecommendationVersion = MutableStateFlow(0)
    private val _preferredWorkoutId = MutableStateFlow<String?>(sharedPrefs.getString("preferred_workout_id", null))
    val preferredWorkoutId = _preferredWorkoutId.asStateFlow()

    val preferredProtocolId = MutableStateFlow<String?>(sharedPrefs.getString("preferred_protocol_id", "FULL_BODY") ?: "FULL_BODY")
    val isHybridRunningEnabled = MutableStateFlow<Boolean>(sharedPrefs.getBoolean("preferred_hybrid_running", true))

    // --- BUILD #48 (v4.8) — MOTOR DE INTELIGÊNCIA HÍBRIDA (FADIGA CONCORRENTE & SUPERCOMPENSAÇÃO) ---
    val fatigueDecisionState = MutableStateFlow(com.example.services.recommendation.FatigueAdvisoryDecision.PENDING)
    val isFatigueSimulationEnabled = MutableStateFlow(false)
    private val hybridRecommendationEngine = com.example.services.recommendation.RecommendationEngine()

    val fatigueAdvisoryState: StateFlow<com.example.services.recommendation.FatigueAdvisoryState> = combine(
        history,
        runningSessions,
        cardioSessions,
        fatigueDecisionState,
        isFatigueSimulationEnabled
    ) { gymHist, runHist, cardioHist, decision, simEnabled ->
        val isHybridActive = isHybridRunningEnabled.value || preferredProtocolId.value == "FULL_BODY"
        hybridRecommendationEngine.evaluateConcurrentFatigue(
            gymHistory = gymHist,
            runningHistory = runHist,
            cardioHistory = cardioHist,
            decision = decision,
            simulateThreeConsecutiveDays = simEnabled,
            hybridScheduleHasThreeConsecutiveDays = isHybridActive && simEnabled
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        hybridRecommendationEngine.evaluateConcurrentFatigue(
            gymHistory = emptyList(),
            runningHistory = emptyList(),
            cardioHistory = emptyList(),
            decision = com.example.services.recommendation.FatigueAdvisoryDecision.PENDING,
            simulateThreeConsecutiveDays = false,
            hybridScheduleHasThreeConsecutiveDays = false
        )
    )

    val activeWorkout = MutableStateFlow<Workout?>(null)
    val currentSessionId = MutableStateFlow<String?>(null)
    val activeSessionsList = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())
    val activeSessionState: StateFlow<WorkoutSessionEntity?> = repository.activeWorkoutSessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hasActiveSession: StateFlow<Boolean> = repository.activeWorkoutSessionFlow
        .map { it != null && it.status == WorkoutSessionStatus.ATIVO.rawValue }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sessionArchivedNotification = MutableStateFlow<String?>(null)
    val showActiveSessionPrompt = MutableStateFlow(false)
    val isMultipleActiveSessionsConflict = MutableStateFlow(false)
    val sessionToDiscardConfirm = MutableStateFlow<WorkoutSessionEntity?>(null)
    val showNoSetsDialog = MutableStateFlow(false)
    private val sessionUpdateMutex = kotlinx.coroutines.sync.Mutex()
    val isPreSessionActive = MutableStateFlow(false)
    val workoutWarningState = MutableStateFlow<WorkoutWarningData?>(null)
    val workoutErrorMessage = MutableStateFlow<String?>(null)
    val fastSetWarningMessage = MutableStateFlow<String?>(null)
    private val lastSetCompletionTimes = mutableMapOf<String, Long>()
    val activeExercises = MutableStateFlow<List<WorkoutExercise>>(emptyList())
    val exerciseProgressionSuggestions = MutableStateFlow<Map<String, com.example.data.repository.LoadProgressionSuggestion>>(emptyMap())
    val isRecalculatingSession = MutableStateFlow(false)
    val currentExerciseIndex = MutableStateFlow(0)
    val workoutDurationSeconds = com.example.services.WorkoutServiceState.workoutDurationSeconds

    // Rest Timer
    val isRestTimerActive = com.example.services.WorkoutServiceState.isRestTimerActive
    val restTimeTotal = com.example.services.WorkoutServiceState.restTimeTotal
    val restTimeRemaining = com.example.services.WorkoutServiceState.restTimeRemaining

    // TEMPO Timer
    val isTempoTimerActive = com.example.services.WorkoutServiceState.isTempoTimerActive
    val tempoTimeRemaining = com.example.services.WorkoutServiceState.tempoTimeRemaining
    val tempoTimeTotal = com.example.services.WorkoutServiceState.tempoTimeTotal
    val selectedTrainingMethod = MutableStateFlow<com.example.data.model.TrainingMethod>(com.example.data.model.TrainingMethod.LinearMethod)

    // Default Rest Seconds for active session
    val sessionDefaultRestSeconds: MutableStateFlow<Int> = MutableStateFlow(60)

    // Rest Sound Preferences
    val restSoundOption = MutableStateFlow(sharedPrefs.getString("rest_sound_option", "default") ?: "default")
    val restSoundEnabled = MutableStateFlow(sharedPrefs.getBoolean("rest_sound_enabled", true))
    val alertVolume = MutableStateFlow(sharedPrefs.getFloat("audio_alert_volume", 0.85f)).also {
        AudioSynth.setVolumeFactor(it.value)
    }

    val recommendationDecision: StateFlow<com.example.services.recommendation.DecisionResult> = combine(
        userProfile,
        history,
        activeGymProfile
    ) { prof, hist, gym ->
        if (prof.nome.isBlank()) {
            return@combine com.example.services.recommendation.DecisionResult(
                decision = "INITIAL_STATE",
                title = "INICIALIZANDO RECOMENDAÇÃO",
                description = "Carregando dados fisiológicos para formular a melhor decisão estratégica...",
                details = emptyList(),
                nextAction = "Por favor, aguarde.",
                suggestedSplit = "Carregando...",
                suggestedVolume = "Carregando...",
                suggestedIntensity = "Carregando..."
            )
        }
        val engine = com.example.services.recommendation.RecommendationEngine()

        // Base defaults (PADRAO)
        val exp = prof.nivelExperiencia.ifBlank { "Iniciante" }
        val dur = 60
        val obj = if (exp == "Iniciante") "Adaptação" else "Hipertrofia"
        val fatigue = if (hist.size > 8) 5.5 else 2.0
        val rec = if (hist.isNotEmpty()) 8.0 else 10.0
        val targetWeekly = prof.metaSemanal.coerceAtLeast(1)

        val context = com.example.engine.WorkoutContext(
            durationMinutes = dur,
            objective = obj,
            gymProfile = gym,
            technique = "Linear",
            userPreferences = com.example.engine.UserPreferences(
                level = exp,
                preferredFocus = obj
            ),
            experience = exp,
            availableTime = dur,
            fatigueLevel = fatigue,
            recoveryProfile = rec,
            injuredMuscles = emptyList(),
            constraints = com.example.engine.WorkoutConstraints(
                busyGym = false,
                timeLimitMinutes = dur
            )
        )
        engine.makeDecision(context, hist, targetWeekly)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.example.services.recommendation.DecisionResult(
            decision = "INITIAL_STATE",
            title = "INICIALIZANDO RECOMENDAÇÃO",
            description = "Carregando dados fisiológicos para formular a melhor decisão estratégica...",
            details = emptyList(),
            nextAction = "Por favor, aguarde.",
            suggestedSplit = "Carregando...",
            suggestedVolume = "Carregando...",
            suggestedIntensity = "Carregando..."
        )
    )

    // Admin Panel Selected Tab (Hoisted for Deep Links)
    val adminSelectedTab = MutableStateFlow(0)

    // Sync progress indicators
    val isSyncing = MutableStateFlow(false)
    val syncStatusMessage = MutableStateFlow<String?>(null)

    // AI Coach Interaction States
    val aiCoachResponse = MutableStateFlow<String?>(null)
    val isAiLoading = MutableStateFlow(false)
    val aiQueriesToday = MutableStateFlow(0)

    // Gym Profile (MVP) - Machine Default Weights
    private val _gymConfigs = MutableStateFlow<Map<String, com.example.data.model.GymEquipmentConfig>>(emptyMap())
    val gymConfigs = _gymConfigs.asStateFlow()

    private val _promptedExercises = MutableStateFlow<Set<String>>(emptySet())
    val activeGymPrompt = MutableStateFlow<Pair<String, Float>?>(null) // Pair(exerciseName, weight)

    // PR Celebration and RPE States
    val activePrCelebration = MutableStateFlow<PrCelebration?>(null)
    val activeRpePrompt = MutableStateFlow<RpePrompt?>(null)
    val prSetIds = MutableStateFlow<Set<String>>(emptySet())

    // Active Classroom Chat
    val activeClassroom = MutableStateFlow<Classroom?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val classroomChatMessages = activeClassroom.flatMapLatest { cls ->
        if (cls != null) repository.getChatMessagesFlow(cls.id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Nível de Experiência e Sugestão
    val showLevelUpSuggestion = MutableStateFlow<com.example.utils.LevelSuggestion?>(null)
    val forceDiagnosticMessage = MutableStateFlow<String?>(null)

    private var lastSetCompletionTimestamp: Long = 0L
    val lastSetCompletionTimeMs = MutableStateFlow(0L)
    val favoriteExercisesIds = MutableStateFlow<Set<String>>(emptySet())

    private var tickerJob: Job? = null

    fun setAppMode(mode: com.example.data.model.AppMode) {
        if (appMode.value == mode) {
            activeScreen.value = AppScreen.HOME
            return
        }
        appMode.value = mode
        sharedPrefs.edit().putString("app_mode", mode.name).apply()
        activeScreen.value = AppScreen.HOME
    }

    private fun initializeReactiveObservers() {
        viewModelScope.launch {
            combine(history, activeGymProfile, repository.rawUserProfileFlow) { _, _, profile ->
                com.example.utils.WeightCalculator.activeUserProfile = profile?.takeIf { it.nome.isNotBlank() }
            }.collect {
                com.example.engine.WorkoutEngine.clearCache()
            }
        }
        viewModelScope.launch {
            runningSessions.collect { sessions ->
                recalculateAndPersistShoeMileage(sessions)
            }
        }
    }

    fun saveBetaEvaluation(evaluation: BetaEvaluationEntity) {
        viewModelScope.launch {
            repository.saveBetaEvaluation(evaluation)
        }
    }

    fun deleteBetaEvaluation(id: String) {
        viewModelScope.launch {
            repository.deleteBetaEvaluation(id)
        }
    }

    fun saveRunnerBaselinePace(baselinePaceMinPerKm: Float, updateTimestamp: Boolean = true) {
        viewModelScope.launch {
            repository.updateRunnerBaselinePace(baselinePaceMinPerKm)
            if (updateTimestamp) {
                val now = System.currentTimeMillis()
                lastPaceCalibrationTimestamp.value = now
                sharedPrefs.edit().putLong("last_pace_calibration_timestamp", now).apply()
            }
        }
    }

    // --- RUNNING SHOES ROTATION & SINGLE SOURCE OF TRUTH ---
    fun shoeMatches(shoe: com.example.data.model.RunningShoe, sessionShoeName: String?): Boolean {
        if (sessionShoeName.isNullOrBlank()) return false
        val trimmed = sessionShoeName.trim()
        if (shoe.id.equals(trimmed, ignoreCase = true)) return true
        if (shoe.name.equals(trimmed, ignoreCase = true)) return true
        val fullBrandAndName = "${shoe.brand} ${shoe.name}".trim()
        if (fullBrandAndName.equals(trimmed, ignoreCase = true)) return true
        if (trimmed.equals("${shoe.name} ${shoe.brand}".trim(), ignoreCase = true)) return true
        if (shoe.name.length >= 3 && (trimmed.contains(shoe.name, ignoreCase = true) || shoe.name.contains(trimmed, ignoreCase = true))) {
            return true
        }
        return false
    }

    fun recalculateAndPersistShoeMileage(sessionsList: List<RunningSession>? = null) {
        val currentShoes = _runningShoes.value
        val sessions = sessionsList ?: runningSessions.value
        if (currentShoes.isEmpty()) return
        val updatedList = currentShoes.map { shoe ->
            val linkedMeters = sessions
                .filter { s -> shoeMatches(shoe, s.shoeName) }
                .sumOf { it.distanceMeters.toLong() }
            val linkedKm = (linkedMeters / 1000.0).toFloat()
            val baseInitial = shoe.initialDistanceKm.coerceAtLeast(0f)
            val calculatedTotal = baseInitial + linkedKm
            val lifespan = if (shoe.maxLifespanKm > 0f && shoe.maxLifespanKm != 600f) shoe.maxLifespanKm else shoe.maxDistanceKm
            shoe.copy(
                initialDistanceKm = baseInitial,
                totalDistanceKm = calculatedTotal,
                maxDistanceKm = lifespan,
                maxLifespanKm = lifespan
            )
        }
        _runningShoes.value = updatedList
        savePersistedRunningShoes(updatedList)
    }

    fun addRunningShoe(shoe: com.example.data.model.RunningShoe) {
        val isFirstShoe = _runningShoes.value.isEmpty()
        val shoeToAdd = if (isFirstShoe) shoe.copy(isDefault = true) else shoe
        val newList = if (shoeToAdd.isDefault) {
            _runningShoes.value.map { it.copy(isDefault = false) } + shoeToAdd
        } else {
            _runningShoes.value + shoeToAdd
        }
        _runningShoes.value = newList
        recalculateAndPersistShoeMileage()
    }

    fun updateRunningShoe(updatedShoe: com.example.data.model.RunningShoe) {
        val newList = _runningShoes.value.map { shoe ->
            if (shoe.id == updatedShoe.id) updatedShoe
            else if (updatedShoe.isDefault) shoe.copy(isDefault = false)
            else shoe
        }
        _runningShoes.value = newList
        recalculateAndPersistShoeMileage()
    }

    fun setDefaultRunningShoe(shoeId: String) {
        val newList = _runningShoes.value.map { shoe ->
            shoe.copy(isDefault = (shoe.id == shoeId))
        }
        _runningShoes.value = newList
        savePersistedRunningShoes(newList)
    }

    fun retireRunningShoe(shoeId: String) {
        val newList = _runningShoes.value.map { shoe ->
            if (shoe.id == shoeId) shoe.copy(isRetired = true, isDefault = false) else shoe
        }
        _runningShoes.value = newList
        savePersistedRunningShoes(newList)
    }

    fun deleteRunningShoe(shoeId: String) {
        val newList = _runningShoes.value.filter { it.id != shoeId }
        _runningShoes.value = newList
        savePersistedRunningShoes(newList)
    }

    fun adjustShoeDistance(shoeName: String, deltaKm: Float) {
        if (deltaKm == 0f || shoeName.isBlank()) return
        val newList = _runningShoes.value.map { shoe ->
            if (shoeMatches(shoe, shoeName)) {
                val newInitial = (shoe.initialDistanceKm + deltaKm).coerceAtLeast(0f)
                shoe.copy(initialDistanceKm = newInitial)
            } else {
                shoe
            }
        }
        _runningShoes.value = newList
        recalculateAndPersistShoeMileage()
    }

    fun addDistanceToShoe(shoeName: String, distanceKm: Float) {
        adjustShoeDistance(shoeName, distanceKm)
    }

    fun saveRunningSession(session: RunningSession) {
        viewModelScope.launch {
            val alreadyExisted = runningSessions.value.any { it.id == session.id }
            repository.saveRunningSession(session)
            if (selectedDetailSession.value?.id == session.id) {
                selectedDetailSession.value = session
            }
            if (selectedShareSession.value?.id == session.id) {
                selectedShareSession.value = session
            }
            if (!session.routePointsJson.isNullOrBlank()) {
                try {
                    val trackPoints = session.routePointsJson.toGpsTrackPoints(session.id)
                    if (trackPoints.isNotEmpty()) {
                        db.gpsTrackPointDao().insertAll(trackPoints)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error saving track points to Room", e)
                }
            }
            val currentList = runningSessions.value.filter { it.id != session.id } + session
            recalculateAndPersistShoeMileage(currentList)

            // Avança para o próximo dia da planilha EXCLUSIVAMENTE ao salvar o treino confirmado pela primeira vez
            val isPlanSession = !alreadyExisted &&
                session.weekNumber in 1..10 &&
                !session.isGentleMode &&
                session.sessionType != "GENTLE_WALK" &&
                !com.example.engine.RunningProgramManager.isFreeOrRaceSessionType(session.sessionType) &&
                !com.example.engine.RunningProgramManager.isEffectiveFreeRun(session)
            if (isPlanSession) {
                val w = session.weekNumber.coerceIn(1, 10)
                val d = session.dayNumber.coerceIn(1, 3)
                val (nextW, nextD) = when {
                    d == 1 -> Pair(w, 2)
                    d == 2 -> Pair(w, 3)
                    else -> if (w < 10) Pair(w + 1, 1) else Pair(10, 3)
                }
                setManualPlanDayOverride(nextW, nextD)
            }
        }
    }

    fun acceptFatigueRestDay() {
        fatigueDecisionState.value = com.example.services.recommendation.FatigueAdvisoryDecision.ACCEPTED_REST
        homeRecommendationVersion.value++
    }

    fun overrideFatigueAndTrain() {
        fatigueDecisionState.value = com.example.services.recommendation.FatigueAdvisoryDecision.OVERRIDDEN_TRAIN
        homeRecommendationVersion.value++
    }

    fun resetOrSimulateFatigueAdvisory(enableSimulation: Boolean = false) {
        isFatigueSimulationEnabled.value = false
        fatigueDecisionState.value = com.example.services.recommendation.FatigueAdvisoryDecision.PENDING
        homeRecommendationVersion.value++
    }

    fun getMobilityRecoveryWorkout(): com.example.data.model.Workout {
        return hybridRecommendationEngine.createMobilityRecoveryWorkout()
    }

    fun setPreferredProtocol(protocolId: String?, hybridWithRunning: Boolean = true) {
        _preferredWorkoutId.value = null
        preferredProtocolId.value = protocolId
        isHybridRunningEnabled.value = hybridWithRunning
        sharedPrefs.edit()
            .remove("preferred_workout_id")
            .putString("preferred_protocol_id", protocolId)
            .putBoolean("preferred_hybrid_running", hybridWithRunning)
            .apply()
        homeRecommendationVersion.value++
    }

    fun setPreferredWorkout(workoutId: String?) {
        preferredProtocolId.value = null
        _preferredWorkoutId.value = workoutId
        sharedPrefs.edit()
            .remove("preferred_protocol_id")
            .putString("preferred_workout_id", workoutId)
            .apply()
        homeRecommendationVersion.value++
    }

    fun clearPreferredWorkoutAndProtocol() {
        _preferredWorkoutId.value = null
        preferredProtocolId.value = null
        sharedPrefs.edit()
            .remove("preferred_workout_id")
            .remove("preferred_protocol_id")
            .apply()
        homeRecommendationVersion.value++
    }

    fun clearSessionArchivedNotification() {
        sessionArchivedNotification.value = null
    }

    fun setSessionDefaultRestSeconds(seconds: Int) {
        val sanitized = seconds.coerceIn(5, 600)
        sessionDefaultRestSeconds.value = sanitized
        val currentList = activeExercises.value
        val updatedList = currentList.map { ex ->
            ex.copy(restTimeSeconds = sanitized)
        }
        activeExercises.value = updatedList
        saveActiveSession()  // persistir
    }

    fun addExerciseToActiveWorkout(exercise: com.example.data.model.Exercise) {
        val isTempo = exercise.category.equals("Cardio", ignoreCase = true) || 
                      exercise.name.contains("Prancha", ignoreCase = true) || 
                      exercise.name.contains("Corrida", ignoreCase = true) || 
                      exercise.name.contains("Bicicleta", ignoreCase = true)
        
        val executionTypeStr = if (isTempo) "TEMPO" else "REPS"
        val defaultReps = if (isTempo) 30 else 10

        val progressionDetails = com.example.data.repository.ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history.value,
            exerciseNameOrId = exercise.id.ifEmpty { exercise.name },
            equipment = exercise.equipment,
            gymProfile = activeGymProfile.value,
            progressionType = "Linear",
            targetReps = defaultReps,
            userProfile = userProfile.value
        )
        val suggestedWeight = progressionDetails.suggestedWeightKg
        if (!isTempo && progressionDetails.progressionApplied && progressionDetails.deltaKg > 0f) {
            val key = exercise.name.trim().lowercase()
            if (key.isNotEmpty()) {
                exerciseProgressionSuggestions.value = exerciseProgressionSuggestions.value + (key to progressionDetails)
            }
        }
        
        val exerciseIdInWorkout = "ex_${java.util.UUID.randomUUID()}"
        val setsList = (1..3).map { index ->
            com.example.data.model.WorkoutSet(
                id = "set_${exerciseIdInWorkout}_$index",
                index = index,
                reps = defaultReps,
                weight = suggestedWeight,
                baseWeight = suggestedWeight,
                completed = false
            )
        }
        
        val newWorkoutExercise = com.example.data.model.WorkoutExercise(
            id = exerciseIdInWorkout,
            exerciseId = exercise.id,
            name = exercise.name,
            category = exercise.category,
            sets = setsList,
            restTimeSeconds = sessionDefaultRestSeconds.value,
            progressionType = "Linear",
            executionType = executionTypeStr
        )
        
        val currentList = activeExercises.value.toMutableList()
        currentList.add(newWorkoutExercise)
        activeExercises.value = currentList
        
        saveActiveSession()
    }

    private fun syncSoundPrefsWithService() {
        if (com.example.services.WorkoutForegroundService.isServiceRunning) {
            try {
                val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
                    action = com.example.services.WorkoutForegroundService.ACTION_UPDATE_SOUND_PREFS
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_SOUND_OPTION, restSoundOption.value)
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_SOUND_ENABLED, restSoundEnabled.value)
                }
                sendServiceCommand(intent)
            } catch (_: Exception) {}
        }
    }

    fun setRestSoundOption(option: String) {
        restSoundOption.value = option
        sharedPrefs.edit().putString("rest_sound_option", option).apply()
        syncSoundPrefsWithService()
        if (restSoundEnabled.value) {
            com.example.utils.GymAudioEngine.previewRestSound(getApplication(), option)
        }
    }

    fun setRestSoundEnabled(enabled: Boolean) {
        restSoundEnabled.value = enabled
        sharedPrefs.edit().putBoolean("rest_sound_enabled", enabled).apply()
        syncSoundPrefsWithService()
        if (!enabled) {
            com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        }
    }

    fun setAlertVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        alertVolume.value = clamped
        AudioSynth.setVolumeFactor(clamped)
        sharedPrefs.edit().putFloat("audio_alert_volume", clamped).apply()
    }

    // Controle de Zoom de Fonte da Musculação (A- / 100% / A+)
    val gymFontScale = MutableStateFlow(
        sharedPrefs.getFloat("gym_font_scale", 1.0f).coerceIn(0.85f, 1.40f)
    )

    fun adjustGymFontScale(delta: Float) {
        val raw = (gymFontScale.value + delta).coerceIn(0.85f, 1.40f)
        val rounded = (kotlin.math.round(raw * 20f) / 20f).coerceIn(0.85f, 1.40f)
        gymFontScale.value = rounded
        sharedPrefs.edit().putFloat("gym_font_scale", rounded).apply()
    }

    fun resetGymFontScale() {
        gymFontScale.value = 1.0f
        sharedPrefs.edit().putFloat("gym_font_scale", 1.0f).apply()
    }

    fun isGymWorkoutActivelyRunning(): Boolean {
        return activeScreen.value == AppScreen.PLAYER &&
            activeWorkout.value != null &&
            workoutFlowState.value == WorkoutFlowState.Running &&
            !isPreSessionActive.value
    }

    fun finalizeActiveWorkoutAndNavigateToCompleted() {
        tickerJob?.cancel()
        isRestTimerActive.value = false
        restTimeRemaining.value = 0
        isTempoTimerActive.value = false
        tempoTimeRemaining.value = 0
        try {
            com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to stop sounds in finalizeActiveWorkoutAndNavigateToCompleted", e)
        }
        com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())
        activeWorkout.value = null
        activeExercises.value = emptyList()
        exerciseProgressionSuggestions.value = emptyMap()
        workoutFlowState.value = WorkoutFlowState.Finished
        activeScreen.value = AppScreen.CONCLUIDO
    }

    fun selectGymProfile(profile: GymProfile?) {
        activeGymProfile.value = profile
        if (profile != null) {
            sharedPrefs.edit().putString("active_gym_profile_id", profile.id).apply()
        } else {
            sharedPrefs.edit().remove("active_gym_profile_id").apply()
        }
        com.example.engine.GymContextManager.saveActiveGym(getApplication(), profile)
    }

    fun saveGymProfileFromSession(name: String, lat: Double?, lng: Double?, usedExercisesIds: List<String>) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val profile = GymProfile(
                id = id,
                name = name,
                latitude = lat,
                longitude = lng,
                availableExercisesIds = usedExercisesIds,
                unavailableExercisesIds = emptyList()
            )
            db.gymProfileDao().insertGymProfile(profile)
            selectGymProfile(profile)
        }
    }

    fun deleteGymProfile(id: String) {
        viewModelScope.launch {
            db.gymProfileDao().deleteGymProfileById(id)
            if (activeGymProfile.value?.id == id) {
                val remaining = db.gymProfileDao().getAllGymProfiles().first().filter { it.id != id }
                selectGymProfile(remaining.firstOrNull())
            }
        }
    }

    fun updateGymProfile(profile: GymProfile) {
        viewModelScope.launch {
            db.gymProfileDao().insertGymProfile(profile)
            if (activeGymProfile.value?.id == profile.id || activeGymProfile.value == null) {
                selectGymProfile(profile)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkNearbyGymProfile(context: Context, onResult: (GymProfile?) -> Unit) {
        try {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                onResult(null)
                return
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_LOW_POWER,
                null
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    val nearby = gymProfiles.value.firstOrNull { profile ->
                        if (profile.latitude != null && profile.longitude != null) {
                            val results = FloatArray(1)
                            Location.distanceBetween(lat, lng, profile.latitude, profile.longitude, results)
                            results[0] < 500f // 500m radius
                        } else {
                            false
                        }
                    }
                    onResult(nearby)
                } else {
                    onResult(null)
                }
            }.addOnFailureListener {
                onResult(null)
            }
        } catch (e: Exception) {
            onResult(null)
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, onResult: (Double?, Double?) -> Unit) {
        try {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                onResult(null, null)
                return
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_LOW_POWER,
                null
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onResult(location.latitude, location.longitude)
                } else {
                    onResult(null, null)
                }
            }.addOnFailureListener {
                onResult(null, null)
            }
        } catch (e: Exception) {
            onResult(null, null)
        }
    }

    fun getNeighborhoodFromLocation(context: Context, latitude: Double, longitude: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                val neighborhood = address?.subLocality ?: address?.subAdminArea
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(neighborhood)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun setAdminSelectedTab(index: Int) {
        adminSelectedTab.value = index
    }

    fun loadGymConfigs() {
        try {
            val sharedPref = (getApplication() as android.content.Context).getSharedPreferences("gym_profile_prefs", Context.MODE_PRIVATE)
            val json = sharedPref.getString("gym_configs_json", "") ?: ""
            if (json.isNotEmpty()) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.GymEquipmentConfig::class.java)
                val adapter = JsonUtils.moshi.adapter<List<com.example.data.model.GymEquipmentConfig>>(type)
                val list = adapter.fromJson(json) ?: emptyList()
                _gymConfigs.value = list.associateBy { it.equipmentName }
            }
            val prompted = sharedPref.getStringSet("prompted_exercises", emptySet()) ?: emptySet()
            _promptedExercises.value = prompted.toSet()
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to load gym configs", e)
        }
    }

    fun saveGymConfig(exerciseName: String, load: Float) {
        try {
            val currentGym = userProfile.value.localTreino.ifBlank { "Minha Academia" }
            val newConfig = com.example.data.model.GymEquipmentConfig(
                gymName = currentGym,
                equipmentName = exerciseName,
                defaultLoad = load,
                initialLoad = load,
                increment = 2.5f,
                barWeight = 0f,
                notes = "Salvo automaticamente como padrão da academia: $currentGym"
            )
            val updatedMap = _gymConfigs.value.toMutableMap()
            updatedMap[exerciseName] = newConfig
            _gymConfigs.value = updatedMap

            // Serialize to SharedPreferences
            val sharedPref = (getApplication() as android.content.Context).getSharedPreferences("gym_profile_prefs", Context.MODE_PRIVATE)
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.GymEquipmentConfig::class.java)
            val adapter = JsonUtils.moshi.adapter<List<com.example.data.model.GymEquipmentConfig>>(type)
            val json = adapter.toJson(updatedMap.values.toList())
            sharedPref.edit().putString("gym_configs_json", json).apply()
            android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Gym config saved successfully for $exerciseName: $load kg")
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to save gym config", e)
        }
    }

    fun confirmGymConfig(exerciseName: String, load: Float) {
        saveGymConfig(exerciseName, load)
        dismissGymConfigPrompt(exerciseName)
    }

    fun dismissGymConfigPrompt(exerciseName: String) {
        try {
            val updatedSet = _promptedExercises.value.toMutableSet()
            updatedSet.add(exerciseName)
            _promptedExercises.value = updatedSet

            val sharedPref = (getApplication() as android.content.Context).getSharedPreferences("gym_profile_prefs", Context.MODE_PRIVATE)
            sharedPref.edit().putStringSet("prompted_exercises", updatedSet).apply()
            activeGymPrompt.value = null
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to dismiss gym config prompt", e)
        }
    }

    init {
        com.example.data.repository.GymHistoryRepository.init(application)
        loadGymConfigs()
        viewModelScope.launch {
            initializeReactiveObservers()
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                com.example.services.UserManager.init(app)
            } catch (e: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Erro ao inicializar UserManager na abertura do app", e)
            }
            
            // Auto-reset when new build is detected (for clean testing as requested)
            val buildPrefs = app.getSharedPreferences("max_performance_build_prefs", Context.MODE_PRIVATE)
            val lastBuildId = buildPrefs.getString("last_compiled_build_id", "")
            val currentBuildId = "build_rev_2026_06_28_03_v1" // Increment whenever we change the APK
            if (lastBuildId != currentBuildId) {
                try {
                    repository.clearHistory()
                    buildPrefs.edit().putString("last_compiled_build_id", currentBuildId).apply()
                    android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Detected new APK build $currentBuildId! Resetting history and records for clean testing.")
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to auto-reset on new build", e)
                }
            }
            
            try {
                // Automatically initialize user "Usuário Teste" and prepopulate mock database
                repository.initDefaultTestUser()
            } catch (e: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Erro ao inicializar usuário de teste padrão", e)
            }
            
            try {
                // Read AI limit from SharedPreferences
                checkAiCreditsReset()
            } catch (e: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Erro ao checar créditos de IA", e)
            }

            // Rotina de manutenção em segundo plano para limpeza de sessões expiradas (>30 dias)
            // Não bloqueia nem atrasa a abertura do aplicativo nem a consulta de sessões ativas
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val purged = repository.purgeExpiredSessions(30)
                    if (purged > 0) {
                        android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Manutenção: $purged sessões expiradas removidas do Room.")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Falha na rotina de manutenção de sessões", e)
                }
            }

            // Consultar todas as sessões com status ATIVO no Room
            checkActiveSessions()

            // Set up active session auto-save after the restore operation has completed
            setupActiveSessionAutoSave()
        }

        // Restore active gym context persistently across sessions and builds
        viewModelScope.launch {
            gymProfiles.collect { profiles ->
                val app = getApplication<Application>()
                val isNoneSelected = com.example.engine.GymContextManager.isNoneSelectedExplicitly(app)
                if (!isNoneSelected) {
                    val savedGymId = sharedPrefs.getString("active_gym_profile_id", null)
                        ?: com.example.engine.GymContextManager.getSavedActiveGymId(app)
                    
                    if (savedGymId != null) {
                        val matched = profiles.firstOrNull { it.id == savedGymId }
                        if (matched != null) {
                            if (activeGymProfile.value?.id != matched.id) {
                                activeGymProfile.value = matched
                                com.example.engine.GymContextManager.saveActiveGym(app, matched)
                            }
                        } else {
                            val savedContext = com.example.engine.GymContextManager.loadSavedGymContext(app)
                            if (savedContext != null && activeGymProfile.value == null) {
                                activeGymProfile.value = savedContext.gymProfile
                            } else if (profiles.isNotEmpty() && activeGymProfile.value == null) {
                                selectGymProfile(profiles.first())
                            }
                        }
                    } else if (profiles.isNotEmpty() && activeGymProfile.value == null) {
                        selectGymProfile(profiles.first())
                    }
                }
            }
        }

        // Automatic synchronization when there are pending actions
        viewModelScope.launch {
            pendingSyncActions.collect { actions ->
                if (actions.isNotEmpty() && !isSyncing.value) {
                    isSyncing.value = true
                    val success = repository.syncPendingActions()
                    if (success) {
                        syncStatusMessage.value = "Sincronização automática concluída!"
                        delay(2000)
                        syncStatusMessage.value = null
                    }
                    isSyncing.value = false
                }
            }
        }

        // Automatic checking for level-up suggestions based on profile/history updates
        viewModelScope.launch {
            combine(repository.rawUserProfileFlow, repository.historyFlow) { p, h ->
                Pair(p, h)
            }.collect { (p, h) ->
                if (p != null && p.nome.isNotBlank() && h.isNotEmpty()) {
                    checkLevelSuggestion(p, h)
                }
            }
        }

        // Automatic complete of TEMPO exercise set when countdown finishes
        viewModelScope.launch {
            var lastRemaining = -1
            tempoTimeRemaining.collect { remaining ->
                if (remaining == 0 && lastRemaining > 0) {
                    val exIdx = currentExerciseIndex.value
                    val list = activeExercises.value
                    if (exIdx in list.indices) {
                        val ex = list[exIdx]
                        if (ex.executionType == "TEMPO") {
                            val uncompletedSetIndex = ex.sets.indexOfFirst { !it.completed }
                            if (uncompletedSetIndex != -1) {
                                toggleSetCompletion(exIdx, uncompletedSetIndex)
                            }
                        }
                    }
                }
                lastRemaining = remaining
            }
        }
    }

    fun checkLevelSuggestion(profile: UserProfileData, hist: List<WorkoutHistoryEntry>) {
        val suggestion = com.example.utils.LevelSuggester.shouldSuggestLevelUp(profile, hist)
        if (suggestion.suggest) {
            val declinedAt = sharedPrefs.getInt("level_suggestion_declined_at_workout_count", -999)
            if (declinedAt == -999 || hist.size - declinedAt >= 5) {
                showLevelUpSuggestion.value = suggestion
            } else {
                showLevelUpSuggestion.value = null
            }
        } else {
            showLevelUpSuggestion.value = null
        }
    }

    fun checkActiveSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeSessions = repository.getAllActiveWorkoutSessions()
                if (activeSessions.size > 1) {
                    // Edge Case Crítico: múltiplas sessões ativas encontradas.
                    // Não crashar: manter apenas a mais recente (maior startTimeMonotonic ou startTime).
                    val mostRecent = activeSessions.maxByOrNull { 
                        maxOf(it.startTimeMonotonic, it.startTime)
                    } ?: activeSessions.first()

                    val toArchive = activeSessions.filter { it.id != mostRecent.id }
                    val archiveTimestamp = System.currentTimeMillis()

                    android.util.Log.w(
                        "MAX_PERFORMANCE_DEBUG",
                        "⚠️ Concorrência detectada: ${activeSessions.size} sessões ATIVO encontradas. Mantendo a mais recente [${mostRecent.id} - ${mostRecent.workoutName}] e arquivando ${toArchive.size} sessões antigas como NAO_REALIZADO."
                    )

                    for (oldSession in toArchive) {
                        val dur = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
                            oldSession.startTimeMonotonic,
                            oldSession.startTime,
                            oldSession.monotonicStartTimeNanos
                        )
                        val success = repository.updateWorkoutSessionStatusVersioned(
                            sessionId = oldSession.id,
                            status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                            newVersion = oldSession.version + 1,
                            completedAt = archiveTimestamp,
                            durationSeconds = dur
                        )
                        android.util.Log.w(
                            "MAX_PERFORMANCE_DEBUG",
                            "Sessão anterior arquivada com status NAO_REALIZADO [id=${oldSession.id}, treino=${oldSession.workoutName}, sucesso=$success]"
                        )
                    }

                    val archivedNames = toArchive.joinToString(", ") { it.workoutName }
                    val notifyMessage = if (toArchive.size == 1) {
                        "Uma sessão anterior incompleta ($archivedNames) foi arquivada automaticamente."
                    } else {
                        "${toArchive.size} sessões anteriores incompletas foram arquivadas automaticamente."
                    }

                    withContext(Dispatchers.Main) {
                        activeSessionsList.value = listOf(mostRecent)
                        currentSessionId.value = mostRecent.id
                        isMultipleActiveSessionsConflict.value = false
                        showActiveSessionPrompt.value = true
                        sessionArchivedNotification.value = notifyMessage
                    }
                } else if (activeSessions.size == 1) {
                    val single = activeSessions.first()
                    withContext(Dispatchers.Main) {
                        activeSessionsList.value = activeSessions
                        currentSessionId.value = single.id
                        isMultipleActiveSessionsConflict.value = false
                        showActiveSessionPrompt.value = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        activeSessionsList.value = emptyList()
                        currentSessionId.value = null
                        isMultipleActiveSessionsConflict.value = false
                        showActiveSessionPrompt.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Erro ao checar sessões ativas no Room", e)
            }
        }
    }

    fun acceptLevelUpSuggestion(newLevel: String) {
        viewModelScope.launch {
            val updated = userProfile.value.copy(nivelExperiencia = newLevel)
            sharedPrefs.edit().putString("user_level", newLevel).apply()
            repository.saveProfile(updated)
            showLevelUpSuggestion.value = null
            repository.addFeedItem("Subi para o nível ${newLevel.uppercase()} no MAX PERFORMANCE! Rumo à alta performance!", null, updated.nome)
            AudioSynth.playChimeSound()
        }
    }

    fun declineLevelUpSuggestion() {
        val histSize = history.value.size
        sharedPrefs.edit().putInt("level_suggestion_declined_at_workout_count", histSize).apply()
        showLevelUpSuggestion.value = null
        AudioSynth.playTickSound()
    }

    fun setFirstLaunch(value: Boolean) {
        sharedPrefs.edit().putBoolean("first_launch", value).apply()
        firstLaunch.value = value
    }

    fun saveOnboardingProfile(
        name: String,
        age: Int,
        weight: Float,
        height: Int,
        experience: String,
        goal: String
    ) {
        viewModelScope.launch {
            val current = userProfile.value
            val heightM = height / 100f
            val imcVal = if (heightM > 0) weight / (heightM * heightM) else 0f
            val updated = current.copy(
                nome = name,
                idade = age,
                peso = weight,
                altura = height,
                nivelExperiencia = experience,
                imc = imcVal,
                localTreino = if (goal == "Hipertrofia" || goal == "Força") "Academia" else "Casa"
            )
            repository.saveProfile(updated)
        }
    }

    fun saveDeveloperOnboardingProfile(name: String) {
        if (!com.example.BuildConfig.DEBUG) return
        viewModelScope.launch {
            val current = userProfile.value
            val weight = 82f
            val height = 178
            val heightM = height / 100f
            val imcVal = if (heightM > 0) weight / (heightM * heightM) else 0f
            val updated = current.copy(
                nome = name,
                idade = 35,
                peso = weight,
                altura = height,
                nivelExperiencia = "Intermediário",
                imc = imcVal,
                localTreino = "Academia Teste",
                sexo = "Masculino",
                metaSemanal = 5
            )
            repository.saveProfile(updated)
            
            // Seed default exercises but do NOT create the ABCDE routine automatically (database starts empty)
            com.example.utils.DeveloperTools.seedExercises(db)
            // com.example.utils.DeveloperTools.createWorkoutRoutine(db, "ABCDE")
            
            // Transition directly to Home
            setFirstLaunch(false)
        }
    }

    fun runManualLevelSuggestion() {
        val profile = userProfile.value
        val hist = history.value
        val totalWorkouts = hist.size
        val monthsActive = com.example.utils.LevelSuggester.getMonthsSinceFirstWorkout(hist)
        val weightIncrease = com.example.utils.LevelSuggester.getWeightIncreasePercentage(hist)

        val targetWorkouts = if (profile.nivelExperiencia == "Iniciante") 50 else 120
        val targetMonths = if (profile.nivelExperiencia == "Iniciante") 4 else 12
        val targetIncrease = if (profile.nivelExperiencia == "Iniciante") 15 else 25
        val nextLevelName = if (profile.nivelExperiencia == "Iniciante") "Intermediário" else "Avançado"

        if (profile.nivelExperiencia == "Avançado") {
            forceDiagnosticMessage.value = "Você já está no nível máximo: AVANÇADO! Parabéns pela consistência e força!"
            return
        }

        val suggestion = com.example.utils.LevelSuggester.shouldSuggestLevelUp(profile, hist)
        if (suggestion.suggest) {
            showLevelUpSuggestion.value = suggestion
        } else {
            forceDiagnosticMessage.value = """
                Diagnóstico para nível $nextLevelName:
                
                • Treinos: $totalWorkouts / $targetWorkouts concluídos ${if (totalWorkouts >= targetWorkouts) "[OK]" else "[Pendente]"}
                • Meses ativo: $monthsActive / $targetMonths meses ${if (monthsActive >= targetMonths) "[OK]" else "[Pendente]"}
                • Progressão de Carga: $weightIncrease% / $targetIncrease% de aumento ${if (weightIncrease >= targetIncrease) "[OK]" else "[Pendente]"}
                
                Dica: Continue treinando duro! O app sugerirá o próximo nível assim que você atingir todas as metas acima.
            """.trimIndent()
        }
    }

    fun clearWorkoutError() {
        workoutErrorMessage.value = null
    }

    private fun getSavedRestTimeForExercise(exerciseName: String, defaultSeconds: Int): Int {
        val prefs = getApplication<Application>().getSharedPreferences("exercise_preferences", Context.MODE_PRIVATE)
        val key = "rest_time_${exerciseName.trim().lowercase()}"
        return prefs.getInt(key, defaultSeconds)
    }

    private fun saveRestTimeForExercise(exerciseName: String, seconds: Int) {
        val prefs = getApplication<Application>().getSharedPreferences("exercise_preferences", Context.MODE_PRIVATE)
        val key = "rest_time_${exerciseName.trim().lowercase()}"
        prefs.edit().putInt(key, seconds).apply()
    }

    // --- TIMERS TICK ENGINE ---
    private var isStartingWorkoutSession = false

    fun startWorkoutSession(workout: Workout, forceStart: Boolean = false) {
        if (isStartingWorkoutSession && !forceStart) return

        // Trava de Sessão Única (Singleton) em memória: bloqueia criação de "treino fantasma" duplicado
        val hasInMemoryActive = activeWorkout.value != null &&
            (workoutFlowState.value == WorkoutFlowState.Running || isPreSessionActive.value)
        if (hasInMemoryActive && !forceStart) {
            if (activeWorkout.value?.id == workout.id) {
                activeScreen.value = AppScreen.PLAYER
                return
            } else {
                showActiveSessionPrompt.value = true
                workoutErrorMessage.value = "Você já possui um treino em andamento (${activeWorkout.value?.name}). Retome ou descarte a sessão ativa antes de iniciar outro treino."
                return
            }
        }

        isStartingWorkoutSession = true

        val gymContextName = activeGymProfile.value?.name ?: "Sem Contexto"
        val prepTrace = com.example.services.telemetry.PerformanceMonitor.startSessionPrepTrace(
            workoutId = workout.id,
            workoutName = workout.name,
            gymContext = gymContextName
        )

        // Instant UX Feedback: Immediate state update on UI thread before async dispatch
        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(
            isPreparing = true,
            currentStepText = "Preparando seu treino...",
            completedSteps = emptyList()
        )

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Garantir no máximo uma sessão ativa: verificar se já existe sessão ATIVO
                val activeSessions = repository.getAllActiveWorkoutSessions()
                if (activeSessions.isNotEmpty()) {
                    if (activeSessions.size > 1) {
                        val mostRecent = activeSessions.maxByOrNull { 
                            maxOf(it.startTimeMonotonic, it.startTime)
                        } ?: activeSessions.first()
                        val toArchive = activeSessions.filter { it.id != mostRecent.id }
                        val archiveTimestamp = System.currentTimeMillis()
                        android.util.Log.w(
                            "MAX_PERFORMANCE_DEBUG",
                            "⚠️ Múltiplas sessões em startWorkoutSession: mantendo ${mostRecent.id} e arquivando ${toArchive.size} antigas."
                        )
                        for (oldSession in toArchive) {
                            val dur = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
                                oldSession.startTimeMonotonic,
                                oldSession.startTime,
                                oldSession.monotonicStartTimeNanos
                            )
                            repository.updateWorkoutSessionStatusVersioned(
                                sessionId = oldSession.id,
                                status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                                newVersion = oldSession.version + 1,
                                completedAt = archiveTimestamp,
                                durationSeconds = dur
                            )
                        }
                        val archivedNames = toArchive.joinToString(", ") { it.workoutName }
                        val notifyMsg = if (toArchive.size == 1) {
                            "Uma sessão anterior incompleta ($archivedNames) foi arquivada automaticamente."
                        } else {
                            "${toArchive.size} sessões anteriores incompletas foram arquivadas automaticamente."
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            activeSessionsList.value = listOf(mostRecent)
                            currentSessionId.value = mostRecent.id
                            isMultipleActiveSessionsConflict.value = false
                            showActiveSessionPrompt.value = true
                            sessionArchivedNotification.value = notifyMsg
                            workoutErrorMessage.value = "Você possui um treino em andamento. Retome ou descarte a sessão antes de iniciar um novo treino."
                            sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                            isStartingWorkoutSession = false
                        }
                        prepTrace.finishTrace(isSuccess = false, errorMessage = "Sessão ativa existente bloqueou novo treino")
                        return@launch
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        activeSessionsList.value = activeSessions
                        currentSessionId.value = activeSessions.first().id
                        isMultipleActiveSessionsConflict.value = false
                        showActiveSessionPrompt.value = true
                        workoutErrorMessage.value = "Você possui um treino em andamento. Retome ou descarte a sessão antes de iniciar um novo treino."
                        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                        isStartingWorkoutSession = false
                    }
                    prepTrace.finishTrace(isSuccess = false, errorMessage = "Sessão ativa existente bloqueou novo treino")
                    return@launch
                }

                if (!forceStart) {
                    val warningEval = com.example.services.recommendation.engine001.TrainingEngine001
                        .evaluateSessionRecoveryWarning(workout, history.value)
                    if (warningEval != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                            workoutWarningState.value = WorkoutWarningData(
                                workout = workout,
                                muscleName = warningEval.muscleOrWorkoutName,
                                lastTrainedDateStr = warningEval.lastTrainedDateStr,
                                daysAgo = warningEval.daysAgo,
                                isFullBodyWarning = warningEval.isFullBodyWarning,
                                lastWorkoutName = warningEval.lastWorkoutName,
                                targetWorkoutName = warningEval.targetWorkoutName,
                                customMessage = warningEval.formattedMessage
                            )
                            sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                            isStartingWorkoutSession = false
                        }
                        return@launch
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    workoutWarningState.value = null
                }

                // Step 1: Recovery Assessment
                sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(
                    isPreparing = true,
                    currentStepText = "avaliando recuperação...",
                    completedSteps = listOf("Preparando seu treino")
                )

                android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Iniciando sessão: ${workout.id} - ${workout.name}")

                val list = prepTrace.measureStep({ prepTrace.jsonParsingNanos = it }) {
                    val rawExercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
                    com.example.data.repository.WorkoutProtocolsRepository.ensureCalfAndTrapsInExercises(
                        workoutName = workout.name,
                        exercises = rawExercises
                    )
                }

                if (list.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        workoutErrorMessage.value = "Este treino está corrompido ou não possui exercícios definidos."
                        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                    }
                    prepTrace.finishTrace(isSuccess = false, errorMessage = "Treino corrompido ou vazio")
                    return@launch
                }

                val isRestoredSession = activeExercises.value.isNotEmpty() &&
                        sharedPrefs.getBoolean("has_active_session", false) &&
                        activeWorkout.value?.id == workout.id

                prepTrace.isCacheHit = isRestoredSession

                if (isRestoredSession) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        activeScreen.value = AppScreen.PLAYER
                        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                        if (workoutFlowState.value == WorkoutFlowState.Running && !isPreSessionActive.value) {
                            startTicker()
                        }
                    }
                    prepTrace.finishTrace(isSuccess = true)
                    return@launch
                }

                // Step 2: Adjusting loads & hydration
                sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(
                    isPreparing = true,
                    currentStepText = "ajustando cargas...",
                    completedSteps = listOf("Preparando seu treino", "✓ avaliando recuperação")
                )

                val computedProgressionSuggestions = mutableMapOf<String, com.example.data.repository.LoadProgressionSuggestion>()

                val resolvedWorkout = if (isRestoredSession) {
                    activeWorkout.value ?: workout
                } else {
                    val defaultRest = sessionDefaultRestSeconds.value
                    val gymConfigsMap = _gymConfigs.value
                    val histValue = history.value
                    val activeGym = activeGymProfile.value

                    val originalExercises = prepTrace.measureStep({ prepTrace.hydrationNanos = it }) {
                        list.map { ex ->
                            val savedRest = getSavedRestTimeForExercise(ex.name, defaultRest)
                            val gymConfig = gymConfigsMap[ex.name]
                            val targetRepsForEx = ex.sets.firstOrNull()?.reps?.takeIf { it > 0 } ?: 10

                            var historicalWeight = 0f
                            var progressionDetails: com.example.data.repository.LoadProgressionSuggestion? = null
                            try {
                                progressionDetails = com.example.data.repository.ExerciseHistoryRepository
                                    .getSuggestedLoadDetailsForExercise(
                                        history = histValue,
                                        exerciseNameOrId = ex.name.ifEmpty { ex.exerciseId },
                                        equipment = ex.equipment,
                                        gymProfile = activeGym,
                                        progressionType = ex.progressionType,
                                        targetReps = targetRepsForEx,
                                        isLowerBodyCompound = ex.exerciseType.lowercase().contains("composto") && (ex.category.lowercase().contains("perna") || ex.category.lowercase().contains("glúteo")),
                                        userProfile = userProfile.value
                                    )
                                historicalWeight = progressionDetails.suggestedWeightKg
                            } catch (e: Exception) {
                                com.example.services.telemetry.Telemetry.log(
                                    com.example.data.model.TelemetryEventType.ERROR,
                                    mapOf("error" to (e.message ?: "Failed loading last weight for ${ex.name}"))
                                )
                            }

                            val isTempoExercise = ex.executionType.equals("TEMPO", ignoreCase = true)
                            if (!isTempoExercise && gymConfig?.defaultLoad == null &&
                                progressionDetails != null && progressionDetails.progressionApplied && progressionDetails.deltaKg > 0f
                            ) {
                                val badgeKey = ex.name.trim().lowercase()
                                if (badgeKey.isNotEmpty()) {
                                    computedProgressionSuggestions[badgeKey] = progressionDetails
                                }
                            }
                            val freshSets = ex.sets.mapIndexed { idx, s ->
                                val exerciseDefault = com.example.utils.WeightCalculator.defaultStartingWeightForExercise(
                                    ex.name,
                                    ex.equipment,
                                    userProfile.value
                                )
                                val defaultWeight = when {
                                    isTempoExercise -> s.weight
                                    historicalWeight > 0f -> historicalWeight
                                    exerciseDefault > 0f -> exerciseDefault
                                    else -> 0f
                                }
                                val rawFinal = gymConfig?.defaultLoad ?: defaultWeight
                                val finalWeight = if (isTempoExercise || (historicalWeight > 0f && gymConfig?.defaultLoad == null)) {
                                    rawFinal
                                } else {
                                    com.example.utils.WeightCalculator.roundToAvailableWeight(rawFinal, ex.equipment, activeGym)
                                }
                                s.copy(
                                    id = "set_session_${java.util.UUID.randomUUID()}_${idx + 1}",
                                    index = if (s.index <= 0) idx + 1 else s.index,
                                    completed = false,
                                    weight = finalWeight,
                                    baseWeight = finalWeight
                                )
                            }

                            ex.copy(sets = freshSets, restTimeSeconds = savedRest)
                        }
                    }

                    val baseWorkoutWithHistoricalLoads = workout.copy(
                        exercisesJson = JsonUtils.serializeWorkoutExercises(originalExercises)
                    )

                    var currentTonnage = 0f
                    originalExercises.forEach { ex ->
                        ex.sets.forEach { s ->
                            currentTonnage += com.example.utils.WeightCalculator.calculateSetVolumeKg(
                                getApplication(),
                                ex.name,
                                ex.equipment,
                                s.weight,
                                s.reps
                            )
                        }
                    }

                    val musclesInWorkout = originalExercises.map { it.category }.distinct()
                    val relevantRecoveries = recoveryList.value.filter { it.name in musclesInWorkout }
                    val avgRecoveryPercent = if (relevantRecoveries.isNotEmpty()) {
                        relevantRecoveries.map { it.recoveryPercent }.average().toFloat()
                    } else {
                        1.0f
                    }
                    val recoveryProfile = avgRecoveryPercent.toDouble() * 10.0
                    val fatigueLevel = (1.0f - avgRecoveryPercent.coerceIn(0.0f, 1.0f)) * 10.0
                    val recuperacaoAlta = (recoveryProfile >= 8.5)

                    val last14DaysMs = 14L * 24L * 60L * 60L * 1000L
                    val now = System.currentTimeMillis()
                    val workoutsInLast14Days = histValue.count { now - it.date <= last14DaysMs }
                    val targetWorkouts14Days = userProfile.value.metaSemanal * 2
                    val adherenceRate = if (targetWorkouts14Days > 0) {
                        workoutsInLast14Days.toDouble() / targetWorkouts14Days.toDouble()
                    } else {
                        1.0
                    }

                    val last4WeeksTonnages = histValue.filter { it.workoutId == workout.id }.take(4).map { it.totalWeight }.ifEmpty { listOf(currentTonnage) }
                    val lastSessionForWorkout = histValue.firstOrNull { it.workoutId == workout.id }
                    val repsAlvoBatidas = lastSessionForWorkout?.let { last ->
                        val lastExs = try { JsonUtils.parseWorkoutExercises(last.exercisesJson) } catch (e: Exception) { emptyList() }
                        if (lastExs.isEmpty()) true
                        else lastExs.all { ex -> ex.sets.all { s -> s.completed } }
                    } ?: true

                    val returnSessionsRemaining = sharedPrefs.getInt("return_sessions_remaining_${workout.id}", 0)
                    val consecutiveFloorWeeks = sharedPrefs.getInt("consecutive_floor_weeks_${workout.id}", 0)

                    val currentWorkoutContext = com.example.engine.WorkoutContext(
                        durationMinutes = 60,
                        objective = "Hipertrofia",
                        gymProfile = activeGym,
                        technique = "Linear",
                        userPreferences = com.example.engine.UserPreferences(
                            level = userProfile.value.nivelExperiencia,
                            preferredFocus = "Hipertrofia"
                        ),
                        experience = userProfile.value.nivelExperiencia,
                        availableTime = 60,
                        fatigueLevel = fatigueLevel,
                        recoveryProfile = recoveryProfile,
                        lastWorkoutDate = histValue.firstOrNull()?.date,
                        treinosTotaisHistorico = histValue.size,
                        metaSemanal = userProfile.value.metaSemanal,
                        adherenceRate = adherenceRate,
                        recuperacaoAlta = recuperacaoAlta,
                        repsAlvoBatidas = repsAlvoBatidas,
                        tonelagemAtual = currentTonnage,
                        last4WeeksTonnages = last4WeeksTonnages,
                        consecutiveFloorWeeks = consecutiveFloorWeeks,
                        returnSessionsRemaining = returnSessionsRemaining
                    )

                    // Step 3: Organizing sequence & Decision Engine
                    sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(
                        isPreparing = true,
                        currentStepText = "organizando sequência...",
                        completedSteps = listOf("Preparando seu treino", "✓ avaliando recuperação", "✓ ajustando cargas")
                    )

                    val resolved = prepTrace.measureStep({ prepTrace.decisionEngineNanos = it }) {
                        com.example.engine.DecisionCatalog.resolveWorkout(currentWorkoutContext, baseWorkoutWithHistoricalLoads)
                    }

                    prepTrace.workoutBuilderNanos = prepTrace.decisionEngineNanos

                    sharedPrefs.edit()
                        .putInt("return_sessions_remaining_${workout.id}", currentWorkoutContext.returnSessionsRemaining)
                        .putInt("consecutive_floor_weeks_${workout.id}", currentWorkoutContext.consecutiveFloorWeeks)
                        .apply()

                    resolved
                }

                val freshList = if (isRestoredSession) {
                    activeExercises.value
                } else {
                    try {
                        JsonUtils.parseWorkoutExercises(resolvedWorkout.exercisesJson).map { ex ->
                            ex.copy(sets = ex.sets.map { s -> s.copy(completed = false, rpe = "") })
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                if (freshList.isEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        workoutErrorMessage.value = "O treino não possui nenhum exercício válido ou compatível disponível."
                        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                    }
                    prepTrace.finishTrace(isSuccess = false, errorMessage = "Exercícios válidos vazios")
                    return@launch
                }

                prepTrace.exerciseCount = freshList.size

                // Step 4: Ready
                sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(
                    isPreparing = true,
                    currentStepText = "pronto",
                    completedSteps = listOf("Preparando seu treino", "✓ avaliando recuperação", "✓ ajustando cargas", "✓ organizando sequência")
                )

                prepTrace.measureStep({ prepTrace.navigationNanos = it }) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                        activeWorkout.value = resolvedWorkout
                        activeExercises.value = freshList
                        if (!isRestoredSession) {
                            exerciseProgressionSuggestions.value = computedProgressionSuggestions.toMap()
                        }
                        currentExerciseIndex.value = 0
                        workoutDurationSeconds.value = 0L
                        isRestTimerActive.value = false
                        isPreSessionActive.value = true
                        workoutErrorMessage.value = null
                        fastSetWarningMessage.value = null
                        lastSetCompletionTimes.clear()
                        workoutFlowState.value = WorkoutFlowState.Idle
                        activeScreen.value = AppScreen.PLAYER
                        sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                        AudioSynth.playTickSound()
                    }
                }

                prepTrace.finishTrace(isSuccess = true)

                com.example.services.telemetry.Telemetry.log(
                    com.example.data.model.TelemetryEventType.WORKOUT_STARTED,
                    mapOf("workout_id" to workout.id, "workout_name" to workout.name)
                )
            } catch (e: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Crash evitado ao iniciar treino", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    workoutErrorMessage.value = "Não foi possível iniciar o treino: ${e.localizedMessage ?: "Erro desconhecido"}"
                    sessionPrepStatus.value = com.example.services.telemetry.SessionPrepStepState(isPreparing = false)
                }
                prepTrace.finishTrace(isSuccess = false, errorMessage = e.message)
            } finally {
                isStartingWorkoutSession = false
            }
        }
    }

    fun replaceExerciseInActiveWorkout(index: Int, newExercise: Exercise) {
        val currentExs = activeExercises.value.toMutableList()
        if (index in currentExs.indices) {
            val oldEx = currentExs[index]
            val newEquip = newExercise.equipment.ifBlank { oldEx.equipment }
            val targetRepsForEx = oldEx.sets.firstOrNull()?.reps?.takeIf { it > 0 } ?: 10
            val progressionDetails = com.example.data.repository.ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
                history = history.value,
                exerciseNameOrId = newExercise.name.ifEmpty { newExercise.id },
                equipment = newEquip,
                gymProfile = activeGymProfile.value,
                progressionType = oldEx.progressionType,
                targetReps = targetRepsForEx,
                userProfile = userProfile.value
            )
            val resolvedHistoricalWeight = progressionDetails.suggestedWeightKg
            val fallbackWeight = com.example.utils.WeightCalculator.defaultStartingWeightForExercise(
                newExercise.name,
                newEquip,
                userProfile.value
            )
            val targetWeight = if (resolvedHistoricalWeight > 0f) resolvedHistoricalWeight else fallbackWeight
            val updatedMap = exerciseProgressionSuggestions.value.toMutableMap()
            updatedMap.remove(oldEx.name.trim().lowercase())
            if (progressionDetails.progressionApplied && progressionDetails.deltaKg > 0f) {
                val newKey = newExercise.name.trim().lowercase()
                if (newKey.isNotEmpty()) {
                    updatedMap[newKey] = progressionDetails
                }
            }
            exerciseProgressionSuggestions.value = updatedMap
            val updatedSets = oldEx.sets.map { s ->
                s.copy(
                    weight = targetWeight,
                    baseWeight = targetWeight,
                    completed = false
                )
            }
            currentExs[index] = oldEx.copy(
                exerciseId = newExercise.id,
                name = newExercise.name,
                category = newExercise.category,
                equipment = newEquip,
                sets = updatedSets
            )
            activeExercises.value = currentExs
            saveActiveSession()
            AudioSynth.playChimeSound()
        }
    }

    fun swapExerciseOrderInActiveWorkout(fromIndex: Int, toIndex: Int) {
        val currentExs = activeExercises.value.toMutableList()
        if (fromIndex in currentExs.indices && toIndex in currentExs.indices && fromIndex != toIndex) {
            val temp = currentExs[fromIndex]
            currentExs[fromIndex] = currentExs[toIndex]
            currentExs[toIndex] = temp
            activeExercises.value = currentExs
            saveActiveSession()
            AudioSynth.playTickSound()
        }
    }

    fun createAndReplaceCustomExerciseInActiveWorkout(
        index: Int,
        name: String,
        equipmentCategory: String,
        useLbs: Boolean
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return

        val appContext = getApplication<Application>()
        val defaultStepKg = WeightCalculator.defaultStepForCategory(equipmentCategory, 20f)
        WeightCalculator.saveExerciseEquipmentConfig(
            context = appContext,
            exerciseName = cleanName,
            category = equipmentCategory,
            stepKg = defaultStepKg,
            isCustomStep = true,
            useLbs = useLbs,
            stepLbs = 10f
        )

        val currentExs = activeExercises.value.toMutableList()
        val oldEx = currentExs.getOrNull(index)
        val targetMuscleCategory = oldEx?.category?.ifBlank { "Geral" } ?: "Geral"
        val newCustomId = "custom_${UUID.randomUUID()}"
        val unitLabel = if (useLbs) "Placas em Libras (LBS)" else "Quilos (KG)"

        viewModelScope.launch {
            val entity = CustomExerciseEntity(
                id = newCustomId,
                name = cleanName,
                category = targetMuscleCategory,
                description = "Equipamento: $equipmentCategory • Unidade nativa: $unitLabel",
                difficulty = "Intermediário"
            )
            repository.addCustomExercise(entity)
        }

        if (oldEx != null) {
            val isBodyweight = equipmentCategory.contains("Peso Corporal", ignoreCase = true)
            val adaptedSets = oldEx.sets.map { s ->
                val initialWeight = when {
                    isBodyweight -> 0f
                    useLbs && s.weight <= 0f -> (50f * WeightCalculator.LBS_TO_KG)
                    else -> s.weight
                }
                s.copy(weight = initialWeight)
            }
            currentExs[index] = oldEx.copy(
                exerciseId = newCustomId,
                name = cleanName,
                category = targetMuscleCategory,
                equipment = equipmentCategory,
                sets = adaptedSets
            )
            activeExercises.value = currentExs
            saveActiveSession()
            AudioSynth.playChimeSound()
        }
    }

    fun confirmAndStartWorkout() {
        if (workoutFlowState.value == WorkoutFlowState.Running) {
            isPreSessionActive.value = false
            saveActiveSession()
            return
        }

        val method = selectedTrainingMethod.value
        val adjustedExercises = activeExercises.value.map { ex ->
            val updatedSets = method.applyMethod(ex.sets).map { s -> s.copy(completed = false, rpe = "") }
            ex.copy(sets = updatedSets)
        }
        activeExercises.value = adjustedExercises

        isPreSessionActive.value = false
        workoutFlowState.value = WorkoutFlowState.Running
        currentExerciseIndex.value = 0
        com.example.services.WorkoutServiceState.startOrSyncWorkoutClock(0L)
        com.example.services.WorkoutServiceState.resetTimers()
        
        val sessionId = java.util.UUID.randomUUID().toString()
        currentSessionId.value = sessionId
        val startTime = System.currentTimeMillis()

        sharedPrefs.edit()
            .putString("active_session_id", sessionId)
            .putLong("active_session_start_time", startTime)
            .apply()

        // Start foreground service
        val workout = activeWorkout.value
        if (workout != null) {
            val sessionEntity = WorkoutSessionEntity(
                id = sessionId,
                workoutId = workout.id,
                workoutName = workout.name,
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = startTime,
                endTime = null,
                completedAt = null,
                durationSeconds = 0L,
                exercisesJson = JsonUtils.serializeWorkoutExercises(adjustedExercises),
                version = 1,
                startTimeMonotonic = android.os.SystemClock.elapsedRealtime(),
                monotonicStartTimeNanos = System.nanoTime()
            )
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repository.saveWorkoutSession(sessionEntity)
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to save WorkoutSessionEntity to Room", e)
                }
            }
            try {
                com.example.services.WorkoutForegroundService.isWorkoutActive = true
                val serviceIntent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
                    action = com.example.services.WorkoutForegroundService.ACTION_START_WORKOUT
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_WORKOUT_NAME, workout.name)
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_SOUND_OPTION, restSoundOption.value)
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_SOUND_ENABLED, restSoundEnabled.value)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(serviceIntent)
                } else {
                    getApplication<Application>().startService(serviceIntent)
                }
            } catch (e: Exception) {
                com.example.utils.CrashLog.record(e, "MainViewModel.confirmAndStartWorkout.startForegroundService")
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to start foreground service in confirmAndStartWorkout", e)
            }
        }

        startTicker()
        com.example.utils.MaxSoundManager.playWorkoutStarted(getApplication())
    }

    fun cancelPreSession() {
        tickerJob?.cancel()
        com.example.services.WorkoutServiceState.workoutStartElapsedRealtime = 0L
        com.example.services.WorkoutServiceState.workoutStartWallClockMillis = 0L
        com.example.services.WorkoutServiceState.resetTimers()
        com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())
        activeWorkout.value = null
        activeExercises.value = emptyList()
        exerciseProgressionSuggestions.value = emptyMap()
        isPreSessionActive.value = false
        workoutFlowState.value = WorkoutFlowState.Idle
        activeScreen.value = AppScreen.HOME
        saveActiveSession()
    }

    fun pauseActiveSessionAndExit() {
        tickerJob?.cancel()
        com.example.services.WorkoutServiceState.workoutStartElapsedRealtime = 0L
        com.example.services.WorkoutServiceState.workoutStartWallClockMillis = 0L
        com.example.services.WorkoutServiceState.resetTimers()
        com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())
        activeScreen.value = AppScreen.HOME
        saveActiveSession()
    }

    fun generateAndStartExpressWorkout(
        durationMinutes: Int,
        objective: String,
        muscleGroup: String,
        technique: String,
        includeWarmup: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val workout = withContext(Dispatchers.Default) {
                    val provider = QuickWorkoutProvider(
                        durationMinutes = durationMinutes,
                        objective = objective,
                        gymProfile = activeGymProfile.value,
                        technique = technique,
                        userPreferences = com.example.engine.UserPreferences(
                            level = userProfile.value.nivelExperiencia,
                            preferredFocus = objective
                        ),
                        muscleGroup = muscleGroup
                    )
                    val context = provider.build().copy(includeWarmup = includeWarmup)
                    WorkoutEngine.generate(context)
                }
                startWorkoutSession(workout)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Erro ao gerar treino expresso", e)
                workoutErrorMessage.value = e.message ?: "Erro ao gerar treino expresso"
            }
        }
    }

    fun generateExpressWorkout(
        durationMinutes: Int,
        objective: String,
        muscleGroup: String = "",
        technique: String = "Linear",
        includeWarmup: Boolean = true
    ): com.example.data.model.Workout {
        val provider = QuickWorkoutProvider(
            durationMinutes = durationMinutes,
            objective = objective,
            gymProfile = activeGymProfile.value,
            technique = technique,
            userPreferences = com.example.engine.UserPreferences(
                level = userProfile.value.nivelExperiencia,
                preferredFocus = objective
            ),
            muscleGroup = muscleGroup
        )
        val context = provider.build().copy(includeWarmup = includeWarmup)
        return WorkoutEngine.generate(context)
    }

    /**
     * Recalcula instantaneamente `restTimeRemaining` e `workoutDurationSeconds` a partir de
     * `SystemClock.elapsedRealtime()` no exato milissegundo em que a tela é desbloqueada (`ON_RESUME`).
     */
    fun syncTimersFromPhysicalClockOnResume() {
        val restJustCompleted = com.example.services.WorkoutServiceState.recalculateNowFromPhysicalClock()
        if (restJustCompleted) {
            com.example.utils.GymAudioEngine.playRestFinishedAlert(
                context = getApplication(),
                soundOptionOverride = restSoundOption.value,
                soundEnabledOverride = restSoundEnabled.value
            )
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        if (com.example.services.WorkoutServiceState.workoutStartElapsedRealtime <= 0L) {
            com.example.services.WorkoutServiceState.startOrSyncWorkoutClock(workoutDurationSeconds.value)
        }
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(250L)
                // Mesmo que o ForegroundService esteja rodando, ou como fallback se não estiver,
                // o cálculo é idempotente por diferença de timestamp físico (SystemClock.elapsedRealtime()).
                if (!com.example.services.WorkoutForegroundService.isServiceRunning) {
                    val nowElapsed = android.os.SystemClock.elapsedRealtime()
                    workoutDurationSeconds.value = com.example.services.WorkoutServiceState.computeWorkoutDurationSeconds(nowElapsed)
                    
                    if (isRestTimerActive.value) {
                        val remaining = com.example.services.WorkoutServiceState.computeRemainingRestSeconds(nowElapsed)
                        restTimeRemaining.value = remaining
                        if (remaining in 1..3 && com.example.services.WorkoutServiceState.lastCountdownBeepSec != remaining) {
                            com.example.services.WorkoutServiceState.lastCountdownBeepSec = remaining
                            com.example.utils.GymAudioEngine.playRestCountdownBeep(
                                context = getApplication(),
                                secondRemaining = remaining,
                                soundEnabledOverride = restSoundEnabled.value
                            )
                        }
                        if (remaining <= 0) {
                            com.example.utils.GymAudioEngine.playRestFinishedAlert(
                                context = getApplication(),
                                soundOptionOverride = restSoundOption.value,
                                soundEnabledOverride = restSoundEnabled.value
                            )
                            com.example.services.WorkoutServiceState.resetTimers()
                        }
                    }

                    if (isTempoTimerActive.value) {
                        val remTempo = if (com.example.services.WorkoutServiceState.tempoEndElapsedRealtime > 0L) {
                            kotlin.math.max(0, ((com.example.services.WorkoutServiceState.tempoEndElapsedRealtime - nowElapsed + 999L) / 1000L).toInt())
                        } else {
                            (tempoTimeRemaining.value - 1).coerceAtLeast(0)
                        }
                        tempoTimeRemaining.value = remTempo
                        if (remTempo <= 0) {
                            com.example.utils.GymAudioEngine.playRestFinishedAlert(
                                context = getApplication(),
                                soundOptionOverride = restSoundOption.value,
                                soundEnabledOverride = restSoundEnabled.value
                            )
                            tempoTimeRemaining.value = 0
                            isTempoTimerActive.value = false
                            com.example.services.WorkoutServiceState.tempoEndElapsedRealtime = 0L
                        }
                    }
                }
            }
        }
    }

    fun startTempoTimer(seconds: Int) {
        com.example.services.WorkoutServiceState.startTempoClock(seconds)
        AudioSynth.playTickSound()
    }

    fun pauseTempoTimer() {
        isTempoTimerActive.value = false
        AudioSynth.playTickSound()
    }

    fun resumeTempoTimer() {
        isTempoTimerActive.value = true
        AudioSynth.playTickSound()
    }

    fun resetTempoTimer() {
        isTempoTimerActive.value = false
        tempoTimeRemaining.value = tempoTimeTotal.value
        AudioSynth.playTickSound()
    }

    fun getHistoricalMaxForExercise(exerciseName: String): Pair<Float, Int> {
        return com.example.services.domain.PerformanceEngine.getHistoricalMaxForExercise(history.value, exerciseName)
    }

    fun getHistoricalMaxRepsForWeight(exerciseName: String, weight: Float): Int {
        return com.example.services.domain.PerformanceEngine.getHistoricalMaxRepsForWeight(history.value, exerciseName, weight)
    }

    data class ExerciseWeeklyProgress(
        val weekLabel: String,
        val totalVolume: Float,
        val maxWeight: Float,
        val totalSets: Int,
        val timestamp: Long
    )

    fun getWeeklyProgressForExercise(exerciseName: String): List<ExerciseWeeklyProgress> {
        return com.example.services.domain.PerformanceEngine.getWeeklyProgressForExercise(history.value, exerciseName)
    }

    /**
     * Retorna o progresso semanal de treinos (Pair<Int, Int> -> treinosConcluidos, metaSemanal).
     * Conta estritamente os treinos realizados a partir de segunda-feira (00:00) da semana corrente.
     */
    fun getWeeklyProgress(): Pair<Int, Int> {
        val zoneId = java.time.ZoneId.systemDefault()
        val mondayStartMillis = java.time.LocalDate.now(zoneId)
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val gymCount = history.value.count { it.date >= mondayStartMillis }
        val runCount = runningSessions.value.count { it.date >= mondayStartMillis }
        val cardioCount = cardioSessions.value.count { it.date >= mondayStartMillis }
        val completedCount = gymCount + runCount + cardioCount
        val targetWeekly = userProfile.value.metaSemanal.coerceAtLeast(1)
        return Pair(completedCount, targetWeekly)
    }

    fun dismissPrCelebration(celebration: PrCelebration) {
        activePrCelebration.value = null
    }

    fun recordRpeForSet(exerciseIndex: Int, setIndex: Int, rpeValue: String) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val currentSets = ex.sets.toMutableList()
            if (setIndex in currentSets.indices) {
                val set = currentSets[setIndex]
                currentSets[setIndex] = set.copy(rpe = rpeValue)
                currentExs[exerciseIndex] = ex.copy(sets = currentSets)
                activeExercises.value = currentExs
                android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Recorded RPE '$rpeValue' for ${ex.name} Set #${setIndex + 1}")

                // Navigation after exercise completion: advance directly to next incomplete exercise in Player
                if (currentSets.all { it.completed }) {
                    val nextIncompleteIdx = currentExs.indexOfFirst { exercise ->
                        exercise.sets.any { !it.completed }
                    }
                    if (nextIncompleteIdx != -1) {
                        currentExerciseIndex.value = nextIncompleteIdx
                    }
                    isPreSessionActive.value = false
                }
            }
        }
        activeRpePrompt.value = null
    }

    fun persistActiveSessionToRoom() {
        val sid = currentSessionId.value
        val exs = activeExercises.value
        val wrk = activeWorkout.value
        if (wrk == null || exs.isEmpty()) return

        // Validação de cargas e repetições antes de persistir
        try {
            WorkoutSessionValidator.validateExercises(exs)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Validação de exercícios falhou antes de persistir", e)
            return
        }

        val serialized = JsonUtils.serializeWorkoutExercises(exs)
        viewModelScope.launch(Dispatchers.IO) {
            sessionUpdateMutex.withLock {
                try {
                    val targetSid = sid ?: run {
                        val activeSessions = repository.getAllActiveWorkoutSessions()
                        activeSessions.firstOrNull()?.id
                    }

                    if (targetSid != null) {
                        currentSessionId.value = targetSid
                        val currentDb = repository.getWorkoutSessionById(targetSid)
                        if (currentDb == null) {
                            android.util.Log.w("MainViewModel", "Sessão $targetSid não encontrada no Room")
                            return@withLock
                        }
                        if (currentDb.status != WorkoutSessionStatus.ATIVO.rawValue) {
                            android.util.Log.w("MainViewModel", "Não é permitido alterar séries em sessão com status '${currentDb.status}'")
                            return@withLock
                        }

                        // Atualização otimista baseada na versão persistida e recuperada do banco
                        val updated = repository.updateWorkoutSessionExercisesOptimistic(
                            sessionId = targetSid,
                            exercisesJson = serialized,
                            expectedVersion = currentDb.version
                        )
                        if (!updated) {
                            android.util.Log.w("MainViewModel", "Conflito de versão ao persistir séries para $targetSid (versão esperada: ${currentDb.version}). Buscando versão atualizada no banco...")
                            val freshDb = repository.getWorkoutSessionById(targetSid)
                            if (freshDb != null && freshDb.status == WorkoutSessionStatus.ATIVO.rawValue) {
                                repository.updateWorkoutSessionExercisesOptimistic(
                                    sessionId = targetSid,
                                    exercisesJson = serialized,
                                    expectedVersion = freshDb.version
                                )
                            }
                        }
                    } else {
                        // Se não existia sessão salva, criar nova com status ATIVO e version 1
                        val newSid = UUID.randomUUID().toString()
                        currentSessionId.value = newSid
                        val newSession = WorkoutSessionEntity(
                            id = newSid,
                            workoutId = wrk.id,
                            workoutName = wrk.name,
                            status = WorkoutSessionStatus.ATIVO.rawValue,
                            startTime = System.currentTimeMillis(),
                            durationSeconds = workoutDurationSeconds.value,
                            exercisesJson = serialized,
                            version = 1,
                            startTimeMonotonic = android.os.SystemClock.elapsedRealtime(),
                            monotonicStartTimeNanos = System.nanoTime()
                        )
                        repository.saveWorkoutSession(newSession)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to persist active session to Room", e)
                }
            }
        }
    }

    fun updateExerciseNotes(exerciseIndex: Int, notes: String) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            currentExs[exerciseIndex] = ex.copy(notes = notes)
            activeExercises.value = currentExs
            persistActiveSessionToRoom()
        }
    }

    fun updateExerciseNotesByName(exerciseName: String, notes: String) {
        val currentExs = activeExercises.value.toMutableList()
        val idx = currentExs.indexOfFirst { it.name.trim().equals(exerciseName.trim(), ignoreCase = true) }
        if (idx != -1) {
            updateExerciseNotes(idx, notes)
        }
    }

    fun updateExerciseEquipmentByName(
        exerciseName: String,
        equipmentCategory: String,
        applyWeightToUncompletedSets: Float? = null
    ) {
        val currentExs = activeExercises.value.toMutableList()
        val idx = currentExs.indexOfFirst { it.name.trim().equals(exerciseName.trim(), ignoreCase = true) }
        if (idx != -1) {
            val ex = currentExs[idx]
            val updatedSets = if (applyWeightToUncompletedSets != null) {
                ex.sets.map { s ->
                    if (!s.completed) s.copy(weight = applyWeightToUncompletedSets) else s
                }
            } else {
                ex.sets
            }
            currentExs[idx] = ex.copy(equipment = equipmentCategory, sets = updatedSets)
            activeExercises.value = currentExs
            persistActiveSessionToRoom()
        }
    }

    fun toggleSetCompletion(exerciseIndex: Int, setIndex: Int) {
        try {
            val currentExs = activeExercises.value.toMutableList()
            if (exerciseIndex in currentExs.indices) {
                val ex = currentExs[exerciseIndex]
                val currentSets = ex.sets.toMutableList()
                if (setIndex in currentSets.indices) {
                    val set = currentSets[setIndex]
                    val wasCompleted = set.completed
                    
                    // Proteção Anti-Toque Duplo ao Concluir Série (Debounce de 500ms)
                    val now = System.currentTimeMillis()
                    if (!wasCompleted) {
                        if (now - lastSetCompletionTimestamp < 500L) {
                            // Ignora silenciosamente o toque acidental dentro da janela de 500ms
                            return
                        }
                        lastSetCompletionTimestamp = now
                        lastSetCompletionTimeMs.value = now
                    }

                    currentSets[setIndex] = set.copy(completed = !wasCompleted)
                    currentExs[exerciseIndex] = ex.copy(sets = currentSets)
                    activeExercises.value = currentExs

                    // Salvar cada alteração de série localmente antes de qualquer timer, notificação ou cálculo
                    persistActiveSessionToRoom()

                    // Play satisfaction tick sound if completed
                    if (!wasCompleted) {
                        // Validação Temporal de Séries (15 segundos) para o mesmo exercício
                        val now = System.currentTimeMillis()
                        val exerciseKey = ex.exerciseId.ifBlank { ex.name }
                        val lastTime = lastSetCompletionTimes[exerciseKey]
                        if (lastTime != null && (now - lastTime) < 15_000L) {
                            fastSetWarningMessage.value = "Série registrada muito rápido. Confira os dados."
                        }
                        lastSetCompletionTimes[exerciseKey] = now

                        try {
                            AudioSynth.playTickSound()
                        } catch (e: Exception) {
                            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to play tick sound", e)
                        }
                        
                        // Automatically trigger Rest Timer when a set is marked complete!
                        try {
                            triggerRestTimer(ex.restTimeSeconds)
                        } catch (e: Exception) {
                            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to trigger rest timer", e)
                        }

                        // PR Check
                        try {
                            val (histMaxWeight, histMaxReps) = getHistoricalMaxForExercise(ex.name)
                            
                            // Get the current set's historical max reps for its weight
                            val histMaxRepsAtCurrentWeight = getHistoricalMaxRepsForWeight(ex.name, set.weight)
                            
                            // Get other completed sets in this session to compare
                            val otherCompletedSets = currentSets.filterIndexed { idx, s -> s.completed && idx != setIndex }
                            val sessionMaxWeight = otherCompletedSets.map { it.weight }.maxOrNull() ?: 0f
                            val sessionMaxRepsAtCurrentWeight = otherCompletedSets.filter { it.weight == set.weight }.map { it.reps }.maxOrNull() ?: 0
                            
                            val overallMaxWeight = maxOf(histMaxWeight, sessionMaxWeight)
                            val overallMaxRepsAtCurrentWeight = maxOf(histMaxRepsAtCurrentWeight, sessionMaxRepsAtCurrentWeight)
                            
                            // It's a weight PR if this weight is strictly greater than the historical or session maximum weight.
                            // If overallMaxWeight is 0f, it's also a PR because it's the first completed set!
                            val isWeightPR = set.weight > overallMaxWeight || (overallMaxWeight == 0f && set.weight > 0f)
                            
                            // It's a reps PR if:
                            // 1. It's not a weight PR.
                            // 2. The reps are greater than overallMaxRepsAtCurrentWeight.
                            // 3. To avoid spamming on a totally new weight that is lighter than overallMaxWeight,
                            //    we require that they have done this weight before (overallMaxRepsAtCurrentWeight > 0),
                            //    OR if they haven't done it before but it is their overallMaxWeight (meaning weight is equal to overallMaxWeight).
                            val isRepsPR = !isWeightPR && (
                                (overallMaxRepsAtCurrentWeight > 0 && set.reps > overallMaxRepsAtCurrentWeight) ||
                                (overallMaxRepsAtCurrentWeight == 0 && set.weight == overallMaxWeight && overallMaxWeight > 0f)
                            )
                            
                            if (isWeightPR || isRepsPR) {
                                prSetIds.value = prSetIds.value + set.id
                                
                                val formattedMaxWeight = if (overallMaxWeight % 1 == 0f) "${overallMaxWeight.toInt()} kg" else "$overallMaxWeight kg"
                                val formattedSetWeight = if (set.weight % 1 == 0f) "${set.weight.toInt()} kg" else "${set.weight} kg"
                                
                                val previousValue = if (isWeightPR) {
                                    if (overallMaxWeight > 0f) formattedMaxWeight else "Nenhum"
                                } else {
                                    if (overallMaxRepsAtCurrentWeight > 0) "$overallMaxRepsAtCurrentWeight reps" else "Nenhum"
                                }
                                val newValue = if (isWeightPR) formattedSetWeight else "${set.reps} reps"
                                
                                // Regra de Significância do Toast de PR:
                                // Para não poluir e cansar iniciantes onde qualquer aumento mínimo gera toast flutuante,
                                // o toast expansivo aparece apenas quando o aumento for significativo:
                                // 1. Se for o primeiro peso já registrado (> 0f e anterior == 0f).
                                // 2. Para peso: aumento de carga >= 5% sobre a marca anterior (set.weight >= overallMaxWeight * 1.05f).
                                // 3. Para repetições: aumento de ao menos 2 repetições (set.reps >= overallMaxRepsAtCurrentWeight + 2).
                                // Aumentos menores ganham o ícone 🏆 discreto na própria série sem popup invasivo.
                                val isSignificantPR = if (isWeightPR) {
                                    overallMaxWeight == 0f || (set.weight >= overallMaxWeight * 1.05f)
                                } else {
                                    set.reps >= (overallMaxRepsAtCurrentWeight + 2)
                                }

                                if (isSignificantPR) {
                                    activePrCelebration.value = PrCelebration(
                                        exerciseName = ex.name,
                                        isWeightPR = isWeightPR,
                                        value = newValue,
                                        previousValue = previousValue,
                                        exerciseIndex = exerciseIndex,
                                        setIndex = setIndex,
                                        setId = set.id,
                                        reps = set.reps,
                                        weight = set.weight
                                    )
                                }
                            }
                            
                            val allSetsCompletedNow = currentSets.all { it.completed }
                            if (allSetsCompletedNow && !wasCompleted) {
                                val lastSetIndex = currentSets.lastIndex
                                val lastSet = currentSets[lastSetIndex]
                                activeRpePrompt.value = RpePrompt(
                                    exerciseIndex = exerciseIndex,
                                    setIndex = lastSetIndex,
                                    exerciseName = ex.name,
                                    reps = lastSet.reps,
                                    weight = lastSet.weight,
                                    setId = lastSet.id
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed during PR check or RPE flow", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "CRITICAL: crash caught in toggleSetCompletion", e)
        }
    }

    fun updateSetMetrics(exerciseIndex: Int, setIndex: Int, repsDelta: Int, weightDelta: Float) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val currentSets = ex.sets.toMutableList()
            if (setIndex in currentSets.indices) {
                val set = currentSets[setIndex]
                val oldReps = set.reps
                val oldWeight = set.weight
                val newReps = (oldReps + repsDelta).coerceAtLeast(1)
                val inferredEq = com.example.utils.WeightCalculator.inferEquipment(ex.name, ex.equipment)
                val hasCustomStep = com.example.utils.WeightCalculator.hasExplicitCustomStep(getApplication(), ex.name)
                val savedCategory = com.example.utils.WeightCalculator.getSavedEquipmentCategory(getApplication(), ex.name, ex.equipment)
                val isDumbbell = savedCategory.contains("halter", ignoreCase = true) ||
                    inferredEq.contains("halter", ignoreCase = true) ||
                    ex.equipment.contains("halter", ignoreCase = true)
                val newWeight = when {
                    weightDelta == 0f -> {
                        if (isDumbbell && !hasCustomStep) com.example.utils.WeightCalculator.roundToAvailableWeight(oldWeight, "Halteres") else oldWeight
                    }
                    hasCustomStep -> {
                        (java.lang.Math.round((oldWeight + weightDelta) * 100f) / 100f).coerceAtLeast(0f)
                    }
                    isDumbbell -> com.example.utils.WeightCalculator.stepWeightForEquipment(
                        currentWeight = oldWeight,
                        increase = weightDelta > 0f,
                        equipment = "Halteres",
                        exerciseName = ex.name,
                        context = getApplication()
                    )
                    else -> (java.lang.Math.round((oldWeight + weightDelta) * 100f) / 100f).coerceAtLeast(0f)
                }
                
                currentSets[setIndex] = set.copy(reps = newReps, weight = newWeight)
                
                // Determine if this is a progressive loading exercise (sets have different weights or explicit progressionType)
                val distinctWeights = ex.sets.map { it.weight }.distinct()
                val isProgressiveLoad = distinctWeights.size > 1 || 
                                       ex.progressionType.equals("Carga", ignoreCase = true) || 
                                       ex.progressionType.contains("progress", ignoreCase = true)
                
                // Update subsequent uncompleted sets only
                for (i in (setIndex + 1) until currentSets.size) {
                    val subSet = currentSets[i]
                    if (subSet.completed) continue
                    var subReps = subSet.reps
                    var subWeight = subSet.weight
                    
                    // Update reps
                    if (repsDelta != 0) {
                        subReps = newReps
                    }
                    
                    // Update weight
                    if (weightDelta != 0f) {
                        val rawSubWeight = if (isProgressiveLoad) {
                            val ratio = if (oldWeight > 0f) newWeight / oldWeight else 1f
                            (java.lang.Math.round(subSet.weight * ratio * 100f) / 100f).coerceAtLeast(0f)
                        } else {
                            (java.lang.Math.round(newWeight * 100f) / 100f).coerceAtLeast(0f)
                        }
                        subWeight = if (isDumbbell && !hasCustomStep) {
                            com.example.utils.WeightCalculator.roundToAvailableWeight(rawSubWeight, "Halteres")
                        } else {
                            rawSubWeight
                        }
                    }
                    
                    currentSets[i] = subSet.copy(reps = subReps, weight = subWeight)
                }
                
                currentExs[exerciseIndex] = ex.copy(sets = currentSets)
                activeExercises.value = currentExs
                persistActiveSessionToRoom()
                AudioSynth.playTickSound()

                // Trigger gym profile prompt if this is the first manual weight change
                if (weightDelta != 0f && !_promptedExercises.value.contains(ex.name) && !_gymConfigs.value.containsKey(ex.name)) {
                    activeGymPrompt.value = Pair(ex.name, newWeight)
                }
            }
        }
    }

    fun setSetMetrics(exerciseIndex: Int, setIndex: Int, reps: Int, weight: Float) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val currentSets = ex.sets.toMutableList()
            if (setIndex in currentSets.indices) {
                val set = currentSets[setIndex]
                val oldReps = set.reps
                val oldWeight = set.weight
                val newReps = reps.coerceAtLeast(1)
                val inferredEq = com.example.utils.WeightCalculator.inferEquipment(ex.name, ex.equipment)
                val isDumbbell = inferredEq.contains("halter", ignoreCase = true) || ex.equipment.contains("halter", ignoreCase = true)
                val newWeight = if (isDumbbell) {
                    com.example.utils.WeightCalculator.roundToAvailableWeight(weight.coerceAtLeast(1f), "Halteres")
                } else {
                    weight.coerceAtLeast(0f)
                }
                
                currentSets[setIndex] = set.copy(reps = newReps, weight = newWeight)
                
                // If it is STANDARD, propagate to subsequent uncompleted sets
                if (set.setType == SetType.STANDARD) {
                    for (i in (setIndex + 1) until currentSets.size) {
                        val subSet = currentSets[i]
                        if (!subSet.completed) {
                            var subReps = subSet.reps
                            var subWeight = subSet.weight
                            
                            if (newReps != oldReps) {
                                subReps = newReps
                            }
                            if (newWeight != oldWeight) {
                                subWeight = newWeight
                            }
                            currentSets[i] = subSet.copy(reps = subReps, weight = subWeight)
                        }
                    }
                }
                
                currentExs[exerciseIndex] = ex.copy(sets = currentSets)
                activeExercises.value = currentExs
                persistActiveSessionToRoom()
                AudioSynth.playTickSound()

                // Trigger gym profile prompt if this is the first manual weight change
                if (newWeight != oldWeight && !_promptedExercises.value.contains(ex.name) && !_gymConfigs.value.containsKey(ex.name)) {
                    activeGymPrompt.value = Pair(ex.name, newWeight)
                }
            }
        }
    }

    fun updateExerciseSetType(exerciseIndex: Int, newType: SetType) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val updatedSets = ex.sets.map { it.copy(setType = newType) }
            currentExs[exerciseIndex] = ex.copy(sets = updatedSets)
            activeExercises.value = currentExs
            AudioSynth.playTickSound()
        }
    }

    fun updateExerciseRestTime(exerciseIndex: Int, restSeconds: Int) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            currentExs[exerciseIndex] = ex.copy(restTimeSeconds = restSeconds)
            activeExercises.value = currentExs
            AudioSynth.playTickSound()
            
            // Save the customized rest time so it is remembered!
            saveRestTimeForExercise(ex.name, restSeconds)
            
            // Se o cronômetro de descanso já estiver rodando, recalcula o restante descontando o que já foi descansado!
            if (isRestTimerActive.value) {
                val currentRemaining = com.example.services.WorkoutServiceState.computeRemainingRestSeconds()
                val elapsedRestSeconds = (restTimeTotal.value - currentRemaining).coerceAtLeast(0)
                val newRemainingSeconds = (restSeconds - elapsedRestSeconds).coerceAtLeast(1)
                com.example.services.WorkoutServiceState.startRestClock(newRemainingSeconds)
                restTimeTotal.value = restSeconds
                
                try {
                    val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
                        action = com.example.services.WorkoutForegroundService.ACTION_MODIFY_REST
                        putExtra(com.example.services.WorkoutForegroundService.EXTRA_REST_DURATION, 0)
                    }
                    sendServiceCommand(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to update rest duration in service", e)
                }
            }
        }
    }

    fun updateExerciseProgression(exerciseId: String, newProgression: String) {
        try {
            val mappedProgression = com.example.data.model.ProgressionType.fromString(newProgression)
            val currentExs = activeExercises.value.toMutableList()
            var updated = false
            for (i in currentExs.indices) {
                val ex = currentExs[i]
                if (ex.id == exerciseId || ex.exerciseId == exerciseId) {
                    ex.progressionType = mappedProgression.displayName
                    currentExs[i] = ex
                    updated = true
                }
            }
            if (updated) {
                activeExercises.value = currentExs
                com.example.services.telemetry.Telemetry.log(
                    com.example.data.model.TelemetryEventType.PROGRESSION_CHANGED,
                    mapOf(
                        "exerciseId" to exerciseId,
                        "newProgression" to mappedProgression.name
                    )
                )
            }
        } catch (e: Exception) {
            com.example.services.telemetry.Telemetry.log(
                com.example.data.model.TelemetryEventType.ERROR,
                mapOf(
                    "error" to (e.message ?: "Failed to update exercise progression"),
                    "exerciseId" to exerciseId,
                    "newProgression" to newProgression
                )
            )
        }
    }

    fun deleteSetFromExercise(exerciseIndex: Int, setIndex: Int) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val currentSets = ex.sets.toMutableList()
            if (setIndex in currentSets.indices && currentSets.size > 1) {
                currentSets.removeAt(setIndex)
                // Re-index remaining sets
                val reindexedSets = currentSets.mapIndexed { index, s -> s.copy(index = index + 1) }
                currentExs[exerciseIndex] = ex.copy(sets = reindexedSets)
                activeExercises.value = currentExs
                AudioSynth.playTickSound()
            }
        }
    }

    fun addSetToExercise(exerciseIndex: Int) {
        val currentExs = activeExercises.value.toMutableList()
        if (exerciseIndex in currentExs.indices) {
            val ex = currentExs[exerciseIndex]
            val currentSets = ex.sets.toMutableList()
            val lastSet = currentSets.lastOrNull() ?: WorkoutSet("s_${UUID.randomUUID()}", 0, 10, 20f)
            val newSet = WorkoutSet(
                id = "set_${UUID.randomUUID()}",
                index = currentSets.size + 1,
                reps = lastSet.reps,
                weight = lastSet.weight,
                completed = false
            )
            currentSets.add(newSet)
            currentExs[exerciseIndex] = ex.copy(sets = currentSets)
            activeExercises.value = currentExs
            AudioSynth.playTickSound()
        }
    }

    private fun sendServiceCommand(intent: Intent) {
        try {
            val canStartForeground = intent.action == com.example.services.WorkoutForegroundService.ACTION_START_WORKOUT ||
                intent.action == com.example.services.WorkoutForegroundService.ACTION_START_REST
            val isRunning = com.example.services.WorkoutForegroundService.isServiceRunning
            if (!canStartForeground && (!isRunning || !com.example.services.WorkoutForegroundService.isWorkoutActive)) {
                return
            }
            if (canStartForeground) {
                com.example.services.WorkoutForegroundService.isWorkoutActive = true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && canStartForeground && !isRunning) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainViewModel.sendServiceCommand")
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed to send command to foreground service: ${intent.action}", e)
        }
    }

    private fun triggerRestTimer(seconds: Int) {
        com.example.services.WorkoutServiceState.startRestClock(seconds)
        
        val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
            action = com.example.services.WorkoutForegroundService.ACTION_START_REST
            putExtra(com.example.services.WorkoutForegroundService.EXTRA_REST_DURATION, seconds)
        }
        sendServiceCommand(intent)
    }

    fun modifyRestTimer(secondsDelta: Int) {
        if (isRestTimerActive.value) {
            com.example.services.WorkoutServiceState.modifyRestClock(secondsDelta)
            val newRemaining = restTimeRemaining.value
            if (newRemaining <= 0) {
                com.example.services.WorkoutServiceState.resetTimers()
                val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
                    action = com.example.services.WorkoutForegroundService.ACTION_SKIP_REST
                }
                sendServiceCommand(intent)
            } else {
                val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
                    action = com.example.services.WorkoutForegroundService.ACTION_MODIFY_REST
                    putExtra(com.example.services.WorkoutForegroundService.EXTRA_REST_DURATION, secondsDelta)
                }
                sendServiceCommand(intent)
            }
            AudioSynth.playTickSound()
        }
    }

    fun skipRestTimer() {
        com.example.services.WorkoutServiceState.resetTimers()
        com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        val intent = Intent(getApplication(), com.example.services.WorkoutForegroundService::class.java).apply {
            action = com.example.services.WorkoutForegroundService.ACTION_SKIP_REST
        }
        sendServiceCommand(intent)
        AudioSynth.playTickSound()
    }

    fun finishActiveWorkout(isEarly: Boolean = false, autoCompleteIfEmpty: Boolean = true) {
        viewModelScope.launch {
            try {
                val workout = activeWorkout.value ?: return@launch
                var exercises = activeExercises.value
                
                // Calculate total volume lifted
                var totalWeightLifted = 0f
                var completedCount = 0
                for (ex in exercises) {
                    for (s in ex.sets) {
                        if (s.completed) {
                            totalWeightLifted += com.example.utils.WeightCalculator.calculateSetVolumeKg(
                                getApplication(),
                                ex.name,
                                ex.equipment,
                                s.weight,
                                s.reps
                            )
                            completedCount++
                        }
                    }
                }

                // Se nenhuma série foi marcada manualmente mas o usuário confirmou a conclusão do treino,
                // marca as séries planejadas como concluídas para garantir a persistência no Diário (Room + GymHistory)
                if (completedCount == 0 && autoCompleteIfEmpty && exercises.isNotEmpty()) {
                    exercises = exercises.map { ex ->
                        ex.copy(sets = ex.sets.map { s -> s.copy(completed = true) })
                    }
                    activeExercises.value = exercises
                    for (ex in exercises) {
                        for (s in ex.sets) {
                            totalWeightLifted += com.example.utils.WeightCalculator.calculateSetVolumeKg(
                                getApplication(),
                                ex.name,
                                ex.equipment,
                                s.weight,
                                s.reps
                            )
                            completedCount++
                        }
                    }
                }

                // Validação: não é permitido concluir uma sessão sem pelo menos uma série registrada
                if (completedCount == 0) {
                    showNoSetsDialog.value = true
                    return@launch
                }

                val completedExsCount = exercises.count { ex -> ex.sets.any { s -> s.completed } }
                
                val totalPlannedSetsVal = exercises.sumOf { it.sets.size }
                var estSecs = 0
                for (ex in exercises) {
                    estSecs += 60
                    for (s in ex.sets) {
                        estSecs += 45 + (ex.restTimeSeconds)
                    }
                }
                var fatigueUsedVal = 0.0
                for (ex in exercises) {
                    val baseEx = com.example.constants.TemplatesAndExercises.BASE_EXERCISES.firstOrNull { it.id == ex.exerciseId || it.name.trim().equals(ex.name.trim(), ignoreCase = true) }
                    if (baseEx != null) {
                        fatigueUsedVal += baseEx.fatigueCost
                    }
                }
                val userLvl = userProfile.value?.nivelExperiencia?.lowercase() ?: "iniciante"
                val fatigueLimitVal = if (userLvl.contains("iniciante") || userLvl.contains("basic")) 5.5
                                      else if (userLvl.contains("avançado") || userLvl.contains("advanced")) 12.0
                                      else 8.5

                lastCompletedWorkoutSummary.value = CompletedWorkoutSummary(
                    workoutName = workout.name,
                    durationSeconds = workoutDurationSeconds.value.toInt(),
                    totalVolume = totalWeightLifted,
                    exercisesCompletedCount = completedExsCount,
                    setsCompletedCount = completedCount,
                    isEarly = isEarly,
                    totalPlannedSets = totalPlannedSetsVal,
                    estimatedDurationSeconds = estSecs,
                    fatigueUsed = fatigueUsedVal,
                    fatigueLimit = fatigueLimitVal,
                    completedExercises = exercises
                )

                // Pack final exercises back to JSON
                val finalJson = JsonUtils.serializeWorkoutExercises(exercises)

                val historyEntry = WorkoutHistoryEntry(
                    id = "hist_${UUID.randomUUID()}",
                    workoutId = workout.id,
                    workoutName = workout.name,
                    durationSeconds = workoutDurationSeconds.value,
                    totalWeight = totalWeightLifted,
                    exercisesCompletedCount = completedExsCount,
                    exercisesJson = finalJson
                )

                // Save history safely
                var saveSucceeded = false
                try {
                    repository.addHistoryEntry(historyEntry)
                    saveSucceeded = true
                } catch (dbEx: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "DB Error adding history entry", dbEx)
                }

                // Gravação Imediata ao Finalizar: Salva objeto completo GymWorkoutSession de forma persistente
                try {
                    val gymSession = com.example.data.model.GymWorkoutSession(
                        id = historyEntry.id,
                        date = historyEntry.date,
                        workoutName = workout.name,
                        durationSeconds = workoutDurationSeconds.value.toInt(),
                        totalVolumeKg = totalWeightLifted,
                        totalSets = totalPlannedSetsVal,
                        completedSets = completedCount,
                        exercises = exercises.map { ex ->
                            com.example.data.model.GymExerciseRecord(
                                name = ex.name,
                                equipment = ex.equipment,
                                category = ex.category,
                                sets = ex.sets.map { s ->
                                    com.example.data.model.GymSetRecord(
                                        setIndex = s.index,
                                        weight = s.weight,
                                        reps = s.reps,
                                        completed = s.completed
                                    )
                                },
                                notes = ex.notes
                            )
                        },
                        notes = exercises.filter { it.notes.isNotBlank() }
                            .joinToString(separator = " • ") { "${it.name}: ${it.notes}" }
                    )
                    com.example.data.repository.GymHistoryRepository.saveSession(getApplication(), gymSession)
                } catch (gymEx: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error saving GymWorkoutSession", gymEx)
                }

                if (saveSucceeded) {
                    // Log Telemetry: WORKOUT_FINISHED
                    com.example.services.telemetry.Telemetry.log(
                        com.example.data.model.TelemetryEventType.WORKOUT_FINISHED,
                        mapOf("workout_id" to workout.id, "workout_name" to workout.name, "duration_seconds" to workoutDurationSeconds.value)
                    )

                    // Log Telemetry: RECOVERY_UPDATED
                    try {
                        val currentHist = repository.historyFlow.first()
                        val calculatedRecovery = RecoveryAndTrophies.calculateRecovery(currentHist)
                        com.example.services.telemetry.Telemetry.log(
                            com.example.data.model.TelemetryEventType.RECOVERY_UPDATED,
                            mapOf("recovery_status" to calculatedRecovery.map { "${it.name}:${it.recoveryPercent}" })
                        )
                    } catch (e: Exception) {
                        com.example.services.telemetry.Telemetry.log(
                            com.example.data.model.TelemetryEventType.ERROR,
                            mapOf("message" to "Recovery calculation failed", "error" to (e.localizedMessage ?: "Unknown"))
                        )
                    }

                    // Log Telemetry: HOME_REFRESHED
                    com.example.services.telemetry.Telemetry.log(
                        com.example.data.model.TelemetryEventType.HOME_REFRESHED,
                        mapOf("previous_screen" to "PLAYER", "current_screen" to "HOME")
                    )
                }

                // RPE Progression Engine: update the original workout template
                try {
                    val originalWorkout = activeWorkout.value
                    if (originalWorkout != null) {
                        val templateExs = JsonUtils.parseWorkoutExercises(originalWorkout.exercisesJson).toMutableList()
                        var anyUpdated = false
                        
                        for (i in templateExs.indices) {
                            val tempEx = templateExs[i]
                            val activeEx = exercises.find { it.exerciseId == tempEx.exerciseId || it.name.equals(tempEx.name, ignoreCase = true) }
                            if (activeEx != null) {
                                val updatedSets = tempEx.sets.mapIndexed { setIdx, tempSet ->
                                    val activeSet = activeEx.sets.getOrNull(setIdx)
                                    if (activeSet != null && activeSet.completed) {
                                        val matchingBaseEx = com.example.constants.TemplatesAndExercises.ALL_EXERCISES.find { it.id == tempEx.exerciseId || it.name.equals(tempEx.name, ignoreCase = true) }
                                        val increment = matchingBaseEx?.weightIncrement ?: 2.5f
                                        when (activeSet.rpe) {
                                            "3+", "Fácil", "Leve" -> {
                                                anyUpdated = true
                                                tempSet.copy(
                                                    weight = activeSet.weight + increment,
                                                    reps = (activeSet.reps + 1).coerceAtMost(20)
                                                )
                                            }
                                            "1-2", "Moderado" -> {
                                                anyUpdated = true
                                                if (activeSet.reps >= 14) {
                                                    tempSet.copy(weight = activeSet.weight + increment, reps = 10)
                                                } else {
                                                    tempSet.copy(weight = activeSet.weight, reps = activeSet.reps + 2)
                                                }
                                            }
                                            "Difícil", "Difícil (Ideal)", "Alto" -> {
                                                anyUpdated = true
                                                tempSet.copy(weight = activeSet.weight, reps = activeSet.reps)
                                            }
                                            "Máximo", "Máximo / Falha", "Muito Alto" -> {
                                                anyUpdated = true
                                                if (activeSet.reps < tempSet.reps && activeSet.weight >= increment) {
                                                    tempSet.copy(weight = (activeSet.weight - increment).coerceAtLeast(0f))
                                                } else {
                                                    tempSet.copy(weight = activeSet.weight, reps = activeSet.reps)
                                                }
                                            }
                                            else -> tempSet
                                        }
                                    } else {
                                        tempSet
                                    }
                                }
                                templateExs[i] = tempEx.copy(sets = updatedSets)
                            }
                        }
                        
                        if (anyUpdated) {
                            val updatedJson = JsonUtils.serializeWorkoutExercises(templateExs)
                            val updatedWorkout = originalWorkout.copy(exercisesJson = updatedJson)
                            try {
                                repository.createWorkout(updatedWorkout)
                            } catch (dbEx: Exception) {
                                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "DB Error updating workout template", dbEx)
                            }
                            android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Workout template '${originalWorkout.name}' updated with RPE progression!")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error applying RPE progression: ${e.message}", e)
                }

                // Stop foreground service and clear notifications immediately
                com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())

                // End session (prepare for gym save dialog first)
                exercisesToSaveInGym.value = exercises.map { it.exerciseId }
                workoutFlowState.value = WorkoutFlowState.Finished
                isRestTimerActive.value = false
                isTempoTimerActive.value = false
                tickerJob?.cancel()
                showGymSavePrompt.value = true

                val finishSid = currentSessionId.value
                val finishTime = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    sessionUpdateMutex.withLock {
                        try {
                            val sessionDb = finishSid?.let { repository.getWorkoutSessionById(it) } 
                                ?: repository.getActiveWorkoutSession()
                            val finishDur = if (sessionDb != null) {
                                WorkoutTimeUtils.calculateMonotonicDurationSeconds(sessionDb.startTimeMonotonic, sessionDb.startTime, sessionDb.monotonicStartTimeNanos)
                            } else {
                                maxOf(0L, workoutDurationSeconds.value)
                            }
                            val targetId = finishSid ?: sessionDb?.id
                            if (targetId != null) {
                                val success = if (sessionDb != null) {
                                    repository.updateWorkoutSessionStatusVersioned(
                                        sessionId = targetId,
                                        status = WorkoutSessionStatus.CONCLUIDO.rawValue,
                                        newVersion = sessionDb.version + 1,
                                        completedAt = finishTime,
                                        durationSeconds = finishDur
                                    )
                                } else {
                                    repository.updateWorkoutSessionStatus(
                                        sessionId = targetId,
                                        status = WorkoutSessionStatus.CONCLUIDO.rawValue,
                                        endTime = finishTime,
                                        durationSeconds = finishDur
                                    )
                                }
                                if (success) {
                                    com.example.engine.WorkoutEngine.clearCache()
                                    withContext(Dispatchers.Main) {
                                        homeRecommendationVersion.value += 1
                                        lastSetCompletionTimes.clear()
                                    }
                                } else {
                                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Falha na transição para CONCLUIDO (status não era ATIVO ou conflito de concorrência)")
                                }
                            } else {
                                val fallbackSession = com.example.data.model.WorkoutSessionEntity(
                                    id = historyEntry.id,
                                    workoutId = workout.id,
                                    workoutName = workout.name,
                                    status = WorkoutSessionStatus.CONCLUIDO.rawValue,
                                    startTime = maxOf(1L, finishTime - (workoutDurationSeconds.value * 1000L)),
                                    endTime = finishTime,
                                    completedAt = finishTime,
                                    durationSeconds = maxOf(60L, workoutDurationSeconds.value),
                                    exercisesJson = finalJson
                                )
                                repository.saveWorkoutSession(fallbackSession)
                                com.example.engine.WorkoutEngine.clearCache()
                                withContext(Dispatchers.Main) {
                                    homeRecommendationVersion.value += 1
                                    lastSetCompletionTimes.clear()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed updating session to CONCLUIDO in Room", e)
                        }
                    }
                }
                currentSessionId.value = null
                clearSavedActiveSession()

                val activeGym = activeGymProfile.value
                if (activeGym != null) {
                    val merged = (activeGym.availableExercisesIds + exercises.map { it.exerciseId }).distinct()
                    updateGymProfile(activeGym.copy(availableExercisesIds = merged))
                }
                isPreSessionActive.value = false
                showGymSavePrompt.value = false
                finalizeActiveWorkoutAndNavigateToCompleted()

                // Play celebratory sound
                try {
                    com.example.utils.MaxSoundManager.playWorkoutFinished(getApplication())
                } catch (e: Exception) {
                    android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Failed playing workout finished sound", e)
                }
            } catch (globalEx: Exception) {
                android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Global error inside finishActiveWorkout", globalEx)
                // Fallback clean-up to ensure screen does not freeze or block user
                tickerJob?.cancel()
                activeWorkout.value = null
                activeExercises.value = emptyList()
                exerciseProgressionSuggestions.value = emptyMap()
                workoutFlowState.value = WorkoutFlowState.Finished
                activeScreen.value = AppScreen.CONCLUIDO
            }
        }
    }

    fun dismissNoSetsDialog() {
        showNoSetsDialog.value = false
    }

    fun endWorkoutAsNotPerformed() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    tickerJob?.cancel()
                    try {
                        com.example.utils.MaxSoundManager.stopAllSounds()
                    } catch (e: Exception) {}
                    com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())
                }

                val sid = currentSessionId.value
                val sessionDb = sid?.let { repository.getWorkoutSessionById(it) } ?: repository.getActiveWorkoutSession()
                if (sessionDb != null) {
                    val finishTime = System.currentTimeMillis()
                    val durationSec = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
                        sessionDb.startTimeMonotonic,
                        sessionDb.startTime,
                        sessionDb.monotonicStartTimeNanos
                    )
                    val success = repository.updateWorkoutSessionStatusVersioned(
                        sessionId = sessionDb.id,
                        status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                        newVersion = sessionDb.version + 1,
                        completedAt = finishTime,
                        durationSeconds = durationSec
                    )
                    if (!success) {
                        android.util.Log.e("MainViewModel", "Falha ao transicionar para NAO_REALIZADO (status não era ATIVO ou versão em conflito)")
                    }
                }

                withContext(Dispatchers.Main) {
                    currentSessionId.value = null
                    clearSavedActiveSession()
                    activeWorkout.value = null
                    activeExercises.value = emptyList()
                    exerciseProgressionSuggestions.value = emptyMap()
                    workoutFlowState.value = WorkoutFlowState.Abandoned
                    showNoSetsDialog.value = false
                    activeScreen.value = AppScreen.HOME
                    checkActiveSessions()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Erro ao encerrar treino não realizado", e)
                withContext(Dispatchers.Main) {
                    showNoSetsDialog.value = false
                    activeScreen.value = AppScreen.HOME
                }
            }
        }
    }

    fun requestDiscardSession(session: WorkoutSessionEntity) {
        sessionToDiscardConfirm.value = session
    }

    fun requestDiscardCurrentActiveSession() {
        val sid = currentSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            val session = sid?.let { repository.getWorkoutSessionById(it) }
                ?: repository.getActiveWorkoutSession()
                ?: activeSessionsList.value.firstOrNull()
            withContext(Dispatchers.Main) {
                if (session != null) {
                    sessionToDiscardConfirm.value = session
                } else {
                    cancelActiveWorkoutDirect()
                }
            }
        }
    }

    fun cancelDiscardConfirmation() {
        sessionToDiscardConfirm.value = null
    }

    fun executeDiscardSession() {
        val session = sessionToDiscardConfirm.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val discardTime = System.currentTimeMillis()
                val durationSec = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
                    session.startTimeMonotonic,
                    session.startTime,
                    session.monotonicStartTimeNanos
                )
                val success = repository.updateWorkoutSessionStatusVersioned(
                    sessionId = session.id,
                    status = WorkoutSessionStatus.DESCARTADO.rawValue,
                    newVersion = session.version + 1,
                    completedAt = discardTime,
                    durationSeconds = durationSec
                )
                if (!success) {
                    android.util.Log.e("MainViewModel", "Falha na transição para DESCARTADO (status não era ATIVO ou versão em conflito)")
                }

                if (currentSessionId.value == session.id) {
                    withContext(Dispatchers.Main) {
                        cancelActiveWorkoutDirect()
                    }
                }

                withContext(Dispatchers.Main) {
                    sessionToDiscardConfirm.value = null
                }
                checkActiveSessions()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Erro ao descartar sessão", e)
                withContext(Dispatchers.Main) {
                    sessionToDiscardConfirm.value = null
                }
            }
        }
    }

    fun cancelActiveWorkoutDirect() {
        tickerJob?.cancel()
        isRestTimerActive.value = false
        restTimeRemaining.value = 0
        isTempoTimerActive.value = false
        tempoTimeRemaining.value = 0
        try {
            com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication())
        } catch (e: Exception) {}

        com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(getApplication())

        currentSessionId.value = null
        clearSavedActiveSession()
        activeWorkout.value = null
        activeExercises.value = emptyList()
        exerciseProgressionSuggestions.value = emptyMap()
        workoutFlowState.value = WorkoutFlowState.Abandoned
        activeScreen.value = AppScreen.HOME
        AudioSynth.playTickSound()
    }

    fun cancelActiveWorkout() {
        requestDiscardCurrentActiveSession()
    }

    fun tryFinishActiveWorkout(onShowPartialConfirmation: () -> Unit) {
        val exercises = activeExercises.value
        if (exercises.isEmpty()) {
            cancelActiveWorkout()
            return
        }
        val allCompleted = exercises.all { ex -> ex.sets.any { s -> s.completed } }
        if (allCompleted) {
            finishActiveWorkout(isEarly = false)
        } else {
            onShowPartialConfirmation()
        }
    }

    // --- PROFILE AND REGISTRATION CONTROL ---
    fun loadPresetProfile(preset: com.example.data.model.UserProfileData) {
        viewModelScope.launch {
            com.example.utils.WeightCalculator.activeUserProfile = preset
            com.example.engine.WorkoutEngine.clearCache()
            repository.saveProfile(preset)
            AudioSynth.playTickSound()
        }
    }

    fun completeInitialProfileSetup(
        name: String,
        gender: String,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        weeklyGoal: Int
    ) {
        val newProfile = buildInitialUserProfile(
            name = name,
            gender = gender,
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            weeklyGoal = weeklyGoal
        ) ?: return

        viewModelScope.launch {
            com.example.utils.WeightCalculator.activeUserProfile = newProfile
            com.example.engine.WorkoutEngine.clearCache()
            repository.saveProfile(newProfile)
            AudioSynth.playTickSound()
        }
    }

    fun updateProfileData(name: String, gender: String, age: Int, height: Int, weight: Float, experience: String, location: String, weeklyGoal: Int, iconStyle: String) {
        viewModelScope.launch {
            val updated = userProfile.value.copy(
                nome = name,
                sexo = gender,
                idade = age,
                altura = height,
                heightCm = height,
                peso = weight,
                weightKg = weight,
                nivelExperiencia = experience,
                localTreino = location,
                metaSemanal = weeklyGoal,
                iconStyle = iconStyle
            )
            com.example.utils.WeightCalculator.activeUserProfile = updated
            com.example.engine.WorkoutEngine.clearCache()
            repository.saveProfile(updated)
            AudioSynth.playTickSound()
        }
    }

    fun deleteRunningSession(sessionId: String) {
        viewModelScope.launch {
            val deletedSession = runningSessions.value.find { it.id == sessionId }
            val remainingSessions = runningSessions.value.filter { it.id != sessionId }
            repository.deleteRunningSession(sessionId)
            recalculateAndPersistShoeMileage(remainingSessions)
            if (deletedSession != null &&
                deletedSession.weekNumber in 1..10 &&
                !deletedSession.isGentleMode &&
                deletedSession.sessionType != "GENTLE_WALK" &&
                !com.example.engine.RunningProgramManager.isFreeOrRaceSessionType(deletedSession.sessionType) &&
                !com.example.engine.RunningProgramManager.isEffectiveFreeRun(deletedSession)
            ) {
                // Restaura imediatamente o dia da planilha correspondente ao treino excluído para pendente
                setManualPlanDayOverride(deletedSession.weekNumber, deletedSession.dayNumber)
            }
            homeRecommendationVersion.value++
            AudioSynth.playTickSound()
        }
    }

    fun deleteGymWorkoutSession(sessionId: String) {
        viewModelScope.launch {
            val targetSession = gymWorkoutSessions.value.find { it.id == sessionId }
            com.example.data.repository.GymHistoryRepository.deleteSession(getApplication(), sessionId)
            repository.deleteHistoryEntry(sessionId)
            if (targetSession != null) {
                val matchingEntries = history.value.filter { entry ->
                    entry.id == sessionId ||
                    (entry.workoutName.equals(targetSession.workoutName, ignoreCase = true) &&
                        kotlin.math.abs(entry.date - targetSession.date) < 60_000L)
                }
                matchingEntries.forEach { entry ->
                    repository.deleteHistoryEntry(entry.id)
                }
            }
            com.example.data.repository.ExerciseHistoryRepository.clearCache()
            homeRecommendationVersion.value++
            AudioSynth.playTickSound()
        }
    }

    fun resetAndClearAllHistory() {
        viewModelScope.launch {
            com.example.data.repository.GymHistoryRepository.clearAllSessions(getApplication())
            repository.clearHistory()
            com.example.data.repository.ExerciseHistoryRepository.clearCache()
            AudioSynth.playTickSound()
        }
    }

    fun saveBioimpedanceRecord(record: BioimpedanceRecord) {
        viewModelScope.launch {
            val profile = userProfile.value
            val currentList = getBioimpedanceHistory(profile.bioimpedanciaHistoricoJson).toMutableList()
            
            // Check unarchived items in currentList. If size is >= 2, archive the oldest.
            val unarchived = currentList.filter { !it.archived }.sortedBy { it.date }
            if (unarchived.size >= 2) {
                val oldest = unarchived.first()
                val oldestIndex = currentList.indexOfFirst { it.id == oldest.id }
                if (oldestIndex != -1) {
                    currentList[oldestIndex] = currentList[oldestIndex].copy(archived = true)
                }
            }
            
            // Add new record as unarchived
            currentList.add(record.copy(archived = false))
            
            val historyJson = serializeBioimpedanceHistory(currentList)
            val updated = profile.copy(
                peso = record.peso,
                imc = record.imc,
                percentualGordura = record.percentualGordura,
                taxaMuscular = record.taxaMuscular,
                massaMagra = record.massaMagra,
                gorduraSubcutanea = record.gorduraSubcutanea,
                gorduraVisceral = record.gorduraVisceral,
                aguaCorporal = record.aguaCorporal,
                massaMuscularEsqueletica = record.massaMuscularEsqueletica,
                massaMuscular = record.massaMuscular,
                massaOssea = record.massaOssea,
                proteina = record.proteina,
                tmb = record.tmb,
                idadeMetabolica = record.idadeMetabolica,
                bioimpedanciaHistoricoJson = historyJson
            )
            repository.saveProfile(updated)
            AudioSynth.playChimeSound()
        }
    }

    fun deleteBioimpedanceRecord(recordId: String) {
        viewModelScope.launch {
            val profile = userProfile.value
            val currentList = getBioimpedanceHistory(profile.bioimpedanciaHistoricoJson).toMutableList()
            currentList.removeAll { it.id == recordId }
            val historyJson = serializeBioimpedanceHistory(currentList)
            
            val latest = currentList.filter { !it.archived }.maxByOrNull { it.date }
            val updated = if (latest != null) {
                profile.copy(
                    peso = latest.peso,
                    imc = latest.imc,
                    percentualGordura = latest.percentualGordura,
                    taxaMuscular = latest.taxaMuscular,
                    massaMagra = latest.massaMagra,
                    gorduraSubcutanea = latest.gorduraSubcutanea,
                    gorduraVisceral = latest.gorduraVisceral,
                    aguaCorporal = latest.aguaCorporal,
                    massaMuscularEsqueletica = latest.massaMuscularEsqueletica,
                    massaMuscular = latest.massaMuscular,
                    massaOssea = latest.massaOssea,
                    proteina = latest.proteina,
                    tmb = latest.tmb,
                    idadeMetabolica = latest.idadeMetabolica,
                    bioimpedanciaHistoricoJson = historyJson
                )
            } else {
                profile.copy(bioimpedanciaHistoricoJson = historyJson)
            }
            repository.saveProfile(updated)
        }
    }

    fun getBioimpedanceHistory(json: String): List<BioimpedanceRecord> {
        if (json.isEmpty() || json == "[]") return emptyList()
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, BioimpedanceRecord::class.java)
            val adapter = com.example.utils.JsonUtils.moshi.adapter<List<BioimpedanceRecord>>(type)
            return adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun serializeBioimpedanceHistory(list: List<BioimpedanceRecord>): String {
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, BioimpedanceRecord::class.java)
            val adapter = com.example.utils.JsonUtils.moshi.adapter<List<BioimpedanceRecord>>(type)
            return adapter.toJson(list)
        } catch (e: Exception) {
            return "[]"
        }
    }

    fun validateAndFixExtractedData(
        raw: BioimpedanceRecord,
        profile: UserProfileData
    ): BioimpedanceRecord {
        val errors = mutableListOf<String>()

        // 1. VALIDAÇÃO DO PESO (Ancorar no perfil +- 5kg para evitar leituras bizarras / OCR)
        val peso = if (raw.peso in (profile.peso - 5.0f)..(profile.peso + 5.0f)) {
            raw.peso
        } else {
            errors.add("Peso ${raw.peso} fora do esperado (${profile.peso})")
            profile.peso // Fallback para peso atual do perfil
        }

        // 2. VALIDAÇÃO DA GORDURA (Ancorar no perfil +- 5% para evitar erros graves de OCR)
        val gordura = if (raw.percentualGordura in (profile.percentualGordura - 5.0f)..(profile.percentualGordura + 5.0f)) {
            raw.percentualGordura
        } else {
            errors.add("Gordura ${raw.percentualGordura}% fora do esperado (${profile.percentualGordura}%)")
            profile.percentualGordura // Fallback para gordura atual do perfil
        }

        // 3. RECALCULAR IMC se inconsistente com Peso e Altura
        val alturaMetros = profile.altura / 100.0f
        val imcCalculado = if (alturaMetros > 0) peso / (alturaMetros * alturaMetros) else profile.imc
        val imc = if (java.lang.Math.abs(raw.imc - imcCalculado) < 1.5f) raw.imc else imcCalculado

        // 4. VERIFICAR CONSISTÊNCIA DA MASSA MAGRA
        val massaMagraEsperada = peso * (1.0f - (gordura / 100.0f))
        val massaMagra = if (raw.massaMagra > 0f && java.lang.Math.abs(raw.massaMagra - massaMagraEsperada) < 5.0f) {
            raw.massaMagra
        } else {
            massaMagraEsperada
        }

        // 5. VALIDAR IDADE METABÓLICA
        val idadeMetabolica = if (raw.idadeMetabolica in 12..100) raw.idadeMetabolica else profile.idade

        // Outros campos: preencher com o baseline do perfil se vierem vazios/zerados (evita apagar dados existentes)
        val taxaMuscular = if (raw.taxaMuscular > 0f) raw.taxaMuscular else profile.taxaMuscular
        val gorduraSubcutanea = if (raw.gorduraSubcutanea > 0f) raw.gorduraSubcutanea else profile.gorduraSubcutanea
        val gorduraVisceral = if (raw.gorduraVisceral > 0f) raw.gorduraVisceral else profile.gorduraVisceral
        val aguaCorporal = if (raw.aguaCorporal > 0f) raw.aguaCorporal else profile.aguaCorporal
        val massaMuscularEsqueletica = if (raw.massaMuscularEsqueletica > 0f) raw.massaMuscularEsqueletica else profile.massaMuscularEsqueletica
        val massaMuscular = if (raw.massaMuscular > 0f) raw.massaMuscular else profile.massaMuscular
        val massaOssea = if (raw.massaOssea > 0f) raw.massaOssea else profile.massaOssea
        val proteina = if (raw.proteina > 0f) raw.proteina else profile.proteina
        val tmb = if (raw.tmb > 0) raw.tmb else profile.tmb

        android.util.Log.d("Bioimpedance", "Validação concluída com ${errors.size} correções. Erros: $errors")

        return raw.copy(
            peso = peso,
            imc = imc,
            percentualGordura = gordura,
            taxaMuscular = taxaMuscular,
            massaMagra = massaMagra,
            gorduraSubcutanea = gorduraSubcutanea,
            gorduraVisceral = gorduraVisceral,
            aguaCorporal = aguaCorporal,
            massaMuscularEsqueletica = massaMuscularEsqueletica,
            massaMuscular = massaMuscular,
            massaOssea = massaOssea,
            proteina = proteina,
            tmb = tmb,
            idadeMetabolica = idadeMetabolica
        )
    }

    fun checkBiologicalConsistency(record: BioimpedanceRecord): Boolean {
        return com.example.services.domain.HealthEngine.checkBiologicalConsistency(record)
    }

    fun parseBioimpedanceRecordFromJson(json: String): BioimpedanceRecord {
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = com.example.utils.JsonUtils.moshi.adapter<Map<String, Any>>(type)
            val rootMap = mapAdapter.fromJson(json) ?: throw Exception("Formato inválido de JSON")
            
            if (rootMap.isEmpty()) {
                throw Exception("Nenhum dado pôde ser extraído da imagem de bioimpedância.")
            }
            
            // Tenta obter o mapa aninhado caso venha estruturado em "dados_encontrados"
            val dadosEncontrados = rootMap["dados_encontrados"] as? Map<*, *>
            
            fun getNestedFloat(key: String, alternativeKeys: List<String>, default: Float): Float {
                if (dadosEncontrados != null) {
                    for (altKey in (listOf(key) + alternativeKeys)) {
                        val field = dadosEncontrados[altKey] as? Map<*, *>
                        if (field != null) {
                            val v = field["valor"]
                            if (v != null) {
                                return (v as? Number)?.toFloat() ?: default
                            }
                        }
                    }
                }
                // Fallback para leitura na raiz
                for (altKey in (listOf(key) + alternativeKeys)) {
                    val v = rootMap[altKey]
                    if (v != null) {
                        return (v as? Number)?.toFloat() ?: default
                    }
                }
                return default
            }

            fun getNestedInt(key: String, alternativeKeys: List<String>, default: Int): Int {
                if (dadosEncontrados != null) {
                    for (altKey in (listOf(key) + alternativeKeys)) {
                        val field = dadosEncontrados[altKey] as? Map<*, *>
                        if (field != null) {
                            val v = field["valor"]
                            if (v != null) {
                                return (v as? Number)?.toInt() ?: default
                            }
                        }
                    }
                }
                // Fallback para leitura na raiz
                for (altKey in (listOf(key) + alternativeKeys)) {
                    val v = rootMap[altKey]
                    if (v != null) {
                        return (v as? Number)?.toInt() ?: default
                    }
                }
                return default
            }

            var parsedTime = System.currentTimeMillis()
            val dateStr = (rootMap["data_extracao"] as? String) ?: (rootMap["dateStr"] as? String)
            if (!dateStr.isNullOrBlank()) {
                try {
                    val formats = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy")
                    for (fmt in formats) {
                        try {
                            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                            val d = sdf.parse(dateStr)
                            if (d != null) {
                                parsedTime = d.time
                                break
                            }
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }

            return BioimpedanceRecord(
                id = "bio_" + java.util.UUID.randomUUID().toString(),
                date = parsedTime,
                peso = getNestedFloat("peso", emptyList(), 0.0f),
                imc = getNestedFloat("imc", emptyList(), 0.0f),
                percentualGordura = getNestedFloat("gordura", listOf("percentualGordura"), 0.0f),
                taxaMuscular = getNestedFloat("taxa_muscular", listOf("taxaMuscular"), 0.0f),
                massaMagra = getNestedFloat("massa_magra", listOf("massaMagra"), 0.0f),
                gorduraSubcutanea = getNestedFloat("gordura_subcutanea", listOf("gorduraSubcutanea"), 0.0f),
                gorduraVisceral = getNestedFloat("gordura_visceral", listOf("gorduraVisceral"), 0.0f),
                aguaCorporal = getNestedFloat("agua_corporal", listOf("aguaCorporal"), 0.0f),
                massaMuscularEsqueletica = getNestedFloat("massa_muscular_esqueletica", listOf("massaMuscularEsqueletica"), 0.0f),
                massaMuscular = getNestedFloat("massa_muscular", listOf("massaMuscular"), 0.0f),
                massaOssea = getNestedFloat("massa_ossea", listOf("massaOssea"), 0.0f),
                proteina = getNestedFloat("proteina", emptyList(), 0.0f),
                tmb = getNestedInt("tmb", emptyList(), 0),
                idadeMetabolica = getNestedInt("idade_metabolica", listOf("idadeMetabolica"), 0)
            )
        } catch (e: Exception) {
            throw Exception(e.message ?: "Erro ao decodificar dados de bioimpedância.")
        }
    }

    fun scanCardio(base64Image: String, onResult: (CardioSession) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            isAiLoading.value = true
            try {
                val jsonString = com.example.services.ai.GeminiService.extractCardioFromImage(base64Image)
                val session = parseCardioSessionFromJson(jsonString)
                onResult(session)
            } catch (e: Exception) {
                onError(e.message ?: "Erro desconhecido")
            } finally {
                isAiLoading.value = false
            }
        }
    }

    fun saveCardioSession(session: CardioSession) {
        viewModelScope.launch {
            val unarchived = cardioSessions.value.filter { !it.archived }.sortedBy { it.date }
            if (unarchived.size >= 2) {
                val oldest = unarchived.first()
                repository.logCardio(oldest.copy(archived = true))
            }
            repository.logCardio(session.copy(archived = false))
            AudioSynth.playChimeSound()
        }
    }

    fun deleteCardioSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteCardio(sessionId)
        }
    }

    fun parseCardioSessionFromJson(json: String): CardioSession {
        try {
            val type = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val mapAdapter = com.example.utils.JsonUtils.moshi.adapter<Map<String, Any>>(type)
            val map = mapAdapter.fromJson(json) ?: throw Exception("Formato inválido de JSON")
            
            if (map.isEmpty()) {
                throw Exception("Nenhum dado pôde ser extraído da imagem do smartwatch.")
            }
            
            fun getFloat(key: String, default: Float): Float {
                val v = map[key] ?: return default
                return (v as? Number)?.toFloat() ?: default
            }
            fun getInt(key: String, default: Int): Int {
                val v = map[key] ?: return default
                return (v as? Number)?.toInt() ?: default
            }
            fun getString(key: String, default: String): String {
                return map[key]?.toString() ?: default
            }

            val durationSeconds = getInt("durationSeconds", 0)
            val minutes = if (durationSeconds > 0) (durationSeconds / 60).coerceAtLeast(1) else getInt("durationMinutes", 0)
            val distance = getFloat("distance", 0.0f)

            if (distance <= 0.0f && durationSeconds <= 0 && minutes <= 0) {
                throw Exception("Não foi possível ler distância nem duração válidas no print de cardio.")
            }

            val dateStr = getString("date", "")
            var timestamp = System.currentTimeMillis()
            if (dateStr.isNotEmpty()) {
                try {
                    val formats = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy")
                    for (fmt in formats) {
                        try {
                            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
                            val d = sdf.parse(dateStr)
                            if (d != null) {
                                timestamp = d.time
                                break
                            }
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }

            return CardioSession(
                id = "cardio_" + java.util.UUID.randomUUID().toString(),
                type = getString("type", "Corrida"),
                durationMinutes = minutes,
                calories = getInt("calories", 0),
                date = timestamp,
                distance = distance,
                durationSeconds = durationSeconds,
                avgHr = getInt("avgHr", 0),
                steps = getInt("steps", 0),
                pace = getString("pace", ""),
                fatBurnZone = getFloat("fatBurnZone", 0.0f),
                anaerobicZone = getFloat("anaerobicZone", 0.0f),
                limitZone = getFloat("limitZone", 0.0f)
            )
        } catch (e: Exception) {
            throw Exception(e.message ?: "Erro ao decodificar dados do smartwatch.")
        }
    }

    // --- SYNC ENGINE MANUAL ACTIONS ---
    fun triggerManualSync() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            isSyncing.value = true
            syncStatusMessage.value = "Sincronizando pendências com a nuvem..."
            
            val success = repository.syncPendingActions()
            
            if (success) {
                syncStatusMessage.value = "Sincronização concluída com sucesso!"
                AudioSynth.playChimeSound()
            } else {
                syncStatusMessage.value = "Erro ao sincronizar. Tente mais tarde."
                AudioSynth.playTickSound()
            }
            
            delay(2500)
            syncStatusMessage.value = null
            isSyncing.value = false
        }
    }

    // --- WORKOUT CREATOR BUILDER ---
    fun createAndSaveCustomWorkout(name: String, description: String, selectedExercises: List<Exercise>) {
        viewModelScope.launch {
            val workoutExs = selectedExercises.mapIndexed { index, ex ->
                WorkoutExercise(
                    id = "ex_${UUID.randomUUID()}",
                    exerciseId = ex.id,
                    name = ex.name,
                    category = ex.category,
                    sets = TemplatesAndExercises.createDefaultSets(ex.id, 3, 10, 20f),
                    restTimeSeconds = 60,
                    progressionType = "Linear"
                )
            }

            val json = JsonUtils.serializeWorkoutExercises(workoutExs)

            val newWorkout = Workout(
                id = "workout_${UUID.randomUUID()}",
                templateId = null,
                name = name,
                description = description,
                exercisesJson = json
            )

            repository.createWorkout(newWorkout)
            activeScreen.value = AppScreen.WORKOUTS
            AudioSynth.playChimeSound()
        }
    }

    fun saveAndStartProtocol(protocol: com.example.data.model.WorkoutProtocol) {
        viewModelScope.launch {
            val workout = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(protocol)
            repository.createWorkout(workout)
            _preferredWorkoutId.value = workout.id
            startWorkoutSession(workout)
        }
    }

    fun importAiWorkout(aiText: String) {
        viewModelScope.launch {
            val allExs = com.example.constants.TemplatesAndExercises.ALL_EXERCISES
            val foundExs = mutableListOf<com.example.data.model.Exercise>()
            val lines = aiText.lines()
            for (line in lines) {
                val matched = allExs.firstOrNull { ex ->
                    val cleanExName = ex.name.lowercase().trim()
                    line.lowercase().contains(cleanExName) || cleanExName.contains(line.lowercase().trim())
                }
                if (matched != null && !foundExs.any { it.id == matched.id }) {
                    foundExs.add(matched)
                }
            }
            
            if (foundExs.isEmpty()) {
                for (ex in allExs) {
                    if (aiText.lowercase().contains(ex.name.lowercase())) {
                        foundExs.add(ex)
                    }
                }
            }
            
            if (foundExs.isEmpty()) {
                val lowerText = aiText.lowercase()
                val defaultCategory = when {
                    lowerText.contains("perna") || lowerText.contains("agachamento") -> "Pernas"
                    lowerText.contains("peito") || lowerText.contains("supino") -> "Peito"
                    lowerText.contains("costas") || lowerText.contains("puxador") -> "Costas"
                    lowerText.contains("ombro") || lowerText.contains("elevação") -> "Ombros"
                    lowerText.contains("braço") || lowerText.contains("bíceps") || lowerText.contains("tríceps") -> "Bíceps"
                    else -> "Peito"
                }
                foundExs.addAll(allExs.filter { it.category == defaultCategory }.take(4))
            }
            
            val name = "Treino IA - " + when {
                aiText.lowercase().contains("perna") || aiText.lowercase().contains("legs") -> "Pernas"
                aiText.lowercase().contains("peito") || aiText.lowercase().contains("push") -> "Peito e Ombros"
                aiText.lowercase().contains("costas") || aiText.lowercase().contains("pull") -> "Costas e Bíceps"
                aiText.lowercase().contains("ombro") -> "Ombros"
                aiText.lowercase().contains("braço") -> "Braços"
                else -> "Personalizado"
            }
            
            val description = "Treino gerado pelo Coach IA."
            createAndSaveCustomWorkout(name, description, foundExs)
        }
    }

    fun deleteSavedWorkout(id: String) {
        viewModelScope.launch {
            repository.deleteWorkout(id)
            AudioSynth.playTickSound()
        }
    }

    // --- CARDIO LOGGING ACTIONS ---
    fun logCardioActivity(type: String, durationMin: Int, caloriesBurned: Int) {
        viewModelScope.launch {
            val unarchived = cardioSessions.value.filter { !it.archived }.sortedBy { it.date }
            if (unarchived.size >= 2) {
                val oldest = unarchived.first()
                repository.logCardio(oldest.copy(archived = true))
            }
            val session = CardioSession(
                id = "cardio_${UUID.randomUUID()}",
                type = type,
                durationMinutes = durationMin,
                calories = caloriesBurned,
                archived = false
            )
            repository.logCardio(session)
            AudioSynth.playChimeSound()
        }
    }

    // --- LIBRARY CUSTOM EXERCISES AND FAVORITES ---
    fun toggleExerciseFavorite(id: String) {
        val current = favoriteExercisesIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        favoriteExercisesIds.value = current
        
        // Save to SharedPreferences
        sharedPrefs.edit().putStringSet("favorites", current).apply()
        AudioSynth.playTickSound()
    }

    fun loadFavoritesFromPrefs() {
        val saved = sharedPrefs.getStringSet("favorites", emptySet()) ?: emptySet()
        favoriteExercisesIds.value = saved
    }

    fun addNewCustomExercise(name: String, category: String, description: String, difficulty: String) {
        viewModelScope.launch {
            val entity = CustomExerciseEntity(
                id = "custom_${UUID.randomUUID()}",
                name = name,
                category = category,
                description = description,
                difficulty = difficulty
            )
            repository.addCustomExercise(entity)
            AudioSynth.playChimeSound()
        }
    }

    // --- AI COACH CREDITS AND SERVICE CALLS ---
    private fun checkAiCreditsReset() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = sharedPrefs.getString("ai_last_date", "")
        
        if (savedDate != todayStr) {
            // Reset daily queries to 0
            sharedPrefs.edit()
                .putString("ai_last_date", todayStr)
                .putInt("ai_queries_today", 0)
                .apply()
            aiQueriesToday.value = 0
        } else {
            aiQueriesToday.value = sharedPrefs.getInt("ai_queries_today", 0)
        }
        
        loadFavoritesFromPrefs()
    }

    fun queryAiCoach(prompt: String) {
        viewModelScope.launch {
            checkAiCreditsReset()
            
            if (aiQueriesToday.value >= 2) {
                aiCoachResponse.value = "Aviso: Limite diário de 2 consultas excedido! Volte amanhã para mais dicas do Coach de IA."
                AudioSynth.playTickSound()
                return@launch
            }

            isAiLoading.value = true
            aiCoachResponse.value = "Carregando conselho do Coach IA..."
            
            val profile = userProfile.value
            val recentHist = history.value
            val currentRecovery = recoveryList.value
            val recentCard = cardioSessions.value
            val bioHistory = getBioimpedanceHistory(profile.bioimpedanciaHistoricoJson)
            
            val fitnessContext = com.example.services.ai.UserFitnessContext(
                profile = profile,
                recentHistory = recentHist,
                recoveryList = currentRecovery,
                bioimpedanceHistory = bioHistory,
                recentCardio = recentCard
            )
            
            val response = GeminiService.generateExerciseSuggestion(prompt, fitnessContext)
            
            // Deduct query credits
            val newQueriesCount = aiQueriesToday.value + 1
            sharedPrefs.edit().putInt("ai_queries_today", newQueriesCount).apply()
            aiQueriesToday.value = newQueriesCount
            
            aiCoachResponse.value = response
            isAiLoading.value = false
            AudioSynth.playChimeSound()
        }
    }

    fun clearAiResponse() {
        aiCoachResponse.value = null
    }

    // --- DEVELOPER MODE OPERATIONS ---
    fun devResetEnvironment() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.resetEnvironment(db, getApplication())
        }
    }

    fun devSeedExercises() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.seedExercises(db)
        }
    }

    fun devGenerateHistory(months: Int) {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.generateHistory(db, months)
        }
    }

    fun devGenerateSessions(count: Int) {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.generateHistory(db, 12, count)
        }
    }

    fun devInsertFakeBioimpedance() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.insertFakeBioimpedance(db)
        }
    }

    fun devInsertFakeCardio() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.insertFakeCardio(db)
        }
    }

    fun devSimulateLowRecovery() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.simulateLowRecovery(db)
        }
    }

    fun devUnlockAllTrophies() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.unlockAllTrophies(db)
        }
    }

    fun devCreateWorkoutRoutine(type: String) {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.createWorkoutRoutine(db, type)
        }
    }

    fun devClearWorkouts() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.clearWorkouts(db)
        }
    }

    fun devClearBioimpedance() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.clearBioimpedance(db)
        }
    }

    fun devClearRecovery() {
        viewModelScope.launch {
            com.example.utils.DeveloperTools.clearRecovery(db)
        }
    }

    fun devImportAuthorHistory(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val inserted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.utils.DeveloperTools.importAuthorHistory(db)
            }
            onResult(inserted)
        }
    }

    fun devAddDefaultTafUpcomingEvent(): Boolean {
        val defaultTaf = com.example.engine.HybridLoadCoachEngine.buildDefaultTafUpcomingEvent()
        if (_upcomingRaces.value.any { it.id == defaultTaf.id }) {
            return false
        }
        saveUpcomingRaceEvent(defaultTaf)
        return true
    }

    fun getFlowStateString(flowState: WorkoutFlowState): String {
        return when (flowState) {
            is WorkoutFlowState.Idle -> "Idle"
            is WorkoutFlowState.Running -> "Running"
            is WorkoutFlowState.Finished -> "Finished"
            is WorkoutFlowState.Abandoned -> "Abandoned"
        }
    }

    fun parseFlowState(flowStateStr: String?): WorkoutFlowState {
        return when (flowStateStr) {
            "Idle" -> WorkoutFlowState.Idle
            "Running" -> WorkoutFlowState.Running
            "Finished" -> WorkoutFlowState.Finished
            "Abandoned" -> WorkoutFlowState.Abandoned
            else -> WorkoutFlowState.Idle
        }
    }

    fun getActiveWorkoutSession(): com.example.data.model.ActiveWorkoutSession? {
        val workout = activeWorkout.value ?: return null
        val exercises = activeExercises.value
        val index = currentExerciseIndex.value
        val duration = workoutDurationSeconds.value
        val flowState = workoutFlowState.value
        
        val completedExs = exercises.filter { ex -> ex.sets.any { s -> s.completed } }.map { it.exerciseId }
        val completedSetsList: List<String> = exercises.flatMap { ex: com.example.data.model.WorkoutExercise -> 
            ex.sets.filter { s: com.example.data.model.WorkoutSet -> s.completed }.map { s: com.example.data.model.WorkoutSet -> s.id } 
        }
        val weightsList: List<String> = exercises.flatMap { ex: com.example.data.model.WorkoutExercise -> 
            ex.sets.map { s: com.example.data.model.WorkoutSet -> "${ex.exerciseId}:${s.index}:${s.weight}" } 
        }
        val repsList: List<String> = exercises.flatMap { ex: com.example.data.model.WorkoutExercise -> 
            ex.sets.map { s: com.example.data.model.WorkoutSet -> "${ex.exerciseId}:${s.index}:${s.reps}" } 
        }
        val timersStr = "restActive=${isRestTimerActive.value},tempoActive=${isTempoTimerActive.value}"
        val restStatusStr = "${restTimeRemaining.value}:${restTimeTotal.value}"
        val progression = exercises.firstOrNull()?.progressionType ?: "Linear"
        val order = exercises.map { it.exerciseId }
        val stateStr = if (flowState == WorkoutFlowState.Running) "ACTIVE" else "INACTIVE"
        val exercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
        val workoutJson = try {
            JsonUtils.moshi.adapter(Workout::class.java).toJson(workout)
        } catch (e: Exception) {
            ""
        }
        
        val sessionId = sharedPrefs.getString("active_session_id", null) ?: java.util.UUID.randomUUID().toString()
        val startTime = sharedPrefs.getLong("active_session_start_time", System.currentTimeMillis())
        
        return com.example.data.model.ActiveWorkoutSession(
            workoutId = workout.id,
            sessionId = sessionId,
            startTime = startTime,
            currentExerciseIndex = index,
            completedExercises = completedExs,
            completedSets = completedSetsList,
            weightsUsed = weightsList,
            repsPerformed = repsList,
            timers = timersStr,
            restStatus = restStatusStr,
            progressionChosen = progression,
            exerciseOrder = order,
            elapsedTime = duration,
            sessionState = stateStr,
            exercisesJson = exercisesJson,
            workoutName = workout.name,
            workoutJson = workoutJson
        )
    }

    fun saveActiveSession() {
        val workout = activeWorkout.value
        val exercises = activeExercises.value
        val index = currentExerciseIndex.value
        val duration = workoutDurationSeconds.value
        val flowState = workoutFlowState.value
        val isRestActive = isRestTimerActive.value
        val restRemaining = restTimeRemaining.value
        val restTotal = restTimeTotal.value
        val isTempoActive = isTempoTimerActive.value
        val tempoRemaining = tempoTimeRemaining.value
        val tempoTotal = tempoTimeTotal.value
        val isPreActive = isPreSessionActive.value
        
        val editor = sharedPrefs.edit()
        
        if (workout == null || flowState == WorkoutFlowState.Finished || flowState == WorkoutFlowState.Abandoned) {
            editor.putBoolean("has_active_session", false)
            editor.remove("active_workout_session_json")
            editor.remove("active_workout_json")
            editor.remove("active_exercises_json")
            editor.remove("active_current_exercise_index")
            editor.remove("active_workout_duration_seconds")
            editor.remove("active_is_rest_timer_active")
            editor.remove("active_rest_time_remaining")
            editor.remove("active_rest_time_total")
            editor.remove("active_is_tempo_timer_active")
            editor.remove("active_tempo_time_remaining")
            editor.remove("active_tempo_time_total")
            editor.remove("active_is_pre_session_active")
            editor.remove("active_workout_flow_state")
            editor.remove("active_session_timestamp")
            editor.remove("active_session_default_rest_seconds")
        } else {
            val session = getActiveWorkoutSession()
            if (session != null) {
                val sessionJson = JsonUtils.serializeActiveWorkoutSession(session)
                editor.putString("active_workout_session_json", sessionJson)
            }
            
            val exercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
            val hydratedWorkout = workout.copy(exercisesJson = exercisesJson)
            val workoutJson = try {
                JsonUtils.moshi.adapter(Workout::class.java).toJson(hydratedWorkout)
            } catch (e: Exception) {
                null
            }
            
            editor.putString("active_workout_json", workoutJson)
            editor.putString("active_exercises_json", exercisesJson)
            editor.putInt("active_current_exercise_index", index)
            editor.putLong("active_workout_duration_seconds", duration)
            editor.putBoolean("active_is_rest_timer_active", isRestActive)
            editor.putInt("active_rest_time_remaining", restRemaining)
            editor.putInt("active_rest_time_total", restTotal)
            editor.putBoolean("active_is_tempo_timer_active", isTempoActive)
            editor.putInt("active_tempo_time_remaining", tempoRemaining)
            editor.putInt("active_tempo_time_total", tempoTotal)
            editor.putBoolean("active_is_pre_session_active", isPreActive)
            editor.putString("active_workout_flow_state", getFlowStateString(flowState))
            editor.putLong("active_session_timestamp", System.currentTimeMillis())
            editor.putInt("active_session_default_rest_seconds", sessionDefaultRestSeconds.value)
            editor.putBoolean("has_active_session", true)
        }
        editor.apply()
    }

    fun reorderSessionExercises(fromIndex: Int, toIndex: Int) {
        reorderActiveWorkoutExercises(fromIndex, toIndex)
    }

    fun commitReorderedSessionExercises(newOrderedList: List<com.example.data.model.WorkoutExercise>) {
        if (newOrderedList.isEmpty()) return
        val recalculatedList = com.example.services.recommendation.engine001.TrainingEngine001.calculateLoadsForSession(
            exercises = newOrderedList,
            context = getApplication()
        )
        activeExercises.value = recalculatedList
        saveActiveSession()
        val msg = "Sequência alterada: cargas reajustadas para a nova curva de fadiga"
        fastSetWarningMessage.value = msg
        viewModelScope.launch {
            delay(3500)
            if (fastSetWarningMessage.value == msg) {
                fastSetWarningMessage.value = null
            }
        }
        try {
            android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
            com.example.utils.AudioSynth.playTickSound()
        } catch (e: Exception) {}
    }

    fun reorderActiveWorkoutExercises(fromIndex: Int, toIndex: Int) {
        val currentList = activeExercises.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            commitReorderedSessionExercises(currentList)
        }
    }

    fun removeExerciseFromActiveWorkout(index: Int) {
        val currentList = activeExercises.value.toMutableList()
        if (index in currentList.indices) {
            currentList.removeAt(index)
            activeExercises.value = currentList
            saveActiveSession()
            try {
                com.example.utils.AudioSynth.playTickSound()
            } catch (e: Exception) {}
        }
    }

    fun optimizeActiveWorkoutSequence() {
        val currentList = activeExercises.value.toMutableList()
        if (currentList.size <= 2) return
        
        val optimizedList = mutableListOf<com.example.data.model.WorkoutExercise>()
        
        var lastPlaced = currentList.removeAt(0)
        optimizedList.add(lastPlaced)
        
        while (currentList.isNotEmpty()) {
            val nextIndex = currentList.indexOfFirst { 
                !it.category.trim().equals(lastPlaced.category.trim(), ignoreCase = true) 
            }
            
            val chosen = if (nextIndex != -1) {
                currentList.removeAt(nextIndex)
            } else {
                currentList.removeAt(0)
            }
            optimizedList.add(chosen)
            lastPlaced = chosen
        }
        
        val originalExercises = activeWorkout.value?.let {
            try {
                com.example.utils.JsonUtils.parseWorkoutExercises(it.exercisesJson)
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
        
        val updatedList = optimizedList.mapIndexed { idx, ex ->
            val isConsecutiveSameMuscle = idx > 0 && ex.category.trim().equals(optimizedList[idx - 1].category.trim(), ignoreCase = true)
            
            val historicalMax = getHistoricalMaxForExercise(ex.name).first
            
            val updatedSets = ex.sets.mapIndexed { setIdx, set ->
                val originalEx = originalExercises.find { it.exerciseId == ex.exerciseId || it.name.equals(ex.name, ignoreCase = true) }
                val originalSetWeight = originalEx?.sets?.getOrNull(setIdx)?.weight ?: set.weight
                
                val baselineWeight = if (historicalMax > 0f) historicalMax else originalSetWeight
                
                val newWeight = if (isConsecutiveSameMuscle) {
                    baselineWeight * 0.9f
                } else {
                    baselineWeight
                }
                
                val roundedWeight = (Math.round(newWeight * 10f) / 10f).toFloat()
                set.copy(weight = roundedWeight)
            }
            ex.copy(sets = updatedSets)
        }
        
        val recalculated = com.example.services.recommendation.engine001.TrainingEngine001.calculateLoadsForSession(
            exercises = updatedList,
            context = getApplication()
        )
        activeExercises.value = recalculated
        saveActiveSession()
        val msg = "Sequência alterada: cargas reajustadas para a nova curva de fadiga"
        fastSetWarningMessage.value = msg
        viewModelScope.launch {
            delay(3500)
            if (fastSetWarningMessage.value == msg) {
                fastSetWarningMessage.value = null
            }
        }
        try {
            android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
            com.example.utils.AudioSynth.playTickSound()
        } catch (e: Exception) {}
    }

    private fun clearSavedActiveSession() {
        val editor = sharedPrefs.edit()
        editor.putBoolean("has_active_session", false)
        editor.remove("active_workout_session_json")
        editor.remove("active_workout_json")
        editor.remove("active_exercises_json")
        editor.remove("active_current_exercise_index")
        editor.remove("active_workout_duration_seconds")
        editor.remove("active_is_rest_timer_active")
        editor.remove("active_rest_time_remaining")
        editor.remove("active_rest_time_total")
        editor.remove("active_is_tempo_timer_active")
        editor.remove("active_tempo_time_remaining")
        editor.remove("active_tempo_time_total")
        editor.remove("active_is_pre_session_active")
        editor.remove("active_workout_flow_state")
        editor.remove("active_session_timestamp")
        editor.remove("active_session_default_rest_seconds")
        editor.apply()
    }

    fun restoreActiveSession(targetSessionId: String? = null) {
        viewModelScope.launch {
            try {
                val sid = targetSessionId ?: currentSessionId.value ?: activeSessionsList.value.firstOrNull()?.id
                val activeDb = if (sid != null) {
                    repository.getWorkoutSessionById(sid)
                } else {
                    repository.getActiveWorkoutSession()
                }

                if (activeDb != null && activeDb.status == WorkoutSessionStatus.ATIVO.rawValue) {
                    currentSessionId.value = activeDb.id
                    val rawExercises = try {
                        JsonUtils.parseWorkoutExercises(activeDb.exercisesJson)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (rawExercises.isNotEmpty() && !rawExercises.any { it.sets.isNullOrEmpty() }) {
                        val exercises = rawExercises.map { ex ->
                            val setsList = ex.sets ?: emptyList()
                            ex.copy(sets = setsList.map { set ->
                                val repsVal = set.reps ?: 1
                                val weightVal = set.weight ?: 0f
                                set.copy(
                                    reps = repsVal.coerceIn(0, 999),
                                    weight = weightVal.coerceIn(0f, 9999f)
                                )
                            })
                        }
                        val allWorkouts = repository.workoutsFlow.first()
                        val baseWorkout = allWorkouts.find { it.id == activeDb.workoutId }
                            ?: Workout(id = activeDb.workoutId, name = activeDb.workoutName, exercisesJson = activeDb.exercisesJson)

                        val hydratedWorkout = baseWorkout.copy(exercisesJson = activeDb.exercisesJson)
                        activeWorkout.value = hydratedWorkout
                        activeExercises.value = exercises
                        currentExerciseIndex.value = 0

                        val timeElapsedSeconds = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
                            activeDb.startTimeMonotonic,
                            activeDb.startTime,
                            activeDb.monotonicStartTimeNanos
                        )
                        com.example.services.WorkoutServiceState.startOrSyncWorkoutClock(timeElapsedSeconds)

                        // O timer de descanso não pode alegar ter continuado rodando
                        com.example.services.WorkoutServiceState.resetTimers()
                        isPreSessionActive.value = false
                        workoutFlowState.value = WorkoutFlowState.Running
                        showActiveSessionPrompt.value = false
                        isMultipleActiveSessionsConflict.value = false

                        activeScreen.value = AppScreen.PLAYER
                        startTicker()
                        saveActiveSession()
                        android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Sessão de treino ativa restaurada com sucesso do Room: ${activeDb.workoutName}")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Falha ao restaurar sessão ativa do Room, tentando sharedPrefs", e)
            }

            restoreActiveSessionFromPrefs()
        }
    }

    private fun restoreActiveSessionFromPrefs() {
        val hasSession = sharedPrefs.getBoolean("has_active_session", false)
        if (!hasSession) return
        
        val sessionJson = sharedPrefs.getString("active_workout_session_json", null)
        if (sessionJson != null) {
            val session = JsonUtils.parseActiveWorkoutSession(sessionJson)
            if (session != null) {
                try {
                    val workout = JsonUtils.moshi.adapter(Workout::class.java).fromJson(session.workoutJson)
                    val rawExercises = JsonUtils.parseWorkoutExercises(session.exercisesJson)

                    // Rigid validation before restoration
                    if (workout == null || session.exercisesJson.isBlank() || session.exercisesJson == "[]" ||
                        rawExercises.isEmpty() || rawExercises.any { it.sets.isNullOrEmpty() }
                    ) {
                        android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Aborting session restore: corrupted exercises or empty sets found.")
                        clearSavedActiveSession()
                        activeScreen.value = AppScreen.HOME
                        return
                    }
                    
                    val exercises = rawExercises.map { ex ->
                        val setsList = ex.sets ?: emptyList()
                        ex.copy(sets = setsList.map { set ->
                            val repsVal = set.reps ?: 1
                            val weightVal = set.weight ?: 0f
                            set.copy(
                                reps = repsVal.coerceIn(1, 999),
                                weight = weightVal.coerceIn(0f, 9999f)
                            )
                        })
                    }
                    
                    val hydratedWorkout = workout.copy(exercisesJson = session.exercisesJson)
                    activeWorkout.value = hydratedWorkout
                    activeExercises.value = exercises
                    currentExerciseIndex.value = session.currentExerciseIndex
                    
                    val savedTimestamp = sharedPrefs.getLong("active_session_timestamp", 0L)
                    val timeElapsedSeconds = if (savedTimestamp > 0) (System.currentTimeMillis() - savedTimestamp) / 1000 else 0L
                    
                    com.example.services.WorkoutServiceState.startOrSyncWorkoutClock(session.elapsedTime + timeElapsedSeconds)
                    
                    val timersMap = session.timers.split(",").associate {
                        val parts = it.split("=")
                        if (parts.size == 2) parts[0] to parts[1].toBoolean() else "" to false
                    }
                    val isRestActive = timersMap["restActive"] ?: false
                    
                    val restParts = session.restStatus.split(":")
                    val savedRestRemaining = if (restParts.isNotEmpty()) restParts[0].toIntOrNull() ?: 0 else 0
                    val savedRestTotal = if (restParts.size > 1) restParts[1].toIntOrNull() ?: 60 else 60
                    if (isRestActive) {
                        val newRestRemaining = (savedRestRemaining - timeElapsedSeconds).toInt()
                        if (newRestRemaining > 0) {
                            com.example.services.WorkoutServiceState.startRestClock(newRestRemaining)
                            restTimeTotal.value = savedRestTotal
                        } else {
                            com.example.services.WorkoutServiceState.resetTimers()
                        }
                    } else {
                        com.example.services.WorkoutServiceState.resetTimers()
                        restTimeTotal.value = savedRestTotal
                    }
                    
                    isTempoTimerActive.value = timersMap["tempoActive"] ?: false
                    
                    val isPreActive = sharedPrefs.getBoolean("active_is_pre_session_active", false)
                    isPreSessionActive.value = isPreActive
                    
                    sessionDefaultRestSeconds.value = sharedPrefs.getInt("active_session_default_rest_seconds", 60)
                    
                    val flowStateStr = sharedPrefs.getString("active_workout_flow_state", "Running")
                    workoutFlowState.value = parseFlowState(flowStateStr)
                    
                    activeScreen.value = AppScreen.PLAYER
                    
                    if (!isPreActive) {
                        startTicker()
                    }
                    
                    android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Sessão de treino ativa restaurada de ActiveWorkoutSession com sucesso: ${workout.name}")
                    return
                } catch (e: Exception) {
                    android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Erro ao restaurar de ActiveWorkoutSession, caindo para o método padrão", e)
                    clearSavedActiveSession()
                    activeScreen.value = AppScreen.HOME
                    return
                }
            }
        }
        
        val workoutJson = sharedPrefs.getString("active_workout_json", null)
        val exercisesJson = sharedPrefs.getString("active_exercises_json", null)
        
        if (workoutJson == null || exercisesJson == null || exercisesJson.isBlank() || exercisesJson == "[]") {
            clearSavedActiveSession()
            activeScreen.value = AppScreen.HOME
            return
        }
        
        try {
            val workout = JsonUtils.moshi.adapter(Workout::class.java).fromJson(workoutJson)
            val rawExercises = JsonUtils.parseWorkoutExercises(exercisesJson)

            // Rigid validation before restoration
            if (workout == null || rawExercises.isEmpty() || rawExercises.any { it.sets.isNullOrEmpty() }) {
                android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Aborting session restore: corrupted exercises or empty sets found.")
                clearSavedActiveSession()
                activeScreen.value = AppScreen.HOME
                return
            }
            
            // Defensively sanitize reps and weights to prevent layout crashes from previous corrupt state
            val exercises = rawExercises.map { ex ->
                val setsList = ex.sets ?: emptyList()
                ex.copy(sets = setsList.map { set ->
                    val repsVal = set.reps ?: 1
                    val weightVal = set.weight ?: 0f
                    set.copy(
                        reps = repsVal.coerceIn(1, 999),
                        weight = weightVal.coerceIn(0f, 9999f)
                    )
                })
            }
            
            val hydratedWorkout = workout.copy(exercisesJson = exercisesJson)
            activeWorkout.value = hydratedWorkout
            activeExercises.value = exercises
            currentExerciseIndex.value = sharedPrefs.getInt("active_current_exercise_index", 0)
            
            val savedTimestamp = sharedPrefs.getLong("active_session_timestamp", 0L)
            val timeElapsedSeconds = if (savedTimestamp > 0) (System.currentTimeMillis() - savedTimestamp) / 1000 else 0L

            val duration = sharedPrefs.getLong("active_workout_duration_seconds", 0L)
            com.example.services.WorkoutServiceState.startOrSyncWorkoutClock(duration + timeElapsedSeconds)
            
            val isRestActive = sharedPrefs.getBoolean("active_is_rest_timer_active", false)
            val savedRestRemaining = sharedPrefs.getInt("active_rest_time_remaining", 0)
            val savedRestTotal = sharedPrefs.getInt("active_rest_time_total", 60)
            if (isRestActive) {
                val newRestRemaining = (savedRestRemaining - timeElapsedSeconds).toInt()
                if (newRestRemaining > 0) {
                    com.example.services.WorkoutServiceState.startRestClock(newRestRemaining)
                    restTimeTotal.value = savedRestTotal
                } else {
                    com.example.services.WorkoutServiceState.resetTimers()
                }
            } else {
                com.example.services.WorkoutServiceState.resetTimers()
                restTimeTotal.value = savedRestTotal
            }
            
            val isTempoActive = sharedPrefs.getBoolean("active_is_tempo_timer_active", false)
            isTempoTimerActive.value = isTempoActive
            val savedTempoRemaining = sharedPrefs.getInt("active_tempo_time_remaining", 0)
            if (isTempoActive) {
                val newTempoRemaining = (savedTempoRemaining - timeElapsedSeconds).toInt()
                if (newTempoRemaining > 0) {
                    tempoTimeRemaining.value = newTempoRemaining
                } else {
                    tempoTimeRemaining.value = 0
                    isTempoTimerActive.value = false
                }
            } else {
                tempoTimeRemaining.value = savedTempoRemaining
            }
            tempoTimeTotal.value = sharedPrefs.getInt("active_tempo_time_total", 0)
            
            val isPreActive = sharedPrefs.getBoolean("active_is_pre_session_active", false)
            isPreSessionActive.value = isPreActive
            
            sessionDefaultRestSeconds.value = sharedPrefs.getInt("active_session_default_rest_seconds", 60)
            
            val flowStateStr = sharedPrefs.getString("active_workout_flow_state", "Running")
            workoutFlowState.value = parseFlowState(flowStateStr)
            
            // Restore screen to PLAYER so user is brought exactly back to where they were!
            activeScreen.value = AppScreen.PLAYER
            
            // Restart the ticker!
            if (!isPreActive) {
                startTicker()
            }
            
            android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Sessão de treino ativa restaurada com sucesso para o treino: ${workout.name}")
        } catch (e: Exception) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Erro ao restaurar sessão ativa", e)
            clearSavedActiveSession()
            activeScreen.value = AppScreen.HOME
        }
    }
 
    fun setupActiveSessionAutoSave() {
        viewModelScope.launch {
            activeWorkout.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            activeExercises.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            currentExerciseIndex.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            isRestTimerActive.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            isTempoTimerActive.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            isPreSessionActive.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            sessionDefaultRestSeconds.collect { saveActiveSession() }
        }
        viewModelScope.launch {
            workoutFlowState.collect { saveActiveSession() }
        }
    }

    fun generateWorkoutFeedbackMessage(
        workout: com.example.data.model.Workout,
        exercises: List<com.example.data.model.WorkoutExercise>,
        durationSeconds: Int,
        isEarly: Boolean
    ): List<String> {
        val messages = mutableListOf<String>()
        
        // 1. Primeira vez no Hip Thrust
        val hasHipThrust = exercises.any { it.exerciseId == "pernas_6" || it.name.lowercase().contains("hip thrust") }
        if (hasHipThrust) {
            val alreadyDone = history.value.any { entry ->
                entry.workoutId != workout.id && 
                (entry.workoutName.lowercase().contains("hip thrust") || entry.exercisesJson.lowercase().contains("hip thrust"))
            }
            if (!alreadyDone) {
                messages.add("Biomecânica do Hip Thrust: Mantenha a tíbia estritamente vertical em relação ao solo no topo da contração de pico para máximo recrutamento do glúteo máximo e estabilidade articular do quadril, evitando sobrecarga lombar.")
            }
        }
        
        // 2. Sucesso no Búlgaro
        val hasBulgarian = exercises.any { it.exerciseId == "pernas_8" || it.name.lowercase().contains("búlgaro") || it.name.lowercase().contains("bulgaro") }
        if (hasBulgarian) {
            val bulgarianEx = exercises.firstOrNull { it.exerciseId == "pernas_8" || it.name.lowercase().contains("búlgaro") || it.name.lowercase().contains("bulgaro") }
            val allBulgarianCompleted = bulgarianEx?.sets?.all { it.completed } ?: false
            if (allBulgarianCompleted) {
                messages.add("Progressão no Agachamento Búlgaro: Excelente nível técnico demonstrado! Como você completou todas as séries propostas, sugerimos aumentar a carga em 5% a 10% na próxima sessão ou introduzir uma pausa isométrica de 2 segundos no ponto de máxima amplitude.")
            }
        }
        
        // 3. Queda de reps ou RPE > 9.5
        var rpeThresholdExceeded = false
        var repsDropped = false
        for (ex in exercises) {
            for (i in ex.sets.indices) {
                val set = ex.sets[i]
                if (set.completed) {
                    val rpeVal = set.rpe.split("-").lastOrNull()?.trim()?.toDoubleOrNull() 
                        ?: set.rpe.toDoubleOrNull() ?: 0.0
                    if (rpeVal > 9.5) {
                        rpeThresholdExceeded = true
                    }
                    if (i > 0) {
                        val prevSet = ex.sets[i - 1]
                        if (prevSet.completed && set.reps < prevSet.reps - 2) {
                            repsDropped = true
                        }
                    }
                }
            }
        }
        if (rpeThresholdExceeded || repsDropped) {
            messages.add("Alerta de Fadiga/Deload: Queda abrupta no rendimento de repetições ou percepção de esforço extremo (RPE > 9.5) detectados nas séries finais. Recomendamos programar uma semana de microciclo regenerativo (deload) reduzindo o volume e a intensidade em 20% para restaurar a integridade tendínea.")
        }
        
        // 4. Tempo real < 65% do estimado
        var estimatedDurationSeconds = 0
        for (ex in exercises) {
            estimatedDurationSeconds += 60 // setup
            for (s in ex.sets) {
                estimatedDurationSeconds += 45 + (ex.restTimeSeconds)
            }
        }
        
        if (durationSeconds > 0 && estimatedDurationSeconds > 0) {
            val ratio = durationSeconds.toFloat() / estimatedDurationSeconds.toFloat()
            if (ratio < 0.65f) {
                messages.add("Diretriz de Descanso: Seu tempo de treino real foi inferior a 65% do tempo cientificamente estimado. Atenção: encurtar os intervalos de descanso recomendados reduz a remoção de metabólitos, o que deprime severamente o volume de séries efetivas devido à fadiga central residual.")
            }
        }
        
        if (messages.isEmpty()) {
            val defaultInsights = listOf(
                "Consistência e Volume: O volume total acumulado é o principal direcionador para hipertrofia muscular. Foque em manter uma técnica rígida antes de aumentar a carga de trabalho.",
                "Recuperação Ativa: Certifique-se de consumir proteínas e carboidratos de alta qualidade nas horas subsequentes à sessão para maximizar a ressíntese proteica muscular.",
                "Tensão Mecânica: Concentre-se na fase excêntrica de cada repetição (descida lenta de 3 a 4 segundos) para impor maior estresse e sinalização hipertrófica nas fibras musculares."
            )
            val seed = (System.currentTimeMillis() / (24 * 60 * 60 * 1000)).toInt()
            val index = Math.abs(seed) % defaultInsights.size
            messages.add(defaultInsights[index])
        }
        
        val seedIndex = (System.currentTimeMillis() / 60000).toInt()
        if (messages.size > 2) {
            val shuffled = messages.shuffled(java.util.Random(seedIndex.toLong()))
            return shuffled.take(2)
        }
        
        return messages
    }

    fun getSuggestedPlanWorkout(): com.example.data.model.Workout {
        if (fatigueAdvisoryState.value.isRestDayAccepted) {
            return getMobilityRecoveryWorkout()
        }
        val prefProtoId = preferredProtocolId.value
        val allProtocols = com.example.data.repository.WorkoutProtocolsRepository.ALL_PROTOCOLS
        val isFullBody = prefProtoId == null || prefProtoId == "FULL_BODY" || prefProtoId.startsWith("protocol_fb")
        val isPpl = prefProtoId == "PPL" || prefProtoId == "protocol_ppl_sup_inf"
        val hybrid = isHybridRunningEnabled.value || isFullBody
        val hist = history.value
        val runningHist = runningSessions.value

        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastGymEntry = hist.maxByOrNull { it.date }
        val lastFbHist = hist.filter { h ->
            h.workoutName.contains("Full Body", ignoreCase = true) || h.workoutId.contains("protocol_fb", ignoreCase = true)
        }.maxByOrNull { it.date }

        val daysSinceLastGym = if (lastGymEntry != null) {
            com.example.services.recommendation.engine001.TrainingEngine001.calculateCalendarDaysBetween(lastGymEntry.date)
        } else 999
        val hoursSinceLastGym = if (lastGymEntry != null) {
            (System.currentTimeMillis() - lastGymEntry.date) / (1000.0 * 3600.0)
        } else 999.0
        val alreadyRanToday = runningHist.any { it.date >= todayStart }

        // Janela de recuperação de 48h pós-Full Body (ou modo híbrido quando treinou musculação ontem)
        val shouldIntercalateRunToday = hybrid && lastGymEntry != null &&
            (daysSinceLastGym == 1 || (daysSinceLastGym == 0 && hoursSinceLastGym < 48.0 && !hist.any { it.date >= todayStart })) &&
            !alreadyRanToday

        if (shouldIntercalateRunToday) {
            return com.example.data.model.Workout(
                id = "WORKOUT_RUNNING_SESSION",
                templateId = "RUNNING",
                name = "Dia de Corrida & Recuperação - Módulo de Corrida",
                description = "Sessão aeróbica do protocolo híbrido. Alternância inteligente com musculação para máxima recuperação e condicionamento.",
                exercisesJson = "[]",
                createdAt = System.currentTimeMillis()
            )
        }

        if (isFullBody) {
            val fbProtocols = allProtocols.filter { it.category == "FULL_BODY" }.take(3)
            val lastIdx = if (lastFbHist != null) {
                when {
                    lastFbHist.workoutName.contains("Full Body A", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_a", ignoreCase = true) -> 0
                    lastFbHist.workoutName.contains("Full Body B", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_b", ignoreCase = true) -> 1
                    lastFbHist.workoutName.contains("Full Body C", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_c", ignoreCase = true) -> 2
                    lastFbHist.workoutName.contains("Full Body D", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_d", ignoreCase = true) -> 2
                    else -> -1
                }
            } else {
                -1
            }

            val nextIdx = if (lastIdx != -1) {
                (lastIdx + 1) % fbProtocols.size
            } else if (prefProtoId != null && prefProtoId.startsWith("protocol_fb_")) {
                fbProtocols.indexOfFirst { it.id == prefProtoId }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            val targetProto = fbProtocols[nextIdx]
            return com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetProto)
        } else if (isPpl) {
            val targetProto = allProtocols.firstOrNull { it.id == "protocol_ppl_sup_inf" } ?: allProtocols.first()
            return com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetProto)
        } else if (prefProtoId.isNotBlank()) {
            val targetProto = allProtocols.firstOrNull { it.id == prefProtoId }
            if (targetProto != null) {
                return com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetProto)
            }
        }

        val preferredId = _preferredWorkoutId.value
        if (preferredId != null) {
            val found = workouts.value.firstOrNull { it.id == preferredId }
            if (found != null) {
                return found
            }
        }

        val (protocol, nextDayIndex) = resolveUserPeriodizedProtocolAndDayIndex()
        val nextDay = protocol.dias[nextDayIndex]
        return protocol.toWorkout(nextDay.nome) ?: protocol.toWorkout(protocol.dias.first().nome)!!
    }

    private fun resolveUserPeriodizedProtocolAndDayIndex(): Pair<com.example.constants.PremiumProtocolo, Int> {
        val rawLevel = userProfile.value?.nivelExperiencia ?: "Iniciante"
        val normLevel = com.example.utils.FuzzySearchUtils.normalize(rawLevel)
        val targetEnumLevel = when {
            normLevel.contains("avancado") || normLevel.contains("advanced") -> com.example.constants.NivelExperiencia.AVANCADO
            normLevel.contains("intermediario") || normLevel.contains("medium") -> com.example.constants.NivelExperiencia.INTERMEDIARIO
            else -> com.example.constants.NivelExperiencia.INICIANTE
        }

        val levelMatchedProtocol = com.example.constants.PremiumTrainings.ALL_PROTOCOLS.firstOrNull {
            it.nivel == targetEnumLevel
        } ?: com.example.constants.PremiumTrainings.ALL_PROTOCOLS.first()

        val hist = history.value
        val lastPremium = hist.firstOrNull { it.workoutId.startsWith("premium_workout_") }

        if (lastPremium != null) {
            val foundProtocol = com.example.constants.PremiumTrainings.ALL_PROTOCOLS.firstOrNull {
                lastPremium.workoutId.contains(it.id) ||
                (it.id == "UL_INICIANTE" && lastPremium.workoutId.contains("upper_lower_beginner")) ||
                (it.id == "UL_PROGRESSIVO" && lastPremium.workoutId.contains("upper_lower_progressive")) ||
                (it.id == "GLUTEOS_3X" && lastPremium.workoutId.contains("glute_3x_advanced"))
            }

            if (foundProtocol != null && foundProtocol.nivel == targetEnumLevel) {
                val completedDayIndex = foundProtocol.dias.indexOfFirst { dia ->
                    lastPremium.workoutId.endsWith(dia.nome) || lastPremium.workoutName.contains(dia.nome)
                }
                val currentDayIndex = if (completedDayIndex != -1) {
                    (completedDayIndex + 1) % foundProtocol.dias.size
                } else {
                    0
                }
                return foundProtocol to currentDayIndex
            }
        }
        return levelMatchedProtocol to 0
    }

    /**
     * Retorna a sequência real dos próximos treinos da ficha periodizada ativa do usuário.
     * Mostra apenas dados concretos: Nome do Treino, Grupos Musculares, Duração estimada e Posição.
     */
    fun getPeriodizedWorkoutSequence(): List<com.example.data.model.PeriodizedWorkoutCardData> {
        val (protocol, currentDayIndex) = resolveUserPeriodizedProtocolAndDayIndex()

        val mobWorkout = getMobilityRecoveryWorkout()
        val mobExs = try { com.example.utils.JsonUtils.parseWorkoutExercises(mobWorkout.exercisesJson) } catch (e: Exception) { emptyList() }
        val mobilityCard = com.example.data.model.PeriodizedWorkoutCardData(
            workout = mobWorkout,
            title = "Mobilidade & Recuperação",
            muscleGroups = "SNC, Tendões e Articulações",
            durationText = "15 min",
            sequenceTag = "Recuperação Ativa",
            exerciseCount = mobExs.size
        )

        val prefProtoId = preferredProtocolId.value
        if (prefProtoId != null) {
            val allProtocols = com.example.data.repository.WorkoutProtocolsRepository.ALL_PROTOCOLS
            val isFullBody = prefProtoId == "FULL_BODY" || prefProtoId.startsWith("protocol_fb")
            if (isFullBody) {
                val fbProtocols = allProtocols.filter { it.category == "FULL_BODY" }.take(3)
                val fbList = fbProtocols.mapIndexed { index, proto ->
                    val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(proto)
                    val letter = ('A'.code + index).toChar()
                    val seqTag = when (index) {
                        0 -> "Full Body $letter (Dia 1)"
                        1 -> "Full Body $letter (Dia 2)"
                        2 -> "Full Body $letter (Dia 3)"
                        else -> "Full Body $letter (Dia 4)"
                    }
                    val muscles = proto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                    com.example.data.model.PeriodizedWorkoutCardData(
                        workout = w,
                        title = proto.name,
                        muscleGroups = muscles,
                        durationText = "45-55 min",
                        sequenceTag = seqTag,
                        exerciseCount = proto.exercises.size
                    )
                }
                return fbList + mobilityCard
            } else {
                val targetProto = allProtocols.firstOrNull { it.id == prefProtoId }
                if (targetProto != null) {
                    val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetProto)
                    val muscles = targetProto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                    return listOf(
                        com.example.data.model.PeriodizedWorkoutCardData(
                            workout = w,
                            title = targetProto.name,
                            muscleGroups = muscles,
                            durationText = "45-55 min",
                            sequenceTag = "Protocolo Ativo",
                            exerciseCount = targetProto.exercises.size
                        ),
                        mobilityCard
                    )
                }
            }
        }

        val savedWorkouts = workouts.value.filter { it.name.isNotBlank() }
        if (savedWorkouts.size >= 2) {
            val sorted = savedWorkouts.sortedBy { it.name }
            val customList = sorted.mapIndexed { index, w ->
                val exs = try { com.example.utils.JsonUtils.parseWorkoutExercises(w.exercisesJson) } catch (e: Exception) { emptyList() }
                val muscles = if (exs.isNotEmpty()) {
                    exs.map { it.category }
                        .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                        .distinct()
                        .take(2)
                        .joinToString(" e ")
                } else {
                    val parts = w.name.split("-", "–", ":")
                    if (parts.size >= 2) parts[1].trim() else "Geral"
                }
                val duration = "${(exs.size * 7 + 15).coerceIn(40, 75)} min"
                val sequenceTag = when (index) {
                    0 -> "Sugerido Hoje"
                    1 -> "Próximo (Amanhã)"
                    2 -> "Em 2 dias"
                    else -> "Treino ${index + 1} da Ficha"
                }
                val cleanTitle = if (w.name.contains("-")) w.name.split("-")[0].trim() else w.name
                com.example.data.model.PeriodizedWorkoutCardData(
                    workout = w,
                    title = cleanTitle.ifBlank { "Treino ${('A'.code + index).toChar()}" },
                    muscleGroups = muscles.ifBlank { "Força e Hipertrofia" },
                    durationText = duration,
                    sequenceTag = sequenceTag,
                    exerciseCount = exs.size
                )
            }
            return customList + mobilityCard
        }

        val sequence = mutableListOf<com.example.data.model.PeriodizedWorkoutCardData>()
        val totalDays = protocol.dias.size

        for (offset in 0 until totalDays) {
            val idx = (currentDayIndex + offset) % totalDays
            val dia = protocol.dias[idx]
            val w = protocol.toWorkout(dia.nome) ?: continue
            val exs = try { com.example.utils.JsonUtils.parseWorkoutExercises(w.exercisesJson) } catch (e: Exception) { emptyList() }
            
            val sequenceTag = when (offset) {
                0 -> "Sugerido Hoje"
                1 -> "Próximo (Amanhã)"
                2 -> "Em 2 dias"
                3 -> "Em 3 dias"
                else -> "Dia ${dia.ordem} da Ficha"
            }
            
            val letter = if (idx < 26) ('A'.code + idx).toChar().toString() else "${idx + 1}"
            val cleanTitle = "Treino $letter"
            
            sequence.add(
                com.example.data.model.PeriodizedWorkoutCardData(
                    workout = w,
                    title = cleanTitle,
                    muscleGroups = dia.foco,
                    durationText = "${dia.duracaoEstimada} min",
                    sequenceTag = sequenceTag,
                    exerciseCount = exs.size
                )
            )
        }
        sequence.add(mobilityCard)
        return sequence
    }

    /**
     * Retorna a lista de itens do Carrossel do Hero Card e o índice da página inicial/fixada.
     * Suporta protocolos estruturados (Full Body A/B/C/D, PPL), treinos salvos, alternância híbrida com corrida
     * e planos sugeridos, respeitando a janela de recuperação de 48h pós-Full Body.
     */
    fun getHeroCarouselData(): Pair<List<com.example.data.model.HeroCarouselItem>, Int> {
        val prefProtoId = preferredProtocolId.value
        val isHybrid = isHybridRunningEnabled.value
        val allProtocols = com.example.data.repository.WorkoutProtocolsRepository.ALL_PROTOCOLS
        val hist = history.value
        val runHist = runningSessions.value
        val advisory = fatigueAdvisoryState.value

        val lastFbHist = hist.filter { h ->
            h.workoutName.contains("Full Body", ignoreCase = true) || h.workoutId.contains("protocol_fb", ignoreCase = true)
        }.maxByOrNull { it.date }
        val lastGymHist = hist.maxByOrNull { it.date }

        val isFullBody = prefProtoId == "FULL_BODY" ||
            (prefProtoId != null && prefProtoId.startsWith("protocol_fb")) ||
            (prefProtoId == null && lastFbHist != null)

        val runProgId = selectedRunningProgramId.value
        val runProgInfo = com.example.engine.RunningProgramManager.getProgramById(runProgId)
        val runProgress = com.example.engine.RunningProgramManager.calculateCurrentProgress(runHist)
        val runWorkoutHeaderLabel = "Semana ${runProgress.weekNumber} • Dia ${runProgress.dayNumber} — ${runProgInfo.title}"

        fun createRunningCarouselItem(
            idx: Int = 0,
            customTitle: String = "Módulo de Corrida",
            customSubtitle: String = "Cardio, Resistência & VO2 Max",
            customBadge: String = "SESSÃO AERÓBICA",
            customDuration: String = "35-45 min",
            isSuggestedToday: Boolean = false,
            advanceGymWorkout: com.example.data.model.Workout? = null,
            advanceGymLabel: String? = null
        ): com.example.data.model.HeroCarouselItem {
            val runWorkout = com.example.data.model.Workout(
                id = "WORKOUT_RUNNING_SESSION_$idx",
                templateId = "RUNNING",
                name = "$customTitle - Módulo de Corrida",
                description = customSubtitle,
                exercisesJson = "[]",
                createdAt = System.currentTimeMillis()
            )
            return com.example.data.model.HeroCarouselItem(
                id = runWorkout.id,
                workout = runWorkout,
                isRunning = true,
                isFixedOrSuggested = isSuggestedToday,
                badgeLabel = customBadge,
                title = customTitle,
                subtitle = customSubtitle,
                durationText = customDuration,
                nextGymWorkoutToAdvance = advanceGymWorkout,
                advanceGymButtonLabel = advanceGymLabel
            )
        }

        fun createMobilityCarouselItem(): com.example.data.model.HeroCarouselItem {
            val mobWorkout = getMobilityRecoveryWorkout()
            return com.example.data.model.HeroCarouselItem(
                id = mobWorkout.id,
                workout = mobWorkout,
                isRunning = false,
                isFixedOrSuggested = advisory.isRestDayAccepted,
                badgeLabel = if (advisory.isRestDayAccepted) "DESCANSO ATIVO" else "RECUPERAÇÃO LIVRE",
                title = "Mobilidade Regenerativa",
                subtitle = "Recuperação do SNC e Articulações",
                durationText = "15 min"
            )
        }

        // 1. Caso PROTOCOLO FULL BODY (ou Híbrido com Full Body recente)
        if (isFullBody) {
            val fbProtocols = allProtocols.filter { it.category == "FULL_BODY" }
            val coreFbRotation = fbProtocols.take(3) // Rotação clássica Full Body A -> B -> C -> A
            val items = mutableListOf<com.example.data.model.HeroCarouselItem>()
            var pinnedIndex = 0

            val lastFbIdx = if (lastFbHist != null) {
                when {
                    lastFbHist.workoutName.contains("Full Body A", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_a", ignoreCase = true) -> 0
                    lastFbHist.workoutName.contains("Full Body B", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_b", ignoreCase = true) -> 1
                    lastFbHist.workoutName.contains("Full Body C", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_c", ignoreCase = true) -> 2
                    lastFbHist.workoutName.contains("Full Body D", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_d", ignoreCase = true) -> 2
                    else -> -1
                }
            } else -1

            val nextFbIdx = if (lastFbIdx != -1) {
                (lastFbIdx + 1) % coreFbRotation.size
            } else if (prefProtoId != null && prefProtoId.startsWith("protocol_fb_")) {
                fbProtocols.indexOfFirst { it.id == prefProtoId }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            val nextFbProto = fbProtocols.getOrNull(nextFbIdx) ?: fbProtocols.first()
            val nextFbWorkout = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(nextFbProto)
            val nextFbName = nextFbProto.name

            val daysSinceLastFb = if (lastFbHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.calculateCalendarDaysBetween(lastFbHist.date)
            } else 999
            val hoursSinceLastFb = if (lastFbHist != null) {
                (System.currentTimeMillis() - lastFbHist.date) / (1000.0 * 3600.0)
            } else 999.0

            val lastCompletedFbName = if (lastFbHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.extractCleanWorkoutTitle(
                    lastFbHist.workoutName,
                    lastFbHist.workoutId
                )
            } else "Full Body"

            // Detecta se o usuário treinou Full Body ontem (daysSinceLastFb == 1) dentro da janela de 48h
            val isPostFullBodyRecoveryToday = lastFbHist != null && daysSinceLastFb == 1 && hoursSinceLastFb < 48.0

            if (advisory.isRestDayAccepted) {
                items.add(createMobilityCarouselItem())
            }

            if (isPostFullBodyRecoveryToday && !advisory.isRestDayAccepted) {
                // Card Principal (Index 0): DIA DE CORRIDA & RECUPERAÇÃO
                val recoveryRunItem = createRunningCarouselItem(
                    idx = 0,
                    customTitle = "DIA DE CORRIDA & RECUPERAÇÃO",
                    customSubtitle = "Ontem você concluiu o $lastCompletedFbName • Próxima musculação ($nextFbName) programada para amanhã",
                    customBadge = "INTERCALAÇÃO HÍBRIDA • CORRIDA",
                    customDuration = "Semana ${runProgress.weekNumber} • Dia ${runProgress.dayNumber}",
                    isSuggestedToday = true,
                    advanceGymWorkout = nextFbWorkout,
                    advanceGymLabel = "Antecipar $nextFbName para hoje"
                )
                items.add(recoveryRunItem)
                pinnedIndex = 0
            }

            // Adiciona os protocolos Full Body na ordem da rotação (começando pelo próximo da fila)
            val orderedFbProtocols = buildList {
                for (i in coreFbRotation.indices) {
                    add(coreFbRotation[(nextFbIdx + i) % coreFbRotation.size])
                }
                if (fbProtocols.size > 3) {
                    add(fbProtocols[3])
                }
            }

            orderedFbProtocols.forEachIndexed { idx, proto ->
                val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(proto)
                val isPrimaryGymTarget = !advisory.isRestDayAccepted && !isPostFullBodyRecoveryToday && (idx == 0)
                val badge = when {
                    advisory.isRestDayAccepted && idx == 0 -> "FILA AJUSTADA (AMANHÃ)"
                    isPostFullBodyRecoveryToday && idx == 0 -> "PROGRAMADO PARA AMANHÃ"
                    isPrimaryGymTarget -> "TREINO DE HOJE"
                    else -> "ROTAÇÃO FULL BODY"
                }
                val muscles = proto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                val subtitle = if (isPostFullBodyRecoveryToday && idx == 0) {
                    "Programado para amanhã (pós-recuperação de 48h do $lastCompletedFbName) • $muscles"
                } else {
                    muscles
                }
                val item = com.example.data.model.HeroCarouselItem(
                    id = proto.id,
                    workout = w,
                    isRunning = false,
                    isFixedOrSuggested = isPrimaryGymTarget,
                    badgeLabel = badge,
                    title = proto.name,
                    subtitle = subtitle,
                    durationText = "45-55 min"
                )
                items.add(item)
                if (isPrimaryGymTarget) {
                    pinnedIndex = items.size - 1
                }
            }

            if (!isPostFullBodyRecoveryToday) {
                items.add(
                    createRunningCarouselItem(
                        idx = 0,
                        customTitle = runWorkoutHeaderLabel,
                        customSubtitle = "Sessão aeróbica do MAX RUN • Cardio, Resistência & VO2 Max",
                        customBadge = "SESSÃO AERÓBICA"
                    )
                )
            }
            if (!advisory.isRestDayAccepted) {
                items.add(createMobilityCarouselItem())
            }

            val finalItems = items.take(7)
            val finalPinnedIndex = if (advisory.isRestDayAccepted) 0 else pinnedIndex.coerceIn(0, (finalItems.size - 1).coerceAtLeast(0))
            return Pair(finalItems, finalPinnedIndex)
        }

        // 2. Caso OUTRO PROTOCOLO ESTRUTURADO (ex: PPL)
        if (prefProtoId != null) {
            val targetProto = allProtocols.firstOrNull { it.id == prefProtoId } ?: allProtocols.first()
            val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetProto)
            val muscles = targetProto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")

            val daysSinceLastGym = if (lastGymHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.calculateCalendarDaysBetween(lastGymHist.date)
            } else 999
            val lastCompletedGymName = if (lastGymHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.extractCleanWorkoutTitle(
                    lastGymHist.workoutName,
                    lastGymHist.workoutId
                )
            } else "Musculação"

            val items = mutableListOf<com.example.data.model.HeroCarouselItem>()
            if (advisory.isRestDayAccepted) {
                items.add(createMobilityCarouselItem())
            }

            if (isHybrid && daysSinceLastGym == 1 && !advisory.isRestDayAccepted) {
                items.add(
                    createRunningCarouselItem(
                        idx = 0,
                        customTitle = "DIA DE CORRIDA & RECUPERAÇÃO",
                        customSubtitle = "Ontem você concluiu o $lastCompletedGymName • Próxima musculação (${targetProto.name}) programada para amanhã",
                        customBadge = "INTERCALAÇÃO HÍBRIDA • CORRIDA",
                        customDuration = "Semana ${runProgress.weekNumber} • Dia ${runProgress.dayNumber}",
                        isSuggestedToday = true,
                        advanceGymWorkout = w,
                        advanceGymLabel = "Antecipar ${targetProto.name} para hoje"
                    )
                )
            }

            val mainItem = com.example.data.model.HeroCarouselItem(
                id = targetProto.id,
                workout = w,
                isRunning = false,
                isFixedOrSuggested = !advisory.isRestDayAccepted && !(isHybrid && daysSinceLastGym == 1),
                badgeLabel = when {
                    advisory.isRestDayAccepted -> "FILA AJUSTADA (AMANHÃ)"
                    isHybrid && daysSinceLastGym == 1 -> "PROGRAMADO PARA AMANHÃ"
                    else -> "PLANO FIXADO"
                },
                title = targetProto.name,
                subtitle = muscles,
                durationText = "45-55 min"
            )
            items.add(mainItem)
            if (isHybrid && daysSinceLastGym != 1) {
                items.add(createRunningCarouselItem(0))
            }
            allProtocols.filter { it.category == "FULL_BODY" }.take(2).forEach { proto ->
                val fbW = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(proto)
                val fbMuscles = proto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                items.add(
                    com.example.data.model.HeroCarouselItem(
                        id = proto.id,
                        workout = fbW,
                        isRunning = false,
                        isFixedOrSuggested = false,
                        badgeLabel = "PROTOCOLO FULL BODY",
                        title = proto.name,
                        subtitle = fbMuscles,
                        durationText = "45-55 min"
                    )
                )
            }
            if (!isHybrid) {
                items.add(createRunningCarouselItem(0))
            }
            if (!advisory.isRestDayAccepted) {
                items.add(createMobilityCarouselItem())
            }
            val finalItems = items.take(7)
            return Pair(finalItems, 0)
        }

        // 3. Caso NENHUM PROTOCOLO ESTRUTURADO FIXADO (Estado Padrão / IA Ativa):
        val items = mutableListOf<com.example.data.model.HeroCarouselItem>()
        if (advisory.isRestDayAccepted) {
            items.add(createMobilityCarouselItem())
        }
        val prefWorkoutId = _preferredWorkoutId.value
        val savedWorkouts = workouts.value.filter { it.name.isNotBlank() }
        val foundCustom = if (prefWorkoutId != null) savedWorkouts.firstOrNull { it.id == prefWorkoutId } else null

        val card0 = if (foundCustom != null) {
            val exs = try { com.example.utils.JsonUtils.parseWorkoutExercises(foundCustom.exercisesJson) } catch (e: Exception) { emptyList() }
            val muscles = exs.map { it.category }.filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }.distinct().take(3).joinToString(", ")
            com.example.data.model.HeroCarouselItem(
                id = foundCustom.id,
                workout = foundCustom,
                isRunning = false,
                isFixedOrSuggested = !advisory.isRestDayAccepted,
                badgeLabel = if (advisory.isRestDayAccepted) "FILA AJUSTADA (AMANHÃ)" else "PLANO FIXADO",
                title = foundCustom.name,
                subtitle = muscles.ifBlank { "Força e Hipertrofia" },
                durationText = "${(exs.size * 7 + 15).coerceIn(40, 75)} min"
            )
        } else {
            val suggested = getSuggestedPlanWorkout()
            val exs = try { com.example.utils.JsonUtils.parseWorkoutExercises(suggested.exercisesJson) } catch (e: Exception) { emptyList() }
            val muscles = exs.map { it.category }.filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }.distinct().take(3).joinToString(", ")
            com.example.data.model.HeroCarouselItem(
                id = suggested.id,
                workout = suggested,
                isRunning = suggested.id.contains("RUNNING") || suggested.name.lowercase().contains("corrida"),
                isFixedOrSuggested = true,
                badgeLabel = if (advisory.isRestDayAccepted) "DESCANSO ATIVO" else "SUGERIDO HOJE",
                title = suggested.name,
                subtitle = muscles.ifBlank { "Hipertrofia & Força" },
                durationText = "${(exs.size * 7 + 15).coerceIn(40, 75)} min"
            )
        }
        if (!advisory.isRestDayAccepted || card0.id != "WORKOUT_ACTIVE_RECOVERY_MOBILITY") {
            items.add(card0)
        }

        val fbShowcase = allProtocols.filter { it.category == "FULL_BODY" }.take(3)
        fbShowcase.forEach { proto ->
            val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(proto)
            val muscles = proto.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
            items.add(
                com.example.data.model.HeroCarouselItem(
                    id = proto.id,
                    workout = w,
                    isRunning = false,
                    isFixedOrSuggested = false,
                    badgeLabel = "PROTOCOLO FULL BODY",
                    title = proto.name,
                    subtitle = muscles,
                    durationText = "45-55 min"
                )
            )
        }

        items.add(createRunningCarouselItem(0))
        if (!advisory.isRestDayAccepted) {
            items.add(createMobilityCarouselItem())
        }

        val finalItems = items.take(7)
        val finalPinnedIndex = 0.coerceIn(0, (finalItems.size - 1).coerceAtLeast(0))
        return Pair(finalItems, finalPinnedIndex)
    }

    /**
     * Retorna o Cronograma da Semana Atual Completa (Segunda a Domingo - 7 dias).
     * - Dias passados da semana: exibe histórico real de treinos concluídos (Musculação / Corrida) com selo ✓.
     * - Dia de Hoje: destaca como selecionado por padrão, respeitando a janela de 48h pós-Full Body (Dia de Corrida & Recuperação).
     * - Dias futuros da semana: exibe a programação real respeitando a intercalação híbrida (Musculação <-> Corrida).
     */
    fun getUpcoming7DaysSchedule(): List<com.example.data.model.CalendarDaySchedule> {
        val prefProtoId = preferredProtocolId.value
        val isHybrid = isHybridRunningEnabled.value
        val hist = history.value
        val runHist = runningSessions.value
        val cardioHist = cardioSessions.value
        val advisory = fatigueAdvisoryState.value
        val isRestAcceptedToday = advisory.isRestDayAccepted
        val dayNames = arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")

        val todayCalendar = java.util.Calendar.getInstance()
        val todayDow = todayCalendar.get(java.util.Calendar.DAY_OF_WEEK)
        // Posição de Hoje dentro da semana Segunda(0) .. Domingo(6)
        val daysSinceMonday = if (todayDow == java.util.Calendar.SUNDAY) 6 else (todayDow - java.util.Calendar.MONDAY)

        val lastFbHist = hist.filter { h ->
            h.workoutName.contains("Full Body", ignoreCase = true) || h.workoutId.contains("protocol_fb", ignoreCase = true)
        }.maxByOrNull { it.date }

        val isFullBody = prefProtoId == "FULL_BODY" ||
            (prefProtoId != null && prefProtoId.startsWith("protocol_fb")) ||
            prefProtoId == null ||
            lastFbHist != null

        val runProgId = selectedRunningProgramId.value
        val runProgInfo = com.example.engine.RunningProgramManager.getProgramById(runProgId)
        val runProgress = com.example.engine.RunningProgramManager.calculateCurrentProgress(runHist)

        fun buildPastCompletedCardData(
            dateShortStr: String,
            gymEntry: com.example.data.model.WorkoutHistoryEntry?,
            runEntry: com.example.data.model.RunningSession?,
            cardioEntry: com.example.data.model.CardioSession?
        ): com.example.data.model.PeriodizedWorkoutCardData? {
            if (gymEntry != null) {
                val cleanGymTitle = com.example.services.recommendation.engine001.TrainingEngine001
                    .extractCleanWorkoutTitle(gymEntry.workoutName, gymEntry.workoutId)
                val combinedTitle = if (runEntry != null) "$cleanGymTitle + Corrida" else cleanGymTitle
                val exs = try {
                    com.example.utils.JsonUtils.parseWorkoutExercises(gymEntry.exercisesJson)
                } catch (_: Exception) {
                    emptyList()
                }
                val muscles = exs.map { it.category }
                    .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                    .distinct()
                    .take(3)
                    .joinToString(", ")
                    .ifBlank { "Corpo Inteiro" }
                val durMins = (gymEntry.durationSeconds / 60).coerceAtLeast(1)
                val w = com.example.data.model.Workout(
                    id = gymEntry.workoutId.ifBlank { "HIST_${gymEntry.id}" },
                    name = combinedTitle,
                    description = "Concluído em $dateShortStr",
                    exercisesJson = gymEntry.exercisesJson,
                    createdAt = gymEntry.date
                )
                return com.example.data.model.PeriodizedWorkoutCardData(
                    workout = w,
                    title = combinedTitle,
                    muscleGroups = muscles,
                    durationText = "$durMins min",
                    sequenceTag = "$dateShortStr • Concluído ✓",
                    exerciseCount = exs.size
                )
            }
            if (runEntry != null) {
                val runTitle = com.example.engine.RunningProgramManager.getDisplayTitleForSession(runEntry)
                val distKm = String.format(java.util.Locale.getDefault(), "%.2f km", runEntry.distanceMeters / 1000f)
                val durMins = (runEntry.durationSec / 60).coerceAtLeast(1)
                val w = com.example.data.model.Workout(
                    id = "WORKOUT_RUNNING_SESSION",
                    templateId = "RUNNING",
                    name = runTitle,
                    description = "Concluído em $dateShortStr",
                    exercisesJson = "[]"
                )
                return com.example.data.model.PeriodizedWorkoutCardData(
                    workout = w,
                    title = runTitle,
                    muscleGroups = "$distKm • Sessão Aeróbica",
                    durationText = "$durMins min",
                    sequenceTag = "$dateShortStr • Concluído ✓",
                    exerciseCount = 0
                )
            }
            if (cardioEntry != null) {
                val cardioTitle = "Cardio (${cardioEntry.type})"
                val w = com.example.data.model.Workout(
                    id = "WORKOUT_CARDIO_SESSION",
                    name = cardioTitle,
                    description = "Concluído em $dateShortStr",
                    exercisesJson = "[]"
                )
                return com.example.data.model.PeriodizedWorkoutCardData(
                    workout = w,
                    title = cardioTitle,
                    muscleGroups = "Condicionamento Aeróbico",
                    durationText = "${cardioEntry.durationMinutes} min",
                    sequenceTag = "$dateShortStr • Concluído ✓",
                    exerciseCount = 0
                )
            }
            return null
        }

        if (isFullBody) {
            val allFbProtocols = com.example.data.repository.WorkoutProtocolsRepository.ALL_PROTOCOLS.filter { it.category == "FULL_BODY" }
            val coreFbProtocols = allFbProtocols.take(3) // Full Body A -> B -> C -> A
            val schedule = mutableListOf<com.example.data.model.CalendarDaySchedule>()

            val lastIdx = if (lastFbHist != null) {
                when {
                    lastFbHist.workoutName.contains("Full Body A", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_a", ignoreCase = true) -> 0
                    lastFbHist.workoutName.contains("Full Body B", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_b", ignoreCase = true) -> 1
                    lastFbHist.workoutName.contains("Full Body C", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_c", ignoreCase = true) -> 2
                    lastFbHist.workoutName.contains("Full Body D", ignoreCase = true) || lastFbHist.workoutId.contains("protocol_fb_d", ignoreCase = true) -> 2
                    else -> -1
                }
            } else -1

            val startFbIdx = if (lastIdx != -1) {
                (lastIdx + 1) % coreFbProtocols.size
            } else if (prefProtoId != null && prefProtoId.startsWith("protocol_fb_")) {
                coreFbProtocols.indexOfFirst { it.id == prefProtoId }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            val daysSinceLastFb = if (lastFbHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.calculateCalendarDaysBetween(lastFbHist.date)
            } else 999
            val hoursSinceLastFb = if (lastFbHist != null) {
                (System.currentTimeMillis() - lastFbHist.date) / (1000.0 * 3600.0)
            } else 999.0

            val lastCompletedFbName = if (lastFbHist != null) {
                com.example.services.recommendation.engine001.TrainingEngine001.extractCleanWorkoutTitle(
                    lastFbHist.workoutName,
                    lastFbHist.workoutId
                )
            } else "Full Body"
            val nextFbName = coreFbProtocols[startFbIdx % coreFbProtocols.size].name

            // Se treinou Full Body ontem (ex: 23/10), hoje (24/10) é Dia de Corrida & Recuperação (janela de 48h)
            val todayIsPostFbRecovery = lastFbHist != null && daysSinceLastFb == 1 && hoursSinceLastFb < 48.0

            var fbCounter = startFbIdx
            var prevDayWasGym = false

            for (dayIdx in 0..6) {
                val offsetFromToday = dayIdx - daysSinceMonday
                val cal = (todayCalendar.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, offsetFromToday)
                }
                val dayOfWeekConst = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val dayOfWeekStr = dayNames[dayOfWeekConst - 1]
                val dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
                val isToday = (offsetFromToday == 0)
                val dateShortStr = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(cal.time)

                val startOfDay = (cal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfDay = startOfDay + (24L * 60 * 60 * 1000)

                val gymOnThisDay = hist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
                val runOnThisDay = runHist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
                val cardioOnThisDay = cardioHist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
                val completedOnThisDay = gymOnThisDay != null || runOnThisDay != null || cardioOnThisDay != null

                // 1. DIAS PASSADOS DA SEMANA ATUAL (ex: Segunda a Sexta quando hoje é Sábado)
                if (offsetFromToday < 0) {
                    val pastCardData = buildPastCompletedCardData(dateShortStr, gymOnThisDay, runOnThisDay, cardioOnThisDay)
                    schedule.add(
                        com.example.data.model.CalendarDaySchedule(
                            dateMillis = cal.timeInMillis,
                            dayOfWeek = dayOfWeekStr,
                            dayOfMonth = dayOfMonth,
                            isToday = false,
                            isRestDay = pastCardData == null,
                            workoutCard = pastCardData,
                            isCompleted = completedOnThisDay
                        )
                    )
                    continue
                }

                // 2. DIA DE HOJE
                if (isToday) {
                    if (isRestAcceptedToday) {
                        val mobWorkout = getMobilityRecoveryWorkout()
                        val restCardData = com.example.data.model.PeriodizedWorkoutCardData(
                            workout = mobWorkout,
                            title = "Mobilidade Regenerativa",
                            muscleGroups = "Recuperação do SNC e Articulações",
                            durationText = "15 min",
                            sequenceTag = "Hoje • Descanso Ativo",
                            exerciseCount = 4
                        )
                        schedule.add(
                            com.example.data.model.CalendarDaySchedule(
                                dateMillis = cal.timeInMillis,
                                dayOfWeek = dayOfWeekStr,
                                dayOfMonth = dayOfMonth,
                                isToday = true,
                                isRestDay = true,
                                workoutCard = restCardData,
                                isCompleted = true
                            )
                        )
                        prevDayWasGym = false
                        continue
                    }

                    if (completedOnThisDay && gymOnThisDay != null) {
                        val todayCompletedCard = buildPastCompletedCardData(dateShortStr, gymOnThisDay, runOnThisDay, cardioOnThisDay)
                        schedule.add(
                            com.example.data.model.CalendarDaySchedule(
                                dateMillis = cal.timeInMillis,
                                dayOfWeek = dayOfWeekStr,
                                dayOfMonth = dayOfMonth,
                                isToday = true,
                                isRestDay = false,
                                workoutCard = todayCompletedCard,
                                isCompleted = true
                            )
                        )
                        prevDayWasGym = true
                        continue
                    }

                    if (todayIsPostFbRecovery) {
                        val runTitle = "Dia de Corrida & Recuperação"
                        val runSubtitle = "Ontem: $lastCompletedFbName • Próxima musculação ($nextFbName) amanhã"
                        val runWorkout = com.example.data.model.Workout(
                            id = "WORKOUT_RUNNING_SESSION",
                            templateId = "RUNNING",
                            name = runTitle,
                            description = runSubtitle,
                            exercisesJson = "[]",
                            createdAt = System.currentTimeMillis()
                        )
                        val cardData = com.example.data.model.PeriodizedWorkoutCardData(
                            workout = runWorkout,
                            title = runTitle,
                            muscleGroups = "$runSubtitle • Semana ${runProgress.weekNumber} Dia ${runProgress.dayNumber} (${runProgInfo.title})",
                            durationText = "30-45 min",
                            sequenceTag = "Hoje • Corrida & Recuperação",
                            exerciseCount = 0
                        )
                        schedule.add(
                            com.example.data.model.CalendarDaySchedule(
                                dateMillis = cal.timeInMillis,
                                dayOfWeek = dayOfWeekStr,
                                dayOfMonth = dayOfMonth,
                                isToday = true,
                                isRestDay = false,
                                workoutCard = cardData,
                                isCompleted = completedOnThisDay
                            )
                        )
                        prevDayWasGym = false
                        continue
                    }

                    // Hoje é dia de Musculação Full Body
                    val targetFb = coreFbProtocols[fbCounter % coreFbProtocols.size]
                    fbCounter++
                    val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetFb)
                    val muscles = targetFb.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                    val cardData = com.example.data.model.PeriodizedWorkoutCardData(
                        workout = w,
                        title = targetFb.name,
                        muscleGroups = muscles,
                        durationText = "45-55 min",
                        sequenceTag = "Hoje • Musculação",
                        exerciseCount = targetFb.exercises.size
                    )
                    schedule.add(
                        com.example.data.model.CalendarDaySchedule(
                            dateMillis = cal.timeInMillis,
                            dayOfWeek = dayOfWeekStr,
                            dayOfMonth = dayOfMonth,
                            isToday = true,
                            isRestDay = false,
                            workoutCard = cardData,
                            isCompleted = completedOnThisDay
                        )
                    )
                    prevDayWasGym = true
                    continue
                }

                // 3. DIAS FUTUROS DA SEMANA ATUAL (offsetFromToday > 0)
                val tag = when (offsetFromToday) {
                    1 -> if (isRestAcceptedToday) "Amanhã (Fila Deslocada)" else "Amanhã"
                    else -> "+$offsetFromToday dias"
                }

                // Se ontem/dia anterior na fila foi Musculação Full Body, o dia seguinte respeita a janela de 48h (Corrida/Recuperação)
                val isRunningOrRecoveryThisDay = prevDayWasGym

                val cardData = if (isRunningOrRecoveryThisDay) {
                    prevDayWasGym = false
                    if (isHybrid) {
                        val runWorkout = com.example.data.model.Workout(
                            id = "WORKOUT_RUNNING_SESSION",
                            templateId = "RUNNING",
                            name = "Dia de Corrida & Recuperação",
                            description = "Sessão aeróbica no Módulo de Corrida",
                            exercisesJson = "[]",
                            createdAt = System.currentTimeMillis()
                        )
                        com.example.data.model.PeriodizedWorkoutCardData(
                            workout = runWorkout,
                            title = "Treino de Corrida",
                            muscleGroups = "Cardio, Resistência & VO2 Max",
                            durationText = "30-45 min",
                            sequenceTag = "$tag • Corrida",
                            exerciseCount = 0
                        )
                    } else {
                        null
                    }
                } else {
                    prevDayWasGym = true
                    val targetFb = coreFbProtocols[fbCounter % coreFbProtocols.size]
                    fbCounter++
                    val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(targetFb)
                    val muscles = targetFb.exercises.map { it.muscleGroup }.distinct().take(3).joinToString(", ")
                    com.example.data.model.PeriodizedWorkoutCardData(
                        workout = w,
                        title = targetFb.name,
                        muscleGroups = muscles,
                        durationText = "45-55 min",
                        sequenceTag = "$tag • Musculação",
                        exerciseCount = targetFb.exercises.size
                    )
                }

                schedule.add(
                    com.example.data.model.CalendarDaySchedule(
                        dateMillis = cal.timeInMillis,
                        dayOfWeek = dayOfWeekStr,
                        dayOfMonth = dayOfMonth,
                        isToday = false,
                        isRestDay = cardData == null,
                        workoutCard = cardData,
                        isCompleted = completedOnThisDay
                    )
                )
            }
            return schedule
        }

        val periodizedSequence = getPeriodizedWorkoutSequence()
        val freq = userProfile.value?.metaSemanal ?: 5
        val schedule = mutableListOf<com.example.data.model.CalendarDaySchedule>()

        var workoutIndex = 0

        for (dayIdx in 0..6) {
            val offsetFromToday = dayIdx - daysSinceMonday
            val cal = (todayCalendar.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, offsetFromToday)
            }
            val dayOfWeekConst = cal.get(java.util.Calendar.DAY_OF_WEEK)
            val dayOfWeekStr = dayNames[dayOfWeekConst - 1]
            val dayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val isToday = (offsetFromToday == 0)
            val dateShortStr = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(cal.time)

            val startOfDay = (cal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val endOfDay = startOfDay + (24L * 60 * 60 * 1000)

            val gymOnThisDay = hist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
            val runOnThisDay = runHist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
            val cardioOnThisDay = cardioHist.filter { it.date in startOfDay until endOfDay }.maxByOrNull { it.date }
            val completedOnThisDay = gymOnThisDay != null || runOnThisDay != null || cardioOnThisDay != null

            if (offsetFromToday < 0) {
                val pastCardData = buildPastCompletedCardData(dateShortStr, gymOnThisDay, runOnThisDay, cardioOnThisDay)
                schedule.add(
                    com.example.data.model.CalendarDaySchedule(
                        dateMillis = cal.timeInMillis,
                        dayOfWeek = dayOfWeekStr,
                        dayOfMonth = dayOfMonth,
                        isToday = false,
                        isRestDay = pastCardData == null,
                        workoutCard = pastCardData,
                        isCompleted = completedOnThisDay
                    )
                )
                continue
            }

            if (isToday && isRestAcceptedToday) {
                val mobWorkout = getMobilityRecoveryWorkout()
                val restCardData = com.example.data.model.PeriodizedWorkoutCardData(
                    workout = mobWorkout,
                    title = "Descanso / Mobilidade Leve",
                    muscleGroups = "Recuperação SNC & Articulações • Ofensiva Mantida",
                    durationText = "15-20 min",
                    sequenceTag = "Hoje • Descanso Ativo",
                    exerciseCount = 4
                )
                schedule.add(
                    com.example.data.model.CalendarDaySchedule(
                        dateMillis = cal.timeInMillis,
                        dayOfWeek = dayOfWeekStr,
                        dayOfMonth = dayOfMonth,
                        isToday = true,
                        isRestDay = true,
                        workoutCard = restCardData,
                        isCompleted = true
                    )
                )
                continue
            }

            val isRestDay = if (isRestAcceptedToday && offsetFromToday == 1) {
                false
            } else {
                when (freq) {
                    1, 2 -> dayOfWeekConst !in listOf(java.util.Calendar.TUESDAY, java.util.Calendar.THURSDAY)
                    3 -> dayOfWeekConst !in listOf(java.util.Calendar.MONDAY, java.util.Calendar.WEDNESDAY, java.util.Calendar.FRIDAY)
                    4 -> dayOfWeekConst in listOf(java.util.Calendar.WEDNESDAY, java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
                    5 -> dayOfWeekConst in listOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
                    6 -> dayOfWeekConst == java.util.Calendar.SUNDAY
                    else -> false
                }
            }

            val cardData = if (completedOnThisDay && isToday) {
                buildPastCompletedCardData(dateShortStr, gymOnThisDay, runOnThisDay, cardioOnThisDay)
            } else if (!isRestDay && periodizedSequence.isNotEmpty()) {
                val assigned = periodizedSequence[workoutIndex % periodizedSequence.size]
                workoutIndex++
                val tag = when (offsetFromToday) {
                    0 -> "Hoje"
                    1 -> if (isRestAcceptedToday) "Amanhã (Fila Deslocada)" else "Amanhã"
                    else -> "+$offsetFromToday dias"
                }
                assigned.copy(sequenceTag = tag)
            } else {
                null
            }

            schedule.add(
                com.example.data.model.CalendarDaySchedule(
                    dateMillis = cal.timeInMillis,
                    dayOfWeek = dayOfWeekStr,
                    dayOfMonth = dayOfMonth,
                    isToday = isToday,
                    isRestDay = isRestDay && !completedOnThisDay,
                    workoutCard = cardData,
                    isCompleted = completedOnThisDay
                )
            )
        }

        return schedule
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        tickerJob = null
        runCatching { com.example.utils.GymAudioEngine.stopAllAndRelease(getApplication()) }
        if (!com.example.MainActivity.isRunningSessionActive) {
            runCatching { com.example.utils.AudioSynth.abandonAudioFocus(getApplication()) }
        }
    }
}

data class PrCelebration(
    val exerciseName: String,
    val isWeightPR: Boolean,
    val value: String,
    val previousValue: String,
    val exerciseIndex: Int,
    val setIndex: Int,
    val setId: String,
    val reps: Int,
    val weight: Float
)

data class RpePrompt(
    val exerciseIndex: Int,
    val setIndex: Int,
    val exerciseName: String,
    val reps: Int,
    val weight: Float,
    val setId: String
)

data class WorkoutWarningData(
    val workout: Workout,
    val muscleName: String,
    val lastTrainedDateStr: String,
    val daysAgo: Int,
    val isFullBodyWarning: Boolean = false,
    val lastWorkoutName: String = "",
    val targetWorkoutName: String = "",
    val customMessage: String? = null
)

data class CompletedWorkoutSummary(
    val workoutName: String,
    val durationSeconds: Int,
    val totalVolume: Float,
    val exercisesCompletedCount: Int,
    val setsCompletedCount: Int,
    val isEarly: Boolean,
    val totalPlannedSets: Int,
    val estimatedDurationSeconds: Int,
    val fatigueUsed: Double,
    val fatigueLimit: Double,
    val completedExercises: List<WorkoutExercise> = emptyList()
)
