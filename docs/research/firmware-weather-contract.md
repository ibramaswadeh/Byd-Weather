# Weather contract in the supplied DiLink 3.0 firmware

Research date: 2026-10-02. This supersedes the native-parser uncertainty in [the earlier hourly investigation](hourly-night-icons.md) for **this extracted firmware**. The stock APK proves that its hourly icon renderer ignores the hourly day/night flag and uses one solar-state decision for the entire list. It also exposes a pre-dawn classification error when daily `publictime` values are calendar midnights. This is native consumer evidence, not a conclusion inferred from another community adapter. [3], [4], [7], [8]

## Artifacts and method

The user supplied the [original iCloud firmware share][1] named `Di3.0_1for2_18.1.2.2601230.1_0`. The downloaded outer ZIP has SHA-256 `50434a6f5dd501f8d10bf9e5e47ef3e4f0b1354363ecac1ddd877b140589263a`. Its [A/B OTA metadata](/workspace/scratch/byd-firmware/ota-metadata.txt) identifies `BYD-AUTO/DiLink3.0/DiLink3.0:10/QKQ1.210910.001/eng.build.20260123.222055:user/release-keys`, external version `18.1.2.2601230.1`, Android 10/API 29, and security patch `2023-02-05`. The January firmware build date differs from the bundled weather APK's June 2025 version date. [2]

| Primary artifact | Identity |
| --- | --- |
| `system/app/WeatherData/WeatherData.apk` | Package `com.byd.weatherdata`, version `2.9.5.250616`, code `60`, 33,148,201 bytes; SHA-256 `f585062b2b393e3a87019da3b40c2daa089e60ddacdfab189c22c67f2279281a`. [3] |
| `system/priv-app/Launcher3/Launcher3.apk` | 10,517,036 bytes; SHA-256 `2e657eb3f0bb94fdb23aa6f2ed06f6bdac6f2476175bc716f34e33a4cb9dcaae`. [2] |
| Adapter comparison | BYD Weather 1.1.1/code 3, repository commit `31a41f80fe56a0a86ddc577503940dc64a2059b8`, before firmware-driven changes. [12] |

The OTA partitions were extracted without executing firmware/APK code. The weather APK's three DEX files were disassembled with Android SDK `dexdump`; JADX 1.5.6 provided readable Java for the DTOs and consumer methods. Field names/types below were cross-checked against `classes2.dex`, rather than accepted solely from decompiler output. The source references identify the original APK hash, class and method, and the local inspection output. Decompiled line numbers are navigation aids rather than original source-line guarantees. The APK was not installed on the user's vehicle during this audit; firmware version agreement with the installed package remains a separate fact to establish. [2], [3], [4], [5]

No service/signing credentials were used or documented. Service request authentication is outside this schema investigation.

## Provider, parser and model boundaries

`WeatherContentProvider` directly stores the `weather` SQLite table. `DbOpenHelper` declares `_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, `name TEXT`, and `altitude TEXT`. `insert`/`update` notify the content resolver; they do not parse or validate JSON. Consequently, a successful write/read-back is insufficient to establish native UI parsing. The consumer URI is `content://com.byd.weatherdata.utils.WeatherContentProvider/weather`. [6]

`WeatherQueryUtil.queryWeatherBean(Context)` reads the **first provider row's `name` column** and calls an ordinary `new Gson().fromJson(..., WeatherInfoBean.class)`. Parse failure or an empty result returns a new, unpopulated bean. Its model is `com.byd.weatherlibrary.WeatherInfoBean`. `RequestService.getWeatherInfo(JsonObject)` and `processWeatherData()` instead parse `com.byd.weatherdata.WeatherBean`. These are two different native schemas in the same APK. [4], [5], [9]

| Model | Top-level `data` fields |
| --- | --- |
| `WeatherInfoBean` (widget/dialog library) | `alarm`, `city`, `condition`, `dailys`, **`hourlys`**. |
| `WeatherBean` (service) | `alarm`, **`aqi`, `aqidays`**, `city`, `condition`, `dailys`, **`liveInfos`, `mobilelink`, `radar`, `weatherDesc`**; no `hourlys`. |

