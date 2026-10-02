# Native weather icons and dynamic Open-Meteo mapping

Research date: 2026-10-02. Scope: the supplied DiLink 3.0 firmware's `com.byd.weatherdata` APK, version **2.9.5.250616**, code **60**, SHA-256 `f585062b2b393e3a87019da3b40c2daa089e60ddacdfab189c22c67f2279281a`. These findings describe that APK, not every BYD firmware. The extraction and parser evidence are recorded in [the firmware contract](firmware-weather-contract.md). This investigation inspected existing sources and resources; it did not change the app or produce a new APK. [1]

## Available artwork

The native mipmap resource table declares **nine weather-condition icons**, including the no-data fallback, plus **two solar-event icons**. There is one clear-night moon; there are no additional condition mipmaps for cloudy nights, separate rain intensities, separate snow intensities, freezing precipitation, or hail. This inventory is limited to the inspected native weather mipmaps and selector methods. [2], [3], [4]

| Display category | Native resource | Resource ID |
| --- | --- | --- |
| Clear day / sun | `icon_weather_sunny` | `0x7f0c0040` |
| Clear night / moon | `icon_weather_night` | `0x7f0c003c` |
| Cloudy | `icon_weather_cloudy` | `0x7f0c0039` |
| Overcast | `icon_weather_dready` | `0x7f0c003a` |
| Fog | `icon_weather_fog` | `0x7f0c003b` |
| Rain | `icon_weather_rain` | `0x7f0c003e` |
| Snow | `icon_weather_snow` | `0x7f0c003f` |
| Thunderstorm | `icon_weather_thundershower` | `0x7f0c0041` |
| No data | `icon_weather_no_data` | `0x7f0c003d` |
| Sunrise event | `icon_sunrise` | `0x7f0c0033` |
| Sunset event | `icon_sunset` | `0x7f0c0034` |

The [extracted icon gallery](/workspace/reports/byd-weather-icons/icons.html) displays all eleven forecast/event images and fourteen separate warning images; its [inventory](/workspace/reports/byd-weather-icons/icon-inventory.json) records the APK paths and resource IDs. Both cloudy and overcast artwork are clouds without an embedded sun/moon; the thunderstorm image has cloud/lightning. These Android resource IDs are internal artwork addresses: **`0x7f0c003c` is not a valid JSON moon `cnweatherid`**. [2], [11]

`dready` is the APK's actual spelling. Its selector uses native condition ID `2`, whose string resource is the overcast condition. The resource table also contains landscape/portrait condition backgrounds and cards; these are separate assets from the forecast icons. Wind, humidity, UV (`icon_rays`), visibility, and general controls are also present. [2], [3]

Warning artwork belongs to the native alarm-type selector and is not selected by ordinary `cnweatherid` values. The warning renderer in this APK is `FlowTagAdapter`, which reads `AlarmBean.type` through `WeatherUtils.transformAlarmTypeToPic` and uses the alarm level for the tag background. `NumberUtils.mapTypeList` accepts native `alarm[].type` decimal strings `"1"` through `"14"` and parses them into those integer types. Its exact icon mapping is below. [3], [12], [13]

| Native alarm type | Warning category | Resource |
| --- | --- | --- |
| `1` | Typhoon | `icon_typhoon_warn` |
| `2` | Storm | `icon_storm_warn` |
| `3` | Snow | `icon_snow_warn` |
| `4` | Cold wave | `icon_cold_wave_warn` |
| `5` | Wind | `icon_wind_warn` |
| `6` | Sandstorm | `icon_sandstorm_warn` |
| `7` | High temperature | `icon_high_temp_warn` |
| `8` | Drought | `icon_drought_warn` |
| `9` | Thunderstorm | `icon_thunderstorm_warn` |
| `10` | Hail | `icon_hail_warn` |
| `11` | Frost | `icon_frost_warn` |
| `12` | Fog | `icon_fog_warn` |
| `13` | Haze | `icon_haze_warn` |
| `14` | Road ice | `icon_road_ice_warn` |

