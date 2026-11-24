package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.GymWorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GymShareCardGenerator {

    suspend fun generateGymReportBitmap(
        context: Context,
        session: GymWorkoutSession,
        athleteName: String = "Atleta",
        isLightMode: Boolean = false
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val ptBr = Locale("pt", "BR")

        // Filtra exclusivamente exercícios que tiveram pelo menos 1 série efetivamente concluída
        val executedExercises = session.exercises.filter { ex -> ex.sets.any { it.completed } }

        // Destaques de Cargas Máximas (Top 3 maiores cargas efetivamente executadas na sessão)
        val maxLoadHighlights = executedExercises.mapNotNull { ex ->
            val maxSet = ex.sets.filter { it.completed }.maxByOrNull { it.weight }
            if (maxSet != null && maxSet.weight > 0f) {
                Triple(ex.name, maxSet.weight, maxSet.reps)
            } else null
        }.sortedByDescending { it.second }.take(3)

        val highlightsBlockHeight = if (maxLoadHighlights.isNotEmpty()) 210 else 0
        val notesExtraHeight = executedExercises.count { it.notes.isNotBlank() } * 36

        // Estimar altura dinâmica de acordo com a quantidade de exercícios executados e destaques
        val baseHeaderHeight = 520 + highlightsBlockHeight + notesExtraHeight
        val exerciseRowHeight = 95
        val footerHeight = 160
        val calculatedHeight = baseHeaderHeight + (executedExercises.size * exerciseRowHeight) + footerHeight
        val height = calculatedHeight.coerceAtLeast(1100)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = if (isLightMode) Color.rgb(248, 250, 252) else Color.rgb(13, 15, 20)
        val cardBgColor = if (isLightMode) Color.rgb(255, 255, 255) else Color.rgb(22, 25, 34)
        val primaryText = if (isLightMode) Color.rgb(15, 23, 42) else Color.rgb(248, 250, 252)
        val secondaryText = if (isLightMode) Color.rgb(100, 116, 139) else Color.rgb(148, 163, 184)
        val accentColor = Color.rgb(14, 165, 233) // Ciano/Azul
        val goldColor = Color.rgb(250, 204, 21) // Dourado para Destaques de Carga Máxima

        // 1. Fundo Geral
        canvas.drawColor(bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 2. Card Principal Superior (Header)
        val marginX = 40f
        var currentY = 50f

        val headerRect = RectF(marginX, currentY, width - marginX, currentY + 380f)
        paint.color = cardBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(headerRect, 24f, 24f, paint)

        // Borda do Card
        paint.color = if (isLightMode) Color.rgb(226, 232, 240) else Color.rgb(38, 43, 56)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(headerRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        // Filete Ciano/Azul
        val accentRect = RectF(marginX, currentY, marginX + 12f, currentY + 380f)
        paint.color = accentColor
        canvas.drawRoundRect(accentRect, 6f, 6f, paint)

        // Título e Selo
        paint.color = accentColor
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MAX GYM • SHARE CARD OFICIAL", marginX + 40f, currentY + 60f, paint)

        // Nome do Atleta e Ficha
        paint.color = primaryText
        paint.textSize = 46f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val titleStr = if (session.workoutName.length > 26) session.workoutName.take(26) + "..." else session.workoutName
        canvas.drawText(titleStr.uppercase(), marginX + 40f, currentY + 125f, paint)

        paint.color = secondaryText
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateFmt = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", ptBr)
        val dateText = dateFmt.format(Date(session.date)).replaceFirstChar { it.uppercase() }
        canvas.drawText("Atleta: $athleteName • $dateText", marginX + 40f, currentY + 175f, paint)

        // Linha Divisória interna
        paint.color = if (isLightMode) Color.rgb(226, 232, 240) else Color.rgb(38, 43, 56)
        paint.strokeWidth = 1.5f
        canvas.drawLine(marginX + 40f, currentY + 205f, width - marginX - 40f, currentY + 205f, paint)

        // 3 Métricas Hero (Volume, Duração, Séries) em 3 Colunas
        val colWidth = (width - (marginX * 2) - 80f) / 3f

        // Coluna 1: Volume Total (kg)
        val col1X = marginX + 40f
        paint.color = secondaryText
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("VOLUME TOTAL (KG)", col1X, currentY + 250f, paint)

        paint.color = primaryText
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val volStr = String.format(ptBr, "%.0f kg", session.totalVolumeKg)
        canvas.drawText(volStr, col1X, currentY + 310f, paint)

        // Coluna 2: Duração
        val col2X = col1X + colWidth
        paint.color = secondaryText
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("DURAÇÃO", col2X, currentY + 250f, paint)

        paint.color = primaryText
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        val mins = session.durationSeconds / 60
        val secs = session.durationSeconds % 60
        val durStr = String.format(ptBr, "%02d:%02d", mins, secs)
        canvas.drawText(durStr, col2X, currentY + 310f, paint)

        // Coluna 3: Séries
        val col3X = col2X + colWidth
        paint.color = secondaryText
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("SÉRIES CONCLUÍDAS", col3X, currentY + 250f, paint)

        paint.color = primaryText
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("${session.completedSets}/${session.totalSets}", col3X, currentY + 310f, paint)

        currentY += 420f

        // 2.5 Bloco de Destaques de Cargas Máximas
        if (maxLoadHighlights.isNotEmpty()) {
            val prRect = RectF(marginX, currentY, width - marginX, currentY + 170f)
            paint.color = cardBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(prRect, 20f, 20f, paint)

            paint.color = goldColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(prRect, 20f, 20f, paint)
            paint.style = Paint.Style.FILL

            paint.color = goldColor
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            canvas.drawText("★ DESTAQUES DE CARGAS MÁXIMAS", marginX + 28f, currentY + 42f, paint)

            val innerAreaWidth = width - (marginX * 2) - 56f
            val itemW = innerAreaWidth / 3f
            val maxTextW = itemW - 18f

            maxLoadHighlights.forEachIndexed { hIdx, (exName, maxW, reps) ->
                val hx = marginX + 28f + (hIdx * itemW)
                paint.color = secondaryText
                paint.textSize = 18.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                var displayEx = exName
                if (paint.measureText(displayEx) > maxTextW) {
                    val ellipsis = "..."
                    val ellipsisW = paint.measureText(ellipsis)
                    while (displayEx.isNotEmpty() && paint.measureText(displayEx) + ellipsisW > maxTextW) {
                        displayEx = displayEx.dropLast(1)
                    }
                    displayEx = displayEx.trimEnd() + ellipsis
                }
                canvas.drawText(displayEx, hx, currentY + 86f, paint)

                val wStr = if (maxW % 1f == 0f) "${maxW.toInt()} kg" else String.format(ptBr, "%.1f kg", maxW)
                paint.color = primaryText
                paint.textSize = 30f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                canvas.drawText("$wStr ($reps reps)", hx, currentY + 132f, paint)
            }

            currentY += 205f
        }

        // 3. Bloco de Aparelhos e Cargas
        paint.color = primaryText
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("EXERCÍCIOS E CARGAS EXECUTADAS", marginX + 10f, currentY, paint)

        currentY += 30f

        val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightMode) Color.rgb(203, 213, 225) else Color.rgb(30, 41, 59) // #1E293B
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(6f, 8f), 0f)
        }

        executedExercises.forEachIndexed { idx, ex ->
            val hasNote = ex.notes.isNotBlank()
            val rowH = if (hasNote) 114f else 80f
            val rowRect = RectF(marginX, currentY, width - marginX, currentY + rowH)
            paint.color = cardBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rowRect, 14f, 14f, paint)

            paint.color = if (isLightMode) Color.rgb(226, 232, 240) else Color.rgb(38, 43, 56)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            canvas.drawRoundRect(rowRect, 14f, 14f, paint)
            paint.style = Paint.Style.FILL

            // 1. Medir primeiro o Resumo de Séries / Carga Máxima à direita (apenas séries concluídas)
            val completedSets = ex.sets.filter { it.completed }
            val setsForMax = completedSets
            val maxWeight = setsForMax.map { it.weight }.maxOrNull() ?: 0f
            val isBw = com.example.utils.WeightCalculator.isBodyweightExercise(null, ex.name, ex.equipment)
            val summaryRight = if (isBw && maxWeight <= 0.05f) {
                val maxReps = setsForMax.maxOfOrNull { it.reps } ?: 15
                "${setsForMax.size} séries • Peso Corporal x $maxReps reps"
            } else {
                val maxWeightStr = "${com.example.utils.WeightCalculator.formatStepNumber(maxWeight)} kg"
                "${setsForMax.size} séries • Máx: $maxWeightStr"
            }
            paint.color = accentColor
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            val summaryWidth = paint.measureText(summaryRight)
            val summaryStartX = width - marginX - 24f - summaryWidth
            canvas.drawText(summaryRight, summaryStartX, currentY + 48f, paint)

            // 2. Número + Nome do Aparelho usando toda a largura disponível até próximo da coluna direita (~28px = ~14.dp)
            paint.color = primaryText
            paint.textSize = 25f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val exNum = idx + 1
            val leftStartX = marginX + 24f
            val maxNameWidth = (summaryStartX - leftStartX - 28f).coerceAtLeast(200f)
            var fullLeftLabel = "$exNum. ${ex.name}"
            if (paint.measureText(fullLeftLabel) > maxNameWidth) {
                val ellipsis = "..."
                val ellipsisW = paint.measureText(ellipsis)
                while (fullLeftLabel.isNotEmpty() && paint.measureText(fullLeftLabel) + ellipsisW > maxNameWidth) {
                    fullLeftLabel = fullLeftLabel.dropLast(1)
                }
                fullLeftLabel = fullLeftLabel.trimEnd() + ellipsis
            }
            canvas.drawText(fullLeftLabel, leftStartX, currentY + 48f, paint)

            // 3. Linha guia pontilhada sutil (#1E293B) conectando o fim do nome do aparelho ao resumo da direita
            val nameEndX = leftStartX + paint.measureText(fullLeftLabel)
            val dotStartX = nameEndX + 16f
            val dotEndX = summaryStartX - 16f
            if (dotEndX - dotStartX > 20f) {
                val guidePath = Path().apply {
                    moveTo(dotStartX, currentY + 40f)
                    lineTo(dotEndX, currentY + 40f)
                }
                canvas.drawPath(guidePath, guideLinePaint)
            }

            if (hasNote) {
                paint.color = secondaryText
                paint.textSize = 20f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                val noteClip = if (ex.notes.length > 65) ex.notes.take(65) + "..." else ex.notes
                canvas.drawText("Nota: $noteClip", marginX + 24f, currentY + 86f, paint)
            }

            currentY += rowH + 12f
        }

        // 4. Rodapé Oficial
        currentY += 20f
        paint.color = secondaryText
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val footerText = "MAX PERFORMANCE • v${AppBuildInfo.VERSION_NAME} • Build #${AppBuildInfo.BUILD_NUMBER}"
        val footerW = paint.measureText(footerText)
        canvas.drawText(footerText, (width - footerW) / 2f, currentY + 30f, paint)

        bitmap
    }

    suspend fun saveGymReportToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, AppExportPaths.PICTURES_RELATIVE_PATH)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return@withContext uri
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, AppExportPaths.PUBLIC_FOLDER_NAME)
                targetDir.mkdirs()
                val imageFile = File(targetDir, fileName)
                FileOutputStream(imageFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                return@withContext Uri.fromFile(imageFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun shareGymReport(
        context: Context,
        session: GymWorkoutSession,
        athleteName: String = "Atleta",
        isLightMode: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateGymReportBitmap(
                context = context,
                session = session,
                athleteName = athleteName,
                isLightMode = isLightMode
            )

            val fileName = "MaxGym_Treino_${System.currentTimeMillis()}.png"
            val galleryUri = saveGymReportToGallery(context, bitmap, fileName)

            val uriToShare: Uri = galleryUri ?: run {
                val cachePath = File(context.cacheDir, "gym_reports")
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
                Toast.makeText(context, "Imagem salva na pasta ${AppExportPaths.PICTURES_RELATIVE_PATH}!", Toast.LENGTH_LONG).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uriToShare)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Treino MAX GYM"))
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erro ao gerar share card gym: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
