# District fix and Open-Meteo widget conversions

This records the updated 1.1.1 implementation, following the district-only-city report. Version code remains 3. The [district investigation](bigdatacloud-district-fields.md), [full public widget attribute inventory](byd-widget-schema.md), and [Open-Meteo feature research](open-meteo-widget-features.md) contain pinned primary sources and distinguish adapter conventions from verified native behavior.

The subsequent [original-firmware audit](firmware-weather-contract.md) verifies the native DTOs and consumer methods for DiLink 3.0's WeatherData 2.9.5.250616. Numeric daily item `publictime` now uses that date's actual sunrise: the native night search then selects yesterday's sunset before today's sunrise. Calendar labels, daily selection strings, AQI aggregation, period sampling and lunar fallback still use the original local date. A public-mapper regression and isolated original native-method replay verify the pre-dawn correction. The stock hourly renderer still uses one global night state, so per-hour flags cannot correct a forecast spanning a solar transition.

## Why the district could disappear

The previous mapper used `locality` (or, only if absent, `localityName`) and `city`. When locality equalled city, it collapsed the duplicate and stopped. It never examined `localityInfo.administrative`, even when a more specific district was available there. A constructed provider-shaped regression reproduced the city-only result; it is not a captured response from the user's car.

The mapper now tries distinct `locality`, then distinct `localityName`, then the most specific named administrative entry below the matching city's documented numeric `order`. Without a city anchor it accepts only a conservative district/suburb/neighbourhood/borough/ward description. Country, state, province, region, county, and governorate entries cannot substitute for a district. Missing district detail still produces the city alone; names are never invented. A trailing English `Sub-District`, `Sub District`, or `Subdistrict` label is removed from the displayed district name while preserving the place name. Both `name` and `englishCityName` receive the combined label. Available country, province, and parent-city metadata is preserved separately.

The app's status now displays **Location: …** for the last successful provider write/readback. Compare that label with the stock widget after an update. If the app's label includes the district but the stock widget omits it, the next investigation is the stock app's field selection/cache/refresh behavior. If both show only the city, inspect the device's permitted BigDataCloud response; it may lack a usable district or city hierarchy anchor. Neither possibility is established without device evidence.

## Implemented conversions

| Upstream data | Native attributes / behavior | Conversion and availability |
| --- | --- | --- |
| Response `timezone` and ISO timestamps | Current/hourly/daily epochs and formatted strings | Parse in source zone, format in forecast zone. Device zone no longer shifts forecast times. |
| Full hourly history + 15 forecast days | Up to 48 `hourlyweathers`, minimum 8 | Input contains 16 full local calendar dates; output begins at the current forecast hour, including a partially elapsed hour. |
| Hourly `is_day` | `isdaynight` and `Isdaynight` | Exact 0/1 becomes Boolean; both observed adapter spellings emitted. Every published hour is validated for matching Boolean flags. Native casing remains firmware-dependent. |
| Hourly precipitation | `hourlyweathers[].precipitation` | Number in mm for the preceding hourly interval. Null/missing values are omitted. Existing `rainprobability` remains all-precipitation probability. |
| Sustained wind and actual gust speed | `windlevel`, hourly `wp`, period `windGustPow`, current `windgustlevel` | Standard integer km/h Beaufort bands 0–12; actual speeds remain km/h. This replaces the arbitrary baseline buckets and avoids copying sustained force into gust force. Native wind-scale consumption still needs car verification. |
| Hourly weather, wind and `is_day` | Distinct `conditionDay` / `conditionNight` | Representative samples nearest local noon / midnight within that date and corresponding day flag. Ties retain first chronological sample. No matching usable sample keeps the original daily fallback. |
| Representative humidity, cloud and precipitation probability | Period `humidity` Number, `cloudCover` String, `precProb` Number | Rounded percentages, preserving observed native JSON types. No rain-only probability is fabricated. |
| Representative rain + showers, all precipitation | Period `rain` / `totalLiquid` Strings | Rain combines rain and showers; totalLiquid retains provider precipitation (including snow water equivalent). These are preceding-hour amounts at the selected sample, not accumulated half-day totals or snow depth. |
| Daily UV maximum | `uvIndex` / `uvIndexText` | Existing rounded numeric maximum is also emitted as a String; no descriptive vocabulary is guessed. |
| Daily apparent temperature max/min | `realFeelTempMax` / `realFeelTempMin` | Rounded Celsius as Strings, matching the public adapter's field types. |
| Daily mean sea-level pressure / minimum visibility | Daily `pressure` / `visibility` | Rounded hPa / kilometres. Missing optional metrics do not fail the forecast. |
| Daily moonrise / moonset | `moonRise`, `moonSet`, matching formats | Real event instants become epoch milliseconds; JSON null means no event and produces no numeric timestamp. |
| Daily date and optional real moonset | `moonSetFmt` | Offset ISO string always carries that daily date because the public stock-adapter investigation uses it to select today's min/max. When no same-date moonset exists, midnight is only a compatibility date string, never a fabricated numeric event. |
| Hourly European AQI | Existing daily `aqivalue`, `lv`, `aqivaluetext` | Worst available valid hourly value per forecast-local date. Current European AQI is retained separately; today falls back to current if hourly data is absent. Uncovered dates remain -1 / `--`. European scale remains explicit. |

All request units are pinned to Celsius, km/h and mm. The production weather query returned HTTP 200 with 384 hours and 16 daily entries in Asia/Amman. The production AQI query returned HTTP 200 with 144 hours (one past day plus five forecast days), units EAQI and PM concentrations µg/m³. Probes are source/API validation, not a car display test.

Beaufort uses integer km/h ranges 0–1, 2–5, 6–11, 12–19, 20–28, 29–38, 39–49, 50–61, 62–74, 75–88, 89–102, 103–117, and 118+. The [Met Office Beaufort scale](https://www.metoffice.gov.uk/weather/guides/coast-and-sea/beaufort-scale) establishes forces 0–12 and corresponding wind bands in knots/m/s (successfully retrieved during this research). The integer km/h rounding policy above is explicit; the native scale is inferred from the independent adapter, not a published BYD specification.

## Remaining limits

- `moon_phase` is available as a cycle fraction, but native `moonphase` vocabulary is unknown. No guessed label/enum is sent.
- Native pollutant-gas units, snow/ice amount units, pressure-trend codes, rain/snow/ice/thunder probabilities, radar item formats, and `aqidays` item schemas are unverified. Available source numbers cannot establish the native meaning. Existing PM2.5/PM10 and current AQI remain supported.
- Open-Meteo does not supply native alert/lifestyle objects; `alarm` and `liveInfos` stay empty. Filling them requires an actual schema and appropriate source, not weather-condition guesses.
- Numeric daily UV text is mapped. Current `uvIndexDesc` descriptive vocabulary remains unverified and unused.
- Solar rise/set remain required by the existing core contract. Polar dates with no event can still fail that core mapping; adding correct native absent-event handling requires further contract evidence.
- No stock WeatherData APK/parser or original full cloud/provider capture was found. The attribute inventory is the full union found in public adapters, not proof of every field on every DiLink firmware. Automated validation does not establish that every added field renders on the user's car.

The subsequent [hourly night-icon investigation](hourly-night-icons.md) confirms the mapper emits night flags at 03:00 in the recorded response. The user reports the stock widget still shows a sun; the stock icon-selection contract remains unresolved.
