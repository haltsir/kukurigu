package com.kukurigu.sunalarm.alarm

import com.kukurigu.sunalarm.solar.DawnPhase

/**
 * Stable constants shared across the alarm scheduling and firing components:
 * the notification channel, broadcast/service intent actions, extra keys, and
 * a deterministic per-phase request code so [android.app.PendingIntent]s remain
 * distinct and individually cancelable.
 */
object AlarmConstants {

    /** Id of the high-importance notification channel used for firing alarms. */
    const val CHANNEL_ID: String = "sunrise_alarm"

    /** User-visible name of the alarm notification channel. */
    const val CHANNEL_NAME: String = "Sunrise Alarm"

    /** Description of the alarm notification channel. */
    const val CHANNEL_DESCRIPTION: String =
        "Wake-up alarms synced to the daily dawn phases."

    /** Broadcast action delivered to [AlarmReceiver] when an alarm should fire. */
    const val ACTION_FIRE: String = "com.kukurigu.sunalarm.ALARM_FIRE"

    /** Service action telling [AlarmService] to dismiss the current alarm. */
    const val ACTION_DISMISS: String = "com.kukurigu.sunalarm.ALARM_DISMISS"

    /** Service action telling [AlarmService] to snooze the current alarm. */
    const val ACTION_SNOOZE: String = "com.kukurigu.sunalarm.ALARM_SNOOZE"

    /** Extra carrying the [DawnPhase] name (see [DawnPhase.name]). */
    const val EXTRA_PHASE: String = "com.kukurigu.sunalarm.extra.PHASE"

    /** Extra carrying the user-facing alarm label. */
    const val EXTRA_LABEL: String = "com.kukurigu.sunalarm.extra.LABEL"

    /** Extra carrying the actual trigger time in epoch milliseconds. */
    const val EXTRA_TIME_MILLIS: String = "com.kukurigu.sunalarm.extra.TIME_MILLIS"

    /**
     * Snapshot of the alarm's vibrate / sound / snooze settings, threaded through
     * the fire intent so [AlarmService] never has to read DataStore on the main
     * thread at the moment the alarm must ring.
     */
    const val EXTRA_VIBRATE: String = "com.kukurigu.sunalarm.extra.VIBRATE"
    const val EXTRA_SOUND_URI: String = "com.kukurigu.sunalarm.extra.SOUND_URI"
    const val EXTRA_SNOOZE_MINUTES: String = "com.kukurigu.sunalarm.extra.SNOOZE_MINUTES"

    /** Default snooze length (minutes) when none is supplied. */
    const val DEFAULT_SNOOZE_MINUTES: Int = 9

    /** Notification id used by the foreground [AlarmService]. */
    const val NOTIFICATION_ID: Int = 42

    /**
     * Offset applied to a phase ordinal to derive a stable base request code so
     * that each phase's [android.app.PendingIntent]s are distinct.
     */
    private const val REQUEST_CODE_BASE: Int = 1000

    /** Stable per-phase request code, e.g. [DawnPhase.ordinal] + 1000. */
    fun requestCode(phase: DawnPhase): Int = phase.ordinal + REQUEST_CODE_BASE

    /**
     * Distinct request code for a phase's full-screen "show" PendingIntent so it
     * does not collide with the operation PendingIntent that fires the alarm.
     */
    fun showRequestCode(phase: DawnPhase): Int = phase.ordinal + REQUEST_CODE_BASE + 100

    /**
     * Distinct request code for a phase's snooze operation PendingIntent so a
     * snooze alarm occupies its own slot and does not overwrite the regular
     * daily alarm scheduled under [requestCode].
     */
    fun snoozeRequestCode(phase: DawnPhase): Int = phase.ordinal + REQUEST_CODE_BASE + 300

    /** Request code for the notification's Dismiss action (service PendingIntent). */
    fun notificationDismissRequestCode(phase: DawnPhase): Int =
        phase.ordinal + REQUEST_CODE_BASE + 200

    /** Request code for the notification's Snooze action (service PendingIntent). */
    fun notificationSnoozeRequestCode(phase: DawnPhase): Int =
        phase.ordinal + REQUEST_CODE_BASE + 400
}
