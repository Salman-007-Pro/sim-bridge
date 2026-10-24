# Phones par testing guide (Phase 0–5 exit gates)

Yeh steps 2 physical phones par chalane hain: **Host** (SIM wala) aur **Client** (dusra phone). Ek teesra phone
(ya landline) call/SMS test ke liye chahiye.

## 0. Install

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr"
./gradlew assembleDebug            # ya signed: ./gradlew assembleRelease (keystore.properties zaroori)

adb devices -l                     # dono phones nazar aane chahiye (USB debugging on)
adb -s <HOST_SERIAL>   install -r apps/host/build/outputs/apk/debug/host-debug.apk
adb -s <CLIENT_SERIAL> install -r apps/client/build/outputs/apk/debug/client-debug.apk
```

Signed APKs: `release-apks/simbridge-host-0.1.0.apk`, `release-apks/simbridge-client-0.1.0.apk`.

Device tests (Room + Compose UI) phone connect karke:

```bash
./gradlew :apps:host:connectedDebugAndroidTest :apps:client:connectedDebugAndroidTest \
          :core:storage:connectedDebugAndroidTest :core:ui:connectedDebugAndroidTest
```

## Phase 0 — Device feasibility

- [ ] Host app → Dashboard → **Device capabilities** → Share → row `docs/device-matrix.md` mein paste.
- [ ] Client phone ka model/Android version bhi matrix mein likhein.

## Phase 2 — Pairing

- [ ] Dono phones same Wi-Fi par, ya Client ko Host ke hotspot se connect karein.
- [ ] Host onboarding: consent → capabilities → permissions → network → **Start bridge** → QR.
- [ ] Client: Connect → QR scan → dono phones par **same 6-digit code** → Host par Approve.
- [ ] Ghalat phone ko reject karein: Host par Reject → Client "Pairing was rejected" dikhaye.
- [ ] Bina pairing ke koi teesra Client connect na kar sake (pairing screen band ho to `NOT_PAIRED`).
- [ ] Wi-Fi off → on: Client 15 second ke andar "Connected" (stopwatch se time note karein).
- [ ] Host dashboard → Unpair: Client "Access removed" dikhaye aur reconnect na ho.
- [ ] Client Settings → Unpair: Host ki paired list se phone hat jaye.

## Phase 3 — SMS

- [ ] Host: Permissions → **Default SMS app** allow.
- [ ] Teesre phone se Host number par SMS → Client par notification + thread.
- [ ] Client se SMS bhejein → status: Sent to Host → Sending → Sent → Delivered (agar network report de).
- [ ] Lamba Urdu/emoji SMS (multipart) dono taraf.
- [ ] Client offline karke SMS likhein → "Waiting for Host" → Wi-Fi on → ek hi SMS jaye (duplicate nahi).
- [ ] Host reboot → Client reconnect → missed SMS sync ho jayein.

## Phase 4 — Calls

- [ ] Host: Permissions → **Default phone app** allow.
- [ ] Incoming call (teesre phone se): Client ring kare, full-screen screen (lock par bhi).
- [ ] Client se Answer / Decline / Hang up → Host par asar ho. Audio Host phone par hi rahega.
- [ ] Client dial pad se call → Host call lagaye; Client par "Calling…" → Active timer.
- [ ] Missed, rejected, busy calls → Client history sahi label dikhaye.
- [ ] `1122` / `15` dial karein → Client block kare (emergency calls bridge se nahi).
- [ ] Call ke dauran Wi-Fi off → Client "Host unreachable" dikhaye; Host par call chalti rahe.

## Phase 5 — Hardening

- [ ] Dono phones par Battery and background → Unrestricted + OEM steps.
- [ ] Host reboot → bridge khud start ho (notification "SIM Bridge is active").
- [ ] Client reboot → khud reconnect.
- [ ] 10 minute: Client se lagataar SMS/commands; Settings → "Commands acknowledged" >= 99%.
- [ ] Debug report share karein aur check karein ke number / message text nazar na aaye.

Har result `docs/device-matrix.md` ke "Test results" table mein likhein.
