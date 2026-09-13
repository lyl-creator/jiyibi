package com.jiyibi.ledger.service

import com.jiyibi.ledger.data.Categories
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.util.Money
import java.util.UUID

/**
 * 支付/收款通知解析器。
 * 从通知标题 + 正文提取金额、收支方向、商户/对方，映射到现有分类体系。
 */
object NotifyParser {

    /** 解析结果；null 表示该通知不是有效交易 */
    data class Parsed(
        val record: Record,
        val packageName: String,
        val sourceLabel: String
    )

    /** 目标应用包名 → 应用名（用于备注前缀与账户推断） */
    private val TARGET_APPS = mapOf(
        "com.tencent.mm" to "微信",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.icbc" to "工商银行",
        "com.cmbchina" to "招商银行",
        "com.ccb" to "建设银行",
        "com.bankcomm" to "交通银行",
        "com.boc" to "中国银行",
        "com.abchina" to "农业银行",
        "com.spdb" to "浦发银行",
        "com.cib" to "兴业银行",
        "com.cebbank" to "光大银行",
        "com.hxb" to "华夏银行",
        "com.cmbc" to "民生银行",
        "com.pingan" to "平安银行",
        "com.citic" to "中信银行",
        "com.gdb" to "广发银行",
        "com.bjrcb" to "北京农商银行",
        "com.unionpay" to "云闪付"
    )

    /** 金额正则：支持 ¥12.34 / ￥12.34 / 12.34元 / 支出1,234.56 / 金额：12.34 */
    private val AMOUNT_RE = Regex(
        "[¥￥]?\\s*(?:金额|支出|收入|付款|收款|转账|到账|支付|消费|入账|出账)?[:：]?\\s*" +
                "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    )

    /** 明确支出关键词（出现即判定为支出，不再看收入） */
    private val EXPENSE_KEYWORDS = listOf(
        "付款", "支付", "消费", "支出", "扣款", "代扣", "缴费", "还款", "转账给", "扫码支付",
        "付款成功", "支付成功", "交易成功-支出", "POS消费", "快捷支付", "网上支付", "跨行转出",
        "购买", "下单", "订单支付", "自动扣费", "续费", "充值", "提现"
    )

    /** 明确收入关键词 */
    private val INCOME_KEYWORDS = listOf(
        "收款", "到账", "入账", "收入", "转入", "退款", "报销", "工资", "奖金", "转账来自",
        "收款到账", "转账到账", "退款成功", "已入账", "代发工资", "利息", "分红", "理财收益",
        "红包", "补贴", "报销款", "工资到账", "劳务报酬", "稿酬"
    )

    /** 应跳过的关键词（广告、活动、非交易） */
    private val SKIP_KEYWORDS = listOf(
        "广告", "推广", "活动", "优惠券", "积分", "抽奖", "签到", "任务", "邀请",
        "登录", "验证码", "密码", "安全", "风险提示", "升级", "更新", "版本",
        "额度", "提额", "借款", "贷款", "分期", "信用卡申请", "新户礼",
        "未付款", "待付款", "已取消", "交易关闭", "支付失败"
    )

    /**
     * 解析通知。
     * @param packageName 通知来源包名
     * @param title 通知标题（可能为空）
     * @param text 通知正文
     * @return Parsed 或 null（非交易/无法解析）
     */
    fun parse(packageName: String, title: String?, text: String?): Parsed? {
        val appName = TARGET_APPS.entries.firstOrNull { packageName.startsWith(it.key, ignoreCase = true) }
            ?: return null

        val fullText = "${title.orEmpty()} ${text.orEmpty()}".trim()
        if (fullText.isEmpty()) return null

        // 跳过非交易通知
        if (SKIP_KEYWORDS.any { fullText.contains(it) }) return null

        // 提取金额
        val amountMatch = AMOUNT_RE.find(fullText) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "")
        val amountCents = Money.parseFromBill(amountStr)
        if (amountCents <= 0) return null

        // 判定收支方向
        val isIncome = INCOME_KEYWORDS.any { fullText.contains(it) }
        val isExpense = EXPENSE_KEYWORDS.any { fullText.contains(it) }

        val type = when {
            isIncome && !isExpense -> "income"
            isExpense && !isIncome -> "expense"
            isIncome && isExpense -> {
                // 同时出现：按关键词优先级，收入优先（如"退款"含"支付"字样时）
                if (fullText.contains("退款") || fullText.contains("到账") || fullText.contains("入账")) "income"
                else "expense"
            }
            else -> {
                // 无明确关键词：默认按应用习惯
                if (appName.value == "微信" && fullText.contains("收款")) "income"
                else if (appName.value == "支付宝" && fullText.contains("到账")) "income"
                else return null
            }
        }

        // 提取商户/对方（取标题或正文中的关键片段）
        val note = extractNote(title, text, appName.value)

        // 推断分类
        val categoryId = guessCategory(fullText, type)

        // 推断账户
        val account = inferAccount(appName.value, fullText)

        val record = Record(
            id = UUID.randomUUID().toString(),
            type = type,
            amount = amountCents,
            category = categoryId,
            date = com.jiyibi.ledger.util.DateUtil.today(),
            note = note,
            account = account,
            createdAt = System.currentTimeMillis()
        )

        return Parsed(record, packageName, appName.value)
    }

