# Task 3A report — Idempotent ledger writes

## Status

DONE_WITH_CONCERNS

Commit message: `feat: make ledger writes idempotent`

The final handoff records the commit SHA. A commit cannot contain its own SHA
without changing that SHA.

## RED/GREEN evidence

Focused tests were written before their production contracts. The first
repository run failed to compile because the result, conflict, record-kind,
and operation-ID APIs did not exist. The caller-owned use-case tests then
exposed old `Long` results, internal ID generation, ambient time, and missing
ViewModel IDs. CAS and reminder tests likewise failed before those APIs
existed.

A later primary-key collision test failed behaviorally until InMemory was
aligned with Room's explicit-ID collision behavior.

Final focused suites:

- `LedgerInsertIdempotencyTest`: 7/7
- `LedgerOperationIdUseCaseTest`: 2/2
- `LedgerActiveUpdateCasTest`: 4/4
- `ProcessDueReminderUseCaseTest`: 6/6
- `LedgerFormSavedStateTest`: 4/4

The final debug-unit gate passed 208/208 tests.

## Exact idempotency contracts

- Cash compares account, direction, amount, trimmed note, and occurrence time.
- Transfer compares both accounts, amount, trimmed note, and occurrence time.
- Balance update compares account, actual balance, and occurrence time.
  Stored prior balance and delta remain first-insert evidence.
- Balance adjustment compares account, delta, and occurrence time.
- Comparisons ignore row ID and lifecycle timestamps. Operation IDs remain
  table-local.

New commands return `inserted=true`. Equal replays return the original row ID
with `inserted=false`. Payload mismatches throw
`LedgerOperationConflictException`. Tombstone replays return the original
tombstone without resurrection. Unrelated primary-key collisions fail
explicitly.

Room uses insert-or-ignore followed by operation-ID lookup in one transaction.
InMemory serializes mutations and rolls back all four collections, ID counters,
and change version on failure. Sixteen equal concurrent calls converge to one
row, one ID, and one inserted result.

## Use cases and reconciliation evidence

The four create use cases require a nonblank caller operation ID and a
`ClockProvider`. Each captures time once and performs replay lookup inside the
transaction before account guards or derived calculations. Replays therefore
work for later-closed accounts and tombstones. Activity refresh runs only for a
new insert.

Balance-update retries return the original stored evidence and fixed delta,
even after later ledger changes. Existing reconciliation deltas are never
rewritten.

## Race-safe updates

All four Room updates are active-row CAS statements keyed by row ID, original
operation ID, null deletion time, and expected old update time. They cannot
change operation ID, creation time, or deletion time. InMemory uses the same
predicate.

Mutation timestamps are strictly newer under a frozen or regressing clock.
Overflow at `Long.MAX_VALUE` is rejected. All four update use cases translate
CAS rejection into `LedgerRecordChangedException`.

Room change observation now relies on Room invalidation flows. Notifications
occur after commit, including same-count updates, and do not escape a rollback.

## Saved state and single flight

A dedicated SavedState-aware ViewModel factory uses
`CreationExtras.createSavedStateHandle()`. The existing no-argument factory is
unchanged.

Cash, transfer, and single-account balance forms persist one UUID. Batch
reconciliation persists one UUID per account and the batch occurrence time.
Recreation and partial-failure retry therefore retain both identity and
payload.

Every save path acquires a synchronous guard before launching. Validation or
save failure releases it. Tests cover double taps, fail-first retry, recreation,
stable batch IDs and time, and distinct IDs between accounts.

## Reminder occurrence processing

Reminder navigation carries the exact `expectedDueAt` into the cash form. Its
operation ID is:

```text
cash:reminder:<reminderId>:<expectedDueAt>
```

Replay lookup precedes reminder and account guards. A new command requires an
enabled reminder whose current due time matches, then inserts and advances it
with one CAS in the same Room transaction.

Tests cover replay, conflict, stale due time, the next occurrence, archived
accounts, and sixteen concurrent callers converging to one row and one
progression.

## Room/InMemory coverage

`TransactionRepositoryContractTest` compiles Android coverage for:

- all four kinds: insert, replay, conflict, tombstone, and CAS rejection
- all four kinds: sixteen-way concurrent Room convergence
- explicit primary-key collision
- rollback without phantom notification
- committed insert and same-count update notifications

No emulator or device was attached, so these tests were compiled but not run.

## Verification

Using the Android Studio JBR:

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:kspDebugKotlin
.\gradlew.bat :app:compileDebugAndroidTestKotlin `
  --rerun-tasks
.\gradlew.bat test --rerun-tasks
git diff --check
```

