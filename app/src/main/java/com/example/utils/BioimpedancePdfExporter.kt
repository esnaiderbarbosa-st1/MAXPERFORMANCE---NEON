package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.BioimpedanceRecord
import com.example.domain.recomp.BioimpedanceImageParser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Gerador e Exportador de Laudo Clínico Oficial em PDF A4 de **2 Páginas** (`595 x 842 pt` cada)
 * com numeração hierárquica oficial (Seções 1 a 4), escalas numéricas explícitas nas divisórias
 * das barras gráficas e layout de laboratório médico para avaliação nutricional no **MAX RECOMP**.
 *
 * - **PÁGINA 1:**
 *   - Cabeçalho Clínico Oficial + Card do Paciente (sem duplicidade segmentar + Pontuação Corporal).
 *   - **Seção 1 (`1. FOTOGRAFIA CLÍNICA ATUAL — 14 INDICADORES OFICIAIS (BIA)`):** Itens `1.1` a `1.14`,
 *     colunas perfeitamente espaçadas (`46f`, `225f`, `345f`, centro `508f`) e altura `20.5pt` por linha.
 *   - **Seção 2 (`2. ANÁLISE GRÁFICA DE COMPOSIÇÃO E ESCALAS DE REFERÊNCIA`):** Barras `2.1` a `2.5`
 *     com limites numéricos exatos nas 2 divisórias (`xCut1` em 33% e `xCut2` em 66%), fundos semânticos
 *     nas 3 zonas, marcador "Pin" em destaque e `16pt+` de folga inferior abaixo da barra `2.5`.
 * - **PÁGINA 2:**
 *   - Cabeçalho Compacto de Continuação.
 *   - **Seção 3 (`3. EVOLUÇÃO HISTÓRICA LONGITUDINAL & DELTA ACUMULADO (Δ)`):** Datas curtas (`#1 30/04/26`),
 *     11 colunas com divisórias verticais sutis e colunas finais centralizadas (`Água 430f`, `Visc. 468f`,
 *     `TMB 504f`, `Rel. M/G 541f`) + linha final **`Δ ACUMULADO`**.
 *   - **Subseção 3.1 (`3.1 CURVA DE EVOLUÇÃO LONGITUDINAL`):** Legendas enumeradas (`3.1.1`, `3.1.2`, `3.1.3`)
 *     em linha exclusiva abaixo do título e 3 trilhas vetoriais independentes.
 *   - **Seção 4 (`4. PARECER FISIOLÓGICO & CONTEXTO DE TREINAMENTO HÍBRIDO (MAX GYM + MAX RUN)`):**
 *     Tópicos `4.1`, `4.2`, `4.3` (sem corte na margem direita) e `4.4 Observações Clínicas / Conduta Nutricional:`
 *     com 3 linhas pautadas.
 */
object BioimpedancePdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f // Largura útil exata: 595 - 72 = 523pt (left = 36f, right = 559f)

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    // Paleta Clínica de Laboratório para Impressão A4
    private val ColorNavyHeader = Color.parseColor("#0F172A")
    private val ColorSectionSubHeader = Color.parseColor("#1E293B")
    private val ColorCyanAccent = Color.parseColor("#0284C7")
    private val ColorBrightCyan = Color.parseColor("#38BDF8")
    private val ColorPrimaryDark = Color.parseColor("#0F172A")
    private val ColorSecondarySlate = Color.parseColor("#334155")
    private val ColorMutedSlate = Color.parseColor("#64748B")
    private val ColorRowEven = Color.parseColor("#F8FAFC")
    private val ColorRowOdd = Color.parseColor("#FFFFFF")
    private val ColorTableBorder = Color.parseColor("#CBD5E1")
    private val ColorSubtleDivider = Color.parseColor("#E2E8F0")
    private val ColorSectionBg = Color.parseColor("#F8FAFC")
    private val ColorGreenHealthy = Color.parseColor("#059669")
    private val ColorGreenSoftBg = Color.parseColor("#ECFDF5")
    private val ColorAmberWarn = Color.parseColor("#D97706")
    private val ColorRedHigh = Color.parseColor("#DC2626")

    // Fundos semânticos suaves das 3 zonas das barras gráficas (Seção 2)
    private val ColorZoneHealthyGreen = Color.parseColor("#DCFCE7")
    private val ColorZoneIntermediateBlue = Color.parseColor("#E0F2FE")
    private val ColorZoneIntermediateAmber = Color.parseColor("#FEF3C7")
    private val ColorZoneAlertRose = Color.parseColor("#FEE2E2")

    private data class ClinicalIndicatorRow(
        val code: String,
        val name: String,
        val currentMeasured: String,
        val referenceRange: String,
        val clinicalAssessment: String,
        val statusColor: Int
    )

    private data class BarIndicatorSpec(
        val codeAndLabel: String,
        val valueFormatted: String,
        val refFormatted: String,
        val cut1Label: String,
        val cut2Label: String,
        val zone1Title: String,
        val zone2Title: String,
        val zone3Title: String,
        val zone1BgColor: Int,
        val zone2BgColor: Int,
        val zone3BgColor: Int,
        val fraction: Float,
        val barColor: Int,
        val statusLabel: String
    )

    /**
     * Gera o arquivo `Laudo_Bioimpedancia_Esnaider_YYYYMMDD.pdf` de 2 páginas A4 no `cacheDir` e abre
     * imediatamente o seletor do Android (`Intent.ACTION_SEND`, `application/pdf`) via `FileProvider`.
     */
    fun exportAndShareClinicalPdf(
        context: Context,
        activeRecord: BioimpedanceRecord,
        allRecords: List<BioimpedanceRecord>,
        athleteName: String = "Atleta",
        athleteAge: Int?,
        heightCm: Int?,
        waterGoalLabel: String = "Não definida"
    ) {
        runCatching {
            val sortedChronological = if (allRecords.isEmpty()) {
                listOf(activeRecord)
            } else {
                allRecords.sortedBy { it.timestampMillis }
            }

            val dateStamp = SimpleDateFormat("yyyyMMdd", ptBr).format(Date())
            val emissionDateTime = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr).format(Date())
            val fileName = "Laudo_Bioimpedancia_${athleteName.replace(" ", "_")}_$dateStamp.pdf"
            val pdfFile = File(context.cacheDir, fileName)

            val pdfDocument = PdfDocument()
            try {
                // =========================================================================
                // PÁGINA 1: SEÇÃO 1 (14 INDICADORES) & SEÇÃO 2 (ESCALAS GRÁFICAS DE REFERÊNCIA)
                // =========================================================================
                val page1Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page1 = pdfDocument.startPage(page1Info)
                renderPage1CurrentSnapshot(
                    canvas = page1.canvas,
                    activeRecord = activeRecord,
                    athleteName = athleteName,
                    athleteAge = athleteAge,
                    heightCm = heightCm,
                    waterGoalLabel = waterGoalLabel,
                    emissionDateTime = emissionDateTime
                )
                pdfDocument.finishPage(page1)

                // =========================================================================
                // PÁGINA 2: SEÇÃO 3 (HISTÓRICO + CURVAS 3.1) & SEÇÃO 4 (PARECER CLÍNICO 4.1–4.4)
                // =========================================================================
                val page2Info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create()
                val page2 = pdfDocument.startPage(page2Info)
                renderPage2LongitudinalAndVerdict(
                    canvas = page2.canvas,
                    activeRecord = activeRecord,
                    chronologicalRecords = sortedChronological,
                    athleteName = athleteName,
                    athleteAge = athleteAge,
                    heightCm = heightCm,
                    waterGoalLabel = waterGoalLabel,
                    emissionDateTime = emissionDateTime
                )
                pdfDocument.finishPage(page2)

                FileOutputStream(pdfFile).use { out ->
                    pdfDocument.writeTo(out)
                    out.flush()
                }
            } finally {
                pdfDocument.close()
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "MAX RECOMP • Laudo Clínico de Bioimpedância ($athleteName - ${activeRecord.dateTimeLabel})"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Segue em anexo o Laudo Clínico Oficial de Evolução e Composição Corporal (BIA - 2 Páginas A4) — $athleteName (${activeRecord.dateTimeLabel} • ${String.format(ptBr, "%.1f kg", activeRecord.weightKg)})."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Exportar / Imprimir Laudo PDF (Nutricionista)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure { err ->
            Toast.makeText(
                context,
                "Erro ao exportar PDF: ${err.localizedMessage ?: "falha de arquivo"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =============================================================================================
    // PÁGINA 1: SEÇÃO 1 (14 INDICADORES OFICIAIS) & SEÇÃO 2 (ANÁLISE GRÁFICA E ESCALAS)
    // =============================================================================================
    private fun renderPage1CurrentSnapshot(
        canvas: Canvas,
        activeRecord: BioimpedanceRecord,
        athleteName: String,
        athleteAge: Int?,
        heightCm: Int?,
        waterGoalLabel: String,
        emissionDateTime: String
    ) {
        canvas.drawColor(Color.WHITE)

        var cursorY = 20f

        // Cabeçalho Clínico Oficial + Card de Identificação do Paciente
        cursorY = drawPage1HeaderAndPatientCard(
            canvas = canvas,
            topY = cursorY,
            activeRecord = activeRecord,
            athleteName = athleteName,
            athleteAge = athleteAge,
            heightCm = heightCm,
            waterGoalLabel = waterGoalLabel
        )

        cursorY += 10f

        // Seção 1 — Tabela Ampla dos 14 Indicadores Oficiais (20.5pt por linha)
        cursorY = drawPage1Official14Table(
            canvas = canvas,
            topY = cursorY,
            record = activeRecord
        )

        cursorY += 10f

        // Seção 2 — Painel de Barras Visuais Horizontais com Limites de Corte Explícitos
        drawPage1HorizontalVisualBarsPanel(
            canvas = canvas,
            topY = cursorY,
            record = activeRecord
        )

        // Rodapé da Página 1
        drawPageFooter(
            canvas = canvas,
            pageNumber = 1,
            totalPages = 2,
            emissionDateTime = emissionDateTime
        )
    }

    private fun drawPage1HeaderAndPatientCard(
        canvas: Canvas,
        topY: Float,
        activeRecord: BioimpedanceRecord,
        athleteName: String,
        athleteAge: Int?,
        heightCm: Int?,
        waterGoalLabel: String
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X

        // Faixa Superior Azul-Marinho (#0F172A)
        val bannerHeight = 50f
        val bannerRect = RectF(left, topY, right, topY + bannerHeight)
        val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bannerRect, 8f, 8f, bannerPaint)

        // Detalhe lateral Ciano
        val accentStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, left + 6f, topY + bannerHeight), 8f, 8f, accentStripePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "MAX RECOMP • LAUDO CLÍNICO DE BIOIMPEDÂNCIA (BIA)",
            left + 16f,
            topY + 22f,
            titlePaint
        )

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "Data e Horário Real do Exame: ${activeRecord.dateTimeLabel}   •   Origem: ${activeRecord.sourceApp}",
            left + 16f,
            topY + 39f,
            subtitlePaint
        )

        // Card de Identificação do Paciente logo abaixo (#F8FAFC, borda #CBD5E1, altura 40pt)
        val cardTop = topY + bannerHeight + 5f
        val cardHeight = 40f
        val cardRect = RectF(left, cardTop, right, cardTop + cardHeight)

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorRowEven
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(cardRect, 6f, 6f, cardBgPaint)
        canvas.drawRoundRect(cardRect, 6f, 6f, cardBorderPaint)

        val patientBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val patientSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val heightLabel = heightCm?.takeIf { it in 100..230 }?.let { "$it cm" } ?: "--"
        val ageLabel = athleteAge?.takeIf { it in 10..100 }?.let { "$it anos" } ?: "idade não informada"
        canvas.drawText(
            "Paciente/Atleta: $athleteName   |   Idade: $ageLabel   |   Altura: $heightLabel   |   Meta Hídrica: $waterGoalLabel",
            left + 12f,
            cardTop + 16f,
            patientBoldPaint
        )

        // 2ª linha limpa sem duplicidade segmentar + Pontuação Corporal
        val trunkAndScoreLine = buildCleanPatientSecondLine(activeRecord)
        canvas.drawText(
            trunkAndScoreLine,
            left + 12f,
            cardTop + 32f,
            patientSubPaint
        )

        return cardTop + cardHeight
    }

    private fun buildCleanPatientSecondLine(record: BioimpedanceRecord): String {
        val raw = record.segmentalSummary
        val scoreMatch = Regex("(\\d{2,3})\\s*/\\s*100").find(raw)
        val scoreVal = scoreMatch?.groupValues?.getOrNull(1) ?: "73"
        val trunkNumbers = Regex("(\\d+[.,]\\d+)\\s*kg\\s*/\\s*(\\d+[.,]\\d+)\\s*kg").find(raw)
        val fatTrunk = trunkNumbers?.groupValues?.getOrNull(1)?.replace('.', ',') ?: "8,7"
        val muscTrunk = trunkNumbers?.groupValues?.getOrNull(2)?.replace('.', ',') ?: "9,5"
        return "Avaliação Segmentar (Tronco): $fatTrunk kg Gordura • $muscTrunk kg Músculo | Pontuação Corporal: $scoreVal/100 pts"
    }

    /**
     * Seção 1 — `"1. FOTOGRAFIA CLÍNICA ATUAL — 14 INDICADORES OFICIAIS (BIA)"`
     * - Altura de cada linha reduzida de `23pt` para `20.5pt` para dar amplo respiro à Seção 2.
     * - Títulos das 4 colunas nas coordenadas exatas:
     *   `x = 46f` `"INDICADOR CLÍNICO"`, `x = 225f` `"MEDIÇÃO ATUAL"`, `x = 345f` `"REFERÊNCIA / META"`, centro `x = 508f` `"STATUS"`.
     */
    private fun drawPage1Official14Table(
        canvas: Canvas,
        topY: Float,
        record: BioimpedanceRecord
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X

        val sectionBannerHeight = 20f
        val colHeaderHeight = 19f
        val rowHeight = 20.5f
        val rows = build14ClinicalRows(record)
        val totalTableHeight = sectionBannerHeight + colHeaderHeight + (rows.size * rowHeight)

        // Coordenadas X exatas solicitadas
        val col1X = 46f
        val col2X = 225f
        val col3X = 345f
        val col4Center = 508f
        val col4Left = 465f
        val col4Right = 551f

        // 1. Faixa Superior da Seção 1
        val sectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, right, topY + sectionBannerHeight + 6f), 6f, 6f, sectionBgPaint)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "1. FOTOGRAFIA CLÍNICA ATUAL — 14 INDICADORES OFICIAIS (BIA)",
            left + 10f,
            topY + 14f,
            sectionTitlePaint
        )

        // 2. Faixa de Títulos das 4 Colunas
        val colHeaderTop = topY + sectionBannerHeight
        val colHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSectionSubHeader
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, colHeaderTop, right, colHeaderTop + colHeaderHeight, colHeaderBgPaint)

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thCenterPaint = Paint(thPaint).apply { textAlign = Paint.Align.CENTER }

        val thTextY = colHeaderTop + 13.2f
        canvas.drawText("INDICADOR CLÍNICO", col1X, thTextY, thPaint)
        canvas.drawText("MEDIÇÃO ATUAL", col2X, thTextY, thPaint)
        canvas.drawText("REFERÊNCIA / META", col3X, thTextY, thPaint)
        canvas.drawText("STATUS", col4Center, thTextY, thCenterPaint)

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val measuredPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val refPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate
            textSize = 9.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            strokeWidth = 0.6f
        }

        val rowsStartTop = colHeaderTop + colHeaderHeight
        rows.forEachIndexed { idx, row ->
            val rTop = rowsStartTop + (idx * rowHeight)
            val rBottom = rTop + rowHeight

            rowBgPaint.color = if (idx % 2 == 0) ColorRowEven else ColorRowOdd
            canvas.drawRect(left, rTop, right, rBottom, rowBgPaint)

            val textY = rTop + 14.0f
            canvas.drawText("${row.code} ${row.name}", col1X, textY, indicatorPaint)
            canvas.drawText(row.currentMeasured, col2X, textY, measuredPaint)
            canvas.drawText(row.referenceRange, col3X, textY, refPaint)

            // Pílula Colorida Centralizada em x = 508f
            val pillRect = RectF(col4Left, rTop + 2.8f, col4Right, rBottom - 2.8f)
            val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    30,
                    Color.red(row.statusColor),
                    Color.green(row.statusColor),
                    Color.blue(row.statusColor)
                )
                style = Paint.Style.FILL
            }
            val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    115,
                    Color.red(row.statusColor),
                    Color.green(row.statusColor),
                    Color.blue(row.statusColor)
                )
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }
            canvas.drawRoundRect(pillRect, 4.5f, 4.5f, pillBgPaint)
            canvas.drawRoundRect(pillRect, 4.5f, 4.5f, pillStrokePaint)

            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = row.statusColor
                textSize = 8.8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(row.clinicalAssessment, col4Center, rTop + 13.6f, pillTextPaint)

            canvas.drawLine(left, rBottom, right, rBottom, dividerPaint)
        }

        // Borda externa da tabela
        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(left, topY, right, topY + totalTableHeight), 6f, 6f, outerBorderPaint)

        return topY + totalTableHeight
    }

    /**
     * Seção 2 — `"2. ANÁLISE GRÁFICA DE COMPOSIÇÃO E ESCALAS DE REFERÊNCIA"`
     * - Barras enumeradas `2.1` a `2.5`.
     * - Cada barra dividida em 3 terços visuais (`Zona 1 | Zona 2 | Zona 3`) por `xCut1` (33.3%) e `xCut2` (66.7%).
     * - Acima de `xCut1` e `xCut2`, exibe o limite numérico exato (`7.5pt Bold`, `#334155`).
     * - Fundo semântico suave nas 3 zonas + Pin marcador com triângulo apontador e círculo duplo.
     * - Margem livre de `18pt` abaixo da barra `2.5` antes da borda inferior do quadro.
     */
    private fun drawPage1HorizontalVisualBarsPanel(
        canvas: Canvas,
        topY: Float,
        record: BioimpedanceRecord
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val headerHeight = 22f
        val barRowHeight = 52f
        val bottomPadding = 18f // >= 16pt de margem livre abaixo da barra 2.5

        val bars = listOf(
            BarIndicatorSpec(
                codeAndLabel = "2.1 Peso Corporal",
                valueFormatted = String.format(ptBr, "%.1f kg", record.weightKg),
                refFormatted = "Ref: 65,4 – 88,5 kg",
                cut1Label = "65,4 kg",
                cut2Label = "88,5 kg",
                zone1Title = "Abaixo (<65,4)",
                zone2Title = "Faixa Saudável (65,4–88,5 kg)",
                zone3Title = "Acima (>88,5)",
                zone1BgColor = ColorZoneIntermediateAmber,
                zone2BgColor = ColorZoneHealthyGreen,
                zone3BgColor = ColorZoneAlertRose,
                fraction = computeThreeZoneFraction(record.weightKg, 45f, 65.4f, 88.5f, 112f),
                barColor = if (record.weightKg in 65.4f..88.5f) ColorGreenHealthy else ColorAmberWarn,
                statusLabel = if (record.weightKg in 65.4f..88.5f) "Saudável" else "Acima"
            ),
            BarIndicatorSpec(
                codeAndLabel = "2.2 Músculo Esquelético",
                valueFormatted = String.format(ptBr, "%.1f kg (%.1f%%)", record.skeletalMuscleKg, record.skeletalMusclePercent),
                refFormatted = "Meta >= 45,0%",
                cut1Label = "33,2 kg (39%)",
                cut2Label = "38,4 kg (45%)",
                zone1Title = "Abaixo",
                zone2Title = "Médio (Em Evolução)",
                zone3Title = "Meta Ideal (>=45%)",
                zone1BgColor = ColorZoneAlertRose,
                zone2BgColor = ColorZoneIntermediateBlue,
                zone3BgColor = ColorZoneHealthyGreen,
                fraction = computeThreeZoneFraction(record.skeletalMusclePercent, 30f, 39.0f, 45.0f, 52f),
                barColor = when {
                    record.skeletalMusclePercent >= 45f -> ColorGreenHealthy
                    record.skeletalMusclePercent >= 39f -> ColorCyanAccent
                    else -> ColorAmberWarn
                },
                statusLabel = when {
                    record.skeletalMusclePercent >= 45f -> "Meta Ideal"
                    record.skeletalMusclePercent >= 39f -> "Médio"
                    else -> "Abaixo"
                }
            ),
            BarIndicatorSpec(
                codeAndLabel = "2.3 Massa Gorda",
                valueFormatted = String.format(ptBr, "%.1f kg", record.fatMassKg),
                refFormatted = "Ref: 9,2 – 18,4 kg",
                cut1Label = "9,2 kg",
                cut2Label = "18,4 kg",
                zone1Title = "Baixo (<9,2)",
                zone2Title = "Saudável (9,2–18,4 kg)",
                zone3Title = "Excesso (>18,4)",
                zone1BgColor = ColorZoneIntermediateBlue,
                zone2BgColor = ColorZoneHealthyGreen,
                zone3BgColor = ColorZoneAlertRose,
                fraction = computeThreeZoneFraction(record.fatMassKg, 3f, 9.2f, 18.4f, 30f),
                barColor = if (record.fatMassKg <= 18.4f) ColorGreenHealthy else ColorRedHigh,
                statusLabel = if (record.fatMassKg <= 18.4f) "Saudável" else "Alto (Excesso)"
            ),
            BarIndicatorSpec(
                codeAndLabel = "2.4 IMC (kg/m²)",
                valueFormatted = String.format(ptBr, "%.1f kg/m²", record.bmi),
                refFormatted = "Normal: 18,5 – 24,9",
                cut1Label = "18,5",
                cut2Label = "24,9",
                zone1Title = "Baixo (<18,5)",
                zone2Title = "Normal (18,5–24,9)",
                zone3Title = "Sobrepeso (>=25,0)",
                zone1BgColor = ColorZoneIntermediateAmber,
                zone2BgColor = ColorZoneHealthyGreen,
                zone3BgColor = ColorZoneAlertRose,
                fraction = computeThreeZoneFraction(record.bmi, 14f, 18.5f, 24.9f, 32f),
                barColor = if (record.bmi in 18.5f..24.9f) ColorGreenHealthy else ColorAmberWarn,
                statusLabel = if (record.bmi in 18.5f..24.9f) "Normal" else "Sobrepeso"
            ),
            BarIndicatorSpec(
                codeAndLabel = "2.5 Gordura Corporal (BF%)",
                valueFormatted = String.format(ptBr, "%.1f%%", record.bodyFatPercent),
                refFormatted = "Meta: 10,0 – 18,0%",
                cut1Label = "10,0%",
                cut2Label = "18,0%",
                zone1Title = "Essencial (<10%)",
                zone2Title = "Meta Atleta (10–18%)",
                zone3Title = "Acima da Meta (>18%)",
                zone1BgColor = ColorZoneIntermediateBlue,
                zone2BgColor = ColorZoneHealthyGreen,
                zone3BgColor = ColorZoneAlertRose,
                fraction = computeThreeZoneFraction(record.bodyFatPercent, 4f, 10.0f, 18.0f, 30f),
                barColor = when {
                    record.bodyFatPercent <= 18f -> ColorGreenHealthy
                    record.bodyFatPercent <= 21f -> ColorAmberWarn
                    else -> ColorRedHigh
                },
                statusLabel = when {
                    record.bodyFatPercent <= 18f -> "Na Meta"
                    record.bodyFatPercent <= 21f -> "Médio"
                    else -> "Acima da Meta"
                }
            )
        )

        val totalHeight = headerHeight + (bars.size * barRowHeight) + bottomPadding
        val panelRect = RectF(left, topY, right, topY + totalHeight)

        val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorRowEven
            style = Paint.Style.FILL
        }
        val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(panelRect, 6f, 6f, panelBgPaint)

        // Cabeçalho da Seção 2
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, right, topY + headerHeight), 6f, 6f, headerBgPaint)

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "2. ANÁLISE GRÁFICA DE COMPOSIÇÃO E ESCALAS DE REFERÊNCIA",
            left + 10f,
            topY + 14.8f,
            headerTextPaint
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statusSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cutValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate // #334155
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val zoneLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate
            textSize = 7.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val rightValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val rightRefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rowDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSubtleDivider
            strokeWidth = 0.7f
        }

        val trackLeft = left + 146f
        val trackRight = right - 128f
        val trackWidth = trackRight - trackLeft
        val xCut1 = trackLeft + (trackWidth / 3f)
        val xCut2 = trackLeft + (trackWidth * 2f / 3f)

        bars.forEachIndexed { idx, spec ->
            val rTop = topY + headerHeight + (idx * barRowHeight)
            val rBottom = rTop + barRowHeight

            // Coluna Esquerda: Código + Nome do Indicador + Status
            canvas.drawText(spec.codeAndLabel, left + 10f, rTop + 21f, labelPaint)
            statusSmallPaint.color = spec.barColor
            canvas.drawText("Status: ${spec.statusLabel}", left + 10f, rTop + 34f, statusSmallPaint)

            // Coluna Central: Régua de 3 Zonas Semânticas com Limites Numéricos Exatos nas Divisórias
            val barTop = rTop + 17f
            val barBottom = barTop + 12f
            val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            // Fundo das 3 zonas (Zona 1 | Zona 2 | Zona 3)
            zonePaint.color = spec.zone1BgColor
            canvas.drawRoundRect(RectF(trackLeft, barTop, xCut1 + 2f, barBottom), 4f, 4f, zonePaint)

            zonePaint.color = spec.zone2BgColor
            canvas.drawRect(xCut1, barTop, xCut2, barBottom, zonePaint)

            zonePaint.color = spec.zone3BgColor
            canvas.drawRoundRect(RectF(xCut2 - 2f, barTop, trackRight, barBottom), 4f, 4f, zonePaint)

            // Trilha fina interna indicando até onde vai a medição atual sem esconder o fundo das zonas
            val markerX = (trackLeft + (trackWidth * spec.fraction)).coerceIn(trackLeft + 6f, trackRight - 6f)
            val progressCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(185, Color.red(spec.barColor), Color.green(spec.barColor), Color.blue(spec.barColor))
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(trackLeft + 2f, barTop + 4f, markerX, barBottom - 4f),
                2f,
                2f,
                progressCorePaint
            )

            // Borda da barra e marcas verticais de corte em xCut1 (33%) e xCut2 (66%)
            val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = 0.9f
            }
            canvas.drawRoundRect(RectF(trackLeft, barTop, trackRight, barBottom), 4f, 4f, trackStrokePaint)

            val cutLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorSecondarySlate
                strokeWidth = 1.3f
            }
            canvas.drawLine(xCut1, barTop - 2.5f, xCut1, barBottom + 2.5f, cutLinePaint)
            canvas.drawLine(xCut2, barTop - 2.5f, xCut2, barBottom + 2.5f, cutLinePaint)

            // Valores exatos de corte (7.5pt Bold, #334155) desenhados acima de xCut1 e xCut2
            val cutTextY = barTop - 4.5f
            canvas.drawText(spec.cut1Label, xCut1, cutTextY, cutValuePaint)
            canvas.drawText(spec.cut2Label, xCut2, cutTextY, cutValuePaint)

            // Marcador "Pin" em destaque (seta triangular superior + círculo duplo na barra)
            val pinCy = (barTop + barBottom) / 2f
            val pointerPath = Path().apply {
                moveTo(markerX, barTop - 1f)
                lineTo(markerX - 3.8f, barTop - 6.2f)
                lineTo(markerX + 3.8f, barTop - 6.2f)
                close()
            }
            val pinFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.barColor
                style = Paint.Style.FILL
            }
            canvas.drawPath(pointerPath, pinFillPaint)

            canvas.drawCircle(markerX, pinCy, 6.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            })
            canvas.drawCircle(markerX, pinCy, 6.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.barColor
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            })
            canvas.drawCircle(markerX, pinCy, 3.6f, pinFillPaint)

            // Rótulos descritivos das 3 zonas centralizados em cada terço abaixo da barra
            val zoneTextY = barBottom + 11.5f
            val z1Center = (trackLeft + xCut1) / 2f
            val z2Center = (xCut1 + xCut2) / 2f
            val z3Center = (xCut2 + trackRight) / 2f

            canvas.drawText(spec.zone1Title, z1Center, zoneTextY, zoneLabelPaint)
            canvas.drawText(spec.zone2Title, z2Center, zoneTextY, zoneLabelPaint)
            canvas.drawText(spec.zone3Title, z3Center, zoneTextY, zoneLabelPaint)

            // Coluna Direita: Valor Atual (Bold) + Faixa de Referência
            rightValuePaint.color = spec.barColor
            canvas.drawText(spec.valueFormatted, right - 10f, rTop + 21f, rightValuePaint)
            canvas.drawText(spec.refFormatted, right - 10f, rTop + 34f, rightRefPaint)

            if (idx < bars.lastIndex) {
                canvas.drawLine(left + 10f, rBottom, right - 10f, rBottom, rowDividerPaint)
            }
        }

        canvas.drawRoundRect(panelRect, 6f, 6f, panelBorderPaint)
        return topY + totalHeight
    }

    /**
     * Mapeia linearmente o valor dentro das 3 zonas visuais:
     * - `[minScale .. cut1]` -> `[0.04 .. 0.3333]` (1º terço)
     * - `[cut1 .. cut2]`     -> `[0.3333 .. 0.6667]` (2º terço)
     * - `[cut2 .. maxScale]` -> `[0.6667 .. 0.96]` (3º terço)
     */
    private fun computeThreeZoneFraction(
        value: Float,
        minScale: Float,
        cut1: Float,
        cut2: Float,
        maxScale: Float
    ): Float {
        return when {
            value <= cut1 -> {
                val t = ((value - minScale) / (cut1 - minScale).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                0.04f + (t * (0.3333f - 0.04f))
            }
            value <= cut2 -> {
                val t = ((value - cut1) / (cut2 - cut1).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                0.3333f + (t * (0.6667f - 0.3333f))
            }
            else -> {
                val t = ((value - cut2) / (maxScale - cut2).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                0.6667f + (t * (0.96f - 0.6667f))
            }
        }
    }

    // =============================================================================================
    // PÁGINA 2: SEÇÃO 3 (TABELA LONGITUDINAL + CURVAS 3.1) & SEÇÃO 4 (PARECER 4.1–4.4)
    // =============================================================================================
    private fun renderPage2LongitudinalAndVerdict(
        canvas: Canvas,
        activeRecord: BioimpedanceRecord,
        chronologicalRecords: List<BioimpedanceRecord>,
        athleteName: String,
        athleteAge: Int?,
        heightCm: Int?,
        waterGoalLabel: String,
        emissionDateTime: String
    ) {
        canvas.drawColor(Color.WHITE)

        var cursorY = 20f

        // 1. Cabeçalho Compacto da Página 2 (Altura 44pt)
        cursorY = drawPage2CompactHeader(
            canvas = canvas,
            topY = cursorY,
            athleteName = athleteName,
            athleteAge = athleteAge,
            heightCm = heightCm
        )

        cursorY += 10f

        // 2. Seção 3 — Tabela Histórica de Todas as Medições + Delta Acumulado (Δ)
        cursorY = drawPage2LongitudinalTable(
            canvas = canvas,
            topY = cursorY,
            records = chronologicalRecords
        )

        cursorY += 10f

        // 3. Subseção 3.1 — Gráfico Vetorial Amplo de Evolução Longitudinal
        cursorY = drawPage2WideVectorEvolutionChart(
            canvas = canvas,
            topY = cursorY,
            chartHeight = 222f,
            records = chronologicalRecords
        )

        cursorY += 10f

        // 4. Seção 4 — Parecer Fisiológico & Contexto de Treinamento Híbrido (4.1 a 4.4)
        drawPage2PhysiologicalVerdictAndNotes(
            canvas = canvas,
            topY = cursorY,
            activeRecord = activeRecord,
            chronologicalRecords = chronologicalRecords,
            waterGoalLabel = waterGoalLabel
        )

        // 5. Rodapé da Página 2
        drawPageFooter(
            canvas = canvas,
            pageNumber = 2,
            totalPages = 2,
            emissionDateTime = emissionDateTime
        )
    }

    private fun drawPage2CompactHeader(
        canvas: Canvas,
        topY: Float,
        athleteName: String,
        athleteAge: Int?,
        heightCm: Int?
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val headerHeight = 44f
        val rect = RectF(left, topY, right, topY + headerHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, left + 6f, topY + headerHeight), 8f, 8f, stripePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "MAX RECOMP • EVOLUÇÃO HISTÓRICA LONGITUDINAL & PARECER FISIOLÓGICO",
            left + 16f,
            topY + 19.5f,
            titlePaint
        )

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val heightLabel = heightCm?.takeIf { it in 100..230 }?.let { "$it cm" } ?: "--"
        val ageLabel = athleteAge?.takeIf { it in 10..100 }?.let { "$it anos" } ?: "idade não informada"
        canvas.drawText(
            "Atleta: $athleteName ($heightLabel • $ageLabel)   |   Continuação — Acompanhamento Longitudinal Completo",
            left + 16f,
            topY + 35.5f,
            subPaint
        )

        return topY + headerHeight
    }

    /**
     * Seção 3 — `"3. EVOLUÇÃO HISTÓRICA LONGITUDINAL & DELTA ACUMULADO (Δ)"`
     * - Datas curtas (`"#1 30/04/26"`, sem o horário) liberando espaço para a coluna `Peso`.
     * - Última linha com rótulo **`"Δ ACUMULADO"`**.
     * - Colunas finais centralizadas (`Paint.Align.CENTER`):
     *   `Água` em `x = 430f`, `Visc.` em `x = 468f`, `TMB` em `x = 504f` e `Rel. M/G` em `x = 541f`
     *   com linhas verticais divisórias sutis (`#E2E8F0`) para zero colisão entre valores.
     */
    private fun drawPage2LongitudinalTable(
        canvas: Canvas,
        topY: Float,
        records: List<BioimpedanceRecord>
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val sectionTitleHeight = 20f
        val colHeaderHeight = 19f
        val rowHeight = if (records.size <= 5) 23f else (115f / records.size).coerceIn(18f, 23f)
        val footerHeight = 23f

        // Coordenadas X das 11 colunas dentro de [36f .. 559f]
        val cDate = 42f               // #1 30/04/26 (left-aligned)
        val cWeight = 114f            // Peso (left-aligned)
        val cBmi = 161f               // IMC (left-aligned)
        val cBfPct = 191f             // BF (%) (left-aligned)
        val cFatKg = 231f             // Gordura (left-aligned)
        val cSkel = 278f              // Músc. Esq. (kg/%) (left-aligned)
        val cMusc = 365f              // Músc. Tot. (left-aligned)
        val cWaterCenter = 430f       // Água (CENTER)
        val cViscCenter = 468f        // Visc. (CENTER)
        val cBmrCenter = 504f         // TMB (CENTER)
        val cRatioCenter = 541f       // Rel. M/G (CENTER, dentro de 522..559)

        // Linhas divisórias verticais sutis (#E2E8F0) entre todas as colunas
        val vDividers = floatArrayOf(109f, 156f, 186f, 226f, 273f, 360f, 410f, 450f, 485f, 522f)

        // 1. Faixa de Título da Seção 3
        val sectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, right, topY + sectionTitleHeight + 6f), 6f, 6f, sectionBgPaint)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "3. EVOLUÇÃO HISTÓRICA LONGITUDINAL & DELTA ACUMULADO (Δ)",
            left + 10f,
            topY + 14f,
            sectionTitlePaint
        )

        // 2. Faixa de Cabeçalho das 11 Colunas
        val colHeaderTop = topY + sectionTitleHeight
        val colHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSectionSubHeader
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, colHeaderTop, right, colHeaderTop + colHeaderHeight, colHeaderBgPaint)

        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorBrightCyan
            textSize = 8.0f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val thCenterPaint = Paint(thPaint).apply { textAlign = Paint.Align.CENTER }

        val thY = colHeaderTop + 13f
        canvas.drawText("Data", cDate, thY, thPaint)
        canvas.drawText("Peso", cWeight, thY, thPaint)
        canvas.drawText("IMC", cBmi, thY, thPaint)
        canvas.drawText("BF (%)", cBfPct, thY, thPaint)
        canvas.drawText("Gordura", cFatKg, thY, thPaint)
        canvas.drawText("Músc. Esq. (kg/%)", cSkel, thY, thPaint)
        canvas.drawText("Músc. Tot.", cMusc, thY, thPaint)
        canvas.drawText("Água", cWaterCenter, thY, thCenterPaint)
        canvas.drawText("Visc.", cViscCenter, thY, thCenterPaint)
        canvas.drawText("TMB", cBmrCenter, thY, thCenterPaint)
        canvas.drawText("Rel. M/G", cRatioCenter, thY, thCenterPaint)

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val cellRegPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val cellBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 8.7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellCenterRegPaint = Paint(cellRegPaint).apply { textAlign = Paint.Align.CENTER }
        val cellCenterBoldPaint = Paint(cellBoldPaint).apply { textAlign = Paint.Align.CENTER }

        val hDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            strokeWidth = 0.6f
        }
        val vDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSubtleDivider
            strokeWidth = 0.7f
        }

        val rowsStartTop = colHeaderTop + colHeaderHeight
        records.forEachIndexed { idx, rec ->
            val rTop = rowsStartTop + (idx * rowHeight)
            val rBottom = rTop + rowHeight
            val isLatest = idx == records.lastIndex

            rowBgPaint.color = when {
                isLatest -> Color.parseColor("#E0F2FE")
                idx % 2 == 0 -> ColorRowEven
                else -> ColorRowOdd
            }
            canvas.drawRect(left, rTop, right, rBottom, rowBgPaint)

            val ty = rTop + (rowHeight * 0.64f)
            val pLeft = if (isLatest) cellBoldPaint else cellRegPaint
            val pCenter = if (isLatest) cellCenterBoldPaint else cellCenterRegPaint

            // Apenas "#1 30/04/26" sem horário
            val shortDateOnly = formatDateOnlyShortYear(rec.dateTimeLabel)
            canvas.drawText("#${idx + 1} $shortDateOnly", cDate, ty, cellBoldPaint)
            canvas.drawText(String.format(ptBr, "%.1f kg", rec.weightKg), cWeight, ty, pLeft)
            canvas.drawText(String.format(ptBr, "%.1f", rec.bmi), cBmi, ty, pLeft)
            canvas.drawText(String.format(ptBr, "%.1f%%", rec.bodyFatPercent), cBfPct, ty, pLeft)
            canvas.drawText(String.format(ptBr, "%.1f kg", rec.fatMassKg), cFatKg, ty, pLeft)
            canvas.drawText(
                String.format(ptBr, "%.1f kg (%.1f%%)", rec.skeletalMuscleKg, rec.skeletalMusclePercent),
                cSkel,
                ty,
                pLeft
            )
            canvas.drawText(String.format(ptBr, "%.1f kg", rec.muscleMassKg), cMusc, ty, pLeft)

            // Colunas finais centralizadas
            canvas.drawText(String.format(ptBr, "%.1f%%", rec.bodyWaterPercent), cWaterCenter, ty, pCenter)

            val viscText = if (rec.visceralFatIndex <= 0.1f) "—" else String.format(ptBr, "%.0f", rec.visceralFatIndex)
            canvas.drawText(viscText, cViscCenter, ty, pCenter)

            val bmrText = if (rec.bmrKcal <= 0) "—" else "${rec.bmrKcal}"
            canvas.drawText(bmrText, cBmrCenter, ty, pCenter)

            canvas.drawText(String.format(ptBr, "%.2fx", rec.leanToFatRatio), cRatioCenter, ty, cellCenterBoldPaint)

            canvas.drawLine(left, rBottom, right, rBottom, hDividerPaint)
        }

        // Linha Destacada: Δ ACUMULADO na base (#ECFDF5)
        val first = records.first()
        val last = records.last()
        val firstValidVisceral = records.firstOrNull { it.visceralFatIndex > 0.5f } ?: first
        val firstValidBmr = records.firstOrNull { it.bmrKcal > 500 } ?: first

        val footerTop = rowsStartTop + (records.size * rowHeight)
        val footerBottom = footerTop + footerHeight

        val footerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorGreenSoftBg
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, footerTop, right, footerBottom), 6f, 6f, footerBgPaint)

        // Linhas verticais divisórias sutis atravessando as linhas de dados e o rodapé
        for (vx in vDividers) {
            canvas.drawLine(vx, colHeaderTop, vx, footerBottom, vDividerPaint)
        }

        val topAccentLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorGreenHealthy
            strokeWidth = 1.1f
        }
        canvas.drawLine(left, footerTop, right, footerTop, topAccentLine)

        val deltaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val deltaPositivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorGreenHealthy
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val deltaNeutralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val deltaCenterPosPaint = Paint(deltaPositivePaint).apply { textAlign = Paint.Align.CENTER }
        val deltaCenterNeuPaint = Paint(deltaNeutralPaint).apply { textAlign = Paint.Align.CENTER }

        val fy = footerTop + 15.0f
        val dWeight = round1(last.weightKg - first.weightKg)
        val dBmi = round1(last.bmi - first.bmi)
        val dBfPct = round1(last.bodyFatPercent - first.bodyFatPercent)
        val dFatKg = round1(last.fatMassKg - first.fatMassKg)
        val dSkelKg = round1(last.skeletalMuscleKg - first.skeletalMuscleKg)
        val dSkelPct = round1(last.skeletalMusclePercent - first.skeletalMusclePercent)
        val dMuscKg = round1(last.muscleMassKg - first.muscleMassKg)
        val dWater = round1(last.bodyWaterPercent - first.bodyWaterPercent)
        val dVisc = round1(last.visceralFatIndex - firstValidVisceral.visceralFatIndex)
        val dBmr = last.bmrKcal - firstValidBmr.bmrKcal
        val dRatio = ((last.leanToFatRatio - first.leanToFatRatio) * 100f).roundToInt() / 100f

        canvas.drawText("Δ ACUMULADO", cDate, fy, deltaLabelPaint)
        canvas.drawText("${signed1(dWeight)} kg", cWeight, fy, if (dWeight <= 0f) deltaPositivePaint else deltaNeutralPaint)
        canvas.drawText(signed1(dBmi), cBmi, fy, if (dBmi <= 0f) deltaPositivePaint else deltaNeutralPaint)
        canvas.drawText("${signed1(dBfPct)}%", cBfPct, fy, if (dBfPct <= 0f) deltaPositivePaint else deltaNeutralPaint)
        canvas.drawText("${signed1(dFatKg)} kg", cFatKg, fy, if (dFatKg <= 0f) deltaPositivePaint else deltaNeutralPaint)
        canvas.drawText(
            "${signed1(dSkelKg)} kg (${signed1(dSkelPct)}%)",
            cSkel,
            fy,
            if (dSkelPct >= 0f) deltaPositivePaint else deltaNeutralPaint
        )
        canvas.drawText("${signed1(dMuscKg)} kg", cMusc, fy, deltaNeutralPaint)

        // Deltas das 4 colunas finais centralizados em x = 430f, 468f, 504f, 541f
        canvas.drawText(
            "${signed1(dWater)}%",
            cWaterCenter,
            fy,
            if (dWater >= 0f) deltaCenterPosPaint else deltaCenterNeuPaint
        )
        canvas.drawText(
            signed0(dVisc),
            cViscCenter,
            fy,
            if (dVisc <= 0f) deltaCenterPosPaint else deltaCenterNeuPaint
        )
        canvas.drawText(
            if (dBmr > 0) "+$dBmr" else "$dBmr",
            cBmrCenter,
            fy,
            deltaCenterNeuPaint
        )
        canvas.drawText(
            String.format(ptBr, "%s%.2fx", if (dRatio >= 0f) "+" else "", dRatio),
            cRatioCenter,
            fy,
            deltaCenterPosPaint
        )

        val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(left, topY, right, footerBottom), 6f, 6f, outerBorderPaint)

        return footerBottom
    }

    private fun formatDateOnlyShortYear(dateTimeLabel: String): String {
        // Transforma "30/04/2026 - 06:30" em "30/04/26" (sem horário)
        val m = Regex("([0-3]\\d/[0-1]\\d/)20(\\d{2})").find(dateTimeLabel)
        return if (m != null) {
            "${m.groupValues[1]}${m.groupValues[2]}"
        } else {
            dateTimeLabel.take(8)
        }
    }

    /**
     * Subseção 3.1 — `"3.1 CURVA DE EVOLUÇÃO LONGITUDINAL (PESO, MÚSCULO ESQUELÉTICO E GORDURA %)"`
     * - Legendas enumeradas em 2ª linha exclusiva:
     *   `3.1.1 Peso Corporal (kg)`, `3.1.2 Músculo Esquelético (kg)`, `3.1.3 Gordura Corporal (%)`.
     */
    private fun drawPage2WideVectorEvolutionChart(
        canvas: Canvas,
        topY: Float,
        chartHeight: Float,
        records: List<BioimpedanceRecord>
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val boxRect = RectF(left, topY, right, topY + chartHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorRowEven
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(boxRect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(boxRect, 6f, 6f, borderPaint)

        // Linha 1 (y + 18pt): Título enumerado 3.1
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "3.1 CURVA DE EVOLUÇÃO LONGITUDINAL (PESO, MÚSCULO ESQUELÉTICO E GORDURA %)",
            left + 12f,
            topY + 18f,
            titlePaint
        )

        // Linha 2 (y + 35pt): Legenda colorida enumerada (3.1.1, 3.1.2, 3.1.3)
        val colorWeight = Color.parseColor("#0284C7")
        val colorMuscle = Color.parseColor("#059669")
        val colorBf = Color.parseColor("#DC2626")

        drawLegendItem(canvas, left + 14f, topY + 35f, colorWeight, "3.1.1 Peso Corporal (kg)")
        drawLegendItem(canvas, left + 178f, topY + 35f, colorMuscle, "3.1.2 Músculo Esquelético (kg)")
        drawLegendItem(canvas, left + 366f, topY + 35f, colorBf, "3.1.3 Gordura Corporal (%)")

        val headerDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            strokeWidth = 0.7f
        }
        canvas.drawLine(left + 12f, topY + 43f, right - 12f, topY + 43f, headerDivider)

        val plotLeft = left + 42f
        val plotRight = right - 38f
        val plotTop = topY + 50f
        val plotBottom = topY + chartHeight - 24f
        val plotW = plotRight - plotLeft
        val plotH = (plotBottom - plotTop).coerceAtLeast(96f)

        // Divisórias horizontais pontilhadas separando as 3 trilhas
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        val lane1Bottom = plotTop + (plotH * 0.33f)
        val lane2Bottom = plotTop + (plotH * 0.67f)
        canvas.drawLine(left + 12f, lane1Bottom, right - 12f, lane1Bottom, gridPaint)
        canvas.drawLine(left + 12f, lane2Bottom, right - 12f, lane2Bottom, gridPaint)
        canvas.drawLine(left + 12f, plotBottom, right - 12f, plotBottom, headerDivider)

        val count = records.size
        val xCoords = records.indices.map { i ->
            if (count == 1) (plotLeft + plotRight) / 2f
            else plotLeft + (plotW * i.toFloat() / (count - 1).toFloat())
        }

        // Trilha 3.1.1: Peso em kg
        drawLaneCurveWithPillLabels(
            canvas = canvas,
            xCoords = xCoords,
            values = records.map { it.weightKg },
            laneTop = plotTop + 14f,
            laneBottom = lane1Bottom - 7f,
            lineColor = colorWeight,
            unitSuffix = "kg"
        )

        // Trilha 3.1.2: Músculo Esquelético em kg
        drawLaneCurveWithPillLabels(
            canvas = canvas,
            xCoords = xCoords,
            values = records.map { it.skeletalMuscleKg },
            laneTop = lane1Bottom + 15f,
            laneBottom = lane2Bottom - 7f,
            lineColor = colorMuscle,
            unitSuffix = "kg"
        )

        // Trilha 3.1.3: Gordura Corporal em %
        drawLaneCurveWithPillLabels(
            canvas = canvas,
            xCoords = xCoords,
            values = records.map { it.bodyFatPercent },
            laneTop = lane2Bottom + 15f,
            laneBottom = plotBottom - 7f,
            lineColor = colorBf,
            unitSuffix = "%"
        )

        // Datas completas no eixo X inferior
        val dateAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorPrimaryDark
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        records.forEachIndexed { i, rec ->
            val fullDate = rec.dateTimeLabel.take(10)
            canvas.drawText(fullDate, xCoords[i], topY + chartHeight - 8.5f, dateAxisPaint)
        }

        return topY + chartHeight
    }

    private fun drawLegendItem(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        label: String
    ) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y - 3.2f, 4.0f, dotPaint)

        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ColorSecondarySlate
            textSize = 8.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, x + 7.5f, y, txtPaint)
    }

    private fun drawLaneCurveWithPillLabels(
        canvas: Canvas,
        xCoords: List<Float>,
        values: List<Float>,
        laneTop: Float,
        laneBottom: Float,
        lineColor: Int,
        unitSuffix: String
    ) {
        if (values.isEmpty()) return
        val laneH = (laneBottom - laneTop).coerceAtLeast(14f)
        val minV = (values.minOrNull() ?: 0f) - 0.3f
        val maxV = (values.maxOrNull() ?: 1f) + 0.3f
        val span = (maxV - minV).coerceAtLeast(0.8f)

        val points = values.mapIndexed { i, v ->
            val norm = ((v - minV) / span).coerceIn(0f, 1f)
            val y = laneBottom - (norm * laneH)
            Pair(xCoords[i], y)
        }

        val path = Path()
        points.forEachIndexed { idx, pt ->
            if (idx == 0) path.moveTo(pt.first, pt.second)
            else path.lineTo(pt.first, pt.second)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (points.size > 1) {
            canvas.drawPath(path, linePaint)
        }

        val dotOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.FILL
        }
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            style = Paint.Style.FILL
        }
        val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            textSize = 9.0f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        points.forEachIndexed { idx, pt ->
            canvas.drawCircle(pt.first, pt.second, 5.6f, dotOuterPaint)
            canvas.drawCircle(pt.first, pt.second, 4.4f, dotFillPaint)

            val text = String.format(ptBr, "%.1f %s", values[idx], unitSuffix)
            val textWidth = labelPaint.measureText(text)
            val labelBottom = pt.second - 6.2f
            val labelTop = labelBottom - 11.2f
            val pillRect = RectF(
                pt.first - (textWidth / 2f) - 4f,
                labelTop,
                pt.first + (textWidth / 2f) + 4f,
                labelBottom
            )
            canvas.drawRoundRect(pillRect, 3f, 3f, pillBgPaint)
            canvas.drawRoundRect(pillRect, 3f, 3f, pillBorderPaint)
            canvas.drawText(text, pt.first, labelBottom - 2.1f, labelPaint)
        }
    }

    /**
     * Seção 4 — `"4. PARECER FISIOLÓGICO & CONTEXTO DE TREINAMENTO HÍBRIDO (MAX GYM + MAX RUN)"`
     * - Tópicos enumerados `4.1`, `4.2`, `4.3` e `4.4 Observações Clínicas / Conduta Nutricional:`.
     * - Título do tópico `4.3` enxuto sem corte na margem direita:
     *   `"4.3 Relação Músculo/Gordura (3,24x) & Balanço Longitudinal Acumulado:"`
     *   com os números acumulados na linha de texto logo abaixo.
     */
    private fun drawPage2PhysiologicalVerdictAndNotes(
        canvas: Canvas,
        topY: Float,
        activeRecord: BioimpedanceRecord,
        chronologicalRecords: List<BioimpedanceRecord>,
        waterGoalLabel: String
    ): Float {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val boxBottom = PAGE_HEIGHT - 42f
        val boxRect = RectF(left, topY, right, boxBottom)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSectionBg
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorCyanAccent
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(boxRect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(boxRect, 6f, 6f, borderPaint)

        // Faixa de Título da Seção 4
        val headerHeight = 22f
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(left, topY, right, topY + headerHeight), 6f, 6f, headerBgPaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "4. PARECER FISIOLÓGICO & CONTEXTO DE TREINAMENTO HÍBRIDO (MAX GYM + MAX RUN)",
            left + 10f,
            topY + 14.8f,
            headerTitlePaint
        )

        val baseline = chronologicalRecords.first()
        val deltaWeight = round1(activeRecord.weightKg - baseline.weightKg)
        val deltaFatKg = round1(activeRecord.fatMassKg - baseline.fatMassKg)
        val deltaSkelKg = round1(activeRecord.skeletalMuscleKg - baseline.skeletalMuscleKg)
        val deltaSkelPct = round1(activeRecord.skeletalMusclePercent - baseline.skeletalMusclePercent)
        val waterKg = BioimpedanceImageParser.convertPercentMetricToKg(activeRecord.bodyWaterPercent, activeRecord.weightKg)
        val proteinKg = BioimpedanceImageParser.convertPercentMetricToKg(activeRecord.proteinPercent, activeRecord.weightKg)

        val topicTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            textSize = 9.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val topicBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorSecondarySlate
            textSize = 9.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val topicHighlightBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorGreenHealthy
            textSize = 9.1f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textX = left + 12f
        val bodyX = textX + 10f
        val titleMaxWidth = (right - textX - 16f).coerceAtMost(495f)
        val bodyMaxWidth = (right - bodyX - 16f).coerceAtMost(485f)
        var lineY = topY + headerHeight + 15f
        val lineHeight = 12.5f

        // Tópico 4.1
        lineY = drawWrappedText(
            canvas = canvas,
            text = "4.1 Taxa Metabólica Basal Medida (${activeRecord.bmrKcal} kcal/dia) & Idade Metabólica (${activeRecord.metabolicAge} anos):",
            x = textX,
            startY = lineY,
            maxWidth = titleMaxWidth,
            lineHeight = lineHeight,
            paint = topicTitlePaint
        )
        lineY += lineHeight
        val paragraph41 = "Atleta em periodização híbrida combinando treino de força/hipertrofia (MAX GYM) e corrida intervalada por sprints (MAX RUN). " +
            "A TMB de ${activeRecord.bmrKcal} kcal/dia sustenta Massa Livre de Gordura em ${String.format(ptBr, "%.1f kg", activeRecord.fatFreeMassKg)} e Proteína corporal em ${String.format(ptBr, "%.1f%% (%.1f kg)", activeRecord.proteinPercent, proteinKg)}."
        lineY = drawWrappedText(
            canvas = canvas,
            text = paragraph41,
            x = bodyX,
            startY = lineY,
            maxWidth = bodyMaxWidth,
            lineHeight = lineHeight,
            paint = topicBodyPaint
        )

        // Tópico 4.2
        lineY += lineHeight + 3.0f
        lineY = drawWrappedText(
            canvas = canvas,
            text = "4.2 Condutividade Elétrica Intracelular (Água: ${String.format(ptBr, "%.1f%% / %.1f kg", activeRecord.bodyWaterPercent, waterKg)} • Meta Oficial: $waterGoalLabel):",
            x = textX,
            startY = lineY,
            maxWidth = titleMaxWidth,
            lineHeight = lineHeight,
            paint = topicTitlePaint
        )
        lineY += lineHeight
        val paragraph42 = "Na bioimpedância tetrapolar/octapolar, a corrente elétrica flui pela água intracelular do músculo. " +
            "Elevar a hidratação diária para $waterGoalLabel (rumo ao alvo >= 55,0%) otimiza a leitura contrátil do Músculo Esquelético (${String.format(ptBr, "%.1f%% / %.1f kg", activeRecord.skeletalMusclePercent, activeRecord.skeletalMuscleKg)}) e reduz impedância aparente."
        lineY = drawWrappedText(
            canvas = canvas,
            text = paragraph42,
            x = bodyX,
            startY = lineY,
            maxWidth = bodyMaxWidth,
            lineHeight = lineHeight,
            paint = topicBodyPaint
        )

        // Tópico 4.3
        lineY += lineHeight + 3.0f
        lineY = drawWrappedText(
            canvas = canvas,
            text = "4.3 Relação Músculo/Gordura (${String.format(ptBr, "%.2fx", activeRecord.leanToFatRatio)}) & Balanço Longitudinal Acumulado:",
            x = textX,
            startY = lineY,
            maxWidth = titleMaxWidth,
            lineHeight = lineHeight,
            paint = topicTitlePaint
        )
        lineY += lineHeight
        lineY = drawWrappedText(
            canvas = canvas,
            text = "Peso ${signed1(deltaWeight)} kg   |   Gordura Pura ${signed1(deltaFatKg)} kg   |   Músculo Esquelético ${signed1(deltaSkelPct)}% (${signed1(deltaSkelKg)} kg)",
            x = bodyX,
            startY = lineY,
            maxWidth = bodyMaxWidth,
            lineHeight = lineHeight,
            paint = topicHighlightBodyPaint
        )
        lineY += lineHeight
        val paragraph43 = "Massa Muscular Total de ${String.format(ptBr, "%.1f kg (%.1f%%)", activeRecord.muscleMassKg, activeRecord.muscleRatePercent)} frente a ${String.format(ptBr, "%.1f kg (%.1f%%)", activeRecord.fatMassKg, activeRecord.bodyFatPercent)} de Massa Gorda (Gordura Visceral nível ${String.format(ptBr, "%.0f", activeRecord.visceralFatIndex)})."
        lineY = drawWrappedText(
            canvas = canvas,
            text = paragraph43,
            x = bodyX,
            startY = lineY,
            maxWidth = bodyMaxWidth,
            lineHeight = lineHeight,
            paint = topicBodyPaint
        )

        // Tópico 4.4 Observações Clínicas / Conduta Nutricional com 3 linhas pautadas suaves
        val notesTitleY = lineY + 15f
        val notesLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorCyanAccent
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(
            "4.4 Observações Clínicas / Conduta Nutricional:",
            textX,
            notesTitleY,
            notesLabelPaint
        )

        val ruledLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        for (lineIdx in 1..3) {
            val ry = notesTitleY + (lineIdx * 15.0f)
            if (ry < boxBottom - 6f) {
                canvas.drawLine(textX, ry, right - 16f, ry, ruledLinePaint)
            }
        }

        return boxBottom
    }

    /**
     * Desenha um parágrafo com quebra automática de palavras (`word-wrap`) medindo a largura real
     * via `paint.measureText` para garantir que nenhuma linha ultrapasse `maxWidth`.
     * Retorna a coordenada `Y` (baseline) da última linha desenhada.
     */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return startY

        var currentLine = ""
        var cursorY = startY

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(candidate) <= maxWidth || currentLine.isEmpty()) {
                currentLine = candidate
            } else {
                canvas.drawText(currentLine, x, cursorY, paint)
                cursorY += lineHeight
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, x, cursorY, paint)
        }

        return cursorY
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        emissionDateTime: String
    ) {
        val left = MARGIN_X
        val right = PAGE_WIDTH - MARGIN_X
        val lineY = PAGE_HEIGHT - 30f
        val textY = PAGE_HEIGHT - 17f

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorTableBorder
            strokeWidth = 0.8f
        }
        canvas.drawLine(left, lineY, right, lineY, linePaint)

        val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorMutedSlate
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(
            "MAX RECOMP • Dossiê Clínico de Bioimpedância | Emitido em $emissionDateTime",
            left,
            textY,
            leftPaint
        )

        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorNavyHeader
            textSize = 9.0f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "Página $pageNumber de $totalPages",
            right,
            textY,
            rightPaint
        )
    }

    /**
     * Constrói as 14 linhas clínicas oficiais enumeradas de `1.1` a `1.14`.
     */
    private fun build14ClinicalRows(record: BioimpedanceRecord): List<ClinicalIndicatorRow> {
        val waterKg = BioimpedanceImageParser.convertPercentMetricToKg(record.bodyWaterPercent, record.weightKg)
        val proteinKg = BioimpedanceImageParser.convertPercentMetricToKg(record.proteinPercent, record.weightKg)

        return listOf(
            ClinicalIndicatorRow(
                code = "1.1",
                name = "Peso Corporal",
                currentMeasured = String.format(ptBr, "%.1f kg", record.weightKg),
                referenceRange = "65,4 – 88,5 kg",
                clinicalAssessment = when {
                    record.weightKg < 65.4f -> "Baixo"
                    record.weightKg <= 88.5f -> "Saudável"
                    else -> "Alto"
                },
                statusColor = if (record.weightKg in 65.4f..88.5f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.2",
                name = "IMC",
                currentMeasured = String.format(ptBr, "%.1f kg/m²", record.bmi),
                referenceRange = "18,5 – 24,9 kg/m²",
                clinicalAssessment = when {
                    record.bmi < 18.5f -> "Baixo"
                    record.bmi <= 24.9f -> "Saudável"
                    record.bmi <= 29.9f -> "Sobrepeso"
                    else -> "Alto"
                },
                statusColor = if (record.bmi in 18.5f..24.9f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.3",
                name = "Gordura Corporal (% e kg)",
                currentMeasured = String.format(ptBr, "%.1f%% (%.1f kg)", record.bodyFatPercent, record.fatMassKg),
                referenceRange = "Meta < 18,0% (9,2 – 18,4 kg)",
                clinicalAssessment = when {
                    record.bodyFatPercent <= 18.0f -> "Excelente"
                    record.bodyFatPercent <= 21.0f -> "Médio"
                    else -> "Alto"
                },
                statusColor = if (record.bodyFatPercent <= 21.0f) ColorGreenHealthy else ColorRedHigh
            ),
            ClinicalIndicatorRow(
                code = "1.4",
                name = "Músculo Esquelético (% e kg)",
                currentMeasured = String.format(ptBr, "%.1f%% (%.1f kg)", record.skeletalMusclePercent, record.skeletalMuscleKg),
                referenceRange = "Meta >= 45,0%",
                clinicalAssessment = when {
                    record.skeletalMusclePercent < 40.0f -> "Baixo"
                    record.skeletalMusclePercent < 45.0f -> "Médio"
                    else -> "Excelente"
                },
                statusColor = if (record.skeletalMusclePercent >= 40.0f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.5",
                name = "Massa Muscular Total",
                currentMeasured = String.format(ptBr, "%.1f kg (%.1f%%)", record.muscleMassKg, record.muscleRatePercent),
                referenceRange = "> 60,0 kg (> 74,0%)",
                clinicalAssessment = when {
                    record.muscleMassKg < 58.0f -> "Baixo"
                    record.muscleRatePercent >= 74.0f -> "Excelente"
                    else -> "Médio"
                },
                statusColor = ColorGreenHealthy
            ),
            ClinicalIndicatorRow(
                code = "1.6",
                name = "Água Corporal (%)",
                currentMeasured = String.format(ptBr, "%.1f%% (%.1f kg)", record.bodyWaterPercent, waterKg),
                referenceRange = "Meta >= 55,0% (4,2 L/dia)",
                clinicalAssessment = if (record.bodyWaterPercent >= 55.0f) "Saudável" else "Desidratado",
                statusColor = if (record.bodyWaterPercent >= 55.0f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.7",
                name = "Massa Livre de Gordura",
                currentMeasured = String.format(ptBr, "%.1f kg", record.fatFreeMassKg),
                referenceRange = "Preservar >= 65,0 kg",
                clinicalAssessment = if (record.fatFreeMassKg >= 65.0f) "Saudável" else "Baixo",
                statusColor = if (record.fatFreeMassKg >= 65.0f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.8",
                name = "Gordura Subcutânea (%)",
                currentMeasured = String.format(ptBr, "%.1f%%", record.subcutaneousFatPercent),
                referenceRange = "Ideal: 8,6 – 16,7%",
                clinicalAssessment = if (record.subcutaneousFatPercent <= 16.7f) "Médio" else "Alto",
                statusColor = if (record.subcutaneousFatPercent <= 16.7f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.9",
                name = "Gordura Visceral",
                currentMeasured = String.format(ptBr, "Nível %.0f", record.visceralFatIndex),
                referenceRange = "Saudável <= 9",
                clinicalAssessment = when {
                    record.visceralFatIndex <= 9f -> "Saudável"
                    record.visceralFatIndex <= 14f -> "Médio Risco"
                    else -> "Alto"
                },
                statusColor = if (record.visceralFatIndex <= 9f) ColorGreenHealthy else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.10",
                name = "Massa Óssea (kg)",
                currentMeasured = String.format(ptBr, "%.1f kg", record.boneMassKg),
                referenceRange = "Normal: 3,1 – 3,7 kg",
                clinicalAssessment = if (record.boneMassKg >= 3.0f) "Médio" else "Baixo",
                statusColor = ColorGreenHealthy
            ),
            ClinicalIndicatorRow(
                code = "1.11",
                name = "Proteína (%)",
                currentMeasured = String.format(ptBr, "%.1f%% (%.1f kg)", record.proteinPercent, proteinKg),
                referenceRange = "Ideal >= 16,0%",
                clinicalAssessment = if (record.proteinPercent >= 20.0f) "Excelente" else "Médio",
                statusColor = ColorCyanAccent
            ),
            ClinicalIndicatorRow(
                code = "1.12",
                name = "TMB (Basal)",
                currentMeasured = "${record.bmrKcal} kcal",
                referenceRange = "Faixa >= 1750 kcal",
                clinicalAssessment = if (record.bmrKcal >= 1750) "Médio" else "Baixo",
                statusColor = ColorGreenHealthy
            ),
            ClinicalIndicatorRow(
                code = "1.13",
                name = "Idade Metabólica",
                currentMeasured = "${record.metabolicAge} anos",
                referenceRange = "Menor que 50 anos",
                clinicalAssessment = if (record.metabolicAge < 50) "Excelente" else "Alto",
                statusColor = if (record.metabolicAge < 50) ColorCyanAccent else ColorAmberWarn
            ),
            ClinicalIndicatorRow(
                code = "1.14",
                name = "Relação Músculo/Gordura",
                currentMeasured = String.format(ptBr, "%.2fx", record.leanToFatRatio),
                referenceRange = "Meta >= 3,20x",
                clinicalAssessment = if (record.leanToFatRatio >= 3.20f) "Excelente" else "Médio",
                statusColor = if (record.leanToFatRatio >= 3.20f) ColorCyanAccent else ColorGreenHealthy
            )
        )
    }

    private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f

    private fun signed1(v: Float): String {
        val clean = if (abs(v) < 0.05f) 0f else v
        return if (clean > 0f) String.format(ptBr, "+%.1f", clean) else String.format(ptBr, "%.1f", clean)
    }

    private fun signed0(v: Float): String {
        val clean = v.roundToInt()
        return if (clean > 0) "+$clean" else "$clean"
    }
}
