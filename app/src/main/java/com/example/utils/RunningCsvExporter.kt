package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.RunningSession
import com.example.data.model.RunningShoe
import com.example.data.model.UserProfileData
import com.example.data.model.calculateSprintAnalytics
import com.example.data.model.isClassifiedAsRace
import com.example.data.model.isSimulatedOrTestSession
import com.example.data.model.toRoutePointItems
import com.example.engine.RunningProgramManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object RunningCsvExporter {

    private fun resolveCityFromGps(context: Context, session: RunningSession): String {
        val pts = session.routePointsJson.toRoutePointItems()
        val firstPt = pts.firstOrNull()
        if (firstPt == null) {
            return if (session.isSimulatedOrTestSession()) "Simulacao" else ""
        }
        val lat = firstPt.lat
        val lon = firstPt.lon

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val state = addr.adminArea
                    if (!city.isNullOrBlank()) {
                        return if (!state.isNullOrBlank() && state != city && state.length <= 3) {
                            "$city - $state"
                        } else {
                            city
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignora falha de rede do Geocoder e aplica heurística geográfica por coordenadas reais
        }

        return when {
            lat in -10.15..-9.80 && lon in -68.05..-67.65 -> "Rio Branco - AC"
            lat in -23.85..-23.35 && lon in -46.85..-46.30 -> "Sao Paulo - SP"
            lat in -23.10..-22.70 && lon in -43.80..-43.10 -> "Rio de Janeiro - RJ"
            lat in -16.05..-15.50 && lon in -48.30..-47.70 -> "Brasilia - DF"
            lat in -8.90..-8.60 && lon in -64.05..-63.75 -> "Porto Velho - RO"
            lat in -3.25..-2.90 && lon in -60.15..-59.85 -> "Manaus - AM"
            else -> String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }
    }

    private fun formatPaceMmSs(paceSec: Int): String {
        if (paceSec <= 0) return ""
        val pMin = paceSec / 60
        val pSec = paceSec % 60
        return String.format(Locale.US, "%d:%02d", pMin, pSec)
    }

    private fun formatDurationMmSs(durationSec: Int): String {
        if (durationSec <= 0) return "00:00"
        val mins = durationSec / 60
        val secs = durationSec % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    /**
     * Gera o conteúdo CSV analítico em memória (puro JVM) incluindo classificação Treino/Prova e título atualizado.
     */
    fun buildCsvContent(
        history: List<RunningSession>,
        userProfile: UserProfileData? = null,
        runningShoes: List<RunningShoe> = emptyList(),
        cityResolver: (RunningSession) -> String = { session ->
            if (session.isSimulatedOrTestSession()) "Simulacao" else ""
        }
    ): String {
        val ptBr = Locale("pt", "BR")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", ptBr)
        val timeFormat = SimpleDateFormat("HH:mm", ptBr)
        val athleteName = userProfile?.nome?.ifBlank { "Atleta MAX" } ?: "Atleta MAX"

        val csvBuilder = StringBuilder()
        // BOM UTF-8 para abertura perfeita de acentos no Excel / Google Sheets
        csvBuilder.append("\uFEFF")

        // Cabeçalho Analítico (Padrão Estudo Mensal) separado em 5 grupos claros:
        // 1. Identificação | 2. Treino Principal | 3. Sessão Total | 4. Desempenho nos Tiros | 5. Fisiologia/Equipamento
        val columns = listOf(
            // Identificação
            "Data",
            "Hora",
            "Atleta",
            "Classificacao",
            "Titulo_Sessao",
            "Semana",
            "Dia",
            "Estrutura",
            "Cidade_GPS",
            // Treino Principal
            "Dist_Principal_KM",
            "Tempo_Principal_Seg",
            "Tempo_Principal_Min_Dec",
            "Tempo_Principal_MMSS",
            "Pace_Principal",
            // Sessão Total
            "Dist_Total_KM",
            "Tempo_Total_Seg",
            "Tempo_Total_Min_Dec",
            "Tempo_Total_MMSS",
            "Pace_Total",
            // Desempenho nos Tiros
            "Pace_Medio_Tiros",
            "Pace_Melhor_Tiro",
            "Tempo_Correndo_Seg",
            "Tempo_Caminhando_Seg",
            // Fisiologia / Equipamento
            "BPM_Medio",
            "BPM_Max",
            "Cadencia_SPM",
            "Tenis_Utilizado",
            "KM_Acumulado_Tenis",
            "RPE_Manual",
            "Dor_Manual"
        )
        csvBuilder.append(columns.joinToString(";")).append("\n")

        // Ordena cronologicamente para cálculo de KM acumulado por tênis
        val chronologicalHistory = history.sortedBy { it.date }
        val shoeAccumulatedMetersMap = mutableMapOf<String, Long>()

        // Mapeia o KM acumulado até cada sessão
        val sessionShoeKmSnapshot = mutableMapOf<String, Float>()
        for (s in chronologicalHistory) {
            val sName = s.shoeName?.trim() ?: ""
            if (sName.isNotEmpty() && !sName.equals("Nenhum", ignoreCase = true)) {
                val prevMeters = shoeAccumulatedMetersMap[sName] ?: 0L
                val newMeters = prevMeters + s.distanceMeters.toLong()
                shoeAccumulatedMetersMap[sName] = newMeters
                sessionShoeKmSnapshot[s.id] = newMeters / 1000f
            }
        }

        for (session in history.sortedByDescending { it.date }) {
            val dateObj = Date(session.date)
            val dateStr = dateFormat.format(dateObj)
            val hourStr = timeFormat.format(dateObj)
            val classificationStr = if (session.isClassifiedAsRace()) "Prova" else "Treino"
            val sessionTitleStr = RunningProgramManager.getDisplayTitleForSession(session)
            val weekStr = session.weekNumber.toString()
            val dayStr = session.dayNumber.toString()

            val sprintAnalytics = calculateSprintAnalytics(session, emptyList())
            val structureStr = sprintAnalytics.structureSummary
            val cityGpsStr = cityResolver(session).replace(";", ",")

            // Treino Principal
            val mainDistMeters = sprintAnalytics.mainDistMeters
            val mainDurationSec = sprintAnalytics.mainDurationSec
            val mainDistKmStr = String.format(ptBr, "%.2f", mainDistMeters / 1000f)
            val mainDurationMinDecStr = String.format(ptBr, "%.2f", mainDurationSec / 60f)
            val mainDurationMmSsStr = formatDurationMmSs(mainDurationSec)
            val mainPaceStr = formatPaceMmSs(sprintAnalytics.mainPaceSecPerKm)

            // Sessão Total
            val totalDistMeters = sprintAnalytics.totalDistMeters
            val totalDurationSec = sprintAnalytics.totalDurationSec
            val totalDistKmStr = String.format(ptBr, "%.2f", totalDistMeters / 1000f)
            val totalDurationMinDecStr = String.format(ptBr, "%.2f", totalDurationSec / 60f)
            val totalDurationMmSsStr = formatDurationMmSs(totalDurationSec)
            val totalPaceStr = formatPaceMmSs(sprintAnalytics.totalPaceSecPerKm)

            // Desempenho nos Tiros
            val avgSprintPaceStr = formatPaceMmSs(sprintAnalytics.avgSprintPaceSecPerKm)
            val bestSprintPaceStr = formatPaceMmSs(sprintAnalytics.bestSprintPaceSecPerKm)
            val runningTimeSecStr = sprintAnalytics.runningTimeSec.toString()
            val walkingTimeSecStr = sprintAnalytics.walkingTimeSec.toString()

            // Fisiologia / Equipamento
            val avgBpmStr = session.avgHr?.takeIf { it > 0 }?.toString() ?: ""
            val maxBpmStr = session.maxHr?.takeIf { it > 0 }?.toString() ?: ""
            val cadenceStr = sprintAnalytics.avgCadenceSpm.takeIf { it > 0 }?.toString() ?: ""

            val shoeStr = session.shoeName?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Nenhum", ignoreCase = true) } ?: ""
            val matchedCatalogShoe = runningShoes.firstOrNull {
                "${it.brand} ${it.name}".trim().equals(shoeStr, ignoreCase = true) ||
                    it.name.trim().equals(shoeStr, ignoreCase = true)
            }
            val accumulatedShoeKm: Float? = when {
                matchedCatalogShoe != null && matchedCatalogShoe.totalDistanceKm > 0f -> matchedCatalogShoe.totalDistanceKm
                sessionShoeKmSnapshot.containsKey(session.id) -> sessionShoeKmSnapshot[session.id]
                else -> null
            }
            val accumulatedShoeKmStr = accumulatedShoeKm?.let { String.format(ptBr, "%.2f", it) } ?: ""

            // RPE_Manual e Dor_Manual: vazio ("") se não tiverem sido preenchidos manualmente pelo usuário!
            val rpeManualStr = if (session.rpe in 1..10) session.rpe.toString() else ""
            val dorManualStr = session.painResponse?.trim()?.takeIf { it.isNotEmpty() } ?: ""

            val rowValues = listOf(
                dateStr,
                hourStr,
                athleteName.replace(";", ","),
                classificationStr,
                sessionTitleStr.replace(";", ","),
                weekStr,
                dayStr,
                structureStr.replace(";", ","),
                cityGpsStr,
                mainDistKmStr,
                mainDurationSec.toString(),
                mainDurationMinDecStr,
                mainDurationMmSsStr,
                mainPaceStr,
                totalDistKmStr,
                totalDurationSec.toString(),
                totalDurationMinDecStr,
                totalDurationMmSsStr,
                totalPaceStr,
                avgSprintPaceStr,
                bestSprintPaceStr,
                runningTimeSecStr,
                walkingTimeSecStr,
                avgBpmStr,
                maxBpmStr,
                cadenceStr,
                shoeStr.replace(";", ","),
                accumulatedShoeKmStr,
                rpeManualStr,
                dorManualStr.replace(";", ",")
            )
            csvBuilder.append(rowValues.joinToString(";")).append("\n")
        }

        return csvBuilder.toString()
    }

    fun exportHistoryToCsv(
        context: Context,
        history: List<RunningSession>,
        userProfile: UserProfileData? = null,
        runningShoes: List<RunningShoe> = emptyList()
    ) {
        if (history.isEmpty()) {
            Toast.makeText(context, "Nenhum treino no histórico para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val ptBr = Locale("pt", "BR")
            val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmm", ptBr)
            val timestampStr = fileDateFormat.format(Date())
            val fileName = "MAX_RUN_Analitico_${timestampStr}.csv"

            val csvContent = buildCsvContent(
                history = history,
                userProfile = userProfile,
                runningShoes = runningShoes,
                cityResolver = { session -> resolveCityFromGps(context, session) }
            )
            val bytes = csvContent.toByteArray(Charsets.UTF_8)
            val relativeSubfolder = AppExportPaths.DOWNLOADS_CSV_RELATIVE_PATH

            // 1. Gravar na pasta pública Download/MAXPERFORMANCE/CSV/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubfolder)
                }
                val resolver = context.contentResolver
                val publicUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (publicUri != null) {
                    resolver.openOutputStream(publicUri)?.use { os ->
                        os.write(bytes)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "${AppExportPaths.PUBLIC_FOLDER_NAME}/CSV"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val publicFile = File(downloadsDir, fileName)
                publicFile.writeBytes(bytes)
            }

            // 2. Gravar no cacheDir para compartilhamento imediato via FileProvider
            val cacheFile = File(context.cacheDir, fileName)
            cacheFile.writeBytes(bytes)

            val shareUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            // 3. Feedback via Toast
            Toast.makeText(
                context,
                "Planilha salva em ${AppExportPaths.DOWNLOADS_CSV_RELATIVE_PATH}/$fileName",
                Toast.LENGTH_LONG
            ).show()

            // 4. Abrir Share Sheet imediato (Intent.createChooser)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Planilha Analítica MAX RUN")
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Compartilhar Planilha CSV Analítica (MAX RUN)"))
            AudioSynth.playTickSound()
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
