# BYD widget attributes and Open-Meteo opportunities

The [original-firmware audit](firmware-weather-contract.md) now adds a verified native field inventory for the user's DiLink 3.0 WeatherData 2.9.5.250616, including the separate widget and service DTOs, accepted types, actually rendered fields and native limitations. The public-adapter inventory below remains historical evidence of compatibility conventions.

The [implementation note](widget-mapping-implementation.md) records which researched fields are now converted in the updated 1.1.1 build.

Research date: 2026-10-01. Application baseline: `478a255ce9b12b5169d9fd65a122b75b2c76d5f3` (the existing BigDataCloud 1.1.1 build). This note inventories every attribute found in the two public BYD weather adapters examined and separates that evidence from a verified native parser contract.

## What is actually known about the native widget

The strongest public first-hand evidence is Denza Lab's in-car investigation on **DiLink 5.1, `com.byd.weatherdata` 2.9.36.260424**. It reports successful provider writes, read-back, stock service refresh, and `WeatherWidgetProvider.onUpdate` for two widgets. The provider URI is `content://com.byd.weatherdata.utils.WeatherContentProvider/weather`; the `name` column holds the JSON envelope below and the stock app normally replaces row `_id=0`. The launcher consumes `com.byd.weatherdata.action.THIRD_REFRESH`; stock `RequestService` observes the `time_12_24` settings URI and updates its own widgets. These are observed firmware behaviors, not a published BYD API. [1], [2]

Denza's mapper also records a specific stock behavior: **today's minimum/maximum temperatures are selected using the date in `dailyweathers[].moonSetFmt`**. This is unusually important because our baseline emits `publictimeFmt` but omits `moonSetFmt`. The field's compatibility purpose must be distinguished from a real astronomical moonset value. Its source comment is first-hand adapter evidence, not a published native parser or a guarantee for every firmware. [3], [4]

No public stock WeatherData APK, native Java model/parser, complete original cloud response, or original provider JSON capture was found in the examined repositories or exact-name GitHub searches. The searches included `com.byd.weatherdata`, `WeatherData.apk`, `WeatherData_WeatherWidgetProvider`, `moonSetFmt`, `windGustPow`, and the weather service host, plus publicly indexed DiLink firmware repositories. The firmware simulator repository describes local extracted firmware; it does not publish a weather parser or stock APK. Consequently, this is a **complete inventory of the public adapter attributes found**, not a claim that it is the complete native schema on the user's car. [1], [3], [5], [6]

This distinction affects three things:

- A payload that writes and refreshes successfully does not prove that every attribute is parsed or rendered by every stock layout. The Denza findings explicitly validate provider/refresh and temperature; they do not enumerate every UI field. [1]
- BYD Extend emits hourly `isdaynight`; Denza emits hourly `Isdaynight`. Both are booleans with `true` for day, but JSON casing is significant. Our added current `condition.isdaynight` has no native-parser confirmation in these sources. No source proves that it changes a current icon. [3], [5], [7]
- Denza's localized city names were build-verified after its live provider validation and were still awaiting a live-car check. Its city object is useful schema evidence, but does not prove which name or administrative field each launcher displays. [1], [3]

## Reading the inventory

**B** means emitted by our application baseline; its mapper derives from BYD Extend. **D** means emitted by the Denza mapper. Every type below is the JSON type that the adapter writes. Units are established by adapter arithmetic/source variables where stated; they are not a universal native SDK specification. An omitted B field is a potential adapter gap, not proof that the stock widget requires it. Sources for the inventory are [3], [4], and [5].

### Envelope and data container

| Attribute | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `resultcode` | String `"0"` for success. | B, D |
| `resultinfo` | String; B `"success"`, D source description. | B, D |
| `servertime` | Number, epoch milliseconds of refresh. | B, D |
| `data` | Object containing the groups below. | B, D |
| `data.ts` | Number, epoch milliseconds of refresh. | D |
| `data.citycode` | String `"GPS"`, an adapter sentinel rather than an official BYD city ID. | D |
| `data.updatetime` | Number, epoch milliseconds of refresh. | D |
| `data.weatherDesc` | String source/description text. | B, D |
| `data.mobilelink` | String URL or empty string. | B, D |
| `data.city`, `condition`, `hourlys`, `dailys`, `aqi` | Objects described below. | B, D |
| `data.radar` | Object described below. | D |
| `data.alarm`, `liveInfos`, `aqidays` | Arrays; empty in both examined adapters, so the item schemas are **unknown**. | B, D |

