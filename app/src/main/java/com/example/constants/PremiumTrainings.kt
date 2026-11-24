package com.example.constants

import com.example.data.model.*
import com.example.utils.JsonUtils

enum class NivelExperiencia { INICIANTE, INTERMEDIARIO, AVANCADO }
enum class Objetivo { HIPERTROFIA, FORCA, RESISTENCIA, DEFINICAO }
enum class TecnicaIntensidade {
    NENHUMA, FALHA, REST_PAUSE, DROP_SET, STRIP_SET, CLUSTER, 
    M_TUT, FST_7, PICO_CONTRACAO, TENS_PROGRESSIVA, BI_SET, TRI_SET
}

data class TecnicaExecucao(
    val tecnica: TecnicaIntensidade,
    val parametros: Map<String, String> = emptyMap(),
    val descricao: String = ""
)

data class PremiumSerie(
    val repeticoes: String, // Ex: "10-15" ou "6-12" ou "7"
    val cargaPercentual: Int? = null, // % de 1RM ou do Peso Corporal
    val rpe: String = "7-9", // Escala 1-10
    val tecnica: TecnicaExecucao? = null,
    val intervalo: Int = 60, // segundos
    val velocidade: String = "1/1" // Fase excêntrica/concêntrica
) {
    val repsRange: RepsRange get() = RepsRange.parse(repeticoes)
    val rpeRangeVal: RpeRange get() = RpeRange.parse(rpe)
    val restInterval: RestInterval get() = RestInterval(intervalo)
}

data class PremiumExercicio(
    val id: String,
    val nome: String,
    val grupoMuscular: List<String>,
    val series: List<PremiumSerie>,
    val instrucoes: String = "",
    val implemento: String? = null
) {
    val exerciseIdentifier: ExerciseId get() = ExerciseId(id)
}

data class PremiumDiaTreino(
    val nome: String,
    val foco: String,
    val ordem: Int,
    val exercicios: List<PremiumExercicio>,
    val duracaoEstimada: Int = 45 // minutos
)

