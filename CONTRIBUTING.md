# Contributing to KittyTune ( ◡‿◡ *)

Thanks for taking the time to help out. KittyTune is a community-driven project, and whether you're fixing a bug, suggesting a feature, or translating the app, I appreciate it.

### Getting Started
To get the project running on your machine:
1. **Clone the repo**: `git clone https://github.com/alan7383/kittytune.git`
2. **Setup Environment**: You'll need **JDK 21** and a recent version of Android Studio. 
3. **Build**: Run `./gradlew assembleDebug` or simply open the project in Android Studio and let Gradle sync.

### Submitting a Change
1. **Branching**: Create a new branch for your change (e.g., `feature/new-effect` or `fix/crash-on-search`).
2. **Commit Messages**: We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification (e.g., `feat:`, `fix:`, `perf:`, `refactor:`, `chore:`). These are used by `git-cliff` to automatically generate release changelogs.
3. **Pull Requests**: Open a PR with a brief summary of what you've adjusted and why. If it's a UI change, a screenshot or recording in the PR description helps immensely.

### Code Guidelines
- **UI Architecture**: Everything is built with **Jetpack Compose**. Avoid adding legacy XML views.
- **Pattern**: We follow a standard **MVVM** pattern. Keep your business logic in ViewModels and out of UI composables.
- **Responsive Layouts**: Ensure UI components adapt gracefully to both phones (portrait & landscape) and tablets using `rememberWindowSizeInfo()`.
- **Aesthetic**: Try to match the existing "Material 3 Expressive" vibe. Think fluid transitions, organic spring animations, and dynamic colors.
- **Privacy**: Never add tracking, analytics, or required logins. KittyTune is designed to stay anonymous and private.

### Translations ♬
Translating the app is the best way to help out. We use **Crowdin** to manage translations, so you don't need to touch any code.

1. **Visit our project**: [KittyTune on Crowdin](https://crowdin.com/project/kittytune)
2. **Translate**: Pick your language and start typing. 
3. **Sync**: Once translations are approved, they are automatically merged into the app via GitHub.

*If your language isn't listed, just ask in the GitHub Discussions to have it added.*

**Current languages & maintainers:**
- **English & French**: [alananasss](https://github.com/alan7383)
- **Hungarian**: mattdotcat
- **Russian**: Community contributors

### Reporting Issues
If you find a bug, please open an issue with:
- A clear description of the problem.
- Steps to reproduce it.
- Your device model and Android version.
- Logcat output if you have it.

---
*Happy coding! ♬ ( ˘ ɜ˘)*
