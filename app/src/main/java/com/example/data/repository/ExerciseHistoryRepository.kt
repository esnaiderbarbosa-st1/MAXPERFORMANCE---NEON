package com.example.data.repository

import android.util.Log
import com.example.data.model.GymProfile
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutHistoryEntry
import com.example.services.domain.PerformanceEngine
import com.example.utils.JsonUtils
import com.example.utils.WeightCalculator

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
     * After identifying the base weight, applies configured progression and rounds to equipment/gym profile.
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
        val effectiveEquipment = if (equipment.isBlank() || equipment.equals("Outros", ignoreCase = true)) {
            WeightCalculator.inferEquipment(exerciseNameOrId, equipment)
        } else {
            equipment
        }
        val isDumbbellEx = effectiveEquipment.contains("halter", ignoreCase = true) ||
            effectiveEquipment.contains("dumbbell", ignoreCase = true) ||
            WeightCalculator.isPerDumbbellWeight(null, exerciseNameOrId, effectiveEquipment)

        if (WeightCalculator.isBodyweightExercise(null, exerciseNameOrId, effectiveEquipment)) {
            return 0f
        }

        val validSessions = getValidSessionsForExercise(history, exerciseNameOrId, maxSessions = 8)

        // Também consulta o GymHistoryRepository (relatórios detalhados de campo) para garantir a última carga real consolidada
        val gymSessionLastWeight: Float = runCatching {
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
                    matchedEx?.sets?.lastOrNull { it.completed && it.weight > 0f }?.weight
                        ?: matchedEx?.sets?.filter { it.completed && it.weight > 0f }?.maxOfOrNull { it.weight }
                } ?: 0f
        }.getOrDefault(0f)

        val defaultWeight = WeightCalculator.defaultStartingWeightForExercise(exerciseNameOrId, effectiveEquipment, userProfile)

        // Priority 1: Last completed set in the most recent valid session (or GymHistoryRepository)
        val mostRecentExercise = validSessions.firstOrNull()?.second
        val roomLastWeight = mostRecentExercise?.sets
            ?.lastOrNull { s -> s.completed && s.weight > 0f }?.weight
            ?: mostRecentExercise?.sets?.filter { s -> s.completed && s.weight > 0f }?.maxOfOrNull { s -> s.weight }
            ?: 0f

        val roomLastDate = validSessions.firstOrNull()?.first?.date ?: 0L
        val gymLastDate = GymHistoryRepository.sessions.value.firstOrNull()?.date ?: 0L
        val rawLastCompletedSetWeight = when {
            gymSessionLastWeight > 0f && (roomLastWeight <= 0f || gymLastDate >= roomLastDate) -> gymSessionLastWeight
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
                    return (kotlin.math.round(clampedAi * 10f) / 10f).coerceAtLeast(0f)
                }
                return WeightCalculator.roundToAvailableWeight(safeAiWeight, effectiveEquipment, gymProfile)
            }
        }

        if (validSessions.isEmpty() && lastCompletedSetWeight <= 0f) {
            Log.d(TAG, "No history found for '$exerciseNameOrId'. Using biometric starting weight: $defaultWeight kg ($effectiveEquipment)")
            val safeDefault = if (isDumbbellEx && defaultWeight > WeightCalculator.MAX_INITIAL_SINGLE_DUMBBELL_KG) {
                WeightCalculator.estimateSingleDumbbellFromBilateralLoad(defaultWeight)
            } else {
                defaultWeight
            }
            return WeightCalculator.roundToAvailableWeight(safeDefault, effectiveEquipment, gymProfile)
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

        // TRAVA ANTI-SALTO: Preserva exatamente a última carga consolidada do histórico (ex: 61 kg na Cadeira Extensora ou 22 kg no Supino Inclinado com Halteres).
        // Só reduz em caso explícito de Deload; a progressão fica a cargo do atleta ou de 1 incremento físico validado (máx +2 kg por halter).
        val finalWeight = if (isFromHistory) {
            when (progressionType.lowercase()) {
                "descarregamento", "deload" -> WeightCalculator.roundToAvailableWeight(baseWeight * 0.90f, effectiveEquipment, gymProfile)
                else -> (kotlin.math.round(baseWeight * 10f) / 10f).coerceAtLeast(0f)
            }
        } else {
            val cappedBase = if (isDumbbellEx) baseWeight.coerceAtMost(WeightCalculator.MAX_INITIAL_SINGLE_DUMBBELL_KG) else baseWeight
            WeightCalculator.roundToAvailableWeight(cappedBase, effectiveEquipment, gymProfile)
        }

        Log.d(TAG, "Historical load resolved for '$exerciseNameOrId': Level=$levelText, BaseWeight=$baseWeight kg -> FinalWeight=$finalWeight kg")
        return finalWeight
    }
}

