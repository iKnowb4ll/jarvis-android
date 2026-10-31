# JARVIS Android — v2

A phone-first personal AI assistant prototype for Android, with OpenAI and Claude support, voice input/output, local conversation history, user-managed memory, app shortcuts and a floating launcher bubble.

## Start here
Read `PHONE_ONLY_BUILD.md` for instructions to build the APK using only your Android phone through GitHub Actions or a compatible on-device IDE.

## Project requirements
- Android 8.0 (API 26) or later
- Android SDK 35 to build
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Internet permission for AI provider requests

## Important security note
API keys are encrypted in app-private local preferences. Do not publish a build containing your personal API key. For a real public product, route AI requests through a secure backend with user authentication, rate limits and abuse controls.

## Current limits
No always-on wake word, no full overlay chat panel, no remote device administration and no hidden access to phone data. Voice input is tap-to-talk. The floating bubble launches the app.
