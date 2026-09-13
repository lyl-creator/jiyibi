package com.jiyibi.ledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.ui.components.EntrySheet
import com.jiyibi.ledger.ui.screens.ListScreen
import com.jiyibi.ledger.ui.screens.SettingsScreen
import com.jiyibi.ledger.ui.screens.StatsScreen
import com.jiyibi.ledger.util.DateUtil
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 应用根容器：顶部周导航 + 内容区 + 底部导航栏 + 记账面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: LedgerViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    var tab by remember { mutableStateOf("list") }
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Record?>(null) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { WeekTopBar(vm, hazeState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.hazeChild(state = hazeState),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            ) {
                TabItem("list", "明细", tab == "list") { tab = "list" }
                TabItem("stats", "统计", tab == "stats") { tab = "stats" }
                TabItem("settings", "设置", tab == "settings") { tab = "settings" }
            }
        },
        floatingActionButton = {
            if (tab != "settings") {
                FloatingActionButton(
                    onClick = { editing = null; sheetOpen = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .haze(state = hazeState)
        ) {
            when (tab) {
                "list" -> ListScreen(vm) { rec -> editing = rec; sheetOpen = true }
                "stats" -> StatsScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }

    if (sheetOpen) {
        EntrySheet(vm = vm, initial = editing, onDismiss = { sheetOpen = false }, hazeState = hazeState)
    }
}

/* ---------------- 顶部应用栏 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTopBar(vm: LedgerViewModel, hazeState: HazeState) {
    var pickerOpen by remember { mutableStateOf(false) }
    val canGoNext = !vm.isCurrentWeek()

    CenterAlignedTopAppBar(
        modifier = Modifier.hazeChild(state = hazeState),
        title = {
            TextButton(onClick = { pickerOpen = true }) {
                Text(
                    text = DateUtil.weekTitle(vm.weekStart),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { vm.shiftWeek(-1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一周")
            }
        },
        actions = {
            if (canGoNext) {
                IconButton(onClick = { vm.shiftWeek(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一周")
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    )

    if (pickerOpen) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { vm.goToWeekOf(DateUtil.fromUtcMillis(it)) }
                    pickerOpen = false
                }) { Text("前往") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/* ---------------- 底部导航项 ---------------- */

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    id: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { NavIcon(id, selected, LocalNavTint(selected)) },
        label = { Text(label) }
    )
}

@Composable
private fun LocalNavTint(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

/** 自绘导航图标：未选中为描边态，选中为填充态（M3 规范） */
@Composable
fun NavIcon(type: String, selected: Boolean, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = if (selected) w * 0.105f else w * 0.072f
        val thin = w * 0.068f

        when (type) {
            "list" -> {
                val lineStart = w * 0.34f
                val lineEnd = w * 0.86f
                listOf(0.26f, 0.50f, 0.74f).forEach { f ->
                    val y = h * f
                    drawLine(
                        color = tint, start = Offset(lineStart, y), end = Offset(lineEnd, y),
                        strokeWidth = if (selected) strokeW else thin, cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = tint,
                        radius = if (selected) w * 0.058f else w * 0.045f,
                        center = Offset(w * 0.16f, y)
                    )
                }
            }

            "stats" -> {
                val heights = listOf(0.44f, 0.76f, 0.56f)
                val barW = w * 0.145f
                val gap = w * 0.095f
                val baseY = h * 0.82f
                val startX = (w - (3 * barW + 2 * gap)) / 2f
                heights.forEachIndexed { i, ratio ->
                    val x = startX + i * (barW + gap)
                    val barH = h * ratio
                    val top = baseY - barH
                    if (selected) {
                        drawRoundRect(
                            color = tint, topLeft = Offset(x, top), size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2.5f, barW / 2.5f)
                        )
                    } else {
                        drawRoundRect(
                            color = tint, topLeft = Offset(x, top), size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2.5f, barW / 2.5f),
                            style = Stroke(width = thin)
                        )
                    }
                }
            }

            else -> {   // 设置（齿轮）
                val c = Offset(w / 2f, h / 2f)
                val r = w * 0.19f
                if (selected) {
                    drawCircle(color = tint, radius = r, center = c)
                } else {
                    drawCircle(color = tint, radius = r, center = c, style = Stroke(width = thin))
                }
                repeat(8) { i ->
                    val a = (i * PI / 4).toFloat()
                    val inner = w * 0.29f
                    val outer = w * 0.44f
                    drawLine(
                        color = tint,
                        start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                        end = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                        strokeWidth = if (selected) strokeW * 1.4f else thin * 1.25f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
