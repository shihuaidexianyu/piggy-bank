# UI/UX Iteration Plan — v1

Status: Phase 1 (P0) implemented in 2.5.14 — kept as the design reference for Phase 2
Scope owner: Money (com.shihuaidexianyu.money)
Target release: next feature release after 2.5.13

This document is the executable design for the first UI/UX iteration of Money.
It covers three Phase 1 features in implementation detail (save-and-continue
recording, home hierarchy redesign, period-switcher touch target) plus a
prioritized Phase 2 backlog. All UI strings are Chinese (Simplified); code,
comments, and this documentation stay in English per `AGENTS.md`.

## 1. Goals

1. Make the highest-frequency path — recording a transaction — support
   consecutive entries without leaving the page.
2. Reduce home-screen information density so the first screen communicates one
   clear story: *current net assets*, then *period cash flow*.
3. Fix the undersized period switcher (30 dp) to meet Material touch-target
   guidance without losing the current segmented-control aesthetic.

Non-goals for Phase 1: no database migration, no new dependencies, no new
categories, no changes to `HomeProjector`/`HomeUiState`, no changes to backup
format or device-preference portability semantics.

## 2. Phase 1 — Detailed design

### 2.1 Feature A: Save-and-continue recording（连续记账）

#### 2.1.1 UX behavior

A new device-local preference `continueRecording` (default **off**):

- When **off** (default): current behavior is unchanged — save succeeds, the
  form emits the `SAVED` terminal, the screen calls `onSaved()`, and the user
  returns to the previous page.
- When **on**: after a successful save the record page stays open, amount and
  note are cleared, the selected account(s) and date/time are kept, and a
  snackbar shows `已保存，可继续记账`. The user can immediately enter the next
  amount.
- The toggle is rendered on both the cash-flow and transfer record pages, just
  below the save button, as a compact switch row:
  - Title: `保存后继续记账`
  - Supporting text: `保存后留在本页，继续记录下一笔`
- The toggle is **hidden (and the preference ignored)** when the page is opened
  in a stateful context where leaving the page matters:
  - reminder processing (`reminderId != null`), because a due reminder is
    consumed by the save;
  - the balance-update supplemental entry flow (`allowContinue = false` from
    `BalanceNavGraph`), because the balance-update screen must observe the
    `SupplementalEntrySavedTokenKey` when the record page is popped.
- The toggle is disabled while `isSaving`.
- The preference is persisted in DataStore (`DevicePreferences`), so it is
  device-local, never exported, and survives process death.

Reset semantics after a continue-save:

| Field | After continue-save |
|---|---|
| `amountText` | `""` |
| `note` | `""` |
| `selectedAccountId` / `fromAccountId` / `toAccountId` | kept |
| `occurredAtMillis` | kept (supports backfilling consecutive old entries) |
| `noteSuggestions` | refreshed |
| `isDirty` | `false` (back navigation must not show the dirty prompt) |
| errors | cleared |
| `isSaving` | `false` |
| `pendingTerminal` | stays `null` (the screen must not navigate) |
| `operationId` | **regenerated** (critical, see 2.1.5) |

#### 2.1.2 Data & persistence changes

1. `domain/model/Settings.kt` — add to `DevicePreferences`:
   ```kotlin
   val continueRecording: Boolean = false,
   ```
   `failClosedDevicePreferences()` stays unchanged (default `false` is already
   the safe value).

2. `domain/repository/DevicePreferencesRepository.kt` — add:
   ```kotlin
   suspend fun updateContinueRecording(enabled: Boolean)
   ```

3. `data/repository/DevicePreferencesRepositoryImpl.kt`:
   - Add `val ContinueRecording = booleanPreferencesKey("continue_recording")`
     to `Keys`.
   - Read/write it in `DevicePreferencesMapper.fromPreferences` / `write`.

4. `data/repository/InMemoryDevicePreferencesRepository.kt` — implement the new
   method with the same `update { copy(continueRecording = enabled) }` pattern.

No `DataGraph`/`UseCaseGraph` changes: both record ViewModels already receive
`devicePreferencesRepository`.

#### 2.1.3 ViewModel changes

