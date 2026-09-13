package com.jiyibi.ledger.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.ledger.data.Categories
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.ui.LedgerViewModel
import com.jiyibi.ledger.ui.theme.LocalMoneyColors
import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money

/**
 * 统计页：分类占比环 + 排行榜 + 最近 6 周柱状图
 */
@Composable
fun StatsScreen(vm: LedgerViewModel) {
    val records = remember(vm.weekStart, vm.data) { vm.weekRecords(vm.weekStart) }
    val (expenseTotal, incomeTotal) = remember(records) {
        records.filter { !it.isIncome }.sumOf { it.amount } to
            records.filter { it.isIncome }.sumOf { it.amount }
    }
    val slices = remember(records, vm.statsType) { categorySlices(records, vm.statsType) }
    val money = LocalMoneyColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
    ) {
        // 类型切换
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SegmentedButton(
                selected = vm.statsType == "expense",
                onClick = { vm.updateStatsType("expense") },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("支出") }
            SegmentedButton(
                selected = vm.statsType == "income",
                onClick = { vm.updateStatsType("income") },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("收入") }
        }

        // 环形图
        DonutCard(
            total = if (vm.statsType == "expense") expenseTotal else incomeTotal,
            slices = slices,
            centerLabel = if (vm.statsType == "expense") "总支出" else "总收入",
            moneyColor = if (vm.statsType == "expense") money.out else money.income
        )

        // 排行榜
        RankingCard(slices = slices)

        // 6 周柱状图（仅展示支出）
        if (expenseTotal + incomeTotal > 0 || vm.data.records.any { !it.isIncome }) {
            WeeklyTrendCard(vm)
        }
    }
}

private data class Slice(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Color,
    val amount: Long
)

private fun categorySlices(records: List<Record>, type: String): List<Slice> {
    return records
        .filter { it.type == type }
        .groupBy { it.category }
        .map { (catId, list) ->
            val cat = Categories.find(type, catId)
            Slice(cat.id, cat.name, cat.emoji, Color(cat.color), list.sumOf { it.amount })
        }
        .filter { it.amount > 0 }
        .sortedByDescending { it.amount }
}

/* ---------------- 环形图 ---------------- */

@Composable
private fun DonutCard(
    total: Long,
    slices: List<Slice>,
    centerLabel: String,
    moneyColor: Color
) {
    val target = if (total > 0) 1f else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "donut"
    )
    val sweepTotal = 360f * progress

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val stroke = size.minDimension * 0.14f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)

                    // 背景圆环
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.25f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )

                    // 分类段
                    if (slices.isNotEmpty() && total > 0) {
                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = (slice.amount.toFloat() / total) * sweepTotal
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke)
                            )
                            startAngle += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "¥${Money.format(total)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = moneyColor
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            // 顶部三类图例
            Column {
                if (slices.isEmpty()) {
                    Text(
                        text = "本周暂无${if (moneyColor == Color.Red) "支出" else "收入"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    slices.take(3).forEach { s ->
                        LegendRow(s)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(slice: Slice) {
    val total = slice.amount
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(slice.color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${slice.emoji} ${slice.name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = Money.short(total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------------- 排行榜 ---------------- */

@Composable
private fun RankingCard(slices: List<Slice>) {
    if (slices.isEmpty()) return
    val max = slices.maxOf { it.amount }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "分类排行",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        slices.forEach { s ->
            RankingRow(slice = s, ratio = s.amount.toFloat() / max)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RankingRow(slice: Slice, ratio: Float) {
    val animated by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 500),
        label = "rank"
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${slice.emoji} ${slice.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "¥${Money.format(slice.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(slice.color)
            )
        }
    }
}

/* ---------------- 6 周趋势 ---------------- */

@Composable
private fun WeeklyTrendCard(vm: LedgerViewModel) {
    val today = DateUtil.today()
    val thisMonday = DateUtil.mondayOf(today)
    val weeks = (5 downTo 0).map { DateUtil.shiftWeeks(thisMonday, -it) }
    val expenses = weeks.map { monday ->
        vm.weekStat(monday).expense
    }
    val incomes = weeks.map { monday ->
        vm.weekStat(monday).income
    }
    val max = (expenses + incomes).maxOrNull()?.coerceAtLeast(1) ?: 1L
    val money = LocalMoneyColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "近 6 周收支",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weeks.forEachIndexed { i, monday ->
                val outRatio = expenses[i].toFloat() / max
                val inRatio = incomes[i].toFloat() / max
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BarColumn(
                            ratio = outRatio,
                            color = money.out,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        )
                        BarColumn(
                            ratio = inRatio,
                            color = money.income,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = DateUtil.md(monday),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(money.out); Spacer(Modifier.width(4.dp))
            Text("支出", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            LegendDot(money.income); Spacer(Modifier.width(4.dp))
            Text("收入", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarColumn(ratio: Float, color: Color, modifier: Modifier = Modifier) {
    val target = ratio.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "bar"
    )
    val frac = animated.coerceAtLeast(0.015f)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac)
                .height((110f * animated).coerceAtLeast(3f).dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(color)
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

/* 简化：列表式排行也可以走 LazyColumn，但与可滚动 Column 同层会冲突。
   本实现直接用 Column 渲染（分类通常 ≤ 8 项），避免嵌套滚动问题。
   若希望后续改为 LazyColumn，将上方 RankingCard 的 Column 替换为：
       LazyColumn(...)
           items(slices) { ... }
   即可。 */
@Suppress("unused")
private fun rankingAsList(slices: List<Slice>) = slices  // 占位提示
