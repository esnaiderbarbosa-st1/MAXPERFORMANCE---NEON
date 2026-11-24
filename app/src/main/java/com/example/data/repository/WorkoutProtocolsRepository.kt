package com.example.data.repository

import com.example.data.model.ProtocolExercise
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutProtocol
import com.example.data.model.WorkoutSet

object WorkoutProtocolsRepository {

    val ALL_PROTOCOLS: List<WorkoutProtocol> = listOf(
        WorkoutProtocol(
            id = "protocol_fb_a",
            name = "Full Body A",
            category = "FULL_BODY",
            frequency = "3x por semana (Ciclo A → B → C)",
            description = "Força Geral Anti-Fadiga SNC (Zig-Zag Inferior ⇄ Superior) — Máx 1 axial na sessão e prioridade para Torre de Placas/Polias.",
            exercises = listOf(
                ProtocolExercise("Agachamento Livre com Barra", "ou Leg Press 45° / Hack Squat", 4, "6-10", "1. Perna (Único Axial da Sessão): Mantenha o tronco firme e desça controlando o peso sem valgo dinâmico.", "Quadríceps"),
                ProtocolExercise("Supino Reto com Barra", "ou Supino Reto Máquina (Torre de Placas) / Halteres", 3, "8-10", "2. Superior Empurrar: Escápulas aduzidas no banco, descida controlada até a linha do esterno.", "Peito"),
                ProtocolExercise("Cadeira Flexora Máquina", "ou Mesa Flexora (Torre de Placas)", 3, "10-12", "3. Perna Guiado (Zero Carga Axial): Foque na contração de pico dos isquiotibiais mantendo o quadril apoiado.", "Posterior de Coxa"),
                ProtocolExercise("Remada Sentada na Máquina (Torre de Placas)", "ou Remada Baixa na Polia (Triângulo)", 3, "8-12", "4. Superior Puxar (Torre de Placas • Zero Fadiga Lombar): Puxe focando em espremer as escápulas com tronco estável.", "Costas"),
                ProtocolExercise("Cadeira Extensora", "ou Leg Press Horizontal (Torre de Placas)", 3, "10-12", "5. Perna Guiado: Estímulo tensional limpo para quadríceps sem comprimir a coluna.", "Quadríceps"),
                ProtocolExercise("Desenvolvimento na Máquina", "ou Desenvolvimento com Halteres", 3, "8-12", "6. Ombros Guiado: Empurre com estabilidade escapular sem hiperextender a lombar.", "Ombros"),
                ProtocolExercise("Encolhimento de Ombros com Halteres", "ou Encolhimento com Barra / Smith", 3, "10-12", "7. Trapézio / Cintura Escapular: Eleve as escápulas verticalmente segurando 2s no pico de contração.", "Trapézio"),
                ProtocolExercise("Panturrilha em Pé na Máquina", "ou Panturrilha no Leg Press", 4, "12-15", "8. Panturrilha: Amplitude máxima no alongamento do tendão de Aquiles e pausa de 1s no topo.", "Panturrilha"),
                ProtocolExercise("Tríceps Corda na Polia", "ou Rosca Direta na Polia", 3, "10-12", "9. Braços / Finalizador na Polia: Extensão completa com tensão contínua do cabo.", "Tríceps")
            )
        ),
        WorkoutProtocol(
            id = "protocol_fb_b",
            name = "Full Body B",
            category = "FULL_BODY",
            frequency = "3x por semana (Ciclo A → B → C)",
            description = "Ênfase Tronco / Upper + Pernas Guiado (Zig-Zag Inferior ⇄ Superior) — Zero carga axial na coluna, ideal pós-corrida.",
            exercises = listOf(
                ProtocolExercise("Cadeira Extensora", "ou Leg Press Horizontal (Torre de Placas)", 3, "10-12", "1. Perna Guiado: Estímulo controlado de quadríceps em torre de placas sem impacto articular.", "Quadríceps"),
                ProtocolExercise("Supino Declinado Máquina Total Health", "ou Supino Inclinado com Halteres / Supino Máquina", 3, "8-12", "2. Superior Empurrar: Máquina articulada convergente ou halteres com controle excêntrico.", "Peito"),
                ProtocolExercise("Cadeira Flexora", "ou Mesa Flexora Máquina (Torre de Placas)", 3, "10-12", "3. Perna Posterior Guiado: Controle excêntrico suave para proteção e irrigação dos isquiotibiais.", "Posterior de Coxa"),
                ProtocolExercise("Puxada Alta na Polia", "ou Puxada Alta Máquina Anilhas", 4, "8-12", "4. Superior Puxar (Torre de Placas): Puxe a barra em direção ao peito conduzindo a força pelos cotovelos.", "Costas"),
                ProtocolExercise("Puxada Alta Máquina Anilhas", "ou Remada Sentada na Máquina (Torre de Placas)", 3, "8-12", "5. Dorsal Densidade Guiada: Tracionamento dorsal sem sobrecarregar a lombar.", "Costas"),
                ProtocolExercise("Elevação Lateral com Halteres", "ou Elevação Lateral na Polia", 3, "12-15", "6. Ombros: Eleve até a altura dos ombros mantendo controle na descida.", "Ombros"),
                ProtocolExercise("Face Pull na Polia com Corda", "ou Encolhimento de Ombros com Halteres", 3, "12-15", "7. Trapézio / Cintura Escapular: Puxe a corda na direção da testa com rotação externa e retração escapular.", "Trapézio"),
                ProtocolExercise("Panturrilha no Leg Press", "ou Panturrilha Sentado na Máquina", 4, "12-15", "8. Panturrilha: Empurre com a ponta dos pés controlando a fase excêntrica sem quicar.", "Panturrilha"),
                ProtocolExercise("Rosca Martelo com Halteres", "ou Rosca Direta na Polia", 3, "10-12", "9. Braços: Pegada neutra focando no braquial e braquiorradial.", "Bíceps")
            )
        ),
        WorkoutProtocol(
            id = "protocol_fb_c",
            name = "Full Body C",
            category = "FULL_BODY",
            frequency = "3x por semana (Ciclo A → B → C)",
            description = "Ênfase Posterior / Glúteos Anti-Fadiga SNC (Zig-Zag Inferior ⇄ Superior) — RDL como único axial e costura alternada com superiores.",
            exercises = listOf(
                ProtocolExercise("Terra Romeno com Barra (RDL)", "ou Stiff com Barra", 4, "8-10", "1. Perna (Único Axial da Sessão): Coluna perfeitamente neutra e peito aberto, descendo a barra rente às canelas.", "Posterior de Coxa"),
                ProtocolExercise("Supino Inclinado com Halteres", "ou Supino Inclinado na Máquina", 3, "8-12", "2. Superior Empurrar (Descanso Lombar): Composto multiarticular com banco a 30°-45° (carga por cada halter).", "Peito"),
                ProtocolExercise("Standing Hip Thrust", "ou Elevação Pélvica na Máquina", 3, "8-12", "3. Perna / Glúteo: Extensão potente de quadril focando em glúteos com zero compressão axial.", "Glúteos"),
                ProtocolExercise("Remada Sentada na Máquina (Torre de Placas)", "ou Remada Baixa na Polia (Triângulo)", 3, "8-12", "4. Superior Puxar (Torre de Placas • Zero Carga Axial Lombar): Tracionamento dorsal guiado poupando o SNC e eretores da espinha.", "Costas"),
                ProtocolExercise("Leg Press Horizontal", "ou Cadeira Extensora (Torre de Placas)", 3, "10-12", "5. Perna Guiado: Empuxo horizontal guiado na máquina com coluna totalmente apoiada.", "Quadríceps"),
                ProtocolExercise("Desenvolvimento na Máquina", "ou Desenvolvimento com Halteres", 3, "10-12", "6. Ombros Guiado: Empurre acima da cabeça mantendo o abdômen contraído e costas apoiadas.", "Ombros"),
                ProtocolExercise("Remada Alta na Polia", "ou Encolhimento de Ombros com Halteres", 3, "10-12", "7. Trapézio / Cintura Escapular: Tracionamento vertical controlado focando em trapézio superior e deltoide lateral.", "Trapézio"),
                ProtocolExercise("Panturrilha Sentado na Máquina", "ou Panturrilha em Pé na Máquina", 4, "12-15", "8. Panturrilha: Estímulo direto no sóleo e gastrocnêmio com pausa no alongamento máximo.", "Panturrilha"),
                ProtocolExercise("Abdominal Infra Solo", "ou Prancha Isométrica", 3, "12-15", "9. Core (Peso Corporal): Eleve o quadril do solo controladamente para ativação completa do abdômen inferior.", "Abdominais")
            )
        ),
        WorkoutProtocol(
            id = "protocol_ppl_sup_inf",
            name = "PPL Superior/Inferior",
            category = "PPL",
            frequency = "5x por semana (SEG a SEX)",
            description = "Divisão de alta frequência separada por padrões de empurrar, puxar e membros inferiores.",
            exercises = listOf(
                ProtocolExercise("Supino Inclinado Barra", "ou Supino Reto com Halteres", 4, "8-10", "Sessões de Superior (SEG/QUI): Foco no peitoral e deltoides.", "Peito"),
                ProtocolExercise("Puxada Articulada", "ou Remada Baixa Pegada Aberta", 4, "8-12", "Sessões de Inferior/Costas (TER/SEX): Foco na largura e densidade de dorsais.", "Costas"),
                ProtocolExercise("Agachamento / Leg Press", "ou Agachamento Hack", 4, "8-10", "Sessão de Pernas (QUA): Base pesada para quadríceps e glúteos.", "Quadríceps"),
                ProtocolExercise("Elevação Lateral Halteres", "ou Elevação Lateral Polia", 4, "12-15", "Desenvolvimento dos deltoides laterais para proporção física.", "Ombros"),
                ProtocolExercise("Encolhimento com Halteres / Face Pull", "ou Remada Alta na Polia", 3, "12-15", "Trapézio e estabilidade da cintura escapular.", "Trapézio"),
                ProtocolExercise("Stiff / Mesa Flexora", "ou Cadeira Flexora", 4, "10-12", "Fortalecimento intenso de isquiotibiais e glúteos.", "Posterior de Coxa"),
                ProtocolExercise("Panturrilha em Pé na Máquina", "ou Panturrilha Sentado", 4, "12-15", "Trabalho direto de gastrocnêmio e sóleo.", "Panturrilha"),
                ProtocolExercise("Supino Fechado / Tríceps Corda", "ou Tríceps Francês na Polia", 3, "10-12", "Isolamento das 3 cabeças do tríceps.", "Tríceps"),
                ProtocolExercise("Rosca Direta / Rosca Martelo", "ou Rosca Scott", 3, "10-12", "Desenvolvimento de bíceps e braquial.", "Bíceps"),
                ProtocolExercise("Abdominal Prancha / Infra", "ou Abdominal Polia", 3, "30-45s / 15 reps", "Estabilização de core e força abdominal.", "Abdominais")
            )
        ),
        WorkoutProtocol(
            id = "protocol_mobility_recovery",
            name = "Mobilidade & Recuperação Ativa (15 min)",
            category = "MOBILIDADE",
            frequency = "Livre • Dias de Descanso e Regeneração",
            description = "Sessão regenerativa de 15 min de baixa carga interna para blindar o Sistema Nervoso Central (SNC), tendões, articulações e acelerar a recuperação muscular.",
            exercises = listOf(
                ProtocolExercise("Mobilidade de Quadril 90/90 e Cápsula Articular", "ou Alongamento Borboleta", 2, "10-12", "Movimento controlado de rotação interna e externa do quadril, mantendo o peito ereto.", "Mobilidade"),
                ProtocolExercise("Rotação Torácica e Abertura Escapular no Solo", "ou Rotação Torácica em 4 Apoios", 2, "10-12", "Apoie os joelhos e alcance longe abrindo a cintura escapular com respiração profunda.", "Mobilidade"),
                ProtocolExercise("Alongamento Dinâmico Posterior e Tornozelo", "ou Descida em Stiff Livre", 2, "12-15", "Mobilize a cadeia posterior e tornozelo suavemente sem forçar o limite doloroso.", "Mobilidade"),
                ProtocolExercise("Descompressão Lombar (Cat-Cow e Child's Pose)", "ou Postura da Criança", 2, "10-12", "Alterne flexão e extensão suave da coluna e descanse quadril sobre calcanhares.", "Mobilidade")
            )
        )
    )

