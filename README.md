# Money

A personal finance tracking app for Android, built with Kotlin and Jetpack Compose.

## Features

- **Multi-account management** — Payment, Bank, and Investment account groups with independent tracking
- **Cash flow recording** — Track inflow and outflow with purpose tagging
- **Transfers** — Record money movements between accounts
- **Balance updates** — Periodic balance snapshots with automatic delta calculation
- **Investment settlements** — Automatic PnL and return rate calculation for investment accounts
- **Balance trend chart** — Visual balance history per account
- **History & search** — Filter by account, date range, amount, and keyword
- **Dark mode** — Full dark theme support following system settings
- **Data export** — Export all data as JSON

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture (Domain / Data / UI) + MVVM
- **Database**: Room (SQLite)
- **Settings**: DataStore Preferences
- **Navigation**: Navigation Compose
- **DI**: Manual dependency injection via `MoneyAppContainer`
- **Min SDK**: 31 (Android 12)

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

## Project Structure

```
app/src/main/java/com/shihuaidexianyu/money/
├── domain/          # Business logic (use cases, repository interfaces, models)
├── data/            # Room entities, DAOs, repository implementations
├── ui/              # Compose screens and ViewModels by feature
├── navigation/      # Navigation graph definitions
└── util/            # Formatting and helper utilities
```

## License

All rights reserved.
