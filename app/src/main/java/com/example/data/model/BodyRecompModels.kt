package com.example.data.model

import kotlin.math.roundToInt

/**
 * Status semântico padronizado para os 14 indicadores da balança de bioimpedância
 * (padrão Fitdays / RelaxFit). Labels curtos e diretos que nunca quebram no meio da palavra
 * (`maxLines = 1`, `softWrap = false`).
 */
enum class IndicatorStatus(
    val labelPt: String,
    val badgeColorHex: Long
) {
    BAIXO(labelPt = "Baixo", badgeColorHex = 0xFF00B0FF),
    DESIDRATADO(labelPt = "Desidratado", badgeColorHex = 0xFFFFB300),
    SAUDAVEL(labelPt = "Saudável", badgeColorHex = 0xFF00E676),
    MEDIO(labelPt = "Médio", badgeColorHex = 0xFF00E676),
    EXCELENTE(labelPt = "Excelente", badgeColorHex = 0xFF00E5FF),
    ALTO(labelPt = "Alto", badgeColorHex = 0xFFFF5252),
    SOBREPESO(labelPt = "Sobrepeso", badgeColorHex = 0xFFFFB300),
    MEDIO_RISCO(labelPt = "Risco Médio", badgeColorHex = 0xFFFF7043),
    NEUTRO(labelPt = "—", badgeColorHex = 0xFF94A3B8)
}

/**
 * Classificação automática da evolução corporal entre pesagens consecutivas no MAX RECOMP.
 */
enum class RecompClassification(
    val title: String,
    val badgeLabel: String,
    val accentColorHex: Long
) {
    OURO_RECOMPOSICAO_PURA(
        title = "Recomposição Corporal Pura",
        badgeLabel = "OURO • RECOMP PURA",
        accentColorHex = 0xFF00E5FF
    ),
    BULKING_LIMPO(
        title = "Anabolismo / Bulking Limpo",
        badgeLabel = "BULKING LIMPO",
        accentColorHex = 0xFF00E676
    ),
    CUTTING_PRESERVADO(
        title = "Cutting com Preservação Muscular",
        badgeLabel = "CUTTING EFICIENTE",
        accentColorHex = 0xFF00B0FF
    ),
    ALERTA_DESIDRATACAO_OU_CATABOLISMO(
        title = "Alerta de Desidratação ou Catabolismo",
        badgeLabel = "HIDRATAÇÃO",
        accentColorHex = 0xFFFF5252
    ),
    MARCO_ZERO_BASE(
        title = "Marco Zero • Baseline Fisiológico",
        badgeLabel = "BASELINE #1",
        accentColorHex = 0xFF00B0FF
    )
}

/**
 * Registro oficial de Bioimpedância padrão Fitdays / Relaxmedic contendo os 14 indicadores
 * diretos + grandezas derivadas exatas em kg, dados segmentares e índice músculo/gordura.
 */
