# Third-party notices

- Weather adapter (`WeatherRuntime.java`, `WeatherMapping.java`): ported from
  [BYD Extend](https://github.com/sunlixWhyNotAvailable/byd-turnsignal-cameraview), copyright 2026
  sunlixWhyNotAvailable, AGPL-3.0-only. This repository is AGPL-3.0-only;
  the complete license is in `LICENSE`.
- Launcher, themed and notification icons: original sun/cloud vector artwork
  created for BYD Weather, included under this repository's AGPL-3.0-only license.
- Weather data: [MET Norway](https://www.met.no/en), under
  [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).
  Attribution is displayed in the app and stored in the BYD weather payload. The app
  derives daily summaries, converts units, and maps provider symbols to BYD categories.
  The MET JSON test fixtures are sample responses captured on 2026-10-12 for public
  example coordinates in Moscow and Norway; the air-quality fixture is shortened.
- The MET symbol contract records public identifier names from
  [metno/weathericons](https://github.com/metno/weathericons). No MET/Yr artwork is
  bundled in the app; the factory widget uses its installed resources.
