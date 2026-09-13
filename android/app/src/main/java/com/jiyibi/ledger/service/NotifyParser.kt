package com.jiyibi.ledger.service

import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money
import java.util.UUID

/**
 * 支付/收款通知解析器。
 *
 * 从通知的标题与正文中提取金额、收支方向、商户/对方，映射到现有分类体系。
 * 目标应用分为两类：
 *  1. 明确列举的支付类 App（微信、支付宝、云闪付、各家手机银行等）；
 *  2. 未被列举、但应用名含「银行 / 支付 / 钱包 / 信用卡」等特征的 App，按应用名放行，
 *     避免因手机银行包名繁多而漏记。
 */
object NotifyParser {

    /** 解析结果；null 表示该通知不是有效交易 */
    data class Parsed(
        val record: Record,
        val packageName: String,
        val sourceLabel: String
    )

    /** 已知包名前缀 → 兜底展示名（取不到系统应用名时使用） */
    private val KNOWN_PACKAGES = linkedMapOf(
        // 第三方支付 / 社交支付
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ",
        "com.tencent.qqlite" to "QQ",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.alipay" to "支付宝",
        "com.unionpay" to "云闪付",
        "com.jd.jrapp" to "京东金融",
        "com.baidu.wallet" to "度小满",
        "com.sankuai.meituan" to "美团",
        "com.webank.wemoney" to "微众银行",
        "com.mybank.android.phone" to "网商银行",
        "cn.gov.pbc.dcep" to "数字人民币",
        // 全国性银行
        "com.icbc" to "工商银行",
        "com.chinamworld.main" to "建设银行",
        "com.abchina" to "农业银行",
        "com.android.bankabc" to "农业银行",
        "com.boc" to "中国银行",
        "com.bankcomm" to "交通银行",
        "com.cmbchina" to "招商银行",
        "com.yitong.mbank.psbc" to "邮储银行",
        "com.cmbc" to "民生银行",
        "com.citic" to "中信银行",
        "com.ecitic" to "中信银行",
        "com.cebbank" to "光大银行",
        "com.hxb" to "华夏银行",
        "com.spdb" to "浦发银行",
        "com.spdbccc" to "浦发信用卡",
        "com.cib" to "兴业银行",
        "com.cgbchina" to "广发银行",
        "com.pingan" to "平安银行",
        // 城商行 / 农商行
        "com.bankofbeijing" to "北京银行",
        "com.bjrcb" to "北京农商银行",
        "cn.com.nbcb" to "宁波银行",
        "com.shsscb" to "上海银行",
        "com.bosc" to "上海银行"
    )

    /**
     * 应用名启发式关键词。
     * 未在 [KNOWN_PACKAGES] 中列举的手机银行 App，只要其桌面名称命中其中之一，
     * 即视为可识别的支付/银行类应用（仍需同时解析出金额与收支方向才会记账）。
     */
    private val APP_NAME_HINTS = listOf(
        "银行", "信用社", "农商", "农信", "银联", "云闪付",
        "支付", "钱包", "信用卡", "银行卡",
        "bank", "pay", "wallet", "credit"
    )

    /** 金额候选正则，按可靠性由高到低依次尝试 */
    private val AMOUNT_PATTERNS = listOf(
        // 1. 带货币符号：¥12.34 / ￥1,234.56
        Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
        // 2. 以「元」结尾：12.34元
        Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元"),
        // 3. 交易关键词之后的数额：金额12.34 / 消费人民币12.34 / 收入 1,234.00
        Regex(
            "(?:交易金额|金额|支出|收入|付款|收款|到账|入账|支付|消费|扣款|转账|人民币|RMB|CNY)" +
                    "[:：]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
        ),
        // 4. 兜底：含小数点的独立数额（卡号尾号与日期时间均无小数，天然被排除）
        Regex("(?<![0-9.])([0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]{1,2}|[0-9]+\\.[0-9]{1,2})(?![0-9])")
    )

    /** 明确支出关键词（出现即判定为支出，不再看收入） */
    private val EXPENSE_KEYWORDS = listOf(
        "付款", "支付", "消费", "支出", "扣款", "代扣", "缴费", "还款", "转账给", "扫码支付",
        "付款成功", "支付成功", "交易成功-支出", "POS消费", "快捷支付", "网上支付", "跨行转出",
        "购买", "下单", "订单支付", "自动扣费", "续费", "充值", "提现", "出账", "支取"
    )

