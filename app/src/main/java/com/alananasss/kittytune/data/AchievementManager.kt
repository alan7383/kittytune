package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class Achievement(
    val id: String,
    val category: AchievementCategory,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val iconEmoji: String,
    val targetValue: Int,
    val isSecret: Boolean = false,
    val xpReward: Int = 10
)

enum class AchievementCategory(@StringRes val titleResId: Int) {
    TIME(R.string.ach_cat_time),
    VOLUME(R.string.ach_cat_volume),
    LOYALTY(R.string.ach_cat_loyalty),
    COLLECTION(R.string.ach_cat_collection),
    PLAYER(R.string.ach_cat_player),
    HARDCORE(R.string.ach_cat_hardcore),
    SECRET(R.string.ach_cat_secret)
}

data class AchievementProgress(
    val id: String,
    val currentValue: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long = 0
)

object AchievementManager {
    private const val PREFS_NAME = "achievements_prefs"
    private lateinit var prefs: SharedPreferences
    private lateinit var context: Context
    private val scope = CoroutineScope(Dispatchers.Main)

    val definitions = listOf(
        Achievement("time_1h", AchievementCategory.TIME, R.string.ach_time_1h_title, R.string.ach_time_1h_desc, "🎧", 3600, xpReward = 10),
        Achievement("time_24h", AchievementCategory.TIME, R.string.ach_time_24h_title, R.string.ach_time_24h_desc, "🌙", 86400, xpReward = 100),
        Achievement("time_100h", AchievementCategory.TIME, R.string.ach_time_100h_title, R.string.ach_time_100h_desc, "🔥", 360000, xpReward = 500),
        Achievement("time_500h", AchievementCategory.TIME, R.string.ach_time_500h_title, R.string.ach_time_500h_desc, "⚡", 1800000, xpReward = 2000),
        Achievement("time_1000h", AchievementCategory.TIME, R.string.ach_time_1000h_title, R.string.ach_time_1000h_desc, "🎖️", 3600000, xpReward = 5000),
        Achievement("time_2500h", AchievementCategory.TIME, R.string.ach_time_2500h_title, R.string.ach_time_2500h_desc, "🧘", 9000000, xpReward = 15000),
        Achievement("time_5000h", AchievementCategory.TIME, R.string.ach_time_5000h_title, R.string.ach_time_5000h_desc, "🌌", 18000000, xpReward = 50000),
        Achievement("time_10000h", AchievementCategory.TIME, R.string.ach_time_10000h_title, R.string.ach_time_10000h_desc, "🧠", 36000000, xpReward = 100000),
        Achievement("plays_1", AchievementCategory.VOLUME, R.string.ach_plays_1_title, R.string.ach_plays_1_desc, "🎵", 1, xpReward = 5),
        Achievement("plays_100", AchievementCategory.VOLUME, R.string.ach_plays_100_title, R.string.ach_plays_100_desc, "💿", 100, xpReward = 50),
        Achievement("plays_1000", AchievementCategory.VOLUME, R.string.ach_plays_1000_title, R.string.ach_plays_1000_desc, "🧭", 1000, xpReward = 500),
        Achievement("plays_5000", AchievementCategory.VOLUME, R.string.ach_plays_5000_title, R.string.ach_plays_5000_desc, "🌊", 5000, xpReward = 2000),
        Achievement("plays_10000", AchievementCategory.VOLUME, R.string.ach_plays_10000_title, R.string.ach_plays_10000_desc, "🤖", 10000, xpReward = 5000),
        Achievement("plays_20000", AchievementCategory.VOLUME, R.string.ach_plays_20000_title, R.string.ach_plays_20000_desc, "👁️", 20000, xpReward = 10000),
        Achievement("plays_50000", AchievementCategory.VOLUME, R.string.ach_plays_50000_title, R.string.ach_plays_50000_desc, "🔮", 50000, xpReward = 25000),
        Achievement("plays_100000", AchievementCategory.VOLUME, R.string.ach_plays_100000_title, R.string.ach_plays_100000_desc, "👑", 100000, xpReward = 100000),
        Achievement("streak_7", AchievementCategory.LOYALTY, R.string.ach_streak_7_title, R.string.ach_streak_7_desc, "🗓️", 7, xpReward = 100),
        Achievement("streak_30", AchievementCategory.LOYALTY, R.string.ach_streak_30_title, R.string.ach_streak_30_desc, "📅", 30, xpReward = 500),
        Achievement("streak_100", AchievementCategory.LOYALTY, R.string.ach_streak_100_title, R.string.ach_streak_100_desc, "💯", 100, xpReward = 2000),
        Achievement("streak_200", AchievementCategory.LOYALTY, R.string.ach_streak_200_title, R.string.ach_streak_200_desc, "⚔️", 200, xpReward = 5000),
        Achievement("streak_365", AchievementCategory.LOYALTY, R.string.ach_streak_365_title, R.string.ach_streak_365_desc, "🏆", 365, xpReward = 20000),
        Achievement("streak_500", AchievementCategory.LOYALTY, R.string.ach_streak_500_title, R.string.ach_streak_500_desc, "🛡️", 500, xpReward = 50000),
        Achievement("early_bird", AchievementCategory.LOYALTY, R.string.ach_early_bird_title, R.string.ach_early_bird_desc, "🌅", 10, xpReward = 100),
        Achievement("night_owl", AchievementCategory.LOYALTY, R.string.ach_night_owl_title, R.string.ach_night_owl_desc, "🦉", 10, xpReward = 100),
        Achievement("lunch_break", AchievementCategory.LOYALTY, R.string.ach_lunch_break_title, R.string.ach_lunch_break_desc, "🍔", 10, xpReward = 100),
        Achievement("liker_50", AchievementCategory.COLLECTION, R.string.ach_liker_50_title, R.string.ach_liker_50_desc, "💖", 50, xpReward = 100),
        Achievement("liker_1000", AchievementCategory.COLLECTION, R.string.ach_liker_1000_title, R.string.ach_liker_1000_desc, "🏆", 1000, xpReward = 2000),
        Achievement("liker_5000", AchievementCategory.COLLECTION, R.string.ach_liker_5000_title, R.string.ach_liker_5000_desc, "♾️", 5000, xpReward = 10000),
        Achievement("playlist_creator", AchievementCategory.COLLECTION, R.string.ach_playlist_creator_title, R.string.ach_playlist_creator_desc, "💾", 5, xpReward = 100),
        Achievement("playlist_god", AchievementCategory.COLLECTION, R.string.ach_playlist_god_title, R.string.ach_playlist_god_desc, "🏗️", 50, xpReward = 2500),
        Achievement("download_100", AchievementCategory.COLLECTION, R.string.ach_download_100_title, R.string.ach_download_100_desc, "📦", 100, xpReward = 500),
        Achievement("download_1000", AchievementCategory.COLLECTION, R.string.ach_download_1000_title, R.string.ach_download_1000_desc, "🗄️", 1000, xpReward = 5000),
        Achievement("skipper_100", AchievementCategory.PLAYER, R.string.ach_skipper_100_title, R.string.ach_skipper_100_desc, "⏭️", 100, xpReward = 100),
        Achievement("skipper_1000", AchievementCategory.PLAYER, R.string.ach_skipper_1000_title, R.string.ach_skipper_1000_desc, "🙅", 1000, xpReward = 1000),
        Achievement("bass_addict", AchievementCategory.PLAYER, R.string.ach_bass_addict_title, R.string.ach_bass_addict_desc, "🤯", 36000, xpReward = 2000),
        Achievement("speed_demon", AchievementCategory.PLAYER, R.string.ach_speed_demon_title, R.string.ach_speed_demon_desc, "🏎️", 3600, xpReward = 500),
        Achievement("social_star", AchievementCategory.PLAYER, R.string.ach_social_star_title, R.string.ach_social_star_desc, "🌐", 50, xpReward = 1000),
        Achievement("obsessed_50", AchievementCategory.HARDCORE, R.string.ach_obsessed_50_title, R.string.ach_obsessed_50_desc, "🔄", 50, xpReward = 1000),
        Achievement("obsessed_200", AchievementCategory.HARDCORE, R.string.ach_obsessed_200_title, R.string.ach_obsessed_200_desc, "😵‍💫", 200, xpReward = 10000),
        Achievement("night_shift_pro", AchievementCategory.HARDCORE, R.string.ach_night_shift_pro_title, R.string.ach_night_shift_pro_desc, "🧛", 28800, xpReward = 15000), // 8 hours
        Achievement("marathon", AchievementCategory.HARDCORE, R.string.ach_marathon_title, R.string.ach_marathon_desc, "🏃", 28800, xpReward = 5000),
        Achievement("no_skip_50", AchievementCategory.HARDCORE, R.string.ach_no_skip_50_title, R.string.ach_no_skip_50_desc, "🧘", 50, xpReward = 1000),
        Achievement("developer", AchievementCategory.SECRET, R.string.ach_developer_title, R.string.ach_secret_desc, "💻", 10, isSecret = true, xpReward = 1000),
        Achievement("weekend_warrior", AchievementCategory.SECRET, R.string.ach_weekend_warrior_title, R.string.ach_secret_desc, "🎉", 2, isSecret = true, xpReward = 200),
        Achievement("ghost", AchievementCategory.SECRET, R.string.ach_ghost_title, R.string.ach_secret_desc, "👻", 86400, isSecret = true, xpReward = 5000),
        Achievement("lucky7", AchievementCategory.SECRET, R.string.ach_lucky7_title, R.string.ach_secret_desc, "🎰", 7, isSecret = true, xpReward = 7777),
        Achievement("glitch", AchievementCategory.SECRET, R.string.ach_glitch_title, R.string.ach_secret_desc, "👾", 1, isSecret = true, xpReward = 1337)
    )

