package com.example.services.ai

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Key-value local coach as backup if API is unavailable or keys are placeholders
    private val RULE_BASED_COACH_RESPONSES = mapOf(
        "peito" to """
            🔥 *TREINO DE PEITO, OMBROS E TRÍCEPS (ESTILO PACHOLOK)* 🔥
            
            *AQUECIMENTO:*
            - Geral: 5 a 10 min de bike/esteira.
            - Mobilidade de ombros com elástico/bastão.
            - Aclimação: 3 séries progressivas (15 reps leve, 12 reps média, 10 reps pesada) no primeiro exercício.
            
            *FICHA DE TREINO:*
            1. **Supino Inclinado Smith ou Máquina**: 4 séries
               - Série 1: 1x15 (ativação/carga leve a média) - Descanso: 90s
               - Série 2: 1x10 (progressão de carga) - Descanso: 2 min
               - Série 3: 1x8 (carga pesada) - Descanso: 2 min
               - Série 4: 1x6 + 2 Drop-sets (até a falha total) - Descanso: 3 min
            2. **Supino Reto com Halteres**: 3 séries x 8 a 12 repetições
               - Descanso: 2 min (foco em progressão de carga e máxima amplitude)
            3. **Crucifixo Inclinado Halteres + Supino Inclinado Halteres (Bi-set)**: 3 séries x 10 repetições
               - Descanso: 60s entre os bi-sets.
            4. **Elevação Lateral Unilateral Cabo (por trás do corpo)**: 3 séries x 10 a 12 repetições com Pico de Contração de 2 segundos.
               - Descanso: 45s.
            5. **Desenvolvimento Sentado com Halteres**: 3 séries x 8 a 12 repetições.
               - Descanso: 90s.
            6. **Tríceps Testa com Corda no Banco 45**: 4 séries
               - 1x15, 1x10, 1x8, 1x6 + 2 Drop-sets. Descanso: 2 min.
            7. **Tríceps Corda na Polia**: 3 séries x 8 a 12 repetições com Pico de Contração de 2 segundos.
               - Descanso: 45s.
               
            *DICA DO COACH:* Mantenha as escápulas aduzidas e os cotovelos sob controle na fase excêntrica (3 segundos na descida). Descanso e alimentação hiperproteica são as chaves!
        """.trimIndent(),
        "ombros" to """
            🔥 *TREINO DE OMBROS DETALHADO (ESTILO PACHOLOK)* 🔥
            
            *AQUECIMENTO:*
            - Geral: 5 a 10 min de bike/esteira.
            - Mobilidade de ombros com elástico.
            - Aclimação: 2 séries de elevação lateral bem leve (20 reps).
            
            *FICHA DE TREINO:*
            1. **Desenvolvimento Sentado com Halteres**: 4 séries
               - 1x15 (ativação), 1x10 (carga média), 1x8 (carga alta), 1x6 + Strip-set (até a falha) - Descanso: 2 min.
            2. **Elevação Lateral Unilateral Banco 45**: 3 séries x 8 a 12 repetições (foco na porção lateral isolada) - Descanso: 45s.
            3. **Elevação Lateral no Cabo**: 3 séries x 8 a 12 repetições com Pico de Contração de 2 segundos - Descanso: 45s.
            4. **Crucifixo Invertido Cross / Halteres**: 3 séries x 12 repetições com Pico de Contração de 2s - Descanso: 60s.
            
            *DICA DO COACH:* Ombros largos constroem a silhueta em V perfeita. Nunca balance o tronco na elevação lateral!
        """.trimIndent(),
        "triceps" to """
            🔥 *TREINO DE TRÍCEPS COMPLETO (ESTILO PACHOLOK)* 🔥
            
            *FICHA DE TREINO:*
            1. **Tríceps Testa com Corda no Banco 45**: 4 séries
               - 1x15 (aquecimento), 1x10 (carga média), 1x8 (pesado), 1x6 + 2 Drop-sets (falha total) - Descanso: 90s.
            2. **Tríceps Corda na Polia**: 3 séries x 8 a 12 repetições com Pico de Contração de 2 segundos - Descanso: 45s.
            3. **Tríceps Francês Sentado com Corda / Halter**: 3 séries x 12 repetições com Drop na última série - Descanso: 60s.
            
            *DICA DO COACH:* O tríceps representa 60% do volume do braço. Controle a descida lenta!
        """.trimIndent(),
        "costas" to "Coach IA: Para dorsais largas, o foco deve ser Barra Fixa Pronada (4x Max reps) seguido de Remada Curvada com Barra (3x10). Puxe iniciando pelos cotovelos, ativando a musculatura das costas e minimizando o esforço dos bíceps.",
        "pernas" to "Coach IA: O rei das pernas é o Agachamento Livre com Barra (4x8). Para focar nos quadríceps, adicione Leg Press 45 (3x12) com cadência controlada (3s na descida). Hidratação e descanso de 90s entre as séries são cruciais.",
        "biceps" to "Coach IA: Rosca Direta na Barra W (4x10) com contração de pico de 1s no topo. Combine com Rosca Martelo (3x12) para desenvolver o braquiorradial e dar volume geral ao braço.",
        "cardio" to "Coach IA: Para maximizar queima de calorias sem perder massa magra, recomendo HIIT de 20 minutos na esteira (1 min corrida tiro intenso, 1 min caminhada ativa). Faça pós-treino ou em dias alternados.",
        "suplemento" to "Coach IA: Creatina (3-5g diários de forma contínua) é o suplemento nº 1 para força. Whey protein ajuda a bater a meta de proteínas (mire em 1.6g a 2.0g por kg de peso corporal para hipertrofia)."
    )

    suspend fun generateExerciseSuggestion(prompt: String): String {
        return generateExerciseSuggestion(prompt, UserFitnessContext(null, emptyList(), emptyList(), emptyList(), emptyList()))
    }

    suspend fun generateExerciseSuggestion(prompt: String, context: UserFitnessContext): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY_1
        
        // If api key is placeholder or empty, use fallback coach response to remain functional
        if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI") || apiKey.contains("placeholder")) {
            return@withContext getLocalCoachFallback(prompt)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val textPrompt = AiContextManager.buildInterpreterPrompt(prompt, context)
        val escapedPrompt = textPrompt.replace("\"", "\\\"").replace("\n", "\\n")
        
        val jsonPayload = """
            {
                "contents": [{
                    "parts": [{
                        "text": "$escapedPrompt"
                    }]
                }]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext getLocalCoachFallback(prompt)
                }
                val bodyString = response.body?.string() ?: return@withContext getLocalCoachFallback(prompt)
                try {
                    val adapter = moshi.adapter(GeminiResponse::class.java)
                    val responseObj = adapter.fromJson(bodyString)
                    val parsed = responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (parsed != null) {
                        return@withContext parsed
                    }
                } catch (e: Exception) {}
                return@withContext getLocalCoachFallback(prompt)
            }
        } catch (e: Exception) {
            return@withContext getLocalCoachFallback(prompt)
        }
    }

    private fun getLocalCoachFallback(prompt: String): String {
        val lowercase = prompt.lowercase()
        for ((key, response) in RULE_BASED_COACH_RESPONSES) {
            if (lowercase.contains(key)) {
                return response
            }
        }
        return "Coach IA: Treinar com máxima performance exige dedicação! Foque em progredir cargas de forma linear (adicione +1kg a cada treino), controle a alimentação com excedente calórico leve para ganho de massa, e durma pelo menos 7-8h por noite."
    }

    suspend fun extractCardioFromImage(base64Image: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY_1
        
        if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI") || apiKey.contains("placeholder")) {
            android.util.Log.w("GeminiService", "Chave API do Gemini ausente para cardio")
            throw Exception("Chave da API Gemini não configurada. Não foi possível ler o print.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val prompt = "Analyze this smartwatch activity screenshot (usually from apps like Haylou Fun or Strava). Perform a strict double-verification pass over all metrics you read to prevent OCR mistakes. Ensure duration, distance, calories, and average heart rate are mathematically consistent. Also, look very carefully for any date of the activity visible in the image (e.g. '2026-06-25', '25/06/2026') and parse/return it as a string in 'YYYY-MM-DD' format (or 'YYYY-MM-DD HH:mm') in the 'date' key. Also look for activity type (e.g. running, walking, cycling) and include it in 'type' key. Return ONLY a valid JSON object with the following keys, and numeric/string values. Keys: distance, durationSeconds, calories, avgHr, steps, pace, fatBurnZone, anaerobicZone, limitZone, date, type."

        val cleanBase64 = (if (base64Image.contains(",")) base64Image.substringAfter(",") else base64Image).replace("\\s".toRegex(), "")

        val jsonPayload = """
            {
                "contents": [{
                    "parts": [
                        { "text": "$prompt" },
                        { "inlineData": { "mimeType": "image/jpeg", "data": "$cleanBase64" } }
                    ]
                }],
                "generationConfig": {
                    "responseMimeType": "application/json"
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw Exception("Erro na chamada à API do Gemini (status ${response.code}). Detalhe: $errBody")
                }
                val bodyString = response.body?.string() ?: throw Exception("Resposta vazia da API do Gemini")
                val adapter = moshi.adapter(GeminiResponse::class.java)
                val responseObj = adapter.fromJson(bodyString)
                var parsed = responseObj?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw Exception("Não foi possível extrair a estrutura de texto da resposta do Gemini.")
                if (parsed.contains("```")) {
                    parsed = parsed.substringAfter("```json").substringAfter("```").substringBefore("```")
                }
                return@withContext parsed.trim()
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Erro no escaneamento de atividade cardio", e)
            throw Exception(e.message ?: "Erro ao ler o print de cardio pela API Gemini.", e)
        }
    }
}

// Moshi response classes
class GeminiResponse(val candidates: List<GeminiCandidate>?)
class GeminiCandidate(val content: GeminiContent?)
class GeminiContent(val parts: List<GeminiPart>?)
class GeminiPart(val text: String?)
