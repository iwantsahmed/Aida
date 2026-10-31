# Architecture

How Mira is put together, and why each boundary exists. Rules that bind every session live in
`AGENTS.md`; this document explains the reasoning behind them and records the decisions.

---

## 1. Module strategy

**Mira is deliberately a single `:app` module.** `settings.gradle.kts` includes exactly
`:app`, and that is not an accident or a thing waiting to be fixed.

Why one module:

- One developer, one device, one product. Gradle module boundaries buy parallel compilation and
  team-scale enforcement; neither applies here, and both cost build configuration time on every
  single edit.
- The whole toolchain (AGP 9 built-in Kotlin, KSP 2.3.x, Hilt 2.60.1) has exactly one known-good
  configuration. Multiplying that configuration across modules multiplies the surface for
  `google/ksp#3050`-class breakage.
- Layering is enforced by package discipline and code review, not by the build graph.

**The package layout carries the layering instead:**

```
com.rayluxeo.aida
├── core/
│   ├── common/      AppResult, MiraError, Language, MiraClock, DispatcherProvider
│   └── design/      MiraTheme, MiraColors, MiraTypography, MiraSpacing, MiraShapes,
│       │            MiraMotion, MiraHaptics  (AGENTS.md §11)
│       └── component/  the 23-component library every screen composes from
├── domain/          PURE KOTLIN — no Android imports at all
│   ├── model/       Task, Reminder, VaultItem, ConversationTurn, DailyBrief, Money
│   ├── command/     AppCommand, CommandRequest, CommandEngine, CommandValidator,
│   │                CommandDispatcher, MiraResponse, CommandOutcome
│   ├── repository/  TaskRepository, ReminderRepository, VaultRepository,
│   │                ConversationRepository, BriefRepository, SettingsRepository,
│   │                GeminiCredentialStore, MiraSettings, VoicePreferences
│   ├── scheduler/   ReminderScheduler
│   ├── nlu/         TextNormalizer, DateTimeParser — ports, not implementations
│   ├── finance/     Calculator — the port; the arithmetic lives in finance/
│   └── usecase/     the orchestration the UI calls for anything with rules
├── data/            Room, DataStore, Keystore, AlarmManager. Implements domain/repository.
│   └── di/          DataModule
├── ai/              CommandRouter (implements CommandInterpreter) + both CommandEngines
│   ├── nlu/         MiraTextNormalizer, MiraDateTimeParser, transliteration, lexicon
│   ├── local/       LocalCommandEngine
│   ├── gemini/      GeminiCommandEngine, GeminiClient, GeminiCommandMapper
│   └── di/          NluModule, GeminiModule
├── voice/           MiraTextToSpeech, SpeechRecognitionManager, VoicePersonaManager,
│                 VoiceResponseFormatter, MiraAudioFocusManager, MiraEarconPlayer,
│                 VoiceConversationController
│   └── di/          VoiceModule
├── finance/         MiraCalculator, MoneyMath, CalculationVocabulary — BigDecimal only
├── ui/              Compose screens, view models, onboarding, navigation, ResponseRenderer
└── di/              EngineQualifiers + AppBindingsModule (the cross-cutting wiring only)
```

Hilt modules sit next to the code they bind — `data/di`, `ai/di`, `voice/di` — rather than in one
central package. Only bindings that belong to no single feature live in `di/`.

### Dependency direction

```
        ui ────────┐
      voice ───────┤
    finance ───────┼──────►  domain  ◄────────── data
         ai ───────┘         (pure)              (Android, Room, Keystore)
```

- `domain` depends on nothing but Kotlin, `java.time` and coroutines. This is enforced by
  reading, not by the compiler — the single module cannot say no — so it is checked on every
  change: `grep -rn "^import com.rayluxeo.aida.\\(ai\\|ui\\|voice\\|finance\\|data\\)\\." app/src/main/java/com/rayluxeo/mira/domain/`
  must print nothing.
- Where the domain needs something the outside provides, it declares a **port** and the outer
  layer implements it: `TextNormalizer` and `DateTimeParser` (implemented in `ai/nlu`),
  `Calculator` (implemented in `finance/`), `CommandInterpreter` (implemented by
  `ai/CommandRouter`), `ReminderScheduler` (implemented in `data/scheduler`), and every
  `domain/repository` interface (implemented in `data/repository`).
- `data` depends on `domain` (to implement its interfaces) and on Android.
- `ui`, `voice`, `finance`, `ai` depend on `domain`. They never depend on `data`.
- **Nothing outside `data/` may import `androidx.room` or reference a DAO.**
- **Every state change goes through a use case.** A view model may observe a repository `Flow`
  directly for a read path — a pass-through use case around `observe()` adds a file and no
  meaning — but anything that writes, validates, schedules or decides belongs in
  `domain/usecase`. Persisting a settings toggle is the one write a view model may do itself,
  because `SettingsRepository` carries no rules beyond the write.

