package com.jiyibi.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.jiyibi.ledger.ui.AppRoot
import com.jiyibi.ledger.ui.LedgerViewModel
import com.jiyibi.ledger.ui.theme.LedgerTheme
import com.jiyibi.ledger.worker.DailySummaryWorker

/** 应用唯一 Activity：Compose 入口 */
class MainActivity : ComponentActivity() {

    private val vm: LedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建每日提醒通知渠道（Android 8+ 必需）
        DailySummaryWorker.createChannel(applicationContext)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LedgerTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}
