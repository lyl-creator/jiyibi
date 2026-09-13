package com.jiyibi.ledger.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jiyibi.ledger.R
import com.jiyibi.ledger.data.Store
import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日昨日收支汇总通知。
 * 由 WorkManager 每日触发，计算前一天 00:00-24:00 的收入/支出/结余，发系统通知。
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "daily_summary"
        const val WORK_NAME = "daily_summary_work"

        /** 调度每日通知。hour/minute 为触发时间（本地时间）。 */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // 若今天该时间已过，则从明天开始
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)

            val initialDelay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** 创建通知渠道（Android 8+ 必需） */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "每日收支提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天定时推送昨日收入与支出汇总"
                enableVibration(true)
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val data = store.load()

        // 开关关闭时不发通知
        if (!data.notifyEnabled) return Result.success()

        // 计算昨日范围
        val yesterday = DateUtil.shiftDays(DateUtil.today(), -1)
        val startMillis = DateUtil.parse(yesterday).timeInMillis
        val endMillis = startMillis + 24 * 60 * 60 * 1000

        val records = data.records.filter {
            it.createdAt >= startMillis && it.createdAt < endMillis
        }
        val expense = records.filter { !it.isIncome }.sumOf { it.amount }
        val income = records.filter { it.isIncome }.sumOf { it.amount }
        val balance = income - expense

        // 无记录时不打扰
        if (records.isEmpty()) return Result.success()

        val dateLabel = "${yesterday.substring(5, 7).toInt()}月${yesterday.substring(8, 10).toInt()}日"
        val content = "昨日收支：收入 ¥${Money.format(income)}，支出 ¥${Money.format(expense)}，结余 ¥${Money.format(balance)}"

        val intent = Intent(applicationContext, Class.forName("com.jiyibi.ledger.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("记一笔 · $dateLabel 收支汇总")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm?.notify(1001, notification)

        return Result.success()
    }
}
