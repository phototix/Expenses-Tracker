package com.brandon.expensestracker.domain

data class Expense(
    val id: Long = 0,
    val title: String,
    val category: String,
    val amount: Double,
    val date: String
)
