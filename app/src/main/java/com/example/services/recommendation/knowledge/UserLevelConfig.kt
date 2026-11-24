package com.example.services.recommendation.knowledge

data class UserLevelConfig(
    val level: String,
    val minMainExercises: Int,
    val maxMainExercises: Int,
    val setsPerExercise: Int,
    val rirAlvoMin: Int,
    val rirAlvoMax: Int,
    val allowedMethods: List<String>,
    val blockedMethods: List<String>
) {
    /** Faixa total incluindo aquecimento + alongamento. */
    val totalExercisesRange: IntRange
        get() = (minMainExercises + 2)..(maxMainExercises + 2)
}

object UserLevelConfigs {

    val INICIANTE = UserLevelConfig(
        level = "Iniciante",
        minMainExercises = 3,
        maxMainExercises = 6,
        setsPerExercise = 3,
        rirAlvoMin = 2,
        rirAlvoMax = 3,
        allowedMethods = listOf("straight_sets"),
        blockedMethods = listOf("cluster", "top_set", "drop_set", "rest_pause", "myo_reps", "punto_zero")
    )

    val INTERMEDIARIO = UserLevelConfig(
        level = "Intermediário",
        minMainExercises = 4,
        maxMainExercises = 8,
        setsPerExercise = 3,
        rirAlvoMin = 1,
        rirAlvoMax = 2,
        allowedMethods = listOf("straight_sets", "back_off", "drop_set", "rest_pause", "superset"),
        blockedMethods = listOf("cluster", "top_set", "punto_zero")
    )

    val AVANCADO = UserLevelConfig(
        level = "Avançado",
        minMainExercises = 5,
        maxMainExercises = 7,
        setsPerExercise = 4,
        rirAlvoMin = 0,
        rirAlvoMax = 2,
        allowedMethods = listOf(
            "straight_sets", "back_off", "top_set", "drop_set",
            "rest_pause", "myo_reps", "superset", "cluster", "giant_set"
        ),
        blockedMethods = emptyList()
    )

    fun getByLevel(level: String): UserLevelConfig {
        val normalized = java.text.Normalizer.normalize(level ?: "", java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
        return when {
            normalized.contains("inician") || normalized.contains("beginn") || normalized.contains("novat") -> INICIANTE
            normalized.contains("avanc") || normalized.contains("advanc") -> AVANCADO
            normalized.contains("intermed") -> INTERMEDIARIO
            else -> INTERMEDIARIO
        }
    }
}
