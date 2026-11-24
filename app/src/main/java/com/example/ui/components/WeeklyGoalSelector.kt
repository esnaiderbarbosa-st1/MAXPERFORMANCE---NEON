package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.normalizeWeeklyGoalDays
import com.example.utils.AudioSynth

/**
 * Seletor visual reutilizável de meta semanal de treinos (1 a 7 dias/semana).
 * Projetado para uso na edição de perfil e em fluxos de configuração inicial (ProfileSetupScreen).
 */
@Composable
fun WeeklyGoalSelector(
    selectedDays: Int,
    onSelectDays: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedDays = normalizeWeeklyGoalDays(selectedDays)
    val summaryLabel = if (normalizedDays == 1) "1 dia/sem" else "$normalizedDays dias/sem"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_goal_selector"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Meta semanal",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = summaryLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (day in 1..7) {
                val isSelected = day == normalizedDays
                val dayDescription = if (day == 1) "1 dia por semana" else "$day dias por semana"

                Surface(
                    selected = isSelected,
                    onClick = {
                        AudioSynth.playTickSound()
                        onSelectDays(day)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    tonalElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = dayDescription
                        }
                        .testTag("weekly_goal_day_$day")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = day.toString(),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
