# Complete native weather field coverage

Research date: 2026-10-02. This audit records **every one of the 371 declared fields in 29 native DTO classes**, including the duplicated service/library declarations. The [machine-readable coverage](native-field-coverage.json) contains each field's native model/class/path/type, disposition, emission, exact input/conversion, units, native consumer and primary-source references. The table below merges identical JSON paths only for reading; the JSON retains all 371 declaration identities. The target is the supplied DiLink 3.0 WeatherData APK **2.9.5.250616/code60**, SHA-256 `f585062b2b393e3a87019da3b40c2daa089e60ddacdfab189c22c67f2279281a`, as documented in [the firmware contract](firmware-weather-contract.md). [1]

“Complete mapping” must include honest dispositions for fields whose source or native meaning is unavailable. It cannot mean manufacturing warnings, administrative IDs, radar series, particle/gas units, half-day totals or proprietary indices. Native field names/types establish what a parser can bind; they do not establish all field semantics. No network requests, firmware execution or application builds were performed for this audit.

## What each disposition means

| Disposition | Meaning at the recorded adapter snapshot |
| --- | --- |
| mapped | A direct provider value or populated container is emitted; minor numeric rounding is stated. |
| derived | An emitted value follows an explicit conversion, representative selection, format or aggregation. |
| compatibility | An emitted protocol, refresh/expiry, legacy alias or native-consumer workaround; no invented measurement. |
| unavailable | Required warning/lifestyle/civic/independent-variable source is absent from examined provider interfaces; omit item fields. |
| unverified | A source may exist, but native unit, scale, vocabulary, ID system, interval or purpose is unresolved; omit rather than guess. |
| unused | Optional metadata has a safe possible source/policy but was omitted or empty at the snapshot; absence is distinct from nonexistent upstream weather data. |

Statuses describe **emission at this source snapshot**, not a promise that each emitted field renders. The per-field units object explicitly records unresolved native units even for already emitted values. A “no read site” observation is bounded to the inspected native `com.byd` widget/dialog/service sources; it does not prove that another installed package or firmware cannot consume the field.

## Verified additions and native limits

1. The audited mapper supports official WMO **97, heavy thunderstorm**, and all **29** descriptions. The cached official Open-Meteo docs explicitly say ordinary instability models can emit 95 and 97. Actual descriptions and owned `sourceWeatherCode` preserve distinctions when native artwork merges several intensities. The original mapper omitted97. [2], [4]
2. Period `precProb` and current `aqi.lv` now use **String**, matching the native DTOs. Bundled Gson also accepts numeric tokens, so this cleanup does not explain or fix the native icon bug. `rain`, `totalLiquid`, `cloudCover` and `windGustPow` are also native period strings, although unknown native amount semantics keep `rain`/`totalLiquid` omitted. [1], [3], [4]
3. Explicit current `expiretime`, attribution/link, hourly link, daily item attribution/link and AQ link describe real adapter freshness/source policy. `servertime` remains actual refresh time and has a native ten-minute cache consumer; current `updatetime` remains forecast current-data time and solar-search input. Metadata is not an additional meteorological measurement. Proprietary weather-map/comfort URLs are omitted. [1], [4]
4. Actual geocoder `principalSubdivision` fills `administrativearea.localizedname` and `supplementalAdminAreas[].localizedName` without fabricated `id`/`level`. English-country/admin labels require a known-English source request; the runtime presently requests normalized device language. Coordinate keys `ca`/`co` and native city/prefecture ID systems remain unverified by native consumers. [1], [5]
5. An owned renderer can use each hour's actual `is_day` Boolean, native-style condition artwork, complete descriptions, UV/bounds and Beaufort0–12, and explicitly labelled **European AQI**. The stock renderer ignores hourly `isdaynight`, uses one global solar state, omits UV descriptions outside1–10, and omits wind labels11/12. Extra provider fields cannot change those private native switches. Physical stock-launcher hosting is constrained separately below. [1], [6], [9]

The native daily `lv` pollution labels are Excellent, Good, Light pollution, Moderate pollution, Severe pollution and Serious pollution. These differ from European AQI's Good/Fair/Moderate/Poor/Very poor/Extremely poor categories. Copying European category numbers into native `lv` does not convert to Chinese AQI; the inspected APK contains no numeric threshold/averaging computation proving a native AQI standard. The mapper emits native daily `lv=0` and current `aqi.lv="0"` to keep that native label unknown, and explicitly identifies European AQI in the owned extension. Native numeric `aqivalue` retains the European value for compatibility; it is not a verified native AQI-scale conversion. No multiplying, stretching or silent substitution of U.S. AQI is applied. [1], [4], [7]

Native period `rain`/`totalLiquid` are omitted because their **amount units and interval remain unverified**. Known original Open-Meteo preceding-hour mm are preserved in the owned extension. Native daily `pressure`/`visibility` are likewise omitted while source daily hPa/metres remain available there; a verified current visibility-km display does not prove the separate daily contract. Native `snow` could mean depth, liquid equivalent or another quantity; source cm and mm are not interchangeable. Native `no2`/`o3`/`so2` units and radar integer time/amount meaning remain unverified. The inspected native AQI DTO has **no CO field**. [1], [4], [7], [8]

The actual native readers are different models: `WeatherInfoBean` contains hourly forecast but no current AQ/radar/lifestyle object; `WeatherBean` contains those service objects but no hourly group. Native service-only arrays do not create a new stock-widget feature. Alert `type` strings1–14 and numeric levels are known, but Open-Meteo forecast/AQ supplies no official native warning feed or grade mapping. No warnings are invented from severe WMO conditions. [1]

