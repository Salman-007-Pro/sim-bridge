# SIM Bridge — Product Requirements Document (PRD)

**Version:** 1.0  
**Date:** 24 October 2026  
**Status:** Proposed / research-gated  
**Platforms:** Android (two separate apps)  
**Language:** English — implementation specification for engineers / AI coding agents  
**Working name:** SIM Bridge

> **Non-negotiable reality check:** This product provides remote access to a SIM hosted in another phone. It does **not** move, duplicate, virtualize, or register the SIM/IMEI in the client phone. On normal unrooted Android, full two-way *cellular* call-audio capture and injection are not guaranteed by the public SDK. A production promise of “works on every Android phone” must **not** be made until platform/OEM-specific evidence exists. The audio path is an explicit R&D gate.

---

## 1. Executive Summary

Build two independently installed Android applications:

1. **SIM Bridge Host** — runs on a PTA-approved Android phone containing a functional cellular SIM. Owns the actual cellular connection, call state, and permitted SMS operations.
2. **SIM Bridge Client** — runs on a second Android phone without requiring an active cellular SIM. Presents a familiar dialer, incoming-call screen, SMS inbox, contacts, and settings. Connects to Host over a local wireless link.

**Target user experience:** User keeps Host charged and within wireless range, carries Client, answers/places ordinary calls made using Host's SIM number, sends/receives SMS through Host, and uses the Client microphone/speaker for calls **only on configurations for which two-way audio is proven**.

### Outcomes

- Minimal-steps pairing, secure reconnection, and clear connection health.
- A genuinely useful, independently functioning SMS + call-control bridge.
- A modular, measurable program to prove or reject cellular-audio bridging on each device class.
- Honest compatibility messaging with no fake SIM-present status.

### Not goals

- IMEI spoofing, IMEI modification, SIM cloning, carrier authentication replication, PTA/DIRBS bypass, or changing cellular registration.
- Pretending the Client has a local SIM for third-party app/carrier APIs.
- Universal support for 2FA auto-fill or bank apps' local-SIM checks.
- Unsupported emergency-call routing or guaranteed carrier features (VoLTE, VoWiFi, USSD, MMS, RCS).

## 2. Problem, Users, Jobs to Be Done

**Problem:** A user owns a cellular-capable approved Android phone and a second phone that cannot use a cellular subscription directly. They want ordinary communication from the second phone without manually switching devices.

**Primary persona:** one owner controls both nearby phones and explicitly approves all pairing and remote access.

**Core jobs:**

- See the Host's cellular status and connection status on Client.
- Receive real-time incoming-call alerts on Client; answer/reject/hang up when supported.
- Place calls through Host SIM from Client's dialer.
- Hold a natural two-way conversation over Client audio, only after the audio integration passes device verification.
- Read, send, and sync SMS with transparent per-message delivery state.
- Resume after a transient Wi-Fi disconnect without duplicated actions or data loss.

## 3. Design Principles

1. **Two APKs; one product:** distinct `host` and `client` application IDs and store listings. Separate responsibilities simplify setup and permissions.
2. **First-run setup under 3 minutes** on validated devices; connection under 15 seconds after pairing (targets, not guaranteed OS timings).
3. **No mandatory cloud account or subscription:** local-first pairing and message transport.
4. **Audio capability negotiated, not assumed:** unavailable audio = explicit “Call audio unsupported on this Host” state.
5. **Least privilege + local consent:** no silent interception, no covert monitoring, no automatic transfer of sensitive messages without setup consent.
6. **Fallbacks are real:** Wi-Fi LAN/hotspot as primary; Wi-Fi Direct if supported; Bluetooth for discovery/control fallback, not presumed high-quality audio.
7. **Data resilience:** idempotent commands, acknowledgments, persistence, safe retry.
8. **OEM diversity:** capability probes and a published device/Android-version matrix.

## 4. High-Level Architecture

```text
   CELLULAR NETWORK
          |
          v
 +----------------------------+
 | SIM BRIDGE HOST (APK A)    |
 | PTA-approved Android + SIM |
 | Telephony role adapter     |
 | SMS role adapter           |
 | AudioBridgeProvider [R&D]  |
 | SecureSessionService       |
 | Wi-Fi/Bluetooth transport  |
 +-------------+--------------+
               |
         Encrypted local link
     Signaling + optional RTP audio
               |
 +-------------v--------------+
 | SIM BRIDGE CLIENT (APK B)  |
 | Android, SIM not required  |
 | Dialer / Call UI           |
 | Messages / History         |
 | MediaPlayer/Microphone     |
 | Pairing & Session UI       |
 +----------------------------+
```

