package com.example.data.repository

import android.util.Log
import com.example.data.model.GymExerciseRecord
import com.example.data.model.GymProfile
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutHistoryEntry
import com.example.services.domain.PerformanceEngine
import com.example.services.recommendation.knowledge.TrainingKnowledgeBase
import com.example.utils.JsonUtils
import com.example.utils.WeightCalculator

data class LoadProgressionSuggestion(
    val suggestedWeightKg: Float,
    val previousWeightKg: Float,
    val deltaKg: Float,
    val progressionApplied: Boolean,
    val strategyId: String,
    val reason: String
)

object ExerciseHistoryRepository {
    private const val TAG = "ExerciseHistoryRepo"

    @Volatile
    private var cachedHistoryRef: List<WorkoutHistoryEntry>? = null
    @Volatile
    private var cachedParsedHistory: List<Pair<WorkoutHistoryEntry, List<WorkoutExercise>>> = emptyList()
    private val sessionIndexCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<WorkoutHistoryEntry, WorkoutExercise>>>()
    private val aiSuggestedWeightOverrides = java.util.concurrent.ConcurrentHashMap<String, Float>()
    private val aiSuggestedRepsOverrides = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun setAiLoadOverride(exerciseName: String, weightKg: Float, targetReps: Int? = null) {
        val key = exerciseName.trim().lowercase()
        if (key.isNotEmpty() && weightKg > 0f) {
            aiSuggestedWeightOverrides[key] = weightKg
            if (targetReps != null && targetReps > 0) {
                aiSuggestedRepsOverrides[key] = targetReps
            }
        }
    }

    fun getAiWeightOverride(exerciseName: String): Float? {
        val key = exerciseName.trim().lowercase()
        return aiSuggestedWeightOverrides[key]
    }

    fun getAiRepsOverride(exerciseName: String): Int? {
        val key = exerciseName.trim().lowercase()
        return aiSuggestedRepsOverrides[key]
    }

    fun clearAiOverrides() {
        aiSuggestedWeightOverrides.clear()
        aiSuggestedRepsOverrides.clear()
    }

    fun clearCache() {
        cachedHistoryRef = null
        cachedParsedHistory = emptyList()
        sessionIndexCache.clear()
    }

