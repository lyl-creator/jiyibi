package com.jiyibi.ledger.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiyibi.ledger.data.Categories
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.ui.LedgerViewModel
import com.jiyibi.ledger.ui.theme.LocalMoneyColors
import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money

/**
 * 明细页：本周概览 + 日聚合列表
 * @param onEdit 列表项点击回调，传入要编辑的记录
 */
@Composable
fun ListScreen(
    vm: LedgerViewModel,
    onEdit: (Record) -> Unit
) {
    val records = remember(vm.weekStart, vm.data) { vm.weekRecords(vm.weekStart) }
    val stat = remember(records) {
        val expense = records.filter { !it.isIncome }.sumOf { it.amount }
        val income = records.filter { it.isIncome }.sumOf { it.amount }
        Triple(expense, income, records.size)
    }
    val money = LocalMoneyColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WeeklyOverviewCard(
            expense = stat.first,
            income = stat.second,
            count = stat.third,
            weeklyBudget = vm.data.weeklyBudget,
            monthExpense = vm.monthExpenseOf(DateUtil.today()),
            monthlyBudget = vm.data.monthlyBudget,
            money = money
        )

        if (records.isEmpty()) {
            EmptyState()
        } else {
            DayGroupedList(
                records = records,
                onEdit = onEdit,
                onDelete = { id -> vm.delete(id) }
            )
        }
    }
}

@Composable
private fun WeeklyOverviewCard(
    expense: Long,
    income: Long,
    count: Int,
    weeklyBudget: Long,
    monthExpense: Long,
    monthlyBudget: Long,
    money: MoneyColors
) {
    val weekRatio = if (weeklyBudget > 0) (expense.toFloat() / weeklyBudget).coerceIn(0f, 1.5f) else 0f
    val monthRatio = if (monthlyBudget > 0) (monthExpense.toFloat() / monthlyBudget).coerceIn(0f, 1.5f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "本周支出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "¥${Money.format(expense)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = money.out
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "本周收入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "¥${Money.format(income)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = money.income
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "共 $count 笔 · 结余 ¥${Money.format(income - expense)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (weeklyBudget > 0) {
            Spacer(Modifier.height(14.dp))
            BudgetRow(
                label = "周预算",
                spent = expense,
                budget = weeklyBudget,
                ratio = weekRatio,
                overColor = money.out
            )
        }
        if (monthlyBudget > 0) {
            Spacer(Modifier.height(10.dp))
            BudgetRow(
                label = "月预算",
                spent = monthExpense,
                budget = monthlyBudget,
                ratio = monthRatio,
                overColor = money.out
            )
        }
    }
}

@Composable
private fun BudgetRow(
    label: String,
    spent: Long,
    budget: Long,
    ratio: Float,
    overColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$label  ${Money.format(spent)} / ${Money.format(budget)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (ratio >= 1f) overColor else MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (ratio >= 1f) overColor else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🗒️",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "本周还没有记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点击右下角按钮记一笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayGroupedList(
    records: List<Record>,
    onEdit: (Record) -> Unit,
    onDelete: (String) -> Unit
) {
    val grouped = remember(records) {
        records
            // 日期降序；同一天内按记账时刻降序，新发生的排在上面
            .sortedWith(
                compareByDescending<Record> { DateUtil.key(it.date) }
                    .thenByDescending { it.createdAt }
            )
            .groupBy { it.date }
            .toSortedMap(compareByDescending { DateUtil.key(it) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        grouped.forEach { (date, list) ->
            item(key = "h_$date") {
                DayHeader(date = date, totalOut = list.filter { !it.isIncome }.sumOf { it.amount })
            }
            items(list, key = { it.id }) { rec ->
                RecordRow(
                    record = rec,
                    onEdit = { onEdit(rec) },
                    onDelete = { onDelete(rec.id) }
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: String, totalOut: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = DateUtil.dayLabel(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (totalOut > 0) {
            Text(
                text = "支出 ¥${Money.format(totalOut)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordRow(
    record: Record,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cat = Categories.find(record.type, record.category)
    val money = LocalMoneyColors.current
    var confirming by remember(record.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(cat.color).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = cat.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cat.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val sub = listOfNotNull(
                DateUtil.timeLabel(record.createdAt),
                record.account.takeIf { it.isNotEmpty() },
                record.note.takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = (if (record.isIncome) "+" else "-") + Money.format(record.amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (record.isIncome) money.income else money.out
        )

        if (confirming) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onDelete(); confirming = false }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "确认删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            TextButtonSmall(text = "取消", onClick = { confirming = false })
        } else {
            Spacer(Modifier.width(4.dp))
            TextButtonSmall(text = "删除", onClick = { confirming = true })
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 68.dp)
    )
}

@Composable
private fun TextButtonSmall(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

/* 内部别名 —— 避免与 Theme.kt 中同名类混淆 */
private typealias MoneyColors = com.jiyibi.ledger.ui.theme.MoneyColors
