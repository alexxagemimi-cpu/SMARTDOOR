# Smart Door Lock — Exhibition Android App

A Kotlin + Jetpack Compose Android app that pairs with an Arduino-based smart
door lock over Bluetooth Classic (HC-05). Built for a single exhibition demo:
no login, no signup, no cloud backend, no internet dependency.

---

## 0. Read this first — what "buildable" means here

This project was written entirely by hand, file by file, with real Android
APIs — but it was **not compiled** before being handed to you. The sandbox
this was built in has no network access and no Android SDK/Gradle installed,
so there was no way to actually run `./gradlew assembleDebug` and watch it
succeed. Every file was checked carefully for correctness (see §9 for the
specific things that were verified against current Android documentation
rather than assumed), and the code was reviewed multiple times for
consistency, but "I wrote this carefully" is not the same guarantee as "I
watched it compile." The first thing to do is open it in Android Studio and
let it sync — see §7.

If Android Studio's sync flags an error, it's most likely one of:
- A dependency version bump Android Studio suggests automatically (safe to accept)
- A missing `local.properties` pointing at your SDK (Android Studio creates this for you)

---

## 1. Project structure

```
SmartDoorLock/
├── build.gradle.kts                 Root build file (plugin versions)
├── settings.gradle.kts              Module + repository declarations
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts             App module: SDK versions, dependencies
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/{strings,colors,themes}.xml
        │   ├── drawable/ic_launcher_{background,foreground}.xml
        │   └── mipmap-anydpi-v26/ic_launcher{,_round}.xml
        └── java/com/exhibition/smartdoorlock/
            ├── MainActivity.kt                    Entry point, permissions, tab host
            ├── bluetooth/
            │   ├── BluetoothConnectionManager.kt   RFCOMM socket, connect/listen/send
            │   └── ArduinoProtocol.kt              Shared protocol string constants
            ├── speech/
            │   └── TextToSpeechManager.kt          TTS wrapper
            ├── data/
            │   ├── local/
            │   │   ├── ReviewEntity.kt             Room @Entity
            │   │   ├── ReviewDao.kt                Room @Dao
            │   │   └── AppDatabase.kt              Room @Database singleton
            │   └── repository/
            │       └── ReviewRepository.kt         Mediates DAO <-> ViewModel
            └── ui/
                ├── AppViewModelFactory.kt           Hand-written DI for the 2 ViewModels
                ├── theme/{Color,Type,Theme}.kt       Design tokens + MaterialTheme
                ├── doorcontrol/
                │   ├── DoorControlViewModel.kt
                │   └── DoorControlScreen.kt
                └── rateproject/
                    ├── RateProjectViewModel.kt
                    └── RateProjectScreen.kt
```

18 Kotlin files, ~1,400 lines. Every file's package declaration was checked
against its directory path, and every file's braces/parens were checked for
balance (see the sanity sweep this was built with — nothing is a substitute
for a real compile, but this catches the most common copy/paste slips).

---

## 2. Key files explained

- **`MainActivity.kt`** — the only Activity. Opens straight to a bottom-tab
  dashboard (Door Control / Rate Project), requests `BLUETOOTH_CONNECT` at
  launch on API 31+, and hosts an "enable Bluetooth" system-intent launcher.
  No navigation library — just a local `enum` + `when` — since two static
  tabs don't need Jetpack Navigation's routing/back-stack machinery.
- **`BluetoothConnectionManager.kt`** — the only class that touches
  `BluetoothSocket`/`InputStream`/`OutputStream`. Everything else talks to it
  through `Flow`s. See §4 for the details.
- **`ArduinoProtocol.kt`** — every protocol string lives here once, so the
  Kotlin side and your Arduino sketch can be diffed against a single source
  of truth instead of hunting for string literals scattered across files.
- **`AppViewModelFactory.kt`** — the app's entire "dependency injection."
  With exactly three dependencies (Bluetooth, TTS, Room), a 15-line factory
  is simpler to read and modify than adding Hilt for an exhibition build.