`RecordCashFlowViewModel`:

- New constructor param `allowContinueRecording: Boolean = true` (wired from
  navigation, see 2.1.4).
- New state fields:
  ```kotlin
  val allowContinueRecording: Boolean,
  val continueRecording: Boolean = false,
  ```
- In `loadDependencies()`, load the preference alongside `recentAccountIds`:
  `devicePreferencesRepository?.query()?.continueRecording ?: false`.
- New method `updateContinueRecording(enabled: Boolean)` — updates state and
  persists via the repository (fail-silent like `rememberRecentAccounts`, i.e.
  wrap in `runCatching`).
- In `save()`, keep the existing validation and mutation logic. In the
  `onSuccess` block, branch:
  ```kotlin
  rememberRecentAccounts(accountId)
  if (allowContinueRecording && _uiState.value.continueRecording) {
      resetAfterSave(accountId = accountId)
      effects.emit(RecordCashFlowEffect.ShowMessage("已保存，可继续记账"))
  } else {
      setPendingTerminal(pendingFormTerminal(FormTerminalKind.SAVED))
  }
  ```
- New private `resetAfterSave(accountId: Long)`:
  - clear amount/note/errors;
  - keep `selectedAccountId`, `occurredAtMillis`, `noteSuggestions`;
  - `isDirty = false`, `isSaving = false`;
  - regenerate `operationId` (see 2.1.5);
  - write the updated draft to `savedStateHandle` via the existing
    `updateDraft` mechanism;
  - `saveInFlight = false`.
- On the failure path, behavior is unchanged (stay on page, show field errors
  or `ShowMessage`); `saveInFlight` must be reset as today.

`RecordTransferViewModel` — identical changes:

- `allowContinueRecording`/`continueRecording` state + constructor param;
- same preference load in `loadDependencies()`;
- `updateContinueRecording(enabled: Boolean)`;
- `resetAfterSave(fromAccountId, toAccountId)` keeps both accounts and
  `occurredAtMillis`, clears amount/note/errors, resets dirty/saving, and
  regenerates `operationId`;
- success branch emits `RecordTransferEffect.ShowMessage("已保存，可继续记账")`
  instead of the `SAVED` terminal when continue mode applies.

#### 2.1.4 Screen & navigation changes

1. New shared composable `ui/record/ContinueRecordingToggle.kt`:
   ```kotlin
   @Composable
   fun ContinueRecordingToggle(
       enabled: Boolean,          // preference state
       onCheckedChange: (Boolean) -> Unit,
       modifier: Modifier = Modifier,
       isEnabled: Boolean = true, // false while saving
   )
   ```
   Renders a `Row` with `Switch` + `Text` title + supporting text, using
   existing M3 components. Place it below `MoneySaveButton` inside the form
   `MoneyCard` on both record screens.

2. `RecordCashFlowScreen.kt` / `RecordTransferScreen.kt`:
   - Render the toggle only when `state.allowContinueRecording`.
   - No other screen logic changes: in continue mode the ViewModel never emits
     the `SAVED` terminal, so the existing `LaunchedEffect(terminal)` never
     fires and `onSaved()` is never called.

3. Navigation plumbing:
   - `MoneyDestination.recordCashFlowRoute(...)` gains an optional
     `allowContinue: Boolean = true` query parameter (encode/decode in the same
     style as `amount`/`purpose`; add `navArgument("allowContinue") { type =
     NavType.BoolType; defaultValue = true }` in `RecordNavGraph`).
   - `BalanceNavGraph.kt` passes `allowContinue = false` when navigating to the
     supplemental cash-flow entry.
   - `ReminderNavGraph.kt` already passes `reminderId > 0`, which forces
     `allowContinueRecording = false` in `RecordNavGraph` regardless of the
     argument:
     ```kotlin
     val allowContinueRecording = reminderId <= 0L && allowContinue
     ```
   - FAB (`MoneyNavGraph`), account-detail (`AccountsNavGraph`), and share
     paths keep the default `true`.

