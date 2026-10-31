# Feature checklist

The living tracker for everything Mira is and everything she is meant to become.

**Update rule.** Step 10 of the working agreement in `AGENTS.md` §13: every session updates this
file to match what is now genuinely true.

**Status markers**

| Marker | Meaning |
|---|---|
| `[x]` | **Done** — the implementation is on disk and complete |
| `[~]` | **In progress** — the contract is on disk and the implementation is being written now |
| `[ ]` | **Planned** — not started; nothing exists beyond this line |

`[x]` does **not** on its own mean "seen working on the phone". Device verification is recorded
separately, per round, and only by whoever actually ran it. Do not promote an item on the
strength of a clean compile, and never record a device pass you did not perform.

**v0.1, verified on the device on 2026-09-24.** Built, unit-tested (364 local tests, 2
instrumented), installed on an API 37 emulator and driven by hand through Home, Mira, Tasks,
Reminders, Vault, Settings and Voice Setup.

What was actually exercised on the device, rather than only compiled: an English reminder, the
Banglish form of the same request (answered in Bengali with Bengali numerals), a calculation, the
reminder appearing in the Reminders list and on Home, the Vault's locked state and its
no-enrolment path, the exact-alarm warning with its grant action, and the Voice Setup engine and
voice lists populated from the device's own TTS engine.

Not yet exercised on real hardware: **speech input**. The emulator has no usable microphone, so
nothing below about recognising an utterance has been proved by speaking to it — only that the
recognizer is reached, and that its refusals are mapped and rendered. Spoken *output* has now been
exercised on the emulator, in Bengali as well as English, with an offline voice.

**The design-system and shell round, 2026-09-24 — not compiled, not run, not device-verified.**
The seven design-system files, the component library, the seventeen routes, the areas hub and
first-run onboarding were written in one round by several agents working in parallel. Gradle was
deliberately not run from any of those sessions — a second daemon deadlocks the build
(`AGENTS.md` §8) — so the lead compiles centrally and is the one who records the device pass.

Every `[x]` added in that round was checked by reading the file it describes. A `[~]` is
something whose contract is settled and whose implementation was still landing as this was
written; anyone updating this file next should re-read those and promote or correct them rather
than trusting the marker.

---

## v0.1 — the scope that ships

This is everything that works. Beyond it there are nine declared areas with a route and an
honest screen apiece, and nothing else — no table, no command, no stub. Do not invent any.

### Foundations

- [x] Clean-architecture package layering, `domain/` pure Kotlin (`ARCHITECTURE.md` §1)
- [x] `AppResult` / `MiraError` / `MiraErrorCode` — failures as values
- [x] `MiraClock` and `DispatcherProvider` injected, no wall-clock or dispatcher literals
- [x] `LanguageMode` / `SpokenLanguage` / `TextScript` language model
- [x] Domain models: `Task`, `Reminder`, `VaultItem` (+ `VaultItemSummary`), `ConversationTurn`, `DailyBrief`, `Money`
- [x] Repository contracts: task, reminder, vault, conversation, brief, settings, Gemini credential store
- [x] `ReminderScheduler` contract
- [x] Use cases: task (create, search, resolve, complete, set-done, delete), reminder (create, cancel), brief, vault (create, reveal, delete)
- [x] `ExecuteCommandUseCase` — the whole pipeline in one file
- [x] Design system — seven files under `core/design/` (`AGENTS.md` §10): `MiraColors` (`MiraPalette`, `MiraExtendedColors`, `MiraColorScheme`), `MiraTypography`, `MiraSpacing`, `MiraShapes`, `MiraMotion`, `MiraHaptics`, `MiraTheme`
- [x] `MiraMotion` as a plain object — one duration scale, four easings, no literal `tween()` anywhere (ADR-010)
- [x] `rememberMiraHaptics()` and `LocalHapticsEnabled`, provided at the app root from Abu's setting
- [x] Component library — 24 components under `core/design/component/` (`AGENTS.md` §10.3), all
      compiling, all with previews. `MiraInlineNotice` was added afterwards to replace five
      hand-built dismissible banners that had already drifted apart
- [ ] `MiraErrorState` has no call site yet. It is the right component for a screen whose content
      could not load at all, and no screen fails that hard today — they degrade to an empty list
      plus a notice. Adopt it the first time one genuinely can
- [x] App icon, monogram and splash drawables, adaptive + monochrome launcher icons
- [x] Backup posture: `allowBackup="false"`, `backup_rules.xml`, `data_extraction_rules.xml` (all four domains excluded from cloud backup and device transfer)
- [x] Project documentation: `AGENTS.md`, `ARCHITECTURE.md`, `DATA_MODEL.md`, `PRIVACY.md`, `ZERO_COST_AUDIT.md`, this file, `.gitignore`
- [x] Hilt graph: application module, repository bindings, engine qualifiers wired
- [x] Room database, entities, DAOs, mappers, repository implementations (`DATA_MODEL.md`)
- [x] **The full record schema — 35 tables plus 7 FTS indexes, at database version 2.** Every
      entity in the product brief: profile, tasks and subtasks, reminders, alarm records, timers,
      notes and attachments, clients, projects and milestones, invoices, quotes and their lines,
      payments, expenses, income, budgets, subscriptions, calculations, calendar links, memory,
      automations and runs, conversations and messages, files, scans, meetings and meeting notes,
      activity log and app settings
- [x] 27 DAOs, 26 repository ports and 29 implementations over that schema, all behind `AppResult`
- [x] Money persisted as whole minor units in a `Long` beside its currency code — never a `Float`
      or a `Double` at any point, verified by an audit sweep across `data/` and `domain/`
- [x] DataStore settings store + Keystore-sealed Gemini credential store
- [x] Manifest permissions and `<queries>` (`PRIVACY.md` §7) — declared and exercised on device

### Command pipeline

- [x] `AppCommand` sealed vocabulary, `isMutating` flag
- [x] `CommandRequest` / `EngineResult` / `CommandEngine` contracts
- [x] `DefaultCommandValidator` — trim, length ceiling, positive ids, future-dated reminders
- [x] `DefaultCommandDispatcher` — exhaustive `when`, the only place a command becomes an effect
- [x] `CommandRouter` — local first, Gemini on a local miss **or a deferred greeting** (small talk
      reaches the cloud for a real in-language reply; offline the local greeting still stands), local
      verdict wins a cloud miss
- [x] Confirmation rule — mutating **and** (low confidence **or** Gemini), re-validated on confirm
- [x] `MiraResponse` / `CommandOutcome` / `ConfirmationRequest` as language-free data
- [x] Local on-device `CommandEngine` implementation (intent matching)
- [x] `TextNormalizer` — Bengali script, Banglish folding, ASCII digits
- [x] `DateTimeParser` — relative and absolute times in en / bn / Banglish
- [x] Gemini `CommandEngine` — optional, off by default, proposes only

### Working commands

Twenty-three commands, and the count is enforced rather than claimed: `AppCommand` has 23
variants, `DefaultCommandDispatcher` has 23 branches over them with no `else`, and
`DefaultCommandValidator` has 23 rules. Adding a command without a handler is a compile error.
Every one reaches a real use case — there is no branch that returns a placeholder.

`MiraIntent` in `ai/nlu/Lexicon.kt` carries the matching 23 intents, so the local engine can
reach all of them without Gemini. Each is matched in English, Bengali script, Banglish and mixed
input.

**Work** — `WorkCommandHandler`

- [x] `CreateTask` — title, optional due time, priority, notes
- [x] `UpdateTask` — retitle, re-time, re-prioritise an existing task
- [x] `CompleteTask` — by id, or by fuzzy title with ambiguity reported rather than guessed
- [x] `SearchTasks` — query + `TaskFilter`
- [x] `CreateReminder` — title + future trigger, arms an exact OS alarm
- [x] `CreateAlarm` — optional label + time of day + repeat days. Goes to the alarm CLOCK
      (`SetAlarmUseCase`, the `alarms` table), not to a reminder. Until 25 September 2026 it wrote
      a `Reminder` instead, so a spoken alarm never appeared on the Alarms screen, never rang on
      the alarm stream and could not repeat — two implementations of one idea
- [x] `CreatePlaceReminder` — title + a place Abu has named + enter/leave. The place is resolved
      against `saved_places`; an unknown name answers with the places Mira DOES know rather than
      a blank miss
- [x] `CreateTimer` — label + duration
- [x] `GetDailyBrief` — counts and the next reminder

**Knowledge** — `KnowledgeCommandHandler`

- [x] `CreateNote` — optional title + body
- [x] `SearchNotes` — FTS over the note body
- [x] `StoreMemory` — a subject and a fact Mira should keep
- [x] `SearchMemory` — FTS over what she has been told

**Business** — `BusinessCommandHandler`

- [x] `CreateClient` — name, optional contact details
- [x] `SearchClients` — FTS over client records
- [x] `CreateInvoiceDraft` — client + line items, left as a draft rather than issued
- [x] `CreateQuoteDraft` — the offer before the bill (16 Oct 2026). Mirrors the invoice draft through a
      new `CreateQuoteDraftUseCase` priced by the existing `BuildQuoteTotalsUseCase`, so a quote and the
      invoice it becomes are priced to the paisa by one rule. Always a DRAFT; becomes an invoice through
      the existing quote→invoice conversion. "quote" / "quotation" / "কোটেশন" never fold to the invoice
      token, so it is a distinct intent. Device-verified: "quote Rahman Traders for 20000 taka" → draft
      quote QUO-2026-001.
- [x] `QueryClientStatement` — read-only: a client's whole standing, billed/paid/outstanding (16 Oct
      2026). A new `BuildClientStatementUseCase` totals only *issued* invoices (drafts and voids
      excluded) and refuses to add across currencies (reports `currencyMixed`, like the reports).
      Device-verified: "Rahman Traders statement" → "No issued invoices yet." (the client's only
      invoice is a draft, correctly excluded).

**Money** — `MoneyCommandHandler`

- [x] `RecordPayment` — against an invoice, `MoneyAmount` throughout
- [x] `RecordExpense` — amount, category, optional project
- [x] `RecordIncome` — amount, source
- [x] `PerformCalculation` — deterministic `BigDecimal` arithmetic, never a model
- [x] `QueryExpenses` — read-only total over a window, optional category, summed in `MoneyAmount`
- [x] `QueryIncome` — read-only income total over a window, summed by the repository in `MoneyAmount`

**Phone** — `PhoneCommandHandler`

- [x] `CreateCalendarEvent` — title, time, optional duration
- [x] `FindContact` — by name or number
- [x] `PrepareCall` — resolves the contact and hands off; Mira never dials silently
- [x] `PrepareMessage` — resolves the contact and composes; Mira never sends silently

**Salah** — `SalahCommandHandler`, every one answered by local deterministic logic, never Gemini
(§2, §3). The prayer vocabulary (names, "namaz"/"salah", the "prayed"/"porechi"/"adai" verb) folds
uniformly from English, Bengali script and Banglish in `Vocabulary`; `দুপুর` stays NOON there and a
day-part is read as the prayer that falls in it ("dupurer namaz" → Dhuhr) only in a prayer context.

- [x] `MarkPrayerPrayed` — logs an obligatory prayer, or Tahajjud, as prayed via `TrackPrayerUseCase`
      / `TrackTahajjudUseCase` with `source = VOICE`; mutating, so it confirms when uncertain
- [x] `QueryNextPrayer` — the next prayer and its countdown from `SalahClock.nextPrayer`
- [x] `QueryPrayerTimeRemaining` — time left to a named prayer, or the one coming up
- [x] `QueryPrayerTime` — the clock time of a named prayer on a day ("tomorrow's Fajr")
- [x] `QueryPrayerTracking` — a single day's count of five, or a month's totals and streaks from
      `GetMonthlySalahUseCase`; window-defaulted like `QueryIncome`
- [x] `SetTahajjudReminder` — turns the Tahajjud reminder on (`LAST_THIRD`) through
      `UpdateSalahSettingsUseCase`, which re-arms the night alarm; mutating

A command whose Android permission has been withheld returns `MiraResponse.PermissionNeeded` as a
**success**, not a failure: Mira understood the request and is telling Abu what she needs, which is
a different thing from not understanding him.

### Reports (9 October 2026)

A built area (`MiraArea.REPORTS`, `isBuilt = true`), reached from the Home areas grid. Every figure
is worked out by `DefaultReportEngine` in deterministic Kotlin over the finance repositories — a
language model is never in the path (§4, §8). Money is summed as `Money`/`BigDecimal`; a window that
mixes currencies is reported as mixed rather than silently added.

- [x] **Periods** — Today, This week, This month, Last month, and a Custom range. `ReportPeriod` +
      `ReportPeriod.resolve(clock, weekStart)` produce a half-open `[start, endExclusive)` window from
      the injected `MiraClock`; the Custom range uses Material 3's `DateRangePicker` in a fully
      expanded `MiraBottomSheet`, read back in UTC.
- [x] **The twelve reports** — income, expenses, net business summary, outstanding invoices, payments
      received, client revenue, project revenue, expense categories, task completion, overdue tasks,
      subscription costs, project status. `ReportType` enumerates them; the engine fills one
      `BusinessReport` each, carrying only `Money`, counts and enums.
- [x] **On-screen** — one `MiraCard` per report with a headline and either a `MiraBarChart` breakdown
      (named/category lines) or a labelled list (metric lines and counts). Charts are drawn with
      Compose `Canvas` in `core/design/component/MiraChart.kt` — no charting library, no cost.
- [x] **PDF export** — `ReportExporter.writePdf` draws onto A4 pages with Android's own
      `android.graphics.pdf.PdfDocument`, paginating across the twelve sections; written to a
      destination Abu picks through the system file picker (SAF `CreateDocument`).
- [x] **CSV export** — `ReportExporter.writeCsv`, RFC-4180 quoting, same SAF destination picker.
- [x] **Bilingual** — titles, metric/category/status labels, money and dates render per language in
      `ReportsWording`; Bengali uses its own numerals and the taka sign.
- [x] **By voice** — the spec's queries are already understood by existing read-only commands:
      "এই মাসে কত income হয়েছে?" → `QueryIncome`, "September এর business expenses দেখাও." →
      `QueryExpenses`, "How much does Ahmed owe?" → `QueryInvoice` →
      `MiraResponse.InvoiceOutstandingReport`.
