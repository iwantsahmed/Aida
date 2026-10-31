# Zero-Cost Audit

Mira must cost nothing to build, run or keep running. Not a subscription, not a billing account,
not a free tier that converts on overage, not a developer-programme fee.

This document records every dependency, SDK, API, service, and library the app touches, with its
licence, operational requirements, and zero-cost finding. It is updated on every architectural review
and dependency change.

**Audit Date:** 2026-10-11 · **Scope:** `gradle/libs.versions.toml`, `app/build.gradle.kts`,
`settings.gradle.kts`, `debugRuntimeClasspath` transitive graph, and every runtime service reachable
from the source tree.

**Overall Finding: PASS.**
Every artefact on the classpath is free and open-source. Every runtime service is either an AOSP
platform API, a preinstalled system component, or runs on-device. The single external cloud API
(Google Gemini) is optional, disabled by default, free-tier only with no billing account, and not
required for any core feature.

---

## 1. Audit Framework & Zero-Cost Invariants

Every component evaluated in this audit must satisfy Abu's hard constraints:
1. **Abu must not need to pay to use Mira.**
2. **No unexpected charges:** Never rely on a service that requires a credit card on file, a billing
   account, or automatically charges on overage.
3. **Offline-first & Radio-off resilience:** Core functionality must work without Gemini, without
   Firebase cloud servers, without internet, and without paid cloud APIs.
4. **Clean fallback:** When an optional network path (Gemini) is unavailable or throttled, Mira
   degrades instantly to deterministic on-device engines.

---

## 2. External Services & Cloud APIs

| Field | Google Gemini API (AI Studio) | Direct Gmail API | Firebase Cloud Services |
|---|---|---|---|
| **Name** | Google Gemini API (`generativelanguage.googleapis.com`) | Direct Gmail API (`gmail.googleapis.com`) | Firebase (Auth, Firestore, Functions, etc.) |
| **Purpose** | Optional fallback command interpretation when on-device engine returns `NotUnderstood` | Direct background email sending for invoices | Cloud sync, auth, analytics, crash reporting |
| **License** | Google AI Studio Terms of Service | Google APIs Terms of Service | Apache 2.0 / Google Terms of Service |
| **Internet required?** | Yes (when invoked) | Yes | Yes |
| **Account required?** | Optional personal Google AI Studio account (only if Abu chooses to supply an API key) | Google Account + OAuth 2.0 setup | Google Cloud / Firebase Account |
| **Payment method required?** | **No.** Google AI Studio free tier requires no credit card. | Yes if Cloud project quotas exceeded or workspace billed | Often requires Blaze plan for production |
| **Billing account required?** | **No.** Strictly forbidden in Mira (`AGENTS.md` §2). | Forbidden | Forbidden |
| **Can it generate charges?** | **No.** Without a billing account attached, requests over limit fail with HTTP 429; no billing is possible. | Risk of charges if project billed | Risk of charges |
| **Free quota?** | Free tier (up to 15 RPM, 1M TPM, 1,500 RPD on Flash models) | Varies | Varies |
| **What happens when quota is exhausted?** | Returns HTTP 429 (`TOO_MANY_REQUESTS`). `GeminiAvailabilityManager` updates quota state and router falls back to local engine. | N/A | N/A |
| **Offline fallback?** | **Default on-device engine (`DefaultCommandEngine`).** Mira functions 100% offline without Gemini. | **Not implemented.** Mira uses standard Android Intent sharing (`ACTION_SENDTO` / `ACTION_SEND` with `FileProvider`) to pre-fill the native email app. | **Not used.** Local Room database + DataStore preferences provide 100% offline storage. |
| **Status in Mira** | **Optional, off by default.** Zero cost verified. | **Deliberately omitted.** Zero cost verified. | **Deliberately omitted.** Zero cost verified. |

---

## 3. On-Device SDKs & System Services

