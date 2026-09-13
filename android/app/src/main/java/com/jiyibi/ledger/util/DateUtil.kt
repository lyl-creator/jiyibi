package com.jiyibi.ledger.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 日期工具。
 * 应用以「周」为周期，一周从周一开始、周日结束。
 */
object DateUtil {

    fun today(): String = format(System.currentTimeMillis())

    fun format(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun parse(date: String): Calendar {
        val p = date.split("-")
        return Calendar.getInstance().apply {
            clear()
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        }
    }

    /** 该日期所在周的周一 */
    fun mondayOf(date: String): String {
        val c = parse(date)
        val dow = c.get(Calendar.DAY_OF_WEEK)          // 周日 = 1，周六 = 7
        val delta = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
        c.add(Calendar.DAY_OF_MONTH, delta)
        return format(c.timeInMillis)
    }

    fun shiftDays(date: String, days: Int): String {
        val c = parse(date)
        c.add(Calendar.DAY_OF_MONTH, days)
        return format(c.timeInMillis)
    }

    fun shiftWeeks(monday: String, weeks: Int): String = shiftDays(monday, weeks * 7)

    /** "9/7" 形式的短日期 */
    fun md(date: String): String {
        val p = date.split("-")
        return "${p[1].toInt()}/${p[2].toInt()}"
    }

    /** "9/7–9/13"；跨年时补上年份 */
    fun weekRangeLabel(monday: String): String {
        val sunday = shiftDays(monday, 6)
        val y1 = monday.substring(0, 4)
        val y2 = sunday.substring(0, 4)
        return if (y1 != y2) "$y1/${md(monday)}–$y2/${md(sunday)}"
        else "${md(monday)}–${md(sunday)}"
    }

    /** 顶部标题：本周 / 上周 前缀 */
    fun weekTitle(monday: String): String {
        val thisMonday = mondayOf(today())
        val range = weekRangeLabel(monday)
        return when (monday) {
            thisMonday -> "本周 · $range"
            shiftWeeks(thisMonday, -1) -> "上周 · $range"
            else -> range
        }
    }

    fun isCurrentWeek(monday: String): Boolean = monday == mondayOf(today())

    fun dayLabel(date: String): String {
        val c = parse(date)
        val base = "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
        return when (date) {
            today() -> "今天 · $base"
            shiftDays(today(), -1) -> "昨天 · $base"
            else -> "$base 周${weekdayCn(date)}"
        }
    }

    private fun weekdayCn(date: String): String {
        val idx = parse(date).get(Calendar.DAY_OF_WEEK)
        return "日一二三四五六"[idx - 1].toString()
    }

    /** 用于比较的数值键 */
    fun key(date: String): Int = date.replace("-", "").toInt()

    /**
     * DatePicker 的 selectedDateMillis 表示的是以 UTC 计的当日零点，
     * 需按 UTC 时区还原，才能得到用户实际点选的日历日期。
     */
    fun fromUtcMillis(millis: Long): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.timeInMillis = millis
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }
}
