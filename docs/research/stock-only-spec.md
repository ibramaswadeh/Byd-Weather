# Stock-only BYD weather adapter

Latest user request supersedes the added-widget feature: remove the new widget and keep mappings to the firmware's stock widget; check every useful Open-Meteo value against attributes that can be set in the original firmware.

Remove the owned AppWidgetProvider, layout/art resources, pin button, in-app forecast preview, owned payload cache and fetch-success state. Keep the settings/service/native provider integration. Only verified stock-provider write/readback counts as a successful update and advances its saved timestamp/location; failure preserves native data and uses existing retry behavior. Retain optional AQ/geocoding failure tolerance, BigDataCloud cleaned district/city and all29 supported WMO mappings, day flags, source metadata, solar anchors, daily period selection and payload validation.

Native-declared fields and native-rendered fields are separate. Audit the primary firmware DTOs, renderer methods, labels/units and primary provider documentation. Populate all already-verified compatible native attributes; add a missing mapping only after proving semantics, units and aggregation. Remove the owned data.openMeteo extension and API parameters used only by it. Do not count ignored JSON as stock-widget functionality or fabricate unavailable warning/radar/lifestyle data, administrative identifiers or unverified conversions. Record every usable field and remaining unavailable/unverified source purpose in stock-widget-data-audit.md.

The native renderer's global hourly day/night behavior remains a documented limitation. It cannot be changed by supplying individual flags; no new renderer or firmware mutation is in scope.

Tests reuse public WeatherMapping.toBydJson/isComplete and the existing public runtime start/requestNow seam, with external HTTP/device adapters. Trace native DTO/icon methods. Review against approved pinned main d0b8668581339d74c62cf0bf74d563d91982656e, using this latest spec as the originating intent. Preserve release1.1.1/code3 and the supplied signing identity; CI test/lint/build, exact-source/artifact/signing verification and APK delivery finish the work.
