package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * =========================================================================
 * MAX RUN — TILE CACHE (2-LAYER MEMORY + DISK LRU CACHE)
 *
 * Camada 1 (RAM):
 * - `LruCache<String, Bitmap>` limitada a 24 MB (24 * 1024 KB)
 * - `LruCache<String, ImageBitmap>` limitada a 120 tiles prontos para o Canvas
 * - Indexada pela chave única "{provider}_{z}_{x}_{y}"
 *
 * Camada 2 (Disco Local):
 * - Diretório `context.cacheDir/maxrun_map_tiles`
 * - Estratégia Cache-First com Network Fallback
 * - TTL de 30 dias (2_592_000_000L ms)
 * - Controle de espaço automático em background: Max 50 MB -> poda LRU até < 40 MB
 * - Deduplicação de requisições concorrentes via `inFlightRequests`
 * =========================================================================
 */
object TileCache {
    private const val TAG = "TileCache"

    const val TILE_CACHE_DIR_NAME = "maxrun_map_tiles"
    const val TILE_TTL_MS = 2_592_000_000L // 30 dias em milissegundos
    const val MAX_DISK_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB
    const val TARGET_DISK_CACHE_BYTES = 40L * 1024L * 1024L // 40 MB após limpeza
    private const val MAX_MEMORY_CACHE_KB = 24 * 1024 // 24 MB em KB
    private const val MAX_IMAGE_BITMAP_COUNT = 120

    @Volatile
    private var diskCacheDir: File? = null

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isDiskCleanupRunning = AtomicBoolean(false)
    private val writeCounterSinceLastCheck = AtomicInteger(0)

