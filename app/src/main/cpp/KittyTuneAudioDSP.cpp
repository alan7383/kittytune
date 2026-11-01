#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <float.h>

extern "C" {
#include "ebur128.h"
}

#define LOG_TAG "KittyTuneAudioDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static float linearToDb(float linear) {
    if (linear <= 0.0f) return -120.0f;
    return 20.0f * log10f(linear);
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── LOOKAHEAD PEAK LIMITER ───────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════
//
// The audio is delayed by the lookahead, and the gain each frame needs is known that long before the
// frame comes out. A sliding minimum over the lookahead window gives the deepest reduction coming up,
// and the gain glides to it before the peak arrives — so a peak is caught on its first sample, with
// no overshoot and no click, and released smoothly afterwards. Stereo-linked: one gain for all
// channels, so the image does not wander.

static const float kLimiterThreshold = 0.8912509f;   // -1 dBFS
static const float kLimiterLookaheadMs = 5.0f;
static const float kLimiterReleaseMs = 150.0f;

typedef struct {
    int channels;
    int lookahead;            // frames
    float* delay;             // lookahead * channels, ring
    int pos;
    float* minValues;         // sliding-minimum deque (values), ring of lookahead + 1
    long long* minIndex;      // sliding-minimum deque (frame indices)
    int dqHead;
    int dqSize;
    int dqCap;
    long long frame;
    float gain;
    float attackCoeff;
    float releaseCoeff;
    float deepestReductionDb; // since last read, for diagnostics
} Limiter;

static int limiterInit(Limiter* l, int channels, int sampleRate) {
    memset(l, 0, sizeof(Limiter));
    l->channels = channels;
    l->lookahead = (int)(sampleRate * kLimiterLookaheadMs / 1000.0f);
    if (l->lookahead < 1) l->lookahead = 1;
    l->dqCap = l->lookahead + 2;
    l->delay = (float*)calloc((size_t)l->lookahead * channels, sizeof(float));
    l->minValues = (float*)calloc((size_t)l->dqCap, sizeof(float));
    l->minIndex = (long long*)calloc((size_t)l->dqCap, sizeof(long long));
    if (!l->delay || !l->minValues || !l->minIndex) return 0;
    l->gain = 1.0f;
    // Reach the target within the lookahead: e^-5 of the error is left when the peak comes out.
    l->attackCoeff = 1.0f - expf(-5.0f / (float)l->lookahead);
    l->releaseCoeff = 1.0f - expf(-1.0f / (sampleRate * kLimiterReleaseMs / 1000.0f));
    return 1;
}

static void limiterFree(Limiter* l) {
    free(l->delay);
    free(l->minValues);
    free(l->minIndex);
    memset(l, 0, sizeof(Limiter));
}

static void limiterReset(Limiter* l) {
    if (!l->delay) return;
    memset(l->delay, 0, (size_t)l->lookahead * l->channels * sizeof(float));
    l->pos = 0;
    l->dqHead = 0;
    l->dqSize = 0;
    l->frame = 0;
    l->gain = 1.0f;
}

/**
 * Processes one interleaved frame in place. With [active] false the limiter only delays, so turning
 * it on or off never shifts the audio in time.
 */
