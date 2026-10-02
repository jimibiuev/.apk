<p align="center">
  <img src="docs/images/banner.png" alt="aycho" width="100%">
</p>

<h1 align="center">aycho</h1>

<p align="center">Say it once — your phone takes care of the rest.</p>

<p align="center">
  <a href="README.md">简体中文</a> ｜ <b>English</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-2674F8?style=flat-square" alt="platform">
  <img src="https://img.shields.io/badge/version-2.1.0-2674F8?style=flat-square" alt="version">
  <img src="https://img.shields.io/badge/license-MIT-2674F8?style=flat-square" alt="license">
  <img src="https://img.shields.io/badge/theme-blue%20%2F%20black-0B1220?style=flat-square" alt="theme">
</p>

---

## Contents

- [Introduction](#introduction)
- [App Overview](#app-overview)
- [Highlights](#highlights)
- [How It Works](#how-it-works)
- [Requirements](#requirements)
- [Download & Install](#download--install)
- [Build from Source](#build-from-source)
- [Quick Start](#quick-start)
- [Model Providers](#model-providers)
- [Built-in Skills](#built-in-skills)
- [Project Layout](#project-layout)
- [FAQ](#faq)
- [Open Source Notice](#open-source-notice)
- [License](#license)

---

## Introduction

**aycho** is an automation assistant that runs on your Android phone. Describe what you want in plain language — "order my usual takeout", "navigate to the office", "message mom that I'll be late" — and aycho reads the screen, plans the steps, and performs the taps, text input and navigation itself.

It relies on no private APIs and requires no cooperation from the target apps. aycho takes the **vision + UI automation** route: it captures the screen to understand what is displayed, then interacts the way a person would. That makes it work with virtually any app.

Everything happens in a floating bubble above your current screen, so your work is not interrupted. When the task finishes, the bubble disappears and leaves you the result.

## App Overview

<p align="center">
  <img src="docs/images/app_icon.png" width="112" alt="App icon">
  &nbsp;&nbsp;&nbsp;
  <img src="docs/images/splash_preview.png" width="260" alt="Splash screen">
</p>

<p align="center"><sub>App icon and splash screen — blue / black theme</sub></p>

The interface consists of six screens:

| Screen | Purpose |
| --- | --- |
| Home | Type or speak a task; live narration bubbles during execution |
| Capabilities | Browse all built-in skills and what they can do |
| Memory | Manage preferences reused across tasks |
| History | Review previously completed tasks |
| Settings | Model provider, thinking mode, voice narration |
| Onboarding | First-run permissions and usage walkthrough |

## Highlights

**Natural-language driven**
No commands or menu paths to memorise. State the goal and aycho handles step planning, screen recognition and error recovery on its own.

**Screen understanding**
Before each action it captures and parses the screen: it locates buttons, input fields and list items, works out which page it is on, then decides the next move.

**Voice narration**
A narration bubble reports progress in real time, with optional TTS playback. Narration stays in a floating layer, so your current page is never pushed away — and you can interrupt or stop at any moment.

**Memory module**
Preferences are persisted and reused across tasks — your usual order, home and office addresses, frequent contacts. No need to repeat yourself next time.

**Thinking mode toggle**
Expand the model's reasoning chain when precision matters, or switch it off for a faster response path.

**Skill system**
21 built-in skills covering takeout, travel, shopping, social, media, payments and utilities. Every skill has two execution paths: a deep link when one exists, UI automation as the fallback.

**Multiple model backends**
The model gateway supports OpenAI, Gemini and Claude protocol families out of the box, plus any OpenAI-compatible endpoint. Switching models needs no code change — just a setting.

**Consistent visual language**
A blue/black dark theme throughout, Material icons only, and no emoji anywhere in the interface.

## How It Works

Internally aycho runs a cooperative agent loop that advances in four steps:

```text
Observe (screenshot + UI parsing)
   ↓
Plan (understand the goal → derive the next action)
   ↓
Act (tap / type / swipe / deep link)
   ↓
Verify (confirm the result; roll back and retry if needed)
   ↑______________ loop until the task is done ______________|
```

Key design points:

- **Task-level blackboard** — planning, acting and verification exchange state through a shared blackboard instead of acting in isolation.
- **Result verification** — every action is followed by a confirmation step, so a mis-tap is caught immediately rather than compounding.
- **Session memory with token control** — screenshots are discarded right after use; only the necessary text context is kept, so long tasks never blow up the context window.
- **Capability registry** — every primitive tool (app lookup, app launch, URI routing, shell bridge, HTTP request, clipboard) is registered centrally and invoked on demand by the planner.

## Requirements

| Item | Requirement |
| --- | --- |
| OS | Android 8.0 (API 26) or later |
| Privileges | Shizuku (recommended) or Root |
| Network | Internet access to your chosen model provider |
| Model | A valid API key (Agnes AI by default) |

> aycho obtains automation privileges through **Shizuku** — no Root required. Shizuku grants privileges at the ADB level, which is more stable than an accessibility service and does not sit permanently in your screen-reading path.

## Download & Install

1. Open the [Releases](https://github.com/jimibiuev/aycho/releases) page and download `aycho-v2.1.0-release-signed.apk`
2. Allow "Install unknown apps" in system settings, then install
3. Install and start **Shizuku**, and grant it privileges (on Android 11+ you can start it via wireless debugging)
4. In aycho, open **Settings → API Provider** and enter your API key
5. Go back to Home and state your first task

**Verify the package** (optional):

```bash
sha256sum -c SHA256SUMS.txt
```

## Build from Source

Requires JDK 17, Android SDK 34 and Gradle 8.x:

```bash
git clone https://github.com/jimibiuev/aycho.git
cd aycho
./gradlew assembleDebug
```

The output lands in `app/build/outputs/apk/debug/`.

To build a signed release yourself, configure signing in `app/build.gradle.kts` and run:

```bash
./gradlew assembleRelease
```

> Keep your signing key (`.jks`) safe — if it is lost, you can no longer push updates to an already published app. The repository `.gitignore` excludes all keystore files by default.

## Quick Start

Once installed, just say what you need on the Home screen:

| You say | aycho does |
| --- | --- |
| Find a highly rated Sichuan restaurant nearby | Opens a local-services app and filters by rating |
| Navigate to the office | Plans the route and launches navigation |
| Call a car to the airport | Opens a ride-hailing app, sets the destination and books |
| Tell mom I'll be late | Opens a chat app, finds the contact and sends it |
| Set an alarm for 7 a.m. tomorrow | Opens the clock app and configures it |
| Buy two movie tickets for tomorrow afternoon | Walks the ticketing flow and picks seats |
| Post an update with the photo I just took | Composes and publishes a social post |

A narration bubble appears above the screen during execution, describing each step. It collapses automatically when the task completes.

## Model Providers

Choose or define one in **Settings → API Provider**:

| Provider | Protocol | Notes |
| --- | --- | --- |
| Agnes AI | OpenAI-compatible | Default, works out of the box |
| Google Gemini | Gemini | Native protocol |
| OpenAI | OpenAI | Official endpoint |
| Anthropic Claude | Claude | Native protocol |
| Custom | OpenAI-compatible | Any compatible endpoint: Base URL + model ID + API key |

**API keys are stored on-device only**, in encrypted storage. They are never uploaded to any third party.

## Built-in Skills

21 skills, grouped by scenario:

**Lifestyle & Shopping**

| Skill | Description |
| --- | --- |
| Order takeout | Pick a restaurant by taste and place the order |
| Find food nearby | Search nearby restaurants and places to eat |
| Find fun nearby | Search nearby attractions and entertainment |
| Book a hotel | Reserve hotels and homestays |
| Buy movie tickets | Purchase tickets and select seats |
| Online shopping | Shop and place orders online |

**Travel**

| Skill | Description |
| --- | --- |
| Route navigation | Plan a route and navigate to the destination |
| Call a ride | Hail a ride-hailing car |

**Social & Content**

| Skill | Description |
| --- | --- |
| Send message | Message a friend |
| Post an update | Publish social posts with media |
| Photo notes | Publish photo notes and long-form posts |

**Media & Reading**

| Skill | Description |
| --- | --- |
| Play music | Play the music you want |
| Play video | Watch videos and short clips |
| E-book reading | Read e-books |

**Payment & Utilities**

| Skill | Description |
| --- | --- |
| Scan to pay | Scan a code and complete payment |
| Scan QR code | Scan QR codes and barcodes |
| Take a photo | Open the camera and shoot |
| Set alarm | Set alarms and reminders |

**AI Capabilities**

| Skill | Description |
| --- | --- |
| Q&A | Conversational Q&A with the AI assistant |
| General assistant | Delegate queries, planning and daily tasks to the AI |
| Image generation | Generate images with AI |

## Project Layout

```text
app/src/main/java/com/aycho/app/
├── App.kt / MainActivity.kt        Entry point and main host
├── agent/                          Agent runtime
│   ├── AgentRuntime.kt             Task loop: observe → plan → act → verify
│   ├── Planner.kt                  Screen understanding and next-action planning
│   ├── Actuator.kt                 Action execution (tap / type / swipe / deep link)
│   ├── Verifier.kt                 Result verification and correction
│   ├── Scribe.kt                   Run log and narration text
│   ├── Blackboard.kt               Task-level shared state
│   └── SessionMemory.kt            Session memory (screenshots discarded after use)
├── skills/                         Skill system (SkillRegistry / IntentRouter)
├── tools/                          Primitive tools (AppFinder / AppLauncher / UriRouter /
│                                   ShellBridge / HttpRequest / ClipboardTool)
├── controller/                     AppIndexer (app index) / DeviceBridge (device state)
├── vlm/ModelGateway.kt             Model gateway: OpenAI / Gemini / Claude
├── ui/                             UI layer
│   ├── HeadsUpService.kt           Floating narration bubble (foreground service)
│   ├── screens/                    Home / Capabilities / Memory / History / Settings / Onboarding
│   └── theme/                      Blue/black dark theme
├── voice/VoiceManager.kt           Narration and TTS playback
├── service/ShellService.kt         Shell execution bridge
├── data/                           Preference and memory storage
└── utils/CrashHandler.kt           Crash handling

app/src/main/assets/
├── intents.json                    Skill and intent definitions
└── licenses/                       Third-party license notices
```

## FAQ

**Do I need Root?**
No. Shizuku is enough. On Android 11+ you can activate it over wireless debugging, without a computer.

**Does it read my private data?**
aycho captures the screen only when you start a task, purely to decide where to interact next. Screenshots are deleted immediately after each round — never uploaded, never retained.

**Where is my API key stored?**
In encrypted storage on your device only. It is never synced or uploaded.

**Why does it ask for confirmation sometimes?**
For payments, passwords and privacy-related actions, aycho pauses and asks you first, to prevent accidental loss.

**Which Android versions are supported?**
Android 8.0 (API 26) and later.

**Can it automate any app?**
Deep links are used when available; everything else falls back to UI automation. Most mainstream apps work; a few with aggressive anti-automation detection may be limited.

## Open Source Notice

aycho is a derivative work of the open-source project [Turbo1123/roubao](https://github.com/Turbo1123/roubao) (MIT License). Branding, UI, voice conversation, the memory module and the skill system are reimplemented on top of the inherited automation framework.

- The multi-agent execution architecture is inherited from roubao / Mobile-Agent
- The skill system follows the same Skills + Tools two-layer JSON structure
- Branding, UI, color scheme, icons, voice and memory modules are aycho's own implementation

The upstream MIT license text is in [LICENSE](LICENSE); third-party attributions are in [NOTICE](NOTICE) and [THIRD_PARTY_LICENSES](app/src/main/assets/licenses/THIRD_PARTY_LICENSES.txt); change history is in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT License](LICENSE)
