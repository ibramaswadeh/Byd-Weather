# BYD Weather

Updates the firmware's stock BYD weather display with local forecasts from
[Open-Meteo](https://open-meteo.com/). Runs as a background service on compatible
DiLink head units. The app contains settings, permissions and refresh controls.
It does not provide an additional widget or forecast renderer.

## Features

- All 29 documented WMO conditions mapped to the firmware's native condition IDs.
- Current weather, hourly/daily forecasts and available compatible native metadata.
- Location names from BigDataCloud as `district, city`, with duplicate names and
  trailing `Sub-District` labels removed.
- Updates every 15 minutes by default, adjustable from 5 to 180 minutes.
- Optional startup with the car and a persistent status notification.
- Manual updates from the app or the optional stock-refresh Accessibility handler.

## Requirements and setup

Android 8.0/API 26 or later; designed around DiLink on Android 10. The head unit
must expose the stock BYD WeatherData provider, fresh GPS NMEA messages and a
validated internet connection. Precise/background location and DiLink background
startup permissions are required where applicable. No root, ADB or API key is needed.

1. Install the APK and open BYD Weather. Disable another app's stock-weather updater
   if one is already active.
2. Enable **Keep weather updated** and grant precise location. Use **Grant background
   location** for updates after startup where Android requires it.
3. Enable **Start automatically with the car** and allow background operation in
   **Open BYD background-start settings**.
4. Enable GPS and usable internet, then tap **Update weather now**. Confirm the stock
   widget updates and check the saved successful-update timestamp/location.
5. Choose your refresh interval.
6. Optional: use **Enable stock weather refresh button** and enable the BYD Weather
   refresh Accessibility handler. It observes stock WeatherData taps only.

## Data coverage and firmware limits

The [stock-widget audit](docs/research/stock-widget-data-audit.md) traces each
rendered attribute and each usable declared field back to the actual firmware,
its units and Open-Meteo. The [native inventory](docs/research/native-field-coverage.json)
records all 371 declarations, 29 classes and 211 distinct JSON paths, distinguishing
mapped/derived/compatibility values from unavailable, unused and unverified fields.

Every safely sourced stock-display weather attribute is mapped. Current pressure,
precipitation, cloud cover, feels-like/gust values, period humidity/cloud/probability/
gust-force values, daily feels-like/lunar values and source metadata also retain
verified declared mappings, even where this firmware's renderer does not show them.
Only variables used by those mappings are requested: 13 current, 9 hourly and
12 daily forecast variables, plus optional current/hourly air-quality data.

The removed renderer's raw `data.openMeteo` extension is not sent. Unsupported
hourly precipitation fields and source-only requests for rain/snow totals, dew
point, sunshine duration and other unused metrics have been removed. Native
period rain/snow/totalLiquid and daily pressure/visibility have unverified units
or intervals; similarly named source values are not sufficient proof of a correct
conversion. Official alerts, radar, lifestyle advice and administrative IDs are
not fabricated.

The stock hourly renderer applies one global night state to all icons and ignores
individual hourly day/night flags. Verified sunrise anchors correct its pre-dawn
state, but a row crossing sunrise/sunset still cannot select sun/moon separately
without changing the native renderer. Native UV/wind labels also have fixed ranges.
European AQI differs from the native pollution-category scale; values/descriptions
are retained as data, but neutral category 0 avoids misleading native labels.

## Refresh behavior

The service waits for validated internet and collects a fresh NMEA position for
up to 60 seconds. Cached Android/network positions are not used. GPS listeners
are released after acquisition ends or is cancelled. The fresh position goes to
BigDataCloud's device-client endpoint for a localized district/city name. Missing
or failed geocoding falls back to **Current location**; optional AQ failure does
not block weather.

Payloads are validated before native writes. Unknown/fractional weather codes or
invalid day flags reject the update, including beyond the displayed hourly slice.
Only successful provider write/readback advances the saved timestamp/location
and normal refresh cadence. Provider failure attempts to restore the previous
native row; failures retry after five minutes while prerequisites are available,
otherwise the service waits for their recovery. Removing the additional widget
also removes its cache and separate fetch-success state.

Provider access, GPS delivery and background startup depend on the firmware.
Automated/native-method checks do not establish physical-car behavior.

## Build and validation

Use JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Set `ANDROID_HOME` or
`sdk.dir` in local.properties. The wrapper downloads Gradle 8.9 on first use.

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

Release output is `app/build/outputs/apk/release/app-release-unsigned.apk` unless
all signing variables are set: `BYD_WEATHER_KEYSTORE`, `BYD_WEATHER_STORE_PASSWORD`,
`BYD_WEATHER_KEY_PASSWORD`. The key alias is `byd-weather`; signed Gradle output is
`app/build/outputs/apk/release/app-release.apk`. Keep the existing signing key for
updates. The authorized delivery remains version 1.1.1/code 3.

[CI](.github/workflows/android.yml) tests, lints and builds a release APK with
checksum/check-report artifacts. Device icon instrumentation is available through
`./gradlew connectedDebugAndroidTest` when a device/emulator is connected.

## License and credits

Licensed under [AGPL-3.0-only](LICENSE). Data by [Open-Meteo](https://open-meteo.com/),
location names by [BigDataCloud](https://www.bigdatacloud.com/). See
[third-party notices](THIRD_PARTY_NOTICES.md) for source attribution.
