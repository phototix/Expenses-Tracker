package com.brandon.expensestracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.brandon.expensestracker.domain.Expense
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsState()
    val email by viewModel.authEmailInput.collectAsState()
    val password by viewModel.authPasswordInput.collectAsState()
    val incomeInput by viewModel.incomeEditorInput.collectAsState()
    val payDayInput by viewModel.payDayEditorInput.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbar.showSnackbar(state.message)
            viewModel.onMessageShown()
        }
    }

    val filteredExpenses = viewModel.filteredExpenses(state)
    val totalExpenses = filteredExpenses.sumOf { it.amount }
    val effectiveIncome = viewModel.summaryIncome(state)
    val remaining = effectiveIncome - totalExpenses
    val cycleOptions = viewModel.cycleOptions(state)

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Expense Tracker", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (state.auth.isLoggedIn) "Signed in as ${state.auth.email}" else "Not logged in",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    NavigationDrawerItem(
                        label = { Text("Account") },
                        selected = state.screen == AppScreen.ACCOUNT,
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        onClick = {
                            viewModel.openAccount()
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = state.screen == AppScreen.SETTINGS,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = {
                            viewModel.openSettings()
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        label = { Text("Sync Now") },
                        selected = false,
                        icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                        onClick = {
                            viewModel.syncNow()
                            scope.launch { drawerState.close() }
                        }
                    )

                    if (state.auth.isLoggedIn) {
                        NavigationDrawerItem(
                            label = { Text("Logout") },
                            selected = false,
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            onClick = {
                                viewModel.logout(clearLocalData = false)
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbar) },
                topBar = {
                    TopAppBar(
                        title = { Text(screenTitle(state.screen)) },
                        navigationIcon = {
                            if (state.screen == AppScreen.HOME) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            } else {
                                IconButton(onClick = viewModel::goHome) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (state.screen == AppScreen.HOME) {
                        FloatingActionButton(onClick = viewModel::openAddExpense) {
                            Icon(Icons.Default.Add, contentDescription = "Add expense", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            ) { padding ->
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen-transition"
                ) { screen ->
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            state = state,
                            filteredExpenses = filteredExpenses,
                            cycleOptions = cycleOptions,
                            selectedCycle = state.selectedCycle,
                            totalExpenses = totalExpenses,
                            effectiveIncome = effectiveIncome,
                            remaining = remaining,
                            onSelectCycle = viewModel::onSelectCycle,
                            cycleLabel = { key -> viewModel.cycleLabel(key, state.payDay) },
                            onEditExpense = viewModel::editExpense,
                            onDeleteExpense = viewModel::deleteExpense
                        )

                        AppScreen.EXPENSE_FORM -> ExpenseFormScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(12.dp),
                            state = state.editor,
                            onChange = viewModel::onEditorChange,
                            onSave = viewModel::saveExpense,
                            titleSuggestions = { query -> viewModel.titleSuggestions(query) },
                            categorySuggestions = { query -> viewModel.categorySuggestions(query) }
                        )

                        AppScreen.SETTINGS -> SettingsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(12.dp),
                            income = incomeInput,
                            payDay = payDayInput,
                            onIncomeChange = viewModel::onIncomeInputChange,
                            onPayDayChange = viewModel::onPayDayInputChange,
                            onSave = viewModel::saveSettings
                        )

                        AppScreen.ACCOUNT -> AccountScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(12.dp),
                            loggedIn = state.auth.isLoggedIn,
                            email = email,
                            password = password,
                            currentUser = state.auth.email,
                            isLoading = state.isLoading,
                            onEmailChange = viewModel::onAuthEmailChange,
                            onPasswordChange = viewModel::onAuthPasswordChange,
                            onRegister = viewModel::register,
                            onLogin = viewModel::login,
                            onLogout = { viewModel.logout(clearLocalData = false) },
                            onSyncNow = viewModel::syncNow
                        )
                    }
                }
            }
        }
    }
}

