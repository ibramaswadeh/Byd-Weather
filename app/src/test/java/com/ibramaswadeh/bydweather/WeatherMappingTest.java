package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.*;

/** Tests the Open-Meteo to BYD payload contract through the public mapper. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherMappingTest {
    private static final long NOW_MS = 1_759_320_000_000L;

    @Test public void anchorsNativeDailyRecordsAtSunriseForPreDawnNightSelection() throws Exception {
        JSONObject source = forecast();
        source.getJSONObject("current").put("time", "2025-10-01T02:00").put("is_day", 0);
        JSONObject hourly = source.getJSONObject("hourly");
        for (int i = 0; i < 8; i++) {
            hourly.getJSONArray("time").put(i, LocalDateTime.of(2025, 10, 1, 2, 0)
                    .plusHours(i).toString());
            hourly.getJSONArray("is_day").put(i, i < 4 ? 0 : 1);
        }
        JSONObject result = payload(source);
        JSONArray days = result.getJSONObject("data").getJSONObject("dailys")
                .getJSONArray("dailyweathers");
        assertEquals(Instant.parse("2025-09-30T03:00:00Z").toEpochMilli(),
                days.getJSONObject(0).getLong("publictime"));
        assertEquals(Instant.parse("2025-10-01T03:00:00Z").toEpochMilli(),
                days.getJSONObject(1).getLong("publictime"));
        assertEquals("2025-09-30", days.getJSONObject(0).getString("publictimeFmt"));
        assertEquals("2025-10-01", days.getJSONObject(1).getString("publictimeFmt"));
        assertEquals("2025-10-01T00:00:00+03:00", days.getJSONObject(1).getString("moonSetFmt"));
        assertEquals(Boolean.FALSE, result.getJSONObject("data").getJSONObject("hourlys")
                .getJSONArray("hourlyweathers").getJSONObject(1).get("isdaynight"));
        assertTrue(WeatherMapping.isComplete(result.toString()));
    }

    @Test public void removesSubDistrictSuffixFromWidgetLocation() throws Exception {
        String[] inputs = {
                "{\"city\":\"Amman\",\"locality\":\"Al Jubeiha Sub-District\"}",
                "{\"city\":\"Amman\",\"localityName\":\"Al Jubeiha sub district\"}",
                "{\"city\":\"Amman\",\"localityInfo\":{\"administrative\":[{\"name\":\"Al Jubeiha Subdistrict\",\"description\":\"sub-district\",\"order\":4}]}}"
        };
        for (String input : inputs) {
            JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                    new JSONObject(input), "Current location", NOW_MS));
            JSONObject city = result.getJSONObject("data").getJSONObject("city");
            assertEquals("Al Jubeiha, Amman", city.getString("name"));
            assertEquals("Al Jubeiha, Amman", city.getString("englishCityName"));
        }
    }

    @Test public void providesNumericDailyUvTextWithoutGuessingDescriptions() throws Exception {
        JSONObject data = payload(forecast()).getJSONObject("data");
        JSONObject day = data.getJSONObject("dailys").getJSONArray("dailyweathers").getJSONObject(1);
        assertEquals("5", day.get("uvIndexText"));
        assertEquals(5, day.getInt("uvIndex"));
        assertFalse(data.getJSONObject("condition").has("uvIndexDesc"));
    }

    @Test public void refusesBroadOrUntypedDistrictFallbackWithoutCityAnchor() throws Exception {
        JSONObject location = new JSONObject("""
                {"city":"Amman","locality":"Amman","localityInfo":{"administrative":[
                  {"name":"Amman District","description":"county district","order":5},
                  {"name":"Jubeiha Park","description":"award-winning park","order":6}]}}
                """);
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Amman", result.getJSONObject("data").getJSONObject("city").getString("name"));
        location.getJSONObject("localityInfo").getJSONArray("administrative")
                .put(new JSONObject().put("name", "Al Jubeiha").put("description", "urban ward"));
        result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Al Jubeiha, Amman",
                result.getJSONObject("data").getJSONObject("city").getString("name"));
    }

    @Test public void rejectsMissingOrConflictingHourlyDayFlagsBeforeProviderWrite() throws Exception {
        for (String key : new String[]{"isdaynight", "Isdaynight"}) {
            for (Object invalid : new Object[]{null, JSONObject.NULL, 1, "true", false}) {
                JSONObject payload = payload(forecast());
                JSONArray hours = payload.getJSONObject("data").getJSONObject("hourlys")
                        .getJSONArray("hourlyweathers");
                JSONObject extra = new JSONObject(hours.getJSONObject(0).toString());
                if (invalid == null) extra.remove(key);
                else extra.put(key, invalid);
                hours.put(extra);
                assertFalse(WeatherMapping.isComplete(payload.toString()));
            }
        }
    }

    @Test public void preservesLocationMetadataWithoutChangingDistrictLabel() throws Exception {
        JSONObject location = new JSONObject().put("city", "Amman").put("locality", "Al Jubeiha")
                .put("countryName", "Jordan").put("countryCode", "JO")
                .put("principalSubdivision", "Amman Governorate");
        JSONObject data = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS)).getJSONObject("data");
        JSONObject city = data.getJSONObject("city");
        assertEquals("Al Jubeiha, Amman", city.getString("name"));
        assertEquals("Jordan", city.getString("countryname"));
        assertEquals("JO", city.getString("countryCode"));
        assertEquals("Amman Governorate", city.getString("provincename"));
        assertEquals("Amman", city.getString("parentcity"));
        assertEquals("Asia/Amman", city.getString("timezone"));
    }

    @Test public void mapsWorstAvailableDailyEuropeanAqiInForecastZone() throws Exception {
        JSONObject air = new JSONObject().put("timezone", "UTC")
                .put("current", new JSONObject().put("european_aqi", 35))
                .put("hourly", new JSONObject()
                        .put("time", new JSONArray().put("2025-09-30T19:00")
                                .put("2025-09-30T22:00").put("2025-10-01T10:00").put("2025-10-01T21:00"))
                        .put("european_aqi", new JSONArray().put(20).put(70).put(50).put(JSONObject.NULL)));
        JSONObject data = new JSONObject(WeatherMapping.toBydJson(forecast(), air,
                "Amman", "Amman", NOW_MS)).getJSONObject("data");
        JSONArray days = data.getJSONObject("dailys").getJSONArray("dailyweathers");
        assertEquals(20, days.getJSONObject(0).getInt("aqivalue"));
        assertEquals(70, days.getJSONObject(1).getInt("aqivalue"));
        assertEquals(4, days.getJSONObject(1).getInt("lv"));
        assertEquals(-1, days.getJSONObject(2).getInt("aqivalue"));
        assertEquals("--", days.getJSONObject(2).getString("aqivaluetext"));
        assertEquals(35, data.getJSONObject("aqi").getInt("aqivalue"));
        air.getJSONObject("hourly").getJSONArray("european_aqi").put(1, JSONObject.NULL).put(2, -1);
        data = new JSONObject(WeatherMapping.toBydJson(forecast(), air,
                "Amman", "Amman", NOW_MS)).getJSONObject("data");
        assertEquals(35, data.getJSONObject("dailys").getJSONArray("dailyweathers")
                .getJSONObject(1).getInt("aqivalue"));
    }

    @Test public void separatesDailyDayAndNightUsingMatchingHourlySamples() throws Exception {
        JSONObject forecast = forecast();
        forecast.getJSONObject("daily").getJSONArray("weather_code").put(1, 95);
        JSONObject hourly = forecast.getJSONObject("hourly");
        hourly.getJSONArray("weather_code").put(6, 61).put(7, 45);
        hourly.getJSONArray("wind_speed_10m").put(6, 25);
        hourly.getJSONArray("wind_direction_10m").put(6, 180);
        for (String key : new String[]{"relative_humidity_2m", "cloud_cover", "precipitation",
                "rain", "showers", "wind_gusts_10m"}) {
            hourly.put(key, new JSONArray());
        }
        hourly.getJSONArray("relative_humidity_2m").put(6, 80);
        hourly.getJSONArray("cloud_cover").put(6, 90);
        hourly.getJSONArray("precipitation_probability").put(6, 75);
        hourly.getJSONArray("precipitation").put(6, 2.5);
        hourly.getJSONArray("rain").put(6, 1.2);
        hourly.getJSONArray("showers").put(6, 0.8);
        hourly.getJSONArray("wind_gusts_10m").put(6, 65);
        JSONArray days = payload(forecast).getJSONObject("data")
                .getJSONObject("dailys").getJSONArray("dailyweathers");
        JSONObject today = days.getJSONObject(1);
        JSONObject day = today.getJSONObject("conditionDay");
        JSONObject night = today.getJSONObject("conditionNight");
        assertEquals(0, day.getInt("cnweatherid"));
        assertEquals(7, night.getInt("cnweatherid"));
        assertEquals(4, night.getInt("windlevel"));
        assertEquals("S", night.getString("winddir"));
        assertEquals(80, night.getInt("humidity"));
        assertEquals("90", night.get("cloudCover"));
        assertEquals(75, night.getInt("precProb"));
        assertEquals("2.0", night.get("rain"));
        assertEquals("2.5", night.get("totalLiquid"));
        assertEquals("8", night.get("windGustPow"));
        assertFalse(day.has("humidity"));
        assertFalse(night.has("rainProb"));
        assertEquals(0, days.getJSONObject(0).getJSONObject("conditionNight").getInt("cnweatherid"));
    }

    @Test public void mapsOptionalDailyMetricsAndRealLunarEventsWithDateFallback() throws Exception {
        JSONObject forecast = forecast();
        JSONObject daily = forecast.getJSONObject("daily");
        for (String key : new String[]{"apparent_temperature_max", "apparent_temperature_min",
                "pressure_msl_mean", "visibility_min", "moonrise", "moonset"}) {
            daily.put(key, new JSONArray().put(JSONObject.NULL));
        }
        daily.getJSONArray("apparent_temperature_max").put(1, 28.6);
        daily.getJSONArray("apparent_temperature_min").put(1, 16.2);
        daily.getJSONArray("pressure_msl_mean").put(1, 1015.7);
        daily.getJSONArray("visibility_min").put(1, 15500);
        daily.getJSONArray("moonrise").put(1, "2025-10-01T21:31");
        daily.getJSONArray("moonset").put(1, "2025-10-01T11:34");
        JSONArray result = payload(forecast).getJSONObject("data")
                .getJSONObject("dailys").getJSONArray("dailyweathers");
        JSONObject today = result.getJSONObject(1);
        assertEquals("29", today.get("realFeelTempMax"));
        assertEquals("16", today.get("realFeelTempMin"));
        assertEquals(1016, today.getInt("pressure"));
        assertEquals(16, today.getInt("visibility"));
        assertEquals(Instant.parse("2025-10-01T18:31:00Z").toEpochMilli(), today.getLong("moonRise"));
        assertEquals(Instant.parse("2025-10-01T08:34:00Z").toEpochMilli(), today.getLong("moonSet"));
        assertEquals("2025-10-01T11:34:00+03:00", today.getString("moonSetFmt"));
        JSONObject yesterday = result.getJSONObject(0);
        assertEquals("2025-09-30T00:00:00+03:00", yesterday.getString("moonSetFmt"));
        assertFalse(yesterday.has("moonSet"));
        assertFalse(yesterday.has("moonRise"));
        assertFalse(yesterday.has("realFeelTempMax"));
        assertFalse(today.has("moonphase"));
        assertTrue(WeatherMapping.isComplete(payload(forecast).toString()));
    }

    @Test public void convertsWindAndActualGustSpeedToBeaufortForce() throws Exception {
        JSONObject forecast = forecast();
        forecast.getJSONObject("current").put("wind_speed_10m", 25).put("wind_gusts_10m", 65);
        JSONObject condition = payload(forecast).getJSONObject("data").getJSONObject("condition");
        assertEquals(25, condition.getInt("windspeed"));
        assertEquals(4, condition.getInt("windlevel"));
        assertEquals(8, condition.getInt("windgustlevel"));
        for (int[] example : new int[][]{{0,0},{1,0},{2,1},{5,1},{6,2},{19,3},{20,4},
                {28,4},{29,5},{49,6},{50,7},{74,8},{75,9},{102,10},{103,11},{117,11},{118,12}}) {
            forecast.getJSONObject("current").put("wind_speed_10m", example[0]);
            assertEquals(example[1], payload(forecast).getJSONObject("data")
                    .getJSONObject("condition").getInt("windlevel"));
        }
    }

    @Test public void mapsHourlyRainfallAndBothDayFlagSpellings() throws Exception {
        JSONObject forecast = forecast();
        forecast.getJSONObject("hourly").put("precipitation", new JSONArray()
                .put(1.25).put(JSONObject.NULL));
        JSONArray result = payload(forecast).getJSONObject("data")
                .getJSONObject("hourlys").getJSONArray("hourlyweathers");
        assertEquals(1.25, result.getJSONObject(0).getDouble("precipitation"), 0.001);
        assertEquals(Boolean.TRUE, result.getJSONObject(0).get("Isdaynight"));
        assertEquals(Boolean.FALSE, result.getJSONObject(7).get("Isdaynight"));
        assertFalse(result.getJSONObject(1).has("precipitation"));
        assertTrue(WeatherMapping.isComplete(payload(forecast).toString()));
        forecast.getJSONObject("hourly").getJSONArray("is_day").put(0, 0.5);
        assertThrows(org.json.JSONException.class, () -> payload(forecast));
    }

    @Test public void startsHourlyForecastAtCurrentHourAndKeepsUpTo48Hours() throws Exception {
        JSONObject forecast = forecast();
        JSONObject hourly = forecast.getJSONObject("hourly");
        for (String key : new String[]{"time", "temperature_2m", "weather_code",
                "precipitation_probability", "wind_speed_10m", "wind_direction_10m", "is_day"}) {
            hourly.put(key, new JSONArray());
        }
        for (int i = 0; i < 100; i++) {
            hourly.getJSONArray("time").put(LocalDateTime.of(2025, 9, 30, 0, 0).plusHours(i).toString());
            hourly.getJSONArray("temperature_2m").put(i);
            hourly.getJSONArray("weather_code").put(0);
            hourly.getJSONArray("precipitation_probability").put(0);
            hourly.getJSONArray("wind_speed_10m").put(10);
            hourly.getJSONArray("wind_direction_10m").put(90);
            hourly.getJSONArray("is_day").put(1);
        }
        forecast.getJSONObject("current").put("time", "2025-10-01T12:15");
        JSONArray result = payload(forecast).getJSONObject("data")
                .getJSONObject("hourlys").getJSONArray("hourlyweathers");
        assertEquals(48, result.length());
        assertEquals(36, result.getJSONObject(0).getInt("temp"));
        assertEquals(Instant.parse("2025-10-01T09:00:00Z").toEpochMilli(),
                result.getJSONObject(0).getLong("date"));
        assertEquals(83, result.getJSONObject(47).getInt("temp"));
    }

    @Test public void triesDistinctCompatibilityLocalityBeforeHierarchy() throws Exception {
        JSONObject location = new JSONObject().put("city", "Amman").put("locality", "Amman")
                .put("localityName", "Al Jubeiha");
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Al Jubeiha, Amman",
                result.getJSONObject("data").getJSONObject("city").getString("name"));
    }

    @Test public void selectsMostSpecificAdministrativeAreaBelowCity() throws Exception {
        JSONObject location = new JSONObject("""
                {"city":"Amman","locality":"Amman","countryName":"Jordan",
                 "principalSubdivision":"Amman Governorate","localityInfo":{"administrative":[
                   {"name":"Al Jubeiha","description":"residential area","order":6},
                   {"name":"Amman District","description":"district","order":3},
                   {"name":"Jordan","description":"country","order":1},
                   {"name":"Amman","description":"city","order":4},
                   {"name":"University District","description":"district","order":5}]}}
                """);
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Al Jubeiha, Amman",
                result.getJSONObject("data").getJSONObject("city").getString("name"));
    }

    @Test public void parsesAndFormatsForecastTimesInResponseZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            JSONObject data = payload(forecast()).getJSONObject("data");
            assertEquals(Instant.parse("2025-10-01T09:00:00Z").toEpochMilli(),
                    data.getJSONObject("condition").getLong("updatetime"));
            assertEquals("2025-10-01 12:00", data.getJSONObject("condition").getString("updatetimeFmt"));
            assertEquals(Instant.parse("2025-09-30T03:00:00Z").toEpochMilli(),
                    data.getJSONObject("dailys").getJSONArray("dailyweathers")
                            .getJSONObject(0).getLong("publictime"));
            assertEquals(Instant.parse("2025-09-30T03:00:00Z").toEpochMilli(),
                    data.getJSONObject("dailys").getJSONArray("dailyweathers")
                            .getJSONObject(0).getLong("sunRise"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void usesAdministrativeDistrictWhenLocalityRepeatsCity() throws Exception {
        JSONObject location = new JSONObject()
                .put("city", "Amman").put("locality", "Amman")
                .put("localityInfo", new JSONObject().put("administrative", new JSONArray()
                        .put(new JSONObject().put("name", "Al Jubeiha")
                                .put("description", "district").put("order", 4))));
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Al Jubeiha, Amman",
                result.getJSONObject("data").getJSONObject("city").getString("name"));
    }

    @Test public void passesBigDataCloudDistrictAndCityToWidgetName() throws Exception {
        JSONObject location = new JSONObject().put("locality", "Al Jubeiha")
                .put("city", "Amman");
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        JSONObject city = result.getJSONObject("data").getJSONObject("city");
        assertEquals("Al Jubeiha, Amman", city.getString("name"));
        assertEquals("Al Jubeiha, Amman", city.getString("englishCityName"));
        assertTrue(WeatherMapping.isComplete(result.toString()));
    }

    @Test public void acceptsBigDataCloudLocalityNameVariant() throws Exception {
        JSONObject location = new JSONObject().put("locality", " ")
                .put("localityName", " Al Jubeiha ").put("city", " Amman ");
        JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                location, "Current location", NOW_MS));
        assertEquals("Al Jubeiha, Amman",
                result.getJSONObject("data").getJSONObject("city").getString("name"));
    }

    @Test public void avoidsEmptyOrRepeatedLocationLabels() throws Exception {
        String[][] cases = {
                {"{\"city\":\"Amman\"}", "Amman"},
                {"{\"locality\":\"Al Jubeiha\"}", "Al Jubeiha"},
                {"{\"locality\":\" amman \",\"city\":\"Amman\"}", "Amman"},
                {"{\"locality\":\"Al Jubeiha\",\"localityName\":\"Other\",\"city\":\"Amman\"}", "Al Jubeiha, Amman"},
                {"{\"locality\":null,\"city\":\" \"}", "Current location"},
                {"{\"locality\":42,\"city\":{},\"principalSubdivision\":\"Amman Governorate\"}", "Current location"},
                {"{}", "Current location"},
                {null, "Current location"}
        };
        for (String[] example : cases) {
            JSONObject location = example[0] == null ? null : new JSONObject(example[0]);
            JSONObject result = new JSONObject(WeatherMapping.toBydJson(forecast(), null,
                    location, "Current location", NOW_MS));
            JSONObject city = result.getJSONObject("data").getJSONObject("city");
            assertEquals(example[1], city.getString("name"));
            assertEquals(example[1], city.getString("englishCityName"));
            assertTrue(WeatherMapping.isComplete(result.toString()));
        }
    }

    @Test public void passesCurrentDayAndNightAsBooleanFlags() throws Exception {
        JSONObject forecast = forecast();
        JSONObject daytime = payload(forecast).getJSONObject("data").getJSONObject("condition");
        assertEquals(Boolean.TRUE, daytime.get("isdaynight"));

        forecast.getJSONObject("current").put("is_day", 0);
        JSONObject nighttime = payload(forecast).getJSONObject("data").getJSONObject("condition");
        assertEquals(Boolean.FALSE, nighttime.get("isdaynight"));
    }


    @Test public void rejectsMissingOrInvalidCurrentDayFlag() throws Exception {
        for (Object invalid : new Object[]{null, JSONObject.NULL, -1, 2, 0.5, "1", "day", true}) {
            JSONObject forecast = forecast();
            if (invalid == null) forecast.getJSONObject("current").remove("is_day");
            else forecast.getJSONObject("current").put("is_day", invalid);
            assertThrows(org.json.JSONException.class, () -> payload(forecast));
        }
    }


    @Test public void requiresBooleanCurrentDayFlagBeforeProviderWrite() throws Exception {
        JSONObject payload = payload(forecast());
        assertTrue(WeatherMapping.isComplete(payload.toString()));
        JSONObject condition = payload.getJSONObject("data").getJSONObject("condition");
        condition.put("isdaynight", false);
        assertTrue(WeatherMapping.isComplete(payload.toString()));
        for (Object invalid : new Object[]{null, JSONObject.NULL, 0, 1, "true", "false"}) {
            if (invalid == null) condition.remove("isdaynight");
            else condition.put("isdaynight", invalid);
            assertFalse(WeatherMapping.isComplete(payload.toString()));
        }
    }

    private static JSONObject payload(JSONObject forecast) throws Exception {
        return new JSONObject(WeatherMapping.toBydJson(forecast, null,
                "Amman", "Amman", NOW_MS));
    }

    private static JSONObject forecast() throws Exception {
        JSONObject current = new JSONObject()
                .put("time", "2025-10-01T12:00")
                .put("temperature_2m", 25)
                .put("relative_humidity_2m", 40)
                .put("apparent_temperature", 24)
                .put("is_day", 1)
                .put("weather_code", 0)
                .put("pressure_msl", 1013)
                .put("visibility", 20000)
                .put("wind_speed_10m", 10)
                .put("wind_direction_10m", 90)
                .put("wind_gusts_10m", 15)
                .put("uv_index", 4)
                .put("cloud_cover", 10)
                .put("precipitation", 0);
        JSONObject hourly = new JSONObject();
        for (String key : new String[]{"time", "temperature_2m", "weather_code",
                "precipitation_probability", "wind_speed_10m", "wind_direction_10m", "is_day"}) {
            hourly.put(key, new JSONArray());
        }
        for (int i = 0; i < 8; i++) {
            hourly.getJSONArray("time").put(LocalDateTime.of(2025, 10, 1, 12, 0)
                    .plusHours(i).toString());
            hourly.getJSONArray("temperature_2m").put(25);
            hourly.getJSONArray("weather_code").put(0);
            hourly.getJSONArray("precipitation_probability").put(0);
            hourly.getJSONArray("wind_speed_10m").put(10);
            hourly.getJSONArray("wind_direction_10m").put(90);
            hourly.getJSONArray("is_day").put(i < 6 ? 1 : 0);
        }
        JSONObject daily = new JSONObject();
        for (String key : new String[]{"time", "weather_code", "temperature_2m_max",
                "temperature_2m_min", "sunrise", "sunset", "uv_index_max",
                "wind_speed_10m_max", "wind_direction_10m_dominant"}) {
            daily.put(key, new JSONArray());
        }
        for (int i = 0; i < 16; i++) {
            String date = LocalDate.of(2025, 9, 30).plusDays(i).toString();
            daily.getJSONArray("time").put(date);
            daily.getJSONArray("weather_code").put(0);
            daily.getJSONArray("temperature_2m_max").put(27);
            daily.getJSONArray("temperature_2m_min").put(18);
            daily.getJSONArray("sunrise").put(date + "T06:00");
            daily.getJSONArray("sunset").put(date + "T18:00");
            daily.getJSONArray("uv_index_max").put(5);
            daily.getJSONArray("wind_speed_10m_max").put(15);
            daily.getJSONArray("wind_direction_10m_dominant").put(90);
        }
        return new JSONObject().put("timezone", "Asia/Amman")
                .put("current", current).put("hourly", hourly).put("daily", daily);
    }
}
