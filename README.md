# SIM Bridge

Use the SIM in one Android phone from a second Android phone over your local Wi-Fi. Get call alerts, answer or
reject calls, dial out, and read and send SMS on the second phone. No account, no server, no cloud.

SIM Bridge is two separate apps that pair with each other:

| App | Install on | Role |
|---|---|---|
| **SIM Bridge Host** (`com.simbridge.host`) | The phone that has the SIM | Becomes the default Phone and SMS app, owns the real calls and messages, and serves them to the paired Client |
| **SIM Bridge Client** (`com.simbridge.client`) | The phone you carry (no SIM needed) | Dial pad, incoming-call screen, call history and SMS inbox, all driven through the Host |

> **Important:** SIM Bridge gives remote access to a SIM that stays inside the Host. It does not move, clone or
> virtualise the SIM or IMEI. In the current version **call audio stays on the Host phone** (or a headset paired to
> it): you control calls from the Client, but the conversation happens on the Host. Two-way audio on the Client is a
> gated research track — see [Audio R&D](docs/audio-rnd/README.md).

## Contents

- [Features](#features)
- [How it works](#how-it-works)
- [Project status](#project-status)
- [Requirements](#requirements)
- [Setup](#setup)
- [First run: pair the two phones](#first-run-pair-the-two-phones)
- [Release builds and signing](#release-builds-and-signing)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Troubleshooting](#troubleshooting)
- [Security and privacy](#security-and-privacy)
- [Known limitations](#known-limitations)
- [Documentation](#documentation)

## Features

**Pairing and connection**

- Pair by scanning a QR code (or pasting a pairing link), then confirm the same 6-digit code on both phones. Nothing
  is trusted until the Host user taps Approve.
- Mutual TLS 1.3 with a per-device key kept in the Android Keystore. No certificate authority, no cloud relay.
- Works on a shared Wi-Fi network or on the Host's own hotspot. The Client finds the Host automatically (NSD), with a
  manual address as fallback.
- Automatic reconnect after Wi-Fi drops, and both apps restart their bridge after a reboot.

**SMS**

- Threads, conversation view and compose on the Client, including long (multipart) and Unicode messages.
- Per-message status: Sent to Host → Sending → Sent → Delivered.
- Messages written while offline wait for the Host and are sent exactly once.
- Missed messages sync after a reconnect. One-time codes are masked in notifications.

**Calls**

- Full-screen incoming call on the Client (also on the lock screen) with Answer, Decline and Hang up.
- Dial pad that places calls through the Host's SIM, plus call history with missed, rejected and busy labels.
- Remote dials to emergency and premium numbers are refused. Emergency calls must be made on the Host itself.

**Reliability and privacy**

- Every command is acknowledged and recorded in a ledger, so reconnects never cause duplicate SMS, and a dial is never
  retried automatically.
- Logs are redacted at the source. The shareable debug report strips numbers, message text and keys.
- Cloud backup and device-transfer backup are disabled in both apps.

## How it works

```text
          CELLULAR NETWORK
                 |
+----------------v-----------------+                              +----------------------------------+
| SIM Bridge Host                  |    Wi-Fi LAN or Host hotspot  | SIM Bridge Client                |
|  default Phone + SMS app         | <--------------------------> |  dial pad, incoming-call screen  |
|  InCallService, SMS store        |   mutual TLS 1.3, TCP 47820   |  SMS inbox, call history         |
|  foreground bridge service       |   JSON messages, ACK, sync    |  foreground bridge service       |
+----------------------------------+                              +----------------------------------+
```

The Host's telephony state is the single source of truth. The Client sends commands (send SMS, dial, answer) and shows
what the Host reports back. Messages are versioned, schema-checked JSON frames with per-session sequence numbers.

## Project status

**Version 0.1.0 — control-only MVP (compatibility "Tier B").** The code for pairing, SMS, call control and
hardening is complete and all JVM unit tests pass.

Not yet proven on real hardware: pairing between two physical phones, real carrier SMS and calls, OEM background
behaviour, and the 10-minute Wi-Fi reliability run. Phase-by-phase progress lives in [`tasks/todo.md`](tasks/todo.md),
and the device checklist in [`docs/testing-on-devices.md`](docs/testing-on-devices.md).

## Requirements

### Development machine

| Tool | Version |
|---|---|
| Android Studio | A recent release that supports AGP 9.4 (it bundles the JBR 21 runtime) |
| JDK | 17 or newer; Android Studio's bundled JBR 21 is recommended |
| Android SDK | SDK Platform 36 and Platform-Tools (`adb`) |
| Gradle | Not needed — the wrapper downloads Gradle 9.8.2 |
| Git | Any recent version |

### Phones

| | Host phone | Client phone |
|---|---|---|
| Android | 11 (API 30) or newer | 11 (API 30) or newer |
| SIM | Working cellular SIM | Not required |
| Roles | Must allow SIM Bridge as the default Phone and SMS app | — |
| Network | Wi-Fi, or its own hotspot | Same Wi-Fi, or connected to the Host's hotspot |

To install debug builds you also need USB debugging on both phones. To test calls and SMS end to end, keep a third
phone (or landline) handy.

## Setup

### 1. Clone the repository

```bash
git clone <repository-url> SimBridge
cd SimBridge
```

### 2. Choose a JDK

**Android Studio:** open the project root with *File → Open*. Make sure *Settings → Build, Execution, Deployment →
Build Tools → Gradle → Gradle JDK* points to the bundled JBR (21), then let Gradle sync. Studio also creates
`local.properties` for you, so you can skip step 3.

**Command line:** point `JAVA_HOME` at a JDK 17+ (adjust the path to where Android Studio is installed):

```bash
# Windows (Git Bash)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Linux
export JAVA_HOME="$HOME/android-studio/jbr"
```

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Check it with `"$JAVA_HOME/bin/java" -version` — it should report version 17 or newer.

### 3. Point Gradle at the Android SDK

If you are not using Android Studio, create `local.properties` in the project root (it is git-ignored):

```properties
# Windows — escape the colon and the backslashes
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk

# macOS
# sdk.dir=/Users/<you>/Library/Android/sdk

# Linux
# sdk.dir=/home/<you>/Android/Sdk
```

Setting the `ANDROID_HOME` environment variable works too.

### 4. Build both apps

```bash
./gradlew assembleDebug
```

On Windows Command Prompt or PowerShell, use `.\gradlew.bat assembleDebug` instead. The first build downloads Gradle
and all dependencies, so it takes a few minutes. The APKs land here:

```text
apps/host/build/outputs/apk/debug/host-debug.apk
apps/client/build/outputs/apk/debug/client-debug.apk
```

### 5. Run the unit tests

```bash
./gradlew testDebugUnitTest test
```

These are JVM tests for every module (protocol, crypto, handshake, reliability, ledger, call states, OTP masking) and
need no phone.

### 6. Install on the phones

Turn on *Developer options → USB debugging* on both phones, connect them, and install each APK on the right phone:

```bash
adb devices -l     # note both serial numbers

adb -s <HOST_SERIAL>   install -r apps/host/build/outputs/apk/debug/host-debug.apk
adb -s <CLIENT_SERIAL> install -r apps/client/build/outputs/apk/debug/client-debug.apk
```

`adb` lives in `<sdk>/platform-tools/`. With only one phone connected, `./gradlew :apps:host:installDebug` or
`./gradlew :apps:client:installDebug` also works.

## First run: pair the two phones

1. **Network.** Put both phones on the same Wi-Fi, or turn on the Host's hotspot and connect the Client to it. No
   internet is needed. Guest and public Wi-Fi networks often block phone-to-phone traffic, so prefer home Wi-Fi or
   the hotspot.
2. **Host setup.** Open SIM Bridge Host and follow the five onboarding steps:
   1. Read what is and is not shared, and confirm.
   2. Review the capability check for this phone.
   3. Grant permissions — set SIM Bridge Host as the **default Phone app** and the **default SMS app**, and allow
      notifications.
   4. Pick the local network (the screen lists the Host's address) and tap **Start bridge**.
   5. A pairing QR code appears.
3. **Client setup.** Open SIM Bridge Client, tap **Connect to your Host phone**, and scan the QR code. No camera? On
   the Host tap *Share the pairing link instead*, and on the Client tap *Paste a pairing link instead*.
4. **Confirm.** Both phones now show a 6-digit code. If the codes match, tap **Approve** on the Host. If they do not,
   tap Reject.
5. **Keep it running.** On both phones, set battery usage to *Unrestricted* and allow auto-start if your phone maker
   has that setting. Both apps include a *Battery and background* guide. Without this, Android may stop the bridge in
   the background.

After pairing, the Client reconnects by itself and the Host shows a persistent "SIM Bridge is active" notification
while the bridge runs.

**Manual address:** if the Client cannot find the Host automatically, open *Client → Settings → Manual Host address*
and enter the Host's address, for example `192.168.1.20:47820`.

**Unpairing:** use *Unpair from Host* in the Client's settings, or *Unpair* on the Host dashboard. *Remove all phones
and reset keys* on the Host revokes every paired phone at once.

**Going back:** while SIM Bridge Host is the default Phone and SMS app, the Host's previous apps stop receiving new
calls and SMS. To undo this, pick your old apps again under *Settings → Apps → Default apps*.

## Release builds and signing

Release builds are minified and resource-shrunk. Signing is read from a `keystore.properties` file in the project
root; both it and `*.jks` files are git-ignored. Without that file, `assembleRelease` still works but produces
unsigned APKs.

**1. Create a keystore** with one key per app (only once; `keytool` is in `$JAVA_HOME/bin`):

```bash
mkdir -p release-keys

keytool -genkeypair -keystore release-keys/simbridge-release.jks -alias simbridge-host \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=SIM Bridge host"

keytool -genkeypair -keystore release-keys/simbridge-release.jks -alias simbridge-client \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=SIM Bridge client"
```

**2. Create `keystore.properties`** in the project root:

```properties
storeFile=release-keys/simbridge-release.jks
storePassword=<keystore password>
keyPassword=<key password>
hostKeyAlias=simbridge-host
clientKeyAlias=simbridge-client
```

Both aliases share `keyPassword`. With the default PKCS12 keystore format, that is the same as the keystore password.

**3. Build:**

```bash
./gradlew assembleRelease
```

```text
apps/host/build/outputs/apk/release/host-release.apk
apps/client/build/outputs/apk/release/client-release.apk
```

**4. Verify the signature** (`apksigner` is in `<sdk>/build-tools/<version>/`):

```bash
apksigner verify --print-certs apps/host/build/outputs/apk/release/host-release.apk
```

The output should show `CN=SIM Bridge host` (or `CN=SIM Bridge client` for the Client APK).

Back up the keystore and its passwords somewhere private. If the key is lost, users have to uninstall before they can
install a newer version. The current plan is private sideloading; Google Play is deferred for policy reasons — see
[`docs/distribution.md`](docs/distribution.md).

## Testing

| What | Command | Phone needed |
|---|---|---|
| All JVM unit tests | `./gradlew testDebugUnitTest test` | No |
| 10-minute transport soak test | `./gradlew :core:transport:testDebugUnitTest -PsoakSeconds=600` | No |
| Instrumented tests (Room, Compose accessibility) | see below | Yes |
| Manual end-to-end checklist | [`docs/testing-on-devices.md`](docs/testing-on-devices.md) | Two phones + a third for calls/SMS |

Instrumented tests, with a phone connected over `adb`:

```bash
./gradlew :apps:host:connectedDebugAndroidTest :apps:client:connectedDebugAndroidTest \
          :core:storage:connectedDebugAndroidTest :core:ui:connectedDebugAndroidTest
```

Record device results in [`docs/device-matrix.md`](docs/device-matrix.md). The Host's *Device capabilities* screen has
a Share button that produces a ready-made row for it.

## Project structure

```text
SimBridge/
├── apps/
│   ├── host/            Host app: bridge service, pairing, SMS role, InCallService, dashboard
│   │   └── src/debug/   Debug-only Audio Lab (Shizuku and Bluetooth probes); not in release builds
│   └── client/          Client app: onboarding and QR scanner, dial pad, call screens, messages, settings
├── core/
│   ├── model/           Call and SMS state machines, phone numbers, redaction (pure Kotlin/JVM)
│   ├── protocol/        Message envelope, schemas, codec, version negotiation, sequence guard (JVM)
│   ├── crypto/          Keystore identity, fingerprints, QR payload, 6-digit code, pinned TLS
│   ├── transport/       TLS server and client, framing, sessions, heartbeat, handshake, NSD discovery
│   ├── storage/         Command ledger (Room), paired-device store, redacted logging
│   ├── ui/              Shared Compose theme and components, battery guide, debug report
│   ├── media/           Audio provider interface and support policy for the audio R&D gate (JVM)
│   └── testing/         FakeHost and test identities used by unit tests
├── docs/                Compatibility, security, privacy, distribution, device testing, audio R&D
├── tasks/               Phase tracker (todo.md) and lessons
├── gradle/
│   └── libs.versions.toml   Version catalog for all dependencies
└── SIM_Bridge_PRD_v1.md     Product requirements document
```

## Tech stack

| Area | Choice |
|---|---|
| Language and build | Kotlin 2.3.21, Gradle 9.8.2, AGP 9.4.1 (built-in Kotlin), KSP 2.3.12 |
| Android targets | `compileSdk` / `targetSdk` 36, `minSdk` 30, Java 17 bytecode |
| UI | Jetpack Compose (BOM 2025.10.01), Material 3, Navigation Compose |
| Architecture | Hilt 2.60.1, ViewModel, Kotlin Coroutines, foreground services |
| Data | Room 2.8.5, DataStore, kotlinx.serialization |
| Pairing | ZXing (Host QR), CameraX + ML Kit barcode scanning (Client) |
| Security | TLS 1.3 with mutual auth, Android Keystore EC P-256 keys |

All versions are managed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Troubleshooting

**`SDK location not found`** — create `local.properties` with `sdk.dir` (see [Setup step 3](#3-point-gradle-at-the-android-sdk))
or set `ANDROID_HOME`.

**Gradle complains about the Java version** — `JAVA_HOME` (or Android Studio's Gradle JDK) points at an old JDK. Use
JDK 17 or newer, ideally Android Studio's JBR.

**`Could not delete '.../build/kotlin/compileKotlin/cacheable/caches-jvm'`** or a Kotlin daemon "corruption" message —
the Kotlin incremental cache for that module is stale or locked by an old daemon. Run `./gradlew --stop`, delete that
module's `build/` folder (for example `core/media/build`), and build again.

**The Client cannot find the Host**

- Check both phones are on the same network and that it is not a guest network with client isolation. The Host's
  hotspot always works.
- Check the Host shows the "SIM Bridge is active" notification.
- Enter the address by hand under *Client → Settings → Manual Host address*. The Host listens on TCP port `47820`
  (it falls back to another free port only if that one is busy).

**The pairing code stopped working** — pairing codes expire after 2 minutes and work only once. Start a new pairing
from the Host.

**The bridge stops when the screen is off** — set battery usage to *Unrestricted* and allow auto-start on both phones.

**No option to make SIM Bridge the default Phone or SMS app** — the device does not offer those roles (common on
tablets without telephony). It cannot be used as a Host.

**`assembleRelease` produced `*-release-unsigned.apk`** — `keystore.properties` is missing, or its `storeFile` path is
wrong. Paths are relative to the project root.

## Security and privacy

- Everything stays on your local network. There is no account, backend or analytics.
- Pairing uses a one-time QR token (2-minute expiry), a 6-digit confirmation code on both phones, and explicit Host
  approval. After that, every connection is mutual TLS 1.3 against pinned key fingerprints.
- The Host only accepts connections that arrive on private LAN or hotspot addresses, and limits concurrent handshakes.
- Release builds contain no microphone permission. The Audio Lab exists only in debug builds.

Read more in the [security review](docs/security-review.md) and the [privacy policy draft](docs/privacy-policy.md).
An independent security audit and a legal review are still required before any public release.

## Known limitations

- Call audio is not bridged — you talk on the Host phone.
- MMS, RCS, USSD, VoLTE/VoWiFi settings and emergency calls are not bridged.
- Banking or 2FA apps that check for a local SIM will not see one on the Client.
- Calls ring on both phones.
- Background reliability depends on the phone maker's battery management.

Details: [`docs/compatibility.md`](docs/compatibility.md).

## Documentation

| Document | What it covers |
|---|---|
| [`SIM_Bridge_PRD_v1.md`](SIM_Bridge_PRD_v1.md) | Product requirements and the full specification |
| [`docs/testing-on-devices.md`](docs/testing-on-devices.md) | Step-by-step checklist for testing on two phones |
| [`docs/device-matrix.md`](docs/device-matrix.md) | Record of tested phones and results |
| [`docs/compatibility.md`](docs/compatibility.md) | Supported devices, tiers and limitations |
| [`docs/security-review.md`](docs/security-review.md) | Security controls, threat cases and open items |
| [`docs/privacy-policy.md`](docs/privacy-policy.md) | Privacy policy draft |
| [`docs/distribution.md`](docs/distribution.md) | Why sideloading for now, and the release checklist |
| [`docs/audio-rnd/README.md`](docs/audio-rnd/README.md) | Audio research gate, lab setup and go/no-go criteria |
| [`tasks/todo.md`](tasks/todo.md) | Phase-by-phase progress tracker |
