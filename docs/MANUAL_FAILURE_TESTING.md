# Manual Failure & Permission Audit Guide — Mira

This document outlines manual testing procedures for real devices (Samsung Galaxy S24 Ultra, Android 14+) and emulators (Pixel 6, API 37) to verify that Mira fails gracefully under edge cases, hardware constraints, OS kills, and permission denials.

---

## 1. Permission Denials

### 1.1 Microphone Permission Denied
* **Setup:** In Android Settings > Apps > Mira > Permissions > Microphone, set to **"Don't allow"**.
* **Test Action:** Tap the microphone / voice orb on the Home screen or Assistant screen, or activate voice input.
* **Expected Result:**
  - Mira does **not** crash.
  - The voice orb transitions from listening to an error/settled state.
  - Returns `SpeechFailure.PERMISSION`.
  - A clear prompt or toast appears informing Abu that microphone access is disabled, allowing him to type commands instead.

### 1.2 Notification Permission Denied (`POST_NOTIFICATIONS`)
* **Setup:** Android Settings > Apps > Mira > Notifications > Toggle OFF.
* **Test Action:**
  - Create a reminder due in 1 minute.
  - Wait for the alarm to trigger (`ReminderReceiver` / `MiraNotifications`).
  - Trigger a prayer time notification or Daily Brief.
* **Expected Result:**
  - Mira catches `SecurityException` inside `MiraNotifications.notifySafely`.
  - No crash (`Fatal Exception`).
  - Ledger and alarm state update gracefully; in-app reminder list continues to show reminder as due/fired.

### 1.3 Calendar Permission Denied (`READ_CALENDAR`, `WRITE_CALENDAR`)
* **Setup:** Android Settings > Apps > Mira > Permissions > Calendar > Set to **"Don't allow"**.
* **Test Action:** Speak or type: *"What do I have tomorrow?"* or *"Add meeting with Ahmed next Tuesday at 3pm"*.
* **Expected Result:**
  - Mira does not crash with `SecurityException`.
  - `CalendarAccessResult.PermissionDenied` is returned.
  - Mira responds politely: *"Calendar permission is required to check your schedule"* without executing invalid writes.

### 1.4 Contacts Permission Denied (`READ_CONTACTS`)
* **Setup:** Android Settings > Apps > Mira > Permissions > Contacts > Set to **"Don't allow"**.
* **Test Action:** Speak or type: *"Find Ahmed's number"* or *"Ahmed এর email কি?"*.
* **Expected Result:**
  - `ContactsProvider` safely catches permission checks and returns an empty/denied result.
  - Mira states that contacts could not be read due to missing permission.
  - No crash.

### 1.5 Location Permission Denied & Hardware Disabled
* **Setup:**
  - Case A: Android Settings > Apps > Mira > Permissions > Location > Set to **"Don't allow"**.
  - Case B: Android quick settings > Turn off Location toggle globally.
* **Test Action:** Set or inspect a location-based reminder or prayer calculation depending on device GPS.
* **Expected Result:**
  - Falls back immediately to the user's default configured city/coordinates (e.g. Dhaka).
  - Geofence registration fails gracefully with `PlaceWatchRefusal.PERMISSION_DENIED` or `LOCATION_DISABLED`.
  - No platform service crash.

---

## 2. Network & Cloud Engine Failures

### 2.1 No Internet / Airplane Mode
* **Setup:** Enable Airplane Mode (Wi-Fi and Cellular off).
* **Test Action:** Speak/type financial queries, reminder scheduling, currency conversion, or prayer time queries.
* **Expected Result:**
  - On-device speech recognition functions seamlessly (via `createOnDeviceSpeechRecognizer`).
  - Local rule-based NLU parses the command.
  - 100% of core features (Finance, Invoices, Room DB, Reminders, Prayer calculation) execute offline.
  - Zero network errors or timeouts surfaced to Abu.

### 2.2 Gemini Unavailable or Quota Exhausted (HTTP 429 / 503 / SocketTimeout)
* **Setup:** Configure an invalid/exhausted Gemini API key or block Google API endpoints.
* **Test Action:** Enter an utterance that falls through to Gemini (if enabled).
* **Expected Result:**
  - `CommandRouter` invokes Gemini, catches network exception / API quota error.
  - Automatically falls back to the local engine verdict (`AppResult.Failure` or local best-effort parsing).
  - Abu receives a concise, deterministic answer rather than a raw stack trace.

---

## 3. Speech & TTS Engine Failures

### 3.1 Speech Recognizer Unavailable
* **Setup:** On an AVD or device without Google Speech Services or offline language pack.
* **Test Action:** Tap the voice orb.
* **Expected Result:**
  - `SpeechRecognitionManager` catches `ERROR_SERVER_DISCONNECTED` or missing recognition service.
  - Orb returns to resting state; text input field remains focused and usable.

