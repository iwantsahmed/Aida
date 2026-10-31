# AGENTS.md — Mira

Read this file before writing any code in this repository. Every rule here has cost someone
a broken build, a wrong number, or a privacy leak. None of them are stylistic preferences.

---

## 1. What Mira is

- A private assistant for exactly one person: **Abu Sayed Ahmed** ("Abu").
- Primary and only target device: **Samsung Galaxy S24 Ultra**, Android 14+.
- Package / applicationId: **`com.rayluxeo.aida`**. Debug builds get `.debug` appended.
- Not a product. No multi-user support, no accounts, no sign-in, no analytics, no crash
  reporting service, no telemetry of any kind.
- She understands **English, Bengali (bn-BD), Romanized Bengali ("Banglish") and mixed
  Bengali-English**. Any of these may appear inside a single utterance.
- Tone: calm, concise, confident, natural, professional, helpful. Never chatty, never cute.

---

## 2. Hard constraint: ZERO MONETARY COST

Nothing added to this project may cost money, now or later.

**Forbidden:**
- Paid libraries, paid SDKs, or libraries with a commercial licence tier required for this use.
- Any service that requires a billing account, a credit card on file, or a subscription.
- Paid hosting, paid backends, paid CI, paid model APIs, paid TTS/STT voices, paid map tiles,
  paid fonts, paid icon sets, paid push infrastructure.
- Any free tier that silently converts to billing on overage.

**Gemini is the single external service and it is optional.** Free tier only, no billing
account, disabled by default, and Mira is fully functional without it. See §4.

Every new dependency must be recorded in `docs/ZERO_COST_AUDIT.md` with its licence and its
cost finding, in the same change that adds it.

---

## 3. Hard constraint: OFFLINE-FIRST

Core functionality works with the radio off and with no Gemini key configured.

- The on-device engine interprets every command Mira can execute today. Gemini is never
  required to reach a working outcome.
- Speech input uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31) — the
  structural offline guarantee. `EXTRA_PREFER_OFFLINE` is advisory and must never be
  treated as a guarantee.
- TTS uses device voices. No remote assets, no network images, and no font fetched at
  runtime — Mira's typeface is **bundled in the APK** (§10), so she looks identical on a plane.
- Storage is local Room + DataStore. There is no sync, no cloud backup, no server.
- If a code path cannot work offline, it is optional and degrades silently, never blocks.

---

## 4. Architecture rules

**Clean Architecture, strictly layered. Dependencies point inward only.**

```
ui / voice / finance  ->  domain  <-  data
        (presentation)   (pure)      (Room, DataStore, Keystore, AlarmManager)
              ai (engines) ---------------^ never; engines depend on domain only
```

- `domain/` is pure Kotlin. No Android imports, no Room, no Compose, no Context, and no import
  of `ai`, `ui`, `voice`, `finance` or `data`. Check it on every change:
  `grep -rn "^import com.rayluxeo.aida.\\(ai\\|ui\\|voice\\|finance\\|data\\)\\." app/src/main/java/com/rayluxeo/mira/domain/`
  must print nothing.
- Where the domain needs the outside world it declares a **port** and an outer layer implements
  it: `domain/nlu` (TextNormalizer, DateTimeParser), `domain/finance/Calculator`,
  `domain/command/CommandInterpreter`, `domain/scheduler/ReminderScheduler`, and every
  `domain/repository` interface.
- `data/` implements `domain/repository/*` interfaces. Nothing outside `data/` knows Room exists.
- **Every state change goes through a use case.** A view model may observe a repository `Flow`
  for a read path — a pass-through wrapper around `observe()` adds a file and no meaning — but
  anything that writes, validates, schedules or decides belongs in `domain/usecase`. Persisting
  a settings toggle is the single exception, because `SettingsRepository` carries no rules
  beyond the write. A view model never touches a DAO.
- Hilt modules live beside the code they bind (`data/di`, `ai/di`, `voice/di`); `di/` holds only
  the qualifiers and the cross-cutting `AppBindingsModule`.

**Non-negotiable boundaries:**

1. **Gemini may never touch Room.** Gemini is confined behind `CommandEngine`, which can only
   *propose* an `AppCommand`. It has no repository, no DAO, no Context, no file access.
   Every command it proposes passes through `CommandValidator` exactly like a local one.
2. **Financial arithmetic is never done by a language model.** `Money` is `BigDecimal`, never
   `Double`. Sums go through `finance/Calculator`, which is deterministic Kotlin. A model may
   at most hand an expression string to the evaluator.
3. **Voice gender is never inferred from a voice name.** The Android TTS API exposes no gender
   field. Abu picks a voice by listening to it. Never parse `Voice.getName()` for "female",
   "male", "f", a number, or anything else.
4. **`Instant.now()` is never called directly.** Inject `MiraClock`. Relative-time parsing
   ("kal shokal 10 tay") must be testable without wall-clock flakiness.
