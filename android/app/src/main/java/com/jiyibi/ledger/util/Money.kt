package com.jiyibi.ledger.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 金额工具：内部一律以「分」为单位存储，规避浮点误差 */
object Money {

    private val nf: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** 分 → "1,234.56" */
    fun format(cents: Long): String = nf.format(cents / 100.0)

    /** 分 → "1.2万"，用于图表等空间受限处 */
    fun short(cents: Long): String {
        val v = cents / 100.0
        if (abs(v) < 10000) return v.roundToLong().toString()
        val w = v / 10000.0
        return if (w == w.roundToLong().toDouble()) "${w.roundToLong()}万"
        else String.format(Locale.US, "%.1f万", w)
    }

    /** 元字符串 → 分 */
    fun parseYuan(text: String): Long {
        val cleaned = text.replace(",", "").replace("¥", "").trim()
        if (cleaned.isEmpty()) return 0
        return (cleaned.toDoubleOrNull() ?: 0.0).times(100).roundToLong()
    }

    /** 从账单文本中提取金额（分），忽略货币符号等杂质 */
    fun parseFromBill(text: String): Long {
        val cleaned = text.replace(Regex("[^0-9.\\-]"), "")
        if (cleaned.isEmpty()) return 0
        return abs((cleaned.toDoubleOrNull() ?: 0.0).times(100).roundToLong())
    }
}
