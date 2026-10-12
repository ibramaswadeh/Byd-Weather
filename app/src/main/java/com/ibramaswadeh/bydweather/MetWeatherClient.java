package com.ibramaswadeh.bydweather;

import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Loads the forecast and solar events needed for a complete stock-widget update. */
final class MetWeatherClient {
    static final String API_ROOT = "https://api.met.no/weatherapi/";
    interface JsonSource {
        JSONObject get(String url) throws Exception;
        default void invalidate(String url) { }
    }

    private final JsonSource source;
    private final File historyFile;
    private JSONObject history;

    MetWeatherClient(File cacheDirectory) {
        this(new WeatherHttpClient(new File(cacheDirectory, "met-http-v1")),
                new File(cacheDirectory, "met-history-v1.json"));
    }

    MetWeatherClient(JsonSource source) {
        this(source, null);
    }

    MetWeatherClient(JsonSource source, File historyFile) {
        this.source = source;
        this.historyFile = historyFile;
    }

    Result load(double latitude, double longitude, String countryCode, ZoneId zone, long now,
            Consumer<String> progress) throws Exception {
        WeatherHttpClient.checkInterrupted();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String coordinates = coordinates(latitude, longitude, 4);
        String forecastUrl = API_ROOT + "locationforecast/2.0/complete?" + coordinates;
        progress.accept("Downloading the weather forecast…");
        JSONObject response = download(forecastUrl, WeatherErrors.Stage.FORECAST);
        MetForecast forecast;
        try {
            MetForecast.parse(response).upcomingHours(now);
            response = includeEarlierHours(response, latitude, longitude, today, zone);
            forecast = MetForecast.parse(response);
            forecast.upcomingHours(now);
            for (int day = 0; day <= MetForecast.FUTURE_DAYS; day++) {
                forecast.summarize(today.plusDays(day), zone);
            }
        } catch (JSONException invalid) {
            source.invalidate(forecastUrl);
            throw new WeatherErrors.UpdateFailure(WeatherErrors.Stage.RESPONSE, invalid);
        }
        saveHistory(response, latitude, longitude);

        progress.accept("Getting sunrise and sunset…");
        Map<LocalDate, SunTimes> daylight = new LinkedHashMap<>();
        // A kilometre-scale solar location keeps GPS drift from invalidating date caches.
        String solarCoordinates = coordinates(latitude, longitude, 2);
        for (int day = -1; day <= MetForecast.FUTURE_DAYS; day++) {
            WeatherHttpClient.checkInterrupted();
            LocalDate date = today.plusDays(day);
            String offset = date.atTime(12, 0).atZone(zone).getOffset().getId();
            if (offset.equals("Z")) offset = "+00:00";
            String url = API_ROOT + "sunrise/3.0/sun?" + solarCoordinates + "&date=" + date
                    + "&offset=" + offset.replace("+", "%2B").replace(":", "%3A");
            JSONObject sun = download(url, WeatherErrors.Stage.ASTRONOMY);
            try {
                daylight.put(date, SunTimes.parse(sun, date, zone));
            } catch (JSONException invalid) {
                source.invalidate(url);
                throw new WeatherErrors.UpdateFailure(WeatherErrors.Stage.ASTRONOMY, invalid);
            }
        }

        MetAirQuality airQuality = null;
        List<String> notices = new ArrayList<>();
        if (hasAirQualityCoverage(latitude, longitude, countryCode)) {
            WeatherHttpClient.checkInterrupted();
            progress.accept("Downloading air quality…");
            String url = API_ROOT + "airqualityforecast/0.1/?" + coordinates + "&areaclass=grunnkrets";
            try {
                airQuality = MetAirQuality.parse(source.get(url), now);
            } catch (Exception unavailable) {
                WeatherHttpClient.checkInterrupted();
                if (unavailable instanceof JSONException) source.invalidate(url);
                notices.add("Air quality unavailable. " + WeatherErrors.describe(WeatherErrors.Stage.AIR_QUALITY, unavailable));
            }
        }
        WeatherHttpClient.checkInterrupted();
        return new Result(forecast, daylight, airQuality, zone, now, notices);
    }

