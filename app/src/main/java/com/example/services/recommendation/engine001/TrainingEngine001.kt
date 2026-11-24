package com.example.services.recommendation.engine001

import android.util.Log
import com.example.data.model.Exercise

object TrainingEngine001 {

    private const val TAG = "TrainingEngine001"

    /**
     * Enforces SPRINT — TRAINING ENGINE 001 rules on any exercise list.
     * Includes complete defensive validation for nulls, corrupt metadata, and boundary cases.
     */
    fun applyRules(rawExercises: List<Exercise>?, targetMuscleGroup: String?): List<Exercise> {
        // --- HOTFIX 2: INPUT VALIDATION ---
        val safeMuscleGroup = targetMuscleGroup.orEmpty().lowercase().trim()

        if (rawExercises.isNullOrEmpty()) {
            Log.w("ENGINE001_INVALID_EXERCISE", "Input exercise list is null or empty. Returning empty list.")
            return emptyList()
        }

        // Filter nulls, blank names, and sanitize exercises
        val validatedExercises = rawExercises.mapNotNull { ex ->
            if (ex == null) {
                Log.w("ENGINE001_INVALID_EXERCISE", "Found null exercise entry in input list")
                null
            } else if (ex.name.isNullOrBlank()) {
                Log.w("ENGINE001_INVALID_EXERCISE", "id=${ex.id.orEmpty()} name=${ex.name} equipment=${ex.equipment} movementPattern=${ex.movementPattern} - Invalid exercise name")
                null
            } else {
                sanitizeExercise(ex)
            }
        }

        if (validatedExercises.isEmpty()) {
            Log.w("ENGINE001_INVALID_EXERCISE", "All exercises were filtered out during validation. Returning empty list.")
            return emptyList()
        }

        if (validatedExercises.size <= 1) {
            return validatedExercises
        }

        // --- RULES EXECUTION ---
        val sorted = try {
            val isLegDay = safeMuscleGroup.contains("perna") || safeMuscleGroup.contains("leg") || 
                           safeMuscleGroup.contains("quadriceps") || safeMuscleGroup.contains("posterior") || 
                           safeMuscleGroup.contains("gluteo")

            if (isLegDay) {
                applyLegDayRule9(validatedExercises)
            } else {
                applyGeneralRules(validatedExercises, safeMuscleGroup)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sorting exercises in rules execution, fallback to validated list", e)
            validatedExercises
        }

        // Apply Equipment Alternation (Rule 3) and Gym Flow (Rule 5)
        val flowResult = try {
            applyEquipmentAlternationAndFlow(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying equipment flow, fallback to sorted list", e)
            sorted
        }

        // --- HOTFIX 2: FINAL OUTPUT VALIDATION ---
        val finalOutput = flowResult.filterNotNull()
            .filter { !it.name.isNullOrBlank() }
            .distinctBy { if (it.id.isNotBlank()) it.id else it.name }

        return if (finalOutput.isNotEmpty()) {
            finalOutput
        } else {
            Log.w(TAG, "Final output validation produced empty list. Returning validated input fallback.")
            validatedExercises
        }
    }

    /**
     * HOTFIX 1 & HOTFIX 6 — Sanitize and apply safe default values for all optional/new metadata fields.
     */
    private fun sanitizeExercise(ex: Exercise): Exercise {
        val safeId = ex.id.orEmpty()
        val safeName = ex.name.orEmpty().trim()
        val safeCategory = ex.category.orEmpty().ifBlank { "Geral" }
        val safeEquipment = ex.equipment.orEmpty().ifBlank { "Outros" }
        val safeMovementPattern = ex.movementPattern.orEmpty().ifBlank { "Outros" }
        val safePrimaryMuscle = ex.primaryMuscle.orEmpty().ifBlank { safeCategory }
        val safeSecondaryMuscles = ex.secondaryMuscles ?: emptyList()
        val safeExerciseType = ex.exerciseType.orEmpty().ifBlank { "Outros" }
        val safeGymTrafficScore = if (ex.gymTrafficScore in 1..5) ex.gymTrafficScore else 2
        val safeSetupTime = if (ex.setupTime >= 0) ex.setupTime else 60
        val safeTransitionCost = if (ex.transitionCost >= 0) ex.transitionCost else 15
        val safePriority = if (ex.priority >= 0) ex.priority else 1
        val safeEvidenceLevel = ex.evidenceLevel.orEmpty().ifBlank { "Alta" }

        // Telemetry audit for missing critical metadata (HOTFIX 5)
        if (ex.equipment.isNullOrBlank() || ex.movementPattern.isNullOrBlank()) {
            Log.w("ENGINE001_INVALID_EXERCISE", "id=$safeId name=$safeName equipment=${ex.equipment} movementPattern=${ex.movementPattern} source=Sanitizer")
        }

        return ex.copy(
            id = safeId,
            name = safeName,
            category = safeCategory,
            equipment = safeEquipment,
            movementPattern = safeMovementPattern,
            primaryMuscle = safePrimaryMuscle,
            secondaryMuscles = safeSecondaryMuscles,
            exerciseType = safeExerciseType,
            gymTrafficScore = safeGymTrafficScore,
            setupTime = safeSetupTime,
            transitionCost = safeTransitionCost,
            priority = safePriority,
            evidenceLevel = safeEvidenceLevel
        )
    }

    /**
     * REGRA 9 — Inteligência para Pernas
     */
    private fun applyLegDayRule9(exercises: List<Exercise>): List<Exercise> {
        if (exercises.isEmpty()) return emptyList()

        fun getLegTier(ex: Exercise): Int {
            val name = ex.name.orEmpty().lowercase()
            val cat = ex.category.orEmpty().lowercase()

            return when {
                // Tier 1: Agachamento principal
                name.contains("agachamento livre") || name.contains("agachamento frontal") || 
                (name.contains("agachamento") && !name.contains("búlgaro") && !name.contains("bulgaro") && !name.contains("hack")) -> 1

                // Tier 2: Leg Press / Hack / Agachamento Hack
                name.contains("leg press") || name.contains("hack") -> 2

                // Tier 3: Unilaterais (Afundo, Passada, Búlgaro)
                name.contains("afundo") || name.contains("passada") || name.contains("búlgaro") || name.contains("bulgaro") -> 3

                // Tier 4: Posterior Hinge (Stiff, Terra, Hip Thrust)
                name.contains("stiff") || name.contains("terra") || name.contains("hip thrust") || name.contains("elevação pélvica") -> 4

                // Tier 5: Isolamento Posterior (Mesa Flexora / Cadeira Flexora)
                name.contains("flexora") || cat.contains("posterior") -> 5

                // Tier 6: Isolamento Quadríceps (Cadeira Extensora)
                name.contains("extensora") -> 6

                // Tier 7: Panturrilha
                name.contains("panturrilha") || cat.contains("panturrilha") -> 7

                // Tier 8: Core / Abdômen
                cat.contains("abdom") || name.contains("prancha") || name.contains("core") -> 8

                else -> 4
            }
        }

        return exercises.sortedWith(
            compareBy<Exercise> { getLegTier(it) }
                .thenBy { it.gymTrafficScore } // Rule 4: Traffic priority
        )
    }

    /**
     * REGRA 1, 2, 6, 7, 8 — Regras gerais de sequenciamento fisiológico.
     */
    private fun applyGeneralRules(exercises: List<Exercise>, targetMuscleGroup: String): List<Exercise> {
        if (exercises.isEmpty()) return emptyList()
        val safeTarget = targetMuscleGroup.lowercase().trim()

        fun getGeneralTier(ex: Exercise): Int {
            val name = ex.name.orEmpty().lowercase()
            val cat = ex.category.orEmpty().lowercase()
            val prim = ex.primaryMuscle.orEmpty().lowercase()
            val exType = ex.exerciseType.orEmpty()
            val isCompound = ex.compound || exType.equals("Composto", ignoreCase = true)

            // REGRA 8 — Exercícios corretivos e Core no final
            if (name.contains("face pull") || name.contains("manguito") || name.contains("rotação externa") || name.contains("mobilidade")) {
                return 5
            }
            if (cat.contains("abdom") || name.contains("prancha") || name.contains("core")) {
                return 6
            }

            // Check if exercise matches primary target muscle group
            val isTargetMuscle = safeTarget.isNotBlank() && (
                cat.contains(safeTarget) || prim.contains(safeTarget) || name.contains(safeTarget) ||
                (safeTarget.contains("peito") && (cat.contains("peito") || prim.contains("peito"))) ||
                (safeTarget.contains("costas") && (cat.contains("costas") || prim.contains("costas"))) ||
                (safeTarget.contains("perna") && (cat.contains("perna") || cat.contains("quadr") || cat.contains("glút") || cat.contains("posterior")))
            )

            return when {
                isTargetMuscle && isCompound -> 1
                isTargetMuscle && !isCompound -> 2
                !isTargetMuscle && isCompound -> 3
                else -> 4
            }
        }

        return exercises.sortedWith(
            compareBy<Exercise> { getGeneralTier(it) }
                .thenBy { it.category } // Group by category within tier
                .thenBy { it.gymTrafficScore } // Rule 4: Prefer lower traffic equipment
        )
    }

    /**
     * REGRA 3 — Alternância inteligente de equipamentos & REGRA 5 — Fluxo físico
     * HOTFIX 4 — Full guard against IndexOutOfBoundsException and empty lists.
     * Prevents swapping across different muscle groups so chest exercises stay together.
     */
    private fun applyEquipmentAlternationAndFlow(exercises: List<Exercise>): List<Exercise> {
        if (exercises.isEmpty()) return emptyList()
        val result = exercises.toMutableList()
        if (result.size < 3) return result

        for (i in 0 until result.size - 2) {
            if (i + 2 >= result.size) break
            val eq1 = getEquipmentGroup(result[i].equipment)
            val eq2 = getEquipmentGroup(result[i + 1].equipment)
            val eq3 = getEquipmentGroup(result[i + 2].equipment)

            if (eq1 == eq2 && eq2 == eq3 && eq1 != "UNKNOWN" && eq1 != "Outros") {
                val targetCat = result[i + 1].category.lowercase().trim()
                val targetMuscle = result[i + 1].primaryMuscle.lowercase().trim()

                // Try swapping index i+1 with a downstream exercise of DIFFERENT equipment BUT SAME muscle group
                val swapIndex = (i + 3 until result.size).firstOrNull { idx ->
                    val otherEq = getEquipmentGroup(result[idx].equipment)
                    val otherCat = result[idx].category.lowercase().trim()
                    val otherMuscle = result[idx].primaryMuscle.lowercase().trim()
                    otherEq != eq1 && (otherCat == targetCat || (targetMuscle.isNotBlank() && otherMuscle == targetMuscle))
                }
                if (swapIndex != null && swapIndex in 0 until result.size && (i + 1) in 0 until result.size) {
                    val temp = result[i + 1]
                    result[i + 1] = result[swapIndex]
                    result[swapIndex] = temp
                }
            }
        }
        return result
    }

    /**
     * HOTFIX 3 — Equipment Seguro
     * Complete handling for null, empty, blank, or unknown equipment strings.
     */
    fun getEquipmentGroup(equip: String?): String {
        if (equip.isNullOrBlank()) return "UNKNOWN"
        val e = equip.lowercase().trim()
        return when {
            e.contains("barra") -> "Barra"
            e.contains("halter") -> "Halter"
            e.contains("polia") || e.contains("cabo") -> "Polia"
            e.contains("smith") -> "Smith"
            e.contains("máquina") || e.contains("maquina") -> "Máquina"
            e.contains("peso corporal") -> "Peso Corporal"
            e == "outros" -> "Outros"
            else -> "UNKNOWN"
        }
    }

    /**
     * Recalculates physiological loads for a session's exercises based on sequence order.
     * Exercises earlier in sequence have full load capacity.
     * Subsequent exercises suffer fatigue-based load reduction (-5% to -10%), snapped to the real
     * equipment step (WeightCalculator). Moving an exercise to the top restores its base load.
     * Already completed sets are never altered.
     */
    fun calculateLoadsForSession(
        exercises: List<com.example.data.model.WorkoutExercise>,
        context: android.content.Context? = null
    ): List<com.example.data.model.WorkoutExercise> {
        if (exercises.isEmpty()) return emptyList()

        var accumulatedFatigue = 0.0
        return exercises.map { exercise ->
            val isWarmupOrStretch = exercise.blockType.equals("Aquecimento", ignoreCase = true) ||
                exercise.blockType.equals("Alongamento", ignoreCase = true) ||
                exercise.blockType.equals("Cardio", ignoreCase = true) ||
                exercise.name.contains("Aquecimento", ignoreCase = true)

            if (isWarmupOrStretch) {
                exercise
            } else {
                // Multiplier decreases with accumulated fatigue from preceding main exercises (-5% to -10% per block, min 0.75x)
                val positionMultiplier = (1.0f - (accumulatedFatigue * 0.04f).toFloat()).coerceIn(0.75f, 1.0f)
                val savedCat = com.example.utils.WeightCalculator.getSavedEquipmentCategory(
                    context,
                    exercise.name,
                    exercise.equipment
                )
                val isBodyweight = com.example.utils.WeightCalculator.isBodyweightExercise(
                    context,
                    exercise.name,
                    exercise.equipment
                )

                val updatedSets = exercise.sets.map { set ->
                    if (set.completed) {
                        // Preserve already completed sets intact
                        set
                    } else {
                        val base = set.baseWeight ?: set.weight
                        if (base > 0f && !isBodyweight) {
                            val rawNewWeight = base * positionMultiplier
                            val step = com.example.utils.WeightCalculator.getSavedWeightStep(
                                context = context,
                                exerciseName = exercise.name,
                                fallbackEquipment = exercise.equipment,
                                currentWeight = base
                            ).coerceAtLeast(0.5f)

                            val roundedWeight = when {
                                positionMultiplier >= 0.99f -> base
                                savedCat.contains("Halter", ignoreCase = true) &&
                                    !com.example.utils.WeightCalculator.hasExplicitCustomStep(context, exercise.name) -> {
                                    com.example.utils.WeightCalculator.roundToAvailableWeight(rawNewWeight, "Halteres")
                                }
                                else -> {
                                    val stepsCount = kotlin.math.round(rawNewWeight / step)
                                    (kotlin.math.round(stepsCount * step * 100f) / 100f).coerceAtLeast(step)
                                }
                            }
                            set.copy(weight = roundedWeight, baseWeight = base)
                        } else {
                            set.copy(baseWeight = base)
                        }
                    }
                }

                val isCompound = exercise.exerciseType.equals("Composto", ignoreCase = true) ||
                    exercise.movementPattern.equals("Composto", ignoreCase = true) ||
                    exercise.name.lowercase().contains("agachamento") ||
                    exercise.name.lowercase().contains("supino") ||
                    exercise.name.lowercase().contains("terra") ||
                    exercise.name.lowercase().contains("remada") ||
                    exercise.name.lowercase().contains("desenvolvimento") ||
                    exercise.name.lowercase().contains("leg press")
                val exerciseFatigue = if (isCompound) 1.6 else 1.0
                accumulatedFatigue += exerciseFatigue

                exercise.copy(sets = updatedSets)
            }
        }
    }

    /**
     * Calcula a diferença exata de dias do calendário entre duas datas (ex: 23/10 20:00 vs 24/10 10:00 = 1 dia / Ontem).
     * Evita o bug de divisão inteira de horas (< 24h virando 0 / "hoje").
     */
    fun calculateCalendarDaysBetween(pastMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        val nowCal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val pastCal = java.util.Calendar.getInstance().apply {
            timeInMillis = pastMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val diffMillis = nowCal.timeInMillis - pastCal.timeInMillis
        return kotlin.math.round(diffMillis / (1000.0 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    /**
     * Extrai um nome limpo e legível do treino (ex: "Full Body C", "Full Body A", etc.).
     */
    fun extractCleanWorkoutTitle(rawName: String, rawId: String = ""): String {
        val combined = "$rawName $rawId"
        return when {
            combined.contains("Full Body A", ignoreCase = true) || combined.contains("protocol_fb_a", ignoreCase = true) -> "Full Body A"
            combined.contains("Full Body B", ignoreCase = true) || combined.contains("protocol_fb_b", ignoreCase = true) -> "Full Body B"
            combined.contains("Full Body C", ignoreCase = true) || combined.contains("protocol_fb_c", ignoreCase = true) -> "Full Body C"
            combined.contains("Full Body D", ignoreCase = true) || combined.contains("protocol_fb_d", ignoreCase = true) -> "Full Body D"
            else -> rawName
                .replace(Regex("^(?i)(treino de hoje|hoje)\\s*[:•\\-]\\s*"), "")
                .split(" - ", " – ")
                .firstOrNull()
                ?.trim()
                ?.ifBlank { rawName.trim() }
                ?: rawName.trim()
        }
    }

    data class RecoveryWarningEvaluation(
        val muscleOrWorkoutName: String,
        val lastTrainedDateStr: String,
        val daysAgo: Int,
        val isFullBodyWarning: Boolean,
        val lastWorkoutName: String,
        val targetWorkoutName: String,
        val formattedMessage: String
    )

    /**
     * Avalia se o início de uma sessão de musculação ocorre dentro da janela crítica de descanso (< 36h–48h pós-Full Body
     * ou mesmo grupamento muscular treinado hoje/ontem).
     */
    fun evaluateSessionRecoveryWarning(
        targetWorkout: com.example.data.model.Workout,
        history: List<com.example.data.model.WorkoutHistoryEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): RecoveryWarningEvaluation? {
        val wNameLower = targetWorkout.name.lowercase()
        val wIdLower = targetWorkout.id.lowercase()
        if (wIdLower.contains("running") || wNameLower.contains("corrida") ||
            wIdLower.contains("mobility") || wNameLower.contains("mobilidade") ||
            wNameLower.contains("descanso")
        ) {
            return null
        }

        if (history.isEmpty()) return null

        val lastEntry = history.maxByOrNull { it.date } ?: return null
        val calendarDaysAgo = calculateCalendarDaysBetween(lastEntry.date, nowMillis)
        val hoursElapsed = (nowMillis - lastEntry.date) / (1000.0 * 3600.0)

        val dateShort = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date(lastEntry.date))

        val relativeWhenText = when (calendarDaysAgo) {
            0 -> "hoje ($dateShort)"
            1 -> "ontem ($dateShort)"
            else -> "há $calendarDaysAgo dias ($dateShort)"
        }

        val lastCleanName = extractCleanWorkoutTitle(lastEntry.workoutName, lastEntry.workoutId)
        val targetCleanName = extractCleanWorkoutTitle(targetWorkout.name, targetWorkout.id)

        val isLastFullBody = lastEntry.workoutName.contains("Full Body", ignoreCase = true) ||
            lastEntry.workoutId.contains("protocol_fb", ignoreCase = true)
        val isTargetFullBody = targetWorkout.name.contains("Full Body", ignoreCase = true) ||
            targetWorkout.id.contains("protocol_fb", ignoreCase = true)

        // 1. Caso Full Body: janela de descanso de 48h (bloqueia/alerta apenas se foi realizado Hoje [0] ou Ontem [1] com < 48h)
        if (isLastFullBody || isTargetFullBody) {
            if (calendarDaysAgo <= 1 && hoursElapsed < 48.0) {
                val msg = "Você realizou o treino $lastCleanName $relativeWhenText. " +
                    "Para recuperação muscular e do Sistema Nervoso Central pós-Full Body (janela de 48h), " +
                    "o ideal hoje é realizar sua sessão de Corrida ou descansar."
                return RecoveryWarningEvaluation(
                    muscleOrWorkoutName = lastCleanName,
                    lastTrainedDateStr = dateShort,
                    daysAgo = calendarDaysAgo,
                    isFullBodyWarning = true,
                    lastWorkoutName = lastCleanName,
                    targetWorkoutName = targetCleanName,
                    formattedMessage = msg
                )
            }
            return null
        }

        // 2. Caso divisão muscular (Split): verifica se o mesmo grupamento alvo foi treinado hoje (0) ou ontem (1) (< 36h)
        if (calendarDaysAgo <= 1 && hoursElapsed < 36.0) {
            val targetExercises = try {
                com.example.utils.JsonUtils.parseWorkoutExercises(targetWorkout.exercisesJson)
            } catch (_: Exception) {
                emptyList()
            }
            val targetCategories = targetExercises.map { it.category.trim() }
                .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                .distinct()

            val lastExercises = try {
                com.example.utils.JsonUtils.parseWorkoutExercises(lastEntry.exercisesJson)
            } catch (_: Exception) {
                emptyList()
            }
            val overlappingCategory = targetCategories.firstOrNull { cat ->
                lastExercises.any { ex ->
                    ex.category.equals(cat, ignoreCase = true) && ex.sets.any { s -> s.completed }
                }
            }

            if (overlappingCategory != null) {
                val msg = "Você treinou $overlappingCategory no treino $lastCleanName $relativeWhenText. " +
                    "Recomendamos aguardar a janela de recuperação de 48h ou alternar para Corrida/Descanso hoje."
                return RecoveryWarningEvaluation(
                    muscleOrWorkoutName = overlappingCategory,
                    lastTrainedDateStr = dateShort,
                    daysAgo = calendarDaysAgo,
                    isFullBodyWarning = false,
                    lastWorkoutName = lastCleanName,
                    targetWorkoutName = targetCleanName,
                    formattedMessage = msg
                )
            }
        }

        return null
    }
}