### City/location

| Attribute under `data.city` | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `name` | String, displayed/localized location candidate. Our intended combined label is `district, city`. | B, D |
| `englishCityName` | String, English location candidate. | B, D |
| `timezone` | String IANA time zone. B uses forecast zone; D uses the passed zone. | B, D |
| `countryname`, `englishCountryName`, `countryCode` | Strings; B writes empty values; D uses geocoder metadata if available. | B, D |
| `citycode` | String `"GPS"`; official city-code semantics unknown. | D |
| `provincename` | String, region or city fallback. | D |
| `parentcity` | String, city. | D |
| `co`, `ca` | Strings containing longitude and latitude, respectively. The unusual key names are literal. | D |
| `level` | Number `3`; native administrative-level semantics unknown. | D |
| `supplementalAdminAreas` | Array of an adapter-supplied administrative object. | D |
| `supplementalAdminAreas[].id` | String `"GPS"`; official identifier semantics unknown. | D |
| `supplementalAdminAreas[].localizedName`, `.englishName` | Strings, region name. The capital `N` in `localizedName` is literal. | D |
| `supplementalAdminAreas[].level` | Number `2`; native meaning unknown. | D |
| `administrativearea` | Object for region metadata. Lowercase key is literal. | D |
| `administrativearea.id` | String `"GPS"`. | D |
| `administrativearea.localizedname`, `.englishName` | Strings, region name. Lowercase `localizedname` differs from the supplemental object. | D |
| `administrativearea.level` | Number `1`; native meaning unknown. | D |

Geocoding and forecast coordinates must use the same fresh fix. Open-Meteo's forecast response provides a time zone and grid coordinates, but **does not supply a district or city name**; those belong to reverse geocoding. The `district, city` failure cannot be repaired by adding forecast variables. A district is also not automatically the same entity as BigDataCloud's top-level `locality`; the separate geocoding investigation must define the administrative selection. [4], [8]

### Current condition

| Attribute under `data.condition` | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `temperature` | Number, rounded °C. | B, D |
| `realfeel` | Number, rounded °C; B maps Open-Meteo apparent temperature; D copies ordinary temperature. | B, D |
| `realfeelDesc` | String, empty in D; no verified native description vocabulary. | D |
| `feelTemperatureShade` | Number, °C; D copies ordinary temperature. This is not evidence for a shade-temperature algorithm. | D |
| `cnweatherid`, `weatherid`, `zmweatherid` | Numbers; both adapters put the same mapped ID into all three. Distinct native codebooks remain unverified. | B, D |
| `weathertext` | String condition text. | B, D |
| `isdaynight` | Boolean, true for daylight; current field is our adapter extension with native consumption unverified. | B |
| `uVIndex` | Number, rounded UV index; capitalization is literal. D writes zero because its provider lacks UV. | B, D |
| `uvIndexDesc` | String, empty in D. | D |
| `humidity` | Number, rounded relative humidity %. | B, D |
| `pressure` | Number, rounded sea-level pressure hPa. | B, D |
| `pressureTendency` | String `"S"` in D, not computed. Native trend vocabulary unknown. | D |
| `visibility` | Number, kilometres; B rounds metres first then integer-divides by 1000; D rounds kilometres. Precision policy and native numeric precision unknown. | B, D |
| `windspeed`, `windgustspeed` | Numbers, rounded km/h. D copies sustained wind into its gust field; B has actual gust source. | B, D |
| `windlevel` | Number; B uses its ad-hoc km/h buckets; D uses Beaufort-like m/s thresholds. Native scale unverified. | B, D |
| `windgustlevel` | Number; D copies the sustained wind level. | D |
| `winddegrees` | Number, rounded meteorological wind-from bearing in degrees. | B, D |
| `winddir`, `winddirtext` | Strings, eight-point compass direction. | B, D |
| `windgustdir` | String compass direction; D copies sustained direction. No separate gust-direction source. | D |
| `cloudCover` | Number, rounded cloud cover %. | B, D |
| `precipitation` | Number, precipitation mm for the provider's measurement interval. B current interval and D next-forecast-period semantics differ. | B, D |
| `updatetime` | Number, epoch milliseconds. B uses current forecast timestamp; D deliberately uses actual refresh time. | B, D |
| `updatetimeFmt` | String timestamp; B `yyyy-MM-dd HH:mm` in device zone; D ISO date-time with offset. | B, D |
| `expiretime` | Number, epoch milliseconds; D refresh + 1 h. | D |
| `desc` | String source text. | D |
| `weatherMapLink`, `comfortlink`, `mobilelink` | Strings, empty in D. | D |
| `compareFlag`, `vipLocation` | Strings, empty in D; semantics unknown. | D |

