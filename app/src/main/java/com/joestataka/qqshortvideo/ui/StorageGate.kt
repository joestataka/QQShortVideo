package com.joestataka.qqshortvideo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 未授予「所有文件访问权限」时的引导页。
 */
@Composable
fun StorageGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "需要文件访问权限",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "QQ 短视频缓存位于 Android/data 目录，\n" +
                "请授予本应用「所有文件访问权限」，\n" +
                "以便读取视频与封面。",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )
        Button(onClick = onRequest) { Text("去授权") }
        Text(
            text = "若列表为空，也可在已 root 的设备上\n为应用开启 root 权限作为备选。",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}