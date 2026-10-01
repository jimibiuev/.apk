# aycho

aycho is an Android automation assistant powered by a vision-language model (VLM). Describe a task in natural language and aycho completes it on your phone.

## Features

- Voice conversation: narration + TTS + bubbles, without interrupting the current screen
- Memory module: persists and reuses preferences across tasks
- Thinking-mode toggle
- 21 built-in skills: deep-link dispatch and GUI automation
- Blue/black dark theme, no emoji, unified Material icons
- Providers: Agnes AI (default), Alibaba DashScope, OpenAI, OpenRouter, custom OpenAI-compatible endpoint

## Requirements

- Android 8.0 (API 26)+
- Shizuku (or Root) for automation privileges

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17, Android SDK 34, Gradle 8.x.

## Open source notice

aycho is a derivative work of [Turbo1123/roubao](https://github.com/Turbo1123/roubao) (MIT License, Copyright (c) 2025 Roubao Team).

- The automation framework inherits the multi-agent architecture (Manager / Executor / Reflector / Notetaker) of roubao / Mobile-Agent
- The skill system follows the same Skills + Tools two-layer JSON structure
- Branding, UI, color scheme, icons, voice conversation and memory modules are aycho's own implementation

See [LICENSE](LICENSE) for the original license, [NOTICE](NOTICE) for attributions, and [CHANGELOG.md](CHANGELOG.md) for changes.
