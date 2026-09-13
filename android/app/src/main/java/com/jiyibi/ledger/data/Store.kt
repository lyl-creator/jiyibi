package com.jiyibi.ledger.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 数据持久化：以 JSON 文件保存在应用内部存储，完全离线 */
class Store(context: Context) {

    private val file = File(context.filesDir, "ledger.json")

    /**
     * 最近一次成功读写的快照。
     *
     * 用于区分「文件确实为空」与「本次读取失败」：前者是用户清空，后者是并发读写
     * 或磁盘异常，此时应保留上次的数据而不是把界面清空（否则下一次保存会把空数据落盘）。
     */
    @Volatile
    private var lastGood: LedgerData? = null

    fun load(): LedgerData {
        if (!file.exists()) return LedgerData()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("records") ?: JSONArray()
            val records = ArrayList<Record>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                Record.from(o)?.let { records.add(it) }
            }
            LedgerData(
                records = records,
                weeklyBudget = root.optLong("weekly", 0).coerceAtLeast(0),
                monthlyBudget = root.optLong("monthly", 0).coerceAtLeast(0),
                monitorEnabled = root.optBoolean("monitor", false),
                smsEnabled = root.optBoolean("sms", false),
                notifyEnabled = root.optBoolean("notify", false),
                notifyHour = root.optInt("notifyHour", 8).coerceIn(0, 23),
                notifyMinute = root.optInt("notifyMinute", 0).coerceIn(0, 59),
                updateUrl = normalizeUpdateUrl(root.optString("updateUrl", DEFAULT_UPDATE_URL)),
                themeMode = root.optString("themeMode", "system"),
                themeColor = root.optString("themeColor", "purple")
            ).also { lastGood = it }
        } catch (e: Exception) {
            // 解析失败视为读取异常，回退到上次成功快照，绝不返回空账本
            lastGood ?: LedgerData()
        }
    }

    fun save(data: LedgerData) {
        try {
            val arr = JSONArray()
            data.records.forEach { arr.put(it.toJson()) }
            val root = JSONObject().apply {
                put("version", 2)
                put("records", arr)
                put("weekly", data.weeklyBudget)
                put("monthly", data.monthlyBudget)
                put("monitor", data.monitorEnabled)
                put("sms", data.smsEnabled)
                put("notify", data.notifyEnabled)
                put("notifyHour", data.notifyHour)
                put("notifyMinute", data.notifyMinute)
                put("updateUrl", data.updateUrl)
                put("themeMode", data.themeMode)
                put("themeColor", data.themeColor)
                put("savedAt", System.currentTimeMillis())
            }
            val json = root.toString()

            // 先写临时文件再原子替换，避免读写并发时读到半截 JSON
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                // 个别文件系统不支持覆盖式重命名，退化为直接写入
                file.writeText(json, Charsets.UTF_8)
                tmp.delete()
            }
            lastGood = data
        } catch (e: Exception) {
            // 写入失败时不中断界面操作
        }
    }

    /** 生成一个位于缓存目录的导出文件，便于通过系统分享或文件管理器取出 */
    fun newExportFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "export")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    /**
     * 归一化更新源地址。
     *
     * 历史版本（≤2.4.0）默认使用自建站点 weekly-ledger.app.workbuddy.host，
     * 该过渡桥已废弃，升级到本版本后自动迁移到 GitHub 源，避免继续依赖旧站点。
     */
    private fun normalizeUpdateUrl(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return DEFAULT_UPDATE_URL
        return if (url.contains("weekly-ledger.app.workbuddy.host", ignoreCase = true)) {
            DEFAULT_UPDATE_URL
        } else {
            url
        }
    }
}
