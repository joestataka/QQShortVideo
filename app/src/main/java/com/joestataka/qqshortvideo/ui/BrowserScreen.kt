package com.joestataka.qqshortvideo.ui

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joestataka.qqshortvideo.data.RootShell
import com.joestataka.qqshortvideo.data.VideoItem
import com.joestataka.qqshortvideo.data.VideoScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** 主界面：单页浏览 —— 搜索 / 排序 / 月份筛选 + 封面网格（长按可多选） */
@Composable
fun BrowserScreen() {
    val ctx = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var sortKey by remember { mutableStateOf(SortKey.DATE_DESC) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var query by remember { mutableStateOf("") }
    var month by remember { mutableStateOf<String?>(null) } // null = 全部
    var shizukuAvailable by remember { mutableStateOf(false) }

    // 多选
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var detailTarget by remember { mutableStateOf<VideoItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun exitSelect() {
        selecting = false
        selected = emptySet()
    }

    // 监听 Shizuku 授权结果
    DisposableEffect(Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    reload++
                }
            }
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }

    LaunchedEffect(reload) {
        loading = true
        exitSelect()
        shizukuAvailable = withContext(Dispatchers.IO) { RootShell.isShizukuAvailable() }
        val list = withContext(Dispatchers.IO) { VideoScanner.scan() }
        videos = list
        rootOk = if (list.isEmpty()) {
            withContext(Dispatchers.IO) { RootShell.probe() }
        } else true
        loading = false
    }

    // 月份列表（倒序）
    val months = remember(videos) {
        videos.groupBy { it.timeText.take(7) }.keys.sortedDescending()
    }

    // 过滤 + 排序
    val filtered = remember(videos, month, query, sortKey) {
        val q = query.trim()
        val base = videos.filter { v ->
            (month == null || v.timeText.startsWith(month!!)) &&
                (q.isEmpty() || v.id.contains(q, true) || v.timeText.contains(q))
        }
        sortVideos(base, sortKey)
    }

    val selectedItems = remember(videos, selected) { videos.filter { it.id in selected } }

    // 排序 / 筛选变化时回到顶部
    val gridState = rememberLazyGridState()
    LaunchedEffect(sortKey, month, query) {
        runCatching { gridState.scrollToItem(0) }
    }

    // 选择模式下返回键 = 退出选择
    BackHandler(enabled = selecting) { exitSelect() }

    playing?.let { current ->
        PlayerScreen(video = current, onBack = { playing = null })
        return
    }

    if (videos.isEmpty() && rootOk == false) {
        RootGate(
            shizukuAvailable = shizukuAvailable,
            onRequestShizuku = {
                runCatching { Shizuku.requestPermission(0) }
                    .onFailure { reload++ }
            },
            onRequestRoot = { reload++ },
            onRetry = { reload++ }
        )
        return
    }

    val toggle: (VideoItem) -> Unit = { item ->
        selected = if (item.id in selected) selected - item.id else selected + item.id
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        if (selecting) {
            // 多选工具栏
            val allSelected = filtered.isNotEmpty() && filtered.all { it.id in selected }
            SelectionTopBar(
                count = selected.size,
                allSelected = allSelected,
                onExit = { exitSelect() },
                onToggleAll = {
                    selected = if (allSelected) emptySet() else filtered.map { it.id }.toSet()
                }
            )
        } else {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text("QQ 短视频库", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "  ${filtered.size} 个视频",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        // 搜索 + 排序
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索文件名 / 日期…", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            SortMenu(sortKey = sortKey, onSort = { sortKey = it })
        }

        // 月份筛选
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                MonthChip(text = "全部", selected = month == null) { month = null }
            }
            items(items = months, key = { it }) { m ->
                MonthChip(text = m, selected = month == m) { month = m }
            }
        }

        // 网格 + 右侧独立滚动条（滚动条占单独一列，不遮挡封面）
        Row(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    videos.isEmpty() -> EmptyHint(Modifier.align(Alignment.Center))
                    filtered.isEmpty() -> Text(
                        text = "没有匹配的视频",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> VideoGrid(
                        videos = filtered,
                        sortKey = sortKey,
                        groupByDate = false,
                        state = gridState,
                        selectedIds = selected,
                        onPlay = { item -> if (selecting) toggle(item) else playing = item },
                        onLongClick = { item ->
                            if (selecting) {
                                toggle(item)
                            } else {
                                selecting = true
                                selected = setOf(item.id)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            VerticalScrollbar(state = gridState)
            // 右侧留白，避开系统返回手势热区
            Spacer(modifier = Modifier.width(10.dp))
        }

        // 多选操作栏
        if (selecting) {
            SelectionBottomBar(
                count = selected.size,
                onShare = {
                    val targets = selectedItems
                    if (targets.isNotEmpty()) {
                        VideoActions.toast(ctx, "正在准备分享 ${targets.size} 个…")
                        scope.launch {
                            val n = VideoActions.shareAndSend(ctx, targets)
                            if (n == 0) VideoActions.toast(ctx, "分享失败：无法读取源文件")
                            exitSelect()
                        }
                    }
                },
                onDetail = { detailTarget = selectedItems.firstOrNull() },
                onDelete = { if (selected.isNotEmpty()) showDeleteConfirm = true }
            )
        }
    }

    detailTarget?.let { target ->
        VideoDetailDialog(item = target, onDismiss = { detailTarget = null })
    }

    if (showDeleteConfirm) {
        val targets = selectedItems
        DeleteConfirmDialog(
            count = targets.size,
            totalBytes = targets.sumOf { it.sizeBytes },
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                scope.launch {
                    val ok = VideoActions.deleteMany(targets)
                    if (ok) {
                        val ids = targets.map { it.id }.toSet()
                        videos = videos.filterNot { it.id in ids }
                        VideoActions.toast(ctx, "已删除 ${targets.size} 个")
                    } else {
                        VideoActions.toast(ctx, "删除失败")
                    }
                    exitSelect()
                }
            }
        )
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onToggleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✕",
            fontSize = 20.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onExit)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        Text(
            text = "已选 $count 个",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (allSelected) "取消全选" else "全选",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleAll)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SelectionBottomBar(
    count: Int,
    onShare: () -> Unit,
    onDetail: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BarButton(text = "📤 分享", enabled = count > 0, onClick = onShare)
            BarButton(text = "ℹ️ 详情", enabled = count == 1, onClick = onDetail)
            BarButton(
                text = "🗑 删除",
                enabled = count > 0,
                color = Color(0xFFD32F2F),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun BarButton(
    text: String,
    enabled: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = if (enabled) color else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun MonthChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, if (selected) primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun SortMenu(sortKey: SortKey, onSort: (SortKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = sortKey.label,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (key == sortKey) "✓ ${key.label}" else key.label,
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onSort(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Text(
        text = "没找到视频。\n请确认 QQ 短视频缓存目录存在。",
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(32.dp)
    )
}