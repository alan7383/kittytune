<img src="https://github.com/user-attachments/assets/af2b1f31-3086-4d22-994d-d392b0d1bbb2" alt="KittyTune Logo" align="right"/>

<div align="center">

# KittyTune ( ◡‿◡ *)

**A sophisticated, privacy-focused hybrid music client for Android.**
Seamlessly unifying SoundCloud, YouTube, and local storage into a high-fidelity audio experience.

<br/>

[![Download](https://img.shields.io/badge/DOWNLOAD_APK-black?style=for-the-badge&logo=android&logoColor=white)](https://github.com/alan7383/kittytune/releases/latest)
[![Website](https://img.shields.io/badge/WEBSITE-d0bcff?style=for-the-badge)](https://alan7383.github.io/kittytune)

<br/>

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/UI-Material_3_Expressive-4285F4?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Android Auto](https://img.shields.io/badge/Support-Android_Auto-3DDC84?style=flat-square&logo=androidauto&logoColor=white)](https://developer.android.com/training/cars/media)
[![Build](https://img.shields.io/github/actions/workflow/status/alan7383/kittytune/android.yml?style=flat-square&label=Build)](https://github.com/alan7383/kittytune/actions/workflows/android.yml)
[![License](https://img.shields.io/github/license/alan7383/kittytune?style=flat-square&color=white)](LICENSE)

</div>

<br/>

<details>
  <summary align="center"><b>Table of Contents / Navigation</b> ▽</summary>
  <br/>
  <div align="center">
    <a href="#about-the-project">About</a> &nbsp;•&nbsp;
    <a href="#features">Features</a> &nbsp;•&nbsp;
    <a href="#screenshots">Screenshots</a> &nbsp;•&nbsp;
    <a href="#tech-stack--architecture">Tech Stack</a> &nbsp;•&nbsp;
    <a href="#installation">Installation</a> &nbsp;•&nbsp;
    <a href="#credits--acknowledgements">Credits</a> &nbsp;•&nbsp;
    <a href="#license">License</a>
  </div>
</details>

<br/>

---

## About The Project

**KittyTune** is not just another music player. It is an engineering attempt to break down the barriers between streaming platforms and local libraries. Built entirely with **Jetpack Compose** and **Kotlin**, it leverages a custom-built audio pipeline to deliver features typically reserved for desktop DAWs (Digital Audio Workstations) directly on your mobile device.

Unlike standard clients, KittyTune operates without official APIs, using advanced scraping and session interception techniques to provide a robust, ad-free, and account-optional experience.

### Key Differentiators ( ˘ ɜ˘) ♬

★ **Hybrid Token Generation:** Uses a background headless WebView to dynamically harvest valid OAuth tokens and Client IDs, ensuring long-term API stability.
<br/>
★ **Custom DSP Engine:** Implements low-level `AudioProcessor` chains for real-time effects (8D, Reverb, Parametric EQ) directly on the PCM byte stream.
<br/>
★ **Universal Search:** Queries multiple sources (SoundCloud V2 API, InnerTube/YouTube) concurrently to find the best audio stream available.

---

## Features

<table>
  <tr>
    <td width="50%" valign="top">

### Core Experience

▸ **Unified Library**
Mix SoundCloud tracks, YouTube videos, and local MP3/FLAC files in the same playlist seamlessly.

▸ **Smart Fallback**
Automatically resolves restricted SoundCloud Go+ tracks via YouTube/NewPipe or Invidious instances without user intervention.

▸ **Synced Lyrics**
Real-time synchronized lyrics fetching via **LrcLib**, with manual search and time-synced scrolling.

▸ **Gapless Playback**
Powered by `androidx.media3` with precise buffer management.

</td>
<td width="50%" valign="top">

### Ecosystem Integration

▸ **Android Auto**
Full implementation of `MediaLibraryService` for safe driving with car display support.

▸ **Home Screen Widgets**
Responsive widgets built with **Jetpack Glance**, strictly following Material 3 guidelines.

▸ **System Controls**
Native integration with Android 13+ media notifications and lock screen controls.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Audio Engineering

▸ **Parametric DSP Effects**
Nightcore, Vaporwave, and precise pitch control (0.5x - 2.0x).

▸ **8D Audio & Reverb**
Auto-panning algorithm based on sinusoidal oscillation and custom delay-line implementation for spatial ambience.

▸ **Bass Boost & Rain Mode**
Low-shelf filter implementation and ambient noise overlay for focus/study.

</td>
<td width="50%" valign="top">

### Advanced Tools

▸ **Ghost Mode** (¬‿¬)
Full feature set access without login (local database persistence).

▸ **Backup & Restore**
JSON-based export of playlists, listening history, and settings.

▸ **Gamification & Stats**
Extensive achievement system tracking listening habits and streaks.

</td>
  </tr>
</table>

---

## Screenshots

| Home & Discovery | Immersive Player | Synced Lyrics | User Profile |
|:---:|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/home.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/player.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/lyrics.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/profile.jpg" width="200"/> |

---

## Tech Stack & Architecture

The project follows a **Modern Android Architecture** (MVVM) principles, emphasizing separation of concerns and reactive data flow.

| Layer | Library / Technology | Purpose |
|:---|:---|:---|
| **Language** | **Kotlin** | 100% codebase coverage. |
| **UI Framework** | **Jetpack Compose** | Material 3 Expressive Design System. |
| **Navigation** | **Compose Navigation** | Single Activity Architecture with deep linking. |
| **Audio Core** | **Media3 (ExoPlayer)** | Playback, DSP, and Session Management. |
| **Database** | **Room (SQLite)** | Offline caching, history, and relational data. |
| **Network** | **Retrofit + OkHttp** | REST API communication & Interceptors. |
| **Image Loading** | **Coil** | Async image loading with memory caching and blur effects. |
| **Extractors** | **NewPipeExtractor** | YouTube stream resolution logic. |
| **Widgets** | **Jetpack Glance** | Declarative UI for App Widgets. |
| **Concurrency** | **Coroutines & Flow** | Asynchronous operations and state management. |

---

## Installation

**Important Note:** KittyTune utilizes internal APIs and scraping methods not compliant with the Google Play Store policies. It is distributed exclusively via GitHub.

### Standard
1. Go to the [**Releases Page**](https://github.com/alan7383/kittytune/releases).
2. Download the latest `app-release.apk`.
3. Install on your Android device (Allow installation from unknown sources).

### For Developers
```bash
# Clone the repository
git clone https://github.com/alan7383/kittytune.git

# Open in Android Studio
# Sync Gradle Project

# Build Debug APK
./gradlew assembleDebug
```

---

## Credits & Acknowledgements

This project stands on the shoulders of giants. Special thanks to the open-source community:

*   **[NewPipe Team](https://github.com/TeamNewPipe/NewPipeExtractor):** For the incredible extraction library that powers the YouTube fallback mechanism.
*   **[ZionHuang (InnerTube)](https://github.com/z-huang/InnerTune):** For research on YouTube Music internal APIs.
*   **[LrcLib](https://lrclib.net):** For the free and open synchronized lyrics API.
*   **[mp3agic](https://github.com/mpatric/mp3agic):** For java-based ID3 tag manipulation.
*   **[Accompanist](https://google.github.io/accompanist/):** For supplementary Jetpack Compose libraries.
*   **[AboutLibraries](https://github.com/mikepenz/AboutLibraries):** For license management.

### Translations (Thank you! <3)

*   🇫🇷 **French & English:** [alananasss](https://github.com/alan7383)
*   🇭🇺 **Hungarian:** [mattdotcat](https://t.me/b37246)

🌍 **Want to appear here?** Help us translate KittyTune into your language! Feel free to open a Pull Request or contact us.

---

## License

KittyTune is strictly for educational and personal use.

Distributed under the [GNU General Public License v3.0](LICENSE). See [LICENSE](LICENSE) for more information.

> [!WARNING]
> **Legal Disclaimer**
>
> This app is **not affiliated with, endorsed, or sponsored** by SoundCloud or YouTube.
> It operates as a client-side wrapper and does not host any copyrighted content.
> **Use at your own discretion.**

---

<div align="center">

**Crafted with obsession by [alananasss](https://github.com/alan7383)** (=^･ω･^=)

<sub>If you find this code useful, please consider starring the repository ★</sub>

</div>


