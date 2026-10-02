# Hourly night icons: initial evidence and firmware follow-up

The [DiLink 3.0 firmware audit](firmware-weather-contract.md) now establishes the native parser and icon logic for the user's supplied firmware. Its hourly renderer ignores per-hour flags and derives a global night state from daily timestamps. A native-method replay reproduces the 03:00 sun; anchoring daily records to real sunrise corrects pre-dawn classification. Mixed day/night lists still use one global state. The investigation below records the earlier evidence and uncertainty before the APK was available.

Research date: 2026-10-02. The reported symptom is a sun icon beside the stock widget's 03:00 hourly forecast. This note distinguishes source data and adapter output from the stock widget's consumption rules.

## What is established

The current mapper preserves each selected Open-Meteo hour's `is_day` as a Boolean in both `isdaynight` and `Isdaynight`. The parent investigation's live reference request returned `is_day: 0` at 03:00, and its mapper replay emitted both fields as `false`. That establishes correct night flags for the inspected request and replay; it is not a provider capture from the user's car.

The known community native adapters encode clear weather as ID `0` and partly cloudy weather as ID `1` independently of day or night. Denza's mapper discards the MET symbol's `_day`/`_night` suffix when choosing the condition ID. Its hourly payload separately emits `Isdaynight: point.symbol.endsWith("_day")`. [1], [2]

BYD Extend uses the lowercase spelling `isdaynight` and maps Open-Meteo `is_day == 1` to a Boolean. It also assigns the same mapped condition ID to `weatherid` and `zmweatherid` regardless of night. These are the sources of the compatibility spellings already emitted by this app. They are community adapter implementations, not proof of the stock parser's accepted field names, polarity, or icon-selection behavior. [3]

Denza's first-hand native validation identifies DiLink 5.1 and stock `com.byd.weatherdata` version `2.9.36.260424`. It verifies provider replacement/read-back, temperature, refresh service and widget update notifications. The published findings do not establish that a clear hourly forecast at night renders a moon, or identify the exact method that selects its icon. [4]

The temporary Robolectric replay passed through the actual public mapper with the recorded Open-Meteo response, setting the cutoff to `2026-10-01T03:00` in `Asia/Amman`. The first native hour was `date: 1790812800000` (2026-10-01 00:00 UTC), `weatherid/cnweatherid/zmweatherid: 0`, and both day/night flags `false`. The replay source and first-hour JSON are clearly marked diagnostic files under `/workspace/scratch/byd-build`; the temporary test was removed after collecting the signal. This verifies the application boundary, not the stock renderer or the user's provider row.

A separate device time-zone check matters when interpreting the displayed hour: a native renderer using device UTC would label the Amman 06:00 instant as 03:00. That is a conditional timestamp example, not a confirmed cause of this report. The actual device zone and provider row are needed before treating a clock label as a particular source hour.

The user subsequently confirmed the car is set to Amman/GMT+3 and that the widget's release date is correct. A different device timezone is therefore not the reported configuration, and this change does not shift timestamps to compensate for a hypothetical UTC setting. Investigation now needs the stock hourly parser/icon selector or a native payload capture.

## What remains unknown

No inspected public source contains the stock hourly JSON parser or icon-selection code. Therefore the available evidence does not establish whether this firmware's hourly widget reads either Boolean spelling, reads another spelling/type, derives night from timestamps, or selects night through a different weather-ID field. The reported rendering is evidence that the current compatibility payload is insufficient for that widget; it does not by itself identify which contract detail differs.

Changing clear weather to an invented night ID, reversing the Boolean, changing it to an integer, or borrowing an AccuWeather/Huawei icon enumeration would be a guess. Those systems' enumerations cannot establish BYD's native ID contract. Retain truthful source night flags while obtaining the consumer evidence.

## Searches and limits

The investigation inspected the pinned Denza payload, condition mapper and native findings; searched public GitHub code for `com.byd.weatherdata`, `zmweatherid`, `cnweatherid`, `Isdaynight`, `WeatherData_WeatherWidgetProvider`, `WeatherData.apk`, and related weather/night terms; and searched repositories for BYD WeatherData, BYD firmware, DiLink APK and Denza firmware. Exact `zmweatherid` results were the existing BYD Weather, BYD Extend and Denza community adapters. No stock parser was found.

The current public Denza repository tree includes adapter source, tests and firmware-extraction scripts, but no stock WeatherData APK or decompiled WeatherData parser. Its current mapper blob has the same day-independent condition-ID mapping. A firmware catalogue URL appears in Denza's extraction notes, but a catalogue download/extraction was not completed here; this note makes no claim to have inspected that firmware's WeatherData binary. [5]

GitHub search is bounded by its indexed public content. Absence from these results does not prove that the artifact is unavailable elsewhere.

## Concrete next evidence

The installed stock **`com.byd.weatherdata` APK**, including any split APKs, is the most useful artifact. Its parser and hourly icon renderer can determine the accepted field name, type and true/false meaning, and whether an independent night weather ID is required. The installed package version should accompany it because the contract can vary by firmware.

A provider `name` JSON capture immediately after this app refreshes, plus a capture from the stock app's own successful update in a supported region, would additionally distinguish payload, stale refresh, timestamp and consumer behavior. The user does not need to change region merely to supply the APK. The relevant provider URI is `content://com.byd.weatherdata.utils.WeatherContentProvider/weather`. [4]

With authorized ADB access, list the installed package paths using `adb shell pm path com.byd.weatherdata` and pull every returned APK. Inspect `adb shell dumpsys package com.byd.weatherdata` for the version. These are suggested artifact-collection commands; they were not executed against the user's car during this investigation.

## Primary sources

[1]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/WeatherCodeMapper.kt
[2]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/NativeWeatherPayload.kt
[3]: https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview/blob/37fbd69f2507433126aa1d4b615b066f807c4c8f/app/src/main/java/com/byd/extend/WeatherMapping.java
[4]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/docs/weather-adapter-findings.md
[5]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/research/telematics-firmware/README.md