Using these as official warning tags requires an actual warning source and severity mapping. A WMO thunderstorm code is a forecast condition; it does not establish an official thunderstorm warning. The examined Open-Meteo forecast/AQ interfaces do not supply the native alert objects, so no warning feed is inferred or added here. [1], [8]

## Exact native condition-to-icon selector

`WeatherUtils.weatherConditionMap(Integer)` consumes **`cnweatherid`**, not the Open-Meteo WMO number. The same numbers have different meanings in the two systems; WMO `3` is overcast, but native `3` selects rain. The native mapping is: [3], [5], [6]

| Native `cnweatherid` | Selected resource |
| --- | --- |
| `0` | `icon_weather_sunny` |
| `1`, `20`, `29`, `30`, `31`, `53` | `icon_weather_cloudy` |
| `2` | `icon_weather_dready` |
| `3`, `6`, `7`, `8`, `9`, `10`, `11`, `12`, `19`, `21`, `22`, `23`, `24`, `25` | `icon_weather_rain` |
| `4`, `5` | `icon_weather_thundershower` |
| `13`, `14`, `15`, `16`, `17`, `26`, `27`, `28` | `icon_weather_snow` |
| `18` | `icon_weather_fog` |
| Any other integer | `icon_weather_no_data` |

The grouping describes artwork reuse, not equivalent weather meanings. Native sand/dust/haze codes share the cloudy icon; native mixed rain/snow and freezing-rain codes share rain. Light, moderate, and heavy rain have distinct native IDs `7`, `8`, `9`, but all use the same picture. Snow IDs `14`, `15`, `16` likewise reuse one snow icon, and both thunderstorm IDs `4`, `5` reuse one thunderstorm icon. The condition strings retain distinctions that the artwork does not show. [2], [3]

The moon is **not assigned a numeric weather-condition ID** by this method. `HoursAdapter` selects it in a separate branch. Its event rows reserve native `-1` for sunrise and `-2` for sunset; the adapter inserts these from each daily entry's `sunRise` and `sunSet`. Never publish `-1` as an invented moon or unknown-weather code: the hourly renderer interprets it as a sunrise event. [3], [4]

## Mapping incoming Open-Meteo rows

The app already performs the dynamic translation. `WeatherRuntime` requests current/hourly `weather_code` and `is_day`; `WeatherMapping.toBydJson` converts each selected hour's own `weather_code`, copies the resulting native ID into `cnweatherid`, and preserves that hour's `is_day == 1` as a Boolean `isdaynight`. It does not reuse the current condition's code across all hours. The mapper also emits the compatibility spelling `Isdaynight`; only lowercase `isdaynight` is a declared field in this native hourly DTO. [1], [6], [7]

The following table records the current mapper's implemented choices for all29 documented WMO codes, including `97`. WMO meanings follow the pinned provider-owned server research and the cached official documentation. The BYD IDs are this adapter's compatibility choices, confirmed against this firmware's icon selector rather than a published BYD API standard. [3], [6], [8], [14]

| Incoming WMO `weather_code` | Source category | Current native `cnweatherid` | Native artwork |
| --- | --- | --- | --- |
| `0` | Clear | `0` | Sun, with a separate moon branch |
| `1`, `2` | Mainly clear / partly cloudy | `1` | Cloudy |
| `3` | Overcast | `2` | Overcast |
| `45`, `48` | Fog / depositing rime fog | `18` | Fog |
| `51`, `53`, `55` | Slight / moderate / dense drizzle | `7`, `8`, `9`, respectively | Rain |
| `56`, `57` | Freezing drizzle | `19` | Rain |
| `61`, `63`, `65` | Slight / moderate / heavy rain | `7`, `8`, `9`, respectively | Rain |
| `66`, `67` | Freezing rain | `19` | Rain |
| `71`, `73`, `75` | Slight / moderate / heavy snowfall | `14`, `15`, `16`, respectively | Snow |
| `77` | Snow grains | `14` | Snow |
| `80`, `81`, `82` | Slight / moderate / violent rain showers | `3` | Rain |
| `85`, `86` | Slight / heavy snow showers | `13` | Snow |
| `95` | Thunderstorm | `4` | Thunderstorm |
| `97` | Heavy thunderstorm | `4` | Thunderstorm |
| `96`, `99` | Thunderstorm with hail | `5` | Thunderstorm |

