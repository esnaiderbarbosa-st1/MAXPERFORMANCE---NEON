package com.example.data.model

import java.io.Serializable

// --- 1. VALUE CLASSES TO ELIMINATE PRIMITIVE OBSESSION ---

@JvmInline
value class BodyWeight(val kg: Float) : Serializable {
    init {
        require(kg > 0f && kg < 1000f) { "Weight must be between 0 and 1000 kg ($kg)" }
    }
}

@JvmInline
value class BodyHeight(val cm: Int) : Serializable {
    init {
        require(cm > 0 && cm < 300) { "Height must be between 0 and 300 cm ($cm)" }
    }
}

@JvmInline
value class HeartRate(val bpm: Int) : Serializable {
    init {
        require(bpm in 0..300) { "Heart rate must be between 0 and 300 bpm ($bpm)" }
    }
}

@JvmInline
value class EnergyCalories(val kcal: Int) : Serializable {
    init {
        require(kcal >= 0) { "Energy must be non-negative ($kcal)" }
    }
}

@JvmInline
value class BodyFatPercent(val value: Float) : Serializable {
    init {
        require(value in 0f..100f) { "Body fat percent must be in 0..100 range ($value)" }
    }
}

@JvmInline
value class BodyWaterPercent(val value: Float) : Serializable {
    init {
        require(value in 0f..100f) { "Body water percent must be in 0..100 range ($value)" }
    }
}

@JvmInline
value class MusclePercent(val value: Float) : Serializable {
    init {
        require(value in 0f..100f) { "Muscle percent must be in 0..100 range ($value)" }
    }
}

// --- 2. ENUMS FOR CARDIO & HEALTH STANDARDIZATION ---

enum class HeartRateZone(val displayName: String, val description: String) {
    REST("Repouso", "Frequência cardíaca basal"),
    WARM_UP("Aquecimento", "Zona aeróbica leve de recuperação (50-60% FCMax)"),
    FAT_BURN("Queima de Gordura", "Zona aeróbica moderada / queima lipídica (60-70% FCMax)"),
    AEROBIC("Aeróbica", "Melhoria da capacidade cardiovascular geral (70-80% FCMax)"),
    ANAEROBIC("Anaeróbica", "Zona de limiar de lactato e performance de pico (80-90% FCMax)"),
    RED_LINE("Zona Limite", "Esforço máximo de curta duração (90-100% FCMax)")
}

enum class BmiClassification(val displayName: String, val description: String) {
    UNDERWEIGHT("Abaixo do Peso", "Abaixo do peso ideal saudável"),
    NORMAL("Peso Normal", "Intervalo de peso ideal e saudável"),
    OVERWEIGHT("Sobrepeso", "Acima do peso saudável preconizado"),
    OBESITY_CLASS_1("Obesidade Grau I", "Moderadamente acima do peso ideal"),
    OBESITY_CLASS_2("Obesidade Grau II", "Severamente acima do peso ideal"),
    OBESITY_CLASS_3("Obesidade Mórbida", "Risco muito elevado para a saúde")
}

enum class IntegrationSource(val displayName: String) {
    MANUAL("Registro Manual"),
    HEALTH_CONNECT("Google Health Connect"),
    GARMIN("Garmin Connect"),
    POLAR("Polar Flow"),
    WEAR_OS("Wear OS Smartwatch"),
    FITBIT("Fitbit")
}

// --- 3. PLATFORM INTEGRATION ENVELOPE CONTRACTS (PREPARATION FOR THE FUTURE) ---

data class HealthPlatformMetadata(
    val source: IntegrationSource,
    val deviceId: String?,
    val externalRecordId: String?,
    val synchronizedAt: Long = System.currentTimeMillis()
) : Serializable

interface SynchronizableHealthData {
    val metadata: HealthPlatformMetadata?
    val isExternal: Boolean get() = metadata?.source != null && metadata?.source != IntegrationSource.MANUAL
}

// --- 4. DATA VALIDATION CONTEXT ---

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult() {
        val message: String get() = errors.joinToString(", ")
    }
}