5. **`java.time` only.** Core library desugaring is on. Never `java.util.Date`, never `Calendar`.
6. **Failures are values.** Return `AppResult.Failure` with a `MiraErrorCode`. Do not throw for
   expected conditions. Human wording never lives in `domain` — `MiraErrorCode` is rendered per
   language by `ui/response/ResponseRenderer`.

---

## 5. The command pipeline (canonical flow)

Every request — typed or spoken, Bengali, English or Banglish — travels exactly this path.
Do not add a shortcut around any stage.

```
raw utterance (voice or text)
  -> TextNormalizer.normalize(raw, LanguageMode)      -> NormalizedText (lowercase Latin,
                                                          ASCII digits, folded Banglish)
  -> CommandRequest(raw, normalized, language, script, source)
  -> ConversationRepository.append(ABU turn)           (Abu's own words, logged verbatim)
  -> CommandRouter.route(request)
        local engine first, always
        Gemini only if (local returned NotUnderstood OR a deferrable greeting) AND gemini.isAvailable()
          a greeting (AppCommand.Greet) comes back Interpreted with deferToCloud = true: offline it
          is the reply, but online Mira hands small talk to Gemini for a real answer (ADR-014)
        if Gemini also misses or errors -> the LOCAL verdict wins (for a greeting, still a real reply)
  -> CommandValidator.validate(command)                (gate for every engine equally)
  -> confirmation gate:
        confirm only if command.isMutating
        AND (confidence < EngineResult.CONFIRM_THRESHOLD OR engine == GEMINI)
        -> CommandOutcome.NeedsConfirmation(pending: ConfirmationRequest)
  -> CommandDispatcher.dispatch(command)               (exhaustive `when`, the only place a
                                                          command becomes an effect)
  -> MiraResponse (structured data, language-free)
  -> CommandOutcome.Completed | NeedsConfirmation | Rejected
  -> ResponseRenderer.speak / .display in Abu's language
  -> MiraTextToSpeech.speak(...) if spoken responses are on
  -> ExecuteCommandUseCase.logMiraReply(rendered text)  (real sentences in the transcript)
```

Rules that fall out of this:

- **Mira never asks permission to look something up.** Read-only commands skip confirmation.
- **The gate applies to the spoken path exactly as it does to the typed one.** A spoken command
  held for confirmation has NOT run: it gets no completion tone and no success state, and the orb
  settles to idle while Mira waits. Claiming a mutating command landed when it did not is the one
  mistake here Abu has no way to notice.
- **A cloud miss must not mask the local verdict.** Abu hears one clear answer.
- Adding an `AppCommand` without a `CommandDispatcher` handler is a compile error. Keep it
  that way — no `else ->` branches over `AppCommand`.
- New commands ship with a handler, a validator rule, a renderer string in both languages,
  and a test. Planned vocabulary lives in `docs/FEATURE_CHECKLIST.md`, never as an empty type.

---

## 6. Verified toolchain — exact pins and why

Do not change a version without re-running the equivalent research. Every pin is load-bearing.

