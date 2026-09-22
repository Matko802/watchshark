<div align="center">
  <br>
  <h1>WatchShark Tube</h1>
  <p><strong>Fork of <a href="https://github.com/codexskills/YouTubeClone">codexskills/YouTubeClone</a>, rewired to run on the <a href="https://github.com/Matko802/watchshark">WatchShark</a> media server instead of YouTube.</strong><br>
  Same Compose Material 3 UI and ExoPlayer playback — Home feed, search, watch with qualities, Shorts-style wheels, channels with follow, likes, comments, notifications, uploads, and WatchShark login. All YouTube/InnerTube and extractor code was removed.</p>
  <br>
  <img src="https://img.shields.io/badge/YouTubeClone-v1.0-FF0000?style=flat-square&logo=youtube&logoColor=white" alt="Version">
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/API-24%2B-FF6600?style=flat-square&logo=android&logoColor=white" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License">
  <br><br>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/📱_YouTubeClone-FF0000?style=for-the-badge&logo=youtube&logoColor=white&labelColor=111">
    <img src="https://img.shields.io/badge/📱_YouTubeClone-FF0000?style=for-the-badge&logo=youtube&logoColor=white&labelColor=fff" alt="YouTubeClone" width="400">
  </picture>
  <br><br>
  <p><strong>A premium, open-source YouTube client for Android</strong><br>
  Built with Kotlin + Jetpack Compose + ExoPlayer — featuring YouTube-style UI, video playback, settings, and more.</p>
  <br>
  <a href="http://187.127.175.206:8080/app-release.apk">
    <img src="https://img.shields.io/badge/⬇_Download_APK-FF0000?style=for-the-badge&logo=android&logoColor=white" alt="Download">
  </a>
  &nbsp;
  <a href="https://github.com/codexskills/YouTubeClone">
    <img src="https://img.shields.io/badge/🐙_Source_Code-181717?style=for-the-badge&logo=github&logoColor=white" alt="Source">
  </a>
  &nbsp;
  <a href="https://t.me/codex_update">
    <img src="https://img.shields.io/badge/📢_Updates-0088CC?style=for-the-badge&logo=telegram&logoColor=white" alt="Updates">
  </a>
  <br><br>
</div>

---

## ✨ Highlights

| Area | Features |
|------|----------|
| **🎬 Player** | ExoPlayer, double-tap seek (±10s), speed (0.25x–5x), quality selector, loop, captions, stats for nerds, fullscreen pinch-zoom |
| **📺 UI** | YouTube-style flat bottom nav (Home, Shorts, Create+, Notifications, Profile), dark theme, mini player |
| **🏠 Home** | Category feeds, video cards, channel bar, pull-to-refresh, shimmer loading |
| **📱 Shorts** | Vertical swipe feed, autoplay, like/dislike, share |
| **👤 Profile** | Avatar, stats, playlists, history, downloads, liked videos, sign-in prompt |
| **⚙️ Settings** | 11 sub-pages: General, Account, Data Saver, Autoplay, Quality, Downloads, TV, History, Privacy, Notifications, Accessibility |
| **🔐 Login** | 2-step login with guest mode, YouTube-style form |
| **⬇️ Downloads** | Quality selector, download manager |
| **🔔 Notifications** | Notification feed |
| **💬 About** | Developer credits, version info, release notes |

---

## 📸 Screenshots

```
┌─────────────────────────────────────────────────────────────────┐
│  Screenshots coming soon — Replace this section with your own  │
│  device screenshots of Home, Player, Settings, Profile, etc.   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Download & Install

```bash
# Option 1: Direct download
curl -O http://187.127.175.206:8080/app-release.apk
adb install app-release.apk

# Option 2: Browse to URL
open http://187.127.175.206:8080/
```

### Build from Source

```bash
# Clone
git clone https://github.com/codexskills/YouTubeClone.git
cd YouTubeClone

# Build
chmod +x gradlew
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
# Sign with your keystore before install
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose, Material 3 |
| **Player** | ExoPlayer (Media3) |
| **API** | InnerTube (YouTube) |
| **Async** | Coroutines + Flow |
| **DI** | Hilt |
| **Storage** | DataStore Preferences |
| **Build** | Gradle 9.4, Kotlin 2.3 |
| **Min SDK** | API 24 (Android 7.0) |
| **Target SDK** | API 36 |

---

## 📁 Architecture

