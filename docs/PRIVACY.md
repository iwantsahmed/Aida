# Privacy

Mira is a private assistant for one person on one device. This document states exactly where
Abu's data lives, what protects it, what leaves the device, and what never does.

There is no server, no account, no sync, no analytics SDK, no crash reporter and no telemetry.
Nothing in this project reports to anyone.

---

## 1. Where the data lives

Everything is in the app's private storage on the S24 Ultra, under
`/data/data/com.rayluxeo.aida/` (`.debug` suffix on debug builds), readable only by the app's
own UID and protected at rest by the device's file-based encryption.

| Data | Store | Form |
|---|---|---|
| Tasks | Room — `tasks` | plaintext |
| Reminders | Room — `reminders` | plaintext |
| Conversation transcript | Room — `conversation_turns` | plaintext |
| Vault item secrets | Room — `vault_items.secret_cipher` | **AES-256-GCM ciphertext** |
| Vault titles, categories, timestamps | Room — `vault_items` | plaintext (see §3) |
| Settings, voice profile, flags | DataStore Preferences — `mira_settings` | plaintext |
| Gemini API key | DataStore Preferences — `mira_credentials`, separate file | **AES-256-GCM ciphertext** |
| Encryption keys | **Android Keystore** | never leave the Keystore; hardware-backed on this device |

There is no external-storage path, no cache directory holding user content, no exported
`ContentProvider`, and no `FileProvider`. Nothing is written to `getExternalFilesDir()`.

---

## 2. Encryption

**Algorithm:** AES-256-GCM. **Key storage:** Android Keystore (`AndroidKeyStore` provider),
hardware-backed on the S24 Ultra. Keys are generated on first use, never exported, never
derived from anything in the APK, and never written to disk in any form the app can read.

Two separate keys, two separate purposes:

| Alias | Protects | Notes |
|---|---|---|
| vault key | `vault_items.secret_cipher` | May require user authentication to use — see below |
| credential key | the Gemini API key in `mira_credentials` | No user-auth requirement: Mira needs it without prompting |

Rules:

- A **fresh random 12-byte IV per write**, including every update. The IV is stored alongside
  the ciphertext in `secret_iv`. An IV is never reused with the same key.
- The GCM authentication tag is kept with the ciphertext, so a tampered or truncated row fails
  to decrypt rather than yielding garbage.
- `key_alias` is stored per vault row, so the key can be rotated without a schema change.
- Decryption failure returns `AppResult.Failure(MiraErrorCode.AUTHENTICATION_FAILED)`. It never
  crashes, and it never falls back to handing ciphertext to the UI as though it were text.
- **Biometric enrolment changes invalidate an auth-bound key.** A key created with
  `setInvalidatedByBiometricEnrollment(true)` stops working the moment a fingerprint is added,
  which makes every existing row permanently unreadable. That is a defensible posture, but it
  must be a conscious choice and Abu has to be told before he stores anything he cannot obtain
  again.

**The decision as it actually stands.** `MiraKeystore` creates the vault key with
`setUserAuthenticationRequired(false)`. Vault unlock uses the **platform**
`android.hardware.biometrics.BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`
(`androidx.biometric` is deliberately not a dependency — ADR-001 in `docs/ARCHITECTURE.md`), and
that prompt gates the **reveal screen**, not the key.

Stated honestly, the vault's strength today is: a secret is AES-256-GCM ciphertext under a
hardware-backed Keystore key, unreadable by any other app and unreadable if the file is copied
off the device; and the biometric prompt in front of `reveal(id)` is an application-level gate
rather than a cryptographic one. Binding the key to the prompt with a `CryptoObject` would close
that gap, and it is carried as an open item in `docs/FEATURE_CHECKLIST.md`. It is written down
here because an undocumented gap is the one that gets mistaken for a guarantee.

---

## 3. What is deliberately *not* encrypted, and why

**Vault titles are not secrets.** `title`, `category` and the timestamps in `vault_items` are
stored in plaintext because `VaultRepository.observeSummaries()` has to list and sort the vault
without unlocking it. Encrypting titles would mean a biometric prompt just to see a list of
names.

The consequence must be surfaced in the UI at the point of entry: *"Gmail recovery code"* is a
fine title; the code itself belongs in the secret field.

