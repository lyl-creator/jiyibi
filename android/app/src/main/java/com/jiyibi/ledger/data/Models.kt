package com.jiyibi.ledger.data

import org.json.JSONObject
import java.util.UUID

/** 一条收支记录。金额以「分」存储。 */
data class Record(
    val id: String,
    val type: String,        // "expense" 支出 | "income" 收入
    val amount: Long,        // 分
    val category: String,
    val date: String,        // yyyy-MM-dd
    val note: String,
    val account: String,
    val createdAt: Long
) {
    val isIncome: Boolean get() = type == "income"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("amount", amount)
        put("category", category)
        put("date", date)
        put("note", note)
        put("account", account)
        put("createdAt", createdAt)
    }

    companion object {
        private val DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")

        fun from(o: JSONObject): Record? {
            val amount = o.optLong("amount")
            if (amount <= 0) return null
            val date = o.optString("date")
            if (!DATE_RE.matches(date)) return null
            val type = if (o.optString("type") == "income") "income" else "expense"
            return Record(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                type = type,
                amount = amount,
                category = o.optString("category").ifEmpty {
                    if (type == "income") "other_i" else "other_e"
                },
                date = date,
                note = o.optString("note"),
                account = o.optString("account"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/** 全部持久化数据 */
data class LedgerData(
    val records: List<Record> = emptyList(),
    val weeklyBudget: Long = 0,
    val monthlyBudget: Long = 0,
    /** 是否开启实时收支监测（通知监听） */
    val monitorEnabled: Boolean = false,
    /** 是否开启银行短信监测（短信自动记账） */
    val smsEnabled: Boolean = false,
    /** 是否开启每日昨日收支提醒 */
    val notifyEnabled: Boolean = false,
    /** 提醒时间：小时（0-23） */
    val notifyHour: Int = 8,
    /** 提醒时间：分钟（0-59） */
    val notifyMinute: Int = 0,
    /** 软件更新检查地址（version.json 的 URL） */
    val updateUrl: String =
        "https://github.com/lyl-creator/jiyibi/releases/latest/download/version.json",
    /** 主题模式：system 跟随系统 / light 白天 / dark 夜间 */
    val themeMode: String = "system",
    /** 主题色标识，对应 ui.theme.brandPalettes 中的 id */
    val themeColor: String = "purple"
)

/** 某一周的汇总 */
data class WeekStat(
    val monday: String,
    val expense: Long,
    val income: Long,
    val count: Int,
    val records: List<Record>
) {
    val balance: Long get() = income - expense
}
