package com.sundys.zrmt

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub 更新检查与下载：按“自定义代理 → 直连 → 内置镜像”顺序探测，
 * 任一环节成功即使用该通道；下载失败自动换下一个通道重试。
 */
object Updater {

    const val REPO = "sundys/Zrmt"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** 内置 GitHub 加速镜像（前缀拼接完整原始 URL） */
    private val MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.net/"
    )

    private const val CONNECT_TIMEOUT = 6000
    private const val API_READ_TIMEOUT = 8000
    private const val DL_READ_TIMEOUT = 30000

    data class ReleaseInfo(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String,
        val via: String
    )

    /** 通道列表：用户自定义代理优先，其次直连，最后内置镜像 */
    private fun bases(context: Context): List<String> {
        val list = ArrayList<String>()
        val custom = AppPrefs.githubProxy(context).trim()
        if (custom.isNotEmpty()) {
            list.add(if (custom.endsWith("/")) custom else "$custom/")
        }
        list.add("")
        list.addAll(MIRRORS)
        return list
    }

    private fun open(base: String, url: String, readTimeout: Int, acceptJson: Boolean): HttpURLConnection {
        val conn = (URL(base + url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            this.readTimeout = readTimeout
            setRequestProperty("User-Agent", "Zrmt-app")
            if (acceptJson) setRequestProperty("Accept", "application/vnd.github+json")
        }
        return conn
    }

    /** 获取最新 Release；全部通道失败返回 null */
    fun fetchLatest(context: Context): ReleaseInfo? {
        for (base in bases(context)) {
            var conn: HttpURLConnection? = null
            try {
                conn = open(base, API_URL, API_READ_TIMEOUT, acceptJson = true)
                if (conn.responseCode != 200) continue
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                val tag = obj.optString("tag_name")
                if (tag.isEmpty()) continue
                var apkUrl = ""
                val assets = obj.optJSONArray("assets")
                if (assets != null) {
                    val want = preferredAbi()
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        val url = a.optString("browser_download_url")
                        if (url.isEmpty() || !name.endsWith(".apk")) continue
                        if (name.contains(want)) { apkUrl = url; break }
                        if (apkUrl.isEmpty()) apkUrl = url
                    }
                }
                if (apkUrl.isEmpty()) continue
                val via = if (base.isEmpty()) "直连" else base.removePrefix("https://").removeSuffix("/")
                return ReleaseInfo(
                    version = tag,
                    notes = obj.optString("body"),
                    pageUrl = obj.optString("html_url", "https://github.com/$REPO/releases"),
                    apkUrl = apkUrl,
                    via = via
                )
            } catch (_: Exception) {
                // 换下一个通道
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * 下载 APK 到 files/updates/zrmt-update.apk。
     * onProgress(percent, doneBytes, totalBytes)；total<=0 表示未知大小（percent 传 -1）。
     * cancelled() 返回 true 时中止并删除临时文件、返回 null。
     */
    fun download(
        context: Context,
        url: String,
        onProgress: (Int, Long, Long) -> Unit,
        cancelled: () -> Boolean
    ): File? {
        for (base in bases(context)) {
            var conn: HttpURLConnection? = null
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val tmp = File(dir, "zrmt-update.apk.part")
            try {
                conn = open(base, url, DL_READ_TIMEOUT, acceptJson = false)
                if (conn.responseCode != 200) continue
                val total = conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled()) {
                                tmp.delete()
                                return null
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done * 100 / total).toInt(), done, total)
                            else onProgress(-1, done, -1)
                        }
                    }
                }
                val file = File(dir, "zrmt-update.apk")
                if (tmp.renameTo(file)) return file
                return null
            } catch (_: Exception) {
                tmp.delete()
                // 换下一个通道重试
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /** 当前设备优先的 APK 架构标识（与发布资产文件名匹配） */
    private fun preferredAbi(): String =
        if (Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) "arm64-v8a" else "armeabi-v7a"

    /** 语义化版本比较（忽略 v 前缀与残留非数字字符） */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.trim().removePrefix("v").removePrefix("V").split('.')
        val c = current.trim().split('.')
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val cv = c.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (rv != cv) return rv > cv
        }
        return false
    }
}