### Extraction path, and the trigger for taking it

If this ever needs to become a multi-module build, the split is already drawn along the package
boundaries above:

| Target module | From |
|---|---|
| `:core:common` | `core/common` |
| `:core:design` | `core/design` |
| `:core:domain` | `domain/` entire |
| `:core:data` | `data/` |
| `:feature:tasks`, `:feature:vault`, `:feature:mira`, `:feature:home` | the matching `ui/` subtrees |
| `:core:ai`, `:core:voice` | `ai/`, `voice/` |

The split is only worth doing when **one** of these becomes true:

1. A clean `:app:assembleDebug` after touching a single UI file routinely exceeds ~90 seconds.
2. A second person starts committing, so package discipline stops being self-enforcing.
3. A second app surface appears that must not link the whole codebase — a Wear companion, a
   Quick Settings-only build, or a widget-only process.
4. The layering is being violated in review more than occasionally, i.e. the compiler needs to
   start saying no.

Until then, one module is the correct answer. Do not split it for tidiness.

---

## 2. The command pipeline, end to end

This is the spine of the app. `domain/usecase/ExecuteCommandUseCase.kt` is the whole pipeline
in one readable file, and it should stay that way.

### 2.1 Normalisation

`TextNormalizer.normalize(raw, LanguageMode)` produces a `NormalizedText`:

- `raw` — exactly what Abu said or typed, preserved so Mira can quote him back.
- `normalized` — lowercase, Latin script, ASCII digits, with Banglish spelling variants folded
  to one spelling. The intent matcher sees the same string whether Abu wrote `কল`, `call`,
  `kol` or `kall`.
- `language` — `SpokenLanguage.BENGALI` or `ENGLISH`, resolved per utterance when the setting
  is `LanguageMode.AUTO`.
- `script` — `TextScript.BENGALI`, `LATIN` or `MIXED`. Independent of language: *"kal shokal
  10 tay"* is Bengali written in Latin script.

Both forms travel onward in `CommandRequest`, together with the `InputSource`
(`VOICE`, `TEXT`, `SYSTEM`).

### 2.2 Logging Abu's turn

Abu's words are appended to `ConversationRepository` **before** interpretation, so the
transcript is honest even when Mira fails to understand.

Mira's own reply is logged separately by the caller, via
`ExecuteCommandUseCase.logMiraReply(...)`, *after* `ResponseRenderer` has turned the structured
`MiraResponse` into Bengali or English. The transcript therefore holds real sentences rather
than the internal shape of a response.

### 2.3 Engine routing

`ai/CommandRouter` implements one rule, and the rule is short on purpose:

```
localResult = local.interpret(request)
if localResult is Interpreted && !localResult.deferToCloud -> return (localResult, LOCAL)
if !gemini.isAvailable()                                   -> return (localResult, LOCAL)
if request.source == SHARED                                -> return (localResult, LOCAL)
when (gemini.interpret(request)) {
    is Interpreted                                         -> return (remote, GEMINI)
    NotUnderstood, Unavailable                             -> return (localResult, LOCAL)
}
```

Read as prose:

- **Local first, always.** The on-device engine runs on every single utterance.
- **Gemini is consulted on a genuine local miss, or on a deferred pleasantry** — and only when
  it is configured (a key is present, the feature is enabled) and reachable. `isAvailable()` is
  required to be cheap and side-effect-free precisely so this check never costs Abu a pause.
- **A greeting is the one local "Interpreted" that still looks past itself.** `AppCommand.Greet`
  comes back with `EngineResult.Interpreted.deferToCloud = true`: offline it is a real reply so
  Mira greets with the radio off, but when Gemini is reachable the greeting is handed over so
  "tumi kemon acho" gets a genuine answer in Abu's own words rather than a canned pleasantry. If
  Gemini is away or misses, the local greeting still stands. Gemini never sets the flag — what it
  returns is always a firm verdict. See ADR-014.
- **A cloud miss never masks the local verdict.** If Gemini also fails, Mira answers with what
  the local engine concluded. Abu hears one clear answer, not two competing ones.
- **Mira never stalls on the network.** With no key, no data, or a dead connection, the
  pipeline runs to completion at local speed.

`CommandEngine` is a deliberately narrow interface: an engine may *propose* an `AppCommand` with
a confidence, and nothing else. It has no repository, no DAO, no `Context` and no file access.
That narrowness is the enforcement mechanism for "Gemini may never touch Room" — there is no
call it could make even if it wanted to.