    private val _isAllUnlocked = MutableStateFlow(false)
    val isAllUnlocked = _isAllUnlocked.asStateFlow()

    private val _progressFlow = MutableStateFlow<Map<String, AchievementProgress>>(emptyMap())
    val progressFlow = _progressFlow.asStateFlow()

    fun init(context: Context) {
        this.context = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadProgress()
        checkCompletion()
    }

    private fun loadProgress() {
        val newMap = mutableMapOf<String, AchievementProgress>()
        definitions.forEach { def ->
            val current = prefs.getInt("curr_${def.id}", 0)
            val unlocked = prefs.getBoolean("unlocked_${def.id}", false)
            val time = prefs.getLong("time_${def.id}", 0)
            newMap[def.id] = AchievementProgress(def.id, current, unlocked, time)
        }
        _progressFlow.value = newMap
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        _isAllUnlocked.value = false
        loadProgress()
    }

    fun increment(id: String, amount: Int = 1) {
        val def = definitions.find { it.id == id } ?: return
        val currentProgress = _progressFlow.value[id] ?: AchievementProgress(id, 0, false)
        if (currentProgress.isUnlocked) return
        val newValue = currentProgress.currentValue + amount
        if (newValue >= def.targetValue) {
            unlock(id)
        } else {
            prefs.edit().putInt("curr_$id", newValue).apply()
            updateFlow(id, currentProgress.copy(currentValue = newValue))
        }
    }

