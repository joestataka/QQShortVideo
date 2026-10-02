package com.java.myapplication.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.File

/**
 * QQ shortvideo 缓存目录格式（实测）：
 *
 *   shortvideo/
 *   ├── <32位哈希>/<32位哈希>.mp4   视频本体（每个目录最多一个，文件名 = 目录名）
 *   ├── thumbs/<32位哈希>_0.png     封面图；**很多封面对应的视频已被清理**，只剩封面
 *   └── Temp/                       空
 *
 * 实测数量：哈希目录 5340、真实视频 730、封面 4021，
 * 其中「有封面但无视频」3292 个 —— 所以必须以**实际视频文件**为准，不能以封面为准。
 *
 * 读取策略：优先 File API（需「所有文件访问权限」）；读不到则回退 root。
 */
enum class Source { FILE, ROOT }

data class VideoItem(
    /** 32 位哈希（目录名 = 视频文件名） */
    val id: String,
    /** 视频文件绝对路径 */
    val path: String,
    /** 封面 png 路径；为空表示没有封面（需要运行时抽帧/占位） */
    val coverPath: String,
    /** 视频文件大小（字节） */
    val sizeBytes: Long,
    /** 修改时间文本 yyyy-MM-dd HH:mm */
    val timeText: String,
    val source: Source
)

object VideoScanner {

    private const val TAG = "QQShort"

    const val DEFAULT_ROOT =
        "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/MobileQQ/shortvideo"

    private const val THUMB_DIR = "thumbs"
    private val HASH_REGEX = Regex("^[0-9a-f]{32}$")
    private val VIDEO_EXT = listOf("mp4", "m4v", "mov", "3gp", "mkv", "webm")

    fun scan(rootPath: String = DEFAULT_ROOT): List<VideoItem> {
        RootShell.fileLog("scan start")
        scanByFile(rootPath)?.let {
            RootShell.fileLog("scan via FILE: ${it.size}")
            return it
        }
        val r = scanByRoot(rootPath)
        RootShell.fileLog("scan via ROOT: ${r.size}")
        return r
    }

    /** File API 扫描：遍历哈希子目录找视频文件，封面从 thumbs 取（存在才算）。 */
    private fun scanByFile(rootPath: String): List<VideoItem>? {
        val root = File(rootPath)
        if (!root.isDirectory || !root.canRead()) return null
        val dirs = root.listFiles() ?: return null

        // 已有的封面集合
        val covers = File(root, THUMB_DIR).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".png") }
            ?.associateBy { it.name.substringBeforeLast('_') }
            ?: emptyMap()

        val out = ArrayList<VideoItem>()
        for (d in dirs) {
            if (!d.isDirectory || !HASH_REGEX.matches(d.name)) continue
            val video = d.listFiles()?.firstOrNull {
                it.isFile && it.extension.lowercase() in VIDEO_EXT
            } ?: continue
            out += VideoItem(
                id = d.name,
                path = video.absolutePath,
                coverPath = covers[d.name]?.absolutePath ?: "",
                sizeBytes = video.length(),
                timeText = fmt(video.lastModified()),
                source = Source.FILE
            )
        }
        return if (out.isEmpty()) null else out.sortedByDescending { it.timeText }
    }

    /**
     * root 扫描：一条命令列出所有视频（含大小/时间），再用 `ls thumbs` 取封面集合。
     */
    private fun scanByRoot(rootPath: String): List<VideoItem> {
        val findCmd = buildString {
            append("find '").append(rootPath).append("' -maxdepth 2 -type f \\(")
            VIDEO_EXT.forEachIndexed { i, e ->
                if (i > 0) append(" -o")
                append(" -iname '*.").append(e).append("'")
            }
            append(" \\) -exec ls -l {} +")
        }
        val listing = RootShell.execText(findCmd) ?: return emptyList()

        // 封面集合（hash -> true）
        val coverSet = RootShell.execText("ls '$rootPath/$THUMB_DIR'")
            ?.lines()
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.endsWith(".png") }
            ?.map { it.substringBeforeLast('_') }
            ?.toHashSet()
            ?: emptySet()

        val out = ArrayList<VideoItem>()
        listing.lines().forEach { raw ->
            val parts = raw.trim().split(' ').filter { it.isNotBlank() }
            // toybox ls -l 共 8 列：mode links owner group size date time name
            if (parts.size < 8) return@forEach
            val full = parts[parts.size - 1]
            if (!full.startsWith(rootPath)) return@forEach
            val id = full.substringBeforeLast('/').substringAfterLast('/')
            if (!HASH_REGEX.matches(id)) return@forEach
            out += VideoItem(
                id = id,
                path = full,
                coverPath = if (id in coverSet) "$rootPath/$THUMB_DIR/${id}_0.png" else "",
                sizeBytes = parts[4].toLongOrNull() ?: 0L,
                timeText = "${parts[5]} ${parts[6]}",
                source = Source.ROOT
            )
        }
        return out.sortedByDescending { it.timeText }
    }

    private fun fmt(ms: Long): String {
        val s = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return s.format(java.util.Date(ms))
    }
}

/** 封面加载 + 内存 LRU 缓存。务必在工作线程调用。 */
object ThumbnailCache {

    private const val TARGET_WIDTH = 320

    private val cache = object : LruCache<String, Bitmap>(maxSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun maxSize(): Int =
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(8 * 1024 * 1024)

    fun get(item: VideoItem): Bitmap? {
        if (item.coverPath.isEmpty()) return null
        cache.get(item.id)?.let { return it }
        val bmp = when (item.source) {
            Source.FILE -> decodeFile(File(item.coverPath))
            Source.ROOT -> RootShell.execBytes("cat '${item.coverPath}'")?.let { decodeBytes(it) }
        } ?: return null
        cache.put(item.id, bmp)
        return bmp
    }

    private fun decodeFile(file: File): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth)
        })
    } catch (t: Throwable) {
        null
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth)
        })
    } catch (t: Throwable) {
        null
    }

    private fun sample(width: Int): Int {
        var s = 1
        while (width / s > TARGET_WIDTH * 2) s *= 2
        return s
    }
}