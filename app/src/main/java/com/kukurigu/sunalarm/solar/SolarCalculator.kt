package com.kukurigu.sunalarm.solar

import java.time.Instant
import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure-Kotlin solar-position engine implementing the NOAA solar calculator
 * (the algorithm published with the NOAA Solar Calculation spreadsheet, itself
 * based on Jean Meeus, "Astronomical Algorithms").
 *
 * No Android dependencies — only [java.time] and [kotlin.math].
 *
 * All math is done in UTC. A "morning phase" is the moment the rising sun's
 * center crosses a given zenith angle (see [DawnPhase]); the result is returned
 * as a UTC [Instant], or null when the phase does not occur on that calendar
 * date (polar day / polar night for that elevation).
 *
 * Accuracy: a single refinement iteration is performed. The first pass evaluates
 * the solar parameters at solar noon (a good approximation for the day) to find an
 * approximate event time, then those parameters are recomputed at that approximate
 * event time and the event time is solved again. One iteration brings the error
 * well under one minute for all non-polar latitudes, comfortably inside the
 * ~2-minute tolerance the tests assert against.
 */
object SolarCalculator {

    /**
     * Returns the UTC [Instant] of the given MORNING [phase] for the calendar
     * date [date] at the given latitude/longitude (decimal degrees, east-positive
     * longitude, north-positive latitude), or null if the phase does not occur
     * that day (polar day or polar night for this elevation).
     */
    fun morningPhase(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        phase: DawnPhase,
    ): Instant? {
        // Julian Day at 0h UTC for the calendar date.
        val jd0h = julianDay(date)

        // --- Pass 1: evaluate at solar noon, find approximate event time. ---
        // Julian centuries at 0.5 day (noon UTC) is a stable anchor for the day.
        val firstMinutes = eventMinutesUtc(
            jd0h = jd0h,
            evalDayFraction = 0.5, // start by evaluating at noon UTC
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            phase = phase,
        ) ?: return null

        // --- Pass 2: refine, evaluating solar parameters at the approximate event time. ---
        val secondMinutes = eventMinutesUtc(
            jd0h = jd0h,
            evalDayFraction = firstMinutes / MINUTES_PER_DAY,
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            phase = phase,
        ) ?: return null

        return instantFromUtcMidnight(date, secondMinutes)
    }

    /**
     * Computes the morning-event time, expressed as minutes from this UTC date's
     * midnight, solving the sun-position equations with all solar parameters
     * evaluated at the instant [evalDayFraction] of a day after 0h UTC.
     *
     * Returns null on polar day/night (no crossing of the phase zenith).
     */
    private fun eventMinutesUtc(
        jd0h: Double,
        evalDayFraction: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        phase: DawnPhase,
    ): Double? {
        // Julian Day and Julian Century at the evaluation instant.
        val jd = jd0h + evalDayFraction
        val t = (jd - 2451545.0) / 36525.0

        // 3. Geometric Mean Longitude of the Sun (degrees), normalized to [0,360).
        val l0 = mod360(280.46646 + t * (36000.76983 + 0.0003032 * t))

        // 4. Geometric Mean Anomaly of the Sun (degrees).
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val mRad = Math.toRadians(m)

        // 5. Eccentricity of Earth's orbit (unitless).
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        // 6. Sun's Equation of Center (degrees).
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2.0 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3.0 * mRad) * 0.000289

        // 7. True longitude and apparent longitude (degrees).
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val omegaRad = Math.toRadians(omega)
        val lambda = trueLong - 0.00569 - 0.00478 * sin(omegaRad)
        val lambdaRad = Math.toRadians(lambda)

        // 8. Mean obliquity of the ecliptic and corrected obliquity (degrees).
        val eps0 = 23.0 +
            (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = eps0 + 0.00256 * cos(omegaRad)
        val epsRad = Math.toRadians(eps)

        // 9. Sun's declination (radians): sin(delta) = sin(eps) * sin(lambda).
        val deltaRad = Math.asin(sin(epsRad) * sin(lambdaRad))

        // 10. Equation of Time (minutes), NOAA y = tan^2(eps/2) formulation.
        val y = tan(epsRad / 2.0) * tan(epsRad / 2.0)
        val l0Rad = Math.toRadians(l0)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2.0 * l0Rad) -
                2.0 * e * sin(mRad) +
                4.0 * e * y * sin(mRad) * cos(2.0 * l0Rad) -
                0.5 * y * y * sin(4.0 * l0Rad) -
                1.25 * e * e * sin(2.0 * mRad),
        )

        // 11. Hour angle (degrees) at the phase zenith Z.
        val latRad = Math.toRadians(latitudeDeg)
        val zenithRad = Math.toRadians(phase.zenithDegrees)
        val cosH = (cos(zenithRad) / (cos(latRad) * cos(deltaRad))) - tan(latRad) * tan(deltaRad)

        // cosH > 1  -> sun never rises this high (polar night for this phase).
        // cosH < -1 -> sun stays above this angle (polar day, no morning crossing).
        if (cosH > 1.0 || cosH < -1.0) return null
        val haDeg = Math.toDegrees(acos(cosH)) // positive

        // 12. Solar noon in minutes from UTC midnight (longitude east-positive).
        val solarNoon = 720.0 - 4.0 * longitudeDeg - eqTime

        // 13. Morning phase time in minutes from UTC midnight.
        return solarNoon - 4.0 * haDeg
    }

    /**
     * Converts a minutes-from-UTC-midnight value on [date] (which may be negative
     * or exceed 1440 due to longitude/event wrap) into a UTC [Instant], rolling
     * the day boundary as needed.
     */
    private fun instantFromUtcMidnight(date: LocalDate, minutesFromMidnight: Double): Instant {
        val baseEpochSeconds = date.toEpochDay() * SECONDS_PER_DAY
        val secondsFromMidnight = Math.round(minutesFromMidnight * 60.0)
        return Instant.ofEpochSecond(baseEpochSeconds + secondsFromMidnight)
    }

    /** Julian Day number at 0h UTC for a Gregorian calendar [date]. */
    private fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = Math.floor(year / 100.0)
        val b = 2.0 - a + Math.floor(a / 4.0)
        return Math.floor(365.25 * (year + 4716)) +
            Math.floor(30.6001 * (month + 1)) +
            day + b - 1524.5
    }

    /** Normalize a value in degrees to the range [0, 360). */
    private fun mod360(value: Double): Double {
        val r = value % 360.0
        return if (r < 0) r + 360.0 else r
    }

    private const val MINUTES_PER_DAY = 1440.0
    private const val SECONDS_PER_DAY = 86_400L
}