**Transport:** Wi-Fi IP link is the MVP media/data path. Control protocol messages are authenticated and versioned. Voice media uses low-latency packets/Opus only **after** the Host audio provider proves capture and injection. WebRTC is an option, not a way to obtain protected cellular audio.

## 5. Functional Requirements

Priority labels: **P0** mandatory for baseline MVP; **P1** mandatory for audio-ready beta; **P2** later.

### 5.1 Host App

| ID | Priority | Requirement | Acceptance |
|---|---|---|---|
| HOST-01 | P0 | Setup wizard: purpose, device ownership, explicit consent | User understands phone role and what can/cannot transfer |
| HOST-02 | P0 | Display cellular SIM/carrier/reception where OS permits | Clear status; no invented signal strength |
| HOST-03 | P0 | Pair via local discovery + QR/6-digit confirmation | Unapproved Client cannot connect |
| HOST-04 | P0 | Secure session and foreground status | Notification shows when bridge is operating |
| HOST-05 | P0 | Detect incoming/outgoing call states via supported role APIs | State sync tested on real device |
| HOST-06 | P0 | Initiate, answer, reject, end calls where permissions/roles allow | Correct call outcome and error handling |
| HOST-07 | P0 | Send/receive SMS when eligible and configured as default handler | Actual carrier SMS end-to-end |
| HOST-08 | P0 | Persist command IDs, SMS IDs, timestamps, sync checkpoints | No duplicates on reconnect |
| HOST-09 | P1 | Audio provider self-test + supported/unsupported status | No false “audio working” flag |
| HOST-10 | P1 | Capture downlink and inject uplink on validated platforms | Demonstrated on physical cellular call |
| HOST-11 | P1 | Battery optimization and service recovery guidance | Predictable behavior on supported OEMs |
| HOST-12 | P2 | Multiple SIM selection and per-SIM calling | Supported only where Android APIs allow |

### 5.2 Client App

| ID | Priority | Requirement | Acceptance |
|---|---|---|---|
| CLIENT-01 | P0 | Simple welcome: “Connect to your Host phone” | Setup does not request a local SIM |
| CLIENT-02 | P0 | Discover Host or scan pairing QR | Clear success/failure/retry screens |
| CLIENT-03 | P0 | Connection dashboard (reachable, signal from Host, audio status) | Accurate live states |
| CLIENT-04 | P0 | Dial pad; call history; optional contacts | Dial commands reach paired Host |
| CLIENT-05 | P0 | Incoming call full-screen UX/notification per OS rules | Reliable when notification permission granted |
| CLIENT-06 | P0 | SMS inbox, compose, send, sync, status | Correct remote SMS experience |
| CLIENT-07 | P0 | Clear disconnect, unpair, wipe-local-data actions | Both sides revoke access |
| CLIENT-08 | P1 | Mic/speaker/earpiece/Bluetooth headset in active call | Device-permission and routing tests pass |
| CLIENT-09 | P1 | Network jitter handling, mute, speaker toggle | Two-way audio success conditions met |
| CLIENT-10 | P2 | Optional local notification privacy controls | Hide sender and message on lock screen |

### 5.3 Calls and Messaging State

Call state model: `IDLE -> DIALING/RINGING -> CONNECTING -> ACTIVE -> HOLD/ENDING -> ENDED` plus `FAILED`, `HOST_UNREACHABLE`, `AUDIO_UNAVAILABLE`.

SMS states: `QUEUED -> SENT_TO_HOST -> SUBMITTED_TO_MODEM -> SENT/DELIVERED/FAILED`; distinguish network submission from recipient delivery. Preserve thread order and retry only idempotent operations.

**Conflict rule:** Host and Client must never create two independent control authorities. Host's telephony state is authoritative. Client requests are commands and UI projections.

## 6. Audio R&D: Critical Product Gate

### 6.1 Why This Is Hard

Public Android `InCallService` is a **call control/UI** mechanism, not a general cellular audio capture/injection API. Protected voice-audio sources require privileged access. `RECORD_AUDIO`, microphone capture, speakerphone routing, WebRTC, and ordinary VoIP APIs do not independently solve this restriction.