```
app/
├── src/main/java/com/darkk/youtube/
│   ├── data/
│   │   └── LocalRepository.kt        # DataStore + session management
│   ├── download/
│   │   └── DownloadManager.kt        # Video download handler
│   ├── innertube/
│   │   ├── InnerTubeClient.kt        # YouTube API client
│   │   └── PlayerData.kt             # Data models
│   ├── ui/
│   │   ├── components/               # Reusable composables
│   │   │   ├── VideoOptionsSheet.kt
│   │   │   ├── DownloadDialog.kt
│   │   │   └── YouTubeTopBar.kt
│   │   ├── screens/                  # All screen composables
│   │   │   ├── HomeScreen.kt
│   │   │   ├── ShortsScreen.kt
│   │   │   ├── PlayerScreen.kt       # 1800+ lines
│   │   │   ├── ProfileScreen.kt
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── LoginScreen.kt
│   │   │   ├── AboutScreen.kt
│   │   │   ├── NotificationsScreen.kt
│   │   │   ├── CreateScreen.kt
│   │   │   ├── SettingsSubPage.kt    # Reusable settings UI
│   │   │   └── *_SettingsScreen.kt   # 11 sub-settings
│   │   └── theme/
│   └── viewmodel/
│       └── YouTubeViewModel.kt       # Main ViewModel
├── src/main/res/
│   ├── drawable/
│   │   ├── ic_launcher_foreground.xml # YouTube-style play icon
│   │   └── ic_launcher_background.xml # Red background
│   └── mipmap-*/                     # Launcher icons
└── build.gradle.kts
```

---

## ⚙️ Configuration

Settings stored via DataStore:

| Setting | Values | Default |
|---------|--------|---------|
| Playback Speed | 0.25x – 5x | 1x (Normal) |
| Video Quality | Auto, 144p–1080p | Auto |
| Data Saver | On/Off | Off |
| Autoplay | On/Off | On |
| Dark Theme | On/Off | On |
| Save History | On/Off | On |
| Captions | Off / Language | Off |
| Loop | On/Off | Off |

---

## 🖼️ App Icon

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="https://img.shields.io/badge/🔴-FF0000?style=for-the-badge&label=Background&labelColor=FF0000" alt="Red BG"><br>
        <strong>Red (#FF0000)</strong>
      </td>
      <td align="center">
        <img src="https://img.shields.io/badge/▶-FFFFFF?style=for-the-badge&label=Foreground&labelColor=FFFFFF" alt="White Play"><br>
        <strong>Play Triangle</strong>
      </td>
    </tr>
  </table>
  <p><em>YouTube-style adaptive icon for API 26+</em></p>
</div>

---

## 👨‍💻 Developer

<div align="center">
  <br>
  <table>
    <tr>
      <td align="center" width="200">
        <img src="https://avatars.githubusercontent.com/u/191073266?v=4" width="120" style="border-radius: 50%; border: 3px solid #FF0000;" alt="Codex Skiller"><br><br>
        <strong style="font-size: 18px;">Codex Skiller</strong><br>
        <span style="color: #888;">Android Developer & Creator</span>
      </td>
    </tr>
  </table>
  <br><br>
  <table>
    <tr>
      <td align="center">
        <a href="https://github.com/codexskills">
          <img src="https://img.shields.io/badge/🐙_@codexskills-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub">
        </a>
      </td>
      <td align="center">
        <a href="https://t.me/codex_update">
          <img src="https://img.shields.io/badge/📢_@codex_update-0088CC?style=for-the-badge&logo=telegram&logoColor=white" alt="Updates">
        </a>
      </td>
      <td align="center">
        <a href="https://t.me/codexskills">
          <img src="https://img.shields.io/badge/💬_@codexskills-0088CC?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram">
        </a>
      </td>
      <td align="center">
        <a href="http://187.127.175.206:8080/">
          <img src="https://img.shields.io/badge/🌐_Portfolio-FF0000?style=for-the-badge&logo=google-chrome&logoColor=white" alt="Portfolio">
        </a>
      </td>
    </tr>
  </table>
  <br>
</div>

---

## 📜 License

```
This project is for educational purposes only.
YouTube is a trademark of Google LLC.
InnerTube API is unofficial and may break at any time.
```

<div align="center">
  <br><br>
  <sub>
    Built with ❤️ by <a href="https://github.com/codexskills"><strong>Codex Skiller</strong></a> •
    Join <a href="https://t.me/codex_update"><strong>@codex_update</strong></a> for latest releases
  </sub>
  <br><br>
  <img src="https://capsule-render.vercel.app/api?type=waving&color=ff0000&height=100&section=footer&text=⭐%20Star%20if%20you%20like%20it!&fontSize=20&fontColor=fff" width="100%">
</div>
