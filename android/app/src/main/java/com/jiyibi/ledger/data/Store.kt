package com.jiyibi.ledger.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 数据持久化：以 JSON 文件保存在应用内部存储，完全离线 */
class Store(context: Context) {

    private val file = File(context.filesDir, "ledger.json")

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
                updateUrl = root.optString(
                    "updateUrl",
                    "https://github.com/lyl-creator/jiyibi/releases/latest/download/version.json"
                ),
                themeMode = root.optString("themeMode", "system"),
                themeColor = root.optString("themeColor", "purple")
            )
        } catch (e: Exception) {
            LedgerData()
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
            file.writeText(root.toString(), Charsets.UTF_8)
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
}