Neither model has `@SerializedName` aliases or a custom field naming policy. The bundled Gson uses its identity naming strategy, exact field-name map lookup, and skips unknown JSON keys. Thus the lowercase hourly `isdaynight` is parsed, while `Isdaynight` is ignored by this parser. Missing boxed fields remain null. A current `condition.isdaynight` and hourly `precipitation` are also unknown to these DTOs. [4], [5], [10]

The declared `String` type is not necessarily a hard JSON-token restriction: the bundled Gson String adapter accepts numeric tokens through `JsonReader.nextString()`. BYD Weather's numeric period `precProb` and numeric `aqi.lv` therefore coerce to strings with this parser. That is a type difference to record, not a demonstrated parse failure. Boolean parsing accepts Boolean tokens or strings; numeric `0`/`1` is not the native Boolean representation. [10]

The stock cloud-response transform is `NumberUtils.dealWeatherJsonData(JsonObject)`: it requires the string success code `"0"`, reads `data.weatherData`, hex-decodes it, decompresses GZIP to UTF-8, and puts the decoded JSON object under `data`, preserving `resultcode`, `resultinfo`, and `servertime`. The service stores that decoded envelope in `name`. A local provider replacement should use the decoded envelope, as the adapter already does. [6], [7], [9]

## Icon consumer and the 03:00 symptom

The hourly DTO declares `Boolean isdaynight` and a matching `getIsdaynight()` accessor. Its renderer **does not call that accessor**. `HoursAdapter` computes `isDuringNight` once from `NumberUtils.isDuringNight(weatherInfoBean)`, then selects the moon resource only when that global flag is true and the hourly **`cnweatherid`** equals `0`. Otherwise it calls `WeatherUtils.weatherConditionMap(cnweatherid)`. `weatherid` and `zmweatherid` do not select these hourly icons. [4], [7], [8]

The native night calculation is:

1. Read the current condition's `updatetime`.
2. Binary-search the sorted list of `dailyweathers[].publictime` for the preceding entry.
3. Return true only if the update instant is **strictly after that entry's `sunSet` and strictly before the next entry's `sunRise`**. Out-of-range selection, including the final daily entry, returns false. [7]

With daily midnights, 03:00 belongs to today's entry. Today's sunset is still in the future, so the first comparison is false. Correct hourly flags cannot change this path. The actual source-hour replay from the earlier investigation emitted clear ID `0` and both Boolean flags `false` at 03:00 in `Asia/Amman`; the user confirmed the vehicle is set to Amman/GMT+3 and its release date is correct. Those earlier observations are consistent with this extracted consumer's failure, although they are not a fresh provider capture from the vehicle. [7], [8], [13]

`WeatherUtils.weatherConditionMap` maps ID `0` to the sun and has no numeric code mapping to the moon resource; the moon is selected by the separate Boolean branch. Inventing a moon condition ID or reversing the hourly flag cannot repair this native path. `DaysAdapter` similarly uses a global current night flag and `conditionDay.cnweatherid`, so even its future-day clear icons can be moons when the current state is night. [8]

A **compatibility workaround**, rather than a proven original-cloud `publictime` meaning, is to anchor each daily item's numeric `publictime` to that date's real sunrise. Before today's sunrise, the binary search then chooses yesterday and checks yesterday's sunset against today's sunrise. The usage audit found only two numeric item consumers: this night search and `DaysAdapter`'s weekday/date labels through `DateUtils`. Same-date sunrise preserves the latter labels when vehicle and forecast zones agree. The widget/dialog min/max selectors use `publictimeFmt`; the service uses `moonSetFmt`. Keep those date strings unchanged. Top-level `dailys.publictime` has no non-accessor consumer in the inspected BYD code. [7], [8], [9], [11]

This workaround can correct the current pre-dawn classification. It **cannot make a mixed day/night hourly list accurate per hour**, because the stock renderer applies one state to every clear hourly item. Exact sunrise/sunset instants retain the native strict-boundary behavior. Missing/polar solar events and the forecast's last date also need explicit handling before any broader claim. [7], [8]

