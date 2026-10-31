# JARVIS v2 — build from your phone

## Easiest phone-only option: build in the cloud

This project includes a GitHub Actions workflow. You can trigger an Android APK build from your phone's browser; the compilation runs on GitHub's cloud computer.

1. Create or sign in to a GitHub account in your phone browser.
2. Create a new repository. Set it to **Private** because source code and configuration may be personal.
3. Upload the contents of this project folder (`JarvisAndroid`) to the repository. Keep the `.github/workflows/android-apk.yml` file and its folder structure.
4. Open the repository's **Actions** tab. If prompted, enable workflows.
5. Select **Build JARVIS Android APK**, then choose **Run workflow**.
6. Wait for the workflow to finish successfully.
7. Open the completed run and download the `JARVIS-debug-apk` artifact.
8. Extract the artifact ZIP on your phone, tap `app-debug.apk`, and follow Android's install prompts.

The APK is a debug build for personal testing, not a Play Store release. GitHub may change its interface; menu names can vary.

## Alternative: build locally on Android

Use AndroidIDE if a version compatible with your Android release is available. Open the project root (the folder containing `settings.gradle.kts`), install the requested SDK/build tools, sync Gradle, then build `:app:assembleDebug`.

Termux/proot is another option but requires manual Android SDK command-line setup. It is more advanced and can use substantial storage and memory.

## What is in v2?

- OpenAI and Anthropic Claude provider selection
- Editable model IDs for both providers
- Multi-turn conversation context
- Local conversation history (last 80 messages)
- Encrypted local storage for API keys and preferences
- Editable user-controlled memory field
- UK English speech input through Android's speech recognizer
- UK English text-to-speech when the installed engine provides it
- Voice-output toggle and stop-speaking control
- Quick-launch shortcuts for supported installed apps
- Floating JARVIS bubble that opens the main app
- Sci-fi dark HUD-inspired interface
- GitHub Actions workflow to build an APK from a phone

## Set up API access

1. Install the APK and open JARVIS.
2. Open **Settings**.
3. Choose OpenAI or Claude.
4. Enter the corresponding API key and model ID, then save.
5. Send a test message.

API usage is billed separately by the provider. Keep keys private. The app stores keys using Android encrypted preferences, but a production app should use a backend so API keys are never embedded in a distributed APK.

## Honest feature limits

- This is not a true always-listening wake-word assistant. Android restrictions and battery/privacy controls make continuous background listening a separate, permission-heavy feature.
- The floating bubble currently opens the full JARVIS app; it is not yet an independent chat window. Android may stop the background overlay service to save resources.
- App launching is limited to the explicit shortcut allow-list and installed apps. It cannot silently control every phone setting or access private app data.
- The Android speech recognizer may use the device's configured recognition service and may require internet.
- The default model IDs are examples. Provider model availability can change; change the model field if your account/provider no longer supports the default.
- This project has not been built or tested on your exact TECNO device. A successful cloud build confirms compilation, not full device compatibility.
