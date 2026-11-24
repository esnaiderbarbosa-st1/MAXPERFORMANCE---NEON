package com.example.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class TileCoord(val x: Int, val y: Int, val zoom: Int)

data class MapBounds(
    val minWorldX: Double,
    val maxWorldX: Double,
    val minWorldY: Double,
    val maxWorldY: Double
)

data class MapProjectionData(
    val bounds: MapBounds,
    val zoom: Int,
    val visibleTiles: List<TileCoord>,
    val fitScale: Float = 1f,
    val centerWorldX: Double = 0.0,
    val centerWorldY: Double = 0.0
)

object MapTileHelper {
    private const val TAG = "MapTileHelper"
    val tileBitmapCache = ConcurrentHashMap<String, Bitmap>()

    // Provedor único de Satélite com cobertura de alta resolução até zoom 19/20 no Brasil inteiro
    const val SATELLITE_URL = "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
    const val ARCGIS_SATELLITE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    const val OSM_URL = SATELLITE_URL
    const val VOYAGER_URL = SATELLITE_URL
    const val DARK_URL = SATELLITE_URL
    const val LIGHT_URL = SATELLITE_URL

    /**
     * Gera uma rota simulada apenas para sessões explicitamente de teste/simulação.
     */
    fun getAvenueRoutePoints(distanceMeters: Int): List<Pair<Double, Double>> {
        val startLat = -9.9554
        val startLon = -67.8652
        val endLat = -9.9648
        val endLon = -67.8535

        val totalDist = distanceMeters.coerceIn(1000, 25000)
        val baseSegDist = 1650.0
        val laps = (totalDist / baseSegDist).coerceAtLeast(0.5)

        val numPoints = 28
        val points = mutableListOf<Pair<Double, Double>>()

        for (i in 0..numPoints) {
            val progressNorm = i.toDouble() / numPoints.toDouble()
            val phase = (progressNorm * laps) % 2.0
            val t = if (phase <= 1.0) phase else 2.0 - phase
            val curve = sin(progressNorm * PI) * 0.00035

            val lat = startLat + (endLat - startLat) * t + curve
            val lon = startLon + (endLon - startLon) * t - (curve * 0.5)
            points.add(Pair(lat, lon))
        }
        return points
    }

    fun lonToWorldX(lon: Double, zoom: Int): Double {
        return ((lon + 180.0) / 360.0) * (256.0 * (1 shl zoom))
    }

