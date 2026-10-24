# Audio R&D gate (PRD section 6)

## Current decision

**NO-GO (default) — 24 Oct 2026.** No physical-call evidence exists yet, so SIM Bridge ships as
**Tier B (control-only)**: calls are controlled from the Client but the conversation stays on the Host phone.
`HostModule` binds `UnsupportedAudioProvider`, and `AudioSupportPolicy` can only report `VERIFIED` for an exact
build fingerprint with a recorded passing experiment. Phase 7 (audio integration) starts only after a GO below.

## Go / No-go criteria (PRD 6.4)

- **GO for audio beta:** both directions pass on **3 Host models from 2 OEM families**, reproducible setup, stable.
- **NO-GO if:** audio only works through speaker/mic feedback; depends on permissions that vanish on reboot without
  usable re-activation; or fails during calls.
- Targets: < 250 ms added latency, < 1% packet loss on stable Wi-Fi, 10-minute call without crash, no sustained howl.

## Providers under test

| Provider | How the lab tests it | Expected |
|---|---|---|
| A · Public SDK | Lab card A: `VOICE_DOWNLINK/UPLINK/CALL` from the app | Blocked (needs `CAPTURE_AUDIO_OUTPUT`) |
| B · Shizuku / ADB shell | Lab card B: same sources as uid 2000 + telephony TX tone | Device-specific; main experiment |
| D · Bluetooth HFP client | Lab card D: `HEADSET_CLIENT` profile proxy | Usually unavailable to apps |
| F · Acoustic loopback | Not implemented on purpose | Lab demo only, never production |
| C / E · OEM partnership, external gateway | Out of scope for software lab | Separate tracks |

## Lab setup

1. Install the **debug** Host APK (`./gradlew :apps:host:installDebug`). Release APKs contain no lab.
2. Host dashboard → **Audio lab (debug)** → *Grant microphone and Bluetooth*.
3. For Provider B install [Shizuku](https://shizuku.rikka.app/guide/setup/), start it via Wireless debugging,
   then tap *Grant Shizuku access*. The status chip must show `Ready · uid 2000`.
4. Call an independent third phone **with the other person's consent**. Keep the call active.

## Experiment protocol (one row per Host build)

1. Record OEM, model, SoC, Android/API, patch, VoLTE/2G/3G, build fingerprint (lab *Share results* header).
2. Verify answer / reject / hang-up from the Client independently of audio (Phase 4 already does this).
3. **Downlink:** third phone speaks continuously; run capture probes. Pass = `SIGNAL` on a downlink/call source
   while the Host microphone is covered, and the saved capture is intelligible (not just room sound on `MIC`).
4. **Uplink:** run *Play tone to telephony TX*. Pass = the third phone hears the 1 kHz beep while the Host mic is
   covered / muted.
5. Repeat with simultaneous speech, mute, earpiece/speaker, headset, screen off, app in background, 10 minutes.
6. Log packet loss, one-way latency, jitter, echo, route changes and exceptions (Phase 7 transport needed for loss/latency).
7. Reboot; note whether Shizuku must be re-activated and whether the result repeats.
8. Repeat on more Android versions / OEMs.

## Results

Copy *Share results* output into `results/<brand>-<model>-<date>.txt`, then add a row:

| Date | Host model | Android / patch | VoLTE | Provider | Downlink | Uplink | Mic isolated | 10 min | After reboot | Notes |
|---|---|---|---|---|---|---|---|---|---|---|

No rows yet → decision stays NO-GO.
