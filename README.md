# BYD Weather

Keeps the stock BYD weather widget updated with local forecasts from
[Open-Meteo](https://open-meteo.com/). Runs as a background service on compatible
Chinese DiLink head units, with a small settings screen for permissions and
refresh controls.

## Features

- Current weather, hourly and daily forecasts, and air quality when available.
- Widget location names from BigDataCloud, formatted as `district, city`.
- Automatic updates every 15 minutes by default; adjustable from 5 to 180 minutes.
- Optional startup with the car and a persistent status notification.
- Manual updates from the app or, optionally, the stock widget's refresh button.
- Adaptive launcher icons and monochrome icons for Android 13+.

## Requirements

The APK requires Android 8.0 (API 26) or later. The integration is designed around
DiLink on Android 10; installation alone does not establish compatibility with
a particular car or firmware.

The head unit must provide:

- Access to the stock BYD WeatherData provider and widget.
- GPS that exposes fresh NMEA messages to the app.
- Precise location permission, plus background location permission for startup
  where Android requires it.
- An internet connection that Android recognizes as usable.
- Permission to start and run in the background through DiLink's app manager.

The app does not require root, ADB, or a weather API key.

## Setup

1. Install the APK and open **BYD Weather**. If another app updates the same
   widget, disable its weather updates first.
2. Enable **Keep weather updated** and grant precise location permission.
   Use **Grant background location** to allow location access all the time
   when Android offers that option.
3. Enable **Start automatically with the car** if you want automatic startup.
   Open **Open BYD background-start settings** and allow startup and background
   operation in DiLink's app manager.
4. Turn on GPS, connect to the internet, then tap **Update weather now**.
   Check the app's status and last successful update time, and confirm the
   stock widget changes.
5. Set your preferred refresh interval.
6. Optional: tap **Enable stock weather refresh button** and enable
   **BYD Weather refresh button** in Android Accessibility settings.

Accessibility is only needed for updates triggered by the stock widget's button.
The stock weather app can still perform its own refresh action.

## How updates work

The service waits for usable internet, then collects a fresh GPS fix from NMEA
messages for up to 60 seconds. Cached locations and network-based coordinates
are not used. GPS listeners are released after acquisition finishes or stops.

The app sends that fresh fix directly to [BigDataCloud](https://www.bigdatacloud.com/)
to resolve the widget location name. It combines the returned locality (district
or suburb) and city as `district, city`, using the device language. If only one
name is available, it uses that name; matching names appear once. Failed or empty
lookups use **Current location** and allow the weather update to continue. No
geocoding API key is needed. See the [BigDataCloud research note](docs/research/bigdatacloud-geocoding.md)
for the response fields and the fresh-device-location requirement.

After fetching weather, the app writes it to the BYD provider, reads it back,
and requests a widget refresh. A successful update advances the saved timestamp.

Current weather and hourly forecasts include an `isdaynight` boolean derived
from Open-Meteo's `is_day`: `true` means daytime and `false` means night. A missing
or invalid current flag fails the update, preserving the previous weather data.
The current flag follows the existing hourly convention; the stock widget's
support for that current field still needs verification on a physical head unit.
See the [BYD weather API audit](docs/research/byd-weather-api.md) for sources,
remaining mapping gaps, and compatibility limits.

GPS and weather-fetch failures keep the previous weather data. Failed updates
leave the saved success timestamp unchanged. Failed provider writes attempt
to restore the previous data.

Failures retry after five minutes when prerequisites remain available. Otherwise,
the app waits for GPS or network recovery; revoked location permission must be
restored before updates can resume. An air-quality fetch failure does not block
the weather update.

## Troubleshooting and limits

| Symptom | Check |
| --- | --- |
| Waiting for GPS or internet | Enable GPS, grant precise location, and check that Android recognizes the internet connection. |
| Raw GPS unavailable | The head unit must expose fresh NMEA messages. The app retries after five minutes. |
| Update failed | Check the reported error, network access, and access to the BYD weather provider. |
| Updates stop after reboot | Check both startup switches, background location permission, and DiLink's background settings. |
| A settings screen is unavailable | Open the relevant system settings from the car launcher. |
| Last successful time stays unchanged | A GPS fix or weather request alone is not enough; the provider update must succeed. |

Provider access, GPS delivery, and automatic startup depend on DiLink firmware.
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

The [GitHub Actions workflow](.github/workflows/android.yml) runs unit tests,
release lint, and the release build, then uploads an unsigned APK and its SHA-256
checksum. Connected icon rendering tests require a device or emulator.

## License and credits

Licensed under [AGPL-3.0-only](LICENSE). The weather adapter originates from
[BYD Extend](https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview).

Weather data comes from Open-Meteo; attribution appears in the app and weather
payload. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for credits.
Location names come from BigDataCloud's free client reverse-geocoding API.
