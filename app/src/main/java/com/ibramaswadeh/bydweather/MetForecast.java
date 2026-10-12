package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validates MET time series and derives the hourly and daily values the widget displays. */
final class MetForecast {
    static final int FUTURE_DAYS = 7;
    private static final long HOUR_MS = Duration.ofHours(1).toMillis();
    final long updatedAt;
    final List<Point> points;

    private MetForecast(long updatedAt, List<Point> points) {
        this.updatedAt = updatedAt;
        this.points = Collections.unmodifiableList(points);
    }

    static MetForecast parse(JSONObject response) throws JSONException {
        JSONObject properties = response.getJSONObject("properties");
        JSONObject units = properties.getJSONObject("meta").getJSONObject("units");
        requireUnit(units, "air_temperature", "celsius");
        requireUnit(units, "wind_speed", "m/s");
        requireUnit(units, "wind_from_direction", "degrees");
        requireUnit(units, "relative_humidity", "%");
        requireUnit(units, "air_pressure_at_sea_level", "hPa");
        long updatedAt = timestamp(properties.getJSONObject("meta").getString("updated_at"));
        JSONArray series = properties.getJSONArray("timeseries");
        List<Point> points = new ArrayList<>();
        long previous = 0;
        for (int i = 0; i < series.length(); i++) {
            JSONObject entry = series.getJSONObject(i);
            long time = timestamp(entry.getString("time"));
            if (time <= previous) throw new JSONException("Forecast times are not in order");
            previous = time;
            JSONObject data = entry.getJSONObject("data");
            JSONObject details = data.getJSONObject("instant").getJSONObject("details");
            JSONObject period = firstPeriod(data);
            MetCondition condition = period == null ? null
                    : MetCondition.parse(period.getJSONObject("summary").getString("symbol_code"));
            JSONObject sixHours = data.optJSONObject("next_6_hours");
            JSONObject extremes = sixHours == null ? null : sixHours.optJSONObject("details");
            JSONObject periodDetails = period == null ? null : period.optJSONObject("details");
            points.add(new Point(time,
                    number(details, "air_temperature", -100, 65),
                    number(details, "relative_humidity", 0, 100),
                    number(details, "air_pressure_at_sea_level", 800, 1100),
                    number(details, "wind_speed", 0, 150),
                    number(details, "wind_from_direction", 0, 360),
                    optional(details, "apparent_air_temperature", -150, 100),
                    optional(details, "wind_speed_of_gust", 0, 200),
                    optional(details, "cloud_area_fraction", 0, 100),
                    optional(details, "ultraviolet_index_clear_sky", 0, 100),
                    optional(periodDetails, "precipitation_amount", 0, 2000),
                    optional(periodDetails, "probability_of_precipitation", 0, 100),
                    optional(extremes, "air_temperature_min", -100, 65),
                    optional(extremes, "air_temperature_max", -100, 65), condition));
        }
        if (points.size() < 8) throw new JSONException("Too few forecast hours");
        return new MetForecast(updatedAt, points);
    }

    List<Point> upcomingHours(long now) throws JSONException {
        int start = 0;
        while (start + 1 < points.size() && points.get(start + 1).time <= now) start++;
        if (Math.abs(points.get(start).time - now) > HOUR_MS) {
            throw new JSONException("No current forecast; check the car's date and time");
        }
        List<Point> hours = new ArrayList<>();
        for (int i = start; i < points.size() && hours.size() < 48; i++) {
            Point point = points.get(i);
            if (point.condition == null || (!hours.isEmpty()
                    && point.time - hours.get(hours.size() - 1).time != HOUR_MS)) break;
            hours.add(point);
        }
        if (hours.size() < 8) throw new JSONException("Too few upcoming forecast hours");
        return hours;
    }

    Day summarize(LocalDate date, ZoneId zone) throws JSONException {
        long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        Point selected = null;
        for (Point point : points) {
            if (point.time < start || point.time >= end) continue;
            low = Math.min(low, point.temperature);
            high = Math.max(high, point.temperature);
            // A period crossing midnight cannot be assigned wholly to either local day.
            if (point.time + 6 * HOUR_MS <= end) {
                if (point.minimum != null) low = Math.min(low, point.minimum);
                if (point.maximum != null) high = Math.max(high, point.maximum);
            }
            if (point.condition != null && (selected == null
                    || point.condition.severity > selected.condition.severity)) selected = point;
        }
        if (selected == null || !Double.isFinite(low) || low > high) {
            throw new JSONException("Daily forecast is incomplete");
        }
        return new Day(date, (int) Math.round(low), (int) Math.round(high), selected);
    }

    private static JSONObject firstPeriod(JSONObject data) {
        for (String key : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
            JSONObject period = data.optJSONObject(key);
            if (period != null && period.has("summary")) return period;
        }
        return null;
    }

    private static void requireUnit(JSONObject units, String key, String expected) throws JSONException {
        if (!expected.equals(units.optString(key))) throw new JSONException("Unexpected units for " + key);
    }

    static long timestamp(String value) throws JSONException {
        try {
            return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (RuntimeException invalid) {
            throw new JSONException("Invalid weather timestamp");
        }
    }

    static Double optional(JSONObject object, String key, double low, double high) {
        Object value = object == null ? null : object.opt(key);
        if (!(value instanceof Number)) return null;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= low && number <= high ? number : null;
    }

    private static double number(JSONObject object, String key, double low, double high) throws JSONException {
        Double value = optional(object, key, low, high);
        if (value == null) throw new JSONException("Missing or invalid " + key);
        return value;
    }

    static final class Point {
        final long time;
        final double temperature, humidity, pressure, windSpeed, windDirection;
        final Double feelsLike, gustSpeed, cloudCover, clearSkyUv, precipitation, rainProbability;
        final Double minimum, maximum;
        final MetCondition condition;

        Point(long time, double temperature, double humidity, double pressure, double windSpeed,
                double windDirection, Double feelsLike, Double gustSpeed, Double cloudCover,
                Double clearSkyUv, Double precipitation, Double rainProbability,
                Double minimum, Double maximum, MetCondition condition) {
            this.time = time;
            this.temperature = temperature;
            this.humidity = humidity;
            this.pressure = pressure;
            this.windSpeed = windSpeed;
            this.windDirection = windDirection;
            this.feelsLike = feelsLike;
            this.gustSpeed = gustSpeed;
            this.cloudCover = cloudCover;
            this.clearSkyUv = clearSkyUv;
            this.precipitation = precipitation;
            this.rainProbability = rainProbability;
            this.minimum = minimum;
            this.maximum = maximum;
            this.condition = condition;
        }
    }

    static final class Day {
        final LocalDate date;
        final int minimum, maximum;
        final Point representative;

        Day(LocalDate date, int minimum, int maximum, Point representative) {
            this.date = date;
            this.minimum = minimum;
            this.maximum = maximum;
            this.representative = representative;
        }
    }
}
