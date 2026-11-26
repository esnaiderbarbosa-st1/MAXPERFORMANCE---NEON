package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.RoutePointItem
import com.example.ui.components.MapTileStyle
import com.example.ui.components.RunRoutePhotoManager
import com.example.ui.components.RunRoutePhotoPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Exportador de Vídeo 3D do Percurso (`Route3dVideoExporter`) em formato MP4 vertical (720x1280 @ 30fps)
 * pronto para Reels, Stories e Status do WhatsApp.
 *
 * Renderiza:
 * - Mapa real (tiles cacheados) + malha tática 3D com câmera inclinada acompanhando o corredor
 * - Linha neon do percurso (colorida por ritmo/sprint ou ciano/verde neon)
 * - Bandeiras de Largada, Km 1, Km 2... e Chegada
 * - Fotos do percurso 100% em pé (corrigidas via EXIF) abrindo em pop-up nos seus respectivos KMs
 * - Placar ao vivo de Distância (0,00 km -> final), Pace e Frequência Cardíaca.
 */
object Route3dVideoExporter {
    private const val TAG = "Route3dVideoExporter"
    private const val VIDEO_WIDTH = 720
    private const val VIDEO_HEIGHT = 1280
    private const val FPS = 30
    private const val BIT_RATE = 4_500_000

    private data class ExportTimelineFrame(
        val progress: Float,
        val activePhotoPin: RunRoutePhotoPin?,
        val popupScale: Float
    )

