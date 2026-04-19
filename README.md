<img src="https://raw.githubusercontent.com/alan7383/kittytune/refs/heads/main/app/src/main/ic_launcher-playstore.png" alt="KittyTune Logo" width="150" align="right"/>

# KittyTune ( ◡‿◡ *)

**SoundCloud, YouTube, and local files in one place. No ads, no tracking, and zero account dependencies.**

I built KittyTune because I was tired of jumping between different apps just to listen to my own library. It pulls SoundCloud likes, YouTube playlists, and local MP3s into a single, unified list. If a song disappears from one platform, the app automatically finds a backup on the other so your music never stops.

### The Core Experience
Most players are just wrappers. I wanted KittyTune to be a standalone machine that handles the mess of modern streaming for you.
- **Unified Library**: SoundCloud, YouTube, and local files live in the same UI. No more switching.
- **Smart Fallback**: If a track is geo-blocked or deleted, the app resolves a backup stream from a different platform instantly.
- **Ghost Mode**: You don't need a login. I use scraping and direct extraction to keep your data private and avoid official API limits.
- **Gapless flow**: Background token refreshes and adjustable crossfade (up to 12s) keep the playback moving.

### Serious Audio Engineering
Usually, you need a desktop DAW for these effects. I've baked a custom DSP pipeline directly into the engine.
- **DSP Suite**: Real-time 8D spatial audio, reverb with adjustable decay, and heavy bass boost.
- **Nightcore & Vaporwave**: You can shift pitch and speed independently to change the vibe of any track.
- **Rain Ambience**: There's a toggleable rain overlay that plays alongside your music when you need to focus.
- **Precision**: Fine-grained speed sliders and precise seek controls for the perfectionists.

### Progression & Stats
I wanted the app to feel alive, like an RPG for your ears. The more you listen, the more your profile grows.
- **Achievements**: Unlock over 40 badges (like "Vampire", "Night Owl", or "Ghost") and earn XP for your listener level.
- **Real Stats**: See your play counts and genre breakdowns whenever you want. No need to wait for a yearly "wrapped" event.
- **Discord RPC**: Automatically show what you're listening to on your Discord profile with rich media status.
- **Backup**: Export your entire library, history, and stats to a JSON file whenever you need to move device.

### Your UI, Your Way
KittyTune uses the latest Material 3 Expressive guidelines. It's fluid, translucent, and scales to whatever style you prefer.
- **Dynamic Themes**: Full support for Monet (Material You), custom primary colors, and high-contrast OLED black modes.
- **Variable Fonts**: You can tweak font weight, width, and slant directly in the settings.
- **Adaptive**: Built-in support for home screen widgets and Android Auto.

### Under the Hood
- **Language**: 100% Kotlin.
- **Interface**: Jetpack Compose (MVVM).
- **Audio Core**: Media3 / ExoPlayer.
- **Scrapers**: NewPipeExtractor for YouTube and custom SoundCloud resolvers.
- **Persistence**: Room database for fast local metadata access.

### Installation
You won't find this on the Play Store. Download the latest APK from the [releases page](https://github.com/alan7383/kittytune/releases/latest) and install it manually. 

To build it yourself:
```bash
git clone https://github.com/alan7383/kittytune.git
./gradlew assembleDebug
```

### Huge thanks to
- **NewPipe Team** for the extraction logic.
- **InnerTune** for the research on YouTube Music search.
- **LrcLib** for the synced lyrics API.
- **mattdotcat** for the Hungarian translation.

*Made by [alananasss](https://github.com/alan7383). If you find this useful, drop a ★.*