The two adapters' wind-level conversions disagree on routine conditions: 25 km/h produces B level 5, but D level 4. This supports investigating a documented Beaufort conversion, not simply copying an unverified native scale. For current `updatetime`, Denza explicitly fixed the native “data released” label by using actual adapter refresh time rather than an hourly forecast timestamp. Keep forecast-observation age distinct from successful-refresh age when implementing compatibility behavior. [1], [3], [4], [9]

### Hourly forecast

| Attribute | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `data.hourlys.expiretime` | Number, epoch milliseconds; B +6 h, D +1 h. | B, D |
| `data.hourlys.hourlyweathers` | Array; B emits 8 points, D up to 48. Those counts are adapter choices. | B, D |
| `hourlyweathers[].date` | Number, epoch milliseconds of the point. | B, D |
| `.temp` | Number, rounded °C. | B, D |
| `.cnweatherid`, `.weatherid`, `.zmweatherid` | Numbers, same mapped condition ID in each field. | B, D |
| `.rainprobability` | Number, precipitation probability %. Despite the name, B's source covers all precipitation, not rain alone. | B, D |
| `.precipitation` | Number, amount mm for the associated provider interval. | D |
| `.wp` | Number, sustained wind level under the adapter's conversion. | B, D |
| `.wd` | String compass direction. | B, D |
| `.isdaynight` | Boolean, true for day. Native spelling unverified. | B |
| `.Isdaynight` | Boolean, true for symbols ending `_day`. Native spelling unverified. | D |
| `.mobilelink` | String, empty in D. | D |

### Daily forecast container and daily item

| Attribute | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `data.dailys.publictime` | Number, epoch milliseconds; B today's date midnight, D refresh time. | B, D |
| `data.dailys.publictimeFmt` | String date/timestamp; B date only, D ISO with offset. | B, D |
| `data.dailys.expiretime` | Number, epoch milliseconds; B +24 h, D +1 h. | B, D |
| `data.dailys.mobilelink` | String, empty in D. | D |
| `data.dailys.dailyweathers` | Array; B requires/emits 16 dates including yesterday, D up to 9 available dates. Native fixed count unknown. | B, D |
| `dailyweathers[].publictime` | Number, epoch milliseconds at that date's midnight. | B, D |
| `.publictimeFmt` | String date/timestamp; B date only, D ISO with offset. | B, D |
| `.mintemp`, `.maxtemp` | Numbers, rounded °C. | B, D |
| `.realFeelTempMin`, `.realFeelTempMax` | Strings with rounded temperatures; D uses ordinary min/max, not a feels-like source. | D |
| `.conditionDay`, `.conditionNight` | Objects described below. B uses the same whole-day condition object for both; D selects forecast points near noon and midnight. | B, D |
| `.aqivalue` | Number AQI; B today's current European AQI only and `-1` on other dates, D zero placeholder. | B, D |
| `.aqivaluetext` | String AQI display value or `"--"`. | B, D |
| `.lv` | Number quality category; B uses European category thresholds; D zero placeholder. Native scale unknown. | B, D |
| `.pm25` | Number; D zero placeholder. Native daily aggregation and units unknown. | D |
| `.uvIndex` | Number, daily maximum UV; D zero placeholder. | B, D |
| `.uvIndexText` | String; D `"--"` placeholder. | D |
| `.sunRise`, `.sunSet` | Numbers, epoch milliseconds. | B, D |
| `.sunRiseFmt`, `.sunSetFmt` | Strings formatted from the matching solar instants; B device-zone text, D ISO with offset. | B, D |
| `.moonRise`, `.moonSet` | Numbers; D midnight compatibility placeholders, not astronomical events. | D |
| `.moonRiseFmt`, `.moonSetFmt` | Strings; D ISO date-time at date midnight. `.moonSetFmt` carries a stock min/max-selection compatibility date. | D |
| `.moonphase` | String, empty in D. | D |
| `.visibility` | Number, kilometres; D fixed 10 placeholder. | D |
| `.pressure` | Number, hPa; D average over that date's available forecast points. | D |
| `.source` | String source attribution. | D |
| `.currentFestival`, `.currentRestrict` | Strings, empty in D; no verified native vocabularies. | D |
| `.spanDays`, `.spanDaysFull`, `.spanDaysNew` | Numbers, zero in D; meanings unknown. | D |
| `.currentlink`, `.mobilelink` | Strings, empty in D. | D |