4. Strings (`app/src/main/res/values/strings.xml`):

   | Name | Chinese |
   |---|---|
   | `record_continue_recording` | 保存后继续记账 |
   | `record_continue_recording_hint` | 保存后留在本页，继续记录下一笔 |
   | `record_saved_continue_hint` | 已保存，可继续记账 |

#### 2.1.5 Critical edge case: operation ID reuse

`operationId` is currently generated once per ViewModel instance and persisted
in `SavedStateHandle`. Ledger tables have unique `operationId` constraints.
Without regeneration, the second save in continue mode would violate
uniqueness and fail.

Implementation: store the `operationIdFactory` on the ViewModel, and in
`resetAfterSave` do:
```kotlin
operationId = operationIdFactory.create()
savedStateHandle[OPERATION_ID_KEY] = operationId
```
The existing `savedOperationId(existing, factory)` helper only applies at
construction; regeneration after a save must go through the factory directly.

#### 2.1.6 Test plan (Feature A)

Unit (`app/src/test`):

- `RecordCashFlowViewModelTest`:
  - default: `continueRecording == false`, save emits `SAVED` terminal and
    `onSaved` behavior is unchanged;
  - continue mode: save keeps `pendingTerminal == null`, clears amount/note,
    keeps account/time, `isDirty == false`, emits `ShowMessage`;
  - second save after continue creates a second record with a **different**
    `operationId` (assert both records exist in the in-memory repository);
  - `allowContinueRecording == false` (reminder/supplemental) forces `SAVED`
    terminal even when the preference is `true`;
  - validation failure in continue mode does not clear the form.
- `RecordTransferViewModelTest`: same matrix for the transfer form.
- `DevicePreferencesMapper` round-trip test for `continue_recording` (or extend
  the existing mapper test class).

Instrumented / Compose (`app/src/androidTest`):

- Record page shows the toggle on a plain entry and saves without navigating
  when enabled (assert page title still displayed + snackbar text);
- record page hides the toggle when `allowContinueRecording == false`;
- toggle is disabled while saving (optional timing-based assertion).

### 2.2 Feature B: Home hierarchy redesign（首页信息层级）

#### 2.2.1 Current state

`PeriodOverviewBlock` (HomeScreen.kt) packs all of the following into one card:

1. `当前净资产` label + `PeriodSwitcher`;
2. animated net-assets amount;
3. net-worth delta vs period start;
4. funding/investment asset split (two `PeriodMetricCell`s, only with
   investment accounts);
5. `HorizontalDivider`;
6. `FlowSplitBar` (only when both inflow and outflow > 0);
7. income / expense / net-cash-flow `PeriodMetricCell` row;
8. investment P&L row (only with investment accounts).

#### 2.2.2 Target layout

Replace the single block with **two cards** inside the same `item` slot,
separated by the existing 14 dp `Arrangement.spacedBy` rhythm of the
`LazyColumn`. The public `HomeScreen` signature, `HomeUiState`, and
`HomeProjector` are **unchanged**; this is a pure presentation refactor.

**Card 1 — `NetWorthHeroCard`** (state at a glance):

- Row: `当前净资产` + period switcher (2.3);
- animated amount (keep `AnimatedContent`);
- `DeltaLabel` (环比, unchanged);
- subtle divider;
- `AssetSplitLine` (only with investment accounts): one subdued row
  `现金资产 ¥x · 投资资产 ¥y`, rendered as two compact columns
  (`labelSmall` label + `titleSmall` value, no deltas). This replaces the two
  large `PeriodMetricCell`s and reads as a secondary fact, not a headline.

**Card 2 — `PeriodFlowsCard`** (period behavior):

- `FlowSplitBar` (same visibility rule as today);
- income / expense cells: reuse `PeriodMetricCell` with deltas (same props as
  today), two columns with `weight(1f)` each;
- new full-width `NetCashFlowRow`: label (`本月净现金流` / `本周净现金流` /
  `今年净现金流`) on the left, colored signed amount on the right — avoids the
  three-column squeeze on narrow screens;
- investment P&L row (same visibility rule as today).

Visual language: both cards use `surfaceContainerLowest` (same as the current
`MoneyCard` group convention). No new colors; semantic colors stay reserved for
amounts and deltas.

#### 2.2.3 Component changes

