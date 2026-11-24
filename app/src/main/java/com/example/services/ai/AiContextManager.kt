package com.example.services.ai

import com.example.data.model.UserProfileData
import com.example.data.model.WorkoutHistoryEntry
import com.example.data.model.CardioSession
import com.example.data.model.BioimpedanceRecord
import com.example.utils.MuscleRecovery
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the complete user health & fitness state for AI interpretation.
 */
data class UserFitnessContext(
    val profile: UserProfileData?,
    val recentHistory: List<WorkoutHistoryEntry>,
    val recoveryList: List<MuscleRecovery>,
    val bioimpedanceHistory: List<BioimpedanceRecord>,
    val recentCardio: List<CardioSession>
)

/**
 * Centralized manager for generating highly contextualized prompts from user data.
 * Transforms the AI from a generic chatbot into a precise, offline-fallback-friendly fitness data interpreter.
 */
object AiContextManager {

    /**
     * Builds a structured prompt focusing on interpreting the user's actual data.
     */
    fun buildInterpreterPrompt(userQuery: String, context: UserFitnessContext): String {
        val sb = StringBuilder()
        
        // 1. Establish the professional role as an expert interpreter
        sb.append("Você é o Intérprete de Dados de Alta Performance da MAX PERFORMANCE. ")
        sb.append("Sua função NÃO é ser um chatbot genérico de bate-papo, mas sim analisar de forma precisa e técnica o estado físico, histórico, metas e recuperação do usuário para fornecer interpretações clínicas e esportivas baseadas em dados reais.\n\n")

        // 2. Inject User Profile & Goals
        val profile = context.profile
        if (profile != null) {
            sb.append("=== PERFIL E METAS DO USUÁRIO ===\n")
            sb.append("- Nome: ${profile.nome}\n")
            sb.append("- Idade: ${profile.idade} anos\n")
            sb.append("- Gênero: ${profile.sexo}\n")
            sb.append("- Altura: ${profile.altura} cm\n")
            sb.append("- Nível de Experiência: ${profile.nivelExperiencia}\n")
            sb.append("- Local de Treino Preferencial: ${profile.localTreino}\n")
            sb.append("- Meta Semanal: ${profile.metaSemanal} dias de treino por semana\n\n")
        }

        // 3. Inject Bioimpedance (Body Composition)
        val latestBio = context.bioimpedanceHistory.filter { !it.archived }.maxByOrNull { it.date }
        if (latestBio != null) {
            sb.append("=== COMPOSIÇÃO CORPORAL RECENTE (BIOIMPEDÂNCIA) ===\n")
            sb.append("- Peso Atual: ${latestBio.peso} kg (IMC: ${latestBio.imc})\n")
            sb.append("- Percentual de Gordura: ${latestBio.percentualGordura}%\n")
            sb.append("- Massa Magra: ${latestBio.massaMagra} kg\n")
            sb.append("- Massa Muscular Esquelética: ${latestBio.massaMuscularEsqueletica} kg\n")
            sb.append("- Água Corporal: ${latestBio.aguaCorporal}%\n")
            sb.append("- Gordura Visceral: Nível ${latestBio.gorduraVisceral}\n")
            sb.append("- Taxa Metabólica Basal (TMB): ${latestBio.tmb} kcal\n")
            sb.append("- Idade Metabólica: ${latestBio.idadeMetabolica} anos\n\n")
        } else if (profile != null && profile.peso > 0f) {
            sb.append("=== COMPOSIÇÃO CORPORAL RECENTE ===\n")
            sb.append("- Peso: ${profile.peso} kg\n")
            sb.append("- Percentual de Gordura Estimado: ${profile.percentualGordura}%\n\n")
        }

        // 4. Inject Muscle Recovery Status
        if (context.recoveryList.isNotEmpty()) {
            sb.append("=== RECOVERY E RECUPERAÇÃO MUSCULAR ===\n")
            context.recoveryList.forEach { rec ->
                val status = if (rec.recoveryPercent >= 0.9f) "Excelente" else if (rec.recoveryPercent >= 0.7f) "Moderada" else "Fadiga Alta (Requer Atenção)"
                sb.append("- ${rec.name}: ${(rec.recoveryPercent * 100).toInt()}% de recuperação ($status)\n")
            }
            sb.append("\n")
        }

        // 5. Inject Recent Workout Performance & Volume
        if (context.recentHistory.isNotEmpty()) {
            sb.append("=== HISTÓRICO DE TREINOS RECENTES (ÚLTIMOS 5) ===\n")
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            context.recentHistory.sortedByDescending { it.date }.take(5).forEach { entry ->
                sb.append("- Treino: ${entry.workoutName} em ${sdf.format(Date(entry.date))} (Duração: ${entry.durationSeconds / 60} min, Carga Total: ${entry.totalWeight} kg, Exercícios: ${entry.exercisesCompletedCount})\n")
            }
            sb.append("\n")
        }

        // 6. Inject Recent Cardiovascular Training
        if (context.recentCardio.isNotEmpty()) {
            sb.append("=== TREINOS CARDIOVASCULARES RECENTES ===\n")
            context.recentCardio.sortedByDescending { it.date }.take(3).forEach { cardio ->
                sb.append("- Tipo: ${cardio.type} | Duração: ${cardio.durationMinutes} min | Distância: ${cardio.distance} km | FC Média: ${cardio.avgHr} bpm | Calorias: ${cardio.calories} kcal\n")
            }
            sb.append("\n")
        }

        // 7. Inject User Request
        sb.append("=== PERGUNTA OU SOLICITAÇÃO DE FOCO ===\n")
        sb.append("O usuário enviou a seguinte mensagem/dúvida específica:\n")
        sb.append("\"$userQuery\"\n\n")

        // 8. Output Constraints & Formatting Rules
        sb.append("=== INSTRUÇÕES DE SAÍDA ===\n")
        sb.append("1. Responda em português, usando o estilo motivador, altamente técnico e direto do treinador Mauricio Pacholok.\n")
        sb.append("2. Interprete os dados acima de forma integrada. Por exemplo, se o usuário pedir um treino de pernas e a recuperação de pernas estiver baixa, alerte-o e sugira ajustes ou técnicas regenerativas.\n")
        sb.append("3. Se o objetivo do usuário for hipertrofia ou emagrecimento, cruze isso com a Taxa Metabólica Basal e as calorias do cardio recente.\n")
        sb.append("4. Forneça números concretos, técnicas de intensidade (Drop-sets, Rest-pause, Bi-sets, Pico de contração de 2s) e orientações baseadas em evidências.\n")
        sb.append("5. Mantenha a resposta concisa, estruturada com marcadores de tópicos e altamente legível.")

        return sb.toString()
    }
}
