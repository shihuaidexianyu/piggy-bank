# AGENTS.md

This file contains essential context for AI coding agents working on the **Money** Android project.

## Project Overview

**Money** is a personal finance tracking app for Android, built with Kotlin and Jetpack Compose.
It supports multi-account management, cash flow recording, transfers, balance updates, recurring reminders (with background notifications), history search, plaintext JSON data export/import, a net-worth savings goal, a home-screen widget, app shortcuts, share-to-record, biometric lock, and dark mode.

- **Package**: `com.shihuaidexianyu.money`
- **Application ID**: `com.shihuaidexianyu.money`
- **Version**: `2.3.0` (versionCode `103`)
- **Min SDK**: 31 (Android 12)
- **Target/Compile SDK**: 36
- **Language**: Kotlin 2.2.20
- **Java compatibility**: VERSION_17

## Technology Stack

| Layer | Technology |
| ------- | ------------ |
| UI | Jetpack Compose (BOM 2025.10.01) + Material 3 |
| Architecture | Clean Architecture (Domain / Data / UI) + MVVM |
| Database | Room 2.8.0 (SQLite) with KSP |
| Settings | DataStore Preferences 1.1.7 |
| Navigation | Navigation Compose 2.9.5 |
| Serialization | kotlinx.serialization 1.9.0 (JSON export/import/backup) |
| Background work | WorkManager 2.10.1 (reminder checks, balance checks, widget refresh) |
| Scheduling | WorkManager (15-minute periodic check plus debounced one-time sync) |
| Biometrics | AndroidX Biometric 1.2.0-alpha05 |
| DI | Manual (no Hilt/Dagger/Koin) via `MoneyAppContainer` → `DataGraph` + `UseCaseGraph` |
| Reminders | In-app due display **plus** WorkManager-backed background notifications |
| Testing | JUnit 4 + `kotlin.test` assertions + Turbine + coroutines-test |

## Build and Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"

# Run a single test method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

### Automated Release Script

A PowerShell release script is provided at `scripts/build-release.ps1`:

```powershell
# Bump patch version and build release APK
.\scripts\build-release.ps1

# Run tests, build, commit, and push
.\scripts\build-release.ps1 -RunTests -Commit -Push
```

The script:

- Auto-bumps `versionName` and `versionCode` in `app/build.gradle.kts` if not provided.
- Checks that the git working tree is clean before modifying release files unless `-AllowDirty` is passed.
- Sets isolated `GRADLE_USER_HOME` and `ANDROID_USER_HOME` for reproducible release builds.
- Expects `JAVA_HOME` at `C:\Program Files\Android\Android Studio\jbr`.
- Signs release builds using `signing/keystore.properties` (local, gitignored) if present, otherwise falls back to `../timeline/keystore.properties`. The keystore file itself is referenced by the `storeFile` property there.

## Project Structure

```text
app/src/main/java/com/shihuaidexianyu/money/
├── MainActivity.kt              # Entry point (splash + biometric gate + edge-to-edge)
├── MoneyApplication.kt          # Application class (schedules workers/alarms/widget)
├── MoneyApp.kt                  # Root Compose app
├── MoneyAppContainer.kt         # Top-level DI container (delegates to DataGraph + UseCaseGraph)
├── di/                          # Manual DI wiring
│   ├── DataGraph.kt             #   Database, 6 repositories, file writers/readers
│   └── UseCaseGraph.kt          #   35+ use cases
├── data/
│   ├── dao/                     # Room DAOs
│   ├── db/                      # MoneyDatabase, migrations, DataStore extensions, legacy importer
│   ├── debug/                   # DebugSampleDataSeeder
│   ├── entity/                  # Room entities
│   ├── export/                  # ExportJsonFileWriter (plaintext JSON only)
│   ├── backup/                  # v4 codec, staged import, safety snapshots, receipts, repository
│   └── repository/              # Repository implementations + in-memory test variants
├── domain/
│   ├── model/                   # Enums, value objects, AppSettings, @Serializable backup DTOs
│   ├── repository/              # Repository interfaces (incl. BackupRepository, BackupJsonEncoder)
│   └── usecase/                 # Business logic / use cases
├── navigation/                  # Nav graphs, destinations, ViewModel factories, query codec
├── notification/                # WorkManager-backed notifications and channels
│   ├── ReminderNotificationScheduler.kt   # Schedules 15-min periodic workers
│   ├── RecurringReminderWorker.kt         # Posts due-reminder notifications
│   ├── BalanceCheckWorker.kt             # Posts stale-account "balance needs check" notifications
│   ├── ReminderAlarmScheduler.kt          # Exact-alarm scheduling + ReminderAlarmReceiver
│   └── ReminderNotifications.kt           # Notification channels + posting helpers
├── widget/
│   ├── BalanceOverviewWidgetProvider.kt   # Home-screen widget (total assets + month flow)
│   └── WidgetUpdateWorker.kt              # Periodic widget refresh
├── ui/
│   ├── accounts/                # Accounts list, detail, create, edit, reorder
│   ├── balance/                 # Balance update, batch reconcile, adjustment detail
│   ├── common/                  # Shared composables, UiEffect, BiometricGatekeeper, Account picker, amount keypad
│   ├── history/                 # History & search screen
│   ├── home/                    # Home dashboard
│   ├── record/                  # Record cash flow / transfer
│   ├── reminder/                # Recurring reminders
│   ├── settings/                # App settings (incl. export/import)
│   ├── stats/                   # Statistics screens (asset-flow diagram)
│   └── theme/                   # Material 3 theming (light + dark + dynamic color)
└── util/                        # Formatters, parsers, time utilities, validators, share-text extractor
```