static inline void limiterProcessFrame(Limiter* l, float* frame, int active) {
    const int ch = l->channels;
    float peak = 0.0f;
    for (int c = 0; c < ch; c++) {
        float a = fabsf(frame[c]);
        if (a > peak) peak = a;
    }
    float required = (active && peak > kLimiterThreshold) ? kLimiterThreshold / peak : 1.0f;

    // Sliding minimum of the required gain over the next [lookahead] frames.
    while (l->dqSize > 0) {
        int back = (l->dqHead + l->dqSize - 1) % l->dqCap;
        if (l->minValues[back] >= required) l->dqSize--; else break;
    }
    int slot = (l->dqHead + l->dqSize) % l->dqCap;
    l->minValues[slot] = required;
    l->minIndex[slot] = l->frame;
    l->dqSize++;
    while (l->dqSize > 0 && l->minIndex[l->dqHead] <= l->frame - l->lookahead) {
        l->dqHead = (l->dqHead + 1) % l->dqCap;
        l->dqSize--;
    }
    float target = l->dqSize > 0 ? l->minValues[l->dqHead] : 1.0f;

    if (target < l->gain) l->gain += (target - l->gain) * l->attackCoeff;
    else l->gain += (target - l->gain) * l->releaseCoeff;

    float* slotFrame = l->delay + (size_t)l->pos * ch;
    for (int c = 0; c < ch; c++) {
        float delayed = slotFrame[c];
        slotFrame[c] = frame[c];
        float out = delayed * l->gain;
        if (active) {
            // The glide leaves a sliver of the overshoot; never let it through.
            if (out > kLimiterThreshold) out = kLimiterThreshold;
            if (out < -kLimiterThreshold) out = -kLimiterThreshold;
        }
        frame[c] = out;
    }
    l->pos = (l->pos + 1) % l->lookahead;
    l->frame++;

    if (l->gain < 1.0f) {
        float reductionDb = linearToDb(l->gain);
        if (reductionDb < l->deepestReductionDb) l->deepestReductionDb = reductionDb;
    }
}

static inline short toShort(float v) {
    if (v > 1.0f) v = 1.0f;
    if (v < -1.0f) v = -1.0f;
    return (short)lrintf(v * 32767.0f);
}