| Component | Version | Why this exact pin |
|---|---|---|
| AGP | 9.4.1 | Has **built-in Kotlin**. Required by Hilt 2.59+ (which refuses AGP 8). |
| Gradle | 9.6.1 | AGP 9.4 minimum and default. |
| JDK | **21** | JDK 25 crashes this project's KSP/Room processing. See §8. |
| Kotlin | 2.3.20 | AGP 9.4.1 only *bundles* KGP 2.2.10; the compose/serialization plugins pull it up to 2.3.20 by conflict resolution. |
| KSP | 2.3.12 | Must track Kotlin. 2.3.12 is built against kotlin-stdlib 2.3.20. **Never 2.3.10** (google/ksp#3047). |
| Hilt | 2.60.1 | **Never 2.59.0** (google/dagger#5099, missing `ComponentTreeDeps`). 2.60+ is the first with stable KSP support and the Kotlin 2.4 metadata fix. |
| compileSdk | 37 | Android 17, stable since 2026-06-16. AGP 9.4's documented ceiling. Stay on **37.0**; 37.1/37.2 are beyond what AGP documents. |
| targetSdk | 37 | Play requires 36+; 37 is opt-in. See the watch list in `docs/ARCHITECTURE.md`. |
| minSdk | 31 | The only value where on-device `SpeechRecognizer`, Compose and platform `BiometricPrompt` all work with no runtime branching. |
| jvmTarget | 17 | AGP 9.4 requires JDK 17 bytecode. |
| Core library desugaring | ON | Gives `java.time` at minSdk 31. |
| Compose BOM | 2026.09.00 | With Material 3. |
| Room | 2.8.5 | Latest stable. AGP 9 support is de-facto, not documented by AndroidX. |
| Navigation Compose | 2.10.2 | |
| Lifecycle | 2.11.0 | |
| DataStore Preferences | 1.2.1 | |
| WorkManager | 2.12.0 | **Not on the classpath.** Researched and pinned, then removed rather than shipped unused. Add it back — through the lead — with the first Worker, which is the Automations area |
| kotlinx-coroutines | 1.11.0 | |
| kotlinx-serialization-json | 1.9.0 | |
| androidx.hilt navigation/viewmodel compose | 1.4.0 | |

**On the classpath for tests:** junit 4.13.2, Truth 1.4.5, Turbine 1.2.1, Robolectric 4.17,
androidx.test core + ext, kotlinx-coroutines-test, room-testing.

**Deliberately NOT available — do not add:** `androidx.biometric` (no stable release carries
Class 3 + CryptoObject; use the platform `android.hardware.biometrics.BiometricPrompt`),
OkHttp, Retrofit, Moshi, Gson, Mockito, MockK, Coil, Accompanist.

**Gradle files are owned by the lead engineer.** If you need a dependency, say so in your
report. Do not edit `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts` or
`gradle/libs.versions.toml` yourself.

---

## 7. Toolchain landmines

Each of these silently breaks something. Issue numbers are real; look them up before arguing.

1. **No `org.jetbrains.kotlin.android`.** AGP 9 has built-in Kotlin and applying the standalone
   plugin breaks the build. Keep `org.jetbrains.kotlin.plugin.compose` — AGP does *not*
   auto-apply the Compose compiler from `buildFeatures.compose = true`.
   Compiler options live in the top-level `kotlin { compilerOptions { } }` block, never in
   `android { kotlinOptions { } }`.

2. **AGP 9 has no built-in KSP.** Its POM carries KSP only as a version *constraint*. The
   `com.google.devtools.ksp` plugin is still applied explicitly with a version.

3. **`kspTest` / `kspAndroidTest` are mandatory.** Since KSP 2.3.10,
   `ksp.allow.all.target.configuration` defaults to false, so `ksp(...)` covers only `src/main`.
   Omitting them compiles clean and then fails at *runtime* with
   `ClassNotFoundException: ..._TestComponentDataSupplier` (google/ksp#3050). Both are already
   declared in `app/build.gradle.kts`. Do not remove them.

4. **NO HILT IN LOCAL UNIT TESTS.** Hilt's local-test bytecode transform does not run under
   AGP 9 (**google/dagger#5261**, open, no fix). `hilt { enableTransformForLocalTests = true }`
   has no effect. Under `app/src/test` there must be:
   - no `@HiltAndroidTest`
   - no `HiltTestApplication`
   - no `@AndroidEntryPoint` activity driven by Robolectric

   Construct classes directly with hand-written fake collaborators. **This is not negotiable.**
   Hilt instrumentation tests under `app/src/androidTest` are fine — `MiraTestRunner` is set up
   for them.

5. **Room entities must be plain data classes with no overridden properties.** KSP 2.3.10+ drops
   `@ColumnInfo(name = ...)` on an `override` property that also carries a `@property:`-targeted
   annotation (**google/ksp#3047**). It is a silent miscompile: the column gets the Kotlin
   property's name and surfaces later as `IllegalStateException: Migration didn't properly
   handle`. No base classes, no interfaces with properties, on entities.

6. **Never put `@Parcelize` on a Room entity.** It fails KSP type resolution under AGP 9 with
   `[MissingType]: Element ... references a type that is not present` (**google/ksp#3053**, open).

7. **`java.time` is desugared and available.** Never `java.util.Date`, never `Calendar`.

8. **Package visibility:** enumerating TTS engines or recognition services needs `<queries>`
   entries in the manifest. Without them `getEngines()` silently returns a truncated list.
   Never request `QUERY_ALL_PACKAGES`.

9. **Never call `super.onReceive()` in a Hilt `BroadcastReceiver`.** The Hilt Gradle plugin
   rewrites the class to extend its generated base and injects that call into the bytecode
   itself (`@OnReceiveBytecodeInjectionMarker`). Writing it in Kotlin source resolves against
   `BroadcastReceiver`'s *abstract* member and fails to compile with
   `Abstract member cannot be accessed directly`. `ReminderReceiver` and `BootReceiver` both
   carry a comment saying so; leave it there.

10. **Compose opt-ins are project-wide, in `app/build.gradle.kts`.** Material 3's bottom sheets,
    date pickers and time pickers are all still experimental and Mira uses all three, so
    `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api` is set once in
    `freeCompilerArgs`. Do not scatter `@OptIn` across screens.

11. **`StateFlow.collect()` with no argument does not compile.** `StateFlow` declares
    `collect(collector)` as a member, which shadows the no-arg terminal extension. In tests that
    keep a `WhileSubscribed` flow hot, write `state.collect { }`.

12. **One grammar, one source of truth.** `ArithmeticExpression` (which decides *whether* a
    sentence holds a sum) reads its vocabulary from `finance/CalculationVocabulary` (which
    decides how it is *worked out*). They were once two hand-maintained lists, and the narrower
    one quietly truncated `12450 * 3 + 7.5% of 2400` to `12450 * 3 + 7.5` and answered with
    confidence. Never reintroduce a second copy of that vocabulary.

---

## 8. Data rules

The schema is 35 tables at database version 2. `docs/DATA_MODEL.md` describes it; these are the
rules that must not be broken while working on it.

- **Money is never a `Float` or a `Double`**, at any point, including an intermediate expression
  or a test assertion. On disk an amount is a whole number of minor units in a `Long` beside the
  owning record's `currencyCode`. Convert only through `domain/model/MoneyAmount.kt`. Quantities
  are stored at three decimal places, rates in basis points (1500 is 15.00 percent).
- `MoneyAmount` REFUSES an amount finer than its currency can express rather than rounding it.
  Do not "fix" that by adding a RoundingMode.
- **No destructive migration, ever.** `fallbackToDestructiveMigration` must not appear anywhere.
  A schema change ships with a written migration or it does not ship.
- A migration is GENERATED from Room's exported DDL in `app/schemas/`, never retyped. A migration
  that disagrees with the generated schema by one column type passes every test that does not open
  a real old database, then fails on the one phone that has the data.
- Every read query on a table with `deletedAtEpochMillis` filters `deletedAtEpochMillis IS NULL` —
  lists, counts and FTS joins alike. The one deliberate exception is a repository's `byId`, which
  reads a row whatever its state so restore can find it; those ports say so in their KDoc.
- **Cascade rules are not uniform, and the differences are load-bearing.** CASCADE only where a
  child is meaningless alone. RESTRICT from clients to invoices and quotes. SET NULL wherever a
  record is a fact that must outlive its parent. **A CASCADE from clients, projects or invoices
  onto payments, income or expenses would destroy financial history and must never be added.**
- Anything writing to more than one table, or writing a row and then updating a derived counter or
  total, is `@Transaction` on an abstract DAO class.
- FTS4: the index is external-content, Room maintains it by trigger, and a search joins
  `fts.rowid = content.id`. Two traps — the FTS table must NOT be aliased in a `MATCH` (the match
  operand is a hidden column named after the table), and the soft-delete filter goes on the
  CONTENT table because the index shadows deleted rows too.
- Migration tests are INSTRUMENTED, not local: `MigrationTestHelper` opens a real database file and
  Robolectric's FileDescriptor interception cannot serve one on JDK 21.

---

## 9. Building and running locally

**Do not run Gradle while other agents are editing the project.** A second daemon deadlocks or
corrupts the build. The lead engineer compiles centrally.

When you are the one building:

```sh
# The exact JDK 21 this machine already has; `java` is not on PATH and Android Studio's
# bundled JBR is 25, which KSP/Room cannot use.
export JAVA_HOME="$HOME/.gradle/jdks/jetbrains_s_r_o_-21-aarch64-os_x.2/jbrsdk_jcef-21.0.11-osx-aarch64-b1163.116/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # 364 local tests
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest  # needs a device or a booted emulator
```

There is no physical S24 Ultra attached to this machine today, so device verification runs on the
`Pixel_6` AVD (API 37, arm64). Boot it with:

```sh
"$ANDROID_HOME"/emulator/emulator -avd Pixel_6 -no-snapshot-load -no-boot-anim &
```

The debug build installs as `com.rayluxeo.aida.debug` (`applicationIdSuffix`), so it can sit
alongside a release build. Launch it with
`adb shell am start -n com.rayluxeo.aida.debug/com.rayluxeo.aida.MainActivity`.

- `~/.gradle/gradle.properties` pins `org.gradle.java.home` to a JDK 21 **because JDK 25
  crashes KSP/Room**. Leave it alone. If Android Studio overrides it back to JDK 25, fix the
  IDE's Gradle JDK setting rather than the build files.
- The Android SDK lives at `~/Library/Android/sdk`. `local.properties` already points there and
  is git-ignored — never commit it.
- Logcat, filtered to Mira:
  `adb logcat --pid=$(adb shell pidof -s com.rayluxeo.aida.debug)`
- Room's exported schemas land in `app/schemas/` and **are committed**.

---

## 10. Design rules

AMOLED-dark only. There is no light theme and `MiraTheme` deliberately ignores the system setting.

| Token | Hex | Use |
|---|---|---|
| Obsidian | `#080808` | app background, status/nav bar |
| Carbon | `#101013` | surfaces, cards |
| Graphite | `#191A1E` | raised surfaces, chips |
| Accent | `#A6C8E4` | the single accent — a restrained **ice-blue** |
| Platinum | `#F1F3F7` | primary text |
| Muted | `#878B93` | secondary text |

The accent used to read "Champagne `#D8BD8A`" and the text "Ivory `#F4F1EA`". The implemented
palette moved to a cool, near-monochrome **ice-blue** accent on platinum text — premium on a black
screen comes from cool neutrals and precise hierarchy, not a warm metallic. `MiraColors.kt` is the
source of truth, and this table now matches it; the derived tones there are `AccentDim`,
`AccentWash`, `Hairline`, `HairlineStrong`, `Scrim`, `Danger`, `Positive`, plus the `Aurora*` hues
(§10's one sanctioned purple). The one place warm champagne survives is the printed invoice
(`InvoicePdfGenerator`), where gold on white reads as a studio letterhead — on paper, not on screen.

- All colours come from `MiraPalette` / `MiraTheme.colors`. No screen invents a colour.
- All spacing comes from `MiraTheme.spacing`. No ad-hoc `dp` values.
- Generous spacing, hairline borders (`MiraTheme.colors.hairline`), minimal icons, subtle
  motion, restrained haptics.
- **Icons are the Solar family (CC0), bundled as vector drawables and reached through `MiraIcons`**
  (set 9 October 2026). Never write `Icons.Outlined.*`/`Icons.Filled.*` again — use `MiraIcons.<Name>`
  (Linear, the resting weight) and `MiraIcons.Filled.<Name>` (Bold, for a selected tab or an active
  toggle). `MiraIcons.<Name>` is a `@Composable` accessor, so a `when` that returns one must itself
  be `@Composable`; an enum or data class that needs an icon stores a `@DrawableRes Int` and resolves
  it with `ImageVector.vectorResource(…)` at render (see `MiraTab`/`MiraArea`). A new icon is added by
  dropping its Solar drawable in `res/drawable/ic_*.xml` and an accessor in `MiraIcons` — not by
  pulling in a Material icon.
- **Two bundled OFL faces, one per script, paired by weight in `MiraFont.kt`:** **Manrope** for
  Latin (`res/font/manrope_*.ttf`) and **Noto Serif Bengali** for Bangla
  (`res/font/noto_serif_bengali.ttf`). This rule used to read "system sans only, no downloaded
  fonts", then "one family, Anek Bangla, for both scripts". Abu changed it again on **9 October
  2026**: Bangla is now a **serif** (Noto Serif Bengali), used for Bangla and nothing else — a
  quiet counterpoint to the Latin grotesque. Typography is the largest single lever on whether the
  app feels premium, which is why it has earned deliberate change.

  The two reasons behind the original rule still stand and are met, not abandoned:

  1. **Offline.** Both faces are bundled, never fetched. Nothing about them needs a network.
  2. **Bengali, with REAL per-glyph fallback.** Manrope carries no Bengali. The trap learned the
     hard way (9 Oct 2026): a Compose `FontFamily` that merely *lists* a second bundled font per
     weight does **not** fall through to it for a missing glyph — it draws the first font and lets
     the **system** face cover the rest, so a bundled Bengali face listed second never rendered and
     Bangla silently came out in the device's own sans. `MiraFont.kt` instead hands Compose a single
     `android.graphics.Typeface` built with `Typeface.CustomFallbackBuilder` (API 29; minSdk is 31):
     Manrope is the primary at every weight, Noto Serif Bengali is the explicit fallback. Bangla is
     then always the serif, Latin always Manrope — even inside one Banglish word — and never the
     system face. The type scale is built from that typeface in `MiraTheme` via
     `miraFontFamily(context)`, so it needs a `Context`; do not reintroduce a top-level `FontFamily`
     constant and do not go back to listing fonts in a `FontFamily`, which is the thing that failed.
     Coverage was VERIFIED rather than assumed: every Bengali codepoint appearing anywhere in Mira's
     own source was checked against the font's `cmap`, including the digits ০–৯ and the taka sign ৳,
     both of which she prints. **Any replacement Bangla face must pass that same check before it
     lands**, or an invoice total becomes tofu.

  No third family may be added, and Noto Serif Bengali is for Bangla only — never set it, or any
  `fontFamily`, at a call site. The type scale in `MiraTypography.kt` already carries the pairing.

**Never:** purple AI gradients, glassmorphism, cartoon clutter, emoji in UI copy, crowded
screens, neon, drop shadows used as decoration.

**The one sanctioned aurora.** Abu reversed the "no purple AI gradients, no neon" half of the
line above on **16 October 2026**, for exactly one surface: `MiraAmbientBackground`, the living
Gemini-style backdrop behind Mira's presence screens (Home, the assistant screen, onboarding). It
is a purple→magenta glow and a halftone dot field that breathe low behind the content — and it is
the *only* place purple is allowed. Every control, state, icon and letter stays ice-blue; the
accent is unchanged. Its hues live in `MiraPalette.Aurora*` (read through `MiraTheme.colors`), never
invented at a call site, and are never used as a UI accent, a text colour or a fill. It is still
drawn in code (no video, no image, §3), it still re-tints with the palette, and the hot core sits
low so the greeting and the mark above it keep their contrast. Nothing else here is relaxed: the
rest of the "Never" line stands.

**Mira has a face.** This rule used to read "no mascots, no cartoon graphics". Abu reversed that
on **9 October 2026**: Mira is now a character — a rounded figure in round glasses, `MiraAvatar`,
drawn in code — who shows an expression for each of her states and takes a different accent colour
each time she appears. She is the one sanctioned "mascot", and the ban above now means *other*
cartoon decoration, not her. Two things did not change with her: she is still drawn entirely in
code (no raster asset, identical offline), and her **tone stays calm, concise and professional**
(§1) — the warmth is in her face, never in chatty or cute copy. She appears where a presence
belongs — onboarding, the assistant overlay, the app icon — not scattered across every screen.

---

## 11. The design system

Ten files under `core/design/`, plus a component library under `core/design/component/`.
A screen **composes** from them. It never declares a colour, a dp value, a text style or a
duration of its own.

| File | Owns |
|---|---|
| `MiraColors.kt` | `MiraPalette` — the six tokens in §10 plus the derived tones (`AccentDim`, `AccentWash`, `Hairline`, `HairlineStrong`, `Scrim`, `Danger`, `Positive`) and the `Aurora*` hues — `MiraExtendedColors` for what Material has no slot for, and the `MiraColorScheme` the rest map into |
| `MiraFont.kt` | The bundled variable family: `MiraSans`, and `MiraSansDisplay` — the same face drawn a few percent narrower for large type, which is what makes a heading look *set* rather than merely enlarged |
| `MiraTypography.kt` | The Material `Typography` at Mira's sizes, weights and tracking. Tracking tightens as type grows; line heights are set from the Bengali side, which needs room above and below |
| `MiraSpacing.kt` | The rhythm: `hairline`, `xxs`…`xxl`, plus `screenHorizontal`, `sectionGap`, `cardPadding` |
| `MiraShapes.kt` | `ContinuousRoundedCornerShape` — **continuous curvature**, not a quarter-circle arc — and the radii, 10dp to 36dp. A circular corner beside one of these is visible; never use `RoundedCornerShape` again |
| `MiraMotion.kt` | Every duration and easing, and the spring helpers. **Anything that moves uses a spring; anything that fades uses a curve** — `move()`/`moveGently()`/`track()` against `fade()` |
| `MiraSurfaceLight.kt` | `Modifier.miraRaised` — the specular top edge that gives a surface depth on a black screen, where a shadow has nothing to cast onto |
| `MiraFadingEdges.kt` | `Modifier.fadingHorizontalEdges` — the soft "there is more, swipe" fade on a scrolling chip or tab row, present at an edge only while that edge can still scroll. Every single-line filter/tab row (the Money tabs, the Tasks/Notes/Files/Reports/Activity/Memory/Scanner filters) wears it, so overflow is never invisible again |
| `MiraHaptics.kt` | `rememberMiraHaptics()` and `LocalHapticsEnabled` |
| `MiraTheme.kt` | Wires all of the above into `MaterialTheme` and exposes `MiraTheme.spacing` / `MiraTheme.colors` |

`MiraShapes` and `MiraTypography` each live in **their own file**. One public concept per file
(§13), and sharing `MiraTheme.kt` hid two things that are edited independently.

`MiraMotion` is a **plain object**, not a composition local. An animation spec never varies by
where it is read from, so a local would buy nothing and would make motion unusable outside
composition. Do not convert it. See ADR-010 in `docs/ARCHITECTURE.md`.

Where a token is read from:

- Colour → `MaterialTheme.colorScheme`, and `MiraTheme.colors` only for the things Material has
  no slot for (`hairline`, `hairlineStrong`, `accentWash`, `accentDim`, `danger`, `positive`).
- Spacing → `MiraTheme.spacing`. Type → `MaterialTheme.typography`. Shape → `MaterialTheme.shapes`.

### 10.1 Motion

| Constant | ms | For |
|---|---|---|
| `INSTANT_MS` | 90 | a state flip with no travel — a tint, a checkbox, a selected chip |
| `QUICK_MS` | 140 | small movement over a short distance — a press, a row expanding, an indicator sliding |
| `STANDARD_MS` | 200 | the default: content fading, a sheet settling, a segment moving |
| `EXPRESSIVE_MS` | 320 | something entering from off-screen, or a deliberate reveal such as the vault unlocking |
| `AMBIENT_MS` | 2600 | continuous idle motion, meant to be felt rather than watched |

Easings: **`Enter`** (decelerating — for things arriving; the settle at the end is what reads as
quality), **`Exit`** (accelerating — things leaving must not linger), **`Standard`** (symmetric,
for a value moving between two states while staying on screen), **`Linear`** (even, for
continuous motion such as a waveform).

Helpers: `instant()`, `quick()`, `standard()`, `enter(durationMs = EXPRESSIVE_MS)`,
`exit(durationMs = QUICK_MS)`, and `settle()` — the one spring in the system, critically damped
because an overshoot on a champagne accent looks like a toy.

**Never call `tween()` with a literal duration.** A number typed at a call site cannot be
changed centrally and matches nothing else on screen.

### 10.2 Haptics

`rememberMiraHaptics()` gives `tap()` (a light tick), `confirm()` (something Abu asked for
landed) and `error()` (Mira could not do it — deliberately distinct, so the hand knows before
the eye). All three already honour Abu's setting through `LocalHapticsEnabled`, so call them
without checking anything first. No screen calls `performHapticFeedback` itself.

### 10.3 Component inventory

Twenty-seven components. Read the signature before calling one; do not reimplement any of them.

| Component | What it is for |
|---|---|
| `MiraSkeleton` (`MiraListSkeleton` / `MiraSkeletonBox` / `MiraSkeletonLine` + `rememberMiraShimmer`) | The loading placeholder, shaped like the content that is coming, shimmering gently — shown for a screen's INITIAL load instead of a spinner (16 Oct 2026). `MiraListSkeleton` is the drop-in for any list/content screen; a bespoke one (e.g. Home's) is built from the box/line atoms sharing one `rememberMiraShimmer` brush. An action already under way is still a `MiraLoadingIndicator` — a skeleton there would lie about a result whose shape is not yet known |
| `MiraScaffold` | The frame every screen sits in. Obsidian edge to edge, one set of content padding with the window insets and the screen gutter already folded in. An optional `background` slot draws a full-bleed layer behind the content — where `MiraAmbientBackground` lives on the presence screens — and turns the container transparent so it shows through |
| `MiraAmbientBackground` | The living aurora behind Mira's presence screens (Home, the assistant screen, onboarding), added 16 Oct 2026 at Abu's request. Purple→magenta glows and a halftone dot field, drawn in code, breathing low on the shared frame clock and lifting with `amplitude`/`active` like the waveform. The one sanctioned purple (see §10); its hues are `MiraPalette.Aurora*` and nothing else uses them |
| `MiraTopBar` | A title and at most one control either side. No elevation, no surface tint, no scroll-collapse |
| `MiraNavigationBar` + `MiraNavigationBarItem` | The floating bottom pill. Selection is a soft highlight that slides and stretches between icons, not a filled bar. The pill is **translucent** (16 Oct 2026) so the shell's aurora reads through it on the presence screens. Knows nothing about routes — it takes items and reports taps |
| `MiraCard` | Carbon on obsidian, separated by a hairline rather than a shadow. Champagne ripple when it is clickable |
| `MiraListRow` | One line of a list: leading element, the words, trailing element. Generous height |
| `MiraButton` | The button. `MiraButtonStyle.PRIMARY` / `SECONDARY` / `TEXT`. One component, because the difference is emphasis and not behaviour (ADR-009) |
| `MiraIconButton` | A bare glyph in a 48dp target — minimal icons only stay minimal if they are not also hard to hit |
| `MiraTextField` | Graphite field, hairline border, champagne only while the caret is in it |
| `MiraSearchField` | A field shaped for search: leading glyph, clear action, IME search action |
| `MiraChip` | A small pill for a filter or a label. Selected fills with the wash, never solid champagne |
| `MiraDivider` | A true hairline, not Material's device-pixel grey rule |
| `MiraSectionHeader` | Groups a list without drawing a box round it. Upper-cased for Latin; Bengali passes through unchanged |
| `MiraEmptyState` | An empty list is a normal state, not a fault: dim glyph, one calm line, an optional way forward |
| `MiraErrorState` | Something failed: what happened, an optional detail, and a retry only when retrying is real |
| `MiraLoadingIndicator` | One thin champagne arc on a hairline track. Nothing that suggests the app is struggling |
| `MiraSegmentedControl` | Two or three exclusive options, the indicator sliding between them |
| `MiraBottomSheet` | A modal sheet in Mira's own surfaces, with a hairline handle instead of Material's grey pill |
| `MiraDialog` | A decision that has to be made now. `destructive = true` colours the confirm with `danger` |
| `MiraConfirmationCard` | The pipeline's confirmation gate, inline in the conversation: the question, an optional detail, confirm and cancel |
| `MiraStatusIndicator` | `MiraStatus.IDLE` / `ACTIVE` / `SUCCESS` / `WARNING` / `ERROR` with a label |
| `MiraWaveform` | The signature element: a flowing ribbon of fine light that rises into two hills flanking Mira and dips to a valley where she sits, driven by live amplitude while listening and drifting slowly when idle. Many thin strands, not bars — the crests brighten where the fanned strands overlap |
| `MiraAvatar` | Mira herself AND her voice control — a rounded character in round glasses, drawn entirely in code (no asset, identical offline). Her body takes a different `MASCOT_PALETTE` colour each appearance; her glasses and eyes stay ivory. Each `MiraAvatarState.IDLE` / `LISTENING` / `PROCESSING` / `EXECUTING` / `SPEAKING` / `SUCCESS` / `ERROR` is an **expression** — wide eyes while listening, a glance-away while thinking, a happy squint on success, small lowered eyes on error — smoothed toward, not snapped to, over one frame clock. `amplitude` makes her pulse while listening. A face now, deliberately (see §10, 9 Oct 2026); replaced the earlier sphere, which replaced `MiraVoiceOrb` |
| `MiraMountainHero` | The gold-lit mountain behind Home's greeting — drawn from the palette, not a photo, so it is offline, retints with the theme, and needs no licence |
| `MiraInlineNotice` | A transient line above the content, with an optional action and a dismiss. `MiraNoticeTone.INFO` / `DANGER` / `CONFIRM` |
| `MiraPreviewSurface` | Puts an `@Preview` on obsidian, so an AMOLED-dark library can actually be judged |

A screen that needs something the library does not have adds it **to the library**, with a
`@Preview`, rather than building it inline. A one-off composable living in a screen file is how
a design system stops being one. `MiraInlineNotice` exists because five screens had each grown
their own copy of the same dismissible banner and they had already drifted.

Note the boundary between the two failure components, because it was got wrong once:
`MiraErrorState` owns a whole screen when its content could not load and offers a retry;
`MiraInlineNotice` sits above content Abu is already reading, says one thing and can be waved
away. `MiraErrorState` has no call site yet — no screen currently fails hard enough to need it,
since they all degrade to an empty list plus a notice. It stays in the library as the correct
component for that case rather than being forced into a slot it does not fit.

---

## 12. Quality bar

A change is not done if any of these are true:

- A button exists that does nothing, or does something fake.
- A screen is reachable but empty, or unreachable but present. (One governed exception: §11.1.)
- A click target has no handler, or a handler that only logs.
- A TODO comment stands in for a production feature.
- A stub function returns a hard-coded value pretending to be real data.
- Seed, sample, demo or placeholder data ships in a release path.
- Navigation dead-ends: no back, wrong back, or a route with no destination.
- An error state is unhandled, or shown as a raw exception message.
- A string is hard-coded in one language on a user-facing path.
- Dead code, unused parameters "kept for later", or commented-out blocks remain.

If a feature cannot be finished properly, do not ship a shell of it. Leave it out and record
it in `docs/FEATURE_CHECKLIST.md` as planned.

### 11.1 The one exception — a declared, unbuilt area

Mira routes to nine areas that have no feature behind them yet, and each renders
`PlannedAreaScreen`. That is a deliberate decision, not an oversight: the alternative is a hub
whose rows do nothing, which is the defect this section exists to forbid.

The exception is narrow and it holds only on all four of these terms:

1. It covers **only** an area declared in `MiraArea` with `isBuilt = false`. Nothing else in the
   app may claim it.
2. The screen names the area, says plainly that it is not built, lists what it will do when it
   is, and offers **at least one action that genuinely works** — handing the request to Mira,
   or going back to the hub.
3. It never shows fabricated data, never implies a date or a version, and never looks like an
   error. It is a statement of scope, not a failure.
4. The moment the area ships, `isBuilt` flips to `true` and the exception stops applying to it.
   There is no second grace period, and no "built enough".

Everything else in this section stands unchanged. This is **not** licence for a stub screen, a
dead button, a handler that only logs, fake data, or a TODO standing in for a feature.

---

## 13. House style

- 4-space indent. Trailing commas in multi-line argument lists. Explicit imports, never `*`.
- KDoc on public types and non-obvious public functions. KDoc explains **why** something exists
  or what invariant it protects — not what the next line literally does.
- Inline comments are rare and earn their place. Do not narrate code.
- Prose is plain and concrete. Where a comment needs to name the user, he is "Abu".
- Constructor injection with `javax.inject.Inject`. No field injection outside Android entry points.
- `suspend` for one-shot work, `Flow` for streams. No `GlobalScope`, no `runBlocking` in
  production code.
- One public concept per file; a file is named after it.

---

## 14. Per-prompt working agreement

Every session, in this order:

1. **Inspect the code** that the task touches. Read the contracts, not just the prompt.
2. **Read this file** and the relevant doc under `docs/`.
3. **Implement**, respecting §4–§7.
4. **Add tests.** Plain JUnit + Truth + Turbine, hand-written fakes, **no Hilt** (§7.4).
5. **Build.** `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
6. **Fix every build error.** A red build is never handed on.
7. **Run on the device or emulator.** Actually exercise the change.
8. **Inspect Logcat.** Read it; do not skim for the word "error".
9. **Fix runtime errors**, including ones that do not crash.
10. **Update `docs/FEATURE_CHECKLIST.md`** to match what is now genuinely true.
11. **Report** changed files (absolute paths) and test results, honestly — partial work is
    reported as partial.

---

## 15. Where the rest of the documentation is

| File | Contents |
|---|---|
| `docs/ARCHITECTURE.md` | Layering, module strategy, command pipeline, engine routing, decision records |
| `docs/DATA_MODEL.md` | Domain models, Room schema, encryption, migration policy |
| `docs/PRIVACY.md` | Where Abu's data lives, what is encrypted, what leaves the device, permissions |
| `docs/ZERO_COST_AUDIT.md` | Per-dependency licence and cost findings, review rule |
| `docs/FEATURE_CHECKLIST.md` | Living tracker: what ships, what is planned |
