# MET Norway weather integration

The provider boundary is `MetWeatherClient.load`: it returns validated forecast
points, solar dates, optional Norwegian air quality, and notices. `WeatherRuntime`
retains location acquisition, Android Geocoder, cancellation, scheduling, and the
verified stock-provider write. `WeatherMapping` only serializes the native payload.

`WeatherHttpClient` keeps bounded JSON downloads, conditional disk caching, gzip,
application identification, and persistent server backoff in one place. Invalid
source data invalidates its cached response. A transport failure never writes a
replacement widget payload. Cache storage failures do not block a valid download.

## Source fields

| MET field | Native use |
| --- | --- |
| `air_temperature` | Rounded current/hourly Celsius temperature; daily extrema. |
| `air_temperature_min/max` in `next_6_hours` | Daily extrema when the whole period belongs to that local date. |
| `symbol_code` | Native condition family, with original symbol and detailed source text retained. |
| `wind_speed`, `wind_from_direction` | m/s converted to km/h, Beaufort level, and eight-point compass direction. |
| `relative_humidity`, `air_pressure_at_sea_level` | Percent and hPa. |
| `ultraviolet_index_clear_sky` | Rounded clear-sky UV; `-1` when unavailable. |
| `apparent_air_temperature`, `wind_speed_of_gust` | Optional feels-like temperature and gust speed. |
| `probability_of_precipitation` | Optional hourly probability from the selected forecast period. |
| Sunrise `sunrise.time`, `sunset.time` | Epoch event times; offsets are parsed, not stripped. |
| Airqualityforecast `AQI` | Norwegian health-risk index displayed in this app, not converted to a Chinese AQI label. |

The shortest available `next_1_hours`, `next_6_hours`, or `next_12_hours` summary
is used. At least eight consecutive upcoming hourly records and all seven future
local dates must be present. The final instant-only source point does not need a
condition. Required fields with missing data or unexpected units reject an update.
Optional fields stay absent when unavailable; zero is never substituted for a
missing temperature, UV, concentration, or probability.

## Conditions

| MET condition | Stock ID and family |
| --- | --- |
| Clear sky | `0`, clear/sunny; factory astronomy selects its night card. |
| Fair, partly cloudy | `1`, cloudy. |
| Cloudy | `2`, overcast/dreary. |
| Fog | `18`, fog. |
| Light, moderate, heavy rain | `7`, `8`, `9`. |
| Rain showers, any intensity | `3`, shower. |
| Light, moderate, heavy snow | `14`, `15`, `16`. |
| Snow showers, any intensity | `13`, snow shower. |
| Sleet, including showers | `6`, rain and snow. |
| Any thunder symbol | `4`, thundershower; hail is not inferred. |

The [symbol fixture](../app/src/test/resources/met-symbol-contract.json) pins all
41 published symbol basenames to a MET repository revision. Tests exercise each
with day, night, and polar-twilight suffixes against the independent
[REA-derived factory contract](../app/src/test/resources/byd-weatherdata-2.9.5-contract.json).

## Factory timing and unavailable fields

The native daily adapter reads array positions 2–8. Position 0 supplies only
yesterday's solar timing, position 1 is today, and positions 2–8 are the forecast.
No historical weather values are fabricated for position 0. Normal daily anchors
are sunrise timestamps because the factory binary search uses the preceding
sunset and next sunrise to identify pre-dawn conditions.

The refresh timestamp is independent of the source forecast hour. A refresh just
after sunset must select night artwork even if the latest hourly point was during
daylight. Polar dates use out-of-range solar sentinels (`0`/`Long.MAX_VALUE`), which
the stock hourly insertion logic excludes. Absent events have `--` formatted text.
`moonSetFmt` carries the local date solely for the factory's date-selection path;
no moon observation is supplied.

REA inspection confirms visibility and UV are `Integer` fields. Visibility has no
MET source and uses `-1`; custom text cannot replace it in the unmodified detail
screen. The launcher card does not consume this field. Stock AQI categories remain
`lv=0`; MET's Norwegian risk index and pollutant values are retained separately.

The factory ignores custom condition text and per-hour night flags for artwork.
Its cloudy card is shared between day and night. These are renderer limitations,
not failed provider updates. Automated contract tests model the inspected firmware;
they do not replace testing on a physical head unit.

## Primary references

- [Locationforecast interface](https://api.met.no/weatherapi/locationforecast/2.0/documentation)
  and [data model](https://docs.api.met.no/doc/locationforecast/datamodel.html).
- [Sunrise 3.0](https://api.met.no/weatherapi/sunrise/3.0/documentation).
- [Airqualityforecast](https://api.met.no/weatherapi/airqualityforecast/0.1/documentation)
  and [index definitions](https://api.met.no/weatherapi/airqualityforecast/0.1/aqi_description).
- [Weather API terms](https://docs.api.met.no/doc/TermsOfService.html).
- [MET weather symbols](https://github.com/metno/weathericons/tree/main/weather).