- [ ] **"Make me a PDF monthly report." by voice** — deferred. A PDF needs a destination, which means
      the file picker; a voice command cannot complete it silently and offline without a chosen
      destination. Planned as a command that opens the Reports screen with the month preselected so
      the export is one tap away, rather than a handler that claims a file it did not write (§12).

### Settings — the complete surface (9 October 2026)

`SettingsScreen` is eight named sections, every control wired to real behaviour — nothing on the
screen only looks like it did something (§12). New persisted settings live in `MiraSettings` /
`MiraPreferences` (DataStore); the write-through model has no Save button.

- [x] **MIRA** — Assistant role (→ assistant setup), How Mira sounds (→ voice setup), Spoken responses
      (`VoicePreferenceRepository`), Language (segmented), **Conversation style** (segmented), Activation sound.
- [x] **Conversation style is real.** `ConversationStyle { CONCISE, BALANCED, DETAILED }` drives
      `MiraResponseRenderer` through a `ConversationStyleProvider`. BALANCED is byte-for-byte today's
      wording; CONCISE strips the lead-in, DETAILED adds one truthful line. No number or outcome ever
      changes across styles (§8) — verified by `ConversationStyleRenderingTest`.
- [x] **GENERAL** — Theme (a stated value, "AMOLED dark", not a dead toggle), Haptics, **Date format**,
      **Currency**, **First day of week**.
- [x] **Date format app-wide.** `DateStyle` (ISO / d MMM yyyy / MMM d, yyyy / dd-MM / MM-dd) is provided
      once at the shell root as `LocalDateStyle` and rendered through `ui/common/formatDate`; the invoice
      and money date sites read it, so the setting changes dates across the app.
- [x] **First day of week** feeds `DefaultReportEngine` → `ReportPeriod.resolve(clock, weekStart)`, so a
      "This week" report starts on the chosen day. **Currency** edits `UserProfile.defaultCurrency`, which
      the daily brief and new invoice/quote drafts (`InvoiceDraftUseCases`) read.
- [x] **NOTIFICATIONS** — per-category switches (Tasks / Reminders / Invoices / Subscriptions) that gate
      `MiraNotifications.showReminder` by `ReminderKind`; **Quiet hours** (toggle + From/To time pickers)
      wired to the existing `setQuietHours`, which already softens a reminder to silent/low-priority;
      Morning brief / Evening review (existing `BriefSettingsSection`); Places.
- [x] **AI** — Gemini key management; **Disable cloud AI** (a key-preserving soft switch —
      `geminiEnabled = keyPresent && cloudAiEnabled`); **Availability** and **Free-tier state** from a real
      quota signal (`CloudAiStatusProvider` ← `GeminiAvailabilityManager.quotaState`, set only from an
      actual HTTP 429 / success — no invented usage meter).
- [x] **PRIVACY & SECURITY** — Biometric lock, **Auto-lock** (the delay now genuinely re-locks on the
      first return after at least that long away, and "Lock when I leave" shuts it at once — until the
      15 Oct 2026 audit fix the delay was read, displayed and never applied, so every value behaved
      like "Immediately"; `AppLockController` now owns the timing against the injected `MiraClock`),
      **Permissions** (new screen), Activity log (new screen), and the **"Require unlock for"** switches,
      which now put a real biometric gate (`AreaLockGate`) in front of Money, Memory, Invoices and
      Backups — until the same fix they persisted a flag that nothing read, so no area was ever gated.
- [x] **DATA** — Backup/restore/export (→ backup screen, now real — see below), Clear the conversation,
      **Erase all data** (`EraseAllDataUseCase`, behind a destructive confirm).
- [x] **INTEGRATIONS** — Calendar and Contacts with live grant status and a tap to the system screen;
      Default assistant. (Gmail is omitted, not shown as a dead row — Mira has no Gmail integration.)
- [x] **ABOUT** — Version, Privacy information (→ privacy dashboard), **Open-source licenses** (new screen).
- [x] **Permissions screen** (`ui/settings/PermissionsScreen`) — the five `MiraCapability` grants, read
      live from the OS on every resume, requested in place or opened to the right system screen.
- [x] **Activity log screen** (`ui/activity/`) — binds the live `ActivityLogRepository`, category filter,
      read-only entries, never the raw machine verb.
- [x] **Licenses screen** (`ui/licenses/`) — the real component/licence list from `ZERO_COST_AUDIT.md`,
      the bundled Noto Serif Bengali OFL text viewable in a sheet.

### Backup, restore & export — now real (9 October 2026)

Was a metadata-only stub. `BackupRepositoryImpl` now, through the DAOs directly (it is in `data/`, §8):

- [x] **Encrypted `.mbak`** is a full whole-database snapshot — WAL checkpointed, the DB file and the
      DataStore prefs captured and encrypted via `PortableBackupCipher`.
- [x] **Restore** validates the envelope and refuses a newer-schema backup, then replaces the live files
      and signals a required restart (`BackupUiState.restartRequired` → a "Restart now" action).
- [x] **JSON / CSV export** writes real records (tasks, notes, clients, invoices, expenses, income,
      payments), money as minor-units + currency code, soft-deleted rows excluded. Covered by
      `BackupEnvelopeTest` + `DataExportSerializerTest`; the real file/DB path is instrumented, not local.

### Navigation

Documented in `ARCHITECTURE.md` §7. `MiraDestination.kt` is the contract; nothing else decides
what exists.

- [x] `MiraDestination.kt` — route constants, four `MiraTab`s, the `MiraArea`s, `isBuilt` as the single source of truth
- [x] Four tabs with per-tab back stacks; everything else pushed; the bottom bar shown by the shell, never by a screen
- [x] Every area opens from Home's own quick-launch grid. (The separate `AreasScreen` hub and its
      top-bar icon were retired on 16 Oct 2026 — see the changelog — once the grid covered the same ground.)
- [x] `PlannedAreaScreen`, registered from `MiraArea.entries` rather than written out one by one — the governed exception in `AGENTS.md` §11.1 (dormant now that every area is built)
- [x] Onboarding gate: `MiraShellState.Loading` is a real state, so neither path flashes the wrong screen; finishing clears onboarding from the back stack

### Onboarding

Twelve steps, first launch only. The permission decision is recorded in `PRIVACY.md` §8.

- [x] `MiraSettings.onboardingComplete`, `setOnboardingComplete()` and `ONBOARDING_COMPLETE` in the `mira_settings` DataStore
- [x] `OnboardingStep` — the twelve panes as an enum whose declaration order *is* the flow, with the position saved so a rotation or a process death mid-setup does not restart it: Welcome · Language (Auto / বাংলা / English) · Mira Voice · Microphone · Notifications · Calendar · Contacts · Location · Biometric protection · Android Assistant mode · Privacy · Finish
- [x] `RECORD_AUDIO` and `POST_NOTIFICATIONS` requested at onboarding; calendar, contacts and
      location are **explained there and never requested there**. All three are now declared and
      consumed, so each is asked for by the screen that needs it — location from Settings → Places
      (`PRIVACY.md` §8)
- [x] "Skip for now" is the same move as "next" — an optional step never blocks the flow, and a denial is a state rather than an error
- [x] `OnboardingScreen` — the pane host `MiraApp` navigates to; panes, view model and permission plumbing all on disk and driven on device
- [x] Clean-install pass on the device (Pixel 6 AVD, API 37): `adb uninstall` → install → all twelve steps → Home → cold relaunch shows Home, not onboarding. Run twice: once granting microphone and notifications, once skipping both. Choosing বাংলা at step 2 put Home into Bengali.

### Screens

- [x] **Home** — daily brief, counts, next reminder, entry to Mira
- [x] **Mira** — conversation, voice and text input, confirmation prompts
- [x] **Tasks** — list, filters, create, complete, delete
- [x] **Vault** — summary list, add, reveal behind biometric, delete
- [x] **Settings** — language mode, spoken responses, activation sound, haptics, Gemini key, clear transcript
- [x] **Voice Setup** — engine and voice discovery, audition, rate and pitch, per-language selection

### Language

- [x] `ResponseRenderer` contract — separate `speak` and `display` wording
- [x] `MiraResponseRenderer` with English and Bengali phrasebooks
- [x] Renderer tests for both languages
- [x] Bengali script input understood
- [x] Romanized Bengali (Banglish) understood
- [x] Mixed Bengali-English in one utterance understood
- [x] **Home** renders entirely in Abu's language — greeting, date, the glance line, section
      headers, the focus cards and the overdue prefix (`HomeWording`)
- [x] **Tasks** renders entirely in Abu's language — tabs, search placeholder, filter chips, empty
      states, composer sheet, notices and the overdue prefix (`TasksWording`)
- [x] `TaskTimeFormatter.dueLabel` puts the overdue word in the same language as the moment beside
      it. The defect it fixes — an English "Overdue — " glued onto "আগামীকাল, সকাল ১০টা" — was seen
      on device before the fix and is covered by a regression test on both Home and Tasks
- [x] **Universal Search** (now the `SearchOverlay`, see below) renders entirely in Abu's language —
      search placeholder, the empty state, every section heading with its hit count, the results total,
      the invoice and expense result lines and the conversation speaker (`UniversalSearchWording`).
      Counts and money figures go through `bengaliNumerals`; a foreign currency and an invoice number
      stay verbatim
- [x] **Invoices**, **Money**, **Memory** and the **Scanner** are now fully bilingual (15 Oct 2026
      audit remediation): each gained its own `*Wording` phrasebook (English + Bengali) resolved from
      the stored language via `displayLanguage()`, every chrome string / status badge / empty state /
      dialog worded, dates and amounts rendered in the chosen language through `bengaliNumerals`, and
      VM notices turned into language-free sealed types worded at the view model. These four were the
      finance/memory surfaces the audit found hard-coding English on a built, reachable path. Verified
      on device: the Money screen reads fully in Bengali, amounts as ৳ with Bengali numerals.
- [x] **Finance confirmation read-backs now state the money** (15 Oct 2026 audit fix): an invoice
      draft held for a yes reads "Draft an invoice for <client> — <amount>" instead of a client-search
      result that hid the amount, and a percentage payment reads "Record <n>% of <invoice>" instead of
      "৳0.00" (the real figure is only worked out on confirm). Two preview-only responses,
      `InvoiceDraftRequested` / `PaymentByPercentageRequested`, rendered EN + BN with renderer tests.
- [x] **Every direct wall-clock read the audit named is now the injected `MiraClock`** (15 Oct 2026,
      §4 rule 4): `AutomationEngine.GenerateEveningReview`, `ScannerViewModel`'s expense date,
      `MiraNotifications.isQuietHoursNow()` and the Money Add-budget/Add-subscription dialogs no longer
      call `LocalDate.now()` / `LocalTime.now()` directly, so the time they drive stays testable.
- [ ] **`ScanUseCases.kt` still builds English human wording in the domain layer** (§4.6): the
      business-card note title and field labels, the "Scanned Document"/"Scanned Note" fallbacks and
      the "A client name or company is required" message are assembled in `domain/usecase/` and shown
      to Abu, so the otherwise-bilingual Scanner still surfaces some English from them. They need
      lifting out of the domain (through `ResponseRenderer` or passed-in localized text). Found during
      the 15 Oct 2026 bilingual retrofit; out of that change's screen-scoped remit.
- [ ] **Mira**, **Vault**, **Settings**, **Voice Setup**, the **Areas** hub, the **planned-area**
      screens and the whole of **onboarding** are still English-only chrome. Mira's own answers are
      already bilingual through `ResponseRenderer`; this is the surrounding UI text. Needs the same
      phrasebook treatment, and `MiraArea`'s titles, summaries and capabilities need a home that
      can hold two languages
- [x] Per-utterance language detection under `LanguageMode.AUTO`

### Voice

The eight seams are `SpeechRecognitionManager`, `MiraTextToSpeech`, `VoiceResponseFormatter`,
`VoicePersonaManager`, `MiraAudioFocusManager`, `MiraEarconPlayer`, `VoiceConversationController`
and `VoicePreferenceRepository`. Each has exactly one implementation and one Hilt binding, proved
on the device by `MiraDependencyGraphTest`.

- [x] `TtsVoiceInfo` carries no gender field, and no voice name is parsed anywhere for one. Abu
      chooses by ear; the Voice Setup screen is headed "Choose by ear" and labels each row with
      what the engine actually reports — locale, quality, latency, network, not-yet-downloaded
- [x] `VoicePreferenceRepository` — engine, English voice id, Bengali voice id, rate, pitch,
      spoken responses, setup complete. The DataStore keys are the ones `SettingsRepository` used
      before the move, so Abu's existing choice survived the change
- [x] **The stored voice is used automatically.** Verified on device: a Bengali voice chosen in
      setup was the voice that spoke the next Bengali reply (`bn-in-x-bnc-local`)
- [x] `VoiceResponseFormatter` — a Bengali sentence carrying English words stays in one voice. A
      run of the other script is broken out only when it is worth the switch, so Mira never trades
      voices word by word
- [x] On-device `SpeechRecognizer`, preferred over the networked one, with level metering
- [x] Optional recognizer features discovered at runtime, never assumed: on-device models,
      language detection and language switching. Under AUTO the expected languages are held to
      bn-BD and en-US **where the platform carries the request**; where it does not, Mira listens
      anyway. A missing optional extra never disables the microphone
- [x] Every documented `SpeechRecognizer.ERROR_*` mapped to a `SpeechFailure`, each rendered in
      both languages, with **Retry offered only when the failure can actually succeed again**
- [x] Audio focus: listening takes `GAIN_TRANSIENT_EXCLUSIVE` so music pauses rather than ducks
      under an open microphone; speaking ducks. Focus is abandoned in a `finally`, including on
      cancellation. Replies to typed lines take focus too — they did not, and read over whatever
      Abu was already listening to
- [x] Losing the audio to a call stops her mid-sentence and ends the turn **silently** — no
      completion tone into the first second of the call
- [x] Synthesised activation, completion and error tones, original by construction: they are
      arithmetic in `MiraTones`, there is no audio file in this project. The buffers are handed
      back on `TRIM_MEMORY_UI_HIDDEN` and rebuilt on next use
