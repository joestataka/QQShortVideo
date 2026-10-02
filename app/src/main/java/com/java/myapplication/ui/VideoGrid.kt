package com.java.myapplication.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.ThumbnailCache
import com.java.myapplication.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 排序方式 */
enum class SortKey(val label: String) {
    DATE_DESC("最新 ↓"),
    DATE_ASC("最早 ↑"),
    SIZE_DESC("最大 ↓"),
    SIZE_ASC("最小 ↑");

    val isDate: Boolean get() = this == DATE_DESC || this == DATE_ASC
}

fun sortVideos(list: List<VideoItem>, key: SortKey): List<VideoItem> = when (key) {
    SortKey.DATE_DESC -> list.sortedByDescending { it.timeText }
    SortKey.DATE_ASC -> list.sortedBy { it.timeText }
    SortKey.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
    SortKey.SIZE_ASC -> list.sortedBy { it.sizeBytes }
}

/**
 * 封面网格。按日期排序时自动插入日期分组头（整行）。
 * [selectedIds] 非空时展示多选态（选中项加边框 + 勾选角标）。
 */
@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    sortKey: SortKey,
    groupByDate: Boolean = true,
    state: LazyGridState = rememberLazyGridState(),
    selectedIds: Set<String> = emptySet(),
    onPlay: (VideoItem) -> Unit,
    onLongClick: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sortKey.isDate && groupByDate) {
            // 按日期分组，组内保持当前排序顺序
            videos.groupBy { it.timeText.take(10) }.forEach { (date, group) ->
                item(key = "header_$date", span = { GridItemSpan(maxLineSpan) }) {
                    GroupHeader(date = date, count = group.size)
                }
                items(items = group, key = { it.id }) { item ->
                    VideoCell(
                        item = item,
                        selected = item.id in selectedIds,
                        onClick = { onPlay(item) },
                        onLongClick = { onLongClick(item) }
                    )
                }
            }
        } else {
            items(items = videos, key = { it.id }) { item ->
                VideoCell(
                    item = item,
                    selected = item.id in selectedIds,
                    onClick = { onPlay(item) },
                    onLongClick = { onLongClick(item) }
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(date: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "  ·  $count 个",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCell(
    item: VideoItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        val b = withContext(Dispatchers.IO) { ThumbnailCache.get(item) }
        if (b == null) failed = true else bitmap = b
    }

    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            when {
                bmp != null -> Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.id,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                failed -> Text("无封面", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(20.dp))
            }

            // 中央播放按钮
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x59000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", fontSize = 15.sp, color = Color.White)
            }

            // 底部信息条：日期 + 大小
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.timeText.drop(5),
                    fontSize = 9.sp,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = humanSize(item.sizeBytes),
                    fontSize = 9.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }

            // 选中态：半透明遮罩 + 右上角勾选
            if (selected) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x33000000)))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun humanSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1fM".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0fK".format(bytes / 1024.0)
    else -> "${bytes}B"
}