# BigDataCloud reverse geocoding for the widget location

This is the earlier baseline audit. The [district and widget mapping update](widget-mapping-implementation.md) records the fixes and expanded conversions implemented afterward.

Research date: 2026-10-01. Application baseline: `63c48f6edce188bf64a0df3f5b08dc237a07ec33` (version 1.1.1, version code 3).

## Endpoint and Android use

BigDataCloud's provider-owned Android client uses a keyless HTTPS GET request to:

```text
https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=<live latitude>&longitude=<live longitude>&localityLanguage=<language>
```

Its HTTP implementation supplies `latitude`, `longitude`, and `localityLanguage`; the Android README explicitly says no API key is required. The client uses Android's `HttpURLConnection`, so this application can reuse its existing bounded HTTP JSON transport instead of adding the Kotlin client or Google Play Services dependencies. [1], [2]

The provider's JavaScript client defaults `localityLanguage` to `en`, describes the language as BCP 47, and builds query values with URL encoding. The React README describes ISO 639-1 language values and gives `ja` as an example. Existing application language codes such as `en`, `ar`, and `pl` fit both descriptions. [3], [4], [5]

The free client endpoint is specifically for the calling device's current location. The Android and React READMEs require direct device requests, current live GPS/WiFi coordinates obtained through platform geolocation with user permission, and prohibit stored, cached, externally supplied coordinates and server-side automation. They document HTTP 402 and an IP ban for violations. The provider's JavaScript client requests a fresh position using `maximumAge: 0`. This is a real constraint on replacing the application's geocoder: a remembered location can remain useful for weather forecasting, but must not be submitted to this endpoint as a new reverse-geocoding lookup. [1], [4], [5]

For server-side or already-known coordinates, the provider directs users to its separate reverse-geocoding API with an API key. That endpoint is unnecessary for this device application when it requests a fresh position with the existing location permission. The official Android client also supports an IP fallback by omitting both coordinates, but IP estimation is less precise; the widget need not adopt this fallback to implement the requested GPS-based names. [1], [2]

## District and city fields

| Field | Provider evidence | Mapping recommendation |
| --- | --- | --- |
| `city` | React and JavaScript READMEs describe city/town; Android parser reads `city`. [2], [3], [5] | Use for the city component. |
| `locality` | React response schema describes a neighbourhood/suburb. [5] | Prefer for the district component when it is a nonblank string. |
| `localityName` | JavaScript README describes locality/suburb. Android model explicitly describes suburb or district and its parser reads this key. [2], [3], [6] | Accept as a compatibility fallback when `locality` is absent or blank. |
| `principalSubdivision` | Provider describes state/province. [3], [5] | Do not label it as a district or city. |
| `localityInfo.administrative` | React describes a country/state/city hierarchy; Android model exposes `name`, `description`, `adminLevel`, and `order`. [2], [5], [6] | Do not assume a fixed array position or admin level is the user's district. |

The provider-owned sources disagree on the raw locality key: the React schema uses `locality`, whereas the Android parser and JavaScript README use `localityName`. The JavaScript implementation returns `response.json()` directly without translating fields, so it does not resolve that documentation disagreement. Reading both keys, with `locality` preferred, covers the documented variants without copying the Android client's assumption that only `localityName` exists. [2], [3], [4], [5]

The requested widget name is `district, city`, using the nearest locality as the district component. This describes the provider's neighbourhood/suburb/district result; it does not guarantee a legal administrative district in every country. No examined official source establishes that a particular Jordanian administrative level or array index is an Amman neighbourhood. This research does not claim a live Jordan/Amman response. [5], [6]

Recommended application behavior, rather than a provider guarantee:

1. Trim names and accept strings only; do not display JSON `null`, numbers, or object values as names.
2. When district and city are distinct, emit exactly `district, city` with comma and one space.
3. When only one component exists, display that component without a dangling comma. Collapse equal names after trimming, preferably ignoring case.
4. When neither usable component exists, retain the application's friendly current-location fallback and continue fetching weather.
5. Format both localized and English names through the same rule before handing them to `WeatherMapping.toBydJson`, so the BYD payload's `city.name` and `englishCityName` both carry the requested structure when the service supplies both components.

Unit tests should use documented response fixtures and a fake HTTP transport, not automated queries to the free client endpoint from a cloud build machine. Any physical head-unit check should use that device's current position. This note was completed from provider-owned documentation and source; no successful live API probe is asserted. [1], [5]

## Sources

[1]: https://github.com/bigdatacloudapi/bigdatacloud-kotlin-client/blob/80342a1e9f25697dac397d4d7c3d44d689db9554/README.md
[2]: https://github.com/bigdatacloudapi/bigdatacloud-kotlin-client/blob/80342a1e9f25697dac397d4d7c3d44d689db9554/library/src/main/kotlin/com/bigdatacloud/api/ApiClient.kt
[3]: https://github.com/bigdatacloudapi/js-reverse-geocode-client/blob/9ab66c35f7914b9d9a54b3e57fd465916d45ec65/README.md
[4]: https://github.com/bigdatacloudapi/js-reverse-geocode-client/blob/9ab66c35f7914b9d9a54b3e57fd465916d45ec65/bigdatacloud_reverse_geocode.mjs
[5]: https://github.com/bigdatacloudapi/react-reverse-geocode-client/blob/a0a8e8dc9d03adfa5ca92c75bb8d76aec508e18b/README.md
[6]: https://github.com/bigdatacloudapi/bigdatacloud-kotlin-client/blob/80342a1e9f25697dac397d4d7c3d44d689db9554/library/src/main/kotlin/com/bigdatacloud/models/Models.kt

All sources are maintained by BigDataCloud. The fair-use statements above were verified in its official client READMEs, which link to the provider's [full policy](https://www.bigdatacloud.com/docs/article/fair-use-policy-for-free-client-side-reverse-geocoding-api); that separate webpage was not successfully retrieved in this environment.
