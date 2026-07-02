package com.brandon.expensestracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brandon.expensestracker.data.ExpenseRepository
import com.brandon.expensestracker.data.local.AuthData
import com.brandon.expensestracker.data.local.SettingsData
import com.brandon.expensestracker.domain.Expense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ExpenseEditorState(
    val id: Long = 0,
    val title: String = "",
    val category: String = "",
    val amount: String = "",
    val date: String = LocalDate.now().toString()
)

enum class AppScreen {
    HOME,
    EXPENSE_FORM,
    SETTINGS,
    ACCOUNT
}

data class AppUiState(
    val expenses: List<Expense> = emptyList(),
    val monthlyIncome: Double = 0.0,
    val payDay: Int = 1,
    val auth: AuthData = AuthData(),
    val screen: AppScreen = AppScreen.HOME,
    val editor: ExpenseEditorState = ExpenseEditorState(),
    val selectedCycle: String = "all",
    val message: String = "",
    val isLoading: Boolean = false
)

class AppViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {
    private val editorState = MutableStateFlow(ExpenseEditorState())
    private val currentScreen = MutableStateFlow(AppScreen.HOME)
    private val selectedCycle = MutableStateFlow("all")
    private val message = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)

    private val emailInput = MutableStateFlow("")
    private val passwordInput = MutableStateFlow("")
    private val incomeInput = MutableStateFlow("")
    private val payDayInput = MutableStateFlow("1")

    private val baseStateFlow = combine(
        repository.expensesFlow,
        repository.settingsFlow,
        repository.authFlow
    ) { expenses, settings, auth ->
        Triple(expenses, settings, auth)
    }

    private val statusFlow = combine(message, isLoading) { msg, loading ->
        msg to loading
    }

    val uiState: StateFlow<AppUiState> = combine(
        baseStateFlow,
        currentScreen,
        editorState,
        selectedCycle,
        statusFlow
    ) { base, screen, editor, cycle, status ->
        val (msg, loading) = status
        val (expenses, settings, auth) = base
        AppUiState(
            expenses = expenses,
            monthlyIncome = settings.monthlyIncome,
            payDay = settings.payDay,
            auth = auth,
            screen = screen,
            editor = editor,
            selectedCycle = cycle,
            message = msg,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUiState())

    val authEmailInput: StateFlow<String> = emailInput
    val authPasswordInput: StateFlow<String> = passwordInput
    val incomeEditorInput: StateFlow<String> = incomeInput
    val payDayEditorInput: StateFlow<String> = payDayInput

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                if (incomeInput.value.isBlank()) {
                    incomeInput.value = settings.monthlyIncome.toString()
                }
                if (payDayInput.value == "1") {
                    payDayInput.value = settings.payDay.toString()
                }
            }
        }
    }

    fun onEditorChange(newState: ExpenseEditorState) {
        editorState.value = newState
    }

    fun goHome() {
        currentScreen.value = AppScreen.HOME
    }

    fun openAddExpense() {
        editorState.value = ExpenseEditorState()
        currentScreen.value = AppScreen.EXPENSE_FORM
    }

    fun openSettings() {
        val settings = uiState.value
        incomeInput.value = settings.monthlyIncome.toString()
        payDayInput.value = settings.payDay.toString()
        currentScreen.value = AppScreen.SETTINGS
    }

    fun openAccount() {
        if (emailInput.value.isBlank()) {
            emailInput.value = uiState.value.auth.email
        }
        currentScreen.value = AppScreen.ACCOUNT
    }

    fun onSelectCycle(cycle: String) {
        selectedCycle.value = cycle
    }

    fun onMessageShown() {
        message.value = ""
    }

    fun onAuthEmailChange(text: String) {
        emailInput.value = text
    }

    fun onAuthPasswordChange(text: String) {
        passwordInput.value = text
    }

    fun onIncomeInputChange(text: String) {
        incomeInput.value = text
    }

    fun onPayDayInputChange(text: String) {
        payDayInput.value = text
    }

    fun saveExpense() {
        val current = editorState.value
        val amount = current.amount.toDoubleOrNull()
        if (current.title.isBlank() || current.category.isBlank() || current.date.isBlank() || amount == null) {
            message.value = "Fill all expense fields"
            return
        }

        viewModelScope.launch {
            repository.saveExpense(
                Expense(
                    id = current.id,
                    title = current.title.trim(),
                    category = current.category.trim(),
                    amount = amount,
                    date = current.date.trim()
                )
            )
            repository.uploadLocalDataToCloud()
            editorState.value = ExpenseEditorState()
            currentScreen.value = AppScreen.HOME
            message.value = if (current.id == 0L) "Expense added" else "Expense updated"
        }
    }

    fun editExpense(expense: Expense) {
        editorState.value = ExpenseEditorState(
            id = expense.id,
            title = expense.title,
            category = expense.category,
            amount = expense.amount.toString(),
            date = expense.date
        )
        currentScreen.value = AppScreen.EXPENSE_FORM
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            repository.uploadLocalDataToCloud()
            message.value = "Expense deleted"
        }
    }

    fun saveSettings() {
        val income = incomeInput.value.toDoubleOrNull()
        val payDay = payDayInput.value.toIntOrNull()
        if (income == null || payDay == null) {
            message.value = "Invalid income or pay day"
            return
        }

        viewModelScope.launch {
            repository.updateSettings(monthlyIncome = income, payDay = payDay)
            repository.uploadLocalDataToCloud()
            currentScreen.value = AppScreen.HOME
            message.value = "Settings saved"
        }
    }

    fun register() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()
        if (email.isBlank() || password.isBlank()) {
            message.value = "Enter email and password"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            val result = repository.register(email, password)
            result.fold(
                onSuccess = {
                    repository.uploadLocalDataToCloud()
                    currentScreen.value = AppScreen.HOME
                    message.value = "Registration successful"
                },
                onFailure = { message.value = it.message ?: "Registration failed" }
            )
            isLoading.value = false
        }
    }

    fun login() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()
        if (email.isBlank() || password.isBlank()) {
            message.value = "Enter email and password"
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            val result = repository.login(email, password)
            result.fold(
                onSuccess = {
                    val syncResult = repository.syncCloudData(forcePull = true)
                    currentScreen.value = AppScreen.HOME
                    message.value = syncResult.getOrElse { it.message ?: "Sync failed" }
                },
                onFailure = { message.value = it.message ?: "Login failed" }
            )
            isLoading.value = false
        }
    }

    fun logout(clearLocalData: Boolean) {
        viewModelScope.launch {
            repository.logout(clearLocalData)
            message.value = "Logged out"
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.syncCloudData(forcePull = false)
            message.value = result.getOrElse { it.message ?: "Sync failed" }
            isLoading.value = false
        }
    }

    fun filteredExpenses(state: AppUiState): List<Expense> {
        if (state.selectedCycle == "all") {
            return state.expenses
        }
        return state.expenses.filter { expense ->
            cycleKey(expense.date, state.payDay) == state.selectedCycle
        }
    }

    fun cycleOptions(state: AppUiState): List<String> {
        val keys = state.expenses.mapNotNull { expense -> cycleKey(expense.date, state.payDay) }.distinct().sortedDescending()
        return listOf("all") + keys
    }

    fun summaryIncome(state: AppUiState): Double {
        val cycleCount = if (state.selectedCycle == "all") {
            filteredExpenses(state).mapNotNull { cycleKey(it.date, state.payDay) }.distinct().size.coerceAtLeast(1)
        } else {
            1
        }
        return state.monthlyIncome * cycleCount
    }

    fun titleSuggestions(query: String): List<String> {
        return rankedSuggestions(uiState.value.expenses.map { it.title }, query)
    }

    fun categorySuggestions(query: String): List<String> {
        return rankedSuggestions(uiState.value.expenses.map { it.category }, query)
    }

    private fun rankedSuggestions(values: List<String>, query: String): List<String> {
        val cleaned = values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        if (cleaned.isEmpty()) {
            return emptyList()
        }

        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            return cleaned.sortedBy { it.lowercase() }.take(6)
        }

        val startsWithMatches = cleaned.filter { it.lowercase().startsWith(normalizedQuery) }
        val containsMatches = cleaned.filter {
            !it.lowercase().startsWith(normalizedQuery) && it.lowercase().contains(normalizedQuery)
        }

        return (startsWithMatches + containsMatches).take(6)
    }

    private fun cycleKey(dateString: String, payDay: Int): String? {
        val date = runCatching { LocalDate.parse(dateString) }.getOrNull() ?: return null
        val currentStart = cycleStartForMonth(date.year, date.monthValue, payDay)
        val actualStart = if (date.isBefore(currentStart)) {
            val previousMonth = date.minusMonths(1)
            cycleStartForMonth(previousMonth.year, previousMonth.monthValue, payDay)
        } else {
            currentStart
        }
        return actualStart.toString()
    }

    private fun cycleStartForMonth(year: Int, month: Int, payDay: Int): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        val startDay = payDay.coerceAtMost(yearMonth.lengthOfMonth())
        return LocalDate.of(year, month, startDay)
    }

    fun cycleLabel(cycleKey: String, payDay: Int): String {
        if (cycleKey == "all") {
            return "All cycles"
        }
        val start = runCatching { LocalDate.parse(cycleKey) }.getOrNull() ?: return cycleKey
        val nextMonth = start.plusMonths(1)
        val nextStartDay = payDay.coerceAtMost(YearMonth.from(nextMonth).lengthOfMonth())
        val nextStart = LocalDate.of(nextMonth.year, nextMonth.monthValue, nextStartDay)
        val end = nextStart.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        return "${start.format(formatter)} - ${end.format(formatter)}"
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
