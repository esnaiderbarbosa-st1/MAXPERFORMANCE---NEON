package com.example.constants

/**
 * SPRINT UX-002 — HOTFIX-UX-014: ILLUSTRATION CONFIDENCE VALIDATION
 * Manually maintained table of biomechanical aliases for exercise illustrations.
 * Never generated automatically.
 */
data class AliasEntry(
    val aliasIllustrationId: String,
    val confidenceScore: Float // Value from 0.0f to 1.0f (must be >= 0.70f to be accepted)
)

val IllustrationAliases: Map<String, List<AliasEntry>> = mapOf(
    "bench_press_machine" to listOf(
        AliasEntry("bench_press_plate_loaded", 0.90f),
        AliasEntry("bench_press_selectorized", 0.85f)
    ),
    "lat_pulldown" to listOf(
        AliasEntry("lat_pulldown_wide", 0.90f),
        AliasEntry("lat_pulldown_neutral", 0.85f)
    ),
    "curl_alternating_db" to listOf(
        AliasEntry("curl_standing_db", 0.85f),
        AliasEntry("curl_hammer_db", 0.72f)
    ),
    "supino_inclinado_halteres" to listOf(
        AliasEntry("bench_press_incline_db", 0.90f)
    ),
    "leg_press_45" to listOf(
        AliasEntry("leg_press_horizontal", 0.80f)
    ),
    "seated_cable_row" to listOf(
        AliasEntry("seated_row_narrow", 0.88f)
    ),
    "cable_crossover_high" to listOf(
        AliasEntry("cable_flye_high", 0.85f)
    ),
    "smith_squat" to listOf(
        AliasEntry("smith_machine_squat", 0.95f)
    ),
    "dumbbell_shoulder_press" to listOf(
        AliasEntry("seated_dumbbell_press", 0.90f)
    )
)

/**
 * Returns the highest confidence alias entry for a given illustrationId, provided confidence >= 0.70f (70%).
 */
fun getBiomechanicalAlias(illustrationId: String): AliasEntry? {
    val entries = IllustrationAliases[illustrationId.lowercase()] ?: return null
    return entries.filter { it.confidenceScore >= 0.70f }
        .maxByOrNull { it.confidenceScore }
}
