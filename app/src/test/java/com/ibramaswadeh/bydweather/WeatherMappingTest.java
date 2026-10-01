package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** Tests the Open-Meteo to BYD payload contract through the public mapper. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherMappingTest {
    private static final long NOW_MS = 1_759_320_000_000L;

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
