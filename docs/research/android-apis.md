# Android side — APIs, permissions, background rules

Status: **first pass (2026-11-01).** developer.android.com / developers.google.com could
not be opened directly from the build environment (network block); facts come from
search results of those pages. Re-check each item against the live docs before coding.

Decided: **minSdk 29 (Android 10)**, package `com.aniket.tapshare`, v1 shares
**contact + photos + any file**.

## 1. Permissions (Nearby Connections "get started" + BLE)

| Permission | Versions | Why |
|---|---|---|
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | maxSdk 30 | old BLE APIs |
| `ACCESS_FINE_LOCATION` | API 29–31 (runtime) | BLE scan needed location before Android 12 |
| `BLUETOOTH_SCAN` (`neverForLocation`) | 31+ (runtime) | our BLE scan |
| `BLUETOOTH_ADVERTISE` | 31+ (runtime) | our BLE advert |
| `BLUETOOTH_CONNECT` | 31+ (runtime) | Nearby + connectedDevice FGS prerequisite |
| `NEARBY_WIFI_DEVICES` | 32/33+ (runtime) | Nearby Connections Wi-Fi upgrade |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | all | Nearby Connections |
| `POST_NOTIFICATIONS` | 33+ (runtime) | foreground-service notification |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` | 28+ / 34+ | background service |
| `SYSTEM_ALERT_WINDOW` | special (Settings screen) | glow overlay when app is closed (Phase 4) |
| `READ_MEDIA_IMAGES` / photo picker | — | **Not needed** if we use the system Photo Picker / document picker (gives a Uri without a permission) |

If any required permission is missing, Nearby Connections **refuses to advertise or
discover** — the permission screen must block until they're granted, with plain-word
reasons.

**Open check:** with `neverForLocation` on Android 12+, some BLE ads get filtered — our
ad is our own service UUID, so should be fine; verify on real phones in Phase 1.

## 2. Foreground service

- Android 14+: must declare `foregroundServiceType="connectedDevice"`, else
  `MissingForegroundServiceTypeException`.
- `connectedDevice` needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` **and** one of the
  Bluetooth/NFC/USB runtime permissions (e.g. `BLUETOOTH_CONNECT`) granted *before*
  `startForeground()`. → Start the service only after the permission screen is done.
- **Android 15**: the `SYSTEM_ALERT_WINDOW` exemption for starting an FGS from the
  background now needs a **visible** `TYPE_APPLICATION_OVERLAY` window first; else
  `ForegroundServiceStartNotAllowedException`. Test with
  `adb shell am compat enable FGS_SAW_RESTRICTIONS com.aniket.tapshare`.
  → Plan: start the service while the app is in the foreground (after permissions) and
  keep it running; restart on boot is a Phase 4 question.

## 3. BLE scanning rules (battery)

- **Unfiltered scans are paused on screen-off** by the system. We want screen-off = stop
  anyway (matches NameDrop "must be unlocked"), so this agrees with our design.
- Use a `ScanFilter` on our service UUID → hardware filtering on supported chips.
- `startScan(filters, settings, PendingIntent)` exists for "process not always running"
  cases, but field reports show OEMs (Xiaomi, OnePlus) batch/delay results by minutes →
  **not suitable for tap detection** (we need results within ~100 ms). Keep the callback
  scan inside the foreground service.
- OEM battery savers (Xiaomi/MIUI, OnePlus, Samsung) may kill the service → the app
  needs a "disable battery optimisation" helper screen (Phase 4).

## 4. Better proximity APIs (future, Open Decision 4)

| Tech | Phones | Notes |
|---|---|---|
| BLE RSSI | all | noisy; v1 plan; calibrate with debug screen |
| NFC | most phones | Google's own Tap to Share uses this; usually back-to-back |
| **Android 16 `RangingManager`** | Android 16+ | one API for UWB, **BLE Channel Sounding** (cm-level distance, Pixel 9/10 tested), BLE RSSI, Wi-Fi RTT |
| UWB | few phones | precise but rare |

`RangingManager` is new and worth a Phase 5 look: same code path could give real
distance on new phones and fall back to RSSI on old ones.

## 5. Nearby Connections (transfer)

- Dependency: `com.google.android.gms:play-services-nearby` (latest version to confirm —
  could not reach Google Maven from the build environment).
- Strategy `P2P_POINT_TO_POINT` (1-to-1, highest bandwidth).
- Sending a file: `contentResolver.openFileDescriptor(uri, "r")` →
  `Payload.fromFile(pfd)` (no extra copy). Works with Photo Picker Uris.
- Receiving a file (Android 10+, scoped storage): the file lands in
  `Downloads/Nearby/...`; we must read it via the **Uri** the service gives
  (`payload.asFile().asUri()`), not a file path, then copy into MediaStore / our folder
  with the real file name sent in a small metadata BYTES payload first.
- Contact card = small vCard as a BYTES payload.
- Built-in encryption; both sides get an auth token/digits on connection — we can show
  or compare them silently as an extra check.

## 6. Build environment note

The cloud build box cannot reach `dl.google.com` (Android SDK + Google Maven), so it
**cannot run `./gradlew assembleDebug`**. Either allow that host in the environment's
network settings, or Aniket builds locally in Android Studio.

## Sources
- [Nearby Connections — Get started](https://developers.google.com/nearby/connections/android/get-started)
- [Nearby Connections — Exchange data](https://developers.google.com/nearby/connections/android/exchange-data)
- [Android 14 — FGS types required](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [SDL guide — connectedDevice needs BLUETOOTH_CONNECT](https://smartdevicelink.com/en/guides/android/migrating-to-newer-sdl-java-suite-versions/updating-to-v57/)
- [Android 15 behaviour changes (SAW + FGS)](https://developer.android.com/about/versions/15/behavior-changes-15)
- [BluetoothLeScanner reference](https://developer.android.com/reference/kotlin/android/bluetooth/le/BluetoothLeScanner)
- [Nordic DevZone — PendingIntent scan stalls on Xiaomi](https://devzone.nordicsemi.com/f/nordic-q-a/117280/android-problem-with-ble-scanning-stopped-while-screen-is-off-on-newer-phones/515318)
- [Android 16 summary](https://developer.android.com/about/versions/16/summary?hl=en)
- [RangingManager reference](https://developer.android.com/reference/android/ranging/RangingManager)
- [Nordic — Channel Sounding on Pixel 10](https://devzone.nordicsemi.com/nordic/nordic-blog/b/blog/posts/evaluating-bluetooth-channel-sounding-with-our-open_2d00_source-android-app-on-google-pixel-10)
- [Argenox — Bluetooth in Android 15](https://argenox.com/blog/whats-new-in-bluetooth-for-android-15)
