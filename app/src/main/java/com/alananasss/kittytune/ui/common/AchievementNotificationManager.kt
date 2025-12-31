package com.alananasss.kittytune.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AchievementNotification(
    val title: String,
    val subtitle: String,
    val iconEmoji: String?,
    val xpReward: Int? = null
)

object AchievementNotificationManager {
    private val _notifications = MutableSharedFlow<AchievementNotification>(replay = 0)
    val notifications = _notifications.asSharedFlow()

    suspend fun showNotification(notification: AchievementNotification) {
        _notifications.emit(notification)
    }
}