    private JSONObject download(String url, WeatherErrors.Stage stage) throws Exception {
        WeatherHttpClient.checkInterrupted();
        try {
            JSONObject result = source.get(url);
            WeatherHttpClient.checkInterrupted();
            return result;
        } catch (Exception failure) {
            WeatherHttpClient.checkInterrupted();
            throw new WeatherErrors.UpdateFailure(stage, failure);
        }
    }

    static String coordinates(double latitude, double longitude, int decimals) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid location coordinates");
        }
        return "lat=" + decimal(latitude, decimals) + "&lon=" + decimal(longitude, decimals);
    }

    private static String decimal(double number, int places) {
        return BigDecimal.valueOf(number).setScale(places, RoundingMode.DOWN).toPlainString();
    }

    private static boolean hasAirQualityCoverage(double latitude, double longitude, String country) {
        if (country != null && !country.isEmpty()) return "NO".equalsIgnoreCase(country);
        return latitude >= 57 && latitude <= 72 && longitude >= 4 && longitude <= 32;
    }

    private JSONObject includeEarlierHours(JSONObject response, double latitude, double longitude,
            LocalDate today, ZoneId zone) {
        try {
            if (history == null && historyFile != null) {
                try (InputStream input = new AtomicFile(historyFile).openRead()) {
                    history = WeatherHttpClient.readJson(input);
                }
            }
            if (history == null || Math.abs(history.getDouble("latitude") - latitude) > 0.01
                    || Math.abs(history.getDouble("longitude") - longitude) > 0.01) return response;
            MetForecast.parse(history.getJSONObject("forecast"));
            JSONArray incoming = response.getJSONObject("properties").getJSONArray("timeseries");
            long first = MetForecast.timestamp(incoming.getJSONObject(0).getString("time"));
            long cutoff = today.atStartOfDay(zone).toInstant().toEpochMilli();
            TreeMap<Long, JSONObject> merged = new TreeMap<>();
            JSONArray previous = history.getJSONObject("forecast").getJSONObject("properties").getJSONArray("timeseries");
            for (int i = 0; i < previous.length(); i++) {
                JSONObject point = previous.getJSONObject(i);
                long time = MetForecast.timestamp(point.getString("time"));
                if (time >= cutoff && time < first) merged.put(time, point);
            }
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject point = incoming.getJSONObject(i);
                merged.put(MetForecast.timestamp(point.getString("time")), point);
            }
            JSONObject result = new JSONObject(response.toString());
            result.getJSONObject("properties").put("timeseries", new JSONArray(merged.values()));
            return result;
        } catch (Exception invalidHistory) {
            history = null;
            return response;
        }
    }

    private void saveHistory(JSONObject forecast, double latitude, double longitude) {
        AtomicFile file = historyFile == null ? null : new AtomicFile(historyFile);
        FileOutputStream output = null;
        try {
            history = new JSONObject().put("latitude", latitude).put("longitude", longitude)
                    .put("forecast", forecast);
            if (file == null) return;
            output = file.startWrite();
            output.write(history.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception ignored) {
            if (file != null && output != null) file.failWrite(output);
        }
    }

    static final class Result {
        final MetForecast forecast;
        final Map<LocalDate, SunTimes> daylight;
        final MetAirQuality airQuality;
        final ZoneId zone;
        final long time;
        final List<String> notices;

        Result(MetForecast forecast, Map<LocalDate, SunTimes> daylight, MetAirQuality airQuality,
                ZoneId zone, long time, List<String> notices) {
            this.forecast = forecast;
            this.daylight = daylight;
            this.airQuality = airQuality;
            this.zone = zone;
            this.time = time;
            this.notices = notices;
        }
    }
}
