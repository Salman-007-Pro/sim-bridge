# Device Compatibility Matrix

PRD section 13 ke mutabiq har tested phone ka record. Host app ke **Device capabilities** screen par Share button
probe row deta hai; usay neeche "Probe" table mein paste karein, phir test results bharein.

## Tiers

- **Tier A — Full:** verified calls + bidirectional audio + SMS on exact Host/Client builds.
- **Tier B — Control:** pairing, call controls, SMS work; cellular audio unsupported.
- **Tier C — Unsupported:** missing roles/radios/permissions or blocked transport.

## Probe (auto-generated rows)

| Model | Manufacturer | SoC | Android | Patch | SIM | Operator | Modems | Dialer role | SMS role | Wi-Fi Direct | BLE | Audio | Build fingerprint |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Test results

| Device (role) | VoLTE | Transport | Setup / privileges | Pairing | Incoming call | Outgoing call | SMS in | SMS out | Audio RX | Audio TX | Crashes | Tier | Limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## How to record

1. `adb install -r apps/host/build/outputs/apk/debug/host-debug.apk` (Host phone) aur client APK (Client phone).
2. Host app kholein, **Share report** se row copy karein.
3. Har phase ke exit gate test ke baad "Test results" row update karein.
