# BYD Weather

Keeps the stock BYD weather widget updated with local forecasts from
[Open-Meteo](https://open-meteo.com/). Runs as a background service on compatible
Chinese DiLink head units, with a small settings screen for permissions and
refresh controls.

## Features

- Current weather, hourly and daily forecasts, and air quality when available.
- Automatic updates every 15 minutes by default; adjustable from 5 to 180 minutes.
- Optional startup with the car and a persistent status notification.
- Manual updates from the app or, optionally, the stock widget's refresh button.
- Automatic GPS with Wi-Fi/cell fallback, or a Wi-Fi/cell-only location option.
- Update progress, specific failure messages, and the last successful location source.
- Adaptive launcher icons and monochrome icons for Android 13+.

## Requirements

The APK requires Android 8.0 (API 26) or later. The integration is designed around
DiLink on Android 10; installation alone does not establish compatibility with
a particular car or firmware.

The head unit must provide:

- Access to the stock BYD WeatherData provider and widget.
- GPS callbacks/NMEA, or a working Android Wi-Fi/cell location provider.
- Location permission: precise access enables GPS; approximate access can use
  Wi-Fi/cell. Background location permission is needed for startup where Android
  requires it.
- An internet connection that Android recognizes as usable.
- Permission to start and run in the background through DiLink's app manager.

The app does not require root, ADB, or a weather API key.

## Setup

1. Install the APK and open **BYD Weather**. If another app updates the same
   widget, disable its weather updates first.
2. Enable **Keep weather updated** and grant location permission.
   Use **Grant background location** to allow location access all the time
   when Android offers that option.
3. Enable **Start automatically with the car** if you want automatic startup.
   Open **Open BYD background-start settings** and allow startup and background
   operation in DiLink's app manager.
4. Enable Android location, connect to the internet, and choose **Location method**:
   **Automatic** prefers GPS and falls back to Wi-Fi/cell; **Wi-Fi/cell only**
   requests the car's network-location provider without GPS/NMEA listeners.
   Use Wi-Fi/cell only when GPS is unreliable. Tap **Update weather now**.
   Check the app's status and last successful update time, and confirm the
   stock widget changes.
5. Set your preferred refresh interval.
6. Optional: tap **Enable stock weather refresh button** and enable
   **BYD Weather refresh button** in Android Accessibility settings.

Accessibility is only needed for updates triggered by the stock widget's button.
The stock weather app can still perform its own refresh action.

## How updates work

The service waits for usable internet, then requests a location for up to
60 seconds. In Automatic mode, it first subscribes to Android GPS updates and
optional NMEA messages. If GPS has no fix after ten seconds, it also requests
`LocationManager.NETWORK_PROVIDER`. If GPS is off or precise permission is not
granted, the network request starts immediately. Wi-Fi/cell-only mode requests
only the network provider. The first usable fix completes the lookup; all
listeners and fallback timers are then released.

GPS fixes must be no more than ten seconds old. NMEA requires a valid checksum,
fix status, and UTC fix time. Network fixes must be no more than 60 seconds old,
have valid coordinates and a reported accuracy of 25 km or better, and must not
be marked as mock or GPS-derived. The app uses the provider's monotonic timestamp
to calculate the remaining lifetime and never queries last-known locations.
The validation and cleanup rules are recorded in
[the location contract](docs/gps-location-contract.json).

Wi-Fi/cell availability depends on the head unit's installed and enabled network
provider, internet access, and regional radio coverage. The status shows **Wi-Fi**
or **Cell network** when the provider reports the radio source, and **Network
provider** when it does not. OEM providers can reuse positions and rewrite fix
timestamps; the Android callback alone cannot prove an independent, freshly
measured radio fix. Wi-Fi/cell-only mode controls which provider this app requests.

City names use Android's built-in `Geocoder`, restored from the 1.1.0 release.
The app displays the returned city, or its sub-administrative/admin area when
the city is unavailable. If geocoding is unavailable or fails, it displays
**Current location** and continues the weather update. Availability and naming
depend on the head unit's geocoding service. A successful update reports when
the city name or optional air-quality data could not be obtained.

After fetching weather, the app writes it to the BYD provider, reads it back,
and requests a widget refresh. A successful update advances the saved timestamp.
The app updates and verifies the provider's first row, matching the stock widget's
query, even when older integrations have left multiple rows.

In DiLink 3.0 firmware (`18.1.2.2601230.1`), WeatherData
`2.9.5.250616` selects both the widget label and artwork from `cnweatherid`.
It ignores the payload's `weathertext` and current day/night flag. Clear weather
uses native ID `0`, whose stock English label is **Sunny** during both day and
night. Night artwork is selected separately using the observation timestamp,
sunset, and next sunrise. Daily records use sunrise anchors so this also works
before dawn. Changing the payload text cannot change that stock English label.

The night-artwork switch applies only to native ID `0`. Native ID `1`
(**Cloudy**) always uses the same sun-behind-cloud card, including at night.
Partly cloudy weather can therefore refresh successfully and still show a sun
at night. Selecting a moon-and-cloud card requires a change to the stock
WeatherData widget renderer or resources; the provider payload cannot select
that artwork.

The firmware's available categories are coarser than Open-Meteo's descriptions:
mainly clear and partly cloudy both display **Cloudy**, overcast displays
**Dreary**, and drizzle uses the corresponding rain-intensity label. Light and
heavy rain share rain artwork; snow intensities similarly share snow artwork.
The stock hourly list uses the current observation's night state for every
clear-weather icon, rather than each hour's day/night flag. Daily icons use
`conditionDay` and that same current night state, ignoring `conditionNight`.
Those rendering choices belong to WeatherData and cannot be overridden with
different payload text.

This firmware redraws through its running weather service's observer of
`time_12_24`; BYD Weather notifies that observer after verifying the committed
data. A recent successful update confirms the provider write and readback;
it does not acknowledge rendering on the head unit. The firmware contract,
artifact hashes, and REA evidence IDs are retained in
[the test fixture](app/src/test/resources/byd-weatherdata-2.9.5-contract.json).

Location and weather-fetch failures keep the previous weather data. Failed updates
leave the saved success timestamp unchanged. Failed provider writes attempt
to restore the previous data.

Failures retry after five minutes when prerequisites remain available. Otherwise,
the app waits for location or network recovery; revoked location permission must be
restored before updates can resume. An air-quality fetch failure does not block
the weather update.

## Troubleshooting and limits

| Symptom | Check |
| --- | --- |
| No internet or internet check pending | Connect Wi-Fi/mobile data and complete any sign-in screen. Background updates resume when Android confirms access. |
| Location unavailable | Check Android location settings and permission. Try Wi-Fi/cell only if GPS is unreliable; the car must expose a working network provider. |
| Wi-Fi/cell location provider unavailable or off | Enable network location in Android settings if offered. An installed provider APK alone does not establish that it is bound and delivering fixes. |
| Weather download failed | The message distinguishes DNS, timeout, secure-connection, and API errors. Check internet access; check date/time for secure-connection failures. |
| Forecast data could not be read | Retry the update or update BYD Weather. The previous weather is kept. |
| BYD widget update failed | Check access to the stock weather provider and firmware compatibility. |
| Updates stop after reboot | Check both startup switches, background location permission, and DiLink's background settings. |
| A settings screen is unavailable | Open the relevant system settings from the car launcher. |
| Last successful time stays unchanged | A location fix or weather request alone is not enough; the provider update must succeed. |

Provider access, location delivery, and automatic startup depend on DiLink firmware.
Automated tests cover parsing, navigation, and icon rendering; they do not
establish those behaviors on a physical head unit.

## Build

Use JDK 17, Android SDK Platform 35, and Build Tools 35.0.0. Configure the SDK
location through `ANDROID_HOME` or `sdk.dir` in a local `local.properties` file.
The Gradle wrapper downloads Gradle 8.9 on first use.

```sh
git clone https://github.com/ibramaswadeh/Byd-Weather.git
cd Byd-Weather
./gradlew assembleDebug
```

The debug APK uses the Android debug key. For a release build:

```sh
./gradlew assembleRelease
```

To sign that release, set all three environment variables before building:

| Variable | Value |
| --- | --- |
| `BYD_WEATHER_KEYSTORE` | Absolute path to your keystore |
| `BYD_WEATHER_STORE_PASSWORD` | Keystore password |
| `BYD_WEATHER_KEY_PASSWORD` | Password for the `byd-weather` key alias |

| Build | APK output |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release with signing configured | `app/build/outputs/apk/release/app-release.apk` |
| Release without signing configured | `app/build/outputs/apk/release/app-release-unsigned.apk` |

An unsigned APK must be signed before installation. Keep the same signing key
for updates. Switching from a debug key to a different release key requires
uninstalling the existing app, which removes its local settings.

## Validation

Run unit tests, release lint, and the release build:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

With a device or emulator connected, run the icon rendering tests:

```sh
./gradlew connectedDebugAndroidTest
```

The [GitHub Actions workflow](.github/workflows/android.yml) also runs these
checks and captures icons on Android 10 emulators at head-unit and phone sizes.

## License and credits

Licensed under [AGPL-3.0-only](LICENSE). The weather adapter originates from
[BYD Extend](https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview).

Weather data comes from Open-Meteo; attribution appears in the app and weather
payload. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for credits.