### 3.2 Bengali Recognition / Bengali TTS Voice Unavailable
* **Setup:** System language set to English, Bengali voice data not downloaded in Samsung TTS / Google TTS.
* **Test Action:**
  - Speak Bengali command.
  - Request Mira to speak a Bengali response.
* **Expected Result:**
  - Recognition: Falls back to Latin/Banglish transliteration normalizer.
  - TTS: `MiraTextToSpeech` checks `isLanguageAvailable(Locale("bn", "BD"))`. If missing or degraded, falls back to default system voice or displays text without audio crash.

---

## 4. Communication & Messaging Edge Cases

### 4.1 No Email Application Installed
* **Setup:** On an emulator with no email client (no Gmail, no Outlook).
* **Test Action:** Create an invoice and confirm delivery via Email Share / Intent.
* **Expected Result:**
  - `startActivity` catches `ActivityNotFoundException`.
  - Mira records invoice delivery as `FAILED` with details *"No email app found"*.
  - Invoice status in Room remains intact.

### 4.2 Gmail Disconnected / Revoked OAuth Token
* **Setup:** Revoke Google OAuth permissions in Google Account Security settings.
* **Test Action:** Attempt Gmail direct draft preparation/sending.
* **Expected Result:**
  - Auth token refresh returns an unauthorized/expired error.
  - Mira cleanly prompts to re-authenticate or offers the Android system share sheet fallback.

---

## 5. Storage & Database Resilience

### 5.1 Storage Full
* **Setup:** Fill device storage using `adb shell fallocate -l <size> /data/local/tmp/fill`.
* **Test Action:** Attempt to export PDF invoice or create a backup.
* **Expected Result:**
  - `IOException` caught during file write.
  - UI displays informative error: *"Insufficient storage"*.
  - No partial/corrupt records written to Room (atomic transaction rollbacks).

### 5.2 Corrupt Backup File or Incorrect Passphrase
* **Setup:**
  - Case A: Import a backup file with random bytes or altered checksum.
  - Case B: Enter wrong password during backup restoration.
* **Test Action:** Restore backup from Settings > Data & Backup.
* **Expected Result:**
  - `PortableBackupCipher` throws `AUTHENTICATION_FAILED` (AEAD tag mismatch).
  - Restoring aborts before wiping or replacing current Room database.
  - Current database remains completely unaffected.

### 5.3 Missing Client / Deleted Linked Client
* **Setup:**
  - Attempt to delete a client who has existing invoices or quotes.
* **Test Action:** Client deletion query executed.
* **Expected Result:**
  - SQLite foreign key constraint `ForeignKey.RESTRICT` enforces integrity.
  - Database rejects deletion with foreign key violation; UI informs that active invoices must first be settled or deleted.
  - No orphan or ghost invoices created.

---

## 6. System Lifecycle & Time Changes

### 6.1 Device Reboot
* **Setup:**
  - Schedule reminders, alarms, and daily briefs.
  - Reboot device via `adb reboot`.
* **Test Action:** Inspect scheduled alarms via `adb shell dumpsys alarm | grep com.rayluxeo.aida`.
* **Expected Result:**
  - `BootReceiver` receives `ACTION_BOOT_COMPLETED`.
  - Re-arms all pending reminders from `ReminderRepository`.
  - Recalculates prayer times for the current date.
  - Zero missed alarms or dropped reminders.

### 6.2 Timezone Change & System Clock Jump
* **Setup:**
  - Have pending reminders for 5:00 PM.
  - In Android Settings > Date & Time:
    - Change Timezone (e.g., from GMT+6 Dhaka to GMT+0 London).
    - Change System Time manually (e.g. forward by 2 hours).
* **Test Action:** Observe broadcast reception and alarm schedule.
* **Expected Result:**
  - `BootReceiver` listens to `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_SET`.
  - Re-syncs all reminders and recomputes prayer solar calculations.
  - No duplicate alarms fired.

### 6.3 App Process Killed (`am kill` / Low Memory)
* **Setup:**
  - Schedule tasks / open an invoice draft.
  - Run `adb shell am kill com.rayluxeo.aida.debug`.
  - Reopen app.
* **Test Action:** Inspect state.
* **Expected Result:**
  - Room state persists completely.
  - Background workers (`DerivedReminderWorker`) maintain idempotency keys: duplicate sweeps do **not** re-raise duplicate notifications.

### 6.4 Battery Optimization (Doze Mode / App Standby)
* **Setup:**
  - Device enters Doze: `adb shell dumpsys deviceidle force-idle`.
* **Test Action:** Wait for an exact reminder/alarm to reach its trigger time.
* **Expected Result:**
  - Alarms scheduled with `setExactAndAllowWhileIdle` fire reliably on time.
  - If `SCHEDULE_EXACT_ALARM` was revoked by the user, fallback inexact window alarms trigger as soon as the maintenance window opens.
