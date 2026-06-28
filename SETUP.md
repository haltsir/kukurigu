# Setup

## Recommended: Android Studio (one install gives you everything)

Android Studio bundles a JDK 17, the Android SDK, Gradle, and an emulator. Nothing
else needs to be installed on your machine.

```bash
brew install --cask android-studio   # or download from developer.android.com
```

Then:

1. Launch Android Studio once and complete the setup wizard (installs the SDK + an
   emulator system image).
2. *File → Open* → select this project folder (`kukurigu`).
3. Wait for **Gradle Sync** to finish. The first sync:
   - downloads AGP / Kotlin / the Android SDK platform if missing (click any
     "Install" links it offers, e.g. *SDK Platform 34*),
   - **generates the Gradle wrapper jar** (`gradle/wrapper/gradle-wrapper.jar`) and
     `gradlew` scripts automatically — these are the only files not checked in,
     because they are binary/generated.
4. If Android Studio's **AGP Upgrade Assistant** offers to bump the Android Gradle
   Plugin, accept it — it keeps AGP and the Gradle wrapper in lockstep with your IDE.
5. Pick a device (a physical phone over USB, or *Device Manager → Create Device*)
   and press **Run ▶**.

## In-app first run

- Grant **Notifications** and **Alarms & reminders** (exact alarms) when prompted.
  On Android 14+ also grant **full-screen notifications** if the banner appears —
  this is what lets the alarm show over the lock screen.
- Set a **location**: tap the location card and **search for your city** (e.g.
  "Berlin"), or tap *Use my location* (needs location permission). Manual coordinate
  entry is available under "Enter coordinates manually" if you ever need it.

## CLI-only alternative (no IDE)

Only if you specifically want to avoid Android Studio:

```bash
brew install --cask temurin@17                 # JDK 17
brew install --cask android-commandlinetools   # sdkmanager
export ANDROID_HOME="$HOME/Library/Android/sdk"
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
           "emulator" "system-images;android-34;google_apis;arm64-v8a"
sdkmanager --licenses
gradle wrapper --gradle-version 8.13           # generate the wrapper jar once
./gradlew assembleDebug                          # build the APK
```

> Your machine currently has only JDK 8 and no Android SDK, which is why the IDE
> route (which ships its own JDK 17 + SDK) is the path of least resistance.

## Testing on hardware

The alarm logic and UI run fine on an emulator, but the real "rings while the phone
is asleep on the nightstand and lights up the lock screen" behavior is most faithful
on a **physical Android phone**, where Doze and full-screen-intent handling match
production.