## Architecture Rules

### Clean Architecture Layers

1. **Domain** (`domain/`): Pure Kotlin. No Android framework dependencies.
   - `model/`: Enums and value objects plus `@Serializable` backup DTOs (`MoneyBackupSnapshot`, schema version 4).
   - `repository/`: Interfaces only (`AccountRepository`, `TransactionRepository`, `SettingsRepository`, `AccountReminderSettingsRepository`, `RecurringReminderRepository`, `BackupRepository`, `BackupJsonEncoder`).
   - `usecase/`: Single-responsibility business logic. Use cases accept repository interfaces via constructor.

2. **Data** (`data/`):
   - `entity/`: Room entities. Amounts are always stored as `Long` (cents/fen).
   - `dao/`: Room DAOs. All four ledger record types use `deletedAt` soft deletion and unique `operationId` values. `HistoryRecordDao` unions 4 tables with keyset pagination.
   - `repository/`: Concrete implementations plus `InMemory*` variants for unit tests.
   - `db/MoneyDatabase.kt`: Room database (current version = 14, `exportSchema = true`).
   - `export/`: `ExportJsonFileWriter` writes plaintext `.json` files with collision-resistant names.
   - `backup/`: `BackupJsonCodec` (kotlinx.serialization + v1→v4 migrations), staged URI copies, validated safety snapshots, durable import receipts, and `BackupRepositoryImpl`.

3. **UI** (`ui/`):
   - One package per feature.
   - Each screen has a paired `ViewModel` exposing a single `StateFlow<UiState>`.
   - Compose UI collects state and triggers events back to the ViewModel.

### Dependency Injection

All dependencies are wired manually. `MoneyAppContainer` delegates to two graph objects:

- `di/DataGraph.kt` — creates the Room database, repositories, plaintext export writer, staged backup reader, safety snapshot/receipt stores, and the startup migration backend.
- `di/UseCaseGraph.kt` — constructs 40+ use cases, wiring repository interfaces and shared helper use cases (e.g. `RefreshAccountActivityStateUseCase`, `CalculateCurrentBalanceUseCase`, `ObserveSavingsGoalsUseCase`).

ViewModels are created via `moneyViewModelFactory` in `navigation/NavigationViewModels.kt`. Do **not** introduce Hilt, Dagger, or Koin without explicit approval.

### Mutation Side-Effect Pattern

After any data mutation, use cases must refresh derived state:

- Create/Update/Delete cash flow and transfer records run the mutation, then call `RefreshAccountActivityStateUseCase` for affected accounts.
- Balance updates are ordinary ledger events with a fixed `delta`; changing older records must not rewrite later balance update deltas.
- Balance updates and manual adjustments call `RefreshAccountActivityStateUseCase`.
- Closed accounts are read-only. Use mutation use cases rather than repositories directly so lifecycle guards are applied.

## Code Style Guidelines

- **Kotlin code style**: Official (set in `gradle.properties`).
- **Formatting**: 4-space indentation.
- **Naming**: Standard Kotlin conventions (`PascalCase` for types, `camelCase` for functions/variables).
- **Imports**: Prefer explicit imports; avoid wildcard imports.
- **Coroutines**: Use `viewModelScope` in ViewModels. Use `runBlocking` only in tests or initialization (e.g., `LegacyMoneyStoreImporter`).
- **User-facing strings**: All UI text is in **Chinese (Simplified)**. Keep it consistent.
- **Amount handling**: Never use `Float`/`Double` for money. Always use `Long` representing the smallest currency unit (cents / fen).

## Testing Instructions

### Unit Tests

Located in `app/src/test/java/com/shihuaidexianyu/money/`.

- Use `InMemoryAccountRepository`, `InMemoryTransactionRepository`, `InMemoryAccountReminderSettingsRepository`, and `InMemoryRecurringReminderRepository` for fast, hermetic tests.
- Wrap suspend calls in `runBlocking`.
- Assertions use `kotlin.test.assertEquals` and JUnit 4 (`@Test`).
- Flow testing uses **Turbine** and **kotlinx-coroutines-test**.

### Instrumented Tests

Located in `app/src/androidTest/`. Currently minimal (`ExampleInstrumentedTest`).

### Running Tests

Always run unit tests before submitting changes:

```bash
./gradlew test
```

## Database Migrations

Room schema is exported to `app/schemas/`. Current database version is **14**.

Existing migrations:

