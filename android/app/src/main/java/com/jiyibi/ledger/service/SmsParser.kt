package com.jiyibi.ledger.service

import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.util.Money
import java.util.UUID

/**
 * 银行交易短信解析器。
 *
 * 覆盖主流银行的通知格式，提取交易金额、收支方向、卡号尾号，
 * 并按关键词推断分类。核心难点是从含「余额」的短信中正确区分交易金额与账户余额。
 */
object SmsParser {

    data class Parsed(
        val record: Record,
        val bank: String,
        val cardTail: String
    )

    /** 银行客服短号 → 银行名称 */
    private val BANK_BY_NUMBER = mapOf(
        "95588" to "工商银行",
        "95555" to "招商银行",
        "95533" to "建设银行",
        "95599" to "农业银行",
        "95566" to "中国银行",
        "95559" to "交通银行",
        "95528" to "浦发银行",
        "95577" to "华夏银行",
        "95561" to "兴业银行",
        "95595" to "光大银行",
        "95568" to "民生银行",
        "95511" to "平安银行",
        "95558" to "中信银行",
        "95508" to "广发银行",
        "95580" to "邮储银行",
        "95594" to "上海银行",
        "95501" to "上海银行",
        "96666" to "农村信用社",
        "96588" to "农村信用社",
        "96518" to "农村信用社",
        "95313" to "广州银行",
        "95537" to "浦发银行",
        "95527" to "浙商银行",
        "956056" to "渤海银行",
        "95337" to "宁波银行",
        "95302" to "南京银行",
        "95312" to "江苏银行"
    )

    /** 短信签名中的银行名称关键词 */
    private val BANK_NAME_KEYWORDS = listOf(
        "工商银行", "招商银行", "建设银行", "农业银行", "中国银行", "交通银行",
        "浦发银行", "华夏银行", "兴业银行", "光大银行", "民生银行", "平安银行",
        "中信银行", "广发银行", "邮储银行", "邮政储蓄", "上海银行", "北京银行",
        "宁波银行", "南京银行", "江苏银行", "浙商银行", "渤海银行", "农村信用社",
        "农商银行", "农村商业银行", "信用社", "银行"
    )

    /** 支出方向关键词 */
    private val EXPENSE_KEYWORDS = listOf(
        "支出", "消费", "取现", "取款", "扣款", "代扣", "支付", "转出",
        "扣费", "缴费", "还款", "付款", "交易金额", "取出", "透支", "划出",
        "支出(消费)", "支出（消费）", "现支", "消费支出"
    )

    /** 收入方向关键词 */
    private val INCOME_KEYWORDS = listOf(
        "收入", "存入", "转入", "存入金额", "代发", "工资", "薪金", "利息",
        "收益", "退款", "退货", "汇入", "汇入汇款", "入账", "到账", "返现",
        "理财分红", "分红", "补贴", "报销", "奖金", "转入金额", "收"
    )

    /** 必须跳过的非交易短信 */
    private val SKIP_KEYWORDS = listOf(
        // 安全类
        "验证码", "动态密码", "校验码", "验证码为", "请勿泄露", "密码",
        "登录", "注册", "激活", "修改密码", "重置密码",
        // 营销类
        "广告", "推广", "活动", "优惠", "折扣", "抽奖", "领取", "抢购",
        "办卡", "申请", "推荐", "新品", "特惠", "尊享", "回馈", "积分兑换",
        "退订", "回复TD", "回复N",
        // 额度/账单类（非实际交易）
        "额度调整", "提额", "临时额度", "可用额度", "授信额度",
        "账单已出", "账单日", "最低还款", "分期付款", "分期申请",
        // 状态类
        "交易失败", "支付失败", "已撤销", "已冲正", "交易关闭", "未成功",
        "余额查询", "查询余额", "账户余额为"
    )

    /** 带两位小数的金额（银行交易额通常保留两位小数） */
    private val AMOUNT_DECIMAL_RE = Regex("([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2})")

    /** 数字后跟「元」的金额（兼容整数金额） */
    private val AMOUNT_YUAN_RE = Regex("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元")

    /** 卡号尾号：尾号1234 / 尾号为1234 / 卡号后四位1234 */
    private val CARD_TAIL_RE = Regex("(?:尾号|尾数为|后四位|末四位)[为是]?\\s*(\\d{3,4})")

