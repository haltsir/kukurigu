package com.kukurigu.sunalarm.solar

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit tests for [SolarCalculator.morningPhase].
 *
 * Reference times are published values from timeanddate.com (rounded to the whole
 * minute on those pages). Each assertion allows a tolerance of 2 minutes, which
 * comfortably covers both the NOAA algorithm's residual error and the rounding of
 * the published references.
 *
 * Published references used (all for calendar year 2024):
 *
 *  London  (51.5074, -0.1278, Europe/London)
 *    2024-06-21 sunrise            04:43 BST   (timeanddate: 04:43)
 *    2024-01-15 sunrise            08:00 GMT   (timeanddate: 08:00)
 *    2024-01-15 civil dawn         07:21 GMT   (timeanddate twilight table)
 *    2024-01-15 nautical dawn      06:39 GMT   (timeanddate twilight table)
 *    2024-01-15 astronomical dawn  05:59 GMT   (timeanddate twilight table)
 *
 *  New York (40.7128, -74.0060, America/New_York)
 *    2024-06-21 sunrise            05:25 EDT   (timeanddate: 05:25)
 *    2024-12-21 sunrise            07:17 EST   (timeanddate: 07:17)
 *
 *  Sydney  (-33.8688, 151.2093, Australia/Sydney) — southern hemisphere
 *    2024-06-21 sunrise (winter)   07:00 AEST  (timeanddate: 07:00)
 *
 *  Longyearbyen (78.0, 15.0) — high Arctic, polar night mid-winter.
 */
class SolarCalculatorTest {

    // ---- Locations ----
    private val londonLat = 51.5074
    private val londonLng = -0.1278
    private val londonZone = ZoneId.of("Europe/London")

    private val nycLat = 40.7128
    private val nycLng = -74.0060
    private val nycZone = ZoneId.of("America/New_York")

    private val sydneyLat = -33.8688
    private val sydneyLng = 151.2093
    private val sydneyZone = ZoneId.of("Australia/Sydney")

    private val toleranceMinutes = 2L

    /** Build the expected [Instant] from a local wall-clock time in a given zone. */
    private fun expected(zone: ZoneId, date: LocalDate, hour: Int, minute: Int): Instant =
        ZonedDateTime.of(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)), zone).toInstant()

    /** Assert the computed instant is within [toleranceMinutes] of [expected]. */
    private fun assertCloseTo(expected: Instant, actual: Instant?, message: String) {
        assertNotNull("$message: expected a non-null time", actual)
        val diff = Duration.between(expected, actual).abs()
        assertTrue(
            "$message: computed ${actual} differs from expected $expected by ${diff.toSeconds()}s " +
                "(> ${toleranceMinutes}min)",
            diff <= Duration.ofMinutes(toleranceMinutes),
        )
    }

    @Test
    fun londonSummerSunrise() {
        val date = LocalDate.of(2024, 6, 21)
        val actual = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.SUNRISE)
        // 04:43 BST
        assertCloseTo(expected(londonZone, date, 4, 43), actual, "London 2024-06-21 sunrise")
    }

    @Test
    fun londonWinterSunrise() {
        val date = LocalDate.of(2024, 1, 15)
        val actual = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.SUNRISE)
        // 08:00 GMT
        assertCloseTo(expected(londonZone, date, 8, 0), actual, "London 2024-01-15 sunrise")
    }

    @Test
    fun newYorkSummerSunrise() {
        val date = LocalDate.of(2024, 6, 21)
        val actual = SolarCalculator.morningPhase(date, nycLat, nycLng, DawnPhase.SUNRISE)
        // 05:25 EDT
        assertCloseTo(expected(nycZone, date, 5, 25), actual, "New York 2024-06-21 sunrise")
    }

    @Test
    fun newYorkWinterSunrise() {
        val date = LocalDate.of(2024, 12, 21)
        val actual = SolarCalculator.morningPhase(date, nycLat, nycLng, DawnPhase.SUNRISE)
        // 07:17 EST
        assertCloseTo(expected(nycZone, date, 7, 17), actual, "New York 2024-12-21 sunrise")
    }

    @Test
    fun sydneyWinterSunrise() {
        // Southern hemisphere: June is winter, late sunrise.
        val date = LocalDate.of(2024, 6, 21)
        val actual = SolarCalculator.morningPhase(date, sydneyLat, sydneyLng, DawnPhase.SUNRISE)
        // 07:00 AEST
        assertCloseTo(expected(sydneyZone, date, 7, 0), actual, "Sydney 2024-06-21 sunrise")
    }

    @Test
    fun londonWinterAllPhasesMatchReference() {
        val date = LocalDate.of(2024, 1, 15)
        val astro = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.ASTRONOMICAL)
        val nautical = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.NAUTICAL)
        val civil = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.CIVIL)
        val sunrise = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.SUNRISE)

        assertCloseTo(expected(londonZone, date, 5, 59), astro, "London 2024-01-15 astronomical dawn")
        assertCloseTo(expected(londonZone, date, 6, 39), nautical, "London 2024-01-15 nautical dawn")
        assertCloseTo(expected(londonZone, date, 7, 21), civil, "London 2024-01-15 civil dawn")
        assertCloseTo(expected(londonZone, date, 8, 0), sunrise, "London 2024-01-15 sunrise")
    }

    @Test
    fun phasesAreStrictlyOrderedThroughTheMorning() {
        // On a date/location where all four phases occur, they must be ordered
        // earliest -> latest: ASTRONOMICAL < NAUTICAL < CIVIL < SUNRISE.
        val date = LocalDate.of(2024, 1, 15)
        val astro = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.ASTRONOMICAL)
        val nautical = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.NAUTICAL)
        val civil = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.CIVIL)
        val sunrise = SolarCalculator.morningPhase(date, londonLat, londonLng, DawnPhase.SUNRISE)

        assertNotNull(astro)
        assertNotNull(nautical)
        assertNotNull(civil)
        assertNotNull(sunrise)

        assertTrue("astronomical must precede nautical", astro!! < nautical!!)
        assertTrue("nautical must precede civil", nautical < civil!!)
        assertTrue("civil must precede sunrise", civil < sunrise!!)
    }

    @Test
    fun arcticPolarNightReturnsNullForSunrise() {
        // Longyearbyen latitude ~78N in deep midwinter: the sun never rises.
        val date = LocalDate.of(2024, 12, 21)
        val actual = SolarCalculator.morningPhase(date, 78.0, 15.0, DawnPhase.SUNRISE)
        assertNull("High Arctic mid-winter sunrise should be null (polar night)", actual)
    }

    @Test
    fun arcticPolarDayReturnsNullForSunrise() {
        // Longyearbyen latitude ~78N at midsummer: the sun never sets, so there is
        // no morning sunrise crossing -> null.
        val date = LocalDate.of(2024, 6, 21)
        val actual = SolarCalculator.morningPhase(date, 78.0, 15.0, DawnPhase.SUNRISE)
        assertNull("High Arctic midsummer sunrise should be null (polar day)", actual)
    }
}
