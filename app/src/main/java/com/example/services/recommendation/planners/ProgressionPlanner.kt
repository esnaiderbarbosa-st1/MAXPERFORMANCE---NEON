package com.example.services.recommendation.planners

import com.example.services.recommendation.knowledge.TrainingKnowledgeBase

interface ProgressionPlanner {
    data class ProgressionSuggestion(
        val primaryStrategyId: String,
        val displayName: String,
        val description: String,
        val alternativeStrategyIds: List<String>,
        val reason: String
    )

    fun suggestProgression(
        objective: String,
        experience: String
    ): ProgressionSuggestion
}

class ProgressionPlannerImpl : ProgressionPlanner {
    override fun suggestProgression(
        objective: String,
        experience: String
    ): ProgressionPlanner.ProgressionSuggestion {
        // Normalize values
        val normalizedObjective = when (objective.lowercase()) {
            "força" -> "strength"
            "hipertrofia" -> "hypertrophy"
            "emagrecimento" -> "weight_loss"
            "resistência" -> "endurance"
            "manutenção" -> "maintenance"
            else -> objective.lowercase()
        }

        val allStrategies = TrainingKnowledgeBase.ProgressionStrategies.getAll()

        // Filter based on objective and experience level
        // Experience is: "Iniciante", "Intermediário", "Avançado"
        val experienceRank = when (experience.lowercase()) {
            "iniciante" -> 1
            "intermediário" -> 2
            "avançado" -> 3
            else -> 1
        }

        // Helper to check strategy minimum experience level suitability
        fun isExperienceCompatible(strategyMinExp: String): Boolean {
            val strategyRank = when (strategyMinExp.lowercase()) {
                "iniciante" -> 1
                "intermediário" -> 2
                "avançado" -> 3
                else -> 1
            }
            return experienceRank >= strategyRank
        }

        // Find matches
        val compatibleStrategies = allStrategies.filter { strategy ->
            isExperienceCompatible(strategy.minimumExperience) &&
            (strategy.recommendedGoals.contains(normalizedObjective) || strategy.id == "linear")
        }

        // Select primary strategy
        val primary = when {
            experienceRank == 1 -> {
                // Beginners should almost always use Linear progression
                allStrategies.firstOrNull { it.id == "linear" } ?: allStrategies.first()
            }
            normalizedObjective == "strength" -> {
                // Strength intermediate/advanced
                if (experienceRank == 3) {
                    allStrategies.firstOrNull { it.id == "top_set_back_off" } ?:
                    allStrategies.firstOrNull { it.id == "reverse_pyramid" } ?:
                    allStrategies.first()
                } else {
                    allStrategies.firstOrNull { it.id == "reverse_pyramid" } ?:
                    allStrategies.firstOrNull { it.id == "linear" } ?:
                    allStrategies.first()
                }
            }
            normalizedObjective == "hypertrophy" || normalizedObjective == "bodybuilding" -> {
                // Hypertrophy intermediate/advanced
                if (experienceRank == 3) {
                    allStrategies.firstOrNull { it.id == "top_set_back_off" } ?:
                    allStrategies.firstOrNull { it.id == "double_progression" } ?:
                    allStrategies.first()
                } else {
                    allStrategies.firstOrNull { it.id == "double_progression" } ?:
                    allStrategies.first()
                }
            }
            normalizedObjective == "performance" -> {
                if (experienceRank == 3) {
                    allStrategies.firstOrNull { it.id == "wave_loading" } ?:
                    allStrategies.firstOrNull { it.id == "daily_undulating" } ?:
                    allStrategies.first()
                } else {
                    allStrategies.firstOrNull { it.id == "linear" } ?:
                    allStrategies.first()
                }
            }
            else -> {
                compatibleStrategies.firstOrNull() ?: allStrategies.first()
            }
        }

        val alternatives = compatibleStrategies
            .filter { it.id != primary.id }
            .map { it.id }

        val reasonText = when (experienceRank) {
            1 -> "Para nível Iniciante, a progressão Linear é recomendada por permitir ganhos constantes de força neural de forma segura."
            2 -> "Para nível Intermediário com foco em ${primary.name}, a estratégia ${primary.name} equilibra volume de trabalho e sobrecarga progressiva."
            3 -> "Para atletas Avançados, a estratégia de ${primary.name} é recomendada para quebrar platôs de força e hipertrofia com segurança."
            else -> "Recomendação baseada nas diretrizes do KnowledgeBase para o objetivo informado."
        }

        return ProgressionPlanner.ProgressionSuggestion(
            primaryStrategyId = primary.id,
            displayName = primary.name,
            description = primary.description,
            alternativeStrategyIds = alternatives,
            reason = reasonText
        )
    }
}
