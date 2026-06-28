package com.kukurigu.sunalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kukurigu.sunalarm.ServiceLocator
import com.kukurigu.sunalarm.solar.DawnPhase
import kotlinx.coroutines.runBlocking

/**
 * Broadcast receiver invoked by [android.app.AlarmManager] when a scheduled
 * dawn-phase alarm fires.
 *
 * On fire it:
 *  1. starts [AlarmService] as a foreground service to play sound + vibration
 *     and post the full-screen alarm notification, and
 *  2. reschedules the same phase for its next occurrence, since dawn times shift
 *     daily with the sun.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmConstants.ACTION_FIRE) return

        ServiceLocator.ensureInit(context)

        val phaseName: String? = intent.getStringExtra(AlarmConstants.EXTRA_PHASE)
        val phase: DawnPhase? = phaseName?.let { runCatching { DawnPhase.valueOf(it) }.getOrNull() }
        if (phase == null) {
            Log.w(TAG, "Received alarm with unknown phase: $phaseName")
            return
        }

        val label: String = intent.getStringExtra(AlarmConstants.EXTRA_LABEL) ?: phase.title
        val timeMillis: Long =
            intent.getLongExtra(AlarmConstants.EXTRA_TIME_MILLIS, System.currentTimeMillis())
        val vibrate: Boolean = intent.getBooleanExtra(AlarmConstants.EXTRA_VIBRATE, true)
        val soundUri: String? = intent.getStringExtra(AlarmConstants.EXTRA_SOUND_URI)
        val snoozeMinutes: Int =
            intent.getIntExtra(AlarmConstants.EXTRA_SNOOZE_MINUTES, AlarmConstants.DEFAULT_SNOOZE_MINUTES)

        // Kick off the foreground alarm service immediately so the platform sees
        // a foreground service start within the broadcast window. The ring settings
        // ride along on the intent so the service performs no disk read at fire time.
        val serviceIntent =
            AlarmService.startIntent(context, phase, label, timeMillis, vibrate, soundUri, snoozeMinutes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Reschedule this phase's next occurrence. Use goAsync so the short
        // repository read + reschedule can complete reliably off the main thread.
        val pendingResult = goAsync()
        Thread {
            try {
                rescheduleNext(context, phase)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to reschedule $phase", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun rescheduleNext(context: Context, phase: DawnPhase) {
        ServiceLocator.ensureInit(context)
        val repo = ServiceLocator.repository
        val (config, location) = runBlocking {
            val cfg = repo.getAlarmsOnce().firstOrNull { it.phase == phase }
            cfg to repo.getLocationOnce()
        }
        if (config == null || location == null) {
            Log.w(TAG, "Cannot reschedule $phase: config=$config location=$location")
            return
        }
        ServiceLocator.scheduler.scheduleNext(config, location)
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