    fun isCalfExercise(ex: WorkoutExercise): Boolean {
        val n = ex.name.lowercase()
        val c = ex.category.lowercase()
        return n.contains("panturrilha") || n.contains("gêmeos") || n.contains("gemeos") ||
            n.contains("soleo") || n.contains("sóleo") || c.contains("panturrilha")
    }

    fun isTrapsOrScapularExercise(ex: WorkoutExercise): Boolean {
        val n = ex.name.lowercase()
        val c = ex.category.lowercase()
        return n.contains("encolhimento") || n.contains("trapézio") || n.contains("trapezio") ||
            n.contains("face pull") || n.contains("remada alta") || c.contains("trapézio") || c.contains("trapezio")
    }

    /**
     * Garante que qualquer treino Full Body (Full Body A, B, C ou Recalculated Split)
     * inclua obrigatoriamente trabalho direto de Trapézio / Cintura Escapular e Panturrilha.
     */
    fun ensureCalfAndTrapsInExercises(workoutName: String, exercises: List<WorkoutExercise>): List<WorkoutExercise> {
        val lowerName = workoutName.lowercase()
        val isFullBody = lowerName.contains("full body") || lowerName.contains("corpo inteiro")
        if (!isFullBody || exercises.isEmpty()) return exercises

        val result = exercises.toMutableList()
        val hasTraps = result.any { isTrapsOrScapularExercise(it) }
        val hasCalf = result.any { isCalfExercise(it) }
        if (hasTraps && hasCalf) return result

        val isVariantB = lowerName.contains("full body b")
        val isVariantC = lowerName.contains("full body c")

        if (!hasTraps) {
            val (trapId, trapName, trapEq, trapReps, trapWeight) = when {
                isVariantB -> Quintuple("trapezio_face_pull", "Face Pull na Polia com Corda", "Polia", 15, 15f)
                isVariantC -> Quintuple("trapezio_remada_alta", "Remada Alta na Polia", "Polia", 12, 20f)
                else -> Quintuple("trapezio_encolhimento_halter", "Encolhimento de Ombros com Halteres", "Halteres", 12, 16f)
            }
            val trapExercise = WorkoutExercise(
                id = "ex_trap_${java.util.UUID.randomUUID()}",
                exerciseId = trapId,
                name = trapName,
                category = "Trapézio",
                equipment = trapEq,
                restTimeSeconds = 60,
                progressionType = "Linear",
                movementPattern = "Trapézio",
                blockType = "Acessórios",
                sets = (1..3).map { sIdx ->
                    WorkoutSet(
                        id = "set_trap_${java.util.UUID.randomUUID()}_$sIdx",
                        index = sIdx,
                        reps = trapReps,
                        weight = trapWeight,
                        completed = false
                    )
                }
            )
            result.add(trapExercise)
        }

        if (!hasCalf) {
            val (calfId, calfName, calfEq, calfWeight) = when {
                isVariantB -> Quadruple("panturrilha_leg_press", "Panturrilha no Leg Press", "Máquina", 60f)
                isVariantC -> Quadruple("panturrilha_sentado", "Panturrilha Sentado na Máquina", "Máquina", 30f)
                else -> Quadruple("panturrilha_em_pe", "Panturrilha em Pé na Máquina", "Máquina", 40f)
            }
            val calfExercise = WorkoutExercise(
                id = "ex_calf_${java.util.UUID.randomUUID()}",
                exerciseId = calfId,
                name = calfName,
                category = "Panturrilha",
                equipment = calfEq,
                restTimeSeconds = 60,
                progressionType = "Linear",
                movementPattern = "Panturrilha",
                blockType = "Acessórios",
                sets = (1..4).map { sIdx ->
                    WorkoutSet(
                        id = "set_calf_${java.util.UUID.randomUUID()}_$sIdx",
                        index = sIdx,
                        reps = 15,
                        weight = calfWeight,
                        completed = false
                    )
                }
            )
            result.add(calfExercise)
        }

        return result
    }

