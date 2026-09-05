# Money — Product and interaction redesign

Status: UI implementation complete; verification results and implementation scope recorded in section 8.
Date: 2026-09-05
Scope: the complete Android application, including secondary flows and exceptional states.

## 1. Product direction

Money helps its owner understand their assets, keep account balances accurate, and
record changes with little effort. Account and asset management is the primary
direction; recording income, expenses, and transfers remains immediately accessible.

Three questions organize the experience:

1. What do I have now?
2. What changed during this period?
3. What needs recording or checking?

Current balances and period flows are different concepts. Their controls and
visual groups must communicate that difference. Investment valuation changes
remain distinct from cash income and expense. Transfers remain asset movement.

## 2. Information architecture

```text
Overview (总览)
  Current net assets
  Funding / investment breakdown
  Period activity [week / month / year]
  Due reminders and accounts requiring a check
Accounts (账户)
  Funding / investment account lists
  Hidden accounts; closed accounts
  Account detail -> Check balance / Account activity / Manage account
  Add account; reorder accounts; batch checking
Activity (明细)
  Search; account / date / type filters
  Active filters and matching totals
  Record detail -> Edit / Delete
Record action (记一笔)
  Expense / Income / Transfer / Check balance
Settings (设置, toolbar entry)
  Appearance
  Privacy and lock
  Reminders and notifications
  Data and computer connection
  About
```

Existing deep links, shortcuts, notification routing, and saved drafts must
continue reaching their intended tasks. A pushed page always offers visible back
navigation. Successful actions return to the originating context.

## 3. Visual system

Direction: a quiet, readable personal ledger. Neutral surfaces, dark text,
carefully aligned amounts, and monochrome action colors. Chinese
system typography stays familiar. No decorative gradients, dashboard ornaments,
or number animation that fragments accessibility announcements.

| Element | Specification |
| --- | --- |
| Page | Neutral white / gray in light mode; charcoal in dark mode |
| Content | Continuous reading surface; spacing and dividers establish groups |
| Containers | Used for interactive groups and consequential summaries |
| Primary action | Near-black in light mode, light gray in dark mode; at least 48 dp interactive target |
| Secondary action | Text or tonal control; no resting shadow |
| Main amount | 40 sp baseline, tabular figures, complete accessible value |
| Ordinary labels | 14–16 sp; no artificial letter spacing for Chinese |
| Section titles | Clear medium-weight text; optional contextual action |
| Shapes | 4 / 8 / 12 / 16 / 24 dp theme scale |
| Spacing | 4 / 8 / 12 / 16 / 24 / 32 dp |
| Semantic color | Income / expense preference preserved; text and signs also identify meaning |
| Motion | Short navigation and state feedback; respect system animation settings |

Support large text without clipping essential amounts or actions. Forms remain
scrollable when the keyboard is visible. Compact windows use a navigation bar;
wide windows use a rail and readable content width. Privacy masking covers amounts
and derived proportions together.

## 4. Page and workflow specification

### 4.1 Overview

Top bar: 总览, reminders, settings. The main amount is presented directly on the
page, with a small 当前净资产 label. Investment and funding amounts are a secondary
breakdown. This area only displays asset information. Accounts are reached through
the top-level navigation, and the period switch belongs to the next section.

The next section is 期间收支, with its own week/month/year control. Show income,
expense, net cash flow, and investment P&L where applicable. Labels always name
the selected period. A change in this control does not imply that current assets
have changed.

Due items appear only when they exist and lead directly to the relevant task.
Record browsing belongs in Activity; the overview ends after its summaries and
due items. With no accounts, the page explains the first useful action and
provides 创建账户. With only closed accounts, the overview retains its summaries
and provides an account management action; history remains available in Activity.

### 4.2 Accounts

The account list prioritizes name and current balance. Funding and investment
groups use the same row structure. Account state is secondary, with a clear cue
when a balance check is due. Avoid presenting decorative proportions as if they
were actions.

Add and reorder controls stay in the toolbar. Hidden and closed accounts remain
discoverable and do not change ledger calculations. Closed-account rows open a
read-only detail page. Reopening and settling legacy nonzero balances retain
their lifecycle safeguards.

Account order uses the same funding / investment groups as the account list, with
hidden accounts in a collapsed section and closed accounts excluded. Rows show
only the name, a subdued balance, and a 48 dp drag handle when the group has more
than one account. Dragging stays within a group, lifts the held row slightly,
animates its neighbors, and scrolls at the viewport edges. Accessibility actions
provide group-aware moves up and down. Save stays in the top bar and is enabled
only for a changed draft. More actions offers one-time sorting within each group
and 撤销本次调整, which restores the order from page entry. Back confirms discarding
changes; saving freezes edits, and a failed save retains the draft for retry.

