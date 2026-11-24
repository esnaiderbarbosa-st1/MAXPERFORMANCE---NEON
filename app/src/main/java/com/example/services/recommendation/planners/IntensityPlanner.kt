package com.example.services.recommendation.planners

import com.example.services.recommendation.knowledge.TrainingKnowledgeBase

interface IntensityPlanner {
    data class IntensitySuggestion(
        val recommendedMethods: List<String>, // List of method IDs like "straight_sets", "drop_set", etc.
        val reasons: Map<String, String> // methodId -> explanatory reason
    )

    fun suggestIntensityMethods(
        objective: String,
        experience: String
    ): IntensitySuggestion
}

class IntensityPlannerImpl : IntensityPlanner {
    override fun suggestIntensityMethods(
        objective: String,
        experience: String
    ): IntensityPlanner.IntensitySuggestion {
        // Normalize objective name
        val normalizedObjective = when (objective.lowercase()) {
            "força" -> "strength"
            "hipertrofia" -> "hypertrophy"
            "emagrecimento" -> "weight_loss"
            "resistência" -> "endurance"
            "manutenção" -> "maintenance"
            else -> objective.lowercase()
        }

        val allMethods = TrainingKnowledgeBase.IntensityMethods.getAll()

        // Get athlete's experience level rank
        val experienceRank = when (experience.lowercase()) {
            "iniciante" -> 1
            "intermediário" -> 2
            "avançado" -> 3
            else -> 1
        }

        // Filter methods by compatibility with experience level
        val eligibleMethods = allMethods.filter { method ->
            val methodLevelRank = when (method.recommendedLevel.lowercase()) {
                "iniciante" -> 1
                "intermediário" -> 2
                "avançado" -> 3
                else -> 1
            }
            experienceRank >= methodLevelRank
        }

        // Filter compatible methods by whether they align with the training objective
        val recommendedMethods = eligibleMethods.filter { method ->
            method.recommendedGoals.contains(normalizedObjective) || method.id == "straight_sets"
        }

        // Map reasons
        val reasonsMap = recommendedMethods.associate { method ->
            val explanation = when (method.id) {
                "straight_sets" -> "Método padrão ideal para manter a carga constante e focar na execução correta de cada série."
                "top_set" -> "Permite estimular ganho de força máxima na primeira série de trabalho com total foco mental."
                "back_off" -> "Ajuda a acumular volume extra de qualidade com fadiga reduzida após a série principal pesada."
                "drop_set" -> "Garante altíssimo acúmulo de estresse metabólico e bombeamento sanguíneo em exercícios de isolamento."
                "rest_pause" -> "Excelente para manter alta intensidade de carga enquanto acumula repetições adicionais com curtos descansos."
                "cluster" -> "Altamente eficiente para manter a velocidade e potência do movimento reduzindo o acúmulo de ácido lático."
                "superset" -> "Maximiza a densidade do treino, permitindo treinar mais em menos tempo poupando o sistema cardiovascular."
                "giant_set" -> "Gera altíssimo gasto calórico e condicionamento muscular metabólico com alto estímulo de resistência."
                "myo_reps" -> "Variação eficiente de rest-pause excelente para hipertrofia com economia de tempo significativa."
                "feeder_sets" -> "Acelera a recuperação tecidual direcionando fluxo sanguíneo localizado sem sobrecarregar as articulações."
                else -> method.description
            }
            method.id to explanation
        }

        return IntensityPlanner.IntensitySuggestion(
            recommendedMethods = recommendedMethods.map { it.id },
            reasons = reasonsMap
        )
    }
}
