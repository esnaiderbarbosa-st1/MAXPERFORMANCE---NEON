package com.example.services.recommendation

import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.utils.JsonUtils
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Cluster {
    EMPURRAO, TRACAO, LEGS, CORE
}

enum class ExperienceLevel {
    INICIANTE, INTERMEDIARIO, AVANCADO, ELITE;
    
    companion object {
        fun fromString(str: String): ExperienceLevel {
            return when (str.lowercase().trim()) {
                "iniciante", "beginner", "novato" -> INICIANTE
                "intermediário", "intermediario", "intermediate" -> INTERMEDIARIO
                "avançado", "avancado", "advanced" -> AVANCADO
                "elite" -> ELITE
                else -> INTERMEDIARIO
            }
        }
    }
}

enum class GymContext {
    GYM_COMMERCIAL, CASA
}

val WorkoutExercise.cluster: Cluster?
    get() = when (category.lowercase()) {
        "peito", "ombros", "tríceps" -> Cluster.EMPURRAO
        "costas", "bíceps", "trapézio" -> Cluster.TRACAO
        "pernas", "glúteos" -> Cluster.LEGS
        "abdominais", "core" -> Cluster.CORE
        else -> null
    }

val com.example.data.model.Exercise.cluster: Cluster?
    get() = when (category.lowercase()) {
        "peito", "ombros", "tríceps" -> Cluster.EMPURRAO
        "costas", "bíceps", "trapézio" -> Cluster.TRACAO
        "pernas", "glúteos" -> Cluster.LEGS
        "abdominais", "core" -> Cluster.CORE
        else -> null
    }

val Workout.exercises: List<WorkoutExercise>
    get() = try {
        JsonUtils.parseWorkoutExercises(exercisesJson)
    } catch (e: Exception) {
        emptyList()
    }

fun getClusterForTargetGroup(group: String): Cluster? {
    return when (group.lowercase().trim()) {
        "peito", "ombros", "tríceps", "peitoral", "ombro", "triceps", "chest", "shoulders", "triceps" -> Cluster.EMPURRAO
        "costas", "bíceps", "trapézio", "dorsal", "biceps", "trapezio", "back", "trapezius" -> Cluster.TRACAO
        "pernas", "glúteos", "perna", "legs", "quadriceps", "posterior" -> Cluster.LEGS
        "abdominais", "core", "reto abdominal", "abdômen", "abdominal", "abs" -> Cluster.CORE
        else -> null
    }
}

fun validateClusterBoundary(workout: Workout, targetCluster: Cluster): Boolean {
    // Ignore Cardio and Alongamento blocks
    val filteredExercises = workout.exercises.filter { 
        it.blockType != "Cardio" && it.blockType != "Alongamento" && 
        it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento" 
    }
    return filteredExercises.all { ex ->
        val exCluster = ex.cluster
        if (exCluster == targetCluster) {
            true
        } else {
            // Allow valid synergists and stabilizers for each split cluster
            when (targetCluster) {
                Cluster.EMPURRAO -> exCluster == Cluster.CORE || ex.category.equals("Abdominais", ignoreCase = true) || ex.name.lowercase().contains("manguito")
                Cluster.TRACAO -> ex.category.equals("Ombros", ignoreCase = true) || ex.category.equals("Abdominais", ignoreCase = true) || ex.category.equals("Trapézio", ignoreCase = true) || ex.name.lowercase().contains("face pull")
                Cluster.LEGS -> exCluster == Cluster.CORE || ex.category.equals("Abdominais", ignoreCase = true)
                Cluster.CORE -> true
            }
        }
    }
}

// SPRINT 2: Expected exercise counts based on Reference Table
fun getExpectedExerciseCount(durationMinutes: Int, levelStr: String): IntRange {
    val normLevel = when (levelStr.lowercase().trim()) {
        "iniciante", "beginner", "novato" -> "iniciante"
        "intermediário", "intermediario", "intermediate" -> "intermediário"
        "avançado", "avancado", "advanced" -> "avançado"
        "elite" -> "elite"
        else -> "intermediário"
    }
    
    // Find nearest standard duration
    val standardDurations = listOf(30, 45, 60, 75, 90)
    val nearestDuration = standardDurations.minByOrNull { kotlin.math.abs(it - durationMinutes) } ?: 60

    return when (normLevel) {
        "iniciante" -> when (nearestDuration) {
            30 -> 3..4
            45 -> 4..5
            60 -> 5..6
            75 -> 6..7
            else -> 7..8
        }
        "intermediário" -> when (nearestDuration) {
            30 -> 4..5
            45 -> 5..6
            60 -> 6..8
            75 -> 8..9
            else -> 9..10
        }
        "avançado" -> when (nearestDuration) {
            30 -> 5..6
            45 -> 6..8
            60 -> 8..10
            75 -> 10..12
            else -> 12..14
        }
        "elite" -> when (nearestDuration) {
            30 -> 6..7
            45 -> 8..10
            60 -> 10..12
            75 -> 12..14
            else -> 14..16
        }
        else -> 5..6
    }
}

