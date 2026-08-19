# contributing to kittytune (=^･ω･^=)

Thanks for checking out KittyTune! Whether you want to fix a bug, add a feature, tweak audio processing, or translate the app, contributions are always welcome.

---

### > setup

1. Clone the repo:
   ```bash
   git clone https://github.com/alan7383/kittytune.git
   cd kittytune
   ```
2. What you need:
   - JDK 21
   - Android Studio (Ladybug / Meerkat or newer)
   - Android NDK & CMake (via SDK Manager in Android Studio)
3. Build:
   ```bash
   ./gradlew assembleDebug
   ```
   or run it directly from Android Studio.

---

### * project structure

KittyTune is organized into a few modules:

- `:app`: Jetpack Compose UI, ViewModels, Room DB, ExoPlayer / Media3, and C++ audio DSP (`app/src/main/cpp`)
- `:innertube`: YouTube search and stream fallback
- `:shazamkit`: song recognition via Shazam
- `:lrclib` & `:kugou`: synchronized and plain lyrics
- `:kizzy`: Discord Rich Presence

---

### $ code & pull requests

A few simple guidelines:

- Everything in UI is Jetpack Compose + Material 3 (no XML views)
- MVVM pattern: keep logic in ViewModels and UI in composables
- Make sure layouts look good on both phones and tablets with `rememberWindowSizeInfo()`
- Zero telemetry or analytics, keep it private
- Follow [conventional commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, etc.) for git-cliff changelogs

When opening a PR, just add a short summary of what you did. If you touched the UI, adding a screenshot or recording in the PR description helps a lot!

---

### ♬ translations

Translations are managed via **Crowdin**, so no code changes needed:

1. Go to [KittyTune on Crowdin](https://crowdin.com/project/kittytune)
2. Pick your language (or ask in discussions to add one)
3. Translate at your own pace, approved strings get synced automatically

---

### ! issues & bugs

If you run into a bug or crash:
- Check if it hasn't been reported already
- Open an issue with your device model, Android version, steps to reproduce, and logcat if possible

---

<p align="center">
  thanks for contributing! (=｀ω´=)
</p>
