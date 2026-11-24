package com.example.services.recommendation.planners

import com.example.constants.TemplatesAndExercises
import com.example.data.model.Exercise

enum class FunctionalRoleType {
    HEAVY_PRIMARY_COMPOUND,   // Movimento pesado (Multiarticular)
    SECONDARY_DEVELOPMENT,    // Desenvolvimento / Variação tensional
    UNILATERAL_STABILITY,     // Remada unilateral / Exercício unilateral
    ISOLATION_TARGET,         // Isolamento direcionado ao vetor principal
    JOINT_STABILIZER,         // Estabilizadores (Manguito, Serrátil, Deltoide Posterior, Core)
    SYNERGIST_PUMP_FINISHER   // Sinergista / Finalização metabólica (Tríceps, Bíceps, Trapézio, Panturrilhas)
}

data class RoleDefinition(
    val roleType: FunctionalRoleType,
    val name: String,
    val description: String,
    val targetMuscles: List<String>,
    val keywords: List<String>,
    val preferredEquipmentOrder: List<String> = listOf("Barra", "Máquina", "Polia", "Halteres", "Peso Corporal"),
    val isCompoundRequired: Boolean = false
)

interface ExerciseSelector {
    fun selectExercises(
        muscleGroup: String,
        equipmentList: List<String>,
        timeAvailableMinutes: Int,
        restrictions: List<String>
    ): List<Exercise>
}

class ExerciseSelectorImpl : ExerciseSelector {
    override fun selectExercises(
        muscleGroup: String,
        equipmentList: List<String>,
        timeAvailableMinutes: Int,
        restrictions: List<String>
    ): List<Exercise> {
        val allExercises = TemplatesAndExercises.ALL_EXERCISES
        val roleDefinitions = getRoleDefinitionsForSplit(muscleGroup)

        // Determine how many roles to fill based on time available
        val isFullBodySplit = muscleGroup.trim().startsWith("Full Body", ignoreCase = true)
        val rolesToUse = when {
            isFullBodySplit && timeAvailableMinutes >= 45 -> roleDefinitions
            timeAvailableMinutes <= 35 -> roleDefinitions.take(4)
            timeAvailableMinutes <= 50 -> roleDefinitions.take(5)
            timeAvailableMinutes <= 70 -> roleDefinitions.take(6)
            else -> roleDefinitions // Take all defined roles + optional duplicates for long sessions
        }

        val selectedExercises = mutableListOf<Exercise>()
        val selectedIds = mutableSetOf<String>()
        var axialExercisesSelected = 0

        for (role in rolesToUse) {
            val candidate = findCandidateForRole(
                role = role,
                availableExercises = allExercises,
                equipmentList = equipmentList,
                restrictions = restrictions,
                alreadySelectedIds = selectedIds,
                forbidAxialSpinal = isFullBodySplit && axialExercisesSelected >= 1
            )
            if (candidate != null) {
                val processedCandidate = if (!candidate.category.equals(muscleGroup, true) &&
                    !candidate.primaryMuscle.equals(muscleGroup, true) &&
                    !candidate.secondaryMuscles.any { it.equals(muscleGroup, true) }) {
                    candidate.copy(secondaryMuscles = candidate.secondaryMuscles + muscleGroup)
                } else {
                    candidate
                }
                selectedExercises.add(processedCandidate)
                selectedIds.add(candidate.id)
                if (isAxialSpinalExercise(candidate)) {
                    axialExercisesSelected++
                }
            }
        }

        // If time is long (>70m) and we need 1-2 more exercises, pick high-value complementary/synergist exercises
        if (timeAvailableMinutes > 70 && selectedExercises.size < 8) {
            val complementaryRoles = listOf(
                RoleDefinition(
                    roleType = FunctionalRoleType.ISOLATION_TARGET,
                    name = "Variação de Isolamento",
                    description = "Segunda variação de isolamento",
                    targetMuscles = roleDefinitions.firstOrNull()?.targetMuscles ?: listOf(muscleGroup),
                    keywords = listOf("crucifixo", "cross over", "elevação", "pullover", "flexora", "extensora")
                ),
                RoleDefinition(
                    roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                    name = "Finalizador Metabólico Adicional",
                    description = "Pump adicional em músculo auxiliar",
                    targetMuscles = listOf("Tríceps", "Bíceps", "Ombros", "Abdominais", "Pernas"),
                    keywords = listOf("corda", "martelo", "prancha", "panturrilha", "flexão")
                )
            )

            for (cRole in complementaryRoles) {
                if (selectedExercises.size >= 8) break
                val extraCandidate = findCandidateForRole(
                    role = cRole,
                    availableExercises = allExercises,
                    equipmentList = equipmentList,
                    restrictions = restrictions,
                    alreadySelectedIds = selectedIds
                )
                if (extraCandidate != null) {
                    selectedExercises.add(extraCandidate)
                    selectedIds.add(extraCandidate.id)
                }
            }
        }

        // Fallback: If for any reason fewer than 3 exercises were selected (e.g. strict restrictions), fill from category
        if (selectedExercises.size < 3) {
            val targetCategory = when (muscleGroup.lowercase().trim()) {
                "peito", "chest" -> "Peito"
                "costas", "back" -> "Costas"
                "pernas", "legs", "perna" -> "Pernas"
                "ombros", "shoulders", "ombro" -> "Ombros"
                "bíceps", "biceps" -> "Bíceps"
                "tríceps", "triceps" -> "Tríceps"
                "trapézio", "trapezio" -> "Trapézio"
                "abdominais", "core" -> "Abdominais"
                else -> muscleGroup
            }

            val fallbackCandidates = allExercises.filter { ex ->
                !selectedIds.contains(ex.id) &&
                (ex.category.equals(targetCategory, ignoreCase = true) || ex.primaryMuscle.equals(targetCategory, ignoreCase = true)) &&
                restrictions.none { r -> r.isNotEmpty() && ex.name.lowercase().contains(r.lowercase()) }
            }

            for (fEx in fallbackCandidates) {
                if (selectedExercises.size >= 4) break
                selectedExercises.add(fEx)
                selectedIds.add(fEx.id)
            }
        }

        // Apply Variety Rules & Implement Alternation
        val varied = enforceVarietyAndImplementAlternation(
            selected = selectedExercises,
            allAvailable = allExercises,
            equipmentList = equipmentList,
            restrictions = restrictions
        )
        return if (isFullBodySplit) {
            enforceZigZagLowerUpperOrder(varied)
        } else {
            varied
        }
    }

