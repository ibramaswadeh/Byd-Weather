package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the public refresh flow against provider/widget contracts inspected with REA. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@LooperMode(LooperMode.Mode.PAUSED)
public class WeatherWidgetRefreshTest {
    private static final Uri WEATHER_URI = Uri.parse(WeatherRuntime.WEATHER_URI);

    @Test public void refreshReplacesUndecodablePayloadWithReadableForecasts() throws Exception {
        try (RefreshFixture fixture = new RefreshFixture()) {
            JSONObject stored = new JSONObject(payload(forecast("cloudy", 18)));
            stored.getJSONObject("data").getJSONObject("condition").put("realfeel", 13.4);
            fixture.provider.seed(stored.toString());
            JSONObject source = forecast("cloudy", 18);
            JSONObject first = source.getJSONObject("properties").getJSONArray("timeseries")
                    .getJSONObject(0).getJSONObject("data");
            first.getJSONObject("instant").getJSONObject("details")
                    .put("apparent_air_temperature", 13.4).put("cloud_area_fraction", 87.5);
            first.getJSONObject("next_1_hours").getJSONObject("details")
                    .put("probability_of_precipitation", 62.8);
            fixture.forecast.set(source);

            fixture.refresh();

            WeatherForecastScreenTest.NativeWeather weather =
                    WeatherForecastScreenTest.assertForecastRowsDecode(nativeWeatherJson(fixture.context));
            assertEquals(Integer.valueOf(13), weather.data.condition.realfeel);
            assertEquals(Integer.valueOf(88), weather.data.condition.cloudCover);
            assertEquals(Integer.valueOf(63), weather.data.hourlys.hourlyweathers.get(0).rainprobability);
        }
    }

    @Test public void nativeFirstRowReflectsBothSuccessfulRefreshesWhenProviderHasMultipleRows()
            throws Exception {
        try (RefreshFixture fixture = new RefreshFixture()) {
            fixture.provider.seed(payload(forecast("clearsky_day", 11)));
            String untouchedSecondRow = payload(forecast("cloudy", 22));
            long secondRowId = fixture.provider.seed(untouchedSecondRow);

            fixture.forecast.set(forecast("lightrain", 33));
            fixture.refresh();
            JSONObject firstRefresh = new JSONObject(nativeWeatherJson(fixture.context));

            fixture.forecast.set(forecast("lightsnow", 44));
            fixture.refresh();
            JSONObject secondRefresh = new JSONObject(nativeWeatherJson(fixture.context));

            assertEquals("After a successful refresh, native moveToFirst must see the new weather",
                    33, firstRefresh.getJSONObject("data").getJSONObject("condition")
                            .getInt("temperature"));
            assertEquals(7, firstRefresh.getJSONObject("data").getJSONObject("condition")
                    .getInt("cnweatherid"));
            assertEquals("A second refresh must update the row that native readers consume",
                    44, secondRefresh.getJSONObject("data").getJSONObject("condition")
                            .getInt("temperature"));
            assertEquals(14, secondRefresh.getJSONObject("data").getJSONObject("condition")
                    .getInt("cnweatherid"));
            assertEquals("Refreshing the native first row must preserve other stored rows",
                    untouchedSecondRow, fixture.provider.rowPayload(secondRowId));
            assertEquals(2, fixture.provider.rowCount());
        }
    }

