package com.jiyibi.ledger.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.jiyibi.ledger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：检查 version.json → 下载 APK → 触发系统安装。
 *
 * version.json 格式示例：
 * {
 *   "versionCode": 22000,
 *   "versionName": "2.2.0",
 *   "downloadUrl": "https://example.com/app-release.apk",
 *   "changelog": "新增xxx功能"
 * }
 */
object UpdateManager {

    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val changelog: String
    )

    /** 当前应用版本号 */
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** 从指定 URL 获取版本信息；失败返回 null。支持 .json 文件或 HTML 内嵌 JSON。 */
    suspend fun fetchVersionInfo(url: String): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json, text/html;q=0.9")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            conn.disconnect()

            // 先尝试直接解析为 JSON（.json 文件或 API 返回）
            val jsonText = extractJson(body) ?: return@withContext null
            val json = JSONObject(jsonText)
            val code = json.optInt("versionCode", 0)
            val name = json.optString("versionName", "")
            val download = json.optString("downloadUrl", "")
            val log = json.optString("changelog", "")
            if (code <= 0 || name.isEmpty() || download.isEmpty()) return@withContext null
            VersionInfo(code, name, download, log)
        } catch (e: Exception) {
            null
        }
    }

    /** 从文本中提取 JSON：支持纯 JSON 或 HTML 中 <script type="application/json"> 内嵌 */
    private fun extractJson(body: String): String? {
        val trimmed = body.trim()
        // 纯 JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        // HTML 内嵌：<script type="application/json">...</script>
        val scriptRe = Regex(
            """<script[^>]*type\s*=\s*["']application/json["'][^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        scriptRe.find(body)?.let { return it.groupValues[1].trim() }
        // 兜底：找第一个 { 到最后一个 }
        val first = body.indexOf('{')
        val last = body.lastIndexOf('}')
        if (first >= 0 && last > first) return body.substring(first, last + 1)
        return null
    }

    /** 使用系统 DownloadManager 下载 APK；返回 downloadId */
    fun startDownload(context: Context, info: VersionInfo): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("记一笔 ${info.versionName}")
            .setDescription("正在下载更新...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "jiyibi-${info.versionName}.apk"
            )
            .setMimeType("application/vnd.android.package-archive")
        return dm.enqueue(request)
    }

    /** 查询下载进度（0-100）；-1 表示失败或不存在 */
    fun queryProgress(context: Context, downloadId: Long): Int {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return -1
        return try {
            if (!cursor.moveToFirst()) return -1
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_FAILED) return -1
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            cursor.close()
            if (total <= 0) 0 else (downloaded * 100 / total).toInt()
        } catch (e: Exception) {
            -1
        } finally {
            if (!cursor.isClosed) cursor.close()
        }
    }

    /** 下载完成后触发系统安装 */
    fun installApk(context: Context, info: VersionInfo) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "jiyibi-${info.versionName}.apk")
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** 注册下载完成广播；返回 unregister  lambda */
    fun registerDownloadReceiver(
        context: Context,
        downloadId: Long,
        onComplete: () -> Unit
    ): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    onComplete()
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
        return { context.unregisterReceiver(receiver) }
    }
}
