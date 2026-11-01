# CLAUDE.md — TapShare (working name)

## What this project is
An Android app that copies the feel of iPhone NameDrop / AirDrop:
**bring two phones close → they detect each other → both users tap "Share" → contact / photo / file goes across.**

- Owner: Aniket (student, building this to learn and to use). He builds and installs the APK himself.
- Platform: **Android first.** iPhone is out of scope for v1 (see "iPhone limits" below).
- Goal: the **look and feel — animation, haptics, screens, flow — must match iPhone NameDrop/AirDrop "same to same"**. This is the top priority for UI.
- The system-level behaviour can't be 100% identical (NameDrop is built into iOS). When something can't match, tell Aniket honestly and give the closest option — never silently ship something weaker.
- Note (Nov 2026): Google shipped its own NFC-based "Tap to Share" in Quick Share (Pixel 6+, Aug 2026, more phones by end of 2026). See `docs/research/namedrop-flow.md` §5.

---

## Research first (mandatory — before building ANY feature or animation)
Don't build from memory or guesses. For every feature:
1. **Search the web for how iPhone does it**: Apple Support pages (NameDrop, AirDrop), Apple Human Interface Guidelines (motion, haptics), WWDC sessions, slow-motion / screen-recorded YouTube videos of NameDrop and AirDrop (iOS 17 and newer), articles and teardowns. Also study Google's own NameDrop-style feature in Play Services ("Gesture Exchange" / contact exchange, shipped as "Tap to Share") to see how Android does it.
2. **Study the details**: what appears, where it starts, colours, size, how long each step takes (in ms), easing/spring, when haptics fire, sound, what both phones show, what happens on cancel / failure / phones pulled apart.
3. **Write findings** to `docs/research/<topic>.md`: short notes, a timing table, and source links. Show Aniket a short summary before building.
4. **Research the Android side too**: check current Android docs for every API used. Permissions and background rules change every year — never rely on memory.
5. If research leaves something unclear, ask Aniket for a reference video/screenshot instead of guessing.

---

## How it works (big picture)

```
 Phone A                                   Phone B
┌───────────────────────┐                ┌───────────────────────┐
│ 1. BLE advertise+scan │ ◄────────────► │ 1. BLE advertise+scan │   always on, low power,
│    (low power)        │                │    (low power)        │   only while screen is on
└──────────┬────────────┘                └──────────┬────────────┘
           │  RSSI very strong for ~0.5 s AND both phones see each other
           ▼                                        ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. "TAP" detected → short burst of fast scanning to confirm     │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Handshake: show "Share with <name>?" on BOTH phones          │
│    nothing is sent until both users accept                      │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Transfer over Nearby Connections (uses Wi-Fi Direct /        │
│    hotspot under the hood → fast, encrypted)                    │
└─────────────────────────────────────────────────────────────────┘
```

Mapping to iPhone tech:
| iPhone | This app |
|---|---|
| Always-on BLE discovery | BLE advertise + scan in a foreground service |
| Proximity check (sensor / UWB) | BLE RSSI threshold (v1), NFC (optional), UWB / Android 16 RangingManager (optional) |
| AWDL direct Wi-Fi for data | Nearby Connections (v1), raw Wi-Fi Direct (fallback, later) |

---