data class PremiumProtocolo(
    val id: String,
    val nome: String,
    val nivel: NivelExperiencia,
    val objetivo: Objetivo,
    val semanas: Int,
    val diasPorSemana: Int,
    val dias: List<PremiumDiaTreino>,
    val descricaoGeral: String,
    val versao: String = "1.0.0"
) {
    val protocolIdentifier: ProtocolId get() = ProtocolId(id)

    // Conveter para objeto Workout do banco de dados para poder iniciar a sessão de treino real!
    fun toWorkout(
        diaId: String,
        cardioAtivo: Boolean = true,
        adicionarAquecimento: Boolean = false,
        limiteTempoMinutos: Int = 60
    ): Workout? {
        val dia = dias.firstOrNull { it.nome == diaId || it.nome.contains(diaId, ignoreCase = true) } ?: return null
        
        // Se cardio desativado, remove cardio.
        var filteredExercicios = dia.exercicios.filter { ex ->
            if (!cardioAtivo) {
                val isCardio = ex.grupoMuscular.any { it.contains("cardio", ignoreCase = true) || it.contains("aeróbico", ignoreCase = true) || it.contains("aerobico", ignoreCase = true) } ||
                        ex.id.contains("cardio", ignoreCase = true) ||
                        ex.nome.lowercase().contains("cardio") ||
                        ex.nome.lowercase().contains("esteira") ||
                        ex.nome.lowercase().contains("corrida")
                !isCardio
            } else {
                true
            }
        }.toMutableList()

        // Adiciona séries de aquecimento conforme preferência
        if (adicionarAquecimento && filteredExercicios.isNotEmpty()) {
            val firstEx = filteredExercicios[0]
            val firstSerie = firstEx.series.firstOrNull()
            if (firstSerie != null) {
                val warmupSerie = PremiumSerie(
                    repeticoes = firstSerie.repeticoes,
                    cargaPercentual = ((firstSerie.cargaPercentual ?: 50) * 0.6f).toInt(),
                    rpe = "4-5",
                    tecnica = TecnicaExecucao(tecnica = TecnicaIntensidade.NENHUMA, descricao = "Aquecimento"),
                    intervalo = 45,
                    velocidade = firstSerie.velocidade
                )
                filteredExercicios[0] = firstEx.copy(
                    series = listOf(warmupSerie) + firstEx.series
                )
            }
        }

        // Se tempo exceder limite, ajusta séries sem nunca derrubar para menos de 5 exercícios ou 3 séries
        fun calcularTempoEstimado(exercicios: List<PremiumExercicio>): Int {
            var totalSegundos = 0
            for (ex in exercicios) {
                totalSegundos += 45 // Setup time
                for (s in ex.series) {
                    totalSegundos += 40 + s.intervalo // Execution time + rest interval
                }
            }
            return totalSegundos / 60
        }

        while (calcularTempoEstimado(filteredExercicios) > limiteTempoMinutos) {
            var reduziuSerie = false
            for (i in filteredExercicios.indices) {
                val ex = filteredExercicios[i]
                if (ex.series.size > 3) {
                    filteredExercicios[i] = ex.copy(series = ex.series.take(ex.series.size - 1))
                    reduziuSerie = true
                    break
                }
            }
            if (reduziuSerie) continue

            if (filteredExercicios.size > 5) {
                filteredExercicios.removeAt(filteredExercicios.size - 1)
            } else {
                break
            }
        }

        // Guarantees that no exercise ever has 0 series
        filteredExercicios = filteredExercicios.map { ex ->
            if (ex.series.isEmpty()) {
                ex.copy(series = listOf(
                    PremiumSerie(repeticoes = "10", intervalo = 60),
                    PremiumSerie(repeticoes = "10", intervalo = 60),
                    PremiumSerie(repeticoes = "10", intervalo = 60)
                ))
            } else ex
        }.toMutableList()

        val workoutExercises = filteredExercicios.mapIndexed { idx, ex ->
            val inferredEquipment = ex.implemento ?: inferEquipmentFromName(ex.nome)
            val workoutSets = ex.series.mapIndexed { sIdx, s ->
                val repsVal = s.repsRange.min
                val rawWeight = if (s.cargaPercentual != null && s.cargaPercentual > 0) {
                    s.cargaPercentual.toFloat()
                } else {
                    com.example.utils.WeightCalculator.defaultStartingWeightForExercise(ex.nome, inferredEquipment)
                }
                val weightEst = com.example.utils.WeightCalculator.roundToAvailableWeight(rawWeight, inferredEquipment)
                WorkoutSet(
                    id = "premium_set_${id}_${diaId}_${ex.id}_${idx}_$sIdx",
                    index = sIdx + 1,
                    reps = repsVal,
                    weight = weightEst,
                    completed = false,
                    rpe = s.rpe
                )
            }
            WorkoutExercise(
                id = "premium_w_ex_${id}_${diaId}_${ex.id}_$idx",
                exerciseId = ex.id,
                name = ex.nome,
                category = ex.grupoMuscular.firstOrNull() ?: "Pernas",
                sets = workoutSets,
                restTimeSeconds = ex.series.firstOrNull()?.intervalo ?: 60,
                equipment = inferredEquipment
            )
        }
        return Workout(
            id = "premium_workout_${id}_${diaId}",
            templateId = "premium_workout_${id}_${diaId}",
            name = "$nome - ${dia.nome}",
            description = "${dia.foco} (${nivel.name}) - ${explicarTecnicasPremium(dia)}",
            exercisesJson = JsonUtils.serializeWorkoutExercises(workoutExercises),
            createdAt = System.currentTimeMillis()
        )
    }

    private fun inferEquipmentFromName(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("barra") || n.contains("smith") -> "Barra"
            n.contains("halter") -> "Halteres"
            n.contains("pulley") || n.contains("polia") || n.contains("cabo") || n.contains("triângulo") -> "Polia"
            n.contains("press") || n.contains("máquina") || n.contains("maquina") || n.contains("extensora") || n.contains("flexora") -> "Máquina"
            n.contains("abdominal") || n.contains("solo") || n.contains("flexão") -> "Peso Corporal"
            else -> "Outros"
        }
    }

    private fun explicarTecnicasPremium(dia: PremiumDiaTreino): String {
        val tecnicas = dia.exercicios.flatMap { it.series.mapNotNull { s -> s.tecnica?.tecnica } }.distinct()
        if (tecnicas.isEmpty()) return "Progressão linear baseada em esforço."
        return "Técnicas inclusas: " + tecnicas.joinToString { it.name }
    }
}