### Each `conditionDay` / `conditionNight`

| Attribute | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `cnweatherid`, `weatherid`, `zmweatherid` | Numbers, mapped condition ID. | B, D |
| `weathertext` | String condition description. | B, D |
| `windspeed` | Number, rounded km/h; B daily maximum, D selected-point speed. | B, D |
| `windlevel` | Number from that wind speed using adapter-specific scale. | B, D |
| `winddir` | String; B daily dominant direction, D selected-point direction. | B, D |
| `windGustDir` | String; D copies sustained wind direction. | D |
| `windGustPow` | String wind-level number; D copies sustained level. | D |
| `humidity` | Number, relative humidity % at selected point. | D |
| `cloudCover` | String number, cloud cover % at selected point. | D |
| `precProb` | Number, precipitation probability % at selected point. | D |
| `rainProb` | String number; D copies total precipitation probability. It is not a verified separate rain-only probability. | D |
| `rain`, `totalLiquid` | String precipitation amount, mm for selected forecast period; D writes the same value to both. Full-day/half-day native amount semantics unknown. | D |
| `snow`, `ice` | String `"0"` placeholders; native amount units unknown. | D |
| `thunProb`, `snowProb`, `iceProb` | String `"0"` placeholders; no actual probability source in D. | D |

Open-Meteo daily `weather_code` describes the **most severe condition of the whole day**. It is not a day-specific or night-specific code. To populate separate halves honestly, request hourly coverage for every emitted date, group in the response time zone, partition using `is_day`, and define the representative-condition and aggregation rules. Eight future hours cannot establish 16 days of separate day/night conditions. Picking a representative hour, such as D does, and selecting the most severe condition of a half-day are different policies; name the chosen policy in the research/tests. [3], [4], [8]

### AQI, radar, and unpopulated arrays

| Attribute | JSON type and adapter meaning | Present |
| --- | --- | --- |
| `data.aqi.aqivalue` | Number; B current European AQI, D zero placeholder. | B, D |
| `.aqivaluetext` | String AQI display or `"--"`. | B, D |
| `.aqidesc` | String description; B European category labels, D empty. | B, D |
| `.lv` | Number category; B European thresholds, D zero. | B, D |
| `.pm25`, `.pm10` | Numbers; B rounded current μg/m³, D zero placeholders. | B, D |
| `.pm25desc` | String, empty in D; native descriptive vocabulary unknown. | D |
| `.o3`, `.co`, `.no2`, `.so2` | Numbers, zero placeholders in D. **Native pollutant units unknown**, especially CO. | D |
| `.updatetime` | Number, epoch milliseconds of adapter refresh. | B, D |
| `.mobilelink` | String, empty in D. | D |
| `data.radar.dataTime` | Number, epoch milliseconds of forecast point. | D |
| `data.radar.skycon` | String provider symbol; native codebook unknown. | D |
| `data.radar.dataseries` | Empty array; item format and native time interval unknown. | D |
| `data.aqidays[]` | Item format, date field, aggregation, and AQI scale unknown. Neither adapter supplies a populated example. | Empty B, D |
| `data.alarm[]` | Alert item format, severity vocabulary, effective interval, and native source requirements unknown. | Empty B, D |
| `data.liveInfos[]` | Lifestyle item format and grading rules unknown. | Empty B, D |

