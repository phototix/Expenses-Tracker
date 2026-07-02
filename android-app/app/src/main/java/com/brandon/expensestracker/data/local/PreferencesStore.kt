package com.brandon.expensestracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "expense_tracker_prefs")

data class SettingsData(
    val monthlyIncome: Double = 0.0,
    val payDay: Int = 1
)

data class AuthData(
    val email: String = "",
    val appId: String = "expenses-tracker",
    val passwordKey: String = "",
    val lastSync: String = ""
) {
    val isLoggedIn: Boolean
        get() = email.isNotBlank() && passwordKey.isNotBlank()
}

class PreferencesStore(private val context: Context) {
    companion object {
        private val KEY_MONTHLY_INCOME = doublePreferencesKey("monthly_income")
        private val KEY_PAY_DAY = intPreferencesKey("pay_day")
        private val KEY_EMAIL = stringPreferencesKey("auth_email")
        private val KEY_APP_ID = stringPreferencesKey("auth_app_id")
        private val KEY_PASSWORD_KEY = stringPreferencesKey("auth_password_key")
        private val KEY_LAST_SYNC = stringPreferencesKey("auth_last_sync")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            SettingsData(
                monthlyIncome = prefs[KEY_MONTHLY_INCOME] ?: 0.0,
                payDay = (prefs[KEY_PAY_DAY] ?: 1).coerceIn(1, 31)
            )
        }

    val authFlow: Flow<AuthData> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            AuthData(
                email = prefs[KEY_EMAIL].orEmpty(),
                appId = prefs[KEY_APP_ID] ?: "expenses-tracker",
                passwordKey = prefs[KEY_PASSWORD_KEY].orEmpty(),
                lastSync = prefs[KEY_LAST_SYNC].orEmpty()
            )
        }

    suspend fun setSettings(monthlyIncome: Double, payDay: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MONTHLY_INCOME] = monthlyIncome
            prefs[KEY_PAY_DAY] = payDay.coerceIn(1, 31)
        }
    }

    suspend fun setAuth(email: String, appId: String, passwordKey: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = email
            prefs[KEY_APP_ID] = appId
            prefs[KEY_PASSWORD_KEY] = passwordKey
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_PASSWORD_KEY)
            prefs.remove(KEY_LAST_SYNC)
        }
    }

    suspend fun setLastSync(lastSyncIso: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = lastSyncIso
        }
    }
}
