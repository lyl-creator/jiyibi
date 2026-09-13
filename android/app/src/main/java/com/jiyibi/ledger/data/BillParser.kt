package com.jiyibi.ledger.data

import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money
import java.util.UUID

/**
 * 微信 / 支付宝账单 CSV 解析。
 * 自动定位表头、判断收支方向、跳过转账与还款类记录，并按关键词推断分类。
 */
object BillParser {

    data class Result(val records: List<Record>, val skipped: Int)

    private data class Cols(
        val head: Int, val date: Int, val amount: Int, val flow: Int,
        val party: Int, val goods: Int, val type: Int, val status: Int, val method: Int
    )

    private val EXPENSE_KEYWORDS = listOf(
        Regex("餐|饭|美团|饿了么|外卖|肯德基|麦当劳|星巴克|咖啡|奶茶|零食|水果|生鲜|食堂|烧烤|火锅|小吃") to "food",
        Regex("地铁|公交|出租|滴滴|打车|网约车|加油|停车|高速|高铁|火车|动车|机票|航空|单车|摩拜|哈啰|青桔") to "transport",
        Regex("淘宝|天猫|京东|拼多多|唯品会|苏宁|商场|百货|服饰|化妆品|护肤|数码|超市|便利店|购物") to "shopping",
        Regex("房租|租金|水费|电费|燃气|物业|家居|家具|家电|装修") to "home",
        Regex("话费|流量|宽带|中国移动|中国联通|中国电信|通信") to "telecom",
        Regex("电影|游戏|娱乐|视频会员|腾讯视频|爱奇艺|优酷|芒果|音乐|演出|门票|剧本|酒吧") to "entertain",
        Regex("医院|药店|药房|诊所|挂号|体检|医疗|门诊|口腔|牙科|医药") to "medical",
        Regex("书店|图书|课程|培训|学费|教育|知识|考试|报名费|文具") to "study",
        Regex("红包|礼金|人情|份子|贺礼|随礼") to "social",
        Regex("健身|运动|游泳|球馆|跑步|瑜伽|体育") to "sport",
        Regex("宠物|猫|狗|猫粮|狗粮") to "pet"
    )

    private val INCOME_KEYWORDS = listOf(
        Regex("工资|薪资|薪酬|劳务") to "salary",
        Regex("奖金|绩效|年终|提成|奖励") to "bonus",
        Regex("兼职|稿费|酬劳|外快|私活") to "parttime",
        Regex("理财|基金|股票|收益|利息|分红|余额宝|零钱通") to "invest",
        Regex("红包|礼金|压岁") to "redpack",
        Regex("报销|差旅|补贴") to "reimburse",
        Regex("退款|退货|返现") to "refund"
    )

    private val SKIP_FLOW = Regex("不计收支")
    private val SKIP_STATUS = Regex("已全额退款|已退款|交易关闭|失败|已撤销")
    private val SKIP_TEXT = Regex("转账|还款|零钱通|余额宝转|信用卡还款|提现|亲情卡")
    private val DATE_RE = Regex("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})")

