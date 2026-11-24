package com.example.ui.viewmodel

import com.example.data.model.UserProfileData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileGateAndSetupTest {

    @Test
    fun profileGateTransitionsFromSetupNeededToReadyBasedOnStoredProfile() = runTest {
        assertEquals(ProfileGate.SetupNeeded, resolveProfileGate(null))
        assertEquals(ProfileGate.SetupNeeded, resolveProfileGate(UserProfileData(nome = "")))
        assertEquals(ProfileGate.SetupNeeded, resolveProfileGate(UserProfileData(nome = "   ")))

        val savedProfile = UserProfileData(id = "1", nome = "Carla Mendes", idade = 29, altura = 166, peso = 60.5f)
        assertEquals(ProfileGate.Ready, resolveProfileGate(savedProfile))

        // Simula fluxo reativo do Room (instalação limpa -> cadastro ou restauração de backup)
        val fakeRoomFlow = MutableStateFlow<UserProfileData?>(null)
        val gateFlow = fakeRoomFlow.map(::resolveProfileGate)
        assertEquals(ProfileGate.SetupNeeded, gateFlow.first())

        fakeRoomFlow.value = savedProfile
        assertEquals(ProfileGate.Ready, gateFlow.first())
    }

    @Test
    fun buildInitialUserProfileComputesBmiUnitsAndNeverInjectsThirdPartyData() {
        assertNull(
            buildInitialUserProfile(
                name = "   ",
                gender = "Masculino",
                age = 30,
                heightCm = 175,
                weightKg = 70f,
                weeklyGoal = 4
            )
        )

        val profile = buildInitialUserProfile(
            name = "  Lucas Oliveira  ",
            gender = "Masculino",
            age = 31,
            heightCm = 175,
            weightKg = 70.0f,
            weeklyGoal = 4
        )
        assertNotNull(profile)
        profile!!

        assertEquals("1", profile.id)
        assertEquals("Lucas Oliveira", profile.nome)
        assertEquals("Masculino", profile.sexo)
        assertEquals(31, profile.idade)
        assertEquals(175, profile.altura)
        assertEquals(175, profile.heightCm)
        assertEquals(70.0f, profile.peso, 0.001f)
        assertEquals(70.0f, profile.weightKg ?: 0f, 0.001f)
        assertEquals(4, profile.metaSemanal)

        val expectedImc = 70.0f / (1.75f * 1.75f)
        assertEquals(expectedImc, profile.imc, 0.01f)

        // Garante ausência de dados hardcoded de terceiros (calibrações pessoais / metas / bioimpedância do autor)
        assertFalse(profile.nome.contains("Esnaider", ignoreCase = true))
        assertFalse(profile.nome.contains("Alyne", ignoreCase = true))
        assertNull(profile.fcMaxCalibradaBpm)
        assertNull(profile.limiarLt1Bpm)
        assertNull(profile.limiarLt2Bpm)
        assertNull(profile.metaPercentualGordura)
        assertNull(profile.metaPercentualMusculo)
        assertNull(profile.metaAguaLitros)
        assertNull(profile.baselinePaceMinPerKm)
        assertEquals(0f, profile.percentualGordura, 0.001f)
        assertEquals(0f, profile.massaMagra, 0.001f)
    }

    @Test
    fun weeklyGoalAlwaysStaysWithinOneToSevenDays() {
        val inputs = listOf(-10, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 74, 999)
        for (raw in inputs) {
            val normalized = normalizeWeeklyGoalDays(raw)
            assertTrue("Valor $raw fora da faixa 1..7: $normalized", normalized in 1..7)
        }
        assertEquals(1, normalizeWeeklyGoalDays(0))
        assertEquals(1, normalizeWeeklyGoalDays(-5))
        assertEquals(7, normalizeWeeklyGoalDays(74))
        assertEquals(7, normalizeWeeklyGoalDays(8))
        assertEquals(5, normalizeWeeklyGoalDays(5))

        val lowGoalProfile = buildInitialUserProfile("Maria", "Feminino", 28, 165, 58f, weeklyGoal = 0)
        val highGoalProfile = buildInitialUserProfile("Maria", "Feminino", 28, 165, 58f, weeklyGoal = 74)
        assertEquals(1, lowGoalProfile?.metaSemanal)
        assertEquals(7, highGoalProfile?.metaSemanal)
    }
}
