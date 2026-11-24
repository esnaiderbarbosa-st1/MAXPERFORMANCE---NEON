package com.example.utils

enum class FeatureType {
    FEATURE_TREINOS_BASICOS,
    FEATURE_TREINOS_INTERMEDIARIOS,
    FEATURE_TREINOS_AVANCADOS,
    FEATURE_BIOIMPEDANCIA,
    FEATURE_IA,
    FEATURE_DASHBOARD_METRICAS,
    FEATURE_EXPORTACAO,
    FEATURE_NUTRICAO,
    FEATURE_PAINEL_ADMIN
}

object AccessControl {

    fun isFeatureEnabled(nivelAcesso: String, feature: FeatureType): Boolean {
        val normalizedNivel = nivelAcesso.uppercase()
        return when (feature) {
            FeatureType.FEATURE_TREINOS_BASICOS -> {
                normalizedNivel in listOf("BASICO", "INTERMEDIARIO", "FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_TREINOS_INTERMEDIARIOS -> {
                normalizedNivel in listOf("INTERMEDIARIO", "FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_TREINOS_AVANCADOS -> {
                normalizedNivel in listOf("FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_BIOIMPEDANCIA -> {
                normalizedNivel in listOf("INTERMEDIARIO", "FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_IA -> {
                normalizedNivel in listOf("INTERMEDIARIO", "FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_DASHBOARD_METRICAS -> {
                normalizedNivel in listOf("FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_EXPORTACAO -> {
                normalizedNivel in listOf("FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_NUTRICAO -> {
                normalizedNivel in listOf("FULL", "SUPERUSER")
            }
            FeatureType.FEATURE_PAINEL_ADMIN -> {
                normalizedNivel == "SUPERUSER"
            }
        }
    }

    fun getNivelDisplayName(nivel: String): String {
        return when (nivel.uppercase()) {
            "BASICO" -> "Nível Básico"
            "INTERMEDIARIO" -> "Nível Intermediário"
            "FULL" -> "Nível Full (Completo)"
            "SUPERUSER" -> "Superusuário (Administrador)"
            else -> "Aguardando Aprovação"
        }
    }

    fun getNivelColorHex(nivel: String): Long {
        return when (nivel.uppercase()) {
            "BASICO" -> 0xFF81C784L
            "INTERMEDIARIO" -> 0xFF64B5F6L
            "FULL" -> 0xFFFFB74DL
            "SUPERUSER" -> 0xFFE57373L
            else -> 0xFFB0BEC5L
        }
    }

    @Deprecated(
        message = "Introduces Android framework dependency inside domain utility. Use getNivelColorHex or UI-level color mapper.",
        replaceWith = ReplaceWith("Color(AccessControl.getNivelColorHex(nivel))")
    )
    fun getNivelColor(nivel: String): androidx.compose.ui.graphics.Color {
        return androidx.compose.ui.graphics.Color(getNivelColorHex(nivel))
    }
}
