# BYD weather API and Open-Meteo mapping

Research date: 2026-10-01. Baseline: `ibramaswadeh/Byd-Weather` commit `d0b8668581339d74c62cf0bf74d563d91982656e`.

## Confirmed current day/night omission

Open-Meteo supplies numeric `is_day` for current and hourly weather: `1` means daylight and `0` means night. BYD Weather and BYD Extend request it for current weather, but their baseline mappings discard it there. Both write hourly values as the boolean `data.hourlys.hourlyweathers[].isdaynight`, with `true` for day. [1], [2], [3], [12]

Version 1.1.1 preserves the current value at `data.condition.isdaynight`, following that hourly convention. This is an adapter extension: the public sources examined do not establish that the stock BYD app parses this current field or uses it to select an icon. No current-condition `isDayTime`, `isDaytime`, or native day/night model was found in the examined BYD Extend and Denza adapter sources. A physical head-unit check is still needed. [2], [4], [12]

## BYD online service and local provider

The Denza Lab author's investigation reports in-car validation against DiLink 5.1 and stock `com.byd.weatherdata` version `2.9.36.260424`. It identifies the production host `data-weather-cn.denzacloud.com` and a coverage problem for its tested Russian location. It does not publish an official endpoint path, authenticated request contract, or complete response schema. This research did not access that proprietary service or use credentials. The host is an observation for that Denza firmware, not a verified universal BYD endpoint. [5]

The same investigation documents:

```text
content://com.byd.weatherdata.utils.WeatherContentProvider/weather
```

The provider's `name` column holds JSON containing `resultcode`, `resultinfo`, `servertime`, and a decoded `data` object. It reports that `com.byd.weatherdata.action.THIRD_REFRESH` refreshes the launcher and that stock `RequestService` observes the `time_12_24` settings URI for widget refresh. BYD Weather uses these mechanisms. These are observed firmware contracts, not a published BYD SDK specification. [3], [5], [6]

## Remaining gaps and uncertainties

| Area | Baseline behavior | Finding |
| --- | --- | --- |
| Hourly key spelling | Lowercase `isdaynight`. | BYD Extend agrees; the independent Denza adapter uses capitalized `Isdaynight`, true for symbols ending `_day`. JSON keys are case-sensitive. Neither adapter proves what the native parser accepts. [2], [4], [12] |
| Daily day/night conditions | The same daily code populates `conditionDay` and `conditionNight`. | Open-Meteo's daily code is the most severe condition over the whole day, with no separate daily day/night codes. Separate conditions require defined aggregation of a longer hourly forecast; eight requested hours cannot cover all 16 output days. [1], [2], [3] |
| Time zones | Emits sunrise/sunset and `city.timezone`, but parses and formats offset-free timestamps in the device zone. | `timezone=auto` returns local times for the coordinates. A different device zone produces wrong epochs for current updates, hourly dates, daily dates, sunrise, and sunset. Use the response zone for parsing and formatting. [1], [2], [3] |
| Weather IDs | Copies a mapped WMO ID into `cnweatherid`, `weatherid`, and `zmweatherid`. | This follows BYD Extend; public sources do not establish that these native ID systems are interchangeable or define separate day/night IDs. [2], [4], [12] |
| AQI | Requests European AQI, PM2.5, PM10; populates today's daily AQI; leaves `aqidays` empty. | Open-Meteo also offers pollutant gases, U.S. AQI, and hourly AQI forecasts. Denza's adapter contains `o3`, `co`, `no2`, `so2`; their native units and requirements remain unverified. [2], [3], [4], [7] |
| Alerts/life indices | Empty `alarm` and `liveInfos`. | The documented forecast and air-quality interfaces examined do not provide BYD alert/lifestyle objects. These need another source and schema, rather than being fetched values silently dropped. [1], [2], [7] |
| City/extra native metadata | Blank country fields and a relatively small condition object. | Denza's adapter also emits coordinates, administrative metadata, condition expiry/descriptions, moon/extra daily fields, and a radar placeholder. Another adapter's shape does not prove universal widget requirements. [2], [4] |

The eight hourly items, 16 daily items, and today's daily index 1 are adapter assumptions paired with `past_days=1`, not verified universal BYD requirements. Denza's independent adapter emits up to 48 hours and nine days. [2], [3], [4]

The baseline's AQI descriptions and thresholds match the documented European categories (0–20, 20–40, 40–60, 60–80, 80–100, above 100). Native field names do not establish what scale the stock UI expects; do not assume these are Chinese AQI values. [2], [7]

## Current UV/visibility and WMO code uncertainty

Official Open-Meteo documentation says hourly weather variables are available as current conditions. Its controller shares the variable loader, and the surface-variable enum includes `is_day`, `visibility`, and `uv_index`. These current parameters are supported by the examined official docs/source. Null or missing visibility/UV values still reject updates under the baseline's strict mapping. A live probe in this research did not complete before the workspace failed; no particular live response is claimed here. [1], [2], [3], [8], [9]

At the examined revisions, the website WMO table lists `97` as heavy thunderstorm, while the server enum lists only `95`, `96`, and `99` for thunderstorms. The baseline maps the latter and rejects `97`. This disagreement does not prove that the running API emits `97`; reconcile the revision or observe a real response before treating it as a confirmed missing live mapping. [2], [10], [11]

## Follow-up order

1. Verify the current/hourly native field spelling and polarity using the installed stock APK or provider records captured during known day and night. The mapper tests establish the adapter output only. [2], [4], [5]
2. Correct timestamp handling to use the response time zone. [1], [2]
3. Define the native AQI scale, pollutant units, and forecast format before extending AQI mapping. [4], [7]
4. Define hourly aggregation before creating separate daily conditions. Keep unavailable alert/lifestyle values empty. [1], [4], [7]

## Sources

[1]: https://github.com/open-meteo/open-meteo-website/blob/a8dd5c8eef07583f983938c40b150776035f4b8e/src/routes/en/docs/%2Bpage.svelte
[2]: https://github.com/ibramaswadeh/Byd-Weather/blob/d0b8668581339d74c62cf0bf74d563d91982656e/app/src/main/java/com/ibramaswadeh/bydweather/WeatherMapping.java
[3]: https://github.com/ibramaswadeh/Byd-Weather/blob/d0b8668581339d74c62cf0bf74d563d91982656e/app/src/main/java/com/ibramaswadeh/bydweather/WeatherRuntime.java
[4]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/NativeWeatherPayload.kt
[5]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/docs/weather-adapter-findings.md
[6]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/NativeWeatherStore.kt
[7]: https://github.com/open-meteo/open-meteo-website/blob/a8dd5c8eef07583f983938c40b150776035f4b8e/src/routes/en/docs/air-quality-api/%2Bpage.svelte
[8]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/ForecastapiController.swift#L295
[9]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/VariableHourly.swift
[10]: https://github.com/open-meteo/open-meteo-website/blob/a8dd5c8eef07583f983938c40b150776035f4b8e/src/lib/components/variables/wmo-codes-table.svelte
[11]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Helper/WeatherCode.swift
[12]: https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview/blob/37fbd69f2507433126aa1d4b615b066f807c4c8f/app/src/main/java/com/byd/extend/WeatherMapping.java

Open-Meteo sources are provider-owned documentation/server source. BYD Extend and Denza Lab are primary sources for their adapters and the Denza author's observations, not official BYD API documentation.
