package com.jiyibi.ledger.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知处理日志。
 *
 * 用于诊断「手机上明明收到了支付通知，应用里却没有记录」这类问题：
 * 每条被判定为支付/银行类的通知都会留痕，并写明处理结果或未记账的原因，
 * 用户可在「设置 → 通知诊断」中直接核对。
 *
 * 仅保存在本机 SharedPreferences，最多保留 [MAX] 条，超出后丢弃最旧记录。
 */
object NotifyLog {

    data class Entry(
        val time: Long,
        val app: String,
        val title: String,
        val text: String,
        val result: String,
        val ok: Boolean
    )

    private const val MAX = 30
    private const val PREF = "notify_log"
    private const val KEY = "entries"

    private val entries = ArrayDeque<Entry>()

    @Volatile
    private var loaded = false

    /** 记录一条通知的处理结果 */
    @Synchronized
    fun add(
        context: Context,
        app: String,
        title: String?,
        text: String?,
        result: String,
        ok: Boolean
    ) {
        ensureLoaded(context)
        entries.addFirst(
            Entry(
                time = System.currentTimeMillis(),
                app = app,
                title = title.orEmpty().take(60),
                text = text.orEmpty().take(160),
                result = result,
                ok = ok
            )
        )
        while (entries.size > MAX) entries.removeLast()
        persist(context)
    }

    /** 读取全部日志（新的在前） */
    @Synchronized
    fun list(context: Context): List<Entry> {
        ensureLoaded(context)
        return entries.toList()
    }

    /** 清空日志 */
    @Synchronized
    fun clear(context: Context) {
        entries.clear()
        loaded = true
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** 时间戳 → "09-13 22:30:15" */
    fun formatTime(time: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))

    /* ---------------- 持久化 ---------------- */

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                entries.addLast(
                    Entry(
                        time = o.optLong("t", 0L),
                        app = o.optString("app", ""),
                        title = o.optString("title", ""),
                        text = o.optString("text", ""),
                        result = o.optString("result", ""),
                        ok = o.optBoolean("ok", false)
                    )
                )
            }
        } catch (e: Exception) {
            entries.clear()   // 日志损坏时直接丢弃，不影响主流程
        }
    }

    private fun persist(context: Context) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("t", e.time)
                        put("app", e.app)
                        put("title", e.title)
                        put("text", e.text)
                        put("result", e.result)
                        put("ok", e.ok)
                    }
                )
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, arr.toString())
                .apply()
        } catch (e: Exception) {
            // 日志写入失败不影响记账
        }
    }
}
