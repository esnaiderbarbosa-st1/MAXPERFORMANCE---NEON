package com.example.services.recommendation

import com.example.data.model.Exercise
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.engine.WorkoutContext
import com.example.engine.WorkoutConstraints
import com.example.engine.UserPreferences
import com.example.services.recommendation.knowledge.TrainingKnowledgeBase
import com.example.services.recommendation.knowledge.UserLevelConfigs
import com.example.services.recommendation.planners.*
import com.example.services.recommendation.ruleengine.RuleEngine
import com.example.services.recommendation.ruleengine.RuleEngineState
import com.example.services.telemetry.Telemetry
import com.example.data.model.TelemetryEventType
import com.example.utils.JsonUtils
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

// Legacy Context mapper to maintain compatibility across any unmigrated files
data class RecommendationContext(
    val objective: String,
    val timeAvailableMinutes: Int,
    val availableEquipment: List<String>,
    val targetMuscleGroup: String,
    val userLevel: String,
    val restrictions: List<String>,
    val preferences: List<String> = emptyList(),
    val recoveryIndex: Double = 8.0,
    val history: List<Any> = emptyList(),
    val bioimpedance: Map<String, Any> = emptyMap(),
    val fatigueScore: Double = 0.0,
    val useAI: Boolean = false
)

