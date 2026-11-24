package com.example.services.recommendation.ruleengine

import com.example.engine.WorkoutContext

/**
 * Interface representing a rule condition.
 */
interface Condition {
    fun evaluate(context: WorkoutContext): Boolean
}

/**
 * Interface representing a rule action.
 */
interface Action {
    fun execute(context: WorkoutContext, builder: RuleEngineState)
}

/**
 * Represents a rule with an identifier, a descriptive name, a condition, and an action.
 */
data class Rule(
    val id: String,
    val name: String,
    val condition: Condition,
    val action: Action
)

/**
 * Mutable state modified by rules during evaluation.
 */
data class RuleEngineState(
    var includeAccessories: Boolean = true,
    var allowClusterSets: Boolean = true,
    var avoidTopSet: Boolean = false,
    var maxSetsPerExercise: Int = 4,
    var preferredIntensityMethod: String? = null,
    val explanations: MutableList<String> = mutableListOf(),
    val triggeredRules: MutableList<String> = mutableListOf()
)

/**
 * The Rule Engine executes registered rules against a WorkoutContext to adapt the workout generation.
 */
class RuleEngine {
    private val rules = mutableListOf<Rule>()

    init {
        // Register default rules as requested in Sprint ENG-007
        
        // 1. Time Rule (Tempo < 35 min -> remover acessórios)
        registerRule(
            Rule(
                id = "TIME_LIMIT_RULE",
                name = "Time Available Below 35 Minutes",
                condition = object : Condition {
                    override fun evaluate(context: WorkoutContext): Boolean =
                        context.durationMinutes < 35 || context.availableTime < 35
                },
                action = object : Action {
                    override fun execute(context: WorkoutContext, builder: RuleEngineState) {
                        builder.includeAccessories = false
                        builder.maxSetsPerExercise = 2
                        builder.explanations.add("Treino reduzido: Tempo disponível ${context.durationMinutes} min")
                    }
                }
            )
        )

        // 2. Recovery Rule (Recuperação baixa -> evitar Top Set)
        registerRule(
            Rule(
                id = "LOW_RECOVERY_RULE",
                name = "Low Recovery Level",
                condition = object : Condition {
                    override fun evaluate(context: WorkoutContext): Boolean =
                        context.recoveryProfile < 5.0 || context.fatigueLevel > 6.0
                },
                action = object : Action {
                    override fun execute(context: WorkoutContext, builder: RuleEngineState) {
                        builder.avoidTopSet = true
                        builder.maxSetsPerExercise = 3
                        builder.explanations.add("Drop Set removido: Recuperação baixa")
                    }
                }
            )
        )

        // 3. Beginner Rule (Iniciante -> não usar Cluster)
        registerRule(
            Rule(
                id = "BEGINNER_SAFETY_RULE",
                name = "Beginner Experience Safety",
                condition = object : Condition {
                    override fun evaluate(context: WorkoutContext): Boolean =
                        context.experience.lowercase() == "iniciante" || context.userPreferences.level.lowercase() == "iniciante"
                },
                action = object : Action {
                    override fun execute(context: WorkoutContext, builder: RuleEngineState) {
                        builder.allowClusterSets = false
                        builder.explanations.add("Não utilizar Cluster: Iniciante")
                    }
                }
            )
        )

        // 4. Barbell Constraint Rule (Sem barra -> Sem agachamento)
        registerRule(
            Rule(
                id = "NO_BARBELL_CONSTRAINT_RULE",
                name = "No Barbell Available Fallback",
                condition = object : Condition {
                    override fun evaluate(context: WorkoutContext): Boolean {
                        val hasNoBarbell = context.constraints.noBarbell || 
                                           (context.gymProfile != null && !context.gymProfile!!.hasOlympicBar)
                        return hasNoBarbell
                    }
                },
                action = object : Action {
                    override fun execute(context: WorkoutContext, builder: RuleEngineState) {
                        builder.explanations.add("Smith escolhido: Barra indisponível")
                    }
                }
            )
        )
    }

    fun registerRule(rule: Rule) {
        rules.add(rule)
    }

    /**
     * Executes all rules and produces the final adapted State.
     */
    fun evaluate(context: WorkoutContext): RuleEngineState {
        val state = RuleEngineState()
        for (rule in rules) {
            if (rule.condition.evaluate(context)) {
                rule.action.execute(context, state)
                state.triggeredRules.add(rule.id)
            }
        }
        return state
    }
}