An isolated executable replay confirmed the distinction. The production mapper emitted a forecast with current time **2026-10-02 02:00 Asia/Amman**, yesterday at daily index 0, and the visible hourly index 1 at **03:00**, with `cnweatherid: 0` and both flags false. The oracle compiled the original DTO and resource constants plus the verbatim decompiled bodies of `NumberUtils.isDuringNight`, `WeatherUtils.weatherConditionMap`, and `HoursAdapter.getValidResourceId`; Android/UI dependencies were replaced by a minimal wrapper. It used Gson 2.10.1 for deserialization. This runs the extracted decision logic, not the full APK, Android widget, or vehicle display. Method-body SHA-256 provenance is retained. [15]

| Replay input | Original native decision |
| --- | --- |
| Unchanged mapper payload | `isdaynight=false`, `globalNight=false`, `icon=SUN`; the expected-moon assertion fails, exit 1. |
| Change only hourly flags to true | `isdaynight=true`, `globalNight=false`, `icon=SUN`; the assertion still fails. |
| Change only each daily item's numeric `publictime` to its actual `sunRise` | `isdaynight=false`, `globalNight=true`, `icon=MOON`; assertion passes, exit 0. |

The three cases were rerun successfully with those expected outcomes. The final mapper output also passes the oracle (`native-night-after.log`). For a durable before/after comparison, the baseline was recovered from the pre-change flag-only probe by restoring unchanged source-hour flags; this recovery is recorded in `native-night-payload-diff.json`. A structural comparison confirmed that the sunrise probe changed exactly the sixteen `data.dailys.dailyweathers[i].publictime` values and no other payload path. The original failed replay and sunrise probe output are retained as `native-night-red.log` and `native-night-sunrise-probe.log`. [15]

## Complete declared native DTO field inventory

This inventory includes every declared instance field in the two parsed weather DTO families. It establishes native field names and Java types, not native units, mandatory server fields, or proof that every field appears on screen. `Integer`/`Long`/`Float`/`Boolean` are nullable boxed Java types. Grouped keys all have the listed type. Unless a difference is stated, both models have the field. Each row is grounded in the named nested class in the original APK's `classes2.dex`. [3], [4], [5]

