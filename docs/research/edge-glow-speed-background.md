# Edge glow, transfer speed, background mode (2026-11-01)

Aniket asked for: a Mac/iPhone **Siri-style rainbow edge glow** when phones connect, a clear
**connected / disconnected** indicator, a **faster transfer** option, and working **with the app closed**.

## 1. Edge glow (Apple Intelligence / Siri style)
- No official spec (colours, timing) exists. Descriptions: a rainbow glow around the screen edge that
  drifts, blooms past the bezel and fades when done; it's a *status* indicator ("working on it").
- Common recreation: inset rounded rectangle stroked with an angular (sweep) gradient that rotates,
  plus one or two blurred copies for the bloom. Keep it to edge pixels (cheap), fixed blur radii.
- Ours: intro = glow runs out of the camera and travels around both edges (~0.7 s), then rotates
  (one turn ≈ 3 s) while connecting/transferring, brightness pulse on "connected", fade out at the end.
  Colours (ours): blue #3A7BFF, violet #A15BFF, pink #FF5FA8, orange #FFA24A, cyan #3EDCFF.
- Drawn with `android.graphics` (sweep gradient + `BlurMaskFilter`, hardware-accelerated since API 28)
  so the same painter works inside the app (Compose) and in the background overlay window (View).

## 2. Transfer speed (Nearby Connections)
| `ConnectionType` | Meaning | Ours |
|---|---|---|
| BALANCED (0, default) | changes Wi-Fi/BT only if needed | "Fast mode" off |
| DISRUPTIVE (1) | changes Wi-Fi/BT for best throughput — **may drop internet** during transfer | "Fast mode" on (default) |
| NON_DISRUPTIVE (2) | never touches radios | not used |
- Set via `ConnectionOptions.setConnectionType` (requester) and `AdvertisingOptions` (advertiser).
- Wi-Fi off → transfer falls back to Bluetooth (slow). Fast mode asks to turn Wi-Fi on (Settings panel).
- "Send smaller photos": decode with `ImageDecoder` (applies EXIF rotation), long side ≤ 2048 px,
  JPEG q82 → typically 5–10× smaller. Videos/other files untouched.

## 3. Background (app closed, screen on + unlocked)
- Background **activity** starts are allowed when the app holds `SYSTEM_ALERT_WINDOW` (user-granted)
  — still listed on the official page (Android 10 → 17).
- Android 15: starting a FGS from background via SAW also needs a **visible overlay window** first.
- `connectedDevice` FGS may start from `BOOT_COMPLETED` (Android 15's boot restriction covers dataSync,
  camera, mediaPlayback, phoneCall, mediaProjection, microphone only).
- Android 10/11: BLE scan results in the background need `ACCESS_BACKGROUND_LOCATION`
  ("Allow all the time"); must be requested separately after foreground location.
- Battery: background = BALANCED advert (~4/s) + BALANCED scan; a phone getting near triggers a
  ≤ 10 s LOW_LATENCY burst (at most one per 20 s, to stay under the 5-scan-starts/30 s limit).

Sources: [VP0 glow border guide](https://vp0.com/blogs/apple-intelligence-glow-border-effect-code-ios-free-ios-template-vibe-coding-gui) ·
[SlashGear: why iPhone glows at the edges](https://www.slashgear.com/1865686/iphone-glowing-around-edges-reason/) ·
[Nearby ConnectionType](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/ConnectionType) ·
[ConnectionOptions.Builder](https://developers.google.com/android/reference/com/google/android/gms/nearby/connection/ConnectionOptions.Builder) ·
[Background activity starts](https://developer.android.com/guide/components/activities/background-starts) ·
[Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15) ·
[Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