    suspend fun exportAndShareRoute3dVideo(
        context: Context,
        sessionId: String,
        sessionTitle: String,
        cityStateLabel: String,
        sessionEndTimestampMs: Long,
        distanceMeters: Int,
        avgPaceSecPerKm: Int,
        avgHrBpm: Int,
        hrTimelineList: List<Int>,
        routePoints: List<Pair<Double, Double>>,
        routePointItems: List<RoutePointItem>,
        mapStyle: MapTileStyle,
        colorByPace: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            withContext(Dispatchers.Main) { onProgress(0.02f) }

            val effectiveItems: List<RoutePointItem> = when {
                routePointItems.size >= 2 -> routePointItems
                routePoints.size >= 2 -> routePoints.mapIndexed { idx, pair ->
                    RoutePointItem(
                        lat = pair.first,
                        lon = pair.second,
                        speed = if (avgPaceSecPerKm > 0) (1000f / avgPaceSecPerKm) else 2.85f,
                        phase = "RUN",
                        timestamp = idx * 1000L
                    )
                }
                else -> {
                    val baseLat = routePoints.firstOrNull()?.first ?: -9.97499
                    val baseLon = routePoints.firstOrNull()?.second ?: -67.8243
                    List(60) { i ->
                        val angle = (i.toDouble() / 60.0) * 2.0 * PI
                        RoutePointItem(
                            lat = baseLat + 0.0028 * sin(angle),
                            lon = baseLon + 0.0032 * cos(angle),
                            speed = 2.9f,
                            phase = "RUN",
                            timestamp = i * 1000L
                        )
                    }
                }
            }

            val pts = effectiveItems.map { Pair(it.lat, it.lon) }
            val sortedPhotos = RunRoutePhotoManager.loadPhotosForSession(context, sessionId)
                .sortedBy { it.progress }

            // Pré-carrega fotos do percurso já corrigidas pelo EXIF (100% em pé)
            val photoBitmaps = mutableMapOf<String, Bitmap>()
            var cachedTileBitmaps: Map<TileCoord, Bitmap> = emptyMap()
            var encoder: MediaCodec? = null
            var inputSurface: android.view.Surface? = null
            var muxer: MediaMuxer? = null
            var muxerStarted = false

            val outDir = File(context.cacheDir, "shared_videos").apply { if (!exists()) mkdirs() }
            val outFile = File(outDir, "MAX_RUN_3D_${System.currentTimeMillis()}.mp4")

            try {
                for (pin in sortedPhotos) {
                    RunRoutePhotoManager.decodeUprightBitmap(context, pin.uriString, 600)?.let { bmp ->
                        photoBitmaps[pin.id] = bmp
                    }
                }

                // Monta a linha do tempo de quadros (30 fps): ~7s de voo + 1.2s em cada foto + 0.9s final
                val timeline = buildTimelineFrames(sortedPhotos)
                val totalFrames = timeline.size.coerceAtLeast(1)

                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder = codec
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = codec.createInputSurface()
                inputSurface = surface
                codec.start()

                val mediaMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = mediaMuxer
                val bufferInfo = MediaCodec.BufferInfo()
                var trackIndex = -1

                // Projeção Mercator base para o quadro 720x1280
                val mapProjection = computeProjection(pts, VIDEO_WIDTH.toFloat(), VIDEO_HEIGHT.toFloat())
                cachedTileBitmaps = loadCachedTiles(context, mapStyle, mapProjection.visibleTiles)

                fun drainEncoder(endOfStream: Boolean) {
                    if (endOfStream) {
                        runCatching { codec.signalEndOfInputStream() }
                    }
                    while (true) {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                if (!endOfStream) break
                            }
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!muxerStarted) {
                                    trackIndex = mediaMuxer.addTrack(codec.outputFormat)
                                    mediaMuxer.start()
                                    muxerStarted = true
                                }
                            }
                            outIndex >= 0 -> {
                                val encodedData = codec.getOutputBuffer(outIndex)
                                if (encodedData != null) {
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                        bufferInfo.size = 0
                                    }
                                    if (bufferInfo.size != 0 && muxerStarted) {
                                        encodedData.position(bufferInfo.offset)
                                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                        mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                                    }
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    break
                                }
                            }
                        }
                    }
                }

                for ((frameIdx, frameSpec) in timeline.withIndex()) {
                    val canvas = surface.lockCanvas(null)
                    try {
                        renderFrameToCanvas(
                            canvas = canvas,
                            frameSpec = frameSpec,
                            effectiveItems = effectiveItems,
                            pts = pts,
                            projection = mapProjection,
                            cachedTiles = cachedTileBitmaps,
                            sortedPhotos = sortedPhotos,
                            photoBitmaps = photoBitmaps,
                            sessionTitle = sessionTitle,
                            cityStateLabel = cityStateLabel,
                            sessionEndTimestampMs = sessionEndTimestampMs,
                            distanceMeters = distanceMeters,
                            avgPaceSecPerKm = avgPaceSecPerKm,
                            avgHrBpm = avgHrBpm,
                            hrTimelineList = hrTimelineList,
                            colorByPace = colorByPace
                        )
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(endOfStream = false)

                    if (frameIdx % 6 == 0 || frameIdx == totalFrames - 1) {
                        val ratio = ((frameIdx + 1).toFloat() / totalFrames.toFloat()).coerceIn(0.05f, 0.95f)
                        withContext(Dispatchers.Main) { onProgress(ratio) }
                    }
                }

                drainEncoder(endOfStream = true)
            } finally {
                runCatching { encoder?.stop() }
                runCatching { encoder?.release() }
                runCatching { inputSurface?.release() }
                if (muxerStarted) {
                    runCatching { muxer?.stop() }
                }
                runCatching { muxer?.release() }
                photoBitmaps.values.forEach { bmp ->
                    runCatching { if (!bmp.isRecycled) bmp.recycle() }
                }
                cachedTileBitmaps.values.forEach { bmp ->
                    runCatching { if (!bmp.isRecycled) bmp.recycle() }
                }
            }

            // Salva cópia pública na Galeria (Movies/MAXPERFORMANCE)
            val savedGalleryUri = saveVideoToGallery(context, outFile)

            withContext(Dispatchers.Main) {
                onProgress(1.0f)
                Toast.makeText(
                    context,
                    "🎬 Vídeo 3D salvo na Galeria (${AppExportPaths.MOVIES_RELATIVE_PATH})!",
                    Toast.LENGTH_LONG
                ).show()
                shareVideoFile(context, outFile, savedGalleryUri, sessionTitle)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar vídeo 3D do percurso", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Não foi possível gerar o vídeo 3D: ${e.localizedMessage ?: "erro interno"}",
                    Toast.LENGTH_LONG
                ).show()
            }
            false
        }
    }

    private fun buildTimelineFrames(sortedPhotos: List<RunRoutePhotoPin>): List<ExportTimelineFrame> {
        val frames = mutableListOf<ExportTimelineFrame>()
        val baseFlyFrames = 7 * FPS // 7 segundos de voo fluido
        val photoPauseFrames = (1.25f * FPS).roundToInt() // 1.25s exibindo cada foto
        val outroFrames = (1.0f * FPS).roundToInt()

        var lastProg = 0f
        for (pin in sortedPhotos) {
            val stopProg = pin.progress.coerceIn(0.05f, 0.95f)
            if (stopProg > lastProg + 0.01f) {
                val segFrames = ((stopProg - lastProg) * baseFlyFrames).roundToInt().coerceAtLeast(10)
                for (i in 0 until segFrames) {
                    val t = i.toFloat() / segFrames.toFloat()
                    frames.add(
                        ExportTimelineFrame(
                            progress = lastProg + (stopProg - lastProg) * t,
                            activePhotoPin = null,
                            popupScale = 0f
                        )
                    )
                }
                lastProg = stopProg
            }
            for (p in 0 until photoPauseFrames) {
                val scale = when {
                    p < 6 -> (p + 1) / 6f
                    p > photoPauseFrames - 6 -> ((photoPauseFrames - p) / 6f).coerceIn(0f, 1f)
                    else -> 1f
                }
                frames.add(
                    ExportTimelineFrame(
                        progress = lastProg,
                        activePhotoPin = pin,
                        popupScale = scale
                    )
                )
            }
        }

        if (lastProg < 1f) {
            val remFrames = ((1f - lastProg) * baseFlyFrames).roundToInt().coerceAtLeast(15)
            for (i in 0..remFrames) {
                val t = i.toFloat() / remFrames.toFloat()
                frames.add(
                    ExportTimelineFrame(
                        progress = (lastProg + (1f - lastProg) * t).coerceIn(0f, 1f),
                        activePhotoPin = null,
                        popupScale = 0f
                    )
                )
            }
        }

        for (i in 0 until outroFrames) {
            frames.add(ExportTimelineFrame(progress = 1f, activePhotoPin = null, popupScale = 0f))
        }
        return frames
    }

    private data class TileCoord(val x: Int, val y: Int, val z: Int)
    private data class ExportProjection(
        val minWorldX: Double,
        val maxWorldX: Double,
        val minWorldY: Double,
        val maxWorldY: Double,
        val zoom: Int,
        val fitScale: Float,
        val visibleTiles: List<TileCoord>
    )

    private fun lonToWorldX(lon: Double, zoom: Int): Double {
        val mapSize = 256.0 * (1 shl zoom)
        return ((lon + 180.0) / 360.0) * mapSize
    }

    private fun latToWorldY(lat: Double, zoom: Int): Double {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clampedLat * PI / 180.0
        val mapSize = 256.0 * (1 shl zoom)
        return (0.5 - ln(tan(PI / 4.0 + rad / 2.0)) / (2.0 * PI)) * mapSize
    }

    private fun computeProjection(
        points: List<Pair<Double, Double>>,
        widthPx: Float,
        heightPx: Float
    ): ExportProjection {
        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }
        val maxLon = points.maxOf { it.second }

        var zoom = 17
        while (zoom > 11) {
            val spanX = abs(lonToWorldX(maxLon, zoom) - lonToWorldX(minLon, zoom))
            val spanY = abs(latToWorldY(minLat, zoom) - latToWorldY(maxLat, zoom))
            if (spanX <= widthPx * 0.72f && spanY <= heightPx * 0.56f) break
            zoom--
        }

        val minX = lonToWorldX(minLon, zoom)
        val maxX = lonToWorldX(maxLon, zoom)
        val minY = latToWorldY(maxLat, zoom)
        val maxY = latToWorldY(minLat, zoom)

        val spanX = max(12.0, maxX - minX)
        val spanY = max(12.0, maxY - minY)
        val fitScale = min((widthPx * 0.72) / spanX, (heightPx * 0.54) / spanY).toFloat().coerceIn(0.6f, 6.0f)

        val centerWorldX = (minX + maxX) / 2.0
        val centerWorldY = (minY + maxY) / 2.0
        val halfW = (widthPx / fitScale) * 0.9
        val halfH = (heightPx / fitScale) * 0.9

        val maxTileIdx = (1 shl zoom) - 1
        val startTileX = floor((centerWorldX - halfW) / 256.0).toInt().coerceIn(0, maxTileIdx)
        val endTileX = floor((centerWorldX + halfW) / 256.0).toInt().coerceIn(0, maxTileIdx)
        val startTileY = floor((centerWorldY - halfH) / 256.0).toInt().coerceIn(0, maxTileIdx)
        val endTileY = floor((centerWorldY + halfH) / 256.0).toInt().coerceIn(0, maxTileIdx)

        val tiles = mutableListOf<TileCoord>()
        for (tx in startTileX..endTileX) {
            for (ty in startTileY..endTileY) {
                if (tiles.size < 64) {
                    tiles.add(TileCoord(tx, ty, zoom))
                }
            }
        }

        return ExportProjection(minX, maxX, minY, maxY, zoom, fitScale, tiles)
    }

    private fun loadCachedTiles(
        context: Context,
        style: MapTileStyle,
        coords: List<TileCoord>
    ): Map<TileCoord, Bitmap> {
        val result = mutableMapOf<TileCoord, Bitmap>()
        val cacheDir = File(context.cacheDir, "map_tiles_cache_v3")
        if (!cacheDir.exists()) return result
        val prefix = style.name.lowercase(Locale.US)
        for (tc in coords) {
            val f = File(cacheDir, "${prefix}_${tc.z}_${tc.x}_${tc.y}.tile")
            if (f.exists() && f.length() > 100L) {
                runCatching {
                    FileInputStream(f).use { fis ->
                        BitmapFactory.decodeStream(fis)?.let { bmp ->
                            result[tc] = bmp
                        }
                    }
                }
            }
        }
        return result
    }

    private fun renderFrameToCanvas(
        canvas: Canvas,
        frameSpec: ExportTimelineFrame,
        effectiveItems: List<RoutePointItem>,
        pts: List<Pair<Double, Double>>,
        projection: ExportProjection,
        cachedTiles: Map<TileCoord, Bitmap>,
        sortedPhotos: List<RunRoutePhotoPin>,
        photoBitmaps: Map<String, Bitmap>,
        sessionTitle: String,
        cityStateLabel: String,
        sessionEndTimestampMs: Long,
        distanceMeters: Int,
        avgPaceSecPerKm: Int,
        avgHrBpm: Int,
        hrTimelineList: List<Int>,
        colorByPace: Boolean
    ) {
        val w = VIDEO_WIDTH.toFloat()
        val h = VIDEO_HEIGHT.toFloat()
        val progress = frameSpec.progress.coerceIn(0f, 1f)
        val ptBr = Locale.forLanguageTag("pt-BR")

        // 1. Fundo escuro profundo com gradiente tático
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFF070A10.toInt(), 0xFF0D131F.toInt(), 0xFF070A10.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Converte pontos GPS para coordenadas de tela base
        val centerWorldX = (projection.minWorldX + projection.maxWorldX) / 2.0
        val centerWorldY = (projection.minWorldY + projection.maxWorldY) / 2.0
        val screenPts = pts.map { (lat, lon) ->
            val wx = lonToWorldX(lon, projection.zoom)
            val wy = latToWorldY(lat, projection.zoom)
            val sx = (w / 2f) + ((wx - centerWorldX) * projection.fitScale).toFloat()
            val sy = (h / 2f) + ((wy - centerWorldY) * projection.fitScale).toFloat()
            Pair(sx, sy)
        }

        // Posição atual da ponta ativa
        val exactIdx = (screenPts.size - 1) * progress
        val iLow = floor(exactIdx).toInt().coerceIn(0, (screenPts.size - 1).coerceAtLeast(0))
        val iHigh = (iLow + 1).coerceIn(0, (screenPts.size - 1).coerceAtLeast(0))
        val frac = exactIdx - iLow
        val headX = screenPts[iLow].first + (screenPts[iHigh].first - screenPts[iLow].first) * frac
        val headY = screenPts[iLow].second + (screenPts[iHigh].second - screenPts[iLow].second) * frac

        // Cálculo da câmera 3D Cine Flyover (inclinada 46° durante o voo e fazendo zoom out suave no final)
        val endBlend = ((progress - 0.88f) / 0.12f).coerceIn(0f, 1f)
        val cineScale = 1.95f * (1f - endBlend) + 1.0f * endBlend
        val pitchDeg = 46f * (1f - endBlend)
        val camOffsetX = ((w / 2f) - headX) * (1f - endBlend)
        val camOffsetY = ((h * 0.54f) - headY) * (1f - endBlend)

        canvas.save()
        val camera3d = Camera()
        val matrix3d = Matrix()
        camera3d.save()
        camera3d.rotateX(pitchDeg)
        camera3d.getMatrix(matrix3d)
        camera3d.restore()
        matrix3d.preTranslate(-w / 2f, -h / 2f)
        matrix3d.postTranslate(w / 2f, h / 2f)
        canvas.concat(matrix3d)

        canvas.translate(w / 2f, h / 2f)
        canvas.scale(cineScale, cineScale)
        canvas.translate(-w / 2f + camOffsetX, -h / 2f + camOffsetY)

        // 2. Desenha tiles reais cacheados (se disponíveis) + grade tática 3D
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 205
        }
        for ((tc, bmp) in cachedTiles) {
            val tileWorldX = tc.x * 256.0
            val tileWorldY = tc.y * 256.0
            val left = (w / 2f) + ((tileWorldX - centerWorldX) * projection.fitScale).toFloat()
            val top = (h / 2f) + ((tileWorldY - centerWorldY) * projection.fitScale).toFloat()
            val sizePx = (256.0 * projection.fitScale).toFloat()
            canvas.drawBitmap(bmp, null, RectF(left, top, left + sizePx + 0.8f, top + sizePx + 0.8f), tilePaint)
        }

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2200E5FF
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        val step = 90f
        var gx = -w
        while (gx <= w * 2f) {
            canvas.drawLine(gx, -h, gx, h * 2f, gridPaint)
            gx += step
        }
        var gy = -h
        while (gy <= h * 2f) {
            canvas.drawLine(-w, gy, w * 2f, gy, gridPaint)
            gy += step
        }

        // 3. Traçado fantasma de fundo (rota completa sutil)
        if (screenPts.size >= 2) {
            val fullPath = Path().apply {
                moveTo(screenPts[0].first, screenPts[0].second)
                for (k in 1 until screenPts.size) {
                    lineTo(screenPts[k].first, screenPts[k].second)
                }
            }
            val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3394A3B8
                strokeWidth = 6f / cineScale.coerceAtLeast(1f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(fullPath, ghostPaint)

            // 4. Traçado Neon ativo até `progress`
            val activePath = Path().apply {
                moveTo(screenPts[0].first, screenPts[0].second)
                for (k in 1..iLow) {
                    lineTo(screenPts[k].first, screenPts[k].second)
                }
                lineTo(headX, headY)
            }
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (colorByPace) 0x5500E676 else 0x5500E5FF
                strokeWidth = 18f / cineScale.coerceAtLeast(1f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (colorByPace) 0xFF00E676.toInt() else 0xFF00E5FF.toInt()
                strokeWidth = 7.5f / cineScale.coerceAtLeast(1f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(activePath, glowPaint)
            canvas.drawPath(activePath, corePaint)

            val mScale = (1f / cineScale).coerceIn(0.52f, 1.0f)

            // Marcador de Largada
            val startPt = screenPts.first()
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            dotPaint.color = Color.WHITE
            canvas.drawCircle(startPt.first, startPt.second, 10f * mScale, dotPaint)
            dotPaint.color = 0xFF00E676.toInt()
            canvas.drawCircle(startPt.first, startPt.second, 7f * mScale, dotPaint)

            // Marcos de Km
            val totalKmFloat = (distanceMeters.coerceAtLeast(100) / 1000f)
            val fullKms = floor(totalKmFloat).toInt().coerceIn(0, 42)
            for (km in 1..fullKms) {
                val kmProg = (km.toFloat() / totalKmFloat).coerceIn(0.05f, 0.94f)
                if (progress >= kmProg) {
                    val idxKm = ((screenPts.size - 1) * kmProg).roundToInt().coerceIn(0, screenPts.lastIndex)
                    val kp = screenPts[idxKm]
                    dotPaint.color = Color.WHITE
                    canvas.drawCircle(kp.first, kp.second, 7.5f * mScale, dotPaint)
                    dotPaint.color = 0xFF00E5FF.toInt()
                    canvas.drawCircle(kp.first, kp.second, 5f * mScale, dotPaint)
                }
            }

            // Pins de Fotos no mapa
            for (pin in sortedPhotos) {
                val idxPin = ((screenPts.size - 1) * pin.progress).roundToInt().coerceIn(0, screenPts.lastIndex)
                val pp = screenPts[idxPin]
                dotPaint.color = 0x66F59E0B
                canvas.drawCircle(pp.first, pp.second, 18f * mScale, dotPaint)
                dotPaint.color = Color.WHITE
                canvas.drawCircle(pp.first, pp.second, 11f * mScale, dotPaint)
                dotPaint.color = 0xFFF59E0B.toInt()
                canvas.drawCircle(pp.first, pp.second, 8.5f * mScale, dotPaint)
            }

            // Ponta luminosa do corredor
            dotPaint.color = 0x5500E5FF
            canvas.drawCircle(headX, headY, 26f * mScale, dotPaint)
            dotPaint.color = Color.WHITE
            canvas.drawCircle(headX, headY, 10f * mScale, dotPaint)
            dotPaint.color = 0xFF00E5FF.toInt()
            canvas.drawCircle(headX, headY, 6.5f * mScale, dotPaint)
        }

        canvas.restore()

        // ==================== OVERLAYS 2D (CABEÇALHO, POPUP DE FOTO E PLACAR INFERIOR) ====================
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE60F172A.toInt()
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8800E5FF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // 1. Cabeçalho Superior
        val topRect = RectF(32f, 48f, w - 32f, 152f)
        canvas.drawRoundRect(topRect, 24f, 24f, cardPaint)
        canvas.drawRoundRect(topRect, 24f, 24f, borderPaint)

        val headerTitle = if (sessionTitle.isNotBlank()) "📍 $cityStateLabel • $sessionTitle" else "📍 $cityStateLabel"
        canvas.drawText(headerTitle.take(40), 56f, 94f, titlePaint)

        val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", ptBr)
        val dateStr = dateFmt.format(Date(sessionEndTimestampMs)).replaceFirstChar { it.uppercase(ptBr) }
        canvas.drawText(dateStr, 56f, 128f, subPaint)

        // Badge "MAX RUN 3D"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("🎥 MAX RUN 3D", w - 54f, 94f, badgePaint)

        // 2. Pop-up de Foto Georreferenciada (100% em pé via EXIF) quando o voo passa pelo pin
        val activePin = frameSpec.activePhotoPin
        if (activePin != null && frameSpec.popupScale > 0.05f) {
            val bmp = photoBitmaps[activePin.id]
            if (bmp != null && !bmp.isRecycled) {
                canvas.save()
                val cx = w / 2f
                val cy = h * 0.46f
                canvas.scale(frameSpec.popupScale, frameSpec.popupScale, cx, cy)

                val cardW = 390f
                val cardH = 460f
                val pRect = RectF(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f)
                val popupBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xF50D1117.toInt()
                    style = Paint.Style.FILL
                }
                val popupBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFF59E0B.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                }
                canvas.drawRoundRect(pRect, 32f, 32f, popupBg)
                canvas.drawRoundRect(pRect, 32f, 32f, popupBorder)

                val pinHeader = String.format(ptBr, "📸 Km %.1f • %s", activePin.distanceKmAtPoint, activePin.paceLabelAtPoint)
                val pinTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(pinHeader, cx, pRect.top + 44f, pinTitlePaint)

                // Desenha a foto com Center-Crop dentro do quadro
                val imgRect = RectF(pRect.left + 22f, pRect.top + 64f, pRect.right - 22f, pRect.bottom - 22f)
                val saveClip = canvas.save()
                val clipPath = Path().apply { addRoundRect(imgRect, 22f, 22f, Path.Direction.CW) }
                canvas.clipPath(clipPath)

                val scale = max(imgRect.width() / bmp.width.toFloat(), imgRect.height() / bmp.height.toFloat())
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val dx = imgRect.centerX() - dw / 2f
                val dy = imgRect.centerY() - dh / 2f
                canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                canvas.restoreToCount(saveClip)

                canvas.restore()
            }
        }

        // 3. Placar Inferior Ao Vivo (Distância 0,00 -> Final | Ritmo | Cardíaco)
        val liveKm = ((distanceMeters.coerceAtLeast(100) / 1000f) * progress).coerceAtLeast(0f)
        val liveIdx = ((effectiveItems.size - 1) * progress).roundToInt().coerceIn(0, (effectiveItems.size - 1).coerceAtLeast(0))
        val curItem = effectiveItems.getOrNull(liveIdx)
        val spd = curItem?.speed?.takeIf { it > 0.45f }
        val paceSec = if (spd != null) (1000f / spd).roundToInt().coerceIn(160, 960) else (avgPaceSecPerKm.takeIf { it > 0 } ?: 360)
        val livePaceStr = String.format(Locale.US, "%d'%02d\"/km", paceSec / 60, paceSec % 60)
        val liveBpm = when {
            hrTimelineList.isNotEmpty() -> {
                val hIdx = ((hrTimelineList.size - 1) * progress).roundToInt().coerceIn(0, hrTimelineList.lastIndex)
                hrTimelineList[hIdx]
            }
            avgHrBpm > 0 -> avgHrBpm
            else -> 158
        }

        val botRect = RectF(32f, h - 196f, w - 32f, h - 56f)
        canvas.drawRoundRect(botRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(botRect, 28f, 28f, borderPaint)

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }

        val col1X = botRect.left + botRect.width() * 0.18f
        val col2X = botRect.left + botRect.width() * 0.50f
        val col3X = botRect.left + botRect.width() * 0.82f

        valPaint.color = Color.WHITE
        canvas.drawText(String.format(ptBr, "%.2f km", liveKm), col1X, botRect.top + 66f, valPaint)
        canvas.drawText("Distância", col1X, botRect.top + 104f, lblPaint)

        valPaint.color = 0xFF00E676.toInt()
        canvas.drawText(livePaceStr, col2X, botRect.top + 66f, valPaint)
        canvas.drawText("Ritmo", col2X, botRect.top + 104f, lblPaint)

        valPaint.color = 0xFFFFD700.toInt()
        canvas.drawText("$liveBpm bpm", col3X, botRect.top + 66f, valPaint)
        canvas.drawText("Cardíaco", col3X, botRect.top + 104f, lblPaint)
    }

    private fun saveVideoToGallery(context: Context, videoFile: File): Uri? {
        return runCatching {
            val fileName = "MAX_RUN_3D_${System.currentTimeMillis()}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, AppExportPaths.MOVIES_RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }

    private fun shareVideoFile(
        context: Context,
        videoFile: File,
        galleryUri: Uri?,
        sessionTitle: String
    ) {
        runCatching {
            val shareUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
            }.getOrNull() ?: galleryUri ?: return

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (sessionTitle.isNotBlank()) "MAX RUN 3D • $sessionTitle" else "MAX RUN 3D • Replay do Percurso"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Compartilhar Vídeo 3D do Percurso").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
