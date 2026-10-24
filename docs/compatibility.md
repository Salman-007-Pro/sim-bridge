# Compatibility

**Public wording:** "Supports Android devices meeting the listed requirements; voice audio availability varies by
Host model and Android version." Never say "works on all Android phones".

## Requirements

| | Host phone | Client phone |
|---|---|---|
| Android | 11 (API 30) or newer | 11 (API 30) or newer |
| SIM | Working cellular SIM | Not required |
| Roles | Default phone app and default SMS app must be available and granted | — |
| Network | Wi-Fi or its own hotspot | Same Wi-Fi, or connected to the Host's hotspot |
| Background | Battery optimisation off, OEM auto-start on | Same |

## Tiers

- **Tier A — Full:** verified calls + bidirectional audio + SMS on exact published Host/Client builds.
  _No device is Tier A. Audio is gated by `docs/audio-rnd` (currently NO-GO)._
- **Tier B — Control:** pairing, call alerts and controls, SMS. Call audio stays on the Host.
  _This is the shipping configuration for every supported device._
- **Tier C — Unsupported:** Android 10 or older, phone/SMS roles not available (some tablets / no telephony),
  or a network that blocks device-to-device traffic (client isolation on public Wi-Fi).

## Known limitations (all tiers)

- MMS, RCS, USSD, VoLTE/VoWiFi settings and emergency calling are not bridged.
- Bank or 2FA apps that check for a local SIM will not see one on the Client.
- While SIM Bridge Host is the default SMS / phone app, the Host's previous apps stop receiving new SMS and calls.
- Calls ring on both phones; the conversation happens on the Host (or a headset paired to it).

## Verified devices

Fill from `docs/device-matrix.md` after the device test pass in `docs/testing-on-devices.md`.

| Host | Client | Android | Tier | Date | Notes |
|---|---|---|---|---|---|
