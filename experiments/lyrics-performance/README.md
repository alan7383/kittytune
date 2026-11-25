# Progressive lyrics search performance

The Android search merges provider results on Main, including background searches after a track's lyrics settle. At baseline `8db1db4e`, comparator callbacks repeatedly calculate title/artist matching and construct normalization regexes. This patch computes each retained result's sort keys once, reuses the same regex patterns and runs merges on Default. A per-search coordinator serializes arrivals, rejects superseded searches and retries when selection changes during computation.

Run with JDK 21 and Python 3.12 or newer:

```sh
python experiments/lyrics-performance/run.py --output verification/lyrics
```

`--dependency-cache <GRADLE_USER_HOME>` can reuse jars from an existing Gradle cache without writing to it. Otherwise fixed Maven Central dependencies are downloaded to the output directory. The script compiles real production ranking, matcher, models and coordinator; no ranking implementation is replaced by a test stub. JUnit covers ordering, duplicate choice, stable ties, multilingual normalization, simultaneous arrivals, cancellation, superseded generations and selection changes.

The reference matcher and merge implementation are generated from the baseline commit in Git. Five fresh JVMs compare both implementations in alternating order, after warm-up, on identical 40/150/400-result fixtures. Each process verifies exact output equality before measurement. Raw CSV samples, revision, source hashes, Java version and process medians are saved beside `summary.json`.

These are component measurements. This harness does not compile PlayerViewModel or the full Android app, exercise device audio, measure frame times or establish whole-app speedups. Device profiling and the Android unit/build checks remain necessary before calling the change verified. No measurements or test outcomes have been recorded yet.

The dedicated GitHub workflow only verifies this branch; it does not publish app releases.