fun validateQuantityCoherence(
    workout: Workout, 
    duration: Int, 
    level: ExperienceLevel
): Boolean {
    return validateQuantityCoherence(workout, duration, level.name)
}

fun validateQuantityCoherence(
    workout: Workout, 
    duration: Int, 
    level: String
): Boolean {
    val expectedRange = getExpectedExerciseCount(duration, level)
    if (workout.exercises.isEmpty()) return false
    
    // Check against legacy expectedRange
    if (workout.exercises.size in expectedRange) return true
    
    // Check against new UserLevelConfigs range
    val levelConfig = com.example.services.recommendation.knowledge.UserLevelConfigs.getByLevel(level)
    val configRange = levelConfig.totalExercisesRange
    
    return workout.exercises.size in configRange || workout.exercises.size >= 5
}

// SPRINT 3: Monotony and Equipment Harmony
fun getSpecificMovementPattern(name: String, basePattern: String): String {
    val nameLower = name.lowercase()
    return when {
        nameLower.contains("inclinado") -> "Empurrão Inclinado"
        nameLower.contains("declinado") -> "Empurrão Declinado"
        nameLower.contains("desenvolvimento") || nameLower.contains("vertical") || nameLower.contains("militar") -> "Empurrão Vertical"
        nameLower.contains("unilateral") -> "Empurrão Unilateral"
        nameLower.contains("máquina") || nameLower.contains("maquina") -> "Empurrão na Máquina"
        nameLower.contains("supino") || nameLower.contains("flexão") || nameLower.contains("push up") -> "Empurrão Horizontal"
        nameLower.contains("crucifixo") || nameLower.contains("cross over") || nameLower.contains("voador") || nameLower.contains("peck deck") || nameLower.contains("fly") -> "Isolamento"
        nameLower.contains("alongamento") -> "Alongamento"
        else -> basePattern
    }
}

fun getSpecificImplementCategory(equipment: String): String {
    val eqLower = equipment.lowercase()
    return when {
        eqLower.contains("barra") -> "Barra"
        eqLower.contains("halter") -> "Halter"
        eqLower.contains("polia") || eqLower.contains("cabo") -> "Cabo"
        eqLower.contains("máquina") || eqLower.contains("maquina") -> "Máquina"
        eqLower.contains("peso corporal") || eqLower.contains("bodyweight") -> "Peso Corporal"
        else -> equipment
    }
}

fun calculateMonotony(exercises: List<WorkoutExercise>): Double {
    if (exercises.isEmpty()) return 0.0
    val filtered = exercises.filter { 
        it.blockType != "Cardio" && it.blockType != "Alongamento" && it.blockType != "Aquecimento" &&
        it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento" && it.category.lowercase() != "aquecimento"
    }
    if (filtered.isEmpty()) return 0.0
    
    val patternGroups = filtered.groupBy { getSpecificMovementPattern(it.name, it.movementPattern) }
    val implementGroups = filtered.groupBy { getSpecificImplementCategory(it.equipment) }
    
    val patternScore = patternGroups.values.sumOf { 
        (it.size / filtered.size.toDouble()).pow(2) 
    }
    val implementScore = implementGroups.values.sumOf { 
        (it.size / filtered.size.toDouble()).pow(2) 
    }
    
    return (patternScore + implementScore) / 2
}

fun calculateMonotonyForExercises(exercises: List<com.example.data.model.Exercise>): Double {
    if (exercises.isEmpty()) return 0.0
    val filtered = exercises.filter {
        it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento"
    }
    if (filtered.isEmpty()) return 0.0

    val patternGroups = filtered.groupBy { getSpecificMovementPattern(it.name, it.movementPattern) }
    val implementGroups = filtered.groupBy { getSpecificImplementCategory(it.equipment) }

    val patternScore = patternGroups.values.sumOf {
        (it.size / filtered.size.toDouble()).pow(2)
    }
    val implementScore = implementGroups.values.sumOf {
        (it.size / filtered.size.toDouble()).pow(2)
    }

    return (patternScore + implementScore) / 2
}