    /** 卡片类型关键词，用于账户归类 */
    private val CREDIT_KEYWORDS = listOf("信用卡", "贷记卡", "准贷记卡")

    /**
     * 解析银行短信。
     * @param sender 发件人号码
     * @param body 短信正文
     * @param timestamp 短信接收时间（毫秒），作为交易日期基准
     * @return Parsed 或 null（非交易短信）
     */
    fun parse(sender: String, body: String, timestamp: Long): Parsed? {
        if (body.isBlank()) return null

        // 1. 跳过非交易短信
        if (SKIP_KEYWORDS.any { body.contains(it) }) return null

        // 2. 识别银行来源（发件人或签名），非银行短信直接忽略
        val bank = identifyBank(sender, body) ?: return null

        // 3. 判定收支方向；无明确方向则跳过
        val isIncome = detectDirection(body) ?: return null

        // 4. 提取交易金额（排除余额）
        val amountCents = extractAmount(body, isIncome)
        if (amountCents <= 0) return null

        // 5. 卡号尾号
        val cardTail = CARD_TAIL_RE.find(body)?.groupValues?.get(1).orEmpty()

        // 6. 账户类型
        val isCredit = CREDIT_KEYWORDS.any { body.contains(it) }
        val account = if (isCredit) "信用卡" else "银行卡"

        // 7. 分类推断
        val categoryId = guessCategory(body, isIncome)

        // 8. 备注：银行 + 卡号尾号 + 摘要
        val note = buildNote(bank, cardTail, body)

        val record = Record(
            id = UUID.randomUUID().toString(),
            type = if (isIncome) "income" else "expense",
            amount = amountCents,
            category = categoryId,
            date = com.jiyibi.ledger.util.DateUtil.format(timestamp),
            note = note,
            account = account,
            createdAt = timestamp
        )

        return Parsed(record, bank, cardTail)
    }

    /* ---------------- 内部实现 ---------------- */

    /** 识别银行名称：先看服务号，再看短信签名与正文 */
    private fun identifyBank(sender: String, body: String): String? {
        // 服务号匹配（发件人可能是 95588 或 1069000795588 这类网关号）
        BANK_BY_NUMBER.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { sender.contains(it.key) }
            ?.let { return it.value }

        // 短信签名【XX银行】
        val sign = Regex("[【\\[]([^】\\]]{2,20})[】\\]]").find(body)?.groupValues?.get(1)
        if (sign != null) {
            BANK_NAME_KEYWORDS.firstOrNull { sign.contains(it) }?.let { return sign.trim() }
        }

        // 正文中的银行名
        BANK_NAME_KEYWORDS.firstOrNull { body.contains(it) }?.let {
            return if (it == "银行") null else it
        }

        return null
    }

    /** 判定收支方向；返回 null 表示无法确定 */
    private fun detectDirection(body: String): Boolean? {
        val hasIncome = INCOME_KEYWORDS.any { body.contains(it) }
        val hasExpense = EXPENSE_KEYWORDS.any { body.contains(it) }

        return when {
            hasIncome && !hasExpense -> true
            hasExpense && !hasIncome -> false
            hasIncome && hasExpense -> {
                // 同时出现时按出现位置判定：靠前的更可能是主交易方向
                val firstIncome = INCOME_KEYWORDS.minOfOrNull { k ->
                    body.indexOf(k).takeIf { it >= 0 } ?: Int.MAX_VALUE
                } ?: Int.MAX_VALUE
                val firstExpense = EXPENSE_KEYWORDS.minOfOrNull { k ->
                    body.indexOf(k).takeIf { it >= 0 } ?: Int.MAX_VALUE
                } ?: Int.MAX_VALUE
                firstIncome <= firstExpense
            }
            else -> null
        }
    }