    fun addPlayTime(seconds: Int, isGuest: Boolean, speed: Float) {
        definitions.filter { it.category == AchievementCategory.TIME }.forEach { increment(it.id, seconds) }
        if (isGuest) increment("ghost", seconds)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 6) {
            increment("night_shift_pro", seconds)
        } else {
            if (_progressFlow.value["night_shift_pro"]?.isUnlocked == false) {
                prefs.edit().putInt("curr_night_shift_pro", 0).apply()
                updateFlow("night_shift_pro", AchievementProgress("night_shift_pro", 0, false))
            }
        }

        increment("marathon", seconds)
        if (speed >= 1.2f) increment("speed_demon", seconds)
    }

    fun checkDailyStreak() {
        val now = Calendar.getInstance()
        val currentDayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val currentYear = now.get(Calendar.YEAR)

        val lastDayOfYear = prefs.getInt("last_streak_day_of_year", -1)
        val lastYear = prefs.getInt("last_streak_year", -1)

        var currentStreak = prefs.getInt("current_streak_count", 0)
        val isNewDay = currentYear != lastYear || currentDayOfYear != lastDayOfYear

        if (isNewDay) {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isConsecutive = (lastYear == yesterday.get(Calendar.YEAR) && lastDayOfYear == yesterday.get(Calendar.DAY_OF_YEAR))

            currentStreak = if (isConsecutive) currentStreak + 1 else 1

            prefs.edit()
                .putInt("last_streak_day_of_year", currentDayOfYear)
                .putInt("last_streak_year", currentYear)
                .putInt("current_streak_count", currentStreak)
                .apply()

            scope.launch {
                val popupPrefs = PlayerPreferences(context)
                if (popupPrefs.getAchievementPopupsEnabled()) {
                    AchievementNotificationManager.showNotification(
                        AchievementNotification(
                            title = context.getString(R.string.streak_popup_title),
                            subtitle = context.getString(R.string.streak_popup_subtitle, currentStreak),
                            iconEmoji = "🔥",
                            xpReward = null
                        )
                    )
                }
            }

            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                increment("weekend_warrior")
            }
        }

        val streakDefs = definitions.filter { it.category == AchievementCategory.LOYALTY && it.id.startsWith("streak_") }
        streakDefs.forEach { def ->
            if (_progressFlow.value[def.id]?.isUnlocked == false) {
                prefs.edit().putInt("curr_${def.id}", currentStreak).apply()
                updateFlow(def.id, AchievementProgress(def.id, currentStreak, false))
            }
            if (currentStreak >= def.targetValue) {
                unlock(def.id)
            }
        }
    }

    fun checkTrackNameSecret(title: String) {
        if (title.contains("777") || title.contains("Lucky", ignoreCase = true)) increment("lucky7", 1)
        if (title.contains("Error") || title.contains("Glitch", ignoreCase = true)) increment("glitch", 1)
    }

    fun trackSkipped() {
        prefs.edit().putInt("curr_no_skip_50", 0).apply()
        updateFlow("no_skip_50", AchievementProgress("no_skip_50", 0, false))
    }

    fun resetSessionAchievements() {
        // Seuls les succès qui DOIVENT être faits en une seule session sont listés ici.
        // Vampire n'en fait plus partie car sa logique de reset est basée sur l'heure.
        val sessionAchievements = listOf("marathon", "no_skip_50")
        sessionAchievements.forEach { id ->
            if (_progressFlow.value[id]?.isUnlocked == false) {
                prefs.edit().putInt("curr_$id", 0).apply()
                updateFlow(id, AchievementProgress(id, 0, false))
            }
        }
    }

    private fun unlock(id: String) {
        if (_progressFlow.value[id]?.isUnlocked == true) return
        val now = System.currentTimeMillis()
        val def = definitions.find { it.id == id } ?: return

        scope.launch {
            val playerPrefs = PlayerPreferences(context)
            if (playerPrefs.getAchievementPopupsEnabled()) {
                AchievementNotificationManager.showNotification(
                    AchievementNotification(
                        title = context.getString(R.string.achievement_unlocked),
                        subtitle = context.getString(def.titleResId),
                        iconEmoji = def.iconEmoji,
                        xpReward = def.xpReward
                    )
                )
            }
        }

        prefs.edit()
            .putInt("curr_$id", def.targetValue)
            .putBoolean("unlocked_$id", true)
            .putLong("time_$id", now)
            .apply()

        val newProgress = AchievementProgress(id, def.targetValue, true, now)
        updateFlow(id, newProgress)
        checkCompletion()
    }

    private fun updateFlow(id: String, progress: AchievementProgress) {
        _progressFlow.update { it.toMutableMap().apply { put(id, progress) } }
    }

    private fun checkCompletion() {
        val unlockedCount = _progressFlow.value.values.count { it.isUnlocked }
        val totalCount = definitions.size
        if (unlockedCount >= totalCount && !_isAllUnlocked.value) {
            _isAllUnlocked.value = true
        }
    }

    fun getLevelInfo(): Triple<Int, Int, Int> {
        val totalXp = _progressFlow.value.values.filter { it.isUnlocked }.sumOf { prog ->
            definitions.find { it.id == prog.id }?.xpReward ?: 0
        }
        var level = 1
        var xpForNext = 1000
        var xpAccumulated = 0
        while (totalXp >= xpAccumulated + xpForNext) {
            xpAccumulated += xpForNext
            level++
            xpForNext = (xpForNext * 1.2).toInt()
        }
        return Triple(level, totalXp - xpAccumulated, xpForNext)
    }
}