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

} // extern "C"
