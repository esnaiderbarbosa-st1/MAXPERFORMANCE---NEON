package com.example.services.recommendation.knowledge

import com.example.services.recommendation.knowledge.models.*

/**
 * TrainingKnowledgeBase serves as the centralized repository of technical athletic training knowledge.
 *
 * This singleton object encapsulates definitions, guidelines, and recommendations for training objectives,
 * intensity techniques, progression structures, recovery protocols, and cardiovascular strategies. It is completely
 * decoupled from the UI layer and acts as a pure read-only knowledge repository.
 */
object TrainingKnowledgeBase {

    /**
     * Technical guidelines and parameters associated with primary fitness objectives.
     */
    object Objectives {
        private val list = listOf(
            TrainingObjective(
                id = "hypertrophy",
                name = "Hipertrofia",
                description = "Foco em ganho de massa muscular através de volume moderado a alto e intensidade controlada.",
                recommendedVolume = "10-20 weekly sets per muscle group",
                recommendedRIR = "0-3 RIR (Reps in Reserve)",
                preferredRestRange = "60-120 seconds",
                preferredMethods = listOf("straight_sets", "drop_set", "rest_pause", "myo_reps"),
                preferredProgressions = listOf("double_progression", "linear"),
                recommendedCardio = "2-3 times per week, moderate intensity (LISS/MISS)",
                fatigueTolerance = "Moderate to High",
                difficulty = "Medium"
            ),
            TrainingObjective(
                id = "strength",
                name = "Força",
                description = "Foco no ganho de força neuromuscular, cargas altas e maior tempo de descanso.",
                recommendedVolume = "6-12 weekly sets per muscle group",
                recommendedRIR = "1-4 RIR",
                preferredRestRange = "3-5 minutes",
                preferredMethods = listOf("straight_sets", "top_set", "back_off"),
                preferredProgressions = listOf("linear", "wave_loading", "reverse_pyramid"),
                recommendedCardio = "1-2 times per week, low intensity (LISS)",
                fatigueTolerance = "High",
                difficulty = "High"
            ),
            TrainingObjective(
                id = "weight_loss",
                name = "Emagrecimento",
                description = "Foco em gasto calórico elevado, manutenção de massa magra e condicionamento metabólico.",
                recommendedVolume = "12-16 weekly sets per muscle group",
                recommendedRIR = "1-3 RIR",
                preferredRestRange = "45-90 seconds",
                preferredMethods = listOf("straight_sets", "superset", "drop_set"),
                preferredProgressions = listOf("double_progression"),
                recommendedCardio = "3-5 times per week, combined LISS and HIIT",
                fatigueTolerance = "Medium",
                difficulty = "Medium"
            ),
            TrainingObjective(
                id = "endurance",
                name = "Resistência",
                description = "Foco em resistência muscular localizada e cardiovascular, com altas repetições e pouco descanso.",
                recommendedVolume = "12-18 weekly sets per muscle group",
                recommendedRIR = "2-4 RIR",
                preferredRestRange = "30-60 seconds",
                preferredMethods = listOf("straight_sets", "giant_set", "superset"),
                preferredProgressions = listOf("double_progression"),
                recommendedCardio = "3-4 times per week, MISS/HIIT",
                fatigueTolerance = "High",
                difficulty = "Medium"
            ),
            TrainingObjective(
                id = "maintenance",
                name = "Manutenção",
                description = "Foco em preservar a massa muscular e os níveis de força atuais com menor volume geral.",
                recommendedVolume = "4-8 weekly sets per muscle group",
                recommendedRIR = "2-4 RIR",
                preferredRestRange = "90-120 seconds",
                preferredMethods = listOf("straight_sets"),
                preferredProgressions = listOf("linear"),
                recommendedCardio = "2-3 times per week, LISS",
                fatigueTolerance = "Low",
                difficulty = "Low"
            ),
            TrainingObjective(
                id = "performance",
                name = "Performance",
                description = "Foco em potência, velocidade, agilidade e otimização dos sistemas energéticos.",
                recommendedVolume = "8-12 weekly sets per muscle group",
                recommendedRIR = "1-4 RIR",
                preferredRestRange = "2-3 minutes",
                preferredMethods = listOf("straight_sets", "cluster"),
                preferredProgressions = listOf("wave_loading", "daily_undulating"),
                recommendedCardio = "3-4 times per week, HIIT/Sprint",
                fatigueTolerance = "High",
                difficulty = "High"
            ),
            TrainingObjective(
                id = "bodybuilding",
                name = "Bodybuilding",
                description = "Desenvolvimento muscular estético avançado com técnicas de alta intensidade e isolamento muscular.",
                recommendedVolume = "15-25 weekly sets per muscle group",
                recommendedRIR = "0-2 RIR",
                preferredRestRange = "60-90 seconds",
                preferredMethods = listOf("top_set", "back_off", "drop_set", "rest_pause", "myo_reps", "giant_set"),
                preferredProgressions = listOf("top_set_back_off", "double_progression"),
                recommendedCardio = "3-5 times per week, LISS/Incline Walk",
                fatigueTolerance = "Very High",
                difficulty = "Very High"
            ),
            TrainingObjective(
                id = "rehabilitation",
                name = "Reabilitação",
                description = "Foco na recuperação de lesões, fortalecimento de desequilíbrios musculares e mobilidade.",
                recommendedVolume = "4-8 weekly sets per muscle group",
                recommendedRIR = "3-5 RIR",
                preferredRestRange = "60-120 seconds",
                preferredMethods = listOf("straight_sets", "feeder_sets"),
                preferredProgressions = listOf("linear"),
                recommendedCardio = "2-3 times per week, Recovery Cardio",
                fatigueTolerance = "Very Low",
                difficulty = "Low"
            )
        )