| JSON object | Native fields grouped by type |
| --- | --- |
| Envelope | `resultcode`, `resultinfo`: String; `servertime`: Long; `data`: DataDTO. |
| `data` shared | `city`: CityDTO; `condition`: ConditionDTO; `dailys`: DailysDTO; `alarm`: List<AlarmsDTO>. |
| `data` library only | `hourlys`: HourlysDTO. |
| `data` service only | `aqi`: AqiDTO; `aqidays`: List<AqidaysDTO>; `liveInfos`: List<LiveInfosDTO>; `radar`: RadarDTO; `mobilelink`, `weatherDesc`: String. |
| `city` shared | `ca`, `co`, `citycode`, `countryCode`, `countryname`, `englishCityName`, `englishCountryName`, `name`, `parentcity`, `provincename`, `timezone`: String; `administrativearea`: AdministrativeareaDTO; `supplementalAdminAreas`: List<SupplementalAdminAreasDTO>. |
| `city` differences | Library: `level`: Integer. Service: `prefectureCity`: PrefectureCityDTO. |
| `city.administrativearea` | `englishName`, `id`, **`localizedname`**: String; `level`: Integer. |
| `city.supplementalAdminAreas[]` | `englishName`, `id`, **`localizedName`**: String; `level`: Integer. |
| `city.prefectureCity` (service) | `id`: String. |
| `condition` Integer fields | `cloudCover`, `cnweatherid`, `feelTemperatureShade`, `humidity`, `pressure`, `realfeel`, `temperature`, **`uVIndex`**, `visibility`, `weatherid`, `winddegrees`, `windgustlevel`, `windgustspeed`, `windlevel`, `windspeed`, `zmweatherid`. |
| `condition` String fields | `comfortlink`, `compareFlag`, `desc`, `mobilelink`, `pressureTendency`, `realfeelDesc`, `updatetimeFmt`, **`uvIndexDesc`**, `vipLocation`, `weatherMapLink`, `weathertext`, `winddir`, `winddirtext`, `windgustdir`. |
| `condition` other fields | `expiretime`, `updatetime`: Long; `precipitation`: Float. **No day/night field.** |
| `hourlys` (library) | `expiretime`: Long; `hourlyweathers`: List<HourlyweathersDTO>. |
| `hourlys.hourlyweathers[]` | `cnweatherid`, `rainprobability`, `temp`, `weatherid`, `wp`, `zmweatherid`: Integer; `date`: Long; **`isdaynight`: Boolean**; `wd`: String. |
| `dailys` | `dailyweathers`: List<DailyweathersDTO>; `expiretime`, `publictime`: Long; `mobilelink`, `publictimeFmt`: String. |
| `dailys.dailyweathers[]` Integer fields | `aqivalue`, `lv`, `maxtemp`, `mintemp`, `pm25`, `pressure`, `spanDays`, `spanDaysFull`, `spanDaysNew`, `uvIndex`, `visibility`. |
| `dailys.dailyweathers[]` String fields | `aqivaluetext`, `currentFestival`, `currentRestrict`, `currentlink`, `mobilelink`, `moonRiseFmt`, `moonSetFmt`, `moonphase`, `publictimeFmt`, `realFeelTempMax`, `realFeelTempMin`, `source`, `sunRiseFmt`, `sunSetFmt`, `uvIndexText`. |
| `dailys.dailyweathers[]` other fields | `moonRise`, `moonSet`, `publictime`, `sunRise`, `sunSet`: Long; `conditionDay`: ConditionDayDTO; `conditionNight`: ConditionNightDTO. |
| Each `conditionDay` / `conditionNight` Integer fields | `cnweatherid`, `humidity`, `weatherid`, `windlevel`, `windspeed`, `zmweatherid`. |
| Each `conditionDay` / `conditionNight` String fields | `cloudCover`, `ice`, `iceProb`, **`precProb`**, `rain`, `rainProb`, `snow`, `snowProb`, `thunProb`, `totalLiquid`, `weathertext`, `windGustDir`, `windGustPow`, `winddir`. |
| `aqi` (service) Integer fields | `aqivalue`, `no2`, `o3`, `pm10`, `pm25`, `so2`. **No `co`.** |
| `aqi` (service) other fields | `aqidesc`, `aqivaluetext`, **`lv`**, `mobilelink`, `pm25desc`: String; `updatetime`: Long. |
| `aqidays[]` (service) | `aqi`, `aqiMax`, `aqiMin`, `lv`, `pm25`: Integer; `date`: String. |
| `alarm[]` Integer fields | `level`, `priority`. |
| `alarm[]` Long fields | `endtime`, `expiretime`, `publictime`, `relievedtime`. |
| `alarm[]` String fields | `affectarea`, `content`, `gradeDesc`, `guide`, `id`, `isEffect`, `levelName`, `mobilelink`, `releaseAgency`, `source`, `title`, `titleEn`, `type`, `zmRecommend`. |
| `liveInfos[]` (service) | `code`, `content`, `day`, **`mobilelilnk`**, `name`, `status`: String; `level`: Integer; `expiretime`, `updatetime`: Long. The misspelling `mobilelilnk` is native. |
| `radar` (service) | `dataTime`: Long; `dataseries`: **List<Integer>**; `skycon`: String. The integer series' time interval and units remain unknown. |

## Rendered fields, gaps and limits

`WeatherWidgetProvider` and `DialogActivity` choose `city.englishCityName` when the active language is English and `city.name` otherwise. This verifies the two combined district/city name targets. The service separately reads the final `supplementalAdminAreas[].localizedName`, falling back to `N/A`; its metadata path is different from the widget's visible name choice. [8], [9], [11]

`DialogActivity.handleValidData` displays current temperature, `cnweatherid` condition text, `uVIndex`, sustained `windspeed`, `windlevel`, `winddir`/`winddirtext`, visibility, humidity, release time, daily min/max and daily `lv`. Resources explicitly label windspeed **km/h** and visibility **km**. Date/hour formatting uses the vehicle's default time zone; `city.timezone` is declared but has no consumer in these native rendering methods. The user's matching Amman configuration avoids that specific mismatch. [11], [14]

