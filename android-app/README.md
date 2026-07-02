# Expense Tracker Android App

Native Android implementation of the existing Expense Tracker using Kotlin + Jetpack Compose.

## Features

- Native expense CRUD (add, edit, delete)
- Login and register with MasterAuth API
- Offline-first local storage:
  - Room database for expenses
  - DataStore for settings and auth/session data
- Cloud sync with `config/app` API, compatible with the web app snapshot model
- Manual sync button and periodic background sync via WorkManager
- Cycle-based filtering using pay day

## Tech

- Kotlin
- Jetpack Compose (Material 3)
- Room
- DataStore Preferences
- Retrofit + Gson
- WorkManager

## Build APK

From this folder:

```bash
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Notes

- Base API URL is configured in `app/src/main/java/com/brandon/expensestracker/AppContainer.kt`.
- App identifier sent to API is `expenses-tracker`.
