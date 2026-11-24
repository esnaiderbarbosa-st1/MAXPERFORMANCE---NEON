package com.example.services.ai

import com.example.data.model.GpsTrackPoint
import com.example.data.model.RunningSession
import com.example.data.model.SessionExecutionAudit
import com.example.data.model.UserProfileData
import com.example.data.model.calculateSprintAnalytics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPhysiologistReportFixesTest {

    @Test
    fun hr160WithMaxHr190And196_isNeverClassifiedAs100PercentZ5() {
        val historicalPeakSession = RunningSession(
            id = "hist_peak_192",
            date = 1_730_000_000_000L,
            durationSec = 1800,
            distanceMeters = 4500,
            avgPaceSecPerKm = 400,
            avgHr = 165,
            maxHr = 192
        )

        // Caso A: sem curva de FC (ou com apenas média/pico 160 bpm) + FCmáx calibrada em 190 bpm
        val sessionNoCurve = RunningSession(
            id = "free_run_no_curve_160",
            date = 1_730_100_000_000L,
            durationSec = 2352,
            distanceMeters = 4930,
            avgPaceSecPerKm = 477,
            avgHr = 160,
            maxHr = 160,
            hrTimeline = null,
            sessionType = "FREE_RUN",
            weekNumber = 0
        )
        val profile190 = UserProfileData(
            nome = "Atleta Calibrado",
            idade = 50,
            peso = 84f,
            fcMaxCalibradaBpm = 190
        )

        val report190NoCurve = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = sessionNoCurve,
            allRunningSessions = listOf(historicalPeakSession, sessionNoCurve),
            recentGymSessions = emptyList(),
            userProfile = profile190,
            sprintAnalytics = calculateSprintAnalytics(sessionNoCurve),
            splits = emptyList(),
            gpsPoints = emptyList(),
            checkIn = PreAnalysisCheckIn(
                effortPerception = EffortPerception.ESFORCO_CONTROLADO,
                hrSource = HrSourceType.SMARTWATCH
            )
        )

        assertTrue(
            "Deve usar a FCmáx calibrada do perfil (190 bpm) em vez de 196 bpm",
            report190NoCurve.contains("FCmáx real calibrada em 190 bpm")
        )
        assertTrue(
            "Sem curva suficiente de FC, deve informar que não há amostras suficientes para zonas",
            report190NoCurve.contains("Sem amostras suficientes para distribuição em zonas")
        )
        assertFalse(
            "160 bpm jamais pode ser classificada como 100% em Z5",
            report190NoCurve.contains("Z5 (VO2máx/Pico): 100%")
        )

        // Caso B: com curva real em torno de 160 bpm e FCmáx calibrada em 190 bpm e em 196 bpm
        val sessionWithCurve = sessionNoCurve.copy(
            id = "free_run_curve_160",
            hrTimeline = "155,158,160,162,160,159,161,158"
        )
        val profile196 = profile190.copy(fcMaxCalibradaBpm = 196)

        listOf(profile190, profile196).forEach { profile ->
            val report = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
                context = null,
                session = sessionWithCurve,
                allRunningSessions = listOf(historicalPeakSession, sessionWithCurve),
                recentGymSessions = emptyList(),
                userProfile = profile,
                sprintAnalytics = calculateSprintAnalytics(sessionWithCurve),
                splits = emptyList(),
                gpsPoints = emptyList(),
                checkIn = PreAnalysisCheckIn(
                    effortPerception = EffortPerception.ESFORCO_CONTROLADO,
                    hrSource = HrSourceType.SMARTWATCH
                )
            )

            assertFalse(
                "FC ~160 bpm com FCmáx ${profile.fcMaxCalibradaBpm} nunca pode dar Z5 = 100%",
                report.contains("Z5 (VO2máx/Pico): 100%")
            )
            assertTrue(
                "FC ~160 bpm com FCmáx ${profile.fcMaxCalibradaBpm} deve ter Z5 = 0%",
                report.contains("Z5 (VO2máx/Pico): 0%")
            )
        }
    }

    @Test
    fun freeRunSession_neverGeneratesPrescrito10MinCorrida() {
        // Mesmo que haja um executionAudit legado com 10 min prescritos, Corrida Livre não pode exibir prescrição fixa
        val corruptedLegacyAudit = SessionExecutionAudit(
            prescribedStructureLabel = "Semana 1 • Dia 1",
            plannedWorkDurationSec = 600,
            actualWorkRunningSec = 1800,
            unplannedWalkDurationSec = 180
        )
        val freeRunSession = RunningSession(
            id = "free_run_audit_test",
            date = 1_730_200_000_000L,
            durationSec = 2352,
            distanceMeters = 4930,
            avgPaceSecPerKm = 477,
            sessionType = "FREE_RUN",
            weekNumber = 0,
            executionAudit = corruptedLegacyAudit
        )

        val report = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = freeRunSession,
            allRunningSessions = listOf(freeRunSession),
            recentGymSessions = emptyList(),
            userProfile = UserProfileData(nome = "Atleta", idade = 40, fcMaxCalibradaBpm = 190),
            sprintAnalytics = calculateSprintAnalytics(freeRunSession),
            splits = emptyList(),
            gpsPoints = emptyList(),
            checkIn = PreAnalysisCheckIn(effortPerception = EffortPerception.ESFORCO_CONTROLADO)
        )

        assertTrue(
            "Corrida livre deve indicar claramente que não tem prescrição fixa",
            report.contains("Sessão livre (sem prescrição fixa)")
        )
        assertFalse(
            "Corrida livre nunca deve emitir 'Prescrito 10 min corrida'",
            report.contains("Prescrito 10 min")
        )
        assertFalse(
            "Corrida livre não deve emitir texto de 'Auditoria Planejado vs. Executado'",
            report.contains("Auditoria Planejado vs. Executado")
        )
    }

    @Test
    fun zeroCadenceAndStride_neverLeakAs0PpmOr0CmInReport() {
        // 1) Sessão contínua sem GPS e sem cadência/passada medida
        val continuousNoWalkSession = RunningSession(
            id = "free_run_no_cadence_no_walk",
            date = 1_730_300_000_000L,
            durationSec = 1800,
            distanceMeters = 3600,
            avgPaceSecPerKm = 500,
            sessionType = "FREE_RUN",
            weekNumber = 0
        )
        val reportNoWalk = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = continuousNoWalkSession,
            allRunningSessions = listOf(continuousNoWalkSession),
            recentGymSessions = emptyList(),
            userProfile = null,
            sprintAnalytics = calculateSprintAnalytics(continuousNoWalkSession),
            splits = emptyList(),
            gpsPoints = emptyList(),
            checkIn = PreAnalysisCheckIn(effortPerception = EffortPerception.ESFORCO_CONTROLADO)
        )

        assertFalse("Relatório sem caminhada não pode vazar '0 ppm'", reportNoWalk.contains("0 ppm"))
        assertFalse("Relatório sem caminhada não pode vazar '0 cm'", reportNoWalk.contains("0 cm"))

        // 2) Sessão contínua com trechos de corrida + caminhada via GPS e sem sensor de cadência
        val baseTime = 1_730_350_000_000L
        val gpsPoints = buildList {
            // 100s correndo a ~3.0 m/s (~5'33"/km) -> ~0.000027 deg lat por segundo
            var lat = -23.5500
            for (sec in 0..100 step 5) {
                add(
                    GpsTrackPoint(
                        sessionId = "free_run_walk_and_run",
                        timestampMs = baseTime + sec * 1000L,
                        capturedAtMs = baseTime + sec * 1000L,
                        latitude = lat,
                        longitude = -46.6300,
                        accuracyM = 5f,
                        speedMps = 3.0f
                    )
                )
                lat += 0.000135 // ~15m por 5s = 3.0 m/s
            }
            // 100s caminhando a ~1.2 m/s (~13'53"/km)
            for (sec in 105..200 step 5) {
                add(
                    GpsTrackPoint(
                        sessionId = "free_run_walk_and_run",
                        timestampMs = baseTime + sec * 1000L,
                        capturedAtMs = baseTime + sec * 1000L,
                        latitude = lat,
                        longitude = -46.6300,
                        accuracyM = 5f,
                        speedMps = 1.2f
                    )
                )
                lat += 0.000054 // ~6m por 5s = 1.2 m/s
            }
        }
        val sessionWithWalk = RunningSession(
            id = "free_run_walk_and_run",
            date = baseTime,
            durationSec = 200,
            distanceMeters = 420,
            avgPaceSecPerKm = 476,
            sessionType = "FREE_RUN",
            weekNumber = 0
        )
        val analyticsWithWalk = calculateSprintAnalytics(sessionWithWalk, gpsPoints)
        val reportWithWalk = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = sessionWithWalk,
            allRunningSessions = listOf(sessionWithWalk),
            recentGymSessions = emptyList(),
            userProfile = null,
            sprintAnalytics = analyticsWithWalk,
            splits = emptyList(),
            gpsPoints = gpsPoints,
            checkIn = PreAnalysisCheckIn(effortPerception = EffortPerception.ESFORCO_CONTROLADO)
        )

        assertFalse("Relatório com caminhada não pode vazar '0 ppm'", reportWithWalk.contains("0 ppm"))
        assertFalse("Relatório com caminhada não pode vazar '0 cm'", reportWithWalk.contains("0 cm"))
        assertFalse("Relatório não pode conter 'máx 0 ppm'", reportWithWalk.contains("máx 0 ppm"))
        assertFalse("Relatório não pode conter 'global 0 cm'", reportWithWalk.contains("global 0 cm"))
        // Também garante que Pace Médio Correndo (quando há caminhada) é distinto do Pace Global (476s/km = 7'56"/km)
        assertTrue(
            "Com segmentos reais de corrida (~3.0 m/s) e caminhada (~1.2 m/s), pace correndo deve ser mais rápido que o pace global",
            analyticsWithWalk.avgSprintPaceSecPerKm in 280..380 && analyticsWithWalk.avgSprintPaceSecPerKm != 476
        )
    }

    @Test
    fun sobrouReservaCheckIn_neverGenerates6Forte() {
        // Caso 1: sessão salva com o antigo default silencioso rpe = 6 / rpeCr10 = 6
        val sessionWithLegacyDefaultRpe = RunningSession(
            id = "free_run_rpe_default_6",
            date = 1_730_400_000_000L,
            durationSec = 2352,
            distanceMeters = 4930,
            avgPaceSecPerKm = 477,
            rpe = 6,
            rpeCr10 = 6,
            sRpeLoadUa = 235,
            sessionType = "FREE_RUN",
            weekNumber = 0
        )

        val reportFromDefault6 = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = sessionWithLegacyDefaultRpe,
            allRunningSessions = listOf(sessionWithLegacyDefaultRpe),
            recentGymSessions = emptyList(),
            userProfile = null,
            sprintAnalytics = calculateSprintAnalytics(sessionWithLegacyDefaultRpe),
            splits = emptyList(),
            gpsPoints = emptyList(),
            checkIn = PreAnalysisCheckIn(effortPerception = EffortPerception.SOBROU_RESERVA)
        )

        assertFalse(
            "'Sobrou reserva' jamais pode gerar '6 • Forte'",
            reportFromDefault6.contains("6 • Forte")
        )
        assertTrue(
            "'Sobrou reserva' deve conciliar o CR10 para esforço leve/moderado",
            reportFromDefault6.contains("3 • Leve a moderado")
        )

        // Caso 2: sessão salva sem RPE manual (rpe = 0, rpeCr10 = null)
        val sessionWithoutManualRpe = sessionWithLegacyDefaultRpe.copy(
            id = "free_run_rpe_unset",
            rpe = 0,
            rpeCr10 = null,
            sRpeLoadUa = null
        )

        val reportWithoutRpe = AiPhysiologistAnalyzer.generateLocalRunningPhysiologistReport(
            context = null,
            session = sessionWithoutManualRpe,
            allRunningSessions = listOf(sessionWithoutManualRpe),
            recentGymSessions = emptyList(),
            userProfile = null,
            sprintAnalytics = calculateSprintAnalytics(sessionWithoutManualRpe),
            splits = emptyList(),
            gpsPoints = emptyList(),
            checkIn = PreAnalysisCheckIn(effortPerception = EffortPerception.SOBROU_RESERVA)
        )

        assertFalse(
            "Sem RPE manual + 'Sobrou reserva' nunca deve gerar '6 • Forte'",
            reportWithoutRpe.contains("6 • Forte")
        )
        assertTrue(
            "Sem RPE manual + 'Sobrou reserva' deve mapear para '3 • Leve a moderado'",
            reportWithoutRpe.contains("3 • Leve a moderado")
        )
    }
}