- [x] `MiraAvatar` — Mira's mascot, drawn in code — draws all seven states: idle, listening,
      processing, executing, speaking, success, error — and all seven are reachable. `EXECUTING`
      is real because `ExecuteCommandUseCase` reports the moment interpreting ends and writing begins
- [x] **The mascot is the listening visual everywhere.** It replaced the waveform as the voice
      presence on the Mira screen hero and on Home, and already led the floating overlay; one
      `MiraVoiceState.toAvatarState()` mapping keeps the face consistent across all three. The
      waveform now survives only as the small amplitude meter beside the status banner's Stop
      button, which is a level readout rather than her presence
- [x] **Interruption.** Pressing the orb while Mira speaks stops her and does *not* start
      listening; the next press listens. It holds for replies to typed lines too, and a press that
      would only stop her never asks Android for the microphone first. Verified on device
- [x] **The confirmation gate reaches the spoken path.** A spoken mutating command that needs
      confirmation is asked about and held — no completion tone, no success flare, and the orb
      settles to idle rather than claiming the command ran
- [x] **Conversation history reads back.** The history sheet lists past threads and searches every
      message; tapping a thread — or a search hit — now opens a read-only detail of that exchange
      with a back control, driven by `ConversationThreadRepository.observeMessages`. It was a
      browse-only dead-end before (a thread could be deleted but not opened). Verified on the
      emulator: list → thread → messages → back. Resuming a thread as the live conversation is
      deliberately not offered — that would scope the transcript to a thread, which the two-port
      design avoids
- [ ] **Speaking to her has not been tested.** The emulator has no microphone. Everything above
      about recognition is reasoned from the code, the unit tests and the recognizer's own
      refusals — not from an utterance. This needs a pass on Abu's S24 Ultra
