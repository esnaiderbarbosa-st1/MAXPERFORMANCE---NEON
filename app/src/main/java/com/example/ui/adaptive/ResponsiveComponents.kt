package com.example.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.MaxCard
import com.example.ui.theme.Primary

/**
 * Responsive Typography scale based on width class.
 */
@Composable
fun responsiveFontSize(compact: TextUnit, medium: TextUnit, expanded: TextUnit): TextUnit {
    val windowSizeInfo = LocalWindowSizeInfo.current
    return when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> compact
        WindowSize.MEDIUM -> medium
        WindowSize.EXPANDED -> expanded
    }
}

@Composable
fun ResponsiveText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
    compactSize: TextUnit = 14.sp,
    mediumSize: TextUnit = 16.sp,
    expandedSize: TextUnit = 18.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val size = responsiveFontSize(compactSize, mediumSize, expandedSize)
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textAlign = textAlign,
        fontSize = size,
        lineHeight = lineHeight
    )
}

/**
 * Responsive premium Card container.
 * Pads internally by 16dp (compact), 24dp (medium) or 32dp (expanded).
 */
@Composable
fun ResponsiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isGlassmorphism: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val padding = responsivePadding()
    
    MaxCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        isGlassmorphism = isGlassmorphism
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            content()
        }
    }
}

/**
 * ResponsiveButton scales physical sizes and fonts based on width class.
 */
@Composable
fun ResponsiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Primary,
    contentColor: Color = Color.Black,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val windowSizeInfo = LocalWindowSizeInfo.current
    val (verticalPadding, horizontalPadding, fontSize) = when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> Triple(12.dp, 16.dp, 14.sp)
        WindowSize.MEDIUM -> Triple(16.dp, 24.dp, 16.sp)
        WindowSize.EXPANDED -> Triple(20.dp, 32.dp, 18.sp)
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, fontWeight = FontWeight.Bold)) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

/**
 * ResponsiveImage keeps correct aspects with adaptive proportions.
 */
@Composable
fun ResponsiveImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    compactHeight: Dp = 180.dp,
    mediumHeight: Dp = 260.dp,
    expandedHeight: Dp = 340.dp
) {
    val windowSizeInfo = LocalWindowSizeInfo.current
    val height = when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> compactHeight
        WindowSize.MEDIUM -> mediumHeight
        WindowSize.EXPANDED -> expandedHeight
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}