    @Test public void nativeTimeFormatObserverSeesFreshWeatherOnEachSuccessfulRefresh()
            throws Exception {
        try (RefreshFixture fixture = new RefreshFixture()) {
            fixture.provider.seed(payload(forecast("clearsky_day", 11)));
            List<String> renderedPayloads = new ArrayList<>();
            // REA: RequestService observes this Settings URI on its main Handler;
            // TimeFormatChangeObserver.onChange immediately requests a widget redraw.
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange, Uri uri) {
                    renderedPayloads.add(nativeWeatherJson(fixture.context));
                }
            };
            fixture.context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("time_12_24"), false, observer);
            try {
                fixture.forecast.set(forecast("lightrain", 33));
                fixture.refresh();
                assertEquals("One redraw notification is required per successful refresh",
                        1, renderedPayloads.size());
                JSONObject firstCondition = new JSONObject(renderedPayloads.get(0))
                        .getJSONObject("data").getJSONObject("condition");
                assertEquals(33, firstCondition.getInt("temperature"));
                assertEquals(7, firstCondition.getInt("cnweatherid"));
                assertEquals("Light rain", firstCondition.getString("weathertext"));

                fixture.forecast.set(forecast("lightsnow", 44));
                fixture.refresh();
                assertEquals("Both committed updates must notify the native observer",
                        2, renderedPayloads.size());
                JSONObject secondCondition = new JSONObject(renderedPayloads.get(1))
                        .getJSONObject("data").getJSONObject("condition");
                assertEquals(44, secondCondition.getInt("temperature"));
                assertEquals(14, secondCondition.getInt("cnweatherid"));
                assertEquals("Light snow", secondCondition.getString("weathertext"));
            } finally {
                fixture.context.getContentResolver().unregisterContentObserver(observer);
            }
        }
    }

    @Test public void clearNightRefreshReplacesStaleDayArtworkInNativeFirstRow() throws Exception {
        long day = java.time.LocalDate.of(2026, 10, 12).atTime(12, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (RefreshFixture fixture = new RefreshFixture(day)) {
            JSONObject clearDay = MetFixtures.forecast(day, "clearsky_day", 25);
            fixture.provider.seed(payload(clearDay));
            fixture.provider.seed(payload(forecast("cloudy", 22)));
            assertEquals("card_sunny", FirmwareWeatherContract.widgetBackground(
                    new JSONObject(nativeWeatherJson(fixture.context)), false));

            AtomicReference<String> redrawPayload = new AtomicReference<>();
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange, Uri uri) {
                    redrawPayload.set(nativeWeatherJson(fixture.context));
                }
            };
            fixture.context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("time_12_24"), false, observer);
            try {
                fixture.clock.set(day + TimeUnit.HOURS.toMillis(10));
                JSONObject clearNight = MetFixtures.forecast(fixture.clock.get(), "clearsky_night", 18);
                fixture.forecast.set(clearNight);
                fixture.refresh();

                String nativePayload = nativeWeatherJson(fixture.context);
                JSONObject weather = new JSONObject(nativePayload);
                assertEquals("A successful nighttime refresh must replace the stale sunny card",
                        "card_night", FirmwareWeatherContract.widgetBackground(weather, false));
                assertEquals("card_night_p", FirmwareWeatherContract.widgetBackground(weather, true));
                assertEquals("Sunny", FirmwareWeatherContract.widgetLabel(weather));
                assertEquals("The native redraw observer must consume the same night payload",
                        nativePayload, redrawPayload.get());
            } finally {
                fixture.context.getContentResolver().unregisterContentObserver(observer);
            }
        }
    }

    @Test public void refreshRejectsHiddenInsertAndPreservesTheCompetingNativeFirstRow()
            throws Exception {
        try (RefreshFixture fixture = new RefreshFixture()) {
            String competingPayload = payload(forecast("clearsky_day", 11));
            fixture.provider.beforeNextInsert = () -> fixture.provider.seed(competingPayload);
            long previousSuccess = fixture.runtime.lastSuccessMs();
            AtomicInteger redrawNotifications = new AtomicInteger();
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange, Uri uri) {
                    redrawNotifications.incrementAndGet();
                }
            };
            fixture.context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor("time_12_24"), false, observer);
            try {
                fixture.refresh(false);
                assertEquals("Rollback must retain the other writer's native-visible payload",
                        competingPayload, nativeWeatherJson(fixture.context));
                assertEquals("Only our hidden insert should be removed", 1, fixture.provider.rowCount());
                assertEquals(previousSuccess, fixture.runtime.lastSuccessMs());
                assertEquals("An unverified native-visible update must not request a redraw",
                        0, redrawNotifications.get());
            } finally {
                fixture.context.getContentResolver().unregisterContentObserver(observer);
            }
        }
    }

    /** REA: WeatherQueryUtil.queryWeatherJson/Bean use null projection/selection/sort. */
    private static String nativeWeatherJson(Context context) {
        try (Cursor cursor = context.getContentResolver().query(WEATHER_URI, null, null, null, null)) {
            assertNotNull(cursor);
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow("name"));
        }
    }

    private static JSONObject forecast(String symbol, int temperature) throws Exception {
        return MetFixtures.forecast(System.currentTimeMillis(), symbol, temperature);
    }

    private static String payload(JSONObject forecast) throws Exception {
        String result = WeatherMapping.toBydJson(MetFixtures.load(forecast,
                MetForecast.parse(forecast).points.get(0).time + 30 * 60_000, java.time.ZoneId.systemDefault()), "Amman", "Amman");
        assertTrue(WeatherMapping.isComplete(result));
        return result;
    }

    private static final class RefreshFixture implements AutoCloseable {
        final Context context = RuntimeEnvironment.getApplication();
        final SQLiteStockProvider provider = new SQLiteStockProvider();
        final AtomicReference<JSONObject> forecast = new AtomicReference<>(forecast("cloudy", 25));
        final LocationManager location;
        final WeatherRuntime runtime;
        final java.util.concurrent.atomic.AtomicLong clock;

        RefreshFixture() throws Exception { this(System.currentTimeMillis()); }

        RefreshFixture(long time) throws Exception {
            clock = new java.util.concurrent.atomic.AtomicLong(time);
            shadowOf(RuntimeEnvironment.getApplication())
                    .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
            ProviderInfo info = new ProviderInfo();
            info.authority = "com.byd.weatherdata.utils.WeatherContentProvider";
            info.exported = true;
            provider.attachInfo(context, info);
            ShadowContentResolver.registerProviderInternal(info.authority, provider);

            location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            shadowOf(location).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
            ConnectivityManager network = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            shadowOf(network).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                    NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true));
            NetworkCapabilities capabilities = new NetworkCapabilities();
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            shadowOf(network).setNetworkCapabilities(network.getActiveNetwork(), capabilities);

            SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            preferences.edit().clear().putBoolean(WeatherRuntime.PREF_ENABLED, true)
                    .putInt(WeatherRuntime.PREF_INTERVAL_MINUTES, 180)
                    .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, clock.get() - 60_000).apply();
            runtime = new WeatherRuntime(context, preferences, null, url -> {
                return MetFixtures.response(url, forecast.get());
            }, clock::get);
            runtime.start();
        }

        void refresh() throws Exception {
            refresh(true);
        }

        void refresh(boolean expectedSuccess) throws Exception {
            AtomicReference<Boolean> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            assertTrue(runtime.requestNow("test", (success, message) -> {
                error.set(message);
                result.set(success);
            }));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (result.get() == null && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                long now = System.currentTimeMillis();
                String time = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)
                        .format(Instant.ofEpochMilli(now));
                shadowOf(location).simulateNmeaMessage(NmeaCoordinatesTest.sentence(
                        "GNGGA," + time + ",4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), now);
                shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertEquals("Refresh result: " + error.get(), Boolean.valueOf(expectedSuccess), result.get());
            shadowOf(Looper.getMainLooper()).idle();
        }

        @Override public void close() {
            runtime.shutdown();
            provider.database.close();
        }
    }

    /** REA: WeatherContentProvider.query forwards ordering directly to SQLite. */
    private static final class SQLiteStockProvider extends ContentProvider {
        SQLiteDatabase database;
        Runnable beforeNextInsert;

        @Override public boolean onCreate() {
            database = SQLiteDatabase.create(null);
            database.execSQL("CREATE TABLE weather (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
            return true;
        }

        long seed(String payload) {
            ContentValues values = new ContentValues();
            values.put("name", payload);
            return database.insertOrThrow("weather", null, values);
        }

        String rowPayload(long id) {
            try (Cursor cursor = database.query("weather", new String[]{"name"},
                    "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
                assertTrue(cursor.moveToFirst());
                return cursor.getString(0);
            }
        }

        int rowCount() {
            try (Cursor cursor = database.query("weather", new String[]{"_id"},
                    null, null, null, null, null)) {
                return cursor.getCount();
            }
        }

        @Override public String getType(Uri uri) { return "vnd.android.cursor.item/weather"; }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            return database.query("weather", projection, selection, selectionArgs, null, null, sortOrder);
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            Runnable beforeInsert = beforeNextInsert;
            beforeNextInsert = null;
            if (beforeInsert != null) beforeInsert.run();
            return ContentUris.withAppendedId(uri, database.insertOrThrow("weather", null, values));
        }

        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return database.update("weather", values, selection, selectionArgs);
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            return database.delete("weather", selection, selectionArgs);
        }
    }
}
