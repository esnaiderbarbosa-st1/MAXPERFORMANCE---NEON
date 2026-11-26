package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.GpsTrackPoint
import com.example.data.model.RecordedKmSplit
import com.example.data.model.RunningSession
import com.example.data.model.SplitSelectorMode
import com.example.data.model.UserProfileData
import com.example.data.model.buildSplitsForMode
import com.example.data.model.calculateSprintAnalytics
import com.example.data.model.extractValidHrCurveSamples
import com.example.data.model.hasHrCurve
import com.example.data.model.hasHrZones
import com.example.data.model.hrCurveUnavailableMessage
import com.example.data.model.isClassifiedAsRace
import com.example.data.model.isSimulatedOrTestSession
import com.example.data.model.resolveSessionRoutePointsWithPhases
import com.example.data.model.toHrTimelineList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Gerador profissional de Imagem Única Contínua ("Print Longo" / Share Card MAX RUN) do Relatório da Sessão de Corrida.
 * Inclui Mapa pintado por Sprints + Trecho de Vitória (PR) em Dourado (#FFD700) com Auto-BoundingBox,
 * Cabeçalho com Semana X • Dia Y + Estrutura, Grade 3x2 sem BPM duplicado (Pace nos Tiros, Cadência, Elevação)
 * e Tabela de Splits sincronizada com o seletor ativo (Por Bloco/Sprint, 500 m ou 1 km Principal).
 */
object SessionReportBitmapGenerator {

    private const val TAG = "SessionReportBitmap"

    suspend fun generateFullSessionReportBitmap(
        context: Context,
        session: RunningSession,
        isLightReport: Boolean,
        userProfile: UserProfileData?,
        gpsPoints: List<GpsTrackPoint>,
        recordedSplits: List<RecordedKmSplit>,
        splitSelectorMode: SplitSelectorMode = SplitSelectorMode.ONE_KM_MAIN,
        runningShoes: List<com.example.data.model.RunningShoe> = emptyList(),
        mapTileStyle: com.example.ui.components.MapTileStyle? = null
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val ptBr = Locale("pt", "BR")

        val sprintAnalytics = calculateSprintAnalytics(session, gpsPoints)
        val routePointItems = resolveSessionRoutePointsWithPhases(session, gpsPoints)
        val dossier = com.example.data.model.buildRunPhysiologyDossier(session, userProfile, gpsPoints)

        // 1. Determina lista efetiva de splits conforme o seletor ativo
        val effectiveSplits: List<RecordedKmSplit> = if (recordedSplits.isNotEmpty() && recordedSplits.any { it.durationSec > 0 }) {
            recordedSplits
        } else {
            buildSplitsForMode(session, splitSelectorMode, gpsPoints)
        }

        val splitsCount = effectiveSplits.size.coerceAtLeast(1)
        val splitsBlockHeight = 148f + (splitsCount * 64f)

        val reportHrSamples: List<Int> = extractValidHrCurveSamples(dossier, session.hrTimeline)
        val hasCurve = hasHrCurve(reportHrSamples)
        val hasZones = hasHrZones(dossier)
        val hrCardHeight = when {
            hasCurve && hasZones -> 650f
            hasCurve -> 370f
            hasZones -> 490f
            else -> 250f
        }

        val hasMainRun = session.mainRunDistanceMeters != null && session.mainRunDistanceMeters > 0 &&
                session.mainRunDurationSec != null && session.mainRunDurationSec > 0 &&
                (session.mainRunDurationSec < session.durationSec || session.mainRunDistanceMeters < session.distanceMeters)

        val heroHeight = if (hasMainRun) 160f else 120f
        val mapHeight = 440f
        val legendHeight = 56f
        val gridHeight = 424f

        // Cálculo dinâmico da altura total exata sem corte
        val totalHeight = (60f + 240f + heroHeight + mapHeight + 16f + legendHeight + 30f + gridHeight + 48f + hrCardHeight + 36f + splitsBlockHeight + 40f + 110f).roundToInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paleta de Cores
        val bgColor = if (isLightReport) 0xFFF8FAFC.toInt() else 0xFF0D0F14.toInt()
        val cardBgColor = if (isLightReport) 0xFFFFFFFF.toInt() else 0xFF161922.toInt()
        val cardBorderColor = if (isLightReport) 0xFFE2E8F0.toInt() else 0xFF242A38.toInt()
        val primaryTextColor = if (isLightReport) 0xFF0F172A.toInt() else 0xFFFFFFFF.toInt()
        val secondaryTextColor = if (isLightReport) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()
        val accentColor = if (isLightReport) 0xFF0284C7.toInt() else 0xFF00E5FF.toInt()

        // 1. Fundo Geral
        canvas.drawColor(bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBgColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        var currentY = 60f
        val marginX = 48f
        val contentWidth = width - (marginX * 2)

        // ========================================================
        // BLOCO A: CABEÇALHO DO ATLETA + IDENTIFICAÇÃO DO TREINO (SEMANA X • DIA Y + ESTRUTURA)
        // ========================================================
        paint.color = accentColor
        paint.textSize = 28f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
        paint.textSkewX = -0.14f
        canvas.drawText("MAX PERFORMANCE • MAX RUN", marginX, currentY + 30f, paint)
        paint.textSkewX = 0f

        paint.color = primaryTextColor
        paint.textSize = 44f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val athleteName = userProfile?.nome?.ifBlank { "Atleta MAX" } ?: "Atleta MAX"
        canvas.drawText(athleteName, marginX, currentY + 84f, paint)

        // Badge de Identificação da Sessão à direita do nome
        val isRaceBadge = session.isClassifiedAsRace()
        val rawBadgeTitle = com.example.engine.RunningProgramManager.getDisplayTitleForSession(session)
        val workoutBadgeText = if (isRaceBadge && !rawBadgeTitle.startsWith("PROVA", ignoreCase = true)) {
            "PROVA • $rawBadgeTitle"
        } else {
            rawBadgeTitle
        }
        paint.textSize = 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val badgeW = paint.measureText(workoutBadgeText) + 36f
        val badgeRect = RectF(marginX + contentWidth - badgeW, currentY + 46f, marginX + contentWidth, currentY + 92f)
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                isRaceBadge && isLightReport -> 0xFFFEF3C7.toInt()
                isRaceBadge -> 0xFF332309.toInt()
                isLightReport -> 0xFFE0F2FE.toInt()
                else -> 0xFF132A3E.toInt()
            }
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(badgeRect, 14f, 14f, badgeBgPaint)
        paint.color = when {
            isRaceBadge && isLightReport -> 0xFFB45309.toInt()
            isRaceBadge -> 0xFFFBBF24.toInt()
            else -> accentColor
        }
        canvas.drawText(workoutBadgeText, badgeRect.left + 18f, currentY + 77f, paint)

        val dateStr = SimpleDateFormat("dd/MM/yyyy • HH:mm", ptBr).format(Date(session.date))
        val modality = if (session.isGentleMode) "Caminhada Regenerativa" else if (session.environment == "TREADMILL") "Esteira" else "Ar Livre"

        paint.color = secondaryTextColor
        paint.textSize = 25f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        canvas.drawText("$dateStr  •  $modality  •  ${sprintAnalytics.structureSummary}", marginX, currentY + 128f, paint)

        currentY += 158f

        // ========================================================
        // MÉTRICA HERÓI: DISTÂNCIA GIGANTE SLIM & ITALIC (SEPARADOR VÍRGULA)
        // ========================================================
        val heroDistKm = if (hasMainRun) session.mainRunDistanceMeters!! / 1000f else session.distanceMeters / 1000f
        val distNumberStr = String.format(ptBr, "%.2f", heroDistKm)

        paint.color = primaryTextColor
        paint.textSize = 100f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
        paint.textSkewX = -0.14f
        val distNumWidth = paint.measureText(distNumberStr)
        canvas.drawText(distNumberStr, marginX, currentY + 90f, paint)
        paint.textSkewX = 0f

        paint.color = secondaryTextColor
        paint.textSize = 46f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        canvas.drawText(" km", marginX + distNumWidth, currentY + 68f, paint)

        if (hasMainRun) {
            val totalDistStr = String.format(ptBr, "%.2f km", session.distanceMeters / 1000f)
            val totalM = session.durationSec / 60
            val totalS = session.durationSec % 60
            val totalTimeStr = String.format(Locale.US, "%02d:%02d", totalM, totalS)
            paint.color = secondaryTextColor
            paint.textSize = 28f
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            canvas.drawText("Total c/ Aquec. + Desaquec.: $totalDistStr • $totalTimeStr", marginX, currentY + 138f, paint)
            currentY += 160f
        } else {
            currentY += 120f
        }

        // ========================================================
        // BLOCO B: MAPA PINTADO POR SPRINTS + TRECHO DE VITÓRIA (PR) E AUTO-BOUNDINGBOX
        // ========================================================
        val mapRect = RectF(marginX, currentY, marginX + contentWidth, currentY + mapHeight)

        // Sem fallback estático de Rio Branco para corridas reais em outras cidades
        val effectiveRouteItems = if (routePointItems.size >= 2) {
            routePointItems
        } else if (session.isSimulatedOrTestSession()) {
            val simPts = MapTileHelper.getAvenueRoutePoints(session.distanceMeters)
            simPts.mapIndexed { idx, (lat, lon) ->
                val ratio = idx.toDouble() / (simPts.size - 1).coerceAtLeast(1)
                val phase = when {
                    ratio < 0.12 -> "WARMUP"
                    ratio > 0.88 -> "COOL_DOWN"
                    (idx / 4) % 2 == 0 -> "RUN"
                    else -> "WALK"
                }
                com.example.data.model.RoutePointItem(
                    lat = lat,
                    lon = lon,
                    timestamp = idx * 1000L,
                    speed = if (phase == "RUN") 3.2f else 1.5f,
                    phase = phase,
                    blockIndex = idx / 4,
                    isPrSegment = ratio in 0.50..0.64
                )
            }
        } else {
            emptyList()
        }

        val routePointsList: List<Pair<Double, Double>> = effectiveRouteItems.map { Pair(it.lat, it.lon) }

        val mapProj = MapTileHelper.calculateProjection(routePointsList, contentWidth, mapHeight)
        val isSatellite = mapTileStyle == com.example.ui.components.MapTileStyle.SATELLITE
        val tileUrlTemplate = when {
            isSatellite -> MapTileHelper.SATELLITE_URL
            isLightReport -> MapTileHelper.VOYAGER_URL
            else -> MapTileHelper.DARK_URL
        }

        val tileBitmaps: Map<TileCoord, Bitmap> = if (mapProj.visibleTiles.isNotEmpty()) {
            try {
                MapTileHelper.fetchTiles(mapProj.visibleTiles, tileUrlTemplate)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao buscar tiles de rua: ${e.message}")
                emptyMap()
            }
        } else emptyMap()

        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(mapRect, 32f, 32f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val mapBasePaint = Paint().apply {
            color = if (isLightReport && !isSatellite) 0xFFEAEFF5.toInt() else 0xFF141824.toInt()
        }
        canvas.drawRect(mapRect, mapBasePaint)

        val streetGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightReport && !isSatellite) 0xFFD8E2EC.toInt() else 0xFF1F2636.toInt()
            strokeWidth = 4f
        }
        var gx = mapRect.left + 50f
        while (gx < mapRect.right) {
            canvas.drawLine(gx, mapRect.top, gx + 80f, mapRect.bottom, streetGridPaint)
            gx += 120f
        }
        var gy = mapRect.top + 40f
        while (gy < mapRect.bottom) {
            canvas.drawLine(mapRect.left, gy, mapRect.right, gy - 30f, streetGridPaint)
            gy += 100f
        }

        if (routePointsList.size >= 2) {
            val bounds = mapProj.bounds
            val fitScale = mapProj.fitScale.coerceIn(1f, 2.6f)
            val mapCenterX = mapRect.centerX()
            val mapCenterY = mapRect.centerY()

            canvas.save()
            canvas.scale(fitScale, fitScale, mapCenterX, mapCenterY)

            for ((tileCoord, bmp) in tileBitmaps) {
                if (!bmp.isRecycled) {
                    val tileOriginX = marginX + (tileCoord.x * 256.0 - bounds.minWorldX).toFloat()
                    val tileOriginY = currentY + (tileCoord.y * 256.0 - bounds.minWorldY).toFloat()
                    canvas.drawBitmap(bmp, tileOriginX, tileOriginY, null)
                }
            }

            if (tileBitmaps.isNotEmpty() && (isSatellite || !isLightReport)) {
                val scrimPaint = Paint().apply {
                    color = if (isSatellite) 0x2E04070D else 0x38070B14
                    style = Paint.Style.FILL
                }
                canvas.drawRect(
                    mapRect.left - 500f,
                    mapRect.top - 500f,
                    mapRect.right + 500f,
                    mapRect.bottom + 500f,
                    scrimPaint
                )
            }

            val strokeScale = (1f / fitScale).coerceIn(0.48f, 1f)
            val rawScreenPts = routePointsList.map { pt ->
                val worldX = MapTileHelper.lonToWorldX(pt.second, mapProj.zoom)
                val worldY = MapTileHelper.latToWorldY(pt.first, mapProj.zoom)
                PointF(
                    marginX + (worldX - bounds.minWorldX).toFloat(),
                    currentY + (worldY - bounds.minWorldY).toFloat()
                )
            }
            val screenPts = com.example.ui.components.RunRouteLaneOffsetEngine.applyDirectionalLaneOffsetPointF(
                rawPoints = rawScreenPts,
                gpsCoords = routePointsList,
                offsetPx = 7.0f * strokeScale // 3.5.dp equivalente em 1080p
            )

            val borderExtraPx = 5.0f * strokeScale // +2.5.dp equivalente em 1080p
            val contrastBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = com.example.ui.components.RunRouteLaneOffsetEngine.CONTRAST_BORDER_HEX
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = com.example.ui.components.RunRouteLaneOffsetEngine.WARMUP_COLOR_HEX
                style = Paint.Style.STROKE
                strokeWidth = 8f * strokeScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = DashPathEffect(floatArrayOf(18f * strokeScale, 12f * strokeScale), 0f)
            }
            val walkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = com.example.ui.components.RunRouteLaneOffsetEngine.WALK_COLOR_HEX
                style = Paint.Style.STROKE
                strokeWidth = 9.5f * strokeScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val runPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = com.example.ui.components.RunRouteLaneOffsetEngine.SPRINT_COLOR_HEX
                style = Paint.Style.STROKE
                strokeWidth = 10.5f * strokeScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val prGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55FFD600
                style = Paint.Style.STROKE
                strokeWidth = 20f * strokeScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val prCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = com.example.ui.components.RunRouteLaneOffsetEngine.BEST_SPRINT_COLOR_HEX
                style = Paint.Style.STROKE
                strokeWidth = 12f * strokeScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            data class BitmapPhaseSubPath(
                val path: android.graphics.Path,
                val phaseKey: String
            )

            val groupedPaths = ArrayList<BitmapPhaseSubPath>()
            var idx = 0
            while (idx < screenPts.size - 1) {
                val firstItem = effectiveRouteItems.getOrNull(idx + 1) ?: effectiveRouteItems.getOrNull(idx)
                val firstKey = when {
                    firstItem?.isPrSegment == true || firstItem?.phase?.equals("PR", ignoreCase = true) == true -> "PR"
                    firstItem?.phase?.uppercase(Locale.US) in listOf("WARMUP", "COOL_DOWN") -> "WARMUP"
                    firstItem?.phase?.equals("WALK", ignoreCase = true) == true -> "WALK"
                    else -> "RUN"
                }
                val subPath = android.graphics.Path().apply {
                    moveTo(screenPts[idx].x, screenPts[idx].y)
                    lineTo(screenPts[idx + 1].x, screenPts[idx + 1].y)
                }
                var j = idx + 1
                while (j < screenPts.size - 1) {
                    val nextItem = effectiveRouteItems.getOrNull(j + 1) ?: effectiveRouteItems.getOrNull(j)
                    val nextKey = when {
                        nextItem?.isPrSegment == true || nextItem?.phase?.equals("PR", ignoreCase = true) == true -> "PR"
                        nextItem?.phase?.uppercase(Locale.US) in listOf("WARMUP", "COOL_DOWN") -> "WARMUP"
                        nextItem?.phase?.equals("WALK", ignoreCase = true) == true -> "WALK"
                        else -> "RUN"
                    }
                    if (nextKey != firstKey) break
                    subPath.lineTo(screenPts[j + 1].x, screenPts[j + 1].y)
                    j++
                }
                groupedPaths.add(BitmapPhaseSubPath(subPath, firstKey))
                idx = j
            }

            // Halo luminoso de PR
            for (group in groupedPaths) {
                if (group.phaseKey == "PR") {
                    canvas.drawPath(group.path, prGlowPaint)
                }
            }

            // 1ª PASSADA: Contorno escuro contínuo (#0D0F14, alpha = 0.75f, strokeWidth + 2.5.dp) ao longo da rota inteira
            val fullBorderPath = android.graphics.Path().apply {
                moveTo(screenPts.first().x, screenPts.first().y)
                for (k in 1 until screenPts.size) {
                    lineTo(screenPts[k].x, screenPts[k].y)
                }
            }
            contrastBorderPaint.strokeWidth = runPaint.strokeWidth + borderExtraPx
            canvas.drawPath(fullBorderPath, contrastBorderPaint)
            for (group in groupedPaths) {
                if (group.phaseKey == "PR") {
                    contrastBorderPaint.strokeWidth = prCorePaint.strokeWidth + borderExtraPx
                    canvas.drawPath(group.path, contrastBorderPaint)
                }
            }

            // 2ª PASSADA: Sub-Paths contínuos coloridos por fase sem bordas pretas entre segmentos adjacentes
            for (group in groupedPaths) {
                when (group.phaseKey) {
                    "PR" -> canvas.drawPath(group.path, prCorePaint)
                    "WARMUP" -> canvas.drawPath(group.path, dashPaint)
                    "WALK" -> canvas.drawPath(group.path, walkPaint)
                    else -> canvas.drawPath(group.path, runPaint)
                }
            }

            val startPt = screenPts.first()
            val endPt = screenPts.last()
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            markerPaint.color = 0xFF00E676.toInt()
            canvas.drawCircle(startPt.x, startPt.y, 14f * strokeScale, markerPaint)
            markerPaint.color = Color.WHITE
            canvas.drawCircle(startPt.x, startPt.y, 6f * strokeScale, markerPaint)

            markerPaint.color = 0xFFFF3D00.toInt()
            canvas.drawCircle(endPt.x, endPt.y, 14f * strokeScale, markerPaint)
            markerPaint.color = Color.WHITE
            canvas.drawCircle(endPt.x, endPt.y, 6f * strokeScale, markerPaint)

            canvas.restore()
        } else {
            paint.color = secondaryTextColor
            paint.textSize = 28f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val emptyMsg = "Sessão sem rota GPS gravada"
            val emptyW = paint.measureText(emptyMsg)
            canvas.drawText(emptyMsg, mapRect.centerX() - emptyW / 2f, mapRect.centerY() + 10f, paint)
        }

        canvas.restore()
        canvas.drawRoundRect(mapRect, 32f, 32f, borderPaint)

        currentY += mapHeight + 16f

        // Legenda horizontal limpa logo abaixo do mapa: 🔵 Caminhada  🟢 Corrida  🟡 Melhor Sprint (PR)
        val legendRect = RectF(marginX, currentY, marginX + contentWidth, currentY + legendHeight)
        canvas.drawRoundRect(legendRect, 18f, 18f, cardPaint)
        canvas.drawRoundRect(legendRect, 18f, 18f, borderPaint)

        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val legendText = "🔵 Caminhada (Recup.)     🟢 Corrida (Sprint)     🟡 Melhor Sprint (PR)"
        val legendW = paint.measureText(legendText)
        paint.color = primaryTextColor
        canvas.drawText(legendText, marginX + (contentWidth - legendW) / 2f, currentY + 36f, paint)

        currentY += legendHeight + 30f

        // ========================================================
        // BLOCO C: GRADE ABERTA 3x2 DE MÉTRICAS PRINCIPAIS (FIM DO BPM DUPLICADO!)
        // ========================================================
        val effectiveDur = if (hasMainRun) session.mainRunDurationSec!! else session.durationSec
        val durM = effectiveDur / 60
        val durS = effectiveDur % 60
        val durStr = String.format(Locale.US, "%02d:%02d", durM, durS)

        val effectivePaceSec = if (hasMainRun && session.mainRunAvgPaceSecPerKm != null && session.mainRunAvgPaceSecPerKm > 0) {
            session.mainRunAvgPaceSecPerKm
        } else if (session.avgPaceSecPerKm > 0) {
            session.avgPaceSecPerKm
        } else 0

        val paceStr = if (effectivePaceSec > 0) {
            String.format(Locale.US, "%d'%02d\"/km", effectivePaceSec / 60, effectivePaceSec % 60)
        } else "--:--"

        val caloriesDisplayPair = if (dossier.isWeightMissingForCalories) {
            Pair("Peso não informado", "Calorias")
        } else {
            Pair("${dossier.totalCaloriesKcal} kcal", "Total Kcal (${dossier.activeCaloriesKcal} ativas)")
        }

        val cadenceDisplayText = if (dossier.avgCadenceSpm > 0) {
            val maxStr = if (dossier.maxCadenceSpm > 0) "${dossier.maxCadenceSpm}" else "--"
            "${dossier.avgCadenceSpm}/$maxStr ppm"
        } else "--"
        val strideDisplayText = if (dossier.avgStrideLengthCm > 0) {
            val maxStr = if (dossier.maxStrideLengthCm > 0) "${dossier.maxStrideLengthCm}" else "--"
            "${dossier.avgStrideLengthCm}/$maxStr cm"
        } else "--"
        val vitalityDisplayText = if (dossier.vitalityScoreGain > 0) "+${dossier.vitalityScoreGain} Vitalidade" else "--"
        val stepsDisplayText = if (dossier.totalSteps > 0) "${dossier.totalSteps} passos" else "--"

        val gridData = listOf(
            listOf(
                Pair(dossier.formattedDurationHhMmSs, "Tempo (HH:MM:SS)"),
                Pair(if (dossier.avgPaceSecPerKm > 0) "${dossier.formattedAvgPace}/km" else "--", "Média de Ritmo"),
                caloriesDisplayPair
            ),
            listOf(
                Pair(sprintAnalytics.avgSprintPaceText, sprintAnalytics.primaryPaceLabel),
                Pair(cadenceDisplayText, "Cadência Méd / Máx"),
                Pair(strideDisplayText, "Passada Méd / Máx")
            ),
            listOf(
                Pair(vitalityDisplayText, "Efeito do Treino"),
                Pair(stepsDisplayText, "Passos Totais"),
                Pair("+${sprintAnalytics.elevationGainMeters} m", "Elevação (m)")
            )
        )

        val colWidth = (contentWidth - 32f) / 3f
        val cellHeight = 120f

        gridData.forEachIndexed { rowIdx, rowItems ->
            val rowY = currentY + (rowIdx * (cellHeight + 16f))
            rowItems.forEachIndexed { colIdx, item ->
                val colX = marginX + (colIdx * (colWidth + 16f))
                val cellRect = RectF(colX, rowY, colX + colWidth, rowY + cellHeight)
                canvas.drawRoundRect(cellRect, 20f, 20f, cardPaint)
                canvas.drawRoundRect(cellRect, 20f, 20f, borderPaint)

                // Valor (com alto contraste garantido no Modo Claro para Cadência/Passada em Âmbar Escuro #D97706)
                paint.color = when {
                    (rowIdx == 1 && colIdx == 0) || (rowIdx == 2 && colIdx == 0) ->
                        if (isLightReport) 0xFF059669.toInt() else 0xFF00E676.toInt()
                    (rowIdx == 1 && (colIdx == 1 || colIdx == 2)) ->
                        if (isLightReport) 0xFFD97706.toInt() else 0xFFFFCA28.toInt()
                    else -> primaryTextColor
                }
                paint.textSize = 31f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                canvas.drawText(item.first, colX + 18f, rowY + 52f, paint)

                // Rótulo
                paint.color = secondaryTextColor
                paint.textSize = 20f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(item.second, colX + 18f, rowY + 90f, paint)
            }
        }

        currentY += (cellHeight * 3) + 64f

        // ========================================================
        // BLOCO D: SEÇÃO DE FREQUÊNCIA CARDÍACA (5 ZONAS CALIBRADAS POR FCmáx EFETIVA)
        // ========================================================
        val hrCardRect = RectF(marginX, currentY, marginX + contentWidth, currentY + hrCardHeight)
        canvas.drawRoundRect(hrCardRect, 32f, 32f, cardPaint)
        canvas.drawRoundRect(hrCardRect, 32f, 32f, borderPaint)

        val hrAccentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEF4444.toInt()
            style = Paint.Style.FILL
        }
        val hrBarRect = RectF(marginX + 32f, currentY + 32f, marginX + 38f, currentY + 58f)
        canvas.drawRoundRect(hrBarRect, 3f, 3f, hrAccentBarPaint)

        paint.color = primaryTextColor
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("FREQUÊNCIA CARDÍACA", marginX + 48f, currentY + 53f, paint)

        val centerAhrX = marginX + (contentWidth / 4f)
        val centerMaxX = marginX + (contentWidth * 3f / 4f)
        val hrHeaderY = currentY + 110f

        val ahrStr = if (dossier.avgHeartRateBpm > 0) "${dossier.avgHeartRateBpm}" else "--"
        val maxHrStr = if (dossier.maxHeartRateBpm > 0) "${dossier.maxHeartRateBpm}" else "--"

        paint.color = primaryTextColor
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val ahrW = paint.measureText(ahrStr)
        canvas.drawText(ahrStr, centerAhrX - (ahrW / 2f), hrHeaderY, paint)

        paint.color = secondaryTextColor
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        val ahrLbl = "BPM Médio"
        val ahrLblW = paint.measureText(ahrLbl)
        canvas.drawText(ahrLbl, centerAhrX - (ahrLblW / 2f), hrHeaderY + 28f, paint)

        paint.color = primaryTextColor
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val maxW = paint.measureText(maxHrStr)
        canvas.drawText(maxHrStr, centerMaxX - (maxW / 2f), hrHeaderY, paint)

        paint.color = secondaryTextColor
        paint.textSize = 22f
        paint.typeface = Typeface.DEFAULT
        val maxLbl = "BPM Máximo"
        val maxLblW = paint.measureText(maxLbl)
        canvas.drawText(maxLbl, centerMaxX - (maxLblW / 2f), hrHeaderY + 28f, paint)

        val reportZones = listOf(
            Pair("Recuperação / Aquec.", 0xFF29B6F6.toInt()),
            Pair("Queima de Gordura", if (isLightReport) 0xFF10B981.toInt() else 0xFF00E676.toInt()),
            Pair("Aeróbico", if (isLightReport) 0xFFD97706.toInt() else 0xFFFFCA28.toInt()),
            Pair("Anaeróbico", 0xFFFF7043.toInt()),
            Pair("Limite", 0xFFFF5252.toInt())
        )
        val zoneColWidth = 250f

        fun drawZoneBars(startY: Float) {
            var zoneRowY = startY
            reportZones.forEachIndexed { idx, (zoneName, zoneColor) ->
                val pct = dossier.hrZones5.getOrNull(idx)?.percentage ?: 0f

                paint.color = primaryTextColor
                paint.textSize = 23f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(zoneName, marginX + 32f, zoneRowY + 14f, paint)

                val barLeft = marginX + 32f + zoneColWidth
                val barRight = marginX + contentWidth - 110f
                val barWidthTotal = barRight - barLeft
                val barTrackRect = RectF(barLeft, zoneRowY + 2f, barRight, zoneRowY + 16f)

                val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isLightReport) 0xFFE2E8F0.toInt() else 0xFF242A38.toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(barTrackRect, 7f, 7f, trackPaint)

                val fillWidth = (pct / 100f) * barWidthTotal
                if (pct > 0f) {
                    val fillRect = RectF(barLeft, zoneRowY + 2f, barLeft + fillWidth.coerceAtLeast(10f), zoneRowY + 16f)
                    val fillZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = zoneColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(fillRect, 7f, 7f, fillZonePaint)
                }

                paint.color = primaryTextColor
                paint.textSize = 23f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val pctStr = "${pct.roundToInt()}%"
                canvas.drawText(pctStr, barRight + 16f, zoneRowY + 14f, paint)

                zoneRowY += 46f
            }
        }

        if (!hasCurve) {
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = secondaryTextColor
            paint.textAlign = Paint.Align.CENTER
            val noticeY = if (hasZones) hrHeaderY + 76f else hrHeaderY + 92f
            canvas.drawText(
                hrCurveUnavailableMessage(dossier.avgHeartRateBpm, dossier.maxHeartRateBpm),
                marginX + (contentWidth / 2f),
                noticeY,
                paint
            )
            paint.textAlign = Paint.Align.LEFT

            if (hasZones) {
                drawZoneBars(hrHeaderY + 120f)
            }
        } else {
            val graphTop = hrHeaderY + 50f
            val graphHeight = 130f
            val graphRect = RectF(marginX + 32f, graphTop, marginX + contentWidth - 32f, graphTop + graphHeight)

            val graphBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLightReport) 0xFFF1F5F9.toInt() else 0xFF0F121A.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(graphRect, 16f, 16f, graphBgPaint)
            val graphBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLightReport) 0xFFCBD5E1.toInt() else 0xFF242A38.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            canvas.drawRoundRect(graphRect, 16f, 16f, graphBorderPaint)

            val plotTop = graphTop + 10f
            val plotBottom = graphTop + graphHeight - 28f
            val plotH = plotBottom - plotTop
            val minScale = 80f
            val maxScale = maxOf(190f, (((dossier.maxHeartRateBpm + 9) / 10) * 10).toFloat())

            fun bpmToCanvasY(bpm: Float): Float {
                val ratio = (bpm - minScale) / (maxScale - minScale).coerceAtLeast(30f)
                return (plotBottom - (ratio * plotH)).coerceIn(plotTop, plotBottom)
            }

            val topGuideBpm = (maxScale - 5f).coerceAtLeast(180f)
            val guideBpms = listOf(topGuideBpm, 155f, 130f, 105f, 80f)
            val dottedGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLightReport) 0x8094A3B8.toInt() else 0x38FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
            }
            val solidBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isLightReport) 0xFF94A3B8.toInt() else 0x55FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
            }
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = if (isLightReport) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()

            guideBpms.forEach { gBpm ->
                val gy = bpmToCanvasY(gBpm)
                val linePaintToUse = if (gBpm <= 80f) solidBasePaint else dottedGuidePaint
                canvas.drawLine(graphRect.left + 12f, gy, graphRect.right - 56f, gy, linePaintToUse)
                canvas.drawText(gBpm.toInt().toString(), graphRect.right - 48f, gy + 6f, paint)
            }

            val graphPlotRight = graphRect.right - 58f
            val graphPlotLeft = graphRect.left + 16f
            val graphPlotWidth = graphPlotRight - graphPlotLeft

            val stepX = graphPlotWidth / (reportHrSamples.size - 1).coerceAtLeast(1)
            val curvePath = android.graphics.Path()

            val firstX = graphPlotLeft
            val firstY = bpmToCanvasY(reportHrSamples.first().toFloat())
            curvePath.moveTo(firstX, firstY)

            for (i in 0 until reportHrSamples.size - 1) {
                val x0 = graphPlotLeft + (i * stepX)
                val y0 = bpmToCanvasY(reportHrSamples[i].toFloat())
                val x1 = graphPlotLeft + ((i + 1) * stepX)
                val y1 = bpmToCanvasY(reportHrSamples[i + 1].toFloat())
                val midX = (x0 + x1) / 2f
                val midY = (y0 + y1) / 2f
                if (i == 0) {
                    curvePath.lineTo(midX, midY)
                } else {
                    curvePath.quadTo(x0, y0, midX, midY)
                }
            }
            val lastX = graphPlotLeft + ((reportHrSamples.size - 1) * stepX)
            val lastY = bpmToCanvasY(reportHrSamples.last().toFloat())
            curvePath.lineTo(lastX, lastY)

            val z4Top = dossier.hrZones5.getOrNull(3)?.maxBpm?.toFloat() ?: 172f
            val z3Top = dossier.hrZones5.getOrNull(2)?.maxBpm?.toFloat() ?: 154f
            val z2Top = dossier.hrZones5.getOrNull(1)?.maxBpm?.toFloat() ?: 133f
            val z1Top = dossier.hrZones5.getOrNull(0)?.maxBpm?.toFloat() ?: 116f

            val stopZ4 = ((bpmToCanvasY(z4Top) - plotTop) / plotH).coerceIn(0.05f, 0.90f)
            val stopZ3 = ((bpmToCanvasY(z3Top) - plotTop) / plotH).coerceIn(stopZ4 + 0.03f, 0.93f)
            val stopZ2 = ((bpmToCanvasY(z2Top) - plotTop) / plotH).coerceIn(stopZ3 + 0.03f, 0.96f)
            val stopZ1 = ((bpmToCanvasY(z1Top) - plotTop) / plotH).coerceIn(stopZ2 + 0.03f, 0.99f)

            val lineShader = android.graphics.LinearGradient(
                0f, plotTop, 0f, plotBottom,
                intArrayOf(
                    0xFFFF453A.toInt(),
                    0xFFFF5252.toInt(),
                    0xFFFF8A00.toInt(),
                    0xFFFACC15.toInt(),
                    0xFF22C55E.toInt(),
                    0xFF38BDF8.toInt()
                ),
                floatArrayOf(0f, stopZ4, stopZ3, stopZ2, stopZ1, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = lineShader
            }
            canvas.drawPath(curvePath, curvePaint)

            // Régua de Tempo na Base 100% coerente com o card Tempo (HH:MM:SS) -> dossier.durationSeconds
            val xTimeLabels = com.example.ui.components.buildTimeAxisLabels(dossier.durationSeconds, tickCount = 2)
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = if (isLightReport) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()
            xTimeLabels.forEachIndexed { idx, tLabel ->
                val frac = idx.toFloat() / (xTimeLabels.size - 1).coerceAtLeast(1)
                val tx = graphPlotLeft + frac * graphPlotWidth
                val tw = paint.measureText(tLabel)
                val alignedX = when (idx) {
                    0 -> tx
                    xTimeLabels.lastIndex -> tx - tw
                    else -> tx - (tw / 2f)
                }
                canvas.drawText(tLabel, alignedX, graphTop + graphHeight - 7f, paint)
            }

            val zoneRowStartY = graphTop + graphHeight + 36f
            if (!hasZones) {
                paint.color = secondaryTextColor
                paint.textSize = 23f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("Zonas indisponíveis", marginX + (contentWidth / 2f), zoneRowStartY + 18f, paint)
                paint.textAlign = Paint.Align.LEFT
            } else {
                drawZoneBars(zoneRowStartY)
            }
        }

        currentY += hrCardHeight + 36f

        // ========================================================
        // BLOCO E: TABELA DE SPLITS E PARCIAIS COM SELETOR DINÂMICO
        // ========================================================
        val splitsCardRect = RectF(marginX, currentY, marginX + contentWidth, currentY + splitsBlockHeight)
        canvas.drawRoundRect(splitsCardRect, 32f, 32f, cardPaint)
        canvas.drawRoundRect(splitsCardRect, 32f, 32f, borderPaint)

        val splitsAccentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        val splitsBarRect = RectF(marginX + 32f, currentY + 32f, marginX + 38f, currentY + 58f)
        canvas.drawRoundRect(splitsBarRect, 3f, 3f, splitsAccentBarPaint)

        paint.color = primaryTextColor
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("SPLITS E PARCIAIS (${splitSelectorMode.title.uppercase(ptBr)})", marginX + 48f, currentY + 53f, paint)

        // Cabeçalho da Tabela
        var tableY = currentY + 95f
        paint.color = secondaryTextColor
        paint.textSize = 21f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(if (splitSelectorMode == SplitSelectorMode.BY_BLOCK) "BLOCO" else "PARCIAL", marginX + 32f, tableY, paint)
        canvas.drawText("PACE", marginX + 260f, tableY, paint)
        canvas.drawText("RITMO", marginX + 420f, tableY, paint)
        canvas.drawText("TEMPO", marginX + contentWidth - 230f, tableY, paint)
        canvas.drawText("BPM", marginX + contentWidth - 90f, tableY, paint)

        tableY += 24f

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            strokeWidth = 2f
        }
        canvas.drawLine(marginX + 32f, tableY, marginX + contentWidth - 32f, tableY, linePaint)

        val basePace = effectivePaceSec.takeIf { it > 0 } ?: 450
        if (effectiveSplits.isEmpty()) {
            paint.color = secondaryTextColor
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Parciais indisponíveis", marginX + (contentWidth / 2f), tableY + 46f, paint)
            paint.textAlign = Paint.Align.LEFT
        } else effectiveSplits.forEach { split ->
            tableY += 56f

            val labelColor = when (split.phaseTag) {
                "PR" -> if (isLightReport) 0xFFD97706.toInt() else 0xFFFFD700.toInt()
                "RUN" -> if (isLightReport) 0xFF059669.toInt() else 0xFF00E676.toInt()
                "WALK" -> if (isLightReport) 0xFF0284C7.toInt() else 0xFF00B0FF.toInt()
                else -> primaryTextColor
            }
            paint.color = labelColor
            paint.textSize = 23f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            canvas.drawText(split.splitLabel, marginX + 32f, tableY, paint)

            val sMin = split.paceSecPerKm / 60
            val sSec = split.paceSecPerKm % 60
            val pText = String.format(Locale.US, "%d'%02d\"", sMin, sSec)
            paint.color = primaryTextColor
            canvas.drawText(pText, marginX + 260f, tableY, paint)

            val speedRatio = (basePace.toFloat() / split.paceSecPerKm.coerceAtLeast(180)).coerceIn(0.4f, 1.4f)
            val barW = 170f * (speedRatio / 1.4f)
            val splitBarRect = RectF(marginX + 420f, tableY - 14f, marginX + 420f + barW, tableY - 2f)
            val barColorInt = when (split.phaseTag) {
                "PR" -> if (isLightReport) 0xFFD97706.toInt() else 0xFFFFD700.toInt()
                "RUN" -> if (isLightReport) 0xFF10B981.toInt() else 0xFF00E676.toInt()
                "WALK" -> if (isLightReport) 0xFF0284C7.toInt() else 0xFF00B0FF.toInt()
                else -> if (speedRatio >= 1.05f) 0xFF00E676.toInt() else if (speedRatio >= 0.95f) accentColor else 0xFFF97316.toInt()
            }
            val splitBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = barColorInt
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(splitBarRect, 6f, 6f, splitBarPaint)

            val tM = split.durationSec / 60
            val tS = split.durationSec % 60
            val timeText = String.format(Locale.US, "%02d:%02d", tM, tS)
            paint.color = secondaryTextColor
            paint.textSize = 23f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(timeText, marginX + contentWidth - 230f, tableY, paint)

            val hrVal = split.avgHr?.toString() ?: "--"
            paint.color = primaryTextColor
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            canvas.drawText(hrVal, marginX + contentWidth - 90f, tableY, paint)
        }

        currentY += splitsBlockHeight + 32f

        // ========================================================
        // BLOCO F: EXIBIÇÃO DE EQUIPAMENTO (TÊNIS UTILIZADO + KM ACUMULADO DA GARAGEM)
        // ========================================================
        val rawShoeName = session.shoeName?.ifBlank { null } ?: "Olympikus Corre 4"
        val matchedShoe = runningShoes.firstOrNull { shoe ->
            shoe.id.equals(rawShoeName, ignoreCase = true) ||
            shoe.name.equals(rawShoeName, ignoreCase = true) ||
            shoe.fullDisplayName().equals(rawShoeName, ignoreCase = true) ||
            (shoe.name.length >= 3 && (rawShoeName.contains(shoe.name, ignoreCase = true) || shoe.name.contains(rawShoeName, ignoreCase = true)))
        }
        val shoeDisplayLine = if (matchedShoe != null) {
            String.format(Locale.US, "%s  •  %.1f / %.0f km acumulados", matchedShoe.fullDisplayName(), matchedShoe.totalDistanceKm, matchedShoe.maxDistanceKm)
        } else {
            rawShoeName
        }

        val shoeCardRect = RectF(marginX, currentY, marginX + contentWidth, currentY + 110f)
        canvas.drawRoundRect(shoeCardRect, 24f, 24f, cardPaint)
        canvas.drawRoundRect(shoeCardRect, 24f, 24f, borderPaint)

        paint.color = secondaryTextColor
        paint.textSize = 22f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        canvas.drawText("EQUIPAMENTO (TÊNIS UTILIZADO)", marginX + 32f, currentY + 42f, paint)

        paint.color = primaryTextColor
        paint.textSize = 28f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.ITALIC)
        paint.textSkewX = -0.14f
        canvas.drawText(shoeDisplayLine, marginX + 32f, currentY + 84f, paint)
        paint.textSkewX = 0f

        currentY += 150f

        // ========================================================
        // RODAPÉ MAX PERFORMANCE
        // ========================================================
        paint.color = secondaryTextColor
        paint.textSize = 24f
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val footerText = "MAX PERFORMANCE • Plataforma Inteligente de Corrida"
        val footerW = paint.measureText(footerText)
        canvas.drawText(footerText, (width - footerW) / 2f, currentY + 30f, paint)

        bitmap
    }

    fun saveReportBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "MaxPerformance_Corrida_${System.currentTimeMillis()}.png"
    ): Uri? {
        val resolver = context.contentResolver
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, AppExportPaths.PICTURES_RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, AppExportPaths.PUBLIC_FOLDER_NAME)
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val imageFile = File(appDir, fileName)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                var scannedUri: Uri? = null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/png")
                ) { _, uri ->
                    scannedUri = uri
                }
                scannedUri ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar bitmap na Galeria pública: ${e.message}", e)
            null
        }
    }

    suspend fun shareFullSessionReport(
        context: Context,
        session: RunningSession,
        isLightReport: Boolean,
        userProfile: UserProfileData?,
        gpsPoints: List<GpsTrackPoint>,
        recordedSplits: List<RecordedKmSplit>,
        splitSelectorMode: SplitSelectorMode = SplitSelectorMode.ONE_KM_MAIN,
        runningShoes: List<com.example.data.model.RunningShoe> = emptyList(),
        mapTileStyle: com.example.ui.components.MapTileStyle? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateFullSessionReportBitmap(
                context = context,
                session = session,
                isLightReport = isLightReport,
                userProfile = userProfile,
                gpsPoints = gpsPoints,
                recordedSplits = recordedSplits,
                splitSelectorMode = splitSelectorMode,
                runningShoes = runningShoes,
                mapTileStyle = mapTileStyle
            )

            val fileName = "MaxPerformance_Corrida_${System.currentTimeMillis()}.png"
            val galleryUri = saveReportBitmapToGallery(context, bitmap, fileName)

            val uriToShare: Uri = galleryUri ?: run {
                val cachePath = File(context.cacheDir, "reports")
                cachePath.mkdirs()
                val file = File(cachePath, fileName)
                FileOutputStream(file).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            withContext(Dispatchers.Main) {
                AudioSynth.playTickSound()
                Toast.makeText(context, "Imagem salva na Galeria na pasta ${AppExportPaths.PUBLIC_FOLDER_NAME}!", Toast.LENGTH_LONG).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uriToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório de Corrida"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erro ao exportar print longo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
