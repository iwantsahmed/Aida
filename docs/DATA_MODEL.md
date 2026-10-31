# Data model

What is actually on disk, and why it is shaped this way.

**Authority.** `MiraDatabase` is at **version 6**. Room's own export,
`app/schemas/com.rayluxeo.aida.data.db.MiraDatabase/6.json`, is the ground truth for tables,
columns, types, indices and foreign keys. This document describes that file; a change that alters
the schema reconciles this document in the same commit. 50 tables, 7 FTS4 indexes, 31 DAOs.

The versions, and what each one was for:

| Version | Brought |
|---|---|
| 1 | Tasks, reminders, the vault, a flat list of conversation turns. |
| 2 | The full record set — clients, projects, invoices, quotes, money, notes, memory, files, automations, meetings — and the transcript reshaped into conversations and their messages. |
| 3 | The task system: description, all-day due dates, estimates, a client link, recurrence, series, archiving, and tags and attachments of their own. |
| 4 | Reminder kinds, reminder recurrence and snoozes, four more links; `reminder_places` and `alarms`. |
| 5 | `saved_places` — the directory that lets Abu name a place once and refer to it by name afterwards. |
| 6 | Notes become the thing the brief describes: a task link, an archive that is not the bin, tags shared with tasks (`note_tags`), and pages of handwriting (`ink_pages`). |
| 9 | Salah — `prayer_settings` and the `prayer_tracking_entry` prayer log. |
| 10 | `automation_record_fires` — the dedup ledger behind record-driven automation triggers. |

Entities live in `data/db/entity/` and are separate types from the domain models in
`domain/model/`. The domain never sees a Room type and Room never sees a domain type; the
mappers that bridge them sit in `data/repository/` (`WorkMappers`, `BusinessMappers`,
`MoneyDocumentMappers`, `MoneyFlowMappers`, `KnowledgeMappers`, `AssistantMappers`,
`CalendarMappers`, `PlatformMappers`, `Mappers`), grouped the same way the tables are. The
repository interfaces the data layer satisfies are in `domain/repository/`; each one returns
`AppResult<T>` except its flows, with a storage fault mapping to `MiraErrorCode.STORAGE`, a
missing row to `NOT_FOUND`, and a failed vault decryption to `AUTHENTICATION_FAILED`. Room throws
and the domain expects values, so every one-shot call funnels through `storageCall`; an
observation flow has no failure channel in the contracts, so a read error degrades to a fallback
rather than cancelling the collector and taking a screen down with it.