    /** 明确收入关键词 */
    private val INCOME_KEYWORDS = listOf(
        "收款", "到账", "入账", "收入", "转入", "退款", "报销", "工资", "奖金", "转账来自",
        "收款到账", "转账到账", "退款成功", "已入账", "代发工资", "利息", "分红", "理财收益",
        "红包", "补贴", "报销款", "工资到账", "劳务报酬", "稿酬", "退税", "返现", "存入"
    )

    /** 应跳过的关键词（广告、活动、非交易） */
    private val SKIP_KEYWORDS = listOf(
        "广告", "推广", "活动", "优惠券", "积分", "抽奖", "签到", "任务", "邀请",
        "登录", "验证码", "密码", "安全", "风险提示", "升级", "更新", "版本",
        "额度", "提额", "借款", "贷款", "分期", "信用卡申请", "新户礼",
        "未付款", "待付款", "已取消", "交易关闭", "支付失败", "付款失败",
        "余额", "可用额度", "账单已出", "最低还款"
    )

    /** 无信息量的标题通用词（标题仅由这些词与应用名组成时不作为备注） */
    private val GENERIC_TITLE_WORDS = listOf(
        "支付", "钱包", "银行", "通知", "消息", "服务", "提醒", "动账", "客户端"
    )

    /**
     * 解析通知。
     *
     * @param packageName 通知来源包名
     * @param title 通知标题（可能为空）
     * @param text 通知正文（已合并大文本/多行文本等来源）
     * @param appLabel 应用桌面名称，取自 PackageManager；用于识别未列举的手机银行 App
     * @return Parsed 或 null（非交易 / 无法解析）
     */
    fun parse(
        packageName: String?,
        title: String?,
        text: String?,
        appLabel: String? = null
    ): Parsed? {
        val pkg = packageName.orEmpty()
        val appName = resolveAppName(pkg, appLabel) ?: return null

        val fullText = "${title.orEmpty()} ${text.orEmpty()}".trim()
        if (fullText.isEmpty()) return null

        // 跳过非交易通知
        if (SKIP_KEYWORDS.any { fullText.contains(it) }) return null

        // 提取金额
        val amountCents = extractAmountCents(fullText)
        if (amountCents <= 0) return null

        // 判定收支方向
        val type = resolveType(pkg, appName, fullText) ?: return null

        val record = Record(
            id = UUID.randomUUID().toString(),
            type = type,
            amount = amountCents,
            category = guessCategory(fullText, type),
            date = DateUtil.today(),
            note = extractNote(title, text, appName),
            account = inferAccount(pkg, appName, fullText),
            createdAt = System.currentTimeMillis()
        )

        return Parsed(record, pkg, appName)
    }

    /* ---------------- 应用识别 ---------------- */

    /**
     * 解析通知来源应用名。
     * 优先使用系统给出的应用名称；未列举的应用名若命中 [APP_NAME_HINTS] 也予以放行。
     */
    private fun resolveAppName(pkg: String, appLabel: String?): String? {
        val known = KNOWN_PACKAGES.entries.firstOrNull {
            pkg.startsWith(it.key, ignoreCase = true)
        }
        val label = appLabel?.trim().orEmpty()

        if (known != null) return label.ifEmpty { known.value }
        if (label.isEmpty()) return null

        return if (APP_NAME_HINTS.any { label.contains(it, ignoreCase = true) }) label else null
    }

    /* ---------------- 金额提取 ---------------- */

    /**
     * 提取交易金额（分）。
     *
     * 按 [AMOUNT_PATTERNS] 的可靠度顺序尝试，并排除卡号尾号、账号等紧邻的非金额数字，
     * 避免把「尾号 1234」误当作 1234 元。
     */
    private fun extractAmountCents(text: String): Long {
        for (pattern in AMOUNT_PATTERNS) {
            for (match in pattern.findAll(text)) {
                val group = match.groups[1] ?: continue
                // 紧邻的前文若含「尾 / 卡 / 号 / 账」，该数字属于卡号或账号
                val head = text.substring(maxOf(0, group.range.first - 4), group.range.first)
                if (head.any { it in "尾卡账号" }) continue

                val raw = group.value.replace(",", "")
                val cents = Money.parseFromBill(raw)
                if (cents > 0) return cents
            }
        }
        return 0
    }

