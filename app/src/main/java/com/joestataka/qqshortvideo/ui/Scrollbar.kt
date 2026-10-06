package com.joestataka.qqshortvideo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 给 [LazyGridState] 配一个右侧可拖动的滚动条。
 * - 拖动滑块：按比例滚动；
 * - 点击轨道：跳到该位置。
 */
@Composable
fun VerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackH by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    var dragStartFirst by remember { mutableIntStateOf(0) }

    val layout = state.layoutInfo
    val total = layout.totalItemsCount
    val touchW = 14.dp
    val barW = 6.dp

    Box(
        modifier = modifier
            .width(touchW)
            .fillMaxHeight()
            .onSizeChanged { trackH = it.height.toFloat() }
            .pointerInput(Unit) {
                val minThumb = with(density) { 44.dp.toPx() }
                detectDragGestures(
                    onDragStart = { offset ->
                        val info = state.layoutInfo
                        val t = info.totalItemsCount.coerceAtLeast(1)
                        val firstIdx = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                        val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val visible = (lastIdx - firstIdx + 1).coerceAtLeast(1)
                        val th = (trackH * visible / t.toFloat()).coerceAtLeast(minThumb).coerceAtMost(trackH)
                        val maxTop = (trackH - th).coerceAtLeast(0f)
                        val top = (firstIdx.toFloat() / t * trackH).coerceIn(0f, maxTop)
                        val inThumb = offset.y >= top && offset.y <= top + th
                        dragAccum = 0f
                        dragStartFirst = firstIdx
                        if (!inThumb) {
                            val frac = (offset.y / trackH).coerceIn(0f, 1f)
                            val idx = (frac * (t - 1)).roundToInt()
                            scope.launch { state.scrollToItem(idx) }
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val info = state.layoutInfo
                        val t = info.totalItemsCount.coerceAtLeast(1)
                        val firstIdx = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                        val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val visible = (lastIdx - firstIdx + 1).coerceAtLeast(1)
                        val th = (trackH * visible / t.toFloat()).coerceAtLeast(minThumb).coerceAtMost(trackH)
                        val maxTop = (trackH - th).coerceAtLeast(1f)
                        dragAccum += delta.y
                        val frac = dragAccum / maxTop
                        val idx = (dragStartFirst + frac * t).roundToInt().coerceIn(0, t - 1)
                        scope.launch { state.scrollToItem(idx) }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        if (total > 0 && trackH > 0f) {
            val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            val visible = (last - first + 1).coerceAtLeast(1)
            val minThumb = with(density) { 44.dp.toPx() }
            val thumbH =
                (trackH * visible / total.toFloat()).coerceAtLeast(minThumb).coerceAtMost(trackH)
            val maxTop = (trackH - thumbH).coerceAtLeast(0f)
            val thumbTop = (first.toFloat() / total * trackH).coerceIn(0f, maxTop)

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbTop.roundToInt()) }
                    .width(barW)
                    .height(with(density) { thumbH.toDp() })
                    .background(color, RoundedCornerShape(barW / 2))
            )
        }
    }
}