package com.jiyibi.ledger.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.jiyibi.ledger.data.Categories
import com.jiyibi.ledger.data.LedgerData
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.data.Store
import com.jiyibi.ledger.data.WeekStat
import com.jiyibi.ledger.util.DateUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** 应用状态与业务逻辑 */
class LedgerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var data by mutableStateOf(LedgerData())
        private set

    /** 当前浏览的一周（该周周一） */
    var weekStart by mutableStateOf(DateUtil.mondayOf(DateUtil.today()))
        private set

    var statsType by mutableStateOf("expense")
        private set

    var message by mutableStateOf<String?>(null)

    init {
        data = store.load()
    }

    /* ---------------- 查询 ---------------- */

    fun weekRecords(monday: String): List<Record> {
        val end = DateUtil.shiftDays(monday, 6)
        return data.records.filter { it.date >= monday && it.date <= end }
    }

    fun weekStat(monday: String): WeekStat {
        val list = weekRecords(monday)
        return WeekStat(
            monday = monday,
            expense = list.filter { !it.isIncome }.sumOf { it.amount },
            income = list.filter { it.isIncome }.sumOf { it.amount },
            count = list.size,
            records = list
        )
    }

    /** 指定日期所在月份的支出合计（用于月预算进度） */
    fun monthExpenseOf(date: String): Long {
        val ym = date.substring(0, 7)
        return data.records.filter { it.date.startsWith(ym) && !it.isIncome }.sumOf { it.amount }
    }

    fun isCurrentWeek(): Boolean = weekStart == DateUtil.mondayOf(DateUtil.today())

    /* ---------------- 周导航 ---------------- */

    fun shiftWeek(delta: Int) {
        val thisMonday = DateUtil.mondayOf(DateUtil.today())
        val target = DateUtil.shiftWeeks(weekStart, delta)
        weekStart = if (target > thisMonday) thisMonday else target   // 不允许浏览未来周
    }

    fun goToWeekOf(date: String) {
        val thisMonday = DateUtil.mondayOf(DateUtil.today())
        val target = DateUtil.mondayOf(date)
        weekStart = if (target > thisMonday) thisMonday else target
    }

    fun updateStatsType(type: String) { statsType = type }

    /* ---------------- 实时监测 ---------------- */

    /** 是否已授权通知使用权（每次读取系统设置，无需缓存） */
    fun isNotificationAccessGranted(): Boolean {
        return com.jiyibi.ledger.service.PayNotificationListener
            .isNotificationAccessGranted(getApplication())
    }

    /** 监测开关（持久化到 JSON） */
    fun setMonitorEnabled(enabled: Boolean) {
        data = data.copy(monitorEnabled = enabled); persist()
    }

    /* ---------------- 短信监测 ---------------- */

    /** 是否已授予短信权限（RECEIVE_SMS + READ_SMS） */
    fun isSmsPermissionGranted(): Boolean {
        val ctx = getApplication<Application>()
        val receive = ctx.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val read = ctx.checkSelfPermission(android.Manifest.permission.READ_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return receive && read
    }

    fun setSmsEnabled(enabled: Boolean) {
        data = data.copy(smsEnabled = enabled); persist()
    }

    /** 今日通过监测自动产生的记录数（createdAt 在今天 00:00 之后） */
    fun todayAutoCount(): Int {
        val todayStart = DateUtil.parse(DateUtil.today()).timeInMillis
        return data.records.count { it.createdAt >= todayStart }
    }

    /** 重新从磁盘加载（用于监测服务写入后刷新 UI） */
    fun reloadFromDisk() {
        data = store.load()
    }

    /* ---------------- 每日提醒 ---------------- */

    fun setNotifyEnabled(enabled: Boolean) {
        data = data.copy(notifyEnabled = enabled); persist()
        if (enabled) {
            com.jiyibi.ledger.worker.DailySummaryWorker
                .schedule(getApplication(), data.notifyHour, data.notifyMinute)
        } else {
            com.jiyibi.ledger.worker.DailySummaryWorker.cancel(getApplication())
        }
    }

    fun setNotifyTime(hour: Int, minute: Int) {
        data = data.copy(
            notifyHour = hour.coerceIn(0, 23),
            notifyMinute = minute.coerceIn(0, 59)
        ); persist()
        if (data.notifyEnabled) {
            com.jiyibi.ledger.worker.DailySummaryWorker
                .schedule(getApplication(), data.notifyHour, data.notifyMinute)
        }
    }

    /** 检查是否有系统通知权限（Android 13+ 需 POST_NOTIFICATIONS） */
    fun isPostNotificationsGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            getApplication<Application>().checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    /* ---------------- 记录增删改 ---------------- */

    fun upsert(record: Record) {
        val list = data.records.toMutableList()
        val idx = list.indexOfFirst { it.id == record.id }
        if (idx >= 0) list[idx] = record else list.add(record)
        data = data.copy(records = list)
        persist()
        goToWeekOf(record.date)
    }

    fun delete(id: String) {
        data = data.copy(records = data.records.filter { it.id != id })
        persist()
    }

    fun findById(id: String): Record? = data.records.firstOrNull { it.id == id }

    /* ---------------- 预算 ---------------- */

    fun setWeeklyBudget(cents: Long) {
        data = data.copy(weeklyBudget = cents.coerceAtLeast(0)); persist()
    }

    fun setMonthlyBudget(cents: Long) {
        data = data.copy(monthlyBudget = cents.coerceAtLeast(0)); persist()
    }

    /* ---------------- 软件更新 ---------------- */

    fun setUpdateUrl(url: String) {
        data = data.copy(updateUrl = url.trim()); persist()
    }

    /* ---------------- 外观 ---------------- */

    fun setThemeMode(mode: String) {
        data = data.copy(themeMode = mode); persist()
    }

    fun setThemeColor(colorId: String) {
        data = data.copy(themeColor = colorId); persist()
    }

    /* ---------------- 导入 ---------------- */

    fun importRecords(records: List<Record>) {
        if (records.isEmpty()) return
        data = data.copy(records = data.records + records)
        persist()
        records.maxByOrNull { it.date }?.let { goToWeekOf(it.date) }
    }

    /** 合并 JSON 备份，返回新增条数 */
    fun mergeJson(text: String): Int {
        return try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("records") ?: JSONArray()
            val incoming = ArrayList<Record>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                Record.from(o)?.let { incoming.add(it) }
            }
            if (incoming.isEmpty()) return 0

            val seen = data.records.map { dedupKey(it) }.toMutableSet()
            val merged = data.records.toMutableList()
            var added = 0
            incoming.forEach {
                if (seen.add(dedupKey(it))) { merged.add(it); added++ }
            }
            val wb = root.optLong("weekly", 0)
            val mb = root.optLong("monthly", 0)
            data = data.copy(
                records = merged,
                weeklyBudget = if (wb > 0) wb else data.weeklyBudget,
                monthlyBudget = if (mb > 0) mb else data.monthlyBudget
            )
            persist()
            merged.maxByOrNull { it.date }?.let { goToWeekOf(it.date) }
            added
        } catch (e: Exception) {
            0
        }
    }

    private fun dedupKey(r: Record) = "${r.date}|${r.type}|${r.amount}|${r.note}"

    /* ---------------- 导出 ---------------- */

    fun exportJson(): File {
        val f = store.newExportFile(getApplication(), "记一笔-备份-${DateUtil.today()}.json")
        val arr = JSONArray()
        data.records.sortedBy { it.date }.forEach { arr.put(it.toJson()) }
        val root = JSONObject().apply {
            put("app", "jiyibi")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("weekly", data.weeklyBudget)
            put("monthly", data.monthlyBudget)
            put("records", arr)
        }
        f.writeText(root.toString(2), Charsets.UTF_8)
        return f
    }

    fun exportCsv(): File {
        val f = store.newExportFile(getApplication(), "记一笔-明细-${DateUtil.today()}.csv")
        val sb = StringBuilder("日期,类型,分类,金额(元),账户,备注\n")
        data.records.sortedBy { it.date }.forEach { r ->
            sb.append(csv(r.date)).append(',')
                .append(csv(if (r.isIncome) "收入" else "支出")).append(',')
                .append(csv(Categories.find(r.type, r.category).name)).append(',')
                .append(String.format(Locale.US, "%.2f", r.amount / 100.0)).append(',')
                .append(csv(r.account)).append(',')
                .append(csv(r.note)).append('\n')
        }
        f.writeText("\uFEFF" + sb, Charsets.UTF_8)   // BOM 便于 Excel 识别中文
        return f
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\"" else s

    /* ---------------- 清空 ---------------- */

    fun clearAll() {
        data = LedgerData()
        persist()
    }

    private fun persist() { store.save(data) }

    fun toast(msg: String) { message = msg }

    fun consumeMessage() { message = null }
}
