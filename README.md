# Kryzz AI

> ℹ️ This is the **first public release** of the source code. The codebase corresponds to internal build **v5.1.15** — the prior versions (1.x through 5.1.14) were never published.

A private, local-first Android AI workspace for chat, voice, and tool-driven workflows.

Kryzz AI runs on your device and talks directly to the AI providers you choose — OpenRouter, optional Fish Audio for voice synthesis, and optional Parallel for web research. Nothing leaves your phone that you didn't put there yourself: no bundled API keys, no analytics, no telemetry, no HTTP logging.

Build it yourself, read the code, fork it.

---

## Screenshots

| New chat | Agent workflows |
| :------: | :-------------: |
| ![New chat home with Chat/Agent tabs and message composer](work-assets/screenshots/01-chat-home.jpg) | ![Agent workflow picker — Think & Research, Create & Build](work-assets/screenshots/02-agent-workflows.jpg) |

| Voice session | Side drawer |
| :------------: | :---------: |
| ![Voice session overlay in Listening state](work-assets/screenshots/03-voice-session.jpg) | ![Side drawer with Chat / Agent / Voice / Cron navigation and recent chats](work-assets/screenshots/04-side-drawer.jpg) |

| Settings | About |
| :------: | :---: |
| ![Settings home with sectioned list](work-assets/screenshots/05-settings.jpg) | ![Galactic About overlay — triple-tap logo, credits, version](work-assets/screenshots/06-galactic-about.jpg) |

---

## What you can do

- **Chat.** Streamed conversations with a model you pick, attachments, reasoning controls, editing, regeneration, cancellation, citations, token usage, and cost details.
- **Agent.** Bounded tool workflows, deep research, wide web search, content creation, reusable local skills, and generated files.
- **Voice.** Microphone transcription, spoken replies, automatic turn-taking, and barge-in to interrupt Kryzz mid-sentence.
- **Memory.** Local, searchable, categorized, per-entry enable/disable, with optional cross-chat recall during conversation.
- **Library.** Pin, folder, search, rename, delete, and JSON-import/export your conversations and skills.
- **Generated outputs.** DOCX, XLSX, SQLite, code ZIPs, images, video, and audio, kept in private app storage until you share them.

Multimodal attachments and generated media require a model that supports them. Search and voice features need the relevant provider configured.

---

## Download (debug APK)

A pre-built debug APK for the source in this repository lives at:

```
work-assets/releases/kryzz-ai-5.1.15-debug.apk
```

About 74 MB, debug-signed with the Android Studio default key, package `ai.daylight.assistant.debug`.

**To install on Android 9+:**

1. Transfer the APK to the device (USB, cloud, AirDrop-via-web, whatever).
2. On the device: **Settings → Apps → Special access → Install unknown apps** → allow your file manager.
3. Tap the APK and confirm.
4. Launch Kryzz AI from the launcher; the icon is labelled with the version pill.

> This is a debug build meant for evaluation and sideloading. For long-term installs on a daily-driver device, build your own signed release APK from this source.

---

## Privacy, in one breath

- Everything you write or save is stored locally on the device (Room + DataStore).
- Credentials are encrypted with **AES-GCM** under a non-exportable **Android Keystore** key. They are never sent anywhere.
- Conversation **backups** never include credentials.
- Android backup, device-to-device transfer, cleartext traffic, and HTTP logging are all **disabled**.
- The microphone is only active during a voice session; recorded audio is cached for transcription only and deleted afterward.

One small thing: for a fresh conversation's auto-title, Kryzz may send at most the first 1,500 characters of your first message through OpenRouter to a small model (Gemini 2.5 Flash Lite) for naming. This runs in parallel with your first answer, never blocks it, and falls back to a local title if it can't complete.

---

## Providers you'll need

- **OpenRouter** — required, for AI requests, transcription, and model discovery.
- **Parallel** *(optional)* — used only for explicit web search and research workflows.
- **Fish Audio** *(optional)* — for Fish-hosted voice synthesis; OpenRouter TTS also works.

You enter keys only inside the installed app. They are not part of this repository and not needed to build it.

---

## Requirements

- **JDK 17**
- **Android SDK platform 35**
- **Android Build Tools 35.0.0**
- Network access for the first Gradle dependency resolution

Minimum runtime: **Android 9 / API 28**.
Included wrapper: **Gradle 8.9**.

---

## Build

```powershell
.\gradlew.bat assembleDebug
```

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Android instrumented tests (needs an emulator or device connected):

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The debug APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Debug package: `ai.daylight.assistant.debug`.
Release package: `ai.daylight.assistant`.

---

## Release signing

Signing material is **never** committed. Production credentials are read only from environment variables:

```powershell
$env:KRYZZ_KEYSTORE='C:\secure\kryzz-release.jks'
$env:KRYZZ_STORE_PASSWORD='<store password>'
$env:KRYZZ_KEY_ALIAS='kryzz-release'
$env:KRYZZ_KEY_PASSWORD='<key password>'
.\gradlew.bat assembleRelease
```

Without those variables Gradle produces an unsigned release build. The signing certificate must match any version already installed on a user's device for in-place upgrades to work.

---

## First run

1. Install the APK and launch Kryzz AI.
2. Enter an OpenRouter key.
3. Test the key in the onboarding screen.
4. *(Optional)* Enter a Parallel key for web research.
5. *(Optional)* Pick a voice provider (Fish Audio or OpenRouter TTS).
6. *(Optional)* Toggle cross-chat memory.

---

## Architecture (one-screen tour)

| Layer        | Responsibility                                                         |
| ------------ | ---------------------------------------------------------------------- |
| `data/local` | Room entities, DAO, migrations, DB schema                              |
| `data/prefs` | DataStore-backed settings + legacy-value coercion                      |
| `data/remote`| Provider clients, request schemas, streaming, error handling           |
| `security`   | Android-Keystore-backed credential encryption                          |
| `domain`     | Personas, Agent workflows, skills, research, citations                 |
| `voice`      | Recording, playback, adaptive turn detection, session state            |
| `ui`         | Compose navigation, screens, drawer, design system, ViewModels         |

**Stack:** Kotlin · Jetpack Compose (Material 3) · Coroutines / Flow · Room · DataStore · OkHttp · MVVM.

---

## Repository layout

```
app/                     ← Kotlin source, Android resources, tests, Room schemas
build.gradle.kts
settings.gradle.kts
gradle/  gradlew*        ← Gradle wrapper
work-assets/
  ├── screenshots/       ← screenshots embedded in this README
  ├── releases/          ← compiled APKs (debug-signed, eval-only)
  ├── kryzz-mark-chroma.png
  └── qa-4.0.1/          ← older QA assets (historical reference)
```

---

## License

MIT — see [`LICENSE`](./LICENSE).

Copyright (c) 2026 Kristofers Kemers.