object PremiumTrainings {

    // 1. INICIANTE - PROGRESSÃO LINEAR
    val fullBodyFundamentos = PremiumProtocolo(
        id = "FB_FUNDAMENTOS",
        nome = "Full Body Fundamentos",
        nivel = NivelExperiencia.INICIANTE,
        objetivo = Objetivo.RESISTENCIA,
        semanas = 4,
        diasPorSemana = 3,
        descricaoGeral = "Foco em reeducação motora, fortalecimento das articulações e progressão linear de carga.",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Treino A - Corpo Inteiro",
                foco = "Base Muscular Completa",
                ordem = 1,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_1",
                        nome = "Agachamento Livre Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 50, rpe = "6-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 55, rpe = "7-9", intervalo = 60)
                        ),
                        instrucoes = "Mantenha a coluna neutra e joelhos alinhados com a ponta dos pés."
                    ),
                    PremiumExercicio(
                        id = "peito_1",
                        nome = "Supino Reto Barra",
                        grupoMuscular = listOf("Peito", "Tríceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 50, rpe = "6-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 55, rpe = "7-9", intervalo = 60)
                        ),
                        instrucoes = "Desça a barra de forma controlada até o peito e empurre verticalmente."
                    ),
                    PremiumExercicio(
                        id = "costas_1",
                        nome = "Puxada Frontal Pulley",
                        grupoMuscular = listOf("Costas", "Bíceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 50, rpe = "6-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-15", cargaPercentual = 55, rpe = "7-9", intervalo = 60)
                        ),
                        instrucoes = "Puxe a barra em direção ao peito, contraindo as escápulas."
                    ),
                    PremiumExercicio(
                        id = "ombros_2",
                        nome = "Elevação Lateral Halteres",
                        grupoMuscular = listOf("Ombros"),
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 30, rpe = "7-8", intervalo = 45)
                        ),
                        instrucoes = "Eleve os braços lateralmente com leve flexão nos cotovelos."
                    ),
                    PremiumExercicio(
                        id = "abdominais_1",
                        nome = "Abdominal Crunch Solo",
                        grupoMuscular = listOf("Abdominais"),
                        series = listOf(
                            PremiumSerie(repeticoes = "15-20", cargaPercentual = 0, rpe = "7-8", intervalo = 45)
                        ),
                        instrucoes = "Faça a contração do abdômen retirando os ombros do colchonete."
                    )
                )
            )
        )
    )

    // 2. INTERMEDIÁRIO - ONDULAÇÃO DE CARGA
    val abcdProgressivo = PremiumProtocolo(
        id = "ABCD_PROGRESSIVO",
        nome = "ABCD Progressivo",
        nivel = NivelExperiencia.INTERMEDIARIO,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 6,
        diasPorSemana = 4,
        descricaoGeral = "Ondulação controlada de intensidade com inclusão de falha mecânica e pausas Rest-Pause.",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Dia A - Peito/Tríceps",
                foco = "Padrão de Empurrar",
                ordem = 1,
                duracaoEstimada = 50,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "peito_1",
                        nome = "Supino Reto Barra",
                        grupoMuscular = listOf("Peito", "Tríceps"),
                        series = listOf(
                            PremiumSerie(
                                repeticoes = "6-12",
                                cargaPercentual = 80,
                                rpe = "8-9",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.FALHA,
                                    descricao = "Executar até a falha concêntrica na primeira série."
                                ),
                                intervalo = 40
                            ),
                            PremiumSerie(
                                repeticoes = "6-12",
                                cargaPercentual = 80,
                                rpe = "8-9",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.REST_PAUSE,
                                    parametros = mapOf("pausas" to "1", "respiros" to "3"),
                                    descricao = "Após a falha, descanse 15s e realize mais 2-3 reps limite."
                                ),
                                intervalo = 40
                            ),
                            PremiumSerie(
                                repeticoes = "6-12",
                                cargaPercentual = 70,
                                rpe = "7-8",
                                intervalo = 40
                            )
                        )
                    ),
                    PremiumExercicio(
                        id = "peito_2",
                        nome = "Supino Inclinado Halteres",
                        grupoMuscular = listOf("Peito"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 75, rpe = "8-9", intervalo = 40),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 75, rpe = "8-9", intervalo = 40)
                        )
                    ),
                    PremiumExercicio(
                        id = "triceps_1",
                        nome = "Tríceps Pulley Barra",
                        grupoMuscular = listOf("Tríceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "8-9", intervalo = 45),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "8-9", intervalo = 45)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Dia B - Costas/Bíceps",
                foco = "Padrão de Puxar",
                ordem = 2,
                duracaoEstimada = 50,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "costas_3",
                        nome = "Remada Baixa Triângulo",
                        grupoMuscular = listOf("Costas", "Bíceps"),
                        series = listOf(
                            PremiumSerie(
                                repeticoes = "6-12",
                                cargaPercentual = 75,
                                rpe = "8-9",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.DROP_SET,
                                    parametros = mapOf("quedas" to "2", "reducao" to "20%"),
                                    descricao = "Após falhar, reduza 20% do peso e continue até a falha. Repita mais uma vez."
                                ),
                                intervalo = 50
                            ),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 50)
                        )
                    ),
                    PremiumExercicio(
                        id = "costas_1",
                        nome = "Puxada Frontal Pulley",
                        grupoMuscular = listOf("Costas"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 50)
                        )
                    ),
                    PremiumExercicio(
                        id = "biceps_1",
                        nome = "Rosca Direta Barra W",
                        grupoMuscular = listOf("Bíceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 45),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 45)
                        )
                    )
                )
            )
        )
    )

    // 3. AVANÇADO - CHOQUE & INTIMIDAÇÃO MUSCULAR
    val aniquilacaoQuadriceps = PremiumProtocolo(
        id = "CHOQUE_AVANCADO",
        nome = "Aniquilação de Quadríceps",
        nivel = NivelExperiencia.AVANCADO,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 4,
        diasPorSemana = 5,
        descricaoGeral = "Estímulos brutais integrando clusters, modulação de velocidade de tensão (M-TUT) e bi-sets massivos.",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Treino de Quadríceps (Choque)",
                foco = "Destruição Miofibrilar",
                ordem = 1,
                duracaoEstimada = 75,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_2",
                        nome = "Leg Press 45 Graus",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(
                                repeticoes = "7",
                                cargaPercentual = 75,
                                rpe = "9-10",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.CLUSTER,
                                    parametros = mapOf("miniSeries" to "3", "miniReps" to "7", "microPausa" to "15s"),
                                    descricao = "Faça 7 reps, descanse 15s no suporte, faça mais 7 reps, descanse 15s, faça mais 7 reps (Total de 21 reps em uma série)."
                                ),
                                intervalo = 60
                            ),
                            PremiumSerie(
                                repeticoes = "7",
                                cargaPercentual = 75,
                                rpe = "9-10",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.CLUSTER,
                                    parametros = mapOf("miniSeries" to "3", "miniReps" to "7", "microPausa" to "15s"),
                                    descricao = "Faça 7 reps, descanse 15s no suporte, faça mais 7 reps, descanse 15s, faça mais 7 reps."
                                ),
                                intervalo = 60
                            )
                        ),
                        instrucoes = "Mantenha a amplitude total segura."
                    ),
                    PremiumExercicio(
                        id = "pernas_1",
                        nome = "Agachamento Livre Barra",
                        grupoMuscular = listOf("Pernas"),
                        series = listOf(
                            PremiumSerie(
                                repeticoes = "4",
                                cargaPercentual = 65,
                                rpe = "8-9",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.M_TUT,
                                    parametros = mapOf("cadencia" to "Lenta excêntrica"),
                                    descricao = "Fase excêntrica extremamente lenta (5 segundos descendo) para maximizar microlesões."
                                ),
                                intervalo = 60
                            )
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_3",
                        nome = "Cadeira Extensora",
                        grupoMuscular = listOf("Pernas"),
                        series = listOf(
                            PremiumSerie(
                                repeticoes = "6-12",
                                cargaPercentual = 70,
                                rpe = "9-10",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.DROP_SET,
                                    parametros = mapOf("quedas" to "2", "strip" to "sim"),
                                    descricao = "Vá até a falha, reduza 20%, continue, reduza mais 20% e feche com isometria até a falha total."
                                ),
                                intervalo = 50
                            )
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Treino Posterior FST-7",
                foco = "Expansão de Fáscia",
                ordem = 2,
                duracaoEstimada = 60,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_4",
                        nome = "Mesa Flexora",
                        grupoMuscular = listOf("Isquiotibiais"),
                        series = List(7) { index ->
                            PremiumSerie(
                                repeticoes = "12-15",
                                cargaPercentual = 55,
                                rpe = "7-8",
                                tecnica = TecnicaExecucao(
                                    tecnica = TecnicaIntensidade.FST_7,
                                    parametros = mapOf("serie" to "${index + 1}/7", "descanso" to "30s"),
                                    descricao = "Série ${index + 1} de 7 do protocolo FST-7. Apenas 30 segundos de descanso!"
                                ),
                                intervalo = 30
                            )
                        },
                        instrucoes = "Alongue ativamente o posterior nos 30 segundos de intervalo."
                    )
                )
            )
        )
    )

    val upperLowerIniciante = PremiumProtocolo(
        id = "UL_INICIANTE",
        nome = "Upper / Lower Iniciante",
        nivel = NivelExperiencia.INICIANTE,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 6,
        diasPorSemana = 4,
        descricaoGeral = "Divisão clássica superior e inferior com foco em técnica e ganho de força consistente.",
        versao = "1.0.0",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Upper A - Superior",
                foco = "Peito/Costas/Ombros",
                ordem = 1,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "peito_1",
                        nome = "Supino Reto Barra",
                        grupoMuscular = listOf("Peito", "Tríceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 55, rpe = "7-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 60, rpe = "7-8", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "costas_1",
                        nome = "Puxada Frontal Pulley",
                        grupoMuscular = listOf("Costas", "Bíceps"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 55, rpe = "7-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 60, rpe = "7-8", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "ombros_2",
                        nome = "Elevação Lateral Halteres",
                        grupoMuscular = listOf("Ombros"),
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 35, rpe = "7-8", intervalo = 45),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 35, rpe = "7-8", intervalo = 45)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Lower A - Inferior",
                foco = "Pernas/Glúteos",
                ordem = 2,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_1",
                        nome = "Agachamento Livre Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 55, rpe = "7-8", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 60, rpe = "7-8", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_4",
                        nome = "Mesa Flexora",
                        grupoMuscular = listOf("Pernas"),
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 50, rpe = "7-8", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 50, rpe = "7-8", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "abdominais_1",
                        nome = "Abdominal Crunch Solo",
                        grupoMuscular = listOf("Abdominais"),
                        series = listOf(
                            PremiumSerie(repeticoes = "15-20", cargaPercentual = 0, rpe = "7-8", intervalo = 45),
                            PremiumSerie(repeticoes = "15-20", cargaPercentual = 0, rpe = "7-8", intervalo = 45)
                        )
                    )
                )
            )
        )
    )

    val upperLowerProgressivo = PremiumProtocolo(
        id = "UL_PROGRESSIVO",
        nome = "Upper / Lower Progressivo",
        nivel = NivelExperiencia.INTERMEDIARIO,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 8,
        diasPorSemana = 4,
        descricaoGeral = "Progressão de volume semanal ondulada em estrutura superior e inferior.",
        versao = "1.0.0",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Upper A - Superior Intermediário",
                foco = "Peito/Costas/Ombros",
                ordem = 1,
                duracaoEstimada = 50,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "peito_1",
                        nome = "Supino Reto Barra",
                        grupoMuscular = listOf("Peito"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "costas_2",
                        nome = "Remada Curvada Barra",
                        grupoMuscular = listOf("Costas"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "ombros_1",
                        nome = "Desenvolvimento Halteres",
                        grupoMuscular = listOf("Ombros"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 75)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Lower A - Inferior Intermediário",
                foco = "Pernas/Glúteos",
                ordem = 2,
                duracaoEstimada = 50,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_1",
                        nome = "Agachamento Livre Barra",
                        grupoMuscular = listOf("Pernas"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_5",
                        nome = "Afundo com Halteres",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "abdominais_2",
                        nome = "Elevação de Pernas Suspenso",
                        grupoMuscular = listOf("Abdominais"),
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 0, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 0, rpe = "8-9", intervalo = 60)
                        )
                    )
                )
            )
        )
    )

    val gluteo3xSemana = PremiumProtocolo(
        id = "GLUTEOS_3X",
        nome = "Glúteo 3x/semana",
        nivel = NivelExperiencia.INTERMEDIARIO,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 12,
        diasPorSemana = 3,
        descricaoGeral = "Foco total no desenvolvimento dos glúteos e cadeia posterior com alta frequência semanal (3x).",
        versao = "1.0.0",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Dia A - Foco Quadril",
                foco = "Glúteo / Posterior",
                ordem = 1,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_6",
                        nome = "Hip Thrust com Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 75, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 75, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_7",
                        nome = "Cadeira Abdutora",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 65, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 65, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 65, rpe = "8-9", intervalo = 60)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Dia B - Foco Unilateral",
                foco = "Glúteo / Pernas",
                ordem = 2,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_8",
                        nome = "Agachamento Búlgaro",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 65, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "8-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_5",
                        nome = "Afundo com Halteres",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 75)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Dia C - Foco Posterior",
                foco = "Glúteo / Isquiotibiais",
                ordem = 3,
                duracaoEstimada = 45,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_6",
                        nome = "Hip Thrust com Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_4",
                        nome = "Mesa Flexora",
                        grupoMuscular = listOf("Pernas"),
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 65, rpe = "8-9", intervalo = 60)
                        )
                    )
                )
            )
        )
    )

    val upperLowerAvancado = PremiumProtocolo(
        id = "UL_AVANCADO",
        nome = "Upper / Lower Avançado",
        nivel = NivelExperiencia.AVANCADO,
        objetivo = Objetivo.HIPERTROFIA,
        semanas = 6,
        diasPorSemana = 4,
        descricaoGeral = "Treino avançado de alta densidade e alta carga focado em tensão mecânica máxima e volume de hipertrofia completo.",
        versao = "1.0.0",
        dias = listOf(
            PremiumDiaTreino(
                nome = "Upper A - Superior Choque",
                foco = "Peito, Costas, Ombros e Braços",
                ordem = 1,
                duracaoEstimada = 60,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "peito_1",
                        nome = "Supino Reto Barra",
                        grupoMuscular = listOf("Peito", "Tríceps"),
                        implemento = "Barra",
                        series = listOf(
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 80, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9-10", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9-10", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "costas_1",
                        nome = "Puxada Frontal Pulley",
                        grupoMuscular = listOf("Costas", "Bíceps"),
                        implemento = "Polia",
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9-10", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9-10", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "ombros_1",
                        nome = "Desenvolvimento Halteres",
                        grupoMuscular = listOf("Ombros", "Tríceps"),
                        implemento = "Halteres",
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9-10", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "costas_2",
                        nome = "Remada Curvada com Barra",
                        grupoMuscular = listOf("Costas", "Bíceps"),
                        implemento = "Barra",
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9-10", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "ombros_2",
                        nome = "Elevação Lateral Halteres",
                        grupoMuscular = listOf("Ombros"),
                        implemento = "Halteres",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "triceps_1",
                        nome = "Tríceps Pulley Barra",
                        grupoMuscular = listOf("Tríceps"),
                        implemento = "Polia",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "biceps_1",
                        nome = "Rosca Direta Barra W",
                        grupoMuscular = listOf("Bíceps"),
                        implemento = "Barra",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    )
                )
            ),
            PremiumDiaTreino(
                nome = "Lower A - Inferior Choque",
                foco = "Quadríceps, Isquiotibiais, Glúteos e Panturrilhas",
                ordem = 2,
                duracaoEstimada = 60,
                exercicios = listOf(
                    PremiumExercicio(
                        id = "pernas_1",
                        nome = "Agachamento Livre Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        implemento = "Barra",
                        series = listOf(
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 80, rpe = "8-9", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9-10", intervalo = 90),
                            PremiumSerie(repeticoes = "6-8", cargaPercentual = 85, rpe = "9-10", intervalo = 90)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_2",
                        nome = "Leg Press 45",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        implemento = "Máquina",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 80, rpe = "9", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 80, rpe = "9-10", intervalo = 75),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 80, rpe = "9-10", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_3",
                        nome = "Stiff com Barra",
                        grupoMuscular = listOf("Pernas", "Glúteos"),
                        implemento = "Barra",
                        series = listOf(
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 75, rpe = "8-9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9", intervalo = 75),
                            PremiumSerie(repeticoes = "8-10", cargaPercentual = 80, rpe = "9-10", intervalo = 75)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_4",
                        nome = "Cadeira Extensora",
                        grupoMuscular = listOf("Pernas"),
                        implemento = "Máquina",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_5",
                        nome = "Mesa Flexora",
                        grupoMuscular = listOf("Pernas"),
                        implemento = "Máquina",
                        series = listOf(
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "10-12", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    ),
                    PremiumExercicio(
                        id = "pernas_6",
                        nome = "Gêmeos Sentado",
                        grupoMuscular = listOf("Panturrilhas"),
                        implemento = "Máquina",
                        series = listOf(
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 70, rpe = "8-9", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 75, rpe = "9", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 75, rpe = "9-10", intervalo = 60),
                            PremiumSerie(repeticoes = "12-15", cargaPercentual = 75, rpe = "9-10", intervalo = 60)
                        )
                    )
                )
            )
        )
    )

    val ALL_PROTOCOLS = listOf(
        fullBodyFundamentos, 
        abcdProgressivo, 
        upperLowerAvancado,
        aniquilacaoQuadriceps, 
        upperLowerIniciante, 
        upperLowerProgressivo, 
        gluteo3xSemana
    )

    // Aeróbicos
    data class SessaoAerobica(
        val tipo: String, // LISS, HIIT, Tabata
        val duracao: Int, // minutos
        val intensidade: String, // RPE ou FCMax
        val progressao: String,
        val descricao: String
    )

    fun getLissProgressivo(semana: Int) = SessaoAerobica(
        tipo = "LISS (Baixa Intensidade Contínua)",
        duracao = 20 + (semana * 2).coerceAtMost(10),
        intensidade = "60-65% FCMax (RPE 5-6)",
        progressao = "Aumentar de 2 a 3 minutos por semana conforme condicionamento.",
        descricao = "Caminhada acelerada ou bicicleta leve. Perfeito para oxidação lipídica direta sem sobrecarregar o sistema nervoso central."
    )

    fun getHiitGibala(semana: Int) = SessaoAerobica(
        tipo = "HIIT Protocolo Gibala",
        duracao = 20,
        intensidade = "85-95% FCMax (RPE 9-10 nos tiros)",
        progressao = "Aumentar 1 tiro de 60s por semana, começando com 6 e indo até 10 tiros.",
        descricao = "Aquecimento de 3 minutos. Tiros de 60 segundos na intensidade máxima seguidos por 75 segundos de recuperação ativa leve. Repetir ciclo."
    )

    fun getTabata(semana: Int) = SessaoAerobica(
        tipo = "HIIT Protocolo Tabata",
        duracao = 4 + (semana.coerceAtMost(3) * 4),
        intensidade = "170% VO2max (Máximo Esforço Absoluto)",
        progressao = "Adicione 1 bloco extra de 4 minutos conforme tolerância.",
        descricao = "Protocolo de 4 minutos: 8 ciclos de 20 segundos de esforço extremo (corrida, corda ou burpees) seguidos por 10 segundos de descanso total."
    )
}

class CalculadoraTreino {
    // 1. Estimar 1RM (Fórmula de Brzycki)
    fun estimar1RM(peso: Double, repeticoes: Int): Double {
        return com.example.services.domain.PerformanceEngine.estimar1RM(peso, repeticoes)
    }

    // 2. Calcular Carga recomendada para %1RM
    fun calcularCarga(rm: Double, percentual: Int): Double {
        return com.example.services.domain.PerformanceEngine.calcularCarga(rm, percentual)
    }

    // 3. RPE para %1RM Estimado
    fun rpeParaPercentual(rpe: Int): Int {
        return com.example.services.domain.PerformanceEngine.rpeParaPercentual(rpe)
    }
}