private fun screenTitle(screen: AppScreen): String {
    return when (screen) {
        AppScreen.HOME -> "Expense Tracker"
        AppScreen.EXPENSE_FORM -> "Add or Edit Expense"
        AppScreen.SETTINGS -> "Settings"
        AppScreen.ACCOUNT -> "Account"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    modifier: Modifier,
    state: AppUiState,
    filteredExpenses: List<Expense>,
    cycleOptions: List<String>,
    selectedCycle: String,
    totalExpenses: Double,
    effectiveIncome: Double,
    remaining: Double,
    onSelectCycle: (String) -> Unit,
    cycleLabel: (String) -> String,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit
) {
    val cycleExpanded = remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SummaryCards(
                monthlyIncome = effectiveIncome,
                totalExpense = totalExpenses,
                remainingBalance = remaining
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = cycleExpanded.value,
                        onExpandedChange = { cycleExpanded.value = it }
                    ) {
                        OutlinedTextField(
                            value = cycleLabel(selectedCycle),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            label = { Text("Cycle Filter") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cycleExpanded.value) },
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                        ExposedDropdownMenu(
                            expanded = cycleExpanded.value,
                            onDismissRequest = { cycleExpanded.value = false }
                        ) {
                            cycleOptions.forEach { cycle ->
                                DropdownMenuItem(
                                    text = { Text(cycleLabel(cycle)) },
                                    onClick = {
                                        onSelectCycle(cycle)
                                        cycleExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category Summary", style = MaterialTheme.typography.titleMedium)
                    val categoryTotals = filteredExpenses.groupBy { it.category }.mapValues { entry ->
                        entry.value.sumOf { it.amount }
                    }
                    if (categoryTotals.isEmpty()) {
                        Text("No data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        categoryTotals.forEach { (category, amount) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category)
                                Text("$${"%.2f".format(amount)}")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No expenses found",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    onEdit = { onEditExpense(expense) },
                    onDelete = { onDeleteExpense(expense) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun AccountScreen(
    modifier: Modifier,
    loggedIn: Boolean,
    email: String,
    password: String,
    currentUser: String,
    isLoading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit
) {
    val showPassword = rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cloud Account", style = MaterialTheme.typography.titleMedium)
            Text(
                if (loggedIn) "Logged in as $currentUser" else "Not logged in",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!loggedIn) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword.value) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword.value = !showPassword.value }) {
                            Icon(
                                if (showPassword.value) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword.value) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRegister) { Text("Register") }
                    Button(onClick = onLogin) { Text("Login") }
                }
            } else {
                OutlinedTextField(
                    value = currentUser,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Signed in email") },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSyncNow) { Text("Sync Now") }
                    Button(onClick = onLogout) { Text("Logout") }
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    income: String,
    payDay: String,
    onIncomeChange: (String) -> Unit,
    onPayDayChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = income,
                onValueChange = onIncomeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Monthly Income") }
            )
            OutlinedTextField(
                value = payDay,
                onValueChange = onPayDayChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pay Day (1-31)") }
            )
            Button(onClick = onSave) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun ExpenseFormScreen(
    modifier: Modifier,
    state: ExpenseEditorState,
    onChange: (ExpenseEditorState) -> Unit,
    onSave: () -> Unit,
    titleSuggestions: (String) -> List<String>,
    categorySuggestions: (String) -> List<String>
) {
    var activeSuggestionField by remember { mutableStateOf("") }
    val titleMatches = titleSuggestions(state.title)
    val categoryMatches = categorySuggestions(state.category)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.id == 0L) "Add Expense" else "Edit Expense", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.title,
                onValueChange = { onChange(state.copy(title = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            activeSuggestionField = "title"
                        } else if (activeSuggestionField == "title") {
                            activeSuggestionField = ""
                        }
                    },
                label = { Text("Title") }
            )

            SuggestionList(
                query = state.title,
                matches = if (activeSuggestionField == "title") titleMatches else emptyList(),
                onSelect = { suggestion -> onChange(state.copy(title = suggestion)) }
            )

            OutlinedTextField(
                value = state.category,
                onValueChange = { onChange(state.copy(category = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            activeSuggestionField = "category"
                        } else if (activeSuggestionField == "category") {
                            activeSuggestionField = ""
                        }
                    },
                label = { Text("Category") }
            )

            SuggestionList(
                query = state.category,
                matches = if (activeSuggestionField == "category") categoryMatches else emptyList(),
                onSelect = { suggestion -> onChange(state.copy(category = suggestion)) }
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = { onChange(state.copy(amount = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") }
            )
            OutlinedTextField(
                value = state.date,
                onValueChange = { onChange(state.copy(date = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD)") }
            )
            Button(onClick = onSave) {
                Text(if (state.id == 0L) "Add Expense" else "Update Expense")
            }
        }
    }
}

@Composable
private fun SuggestionList(
    query: String,
    matches: List<String>,
    onSelect: (String) -> Unit
) {
    if (query.isBlank() || matches.isEmpty()) {
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            matches.forEach { suggestion ->
                Button(
                    onClick = { onSelect(suggestion) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(suggestion)
                }
            }
        }
    }
}

@Composable
private fun SummaryCards(monthlyIncome: Double, totalExpense: Double, remainingBalance: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Monthly Income")
                Text("$${"%.2f".format(monthlyIncome)}")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Expenses")
                Text("$${"%.2f".format(totalExpense)}")
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining")
                Text("$${"%.2f".format(remainingBalance)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.SemiBold)
                Text("${expense.category} - ${expense.date}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$${"%.2f".format(expense.amount)}")
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
