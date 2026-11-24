package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarDaySchedule
import com.example.data.model.HeroCarouselItem
import com.example.data.model.Workout
import com.example.ui.theme.Primary
import com.example.ui.theme.Success

/**
 * =========================================================================
 * WEEKLY SCHEDULE COMPONENTS (REFAC-02A + UX-SCHEDULE)
 * Componente modularizado do Cronograma de 7 Dias da tela inicial.
 * Substitui o antigo card redundante por uma Faixa Compacta de 1 linha,
 * grafite (#1C1C1E), 100% sincronizada com o treino ativo no Hero Card.
 * =========================================================================
 */

@Composable
fun WeeklyScheduleSection(
    daysSchedule: List<CalendarDaySchedule>,
    activeHeroItem: HeroCarouselItem? = null,
    fatigueAdvisory: com.example.services.recommendation.FatigueAdvisoryState? = null,
    onAcceptRestDay: () -> Unit = {},
    onOverrideTrain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayIndex = remember(daysSchedule) {
        daysSchedule.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
    }
    var selectedDayIndex by remember(todayIndex) { mutableIntStateOf(todayIndex) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_schedule_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cabeçalho da Seção de Calendário
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "CRONOGRAMA 7 DIAS (SEG A DOM)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
            }

            Text(
                text = when {
                    fatigueAdvisory?.isRestDayAccepted == true -> "Fila Ajustada (+1d)"
                    fatigueAdvisory?.shouldShowBanner == true -> "Recuperação Sugerida"
                    else -> "Semana Atual"
                },
                color = when {
                    fatigueAdvisory?.isRestDayAccepted == true -> Color(0xFF34D399)
                    fatigueAdvisory?.shouldShowBanner == true -> Color(0xFF38BDF8)
                    else -> Color(0xFF94A3B8)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Carrossel Horizontal dos 7 dias da semana (Segunda a Domingo)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pillWidth = ((maxWidth - 36.dp) / 7f).coerceIn(46.dp, 62.dp)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
            ) {
                itemsIndexed(daysSchedule) { index, day ->
                    CalendarDayPillCard(
                        daySchedule = day,
                        isSelected = selectedDayIndex == index,
                        fatigueAdvisory = if (day.isToday) fatigueAdvisory else null,
                        onClick = {
                            selectedDayIndex = index
                        },
                        modifier = Modifier.width(pillWidth)
                    )
                }
            }
        }

        // Faixa Compacta de Resumo (sincronizada com o Hero Card e com o histórico real dos dias passados)
        val selectedDay = daysSchedule.getOrNull(selectedDayIndex) ?: daysSchedule.firstOrNull()
        if (selectedDay != null) {
            WeeklyScheduleCompactSummary(
                daySchedule = selectedDay,
                isTodaySelected = selectedDay.isToday,
                activeHeroItem = activeHeroItem,
                fatigueAdvisory = fatigueAdvisory,
                onAcceptRestDay = onAcceptRestDay,
                onOverrideTrain = onOverrideTrain
            )
        }
    }
}

/**
 * Pílula individual de dia no carrossel horizontal de 7 dias (Segunda a Domingo).
 */
