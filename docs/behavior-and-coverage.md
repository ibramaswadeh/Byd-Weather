# Update behavior and weather coverage

See the [README](../README.md) for downloads, setup, and troubleshooting.

## How updates work

The service waits for usable internet, then requests a location for up to
60 seconds. It first subscribes to Android GPS updates and
optional NMEA messages. If GPS has no fix after ten seconds, it also requests
`LocationManager.NETWORK_PROVIDER`. If GPS is off or precise permission is not
granted, the network request starts immediately. The first usable fix completes
the lookup; all
listeners and fallback timers are then released.

GPS fixes must be no more than ten seconds old. NMEA requires a valid checksum,
fix status, and UTC fix time. Network fixes must be no more than 60 seconds old,
have valid coordinates and a reported accuracy of 25 km or better, and must not
be marked as mock or GPS-derived. The app uses the provider's monotonic timestamp
to calculate the remaining lifetime and never queries last-known locations.
The validation and cleanup rules are recorded in
[the location contract](gps-location-contract.json).

Wi-Fi/cell availability depends on the head unit's installed and enabled network
provider, internet access, and regional radio coverage. The status shows **Wi-Fi**
or **Cell network** when the provider reports the radio source, and **Network
provider** when it does not. OEM providers can reuse positions and rewrite fix
timestamps; the Android callback alone cannot prove an independent, freshly
measured radio fix. The app cannot detect every plausible but incorrect GPS fix.

City names use Android's built-in `Geocoder`.
The app displays the returned city, or its sub-administrative/admin area when
the city is unavailable. If geocoding is unavailable or fails, it displays
**Current location** and continues the weather update. Availability and naming
depend on the head unit's geocoding service. A successful update reports when
the city name or optional air-quality data could not be obtained.

After fetching weather, the app writes it to the BYD provider, reads it back,
and requests a widget refresh. A successful update advances the saved timestamp.
The app updates and verifies the provider's first row, matching the stock widget's
query, even when older integrations have left multiple rows.

In DiLink 3.0 firmware (`18.1.2.2601230.1`), WeatherData
`2.9.5.250616` selects both the widget label and artwork from `cnweatherid`.
It ignores the payload's `weathertext` and current day/night flag. Clear weather
uses native ID `0`, whose stock English label is **Sunny** during both day and
night. Night artwork is selected separately using the refresh timestamp,
sunset, and next sunrise. Daily records use sunrise anchors so this also works
before dawn. Changing the payload text cannot change that stock English label.

The night-artwork switch applies only to native ID `0`. Native ID `1`
(**Cloudy**) always uses the same sun-behind-cloud card, including at night.
Partly cloudy weather can therefore refresh successfully and still show a sun
at night. Selecting a moon-and-cloud card requires a change to the stock
WeatherData widget renderer or resources; the provider payload cannot select
that artwork.

The firmware's available categories are coarser than MET's descriptions:
mainly clear and partly cloudy both display **Cloudy**, overcast displays
**Dreary**, and all rain showers share the native shower category. Light and
heavy rain share rain artwork; snow intensities similarly share snow artwork.
The stock hourly list uses the current refresh's night state for every
clear-weather icon, rather than each hour's day/night flag. Daily icons use
`conditionDay` and that same current night state, ignoring `conditionNight`.
Those rendering choices belong to WeatherData and cannot be overridden with
different payload text.

This firmware redraws through its running weather service's observer of
`time_12_24`; BYD Weather notifies that observer after verifying the committed
data. A recent successful update confirms the provider write and readback;
it does not acknowledge rendering on the head unit. The firmware contract,
artifact hashes, and REA evidence IDs are retained in
[the test fixture](../app/src/test/resources/byd-weatherdata-2.9.5-contract.json).

Location and weather-fetch failures keep the previous weather data. Failed updates
leave the saved success timestamp unchanged. Failed provider writes attempt
to restore the previous data.

Failures retry after five minutes when prerequisites remain available. Otherwise,
the app waits for location or network recovery; revoked location permission must be
restored before updates can resume. An air-quality fetch failure does not block
the weather update.

## Weather coverage and mapping

All weather requests use `https://api.met.no/weatherapi/`:

| Product | Use |
| --- | --- |
| `locationforecast/2.0/complete` | Global temperature, conditions, wind, humidity, pressure, and forecasts. |
| `sunrise/3.0/sun` | Sunrise and sunset for yesterday, today, and seven future days. |
| `airqualityforecast/0.1/` | Optional Norwegian air-quality forecast where coverage is expected. |

No API key is needed. Requests identify the application and public project URL,
limit forecast coordinates to four decimal places, support gzip and redirects,
and use disk caching with `Expires`/`Cache-Control` and `If-Modified-Since`.
`Retry-After` is respected for rate limits and temporary service failures.
Solar coordinates use two decimal places to reduce duplicate requests from GPS drift.
Caches remain in private app storage and can be cleared through Android app settings.

The native hourly strip needs the current forecast hour plus seven subsequent
hours. The daily strip displays tomorrow through day seven. MET's later forecast
points are typically six hours apart; the app summarizes available points and
six-hour temperature extrema contained within each local date. The day's most
significant available condition supplies its native icon. Earlier forecasts from
the same location are retained for today's range, but the first update of a day
may only describe its remaining hours. These are forecast summaries, not measured
24-hour extrema. Calendar dates and labels follow the car's configured time zone.

Missing source data is kept explicit:

- **Visibility:** Locationforecast does not provide it. The integer-only stock
  field receives `-1`; the factory detail screen may display that number. The
  main launcher card does not use visibility.
- **UV:** MET supplies a clear-sky forecast, not cloud-adjusted exposure. An absent
  UV reading is unavailable. The app's data coverage panel explains this distinction.
- **Air quality:** unavailable outside Norwegian coverage. In Norway the app shows
  the Norwegian health-risk category and index, and retains PM2.5/PM10 where supplied.
  The stock category remains unavailable (`lv=0`): its Chinese AQI labels cannot
  be populated by renaming Norway's different index.
- **Other values:** feels-like temperature, gusts, and precipitation probability
  are included only where supplied. Hail, dust, and other unsupported conditions
  are not inferred. The app preserves MET shower/intensity text, although the
  stock renderer uses its own coarser categories.
- **Polar dates:** absent sunrise or sunset events remain absent. Timing sentinels
  preserve the factory night comparison without inserting imaginary hourly events.

A forecast or required astronomy failure preserves the previous widget data.
Optional air-quality failure reports a notice and allows the weather update.
See [the MET integration contract](met-weather-contract.md) for mapping and
source references. MET's [terms of service](https://docs.api.met.no/doc/TermsOfService.html)
apply; deployments approaching its aggregate request limits need coordinated caching
or an agreement with the provider.
