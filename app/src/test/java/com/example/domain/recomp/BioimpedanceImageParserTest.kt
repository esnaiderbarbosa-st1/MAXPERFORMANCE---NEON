package com.example.domain.recomp

import com.example.data.model.BioimpedanceRecord
import com.example.data.model.UserProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BioimpedanceImageParserTest {

    private fun allNumericMetrics(record: BioimpedanceRecord): List<Float> = listOf(
        record.weightKg,
        record.bmi,
        record.bodyFatPercent,
        record.muscleRatePercent,
        record.fatFreeMassKg,
        record.subcutaneousFatPercent,
        record.visceralFatIndex,
        record.bodyWaterPercent,
        record.skeletalMusclePercent,
        record.muscleMassKg,
        record.boneMassKg,
        record.proteinPercent,
        record.bmrKcal.toFloat(),
        record.metabolicAge.toFloat()
    )

    @Test
    fun ocrFailureForAlyne_returnsZeroFilledZeroWeightAndNoEsnaiderValues() {
        val alyne = UserProfileData.PRESET_ALYNE.copy(foto = "content://profile/alyne.jpg")
        val result = BioimpedanceImageParser.buildOcrFailureResult(
            profile = alyne,
            photoUri = "content://exam/alyne_report.jpg"
        )

        assertEquals(0, result.filledIndicatorsCount)
        assertEquals(BioimpedanceDetectedTemplate.NAO_DETECTADO, result.detectedTemplate)
        assertEquals(BioimpedanceImageParser.OCR_FAILURE_HEADLINE, result.statusHeadline)
        assertFalse(result.statusHeadline.contains("sucesso", ignoreCase = true))

        val rec = result.record
        assertEquals("Alyne", rec.athleteName)
        assertEquals("content://exam/alyne_report.jpg", rec.photoUri)
        assertEquals(0f, rec.weightKg, 0.001f)

        val metrics = allNumericMetrics(rec)
        metrics.forEach { value ->
            assertEquals(0f, value, 0.001f)
            assertNotEquals(84.7f, value, 0.01f)
            assertNotEquals(85.7f, value, 0.01f)
        }
    }

    @Test
    fun partialOcrText_onlyWeightAndBodyFat_leavesUnreadFieldsBlankWithoutCopyingFallbackBase() {
        val esnaiderFallback = BodyRecompEngine.createOfficialCheckIn3FitdaysRecord().copy(
            athleteName = "Alyne",
            photoUri = "content://exam/alyne_partial.jpg"
        )

        val partialText = """
            Peso: 72,0 kg
            Gordura corporal: 25,0%
        """.trimIndent()

        val result = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = partialText,
            fallbackBase = esnaiderFallback,
            profileHeightCm = null
        )

        val rec = result.record
        // Metadados preservados do fallbackBase
        assertEquals("Alyne", rec.athleteName)
        assertEquals("content://exam/alyne_partial.jpg", rec.photoUri)

        // Campos lidos
        assertEquals(72.0f, rec.weightKg, 0.05f)
        assertEquals(25.0f, rec.bodyFatPercent, 0.05f)

        // Campos não lidos permanecem em branco (0), sem copiar nada do fallbackBase do Esnaider
        assertEquals(0f, rec.bmi, 0.001f)
        assertEquals(0f, rec.muscleRatePercent, 0.001f)
        assertEquals(0f, rec.subcutaneousFatPercent, 0.001f)
        assertEquals(0f, rec.visceralFatIndex, 0.001f)
        assertEquals(0f, rec.bodyWaterPercent, 0.001f)
        assertEquals(0f, rec.skeletalMusclePercent, 0.001f)
        assertEquals(0f, rec.muscleMassKg, 0.001f)
        assertEquals(0f, rec.boneMassKg, 0.001f)
        assertEquals(0f, rec.proteinPercent, 0.001f)
        assertEquals(0, rec.bmrKcal)
        assertEquals(0, rec.metabolicAge)
        assertEquals("", rec.segmentalSummary)

        // Status honesto de leitura parcial (sem mensagem de sucesso)
        assertTrue(result.filledIndicatorsCount in 2..3)
        assertEquals(
            "${result.filledIndicatorsCount} de 14 indicadores lidos. Preencha os demais.",
            result.statusHeadline
        )
        assertFalse(result.statusHeadline.contains("sucesso", ignoreCase = true))
    }

    @Test
    fun fileNameImg85_doesNotAutoSelectTemplate() {
        val alyne = UserProfileData.PRESET_ALYNE
        val suspiciousNames = listOf(
            "IMG_85.jpg",
            "IMG_84.png",
            "relaxmedic_13_10.pdf",
            "fitdays_29_10.jpg"
        )

        suspiciousNames.forEach { fileName ->
            val result = BioimpedanceImageParser.analyzeAttachedImageWithoutOcr(
                fileName = fileName,
                photoUri = "content://downloads/$fileName",
                existingRecords = emptyList(),
                profile = alyne
            )
            assertEquals("Arquivo $fileName não deve selecionar template", BioimpedanceDetectedTemplate.NAO_DETECTADO, result.detectedTemplate)
            assertEquals("Arquivo $fileName deve retornar 0 indicadores", 0, result.filledIndicatorsCount)
            assertEquals("Arquivo $fileName deve retornar peso 0", 0f, result.record.weightKg, 0.001f)
            assertFalse(result.statusHeadline.contains("sucesso", ignoreCase = true))
            allNumericMetrics(result.record).forEach { value ->
                assertNotEquals(84.7f, value, 0.01f)
                assertNotEquals(85.7f, value, 0.01f)
            }
        }
    }

    @Test
    fun savedTemplates_onlyAvailableForProfilesWithPersonalGoals() {
        val profileWithoutGoals = UserProfileData(
            nome = "Esnaider",
            idade = 50,
            altura = 187,
            heightCm = 187,
            peso = 84.7f,
            weightKg = 84.7f,
            metaPercentualGordura = null,
            metaPercentualMusculo = null,
            metaAguaLitros = null
        )
        val profileWithCustomGoals = UserProfileData(
            nome = "Atleta Com Metas",
            idade = 34,
            altura = 175,
            heightCm = 175,
            peso = 75.0f,
            weightKg = 75.0f,
            metaPercentualGordura = 16.0f
        )
        val alyne = UserProfileData.PRESET_ALYNE

        assertTrue(BioimpedanceImageParser.availableTemplatesForProfile(alyne).isEmpty())
        assertTrue(BioimpedanceImageParser.availableTemplatesForProfile(profileWithoutGoals).isEmpty())
        assertTrue(BioimpedanceImageParser.availableTemplatesForProfile(null).isEmpty())

        val customTemplates = BioimpedanceImageParser.availableTemplatesForProfile(profileWithCustomGoals)
        assertTrue(customTemplates.contains(BioimpedanceDetectedTemplate.FITDAYS_29_10))
        assertTrue(customTemplates.contains(BioimpedanceDetectedTemplate.RELAXMEDIC_13_10))
        assertFalse(customTemplates.contains(BioimpedanceDetectedTemplate.NAO_DETECTADO))

        // Mesmo se buildResultForTemplate for chamado diretamente para perfil sem meta*, nunca retorna 84,7 ou 85,7
        val blankNoGoals = BodyRecompEngine.createBlankProfileRecord(profileWithoutGoals, includeProfileWeight = false)
        val blockedFitdays = BioimpedanceImageParser.buildResultForTemplate(
            template = BioimpedanceDetectedTemplate.FITDAYS_29_10,
            photoUri = null,
            fallbackBase = blankNoGoals,
            profile = profileWithoutGoals
        )
        assertEquals(BioimpedanceDetectedTemplate.NAO_DETECTADO, blockedFitdays.detectedTemplate)
        assertEquals(0, blockedFitdays.filledIndicatorsCount)
        assertEquals(0f, blockedFitdays.record.weightKg, 0.001f)

        val blockedRelax = BioimpedanceImageParser.buildResultForTemplate(
            template = BioimpedanceDetectedTemplate.RELAXMEDIC_13_10,
            photoUri = null,
            fallbackBase = blankNoGoals,
            profile = alyne
        )
        assertEquals(BioimpedanceDetectedTemplate.NAO_DETECTADO, blockedRelax.detectedTemplate)
        assertEquals(0, blockedRelax.filledIndicatorsCount)
        assertEquals(0f, blockedRelax.record.weightKg, 0.001f)

        // Para perfil com meta* configurada, seleção explícita de template importa leitura salva com rótulo claro
        val importedWithGoals = BioimpedanceImageParser.buildResultForTemplate(
            template = BioimpedanceDetectedTemplate.FITDAYS_29_10,
            photoUri = null,
            fallbackBase = blankNoGoals,
            profile = profileWithCustomGoals
        )
        assertEquals(84.7f, importedWithGoals.record.weightKg, 0.01f)
        assertTrue(importedWithGoals.statusHeadline.contains("Leitura salva", ignoreCase = true))
    }
}