    /** 按 UTF-8 解码；出现乱码则回退 GBK（微信及部分银行账单为该编码） */
    fun readText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return try {
            String(bytes, charset("GBK"))
        } catch (e: Exception) {
            utf8
        }
    }

    /** 解析账单；返回 null 表示未识别到表头 */
    fun parse(text: String): Result? {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return null
        val cols = locateHeader(rows) ?: return null

        val out = ArrayList<Record>()
        var skipped = 0

        for (i in cols.head + 1 until rows.size) {
            val row = rows[i]
            if (row.size <= cols.amount || row.size <= cols.date) continue

            val m = DATE_RE.find(row[cols.date].trim()) ?: continue
            val date = "%04d-%02d-%02d".format(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
            )

            val amount = Money.parseFromBill(row[cols.amount])
            if (amount <= 0) continue

            val typeRaw = row.getOrElse(cols.type) { "" }
            val flowRaw = row.getOrElse(cols.flow) { "" }.trim()
            val party = row.getOrElse(cols.party) { "" }.trim()
            val goods = row.getOrElse(cols.goods) { "" }.trim()
            val status = row.getOrElse(cols.status) { "" }

            if (SKIP_STATUS.containsMatchIn(status)) { skipped++; continue }
            if (SKIP_FLOW.containsMatchIn(flowRaw)) { skipped++; continue }

            val isIncome = when {
                flowRaw.contains("支出") -> false
                flowRaw.contains("收入") -> true
                typeRaw.contains("收入") -> true
                else -> { skipped++; continue }
            }

            val all = "$typeRaw $goods $party"
            if (SKIP_TEXT.containsMatchIn(all)) { skipped++; continue }

            out.add(
                Record(
                    id = UUID.randomUUID().toString(),
                    type = if (isIncome) "income" else "expense",
                    amount = amount,
                    category = guessCategory(all, isIncome),
                    date = date,
                    note = (if (goods.isNotEmpty()) goods else party).take(40),
                    account = mapAccount(row.getOrElse(cols.method) { "" }),
                    createdAt = DateUtil.parse(date).timeInMillis + i
                )
            )
        }

        return Result(out, skipped)
    }

    /* ---------------- 内部实现 ---------------- */

    private fun List<String>.getOrElse(index: Int, fallback: () -> String): String =
        if (index in indices) this[index] else fallback()

    private fun parseCsv(text: String): List<List<String>> {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val first = lines[0]
        val delim = if (first.count { it == '\t' } > first.count { it == ',' }) '\t' else ','
        return lines.map { parseLine(it, delim) }
    }

    private fun parseLine(line: String, delim: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ }
                    else inQuote = false
                } else cur.append(ch)
            } else {
                when {
                    ch == '"' -> inQuote = true
                    ch == delim -> { out.add(cur.toString()); cur.setLength(0) }
                    else -> cur.append(ch)
                }
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun locateHeader(rows: List<List<String>>): Cols? {
        val ws = Regex("\\s")
        for (i in 0 until minOf(rows.size, 30)) {
            val row = rows[i]
            if (row.size < 3) continue
            if (!Regex("(交易时间|交易日期|交易创建时间|记账日期|日期)").containsMatchIn(row.joinToString(",")))
                continue

            var date = -1; var amount = -1; var flow = -1; var party = -1
            var goods = -1; var type = -1; var status = -1; var method = -1

            for (j in row.indices) {
                val c = row[j].replace(ws, "")
                if (date < 0 && Regex("(交易时间|交易日期|交易创建时间|记账日期|^日期$|^时间$)").containsMatchIn(c)) date = j
                if (amount < 0 && c.contains("金额")) amount = j
                if (flow < 0 && Regex("(收/支|收支|收付|借贷标志|资金状态)").containsMatchIn(c)) flow = j
                if (party < 0 && Regex("(交易对方|对方|商户名称|收款方)").containsMatchIn(c)) party = j
                if (goods < 0 && Regex("(商品|商品说明|商品名称|摘要|备注说明)").containsMatchIn(c)) goods = j
                if (type < 0 && Regex("(交易类型|类型|业务类型)").containsMatchIn(c)) type = j
                if (status < 0 && Regex("(当前状态|交易状态|状态)").containsMatchIn(c)) status = j
                if (method < 0 && Regex("(支付方式|付款方式|收付款方式|资金渠道)").containsMatchIn(c)) method = j
            }

            if (date >= 0 && amount >= 0) {
                return Cols(i, date, amount, flow, party, goods, type, status, method)
            }
        }
        return null
    }

    private fun guessCategory(text: String, isIncome: Boolean): String {
        val list = if (isIncome) INCOME_KEYWORDS else EXPENSE_KEYWORDS
        for ((re, id) in list) if (re.containsMatchIn(text)) return id
        return if (isIncome) "other_i" else "other_e"
    }

    private fun mapAccount(text: String): String {
        if (text.isBlank()) return ""
        return when {
            Regex("零钱|微信").containsMatchIn(text) -> "微信"
            Regex("支付宝|余额宝|花呗|借呗").containsMatchIn(text) -> "支付宝"
            Regex("信用卡").containsMatchIn(text) -> "信用卡"
            Regex("储蓄卡|借记卡|银行卡|银行").containsMatchIn(text) -> "银行卡"
            Regex("现金").containsMatchIn(text) -> "现金"
            else -> ACCOUNTS.firstOrNull { text.contains(it) } ?: ""
        }
    }
}
