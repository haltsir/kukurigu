package com.kukurigu.sunalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kukurigu.sunalarm.ServiceLocator
import com.kukurigu.sunalarm.alarm.NextAlarmComputer
import com.kukurigu.sunalarm.data.AlarmConfig
import com.kukurigu.sunalarm.data.AppLocation
import com.kukurigu.sunalarm.data.City
import com.kukurigu.sunalarm.data.CityCatalog
import com.kukurigu.sunalarm.data.GeocodingService
import com.kukurigu.sunalarm.data.LocationProvider
import com.kukurigu.sunalarm.solar.DawnPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Per-alarm view state shown in the list. [nextTriggerText] is a human friendly
 * description of the next ring time, e.g. "Sat 04:52", or a status hint when no
 * concrete time can be computed (no location, polar night, alarm disabled).
 */
data class AlarmUiState(
    val config: AlarmConfig,
    val nextTrigger: Instant?,
    val nextTriggerText: String,
)

/** Aggregate UI state for [AlarmListScreen]. */
data class AlarmListUiState(
    val alarms: List<AlarmUiState> = emptyList(),
    val location: AppLocation? = null,
    val loading: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository get() = ServiceLocator.repository
    private val scheduler get() = ServiceLocator.scheduler

    private val cityCatalog = CityCatalog(application)
    private val geocoding = GeocodingService(application)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

    private val _detecting = MutableStateFlow(false)
    /** True while a device-location detection is in flight. */
    val detecting: StateFlow<Boolean> = _detecting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** Transient one-shot user message (e.g. "Couldn't detect location"). */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _cityQuery = MutableStateFlow("")
    /** Current text in the city search field. */
    val cityQuery: StateFlow<String> = _cityQuery.asStateFlow()

    private val _searching = MutableStateFlow(false)
    /** True while an online geocoder lookup is in flight. */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /**
     * City search results for [cityQuery]. Emits instant offline catalogue matches
     * first, then enriches with online geocoder results when they arrive. The
     * debounce avoids a geocoder call on every keystroke, and transformLatest
     * cancels an in-flight lookup when the query changes.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val cityResults: StateFlow<List<City>> = _cityQuery
        .debounce(250)
        .transformLatest { raw ->
            val query = raw.trim()
            if (query.length < 2) {
                _searching.value = false
                emit(emptyList())
                return@transformLatest
            }
            val local = cityCatalog.search(query)
            emit(local)
            _searching.value = true
            try {
                val remote = geocoding.search(query)
                if (remote.isNotEmpty()) emit(mergeCities(local, remote))
            } catch (t: Throwable) {
                // Keep the offline results if the geocoder is unavailable or fails.
            } finally {
                _searching.value = false
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<AlarmListUiState> =
        combine(repository.alarms, repository.location) { alarms, location ->
            val now = Instant.now()
            val ordered = alarms.sortedBy { it.phase.ordinal }
            AlarmListUiState(
                alarms = ordered.map { config -> toAlarmUiState(config, location, now) },
                location = location,
                loading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlarmListUiState(),
        )

    private fun toAlarmUiState(
        config: AlarmConfig,
        location: AppLocation?,
        now: Instant,
    ): AlarmUiState {
        val trigger: Instant? = if (location != null) {
            NextAlarmComputer.nextTrigger(config, location, now, zone)
        } else {
            null
        }
        val text = when {
            location == null -> "Set a location to schedule"
            !config.enabled -> "Off"
            trigger == null -> "No sunrise on selected days"
            else -> formatTrigger(trigger, now)
        }
        return AlarmUiState(config = config, nextTrigger = trigger, nextTriggerText = text)
    }

    private fun formatTrigger(trigger: Instant, now: Instant): String {
        val triggerDate = trigger.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val daysAhead = java.time.temporal.ChronoUnit.DAYS.between(today, triggerDate)
        val prefix = when (daysAhead) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> null
        }
        return if (prefix != null) {
            val time = trigger.atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
            "$prefix $time"
        } else if (daysAhead in 2..6) {
            trigger.atZone(zone).format(dayFormatter)
        } else {
            trigger.atZone(zone).format(dateFormatter)
        }
    }

    /** Look up a single alarm config by phase, or null if not present yet. */
    fun configFor(phase: DawnPhase): AlarmConfig? =
        uiState.value.alarms.firstOrNull { it.config.phase == phase }?.config

    fun toggleAlarm(phase: DawnPhase, enabled: Boolean) {
        val current = configFor(phase) ?: return
        updateAlarm(current.copy(enabled = enabled))
    }

    fun updateAlarm(config: AlarmConfig) {
        viewModelScope.launch {
            repository.updateAlarm(config)
            val location = repository.getLocationOnce()
            if (location != null) {
                if (config.enabled) {
                    scheduler.scheduleNext(config, location)
                } else {
                    scheduler.cancel(config.phase)
                }
            } else {
                // No location -> nothing can fire; make sure any stale alarm is cancelled.
                scheduler.cancel(config.phase)
            }
        }
    }

    fun setManualLocation(lat: Double, lng: Double, label: String = "") {
        viewModelScope.launch {
            val location = AppLocation(latitude = lat, longitude = lng, label = label)
            repository.setLocation(location)
            scheduler.rescheduleAll()
        }
    }

    /** Updates the city search query; [cityResults] reacts automatically. */
    fun searchCities(query: String) {
        _cityQuery.value = query
    }

    /** Picks a searched city as the location and clears the search. */
    fun selectCity(city: City) {
        _cityQuery.value = ""
        setManualLocation(city.latitude, city.longitude, city.displayName)
    }

    /** Combines offline + online results, de-duplicated by name/region/country. */
    private fun mergeCities(local: List<City>, remote: List<City>): List<City> {
        val seen = HashSet<String>()
        val merged = ArrayList<City>(local.size + remote.size)
        for (city in local + remote) {
            val key = "${city.name}|${city.admin.orEmpty()}|${city.country.orEmpty()}".lowercase()
            if (seen.add(key)) merged.add(city)
        }
        return merged.take(25)
    }

    fun detectLocation() {
        if (_detecting.value) return
        viewModelScope.launch {
            _detecting.value = true
            try {
                val detected = LocationProvider(getApplication()).current()
                if (detected != null) {
                    // Turn the raw fix into a readable place name when possible.
                    val label = geocoding.reverseGeocode(detected.latitude, detected.longitude)
                        ?: detected.label
                    repository.setLocation(detected.copy(label = label))
                    scheduler.rescheduleAll()
                } else {
                    _message.value = "Couldn't detect location. Search for your city instead."
                }
            } finally {
                _detecting.value = false
            }
        }
    }

    /** Schedules a one-shot test alarm ~8 seconds out so you can preview the ring UX. */
    fun fireTestAlarm() {
        scheduler.scheduleTest()
        _message.value = "Test alarm set for ~8s — lock your phone to see the rooster crow."
    }

    fun consumeMessage() {
        _message.value = null
    }
}