### 2.4 Validation

Every command passes `CommandValidator.validate(...)`, whatever produced it.
`DefaultCommandValidator` trims titles, enforces a 200-character ceiling, rejects empty task
references, requires positive task ids, and refuses a reminder whose trigger is not in the
future (checked against the injected `MiraClock`). A failure becomes
`CommandOutcome.Rejected(MiraResponse.Problem(code))`.

This gate exists so that an engine — particularly a remote one — cannot smuggle an invalid
record past the domain rules.

### 2.5 The confirmation rule

Mira asks for a yes when, and only when, **both** hold:

1. `command.isMutating` is true, **and**
2. the interpretation is uncertain: `confidence < EngineResult.CONFIRM_THRESHOLD` (0.62f)
   **or** the command came from `EngineId.GEMINI`.

Consequences worth stating plainly:

- **Read-only commands are never confirmed.** Mira does not ask permission to look something up.
  `SearchTasks`, `GetDailyBrief` and `PerformCalculation` always just run.
- **Every _mutating_ command Gemini proposes is confirmed**, regardless of the confidence it
  claims. A remote model's self-reported confidence is not evidence, and a wrong write is worse
  than a question. A read-only Gemini result — a daily brief, or a conversational answer — is not a
  write, so it runs without a question like any other read.
- A confident local interpretation of a mutating command runs immediately. Asking Abu to confirm
  "add milk to my tasks" every time would make Mira tiring to use.

A confirmation yields `CommandOutcome.NeedsConfirmation` carrying a `ConfirmationRequest`
(the command, the engine that proposed it, and a preview response). Abu's yes re-enters through
`ExecuteCommandUseCase.confirm(...)`, which **re-validates** before executing — the pending
command is not trusted just because it was validated once.

### 2.6 Dispatch

`DefaultCommandDispatcher` is the only place a command becomes an effect. Its `when` is
exhaustive over the sealed `AppCommand` hierarchy with no `else`, so adding a command without
writing a handler is a compile error rather than a silent no-op.

Handlers delegate to use cases — never to repositories directly — and map the result into a
`MiraResponse`.

### 2.7 Response

`MiraResponse` is data, not prose: `TaskCreated(title, dueAt)`, `TasksFound(count, titles)`,
`CalculationResult(expression, value)`, `AmbiguousTask(title, candidates)`,
`Problem(MiraErrorCode)` and so on. The domain never hard-codes a language.

`ui/response/ResponseRenderer` renders each one twice: `speak(...)` stays short because a long
spoken reply is tiresome, `display(...)` may carry a little more detail. Both take the
`SpokenLanguage` the exchange happened in, so Mira answers in the language Abu actually used.

---

## 3. Decision records

### ADR-001 — Platform `BiometricPrompt`, not `androidx.biometric`

**Decision:** use `android.hardware.biometrics.BiometricPrompt` directly for vault
authentication. `androidx.biometric` is not on the classpath and must not be added.

**Why:**

- `androidx.biometric`'s only stable release is **1.1.0**. The modern Class 3 + `CryptoObject`
  shape, and the raised minSdk that comes with it, exist only in `1.4.0-alpha05..alpha07`.
  Shipping a security-critical path on an alpha is a worse risk than using the platform API.
- At **minSdk 31** the platform API is fully capable: `BiometricPrompt.Builder`,
  `setAllowedAuthenticators(int)` (API 30), `BiometricManager.Authenticators`
  (`BIOMETRIC_STRONG`, `DEVICE_CREDENTIAL`) (API 30) and `canAuthenticate(int)` (API 30) are all
  unconditionally available. The compatibility branches `androidx.biometric` exists to hide are
  branches Mira never takes.
- One fewer dependency on a single-device app, at zero loss of capability.

**Constraint inherited from the platform API:** `setNegativeButtonText()` and
`setAllowedAuthenticators(... or DEVICE_CREDENTIAL)` cannot both be set. Pick one and keep the
choice consistent across the vault.

### ADR-002 — Local-first engine routing, Gemini strictly optional

**Decision:** the on-device engine is authoritative; Gemini is an opt-in fallback confined
behind `CommandEngine`, and every command it proposes is validated and confirmed.

**Why:** Mira has to work in a dead zone, with the radio off, and with no key configured. Zero
cost also forbids depending on a paid tier, and a free tier can be rate-limited or withdrawn at
any time. An assistant that stops working when a third party changes its terms is not the
assistant being built here.

### ADR-003 — Deterministic arithmetic, never a model

**Decision:** `finance/Calculator` evaluates expressions on `BigDecimal`. `Money` wraps
`BigDecimal` with an explicit scale ceiling and refuses to combine mismatched currencies.

