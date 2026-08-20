# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**`AGENTS.md` in the repo root is the detailed source of truth** (full package map, migration history, domain glossary, security notes). Read it when you need depth beyond this summary.

## Project

**Money** — a fully offline personal finance app for Android (Kotlin + Jetpack Compose, package `com.shihuaidexianyu.money`). No `INTERNET` permission, no networking, no remote endpoints. minSdk 31, target/compile SDK 36, Java 17.

**All user-facing strings are Chinese (Simplified); code, comments, and docs are English.**

## Commands

```bash
./gradlew assembleDebug                 # debug APK
./gradlew assembleRelease               # requires a release keystore; fails loudly otherwise
./gradlew test                          # all unit tests
./gradlew testDebugUnitTest             # faster: one variant only
./gradlew lintDebug
./gradlew connectedAndroidTest          # instrumented tests (device/emulator)
./gradlew :benchmark:connectedCheck     # macrobenchmarks (device/emulator)
./gradlew kspDebugKotlin                # export Room schema JSON after entity changes

# single test class / method
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest"
./gradlew test --tests "com.shihuaidexianyu.money.CalculateCurrentBalanceUseCaseTest.balance without update uses initial balance and records"
```

On Windows use `gradlew.bat` from PowerShell; `./gradlew` works from Git Bash. Run `./gradlew test` before submitting changes.

Release packaging goes through `scripts/build-release.ps1` (auto version bump, isolated `GRADLE_USER_HOME`/`ANDROID_USER_HOME`, `apksigner` signature verification, optional commit/push/tag):

```powershell
.\scripts\build-release.ps1 -RunTests -Commit -Push -Tag
```

The script expects `JAVA_HOME` at `C:\Program Files\Android\Android Studio\jbr` and verifies the APK signature so a debug-signed release can never ship unnoticed.

`gradle.properties` sets `android.disallowKotlinSourceSets=false` — a documented KSP-2.x-under-AGP-9.1 compatibility bridge. Configuration fails without it; only remove it as part of an atomic toolchain upgrade.

## Architecture

Two Gradle modules: `:app` and `:benchmark` (self-instrumenting `com.android.test` module targeting `:app`; `:app` declares a matching `benchmark` build type inheriting `release` but signed with the debug key).

Clean Architecture + MVVM under `app/src/main/java/com/shihuaidexianyu/money/`:

- **`domain/`** — pure Kotlin, no Android deps. `repository/` holds interfaces only; `usecase/` holds single-responsibility use cases plus shared calculators/projectors/policies (`LedgerBalanceCalculator`, `HomeProjector`, `BudgetPolicy`, `ReminderNextDueCalculator`); `model/backup/` holds the `@Serializable` snapshot DTOs.
- **`data/`** — Room entities/DAOs, repository impls, `db/MoneyDatabase.kt`, `backup/` (JSON codec + staged import + safety snapshots), `export/`, `migration/` (startup legacy-store upgrade).
- **`ui/`** — one package per feature; each screen has a paired ViewModel exposing a single `StateFlow<UiState>`.
- **`navigation/`, `notification/`, `util/`** — routes and nav graphs, WorkManager-backed notification sync, formatters/parsers.

### Dependency injection is manual — do not add Hilt/Dagger/Koin

`MoneyAppContainer` delegates to `di/DataGraph.kt` (database, repositories, file writers, notification publisher, startup migration backend) and `di/UseCaseGraph.kt` (40+ use cases). ViewModels are constructed via `moneyViewModelFactory` in `navigation/NavigationViewModels.kt`.

### Startup gating

`MoneyApplication.onCreate` creates notification channels, builds the container, then on a background scope runs `StartupMigrationCoordinator.runMigration()` and waits for `StartupMigrationState.Ready` before scheduling notification workers and seeding debug sample data. **Never touch the ledger before `Ready`** — use `withReadyLedgerAccess`. Debug sample data is seeded only when `ApplicationInfo.FLAG_DEBUGGABLE` is true.