@Composable
fun CalendarDayPillCard(
    daySchedule: CalendarDaySchedule,
    isSelected: Boolean,
    fatigueAdvisory: com.example.services.recommendation.FatigueAdvisoryState? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFatigueAlertToday = daySchedule.isToday && fatigueAdvisory?.shouldShowBanner == true
    val isRestAcceptedToday = daySchedule.isToday && fatigueAdvisory?.isRestDayAccepted == true

    val borderStroke = when {
        isRestAcceptedToday -> BorderStroke(1.5.dp, Color(0xFF10B981))
        isFatigueAlertToday -> BorderStroke(1.5.dp, Color(0xFF00E5FF))
        isSelected -> BorderStroke(1.5.dp, Primary)
        daySchedule.isToday -> BorderStroke(1.dp, Primary.copy(alpha = 0.65f))
        daySchedule.isCompleted -> BorderStroke(1.dp, Success.copy(alpha = 0.45f))
        else -> com.example.ui.theme.specularRimBorder(width = 1.dp, topAlpha = 0.24f, bottomAlpha = 0.04f)
    }

    val backgroundColor = when {
        isRestAcceptedToday -> Color(0xFF10B981).copy(alpha = 0.18f)
        isFatigueAlertToday -> Color(0xFF00E5FF).copy(alpha = 0.18f)
        isSelected -> Color(0xFF1E2430)
        daySchedule.isToday -> Color(0xFF1A2230)
        else -> Color(0xFF161922)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = borderStroke,
        modifier = modifier
            .widthIn(min = 46.dp)
            .height(78.dp)
            .testTag("calendar_pill_${daySchedule.dayOfWeek}_${daySchedule.dayOfMonth}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Dia da Semana (e.g. SEG, TER, QUA... ou HOJE)
            Text(
                text = if (daySchedule.isToday) "HOJE" else daySchedule.dayOfWeek,
                color = when {
                    isRestAcceptedToday -> Color(0xFF34D399)
                    isFatigueAlertToday -> Color(0xFF00E5FF)
                    isSelected || daySchedule.isToday -> Primary
                    daySchedule.isCompleted -> Success
                    else -> Color(0xFF94A3B8)
                },
                fontSize = 10.sp,
                fontWeight = if (isSelected || daySchedule.isToday || daySchedule.isCompleted) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.3.sp,
                maxLines = 1
            )

            // Dia do Mês (e.g. 23, 24, 25)
            Text(
                text = "${daySchedule.dayOfMonth}",
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )

            val isRunningPill = daySchedule.workoutCard?.workout?.id == "WORKOUT_RUNNING_SESSION" ||
                daySchedule.workoutCard?.title?.contains("Corrida", ignoreCase = true) == true

            // Indicador de Status: Selo verde ✓ para dias concluídos ou ponto colorido para previstos
            if (daySchedule.isCompleted) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Success.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Treino Concluído",
                        tint = Success,
                        modifier = Modifier.size(11.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRestAcceptedToday -> Color(0xFF10B981)
                                isFatigueAlertToday -> Color(0xFF00E5FF)
                                daySchedule.isRestDay -> Color(0xFF94A3B8).copy(alpha = 0.4f)
                                isRunningPill -> Color(0xFF00E5FF)
                                isSelected -> Primary
                                else -> Primary.copy(alpha = 0.7f)
                            }
                        )
                )
            }
        }
    }
}

/**
 * Faixa Compacta de Resumo (1 linha elegante Dark Neutra #161922) sincronizada com o Hero Card e histórico real.
 */
