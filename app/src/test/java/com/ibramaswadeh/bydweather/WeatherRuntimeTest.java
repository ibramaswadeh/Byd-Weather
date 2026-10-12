package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Looper;
import android.os.SystemClock;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadows.ShadowNetworkInfo;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Public refresh flow, with only the external HTTP/device adapters supplied by the test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, shadows = WeatherRuntimeTest.DeviceGeocoder.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class WeatherRuntimeTest {
    @Before public void configureDeviceGeocoder() {
        DeviceGeocoder.reset();
        Address address = new Address(Locale.ENGLISH);
        address.setLocality("Amman");
        address.setSubLocality("Al Jubeiha");
        address.setSubAdminArea("University District");
        address.setAdminArea("Amman Governorate");
        DeviceGeocoder.addresses = List.of(address);
    }

    @Test public void stockUpdateFailsAndRetriesWhenNativeProviderIsAbsent() throws Exception {
        exerciseStockRefresh(false);
    }

    @Test public void verifiedStockUpdateKeepsChosenIntervalAndAdvancesItsTimestamp() throws Exception {
        exerciseStockRefresh(true);
    }

    @Test @Config(shadows = {WeatherLocationSourceTest.NmeaUnavailableLocationManager.class,
            DeviceGeocoder.class})
    public void stockRefreshUsesLiveGpsCoordinatesWhenNmeaIsUnavailable() throws Exception {
        exerciseStockRefresh(true);
    }

    @Test public void usesSubAdministrativeAreaWhenTheCityIsMissing() throws Exception {
        DeviceGeocoder.addresses.get(0).setLocality("  ");
        exerciseStockRefresh(true, "University District");
    }

    @Test public void usesAdministrativeAreaWhenCityAndSubAreaAreMissing() throws Exception {
        DeviceGeocoder.addresses.get(0).setLocality(null);
        DeviceGeocoder.addresses.get(0).setSubAdminArea(null);
        exerciseStockRefresh(true, "Amman Governorate");
    }

    @Test public void unavailableDeviceGeocoderDoesNotBlockWeatherUpdates() throws Exception {
        DeviceGeocoder.present = false;
        exerciseStockRefresh(true, currentLocationLabel());
    }

    @Test public void emptyGeocoderResultUsesCurrentLocation() throws Exception {
        DeviceGeocoder.addresses = List.of();
        exerciseStockRefresh(true, currentLocationLabel());
    }

    @Test public void geocoderResultWithoutUsableNamesUsesCurrentLocation() throws Exception {
        DeviceGeocoder.addresses = List.of(new Address(Locale.ENGLISH));
        exerciseStockRefresh(true, currentLocationLabel());
    }

    @Test public void geocoderFailureDoesNotBlockWeatherUpdates() throws Exception {
        DeviceGeocoder.fail = true;
        exerciseStockRefresh(true, currentLocationLabel());
    }

    @Test public void preservesTheLocalizedCityReturnedByTheDevice() throws Exception {
        DeviceGeocoder.addresses.get(0).setLocality("عمّان");
        exerciseStockRefresh(true, "عمّان");
    }

    @Test public void networkOnlyRefreshUsesTheNetworkFixWithApproximatePermission() throws Exception {
        DeviceGeocoder.addresses.get(0).setLocality("Москва");
        exerciseStockRefresh(true, "Москва", true, null);
    }

    @Test public void forecastConnectionFailureKeepsPreviousWeatherAndIdentifiesDns() throws Exception {
        exerciseStockRefresh(true, "Amman", false,
                new IOException("private request", new java.net.UnknownHostException("private request")));
    }

    @Test public void apiRateLimitKeepsPreviousWeatherAndReportsRetryAdvice() throws Exception {
        exerciseStockRefresh(true, "Amman", false, new WeatherErrors.HttpFailure(429));
    }

    @Test public void changingLocationModeCancelsOldGpsAndUsesOnlyTheReplacementNetworkFix() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        MemoryStockProvider provider = new MemoryStockProvider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "com.byd.weatherdata.utils.WeatherContentProvider";
        info.exported = true;
        provider.attachInfo(context, info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority, provider);
        LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(location).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        shadowOf(location).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        ConnectivityManager network = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(network).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true));
        NetworkCapabilities capabilities = new NetworkCapabilities();
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(network).setNetworkCapabilities(network.getActiveNetwork(), capabilities);
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        long previousSuccess = System.currentTimeMillis() - 60_000;
        preferences.edit().putBoolean(WeatherRuntime.PREF_ENABLED, true)
                .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, previousSuccess)
                .putInt(WeatherRuntime.PREF_INTERVAL_MINUTES, 180).apply();
        JSONObject forecast;
        try (InputStream stream = getClass().getResourceAsStream("/amman-open-meteo.json")) {
            forecast = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<String> requests = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch oldDownloadStarted = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<Boolean> oldResult = new AtomicReference<>();
        WeatherRuntime runtime = new WeatherRuntime(context, preferences, (name, fields) -> {
            if (name.equals("weather_failure")) failures.add(String.valueOf(fields[1]));
        }, url -> {
            requests.add(url);
            if (url.contains("latitude=10.000000")) {
                oldDownloadStarted.countDown();
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch (InterruptedException cancelled) { throw new IOException("cancelled old request", cancelled); }
            }
            if (url.contains("air-quality")) throw new IOException("optional air quality unavailable");
            return forecast;
        });
        try {
            runtime.start();
            assertTrue(runtime.requestNow("test", (success, error) -> oldResult.set(success)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (shadowOf(location).getLocationUpdateListeners().isEmpty() && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertEquals(1, shadowOf(location).getLocationUpdateListeners().size());
            android.location.LocationListener oldCallback = shadowOf(location).getLocationUpdateListeners().get(0);
            Location oldGps = new Location(LocationManager.GPS_PROVIDER);
            oldGps.setLatitude(10);
            oldGps.setLongitude(20);
            oldGps.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            oldCallback.onLocationChanged(oldGps);
            assertTrue(oldDownloadStarted.await(5, TimeUnit.SECONDS));
            preferences.edit().putString(WeatherRuntime.PREF_LOCATION_MODE, "network").apply();
            runtime.settingsChanged();
            assertEquals(Boolean.FALSE, oldResult.get());
            oldCallback.onLocationChanged(oldGps);
            shadowOf(Looper.getMainLooper()).idle();
            assertNull(provider.payload);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0) == previousSuccess
                    && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Location fix = new Location(LocationManager.NETWORK_PROVIDER);
                fix.setLatitude(31.9539);
                fix.setLongitude(35.9106);
                fix.setAccuracy(100);
                fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                android.os.Bundle extras = new android.os.Bundle();
                extras.putString("netType", "wf");
                fix.setExtras(extras);
                shadowOf(location).simulateLocation(fix);
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertTrue(runtime.lastSuccessMs() > previousSuccess);
            assertEquals("Wi-Fi", preferences.getString(WeatherRuntime.PREF_LAST_LOCATION_METHOD, ""));
            assertTrue(requests.size() >= 2);
            assertTrue(requests.get(0).contains("latitude=10.000000&longitude=20.000000"));
            assertTrue(requests.subList(1, requests.size()).stream()
                    .allMatch(url -> url.contains("latitude=31.953900&longitude=35.910600")));
            assertTrue("Cancelled downloads must not replace the current update's status", failures.isEmpty());
            assertTrue(WeatherMapping.isComplete(provider.payload));
            assertTrue(shadowOf(location).getLocationUpdateListeners().isEmpty());
        } finally {
            runtime.shutdown();
        }
    }

    @Test public void unvalidatedInternetReportsThePrerequisiteWithoutStartingLocationOrHttp() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        ConnectivityManager network = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(network).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true));
        NetworkCapabilities capabilities = new NetworkCapabilities();
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(network).setNetworkCapabilities(network.getActiveNetwork(), capabilities);
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        preferences.edit().putBoolean(WeatherRuntime.PREF_ENABLED, true)
                .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, System.currentTimeMillis()).apply();
        AtomicReference<String> result = new AtomicReference<>();
        WeatherRuntime runtime = new WeatherRuntime(context, preferences, null,
                url -> { fail("Internet readiness must be checked before HTTP"); return null; });
        try {
            runtime.start();
            assertTrue(runtime.requestNow("test", (success, error) -> result.set(error)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (result.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertNotNull(result.get());
            assertTrue(result.get().contains("Internet check pending"));
            LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            assertTrue(shadowOf(location).getLocationUpdateListeners().isEmpty());
            assertTrue(runtime.isRequestInFlight());
        } finally {
            runtime.shutdown();
        }
    }

    private void exerciseStockRefresh(boolean stockAvailable) throws Exception {
        exerciseStockRefresh(stockAvailable, "Amman");
    }

    private static String currentLocationLabel() {
        return RuntimeEnvironment.getApplication().getString(R.string.current_location);
    }

    private void exerciseStockRefresh(boolean stockAvailable, String expectedName) throws Exception {
        exerciseStockRefresh(stockAvailable, expectedName, false, null);
    }

    private void exerciseStockRefresh(boolean stockAvailable, String expectedName,
            boolean networkOnly, Exception forecastFailure) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        if (networkOnly) {
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
            shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        }
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
        shadowOf(location).setProviderEnabled(LocationManager.NETWORK_PROVIDER, networkOnly);
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
                .putString(WeatherRuntime.PREF_LOCATION_MODE, networkOnly ? "network" : "automatic")
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
        AtomicReference<String> geocoderFallback = new AtomicReference<>();
        List<String> requestedUrls = new CopyOnWriteArrayList<>();
        List<String> progress = new CopyOnWriteArrayList<>();
        WeatherRuntime runtime = new WeatherRuntime(context, preferences, (name, fields) -> {
            if (name.equals("weather_scheduled")) scheduledDelay.set(((Number) fields[1]).longValue());
            if (name.equals("weather_failure")) failure.set(String.valueOf(fields[1]));
            if (name.equals("weather_geocoder_fallback")) {
                geocoderFallback.set(String.valueOf(fields[fields.length - 1]));
            }
        }, url -> {
            requestedUrls.add(url);
            if (!url.contains("open-meteo.com")) {
                throw new IllegalStateException("City names must come from the device Geocoder");
            }
            if (url.contains("air-quality")) throw new IllegalStateException("optional AQ unavailable");
            if (forecastFailure != null) throw forecastFailure;
            return forecast;
        });
        try {
            runtime.start();
            assertTrue(runtime.requestNow("test", new WeatherRuntime.ResultCallback() {
                @Override public void onProgress(String message) { progress.add(message); }
                @Override public void onResult(boolean success, String error) { result.set(success); }
            }));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (result.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                Location fix = new Location(networkOnly ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER);
                fix.setLatitude(31.9539);
                fix.setLongitude(35.9106);
                fix.setTime(System.currentTimeMillis());
                fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                if (networkOnly) {
                    fix.setAccuracy(250);
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putString("networkLocationType", "wifi");
                    fix.setExtras(extras);
                }
                shadowOf(location).simulateLocation(fix);
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            boolean shouldSucceed = stockAvailable && forecastFailure == null;
            assertEquals("A stock update needs a forecast and verified provider readback", Boolean.valueOf(shouldSucceed), result.get());
            assertTrue(progress.contains("Looking up the city name…"));
            assertTrue(progress.contains("Downloading the weather forecast…"));
            assertFalse("The GPS fix must drive a weather request", requestedUrls.isEmpty());
            for (String url : requestedUrls) {
                assertTrue(url, url.contains("latitude=31.953900&longitude=35.910600"));
                assertTrue("HTTP requests must only fetch weather or air quality: " + url,
                        url.contains("open-meteo.com"));
            }
            if (DeviceGeocoder.present) {
                double[] query = DeviceGeocoder.lastQuery.get();
                assertNotNull("Android Geocoder must receive the live GPS fix", query);
                assertArrayEquals(new double[]{31.9539, 35.9106, 1}, query, 0.000001);
            } else {
                assertNull(DeviceGeocoder.lastQuery.get());
            }
            if (expectedName.equals(currentLocationLabel())) assertNotNull(geocoderFallback.get());
            else assertNull(geocoderFallback.get());
            assertTrue("One-shot GPS must be released before the update finishes",
                    shadowOf(location).getLocationUpdateListeners().isEmpty());
            assertFalse(preferences.contains("weather_widget_payload"));
            assertFalse(preferences.contains("weather_last_fetch_ms"));
            if (shouldSucceed) {
                assertTrue(progress.contains("Updating the BYD weather widget…"));
                assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(180)), scheduledDelay.get());
                assertNull(failure.get());
                assertTrue(WeatherMapping.isComplete(provider.payload));
                assertEquals(expectedName, new JSONObject(provider.payload).getJSONObject("data")
                        .getJSONObject("city").getString("name"));
                assertEquals(expectedName, new JSONObject(provider.payload).getJSONObject("data")
                        .getJSONObject("city").getString("englishCityName"));
                assertEquals(expectedName, preferences.getString("weather_last_location_name", ""));
                assertEquals(networkOnly ? "Wi-Fi" : "GPS",
                        preferences.getString(WeatherRuntime.PREF_LAST_LOCATION_METHOD, ""));
                assertTrue(preferences.getString(WeatherRuntime.PREF_LAST_UPDATE_NOTICES, "").contains("Air quality unavailable"));
                assertTrue(runtime.lastSuccessMs() > previousNativeSuccess);
                assertEquals(runtime.lastSuccessMs(), preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0));
                assertTrue(shadowOf(RuntimeEnvironment.getApplication()).getBroadcastIntents().stream()
                        .anyMatch(intent -> WeatherRuntime.THIRD_REFRESH_ACTION.equals(intent.getAction())));
            } else {
                assertEquals(Long.valueOf(TimeUnit.MINUTES.toMillis(5)), scheduledDelay.get());
                assertNotNull("Provider failure must be reported", failure.get());
                if (forecastFailure instanceof WeatherErrors.HttpFailure) assertTrue(failure.get().contains("HTTP 429"));
                else if (forecastFailure != null) {
                    assertTrue(failure.get().contains("DNS"));
                    assertFalse(failure.get().contains("private request"));
                } else assertTrue(failure.get().contains("BYD widget"));
                assertEquals(previousNativeSuccess, runtime.lastSuccessMs());
                assertEquals(previousNativeSuccess, preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0));
            }
        } finally {
            runtime.shutdown();
        }
    }

    /** External Android geocoding service; refresh and payload construction remain real. */
    @Implements(Geocoder.class)
    public static class DeviceGeocoder {
        static volatile boolean present;
        static volatile boolean fail;
        static volatile List<Address> addresses;
        static final AtomicReference<double[]> lastQuery = new AtomicReference<>();

        @Implementation protected static boolean isPresent() { return present; }

        @Implementation protected List<Address> getFromLocation(double latitude, double longitude,
                int maxResults) throws IOException {
            lastQuery.set(new double[]{latitude, longitude, maxResults});
            if (fail) throw new IOException("Device geocoding unavailable");
            return addresses;
        }

        @Resetter public static void reset() {
            present = true;
            fail = false;
            addresses = List.of();
            lastQuery.set(null);
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