- `1 → 2`: Re-created a historical balance adjustment index.
- `2 → 3`: Added `recurring_reminders` table.
- `3 → 4`: Dropped `investment_settlements` table and related indexes.
- `4 → 5`: Re-created `accounts` without the legacy group field.
- `5 → 6`: Added legacy visual fields (`iconName`, `colorName`).
- `6 → 7`: Re-created `accounts` without `iconName`, keeping `colorName`.
- `7 → 8`: Re-created transaction tables with foreign keys and refreshed indexes.
- `8 → 9`: Re-created `balance_adjustment_records` without `sourceUpdateRecordId`; rows with `sourceUpdateRecordId != 0` are dropped (data-losing by design).
- `9 → 10`: Re-added `iconName` column to `accounts` (TEXT NOT NULL DEFAULT 'wallet').
- `10 → 11`: Added `savings_goals` and `savings_goal_account_links` tables (many-to-many with CASCADE/RESTRICT).
- `11 → 12`: Re-created `savings_goals` without the `colorName` column (savings goals use the app primary color).
- `12 → 13`: Removed savings-goal account links and reduced the goal model to a net-worth target.
- `13 → 14`: Rebuilt accounts and ledger tables for hidden/closed lifecycle state, tombstones, operation IDs, reminder anchors, portable settings, reminder configs, and migration state.

When modifying entities:

1. Bump `@Database(version = ...)`.
2. Add a `Migration` object in `MoneyDatabase.kt`.
3. Register it in `addMigrations(...)`.
4. Ensure the exported schema JSON is updated (run `./gradlew kspDebugKotlin` or build).

## Key Domain Concepts

- **Accounts**: Open accounts are user-ordered and may be hidden without changing calculations. A zero-balance account may be closed and later reopened; closed accounts are read-only.
- **Account creation**: The account's `initialBalance` is the opening asset event. For period dashboards, accounts opened inside the selected period contribute their initial balance to opening assets, not cash inflow or asset adjustment.
- **Transaction types**:
  - `CashFlow`: Inflow / outflow with an optional note.
  - `Transfer`: Between two accounts.
  - `BalanceUpdate`: Reconciliation adjustment. It stores `actualBalance` and `systemBalanceBeforeUpdate` as evidence, but only its fixed `delta` affects ledger balance.
  - `BalanceAdjustment`: Manual correction ledger event.
- **Balance calculation**: Uses `LedgerBalanceCalculator` semantics: before account opening the balance is `0`; from opening onward balance is `initialBalance + inflow - outflow + transferIn - transferOut + manualAdjustment + reconciliationDelta`.
- **Reminders**: Recurring reminders use `MONTHLY`, `YEARLY`, or `CUSTOM_DAYS` periods anchored to the first due time. WorkManager performs a 15-minute periodic check plus debounced one-time synchronization. No exact-alarm permission is used.
- **Export/import**: Backup schema v4 contains portable settings, accounts, all four ledger record types (including tombstones and operation IDs), reminders, account reminder configs, and the optional singleton savings goal. Export is plaintext JSON only. Import first copies the selected URI into private cache, validates and previews the same bytes, writes a verified safety snapshot, and replaces portable data in one Room transaction. Durable receipts provide conditional rollback.
- **Savings goal**: A nullable singleton (`id = 1`) represents one net-worth target. Progress uses total current net assets and has no deadline.

## Security Considerations

- **No networking**: The app is entirely offline. No API keys, tokens, or remote endpoints.
- **Data export**: Manual export writes unencrypted JSON to app-private cache and shares it only through a `FileProvider` URI under `cache/exports/`. The UI must warn users to save it only to a trusted location.
- **Pre-import backup**: Before any replacement, `SafetySnapshotStore` atomically writes and verifies a snapshot under `filesDir/pre_import_backups/`; `ImportReceiptStore` records its hash and supports rollback without importing device-local privacy preferences.
- **Biometric lock**: Optional app-wide biometric lock (`BiometricGatekeeper` in `ui/common/`), gated by the `biometricLock` setting.
- **Backup**: `AndroidManifest.xml` sets `allowBackup="false"`. The app does not rely on Android automatic cloud/device-transfer backup; use manual export for user-controlled data transfer.
- **Signing**: Release builds use `signing/keystore.properties` when available, with the legacy external path as fallback. Never commit keystore files or `keystore.properties`.
- **Debug data**: `DebugSampleDataSeeder` seeds sample data only when `ApplicationInfo.FLAG_DEBUGGABLE` is true.

## Common Pitfalls for Agents

- Do **not** use `Float`/`Double` for monetary amounts. Use `Long` (cents).
- Do **not** add new DI frameworks. Use `MoneyAppContainer`.
- When deleting any ledger record, set `deletedAt` rather than hard-deleting it; restore through the matching use case.
- Do **not** re-anchor balances on the latest reconciliation or recalculate later reconciliation deltas. Reconciliation deltas are fixed ledger events.
- After any mutation use case, ensure `RefreshAccountActivityStateUseCase` is invoked for affected accounts.
- Do not mutate closed accounts through repositories directly; reopen through the lifecycle use case first.
- All new UI strings must be in Chinese (Simplified).
- If changing Room entities, always provide a migration and update the schema export.
