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
    @Test public void rejectsInvalidConditionsBeyondThePublishedHourlyWindow() throws Exception {
        for (String field : new String[]{"weather_code", "is_day"}) {
            JSONObject source = forecast();
            JSONObject hourly = source.getJSONObject("hourly");
            for (int i = 8; i < 64; i++) {
                hourly.getJSONArray("time").put(LocalDateTime.of(2025, 10, 1, 12, 0).plusHours(i).toString());
                for (String key : new String[]{"temperature_2m", "weather_code", "precipitation_probability",
                        "wind_speed_10m", "wind_direction_10m", "is_day"}) {
                    hourly.getJSONArray(key).put(hourly.getJSONArray(key).get(i % 8));
                }
            }
            assertTrue(WeatherMapping.isComplete(payload(source).toString()));
            hourly.getJSONArray(field).put(50, 0.5);
            assertThrows("Invalid future " + field, org.json.JSONException.class, () -> payload(source));
        }
    }

    private static final long NOW_MS = 1_759_320_000_000L;

    @Test public void representsTheComingEveningInTheDailyNightForecast() throws Exception {
        JSONObject source = forecast();
        JSONObject hourly = source.getJSONObject("hourly");
        for (String key : new String[]{"time", "temperature_2m", "weather_code", "precipitation_probability",
                "wind_speed_10m", "wind_direction_10m", "is_day"}) hourly.put(key, new JSONArray());
        for (int i = 0; i < 24; i++) {
            hourly.getJSONArray("time").put(LocalDateTime.of(2025,10,1,0,0).plusHours(i).toString());
            hourly.getJSONArray("temperature_2m").put(25);
            hourly.getJSONArray("weather_code").put(i == 21 ? 61 : i < 6 ? 95 : 0);
            hourly.getJSONArray("precipitation_probability").put(0);
            hourly.getJSONArray("wind_speed_10m").put(10);
            hourly.getJSONArray("wind_direction_10m").put(90);
            hourly.getJSONArray("is_day").put(i >= 6 && i < 18 ? 1 : 0);
        }
        JSONObject night = payload(source).getJSONObject("data").getJSONObject("dailys")
                .getJSONArray("dailyweathers").getJSONObject(1).getJSONObject("conditionNight");
        assertEquals(7, night.getInt("cnweatherid"));
        assertEquals("Slight rain", night.getString("weathertext"));
    }

    @Test public void doesNotAssignHourlyAmountsToUnverifiedNativePeriodUnits() throws Exception {
        JSONObject source = forecast();
        for (String key : new String[]{"rain", "showers", "precipitation"}) {
            source.getJSONObject("hourly").put(key, new JSONArray().put(1.2));
        }
        JSONObject data = payload(source).getJSONObject("data");
        JSONObject day = data.getJSONObject("dailys").getJSONArray("dailyweathers")
                .getJSONObject(1).getJSONObject("conditionDay");
        assertFalse(day.has("rain"));
        assertFalse(day.has("totalLiquid"));
    }

    @Test public void preservesNativePrecipitationPrecisionWithoutApplyingEuropeanCategoriesAsChinese() throws Exception {
        JSONObject source = forecast();
        source.getJSONObject("current").put("precipitation", 0.125);
        JSONObject air = new JSONObject().put("current", new JSONObject().put("european_aqi", 35));
        JSONObject data = new JSONObject(WeatherMapping.toBydJson(source, air, "Amman", "Amman", NOW_MS))
                .getJSONObject("data");
        assertEquals(0.125, data.getJSONObject("condition").getDouble("precipitation"), 0.0001);
        assertEquals(35, data.getJSONObject("aqi").getInt("aqivalue"));
        assertEquals("0", data.getJSONObject("aqi").get("lv"));
        assertEquals(0, data.getJSONObject("dailys").getJSONArray("dailyweathers").getJSONObject(1).getInt("lv"));
    }

    @Test public void checksAllDailyRowsAndBothPeriodsBeforeAcceptingPayload() throws Exception {
        for (String key : new String[]{"sunRise", "sunSet", "mintemp", "maxtemp", "publictime"}) {
            JSONObject result = payload(forecast());
            result.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers")
                    .getJSONObject(15).remove(key);
            assertFalse(key, WeatherMapping.isComplete(result.toString()));
        }
        for (String section : new String[]{"conditionDay", "conditionNight"}) {
            JSONObject result = payload(forecast());
            result.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers")
                    .getJSONObject(7).getJSONObject(section).put("cnweatherid", -1);
            assertFalse(section, WeatherMapping.isComplete(result.toString()));
        }
        JSONObject result = payload(forecast());
        result.getJSONObject("data").getJSONObject("hourlys").getJSONArray("hourlyweathers")
                .getJSONObject(7).put("cnweatherid", 0.4);
        assertFalse(WeatherMapping.isComplete(result.toString()));
    }

    @Test public void rejectsNonIntegralAndUnknownConditionCodesInEverySourceSection() throws Exception {
        for (double invalid : new double[]{0.4, 97.4, -1, 100, 52}) {
            JSONObject source = forecast();
            source.getJSONObject("current").put("weather_code", invalid);
            assertThrows(org.json.JSONException.class, () -> payload(source));
            JSONObject hourlySource = forecast();
            hourlySource.getJSONObject("hourly").getJSONArray("weather_code").put(7, invalid);
            assertThrows(org.json.JSONException.class, () -> payload(hourlySource));
            JSONObject dailySource = forecast();
            dailySource.getJSONObject("daily").getJSONArray("weather_code").put(15, invalid);
            assertThrows(org.json.JSONException.class, () -> payload(dailySource));
        }
    }

    @Test public void preservesEveryDocumentedConditionAndItsIntensity() throws Exception {
        // Expected IDs come from the extracted native icon map; text from the official WMO table.
        int[] wmo = {0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99};
        int[] nativeId = {0,1,1,2,18,18,7,8,9,19,19,7,8,9,19,19,14,15,16,14,3,3,3,13,13,4,5,4,5};
        String[] label = {"Clear sky","Mainly clear","Partly cloudy","Overcast","Fog","Depositing rime fog",
                "Light drizzle","Moderate drizzle","Dense drizzle","Light freezing drizzle","Dense freezing drizzle",
                "Slight rain","Moderate rain","Heavy rain","Light freezing rain","Heavy freezing rain",
                "Slight snowfall","Moderate snowfall","Heavy snowfall","Snow grains","Slight rain showers",
                "Moderate rain showers","Violent rain showers","Slight snow showers","Heavy snow showers",
                "Thunderstorm","Thunderstorm with slight hail","Heavy thunderstorm","Thunderstorm with heavy hail"};
        for (int n = 0; n < wmo.length; n++) {
            JSONObject source = forecast();
            source.getJSONObject("current").put("weather_code", wmo[n]);
            for (int i = 0; i < 8; i++) source.getJSONObject("hourly").getJSONArray("weather_code").put(i, wmo[n]);
            for (int i = 0; i < 16; i++) source.getJSONObject("daily").getJSONArray("weather_code").put(i, wmo[n]);
            JSONObject data = payload(source).getJSONObject("data");
            JSONObject[] conditions = {data.getJSONObject("condition"),
                    data.getJSONObject("hourlys").getJSONArray("hourlyweathers").getJSONObject(0),
                    data.getJSONObject("dailys").getJSONArray("dailyweathers").getJSONObject(1).getJSONObject("conditionDay"),
                    data.getJSONObject("dailys").getJSONArray("dailyweathers").getJSONObject(1).getJSONObject("conditionNight")};
            for (JSONObject condition : conditions) {
                assertEquals("WMO " + wmo[n], nativeId[n], condition.getInt("cnweatherid"));
                assertEquals(label[n], condition.getString("weathertext"));
                assertEquals(wmo[n], condition.getInt("sourceWeatherCode"));
            }
        }
    }

    @Test public void acceptsHeavyThunderstormsThroughoutTheForecast() throws Exception {
        JSONObject source = forecast();
        source.getJSONObject("current").put("weather_code", 97);
        for (int i = 0; i < 8; i++) source.getJSONObject("hourly").getJSONArray("weather_code").put(i, 97);
        for (int i = 0; i < 16; i++) source.getJSONObject("daily").getJSONArray("weather_code").put(i, 97);
        JSONObject result = payload(source);
        JSONObject data = result.getJSONObject("data");
        assertEquals(4, data.getJSONObject("condition").getInt("cnweatherid"));
        assertEquals(4, data.getJSONObject("hourlys").getJSONArray("hourlyweathers")
                .getJSONObject(1).getInt("cnweatherid"));
        assertEquals(4, data.getJSONObject("dailys").getJSONArray("dailyweathers")
                .getJSONObject(1).getJSONObject("conditionNight").getInt("cnweatherid"));
        assertTrue(WeatherMapping.isComplete(result.toString()));
    }

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
        assertEquals(0, days.getJSONObject(1).getInt("lv"));
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
        hourly.getJSONArray("weather_code").put(7, 45).put(7, 61);
        hourly.getJSONArray("wind_speed_10m").put(7, 25);
        hourly.getJSONArray("wind_direction_10m").put(7, 180);
        for (String key : new String[]{"relative_humidity_2m", "cloud_cover", "precipitation",
                "rain", "showers", "wind_gusts_10m"}) {
            hourly.put(key, new JSONArray());
        }
        hourly.getJSONArray("relative_humidity_2m").put(7, 80);
        hourly.getJSONArray("cloud_cover").put(7, 90);
        hourly.getJSONArray("precipitation_probability").put(7, 75);
        hourly.getJSONArray("precipitation").put(7, 2.5);
        hourly.getJSONArray("rain").put(7, 1.2);
        hourly.getJSONArray("showers").put(7, 0.8);
        hourly.getJSONArray("wind_gusts_10m").put(7, 65);
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
        assertEquals("75", night.get("precProb"));
        assertFalse(night.has("rain"));
        assertFalse(night.has("totalLiquid"));
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
        assertFalse(today.has("pressure"));
        assertFalse(today.has("visibility"));
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

    @Test public void mapsBothNativeHourlyDayFlagSpellings() throws Exception {
        JSONObject forecast = forecast();
        JSONArray result = payload(forecast).getJSONObject("data")
                .getJSONObject("hourlys").getJSONArray("hourlyweathers");
        assertEquals(Boolean.TRUE, result.getJSONObject(0).get("Isdaynight"));
        assertEquals(Boolean.FALSE, result.getJSONObject(7).get("Isdaynight"));
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
