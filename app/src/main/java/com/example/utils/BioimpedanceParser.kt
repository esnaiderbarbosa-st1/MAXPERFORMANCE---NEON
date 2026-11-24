package com.example.utils

import com.example.domain.recomp.BioimpedanceImageParser
import com.example.domain.recomp.BodyRecompEngine

data class BioimpedanceData(
    val peso: Float = 0f,
    val massaMagra: Float = 0f,
    val percentualGordura: Float = 0f,
    val agua: Float = 0f
)

object BioimpedanceParser {

    /**
     * Analisa um bloco de texto ou OCR de relatório Relaxmedic / Fitdays extraindo
     * Peso, Massa Magra, % de Gordura Corporal e Água Corporal.
     */
    fun parseBioimpedanceText(text: String): BioimpedanceData {
        val parsed = BioimpedanceImageParser.parseRelaxmedicOrFitdaysStructuredText(
            reconstructedText = text,
            fallbackBase = BodyRecompEngine.defaultFallbackRecord(WeightCalculator.activeUserProfile)
        ).record

        return BioimpedanceData(
            peso = parsed.weightKg,
            massaMagra = parsed.fatFreeMassKg,
            percentualGordura = parsed.bodyFatPercent,
            agua = parsed.bodyWaterPercent
        )
    }
}
