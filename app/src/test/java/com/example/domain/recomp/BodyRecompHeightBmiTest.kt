package com.example.domain.recomp

import com.example.data.model.BioimpedanceRecord
import com.example.data.model.UserProfileData
import com.example.data.model.calculateAutoBmiOrNull
import com.example.data.model.resolveValidHeightCmOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BodyRecompHeightBmiTest {

    @Test
    fun weight72kgAndHeight155cm_computesBmiNear30_andEngineDoesNotOverwrite() {
        val alyneHeightCm = resolveValidHeightCmOrNull(UserProfileData.PRESET_ALYNE)
        assertEquals(155, alyneHeightCm)

        // 1. Auto-BMI calculation uses active profile height (72 / 1.55^2 = 29.97 ≈ 30.0)
        val autoBmi = calculateAutoBmiOrNull(weightKg = 72f, heightCm = alyneHeightCm)
        assertNotNull(autoBmi)
        assertEquals(30.0f, autoBmi!!, 0.1f)

        // Invalid or absent height does not invent an auto-BMI
        assertNull(calculateAutoBmiOrNull(weightKg = 72f, heightCm = null))
        assertNull(calculateAutoBmiOrNull(weightKg = 72f, heightCm = 90))
        assertNull(calculateAutoBmiOrNull(weightKg = 72f, heightCm = 240))

        // 2. Parser uses profile height when exam text omits "altura: NN cm"
        val fallback = BodyRecompEngine.createOfficialCheckIn3FitdaysRecord().copy(bmi = 0f)
        val parsedWithProfileHeight = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = "Peso: 72,0 kg",
            fallbackBase = fallback,
            profileHeightCm = 155f
        )
        assertEquals(30.0f, parsedWithProfileHeight.record.bmi, 0.1f)

        // Parser prioritizes "altura: NN cm" from the exam over profile height
        val parsedWithExamHeight = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = "Altura: 155 cm\nPeso: 72,0 kg",
            fallbackBase = fallback,
            profileHeightCm = 187f
        )
        assertEquals(30.0f, parsedWithExamHeight.record.bmi, 0.1f)

        // Parser does NOT invent BMI using 187 cm when neither exam nor profile provides a valid height
        val parsedWithoutAnyHeight = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = "Peso: 72,0 kg",
            fallbackBase = fallback,
            profileHeightCm = null
        )
        assertEquals(0f, parsedWithoutAnyHeight.record.bmi, 0.01f)

        // 3. BodyRecompEngine preserves record.bmi (does NOT overwrite 30.0 with 72 / 1.87^2 = 20.6)
        val alyneRecord = BioimpedanceRecord(
            id = "alyne_checkin_72kg",
            timestampMillis = 1_762_000_000_000L,
            dateTimeLabel = "01/11/2026 - 07:00",
            athleteName = "Alyne",
            sourceApp = "Fitdays Oficial",
            weightKg = 72.0f,
            bmi = 30.0f,
            bodyFatPercent = 25.0f,
            muscleRatePercent = 46.0f,
            fatFreeMassKg = 54.0f,
            subcutaneousFatPercent = 20.0f,
            visceralFatIndex = 7.0f,
            bodyWaterPercent = 54.0f,
            skeletalMusclePercent = 36.0f,
            muscleMassKg = 50.0f,
            boneMassKg = 2.6f,
            proteinPercent = 18.0f,
            bmrKcal = 1450,
            metabolicAge = 35
        )

        val migrated = BodyRecompEngine.migrateOrFixDuplicateBaselineRecords(
            loaded = listOf(alyneRecord),
            savedMigrationVersion = 4
        )
        assertEquals(1, migrated.size)
        assertEquals(30.0f, migrated.first().bmi, 0.01f)

        val timelineCards = BodyRecompEngine.buildRecompTimelineCards(migrated)
        assertEquals(30.0f, timelineCards.first().current.bmi, 0.01f)

        val bmiIndicator = BodyRecompEngine.evaluateIndicators(migrated.first()).first { it.titlePt == "IMC" }
        assertEquals("30,0", bmiIndicator.formattedValue)
    }
}
