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

## Release 1.9.26

- Watch player (fullscreen and inline): the whole bottom block — gradient, progress bar and time — now hides together with the controls after the auto-hide delay, YouTube-style; tap to bring it back.
- Progress bar wiggle now follows playback instead of the controls state: the wheels bar waves while its wheel is playing (static when paused), and the watch bar stays lively whenever the video is playing.

## Release 1.9.25

- Top bar auto-hides while scrolling down and slides back on scroll up, YouTube-style (home, watch, channel, messages, notifications, settings, admin; wheels/chat/upload keep it pinned).
- Wheels: scrub bar sits flush at the bottom of the player instead of floating 100 dp above it; title and action overlays lifted above the scrub line.
- Includes all 1.9.24 changes (double-tap like, media-cache playback, feed race fix, wheels retry on network errors, locale-stable counters).

## Release 1.9.24

- Watch screen: double-tap the video to like (heart burst, same as Wheels), like/follow failures now toast instead of failing silently, comment posting errors show inline.
- Watch screen: playback no longer restarts or auto-plays when you tap like/follow or edit meta while paused; position/resume state is preserved across quality switches.
- Watch screen streams through the shared 300 MB media cache like Wheels, so replays and quality switches hit disk before the network.
- Home: sort/search responses are generation-guarded, so a slow older response can no longer overwrite the newer sort or search result.
- Wheels: network failure during preload shows "Couldn't load wheels" with a Retry button instead of a false "No wheels yet"; like/follow failures toast.
- Wheel comments: silent post failures now show an error; the header shows the real count only once loaded.
- Player timestamps and compact counts (1.2K) are locale-stable (US digits/separators); relative ages are grammatical ("1 minute ago") and include a years bucket.
- Chat: message list reuses a reversed view instead of reallocating on every recomposition.
- Removed dead `PlayerManager` lifecycle/resume machinery.

## Release 1.9.23

- `versionCode 10923`, `versionName 1.9.23`, `targetSdk 35`, `media3 1.6.1`, `core-ktx 1.16.0`, `navigation 2.9.3`, `lifecycle 2.9.2`, `work 2.10.3`, `coroutines 1.10.2`, `retrofit 2.12.0`, `coil 2.7.0`, version catalog at `gradle/libs.versions.toml`.
- Push to `main` triggers `.github/workflows/android-apk.yml`: builds release+debug, verifies signature, and auto-publishes `android-v1.9.23` with `WatchShark-1.9.23.apk` when the version has no tag yet.
- New signing key: `android/release.keystore` (gitignored, alias `watchshark`) is used when `RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD` env or Gradle props are set. CI restores it from `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` secrets, else falls back to `debug.keystore` so 1.8.x updates keep working. To cut over, upload the new keystore as secrets, then note 1.8.x users must uninstall first.
- Backend in same release: `/api/dm/send` accepts `to` or `user`.

## Configuration

The server URL is baked in at build time via `APP_URL` in
`app/build.gradle.kts` (default `https://watchshark.duckdns.org`).
