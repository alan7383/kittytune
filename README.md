# kittytune ( ◡‿◡ *)

hi everyone! welcome to **kittytune**, a cute and powerful music player for android that i've been working on. it's my little passion project to make listening to music more fun.

i just released the very first version! you can grab the **apk** right now in the [releases](https://github.com/alananasss/KittyTune/releases) tab on the right side of this page. go install it and tell me what you think! (ﾉ◕ヮ◕)ﾉ*:･ﾟ✧

### what can you do with it? ( o . o )

i didn't want just a boring player, so i packed it with features i really wanted to use myself:

*   **soundcloud & local music:** you can stream millions of songs from soundcloud (log in to sync your likes or just use the **guest mode** if you want to stay anonymous) AND play your own mp3 files stored on your phone. best of both worlds!
*   **level up your listening:** this is my favorite part... i added a gamification system! ( > ᴗ < ) listening to music gives you **XP**, levels you up, and unlocks cool **achievements**. try to keep a daily streak or find the secret badges!
*   **audio wizardry:** want to listen to a song in **nightcore** style? just speed it up! want to feel like you are in a concert? turn on the **8d audio** or the **reverb** effect. there is also a bass boost for the heavy drops.
*   **offline mode:** you can download tracks to your device so you can keep listening even when you have no wifi.
*   **lyrics:** the app automatically finds lyrics for you, or uses the ones inside your files.
*   **it looks good:** since it uses material you, the whole app changes colors to match your phone wallpaper. aesthetic!

### how it works (under the hood) (⌐■_■)

if you are a dev curious about the code, here is a deeper dive into how i built this. the project is written 100% in [kotlin](https://kotlinlang.org/) and follows the **mvvm** architecture to keep things clean. i built the ui entirely with [jetpack compose](https://developer.android.com/jetpack/compose) because i wanted the interface to be super smooth and modern without dealing with old xml layouts.

for the audio engine, i am using [media3 (exoplayer)](https://developer.android.com/media/media3) as the foundation, but i had to get creative to make the effects work. i wrote custom audio processors (you can peek at [`ui/player/audio/`](app/src/main/java/com/alananasss/kittytune/ui/player/audio/)) where i manipulate the `ByteBuffer` directly to create the **8d auto-pan**, the biquad filters for the bass boost, and the delay buffer for the reverb effect.

connecting to soundcloud's api is a bit tricky, so i built a `SessionManager` that runs a hidden, background webview. it basically acts like a real user to scrape a valid `client_id` and authentication tokens, which means the app keeps working even if the keys change ( ¬‿¬). for the lyrics, i implemented a client for the [lrclib](https://lrclib.net/) api to fetch synced lines, but it also falls back to reading `ID3` tags from your local files using [mp3agic](https://github.com/mpatric/mp3agic). finally, everything from your downloaded tracks and playlists to your xp progression is stored safely on your device using a [room database](https://developer.android.com/training/data-storage/room).

### contributing & bugs ( ˙꒳​˙ )

since this is the very first release, there might be some tiny bugs hiding around.

if you find one, or if you have a crazy idea for a new feature, **please tell me**! open an issue or start a discussion in the tab above. i am totally open to suggestions and i'd love to fix things to make the app better for everyone.

### want to help translate? ( ˘ ³˘)♥

i want kittytune to be available for everyone! right now we support english, french, and hungarian (huge thanks to mattdotcat for that!).

if you want to see the app in your language, it's super easy to help. just look into the [`res/values`](app/src/main/res/values) folder. you can copy the `strings.xml` file, translate the lines inside, and open a pull request. i would be super happy to add your name to the credits!

---

thanks for stopping by. happy listening!
~ alan (´｡• ᵕ •｡`)
