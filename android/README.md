# WatchShark Android app

Fully native Kotlin app for the WatchShark media platform — no WebView.
Material 3 UI, ExoPlayer video/audio, Coil image loading, Retrofit networking.

## Screens

- Home feed (Latest / Trending tabs, search, endless grid)
- Watch (ExoPlayer, likes, follow, comments, edit/delete, quality menu)
- Wheels (vertical pager, shared ExoPlayer playlist, likes, quality menu)
- Music (track list, mini player, full player with seek)
- Upload (video / wheel / music + thumbnail, progress)
- Channel, Profile, Settings, Admin (ban / unban / delete / restore / approve), Auth, Notifications

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
