package com.java.myapplication.ui

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.RootShell
import com.java.myapplication.data.Source
import com.java.myapplication.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 针对视频的写操作（都需要 Shizuku / root 特权通道），支持批量。
 * - 删除：直接删掉视频本体和对应封面；
 * - 分享：先由特权通道把文件复制到公共 Movies 目录，再交给系统媒体库分享。
 */
object VideoActions {

    private const val SHARE_DIR = "/sdcard/Movies/QQShortVideo"

    /** 批量删除视频本体 + 封面 + 空目录。 */
    suspend fun deleteMany(items: List<VideoItem>): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        val cmd = StringBuilder()
        items.forEach { it ->
            cmd.append("rm -f '").append(it.path).append("'; ")
            if (it.coverPath.isNotEmpty()) cmd.append("rm -f '").append(it.coverPath).append("'; ")
            cmd.append("rmdir '").append(it.path.substringBeforeLast('/')).append("' 2>/dev/null; ")
        }
        cmd.append("echo __DONE__")
        val out = RootShell.execText(cmd.toString(), timeoutSec = 180)
        RootShell.fileLog("deleteMany ${items.size}: ${out?.trim()?.take(60)}")
        out?.contains("__DONE__") == true
    }

    /** 批量复制到公共目录并扫描进媒体库，返回可分享的 content Uri 列表。 */
    suspend fun shareMany(ctx: Context, items: List<VideoItem>): List<Uri> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext emptyList()
            val cmd = StringBuilder("mkdir -p '$SHARE_DIR'")
            items.forEach { item ->
                cmd.append(" && cp -f '").append(item.path)
                    .append("' '").append(SHARE_DIR).append("/").append(item.id).append(".mp4'")
            }
            cmd.append(" && echo __OK__")
            val out = RootShell.execText(cmd.toString(), timeoutSec = 300)
            if (out?.contains("__OK__") != true) {
                RootShell.fileLog("shareMany copy failed n=${items.size}")
                return@withContext emptyList()
            }
            RootShell.fileLog("shareMany copy ok n=${items.size}")
            val paths = items.map { "$SHARE_DIR/${it.id}.mp4" }
            scan(ctx, paths)
        }

    private suspend fun scan(ctx: Context, paths: List<String>): List<Uri> =
        suspendCancellableCoroutine { cont ->
            val result = ArrayList<Uri>()
            MediaScannerConnection.scanFile(
                ctx,
                paths.toTypedArray(),
                Array(paths.size) { "video/mp4" }
            ) { _, uri ->
                if (uri != null) result.add(uri)
                if (result.size >= paths.size && cont.isActive) cont.resume(result)
            }
        }

    /** 分享一个或多个视频（会先等待扫描，超时则分享已就绪的部分）。 */
    suspend fun shareAndSend(ctx: Context, items: List<VideoItem>): Int = withContext(Dispatchers.IO) {
        val uris = withTimeoutOrNull(30_000) { shareMany(ctx, items) } ?: emptyList()
        if (uris.isEmpty()) return@withContext 0
        withContext(Dispatchers.Main) { sendShareIntent(ctx, uris) }
        uris.size
    }

    fun sendShareIntent(ctx: Context, uris: List<Uri>) {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/mp4"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "分享视频").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}

/** 详情信息对话框 */
@Composable
fun VideoDetailDialog(item: VideoItem, onDismiss: () -> Unit) {
    val channel = when (item.source) {
        Source.FILE -> "文件访问权限"
        Source.ROOT -> RootShell.mode()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("视频详情") },
        text = {
            SelectionContainer {
                Column {
                    DetailLine("文件名", "${item.id}.mp4")
                    DetailLine("哈希", item.id)
                    DetailLine("大小", "${humanSize(item.sizeBytes)}（${item.sizeBytes} 字节）")
                    DetailLine("修改时间", item.timeText)
                    DetailLine("读取方式", channel)
                    DetailLine("封面", if (item.coverPath.isEmpty()) "无" else "有")
                    DetailLine("路径", item.path)
                    Text(
                        text = "长按上方文字可选中并复制",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Text(text = value, fontSize = 13.sp)
    }
}

/** 批量删除确认对话框 */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    totalBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除所选视频？") },
        text = {
            Text("共 $count 个视频（${humanSize(totalBytes)}），将连同封面一起删除，不可恢复。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = Color(0xFFD32F2F))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}