Account detail shows balance first, last check second, and a prominent 核对余额
action (investment wording: 更新市值). Period activity and recent records follow.
Management opens account settings; it is visually secondary to working with the
account. Account-scoped record entry preserves that account.

### 4.3 Recording

The record launcher provides four plainly named actions. Expense appears before
income. Transfer and balance checking remain available without being described
as income or expense. No explanatory subtitle is needed for this simple choice.

Cash recording order: amount, account, optional note, date/time, save. Amount input
opens immediately. Account and date defaults stay visible and editable. Empty
optional notes do not block save. Display validation beside the responsible field.

Transfer order: amount, source account, destination account, optional note,
date/time, save. Swap and transfer-all controls stay attached to the account
selection. The direction remains readable before saving.

Saving displays progress, prevents duplicate submission, then returns to the
origin. Dirty drafts retain the existing discard protection. Editing uses the
same field language and keeps delete separate from save.

### 4.4 Checking balances and investment value

Select account -> enter actual balance / market value -> inspect difference ->
save -> return to the originating page with a short confirmation.

The difference preview communicates consequences before saving. For a funding
account with a difference, offer supplemental income/expense recording in context.
For investments, explain that the difference becomes investment P&L, and transfers
are recorded separately. A zero difference can be confirmed directly.

Remove the obligatory post-save result stop. Detailed evidence remains available
in the saved balance record. Batch checking keeps progress, individual errors,
and a final count without obscuring which accounts succeeded.

### 4.5 Activity and record details

The activity page is a chronological ledger. Search is easy to find; optional
filters are disclosed when needed. Active filters stay visible and removable.
Account-scoped activity names the account and keeps its scope fixed.

Dates organize rows. Each row answers what happened, where, and for how much.
Totals describe the current filter scope. Loading another page keeps the current
rows usable; failure provides retry in place. Empty search results offer a clear
filter reset. A fresh ledger offers recording.

Record detail retains before/after balance evidence where meaningful, edit and
delete capabilities, and closed-account restrictions. Technical record categories
are translated into human-readable financial meaning.

### 4.6 Reminders

Separate due tasks, upcoming reminders, and paused reminders visually. A due
reminder exposes its useful action (record or check); edit and delete remain
secondary. Skipping retains undo. Notification permission guidance appears when
it affects delivery. Creating and editing reminders makes the first due date,
recurrence, account, and amount understandable before save.

### 4.7 Settings, backup, and privacy

Appearance: theme, amount colors, currency symbol.
Privacy: app lock, relock delay, recent-task hiding, notification amount hiding.
Reminders: notification status, recurring reminders, account check schedules.
Data: computer connection, export, import, eligible import rollback.
About: version and a concise explanation of local data storage.

Use result-oriented copy: relock delay describes when the app locks; no monotonic
clock terminology. A currency-symbol setting must not imply currency conversion.
Export explains plaintext handling at the relevant step. Import shows replacement
scope and its safety copy before confirmation. Successful import acknowledges the
result, and eligible rollback remains findable in data settings.

### 4.8 Computer / AI connection

Use 连接电脑 for the user-facing entry and AI 修改记录 for the mutation journal.
The page answers: is it running, may the computer modify records, which devices
are connected, and how can changes be undone?

Show connection details only while running. Keep manual pairing as a fallback.
Service duration, trusted-LAN requirement, write capability, persistent device
pairing, revocation, and conflict-aware last-in-first-out undo remain explicit
when relevant. No technical protocol identifiers in ordinary labels.

### 4.9 Startup, lock, and recovery

Startup loading preserves a stable page shell. Biometric lock has one clear unlock
action and readable errors. Recovery names the data problem and explains each
available action without exposing route IDs or internal state-machine terms.
Shortcuts and notification tasks wait for unlock and migration readiness.

## 5. Complete screen coverage

| Area | Screens / surfaces | Required treatment |
| --- | --- | --- |
| Shell | Navigation, top bars, snackbar, launch gates | Consistent back behavior, primary entry, insets |
| Overview | Home, empty/loading/error | Asset-first hierarchy, correctly scoped period control |
| Accounts | List, detail, create, edit, reorder | Ledger rows, lifecycle clarity, task-first detail |
| Records | Create/edit cash flow, create/edit transfer | Amount-first forms, defaults, draft and save feedback |
| Balance | Check, batch, result, detail/edit update, adjustment detail | Preview consequence, direct completion, retained evidence |
| Activity | Search, filters, record list, account drill-down | Clear scope, chronological rows, contextual totals |
| Reminders | List, create, edit, permission gateway | Due-task priority, recurrence clarity, skip undo |
| Settings | Appearance, privacy, notifications, data, about | Correct grouping, short copy, consequential confirmations |
| Connection | Service, pairing, devices, AI changes, undo | State-led disclosure, understandable controls |
| Shared | Account/amount/date/time pickers, dialogs, empty/error states | Consistent language, accessible targets, large text |
| Access | Lock, startup migration and recovery | Clear state and recovery, protected ledger access |