The air-quality source documents PM2.5/PM10 and all four gas concentrations in **μg/m³**. Convert CO by dividing by 1000 only if the native field is established as mg/m³; its key alone is not sufficient evidence. European AQI and U.S. AQI are different scales. Neither is automatically China's AQI, and relabeling or stretching European numbers cannot recover a different system's rolling pollutant averages. Keep the selected system explicit in source/description metadata until the firmware expectation is established. [10]

## Open-Meteo values that can fill the known purposes

The table describes source availability and sensible compatibility conversions; final native rendering still has the evidence limits above. Requesting an optional value should not make the already usable temperature/condition update fail when that extra is unavailable. Official forecast documentation provides variable units, local-time behavior, interval semantics, and daily aggregation definitions. Official air-quality documentation provides pollutant units and index systems. [8], [10]

| Native purpose / target | Open-Meteo source | Conversion and limitation | Baseline |
| --- | --- | --- | --- |
| Current temperature / feels-like | `temperature_2m`, `apparent_temperature` | Round °C to numeric fields. Apparent temperature is a feels-like equivalent, not a proprietary RealFeel algorithm. Do not claim a shade-only calculation for `feelTemperatureShade`. | Used in `temperature`, `realfeel`. |
| Daily feels-like range | Daily `apparent_temperature_min`, `apparent_temperature_max` | Rounded °C encoded as strings for observed `realFeelTempMin/Max`. | Available, unused. |
| UV number and description | Current/hourly `uv_index`; daily `uv_index_max` | Keep numeric index; add descriptive risk bands only using an explicitly documented scale, not a guessed native enum. `uvIndexText` could display the actual maximum instead of a placeholder. | Number used; descriptions missing. |
| Wind gust strength | `wind_gusts_10m`; daily `wind_gusts_10m_max` | Keep km/h and derive a documented wind level consistently. Direction available is sustained wind-from, not separate gust bearing. | Current speed used; gust-level/day gust fields missing. |
| Wind level | `wind_speed_10m` | Convert km/h to m/s (`/3.6`) before a documented Beaufort threshold table if native purpose is confirmed as Beaufort. Do not use equal 5 km/h steps. | Ad-hoc buckets used. |
| Hourly amount | Hourly `precipitation` | Numeric mm for the **preceding hour**. Associate with the matching hourly timestamp; do not confuse with current 15-minute interval amount. | Unused. |
| Daily rain amount | Hourly `rain` and `showers`, or daily `rain_sum` + `showers_sum` | Sum the liquid rain components over the chosen day/night partition; observed field is a numeric string. Whole-day totals must not be duplicated into both halves. | Unused. |
| Daily total liquid equivalent | Hourly `precipitation`, or daily `precipitation_sum` | Sum mm over the stated interval/partition; includes liquid-equivalent snowfall. Native interval must be documented by implementation. | Unused. |
| Snow amount | Hourly `snowfall`, daily `snowfall_sum` | Source is **cm**. Preserve cm if native snow-depth purpose/units are verified; convert to mm with `×10` only for a native snow-depth-mm field. A snow-water-equivalent field instead needs the provider's density convention. | Unused; target unit not verified. |
| Day/night chance | Hourly `precipitation_probability` or daily `precipitation_probability_max` | Percent. A max over a partition is a representative hourly chance, not the statistically combined probability of any precipitation during that entire half. Label the approximation. It is not separate thunder/snow/ice probability. | Hourly used; daily absent. |
| Day/night humidity/cloud/pressure/visibility | Hourly `relative_humidity_2m`, `cloud_cover`, `pressure_msl`, `visibility` | Group in response time zone; choose explicit average/min/max/representative policy by purpose. Visibility metres → km. Daily visibility might sensibly use minimum viewing distance; that is a new aggregation decision, not a documented native requirement. | Only current fields used. |
| Different daily day/night icons | Hourly `weather_code` and `is_day` across the full emitted range | Define representative condition for each partition and use the adapter's WMO→native ID mapping. When no hours exist for a partition, use a documented fallback to the whole-day condition. | Both halves currently identical. |
| Current and hourly solar state | `is_day`; daily `sunrise`, `sunset` | `1` → day `true`, `0` → night `false`; validate exact values. Solar instants must use source time zone. Native field spelling/consumption still unverified. | Current/hourly flag and solar times used. |
| Daily date compatibility | Daily `time` + response `timezone` | Midnight of the forecast's local date, not device midnight. Set the observed `moonSetFmt` compatibility date without pretending it is an actual lunar event. | `moonSetFmt` absent. |
| Country/region/coordinates | Response time zone/grid coordinates; fresh GPS and geocoder metadata | Use actual requested GPS for location metadata, and trusted reverse-geocoder country/region. Grid coordinates can differ from GPS. `co`/`ca` observed type is string. | Country blank; coordinates/region absent. |
| Pollutant detail | AQ `ozone`, `carbon_monoxide`, `nitrogen_dioxide`, `sulphur_dioxide`, `pm2_5`, `pm10` | Source μg/m³. Gas target units must first be established. Do not guess `co` units. | Particles used; gases unrequested. |
| AQI forecasts / daily AQI | AQ hourly `european_aqi` / `us_aqi` and pollutant series | Requires populated native `aqidays` contract and explicit AQI system/aggregation. Full weather horizon can exceed AQ horizon; missing AQ forecasts stay unavailable. | Current European AQI only. |
| Moonrise/moonset/phase | Daily `moonrise`, `moonset`, `moon_phase` in the current official server | Real rise/set instants can fill observed numeric `moonRise`/`moonSet` and matching formatted strings after response-zone parsing. `moon_phase` is a synodic-cycle fraction, not illumination %. Native `.moonphase` string vocabulary is unknown, so a native phase conversion cannot yet be claimed. Preserve the `moonSetFmt` date-compatibility purpose on dates with no actual set event. | Available, unused; public API lunar response verified in this investigation. |
| Official warnings | No equivalent warning objects in the examined forecast/AQ interfaces | High wind or severe WMO code is a forecast condition, not an official warning. Keep `alarm` empty until a real alert source and contract are established. | Empty. |
| Lifestyle indices | No BYD `liveInfos` objects in forecast/AQ | Derived advice could use temperature/UV/rain/air quality, but native item schema and grading rules must be established before writing it. | Empty. |
| Radar/minute precipitation curve | Gridded `minutely_15` precipitation is available in documented regions and model combinations | Forecast 15-minute amounts are not radar observations, and no native `dataseries` structure is known. Do not relabel an arbitrary array as radar. | No radar object. |
| Other Open-Meteo features | Dew point, soil temperature/moisture, radiation, ET₀, snow depth, CAPE, freezing level, pressure-level weather, etc. | No corresponding native widget attributes were found. Adding unused JSON keys alone will not make these visible. | Not requested. |