    private fun ensureHistoryParsed(history: List<WorkoutHistoryEntry>): List<Pair<WorkoutHistoryEntry, List<WorkoutExercise>>> {
        val currentRef = cachedHistoryRef
        if (currentRef === history && cachedParsedHistory.isNotEmpty()) {
            return cachedParsedHistory
        }
        synchronized(this) {
            if (cachedHistoryRef === history && cachedParsedHistory.isNotEmpty()) {
                return cachedParsedHistory
            }
            val parsed = history
                .filter { entry -> entry.exercisesCompletedCount > 0 || entry.totalWeight > 0f }
                .sortedByDescending { it.date }
                .map { entry ->
                    val exercises = try {
                        JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    Pair(entry, exercises)
                }
            cachedParsedHistory = parsed
            cachedHistoryRef = history
            sessionIndexCache.clear()
            return parsed
        }
    }

    /**
     * Filters history to return up to [maxSessions] valid, finished sessions for a given exercise name or ID.
     * Abandoned or incomplete sessions are strictly excluded.
     */
    fun getValidSessionsForExercise(
        history: List<WorkoutHistoryEntry>,
        exerciseNameOrId: String,
        maxSessions: Int = 8
    ): List<Pair<WorkoutHistoryEntry, WorkoutExercise>> {
        if (history.isEmpty() || exerciseNameOrId.isBlank()) return emptyList()

        fun stripEquipmentVariations(str: String): String {
            return str.trim().lowercase()
                .replace("com barra", "").replace("com halteres", "")
                .replace("na máquina", "").replace("na maquina", "")
                .replace("no cross", "").replace("no smith", "").trim()
        }

        val searchNormalized = stripEquipmentVariations(exerciseNameOrId)
        val cacheKey = "$exerciseNameOrId|$searchNormalized"

        ensureHistoryParsed(history)
        sessionIndexCache[cacheKey]?.let { cached ->
            return cached.take(maxSessions)
        }

        fun hasEquipmentTypeConflict(a: String, b: String): Boolean {
            val al = a.lowercase()
            val bl = b.lowercase()
            val aIsDumbbell = al.contains("halter") || al.contains("dumbbell") || al.contains("serrote") || al.contains("arnold")
            val bIsDumbbell = bl.contains("halter") || bl.contains("dumbbell") || bl.contains("serrote") || bl.contains("arnold")
            val aIsBarbellOrMachine = al.contains("barra") || al.contains("máquina") || al.contains("maquina") ||
                al.contains("smith") || al.contains("anilha") || al.contains("total health") || al.contains("articulad") ||
                al.contains("polia") || al.contains("pulley") || al.contains("cabo")
            val bIsBarbellOrMachine = bl.contains("barra") || bl.contains("máquina") || bl.contains("maquina") ||
                bl.contains("smith") || bl.contains("anilha") || bl.contains("total health") || bl.contains("articulad") ||
                bl.contains("polia") || bl.contains("pulley") || bl.contains("cabo")
            if ((aIsDumbbell && !bIsDumbbell) || (bIsDumbbell && !aIsDumbbell)) return true
            if ((aIsDumbbell && bIsBarbellOrMachine) || (bIsDumbbell && aIsBarbellOrMachine)) return true

            val aIsPlateLoaded = al.contains("anilha") || al.contains("total health") || al.contains("articulad")
            val bIsPlateLoaded = bl.contains("anilha") || bl.contains("total health") || bl.contains("articulad")
            val aIsCable = al.contains("polia") || al.contains("pulley") || al.contains("cabo")
            val bIsCable = bl.contains("polia") || bl.contains("pulley") || bl.contains("cabo")
            return (aIsPlateLoaded && bIsCable) || (bIsPlateLoaded && aIsCable)
        }

        val parsedHistory = cachedParsedHistory
        val result = parsedHistory.mapNotNull { (entry, exercises) ->
            val matchEx = exercises.find { ex ->
                if (hasEquipmentTypeConflict(exerciseNameOrId, ex.name)) return@find false
                val exIdMatch = ex.exerciseId.isNotBlank() && (
                    ex.exerciseId.equals(exerciseNameOrId, ignoreCase = true) ||
                    exerciseNameOrId.contains(ex.exerciseId, ignoreCase = true)
                )
                val exNameExact = ex.name.trim().equals(exerciseNameOrId.trim(), ignoreCase = true)
                val exNormalized = stripEquipmentVariations(ex.name)
                
                val exNormalizedMatch = searchNormalized.length >= 3 && exNormalized.length >= 3 &&
                        (exNormalized == searchNormalized || exNormalized.contains(searchNormalized) || searchNormalized.contains(exNormalized))
                
                exIdMatch || exNameExact || exNormalizedMatch
            }

            if (matchEx != null && (matchEx.sets.any { s -> s.completed && s.weight > 0f } ||
                    (WeightCalculator.isBodyweightExercise(null, matchEx.name, matchEx.equipment) && matchEx.sets.any { it.completed }))) {
                Pair(entry, matchEx)
            } else {
                null
            }
        }

        sessionIndexCache[cacheKey] = result
        return result.take(maxSessions)
    }

    /**
     * Retrieves the suggested weight for an exercise following the strict 4-level Data Priority:
     * 1) Last successfully completed set (Última série concluída com sucesso)
     * 2) Best recent set across last 8 valid sessions (Melhor série recente)
     * 3) Average of recent sessions (Média das últimas sessões)
     * 4) Equipment default starting weight (Carga padrão apenas se não houver histórico)
     *
     * After identifying the base weight, applies conservative automatic progression aligned with
     * [TrainingKnowledgeBase.ProgressionStrategies] when all sets in the latest valid session hit [targetReps].
     */
    fun getSuggestedWeightForExercise(
        history: List<WorkoutHistoryEntry>,
        exerciseNameOrId: String,
        equipment: String = "Barra",
        gymProfile: GymProfile? = null,
        progressionType: String = "Linear",
        targetReps: Int = 10,
        isLowerBodyCompound: Boolean = false,
        userProfile: com.example.data.model.UserProfileData? = WeightCalculator.activeUserProfile
    ): Float {
        return getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = exerciseNameOrId,
            equipment = equipment,
            gymProfile = gymProfile,
            progressionType = progressionType,
            targetReps = targetReps,
            isLowerBodyCompound = isLowerBodyCompound,
            userProfile = userProfile
        ).suggestedWeightKg
    }

