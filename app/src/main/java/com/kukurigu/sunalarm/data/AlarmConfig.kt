package com.kukurigu.sunalarm.data

import com.kukurigu.sunalarm.solar.DawnPhase
import kotlinx.serialization.Serializable

/**
 * User-configurable settings for a single dawn-phase alarm.
 *
 * Each [DawnPhase] maps to exactly one [AlarmConfig]. The concrete trigger time is
 * not stored here — it is recomputed daily from the device location and this config
 * (see NextAlarmComputer), then shifted by [offsetMinutes].
 *
 * @property phase Which dawn phase this alarm tracks.
 * @property enabled Whether the alarm is active and should be scheduled.
 * @property offsetMinutes Minutes to shift the trigger relative to the phase.
 *   Negative rings before the phase, positive rings after.
 * @property daysOfWeek Days the alarm may fire, as java.time DayOfWeek values
 *   (1 = Monday .. 7 = Sunday). Defaults to every day.
 * @property vibrate Whether the device vibrates while ringing.
 * @property snoozeMinutes How many minutes a snooze postpones the alarm.
 * @property soundUri Optional ringtone/audio URI string; null uses the default alarm ringtone.
 * @property soundName Human-readable name of the chosen sound, for display; null = default.
 * @property label Display name for the alarm; defaults to the phase title.
 */
@Serializable
data class AlarmConfig(
    val phase: DawnPhase,
    val enabled: Boolean,
    val offsetMinutes: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 9,
    val soundUri: String? = null,
    val soundName: String? = null,
    val label: String = phase.title,
)