This is a lookup evaluated whenever data is mapped: `Open-Meteo weather_code → native cnweatherid → native resource`. No new icon needs to be downloaded when a condition changes. Changing IDs to improve intensity labels will not create additional intensity-specific artwork in the stock app. The mapper returns `-1` internally for unsupported WMO codes and rejects unsupported hourly conditions before publication; that internal error sentinel must remain distinct from the native renderer's published sunrise marker. [3], [4], [6]

The cached official Open-Meteo docs list `97` as **heavy thunderstorm** and state that models without explicit hail detection "derive thunderstorms from instability parameters and report codes 95 and 97." The implemented mapper accepts `97` and maps it to native `4`, because `97` does not assert hail; native `5` is the hail condition. Both native IDs select the same thunderstorm artwork, while the emitted source description preserves **Heavy thunderstorm**. The earlier missing mapping has been resolved. This implementation is grounded in documented upstream support; no retained live fixture establishes an observed `97` response, and the older pinned server enum's absence of `97` is not evidence that the current API cannot emit it. [2], [3], [6], [14]

## Day/night limitation and recommended renderer rule

Open-Meteo `is_day` means `1` for daylight and `0` for night. The stock `HoursAdapter` computes one `isDuringNight` flag from the current update time and daily solar events, then applies it to every row:

```java
globalNight && cnweatherid == 0
    ? icon_weather_night
    : WeatherUtils.weatherConditionMap(cnweatherid)
```

It does not read that row's parsed `isdaynight` flag. `DaysAdapter` also uses the global current-night flag and `conditionDay.cnweatherid`; its future-day clear icons can consequently be moons when the current state is night. These paths are visible in the decompiled methods and independently corroborated by their DEX instructions. [4], [5], [9], [10]

The existing sunrise-anchored `dailyweathers[].publictime` workaround can correct the stock pre-dawn global-night calculation. It cannot make a list spanning sunrise/sunset choose correctly for every clear hour. There is no payload-only per-hour moon condition ID available through this selector. [1], [3], [4]

For a renderer we control, the rule should use the values at the **same hourly array index**:

```text
row = { time: hourly.time[i], code: hourly.weather_code[i], day: hourly.is_day[i] }
if row.code == 0 and row.day == 0: choose clear-night moon
else if row.code == 0 and row.day == 1: choose clear-day sun
else: choose the weather category from the WMO table above
```

Validate missing/unknown codes and invalid clear-condition day flags; show a no-data state in a custom renderer rather than inventing a condition. Insert sunrise/sunset as separate event rows from daily solar timestamps, not as WMO conditions. Keep nonclear categories the same at night while reusing this native asset set, because it has no cloudy-night variants. Additional night-cloud or intensity artwork would be a separate design addition. These are recommendations, not changes implemented in this research. [2], [4], [6], [8]

Accurate per-hour sun/moon therefore requires a **renderer change**: either a widget/dialog owned by this app or a deliberately modified stock consumer that reads each hourly `isdaynight`. Mapping incoming weather conditions already works dynamically; the remaining defect is the stock consumer's global day/night decision. No stock APK patch, installation, or replacement is implied by this note. [1], [4], [6]

## Sources and verification boundary