Daily `conditionDay` selects a valid same-date `is_day=1` sample nearest **12:00**. `conditionNight` selects a valid same-date evening `is_day=0` sample with local hour **>=12**, nearest **21:00**, so the daily night row represents the upcoming evening rather than that date's preceding03:00 weather. If no qualifying sample exists, the real daily forecast supplies the fallback. These remain representative conditions rather than half-day accumulation. [4]

Before that daily sampling, `WeatherMapping.toBydJson` validates **every supplied hourly `weather_code` and `is_day` entry**, including hours beyond the selected48 display rows. An unsupported/non-integral code or invalid day flag anywhere in that source series rejects the payload before publication, so malformed later hours cannot silently become an ordinary daily fallback. This validation changes no native field type or disposition. [4]

`WeatherRuntime` tracks **validated forecast/cache refresh success separately from native provider sync**. After complete payload validation it persists the owned renderer's payload and `weather_last_fetch_ms`, records forecast success, and follows normal refresh cadence. Native sync/readback independently updates `weather_last_success_ms`; unavailable stock sync is reported separately. Native provider failure therefore does not erase a valid owned forecast or force the weather-fetch failure cadence. Payload `servertime` remains the adapter refresh/generated `nowMs`, while native-sync success and runtime fetch/cadence timestamps are separate state. These runtime preferences are outside the371 native DTO declarations. [4]

## Owned source-data extension

`data.openMeteo` is an owned **schemaVersion1** extension, outside both native DTO families; native Gson ignores it. It preserves exact provider precision and original unit objects for current data, the same selected48 hours, sixteen daily rows, and optional current AQ. All requested forecast variables are retained, including hourly precipitation/rain/showers/snowfall, apparent temperature, pressure, visibility, UV and dew point, and daily precipitation/rain/showers/snowfall sums, maximum precipitation probability, precipitation hours, sunshine duration and maximum gusts. Original source times use `data.city.timezone`. The JSON's `owned_extension.groups` lists every requested variable and its unit-object path. [3], [4], [7]

Source snowfall uses **cm depth**; precipitation/rain/showers use **mm** with their original hourly/daily intervals. Daily sunshine duration uses **seconds** and precipitation hours uses **hours**. Forecast temperature is requested in Celsius and wind in km/h. AQ is explicitly **European AQI**, with PM2.5/PM10 in source **µg/m³**. Daily AQ values are the maximum available hourly European AQI for each forecast-local date; unavailable future dates retain no invented value. Source preservation does not claim every extra variable is displayed or manufacture a native radar, alert or lifestyle feed. Native condition/hour/period `sourceWeatherCode` fields preserve original WMO numbers as additional owned fields. [2], [4], [7]

The retained expanded API response independently contains all requested forecast variables and their units: **384 source hours**, **16 daily dates**, `timezone=Asia/Amman`, and `utc_offset_seconds=10800`. It confirms the requested current visibility/UV, daily mean pressure/minimum visibility, lunar event fields and additional precipitation/sunshine/gust variables are accepted in this response. This is provider-response evidence, not native semantic proof, a guarantee of non-null values in every model/location, or a vehicle/UI test. The audit made no additional network request. [10]

## Stock launcher hosting constraint

The supplied Launcher3 APK supports Android AppWidget hosting and a widget picker, but it **filters arbitrary providers**. This is a confirmed constraint in this firmware, not a device test. `WidgetsModel.<clinit>` defines four component arrays for domestic/ROW and Dolphin variants; none contains the BYD Weather app. `WidgetsModel.getWhiteWidgets` selects one compiled array. `AutoStudyUtil.updateWhiteWidgetAndFilterBigshortcuts` clears its set and adds only installed/enabled providers already in that array. No provider metadata or user setting is read to extend this set. All five `AutoStudyUtil.getWhiteWidgets` callers use membership checks; none adds a provider. [9]

`WidgetsModel.setWidgetsAndShortcuts` rejects providers absent from the set. `AutoInstallsLayout.AppWidgetParser.parseAndAdd` also rejects them, and `LauncherModel.LoaderTask.loadWorkspace` removes non-whitelisted existing widgets, logging `Invalid widget removed because filter`. Merely declaring a standard AppWidget provider therefore does not make it usable on this exact stock picker. [9]

The original manifest has **no** `android.content.pm.action.CONFIRM_PIN_APPWIDGET`/`CONFIRM_PIN_SHORTCUT` activity filter and no `AddItemActivity`. Exact DEX searches found no `PinItemRequest`, `getPinItemRequest`, pin-confirmation action or `requestPinAppWidget` consumer; only the legacy `APPWIDGET_BIND` path from drag/drop was found. Android10 alone does not establish pin-capable hosting. A runtime `isRequestPinAppWidgetSupported` check can enable legitimate user-confirmed pinning on a compatible host, but no stock-launcher pin route or sanctioned arbitrary-provider enablement was found in this artifact. An owned forecast screen or standard widget on another compatible host can expose correct per-hour icons; neither is proof of stock-host widget placement. No firmware/launcher mutation was performed. [9]

## All native JSON paths

The emission/conversion and unit details are expanded in the JSON for every native declaration. **L** is `com.byd.weatherlibrary.WeatherInfoBean`; **S** is `com.byd.weatherdata.WeatherBean`. Native types are nullable Java types. Both models' identical fields remain independent declarations in the JSON.

