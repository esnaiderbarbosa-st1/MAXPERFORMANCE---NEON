package com.example.domain.recomp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.UserProfileData
import com.example.data.model.resolveValidHeightCmOrNull
import com.example.utils.WeightCalculator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Formato detectado pelo extrator inteligente de relatórios de bioimpedância
 * (`Fitdays` vertical teal/verde-água vs `Relaxmedic` ficha técnica branca de 2 colunas).
 */
enum class BioimpedanceDetectedTemplate(
    val id: String,
    val chipLabel: String,
    val sourceTitle: String,
    val summaryDescription: String
) {
    FITDAYS_29_10(
        id = "fitdays_29_10",
        chipLabel = "Importar leitura salva: Fitdays 29/10",
        sourceTitle = "Leitura salva • Fitdays (29/10/2026)",
        summaryDescription = "Importa a leitura salva Fitdays (29/10/2026) para revisão antes de salvar."
    ),
    RELAXMEDIC_13_10(
        id = "relaxmedic_13_10",
        chipLabel = "Importar leitura salva: Relaxmedic 13/10",
        sourceTitle = "Leitura salva • Relaxmedic (13/10/2026)",
        summaryDescription = "Importa a leitura salva Relaxmedic (13/10/2026) convertida de kg para % para revisão antes de salvar."
    ),
    MANUAL_ENTRY(
        id = "manual_entry",
        chipLabel = "Entrada Manual",
        sourceTitle = "Preenchimento Manual / Editável",
        summaryDescription = "Edite livremente qualquer um dos 14 indicadores ou cole os dados brutos do aplicativo."
    ),
    NAO_DETECTADO(
        id = "nao_detectado",
        chipLabel = "Não detectado",
        sourceTitle = "Leitura automática indisponível",
        summaryDescription = "Não foi possível ler este exame automaticamente. Preencha os valores manualmente."
    )
}

/**
 * Resultado estruturado da leitura automática de imagem (JPG/PNG) ou PDF de bioimpedância.
 */
data class ParsedBioimpedanceResult(
    val record: BioimpedanceRecord,
    val detectedTemplate: BioimpedanceDetectedTemplate,
    val statusHeadline: String,
    val conversionDetails: String,
    val wasConvertedFromKgToPercent: Boolean,
    val filledIndicatorsCount: Int = 14,
    val renderedBitmap: Bitmap? = null,
    val reconstructedOcrText: String = ""
)

/**
 * Extrator Especialista de Bioimpedância para Relatórios **Relaxmedic** & **Fitdays**
 * (suporta Imagem JPG/PNG e PDF via `PdfRenderer` + **ML Kit Text Recognition** com
 * Reconstrução Geométrica de Linhas por Coluna para relatórios de 2 colunas).
 */
object BioimpedanceImageParser {

    const val TOTAL_EXPECTED_INDICATORS = 14
    const val OCR_FAILURE_HEADLINE =
        "Não foi possível ler este exame automaticamente. Preencha os valores manualmente."

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Retorna os chips de importação de leitura salva disponíveis para o perfil ativo.
     * Apenas perfis com metas pessoais configuradas (`hasPersonalGoals`) recebem os chips.
     */
    fun availableTemplatesForProfile(profile: UserProfileData?): List<BioimpedanceDetectedTemplate> {
        return if (BioimpedanceReferences.hasPersonalGoals(profile)) {
            listOf(
                BioimpedanceDetectedTemplate.FITDAYS_29_10,
                BioimpedanceDetectedTemplate.RELAXMEDIC_13_10,
                BioimpedanceDetectedTemplate.MANUAL_ENTRY
            )
        } else {
            emptyList()
        }
    }

    private data class OcrPositionedPiece(
        val text: String,
        val box: Rect,
        val centerX: Float,
        val centerY: Float
    )

    /**
     * Pipeline assíncrono completo para importar um arquivo de relatório (Imagem JPG/PNG ou PDF):
     * 1. Se PDF, renderiza a 1ª página em um `Bitmap` de alta resolução (`width = 1600`) com `PdfRenderer`.
     * 2. Se Imagem, decodifica em alta resolução preservando nitidez das 2 colunas.
     * 3. Executa o **ML Kit Text Recognition** (`TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`).
     * 4. Aplica a **Reconstrução Geométrica de Linhas por Coluna** (`x < pageWidth * 0.58f` vs `x >= pageWidth * 0.58f`,
     *    agrupando por `abs(lineA.centerY - lineB.centerY) <= 18px` e ordenando por `centerX`).
     * 5. Extrai os 14 indicadores oficiais + dados segmentares + pontuação corporal.
     */
    suspend fun extractFromUri(
        context: Context,
        uri: Uri,
        existingRecords: List<BioimpedanceRecord>,
        fallbackBase: BioimpedanceRecord,
        profileHeightCm: Float? = resolveValidHeightCmOrNull(WeightCalculator.activeUserProfile)?.toFloat(),
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): ParsedBioimpedanceResult = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(context, uri)
        val mimeType = runCatching { context.contentResolver.getType(uri) ?: "" }.getOrDefault("")
        val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
            fileName.lowercase(ptBr).endsWith(".pdf")

        val bitmap: Bitmap? = if (isPdf) {
            renderPdfFirstPageHighRes(context, uri, targetWidth = 1600)
        } else {
            decodeHighResBitmap(context, uri, targetWidth = 1600)
        }

        if (bitmap != null) {
            val ocrText = runMlKitOcrAndReconstructColumns(bitmap)
            if (ocrText.isNotBlank()) {
                val parsedRes = parseRelaxmedicOrFitdaysStructuredText(
                    reconstructedText = ocrText,
                    fallbackBase = fallbackBase,
                    photoUri = uri.toString(),
                    renderedBitmap = bitmap,
                    profileHeightCm = profileHeightCm
                )
                if (parsedRes.filledIndicatorsCount >= 4) {
                    return@withContext parsedRes
                }
            }
        }

