# WatchShark

Open-source media platform — watch and share videos, wheels (shorts) and music.
Single Go binary + SQLite + FFmpeg, Material 3 web UI, Dart/Flutter Android app.

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

## Android app (Flutter)

Dart/Flutter app in `flutter_app/` mirroring the website 1:1 — home feed,
watch with custom player controls, wheels pager, music discover with player,
upload, channel, settings, admin, auth dialog, notifications.

```sh
cd flutter_app
flutter pub get
flutter build apk --debug --split-per-abi
```

APKs: `flutter_app/build/app/outputs/flutter-apk/app-*-debug.apk`
(downloads for releases live on the GitHub Releases page).

## Layout

- `server.go` — backend (auth, videos, wheels, music, notifications, admin)
- `public/` — web frontend
- `flutter_app/` — Dart/Flutter Android app (mirrors the website)
- `Containerfile`, `podman-compose.yml`, `deploy.sh`, `caddy-snippet.txt` — self-hosting

## License

MIT — see `LICENSE`.
