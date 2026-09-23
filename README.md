# WatchShark

Open-source media platform — watch and share videos, wheels (shorts) and music.
Single Go binary + SQLite + FFmpeg, Material 3 web UI, native Kotlin Android app.

## Features

- Video / wheel / music uploads with AV1 + Opus transcoding (720p / 480p / 360p renditions)
- Channels, follows, upload notifications, likes, comments
- Email verification, password reset, admin approval + bans / soft-delete with notices
- View counting (signed-in users), trending, search with animated expanding search box
- PWA-friendly web UI (`public/`), Android app (`android/`) and
  forked YouTube-style client (`android-tube/`, also at
  Matko802/WatchSharkTube)

## Server quick start (podman)

```sh
podman build -t watchshark:latest -f Containerfile .
podman-compose -f podman-compose.yml up -d
```

Or run locally with Go 1.27+:

```sh
go run .  # PORT=3000 DATA_DIR=./data PUBLIC_DIR=./public
```

Key env vars: `PORT`, `DATA_DIR`, `PUBLIC_DIR`, `JWT_SECRET`, `ADMIN_USER`,
`APP_URL`, `MAX_UPLOAD_MB`, `QUOTA_GB`, `SMTP_HOST/PORT/USER/PASS/FROM`.

See `deploy.sh` and `caddy-snippet.txt` for the full self-host setup.

## Android app (native Kotlin)

Fully native Kotlin app in `android/` — no WebView, no Flutter.
Material 3 UI, ExoPlayer video/audio, Coil image loading, Retrofit networking.

```sh
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`
(downloads for releases live on the GitHub Releases page).

## Layout

- `server.go` — backend (auth, videos, wheels, music, notifications, admin)
- `public/` — web frontend
- `android/` — native Kotlin Android app (mirrors the website)
- `Containerfile`, `podman-compose.yml`, `deploy.sh`, `caddy-snippet.txt` — self-hosting

## License

MIT — see `LICENSE`.
