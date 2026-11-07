<p align="center">
  <img src="artwork/icon-1024.png" alt="Teams for Android icon" width="128">
</p>

# Teams for Android

A minimal Android WebView wrapper for Microsoft Teams Web.

## Why

The official Teams Android app is not always wanted or available. Teams Web can work on Android when it identifies as a current desktop browser, but running it in a full browser such as Cromite adds more overhead than a dedicated wrapper needs.

This project opens `https://teams.cloud.microsoft/` in the system WebView, keeps Android touch behavior, enables the storage and cookies required by Microsoft sign-in, and derives a Linux desktop User-Agent from the installed WebView's Chromium version.

## Relationship to Teams for Linux

This project was inspired by [Teams for Linux](https://github.com/IsmaelMartinez/teams-for-linux), which proves that a thin desktop wrapper around Teams Web is practical.

**It does not contain Teams for Linux source code.** Teams for Linux is an Electron desktop application; this is an independent native Android WebView wrapper implementing the same basic idea with much less code.

Microsoft Teams itself is proprietary software served by Microsoft and is not included in this repository.

## Feature parity

See [docs/feature-parity.md](docs/feature-parity.md) for which Teams for Linux features are ported, planned, deferred or not applicable.

## Artwork

The launcher icon adapts the visual composition of the Teams for Linux icon, replacing Tux with an original Android-inspired robot. Teams for Linux credits its icon source to Icon Duck under CC BY 4.0. See [NOTICE.md](NOTICE.md) for attribution.

## Current scope

- Teams Web sign-in and chat
- Persistent cookies
- Android touch and pinch zoom
- Linux desktop User-Agent using the installed Chromium/WebView version
- Camera and microphone for Teams calls, granted only to Teams origins after Android asks for permission
- Android notifications for Teams Web notifications while the app runs in the background; tapping one opens the chat
- Teams chat and meeting links (`https://teams.microsoft.com/l/...`, `/meet/...`, `msteams:`) from other apps open in the running app. For `https` links on Android 12+, enable them once under **Android Settings → Apps → Teams → Open by default → Add link**.

Not implemented: notifications after Android stops the app (Teams Web push needs a browser push service that WebView lacks), calls in the background, screen sharing, background services, or file-picker integration.

## Diagnostics

Press **volume up and volume down together** to open a diagnostics menu:

- **Media diagnostics:** camera/microphone permission state, capture test, device list and Android call-audio routing, with a Copy button for bug reports.
- **Test notification in 10 s:** sends a notification through the same path as Teams, so the app can be moved to the background first.
- **Android app settings:** permissions and notifications for this app.
- **Call audio: switch to earpiece/loudspeaker** (Android 12+): Teams cannot select an audio output in WebView, so this switches between the earpiece and the loudspeaker.

Pull-request test builds additionally allow WebView remote debugging via `chrome://inspect`.

## Build

Requirements: JDK 21 and an Android SDK containing API 37.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

The APK is written to:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Local release builds are unsigned. Each merge to `main` creates a signed GitHub Release with an increasing Android version code.

## Status

This is an experimental wrapper. Microsoft can change Teams Web or its browser checks at any time.