## 6. Implementation invariants

This redesign preserves all money-as-Long arithmetic, fixed reconciliation deltas,
soft deletion, lifecycle guards, journaled AI mutations, transactional imports,
startup gating, privacy preferences, and existing protocol and backup formats.
It does not invent categories, budgets, cloud accounts, exchange rates, or returns.
Existing shared components are evolved rather than introducing a second UI stack.

## 7. Acceptance and verification

- Record a 38-yuan expense; edit its account; return to the originating page.
- Transfer between two accounts and verify visible direction before save.
- Check a funding balance with and without a difference; supplement a missing
  entry; confirm that the checking form refreshes after returning.
- Update an investment value and see the correct P&L interpretation.
- Find a past transfer with account and date filters; clear filters.
- Create, hide, reorder, close, and reopen eligible accounts.
- Process or skip a reminder; undo a skip.
- Export, preview import, understand replacement, find eligible rollback.
- Start/stop computer connection, approve/revoke a device, inspect and undo an AI change.
- Check light/dark, compact/wide, large text, masked amounts, long names/amounts,
  empty states, failures, keyboard, back navigation, lock, and startup recovery.
- Run the full unit test suite, debug build and lint; perform device UI verification
  where a device or emulator is available. Record actual results separately from goals.

Implementation references: Android's [Scaffold guidance](https://developer.android.com/develop/ui/compose/components/scaffold)
for layout/insets and [accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
for semantic controls and touch targets. These support implementation details;
the product direction above is a project-specific design decision.

## 8. Implemented scope and actual verification

### Implementation

- Applied the monochrome light/dark palette, high-contrast actions, spacing, shapes,
  quieter static containers, wrapping list labels, and revised section headings
  through the shared UI components.
- Changed the top-level order to Overview / Accounts / Activity. Rebuilt overview
  hierarchy around current assets and separately controlled period activity.
- Simplified the record launcher to four full-width actions. Account detail now
  offers preselected expense, income, and transfer entry, with availability guards.
- Removed account proportion rings, added a batch-check entry and explicit period
  labels, and made each active history filter directly removable.
- Added fixed save footers, keyboard avoidance, and a 640 dp maximum content width
  to shared forms. Updated create/edit record, transfer, account, reminder, and
  reconciliation forms. Added optional disclosures to account creation.
- Reconciliation completion returns to its originating screen with a short
  confirmation. Investment entry and editing use market-value terminology. The
  legacy result route remains for compatibility but is no longer a required stop.
- Reorganized settings and computer connection, shortened redundant copy, and
  explained consequential controls in user language.

Secondary screens share the revised theme and components; their existing lifecycle,
lock, backup, reminder, and recovery behavior remains in place. The screen matrix
describes the full product design, not a claim that every secondary ViewModel was
rewritten. Existing amount/date/time picker mechanics were retained. No database,
release-version, protocol, or ledger arithmetic change was required.

Two repository-contract discrepancies surfaced during validation: the in-memory
history search now includes the historical balance-adjustment alias already used
by Room, and the instrumented comparison fixture now supplies the same opening
balance to both implementations.

### Automated verification

On 2026-09-05:

| Check | Result |
| --- | --- |
| `gradlew.bat test` | 664 tests in 131 classes; zero failures/errors/skips |
| `:app:assembleDebug` | Successful; `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:lintDebug` | Zero errors; 25 warnings remain |
| `:app:connectedDebugAndroidTest` | 80 tests; zero failures/skips; Android 17 Pixel 9a emulator |
| `git diff --check` | Passed |

The device suite includes navigation, page states, accessible semantics, large-text
reachability, Room/repository contracts, migration, and launch behavior. A new
360-by-480 dp, 2x-font form test verifies that the fixed save action stays visible
before and after scrolling to the last field. After the final investment wording
correction, unit tests, debug build, and lint were repeated successfully; that
workflow was also checked manually.

### Manual review and artifacts

Inspected overview, account list/detail, account-prefilled expense entry, calculator,
dirty-draft dismissal, activity, settings, computer-connection stopped state, and
investment checking in a debug-seeded emulator. Confirming an unchanged investment
value returned directly to its account detail. Light and dark overview/account
screens were captured and inspected.

The local Chinese review artifact is `_preview/ui-redesign/review.html`, with
clickable screenshots, navigation, workflow explanations, and implementation scope.
Screenshots use generated demonstration ledger data, not a user's financial data.
The APK is a debug build with demonstration seeding, not a signed release.

Manual review did not exercise an external computer pairing session or real-device
biometrics. The complete acceptance list in section 7 is the product checklist;
the manual observations above delimit the scenarios actually exercised by hand.

### Palette review

After reviewing the running build, the owner chose black, white, and gray. Both
themes now use strictly neutral surface and action roles: light background
`#FAFAFA`, primary `#202020`, text `#1A1A1A`; dark background `#141414`, primary
`#E8E8E8`, text `#EDEDED`. Borders, navigation selection, sheets, inputs, and
secondary actions use matching neutral-gray roles. Transfers, balance corrections,
and reminders also use neutral accents. Income/expense retain the configured
red/green meaning, and user-selected account icon colors remain identifiable.

The palette update passed all 664 unit tests, debug assembly, and lint (zero
errors, 25 warnings). Twelve text/background role pairs per theme passed the
4.5:1 contrast check, with minimum ratios of 4.71:1 in light and 5.63:1 in dark.
The review screenshots were refreshed from the installed monochrome debug build.

### Activity refinement

The activity page now starts with a compact title bar containing search and filter
controls. Search expands on demand, existing queries stay visible, and closing it
clears only the keyword. Loaded-page counts no longer compete with the records.

Each date remains a sticky group header with labeled cash-in/cash-out subtotals;
a partially loaded date remains explicitly identified. Record rows have no type
icons. They align the title and amount above the account and time. Rows show only
`HH:mm`, because the group header supplies the date. Non-cash events use a short
text label where their meaning needs clarification. Long amounts and large text
stack vertically rather than truncating monetary values.

Tapping a row opens a detail sheet containing the complete timestamp, account(s),
and before/after balance evidence. Editing remains an explicit action; closed
accounts expose readable details without an edit action. Account-scoped activity
also shows the scoped account's post-record balance, including the receiving side
of a transfer. Source account identity is carried explicitly in the UI model.

Records are individually keyed lazy items. Refresh-anchor indices account for
both date headers and records. The record action collapses while browsing and
expands again at the top. Detail sheets finish hiding before editor navigation.
The NavHost viewport stays independent of the bottom bar/rail: top-level pages
reserve their own chrome padding. Returning to activity keeps the list stationary
instead of scaling and translating it behind the exiting editor.

Verification for the activity refinement: 666 unit tests passed; all 12 targeted
device tests passed across HistoryPresentationTest, HistoryScreenLockedModeTest,
AsyncPageShellTest, and AdaptiveTopLevelNavigationTest. Debug build succeeded;
lint reported zero errors and 29 warnings (including unused legacy resources).
Manual emulator checks confirmed that list row positions match before entering
an editor and after returning. Return animation frames were inspected with the
emulator temporarily slowed, and its original animation setting was restored.

### Account order refinement

The order editor now mirrors account groups, uses monochrome name/balance rows
with drag handles, and collapses hidden accounts. Quick sorting and undo live in
the overflow menu; save remains in the top bar. Moves and quick sorts preserve
the storage slots of other groups. A released drag waits for pending measurement
before committing its final position, including a quick move to the last row.

Verification: 671 unit tests and all five ReorderAccountsPresentationTest device
tests passed. Coverage includes group boundaries, hidden accounts, first-to-last
dragging, edge auto-scroll, accessible moves, discard confirmation, large text,
save locking, and failed-save retry. Debug build and lint passed (zero errors,
29 warnings). Manual forward/reverse dragging, saving, and reopening matched the
account list; the original sample order was restored. Light/dark captures are in
`_preview/ui-redesign/account-order-light.png` and `account-order-dark.png`.

### Launcher icon refinement

The icon follows Cue's matte charcoal (`#18181B`), single coral (`#E5484D`) symbol,
and rounded geometry. A coin with a rotated, rounded square aperture replaces the
piggy-bank illustration; letter-based marks were rejected. Light mode uses an
off-white (`#FAFAFA`) background; night resources provide the charcoal background.
Both retain the coral symbol. Android's themed icon uses the matching white alpha
mask with a transparent aperture. Foreground and monochrome layers
are 108 dp vectors, with the 48 dp coin centered inside the adaptive icon safe
area. Android supplies the launcher mask. The README selects a matching light/dark
SVG with a rounded-square treatment, and the existing splash resource consumes the updated
adaptive icon.

Reference geometry: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Icon validation: the foreground and monochrome paths and transparent apertures
match; 671 unit tests, debug build, and lint pass. Both light and dark launcher
resources were verified on the Pixel emulator. Its launcher caches the rendered
icon across a night-mode switch; reinstalling the same APK in the target mode
refreshes that cache and confirms the matching resource. Immediate launcher-icon
switching is therefore not guaranteed. The system mode was restored to auto.
The two designs are shown in `_preview/icon-redesign/day-night.png`.
