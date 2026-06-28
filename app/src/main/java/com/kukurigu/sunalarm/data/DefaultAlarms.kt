package com.kukurigu.sunalarm.data

import com.kukurigu.sunalarm.solar.DawnPhase

/**
 * Provides the initial set of alarms seeded on first run.
 *
 * All four dawn phases are present. Only [DawnPhase.SUNRISE] is enabled by default;
 * the earlier, darker phases are present but disabled so the user can opt in to them.
 */
object DefaultAlarms {

    /**
     * Returns a fresh list containing one [AlarmConfig] per [DawnPhase], in
     * chronological morning order (earliest phase first). Sensible defaults are
     * applied: no offset, every day of the week, vibration on, 9-minute snooze.
     */
    fun list(): List<AlarmConfig> = DawnPhase.entries.map { phase ->
        AlarmConfig(
            phase = phase,
            enabled = phase == DawnPhase.SUNRISE,
            offsetMinutes = 0,
            daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
            vibrate = true,
            snoozeMinutes = 9,
            soundUri = null,
            label = phase.title,
        )
    }
}
