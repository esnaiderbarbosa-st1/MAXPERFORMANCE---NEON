package com.example.utils

import java.text.Normalizer
import java.util.Locale

object FuzzySearchUtils {

    /**
     * Normalizes a string by removing accents/diacritics and correcting common PT-BR exercise typos/phonetics.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val temp = Normalizer.normalize(text, Normalizer.Form.NFD)
        var s = Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").lowercase(Locale.ROOT)
        
        // Common PT-BR workout phonetic / typo mappings
        s = s.replace("tricepes", "triceps")
            .replace("triseps", "triceps")
            .replace("tricep", "triceps")
            .replace("bicepes", "biceps")
            .replace("biseps", "biceps")
            .replace("bicep", "biceps")
            .replace("quadricepes", "quadriceps")
            .replace("quadriseps", "quadriceps")
            .replace("quadricep", "quadriceps")
            .replace("elevacao", "elevacao")
            .replace("exercicio", "exercicio")
        
        return Regex("[^a-z0-9\\s]").replace(s, " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Checks if a target string matches the search query.
     * Supports:
     * - Case-insensitivity
     * - Accent/Diacritic removal (e.g. "tríceps" -> "triceps")
     * - Substring matching
     * - Phonetic/typo mapping (e.g. "tricepes", "triseps")
     * - Levenshtein fuzzy distance matching per token
     */
    fun matches(query: String, target: String): Boolean {
        if (query.isBlank()) return true
        if (target.isBlank()) return false

        val normQuery = normalize(query)
        val normTarget = normalize(target)

        if (normQuery.isBlank()) return true
        if (normTarget.contains(normQuery)) return true

        val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }
        val targetTokens = normTarget.split(" ").filter { it.isNotBlank() }

        return queryTokens.all { qToken ->
            // Substring match in full target
            if (normTarget.contains(qToken)) return@all true

            // Token fuzzy match
            targetTokens.any { tToken ->
                if (tToken.contains(qToken) || qToken.contains(tToken)) {
                    true
                } else {
                    val maxDistance = when {
                        qToken.length <= 3 -> 0
                        qToken.length <= 6 -> 1
                        else -> 2
                    }
                    levenshteinDistance(qToken, tToken) <= maxDistance
                }
            }
        }
    }
}