### 6.2 Candidate Providers

| Provider | Root | Hardware | Viability / risk | Action |
|---|---|---|---|---|
| A: Public Android SDK digital cellular audio | No | No | Protected audio unavailable on ordinary apps | Rejected as universal solution |
| B: Device-specific ADB/Shizuku/hidden audio APIs | No root; extra setup | No | May work on selected OS/OEM versions; fragile across updates | Primary R&D experiment, **not universal promise** |
| C: Native OEM/system/privileged integration | Depends on OEM | No | More reliable but requires manufacturer partnership/system signing | Strategic partnership track |
| D: Bluetooth HFP Client role | No root potentially | No | Stock phones generally do not expose universal HFP Client role to apps | Capability probe and OEM tests |
| E: External wired/Bluetooth audio gateway | No root potentially | Yes | Physically testable fallback; adds cost/complexity | Separate hardware research track |
| F: Acoustic speaker/mic loopback | No root | No | Echo, privacy, feedback, mic competition | Lab-only demonstration; not production |

**No-root policy:** Do not require rooting. ADB/Shizuku setup may violate the “anyone can install and use” simplicity target, so its success alone does not make universal consumer readiness.

### 6.3 Audio Provider Interface

```kotlin
interface CellularAudioProvider {
    suspend fun probe(): AudioCapabilities
    suspend fun prepare(callId: String): AudioResult
    fun downlinkPcm(): Flow<PcmFrame>
    suspend fun writeUplink(frame: PcmFrame): AudioResult
    suspend fun stop()
}

data class AudioCapabilities(
    val downlinkCapture: Boolean,
    val uplinkInjection: Boolean,
    val requiresAdbSetup: Boolean,
    val supportedRoute: String?,
    val failureReason: String?
)
```

Separate this from Codec, Network, Telephony, and UI. Never show `SUPPORTED` on API existence alone: it must pass a **physical call loop test**.

### 6.4 Mandatory Real-Call Experiments

1. Select **one** unrooted Host and Client Android phone. Record OEM, model, SoC, Android/API level, patch level, call technology (VoLTE/2G/3G), build fingerprint.
2. Establish a cellular call to an independent third phone with the call participant's knowledge/consent.
3. Verify call events + answer/reject independently of audio.
4. Test downlink: third phone speaks; Client speaker plays intelligible speech, Host microphone does not merely relay room sound.
5. Test uplink: speak **only** into Client microphone; third phone hears. Test with Host mic physically distant/muted where technically appropriate.
6. Test simultaneous speech, mute, earpiece/speaker, headset, screen-off, background, and 10-minute duration.
7. Log packet loss, end-to-end one-way latency, jitter, echo, audio route changes, process lifecycle, exceptions.
8. Reboot and retest setup requirements; repeat on multiple Android versions and OEMs.

**Go criteria for audio-enabled beta:** Both voice directions pass on at least three real Host models from two OEM families, with reproducible build/setup steps and measurable stability; device-specific support labeling retained. **No-go:** audio only works via uncontrolled speaker feedback, depends on unsupported permissions that vanish on reboot without usable reactivation, or fails during calls.

**Audio performance targets, subject to testing:** less than 250 ms additional local bridge latency preferred; less than 1% average audio packet loss on stable Wi-Fi; 10-minute two-way call without crash; clear intelligibility and no sustained acoustic howl. These are product targets, not proven capability claims.

## 7. Connection, Pairing and Setup UX

### 7.1 Host onboarding (target <= 6 screens)

1. Welcome: “This phone holds your SIM”; plain explanation.
2. Capability check: Android version, telephony features, SMS role eligibility, known audio support.
3. Permission and default-handler walkthrough, requested only when needed.
4. Choose local network: same Wi-Fi/hotspot first; nearby discovery alternatives where supported.
5. Display expiring QR pairing code; Client scan and Host confirmation.
6. Dashboard: Connected/Disconnected; Calls/SMS/Audio capabilities shown separately.

### 7.2 Client onboarding (target <= 4 screens)

1. Welcome: “Use your other phone's number”.
2. Scan QR or discover Host.
3. Confirm device names and shared verification code.
4. Home screen with tabs `Dial`, `Messages`, `Calls`, `Settings`.

### 7.3 Discovery strategy