    /**
     * Lei do Teto Axial (Máx 1 Exercício Axial/Eretores da Espinha por Sessão Full Body):
     * Identifica exercícios livres que exigem forte estabilização isométrica lombar/axial.
     */
    private fun isAxialSpinalExercise(ex: Exercise): Boolean {
        val n = ex.name.lowercase()
        val eq = ex.equipment.lowercase()
        val isGuidedMachineOrCable = n.contains("máquina") || n.contains("maquina") ||
            n.contains("polia") || n.contains("cabo") || n.contains("torre") ||
            n.contains("leg press") || n.contains("cadeira") || n.contains("mesa") ||
            n.contains("apoio no peito") || n.contains("standing hip thrust") ||
            eq.contains("polia") || eq.contains("máquina") || eq.contains("maquina")
        if (isGuidedMachineOrCable) return false

        return n.contains("terra") || n.contains("romeno") || n.contains("rdl") ||
            n.contains("stiff") || n.contains("agachamento livre") ||
            n.contains("agachamento frontal") || n.contains("remada curvada") ||
            n.contains("remada cavalinho") || n.contains("bom dia") || n.contains("good morning")
    }

    private fun isLowerBodyExercise(ex: Exercise): Boolean {
        val c = ex.category.lowercase()
        val p = ex.primaryMuscle.lowercase()
        val n = ex.name.lowercase()
        return c.contains("perna") || c.contains("quadríceps") || c.contains("posterior") || c.contains("glúteo") ||
            p.contains("perna") || p.contains("quadríceps") || p.contains("posterior") || p.contains("glúteo") ||
            n.contains("agachamento") || n.contains("leg press") || n.contains("extensora") ||
            n.contains("flexora") || n.contains("terra") || n.contains("romeno") || n.contains("rdl") ||
            n.contains("stiff") || n.contains("hip thrust") || n.contains("elevação pélvica") ||
            n.contains("búlgaro") || n.contains("passada") || n.contains("afundo") || n.contains("panturrilha")
    }

