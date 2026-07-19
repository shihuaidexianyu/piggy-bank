# AGENTS.md

This file contains essential context for AI coding agents working on the **Money** Android project.

## Project Overview

**Money** is a fully offline personal finance tracking app for Android, built with Kotlin and Jetpack Compose.
It supports multi-account management (ordering, hiding, closing, reopening), cash flow recording, transfers, balance reconciliation, manual balance adjustments, recurring reminders (with background notifications), history search, plaintext JSON backup export/import, a net-worth savings goal, a home-screen widget, app shortcuts, share-to-record, biometric app lock, amount privacy masking, and dark mode.

- **Package / Application ID**: `com.shihuaidexianyu.money`
- **Version**: `2.4.4` (versionCode `109`)
- **Min SDK**: 31 (Android 12)
- **Target/Compile SDK**: 36
- **Language**: Kotlin 2.2.20
- **Java compatibility**: VERSION_17
- **User-facing strings**: All UI text is in Chinese (Simplified). Code, comments, and this documentation are in English.

## Technology Stack

| Layer | Technology |
| ------- | ------------ |
| UI | Jetpack Compose (BOM 2025.10.01) + Material 3 |
| Architecture | Clean Architecture (Domain / Data / UI) + MVVM |
| Database | Room 2.8.0 (SQLite) with KSP 2.3.2, schema version 14 |
| Settings | Room (`portable_settings`, backupable) + DataStore Preferences 1.1.7 (device-local) |
| Navigation | Navigation Compose 2.9.5 |
| Serialization | kotlinx.serialization 1.9.0 (JSON backup export/import) |
| Background work | WorkManager 2.10.1 (notification sync, widget refresh) |
| App entry | AndroidX Splashscreen 1.0.1, edge-to-edge |
| Biometrics | AndroidX Biometric 1.2.0-alpha05 |
| DI | Manual (no Hilt/Dagger/Koin) via `MoneyAppContainer` → `DataGraph` + `UseCaseGraph` |
| Benchmarking | Macrobenchmark 1.4.1 + UI Automator (`:benchmark` module) |
| Testing | JUnit 4 + `kotlin.test` assertions + Turbine + coroutines-test + Room testing |
| Build | AGP 9.1.0, Gradle wrapper, version catalog in `gradle/libs.versions.toml` |

## Build and Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires a signing config, see below)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"

# Run a single test method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"

# Run Android instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Debug lint
./gradlew lintDebug
```

Notes:

- On Windows, use `gradlew.bat` from PowerShell/CMD; `./gradlew` works in Git Bash.
- `gradle.properties` sets `android.disallowKotlinSourceSets=false`, a documented compatibility bridge required for KSP 2.x under AGP 9.1. Do not remove it without an atomic toolchain upgrade.
- Release builds fail fast with a `GradleException` if no release keystore is configured — this is intentional, to never silently fall back to the debug key.

### Automated Release Script

A PowerShell release script is provided at `scripts/build-release.ps1`:

```powershell
# Bump patch version and build release APK
.\scripts\build-release.ps1

