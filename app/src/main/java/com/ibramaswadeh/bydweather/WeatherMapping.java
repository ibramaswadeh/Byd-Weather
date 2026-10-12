package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Serializes validated weather into the stock WeatherData contract. */
final class WeatherMapping {
    static final String ATTRIBUTION = "Weather data: MET Norway (CC BY 4.0)";
    static final String SOURCE_URL = "https://www.met.no/en";
    static final int REQUIRED_HOURLY_COUNT = 8;
    static final int REQUIRED_DAILY_COUNT = MetForecast.FUTURE_DAYS + 2;
    private static final int UNAVAILABLE = -1;

    private WeatherMapping() { }

    static String toBydJson(MetWeatherClient.Result weather, String cityName,
            String englishCityName) throws JSONException {
        long now = weather.time;
        LocalDate today = date(now, weather.zone);
        List<MetForecast.Point> hours = weather.forecast.upcomingHours(now);
        MetForecast.Point current = hours.get(0);
        JSONObject condition = condition(current)
                .put("temperature", Math.round(current.temperature))
                .put("humidity", Math.round(current.humidity))
                .put("pressure", Math.round(current.pressure))
                .put("isdaynight", sunlight(weather, today).isDay(now))
                .put("uVIndex", rounded(current.clearSkyUv))
                .put("uvIndexDesc", "Clear-sky UV")
                // The stock model accepts an Integer; it cannot display a text placeholder.
                .put("visibility", UNAVAILABLE).put("visibilityAvailable", false)
                .put("updatetime", now).put("updatetimeFmt", formatTime(now, weather.zone))
                .put("forecastTime", current.time).put("forecastUpdatedAt", weather.forecast.updatedAt)
                .put("expiretime", now + 3 * 60 * 60 * 1000L)
                .put("desc", ATTRIBUTION).put("mobilelink", SOURCE_URL);
        putIfPresent(condition, "realfeel", current.feelsLike);
        putIfPresent(condition, "cloudCover", current.cloudCover);
        putIfPresent(condition, "precipitation", current.precipitation);

        JSONArray hourlyItems = new JSONArray();
        for (MetForecast.Point point : hours) {
            boolean day = sunlight(weather, date(point.time, weather.zone)).isDay(point.time);
            JSONObject hour = condition(point).put("date", point.time)
                    .put("temp", Math.round(point.temperature))
                    .put("wd", windDirection(point.windDirection)).put("wp", windLevel(point.windSpeed))
                    .put("isdaynight", day).put("Isdaynight", day);
            putIfPresent(hour, "rainprobability", point.rainProbability);
            hourlyItems.put(hour);
        }

        JSONArray dailyItems = new JSONArray();
        // The factory skips yesterday in its forecast list but reads its solar events.
        dailyItems.put(solarRecord(sunlight(weather, today.minusDays(1)), weather.zone)
                .put("timingOnly", true));
        for (int i = 0; i <= MetForecast.FUTURE_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            MetForecast.Day day = weather.forecast.summarize(date, weather.zone);
            dailyItems.put(solarRecord(sunlight(weather, date), weather.zone)
                    .put("mintemp", day.minimum).put("maxtemp", day.maximum)
                    .put("conditionDay", condition(day.representative))
                    .put("conditionNight", condition(day.representative))
                    .put("lv", 0).put("aqivalue", UNAVAILABLE).put("aqivaluetext", "--")
                    .put("source", ATTRIBUTION).put("mobilelink", SOURCE_URL));
        }

        String city = nonEmpty(cityName, "Current location");
        JSONObject data = new JSONObject()
                .put("city", new JSONObject().put("name", city)
                        .put("englishCityName", nonEmpty(englishCityName, city))
                        .put("timezone", weather.zone.getId()))
                .put("condition", condition)
                .put("hourlys", new JSONObject().put("hourlyweathers", hourlyItems)
                        .put("mobilelink", SOURCE_URL))
                .put("dailys", new JSONObject().put("dailyweathers", dailyItems)
                        .put("publictime", today.atStartOfDay(weather.zone).toInstant().toEpochMilli())
                        .put("publictimeFmt", today.toString()).put("mobilelink", SOURCE_URL))
                .put("aqi", airQuality(weather.airQuality, now))
                .put("aqidays", new JSONArray()).put("alarm", new JSONArray())
                .put("liveInfos", new JSONArray()).put("weatherDesc", ATTRIBUTION)
                .put("mobilelink", SOURCE_URL);
        return new JSONObject().put("resultcode", "0").put("resultinfo", "success")
                .put("servertime", now).put("data", data).toString();
    }

    private static JSONObject condition(MetForecast.Point point) throws JSONException {
        if (point.condition == null) throw new JSONException("Missing forecast condition");
        JSONObject result = new JSONObject().put("cnweatherid", point.condition.nativeCode)
                .put("weatherid", point.condition.nativeCode).put("zmweatherid", point.condition.nativeCode)
                .put("weathertext", point.condition.description).put("sourceWeatherSymbol", point.condition.symbol)
                .put("windspeed", Math.round(point.windSpeed * 3.6)).put("windlevel", windLevel(point.windSpeed))
                .put("winddegrees", Math.round(point.windDirection))
                .put("winddir", windDirection(point.windDirection)).put("winddirtext", windDirection(point.windDirection));
        if (point.gustSpeed != null) {
            result.put("windgustspeed", Math.round(point.gustSpeed * 3.6))
                    .put("windgustlevel", windLevel(point.gustSpeed));
        }
        return result;
    }