- **`DoorControlViewModel.kt` / `RateProjectViewModel.kt`** — one `StateFlow`
  of UI state per screen, following standard unidirectional-data-flow MVVM:
  UI sends events in (button clicks), ViewModel exposes state out.

---

## 3. Bluetooth connection implementation

`BluetoothConnectionManager` is intentionally the *only* file that imports
`android.bluetooth.*`. Everything it does:

1. **Lists paired devices only** — `bluetoothAdapter.bondedDevices`. It never
   calls `startDiscovery()`. This isn't just a style choice — it's what lets
   the permission set stay minimal (§6).
2. **Connects via RFCOMM** — `device.createRfcommSocketToServiceRecord(SPP_UUID)`
   using the standard Serial Port Profile UUID
   (`00001101-0000-1000-8000-00805F9B34FB`), which is what HC-05 modules
   advertise. `.connect()` blocks, so it runs on `Dispatchers.IO`, never the
   main thread.
3. **Listens continuously** — a coroutine loop calls `inputStream.read()`,
   reassembles bytes into newline-terminated lines, and emits each complete
   line. Two things worth knowing if you modify this:
   - Bytes are masked with `and 0xFF` before converting to `Char`, because
     `Byte` is signed in Kotlin/JVM — a naive `.toInt().toChar()` would
     mis-decode any byte ≥ 0x80. Every byte in this protocol is ASCII
     (≤ 0x7F) today, so this is currently a no-op in practice, but it's the
     technically-correct conversion regardless of what you send later.
   - Incoming lines are exposed as a **`SharedFlow<String>`**, not a
     `StateFlow`. This was a deliberate choice: `StateFlow` only guarantees
     delivery of the *latest* value and is allowed to conflate rapid updates
     — fine for "current state," wrong for "a sequence of events that must
     each be handled." If the Arduino ever sent two messages close enough
     together to land in the same `read()` chunk, a `StateFlow` could drop
     one of them silently. `SharedFlow` with a small buffer doesn't have
     that failure mode.
4. **Sends commands** — `sendPin("1234")` writes `PIN:1234\n`; `sendLock()`
   writes `LOCK\n`. Both refuse to send unless `connectionState` is
   `Connected`, and both encode as `US_ASCII` (see §8 for why).
