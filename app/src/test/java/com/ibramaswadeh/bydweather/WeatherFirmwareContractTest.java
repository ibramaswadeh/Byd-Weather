package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherFirmwareContractTest {
    @Test public void clearWeatherSelectsNightArtworkBeforeDawnAndAfterSunset() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            // Native night selection uses epoch times, independent of the car's display zone.
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            for (String clock : new String[]{"02:00", "12:00", "22:00"}) {
                JSONObject source = forecast();
                String time = "2026-10-02T" + clock;
                boolean day = clock.equals("12:00");
                source.getJSONObject("current").put("time", time)
                        .put("weather_code", 0).put("is_day", day ? 1 : 0);
                JSONObject payload = payload(source);
                assertEquals(LocalDateTime.parse(time).atZone(ZoneId.of("Asia/Amman"))
                        .toInstant().toEpochMilli(), payload.getJSONObject("data")
                        .getJSONObject("condition").getLong("updatetime"));
                assertEquals(!day, FirmwareWeatherContract.isDuringNight(payload));
                assertEquals(day ? "card_sunny" : "card_night",
                        FirmwareWeatherContract.widgetBackground(payload, false));
                assertEquals(day ? "card_sunny_p" : "card_night_p",
                        FirmwareWeatherContract.widgetBackground(payload, true));
                // Stock English weather_id_0 is "Sunny" even when it renders the moon card.
                assertEquals("Sunny", FirmwareWeatherContract.widgetLabel(payload));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void widgetIgnoresPayloadLabelAndDayFlagWhenChoosingNightArtwork() throws Exception {
        JSONObject source = forecast();
        source.getJSONObject("current").put("time", "2026-10-02T02:00")
                .put("weather_code", 0).put("is_day", 0);
        JSONObject payload = payload(source);
        payload.getJSONObject("data").getJSONObject("condition")
                .put("weathertext", "Clear night").put("isdaynight", true);
        assertEquals("Sunny", FirmwareWeatherContract.widgetLabel(payload));
        assertEquals("card_night", FirmwareWeatherContract.widgetBackground(payload, false));

        // A midnight anchor makes native binarySearch choose today's sunset at 02:00.
        // Merely setting weathertext or isdaynight cannot repair that older payload shape.
        JSONArray days = payload.getJSONObject("data").getJSONObject("dailys")
                .getJSONArray("dailyweathers");
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            day.put("publictime", LocalDateTime.parse(day.getString("publictimeFmt") + "T00:00")
                    .atZone(ZoneId.of("Asia/Amman")).toInstant().toEpochMilli());
        }
        assertEquals("card_sunny", FirmwareWeatherContract.widgetBackground(payload, false));
    }

    private static JSONObject forecast() throws Exception {
        try (InputStream stream = WeatherFirmwareContractTest.class
                .getResourceAsStream("/amman-open-meteo.json")) {
            assertNotNull(stream);
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static JSONObject payload(JSONObject source) throws Exception {
        return new JSONObject(WeatherMapping.toBydJson(source, null,
                "Amman", "Amman", 1_791_110_000_000L));
    }
}