        // Fallback caso a imagem não contenha texto legível ou menos de 4 indicadores no OCR
        analyzeAttachedImage(
            context = context,
            uri = uri,
            bitmap = bitmap,
            existingRecords = existingRecords,
            fallbackBase = fallbackBase,
            profile = profile
        ).copy(renderedBitmap = bitmap)
    }

    /**
     * Renderiza a 1ª página de um PDF em um `Bitmap` de alta resolução (`width = 1600`)
     * sobre fundo branco puro usando `android.graphics.pdf.PdfRenderer`.
     */
    fun renderPdfFirstPageHighRes(
        context: Context,
        uri: Uri,
        targetWidth: Int = 1600
    ): Bitmap? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return@use null
                    renderer.openPage(0).use { page ->
                        val srcW = page.width.coerceAtLeast(1)
                        val srcH = page.height.coerceAtLeast(1)
                        val targetHeight = ((targetWidth.toFloat() * srcH.toFloat()) / srcW.toFloat())
                            .roundToInt()
                            .coerceIn(1000, 4096)
                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * Decodifica uma imagem JPG/PNG garantindo largura próxima a `1600px` para máxima precisão
     * geométrica no OCR de relatórios de 2 colunas.
     */
    private fun decodeHighResBitmap(
        context: Context,
        uri: Uri,
        targetWidth: Int = 1600
    ): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            if (boundsOpts.outWidth > targetWidth * 2) {
                while (boundsOpts.outWidth / (sampleSize * 2) >= targetWidth) {
                    sampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            if (rawBmp.width in 1200..2200) {
                rawBmp
            } else {
                val scaledH = ((targetWidth.toFloat() * rawBmp.height.toFloat()) / rawBmp.width.coerceAtLeast(1).toFloat())
                    .roundToInt()
                    .coerceIn(800, 4096)
                Bitmap.createScaledBitmap(rawBmp, targetWidth, scaledH, true)
            }
        }.getOrNull()
    }

    /**
     * Executa o **ML Kit Text Recognition** e reconstrói geometricamente as linhas separando:
     * - **Coluna Esquerda:** `x < pageWidth * 0.58f`
     * - **Coluna Direita:** `x >= pageWidth * 0.58f`
     * Agrupa elementos/linhas com `abs(lineA.centerY - lineB.centerY) <= 18px` e ordena por `centerX`.
     */
    suspend fun runMlKitOcrAndReconstructColumns(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val visionText: Text? = suspendCancellableCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener {
                    recognizer.close()
                    if (cont.isActive) cont.resume(null)
                }
        }

        if (visionText == null) return@withContext ""
        reconstructTwoColumnTextFromVision(visionText, bitmap.width, bitmap.height)
    }

    /**
     * Reconstrução Geométrica de Linhas por Coluna (Coluna Esquerda `x < pageWidth * 0.58f` |
     * Coluna Direita `x >= pageWidth * 0.58f`) com agrupamento vertical `<= 18px` e ordenação horizontal `centerX`.
     */
    private fun reconstructTwoColumnTextFromVision(
        visionText: Text,
        pageWidth: Int,
        pageHeight: Int
    ): String {
        val pieces = mutableListOf<OcrPositionedPiece>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val txt = line.text.trim()
                if (txt.isNotEmpty()) {
                    pieces.add(
                        OcrPositionedPiece(
                            text = txt,
                            box = box,
                            centerX = box.exactCenterX(),
                            centerY = box.exactCenterY()
                        )
                    )
                }
            }
        }
        if (pieces.isEmpty()) return visionText.text

        val splitX = pageWidth * 0.58f
        val leftEdgeSplitX = pageWidth * 0.53f
        val yTolerancePx = max(18f, pageHeight * 0.0082f)

        // Separa em Coluna Esquerda e Coluna Direita
        val leftColumnPieces = pieces.filter { piece ->
            piece.centerX < splitX && piece.box.left < leftEdgeSplitX
        }
        val rightColumnPieces = pieces.filter { piece ->
            piece.centerX >= splitX || piece.box.left >= leftEdgeSplitX
        }

        val leftLines = clusterPiecesIntoLines(leftColumnPieces, yTolerancePx)
        val rightLines = clusterPiecesIntoLines(rightColumnPieces, yTolerancePx)
        val fullWidthLines = clusterPiecesIntoLines(pieces, yTolerancePx)

        return buildString {
            appendLine("=== COLUNA_ESQUERDA ===")
            leftLines.forEach { appendLine(it) }
            appendLine("=== COLUNA_DIREITA ===")
            rightLines.forEach { appendLine(it) }
            appendLine("=== LINHA_COMPLETA ===")
            fullWidthLines.forEach { appendLine(it) }
        }
    }

    private fun clusterPiecesIntoLines(
        columnPieces: List<OcrPositionedPiece>,
        yTolerancePx: Float
    ): List<String> {
        if (columnPieces.isEmpty()) return emptyList()
        val sortedByY = columnPieces.sortedBy { it.centerY }
        val clusters = mutableListOf<MutableList<OcrPositionedPiece>>()

        for (piece in sortedByY) {
            val matchingCluster = clusters.lastOrNull { cluster ->
                val avgY = cluster.map { it.centerY }.average().toFloat()
                abs(piece.centerY - avgY) <= yTolerancePx
            }
            if (matchingCluster != null) {
                matchingCluster.add(piece)
            } else {
                clusters.add(mutableListOf(piece))
            }
        }

        return clusters.map { cluster ->
            cluster
                .sortedBy { it.centerX }
                .joinToString(" ") { it.text }
                .replace(Regex("\\s+"), " ")
                .trim()
        }.filter { it.isNotEmpty() }
    }

    /**
     * Parser Especialista de Relatórios **Relaxmedic** & **Fitdays** (2 colunas ou 1 coluna).
     * Aplica todas as regras de extração da tabela "Análise da composição corporal",
     * gráficos de barras ("Análise de gordura muscular" / "Análise de obesidade"),
     * coluna direita ("Pontuação corporal", "Controle de peso", "Outros indicadores")
     * e avaliação segmentar (Braços, Tronco, Pernas).
     */
    fun parseRelaxmedicOrFitdaysStructuredText(
        reconstructedText: String,
        fallbackBase: BioimpedanceRecord,
        photoUri: String? = fallbackBase.photoUri,
        renderedBitmap: Bitmap? = null,
        nowMillis: Long = System.currentTimeMillis(),
        profileHeightCm: Float? = resolveValidHeightCmOrNull(WeightCalculator.activeUserProfile)?.toFloat()
    ): ParsedBioimpedanceResult {
        val allLinesWithMarkers = reconstructedText
            .lines()
            .map { it.replace("\u00A0", " ").trim() }
            .filter { it.isNotBlank() }

        val rawLines = allLinesWithMarkers.filter { !it.startsWith("===") }

        var heightCm: Float? = profileHeightCm?.takeIf { it in 100f..230f }
        var detectedDateLabel: String? = extractExamDateTimeFromReport(reconstructedText, rawLines)
        var bodyScore: Int? = null

        var weightKg: Float? = null
        var bmi: Float? = null
        var fatMassKg: Float? = null
        var bodyFatPercent: Float? = null
        var waterKg: Float? = null
        var bodyWaterPercent: Float? = null
        var muscleMassKg: Float? = null
        var muscleRatePercent: Float? = null
        var skeletalMuscleKg: Float? = null
        var skeletalMusclePercent: Float? = null
        var proteinKg: Float? = null
        var proteinPercent: Float? = null
        var boneMassKg: Float? = null
        var visceralFatIndex: Float? = null
        var bmrKcal: Int? = null
        var fatFreeMassKg: Float? = null
        var subcutaneousFatPercent: Float? = null
        var metabolicAge: Int? = null

        val segmentalParts = mutableListOf<String>()

        // Escala padrão das réguas dos gráficos de barras para ignorar marcas de eixo (inclui 10.0 e 15.0 da régua de IMC/PGC)
        val barScaleTicks = setOf(
            55f, 70f, 85f, 100f, 115f, 130f, 145f, 160f, 175f, 190f, 205f,
            10.0f, 15.0f, 18.5f, 20.0f, 22.0f, 25.0f, 30.0f, 35.0f
        )

        val dateRegex = Regex(
            "([0-3]?\\d\\s*[/.-]\\s*[0-1]?\\d\\s*[/.-]\\s*20\\d{2}\\s*-?\\s*[0-2]?\\d:[0-5]\\d|[0-2]?\\d:[0-5]\\d\\s+[0-3]?\\d\\s*[/.-]\\s*[0-1]?\\d\\s*[/.-]\\s*20\\d{2}|[0-3]?\\d\\s*[/.-]\\s*[0-1]?\\d\\s*[/.-]\\s*20\\d{2})"
        )
        val heightRegex = Regex("altura\\s*:?\\s*(\\d{2,3}(?:[.,]\\d+)?)\\s*cm", RegexOption.IGNORE_CASE)
        val scoreRegex = Regex("(\\d{2,3})\\s*/\\s*100\\s*(?:pontos)?", RegexOption.IGNORE_CASE)

        for (i in rawLines.indices) {
            val line = rawLines[i]
            val normLine = normalizeForMatch(line)

            // 1. Cabeçalho: Altura e Data/Hora da Medição
            heightRegex.find(line)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()?.let { h ->
                if (h in 100f..230f) heightCm = h
            }
            if (detectedDateLabel == null || !detectedDateLabel!!.contains(":")) {
                dateRegex.find(line)?.value?.let { rawDate ->
                    val candidate = normalizeDateTimeLabel(rawDate)
                    if (detectedDateLabel == null || candidate.contains(":")) {
                        detectedDateLabel = candidate
                    }
                }
            }

            // 2. Pontuação corporal (ex: "73 /100 Pontos")
            if (bodyScore == null && (normLine.contains("pontuacao") || normLine.contains("/100") || normLine.contains("pontos"))) {
                scoreRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { sc ->
                    if (sc in 30..100) bodyScore = sc
                }
            }

            // Remove faixas de referência entre parênteses como (65.4-88.5) ou (9.2-18.4)
            val numbers = extractNumbersOutsideReferenceRanges(line)
            val nextLineNumbers = if (numbers.isEmpty() && i + 1 < rawLines.size) {
                extractNumbersOutsideReferenceRanges(rawLines[i + 1])
            } else {
                emptyList()
            }
            val effectiveNumbers = numbers.ifEmpty { nextLineNumbers }

            // 3. Tabela "Análise da composição corporal" & Gráficos de Barras
            when {
                // Peso corporal Livre de Gordura (Coluna Direita - Outros indicadores)
                normLine.contains("livre de gordura") ||
                    normLine.contains("sem gordura") ||
                    normLine.contains("massa magra livre") -> {
                    effectiveNumbers.firstOrNull { it in 30f..160f }?.let { ffm ->
                        if (fatFreeMassKg == null) fatFreeMassKg = ffm
                    }
                }

                // Grau de gordura visceral
                normLine.contains("visceral") -> {
                    effectiveNumbers.firstOrNull { it in 1f..35f }?.let { visc ->
                        if (visceralFatIndex == null) visceralFatIndex = visc
                    }
                }

                // Taxa metabólica basal (TMB)
                normLine.contains("taxa metabolica basal") ||
                    normLine.contains("metabolismo basal") ||
                    normLine.startsWith("tmb") ||
                    normLine.contains(" kcal") -> {
                    val kcalRegex = Regex("(\\d{3,4})\\s*kcal", RegexOption.IGNORE_CASE)
                    val explicitKcal = kcalRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val candidate = explicitKcal ?: effectiveNumbers.firstOrNull { it in 900f..4500f }?.roundToInt()
                    if (candidate != null && candidate in 900..4500 && bmrKcal == null) {
                        bmrKcal = candidate
                    }
                }

                // Gordura subcutânea
                normLine.contains("subcutanea") -> {
                    effectiveNumbers.firstOrNull { it in 2f..55f }?.let { sub ->
                        if (subcutaneousFatPercent == null) subcutaneousFatPercent = sub
                    }
                }

                // Idade do corpo / Idade metabólica
                normLine.contains("idade do corpo") ||
                    normLine.contains("idade metabolica") ||
                    normLine.contains("idade fisica") -> {
                    effectiveNumbers.firstOrNull { it in 12f..99f }?.roundToInt()?.let { age ->
                        if (metabolicAge == null) metabolicAge = age
                    }
                }

                // Taxa de gordura corporal (%) no gráfico de barras "Análise de obesidade"
                normLine.contains("taxa de gordura corporal") || normLine.startsWith("pgc") -> {
                    val candidates = effectiveNumbers.filter { it in 3f..65f && it !in barScaleTicks }
                    val picked = candidates.lastOrNull() ?: effectiveNumbers.firstOrNull { it in 3f..65f && it !in barScaleTicks }
                    if (picked != null && bodyFatPercent == null) {
                        bodyFatPercent = picked
                    }
                }

                // Gordura corporal (Tabela: 1º número = Massa Gorda kg, 2º número = BF%)
                normLine.contains("gordura corporal") && !normLine.contains("controle") -> {
                    val valid = effectiveNumbers.filter { it in 2f..95f }
                    if (valid.size >= 2) {
                        if (fatMassKg == null) fatMassKg = valid[0]
                        if (bodyFatPercent == null) bodyFatPercent = valid[1]
                    } else if (valid.size == 1) {
                        val single = valid[0]
                        if (line.contains("%")) {
                            if (bodyFatPercent == null) bodyFatPercent = single
                        } else {
                            if (fatMassKg == null) fatMassKg = single
                        }
                    }
                }

                // Massa gorda(kg) no gráfico de barras
                normLine.contains("massa gorda") -> {
                    val candidates = effectiveNumbers.filter { it in 2f..90f && it !in barScaleTicks }
                    val picked = candidates.lastOrNull() ?: effectiveNumbers.firstOrNull { it in 2f..90f }
                    if (picked != null && fatMassKg == null) {
                        fatMassKg = picked
                    }
                }

                // Água corporal (Tabela: 1º número = kg/L, 2º número = %)
                normLine.contains("agua corporal") || (normLine.startsWith("agua") && !normLine.contains("meta")) -> {
                    val valid = effectiveNumbers.filter { it in 15f..85f }
                    if (valid.size >= 2) {
                        if (waterKg == null) waterKg = valid[0]
                        if (bodyWaterPercent == null && valid[1] in 30f..80f) bodyWaterPercent = valid[1]
                    } else if (valid.size == 1) {
                        val single = valid[0]
                        if (single >= 48f && line.contains("%")) {
                            if (bodyWaterPercent == null) bodyWaterPercent = single
                        } else {
                            if (waterKg == null) waterKg = single
                        }
                    }
                }

                // Músculo esquelético (Tabela: 1º número = MME kg, 2º número = % MME | Gráfico: MME kg)
                normLine.contains("musculo esqueletico") || normLine.contains("mme") -> {
                    val isBarChartLine = normLine.contains("(kg)")
                    if (isBarChartLine) {
                        val candidates = effectiveNumbers.filter { it in 15f..75f && it !in barScaleTicks }
                        val picked = candidates.lastOrNull() ?: effectiveNumbers.firstOrNull { it in 15f..75f }
                        if (picked != null && skeletalMuscleKg == null) {
                            skeletalMuscleKg = picked
                        }
                    } else {
                        val valid = effectiveNumbers.filter { it in 15f..75f }
                        if (valid.size >= 2) {
                            if (skeletalMuscleKg == null) skeletalMuscleKg = valid[0]
                            if (skeletalMusclePercent == null) skeletalMusclePercent = valid[1]
                        } else if (valid.size == 1) {
                            val single = valid[0]
                            if (line.contains("%") && single >= 36f) {
                                if (skeletalMusclePercent == null) skeletalMusclePercent = single
                            } else {
                                if (skeletalMuscleKg == null) skeletalMuscleKg = single
                            }
                        }
                    }
                }

                // Músculo (sem "esquelético" / "gordura muscular" / "equilíbrio muscular" / "controle")
                (normLine.startsWith("musculo") || normLine.contains("massa muscular")) &&
                    !normLine.contains("esqueletico") &&
                    !normLine.contains("gordura") &&
                    !normLine.contains("equilibrio") &&
                    !normLine.contains("controle") -> {
                    val valid = effectiveNumbers.filter { it in 25f..130f }
                    if (valid.size >= 2) {
                        if (muscleMassKg == null) muscleMassKg = valid[0]
                        if (muscleRatePercent == null && valid[1] in 35f..92f) {
                            muscleRatePercent = valid[1]
                        }
                    } else if (valid.size == 1) {
                        if (muscleMassKg == null) muscleMassKg = valid[0]
                    }
                }

                // Taxa muscular direta (Fitdays)
                normLine.contains("taxa muscular") -> {
                    effectiveNumbers.firstOrNull { it in 35f..92f }?.let { mr ->
                        if (muscleRatePercent == null) muscleRatePercent = mr
                    }
                }

                // Proteína (1º número = kg, 2º número = %)
                normLine.contains("proteina") -> {
                    val valid = effectiveNumbers.filter { it in 5f..35f }
                    if (valid.size >= 2) {
                        if (proteinKg == null) proteinKg = valid[0]
                        if (proteinPercent == null) proteinPercent = valid[1]
                    } else if (valid.size == 1) {
                        val single = valid[0]
                        if (line.contains("%") || single >= 18.2f) {
                            if (proteinPercent == null) proteinPercent = single
                        } else {
                            if (proteinKg == null) proteinKg = single
                        }
                    }
                }

                // Sal inorgânico / Massa óssea
                normLine.contains("sal inorganico") ||
                    normLine.contains("massa ossea") ||
                    normLine.contains("minerais") -> {
                    val valid = effectiveNumbers.filter { it in 1.0f..8.0f }
                    if (valid.isNotEmpty() && boneMassKg == null) {
                        boneMassKg = valid[0]
                    }
                }

                // IMC (Gráfico de barras "Análise de obesidade" ou linha IMC - exige decimal para nunca confundir com Idade 49/50)
                normLine.startsWith("imc") || normLine.contains("imc(kg") || normLine.contains("imc (kg") -> {
                    val nextRawLine = rawLines.getOrNull(i + 1).orEmpty()
                    val decimalTokens = Regex("\\b\\d{2}[.,]\\d+\\b")
                        .findAll("$line $nextRawLine")
                        .mapNotNull { it.value.replace(',', '.').toFloatOrNull() }
                        .filter { it in 12f..55f && it !in barScaleTicks }
                        .toList()
                    val isBarChartImc = normLine.contains("(kg")
                    val pickedDecimal = if (isBarChartImc) decimalTokens.lastOrNull() else decimalTokens.firstOrNull()
                    val fallbackCandidates = effectiveNumbers.filter {
                        it in 12f..45f && it !in barScaleTicks && abs(it - it.roundToInt()) > 0.01f
                    }
                    val picked = pickedDecimal ?: fallbackCandidates.lastOrNull()
                    if (picked != null && bmi == null) {
                        bmi = picked
                    }
                }

                // Peso (Tabela "Análise da composição corporal" ou Gráfico "Peso(kg)")
                (normLine.startsWith("peso") || normLine.contains("peso(kg)") || normLine.contains("peso corporal")) &&
                    !normLine.contains("livre") &&
                    !normLine.contains("sem gordura") &&
                    !normLine.contains("controle") &&
                    !normLine.contains("alvo") &&
                    !normLine.contains("padrao") &&
                    !normLine.contains("ideal") -> {
                    val isBarChartLine = normLine.contains("(kg)")
                    val valid = if (isBarChartLine) {
                        effectiveNumbers.filter { it in 35f..250f && it !in barScaleTicks }
                    } else {
                        effectiveNumbers.filter { it in 35f..250f && abs(it - 100.0f) > 0.05f }
                    }
                    val picked = if (isBarChartLine) valid.lastOrNull() else valid.firstOrNull()
                    if (picked != null && weightKg == null) {
                        weightKg = picked
                    }
                }

                // Análise Segmentar (Tronco, Braços, Pernas) — filtra régua de IMC (10.0, 15.0, 18.5, 22.0, 25.0, 30.0)
                normLine.contains("tronco") || normLine.contains("braco") || normLine.contains("perna") -> {
                    val isTrunk = normLine.contains("tronco")
                    val label = when {
                        isTrunk -> "Tronco"
                        normLine.contains("perna") -> "Pernas"
                        else -> "Braços"
                    }
                    if (segmentalParts.none { it.startsWith(label) }) {
                        val combinedWindow = buildString {
                            append(line)
                            rawLines.getOrNull(i + 1)?.let { next1 ->
                                val n1 = normalizeForMatch(next1)
                                if (!n1.contains("braco") && !n1.contains("perna") && !n1.contains("tronco") && !n1.contains("imc")) {
                                    append(" ").append(next1)
                                }
                            }
                        }
                        // Prioriza valores seguidos de "kg" (ex: 8.7kg, 9.5kg) e exclui marcas da régua de IMC (10.0, 15.0, etc.)
                        val explicitKgNums = Regex("(\\d{1,2}[.,]\\d)\\s*kg", RegexOption.IGNORE_CASE)
                            .findAll(combinedWindow)
                            .mapNotNull { it.groupValues.getOrNull(1)?.replace(',', '.')?.toFloatOrNull() }
                            .filter { num ->
                                num !in barScaleTicks &&
                                    abs(num - 10.0f) > 0.05f &&
                                    abs(num - 15.0f) > 0.05f &&
                                    if (isTrunk) num in 5.0f..17.5f else num in 0.4f..14.0f
                            }
                            .distinct()
                            .toList()

                        val fallbackSegNums = extractNumbersOutsideReferenceRanges(combinedWindow)
                            .filter { num ->
                                num !in barScaleTicks &&
                                    abs(num - 10.0f) > 0.05f &&
                                    abs(num - 15.0f) > 0.05f &&
                                    abs(num - 100.0f) > 0.05f &&
                                    if (isTrunk) num in 5.0f..17.5f else num in 0.4f..14.0f
                            }
                            .distinct()

                        val segNums = if (explicitKgNums.size >= 2) explicitKgNums else fallbackSegNums
                        if (segNums.isNotEmpty()) {
                            val formattedNums = segNums.take(2).joinToString(" / ") {
                                String.format(ptBr, "%.1f kg", it)
                            }
                            segmentalParts.add("$label: $formattedNums")
                        }
                    }
                }
            }
        }

        // Resolução de Conversões Automáticas kg -> % e Fallbacks Matemáticos Exatos (apenas com dados lidos)
        val finalWeightKg = weightKg ?: 0f

        if (bodyFatPercent == null && fatMassKg != null && finalWeightKg > 30f) {
            bodyFatPercent = convertKgMetricToPercent(fatMassKg!!, finalWeightKg)
        }
        if (fatMassKg == null && bodyFatPercent != null && finalWeightKg > 30f) {
            fatMassKg = convertPercentMetricToKg(bodyFatPercent!!, finalWeightKg)
        }
        if (bodyWaterPercent == null && waterKg != null && finalWeightKg > 30f) {
            bodyWaterPercent = convertKgMetricToPercent(waterKg!!, finalWeightKg)
        }
        if (muscleRatePercent == null && muscleMassKg != null && finalWeightKg > 30f) {
            muscleRatePercent = convertKgMetricToPercent(muscleMassKg!!, finalWeightKg)
        }
        if (muscleMassKg == null && muscleRatePercent != null && finalWeightKg > 30f) {
            muscleMassKg = convertPercentMetricToKg(muscleRatePercent!!, finalWeightKg)
        }
        if (skeletalMusclePercent == null && skeletalMuscleKg != null && finalWeightKg > 30f) {
            skeletalMusclePercent = convertKgMetricToPercent(skeletalMuscleKg!!, finalWeightKg)
        }
        if (proteinPercent == null && proteinKg != null && finalWeightKg > 30f) {
            proteinPercent = convertKgMetricToPercent(proteinKg!!, finalWeightKg)
        }
        val validHeightCm = heightCm?.takeIf { it in 100f..230f }
        if (finalWeightKg > 30f && validHeightCm != null) {
            val hm = validHeightCm / 100f
            val calculatedBmi = (((finalWeightKg / (hm * hm)) * 10f).roundToInt() / 10f)
            // Trava de precisão do IMC: se o OCR não leu ou divergiu em > 1.2 do cálculo real (ex: leu Idade 49/50), crava o IMC matemático exato
            if (bmi == null || abs(bmi!! - calculatedBmi) > 1.2f) {
                bmi = calculatedBmi
            }
        }
        if (fatFreeMassKg == null && weightKg != null && (fatMassKg != null || bodyFatPercent != null)) {
            val fatKg = fatMassKg ?: (finalWeightKg * (bodyFatPercent!! / 100f))
            fatFreeMassKg = (((finalWeightKg - fatKg) * 10f).roundToInt() / 10f)
        }

        // Contabiliza quantos dos 14 indicadores foram preenchidos automaticamente
        val filledCount = listOfNotNull(
            weightKg,
            bmi,
            bodyFatPercent,
            muscleRatePercent,
            fatFreeMassKg,
            subcutaneousFatPercent,
            visceralFatIndex,
            bodyWaterPercent,
            skeletalMusclePercent,
            muscleMassKg,
            boneMassKg,
            proteinPercent,
            bmrKcal,
            metabolicAge
        ).size

        val normAll = normalizeForMatch(reconstructedText)
        val isRelaxmedicSheet = normAll.contains("analise da composicao corporal") ||
            normAll.contains("outros indicadores") ||
            normAll.contains("sal inorganico") ||
            normAll.contains("pontuacao corporal")

        val resolvedDateLabel = detectedDateLabel
            ?: SimpleDateFormat("dd/MM/yyyy - HH:mm", ptBr).format(Date(nowMillis))
        val resolvedTimestamp = parseDateTimeMillis(resolvedDateLabel, nowMillis)

        val finalSegmentalSummary = buildString {
            if (bodyScore != null) {
                append("Pontuação: ${bodyScore}/100 pts")
            }
            if (segmentalParts.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Segmentar: ")
                append(segmentalParts.joinToString(" • "))
            }
        }

        val resolvedSourceApp = when {
            filledCount == 0 -> "Sem registro"
            isRelaxmedicSheet -> "Relaxmedic / Fitdays"
            else -> "Fitdays / Relaxmedic"
        }

        // Campos de medição não lidos permanecem vazios (0 / exibidos como "--"); fallbackBase fornece apenas metadados.
        val record = BioimpedanceRecord(
            id = "recomp_${resolvedTimestamp}",
            timestampMillis = resolvedTimestamp,
            dateTimeLabel = resolvedDateLabel,
            athleteName = fallbackBase.athleteName,
            sourceApp = resolvedSourceApp,
            weightKg = round1(finalWeightKg),
            bmi = bmi?.let { round1(it) } ?: 0f,
            bodyFatPercent = bodyFatPercent?.let { round1(it) } ?: 0f,
            muscleRatePercent = muscleRatePercent?.let { round1(it) } ?: 0f,
            fatFreeMassKg = fatFreeMassKg?.let { round1(it) } ?: 0f,
            subcutaneousFatPercent = subcutaneousFatPercent?.let { round1(it) } ?: 0f,
            visceralFatIndex = visceralFatIndex?.let { round1(it) } ?: 0f,
            bodyWaterPercent = bodyWaterPercent?.let { round1(it) } ?: 0f,
            skeletalMusclePercent = skeletalMusclePercent?.let { round1(it) } ?: 0f,
            muscleMassKg = muscleMassKg?.let { round1(it) } ?: 0f,
            boneMassKg = boneMassKg?.let { round1(it) } ?: 0f,
            proteinPercent = proteinPercent?.let { round1(it) } ?: 0f,
            bmrKcal = bmrKcal ?: 0,
            metabolicAge = metabolicAge ?: 0,
            photoUri = photoUri ?: fallbackBase.photoUri,
            segmentalSummary = finalSegmentalSummary,
            explicitFatMassKg = fatMassKg?.let { round1(it) }
        )

        val headline = when {
            filledCount == 0 -> OCR_FAILURE_HEADLINE
            filledCount >= TOTAL_EXPECTED_INDICATORS ->
                "✅ Relatório lido com sucesso! $filledCount de $TOTAL_EXPECTED_INDICATORS indicadores preenchidos automaticamente."
            else ->
                "$filledCount de $TOTAL_EXPECTED_INDICATORS indicadores lidos. Preencha os demais."
        }

        val details = if (filledCount == 0) {
            "Nenhum indicador reconhecido automaticamente."
        } else {
            val parts = mutableListOf<String>()
            if (record.weightKg > 0f) {
                parts.add(String.format(ptBr, "Peso: %.1f kg", record.weightKg))
            }
            if (record.bodyFatPercent > 0f) {
                if (record.fatMassKg > 0f) {
                    parts.add(String.format(ptBr, "Gordura: %.1f%% (%.1f kg)", record.bodyFatPercent, record.fatMassKg))
                } else {
                    parts.add(String.format(ptBr, "Gordura: %.1f%%", record.bodyFatPercent))
                }
            }
            if (record.skeletalMusclePercent > 0f) {
                parts.add(String.format(ptBr, "Músc. Esq.: %.1f%%", record.skeletalMusclePercent))
            }
            if (record.visceralFatIndex > 0f) {
                parts.add(String.format(ptBr, "Visceral: %.0f", record.visceralFatIndex))
            }
            if (record.bmrKcal > 0) {
                parts.add(String.format(ptBr, "TMB: %d kcal", record.bmrKcal))
            }
            if (bodyScore != null) {
                parts.add("Score: ${bodyScore}/100")
            }
            parts.joinToString(" • ")
        }

        return ParsedBioimpedanceResult(
            record = record,
            detectedTemplate = if (filledCount == 0) {
                BioimpedanceDetectedTemplate.NAO_DETECTADO
            } else {
                BioimpedanceDetectedTemplate.MANUAL_ENTRY
            },
            statusHeadline = headline,
            conversionDetails = details,
            wasConvertedFromKgToPercent = waterKg != null || skeletalMuscleKg != null || proteinKg != null,
            filledIndicatorsCount = filledCount,
            renderedBitmap = renderedBitmap,
            reconstructedOcrText = reconstructedText
        )
    }

    /**
     * Extrai com máxima tolerância a data e hora real impressa no relatório Relaxmedic / Fitdays
     * (ex: `"Horário da medição: 03/11/2026 06:05"` -> `"03/11/2026 - 06:05"`), mesmo quando o OCR
     * separa a data e a hora em colunas/linhas adjacentes ou usa `.` / `-` como separador.
     */
    private fun extractExamDateTimeFromReport(
        reconstructedText: String,
        rawLines: List<String>
    ): String? {
        val fullDateTimeRegex = Regex(
            "([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2})\\s*(?:[-–•]|as|às)?\\s*([0-2]?\\d)\\s*[:.h]\\s*([0-5]\\d)",
            RegexOption.IGNORE_CASE
        )
        val timeFirstRegex = Regex(
            "([0-2]?\\d)\\s*[:.h]\\s*([0-5]\\d)\\s*(?:[-–•])?\\s*([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2})",
            RegexOption.IGNORE_CASE
        )
        val isoDateTimeRegex = Regex(
            "(20\\d{2})\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*([0-3]?\\d)\\s*(?:[-–•])?\\s*([0-2]?\\d)\\s*[:.h]\\s*([0-5]\\d)"
        )

        fun formatDdMmYyyyHhMm(d: String, m: String, y: String, hh: String, mm: String): String {
            val day = d.trim().padStart(2, '0')
            val month = m.trim().padStart(2, '0')
            val hour = hh.trim().padStart(2, '0')
            val min = mm.trim().padStart(2, '0')
            return "$day/$month/$y - $hour:$min"
        }

        // 1. Procura primeiro nas linhas com "Horário", "medição" ou "Data"
        for (i in rawLines.indices) {
            val window = listOfNotNull(rawLines.getOrNull(i), rawLines.getOrNull(i + 1)).joinToString(" ")
            val normW = normalizeForMatch(window)
            if (normW.contains("horario") || normW.contains("medicao") || normW.contains("data")) {
                fullDateTimeRegex.find(window)?.let { m ->
                    return formatDdMmYyyyHhMm(
                        m.groupValues[1], m.groupValues[2], m.groupValues[3],
                        m.groupValues[4], m.groupValues[5]
                    )
                }
                timeFirstRegex.find(window)?.let { m ->
                    return formatDdMmYyyyHhMm(
                        m.groupValues[3], m.groupValues[4], m.groupValues[5],
                        m.groupValues[1], m.groupValues[2]
                    )
                }
                isoDateTimeRegex.find(window)?.let { m ->
                    return formatDdMmYyyyHhMm(
                        m.groupValues[3], m.groupValues[2], m.groupValues[1],
                        m.groupValues[4], m.groupValues[5]
                    )
                }
            }
        }

        // 2. Procura em todo o texto reconstruído (incluindo LINHA_COMPLETA)
        fullDateTimeRegex.find(reconstructedText)?.let { m ->
            return formatDdMmYyyyHhMm(
                m.groupValues[1], m.groupValues[2], m.groupValues[3],
                m.groupValues[4], m.groupValues[5]
            )
        }
        timeFirstRegex.find(reconstructedText)?.let { m ->
            return formatDdMmYyyyHhMm(
                m.groupValues[3], m.groupValues[4], m.groupValues[5],
                m.groupValues[1], m.groupValues[2]
            )
        }
        isoDateTimeRegex.find(reconstructedText)?.let { m ->
            return formatDdMmYyyyHhMm(
                m.groupValues[3], m.groupValues[2], m.groupValues[1],
                m.groupValues[4], m.groupValues[5]
            )
        }

        // 3. Caso a data e a hora tenham ficado separadas no cabeçalho: combina a 1ª data dd/MM/yyyy com o 1º horário HH:mm
        val dateOnlyRegex = Regex("([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2})")
        val timeOnlyRegex = Regex("\\b([0-2]\\d):([0-5]\\d)\\b")
        val dateMatch = dateOnlyRegex.find(reconstructedText)
        if (dateMatch != null) {
            val timeMatch = timeOnlyRegex.find(reconstructedText)
            val hh = timeMatch?.groupValues?.getOrNull(1) ?: "06"
            val mm = timeMatch?.groupValues?.getOrNull(2) ?: "05"
            return formatDdMmYyyyHhMm(
                dateMatch.groupValues[1],
                dateMatch.groupValues[2],
                dateMatch.groupValues[3],
                hh,
                mm
            )
        }

        return null
    }

    /**
     * Remove parênteses de faixa de referência como `(65.4-88.5)` ou `(9.2-18.4)` (inclusive se o OCR
     * tiver omitido um dos parênteses na faixa `min-max`) antes de extrair os números da linha.
     */
    fun extractNumbersOutsideReferenceRanges(line: String): List<Float> {
        val withoutParens = line
            .replace(Regex("\\([^)]*\\)"), " ")
            // Remove também faixas numéricas com hífen/til ex: 65.4-88.5 mesmo se faltar '(' ou ')' no OCR
            .replace(Regex("\\(?\\d{1,3}[.,]\\d{1,2}\\s*[-~]\\s*\\d{1,3}[.,]\\d{1,2}\\)?"), " ")
            // Remove datas e horas para não confundir dia/mês/ano com indicadores fisiológicos
            .replace(Regex("[0-3]?\\d\\s*[/.-]\\s*[0-1]?\\d\\s*[/.-]\\s*20\\d{2}"), " ")
            .replace(Regex("[0-2]?\\d:[0-5]\\d"), " ")
            // Remove "/100" de pontuação corporal para manter apenas o score
            .replace(Regex("/\\s*100\\b"), " ")

        val numRegex = Regex("(?<![A-Za-z])(\\d{1,4}(?:[.,]\\d{1,2})?)")
        return numRegex.findAll(withoutParens)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()
            }
            .toList()
    }

    private fun normalizeForMatch(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(ptBr), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun normalizeDateTimeLabel(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        val dateFirst = Regex("([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2})\\s*-?\\s*([0-2]?\\d:[0-5]\\d)").find(clean)
        if (dateFirst != null) {
            val d = dateFirst.groupValues[1].padStart(2, '0')
            val m = dateFirst.groupValues[2].padStart(2, '0')
            val y = dateFirst.groupValues[3]
            val t = dateFirst.groupValues[4].padStart(5, '0')
            return "$d/$m/$y - $t"
        }
        val timeFirst = Regex("([0-2]?\\d:[0-5]\\d)\\s+([0-3]?\\d)\\s*[/.-]\\s*([0-1]?\\d)\\s*[/.-]\\s*(20\\d{2})").find(clean)
        if (timeFirst != null) {
            val t = timeFirst.groupValues[1].padStart(5, '0')
            val d = timeFirst.groupValues[2].padStart(2, '0')
            val m = timeFirst.groupValues[3].padStart(2, '0')
            val y = timeFirst.groupValues[4]
            return "$d/$m/$y - $t"
        }
        return clean
    }

    private fun round1(value: Float): Float = ((value * 10f).roundToInt() / 10f)

    /**
     * Constrói o resultado padrão de falha de OCR: registro em branco do perfil ativo
     * (sem peso nem métricas preenchidas), mantendo nome e foto do perfil/anexo, sem mensagem de sucesso.
     */
    fun buildOcrFailureResult(
        profile: UserProfileData? = WeightCalculator.activeUserProfile,
        photoUri: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedBioimpedanceResult {
        val blankRecord = BodyRecompEngine.createBlankProfileRecord(
            profile = profile,
            nowMillis = nowMillis,
            includeProfileWeight = false,
            photoUri = photoUri
        )
        return ParsedBioimpedanceResult(
            record = blankRecord,
            detectedTemplate = BioimpedanceDetectedTemplate.NAO_DETECTADO,
            statusHeadline = OCR_FAILURE_HEADLINE,
            conversionDetails = "Nenhum valor foi preenchido automaticamente.",
            wasConvertedFromKgToPercent = false,
            filledIndicatorsCount = 0
        )
    }

    /**
     * Lógica pura (sem Context) executada quando o OCR falha ou lê menos de 4 indicadores.
     * Nunca escolhe template automaticamente por nome de arquivo, assinatura visual ou pesos já salvos.
     */
    fun analyzeAttachedImageWithoutOcr(
        fileName: String? = null,
        photoUri: String? = null,
        existingRecords: List<BioimpedanceRecord> = emptyList(),
        profile: UserProfileData? = WeightCalculator.activeUserProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedBioimpedanceResult {
        // Ignora intencionalmente fileName e existingRecords para nunca inventar leitura por template.
        return buildOcrFailureResult(
            profile = profile,
            photoUri = photoUri,
            nowMillis = nowMillis
        )
    }

    /**
     * Chamado quando o OCR falha ou lê menos de 4 indicadores.
     * Retorna sempre um resultado de falha com registro em branco do perfil ativo.
     */
    fun analyzeAttachedImage(
        context: Context,
        uri: Uri,
        bitmap: Bitmap?,
        existingRecords: List<BioimpedanceRecord>,
        fallbackBase: BioimpedanceRecord,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): ParsedBioimpedanceResult {
        return runCatching {
            val fileName = queryDisplayName(context, uri)
            analyzeAttachedImageWithoutOcr(
                fileName = fileName,
                photoUri = uri.toString(),
                existingRecords = existingRecords,
                profile = profile
            )
        }.getOrElse {
            buildOcrFailureResult(
                profile = profile,
                photoUri = uri.toString()
            )
        }
    }

    /**
     * Constrói o resultado para seleção explícita do usuário nos chips de "Importar leitura salva"
     * em `BodyRecompScreen`. Os templates `FITDAYS_29_10` e `RELAXMEDIC_13_10` só são permitidos
     * quando `BioimpedanceReferences.hasPersonalGoals(profile)` é `true`.
     */
    fun buildResultForTemplate(
        template: BioimpedanceDetectedTemplate,
        photoUri: String?,
        fallbackBase: BioimpedanceRecord,
        profile: UserProfileData? = WeightCalculator.activeUserProfile
    ): ParsedBioimpedanceResult {
        val allowSavedTemplates = BioimpedanceReferences.hasPersonalGoals(profile)
        return when (template) {
            BioimpedanceDetectedTemplate.FITDAYS_29_10 -> {
                if (!allowSavedTemplates) {
                    return buildOcrFailureResult(profile = profile, photoUri = photoUri)
                }
                val rec = BodyRecompEngine.createOfficialCheckIn3FitdaysRecord().copy(
                    photoUri = photoUri ?: fallbackBase.photoUri
                )
                ParsedBioimpedanceResult(
                    record = rec,
                    detectedTemplate = BioimpedanceDetectedTemplate.FITDAYS_29_10,
                    statusHeadline = "Leitura salva importada (Fitdays 29/10). Confira antes de salvar.",
                    conversionDetails = "Importação manual da leitura salva Fitdays (84,7 kg • 23,0% BF).",
                    wasConvertedFromKgToPercent = false,
                    filledIndicatorsCount = TOTAL_EXPECTED_INDICATORS
                )
            }
            BioimpedanceDetectedTemplate.RELAXMEDIC_13_10 -> {
                if (!allowSavedTemplates) {
                    return buildOcrFailureResult(profile = profile, photoUri = photoUri)
                }
                val rec = BodyRecompEngine.createOfficialCheckIn2RelaxmedicRecord().copy(
                    photoUri = photoUri ?: fallbackBase.photoUri
                )
                ParsedBioimpedanceResult(
                    record = rec,
                    detectedTemplate = BioimpedanceDetectedTemplate.RELAXMEDIC_13_10,
                    statusHeadline = "Leitura salva importada (Relaxmedic 13/10). Confira antes de salvar.",
                    conversionDetails = "Importação manual da leitura salva Relaxmedic convertida kg → % (Água 52,4% • Proteína 20,3% • Músc. Esq. 40,6%).",
                    wasConvertedFromKgToPercent = true,
                    filledIndicatorsCount = TOTAL_EXPECTED_INDICATORS
                )
            }
            BioimpedanceDetectedTemplate.MANUAL_ENTRY -> {
                val blank = BodyRecompEngine.createBlankProfileRecord(
                    profile = profile,
                    includeProfileWeight = false,
                    photoUri = photoUri ?: fallbackBase.photoUri
                )
                ParsedBioimpedanceResult(
                    record = blank,
                    detectedTemplate = BioimpedanceDetectedTemplate.MANUAL_ENTRY,
                    statusHeadline = "Modo de Edição Manual Ativo",
                    conversionDetails = "Preencha os indicadores abaixo antes de salvar.",
                    wasConvertedFromKgToPercent = false,
                    filledIndicatorsCount = 0
                )
            }
            BioimpedanceDetectedTemplate.NAO_DETECTADO -> {
                buildOcrFailureResult(profile = profile, photoUri = photoUri)
            }
        }
    }

    /**
     * Converte valores em `kg` do relatório Relaxmedic para `%` dividindo pelo peso corporal total (`weightKg`).
     */
    fun convertKgMetricToPercent(metricKg: Float, totalWeightKg: Float): Float {
        if (totalWeightKg <= 10f || metricKg <= 0f) return 0f
        return (((metricKg / totalWeightKg) * 100f) * 10f).roundToInt() / 10f
    }

    /**
     * Converte valores em `%` do relatório Fitdays para `kg` multiplicando pelo peso corporal total (`weightKg`).
     */
    fun convertPercentMetricToKg(metricPercent: Float, totalWeightKg: Float): Float {
        if (totalWeightKg <= 10f || metricPercent <= 0f) return 0f
        return (((metricPercent / 100f) * totalWeightKg) * 10f).roundToInt() / 10f
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) ?: "" else ""
                } else ""
            } ?: (uri.lastPathSegment ?: "")
        }.getOrDefault(uri.lastPathSegment ?: "")
    }

    /**
     * Converte data/hora no formato `"dd/MM/yyyy - HH:mm"` ou `"dd/MM/yyyy HH:mm"` para `timestampMillis`.
     */
    fun parseDateTimeMillis(dateTimeLabel: String, fallbackMillis: Long = System.currentTimeMillis()): Long {
        val clean = normalizeDateTimeLabel(dateTimeLabel)
        val formats = listOf(
            "dd/MM/yyyy - HH:mm",
            "dd/MM/yyyy HH:mm",
            "dd.MM.yyyy - HH:mm",
            "dd.MM.yyyy HH:mm",
            "HH:mm dd/MM/yyyy",
            "dd/MM/yyyy",
            "dd.MM.yyyy"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, ptBr).parse(clean)?.time
            }.getOrNull()
            if (parsed != null && parsed > 0L) return parsed
        }
        return fallbackMillis
    }

    /**
     * Utilitário para gerar timestamp determinístico a partir de dia/mês/ano/hora/minuto.
     */
    fun buildTimestamp(day: Int, monthOneBased: Int, year: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (monthOneBased - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
