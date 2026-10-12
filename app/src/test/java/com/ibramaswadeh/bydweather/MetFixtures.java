package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Deterministic examples with MET's hourly-to-six-hourly forecast transition. */
final class MetFixtures {
    static final ZoneId ZONE = ZoneId.of("Asia/Amman");
    static final long NOW = Instant.parse("2026-10-12T09:30:00Z").toEpochMilli();

    static JSONObject forecast(long now, String symbol, double temperature) throws Exception {
        Instant start = Instant.ofEpochMilli(now).truncatedTo(ChronoUnit.HOURS);
        JSONObject units = new JSONObject().put("air_temperature", "celsius")
                .put("relative_humidity", "%").put("air_pressure_at_sea_level", "hPa")
                .put("wind_speed", "m/s").put("wind_from_direction", "degrees");
        JSONArray series = new JSONArray();
        for (int hour = 0; hour <= 240; hour += hour < 60 ? 1 : 6) {
            JSONObject details = new JSONObject().put("air_temperature", temperature)
                    .put("relative_humidity", 62).put("air_pressure_at_sea_level", 1013.2)
                    .put("wind_speed", 4.5).put("wind_from_direction", 220)
                    .put("cloud_area_fraction", 35).put("ultraviolet_index_clear_sky", 2.4);
            JSONObject period = new JSONObject().put("summary", new JSONObject().put("symbol_code", symbol))
                    .put("details", new JSONObject().put("precipitation_amount", 0.5)
                            .put("probability_of_precipitation", 40));
            JSONObject data = new JSONObject().put("instant", new JSONObject().put("details", details));
            if (hour < 240) data.put(hour < 60 ? "next_1_hours" : "next_6_hours", period);
            series.put(new JSONObject().put("time", start.plus(hour, ChronoUnit.HOURS).toString()).put("data", data));
        }
        return new JSONObject().put("type", "Feature").put("properties", new JSONObject()
                .put("meta", new JSONObject().put("updated_at", start.toString()).put("units", units))
                .put("timeseries", series));
    }

    static JSONObject sun(LocalDate date, ZoneId zone, Boolean polarDay) throws Exception {
        return new JSONObject().put("properties", new JSONObject().put("body", "Sun")
                .put("sunrise", new JSONObject().put("time", polarDay == null
                        ? date.atTime(6, 0).atZone(zone).toOffsetDateTime().toString() : JSONObject.NULL))
                .put("sunset", new JSONObject().put("time", polarDay == null
                        ? date.atTime(18, 0).atZone(zone).toOffsetDateTime().toString() : JSONObject.NULL))
                .put("solarnoon", new JSONObject().put("visible", polarDay == null || polarDay)));
    }

    static JSONObject response(String url, JSONObject forecast) throws Exception {
        if (url.contains("/locationforecast/")) return new JSONObject(forecast.toString());
        if (url.contains("/sunrise/")) {
            String date = parameter(url, "date");
            String offset = parameter(url, "offset");
            return sun(LocalDate.parse(date), ZoneOffset.of(offset), null);
        }
        throw new WeatherErrors.HttpFailure(404);
    }

    static String parameter(String url, String name) {
        for (String item : URI.create(url).getRawQuery().split("&")) {
            if (item.startsWith(name + "=")) {
                return URLDecoder.decode(item.substring(name.length() + 1), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing parameter " + name);
    }

    static MetWeatherClient.Result load(JSONObject forecast, long now, ZoneId zone) throws Exception {
        return new MetWeatherClient(url -> response(url, forecast))
                .load(31.95, 35.94, "JO", zone, now, ignored -> { });
    }

    static JSONObject payload(long time, String symbol) throws Exception {
        return new JSONObject(WeatherMapping.toBydJson(load(forecast(time, symbol, 18), time, ZONE), "Amman", "Amman"));
    }
}