    /**
     * Resolves both the suggested load and structured progression metadata (previous weight,
     * conservative delta, strategy ID and reason) for UI display and unit testing.
     */
    fun getSuggestedLoadDetailsForExercise(
        history: List<WorkoutHistoryEntry>,
        exerciseNameOrId: String,
        equipment: String = "Barra",
        gymProfile: GymProfile? = null,
        progressionType: String = "Linear",
        targetReps: Int = 10,
        isLowerBodyCompound: Boolean = false,
        userProfile: com.example.data.model.UserProfileData? = WeightCalculator.activeUserProfile
    ): LoadProgressionSuggestion {
        val effectiveEquipment = if (equipment.isBlank() || equipment.equals("Outros", ignoreCase = true)) {
            WeightCalculator.inferEquipment(exerciseNameOrId, equipment)
        } else {
            equipment
        }
        val isDumbbellEx = effectiveEquipment.contains("halter", ignoreCase = true) ||
            effectiveEquipment.contains("dumbbell", ignoreCase = true) ||
            WeightCalculator.isPerDumbbellWeight(null, exerciseNameOrId, effectiveEquipment)

        if (WeightCalculator.isBodyweightExercise(null, exerciseNameOrId, effectiveEquipment)) {
            return LoadProgressionSuggestion(
                suggestedWeightKg = 0f,
                previousWeightKg = 0f,
                deltaKg = 0f,
                progressionApplied = false,
                strategyId = "bodyweight",
                reason = "Exercício de peso corporal"
            )
        }

        val validSessions = getValidSessionsForExercise(history, exerciseNameOrId, maxSessions = 8)

        // Também consulta o GymHistoryRepository (relatórios detalhados de campo) para garantir a última carga real consolidada
        val matchedGymSession: Pair<Long, GymExerciseRecord>? = runCatching {
            val qLower = exerciseNameOrId.trim().lowercase()
            val qIsDumbbell = qLower.contains("halter") || qLower.contains("dumbbell") || isDumbbellEx
            val qIsPlateLoaded = qLower.contains("anilha") || qLower.contains("total health") || qLower.contains("articulad")
            val qIsCable = qLower.contains("polia") || qLower.contains("pulley") || qLower.contains("cabo")
            val searchNorm = qLower
                .replace("com barra", "").replace("com halteres", "")
                .replace("na máquina", "").replace("na maquina", "")
                .replace("máquina", "").replace("maquina", "").trim()
            GymHistoryRepository.sessions.value
                .sortedByDescending { it.date }
                .firstNotNullOfOrNull { gs ->
                    val matchedEx = gs.exercises.firstOrNull { ex ->
                        val exLower = ex.name.trim().lowercase()
                        val exIsDumbbell = exLower.contains("halter") || exLower.contains("dumbbell") || ex.equipment.contains("halter", ignoreCase = true)
                        if (qIsDumbbell != exIsDumbbell) return@firstOrNull false
                        val exIsPlateLoaded = exLower.contains("anilha") || exLower.contains("total health") || exLower.contains("articulad")
                        val exIsCable = exLower.contains("polia") || exLower.contains("pulley") || exLower.contains("cabo")
                        if ((qIsPlateLoaded && exIsCable) || (exIsPlateLoaded && qIsCable)) return@firstOrNull false
                        val exNorm = exLower
                            .replace("com barra", "").replace("com halteres", "")
                            .replace("na máquina", "").replace("na maquina", "")
                            .replace("máquina", "").replace("maquina", "").trim()
                        ex.name.trim().equals(exerciseNameOrId.trim(), ignoreCase = true) ||
                            (searchNorm.length >= 4 && exNorm.length >= 4 &&
                                (exNorm == searchNorm || exNorm.contains(searchNorm) || searchNorm.contains(exNorm)))
                    }
                    if (matchedEx != null && matchedEx.sets.any { it.completed && it.weight > 0f }) {
                        Pair(gs.date, matchedEx)
                    } else {
                        null
                    }
                }
        }.getOrNull()

        val gymSessionLastWeight: Float = matchedGymSession?.second?.let { matchedEx ->
            matchedEx.sets.lastOrNull { it.completed && it.weight > 0f }?.weight
                ?: matchedEx.sets.filter { it.completed && it.weight > 0f }.maxOfOrNull { it.weight }
                ?: 0f
        } ?: 0f

        val defaultWeight = WeightCalculator.defaultStartingWeightForExercise(exerciseNameOrId, effectiveEquipment, userProfile)

        // Priority 1: Last completed set in the most recent valid session (or GymHistoryRepository)
        val mostRecentExercise = validSessions.firstOrNull()?.second
        val roomLastWeight = mostRecentExercise?.sets
            ?.lastOrNull { s -> s.completed && s.weight > 0f }?.weight
            ?: mostRecentExercise?.sets?.filter { s -> s.completed && s.weight > 0f }?.maxOfOrNull { s -> s.weight }
            ?: 0f

        val roomLastDate = validSessions.firstOrNull()?.first?.date ?: 0L
        val gymLastDate = matchedGymSession?.first ?: 0L
        val useGymAsMostRecent = gymSessionLastWeight > 0f && (roomLastWeight <= 0f || gymLastDate >= roomLastDate)
        val rawLastCompletedSetWeight = when {
            useGymAsMostRecent -> gymSessionLastWeight
            roomLastWeight > 0f -> roomLastWeight
            else -> 0f
        }
        val lastCompletedSetWeight = if (isDumbbellEx && rawLastCompletedSetWeight > 42.0f) {
            // Proteção contra registro antigo acidental de carga bilateral somada em exercício de halteres
            WeightCalculator.getConsolidatedAthleteLoadKg(exerciseNameOrId)
                ?: WeightCalculator.estimateSingleDumbbellFromBilateralLoad(rawLastCompletedSetWeight)
        } else {
            rawLastCompletedSetWeight
        }

        // Se houver override da IA, aplica trava anti-salto (máx +2 kg por haltere ou +1 incremento físico acima da última carga real consolidada)
        getAiWeightOverride(exerciseNameOrId)?.let { aiWeight ->
            if (aiWeight > 0f) {
                val safeAiWeight = if (isDumbbellEx && aiWeight > WeightCalculator.MAX_INITIAL_SINGLE_DUMBBELL_KG && lastCompletedSetWeight <= 0f) {
                    WeightCalculator.getConsolidatedAthleteLoadKg(exerciseNameOrId)
                        ?: WeightCalculator.estimateSingleDumbbellFromBilateralLoad(aiWeight)
                } else {
                    aiWeight
                }
                if (lastCompletedSetWeight > 0f) {
                    val maxStepKg = if (isDumbbellEx) 2.0f else 6.8f
                    val clampedAi = safeAiWeight.coerceIn(
                        (lastCompletedSetWeight - maxStepKg).coerceAtLeast(2.0f),
                        lastCompletedSetWeight + maxStepKg
                    )
                    val roundedAi = (kotlin.math.round(clampedAi * 10f) / 10f).coerceAtLeast(0f)
                    return LoadProgressionSuggestion(
                        suggestedWeightKg = roundedAi,
                        previousWeightKg = lastCompletedSetWeight,
                        deltaKg = (kotlin.math.round((roundedAi - lastCompletedSetWeight) * 10f) / 10f).coerceAtLeast(0f),
                        progressionApplied = false,
                        strategyId = "ai_override",
                        reason = "Ajuste de carga definido pela análise IA"
                    )
                }
                val roundedAi = WeightCalculator.roundToAvailableWeight(safeAiWeight, effectiveEquipment, gymProfile)
                return LoadProgressionSuggestion(
                    suggestedWeightKg = roundedAi,
                    previousWeightKg = 0f,
                    deltaKg = 0f,
                    progressionApplied = false,
                    strategyId = "ai_override",
                    reason = "Carga inicial sugerida pela análise IA"
                )
            }
        }

        if (validSessions.isEmpty() && lastCompletedSetWeight <= 0f) {
            Log.d(TAG, "No history found for '$exerciseNameOrId'. Using biometric starting weight: $defaultWeight kg ($effectiveEquipment)")
            val safeDefault = if (isDumbbellEx && defaultWeight > WeightCalculator.MAX_INITIAL_SINGLE_DUMBBELL_KG) {
                WeightCalculator.estimateSingleDumbbellFromBilateralLoad(defaultWeight)
            } else {
                defaultWeight
            }
            val roundedDefault = WeightCalculator.roundToAvailableWeight(safeDefault, effectiveEquipment, gymProfile)
            return LoadProgressionSuggestion(
                suggestedWeightKg = roundedDefault,
                previousWeightKg = 0f,
                deltaKg = 0f,
                progressionApplied = false,
                strategyId = "default",
                reason = "Carga inicial biométrica (sem histórico)"
            )
        }

        // Priority 2: Best recent set across the up to 8 valid sessions
        var bestRecentSetWeight = 0f
        var max1RM = 0.0
        for ((_, ex) in validSessions) {
            for (s in ex.sets) {
                if (s.completed && s.weight > 0f) {
                    val w = if (isDumbbellEx && s.weight > 42.0f) {
                        WeightCalculator.getConsolidatedAthleteLoadKg(exerciseNameOrId)
                            ?: WeightCalculator.estimateSingleDumbbellFromBilateralLoad(s.weight)
                    } else {
                        s.weight
                    }
                    val current1RM = PerformanceEngine.estimar1RM(w.toDouble(), s.reps)
                    if (current1RM > max1RM) {
                        max1RM = current1RM
                        bestRecentSetWeight = w
                    }
                }
            }
        }

        // Priority 3: Average of completed sets in recent valid sessions
        val completedWeightsList = validSessions.flatMap { (_, ex) ->
            ex.sets.filter { s -> s.completed && s.weight > 0f }.map { s ->
                if (isDumbbellEx && s.weight > 42.0f) {
                    WeightCalculator.getConsolidatedAthleteLoadKg(exerciseNameOrId)
                        ?: WeightCalculator.estimateSingleDumbbellFromBilateralLoad(s.weight)
                } else {
                    s.weight
                }
            }
        }
        val averageRecentWeight = if (completedWeightsList.isNotEmpty()) {
            completedWeightsList.average().toFloat()
        } else {
            0f
        }

        // Apply Data Priority: Priority 1 -> Priority 2 -> Priority 3 -> Priority 4
        val (baseWeight, isFromHistory, levelText) = when {
            lastCompletedSetWeight > 0f -> Triple(lastCompletedSetWeight, true, "P1_LastSet")
            bestRecentSetWeight > 0f -> Triple(bestRecentSetWeight, true, "P2_BestRecent")
            averageRecentWeight > 0f -> Triple(averageRecentWeight, true, "P3_Average")
            else -> Triple(defaultWeight, false, "P4_Default")
        }

        if (!isFromHistory) {
            val cappedBase = if (isDumbbellEx) baseWeight.coerceAtMost(WeightCalculator.MAX_INITIAL_SINGLE_DUMBBELL_KG) else baseWeight
            val roundedDefault = WeightCalculator.roundToAvailableWeight(cappedBase, effectiveEquipment, gymProfile)
            return LoadProgressionSuggestion(
                suggestedWeightKg = roundedDefault,
                previousWeightKg = 0f,
                deltaKg = 0f,
                progressionApplied = false,
                strategyId = "default",
                reason = "Carga inicial padrão"
            )
        }

        val normalizedBase = (kotlin.math.round(baseWeight * 10f) / 10f).coerceAtLeast(0f)

        // Regra explícita de Deload (-10% arredondado ao equipamento)
        if (TrainingKnowledgeBase.ProgressionStrategies.isDeloadMode(progressionType)) {
            val deloadWeight = WeightCalculator.roundToAvailableWeight(baseWeight * 0.90f, effectiveEquipment, gymProfile)
            Log.d(TAG, "Historical load resolved for '$exerciseNameOrId': Level=$levelText, BaseWeight=$normalizedBase kg -> Deload=$deloadWeight kg")
            return LoadProgressionSuggestion(
                suggestedWeightKg = deloadWeight,
                previousWeightKg = normalizedBase,
                deltaKg = 0f,
                progressionApplied = false,
                strategyId = "deload",
                reason = "Redução estruturada de deload (-10%)"
            )
        }

        // Modos não progressivos (Manutenção, Aquecimento, Cardio, Alongamento)
        if (TrainingKnowledgeBase.ProgressionStrategies.isMaintenanceOrNonProgressive(progressionType)) {
            Log.d(TAG, "Historical load resolved for '$exerciseNameOrId': Level=$levelText, BaseWeight=$normalizedBase kg -> Maintained (non-progressive mode)")
            return LoadProgressionSuggestion(
                suggestedWeightKg = normalizedBase,
                previousWeightKg = normalizedBase,
                deltaKg = 0f,
                progressionApplied = false,
                strategyId = "maintenance",
                reason = "Carga mantida pelo modo $progressionType"
            )
        }

        // Progressão automática conservadora alinhada à TrainingKnowledgeBase:
        // Exige que TODAS as séries da última sessão válida tenham sido concluídas atingindo targetReps.
        val resolvedStrategy = TrainingKnowledgeBase.ProgressionStrategies.resolveStrategy(progressionType)
        val safeTargetReps = targetReps.coerceAtLeast(1)
        val latestSessionMeetsCriteria = if (levelText == "P1_LastSet") {
            if (useGymAsMostRecent) {
                val gymSets = matchedGymSession?.second?.sets.orEmpty()
                gymSets.isNotEmpty() && gymSets.all { it.completed && it.weight > 0f && it.reps >= safeTargetReps }
            } else {
                val roomSets = mostRecentExercise?.sets.orEmpty()
                roomSets.isNotEmpty() && roomSets.all { it.completed && it.weight > 0f && it.reps >= safeTargetReps }
            }
        } else {
            false
        }

        if (resolvedStrategy != null && latestSessionMeetsCriteria && normalizedBase > 0f) {
            val detailedCategory = WeightCalculator.inferDetailedEquipmentCategory(exerciseNameOrId, effectiveEquipment)
            val ceilingEquipmentHint = if (isDumbbellEx) "Halteres" else "$effectiveEquipment $detailedCategory"
            val maxCeilingKg = TrainingKnowledgeBase.ProgressionStrategies.maxConservativeIncrementKg(ceilingEquipmentHint)

            val equipmentStep = WeightCalculator.getSavedWeightStep(
                context = null,
                exerciseName = exerciseNameOrId,
                fallbackEquipment = effectiveEquipment,
                currentWeight = normalizedBase
            ).coerceIn(0.5f, maxCeilingKg)

            val rawProgressed = normalizedBase + equipmentStep
            val roundedCandidate = WeightCalculator.roundToAvailableWeight(rawProgressed, effectiveEquipment, gymProfile)

            // Teto rígido anti-salto: garante que o arredondamento nunca exceda 1 degrau conservador nem o teto da categoria
            val safeProgressedWeight = when {
                roundedCandidate - normalizedBase > maxCeilingKg + 0.001f -> {
                    if (isDumbbellEx && normalizedBase >= 10f) {
                        val maxAllowedInt = kotlin.math.floor((normalizedBase + maxCeilingKg).toDouble()).toInt()
                        val evenCap = if (maxAllowedInt % 2 != 0) maxAllowedInt - 1 else maxAllowedInt
                        evenCap.toFloat().coerceAtLeast(normalizedBase + 1.0f)
                    } else {
                        (kotlin.math.round((normalizedBase + maxCeilingKg) * 10f) / 10f)
                    }
                }
                roundedCandidate <= normalizedBase -> {
                    (kotlin.math.round(rawProgressed * 10f) / 10f)
                }
                else -> {
                    (kotlin.math.round(roundedCandidate * 10f) / 10f)
                }
            }.coerceAtMost(kotlin.math.round((normalizedBase + maxCeilingKg) * 10f) / 10f)

            val finalDelta = (kotlin.math.round((safeProgressedWeight - normalizedBase) * 10f) / 10f).coerceAtLeast(0f)

            Log.d(
                TAG,
                "Conservative progression applied for '$exerciseNameOrId': Strategy=${resolvedStrategy.id}, " +
                    "Base=$normalizedBase kg -> Suggested=$safeProgressedWeight kg (+$finalDelta kg, cap=$maxCeilingKg kg)"
            )

            return LoadProgressionSuggestion(
                suggestedWeightKg = safeProgressedWeight,
                previousWeightKg = normalizedBase,
                deltaKg = finalDelta,
                progressionApplied = finalDelta > 0f,
                strategyId = resolvedStrategy.id,
                reason = "Todas as séries atingiram $safeTargetReps+ reps (${resolvedStrategy.name})"
            )
        }

        Log.d(TAG, "Historical load preserved for '$exerciseNameOrId': Level=$levelText, BaseWeight=$normalizedBase kg (progression criteria not met)")
        return LoadProgressionSuggestion(
            suggestedWeightKg = normalizedBase,
            previousWeightKg = normalizedBase,
            deltaKg = 0f,
            progressionApplied = false,
            strategyId = resolvedStrategy?.id ?: "maintained",
            reason = "Carga anterior mantida (critério de repetições não atingido em todas as séries)"
        )
    }
}


