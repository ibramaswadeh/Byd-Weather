package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherMappingTest {
    @Test public void mapsPublishedSymbolsForEveryLightVariantToKnownFactoryConditions() throws Exception {
        JSONObject symbols = resource("met-symbol-contract.json").getJSONObject("symbols");
        JSONObject nativeCodes = FirmwareWeatherContract.descriptors().getJSONObject("conditions");
        Iterator<String> keys = symbols.keys();
        int count = 0;
        while (keys.hasNext()) {
            String symbol = keys.next();
            for (String suffix : new String[]{"", "_day", "_night", "_polartwilight"}) {
                MetCondition condition = MetCondition.parse(symbol + suffix);
                assertEquals(symbol, symbols.getInt(symbol), condition.nativeCode);
                assertTrue(nativeCodes.has(String.valueOf(condition.nativeCode)));
                assertEquals(symbol + suffix, condition.symbol);
                assertFalse(condition.description.isEmpty());
            }
            count++;
        }
        assertEquals(41, count);
        assertThrows(JSONException.class, () -> MetCondition.parse("unknown_day"));
        assertThrows(JSONException.class, () -> MetCondition.parse("hail"));
    }

    @Test public void preservesShowerIntensityAndThunderInSourceText() throws Exception {
        assertEquals(3, MetCondition.parse("heavyrainshowers_night").nativeCode);
        assertEquals("Heavy rain showers", MetCondition.parse("heavyrainshowers_night").description);
        assertEquals(13, MetCondition.parse("lightsnowshowers_day").nativeCode);
        assertEquals("Light snow showers with thunder", MetCondition.parse("lightssnowshowersandthunder_day").description);
        assertEquals(4, MetCondition.parse("heavysleetandthunder").nativeCode);
    }

    @Test public void realGlobalAndNordicForecastsSupplyTheDisplayedHoursAndSevenDays() throws Exception {
        for (String file : new String[]{"met-forecast-moscow.json", "met-forecast-norway.json"}) {
            JSONObject source = resource(file);
            MetForecast parsed = MetForecast.parse(source);
            long now = parsed.points.get(0).time + 30 * 60_000;
            JSONObject payload = new JSONObject(WeatherMapping.toBydJson(MetFixtures.load(source, now,
                    ZoneId.of("Europe/Moscow")), "City", "City"));
            assertTrue(file, WeatherMapping.isComplete(payload.toString()));
            JSONArray days = payload.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers");
            assertEquals(9, days.length());
            assertTrue(days.getJSONObject(0).getBoolean("timingOnly"));
            assertFalse(days.getJSONObject(0).has("mintemp"));
            assertEquals("2026-10-13", days.getJSONObject(2).getString("publictimeFmt"));
            assertEquals("2026-10-19", days.getJSONObject(8).getString("publictimeFmt"));
            assertTrue(payload.getJSONObject("data").getJSONObject("hourlys").getJSONArray("hourlyweathers").length() >= 8);
        }
    }

    @Test public void convertsWindUnitsAndLeavesUnsupportedReadingsUnavailable() throws Exception {
        JSONObject source = MetFixtures.forecast(MetFixtures.NOW, "cloudy", -2.5);
        JSONObject payload = new JSONObject(WeatherMapping.toBydJson(MetFixtures.load(source, MetFixtures.NOW,
                MetFixtures.ZONE), "City", "City"));
        JSONObject condition = payload.getJSONObject("data").getJSONObject("condition");
        assertEquals(16, condition.getInt("windspeed"));
        assertEquals("3", condition.getString("windlevel"));
        assertEquals("SW", condition.getString("winddir"));
        assertEquals(-1, condition.getInt("visibility"));
        assertEquals(2, condition.getInt("uVIndex"));
        assertFalse(condition.has("realfeel"));
        assertFalse(condition.has("windgustspeed"));
        assertEquals("Clear-sky UV", condition.getString("uvIndexDesc"));
        JSONObject aqi = payload.getJSONObject("data").getJSONObject("aqi");
        assertEquals(-1, aqi.getInt("aqivalue"));
        assertEquals("0", aqi.getString("lv"));
        assertEquals(-1, aqi.getInt("pm25"));
        assertEquals("--", aqi.getString("aqivaluetext"));
    }

    @Test public void norwegianIndexIsNeverRelabelledAsChineseAqi() throws Exception {
        long now = Instant.parse("2026-10-12T12:30:00Z").toEpochMilli();
        MetAirQuality air = MetAirQuality.parse(resource("met-air-quality-norway.json"), now);
        assertTrue(air.index >= 1 && air.index < 2);
        assertEquals("Low", air.description());
        MetWeatherClient.Result base = MetFixtures.load(MetFixtures.forecast(now, "cloudy", 10), now, MetFixtures.ZONE);
        JSONObject payload = new JSONObject(WeatherMapping.toBydJson(new MetWeatherClient.Result(base.forecast,
                base.daylight, air, base.zone, now, base.notices), "Oslo", "Oslo"));
        JSONObject aqi = payload.getJSONObject("data").getJSONObject("aqi");
        assertEquals(-1, aqi.getInt("aqivalue"));
        assertEquals("0", aqi.getString("lv"));
        assertEquals(air.index, aqi.getDouble("sourceAqi"), 0.001);
        assertEquals(Math.round(air.pm25), aqi.getInt("pm25"));
    }

    @Test public void dailyExtremaUseInstantPointsAndPeriodsWithinThatLocalDay() throws Exception {
        long now = Instant.parse("2026-10-12T00:00:00Z").toEpochMilli();
        JSONObject source = MetFixtures.forecast(now, "cloudy", 10);
        JSONArray points = source.getJSONObject("properties").getJSONArray("timeseries");
        points.getJSONObject(12).getJSONObject("data").put("next_6_hours", new JSONObject()
                .put("details", new JSONObject().put("air_temperature_min", 2).put("air_temperature_max", 18)));
        points.getJSONObject(23).getJSONObject("data").put("next_6_hours", new JSONObject()
                .put("details", new JSONObject().put("air_temperature_min", -20).put("air_temperature_max", 40)));
        MetForecast.Day day = MetForecast.parse(source).summarize(LocalDate.of(2026, 10, 12), ZoneId.of("UTC"));
        assertEquals(2, day.minimum);
        assertEquals(18, day.maximum);
    }

    @Test public void rejectsMissingOrStaleHoursAndUnknownUnitsInsteadOfFabricatingWeather() throws Exception {
        JSONObject source = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 10);
        assertThrows(JSONException.class, () -> MetForecast.parse(source).upcomingHours(MetFixtures.NOW + 20 * 86400_000L));
        JSONArray points = source.getJSONObject("properties").getJSONArray("timeseries");
        points.remove(3);
        assertThrows(JSONException.class, () -> MetForecast.parse(source).upcomingHours(MetFixtures.NOW));
        source.getJSONObject("properties").getJSONObject("meta").getJSONObject("units").put("wind_speed", "km/h");
        assertThrows(JSONException.class, () -> MetForecast.parse(source));
    }

    @Test public void rejectsMissingCoreReadingsAndIncompleteWidgetPayloads() throws Exception {
        JSONObject source = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 10);
        source.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
                .getJSONObject("instant").getJSONObject("details").put("air_temperature", JSONObject.NULL);
        assertThrows(JSONException.class, () -> MetForecast.parse(source));
        JSONObject payload = MetFixtures.payload(MetFixtures.NOW, "cloudy");
        payload.getJSONObject("data").getJSONObject("condition").put("temperature", "unknown");
        assertFalse(WeatherMapping.isComplete(payload.toString()));
        payload = MetFixtures.payload(MetFixtures.NOW, "cloudy");
        payload.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers").remove(8);
        assertFalse(WeatherMapping.isComplete(payload.toString()));
    }

    @Test public void parsesRealSolarEventsWithOffsets() throws Exception {
        SunTimes sun = SunTimes.parse(resource("met-sun-moscow.json"), LocalDate.of(2026, 10, 12), ZoneId.of("Europe/Moscow"));
        assertTrue(sun.sunrise < sun.sunset);
        assertTrue(sun.isDay(sun.sunrise + 1000));
        assertFalse(sun.isDay(sun.sunset + 1000));
        assertEquals(sun.sunrise.longValue(), sun.nativeAnchor());
    }

    @Test public void polarTransitionDatesSupportJustOneSolarEvent() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 12);
        JSONObject riseOnly = MetFixtures.sun(date, MetFixtures.ZONE, null);
        riseOnly.getJSONObject("properties").getJSONObject("sunset").put("time", JSONObject.NULL);
        riseOnly.getJSONObject("properties").getJSONObject("solarnoon").put("visible", false);
        SunTimes rising = SunTimes.parse(riseOnly, date, MetFixtures.ZONE);
        assertFalse(rising.isDay(rising.sunrise - 1000));
        assertTrue(rising.isDay(rising.sunrise + 1000));
        JSONObject setOnly = MetFixtures.sun(date, MetFixtures.ZONE, null);
        setOnly.getJSONObject("properties").getJSONObject("sunrise").put("time", JSONObject.NULL);
        setOnly.getJSONObject("properties").getJSONObject("solarnoon").put("visible", false);
        SunTimes setting = SunTimes.parse(setOnly, date, MetFixtures.ZONE);
        assertTrue(setting.isDay(setting.sunset - 1000));
        assertFalse(setting.isDay(setting.sunset + 1000));
    }

    static JSONObject resource(String name) throws Exception {
        try (InputStream input = WeatherMappingTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(name, input);
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
