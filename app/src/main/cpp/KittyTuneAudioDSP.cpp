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

static const float kLimiterThresholdLinear = 0.8912509381337455f; // -1 dBFS in linear
static const float kLimiterAttackMs = 5.0f;
static const float kLimiterReleaseMs = 100.0f;

typedef struct {
    ebur128_state* ebur128;
    int sampleRate;
    int channels;

    float currentGainLinear;
    float targetGainLinear;
    float targetLUFS;

    float* delayBuffer;
    int delaySamples;
    int delayWritePos;

    float envelope;
    float smoothedGain;
    float attackCoeff;
    float releaseCoeff;
    float envReleaseCoeff;     // fast release for envelope (instant rise)

    float shortTermLoudness;
    float integratedLoudness;
    float truePeakLinear;
    float maxTruePeakDb;

    float* processBuffer;
    int processBufferCapacity;

    long long frameCount;
    long long logInterval;
    int debugLogging;
} KittyTuneDSPState;

static float linearToDb(float linear) {
    if (linear <= 0.0f) return -120.0f;
    return 20.0f * log10f(linear);
}

static float dbToLinear(float db) {
    return powf(10.0f, db / 20.0f);
}

static void initLimiter(KittyTuneDSPState* s) {
    s->delaySamples = (int)(s->sampleRate * kLimiterAttackMs / 1000.0f);
    if (s->delaySamples < 1) s->delaySamples = 1;

    s->delayBuffer = (float*)calloc(s->delaySamples * s->channels, sizeof(float));
    s->delayWritePos = 0;
    s->envelope = 0.0f;
    s->smoothedGain = 1.0f;

    float attackTime = kLimiterAttackMs / 1000.0f;
    float releaseTime = kLimiterReleaseMs / 1000.0f;
    s->attackCoeff = expf(-1.0f / ((float)s->sampleRate * attackTime));
    s->releaseCoeff = expf(-1.0f / ((float)s->sampleRate * releaseTime));

    float envRelease = 0.02f; // 20ms release for envelope
    s->envReleaseCoeff = expf(-1.0f / ((float)s->sampleRate * envRelease));

    s->currentGainLinear = 1.0f;
    s->targetGainLinear = 1.0f;
}

static void destroyLimiter(KittyTuneDSPState* s) {
    free(s->delayBuffer);
    s->delayBuffer = NULL;
}

static float processLimiterSample(KittyTuneDSPState* s, float gainedInput, int channelIndex) {
    int idx = s->delayWritePos * s->channels + channelIndex;
    s->delayBuffer[idx] = gainedInput;

    float absSample = fabsf(gainedInput);
    s->envelope = (absSample > s->envelope) ? absSample : s->envReleaseCoeff * s->envelope;

    float targetGain = kLimiterThresholdLinear / fmaxf(s->envelope, kLimiterThresholdLinear);
    if (targetGain < s->smoothedGain) {
        s->smoothedGain = targetGain;  // instant attack: lookahead prevents audible click
    } else {
        s->smoothedGain = s->releaseCoeff * s->smoothedGain
            + (1.0f - s->releaseCoeff) * targetGain;
    }

    int readIdx = ((s->delayWritePos + 1) % s->delaySamples) * s->channels + channelIndex;
    float delayed = s->delayBuffer[readIdx];

    return delayed * s->smoothedGain;
}

static void smoothGain(KittyTuneDSPState* s) {
    float gainCoeff = (s->targetGainLinear < s->currentGainLinear)
        ? s->attackCoeff : s->releaseCoeff;
    s->currentGainLinear = gainCoeff * s->currentGainLinear
        + (1.0f - gainCoeff) * s->targetGainLinear;
}

