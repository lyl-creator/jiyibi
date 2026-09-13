package com.jiyibi.ledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.jiyibi.ledger.data.LedgerEvents
import com.jiyibi.ledger.data.Store
import com.jiyibi.ledger.util.Money
import java.util.concurrent.ConcurrentHashMap

/**
 * 银行短信监听：接收交易通知短信，解析后自动写入账本。
 *
 * 需要 RECEIVE_SMS 运行时权限；未授权时系统不会派发短信广播。
 * SMS_RECEIVED 属于隐式广播豁免项，应用在后台亦可收到。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        } ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        // 长短信会被拆分为多条 PDUS，需拼接后再解析
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = messages.firstOrNull()?.timestampMillis?.takeIf { it > 0 }
            ?: System.currentTimeMillis()

        if (body.isBlank()) return

        // 短期去重：同一发件人 + 同一内容 5 分钟内只处理一次
        val fingerprint = "$sender|${body.hashCode()}"
        val now = System.currentTimeMillis()
        if (dedup[fingerprint]?.let { it > now } == true) return
        dedup[fingerprint] = now + DEDUP_TTL
        dedup.entries.removeIf { it.value < now }

        val store = Store(context)
        val data = store.load()

        // 开关关闭时不处理（但仍消耗一次去重标记，避免开关打开后补记）
        if (!data.smsEnabled) return

        val parsed = SmsParser.parse(sender, body, timestamp) ?: return

        try {
            store.save(data.copy(records = data.records + parsed.record))
            // 通知界面立即刷新，无需用户手动点「刷新数据」
            val dir = if (parsed.record.isIncome) "收入" else "支出"
            val tail = parsed.cardTail.takeIf { it.isNotEmpty() }?.let { "($it)" }.orEmpty()
            LedgerEvents.notifyChanged(
                "短信自动记账：${parsed.bank}$tail $dir ¥${Money.format(parsed.record.amount)}"
            )
        } catch (e: Exception) {
            // 写入失败不中断广播处理
        }
    }

    companion object {
        private val dedup = ConcurrentHashMap<String, Long>()
        private const val DEDUP_TTL = 5 * 60 * 1000L
    }
}
