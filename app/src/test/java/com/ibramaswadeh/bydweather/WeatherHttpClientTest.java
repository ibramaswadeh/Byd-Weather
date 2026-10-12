package com.ibramaswadeh.bydweather;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherHttpClientTest {
    private static final String FORECAST = MetWeatherClient.API_ROOT + "locationforecast/2.0/complete?lat=60.0000&lon=11.0000";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final AtomicLong clock = new AtomicLong(MetFixtures.NOW);
    private final AtomicInteger requests = new AtomicInteger();
    private final Queue<FakeConnection> responses = new ArrayDeque<>();

    @Test public void cachedResponseSurvivesRestartAndIsRevalidatedAfterExpiry() throws Exception {
        FakeConnection first = response(200, "{\"temperature\":12}");
        first.headers.put("Expires", httpDate(clock.get() + 60_000));
        first.headers.put("Last-Modified", httpDate(clock.get() - 60_000));
        WeatherHttpClient client = client();
        client.get(FORECAST).put("temperature", 100);
        assertEquals(12, client().get(FORECAST).getInt("temperature"));
        assertEquals(1, requests.get());
        assertEquals(WeatherHttpClient.USER_AGENT, first.getRequestProperty("User-Agent"));
        assertEquals("gzip", first.getRequestProperty("Accept-Encoding"));
        assertEquals(8_000, first.getConnectTimeout());
        assertEquals(12_000, first.getReadTimeout());
        assertTrue(first.disconnected);
        clock.addAndGet(61_000);
        FakeConnection unchanged = response(304, "");
        unchanged.headers.put("Expires", httpDate(clock.get() + 120_000));
        assertEquals(12, client().get(FORECAST).getInt("temperature"));
        assertEquals(first.headers.get("Last-Modified"), unchanged.getRequestProperty("If-Modified-Since"));
        assertEquals(12, client().get(FORECAST).getInt("temperature"));
        assertEquals(2, requests.get());
    }

    @Test public void honorsNoStoreAndCacheControlRevalidation() throws Exception {
        FakeConnection first = response(200, "{\"value\":1}");
        first.headers.put("Expires", httpDate(clock.get() + 3600_000));
        first.headers.put("Cache-Control", "no-store");
        FakeConnection second = response(200, "{\"value\":2}");
        second.headers.put("Expires", httpDate(clock.get() + 3600_000));
        second.headers.put("Cache-Control", "no-cache");
        response(200, "{\"value\":3}");
        WeatherHttpClient client = client();
        assertEquals(1, client.get(FORECAST).getInt("value"));
        assertEquals(2, client.get(FORECAST).getInt("value"));
        assertEquals(3, client.get(FORECAST).getInt("value"));
        assertEquals(3, requests.get());
    }

    @Test public void rateLimitAndRetryAfterSurviveRestartWithoutAnotherRequest() throws Exception {
        FakeConnection limit = response(429, "private response");
        limit.headers.put("Retry-After", "120");
        assertEquals(429, assertThrows(WeatherErrors.HttpFailure.class, () -> client().get(FORECAST)).status);
        assertEquals(429, assertThrows(WeatherErrors.HttpFailure.class, () -> client().get(FORECAST)).status);
        assertEquals(1, requests.get());
        clock.addAndGet(121_000);
        response(200, "{}");
        assertNotNull(client().get(FORECAST));
        assertEquals(2, requests.get());
    }

    @Test public void cachesUnsupportedAirQualityWithoutBlockingWeather() throws Exception {
        String aqi = MetWeatherClient.API_ROOT + "airqualityforecast/0.1/?lat=60&lon=32";
        response(422, "unsupported location");
        assertEquals(422, assertThrows(WeatherErrors.HttpFailure.class, () -> client().get(aqi)).status);
        assertEquals(422, assertThrows(WeatherErrors.HttpFailure.class, () -> client().get(aqi)).status);
        assertEquals(1, requests.get());
        response(200, "{}");
        client().get(FORECAST);
        assertEquals(2, requests.get());
    }

    @Test public void decompressesGzipAndBoundsExpandedResponses() throws Exception {
        FakeConnection compressed = response(200, "");
        compressed.headers.put("Content-Encoding", "gzip");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)); }
        compressed.body = bytes.toByteArray();
        assertTrue(client().get(FORECAST).getBoolean("ok"));
        assertThrows(java.io.IOException.class, () -> WeatherHttpClient.readJson(
                new ByteArrayInputStream(new byte[WeatherHttpClient.MAX_RESPONSE_BYTES + 1])));
    }

    @Test public void malformedJsonIsNotCachedAndInvalidationForcesAnotherDownload() throws Exception {
        response(200, "<html>maintenance</html>");
        WeatherHttpClient client = client();
        assertThrows(org.json.JSONException.class, () -> client.get(FORECAST));
        FakeConnection valid = response(200, "{\"value\":1}");
        valid.headers.put("Expires", httpDate(clock.get() + 3600_000));
        client.get(FORECAST);
        client.invalidate(FORECAST);
        response(200, "{\"value\":2}");
        assertEquals(2, client.get(FORECAST).getInt("value"));
        assertEquals(3, requests.get());
    }

    @Test public void cancellationAndUnsupportedHostsNeverOpenAConnection() throws Exception {
        WeatherHttpClient client = client();
        assertThrows(java.io.IOException.class, () -> client.get("https://example.com/weather"));
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedIOException.class, () -> client.get(FORECAST)); }
        finally { Thread.interrupted(); }
        assertEquals(0, requests.get());
    }

    private WeatherHttpClient client() {
        return new WeatherHttpClient(temporary.getRoot(), url -> {
            requests.incrementAndGet();
            FakeConnection next = responses.poll();
            assertNotNull("Unexpected HTTP request", next);
            return next;
        }, clock::get);
    }

    private FakeConnection response(int status, String body) throws Exception {
        FakeConnection result = new FakeConnection(status, body);
        responses.add(result);
        return result;
    }

    private static String httpDate(long time) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC));
    }

    private static final class FakeConnection extends HttpURLConnection {
        final Map<String, String> headers = new HashMap<>();
        byte[] body;
        boolean disconnected;
        FakeConnection(int status, String content) throws Exception {
            super(new URL(FORECAST));
            responseCode = status;
            body = content.getBytes(StandardCharsets.UTF_8);
        }
        @Override public int getResponseCode() { return responseCode; }
        @Override public String getHeaderField(String key) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey().equalsIgnoreCase(key)) return header.getValue();
            }
            return null;
        }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
