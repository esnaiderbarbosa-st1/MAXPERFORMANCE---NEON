package com.example.data.repository

import android.content.SharedPreferences
import android.os.SystemClock
import com.example.data.model.ActiveWorkoutSession
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSessionEntity
import com.example.data.model.WorkoutSessionStatus
import com.example.data.model.WorkoutTimeUtils
import com.example.utils.JsonUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/**
 * Coordinates MAX GYM active workout session recovery with Room as the primary
 * source of truth and SharedPreferences as a validated, non-resurrecting fallback.
 */
object WorkoutSessionRecoveryCoordinator {

    const val KEY_HAS_ACTIVE_SESSION = "has_active_session"
    const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    const val KEY_ACTIVE_SESSION_START_TIME = "active_session_start_time"
    const val KEY_ACTIVE_SESSION_BOOT_COUNT = "active_session_boot_count"
    const val KEY_ACTIVE_WORKOUT_SESSION_JSON = "active_workout_session_json"
    const val KEY_ACTIVE_WORKOUT_JSON = "active_workout_json"
    const val KEY_ACTIVE_EXERCISES_JSON = "active_exercises_json"
    const val KEY_ACTIVE_CURRENT_EXERCISE_INDEX = "active_current_exercise_index"
    const val KEY_ACTIVE_WORKOUT_DURATION_SECONDS = "active_workout_duration_seconds"
    const val KEY_ACTIVE_IS_REST_TIMER_ACTIVE = "active_is_rest_timer_active"
    const val KEY_ACTIVE_REST_TIME_REMAINING = "active_rest_time_remaining"
    const val KEY_ACTIVE_REST_TIME_TOTAL = "active_rest_time_total"
    const val KEY_ACTIVE_IS_TEMPO_TIMER_ACTIVE = "active_is_tempo_timer_active"
    const val KEY_ACTIVE_TEMPO_TIME_REMAINING = "active_tempo_time_remaining"
    const val KEY_ACTIVE_TEMPO_TIME_TOTAL = "active_tempo_time_total"
    const val KEY_ACTIVE_IS_PRE_SESSION_ACTIVE = "active_is_pre_session_active"
    const val KEY_ACTIVE_WORKOUT_FLOW_STATE = "active_workout_flow_state"
    const val KEY_ACTIVE_SESSION_TIMESTAMP = "active_session_timestamp"
    const val KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS = "active_session_default_rest_seconds"

    private const val NO_BOOT_COUNT = -1
    private const val MAX_BOOT_EPOCH_DRIFT_MS = 120_000L

    enum class RecoverySource {
        NONE,
        ROOM,
        SHARED_PREFS_FALLBACK,
        FAILED
    }

    data class StartupRecoveryOutcome(
        val activeSession: WorkoutSessionEntity?,
        val source: RecoverySource,
        val isDecisionValid: Boolean = source != RecoverySource.FAILED,
        val archivedOldSessionsCount: Int = 0,
        val archivedNotificationMessage: String? = null,
        val purgedStaleOrCorruptFallback: Boolean = false
    )

    data class RestoredSessionPayload(
        val sessionId: String,
        val workout: Workout,
        val exercises: List<WorkoutExercise>,
        val currentExerciseIndex: Int,
        val elapsedDurationSeconds: Long,
        val startTimeMillis: Long,
        val defaultRestSeconds: Int,
        val source: RecoverySource
    )

    private data class FallbackCandidate(
        val sessionId: String,
        val workout: Workout,
        val exercises: List<WorkoutExercise>,
        val exercisesJson: String,
        val currentExerciseIndex: Int,
        val startTimeMillis: Long,
        val savedTimestampMillis: Long,
        val persistedDurationSeconds: Long,
        val savedBootCount: Int?,
        val defaultRestSeconds: Int
    )

