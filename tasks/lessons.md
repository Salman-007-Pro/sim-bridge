# Lessons

## 2026-10-24 — Toolchain versions

- **Correction:** Maine Gradle 8.14.3 + AGP 8.13 choose kiya tha "safe" samajh kar; user ne latest Gradle maanga.
- **Rule:** Naya project shuru karte waqt latest *stable* Gradle (`services.gradle.org/versions/current`) aur us ke compatible latest AGP use karo. Conservative purane versions default mat lo.
- **Rule:** Version bump se pehle chain verify karo: Gradle -> AGP (min Gradle) -> Kotlin (AGP ki KGP dependency) -> KSP (AGP 9 built-in Kotlin ke liye KSP 2.3+) -> Hilt (`kotlin-metadata-jvm` version Kotlin se match ho).
- **AGP 9 note:** Kotlin built-in hai; Android modules par `org.jetbrains.kotlin.android` apply nahi karna. Root par `apply false` se KGP version control hota hai.
