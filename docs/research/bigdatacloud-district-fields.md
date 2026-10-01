# BigDataCloud district extraction for the BYD widget

Research date: 2026-10-01. Scope: the reported city-only widget name in the version 1.1.1 BigDataCloud build. This note supplements [the endpoint and device-use research](bigdatacloud-geocoding.md).

## What the previous mapping missed

The mapper used only top-level `locality`, its documented `localityName` compatibility variant, and `city`. If the locality and city were equal, it deliberately collapsed them to one name. It did not inspect `localityInfo.administrative`, so a district supplied there could never reach `data.city.name` or `data.city.englishCityName`.

This explains a reproducible city-only output when the top-level locality repeats the city and the hierarchy contains a finer area. The user's response has not been captured, so this is a demonstrated missing code path, rather than proof of the precise response received by their head unit. A provider response without any finer name must still display only its available city.

## Verified provider semantics

BigDataCloud's first-party .NET response model documents the following meanings. These comments describe the reverse-geocoding response shared by the provider's clients; the Android model independently exposes the same hierarchy fields. [1], [2]

| Field | Provider definition | Consequence |
| --- | --- | --- |
| `city` | The most significant populated place, likely the city name in the requested language. | Treat it as the city component, rather than selecting an arbitrary administrative level as a replacement. |
| `locality` | The most granular named locality: a suburb, village, or town containing the coordinates. | Prefer a distinct locality for the requested district component; it is not a guarantee of a legal district. |
| `localityName` | The Android model documents its locality as a suburb or district, mapped from this JSON key. | Keep the existing compatibility fallback. |
| `localityInfo.administrative` | Country, state, county, city, suburb, etc., from broadest to most specific. | It can contain useful areas absent from the top-level names. |
| `order` | Sort order within the hierarchy; lower means broader geographic scope. | Higher numeric order identifies the more specific hierarchy entry. A city anchor allows selecting entries below that city. |
| `adminLevel` | OpenStreetMap administrative level; examples include 2 for country, 4 for state/province, and 8 for city. | These are examples, not a universal country-independent district level. |
| `description` | A short description of the place type, with examples such as "state of Australia" and "local government area". | It can support a conservative place-type fallback; no closed enum or guaranteed description language is documented. |
| `name` | The place name in the requested language. | Preserve the supplied localized name. |
| `isoName` | The ISO standard English name, if available. | It is an optional alias, not a substitute for a localized display name. |
| `localityInfo.informative` | Additional layers such as islands, historical regions, and geographic features. | Do not choose these automatically as a district. |

The React schema also describes `locality` as a neighbourhood/suburb and the administrative list as a country-to-city hierarchy. [3] The .NET source provides the explicit ordering definition missing from the earlier Android-only research. [1]

No examined first-party source contains an Amman response or establishes a fixed Jordanian district admin level. A neighbourhood and a legal administrative district are not interchangeable everywhere. The requested widget label can use the provider's nearest named area as its district component, but it cannot manufacture a missing neighbourhood.

## Recommended selection rule

The following is an application algorithm derived from the documented hierarchy, rather than an additional provider guarantee:

1. Accept nonblank string names only, trim surrounding whitespace, and compare names without case distinctions. Read `city`; prefer the documented top-level locality keys when they supply a distinct name.
2. If the locality is absent or repeats the city, inspect administrative entries. Find an entry whose `name` matches the supplied city; use its valid numeric `order` as the city boundary. Match an optional `isoName` only when it actually equals the supplied city, not to invent cross-language equivalence.
3. Among distinct named administrative entries strictly more specific than the city boundary, choose the greatest valid numeric `order`. Exclude obvious country/state/province/region/governorate entries as a defensive check. Do not infer the boundary from a universal admin level or a fixed array index.
4. When no matching city boundary exists, use a conservative place-type fallback: distinct entries explicitly described as district, suburb, neighbourhood, or neighborhood, excluding descriptions that identify a broader county/province/state/governorate or country. If multiple acceptable entries have usable orders, prefer the greatest order. An untyped or ambiguously described entry is insufficient evidence that it belongs below the city.
5. Emit `district, city` only when both components exist and differ. Otherwise emit the usable component; retain the friendly location fallback when neither exists. Feed the resulting name through both widget city-name fields, as in the existing mapping.

Numeric `order` is supported by the first-party definition. Missing or malformed order values do not justify inventing an order. If an implementation accepts descriptions without a city anchor, it should document that these descriptions are a heuristic and keep its allowed types narrow. A response can legitimately lack district detail; further precision then requires more data from the provider or a different geocoder.

## Regression fixture and verification limit

This fixture uses provider-documented keys and demonstrates the missing branch. It is deliberately constructed, and is **not** a captured Amman API response:

```json
{
  "city": "Amman",
  "locality": "Amman",
  "localityInfo": {
    "administrative": [
      {"name": "Jordan", "description": "country", "order": 1},
      {"name": "Amman", "description": "city", "order": 3},
      {"name": "Al Jubeiha", "description": "district", "order": 4}
    ]
  }
}
```

The expected application name is `Al Jubeiha, Amman`. Tests should also cover a coarser district preceding the city, multiple finer entries, a distinct top-level locality, city duplicates, missing orders, and malformed names. A country-level district should not win merely because its description contains the word "district".

Research and automated tests must not call the free client endpoint from a cloud machine with arbitrary coordinates. The provider requires requests directly from the device being located using fresh device coordinates. [3] No successful live request or user-coordinate response is claimed in this note. Device verification can capture the permitted live response and compare its hierarchy to the displayed label.

## Sources

[1]: https://github.com/bigdatacloudapi/bigdatacloud-dotnet/blob/4fd1a7832ecbcb0575f6cab198e86d81f74c8200/src/BigDataCloud/Models/ReverseGeocoding.cs
[2]: https://github.com/bigdatacloudapi/bigdatacloud-kotlin-client/blob/80342a1e9f25697dac397d4d7c3d44d689db9554/library/src/main/kotlin/com/bigdatacloud/models/Models.kt
[3]: https://github.com/bigdatacloudapi/react-reverse-geocode-client/blob/a0a8e8dc9d03adfa5ca92c75bb8d76aec508e18b/README.md