- **MVP:** both devices on same Wi-Fi LAN or Client connected to Host's existing hotspot; mDNS/NSD where supported, QR contains authenticated session bootstrap information, manual IP fallback for troubleshooting.
- **V1.1:** Wi-Fi Direct only with proven OEM support; handle radio restrictions and collisions gracefully.
- **Bluetooth:** BLE advertise/discovery and bonded Bluetooth Classic control when useful; do not assume automatic hotspot creation or Bluetooth HFP audio availability. Android permission behavior varies by API level.
- The app must never silently activate privileged hotspot features that the OS does not expose.

### 7.4 Setup states

`NOT_PAIRED`, `PAIRING`, `PAIRING_CONFIRMATION`, `CONNECTED_CONTROL_ONLY`, `CONNECTED_AUDIO_READY`, `DEGRADED`, `HOST_OFFLINE`, `REVOKED`.

## 8. Security and Privacy Requirements

- Local-only mode by default; **no cloud relay** in MVP. If remote internet mode is introduced, it requires separate threat modeling and user opt-in.
- QR bootstrap includes one-time short-lived token, not a static reusable password.
- Mutual device authentication with per-device keys, key rotation/revocation, replay protection, and authenticated encryption in transit.
- Explicit Host approval before pairing; show paired device identifiers and last connection.
- Consent and visible system notification when Host provides call/SMS access. Clear distinction between messages and audio permissions.
- Never auto-forward OTPs to third-party destinations. SMS access only after explicit role/consent; optional OTP masking in notifications.
- Store local conversation metadata encrypted at rest where feasible; use Android Keystore for secrets; do not expose tokens in logs.
- Reject calls to premium-rate/emergency numbers by default unless explicitly supported and legally reviewed; never advertise emergency-call reliability.
- No hidden microphone, SMS surveillance, or unattended pairing.
- Threat cases: stolen paired Client, shared LAN attacker, replayed call commands, session hijacking, malicious QR, debug logging, lock-screen exposure, call recording laws.

## 9. Permissions / Android Role Strategy

| API / role | Owner | Use | Important limitation |
|---|---|---|---|
| `ROLE_DIALER` + `InCallService` | Host | Default phone UI/call events | User must grant role; provide complete dialer behavior |
| `ROLE_SMS` / SMS permissions | Host | Send/receive and persist SMS | Default handler rules; policy compliance required |
| `READ_CONTACTS` | Client (optional) | Local contact lookup | Optional; do not block first run |
| `POST_NOTIFICATIONS` | Both, OS-dependent | Call/status alerts | Behavior varies by OS |
| `BLUETOOTH_SCAN`/`CONNECT` | Both where used | Pair/discover | Android version scoped |
| `NEARBY_WIFI_DEVICES`, network permissions | Both where used | Wi-Fi discovery | Android version scoped |
| `RECORD_AUDIO` | Client | Client microphone in verified audio session | Does not give Host protected cellular audio |
| Foreground service declarations | Both | Ongoing active tasks | Correct service type and modern Android restrictions |
| `CAPTURE_AUDIO_OUTPUT` | Not requestable as ordinary app | Protected call audio | Privileged; not a standard app permission |

**Distribution warning:** Google Play SMS/Call Log restrictions require approved core use and often being the designated default app. User approval of permissions does not automatically imply policy approval. Release route (private sideload/enterprise vs Play Store) needs a deliberate legal/policy review.

## 10. Recommended Technical Stack

- Kotlin, Jetpack Compose, AndroidX lifecycle, Coroutines/Flow, Hilt DI.
- Two Gradle app modules (`:apps:host`, `:apps:client`); shared modules (`:core:protocol`, `:core:crypto`, `:core:transport`, `:core:media`, `:core:model`, `:core:storage`, `:core:testing`).
- Room for call/SMS UI state, SQL migration tests.
- Versioned protobuf or Kotlin serialization with well-defined envelopes. Strict message schemas.
- TCP/TLS or authenticated WebSocket for signaling; QUIC/UDP-RTP/WebRTC/Opus optional for low-latency audio; benchmark instead of assume.
- Structured logging with sensitive data redaction, trace IDs, physical-device instrumentation tests.
- Offline-first: no login, no backend for initial release.

### Example message schema

```json
{
  "protocolVersion": 1,
  "messageId": "uuid-v4",
  "sessionId": "ephemeral-id",
  "sentAtEpochMs": 0,
  "type": "CALL_ANSWER_REQUEST",
  "body": { "callId": "host-call-id" }
}
```

