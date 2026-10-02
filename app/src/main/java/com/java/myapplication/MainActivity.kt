package com.java.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.java.myapplication.ui.BrowserScreen
import com.java.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    // 目录位于其他应用的 Android/data 下，必须走 root 读取，
    // 因此不再申请（也无用的）MANAGE_EXTERNAL_STORAGE，直接进入浏览界面。
    BrowserScreen()
}