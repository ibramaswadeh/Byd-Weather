package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Looper;
import android.widget.TextView;
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
    @Test public void successfulForecastKeepsChosenIntervalWhenNativeProviderIsAbsent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
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
                .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, previousNativeSuccess).apply();
        JSONObject forecast;
        try (InputStream stream = getClass().getResourceAsStream("/amman-open-meteo.json")) {
            forecast = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        AtomicReference<Long> scheduledDelay = new AtomicReference<>();
        AtomicReference<String> nativeStatus = new AtomicReference<>();
        AtomicReference<Boolean> result = new AtomicReference<>();
        AppWidgetManager widgets = AppWidgetManager.getInstance(context);
        int widgetId = shadowOf(widgets).createWidget(WeatherWidgetProvider.class, R.layout.widget_weather);
        WeatherRuntime runtime = new WeatherRuntime(context, preferences, (name, fields) -> {
            if (name.equals("weather_scheduled")) scheduledDelay.set(((Number) fields[1]).longValue());
            if (name.equals("weather_native_sync_unavailable")) nativeStatus.set(name);
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
            assertTrue(WeatherMapping.isComplete(preferences.getString("weather_widget_payload", null)));
            assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(180)), scheduledDelay.get());
            assertTrue("Valid forecast completes successfully", Boolean.TRUE.equals(result.get()));
            assertEquals("weather_native_sync_unavailable", nativeStatus.get());
            assertTrue(preferences.getLong(WeatherRuntime.PREF_LAST_FETCH_MS, 0) > previousNativeSuccess);
            assertEquals(previousNativeSuccess, runtime.lastSuccessMs());
            assertEquals(previousNativeSuccess, preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0));
            assertTrue(WeatherMapping.isComplete(preferences.getString("weather_widget_payload", null)));
            assertEquals("Al Jubeiha, Amman", ((TextView) shadowOf(widgets).getViewFor(widgetId)
                    .findViewById(R.id.widget_city)).getText().toString());
        } finally {
            runtime.shutdown();
        }
    }
}