    /**
     * Lei da Costura Alternada (Zig-Zag Inferior ⇄ Superior):
     * Proíbe empilhar 2 ou 3 exercícios de perna seguidos quando há exercícios de membros superiores/core para intercalar.
     */
    private fun enforceZigZagLowerUpperOrder(selected: List<Exercise>): List<Exercise> {
        if (selected.size <= 2) return selected
        val lowerQueue = ArrayDeque(selected.filter { isLowerBodyExercise(it) })
        val upperQueue = ArrayDeque(selected.filter { !isLowerBodyExercise(it) })
        if (lowerQueue.isEmpty() || upperQueue.isEmpty()) return selected

        val stitched = mutableListOf<Exercise>()
        var pickLowerNext = true
        while (lowerQueue.isNotEmpty() || upperQueue.isNotEmpty()) {
            if (pickLowerNext) {
                if (lowerQueue.isNotEmpty()) {
                    stitched.add(lowerQueue.removeFirst())
                } else if (upperQueue.isNotEmpty()) {
                    stitched.add(upperQueue.removeFirst())
                }
            } else {
                if (upperQueue.isNotEmpty()) {
                    stitched.add(upperQueue.removeFirst())
                } else if (lowerQueue.isNotEmpty()) {
                    stitched.add(lowerQueue.removeFirst())
                }
            }
            pickLowerNext = !pickLowerNext
        }
        return stitched
    }

    private fun findCandidateForRole(
        role: RoleDefinition,
        availableExercises: List<Exercise>,
        equipmentList: List<String>,
        restrictions: List<String>,
        alreadySelectedIds: Set<String>,
        forbidAxialSpinal: Boolean = false
    ): Exercise? {
        val normalizedEquipments = equipmentList.map { it.lowercase().trim() }

        val candidates = availableExercises.filter { ex ->
            if (alreadySelectedIds.contains(ex.id)) return@filter false
            if (forbidAxialSpinal && isAxialSpinalExercise(ex)) return@filter false

            // Check restriction/injury
            val isRestricted = restrictions.any { r ->
                val rLower = r.lowercase().trim()
                rLower.isNotEmpty() && (ex.name.lowercase().contains(rLower) || ex.category.lowercase().contains(rLower))
            }
            if (isRestricted) return@filter false

            // Match muscle group (primary, category, or secondary)
            val muscleMatch = role.targetMuscles.any { tm ->
                ex.category.equals(tm, ignoreCase = true) ||
                ex.primaryMuscle.equals(tm, ignoreCase = true) ||
                ex.secondaryMuscles.any { sm -> sm.equals(tm, ignoreCase = true) }
            }
            if (!muscleMatch) return@filter false

            // Match equipment availability
            val equipMatch = normalizedEquipments.isEmpty() ||
                    ex.equipment.lowercase().trim() == "peso corporal" ||
                    normalizedEquipments.contains(ex.equipment.lowercase().trim()) ||
                    normalizedEquipments.contains("todos") ||
                    normalizedEquipments.any { available -> ex.equipment.lowercase().contains(available) || available.contains(ex.equipment.lowercase()) }

            if (!equipMatch) return@filter false

            // If compound required
            if (role.isCompoundRequired && !(ex.compound || ex.exerciseType.lowercase() == "composto")) {
                return@filter false
            }

            true
        }

        if (candidates.isEmpty()) return null

        // Score candidates based on keyword match, equipment preference, and compound status
        return candidates.maxByOrNull { ex ->
            var score = 0

            // Keyword score
            val exNameLower = ex.name.lowercase()
            val keywordMatchIndex = role.keywords.indexOfFirst { kw -> exNameLower.contains(kw.lowercase()) }
            if (keywordMatchIndex != -1) {
                score += (100 - keywordMatchIndex * 10)
            }

            // Equipment score based on role's preferredEquipmentOrder
            val equipIndex = role.preferredEquipmentOrder.indexOfFirst { eq -> ex.equipment.lowercase().contains(eq.lowercase()) }
            if (equipIndex != -1) {
                score += (50 - equipIndex * 5)
            }

            if (ex.compound || ex.exerciseType.lowercase() == "composto") {
                score += 20
            }

            score
        }
    }

