"""Reference fakeprints for the Kotlin port, computed exactly like lofcz/ai-music-detector's inference.py."""
import sys, struct
import numpy as np, torch, torchaudio
from scipy.ndimage import minimum_filter1d

w = np.load(sys.argv[1] + "/weights.npz")
weights, bias = w["weights"][0].astype(np.float64), float(w["bias"][0])
out = sys.argv[2]

def signal(sr, seconds):
    n = sr * seconds
    t = np.arange(n, dtype=np.float64) / sr
    x = (0.3 * np.sin(2 * np.pi * 220 * t) + 0.2 * np.sin(2 * np.pi * 440 * t + 0.5)
         + 0.1 * np.sin(2 * np.pi * 1760.5 * t) + 0.05 * np.sin(2 * np.pi * 3520.25 * t)
         + 0.02 * np.sin(2 * np.pi * 5000 * t) * np.sin(2 * np.pi * 0.5 * t))
    state = 12345
    noise = np.empty(n)
    for i in range(n):
        state = (state * 1103515245 + 12345) % 2147483648
        noise[i] = state / 2147483648.0 - 0.5
    return (x + 0.05 * noise).astype(np.float32)

def fakeprint(audio16k):
    # float64 so the reference is free of float32 rounding noise (it moves single bins by up to 0.004)
    spec = torchaudio.transforms.Spectrogram(n_fft=8192, power=2, normalized=False).to(torch.float64)(torch.from_numpy(audio16k.astype(np.float64))[None])
    spec_db = 10 * torch.log10(torch.clamp(spec, min=1e-10, max=1e6))
    mean_spectrum = spec_db.mean(dim=(0, 2)).numpy().astype(np.float64)
    freq_bins = np.linspace(0, 8000, 4097)
    mask = (freq_bins >= 1000) & (freq_bins <= 8000)
    fs = mean_spectrum[mask]
    hull = np.clip(minimum_filter1d(fs, size=10, mode='nearest'), -45, None)
    residue = np.clip(np.clip(fs - hull, 0, None), 0, 5)
    return (residue / (residue.max() + 1e-6)).astype(np.float32), np.where(mask)[0]

for sr in (44100, 48000):
    x = signal(sr, 8)
    y = torchaudio.transforms.Resample(sr, 16000)(torch.from_numpy(x)[None])[0].numpy()
    fp, idx = fakeprint(y)
    logit = float(np.dot(weights, fp.astype(np.float64)) + bias)
    p = 1 / (1 + np.exp(-logit))
    print(sr, "resampled", y.shape, "bins", idx[0], idx[-1], len(idx), "p", p, "logit", logit, "fp nonzero", int((fp > 0).sum()))
    with open(f"{out}/resampled_{sr}.bin", "wb") as f: f.write(y[:4096].astype('<f4').tobytes())
    with open(f"{out}/fakeprint_{sr}.bin", "wb") as f: f.write(fp.astype('<f4').tobytes())
    with open(f"{out}/expected_{sr}.txt", "w") as f: f.write(f"{len(y)} {float(p)!r} {float(logit)!r}\n")

print("minfilter check", minimum_filter1d(np.array([5., 3, 8, 1, 9, 2, 7, 4, 6, 0, 11, 12, 13]), size=10, mode='nearest'))