        /**
         * Resolves a TrainingObjective by its unique ID.
         */
        fun getById(id: String): TrainingObjective? = list.find { it.id == id }

        /**
         * Retrieves all available TrainingObjectives.
         */
        fun getAll(): List<TrainingObjective> = list

        /**
         * Filters the TrainingObjectives collection based on a specified predicate.
         */
        fun find(predicate: (TrainingObjective) -> Boolean): List<TrainingObjective> = list.filter(predicate)

        /**
         * Checks whether a TrainingObjective with the given ID exists.
         */
        fun exists(id: String): Boolean = list.any { it.id == id }
    }

    /**
     * Advanced training methodologies and intensity modifiers.
     */
    object IntensityMethods {
        private val list = listOf(
            IntensityMethod(
                id = "straight_sets",
                name = "Straight Sets",
                description = "Séries convencionais com carga e repetições pré-definidas e descanso completo.",
                recommendedGoals = listOf("hypertrophy", "strength", "weight_loss", "endurance", "maintenance", "performance", "bodybuilding", "rehabilitation"),
                contraindications = "Nenhuma contraindicação geral.",
                fatigueMultiplier = 1.0,
                timeCostMinutes = 0,
                recommendedExerciseTypes = listOf("Composto", "Isolado"),
                recommendedLevel = "Iniciante",
                allowCompound = true,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "top_set",
                name = "Top Set",
                description = "Série principal realizada com o máximo de peso para a faixa de repetição alvo, seguida por séries de menor intensidade.",
                recommendedGoals = listOf("strength", "bodybuilding", "hypertrophy"),
                contraindications = "Não recomendado para iniciantes ou indivíduos com lesões articulares graves devido à alta carga.",
                fatigueMultiplier = 1.3,
                timeCostMinutes = 2,
                recommendedExerciseTypes = listOf("Composto"),
                recommendedLevel = "Avançado",
                allowCompound = true,
                allowIsolation = false
            ),
            IntensityMethod(
                id = "back_off",
                name = "Back-Off",
                description = "Séries realizadas após a principal (Top Set) com carga reduzida (10-20% menor) e maior número de repetições.",
                recommendedGoals = listOf("strength", "bodybuilding", "hypertrophy"),
                contraindications = "Nenhuma contraindicação séria além da fadiga acumulada do Top Set.",
                fatigueMultiplier = 1.1,
                timeCostMinutes = 1,
                recommendedExerciseTypes = listOf("Composto"),
                recommendedLevel = "Intermediário",
                allowCompound = true,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "drop_set",
                name = "Drop Set",
                description = "Execução de uma série até a falha concêntrica, seguida imediatamente por redução do peso e continuação do exercício.",
                recommendedGoals = listOf("hypertrophy", "bodybuilding", "weight_loss"),
                contraindications = "Altamente desgastante para o sistema nervoso central; evite fazer em múltiplos exercícios compostos no mesmo treino.",
                fatigueMultiplier = 1.4,
                timeCostMinutes = 1,
                recommendedExerciseTypes = listOf("Isolado"),
                recommendedLevel = "Intermediário",
                allowCompound = false,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "rest_pause",
                name = "Rest Pause",
                description = "Uma série convencional levada perto da falha, seguida de micropausas (15s) e novas repetições até acumular o alvo.",
                recommendedGoals = listOf("hypertrophy", "bodybuilding"),
                contraindications = "Não recomendado para exercícios livres complexos que requeiram muito equilíbrio e estabilidade (ex: agachamento livre).",
                fatigueMultiplier = 1.4,
                timeCostMinutes = 1,
                recommendedExerciseTypes = listOf("Isolado", "Composto em Máquina"),
                recommendedLevel = "Avançado",
                allowCompound = true,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "cluster",
                name = "Cluster",
                description = "Fracionamento de uma série com curtas pausas (10-20s) entre repetições individuais ou pequenos blocos.",
                recommendedGoals = listOf("strength", "performance"),
                contraindications = "Pode requerer maior tempo para configurar e cronometrar as repetições.",
                fatigueMultiplier = 1.2,
                timeCostMinutes = 3,
                recommendedExerciseTypes = listOf("Composto"),
                recommendedLevel = "Avançado",
                allowCompound = true,
                allowIsolation = false
            ),
            IntensityMethod(
                id = "superset",
                name = "Superset",
                description = "Execução de dois exercícios consecutivos de grupos opostos (antagonistas) ou sinérgicos com mínimo de descanso.",
                recommendedGoals = listOf("hypertrophy", "weight_loss", "endurance"),
                contraindications = "Requer cuidado em academias cheias para manter dois equipamentos ocupados.",
                fatigueMultiplier = 1.25,
                timeCostMinutes = -3,
                recommendedExerciseTypes = listOf("Isolado", "Composto"),
                recommendedLevel = "Intermediário",
                allowCompound = true,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "giant_set",
                name = "Giant Set",
                description = "Circuito de quatro ou mais exercícios para o mesmo grupo muscular ou grupos diferentes com descanso mínimo entre eles.",
                recommendedGoals = listOf("endurance", "weight_loss", "bodybuilding"),
                contraindications = "Gera altíssima fadiga cardiovascular e muscular periférica; não recomendado para atletas iniciantes.",
                fatigueMultiplier = 1.5,
                timeCostMinutes = -5,
                recommendedExerciseTypes = listOf("Isolado"),
                recommendedLevel = "Avançado",
                allowCompound = false,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "myo_reps",
                name = "Myo Reps",
                description = "Variação de rest-pause com uma série de ativação seguida de mini-séries de 3-5 repetições com descansos curtíssimos de 5-10s.",
                recommendedGoals = listOf("hypertrophy", "bodybuilding"),
                contraindications = "Não utilizar em exercícios compostos que sobrecarreguem intensamente a coluna vertebral.",
                fatigueMultiplier = 1.35,
                timeCostMinutes = 0,
                recommendedExerciseTypes = listOf("Isolado"),
                recommendedLevel = "Avançado",
                allowCompound = false,
                allowIsolation = true
            ),
            IntensityMethod(
                id = "feeder_sets",
                name = "Feeder Sets",
                description = "Séries de altíssima repetição (50-100) com cargas extremamente leves para direcionar fluxo sanguíneo e acelerar recuperação.",
                recommendedGoals = listOf("rehabilitation", "bodybuilding"),
                contraindications = "Evitar em tendinites na fase aguda; usar apenas sob orientação de recuperação ou bombeamento direcionado.",
                fatigueMultiplier = 0.8,
                timeCostMinutes = 2,
                recommendedExerciseTypes = listOf("Isolado"),
                recommendedLevel = "Iniciante",
                allowCompound = false,
                allowIsolation = true
            )
        )