data class BioimpedanceRecord(
    // Metadados da medição
    val id: String,
    val timestampMillis: Long,
    val dateTimeLabel: String,
    val athleteName: String = "Esnaider",
    val sourceApp: String = "Fitdays / RelaxFit",

    // Os 14 Indicadores Diretos da Balança Fitdays / Relaxmedic
    val weightKg: Float,               // 1. Peso (kg) — ex: 84.7f
    val bmi: Float,                    // 2. IMC — ex: 24.5f
    val bodyFatPercent: Float,         // 3. Taxa de gordura corporal (%) — ex: 23.0f
    val muscleRatePercent: Float,      // 4. Taxa muscular (%) — ex: 73.0f
    val fatFreeMassKg: Float,          // 5. Massa livre de gordura (kg) — ex: 65.2f
    val subcutaneousFatPercent: Float, // 6. Gordura subcutânea (%) — ex: 16.5f
    val visceralFatIndex: Float,       // 7. Gordura visceral (índice) — ex: 11.0f
    val bodyWaterPercent: Float,       // 8. Água corporal (%) — ex: 52.8f
    val skeletalMusclePercent: Float,  // 9. Músculo esquelético (%) — ex: 40.9f
    val muscleMassKg: Float,           // 10. Massa muscular (kg) — ex: 61.8f
    val boneMassKg: Float,             // 11. Massa óssea (kg) — ex: 3.4f
    val proteinPercent: Float,         // 12. Proteína (%) — ex: 20.2f
    val bmrKcal: Int,                  // 13. TMB (kcal) — ex: 1778
    val metabolicAge: Int,             // 14. Idade metabólica (anos) — ex: 49
    val photoUri: String? = null,      // URI opcional do print anexado na galeria
    val segmentalSummary: String = "", // Resumo segmentar (Braços, Tronco, Pernas) quando disponível
    val explicitFatMassKg: Float? = null // Valor direto de Gordura Corporal em kg lido do laudo Relaxmedic (ex: 19.3f)
) {
    var archived: Boolean = false

    constructor(
        id: String,
        date: Long,
        peso: Float,
        imc: Float,
        percentualGordura: Float,
        taxaMuscular: Float,
        massaMagra: Float,
        gorduraSubcutanea: Float,
        gorduraVisceral: Float,
        aguaCorporal: Float,
        massaMuscularEsqueletica: Float,
        massaMuscular: Float,
        massaOssea: Float,
        proteina: Float,
        tmb: Int,
        idadeMetabolica: Int,
        archived: Boolean = false
    ) : this(
        id = id,
        timestampMillis = date,
        dateTimeLabel = java.text.SimpleDateFormat("HH:mm dd/MM/yyyy", java.util.Locale.forLanguageTag("pt-BR")).format(java.util.Date(date)),
        athleteName = "Esnaider",
        sourceApp = "Fitdays / RelaxFit",
        weightKg = peso,
        bmi = imc,
        bodyFatPercent = percentualGordura,
        muscleRatePercent = taxaMuscular,
        fatFreeMassKg = massaMagra,
        subcutaneousFatPercent = gorduraSubcutanea,
        visceralFatIndex = gorduraVisceral,
        bodyWaterPercent = aguaCorporal,
        skeletalMusclePercent = massaMuscularEsqueletica,
        muscleMassKg = massaMuscular,
        boneMassKg = massaOssea,
        proteinPercent = proteina,
        bmrKcal = tmb,
        metabolicAge = idadeMetabolica
    ) {
        this.archived = archived
    }

    // Aliases de compatibilidade legada
    val date: Long get() = timestampMillis
    val peso: Float get() = weightKg
    val imc: Float get() = bmi
    val percentualGordura: Float get() = bodyFatPercent
    val taxaMuscular: Float get() = muscleRatePercent
    val massaMagra: Float get() = fatFreeMassKg
    val gorduraSubcutanea: Float get() = subcutaneousFatPercent
    val gorduraVisceral: Float get() = visceralFatIndex
    val aguaCorporal: Float get() = bodyWaterPercent
    val massaMuscularEsqueletica: Float get() = skeletalMusclePercent
    val massaMuscular: Float get() = muscleMassKg
    val massaOssea: Float get() = boneMassKg
    val proteina: Float get() = proteinPercent
    val tmb: Int get() = bmrKcal
    val idadeMetabolica: Int get() = metabolicAge

    fun copy(
        peso: Float = this.weightKg,
        imc: Float = this.bmi,
        percentualGordura: Float = this.bodyFatPercent,
        taxaMuscular: Float = this.muscleRatePercent,
        massaMagra: Float = this.fatFreeMassKg,
        gorduraSubcutanea: Float = this.subcutaneousFatPercent,
        gorduraVisceral: Float = this.visceralFatIndex,
        aguaCorporal: Float = this.bodyWaterPercent,
        massaMuscularEsqueletica: Float = this.skeletalMusclePercent,
        massaMuscular: Float = this.muscleMassKg,
        massaOssea: Float = this.boneMassKg,
        proteina: Float = this.proteinPercent,
        tmb: Int = this.bmrKcal,
        idadeMetabolica: Int = this.metabolicAge,
        archived: Boolean = this.archived
    ): BioimpedanceRecord {
        return BioimpedanceRecord(
            id = this.id,
            timestampMillis = this.timestampMillis,
            dateTimeLabel = this.dateTimeLabel,
            athleteName = this.athleteName,
            sourceApp = this.sourceApp,
            weightKg = peso,
            bmi = imc,
            bodyFatPercent = percentualGordura,
            muscleRatePercent = taxaMuscular,
            fatFreeMassKg = massaMagra,
            subcutaneousFatPercent = gorduraSubcutanea,
            visceralFatIndex = gorduraVisceral,
            bodyWaterPercent = aguaCorporal,
            skeletalMusclePercent = massaMuscularEsqueletica,
            muscleMassKg = massaMuscular,
            boneMassKg = massaOssea,
            proteinPercent = proteina,
            bmrKcal = tmb,
            metabolicAge = idadeMetabolica,
            photoUri = this.photoUri,
            segmentalSummary = this.segmentalSummary,
            explicitFatMassKg = this.explicitFatMassKg
        ).also { it.archived = archived }
    }

    /**
     * Massa Gorda Absoluta em kg: prioriza `explicitFatMassKg` (lido diretamente da linha
     * `"Gordura corporal | 19.3 (9.2-18.4) | 22.6"` do relatório Relaxmedic), ou calcula
     * `weightKg * (bodyFatPercent / 100f)` (ex: `85.4 * 0.226 = 19.3 kg`), evitando erro de
     * arredondamento de `weightKg - fatFreeMassKg` (`85.4 - 66.0 = 19.4 kg`).
     */
    val fatMassKg: Float
        get() {
            if (explicitFatMassKg != null && explicitFatMassKg in 1f..<weightKg) {
                return ((explicitFatMassKg * 10f).roundToInt() / 10f).coerceAtLeast(0.1f)
            }
            val raw = if (bodyFatPercent > 0.5f && weightKg > 10f) {
                weightKg * (bodyFatPercent / 100f)
            } else if (fatFreeMassKg in 10f..<weightKg) {
                weightKg - fatFreeMassKg
            } else {
                0.1f
            }
            return ((raw * 10f).roundToInt() / 10f).coerceAtLeast(0.1f)
        }

    /**
     * Músculo Esquelético Absoluto em kg: `weightKg * (skeletalMusclePercent / 100f)`,
     * arredondado para 1 casa decimal (ex: 92.4 * 0.402 = 37.1 kg).
     */
    val skeletalMuscleKg: Float
        get() {
            val raw = weightKg * (skeletalMusclePercent / 100f)
            return ((raw * 10f).roundToInt() / 10f).coerceAtLeast(0.1f)
        }

    /**
     * Relação Músculo / Gordura (`muscleMassKg / fatMassKg`), arredondada em 2 casas decimais.
     * Ex: 65.1 / 23.7 = 2.75.
     */
    val leanToFatRatio: Float
        get() {
            val safeFat = fatMassKg.coerceAtLeast(0.5f)
            return ((muscleMassKg / safeFat) * 100f).roundToInt() / 100f
        }
}