**The conversation transcript is not encrypted.** It is ordinary app data behind the app
sandbox, the device lock and `allowBackup="false"`. If something sensitive is ever spoken into
it, `ConversationRepository.clear()` removes it. The vault is the place for secrets, not the
transcript.

**Settings are not encrypted.** A language preference and a TTS voice name are not secrets. The
Gemini key is kept in a *separate* DataStore file precisely so it is never dumped together with
them.

---

## 4. What leaves the device

**By default: nothing.** With Gemini disabled — which is the shipped default — Mira makes no
outbound network request of any kind. No fonts are downloaded, no images are fetched, no
manifest is checked, no update is polled for.

When Abu explicitly enables Gemini **and** a key is present, exactly one thing can leave:

> **The text of a single utterance**, at the moment the on-device engine failed to understand it.

Bounded precisely by `CommandRouter`:

- The local engine runs first on every utterance. If it produces an `Interpreted` result,
  **nothing is sent**.
- Gemini is called only after a local miss, and only when `gemini.isAvailable()` — key present,
  feature enabled, reachable.
- What is sent is the one `CommandRequest` for that utterance. Nothing else accompanies it.

**What is never sent, under any configuration:**

- The database. Not tasks, not reminders, not the vault, not the transcript, not a summary, not
  an embedding, not a count. `CommandEngine` cannot reach a repository — it has no DAO, no
  `Context` and no file access. The boundary is structural, not a promise.
- Vault secrets, vault titles, or anything derived from them.
- The conversation history. Each request is a single utterance with no prior turns attached.
- Any identifier: no device id, no advertising id, no `ANDROID_ID`, no account, no email, no
  installation id, no user agent carrying a name.
- Audio. Recognition is on-device (`createOnDeviceSpeechRecognizer`); Mira never uploads a
  recording.
- Location, contacts, calendar, SMS, photos or files. SMS, photos and files Mira cannot read at
  all — she holds no permission for them. **Location, contacts and calendar she now can read**,
  and the reason none of it reaches Gemini is structural rather than a missing permission:
  `CommandEngine` receives one normalised utterance and nothing else. It has no repository, no
  DAO, no `Context` and no file access, so there is no path by which a coordinate or a contact
  could be attached to a request even by mistake. That boundary is the guarantee; the permission
  list stopped being one the moment a feature needed the permission.

**What comes back** is a proposed `AppCommand`, which is then validated by
`CommandValidator` and **always** confirmed with Abu before it is allowed to change anything
(`engine == GEMINI` forces confirmation regardless of claimed confidence). Gemini cannot cause a
write on its own.

**Speech model downloads** are a separate matter worth knowing about: `triggerModelDownload` is
executed by the system `RecognitionService` in Google's own process, not Mira's. Any traffic it
generates is attributed to Google Speech Services, and Mira sends nothing as part of it.

---

## 5. What is never logged

- **Vault secrets — plaintext or ciphertext.** Never in a log, never in a stack trace, never in
  a `toString()`. Any vault type whose `toString()` could expose `secret` overrides it.
- **The Gemini API key.** Never logged, never put in a URL or query string, never included in an
  error message, never echoed back in the UI after entry.
- **Anything Abu said or typed**, in release builds. The transcript is the deliberate record of
  that; Logcat is not.
- **Request or response bodies** of the Gemini call.

`MiraError.detail` is developer-facing and may be logged, so it must carry a *description* of a
fault, never the data that caused it: `"Vault row 14 failed to decrypt"`, not the row.

Release builds run R8 with `isMinifyEnabled = true`. Debug logging must be compiled out or
guarded by `BuildConfig.DEBUG` — not merely set to a quiet level.

---

## 6. Backup and extraction posture

On disk today in `AndroidManifest.xml` and `res/xml/`:

