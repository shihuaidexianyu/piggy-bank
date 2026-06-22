# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"

# Run a single test method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

## Architecture

This is a single-module Android app (`:app`) using **Clean Architecture** with **MVVM** and **Jetpack Compose**.

### Language & UI

- Kotlin, Jetpack Compose with Material 3
- All user-facing strings are in Chinese (Simplified)
- Amounts are stored as `Long` (cents/fen), not floating point

### Layers

**Domain** (`domain/`):
- `model/` — Enums and value objects (`CashFlowDirection`, `HomePeriod`, `ThemeMode`, `AppSettings`, etc.)
- `repository/` — Interfaces: `AccountRepository`, `TransactionRepository`, `SettingsRepository`, `AccountReminderSettingsRepository`
- `usecase/` — Business logic. Use cases take repository interfaces as constructor params. Key patterns:
  - Mutation use cases (Create/Update/Delete) call `RefreshAccountActivityStateUseCase` after modifying data
  - Reconciliation records are fixed ledger events; older record edits do not rewrite later reconciliation deltas
  - `CalculateCurrentBalanceUseCase` and `CalculateAccountBalancesUseCase` share `LedgerBalanceCalculator` semantics

**Data** (`data/`):
- `entity/` — Room entities (stored amounts in Long/cents)
- `dao/` — Room DAOs. Soft-delete pattern on cash flow and transfer records (`isDeleted` field)
- `db/` — `MoneyDatabase` (Room, version 10), `LegacyMoneyStoreImporter` for migration from prior format
- `repository/` — Implementations including `InMemory*` variants used in unit tests

**UI** (`ui/`):
- Screens organized by feature: `home/`, `history/`, `accounts/`, `balance/`, `record/`, `settings/`
- Each feature screen has a paired ViewModel
- `common/` — Shared composables
- `theme/` — Material 3 theming with dark mode support

**Navigation** (`navigation/`):
- `MoneyDestination` sealed class defines all routes (4 top-level tabs: Home, History, Accounts, Settings)
- Split into sub-graphs: `TopLevelNavGraph`, `AccountsNavGraph`, `RecordNavGraph`, `BalanceNavGraph`
- Route helpers use companion functions like `MoneyDestination.accountDetailRoute(id)`

### Dependency Injection

Manual DI via `MoneyAppContainer` — no Hilt/Dagger/Koin. All repositories and use cases are wired in this class. ViewModels are created using `moneyViewModelFactory` helper from `NavigationViewModels.kt`.

### Testing

Unit tests use `InMemoryAccountRepository`, `InMemoryTransactionRepository`, and `InMemoryAccountReminderSettingsRepository` — in-memory implementations of the domain repository interfaces. Tests run with `runBlocking` and JUnit 4 + `kotlin.test` assertions.

### Key Domain Concepts

- **Accounts**: Active accounts are user-ordered. Archived accounts are read-only and kept for historical records.
- **Account opening**: `initialBalance` is the opening asset event, and in-period account openings count as opening assets in dashboards.
- **Transaction types**: CashFlow (inflow/outflow), Transfer (between accounts), BalanceUpdate (reconciliation delta event), BalanceAdjustment (manual correction)
- **Balance calculation**: Before account opening balance is `0`; from opening onward balance is `initialBalance + inflow - outflow + transferIn - transferOut + manualAdjustment + reconciliationDelta`
- **Settings**: Stored via DataStore Preferences (`SettingsRepositoryImpl`), not Room