    /* ---------------- 收支方向 ---------------- */

    /** 判定收支方向；无法判定时返回 null（宁可漏记也不错记） */
    private fun resolveType(pkg: String, appName: String, text: String): String? {
        val isIncome = INCOME_KEYWORDS.any { text.contains(it) }
        val isExpense = EXPENSE_KEYWORDS.any { text.contains(it) }

        return when {
            isIncome && !isExpense -> "income"
            isExpense && !isIncome -> "expense"
            isIncome && isExpense -> {
                // 同时命中：退款/到账/入账类语义优先判为收入
                if (text.contains("退款") || text.contains("到账") ||
                    text.contains("入账") || text.contains("收款")
                ) "income" else "expense"
            }
            else -> {
                // 无方向关键词：仅对零钱/余额类入口做保守推断
                when {
                    text.contains("收款") && isWallet(pkg, appName) -> "income"
                    text.contains("到账") && isWallet(pkg, appName) -> "income"
                    else -> null
                }
            }
        }
    }

    /** 是否为第三方钱包类应用（微信 / 支付宝 / 云闪付） */
    private fun isWallet(pkg: String, appName: String): Boolean =
        pkg.startsWith("com.tencent.mm", true) ||
                pkg.startsWith("com.eg.android.AlipayGphone", true) ||
                pkg.startsWith("com.alipay", true) ||
                pkg.startsWith("com.unionpay", true) ||
                appName.contains("微信") || appName.contains("支付宝") || appName.contains("云闪付")

    /* ---------------- 备注提取 ---------------- */

    /** 从通知中提取简洁的备注（优先交易对方，其次有信息量的标题） */
    private fun extractNote(title: String?, text: String?, appName: String): String {
        extractCounterpart(text)?.let { return it }

        val t = title?.trim().orEmpty()
        val usable = t.length in 2..30 && !isGenericTitle(t, appName)
        if (usable) return t.replace(Regex("[\\n\\r]"), " ").take(30)

        return "$appName 交易"
    }

    /** 提取交易对方 / 商户名 */
    private fun extractCounterpart(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("向(.{2,20}?)付款"),
            Regex("向(.{2,20}?)支付"),
            Regex("向(.{2,20}?)转账"),
            Regex("支付给(.{2,20}?)"),
            Regex("(.{2,20}?)向您转账"),
            Regex("(.{2,20}?)向您付款"),
            Regex("(.{2,20}?)给您转账"),
            Regex("收款方[:：](.{2,20}?)"),
            Regex("付款方[:：](.{2,20}?)"),
            Regex("交易对方[:：](.{2,20}?)"),
            Regex("商户[:：](.{2,20}?)"),
            Regex("来自(.{2,20}?)的")
        )
        for (p in patterns) {
            val v = p.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!v.isNullOrBlank()) return v.replace(Regex("[\\n\\r]"), " ").take(30)
        }
        return null
    }

    /** 判断标题是否只由应用名与通用词构成，无额外信息量 */
    private fun isGenericTitle(title: String, appName: String): Boolean {
        if (title.equals(appName, ignoreCase = true)) return true
        var stripped = title
        GENERIC_TITLE_WORDS.forEach { stripped = stripped.replace(it, "") }
        stripped = stripped.trim()
        return stripped.length < 2 || appName.contains(stripped, ignoreCase = true)
    }

    /* ---------------- 分类推断 ---------------- */

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

    /* ---------------- 账户推断 ---------------- */

    private fun inferAccount(pkg: String, appName: String, text: String): String = when {
        pkg.startsWith("com.tencent.mm", ignoreCase = true) -> "微信"
        pkg.startsWith("com.eg.android.AlipayGphone", ignoreCase = true) -> "支付宝"
        pkg.startsWith("com.alipay", ignoreCase = true) -> "支付宝"
        text.contains("信用卡") || appName.contains("信用卡") -> "信用卡"
        else -> "银行卡"
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }
}