    // Camada 1A: LruCache de Bitmaps dimensionada em KB (máx 24 MB)
    private val memoryBitmapCache = object : LruCache<String, Bitmap>(MAX_MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    // Camada 1B: LruCache de ImageBitmaps prontos para o Canvas do Compose (máx 120 tiles)
    private val memoryImageBitmapCache = object : LruCache<String, ImageBitmap>(MAX_IMAGE_BITMAP_COUNT) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    // Controle de requisições simultâneas para evitar downloads duplicados do mesmo tile (z, x, y)
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    /**
     * Inicializa o diretório de cache em disco (`context.cacheDir/maxrun_map_tiles`).
     * Seguro para ser chamado múltiplas vezes.
     */
    fun init(context: Context?) {
        if (context == null || diskCacheDir != null) return
        runCatching {
            val dir = File(context.applicationContext.cacheDir, TILE_CACHE_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            diskCacheDir = dir
            triggerDiskMaintenanceIfNeeded(force = true)
        }.onFailure { e ->
            Log.w(TAG, "Falha ao inicializar diretório TileCache: ${e.message}")
        }
    }

    /**
     * Constrói a chave padronizada "{provider}_{z}_{x}_{y}".
     */
    fun buildKey(provider: String, zoom: Int, tileX: Int, tileY: Int): String {
        val cleanProvider = provider.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")
        return "${cleanProvider}_${zoom}_${tileX}_${tileY}"
    }

    /**
     * Consulta síncrona (Thread-Safe) na Camada 1 (RAM) retornando `ImageBitmap` imediatamente
     * para o `Canvas` sem recomposição assíncrona ou piscada.
     */
    fun getMemoryImageBitmap(key: String): ImageBitmap? {
        return runCatching {
            synchronized(memoryImageBitmapCache) {
                memoryImageBitmapCache.get(key)
            } ?: run {
                val bmp = getMemoryBitmap(key)
                if (bmp != null && !bmp.isRecycled) {
                    val img = bmp.asImageBitmap()
                    synchronized(memoryImageBitmapCache) {
                        memoryImageBitmapCache.put(key, img)
                    }
                    img
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /**
     * Consulta síncrona (Thread-Safe) na Camada 1 (RAM) retornando `Bitmap`.
     */
    fun getMemoryBitmap(key: String): Bitmap? {
        return runCatching {
            val bmp = synchronized(memoryBitmapCache) {
                memoryBitmapCache.get(key)
            }
            if (bmp != null && !bmp.isRecycled) bmp else null
        }.getOrNull()
    }

    /**
     * Armazena o Bitmap nas caches de memória RAM (`Bitmap` + `ImageBitmap`).
     */
    private fun putInMemory(key: String, bitmap: Bitmap) {
        runCatching {
            if (!bitmap.isRecycled) {
                synchronized(memoryBitmapCache) {
                    memoryBitmapCache.put(key, bitmap)
                }
                val imgBitmap = bitmap.asImageBitmap()
                synchronized(memoryImageBitmapCache) {
                    memoryImageBitmapCache.put(key, imgBitmap)
                }
            }
        }
    }

    /**
     * Busca um tile seguindo a estratégia Cache-First (Memória -> Disco -> Rede)
     * com proteção contra chamadas concorrentes (`inFlightRequests`) e blocos `runCatching`.
     */
    suspend fun getOrLoadTileBitmap(
        context: Context?,
        provider: String,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        networkFetcher: () -> Bitmap?
    ): Bitmap? {
        init(context)
        val key = buildKey(provider, zoom, tileX, tileY)

        // 1. Camada 1: Memória RAM (Retorno imediato)
        getMemoryBitmap(key)?.let { return it }

        // 2. Deduplicação de requisições simultâneas para a mesma chave
        val existingDeferred = inFlightRequests[key]
        if (existingDeferred != null) {
            return runCatching { existingDeferred.await() }.getOrNull()
        }

        val newDeferred = CompletableDeferred<Bitmap?>()
        val prevDeferred = inFlightRequests.putIfAbsent(key, newDeferred)
        if (prevDeferred != null) {
            return runCatching { prevDeferred.await() }.getOrNull()
        }

        return try {
            withContext(Dispatchers.IO) {
                // Re-checa memória após entrar no contexto IO
                getMemoryBitmap(key)?.let { cachedBmp ->
                    newDeferred.complete(cachedBmp)
                    return@withContext cachedBmp
                }

                // 3. Camada 2: Disco Local (TTL de 30 dias)
                val diskBmp = readTileFromDisk(key)
                if (diskBmp != null) {
                    putInMemory(key, diskBmp)
                    newDeferred.complete(diskBmp)
                    return@withContext diskBmp
                }

                // 4. Fallback de Rede
                val fetchedBmp = runCatching { networkFetcher() }.getOrNull()
                if (fetchedBmp != null && !fetchedBmp.isRecycled) {
                    putInMemory(key, fetchedBmp)
                    writeTileToDiskSafely(key, fetchedBmp)
                    newDeferred.complete(fetchedBmp)
                    return@withContext fetchedBmp
                }

                newDeferred.complete(null)
                null
            }
        } catch (t: Throwable) {
            newDeferred.complete(null)
            null
        } finally {
            inFlightRequests.remove(key, newDeferred)
        }
    }

    /**
     * Versão bloqueante segura para chamadores que já estão em `Dispatchers.IO`
     * (como `MapTileHelper.downloadTileForCoord`).
     */
    fun getOrLoadTileBitmapBlocking(
        provider: String,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        networkFetcher: () -> Bitmap?
    ): Bitmap? {
        val key = buildKey(provider, zoom, tileX, tileY)

        // 1. Camada 1: Memória RAM
        getMemoryBitmap(key)?.let { return it }

        // 2. Camada 2: Disco Local
        val diskBmp = readTileFromDisk(key)
        if (diskBmp != null) {
            putInMemory(key, diskBmp)
            return diskBmp
        }

        // 3. Fallback de Rede
        val fetchedBmp = runCatching { networkFetcher() }.getOrNull()
        if (fetchedBmp != null && !fetchedBmp.isRecycled) {
            putInMemory(key, fetchedBmp)
            writeTileToDiskSafely(key, fetchedBmp)
            return fetchedBmp
        }

        return null
    }

    /**
     * Lê e decodifica o tile do disco caso exista e esteja dentro do TTL de 30 dias.
     */
    private fun readTileFromDisk(key: String): Bitmap? {
        val dir = diskCacheDir ?: return null
        return runCatching {
            val file = File(dir, "$key.tile")
            if (!file.exists() || !file.isFile || file.length() <= 0L) {
                return null
            }

            val ageMs = System.currentTimeMillis() - file.lastModified()
            if (ageMs > TILE_TTL_MS) {
                runCatching { file.delete() }
                return null
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decoded != null && decoded.width > 0 && decoded.height > 0) {
                decoded
            } else {
                // Arquivo corrompido ou incompleto: remove silenciosamente
                runCatching { file.delete() }
                null
            }
        }.getOrNull()
    }

    /**
     * Grava o tile em disco de maneira atômica (arquivo temporário -> rename)
     * e agenda verificação de limite de 50 MB em background.
     */
    private fun writeTileToDiskSafely(key: String, bitmap: Bitmap) {
        val dir = diskCacheDir ?: return
        runCatching {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val targetFile = File(dir, "$key.tile")
            val tempFile = File(dir, "$key.${System.nanoTime()}.tmp")

            FileOutputStream(tempFile).use { out ->
                // JPEG 90% oferece excelente qualidade para satélite com ~15-25 KB por tile
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
            }

            // A cada 15 novos tiles gravados, verifica se o diretório ultrapassou 50 MB
            if (writeCounterSinceLastCheck.incrementAndGet() >= 15) {
                writeCounterSinceLastCheck.set(0)
                triggerDiskMaintenanceIfNeeded(force = false)
            }
        }.onFailure { e ->
            Log.w(TAG, "Erro ao gravar tile $key em disco: ${e.message}")
        }
    }

    /**
     * Rotina leve em background (`Dispatchers.IO`) que verifica o tamanho da pasta
     * `maxrun_map_tiles` e, caso ultrapasse 50 MB, remove os arquivos mais antigos
     * (`lastModified()`) até ficar abaixo de 40 MB, além de limpar expirados (> 30 dias).
     */
    fun triggerDiskMaintenanceIfNeeded(force: Boolean = false) {
        val dir = diskCacheDir ?: return
        if (!isDiskCleanupRunning.compareAndSet(false, true)) return

        cleanupScope.launch {
            try {
                runCatching {
                    if (!dir.exists() || !dir.isDirectory) return@runCatching
                    val now = System.currentTimeMillis()
                    val files = dir.listFiles() ?: return@runCatching

                    var totalBytes = 0L
                    val validFiles = ArrayList<File>(files.size)

                    for (file in files) {
                        if (!file.isFile) continue
                        val isOrphanTmp = file.name.endsWith(".tmp") && (now - file.lastModified() > 60_000L)
                        val isExpired = (now - file.lastModified()) > TILE_TTL_MS

                        if (isOrphanTmp || isExpired) {
                            runCatching { file.delete() }
                        } else {
                            totalBytes += file.length()
                            validFiles.add(file)
                        }
                    }

                    // Se ultrapassar 50 MB, remove os mais antigos até ficar abaixo de 40 MB
                    if (totalBytes > MAX_DISK_CACHE_BYTES) {
                        val sortedByOldest = validFiles.sortedBy { it.lastModified() }
                        for (oldFile in sortedByOldest) {
                            if (totalBytes <= TARGET_DISK_CACHE_BYTES) break
                            val fileLen = oldFile.length()
                            if (runCatching { oldFile.delete() }.getOrDefault(false)) {
                                totalBytes -= fileLen
                            }
                        }
                    }
                }
            } finally {
                isDiskCleanupRunning.set(false)
            }
        }
    }
}