    private fun enforceVarietyAndImplementAlternation(
        selected: List<Exercise>,
        allAvailable: List<Exercise>,
        equipmentList: List<String>,
        restrictions: List<String>
    ): List<Exercise> {
        val result = selected.toMutableList()
        if (result.size < 3) return result

        // 1. Prevent 3 consecutive pull-downs ("puxadas"), 3 consecutive rows ("remadas"), or 3 identical implements in a row
        for (i in 0 until result.size - 2) {
            val ex1 = result[i]
            val ex2 = result[i + 1]
            val ex3 = result[i + 2]

            val is3Pulldowns = isPulldown(ex1) && isPulldown(ex2) && isPulldown(ex3)
            val is3Rows = isRow(ex1) && isRow(ex2) && isRow(ex3)
            val is3SameImplement = getEquipmentGroup(ex1.equipment) == getEquipmentGroup(ex2.equipment) &&
                    getEquipmentGroup(ex2.equipment) == getEquipmentGroup(ex3.equipment)

            if (is3Pulldowns || is3Rows || is3SameImplement) {
                // Try swapping middle exercise with subsequent exercise if available
                if (i + 3 < result.size) {
                    val temp = result[i + 1]
                    result[i + 1] = result[i + 3]
                    result[i + 3] = temp
                } else if (i > 0) {
                    val temp = result[i]
                    result[i] = result[i + 1]
                    result[i + 1] = temp
                }
            }
        }

        return result
    }

    private fun getEquipmentGroup(equip: String): String {
        val e = equip.lowercase()
        return when {
            e.contains("barra") -> "Barra"
            e.contains("halter") -> "Halter"
            e.contains("polia") || e.contains("cabo") -> "Cabo"
            e.contains("máquina") || e.contains("maquina") -> "Máquina"
            e.contains("peso corporal") || e.contains("bodyweight") -> "Peso Corporal"
            else -> equip
        }
    }

    private fun isPulldown(ex: Exercise): Boolean {
        val n = ex.name.lowercase()
        return n.contains("puxada") || n.contains("pulley") || n.contains("barra fixa") || n.contains("pull-up")
    }

    private fun isRow(ex: Exercise): Boolean {
        val n = ex.name.lowercase()
        return n.contains("remada") || n.contains("serrote") || n.contains("cavalinho")
    }