For a robust implementation, pin metric request units (`temperature_unit=celsius`, `wind_speed_unit=kmh`, `precipitation_unit=mm`) or validate response `*_units`; parse offset-free ISO timestamps in response `timezone`, and retain actual timestamps with units/intervals. `timezone=auto` makes Open-Meteo timestamps local to the coordinates, while the baseline parses them in `ZoneId.systemDefault()`. This mismatch affects updates, dates, solar times, and hourly/day grouping. [4], [8]

The older pinned website catalog does not exhaust the current server's daily variables. The official controller and daily enum at `b06f4760fd1f997e5559bb380f64c5e496b4a509` implement `moonrise`, `moonset`, and `moon_phase`. The lunar calculation documents phase as the fraction `[0, 1)` of the synodic cycle: 0 new moon, 0.25 first quarter, 0.5 full moon, 0.75 last quarter, evaluated at local noon. This is distinct from the illuminated fraction of the moon. [11], [12], [13]

During this investigation, an HTTPS request to the public forecast endpoint for the reference coordinates 31.9566, 35.9457 with `timezone=auto` returned HTTP 200 and daily `moonrise`/`moonset` ISO timestamps plus `moon_phase` values in units `fraction`. The two returned dates included 2026-10-01, with moonrise `21:31`, moonset `11:34`, and phase `0.670` in `Asia/Amman`; the response also contained daily apparent-temperature minimum/maximum, gust maximum, and mean sea-level pressure. This confirms deployment for that request, not every coordinate/date. The local probe is `/workspace/scratch/byd-build/openmeteo-lunar-probe.json`. The companion [Open-Meteo feature research](open-meteo-widget-features.md) records the broader current catalog and source references. Real lunar events remain distinct from Denza's midnight placeholders and its `moonSetFmt` min/max-selection compatibility behavior. [3], [11], [12], [13]