/**
 * Avaliação individual de cada um dos 14 indicadores da bioimpedância para exibição em tabela/cards.
 */
data class BioimpedanceIndicatorAssessment(
    val indexNumber: Int,
    val titlePt: String,
    val formattedValue: String,
    val secondaryValue: String? = null,
    val referenceTargetText: String,
    val status: IndicatorStatus
)

/**
 * Item individual de perda ou ganho exibido nas colunas "GANHOS / EVOLUÇÕES" vs "PERDAS / REDUÇÕES"
 * do Card de Degrau de Evolução (`RecompStepCardSummary`).
 */
data class RecompDeltaItem(
    val metricLabel: String,
    val formattedDelta: String,
    val isPositivePhysiology: Boolean,
    val highlightColorHex: Long
)

/**
 * Barra de progresso fisiológico rumo às 4 metas principais do MAX RECOMP.
 */
data class PhysiologicalGoalProgress(
    val id: String,
    val title: String,
    val currentValue: Float,
    val targetValue: Float,
    val unit: String,
    val isLowerBetter: Boolean,
    val progressFraction: Float,
    val currentFormatted: String,
    val targetFormatted: String,
    val deltaRemainingText: String,
    val status: IndicatorStatus
)

/**
 * Resumo comparativo ("Card de Degrau de Evolução") de uma medição em relação à medição
 * imediatamente anterior (`previous`) e ao Marco Zero (`baseline`).
 */
data class RecompStepCardSummary(
    val stepIndex: Int,
    val isBaseline: Boolean,
    val current: BioimpedanceRecord,
    val previous: BioimpedanceRecord?,
    val baseline: BioimpedanceRecord,

    // Deltas diretos (vs. medição anterior, ou 0f quando Marco Zero)
    val deltaWeightKg: Float,
    val deltaFatMassKg: Float,
    val deltaBodyFatPercent: Float,
    val deltaMuscleMassKg: Float,
    val deltaSkeletalMuscleKg: Float,
    val deltaSkeletalMusclePercent: Float,
    val deltaVisceralFat: Float,
    val deltaWaterPercent: Float,
    val deltaBmrKcal: Int,
    val deltaMetabolicAge: Int,

    // Deltas acumulados desde o Marco Zero (#1)
    val cumulativeDeltaWeightKg: Float,
    val cumulativeDeltaFatMassKg: Float,
    val cumulativeDeltaSkeletalMuscleKg: Float,

    // Classificação, listas de Ganhos/Perdas e Parecer Fisiológico
    val recompClassification: RecompClassification,
    val gainsList: List<RecompDeltaItem>,
    val lossesList: List<RecompDeltaItem>,
    val coachPhysiologyVerdict: String,
    val hydrationWarning: String?
)
