package com.joestataka.qqshortvideo.data

import java.util.concurrent.ConcurrentHashMap

/**
 * 从 MP4 里读出**视频时长**。
 *
 * 为什么不用 MediaMetadataRetriever：视频在别的 App 的 Android/data 下，本进程拿不到可用的
 * FileDescriptor（Android 16 实测 File.exists() 都为 false）。所以走两条捷径：
 *
 *  - 时长藏在 `moov > mvhd` 里，而它要么在文件**开头**（faststart），要么在**结尾**；
 *  - 用特权通道 `dd` 只读头/尾各一段（几十 KB），自己扫 `mvhd` 解析，不必整file搬运。
 *
 * 结果按路径缓存（失败的也缓存，避免重复扫）。
 */
object DurationProbe {

    private val cache = ConcurrentHashMap<String, Long>()
    private const val UNKNOWN = -1L

    /** 返回毫秒；未知返回 null */
    fun get(path: String): Long? {
        val v: Long = cache[path] ?: run {
            val d = probe(path)
            val stored = d ?: UNKNOWN
            cache[path] = stored
            stored
        }
        return v.takeIf { it > 0 }
    }

    private fun probe(path: String): Long? {
        // 1) 先读头部 96KB（faststart 的 mp4 都在这儿）
        readChunk(path, skip = 0, count = 96 * 1024)?.let { head ->
            parseMvhd(head)?.let { return it }
        }
        // 2) 再读尾部 192KB（手机录制的 mp4 通常 moov 在末尾）
        val size = fileSize(path) ?: return null
        val tail = 192 * 1024L
        if (size > tail) {
            readChunk(path, skip = size - tail, count = tail.toInt())?.let { back ->
                parseMvhd(back)?.let { return it }
            }
        }
        return null
    }

    private fun readChunk(path: String, skip: Long, count: Int): ByteArray? {
        val bs = 4096
        val blocks = (count + bs - 1) / bs
        val cmd = if (skip <= 0) {
            "dd if='$path' bs=$bs count=$blocks 2>/dev/null"
        } else {
            "dd if='$path' bs=$bs skip=${skip / bs} count=$blocks 2>/dev/null"
        }
        val bytes = RootShell.execBytes(cmd, timeoutSec = 20) ?: return null
        return bytes.takeIf { it.isNotEmpty() }
    }

    private fun fileSize(path: String): Long? =
        RootShell.execText("stat -c %s '$path' 2>/dev/null")?.trim()?.toLongOrNull()

    /** 在字节流里找 `mvhd`，按 box 结构读 timescale / duration */
    private fun parseMvhd(b: ByteArray): Long? {
        val asText = String(b, Charsets.ISO_8859_1)
        var from = 0
        while (true) {
            val idx = asText.indexOf("mvhd", from)
            if (idx < 0) return null
            // idx 指向 box 的 type，payload 紧跟其后
            val p = idx + 4
            if (p + 20 <= b.size) {
                val version = b[p].toInt() and 0xFF
                if (version == 1) {
                    if (p + 32 <= b.size) {
                        val ts = readU32(b, p + 20)
                        val dur = readU64(b, p + 24)
                        if (ts > 0) return dur * 1000L / ts
                    }
                } else {
                    if (p + 20 <= b.size) {
                        val ts = readU32(b, p + 12)
                        val dur = readU32(b, p + 16)
                        if (ts > 0) return dur * 1000L / ts
                    }
                }
            }
            from = idx + 4
        }
    }

    private fun readU32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or
            ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or
            (b[at + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, at: Int): Long =
        (readU32(b, at) shl 32) or readU32(b, at + 4)
}