## Tech stack (decided)
- **Kotlin** + **Jetpack Compose** (Material 3)
- Coroutines + Flow for async/state
- **Google Nearby Connections API** (`play-services-nearby`) for transfer, strategy `P2P_POINT_TO_POINT`
- Android BLE APIs (`BluetoothLeAdvertiser`, `BluetoothLeScanner`) for discovery + tap detection
- Foreground service, type `connectedDevice`
- Gradle Kotlin DSL, version catalog (`libs.versions.toml`)
- Keep dependencies minimal. No Hilt/DI framework unless Aniket agrees — manual DI is fine for this size.
- **minSdk 29 (Android 10)** — decided 2026-11-01. **compileSdk / targetSdk 37** (Compose 1.12 requires compileSdk 37).
- Toolchain: AGP 9.5.0 (built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android`), Kotlin 2.4.21 via root `buildscript` classpath, Gradle 9.8.2, JDK 17+, Compose BOM 2026.09.00.
- UI font: Inter (OFL, bundled in `res/font`, licence in `docs/licenses/`). iOS-style components live in `ui/components/Components.kt` — reuse them, don't use Material widgets directly.
- **Package `com.aniket.tapshare`** (fixed) — decided 2026-11-01. Display name: "TapShare" placeholder, final name later (easy to change).
- **v1 shares: contact card + photos + any file** — decided 2026-11-01. Build order inside Phase 3: contact (vCard) → photos → any file. Use the system Photo Picker / document picker (no storage permission).
- Code lives in its own repo (not in `aniket550/aniket550`, which is the English 30 app).

## Open decisions — ASK Aniket before choosing
Do not guess these. Ask, then record the answer in this file.
1. Whether to add **NFC tap trigger** and **UWB / RangingManager** after v1.
   (Decided 2026-11-01: background mode uses SYSTEM_ALERT_WINDOW + ACCESS_BACKGROUND_LOCATION on Android 10/11.) (Research note: Google's Tap to Share uses NFC — decide after Phase 1 RSSI data.)
2. Whether an **iPhone version** is wanted later.
3. Final **display name** of the app.

---

## Project structure (target)
```
app/src/main/java/com/aniket/tapshare/
  discovery/   BleAdvertiser, BleScanner, ProximityDetector (RSSI logic)
  handshake/   pairing/consent flow, session tokens
  transfer/    TransferManager (interface), NearbyTransfer, (later) WifiDirectTransfer
  share/       ContactCard (vCard .vcf), file/photo payload helpers
  service/     TapShareService (foreground service, screen on/off handling)
  ui/          Compose screens: Home, Permissions, ContactPoster, Progress, Settings, Debug
  ui/animation/  camera glow (AGSL shaders), ripple, overlay window, haptics
  util/        permissions, logging (tag "TapShare")
app/src/main/res/raw/   .agsl shader files (if kept as files)
docs/research/          research notes (see "Research first")
```
Rule: discovery, handshake and transfer must stay separate, so the transfer method can be swapped (Nearby ↔ Wi-Fi Direct) without touching discovery.

---

## Build phases (do in order, one at a time)
Status: Phase 0 ✅, Phase 1 ✅ tested by Aniket (2026-11-01; Ultra low: touching ≈ −43 dBm, far ≈ −80). Phase 2+3 ✅ working on his phones. 2026-11-01 round 2 (his choices): **rainbow edge glow** (Siri-style) replaces the camera ripple; **connection pill** (Connecting → Connected · name → Disconnected/Connection lost); **Fast mode** (Nearby DISRUPTIVE, default on) + **Send smaller photos** (opt-in); **Phase 4 background** (overlay permission + Android 10/11 background location — he approved both). Waiting for his test. See `docs/research/edge-glow-speed-background.md`.
APK: every push to `main` → GitHub Actions → https://github.com/aniket550/tapshare/releases/tag/latest
Finish and test each phase on two real phones before starting the next.

- **Phase 0 — Research + skeleton**: research notes in `docs/research/` (NameDrop flow, animation timings, haptics). Compose app, permission screen, empty foreground service with notification.
- **Phase 1 — Discovery**: BLE advertise + scan with our service UUID. **Debug screen** listing nearby TapShare phones with live RSSI (needed to calibrate the tap threshold).
- **Phase 2 — Tap detection + glow**: RSSI threshold + must hold ~0.5 s + both phones detect each other → trigger. Camera glow animation + haptic, then contact poster screen with Receive Only / Share on both phones.
- **Phase 3 — Transfer**: send contact card (vCard) via Nearby Connections, with the transfer animation. Then photos, then any file. Progress + cancel + done screens.
- **Phase 4 — Background mode + battery**: works with app closed (screen on & unlocked) including the overlay glow, measure battery, tune.
- **Phase 4.5 — Animation polish**: side-by-side video comparison with iPhone, tune until it matches.
- **Phase 5 — Optional**: NFC tap trigger, UWB / RangingManager precision, Wi-Fi Direct fallback (phones without Play Services), receive history.

---

## Animation & look — must match iPhone (top priority for UI)

### What iPhone does (starting point — verify with research, then fill exact timings)
1. Phones touch → **strong haptic** + a **sparkle / white glow emerges from the top of the screen**, right around the camera (Dynamic Island). It ripples and **warps the top part of the screen** — organic, liquid-looking, not a simple fade.
2. About a second later, each phone shows its **owner's own contact poster full-screen**, with two buttons: **Receive Only** and **Share**.
3. During the transfer another glow/ripple animation plays. Pulling the phones apart early cancels it.
4. Happens on both phones at the same time, so both animations must start in sync.

### How to build it on Android
- **Glow starts from the front camera** of each phone. Get the camera hole position from `WindowInsets` → `DisplayCutout.boundingRects`. Handle: centre punch-hole, corner punch-hole, waterdrop notch, and no cutout (fallback: top-centre).
- **Ripple / warp / glow**: AGSL `RuntimeShader` (Android 13+). Fallback for older phones: Compose `Canvas` with radial gradients + blur — must still look close.
- **Warping what's behind**: an app can't read other apps' pixels (only via screen-recording permission — don't use that). Fake the warp with a refraction-style shader on our own layer; try cross-window background blur (Android 12+, check `isCrossWindowBlurEnabled`) for the hazy look.
- **Motion**: spring / iOS-like easing, never linear. Smooth at 60/90/120 Hz, zero dropped frames. Tune timings against the iPhone reference video frame by frame.
- **Haptics**: `VibrationEffect.Composition` primitives (Android 11+), fired exactly with glow start, connect and transfer complete. Check `arePrimitivesSupported` (unsupported primitives play nothing). Fallback: `createWaveform`.
- **Contact poster screen**: same layout and feel as iOS — big name, large photo, Receive Only / Share buttons, same transitions.
- **When the app is closed**: draw the glow in an overlay window (`TYPE_APPLICATION_OVERLAY`, "Display over other apps" permission) laid out into the camera cutout area. Status bar icons may sit on top — test how it looks on real phones. Check current Android rules for showing UI / starting services from the background (Android 15 tightened the overlay-permission exemptions; a visible overlay window is needed first).

### Don't
- Don't copy Apple's actual files — no extracted sounds, images, icons, SF Symbols, SF Pro font, or code from iOS. Recreate everything ourselves in code (use a free font like Inter for the iOS-like look).
- Don't use the Apple logo or call our features "NameDrop" / "AirDrop" inside the app.

### Done means
Record our app and an iPhone doing the same thing, put the videos side by side. If a normal person can't easily tell the difference in the animation, it's done. If not, keep tuning.

---

## Hard rules

### Battery (target: ~1–3% per day in background)
- Scan **only when screen is on and unlocked** (NameDrop also needs unlocked phones). Listen for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` / `ACTION_USER_PRESENT` with a dynamically registered receiver in the service. Stop scan + advertise on screen off.
- Background scan: `SCAN_MODE_LOW_POWER` + `ScanFilter` on our service UUID (lets supported chips filter in hardware so the CPU sleeps).
- `SCAN_MODE_LOW_LATENCY` only in short bursts (≤ 10 s) after a candidate is found, or while the app is open.
- Advertise with `ADVERTISE_MODE_LOW_POWER` and **low TX power** — low TX also makes the RSSI threshold mean "really close", which is what we want.
- Nearby Connections / Wi-Fi stays OFF until both users accept. Tear it down right after transfer.
- No wake locks. No polling loops.
- `startScan(filters, settings, PendingIntent)` was evaluated: OEMs (Xiaomi, OnePlus) batch results by minutes → too slow for tap detection. Keep callback scan in the foreground service.

### Tap detection
- Implemented in `discovery/TapDetector.kt` (pure, tested): **path loss** = peer TX − smoothed RSSI, enter ≤ 32 dB (default, calibratable), exit = enter + 8 dB, hold 600 ms, ≥ 3 samples. Mutual via `closeTo` (4-byte ID prefix) + `FLAG_TRIGGERED` in the 13-byte scan-response service data. Disarmed after a tap until pulled apart.
- RSSI varies a lot between phone models. Never hardcode one magic number without the debug screen data; keep the threshold in a config with a per-device calibration option.
- Require **mutual detection** (A sees B and B sees A) to avoid false triggers from someone sitting nearby.
- Phones' antennas are in different places — test back-to-back and top-to-top.

### Privacy & security
- BLE advertisement carries **only a random rotating ID** (rotate ~every 15 min). Never put name, phone number, or email in BLE ads.
- After a mutual tap, Nearby Hello carries the **display name + 96 px photo** (like AirDrop showing who's sending). Phone/email only in the Card message after "Share"; files only after the receiver taps Accept. Message format: `handshake/SessionMessages.kt` (versioned, tested).
- Nothing is sent before **both** users accept. Never auto-accept files.
- Contact card fields are chosen by the user (they decide what to share).
- Rely on Nearby Connections' built-in encryption; don't roll custom crypto.

### Permissions (handle every Android version, minSdk 29)
- Single source of truth: `util/PermissionPlan.kt` (pure Kotlin, unit-tested). Change permissions there + manifest together.
- Android 10–12L: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (runtime, maxSdk 32) — BLE scan (≤ 11) and Nearby Wi-Fi scan (≤ 12L). Legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30).
- Android 12+: `BLUETOOTH_SCAN` (with `neverForLocation`), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`
- Android 13+: `NEARBY_WIFI_DEVICES` (`neverForLocation`), `POST_NOTIFICATIONS` (optional — app works without it)
- Android 17+: `ACCESS_LOCAL_NETWORK` (same Nearby devices group, no extra prompt)
- Android 14+: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE`, service declared with `foregroundServiceType="connectedDevice"`. Prerequisite is met by the declared `CHANGE_WIFI_STATE`.
- Start the foreground service **only while our Activity is visible** (`ServiceController.sync`). Android 12+ blocks background starts. Service is `START_NOT_STICKY` until Phase 4.
- Broadcast receivers for system state use `RECEIVER_EXPORTED` (Bluetooth state comes from a privileged non-system app) and only trigger a re-read of the real value.
- Open (Phase 4): Android 10/11 need `ACCESS_BACKGROUND_LOCATION` for BLE discovery from a background service — ask Aniket before adding.
- Photos/files: system Photo Picker / document picker → no storage permission.
- Full table: `docs/research/android-apis.md`.
- Explain each permission to the user on the permission screen in plain words.

---

## Commands
```bash
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on connected phone
./gradlew test                 # unit tests
./gradlew lint
adb logcat -s TapShare         # app logs
```
Testing needs **two real Android phones** — emulators have no real BLE/Wi-Fi Direct.
Unit-test pure logic (RSSI smoothing, threshold, vCard building, state machine) without devices.
Cloud sessions: `dl.google.com` (Android SDK + Google Maven) must be allowed in the environment's network settings, or the build can't run there.

---

## iPhone limits (so nobody over-promises)
- iOS apps cannot copy NameDrop at system level; the AWDL Wi-Fi link Apple uses is private.
- iOS heavily restricts BLE scanning/advertising in the background, so an iPhone version would mostly work **only with the app open**.
- Android ↔ iPhone sharing is possible later but only with the app open on the iPhone. Not in v1.

---

## How to work with Aniket
- **Quality bar: work like a senior Android engineer.** Finished, polished, tested on real phones. No placeholder UI, no half-done features, no "TODO later" left silently. Handle every error case: Bluetooth off, Wi-Fi off, permission denied, phones pulled apart, transfer failed, low storage.
- Talk to him in **Hinglish**, simple words. He is learning — briefly explain **why** a change is made, not just what.
- **Understand the request fully before building.** If anything is unclear, ask first — don't guess, and don't change or remove things he didn't ask about.
- One phase / one feature at a time. After each one, give him exact steps: build → install on both phones → what to test → what he should see.
- When a feature can't be done like the iPhone, say so honestly and offer the closest option.
- Update this file when a decision is made (move it from "Open decisions" to "Tech stack" / rules).