    private static JSONObject solarRecord(SunTimes sun, ZoneId zone) throws JSONException {
        return new JSONObject().put("publictime", sun.nativeAnchor()).put("publictimeFmt", sun.date.toString())
                .put("sunRise", sun.nativeSunrise()).put("sunSet", sun.nativeSunset())
                .put("sunRiseFmt", sun.sunrise == null ? "--" : formatTime(sun.sunrise, zone))
                .put("sunSetFmt", sun.sunset == null ? "--" : formatTime(sun.sunset, zone))
                .put("sunriseAvailable", sun.sunrise != null).put("sunsetAvailable", sun.sunset != null)
                // WeatherData uses this formatted date to select today's temperature range.
                .put("moonSetFmt", sun.date.atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private static JSONObject airQuality(MetAirQuality reading, long time) throws JSONException {
        JSONObject result = new JSONObject().put("lv", "0").put("aqivalue", UNAVAILABLE)
                .put("aqivaluetext", "--").put("aqidesc", "Unavailable")
                .put("pm25", UNAVAILABLE).put("pm10", UNAVAILABLE)
                .put("updatetime", time).put("mobilelink", SOURCE_URL);
        if (reading != null) {
            // Norway's risk index is not the Chinese AQI scale used by the factory labels.
            result.put("sourceAqi", reading.index).put("sourceAqiScale", "Norwegian health-risk index")
                    .put("aqidesc", reading.description() + " health risk (Norwegian index)")
                    .put("pm25", rounded(reading.pm25)).put("pm10", rounded(reading.pm10));
        }
        return result;
    }

    static boolean isComplete(String payload) {
        try {
            JSONObject data = new JSONObject(payload).getJSONObject("data");
            if (data.getJSONObject("city").getString("name").trim().isEmpty()) return false;
            JSONObject current = data.getJSONObject("condition");
            if (!validCondition(current)) return false;
            requireNumbers(current, "temperature", "humidity", "pressure", "visibility", "uVIndex", "updatetime");
            current.getBoolean("isdaynight");
            JSONArray hours = data.getJSONObject("hourlys").getJSONArray("hourlyweathers");
            JSONArray days = data.getJSONObject("dailys").getJSONArray("dailyweathers");
            if (hours.length() < REQUIRED_HOURLY_COUNT || days.length() < REQUIRED_DAILY_COUNT) return false;
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < hours.length(); i++) {
                JSONObject hour = hours.getJSONObject(i);
                requireNumbers(hour, "temp", "date");
                if (!validCondition(hour) || hour.getLong("date") <= previous
                        || hour.getBoolean("isdaynight") != hour.getBoolean("Isdaynight")) return false;
                previous = hour.getLong("date");
            }
            previous = Long.MIN_VALUE;
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);
                requireNumbers(day, "publictime", "sunRise", "sunSet");
                if (day.getLong("publictime") <= previous) return false;
                previous = day.getLong("publictime");
                if (i == 0 && day.optBoolean("timingOnly")) continue;
                requireNumbers(day, "mintemp", "maxtemp", "lv");
                if (day.getDouble("mintemp") > day.getDouble("maxtemp")
                        || !validCondition(day.getJSONObject("conditionDay"))
                        || !validCondition(day.getJSONObject("conditionNight"))) return false;
            }
            requireNumbers(data.getJSONObject("aqi"), "aqivalue", "pm25", "pm10");
            return "0".equals(data.getJSONObject("aqi").getString("lv"));
        } catch (Exception invalid) {
            return false;
        }
    }

    private static boolean validCondition(JSONObject condition) throws JSONException {
        requireNumbers(condition, "cnweatherid", "weatherid", "zmweatherid");
        int code = condition.getInt("cnweatherid");
        return ((code >= 0 && code <= 31) || code == 53)
                && code == condition.getInt("weatherid") && code == condition.getInt("zmweatherid");
    }

    private static void requireNumbers(JSONObject object, String... keys) throws JSONException {
        for (String key : keys) {
            Object value = object.get(key);
            if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) {
                throw new JSONException("Invalid widget number");
            }
        }
    }

    private static SunTimes sunlight(MetWeatherClient.Result weather, LocalDate date) throws JSONException {
        SunTimes sun = weather.daylight.get(date);
        if (sun == null) throw new JSONException("Missing solar date");
        return sun;
    }

    private static LocalDate date(long time, ZoneId zone) {
        return Instant.ofEpochMilli(time).atZone(zone).toLocalDate();
    }

    private static String formatTime(long time, ZoneId zone) {
        return Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String windDirection(double degrees) {
        return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[(int) Math.round(degrees / 45) % 8];
    }

    private static String windLevel(double speed) {
        double[] thresholds = {0.3, 1.6, 3.4, 5.5, 8, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
        int level = 0;
        while (level < thresholds.length && speed >= thresholds[level]) level++;
        return String.valueOf(level);
    }

    private static int rounded(Double value) {
        return value == null ? UNAVAILABLE : (int) Math.round(value);
    }

    private static void putIfPresent(JSONObject object, String key, Double value) throws JSONException {
        if (value != null) object.put(key, value);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