# Run tests, build, commit, push, and tag
.\scripts\build-release.ps1 -RunTests -Commit -Push -Tag
```

The script:

- Auto-bumps `versionName` and `versionCode` in `app/build.gradle.kts` if not provided via `-VersionName` / `-VersionCode`.
- Checks that the git working tree is clean before modifying release files unless `-AllowDirty` is passed.
- Sets isolated `GRADLE_USER_HOME` (`.gradle-local`) and `ANDROID_USER_HOME` (`.android-user-home-release`) for reproducible release builds.
- Expects `JAVA_HOME` at `C:\Program Files\Android\Android Studio\jbr`.
- Verifies the built APK signature with `apksigner` (located via `ANDROID_SDK_ROOT` or `local.properties` `sdk.dir`) so a debug-signed release can never ship unnoticed.
- Optionally commits the version bump, pushes the branch, and creates/pushes an annotated `v<versionName>` tag.

### Benchmark Module

`:benchmark` is a self-instrumenting `com.android.test` module targeting `:app` (`benchmark/src/main/java/.../AppShellMacrobenchmark.kt`). It measures cold startup, history-tab frame timing, and large-dataset (10k rows) home rendering. The app module declares a matching `benchmark` build type (inherits `release`, signed with the debug key). Run it against a device/emulator with:

```bash
./gradlew :benchmark:connectedCheck
```

## Project Structure

Two Gradle modules: `:app` (the application) and `:benchmark` (macrobenchmark tests).

```text
app/src/main/java/com/shihuaidexianyu/money/
├── MainActivity.kt              # Entry point: FragmentActivity (BiometricPrompt host), splash screen,
│                                #   edge-to-edge, app-lock gate, startup-migration gate, launch queue
├── MoneyApplication.kt          # Application class (creates container, notification channels, startup
│                                #   migration, schedules workers/widget, installs widget refresh triggers)
├── MoneyApp.kt                  # Root Compose app
├── MoneyAppContainer.kt         # Top-level DI container (delegates to DataGraph + UseCaseGraph)
├── di/                          # Manual DI wiring
│   ├── DataGraph.kt             #   Database, repositories, file writers/readers, startup migration
│   ├── UseCaseGraph.kt          #   40+ use cases and shared helpers
│   └── SystemTimeProviders.kt   #   System clock/zone providers
├── data/
│   ├── dao/                     # Room DAOs (incl. HistoryRecordDao union query, LedgerAggregateDao)
│   ├── db/                      # MoneyDatabase (version 14) + DataStore extensions
│   ├── migration/               # StartupMigrationCoordinator, RoomStartupMigrationBackend,
│   │                            #   LegacySourceRecoveryExporter (legacy-store upgrade + recovery)
│   ├── debug/                   # DebugSampleDataSeeder (debug builds only)
│   ├── entity/                  # Room entities (10 tables)
│   ├── export/                  # ExportJsonFileWriter (plaintext JSON only)
│   ├── backup/                  # BackupJsonCodec (v4, v1→v4 migrations), staged import, safety
│   │                            #   snapshots, import receipts, BackupRepositoryImpl
│   └── repository/              # Repository implementations + InMemory* test variants
├── domain/
│   ├── model/                   # Enums, value objects, settings models, backup/
│   │                            #   (@Serializable DTOs: MoneyBackupSnapshot, schema version 4)
│   ├── repository/              # Repository interfaces only (see Architecture Rules)
│   ├── usecase/                 # Business logic: use cases + calculators/projectors/policies
│   ├── launch/                  # AppLaunchRequest model (external entry points)
│   ├── notification/            # Notification contracts (keys, commands, publisher/sync interfaces)
│   └── time/                    # Clock/time providers, ClockTicker, MutationTimestamp
├── navigation/                  # MoneyDestination routes, nav graphs (top-level, accounts, record,
│                                #   balance, reminder), adaptive shell policy, ViewModel factories,
│                                #   query codecs
├── notification/                # WorkManager-backed notification sync
│   ├── MoneyNotificationScheduler.kt      # 15-min periodic + debounced one-time sync (v2 work names)
│   ├── MoneyNotificationWorker.kt         # Unified notification projection worker
│   ├── RecurringReminderWorker.kt         # Due-reminder notifications
│   ├── BalanceCheckWorker.kt              # Stale-account "balance needs check" notifications
│   ├── AndroidMoneyNotificationPublisher.kt  # Channels + posting
│   └── NotificationLaunchIntentConsumer.kt   # Notification deep links
├── widget/
│   ├── BalanceOverviewWidgetProvider.kt   # Home-screen widget (total assets + month flow)
│   ├── WidgetRefreshCoordinator.kt        # Debounced refresh requests, privacy generation
│   └── WidgetUpdateWorker.kt              # Periodic widget refresh
├── ui/
│   ├── accounts/                # Accounts list, detail, create, edit, reorder
│   ├── balance/                 # Balance update, batch reconcile, update/adjustment detail & edit
│   ├── common/                  # Shared composables: amount keypad, date/time pickers, account picker,
│   │                            #   async content scaffolding, form policies, snackbar queue, UiEffect
│   ├── history/                 # History & search screen
│   ├── home/                    # Home dashboard
│   ├── launch/                  # Launch-intent parsing + routing queue (shortcuts/share/notifications)
│   ├── lock/                    # Biometric app lock (AppLockScreen, AppLockViewModel, gateway)
│   ├── record/                  # Record/edit cash flow & transfer
│   ├── reminder/                # Recurring reminders + notification permission gateway
│   ├── settings/                # App settings (incl. export/import) + savings goal screen
│   ├── share/                   # Share-to-record preview (ACTION_SEND text/plain)
│   └── theme/                   # Material 3 theming (light + dark + dynamic color)
└── util/                        # Formatters, parsers, time utilities, validators, share-text extractor
```

## Architecture Rules

### Clean Architecture Layers

1. **Domain** (`domain/`): Pure Kotlin. No Android framework dependencies.
   - `model/`: Enums and value objects plus `@Serializable` backup DTOs (`MoneyBackupSnapshot`, `MONEY_BACKUP_SCHEMA_VERSION = 4`).
   - `repository/`: Interfaces only — `AccountRepository`, `TransactionRepository`, `LedgerAggregateRepository`, `PortableSettingsRepository`, `DevicePreferencesRepository`, `AccountReminderSettingsRepository`, `RecurringReminderRepository`, `SavingsGoalRepository`, `BackupRepository`, `BackupJsonEncoder`, `DatabaseTransactionRunner`. (The former monolithic `SettingsRepository` was split: portable settings live in Room and travel with backups; device preferences live in DataStore and never leave the device.)
   - `usecase/`: Single-responsibility business logic plus shared helpers (`LedgerBalanceCalculator`, `HomeProjector`, `MonthlyBudgetPolicy`, `ReminderNextDueCalculator`, validators). Use cases accept repository interfaces via constructor.

2. **Data** (`data/`):
   - `entity/`: Room entities. Amounts are always stored as `Long` (cents/fen).
   - `dao/`: Room DAOs. All four ledger record types use `deletedAt` soft deletion and unique `operationId` values. `HistoryRecordDao` unions 4 tables with keyset pagination; `LedgerAggregateDao` serves aggregate queries.
   - `repository/`: Concrete implementations plus `InMemory*` variants (`InMemoryAccountRepository`, `InMemoryTransactionRepository`, `InMemoryAccountReminderSettingsRepository`, `InMemoryRecurringReminderRepository`, `InMemorySavingsGoalRepository`, `InMemoryPortableSettingsRepository`, `InMemoryDevicePreferencesRepository`) for unit tests.
   - `db/MoneyDatabase.kt`: Room database (current version = 14, `exportSchema = true` to `app/schemas/`).
   - `migration/`: `StartupMigrationCoordinator` runs the legacy-store/settings upgrade before the ledger is exposed, surfacing recoverable-error states (retry, use current database, reset settings, export legacy source).
   - `export/`: `ExportJsonFileWriter` writes plaintext `.json` files with collision-resistant names.
   - `backup/`: `BackupJsonCodec` (kotlinx.serialization + v1→v4 migrations), staged URI copies, validated safety snapshots, durable import receipts, and `BackupRepositoryImpl`.

3. **UI** (`ui/`):
   - One package per feature.
   - Each screen has a paired `ViewModel` exposing a single `StateFlow<UiState>`.
   - Compose UI collects state and triggers events back to the ViewModel.

### Dependency Injection

All dependencies are wired manually. `MoneyAppContainer` delegates to two graph objects:

- `di/DataGraph.kt` — creates the Room database, repositories, the plaintext export writer, staged backup reader, safety snapshot/receipt stores, the notification publisher/sync requester, and the startup migration backend. `AccountReminderSettingsRepositoryImpl` is wrapped in `NotificationSyncingAccountReminderSettingsRepository` so config changes trigger a notification sync.
- `di/UseCaseGraph.kt` — constructs 40+ use cases, wiring repository interfaces and shared helper use cases (e.g. `RefreshAccountActivityStateUseCase`, `CalculateCurrentBalanceUseCase`, `ObserveSavingsGoalUseCase`).

ViewModels are created via `moneyViewModelFactory` in `navigation/NavigationViewModels.kt`. Do **not** introduce Hilt, Dagger, or Koin without explicit approval.

### Startup Sequence

`MoneyApplication.onCreate` creates notification channels synchronously, builds the container, then on a background scope: runs `StartupMigrationCoordinator.runMigration()` and waits for `StartupMigrationState.Ready`, applies notification privacy, schedules `MoneyNotificationScheduler.scheduleAfterReady(...)`, schedules the widget update, installs widget refresh triggers (Room `InvalidationTracker` + `hideWidgetAmounts` observer), and seeds debug sample data (debuggable builds only). Ledger access before `Ready` is gated by the coordinator.

### Mutation Side-Effect Pattern

After any data mutation, use cases must refresh derived state:

- Create/Update/Delete cash flow and transfer records run the mutation, then call `RefreshAccountActivityStateUseCase` for affected accounts.
- Balance updates are ordinary ledger events with a fixed `delta`; changing older records must not rewrite later balance update deltas.
- Balance updates and manual adjustments call `RefreshAccountActivityStateUseCase`.
- Closed accounts are read-only. Use mutation use cases rather than repositories directly so lifecycle guards are applied.

### Notifications

- A unified `MoneyNotificationWorker` projects due reminders and stale-account balance checks. Scheduling is a 15-minute periodic unique work (`money-notification-sync-v2`, `UPDATE` policy) plus debounced one-time syncs (1s delay, `REPLACE` / `APPEND_OR_REPLACE` for continuations) requested through the domain `NotificationSyncRequester` interface.
- Legacy unique work names (`recurring-reminder-check`, `balance-check`) are cancelled at startup.
- `POST_NOTIFICATIONS` is declared for Android 13+; `ui/reminder/NotificationPermissionGateway.kt` handles the runtime request. No exact-alarm permission is used.
- Notification amounts can be masked independently via the `hideNotificationAmounts` device preference.

## Code Style Guidelines

- **Kotlin code style**: Official (set in `gradle.properties`).
- **Formatting**: 4-space indentation.
- **Naming**: Standard Kotlin conventions (`PascalCase` for types, `camelCase` for functions/variables).
- **Imports**: Prefer explicit imports; avoid wildcard imports.
- **Coroutines**: Use `viewModelScope` in ViewModels. Use `runBlocking` only in tests or initialization.
- **User-facing strings**: All UI text is in **Chinese (Simplified)**. Keep it consistent.
- **Amount handling**: Never use `Float`/`Double` for money. Always use `Long` representing the smallest currency unit (cents / fen).

## Testing Instructions

### Unit Tests

Located in `app/src/test/java/com/shihuaidexianyu/money/` (100+ test classes covering use cases, balance calculators, backup codec/import coordinator, reminder scheduling, ViewModels, formatters, and policies).

- Use the `InMemory*` repository variants for fast, hermetic tests.
- Wrap suspend calls in `runBlocking`.
- Assertions use `kotlin.test.assertEquals` and JUnit 4 (`@Test`).
- Flow testing uses **Turbine** and **kotlinx-coroutines-test**.
- `androidx.room.testing` and `org.json` are available on the unit test classpath.

### Instrumented Tests

Located in `app/src/androidTest/` — a substantial suite, not a placeholder: Room migration tests (`MoneyDatabaseMigrationTest`, using the exported schemas bundled as androidTest assets), Room/repository contract tests, Compose UI tests (home, navigation, pickers, accessibility semantics, large-text reachability), notification channel/launch tests, and intent pipeline tests.

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

1. Bump `MONEY_DATABASE_VERSION` in `MoneyDatabase.kt`.
2. Add a `Migration` object in `MoneyDatabase.kt`.
3. Register it in `MONEY_DATABASE_MIGRATIONS`.
4. Ensure the exported schema JSON is updated (run `./gradlew kspDebugKotlin` or build).
5. Extend `MoneyDatabaseMigrationTest` in androidTest to cover the new migration.

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
- **Settings split**: `PortableSettings` (Room `portable_settings` table) travel with backups; `DevicePreferences` (DataStore: biometric lock, amount masks, recents hiding, widget/notification privacy) are device-local and never exported.
- **External entry points**: App shortcuts, share-to-record (`ACTION_SEND` `text/plain`), widget, and notification deep links are normalized into `AppLaunchRequest`s and routed through the launch queue in `ui/launch/`.

## Security Considerations

- **No networking**: The app is entirely offline. No API keys, tokens, or remote endpoints (no `INTERNET` permission in the manifest).
- **Data export**: Manual export writes unencrypted JSON to app-private cache and shares it only through a `FileProvider` URI under `cache/exports/`. The UI must warn users to save it only to a trusted location.
- **Pre-import backup**: Before any replacement, `SafetySnapshotStore` atomically writes and verifies a snapshot under `filesDir/pre_import_backups/`; `ImportReceiptStore` records its hash and supports rollback without importing device-local privacy preferences.
- **Biometric lock**: Optional app-wide biometric lock (`ui/lock/`: `AppLockScreen` + `AppLockViewModel` + `AndroidBiometricAuthenticationGateway`, hosted by `MainActivity` as a `FragmentActivity`), gated by a device preference.
- **Privacy masking**: Amounts can be masked in-app, in the widget (`hideWidgetAmounts`, with safe placeholder rendering), and in notifications (`hideNotificationAmounts`) independently.
- **Backup**: `AndroidManifest.xml` sets `allowBackup="false"` and provides `dataExtractionRules`. The app does not rely on Android automatic cloud/device-transfer backup; use manual export for user-controlled data transfer.
- **Signing**: Release builds require `signing/keystore.properties` (gitignored; the whole `signing/` directory is excluded), with `../timeline/keystore.properties` as a legacy fallback. The build throws if neither exists. Never commit keystore files or `keystore.properties`.
- **Debug data**: `DebugSampleDataSeeder` seeds sample data only when `ApplicationInfo.FLAG_DEBUGGABLE` is true.

## Common Pitfalls for Agents

- Do **not** use `Float`/`Double` for monetary amounts. Use `Long` (cents).
- Do **not** add new DI frameworks. Use `MoneyAppContainer`.
- When deleting any ledger record, set `deletedAt` rather than hard-deleting it; restore through the matching use case.
- Do **not** re-anchor balances on the latest reconciliation or recalculate later reconciliation deltas. Reconciliation deltas are fixed ledger events.
- After any mutation use case, ensure `RefreshAccountActivityStateUseCase` is invoked for affected accounts.
- Do not mutate closed accounts through repositories directly; reopen through the lifecycle use case first.
- Do not touch the ledger before `StartupMigrationCoordinator` reports `Ready`; use `withReadyLedgerAccess` where applicable.
- All new UI strings must be in Chinese (Simplified).
- If changing Room entities, always provide a migration, update the schema export, and extend the androidTest migration test.
- `CLAUDE.md` is a legacy snapshot and partially outdated (e.g. it references a removed `SettingsRepository` and an old database version); treat this `AGENTS.md` as the source of truth.