**Protocol types:** `HELLO`, `CAPABILITIES`, `PAIR_REQUEST`, `PAIR_CONFIRM`, `AUTH_RESULT`, `HEARTBEAT`, `HOST_STATUS`, `CALL_INCOMING`, `CALL_STATE`, `CALL_DIAL_REQUEST`, `CALL_ANSWER_REQUEST`, `CALL_REJECT_REQUEST`, `CALL_HANGUP_REQUEST`, `SMS_SEND_REQUEST`, `SMS_RECEIVED`, `SMS_STATUS`, `SYNC_REQUEST`, `SYNC_DELTA`, `ERROR`.

**Rules:** Host owns call IDs; client sends requests, Host replies with ack/state. Sequence numbers and idempotency keys mandatory. Reject unknown protocol versions gracefully. Call control retries must not redial twice.

## 11. UX and Visual Specification

**Host:** compact operational dashboard; SIM/carrier, connected Client, service status, permissions health, audio support label, QR pairing, unpair button, battery optimization help, debug export (redacted).

**Client:** native Android-style dial pad, favorites, call history, SMS threads, compose, incoming-call heads-up/full-screen per OS policy, ongoing call screen with mute/speaker/hangup, offline/error banners.

**Accessibility:** dynamic font sizing, TalkBack labels, large answer/reject targets, high contrast, understandable permission rationales. Initial release English; Urdu localization is P2.

**Never fake:** carrier bars on Client must be explicitly labeled **“Host signal”**; audio readiness separate from wireless connection status.

## 12. Nonfunctional Requirements

| Category | Requirement / target |
|---|---|
| Reliability | 99% command ACK on stable local network in 10-minute test runs; measure across devices |
| Startup | Paired devices reconnect in <15 seconds under favorable Wi-Fi conditions (target) |
| Latency | Call state updates <1 second typical; additional audio latency target <250 ms on verified route |
| Battery | Benchmark Host service power draw across 8-hour idle and 1-hour calling tests |
| Security | All control/SMS messages authenticated/encrypted; zero hardcoded keys |
| Offline | Control and SMS operate without public internet when local link exists |
| Compatibility | Target Android 11+ initially; earlier Android only after device validation |
| Maintainability | OEM-specific audio providers behind interface; unit and integration tests |
| Observability | Non-sensitive logs, per-feature health flags, exportable test report |
| Quality | No silent data loss; resilient permission revocations, process death, device reboot |

## 13. Compatibility Policy

**Marketing wording:** “Supports Android devices meeting listed requirements; voice audio availability varies by Host model and Android version.” Do **not** say “works on all Android phones”.

Three tiers:

- **Tier A — Full:** verified calls + bidirectional audio + SMS on published exact Host/Client builds.
- **Tier B — Control:** pairing, call controls, and permitted SMS work; cellular audio unsupported.
- **Tier C — Unsupported:** missing required OS roles/radios/permissions or blocked transport.

Compatibility record includes exact brand, model, Android version, firmware build, patch, VoLTE status, transport, privileges/setup, incoming/outgoing test, audio RX/TX, recording policy, crash count, limitations.

## 14. Work Plan and Stage Gates

| Stage | Deliverable | Exit gate |
|---|---|---|
| S0 — Technical feasibility | Device matrix, Android roles, audio research harness | Decision on target phone families |
| S1 — Foundation | Two launchable APKs, design system, protocol/crypto, tests | Encrypted pairing and reconnection |
| S2 — Messaging | Host SMS provider/client inbox/send | Real SMS tests; no duplicate messages |
| S3 — Remote dialer | Host call-state + Client UI/control | Calls trigger/answer/reject reliably |
| S4 — Audio POC | Independent adapter + media path | Real two-way audio on validated hardware |
| S5 — Supported-device beta | UX, background stability, OEM fixes | Reproducible beta test matrix |
| S6 — Public-ready release | Security, privacy, docs, compliance | Distribution/legal/Play review complete |

**Critical dependency:** S4 can fail while S1–S3 remain valuable. Do not commit to a universal audio launch date before S4 passes. Avoid coding a large media UI before proving a capture/inject provider.

## 15. Testing & Acceptance Criteria

### Automated

