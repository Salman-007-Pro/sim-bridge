# Security and privacy review — v0.1.0 (24–25 Oct 2026)

Scope: both apps and all `core` modules at the Control-only MVP. Method: walk every PRD section 8 requirement and
threat case to the code that enforces it, then look for gaps. This is an internal review, **not** the independent
security audit PRD section 15 requires before General Availability.

## Controls

| Requirement (PRD 8) | Implementation |
|---|---|
| Local-only, no cloud relay | No backend; sockets only to private LAN / hotspot addresses (`BridgeServer.localNetworkOnly`) |
| One-time short-lived QR token | `PairingWindow`: 128-bit random, 2-minute expiry, consumed on first use (valid or not) |
| Mutual authentication, per-device keys | TLS 1.3 only, client cert required; EC P-256 keys generated in Android Keystore (`AndroidKeystoreIdentityStore`) |
| Pinning, no CA | `PinnedTrustManager`; Client pins the Host fingerprint from the QR; Host authorises by stored fingerprint |
| MITM resistance during pairing | 6-digit SAS = HMAC(token, hostFp, clientFp) shown on both phones; Host user must approve |
| Explicit Host approval | `HostHandshake` waits for `PairingApprover`; nothing is stored before approval |
| Key rotation / revocation | Client rotates key on unpair; Host "Remove all phones and reset keys" rotates Host key; `UNPAIR` message revokes both sides |
| Replay protection | TLS record protection + per-session strictly increasing `seq` (`SequenceGuard`) + session-id check |
| Idempotent commands, no double dial | `CommandLedger` claim-before-execute; SMS unique `clientMessageId`; dial never auto-retried |
| Malformed input | Strict JSON schema per message type, 512 KiB frame cap, 5 consecutive errors close the session, fuzz-tested |
| Visible operation | Foreground-service notifications on both phones while the bridge runs |
| OTP / lock screen | Client message notifications private by default with OTP masking; Host QR screen uses `FLAG_SECURE` |
| Premium / emergency | Remote dials to emergency and premium numbers refused (`DialPolicy` + platform emergency DB); Host's own dialer is unrestricted |
| Secrets in Keystore, no tokens in logs | Only private keys are secret and they never leave the Keystore; `BridgeLog` + `Redaction` at call sites; `ReportScrubber` on export |
| No hidden microphone | Release builds have no `RECORD_AUDIO`; audio lab exists only in debug builds |
| Backups | `allowBackup=false`, all domains excluded from cloud backup and device transfer |

## Threat cases

| Threat | Mitigation | Residual risk |
|---|---|---|
| Stolen paired Client | Host unpair or key reset revokes instantly; Client data protected by the phone lock + file-based encryption | Data already on the stolen phone stays readable to someone who unlocks it |
| Shared-LAN attacker | Must hold a valid client key pinned on the Host; unauthenticated peers are cut off after the handshake | Can burn an open pairing code (user creates a new one) |
| Connection flooding | Max 4 concurrent TLS handshakes, max 3 concurrent app handshakes, 10 s timeouts | A determined LAN flood can delay a legitimate reconnect |
| Replayed call commands | `seq` guard + ledger; a replayed dial key returns the recorded outcome | — |
| Session hijacking | TLS 1.3 with mutual auth; session id bound per connection | — |
| Malicious QR | Strict parser: IPv4 literals only (no DNS), port range, hex fingerprint, token format, length cap, unknown keys rejected; nothing happens until the user taps Connect | — |
| Debug logging | Redacted at source, scrubbed on export, shared only by explicit user action | Logcat on a debug build shows the same redacted lines |
| Lock-screen exposure | Private message notifications + OTP masking; incoming call shows caller name like any phone | — |
| Call recording laws | The product never records calls; the debug lab captures a few seconds for signal checks and keeps nothing | Lab use must have the remote party's consent |

## Findings fixed in this review

1. **Listener reachable from non-LAN interfaces.** The Host bound `0.0.0.0`, so a public or cellular address could
   reach the TLS port. Fixed: `acceptFilter = BridgeServer.localNetworkOnly` drops such connections before TLS.
2. **Unbounded unauthenticated connections.** Fixed with handshake limits in `BridgeServer` and `HostBridge`.
3. **No Host-side key rotation.** Added `HostBridge.resetIdentity()` and the dashboard action.

All three are covered by tests in `core/transport` (`SessionTest`).

## Open items before General Availability

- Independent penetration test and code audit (PRD 15).
- Encrypting Room databases at rest (today: Android file-based encryption only).
- Pairing-code DoS by a LAN attacker (rate-limit pairing attempts per source address).
- Re-run this review after any audio provider work (Phase 7) — media keys and UDP transport are not covered here.
