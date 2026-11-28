# Batch like exclusion updates

The bulk-like UI action and remote batch sync currently parse and rewrite the complete exclusion preferences once per track. For N changes and an exclusion set of size M this repeats work of order N×M, in addition to N preference editor/apply calls. The patch reads, changes and writes the set once per batch. Numeric canonicalization, malformed-value filtering, single-track behavior, final membership, liked-track order and network requests are preserved.

The standalone runner compiles the real helper and six regressions. Tests compare randomized mixed batches against the previous per-track loop and assert one preference read/write for a 5,000-track batch. The benchmark compares the actual helper with the frozen old update algorithm, using the same in-memory preference values in five fresh JVMs with warm-up and alternating order. It does not measure physical flash writes, Android UI frame times or total synchronization time. No performance result is claimed before these checks finish.

Run with Python 3.12+ and JDK21:

```sh
python experiments/like-blacklist/run.py --output verification/like-blacklist
```

The GitHub workflow also builds Android/Wear and runs the targeted tests through the Android module. Single-item and batch preference updates share a repository lock. Storage loading at initial app startup is a separate, unchanged path.
