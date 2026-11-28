# Playback setup verification

The normal playback/prebuffer paths prepare media before scheduling cover loading. Artwork URIs stay in the initial metadata; the same scaled JPEG85 data is appended when available. Delivery is cancelled by a newer request in the same slot, and metadata is only updated for the identical item still present in an existing deck. A delayed response cannot attach artwork to a new occurrence of the same track.

Prepared media items already carry the result of the background download/stream lookup. The DRM provider now consumes their embedded offline key IDs and skips the redundant database lookup. Unresolved media-session and other legacy entries retain the existing lookup fallback. This does not claim to remove every synchronous operation from all legacy DRM paths.

Run with Python 3.12+ and JDK 21:

```sh
python experiments/playback-setup/run.py --output verification/playback-setup
```

An optional `--dependency-cache <GRADLE_USER_HOME>` reuses existing jars read-only. Otherwise fixed Maven dependencies are downloaded to the output directory. The runner compiles the real coordinator/policy and seven JUnit regressions. They cover delayed/failed images, cancellation, same-track reselection, offline key precedence and the legacy fallback. They do not run ExoPlayer or exercise real offline licences. The GitHub workflow separately compiles the entire Android/Wear application and runs the targeted tests through the Android module.

No Android timing gains have been measured. Device validation should include a delayed uncached cover with fast audio, downloaded tracks with remote art, rapid A → B → A selections, gapless playback, crossfade/prebuffer swaps and notification/Android Auto artwork updates. Metadata-only replacement follows the [Media3 notification guidance](https://developer.android.com/media/media3/session/background-playback).
