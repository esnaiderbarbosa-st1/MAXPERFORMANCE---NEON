package com.example.services.recommendation

data class DecisionResult(
    val decision: String, // e.g. "CONTINUE_STRATEGY", "INSERT_DELOAD", etc.
    val title: String, // Portuguese descriptive title
    val description: String, // Dynamic rationale justifying the decision
    val details: List<String>, // Bullet points detailing the specific inputs analyzed
    val nextAction: String, // Dynamic actionable guidance for the user
    val suggestedSplit: String, // Suggested split division template
    val suggestedVolume: String, // Suggested volume adjustments
    val suggestedIntensity: String, // Suggested intensity adjustments
    
    // RFC-REC-003 fields
    val ruleId: String = "DEC-001",
    val priorityLevel: String = "PROGRESSÃO",
    val scoreImpact: Int = 50,
    val scoreSecurity: Int = 90,
    val scoreAdherence: Int = 85,
    val scorePerformance: Int = 80,
    val scoreFinal: Int = 76,
    val blockedActions: List<String> = emptyList(),
    val exitCriteria: String = "Estabilização dos indicadores por 7 dias",
    val reversible: Boolean = true
)
