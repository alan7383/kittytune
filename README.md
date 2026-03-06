<img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/app/src/main/ic_launcher-playstore.png" alt="KittyTune Logo" width="150" align="right"/>

<div align="center">

# KittyTune ( ◡‿◡ *)

**Just a simple but powerful music player for Android.**
It's my take on bringing together SoundCloud, YouTube, and your local files into one place.

<br/>

[![Download](https://img.shields.io/badge/DOWNLOAD_APK-black?style=for-the-badge&logo=android&logoColor=white)](https://github.com/alan7383/kittytune/releases/latest)
[![Website](https://img.shields.io/badge/WEBSITE-d0bcff?style=for-the-badge)](https://alan7383.github.io/kittytune)

<br/>

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/UI-Material_3_Expressive-4285F4?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Android Auto](https://img.shields.io/badge/Support-Android_Auto-3DDC84?style=flat-square&logo=androidauto&logoColor=white)](https://developer.android.com/training/cars/media)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg?style=flat-square&color=white)](LICENSE)

</div>

<br/>

<details>
  <summary align="center"><b>Table of Contents / Navigation</b> ▽</summary>
  <br/>
  <div align="center">
    <a href="#about-the-project">About</a> &nbsp;•&nbsp;
    <a href="#cool-stuff">Features</a> &nbsp;•&nbsp;
    <a href="#how-it-looks">Screenshots</a> &nbsp;•&nbsp;
    <a href="#how-its-built">Tech Stack</a> &nbsp;•&nbsp;
    <a href="#get-it-running">Installation</a> &nbsp;•&nbsp;
    <a href="#credits">Credits</a> &nbsp;•&nbsp;
    <a href="#license">License</a>
  </div>
</details>

<br/>

---

## About The Project

**KittyTune** isn't just another boring music player. It's basically my attempt at breaking down the walls between streaming platforms and your own music collection. I built it with **Jetpack Compose** (which I love) and **Kotlin** because I wanted something that felt smooth and had all those cool desktop-like audio effects on a phone.

It doesn't use any official APIs, so it's ad-free and you don't even need an account. Just open it and play some music. ( ˘ ɜ˘) ♬

### Some cool bits:

★ **Smart Token Stuff:** It handles all the background login stuff for you so the music never stops.
<br/>
★ **Audio Effects:** You can mess around with 8D audio, reverb, and a bunch of other effects in real-time.
<br/>
★ **Everything Search:** It searches SoundCloud and YouTube at the same time to find whatever you're looking for.

---

## Cool Stuff

<table>
  <tr>
    <td width="50%" valign="top">

### The basics

▸ **Unified Library**
Mix your SoundCloud tracks, YouTube videos, and local MP3s in the same playlist.

▸ **Smart Fallback**
If a song is restricted on SoundCloud, it'll try to find it on YouTube automatically. No big deal.

▸ **Lyrics**
Synced lyrics so you can sing along.

▸ **Gapless Playback**
No annoying pauses between tracks.

</td>
<td width="50%" valign="top">

### Extra features

▸ **Android Auto**
Works in your car too!

▸ **Widgets**
Nice-looking widgets for your home screen.

▸ **System Controls**
Standard media controls on your lock screen and notifications.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Audio fun

▸ **DSP Effects**
Nightcore, Vaporwave, and you can change the pitch too.

▸ **8D & Reverb**
Spatial audio and some nice echoey vibes.

▸ **Bass Boost & Rain**
Boost the bass or add some rain sounds for when you're studying.

</td>
<td width="50%" valign="top">

### Tools

▸ **Ghost Mode** (¬‿¬)
Use everything without ever having to log in.

▸ **Backup**
Export your playlists and history to JSON.

▸ **Stats**
See your listening habits and achievements. 

</td>
  </tr>
</table>

---

## How it looks

| Home | Player | Lyrics | Profile |
|:---:|:---:|:---:|:---:|
| <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/home.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/player.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/lyrics.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/screenshots/profile.jpg" width="200"/> |

---

## How it's built

I tried to keep things clean using **MVVM** and modern Android standards. Here's a quick look at what I used:

| Layer | Tools | Why? |
|:---|:---|:---|
| **Language** | **Kotlin** | Because it's great. |
| **UI** | **Jetpack Compose** | To make it look and feel modern. |
| **Audio** | **Media3 (ExoPlayer)** | The heavy lifting for playback. |
| **Database** | **Room** | For caching and saving your history. |
| **Network** | **Retrofit + OkHttp** | Talking to the internet. |
| **Images** | **Coil** | Loading album art and stuff. |
| **Extractors** | **NewPipeExtractor** | Getting those YouTube links. |

---

## Get it running

**Note:** Since KittyTune does some "unofficial" things with APIs, you won't find it on the Play Store. You gotta get it here.

### Just want to use it?
1. Check the [**Releases**](https://github.com/alan7383/kittytune/releases).
2. Grab the `app-release.apk`.
3. Install it (and allow "unknown sources" if your phone asks).

### For devs
```bash
# Clone it
git clone https://github.com/alan7383/kittytune.git

# Open it in Android Studio and let Gradle do its thing.

# Build it
./gradlew assembleDebug
```

---

## Credits

This project wouldn't exist without these libraries and the people behind them:

*   **[NewPipe Team](https://github.com/TeamNewPipe/NewPipeExtractor):** For the magic that makes YouTube work.
*   **[ZionHuang (InnerTube)](https://github.com/z-huang/InnerTune):** For the YTM research.
*   **[LrcLib](https://lrclib.net):** For the lyrics.
*   **And more...** (Check the in-app "About" for the full list).

### Translations (Thank you! <3)

*   🇫🇷 **French & English:** [alananasss](https://github.com/alan7383)
*   🇭🇺 **Hungarian:** mattdotcat

🌍 **Want to help?** If you want to see KittyTune in your language, feel free to open a PR!

---

## License

KittyTune is strictly for educational and personal use.

It's under the [GNU General Public License v3.0](LICENSE). 

> [!WARNING]
> **Important!**
>
> This app is **not affiliated with** SoundCloud or YouTube. 
> It's just a wrapper I made. 
> **Use it at your own risk.**

---

<div align="center">

**Crafted with obsession by [alananasss](https://github.com/alan7383)** (=^･ω･^=)

<sub>If you like the project, maybe leave a star ★</sub>

</div>


