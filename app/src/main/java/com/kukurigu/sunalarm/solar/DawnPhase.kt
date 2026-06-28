package com.kukurigu.sunalarm.solar

/**
 * The four morning dawn phases, defined by the solar elevation of the sun's center
 * relative to the horizon. Each phase corresponds to a fixed zenith angle
 * (zenith = 90 - elevation) used by [SolarCalculator] to compute the moment the
 * rising sun's center crosses that angle.
 *
 * Ordered earliest to latest in the morning:
 * ASTRONOMICAL -> NAUTICAL -> CIVIL -> SUNRISE.
 */
enum class DawnPhase(
    /** Sun-center elevation in degrees at the moment of this phase (negative, below horizon). */
    val elevationDegrees: Double,
    /** Zenith angle in degrees, defined as 90 - elevationDegrees. */
    val zenithDegrees: Double,
    /** Short human-readable title for the phase. */
    val title: String,
    /** Longer human-readable description of the phase. */
    val description: String,
) {
    ASTRONOMICAL(
        elevationDegrees = -18.0,
        zenithDegrees = 108.0,
        title = "First Light",
        description = "Astronomical dawn — the sky's first faint glow",
    ),
    NAUTICAL(
        elevationDegrees = -12.0,
        zenithDegrees = 102.0,
        title = "Blue Hour",
        description = "Nautical dawn — the horizon becomes visible",
    ),
    CIVIL(
        elevationDegrees = -6.0,
        zenithDegrees = 96.0,
        title = "Bright Dawn",
        description = "Civil dawn — enough light to see clearly",
    ),
    SUNRISE(
        elevationDegrees = -0.833,
        zenithDegrees = 90.833,
        title = "Sunrise",
        description = "The sun breaks the horizon",
    ),
}