@Composable
fun WeeklyScheduleCompactSummary(
    daySchedule: CalendarDaySchedule,
    isTodaySelected: Boolean,
    activeHeroItem: HeroCarouselItem?,
    fatigueAdvisory: com.example.services.recommendation.FatigueAdvisoryState? = null,
    onAcceptRestDay: () -> Unit = {},
    onOverrideTrain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardData = daySchedule.workoutCard
    val isRunning = cardData?.workout?.id == "WORKOUT_RUNNING_SESSION" ||
        cardData?.title?.contains("Corrida", ignoreCase = true) == true

    val dateShortStr = remember(daySchedule.dateMillis) {
        java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            .format(java.util.Date(daySchedule.dateMillis))
    }

    val statusIcon: ImageVector
    val iconTintColor: Color
    val iconBgColor: Color
    val badgeText: String
    val badgeTextColor: Color
    val badgeBgColor: Color
    val summaryText: String

    when {
        isTodaySelected && fatigueAdvisory?.shouldShowBanner == true -> {
            statusIcon = Icons.Default.Bedtime
            iconTintColor = Color(0xFF00E5FF)
            iconBgColor = Color(0xFF00E5FF).copy(alpha = 0.16f)
            badgeText = "RECUPERAÇÃO"
            badgeTextColor = Color(0xFF00E5FF)
            badgeBgColor = Color(0xFF00E5FF).copy(alpha = 0.18f)
            summaryText = "Sugestão de Descanso Ativo ou Mobilidade (15 min)"
        }
        isTodaySelected && fatigueAdvisory?.isRestDayAccepted == true -> {
            statusIcon = Icons.Default.CheckCircle
            iconTintColor = Color(0xFF10B981)
            iconBgColor = Color(0xFF10B981).copy(alpha = 0.16f)
            badgeText = "DESCANSO ATIVO"
            badgeTextColor = Color(0xFF34D399)
            badgeBgColor = Color(0xFF10B981).copy(alpha = 0.18f)
            summaryText = "Mobilidade Regenerativa (15 min) • Fila ajustada"
        }
        daySchedule.isCompleted -> {
            statusIcon = Icons.Default.CheckCircle
            iconTintColor = Success
            iconBgColor = Success.copy(alpha = 0.15f)
            badgeText = if (isTodaySelected) "HOJE ✓" else "CONCLUÍDO ✓"
            badgeTextColor = Success
            badgeBgColor = Success.copy(alpha = 0.16f)
            val name = (cardData?.title ?: "Treino").replace(Regex("^(?i)(treino de hoje|hoje)\\s*[:•\\-]\\s*"), "").trim()
            summaryText = "$dateShortStr (${daySchedule.dayOfWeek}) -> $name Concluído ✓"
        }
        isTodaySelected && (activeHeroItem?.isRunning == true || isRunning) -> {
            statusIcon = Icons.Default.DirectionsRun
            iconTintColor = Color(0xFF00E5FF)
            iconBgColor = Color(0xFF00E5FF).copy(alpha = 0.16f)
            badgeText = "HOJE • CORRIDA"
            badgeTextColor = Color(0xFF00E5FF)
            badgeBgColor = Color(0xFF00E5FF).copy(alpha = 0.16f)
            val detail = activeHeroItem?.subtitle?.takeIf { it.isNotBlank() }
                ?: cardData?.muscleGroups?.takeIf { it.isNotBlank() }
                ?: "Dia de Corrida & Recuperação (Janela 48h)"
            summaryText = "Dia de Corrida & Recuperação • $detail"
        }
        isTodaySelected -> {
            val rawHeroName = activeHeroItem?.title?.takeIf { it.isNotBlank() }
                ?: activeHeroItem?.workout?.name?.takeIf { it.isNotBlank() }
                ?: cardData?.title
                ?: "Treino Principal"
            val heroName = rawHeroName
                .replace(Regex("^(?i)(treino de hoje|hoje)\\s*[:•\\-]\\s*"), "")
                .trim()
            val heroMuscles = cardData?.muscleGroups?.takeIf { it.isNotBlank() } ?: "Corpo Inteiro"

            statusIcon = Icons.Default.FitnessCenter
            iconTintColor = Primary
            iconBgColor = Primary.copy(alpha = 0.15f)
            badgeText = "HOJE"
            badgeTextColor = Primary
            badgeBgColor = Primary.copy(alpha = 0.15f)
            summaryText = "$heroName • $heroMuscles"
        }
        daySchedule.isRestDay || cardData == null -> {
            statusIcon = Icons.Default.Bedtime
            iconTintColor = Color(0xFF94A3B8)
            iconBgColor = Color(0xFF1E2430)
            badgeText = "DESCANSO"
            badgeTextColor = Color(0xFF94A3B8)
            badgeBgColor = Color(0xFF1E2430)
            summaryText = "$dateShortStr (${daySchedule.dayOfWeek}): Recuperação muscular / Descanso"
        }
        isRunning -> {
            statusIcon = Icons.Default.DirectionsRun
            iconTintColor = Color(0xFF00E5FF)
            iconBgColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
            badgeText = "CORRIDA"
            badgeTextColor = Color(0xFF00E5FF)
            badgeBgColor = Color(0xFF00E5FF).copy(alpha = 0.15f)
            val prefix = if (cardData.sequenceTag.contains("Fila Deslocada")) "[Fila Ajustada] " else ""
            summaryText = "$dateShortStr (${daySchedule.dayOfWeek}): ${prefix}Treino de Corrida • 30-45 min Aeróbico"
        }
        else -> {
            statusIcon = Icons.Default.FitnessCenter
            iconTintColor = Primary
            iconBgColor = Primary.copy(alpha = 0.15f)
            badgeText = if (cardData.sequenceTag.contains("Fila Deslocada")) "AJUSTADO" else "PREVISTO"
            badgeTextColor = Primary
            badgeBgColor = Primary.copy(alpha = 0.15f)
            val muscles = cardData.muscleGroups.takeIf { it.isNotBlank() } ?: "Corpo Inteiro"
            val cleanTitle = cardData.title.replace(Regex("^(?i)(treino de hoje|hoje)\\s*[:•\\-]\\s*"), "").trim()
            summaryText = "$dateShortStr (${daySchedule.dayOfWeek}): $cleanTitle • $muscles"
        }
    }

    val sanitizedSummaryText = remember(badgeText, summaryText) {
        if (badgeText.startsWith("HOJE", ignoreCase = true) && !badgeText.contains("✓")) {
            summaryText.replace(Regex("^(?i)(treino de hoje|hoje)\\s*[:•\\-]\\s*"), "").trim()
        } else {
            summaryText
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161922),
        border = if (isTodaySelected && fatigueAdvisory?.isRestDayAccepted == true) {
            BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
        } else {
            com.example.ui.theme.specularRimBorder(width = 1.dp, topAlpha = 0.28f, bottomAlpha = 0.04f)
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_schedule_summary_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(17.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = sanitizedSummaryText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Compatibilidade reversa caso outros pontos do app chamem WorkoutCalendarSection.
 */
@Composable
fun WorkoutCalendarSection(
    daysSchedule: List<CalendarDaySchedule>,
    onSelectWorkout: (Workout) -> Unit = {},
    onRestDayClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    WeeklyScheduleSection(
        daysSchedule = daysSchedule,
        activeHeroItem = null,
        modifier = modifier
    )
}

