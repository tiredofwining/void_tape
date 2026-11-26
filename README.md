# VOID TAPE — Offline Android Music Player

Minimal dark offline music player for Android, built with Kotlin and AndroidX Media3.

## Included
- Scans music stored locally on the phone through Android MediaStore
- Lists tracks and artists
- Play/pause, previous/next, queue based on the local library
- MediaSessionService for background playback and system media controls
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact

## Build APK with GitHub Actions
1. Upload the contents of this folder to the root of your GitHub repository.
2. Open **Actions** and enable workflows if GitHub asks.
3. Run **Build VOID TAPE APK** (or push to `main`/`master`).
4. Open the completed workflow run and download the **VOID_TAPE-apk** artifact.
5. Extract the ZIP and install `app-debug.apk` on your Android phone. If Android blocks it, allow installs from that source.

## Build locally
Requires JDK 17, Android SDK platform 36, and Gradle 8.11+:
`gradle assembleDebug`

## Notes
- Playback uses files already on the device. No internet permission is declared and the app does not stream music.
- This is a starter build; the APK has not been compiled or device-tested in this environment.