```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

`backup_rules.xml` excludes `root`, `database`, `sharedpref` and `file`.
`data_extraction_rules.xml` excludes the same four domains from **both** `<cloud-backup>` and
`<device-transfer>`.

So: nothing goes to Google Drive backup, nothing goes to device-to-device transfer, and
`adb backup` returns nothing. A Keystore-sealed secret would be unreadable off-device anyway,
but the data never leaves in the first place.

**The trade Abu is making:** there is no backup. A lost, wiped or broken phone loses the
database. That is the intended posture for a private assistant, and it is why
`docs/DATA_MODEL.md` §4 forbids destructive migrations — the on-device database is the only
copy. If an export feature is ever added, it must be an explicit, user-initiated action that
writes to a location Abu chose, and it must re-encrypt vault secrets rather than exporting them
in the clear.

---

## 6.5 What the assistant role can and cannot see

Mira holds the Android assistant role, which is the most privileged thing she does. What that
actually grants is narrower than it sounds, and the limits are the point.

**What Android hands her, and only when Abu allows it.** When the assist gesture fires, the system
may pass a structure of the current screen and a screenshot. Both are governed by
`assist_structure_enabled` and `assist_screenshot_enabled`, which **default to `0`** — verified on
device, not assumed. An app that sets `FLAG_SECURE` withholds its window regardless. Mira treats
every one of these as an *absence*: `AssistContextReader` returns an empty snapshot and she says
she cannot see the screen, rather than treating a refusal as an error to work around.

**What she never does.** She does not read the screen by any route. The one accessibility service
she can hold is opt-in and off by default (§6.7), and it is declared
`canRetrieveWindowContent="false"` — it can press a system button but has **no capability to read
window content**, so "never reads the screen" stays true with it on. There is no media projection
and no screenshot API of her own. Password and credential fields are skipped when flattening the
assist structure.

**What leaves the device: nothing, without being asked first.** Screen text and screenshots are
not sent anywhere. Content another app hands her through the share sheet is marked
`InputSource.SHARED`, and `CommandRouter` **refuses the cloud engine for it outright** — Abu did
not write it and has not been asked whether it may leave, and a share sheet offers no moment at
which to ask. A local miss is the honest answer there, even though it is a worse one. The rule
lives at the routing seam because that is the single place every engine choice passes through.

**The microphone.** A session listens only when Abu started it. Verified on device with `appops`:
after an assistant session opened and was dismissed, `RECORD_AUDIO` showed a total duration of
23 ms and no further access. There is no always-on wake word — `AlwaysOnHotwordDetector` is not in
the public SDK at all, which is checkable with `javap` rather than taken on trust — and nothing in
Mira holds a microphone open in the background.

## 6.6 What appears outside the app — the home-screen glance

The "Mira — Today" widget draws Abu's day on the launcher's home screen: task counts, the next
event and reminder, and — unless suppressed, below — the outstanding and overdue money. This is the
one place Mira's data is rendered **outside her own window**, in the launcher's process, and so
outside the Money/Invoices app-lock (`protectMoney` / `protectInvoices`, which `AreaLockGate`
enforces only inside the app).

- **The money line is suppressed when either vault flag is on.** `WidgetRefreshWorker` reads the
  settings and passes `includeMoney = !(protectMoney || protectInvoices)` to `WidgetGlanceText`,
  which then omits the figure entirely. A balance the lock exists to hide is not left sitting on an
  unlocked home screen; the glance still shows tasks and agenda.
- **Nothing leaves the device.** The glance is built from the same on-device `DailyBriefEngine`
  Home uses — Room plus the calendar provider, no network on this path.
- **The provider reads nothing.** `MiraGlanceWidgetProvider` holds no data and never touches Room;
  all reads happen in the worker off the launcher's thread. An unplaced widget does no read at all.

## 6.7 The cross-app automation tier (opt-in, off by default)

Mira can be given two system grants that let an automation reach past her own window. Both are
**off by default**, enabled only by Abu from the "Cross-app automations" settings section and the
system screen each grant lives on, and **Mira is fully functional without either** — the entire
tier is additive. A new stored flag, `crossAppAutomationsEnabled`, is the master switch and
defaults to `false`.

- **The accessibility service is a pure actuator, not a screen reader.** `MiraAccessibilityService`
  exists only so a `PerformGlobalAction` automation can press a system button — back, home, recents,
  the notification shade, quick settings. Its config declares `canRetrieveWindowContent="false"`, so
  it has **no capability to read window content**, and its `onAccessibilityEvent` does nothing. It
  presses the button and sees nothing of the screen.
- **The notification listener reads a title to match a rule, and stores nothing.**
  `MiraNotificationListenerService` fires a `NOTIFICATION_POSTED` automation when another app posts a
  notification. It reads the posting package and the notification's title **only** to decide which
  rules match (the pure `NotificationPosted.matches`); the title is never written to the database and
  nothing about the notification leaves the device. Mira ignores her own notifications, so a rule
  that posts a notification cannot loop.
- **Two gates, both opt-in.** Each service needs its OS grant (granted only from system settings,
  never a dialog) **and** the `crossAppAutomationsEnabled` toggle, so Abu can pause the whole tier
  without revoking the system grants. A rule that needs a grant that is off does not quietly do
  nothing: the create sheet says the grant is off with a link to enable it, and a global action that
  cannot run is recorded as a **failed run**, never a silent success (§5/§12 honesty).
- **No new outbound request.** This tier adds no network path. The only new stored field is the
  toggle itself (documented here per §9 rule 5); `app/docs/ZERO_COST_AUDIT.md` records the two
  services as AOSP platform APIs at no cost.
- **`LaunchApp`** opens another app by package through `PackageManager.getLaunchIntentForPackage`,
  the same benign start-an-activity any app may do. The launchable-apps picker reads apps through a
  scoped `MAIN`/`LAUNCHER` `<queries>` entry — never `QUERY_ALL_PACKAGES` (§7.8).

## 7. Permissions

### Declared set, with the reason for each

Eleven permissions, seven `<queries>` intents and one `<queries>` package — read off
`app/src/main/AndroidManifest.xml` rather than off anybody's intent. Nothing else is declared, and
a permission that is not in this table is not in the manifest. High-risk permissions previously declared
(`ACCESS_BACKGROUND_LOCATION`, `READ_CONTACTS`, `BIND_ACCESSIBILITY_SERVICE`, and
`BIND_NOTIFICATION_LISTENER_SERVICE`) were removed to comply with Google Play and Play Protect legal
installation policies.

| Permission | Why Mira needs it | When |
|---|---|---|
| `android.permission.RECORD_AUDIO` | The only permission `SpeechRecognizer` requires. Without it: `ERROR_INSUFFICIENT_PERMISSIONS`. Dangerous — must also be requested at runtime, from a visible screen. | Voice input |
| `android.permission.POST_NOTIFICATIONS` | Show a reminder when it fires. Runtime permission since API 33. Denied means reminders are silent, not broken. | Reminders |
| `android.permission.RECEIVE_BOOT_COMPLETED` | `AlarmManager` drops every alarm on reboot. A `BOOT_COMPLETED` receiver calls `ReminderRepository.rescheduleAll()`. Without it, reminders set before a reboot never fire. | Reminders |
| `android.permission.SCHEDULE_EXACT_ALARM` | A reminder that fires at the wrong minute is worse than no reminder. Declared in preference to `USE_EXACT_ALARM` because it is revocable and Settings shows Abu a way to grant it back; the scheduler degrades to an inexact window when it is denied. See the note below. | Reminders |
| `android.permission.USE_BIOMETRIC` | `BiometricPrompt.authenticate()` is annotated `@RequiresPermission(USE_BIOMETRIC)` and throws `SecurityException` without it. Normal protection, granted at install. | Vault |
| `android.permission.VIBRATE` | The haptics in the design language, gated by `MiraSettings.hapticFeedback` through `LocalHapticsEnabled`. | UI feedback |
| `android.permission.INTERNET` | The optional Gemini call, and nothing else. See the note below. | Gemini |
| `android.permission.ACCESS_NETWORK_STATE` | `ConnectivityManager.getNetworkCapabilities()` needs it for the `NET_CAPABILITY_VALIDATED` check in `GeminiCommandEngine.isAvailable()`. It sends nothing. Without it the probe returns false and Gemini simply never becomes available — a silent degradation, which is why it is listed explicitly. | Gemini |
| `android.permission.READ_CALENDAR` | Reading the next event for Home and the daily brief, through `CalendarRepository`. Dangerous, runtime. Refused means the brief says the day could not be read — deliberately NOT the same as saying the day is clear. | Home, briefs |
| `android.permission.WRITE_CALENDAR` | Creating an event Abu asked for (`AppCommand.CreateCalendarEvent`). Dangerous, runtime. | Calendar commands |
| `android.permission.ACCESS_COARSE_LOCATION` | The weaker of the two location grants; a place alert works on it, less precisely. | Place reminders and prayer times |
| `android.permission.ACCESS_FINE_LOCATION` | Taking the single fix that saves a place, and the accuracy a place alert wants. Dangerous, runtime, requested only from the Places screen. | Place reminders and prayer times |

### `<queries>` — required, and silently fatal if missing

At targetSdk ≥ 30, without these `TextToSpeech.getEngines()` returns a truncated list and
recognition services are invisible. There is no exception and no log line — on a Samsung device
that ships both Samsung TTS and Google TTS, a short engine list is an easily misread symptom.

```xml
<queries>
    <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>
    <intent><action android:name="android.speech.RecognitionService" /></intent>
