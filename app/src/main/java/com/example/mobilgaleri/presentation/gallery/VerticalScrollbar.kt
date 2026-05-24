package com.example.mobilgaleri.presentation.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.mobilgaleri.R

@Composable
fun VerticalScrollbar(
    modifier: Modifier = Modifier,
    state: LazyGridState,
    visible: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val layoutInfo = state.layoutInfo

    if (layoutInfo.totalItemsCount == 0) return

    val totalItems = layoutInfo.totalItemsCount
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isEmpty()) return

    val firstVisibleItemIndex = state.firstVisibleItemIndex
    val visibleItemCount = visibleItemsInfo.size
    val viewportSize = layoutInfo.viewportSize
    val viewportHeight = viewportSize.height.toFloat()

    if (viewportHeight <= 0) return

    val scrollFraction = if (totalItems > visibleItemCount) {
        firstVisibleItemIndex.toFloat() / (totalItems - visibleItemCount).toFloat()
    } else {
        0f
    }

    val density = LocalDensity.current
    val viewportHeightDp = with(density) { viewportSize.height.toDp() }

    val thumbHeight = 60.dp
    val trackHeight = (viewportHeightDp - thumbHeight).coerceAtLeast(0.dp)
    val thumbOffset = trackHeight * scrollFraction

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(250),
        label = "scrollbarAlpha"
    )
    val primary = colorResource(R.color.scrollbar)

    if (alpha == 0f) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(8.dp)
            .alpha(alpha)
            .background(primary.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .offset(y = thumbOffset)
                .width(8.dp)
                .height(thumbHeight)
                .background(primary)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consumeAllChanges()
                        coroutineScope.launch {
                            val relativeDrag = dragAmount / viewportHeight
                            val scrollAmount = relativeDrag * totalItems
                            val newIndex = (firstVisibleItemIndex + scrollAmount.toInt())
                                .coerceIn(0, totalItems - 1)

                            state.scrollToItem(newIndex)
                        }
                    }
                }
        )
    }
}