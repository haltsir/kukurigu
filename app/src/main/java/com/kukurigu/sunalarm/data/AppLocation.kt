package com.kukurigu.sunalarm.data

import kotlinx.serialization.Serializable

/**
 * A geographic location used to compute the daily dawn phases.
 *
 * [label] is a human-friendly description of where the coordinates came from
 * (e.g. "Current location" or a manually entered place name). It is optional and
 * defaults to an empty string.
 */
@Serializable
data class AppLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
)