| Attribute | Android Speech Recognizer | Android Text-to-Speech | Google ML Kit Text Recognition | Android Calendar & Contacts | Android Location & Place Alerts | Android Keystore & BiometricPrompt |
|---|---|---|---|---|---|---|
| **Name** | `android.speech.SpeechRecognizer` | `android.speech.tts.TextToSpeech` | `com.google.mlkit:text-recognition` | `CalendarContract` & `ContactsContract` | `android.location.LocationManager` | `android.security.keystore` & `BiometricPrompt` |
| **Purpose** | Speech-to-text input (English, Bengali, Banglish) | Spoken feedback in Abu's chosen voice | On-device OCR for receipts and documents | Read/write local schedule and search address book | Location-triggered reminders and automations (`addProximityAlert`) | Hardware-backed vault encryption and biometric auth |
| **License** | AOSP / Device System Component | AOSP / Device System Component | Apache 2.0 | AOSP Platform API | AOSP Platform API | AOSP Platform API |
| **Internet required?** | **No.** `createOnDeviceSpeechRecognizer` enforces on-device recognition. | **No.** Uses preinstalled device voice packages. | **No.** On-device model runs entirely locally. | **No.** Local system content providers. | **No.** On-device GPS / network cell location hardware. | **No.** Hardware Secure Element / StrongBox on S24 Ultra. |
| **Account required?** | No | No | No | No | No | No |
| **Payment method required?** | No | No | No | No | No | No |
| **Billing account required?** | No | No | No | No | No | No |
| **Can it generate charges?** | No | No | No | No | No | No |
| **Free quota?** | Unlimited | Unlimited | Unlimited | Unlimited | Unlimited | Unlimited |
| **What happens when quota is exhausted?** | N/A | N/A | N/A | N/A | N/A | N/A |
| **Offline fallback?** | On-device speech recognition is the primary mechanism; typed text input in assistant bar is the manual fallback. | Visual on-screen response rendering in `MiraResponseRenderer`. | Manual entry in expense / note forms. | Stored Mira meeting and client database in Room. | Time-based reminders via `AlarmManager`. | Passcode fallback. |

**Cross-app automation tier (added 16 Oct 2026).** `android.accessibilityservice.AccessibilityService`
(`MiraAccessibilityService`, a pure actuator — `canRetrieveWindowContent="false"`) and
`android.service.notification.NotificationListenerService` (`MiraNotificationListenerService`) are
both AOSP platform APIs — no SDK, no dependency, no network, no billing — in the same zero-cost class
as `AlarmManager` above. Both are opt-in and off by default; Mira is fully functional without them.
Launching an app uses `PackageManager.getLaunchIntentForPackage` with a scoped `MAIN`/`LAUNCHER`
`<queries>` entry, never `QUERY_ALL_PACKAGES`. Cost finding: **$0.00, platform APIs.**

---

## 4. Runtime Libraries & Dependencies

All dependencies are resolved from Google Maven or Maven Central at no charge.

### 4.1 AndroidX / Jetpack

| Attribute | `androidx.core:core-ktx` & `core-splashscreen` | `androidx.activity:activity-compose` | `androidx.compose.*` (BOM 2026.09.00) | `androidx.lifecycle.*` (2.11.0) | `androidx.navigation:navigation-compose` (2.10.2) |
|---|---|---|---|---|---|
| **Purpose** | Kotlin extensions for framework APIs & splash screen | Compose integration for Activity lifecycle | UI layout, Material 3, graphics, typography | Lifecycle management, `ViewModel`, `StateFlow` collection | Compose navigation graph and deep linking |
| **License** | Apache 2.0 | Apache 2.0 | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **Internet required?** | No | No | No | No | No |
| **Account required?** | No | No | No | No | No |
| **Payment method required?** | No | No | No | No | No |
| **Billing account required?** | No | No | No | No | No |
| **Can it generate charges?** | No | No | No | No | No |
| **Free quota?** | Unlimited | Unlimited | Unlimited | Unlimited | Unlimited |
| **What happens on overage?** | N/A | N/A | N/A | N/A | N/A |
| **Offline fallback?** | Built-in | Built-in | Built-in | Built-in | Built-in |