        /**
         * Resolves an IntensityMethod by its unique ID.
         */
        fun getById(id: String): IntensityMethod? = list.find { it.id == id }

        /**
         * Retrieves all available IntensityMethods.
         */
        fun getAll(): List<IntensityMethod> = list

        /**
         * Filters the IntensityMethods collection based on a specified predicate.
         */
        fun find(predicate: (IntensityMethod) -> Boolean): List<IntensityMethod> = list.filter(predicate)

        /**
         * Checks whether an IntensityMethod with the given ID exists.
         */
        fun exists(id: String): Boolean = list.any { it.id == id }
    }

    /**
     * Progressive overload models and overload structures.
     */
    object ProgressionStrategies {
        private val list = listOf(
            ProgressionStrategy(
                id = "linear",
                name = "Linear",
                description = "Aumento linear e constante de carga de treino a cada sessão ou semana.",
                recommendedGoals = listOf("strength", "hypertrophy", "rehabilitation", "maintenance"),
                minimumExperience = "Iniciante",
                recommendedDurationWeeks = 8,
                loadIncrease = "1kg a 2.5kg por treino",
                volumeIncrease = "Nenhum (mantém séries/repetições fixas)",
                deloadRecommended = true
            ),
            ProgressionStrategy(
                id = "double_progression",
                name = "Double Progression",
                description = "Trabalha-se dentro de uma faixa de repetições; quando o limite superior é atingido em todas as séries, aumenta-se a carga.",
                recommendedGoals = listOf("hypertrophy", "bodybuilding", "endurance", "weight_loss"),
                minimumExperience = "Intermediário",
                recommendedDurationWeeks = 12,
                loadIncrease = "2.5kg a 5kg ao atingir teto de repetições",
                volumeIncrease = "Aumento indireto por repetições adicionadas",
                deloadRecommended = true
            ),
            ProgressionStrategy(
                id = "reverse_pyramid",
                name = "Reverse Pyramid",
                description = "Começa-se com a série mais pesada com poucas repetições, reduzindo a carga e aumentando repetições nas séries seguintes.",
                recommendedGoals = listOf("strength", "hypertrophy", "bodybuilding"),
                minimumExperience = "Intermediário",
                recommendedDurationWeeks = 10,
                loadIncrease = "Foco em bater recordes pessoais na primeira série",
                volumeIncrease = "Estável ao longo das semanas",
                deloadRecommended = true
            ),
            ProgressionStrategy(
                id = "daily_undulating",
                name = "Daily Undulating",
                description = "Variação programada de intensidade e volume em cada dia de treino na mesma semana (ex: dia de força, dia de hipertrofia).",
                recommendedGoals = listOf("performance", "strength", "hypertrophy"),
                minimumExperience = "Avançado",
                recommendedDurationWeeks = 16,
                loadIncrease = "Ondulatório semanal conforme planejado",
                volumeIncrease = "Ondula conforme o dia do microciclo",
                deloadRecommended = true
            ),
            ProgressionStrategy(
                id = "top_set_back_off",
                name = "Top Set + Back-Off",
                description = "Progressão focada em performar o máximo em uma única série de alta intensidade, usando as séries adicionais apenas para acúmulo de volume controlado.",
                recommendedGoals = listOf("strength", "bodybuilding"),
                minimumExperience = "Avançado",
                recommendedDurationWeeks = 12,
                loadIncrease = "Aumento semanal no Top Set quando atingido o limite",
                volumeIncrease = "Estável",
                deloadRecommended = true
            ),
            ProgressionStrategy(
                id = "wave_loading",
                name = "Wave Loading",
                description = "Estruturação em 'ondas' de intensidade e repetições (ex: Série 1: 6 repetições, Série 2: 4 rep., Série 3: 2 rep., e repete com mais carga).",
                recommendedGoals = listOf("strength", "performance"),
                minimumExperience = "Avançado",
                recommendedDurationWeeks = 8,
                loadIncrease = "Aumento de carga onda após onda",
                volumeIncrease = "Nenhum",
                deloadRecommended = true
            )
        )

