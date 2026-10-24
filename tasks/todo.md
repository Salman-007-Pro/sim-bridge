# SIM Bridge — Task Tracker

Source: `SIM_Bridge_PRD_v1.md`. Har phase ka exit gate pass hone ke baad hi next phase.

## Toolchain

Gradle 9.8.2, AGP 9.4.1 (built-in Kotlin), Kotlin 2.3.21, KSP 2.3.12, Hilt 2.60.1, Room 2.8.5,
Compose BOM 2025.10.01, compileSdk/targetSdk 36, minSdk 30. Build JDK: Android Studio JBR 21.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew assembleDebug testDebugUnitTest test
```

## Phase 0 — Setup and device feasibility

- [x] Git init, Gradle wrapper, version catalog, module scaffold
- [x] Host Capability Probe screen
- [x] `docs/device-matrix.md` template
- [x] Exit gate: both APKs build (`assembleDebug`)
- [ ] Exit gate (device): install on both phones, record probe in device matrix

## Phase 1 — Protocol, crypto, transport

- [x] `core:protocol` envelope, message types, codec, version negotiation, sequence guard
- [x] `core:crypto` fingerprint, pairing token, SAS, QR payload, pinned TLS, Keystore identity
- [x] `core:transport` frame IO, TLS server/client, session, heartbeat, handshake, backoff, NSD
- [x] `core:storage` command ledger (Room + in-memory), paired device store, redacted log
- [x] `core:testing` FakeHost, test identities
- [x] Exit gate: JVM tests pass (model 6, protocol 11, crypto 8, transport 12, storage 2)

## Phase 2 — Pairing, onboarding, dashboards

- [x] Host onboarding (<= 6 screens) + dashboard + foreground service
- [x] Client onboarding (<= 4 screens) + home tabs + session service
- [x] Unpair / wipe, manual IP fallback, `UNPAIR` protocol message (both sides revoke)
- [ ] Exit gate (device): pairing, reject unapproved, reconnect < 15s, unpair revokes

## Phase 3 — SMS bridge

- [x] Host default SMS role components + gateway + Room store
- [x] Sync (`SYNC_REQUEST` / `SYNC_DELTA` with `syncEpoch`) + idempotent send
- [x] Client threads, conversation, compose, status, OTP masking, contact names
- [ ] Exit gate (device): real carrier SMS both ways, no duplicates

## Phase 4 — Remote dialer

- [x] Host default dialer role: InCallService, Host in-call UI, DIAL activity (emergency allowed locally)
- [x] Call adapter + commands, ledger-backed no-redial rule, emergency/premium block for remote dials
- [x] Client dial pad, history, incoming full-screen, ongoing call screen, honest "audio on Host" notice
- [ ] Exit gate (device): incoming/outgoing/missed/rejected/busy/locked

## Phase 5 — MVP hardening

- [x] Boot / app-update recovery, battery + OEM guidance, role revocation pushes new CAPABILITIES
- [x] Redacted logging + scrubbed debug export (both apps)
- [x] Accessibility: TalkBack labels, live regions, 72dp call targets; Compose UI tests (device)
- [x] Signed release APKs in `release-apks/` (local keystore, gitignored)
- [x] JVM reliability: 194/194 ACK on loopback, reconnect 33 ms (`-PsoakSeconds=600` for 10 min)
- [ ] Exit gate (device): 10-minute reliability run on Wi-Fi — see `docs/testing-on-devices.md`

## Phase 6 — Audio R&D

- [x] `core:media` provider interface (PRD 6.3), `UnsupportedAudioProvider`, `AudioSupportPolicy`, `PcmStats` (4 tests)
- [x] Debug-only audio lab: app probes, Shizuku shell probes (uid 2000), telephony TX tone, HFP client probe
- [x] Release APK verified: no RECORD_AUDIO, no Shizuku provider
- [x] `docs/audio-rnd/README.md`: protocol, criteria, results template
- [x] Decision recorded: **NO-GO by default** until physical evidence → ship Tier B
- [ ] Exit gate (device): run lab on 3 Host models / 2 OEMs and revisit decision

## Phase 7 — Audio integration (only if Go)

- Gated: Phase 6 decision is NO-GO until device evidence exists. Not started on purpose (PRD 14: do not build the
  media path before a capture/inject provider is proven).

## Phase 8 — Public readiness

- [x] Security review (`docs/security-review.md`) — 3 findings fixed + tested: LAN-only accept filter,
      handshake flood limits, Host key reset
- [x] Privacy policy draft (`docs/privacy-policy.md`) — needs legal review
- [x] Distribution decision (`docs/distribution.md`) — private sideload; Play deferred
- [x] Compatibility tiers (`docs/compatibility.md`) — all devices Tier B until audio is verified
- [ ] Independent security audit, legal review, Urdu localization (P2)

## Review

**Code status (25 Oct 2026):** Control-only MVP complete in code. 59 JVM tests pass; instrumented tests
(Room repositories, Compose accessibility) compile and need a connected phone. Debug + signed release APKs build.

**Not yet proven on hardware (no phone was connected to adb during development):**
pairing on two phones, real carrier SMS, real calls, OEM background behaviour, 10-minute Wi-Fi reliability,
and every audio experiment. Follow `docs/testing-on-devices.md` and `docs/audio-rnd/README.md`.

**Protocol additions beyond PRD list:** `ACK` (command acknowledgement), `UNPAIR` (two-sided revoke),
`SyncDelta.syncEpoch` (resync after Host store reset).