1. [Firmware contract and artifact provenance](firmware-weather-contract.md), including the APK version/hash, native DTO inventory, and isolated original-method night replay. The replay is not a vehicle rendering test.
2. Original APK [resource table](/workspace/scratch/byd-firmware/weatherdata-resource-values.txt:15858): all native mipmap specifications, numeric resource IDs, configurations, and native condition strings. Only the `icon_weather_*` resources above are declared as condition icons.
3. Original APK class `com.byd.weatherdata.utils.WeatherUtils`: [decompiled `weatherConditionMap`](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/utils/WeatherUtils.java:92), condition/background maps, `transformCNWeatherId`, and `transformAlarmTypeToPic`.
4. Original APK class `com.byd.weatherdata.adapter.HoursAdapter`: [constructor and binding](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/adapter/HoursAdapter.java:29), [event/condition selectors](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/adapter/HoursAdapter.java:85).
5. Original APK [`weatherConditionMap` DEX instructions](/workspace/scratch/byd-firmware/weatherdata-dexdump2.txt:155674), resource constants and switch; the resource table assigns their names.
6. Current application [`WeatherMapping.java`](../../app/src/main/java/com/ibramaswadeh/bydweather/WeatherMapping.java): `toBydJson` hourly conversion/whole-series validation, `weatherId`, `weatherText`, and `requireDayFlagAt`. The recorded source snapshot and SHA-256 are in [the complete field audit](native-field-coverage.json). Local workspace source inspected on this research date.
7. Current application [`WeatherRuntime.java`](../../app/src/main/java/com/ibramaswadeh/bydweather/WeatherRuntime.java): forecast query variables in `runRequest`. The recorded source snapshot and SHA-256 are in [the complete field audit](native-field-coverage.json). Local workspace source inspected on this research date.
8. Official Open-Meteo server [`WeatherCode.swift` at `b06f4760fd1f997e5559bb380f64c5e496b4a509`](https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Helper/WeatherCode.swift), [`VariableHourly.swift`](https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/VariableHourly.swift), and [`ForecastapiController.swift`](https://github.com/open-meteo/open-meteo/blob/b06f4760fd1f997e5559bb380f64c5e496b4a509/Sources/App/Controllers/ForecastapiController.swift). Provider facts are reused from the pinned primary-source research in [BYD weather API](byd-weather-api.md) and [Open-Meteo widget features](open-meteo-widget-features.md); no new live API observation is claimed. The earlier website/server enum disagreement about `97` should be assessed alongside the direct official-documentation statement in [14].
9. Original APK class `com.byd.weatherdata.adapter.DaysAdapter`: [global-night and daily icon selection](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/adapter/DaysAdapter.java:24).
10. Original APK [`HoursAdapter.getValidResourceId` DEX instructions](/workspace/scratch/byd-firmware/weatherdata-dexdump2.txt:676373) and [`DaysAdapter` DEX instructions](/workspace/scratch/byd-firmware/weatherdata-dexdump2.txt:675837). These corroborate global-night and clear-ID resource selection; decompiled source line numbers are navigation aids, not original source guarantees.
11. [Extracted original icon gallery](/workspace/reports/byd-weather-icons/icons.html) and [inventory](/workspace/reports/byd-weather-icons/icon-inventory.json), created from this APK's original PNG resources during the parent inspection; the images were visually inspected without changing their pixels.
12. Original APK class [`FlowTagAdapter`](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/adapter/FlowTagAdapter.java:29), warning type/icon/text and level/background selection.
13. Original APK class [`NumberUtils`](/workspace/scratch/byd-firmware/decompiled/weatherdata/sources/com/byd/weatherdata/utils/NumberUtils.java:35), `isRightAlarmType` and `mapTypeList`, accepted alert-type strings and conversion into `AlarmBean`.
14. Official [Open-Meteo forecast documentation](https://open-meteo.com/en/docs), cached at [/tmp/open-meteo-docs.html](/tmp/open-meteo-docs.html:193). The existing file is dated 2026-10-01; its WMO table and thunderstorm-model explanation explicitly document `97` and its meaning. Inspected from the existing cache during this research; no new network retrieval or Swift compute-path verification was performed.
