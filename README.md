# TapShare

Do Android phones paas lao → dono "Share" dabao → contact / photo / file chali jaaye.
iPhone NameDrop / AirDrop jaisa feel, Android pe. (Working name — final naam baad mein.)

**Abhi ka status: Phase 2 + 3.** Phones jodo → glow + vibration → contact poster (Share / Receive Only),
ya photos/files bhejo (Gallery → Share → TapShare). **Dono phones pe TapShare khula hona chahiye** (app band
hone pe kaam karna Phase 4 mein aayega).

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

## Naya: glow, connection pill, fast mode, app band hone pe (test karo)

| # | Kya karo | Kya dikhna chahiye |
|---|---|---|
| 1 | Phones chhuao (app khula) | Screen ke **chaaron kinaaron pe rainbow glow** — camera se nikal ke dono taraf daudta hai, phir ghoomta hai |
| 2 | Upar dekho | Kaali pill: "Connecting…" → **"Connected · <naam>"** + hari dhadakti dot; connect pe glow ek baar chamakta hai |
| 3 | Share/transfer khatam | Pill: "Disconnected" (grey) kuch second, phir gayab |
| 4 | Transfer ke beech doosre phone ka Wi-Fi/Bluetooth band karo | Pill laal: **"Connection lost"** + error screen |
| 5 | Home → Transfer speed: **Fast mode** on, Wi-Fi off | "Wi-Fi is off" row + **Turn On** (Wi-Fi panel khulta hai) |
| 6 | **Send smaller photos** on karke 5 badi photos bhejo | Bahut jaldi pahunchti hain; receiver pe ~2048 px JPEG |
| 7 | Home → **When TapShare is closed** → Display over other apps → **Allow** | Settings khulta hai → TapShare on karo → wapas aao, row gayab |
| 8 | Android 10/11 phone: Location "Allow all the time" | Allow ke baad row gayab |
| 9 | Dono phones pe TapShare **band** karo (Home button), koi bhi app kholo, phones chhuao | Kinaaron pe glow (doosre app ke upar), TapShare apne-aap khulta hai → poster / Accept |
| 10 | Phone restart karo, unlock karo, phir 9 dohraao | Bina app khole kaam kare (service boot pe khud chalu) |

## Phase 2 + 3 test checklist (2 phones, dono pe nayi APK)

| # | Kya karo | Kya dikhna chahiye |
|---|---|---|
| 1 | Home → **My Card** → naam + photo daalo (dono phones pe) | Home ke upar aapka naam/photo |
| 2 | Dono phones pe TapShare Home khula rakho, **tops ko chhuao** | Pehle "1 TapShare phone nearby…", phir "Hold them together…" |
| 3 | ~1 second chhue rakho | Zor ki vibration + camera ke paas se safed glow + lehar; ~1 s baad **apna poster** (bada naam/photo) |
| 4 | Poster pe upar dekho | "With <doosre ka naam>" + unki chhoti photo |
| 5 | Dono **Share** dabao | Dono pe doosre ka card + **Save Contact** → Contacts app khulta hai, sab bhara hua |
| 6 | Ek **Receive Only**, ek **Share** | Share karne wale pe "Your card was sent", doosre pe uska card |
| 7 | Glow ke beech phones **door kar do** | Chupchaap cancel, wapas Home |
| 8 | Gallery → photo(s) chuno → **Share → TapShare** | TapShare "3 photos ready" screen (thumbnails) |
| 9 | Ab doosre phone se chhuao (doosre pe Home khula) | Doosre phone pe: aapka naam + photo + preview + "wants to share 3 photos" → **Accept / Decline** |
| 10 | Accept | Dono pe progress ring + "2 of 3 · 4 MB of 10 MB"; end pe ✓ + vibration |
| 11 | Receiver pe **Open** | Photo khulti hai; Gallery → Pictures/TapShare mein bhi |
| 12 | Home → Send → **Files** se PDF bhejo | Download/TapShare mein sahi naam se save |
| 13 | Transfer ke beech **Cancel** | Doosre pe "<naam> cancelled" |
| 14 | Wi-Fi band karke bhejo | Chalega (Bluetooth se, slow) + "Wi-Fi is off…" hint |

**Tap nahi ho raha?** Nearby Phones → dono phones ko chhua ke **Calibrate** dabao (3 s). "loss" number aur
"sees this phone ✓" dono taraf dikhna chahiye. Screenshot bhejo.

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
