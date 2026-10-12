package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherFirmwareContractTest {
    @Test public void clearWeatherSelectsNightArtworkBeforeDawnAndAfterSunset() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            for (int hour : new int[]{2, 12, 22}) {
                long time = LocalDate.of(2026, 10, 12).atTime(hour, 0).atZone(MetFixtures.ZONE).toInstant().toEpochMilli();
                boolean day = hour == 12;
                JSONObject payload = MetFixtures.payload(time, "clearsky_day");
                assertTrue(WeatherMapping.isComplete(payload.toString()));
                assertEquals(time, payload.getJSONObject("data").getJSONObject("condition").getLong("updatetime"));
                assertEquals(!day, FirmwareWeatherContract.isDuringNight(payload));
                assertEquals(day ? "card_sunny" : "card_night", FirmwareWeatherContract.widgetBackground(payload, false));
                assertEquals(day ? "card_sunny_p" : "card_night_p", FirmwareWeatherContract.widgetBackground(payload, true));
                assertEquals("Sunny", FirmwareWeatherContract.widgetLabel(payload));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void refreshTimeAfterSunsetIsUsedEvenWhenForecastHourWasDaytime() throws Exception {
        long now = java.time.Instant.parse("2026-10-12T15:40:00Z").toEpochMilli();
        MetWeatherClient.Result result = MetFixtures.load(MetFixtures.forecast(now, "clearsky_day", 20), now, MetFixtures.ZONE);
        JSONObject payload = new JSONObject(WeatherMapping.toBydJson(result, "City", "City"));
        assertEquals("card_night", FirmwareWeatherContract.widgetBackground(payload, false));
    }

    @Test public void widgetIgnoresCustomTextAndDayFlagAndRetainsCloudyNightArtwork() throws Exception {
        long now = java.time.Instant.parse("2026-10-11T23:00:00Z").toEpochMilli();
        JSONObject payload = MetFixtures.payload(now, "clearsky_night");
        payload.getJSONObject("data").getJSONObject("condition").put("weathertext", "Clear night").put("isdaynight", true);
        assertEquals("Sunny", FirmwareWeatherContract.widgetLabel(payload));
        assertEquals("card_night", FirmwareWeatherContract.widgetBackground(payload, false));
        JSONObject cloudy = MetFixtures.payload(now, "partlycloudy_night");
        assertTrue(FirmwareWeatherContract.isDuringNight(cloudy));
        assertEquals("Cloudy", FirmwareWeatherContract.widgetLabel(cloudy));
        assertEquals(FirmwareWeatherContract.descriptors().getJSONObject("conditions")
                .getJSONObject("1").getString("landscapeCard"), FirmwareWeatherContract.widgetBackground(cloudy, false));
    }

    @Test public void polarDatesDoNotInventSolarEventsOrForceDayArtwork() throws Exception {
        for (boolean polarDay : new boolean[]{true, false}) {
            long now = MetFixtures.NOW;
            JSONObject forecast = MetFixtures.forecast(now, "clearsky_polartwilight", -5);
            MetWeatherClient.Result weather = new MetWeatherClient(url -> {
                if (url.contains("/locationforecast/")) return forecast;
                return MetFixtures.sun(LocalDate.parse(MetFixtures.parameter(url, "date")), ZoneId.of("UTC"), polarDay);
            }).load(78, 15, "SJ", ZoneId.of("UTC"), now, ignored -> { });
            JSONObject payload = new JSONObject(WeatherMapping.toBydJson(weather, "Arctic", "Arctic"));
            assertTrue(WeatherMapping.isComplete(payload.toString()));
            assertEquals(!polarDay, FirmwareWeatherContract.isDuringNight(payload));
            JSONArray days = payload.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers");
            assertEquals("--", days.getJSONObject(1).getString("sunRiseFmt"));
            assertEquals("--", days.getJSONObject(1).getString("sunSetFmt"));
        }
    }
}
