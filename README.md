# BYD Weather

Updates the stock BYD weather widget with current conditions, hourly forecasts,
and seven future days from [MET Norway](https://www.met.no/en). A background service
handles scheduled updates; the app provides setup, update progress, and failure details.

**[Download the signed APK](https://github.com/ibramaswadeh/Byd-Weather/releases/latest)**
· [Release history](https://github.com/ibramaswadeh/Byd-Weather/releases)

## Compatibility

- Android 8.0 (API 26) or later on a Chinese DiLink head unit.
- An accessible stock BYD WeatherData provider and widget.
- Working GPS or an enabled Android network-location provider, plus usable internet.
- Location permission and permission to run in the background through DiLink's app manager.

The integration was inspected against DiLink 3.0 firmware `18.1.2.2601230.1`,
WeatherData `2.9.5.250616`. Compatibility depends on the installed firmware;
static analysis and automated tests do not establish operation on every head unit.
The app requires no root, ADB, or weather API key.

## Setup

1. Download and install the signed APK, then open **BYD Weather**. Disable weather
   updates in any other app that writes to the same stock widget.
2. Enable **Keep weather updated** and grant location permission. Use
   **Grant background location** to allow access all the time when Android offers it.
3. Enable Android location and connect to the internet. Tap **Update weather now**,
   check the result, then open the stock widget to check the forecast.
4. For startup after reboot, enable **Start automatically with the car**. Expand
   **Car setup and data coverage**, open **Open BYD background-start settings**,
   and allow startup and background operation in DiLink's app manager.
5. Set the refresh interval: **15 minutes** by default, adjustable from **5 to 180 minutes**.

Optional: under **Car setup and data coverage**, tap **Enable stock weather refresh button**
and enable **BYD Weather refresh button** in Android Accessibility settings.
This lets a tap on the stock refresh button also request a BYD Weather update;
the stock app can still perform its own refresh action.

When updating an existing installation, install over it using the same signing key,
then tap **Update weather now** to replace the stored weather data.

## Location and updates

Location is automatic: GPS callbacks and optional NMEA first, with Wi-Fi/cell
fallback after ten seconds without a usable fix. If GPS or precise permission is
unavailable, the network request starts immediately. A working Android network
provider is required for fallback; Wi-Fi or mobile internet alone is insufficient.

City names come from Android `Geocoder`. If it cannot return a name, the app shows
**Current location** and continues. The status shows the successful location source
and reports the stage that failed when an update cannot finish.

The app validates weather data, writes it to the stock provider, reads it back,
and requests a widget refresh. A successful timestamp confirms that write and
readback. Weather download failures keep the previous data. Failed updates retry
after five minutes when prerequisites are available.

## What the stock widget can show

| Item | Behavior or limit |
| --- | --- |
| Forecast | Hourly weather and tomorrow through day seven. Times follow the car's time zone. |
| Night artwork | Clear weather can use the moon card, while its stock English label remains **Sunny**. **Cloudy** uses the sun-behind-cloud card at night too. |
| Conditions | Stock labels and artwork group several MET conditions together. Changing source text cannot replace the factory labels. |
| Visibility | MET does not supply it. The stock detail screen may show `-1`. |
| UV | Clear-sky forecast; unavailable when MET omits it. |
| Air quality | Norwegian coverage only. Its health-risk index appears in BYD Weather; the stock AQI category stays unavailable because it uses a different scale. |
| Today's high/low | Summaries of available forecast hours, including earlier hours saved by the app; the first update may cover only the remaining day. |

See [update behavior and coverage](docs/behavior-and-coverage.md) for provider timing,
forecast summaries, caching, API endpoints, and firmware rendering limits.
The [MET contract](docs/met-weather-contract.md) records field mappings and source references;
the [location contract](docs/gps-location-contract.json) records fix validation and cleanup.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No internet or internet check pending | Connect Wi-Fi/mobile data and complete any sign-in screen. Updates resume when Android confirms access. |
| Location unavailable | Enable Android location and grant permission. Automatic fallback needs a working network-location provider on the head unit. |
| Weather or sunrise/sunset download failed | Read the stage-specific DNS, timeout, secure-connection, or API error. Check internet access and the car's date/time. Required astronomy failures keep the previous weather. |
| Hourly or daily forecasts are empty | Install the latest release, tap **Update weather now**, check its result, then reopen the widget. |
| Weather data is incomplete or could not be read | Check the car's date, time, and time zone, then retry. Previous weather is kept. |
| BYD widget update failed | Check stock-provider access and firmware compatibility. |
| Updates stop after reboot | Check the startup switch, background location permission, and DiLink's background-start settings. |
| A settings screen is unavailable | Open the relevant system settings from the car launcher. |
| Successful update, unexpected night icon or text | Check the stock renderer limits above; a successful write does not confirm a visible redraw. |

## Build and validate

Use **JDK 17**, **Android SDK Platform 35**, and **Build Tools 35.0.0**. Set
`ANDROID_HOME` or `sdk.dir` in a local `local.properties` file. The Gradle wrapper
downloads Gradle 8.9 on first use.

```sh
git clone https://github.com/ibramaswadeh/Byd-Weather.git
cd Byd-Weather
./gradlew assembleDebug
./gradlew testDebugUnitTest lintRelease assembleRelease
```

For device/emulator icon-rendering tests, run `./gradlew connectedDebugAndroidTest`.
Unit tests cover location acquisition, HTTP caching, native JSON decoding, forecast
mapping, provider refreshes, and screen layout. Physical head-unit behavior still
requires device testing.

To sign a release during `assembleRelease`, set all three environment variables:

| Variable | Value |
| --- | --- |
| `BYD_WEATHER_KEYSTORE` | Absolute keystore path |
| `BYD_WEATHER_STORE_PASSWORD` | Keystore password |
| `BYD_WEATHER_KEY_PASSWORD` | Password for the `byd-weather` key alias |

| Build | APK output |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Signed release | `app/build/outputs/apk/release/app-release.apk` |
| Release without signing configured | `app/build/outputs/apk/release/app-release-unsigned.apk` |

An unsigned APK must be signed before installation. A debug APK uses a different
key from the published release. Changing signing keys requires uninstalling the
existing app, which removes its local settings. Keep keys and passwords outside Git.

## License and credits

[AGPL-3.0-only](LICENSE). The weather adapter originates from
[BYD Extend](https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview).
MET Norway data is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/);
the app derives forecast summaries and maps MET symbols to stock widget categories.
Attribution appears in the app and payload. See [third-party notices](THIRD_PARTY_NOTICES.md)
and MET's [terms of service](https://docs.api.met.no/doc/TermsOfService.html).
