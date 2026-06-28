package com.kukurigu.sunalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kukurigu.sunalarm.ServiceLocator
import com.kukurigu.sunalarm.data.AlarmConfig
import com.kukurigu.sunalarm.data.AppLocation
import com.kukurigu.sunalarm.solar.DawnPhase
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules and cancels the device alarms backing each [AlarmConfig].
 */
interface AlarmScheduler {

    /** Schedule [config]'s next trigger (or cancel it if the alarm is disabled). */
    fun scheduleNext(config: AlarmConfig, location: AppLocation)

    /** Cancel any pending alarm for [phase]. */
    fun cancel(phase: DawnPhase)

    /** Read the repository and reschedule every enabled alarm. */
    fun rescheduleAll()

    /** Fire a one-shot test alarm a few seconds from now to preview the ring experience. */
    fun scheduleTest()
}

/**
 * [AlarmScheduler] backed by the platform [AlarmManager].
 *
 * Each enabled alarm is scheduled with [AlarmManager.setAlarmClock] — the only
 * API that is reliably exact and Doze-exempt for wake-up alarms — using a per
 * phase operation [PendingIntent] targeting [AlarmReceiver] and a full-screen
 * "show" [PendingIntent] targeting [AlarmActivity].
 */
class AlarmManagerScheduler(context: Context) : AlarmScheduler {

    private val appContext: Context = context.applicationContext
    private val alarmManager: AlarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleNext(config: AlarmConfig, location: AppLocation) {
        if (!config.enabled) {
            cancel(config.phase)
            return
        }

        val zone: ZoneId = ZoneId.systemDefault()
        val trigger: Instant = NextAlarmComputer.nextTrigger(
            config = config,
            location = location,
            after = Instant.now(),
            zone = zone,
        ) ?: run {
            Log.w(TAG, "No upcoming trigger for ${config.phase}; nothing scheduled.")
            return
        }

        scheduleAt(config, trigger.toEpochMilli())
    }

    /**
     * Schedules [config] to fire at [triggerAtMillis] (epoch millis). Shared by
     * [scheduleNext] and one-shot scheduling such as snooze.
     *
     * When [snooze] is true a dedicated request code is used so the snooze alarm
     * occupies its own slot and does not overwrite the regular daily alarm.
     */
    fun scheduleAt(config: AlarmConfig, triggerAtMillis: Long, snooze: Boolean = false) {
        val requestCode =
            if (snooze) AlarmConstants.snoozeRequestCode(config.phase)
            else AlarmConstants.requestCode(config.phase)
        val operationPi: PendingIntent =
            buildOperationPendingIntent(config, triggerAtMillis, requestCode)
        val showPi: PendingIntent = buildShowPendingIntent(config, triggerAtMillis)

        // setAlarmClock is exempt from Doze and is the most reliable exact API;
        // only usable when exact alarms are permitted on API 31+.
        if (canScheduleExact()) {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPi)
            alarmManager.setAlarmClock(info, operationPi)
            Log.d(TAG, "Scheduled exact alarm ${config.phase} at $triggerAtMillis")
        } else {
            // Fall back to an inexact-but-Doze-tolerant alarm. The user is still
            // routed to grant exact-alarm permission from the UI.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operationPi,
            )
            Log.w(
                TAG,
                "Exact alarms not permitted; scheduled inexact ${config.phase} at $triggerAtMillis",
            )
        }
    }

    override fun cancel(phase: DawnPhase) {
        val intent = fireIntent()
        val pi = PendingIntent.getBroadcast(
            appContext,
            AlarmConstants.requestCode(phase),
            intent,
            cancelFlags(),
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
        Log.d(TAG, "Cancelled alarm for $phase")
    }

    override fun rescheduleAll() {
        ServiceLocator.ensureInit(appContext)
        val (alarms, location) = runBlocking {
            val repo = ServiceLocator.repository
            repo.getAlarmsOnce() to repo.getLocationOnce()
        }
        if (location == null) {
            // Without a location the solar math cannot run; leave alarms untouched.
            Log.w(TAG, "rescheduleAll skipped: no location set.")
            return
        }
        alarms.forEach { config -> scheduleNext(config, location) }
    }

    override fun scheduleTest() {
        val config = AlarmConfig(
            phase = DawnPhase.SUNRISE,
            enabled = true,
            label = "Test crow",
        )
        // Uses the snooze request-code slot so it never disturbs the real daily alarm.
        scheduleAt(config, System.currentTimeMillis() + TEST_DELAY_MS, snooze = true)
    }

    // ---------------------------------------------------------------------
    // PendingIntent / Intent construction
    // ---------------------------------------------------------------------

    private fun buildOperationPendingIntent(
        config: AlarmConfig,
        triggerAtMillis: Long,
        requestCode: Int,
    ): PendingIntent {
        val intent = fireIntent().apply {
            putExtra(AlarmConstants.EXTRA_PHASE, config.phase.name)
            putExtra(AlarmConstants.EXTRA_LABEL, config.label)
            putExtra(AlarmConstants.EXTRA_TIME_MILLIS, triggerAtMillis)
            // Snapshot the ring settings so AlarmService needs no disk read at fire time.
            putExtra(AlarmConstants.EXTRA_VIBRATE, config.vibrate)
            putExtra(AlarmConstants.EXTRA_SNOOZE_MINUTES, config.snoozeMinutes)
            config.soundUri?.let { putExtra(AlarmConstants.EXTRA_SOUND_URI, it) }
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            mutableFlags(),
        )
    }

    private fun buildShowPendingIntent(
        config: AlarmConfig,
        triggerAtMillis: Long,
    ): PendingIntent {
        val intent = Intent(appContext, AlarmActivity::class.java).apply {
            action = AlarmConstants.ACTION_FIRE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmConstants.EXTRA_PHASE, config.phase.name)
            putExtra(AlarmConstants.EXTRA_LABEL, config.label)
            putExtra(AlarmConstants.EXTRA_TIME_MILLIS, triggerAtMillis)
        }
        return PendingIntent.getActivity(
            appContext,
            AlarmConstants.showRequestCode(config.phase),
            intent,
            mutableFlags(),
        )
    }

    private fun fireIntent(): Intent =
        Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmConstants.ACTION_FIRE
        }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val TEST_DELAY_MS = 8_000L

        fun mutableFlags(): Int =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun cancelFlags(): Int =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    }
}