fun validateEquipmentHarmony(
    workout: Workout, 
    context: GymContext, 
    preferredExercises: List<String> = emptyList()
): Boolean {
    if (context != GymContext.GYM_COMMERCIAL) return true
    
    val bodyweightCount = workout.exercises.count { 
        it.blockType != "Cardio" && it.blockType != "Alongamento" &&
        (it.equipment.lowercase().contains("peso corporal") || it.equipment.lowercase().contains("bodyweight")) &&
        !preferredExercises.contains(it.exerciseId)
    }
    
    return bodyweightCount == 0  // ZERO bodyweight em academia comercial (except if preferred)
}

fun validateMonotonyAndEquipment(
    workout: Workout, 
    context: GymContext,
    preferredExercises: List<String> = emptyList()
): Boolean {
    val monotony = calculateMonotony(workout.exercises)
    return monotony < 0.50 && validateEquipmentHarmony(workout, context, preferredExercises)
}

fun getGymContext(gymProfile: com.example.data.model.GymProfile?): GymContext {
    if (gymProfile == null) return GymContext.GYM_COMMERCIAL
    val nameLower = gymProfile.name.lowercase()
    if (nameLower.contains("casa") || nameLower.contains("home") || nameLower.contains("apartamento")) {
        return GymContext.CASA
    }
    return GymContext.GYM_COMMERCIAL
}

fun getEquipmentHarmonyScore(equipment: String, context: GymContext): Int {
    val eqLower = equipment.lowercase()
    return when (context) {
        GymContext.GYM_COMMERCIAL -> when {
            eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") -> 5
            eqLower.contains("polia") || eqLower.contains("cabo") || eqLower.contains("cable") -> 4
            eqLower.contains("halter") || eqLower.contains("dumbbells") -> 3
            eqLower.contains("barra") || eqLower.contains("barbell") -> 2
            eqLower.contains("peso corporal") || eqLower.contains("bodyweight") -> 1
            else -> 0
        }
        GymContext.CASA -> when {
            eqLower.contains("halter") || eqLower.contains("dumbbells") -> 5
            eqLower.contains("band") || eqLower.contains("elástico") || eqLower.contains("elastico") -> 4
            eqLower.contains("peso corporal") || eqLower.contains("bodyweight") -> 3
            else -> 1
        }
    }
}

fun hasRepetitionsOfThreeOrMore(exercises: List<com.example.data.model.Exercise>): Boolean {
    val patternCounts = exercises.groupBy { getSpecificMovementPattern(it.name, it.movementPattern) }.mapValues { it.value.size }
    val implementCounts = exercises.groupBy { getSpecificImplementCategory(it.equipment) }.mapValues { it.value.size }
    return patternCounts.values.any { it >= 3 } || implementCounts.values.any { it >= 3 }
}

