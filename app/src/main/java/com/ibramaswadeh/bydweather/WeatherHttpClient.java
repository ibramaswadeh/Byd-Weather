package com.ibramaswadeh.bydweather;

import android.util.AtomicFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;

/** Bounded MET downloads with persistent conditional caching and server backoff. */
final class WeatherHttpClient implements MetWeatherClient.JsonSource {
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    static final String USER_AGENT = "BYDWeather/" + BuildConfig.VERSION_NAME
            + " https://github.com/ibramaswadeh/Byd-Weather";
    private static final int MAX_CACHE_ENTRIES = 96;
    private static final long DAY_MS = Duration.ofDays(1).toMillis();
    private final File directory;
    private final ConnectionFactory connections;
    private final LongSupplier clock;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    WeatherHttpClient(File directory) {
        this(directory, url -> (HttpURLConnection) url.openConnection(), System::currentTimeMillis);
    }

    WeatherHttpClient(File directory, ConnectionFactory connections, LongSupplier clock) {
        this.directory = directory;
        this.connections = connections;
        this.clock = clock;
    }

    @Override public synchronized JSONObject get(String address) throws Exception {
        checkInterrupted();
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !"api.met.no".equals(url.getHost())) {
            throw new IOException("Unsupported weather server");
        }
        String key = cacheKey(address);
        JSONObject cached = readEntry(key);
        checkInterrupted();
        long now = clock.getAsLong();
        if (cached != null && cached.optLong("expiresAt") > now) return cachedBody(cached);
        JSONObject backoff = readEntry("backoff");
        if (backoff != null && backoff.optLong("expiresAt") > now) {
            throw new WeatherErrors.HttpFailure(backoff.optInt("status", 429));
        }
        checkInterrupted();

        HttpURLConnection connection = connections.open(url);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "gzip");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setInstanceFollowRedirects(true);
        if (cached != null && cached.optInt("status") == 200) {
            String modified = cached.optString("lastModified");
            if (!modified.isEmpty()) connection.setRequestProperty("If-Modified-Since", modified);
        }
        try {
            int status = connection.getResponseCode();
            checkInterrupted();
            long expiresAt = expiration(connection, now);
            String cacheControl = connection.getHeaderField("Cache-Control");
            boolean mayStore = cacheControl == null || !cacheControl.toLowerCase(Locale.ROOT).contains("no-store");
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null
                    && cached.optInt("status") == 200) {
                cached.put("expiresAt", expiresAt);
                if (mayStore) writeEntry(key, cached);
                else invalidate(address);
                return cachedBody(cached);
            }
            if (status == 429 || status == 503) {
                String retry = connection.getHeaderField("Retry-After");
                long retryAt = retryAfter(retry, now);
                if (status == 429 || retry != null) {
                    writeEntry("backoff", new JSONObject().put("expiresAt", retryAt).put("status", status));
                }
            }
            if (status < 200 || status >= 300) {
                if (address.contains("/airqualityforecast/")
                        && (status == 400 || status == 404 || status == 422)) {
                    writeEntry(key, new JSONObject().put("status", status)
                            .put("expiresAt", now + DAY_MS));
                }
                throw new WeatherErrors.HttpFailure(status);
            }
            JSONObject body;
            try (InputStream stream = responseStream(connection)) {
                body = readJson(stream);
            }
            checkInterrupted();
            if (mayStore) {
                writeEntry(key, new JSONObject().put("status", 200).put("expiresAt", expiresAt)
                        .put("lastModified", connection.getHeaderField("Last-Modified"))
                        .put("body", body));
            } else invalidate(address);
            return body;
        } finally {
            connection.disconnect();
        }
    }

    @Override public synchronized void invalidate(String url) {
        try { new AtomicFile(new File(directory, cacheKey(url) + ".json")).delete(); }
        catch (Exception ignored) { }
    }

    private static JSONObject cachedBody(JSONObject cached) throws Exception {
        int status = cached.getInt("status");
        if (status != 200) throw new WeatherErrors.HttpFailure(status);
        return new JSONObject(cached.getJSONObject("body").toString());
    }

    static JSONObject readJson(InputStream stream) throws Exception {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        int count;
        while ((count = stream.read(buffer)) != -1) {
            checkInterrupted();
            if (System.nanoTime() > deadline) throw new java.net.SocketTimeoutException("Download deadline exceeded");
            if (count > MAX_RESPONSE_BYTES - body.size()) throw new IOException("Weather response is too large");
            body.write(buffer, 0, count);
        }
        return new JSONObject(body.toString(StandardCharsets.UTF_8.name()));
    }

    private static InputStream responseStream(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getInputStream();
        if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
            try { return new GZIPInputStream(stream); }
            catch (IOException failure) { stream.close(); throw failure; }
        }
        return stream;
    }

    private static long expiration(HttpURLConnection connection, long now) {
        String control = connection.getHeaderField("Cache-Control");
        if (control != null) {
            for (String directive : control.toLowerCase(Locale.ROOT).split(",")) {
                if (directive.trim().equals("no-cache")) return now;
            }
            for (String directive : control.toLowerCase(Locale.ROOT).split(",")) {
                if (!directive.trim().startsWith("max-age=")) continue;
                try {
                    long maxAge = Long.parseLong(directive.trim().substring(8));
                    long age = Math.max(0, connection.getHeaderFieldLong("Age", 0));
                    return now + Math.max(0, Math.min(maxAge - age, 30 * 24 * 3600)) * 1000;
                } catch (NumberFormatException ignored) { }
            }
        }
        long expires = httpDate(connection.getHeaderField("Expires"));
        return expires > now ? expires : now;
    }

    private static long retryAfter(String value, long now) {
        if (value != null) {
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds >= 0) return now + Math.min(seconds, 7 * 24 * 3600) * 1000;
            } catch (NumberFormatException ignored) { }
            long date = httpDate(value);
            if (date > now) return date;
        }
        return now + Duration.ofMinutes(5).toMillis();
    }

    private static long httpDate(String value) {
        if (value == null) return 0;
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
        catch (RuntimeException invalid) { return 0; }
    }

    private JSONObject readEntry(String key) {
        AtomicFile file = new AtomicFile(new File(directory, key + ".json"));
        try (InputStream stream = file.openRead()) {
            JSONObject result = readJson(stream);
            if (!key.equals("backoff") && result.optInt("status") == 200
                    && result.optJSONObject("body") == null) throw new IOException("Incomplete cache");
            return result;
        } catch (InterruptedIOException cancelled) {
            return null;
        } catch (Exception ignored) {
            file.delete();
            return null;
        }
    }

    private void writeEntry(String key, JSONObject entry) {
        if (!directory.isDirectory() && !directory.mkdirs()) return;
        AtomicFile file = new AtomicFile(new File(directory, key + ".json"));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(entry.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            pruneCache();
        } catch (IOException ignored) {
            if (output != null) file.failWrite(output);
        }
    }

    private void pruneCache() {
        File[] entries = directory.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("backoff.json"));
        if (entries == null || entries.length <= MAX_CACHE_ENTRIES) return;
        Arrays.sort(entries, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < entries.length - MAX_CACHE_ENTRIES; i++) new AtomicFile(entries[i]).delete();
    }

    private static String cacheKey(String url) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value & 255));
        return key.toString();
    }

    static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Weather update cancelled");
    }
}