**Entity rules, forced by upstream bugs (AGENTS.md §7):** entities are plain data classes — no
supertypes, no interfaces, no overridden properties, because KSP 2.3.10+ silently drops
`@ColumnInfo(name = ...)` on an override (google/ksp#3047). Never `@Parcelize` on an entity
(google/ksp#3053).

---

## 1. Conventions

Stated once. The table listings in §3 do not repeat them.

| Convention | Rule |
|---|---|
| Primary key | `Long`, `@PrimaryKey(autoGenerate = true)`, always named `id`. `0L` means "not yet persisted". |
| Instants | `INTEGER`, epoch **milliseconds**, column named `...EpochMillis`. |
| Whole dates | `INTEGER`, epoch **days**, column named `...EpochDay`. |
| Enums | `TEXT`, the constant's `name`. |
| Booleans | `INTEGER`, 0/1. |
| Soft delete | `deletedAtEpochMillis`; null means live. |
| Byte arrays | `BLOB`. |

- **Two tables do not take an autoincrement key.** `user_profile` has a fixed `id = 1`
  (`SINGLETON_ID`) because exactly one row ever exists — Mira is a single-person app with no
  accounts, and a table with a fixed key is more honest than a users table that will never hold a
  second name. `app_settings` is keyed by its `key` string.
- **Instants are stored as raw millis rather than through a converter** because the due and
  completion columns are indexed and compared against day windows, and an index over a converted
  column would be unusable to SQLite.
- **Dates with no time of day are epoch days** so "due on the 3rd" cannot drift across a timezone
  change, and so comparing an expense against a budget period is one integer range check rather
  than date-string parsing.
- **Enums are stored by NAME, never by ordinal**, through `MiraConverters`. Reordering a constant
  can therefore never silently relabel Abu's existing rows. An unrecognised name **decays to that
  enum's own default** rather than throwing, because one bad row must not take a whole list down;
  the defaults are named in the KDoc of each enum in `domain/model/RecordState.kt`
  (`InvoiceStatus.DRAFT`, `PaymentMethod.OTHER`, `TimerState.FINISHED` — "an unknown timer must
  not look live" — `AutomationOutcome.FAILED`, `MemorySource.INFERRED`, and so on).
  **Renaming an enum constant is a schema migration**, not a refactor. Add new constants at the
  end; never reorder or rename without a migration that rewrites the stored values.
- **Soft delete** is a `deletedAtEpochMillis` timestamp on the sixteen tables that carry one:
  `tasks`, `clients`, `projects`, `invoices`, `quotes`, `expenses`, `income_entries`, `budgets`,
  `subscriptions`, `notes`, `memory`, `voice_notes`, `files`, `conversations`, `automations`,
  `meetings`. Every one of them is indexed on it, because every read filters on it. Deleting is a
  soft delete so undo restores the same row with its id and its links intact; the purge that
  empties the bin is a separate, deliberate call. Append-only tables (`activity_log`,
  `calculation_records`, `automation_runs`) deliberately have no soft delete — the point of a log
  is that it is not edited.
- **`uuid` appears on exactly five tables: `clients`, `projects`, `invoices`, `quotes`, `files`**
  — the records that leave the phone. The `id` is what the database joins on, compact and fast;
  the `uuid` is what is printed on an exported invoice, written into a backup and matched again on
  restore. An autoincrement key is only unique within one copy of this database, so anything that
  may be read outside it needs the stable identifier too. Every `uuid` column is uniquely indexed.
- **No column in the schema carries a SQL default.** Every value is written explicitly.
- **Foreign keys are only enforced when SQLite is told to**, per connection.
  `MiraDatabaseCallback` executes `PRAGMA foreign_keys = ON` on open. Room does not do this inside
  a migration — the pragma is a no-op within a transaction — which is what makes the `RESTRICT`
  and `SET NULL` rules real rather than decorative.
- Room names an index `index_<table>_<col>[_<col>…]`. §3 gives the column tuples only.

---

## 2. Money

**Nothing monetary is ever a `Float` or a `Double`, anywhere, at any point.** There is no
floating-point column in the schema.

On disk an amount is a whole number of a currency's **minor units** — paisa for BDT, cents for
USD — held in a `Long`, beside the `currencyCode` of the record it belongs to. Why minor units
rather than a decimal string:

- **Exact.** No rounding decision is taken on the way to or from disk.
- **SQL can work with it.** `SUM`, `ORDER BY` and range comparison all do the right thing on an
  integer. A decimal string sorts lexicographically and cannot be summed.

`domain/model/MoneyAmount.kt` is the only crossing point.

| Quantity | Representation | Example |
|---|---|---|
| Amount | minor units in a `Long`, column suffix `Minor` | `1250` = 12.50 BDT |
| Quantity | thousandths in a `Long`, column suffix `Milli`, `QUANTITY_SCALE = 3` | `2500` = 2.5 hours |
| Rate (tax, discount) | basis points in an `Int`, column suffix `BasisPoints`, `PERCENT_SCALE = 2` | `1500` = 15.00% |

The conversion is total and deterministic in both directions: a `Money` at its currency's own
scale maps to exactly one `Long`, and that `Long` maps back to exactly the same `Money`.
**`MoneyAmount` refuses an amount finer than its currency can express rather than rounding it** —
`setScale(digits, RoundingMode.UNNECESSARY)` throws, and `longValueExact()` / `intValueExact()`
throw on overflow. Abu's invoice totals are not a place to lose a paisa silently.

In memory, `Money` is `BigDecimal` + `java.util.Currency`, default **BDT**, scale ceiling
`MAX_SCALE = 6` enforced in `init`. `plus`/`minus` refuse to combine different currencies.
`Currency.defaultFractionDigits` is coerced to at least 0, because it is `-1` for the
pseudo-currencies the JVM also accepts (XXX, XAU, XDR) and passing `-1` to `setScale` rounds to
the nearest **ten** units rather than meaning "no decimals".

**Currency lives on the owning record**, not on the amount: `projects`, `invoices`, `quotes`,
`payments`, `expenses`, `income_entries`, `budgets` and `subscriptions` each carry
`currencyCode`; `clients` carries `defaultCurrencyCode` for `defaultRateMinor`. Invoice and quote
**lines have no currency column** — they are read in their parent's currency, which is what makes
a line incapable of disagreeing with its own invoice.

**Totals are stored, not derived.** `subtotalMinor`, `discountMinor`, `taxMinor`, `totalMinor`
and each `lineTotalMinor` are written down so a sent invoice still reads exactly as it was sent,
even if a line is later corrected or a tax rate changes.

---

## 3. The tables

Grouped as `MiraDatabase` groups them. Column lists omit `id`. Indices are given as column
tuples; `U` marks a unique index.

Nine tables have no `updatedAtEpochMillis` because nothing about them is edited after the fact:
`reminders`, `activity_log`, `invoice_items`, `quote_items`, `calculation_records`,
`note_attachments`, `scan_records`, `conversation_messages`, `automation_runs`. Five have no
`createdAtEpochMillis`: `app_settings`, `activity_log` (it has `occurredAtEpochMillis`),
`invoice_items`, `quote_items`, `automation_runs` (it has `startedAtEpochMillis`).

### 3.1 Work

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `tasks` | What Abu has to do. | `title`, `description?`, `notes?`, `dueAtEpochMillis?`, `dueAllDay`, `estimatedMinutes?`, `priority`, `isDone`, `completedAtEpochMillis?`, `origin`, `projectId?`, `clientId?`, `recurrenceRule?`, `seriesUuid?`, `archivedAtEpochMillis?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(dueAtEpochMillis)`, `(isDone)`, `(projectId)`, `(clientId)`, `(deletedAtEpochMillis)`, `(archivedAtEpochMillis)`, `(seriesUuid)` | `projectId` → `projects.id` **SET NULL**; `clientId` → `clients.id` **SET NULL** |
| `tags` | A label Abu reuses across tasks. | `name`, `createdAtEpochMillis` | `(name)` unique | none |
| `task_tags` | Which tags a task carries. | `taskId`, `tagId` | `(tagId)` | `taskId` → `tasks.id` **CASCADE**; `tagId` → `tags.id` **CASCADE** |
| `task_attachments` | A file hanging off a task. | `taskId`, `fileId`, `createdAtEpochMillis` | `(taskId, fileId)` unique, `(fileId)` | `taskId` → `tasks.id` **CASCADE**; `fileId` → `files.id` **CASCADE** |
| `subtasks` | A checklist line inside a task. | `taskId`, `title`, `isDone`, `completedAtEpochMillis?`, `position`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(taskId)` | `taskId` → `tasks.id` **CASCADE** |
| `reminders` | Abu's intent: be told about this, then. | `title`, `triggerAtEpochMillis`, `state`, `kind`, `recurrenceRule?`, `snoozedUntilEpochMillis?`, `linkedTaskId?`, `linkedInvoiceId?`, `linkedClientId?`, `linkedProjectId?`, `linkedSubscriptionId?`, `origin`, `createdAtEpochMillis` | `(triggerAtEpochMillis)`, `(kind)`, `(state)`, and one per link | all five links **SET NULL** — to `tasks`, `invoices`, `clients`, `projects`, `subscriptions` |
| `reminder_places` | Where one reminder waits, if it waits at a place rather than a time. | `reminderId`, `label`, `latitude`, `longitude`, `radiusMeters`, `trigger`, `isRegistered`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(reminderId)` unique, `(isRegistered)` | `reminderId` → `reminders.id` **CASCADE** |
| `saved_places` | Abu's place directory: a name he can refer to later. | `label`, `labelKey`, `latitude`, `longitude`, `radiusMeters`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(labelKey)` unique, `(label)` | none |
| `alarms` | An alarm clock: a time of day and the days it repeats on. | `label`, `minuteOfDay`, `repeatDays`, `isEnabled`, `nextTriggerAtEpochMillis?`, `snoozedUntilEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(isEnabled)`, `(nextTriggerAtEpochMillis)` | none |
| `alarm_records` | The OS-side fact: what Mira actually asked `AlarmManager` to do. | `reminderId?`, `label`, `triggerAtEpochMillis`, `isExact`, `state`, `firedAtEpochMillis?`, `requestCode`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(reminderId)`, `(requestCode)`, `(state)`, `(triggerAtEpochMillis)` | `reminderId` → `reminders.id` **SET NULL** |
| `timer_records` | A countdown. | `label`, `durationMillis`, `startedAtEpochMillis`, `endsAtEpochMillis`, `state`, `remainingMillisWhenPaused?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(state)`, `(endsAtEpochMillis)` | none |

- A **subtask is a separate table rather than a self-reference on `tasks`**: it has no due date,
  no priority and no reminder of its own, and modelling it as a task would mean every task query
  had to remember to exclude it.
- **`alarm_records` is deliberately distinct from `reminders`.** The reminder is Abu's intent; the
  alarm record is what the operating system was told — which request code was used, whether the
  alarm was allowed to be exact, whether it fired or was missed. Keeping them apart is what makes
  it possible to answer "the reminder exists, so why did nothing happen" after a reboot or a
  revoked exact-alarm permission. `SET NULL` on the reminder, because a fired alarm is a
  historical fact and must outlive its reminder.
- `timer_records` stores **`endsAtEpochMillis` rather than a remaining duration**, so a running
  timer stays correct across a process death: the answer comes from the wall clock, not from
  whatever the app last managed to write down. `remainingMillisWhenPaused` is only meaningful
  while paused.
- **`alarms` is not `alarm_records`, and neither is a `reminder`.** Three things wear the word
  "alarm" and they are genuinely different: `reminders` is Abu's intent, `alarm_records` is the
  ledger of what `AlarmManager` was told, and `alarms` is the alarm clock — the thing he means by
  "my seven o'clock". `alarms` stores a **minute of the day, not an instant**, because a repeating
  alarm means seven o'clock wherever he happens to be; storing an instant would move it every
  time he crossed a time zone.
- **`saved_places` is not `reminder_places`, and the split is load-bearing.** `saved_places` is
  the directory — "the office is here" — and is reusable. `reminder_places` is one reminder's
  trigger and keeps its **own copy** of the coordinate, so renaming, moving or deleting a saved
  place afterwards cannot silently move a reminder Abu has already set. `saved_places` carries no
  `deletedAtEpochMillis`: it holds a name for a spot rather than a record of something that
  happened, nothing armed depends on it once a reminder has copied it, and a tombstone would
  collide with the unique key the moment he re-saved a place under the name he had just freed.
- **`notes.archivedAtEpochMillis` is not `notes.deletedAtEpochMillis`.** Archiving is Abu saying he
  is finished with a note; deleting is him saying it was a mistake. One column doing both would
  make "empty the bin" throw away everything he had filed, so every read filters BOTH and the two
  views are separate.
- **`note_tags` points at the same `tags` table `task_tags` does.** Abu's vocabulary for labelling
  his work is one vocabulary; a second list of names would let "rayluxeo" and "Rayluxeo" become
  different tags depending on which screen he typed it on.
- **`ink_pages` keeps no strokes.** They are a file, like every other blob, and the row points at
  it — `strokeFileId` for the replayable strokes, `imageFileId` for the exported PNG. Both are
  `SET NULL` rather than `CASCADE`: a page whose stroke file was reclaimed can still show its
  image, and a page with neither is shown to Abu as writing that was lost. Cascading would delete
  the row and he would never learn anything had been there.
- `saved_places.labelKey` carries the unique index rather than `label`, so "Office", "office" and
  "the office" are one place instead of three. The key is derived by `SavedPlace.keyOf` before
  the row is written; storing it is what keeps the index and the lookup from drifting apart.

### 3.2 Identity, settings and history

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `user_profile` | Abu himself, and the details printed on an invoice. One row, `id = 1`. | `displayName`, `businessName?`, `email?`, `phone?`, `addressLine?`, `taxId?`, `defaultCurrencyCode`, `createdAtEpochMillis`, `updatedAtEpochMillis` | none | none |
| `app_settings` | Settings that only mean anything alongside the records. PK is `key`. | `value`, `updatedAtEpochMillis` | none | none |
| `activity_log` | What Mira did, in order. Append-only. | `category`, `action`, `summary`, `subjectTable?`, `subjectId?`, `occurredAtEpochMillis` | `(occurredAtEpochMillis)`, `(category)`, `(subjectTable, subjectId)` | none — by design |

- `user_profile` keeps the business details **queryable alongside the records that cite them**,
  rather than stranded in DataStore where an invoice cannot join to them.
- The `app_settings` / DataStore boundary matters: **DataStore holds what Mira needs before the
  database is open** — language, voice, haptics, whether onboarding is done. **`app_settings`
  holds what is written in the same transaction as the row that consumes it**, such as the next
  invoice number or a default tax rate. See §6.
- `activity_log` carries a **loose reference — a table name and a row id — rather than a foreign
  key**, because it has to survive the row it describes being deleted. A log that cascades away
  with its subject cannot answer "what happened to that invoice".

### 3.3 Business

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `clients` | Someone Abu works for. | `uuid`, `name`, `companyName?`, `email?`, `phone?`, `addressLine?`, `notes?`, `defaultCurrencyCode?`, `defaultRateMinor?`, `isArchived`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(uuid)` U, `(name)`, `(isArchived)`, `(deletedAtEpochMillis)` | none |
| `projects` | A body of work, usually for a client. | `uuid`, `clientId?`, `name`, `description?`, `status`, `startedOnEpochDay?`, `dueOnEpochDay?`, `budgetMinor?`, `currencyCode`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(uuid)` U, `(clientId)`, `(status)`, `(dueOnEpochDay)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL** |
| `project_milestones` | A checkpoint inside a project. | `projectId`, `title`, `dueOnEpochDay?`, `isComplete`, `completedAtEpochMillis?`, `position`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(projectId)`, `(dueOnEpochDay)` | `projectId` → `projects.id` **CASCADE** |

- The project's client link is `SET NULL` rather than `CASCADE`: **deleting a client must not
  silently take their project history — and the invoices hanging off it — with them.**
- `CASCADE` is safe on a milestone in a way it is not for an invoice: a milestone has no meaning
  apart from its project, nothing else references it, and nothing financial depends on it.

### 3.4 Money

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `invoices` | A bill Abu has issued. | `uuid`, `number`, `clientId`, `projectId?`, `status`, `issuedOnEpochDay`, `dueOnEpochDay`, `currencyCode`, `subtotalMinor`, `discountMinor`, `taxMinor`, `totalMinor`, `amountPaidMinor`, `notes?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(uuid)` U, `(number)` U, `(clientId)`, `(projectId)`, `(status)`, `(dueOnEpochDay)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **RESTRICT**; `projectId` → `projects.id` **SET NULL** |
| `invoice_items` | One line of an invoice. | `invoiceId`, `description`, `quantityMilli`, `unitPriceMinor`, `taxRateBasisPoints`, `lineTotalMinor`, `position` | `(invoiceId)` | `invoiceId` → `invoices.id` **CASCADE** |
| `quotes` | A price Abu has offered, before it becomes a bill. | `uuid`, `number`, `clientId`, `projectId?`, `status`, `issuedOnEpochDay`, `validUntilEpochDay?`, `currencyCode`, `subtotalMinor`, `discountMinor`, `taxMinor`, `totalMinor`, `convertedInvoiceId?`, `notes?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(uuid)` U, `(number)` U, `(clientId)`, `(projectId)`, `(convertedInvoiceId)`, `(status)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **RESTRICT**; `projectId` → `projects.id` **SET NULL**; `convertedInvoiceId` → `invoices.id` **SET NULL** |
| `quote_items` | One line of a quote. | `quoteId`, `description`, `quantityMilli`, `unitPriceMinor`, `taxRateBasisPoints`, `lineTotalMinor`, `position` | `(quoteId)` | `quoteId` → `quotes.id` **CASCADE** |
| `payments` | Money that actually arrived. | `invoiceId?`, `clientId?`, `amountMinor`, `currencyCode`, `method`, `receivedAtEpochMillis`, `reference?`, `note?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(invoiceId)`, `(clientId)`, `(receivedAtEpochMillis)` | `invoiceId` → `invoices.id` **SET NULL**; `clientId` → `clients.id` **SET NULL** |
| `expenses` | Money that went out. | `description`, `category`, `amountMinor`, `currencyCode`, `spentAtEpochMillis`, `clientId?`, `projectId?`, `receiptFileId?`, `isBillable`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(clientId)`, `(projectId)`, `(receiptFileId)`, `(spentAtEpochMillis)`, `(category)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL**; `receiptFileId` → `files.id` **SET NULL** |
| `income_entries` | Money that came in, invoiced or not. | `source`, `description?`, `amountMinor`, `currencyCode`, `receivedAtEpochMillis`, `clientId?`, `projectId?`, `paymentId?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(clientId)`, `(projectId)`, `(paymentId)`, `(receivedAtEpochMillis)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL**; `paymentId` → `payments.id` **SET NULL** |
| `budgets` | A ceiling Abu set for a stretch of time. | `name`, `category?`, `periodStartEpochDay`, `periodEndEpochDay`, `limitMinor`, `currencyCode`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(periodStartEpochDay, periodEndEpochDay)`, `(category)`, `(deletedAtEpochMillis)` | none |
| `subscriptions` | A recurring charge. | `name`, `vendor?`, `amountMinor`, `currencyCode`, `cycle`, `cycleDays?`, `nextChargeOnEpochDay`, `isActive`, `reminderId?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(reminderId)`, `(nextChargeOnEpochDay)`, `(isActive)`, `(deletedAtEpochMillis)` | `reminderId` → `reminders.id` **SET NULL** |
| `calculation_records` | Every sum Mira has worked out. Append-only. | `expression`, `resultText`, `languageTag`, `createdAtEpochMillis` | `(createdAtEpochMillis)` | none |

- `invoices.number` is the human-facing number Abu quotes on the phone, such as `INV-2026-014`.
  Unique, as is `quotes.number`.
- `invoices.amountPaidMinor` is kept in step with the payments that point at the invoice and is
  **never edited by hand**.
- **The client link on an invoice and on a quote is `RESTRICT`**: an invoice without a client is
  not a record Abu can act on, so the delete is refused rather than allowed to leave one behind.
- `quotes.convertedInvoiceId` records the moment a quote was accepted and became an invoice. It is
  `SET NULL`, not `CASCADE`: **voiding the invoice must leave the quote standing, because the
  quote is evidence of what was agreed.**
- **A payment is a fact about Abu's bank balance.** It happened whether or not the invoice it was
  matched against still exists, so both of its links are `SET NULL`. Deleting either orphans the
  payment rather than erasing it.
- `income_entries` is distinct from `payments` on purpose: a payment settles an invoice, while an
  income entry is anything Abu earned, including work that was never invoiced. `paymentId` links
  them when a payment does produce one, so the same taka is not counted twice.
- `budgets.category` is nullable, and **null means the budget covers every category** — the
  absence is meaningful, which is why `MiraConverters` has a nullable converter for
  `ExpenseCategory`. The period is epoch days rather than a month string so a budget can cover any
  span, including a single project's run.
- `subscriptions.cycleDays` is read only when `cycle` is `CUSTOM_DAYS`. `reminderId` is `SET NULL`
  so cancelling the reminder does not quietly delete the subscription Abu is still paying for.
- `calculation_records.resultText` stores **the exact text that was shown to Abu**, not a number to
  be formatted again later: re-deriving it would risk showing a different answer than the one he
  was given.

### 3.5 Knowledge

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `notes` | Anything worth writing down. | `title?`, `body`, `isPinned`, `clientId?`, `projectId?`, `languageTag`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(clientId)`, `(projectId)`, `(isPinned)`, `(updatedAtEpochMillis)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL** |
| `note_attachments` | The note↔file join. | `noteId`, `fileId`, `position`, `createdAtEpochMillis` | `(noteId, fileId)` U, `(fileId)` | `noteId` → `notes.id` **CASCADE**; `fileId` → `files.id` **CASCADE** |
| `memory` | Something Mira was told to remember, and nothing she was not. | `subject`, `fact`, `source`, `languageTag`, `clientId?`, `projectId?`, `recallCount`, `lastRecalledAtEpochMillis?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(subject)`, `(clientId)`, `(projectId)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL** |
| `voice_notes` | Something Abu said and kept. | `audioFileId?`, `noteId?`, `transcript?`, `durationMillis`, `languageTag`, `recordedAtEpochMillis`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(audioFileId)`, `(noteId)`, `(recordedAtEpochMillis)`, `(deletedAtEpochMillis)` | `audioFileId` → `files.id` **SET NULL**; `noteId` → `notes.id` **SET NULL** |
| `files` | A document on the phone that Mira knows about. | `uuid`, `displayName`, `relativePath`, `mimeType`, `sizeBytes`, `source`, `extractedText?`, `clientId?`, `projectId?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(uuid)` U, `(relativePath)` U, `(clientId)`, `(projectId)`, `(source)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL** |
| `scan_records` | What happened when a document was scanned. | `fileId`, `pageCount`, `recognisedAtEpochMillis?`, `createdAtEpochMillis` | `(fileId)` U | `fileId` → `files.id` **CASCADE** |
| `vault_items` | Abu's secrets, encrypted. | `title`, `cipherText` BLOB, `iv` BLOB, `category`, `createdAtEpochMillis`, `updatedAtEpochMillis` | none | none |

- `note_attachments` is the many-to-many table: one note can carry several documents and one
  document can be cited by several notes. The pair is unique so the same file cannot be attached
  twice. `CASCADE` on the note is correct — the link has no meaning without the note — and
  `CASCADE` on the file is equally safe, **because removing the link never removes the file row
  itself.**
- `memory` splits into a `subject` and a `fact` rather than one blob so recall can match on what
  the fact is *about* before matching its wording. `recallCount` and `lastRecalledAtEpochMillis`
  exist so Abu can see what Mira actually leans on, and prune what she does not.
- `voice_notes` holds what a recording *means*; the audio itself is a `files` row. The file link is
  `SET NULL` so a transcript survives its audio being cleared to reclaim space.
- **`files` never holds bytes.** `relativePath` points inside Mira's own files directory and is
  never absolute, which would not survive a restore. Deleting a file row is a soft delete, because
  a row removed while the file is still on disk would leave the file unreachable and unaccounted
  for; reclaiming the bytes is a separate, deliberate step against the soft-deleted rows.
- A scan is an event with its own outcome — how many pages, whether text recognition ran and when
  — while the file is the lasting artefact. **One file has at most one scan; the unique index says
  so.**

**The vault.** There is deliberately no plaintext column. `cipherText` is AES-256-GCM output and
`iv` is the nonce that produced it (`FieldCipher`, `AES/GCM/NoPadding`, 128-bit tag). The key
lives in the AndroidKeyStore under alias `mira.vault.aes256gcm.v1`, created with
`setRandomizedEncryptionRequired(true)` so the IV comes from the provider and is never derived
from anything; the raw key material never enters Mira's process memory or a backup. User
authentication is **not** required on the key itself — the vault gate is a `BiometricPrompt` at
the UI layer, because binding the key to biometrics would break background decryption and would
destroy every stored secret the moment Abu re-enrolled a fingerprint.

`title`, `category` and the timestamps are **not** encrypted: the summary list must sort and
render without unlocking anything, and decrypting every row to draw a list would mean a biometric
prompt to see a list of names. **Therefore a vault title is not a secret** — "Gmail recovery code"
is a fine title, the code itself is not — and the UI says so at the point of entry.

### 3.6 Assistant

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `conversations` | One thread of talk between Abu and Mira. | `title?`, `startedAtEpochMillis`, `lastMessageAtEpochMillis`, `messageCount`, `isArchived`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(lastMessageAtEpochMillis)`, `(isArchived)`, `(deletedAtEpochMillis)` | none |
| `conversation_messages` | One thing said, by Abu or by Mira. | `conversationId`, `role`, `text`, `languageTag`, `source`, `commandName?`, `engineName?`, `createdAtEpochMillis` | `(conversationId, createdAtEpochMillis)`, `(role)` | `conversationId` → `conversations.id` **CASCADE** |
| `automations` | Something Mira does on her own. | `name`, `description?`, `trigger`, `triggerConfigJson`, `actionConfigJson`, `isEnabled`, `lastRunAtEpochMillis?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(isEnabled)`, `(trigger)`, `(deletedAtEpochMillis)` | none |
| `automation_runs` | One attempt at running an automation. Append-only. | `automationId`, `startedAtEpochMillis`, `finishedAtEpochMillis?`, `outcome`, `detail?` | `(automationId, startedAtEpochMillis)`, `(outcome)` | `automationId` → `automations.id` **CASCADE** |
| `automation_record_fires` | The dedup ledger for record-driven triggers (v10): which record occurrence an automation has already fired for, so the daily check never fires the same rule twice for the same fact. Append-only bookkeeping, no soft delete. | `automationId`, `source`, `recordId`, `occurrenceFromEpochMillis`, `firedAtEpochMillis` | **unique** `(automationId, source, recordId, occurrenceFromEpochMillis)`, `(firedAtEpochMillis)` | `automationId` → `automations.id` **CASCADE** |

- `messageCount` and `lastMessageAtEpochMillis` are **kept on the conversation row rather than
  counted on read**, because the conversation list shows both for every thread and a `COUNT` per
  row would scan the messages table once per thread. They are maintained in the same transaction
  as the insert, so they cannot drift.
- `conversation_messages.commandName` and `engineName` record what the pipeline made of the
  message, which is what lets Abu see later whether an answer came from the phone or from Gemini.
  `engineName` is null for Abu's own messages. `CASCADE` is correct: a message outside its
  conversation is not a record of anything.
- **`automations` stores its trigger and action configuration as JSON strings.** That is
  deliberate: the shape of a place trigger has nothing in common with the shape of a time trigger,
  and a table wide enough to hold every variant as columns would be mostly nulls. The cost is that
  Room cannot validate them, so parsing is the repository's job and a malformed row is reported
  rather than crashing.
- `automation_runs.finishedAtEpochMillis` stays null while a run is in flight, which is how a run
  interrupted by the process dying can be told apart from one that genuinely failed.

### 3.7 Calendar and meetings

| Table | Purpose | Columns | Indices | Foreign keys |
|---|---|---|---|---|
| `meetings` | A conversation with someone else. | `title`, `clientId?`, `projectId?`, `startsAtEpochMillis`, `endsAtEpochMillis?`, `location?`, `deletedAtEpochMillis?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(clientId)`, `(projectId)`, `(startsAtEpochMillis)`, `(deletedAtEpochMillis)` | `clientId` → `clients.id` **SET NULL**; `projectId` → `projects.id` **SET NULL** |
| `meeting_notes` | What was said and agreed at a meeting. | `meetingId`, `body`, `isActionItem`, `taskId?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(meetingId)`, `(taskId)` | `meetingId` → `meetings.id` **CASCADE**; `taskId` → `tasks.id` **SET NULL** |
| `calendar_links` | The join between the phone's own calendar and Mira's records. | `systemEventId`, `systemCalendarId`, `title`, `startsAtEpochMillis`, `endsAtEpochMillis?`, `taskId?`, `projectId?`, `meetingId?`, `createdAtEpochMillis`, `updatedAtEpochMillis` | `(systemEventId)` U, `(taskId)`, `(projectId)`, `(meetingId)`, `(startsAtEpochMillis)` | `taskId` → `tasks.id` **SET NULL**; `projectId` → `projects.id` **SET NULL**; `meetingId` → `meetings.id` **SET NULL** |

- A meeting note is separate from a `notes` row because it is bound to a moment rather than filed
  on its own, and because a meeting usually accumulates several as it goes. `isActionItem` marks
  the ones that should become tasks; `taskId` is set once one has.
- **Mira does not copy the calendar.** The system provider stays the source of truth for what is
  scheduled; `calendar_links` only remembers that a particular system event corresponds to a task,
  a project or a meeting here, so the connection survives across syncs. `systemEventId` is
  `CalendarContract.Events._ID` on this device and is unique, because one event maps to at most
  one Mira record. `title` is a copy of the event title so the link still reads sensibly without
  calendar permission. All three local links are `SET NULL`, so unlinking one side never deletes
  the row that records the pairing.

---

## 4. Relationships and cascade policy

**The rule is not uniform.** Read this before adding a foreign key.

**CASCADE — only where a child is meaningless alone.**

| Child | Parent |
|---|---|
| `invoice_items` | `invoices` |
| `quote_items` | `quotes` |
| `subtasks` | `tasks` |
| `project_milestones` | `projects` |
| `meeting_notes` | `meetings` |
| `automation_runs` | `automations` |
| `scan_records` | `files` |
| `note_attachments` | `notes` *and* `files` |
| `conversation_messages` | `conversations` |

Each of these has no meaning apart from its parent, nothing else references it, and nothing
financial depends on it. `note_attachments` cascades from both sides because it is only the link;
deleting it never deletes the note or the file row.

**RESTRICT — from clients to billing.** `invoices.clientId` and `quotes.clientId` are `RESTRICT`.
Deleting a client with invoices or quotes is **refused**, so billing history cannot be deleted out
from under itself.

**SET NULL — everywhere a record is a FACT that must outlive its parent.**

| Child column | Parent | Why |
|---|---|---|
| `payments.invoiceId`, `payments.clientId` | invoices, clients | The money arrived. It happened whether or not the invoice still exists. |
| `income_entries.clientId/projectId/paymentId` | clients, projects, payments | Earnings are not contingent on the work record surviving. |
| `expenses.clientId/projectId/receiptFileId` | clients, projects, files | The spend happened; losing the receipt must not lose the expense. |
| `alarm_records.reminderId` | reminders | A fired alarm is history. |
| `calendar_links.taskId/projectId/meetingId` | tasks, projects, meetings | Unlinking one side must not delete the record of the pairing. |
| `tasks.projectId` | projects | Deleting a project keeps its tasks. |
| `projects.clientId` | clients | Deleting a client keeps their project history. |
| `notes.*`, `memory.*`, `files.*`, `meetings.*` | clients, projects | The content outlives the filing. |
| `voice_notes.audioFileId/noteId` | files, notes | A transcript survives its audio being cleared. |
| `quotes.convertedInvoiceId` | invoices | The quote is evidence of what was agreed. |
| `subscriptions.reminderId` | reminders | Cancelling a reminder must not delete the charge Abu still pays. |
| `meeting_notes.taskId` | tasks | The note stands whether or not the task does. |

**A `CASCADE` from a client, a project or an invoice onto `payments`, `income_entries` or
`expenses` would destroy financial history and must never be added.** That is the whole reason
clients are `RESTRICT` to billing and every money row is `SET NULL` to everything above it. If a
future change appears to need such a cascade, the change is wrong.

`reminders.linkedTaskId` was an unenforced link in version 1. It has had a declared
`SET NULL` foreign key since version 2, and the table was rebuilt again in version 4 when the
four further links were added; all five are `SET NULL` for the reason in the table above.

---

## 5. Full-text search

Seven **FTS4 external-content** indexes. External-content means the index stores no second copy of
Abu's data; it shadows the content table and Room generates the `AFTER INSERT`, `AFTER UPDATE`,
`BEFORE UPDATE` and `BEFORE DELETE` triggers that keep it in step.

| FTS table | Content table | Indexed columns |
|---|---|---|
| `task_fts` | `tasks` | `title`, `notes` |
| `client_fts` | `clients` | `name`, `companyName`, `notes` |
| `project_fts` | `projects` | `name`, `description` |
| `note_fts` | `notes` | `title`, `body` |
| `memory_fts` | `memory` | `subject`, `fact` |
| `file_fts` | `files` | `displayName`, `extractedText` |
| `conversation_message_fts` | `conversation_messages` | `text` |

All seven use the `simple` tokenizer. A search joins **`fts.rowid = content.id`** and selects from
the content table; the index supplies only the match.

**Two traps, both of which have been hit:**

1. **Never alias the FTS table in a `MATCH`.** FTS4's match operand is a hidden column named after
   the table, so `JOIN conversation_message_fts AS f ... WHERE f MATCH :query` fails with "no such
   column". The content table may be aliased freely; the FTS table may not. `ConversationDao` and
   `FileDao` alias the content side and leave the FTS name bare — that asymmetry is deliberate.
2. **Apply the soft-delete filter to the CONTENT table.** The index shadows the whole table,
   deleted rows included, so `WHERE task_fts MATCH :match` alone returns rows Abu has deleted.
   Every search query carries `AND <content>.deletedAtEpochMillis IS NULL` on the joined row.

**`MATCH` takes a query language, not a literal.** Anything Abu dictates can carry apostrophes,
hyphens, colons or quotes that SQLite reads as operators and rejects. `ftsMatchOrNull`
(`data/repository/FtsQuery.kt`) reduces each token to Unicode letters and digits — so Bengali
script survives intact — and appends a prefix wildcard, so "ahm" finds "Ahmed". It returns null
when nothing searchable remains, which callers treat as "no query at all". No DAO is ever handed a
raw phrase.

---

## 6. What is not in Room

| Data | Lives in | Why |
|---|---|---|
| Language mode, spoken responses, activation sound, haptics, TTS engine and voice names, speech rate, pitch, voice-setup and onboarding flags | **DataStore Preferences**, file `mira_settings` | Mira needs them before the database is open. Small, flat, frequently observed. |
| Gemini API key | **DataStore Preferences**, key `gemini_api_key_sealed`, AES-256-GCM sealed with the Keystore key | Ciphertext only, never the key itself. See `docs/PRIVACY.md`. |
| `DailyBrief` | computed on demand | Derived data; storing it would let it go stale. |
| File bytes | Mira's own files directory, indexed by `files.relativePath` | See §3.5. |

DataStore keys: `language_mode`, `spoken_responses`, `activation_sound`, `haptic_feedback`,
`voice_engine`, `voice_english_name`, `voice_bengali_name`, `voice_speech_rate`, `voice_pitch`,
`voice_setup_complete`, `onboarding_complete`, `gemini_api_key_sealed`. **A preference key is a
string on disk forever; renaming one silently forgets Abu's choice**, which is why they all live
together in `MiraPreferenceKeys` where that cost is visible. The store is a process-keyed
`preferencesDataStore` delegate rather than only a Hilt `@Singleton`, because a Hilt singleton is
unique per component and instrumentation builds a fresh component per test class — the second one
to touch settings died with "There are multiple DataStores active for the same file". A corrupted
file resets to defaults rather than throwing, since settings are reconstructible and Mira refusing
to start over a truncated preferences file would be the worse failure.

`geminiEnabled` is **derived**, not stored: it is true only when a sealed key is actually present.
There is no way to have the flag on with no key.

Voice identity is persisted as **strings** — engine package plus `Voice.getName()` — never as a
`Voice` Parcelable, whose format has no cross-version stability. A stored pair that no longer
resolves degrades to the engine default. See ADR-004.

---

## 7. Migrations

### 7.1 Policy

1. **`fallbackToDestructiveMigration` is forbidden** in every build type, including debug. Abu's
   tasks, vault, conversation history and — from version 2 — his invoices are not reproducible.
   A schema change that arrives without a migration must fail loudly at startup rather than
   quietly emptying the database. There is no destructive fallback in `MiraDatabase`, in
   `MiraMigrations` or in the Hilt module that builds the database.
2. **Every schema change bumps `version` and ships an explicit `Migration`** in
   `MiraMigrations.all`.
3. **The exported schema JSON is committed.** `app/schemas/` is not git-ignored. A change that
   alters the schema without producing a new JSON file has not actually been built.
4. **Every migration gets a test** (§8). A migration that has only been "checked by reading it" is
   untested.
5. **Renaming an enum constant is a migration**, because enums are stored by name (§1).
6. **Never reuse a version number.** If a schema was ever on Abu's device, it is spent.
7. There is no server and no sync, so a migration only has to handle *one* device's history — but
   that device's history is the only copy and there is no backup to restore from
   (`allowBackup="false"`). That makes correctness cheap to achieve and expensive to skip.

**The migration SQL is GENERATED, not hand-written.** Every `CREATE TABLE`, `CREATE INDEX` and
`CREATE TRIGGER` in `MIGRATION_1_2` is taken verbatim from the DDL Room exported for version 2.
This matters: a migration that disagrees with the generated schema by one column type passes every
test that does not open a real old database, and then fails on the one phone that has the old
data. Retyping the DDL is how that happens. `runMigrationsAndValidate` compares the result against
the exported schema, so a hand-edit that drifts is caught — but only because the two are meant to
be identical in the first place.

### 7.2 What 1 → 2 does

Version 1 held `tasks`, `task_fts`, `reminders`, `vault_items` and a flat `conversation_turns`
table. Version 2 adds the full record set — clients, projects, invoices, quotes, money, notes,
memory, files, automations, meetings — and reshapes the transcript into `conversations` and
`conversation_messages`. `reminders` and `vault_items` are untouched.

Three parts are not plain table creation. **All three were real bugs, each caught by the migration
tests.**

**1. `tasks` is REBUILT, not altered.** It gains a foreign key to `projects`, and **SQLite cannot
add a foreign key with `ALTER TABLE`**. So: `projects` is created first, `tasks` is renamed to
`tasks_v1`, the new `tasks` is created with the FK, rows are copied by **explicit column list** —
so a future column-order change cannot silently shift Abu's data into the wrong fields — and
`tasks_v1` is dropped.

The four indices on `tasks` are created **after** the `DROP`, and the order is load-bearing.
`ALTER TABLE ... RENAME TO` carries the old table's indices to the renamed table under their
original names. Creating `index_tasks_isDone` before the drop would therefore hit a name already
taken by the index now belonging to `tasks_v1`, and `CREATE INDEX IF NOT EXISTS` makes that a
**silent no-op**; `DROP TABLE tasks_v1` then takes those indices with it, leaving the new `tasks`
with none. Creating them last is the only correct order.

**2. `task_fts` and `conversation_message_fts` are explicitly rebuilt.** An external-content FTS
index belongs to the table it shadows, and by the time the task rows have been copied that table
no longer exists, so `task_fts` is dropped and recreated against the new `tasks`. More
importantly, **an external-content index is not retrospective**: rows inserted before the sync
triggers exist are invisible to search. Both indexes are therefore backfilled with
`INSERT INTO <fts>(<fts>) VALUES('rebuild')`. Without it the data survives the migration perfectly
and becomes permanently unfindable — the task looks fine in the list and returns nothing to a
search, and every word Abu said before version 2 is in the table and findable by nothing.

**3. Conversation turns are moved into one thread, with their ids preserved**, rather than
discarded — so nothing that referenced a turn is left pointing at the wrong message. The thread
row is inserted with `SELECT … FROM conversation_turns HAVING COUNT(*) > 0`. **`HAVING`, not
`WHERE`**: an aggregate `SELECT` with no `GROUP BY` returns exactly one row however few rows it
read, so a `WHERE` guard would still have created an empty conversation on a database that had
never been spoken to. `conversation_turns` is dropped once the copy is done.

The FTS sync triggers for all seven indexes are created at the end, again verbatim from the
exported DDL.

### 7.3 What 2 → 3 does

The task system. `tasks` gains `description`, `dueAllDay`, `estimatedMinutes`, `clientId`,
`recurrenceRule`, `seriesUuid` and `archivedAtEpochMillis`; `tags`, `task_tags` and
`task_attachments` arrive; `task_fts` gains `description`.

`tasks` is REBUILT again, for the same reason as 1 → 2: `clientId` is a new foreign key and
SQLite cannot add one with `ALTER TABLE`. **Both traps from 1 → 2 apply again and were hit
again** — the seven indices are created after the `DROP`, and `task_fts` is rebuilt with
`INSERT INTO task_fts(task_fts) VALUES('rebuild')` so that tasks written before version 3 stay
searchable. Neither trap announces itself: the first leaves a correct-looking table with no
indices, the second leaves correct-looking rows that no search will ever return.

`TaskStatus` is **derived, not stored**. There is no status column — a task's status is read from
`isDone`, `dueAtEpochMillis`, `archivedAtEpochMillis` and `deletedAtEpochMillis`. A stored copy
would be a second source of truth for something already fully determined, and the two would
disagree the first time a due date moved.

### 7.4 What 3 → 4 does

Reminders, alarms and places. `reminders` gains `kind`, `recurrenceRule`,
`snoozedUntilEpochMillis` and four more links; `reminder_places` and `alarms` are created.

`reminders` is REBUILT — four new foreign keys — and the same two traps apply a third time.

The one judgement call is back-filling `kind` on rows written before the column existed:

```sql
CASE WHEN linkedTaskId IS NULL THEN 'ONE_TIME' ELSE 'TASK' END
```

Nothing beyond that is guessed. A reminder Abu made by hand stays `ONE_TIME` even if its title
mentions an invoice, because a title is not a link and inferring from prose would silently
reclassify his records.

### 7.5 What 4 → 5 does

`saved_places`, and nothing else. The plainest migration in the file: one new table and its two
indices, nothing rebuilt, nothing copied, no back-fill. The place directory is information Mira
could not previously hold, so there is no old column to derive it from. Every reminder already
waiting at a place keeps its own coordinate in `reminder_places` and is untouched — which the
migration test asserts against a real row rather than an empty database.

---

### 7.6 What 5 → 6 does

Notes gain a task link, an archive, tags and handwriting. `notes` is REBUILT, because `taskId` is
a foreign key and SQLite cannot add one with `ALTER TABLE` — the fourth rebuild in this file.

It carries both earlier traps AND a third that is specific to this table:

1. The seven indices are created **after** the `DROP`, as in every other rebuild.
2. `note_fts` is **rebuilt**, because an external-content index is not retrospective.
3. **The four FTS sync triggers are dropped FIRST and recreated last.** This one is new, because
   `note_fts` did not exist when 1 → 2 was written. The triggers are attached to `notes`, so the
   rename carries them to `notes_v5` — where the `BEFORE DELETE` trigger then fires once per row
   as `notes_v5` is dropped, deleting from `note_fts` by a rowid that by then belongs to a
   DIFFERENT note in the rebuilt table. Dropping them up front is what stops the teardown of the
   old table quietly shredding the index of the new one. Nothing about that failure is visible
   until Abu searches for a note he can plainly see in the list.

Neither new column is guessed: every existing note arrives unarchived and linked to no task,
which is what those rows already were.

Verified on a device against real data, not an empty database: two notes written at version 5
came through with their ids, and one of them was still returned by `MATCH 'laminator'` afterwards.

## 8. Testing

`app/src/androidTest/java/com/rayluxeo/mira/data/db/MiraMigrationTest.kt` — **instrumented, not
local.**

**Why not a local Robolectric test:** `MigrationTestHelper` opens a real database file, and
Robolectric's `FileDescriptor` interception cannot serve one on JDK 21. Every test fails with
"Failed to interact with raw FileDescriptor internals" before a single statement runs. Running on
a device also tests the SQLite that will actually run the migration. (No Hilt in local unit tests
either — AGENTS.md §7.4, ADR-007.)

The migration is tested against a **real version 1 database with real rows in it**, never an empty
one: an empty database migrates cleanly even when the statement that moves Abu's data is wrong.
Seven tests cover it — every table present and `conversation_turns`/`tasks_v1` gone; a task
surviving the rebuild with its id and fields; the rebuilt task still findable by FTS; turns moved
into one thread with ids and counts preserved; an empty version 1 database migrating **without
inventing a conversation**; a vault item untouched; and moved turns findable by FTS afterwards.
The two full-text tests each carry a comment saying what goes wrong without the rebuild — that the
data survives and becomes unfindable. Leave those comments there.

Run it with `./gradlew :app:connectedDebugAndroidTest` against a device or the `Pixel_6` AVD.