**Why:** language models produce plausible arithmetic, not correct arithmetic, and a wrong
number in a financial context is silently wrong. The most a model may do is hand an expression
string to the evaluator. `Double` is banned outright for money.

### ADR-004 — Voice chosen by ear, never by name

**Decision:** `VoicePersonaManager` enumerates engines and voices exactly as the device reports
them; `TtsVoiceInfo` deliberately has no gender field; Abu auditions voices and picks one.

**Why:** the Android TTS API exposes no gender anywhere — there are zero occurrences of
"gender" across the `Voice`, `TextToSpeech`, `TextToSpeech.Engine` and
`TextToSpeech.EngineInfo` reference pages. Any gender shown to Abu would be a guess parsed from
a name string, and guessing is worse than not offering it.

**Persistence rule:** store `engineInfo.packageName` + `Voice.getName()` as strings. Never
persist a `Voice` Parcelable — parcel format has no cross-version stability and will break on an
OS or engine update. `Voice.getName()` is unique only *within* an engine, so the engine package
must always be stored and compared alongside it. Always tolerate the pair no longer resolving
on restore.

### ADR-005 — AMOLED-dark only, no light theme

**Decision:** `MiraTheme` ignores `isSystemInDarkTheme()` and always applies the obsidian
scheme.

**Why:** the palette *is* the product's identity, the S24 Ultra's AMOLED panel draws near-zero
power on `#080808`, and a second theme doubles the visual QA surface for a single user who has
already said which one he wants.

### ADR-006 — No `@Parcelize` on Room entities, no overridden entity properties

**Decision:** Room entities are plain data classes with no supertypes and no overridden
properties.

**Why:** two open upstream bugs. `google/ksp#3053` — a `@Parcelize` entity fails KSP type
resolution under AGP 9 built-in Kotlin. `google/ksp#3047` — `@ColumnInfo(name = ...)` is
silently dropped on an `override` property that also carries a `@property:`-targeted
annotation, producing a wrongly-named column that surfaces as a migration crash on the next
upgrade. Neither is fixed as of KSP 2.3.12.

### ADR-007 — No Hilt in local unit tests

**Decision:** `app/src/test` contains no `@HiltAndroidTest`, no `HiltTestApplication`, and no
Robolectric test driving an `@AndroidEntryPoint` activity. Collaborators are hand-written fakes
and classes are constructed directly.

**Why:** `google/dagger#5261` — Hilt's local-test bytecode transform does not run under AGP 9,
and `hilt { enableTransformForLocalTests = true }` has no effect. Tests fail with *"does not
implement interface dagger.hilt.internal.GeneratedComponent"*. Open, no fix. Because the domain
layer is pure and every collaborator is an interface, direct construction is cheap anyway.

Instrumentation tests under `app/src/androidTest` may use Hilt — `MiraTestRunner` already
installs `HiltTestApplication`, and `kspAndroidTest(hilt-compiler)` is declared.

### ADR-008 — Single `:app` module

See §1. Recorded here so the decision is not quietly reversed: the trigger conditions for
splitting are written down, and none of them are met.

### ADR-009 — One `MiraButton` with a style enum, not three components

**Decision:** `MiraButton(text, onClick, modifier, style = MiraButtonStyle.PRIMARY | SECONDARY |
TEXT, …)`. There is no `MiraPrimaryButton`, `MiraSecondaryButton` or `MiraTextButton`.

**Why:**

- The three differ in **emphasis, not behaviour**. Target size, the two-percent press scale, the
  haptic, the disabled treatment and the semantics role are identical in all three. Splitting
  them means three copies of that behaviour, and a change to the press response has to be made
  three times — which is exactly how the feel of a button set drifts apart over a year.
- Emphasis is a property of a *call site*, not of a type. `style = MiraButtonStyle.SECONDARY`
  reads as "the same button, quieter", which is the truth about what it is.
- "A screen carries at most one primary action" is a rule that can be read and checked when
  emphasis is one enum. With three component names it is a convention nobody can grep for.

**Consequence:** a fourth emphasis is a new constant and one branch, not a new file. If a
variant ever needs different *behaviour* — a larger target, a hold-to-confirm — it has stopped
being a style and becomes its own component.

### ADR-010 — `MiraMotion` is an object, not a composition local

**Decision:** colour, spacing, shape and typography reach a composable through composition
locals. Motion does not: `MiraMotion` is a plain Kotlin object of constants, easings and spec
factories.

**Why:**

- A composition local exists so a value can vary by position in the tree. Motion never should.
  There is no sub-tree of Mira that ought to animate at a different speed, and if one appeared
  it would be a bug rather than a feature to support.
