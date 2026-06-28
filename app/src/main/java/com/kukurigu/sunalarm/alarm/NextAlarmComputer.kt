package com.kukurigu.sunalarm.alarm

import com.kukurigu.sunalarm.data.AlarmConfig
import com.kukurigu.sunalarm.data.AppLocation
import com.kukurigu.sunalarm.solar.SolarCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Combines persisted [AlarmConfig] data with the solar math in [SolarCalculator]
 * to find the next concrete trigger [Instant] for a single alarm.
 */
object NextAlarmComputer {

    /** Maximum number of days to search forward (covers polar day/night edge cases). */
    private const val MAX_DAYS_AHEAD: Int = 400

    /**
     * Returns the next valid trigger at/after [after] for the given [config] at
     * [location], expressed as a UTC [Instant], or `null` if no trigger occurs
     * within the search window.
     *
     * For each candidate calendar date (in [zone]) the morning [com.kukurigu.sunalarm.solar.DawnPhase]
     * instant is computed; days where the phase does not occur are skipped. The
     * configured [AlarmConfig.offsetMinutes] is then applied. A candidate is
     * accepted only when its day-of-week (in [zone]) is contained in
     * [AlarmConfig.daysOfWeek] and the resulting instant is strictly after [after].
     */
    fun nextTrigger(
        config: AlarmConfig,
        location: AppLocation,
        after: Instant,
        zone: ZoneId,
    ): Instant? {
        val startDate: LocalDate = after.atZone(zone).toLocalDate()
        val offsetSeconds: Long = config.offsetMinutes.toLong() * 60L

        for (dayOffset in 0..MAX_DAYS_AHEAD) {
            val date: LocalDate = startDate.plusDays(dayOffset.toLong())

            val phaseInstant: Instant = SolarCalculator.morningPhase(
                date = date,
                latitudeDeg = location.latitude,
                longitudeDeg = location.longitude,
                phase = config.phase,
            ) ?: continue

            val triggerInstant: Instant = phaseInstant.plusSeconds(offsetSeconds)

            // Day-of-week is evaluated against the local calendar day the alarm
            // actually rings on, in the device zone.
            val triggerZoned = triggerInstant.atZone(zone)
            if (triggerZoned.dayOfWeek.value !in config.daysOfWeek) continue

            if (triggerInstant.isAfter(after)) {
                return triggerInstant
            }
        }
        return null
    }
}
