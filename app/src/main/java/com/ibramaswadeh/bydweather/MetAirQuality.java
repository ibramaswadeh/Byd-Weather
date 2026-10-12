package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.Locale;

/** Keeps Norway's health-risk index distinct from the factory's pollution categories. */
final class MetAirQuality {
    final double index;
    final Double pm25, pm10;

    private MetAirQuality(double index, Double pm25, Double pm10) {
        this.index = index;
        this.pm25 = pm25;
        this.pm10 = pm10;
    }

    static MetAirQuality parse(JSONObject response, long now) throws JSONException {
        JSONArray times = response.getJSONObject("data").getJSONArray("time");
        JSONObject selected = null;
        long selectedTime = 0;
        for (int i = 0; i < times.length(); i++) {
            JSONObject item = times.getJSONObject(i);
            long time = MetForecast.timestamp(item.getString("from"));
            if (time <= now && time > selectedTime) {
                selected = item.getJSONObject("variables");
                selectedTime = time;
            }
        }
        if (selected == null || now - selectedTime > Duration.ofMinutes(90).toMillis()) {
            throw new JSONException("No current air-quality forecast");
        }
        Double index = value(selected, "AQI", "1");
        if (index == null || index < 1 || index > 5) throw new JSONException("Invalid air-quality index");
        return new MetAirQuality(index, value(selected, "pm25_concentration", "ug/m3"),
                value(selected, "pm10_concentration", "ug/m3"));
    }

    String description() {
        return index < 2 ? "Low" : index < 3 ? "Moderate" : index < 4 ? "High" : "Very high";
    }

    String formattedIndex() {
        return String.format(Locale.ROOT, "%.1f", index);
    }

    private static Double value(JSONObject variables, String key, String unit) {
        JSONObject reading = variables.optJSONObject(key);
        if (reading == null || !unit.equals(reading.optString("units"))) return null;
        return MetForecast.optional(reading, "value", 0, 100_000);
    }
}
