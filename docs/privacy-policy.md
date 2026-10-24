# SIM Bridge — Privacy Policy (draft for legal review)

_Last updated: 25 October 2026. This draft must be reviewed by a lawyer before publication._

## Summary

SIM Bridge connects two phones you own over your local Wi-Fi or hotspot. It has **no account, no server and no
cloud storage**. Your calls and messages never pass through us.

## What the apps handle

| Data | Where it lives | Why |
|---|---|---|
| SMS (number, text, time, status) | Host phone (system SMS store + SIM Bridge database) and, after sync, the paired Client | To send and receive SMS for you on the Client |
| Call events (number, direction, times, outcome) | Host and Client databases | Incoming call alerts, call controls and call history |
| SIM and network status (carrier, signal, battery) | Sent from Host to Client, not stored long-term | To show "Host signal" honestly |
| Device keys | Android Keystore on each phone; never exported | Encrypt and authenticate the link |
| Paired-device names and key fingerprints | Each phone's app storage | Recognise your own phones |
| Contacts (optional, Client only) | Read on the Client, never sent anywhere | Show names instead of numbers |
| Camera (Client, pairing only) | Processed on the phone, nothing saved | Read the pairing QR code |

Call **audio** is not transmitted by the current version. The apps do not record calls.

## Sharing

Nothing is sent to SIM Bridge or third parties. Data travels only between your paired phones, encrypted with TLS 1.3.
A debug report is created only when you tap **Share debug report**; phone numbers, message text, codes and keys are
removed first, and you choose where it goes.

## Your controls

- **Unpair** on either phone removes access immediately; the Client also replaces its key and deletes local data.
- **Wipe messages and call history** on the Client deletes local copies.
- **Remove all phones and reset keys** on the Host revokes every pairing.
- Uninstalling either app deletes its data. Backups to cloud are disabled.

## Permissions

Permissions are requested only when needed and each one is explained in the app (Host: default phone and SMS app,
phone status, notifications; Client: notifications, camera for pairing, optional contacts).

## Children

SIM Bridge is not directed at children.

## Contact

Add the publisher's contact address before publication.
