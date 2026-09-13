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

    /** 解析详情：成功时 [parsed] 非空；失败时 [reason] 说明原因，供「通知诊断」展示 */
    data class Analysis(val parsed: Parsed?, val reason: String)

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

    /** 交易特征词：用于判断未列举的应用是否值得纳入诊断日志 */
    private val TRANSACTION_HINTS = listOf(
        "元", "¥", "￥", "支出", "收入", "付款", "收款", "转账", "消费",
        "扣款", "到账", "入账", "余额", "动账", "交易"
    )

    /**
     * 金额候选正则，按可靠性由高到低依次尝试。
     *
     * 允许省略前导零的小数（`.50 元` / `¥.50`），少数渠道以这种形式显示几角钱。
     */
    private val AMOUNT_PATTERNS = listOf(
        // 1. 带货币符号：¥12.34 / ￥1,234.56 / ¥.50
        Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?|\\.\\d{1,2})"),
        // 2. 以「元」结尾：12.34元 / .50元
        Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?|\\.\\d{1,2})\\s*元"),
        // 3. 交易关键词之后的数额：金额12.34 / 消费人民币12.34 / 收入 1,234.00
        Regex(
            "(?:交易金额|金额|支出|收入|付款|收款|到账|入账|支付|消费|扣款|转账|人民币|RMB|CNY)" +
                    "[:：]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
        )
    )

    /**
     * 兜底金额：含小数点的独立数额。
     * 卡号尾号与「时:分」均不含小数点，天然被排除；日期（09.13 / 2026.09.13）另行过滤。
     */
    private val FALLBACK_AMOUNT_RE = Regex(
        "(?<![0-9.])([0-9]{1,3}(?:,[0-9]{3})+\\.[0-9]{1,2}|[0-9]+\\.[0-9]{1,2})(?![0-9])"
    )

    /**
     * 余额线索（按位置截断用）。
     *
     * `余额(?!宝)` 用于放过「余额宝收益」这类真实交易——余额宝是产品名，不是账户余额。
     */
    private val BALANCE_RE = Regex("余额(?!宝)|可用额度|结余|剩余额度")

    /**
     * 卡号 / 账号线索：紧邻金额出现时，该数字不是交易额。
     *
     * 只认完整词，不用单字「账」——否则「向张三转账 0.50 元」的「转账」
     * 会因含「账」而被误判为账号，金额随之丢失。
     */
    private val ACCOUNT_HINTS = listOf("尾号", "尾数为", "卡号", "账号", "后四位", "末四位")

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

    /** 「向某人转账 / 付款」这类只有动作、不含方向关键词的句式 */
    private val EXPENSE_DIRECTION_RE =
        Regex("向(?!您|我|本人).{1,20}?(转账|付款|支付|汇款|发红包|扫码)")

    /** 「某人向您转账 / 给您付款」这类收入句式 */
    private val INCOME_DIRECTION_RE =
        Regex("(向您转账|向您付款|给您转账|给您付款|转账给您|收到.{1,20}?(转账|付款|红包|汇款))")

    /**
     * 应跳过的关键词（营销、安全、状态类非交易通知）。
     *
     * 这里只放**精确短语**，不放「余额」「额度」「分期」这类宽泛词——
     * 真实交易通知几乎必然带「…，余额 1,234.56 元」这样的尾巴，
     * 用裸词匹配会把正常记账整条丢弃（小额交易没有短信兜底时尤其致命）。
     */
    private val SKIP_KEYWORDS = listOf(
        // 安全类
        "验证码", "动态密码", "校验码", "请勿泄露", "修改密码", "重置密码",
        "安全验证", "安全提醒", "风险提示", "安全中心",
        // 营销类
        "广告", "推广", "活动", "积分", "抽奖", "签到", "任务完成", "邀请有礼",
        "信用卡申请", "新户礼", "提额", "额度调整", "优惠券已到账", "优惠券领取",
        // 额度 / 账单类（非实际交易）；「可用额度」不在此列——它常伴随真实交易出现，
        // 由余额截断逻辑处理即可
        "授信额度", "临时额度", "账单已出", "最低还款",
        "分期申请", "分期办理", "借款", "贷款",
        // 状态类
        "未付款", "待付款", "已取消", "交易关闭", "支付失败", "付款失败", "交易失败",
        "余额查询", "查询余额", "账户余额为", "余额不足"
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
    ): Parsed? = analyze(packageName, title, text, appLabel).parsed

    /**
     * 解析通知并给出失败原因。
     *
     * 与 [parse] 的唯一区别是失败时返回可读原因，便于在「通知诊断」中核对
     * 某条通知为什么没有记上账。
     */
    fun analyze(
        packageName: String?,
        title: String?,
        text: String?,
        appLabel: String? = null
    ): Analysis {
        val pkg = packageName.orEmpty()
        val appName = resolveAppName(pkg, appLabel)
            ?: return Analysis(null, "未识别为支付 / 银行类应用")

        val fullText = "${title.orEmpty()} ${text.orEmpty()}".trim()
        if (fullText.isEmpty()) return Analysis(null, "通知内容为空")

        // 跳过非交易通知
        SKIP_KEYWORDS.firstOrNull { fullText.contains(it) }
            ?.let { return Analysis(null, "命中忽略词「$it」") }

        // 提取金额（自动排除账户余额）
        val amountCents = extractAmountCents(fullText)
        if (amountCents <= 0) return Analysis(null, "未提取到交易金额")

        // 判定收支方向
        val type = resolveType(pkg, appName, fullText)
            ?: return Analysis(null, "无法判定收支方向")

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

        return Analysis(Parsed(record, pkg, appName), "")
    }

    /* ---------------- 诊断辅助 ---------------- */

    /** 是否为可识别的支付 / 银行类应用 */
    fun isPaymentApp(pkg: String?, appLabel: String?): Boolean =
        resolveAppName(pkg.orEmpty(), appLabel) != null

    /** 通知来源的展示名；识别不出时退回应用名，最后退回包名 */
    fun sourceName(pkg: String?, appLabel: String?): String {
        val label = appLabel?.trim().orEmpty()
        return resolveAppName(pkg.orEmpty(), appLabel)
            ?: label.ifEmpty { pkg.orEmpty().ifEmpty { "未知应用" } }
    }

    /** 正文是否具备交易特征；用于把未列举的银行 App 通知也纳入诊断日志 */
    fun looksLikeTransaction(text: String): Boolean =
        TRANSACTION_HINTS.any { text.contains(it) }

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
     * 交易通知的典型形态是「…支出 0.50 元，余额 1,234.56 元」，其中的余额不是交易额。
     * 因此分三步取值：
     *  1. 先在不含余额线索的语句中查找；
     *  2. 再在含余额线索的语句中，只取其线索之前的部分；
     *  3. 最后兜底：全文截断到第一个余额线索之前。
     * 每一步内部都会排除卡号尾号（尾号 / 卡号 / 账号）紧邻的数字。
     */
    private fun extractAmountCents(text: String): Long {
        val clauses = text
            .split(Regex("[，,。；;、\\n\\r]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. 不含余额线索的语句优先
        clauses
            .filterNot { BALANCE_RE.containsMatchIn(it) }
            .forEach { clause ->
                val v = findAmountIn(clause)
                if (v > 0) return v
            }

        // 2. 含余额线索的语句：只取其线索之前的部分
        clauses
            .filter { BALANCE_RE.containsMatchIn(it) }
            .forEach { clause ->
                val cut = BALANCE_RE.find(clause)?.range?.first ?: return@forEach
                val v = findAmountIn(clause.substring(0, cut))
                if (v > 0) return v
            }

        // 3. 兜底：全文截断到第一个余额线索之前
        val cut = BALANCE_RE.find(text)?.range?.first ?: text.length
        return findAmountIn(text.substring(0, cut))
    }

    /** 在给定片段中按可靠度顺序查找金额（分）；返回 0 表示未找到 */
    private fun findAmountIn(segment: String): Long {
        if (segment.isEmpty()) return 0

        for (pattern in AMOUNT_PATTERNS) {
            for (match in pattern.findAll(segment)) {
                val cents = toCents(segment, match, strictDate = false)
                if (cents > 0) return cents
            }
        }

        for (match in FALLBACK_AMOUNT_RE.findAll(segment)) {
            val cents = toCents(segment, match, strictDate = true)
            if (cents > 0) return cents
        }

        return 0
    }

    /**
     * 校验单个匹配并换算为「分」。
     *
     * @param strictDate 为 true 时额外排除日期形态（如 09.13、2026.09.13）
     * @return 分；0 表示该匹配应被舍弃
     */
    private fun toCents(segment: String, match: MatchResult, strictDate: Boolean): Long {
        val group = match.groups[1] ?: return 0

        // 紧邻的前文若含「尾号 / 卡号 / 账号 / 后四位」等，该数字属于卡号或账号
        val head = segment.substring(maxOf(0, group.range.first - 6), group.range.first)
        if (ACCOUNT_HINTS.any { head.contains(it) }) return 0
        // 再兜一层：紧邻 2 字内含「尾 / 卡」，也视为卡号（如「银行卡1234」）
        val near = head.takeLast(2)
        if (near.contains('尾') || near.contains('卡')) return 0

        if (strictDate) {
            val tail = segment.substring(
                group.range.last + 1,
                minOf(segment.length, group.range.last + 3)
            )
            // 「2026.09.13」这类年月日格式：首段后面紧跟「.数字」，视为日期
            if (tail.startsWith(".")) return 0
            if (tail.startsWith("月") || tail.startsWith("日") || tail.startsWith("号")) return 0
            if (head.endsWith("年") || head.endsWith("月") || head.endsWith("日")) return 0
        }

        val raw = group.value.replace(",", "")
        return Money.parseFromBill(raw)
    }

    /* ---------------- 收支方向 ---------------- */

    /** 判定收支方向；无法判定时返回 null（宁可漏记也不错记） */
    private fun resolveType(pkg: String, appName: String, text: String): String? {
        val isIncome = INCOME_KEYWORDS.any { text.contains(it) } ||
                INCOME_DIRECTION_RE.containsMatchIn(text)
        val isExpense = EXPENSE_KEYWORDS.any { text.contains(it) } ||
                EXPENSE_DIRECTION_RE.containsMatchIn(text)

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