fun optimizeMonotony(
    selectedCandidates: List<com.example.data.model.Exercise>,
    targetCount: Int,
    gymContext: GymContext,
    preferredExercises: List<String> = emptyList()
): List<com.example.data.model.Exercise> {
    // If commercial gym, filter out bodyweight exercises entirely from candidates unless explicitly preferred
    val filteredCandidates = if (gymContext == GymContext.GYM_COMMERCIAL) {
        selectedCandidates.filter { 
            val isBodyweight = it.equipment.lowercase().contains("peso corporal") || 
                               it.equipment.lowercase().contains("bodyweight") ||
                               it.weightProfile == "BODYWEIGHT"
            val isPreferred = preferredExercises.contains(it.id)
            !isBodyweight || isPreferred
        }
    } else {
        selectedCandidates
    }

    // Sort all candidates based on Equipment Harmony priority first, with preferred exercises on top
    val sortedCandidates = filteredCandidates.sortedWith(
        compareByDescending<com.example.data.model.Exercise> { preferredExercises.contains(it.id) }
            .thenByDescending { getEquipmentHarmonyScore(it.equipment, gymContext) }
    )
    
    // Pick the top targetCount candidates as our starting list
    val currentSelection = sortedCandidates.take(targetCount).toMutableList()
    
    // If we have fewer than 2 exercises, monotony is always fine or can't be improved
    if (currentSelection.size < 2) return currentSelection

    // We can try to replace repeated items to bring monotony under 0.50 and clear 3+ repetitions
    var attempts = 0
    while ((calculateMonotonyForExercises(currentSelection) >= 0.50 || hasRepetitionsOfThreeOrMore(currentSelection)) && attempts < 20) {
        attempts++
        
        // Find the most repeated pattern or equipment
        val patternCounts = currentSelection.groupBy { getSpecificMovementPattern(it.name, it.movementPattern) }.mapValues { it.value.size }
        val equipmentCounts = currentSelection.groupBy { getSpecificImplementCategory(it.equipment) }.mapValues { it.value.size }
        
        val maxPattern = patternCounts.maxByOrNull { it.value }
        val maxEquipment = equipmentCounts.maxByOrNull { it.value }
        
        // Find an index to replace: choose an exercise that contributes to the most repeated pattern or equipment
        // CRITICAL: NEVER replace a preferred exercise
        val indexToReplace = currentSelection.indexOfFirst {
            val isPreferred = preferredExercises.contains(it.id)
            !isPreferred && (
                (maxPattern != null && getSpecificMovementPattern(it.name, it.movementPattern) == maxPattern.key && maxPattern.value > 1) ||
                (maxEquipment != null && getSpecificImplementCategory(it.equipment) == maxEquipment.key && maxEquipment.value > 1)
            )
        }
        
        if (indexToReplace == -1) break
        
        // Find a candidate not currently selected that would reduce monotony/repetitions
        val replacement = sortedCandidates.filter { candidate ->
            currentSelection.none { it.id == candidate.id || it.name == candidate.name }
        }.minByOrNull { candidate ->
            // Try temporary replacement and calculate score + repetitions
            val tempSelection = currentSelection.toMutableList()
            tempSelection[indexToReplace] = candidate
            
            val mon = calculateMonotonyForExercises(tempSelection)
            val penalty = if (hasRepetitionsOfThreeOrMore(tempSelection)) 1000.0 else 0.0
            mon + penalty
        }
        
        if (replacement != null) {
            currentSelection[indexToReplace] = replacement
        } else {
            // No more candidates to replace with
            break
        }
    }
    
    return currentSelection
}

// SPRINT 4: Neural Sequence and Final 5-Check Validation