    fun latToWorldY(lat: Double, zoom: Int): Double {
        val sinLat = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
        return (0.5 - (ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI))) * (256.0 * (1 shl zoom))
    }

    fun calculateProjection(
        points: List<Pair<Double, Double>>,
        widthPx: Float,
        heightPx: Float
    ): MapProjectionData {
        if (points.isEmpty()) {
            return MapProjectionData(
                bounds = MapBounds(0.0, widthPx.toDouble(), 0.0, heightPx.toDouble()),
                zoom = 15,
                visibleTiles = emptyList(),
                fitScale = 1f,
                centerWorldX = 0.0,
                centerWorldY = 0.0
            )
        }

        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }
        val maxLon = points.maxOf { it.second }

        var zoom = 18
        while (zoom > 10) {
            val minX = lonToWorldX(minLon, zoom)
            val maxX = lonToWorldX(maxLon, zoom)
            val minY = latToWorldY(maxLat, zoom)
            val maxY = latToWorldY(minLat, zoom)

            val spanX = abs(maxX - minX)
            val spanY = abs(maxY - minY)

            if (spanX <= widthPx * 0.84f && spanY <= heightPx * 0.84f) {
                break
            }
            zoom--
        }

        val minX = lonToWorldX(minLon, zoom)
        val maxX = lonToWorldX(maxLon, zoom)
        val minY = latToWorldY(maxLat, zoom)
        val maxY = latToWorldY(minLat, zoom)
        val spanX = abs(maxX - minX)
        val spanY = abs(maxY - minY)

        // Auto-BoundingBox justo: garante que a rota preencha ~82% do quadro, eliminando grandes bordas vazias
        val fitScale = if (spanX > 4.0 || spanY > 4.0) {
            val scaleX = if (spanX > 1.0) (widthPx * 0.82) / spanX else 2.6
            val scaleY = if (spanY > 1.0) (heightPx * 0.82) / spanY else 2.6
            minOf(scaleX, scaleY).toFloat().coerceIn(1.0f, 2.6f)
        } else {
            1.25f
        }

        val centerWorldX = (minX + maxX) / 2.0
        val centerWorldY = (minY + maxY) / 2.0

        val minWorldX = centerWorldX - (widthPx / 2.0)
        val maxWorldX = centerWorldX + (widthPx / 2.0)
        val minWorldY = centerWorldY - (heightPx / 2.0)
        val maxWorldY = centerWorldY + (heightPx / 2.0)

        val bounds = MapBounds(minWorldX, maxWorldX, minWorldY, maxWorldY)

        val halfWorldW = (widthPx / 2.0) / fitScale.coerceAtLeast(1f)
        val halfWorldH = (heightPx / 2.0) / fitScale.coerceAtLeast(1f)
        val startTileX = floor((centerWorldX - halfWorldW) / 256.0).toInt().coerceAtLeast(0)
        val endTileX = floor((centerWorldX + halfWorldW) / 256.0).toInt()
        val startTileY = floor((centerWorldY - halfWorldH) / 256.0).toInt().coerceAtLeast(0)
        val endTileY = floor((centerWorldY + halfWorldH) / 256.0).toInt()

        val visibleTiles = mutableListOf<TileCoord>()
        for (tx in startTileX..endTileX) {
            for (ty in startTileY..endTileY) {
                visibleTiles.add(TileCoord(tx, ty, zoom))
            }
        }

        return MapProjectionData(bounds, zoom, visibleTiles, fitScale, centerWorldX, centerWorldY)
    }

    /**
     * Detecta se um tile retornado por servidores como ArcGIS é o placeholder cinza
     * "Map data not yet available" (imagem monocromática cinza clara sem dados reais de satélite).
     */
    fun isPlaceholderNoDataTile(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 16 || h < 16) return true

        var grayCount = 0
        var totalSamples = 0
        val stepX = (w / 6).coerceAtLeast(1)
        val stepY = (h / 6).coerceAtLeast(1)

        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val maxCh = maxOf(r, g, b)
                val minCh = minOf(r, g, b)
                // No tile "Map data not yet available", quase todos os pixels são cinza chapado (R≈G≈B entre 180 e 225)
                if ((maxCh - minCh) <= 4 && r in 175..232) {
                    grayCount++
                }
                totalSamples++
                x += stepX
            }
            y += stepY
        }
        return totalSamples > 0 && (grayCount.toFloat() / totalSamples.toFloat()) >= 0.88f
    }

    private fun fetchRawBitmap(urlStr: String): Bitmap? {
        return runCatching {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                doInput = true
            }
            try {
                conn.connect()
                if (conn.responseCode == 200) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    conn.inputStream.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, options)
                    }
                } else {
                    null
                }
            } finally {
                runCatching { conn.disconnect() }
            }
        }.onFailure { e ->
            Log.w(TAG, "Falha ao baixar tile $urlStr: ${e.message}")
        }.getOrNull()
    }

    /**
     * Realiza o download de rede puro (com fallback Google Satellite -> ArcGIS Overzoom)
     * envolvido em blocos `runCatching` seguros.
     */
    fun fetchTileNetworkOnly(
        urlTemplate: String,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        isSatellite: Boolean = urlTemplate.contains("lyrs=s") || urlTemplate.contains("World_Imagery")
    ): Bitmap? = runCatching {
        val primaryUrl = urlTemplate
            .replace("{z}", zoom.toString())
            .replace("{x}", tileX.toString())
            .replace("{y}", tileY.toString())

        val primaryBmp = fetchRawBitmap(primaryUrl)
        if (primaryBmp != null && (!isSatellite || !isPlaceholderNoDataTile(primaryBmp))) {
            return@runCatching primaryBmp
        }

        if (isSatellite) {
            // Tenta Google Satellite direto caso o template passado tenha sido ArcGIS
            if (!primaryUrl.contains("google.com")) {
                val googleUrl = SATELLITE_URL
                    .replace("{z}", zoom.toString())
                    .replace("{x}", tileX.toString())
                    .replace("{y}", tileY.toString())
                val gBmp = fetchRawBitmap(googleUrl)
                if (gBmp != null && !isPlaceholderNoDataTile(gBmp)) {
                    return@runCatching gBmp
                }
            }

            // Fallback com Overzoom progressivo no ArcGIS World_Imagery (desce até zoom 13 se necessário e recorta o quadrante)
            for (fallbackZoom in zoom downTo 13) {
                val shift = zoom - fallbackZoom
                val parentX = tileX shr shift
                val parentY = tileY shr shift
                val arcUrl = ARCGIS_SATELLITE_URL
                    .replace("{z}", fallbackZoom.toString())
                    .replace("{x}", parentX.toString())
                    .replace("{y}", parentY.toString())
                val arcBmp = fetchRawBitmap(arcUrl)
                if (arcBmp != null && !isPlaceholderNoDataTile(arcBmp)) {
                    val finalBmp = if (shift == 0) {
                        arcBmp
                    } else {
                        runCatching {
                            val scaleFactor = 1 shl shift
                            val subX = tileX and (scaleFactor - 1)
                            val subY = tileY and (scaleFactor - 1)
                            val cropW = (arcBmp.width / scaleFactor).coerceAtLeast(1)
                            val cropH = (arcBmp.height / scaleFactor).coerceAtLeast(1)
                            val left = (subX * cropW).coerceIn(0, arcBmp.width - cropW)
                            val top = (subY * cropH).coerceIn(0, arcBmp.height - cropH)
                            val cropped = Bitmap.createBitmap(arcBmp, left, top, cropW, cropH)
                            Bitmap.createScaledBitmap(cropped, 256, 256, true)
                        }.getOrDefault(arcBmp)
                    }
                    return@runCatching finalBmp
                }
            }
        } else {
            val osmUrl = "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
            val osmBmp = fetchRawBitmap(osmUrl)
            if (osmBmp != null) {
                return@runCatching osmBmp
            }
            val hybridUrl = "https://mt1.google.com/vt/lyrs=y&x=$tileX&y=$tileY&z=$zoom"
            val hybridBmp = fetchRawBitmap(hybridUrl)
            if (hybridBmp != null) {
                return@runCatching hybridBmp
            }
        }

        primaryBmp
    }.getOrNull()

    /**
     * Baixa ou recupera do `TileCache` (Memória LruCache + Disco Local `maxrun_map_tiles`)
     * um tile de Satélite ou Mapa garantindo fallback automático e overzoom.
     */
    fun downloadTileForCoord(
        urlTemplate: String,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        isSatellite: Boolean = urlTemplate.contains("lyrs=s") || urlTemplate.contains("World_Imagery")
    ): Bitmap? {
        val provider = if (isSatellite) "satellite" else "map_${urlTemplate.hashCode()}"
        val cacheKey = TileCache.buildKey(provider, zoom, tileX, tileY)

        TileCache.getMemoryBitmap(cacheKey)?.let { bmp ->
            tileBitmapCache[cacheKey] = bmp
            return bmp
        }

        val cached = tileBitmapCache[cacheKey]
        if (cached != null && !cached.isRecycled) return cached

        val loaded = TileCache.getOrLoadTileBitmapBlocking(
            provider = provider,
            zoom = zoom,
            tileX = tileX,
            tileY = tileY
        ) {
            fetchTileNetworkOnly(
                urlTemplate = urlTemplate,
                zoom = zoom,
                tileX = tileX,
                tileY = tileY,
                isSatellite = isSatellite
            )
        }

        if (loaded != null && !loaded.isRecycled) {
            tileBitmapCache[cacheKey] = loaded
        }
        return loaded
    }

    fun downloadTileBitmap(urlStr: String): Bitmap? {
        val cached = tileBitmapCache[urlStr]
        if (cached != null && !cached.isRecycled) return cached

        val bmp = fetchRawBitmap(urlStr)
        if (bmp != null) {
            val isSat = urlStr.contains("lyrs=s") || urlStr.contains("World_Imagery")
            if (!isSat || !isPlaceholderNoDataTile(bmp)) {
                tileBitmapCache[urlStr] = bmp
                return bmp
            }
        }

        if (urlStr.contains("cartocdn.com")) {
            val fallbackOsmUrl = urlStr
                .replace("a.basemaps.cartocdn.com/rastertiles/voyager", "tile.openstreetmap.org")
                .replace("basemaps.cartocdn.com/rastertiles/voyager", "tile.openstreetmap.org")
                .replace("a.basemaps.cartocdn.com/dark_all", "tile.openstreetmap.org")
                .replace("basemaps.cartocdn.com/dark_all", "tile.openstreetmap.org")
                .replace("a.basemaps.cartocdn.com/light_all", "tile.openstreetmap.org")
                .replace("basemaps.cartocdn.com/light_all", "tile.openstreetmap.org")
            if (fallbackOsmUrl != urlStr) {
                return downloadTileBitmap(fallbackOsmUrl)
            }
        }
        return null
    }

    suspend fun fetchTiles(
        visibleTiles: List<TileCoord>,
        urlTemplate: String
    ): Map<TileCoord, Bitmap> = withContext(Dispatchers.IO) {
        val resultMap = ConcurrentHashMap<TileCoord, Bitmap>()
        val isSatellite = urlTemplate.contains("lyrs=s") || urlTemplate.contains("World_Imagery")
        val deferreds = visibleTiles.map { tile ->
            async {
                val bmp = downloadTileForCoord(
                    urlTemplate = urlTemplate,
                    zoom = tile.zoom,
                    tileX = tile.x,
                    tileY = tile.y,
                    isSatellite = isSatellite
                )
                if (bmp != null) {
                    resultMap[tile] = bmp
                }
            }
        }
        deferreds.awaitAll()
        resultMap
    }
}
