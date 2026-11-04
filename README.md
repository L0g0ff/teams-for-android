# Teams for Android

A minimal Android WebView wrapper for Microsoft Teams Web.

## Why

The official Teams Android app is not always wanted or available. Teams Web can work on Android when it identifies as a current desktop browser, but running it in a full browser such as Cromite adds more overhead than a dedicated wrapper needs.

This project opens `https://teams.cloud.microsoft/` in the system WebView, keeps Android touch behavior, enables the storage and cookies required by Microsoft sign-in, and derives a Linux desktop User-Agent from the installed WebView's Chromium version.

## Relationship to Teams for Linux

This project was inspired by [Teams for Linux](https://github.com/IsmaelMartinez/teams-for-linux), which proves that a thin desktop wrapper around Teams Web is practical.

**It does not contain Teams for Linux source code.** Teams for Linux is an Electron desktop application; this is an independent native Android WebView wrapper implementing the same basic idea with much less code.

Microsoft Teams itself is proprietary software served by Microsoft and is not included in this repository.

## Current scope

- Teams Web sign-in and chat
- Persistent cookies
- Android touch and pinch zoom
- Linux desktop User-Agent using the installed Chromium/WebView version

Not implemented: Android push notifications, native calls, background services, or file-picker integration.

## Build

Requirements: JDK 21 and an Android SDK containing API 37.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

The APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The current release build uses Android's debug signing key and is intended for sideloading and testing, not store distribution.

## Status

This is an experimental wrapper. Microsoft can change Teams Web or its browser checks at any time.
