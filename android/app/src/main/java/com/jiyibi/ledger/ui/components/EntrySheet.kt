package com.jiyibi.ledger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jiyibi.ledger.data.ACCOUNTS
import com.jiyibi.ledger.data.Categories
import com.jiyibi.ledger.data.Category
import com.jiyibi.ledger.data.Record
import com.jiyibi.ledger.ui.LedgerViewModel
import com.jiyibi.ledger.ui.theme.LocalMoneyColors
import com.jiyibi.ledger.util.DateUtil
import com.jiyibi.ledger.util.Money
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.util.UUID

/**
 * 记账底部弹层：类型切换 → 分类 → 自定义数字键盘 → 账户/备注/日期 → 保存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    vm: LedgerViewModel,
    initial: Record?,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val money = LocalMoneyColors.current

    var type by remember { mutableStateOf(initial?.type ?: "expense") }
    var amount by remember {
        mutableStateOf(if (initial != null) formatInput(initial.amount) else "")
    }
    var categoryId by remember {
        mutableStateOf(initial?.category ?: Categories.of("expense").first().id)
    }
    var account by remember { mutableStateOf(initial?.account?.takeIf { it.isNotEmpty() } ?: ACCOUNTS.first()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: DateUtil.today()) }
    var datePickerOpen by remember { mutableStateOf(false) }

    // 记账时刻：编辑时沿用原记录时间，新建时取进入面板的时刻
    val recordTime = remember { initial?.createdAt ?: System.currentTimeMillis() }

    // 类型变化时若分类不在当前类型列表内则重置为第一项
    val currentList = Categories.of(type)
    val effectiveCategory = currentList.firstOrNull { it.id == categoryId } ?: currentList.first()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = if (hazeState != null) Modifier.hazeChild(state = hazeState) else Modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 类型切换
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = type == "expense",
                    onClick = {
                        type = "expense"
                        categoryId = Categories.expense.first().id
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                        activeContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("支出") }
                SegmentedButton(
                    selected = type == "income",
                    onClick = {
                        type = "income"
                        categoryId = Categories.income.first().id
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text("收入") }
            }

            Spacer(Modifier.height(12.dp))

            // 金额输入：使用系统输入法
            val amountCents = Money.parseYuan(amount)
            val focusRequester = remember { FocusRequester() }
            val keyboardController = LocalSoftwareKeyboardController.current

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = filterAmountInput(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (amountCents > 0)
                        (if (type == "expense") money.out else money.income)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                prefix = {
                    Text(
                        text = "¥",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text("金额") },
                placeholder = { Text("0.00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() }
                )
            )

            Spacer(Modifier.height(12.dp))

            // 分类网格
            CategoryGrid(
                list = currentList,
                selectedId = effectiveCategory.id,
                onSelect = { newId -> categoryId = newId }
            )

            Spacer(Modifier.height(12.dp))

            // 备注
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                placeholder = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // 日期 + 时刻 + 账户
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { datePickerOpen = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(DateUtil.dayLabel(date).split(" · ").last())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = DateUtil.timeLabel(recordTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                AccountPicker(
                    selected = account,
                    onSelect = { account = it }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 操作按钮：编辑时附删除，右侧为「取消 / 确定」
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (initial != null) {
                    TextButton(onClick = {
                        vm.delete(initial.id)
                        vm.toast("已删除")
                        onDismiss()
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }

                Button(
                    onClick = {
                        val cents = Money.parseYuan(amount)
                        if (cents <= 0) {
                            vm.toast("请输入金额")
                            return@Button
                        }
                        val rec = Record(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            type = type,
                            amount = cents,
                            category = effectiveCategory.id,
                            date = date,
                            note = note.trim(),
                            account = account,
                            createdAt = if (initial != null) initial.createdAt else recordTime
                        )
                        vm.upsert(rec)
                        vm.toast(if (initial == null) "已添加" else "已更新")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = amountCents > 0
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("确定")
                }
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = DateUtil.fromUtcMillis(it) }
                    datePickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("取消") }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/* ---------------- 分类网格 ---------------- */

@Composable
private fun CategoryGrid(
    list: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = 220.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = true
    ) {
        items(list, key = { it.id }) { cat ->
            CategoryCell(cat = cat, selected = cat.id == selectedId) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun CategoryCell(cat: Category, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(cat.color).copy(alpha = 0.20f)
    else MaterialTheme.colorScheme.surfaceContainer
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(
                if (selected) Modifier.border(1.5.dp, Color(cat.color), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = cat.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = cat.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/* ---------------- 账户选择 ---------------- */

@Composable
private fun AccountPicker(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("账户：$selected")
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ACCOUNTS.forEach { acc ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(acc) },
                    onClick = {
                        onSelect(acc); expanded = false
                    }
                )
            }
        }
    }
}

/* ---------------- 金额输入 ---------------- */

/** 编辑已有记录时，把「分」还原为输入框字符串，例如 1234 -> "12.34" */
private fun formatInput(cents: Long): String {
    if (cents <= 0) return ""
    val yuan = cents / 100.0
    return if (yuan == yuan.toLong().toDouble()) yuan.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", yuan).trimEnd('0').trimEnd('.')
}

/**
 * 过滤系统输入法的输入：
 *  - 仅保留数字与小数点
 *  - 只允许一个小数点
 *  - 小数部分最多两位，整数部分最多九位
 *  - 去掉多余的前导零
 */
private fun filterAmountInput(input: String): String {
    var s = input.filter { it.isDigit() || it == '.' }

    // 只保留第一个小数点
    val firstDot = s.indexOf('.')
    if (firstDot >= 0) {
        s = s.substring(0, firstDot + 1) + s.substring(firstDot + 1).replace(".", "")
    }

    val dot = s.indexOf('.')
    var intPart = if (dot >= 0) s.substring(0, dot) else s
    var decPart = if (dot >= 0) s.substring(dot + 1) else ""

    if (intPart.length > 9) intPart = intPart.take(9)
    if (decPart.length > 2) decPart = decPart.take(2)

    // 小数点前无内容时补 0（"." → "0."）
    if (dot >= 0 && intPart.isEmpty()) intPart = "0"

    if (intPart.length > 1) {
        intPart = intPart.trimStart('0')
        if (intPart.isEmpty()) intPart = "0"
    }

    return if (dot >= 0) "$intPart.$decPart" else intPart
}
