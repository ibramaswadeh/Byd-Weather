package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Looper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowNetworkInfo;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Public refresh flow, with only the external HTTP/device adapters supplied by the test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@LooperMode(LooperMode.Mode.PAUSED)
public class WeatherRuntimeTest {
    @Test public void stockUpdateFailsAndRetriesWhenNativeProviderIsAbsent() throws Exception {
        exerciseStockRefresh(false);
    }

    @Test public void verifiedStockUpdateKeepsChosenIntervalAndAdvancesItsTimestamp() throws Exception {
        exerciseStockRefresh(true);
    }

    private void exerciseStockRefresh(boolean stockAvailable) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        MemoryStockProvider provider = new MemoryStockProvider();
        if (stockAvailable) {
            android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
            info.authority = "com.byd.weatherdata.utils.WeatherContentProvider";
            info.exported = true;
            provider.attachInfo(context, info);
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, provider);
        }
        LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(location).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        ConnectivityManager network = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(network).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true));
        NetworkCapabilities capabilities = new NetworkCapabilities();
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(network).setNetworkCapabilities(network.getActiveNetwork(), capabilities);
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        long previousNativeSuccess = System.currentTimeMillis() - 60_000;
        preferences.edit().putBoolean(WeatherRuntime.PREF_ENABLED, true)
                .putInt(WeatherRuntime.PREF_INTERVAL_MINUTES, 180)
                .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, previousNativeSuccess)
                .putString("weather_widget_payload", "obsolete renderer cache")
                .putLong("weather_last_fetch_ms", System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)).apply();
        JSONObject forecast;
        try (InputStream stream = getClass().getResourceAsStream("/amman-open-meteo.json")) {
            forecast = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        AtomicReference<Long> scheduledDelay = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicReference<Boolean> result = new AtomicReference<>();
        WeatherRuntime runtime = new WeatherRuntime(context, preferences, (name, fields) -> {
            if (name.equals("weather_scheduled")) scheduledDelay.set(((Number) fields[1]).longValue());
            if (name.equals("weather_failure")) failure.set(String.valueOf(fields[1]));
        }, url -> {
            if (url.contains("bigdatacloud")) return new JSONObject().put("locality", "Al Jubeiha Sub-District").put("city", "Amman");
            if (url.contains("air-quality")) throw new IllegalStateException("optional AQ unavailable");
            return forecast;
        });
        try {
            runtime.start();
            assertTrue(runtime.requestNow("test", (success, error) -> result.set(success)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (result.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                long now = System.currentTimeMillis();
                String time = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(now));
                shadowOf(location).simulateNmeaMessage(NmeaCoordinatesTest.sentence(
                        "GNGGA," + time + ",4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), now);
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertEquals("A stock update needs verified provider readback", Boolean.valueOf(stockAvailable), result.get());
            assertFalse(preferences.contains("weather_widget_payload"));
            assertFalse(preferences.contains("weather_last_fetch_ms"));
            if (stockAvailable) {
                assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(180)), scheduledDelay.get());
                assertNull(failure.get());
                assertTrue(WeatherMapping.isComplete(provider.payload));
                assertEquals("Al Jubeiha, Amman", new JSONObject(provider.payload).getJSONObject("data")
                        .getJSONObject("city").getString("name"));
                assertEquals("Al Jubeiha, Amman", preferences.getString("weather_last_location_name", ""));
                assertTrue(runtime.lastSuccessMs() > previousNativeSuccess);
                assertEquals(runtime.lastSuccessMs(), preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0));
                assertTrue(shadowOf(RuntimeEnvironment.getApplication()).getBroadcastIntents().stream()
                        .anyMatch(intent -> WeatherRuntime.THIRD_REFRESH_ACTION.equals(intent.getAction())));
            } else {
                assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(5)), scheduledDelay.get());
                assertNotNull("Provider failure must be reported", failure.get());
                assertEquals(previousNativeSuccess, runtime.lastSuccessMs());
                assertEquals(previousNativeSuccess, preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0));
            }
        } finally {
            runtime.shutdown();
        }
    }
    /** External Android provider adapter with a single persisted weather row. */
    private static final class MemoryStockProvider extends android.content.ContentProvider {
        String payload;
        @Override public boolean onCreate() { return true; }
        @Override public String getType(android.net.Uri uri) { return "vnd.android.cursor.item/weather"; }
        @Override public android.database.Cursor query(android.net.Uri uri, String[] columns,
                String selection, String[] args, String sortOrder) {
            android.database.MatrixCursor cursor = new android.database.MatrixCursor(new String[]{"_id", "name"});
            if (payload != null) cursor.addRow(new Object[]{1L, payload});
            return cursor;
        }
        @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) {
            payload = values.getAsString("name");
            return android.content.ContentUris.withAppendedId(uri, 1);
        }
        @Override public int update(android.net.Uri uri, android.content.ContentValues values, String selection, String[] args) {
            if (payload == null) return 0;
            payload = values.getAsString("name");
            return 1;
        }
        @Override public int delete(android.net.Uri uri, String selection, String[] args) {
            if (payload == null) return 0;
            payload = null;
            return 1;
        }
    }

}