### Ledger invariants

- **Money is always `Long` in the smallest currency unit (cents/fen).** Never `Float`/`Double`.
- Four record types: `CashFlow`, `Transfer`, `BalanceUpdate` (reconciliation), `BalanceAdjustment` (manual correction). All use `deletedAt` soft deletion and unique `operationId`s — never hard-delete; restore through the matching use case.
- Balance = `initialBalance + inflow - outflow + transferIn - transferOut + manualAdjustment + reconciliationDelta`; zero before the account's opening. A `BalanceUpdate` stores `actualBalance`/`systemBalanceBeforeUpdate` only as evidence — **its `delta` is fixed**. Editing older records must not rewrite later reconciliation deltas, and balances must not be re-anchored on the latest reconciliation.
- After any mutation, call `RefreshAccountActivityStateUseCase` for the affected accounts.
- Closed accounts are read-only; go through the lifecycle use case rather than the repository to reopen.
- **Account kind** is `FUNDING` (default) or `INVESTMENT`. Ledger arithmetic is unchanged, but a reconciliation delta on an investment account is presented as investment P&L at read time; reclassifying an account retroactively reinterprets its whole history.

### External entry points

App shortcuts and notification deep links are normalized into `AppLaunchRequest`s and routed through the launch queue in `ui/launch/`.

### Settings are split in two

`PortableSettings` live in the Room `portable_settings` table and travel with backups. `DevicePreferences` live in DataStore (biometric lock, in-app/notification amount masks, recents hiding) and never leave the device. There is no single `SettingsRepository`.

### Notification refresh

Notification sync uses a unified `MoneyNotificationWorker` (15-minute periodic unique work plus debounced one-time syncs). Legacy unique work names are cancelled at startup. Amounts can be masked independently via `hideNotificationAmounts`.

## Code style

- 4-space indentation, official Kotlin code style.
- Explicit imports; avoid wildcard imports.
- `viewModelScope` in ViewModels; `runBlocking` only in tests or initialization.
- All UI strings in Chinese (Simplified).
- Amounts as `Long` (cents/fen); never `Float`/`Double`.

## Database migrations

Room schema version **17**, exported to `app/schemas/` (bundled as androidTest assets). Version 17 removes the former `savings_goals` table. When changing entities:

1. Bump `MONEY_DATABASE_VERSION` in `MoneyDatabase.kt`.
2. Add the `Migration` object there and register it in `MONEY_DATABASE_MIGRATIONS`.
3. Build (or `./gradlew kspDebugKotlin`) so the schema JSON is exported.
4. Extend `MoneyDatabaseMigrationTest` in androidTest.

## Testing

- Unit tests: `app/src/test/` (~124 classes). Use the `InMemory*` repository variants for hermetic tests; JUnit 4 + `kotlin.test` assertions, Turbine + coroutines-test for flows, `runBlocking` for suspend calls.
- Instrumented tests: `app/src/androidTest/` — Room migration and repository contract tests, Compose UI tests (home, navigation, pickers, accessibility semantics, large-text reachability), notification and intent-pipeline tests.

## Security-relevant behavior

- Backups export **unencrypted** JSON to app-private cache, shared only via a `FileProvider` URI under `cache/exports/`; the UI must keep warning users to store it somewhere trusted.
- Import stages the URI into private cache, validates/previews the same bytes, writes a verified safety snapshot under `filesDir/pre_import_backups/`, then replaces portable data in one Room transaction, with durable receipts enabling rollback.
- Release signing reads `signing/keystore.properties` (gitignored, as is all of `signing/`), falling back to `../timeline/keystore.properties`. Never commit keystores.
- `allowBackup="false"` — the app deliberately does not use Android cloud/device-transfer backup.
- Biometric app lock and amount privacy masking (in-app and notifications independently) live in `DevicePreferences` and the `ui/lock/` / privacy gateways.