| Attribute | `androidx.room:*` (2.8.5) | `androidx.datastore:datastore-preferences` (1.2.1) | `androidx.work:work-runtime-ktx` (2.12.0) | `androidx.hilt:hilt-navigation-compose` & `hilt-work` |
|---|---|---|---|---|
| **Purpose** | Local SQLite relational database, FTS4 full-text search | Key-value settings storage (encrypted via Keystore) | Background maintenance sweeps (e.g. daily reminder sweep) | Dependency injection bindings for Compose navigation and Workers |
| **License** | Apache 2.0 | Apache 2.0 | Apache 2.0 | Apache 2.0 |
| **Internet required?** | No | No | No | No |
| **Account required?** | No | No | No | No |
| **Payment method required?** | No | No | No | No |
| **Billing account required?** | No | No | No | No |
| **Can it generate charges?** | No | No | No | No |
| **Free quota?** | Unlimited | Unlimited | Unlimited | Unlimited |
| **What happens on overage?** | N/A | N/A | N/A | N/A |
| **Offline fallback?** | Runs 100% offline | Runs 100% offline | Runs on local `JobScheduler` / `AlarmManager` | Runs 100% offline |

### 4.2 Google & JetBrains Dependencies

| Attribute | `com.google.dagger:hilt-android` (2.60.1) | `org.jetbrains.kotlinx:kotlinx-coroutines-android` (1.11.0) | `org.jetbrains.kotlinx:kotlinx-serialization-json` (1.9.0) | `com.android.tools:desugar_jdk_libs` (2.1.5) | `com.google.mlkit:text-recognition` (16.0.1) |
|---|---|---|---|---|---|
| **Purpose** | Dependency injection framework | Coroutines, reactive flows (`StateFlow`, `SharedFlow`) | Safe JSON serialization for commands and DataStore | Desugaring of modern `java.time` APIs for Android 12+ (minSdk 31) | On-device OCR text recognition for receipts and invoices |
| **License** | Apache 2.0 | Apache 2.0 | Apache 2.0 | GPLv2 with Classpath Exception | Apache 2.0 |
| **Internet required?** | No | No | No | No | No |
| **Account required?** | No | No | No | No | No |
| **Payment method required?** | No | No | No | No | No |
| **Billing account required?** | No | No | No | No | No |
| **Can it generate charges?** | No | No | No | No | No |
| **Free quota?** | Unlimited | Unlimited | Unlimited | Unlimited | Unlimited |
| **What happens on overage?** | N/A | N/A | N/A | N/A | N/A |
| **Offline fallback?** | Built-in | Built-in | Built-in | Built-in | Manual entry |

---

## 5. Transitive Graph & Firebase Audit

A full inspection of the transitive dependency tree (`./gradlew :app:dependencies --configuration debugRuntimeClasspath`) was executed.

### Transitive Findings:
1. **Firebase libraries in transitive graph:**
   - `com.google.firebase:firebase-components:16.1.0`
   - `com.google.firebase:firebase-encoders:16.1.0`
   - `com.google.firebase:firebase-encoders-json:17.1.0`
   - **Finding:** These are pulled in exclusively as transitive dependencies by `com.google.mlkit:text-recognition-bundled-common`. They are lightweight, local Java dependency-injection and JSON-encoding utility libraries.
   - **Verification:** There is **NO** Firebase runtime connection, no Firebase Auth, no Firestore, no Cloud Functions, no Crashlytics, and no `google-services.json` in the project. They do not communicate with any Firebase server and cannot generate costs.
2. **Google Play Services dependencies:**
   - `com.google.android.gms:play-services-base:18.5.0`
   - `com.google.android.gms:play-services-basement:18.4.0`
   - `com.google.android.gms:play-services-tasks:18.2.0`
   - **Finding:** Pulled transitively by ML Kit to communicate with on-device system services and manage async tasks (`Task<T>`).
   - **Verification:** These are client libraries included with ML Kit and are 100% free of charge. No paid Google Play APIs or billing accounts are referenced.

---

## 6. Build Toolchain & CI

| Component | Version | License | Cost | Can Generate Charges? | Notes |
|---|---|---|---|---|---|
| Android Gradle Plugin (AGP) | 9.4.1 | Apache 2.0 | Free | No | Bundles Kotlin support |
| Gradle | 9.6.1 | Apache 2.0 | Free | No | Local execution only |
| Kotlin (KGP, Compose, Serialization) | 2.3.20 | Apache 2.0 | Free | No | Open source |
| KSP (`com.google.devtools.ksp`) | 2.3.12 | Apache 2.0 | Free | No | Annotation processor |
| Hilt Gradle Plugin | 2.60.1 | Apache 2.0 | Free | No | Code generation |
| JDK 21 (JetBrains Runtime) | 21.0.11 | GPLv2 with Classpath Exception | Free | No | Local JVM on macOS |
| Android SDK / Build Tools | 37.0 / 36.0.0 | Android Software Development Kit License | Free | No | Provided by Google without charge |
| Continuous Integration (CI) | None | N/A | Free | No | All builds run locally on Abu's machine; no GitHub Actions billable minutes |
| Gradle Build Cache | Local only | N/A | Free | No | `org.gradle.caching=true` stores cache in local user directory; no remote cache node |

