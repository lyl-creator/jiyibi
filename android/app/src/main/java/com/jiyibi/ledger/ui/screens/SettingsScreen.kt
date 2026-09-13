package com.jiyibi.ledger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jiyibi.ledger.data.BillParser
import com.jiyibi.ledger.ui.LedgerViewModel
import com.jiyibi.ledger.update.UpdateManager
import com.jiyibi.ledger.util.Money
import com.jiyibi.ledger.worker.DailySummaryWorker
import java.io.File
import kotlinx.coroutines.launch

/**
 * 设置页：预算、导入账单、导出、清空
 */
@Composable
fun SettingsScreen(vm: LedgerViewModel) {
    val context = LocalContext.current
    var weeklyText by remember { mutableStateOf(if (vm.data.weeklyBudget > 0) Money.format(vm.data.weeklyBudget) else "") }
    var monthlyText by remember { mutableStateOf(if (vm.data.monthlyBudget > 0) Money.format(vm.data.monthlyBudget) else "") }
    var clearDialog by remember { mutableStateOf(false) }
    var shareFile by remember { mutableStateOf<File?>(null) }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readUri(context, uri)
        val result = BillParser.parse(text)
        if (result == null || result.records.isEmpty()) {
            vm.toast("未识别到有效账单记录")
        } else {
            vm.importRecords(result.records)
            val msg = buildString {
                append("已导入 ${result.records.size} 条")
                if (result.skipped > 0) append("（跳过 ${result.skipped} 条转账/退款）")
            }
            vm.toast(msg)
        }
    }

    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readUri(context, uri)
        val added = vm.mergeJson(text)
        vm.toast(if (added > 0) "已合并 $added 条记录" else "没有可合并的新记录")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
    ) {
        SectionTitle("预算")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp)
        ) {
            BudgetField(
                label = "周预算（元）",
                value = weeklyText,
                onValueChange = { weeklyText = it.filter { c -> c.isDigit() || c == '.' } },
                onSave = {
                    val cents = Money.parseYuan(weeklyText)
                    vm.setWeeklyBudget(cents)
                    vm.toast(if (cents > 0) "周预算已保存" else "已清除周预算")
                }
            )
            Spacer(Modifier.height(12.dp))
            BudgetField(
                label = "月预算（元）",
                value = monthlyText,
                onValueChange = { monthlyText = it.filter { c -> c.isDigit() || c == '.' } },
                onSave = {
                    val cents = Money.parseYuan(monthlyText)
                    vm.setMonthlyBudget(cents)
                    vm.toast(if (cents > 0) "月预算已保存" else "已清除月预算")
                }
            )
        }

        SectionTitle("实时监测")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            MonitorSection(vm = vm)
        }

        SectionTitle("每日提醒")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            NotifySection(vm = vm)
        }

        SectionTitle("关于")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AboutSection(vm = vm)
        }

        SectionTitle("数据")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            ActionRow(
                icon = Icons.Outlined.PlaylistAdd,
                title = "导入微信/支付宝账单",
                subtitle = "支持 CSV 格式，自动识别表头",
                onClick = { pickCsv.launch("text/csv") }
            )
            Divider()
            ActionRow(
                icon = Icons.Outlined.SwapHoriz,
                title = "合并 JSON 备份",
                subtitle = "去重合并另一台设备的备份文件",
                onClick = { pickJson.launch("application/json") }
            )
            Divider()
            ActionRow(
                icon = Icons.Outlined.FileUpload,
                title = "导出 JSON 备份",
                subtitle = "包含全部记录与预算",
                onClick = {
                    val f = vm.exportJson()
                    shareFile = f
                    vm.toast("已生成 ${f.name}")
                }
            )
            Divider()
            ActionRow(
                icon = Icons.Outlined.FileDownload,
                title = "导出 CSV 明细",
                subtitle = "便于在 Excel 中查看",
                onClick = {
                    val f = vm.exportCsv()
                    shareFile = f
                    vm.toast("已生成 ${f.name}")
                }
            )
        }

        SectionTitle("危险操作")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            ActionRow(
                icon = Icons.Outlined.DeleteForever,
                title = "清空全部记录",
                subtitle = "不可恢复，请先导出备份",
                tint = MaterialTheme.colorScheme.error,
                onClick = { clearDialog = true }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "记一笔 · 本地存储，零上传",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "当前共 ${vm.data.records.size} 条记录",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("清空全部记录？") },
            text = { Text("将删除所有 ${vm.data.records.size} 条记录与预算设置，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll(); clearDialog = false; vm.toast("已清空")
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text("取消") }
            }
        )
    }

    shareFile?.let { f ->
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            f
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (f.name.endsWith(".json")) "application/json" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${f.name}"))
        shareFile = null
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun BudgetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("保存") }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = tint)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