extern "C" {

// ═══════════════════════════════════════════════════════════════════════════════
// ─── TRACK NORMALIZER ─────────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════
//
// First in the effect chain. It measures the track as decoded (EBU R128 momentary, short-term and
// gated integrated loudness, plus the sample peak), applies the gain the Kotlin side decided for this
// stretch of audio as a per-sample ramp, and keeps the result out of clipping with its own limiter.
// The decisions — which gain, how fast to move — live in TrackNormalizerProcessor, where they are
// easier to read and tune; this is only the arithmetic.

typedef struct {
    int channels;
    int sampleRate;
    ebur128_state* meter;
    long long measuredFrames;
    float peakLinear;
    float* buffer;
    int bufferCapacity;
    Limiter limiter;
} TrackNormalizerState;

static ebur128_state* createTrackMeter(int channels, int sampleRate) {
    return ebur128_init((unsigned int)channels, (unsigned long)sampleRate,
                        EBUR128_MODE_S | EBUR128_MODE_I | EBUR128_MODE_HISTOGRAM);
}

static int ensureBuffer(float** buffer, int* capacity, int samples) {
    if (samples <= *capacity) return 1;
    float* grown = (float*)realloc(*buffer, (size_t)samples * sizeof(float));
    if (!grown) return 0;
    *buffer = grown;
    *capacity = samples;
    return 1;
}

static void normalizerFree(TrackNormalizerState* s) {
    if (!s) return;
    if (s->meter) ebur128_destroy(&s->meter);
    limiterFree(&s->limiter);
    free(s->buffer);
    free(s);
}

static double validLoudness(double value) {
    return (isfinite(value) && value > -70.0) ? value : NAN;
}

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeCreate(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {
    TrackNormalizerState* s = (TrackNormalizerState*)calloc(1, sizeof(TrackNormalizerState));
    if (!s) return 0;
    s->channels = channels;
    s->sampleRate = sampleRate;
    s->meter = createTrackMeter(channels, sampleRate);
    if (!s->meter || !limiterInit(&s->limiter, channels, sampleRate)) {
        LOGE("Track normalizer init failed (ch=%d, sr=%d)", channels, sampleRate);
        normalizerFree(s);
        return 0;
    }
    LOGI("Track normalizer ready: ch=%d, sr=%d", channels, sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {
    normalizerFree((TrackNormalizerState*)(intptr_t)handle);
}

/** A new track begins: its measurement starts from nothing. */
JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeResetMeter(
    JNIEnv* env, jclass clazz, jlong handle) {
    TrackNormalizerState* s = (TrackNormalizerState*)(intptr_t)handle;
    if (!s) return;
    if (s->meter) ebur128_destroy(&s->meter);
    s->meter = createTrackMeter(s->channels, s->sampleRate);
    s->measuredFrames = 0;
    s->peakLinear = 0.0f;
}

/** A seek or a new item: the audio in the lookahead belongs to before it, so it is dropped. */
JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeFlush(
    JNIEnv* env, jclass clazz, jlong handle) {
    TrackNormalizerState* s = (TrackNormalizerState*)(intptr_t)handle;
    if (s) limiterReset(&s->limiter);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeProcess(
    JNIEnv* env, jclass clazz, jlong handle,
    jobject inputBuffer, jobject outputBuffer, jint numFrames, jint inOffset,
    jboolean measure, jfloat startGain, jfloat endGain, jboolean limit) {
    TrackNormalizerState* s = (TrackNormalizerState*)(intptr_t)handle;
    if (!s || numFrames <= 0) return;
    short* in = (short*)((char*)env->GetDirectBufferAddress(inputBuffer) + inOffset);
    short* out = (short*)env->GetDirectBufferAddress(outputBuffer);
    if (!in || !out) return;

    const int ch = s->channels;
    const int samples = numFrames * ch;
    if (!ensureBuffer(&s->buffer, &s->bufferCapacity, samples)) return;
    float* f = s->buffer;
    for (int i = 0; i < samples; i++) f[i] = (float)in[i] / 32768.0f;

    if (measure && s->meter) {
        ebur128_add_frames_float(s->meter, f, (size_t)numFrames);
        s->measuredFrames += numFrames;
        for (int i = 0; i < samples; i++) {
            float a = fabsf(f[i]);
            if (a > s->peakLinear) s->peakLinear = a;
        }
    }

    const float step = (endGain - startGain) / (float)numFrames;
    float g = startGain;
    for (int frame = 0; frame < numFrames; frame++) {
        g += step;
        float* fr = f + (size_t)frame * ch;
        for (int c = 0; c < ch; c++) fr[c] *= g;
        limiterProcessFrame(&s->limiter, fr, limit ? 1 : 0);
        for (int c = 0; c < ch; c++) out[(size_t)frame * ch + c] = toShort(fr[c]);
    }
}

/** momentary, short-term, integrated (LUFS, NaN when not yet known), peak dBFS, measured seconds. */
JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_TrackNormalizerProcessor_nativeGetStats(
    JNIEnv* env, jclass clazz, jlong handle, jfloatArray outStats) {
    TrackNormalizerState* s = (TrackNormalizerState*)(intptr_t)handle;
    float stats[5] = {NAN, NAN, NAN, -120.0f, 0.0f};
    if (s && s->meter) {
        double v;
        if (ebur128_loudness_momentary(s->meter, &v) == EBUR128_SUCCESS) stats[0] = (float)validLoudness(v);
        if (ebur128_loudness_shortterm(s->meter, &v) == EBUR128_SUCCESS) stats[1] = (float)validLoudness(v);
        if (ebur128_loudness_global(s->meter, &v) == EBUR128_SUCCESS) stats[2] = (float)validLoudness(v);
        stats[3] = linearToDb(s->peakLinear);
        stats[4] = (float)((double)s->measuredFrames / (double)s->sampleRate);
    }
    env->SetFloatArrayRegion(outStats, 0, 5, stats);
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── SAFETY LIMITER (end of the chain) ────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════════════════
//
// Last in the chain, after the EQ and the effects, which can push a normalized track back over full
// scale. Only a limiter: the level was already set at the start of the chain.

typedef struct {
    int channels;
    float* buffer;
    int bufferCapacity;
    Limiter limiter;
} SafetyLimiterState;

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_audio_normalization_SafetyLimiterProcessor_nativeCreate(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {
    SafetyLimiterState* s = (SafetyLimiterState*)calloc(1, sizeof(SafetyLimiterState));
    if (!s) return 0;
    s->channels = channels;
    if (!limiterInit(&s->limiter, channels, sampleRate)) {
        limiterFree(&s->limiter);
        free(s);
        return 0;
    }
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_SafetyLimiterProcessor_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {
    SafetyLimiterState* s = (SafetyLimiterState*)(intptr_t)handle;
    if (!s) return;
    limiterFree(&s->limiter);
    free(s->buffer);
    free(s);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_SafetyLimiterProcessor_nativeFlush(
    JNIEnv* env, jclass clazz, jlong handle) {
    SafetyLimiterState* s = (SafetyLimiterState*)(intptr_t)handle;
    if (s) limiterReset(&s->limiter);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_audio_normalization_SafetyLimiterProcessor_nativeProcess(
    JNIEnv* env, jclass clazz, jlong handle,
    jobject inputBuffer, jobject outputBuffer, jint numFrames, jint inOffset, jboolean active) {
    SafetyLimiterState* s = (SafetyLimiterState*)(intptr_t)handle;
    if (!s || numFrames <= 0) return;
    short* in = (short*)((char*)env->GetDirectBufferAddress(inputBuffer) + inOffset);
    short* out = (short*)env->GetDirectBufferAddress(outputBuffer);
    if (!in || !out) return;

    const int ch = s->channels;
    const int samples = numFrames * ch;
    if (!ensureBuffer(&s->buffer, &s->bufferCapacity, samples)) return;
    float* f = s->buffer;
    for (int i = 0; i < samples; i++) f[i] = (float)in[i] / 32768.0f;
    for (int frame = 0; frame < numFrames; frame++) {
        float* fr = f + (size_t)frame * ch;
        limiterProcessFrame(&s->limiter, fr, active ? 1 : 0);
        for (int c = 0; c < ch; c++) out[(size_t)frame * ch + c] = toShort(fr[c]);
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── OFFLINE ANALYZER (downloads scanner, upcoming-track pre-analysis) ────────
// ═══════════════════════════════════════════════════════════════════════════════

typedef struct {
    ebur128_state* ebur128;
    int channels;
} AnalyzerState;

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeCreateAnalyzer(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {
    AnalyzerState* s = (AnalyzerState*)calloc(1, sizeof(AnalyzerState));
    if (!s) return 0;
    s->channels = channels;
    int mode = EBUR128_MODE_I | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    s->ebur128 = ebur128_init((unsigned int)channels, (unsigned long)sampleRate, mode);
    if (!s->ebur128) {
        free(s);
        return 0;
    }
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeDestroyAnalyzer(
    JNIEnv* env, jclass clazz, jlong handle) {
    AnalyzerState* s = (AnalyzerState*)(intptr_t)handle;
    if (!s) return;
    if (s->ebur128) ebur128_destroy(&s->ebur128);
    free(s);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeAddFramesFloat(
    JNIEnv* env, jclass clazz, jlong handle, jfloatArray samples, jint numFrames) {
    AnalyzerState* s = (AnalyzerState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;
    jfloat* data = env->GetFloatArrayElements(samples, NULL);
    if (!data) return;
    ebur128_add_frames_float(s->ebur128, data, (size_t)numFrames);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {
    AnalyzerState* s = (AnalyzerState*)(intptr_t)handle;
    if (!s || !s->ebur128) return -70.0f;
    double integrated = -HUGE_VAL;
    if (ebur128_loudness_global(s->ebur128, &integrated) == EBUR128_SUCCESS
        && isfinite(integrated) && integrated > -HUGE_VAL) {
        return (jfloat)integrated;
    }
    return -70.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedTruePeakDb(
    JNIEnv* env, jclass clazz, jlong handle) {
    AnalyzerState* s = (AnalyzerState*)(intptr_t)handle;
    if (!s || !s->ebur128) return -120.0f;
    double maxTp = 0.0;
    for (int ch = 0; ch < s->channels; ch++) {
        double tp;
        if (ebur128_true_peak(s->ebur128, (unsigned int)ch, &tp) == EBUR128_SUCCESS && isfinite(tp)) {
            if (tp > maxTp) maxTp = tp;
        }
    }
    if (maxTp <= 0.0) return -120.0f;
    return (jfloat)(20.0 * log10(maxTp));
}

// ═══════════════════════════════════════════════════════════════════════════════
// ─── PRO DUAL-BRANCH 90° HILBERT / ALLPASS STEREO VOCAL REMOVER ─────────────
// ═══════════════════════════════════════════════════════════════════════════════

typedef struct {
    int sampleRate;
    float suppressionLevel;

    // 4th-order Linkwitz-Riley Low-Pass (130 Hz)
    float lp_b0, lp_b1, lp_b2, lp_a1, lp_a2;
    float lp_x1_l1, lp_x2_l1, lp_y1_l1, lp_y2_l1;
    float lp_x1_l2, lp_x2_l2, lp_y1_l2, lp_y2_l2;
    float lp_x1_r1, lp_x2_r1, lp_y1_r1, lp_y2_r1;
    float lp_x1_r2, lp_x2_r2, lp_y1_r2, lp_y2_r2;

    // 4th-order Linkwitz-Riley High-Pass (10500 Hz)
    float hp_b0, hp_b1, hp_b2, hp_a1, hp_a2;
    float hp_x1_l1, hp_x2_l1, hp_y1_l1, hp_y2_l1;
    float hp_x1_l2, hp_x2_l2, hp_y1_l2, hp_y2_l2;
    float hp_x1_r1, hp_x2_r1, hp_y1_r1, hp_y2_r1;
    float hp_x1_r2, hp_x2_r2, hp_y1_r2, hp_y2_r2;

    // Dome 90° Phase Splitter All-Pass Coefficients
    // Branch A (Left): 150 Hz, 1250 Hz, 9800 Hz
    float ap_a1, ap_a3, ap_a5;
    float ap_x_a1, ap_y_a1;
    float ap_x_a3, ap_y_a3;
    float ap_x_a5, ap_y_a5;

    // Branch B (Right): 430 Hz, 3500 Hz
    float ap_a2, ap_a4;
    float ap_x_b2, ap_y_b2;
    float ap_x_b4, ap_y_b4;
} ProVocalRemoverState;

static float calcAllpassAlpha(double fc, double fs) {
    double tanW = tan(M_PI * fc / fs);
    return (float)((tanW - 1.0) / (tanW + 1.0));
}

static void initProVocalFilters(ProVocalRemoverState* s, int sr) {
    s->sampleRate = sr;
    double fs = (double)sr;

    // Low-Pass at 130 Hz (Q = 0.7071)
    double omegaL = 2.0 * M_PI * 130.0 / fs;
    double sinL = sin(omegaL);
    double cosL = cos(omegaL);
    double alphaL = sinL / (2.0 * 0.70710678);
    double a0_L = 1.0 + alphaL;

    s->lp_b0 = (float)(((1.0 - cosL) / 2.0) / a0_L);
    s->lp_b1 = (float)((1.0 - cosL) / a0_L);
    s->lp_b2 = (float)(((1.0 - cosL) / 2.0) / a0_L);
    s->lp_a1 = (float)((-2.0 * cosL) / a0_L);
    s->lp_a2 = (float)((1.0 - alphaL) / a0_L);

    // High-Pass at 10500 Hz (Q = 0.7071)
    double omegaH = 2.0 * M_PI * 10500.0 / fs;
    double sinH = sin(omegaH);
    double cosH = cos(omegaH);
    double alphaH = sinH / (2.0 * 0.70710678);
    double a0_H = 1.0 + alphaH;

    s->hp_b0 = (float)(((1.0 + cosH) / 2.0) / a0_H);
    s->hp_b1 = (float)((-(1.0 + cosH)) / a0_H);
    s->hp_b2 = (float)(((1.0 + cosH) / 2.0) / a0_H);
    s->hp_a1 = (float)((-2.0 * cosH) / a0_H);
    s->hp_a2 = (float)((1.0 - alphaH) / a0_H);

    // 90° Hilbert All-Pass Network
    s->ap_a1 = calcAllpassAlpha(150.0, fs);
    s->ap_a3 = calcAllpassAlpha(1250.0, fs);
    s->ap_a5 = calcAllpassAlpha(9800.0, fs);

    s->ap_a2 = calcAllpassAlpha(430.0, fs);
    s->ap_a4 = calcAllpassAlpha(3500.0, fs);
}

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_ui_player_audio_VocalRemoverAudioProcessor_nativeCreate(
    JNIEnv* env, jclass clazz, jint sampleRate) {

    ProVocalRemoverState* s = (ProVocalRemoverState*)calloc(1, sizeof(ProVocalRemoverState));
    if (!s) return 0;

    s->suppressionLevel = 1.0f;
    initProVocalFilters(s, sampleRate);

    LOGI("Pro 90-deg AllPass Vocal Remover initialized: sr=%d", sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_VocalRemoverAudioProcessor_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {

    ProVocalRemoverState* s = (ProVocalRemoverState*)(intptr_t)handle;
    if (s) free(s);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_VocalRemoverAudioProcessor_nativeSetLevel(
    JNIEnv* env, jclass clazz, jlong handle, jfloat level) {

    ProVocalRemoverState* s = (ProVocalRemoverState*)(intptr_t)handle;
    if (s) {
        s->suppressionLevel = fmaxf(0.0f, fminf(1.0f, level));
    }
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_VocalRemoverAudioProcessor_nativeProcess(
    JNIEnv* env, jclass clazz, jlong handle,
    jobject inputBuffer, jobject outputBuffer, jint numFrames, jint inOffset) {

    ProVocalRemoverState* s = (ProVocalRemoverState*)(intptr_t)handle;
    if (!s) return;

    short* in = (short*)((char*)env->GetDirectBufferAddress(inputBuffer) + inOffset);
    short* out = (short*)env->GetDirectBufferAddress(outputBuffer);
    if (!in || !out) return;

    float sup = s->suppressionLevel;
    float makeupGain = 1.0f + 1.0f * sup; // x2.0 (+6dB) makeup to restore 100% full instrumental amplitude

    for (int i = 0; i < numFrames; i++) {
        float inL = (float)in[i * 2];
        float inR = (float)in[i * 2 + 1];

        // 1. Cascaded 4th-order Low-Pass (Left & Right)
        float lp1_l = s->lp_b0 * inL + s->lp_b1 * s->lp_x1_l1 + s->lp_b2 * s->lp_x2_l1 - s->lp_a1 * s->lp_y1_l1 - s->lp_a2 * s->lp_y2_l1;
        s->lp_x2_l1 = s->lp_x1_l1; s->lp_x1_l1 = inL; s->lp_y2_l1 = s->lp_y1_l1; s->lp_y1_l1 = lp1_l;

        float lowL = s->lp_b0 * lp1_l + s->lp_b1 * s->lp_x1_l2 + s->lp_b2 * s->lp_x2_l2 - s->lp_a1 * s->lp_y1_l2 - s->lp_a2 * s->lp_y2_l2;
        s->lp_x2_l2 = s->lp_x1_l2; s->lp_x1_l2 = lp1_l; s->lp_y2_l2 = s->lp_y1_l2; s->lp_y1_l2 = lowL;

        float lp1_r = s->lp_b0 * inR + s->lp_b1 * s->lp_x1_r1 + s->lp_b2 * s->lp_x2_r1 - s->lp_a1 * s->lp_y1_r1 - s->lp_a2 * s->lp_y2_r1;
        s->lp_x2_r1 = s->lp_x1_r1; s->lp_x1_r1 = inR; s->lp_y2_r1 = s->lp_y1_r1; s->lp_y1_r1 = lp1_r;

        float lowR = s->lp_b0 * lp1_r + s->lp_b1 * s->lp_x1_r2 + s->lp_b2 * s->lp_x2_r2 - s->lp_a1 * s->lp_y1_r2 - s->lp_a2 * s->lp_y2_r2;
        s->lp_x2_r2 = s->lp_x1_r2; s->lp_x1_r2 = lp1_r; s->lp_y2_r2 = s->lp_y1_r2; s->lp_y1_r2 = lowR;

        // 2. Cascaded 4th-order High-Pass (Left & Right)
        float hp1_l = s->hp_b0 * inL + s->hp_b1 * s->hp_x1_l1 + s->hp_b2 * s->hp_x2_l1 - s->hp_a1 * s->hp_y1_l1 - s->hp_a2 * s->hp_y2_l1;
        s->hp_x2_l1 = s->hp_x1_l1; s->hp_x1_l1 = inL; s->hp_y2_l1 = s->hp_y1_l1; s->hp_y1_l1 = hp1_l;

        float highL = s->hp_b0 * hp1_l + s->hp_b1 * s->hp_x1_l2 + s->hp_b2 * s->hp_x2_l2 - s->hp_a1 * s->hp_y1_l2 - s->hp_a2 * s->hp_y2_l2;
        s->hp_x2_l2 = s->hp_x1_l2; s->hp_x1_l2 = hp1_l; s->hp_y2_l2 = s->hp_y1_l2; s->hp_y1_l2 = highL;

        float hp1_r = s->hp_b0 * inR + s->hp_b1 * s->hp_x1_r1 + s->hp_b2 * s->hp_x2_r1 - s->hp_a1 * s->hp_y1_r1 - s->hp_a2 * s->hp_y2_r1;
        s->hp_x2_r1 = s->hp_x1_r1; s->hp_x1_r1 = inR; s->hp_y2_r1 = s->hp_y1_r1; s->hp_y1_r1 = hp1_r;

        float highR = s->hp_b0 * hp1_r + s->hp_b1 * s->hp_x1_r2 + s->hp_b2 * s->hp_x2_r2 - s->hp_a1 * s->hp_y1_r2 - s->hp_a2 * s->hp_y2_r2;
        s->hp_x2_r2 = s->hp_x1_r2; s->hp_x1_r2 = hp1_r; s->hp_y2_r2 = s->hp_y1_r2; s->hp_y1_r2 = highR;

        // 3. Mid Band (130 Hz - 10.5 kHz)
        float midL = inL - lowL - highL;
        float midR = inR - lowR - highR;

        // 4. Differential Side Signal (where Vocal is strictly 0)
        float side = 0.5f * (midL - midR);

        // 5. Branch A All-Pass Cascade (Left Channel 0° reference)
        float y_a1 = s->ap_a1 * (side - s->ap_y_a1) + s->ap_x_a1;
        s->ap_x_a1 = side; s->ap_y_a1 = y_a1;

        float y_a3 = s->ap_a3 * (y_a1 - s->ap_y_a3) + s->ap_x_a3;
        s->ap_x_a3 = y_a1; s->ap_y_a3 = y_a3;

        float sideA = s->ap_a5 * (y_a3 - s->ap_y_a5) + s->ap_x_a5;
        s->ap_x_a5 = y_a3; s->ap_y_a5 = sideA;

        // 6. Branch B All-Pass Cascade (Right Channel +90° orthogonal)
        float y_b2 = s->ap_a2 * (side - s->ap_y_b2) + s->ap_x_b2;
        s->ap_x_b2 = side; s->ap_y_b2 = y_b2;

        float sideB = s->ap_a4 * (y_b2 - s->ap_y_b4) + s->ap_x_b4;
        s->ap_x_b4 = y_b2; s->ap_y_b4 = sideB;

        // 7. Blend and Recombine (Full Bass + Orthogonal High-Fidelity Instrumental + Crystal Air)
        float finalMidL = midL * (1.0f - sup) + (sideA * makeupGain) * sup;
        float finalMidR = midR * (1.0f - sup) + (sideB * makeupGain) * sup;

        float outL = lowL + finalMidL + highL;
        float outR = lowR + finalMidR + highR;

        out[i * 2] = (short)fmaxf(-32768.0f, fminf(32767.0f, outL));
        out[i * 2 + 1] = (short)fmaxf(-32768.0f, fminf(32767.0f, outR));
    }
}

} // extern "C"