        /**
         * Resolves a ProgressionStrategy by its unique ID.
         */
        fun getById(id: String): ProgressionStrategy? = list.find { it.id == id }

        /**
         * Retrieves all available ProgressionStrategies.
         */
        fun getAll(): List<ProgressionStrategy> = list

        /**
         * Filters the ProgressionStrategies collection based on a specified predicate.
         */
        fun find(predicate: (ProgressionStrategy) -> Boolean): List<ProgressionStrategy> = list.filter(predicate)

        /**
         * Checks whether a ProgressionStrategy with the given ID exists.
         */
        fun exists(id: String): Boolean = list.any { it.id == id }
    }

    /**
     * Dynamic adaptation parameters responding to fatigue and recovery indices.
     */
    object RecoveryStrategies {
        private val list = listOf(
            RecoveryStrategy(
                id = "full_recovery",
                name = "Full Recovery",
                description = "Nível de fadiga baixo, permitindo volume máximo de treino e métodos avançados de intensidade.",
                minimumRecovery = "Sono de 8h+, alimentação hipercalórica/normocalórica, estresse baixo.",
                volumeAdjustment = "100% a 110% do volume planejado",
                restAdjustment = "Descanso regular planejado",
                allowIntensityMethods = true,
                priority = 1
            ),
            RecoveryStrategy(
                id = "moderate_fatigue",
                name = "Moderate Fatigue",
                description = "Fadiga moderada acumulada. Manter volume padrão, monitorando a fadiga central.",
                minimumRecovery = "Sono regular (6-7h), boa hidratação.",
                volumeAdjustment = "90% a 100% do volume planejado",
                restAdjustment = "Aumentar tempo de descanso em 15-30s",
                allowIntensityMethods = true,
                priority = 2
            ),
            RecoveryStrategy(
                id = "high_fatigue",
                name = "High Fatigue",
                description = "Fadiga elevada detectada. Recomenda-se reduzir volume de séries de trabalho para evitar overreaching de longo prazo.",
                minimumRecovery = "Descanso insatisfatório, restrição calórica severa.",
                volumeAdjustment = "70% do volume padrão",
                restAdjustment = "Aumentar tempo de descanso em 30-60s",
                allowIntensityMethods = false,
                priority = 3
            ),
            RecoveryStrategy(
                id = "deload",
                name = "Deload",
                description = "Semana de redução estruturada de estresse físico visando supercompensação sistêmica.",
                minimumRecovery = "Programado a cada 6-12 semanas de treino intenso.",
                volumeAdjustment = "50% do volume convencional",
                restAdjustment = "Livre e generoso",
                allowIntensityMethods = false,
                priority = 4
            ),
            RecoveryStrategy(
                id = "active_recovery",
                name = "Active Recovery",
                description = "Sessões regenerativas focadas em alongamento, fluxo sanguíneo localizado e liberação miofascial sem impacto articular.",
                minimumRecovery = "Utilizado em dias de descanso ativo.",
                volumeAdjustment = "Apenas séries de bombeamento leve (<30% do normal)",
                restAdjustment = "Curto ou livre",
                allowIntensityMethods = false,
                priority = 5
            )
        )