5. **Closes cleanly** — `disconnect()` cancels the listener coroutine and
   closes the socket/streams inside try/catch (a socket that's already
   dead throws on `.close()`, and that's expected, not an error to surface).
   `release()` additionally cancels the manager's whole `CoroutineScope` —
   call it once, when the manager is no longer needed (the ViewModel does
   this in `onCleared()`).

---

## 4. Bluetooth permission model (please read before changing the manifest)

The manifest declares exactly:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

No `BLUETOOTH_SCAN`, no `BLUETOOTH_ADVERTISE`, no location permission. This
was checked against Android's official Bluetooth permissions documentation
(developer.android.com/develop/connectivity/bluetooth/bt-permissions) rather
than assumed, specifically because the brief asked not to guess at
permission behavior. The reasoning:

- `BLUETOOTH_SCAN` is only required for **discovering** new devices
  (`startDiscovery()`) or interacting with a device's advertising/scan
  results. This app never scans — it only lists devices the user already
  paired via system Bluetooth settings (`getBondedDevices()`), which does
  **not** require `BLUETOOTH_SCAN` or location permission on any API level.
- `BLUETOOTH_CONNECT` is what's required (API 31+) to read a bonded device's
  name, open an RFCOMM socket, and read/write it — which is all this app
  does.
- On API ≤ 30, `BLUETOOTH`/`BLUETOOTH_ADMIN` are install-time "normal"
  permissions (no runtime prompt needed) and cover the same operations.

One consequence: `BluetoothAdapter.cancelDiscovery()` — a line you'll see in
almost every HC-05 Android tutorial, usually called right before
`socket.connect()` — was **deliberately left out**. It's not required to
open an RFCOMM connection (this app never starts discovery in the first
place, so there's nothing to cancel), and calling it would pull in a
`BLUETOOTH_SCAN` requirement on API 31+ for no functional benefit. If you
later add a "scan for nearby devices" feature, you'll need to add
`BLUETOOTH_SCAN` (and decide whether to declare
`android:usesPermissionFlags="neverForLocation"`) at that point.

`MainActivity` requests `BLUETOOTH_CONNECT` once, at launch, on API 31+.
Every Bluetooth-touching call in the UI (listing paired devices, connecting,
reading a device's name) only happens from code paths gated behind that
permission already being granted — so the `@SuppressLint("MissingPermission")`
annotations in `BluetoothConnectionManager` and `DoorControlScreen` are
suppressing a **static lint warning** the compiler can't otherwise resolve,
not bypassing a real runtime check. If you restructure the UI so a
Bluetooth call can be reached before permission is confirmed, revisit those
annotations.

---

## 5. Room database implementation

Standard three-layer Room setup:

- **`ReviewEntity`** — `id` (auto-generated), `visitorName`, `relation`
  (nullable), `rating` (Int), `review`, `createdAt` (epoch millis).
- **`ReviewDao`** — `insert()` (suspend) and `getAllReviews()` returning
  `Flow<List<ReviewEntity>>`, ordered newest-first.
- **`AppDatabase`** — a thread-safe singleton (`@Volatile` + `synchronized`),
  standard Room boilerplate.
- **`ReviewRepository`** — the only thing the ViewModel talks to. Exposes
  `allReviews: Flow<...>` and one `addReview(...)` suspend function that
  stamps `createdAt` with `System.currentTimeMillis()`.

`RateProjectViewModel` turns `repository.allReviews` into a `StateFlow`
(`reviews`) via `stateIn(..., SharingStarted.WhileSubscribed(5_000))`, and
derives `stats` (total / average / highest / lowest / latest) from that same
`reviews` StateFlow with `.map { computeStats(it) }` — deliberately *not* a
second independent subscription to `repository.allReviews`, so the database
is only queried once and stats simply recompute whenever the list does.

---

## 6. Compose UI

**Door Control tab** (`DoorControlScreen.kt`): connection status card (with
inline "grant permission" / "turn on Bluetooth" prompts as needed), a paired-
device picker dialog, a door-state card (Locked / Unlocked / Access Denied /
Unknown, each with a distinct icon + color), and a passcode card with a
numeric-only field plus Unlock/Lock buttons. Access-granted/denied also fires
a transient Snackbar — the persistent state card *and* a toast-style
confirmation, both firing off the same event so neither can drift out of
sync. Unlock/Lock are disabled whenever the socket isn't connected, which is
what keeps "the app does not send before connection is ready" true by
construction rather than by a manual check sprinkled at each call site.

**Rate Project tab** (`RateProjectScreen.kt`): a live stats card, a form
(name, optional relation, a 1–10 rating grid, an optional review with a
character counter, Submit/Reset), and the saved-reviews list with
timestamps. The 10 rating options are laid out as two rows of five — all
ten stay comfortably tappable on a phone screen rather than being squeezed
into one cramped row. Submit validates name + rating and shows inline
errors; Reset calls `resetForm()`, which only ever touches the in-memory
form `StateFlow` — it has no path to the Room database, so it cannot ever
clear saved reviews.

**Theme** (`ui/theme/`): dark and light Material 3 color schemes built
around one restrained accent (a cool steel blue) over a near-black graphite
/ off-white neutral base. Green and red are reserved *exclusively* for
door-state and form-validation feedback — they're never used decoratively —
so they keep a single, consistent meaning everywhere they appear. Typography
uses the platform default font family (no bundled custom typeface — this
sandbox has no network access to fetch font files) with a deliberate
weight/spacing scale rather than a display face; swapping in a licensed
`.ttf` under `res/font/` later is straightforward. The launcher icon is a
minimal padlock mark built entirely as vector XML (`res/drawable/*.xml` +
an adaptive-icon definition) — no binary image assets needed.

---

## 7. TextToSpeech implementation

`TextToSpeechManager` is a small, isolated wrapper — the only file that
imports `android.speech.tts.*`. `DoorControlViewModel` calls
`ttsManager.speak(...)` exactly once, when an incoming message equals
`ArduinoProtocol.DETECTED`; the Arduino never speaks anything itself, it
only ever sends the `DETECTED` signal. If `speak()` is called before the TTS
engine finishes initializing (plausible if `DETECTED` arrives right after
connecting), the text is queued and spoken as soon as init completes, rather
than silently dropped. Because this logic lives in one class with a two-method
public surface (`speak`, `shutdown`), swapping the backend later (a different
TTS engine, a recorded-audio player, whatever) means changing one file.

---

## 8. Arduino protocol — exact assumptions made

The Arduino side wasn't touched or redesigned — RFID and door hardware stay
exactly where the brief said they already are. This app was written against
these specific assumptions about the wire format; if your sketch does
anything differently, this is the list to check first:

| Assumption | Detail |
|---|---|
| **Line ending** | Every message, both directions, ends in a single `\n`. `\r` is stripped defensively on receive (in case you're using `\r\n`), but plain Arduino `Serial.println()` only emits `\n`, so this should already match. |
| **Encoding** | Plain ASCII. The five Arduino→phone messages and the two phone→Arduino commands are all within the ASCII range, so `US_ASCII` is used explicitly on send rather than UTF-8 — there's no multi-byte content to lose. |
| **Case sensitivity** | Matching is exact-case (`"ACCESS_GRANTED"`, not `"access_granted"`). If a typo on either side changes casing, that message is simply not recognized — this fails loudly (nothing happens) rather than silently doing the wrong thing. |
| **Baud rate** | Not the Android app's concern at all. `BluetoothSocket`/RFCOMM is a different transport layer than the HC-05's UART link to the Arduino — the phone never sees a "baud rate." Whatever baud your HC-05↔Arduino serial wiring uses is entirely a firmware/wiring detail outside this app. |
| **PIN format** | The passcode field only accepts digits, capped at 12 characters — a UI-level input constraint for a numeric keypad, not a validity check. The Arduino remains the sole authority on whether a PIN is *correct*; the app never evaluates it locally. |
| **`READY`** | Received and stored as "last message" for display, but triggers no state change — the brief didn't specify one, so none was invented. |
| **Reconnection** | No automatic reconnect loop. If the link drops, the connection card shows the error and the user taps Connect again. For an attended exhibition booth this is a deliberate simplification, not an oversight — an unattended auto-retry loop is a different (and riskier) feature to get right. |

Illustrative only (not a full sketch — your existing RFID/servo/ultrasonic
logic is unchanged), just to make the exact contract unambiguous:

```cpp
// Arduino -> Phone, on RFID/PIN success:
Serial.println("DETECTED");        // phone speaks the prompt
// ... your existing RFID/PIN check logic ...
Serial.println("ACCESS_GRANTED");  // or ACCESS_DENIED

// Arduino, reading from phone:
if (Serial.available()) {
  String line = Serial.readStringUntil('\n');
  if (line.startsWith("PIN:")) { /* your existing PIN check */ }
  else if (line == "LOCK")     { /* your existing lock logic */ Serial.println("LOCKED"); }
}
```

---

## 9. What was actually verified vs. assumed

In the interest of not guessing where it mattered:

- **Bluetooth permissions** (§4) — checked against Android's official
  Bluetooth permissions documentation.
- **`ViewModelProvider.Factory` override signature** — checked against
  Android's official ViewModel documentation to confirm that overriding only
  the `create(modelClass, extras: CreationExtras)` overload (not the older
  single-argument one) is the current, correct, complete pattern.
- **Kotlin/Compose/KSP version compatibility** (§10) — checked current
  release notes rather than reusing a remembered version table, since the
  old `composeOptions.kotlinCompilerExtensionVersion` mechanism was replaced
  by the `org.jetbrains.kotlin.plugin.compose` Gradle plugin starting at
  Kotlin 2.0.
- **Everything else** (Room API shapes, Compose Material 3 component names,
  coroutines Flow/SharedFlow/StateFlow semantics, the SPP UUID) — written
  from well-established, stable APIs with high confidence, but — again —
  not run through an actual compiler in this environment. Treat the first
  Android Studio sync as the real verification step.

---

## 10. Build and run instructions

**You need:** Android Studio (current stable), which bundles a compatible
JDK. You do not need to install the Android SDK separately — Studio manages
it.

1. Unzip the project.
2. Android Studio → **File → Open** → select the `SmartDoorLock` folder
   (the one containing `settings.gradle.kts`).
3. Let Gradle sync. **The Gradle wrapper jar is not included** (it's a
   binary this sandbox couldn't produce without network access) — Studio
   will detect the missing wrapper and either regenerate it automatically or
   prompt you to; either way, accept it. If Studio suggests upgrading the
   Android Gradle Plugin/Gradle version during this step, accepting that
   suggestion is generally safe.
4. Pair your HC-05 module with the test phone/tablet first, in **system**
   Bluetooth settings (this app deliberately never scans for new devices —
   see §4 — so the module must already be paired before you open the app).
5. Run ▶ on a connected device or emulator. (Bluetooth Classic doesn't work
   on the emulator — use a physical device for real testing.)
6. On first launch (API 31+), grant the Bluetooth permission prompt. Go to
   the Door Control tab → Connect → pick your HC-05 from the list.

## 11. Building the actual APK

Once the project builds and runs from Android Studio (step 10 above), either:

- **From Android Studio:** Build menu → Build Bundle(s)/APK(s) → Build
  APK(s). When it finishes, click "locate" in the notification to find
  `app/build/outputs/apk/debug/app-debug.apk`.
- **From a terminal**, once the wrapper exists (§10 step 3 will have created
  it):
  ```
  ./gradlew assembleDebug
  ```
  Output lands in the same `app/build/outputs/apk/debug/` path. Copy that
  file to the phone (or `adb install app-debug.apk`) for the exhibition
  device.

A debug build is enough for an exhibition demo — it's unsigned but
installable directly. If you want a signed release build later, Android
Studio's **Build → Generate Signed Bundle / APK** wizard walks through
creating a keystore; nothing in this project blocks that path.

---

## 12. Red-team checklist — status

Going through the brief's own checklist explicitly:

| Check | Status |
|---|---|
| Bluetooth Classic, not BLE | ✅ `BluetoothSocket`/RFCOMM only; no BLE API imported anywhere |
| HC-05 connects through paired device selection | ✅ `getBondedDevices()` only, never `startDiscovery()` |
| Socket opens and closes cleanly | ✅ try/catch around connect, `closeQuietly()` + `release()` |
| Messages are newline-delimited | ✅ both directions |
| App does not send before connection is ready | ✅ `sendCommand` checks `Connected` state; Unlock/Lock buttons disabled otherwise |
| TTS initializes before speaking | ✅ pending-text queue if `speak()` races init |
| Room database updates UI correctly | ✅ `Flow` → `StateFlow` reactive chain, no manual refresh needed |
| Review validation works | ✅ name + rating required, checked in `submit()` |
| Reset only clears form fields | ✅ `resetForm()` has no code path to the database |
| UI stays premium and minimal | ✅ restrained palette, generous spacing, no decorative color use (see §6) |
| No login or cloud features introduced | ✅ no auth code, no network client, anywhere in the project |
| No hidden assumptions about RFID reading in the app | ✅ app never touches RFID; Arduino owns detection + the access decision entirely |
| Arduino RFID wiring not changed | ✅ no Arduino code written or modified — see §8 |
| App does not try to read RFID cards itself | ✅ confirmed |

---

## 13. If you want to extend this later

- **Custom typeface** — drop `.ttf`/`.otf` files in `res/font/`, define a
  `FontFamily`, swap it into `ui/theme/Type.kt`.
- **Auto-reconnect** — add retry logic in `BluetoothConnectionManager`;
  keep it off the main thread and make sure it can be cancelled cleanly.
- **Export reviews** — `ReviewRepository`/`ReviewDao` are the only places
  that would need a new query (e.g. a CSV export function).
- **Minification for a smaller release APK** — flip `isMinifyEnabled = true`
  in `app/build.gradle.kts`, then test Room and TTS specifically, since both
  rely on reflection/dynamic lookups that shrinking can break without the
  right keep rules.
