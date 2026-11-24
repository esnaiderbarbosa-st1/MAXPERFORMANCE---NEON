package com.example.services.domain

import com.example.data.model.*
import kotlin.math.abs

object HealthEngine {

    /**
     * Calculates Body Mass Index (BMI).
     */
    fun calculateBMI(weight: Float, heightCm: Int): Float {
        if (weight <= 0f || heightCm <= 0) return 0f
        val heightM = heightCm / 100f
        return weight / (heightM * heightM)
    }

    /**
     * Classifies BMI value into standard categories.
     */
    fun classifyBMI(bmi: Float): BmiClassification {
        return when {
            bmi < 18.5f -> BmiClassification.UNDERWEIGHT
            bmi < 25f -> BmiClassification.NORMAL
            bmi < 30f -> BmiClassification.OVERWEIGHT
            bmi < 35f -> BmiClassification.OBESITY_CLASS_1
            bmi < 40f -> BmiClassification.OBESITY_CLASS_2
            else -> BmiClassification.OBESITY_CLASS_3
        }
    }

    /**
     * Estimates Basal Metabolic Rate (BMR) using the Mifflin-St Jeor equation.
     */
    fun calculateBmr(weight: Float, heightCm: Int, age: Int, sex: String): Int {
        if (weight <= 0f || heightCm <= 0 || age <= 0) return 0
        val base = (10f * weight) + (6.25f * heightCm) - (5f * age)
        return if (sex.equals("Feminino", ignoreCase = true)) {
            (base - 161f).toInt()
        } else {
            (base + 5f).toInt()
        }
    }

    /**
     * Calculates target heart rate training zones based on the Gellish formula:
     * FCMax = 207 - (0.7 * Age)
     */
    fun calculateHeartRateZones(age: Int): Map<HeartRateZone, ClosedRange<Int>> {
        if (age !in 10..100) return emptyMap()
        val maxHr = 207f - (0.7f * age)
        return mapOf(
            HeartRateZone.REST to 0..60,
            HeartRateZone.WARM_UP to (maxHr * 0.5f).toInt()..(maxHr * 0.6f).toInt(),
            HeartRateZone.FAT_BURN to (maxHr * 0.6f).toInt()..(maxHr * 0.7f).toInt(),
            HeartRateZone.AEROBIC to (maxHr * 0.7f).toInt()..(maxHr * 0.8f).toInt(),
            HeartRateZone.ANAEROBIC to (maxHr * 0.8f).toInt()..(maxHr * 0.9f).toInt(),
            HeartRateZone.RED_LINE to (maxHr * 0.9f).toInt()..maxHr.toInt()
        )
    }

    /**
     * Checks physiological consistency of a bioimpedance record.
     * Ensures components sum up sensibly and water/magra mass ratios are biologically sound.
     */
    fun checkBiologicalConsistency(record: BioimpedanceRecord): Boolean {
        // 1. Weight ≈ Lean Mass + Fat Mass
        val fatMass = record.peso * (record.percentualGordura / 100f)
        val calculatedWeight = record.massaMagra + fatMass
        if (abs(calculatedWeight - record.peso) > 2.0f) {
            android.util.Log.w("HealthEngine", "Biological inconsistency: weight=$record.peso vs calculatedWeight=$calculatedWeight")
            return false
        }

        // 2. Absolute body water should be between 45% and 85% of lean mass
        val absoluteWater = record.peso * (record.aguaCorporal / 100f)
        if (record.massaMagra > 0f) {
            val ratio = absoluteWater / record.massaMagra
            if (ratio < 0.45f || ratio > 0.85f) {
                android.util.Log.w("HealthEngine", "Inconsistent body water to lean mass ratio: $ratio")
                return false
            }
        }
        return true
    }

    /**
     * Validates raw bioimpedance fields to make sure no corrupt OCR inputs slip through.
     */
    fun validateBioimpedanceRecord(record: BioimpedanceRecord): ValidationResult {
        val errors = mutableListOf<String>()

        if (record.peso !in 30f..300f) {
            errors.add("Peso fora da faixa realista (${record.peso} kg)")
        }
        if (record.percentualGordura !in 3f..60f) {
            errors.add("Percentual de gordura fora da faixa realista (${record.percentualGordura}%)")
        }
        if (record.aguaCorporal !in 15f..85f) {
            errors.add("Água corporal fora da faixa realista (${record.aguaCorporal}%)")
        }
        if (record.massaMagra !in 10f..250f) {
            errors.add("Massa magra fora da faixa realista (${record.massaMagra} kg)")
        }
        if (record.tmb !in 500..5000) {
            errors.add("Taxa metabólica basal (TMB) fora de faixa (${record.tmb} kcal)")
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Validates cardio session inputs to ensure clean metrics before DB logging.
     */
    fun validateCardioSession(session: CardioSession): ValidationResult {
        val errors = mutableListOf<String>()

        if (session.durationMinutes <= 0) {
            errors.add("Duração deve ser maior que zero minutos")
        }
        if (session.calories < 0) {
            errors.add("Calorias não podem ser negativas")
        }
        if (session.avgHr !in 0..250) {
            errors.add("Frequência cardíaca média inválida (${session.avgHr} bpm)")
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}