The forecast layouts also establish limits that earlier adapter inventories could not prove: `HoursAdapter` initially skips item 0 and selects items 1–7 when at least eight hours exist, inserts solar-event markers inside that time range, and then displays at most seven items. `DaysAdapter` skips daily indices 0 and 1 and displays indices 2–8 when nine dates exist. The adapter's past-day/today positioning is compatible with those consumers, but emitting 48 hours and 16 days does not make all those entries visible on this layout. [8]

| Adapter comparison at the audited commit | Native finding and implication |
| --- | --- |
| Current `isdaynight`, hourly `Isdaynight`, hourly `precipitation` | Unknown fields in these DTOs; no change to this native renderer. Keep source truth distinct from verified UI capability. [4], [5], [10] |
| Hourly Boolean `isdaynight` | Correct native field/type, but ignored by icon selection. A payload-only per-hour icon correction is unavailable through this field. [4], [8] |
| Copying the same ID into all three weather-ID fields | Widget/hourly/day icon paths read `cnweatherid`. This proves that consumed path, not equivalence of all three ID systems. [8], [11] |
| Midnight daily `publictime` | Conflicts with native pre-dawn search. Sunrise anchoring is an inspected-consumer compatibility option, not evidence of a captured native cloud timestamp. [7], [8] |
| Numeric period `precProb` and `aqi.lv` | Native declarations are String; bundled Gson coerces numeric tokens. Matching the declared types is cleaner, but their present types do not explain the icon failure. [4], [5], [10] |
| European daily AQI category number | Native `transformAirLevel` interprets 1–6 as Excellent, Good, Light pollution, Moderate pollution, Severe pollution, Serious pollution. Those pollution labels differ from European category names. The APK contains no inspected numeric AQI threshold calculation proving a standard or conversion; category substitution can display a misleading native label. [11], [14] |
| Beaufort wind levels 0–12 | Native resource vocabulary confirms Beaufort labels 0–12, but `transformWindLevel` handles only 0–10 and returns invalid-data text for 11/12 despite their resources. Integer source thresholds still come from the documented meteorological conversion, not native arithmetic. [11], [14] |
| Empty `aqidays`, `alarm`, `liveInfos`, missing radar | DTO item structures are now established above. Units/grade calculations/source validity remain independent questions. Alert filtering accepts only decimal `type` strings 1–14 and uses numeric `level`; an actual warning source is still needed. Do not fabricate warnings or indices from ordinary conditions. [5], [7] |
| Missing extra current/city/day fields | The inventory establishes accepted names/types. Most extras have no read site outside getters/setters in the inspected widget/dialog code; adding them does not prove a new visible feature. Existing optional weather-derived fields can remain useful for other firmware consumers. [4], [5], [11] |

The native model alone does not establish pollutant concentration units, AQI numeric scale/aggregation, period rain/snow/ice units, phase vocabulary, radar interval, native administrative-code validity, or expected alert/lifestyle sources. No populated original-cloud response was captured. These gaps remain even though the corresponding DTOs are now available. This audit does not broaden the app into instrument/CAN control; native service observations are recorded to explain schema boundaries. [5], [9]

## Native attributes and available upstream data

The compact matrix emphasizes this firmware's visible fields and whether more data could actually fill a visible gap. **Supplied** means a provider measurement/forecast exists; **derived** means the adapter defines a conversion or aggregation; **unavailable** means the relevant upstream interface does not supply the native object or meaning. Existing conversions and upstream units are documented in [the pinned Open-Meteo research](open-meteo-widget-features.md) and [the implementation record](widget-mapping-implementation.md), with official server/API citations. Native consumption in this column comes from the extracted methods above. [4], [5], [8], [11], [16]

