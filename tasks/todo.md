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

- [ ] Git init, Gradle wrapper, version catalog, module scaffold
- [ ] Host Capability Probe screen
- [ ] `docs/device-matrix.md` template
- [ ] Exit gate: both APKs build (`assembleDebug`)
- [ ] Exit gate (device): install on both phones, record probe in device matrix

## Phase 1 — Protocol, crypto, transport

- [ ] `core:protocol` envelope, message types, codec, frame codec, version negotiation, sequence guard
- [ ] `core:crypto` fingerprint, pairing token, SAS, QR payload, pinned TLS, Keystore identity
- [ ] `core:transport` TLS server/client, session, heartbeat, handshake, reconnect policy, NSD
- [ ] `core:storage` command ledger, paired device store
- [ ] `core:testing` FakeHost, test identities
- [ ] Exit gate: JVM tests pass

## Phase 2 — Pairing, onboarding, dashboards

- [ ] Host onboarding (<= 6 screens) + dashboard + foreground service
- [ ] Client onboarding (<= 4 screens) + home tabs + session service
- [ ] Unpair / wipe, manual IP fallback
- [ ] Exit gate (device): pairing, reject unapproved, reconnect < 15s, unpair revokes

## Phase 3 — SMS bridge

- [ ] Host default SMS role components + gateway + Room store
- [ ] Sync (`SYNC_REQUEST` / `SYNC_DELTA`) + idempotent send
- [ ] Client threads, conversation, compose, status
- [ ] Exit gate (device): real carrier SMS both ways, no duplicates

## Phase 4 — Remote dialer

- [ ] Host default dialer role: InCallService, Host in-call UI, DIAL activity
- [ ] Call adapter + commands, no-redial rule, emergency/premium block
- [ ] Client dial pad, history, incoming full-screen, ongoing call screen
- [ ] Exit gate (device): incoming/outgoing/missed/rejected/busy/locked

## Phase 5 — MVP hardening

- [ ] Boot recovery, battery guidance, permission revocation handling
- [ ] Redacted logging + debug export
- [ ] Accessibility pass + UI tests
- [ ] Signed release APKs
- [ ] Exit gate (device): 10-minute reliability run

## Phase 6 — Audio R&D

- [ ] `core:media` provider interface + unsupported stub
- [ ] Debug-only audio lab (in-app probes, Shizuku probes, HFP probe)
- [ ] `docs/audio-rnd/` experiment protocol + result template
- [ ] Exit gate (device): Go / No-go

## Phase 7 — Audio integration (only if Go)

## Phase 8 — Public readiness

- [ ] Security review, privacy policy draft, distribution decision, compatibility tiers

## Review

(Har phase ke end par yahan results.)
