# WatchShark Android app

Fully native Kotlin app for the WatchShark media platform, no WebView.
Material 3 UI, ExoPlayer video/audio, Coil image loading, Retrofit networking.

## Screens

- Home feed (Latest / Trending tabs, search, endless grid)
- Watch (ExoPlayer, likes, follow, comments with posting, share, quality menu, mini player)
- Wheels (vertical pager, likes, quality menu, comments drawer)
- Upload (video / wheel + thumbnail, streaming body, size and MIME checks, cancel)
- Channel (Videos / Wheels tabs, retry on error), Profile, Settings (light/dark/AMOLED), Admin (search, status filter, ban / unban / delete / restore / approve), Auth (validation, show/hide password), Notifications (mark all read, retry), Messages/Chat (polling, multiline, fixed unread badge)

## Requirements

- JDK 17
- Android SDK with API 35 + Build-Tools (`ANDROID_HOME` or `ANDROID_SDK_ROOT` set)

## Build

```sh
cd android
./gradlew assembleDebug
./gradlew :app:assembleRelease :app:assembleDebug --no-daemon -x test -x lint
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Release 1.9.3

- `versionCode 10903`, `versionName 1.9.3`, `targetSdk 35`, `media3 1.6.1`, `core-ktx 1.16.0`, `navigation 2.9.3`, `lifecycle 2.9.2`, `work 2.10.3`, `coroutines 1.10.2`, `retrofit 2.12.0`, `coil 2.7.0`, version catalog at `gradle/libs.versions.toml`.
- Push to `main` triggers `.github/workflows/android-apk.yml`: builds release+debug, verifies signature, and auto-publishes `android-v1.9.3` with `WatchShark-1.9.3.apk` when the version has no tag yet.
- New signing key: `android/release.keystore` (gitignored, alias `watchshark`) is used when `RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD` env or Gradle props are set. CI restores it from `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` secrets, else falls back to `debug.keystore` so 1.8.x updates keep working. To cut over, upload the new keystore as secrets, then note 1.8.x users must uninstall first.
- Backend in same release: `/api/dm/send` accepts `to` or `user`.

## Configuration

The server URL is baked in at build time via `APP_URL` in
`app/build.gradle.kts` (default `https://watchshark.duckdns.org`).
