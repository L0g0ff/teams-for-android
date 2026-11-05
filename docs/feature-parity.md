# Feature parity with Teams for Linux

Baseline: Teams for Linux [`4e89226`](https://github.com/IsmaelMartinez/teams-for-linux/tree/4e8922644976f5857b5323f54a0ed4dd0266e0cc) (version 2.29.0).

Teams for Linux is an Electron desktop wrapper. Teams for Android is a native WebView wrapper with no code in common, so a feature is ported only if it makes sense on a phone and the cost of maintaining it is low.

## Done

| Feature | Teams for Android | Issue |
|---|---|---|
| Persistent web session | WebView cookies and DOM storage, flushed on pause | — |
| Desktop browser identity | Linux Chromium User-Agent derived from the installed WebView version | — |
| Touch zoom, back navigation, Android 15 system bars | Native WebView settings and a `values-v35` theme | — |
| Camera and microphone | Only granted to Teams origins, after the Android runtime permission | [#10](https://github.com/L0g0ff/teams-for-android/issues/10) |
| Notifications | `Notification` shim bridged to Android notifications while the app is running | [#9](https://github.com/L0g0ff/teams-for-android/issues/9) |
| Launcher icon | Teams for Linux composition with an original robot | [#12](https://github.com/L0g0ff/teams-for-android/issues/12) |
| Signed releases | GitHub Actions: signed release on `main`, pre-release per PR | [#1](https://github.com/L0g0ff/teams-for-android/issues/1) |

## Planned

| Feature | Upstream reference | Issue |
|---|---|---|
| Open non-Teams links in the external browser | [`mainAppWindow/index.js`](https://github.com/IsmaelMartinez/teams-for-linux/blob/4e8922644976f5857b5323f54a0ed4dd0266e0cc/app/mainAppWindow/index.js) | [#3](https://github.com/L0g0ff/teams-for-android/issues/3) |
| Attachments via the Android file picker | — (Electron provides this) | [#4](https://github.com/L0g0ff/teams-for-android/issues/4) |
| Downloads via Android | [`downloadManager/index.js`](https://github.com/IsmaelMartinez/teams-for-linux/blob/4e8922644976f5857b5323f54a0ed4dd0266e0cc/app/downloadManager/index.js) | [#5](https://github.com/L0g0ff/teams-for-android/issues/5) |
| Teams deep links from other apps | [`protocolLink.js`](https://github.com/IsmaelMartinez/teams-for-linux/blob/4e8922644976f5857b5323f54a0ed4dd0266e0cc/app/mainAppWindow/protocolLink.js), [`deepLinkRouter.js`](https://github.com/IsmaelMartinez/teams-for-linux/blob/4e8922644976f5857b5323f54a0ed4dd0266e0cc/app/mainAppWindow/deepLinkRouter.js) | [#6](https://github.com/L0g0ff/teams-for-android/issues/6) |
| Offline and page-load error recovery | [`connectionManager/index.js`](https://github.com/IsmaelMartinez/teams-for-linux/blob/4e8922644976f5857b5323f54a0ed4dd0266e0cc/app/connectionManager/index.js) | [#7](https://github.com/L0g0ff/teams-for-android/issues/7) |
| Clear session (sign out) | — | [#8](https://github.com/L0g0ff/teams-for-android/issues/8) |
| Keyboard must not cover the compose box | — (Android only) | [#11](https://github.com/L0g0ff/teams-for-android/issues/11) |
| Notifications after Android stops the app | — | [#19](https://github.com/L0g0ff/teams-for-android/issues/19) (investigation) |

## Deferred

These are only added once there is a concrete mobile need.

- **Background service / foreground service:** costs battery and requires a persistent notification. Teams Web push needs a browser push service that Android WebView lacks; see #19.
- **Screen sharing:** Android WebView does not support `getDisplayMedia`. Supporting it would require a separate MediaProjection pipeline.
- **Calls in the background:** requires a foreground service plus audio-focus and lifecycle handling.
- **Multiple accounts:** WebView shares one cookie and storage profile per app. Separate profiles require extra Activities and switching logic.
- **Custom themes, backgrounds, stickers and CSS:** cosmetic, and they break whenever Teams changes its DOM.
- **Authentication helpers and Graph API integration:** desktop workarounds; Microsoft sign-in works in the WebView as is.

## Not applicable

Desktop-only and without an Android counterpart, or already provided by Android itself:

- system tray, tray badge and window flashing;
- global keyboard shortcuts;
- D-Bus and MQTT status publishing;
- window management, multiple monitors and zoom persistence per window;
- desktop auto-update (Android installs updates through the signed APK);
- Electron-specific sandbox and IPC configuration.
