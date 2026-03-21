

<img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/app/src/main/ic_launcher-playstore.png" alt="KittyTune Logo" width="150" align="right"/>

<div align="center">

# KittyTune ( ◡‿◡ *)

**SoundCloud, YouTube & local files, one player, no accounts, no ads.**

<br/>

[![Download](https://img.shields.io/badge/DOWNLOAD_APK-black?style=for-the-badge&logo=android&logoColor=white)](https://github.com/alan7383/kittytune/releases/latest)
[![Website](https://img.shields.io/badge/WEBSITE-d0bcff?style=for-the-badge)](https://alan7383.github.io/kittytune)

<br/>

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material_3_Expressive-4285F4?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Android Auto](https://img.shields.io/badge/Android_Auto-3DDC84?style=flat-square&logo=androidauto&logoColor=white)](https://developer.android.com/training/cars/media)
[![License](https://img.shields.io/badge/GPL%20v3-white.svg?style=flat-square)](LICENSE)

</div>

<br/>

---

## What is this

KittyTune is a music player built with **Jetpack Compose** and **Kotlin** that unifies SoundCloud, YouTube, and local files into a single library. No official APIs, no accounts required, no ads. It handles token management silently in the background so playback never interrupts, and if a track is restricted on one platform it falls back to the other automatically.

It also packs desktop-grade audio effects, 8D, reverb, nightcore, bass boost, right on your phone. ( ˘ ɜ˘) ♬

---

## Features

<table>
<tr>
<td width="50%" valign="top">

### Playback
▸ Unified library across SoundCloud, YouTube & local files
<br/>▸ Smart platform fallback for restricted tracks
<br/>▸ Gapless playback
<br/>▸ Synced lyrics
<br/>▸ Android Auto support
<br/>▸ Lock screen & notification controls
<br/>▸ Home screen widgets

</td>
<td width="50%" valign="top">

### Audio DSP
▸ 8D spatial audio
<br/>▸ Reverb & echo
<br/>▸ Nightcore / Vaporwave
<br/>▸ Pitch shifting
<br/>▸ Bass boost
<br/>▸ Rain ambience

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Search & Discovery
▸ Cross-platform search (SoundCloud + YouTube simultaneously)
<br/>▸ Listening stats & achievements
<br/>▸ Playlist & history backup to JSON

</td>
<td width="50%" valign="top">

### Privacy
▸ Ghost mode — full functionality, zero login (¬‿¬)
<br/>▸ No tracking, no telemetry
<br/>▸ No official API dependencies

</td>
</tr>
</table>

---

## Screenshots

| Home | Player | Lyrics | Profile |
|:---:|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/home.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/player.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/lyrics.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/profile.jpg" width="200"/> |

---

## Tech Stack

Architecture follows **MVVM** with modern Android conventions.

| Layer | Stack |
|:---|:---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3 Expressive |
| **Audio** | Media3 / ExoPlayer |
| **Database** | Room |
| **Network** | Retrofit, OkHttp |
| **Images** | Coil |
| **Extractors** | NewPipeExtractor |

---

## Install

KittyTune isn't on the Play Store. Grab it from here.

### APK
1. Go to [**Releases**](https://github.com/alan7383/kittytune/releases/latest)
2. Download `app-release.apk`
3. Allow unknown sources if prompted, install

### Build from source
```bash
git clone https://github.com/alan7383/kittytune.git
cd kittytune
./gradlew assembleDebug
```

---

## Credits

- **[NewPipe Team](https://github.com/TeamNewPipe/NewPipeExtractor)** - YouTube extraction
- **[ZionHuang / InnerTune](https://github.com/z-huang/InnerTune)** - YTM research
- **[LrcLib](https://lrclib.net)** — Lyrics provider

Full list in the in-app About section.

### Translations

| | Language | Contributor |
|:---|:---|:---|
| 🇫🇷 🇬🇧 | French, English | [alananasss](https://github.com/alan7383) |
| 🇭🇺 | Hungarian | mattdotcat |

Want to add your language? Open a PR.

---

## License

[GNU General Public License v3.0](LICENSE) - educational and personal use.

> [!WARNING]
> **Not affiliated with SoundCloud or YouTube.** This is an independent project. Use at your own risk.

---

<div align="center">

**Made by [alananasss](https://github.com/alan7383)** (=^･ω･^=)

<sub>If you like the project, leave a ★</sub>

</div>