    fun ensureCalfAndTrapsForFullBody(workout: Workout): Workout {
        val lowerName = workout.name.lowercase()
        val isFullBody = lowerName.contains("full body") || lowerName.contains("corpo inteiro") ||
            workout.id.contains("protocol_fb_") || workout.id.contains("full_body")
        if (!isFullBody) return workout

        val parsed = try {
            com.example.utils.JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        } catch (_: Exception) {
            emptyList()
        }
        if (parsed.isEmpty()) return workout
        val enriched = ensureCalfAndTrapsInExercises(workout.name.ifBlank { workout.id }, parsed)
        if (enriched.size == parsed.size) return workout
        return workout.copy(
            exercisesJson = com.example.utils.JsonUtils.serializeWorkoutExercises(enriched)
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    /**
     * Converte um protocolo em um Workout para execução no Player/Sessão.
     */
    fun convertProtocolToWorkout(protocol: WorkoutProtocol): Workout {
        if (protocol.id == "protocol_mobility_recovery" || protocol.category == "MOBILIDADE") {
            return com.example.services.recommendation.RecommendationEngine().createMobilityRecoveryWorkout()
        }
        val workoutExercises = protocol.exercises.mapIndexed { index, ex ->
            val targetReps = ex.reps.split("-").lastOrNull()?.trim()?.toIntOrNull() ?: 10
            val inferredEquip = com.example.utils.WeightCalculator.inferEquipment(ex.name)
            val defaultWeight = com.example.utils.WeightCalculator.defaultStartingWeightForExercise(ex.name, inferredEquip)
            val setList = (1..ex.sets).map { setIndex ->
                WorkoutSet(
                    id = "set_proto_${protocol.id}_${index}_$setIndex",
                    index = setIndex,
                    reps = targetReps,
                    weight = defaultWeight,
                    completed = false
                )
            }
            WorkoutExercise(
                id = "ex_proto_${protocol.id}_$index",
                exerciseId = "ex_proto_${protocol.id}_$index",
                name = ex.name,
                category = ex.muscleGroup,
                sets = setList,
                restTimeSeconds = if (index == 0) 90 else 60,
                progressionType = "Linear",
                movementPattern = ex.muscleGroup,
                equipment = inferredEquip,
                blockType = if (index < 4) "Principal" else "Acessórios"
            )
        }

        val json = com.example.utils.JsonUtils.serializeWorkoutExercises(workoutExercises)
        return Workout(
            id = "workout_from_protocol_${protocol.id}",
            templateId = "protocol_${protocol.id}",
            name = protocol.name,
            description = "${protocol.description} • ${protocol.frequency}",
            exercisesJson = json,
            createdAt = 1700000000000L
        )
    }
}
