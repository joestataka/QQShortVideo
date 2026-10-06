package com.joestataka.qqshortvideo.data

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * 以特权身份读取被 Android 隔离的目录（如其他 App 的 Android/data）。
 *
 * 优先级：
 *  1) **Shizuku** —— 通过 Shizuku 服务在 shell 进程(uid 2000)执行命令，免 root；
 *  2) root —— `su` 执行，需 KernelSU / Magisk 授权。
 */
object RootShell {

    private const val TAG = "QQShort"
    private const val SU = "/system/bin/su"
    private const val LOG_FILE = "/data/data/com.joestataka.qqshortvideo/files/root.log"

    fun fileLog(msg: String) {
        runCatching { File(LOG_FILE).appendText("${System.currentTimeMillis()} $msg\n") }
    }

    // ---------- Shizuku 状态 ----------

    fun isShizukuAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isShizukuGranted(): Boolean =
        isShizukuAvailable() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun mode(): String = if (isShizukuGranted()) "Shizuku" else "root"

    // ---------- 对外接口 ----------

    fun probe(): Boolean {
        val out = execText("id", timeoutSec = 20)
        val ok = out?.contains("uid=") == true
        fileLog("probe mode=${mode()} ok=$ok out=${out?.trim()?.take(40)}")
        return ok
    }

    fun execText(cmd: String, timeoutSec: Long = 60): String? {
        val bytes = run(cmd, mergeErr = true, timeoutSec = timeoutSec) ?: return null
        return String(bytes)
    }

    fun execBytes(cmd: String, timeoutSec: Long = 60): ByteArray? =
        run(cmd, mergeErr = false, timeoutSec = timeoutSec)

    fun copyFile(src: String, dst: String): Boolean {
        val out = execText("cp -f '$src' '$dst' && echo __OK__ && ls -l '$dst'")
        return out?.contains("__OK__") == true
    }

    // ---------- 内部 ----------

    private fun run(cmd: String, mergeErr: Boolean, timeoutSec: Long): ByteArray? {
        val full = if (mergeErr) "$cmd 2>&1" else cmd

        if (isShizukuGranted()) {
            fileLog("exec[shizuku]: ${cmd.take(55)}")
            val bytes = execViaShizuku(full)
            fileLog(if (bytes == null) "shizuku fail: ${cmd.take(40)}" else "shizuku ok ${bytes.size}B")
            return bytes
        }

        fileLog("exec[su]: ${cmd.take(55)}")
        val process = try {
            ProcessBuilder(SU, "-c", full).start()
        } catch (t: Throwable) {
            Log.e(TAG, "su start failed: ${t.message}")
            fileLog("su start failed: ${t.message}")
            return null
        }
        return try {
            val buf = ByteArrayOutputStream()
            val reader = Thread {
                runCatching { process.inputStream.use { it.copyTo(buf) } }
            }
            reader.start()
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                reader.join(1000)
                fileLog("su timeout")
                return null
            }
            reader.join(2000)
            val bytes = buf.toByteArray()
            fileLog("su ok ${bytes.size}B")
            bytes
        } catch (t: Throwable) {
            Log.e(TAG, "su failed: ${t.message}")
            fileLog("su failed: ${t.message}")
            runCatching { process.destroy() }
            null
        }
    }

    /** 通过 Shizuku 的 shell 服务执行命令（uid 2000） */
    private fun execViaShizuku(cmd: String): ByteArray? {
        return try {
            val binder = Shizuku.getBinder() ?: return null
            val service = IShizukuService.Stub.asInterface(binder) ?: return null
            val process = service.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val input = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            val bytes = input.use { it.readBytes() }
            runCatching { process.waitFor() }
            runCatching { process.destroy() }
            bytes
        } catch (t: Throwable) {
            Log.e(TAG, "shizuku exec failed: ${t.message}")
            fileLog("shizuku exec failed: ${t.message}")
            null
        }
    }
}