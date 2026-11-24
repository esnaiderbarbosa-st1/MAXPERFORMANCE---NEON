package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "beta_evaluations")
data class BetaEvaluationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "2.4.0-BETA",
    val buildNumber: String = "#20260724",
    val buildDate: String = "24/07/2026",
    val workoutType: String = "Peito",
    val sessionTimeMinutes: String = "45",
    // Ratings (1 to 5)
    val ratingExCount: Int = 5,
    val ratingOrderQuality: Int = 5,
    val ratingVariety: Int = 5,
    val ratingLoadProgression: Int = 5,
    val ratingEstimatedTime: Int = 5,
    val ratingRecommendation: Int = 5,
    val ratingEaseOfUse: Int = 5,
    val ratingOverallQuality: Int = 5,
    // Checklist
    val checkLoadProgression: Boolean = false,
    val checkImages: Boolean = false,
    val checkGifs: Boolean = false,
    val checkTimer: Boolean = false,
    val checkSound: Boolean = false,
    val checkFullNames: Boolean = false,
    val checkCorrectExercises: Boolean = false,
    val checkWarmup: Boolean = false,
    val checkDragDrop: Boolean = false,
    val checkReplacement: Boolean = false,
    val checkTodaySessionScreen: Boolean = false,
    val checkExecutionScreen: Boolean = false,
    val checkFinalScreen: Boolean = false,
    // Text feedback & priority
    val bugsText: String = "",
    val suggestionsText: String = "",
    val priority: String = "Média" // Crítica, Alta, Média, Baixa
) {
    fun toMarkdownReport(): String {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val dateFormatted = sdf.format(java.util.Date(timestamp))

        fun stars(valRating: Int): String {
            val filled = "★".repeat(valRating.coerceIn(1, 5))
            val empty = "☆".repeat((5 - valRating).coerceIn(0, 4))
            return "$filled$empty ($valRating/5)"
        }

        fun checkSymbol(check: Boolean) = if (check) "[x]" else "[ ]"

        return """
            # Avaliação Beta - Max Performance

            **Versão:** $appVersion ($buildNumber)
            **Data da Build:** $buildDate
            **Data da Avaliação:** $dateFormatted
            **Treino Realizado:** $workoutType ($sessionTimeMinutes min)
            **Prioridade:** $priority

            ---

            ## 📊 Experiência
            - Quantidade de exercícios: ${stars(ratingExCount)}
            - Qualidade da ordem: ${stars(ratingOrderQuality)}
            - Variedade: ${stars(ratingVariety)}
            - Progressão de carga: ${stars(ratingLoadProgression)}
            - Tempo estimado: ${stars(ratingEstimatedTime)}
            - Recomendação dos exercícios: ${stars(ratingRecommendation)}
            - Facilidade de uso: ${stars(ratingEaseOfUse)}
            - Qualidade geral: ${stars(ratingOverallQuality)}

            ---

            ## ✅ Checklist de Recursos
            - ${checkSymbol(checkLoadProgression)} Progressão de carga correta
            - ${checkSymbol(checkImages)} Imagens corretas
            - ${checkSymbol(checkGifs)} GIF funcionando
            - ${checkSymbol(checkTimer)} Timer correto
            - ${checkSymbol(checkSound)} Som correto
            - ${checkSymbol(checkFullNames)} Nome completo dos exercícios
            - ${checkSymbol(checkCorrectExercises)} Exercícios corretos
            - ${checkSymbol(checkWarmup)} Aquecimento correto
            - ${checkSymbol(checkDragDrop)} Drag & Drop funcionando
            - ${checkSymbol(checkReplacement)} Substituição funcionando
            - ${checkSymbol(checkTodaySessionScreen)} Tela Sessão de Hoje correta
            - ${checkSymbol(checkExecutionScreen)} Tela Execução correta
            - ${checkSymbol(checkFinalScreen)} Tela Final correta

            ---

            ## 🐛 Bugs Relatados
            ${if (bugsText.isNotBlank()) bugsText else "Nenhum bug reportado."}

            ---

            ## 💡 Sugestões
            ${if (suggestionsText.isNotBlank()) suggestionsText else "Nenhuma sugestão enviada."}
        """.trimIndent()
    }
}