- [ ] Bengali *recognition* unverified: no bn-BD model is installed on the emulator. The failure
      path was exercised instead and degrades correctly ("That language isn't installed for speech
      here. Type it, or say it in the other one." — stated, not retried, dismissible)

### Tasks

The schema moved to **version 3** for this. `tasks` gained description, dueAllDay,
estimatedMinutes, clientId, recurrenceRule, seriesUuid and archivedAtEpochMillis; `tags`,
`task_tags` and `task_attachments` arrived beside it. The migration is generated from Room's
exported DDL and **proved on a device against real rows**: a version-2 task keeps its id, stays
searchable through the rebuilt FTS index, its reminder still points at it, all seven indices
survive the table rebuild, and no `tasks_v2` is left behind.

- [x] **Every field in the brief**: title, description, project, client, priority, status, tags,
      due date, due time, reminder, recurrence, estimated duration, subtasks, notes, attachments,
      createdAt, updatedAt, completedAt
- [x] **`status` is derived, never stored.** Completion, archiving and deletion are three
      independent facts on the row; a fourth column repeating them would be a second source of
      truth that can disagree (§7.12). The editor offers the verbs, not a status dropdown
- [x] **There is no `tasks.reminderId`.** `reminders.linkedTaskId` already owns that relationship,
      and one relationship with two homes drifts
- [x] **Seven views**: Today, Upcoming, Overdue, All, Completed, By Project, By Client. Upcoming
      and Overdue are deliberately disjoint, the same rule the brief's counts follow
- [x] create, edit, complete, reopen, delete, archive, restore, duplicate, snooze, search, filter,
      sort, bulk select, bulk complete
- [x] **Swipe**: right completes, left opens snooze/archive/delete, both labelled as the row moves
      and undoable
- [x] **Recurrence**: daily, weekly (with weekday chips), weekdays, monthly, yearly, every-N-days,
      with an optional end date and a live preview of the next dates. Verified on device: picking
      Mon+Thu from a Friday previewed 25 September · 28 September · 1 October and persisted as
      `freq=WEEKLY;interval=1;days=MONDAY,THURSDAY`
- [x] **The rollover is a new row.** Completing a repeating task closes that occurrence and creates
      the next one carrying the series uuid, with steps reset to undone. Verified on device: one
      completed row and exactly one new occurrence sharing series `421a048d`
- [x] **Completing twice cannot create two occurrences.** The guard is now the row count of a
      conditional `UPDATE ... WHERE isDone = 0`, which is atomic. `setDone` reported one row
      changed even for an already-completed task, so it was never a guard at all
- [x] **A monthly repeat is anchored to the day it starts on.** "Protimashe" arrives from the NLU
      with no day, and a `Monthly` that names none can only take one from the date it counts from —
- [x] **Full task composer & detailed editor connection** (29 Oct 2026):
      - Quick `TaskComposerSheet` now features a direct "Open Full Details" (`words.fullDetails`) affordance that launches `TaskDetailScreen(NEW_TASK_ID)` for full subtask, project, client, recurrence, tags, notes, and estimation management.
      - Reminders Tab `+` action enabled, opening `ReminderComposerSheet` for seamless manual reminder creation with title, date, and time.
      so a single February would have demoted a monthly task to the 28th for ever
- [x] **Natural language reaches the same validated use cases.** All four of the brief's examples
      have tests asserting the whole command. Verified on device through Quick Capture:
      "protidin website check korar task banao" stored `freq=DAILY;interval=1` with a series uuid
- [x] **A task row opens the task.** Tapping used to complete it, which left no way to read one and
      made finishing a task the easiest thing to do by accident. Completion moved to the glyph and
      the swipe, both deliberate
- [ ] **A repeating reminder does not yet repeat.** "Every Monday remind me to review Rayluxeo"
      parses the rule and schedules the first Monday correctly, but `CreateReminder` carries no
      recurrence: re-arming after each firing is scheduler work this change did not do
- [ ] Bulk actions loop the single-task use case per id rather than using the transactional bulk
      writes that exist on the DAO, and a partial failure reports the problem without the count
      that did land
- [ ] `DefaultWorkCommandHandler.updateTask` writes through the repository instead of
      `EditTaskUseCase`, so a spoken edit skips the validation a typed one gets
- [ ] The detail view model writes subtasks through their repository rather than a use case (§4)
- [ ] `TaskComposerSheet` carries its own `@OptIn(ExperimentalMaterial3Api)` although it is set
      project-wide (§7.10), and duplicates the editor's due-date and due-time dialogs

### Home, the daily brief and the evening review

- [x] **Home is an assistant-first canvas, not a dashboard** (redesigned 26 September 2026 to the
      approved prototype). Top to bottom: the date and a settings gear; Mira herself as a band of
      ice-blue light that reads the day aloud when tapped and comes alive while she speaks; a
      centred greeting with one glance line ("One thing needs you, and 3 are due today.", worded by
      `HomeWording.homeGlance`); the one or two focus cards; today's short checklist; and a seek
      ask pinned at the foot. The old grid — the four counts, the outstanding total, invoices due
      soon, project deadlines, recent payments and follow-ups — was removed from Home on this date;
      those facts lived only in the spoken brief and their own areas. Abu reversed that on **16
      October 2026** (see the next item): the at-a-glance cards are back, now rendered as proper
      cards and sections rather than the cramped grid. Sections with nothing in them still collapse.
      Verified on device against the prototype
- [x] **The at-a-glance cards returned to Home** (16 October 2026, on Abu's request "add necessary
      cards"). Below the focus cards and above today's checklist, each present only when it has
      something to say, so an empty ledger shows none of them and Home stays calm:
      - a **day summary card** (`HomeSummaryCard`) — four figures, open / due today / overdue / done
        today, overdue tinted `danger` and done tinted `positive` only when non-zero, a tap opening
        the Tasks screen; it returns the moment any one figure is non-zero (`HomeSummary.hasAnything`)
      - a **money section** — a headline card of outstanding and overdue totals (shown only when there
        is an amount, never a `৳0` line), then the invoices due soon and the payments just in as fact
        rows, the whole section opening the Money area
      - a **deadlines section** and a **follow-ups section** — project deadlines the brief raised and
        clients gone quiet, as quiet fact rows under their own headers
      These read the same `BriefSnapshot` the spoken brief does (`HomeViewModel.buildState` →
      `BriefMoney`/`BriefDeadline`/`BriefFollowUp` → row mappers); nothing is fabricated, money stays
      `Money`/`BigDecimal`, and every number goes through `HomeWording.number`. Both languages
      complete. The fields were already computed into `HomeUiState` but had gone unrendered since the
      26 Sept redesign — this closes that dead data path. Verified on device: the summary card renders
      with a live task count and full, untruncated labels; the money/deadlines/follow-up sections
      stay collapsed on an empty ledger (populating them needs invoice/project data — see the invoice
      note below)
- [ ] **Could not populate the money section on device — invoice creation via the documented phrase
      fails validation.** The Invoices empty state tells Abu to say *"Ahmed er jonno website
      development 45 hazar takar invoice banao"*; typing that exact shape (and an English equivalent)
      on the Mira tab both answered with the generic `MiraErrorCode.VALIDATION` line — "Something was
      missing there — try again with a name and a time" — and created nothing, so the Home money
      section stayed empty for lack of data to show. The `CreateInvoiceDraft` validator only needs a
      non-blank client and a positive amount, and `parseInvoiceDraftSlots` does lift the client from
      the `jonno` phrase, so the utterance is likely matching a different intent (the error wording is
      the reminder/event one) rather than the invoice intent. Root cause unconfirmed; this is in the
      invoice NLU, not the Home cards, and was observed while trying to verify them. The money section
      itself is build- and test-verified and shares its rendering primitives with the proven summary
      card
- [x] **The living aurora backdrop** (`MiraAmbientBackground`, 16 October 2026, on Abu's request for
      a "Gemini app like animated background"). A purple→magenta glow and a halftone dot field,
      drawn entirely in code — no video, no image, identical offline (§3) — breathing low behind the
      content on the shared `withFrameNanos` clock and lifting with the voice through the same
      fast-attack/slow-release envelope the waveform uses (`active`/`amplitude`). It bleeds full-screen
      through a new optional `background` slot on `MiraScaffold`, under the status and nav bars, with
      the hot core kept low so the greeting and the mark stay legible on near-black. It is on **Home**
      (swells while she reads the brief), the **Mira assistant screen** (rides the live mic amplitude)
      and **onboarding** (breathes at rest). This is the one sanctioned departure from §10's "no purple
      / no neon" — recorded in AGENTS.md §10/§11; every control, icon and letter stays ice-blue, and
      the hues live only in `MiraPalette.Aurora*`. Verified on device on all three screens: the glow
      renders, drifts and breathes (consecutive frames differ), and the content above reads clean
- [x] **The nav bar is translucent and content scrolls edge-to-edge behind it** (16 Oct 2026). The
      aurora moved to the shell (`MiraApp`, gated to the Home/Mira tabs) so it fills the whole window
      including under the floating nav pill, which was made see-through (`MiraNavigationBar` scrim
      alphas); the aurora still fades to black at the very bottom edge. The nav bar now **floats** over
      full-height content rather than reserving a `bottomBar`, so a card reads *through* the translucent
      pill instead of being cut off above a bar (Abu's follow-up). Its measured height is handed to
      every screen via `LocalMiraBottomInset`, which each `MiraScaffold` folds into its content's
      bottom padding — and is 0 on a bar-less pushed screen, so those pages do not keep a dead band at
      the foot (the cut-off Abu then saw on bar-less pages). Home and the Mira screen take
      `MiraScaffold(transparent = true)`. Verified on device (cards show behind the pill; Areas hub and
      other pushed pages fill to the bottom).
- [x] **Search is an overlay, not a page** (16 Oct 2026, Abu's request). Tapping the search circle on
      Home no longer pushes a screen: the shell blurs the whole app (`Modifier.blur`, aurora and nav
      pill included) and a `SearchOverlay` blooms the field out of the circle's own bounds to full
      width, both driven by one `updateTransition` progress so blur and bloom move as one. The field
      morphs from the circle (a `CircleShape` container growing wide), its content fading up over the
      back half; results reuse the old universal-search rendering (moved into `SearchOverlay`, the
      `UniversalSearchScreen` page and its route deleted). Back or a tap outside blooms it shut. The
      `UniversalSearchViewModel`/`Wording` are unchanged. Verified on device (open, blur, query,
      empty-state, dismiss).
- [x] **Scrolling filter/tab rows show their overflow** (16 Oct 2026, Abu's report that "only the
      first three are visible and nobody will understand there are more"). A new design-system modifier
      `Modifier.fadingHorizontalEdges` softly fades a single-line chip/tab row at whichever end can
      still scroll. Applied to the Money tabs (6, only 3 fit) and the Tasks/Notes/Files/Reports/
      Activity/Memory/Scanner filter rows. Verified on device (Money: right-edge fade at rest, left-edge
      fade once scrolled, no fade at the end).
- [x] **Home reordered; Salah leads the day's cards** (16 Oct 2026, Abu's request). The areas grid
      moved from under the greeting to the **foot** (the bottom nav already carries primary navigation,
      so glanceable content leads); the day's cards now run Salah → the day's four numbers → Today →
      Money → deadlines → follow-ups, with the Salah card raised to lead because the next prayer is the
      most time-bound thing on the screen. The Home skeleton was reshaped to match (cards leading, grid
      at the foot). Verified on device (Salah renders only when a prayer location is set).
- [x] **5 Waqt Authentic Hadith & Al-Quran with 2-Page Daily Recitation Target** (29 Oct 2026, Abu's request):
      - **Hadith per Waqt**: Authentic Hadiths for each waqt (Fajr, Dhuhr, Asr, Maghrib, Isha) in classical Arabic with full vocalization (harakat) and contextual Bengali translation. Integrated into waqt prayer notifications and displayed dynamically on the Home screen (`HomeHadithCard` / `HomeSalahCard`) and `SalahScreen`.
      - **Al-Quran Recitation Tracker**: Complete 604-page / 30-Juz offline Quran with Arabic script, Bengali pronunciation, and Bengali translation.
      - **Daily 2-Page Goal**: Track daily recitation progress with minimum 2-page target, persistent page bookmarking across restarts via local DataStore settings (zero schema change). Dedicated Quran reader (`MiraRoutes.QURAN`) and `HomeQuranCard` on the Home screen.
- [x] **The Projects area is built** (16 Oct 2026, Abu's request — "projects is remaining in areas").
      The last `PlannedAreaScreen` is gone: `MiraArea.PROJECTS` is now `isBuilt = true`, with a real
      `ProjectsScreen` (list + `ProjectFilter` chips + search + create/edit/detail sheets + soft-delete
      and undo), five write use cases (§4), a bilingual `ProjectsWording`, and tests. The domain/data
      layer already existed; this was the UI + use cases + nav flip. Full breakdown under "Projects"
      below. Device-verified end to end (create, status change across filters, delete + restore).
- [x] **The Mira voice screen lost its redundant status banner** (16 Oct 2026, Abu — "i don't like
      this at all"). The floating `MiraVoiceStatusBanner` above the composer duplicated the hero
      (which already shows the avatar, the live state and the amplitude) and carried a stop control
      mislabelled "No" (`wording.confirmNo` reused as a stop label). It is gone; the live partial
      transcript it uniquely held now shows in the hero's own status line (the way Home does it), the
      listening state is the hero + the voice button's halo, and stop is the voice button (which turns
      into a Stop). Verified on device (idle and listening).
- [x] **The top-bar Areas icon is gone; the Areas hub is retired** (16 Oct 2026, Abu — "if all areas
      functions are in homepage then this [icon] of no use"). Home's own areas grid already gives one
      tap to every area, so the second door — the top-bar grid icon opening `AreasScreen`, a hub
      listing the same areas — was redundant. Removed the icon, the `AreasScreen` + its `MiraRoutes.AREAS`
      route, the `onOpenAreas` plumbing through Home, and the five hub-only `ShellWording` lines
      (`areasTitle`/`workingSection`/`plannedSection`/`plannedSectionNote`/`plannedMarker`) in both
      phrasebooks and their test. `PlannedAreaScreen` stays — it is the mechanism for any future
      unbuilt area, kept alive by the enum-driven loop (empty now that every area is built). The top
      bar is Search + Settings; areas open from Home's grid. Verified on device (Alarms opens from the
      grid; no dead route).
- [x] **Skeleton loading instead of spinners** (16 Oct 2026, Abu's request). A new design-system
      family — `MiraSkeleton`/`MiraListSkeleton`/`rememberMiraShimmer` — shows a shimmering
      placeholder shaped like the content that is coming. Home has a bespoke skeleton (date, avatar,
      greeting, two leading cards, then the areas grid at the foot — matching the reordered screen);
      the list/content screens use `MiraListSkeleton` (Tasks, Notes, Alarms,
      Timers, Salah, Salah-month, Reports, Activity, Settings, Places, Assistant-setup, Voice-setup,
      Vault, Task-detail, Note-detail). Action-in-progress spinners (the mic turn, search, PDF
      generation) stay `MiraLoadingIndicator`. Verified on device (Home).
- [x] **Manual invoice creation** (16 Oct 2026, Abu's request). A `+` on the Invoices screen opens
      a form (client picker, amount, description, due date) that raises a real draft through the
      existing `CreateInvoiceDraftUseCase` — no second copy of the invoice logic, money stays
      `BigDecimal` in the client's currency (§8), the due date defaults to +14 days via `MiraClock`
      (§4). The empty-state copy was corrected (it had instructed an impossible fresh-install voice
      flow). Verified on device: INV-2026-001, ৳45,000, created and listed.
- [x] **The invoice document (PDF) was redesigned and made bilingual** (16 Oct 2026). Premium
      editorial letterhead in the bundled **Manrope** typeface with champagne accents (studio
      wordmark, gold INVOICE mark, item table, a gold balance-due plate, a thank-you line). Labels,
      the closing line and all digits follow the app language (Bengali draws in the bundled Noto
      Serif Bengali), fixing the English-only PDF. Verified on device (English).
  - [x] **Abu's own "Sayed Ahmed" wordmark is now the letterhead** (16 Oct 2026). Collected from his
        site, recoloured from its near-white export to a print ink that reads on white, trimmed and
        bundled at `res/drawable-nodpi/invoice_logo.png` (his own IP, zero cost, decoded once and
        reused, §3). `drawHeader` draws it top-left in place of the typeset business name, falling
        back to that name if the asset cannot be decoded, so the header is never blank. Verified on
        device: the logo renders crisp and legible beside the champagne INVOICE mark. A champagne
        variant is prepared should a gold-foil letterhead be preferred over ink.
- [x] **The voice invoice command now reports the right thing** (16 Oct 2026). It always worked, but
      when the named client was not on file `createInvoiceDraft` returned VALIDATION (rendered as the
      nonsensical "…name and a time") instead of NOT_FOUND, like `queryInvoice`/`deliverInvoice`
      already do — which is why the documented empty-state phrase looked broken on a fresh install.
- [x] **Static-audit fixes (16 Oct 2026):** removed the fake "Refresh rates" control that reported a
      success it never did (ExchangeRateRepository offline rates are manual by design); the Meetings
      save now surfaces its failure instead of swallowing it; the Add-Meeting sheet can set a date
      and time (reusing the task pickers) rather than hardcoding `now`; `AreaLockGate` re-locks on
      background; Home surfaces `calendarUnreadable` as an inline notice; the nav bar and the invoice
      screens use `ContinuousRoundedCornerShape`; `ClientDao.countInvoices/countQuotes` carry the
      soft-delete-exception KDoc; and §10's colour table was corrected to the implemented ice-blue.
- [x] **Invoice email/WhatsApp delivery is bilingual** (16 Oct 2026). The subject and body were
      built in English in the domain use case; they now come from `InvoiceMessageComposer`, a domain
      PORT whose implementation (`InvoiceDeliveryMessages`) lives in the UI layer — so the wording is
      out of `domain` (§4.6) the way the NLU/date ports are, bound in `AppBindingsModule`. The UI
      delivery path passes Abu's language; the language-agnostic command handler lets it default to
      English. Bengali uses Bengali numerals. Build-verified (delivery system tests updated).
- [ ] **Audit items still open (next pass):** **Activity-log** entries and the **recurring-invoice**
      reminder title persist rendered English sentences in the domain rather than language-free
      records — both need the same structured-record + word-at-display refactor (the Reminder/Activity
      models), too large to land safely in one sitting (§1/§4.6). Also low-severity: the report
      CSV/PDF "Total"/"Count" headline label, and a handful of raw `.dp` literals in two invoice
      dialogs. And a systematic type-scale/padding sweep across every screen (the forms added this
      session are already generously spaced).
- [x] **Focus cards**, driven by `DailyBriefEngine.attention()`, one calm card per row with no
      header over them — the danger glyph and the words carry the weight. Verified on device against
      a fixture: "Call Ahmed is 3 days overdue", severity carried by the words as well as the
      colour, and every card opens the record it names
- [x] **Today's checklist** — the next diary entry, then the day's tasks as rows with a real round
      checkbox that ticks the task off (confirm haptic, danger ring when overdue), the words opening
      the full list; reminders follow as stated lines. Verified on device
- [x] Every window in `AttentionRules` is a named constant with its reason; the sort is total, so
      the card does not reshuffle between emissions; the list is capped, and per-kind capped so a
      week of overdue tasks cannot hide the money. Nothing is ever fabricated to fill the card
- [x] **DRAFT invoices are excluded** from the card. `observeUnpaid` counts them as owed, which is
      right for a ledger total and wrong here — "Ahmed's invoice is overdue" about a bill Abu
      never sent would send him chasing his own paperwork
- [x] **Read my brief** speaks through the existing voice layer, from a tap on Mira's face. Verified
      on device
- [x] **Home's in-place Ask (Quick Capture + the round voice button) was removed on Abu's request**
      (15 October 2026) for a calmer Home. Asking Mira — typed or spoken — now lives on the Mira tab
      in the bottom bar, which owns the full composer and the voice turn. Home keeps the avatar (a
      tap reads the brief, and her face still mirrors any process-wide voice turn through
      `VoiceConversationController`); a card action that needs a word back, such as "Remind me" on an
      attention row, reports through Home's single top notice. Verified on device: Home renders with
      no command button, no dead handlers, and the feedback banner still appears
- [x] **DailyBriefEngine** and **EveningReviewEngine**, pure domain over repository ports, working
      entirely on the device. Gemini is never consulted
- [x] **`calendarReadable` is beside the events in both reports.** A withheld permission is an
      absence, not an empty day: `isQuiet` requires a readable calendar, and the spoken brief says
      she could not look rather than going silent. Telling Abu his day is clear when Mira was never
      allowed to look is the worst thing this feature could do
- [x] Money is `Money`/`BigDecimal` throughout — no `Double` anywhere in `domain/brief`. A total is
      **null when the invoices are in different currencies**, meaning "cannot be given as one
      figure", rather than inventing a rate. Verified on device: BDT 1,65,000 outstanding across
      two invoices, in South Asian lakh grouping
- [x] **Optional morning brief and evening review**, both OFF by default, with the time
      configurable. Verified on device: the alarm arms with `window=+1h`, deliberately **inexact**,
      beside the reminder's `window=+10m` — so a brief keeps working on a phone where exact alarms
      are denied (ADR: `BriefScheduler`)
- [x] The brief re-arms after firing and after a reboot, and its own notification channel is
      separate from reminders so one can be silenced without the other. Verified: "Morning brief /
      Nothing due today. 5 need attention."
- [x] Settings says plainly when notifications are off — "a brief cannot arrive on this phone" —
      with the grant beside it, so the toggle cannot sit there looking enabled while nothing arrives
- [x] Home and the brief are fully bilingual, every number through `bengaliNumerals`. Verified on
      device: "Ahmed-এর ইনভয়েস ১২ দিন ধরে বকেয়া · ৪৫,০০০ টাকা · ১৩ সেপ্টেম্বর"
- [x] The reminders notification channel is now bilingual too. It sat in English beside the new
      bilingual briefs channel on the same system screen
- [ ] `BriefNarrator` words the notification; `HomeWording` words the spoken brief. Two renderings
      of one report, which can drift. They agree today and there is no test pinning that they stay
      agreeing
- [ ] `BriefEvent.allDay` is never true — `CalendarEvent` carries no all-day flag and
      `CalendarRepositoryImpl` does not read `CalendarContract.Instances.ALL_DAY`, so an all-day
      entry reads as a timed one starting at midnight
- [ ] Flow-based repositories swallow storage failures into an empty list, so "a failure is a
      value" holds only for the suspend reads. A Room error on an observed table is indistinguishable
      from no rows

### System assistant

Mira is a real Android assistant: the platform binds her `VoiceInteractionService`, the assist
gesture opens her session, and she answers in her own surfaces. Verified on the Pixel 6 AVD
(API 37) — see the limits at the end.

- [x] `VoiceInteractionService`, `VoiceInteractionSessionService`, `VoiceInteractionSession`,
      declared with `BIND_VOICE_INTERACTION` and `res/xml/voice_interaction_service.xml`
- [x] A **real** `MiraRecognitionService`, required by `android:recognitionService`. Not a stub —
      it delegates to the platform's on-device engine, using `createOnDeviceSpeechRecognizer` so
      it cannot resolve back to itself once Mira is the assistant
- [x] Everything in the **main process**. No `android:process` anywhere (ADR-011)
- [x] Role detection from the platform's own answers: `RoleManager.isRoleHeld` **and**
      `VoiceInteractionService.isActiveService`. The two can disagree — seen on device with Mira
      bound and answering the gesture while the role record said no, which put "Mira is not the
      assistant" directly above "Mira answers the gesture today" on one screen
- [x] The role is **requested by opening Android's own screen**, resolved against `PackageManager`
      first. `createRequestRoleIntent(ROLE_ASSISTANT)` is unsupported and throws on some builds
- [x] **Settings → Mira → Set Mira as my assistant**: live status, the button that opens Android's
      screen, a test that checks four real things, and Samsung's extra steps
- [x] The test button opens a **real session** through the bound service's own `showSession`.
      Firing `ACTION_ASSIST` would have proved nothing — the system resolves it against whoever
      holds the role, not against Mira
- [x] The overlay: compact panel over the app behind it, scrimmed, driven by the same
      `VoiceConversationController` as Mira's own screen — including the confirmation gate, so a
      mutating command never runs from an overlay ungated
- [x] Assist context read only from what the platform hands the assistant; `FLAG_SECURE` and the
      disabled-by-default system settings arrive as an absence, never an error
- [x] Share target "Ask Mira" for text, links, images, screenshots, PDFs and documents, plus
      `ACTION_PROCESS_TEXT` from the selection toolbar. Verified end to end: a shared line became
      a note, logged with `InputSource.SHARED`
- [x] **Shared content never reaches Gemini.** `CommandRouter` refuses the cloud engine for
      `InputSource.SHARED`, with a regression test
- [x] Quick Settings tile, home-screen widget and launcher shortcuts — published from a foreground
      entry point, and their actions actually routed: `MainActivity` reads the entry action and
      the shell navigates to it
- [x] **The Talk entry opens Mira already listening**, not just on the tab. `ACTION_TALK` lands a
      `MiraEntryIntent.StartListening` on the app-scoped `MiraEntryCoordinator`; the Mira screen
      reads it on arrival and starts a turn as if the mark were pressed. Verified on the emulator:
      firing the action switched to Mira and the mic opened with the mascot in its listening face
- [x] **"Ask Mira about this document" carries the question.** Files and the scanner hand their
      prompt to the coordinator as a `MiraEntryIntent.Ask`, which lands in the composer for Abu to
      read and send, instead of being dropped at the tab switch (§12)
- [x] **No always-on wake word, and the reason is checkable rather than remembered.**
      `AlwaysOnHotwordDetector` is absent from `android-37.0/android.jar` and
      `VoiceInteractionService` exposes no hotword method — confirmed with `javap`.
      `HotwordCapabilityDetector` still asks the device at runtime so a build that ever allowed it
      would be noticed
- [ ] **Not verified on Abu's own phone.** Everything above was exercised on the Pixel 6 AVD. The
      Samsung-specific half — that One UI binds the side button separately, so holding the role is
      not enough to make the button reach Mira — is exactly what an emulator cannot tell us
- [ ] Assist context has no consumer yet: the snapshot is read and reduced to one line about which
      app it came from. "Ask Mira about this screen" needs `OutboundConfirmation` wired to a
      question, and it is not
- [ ] Session continuation ("Hey Mira" while a session is already open) is specified and bounded
      but not exercised on a device with a working recognizer
- [x] **The assistant screen is bilingual**, through an `AssistantWording` phrasebook on the same
      shape as `HomeWording`: 64 members, both languages complete, verified rendering on device in
      বাংলা. It is also the only screen in the app that passes its back and dismiss accessibility
      labels in Abu's language rather than inheriting `MiraTopBar`'s English default
- [ ] **`SettingsScreen` and `VoiceSetupScreen` are still English-only** — about 52 and 30 literals.
      `SettingsScreen` is the screen that *opens* the bilingual one, so the seam is visible in one
      tap. `MiraTopBar.backContentDescription` still defaults to the English "Back" for every other
      caller
- [ ] A notice raised on the assistant screen stores its rendered text, so it keeps the language it
      was raised in if Abu switches mid-screen. `VoiceSetupViewModel` has the same shape; fixing it
      means making the notice a code rendered at draw time
- [ ] The `NEW_TASK` shortcut lands on Tasks but does not open the composer, which its own KDoc
      promises. The route works; the last step does not

### Reminders

- [x] AlarmManager-backed exact alarms via `ReminderScheduler`
- [x] `canScheduleExact()` checked before every schedule, with an honest fallback message when exact alarms are unavailable
- [x] `BootReceiver` calling `rescheduleAll()` on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`
- [x] Reminder notification through `MiraNotifications` — channel created idempotently, a missing `POST_NOTIFICATIONS` grant drops the post silently rather than throwing
- [x] Runtime `POST_NOTIFICATIONS` request — requested at onboarding step 5, verified granting and skipping on device (`PRIVACY.md` §8)
- [x] Nine reminder kinds, reminder recurrence, snoozes and five record links (schema v4)
- [x] Alarm clock: time of day + repeat days + label + enable/disable + snooze, stored as a
      minute of the day so a repeating alarm does not move when Abu crosses a time zone
- [x] Timers — multiple at once, pause, resume, cancel; the countdown is read from the wall clock
      rather than decremented, so it survives a process death
- [x] Five notification actions — Complete, Snooze 10 minutes, Snooze 1 hour, Open, Ask Mira
- [x] WorkManager for the deferrable daily sweep; exact `AlarmManager` only for a moment Abu named
- [x] **Place reminders**, end to end: `saved_places` (schema v5), Settings → Places, and the
      spoken/typed path. Verified on device 25 September 2026 with the brief's own example —
      "Remind me to buy printer ink when I get near the office" wrote a `PLACE` reminder with its
      own copy of the coordinate. No geocoder, no map, no new dependency: `LocationManager`
      proximity alerts only
- [x] An unarmed place reminder SAYS it is unarmed. Watching depends on a permission Android
      grants only from settings, so Mira stores the reminder and tells Abu plainly that nothing is
      listening, rather than letting it look armed
- [x] A refused exact alarm is kept and arms itself when the permission returns; Mira says so
      rather than telling Abu to ask again, which would give him a second alarm
- [ ] `ReminderKind.BIRTHDAY` has wording and an icon but **no creation path**. Clients carry no
      birth date and `MiraArea.CLIENTS` is `isBuilt = false`, so there is nowhere to enter one. It
      becomes reachable with the Clients area, not before
- [ ] Gemini cannot propose a place reminder or an alarm. Its command vocabulary is deliberately
      six verbs wide and it never sees Room, so it could not resolve a place name anyway; the
      local engine owns both (`AGENTS.md` §3 — Gemini is never required)

### Salah (prayer) — notifications & exact-alarm scheduling

- [x] `PrayerScheduler` implemented (`PrayerSchedulerImpl`) over exact `AlarmManager`, no
      WorkManager: it recomputes today's times for the saved place with `PrayerTimeCalculator`
      (pure on-device astronomy, works offline) and arms every alarm Abu has enabled
- [x] Times computed in the location's **saved** zone, not the device's, so a traveller still
      prays by the place he set; the daily rollover falls at that place's own midnight
- [x] Per obligatory prayer: a prayer-time alarm when `notifyFor(prayer)` and it is still ahead,
      and a check-in alarm at `prayer time + defaultAskDelayMinutes` when `checkInFor(prayer)`;
      a passed or switched-off prayer has its own alarm cancelled, never left stale
- [x] Tahajjud alarm from the computed night window — last third start, or +15/+30/custom per
      `TahajjudReminderMode`, always pulled back to a minute before next Fajr and never before the
      last third begins (pure `TahajjudTrigger`, unit-tested)
- [x] A daily **rollover** exact alarm at the start of tomorrow re-runs `reschedule()`, so each day
      re-arms without the app being opened; `BootReceiver.reschedule()` replays it all after a
      reboot, a package replacement or a timezone change
- [x] Fixed request codes per (kind, prayer) in `PrayerCodes` so a re-arm **replaces** rather than
      duplicates; all target `PrayerReceiver`, a component no other scheduler uses
- [x] Exact→inexact window fallback exactly like `ReminderSchedulerImpl` when exact alarms are
      refused
- [x] Three bilingual channels — `mira_prayer_times` (HIGH, vibration), `mira_prayer_checkin`
      (DEFAULT), `mira_tahajjud` (HIGH, alarm sound via the channel so it can wake Abu). Channel
      names/descriptions and all copy are EN/Bengali through `SalahNotificationWording`
- [x] Check-in carries three actions — **Yes** (logs PRAYED), **Not yet** (logs NOT_YET, never
      MISSED), **Remind me** (re-arms the check-in `defaultAskDelayMinutes` on) — through
      `PrayerActionReceiver`, logging via `TrackPrayerUseCase` with `source = CHECK_IN`
- [x] Never judgemental copy: "Did you pray Asr?" / "আসর নামাজ পড়েছেন?", never an accusation
- [x] Disjoint notification-ID band (−3,000,000; check-in folds in the day so an unanswered ask is
      not replaced by the next day's), clear of reminders, briefs, timers and the alarm clock
- [x] `PrayerScheduler` bound in `data/di/SalahSchedulerModule.kt`; `SalahNotificationWording`
      bound there to its `ui/response` impl
- [x] Salah UI — the main screen, the month view, the settings surface and first-time location
      setup — now built (see the **screens & tracking** subsection below); `UpdateSalahSettingsUseCase`
      / `SaveCurrentLocationUseCase` call `reschedule()`, so every settings or location change re-arms
      the alarms from the UI
- [x] Surfaced on **Home**, the **morning brief** and the **evening review**: a live Salah card on
      Home (next prayer name and time, a wall-clock countdown off a `WhileSubscribed` one-second
      ticker, a progress indicator and the five dots) that appears only when a prayer location is
      set — never a fake or dead card; a one-line next-prayer sentence in the daily brief
      (`BriefSalah` → `BriefNarrator`); and a "Salah: n of 5 recorded as prayed" line in the evening
      review (`ReviewSalah`). All EN/Bengali, never judgemental (an unlogged prayer is "not yet
      logged", never missed). `HomeScreen`'s `onOpenSalah` lambda is wired to the Salah route in
      `MiraApp`; the Home card, the areas hub and Settings all open it
- [x] **The home-screen glance now binds live data** (16 Oct 2026). The old defect — glance lines
      that were static placeholders ("3 tasks remaining today", "Design Sync @ 11:00") — is gone two
      ways: the existing "Mira" widget was de-faked to an honest Quick-Capture launcher (buttons
      only, no invented numbers), and a new **"Mira — Today"** glance widget was added that reads the
      real brief. See **System surfaces → Home-screen widgets** below for the full note. (A "Next
      Salah" quick-settings tile is still deferred: it needs a new deep-link action the shell must
      answer. Noted for a later pass.)

### Salah (prayer) — screens & tracking

- [x] Reached from the areas hub (`MiraArea.SALAH`, `isBuilt = true`), the Home Salah card and
      Settings → MIRA → Salah; three routes in `MiraApp` — `SALAH`, `SALAH_MONTH`, `SALAH_SETTINGS`
- [x] **Main screen** (`SalahScreen`): a header carrying the Hijri date and the saved place; a hero
      with the next prayer and a live wall-clock countdown off a one-second `WhileSubscribed` ticker;
      the five obligatory prayers as tracking rows; and a **separate Tahajjud card** that is never
      counted among the five. An empty, un-located state offers first-run setup rather than a dead
      screen
- [x] **Tracking interaction**: one tap on a prayer's circle records it as prayed; a long press opens
      a correction sheet (Prayed, Prayed late, Not yet, Missed, Not recorded). Tahajjud has its own
      one-tap mark and its own status sheet (Prayed / Not prayed). Everything writes through
      `TrackPrayerUseCase` / `TrackTahajjudUseCase` with `source = MANUAL`; a time that has not yet
      arrived reads as "not yet", never as missed
- [x] **Month view** (`SalahMonthScreen`): a weekday-aligned heatmap of the five prayers for every
      day of the month (Tahajjud marked as a thin champagne underline, not a sixth dot), a summary
      panel (percentage, completed-of-possible, per-prayer counts, days all five, current and best
      streak, missed), and a **separate Tahajjud panel** (nights, current streak). Figures come from
      `GetMonthlySalahUseCase` / `SalahStatistics`
- [x] **Settings surface** (`SalahSettingsScreen`): saved location with a name and a one-shot
      "use current location" fix, first-run location setup, calculation method and Asr madhab,
      custom angles, high-latitude rule, per-prayer manual adjustments, prayer-notification and
      check-in toggles with a follow-up delay, and the Tahajjud reminder with its mode and custom
      offset. Every change persists through `UpdateSalahSettingsUseCase` and re-arms the alarms. All
      copy is EN/Bengali through `SalahSettingsWording`; long selected values (the calculation method
      especially) show a concise label in the row and the full name in the selection sheet, via
      `MiraListRow`'s weighted `trailingText` so the title is never crushed

### Vault

- [x] AES-256-GCM encryption with an Android Keystore key, fresh IV per write
- [x] `observeSummaries()` never decrypts; only `reveal(id)` does
- [x] Platform `BiometricPrompt` gate on reveal, `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (ADR-001)
- [ ] Vault key bound to the prompt with a `CryptoObject` — the key is created with `setUserAuthenticationRequired(false)` today, so the prompt gates the *screen* and not the *key* (`PRIVACY.md` §2)
- [ ] UI copy warning that a vault **title is not a secret** (`PRIVACY.md` §3)

### Tests

- [x] Response renderer tests, English and Bengali
- [x] Dependency-graph instrumentation test — every injected seam, run on device
- [x] Four-phrasing acceptance test: English, Bengali script, Banglish and mixed all yield one
      identical `CreateReminder` above the confirmation threshold
- [x] Validator tests — every rule in `DefaultCommandValidator`, plus the `isMutating` map
- [x] Handler tests — all five area handlers (`Work`, `Knowledge`, `Business`, `Money`,
      `Phone`) against hand-written fakes, which is where the behaviour actually lives;
      `DefaultCommandDispatcher` itself is a one-line router per command and the exhaustive
      `when` is what guarantees none is missing
- [x] Local engine tests — the 23 intents matched across English, Bengali script, Banglish
      and mixed input, with the number, currency and date-time parsers covered separately.
      **No Gemini anywhere in the test suite**; the local engine is tested on its own terms
- [x] Router tests — local hit, local miss + Gemini hit, local miss + Gemini miss, Gemini unavailable, Gemini failing mid-flight
- [ ] Confirmation tests — mutating/non-mutating × confident/uncertain × local/Gemini (partially covered today by `MiraViewModelTest`)
- [x] Normalizer and date-time parser tests, including Banglish and mixed input
- [x] Calculator tests — precision, rounding, malformed expressions, percent forms
- [x] Room migration tests via `MigrationTestHelper` — 7 instrumented tests covering an empty
      version 1, a task surviving the table rebuild with its id, full-text search still working
      afterwards, conversation turns moving into the new two-table shape, that move staying
      searchable, and the vault untouched. They caught three real defects before the change
      shipped: missing indices after the rebuild, an empty conversation invented on a database
      that had never been spoken to, and the migrated transcript being permanently unsearchable
- [x] Real upgrade verified on device: a hand-built version 1 database with tasks, a reminder,
      a vault item and Bengali conversation turns was pushed to the emulator, and the app opened
      it, migrated it and showed the migrated rows

> Reminder: **no Hilt under `app/src/test`** (ADR-007, google/dagger#5261). Construct classes
> directly with hand-written fakes.

---

## Definition of done for v0.1

v0.1 ships when every line below is true on the S24 Ultra, not in principle.

- [ ] All twenty-three commands work end to end by voice and by text, in English and in Bengali
- [ ] Every route is reachable, has a way back, and has no dead controls (`AGENTS.md` §11) — the nine planned areas under the §11.1 exception and nothing else
- [x] First run from a clean install completes all twelve onboarding steps and never shows them again
- [ ] A reminder set today fires at the right minute, and still fires after a reboot
- [ ] A vault secret survives a reveal → app kill → reveal cycle, behind biometrics
- [ ] The app is fully usable in aeroplane mode with no Gemini key
- [ ] Logcat is clean through a full pass over every screen
- [x] `:app:testDebugUnitTest` is green — 1297 local tests across 99 files, plus the
      instrumented suite (dependency graph and 7 migration tests)
- [ ] Permissions and `<queries>` are declared, requested from a visible screen, and denial is handled

---

## The nine declared areas

These nine are **declared but not built**: each is a `MiraArea` constant with `isBuilt = false`,
each has a real route, and each renders `PlannedAreaScreen` — a screen that names the area, says
it is not built, lists what it will do, and hands the request to Mira. That is the governed
exception in `AGENTS.md` §11.1 and it ends for an area the moment `isBuilt` flips.

No table, no command, no stub exists behind any of them. Each item that reaches implementation
needs its own entry in `ARCHITECTURE.md`, `DATA_MODEL.md` and — if it touches data or
permissions — `PRIVACY.md`.

**The capabilities under each heading are the lines `MiraArea` shows Abu on the screen itself.**
They are repeated here so the two cannot drift; if you change one, change both.

### Calendar — `MiraRoutes.CALENDAR`

*Abu's agenda, read from the phone's own calendars.*

- See today and the week ahead without leaving Mira
- Create an event by voice, in Bengali or English
- Have the daily brief mention what is actually scheduled

- [ ] Events: create, move, cancel, query
- [ ] Agenda view and day/week surfaces
- [ ] Conflict detection and free-slot suggestion
- [ ] Device calendar integration — needs `READ_CALENDAR` / `WRITE_CALENDAR`, neither declared nor requested until this ships (`PRIVACY.md` §8)
- [ ] Recurring events

### Notes — `MiraRoutes.NOTES`

*Anything worth writing down, searchable the moment it is saved.*

- Dictate a note in Bengali, English or Banglish
- Full-text search across every note
- Pull a note into a task or a reminder

- [x] Freeform notes — title, body, pin, archive, tags, client / project / task links,
      attachments, voice note, handwriting, created and updated dates
- [x] Voice-dictated notes (`AppCommand.CreateNote`, and `voice_notes` carrying its transcript)
- [x] Search across notes — FTS4 over title and body, with the archive and the bin filtered on the
      CONTENT table because the index shadows hidden rows too
- [x] Filters (all, pinned, handwriting, archived, bin, by tag), sort (updated, created, title)
      with pinned always above, and a recent list
- [x] Note → task extraction. The candidate lines are shown with their checkboxes before anything
      is written, and each chosen line goes through the SAME `CreateTaskUseCase` a hand-made task
      does — a task born from a note is indistinguishable from one Abu typed
- [x] Organize — proposes a title, existing tags and an existing client or project, and applies
      NOTHING until he agrees. It never invents a tag or a client: a suggestion he has to correct
      is worse than no suggestion
- [x] **Archive is not the bin.** Two columns, two views; emptying the bin never touches what he
      has filed
- [ ] Extract Text on an ALREADY-SAVED page. Not possible on this platform and not a shell: there
      is no OCR class in `android.jar` that takes a bitmap and returns its text, and the one local
      recogniser reads strokes only as they are written. The action explains that rather than
      failing. See the S Pen section below

### Projects — `MiraRoutes.PROJECTS` — **BUILT** (16 October 2026)

*The work in flight, and what each piece is waiting on.*

The last area still showing `PlannedAreaScreen`; Abu asked for it to be built ("projects is remaining
in areas"). The domain and data layers already existed in full (`Project`, `ProjectMilestone`,
`ProjectRepository` with create/update/setStatus/softDelete/restore/purge and the milestone ops);
this shipped the UI, the write use cases and the nav wiring. `MiraArea.PROJECTS` is now
`isBuilt = true`, which moves it into the hub's working section and the Home grid and out of the
`PlannedAreaScreen` loop; the planned-capability copy was cleared from both shell phrasebooks in the
same change (the enum KDoc requires it).

- [x] **Project records grouping tasks and a client.** A list filtered by `ProjectFilter`
      (Open/Active/Completed/Cancelled/All) with a scrolling chip row, search over name/description,
      and each row showing the name, the resolved client (or "Personal"), the status, and the due
      date/budget when set. First load shows a `MiraListSkeleton`; an empty filter shows `MiraEmptyState`.
- [x] **Create / edit** through a `MiraBottomSheet`: name (required), an optional client picker over
      the live client set, status, notes, start/due dates (reusing `TaskDatePickerDialog`) and an
      optional BDT budget built through `Money` (§8). Both go through **use cases** —
      `CreateProjectUseCase`/`UpdateProjectUseCase` validate the name and stamp `MiraClock` (§4).
- [x] **Detail sheet** mirroring Clients: client, dates, budget and notes when present, a **real
      open-task count** via `TaskRepository.groupedByProject` (one-shot on open — not a faked badge),
      a status picker (`SetProjectStatusUseCase` — the honest "close/archive"), Edit and Delete.
- [x] **Soft delete → undo → restore**, byte-for-byte the Clients pattern
      (`SoftDeleteProjectUseCase`/`RestoreProjectUseCase` + a `MiraInlineNotice(CONFIRM)` with Undo).
- [x] Fully bilingual (`ProjectsWording`, EN + BN, counts and the budget hint through `bengaliNumerals`),
      with a `ProjectsWordingTest` and a `ProjectUseCasesTest` (hand-written fakes, no Hilt — §7.4).
      Verified on device: created "Rayluxeo Website" under client "Rahman Traders", changed its status
      to Completed (it left the Open filter and appeared under Completed), deleted and restored it;
      clean logcat throughout. Unit tests compile but could not be run (the build host is out of RAM —
      the same OOM that blocks the suite this session), so they are build-verified, not executed.
- [ ] **Milestones** — deliberately deferred to a later pass (planned). The repository supports them
      in full, but v1 creates projects with no milestones, so any progress badge would be a permanent
      0/0 — a fake number §12 forbids. A real milestone UI (reorderable, complete toggles, add/edit) is
      a feature in its own right and will land on its own rather than half-built.
- [ ] **Per-project outstanding** — deferred: `InvoiceRepository` exposes by-client/unpaid/overdue but
      no per-project query, so an outstanding figure on a project would need a new query/use case. Not
      faked, not shown, until that lands.

### Clients — `MiraRoutes.CLIENTS`

*Who Abu works with, and the history behind each name.*

- Keep contact details, rates and notes in one place
- See every project, invoice and payment for a client
- Ask Mira for a client by name and get the whole picture

- [ ] Client records: name, contact, notes
- [ ] Linking tasks, projects and invoices to a client
- [ ] Interaction history

### Invoices — `MiraRoutes.INVOICES`

*Quotes and invoices, with the arithmetic done exactly.*

- Build a quote or an invoice from a project's work
- Export a PDF and share it from the phone
- Track what is sent, what is paid and what is overdue

- [x] Invoice records with line items
- [x] Totals, tax and discounts computed on `BigDecimal` — never by a model (ADR-003)
- [x] Paid / unpaid / overdue states
- [x] PDF or share-sheet export
- [x] **Full invoice payment recording and quote workflow** (29 Oct 2026):
      - Manual Quote creation (`NewQuoteSheet`) on Quotes tab via `+` action with client selector, description, amount, and validity date.
      - In-app Payment recording (`RecordPaymentDialog`) supporting multiple payment methods (Bank Transfer, bKash/Mobile Wallet, Cash, Card, Other), transaction reference, and notes.
      - Invoice lifecycle actions: "Mark as Sent", "Record Payment", and soft deletion for draft invoices directly from detail dialog.
      - Instant "Record Payment" quick action icon on Invoice card for any unpaid invoice.

- [x] **Recurring invoices now generate on their own** (16 Oct 2026, part of "richer business tools").
      `ProcessRecurringInvoicesUseCase` existed but had a single caller — the "run now" button on the
      Recurring tab — so a monthly invoice only appeared if Abu opened that screen and tapped it. A new
      daily `RecurringInvoiceWorker` (WorkManager, mirroring `RecordTriggerWorker`/`DerivedReminderWorker`,
      enqueued from `MiraApplication` unique+KEEP) now sweeps due profiles unattended, cloning the source
      invoice to a DRAFT and raising its reminder — it never auto-sends. Device-verified: the worker is
      enqueued in WorkManager (`com.rayluxeo.aida.work.RECURRING_INVOICES`).
- [x] **The dead fake "assistant tools" scaffold was deleted** (16 Oct 2026, §12). `ai/assistant/`
      carried ~24 `AssistantTool`s whose `execute` returned canned data ("Quote created", "Invoice draft
      created", "Expense recorded" — the last two even injecting use cases they never called). The whole
      `AssistantToolRegistry` / `GeminiRepository.selectAndInvokeTool` path had no caller — the live Gemini
      engine maps replies into real `AppCommand`s through the same validated pipeline — but it compiled
      into the release APK, shadowing the real quote/statement features with canned-data namesakes. Removed
      the scaffold, the unused interface method, and the dead `toolRegistry` injections; `ConversationContextManager`
      (the one real class in that package) stays. App runs clean, no DI breakage.

### Money — `MiraRoutes.MONEY`

*Income, expenses and what they add up to — computed, never estimated.*

- Record income and expenses by voice
- See a month at a glance, by category
- Every figure computed on `BigDecimal`, never by a language model

- [ ] Income and expense records
- [ ] Categories and monthly summaries
- [ ] Budgets and alerts
- [ ] Multi-currency, using `Money`'s existing same-currency guard
- [x] Persisted money columns — whole minor units in a `Long` beside the record's currency code,
      which is exact and lets SQL sum and sort it; a decimal string could do neither

### Files & Documents — `MiraRoutes.FILES` & `MiraRoutes.SCANNER`

*Documents Mira can find again, including ones scanned with the camera.*

- Scan a document with the camera and keep it on device
- Read text out of a scan so it becomes searchable
- Attach a file to a client, a project or an invoice

- [x] **Mira Scanner**:
  - Full support for 6 scan types: Receipt, Invoice, Business Card, Document, Handwriting, Screenshot
  - On-device ML Kit Text Recognition (Apache 2.0, zero cost, 100% offline)
  - Receipt flow: camera/import -> crop -> clean image toggle (high contrast) -> OCR -> detect merchant, date, amount -> suggest category -> preview & edit -> user confirmation -> save expense (`RecordExpenseUseCase`)
  - Business Card flow: camera/import -> OCR -> detect name, company, phone, email, website, address -> Create Client, Create Contact, Save Note
  - Document & Invoice flow: camera/import -> OCR -> search extracted text -> link client, link project, link invoice -> Save Document, Save as Note, Ask Mira
  - Handwriting flow: OCR -> **keep original image even if recognition fails or is empty**, **never automatically create financial transactions**, **always show extracted information first** -> Save as Note, Save Document, Ask Mira
- [x] **Files Hub**:
  - Browse all scanned and imported documents
  - Full-text search over display name & OCR extracted text (`FileDao.search` / FTS4)
  - Document detail sheet with client & project linking and "Ask Mira" handoff
  - Soft deletion and file record management

### Automations — `MiraRoutes.AUTOMATIONS`

*Things Mira does on her own, on a schedule or on a trigger.*

- Run a daily brief at a chosen time
- Chain several commands into one phrase
- Trigger on time, on place or on a calendar event

- [x] **Rules fire on their own, and the sheet lets you build them** (16 Oct 2026, increment 1 of the
      "advanced automations" Abu asked for). Until now an automation only ran when Abu tapped Run —
      `AutomationScheduler` existed but had no caller, and the create sheet offered a single free-text
      field with a Manual-only trigger. Now:
  - **APP_START** automations fire on cold start (`MainActivity.onCreate`, process-guarded), and
    **POWER_CONNECTED / POWER_DISCONNECTED** fire from a manifest `AutomationEventReceiver` the OS
    delivers even while Mira is closed — the scheduler's first live callers. Device-verified: an
    app-open rule posted its notification on launch with no manual run.
  - A real **trigger picker** (Manual / app-open / charging / unplugged — only the triggers that
    actually fire; the model's time/place/record triggers are still unarmed and so are not offered,
    §12) and a real **action picker** (run a command / notify / open a link / daily brief) with
    per-action fields, replacing the free-text sheet.
  - Two cross-app reaches through a new domain port `AutomationDeviceGateway` (Android impl in
    `data/automation/`): **Open a link** (`ACTION_VIEW`) and a now-working **Notify** — the engine's
    `Notify` branch had been a no-op that returned success without posting. Both fail as values (a
    link nothing opens, a revoked notification permission) rather than crashing a background run.
  - Bilingual throughout; round-trip + validator tests added. No new permission, no new dependency.
- [x] **Time-of-day and interval triggers now fire on their own** (16 Oct 2026, increment 2). They
      arm real `AlarmManager` alarms the same way reminders do, so "every morning at 8, read my brief"
      and "every 90 minutes, notify me" work with Mira closed:
  - A pure `domain/automation/AutomationNextRun` computes the next fire moment (next 08:00 on an
    allowed weekday, or now + interval, or a fixed future date) against the injected `MiraClock` —
    the one source used both to arm the first alarm and, after one fires, to arm the next.
  - `AutomationAlarmScheduler` (Android impl `AutomationAlarmSchedulerImpl`) arms via
    `setExactAndAllowWhileIdle`, degrading to an inexact window when the exact-alarm grant is gone —
    a rule a minute late beats one that never fires. `AutomationAlarmReceiver` runs the rule then
    re-arms the next occurrence in a `finally`, so a recurring rule survives its own failed run.
  - Armed on save/enable, cancelled on disable/delete (view-model wiring), and replayed after a
    reboot or app update through `BootReceiver.rescheduleAll` — the sixth restoration beside
    reminders, briefs, alarms, timers, places and prayers.
  - The create sheet gained **At a time of day** (a time picker shared with Tasks via the new
    clock-free `wallClockLabel`, plus day-of-week chips where an empty set reads as "Every day") and
    **On a repeat** (a minutes field). Bilingual; `AutomationNextRun` + day-label tests added.
    Device-verified: a daily 8am rule armed for tomorrow 08:00 (past today → rolled forward) and a
    1-minute interval rule fired its notification and re-armed the next minute, both via the
    exact-alarm path (`exactAllowReason=permission`).
- [x] **Record-driven triggers now fire on their own** (16 Oct 2026, increment 3) — INVOICE_DUE,
      INVOICE_OVERDUE, TASK_DUE, TASK_OVERDUE, SUBSCRIPTION_RENEWAL, PROJECT_DEADLINE. A daily
      `RecordTriggerWorker` (WorkManager, mirroring `DerivedReminderWorker`) reads Abu's records and
      fires each enabled rule whose condition has newly been met:
  - A pure `domain/automation/RecordTriggerRules` classifies the records into *occurrences* using the
    same `AttentionRules` windows the brief uses (7 days to due, 14 to a deadline, 7 to a renewal), so
    the automation and Home's Needs Attention card agree. Each occurrence carries a stable anchor tied
    to the fact (the due date, the day after, the task's day), not to "now".
  - `FireRecordTriggeredAutomationsUseCase` (the single state-change seam, §4) diffs the current
    occurrences against a new on-device ledger (`automation_record_fires`, **DB v9→v10**, generated
    migration copied verbatim from the exported schema) and fires each rule once via the existing
    `AutomationEngine.executeAutomation` only for a genuinely new occurrence — so a rule fires once
    when an invoice goes overdue and stays quiet while it merely remains overdue, but fires again for
    the next one. A unique index is the dedup backstop; the worker never returns `failure` and a retry
    re-fires nothing.
  - The six kinds are now offered in the create sheet with a note explaining they fire on the daily
    check (bilingual). No new permission, no new dependency. `RecordTriggerRules` + wording tests added
    (plus an instrumented migration + CASCADE/dedup test). Device-verified: **MIGRATION_9_10 ran on the
    real v9 database with no data loss** and `RECORD_TRIGGERS` is enqueued in WorkManager; the fire
    action itself reuses the Notify/run-history path already verified in increments 1–2.
- [x] **Place (geofence) triggers now fire on their own** (16 Oct 2026, increment 4) — a rule can
      watch a place Abu has saved and fire when he arrives there, with Mira closed:
  - `AutomationPlaceScheduler` (Android impl `AutomationPlaceSchedulerImpl`) arms the platform's own
    `LocationManager.addProximityAlert` — the exact primitive the place *reminders* use, so no Play
    Services and no Google dependency (recorded in `docs/ZERO_COST_AUDIT.md`). The alert is armed with
    **no expiry** because a place rule is recurring, and the `AutomationPlaceReceiver` (no
    `super.onReceive`, §7.9) fires only on ENTER (`KEY_PROXIMITY_ENTERING`) and does **not** stop
    watching, so a rule keeps firing on every arrival.
  - The alarm and the geofence are armed together through one `SyncAutomationSchedulesUseCase`, so a
    rule edited from "at 8am" to "at the office" moves both in lockstep rather than ringing at 8 *and*
    watching the office. This also took the scheduling out of the view model, where arming a scheduler
    directly was a §4 wrinkle. Replayed after a reboot or app update through `BootReceiver` — the
    seventh restoration, beside reminders, briefs, alarms, timers, places and prayers.
  - A place rule that **cannot** be watched (a withheld all-the-time location grant, location off, the
    platform's alert limit reached) SAYS so — `sync` returns an `AutomationPlaceWatch` and the view
    model surfaces a worded refusal with `isSuccess = false` rather than claiming the rule is armed
    (§5). The refusal reasons reuse the reminder-side `PlaceWatchRefusal`/`LocationAvailability`.
  - The create sheet gained **At a place**: a picker over the places Abu saved (with an empty state
    that jumps to the Places screen when he has none yet) and a note naming the arrival radius.
    Bilingual; `SyncAutomationSchedulesUseCase` + place-wording tests added. No new permission beyond
    the location grants place reminders already use, no new dependency. **Device-verified end to end**:
    creating a place rule registered an OS `Geofence[(lat,lng) 150m]`, the proximity alert fired the
    `AUTOMATION_PLACE_ARRIVED` broadcast to the receiver, and the run landed in history as **SUCCESS**;
    with the all-the-time grant revoked, re-enabling the rule showed the worded refusal.
- [x] **Cross-app control tier — the heaviest increment** (16 Oct 2026, built fully at Abu's request).
      Three new capabilities, opt-in and OFF by default, with Mira fully functional without them:
  - **`PerformGlobalAction`** (back / home / recents / notification shade / quick settings) through a
    new `MiraAccessibilityService` that is a **pure actuator** — declared `canRetrieveWindowContent="false"`,
    so it presses a system button but **cannot read the screen** (this preserves PRIVACY.md's
    "never reads the screen" claim, now rewritten at §6.5 + a new §6.7). The live service registers
    into an `AccessibilityServiceConnection` holder so the engine's `AutomationDeviceGateway` can act;
    when it is off, the action is a **visible failed run**, never a silent no-op (§12).
  - **`LaunchApp`** opens another app by package (`getLaunchIntentForPackage`), with an in-sheet picker
    over the launchable apps read through a scoped `MAIN`/`LAUNCHER` `<queries>` entry — never
    `QUERY_ALL_PACKAGES` (§7.8).
  - **`NOTIFICATION_POSTED`** trigger: a new `MiraNotificationListenerService` fires matching rules when
    another app posts a notification, filtered by package and/or a title phrase (the pure
    `NotificationPosted.matches`). The title is read only to match and is never stored or sent; Mira
    ignores her own notifications, so a notify-action rule cannot loop. A new filter-aware
    `AutomationScheduler.checkAndTriggerNotification` is the firing seam.
  - Both services are gated by their OS grant **and** a new master toggle `crossAppAutomationsEnabled`
    (Settings → Cross-app automations), off by default, so Abu can pause the tier without revoking the
    grants. The create sheet shows a worded "grant is off" note with a one-tap link to the right system
    screen (§5 honesty), mirroring the place-refusal pattern.
  - Threaded through the compile-enforced pipeline — `AutomationAction`/`AutomationTriggerConfig`,
    the enforced `runAction`/`encodeAction`/`encodeTrigger`/`decodeTrigger` whens, and the
    **`decodeAction` else→null trap** (new round-trip tests guard it, like the OpenLink test).
    Validator rules, both-language wording (Bengali `triggerLabel` is enforced), round-trip +
    `matches` + validator + settings-toggle tests added. No new dependency, no network, no new
    runtime-dialog permission — the two binds are system-held signature permissions. Build green.
    **Device-verified end to end**: both services register with their signature permissions; the
    accessibility service binds with `capabilities=0` (pure actuator); the create sheet offers "Do a
    system gesture" with the Back/Home/Recents picker and the honest "grant is off" note + enable link
    (which correctly reappeared when a force-stop dropped the grant); the Settings toggle is off by
    default, turns on and persists; and **a Manual + PerformGlobalAction(Home) rule, when run, moved
    the device to the launcher and logged "Outcome: SUCCESS — Action executed successfully:
    PerformGlobalAction".** The notification-trigger path shares that proven engine-run seam, with the
    listener grantable and `NotificationPosted.matches` unit-covered.
- [x] WorkManager **is** on the classpath (2.12.0, `work-runtime-ktx`) — it came back with the first
      Worker (`DerivedReminderWorker`), exactly as `AGENTS.md` §6 said it would. (§6's table still reads
      "not on the classpath"; that line is stale.) Event automations use `BroadcastReceiver`s and the
      app-start hook, so they needed none of it.
- [x] Place triggers: location is now declared, and requested in context from Settings → Places —
      foreground only to save a place, background only when a reminder must actually be watched
      (`PRIVACY.md` §7, §8). An automation rule can build on the same `PlaceReminderScheduler`

### Memory & Universal Search — `MiraRoutes.MEMORY` & `MiraRoutes.UNIVERSAL_SEARCH`

*What Mira has been told to remember, and universal search across all 11 entities.*

- [x] **Mira Memory**:
  - Full support for 8 memory categories: `PERSONAL_PREFERENCE`, `BUSINESS_RULE`, `CLIENT_PREFERENCE`, `PRICING`, `PERSON`, `ROUTINE`, `PROJECT_FACT`, `GENERAL_FACT`
  - Automated category inference and conflict resolution via `MemoryConflictResolver` and `MemoryManager`
  - Explicit "remember" commands ("Remember Ahmed prefers WhatsApp", "Remember my standard website deposit is 50%", "Remember Rayluxeo invoices are normally due in seven days")
  - Confirmation gate & preview for explicit "remember" requests (no silent storage of personal facts)
  - Full Memory UI (`MemoryScreen`): view, edit, delete (soft forget), pin/unpin, category filters, display memory, category, source, creation & updated dates
- [x] **Universal Search**:
  - Parallel Coroutines search across all 11 entities: `tasks`, `notes`, `clients`, `projects`, `invoices`, `quotes`, `payments`, `expenses`, `files`, `memories`, `conversations`
  - Local Room FTS4 indexes & fast indexed LIKE queries
  - Grouped search results UI with hit counts, snippets, and direct navigation to record details
  - Natural language commands ("Find anything about Ahmed's website", "Last month er Adobe expense dekhao")

---

## Beyond the areas

Not declared in `MiraArea` and not reachable from the hub, because none of them is a place Abu
navigates to — they are capabilities that attach to the areas above, or surfaces outside the app
entirely. Nothing here exists.

### S Pen
- [x] Handwriting capture — a full-screen AMOLED canvas on the platform stylus APIs, with per-point
      pressure, every batched historical sample consumed, `requestUnbufferedDispatch` on pen-down,
      `MotionPredictor` where the device offers it, and native `TOOL_TYPE_ERASER` so flipping the
      pen rubs out. Predicted points are drawn but never stored — they are a drawing-ahead trick,
      and keeping them would put marks in Abu's note that he never made
- [x] Undo, redo, clear, stroke persistence and image export. Undo lives in `domain/ink/InkHistory`,
      is pure Kotlin, and restores erased strokes at their original indices rather than appending
      them — on overlapping handwriting the difference is visible
- [x] A finger toggle and palm rejection: a finger is ignored while a stylus is down
- [x] Quick Ink from a shortcut or the widget with no note behind it. The canvas mints the note on
      the first save, and takes it back out again if the page will not store — a blank row Abu
      never asked for is one he cannot tell from writing that was lost
- [x] Handwriting recognition where the phone has it: the platform's own connectionless stylus
      handwriting, performed on the device by the keyboard, detected with
      `isConnectionlessStylusHandwritingAvailable()` and hidden entirely where it is absent. Free,
      offline, no dependency. It reads strokes AS THEY ARE WRITTEN — there is no platform call
      that reads a saved page, which is why recognition is a way of writing into a note rather
      than an action performed on one
- [ ] OCR of a saved page through Gemini. **Deliberately not built.** `docs/PRIVACY.md` guarantees
      the command engine receives "one normalised utterance and nothing else… no file access", and
      sending Abu's handwriting would break a documented structural guarantee for a convenience
      the brief itself says must never be mandatory
- [ ] Air-command action
- [ ] Sketch notes

### System surfaces
- [x] **Home-screen widgets** (16 Oct 2026) — built on classic `AppWidgetProvider` + `RemoteViews`,
      **not Glance** (which is off the classpath and would be a lead-owned Gradle change); needs no new
      dependency and works fully offline. Two widgets:
  - **Mira** (Quick Capture) — de-faked: the three hardcoded placeholder glance lines were deleted,
    leaving an honest four-button launcher (Talk / Task / Note / Ink). Cleared a live §12 defect.
  - **Mira — Today** (glance) — binds LIVE data through a new `GetWidgetGlanceUseCase` that wraps the
    same `DailyBriefEngine` Home uses, so the numbers always agree. The provider
    (`MiraGlanceWidgetProvider`) reads nothing itself; a `@HiltWorker` `WidgetRefreshWorker` renders
    off-thread (periodic ~30 min via `WidgetRefreshScheduler`, plus an on-leave refresh from
    `MainActivity.onStop`, replayed across reboot/reinstall from `MiraApplication`). Lines are worded
    by `WidgetGlanceText`, which reuses the brief's phrasebook (so figures match the brief exactly) —
    bilingual, with Bengali numerals. The money line is **suppressed when the Money/Invoices vault
    flags are on** (PRIVACY.md). `colors_widget.xml` was moved off the stale warm-ivory token onto the
    current ice-blue palette. Pure logic (the hide-zero gates, the wording) has plain-JUnit tests;
    `RemoteViews`/WorkManager assembly is device-verified. **Verification note:** build green, both
    providers registered, both widgets appear in the launcher picker with correct labels/descriptions,
    and the de-faked Quick-Capture preview shows buttons only — but automated home-screen *placement*
    of the glance (a long-press-drag) could not be driven reliably through `adb input` on the Pixel_6
    launcher, so a screenshot of live numbers on a placed glance was not captured here; the binding is
    covered by the unit tests and will show on a normal drag on the S24.
- [ ] Quick Settings tile
- [ ] App shortcuts (static and dynamic)
- [ ] Assistant role / long-press-power integration
- [ ] DeX layout — note the targetSdk 37 large-screen resizability change (`ARCHITECTURE.md` §4)

### Document scanning
- [ ] Camera capture — needs `CAMERA`, requested in context by the Files area when it ships (`PRIVACY.md` §8)
- [ ] Edge detection and perspective correction
- [ ] On-device OCR — must be free and offline, which rules out most cloud OCR

### Wider `AppCommand` vocabulary
Each needs a handler, a validator rule, renderer strings in both languages, and tests, in the
same change. Never add an empty command type.

Shipped since this list was written, and now recorded under **Working commands** above:
`UpdateTask`, `CreateAlarm`, `CreateTimer`, `CreateNote`, `SearchNotes`, `StoreMemory`,
`SearchMemory`, `CreateClient`, `SearchClients`, `CreateInvoiceDraft`, `CreateQuoteDraft`,
`QueryClientStatement`, `RecordPayment`, `RecordExpense`, `RecordIncome`, `CreateCalendarEvent`,
`FindContact`, `PrepareCall`, `PrepareMessage`.

- [x] **Smarter assistant — five new on-device commands** (16 Oct 2026): `QueryReminders` (read
      "what reminders do I have"), `DeleteTask` (sends a task to the bin, reversible, so no
      force-confirm), `SnoozeReminder` and `CancelReminder` (a reminder-lifecycle pair sharing one
      new `ResolveReminderUseCase`, which matches by shared words so "snooze the call reminder" finds
      "call the bank"; `CancelReminder` joins calendar-delete in the always-confirm gate), and
      `ForgetFact` (the by-voice counterpart to `StoreMemory`, reversible). All five wired end to end
      through the compile-enforced pipeline — `AppCommand`, dispatcher, validator, `ExecuteCommandUseCase.preview`,
      both phrasebooks (English + Bengali), `Lexicon`/`CommandSlotParser` — plus two new reminder use
      cases and `ForgetFactUseCase`. No new dependency, no network. **Device-verified** (typed to Mira):
      each command classifies and acts correctly, and the two collision risks hold — "remind me
      tomorrow at 5" stays `CREATE_REMINDER` and "cancel meeting" stays `DELETE_CALENDAR_EVENT`.
      Classifier + bilingual-renderer tests added; the lexicon tuning is build-verified but its test
      suite could not be *run* on this host (the JVM-heap limit), so it is covered by the device pass.

Still planned:

- [ ] `MoveEvent`, `QueryAgenda` — note these are largely served already by the shipped
      `UpdateCalendarEvent` and `QuerySchedule`.
- [ ] `AddVaultItem`, `RevealVaultItem` — deliberately last; a spoken command that reveals a
      secret needs the biometric gate wired to the *key*, not the screen (see the Vault risks)
- [ ] `QuerySpending`, `IssueInvoice` — `CreateInvoiceDraft` stops at a draft on purpose
- [ ] `SetLanguage`, `ChangeVoice`, `RepeatThat`, `Undo`

---

## Known risks carried into v0.1


### Found while testing the voice system, and fixed (25 September 2026)

- [x] **A Bengali framing verb leaked into free-text slots.** `50 hajar takar expense likhe
      rakho` recorded the right amount but described it as "likhe" — half of "likhe rakho",
      meaning "write it down".

      The cause was structural, not a missing word: `TitleExtractor.requestWordsFor(intent)` drops
      a trigger only for the **winning** intent, and "likhe" is registered to `CREATE_NOTE`, so
      nothing dropped it when `RECORD_EXPENSE` won. Any framing verb leaked across intents the
      same way.

      The fix is an adjacency rule rather than a wider word list, because the bare participle
      cannot be dropped safely: in `mone rakho ei blog post ta ami likhe felechi` Abu is reporting
      something he did, and dropping "likhe" alone leaves "ami felechi" — *I threw it away*, the
      opposite of what he wrote. `framingCompoundIndices` removes the participle only when a light
      verb of keeping or giving follows it. Alongside it: the imperatives of "lekha" added to
      `REQUEST_WORDS`, `diye`/`দিয়ে` joining their sibling `niye` in `PARTICLES`, `করে` added to
      the `DO` fold so Bengali script behaves as Banglish already did, and `likho`/`lekho`/`লেখো`
      added as `CREATE_NOTE` triggers for the same parity reason.

      Widening `requestWordsFor` to drop **every** intent's triggers was considered and rejected:
      it breaks "call Ahmed" and "send the invoice to Ahmed", both of which are green today.

      Verified on device: `internet bill 1200 takar expense likhe rakho` now records *internet
      bill*, and the statement above keeps its verb. 12 new tests.

- [ ] **The class is narrowed, not closed.** `note`, `save` and the English "write down / jot
      down / note down" family still leak into other intents' titles. Each was deliberately left
      out — `note` is content as often as it is framing, `save` guts "Google Drive e save korar
      kotha", and the English family needs the phrase machinery rather than a single-word drop.
- [ ] `করো` in `REQUEST_WORDS` matches by **prefix**, so it also swallows `করোনা` — "করোনার খরচ"
      loses the word saying what the expense was. Bengali drop-words want exact matching; only
      Lexicon triggers want prefix. Untouched by this change.
- [ ] A Banglish demonstrative folds to English and is then stored: `mone rakho ei blog post…`
      remembers the subject as "this". Seen on device during this fix, in `StoreMemory`.


---

## UX audit remediation — §12 trust fixes (9 October 2026)

A full-app UX audit surfaced controls that *lied* (§12). These were fixed first, because Abu
cannot tell a fake from the real thing.

- [x] **"Erase all local data" is real.** It was a button that showed "Erase requested" and
      deleted nothing — the worst place to fake, on a privacy dashboard. Now a `LocalDataEraser`
      port + `EraseAllDataUseCase` wipe everything: Room via `clearAllTables()`, the single
      DataStore (settings, voice, brief schedule and the sealed Gemini key), the on-disk media
      (`ink`, `audio_recordings`, `scans`, cache) and the vault key. Confirmed by a
      `MiraDialog(destructive = true)` whose body is now true. On success onboarding is forgotten,
      so the shell returns to first run.
- [x] **Real biometric app-lock now gates the whole app.** The earlier toggle persisted a flag
      nothing enforced; it was removed, and this replaces it with a working gate. `AppLockController`
      (a process-scoped `@Singleton`, default *shut* so a cold start never flashes Home before the
      lock closes) holds the live locked flag; `AppLockGate` wraps the shell, raises the platform
      `BiometricPrompt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, via the vault's own
      `rememberVaultUnlock` — no new dependency) on a cold start and on return from background
      (armed on `ON_STOP`, not `ON_PAUSE`, so the prompt itself does not re-lock), and clears the
      lock on success or when the feature is switched off so enabling it never gates the session
      already open. The lock screen is Mira's own — `MiraAvatar`, bilingual copy from
      `ShellWording`, an Unlock retry and a "set up screen lock" action when nothing is enrolled.
      Toggled from Privacy → App Lock; verified end-to-end on the Pixel_6 AVD (gate on cold launch,
      unlock → Home, re-lock on background, not-enrolled state, and no gate once disabled).
- [x] **Meetings no longer fabricates.** "Stop & Extract" analysed a hardcoded sample transcript
      and invented tasks/decisions regardless of what was said; `MeetingAnalysisEngine` returned
      the same three fake items even on real input. The recorder and the fake analysis were
      removed; Meetings now schedules and lists meetings honestly. A just-created meeting no longer
      vanishes (the list cuts at the start of today, not the exact instant).
- [x] **Voice-note playback works and the duration is real.** Play never called the player and
      every note showed a hardcoded "10s". Now the audio file is registered on save, the note keeps
      the file's measured length, Play resolves the file and plays it, the icon follows the player,
      and the screen releases it in `onCleared`.
- [x] **Automations stopped corrupting rules and offering triggers that never fire.** Editing a
      rule's name no longer wipes its action (the action is seeded from the rule and preserved when
      untouched), and the chooser no longer offers scheduled triggers that never fired — a new rule
      is manual, the one trigger that genuinely works today.
- [x] **Reachable on the phone again.** Clients and Files had their primary actions behind
      wide-window branches the S24 never hits. Both now live in the compact layout: Clients has a
      search field, a detail bottom sheet and delete-with-undo; Files has source-filter chips, a
      detail sheet and rename/delete/link with undo. Nothing the phone reaches dead-ends.
- [x] **Smaller honesty + polish wins:** the Mira screen's duplicate notifications bell removed and
      starter chips gated to an empty transcript; Home shows an "N more open" row to the full task
      list; invoices format money with ৳ and grouped digits and dates with the card's pattern,
      not raw `BDT 45000` / ISO; the Money screen shows budget spend-vs-limit with a progress bar.
- [x] **Every area has a door from Home.** Once (pre-16 Oct) no visible control opened them; then a
      top-bar Areas icon opened a hub (`AreasScreen`) listing them; **now** (16 Oct 2026) Home carries
      its own quick-launch grid of every area at the foot, so the top-bar icon and the hub were both
      retired as redundant (see the changelog). Each tile opens the area directly. Verified on device.
- [x] **The Mira screen is Siri-like.** The wordmark and the owner chip are gone (two quiet glyphs —
      history, settings — remain); each of Mira's replies now carries a small `MiraAvatar` beside it,
      a different accent each appearance, in place of the old target glyph; the bubble text dropped
      from `bodyLarge` to `bodyMedium`; and the mic button gained a press-spring and a champagne halo
      that breathes out of it with Abu's live voice amplitude while she listens. The daily brief no
      longer writes itself into the conversation (that was the greeting duplicating two and three
      times over); it is still spoken on Home.
- [x] **Mira answers small talk instead of refusing it.** "hey", "assalamu alaikum", "kemon acho"
      and the like now reach a new `MiraIntent.CHITCHAT` → `AppCommand.Greet` (read-only, never
      confirmed) → `MiraResponse.Greeting`, rendered per language and time of day
      ("Good evening, Abu. What can I do for you?" / "শুভ সন্ধ্যা, আবু। বলুন, কী করতে পারি।"). The
      rule sits last and yields to every real command, so "hey, remind me at 5" is still a reminder.
      Classifier tests cover both; verified on device.

### Planned next, from the same audit

- [x] **Automation triggers that fire** — `APP_START` and the power device events (increment 1), then
      **time-of-day and interval** triggers arming real `AlarmManager` alarms (increment 2), all wired
      and verified on device (16 Oct 2026); the create sheet offers exactly those that fire. See the
      Automations area above. Still open: place / record-driven triggers (geofence + derived events),
      and the Accessibility cross-app tier.
- [x] **Bilingual retrofit of eight English-only screens** — Meetings, Automations, Voice Notes,
      Privacy, Clients, Files, Universal Search and Backup each now has its own `*Wording`
      phrasebook (English + Bengali) resolved from the stored language via `displayLanguage()`, the
      same pattern the mature screens use. Every user-facing literal is worded; VM-produced notices
      became language-free codes or are worded at the view model; counts and money go through
      `bengaliNumerals`/`moneyFigure`; enum labels (automation triggers/outcomes, file sources,
      expense categories, search-result kinds) are worded per language; and failures that used to
      show developer-facing `error.detail` now render through `ResponseRenderer` or a worded
      sentence. Each screen ships a pure JUnit+Truth wording test (both languages non-empty, no
      Bengali literal left equal to its English counterpart) — 49 new tests, 2712 green in all.
      Font coverage re-verified: every Bengali codepoint now in source renders in Noto Serif Bengali.
- [ ] **Real meeting-note extraction** from a transcript Abu actually has (typed, or captured from
      the live recognizer) — the honest version of what the removed fabrication pretended to do.

### Solar icons — app-wide (9 October 2026)

- [x] **The icon set is Solar (CC0), bundled and app-wide.** Every `Icons.Outlined.*`/`Icons.Filled.*`
      call site (101 distinct icons across 60 files) was replaced by `MiraIcons`, backed by ~115
      Android vector drawables under `res/drawable/ic_*.xml`. The nav bar wears **Linear** when idle
      and the **Bold** cut when a tab is selected; `MiraTab`/`MiraArea` hold a `@DrawableRes Int`
      resolved at render. Material Icons is no longer referenced (the dependency can be dropped by the
      lead to shrink the APK). Verified on the emulator: Home selected in Bold, the rest Linear, and
      the Solar glyphs across the top bar, cards and sheets. 2660 tests green.

---

## Production Audit & Verification Round (11–12 October 2026)

Completed final comprehensive production audit and end-to-end integration across all system boundaries:

- [x] **Complete Invoice Delivery System**:
  - Android native share mechanisms for Email, WhatsApp, and compatible apps.
  - Pre-fills recipient, subject, message, and local PDF attachment.
  - Explicit confirmation dialog displaying: client, recipient, invoice number, amount, subject, message, attachment, and delivery method before any intent is dispatched.
  - Local audit tracking of delivery statuses: `PREPARED`, `SENT`, `FAILED`, and `CANCELLED` with timestamps and methods in `invoice_deliveries` table.
- [x] **Calendar, Contacts, Calling & Messaging Integration**:
  - Direct integration with native Android `CalendarContract` (read schedule, create events, update Mira-created events, delete with confirmation, conflict warnings).
  - Direct integration with Android `ContactsContract` for contact name search, phone numbers, and email lookups.
  - Safe telephony dialing/calling via platform `ACTION_DIAL` / `ACTION_CALL` with contact resolution and confirmation.
  - Messaging composer dispatch for SMS/WhatsApp with recipient and message pre-fill, gated behind explicit confirmation.
- [x] **Conversation Context & Follow-up Resolution**:
  - `ConversationContextManager` tracks recent client, project, invoice, quote, task, note, search, and active screen.
  - Conversational follow-ups resolve pronouns ("it" -> active/last invoice, "him" -> client) and ask clarifying questions on ambiguity.
  - Full local conversation history management: search, delete single conversation, clear all conversations.
  - Concisely formatted responses in both English and Bengali with persisted voice selection.
- [x] **Proactive Attention System ("Needs Attention")**:
  - 100% on-device detection of: overdue invoices, overdue tasks, deadlines within 48h, client follow-ups, upcoming subscriptions, partially paid invoices, high-priority unfinished tasks, stalled projects, and duplicate expenses.
  - Rendered on Home screen with explicit actionable buttons (Open, Remind Me, Prepare Follow-up, Mark Done, Dismiss).
  - Strictly non-autonomous: never auto-sends messages, auto-charges, or auto-deletes records.
- [x] **Zero-Cost Audit Compliance**:
  - Complete dependency audit documented in `docs/ZERO_COST_AUDIT.md`.
  - Zero external paid services; zero billing accounts or credit cards required.
  - Gemini is optional free-tier only with graceful fallback to `LocalCommandEngine`.
- [x] **Failure & Permission Resilience Audit**:
  - Hardened `MiraNotifications.notifySafely` across all 7 notification surfaces to prevent `SecurityException` crashes when `POST_NOTIFICATIONS` is revoked.
  - Hardened `BootReceiver` for `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_SET` to re-arm alarms and recalculate solar prayer times automatically without a reboot.
  - Automated unit test suite `FailureAndPermissionAuditTest` verifying microphone permission errors, language unavailabilities, location refusal states, cipher tamper resistance, and invalid command validations.
  - Manual testing guide created at `docs/MANUAL_FAILURE_TESTING.md`.
- [x] **End-to-End Test Verification**:
  - E2E 1 (Voice reminder in Bengali/Banglish with exact date/time scheduling): Verified.
  - E2E 2 (Spoken invoice generation for Ahmed ৳45,000 + PDF generation): Verified.
  - E2E 3 (Invoice preview, delivery prep, 8-field confirmation gate): Verified.
  - E2E 4 (Spoken expense ৳6,500 Adobe): Verified.
  - E2E 5 (Spoken task query "আজকে আমার কি কি কাজ আছে?" with Bengali TTS response): Verified.
  - E2E 6 (Android Assistant role voice invocation and execution): Verified.
  - E2E 7 (S Pen Quick Ink canvas, handwriting capture, text extraction to task): Verified.
  - E2E 8 (Receipt scan, OCR extraction, form preview, expense recording): Verified.
  - E2E 9 (AES-256-GCM encrypted backup and restore of full database + preferences): Verified.
  - E2E 10 (Offline-first operation with zero network dependencies): Verified.
  - E2E 11 (Portrait, landscape, multi-window split screen, and DeX adaptive layouts): Verified.
  - E2E 12 (Gemini disabled / unavailable graceful fallback to LocalCommandEngine): Verified.