Results:

- Debug unit tests: success; 208 tests, no failures, errors, or skips.
- KSP/schema generation: success.
- Android instrumentation-test compilation: success.
- Aggregate JVM tests: success; 208 tests, no failures, errors, or skips.
- Diff check: exit 0; only LF-to-CRLF conversion warnings appeared.
- `adb devices`: no attached device.
- Database version remains 14. Entities, migrations, and schema JSON are
  unchanged.

## Changed areas

- Domain: contracts, repository APIs, IDs, clocks, create/update use cases,
  timestamp handling, and reminder processing.
- Data: four ledger DAOs, reminder CAS, Room/InMemory repositories,
  invalidation behavior, and manual DI.
- UI: SavedState factory, four form ViewModels, navigation factories, and the
  reminder occurrence route.
- Tests: focused JVM suites, Android repository contracts, and caller migration
  to explicit IDs, clocks, and results.

No account lifecycle, restore, Undo, savings-goal API, aggregate DAO, entity,
migration, or schema work was introduced.

## Self-review

An independent review found three P2 gaps: incomplete four-kind Room coverage,
missing CAS-failure use-case tests, and an eager notification counter that
could survive rollback. All three were fixed. Re-review found no remaining
blocking, correctness, or scope issue.

Direct checks confirmed early replay lookup, immutable reconciliation evidence,
four active CAS queries, real SavedState handles, replay before stale-due
rejection, no ambient wall-clock calls in touched domain code, and no Room
schema change.

## Concern

The Android tests were compiled only. In particular, the rollback negative
assertion uses a 100 ms settling window and was not exercised on-device here.

## Final review-gap fix wave

An independent final Task 3A review found additional in-scope gaps. They were
closed without changing the Room schema:

- The four DAO `insert` methods are strict `ABORT` again. Idempotent Room
  repositories alone use dedicated `insertOrIgnore` methods, so the legacy
  importer cannot silently commit a partial snapshot.
- Room and InMemory interactive ledger inserts now require `id == 0`, removing
  ambiguity between a legal SQLite row ID of `-1` and Room's ignored-insert
  sentinel.
- All four create use cases and reminder posting resolve replay inside the
  transaction before reading the clock, checking mutable account/reminder
  state, or deriving balance evidence. Equal replay therefore survives a
  device-clock rollback.
- Reminder posting compares and stores the form's selected account and visible
  direction. It never copies requested payload fields from an existing row.
- A processed reminder advances exactly one schedule period from
  `expectedDueAt`, using one occurrence CAS even when the reminder is severely
  overdue.
- Batch reconciliation freezes each account's actual balance together with its
  operation ID and the shared occurrence time in `SavedStateHandle`.
- System back on the balance result screen follows the same close-flow callback
  as the “完成” button.

### RED/GREEN evidence

The fix wave used separate RED/GREEN cycles:

- explicit IDs initially reached Room instead of failing before persistence;
- four create commands and reminder posting initially failed equal replay after
  clock rollback;
- an active edited row's current payload was initially accepted as the original
  create replay;
- severely overdue reminders initially skipped repeatedly to a future date;
- a recreated batch initially changed the failed command payload from `200` to
  `250` while retaining the same operation ID.

Each regression failed for the expected reason before its production change
and passed afterward. Three independent scoped reviews approved the DAO,
replay/reminder, and batch/back changes with no open findings.

### No-schema payload limitation

The current ledger row cannot prove its immutable first create payload after an
edit. Under the no-schema constraint, active rows with `updatedAt != createdAt`
therefore fail replay safely with `LedgerOperationConflictException` in both
Room and InMemory. Required equal tombstone replay remains supported. An
edited-then-deleted row is inherently indistinguishable from an unedited
tombstone without a schema-backed operation journal; this remains documented
rather than adding a hidden persistence mechanism.

### Final verification

Using the Android Studio JBR on the final uncommitted delta:

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain
.\gradlew.bat :app:kspDebugKotlin --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --rerun-tasks --console=plain
.\gradlew.bat test --rerun-tasks --console=plain
git diff --check
```

Results:

- Debug unit tests: success; 216 tests, no failures, errors, or skips.
- KSP/schema generation: success.
- Android instrumentation-test compilation: success.
- Aggregate JVM tests: success; 216 tests, no failures, errors, or skips.
- Diff check: exit 0; only LF-to-CRLF conversion warnings appeared.
- Database version remains 14. Entities, migrations, `MoneyDatabase.kt`, and
  schema JSON are unchanged.
- Android instrumentation tests were compiled, not executed on a device.
