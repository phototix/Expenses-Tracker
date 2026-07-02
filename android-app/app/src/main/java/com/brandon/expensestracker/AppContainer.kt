package com.brandon.expensestracker

import android.content.Context
import androidx.room.Room
import com.brandon.expensestracker.data.ExpenseRepository
import com.brandon.expensestracker.data.local.AppDatabase
import com.brandon.expensestracker.data.local.PreferencesStore
import com.brandon.expensestracker.data.remote.MasterAuthApi
import com.google.gson.Gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(context: Context) {
    private val gson = Gson()

    private val database = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "expense-tracker.db"
    ).build()

    private val preferencesStore = PreferencesStore(context)

    private val api: MasterAuthApi = Retrofit.Builder()
        .baseUrl("https://api.brandon.my/v1/api/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(MasterAuthApi::class.java)

    val repository = ExpenseRepository(
        expenseDao = database.expenseDao(),
        preferencesStore = preferencesStore,
        api = api,
        gson = gson
    )
}
