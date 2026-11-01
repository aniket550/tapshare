# NameDrop / AirDrop proximity — flow research

Status: **Phase 0, first pass (2026-11-01).** Based on web search only. Apple's own
support pages could not be opened directly from the build environment (network block),
so Apple facts below come from articles that quote Apple. Timings marked **(est.)** are
estimates and must be checked frame by frame against a reference video.

## 1. What the user does (iPhone)

1. Both iPhones (or iPhone + Apple Watch) are **unlocked**.
2. Hold the **top edges** of the phones a few centimetres apart, for "a second or two".
3. A glow comes out of the top of both screens, with a vibration.
4. About a second later each phone shows **its owner's own** Contact Poster, with two
   buttons: **Receive Only** and **Share**.
5. The user can pick which phone number / email goes out before sharing.
6. **Share** = send mine + get theirs. **Receive Only** = only get theirs.
   If nobody taps a button, nothing is shared.
7. After the exchange, the other person's card is shown and can be saved.

The same "bring together" gesture also starts an AirDrop transfer for photos/files
when a share sheet / photo is open (Apple calls the setting "Bringing Devices Together").

## 2. What gets shared (NameDrop)

| Shared | Not shared |
|---|---|
| Name | Home address |
| The phone number **or** email the user picked | Birthday |
| Contact Poster (photo/look) | Other card fields |
| | Pronouns (off by default) |

→ For TapShare: the user picks the fields; default = name + one phone number + photo.

## 3. Cancel / failure cases

| Event | iPhone behaviour | TapShare plan |
|---|---|---|
| Pull phones apart soon after the glow starts | Cancels | RSSI drops below "exit" threshold for > N ms → cancel, reverse the glow |
| Lock the phone | Cancels | `ACTION_SCREEN_OFF` → cancel + stop radios |
| Bluetooth or Wi-Fi turned off | Stops | Listen to adapter state → cancel with a clear message |
| Nobody taps Share / Receive Only | Nothing shared | Timeout (value TBD from video) → dismiss |
| Big AirDrop file, phones moved apart mid-transfer | iOS 17+: can continue over the internet (iCloud) | **Can't match** — no server in v1. Nearby Connections needs the phones roughly in range (it uses Wi-Fi, so a few metres is fine, not only touching). Tell the user "keep phones nearby". |

## 4. Timing table (to be filled from a reference video)

| Step | iPhone (est.) | Source | Ours |
|---|---|---|---|
| Phones held together → first glow pixel | ~0.5–1 s "a second or two" | AppleInsider | hold RSSI ~0.5 s, then trigger |
| Haptic | at glow start | TapSmart | fire on the same frame as glow start |
| Glow grows / ripples down the top of the screen | ~1 s (est.) | SlashGear: "about a second later" the card appears | TBD |
| Contact Poster appears | ~1 s after glow | SlashGear | TBD |
| Transfer animation | TBD | — | TBD |
| Done state | TBD | — | TBD |

**Needed from Aniket:** one screen recording (or slow-mo video filmed from the side) of
two iPhones doing NameDrop, plus one doing a photo AirDrop by bringing phones together.
No written source gives the millisecond timings, easing or exact colours.

## 5. How Google does the same thing on Android (important)

Google released **"Tap to Share"** inside Quick Share on **12 Aug 2026**:
- Rolling out to **Pixel 6 and newer**; Samsung Z Fold8 / Flip8 next; "other Android
  devices by end of year".
- Uses **NFC** to detect the tap. Both phones need NFC on and must be unlocked.
- Older Pixels tap **back-to-back** (NFC antenna is on the back). Pixel 11 moved NFC to
  the top edge so it can tap top-to-top like iPhone.
- From the home screen: two-way contact exchange. From the share sheet: photo/file send.
- Phones are held together "until the screens glow".

What this means for us:
1. Our idea is no longer unique on Pixels — but on most non-Pixel phones it isn't out yet,
   and building it is still a great learning project.
2. Google picked **NFC**, not BLE RSSI, as the "phones are touching" signal. RSSI alone is
   noisy (phone model, case, hand position). NFC is a very reliable "touching" signal, but
   only works where the NFC antennas line up (usually back-to-back on Android).
   → Recommendation: keep BLE RSSI for v1 (as planned), but treat **NFC as a strong
   candidate** for Open Decision 4, and decide after Phase 1 debug-screen data.

## Sources
- [AppleInsider — How to use NameDrop (2026)](https://appleinsider.com/articles/26/05/22/how-to-use-namedrop-to-exchange-iphone-contacts-in-seconds)
- [TapSmart — Trade contacts with NameDrop](https://www.tapsmart.com/tips-and-tricks/namedrop/)
- [SlashGear — How to use NameDrop](https://www.slashgear.com/1440375/how-to-use-namedrop-apple-iphone/)
- [Apfelpatient — NameDrop how to use it right](https://www.apfelpatient.com/tips-tricks/namedrop-iphone-apple-watch-share-contacts)
- [MacRumors — Share contact details with NameDrop](https://macrumors.com/how-to/ios-share-contact-details-with-namedrop)
- [MacRumors — AirDrop proximity sharing](https://www.macrumors.com/how-to/ios-share-files-media-airdrop-proximity/)
- [Cult of Mac — iOS 17 NameDrop / internet continuation](https://www.cultofmac.com/news/ios-17s-namedrop-makes-sharing-contact-info-and-more-a-breeze)
- [Apple Personal Safety guide — Secure NameDrop](https://support.apple.com/en-ca/guide/personal-safety/secure-namedrop-ips97e16d3b1/web) (not opened directly — network block)
- [9to5Google — Tap to Share rolling out (Aug 2026)](https://9to5google.com/2026/08/12/pixel-tap-to-share/)
- [TechCrunch — Quick Share adds tap-to-share](https://techcrunch.com/2026/08/12/googles-quick-share-adds-a-tap-to-share-mode/)
- [Android Authority — Tap to Share launch](https://www.androidauthority.com/google-quick-share-tap-to-share-3697202/)
- [Android Authority — Gesture Exchange teardown](https://www.androidauthority.com/google-contact-gesture-exchange-apk-teardown-3615939/)
- [GSMArena — NFC placement / back-to-back advice](https://www.gsmarena.com/more_details_about_googles_namedrop_clone_for_android_leak-news-72335.php)