Adapter snapshot parent commit: `9fd4bb02ea61fbcefa12e915afc27ce273899a6a`. Mapper/runtime working tree modified: **true**; the actual source-file hashes identify the audited contents. Mapper SHA-256: `3bba1941c036ac7c383f534ff2a4828e92088bc774d330dc58763dcb5c19ffed`. Runtime SHA-256: `13cfc3039e3670862fd20ed56f16f911e930969d9908a541953316dfa1d53c95`.

Declaration counts: **compatibility: 48**, **derived: 80**, **mapped: 81**, **unavailable: 75**, **unused: 14**, **unverified: 73**. Total **371**.

| Native JSON path | Model / Java type | Disposition | Source / conversion and boundary |
| --- | --- | --- | --- |
| `$.data` | L: DataDTO, S: DataDTO | mapped | Assemble decoded provider data object |
| `$.data.alarm` | L: List<AlarmsDTO>, S: List<AlarmsDTO> | compatibility | Truthful empty array; upstream does not supply native official-warning/lifestyle objects |
| `$.data.alarm[].affectarea` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].content` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].endtime` | L: Long, S: Long | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].expiretime` | L: Long, S: Long | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].gradeDesc` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].guide` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].id` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].isEffect` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].level` | L: Integer, S: Integer | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].levelName` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].mobilelink` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].priority` | L: Integer, S: Integer | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].publictime` | L: Long, S: Long | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].releaseAgency` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].relievedtime` | L: Long, S: Long | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].source` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].title` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].titleEn` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].type` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.alarm[].zmRecommend` | L: String, S: String | unavailable | No official native warning source supplied by examined weather/AQ APIs; native type1..14 and numeric grade require real warning mapping |
| `$.data.aqi` | S: AqiDTO | mapped | Build populated forecast/geocoder/AQ group |
| `$.data.aqi.aqidesc` | S: String | derived | Current European AQI/value text/European category description; missing=-1/--/Unknown; Native scale unresolved; owned extension explicitly labels European AQI |
| `$.data.aqi.aqivalue` | S: Integer | derived | Current European AQI/value text/European category description; missing=-1/--/Unknown; Native scale unresolved; owned extension explicitly labels European AQI |
| `$.data.aqi.aqivaluetext` | S: String | derived | Current European AQI/value text/European category description; missing=-1/--/Unknown; Native scale unresolved; owned extension explicitly labels European AQI |
| `$.data.aqi.lv` | S: String | compatibility | ConstantString0 leaves incompatible native pollution-category label unknown |
| `$.data.aqi.mobilelink` | S: String | compatibility | Actual Open-Meteo source URL https://open-meteo.com/ |
| `$.data.aqi.no2` | S: Integer | unverified | Source gas concentration exists but native units are unknown; no numeric conversion guessed |
| `$.data.aqi.o3` | S: Integer | unverified | Source gas concentration exists but native units are unknown; no numeric conversion guessed |
| `$.data.aqi.pm10` | S: Integer | mapped | Rounded current particle concentration; missing=-1 |
| `$.data.aqi.pm25` | S: Integer | mapped | Rounded current particle concentration; missing=-1 |
| `$.data.aqi.pm25desc` | S: String | unverified | No native particulate description vocabulary/thresholds established |
| `$.data.aqi.so2` | S: Integer | unverified | Source gas concentration exists but native units are unknown; no numeric conversion guessed |
| `$.data.aqi.updatetime` | S: Long | compatibility | AQ payload refresh nowMs, not a fabricated observation age |
| `$.data.aqidays` | S: List<AqidaysDTO> | compatibility | DTO known; leave empty until native AQ standard/date/aggregation are established; Hourly European AQI availability alone does not define native AQ-day semantics |
| `$.data.aqidays[].aqi` | S: Integer | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.aqidays[].aqiMax` | S: Integer | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.aqidays[].aqiMin` | S: Integer | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.aqidays[].date` | S: String | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.aqidays[].lv` | S: Integer | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.aqidays[].pm25` | S: Integer | unverified | Native AQ standard/date format/aggregation/unit policy not established; hourly source is insufficient by itself |
| `$.data.city` | L: CityDTO, S: CityDTO | mapped | Build populated forecast/geocoder/AQ group |
| `$.data.city.administrativearea` | L: AdministrativeareaDTO, S: AdministrativeareaDTO | mapped | Actual geocoder principalSubdivision supplies localized regional names without IDs/levels |
| `$.data.city.administrativearea.englishName` | L: String, S: String | unused | Real principalSubdivision name only when request language is known English |
| `$.data.city.administrativearea.id` | L: String, S: String | unverified | No source establishes native administrative ID or level semantics |
| `$.data.city.administrativearea.level` | L: Integer, S: Integer | unverified | No source establishes native administrative ID or level semantics |
| `$.data.city.administrativearea.localizedname` | L: String, S: String | mapped | Actual BigDataCloud.principalSubdivision supplies this regional name; no ID/level invented |
| `$.data.city.ca` | L: String, S: String | unverified | Native coordinate keys declared, but axis/purpose/unit not established by native consumer; do not equate API grid coordinates with fresh GPS |
| `$.data.city.citycode` | L: String, S: String | unverified | Native administrative identifier/level system is unknown; no fabricated GPS sentinel, numeric level, or prefecture identifier |
| `$.data.city.co` | L: String, S: String | unverified | Native coordinate keys declared, but axis/purpose/unit not established by native consumer; do not equate API grid coordinates with fresh GPS |
| `$.data.city.countryCode` | L: String, S: String | mapped | BigDataCloud.countryCode |
| `$.data.city.countryname` | L: String, S: String | mapped | BigDataCloud.countryName |
| `$.data.city.englishCityName` | L: String, S: String | derived | Distinct geocoder district plus city; trailing English Sub-District descriptor removed; fallback retained; englishCityName currently receives the same localized combined label; no translation is fabricated |
| `$.data.city.englishCountryName` | L: String, S: String | unused | Optional English label; only copy a countryName when geocoder request language is known English; Runtime geocoder uses device language; localized names are not proof of English translations |
| `$.data.city.level` | L: Integer | unverified | Native administrative identifier/level system is unknown; no fabricated GPS sentinel, numeric level, or prefecture identifier |
| `$.data.city.name` | L: String, S: String | derived | Distinct geocoder district plus city; trailing English Sub-District descriptor removed; fallback retained; englishCityName currently receives the same localized combined label; no translation is fabricated |
| `$.data.city.parentcity` | L: String, S: String | mapped | BigDataCloud.city |
| `$.data.city.prefectureCity` | S: PrefectureCityDTO | unverified | Native administrative identifier/level system is unknown; no fabricated GPS sentinel, numeric level, or prefecture identifier |
| `$.data.city.prefectureCity.id` | S: String | unverified | No verified native prefecture identifier source |
| `$.data.city.provincename` | L: String, S: String | mapped | BigDataCloud.principalSubdivision |
| `$.data.city.supplementalAdminAreas` | L: List<SupplementalAdminAreasDTO>, S: List<SupplementalAdminAreasDTO> | mapped | Actual geocoder principalSubdivision supplies localized regional names without IDs/levels |
| `$.data.city.supplementalAdminAreas[].englishName` | L: String, S: String | unused | Real principalSubdivision name only when request language is known English |
| `$.data.city.supplementalAdminAreas[].id` | L: String, S: String | unverified | No source establishes native administrative ID or level semantics |
| `$.data.city.supplementalAdminAreas[].level` | L: Integer, S: Integer | unverified | No source establishes native administrative ID or level semantics |
| `$.data.city.supplementalAdminAreas[].localizedName` | L: String, S: String | mapped | Actual BigDataCloud.principalSubdivision supplies this regional name; no ID/level invented |
| `$.data.city.timezone` | L: String, S: String | mapped | forecast.timezone |
| `$.data.condition` | L: ConditionDTO, S: ConditionDTO | mapped | Build populated forecast/geocoder/AQ group |
| `$.data.condition.cloudCover` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.cnweatherid` | L: Integer, S: Integer | derived | WMO-to-native condition ID; official WMO97 supported |
| `$.data.condition.comfortlink` | L: String, S: String | unused | No actual adapter weather-map/comfort destination; omit invented proprietary links |
| `$.data.condition.compareFlag` | L: String, S: String | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.desc` | L: String, S: String | compatibility | Actual Open-Meteo attribution / https://open-meteo.com/; source metadata, not a measurement |
| `$.data.condition.expiretime` | L: Long, S: Long | compatibility | Adapter refresh nowMs plus6h; not a forecast validity measurement |
| `$.data.condition.feelTemperatureShade` | L: Integer, S: Integer | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.humidity` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.mobilelink` | L: String, S: String | compatibility | Actual Open-Meteo attribution / https://open-meteo.com/; source metadata, not a measurement |
| `$.data.condition.precipitation` | L: Float, S: Float | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.pressure` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.pressureTendency` | L: String, S: String | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.realfeel` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals; apparent_temperature is an Open-Meteo feels-like equivalent, not a proprietary RealFeel or shade-only formula |
| `$.data.condition.realfeelDesc` | L: String, S: String | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.temperature` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.uVIndex` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.updatetime` | L: Long, S: Long | derived | Forecast current.time parsed in forecast timezone |
| `$.data.condition.updatetimeFmt` | L: String, S: String | derived | Forecast-zone yyyy-MM-dd HH:mm |
| `$.data.condition.uvIndexDesc` | L: String, S: String | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.vipLocation` | L: String, S: String | unverified | No verified native shade formula/trend/description/flag vocabulary; ordinary numbers do not establish it |
| `$.data.condition.visibility` | L: Integer, S: Integer | derived | Rounded source metres then integer division by 1000 |
| `$.data.condition.weatherMapLink` | L: String, S: String | unused | No actual adapter weather-map/comfort destination; omit invented proprietary links |
| `$.data.condition.weatherid` | L: Integer, S: Integer | compatibility | Copy cnweatherid as legacy adapter convention; No consumer proof establishes interchangeability of the three weather-ID systems |
| `$.data.condition.weathertext` | L: String, S: String | derived | English WMO condition description; preserves all29 official descriptions |
| `$.data.condition.winddegrees` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.winddir` | L: String, S: String | derived | Eight-point compass from sustained wind-from bearing |
| `$.data.condition.winddirtext` | L: String, S: String | derived | Eight-point compass from sustained wind-from bearing |
| `$.data.condition.windgustdir` | L: String, S: String | unavailable | Open-Meteo does not supply an independent gust direction; do not copy sustained bearing |
| `$.data.condition.windgustlevel` | L: Integer, S: Integer | derived | Integer km/h Beaufort bands from the respective sustained/gust speed |
| `$.data.condition.windgustspeed` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.windlevel` | L: Integer, S: Integer | derived | Integer km/h Beaufort bands from the respective sustained/gust speed |
| `$.data.condition.windspeed` | L: Integer, S: Integer | mapped | Rounded source value, except precipitation retains decimals |
| `$.data.condition.zmweatherid` | L: Integer, S: Integer | compatibility | Copy cnweatherid as legacy adapter convention; No consumer proof establishes interchangeability of the three weather-ID systems |
| `$.data.dailys` | L: DailysDTO, S: DailysDTO | mapped | Build populated forecast/geocoder/AQ group |
| `$.data.dailys.dailyweathers` | L: List<DailyweathersDTO>, S: List<DailyweathersDTO> | mapped | Forecast-local selected hours / sixteen daily dates; native view slices visible items |
| `$.data.dailys.dailyweathers[].aqivalue` | L: Integer, S: Integer | derived | Peak available hourly European AQI by forecast-local date; today current fallback; missing=-1/--; Do not claim native Chinese AQI equivalence; owned extension explicitly labels European AQI |
| `$.data.dailys.dailyweathers[].aqivaluetext` | L: String, S: String | derived | Peak available hourly European AQI by forecast-local date; today current fallback; missing=-1/--; Do not claim native Chinese AQI equivalence; owned extension explicitly labels European AQI |
| `$.data.dailys.dailyweathers[].conditionDay` | L: ConditionDayDTO, S: ConditionDayDTO | derived | Representative valid is_day1 sample nearest12:00 on that local date; daily fallback if absent; Representative sample, not accumulated half-day conditions |
| `$.data.dailys.dailyweathers[].conditionDay.cloudCover` | L: String, S: String | mapped | Representative sample relative humidity/cloud cover, rounded |
| `$.data.dailys.dailyweathers[].conditionDay.cnweatherid` | L: Integer, S: Integer | derived | Representative period WMO-to-native code; daily fallback when sample absent |
| `$.data.dailys.dailyweathers[].conditionDay.humidity` | L: Integer, S: Integer | mapped | Representative sample relative humidity/cloud cover, rounded |
| `$.data.dailys.dailyweathers[].conditionDay.ice` | L: String, S: String | unverified | Native amount unit/purpose is unknown; source snowfall cm and snow-water equivalent mm are not interchangeable; no ice amount counterpart |
| `$.data.dailys.dailyweathers[].conditionDay.iceProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionDay.precProb` | L: String, S: String | mapped | Representative all-precipitation probability; exact native String type |
| `$.data.dailys.dailyweathers[].conditionDay.rain` | L: String, S: String | unverified | Native period amount units and interval are unverified; preserve known preceding-hour source amounts under data.openMeteo.hourly; No fabricated half-day total or native amount-unit equivalence |
| `$.data.dailys.dailyweathers[].conditionDay.rainProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionDay.snow` | L: String, S: String | unverified | Native amount unit/purpose is unknown; source snowfall cm and snow-water equivalent mm are not interchangeable; no ice amount counterpart |
| `$.data.dailys.dailyweathers[].conditionDay.snowProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionDay.thunProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionDay.totalLiquid` | L: String, S: String | unverified | Native period amount units and interval are unverified; preserve known preceding-hour source amounts under data.openMeteo.hourly; No fabricated half-day total or native amount-unit equivalence |
| `$.data.dailys.dailyweathers[].conditionDay.weatherid` | L: Integer, S: Integer | compatibility | Copy period cnweatherid as legacy convention; separate codebooks unknown |
| `$.data.dailys.dailyweathers[].conditionDay.weathertext` | L: String, S: String | derived | English representative WMO description |
| `$.data.dailys.dailyweathers[].conditionDay.windGustDir` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionDay.windGustPow` | L: String, S: String | derived | Beaufort force from respective representative sustained/actual gust speed |
| `$.data.dailys.dailyweathers[].conditionDay.winddir` | L: String, S: String | derived | Representative sustained wind compass; daily dominant fallback |
| `$.data.dailys.dailyweathers[].conditionDay.windlevel` | L: Integer, S: Integer | derived | Beaufort force from respective representative sustained/actual gust speed |
| `$.data.dailys.dailyweathers[].conditionDay.windspeed` | L: Integer, S: Integer | mapped | Representative sustained wind speed; daily max fallback |
| `$.data.dailys.dailyweathers[].conditionDay.zmweatherid` | L: Integer, S: Integer | compatibility | Copy period cnweatherid as legacy convention; separate codebooks unknown |
| `$.data.dailys.dailyweathers[].conditionNight` | L: ConditionNightDTO, S: ConditionNightDTO | derived | Representative valid is_day0 evening sample, local hour>=12, nearest21:00 on that same date; daily fallback if absent; Representative sample, not accumulated half-day conditions |
| `$.data.dailys.dailyweathers[].conditionNight.cloudCover` | L: String, S: String | mapped | Representative sample relative humidity/cloud cover, rounded |
| `$.data.dailys.dailyweathers[].conditionNight.cnweatherid` | L: Integer, S: Integer | derived | Representative period WMO-to-native code; daily fallback when sample absent |
| `$.data.dailys.dailyweathers[].conditionNight.humidity` | L: Integer, S: Integer | mapped | Representative sample relative humidity/cloud cover, rounded |
| `$.data.dailys.dailyweathers[].conditionNight.ice` | L: String, S: String | unverified | Native amount unit/purpose is unknown; source snowfall cm and snow-water equivalent mm are not interchangeable; no ice amount counterpart |
| `$.data.dailys.dailyweathers[].conditionNight.iceProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionNight.precProb` | L: String, S: String | mapped | Representative all-precipitation probability; exact native String type |
| `$.data.dailys.dailyweathers[].conditionNight.rain` | L: String, S: String | unverified | Native period amount units and interval are unverified; preserve known preceding-hour source amounts under data.openMeteo.hourly; No fabricated half-day total or native amount-unit equivalence |
| `$.data.dailys.dailyweathers[].conditionNight.rainProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionNight.snow` | L: String, S: String | unverified | Native amount unit/purpose is unknown; source snowfall cm and snow-water equivalent mm are not interchangeable; no ice amount counterpart |
| `$.data.dailys.dailyweathers[].conditionNight.snowProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionNight.thunProb` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionNight.totalLiquid` | L: String, S: String | unverified | Native period amount units and interval are unverified; preserve known preceding-hour source amounts under data.openMeteo.hourly; No fabricated half-day total or native amount-unit equivalence |
| `$.data.dailys.dailyweathers[].conditionNight.weatherid` | L: Integer, S: Integer | compatibility | Copy period cnweatherid as legacy convention; separate codebooks unknown |
| `$.data.dailys.dailyweathers[].conditionNight.weathertext` | L: String, S: String | derived | English representative WMO description |
| `$.data.dailys.dailyweathers[].conditionNight.windGustDir` | L: String, S: String | unavailable | No independent rain/snow/ice/thunder chance or gust-direction variable in examined provider schema; do not relabel total chance or copy direction |
| `$.data.dailys.dailyweathers[].conditionNight.windGustPow` | L: String, S: String | derived | Beaufort force from respective representative sustained/actual gust speed |
| `$.data.dailys.dailyweathers[].conditionNight.winddir` | L: String, S: String | derived | Representative sustained wind compass; daily dominant fallback |
| `$.data.dailys.dailyweathers[].conditionNight.windlevel` | L: Integer, S: Integer | derived | Beaufort force from respective representative sustained/actual gust speed |
| `$.data.dailys.dailyweathers[].conditionNight.windspeed` | L: Integer, S: Integer | mapped | Representative sustained wind speed; daily max fallback |
| `$.data.dailys.dailyweathers[].conditionNight.zmweatherid` | L: Integer, S: Integer | compatibility | Copy period cnweatherid as legacy convention; separate codebooks unknown |
| `$.data.dailys.dailyweathers[].currentFestival` | L: String, S: String | unavailable | Forecast/AQ provider does not supply authoritative festivals or traffic restrictions |
| `$.data.dailys.dailyweathers[].currentRestrict` | L: String, S: String | unavailable | Forecast/AQ provider does not supply authoritative festivals or traffic restrictions |
| `$.data.dailys.dailyweathers[].currentlink` | L: String, S: String | unused | No distinct current-condition destination required; source attribution/link supplied |
| `$.data.dailys.dailyweathers[].lv` | L: Integer, S: Integer | compatibility | Constant0 leaves incompatible native pollution-category label unknown; No false conversion from European AQI to native pollution categories |
| `$.data.dailys.dailyweathers[].maxtemp` | L: Integer, S: Integer | mapped | Rounded daily value; realFeel extrema use native strings |
| `$.data.dailys.dailyweathers[].mintemp` | L: Integer, S: Integer | mapped | Rounded daily value; realFeel extrema use native strings |
| `$.data.dailys.dailyweathers[].mobilelink` | L: String, S: String | compatibility | Actual Open-Meteo attribution / https://open-meteo.com/; not proprietary destinations |
| `$.data.dailys.dailyweathers[].moonRise` | L: Long, S: Long | derived | Real event ISO time to epoch ms; absent lunar event remains omitted |
| `$.data.dailys.dailyweathers[].moonRiseFmt` | L: String, S: String | derived | Format actual corresponding event in forecast zone |
| `$.data.dailys.dailyweathers[].moonSet` | L: Long, S: Long | derived | Real event ISO time to epoch ms; absent lunar event remains omitted |
| `$.data.dailys.dailyweathers[].moonSetFmt` | L: String, S: String | compatibility | Real same-date moonset offsetISO, else date-midnight compatibility string without fake moonSet number |
| `$.data.dailys.dailyweathers[].moonphase` | L: String, S: String | unverified | Moon cycle fraction exists; native phase/span vocabulary or purpose is unverified |
| `$.data.dailys.dailyweathers[].pm25` | L: Integer, S: Integer | unverified | Hourly PM2.5 source exists but native daily unit/aggregation is not established |
| `$.data.dailys.dailyweathers[].pressure` | L: Integer, S: Integer | unverified | Native daily unit/aggregation is unverified; preserve original source variable/units under data.openMeteo.daily |
| `$.data.dailys.dailyweathers[].publictime` | L: Long, S: Long | compatibility | Real same-date sunrise anchors native pre-dawn binary search; original cloud meaning unverified |
| `$.data.dailys.dailyweathers[].publictimeFmt` | L: String, S: String | derived | Forecast-local calendar date, preserved independently from sunrise anchor |
| `$.data.dailys.dailyweathers[].realFeelTempMax` | L: String, S: String | mapped | Rounded daily value; realFeel extrema use native strings |
| `$.data.dailys.dailyweathers[].realFeelTempMin` | L: String, S: String | mapped | Rounded daily value; realFeel extrema use native strings |
| `$.data.dailys.dailyweathers[].source` | L: String, S: String | compatibility | Actual Open-Meteo attribution / https://open-meteo.com/; not proprietary destinations |
| `$.data.dailys.dailyweathers[].spanDays` | L: Integer, S: Integer | unverified | Moon cycle fraction exists; native phase/span vocabulary or purpose is unverified |
| `$.data.dailys.dailyweathers[].spanDaysFull` | L: Integer, S: Integer | unverified | Moon cycle fraction exists; native phase/span vocabulary or purpose is unverified |
| `$.data.dailys.dailyweathers[].spanDaysNew` | L: Integer, S: Integer | unverified | Moon cycle fraction exists; native phase/span vocabulary or purpose is unverified |
| `$.data.dailys.dailyweathers[].sunRise` | L: Long, S: Long | derived | Real event ISO time to epoch ms; absent lunar event remains omitted |
| `$.data.dailys.dailyweathers[].sunRiseFmt` | L: String, S: String | derived | Format actual corresponding event in forecast zone |
| `$.data.dailys.dailyweathers[].sunSet` | L: Long, S: Long | derived | Real event ISO time to epoch ms; absent lunar event remains omitted |
| `$.data.dailys.dailyweathers[].sunSetFmt` | L: String, S: String | derived | Format actual corresponding event in forecast zone |
| `$.data.dailys.dailyweathers[].uvIndex` | L: Integer, S: Integer | mapped | Rounded daily value; realFeel extrema use native strings |
| `$.data.dailys.dailyweathers[].uvIndexText` | L: String, S: String | derived | String numeric daily UV maximum; no guessed descriptive enum |
| `$.data.dailys.dailyweathers[].visibility` | L: Integer, S: Integer | unverified | Native daily unit/aggregation is unverified; preserve original source variable/units under data.openMeteo.daily |
| `$.data.dailys.expiretime` | L: Long, S: Long | compatibility | nowMs plus adapter expiry: hourly6h, daily24h |
| `$.data.dailys.mobilelink` | L: String, S: String | unused | Optional daily-group source link; item-level source links supplied |
| `$.data.dailys.publictime` | L: Long, S: Long | derived | Today forecast-local midnight; top-level value is distinct from per-item sunrise anchors |
| `$.data.dailys.publictimeFmt` | L: String, S: String | derived | Today forecast-local yyyy-MM-dd |
| `$.data.hourlys` | L: HourlysDTO | mapped | Build populated forecast/geocoder/AQ group |
| `$.data.hourlys.expiretime` | L: Long | compatibility | nowMs plus adapter expiry: hourly6h, daily24h |
| `$.data.hourlys.hourlyweathers` | L: List<HourlyweathersDTO> | mapped | Forecast-local selected hours / sixteen daily dates; native view slices visible items |
| `$.data.hourlys.hourlyweathers[].cnweatherid` | L: Integer | derived | WMO-to-native ID; official WMO97 supported |
| `$.data.hourlys.hourlyweathers[].date` | L: Long | derived | Hourly ISO instant parsed in forecast zone |
| `$.data.hourlys.hourlyweathers[].isdaynight` | L: Boolean | derived | Validate is_day exact0/1, emit false/true |
| `$.data.hourlys.hourlyweathers[].rainprobability` | L: Integer | mapped | All-precipitation probability, not independent rain-only probability |
| `$.data.hourlys.hourlyweathers[].temp` | L: Integer | mapped | Rounded Celsius temperature |
| `$.data.hourlys.hourlyweathers[].wd` | L: String | derived | Eight-point sustained wind direction |
| `$.data.hourlys.hourlyweathers[].weatherid` | L: Integer | compatibility | Copy hourly cnweatherid; separate code systems unverified |
| `$.data.hourlys.hourlyweathers[].wp` | L: Integer | derived | Sustained wind Beaufort conversion |
| `$.data.hourlys.hourlyweathers[].zmweatherid` | L: Integer | compatibility | Copy hourly cnweatherid; separate code systems unverified |
| `$.data.liveInfos` | S: List<LiveInfosDTO> | compatibility | Truthful empty array; upstream does not supply native official-warning/lifestyle objects |
| `$.data.liveInfos[].code` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].content` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].day` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].expiretime` | S: Long | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].level` | S: Integer | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].mobilelilnk` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].name` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].status` | S: String | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.liveInfos[].updatetime` | S: Long | unavailable | No native lifestyle-index objects/grade algorithm supplied; weather-derived advice is not the source index |
| `$.data.mobilelink` | S: String | compatibility | Open-Meteo attribution / https://open-meteo.com/ |
| `$.data.radar` | S: RadarDTO | unverified | Native radar series units, interval, skycon vocabulary and source are unknown |
| `$.data.radar.dataTime` | S: Long | unverified | Integer radar series timing/units and skycon vocabulary unknown; minutely15 forecast is not a native radar feed |
| `$.data.radar.dataseries` | S: List<Integer> | unverified | Integer radar series timing/units and skycon vocabulary unknown; minutely15 forecast is not a native radar feed |
| `$.data.radar.skycon` | S: String | unverified | Integer radar series timing/units and skycon vocabulary unknown; minutely15 forecast is not a native radar feed |
| `$.data.weatherDesc` | S: String | compatibility | Open-Meteo attribution / https://open-meteo.com/ |
| `$.resultcode` | L: String, S: String | compatibility | Provider success envelope: resultcode="0", resultinfo="success" |
| `$.resultinfo` | L: String, S: String | compatibility | Provider success envelope: resultcode="0", resultinfo="success" |
| `$.servertime` | L: Long, S: Long | compatibility | Adapter payload refresh/generated nowMs; distinct from runtime fetch/native-sync completion timestamps |

## Primary evidence and validation

[1] [Firmware weather contract](firmware-weather-contract.md), grounded in the original APK hash, all native DTO fields in `classes2.dex`, `WeatherQueryUtil`, `DialogActivity`, `WeatherWidgetProvider`, `HoursAdapter`, `DaysAdapter`, `RequestService`, `NumberUtils`, `WeatherUtils` and resource values. Exact declaration descriptors and native class identities are retained in the JSON, alongside `/workspace/scratch/byd-firmware/native-dto-fields.json` and the original disassembly. Native field lists and consumer read sites were inspected directly in this audit.

[2] Official [Open-Meteo forecast docs](https://open-meteo.com/en/docs), existing cache [/tmp/open-meteo-docs.html](/tmp/open-meteo-docs.html). The WMO table gives97 Heavy thunderstorm and its model explanation says non-hail models report95/97. Its SHA-256 is retained in the JSON. No new live response is claimed.

[3] Original APK bundled Gson `ReflectiveTypeAdapterFactory.getFieldNames`/`Adapter.read`, `TypeAdapters.STRING` and `JsonReader.nextString`: exact naming and numeric-token String coercion. Exact native casing matters. No consumer aliases are inferred from getter capitalization.

[4] Original APK `DialogActivity.getWeatherData` and `DateUtils.isLessThanTenMinute`; adapter `WeatherMapping.toBydJson`/`WeatherRuntime`. Snapshot/hash identities above distinguish observed implementation from planned additions.

[5] [BigDataCloud district primary-source research](bigdatacloud-district-fields.md), [geocoding research](bigdatacloud-geocoding.md), and actual adapter `WeatherRuntime.geocode` language query/`WeatherMapping` administrative selection. Geocoder names are not supplied by Open-Meteo.

[6] [Native icon mapping](weather-icon-mapping.md), direct original native selector/resource inventory and the owned-widget opportunity; [firmware contract's executable isolated replay](firmware-weather-contract.md) identifies the global-night bug and sunrise-anchor workaround without claiming full Android/device execution.

[7] [Pinned Open-Meteo source/API research](open-meteo-widget-features.md): official server `VariableHourly.swift`, `VariableDaily.swift`, `AirQuality.swift`, AQ OpenAPI and units; source revision `b06f4760fd1f997e5559bb380f64c5e496b4a509`. The JSON stores direct provider-owned source URLs. [Implemented conversions](widget-mapping-implementation.md) records representative selection, EAQI day peaks and rounding policies. Official units describe upstream values, not an unobserved native gas/amount contract.

[8] Original `WeatherBean.DataDTO.AqiDTO`, period DTOs, `RadarDTO`; source field types inspected in both DEX and readable Java. AQ/radar fields not read outside getters/setters in the inspected current/forecast layouts remain semantic questions rather than visible-feature promises.

[9] Original `Launcher3.apk`, SHA-256 `2e657eb3f0bb94fdb23aa6f2ed06f6bdac6f2476175bc716f34e33a4cb9dcaae`. Primary manifest dump `/workspace/scratch/byd-firmware/launcher-manifest.txt`; DEX disassembly `launcher-dexdump.txt`. JADX1.5.6 single-class readable views are `/workspace/scratch/byd-firmware/decompiled/launcher-whitelist/WidgetsModel.java` and `AutoStudyUtil.java`; the latter has one unrelated decompilation error, so DEX remains authoritative. DEX method offsets: `WidgetsModel.<clinit>` **1ce4e0**, `getWhiteWidgets` **1ce420**, `setWidgetsAndShortcuts` **1ce764** (membership **1ce7ee–1ce806**); `AutoStudyUtil.getWhiteWidgets` **1d2f68**, `updateWhiteWidgetAndFilterBigshortcuts` **1d3214** (only addition **1d32d6–1d32da**); `LauncherModel.LoaderTask.loadWorkspace` **18c918** (filter/removal **18d9d6–18da0a**); `AutoInstallsLayout.AppWidgetParser.parseAndAdd` **1601cc** (filter **160252–160270**). The JSON records these source identities. All native reads were static; no OEM code was run on Android or a vehicle.

[10] Retained primary Open-Meteo response `/workspace/scratch/byd-build/full-mapping-api-probe.json`, SHA-256 `bba86b81d976e6c39f1345f6a50f593a3bc31a120596da5b167784db062d10a8`; original JSON keys, array lengths, timezone/offset and unit objects inspected directly. The query variable list and unit/timezone options are in the audited `WeatherRuntime` source; source preservation is in `WeatherMapping.sourceData`. Response obtained by the coordinated implementation task; no additional request was made by this audit.

Validation: the generated coverage contains **371 unique declaration IDs**, matching all29 native DTO classes exactly. Every declaration has one permitted disposition, source references, units, an emission policy, a mapping explanation and native-consumer observations. The unique-path table is generated from the same records; duplicate service/library declarations are preserved in the JSON rather than silently dropped. Local primary artifact and source-snapshot hashes are recorded for reproducibility. This is documentation/contract validation, not an application test or a vehicle-rendering result.