| Native attribute / visible purpose | Upstream input and status | Current coverage / remaining gap |
| --- | --- | --- |
| Current `temperature`, daily `mintemp`/`maxtemp` | **Supplied:** `temperature_2m`, daily min/max. | Already mapped; verified visible. No missing forecast variable. |
| Current/hourly/day `cnweatherid` | **Derived:** `weather_code` mapped to the verified condition code path. | Already mapped. Native global solar logic, rather than missing weather data, causes the inspected night icon error. |
| Hourly `isdaynight` | **Supplied:** `is_day`; 0/1 becomes Boolean. | Already supplied correctly. Parsed but ignored by this renderer; no extra source field enables per-hour moons. |
| Current `uVIndex` and visible UV description | **Supplied:** `uv_index`; description **derived by native** `transformUVLevel`. | Number already mapped. Native description switch only handles 1–10; it returns empty text outside those cases. Adding `uvIndexDesc` does not change this view's chosen path. |
| Current `windspeed`, `windlevel`, direction | **Supplied:** `wind_speed_10m`, `wind_direction_10m`; Beaufort/eight-point compass **derived**. | Already mapped and visible. Native 11/12 text omission is a renderer limit, not absent gust or sustained-wind data. |
| Current `visibility`, `humidity` | **Supplied:** visibility metres and relative humidity %; km **derived**. | Already mapped and visible in confirmed native units. |
| City `name` / `englishCityName` | **Unavailable from Open-Meteo:** names come from reverse geocoding. | BigDataCloud district/city selection already populates the verified display targets; native administrative objects are additional metadata. |
| Daily `lv` and visible air category | **Supplied:** hourly `european_aqi`; per-date peak/category **derived**. | Numeric summary exists. Native pollution labels differ from the selected European scale; a truthful native-scale/category policy requires explicit standards evidence. More pollutants alone do not define that conversion. |
| Hourly `date`/`temp`, visible forecasts | **Supplied:** hourly times and temperature. | Already mapped. Native layout limits and solar-event insertion determine the seven visible entries. |
| Hourly rain chance/wind; current feels-like/pressure/cloud/precipitation/gusts | **Supplied:** corresponding forecast variables; units/force **derived**. | Already mapped where the DTO accepts them. No display read site in this inspected hourly/current view creates an additional visible feature. Hourly amount has no DTO field here. |
| Daily `conditionDay` / `conditionNight` and additional weather metrics | **Derived:** representative full-horizon hourly conditions/wind/humidity/cloud/chance; supplied daily feels-like extrema, pressure and visibility. | Already enriched; `DaysAdapter` uses only `conditionDay.cnweatherid`, date and ordinary min/max. Other declared fields do not become visible merely by being filled. |
| Daily solar events | **Supplied:** `sunrise`, `sunset`. | Already mapped; native inserts visible markers and uses the events for global night. Numeric `publictime` anchoring is a compatibility derivation, not a missing solar variable. |
| Lunar events / `moonphase` | **Supplied:** `moonrise`, `moonset`, `moon_phase`; timestamps **derived**. | Real events already mapped when available; `moonSetFmt` also carries native service date selection. Native phase vocabulary and a visible lunar display are unverified. |
| `aqidays`, pollutant gases | **Supplied:** hourly AQI/PM and `ozone`, `nitrogen_dioxide`, `sulphur_dioxide`; daily summaries **derived**. | DTO names/types are now known. Native units/scale/aggregation remain unresolved, and these views do not display the service-only arrays/gases. |
| Native `alarm` warnings | **Unavailable in the examined forecast/AQ interfaces:** requires an actual warning source and grade mapping. | Native alert DTO/filter are established, but ordinary WMO severity is not an official alert. Keep unavailable alerts empty. |
| `liveInfos`, radar, festival/restriction metadata | **Unavailable as native objects:** weather numbers do not supply these schemas' source semantics. | Types are known; lifestyle grading, radar integer interval/units and civic metadata remain source/contract gaps. No justified visible feature follows from invented placeholders. |

This firmware's principal missing visible behavior is therefore in its own icon-selection algorithm. More optional meteorological fields cannot make that private algorithm use the hourly day/night flag. [7], [8], [16]

## Primary sources and repeatability

All native references below derive from the APK hash recorded above. Re-extracting that APK and examining the named class/method is the durable way to reproduce the finding; local links refer to this workspace's inspection artifacts.

