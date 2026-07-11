# Task 6 Report: Privacy, Widget, External Launches, and Share Preview

## Scope

Task 6 hardens the application lock and every external entry surface, separates privacy policy by presentation surface, removes database and DataStore access from widget provider callbacks, and adds a tokenized external-launch queue with an editable, idempotent share preview.

Room schema 14 and all database entities remain unchanged.

## Implemented Changes

### Fail-closed application lock

- Added explicit `Loading`, `Locked`, `Authenticating`, `Unlocked`, and `Unavailable` states.
- Moved biometric policy out of Compose into an activity-scoped ViewModel and gateway.
- Root composition creates `MoneyApp` and its `NavHost` only for `StartupMigrationState.Ready + AppLockState.Unlocked`.
- Uses `elapsedRealtime` for immediate, 30-second, 1-minute, and 5-minute relock policies.
- Screen-off locks immediately; every background event invalidates the authentication generation.
- Capability checks, callbacks, and suspended enable persistence all revalidate generation tokens.
- Uses pure biometric authentication (`BIOMETRIC_WEAK`), without treating a PIN or device credential as biometric capability.
- The Android gateway cancels stale prompts and weakly rebinds to the newest Activity across recreation.
- Enabling the lock persists the lock and three external privacy defaults atomically, only after successful authentication.

### Privacy preferences and masking

- Added a durable one-time device marker for existing biometric users.
- The migration atomically enables recent-task, widget, and notification privacy without enabling the in-app mask.
- Added independent settings for in-app, recent-task, widget, and notification privacy.
- `FLAG_SECURE` is enabled before preferences load and can be added or removed at runtime.
- Preference read failures use a fail-closed privacy fallback instead of defaulting to visible amounts.
- Added explicit `AmountPrivacy`, `AmountSurface`, and `AmountVisibility` presentation policy.
- Screen formatting uses a Compose presentation adapter; preformatted ViewModel text explicitly uses the in-app surface.
- Input drafts continue to use editable plain amounts and are not accidentally masked.
- Notification content and widget text/accessibility descriptions use their independent privacy surfaces.

### Widget safety and refresh discipline

- Provider callbacks now only discover widget IDs, paint a safe placeholder, attach a lock-covered launch intent, and enqueue work.
- Removed provider-side `runBlocking`, repository access, Room access, and DataStore access.
- Worker exits before container or SQL access when there are no widget instances.
- Worker paints every instance with a no-digit safe placeholder before reading preferences or the ledger.
- A single Room transaction builds one current-month snapshot for all widget instances.
- Privacy is checked again immediately before rendering; failures leave the safe placeholder in place.
- Cancellation is rethrown instead of becoming a retry.
- Ledger and portable-setting invalidations plus widget-privacy changes use unique 750 ms replacement work.
- The XML 30-minute fallback remains unchanged.

### Notification privacy refresh

- Privacy refresh first cancels every active Money notification, removing any old visible amount.
- Current due and stale notifications are then rebuilt without relying on notification dedupe cursors.
- Privacy/content read or publish failures leave old notifications cancelled.
- The force-refresh path does not modify ordinary occurrence or stale-boundary acknowledgement state.

### Tokenized external launch queue

- Added immutable typed destinations for shortcuts, share, notifications, and Widget Home.
- Initial intents and every `onNewIntent` are parsed above navigation and queued FIFO.
- Queue state is SavedState-backed, deduplicated by token, and acknowledged only after actual navigation.
- Routing remains unavailable while startup is not ready or the app is not unlocked.
- Notification payloads contain stable identity only and are resolved from current data after unlock.
- External source intents are cleared of action, data, type, clip data, and extras after parsing.
- Pending requests are capped at 8, acknowledged history at 64, and share text at 4,000 characters to keep saved state bounded.
- Notification state-change Snackbar display is asynchronous and cannot block queue acknowledgement.

### Share parsing and preview

- Added NFKC normalization for full-width digits, punctuation, and currency symbols.
- Supports grouped amounts and Chinese income/outflow keywords.
- Dates, long order identifiers, phone numbers, non-positive values, negative values, and overflow are rejected or down-ranked.
- Equally plausible different amounts remain ambiguous and require user editing.
- Added a lock-covered preview with original context, uncertainty, direction, amount, open account, optional note, date, and time.
- The preview writes no ledger row before explicit confirmation.
- Draft fields and operation ID use `SavedStateHandle`; submission is single-flight.
- Note input over 200 characters is preserved with a field error and rejected on save rather than silently truncated.
- Recreate/retry/replay uses the same operation ID; the real in-memory ledger path produces at most one row.

## Security Review

An independent security/privacy review ran four review waves. The final review returned PASS with no remaining Critical or Important findings. The review findings included:

1. A suspended enable write could overwrite a newer screen-off lock. The request token and state are now revalidated after persistence.
2. A suspended capability check could start a prompt after a newer lock generation. Capability attempts now have their own generation token.
3. Failed first-time enable attempts could leave a user in a non-retryable lock screen. Unpersisted enable failures return safely to unlocked settings.
4. Notification privacy could leave old visible notifications active. The force-refresh path cancels first and rebuilds from current private content.
5. Widget privacy could leave old visible RemoteViews after a read failure or race. Every refresh now paints safe placeholders first and rechecks privacy before rendering.
6. Privacy refresh originally cancelled more active notifications than it rebuilt. It now rebuilds the complete finite set of keys that were active at the privacy boundary, without scanning new due or stale candidates.
7. Enable persistence originally shared state with pre-authentication enable attempts. A separate persistence-in-flight state now keeps the app locked until the write succeeds or safely recovers after failure.

The review also identified minor external-validation and cancellation issues; expected due times are now strictly positive, shared intents require text MIME types, and Worker cancellation is rethrown.

## Verification

The final post-review command was run in one non-parallel, single-use Gradle process with the Kotlin compiler in-process:

```powershell
.\gradlew.bat "-Dkotlin.compiler.execution.strategy=in-process" test kspDebugKotlin compileDebugAndroidTestKotlin assembleDebug lintDebug --rerun-tasks --no-daemon --no-parallel
```

Results:

- 60 Gradle tasks executed successfully.
- Full JVM suite: 428 tests, 0 failures, 0 errors, 0 skipped.
- KSP and production Kotlin compile: passed.
- AndroidTest Kotlin compile: passed.
- Debug APK assembly: passed.
- Lint: passed with 0 errors. The report contains 35 existing project warnings; no warning points to a Task 6 changed source file. The only related path is an untouched Widget layout baseline-alignment warning.
- `git diff --check`: passed; only line-ending conversion notices were printed.
- Room schema 14: no diff.
- Provider callback guard: no `runBlocking`, repository, DataStore, container, query, or database access.
- In-app Screen guard: no direct `AmountFormatter.format` calls outside the privacy presentation adapter.
- Manifest exact-alarm guard: no exact-alarm permission.
- Widget XML fallback remains `1800000` milliseconds.

Connected-device tests were compiled but require an available emulator or device to execute.