/* ---------------- 实时监测区块 ---------------- */

@Composable
private fun MonitorSection(vm: LedgerViewModel) {
    val context = LocalContext.current
    var accessGranted by remember { mutableStateOf(vm.isNotificationAccessGranted()) }
    var smsGranted by remember { mutableStateOf(vm.isSmsPermissionGranted()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 短信权限申请
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[android.Manifest.permission.RECEIVE_SMS] == true &&
                result[android.Manifest.permission.READ_SMS] == true
        smsGranted = granted
        if (granted) {
            vm.setSmsEnabled(true)
            vm.toast("银行卡短信记账已开启")
        } else {
            vm.toast("未授予短信权限，无法自动记账")
        }
    }

    // 每次进入设置页刷新授权状态（用户可能刚从系统设置返回）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        accessGranted = vm.isNotificationAccessGranted()
        smsGranted = vm.isSmsPermissionGranted()
    }

    Column {
        // 授权状态行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable {
                    com.jiyibi.ledger.service.PayNotificationListener
                        .openNotificationAccessSettings(context)
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = if (accessGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "通知使用权",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (accessGranted) "已授权" else "未授权，点击前往开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accessGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Divider()

        // 监测开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "自动记账",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (vm.data.monitorEnabled) "已开启，监测到交易将自动记录"
                    else "关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vm.data.monitorEnabled,
                onCheckedChange = { checked ->
                    vm.setMonitorEnabled(checked)
                    vm.toast(if (checked) "实时监测已开启" else "实时监测已关闭")
                },
                enabled = accessGranted,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        Divider()

        // 短信权限状态行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable {
                    if (smsGranted) {
                        // 已授权时跳转应用详情页，便于收回权限
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } else {
                        smsPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.RECEIVE_SMS,
                                android.Manifest.permission.READ_SMS
                            )
                        )
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Sms,
                contentDescription = null,
                tint = if (smsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "短信权限",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (smsGranted) "已授权" else "未授权，点击申请",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (smsGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Divider()

        // 短信记账开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "银行卡短信记账",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        !smsGranted -> "需先授予短信权限"
                        vm.data.smsEnabled -> "已开启，收到银行交易短信将自动记录"
                        else -> "关闭"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vm.data.smsEnabled,
                onCheckedChange = { checked ->
                    if (checked && !smsGranted) {
                        smsPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.RECEIVE_SMS,
                                android.Manifest.permission.READ_SMS
                            )
                        )
                        return@Switch
                    }
                    vm.setSmsEnabled(checked)
                    vm.toast(if (checked) "短信记账已开启" else "短信记账已关闭")
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        Divider()

        // 刷新 + 统计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable {
                    vm.reloadFromDisk()
                    accessGranted = vm.isNotificationAccessGranted()
                    refreshTrigger++
                    vm.toast("已刷新数据")
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "刷新数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "今日自动记录 ${vm.todayAutoCount()} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- 每日提醒区块 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifySection(vm: LedgerViewModel) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    val notifGranted = vm.isPostNotificationsGranted()

    Column {
        // 开关行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "昨日收支提醒",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (vm.data.notifyEnabled)
                        "每天 ${String.format("%02d:%02d", vm.data.notifyHour, vm.data.notifyMinute)} 推送"
                    else "关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vm.data.notifyEnabled,
                onCheckedChange = { checked ->
                    if (checked && !notifGranted) {
                        vm.toast("请先授予系统通知权限")
                        // 跳转到系统通知设置
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (e: Exception) {
                            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        }
                        return@Switch
                    }
                    vm.setNotifyEnabled(checked)
                    vm.toast(if (checked) "每日提醒已开启" else "每日提醒已关闭")
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        Divider()

        // 时间选择行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(enabled = vm.data.notifyEnabled) { showTimePicker = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "提醒时间",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (vm.data.notifyEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "点击选择每天推送的时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = String.format("%02d:%02d", vm.data.notifyHour, vm.data.notifyMinute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (vm.data.notifyEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = vm.data.notifyHour,
            initialMinute = vm.data.notifyMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择提醒时间") },
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setNotifyTime(state.hour, state.minute)
                    showTimePicker = false
                    vm.toast("提醒时间已设为 ${String.format("%02d:%02d", state.hour, state.minute)}")
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}

/* ---------------- 关于区块 ---------------- */

@Composable
private fun AboutSection(vm: LedgerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateUrl by remember { mutableStateOf(vm.data.updateUrl) }
    var checking by remember { mutableStateOf(false) }
    var latestInfo by remember { mutableStateOf<UpdateManager.VersionInfo?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadProgress by remember { mutableStateOf(-1) }
    var unregisterReceiver by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 轮询下载进度
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val p = UpdateManager.queryProgress(context, id)
            if (p < 0) {
                downloadProgress = -1
                break
            }
            downloadProgress = p
            if (p >= 100) break
            kotlinx.coroutines.delay(500)
        }
    }

    Column {
        // 版本信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "版本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "v${UpdateManager.currentVersionName} (${UpdateManager.currentVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Divider()

        // 作者
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "作者",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AUTHOR_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Divider()

        // GitHub 仓库
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { openUrl(context, AUTHOR_GITHUB) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub 仓库",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AUTHOR_REPO,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Divider()

        // 更新源地址
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = updateUrl,
                onValueChange = { updateUrl = it },
                label = { Text("更新源 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "version.json 格式：{versionCode, versionName, downloadUrl, changelog}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = {
                        vm.setUpdateUrl(updateUrl)
                        vm.toast("更新源已保存")
                    }
                ) { Text("保存地址") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (updateUrl.isBlank()) {
                            vm.toast("请输入更新源 URL")
                            return@Button
                        }
                        scope.launch {
                            checking = true
                            latestInfo = null
                            val info = UpdateManager.fetchVersionInfo(updateUrl)
                            checking = false
                            if (info == null) {
                                vm.toast("无法获取版本信息，请检查 URL")
                            } else if (info.versionCode <= UpdateManager.currentVersionCode) {
                                vm.toast("当前已是最新版本 v${UpdateManager.currentVersionName}")
                            } else {
                                latestInfo = info
                            }
                        }
                    },
                    enabled = !checking
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("检查更新")
                }
            }
        }
        Divider()

        // 发现新版本
        latestInfo?.let { info ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "发现新版本 v${info.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (downloadId == null) {
                    Button(onClick = {
                        val id = UpdateManager.startDownload(context, info)
                        downloadId = id
                        downloadProgress = 0
                        unregisterReceiver = UpdateManager.registerDownloadReceiver(context, id) {
                            vm.toast("下载完成，点击安装")
                            UpdateManager.installApk(context, info)
                        }
                    }) { Text("下载更新") }
                } else {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "下载中 $downloadProgress%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (downloadProgress >= 100) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                UpdateManager.installApk(context, info)
                            }) { Text("立即安装") }
                        }
                    }
                }
            }
            Divider()
        }
    }
}

/* ---------------- 应用信息 ---------------- */

/** 作者名称 */
private const val AUTHOR_NAME = "lyl-creator"

/** GitHub 仓库地址 */
private const val AUTHOR_GITHUB = "https://github.com/lyl-creator/jiyibi"

/** 仓库简称，用于界面展示 */
private const val AUTHOR_REPO = "lyl-creator/jiyibi"

/** 用系统浏览器打开链接 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: Exception) {
        // 设备无可用浏览器时静默忽略
    }
}

/* 文件读取工具 */
private fun readUri(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: ""
}
