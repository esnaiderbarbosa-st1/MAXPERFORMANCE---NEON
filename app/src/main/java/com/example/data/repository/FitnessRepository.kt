package com.example.data.repository

import com.example.constants.TemplatesAndExercises
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.utils.JsonUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FitnessRepository(private val db: AppDatabase) {

    private val moshi = JsonUtils.moshi

    // --- REPO GETTERS FLOWS ---

    // 1. Raw nullable user profile from Room (used by ProfileGate to detect empty DB vs existing profile)
    val rawUserProfileFlow: Flow<UserProfileData?> = db.userDao().getUserProfile("1")

    // 1b. User profile - emits the single local user, or empty neutral fallback when absent
    val userProfileFlow: Flow<UserProfileData> = rawUserProfileFlow.map { profile ->
        com.example.utils.WeightCalculator.activeUserProfile = profile?.takeIf { it.nome.isNotBlank() }
        profile ?: UserProfileData()
    }

    // 2. Custom + Premade Exercises
    val customExercisesFlow: Flow<List<CustomExerciseEntity>> = db.customExerciseDao().getAllCustomExercises()

    // 3. All Workouts (Templates + User Created Custom Workouts)
    val workoutsFlow: Flow<List<Workout>> = db.workoutDao().getAllWorkouts().map { list ->
        // If DB has custom workouts, combine them.
        // Otherwise, make sure templates are included!
        val templates = TemplatesAndExercises.PRE_MADE_TEMPLATES
        (list + templates).distinctBy { it.id }
    }

    // 4. Workout History Entries
    val historyFlow: Flow<List<WorkoutHistoryEntry>> = db.historyDao().getAllHistory()

    // 5. Cardio Sessions
    val cardioSessionsFlow: Flow<List<CardioSession>> = db.cardioDao().getAllCardioSessions()

    // 5.1 Running Sessions Flow (com desduplicação automática de sessões duplicadas no mesmo dia)
    val runningSessionsFlow: Flow<List<RunningSession>> = db.runningSessionDao().getAllRunningSessions().map { list ->
        list.deduplicatedRunningSessions()
    }

    suspend fun saveRunningSession(session: RunningSession) {
        db.runningSessionDao().insertRunningSession(session)
    }

    suspend fun deleteRunningSession(id: String) {
        db.runningSessionDao().deleteRunningSessionById(id)
    }

    suspend fun updateRunnerProfile(heightCm: Int, weightKg: Float, runningGoal: String, baselinePace: Float? = null) {
        val current = userProfileFlow.first()
        val updated = current.copy(
            heightCm = heightCm,
            weightKg = weightKg,
            runningGoal = runningGoal,
            altura = heightCm,
            peso = weightKg,
            baselinePaceMinPerKm = baselinePace ?: current.baselinePaceMinPerKm
        )
        db.userDao().insertProfile(updated)
    }

    suspend fun updateRunnerBaselinePace(baselinePaceMinPerKm: Float) {
        val current = userProfileFlow.first()
        val updated = current.copy(baselinePaceMinPerKm = baselinePaceMinPerKm)
        db.userDao().insertProfile(updated)
    }

    // 6. Pending Sync Actions Flow
    val pendingSyncActionsFlow: Flow<List<PendingSyncAction>> = db.syncDao().getPendingActionsFlow()

    // 9. Beta Evaluations Flow
    val betaEvaluationsFlow: Flow<List<BetaEvaluationEntity>> = db.betaLabDao().getAllEvaluations()

    suspend fun saveBetaEvaluation(evaluation: BetaEvaluationEntity) {
        db.betaLabDao().insertEvaluation(evaluation)
    }

    suspend fun deleteBetaEvaluation(id: String) {
        db.betaLabDao().deleteEvaluationById(id)
    }

    // 10. Active Workout Sessions
    private val sessionWriteMutex = Mutex()

    val activeWorkoutSessionFlow: Flow<WorkoutSessionEntity?> = db.workoutSessionDao().getActiveSessionFlow()
    val allActiveWorkoutSessionsFlow: Flow<List<WorkoutSessionEntity>> = db.workoutSessionDao().getAllActiveSessionsFlow()
    val archivedWorkoutSessionsFlow: Flow<List<WorkoutSessionEntity>> = db.workoutSessionDao().getArchivedSessionsFlow()

    suspend fun getActiveWorkoutSession(): WorkoutSessionEntity? = db.workoutSessionDao().getActiveSession()

    suspend fun getAllActiveWorkoutSessions(): List<WorkoutSessionEntity> = db.workoutSessionDao().getAllActiveSessions()

    suspend fun getWorkoutSessionById(id: String): WorkoutSessionEntity? = db.workoutSessionDao().getSessionById(id)

    suspend fun getWorkoutSessionByWorkoutAndStartTime(workoutId: String, startTime: Long): WorkoutSessionEntity? =
        db.workoutSessionDao().getSessionByWorkoutAndStartTime(workoutId, startTime)

    suspend fun getWorkoutSessionVersion(id: String): Int? = db.workoutSessionDao().getSessionVersion(id)

    suspend fun saveWorkoutSession(session: WorkoutSessionEntity) = sessionWriteMutex.withLock {
        WorkoutSessionValidator.validateForPersist(session)
        db.workoutSessionDao().insertOrUpdate(session)
    }

    suspend fun updateWorkoutSessionExercises(sessionId: String, exercisesJson: String): Boolean = sessionWriteMutex.withLock {
        db.workoutSessionDao().updateExercisesJson(sessionId, exercisesJson) > 0
    }

    suspend fun updateWorkoutSessionExercisesOptimistic(
        sessionId: String,
        exercisesJson: String,
        expectedVersion: Int
    ): Boolean = sessionWriteMutex.withLock {
        db.workoutSessionDao().updateExercisesJsonOptimistic(sessionId, exercisesJson, expectedVersion) > 0
    }

    suspend fun updateWorkoutSessionExercisesAndDurationOptimistic(
        sessionId: String,
        exercisesJson: String,
        durationSeconds: Long,
        expectedVersion: Int
    ): Boolean = sessionWriteMutex.withLock {
        db.workoutSessionDao().updateExercisesAndDurationOptimistic(
            sessionId = sessionId,
            exercisesJson = exercisesJson,
            durationSeconds = maxOf(0L, durationSeconds),
            expectedVersion = expectedVersion
        ) > 0
    }

    suspend fun updateWorkoutSessionStatus(
        sessionId: String,
        status: String,
        endTime: Long?,
        durationSeconds: Long
    ): Boolean = sessionWriteMutex.withLock {
        val current = db.workoutSessionDao().getSessionById(sessionId) ?: return false
        val fromStatus = WorkoutSessionStatus.fromValue(current.status)
        val toStatus = WorkoutSessionStatus.fromValue(status)
        if (!com.example.data.model.WorkoutSessionStateMachine.isValidTransition(fromStatus, toStatus)) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Transição de status rejeitada para $sessionId: $fromStatus -> $toStatus")
            return false
        }
        val validDuration = maxOf(0L, durationSeconds)
        val affected = db.workoutSessionDao().updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = toStatus.rawValue,
            expectedVersion = current.version + 1,
            completedAt = endTime,
            durationSeconds = validDuration
        )
        affected > 0
    }

    suspend fun updateWorkoutSessionStatusVersioned(
        sessionId: String,
        status: String,
        newVersion: Int,
        completedAt: Long?,
        durationSeconds: Long
    ): Boolean = sessionWriteMutex.withLock {
        val current = db.workoutSessionDao().getSessionById(sessionId) ?: return false
        val expectedCurrentVersion = newVersion - 1
        if (expectedCurrentVersion < 1 || current.version != expectedCurrentVersion) {
            android.util.Log.w(
                "MAX_PERFORMANCE_DEBUG",
                "Conflito otimista de versão em updateWorkoutSessionStatusVersioned para $sessionId: atual=${current.version}, esperada=$expectedCurrentVersion"
            )
            return false
        }
        val fromStatus = WorkoutSessionStatus.fromValue(current.status)
        val toStatus = WorkoutSessionStatus.fromValue(status)
        if (!com.example.data.model.WorkoutSessionStateMachine.isValidTransition(fromStatus, toStatus)) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Transição de status versionada rejeitada para $sessionId: $fromStatus -> $toStatus")
            return false
        }
        val validDuration = maxOf(0L, durationSeconds)
        val affected = db.workoutSessionDao().updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = toStatus.rawValue,
            expectedVersion = newVersion,
            completedAt = completedAt,
            durationSeconds = validDuration
        )
        affected > 0
    }

    suspend fun updateWorkoutSessionStatusExercisesAndDurationVersioned(
        sessionId: String,
        status: String,
        newVersion: Int,
        completedAt: Long?,
        durationSeconds: Long,
        exercisesJson: String
    ): Boolean = sessionWriteMutex.withLock {
        val current = db.workoutSessionDao().getSessionById(sessionId) ?: return false
        val expectedCurrentVersion = newVersion - 1
        if (expectedCurrentVersion < 1 || current.version != expectedCurrentVersion) {
            android.util.Log.w(
                "MAX_PERFORMANCE_DEBUG",
                "Conflito otimista de versão em updateWorkoutSessionStatusExercisesAndDurationVersioned para $sessionId: atual=${current.version}, esperada=$expectedCurrentVersion"
            )
            return false
        }
        val fromStatus = WorkoutSessionStatus.fromValue(current.status)
        val toStatus = WorkoutSessionStatus.fromValue(status)
        if (!com.example.data.model.WorkoutSessionStateMachine.isValidTransition(fromStatus, toStatus)) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Transição de status versionada rejeitada para $sessionId: $fromStatus -> $toStatus")
            return false
        }
        val validDuration = maxOf(0L, durationSeconds)
        val affected = db.workoutSessionDao().updateStatusExercisesAndDurationVersioned(
            sessionId = sessionId,
            newStatus = toStatus.rawValue,
            expectedVersion = newVersion,
            completedAt = completedAt,
            durationSeconds = validDuration,
            exercisesJson = exercisesJson
        )
        affected > 0
    }

    suspend fun archiveOldActiveSessions(
        sessionsToArchive: List<WorkoutSessionEntity>,
        archiveTimestamp: Long
    ): Int = sessionWriteMutex.withLock {
        db.workoutSessionDao().archiveMultipleSessionsAtomically(sessionsToArchive, archiveTimestamp)
    }

    suspend fun deleteWorkoutSession(sessionId: String) = sessionWriteMutex.withLock {
        db.workoutSessionDao().deleteSession(sessionId)
    }

    suspend fun purgeExpiredSessions(thresholdDays: Long = 30): Int = sessionWriteMutex.withLock {
        val thresholdTime = System.currentTimeMillis() - (thresholdDays * 24L * 60L * 60L * 1000L)
        db.workoutSessionDao().deleteExpiredSessions(thresholdTime)
    }

    // --- SYNCHRONIZATION AND CONFLICT RESOLUTION ---

    // Simulates syncing pending offline actions to the cloud
    suspend fun syncPendingActions(): Boolean {
        val actions = db.syncDao().getPendingActionsList()
        if (actions.isEmpty()) return true

        // Simulate syncing delay
        kotlinx.coroutines.delay(1200)

        // Process each action
        for (action in actions) {
            // Under real circumstances, send to Retrofit/API
            // Here, we successfully processed them and clear from DB
            db.syncDao().deleteActionById(action.id)
        }
        return true
    }

    // --- WRITE OPERATIONS (OFFLINE-FIRST FALLBACK) ---

    suspend fun saveProfile(profile: UserProfileData) {
        com.example.utils.WeightCalculator.activeUserProfile = profile
        db.userDao().insertProfile(profile)
        
        // Push sync
        val payload = moshi.adapter(UserProfileData::class.java).toJson(profile)
        queueSyncAction("UPDATE_PROFILE", payload)
    }

    suspend fun createWorkout(workout: Workout) {
        db.workoutDao().insertWorkout(workout)
        
        // Push sync
        val payload = moshi.adapter(Workout::class.java).toJson(workout)
        queueSyncAction("CREATE_WORKOUT", payload)
    }

    suspend fun deleteWorkout(id: String) {
        db.workoutDao().deleteWorkoutById(id)
        
        // Push sync
        queueSyncAction("DELETE_WORKOUT", id)
    }

    suspend fun addHistoryEntry(entry: WorkoutHistoryEntry) {
        db.historyDao().insertHistoryEntry(entry)
        ExerciseHistoryRepository.clearCache()
        
        // Push sync
        val payload = moshi.adapter(WorkoutHistoryEntry::class.java).toJson(entry)
        queueSyncAction("ADD_HISTORY", payload)
    }

    suspend fun deleteHistoryEntry(id: String) {
        db.historyDao().deleteHistoryById(id)
        ExerciseHistoryRepository.clearCache()
        queueSyncAction("DELETE_HISTORY", id)
    }

    suspend fun clearHistory() {
        db.historyDao().clearAllHistory()
        ExerciseHistoryRepository.clearCache()
        queueSyncAction("CLEAR_HISTORY", "")
    }

    suspend fun addCustomExercise(entity: CustomExerciseEntity) {
        db.customExerciseDao().insertCustomExercise(entity)
        
        // Push sync
        val payload = moshi.adapter(CustomExerciseEntity::class.java).toJson(entity)
        queueSyncAction("ADD_CUSTOM_EXERCISE", payload)
    }

    suspend fun logCardio(session: CardioSession) {
        db.cardioDao().insertCardio(session)
        
        // Push sync
        val payload = moshi.adapter(CardioSession::class.java).toJson(session)
        queueSyncAction("LOG_CARDIO", payload)
    }

    suspend fun deleteCardio(id: String) {
        db.cardioDao().deleteCardioById(id)
    }

    fun getChatMessagesFlow(classroomId: String): Flow<List<ChatMessage>> {
        return db.chatDao().getMessagesForClassroom(classroomId)
    }

    suspend fun addFeedItem(text: String, imageUri: String? = null, authorName: String) {
        val item = FeedItem(
            id = "feed_${UUID.randomUUID()}",
            authorName = authorName,
            text = text,
            imageUri = imageUri,
            likesCount = 0,
            hasUserLiked = false,
            commentsJson = "[]",
            createdAt = System.currentTimeMillis()
        )
        db.feedDao().insertFeedItem(item)
        
        val payload = moshi.adapter(FeedItem::class.java).toJson(item)
        queueSyncAction("ADD_FEED_ITEM", payload)
    }

    // --- PRIVATE SYNC QUEUE HELPER ---

    private suspend fun queueSyncAction(type: String, payload: String) {
        val action = PendingSyncAction(
            id = "sync_${UUID.randomUUID()}",
            actionType = type,
            payloadJson = payload,
            timestamp = System.currentTimeMillis()
        )
        db.syncDao().insertAction(action)
    }

    // Initial Database Setup Helper (preserves existing profile; never injects hardcoded profile on clean install)
    suspend fun initDefaultTestUser() {
        val existing = db.userDao().getUserProfileSync("1")
        if (existing != null && existing.nome.isNotBlank()) {
            com.example.utils.WeightCalculator.activeUserProfile = existing
        } else {
            com.example.utils.WeightCalculator.activeUserProfile = null
        }

        // Garante que as novas máquinas e exercícios nativos estejam sincronizados
        runCatching {
            com.example.utils.DeveloperTools.seedExercises(db)
        }
    }
}
