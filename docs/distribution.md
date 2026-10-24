# Distribution decision

**Decision (25 Oct 2026): private sideload of signed APKs for the Control-only MVP. Google Play is deferred.**

## Why not Google Play now

- Google Play's SMS and Call Log permission policy allows these permissions only for an approved core use, usually
  as the user's default SMS / Phone handler, after a Permissions Declaration review. The Host *is* the default
  handler, but the purpose (bridging to another phone) needs a deliberate policy case and may be rejected.
- `USE_FULL_SCREEN_INTENT` on Android 14+ is limited on Play to calling and alarm apps; the Client qualifies only
  if reviewers accept it as a calling app.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted on Play; it is used here because a background bridge is the
  core function.
- User approval of permissions does not imply policy approval (PRD 9).

## Sideload process

1. `keystore.properties` + `release-keys/simbridge-release.jks` stay off git; back them up privately.
   Losing the key means users must uninstall before installing a newer version.
2. `./gradlew assembleRelease` → `apps/*/build/outputs/apk/release/` (copies in `release-apks/`).
3. Verify: `apksigner verify --print-certs <apk>` must show `CN=SIM Bridge host` / `CN=SIM Bridge client`.
4. Share APKs only with testers who own both phones; include `docs/testing-on-devices.md`.

## Legal checklist before any public release

- [ ] Play policy review (SMS / Call Log declaration, full-screen intent, battery exemption) or an alternative store.
- [ ] Pakistan: confirm no PTA / DIRBS implications — the app never touches IMEI, SIM registration or network identity.
- [ ] Call-recording law: the product does not record; keep it that way unless reviewed.
- [ ] Privacy policy (`docs/privacy-policy.md`) reviewed and published.
- [ ] Independent security audit (`docs/security-review.md` open items).
