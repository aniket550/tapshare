# TapShare

Do Android phones paas lao → dono "Share" dabao → contact / photo / file chali jaaye.
iPhone NameDrop / AirDrop jaisa feel, Android pe. (Working name — final naam baad mein.)

**Abhi ka status: Phase 0 (skeleton).** App khulta hai, permissions maangta hai, aur background
service notification ke saath chalti hai. Paas ke phones dhoondhna Phase 1 mein aayega.

## Build kaise karein

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

## Folder structure

```
app/src/main/java/com/aniket/tapshare/
  TapShareApp.kt, AppContainer.kt   app start + manual DI (saari objects ek jagah)
  data/SettingsStore.kt             "sharing on/off" + kaunsi permission pehle maangi
  util/PermissionPlan.kt            har Android version pe kaunsi permission (pure Kotlin, tested)
  util/PermissionsRepository.kt     asli permission state → Flow
  service/ReadinessPolicy.kt        "radios chalein ya nahi?" ka niyam (pure Kotlin, tested)
  service/DeviceConditionsSource.kt screen/lock/Bluetooth/Location → live Flow (no polling)
  service/TapShareService.kt        foreground service (Phase 1 mein BLE yahin judega)
  ui/                               Compose screens + iOS-style components + theme
docs/research/                      research notes (NameDrop flow, animation, Android APIs)
```

Font: [Inter](https://github.com/rsms/inter), SIL Open Font License (`docs/licenses/Inter-OFL.txt`).
