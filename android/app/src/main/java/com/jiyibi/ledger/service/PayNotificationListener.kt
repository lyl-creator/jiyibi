package com.jiyibi.ledger.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jiyibi.ledger.data.LedgerEvents
import com.jiyibi.ledger.data.NotifyLog
import com.jiyibi.ledger.data.Store
import com.jiyibi.ledger.util.Money
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 通知监听服务：捕获微信、支付宝、云闪付及各家手机银行的付款/收款通知，
 * 自动解析并写入账本，同时通知界面立即刷新。
 *
 * 使用前需在系统「设置 → 应用 → 特殊应用权限 → 通知使用权」中手动授权。
 * 授权后系统自动绑定本服务；撤销授权或开关关闭时不处理。
 */
class PayNotificationListener : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val store by lazy { Store(applicationContext) }

    /** 包名 → 应用桌面名称，避免重复查询 PackageManager */
    private val labelCache = ConcurrentHashMap<String, String>()

    /** 短期去重：通知指纹 → 过期时间戳。同一通知在 [DEDUP_TTL] 内只处理一次。 */
    private val dedup = ConcurrentHashMap<String, Long>()

    /** 二次去重：包名+方向+金额 → 过期时间戳。用于拦截同笔交易的汇总通知。 */
    private val amountDedup = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        // 记录连接状态，便于用户在「通知诊断」中确认服务是否真正生效
        NotifyLog.add(
            applicationContext, "系统", "通知监听服务", "",
            "已连接：通知使用权生效，开始接收支付 / 银行类通知", true
        )
    }

    override fun onListenerDisconnected() {
        NotifyLog.add(
            applicationContext, "系统", "通知监听服务", "",
            "已断开：请检查「通知使用权」是否被系统回收", false
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        val (title, text) = extractText(notification)
        if (title.isNullOrBlank() && text.isBlank()) return

        // 一级去重：包名 + 标题 + 正文指纹
        val now = System.currentTimeMillis()
        val fingerprint = "$pkg|${title.orEmpty().take(20)}|${text.take(60)}"
        if (dedup[fingerprint]?.let { it > now } == true) return
        dedup[fingerprint] = now + DEDUP_TTL
        dedup.entries.removeIf { it.value < now }

        // 读取磁盘与解析均放在工作线程，避免阻塞通知回调主线程
        executor.execute {
            val label = appLabel(pkg)
            val source = NotifyParser.sourceName(pkg, label)

            val data = store.load()
            if (!data.monitorEnabled) {
                logIfRelevant(pkg, label, source, title, text, "未记账：自动记账开关未开启")
                return@execute
            }

            val analysis = NotifyParser.analyze(pkg, title, text, label)
            val parsed = analysis.parsed
            if (parsed == null) {
                logIfRelevant(pkg, label, source, title, text, "未记账：${analysis.reason}")
                return@execute
            }

            // 二级去重：同一应用、同方向、同金额在短时间内只记一笔
            val amountKey = "$pkg|${parsed.record.type}|${parsed.record.amount}"
            val ts = System.currentTimeMillis()
            if (amountDedup[amountKey]?.let { it > ts } == true) {
                NotifyLog.add(
                    applicationContext, source, title, text,
                    "未记账：与 40 秒内的同额同向记录重复", false
                )
                return@execute
            }
            amountDedup[amountKey] = ts + AMOUNT_DEDUP_TTL
            amountDedup.entries.removeIf { it.value < ts }

            if (saveRecord(parsed)) {
                val dir = if (parsed.record.isIncome) "收入" else "支出"
                NotifyLog.add(
                    applicationContext, source, title, text,
                    "已记账：$dir ¥${Money.format(parsed.record.amount)}", true
                )
                LedgerEvents.notifyChanged(
                    "已自动记账：${parsed.sourceLabel} $dir ¥${Money.format(parsed.record.amount)}"
                )
            } else {
                NotifyLog.add(
                    applicationContext, source, title, text,
                    "未记账：写入账本失败", false
                )
            }
        }
    }

    /**
     * 仅对「可能与记账有关」的通知留痕：来自支付 / 银行类应用，或正文含交易特征。
     * 避免把聊天、新闻等无关通知灌进诊断日志。
     */
    private fun logIfRelevant(
        pkg: String,
        label: String?,
        source: String,
        title: String?,
        text: String?,
        reason: String
    ) {
        val full = "${title.orEmpty()} ${text.orEmpty()}"
        if (!NotifyParser.isPaymentApp(pkg, label) && !NotifyParser.looksLikeTransaction(full)) return
        NotifyLog.add(applicationContext, source, title, text, reason, false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 不需要处理移除事件
    }

    /**
     * 汇总通知的标题与正文。
     *
     * 各家手机银行的金额位置不一，可能在正文、大文本、子标题、摘要或多行文本中，
     * 这里全部收集并去重拼接，交给解析器统一处理。
     */
    private fun extractText(notification: Notification): Pair<String?, String> {
        val extras = notification.extras ?: return null to ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()

        val parts = mutableListOf<String>()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { parts.add(it.toString()) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            line?.toString()?.let { if (it.isNotBlank()) parts.add(it) }
        }

        val text = parts
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")

        return title to text
    }

    /** 取应用桌面名称；失败时返回 null，由解析器回退到已知包名表 */
    private fun appLabel(pkg: String): String? {
        labelCache[pkg]?.let { return it.ifEmpty { null } }
        val label = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            ""
        }
        labelCache[pkg] = label
        return label.ifEmpty { null }
    }

    /** 写入账本；成功返回 true */
    private fun saveRecord(parsed: NotifyParser.Parsed): Boolean {
        return try {
            val data = store.load()
            store.save(data.copy(records = data.records + parsed.record))
            true
        } catch (e: Exception) {
            false   // 静默失败，避免服务崩溃
        }
    }

    companion object {
        /** 同一通知的重复拦截窗口 */
        private const val DEDUP_TTL = 5 * 60 * 1000L

        /** 同笔交易（应用+方向+金额）的重复拦截窗口 */
        private const val AMOUNT_DEDUP_TTL = 40 * 1000L

        /** 检查当前是否已授权通知使用权 */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return enabledListeners.contains(context.packageName)
        }

        /** 跳转到系统通知使用权设置页 */
        fun openNotificationAccessSettings(context: Context) {
            try {
                context.startActivity(
                    android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // 兜底：通用应用设置
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }
}