val com.example.data.model.Exercise.neuralDemandIndex: Int
    get() {
        val nameLower = name.lowercase()
        val eqLower = equipment.lowercase()
        val typeLower = exerciseType.lowercase()
        val isCompound = compound || typeLower == "composto" || typeLower == "compound"
        
        return when {
            // 0. Primary Barbell Compound Lifts (Supino Reto Barra, Agachamento Livre, Terra, etc.)
            (nameLower.contains("supino reto") || nameLower.contains("agachamento livre") || nameLower.contains("levantamento terra") || nameLower.contains("desenvolvimento com barra") || nameLower.contains("remada curvada")) && (eqLower.contains("barra") || eqLower.contains("barbell")) -> 8

            // 1. Compostos com barra livre (supino, agachamento, terra)
            isCompound && (eqLower.contains("barra") || eqLower.contains("barbell")) && !nameLower.contains("smith") && !eqLower.contains("máquina") && !eqLower.contains("maquina") -> 6
            
            // 2. Compostos com halteres (inclinado halter, desenvolvimento)
            isCompound && (eqLower.contains("halter") || eqLower.contains("dumbbell")) -> 5
            
            // 3. Compostos em máquina (leg press, chest press máquina)
            isCompound && (eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") || nameLower.contains("máquina") || nameLower.contains("maquina") || nameLower.contains("smith")) -> 4
            
            // 4. Isoladores com cabo/máquina (crossover, peck deck, tríceps corda)
            !isCompound && (eqLower.contains("polia") || eqLower.contains("cabo") || eqLower.contains("cable") || eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") || nameLower.contains("máquina") || nameLower.contains("maquina")) -> 3
            
            // 5. Isoladores com halteres (crucifixo halter, elevação lateral)
            !isCompound && (eqLower.contains("halter") || eqLower.contains("dumbbell")) -> 2
            
            // 6. Finisher metabólico (peso corporal, etc. - se aplicável)
            eqLower.contains("peso corporal") || eqLower.contains("bodyweight") || nameLower.contains("finisher") || nameLower.contains("polichinelo") || nameLower.contains("burpee") -> 1
            
            else -> 0
        }
    }

val WorkoutExercise.neuralDemandIndex: Int
    get() {
        val nameLower = name.lowercase()
        val eqLower = equipment.lowercase()
        val typeLower = exerciseType.lowercase()
        val isCompound = typeLower == "composto" || typeLower == "compound"
        
        return when {
            // 0. Primary Barbell Compound Lifts
            (nameLower.contains("supino reto") || nameLower.contains("agachamento livre") || nameLower.contains("levantamento terra") || nameLower.contains("desenvolvimento com barra") || nameLower.contains("remada curvada")) && (eqLower.contains("barra") || eqLower.contains("barbell")) -> 8

            // 1. Compostos com barra livre (supino, agachamento, terra)
            isCompound && (eqLower.contains("barra") || eqLower.contains("barbell")) && !nameLower.contains("smith") && !eqLower.contains("máquina") && !eqLower.contains("maquina") -> 6
            
            // 2. Compostos com halteres (inclinado halter, desenvolvimento)
            isCompound && (eqLower.contains("halter") || eqLower.contains("dumbbell")) -> 5
            
            // 3. Compostos em máquina (leg press, chest press máquina)
            isCompound && (eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") || nameLower.contains("máquina") || nameLower.contains("maquina") || nameLower.contains("smith")) -> 4
            
            // 4. Isoladores com cabo/máquina (crossover, peck deck, tríceps corda)
            !isCompound && (eqLower.contains("polia") || eqLower.contains("cabo") || eqLower.contains("cable") || eqLower.contains("máquina") || eqLower.contains("maquina") || eqLower.contains("machine") || nameLower.contains("máquina") || nameLower.contains("maquina")) -> 3
            
            // 5. Isoladores com halteres (crucifixo halter, elevação lateral)
            !isCompound && (eqLower.contains("halter") || eqLower.contains("dumbbell")) -> 2
            
            // 6. Finisher metabólico (peso corporal, etc. - se aplicável)
            eqLower.contains("peso corporal") || eqLower.contains("bodyweight") || nameLower.contains("finisher") || nameLower.contains("polichinelo") || nameLower.contains("burpee") -> 1
            
            else -> 0
        }
    }

fun sequenceByNeuralDemand(exercises: List<com.example.data.model.Exercise>): List<com.example.data.model.Exercise> {
    return exercises.sortedByDescending { it.neuralDemandIndex }
}

fun verifyNeuralSequence(exercises: List<WorkoutExercise>): Boolean {
    val filtered = exercises.filter { 
        it.blockType != "Aquecimento" && it.blockType != "Cardio" && it.blockType != "Alongamento" &&
        it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento"
    }
    if (filtered.size < 2) return true
    
    for (i in 0 until filtered.size - 1) {
        if (filtered[i].neuralDemandIndex < filtered[i + 1].neuralDemandIndex) {
            return false
        }
    }
    return true
}

data class WorkoutRequest(
    val cluster: Cluster,
    val duration: Int,
    val level: String,
    val context: GymContext,
    val preferredExercises: List<String> = emptyList()
)

sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult
}

fun finalValidation(workout: Workout, request: WorkoutRequest): ValidationResult {
    val errors = mutableListOf<String>()
    
    // Check 1: Cluster
    if (!validateClusterBoundary(workout, request.cluster)) {
        errors.add("CLUSTER_VIOLATION")
    }
    
    // Check 2: Quantity
    if (!validateQuantityCoherence(workout, request.duration, request.level)) {
        errors.add("QUANTITY_MISMATCH")
    }
    
    // Check 3: Monotony
    val filteredMain = workout.exercises.filter { 
        it.blockType != "Cardio" && it.blockType != "Alongamento" && it.blockType != "Aquecimento" &&
        it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento" && it.category.lowercase() != "aquecimento"
    }
    val distinctEquipments = filteredMain.map { getSpecificImplementCategory(it.equipment) }.distinct()

    if (filteredMain.size > 2 && distinctEquipments.size > 1 && calculateMonotony(workout.exercises) >= 0.50) {
        errors.add("MONOTONY_TOO_HIGH")
    }
    
    // Check 4: Equipment
    if (!validateEquipmentHarmony(workout, request.context, request.preferredExercises)) {
        errors.add("EQUIPMENT_MISMATCH")
    }
    
    // Check 5: Sequence
    if (!verifyNeuralSequence(workout.exercises)) {
        errors.add("SEQUENCE_INVALID")
    }
    
    return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
}
