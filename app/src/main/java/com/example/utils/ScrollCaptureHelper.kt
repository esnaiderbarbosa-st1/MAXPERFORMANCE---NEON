package com.example.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Extension on SemanticsPropertyReceiver to mark a Compose layout node as a primary
 * traversal group for accessibility and Android 12+ (API 31+) native ScrollCapture search.
 */
fun SemanticsPropertyReceiver.customViewForScrollCapture() {
    isTraversalGroup = true
}

/**
 * Custom semantics modifier to ensure Android 12+ (API 31+) native ScrollCapture
 * and OEM screenshot tools (Samsung, Xiaomi, Pixel, etc.) correctly recognize
 * Jetpack Compose scroll containers (LazyColumn, verticalScroll) as valid ScrollCaptureTargets.
 */
fun Modifier.customViewForScrollCapture(enabled: Boolean = true): Modifier = if (enabled) {
    this.semantics {
        customViewForScrollCapture()
    }
} else this

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class ScrollCaptureState(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    var isCapturing by mutableStateOf(false)
        private set

    fun captureAndShare(
        scrollState: ScrollState? = null,
        lazyListState: LazyListState? = null
    ) {
        val activity = context.findActivity() ?: return
        isCapturing = true

        coroutineScope.launch {
            try {
                // Auto-scroll to bottom if scroll states are provided
                scrollState?.animateScrollTo(scrollState.maxValue)
                if (lazyListState != null) {
                    val count = lazyListState.layoutInfo.totalItemsCount
                    if (count > 0) {
                        lazyListState.animateScrollToItem(count - 1)
                    }
                }
                delay(300)

                LongScreenshotHelper.captureAndShare(context, activity)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isCapturing = false
            }
        }
    }
}

@Composable
fun rememberScrollCaptureState(): ScrollCaptureState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    return remember(context, coroutineScope) {
        ScrollCaptureState(context, coroutineScope)
    }
}
