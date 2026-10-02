package com.java.myapplication.ui

import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.java.myapplication.data.RootShell
import com.java.myapplication.data.Source
import com.java.myapplication.data.VideoItem
import kotlinx.coroutines.Dispatchers
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
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoPath(path)
                        val controller = MediaController(c)
                        controller.setAnchorView(this)
                        setMediaController(controller)
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
    }
}