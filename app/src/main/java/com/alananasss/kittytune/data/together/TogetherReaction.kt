package com.alananasss.kittytune.data.together

internal enum class TogetherReaction(val wireId: String, val emoji: String) {
    HEART("heart", "❤️"),
    FIRE("fire", "🔥"),
    PAWS("paws", "🐾"),
    PARTY("party", "🎉"),
    CAT("cat", "🐱"),
    DROOLING("drooling", "🤤"),
    MIND_BLOWN("mind_blown", "🤯"),
    LAUGHING("laughing", "😂"),
    HEART_EYES("heart_eyes", "😍"),
    MELTING("melting", "🫠"),
    SKULL("skull", "💀"),
    SPARKLES("sparkles", "✨"),
}

internal data class TogetherReactionEvent(val code: String, val reaction: TogetherReaction)

private const val REACTION_REFILL_NANOS = 20_000_000L
private const val REACTION_BURST_CAPACITY = 120
private const val REACTION_MAX_CREDIT = REACTION_REFILL_NANOS * REACTION_BURST_CAPACITY

internal class TogetherReactionLimiter {
    private var credit = REACTION_MAX_CREDIT
    private var lastCheckNanos: Long? = null

    fun tryAcquire(nowNanos: Long): Boolean {
        lastCheckNanos?.let { lastCheck ->
            val elapsed = (nowNanos - lastCheck).coerceIn(0L, REACTION_MAX_CREDIT)
            credit = minOf(REACTION_MAX_CREDIT, credit + elapsed)
        }
        lastCheckNanos = nowNanos
        if (credit < REACTION_REFILL_NANOS) return false
        credit -= REACTION_REFILL_NANOS
        return true
    }
}
