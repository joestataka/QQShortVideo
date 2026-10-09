package com.joestataka.qqshortvideo.ui

import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.joestataka.qqshortvideo.data.RootShell
import com.joestataka.qqshortvideo.data.Source
import com.joestataka.qqshortvideo.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全屏播放器。视频位于受保护目录，先用 root 复制到应用私有缓存，再用系统 VideoView 播放。
 */
@Composable
fun PlayerScreen(video: VideoItem, onBack: () -> Unit) {
    BackHandler { onBack() }

    val ctx = LocalContext.current
    var localPath by remember(video.id) { mutableStateOf<String?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    // 播放进度（自建进度条，不用系统 MediaController —— 那个 PopupWindow 会抢触摸焦点）
    var durMs by remember(video.id) { mutableLongStateOf(0L) }
    var posMs by remember(video.id) { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var isPlaying by remember(video.id) { mutableStateOf(true) }

    // 退出播放页时真正停止播放并释放播放器。
    // 只把 View 从组合里移除的话，停止依赖 surface 回调，可能出现「界面回去了声音还在响」。
    DisposableEffect(Unit) {
        onDispose {
            videoView?.let { vv ->
                runCatching { vv.stopPlayback() }
                runCatching { vv.setMediaController(null) }
            }
            videoView = null
        }
    }

    LaunchedEffect(video.id) {
        // 已能直接读取（所有文件访问权限）：直接播放原文件
        if (video.source == Source.FILE && File(video.path).isFile) {
            localPath = video.path
            return@LaunchedEffect
        }
        // 否则用 root 复制到应用私有缓存后再播放
        val dst = File(ctx.cacheDir, "${video.id}.mp4")
        if (dst.exists() && dst.length() > 0L) {
            localPath = dst.absolutePath
            return@LaunchedEffect
        }
        val ok = withContext(Dispatchers.IO) {
            RootShell.copyFile(video.path, dst.absolutePath) && dst.length() > 0L
        }
        if (ok) localPath = dst.absolutePath
        else error = "无法读取视频。\n请授予「所有文件访问权限」，或为应用开启 root。"
    }

    // 每 500ms 同步一次播放进度（拖动进度条时不覆盖手指位置）
    LaunchedEffect(video.id, localPath) {
        while (true) {
            videoView?.let { vv ->
                val d = vv.duration
                if (d > 0) durMs = d.toLong()
                if (!dragging) {
                    val p = vv.currentPosition
                    if (p >= 0) posMs = p.toLong()
                }
                isPlaying = vv.isPlaying
            }
            delay(500L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val path = localPath
        when {
            path != null -> AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        videoView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoPath(path)
                        // 不用系统 MediaController：它是 PopupWindow，横跨底部且抢触摸焦点，
                        // 会干扰系统边缘返回手势（表现为「要划很长、还要停顿才返回」）。
                        // 缓存短片不需要进度条，点屏幕即可暂停/继续。
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, _, _ -> true }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            error != null -> Text(
                text = error!!,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )

            else -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "正在通过 root 读取视频…",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        // 兜底手势：屏幕任意位置向右滑 → 退出播放。
        // 不依赖系统边缘返回手势（滑偏一点系统就不认，会被误当成「返回失灵」）。
        Box(
            modifier = Modifier
                .matchParentSize()
                // 从屏幕内侧轻轻一划（≥24dp）即返回。
                // 为什么不依赖系统手势：从最边缘起手时，系统会先抢走手势流（app 收到 CANCEL），
                // 那种情况只能走系统阈值（约 1/6 屏宽）；不贴边起手时由本 app 判定，灵敏得多。
                .pointerInput(Unit) {
                    var acc = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { acc = 0f },
                        onDragCancel = { acc = 0f }
                    ) { _, d ->
                        acc += d
                        if (acc > 24.dp.toPx()) {
                            acc = 0f
                            onBack()
                        }
                    }
                }
                // 轻点屏幕：暂停 / 继续播放（替代被移除的进度条按钮）
                .pointerInput(Unit) {
                    detectTapGestures {
                        videoView?.let { vv ->
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        }
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color(0x88000000))
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("← 返回", color = Color.White, fontSize = 15.sp)
        }

        Text(
            text = video.id.take(12),
            color = Color(0xAAFFFFFF),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(16.dp)
        )

        // 暂停时屏幕中央显示暂停图标，明确告诉用户「是按了暂停，不是卡住/播完了」
        if (path != null && !isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color(0x73000000)),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .size(width = 9.dp, height = 30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                    Box(
                        Modifier
                            .size(width = 9.dp, height = 30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }

        // 底部进度条 + 时间（自建；它在这一层最上面，拖动时不会被返回手势吃掉）
        if (path != null && durMs > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fmtDuration(posMs), color = Color.White, fontSize = 12.sp)
                    Text(fmtDuration(durMs), color = Color.White, fontSize = 12.sp)
                }
                Slider(
                    value = (posMs.toFloat() / durMs).coerceIn(0f, 1f),
                    onValueChange = { f ->
                        dragging = true
                        posMs = (f * durMs).toLong()
                        videoView?.seekTo(posMs.toInt())
                    },
                    onValueChangeFinished = { dragging = false },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color(0x55FFFFFF)
                    )
                )
            }
        }
    }
}