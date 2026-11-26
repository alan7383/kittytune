# Artist cover lookup performance

ListeningReports previously scanned the sorted tracks for each artist and scanned that artist's credits at every step. This repeats work proportional to artists × tracks × credits. The patch builds one track-rank/cover index and selects each artist's minimum matching rank in one pass over its credits. Counter calculation, artist/track sorting, avatars, collaborators and top limits are unchanged. A null cover on the first matching track remains null.

Run the real helper's six regressions and benchmark with Python 3.12+ and JDK21:

```sh
python experiments/stats-cover/run.py --output verification/stats-cover
```

The benchmark compares the production index (including index construction) with the original nested selection on identical input, checks exact result equality and runs five fresh JVMs. Each process uses five warm-up and ten measured iterations per size in alternating order. Fixtures have 100/500 artists, ten distinct tracks per artist and 100 credits per artist, including missing artwork. CSV rows record process, artists, sample, baseline flag, nanoseconds and result checksum. These are in-memory fallback-cover component measurements, not complete report, device frame-time or whole-app speedups. No timing result is claimed before completion.

The GitHub job also compiles Android/Wear and runs both the new index tests and the existing ListeningReportTest through the Android module. Local standalone verification does not compile the report's Android entity dependencies. Real-device statistics navigation still needs validation.