- Durations and easings are needed **outside composition**: in a `LaunchedEffect` calling
  `animateTo`, in the navigation graph's `enterTransition` builder, and in plain helper
  functions. A local forces each of those to thread a parameter or to become `@Composable` for
  no reason.
- The usual argument for the indirection is a second value set to swap in. There is no second
  set: ADR-005 means there is no light theme, and there is no reduced-motion variant — the whole
  scale is already short enough that removing it would change nothing worth changing.
- The call site reads `MiraMotion.standard()` either way, so the decision is cheap to revisit if
  one of the above ever stops being true.

**Rule that falls out:** never call `tween()` with a literal duration. Every animation picks
from the scale in `AGENTS.md` §10.1.

---

### ADR-011 — Mira is the system assistant through `VoiceInteractionService`, and asks for the role by opening Settings

**Decision:** Mira implements the platform voice-interaction stack — `VoiceInteractionService`,
`VoiceInteractionSessionService`, `VoiceInteractionSession` and a real `RecognitionService` —
and everything runs in the **main process**. The "make Mira my assistant" control opens the
system settings screen; it does not attempt an in-app grant.

**Why, established by reading the current platform contract rather than from memory:**

- **`RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)` is not supported for this role** and
  throws `IllegalArgumentException` on some builds. The role is changed only from the system
  screen. Detection is fine — `RoleManager.isRoleHeld(ROLE_ASSISTANT)` works — so Mira reads the
  state honestly and hands Abu a deep link to `Settings.ACTION_VOICE_INPUT_SETTINGS`, resolved
  against `PackageManager` first so the button is never shown when nothing would open. A button
  that claimed to grant the role and silently failed is the fake control §12 forbids.
- **`android:recognitionService` is a required attribute** of `res/xml/voice_interaction_service`
  and must name a `RecognitionService` in this package. Without it Mira never appears in the
  assistant list, and it fails silently. It is not a stub: `MiraRecognitionService` delegates to
  the platform **on-device** recognizer, which also avoids resolving back to itself.
- **No `android:process`.** AOSP suggests running the session service in its own process. Mira
  does not, because a second process gets its own Hilt `SingletonComponent` and its own DataStore
  over the same file, which is not multi-process safe — this project has already hit
  "multiple DataStores active for the same file" once. One process, one store, one database.
- **Always-on "Hey Mira" is not available to a third-party app.** `AlwaysOnHotwordDetector` has
  been a `@SystemApi` since Android 12, reserved for assistants shipped with the OS, and depends
  on SoundTrigger keyphrase hardware besides. `HotwordCapabilityDetector` asks the device anyway
  rather than hard-coding the conclusion, so a build that ever permits it would be noticed. Mira
  is reached by the assist gesture, her own button, a Quick Settings tile, a widget, a shortcut
  and the share sheet. A wake word during an **already open** session is a continuation of a
  conversation Abu started, bounded and visible — never a standing microphone.
- **Assist context is off until Abu turns it on.** `assist_structure_enabled` and
  `assist_screenshot_enabled` default to `0`; an app that sets `FLAG_SECURE` withholds its window
  too. Both arrive as an *absence*, which `AssistContextReader` reports as an empty snapshot
  rather than an error to work around.
- **Nothing from the screen leaves the device without a per-request confirmation** that names the
  app it came from and defaults to no. Local first, always — Gemini is never asked a question the
  on-device engine can answer.

**Watch:** Android 17's background audio hardening (§ the watch list above) applies to a session
that speaks. Audio focus can return `AUDIOFOCUS_REQUEST_FAILED` and playback can fail *silently*
from a non-foreground state, so spoken replies from the assistant overlay must be verified on a
device rather than assumed to work because they work in the app.

---

### ADR-012 — Gemini runs on `gemini-flash-lite-latest`, no thinking config, a 15s read budget, 5xx retry

**Decision:** the cloud engine calls `gemini-flash-lite-latest`, sends no `thinkingConfig`, reads
with a 15s timeout, and retries a transient 5xx up to three times with backoff. All four are set in
`HttpGeminiClient`/`GeminiWire`.

**Why** — each point is a failure observed directly on Abu's S24 (16 Oct 2026), not a guess:

- **Model.** The previous `gemini-flash-latest` returned HTTP 503 ("model overloaded") on *every*
  call — the newest Flash is the most contended tier on the free plan, and the whole call silently
  fell back to the local miss. `gemini-2.5-flash` returns 404 (this free key has no access to the
  2.5 line). `gemini-flash-lite-latest` is the one model the key reaches that both accepts Mira's
  request *and* has free-tier capacity to answer. **Do not move back to `flash-latest`.**
