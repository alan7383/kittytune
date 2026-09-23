# DJ Flow — Engine API

Integration reference for the Non-Stop / DJ Flow engine ([#28](https://github.com/alan7383/kittytune/issues/28)).

The engine is UI-agnostic. It exposes **one `StateFlow` to read** and **one controller to call**.
It never touches Compose, navigation, or any player layout — everything below is safe to build
any UI on top of, classic / modern / soundcloud / pixel alike.

```
UI  ──reads──▶  DjFlowController.flowState : StateFlow<DjFlowState>
UI  ──calls──▶  DjFlowController.<action>()
                        │
                        ▼
              AutomixManager · BeatAnalyzer · DownbeatTracker · BeatPhaseLock
                        │
                        ▼
              MusicManager.crossfadeToMediaItem(...)   ← engine drives this, not the UI
```

`DjFlowController` is owned by `PlayerViewModel` (`viewModel.djFlowController`). Do not construct
your own — the engine holds analysis caches and background jobs tied to the player lifecycle.

---

## 1. Reading state

```kotlin
val dj by viewModel.djFlowController.flowState.collectAsStateWithLifecycle()

if (dj.isActive) {
    Text("${dj.currentBpm.roundToInt()} BPM · ${dj.currentKey?.code ?: "—"}")
}
```

`DjFlowState` is a plain immutable data class. Every field is safe to read on the main thread and
updates land as a whole new instance, so Compose recomposes correctly without extra keys.

### 1.1 What's playing

| Field | Type | Notes |
|---|---|---|
| `isActive` | `Boolean` | Master switch. Everything below is meaningless when false. |
| `currentBpm` | `Float` | `0f` until analysis lands — show a placeholder, not `0`. |
| `currentKey` | `CamelotKey?` | `null` on percussive / atonal material. `.code` → `"7B"`, `.standardName` → `"F Major"`. |
| `energyMode` | `EnergyMode` | `BUILD_UP` / `HOLD` / `WIND_DOWN`, each with `displayName` + `description`. |

### 1.2 The next track and the transition

| Field | Type | Notes |
|---|---|---|
| `nextTrackMatch` | `TrackMatch?` | Top-scored candidate. `null` when the queue has nothing usable. |
| `suggestedMatches` | `List<TrackMatch>` | Ranked, best first. Up to 15. |
| `targetBpm` / `targetKey` | `Float` / `CamelotKey?` | Convenience mirrors of `nextTrackMatch`. |
| `transitionStyle` | `TransitionStyle` | Has `title`, `description`, `defaultPhraseBeats`. |
| `transitionPhase` | `TransitionPhase` | `IDLE` → `PREPARING` → `CROSSFADING`. |
| `transitionProgress` | `Float` | `0f..1f`, only meaningful while `CROSSFADING`. |

`transitionPhase` and `transitionProgress` are driven for autonomous transitions too — earlier
they stayed at `IDLE` for the whole autonomous fade, which also made the engine's own re-entry
guard useless and let three transitions stack inside 0.7 s.

### 1.3 Countdown — the numbers a DJ UI actually wants

| Field | Type | Notes |
|---|---|---|
| `beatsUntilTransition` | `Int?` | Counts down to the mix-out point. `null` when nothing is planned. |
| `barsUntilTransition` | `Int?` | Same, in bars. |
| `timeUntilTransitionMs` | `Long?` | Same, in wall time. |
| `plannedTriggerTimeMs` | `Long?` | Absolute position in the **outgoing** track where the fade starts. |
| `incomingDropPointMs` | `Long?` | Absolute position in the **incoming** track where it starts playing. |
| `plannedTransitionDurationMs` | `Long` | Fade length. Defaults to 8000. |

### 1.4 Live beat grid — for animation

Updated on every position tick. All of these count from a **classified downbeat**, so
`beatInBar == 1` is a real musical "One", not an arbitrary grid position.

| Field | Type | Notes |
|---|---|---|
| `beatInBar` | `Int` | `1..4` |
| `currentBar` | `Int` | Bar number from the start of the track. |
| `beatInPhrase` | `Int` | `1..16` |
| `barInPhrase` | `Int` | `1..4` |
| `isDownbeat` | `Boolean` | True on beat 1 of a bar. Good for a pulse animation. |
| `beatProgress` | `Float` | `0f..1f` through the current beat. Use this to drive smooth motion — it is continuous, `isDownbeat` is not. |

### 1.5 Toggles and stems

| Field | Type | Default |
|---|---|---|
| `isAutonomousEnabled` | `Boolean` | `true` — engine triggers transitions on its own. |
| `isConstantEnergyEnabled` | `Boolean` | `true` — no volume dip through the blend. |
| `isAutoReorderEnabled` | `Boolean` | `true` — engine may promote a better match up the queue. |
| `isLoopExtensionEnabled` / `loopBeats` | `Boolean` / `Int` | `true` / `8` |
| `isLoopActive` / `loopRangeMs` | `Boolean` / `Pair<Long,Long>?` | Live phrase loop. |
| `isInfiniteStreamEnabled` / `selectedCategory` | `Boolean` / `String` | Auto-refill the queue. |
| `isFetchingInfiniteCandidates` | `Boolean` | Show a spinner on the category chip. |
| `vocalStemLevel` / `drumStemLevel` / `bassStemLevel` | `Float` | `0f` = kill, `1f` = normal, `>1f` = boost. |
| `isAutoStemDropCutEnabled` | `Boolean` | `true` |
| `analyzedCandidateCount` / `totalCandidateCount` | `Int` | Analysis progress, e.g. `"7 / 15 analysed"`. |

### 1.6 Engine internals — do not build UI on these

They now live behind one field, `state.sync` (`DjSyncState`): `tempoRatio`, `phaseOffsetMs`,
`outgoingGrid`, `incomingGrid`. They exist so the crossfade can phase-lock the two decks and are
meaningless on screen. They change shape whenever the locking strategy does.

---

## 2. Calling the controller

Every method is main-thread safe, idempotent, and returns immediately — heavy work is dispatched
internally. None of them throw.

### 2.1 Master

```kotlin
fun enableDjMode(enabled: Boolean)   // persists to PlayerPreferences
fun toggleDjMode()
```

### 2.2 Modes and toggles

```kotlin
fun setEnergyMode(mode: EnergyMode)          // re-scores the queue
fun setAutonomousEnabled(enabled: Boolean)
fun setConstantEnergyEnabled(enabled: Boolean)
fun setAutoReorderEnabled(enabled: Boolean)
fun setAutoStemDropCutEnabled(enabled: Boolean)
```

### 2.3 Transitions

```kotlin
fun triggerTransition(targetTrack: Track? = null, crossfadeDurationMs: Long = 4000L)
fun cancelTransition()
```

`triggerTransition(null)` mixes into `nextTrackMatch`. Passing a `Track` mixes into that one and
promotes it in the queue.

> **Caveat:** a manually triggered transition is not prebuffered, so the incoming deck cannot be
> phase-locked by seeking and relies on speed correction instead. Autonomous transitions prebuffer
> ~8 s ahead and lock within a few milliseconds. Manual triggering is fine for a "mix now" button;
> it is measurably less tight than letting the engine choose the moment.

### 2.4 Loop

```kotlin
fun toggleCurrentPhraseLoop()
fun activatePhraseLoopAt(positionMs: Long)
fun clearPhraseLoop()
fun setLoopBeats(beats: Int)          // clamped to 2..32
fun setLoopExtensionEnabled(enabled: Boolean)
```

### 2.5 Stems

```kotlin
fun setStemLevels(vocal: Float, drum: Float, bass: Float)
fun setVocalStem(level: Float)   // 0f instrumental · 1f normal · 2f acapella
fun setDrumStem(level: Float)    // 0f no beat   · 1f normal · 1.5f punch
fun setBassStem(level: Float)    // 0f bass kill · 1f normal · 1.5f boost
fun resetStems()
```

Applied live to both decks. During a transition the engine owns the bass level and will overwrite
whatever the UI set, restoring it when the fade ends.

### 2.6 Queue

```kotlin
fun setInfiniteStreamEnabled(enabled: Boolean)
fun setSelectedCategory(category: String)
fun searchAndPopulateCategory(query: String, startFreshIfIdle: Boolean = true)
fun evaluateQueueAsync()             // force a re-score
```

### 2.7 Lifecycle hooks — already wired, don't call these

`onTrackChanged`, `onQueueUpdated`, `onPositionUpdated` are driven by `PlayerViewModel`.

---

## 3. Minimal integration

`DjFlowPanel` (`ui/player/dj/DjFlowPanel.kt`) is a ~200-line Material 3 reference consumer.
It reads only from `flowState` and calls only the methods above — no private access, no engine
internals — so it doubles as a compile-time check that the public surface is sufficient.

```kotlin
DjFlowPanel(
    state = dj,
    onToggleDj = viewModel.djFlowController::enableDjMode,
    onEnergyMode = viewModel.djFlowController::setEnergyMode,
    onMixNow = { viewModel.djFlowController.triggerTransition() },
)
```

The full `DjDevDebugSheet` remains as a developer surface; it is not meant to ship.

---

## 4. Verifying the engine drives the right calls

Enable DJ Flow, then:

```bash
adb logcat -c
adb logcat -v time | grep -E "DjFlowController|MusicManager|AutomixManager"
```

A healthy autonomous transition logs exactly this sequence, once:

```
DjFlowController: Autonomous DJ: Prebuffering top match <title> at <ms>ms
MusicManager:     Prebuffered transition for track <id> at <ms>ms
DjFlowController: Autonomous DJ: Auto-triggering transition to <title>
MusicManager:     Adopted prebuffered player for track <id>
MusicManager:     Phase lock: residual <x> ms after <n> seek(s)
MusicManager:     Bass swap at <ms> ms (fade <a>..<b>, phrase grid)
```

What to check:

- **`Auto-triggering` appears exactly once per transition.** More than one means the trigger
  re-armed mid-transition — the cascade bug.
- **`Phase lock: residual`** should be single-digit ms on a prebuffered transition. Three digits
  means the incoming deck was not prebuffered.
- **`Bass swap at`** should say `phrase grid`. `no grid` means beat analysis had not finished for
  that track, and the swap fell back to the middle of the fade.

Analysis results are cached in the `beat_info` table:

```bash
adb exec-out run-as com.alananasss.kittytune cat databases/soundtune_db > db.sqlite
sqlite3 db.sqlite "SELECT songId, round(bpm,1), downbeatOffsetMs, phraseOffsetMs,
                          round(downbeatConfidence,2), analysisVersion
                   FROM beat_info ORDER BY analyzedAt DESC LIMIT 10;"
```

`analysisVersion` below `BeatInfoEntity.CURRENT_ANALYSIS_VERSION` means the row predates the
current analyzer and will be re-analysed on next use.

---

## 5. The DJ assistant (`ai/`)

A separate, optional layer: it turns a sentence into a request the engine can act on. It does
not touch the audio engine — it produces a `DjIntent` and the caller decides what to do with it,
which keeps the chat from becoming a second way to start playback.

```kotlin
val router = DjAiRouter(context, apiKeyProvider = { prefs.getAiApiKey() })
val intent = router.interpret("mach mal was Ruhiges", nowPlaying = track.title)
// intent.searchQuery -> "chill", intent.energyMode -> WIND_DOWN, intent.reply -> one sentence
```

`interpret` is **never null**. Providers are tried best-first and any failure — no model, bad
key, timeout — falls through silently to the next. The only visible difference is
`DjIntent.source`.

| Tier | Needs | Notes |
|---|---|---|
| `LOCAL_MODEL` | a downloaded model **and** an inference runtime in the build | Offline, private, free |
| `REMOTE_API` | the user's own API key | Best quality, costs money, needs a connection |
| `KEYWORDS` | nothing | Genres, moods, "schneller"/"ruhiger", `\d+ bpm`, DE + EN |

### 5.1 Why the runtime is not in the default build

Measured, not estimated: adding `com.google.mediapipe:tasks-genai` takes the APK from **79 MB to
180 MB**. `libllm_inference_engine_jni.so` is 18–31 MB *per ABI*, and `release.yml` ships a
universal APK with all four. Every user would pay that for a feature most never enable — on top
of the ~520 MB model download it then needs.

It is therefore opt-in:

```bash
./gradlew assembleDebug -PwithLocalLlm=true
```

`LocalLlmDjProvider` lives in `app/src/localLlm/java/` and only compiles with the flag;
`DjAiRouter` finds it reflectively. `DjAiCapability.canRunLocalModel` reports false without it,
so the UI never offers a half-gigabyte download that nothing in the build could load.

If this should ship to users, the right vehicle is a Play dynamic feature module or an ABI split
— not the default APK.

### 5.2 Two things that must not change

**No API key ships in the app.** A key inside an APK is readable by anyone who unzips it. The
remote tier stays off until the user enters their own.

**The model URL points at an openly downloadable model.** Gemma 3 1B is licence-gated and its
HuggingFace URL answers `401` without a token, so an in-app download of it cannot work. The
default is Qwen2.5 0.5B Instruct (LiteRT `.task`, ~520 MB), which is open and supports range
requests — `DjModelDownloader` relies on that to resume after an interruption.

### 5.3 Package visibility

`AndroidManifest.xml` declares `<package android:name="com.google.android.aicore" />` inside
`<queries>`. Without it, Android 11+ package filtering hides AICore and `DjAiCapability` reports
"no AI runtime" on devices that plainly have one — which then produces the wrong advice in the UI.

---

## 6. Performance and battery

The engine raises the position poll rate so the beat grid can animate. That is expensive and is
gated accordingly.

| State | Tick | Why |
|---|---|---|
| DJ on, player UI on screen | 40 ms | 25 fps beat animation |
| DJ on, player closed or screen off | 250 ms | Nothing to animate; still fast enough for the trigger |
| DJ off | 200–500 ms | Unchanged from before the feature |

`PlayerViewModel.isDjBeatUiVisible` is set from a `DisposableEffect` in `PlayerScreen`, so the
high rate ends exactly when the UI leaves composition.

Measured on a Pixel 10 Pro XL, DJ Flow active, same track:

| | CPU |
|---|---|
| Before these fixes | ~232 % |
| After, player open | ~130 % |
| After, screen off | ~62 % |

Most of that was not the tick rate itself but what ran on each tick:

- `AchievementManager.addPlayTime(1, …)` was called **per tick**, and its parameter is *seconds*.
  At 40 ms that credited 25 seconds of listening per real second, and each call wrote nine
  achievement counters to `SharedPreferences` — about **225 writes a second**. It now credits
  elapsed wall time, so the numbers are also correct for the first time.
- Four `SharedPreferences` reads per tick, for values that change when the user opens settings.
  Now re-read once a second.
- The queue top-up check ran on every tick. Now throttled to 3 s.

If you add work to `onPositionUpdated`, remember it can run 25 times a second.

---

## 7. Threading and cost

- `flowState` emits on the main thread; collect it directly in Compose.
- Beat analysis runs on IO with two mutexes: one for the playing track, one for lookahead. At most
  two analyses run at once.
- A full analysis is a few hundred ms of DSP on ~18 s of decoded audio, plus network for streams.
  Results are cached per track in Room and survive restarts.
- The UI never blocks on analysis. `currentBpm == 0f` simply means "not known yet".
