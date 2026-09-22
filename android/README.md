# WatchShark Android app

Native Kotlin wrapper for the WatchShark media platform with fullscreen video,
file uploads, in-app navigation and offline error handling.

## Requirements

- JDK 17
- Android SDK with API 34 + Build-Tools 34 (`ANDROID_HOME` or `ANDROID_SDK_ROOT` set)

## Build

```sh
cd android
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration

The server URL is baked in at build time via `APP_URL` in
`app/build.gradle.kts` (default `https://watchshark.duckdns.org`).