## Evidence-driven implementation order

1. Fix reverse-geocoder district selection and retain the combined text in both `city.name` and `englishCityName`, with explicit missing-value/duplicate fallbacks. Test the payload output, and diagnose whether the installed native layout reads a different field if the provider already contains the expected text. [4]
2. Fix response-zone parsing/formatting and add the known daily compatibility-date field. These correctness changes improve already supported forecast meanings without inventing new data sources. [3], [4], [8]
3. Add directly supplied equivalents whose known target types are clear: hourly precipitation, daily apparent-temperature range, and current gust-level with a documented conversion. Preserve valid core updates when optional fields are absent. [3], [8]
4. Add full-horizon hourly coverage before separate daily day/night summaries. Define amount, probability, representative-code, and wind aggregation precisely and test different zones, missing partitions, and polar daylight. [3], [8]
5. Obtain the user's stock WeatherData APK/provider sample to confirm case-sensitive flags, weather ID systems, AQI index/units, and array item schemas. Do not advertise unseen fields as visible widget features merely because another adapter writes them. [1], [3], [5]

A native verification capture should distinguish: the installed package version; launcher versus stock app widget; provider JSON immediately after refresh; actual visible label/current icon/min-max/hourly details; and known day/night/location conditions. The existing provider write/refresh mechanism can remain intact while research establishes these consumption boundaries. [1], [2]

## Sources

All links below are primary sources for their owners' code, documentation, or first-hand vehicle observations. Community adapter code is **not** official BYD documentation.

[1]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/docs/weather-adapter-findings.md
[2]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/NativeWeatherStore.kt
[3]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/NativeWeatherPayload.kt
[4]: https://github.com/ibramaswadeh/Byd-Weather/blob/478a255ce9b12b5169d9fd65a122b75b2c76d5f3/app/src/main/java/com/ibramaswadeh/bydweather/WeatherMapping.java
[5]: https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview/blob/37fbd69f2507433126aa1d4b615b066f807c4c8f/app/src/main/java/com/byd/extend/WeatherMapping.java
[6]: https://github.com/i99dash/dilink5-sim/blob/14cc57700b8b7437cbafa21700ea8e4c26052495/docs/GROUND_TRUTH.md
[7]: https://github.com/ibramaswadeh/Byd-Weather/blob/478a255ce9b12b5169d9fd65a122b75b2c76d5f3/docs/research/byd-weather-api.md
[8]: https://github.com/open-meteo/open-meteo-website/blob/a8dd5c8eef07583f983938c40b150776035f4b8e/src/routes/en/docs/%2Bpage.svelte
[9]: https://github.com/xor777/denza-lab/blob/eed3a411c630887a5ff9033e8f860860444738ff/apps/denza-apps/src/main/java/dev/denza/apps/feature/weather/WeatherCodeMapper.kt
[10]: https://github.com/open-meteo/open-meteo-website/blob/a8dd5c8eef07583f983938c40b150776035f4b8e/src/routes/en/docs/air-quality-api/%2Bpage.svelte
[11]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/ForecastapiController.swift#L619
[12]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/VariableDaily.swift#L60
[13]: https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Helper/Solar/Moon.swift#L193
