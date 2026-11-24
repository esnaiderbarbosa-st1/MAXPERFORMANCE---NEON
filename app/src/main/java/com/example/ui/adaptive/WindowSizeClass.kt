package com.example.ui.adaptive

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowSize {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeight {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class LayoutModeOverride {
    AUTOMATIC,
    FORCE_COMPACT,
    FORCE_MEDIUM,
    FORCE_EXPANDED
}

data class WindowSizeInfo(
    val widthSizeClass: WindowSize,
    val heightSizeClass: WindowHeight,
    val widthDp: Dp,
    val heightDp: Dp,
    val isLandscape: Boolean,
    val layoutMode: LayoutModeOverride = LayoutModeOverride.AUTOMATIC
)

val LocalWindowSizeInfo = staticCompositionLocalOf {
    WindowSizeInfo(
        widthSizeClass = WindowSize.COMPACT,
        heightSizeClass = WindowHeight.MEDIUM,
        widthDp = 360.dp,
        heightDp = 640.dp,
        isLandscape = false
    )
}

/**
 * WindowSizeManager handles the reactive state of size detection and preference loading.
 */
object WindowSizeManager {
    @Composable
    fun rememberWindowSize(context: Context = LocalContext.current): WindowSizeInfo {
        val configuration = LocalConfiguration.current
        val sharedPrefs = remember(context) {
            context.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        }
        
        var savedOverrideStr by remember {
            mutableStateOf(sharedPrefs.getString("layout_mode_override", LayoutModeOverride.AUTOMATIC.name) ?: LayoutModeOverride.AUTOMATIC.name)
        }
        
        val overrideMode = remember(savedOverrideStr) {
            try {
                LayoutModeOverride.valueOf(savedOverrideStr)
            } catch (e: Exception) {
                LayoutModeOverride.AUTOMATIC
            }
        }
        
        val widthDp = configuration.screenWidthDp.dp
        val heightDp = configuration.screenHeightDp.dp
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // standard Breakpoints
        val calculatedWidthClass = when {
            widthDp < 600.dp -> WindowSize.COMPACT
            widthDp < 840.dp -> WindowSize.MEDIUM
            else -> WindowSize.EXPANDED
        }
        
        val calculatedHeightClass = when {
            heightDp < 480.dp -> WindowHeight.COMPACT
            heightDp < 900.dp -> WindowHeight.MEDIUM
            else -> WindowHeight.EXPANDED
        }
        
        // Orientation adjustments (Landscape increases size tier)
        val finalWidthClass = when (overrideMode) {
            LayoutModeOverride.FORCE_COMPACT -> WindowSize.COMPACT
            LayoutModeOverride.FORCE_MEDIUM -> WindowSize.MEDIUM
            LayoutModeOverride.FORCE_EXPANDED -> WindowSize.EXPANDED
            LayoutModeOverride.AUTOMATIC -> {
                if (isLandscape) {
                    when (calculatedWidthClass) {
                        WindowSize.COMPACT -> WindowSize.MEDIUM
                        WindowSize.MEDIUM -> WindowSize.EXPANDED
                        WindowSize.EXPANDED -> WindowSize.EXPANDED
                    }
                } else {
                    calculatedWidthClass
                }
            }
        }
        
        return remember(finalWidthClass, calculatedHeightClass, widthDp, heightDp, isLandscape, overrideMode) {
            WindowSizeInfo(
                widthSizeClass = finalWidthClass,
                heightSizeClass = calculatedHeightClass,
                widthDp = widthDp,
                heightDp = heightDp,
                isLandscape = isLandscape,
                layoutMode = overrideMode
            )
        }
    }

    /**
     * Helper to save layout preference override in SharedPreferences.
     */
    fun saveLayoutOverride(context: Context, mode: LayoutModeOverride) {
        val sharedPrefs = context.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("layout_mode_override", mode.name).apply()
    }
}