---

## 7. Assets, Typography & Icons

| Asset | Source / Package | License | Cost | Storage & Network |
|---|---|---|---|---|
| **Noto Serif Bengali** | `res/font/noto_serif_bengali.ttf` | SIL Open Font License 1.1 | Free | Bundled in APK (~960 KB); 100% offline, never fetched from network |
| **Manrope** | `res/font/manrope_*.ttf` (5 cuts) | SIL Open Font License 1.1 | Free | Bundled in APK (~485 KB); 100% offline, never fetched from network |
| **Solar Icons** | `res/drawable/ic_*.xml` (~115 vectors) | CC0 1.0 (Public Domain) | Free | Bundled as vector drawables in APK; zero runtime network requests |
| **Mira Avatar** | `com.rayluxeo.aida.ui.avatar.MiraAvatar` | Proprietary code asset | Free | Drawn purely in code via Compose `Canvas`; no bitmap or network requests |

---

## 8. Financial Arithmetic & Islamic Calculations

| Feature | Implementation | Dependencies | Network Required? | Cost |
|---|---|---|---|---|
| **Financial Calculations** | `com.rayluxeo.aida.domain.finance.Calculator` & `Money` | Pure Kotlin `BigDecimal` with explicit currency scale | **No** | **Free.** Never delegated to a language model or external API. |
| **Salah / Prayer Times** | `com.rayluxeo.aida.domain.salah.PrayerTimeCalculator` | Pure Kotlin astronomical equations (Jean Meeus algorithm) | **No** | **Free.** Computed on-device from latitude/longitude with radio off. |
| **Hijri Date Conversion** | `java.time.chrono.HijrahDate` | Standard Java desugared library (OpenJDK Umm al-Qura) | **No** | **Free.** Offline calendar computation. |

---

## 9. Test Toolchain (Local & Unit Tests)

| Artifact | Version | License | Cost | Notes |
|---|---|---|---|---|
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 | Free | Runs locally |
| `com.google.truth:truth` | 1.4.5 | Apache 2.0 | Free | Assertion library |
| `app.cash.turbine:turbine` | 1.2.1 | Apache 2.0 | Free | Coroutines Flow testing |
| `org.robolectric:robolectric` | 4.17 | MIT | Free | Local Android sandbox testing |
| `androidx.test:core` & `ext:junit` | 1.7.0 / 1.3.0 | Apache 2.0 | Free | Test runner primitives |
| `androidx.room:room-testing` | 2.8.5 | Apache 2.0 | Free | In-memory SQLite testing |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Apache 2.0 | Free | Virtual time dispatchers |
| `com.google.dagger:hilt-android-testing` | 2.60.1 | Apache 2.0 | Free | Instrumented test DI |

---

## 10. Audit Conclusion & Compliance Summary

1. **Are there any paid libraries, SDKs, or services in the project?**
   **No.** Every dependency is open-source (Apache 2.0, MIT, EPL 1.0, SIL OFL 1.1, CC0, or GPLv2 with Classpath Exception).
2. **Can any component generate automatic charges?**
   **No.** No billing account, credit card, or payment provider is attached anywhere in the project or build system.
3. **What external cloud service remains?**
   **Google Gemini API** is the only external network service defined. It remains free because:
   - It targets the Google AI Studio free tier.
   - It requires no Cloud billing account and no payment method.
   - Requests over quota receive HTTP 429 and are rejected; Google does not bill or convert on overage without a linked Cloud billing account.
   - It is disabled by default and requires Abu to explicitly configure his own free-tier API key.
   - Mira has a complete on-device NLU engine (`DefaultCommandEngine`) that handles every command offline with zero network connection.
4. **Offline Resilience:**
   The entire application has been tested and verified to operate with airplane mode active and with no API keys configured.
