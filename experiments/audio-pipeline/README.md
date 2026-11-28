# Player effects pipeline verification

The playback sink used to register 31 creative effects independently. They are format-active even when switched off, and each disabled stage copies every PCM block. The grouped stage skips disabled children and owns one final output buffer. Enabled DSP algorithms, order, controls and export are unchanged.

Run `python experiments/audio-pipeline/run.py --output verification/audio-pipeline` with JDK 21. Optional `--java-home`, `--dependency-cache` and `--media3-sources` allow reuse of local tools/downloads. This compiles actual app DSP and the official Media3 1.11.1 AudioProcessor/BaseAudioProcessor sources; minimal Android/routing/format shims permit desktop execution. GitHub separately compiles the whole Android/Wear app and runs the same regressions with the real Android dependencies and routing matrix.

Tests compare the grouped and original ungrouped real effects byte-for-byte: disabled, each effect, combined presets, chunk sizes, mono, stereo, live toggles, routing bypass, owned output under partial consumption, configure/flush/reset/EOS. Pseudo-random DSP uses its existing deterministic seed.

Five fresh JVMs compare warmed direct PCM buffers at 48 kHz stereo, 1024/4096 frames per block, disabled and two-effect presets. Nine rounds alternate execution order. CSV reports elapsed nanoseconds per 2000 blocks and initial direct buffer allocations (via BufferPoolMXBean). Report absolute time per second of audio as well as ratios. This is a controlled grouped-versus-ungrouped comparison using the same corrected DSP implementations, including the new volatile switches, rather than a frozen upstream-versus-patch benchmark. It does not include Media3 sink scheduling, decoder, native DSP, Android runtime, UI, networking or storage.

All-off creative-effect output copies decrease from 31 to 1; this is not a 31x whole-app gain. At 4096 stereo PCM16 frames, the saved output storage is 30 * 16384 = 491520 bytes per configured deck at that block size. Delay-line/effect-state allocations are unchanged. Android CPU, total RAM and battery impact require a device and remain unmeasured.
