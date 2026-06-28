# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Kukurigu** (Bulgarian for a rooster's crow) is a native **Android** app — Kotlin + Jetpack Compose — that rings a wake-up alarm synced to the daily sunrise. There are four "dawn phases" (astronomical, nautical, civil dawn, and sunrise), each a separate alarm whose time is **recomputed every day** from the device location. Deliberately native-Android, **not** Kotlin Multiplatform; only the `solar/` engine and data models are KMP-portable.

## Build environment (read first)

- Requires **JDK 17+** and the Android SDK. The simplest setup is **Android Studio**, which bundles both, and generates the missing `gradle/wrapper/gradle-wrapper.jar` + `gradlew` scripts on first sync.
- A plain macOS shell on this machine has only **JDK 8 and no SDK**, so `./gradlew` fails there. Run Gradle from **Android Studio's terminal** (or set `JAVA_HOME` to a 17+ JDK first). When you can't build, verify changes statically and have the user run the build in Android Studio and paste errors back.
- Toolchain (in `gradle/libs.versions.toml`): **AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.2.10**, `compileSdk`/`targetSdk` 34, `minSdk` 26.

## Commands

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:installDebug         # build + install on a connected device/emulator
./gradlew :app:testDebugUnitTest    # run JVM unit tests (currently SolarCalculatorTest)
./gradlew :app:lintDebug            # Android lint

# single test class / method:
./gradlew :app:testDebugUnitTest --tests "com.kukurigu.sunalarm.solar.SolarCalculatorTest"
./gradlew :app:testDebugUnitTest --tests "com.kukurigu.sunalarm.solar.SolarCalculatorTest.<method>"
```

The alarm flow can't be unit-tested meaningfully; to exercise it, run the app and tap **"Test the alarm"** on the home screen (`AlarmManagerScheduler.scheduleTest()` fires the real path in ~8 s). Lock the device first — Android only lets a full-screen alarm take over the screen when locked/idle.

## Architecture

Single Gradle module (`:app`), package `com.kukurigu.sunalarm`, organized by layer:

- **`solar/`** — pure-Kotlin NOAA solar engine, **no Android deps**. `DawnPhase` is the enum of the four phases with their zenith angles; `SolarCalculator.morningPhase(date, lat, lng, phase)` returns the UTC `Instant` of that morning event or `null` (polar day/night).
- **`data/`** — `AlarmConfig`/`AppLocation`/`City` (`@Serializable`); `AlarmRepository` (interface + `DataStoreAlarmRepository`); `LocationProvider` (framework `LocationManager`, no Play Services); `CityCatalog` (bundled `assets/cities.json`) + `GeocodingService` (framework `Geocoder`).
- **`alarm/`** — the wake-up machinery (see flow below).
- **`ui/`** — Compose screens + `MainViewModel`; `ui/theme/` holds the theme and `KukuriguArt.kt` (the rooster/dawn Canvas art).
- Root: `MainActivity`, `SunAlarmApp` (Application), `ServiceLocator`.

### The alarm lifecycle (spans several files — understand this before touching `alarm/`)

1. A config change in `MainViewModel` → `AlarmManagerScheduler.scheduleNext()`.
2. `NextAlarmComputer.nextTrigger()` combines `SolarCalculator` + the config (offset, days-of-week) to find the next concrete trigger `Instant`.
3. Scheduled with `AlarmManager.setAlarmClock()` — the only reliably exact, Doze-exempt API.
4. `AlarmReceiver` fires → starts `AlarmService` (foreground) **and immediately re-arms the same phase for its next day** (this is what makes the time shift daily).
5. `AlarmService` plays the looping sound (USAGE_ALARM) + vibration + wakelock, and posts a high-importance notification with a full-screen intent to `AlarmActivity` (the lock-screen ring UI with Dismiss/Snooze).
6. `BootReceiver` re-arms everything on boot / time / timezone change.

### Invariants — do not break these

- **Alarms are one-shot, re-armed per fire.** There is no recurring alarm. Anything that changes timing must reschedule (`scheduleNext` / `rescheduleAll`).
- **`AlarmService` does zero disk I/O at fire time.** Vibrate/soundUri/snoozeMinutes are snapshotted into the fire intent by the scheduler and threaded through `AlarmReceiver` → service. Don't reintroduce a DataStore read on the fire path (ANR risk).
- **`AlarmService.onStartCommand` calls `startForeground()` on every entry** (fire, dismiss, snooze, null restart) before branching, and returns `START_NOT_STICKY` — required to avoid `ForegroundServiceDidNotStartInTimeException`.
- **A failed custom sound falls back to the system default** (`playSound(..., allowFallback)`); an alarm must never be silent.
- **Per-phase PendingIntent request codes are kept distinct** in `AlarmConstants`: operation `ordinal+1000`, show `+100`, dismiss `+200`, snooze-op `+300`, notification-snooze `+400`.
- **`ServiceLocator` is manual DI** (no Hilt). `SunAlarmApp.onCreate` calls `init`; receivers call `ensureInit` defensively before touching `repository`/`scheduler`.
- **Solar sign conventions** (`SolarCalculator`): east-positive longitude, `solarNoon = 720 − 4·lon − eqTime`, `morning = noon − 4·HA`. The strict `cosH > 1 / < -1 → null` polar guard is correct — **do not** clamp `cosH` before it (clamping breaks polar-night/day detection).
- **Permissions are driven from `MainActivity`**: `POST_NOTIFICATIONS` (33+), exact alarms via `canScheduleExactAlarms()` + `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, and `canUseFullScreenIntent()` (34+) via `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. Location is optional (city search / manual entry work without it).

## Toolchain gotchas

- This project uses **AGP 9 built-in Kotlin**: the `org.jetbrains.kotlin.android` plugin has been removed. Re-adding it is a build error. Set Kotlin compiler options via the top-level `kotlin { compilerOptions { } }` block, not `android.kotlinOptions { }`.
- `kotlinx.serialization`'s reified `encodeToString`/`decodeFromString` are **extension functions that need explicit imports** (`import kotlinx.serialization.encodeToString` / `decodeFromString`) — without them the compiler resolves the wrong overload.
- Persistence stores the whole `List<AlarmConfig>` and `AppLocation` as JSON strings in **Preferences DataStore**. Custom audio chosen via the file picker requires `takePersistableUriPermission` (done in `AlarmDetailScreen`) so it survives reboots.

See `README.md` and `SETUP.md` for the user-facing feature list and first-run steps.
