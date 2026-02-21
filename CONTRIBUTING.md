    # Contributing to KittyTune ( ◡‿◡ *)
    
    Thank you for your interest in KittyTune! We welcome community contributions from everyone, whether you are a developer, a translator, or a designer.
    
    ### ★ The Golden Rule
    **Respect the cat spirit.** We are building this for fun and utility. Please be kind, supportive, and keep the environment welcoming for everyone.
    
    ---
    
    ## How to Build the Project
    To compile KittyTune, you will need the latest version of **Android Studio** installed.
    
    1. **Clone the repository**
       ```bash
       git clone https://github.com/alan7383/kittytune.git
       ```
    2. **Set JDK version**
       Ensure your project is set to use **JDK 17**.
    3. **Build via Terminal**
       ```bash
       ./gradlew assembleDebug
       ```
       The APK will be generated in `app/build/outputs/apk/debug/`.
    
    ---
    
    ## Adding Translations ♬
    We want KittyTune to be accessible to everyone. The easiest way to add a new language is through the **Translations Editor** in Android Studio:
    
    1. Open the project and navigate to `app/src/main/res/values/strings.xml`.
    2. Click **"Open editor"** in the top-right corner of the window.
    3. Click the **Globe icon** (Add Locale) and select the language you wish to add.
    4. Fill in the columns for your language in the grid.
    5. Save your changes and open a Pull Request.
    
    **Current contributors:**
    • **French & English:** [alananasss](https://github.com/alan7383)
    • **Hungarian:** [mattdotcat](https://t.me/b37246)
    
    ★ **Want to see your name here?** Help us translate KittyTune into your native language!
    
    ---
    
    ## Coding Guidelines
    • **UI:** We use **Jetpack Compose** with Material 3 Expressive. Please ensure new UI components remain consistent with the existing design.
    • **Architecture:** We follow the **MVVM** pattern. Keep logic out of Composable functions.
    • **Commits:** Use clear and descriptive commit messages (e.g., `feat: add sleep timer` or `fix: layout issue on small screens`).
    
    ---
    
    ## Need Help?
    If you have questions or suggestions, feel free to open a **Discussion** on GitHub or join our Telegram.
    
    *Happy coding!* ♬ ( ˘ ɜ˘)