</queries>
```

(Optionally also `android.speech.tts.engine.CHECK_TTS_DATA` and `...INSTALL_TTS_DATA` if those
activities are ever resolved.)

**`QUERY_ALL_PACKAGES` is forbidden.** It is a policy-restricted permission for accessibility,
browser, device-management, security and antivirus apps. Two `<intent>` blocks do the job.

### Notes on the two awkward ones

**Exact alarms.** Apps targeting API 34+ are not pre-granted `SCHEDULE_EXACT_ALARM`; the user
grants it from system settings, which is why `ReminderScheduler.canScheduleExact()` exists and
must be checked before every schedule. `USE_EXACT_ALARM` (API 33+) is granted at install
instead, but is restricted by Play policy to alarm-clock and calendar-class apps — Mira is
sideloaded onto one device and is genuinely an alarm-setting app, so it qualifies on the merits.
Pick one, declare only that one, and verify the actual grant state on the S24 Ultra rather than
trusting the table: the behaviour has changed across releases and is worth re-confirming.
When exact alarms are unavailable, degrade to an inexact alarm and **tell Abu** the reminder may
be late — do not silently downgrade.

**`INTERNET` cannot be conditional.** A manifest permission is static: if the Gemini code path
is compiled into the app at all, `INTERNET` must be declared, even though the default
configuration never uses it. The honest statement is therefore not "Mira has no network
permission" but "Mira holds the network permission and, with Gemini off, makes no network
request". The structural guarantees in §4 are what actually bound the behaviour.

### Permissions Mira does **not** request

`READ_SMS` / `RECEIVE_SMS` · `CAMERA` · `READ_MEDIA_*` · `READ_EXTERNAL_STORAGE` ·
`MANAGE_EXTERNAL_STORAGE` · `QUERY_ALL_PACKAGES` · `READ_PHONE_STATE` ·
`ACCESS_LOCAL_NETWORK` · any account, sync or billing permission.

Location, contacts and calendar **used to be on this list and no longer are.** Each left it the
way standing rule 5 in §9 requires — carried in by the feature that needed it, in the change that
shipped that feature, with its manifest entry, its runtime request, its refusal path and its row
in the table above landing together. Location was the most recent: it arrived with place
reminders.

Several planned features (Files, document scanning, S Pen capture) would need some of what
remains. Each one is a privacy decision to be made and recorded here **at the time it is added**,
not assumed by whoever implements the feature.

### If a listening foreground service is ever added

Not in v0.1. It would require `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`, a service
with `android:foregroundServiceType="microphone"`, and `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`
passed to `startForeground()`. `RECORD_AUDIO` is while-in-use restricted, so such a service
**cannot be started from the background** — any wake-word design has to begin from a visible
entry point.

---

## 8. Onboarding and permissions

Onboarding runs once, on first launch, and it is the one place where Mira explains herself
before she asks Abu for anything. Twelve steps: welcome, language, voice, microphone,
notifications, calendar, contacts, location, biometric protection, Android Assistant mode,
privacy, finish.

The permission steps fall into two kinds, and that split is the decision worth recording.

### Requested during onboarding

| Permission | Why it is asked for now |
|---|---|
| `RECORD_AUDIO` | Voice input is the point of Mira, and `SpeechRecognitionManager` is built and working today. Asking here means the first tap on the microphone is not interrupted by a system dialog |
| `POST_NOTIFICATIONS` | Reminders exist, fire, and post through `MiraNotifications`. Denied, a reminder is silent rather than broken — the step says exactly that, and moving on is allowed |

Both are already declared in the manifest, and both have a consumer in the shipped app.

### Explained during onboarding, requested only in context

Calendar, contacts and location each get a step of their own. Each step says what the capability
will be for. **None of the three is requested during onboarding** — all three are now declared in
the manifest, because each has a consumer in the shipped app, but the runtime dialog belongs to
the screen that needs it and nowhere else.

The reason is the quality bar, not caution:

- **Requesting a permission with no consumer is the same class of defect as a control that does
  nothing** (`AGENTS.md` §12). Android would put a dialog in front of Abu, he would answer it,
  and his answer would change nothing about what Mira can do.
- A permission granted early is a standing liability: it widens what the app is able to read, for
  no benefit, for however many months pass before the feature that needed it lands.
- Onboarding is the worst possible moment to ask. Abu has not yet seen what any of it is for, so
  a grant there is not an informed one.

**Each is requested in context, by the feature that needs it.** Contacts when Mira is first asked
to call or message someone by name. Calendar when Home or the brief first reads the day. Location
from the Places screen, and only when Abu taps "save where I am now".

Location is worth spelling out, because it is the most intrusive of the three and is split in
two. Saving a place takes a **single fix, on a tap, while Abu is looking at the screen** — that
needs only foreground location, and it is all the Places screen ever asks for. Watching for an
arrival needs `ACCESS_BACKGROUND_LOCATION`, which Android grants only from system settings, and
Mira does not send Abu there until a place reminder actually exists to watch. Nothing about
location is ever presented as setup Mira needs: `uses-feature` is `required="false"`, every
refusal path degrades to an explanation rather than a block, and an Abu who never opens the
Places screen has a fully working assistant.

Mira holds no geocoder and no map. A coordinate enters only by him standing somewhere and saying
so, it is stored in `saved_places` on the device, it is never sent anywhere, and it never reaches
Gemini — see §4.

### What the remaining steps do

| Step | What it does |
|---|---|
| Language | Writes `LanguageMode` — `AUTO`, বাংলা or English — to the settings DataStore. No permission, no dialog |
| Voice | Explains that Abu picks Mira's voice by ear, because the TTS API exposes no gender and anything shown would be a guess parsed from a name (ADR-004). Voice Setup itself lives under Settings |
| Biometric protection | Explains the vault gate. `USE_BIOMETRIC` is a normal permission granted at install, so there is nothing to request; the step enrols nothing and stores nothing |
| Android Assistant mode | Explains that the assistant role is a **system** setting Abu grants in Android's own Settings. Mira cannot take it, does not request it silently, and does not claim to hold it |
| Privacy | The three sentences that matter: most of Abu's data stays on the device, cloud AI is optional, and Mira is fully functional without Gemini. Each is a structural property described in §4, not a promise |

### Rules for anyone changing onboarding

1. **Every optional step is skippable**, through a visible control rather than a back gesture.
   Denying a permission never blocks the next step and never ends onboarding early.
2. A denial is a state Mira handles, not an error she shows. It stays re-askable from Settings,
   which is also where a permanently-denied permission must point Abu.
3. Onboarding never requests a permission that no shipped code path consumes.
4. Nothing said, typed or granted during onboarding leaves the device. Onboarding makes no
   network request of any kind, whatever the Gemini setting happens to be.
5. Completion is one boolean in the `mira_settings` DataStore
   (`MiraPreferenceKeys.ONBOARDING_COMPLETE`, default `false`). It carries no timestamp, no
   version and no record of what Abu answered, because none of those are needed to decide
   whether to show it — and each would be one more thing stored about him for no reason.

---

## 9. Standing rules

1. No analytics, crash-reporting, attribution or A/B SDK is ever added. Not a free one, not a
   self-hosted one.
2. No user data goes in a URL, a query string or a request header.
3. Nothing is sent to an endpoint that was not written into this repository deliberately.
4. Gemini stays off by default and stays behind `CommandEngine`. If a future feature seems to
   need Gemini to read the database, the feature is wrong, not the boundary.
5. Any new permission, any new outbound request and any new stored field is documented here in
   the same change that introduces it.
