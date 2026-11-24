package com.example.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Returns dynamic spacing based on screen size class
 */
@Composable
fun responsiveSpacing(): Dp {
    val windowSizeInfo = LocalWindowSizeInfo.current
    return when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> 16.dp
        WindowSize.MEDIUM -> 20.dp
        WindowSize.EXPANDED -> 24.dp
    }
}

/**
 * Returns dynamic padding for containers based on screen size class
 */
@Composable
fun responsivePadding(): Dp {
    val windowSizeInfo = LocalWindowSizeInfo.current
    return when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> 16.dp
        WindowSize.MEDIUM -> 24.dp
        WindowSize.EXPANDED -> 32.dp
    }
}

/**
 * Returns column count appropriate for content lists (e.g. workout cards, exercise cards)
 */
@Composable
fun responsiveColumnCount(compact: Int = 1, medium: Int = 2, expanded: Int = 3): Int {
    val windowSizeInfo = LocalWindowSizeInfo.current
    return when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> compact
        WindowSize.MEDIUM -> medium
        WindowSize.EXPANDED -> expanded
    }
}

/**
 * Helper to apply dynamic responsive padding around any Composable
 */
@Composable
fun Modifier.responsivePaddingModifier(): Modifier {
    val padding = responsivePadding()
    return this.padding(padding)
}

/**
 * ResponsiveGrid wraps LazyVerticalGrid to render contents in 1 column (compact),
 * 2 columns (medium) or 3/4 columns (expanded) with automated responsive spacing and contentPadding.
 */
@Composable
fun ResponsiveGrid(
    modifier: Modifier = Modifier,
    compactCols: Int = 1,
    mediumCols: Int = 2,
    expandedCols: Int = 3,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit
) {
    val cols = responsiveColumnCount(compactCols, mediumCols, expandedCols)
    val spacing = responsiveSpacing()

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * ResponsiveTwoPane is a layout helper designed for wide screens where you want
 * to display a supporting/detail pane alongside the primary pane (e.g. List-Detail format).
 */
@Composable
fun ResponsiveTwoPane(
    primaryContent: @Composable BoxScope.() -> Unit,
    secondaryContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.45f
) {
    val windowSizeInfo = LocalWindowSizeInfo.current

    if (windowSizeInfo.widthSizeClass == WindowSize.COMPACT) {
        // On compact, show primary content in full size (sub-navigation should switch screen)
        Box(modifier = modifier.fillMaxSize()) {
            primaryContent()
        }
    } else {
        // On medium/expanded, render side-by-side with adjustable split fraction
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(responsiveSpacing())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(splitFraction)
            ) {
                primaryContent()
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - splitFraction)
            ) {
                secondaryContent()
            }
        }
    }
}