    /**
     * 提取交易金额。
     * 策略：先按标点分句，在含交易关键词且不含「余额」的句子中找金额；
     * 找不到再退回全文查找，并排除「余额」之后的数字。
     */
    private fun extractAmount(body: String, isIncome: Boolean): Long {
        val keywords = if (isIncome) INCOME_KEYWORDS else EXPENSE_KEYWORDS
        val clauses = body.split(Regex("[，,。；;、\\n\\r]")).map { it.trim() }.filter { it.isNotEmpty() }

        // 优先：含交易关键词且不含「余额」的句子
        clauses
            .filter { clause ->
                keywords.any { clause.contains(it) } &&
                        !clause.contains("余额") &&
                        !clause.contains("可用额度")
            }
            .forEach { clause ->
                val amount = findAmountIn(clause)
                if (amount > 0) return amount
            }

        // 次选：含交易关键词的句子（允许含余额，但取「余额」之前的金额）
        clauses
            .filter { clause -> keywords.any { clause.contains(it) } }
            .forEach { clause ->
                val beforeBalance = clause.substringBefore("余额")
                val amount = findAmountIn(beforeBalance)
                if (amount > 0) return amount
            }

        // 兜底：全文，截断到「余额」之前
        val beforeBalance = body.substringBefore("余额")
        return findAmountIn(beforeBalance)
    }

    /** 在给定文本中查找金额（分）。优先带两位小数，其次带「元」 */
    private fun findAmountIn(text: String): Long {
        AMOUNT_DECIMAL_RE.find(text)?.let { m ->
            val v = Money.parseFromBill(m.groupValues[1])
            if (v > 0) return v
        }
        AMOUNT_YUAN_RE.find(text)?.let { m ->
            val v = Money.parseFromBill(m.groupValues[1])
            if (v > 0) return v
        }
        return 0
    }

    /** 按关键词推断分类 */
    private fun guessCategory(text: String, isIncome: Boolean): String {
        return if (isIncome) {
            when {
                text.cAny("工资", "薪金", "代发", "薪酬", "劳务") -> "salary"
                text.cAny("奖金", "绩效", "年终", "提成", "奖励") -> "bonus"
                text.cAny("兼职", "稿费", "酬劳", "外快") -> "parttime"
                text.cAny("理财", "基金", "股票", "收益", "利息", "分红") -> "invest"
                text.cAny("红包", "礼金", "压岁") -> "redpack"
                text.cAny("报销", "差旅", "补贴") -> "reimburse"
                text.cAny("退款", "退货", "返现", "冲正") -> "refund"
                else -> "other_i"
            }
        } else {
            when {
                text.cAny("餐", "饭", "美团", "饿了么", "外卖", "餐饮", "食品", "超市") -> "food"
                text.cAny("加油", "停车", "高速", "地铁", "公交", "打车", "出租", "ETC") -> "transport"
                text.cAny("淘宝", "天猫", "京东", "拼多多", "商场", "百货", "服饰", "购物") -> "shopping"
                text.cAny("房租", "租金", "水费", "电费", "燃气", "物业", "家居") -> "home"
                text.cAny("话费", "流量", "宽带", "通信", "移动", "联通", "电信") -> "telecom"
                text.cAny("电影", "游戏", "娱乐", "视频", "音乐", "会员") -> "entertain"
                text.cAny("医院", "药店", "药房", "诊所", "挂号", "体检", "医疗") -> "medical"
                text.cAny("书店", "图书", "课程", "培训", "学费", "教育", "考试") -> "study"
                text.cAny("红包", "礼金", "人情", "份子", "贺礼") -> "social"
                text.cAny("健身", "运动", "游泳", "球馆", "瑜伽", "体育") -> "sport"
                text.cAny("宠物", "猫粮", "狗粮") -> "pet"
                text.cAny("取现", "取款", "ATM") -> "other_e"
                else -> "other_e"
            }
        }
    }

    /** 生成备注：银行 + 尾号 + 交易摘要片段 */
    private fun buildNote(bank: String, cardTail: String, body: String): String {
        val tailPart = if (cardTail.isNotEmpty()) "尾号$cardTail" else ""
        // 截取正文中的交易描述片段（去掉签名与冗余信息）
        val cleaned = body
            .replace(Regex("[【\\[][^】\\]]*[】\\]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val snippet = cleaned.take(40)
        return listOfNotNull(
            bank.takeIf { it.isNotEmpty() },
            tailPart.takeIf { it.isNotEmpty() },
            snippet.takeIf { it.isNotEmpty() }
        ).joinToString(" · ").take(60)
    }

    private fun String.cAny(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }
}