    /** 从通知中提取简洁的备注（优先标题，其次正文中的商户名） */
    private fun extractNote(title: String?, text: String?, appName: String): String {
        val candidates = mutableListOf<String>()
        title?.takeIf { it.isNotBlank() && it.length in 2..30 }?.let { candidates.add(it.trim()) }
        text?.let { t ->
            // 尝试提取"向XXX付款"、"支付给XXX"、"XXX向您转账"等
            val patterns = listOf(
                Regex("向(.{2,20})付款"),
                Regex("支付给(.{2,20})"),
                Regex("(.{2,20})向您转账"),
                Regex("收款方[:：](.{2,20})"),
                Regex("交易对方[:：](.{2,20})"),
                Regex("商户[:：](.{2,20})"),
                Regex("(.{2,20})向您付款"),
                Regex("(.{2,20})给您转账")
            )
            for (p in patterns) {
                p.find(t)?.let { m -> candidates.add(m.groupValues[1].trim()) }
            }
        }
        return candidates.firstOrNull()
            ?.replace(Regex("[\\n\\r]"), " ")
            ?.take(30)
            ?: "$appName 交易"
    }

    /** 按关键词推断分类，复用 BillParser 的关键词表思路 */
    private fun guessCategory(text: String, type: String): String {
        val t = text.lowercase()
        return if (type == "income") {
            when {
                t.containsAny("工资", "薪资", "薪酬", "劳务", "代发") -> "salary"
                t.containsAny("奖金", "绩效", "年终", "提成", "奖励") -> "bonus"
                t.containsAny("兼职", "稿费", "酬劳", "外快", "私活") -> "parttime"
                t.containsAny("理财", "基金", "股票", "收益", "利息", "分红", "余额宝", "零钱通") -> "invest"
                t.containsAny("红包", "礼金", "压岁") -> "redpack"
                t.containsAny("报销", "差旅", "补贴") -> "reimburse"
                t.containsAny("退款", "退货", "返现") -> "refund"
                else -> "other_i"
            }
        } else {
            when {
                t.containsAny("餐", "饭", "美团", "饿了么", "外卖", "肯德基", "麦当劳", "星巴克", "咖啡", "奶茶", "零食", "水果", "生鲜", "食堂", "烧烤", "火锅") -> "food"
                t.containsAny("地铁", "公交", "出租", "滴滴", "打车", "网约车", "加油", "停车", "高速", "高铁", "火车", "动车", "机票", "航空", "单车", "摩拜", "哈啰", "青桔") -> "transport"
                t.containsAny("淘宝", "天猫", "京东", "拼多多", "唯品会", "苏宁", "商场", "百货", "服饰", "化妆品", "护肤", "数码", "超市", "便利店", "购物") -> "shopping"
                t.containsAny("房租", "租金", "水费", "电费", "燃气", "物业", "家居", "家具", "家电", "装修") -> "home"
                t.containsAny("话费", "流量", "宽带", "中国移动", "中国联通", "中国电信", "通信") -> "telecom"
                t.containsAny("电影", "游戏", "娱乐", "视频会员", "腾讯视频", "爱奇艺", "优酷", "芒果", "音乐", "演出", "门票", "剧本", "酒吧") -> "entertain"
                t.containsAny("医院", "药店", "药房", "诊所", "挂号", "体检", "医疗", "门诊", "口腔", "牙科", "医药") -> "medical"
                t.containsAny("书店", "图书", "课程", "培训", "学费", "教育", "知识", "考试", "报名费", "文具") -> "study"
                t.containsAny("红包", "礼金", "人情", "份子", "贺礼", "随礼") -> "social"
                t.containsAny("健身", "运动", "游泳", "球馆", "跑步", "瑜伽", "体育") -> "sport"
                t.containsAny("宠物", "猫", "狗", "猫粮", "狗粮") -> "pet"
                else -> "other_e"
            }
        }
    }

    private fun inferAccount(appName: String, text: String): String {
        return when {
            appName == "微信" -> "微信"
            appName == "支付宝" -> "支付宝"
            text.contains("信用卡") -> "信用卡"
            text.contains("储蓄卡") || text.contains("借记卡") -> "银行卡"
            else -> "银行卡"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }
}
