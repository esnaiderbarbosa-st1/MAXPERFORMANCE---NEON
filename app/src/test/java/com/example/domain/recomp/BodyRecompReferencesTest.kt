package com.example.domain.recomp

import com.example.data.model.BioimpedanceRecord
import com.example.data.model.IndicatorStatus
import com.example.data.model.UserProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyRecompReferencesTest {

    private fun buildSampleRecord(
        athleteName: String,
        weightKg: Float,
        bmi: Float,
        bodyFatPercent: Float = 25.0f
    ): BioimpedanceRecord = BioimpedanceRecord(
        id = "rec_${athleteName.lowercase()}",
        timestampMillis = 1_762_000_000_000L,
        dateTimeLabel = "01/11/2026 - 07:00",
        athleteName = athleteName,
        sourceApp = "Fitdays Oficial",
        weightKg = weightKg,
        bmi = bmi,
        bodyFatPercent = bodyFatPercent,
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

    @Test
    fun alyneProfile_usesHeightDerivedHealthyWeight_andShowsNoEsnaiderReferences() {
        val alyne = UserProfileData.PRESET_ALYNE // feminino, 155 cm, 72 kg
        assertFalse(BioimpedanceReferences.hasPersonalGoals(alyne))

        val weightRange = BioimpedanceReferences.healthyWeightRangeKg(alyne.heightCm)
        assertNotNull(weightRange)
        assertEquals(44.4f, weightRange!!.start, 0.1f)
        assertEquals(59.8f, weightRange.endInclusive, 0.1f)

        val record = buildSampleRecord(athleteName = "Alyne", weightKg = 72.0f, bmi = 30.0f)
        val assessments = BodyRecompEngine.evaluateIndicators(record, alyne)
        assertEquals(14, assessments.size)

        val allRefTexts = assessments.joinToString(" | ") { it.referenceTargetText }
        assertFalse("Não deve conter referência 65,0 do Esnaider", allRefTexts.contains("65,0"))
        assertFalse("Não deve conter referência 18,0% do Esnaider", allRefTexts.contains("18,0%"))
        assertFalse("Não deve conter referência 1750 do Esnaider", allRefTexts.contains("1750"))
        assertFalse("Não deve conter faixa 65,4 – 88,5 do Esnaider", allRefTexts.contains("65,4") || allRefTexts.contains("88,5"))

        val weightItem = assessments.first { it.indexNumber == 1 }
        assertEquals("Ref: 44,4 – 59,8 kg", weightItem.referenceTargetText)
        assertEquals(IndicatorStatus.ALTO, weightItem.status)

        val bmiItem = assessments.first { it.indexNumber == 2 }
        assertEquals("Normal: 18,5 – 24,9", bmiItem.referenceTargetText)
        assertEquals(IndicatorStatus.ALTO, bmiItem.status) // 30,0 = obesidade pela OMS
        assertEquals("Obesidade", BioimpedanceReferences.bmiBandLabel(30.0f))

        // Demais 12 indicadores: apenas valor, "Sem referência" e status neutro
        val otherIndicators = assessments.filter { it.indexNumber >= 3 }
        assertEquals(12, otherIndicators.size)
        otherIndicators.forEach { item ->
            assertEquals("Indicador ${item.titlePt} deve mostrar 'Sem referência'", "Sem referência", item.referenceTargetText)
            assertEquals("Indicador ${item.titlePt} deve ter status NEUTRO", IndicatorStatus.NEUTRO, item.status)
        }

        // Sem seed oficial nem fallback com dados do Esnaider para Alyne
        assertTrue(BodyRecompEngine.buildRecompTimelineCards(emptyList(), alyne).isEmpty())
        assertTrue(BodyRecompEngine.migrateOrFixDuplicateBaselineRecords(emptyList(), 4, alyne).isEmpty())
        val blankFallback = BodyRecompEngine.defaultFallbackRecord(alyne)
        assertEquals("Alyne", blankFallback.athleteName)
        assertEquals(72.0f, blankFallback.weightKg, 0.01f)
        assertEquals(0f, blankFallback.bodyFatPercent, 0.01f)
        val blankIndicators = BodyRecompEngine.evaluateIndicators(blankFallback, alyne)
        assertEquals("--", blankIndicators.first { it.indexNumber == 3 }.formattedValue)
    }

    @Test
    fun profileWithoutValidHeight_hasNoWeightReferenceRange() {
        val noHeightProfile = UserProfileData(
            nome = "Atleta Teste",
            sexo = "Feminino",
            idade = 30,
            altura = 0,
            heightCm = null,
            peso = 65f,
            weightKg = 65f
        )

        assertNull(BioimpedanceReferences.healthyWeightRangeKg(noHeightProfile.heightCm))
        assertNull(BioimpedanceReferences.healthyWeightRangeKg(90))
        assertNull(BioimpedanceReferences.healthyWeightRangeKg(240))

        val record = buildSampleRecord(athleteName = "Atleta Teste", weightKg = 65f, bmi = 22.5f)
        val assessments = BodyRecompEngine.evaluateIndicators(record, noHeightProfile)
        val weightItem = assessments.first { it.indexNumber == 1 }
        assertEquals("Sem referência", weightItem.referenceTargetText)
        assertEquals(IndicatorStatus.NEUTRO, weightItem.status)

        // IMC ainda usa faixas da OMS
        val bmiItem = assessments.first { it.indexNumber == 2 }
        assertEquals("Normal: 18,5 – 24,9", bmiItem.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, bmiItem.status)
        assertEquals("Normal", BioimpedanceReferences.bmiBandLabel(22.5f))
    }

    @Test
    fun esnaiderProfile_preservesCurrentTextsStatusesAndGoalBars() {
        val esnaider = UserProfileData.PRESET_ESNAIDER
        assertTrue(BioimpedanceReferences.hasPersonalGoals(esnaider))

        val record = BodyRecompEngine.createOfficialCheckIn3FitdaysRecord() // 84.7 kg, BMI 24.5, BF 23.0%
        val baseline = BodyRecompEngine.createOfficialBaselineRecord()
        val assessments = BodyRecompEngine.evaluateIndicators(record, esnaider).associateBy { it.indexNumber }

        assertEquals(IndicatorStatus.SAUDAVEL, assessments[1]!!.status)
        assertEquals("Normal: 18,5 – 24,9", assessments[2]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[2]!!.status)
        assertEquals("Meta < 18,0%", assessments[3]!!.referenceTargetText)
        assertEquals(IndicatorStatus.ALTO, assessments[3]!!.status)
        assertEquals("Ideal > 74,0%", assessments[4]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[4]!!.status)
        assertEquals("Preservar >= 65,0 kg", assessments[5]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[5]!!.status)
        assertEquals("Ideal: 8,6 – 16,7%", assessments[6]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[6]!!.status)
        assertEquals("Saudável <= 9", assessments[7]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO_RISCO, assessments[7]!!.status)
        assertEquals("Meta >= 55,0%", assessments[8]!!.referenceTargetText)
        assertEquals(IndicatorStatus.DESIDRATADO, assessments[8]!!.status)
        assertEquals("Meta >= 45,0%", assessments[9]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[9]!!.status)
        assertEquals("Ref > 60,0 kg", assessments[10]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[10]!!.status)
        assertEquals("Normal: 3,1 – 3,7 kg", assessments[11]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[11]!!.status)
        assertEquals("Ideal >= 16,0%", assessments[12]!!.referenceTargetText)
        assertEquals(IndicatorStatus.EXCELENTE, assessments[12]!!.status)
        assertEquals("Faixa: 1750+ kcal", assessments[13]!!.referenceTargetText)
        assertEquals(IndicatorStatus.MEDIO, assessments[13]!!.status)
        assertEquals("Menor que 50 anos", assessments[14]!!.referenceTargetText)
        assertEquals(IndicatorStatus.EXCELENTE, assessments[14]!!.status)

        val goals = BodyRecompEngine.buildPhysiologicalGoalBars(record, esnaider, baseline)
        assertEquals(4, goals.size)
        goals.forEach { bar ->
            assertTrue("Progresso deve ficar entre 0.08 e 1.0", bar.progressFraction in 0.08f..1.0f)
        }
    }

    @Test
    fun goalProgressPanel_isEmptyForProfilesWithoutPersonalGoals() {
        val alyne = UserProfileData.PRESET_ALYNE
        val record = buildSampleRecord(athleteName = "Alyne", weightKg = 72.0f, bmi = 30.0f)

        val alyneGoals = BodyRecompEngine.buildPhysiologicalGoalBars(record, alyne)
        assertTrue("Painel de metas deve ser vazio para Alyne", alyneGoals.isEmpty())

        val nullProfileGoals = BodyRecompEngine.buildPhysiologicalGoalBars(record, null)
        assertTrue("Painel de metas deve ser vazio quando perfil é null", nullProfileGoals.isEmpty())
    }
}