    private fun safeGetBoolean(prefs: SharedPreferences, key: String, defaultValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    private fun safeGetInt(prefs: SharedPreferences, key: String, defaultValue: Int): Int {
        return try {
            prefs.getInt(key, defaultValue)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    private fun safeGetLong(prefs: SharedPreferences, key: String, defaultValue: Long): Long {
        return try {
            prefs.getLong(key, defaultValue)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    private fun safeGetString(prefs: SharedPreferences, key: String, defaultValue: String? = null): String? {
        return try {
            prefs.getString(key, defaultValue)
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    fun clearFallbackPrefs(sharedPrefs: SharedPreferences) {
        sharedPrefs.edit()
            .putBoolean(KEY_HAS_ACTIVE_SESSION, false)
            .remove(KEY_ACTIVE_SESSION_ID)
            .remove(KEY_ACTIVE_SESSION_START_TIME)
            .remove(KEY_ACTIVE_SESSION_BOOT_COUNT)
            .remove(KEY_ACTIVE_WORKOUT_SESSION_JSON)
            .remove(KEY_ACTIVE_WORKOUT_JSON)
            .remove(KEY_ACTIVE_EXERCISES_JSON)
            .remove(KEY_ACTIVE_CURRENT_EXERCISE_INDEX)
            .remove(KEY_ACTIVE_WORKOUT_DURATION_SECONDS)
            .remove(KEY_ACTIVE_IS_REST_TIMER_ACTIVE)
            .remove(KEY_ACTIVE_REST_TIME_REMAINING)
            .remove(KEY_ACTIVE_REST_TIME_TOTAL)
            .remove(KEY_ACTIVE_IS_TEMPO_TIMER_ACTIVE)
            .remove(KEY_ACTIVE_TEMPO_TIME_REMAINING)
            .remove(KEY_ACTIVE_TEMPO_TIME_TOTAL)
            .remove(KEY_ACTIVE_IS_PRE_SESSION_ACTIVE)
            .remove(KEY_ACTIVE_WORKOUT_FLOW_STATE)
            .remove(KEY_ACTIVE_SESSION_TIMESTAMP)
            .remove(KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS)
            .apply()
    }

    fun hasAnyFallbackData(sharedPrefs: SharedPreferences): Boolean {
        return safeGetBoolean(sharedPrefs, KEY_HAS_ACTIVE_SESSION, false) ||
            sharedPrefs.contains(KEY_ACTIVE_WORKOUT_SESSION_JSON) ||
            sharedPrefs.contains(KEY_ACTIVE_WORKOUT_JSON) ||
            sharedPrefs.contains(KEY_ACTIVE_EXERCISES_JSON) ||
            sharedPrefs.contains(KEY_ACTIVE_SESSION_ID) ||
            sharedPrefs.contains(KEY_ACTIVE_SESSION_START_TIME) ||
            sharedPrefs.contains(KEY_ACTIVE_SESSION_BOOT_COUNT) ||
            sharedPrefs.contains(KEY_ACTIVE_WORKOUT_FLOW_STATE)
    }

    fun readSavedBootCount(sharedPrefs: SharedPreferences): Int? {
        val raw = safeGetInt(sharedPrefs, KEY_ACTIVE_SESSION_BOOT_COUNT, NO_BOOT_COUNT)
        return if (raw == NO_BOOT_COUNT) null else raw
    }

    fun saveSessionMetadata(
        sharedPrefs: SharedPreferences,
        sessionId: String,
        startTimeMillis: Long,
        bootCount: Int?
    ) {
        val editor = sharedPrefs.edit()
            .putString(KEY_ACTIVE_SESSION_ID, sessionId)
            .putLong(KEY_ACTIVE_SESSION_START_TIME, startTimeMillis)
        if (bootCount != null) {
            editor.putInt(KEY_ACTIVE_SESSION_BOOT_COUNT, bootCount)
        } else {
            editor.remove(KEY_ACTIVE_SESSION_BOOT_COUNT)
        }
        editor.apply()
    }

    fun parseAndSanitizeExercises(exercisesJson: String?): List<WorkoutExercise>? {
        if (exercisesJson.isNullOrBlank() || exercisesJson.trim() == "[]") return null
        val parsed = try {
            JsonUtils.parseWorkoutExercises(exercisesJson)
        } catch (_: Exception) {
            return null
        }
        if (parsed.isEmpty() || parsed.any { it.sets.isNullOrEmpty() }) {
            return null
        }
        return parsed.map { ex ->
            val safeSets = ex.sets.map { set ->
                val safeWeight = if (set.weight.isFinite()) set.weight.coerceIn(0f, 9999f) else 0f
                val safeBaseWeight = set.baseWeight?.takeIf { it.isFinite() }?.coerceIn(0f, 9999f)
                set.copy(
                    reps = set.reps.coerceIn(0, 999),
                    weight = safeWeight,
                    baseWeight = safeBaseWeight
                )
            }
            ex.copy(sets = safeSets)
        }
    }

    fun resolvePreferredExerciseIndex(exercises: List<WorkoutExercise>, preferredIndex: Int?): Int {
        if (exercises.isEmpty()) return 0
        if (preferredIndex != null && preferredIndex in exercises.indices) {
            return preferredIndex
        }
        val firstIncomplete = exercises.indexOfFirst { ex -> ex.sets.any { !it.completed } }
        return if (firstIncomplete in exercises.indices) firstIncomplete else 0
    }

    fun pickMostRecentActiveSession(sessions: List<WorkoutSessionEntity>): WorkoutSessionEntity {
        return sessions.maxWith(
            compareBy<WorkoutSessionEntity>({ it.startTime }, { it.version })
        )
    }

    /**
     * Resolves active session recovery on app startup:
     * 1. Room (status = ATIVO) always takes precedence over SharedPreferences.
     * 2. Multiple ATIVO sessions in Room are reconciled deterministically by startTime/version,
     *    preferring valid payloads so a corrupt newer row does not destroy a valid older active session.
     * 3. SharedPreferences fallback is consulted ONLY when Room has no valid ATIVO session,
     *    and is rejected + purged if the session is terminal in Room or data is corrupt/invalid.
     * 4. Valid fallback sessions are promoted into Room (status = ATIVO) so Room remains authoritative.
     * 5. On ANY unexpected infrastructure failure (Room or SharedPreferences), returns FAILED
     *    with isDecisionValid = false and NEVER erases fallback data.
     */
    suspend fun resolveStartupRecovery(
        repository: FitnessRepository,
        sharedPrefs: SharedPreferences,
        currentBootCount: Int? = null,
        nowMonotonic: Long = SystemClock.elapsedRealtime(),
        nowNanos: Long = System.nanoTime(),
        nowWallMillis: Long = System.currentTimeMillis()
    ): StartupRecoveryOutcome {
        return try {
            resolveStartupRecoveryInternal(
                repository = repository,
                sharedPrefs = sharedPrefs,
                currentBootCount = currentBootCount,
                nowMonotonic = nowMonotonic,
                nowNanos = nowNanos,
                nowWallMillis = nowWallMillis
            )
        } catch (ce: CancellationException) {
            currentCoroutineContext().ensureActive()
            android.util.Log.e(
                "MAX_PERFORMANCE_DEBUG",
                "Cancelamento interno de infraestrutura (ex.: Room fechado) durante resolveStartupRecovery; decisão inválida e fallback preservado",
                ce
            )
            StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.FAILED,
                isDecisionValid = false
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "MAX_PERFORMANCE_DEBUG",
                "Falha de infraestrutura durante resolveStartupRecovery; decisão inválida e fallback preservado",
                e
            )
            StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.FAILED,
                isDecisionValid = false
            )
        }
    }

    private suspend fun resolveStartupRecoveryInternal(
        repository: FitnessRepository,
        sharedPrefs: SharedPreferences,
        currentBootCount: Int?,
        nowMonotonic: Long,
        nowNanos: Long,
        nowWallMillis: Long
    ): StartupRecoveryOutcome {
        val activeSessions = repository.getAllActiveWorkoutSessions()

        if (activeSessions.isNotEmpty()) {
            val validSessions = activeSessions.filter { parseAndSanitizeExercises(it.exercisesJson) != null }
            val chosen = if (validSessions.isNotEmpty()) {
                pickMostRecentActiveSession(validSessions)
            } else {
                null
            }
            val toArchive = if (chosen != null) {
                activeSessions.filter { it.id != chosen.id }
            } else {
                activeSessions
            }

            var archivedCount = 0
            val archivedNamesList = mutableListOf<String>()
            for (oldSession in toArchive) {
                val dur = WorkoutTimeUtils.calculateSafeDurationSeconds(
                    startTimeMonotonic = oldSession.startTimeMonotonic,
                    fallbackStartMillis = oldSession.startTime,
                    monotonicStartTimeNanos = oldSession.monotonicStartTimeNanos,
                    persistedDurationSeconds = oldSession.durationSeconds,
                    savedBootCount = null,
                    currentBootCount = currentBootCount,
                    nowMonotonic = nowMonotonic,
                    nowNanos = nowNanos,
                    nowWallMillis = nowWallMillis
                )
                val updated = repository.updateWorkoutSessionStatusVersioned(
                    sessionId = oldSession.id,
                    status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                    newVersion = oldSession.version + 1,
                    completedAt = nowWallMillis,
                    durationSeconds = dur
                )
                if (updated) {
                    archivedCount++
                    archivedNamesList.add(oldSession.workoutName)
                }
            }

            if (chosen != null) {
                val archivedNotification = if (archivedCount > 0) {
                    val archivedNames = archivedNamesList.joinToString(", ")
                    if (archivedCount == 1) {
                        "Uma sessão anterior incompleta ($archivedNames) foi arquivada automaticamente."
                    } else {
                        "$archivedCount sessões anteriores incompletas foram arquivadas automaticamente."
                    }
                } else {
                    null
                }
                return StartupRecoveryOutcome(
                    activeSession = chosen,
                    source = RecoverySource.ROOM,
                    isDecisionValid = true,
                    archivedOldSessionsCount = archivedCount,
                    archivedNotificationMessage = archivedNotification
                )
            }

            // All Room ATIVO rows were corrupted and have been archived as NAO_REALIZADO
            val prefsSid = safeGetString(sharedPrefs, KEY_ACTIVE_SESSION_ID, null)
            val purgedFallback = if (prefsSid != null && activeSessions.any { it.id == prefsSid }) {
                clearFallbackPrefs(sharedPrefs)
                true
            } else {
                false
            }
            return StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.NONE,
                isDecisionValid = true,
                archivedOldSessionsCount = archivedCount,
                purgedStaleOrCorruptFallback = purgedFallback
            )
        }

        // Room has 0 ATIVO sessions: consult validated SharedPreferences fallback
        val hadAnyPrefs = hasAnyFallbackData(sharedPrefs)
        if (!hadAnyPrefs) {
            return StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.NONE,
                isDecisionValid = true
            )
        }

        val candidate = parseFallbackCandidate(sharedPrefs, nowWallMillis)
        if (candidate == null) {
            clearFallbackPrefs(sharedPrefs)
            return StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.NONE,
                isDecisionValid = true,
                purgedStaleOrCorruptFallback = true
            )
        }

        // Anti-resurrection check: if Room already records this session in ANY terminal state,
        // Room wins strictly and stale fallback prefs are purged immediately.
        val existingById = repository.getWorkoutSessionById(candidate.sessionId)
        val existingByStart = if (existingById == null && candidate.startTimeMillis > 0L) {
            repository.getWorkoutSessionByWorkoutAndStartTime(
                workoutId = candidate.workout.id,
                startTime = candidate.startTimeMillis
            )
        } else {
            null
        }
        val existingRoomSession = existingById ?: existingByStart
        if (existingRoomSession != null) {
            val status = WorkoutSessionStatus.fromValue(existingRoomSession.status)
            if (status.isTerminal) {
                clearFallbackPrefs(sharedPrefs)
                return StartupRecoveryOutcome(
                    activeSession = null,
                    source = RecoverySource.NONE,
                    isDecisionValid = true,
                    purgedStaleOrCorruptFallback = true
                )
            }
            if (parseAndSanitizeExercises(existingRoomSession.exercisesJson) != null) {
                return StartupRecoveryOutcome(
                    activeSession = existingRoomSession,
                    source = RecoverySource.ROOM,
                    isDecisionValid = true
                )
            }
            repository.updateWorkoutSessionStatusVersioned(
                sessionId = existingRoomSession.id,
                status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                newVersion = existingRoomSession.version + 1,
                completedAt = nowWallMillis,
                durationSeconds = maxOf(0L, existingRoomSession.durationSeconds)
            )
            clearFallbackPrefs(sharedPrefs)
            return StartupRecoveryOutcome(
                activeSession = null,
                source = RecoverySource.NONE,
                isDecisionValid = true,
                archivedOldSessionsCount = 1,
                purgedStaleOrCorruptFallback = true
            )
        }

        // Valid fallback-only session: compute safe duration and promote into Room as ATIVO
        val safeDuration = computeFallbackDurationSeconds(
            candidate = candidate,
            currentBootCount = currentBootCount,
            nowMonotonic = nowMonotonic,
            nowWallMillis = nowWallMillis
        )
        val promotedEntity = WorkoutSessionEntity(
            id = candidate.sessionId,
            workoutId = candidate.workout.id,
            workoutName = candidate.workout.name,
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = candidate.startTimeMillis.coerceAtLeast(1L),
            endTime = null,
            completedAt = null,
            durationSeconds = safeDuration,
            exercisesJson = candidate.exercisesJson,
            version = 1,
            startTimeMonotonic = nowMonotonic,
            monotonicStartTimeNanos = nowNanos
        )
        repository.saveWorkoutSession(promotedEntity)
        saveSessionMetadata(
            sharedPrefs = sharedPrefs,
            sessionId = promotedEntity.id,
            startTimeMillis = promotedEntity.startTime,
            bootCount = currentBootCount
        )
        sharedPrefs.edit()
            .putLong(KEY_ACTIVE_WORKOUT_DURATION_SECONDS, safeDuration)
            .apply()

        return StartupRecoveryOutcome(
            activeSession = promotedEntity,
            source = RecoverySource.SHARED_PREFS_FALLBACK,
            isDecisionValid = true
        )
    }

    /**
     * Resolves a full session payload ready for the active Player screen.
     * Enforces Room precedence and strictly blocks resurrecting terminal sessions.
     */
    suspend fun resolveSessionForRestore(
        repository: FitnessRepository,
        sharedPrefs: SharedPreferences,
        allWorkouts: List<Workout>,
        targetSessionId: String?,
        currentBootCount: Int? = null,
        nowMonotonic: Long = SystemClock.elapsedRealtime(),
        nowNanos: Long = System.nanoTime(),
        nowWallMillis: Long = System.currentTimeMillis()
    ): RestoredSessionPayload? {
        val roomSession = if (!targetSessionId.isNullOrBlank()) {
            repository.getWorkoutSessionById(targetSessionId)
        } else {
            repository.getActiveWorkoutSession()
        }

        if (roomSession != null) {
            val status = WorkoutSessionStatus.fromValue(roomSession.status)
            if (status.isTerminal) {
                // Target session is terminal in Room: NEVER fall back to SharedPreferences
                val prefsSessionId = safeGetString(sharedPrefs, KEY_ACTIVE_SESSION_ID, null)
                if (prefsSessionId == roomSession.id || repository.getAllActiveWorkoutSessions().isEmpty()) {
                    clearFallbackPrefs(sharedPrefs)
                }
                return null
            }

            val exercises = parseAndSanitizeExercises(roomSession.exercisesJson)
            if (exercises == null) {
                // Corrupt exercisesJson in Room: archive cleanly and do not crash
                repository.updateWorkoutSessionStatusVersioned(
                    sessionId = roomSession.id,
                    status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
                    newVersion = roomSession.version + 1,
                    completedAt = nowWallMillis,
                    durationSeconds = maxOf(0L, roomSession.durationSeconds)
                )
                if (safeGetString(sharedPrefs, KEY_ACTIVE_SESSION_ID, null) == roomSession.id) {
                    clearFallbackPrefs(sharedPrefs)
                }
                return null
            }

            val baseWorkout = allWorkouts.find { it.id == roomSession.workoutId }
                ?: Workout(
                    id = roomSession.workoutId,
                    name = roomSession.workoutName,
                    exercisesJson = roomSession.exercisesJson
                )
            val hydratedWorkout = baseWorkout.copy(exercisesJson = roomSession.exercisesJson)

            val prefsMatchesSession = safeGetString(sharedPrefs, KEY_ACTIVE_SESSION_ID, null) == roomSession.id
            val matchingPrefsBootCount = if (prefsMatchesSession) readSavedBootCount(sharedPrefs) else null
            val matchingPrefsDuration = if (prefsMatchesSession) {
                safeGetLong(sharedPrefs, KEY_ACTIVE_WORKOUT_DURATION_SECONDS, 0L).coerceAtLeast(0L)
            } else {
                0L
            }
            val matchingPrefsIndex = if (prefsMatchesSession && sharedPrefs.contains(KEY_ACTIVE_CURRENT_EXERCISE_INDEX)) {
                safeGetInt(sharedPrefs, KEY_ACTIVE_CURRENT_EXERCISE_INDEX, -1).takeIf { it in exercises.indices }
            } else {
                null
            }
            val defaultRest = if (prefsMatchesSession) {
                safeGetInt(sharedPrefs, KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS, 60).coerceIn(5, 600)
            } else {
                exercises.firstOrNull()?.restTimeSeconds?.coerceIn(5, 600) ?: 60
            }

            val safeDuration = WorkoutTimeUtils.calculateSafeDurationSeconds(
                startTimeMonotonic = roomSession.startTimeMonotonic,
                fallbackStartMillis = roomSession.startTime,
                monotonicStartTimeNanos = roomSession.monotonicStartTimeNanos,
                persistedDurationSeconds = maxOf(roomSession.durationSeconds, matchingPrefsDuration),
                savedBootCount = matchingPrefsBootCount,
                currentBootCount = currentBootCount,
                nowMonotonic = nowMonotonic,
                nowNanos = nowNanos,
                nowWallMillis = nowWallMillis
            )

            return RestoredSessionPayload(
                sessionId = roomSession.id,
                workout = hydratedWorkout,
                exercises = exercises,
                currentExerciseIndex = resolvePreferredExerciseIndex(exercises, matchingPrefsIndex),
                elapsedDurationSeconds = safeDuration,
                startTimeMillis = roomSession.startTime,
                defaultRestSeconds = defaultRest,
                source = RecoverySource.ROOM
            )
        }

        // Room had no record for targetSessionId -> promote & validate fallback through startup resolution
        val fallbackOutcome = resolveStartupRecovery(
            repository = repository,
            sharedPrefs = sharedPrefs,
            currentBootCount = currentBootCount,
            nowMonotonic = nowMonotonic,
            nowNanos = nowNanos,
            nowWallMillis = nowWallMillis
        )
        if (!fallbackOutcome.isDecisionValid) return null
        val promoted = fallbackOutcome.activeSession ?: return null
        if (!targetSessionId.isNullOrBlank() && promoted.id != targetSessionId) return null

        val exercises = parseAndSanitizeExercises(promoted.exercisesJson) ?: return null
        val baseWorkout = allWorkouts.find { it.id == promoted.workoutId }
            ?: Workout(id = promoted.workoutId, name = promoted.workoutName, exercisesJson = promoted.exercisesJson)
        val prefsIndex = if (sharedPrefs.contains(KEY_ACTIVE_CURRENT_EXERCISE_INDEX)) {
            safeGetInt(sharedPrefs, KEY_ACTIVE_CURRENT_EXERCISE_INDEX, -1).takeIf { it in exercises.indices }
        } else {
            null
        }
        val defaultRest = safeGetInt(sharedPrefs, KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS, 60).coerceIn(5, 600)

        return RestoredSessionPayload(
            sessionId = promoted.id,
            workout = baseWorkout.copy(exercisesJson = promoted.exercisesJson),
            exercises = exercises,
            currentExerciseIndex = resolvePreferredExerciseIndex(exercises, prefsIndex),
            elapsedDurationSeconds = maxOf(0L, promoted.durationSeconds),
            startTimeMillis = promoted.startTime,
            defaultRestSeconds = defaultRest,
            source = fallbackOutcome.source
        )
    }

    private fun parseFallbackCandidate(
        sharedPrefs: SharedPreferences,
        nowWallMillis: Long
    ): FallbackCandidate? {
        if (!safeGetBoolean(sharedPrefs, KEY_HAS_ACTIVE_SESSION, false)) return null

        val isPreSession = safeGetBoolean(sharedPrefs, KEY_ACTIVE_IS_PRE_SESSION_ACTIVE, false)
        if (isPreSession) return null

        val flowStateStr = safeGetString(sharedPrefs, KEY_ACTIVE_WORKOUT_FLOW_STATE, "Running")
        if (!flowStateStr.equals("Running", ignoreCase = true)) {
            return null
        }

        val savedTimestamp = safeGetLong(sharedPrefs, KEY_ACTIVE_SESSION_TIMESTAMP, 0L)
        val savedBootCount = readSavedBootCount(sharedPrefs)
        val defaultRest = safeGetInt(sharedPrefs, KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS, 60).coerceIn(5, 600)
        val storedSessionId = safeGetString(sharedPrefs, KEY_ACTIVE_SESSION_ID, null)?.takeIf { it.isNotBlank() }
        val storedStartTime = safeGetLong(sharedPrefs, KEY_ACTIVE_SESSION_START_TIME, 0L)

        val sessionJson = safeGetString(sharedPrefs, KEY_ACTIVE_WORKOUT_SESSION_JSON, null)
        if (!sessionJson.isNullOrBlank()) {
            val parsedSession: ActiveWorkoutSession? = try {
                JsonUtils.parseActiveWorkoutSession(sessionJson)
            } catch (_: Exception) {
                null
            }
            if (parsedSession != null) {
                if (!parsedSession.sessionState.equals("ACTIVE", ignoreCase = true)) {
                    return null
                }
                val workout = try {
                    JsonUtils.moshi.adapter(Workout::class.java).fromJson(parsedSession.workoutJson)
                } catch (_: Exception) {
                    null
                }
                val exercises = parseAndSanitizeExercises(parsedSession.exercisesJson)
                if (workout != null && workout.id.isNotBlank() && exercises != null) {
                    val sid = parsedSession.sessionId.takeIf { it.isNotBlank() }
                        ?: storedSessionId
                        ?: UUID.randomUUID().toString()
                    val startMillis = parsedSession.startTime.takeIf { it > 0L }
                        ?: storedStartTime.takeIf { it > 0L }
                        ?: savedTimestamp.takeIf { it > 0L }
                        ?: nowWallMillis
                    val resolvedName = workout.name.ifBlank { parsedSession.workoutName.ifBlank { "Treino Ativo" } }
                    val cleanExercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
                    return FallbackCandidate(
                        sessionId = sid,
                        workout = workout.copy(name = resolvedName, exercisesJson = cleanExercisesJson),
                        exercises = exercises,
                        exercisesJson = cleanExercisesJson,
                        currentExerciseIndex = resolvePreferredExerciseIndex(exercises, parsedSession.currentExerciseIndex),
                        startTimeMillis = startMillis,
                        savedTimestampMillis = savedTimestamp,
                        persistedDurationSeconds = maxOf(0L, parsedSession.elapsedTime),
                        savedBootCount = savedBootCount,
                        defaultRestSeconds = defaultRest
                    )
                }
            }
        }

        // Legacy fallback keys (active_workout_json + active_exercises_json)
        val workoutJson = safeGetString(sharedPrefs, KEY_ACTIVE_WORKOUT_JSON, null)
        val exercisesJson = safeGetString(sharedPrefs, KEY_ACTIVE_EXERCISES_JSON, null)
        if (workoutJson.isNullOrBlank() || exercisesJson.isNullOrBlank()) return null

        val workout = try {
            JsonUtils.moshi.adapter(Workout::class.java).fromJson(workoutJson)
        } catch (_: Exception) {
            null
        } ?: return null
        if (workout.id.isBlank()) return null

        val exercises = parseAndSanitizeExercises(exercisesJson) ?: return null
        val sid = storedSessionId ?: UUID.randomUUID().toString()
        val persistedDur = safeGetLong(sharedPrefs, KEY_ACTIVE_WORKOUT_DURATION_SECONDS, 0L).coerceAtLeast(0L)
        val startMillis = storedStartTime.takeIf { it > 0L }
            ?: savedTimestamp.takeIf { it > 0L }
            ?: (nowWallMillis - persistedDur * 1000L).coerceAtLeast(1L)
        val resolvedName = workout.name.ifBlank { "Treino Ativo" }
        val cleanExercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
        val prefIndex = safeGetInt(sharedPrefs, KEY_ACTIVE_CURRENT_EXERCISE_INDEX, 0)

        return FallbackCandidate(
            sessionId = sid,
            workout = workout.copy(name = resolvedName, exercisesJson = cleanExercisesJson),
            exercises = exercises,
            exercisesJson = cleanExercisesJson,
            currentExerciseIndex = resolvePreferredExerciseIndex(exercises, prefIndex),
            startTimeMillis = startMillis,
            savedTimestampMillis = savedTimestamp,
            persistedDurationSeconds = persistedDur,
            savedBootCount = savedBootCount,
            defaultRestSeconds = defaultRest
        )
    }

    private fun computeFallbackDurationSeconds(
        candidate: FallbackCandidate,
        currentBootCount: Int?,
        nowMonotonic: Long,
        nowWallMillis: Long
    ): Long {
        val bootCountChanged = candidate.savedBootCount != null &&
            currentBootCount != null &&
            candidate.savedBootCount != currentBootCount

        val referenceWallMillis = candidate.savedTimestampMillis.takeIf { it > 0L } ?: candidate.startTimeMillis
        val bootEpochAfterSave = referenceWallMillis > 0L &&
            nowWallMillis >= referenceWallMillis &&
            (nowWallMillis - nowMonotonic) > referenceWallMillis + MAX_BOOT_EPOCH_DRIFT_MS

        if (bootCountChanged || bootEpochAfterSave) {
            // After reboot, preserve the last persisted active duration instead of adding offline reboot hours
            return if (candidate.persistedDurationSeconds > 0L) {
                candidate.persistedDurationSeconds
            } else {
                maxOf(0L, (nowWallMillis - candidate.startTimeMillis) / 1000L)
            }
        }

        val deltaSinceSaveSec = if (candidate.savedTimestampMillis > 0L) {
            maxOf(0L, (nowWallMillis - candidate.savedTimestampMillis) / 1000L)
        } else {
            0L
        }
        return maxOf(0L, candidate.persistedDurationSeconds + deltaSinceSaveSec)
    }
}
