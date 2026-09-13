package com.jiyibi.ledger.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jiyibi.ledger.data.Store
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 通知监听服务：捕获支付类 APP 的收款/付款通知，自动解析并写入账本。
 *
 * 使用前需在系统「设置 → 应用 → 特殊应用权限 → 通知使用权」中手动授权。
 * 授权后系统自动绑定本服务；撤销授权或开关关闭时不处理。
 */
class PayNotificationListener : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val store by lazy { Store(applicationContext) }

    /** 短期去重：key → 过期时间戳（毫秒）。同一通知 5 分钟内只处理一次。 */
    private val dedup = ConcurrentHashMap<String, Long>()
    private val DEDUP_TTL = 5 * 60 * 1000L

    override fun onListenerConnected() {
        // 服务已绑定，可在此做初始化
    }

    override fun onListenerDisconnected() {
        // 系统解绑（用户撤销授权或进程被杀）
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 检查监测开关
        val data = store.load()
        if (!data.monitorEnabled) return

        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        if (text.isNullOrBlank() && title.isNullOrBlank()) return

        // 去重：包名 + 标题 + 金额指纹
        val fingerprint = "$pkg|${title.orEmpty().take(20)}|${text.orEmpty().take(40)}"
        val now = System.currentTimeMillis()
        val expiry = dedup[fingerprint]
        if (expiry != null && expiry > now) return
        dedup[fingerprint] = now + DEDUP_TTL

        // 清理过期键
        dedup.entries.removeIf { it.value < now }

        executor.execute {
            val parsed = NotifyParser.parse(pkg, title, text) ?: return@execute
            saveRecord(parsed)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 不需要处理移除事件
    }

    private fun saveRecord(parsed: NotifyParser.Parsed) {
        try {
            val data = store.load()
            val updated = data.copy(records = data.records + parsed.record)
            store.save(updated)
        } catch (e: Exception) {
            // 静默失败，避免服务崩溃
        }
    }

    companion object {
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