        /**
         * Resolves a RecoveryStrategy by its unique ID.
         */
        fun getById(id: String): RecoveryStrategy? = list.find { it.id == id }

        /**
         * Retrieves all available RecoveryStrategies.
         */
        fun getAll(): List<RecoveryStrategy> = list

        /**
         * Filters the RecoveryStrategies collection based on a specified predicate.
         */
        fun find(predicate: (RecoveryStrategy) -> Boolean): List<RecoveryStrategy> = list.filter(predicate)

        /**
         * Checks whether a RecoveryStrategy with the given ID exists.
         */
        fun exists(id: String): Boolean = list.any { it.id == id }
    }

    /**
     * Cardiovascular conditioning strategies and parameters.
     */
    object CardioStrategies {
        private val list = listOf(
            CardioStrategy(
                id = "liss",
                name = "LISS",
                description = "Low Intensity Steady State. Exercício cardiovascular contínuo de intensidade moderadamente baixa (ex: caminhada rápida).",
                recommendedGoals = listOf("weight_loss", "hypertrophy", "bodybuilding", "maintenance", "rehabilitation"),
                durationMinutes = 30,
                frequency = "3-5 vezes por semana",
                interferenceLevel = "Muito Baixo",
                energyCost = "Baixo a Moderado"
            ),
            CardioStrategy(
                id = "miss",
                name = "MISS",
                description = "Medium Intensity Steady State. Trote ou ciclismo de intensidade média contínua.",
                recommendedGoals = listOf("weight_loss", "endurance", "maintenance"),
                durationMinutes = 40,
                frequency = "2-4 vezes por semana",
                interferenceLevel = "Moderado",
                energyCost = "Moderado a Alto"
            ),
            CardioStrategy(
                id = "hiit",
                name = "HIIT",
                description = "High Intensity Interval Training. Intervalos de esforço máximo de curta duração intercalados com recuperação ativa/passiva.",
                recommendedGoals = listOf("weight_loss", "performance", "endurance"),
                durationMinutes = 15,
                frequency = "1-2 vezes por semana",
                interferenceLevel = "Alto",
                energyCost = "Muito Alto"
            ),
            CardioStrategy(
                id = "sprint",
                name = "Sprint",
                description = "Treino intervalado de sprint focado em explosão anaeróbica (corrida ou bike).",
                recommendedGoals = listOf("performance", "strength"),
                durationMinutes = 10,
                frequency = "1 vez por semana",
                interferenceLevel = "Muito Alto",
                energyCost = "Altíssimo"
            ),
            CardioStrategy(
                id = "incline_walk",
                name = "Incline Walk",
                description = "Caminhada em esteira com inclinação elevada, excelente queima calórica com baixo impacto articular.",
                recommendedGoals = listOf("hypertrophy", "bodybuilding", "weight_loss"),
                durationMinutes = 30,
                frequency = "3-6 vezes por semana",
                interferenceLevel = "Baixo",
                energyCost = "Moderado"
            ),
            CardioStrategy(
                id = "recovery_cardio",
                name = "Recovery Cardio",
                description = "Giro de bike extremamente leve ou caminhada recreativa visando apenas estimulação circulatória para remover metabólitos.",
                recommendedGoals = listOf("rehabilitation", "maintenance", "bodybuilding"),
                durationMinutes = 20,
                frequency = "Conforme necessidade em dias de descanso",
                interferenceLevel = "Nenhum",
                energyCost = "Muito Baixo"
            )
        )

        /**
         * Resolves a CardioStrategy by its unique ID.
         */
        fun getById(id: String): CardioStrategy? = list.find { it.id == id }

        /**
         * Retrieves all available CardioStrategies.
         */
        fun getAll(): List<CardioStrategy> = list

        /**
         * Filters the CardioStrategies collection based on a specified predicate.
         */
        fun find(predicate: (CardioStrategy) -> Boolean): List<CardioStrategy> = list.filter(predicate)

        /**
         * Checks whether a CardioStrategy with the given ID exists.
         */
        fun exists(id: String): Boolean = list.any { it.id == id }
    }
}
