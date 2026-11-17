package com.alananasss.kittytune.ui.widget.pet

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alananasss.kittytune.ui.widget.PetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * New Year's Eve: on December 31 the cat waits with a party hat while the widget shows a live
 * countdown to midnight. The last ten seconds it holds up a sign from 10 to 0 (the widget is
 * refreshed on time by alarms), then fireworks and the new year's banner. January 1 is a party.
 */
object PetNewYear {
    const val COUNTDOWN_SECONDS = 10
    private const val FIREWORKS_FIRST_MS = 10 * 60_000L

    enum class Phase { NONE, EVE, COUNTDOWN, FIREWORKS, NEW_YEAR_DAY }

    /** Midnight starting the next year, in ms, if [now] is on December 31; else null. */
    fun nextMidnight(now: Long): Long? {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        if (c.get(Calendar.MONTH) != Calendar.DECEMBER || c.get(Calendar.DAY_OF_MONTH) != 31) return null
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun phase(now: Long): Phase {
        nextMidnight(now)?.let { midnight ->
            return if (midnight - now <= COUNTDOWN_SECONDS * 1000L + 500) Phase.COUNTDOWN else Phase.EVE
        }
        val c = Calendar.getInstance().apply { timeInMillis = now }
        if (c.get(Calendar.MONTH) == Calendar.JANUARY && c.get(Calendar.DAY_OF_MONTH) == 1) {
            val sinceMidnight = c.get(Calendar.HOUR_OF_DAY) * 3_600_000L + c.get(Calendar.MINUTE) * 60_000L + c.get(Calendar.SECOND) * 1000L
            return if (sinceMidnight < FIREWORKS_FIRST_MS) Phase.FIREWORKS else Phase.NEW_YEAR_DAY
        }
        return Phase.NONE
    }

    /** The year being celebrated (the one starting at the next / last midnight). */
    fun celebratedYear(now: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return if (c.get(Calendar.MONTH) == Calendar.DECEMBER) c.get(Calendar.YEAR) + 1 else c.get(Calendar.YEAR)
    }

    /** Countdown frames from the current second down to 0, then fireworks. */
    fun countdownFrames(now: Long): List<String> {
        val midnight = nextMidnight(now) ?: return fireworksFrames(now)
        // rounded, so a refresh landing a hair early or late still starts on the right number
        val left = ((midnight - now + 500) / 1000).toInt().coerceIn(0, COUNTDOWN_SECONDS)
        val out = ArrayList<String>()
        for (n in left downTo 0) out += "count_$n"
        val fw = fireworksFrames(now)
        var i = 0
        while (out.size < 8) out += fw[i++ % fw.size]
        return out.take(8)
    }

    fun fireworksFrames(now: Long): List<String> {
        val year = celebratedYear(now).coerceIn(2027, 2030)
        return listOf(1, 2, 3, 2, 1, 2, 3, 3).map { "ny_${year}_$it" }
    }

    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    /** Refresh moments: last minute (live clock -> text), T-10s (sign), T-2s (last batch), midnight. */
    // the 2nd batch lands just before the first one (10..3, 8 frames) would loop back to "10"
    private fun targets(midnight: Long) = listOf(midnight - 60_000L, midnight - COUNTDOWN_SECONDS * 1000L, midnight - 2_150L, midnight + 300L)

    /**
     * Refreshes the widget on the second around midnight. While the app process is alive (music
     * playing, app open) in-process timers are exact without any permission; exact alarms back them
     * up when Android allows them (otherwise a tight window, which the system may defer).
     */
    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val midnight = nextMidnight(now) ?: return
        val appContext = context.applicationContext
        if (midnight - now < 3 * 3_600_000L && timerJob?.isActive != true) {
            timerJob = timerScope.launch {
                for (at in targets(midnight)) {
                    val wait = at - System.currentTimeMillis()
                    if (wait < 0) continue
                    delay(wait)
                    PetWidget.refresh(appContext, force = true, reroll = false)
                }
            }
        }
        val am = appContext.getSystemService(AlarmManager::class.java) ?: return
        targets(midnight).forEachIndexed { i, at ->
            if (at <= now) return@forEachIndexed
            val pi = PendingIntent.getBroadcast(
                appContext, 7_100 + i,
                Intent(appContext, PetNewYearReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 1_000L, pi)
        }
    }

    /** The live clock is shown until the last minute (a Chronometer can't stop at zero by itself). */
    fun showLiveClock(midnight: Long, now: Long) = midnight - now > 60_000L
}

/** Fired by [PetNewYear.schedule]: redraw the cat on the second. */
class PetNewYearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PetWidget.refresh(context, force = true, reroll = false)
    }
}
