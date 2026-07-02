package com.brandon.expensestracker.data

import com.brandon.expensestracker.data.local.ExpenseDao
import com.brandon.expensestracker.data.local.ExpenseEntity
import com.brandon.expensestracker.data.local.AuthData
import com.brandon.expensestracker.data.local.PreferencesStore
import com.brandon.expensestracker.data.local.SettingsData
import com.brandon.expensestracker.data.remote.MasterAuthApi
import com.brandon.expensestracker.data.remote.parseRemoteDataMap
import com.brandon.expensestracker.data.remote.string
import com.brandon.expensestracker.domain.Expense
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val preferencesStore: PreferencesStore,
    private val api: MasterAuthApi,
    private val gson: Gson,
    private val appId: String = "expenses-tracker"
) {
    val expensesFlow: Flow<List<Expense>> = expenseDao.observeAll().map { rows ->
        rows.map {
            Expense(
                id = it.id,
                title = it.title,
                category = it.category,
                amount = it.amount,
                date = it.date
            )
        }
    }

    val settingsFlow: Flow<SettingsData> = preferencesStore.settingsFlow
    val authFlow: Flow<AuthData> = preferencesStore.authFlow

    suspend fun saveExpense(expense: Expense) {
        val entity = ExpenseEntity(
            id = expense.id,
            title = expense.title,
            category = expense.category,
            amount = expense.amount,
            date = expense.date
        )
        if (expense.id == 0L) {
            expenseDao.insert(entity)
        } else {
            expenseDao.update(entity)
        }
        preferencesStore.setLastSync(Instant.now().toString())
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.delete(
            ExpenseEntity(
                id = expense.id,
                title = expense.title,
                category = expense.category,
                amount = expense.amount,
                date = expense.date
            )
        )
        preferencesStore.setLastSync(Instant.now().toString())
    }

    suspend fun updateSettings(monthlyIncome: Double, payDay: Int) {
        preferencesStore.setSettings(monthlyIncome, payDay)
        preferencesStore.setLastSync(Instant.now().toString())
    }

    suspend fun register(email: String, password: String): Result<String> {
        return runCatching {
            val payload = JsonObject().apply {
                addProperty("email", email)
                addProperty("password", password)
                addProperty("apps", appId)
            }

            val response = api.register(payload)
            val status = response.string("status")
            val passwordKey = response.string("password_key")

            if (status != "success-registered" || passwordKey.isBlank()) {
                error(if (status.isNotBlank()) status else "registration-failed")
            }

            preferencesStore.setAuth(email = email, appId = appId, passwordKey = passwordKey)
            passwordKey
        }
    }

    suspend fun login(email: String, password: String): Result<String> {
        return runCatching {
            val payload = JsonObject().apply {
                addProperty("email", email)
                addProperty("password", password)
                addProperty("apps", appId)
            }

            val response = api.login(payload)
            val status = response.string("status")
            val passwordKey = response.string("password_key")

            if (status != "success-login" || passwordKey.isBlank()) {
                error(if (status.isNotBlank()) status else "login-failed")
            }

            preferencesStore.setAuth(email = email, appId = appId, passwordKey = passwordKey)
            passwordKey
        }
    }

    suspend fun logout(clearLocalData: Boolean) {
        preferencesStore.clearAuth()
        if (clearLocalData) {
            expenseDao.clearAll()
            preferencesStore.setSettings(monthlyIncome = 0.0, payDay = 1)
        }
    }

    suspend fun syncCloudData(forcePull: Boolean = false): Result<String> {
        return runCatching {
            val auth = authFlow.first()
            if (!auth.isLoggedIn) {
                error("Please login first")
            }

            val remote = api.getAppConfig(
                mapOf(
                    "email" to auth.email,
                    "apps" to auth.appId,
                    "password_key" to auth.passwordKey
                )
            )

            if (remote.string("status") == "invalid-password_key") {
                logout(clearLocalData = false)
                error("Session expired. Please login again.")
            }

            val normalizedLastSync = remote.string("last_sync")
            val remoteDataMap = parseRemoteDataMap(remote.get("data"), gson)

            val localExpenses = expenseDao.getAll()
            val localSettings = settingsFlow.first()
            val localLastSync = auth.lastSync

            val localHasData = localExpenses.isNotEmpty() || localSettings.monthlyIncome > 0
            val remoteHasData = remoteDataMap.isNotEmpty()
            val localStampMs = localLastSync.toEpochMsOrZero()
            val remoteStampMs = normalizedLastSync.toEpochMsOrZero()

            val shouldPullFromCloud = forcePull || (
                remoteHasData && (!localHasData || localStampMs == 0L || remoteStampMs > localStampMs)
            )

            if (shouldPullFromCloud) {
                applyRemoteSnapshot(remoteDataMap)
                preferencesStore.setLastSync(
                    if (normalizedLastSync.isBlank() || normalizedLastSync == "new-data") {
                        Instant.now().toString()
                    } else {
                        normalizedLastSync
                    }
                )
                "Data restored from cloud"
            } else {
                uploadLocalDataToCloud()
                "Cloud sync complete"
            }
        }
    }

    suspend fun uploadLocalDataToCloud(): Result<Unit> {
        return runCatching {
            val auth = authFlow.first()
            if (!auth.isLoggedIn) {
                return@runCatching
            }

            val response = api.updateAppConfig(
                JsonObject().apply {
                    addProperty("email", auth.email)
                    addProperty("apps", auth.appId)
                    addProperty("password_key", auth.passwordKey)
                    add("app_data", gson.toJsonTree(buildLocalSnapshot()))
                }
            )

            val status = response.string("status")
            if (status != "data-updated") {
                if (status == "invalid-password_key") {
                    logout(clearLocalData = false)
                }
                error(if (status.isBlank()) "data-update-failed" else status)
            }

            preferencesStore.setLastSync(Instant.now().toString())
        }
    }

    private suspend fun buildLocalSnapshot(): Map<String, String> {
        val expenses = expenseDao.getAll()
        val settings = settingsFlow.first()
        return mapOf(
            "expenses" to gson.toJson(expenses),
            "monthlyIncome" to settings.monthlyIncome.toString(),
            "payDay" to settings.payDay.toString()
        )
    }

    private suspend fun applyRemoteSnapshot(snapshot: Map<String, String>) {
        val expensesJson = snapshot["expenses"].orEmpty()
        val monthlyIncome = snapshot["monthlyIncome"].orEmpty().toDoubleOrNull() ?: 0.0
        val payDay = (snapshot["payDay"].orEmpty().toIntOrNull() ?: 1).coerceIn(1, 31)

        val cloudExpenses = runCatching {
            gson.fromJson(expensesJson, Array<ExpenseEntity>::class.java).toList()
        }.getOrDefault(emptyList())

        expenseDao.clearAll()
        cloudExpenses.forEach { expense ->
            expenseDao.insert(expense.copy(id = 0))
        }

        preferencesStore.setSettings(monthlyIncome = monthlyIncome, payDay = payDay)
    }

    private fun String.toEpochMsOrZero(): Long {
        if (isBlank() || this == "new-data") {
            return 0
        }
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0)
    }
}
