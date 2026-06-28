package com.kukurigu.sunalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kukurigu.sunalarm.ServiceLocator

/**
 * Re-establishes all scheduled alarms after events that clear the
 * [android.app.AlarmManager] schedule or shift wall-clock time: device boot,
 * a timezone change, or the user setting the clock.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in HANDLED_ACTIONS) return

        ServiceLocator.ensureInit(context)

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                ServiceLocator.scheduler.rescheduleAll()
            } catch (t: Throwable) {
                Log.e(TAG, "rescheduleAll failed after $action", t)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            // Some OEMs deliver the quick-boot variant instead of BOOT_COMPLETED.
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
