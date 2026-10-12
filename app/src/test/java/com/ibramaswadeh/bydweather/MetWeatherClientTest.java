package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class MetWeatherClientTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void skipsNorwayOnlyApiElsewhereAndRequestsNineSolarDatesWithDstOffsets() throws Exception {
        long now = Instant.parse("2026-10-24T10:00:00Z").toEpochMilli();
        JSONObject forecast = MetFixtures.forecast(now, "cloudy", 12);
        List<String> urls = new ArrayList<>();
        MetWeatherClient client = new MetWeatherClient(url -> {
            urls.add(url);
            return MetFixtures.response(url, forecast);
        });
        client.load(52.52, 13.405, "DE", ZoneId.of("Europe/Berlin"), now, ignored -> { });
        assertEquals(10, urls.size());
        assertTrue(urls.stream().allMatch(url -> url.startsWith("https://api.met.no/weatherapi/")));
        assertTrue(urls.stream().noneMatch(url -> url.contains("airqualityforecast")));
        assertTrue(urls.stream().anyMatch(url -> url.contains("date=2026-10-24&offset=%2B02%3A00")));
        assertTrue(urls.stream().anyMatch(url -> url.contains("date=2026-10-25&offset=%2B01%3A00")));
    }

    @Test public void norwayAirQualityIsOptionalButSolarDataIsRequired() throws Exception {
        JSONObject forecast = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 12);
        MetWeatherClient optional = new MetWeatherClient(url -> MetFixtures.response(url, forecast));
        MetWeatherClient.Result result = optional.load(60, 11, "NO", MetFixtures.ZONE, MetFixtures.NOW, ignored -> { });
        assertNull(result.airQuality);
        assertEquals(1, result.notices.size());
        assertTrue(result.notices.get(0).contains("HTTP 404"));
        assertTrue(WeatherMapping.isComplete(WeatherMapping.toBydJson(result, "Oslo", "Oslo")));
        MetWeatherClient required = new MetWeatherClient(url -> {
            if (url.contains("/sunrise/")) throw new java.net.SocketTimeoutException();
            return forecast;
        });
        WeatherErrors.UpdateFailure failure = assertThrows(WeatherErrors.UpdateFailure.class,
                () -> required.load(60, 11, "NO", MetFixtures.ZONE, MetFixtures.NOW, ignored -> { }));
        assertEquals(WeatherErrors.Stage.ASTRONOMY, failure.stage);
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST, failure).startsWith("Sunrise/sunset download timed out"));
    }

    @Test public void incompleteFutureDaysAreRejectedBeforeRequestingAstronomy() throws Exception {
        JSONObject forecast = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 12);
        JSONArray series = forecast.getJSONObject("properties").getJSONArray("timeseries");
        while (series.length() > 30) series.remove(series.length() - 1);
        List<String> urls = new ArrayList<>();
        MetWeatherClient client = new MetWeatherClient(url -> { urls.add(url); return forecast; });
        WeatherErrors.UpdateFailure error = assertThrows(WeatherErrors.UpdateFailure.class,
                () -> client.load(0, 0, "", MetFixtures.ZONE, MetFixtures.NOW, ignored -> { }));
        assertEquals(WeatherErrors.Stage.RESPONSE, error.stage);
        assertEquals(1, urls.size());
    }

    @Test public void retainsEarlierForecastHoursForTodayAcrossRestartsOnlyAtSameLocation() throws Exception {
        long morning = Instant.parse("2026-10-12T03:00:00Z").toEpochMilli();
        long afternoon = morning + 8 * 3600_000L;
        AtomicReference<JSONObject> response = new AtomicReference<>(MetFixtures.forecast(morning, "cloudy", 2));
        File history = new File(temporary.getRoot(), "history.json");
        MetWeatherClient.JsonSource source = url -> MetFixtures.response(url, response.get());
        new MetWeatherClient(source, history).load(31.95, 35.94, "JO", MetFixtures.ZONE, morning, ignored -> { });
        response.set(MetFixtures.forecast(afternoon, "cloudy", 18));
        MetWeatherClient.Result merged = new MetWeatherClient(source, history)
                .load(31.95, 35.94, "JO", MetFixtures.ZONE, afternoon, ignored -> { });
        assertEquals(18, merged.forecast.upcomingHours(afternoon).get(0).temperature, 0);
        assertEquals(2, merged.forecast.summarize(LocalDate.of(2026, 10, 12), MetFixtures.ZONE).minimum);
        assertEquals(18, merged.forecast.summarize(LocalDate.of(2026, 10, 13), MetFixtures.ZONE).minimum);
        MetWeatherClient.Result moved = new MetWeatherClient(source, history)
                .load(32.1, 36.1, "JO", MetFixtures.ZONE, afternoon, ignored -> { });
        assertEquals(18, moved.forecast.summarize(LocalDate.of(2026, 10, 12), MetFixtures.ZONE).minimum);
    }

    @Test public void corruptHistoryDoesNotRejectAFreshValidForecast() throws Exception {
        File history = new File(temporary.getRoot(), "history.json");
        Files.write(history.toPath(), "{\"latitude\":31.95,\"longitude\":35.94,\"forecast\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JSONObject forecast = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 12);
        MetWeatherClient.Result result = new MetWeatherClient(url -> MetFixtures.response(url, forecast), history)
                .load(31.95, 35.94, "JO", MetFixtures.ZONE, MetFixtures.NOW, ignored -> { });
        assertEquals(12, result.forecast.upcomingHours(MetFixtures.NOW).get(0).temperature, 0);
    }

    @Test public void usesBoundedCoordinatePrecisionIndependentlyOfLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("ru", "RU"));
            assertEquals("lat=55.7558&lon=37.6173", MetWeatherClient.coordinates(55.7558123, 37.6173123, 4));
            assertEquals("lat=-33.86&lon=151.20", MetWeatherClient.coordinates(-33.8688, 151.2093, 2));
            assertThrows(IllegalArgumentException.class, () -> MetWeatherClient.coordinates(Double.NaN, 0, 4));
            assertThrows(IllegalArgumentException.class, () -> MetWeatherClient.coordinates(91, 0, 4));
        } finally { Locale.setDefault(previous); }
    }
}