static KittyTuneDSPState* createDspState(int channels, int sampleRate, int mode, float targetLUFS) {
    KittyTuneDSPState* s = (KittyTuneDSPState*)calloc(1, sizeof(KittyTuneDSPState));
    if (!s) {
        LOGE("Failed to allocate DSP state");
        return NULL;
    }

    s->sampleRate = sampleRate;
    s->channels = channels;
    s->targetLUFS = targetLUFS;
    s->shortTermLoudness = -70.0f;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;
    s->maxTruePeakDb = -120.0f;
    s->frameCount = 0;
    s->logInterval = (long long)sampleRate / 2; // log every 0.5s

    s->processBufferCapacity = 16384 * channels;
    s->processBuffer = (float*)malloc(s->processBufferCapacity * sizeof(float));

    s->ebur128 = ebur128_init((unsigned int)channels, (unsigned long)sampleRate, mode);
    if (!s->ebur128) {
        LOGE("Failed to initialize ebur128 (ch=%d, sr=%d)", channels, sampleRate);
        free(s);
        return NULL;
    }

    initLimiter(s);
    return s;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeInit(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {

    int mode = EBUR128_MODE_I | EBUR128_MODE_S | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    KittyTuneDSPState* s = createDspState(channels, sampleRate, mode, -14.0f);
    if (!s) return 0;

    s->debugLogging = 1;
    LOGI("DSP initialized: ch=%d, sr=%d, mode=I+S+TP+HIST", channels, sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;

    if (s->ebur128) {
        ebur128_destroy(&s->ebur128);
        s->ebur128 = NULL;
    }

    destroyLimiter(s);
    if (s->processBuffer) {
        free(s->processBuffer);
        s->processBuffer = NULL;
    }
    free(s);
    LOGI("DSP destroyed");
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeSetTargetLevel(
    JNIEnv* env, jclass clazz, jlong handle, jfloat targetLUFS) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    s->targetLUFS = targetLUFS;
    LOGI("Target LUFS set to %.1f", targetLUFS);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeResetLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    int mode = EBUR128_MODE_I | EBUR128_MODE_S | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    ebur128_destroy(&s->ebur128);
    s->ebur128 = ebur128_init((unsigned int)s->channels, (unsigned long)s->sampleRate, mode);
    s->shortTermLoudness = -70.0f;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;
    s->maxTruePeakDb = -120.0f;

    s->envelope = 0.0f;
    s->smoothedGain = 1.0f;
    s->currentGainLinear = 1.0f;
    s->targetGainLinear = 1.0f;
}

// Returns max true peak across all channels in dBTP
static float computeMaxTruePeakDb(KittyTuneDSPState* s) {
    double maxTp = 0.0;
    for (int ch = 0; ch < s->channels; ch++) {
        double tp;
        if (ebur128_true_peak(s->ebur128, (unsigned int)ch, &tp) == EBUR128_SUCCESS && isfinite(tp)) {
            if (tp > maxTp) maxTp = tp;
        }
    }
    if (maxTp <= 0.0) return -120.0f;
    return (float)(20.0 * log10(maxTp));
}

// Shared chunk-processing core used by both the playback path (nativeProcessShort)
// and the diagnostics harness. forcedGainDb = NaN means "auto" (compute from LUFS +
// headroom, as in production). Otherwise the given fixed gain in dB is used so the
// limiter can be stressed in isolation.
static void processChunk(KittyTuneDSPState* s, short* in, short* out, int numFrames, float forcedGainDb) {
    int totalSamples = numFrames * s->channels;

    if (totalSamples > s->processBufferCapacity) {
        float* newBuf = (float*)realloc(s->processBuffer, totalSamples * sizeof(float));
        if (!newBuf) return;
        s->processBuffer = newBuf;
        s->processBufferCapacity = totalSamples;
    }
    float* floatBuf = s->processBuffer;

    for (int i = 0; i < totalSamples; i++) {
        floatBuf[i] = (float)in[i] / 32768.0f;
    }

    ebur128_add_frames_float(s->ebur128, floatBuf, (size_t)numFrames);

    double stLoudness = -HUGE_VAL;
    if (ebur128_loudness_shortterm(s->ebur128, &stLoudness) == EBUR128_SUCCESS) {
        if (isfinite((float)stLoudness) && stLoudness > -HUGE_VAL) {
            s->shortTermLoudness = (float)stLoudness;
        }
    }

    float gainDb;
    int isForced = (forcedGainDb != forcedGainDb) ? 0 : 1;  // NaN check
    if (isForced) {
        gainDb = forcedGainDb;
    } else {
        gainDb = s->targetLUFS - s->shortTermLoudness;
        float maxTpDb = computeMaxTruePeakDb(s);

        if (gainDb > 0.0f && maxTpDb > -120.0f) {
            float maxGainForHeadroom = -1.0f - maxTpDb;
            if (gainDb > maxGainForHeadroom) {
                gainDb = maxGainForHeadroom;
            }
        }

        if (gainDb < -24.0f) gainDb = -24.0f;
        if (gainDb > 24.0f) gainDb = 24.0f;
    }

    if (!isfinite(gainDb)) gainDb = 0.0f;
    s->targetGainLinear = dbToLinear(gainDb);

    for (int frame = 0; frame < numFrames; frame++) {
        smoothGain(s);

        for (int ch = 0; ch < s->channels; ch++) {
            int sampleIdx = frame * s->channels + ch;
            float inputSample = floatBuf[sampleIdx];
            float gainedInput = inputSample * s->currentGainLinear;
            float processed = processLimiterSample(s, gainedInput, ch);

            if (processed > 1.0f) processed = 1.0f;
            if (processed < -1.0f) processed = -1.0f;
            out[sampleIdx] = (short)(processed * 32767.0f);
        }

        s->delayWritePos = (s->delayWritePos + 1) % s->delaySamples;
    }

    double integrated = -HUGE_VAL;
    if (ebur128_loudness_global(s->ebur128, &integrated) == EBUR128_SUCCESS) {
        if (isfinite((float)integrated) && integrated > -HUGE_VAL) {
            s->integratedLoudness = (float)integrated;
        }
    }

    s->maxTruePeakDb = computeMaxTruePeakDb(s);

    s->frameCount += numFrames;
    if (s->debugLogging && s->frameCount >= s->logInterval) {
        float currentDb = linearToDb(s->currentGainLinear);
        float limiterDb = linearToDb(s->smoothedGain);
        LOGI("LUFS short=%.1f integrated=%.1f | gain=%.1fdB lim=%.1fdB | TP=%.1fdB target=%.1f%s",
             s->shortTermLoudness, s->integratedLoudness,
             currentDb, limiterDb,
             s->maxTruePeakDb, s->targetLUFS,
             isForced ? " (forced)" : "");
        s->frameCount = 0;
    }
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeProcessShort(
    JNIEnv* env, jclass clazz, jlong handle,
    jobject inputBuffer, jobject outputBuffer, jint numFrames, jint inOffset) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    short* in = (short*)((char*)env->GetDirectBufferAddress(inputBuffer) + inOffset);
    short* out = (short*)env->GetDirectBufferAddress(outputBuffer);
    if (!in || !out) return;

    processChunk(s, in, out, numFrames, NAN);
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeGetShortTermLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -70.0f;
    return s->shortTermLoudness;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeGetIntegratedLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -70.0f;
    return s->integratedLoudness;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeGetTruePeakDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return -120.0f;
    return s->maxTruePeakDb;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_ui_player_audio_R128AudioProcessor_nativeGetCurrentGainDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return 0.0f;
    return linearToDb(s->currentGainLinear);
}

// --- Scanner functions ---

JNIEXPORT jlong JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeCreateAnalyzer(
    JNIEnv* env, jclass clazz, jint channels, jint sampleRate) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)calloc(1, sizeof(KittyTuneDSPState));
    if (!s) return 0;

    s->sampleRate = sampleRate;
    s->channels = channels;
    s->integratedLoudness = -70.0f;
    s->truePeakLinear = 0.0f;

    int mode = EBUR128_MODE_I | EBUR128_MODE_TRUE_PEAK | EBUR128_MODE_HISTOGRAM;
    s->ebur128 = ebur128_init((unsigned int)channels, (unsigned long)sampleRate, mode);
    if (!s->ebur128) {
        free(s);
        return 0;
    }

    LOGI("Scanner initialized: ch=%d, sr=%d", channels, sampleRate);
    return (jlong)(intptr_t)s;
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeDestroyAnalyzer(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s) return;
    if (s->ebur128) {
        ebur128_destroy(&s->ebur128);
    }
    if (s->processBuffer) {
        free(s->processBuffer);
    }
    free(s);
}

JNIEXPORT void JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeAddFramesFloat(
    JNIEnv* env, jclass clazz, jlong handle,
    jfloatArray samples, jint numFrames) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return;

    jfloat* data = env->GetFloatArrayElements(samples, NULL);
    if (!data) return;

    ebur128_add_frames_float(s->ebur128, data, (size_t)numFrames);

    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedLoudness(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
    if (!s || !s->ebur128) return -70.0f;

    double integrated = -HUGE_VAL;
    if (ebur128_loudness_global(s->ebur128, &integrated) == EBUR128_SUCCESS) {
        if (isfinite((float)integrated) && integrated > -HUGE_VAL) {
            return (jfloat)integrated;
        }
    }
    return -70.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_alananasss_kittytune_data_AudioScannerManager_nativeGetAnalyzedTruePeakDb(
    JNIEnv* env, jclass clazz, jlong handle) {

    KittyTuneDSPState* s = (KittyTuneDSPState*)(intptr_t)handle;
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

