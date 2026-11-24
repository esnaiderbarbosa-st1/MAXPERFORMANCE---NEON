package com.example.services.recommendation.planners

interface RecoveryPlanner {
    data class RecoveryActions(
        val volumeAdjustmentPercent: Double,
        val shouldSwapExercise: Boolean,
        val avoidMethods: List<String>,
        val restAdjustmentSeconds: Int,
        val recommendationText: String
    )

    fun assessRecovery(recoveryIndex: Double): RecoveryActions
}

class RecoveryPlannerImpl : RecoveryPlanner {
    override fun assessRecovery(recoveryIndex: Double): RecoveryPlanner.RecoveryActions {
        return when {
            recoveryIndex >= 8.0 -> {
                RecoveryPlanner.RecoveryActions(
                    volumeAdjustmentPercent = 1.0,
                    shouldSwapExercise = false,
                    avoidMethods = emptyList(),
                    restAdjustmentSeconds = 0,
                    recommendationText = "Recuperação ideal. Pronto para treinar com volume máximo e métodos avançados."
                )
            }
            recoveryIndex >= 6.0 -> {
                RecoveryPlanner.RecoveryActions(
                    volumeAdjustmentPercent = 1.0,
                    shouldSwapExercise = false,
                    avoidMethods = emptyList(),
                    restAdjustmentSeconds = 15,
                    recommendationText = "Fadiga leve acumulada. Volume mantido, descanso aumentado ligeiramente em 15s."
                )
            }
            recoveryIndex >= 4.0 -> {
                RecoveryPlanner.RecoveryActions(
                    volumeAdjustmentPercent = 0.8,
                    shouldSwapExercise = true,
                    avoidMethods = listOf("drop_set", "myo_reps"),
                    restAdjustmentSeconds = 30,
                    recommendationText = "Fadiga moderada. Redução de 20% no volume, aumento de 30s no descanso e evitação de técnicas metabólicas extremas."
                )
            }
            recoveryIndex >= 2.0 -> {
                RecoveryPlanner.RecoveryActions(
                    volumeAdjustmentPercent = 0.6,
                    shouldSwapExercise = true,
                    avoidMethods = listOf("drop_set", "rest_pause", "myo_reps", "top_set"),
                    restAdjustmentSeconds = 45,
                    recommendationText = "Fadiga severa. Redução de 40% no volume, substituição de exercícios de alto estresse e evitação de métodos avançados."
                )
            }
            else -> {
                // < 2.0 (Extreme fatigue, deload/recovery protocol)
                RecoveryPlanner.RecoveryActions(
                    volumeAdjustmentPercent = 0.4,
                    shouldSwapExercise = true,
                    avoidMethods = listOf("drop_set", "rest_pause", "myo_reps", "top_set", "back_off", "cluster", "superset", "giant_set"),
                    restAdjustmentSeconds = 60,
                    recommendationText = "Fadiga extrema ou lesão! Recomendado semana de Deload: redução drástica de volume e sem métodos de intensidade."
                )
            }
        }
    }
}
