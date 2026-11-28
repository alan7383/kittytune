# Progressive lyrics search performance

The Android search merges provider results on Main, including background searches after a track's lyrics settle. At baseline `8db1db4e`, comparator callbacks repeatedly calculate title/artist matching and construct normalization regexes. This patch computes each retained result's sort keys once, reuses the same regex patterns and runs merges on Default. A per-search coordinator serializes arrivals, rejects superseded searches and retries when selection changes during computation.

Run with JDK 21 and Python 3.12 or newer:

```sh
python experiments/lyrics-performance/run.py --output verification/lyrics
```

`--dependency-cache <GRADLE_USER_HOME>` can reuse jars from an existing Gradle cache without writing to it. Otherwise fixed Maven Central dependencies are downloaded to the output directory. The script compiles real production ranking, matcher, models and coordinator; no ranking implementation is replaced by a test stub. JUnit covers ordering, duplicate choice, stable ties, multilingual normalization, simultaneous arrivals, cancellation, superseded generations and selection changes.

The reference matcher and merge implementation are generated from the baseline commit in Git. Five fresh JVMs compare both implementations in alternating order, after warm-up, on identical 40/150/400-result fixtures. Each process verifies exact output equality before measurement. Raw CSV samples, revision, source hashes, Java version and process medians are saved beside `summary.json`.

These are component measurements. This harness does not compile PlayerViewModel or the full Android app, exercise device audio, measure frame times or establish whole-app speedups. Device profiling and the Android unit/build checks remain necessary before calling the change verified.

## Recorded results

Revision `cb96c584`, baseline `8db1db4e`. On 25 November 2026 all nine targeted parity/concurrency tests passed locally and in [GitHub Actions](https://github.com/ilgiova237/kittytune/actions/runs/45682543234). Five fresh JVMs per environment used ten warm-up iterations and fifteen measured iterations per size, alternating baseline/candidate order.

| Incoming results | Windows JDK 21 median, before → after | GitHub Linux JDK 21 median, before → after |
| --- | --- | --- |
| 40 | 41.96 → 4.52 ms | 13.03 → 1.53 ms |
| 150 | 191.93 → 12.60 ms | 59.74 → 3.89 ms |
| 400 | 570.68 → 29.73 ms | 184.56 → 9.19 ms |

Every measured size improved in every process. Raw CSV samples and source/version metadata are under `results/`. These numbers measure the merge component on desktop JVMs; Android device timings and whole-app responsiveness have not been measured. Full Android compilation is a separate pending check.

The dedicated GitHub workflow only verifies this branch; it does not publish app releases.