- [1] User-supplied original firmware share.
- [2] OTA `META-INF/com/android/metadata` and extraction inventory `weather-artifacts.json`.
- [3] Original `WeatherData.apk`, its manifest, and `classes2.dex` disassembly. Model declaration sections include `WeatherBean` at class 225 and `WeatherInfoBean.DataDTO.HourlysDTO.HourlyweathersDTO` at class 306; field types were read from DEX, not inferred from getter naming.
- [4] `com.byd.weatherlibrary.WeatherInfoBean`: all nested DTO fields; `HourlysDTO.HourlyweathersDTO.getIsdaynight()`.
- [5] `com.byd.weatherdata.WeatherBean`: all nested DTO fields, including `AqiDTO`, `AqidaysDTO`, `AlarmsDTO`, `LiveInfosDTO`, `RadarDTO`.
- [6] `WeatherContentProvider.query/insert/update`, `DbOpenHelper` table declarations.
- [7] `NumberUtils.dealWeatherJsonData`, `uncompressToString`, `isDuringNight`, `isRightAlarmType`, `mapTypeList`.
- [8] `HoursAdapter` constructor, `getValidResourceId`; `DaysAdapter` constructor, `onBindViewHolder`; `WeatherUtils.weatherConditionMap`; `WeatherWidgetProvider.updateAppWidget`.
- [9] `RequestService.getWeatherInfo`, `processWeatherData`, request-success provider storage, `sendWidgetBroadcast`, `TimeFormatChangeObserver`.
- [10] Bundled `Gson` constructor/default field naming; `ReflectiveTypeAdapterFactory.getFieldNames` and `Adapter.read`; `TypeAdapters.STRING`/`BOOLEAN`; `JsonReader.nextString`.
- [11] `DialogActivity.handleValidData`; `DateUtils.getDate/getWeekday/getHourAndMinute`; `WeatherUtils.transformAirLevel/transformWindLevel`.
- [12] Adapter `WeatherMapping.java` at the audited repository commit.
- [13] Earlier source-hour and mapper replay, with the user's time-zone clarification.
- [14] `resources.arsc` values extracted using Android SDK `aapt dump --values resources`, limited to `air_1`–`air_6`, `wv_0`–`wv_12`, `speed_unit`, `km_unit`.
- [15] Local isolated native replay: `build-native-oracle.py`, `native-oracle/provenance.json`, `mapper-night-replay-before.json`, `mapper-night-replay-after.json`, `native-night-payload-diff.json`, `native-night-flag-probe.json`, `native-night-sunrise-probe.json`, and the corresponding replay logs. Original APK methods are preserved verbatim; dependency stripping and Gson 2.10.1 are disclosed above.
- [16] Earlier pinned official Open-Meteo server/API research, including `VariableHourly.swift`, `VariableDaily.swift`, `AirQuality.swift`, forecast/air-quality OpenAPI schemas and retained successful production probes; the current implementation record defines actual aggregation/rounding policies.

[1]: https://www.icloud.com/iclouddrive/027b0msi-3kKdnhlYr1OFT4PA#Di3.0%5F1for2%5F18.1.2.2601230.1%5F0
[2]: /workspace/scratch/byd-firmware/weather-artifacts.json
[3]: /workspace/scratch/byd-firmware/weatherdata-dexdump2.txt
[4]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherlibrary/WeatherInfoBean.java
[5]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/WeatherBean.java
[6]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/utils/WeatherContentProvider.java
[7]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/utils/NumberUtils.java
[8]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/adapter/HoursAdapter.java
[9]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/service/RequestService.java
[10]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/google/gson/internal/bind/ReflectiveTypeAdapterFactory.java
[11]: /workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/DialogActivity.java
[12]: https://github.com/ibramaswadeh/Byd-Weather/blob/31a41f80fe56a0a86ddc577503940dc64a2059b8/app/src/main/java/com/ibramaswadeh/bydweather/WeatherMapping.java
[13]: hourly-night-icons.md
[14]: /workspace/scratch/byd-firmware/ui-weather-unit-values.txt
[15]: /workspace/scratch/byd-firmware/native-oracle/provenance.json
[16]: open-meteo-widget-features.md
