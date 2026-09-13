package com.jiyibi.ledger.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内账本变更事件。
 *
 * 通知监听服务与银行短信接收器写入账本后调用 [notifyChanged]，
 * 界面订阅 [change]，一旦收到即立即从磁盘重新加载数据并提示用户，
 * 无需用户手动点「刷新数据」。
 *
 * 说明：通知监听服务与广播接收器默认与应用运行在同一进程，
 * 因此进程内单例即可完成通信；跨进程重启的场景由界面回到前台时的补刷新兜底。
 */
object LedgerEvents {

    /** 一次外部写入产生的变更；[revision] 单调递增，便于界面作为副作用键 */
    data class Change(
        val revision: Long = 0L,
        val reason: String = ""
    )

    private val _change = MutableStateFlow(Change())

    /** 供界面订阅的变更流 */
    val change: StateFlow<Change> = _change.asStateFlow()

    /**
     * 外部组件（通知监听 / 短信接收）写入账本后调用。
     * @param reason 提示文案，例如「已自动记账：微信 支出 ¥12.34」
     */
    fun notifyChanged(reason: String) {
        val current = _change.value
        _change.value = Change(current.revision + 1L, reason)
    }
}
