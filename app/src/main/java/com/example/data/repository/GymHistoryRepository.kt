package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.GymWorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

object GymHistoryRepository {
    private const val PREFS_NAME = "max_performance_gym_history"
    private const val KEY_SESSIONS_JSON = "gym_sessions_v1"

    private val _sessions = MutableStateFlow<List<GymWorkoutSession>>(emptyList())
    val sessions: StateFlow<List<GymWorkoutSession>> = _sessions.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSessions()
        }
    }

    private const val KEY_SEED_2026_10_30_APPLIED = "gym_seed_2026_10_30_v1"
    private const val KEY_SEED_2026_10_31_APPLIED = "gym_seed_2026_10_31_v1"
    const val OFFICIAL_FIELD_SESSION_2026_10_30_ID = "gym_seed_2026_10_30_full_body_b"
    const val OFFICIAL_FIELD_SESSION_2026_10_31_ID = "gym_seed_2026_10_31_full_body_c"

    fun buildFieldTimestamp2026Oct30(hour: Int = 18, minute: Int = 9): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, 2026)
        cal.set(java.util.Calendar.MONTH, java.util.Calendar.OCTOBER)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 30)
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun buildFieldTimestamp2026Oct31(hour: Int = 16, minute: Int = 28): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, 2026)
        cal.set(java.util.Calendar.MONTH, java.util.Calendar.OCTOBER)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 31)
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun createOfficial30Oct2026FieldSession(): GymWorkoutSession {
        val ts = buildFieldTimestamp2026Oct30(18, 9)
        return GymWorkoutSession(
            id = OFFICIAL_FIELD_SESSION_2026_10_30_ID,
            date = ts,
            workoutName = "Full Body B",
            durationSeconds = 3180, // 53 min
            totalVolumeKg = 11700.0f,
            totalSets = 22,
            completedSets = 22,
            notes = "Treino de campo consolidado em 30/10/2026 (18:09) • Atleta Esnaider (84,7 kg) • Inclusão definitiva das máquinas Supino Declinado Máquina Total Health (50 kg) e Puxada Alta Máquina Anilhas (60 kg).",
            exercises = listOf(
                com.example.data.model.GymExerciseRecord(
                    name = "Puxada Alta na Polia",
                    equipment = "Polia / Torre de Placas",
                    category = "Costas",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 45.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 52.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 52.0f, 12, true),
                        com.example.data.model.GymSetRecord(4, 52.0f, 12, true)
                    ),
                    notes = "Carga base consolidada em 52 kg (4 séries concluídas)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Supino Declinado Máquina Total Health",
                    equipment = "Máquina Articulada / Anilhas",
                    category = "Peito",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 50.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 50.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 50.0f, 12, true)
                    ),
                    notes = "Nova máquina articulada Total Health cadastrada em campo • 50 kg consolidado (3x 12 reps)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Puxada Alta Máquina Anilhas",
                    equipment = "Máquina Articulada / Anilhas",
                    category = "Costas",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 40.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 60.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 60.0f, 12, true)
                    ),
                    notes = "Nova máquina articulada de anilhas cadastrada em campo • Progressão de 40 kg para 60 kg consolidado (12 reps)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Elevação Lateral com Halteres",
                    equipment = "Halteres",
                    category = "Ombros",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 10.0f, 15, true),
                        com.example.data.model.GymSetRecord(2, 10.0f, 15, true),
                        com.example.data.model.GymSetRecord(3, 10.0f, 15, true)
                    ),
                    notes = "10 kg cada halter • 3x 15 reps cada lado."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Cadeira Extensora",
                    equipment = "Máquina",
                    category = "Pernas",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 75.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 61.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 61.0f, 12, true)
                    ),
                    notes = "Pico de 75 kg na S1 e carga consolidada de 61 kg nas séries 2 e 3 (12 reps)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Cadeira Flexora",
                    equipment = "Máquina",
                    category = "Pernas",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 36.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 36.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 36.0f, 12, true)
                    ),
                    notes = "Carga consolidada em 36 kg (3x 12 reps)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Rosca Martelo com Halteres",
                    equipment = "Halteres",
                    category = "Bíceps",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 14.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 14.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 14.0f, 12, true)
                    ),
                    notes = "14 kg cada halter • 3x 12 reps cada lado."
                )
            )
        )
    }

    fun createOfficial31Oct2026FieldSession(): GymWorkoutSession {
        val ts = buildFieldTimestamp2026Oct31(16, 28)
        return GymWorkoutSession(
            id = OFFICIAL_FIELD_SESSION_2026_10_31_ID,
            date = ts,
            workoutName = "Full Body C - Recalculated Split",
            durationSeconds = 3158, // 52:38
            totalVolumeKg = 10566.4f,
            totalSets = 22,
            completedSets = 22,
            notes = "Treino de campo consolidado em 31/10/2026 (16:28) • Atleta Esnaider (84,7 kg) • Duração 52:38 • 22/22 séries concluídas • Volume Total: 10.566,4 kg (Volume específico de pernas: 6.651 kg).",
            exercises = listOf(
                com.example.data.model.GymExerciseRecord(
                    name = "Terra Romeno com Barra (RDL)",
                    equipment = "Barra / Smith",
                    category = "Posterior de Coxa",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 50.0f, 10, true),
                        com.example.data.model.GymSetRecord(2, 50.0f, 10, true),
                        com.example.data.model.GymSetRecord(3, 60.0f, 10, true),
                        com.example.data.model.GymSetRecord(4, 60.0f, 10, true)
                    ),
                    notes = "Progressão consistente de 50 kg para 60 kg (4x10 = 2.200 kg)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Standing Hip Thrust",
                    equipment = "Máquina Articulada / Anilhas",
                    category = "Glúteos",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 50.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 59.1f, 12, true),
                        com.example.data.model.GymSetRecord(3, 63.6f, 12, true)
                    ),
                    notes = "Anilhas em libras convertidas com precisão de 1 casa decimal: 50 kg -> 59,1 kg -> 63,6 kg (2.072,4 kg)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Leg Press Horizontal",
                    equipment = "Máquina",
                    category = "Quadríceps",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 75.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 75.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 75.0f, 12, true)
                    ),
                    notes = "Carga consolidada em 75 kg (3x12 = 2.700 kg)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Supino Inclinado com Halteres",
                    equipment = "Halteres",
                    category = "Peito",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 22.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 22.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 22.0f, 12, true)
                    ),
                    notes = "22 kg cada halter x 12 reps cada lado • Volume bilateral do par: 528 kg/série (1.584 kg total)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Remada Curvada com Barra",
                    equipment = "Barra / Smith",
                    category = "Costas",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 45.0f, 10, true),
                        com.example.data.model.GymSetRecord(2, 45.0f, 10, true),
                        com.example.data.model.GymSetRecord(3, 45.0f, 10, true)
                    ),
                    notes = "Carga consolidada em 45 kg (3x10 = 1.350 kg) • Substituída por Remada na Máquina (Torre de Placas) nos próximos ciclos Full Body para blindagem lombar/SNC."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Desenvolvimento na Máquina",
                    equipment = "Máquina",
                    category = "Ombros",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 15.0f, 12, true),
                        com.example.data.model.GymSetRecord(2, 20.0f, 12, true),
                        com.example.data.model.GymSetRecord(3, 20.0f, 12, true)
                    ),
                    notes = "Progressão de 15 kg na S1 para 20 kg consolidado nas séries 2 e 3 (660 kg)."
                ),
                com.example.data.model.GymExerciseRecord(
                    name = "Abdominal Infra Solo",
                    equipment = "Peso Corporal",
                    category = "Abdominais",
                    sets = listOf(
                        com.example.data.model.GymSetRecord(1, 0.0f, 15, true),
                        com.example.data.model.GymSetRecord(2, 0.0f, 15, true),
                        com.example.data.model.GymSetRecord(3, 0.0f, 15, true)
                    ),
                    notes = "Peso Corporal • 3x 15 reps concluídas."
                )
            )
        )
    }

    private fun loadSessions() {
        val jsonStr = prefs?.getString(KEY_SESSIONS_JSON, null) ?: "[]"
        val seed30Applied = prefs?.getBoolean(KEY_SEED_2026_10_30_APPLIED, false) ?: false
        val seed31Applied = prefs?.getBoolean(KEY_SEED_2026_10_31_APPLIED, false) ?: false
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<GymWorkoutSession>()
            for (i in 0 until arr.length()) {
                val s = GymWorkoutSession.fromJson(arr.getString(i))
                if (s != null) {
                    list.add(s)
                }
            }
            var modified = false
            if (!seed30Applied || list.none { it.id == OFFICIAL_FIELD_SESSION_2026_10_30_ID }) {
                list.removeAll { it.id == OFFICIAL_FIELD_SESSION_2026_10_30_ID }
                list.add(createOfficial30Oct2026FieldSession())
                modified = true
            }
            if (!seed31Applied || list.none { it.id == OFFICIAL_FIELD_SESSION_2026_10_31_ID }) {
                list.removeAll { it.id == OFFICIAL_FIELD_SESSION_2026_10_31_ID }
                list.add(createOfficial31Oct2026FieldSession())
                modified = true
            }
            _sessions.value = list.sortedByDescending { it.date }
            if (modified) {
                prefs?.edit()
                    ?.putBoolean(KEY_SEED_2026_10_30_APPLIED, true)
                    ?.putBoolean(KEY_SEED_2026_10_31_APPLIED, true)
                    ?.apply()
                persistToDisk()
            }
        } catch (e: Exception) {
            val fallback = listOf(
                createOfficial31Oct2026FieldSession(),
                createOfficial30Oct2026FieldSession()
            )
            _sessions.value = fallback
            prefs?.edit()
                ?.putBoolean(KEY_SEED_2026_10_30_APPLIED, true)
                ?.putBoolean(KEY_SEED_2026_10_31_APPLIED, true)
                ?.apply()
            persistToDisk()
        }
    }

    fun saveSession(context: Context, session: GymWorkoutSession) {
        init(context)
        val current = _sessions.value.toMutableList()
        current.removeAll { it.id == session.id }
        current.add(0, session)
        _sessions.value = current.sortedByDescending { it.date }

        persistToDisk()
    }

    fun deleteSession(context: Context, sessionId: String) {
        init(context)
        val current = _sessions.value.toMutableList()
        current.removeAll { it.id == sessionId }
        _sessions.value = current.sortedByDescending { it.date }

        persistToDisk()
    }

    fun clearAllSessions(context: Context) {
        init(context)
        _sessions.value = emptyList()
        prefs?.edit()?.remove(KEY_SESSIONS_JSON)?.apply()
    }

    private fun persistToDisk() {
        val arr = JSONArray()
        _sessions.value.forEach { session ->
            arr.put(session.toJson())
        }
        prefs?.edit()?.putString(KEY_SESSIONS_JSON, arr.toString())?.apply()
    }

    fun exportToTextReport(
        session: GymWorkoutSession,
        athleteName: String = "Atleta",
        athleteWeightKg: Float? = null,
        gymName: String = ""
    ): String {
        val ptBr = java.util.Locale("pt", "BR")
        val dateFmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr)
        val dateStr = dateFmt.format(java.util.Date(session.date))
        val durationMins = session.durationSeconds / 60
        val durationSecs = session.durationSeconds % 60
        val durationStr = String.format(ptBr, "%02d:%02d", durationMins, durationSecs)

        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("MAX GYM - RELATÓRIO DE CAMPO & MUSCULAÇÃO")
        sb.appendLine("==========================================")
        sb.appendLine("Data e Hora: $dateStr")
        val weightInfo = if (athleteWeightKg != null && athleteWeightKg > 0f) " (${String.format(ptBr, "%.1f", athleteWeightKg)} kg)" else ""
        sb.appendLine("Perfil / Atleta: ${athleteName.ifBlank { "Atleta" }}$weightInfo")
        if (gymName.isNotBlank()) {
            sb.appendLine("Local / Academia: $gymName")
        }
        sb.appendLine("Ficha: ${session.workoutName}")
        sb.appendLine("Duração Total: $durationStr")
        sb.appendLine("Volume Total: ${String.format(ptBr, "%.1f", session.totalVolumeKg)} kg")
        sb.appendLine("Total de Séries Concluídas: ${session.completedSets}/${session.totalSets}")
        if (session.notes.isNotBlank()) {
            sb.appendLine("Resumo de Feedbacks: ${session.notes}")
        }

        val executedExercises = session.exercises.filter { ex -> ex.sets.any { it.completed } }

        // Destaques de Cargas Máximas (exclusivamente exercícios efetivamente executados)
        val maxHighlights = executedExercises.mapNotNull { ex ->
            val maxSet = ex.sets.filter { it.completed }.maxByOrNull { it.weight }
            if (maxSet != null && maxSet.weight > 0f) {
                Triple(ex.name, maxSet.weight, maxSet.reps)
            } else null
        }.sortedByDescending { it.second }.take(5)

        if (maxHighlights.isNotEmpty()) {
            sb.appendLine("------------------------------------------")
            sb.appendLine("DESTAQUES DE CARGAS MÁXIMAS:")
            sb.appendLine("------------------------------------------")
            maxHighlights.forEach { (exName, maxW, reps) ->
                val wStr = com.example.utils.WeightCalculator.formatStepNumber(maxW)
                sb.appendLine("★ $exName: $wStr kg ($reps reps)")
            }
        }

        sb.appendLine("------------------------------------------")
        sb.appendLine("EXERCÍCIOS, SÉRIES, CARGAS E FEEDBACKS:")
        sb.appendLine("------------------------------------------")

        executedExercises.forEachIndexed { index, ex ->
            val num = index + 1
            sb.appendLine("$num. ${ex.name.uppercase()} [${ex.equipment}]")
            val setsToPrint = ex.sets.filter { it.completed }
            if (setsToPrint.isEmpty()) {
                sb.appendLine("   - Nenhuma série registrada")
            } else {
                val isBw = com.example.utils.WeightCalculator.isBodyweightExercise(null, ex.name, ex.equipment)
                val isPerDumbbell = com.example.utils.WeightCalculator.isPerDumbbellWeight(null, ex.name, ex.equipment)
                val isPerSide = com.example.utils.WeightCalculator.isEachSideReps(null, ex.name, ex.equipment)
                setsToPrint.forEach { s ->
                    if (isBw && s.weight <= 0.05f) {
                        sb.appendLine("   • Série ${s.setIndex}: Peso Corporal x ${s.reps} reps")
                    } else {
                        val weightStr = com.example.utils.WeightCalculator.formatStepNumber(s.weight)
                        val wUnit = if (isPerDumbbell) "kg (cada halter)" else "kg"
                        val rUnit = if (isPerSide) "reps (cada lado)" else "reps"
                        val pairSuffix = if (isPerDumbbell || isPerSide) {
                            val pairVol = com.example.utils.WeightCalculator.calculateSetVolumeKg(null, ex.name, ex.equipment, s.weight, s.reps).toInt()
                            " [Vol. par: $pairVol kg]"
                        } else ""
                        sb.appendLine("   • Série ${s.setIndex}: $weightStr $wUnit x ${s.reps} $rUnit$pairSuffix")
                    }
                }
            }
            if (ex.notes.isNotBlank()) {
                sb.appendLine("   📝 FEEDBACK DE CAMPO: ${ex.notes}")
            }
            sb.appendLine()
        }

        val exercisesWithNotes = session.exercises.filter { it.notes.isNotBlank() }
        if (exercisesWithNotes.isNotEmpty()) {
            sb.appendLine("------------------------------------------")
            sb.appendLine("COMPILADO DE ANOTAÇÕES DE CAMPO:")
            sb.appendLine("------------------------------------------")
            exercisesWithNotes.forEach { ex ->
                sb.appendLine("• ${ex.name}: ${ex.notes}")
            }
            sb.appendLine()
        }

        if (session.aiCoachReport.isNotBlank()) {
            sb.appendLine("------------------------------------------")
            sb.appendLine("TREINADOR FISIOLOGISTA IA PÓS-TREINO:")
            sb.appendLine("------------------------------------------")
            sb.appendLine(session.aiCoachReport.trim())
            sb.appendLine()
        }

        sb.appendLine("==========================================")
        sb.appendLine("MAX PERFORMANCE • REGISTRO BIOMÉTRICO OFICIAL")
        sb.appendLine("==========================================")

        return sb.toString()
    }
}