- **No `thinkingConfig`.** The 3.x Flash-Lite line rejects an explicit `thinkingBudget: 0` with a
  400 — the request meant to suppress deliberation instead failed outright. Omitting the field is
  the documented workaround; the model keeps a minimum of thinking regardless. **Do not re-add
  `thinkingBudget = 0`.**
- **15s read budget.** With that unavoidable minimum thinking, free-tier Flash-Lite replies land
  around 7–9s, so the old 8s read timeout timed out as often as it succeeded. The cloud path is
  only ever reached *after* a local miss, so there is no good answer being delayed — a longer wait
  beats answering "I didn't catch that" to a question Gemini could have caught.
- **Retry on 5xx.** 503s arrive in bursts; a few bounded retries (450ms then 900ms) turn a passing
  overload into an answer, which is Google's own guidance. A 4xx, a 429 (quota — handled separately)
  or a transport timeout is **not** retried: those are not momentary capacity.

This does not touch [ADR-002](#adr-002--local-first-engine-routing-gemini-strictly-optional) — Gemini
is still optional, still local-first, and Mira is still fully functional with the radio off. It only
makes the optional path actually reach an answer when Abu has asked for it.

---

### ADR-013 — Gemini may answer a question, not only map a command

**Decision:** on a local miss, the cloud engine may propose `AppCommand.Conversational(answer)` — a
plain spoken reply — as well as one of Mira's own commands. It does so through a new `ANSWER` entry
in the response schema, carrying the reply in an `answer` field; `GeminiCommandMapper` turns that
into the command, which is validated and dispatched like any other.

**Why:** Abu asked for it. A question like "what is the capital of Japan" or "explain how wifi works"
used to come back as "I didn't catch that", because Gemini could only ever choose one of six verbs.
Letting it answer makes the optional cloud path useful for more than scheduling.

**What it does NOT change — the boundary of [ADR-002](#adr-002--local-first-engine-routing-gemini-strictly-optional) holds:**

- The cloud engine still only **proposes an `AppCommand`**. `Conversational` carries words and
  nothing else: no repository, no DAO, no Context, no file access. It reads nothing and writes
  nothing, so it is read-only and never held for confirmation.
- It is reached only **after a local miss**, only with a key and a validated network, and never for
  shared content (§5). Fully offline, there is no chatbot, and Mira is unchanged.
- The model is told it **cannot see Abu's own records** and must choose the matching command
  (`SEARCH_TASKS`, `GET_DAILY_BRIEF`, …) for anything about them rather than inventing an answer, and
  it must still hand arithmetic to `PERFORM_CALCULATION` ([ADR-003](#adr-003--deterministic-arithmetic-never-a-model)).

**Tone.** §1 says "never chatty". The answer is therefore constrained in the prompt to how a person
actually speaks: short, plain, no jargon in English, and — at Abu's request — no dashes and no emoji.
The warmth is in being direct and helpful, not in length.

---

### ADR-014 — A local greeting defers to Gemini when the cloud is reachable

**Decision:** `EngineResult.Interpreted` carries a `deferToCloud` flag. `LocalCommandEngine` sets it
for one command — `AppCommand.Greet` — and `CommandRouter` treats a deferred result like a miss for
the purpose of consulting Gemini: it hands the utterance to the cloud when Gemini is available and
the source is not shared, and keeps the local greeting only as the fallback for when Gemini is away,
unconfigured, or misses. Gemini never sets the flag; what it returns is always a firm verdict.

**Why:** Abu typed "tumi kemon acho" and got the canned "Good evening, Abu. What can I do for you?"
instead of a real reply — "Gemini isn't replying in Banglish". The on-device `CHITCHAT` intent
matches small talk and maps it to `Greet`, which returned `Interpreted`, so routing stopped there
and never reached the cloud ([ADR-013](#adr-013--gemini-may-answer-a-question-not-only-map-a-command)).
A bare pleasantry is the one thing Mira can answer on her own yet would rather the cloud answered,
because the cloud answers it in Abu's own words and language. Factual Banglish ("France er rajdhani
ki") already reached Gemini and came back in Banglish; only chit-chat was being intercepted.

**Why a flag and not just dropping `Greet` from the local engine:** offline-first (§3). A greeting
must still work with the radio off, and "I didn't understand" to "how are you" would be a regression.
The deferral keeps the greeting as the guaranteed offline reply while letting the cloud improve on it
when it can — the greeting vocabulary stays in one place (the lexicon), never duplicated.

**What it does NOT change:** the `CommandEngine` boundary ([ADR-002](#adr-002--local-first-engine-routing-gemini-strictly-optional))
holds unchanged — Gemini still only proposes an `AppCommand`. Shared content still never leaves the
phone. Every non-greeting local `Interpreted` is still a firm verdict Gemini is never asked about.

## 4. targetSdk 37 watch list

The app targets API 37, which opts into every Android 17 behaviour change. These are the ones
that can plausibly bite Mira; check them whenever the relevant area is touched.

| Change | Risk to Mira |
|---|---|
| **Background audio hardening** | An app targeting 37 that touches audio in the background needs a foreground service with while-in-use capability, **or** exact-alarm permission plus `USAGE_ALARM` streams. Playback and volume APIs **fail silently** — no exception, no log — and audio focus returns `AUDIOFOCUS_REQUEST_FAILED`. Affects reminder sounds and any TTS attempted from the background. `MiraEarconPlayer` and reminder alarm audio must be verified on-device, not assumed. |
| **`RemoteViews` memory cap** | Combined `Bitmap` + `Icon` memory in a notification parcel is capped at `1.5 × screenWidth × screenHeight × 4`; exceeding it throws `IllegalArgumentException` and crashes the app. Relevant when reminder notifications or a future widget grow custom layouts. |
| **Microphone foreground service while backgrounded** | `RECORD_AUDIO` is while-in-use restricted: a microphone foreground service cannot be *started* from the background. Any always-listening or wake-word design must begin from a visible entry point. |
| **Large-screen resizability opt-out removed** | At targetSdk 37 orientation and resizability restrictions are ignored at sw ≥ 600dp. Matters for DeX, which is on the planned list. |
| **Static final fields become immutable** | `IllegalAccessException` by reflection, hard crash via JNI. Breaks many mocking and instrumentation libraries — another reason the test strategy is hand-written fakes. |
| **Mandatory `ACCESS_LOCAL_NETWORK`** | Enforced at targetSdk 37. Mira makes no local-network calls today; if anything ever does, it needs the runtime permission. |
| **SMS `SMS_RECEIVED_ACTION` delayed 3 hours for OTPs** | Rules out any naive SMS-reading feature; use SMS Retriever / User Consent if one is ever needed. |
| **Certificate Transparency on by default, ECH for TLS** | Only affects the optional Gemini call. No action expected. |

`compileSdk` stays on **37.0**. Platforms 37.1 and 37.2 exist on the stable SDK channel, but
AGP 9.4's documented ceiling is "API level 37" and compiling against the newer minors is
undocumented territory. Do not move without a reason and a re-verification.

---

## 5. Threading and concurrency

- `DispatcherProvider` is injected everywhere a dispatcher is needed, so tests substitute
  deterministic ones. No `Dispatchers.IO` literal outside `DefaultDispatcherProvider`.
- Repositories expose `Flow` for streams and `suspend` for one-shot work. Room supplies the
  flows; nothing polls.
- No `GlobalScope`. No `runBlocking` in production code.
- `SpeechRecognizer` must be created and driven **only from the main thread** — the platform
  class documents this, and `createOnDeviceSpeechRecognizer` throws
  `UnsupportedOperationException` (it does not return null) when on-device recognition is
  unavailable. `SpeechRecognitionManager.listen(...)` returns a cold `Flow` per listening session and is
  responsible for confining those calls correctly.
- TTS engines are enumerated **serially**, one live `TextToSpeech` instance at a time, each
  `shutdown()` before the next. Leaked TTS connections are a known source of ANRs and
  audio-focus problems.

---

## 6. Error handling

`AppResult<T>` is `Success<T>` or `Failure(MiraError)`. `MiraError` carries a `MiraErrorCode`
(`VALIDATION`, `NOT_FOUND`, `STORAGE`, `NOT_UNDERSTOOD`, `ENGINE_UNAVAILABLE`,
`PERMISSION_REQUIRED`, `AUTHENTICATION_FAILED`, `UNKNOWN`), an optional developer-facing
`detail` for logs, and an optional cause.

- Expected conditions return `Failure`. Exceptions are for genuine bugs.
- `detail` is never spoken or shown to Abu verbatim. `ResponseRenderer` maps the *code* to
  wording in his language.
- `map` / `flatMap` / `onSuccess` / `onFailure` keep call sites flat; `getOrNull()` exists but
  discarding an error silently is a review failure.

---

## 7. Navigation

`ui/navigation/MiraDestination.kt` is the contract: `MiraRoutes`, `MiraTab` and `MiraArea`.
Nothing navigates to a string literal, and nothing outside that file decides what exists.

### 7.1 The shape of the graph

Fourteen destinations from Abu's own list — Home, Mira, Tasks, Vault, Calendar, Notes,
Projects, Clients, Invoices, Money, Files, Automations, Memory, Settings — plus the hub, Voice
Setup and the onboarding gate. Seventeen route constants, in four groups.

| Group | Routes | Behaviour |
|---|---|---|
| Bottom-bar tabs | `HOME`, `MIRA`, `TASKS`, `VAULT` | Bottom bar shown. Each keeps its own back stack |
| The hub and its areas | `AREAS`, then `CALENDAR`, `NOTES`, `PROJECTS`, `CLIENTS`, `INVOICES`, `MONEY`, `FILES`, `AUTOMATIONS`, `MEMORY` | Pushed. No bottom bar |
| Settings | `SETTINGS`, `SETTINGS/VOICE` | Pushed. No bottom bar |
| The gate | `ONBOARDING` | Not part of the shell at all — see §7.3 |

**Four tabs, not fourteen.** A bottom bar is a promise that these are the surfaces Abu reaches
constantly. Five is Material's ceiling, and four is where the labels stay readable at Bengali
line lengths. Everything else is one tap further away through `AREAS`, reached from Home.

**Tabs switch; everything else pushes.** Tab switching uses `saveState` / `restoreState` with
`launchSingleTop`, so each tab keeps its own back stack and returning to Tasks does not reset
the scroll or the filter. Pushed screens use the ordinary back stack: system back unwinds
exactly one screen, and every destination has exactly one way back.

It pops back to `MiraRoutes.HOME` **by route**, not to `graph.findStartDestination()`. On a
clean install the graph starts at `ONBOARDING`, which is gone from the stack by the time any tab
can be tapped — and `popUpTo` a destination that is not on the stack silently pops nothing,
which would leave the four tabs piled on top of one another and give Back a long walk home.

**The shell owns the bottom bar, not the screen.** It resolves the current destination against
`MiraTab.entries` and renders the bar only when one matches. A screen never decides whether the
bar is present, so a pushed screen cannot accidentally keep it and a tab cannot accidentally
lose it.

### 7.2 `MiraArea` is the single source of truth

Each of the nine non-tab areas is a `MiraArea` constant carrying its route, title, icon, a
one-line summary, the concrete capabilities it will have, and `isBuilt`.

One enum drives three things that would otherwise drift apart: what the hub lists, what each
route renders, and what `docs/FEATURE_CHECKLIST.md` records as planned. Adding an area is one
constant. Shipping one is flipping `isBuilt` and adding its `composable` — nothing else moves,
and the hub, the screens and the checklist stay in agreement without anyone remembering to
update three places.

Where `isBuilt = false`, the route renders `PlannedAreaScreen`: a screen that names the area,
states plainly that it is not built, lists what it will do, and offers a real action. That is
the governed exception in `AGENTS.md` §11.1. It is bounded by `isBuilt` precisely so it cannot
quietly become the normal way to ship a screen.

`MiraArea.SETTINGS` sits in the enum with `isBuilt = true` so the hub lists it alongside
everything else. The hub is the complete index of what lies outside the bottom bar; a
special-cased row for the one built area would be the beginning of a second list.

### 7.3 The onboarding gate

First run shows `ONBOARDING` instead of the shell. The flag is `MiraSettings.onboardingComplete`
in the `mira_settings` DataStore (`MiraPreferenceKeys.ONBOARDING_COMPLETE`), written by
`SettingsRepository.setOnboardingComplete(true)` on the final step and read back through the
same settings `Flow` as everything else.

**The shell renders nothing until the stored flag resolves.** The answer arrives
asynchronously — DataStore reads the file off the main thread — so for the first frames it is
genuinely unknown, and unknown is represented as its own state rather than collapsed into a
default.

Defaulting in either direction produces a visible flash on one of the two paths:

- Default *complete*: a returning Abu is right, but a **first** run shows Home — empty brief,
  zero counts — for a few frames before cutting to Welcome. The first thing Mira ever shows him
  is a glitch.
- Default *not complete*: a first run is right, but **every** launch afterwards flashes the
  Welcome screen before the shell replaces it.

Holding an empty frame costs one or two frames on both paths and is invisible on obsidian,
which is one more thing the no-light-theme decision (ADR-005) pays for.

Two consequences that are easy to get wrong:

- **The start destination is captured once**, with `rememberSaveable`, rather than read from the
  shell state on each recomposition. Finishing onboarding flips `onboardingComplete`, and a
  `NavHost` whose `startDestination` changes underneath it rebuilds its graph and throws the
  back stack away.
- **Onboarding is a one-way door.** Finishing navigates to `HOME` with
  `popUpTo(ONBOARDING) { inclusive = true }`, so Back from Home leaves the app instead of
  walking Abu back through the permission steps. The only way to see onboarding again is a clean
  install or clearing app data — which is exactly what "first run" ought to mean.