- `ui/home/HomeScreen.kt`:
  - rename `PeriodOverviewBlock` → keep as a wrapper emitting
    `NetWorthHeroCard` + `PeriodFlowsCard` inside a `Column(spacedBy(14.dp))`;
  - extract `NetWorthHeroCard`, `PeriodFlowsCard`, `AssetSplitLine`,
    `NetCashFlowRow`;
  - keep `PeriodMetricCell`, `FlowSplitBar`, `DeltaLabel`, `PeriodSwitcher`
    signatures (extend `PeriodSwitcher` per 2.3);
  - long-amount handling: reuse the existing `recordStyle` length-based
    downshift for the hero amount; `NetCashFlowRow` uses `maxLines = 1` +
    `TextOverflow.Ellipsis` with `titleLarge`/`headlineSmall` downshift.
- `ui/common/MoneySkeleton.kt` (optional, low cost): change the single 120 dp
  hero block into two blocks (hero + flows) so the loading shape matches the
  new layout.

Strings: reuse existing keys (`home_current_net_assets`, `home_*_income`,
`home_*_expense`, `home_*_net_cash_flow`, `home_funding_assets`,
`home_investment_assets`, `home_*_investment_pnl`, `home_period_*`). No new
strings required.

#### 2.2.4 Test plan (Feature B)

- Unit: no changes expected — `HomeProjector` and `HomeViewModel` are
  untouched. Run the full suite to confirm.
- Instrumented:
  - home renders two overview cards when investment accounts exist (hero text
    + flow labels discoverable);
  - `现金资产`/`投资资产` appear once in a compact row;
  - income/expense/net-cash-flow labels remain displayed and clickable/readable
    at 1.0x and 1.3x font scale;
  - update any text/order assertions that assumed the old single-card layout.

### 2.3 Feature C: Period switcher touch target（周期切换器触控尺寸）

#### 2.3.1 Problem

`PeriodSwitcher` (HomeScreen.kt) is a hand-rolled `Surface` pill row with a
fixed width of 146 dp and **30 dp** item height, below the 48 dp (or M3 minimum
40 dp) touch guidance. It is also a high-frequency control (home hero).

#### 2.3.2 Solution

Replace the internals with official Material 3 segmented buttons:

```kotlin
SingleChoiceSegmentedButtonRow(
    modifier = Modifier
        .width(160.dp)
        .semantics { contentDescription = selectorDescription },
) {
    DashboardPeriod.entries.forEachIndexed { index, period ->
        val isSelected = period == selected
        SegmentedButton(
            selected = isSelected,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(period)
            },
            shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = DashboardPeriod.entries.size,
            ),
            label = { Text(stringResource(period.shortLabelRes())) },
        )
    }
}
```

- M3 `SegmentedButton`/`SingleChoiceSegmentedButtonRow` ship a 40 dp minimum
  height and built-in `selected` semantics; remove the hand-rolled
  `Surface(Modifier.height(30.dp))` + `shadowElevation` code.
- Keep the `SegmentTick` haptics, the `home_period_selector` content
  description, and the `周/月/年` labels.
- Keep the outer width explicit (≈160 dp) so the hero row layout does not
  reflow.
- If the exact M3 API is unavailable in the pinned Compose BOM (verify first),
  fallback is the same custom component with `Modifier.heightIn(min = 40.dp)`
  and `minimumInteractiveComponentSize()`; the official API is preferred.

#### 2.3.3 Test plan (Feature C)

- Instrumented:
  - the three period buttons exist, are clickable, and toggle `selected`
    semantics;
  - assert the switcher container height ≥ 40 dp via
    `getUnclippedBoundsInRoot()` (or a touch-target semantics check);
  - large-text (1.3x) reachability test still passes with the new component.

## 3. Phase 2 backlog（后续迭代，不阻塞 Phase 1）

Recorded here so the design has a single source of truth:

1. **Keypad ergonomics** (`MoneyAmountKeypadSheet`): long-press backspace
   clears the expression; swipe-down dismisses the sheet.