    private fun getRoleDefinitionsForSplit(muscleGroup: String): List<RoleDefinition> {
        val normGroup = muscleGroup.lowercase().trim()
        return when {
            normGroup.contains("peito") || normGroup.contains("chest") || normGroup.contains("peitoral") -> {
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "Movimento Pesado Horizontal",
                        description = "Carga tensional para Peitoral Maior",
                        targetMuscles = listOf("Peito", "Peitoral"),
                        keywords = listOf("supino reto", "supino reto barra", "supino reto halteres", "chest press"),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Máquina", "Smith"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "Desenvolvimento Inclinado / Variação",
                        description = "Foco em fibras claviculares e variação angular",
                        targetMuscles = listOf("Peito", "Ombros"),
                        keywords = listOf("supino inclinado", "inclinado halteres", "inclinado barra", "flexão"),
                        preferredEquipmentOrder = listOf("Halteres", "Máquina", "Barra", "Smith")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.UNILATERAL_STABILITY,
                        name = "Sustentação / Unilateral Peitoral",
                        description = "Estabilidade e recrutamento motor unilateral",
                        targetMuscles = listOf("Peito", "Ombros"),
                        keywords = listOf("supino declinado máquina total health", "supino unilateral", "supino máquina", "supino declinado", "flexão", "dips", "paralelas"),
                        preferredEquipmentOrder = listOf("Máquina", "Halteres", "Peso Corporal", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "Estabilizadores Articulares",
                        description = "Fortalecimento do manguito rotador e estabilidade de escápula",
                        targetMuscles = listOf("Ombros", "Peito"),
                        keywords = listOf("manguito", "rotador", "serrátil", "elevação y", "face pull"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Peso Corporal")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "Isolamento Peitoral",
                        description = "Isolamento articular com alto tempo sob tensão",
                        targetMuscles = listOf("Peito"),
                        keywords = listOf("crucifixo", "cross over", "crossover", "voador", "peck deck", "fly"),
                        preferredEquipmentOrder = listOf("Polia", "Máquina", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "Sinergista / Finalização Metabólica",
                        description = "Estímulo secundário em Tríceps/Deltoide e pump",
                        targetMuscles = listOf("Tríceps", "Ombros", "Peito"),
                        keywords = listOf("tríceps corda", "tríceps testa", "elevação frontal", "flexão"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Peso Corporal")
                    )
                )
            }

            normGroup.contains("costas") || normGroup.contains("back") || normGroup.contains("dorsal") -> {
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "Movimento Pesado Horizontal",
                        description = "Remada pesada para espessura de dorsais e romboides",
                        targetMuscles = listOf("Costas", "Trapézio"),
                        keywords = listOf("remada curvada", "remada cavalinho", "levantamento terra", "remada barra"),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Máquina"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "Movimento Pesado Vertical",
                        description = "Puxada alta para largura de latíssimo",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("puxada frontal", "puxada alta", "puxada alta máquina anilhas", "puxada pulley", "barra fixa", "pull-up"),
                        preferredEquipmentOrder = listOf("Polia", "Máquina", "Peso Corporal"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.UNILATERAL_STABILITY,
                        name = "Remada Unilateral",
                        description = "Trabalho unilateral de dorsal e estabilização de tronco",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("serrote", "remada unilateral", "remada articulada", "puxada unilateral"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "Estabilizadores / Deltoide Posterior",
                        description = "Saúde escapular e porção posterior do ombro/trapézio",
                        targetMuscles = listOf("Ombros", "Trapézio", "Costas"),
                        keywords = listOf("face pull", "encolhimento", "crucifixo invertido", "hiperextensão"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "Isolamento / Dorsal Sweeper",
                        description = "Isolamento do latíssimo sem ativação excessiva de braços",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("pullover", "puxada triangulo", "remada baixa"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "Sinergista / Finalização Metabólica",
                        description = "Estímulo indireto em Bíceps / Braquial e hiperemia",
                        targetMuscles = listOf("Bíceps", "Costas"),
                        keywords = listOf("rosca martelo", "rosca direta", "rosca no cabo", "puxada supinada"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Barra")
                    )
                )
            }

            normGroup.contains("ombro") || normGroup.contains("shoulder") || normGroup.contains("deltoid") -> {
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "Movimento Pesado Overhead",
                        description = "Desenvolvimento tensional para deltoide",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("desenvolvimento militar", "desenvolvimento halteres", "desenvolvimento arnold", "desenvolvimento barra"),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Máquina", "Smith"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "Isolamento Lateral Pesado",
                        description = "Foco na cabeça lateral do deltoide",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("elevação lateral", "elevação lateral halteres", "elevação lateral polia"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "Estabilizadores / Deltoide Posterior",
                        description = "Manguito e porção posterior do ombro",
                        targetMuscles = listOf("Ombros", "Costas"),
                        keywords = listOf("face pull", "crucifixo invertido", "manguito", "elevação y"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "Isolamento Deltoide Anterior / Ângulo",
                        description = "Trabalho específico de deltoide anterior",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("elevação frontal", "elevação frontal cabo", "elevação frontal halteres"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "Sinergista Trapézio / Tríceps",
                        description = "Ativação de trapézio e tríceps em supersérie/pump",
                        targetMuscles = listOf("Trapézio", "Tríceps", "Ombros"),
                        keywords = listOf("encolhimento", "remada alta", "tríceps francês", "tríceps corda"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.UNILATERAL_STABILITY,
                        name = "Controle Unilateral em Polia",
                        description = "Tensão contínua unilateral",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("elevação lateral polia", "desenvolvimento unilateral", "crucifixo invertido polia"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres")
                    )
                )
            }

            normGroup.contains("perna") || normGroup.contains("leg") || normGroup.contains("quadriceps") || normGroup.contains("posterior") || normGroup.contains("gluteo") -> {
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "Movimento Pesado Quadríceps",
                        description = "Carga axial e tensional para quadríceps/glúteos",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("agachamento livre", "agachamento frontal", "leg press 45", "agachamento hack"),
                        preferredEquipmentOrder = listOf("Barra", "Máquina", "Smith", "Halteres"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "Movimento Pesado Posterior / Glúteos",
                        description = "Extensão de quadril e cadeia posterior",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("stiff", "levantamento terra sumô", "hip thrust", "agachamento búlgaro"),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Máquina"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.UNILATERAL_STABILITY,
                        name = "Unilateral / Passada",
                        description = "Equilíbrio, estabilidade de joelho e glúteo médio",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("afundo", "passada", "agachamento búlgaro", "leg press horizontal"),
                        preferredEquipmentOrder = listOf("Halteres", "Peso Corporal", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "Isolamento Quadríceps / Posterior",
                        description = "Isolamento articular tensional em máquinas",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("cadeira extensora", "mesa flexora", "cadeira flexora"),
                        preferredEquipmentOrder = listOf("Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "Sinergista Panturrilhas / Abdução",
                        description = "Trabalho específico de panturrilhas e glúteo abdução",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("panturrilha em pé", "panturrilha no leg press", "panturrilha unilateral", "cadeira abdutora"),
                        preferredEquipmentOrder = listOf("Máquina", "Peso Corporal")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "Estabilizadores do Core",
                        description = "Estabilidade lombopélvica para suporte de cargas pesadas",
                        targetMuscles = listOf("Abdominais", "Core", "Pernas"),
                        keywords = listOf("prancha", "pallof press", "rollout", "dead bug"),
                        preferredEquipmentOrder = listOf("Peso Corporal", "Polia")
                    )
                )
            }

            normGroup.contains("full body b") -> {
                // Full Body B: Costura Alternada Zig-Zag (Inferior ⇄ Superior) + Prioridade Torre de Placas / Máquinas (Zero Axial) + Trapézio e Panturrilha
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "1. Perna Guiado (Quadríceps)",
                        description = "Manutenção de membros inferiores em torre de placas sem impacto articular",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("cadeira extensora", "leg press horizontal", "leg press"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "2. Superior Empurrar (Máquina / Inclinado)",
                        description = "Foco peitoral com estabilidade articular",
                        targetMuscles = listOf("Peito"),
                        keywords = listOf("supino declinado máquina total health", "supino inclinado halteres", "supino máquina"),
                        preferredEquipmentOrder = listOf("Máquina", "Halteres", "Polia"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "3. Perna Posterior Guiado",
                        description = "Estímulo controlado de isquiotibiais em torre de placas",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("cadeira flexora", "mesa flexora"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "4. Superior Puxar Vertical (Torre de Placas)",
                        description = "Desenvolvimento de largura dorsal",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("puxada alta na polia", "puxada frontal", "puxada alta", "puxada pulley"),
                        preferredEquipmentOrder = listOf("Polia", "Máquina"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "5. Dorsal Densidade Guiada (Máquina / Torre)",
                        description = "Espessura e largura dorsal sem sobrecarga lombar",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("puxada alta máquina anilhas", "remada sentada na máquina", "remada na máquina", "remada baixa"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "6. Ombros (Deltoide Lateral)",
                        description = "Volume lateral de ombros",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("elevação lateral"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "7. Trapézio / Cintura Escapular",
                        description = "Trabalho direto de trapézio e estabilidade escapular",
                        targetMuscles = listOf("Trapézio", "Ombros", "Costas"),
                        keywords = listOf("face pull", "encolhimento", "remada alta"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "8. Panturrilha Direta",
                        description = "Estímulo direto para gastrocnêmio e sóleo",
                        targetMuscles = listOf("Panturrilha", "Pernas"),
                        keywords = listOf("panturrilha no leg press", "panturrilha em pé", "panturrilha sentado", "panturrilha"),
                        preferredEquipmentOrder = listOf("Máquina", "Leg Press", "Smith", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "9. Braços / Bíceps & Braquial",
                        description = "Trabalho complementar de flexores do cotovelo",
                        targetMuscles = listOf("Bíceps"),
                        keywords = listOf("rosca martelo", "rosca direta", "rosca alternada"),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Barra")
                    )
                )
            }

            normGroup.contains("full body c") -> {
                // Full Body C: Costura Alternada Zig-Zag + Trapézio e Panturrilha Garantidos
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "1. Perna Posterior (Único Axial: RDL)",
                        description = "Cadeia posterior e isquiotibiais",
                        targetMuscles = listOf("Pernas", "Costas"),
                        keywords = listOf("levantamento terra romeno", "terra romeno", "rdl", "stiff"),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Máquina"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "2. Superior Empurrar (Supino Inclinado com Halteres)",
                        description = "Composto multiarticular de peitoral poupando a lombar",
                        targetMuscles = listOf("Peito"),
                        keywords = listOf("supino inclinado halteres", "supino inclinado com halteres", "supino inclinado"),
                        preferredEquipmentOrder = listOf("Halteres", "Máquina"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "3. Perna / Glúteo (Standing Hip Thrust / Elevação Pélvica)",
                        description = "Força de extensão de quadril sem compressão axial",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("standing hip thrust", "hip thrust", "elevação pélvica"),
                        preferredEquipmentOrder = listOf("Máquina", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "4. Superior Puxar Guiado (Torre de Placas / Polia — Zero Carga Axial Lombar)",
                        description = "Tracionamento horizontal guiado em torre de placas ou polia baixa",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("remada sentada na máquina (torre de placas)", "remada na máquina", "remada baixa triângulo", "remada baixa", "remada com apoio no peito"),
                        preferredEquipmentOrder = listOf("Polia", "Máquina", "Halteres"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "5. Perna Guiado (Leg Press Horizontal)",
                        description = "Empuxo de membros inferiores com coluna apoiada",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("leg press horizontal", "leg press", "cadeira extensora"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "6. Ombros Guiado (Desenvolvimento na Máquina)",
                        description = "Estabilidade escapular e deltoides em máquina guiada",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("desenvolvimento na máquina", "desenvolvimento halteres", "desenvolvimento"),
                        preferredEquipmentOrder = listOf("Máquina", "Halteres", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "7. Trapézio / Cintura Escapular",
                        description = "Trabalho direto de trapézio e cintura escapular",
                        targetMuscles = listOf("Trapézio", "Ombros", "Costas"),
                        keywords = listOf("remada alta", "encolhimento", "face pull"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Barra")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "8. Panturrilha Direta",
                        description = "Estímulo direto para sóleo e gastrocnêmio",
                        targetMuscles = listOf("Panturrilha", "Pernas"),
                        keywords = listOf("panturrilha sentado", "panturrilha em pé", "panturrilha no leg press", "panturrilha"),
                        preferredEquipmentOrder = listOf("Máquina", "Leg Press", "Smith", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "9. Core (Abdominal Infra Solo)",
                        description = "Blindagem lombopélvica sem peso externo",
                        targetMuscles = listOf("Abdominais"),
                        keywords = listOf("abdominal infra", "abdominal crunch solo", "prancha"),
                        preferredEquipmentOrder = listOf("Peso Corporal", "Polia")
                    )
                )
            }

            normGroup.contains("full body") -> {
                // Full Body A (ou Full Body padrão): Costura Alternada Zig-Zag + Trapézio e Panturrilha Garantidos
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "1. Perna Base (Único Axial ou Leg Press)",
                        description = "Movimento composto base de membros inferiores",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("agachamento livre", "leg press 45", "agachamento hack"),
                        preferredEquipmentOrder = listOf("Barra", "Máquina", "Smith"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "2. Superior Empurrar Principal",
                        description = "Força base de peitoral, ombros e tríceps",
                        targetMuscles = listOf("Peito"),
                        keywords = listOf("supino reto", "supino halteres", "supino na máquina"),
                        preferredEquipmentOrder = listOf("Barra", "Máquina", "Halteres"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "3. Perna Posterior Guiado (Torre de Placas)",
                        description = "Equilíbrio articular de joelho e quadril sem carga axial",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("cadeira flexora", "mesa flexora"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "4. Superior Puxar Guiado (Torre de Placas / Polia)",
                        description = "Força base de dorsais e romboides sem fadiga lombar",
                        targetMuscles = listOf("Costas"),
                        keywords = listOf("remada sentada na máquina (torre de placas)", "remada baixa triângulo", "remada na máquina", "puxada frontal"),
                        preferredEquipmentOrder = listOf("Polia", "Máquina", "Halteres"),
                        isCompoundRequired = true
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "5. Perna Quadríceps Guiado (Torre de Placas)",
                        description = "Estímulo complementar guiado de quadríceps",
                        targetMuscles = listOf("Pernas"),
                        keywords = listOf("cadeira extensora", "leg press horizontal"),
                        preferredEquipmentOrder = listOf("Máquina", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "6. Ombros Guiado / Halteres",
                        description = "Força vertical de deltoides",
                        targetMuscles = listOf("Ombros"),
                        keywords = listOf("desenvolvimento na máquina", "desenvolvimento halteres", "elevação lateral"),
                        preferredEquipmentOrder = listOf("Máquina", "Halteres", "Polia")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "7. Trapézio / Cintura Escapular",
                        description = "Trabalho direto de trapézio superior e escápulas",
                        targetMuscles = listOf("Trapézio", "Ombros", "Costas"),
                        keywords = listOf("encolhimento halteres", "encolhimento barra", "encolhimento", "face pull"),
                        preferredEquipmentOrder = listOf("Halteres", "Barra", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "8. Panturrilha Direta",
                        description = "Estímulo direto para gastrocnêmio e sóleo",
                        targetMuscles = listOf("Panturrilha", "Pernas"),
                        keywords = listOf("panturrilha em pé", "panturrilha no leg press", "panturrilha sentado", "panturrilha"),
                        preferredEquipmentOrder = listOf("Máquina", "Leg Press", "Smith", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "9. Braços na Polia (Tríceps / Bíceps)",
                        description = "Estímulo metabólico final na torre de cabos",
                        targetMuscles = listOf("Tríceps", "Bíceps"),
                        keywords = listOf("tríceps corda", "tríceps pulley", "rosca direta"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres")
                    )
                )
            }

            else -> {
                // General / Arms / Core fallback
                listOf(
                    RoleDefinition(
                        roleType = FunctionalRoleType.HEAVY_PRIMARY_COMPOUND,
                        name = "Exercício Principal",
                        description = "Movimento multiarticular principal",
                        targetMuscles = listOf(muscleGroup),
                        keywords = listOf(muscleGroup.lowercase()),
                        preferredEquipmentOrder = listOf("Barra", "Halteres", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SECONDARY_DEVELOPMENT,
                        name = "Desenvolvimento Secundário",
                        description = "Variação de ângulo e estímulo",
                        targetMuscles = listOf(muscleGroup),
                        keywords = listOf(muscleGroup.lowercase()),
                        preferredEquipmentOrder = listOf("Halteres", "Polia", "Máquina")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.ISOLATION_TARGET,
                        name = "Isolamento Direcionado",
                        description = "Isolamento articular específico",
                        targetMuscles = listOf(muscleGroup),
                        keywords = listOf(muscleGroup.lowercase()),
                        preferredEquipmentOrder = listOf("Polia", "Máquina", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.JOINT_STABILIZER,
                        name = "Estabilizadores Articulares",
                        description = "Saúde articular e controle motor",
                        targetMuscles = listOf("Ombros", "Abdominais", "Core"),
                        keywords = listOf("manguito", "prancha", "face pull"),
                        preferredEquipmentOrder = listOf("Polia", "Peso Corporal", "Halteres")
                    ),
                    RoleDefinition(
                        roleType = FunctionalRoleType.SYNERGIST_PUMP_FINISHER,
                        name = "Sinergista / Pump",
                        description = "Finalização metabólica em músculo auxiliar",
                        targetMuscles = listOf("Tríceps", "Bíceps", "Abdominais", muscleGroup),
                        keywords = listOf("corda", "martelo", "prancha"),
                        preferredEquipmentOrder = listOf("Polia", "Halteres", "Peso Corporal")
                    )
                )
            }
        }
    }
}

