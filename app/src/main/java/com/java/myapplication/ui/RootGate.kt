package com.java.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 未获得读取权限时的引导页。
 * 目录位于其他应用的 Android/data 下，普通权限读不到，
 * 需要 **Shizuku**（推荐，免 root）或 root。
 */
@Composable
fun RootGate(
    shizukuAvailable: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestRoot: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要授权",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "QQ 短视频缓存位于 Android/data 目录，\n" +
                "Android 13 起系统禁止普通应用读取。\n" +
                "可通过 Shizuku 或 root 授权后读取。",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )

        if (shizukuAvailable) {
            Button(onClick = onRequestShizuku) { Text("授予 Shizuku 权限") }
        } else {
            Text(
                text = "未检测到 Shizuku。可先安装并启动 Shizuku，\n然后回到本页点“刷新”。",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        TextButton(onClick = onRequestRoot) { Text("申请 Root 权限（Magisk 会弹窗）") }
        Text(
            text = "KernelSU 无法自动弹窗，请在管理器中手动为本应用\n开启 root，然后点下面的按钮。",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        TextButton(onClick = onRetry) { Text("已授权，刷新") }
    }
}