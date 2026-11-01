# Phase 1 — BLE discovery research (2026-11-01)

## Findings
| Topic | Finding | Source | Used as |
|---|---|---|---|
| Advert size | Legacy BLE advert = 31 bytes. A 128-bit UUID costs 18 bytes; service data with a 128-bit UUID costs 18 + payload. | BLE spec / Android `AdvertiseData` | UUID + TX power in the advert (24 B), random ID in the **scan response** (26 B) |
| Scan response | Android scans are active by default, so the scan response is merged into `ScanResult.scanRecord`. | Android BLE behaviour (verify on device) | If a phone misses it, we fall back to the (random) MAC so the phone still shows up |
| Scan throttle | Since Android 7: **> 5 `startScan` in 30 s → results silently stop** (`SCAN_FAILED_SCANNING_TOO_FREQUENTLY`, often not even reported). Not in the official docs any more. | [Punch Through](https://punchthrough.com/android-ble-scan-errors/), [Nordic scanner-compat #18](https://github.com/nordicsemiconductor/android-scanner-compat-library/issues/18) | `ScanThrottle` (max 4 per 30 s) + delayed restart |
| Scanner null | `getBluetoothLeScanner()` returns null while Bluetooth is off. | [Find BLE devices](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices) | Treated as "Bluetooth off" |
| Advertiser null | `getBluetoothLeAdvertiser()` is null when BLE peripheral mode isn't supported (some older phones). | Android reference | Shown as "Not supported" with a plain explanation |
| Battery | Never scan in a loop; filter scans. | Find BLE devices | `ScanFilter` on our UUID; scan/advert only in `Readiness.Ready` |
| Modes | Scan: LOW_POWER / BALANCED / LOW_LATENCY. Advert: LOW_POWER (~1 Hz) / BALANCED (~4 Hz) / LOW_LATENCY (~10 Hz). TX: ULTRA_LOW … HIGH. | Android reference | App visible → LOW_LATENCY both; background → LOW_POWER both; TX selectable (default LOW) |

## Open for Phase 2 (needs Phase 1 numbers from real phones)
- RSSI when phones touch top-to-top vs back-to-back vs 30 cm vs 1 m, for each TX level, on both of Aniket's phones.
- LOW_POWER advert (~1 packet/s) is too slow to confirm a 0.5 s hold → Phase 2 needs a short fast burst after a first strong packet.
- If RSSI can't separate "touching" from "10 cm away" reliably → NFC trigger (Open Decision 1).