class RecommendationEngine(
    private val volumePlanner: VolumePlanner = VolumePlannerImpl(),
    private val restPlanner: RestPlanner = RestPlannerImpl(),
    private val progressionPlanner: ProgressionPlanner = ProgressionPlannerImpl(),
    private val intensityPlanner: IntensityPlanner = IntensityPlannerImpl(),
    private val exerciseSelector: ExerciseSelector = ExerciseSelectorImpl(),
    private val timePlanner: TimePlanner = TimePlannerImpl(),
    private val recoveryPlanner: RecoveryPlanner = RecoveryPlannerImpl()
) {

    /**
     * Decision-Based Recommendation Engine (RFC-REC-001)
     * Analyzes all physiological and adherence variables to suggest the next best strategic decision.
     */
    fun makeDecision(
        context: WorkoutContext,
        pastHistory: List<com.example.data.model.WorkoutHistoryEntry>,
        metaSemanal: Int
    ): DecisionResult {
        val now = System.currentTimeMillis()
        val fourteenDaysAgo = now - 14 * 24 * 60 * 60 * 1000L
        val completedCount14Days = pastHistory.count { it.date >= fourteenDaysAgo }
        val avgRealWeeklyFrequency = completedCount14Days.toDouble() / 2.0
        val targetWeeklyWorkouts = metaSemanal
        val adherence = if (targetWeeklyWorkouts > 0) (avgRealWeeklyFrequency / targetWeeklyWorkouts) * 100.0 else 100.0

        val fatigueLevel = context.fatigueLevel
        val recoveryProfile = context.recoveryProfile
        val fatigueTrend = if (fatigueLevel > 7.0 && recoveryProfile < 5.0) {
            "Alta (Acúmulo de Estresse)"
        } else if (fatigueLevel > 4.0 || recoveryProfile < 7.0) {
            "Moderada"
        } else {
            "Baixa (Zona de Performance)"
        }

        val level = context.experience
        val objective = context.objective

        // Recent Progression
        val recentProgression = if (pastHistory.size >= 2) {
            val lastFour = pastHistory.take(4)
            val oldestInFour = lastFour.last()
            val newestInFour = lastFour.first()
            if (newestInFour.totalWeight > oldestInFour.totalWeight) {
                "Aumento de força e sobrecarga progressiva"
            } else if (newestInFour.totalWeight == oldestInFour.totalWeight) {
                "Consistência e estabilização de cargas"
            } else {
                "Foco em técnica e controle de volume"
            }
        } else {
            "Inicializando registros de carga"
        }

        // Accumulated Volume (real completed sets in 14 days, with fallback when exercisesJson is empty)
        val volumeAcumulado = pastHistory.filter { it.date >= fourteenDaysAgo }.sumOf { entry ->
            val parsed = try {
                JsonUtils.parseWorkoutExercises(entry.exercisesJson)
            } catch (_: Exception) {
                emptyList()
            }
            val realCompletedSets = parsed.sumOf { ex -> ex.sets.count { s -> s.completed } }
            if (realCompletedSets > 0) realCompletedSets else (entry.exercisesCompletedCount * 3)
        }

        val details = mutableListOf<String>()
        details.add("• Objetivo principal: ${objective.uppercase()}")
        details.add("• Experiência: $level")
        details.add("• Adesão recente: ${"%.0f".format(adherence)}% (${"%.1f".format(avgRealWeeklyFrequency)} de $targetWeeklyWorkouts treinos planejados/semana)")
        details.add("• Frequência real: ${"%.1f".format(avgRealWeeklyFrequency)} treinos por semana")
        details.add("• Recuperação percebida: ${"%.1f".format(recoveryProfile)}/10")
        details.add("• Tendência de fadiga: $fatigueTrend")
        details.add("• Volume acumulado de séries (14d): $volumeAcumulado séries")
        details.add("• Progressão recente: $recentProgression")

        val injured = context.injuredMuscles + context.excludedMuscles
        if (injured.isNotEmpty()) {
            details.add("• Restrições/Lesões: ${injured.joinToString(", ")}")
        }

        // Determine Decision based on RFC-REC-002 & RFC-REC-003 decision triggers & priority matrix
        return when {
            // Priority 1 (Critical): Safety (dor, limitações e risco de lesão)
            context.injuredMuscles.isNotEmpty() || context.constraints.lowerBackPain -> {
                val injuredLabel = if (context.injuredMuscles.isNotEmpty()) context.injuredMuscles.joinToString() else "região lombar"
                DecisionResult(
                    decision = "SUBSTITUTE_EXERCISE_SAFETY",
                    title = "SUBSTITUIR EXERCÍCIO POR SEGURANÇA",
                    description = "O motor identificou relato de dor ou restrição biomecânica ativa na região ($injuredLabel). A prioridade crítica do Max Performance é proteger as articulações afetadas e evitar o agravamento de processos inflamatórios.",
                    details = details,
                    nextAction = "Evite agachamentos livres profundos ou levantamento terra hoje. Substitua-os por leg press horizontal ou cadeira extensora, limitando a amplitude se necessário.",
                    suggestedSplit = "Divisão Atual (com adaptações)",
                    suggestedVolume = "Reduzido (-20% nas articulações afetadas)",
                    suggestedIntensity = "Reserva biomecânica (RIR Alvo: 3)",
                    ruleId = "DEC-101",
                    priorityLevel = "SEGURANÇA (P1)",
                    scoreImpact = 95,
                    scoreSecurity = 100,
                    scoreAdherence = 90,
                    scorePerformance = 40,
                    scoreFinal = 81,
                    blockedActions = listOf("Aumento de carga", "Uso de pesos livres axiais", "Exercícios unilaterais instáveis"),
                    exitCriteria = "Sessões consecutivas livres de queixas ou dores",
                    reversible = true
                )
            }

            // Priority 2 (Critical): Recovery and fatigue (Recovery < 50%)
            context.recoveryProfile < 5.0 || context.fatigueLevel > 7.5 -> {
                DecisionResult(
                    decision = "INSERT_DELOAD",
                    title = "INSERIR SEMANA DE DELOAD",
                    description = "O motor identificou que o seu índice de recuperação percebida está criticamente baixo (${"%.1f".format(recoveryProfile)}/10) com forte tendência de fadiga sistêmica ($fatigueTrend). Reduzir temporariamente o volume é indispensável para evitar overreaching não-funcional.",
                    details = details,
                    nextAction = "Corte o número de séries planejadas pela metade no próximo treino e use pesos 20% mais leves para restaurar tendões e glicogênio.",
                    suggestedSplit = "Divisão Atual (com adaptação de volume)",
                    suggestedVolume = "Reduzido (-50%)",
                    suggestedIntensity = "Deload (Reserva, RIR 3-4)",
                    ruleId = "DEC-102",
                    priorityLevel = "RECUPERAÇÃO (P2)",
                    scoreImpact = 90,
                    scoreSecurity = 95,
                    scoreAdherence = 85,
                    scorePerformance = 50,
                    scoreFinal = 80,
                    blockedActions = listOf("Aumento de carga", "Aumento de volume", "Aumento de frequência"),
                    exitCriteria = "Recuperação percebida > 70% por 3 sessões seguidas",
                    reversible = true
                )
            }

            // Priority 3 (High): 6 weeks consecutive but falling recovery/sleep
            context.fatigueLevel > 6.0 && context.recoveryProfile < 7.0 && pastHistory.size >= 12 -> {
                DecisionResult(
                    decision = "STABILIZATION_WEEK",
                    title = "SEMANA DE ESTABILIZAÇÃO DE CARGA",
                    description = "Você manteve consistência excelente por várias semanas consecutivas, mas os marcadores periféricos de sono e fadiga sugerem risco de estagnação eminente. Recomenda-se estagnar a progressão temporariamente.",
                    details = details,
                    nextAction = "Não adicione peso ou séries extras nesta sessão. Repita rigorosamente as mesmas variáveis do treino anterior para consolidar a técnica.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Estável",
                    suggestedIntensity = "Submáxima (RIR Alvo: 2)",
                    ruleId = "DEC-103",
                    priorityLevel = "RECUPERAÇÃO (P2)",
                    scoreImpact = 75,
                    scoreSecurity = 85,
                    scoreAdherence = 80,
                    scorePerformance = 70,
                    scoreFinal = 77,
                    blockedActions = listOf("Aumento de peso de trabalho", "Volume de séries extra"),
                    exitCriteria = "Estabilização e melhora na escala de fadiga",
                    reversible = true
                )
            }

            // Priority 4 (High): Failure to reach repetition target recurrently
            context.fatigueLevel > 5.0 && context.recoveryProfile < 6.0 -> {
                DecisionResult(
                    decision = "REDUCE_LOAD_RECOVERY",
                    title = "REDUZIR CARGA E REVISAR RECUPERAÇÃO",
                    description = "O motor detectou que você falhou em atingir a meta de repetições nas últimas sessões de treino. Insistir na mesma carga com fadiga acumulada prejudica o padrão de movimento e eleva o risco de lesões.",
                    details = details,
                    nextAction = "Reduza o peso de trabalho em 5% a 10% nas primeiras séries e priorize a amplitude e cadência perfeitas.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Estável",
                    suggestedIntensity = "RIR Alvo: 2 (Sem falha concêntrica)",
                    ruleId = "DEC-104",
                    priorityLevel = "RECUPERAÇÃO (P2)",
                    scoreImpact = 80,
                    scoreSecurity = 90,
                    scoreAdherence = 85,
                    scorePerformance = 60,
                    scoreFinal = 78,
                    blockedActions = listOf("Carga máxima", "Falha concêntrica na última série"),
                    exitCriteria = "Completar com sucesso todas as repetições alvo em 2 sessões consecutivas",
                    reversible = true
                )
            }

            // Priority 5 (High): Complete all targets with high RIR margin -> Increase load
            pastHistory.size >= 2 && recoveryProfile >= 8.0 && fatigueLevel < 4.0 -> {
                DecisionResult(
                    decision = "INCREASE_LOAD_INTENSITY",
                    title = "AUMENTAR CARGA DE TRABALHO",
                    description = "Sua recuperação fisiológica está espetacular (${"%.1f".format(recoveryProfile)}/10) e a fadiga está sob controle. O motor identificou margem mecânica para aumento gradual de sobrecarga de forma segura.",
                    details = details,
                    nextAction = "Suba de 2% a 5% da carga em exercícios isoladores (superiores) e de 5% a 10% em multiarticulares (inferiores).",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Manter Estável",
                    suggestedIntensity = "Alta (RIR Alvo: 1-2)",
                    ruleId = "DEC-105",
                    priorityLevel = "PROGRESSÃO (P5)",
                    scoreImpact = 60,
                    scoreSecurity = 80,
                    scoreAdherence = 95,
                    scorePerformance = 95,
                    scoreFinal = 82,
                    blockedActions = emptyList(),
                    exitCriteria = "RIR Alvo < 1 ou estagnação da velocidade concêntrica",
                    reversible = true
                )
            }

            // Priority 6 (High): Adherence dropping (Frequência real < planejada)
            adherence < 60.0 && targetWeeklyWorkouts >= 4 -> {
                DecisionResult(
                    decision = "RECALCULATE_SPLIT",
                    title = "RECALCULAR DIVISÃO POR ADERÊNCIA",
                    description = "Sua frequência real média recente está em ${"%.1f".format(avgRealWeeklyFrequency)} treinos/semana, o que representa uma adesão de ${"%.0f".format(adherence)}% em relação à sua meta de $targetWeeklyWorkouts dias. Ajustar o plano à sua realidade semanal é mais produtivo.",
                    details = details,
                    nextAction = "Mude temporariamente sua meta de dias semanais para ${targetWeeklyWorkouts - 1} nas configurações para manter consistência sem culpa.",
                    suggestedSplit = "Full Body ou Upper / Lower (AB)",
                    suggestedVolume = "Estável por sessão",
                    suggestedIntensity = "Foco Técnico",
                    ruleId = "DEC-106",
                    priorityLevel = "ADERÊNCIA (P3)",
                    scoreImpact = 85,
                    scoreSecurity = 80,
                    scoreAdherence = 100,
                    scorePerformance = 65,
                    scoreFinal = 82,
                    blockedActions = listOf("Treinos com alto volume acessório", "Divisões volumosas ou fragmentadas (ABCDE)"),
                    exitCriteria = "Frequência real semanal igualar ou superar a meta por 3 semanas",
                    reversible = true
                )
            }

            // Priority 7 (Medium): Changed availability of days
            targetWeeklyWorkouts in 1..3 && pastHistory.isNotEmpty() -> {
                DecisionResult(
                    decision = "MIGRATE_SPLIT_DAYS",
                    title = "MIGRAR ESTRUTURA DE DIVISÃO",
                    description = "Com a rotina compactada para $targetWeeklyWorkouts dias, o motor reorganizou automaticamente os agrupamentos musculares para otimizar a distribuição do estímulo semanal.",
                    details = details,
                    nextAction = "Adote uma divisão do tipo Full Body ou AB, aproveitando que haverá mais dias de descanso entre as sessões.",
                    suggestedSplit = if (targetWeeklyWorkouts <= 2) "Full Body" else "Upper / Lower (AB)",
                    suggestedVolume = "Volume Consolidado (+15% por sessão)",
                    suggestedIntensity = "RIR Alvo: 1-2",
                    ruleId = "DEC-107",
                    priorityLevel = "ADERÊNCIA (P3)",
                    scoreImpact = 80,
                    scoreSecurity = 85,
                    scoreAdherence = 95,
                    scorePerformance = 70,
                    scoreFinal = 82,
                    blockedActions = emptyList(),
                    exitCriteria = "Alteração manual da disponibilidade de dias na rotina",
                    reversible = true
                )
            }

            // Priority 8 (Medium): Busy gym constraint
            context.constraints.busyGym -> {
                DecisionResult(
                    decision = "SUBSTITUTE_EQUIPMENT_BUSY",
                    title = "ROTINA PARA ACADEMIA LOTADA",
                    description = "O motor identificou restrição operacional de lotação de equipamentos hoje. Prioridade máxima em manter o fluxo do treino contínuo, reduzindo filas.",
                    details = details,
                    nextAction = "Troque exercícios de polias ou aparelhos concorridos por pesos livres (halteres) correspondentes ou máquinas vazias.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Estável",
                    suggestedIntensity = "Foco em Tempo sob Tensão (RIR: 1-2)",
                    ruleId = "DEC-108",
                    priorityLevel = "DISPONIBILIDADE (P4)",
                    scoreImpact = 70,
                    scoreSecurity = 90,
                    scoreAdherence = 95,
                    scorePerformance = 60,
                    scoreFinal = 78,
                    blockedActions = listOf("Polias conjugadas", "Uso de bi-sets concorridos", "Super-séries em múltiplos aparelhos"),
                    exitCriteria = "Sair do horário de pico de lotação",
                    reversible = true
                )
            }

            // Priority 9 (Medium): Beginner Express
            level == "Iniciante" && context.availableTime <= 40 -> {
                DecisionResult(
                    decision = "BEGINNER_EXPRESS",
                    title = "GERAR TREINO INICIANTE EXPRESS",
                    description = "Você possui limite rígido de tempo (${context.availableTime} minutos) e nível de experiência $level. O motor priorizou exercícios multiarticulares eficientes com máquinas estáveis.",
                    details = details,
                    nextAction = "Realize os exercícios em ordem linear com descanso máximo de 60 segundos entre as séries.",
                    suggestedSplit = "Full Body Compacto",
                    suggestedVolume = "Reduzido (-30% volume acessório)",
                    suggestedIntensity = "Foco Técnico (RIR Alvo: 2)",
                    ruleId = "DEC-109",
                    priorityLevel = "ADERÊNCIA (P3)",
                    scoreImpact = 75,
                    scoreSecurity = 95,
                    scoreAdherence = 95,
                    scorePerformance = 50,
                    scoreFinal = 78,
                    blockedActions = listOf("Volume acessório", "Técnicas avançadas (Dropsets, Rest-pause)"),
                    exitCriteria = "Dispor de tempo livre de treino > 40 minutos",
                    reversible = true
                )
            }

            // Priority 10 (Medium): Volume adjustment due to fatigue trend
            volumeAcumulado > 30 && fatigueLevel > 6.5 -> {
                DecisionResult(
                    decision = "REDUCE_VOLUME_FATIGUE",
                    title = "REDUÇÃO ESTRATÉGICA DE VOLUME",
                    description = "Seu volume acumulado de séries está elevado ($volumeAcumulado séries nos últimos 14 dias) acompanhado de sintomas latentes de cansaço periférico. Recomenda-se reduzir as séries preservando as cargas.",
                    details = details,
                    nextAction = "Retire 1 série de cada exercício acessório e mantenha as séries de trabalho principais intactas.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Reduzido (-25%)",
                    suggestedIntensity = "Alta (Manter sobrecarga)",
                    ruleId = "DEC-110",
                    priorityLevel = "RECUPERAÇÃO (P2)",
                    scoreImpact = 75,
                    scoreSecurity = 90,
                    scoreAdherence = 80,
                    scorePerformance = 65,
                    scoreFinal = 77,
                    blockedActions = listOf("Séries extras de isolamento", "Inclusão de novos exercícios"),
                    exitCriteria = "Redução nos marcadores de dor muscular tardia e fadiga crônica",
                    reversible = true
                )
            }

            // Priority 11 (Low): Monotony combat after several weeks
            pastHistory.size >= 24 -> {
                DecisionResult(
                    decision = "VARIATION_ACCESSORY",
                    title = "TROCAR EXERCÍCIOS ACESSÓRIOS",
                    description = "Você completou mais de 24 treinos no mesmo planejamento. O motor identificou saturação psicológica nos movimentos acessórios e recomenda variabilidade neuromuscular simples.",
                    details = details,
                    nextAction = "Troque os últimos 2 exercícios de isolamento da sua ficha atual por variações biomecanicamente equivalentes.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Estável",
                    suggestedIntensity = "Alta (RIR Alvo: 1)",
                    ruleId = "DEC-111",
                    priorityLevel = "OTIMIZAÇÃO (P6)",
                    scoreImpact = 45,
                    scoreSecurity = 85,
                    scoreAdherence = 90,
                    scorePerformance = 65,
                    scoreFinal = 71,
                    blockedActions = emptyList(),
                    exitCriteria = "Fim da janela de adaptação do bloco de variação (4 semanas)",
                    reversible = true
                )
            }

            // Priority 12 (Low): New training block after 8 weeks
            pastHistory.size >= 30 && fatigueLevel < 3.5 && recoveryProfile >= 8.0 -> {
                DecisionResult(
                    decision = "NEW_TRAINING_BLOCK",
                    title = "INICIAR NOVO BLOCO DE TREINAMENTO",
                    description = "Você atingiu um marco fantástico de consistência de 30+ treinos sem fadiga. Hora de evoluir a periodização mudando a faixa de repetições ou foco de volume/intensidade.",
                    details = details,
                    nextAction = "Troque de ficha e alterne a faixa de trabalho de hipertrofia tensional para metabólica (ex.: mude de 6-8 para 12-15 repetições).",
                    suggestedSplit = "Alterar ou Renovar Estrutura",
                    suggestedVolume = "Novo Bloco (+10% progressivo)",
                    suggestedIntensity = "Alta (RIR Alvo: 1)",
                    ruleId = "DEC-112",
                    priorityLevel = "PROGRESSÃO (P5)",
                    scoreImpact = 80,
                    scoreSecurity = 85,
                    scoreAdherence = 90,
                    scorePerformance = 90,
                    scoreFinal = 86,
                    blockedActions = emptyList(),
                    exitCriteria = "Fim do período de acumulação do novo bloco (8-12 semanas)",
                    reversible = false
                )
            }

            // Priority 13 (Low): Fallback / Default Continue Strategy
            else -> {
                DecisionResult(
                    decision = "CONTINUE_STRATEGY",
                    title = "CONSERVAR ESTRATÉGIA ATUAL",
                    description = "Todos os indicadores de volume, adesão (${"%.0f".format(adherence)}%), fadiga e recuperação estão excelentes. Não há necessidade de alteração estrutural no seu planejamento.",
                    details = details,
                    nextAction = "Mantenha o treino atual e busque o aumento progressivo de repetições ou carga de forma gradual e lenta.",
                    suggestedSplit = "Divisão Atual",
                    suggestedVolume = "Manter Estável",
                    suggestedIntensity = "Alta (RIR Alvo: 1-2)",
                    ruleId = "DEC-113",
                    priorityLevel = "OTIMIZAÇÃO (P6)",
                    scoreImpact = 50,
                    scoreSecurity = 90,
                    scoreAdherence = 85,
                    scorePerformance = 80,
                    scoreFinal = 76,
                    blockedActions = emptyList(),
                    exitCriteria = "Disparo de qualquer gatilho das camadas críticas ou altas",
                    reversible = true
                )
            }
        }
    }

    // Helper compatibility method
    fun generateWorkout(legacyContext: RecommendationContext): Workout {
        val mappedContext = WorkoutContext(
            durationMinutes = legacyContext.timeAvailableMinutes,
            objective = legacyContext.objective,
            gymProfile = null,
            technique = "Linear",
            userPreferences = UserPreferences(
                level = legacyContext.userLevel,
                preferredFocus = legacyContext.objective
            ),
            targetMuscles = listOf(legacyContext.targetMuscleGroup),
            availableEquipment = legacyContext.availableEquipment,
            excludedMuscles = legacyContext.restrictions,
            recoveryProfile = legacyContext.recoveryIndex
        )
        return generateWorkout(mappedContext)
    }

    /**
     * Primary workout generation engine utilizing WorkoutContext.
     */
    fun generateWorkout(context: WorkoutContext): Workout {
        val (workout, _) = generateWorkoutWithReport(context)
        return workout
    }

    /**
     * Highly detailed generation with structured rule evaluation, report building, and telemetry.
     */
    fun generateWorkoutWithReport(context: WorkoutContext): Pair<Workout, RecommendationReport> {
        val targetMuscleGroup = context.targetMuscles.firstOrNull() ?: "Peito"
        val targetCluster = getClusterForTargetGroup(targetMuscleGroup) ?: Cluster.EMPURRAO

        var attempt = 1
        var workoutPair: Pair<Workout, RecommendationReport>? = null
        var lastCandidatePair: Pair<Workout, RecommendationReport>? = null
        var validationSuccess = false

        val request = WorkoutRequest(
            cluster = targetCluster,
            duration = context.durationMinutes,
            level = context.experience,
            context = getGymContext(context.gymProfile),
            preferredExercises = context.preferredExercises
        )

        while (attempt <= 3 && !validationSuccess) {
            try {
                val candidatePair = generateWorkoutWithReportInternal(context, attempt, targetCluster)
                lastCandidatePair = candidatePair
                val workout = candidatePair.first
                
                when (val validation = finalValidation(workout, request)) {
                    is ValidationResult.Valid -> {
                        workoutPair = candidatePair
                        validationSuccess = true
                    }
                    is ValidationResult.Invalid -> {
                        println("DIAGNOSTIC - Attempt $attempt failed validation: ${validation.errors}")
                        attempt++
                    }
                }
            } catch (e: Exception) {
                println("DIAGNOSTIC - Attempt $attempt failed with exception: ${e.message}")
                e.printStackTrace()
                attempt++
            }
        }

        if (!validationSuccess || workoutPair == null) {
            if (lastCandidatePair != null && lastCandidatePair.first.exercises.isNotEmpty()) {
                workoutPair = lastCandidatePair
            } else {
                throw IllegalStateException(
                    "Não conseguimos montar um treino ideal com essas configurações.\n" +
                    "Sugestões:\n" +
                    "• Aumentar o tempo disponível\n" +
                    "• Escolher outro grupo muscular\n" +
                    "• Verificar se o perfil de equipamento está correto"
                )
            }
        }

        return workoutPair
    }

    /**
     * Internal implementation of the workout generation.
     */
    fun generateWorkoutWithReportInternal(
        context: WorkoutContext,
        attempt: Int = 1,
        forceCluster: Cluster? = null
    ): Pair<Workout, RecommendationReport> {
        val telemetryEventsLogged = mutableListOf<String>()

        // 1. Evaluate Rule Engine
        val ruleEngine = RuleEngine()
        val ruleState = ruleEngine.evaluate(context)

        // 2. Fetch Objective from Knowledge Base
        val objectiveId = context.objective.lowercase().trim()
        val objectiveObj = TrainingKnowledgeBase.Objectives.getById(objectiveId)
            ?: TrainingKnowledgeBase.Objectives.getById(
                when (objectiveId) {
                    "força" -> "strength"
                    "hipertrofia" -> "hypertrophy"
                    "emagrecimento", "definição", "definicão", "definition" -> "weight_loss"
                    "resistência" -> "endurance"
                    "manutenção" -> "maintenance"
                    else -> "hypertrophy"
                }
            )

        // Objective parameter changes automatically
        val targetRIR = objectiveObj?.recommendedRIR ?: "0-2 RIR"
        val recommendedCardioStrategy = objectiveObj?.recommendedCardio ?: "Moderado"

        // 3. Resolve Target Muscle Group and available equipments
        val targetMuscleGroup = context.targetMuscles.firstOrNull() ?: "Peito"
        val availableEquipments = context.availableEquipment.ifEmpty {
            context.gymProfile?.let { gym ->
                val list = mutableListOf<String>()
                if (gym.hasOlympicBar) list.add("Barra")
                if (gym.hasDumbbells) list.add("Halteres")
                if (gym.hasCables) list.add("Polia")
                if (gym.hasMachines) list.add("Máquina")
                if (gym.hasSmith) list.add("Smith")
                if (gym.hasHack) list.add("Hack")
                if (gym.hasLegPress) list.add("Leg Press")
                list
            } ?: listOf("Barra", "Halteres", "Polia", "Máquina", "Peso Corporal")
        }

        // 4. Select initial candidate exercises using ExerciseSelector
        val exclusions = context.excludedMuscles + context.injuredMuscles
        val mainSelected = exerciseSelector.selectExercises(
            muscleGroup = targetMuscleGroup,
            equipmentList = availableEquipments,
            timeAvailableMinutes = context.durationMinutes,
            restrictions = exclusions
        )

        val targetCluster = getClusterForTargetGroup(targetMuscleGroup) ?: Cluster.EMPURRAO

        // Use role-composed exercises from ExerciseSelector as primary candidates.
        // Fallback candidates must strictly respect equipment availability and target cluster.
        val normalizedEquips = availableEquipments.map { it.lowercase().trim() }
        val otherClusterExs = com.example.constants.TemplatesAndExercises.ALL_EXERCISES.filter { ex ->
            !mainSelected.any { sel -> sel.id == ex.id } &&
            (ex.cluster == targetCluster) &&
            (normalizedEquips.isEmpty() ||
             normalizedEquips.contains("todos") ||
             ex.equipment.lowercase().trim() == "peso corporal" ||
             normalizedEquips.contains(ex.equipment.lowercase().trim()) ||
             normalizedEquips.any { av -> ex.equipment.lowercase().contains(av) || av.contains(ex.equipment.lowercase()) }) &&
            !exclusions.any { r -> r.isNotEmpty() && (ex.name.lowercase().contains(r.lowercase()) || ex.category.lowercase().contains(r.lowercase())) }
        }
        val preferredObjects = com.example.constants.TemplatesAndExercises.ALL_EXERCISES.filter { 
            context.preferredExercises.contains(it.id) 
        }
        var selectedExercises = (preferredObjects + mainSelected + otherClusterExs).distinctBy { it.id }

        // SPRINT 1: Recompose within the same cluster if validation failed previously
        if (attempt > 1 && forceCluster != null) {
            selectedExercises = selectedExercises.filter { it.cluster == forceCluster }
        }

        // 5. Equipment Fallback Swaps conforming to availability
        val mappedCandidateExercises = selectedExercises.map { originalExercise ->
            val finalExercise = findEquipmentFallbackExercise(originalExercise, availableEquipments)
            if (finalExercise.id != originalExercise.id) {
                ruleState.explanations.add("Smith escolhido: Barra indisponível")
                telemetryEventsLogged.add("EquipmentFallback: ${originalExercise.name} -> ${finalExercise.name}")
                try {
                    Telemetry.log(TelemetryEventType.EQUIPMENT_FALLBACK, mapOf(
                        "original" to originalExercise.name,
                        "fallback" to finalExercise.name
                    ))
                } catch (e: Throwable) {
                    // Safe print in JVM tests where android.util.Log is not mocked
                    println("Telemetry fallback: ${originalExercise.name} -> ${finalExercise.name}")
                }
            }
            finalExercise
        }.distinctBy { it.id }

        // 6. Consult RecoveryPlanner and adjust actions
        val recoveryActions = recoveryPlanner.assessRecovery(context.recoveryProfile)
        if (context.recoveryProfile < 5.0) {
            telemetryEventsLogged.add("RecoveryReducedVolume")
            try {
                Telemetry.log(TelemetryEventType.RECOVERY_REDUCED_VOLUME, mapOf(
                    "recoveryProfile" to context.recoveryProfile
                ))
            } catch (e: Throwable) {
                println("Telemetry: Recovery volume reduction logged")
            }
        }

        // 7. Consult VolumePlanner
        val volumeResult = volumePlanner.calculateVolume(
            objective = context.objective,
            timeAvailableMinutes = context.durationMinutes,
            level = context.experience,
            recoveryIndex = context.recoveryProfile
        )

        // HOTFIX ENG-002: Piso reforçado por UserLevelConfig
        val userLevel = context.experience.ifBlank { context.userPreferences.level.ifBlank { "Avançado" } }
        val levelConfig = UserLevelConfigs.getByLevel(userLevel)

        // Logar o que está sendo lido pra debugar problemas de fluxo de dados
        android.util.Log.i("MAX_PERFORMANCE_DEBUG", "generateWorkout: experience='${context.experience}' " +
            "resolvedLevel='${levelConfig.level}' minMain=${levelConfig.minMainExercises}")

        val expectedRange = getExpectedExerciseCount(context.durationMinutes, userLevel)
        val extraExercisesCount = 1 + 1 + (if (context.cardioPreference != "Nenhum") 1 else 0)
        val minMainExercises = levelConfig.minMainExercises  // piso vem do UserLevelConfig
        val maxMainExercises = levelConfig.maxMainExercises  // teto vem do UserLevelConfig

        var targetExercisesCount = (volumeResult.exercisesCount * recoveryActions.volumeAdjustmentPercent).roundToInt()
        // Aplicar piso E teto do UserLevelConfig (não mais do expectedRange)
        targetExercisesCount = targetExercisesCount.coerceIn(minMainExercises, maxMainExercises)

        // SPRINT 3: Monotony and Equipment Harmony
        val gymContext = getGymContext(context.gymProfile)
        val optimizedExercises = optimizeMonotony(mappedCandidateExercises, targetExercisesCount, gymContext, context.preferredExercises)
        
        // SPRINT — TRAINING ENGINE 001: Sequence according to 10 physiological & gym flow rules
        val engine001Ordered = try {
            com.example.services.recommendation.engine001.TrainingEngine001.applyRules(optimizedExercises ?: emptyList(), targetMuscleGroup ?: "")
        } catch (e: Exception) {
            android.util.Log.e("RECOMMENDATION_ENGINE", "Error executing TrainingEngine001, falling back to optimized exercises", e)
            optimizedExercises ?: mappedCandidateExercises ?: emptyList()
        }

        // HOTFIX 7: Re-validate result list from TrainingEngine001
        var exercisesToUse = (if (!engine001Ordered.isNullOrEmpty()) engine001Ordered else (optimizedExercises ?: mappedCandidateExercises ?: emptyList()))
            .filterNotNull()
            .filter { !it.name.isNullOrBlank() }
            .distinctBy { if (it.id.isNotBlank()) it.id else it.name }
            .toMutableList()

        if (exercisesToUse.isEmpty() && !mappedCandidateExercises.isNullOrEmpty()) {
            exercisesToUse = mappedCandidateExercises
                .filterNotNull()
                .filter { !it.name.isNullOrBlank() }
                .distinctBy { if (it.id.isNotBlank()) it.id else it.name }
                .toMutableList()
        }

        // Force include preferred exercises if requested and missing
        if (context.preferredExercises.isNotEmpty()) {
            val preferredObjs = com.example.constants.TemplatesAndExercises.ALL_EXERCISES.filter { 
                context.preferredExercises.contains(it.id) 
            }
            for (pref in preferredObjs) {
                if (!exercisesToUse.any { it.id == pref.id || it.name.equals(pref.name, true) }) {
                    exercisesToUse.add(0, pref)
                }
            }
        }

        // 8. Consult ProgressionPlanner
        val progressionResult = progressionPlanner.suggestProgression(
            objective = context.objective,
            experience = context.experience
        )

        // 9. Consult IntensityPlanner and filter according to safety
        val intensityResult = intensityPlanner.suggestIntensityMethods(
            objective = context.objective,
            experience = context.experience
        )
        val safeIntensityMethods = intensityResult.recommendedMethods.filter {
            !recoveryActions.avoidMethods.contains(it) &&
            !(it == "cluster" && !ruleState.allowClusterSets) &&
            !(it == "top_set" && ruleState.avoidTopSet)
        }
        val preferredMethodId = ruleState.preferredIntensityMethod ?: safeIntensityMethods.firstOrNull() ?: "straight_sets"
        val preferredMethodObj = TrainingKnowledgeBase.IntensityMethods.getById(preferredMethodId)
        val preferredMethodName = preferredMethodObj?.name ?: "Straight Sets"

        telemetryEventsLogged.add("MethodSelected: $preferredMethodName")
        try {
            Telemetry.log(TelemetryEventType.METHOD_SELECTED, mapOf("method" to preferredMethodName))
        } catch (e: Throwable) {
            println("Telemetry: Method selected: $preferredMethodName")
        }

        // 10. Time Adaptive Limits: reduce accessories, then sets
        var finalSetsPerExercise = min(volumeResult.setsPerExercise, ruleState.maxSetsPerExercise)
        var restSeconds = restPlanner.calculateRestTime(
            objective = context.objective,
            methodId = preferredMethodId,
            exercise = exercisesToUse.firstOrNull() ?: Exercise("dummy", "Dummy", "Peito"),
            recoveryIndex = context.recoveryProfile
        ) + recoveryActions.restAdjustmentSeconds

        var totalTimeSeconds = timePlanner.calculateTotalWorkoutTimeSeconds(
            exercises = exercisesToUse,
            setsPerExercise = finalSetsPerExercise,
            restTimeSeconds = restSeconds
        )

        fun isProtectedCalfOrTraps(ex: Exercise): Boolean {
            val n = ex.name.lowercase()
            val c = ex.category.lowercase()
            return n.contains("panturrilha") || n.contains("gêmeos") || n.contains("gemeos") ||
                n.contains("encolhimento") || n.contains("trapézio") || n.contains("trapezio") ||
                n.contains("face pull") || n.contains("remada alta") ||
                c.contains("panturrilha") || c.contains("trapézio") || c.contains("trapezio")
        }

        val maxAllowedTimeSeconds = context.durationMinutes * 60
        if (totalTimeSeconds > maxAllowedTimeSeconds) {
            // SPRINT ENG-006: Insufficient Time -> Reduce accessory/isolation exercises first, keeping compounds AND Panturrilha/Trapézio
            val compoundsOrProtected = exercisesToUse.filter {
                it.compound || it.exerciseType.lowercase() == "composto" || isProtectedCalfOrTraps(it)
            }
            if (compoundsOrProtected.isNotEmpty() && compoundsOrProtected.size < exercisesToUse.size) {
                val discardedCount = exercisesToUse.size - compoundsOrProtected.size
                val allowedDiscard = exercisesToUse.size - minMainExercises
                if (allowedDiscard > 0) {
                    val toDiscard = min(discardedCount, allowedDiscard)
                    val accessories = exercisesToUse.filter {
                        !it.compound && it.exerciseType.lowercase() != "composto" && !isProtectedCalfOrTraps(it)
                    }
                    val accessoriesToRemove = accessories.takeLast(toDiscard)
                    exercisesToUse.removeAll(accessoriesToRemove)

                    telemetryEventsLogged.add("ExerciseDiscarded: ${accessoriesToRemove.size} accessories")
                    try {
                        Telemetry.log(TelemetryEventType.EXERCISE_DISCARDED, mapOf("count" to accessoriesToRemove.size))
                    } catch (e: Throwable) {
                        println("Telemetry: Exercises discarded: ${accessoriesToRemove.size}")
                    }
                    totalTimeSeconds = timePlanner.calculateTotalWorkoutTimeSeconds(
                        exercises = exercisesToUse,
                        setsPerExercise = finalSetsPerExercise,
                        restTimeSeconds = restSeconds
                    )
                }
            }
        }

        // If still too long, reduce sets per exercise
        while (totalTimeSeconds > maxAllowedTimeSeconds && finalSetsPerExercise > 2) {
            finalSetsPerExercise--
            totalTimeSeconds = timePlanner.calculateTotalWorkoutTimeSeconds(
                exercises = exercisesToUse,
                setsPerExercise = finalSetsPerExercise,
                restTimeSeconds = restSeconds
            )
        }

        // HOTFIX ENG-002: Compressão inteligente — remove ISOLADORES antes de COMPOSTOS (protegendo Panturrilha e Trapézio)
        while (totalTimeSeconds > maxAllowedTimeSeconds && exercisesToUse.size > minMainExercises) {
            val isolationIndex = (exercisesToUse.size - 1 downTo 0).firstOrNull { idx ->
                val ex = exercisesToUse[idx]
                !ex.compound && ex.exerciseType.lowercase() != "composto" && !isProtectedCalfOrTraps(ex)
            }
            val toRemove = isolationIndex ?: break
            if (toRemove in 0 until exercisesToUse.size) {
                android.util.Log.d("MAX_PERFORMANCE_DEBUG", "Compressão: removendo '${exercisesToUse[toRemove].name}' (compound=${exercisesToUse[toRemove].compound})")
                exercisesToUse.removeAt(toRemove)
            } else {
                break
            }
            totalTimeSeconds = timePlanner.calculateTotalWorkoutTimeSeconds(
                exercises = exercisesToUse,
                setsPerExercise = finalSetsPerExercise,
                restTimeSeconds = restSeconds
            )
        }

        // 11. Suggest initial carga (weight) based on History or safety
        // Caso exista histórico -> sugerir carga inicial. Caso não exista -> zero carga.
        val lastWorkoutExercises = context.lastWorkout?.let {
            try {
                JsonUtils.parseWorkoutExercises(it.exercisesJson)
            } catch (e: Exception) {
                emptyList<WorkoutExercise>()
            }
        } ?: emptyList()

        // 12. Build Workout using WorkoutBuilder (using structured blocks)
        // Aquecimento -> Principal -> Acessórios -> Cardio -> Alongamento
        val builder = WorkoutBuilder()
        val displayObjName = if (context.objective.isNotBlank()) context.objective else (objectiveObj?.name ?: "Personalizado")
        val finalWorkoutName = when (targetMuscleGroup.lowercase().trim()) {
            "full body a" -> "Full Body A — Força Geral (Pernas Moderado)"
            "full body b" -> "Full Body B — Ênfase Tronco (Upper + Pernas Leve)"
            "full body c" -> "Full Body C — Ênfase Lower / Posterior"
            else -> "Treino $displayObjName - $targetMuscleGroup"
        }
        builder.setName(finalWorkoutName)

        // Compile rationale description
        val descBuilder = StringBuilder()
        descBuilder.append("Treino planejado cientificamente com base no seu WorkoutContext.\n\n")
        descBuilder.append("• Objetivo: ${objectiveObj?.name ?: "Geral"} (RIR Alvo: $targetRIR)\n")
        descBuilder.append("• Recuperação: ${recoveryActions.recommendationText}\n")
        descBuilder.append("• Método de Intensidade: $preferredMethodName\n")
        descBuilder.append("• Progressão: ${progressionResult.displayName}\n")
        descBuilder.append("• Racional de Progressão: ${progressionResult.reason}\n")
        descBuilder.append("• Tempo Estimado: ${totalTimeSeconds / 60} minutos\n")
        if (ruleState.explanations.isNotEmpty()) {
            descBuilder.append("\nAdaptações efetuadas:\n")
            ruleState.explanations.distinct().forEach { descBuilder.append("  - $it\n") }
        }
        builder.setDescription(descBuilder.toString())

        // Helper for minimum equipment weight
        fun defaultStartingWeightForEquipment(equipment: String): Float {
            val eq = equipment.lowercase()
            return when {
                eq.contains("barra") || eq.contains("smith") || eq.contains("barbell") -> 20.0f // Barra olímpica vazia (20kg)
                eq.contains("halter") || eq.contains("dumbbell") -> 4.0f // Par de halteres (4kg)
                eq.contains("máquina") || eq.contains("maquina") || eq.contains("machine") -> 5.0f
                eq.contains("polia") || eq.contains("cabo") || eq.contains("cable") -> 5.0f
                eq.contains("peso corporal") || eq.contains("bodyweight") -> 0.0f
                else -> 5.0f
            }
        }

        // Add Aquecimento (Warmup) Block
        if (context.includeWarmup) {
            val mainCompound = exercisesToUse.firstOrNull { it.compound || it.exerciseType.lowercase() == "composto" } ?: exercisesToUse.firstOrNull()
            if (mainCompound != null) {
                val mainHistWeight = com.example.data.repository.ExerciseHistoryRepository.getSuggestedWeightForExercise(
                    history = context.history,
                    exerciseNameOrId = mainCompound.name.ifEmpty { mainCompound.id },
                    equipment = mainCompound.equipment,
                    gymProfile = context.gymProfile,
                    progressionType = "Manutenção"
                )
                val defaultWarmup = defaultStartingWeightForEquipment(mainCompound.equipment)
                val warmupWeight = com.example.utils.WeightCalculator.roundToAvailableWeight(
                    weight = if (mainHistWeight > defaultWarmup) mainHistWeight * 0.5f else defaultWarmup,
                    equipment = mainCompound.equipment,
                    gymProfile = context.gymProfile
                )
                builder.addExercise(
                    exercise = mainCompound,
                    setsCount = 1,
                    repsRange = "20",
                    weight = warmupWeight,
                    restTimeSeconds = 45,
                    progressionType = "Aquecimento",
                    blockType = "Aquecimento"
                )
            }
        }

        // Add Principal (Main) and Acessórios (Accessories) Blocks
        val isFullBodyTarget = targetMuscleGroup.lowercase().trim().startsWith("full body")
        val sortedExercisesToUse = if (isFullBodyTarget) {
            if (exercisesToUse.size > 9) exercisesToUse.take(9) else exercisesToUse
        } else {
            exercisesToUse.sortedByDescending { it.neuralDemandIndex }
        }
        for ((exIndex, exercise) in sortedExercisesToUse.withIndex()) {
            val isAccessory = !exercise.compound && exercise.exerciseType.lowercase() != "composto"
            val blockType = if (isAccessory) "Acessórios" else "Principal"

            val repsRange = when (context.objective.lowercase().trim()) {
                "força", "strength" -> "6"
                "definição", "definicão", "definition", "resistência", "endurance", "weight_loss", "emagrecimento" -> "15"
                "hipertrofia", "hypertrophy" -> "10"
                else -> exercise.recommendedRepRange.ifEmpty { "10" }
            }

            val isLowerBody = exercise.compound && (exercise.category.lowercase().contains("perna") || exercise.category.lowercase().contains("glúteo"))
            val suggestedWeight = com.example.data.repository.ExerciseHistoryRepository.getSuggestedWeightForExercise(
                history = context.history,
                exerciseNameOrId = exercise.name.ifEmpty { exercise.id },
                equipment = exercise.equipment,
                gymProfile = context.gymProfile,
                progressionType = progressionResult.displayName,
                targetReps = repsRange.toIntOrNull() ?: 10,
                isLowerBodyCompound = isLowerBody
            )

            val setsForThisExercise = if (isFullBodyTarget) {
                if (exIndex == 0) 4 else 3
            } else {
                finalSetsPerExercise
            }

            builder.addExercise(
                exercise = exercise,
                setsCount = setsForThisExercise,
                repsRange = repsRange,
                weight = suggestedWeight,
                restTimeSeconds = restSeconds,
                progressionType = progressionResult.displayName,
                blockType = blockType
            )
        }

        // Add Cardio Block if requested
        if (context.cardioPreference != "Nenhum") {
            val dummyCardio = Exercise("cardio_session", "Cardiovascular Session", "Cardio", equipment = "Peso Corporal")
            builder.addExercise(
                exercise = dummyCardio,
                setsCount = 1,
                repsRange = "1",
                weight = 0f,
                restTimeSeconds = 0,
                progressionType = "Cardio",
                blockType = "Cardio",
                executionType = "TEMPO"
            )
        }

        // Add Alongamento (Stretch) Block only if explicitly requested
        if (context.objective.equals("Alongamento", ignoreCase = true) || context.targetMuscles.any { it.equals("Alongamento", ignoreCase = true) }) {
            val dummyStretch = Exercise("stretch_session", "Alongamento Geral", "Alongamento", equipment = "Peso Corporal")
            builder.addExercise(
                exercise = dummyStretch,
                setsCount = 1,
                repsRange = "1",
                weight = 0f,
                restTimeSeconds = 0,
                progressionType = "Alongamento",
                blockType = "Alongamento",
                executionType = "TEMPO"
            )
        }

        val workout = com.example.data.repository.WorkoutProtocolsRepository.ensureCalfAndTrapsForFullBody(builder.build())
        telemetryEventsLogged.add("WorkoutGenerated: ${workout.id}")
        try {
            Telemetry.log(TelemetryEventType.WORKOUT_GENERATED, mapOf("id" to workout.id, "name" to workout.name))
        } catch (e: Throwable) {
            println("Telemetry: Workout generated: ${workout.id}")
        }

        // 13. Construct RecommendationReport
        val report = RecommendationReport(
            workoutId = workout.id,
            durationMinutes = context.durationMinutes,
            objective = context.objective,
            triggeredRules = ruleState.triggeredRules,
            explanations = ruleState.explanations.distinct(),
            telemetryEventsLogged = telemetryEventsLogged
        )

        return Pair(workout, report)
    }

    /**
     * Estrutura a família Full Body em 3 fichas concorrentes para treino híbrido (Musculação + Corrida):
     * - Full Body A: Força Geral / Pernas Moderado
     * - Full Body B: Ênfase Tronco/Upper + Pernas Leve pós-corrida
     * - Full Body C: Ênfase Lower / Posterior
     */
    fun generateFullBodyVariant(
        variant: String,
        history: List<com.example.data.model.WorkoutHistoryEntry> = emptyList(),
        gymProfile: com.example.data.model.GymProfile? = null
    ): Workout {
        val cleanVariant = when (variant.uppercase().trim()) {
            "B", "FULL BODY B" -> "B"
            "C", "FULL BODY C" -> "C"
            else -> "A"
        }
        val targetGroup = "Full Body $cleanVariant"
        val selectedExercises = exerciseSelector.selectExercises(
            muscleGroup = targetGroup,
            equipmentList = listOf("Barra", "Halteres", "Polia", "Máquina", "Leg Press", "Hack", "Smith", "Peso Corporal"),
            timeAvailableMinutes = 60,
            restrictions = emptyList()
        ).take(9)
        val (workoutId, workoutName, workoutDesc) = when (cleanVariant) {
            "B" -> Triple(
                "full_body_b",
                "Full Body B — Ênfase Tronco (Upper + Pernas Leve)",
                "Ênfase em tronco e membros superiores com volume reduzido de membros inferiores para recuperação articular pós-corrida."
            )
            "C" -> Triple(
                "full_body_c",
                "Full Body C — Ênfase Lower / Posterior",
                "Ênfase em cadeia posterior, glúteos, unilateral moderado e compostos superiores."
            )
            else -> Triple(
                "full_body_a",
                "Full Body A — Força Geral (Pernas Moderado)",
                "Estímulo global equilibrado de força e hipertrofia com volume moderado de membros inferiores."
            )
        }

        val workoutExercises = selectedExercises.mapIndexed { idx, ex ->
            val setsCount = if (idx == 0) 4 else 3
            val repsTarget = if (idx == 0) 8 else 10
            val inferredEq = com.example.utils.WeightCalculator.inferEquipment(ex.name, ex.equipment)
            val suggestedLoad = com.example.data.repository.ExerciseHistoryRepository.getSuggestedWeightForExercise(
                history = history,
                exerciseNameOrId = ex.name.ifEmpty { ex.id },
                equipment = inferredEq,
                gymProfile = gymProfile,
                progressionType = "Progressão Dupla",
                targetReps = repsTarget,
                isLowerBodyCompound = ex.compound && ex.category.equals("Pernas", ignoreCase = true)
            )

            WorkoutExercise(
                exerciseId = ex.id,
                name = ex.name,
                category = ex.category,
                equipment = inferredEq,
                sets = (1..setsCount).map { sIdx ->
                    com.example.data.model.WorkoutSet(
                        index = sIdx,
                        reps = repsTarget,
                        weight = suggestedLoad,
                        rpe = "8",
                        completed = false
                    )
                },
                restTimeSeconds = if (idx == 0) 90 else 60,
                blockType = if (idx < 4) "Principal" else "Acessórios"
            )
        }

        return Workout(
            id = workoutId,
            name = workoutName,
            description = workoutDesc,
            exercisesJson = JsonUtils.serializeWorkoutExercises(workoutExercises)
        )
    }

    fun generateFullBodyFamily(
        history: List<com.example.data.model.WorkoutHistoryEntry> = emptyList(),
        gymProfile: com.example.data.model.GymProfile? = null
    ): List<Workout> {
        return listOf(
            generateFullBodyVariant("A", history, gymProfile),
            generateFullBodyVariant("B", history, gymProfile),
            generateFullBodyVariant("C", history, gymProfile)
        )
    }

    /**
     * Finds the best equipment fallback exercise from all database exercises if the requested equipment is unavailable.
     */
    private fun findEquipmentFallbackExercise(original: Exercise, availableEquipments: List<String>): Exercise {
        if (availableEquipments.contains(original.equipment) || original.equipment.lowercase() == "peso corporal") {
            return original
        }
        val order = listOf("Barra", "Smith", "Máquina", "Halteres", "Peso Corporal")
        for (equip in order) {
            if (availableEquipments.contains(equip)) {
                val match = com.example.constants.TemplatesAndExercises.ALL_EXERCISES.firstOrNull {
                    it.category.equals(original.category, ignoreCase = true) && 
                    it.equipment.equals(equip, ignoreCase = true)
                }
                if (match != null) {
                    return match
                }
            }
        }
        return original
    }

    /**
     * Build #48 (v4.8) — Motor de Inteligência Híbrida:
     * Analisa o histórico recente de sessões concluídas (combinando treinos de força Full Body A/B/C
     * e sessões do módulo de Corrida) e/ou cronograma híbrido ativo para detectar 3 dias consecutivos
     * de estímulo forte/concorrente e sugerir proativamente Descanso ou Mobilidade Leve.
     */
    fun evaluateConcurrentFatigue(
        gymHistory: List<com.example.data.model.WorkoutHistoryEntry>,
        runningHistory: List<com.example.data.model.RunningSession>,
        cardioHistory: List<com.example.data.model.CardioSession> = emptyList(),
        decision: FatigueAdvisoryDecision = FatigueAdvisoryDecision.PENDING,
        simulateThreeConsecutiveDays: Boolean = false,
        hybridScheduleHasThreeConsecutiveDays: Boolean = false
    ): FatigueAdvisoryState {
        val zoneId = java.time.ZoneId.systemDefault()
        val todayDate = java.time.LocalDate.now(zoneId)

        fun cleanWorkoutLabel(rawName: String): String {
            return when {
                rawName.contains("Full Body A", ignoreCase = true) -> "Full Body A"
                rawName.contains("Full Body B", ignoreCase = true) -> "Full Body B"
                rawName.contains("Full Body C", ignoreCase = true) -> "Full Body C"
                rawName.contains("Full Body D", ignoreCase = true) -> "Full Body D"
                rawName.contains("Corrida", ignoreCase = true) -> "Corrida"
                rawName.contains("—") -> rawName.substringBefore("—").trim()
                rawName.contains("-") -> rawName.substringBefore("-").trim()
                else -> rawName.take(18).trim().ifBlank { "Musculação" }
            }
        }

        // Map each of the last 7 calendar days using LocalDate.now() (offset 0 = today, 1 = yesterday, ..., 6 = 6 days ago)
        val dayStimuli = mutableMapOf<Int, String>()
        for (offset in 0..6) {
            val targetDate = todayDate.minusDays(offset.toLong())
            val start = targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val gymOnDay = gymHistory.firstOrNull { it.date in start until end }
            val runOnDay = runningHistory.firstOrNull { it.date in start until end && !it.isGentleMode }
            val cardioOnDay = cardioHistory.firstOrNull { it.date in start until end }

            val label = when {
                gymOnDay != null && runOnDay != null -> "${cleanWorkoutLabel(gymOnDay.workoutName)} + Corrida"
                gymOnDay != null -> cleanWorkoutLabel(gymOnDay.workoutName)
                runOnDay != null -> "Corrida"
                cardioOnDay != null -> "Cardio (${cardioOnDay.type})"
                else -> null
            }
            if (label != null) {
                dayStimuli[offset] = label
            }
        }

        val trainedToday = dayStimuli[0] != null
        val trainedYesterday = dayStimuli[1] != null

        // Regra de Calendário Real: se ontem houve descanso real (nenhum treino registrado na data de ontem),
        // a sequência anterior foi zerada e a fadiga sofre decaimento fisiológico real.
        val realConsecutive: Int
        val realSequence = mutableListOf<String>()

        if (!trainedYesterday) {
            if (trainedToday) {
                realConsecutive = 1
                realSequence.add(dayStimuli[0]!!)
            } else {
                realConsecutive = 0
            }
        } else {
            var count = 0
            val startOffset = if (trainedToday) 0 else 1
            for (offset in startOffset..6) {
                val stim = dayStimuli[offset]
                if (stim != null) {
                    count++
                    realSequence.add(0, stim)
                } else {
                    break
                }
            }
            realConsecutive = count
        }

        // Sem simulação forçada sobrescrevendo dados reais: apenas histórico real de >= 3 dias seguidos ativa o alerta
        val isTriggered = realConsecutive >= 3

        if (!isTriggered) {
            // Decaimento real da fadiga quando ontem foi descanso
            val partialWear = when {
                !trainedYesterday && !trainedToday -> 12
                !trainedYesterday && trainedToday -> 26
                realConsecutive == 1 -> 28
                realConsecutive == 2 -> 54
                else -> 15
            }
            return FatigueAdvisoryState(
                isTriggered = false,
                consecutiveDays = realConsecutive,
                accumulatedWearScore = partialWear,
                wearLevelLabel = if (realConsecutive == 2) "Moderada (2d)" else "Baixa (SNC 100%)",
                detectedSequence = realSequence,
                coachMessage = if (!trainedYesterday) {
                    "Descanso contabilizado ontem. Sequência zerada e SNC recuperado para o treino de hoje."
                } else {
                    "Carga interna equilibrada. Seu SNC e articulações estão prontos para o estímulo de hoje."
                },
                decision = decision,
                isSimulationMode = false
            )
        }

        val finalConsecutiveDays = realConsecutive
        val finalSequence = realSequence.takeLast(3)

        val wearScore = (84 + (finalConsecutiveDays - 3) * 5).coerceIn(84, 98)
        val coachMsg = "Você acumulou $finalConsecutiveDays dias fortes seguidos. O ideal para hoje é Descanso Ativo ou Mobilidade Leve para blindar o SNC e as articulações."

        return FatigueAdvisoryState(
            isTriggered = true,
            consecutiveDays = finalConsecutiveDays,
            accumulatedWearScore = wearScore,
            wearLevelLabel = "Desgaste $wearScore%",
            detectedSequence = finalSequence,
            coachMessage = coachMsg,
            decision = decision,
            isSimulationMode = false
        )
    }

    /**
     * Gera a sessão regenerativa de Mobilidade Leve & Descompressão Articular (15 min)
     * usada quando o usuário aceita o Dia de Descanso Ativo em 1 toque.
     */
    fun createMobilityRecoveryWorkout(): Workout {
        val mobilityExercises = listOf(
            WorkoutExercise(
                id = "mob_1",
                exerciseId = "mob_hip_90_90",
                name = "Mobilidade de Quadril 90/90 e Cápsula Articular",
                category = "Mobilidade",
                equipment = "Peso Corporal",
                sets = (1..2).map { s ->
                    com.example.data.model.WorkoutSet(index = s, reps = 10, weight = 0f, rpe = "4", completed = false)
                },
                restTimeSeconds = 30,
                blockType = "Preparação",
                executionType = "REPS"
            ),
            WorkoutExercise(
                id = "mob_2",
                exerciseId = "mob_thoracic_rotation",
                name = "Rotação Torácica e Abertura Escapular no Solo",
                category = "Mobilidade",
                equipment = "Peso Corporal",
                sets = (1..2).map { s ->
                    com.example.data.model.WorkoutSet(index = s, reps = 10, weight = 0f, rpe = "4", completed = false)
                },
                restTimeSeconds = 30,
                blockType = "Preparação",
                executionType = "REPS"
            ),
            WorkoutExercise(
                id = "mob_3",
                exerciseId = "mob_ankle_hamstring",
                name = "Alongamento Dinâmico Posterior e Tornozelo",
                category = "Mobilidade",
                equipment = "Peso Corporal",
                sets = (1..2).map { s ->
                    com.example.data.model.WorkoutSet(index = s, reps = 12, weight = 0f, rpe = "4", completed = false)
                },
                restTimeSeconds = 30,
                blockType = "Principal",
                executionType = "REPS"
            ),
            WorkoutExercise(
                id = "mob_4",
                exerciseId = "mob_spine_decompression",
                name = "Descompressão Lombar (Cat-Cow e Child's Pose)",
                category = "Mobilidade",
                equipment = "Peso Corporal",
                sets = (1..2).map { s ->
                    com.example.data.model.WorkoutSet(index = s, reps = 12, weight = 0f, rpe = "3", completed = false)
                },
                restTimeSeconds = 30,
                blockType = "Principal",
                executionType = "REPS"
            )
        )

        return Workout(
            id = "WORKOUT_ACTIVE_RECOVERY_MOBILITY",
            templateId = "ACTIVE_RECOVERY",
            name = "Mobilidade Regenerativa - Descanso Ativo - SNC, Quadril, Lombar",
            description = "Sessão regenerativa de 15 min de baixa carga interna para blindar o Sistema Nervoso Central (SNC), tendões e articulações.",
            exercisesJson = JsonUtils.serializeWorkoutExercises(mobilityExercises),
            createdAt = System.currentTimeMillis()
        )
    }
}

