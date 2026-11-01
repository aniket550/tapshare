# TapShare

Do Android phones paas lao → dono "Share" dabao → contact / photo / file chali jaaye.
iPhone NameDrop / AirDrop jaisa feel, Android pe. (Working name — final naam baad mein.)

**Abhi ka status: Phase 1 (discovery).** Dono phones Bluetooth se ek-dusre ko dhoondhte hain aur
**Nearby Phones** screen pe live signal strength (RSSI) dikhti hai. Tap karke share karna Phase 2–3 mein aayega.

## APK seedha phone pe (sabse aasaan)

Har push pe GitHub Actions khud APK banata hai aur **Releases → latest** pe daal deta hai:

1. Phone ke browser mein kholo: **https://github.com/aniket550/tapshare/releases/tag/latest**
2. `TapShare-xxxxxxx.apk` pe tap → download
3. Download khol ke install karo (pehli baar Android "unknown apps" ki permission maangega → Allow)
4. Nayi build aaye to wahi steps — purani app ke upar install ho jaayegi (same debug key), data bana rahega.

Repo private hai to link kholne ke liye GitHub mein login hona chahiye.

## Build kaise karein (computer pe)

Zaroorat: **Android Studio** (latest stable) ya **JDK 17+** + Android SDK **API 37**.

```bash
./gradlew assembleDebug        # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # USB se juda phone pe seedha install
./gradlew test                 # unit tests (phone ki zaroorat nahi)
./gradlew lint
adb logcat -s TapShare         # app ke saare logs
```

Android Studio mein: **File → Open → `tapshare` folder** → Gradle sync → ▶ Run.
SDK 37 na ho to Studio khud download karne ko bolega.

## Phase 0 test checklist (dono phones pe)

| # | Kya karo | Kya dikhna chahiye |
|---|---|---|
| 1 | App kholo (pehli baar) | "Share by bringing phones together" screen, rows: Nearby devices (+ Location on Android 10–12), Notifications (Optional) |
| 2 | **Continue** dabao | Android ke permission popups. Allow karte hi row pe hari ✓ pop hota hai |
| 3 | Sab allow ho gaya | Screen smoothly Home pe badalti hai, upar card "All set", do phones ke beech glow "saans" leta hai |
| 4 | Notification shade kholo | "Ready to share" notification, button **Turn Off** |
| 5 | Bluetooth band karo | Card: "Bluetooth is off" + **Turn On Bluetooth** button. Status mein Bluetooth laal "Off" |
| 6 | Button dabao | Android ka "Bluetooth on karein?" popup → On → card wapas "All set" |
| 7 | "Allow Sharing" switch band karo | Switch spring ke saath slide + halki vibration, card "Sharing is off", service "Stopped", notification gayab |
| 8 | Switch wapas on | Service "Running", notification wapas |
| 9 | Notification se **Turn Off** | Notification hatti, app kholo to switch off dikhe |
| 10 | Screen lock karke unlock | `adb logcat -s TapShare` mein `radios=OFF` (lock pe) aur `radios=ON` (unlock pe) |
| 11 | Permission deny karke dekho (app data clear karo) | Ek baar deny → row pe "Allow" rehta hai. Do baar deny → "Settings" button, footer mein samjhaya hua |
| 12 | Dark mode on karo | Poori app dark (kaala background, grey cards) |
| 13 | Android 10/11 phone ho to Location band karo | Card: "Location is off" + **Open Location Settings** |

Kuch bhi alag dikhe → screenshot + `adb logcat -s TapShare` ka output bhejo.

## Phase 1 test checklist (2 phones)

| # | Kya karo | Kya dikhna chahiye |
|---|---|---|
| 1 | Dono phones pe nayi APK install karo, app kholo | Home pe "Developer → Nearby Phones" row |
| 2 | Dono pe Nearby Phones kholo | "Sending signal: On · fast", "Listening: On · fast" |
| 3 | Phones 1 m door rakho | Doosra phone list mein aata hai (2–3 s mein), "Phone XXXX·XXXX", number jaise −70 dBm |
| 4 | Dheere-dheere paas laao | Number badhta hai (−70 → −50 → −35), bar bharta hai, rang grey → orange → green |
| 5 | Top-to-top chhua ke 5 s rakho → Reset Numbers → phir 5 s | avg / min / max likh lo |
| 6 | Back-to-back yahi karo | avg / min / max likh lo |
| 7 | Signal strength "Ultra low" aur "High" pe 3–6 repeat karo (dono phones pe same setting) | numbers likh lo |
| 8 | Ek phone lock karo | Doosre phone se 8 s mein wo phone list se hat jaata hai |
| 9 | Bluetooth band karo | "Not looking right now — Bluetooth is off" |

**Mujhe yeh table bhar ke bhejo** (Phase 2 ka tap-threshold isi se set hoga):

| Setting | Position | avg | min | max |
|---|---|---|---|---|
| Low | top-to-top chhua hua | | | |
| Low | back-to-back chhua hua | | | |
| Low | 30 cm door | | | |
| Low | 1 m door | | | |
| Ultra low / High | (wahi 4) | | | |

## Folder structure

```
app/src/main/java/com/aniket/tapshare/
  TapShareApp.kt, AppContainer.kt   app start + manual DI (saari objects ek jagah)
  data/SettingsStore.kt             "sharing on/off" + kaunsi permission pehle maangi
  util/PermissionPlan.kt            har Android version pe kaunsi permission (pure Kotlin, tested)
  util/PermissionsRepository.kt     asli permission state → Flow
  service/ReadinessPolicy.kt        "radios chalein ya nahi?" ka niyam (pure Kotlin, tested)
  service/DeviceConditionsSource.kt screen/lock/Bluetooth/Location → live Flow (no polling)
  service/TapShareService.kt        foreground service — Ready hone pe discovery chalu, warna band
  discovery/                        BLE advert + scan, random ID (15 min), RSSI table, scan throttle
  ui/                               Compose screens + iOS-style components + theme
docs/research/                      research notes (NameDrop flow, animation, Android APIs)
```

Font: [Inter](https://github.com/rsms/inter), SIL Open Font License (`docs/licenses/Inter-OFL.txt`).