- Protocol serialization, version negotiation, malformed frame fuzzing, idempotency/replay checks.
- State machine transitions and call-control concurrency/race tests.
- SMS ordering, retry, deletion/sync, permission revocation and reboot recovery tests.
- Pairing cryptography + cancellation + malicious QR handling.
- Compose UI tests and accessibility scans.

### Real-device E2E

- Different carriers and SIM configurations; Wi-Fi router vs hotspot; airplane mode; no public internet.
- Incoming, outgoing, missed, rejected and busy calls; device locked/unlocked.
- SMS with long Unicode text, multipart SMS, poor signal, duplicate delivery receipts.
- Audio: RX only, TX only, simultaneous speech, mute, speakerphone, Bluetooth headphones, 10-minute duration, 30-minute endurance, screen locked.
- OEM battery saver, Doze, foreground restrictions, app update, Host/Client reboot, permission denial/revocation.
- Host unavailable: show clear UI, queue only safe actions, never silently retry a dial command after unknown outcome.

### MVP definition of done

- **Control-only MVP:** two installed apps, secure pairing, Host call state + permitted controls, SMS send/receive, offline/reconnect UX, honest audio-unavailable indication.
- **Audio-enabled Beta:** all above plus proven two-way real cellular call audio on supported exact device combinations; documented setup.
- **General Availability:** security audit, supported-device matrix, privacy and legal review, understandable onboarding, tested update migration.

## 16. Risks, Decisions, Open Questions

| Risk | Impact | Mitigation |
|---|---|---|
| Privileged audio restrictions | Critical | Early independent audio gate; OEM collaboration; alternative transport research |
| Shizuku/ADB fragility | High | Per-build adapters; support diagnostics; cannot promise zero-setup |
| SMS/default dialer conflict | High | Fully implement required role functionality; clear setup and rollback |
| Google Play policy rejection | High | Review policy before choosing distribution; separate permission justification |
| OEM process killing | High | Foreground service; user-guided battery settings; device matrix |
| Latency/echo | Medium–high | Hardware measurement, jitter controls, test RTP/Opus vs WebRTC |
| Security/privacy exposure | Critical | Opt-in pairing, mutual auth, encryption, revocation, data minimization |
| User expecting true SIM | High | Explicit UX, documentation, and truthful branding |

**Open product decisions:** supported initial carrier(s); initial phone-model test set; Play Store vs privately distributed APKs; allow or avoid one-time Shizuku setup; multi-SIM in first release; audio recording policy; messages retained on Client and for how long.

## 17. Suggested Coding Agent Execution Order

1. Generate two compilable Android Studio modules + shared modules; keep independent package IDs.
2. Implement protocol envelope, crypto pairing, secure local transport, simulator/mock Host.
3. Build Host and Client onboarding and connection dashboards with mocked capability flags.
4. Integrate Android roles in **Host only**, with graceful permission refusal and rollback.
5. Build SMS gateway, state sync and idempotent persistence.
6. Add call-state adapters and remote controls with physical-device integration tests.
7. Build standalone audio test harness; **prove capture and injection before integrating media into calls**.
8. Integrate verified audio provider, voice transport, UI and network failover.
9. Run cross-OEM matrix and privacy/security review.

**Engineering instruction:** Never mark a stub `AudioProvider` as implemented; feature-flag it as unsupported until verified on physical cellular calls. Do not fake successful calls in demo screens or tests. Implement progress as small reviewable PRs with device-backed evidence.

## 18. Sources / Standards to Review

1. Android `InCallService`: https://developer.android.com/reference/android/telecom/InCallService
2. Android Wi-Fi P2P Discovery: https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct
3. Android wireless connectivity & Nearby Connections: https://developer.android.com/develop/connectivity/wifi
4. Google Play SMS and Call Log permission policy: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
5. Android default-handler requirements: https://developer.android.com/guide/topics/permissions/default-handlers
6. Shizuku project and setup: https://shizuku.rikka.app/guide/setup/
7. Experimental cellular/SIP reference (independently validate): https://github.com/sparkcyf/android-voice-sip-agent
8. AOSP Bluetooth HFP client internals: https://android.googlesource.com/platform/packages/modules/Bluetooth/

**Source interpretation:** Official documentation establishes supported/public APIs and policies; open-source experimental projects are R&D leads, not proof of portability or approval.

---

*End of PRD v1.0 — Two-app design with audio capability explicitly gated by verification.*
