# Kukurigu — Sunrise Alarm

A wake-up alarm for Android that rings **in sync with the sunrise**. Because the
sun rises at a different time every day, the alarm time drifts day by day — it is
recomputed every morning from your location using astronomical math, not a fixed
clock time.

## The four "sunrises"

As the sun climbs toward the horizon each morning it crosses four well-defined
thresholds. Kukurigu ships **one alarm per threshold** — the four default alarms:

| Phase | Sun's elevation | In the app | What it looks like outside |
|-------|----------------:|------------|----------------------------|
| Astronomical dawn | −18° | **First Light** | The first faint glow in an otherwise black sky |
| Nautical dawn | −12° | **Blue Hour** | The horizon becomes visible; deep blue light |
| Civil dawn | −6° | **Bright Dawn** | Enough light to see and move around outside |
| Sunrise | −0.833° | **Sunrise** | The sun's edge breaks the horizon |

By default only **Sunrise** is enabled; the other three are present and one tap
away. Each alarm is independently configurable.

Per alarm you can set:

- **Offset** — ring up to ±120 minutes around the phase (e.g. "30 min before sunrise").
- **Days of week** — any subset of Mon–Sun.
- **Vibrate**, **snooze length**, **custom label**, and (optionally) a custom sound.

## How the timing works

1. **`SolarCalculator`** (pure Kotlin, no Android dependencies) implements the
   NOAA / Meeus solar-position algorithm. Given a date, latitude, longitude and a
   target elevation angle, it returns the exact UTC instant the rising sun crosses
   that angle — or `null` for polar day/night when the sun never reaches it.
   *Verified accurate to within ~30 seconds of published values.*
2. **`NextAlarmComputer`** walks forward day by day, applies your offset and
   day-of-week rules, and returns the next concrete trigger time.
3. **`AlarmManagerScheduler`** schedules it with `AlarmManager.setAlarmClock()` —
   the only Android API that is reliably exact and exempt from Doze for wake-ups.
4. When an alarm fires, **`AlarmReceiver`** immediately re-arms the *same* phase
   for the next valid day (this is what makes the time shift every morning),
   boots a foreground **`AlarmService`** that plays the alarm and posts a
   full-screen notification, which launches **`AlarmActivity`** over the lock
   screen with Dismiss / Snooze.
5. **`BootReceiver`** re-arms everything after a reboot, time change, or timezone
   change.

## Architecture

```
com.kukurigu.sunalarm
├── MainActivity, SunAlarmApp, ServiceLocator   (entry point + manual DI)
├── solar/   DawnPhase, SolarCalculator          ← pure Kotlin, portable (KMP-ready)
├── data/    AlarmConfig, AppLocation, AlarmRepository (DataStore), LocationProvider
├── alarm/   AlarmScheduler, NextAlarmComputer, AlarmReceiver,
│            AlarmService, AlarmActivity, BootReceiver, AlarmConstants
└── ui/      MainViewModel + Compose screens + Material 3 theme
```

- **UI:** Jetpack Compose + Material 3 (warm sunrise palette, light & dark).
- **Persistence:** Preferences DataStore + `kotlinx.serialization` (no Room/codegen).
- **Location:** pick a **city** by name (Android's framework `Geocoder` for global
  coverage + a bundled offline catalogue of ~130 major cities for instant/offline
  results), **or** auto-detect via `LocationManager` (no Google Play Services;
  reverse-geocoded to a place name), **or** enter coordinates manually. No one needs
  to know their latitude/longitude.
- **DI:** a tiny `ServiceLocator` (no Hilt/Dagger).

## Build & run

You only need **Android Studio** — it bundles JDK 17, the Android SDK, Gradle, and
an emulator. See [`SETUP.md`](SETUP.md) for first-time setup.

1. *File → Open* → select this folder.
2. Let Gradle sync (first sync downloads the toolchain; Android Studio generates
   the Gradle wrapper jar automatically).
3. Run ▶ on a device or emulator.

In the app: grant **Notifications** and **Alarms & reminders** (exact alarms) when
prompted, then **set a location** — search for your city, use GPS, or (advanced)
enter coordinates. Each phase card then shows its next ring time.

Run the solar unit tests with:

```
./gradlew :app:testDebugUnitTest
```

## Testing the alarm without waiting for dawn

Set your location somewhere on Earth where a dawn phase is a couple of minutes
away, or give an enabled phase a large offset to pull the next trigger close, then
enable it and watch it ring over the lock screen. A real **physical phone** is the
most faithful test of the lock-screen / Doze behavior.

## Known limitations

- **iOS / multiplatform:** This is an Android app. The `solar/` and data models are
  pure Kotlin and lift directly into a Kotlin Multiplatform `commonMain` module, but
  the alarm-firing layer is Android-specific by necessity — iOS does not allow
  third-party apps to fire true background alarms.
- **Exact alarms:** If the user denies the "Alarms & reminders" permission, the app
  falls back to an inexact Doze-tolerant alarm that can fire several minutes late.
  The UI nags until the permission is granted.
- **Extreme timezones:** In locations whose civil timezone is far out of step with
  their longitude, the computed dawn time can differ from the "true" local-date dawn
  by a minute or two. Negligible for an alarm.
- **Polar regions:** When a phase does not occur (e.g. no astronomical dawn near the
  summer solstice at high latitude), that alarm is simply skipped until a day it
  occurs again.

## License

Released under the [MIT License](LICENSE).