2. **Accessibility contrast pass**: audit `MoneyStatusPill` / `MoneyMetricTile`
   (`accent.copy(alpha = 0.08f)` backgrounds) against WCAG AA 4.5:1 in light
   and dark themes; raise chroma or switch to tonal containers.
3. **Font-scale resilience**: hero/amount styles must not truncate at
   `fontScale > 1.5`; allow wrapping or scrolling.
4. **Adaptive two-pane**: history list + detail and accounts list + detail on
   wide screens (master-detail).
5. **Settings grouping**: group by 外观 / 隐私与安全 / 数据备份 / 提醒通知 with
   icons.
6. **Data visualization**: 12-month net-worth trend on home or savings-goal
   screen; monthly spend composition derived from note keywords (opt-in, no
   schema change); budget pace reference line using `BudgetPacePolicy`.
7. **Copy review**: evaluate `当前净资产` vs `总资产` wording; keep
   `home_funding_assets` consistent with account-kind terminology.
8. **History quick filters**: show active filters as removable chips above the
   list; optional saved filter presets.

## 4. Acceptance criteria

Feature A:

- [ ] Continue mode can be toggled on cash-flow and transfer pages and survives
      process death (DataStore).
- [ ] Continue-save clears amount/note, keeps account/time, shows the snackbar,
      and never triggers the dirty-back prompt.
- [ ] Consecutive saves produce distinct `operationId`s and distinct records.
- [ ] Reminder and balance-supplemental entries never enter continue mode.
- [ ] All existing tests pass; new unit + compose tests cover the matrix.

Feature B:

- [ ] Home renders exactly two overview cards; hero shows only assets + delta +
      (optional) asset split; flows card holds income/expense/net-cash-flow.
- [ ] No behavior change: same data, same period semantics, same
      `HomeProjector`; full unit suite green.
- [ ] UI tests updated and green; no text regressions.

Feature C:

- [ ] Period switcher uses official M3 segmented buttons (or 40 dp fallback)
      with ≥40 dp touch height and preserved semantics/haptics.

## 5. Implementation order & task list

Suggested order (each step leaves the tree buildable):

1. Feature C (smallest, self-contained): `HomeScreen.kt` `PeriodSwitcher` swap
   + tests.
2. Feature A data layer: `DevicePreferences` field, repository interface +
   impl + in-memory, mapper, strings.
3. Feature A ViewModels: cash-flow, then transfer (mirror), with
   `resetAfterSave` + operation-ID regeneration.
4. Feature A screens + navigation: toggle composable, `allowContinue` arg,
   `BalanceNavGraph`/`RecordNavGraph` wiring.
5. Feature A tests (unit + instrumented).
6. Feature B: split `PeriodOverviewBlock`, extract new components, optional
   skeleton tweak.
7. Feature B tests + full `./gradlew test` and targeted androidTest.
8. Manual QA pass (light/dark, 1.0x/1.3x font scale, narrow 360 dp screen,
   landscape/rail).

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Duplicate `operationId` on second continue-save | Regenerate in `resetAfterSave`; unit test asserts distinct IDs. |
| Continue mode breaks balance-update supplemental refresh | `allowContinue = false` from `BalanceNavGraph`; toggle hidden; test coverage. |
| Continue mode consumes reminder without returning | `reminderId > 0` forces `allowContinueRecording = false`. |
| Dirty-back prompt after continue-save | Reset `isDirty = false` in `resetAfterSave`; assert in tests. |
| M3 segmented-button API missing in pinned BOM | Verify at task start; documented 40 dp fallback. |
| Text-based UI tests brittle after home reflow | Update assertions in the same change; prefer semantics/test tags over raw text where feasible. |
| DataStore preference drift (in-memory vs real impl) | Both implementations updated in the same commit; mapper round-trip test. |

## 7. Open questions (for review)

1. Should `continueRecording` also be exposed in Settings (设备偏好 group), or
   keep it only on the record pages for discoverability-in-context?
2. For the hero card, keep the asset split as a subdued single line, or drop it
   from the hero entirely and let the accounts tab own asset composition?
3. Should `occurredAtMillis` reset to "now" on continue-save instead of being
   kept? (Design assumes keeping it supports backfilling; changing it is a
   one-line tweak.)
