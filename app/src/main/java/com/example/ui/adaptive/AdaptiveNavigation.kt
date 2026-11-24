package com.example.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMode
import com.example.ui.theme.Primary
import com.example.ui.theme.SurfaceDim
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.SecondaryElement
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun AdaptiveNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val windowSizeInfo = LocalWindowSizeInfo.current
    val activeScreen by viewModel.activeScreen.collectAsState()
    val appMode by viewModel.appMode.collectAsState()
    val isRunningHudActive by viewModel.isRunningHudActive.collectAsState()

    val showRunningProgramsCatalog by viewModel.showRunningProgramsCatalog.collectAsState()

    val navItems = if (appMode == AppMode.BODYBUILDING) {
        listOf(
            Triple(AppScreen.HOME, Icons.Default.Home, "Início"),
            Triple(AppScreen.WORKOUTS, Icons.Default.FitnessCenter, "Treinos"),
            Triple(AppScreen.DIARIO, Icons.Default.History, "Histórico"),
            Triple(AppScreen.LIBRARY, Icons.Default.Book, "Biblioteca"),
            Triple(AppScreen.PROFILE, Icons.Default.Person, "Perfil")
        )
    } else {
        listOf(
            Triple(AppScreen.HOME, Icons.Default.DirectionsRun, "Início"),
            Triple(AppScreen.RUNNING_HISTORY, Icons.Default.History, "Histórico"),
            Triple(AppScreen.SHARE_CARD, Icons.Default.Share, "Social"),
            Triple(AppScreen.GYM, Icons.Default.List, "Planos"),
            Triple(AppScreen.PROFILE, Icons.Default.Person, "Perfil")
        )
    }

    val workoutFlowState by viewModel.workoutFlowState.collectAsState()
    val isPreSessionActive by viewModel.isPreSessionActive.collectAsState()
    val activeWorkout by viewModel.activeWorkout.collectAsState()
    val workoutDurationSec by viewModel.workoutDurationSeconds.collectAsState()
    val hasActiveGymSession = (workoutFlowState == com.example.ui.viewmodel.WorkoutFlowState.Running || isPreSessionActive) && activeWorkout != null

    val navHighlightColor = if (appMode == AppMode.RUNNING) com.example.ui.theme.RunColors.brand else Primary

    when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> {
            // COMPACT: Bottom navigation, single-column, standard header top
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                bottomBar = {
                    if (activeScreen != AppScreen.PLAYER && activeScreen != AppScreen.SHARE_CARD && !isRunningHudActive) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (hasActiveGymSession && activeWorkout != null) {
                                ActiveWorkoutFloatingBar(
                                    workoutName = activeWorkout?.name ?: "Treino em Andamento",
                                    durationSeconds = workoutDurationSec,
                                    onResumeClick = {
                                        com.example.utils.AudioSynth.playTickSound()
                                        viewModel.activeScreen.value = AppScreen.PLAYER
                                    }
                                )
                            }
                            NavigationBar(
                                containerColor = SurfaceDim,
                                tonalElevation = 8.dp,
                                modifier = Modifier.navigationBarsPadding()
                            ) {
                                navItems.forEach { (screen, icon, label) ->
                                    val isSelected = if (appMode == AppMode.RUNNING && screen == AppScreen.GYM) {
                                        activeScreen == AppScreen.GYM || showRunningProgramsCatalog
                                    } else if (appMode == AppMode.RUNNING && screen == AppScreen.HOME) {
                                        activeScreen == AppScreen.HOME && !showRunningProgramsCatalog
                                    } else {
                                        activeScreen == screen || (screen == AppScreen.WORKOUTS && (activeScreen == AppScreen.PLAYER || activeScreen == AppScreen.CONCLUIDO)) || (screen == AppScreen.PROFILE && activeScreen == AppScreen.APP_SETTINGS)
                                    }
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.showRunningProgramsCatalog.value = false
                                            if (screen == AppScreen.DIARIO) {
                                                viewModel.historyTab.value = 1
                                            } else if (screen == AppScreen.RUNNING_HISTORY) {
                                                viewModel.historyTab.value = 0
                                            }
                                            viewModel.activeScreen.value = screen
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        icon = { Icon(imageVector = icon, contentDescription = label) },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            selectedTextColor = navHighlightColor,
                                            indicatorColor = navHighlightColor,
                                            unselectedIconColor = OnSurfaceDim,
                                            unselectedTextColor = OnSurfaceDim
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                content = { innerPadding ->
                    content(innerPadding)
                }
            )
        }
        WindowSize.MEDIUM -> {
            // MEDIUM: Navigation Rail on left, content in column next to it
            Row(modifier = modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = SurfaceDim,
                    header = {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MAX",
                            color = Primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    navItems.forEach { (screen, icon, label) ->
                        val isSelected = if (appMode == AppMode.RUNNING && screen == AppScreen.GYM) {
                            activeScreen == AppScreen.GYM || showRunningProgramsCatalog
                        } else if (appMode == AppMode.RUNNING && screen == AppScreen.HOME) {
                            activeScreen == AppScreen.HOME && !showRunningProgramsCatalog
                        } else {
                            activeScreen == screen || (screen == AppScreen.WORKOUTS && (activeScreen == AppScreen.PLAYER || activeScreen == AppScreen.CONCLUIDO)) || (screen == AppScreen.PROFILE && activeScreen == AppScreen.APP_SETTINGS)
                        }
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = {
                                viewModel.showRunningProgramsCatalog.value = false
                                if (screen == AppScreen.DIARIO) {
                                    viewModel.historyTab.value = 1
                                } else if (screen == AppScreen.RUNNING_HISTORY) {
                                    viewModel.historyTab.value = 0
                                }
                                viewModel.activeScreen.value = screen
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            icon = { Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(26.dp)) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = navHighlightColor,
                                indicatorColor = navHighlightColor,
                                unselectedIconColor = OnSurfaceDim,
                                unselectedTextColor = OnSurfaceDim
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues(0.dp))
                    }
                    if (hasActiveGymSession && activeWorkout != null && activeScreen != AppScreen.PLAYER) {
                        ActiveWorkoutFloatingBar(
                            workoutName = activeWorkout?.name ?: "Treino em Andamento",
                            durationSeconds = workoutDurationSec,
                            onResumeClick = {
                                com.example.utils.AudioSynth.playTickSound()
                                viewModel.activeScreen.value = AppScreen.PLAYER
                            }
                        )
                    }
                }
            }
        }
        WindowSize.EXPANDED -> {
            // EXPANDED: Permanent Navigation Drawer on left
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = SurfaceDim,
                        drawerTonalElevation = 4.dp,
                        modifier = Modifier.width(260.dp).fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MAX PERFORMANCE",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))

                        navItems.forEach { (screen, icon, label) ->
                            val isSelected = if (appMode == AppMode.RUNNING && screen == AppScreen.GYM) {
                                activeScreen == AppScreen.GYM || showRunningProgramsCatalog
                            } else if (appMode == AppMode.RUNNING && screen == AppScreen.HOME) {
                                activeScreen == AppScreen.HOME && !showRunningProgramsCatalog
                            } else {
                                activeScreen == screen || (screen == AppScreen.WORKOUTS && (activeScreen == AppScreen.PLAYER || activeScreen == AppScreen.CONCLUIDO)) || (screen == AppScreen.PROFILE && activeScreen == AppScreen.APP_SETTINGS)
                            }
                            NavigationDrawerItem(
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { Text(text = label, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                selected = isSelected,
                                onClick = {
                                    viewModel.showRunningProgramsCatalog.value = false
                                    if (screen == AppScreen.DIARIO) {
                                        viewModel.historyTab.value = 1
                                    } else if (screen == AppScreen.RUNNING_HISTORY) {
                                        viewModel.historyTab.value = 0
                                    }
                                    viewModel.activeScreen.value = screen
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = navHighlightColor,
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color.Black,
                                    unselectedIconColor = OnSurfaceDim,
                                    unselectedTextColor = OnSurfaceDim,
                                    unselectedContainerColor = Color.Transparent
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Custom Layout selector inside Expanded drawer for user preference selection
                        LayoutSelectorFooter()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues(0.dp))
                    }
                    if (hasActiveGymSession && activeWorkout != null && activeScreen != AppScreen.PLAYER) {
                        ActiveWorkoutFloatingBar(
                            workoutName = activeWorkout?.name ?: "Treino em Andamento",
                            durationSeconds = workoutDurationSec,
                            onResumeClick = {
                                com.example.utils.AudioSynth.playTickSound()
                                viewModel.activeScreen.value = AppScreen.PLAYER
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveWorkoutFloatingBar(
    workoutName: String,
    durationSeconds: Long,
    onResumeClick: () -> Unit
) {
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

    Surface(
        onClick = onResumeClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151922),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Primary.copy(alpha = 0.65f)),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip( androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF00E676))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TREINO EM ANDAMENTO",
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "• $timeFormatted",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = workoutName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onResumeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "RETOMAR",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun LayoutSelectorFooter() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val windowSizeInfo = LocalWindowSizeInfo.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryElement)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Preferência de Layout:",
            color = OnSurfaceDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDim),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (windowSizeInfo.layoutMode) {
                            LayoutModeOverride.AUTOMATIC -> "Automático"
                            LayoutModeOverride.FORCE_COMPACT -> "Forçar Celular"
                            LayoutModeOverride.FORCE_MEDIUM -> "Forçar Tablet"
                            LayoutModeOverride.FORCE_EXPANDED -> "Forçar PC"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("▼", color = Primary, fontSize = 10.sp)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDim).border(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                LayoutModeOverride.values().forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (mode) {
                                    LayoutModeOverride.AUTOMATIC -> "Automático (Recomendado)"
                                    LayoutModeOverride.FORCE_COMPACT -> "Celular (Compacto)"
                                    LayoutModeOverride.FORCE_MEDIUM -> "Tablet (Médio)"
                                    LayoutModeOverride.FORCE_EXPANDED -> "Desktop (Expandido)"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        onClick = {
                            WindowSizeManager.saveLayoutOverride(context, mode)
                            expanded = false
                            // Trigger restart or configuration change reaction by restarting activity
                            (context as? android.app.Activity)?.recreate()
                        }
                    )
                }
            }
        }
    }
}
