package com.jiyibi.ledger.data

/** 收支分类。color 为 ARGB 色值，色调与网页版保持一致。 */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long
)

object Categories {

    val expense = listOf(
        Category("food", "餐饮", "🍜", 0xFFF97066),
        Category("transport", "交通", "🚌", 0xFF4F8BFF),
        Category("shopping", "购物", "🛍️", 0xFFB176F0),
        Category("home", "居家", "🏠", 0xFFF7A23B),
        Category("telecom", "通讯", "📱", 0xFF22A2C9),
        Category("entertain", "娱乐", "🎮", 0xFFE5639C),
        Category("medical", "医疗", "💊", 0xFF39B980),
        Category("study", "学习", "📚", 0xFF6D5EF8),
        Category("social", "人情", "🎁", 0xFFEF6B7B),
        Category("sport", "运动", "🏃", 0xFF0EA5B7),
        Category("pet", "宠物", "🐾", 0xFFC58A4A),
        Category("other_e", "其他", "📦", 0xFF8B95A8)
    )

    val income = listOf(
        Category("salary", "工资", "💰", 0xFF12A669),
        Category("bonus", "奖金", "🏆", 0xFFF0A020),
        Category("parttime", "兼职", "💼", 0xFF4F8BFF),
        Category("invest", "投资", "📈", 0xFFE5484D),
        Category("redpack", "红包", "🧧", 0xFFE5484D),
        Category("reimburse", "报销", "🧾", 0xFF6D5EF8),
        Category("refund", "退款", "↩️", 0xFF22A2C9),
        Category("other_i", "其他", "📦", 0xFF8B95A8)
    )

    fun of(type: String): List<Category> = if (type == "income") income else expense

    fun find(type: String, id: String): Category =
        of(type).firstOrNull { it.id == id } ?: of(type).last()
}

/** 账户（支付方式） */
val ACCOUNTS = listOf("微信", "支付宝", "银行卡", "信用卡", "现金", "其他")