enum class FatigueAdvisoryDecision {
    PENDING,
    ACCEPTED_REST,
    OVERRIDDEN_TRAIN
}

data class FatigueAdvisoryState(
    val isTriggered: Boolean = false,
    val consecutiveDays: Int = 0,
    val accumulatedWearScore: Int = 0,
    val wearLevelLabel: String = "Baixo",
    val detectedSequence: List<String> = emptyList(),
    val coachMessage: String = "Você acumulou 3 dias fortes seguidos. O ideal para hoje é Descanso Ativo ou Mobilidade Leve para blindar o SNC e as articulações.",
    val decision: FatigueAdvisoryDecision = FatigueAdvisoryDecision.PENDING,
    val isSimulationMode: Boolean = false
) {
    val shouldShowBanner: Boolean
        get() = isTriggered && decision == FatigueAdvisoryDecision.PENDING

    val isRestDayAccepted: Boolean
        get() = isTriggered && decision == FatigueAdvisoryDecision.ACCEPTED_REST

    val isTrainOverridden: Boolean
        get() = isTriggered && decision == FatigueAdvisoryDecision.OVERRIDDEN_TRAIN

    val sequenceFormatted: String
        get() = if (detectedSequence.isNotEmpty()) {
            detectedSequence.joinToString(" → ")
        } else {
            "Full Body A → Corrida → Full Body B"
        }
}

