package com.ibramaswadeh.bydweather;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/** Fetches local weather and writes it to the BYD weather provider. */
public final class WeatherRuntime {
    public static final String PREF_ENABLED = "weather_enabled";
    public static final String PREF_INTERVAL_MINUTES = "weather_interval_minutes";
    public static final String PREF_LAST_SUCCESS_MS = "weather_last_success_ms";
    static final String PREF_LAST_LOCATION_NAME = "weather_last_location_name";
    static final String PREF_LAST_LOCATION_METHOD = "weather_last_location_method";
    static final String PREF_LAST_UPDATE_NOTICES = "weather_last_update_notices";
    static final String PREF_AIR_QUALITY = "weather_air_quality";
    public static final int DEFAULT_INTERVAL_MINUTES = 15;
    public static final int MIN_INTERVAL_MINUTES = 5;
    public static final int MAX_INTERVAL_MINUTES = 180;
    public static final int RETRY_INTERVAL_MINUTES = 5;
    public static final String THIRD_REFRESH_ACTION = "com.byd.weatherdata.action.THIRD_REFRESH";
    public static final String WEATHER_URI =
            "content://com.byd.weatherdata.utils.WeatherContentProvider/weather";
    static final String PREF_LANGUAGE = "ui_language";

    enum PrerequisiteState {
        READY,
        WAIT_LOCATION,
        WAIT_NETWORK,
        WAIT_BOTH,
        LOCATION_PERMISSION_DENIED
    }

    /** Success means the stock provider write and readback were verified. */
    public interface ResultCallback {
        void onResult(boolean success, String error);
        default void onProgress(String message) { }
    }

    private final MetWeatherClient weatherClient;
    private final LongSupplier clock;
    private final Context context;
    private final SharedPreferences preferences;
    private final BiConsumer<String, Object[]> eventSink;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "weather-runtime");
        thread.setDaemon(true);
        return thread;
    });
    private final Object stateLock = new Object();
    private volatile boolean started;
    private volatile boolean enabled;
    private volatile boolean inFlight;
    private volatile long generation;
    private volatile long lastSuccessMs;
    private volatile ScheduledFuture<?> scheduled;
    private volatile Future<?> activeFuture;
    private boolean requestPending;
    private boolean prerequisiteCheckQueued;
    private boolean prerequisiteRecheckRequested;
    private long pendingGeneration;
    private String pendingReason;
    private ResultCallback pendingCallback;
    private ResultCallback activeCallback;
    private PrerequisiteState waitState = PrerequisiteState.READY;
    private Location callbackLocation;
    private long locationExpiresAtElapsedMs;
    private long locationRetryAtElapsedMs;
    private final WeatherLocationSource locationSource;
    private ConnectivityManager waitingConnectivityManager;
    private boolean locationCallbackRegistered;
    private boolean networkCallbackRegistered;
    private final ConnectivityManager.NetworkCallback networkWaitCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    prerequisiteChanged("network", null);
                }
                @Override public void onLost(Network network) {
                    prerequisiteChanged("network", null);
                }
                @Override public void onCapabilitiesChanged(
                        Network network, NetworkCapabilities capabilities) {
                    prerequisiteChanged("network", null);
                }
                @Override public void onUnavailable() {
                    prerequisiteChanged("network", null);
                }
            };

    public WeatherRuntime(Context context, SharedPreferences preferences) {
        this(context, preferences, null);
    }

    public WeatherRuntime(Context context, SharedPreferences preferences,
            BiConsumer<String, Object[]> eventSink) {
        this(context, preferences, eventSink, new MetWeatherClient(context.getCacheDir()), System::currentTimeMillis);
    }

    WeatherRuntime(Context context, SharedPreferences preferences,
            BiConsumer<String, Object[]> eventSink, MetWeatherClient.JsonSource source) {
        this(context, preferences, eventSink, new MetWeatherClient(source), System::currentTimeMillis);
    }

    WeatherRuntime(Context context, SharedPreferences preferences,
            BiConsumer<String, Object[]> eventSink, MetWeatherClient.JsonSource source, LongSupplier clock) {
        this(context, preferences, eventSink, new MetWeatherClient(source), clock);
    }

    private WeatherRuntime(Context context, SharedPreferences preferences,
            BiConsumer<String, Object[]> eventSink, MetWeatherClient weatherClient, LongSupplier clock) {
        this.weatherClient = weatherClient;
        this.clock = clock;
        if (context == null || preferences == null) throw new IllegalArgumentException("null runtime argument");
        Context application = context.getApplicationContext();
        this.context = application == null ? context : application;
        this.preferences = preferences;
        this.locationSource = new WeatherLocationSource(this.context);
        this.eventSink = eventSink;
        this.lastSuccessMs = preferences.getLong(PREF_LAST_SUCCESS_MS, 0L);
        // Remove obsolete renderer state and the retired location preference.
        preferences.edit().remove("weather_widget_payload").remove("weather_last_fetch_ms")
                .remove("weather_last_fetch_location_name").remove("weather_location_mode").apply();
    }

    public void start() {
        synchronized (stateLock) {
            if (started) return;
            started = true;
            enabled = preferences.getBoolean(PREF_ENABLED, false);
            if (!enabled) return;
            long dueAt = lastSuccessMs <= 0L ? 0L : lastSuccessMs
                    + TimeUnit.MINUTES.toMillis(intervalMinutes());
            scheduleLocked(Math.max(0L, dueAt - clock.getAsLong()));
        }
    }

    /** Applies refresh settings and stops pending updates when disabled. */
    public void settingsChanged() {
        boolean nowEnabled = preferences.getBoolean(PREF_ENABLED, false);
        boolean unregister = false;
        ResultCallback cancelledCallback = null;
        synchronized (stateLock) {
            int interval = intervalMinutes();
            boolean changed = enabled != nowEnabled;
            enabled = nowEnabled;
            cancelScheduledLocked();
            if (!nowEnabled) {
                cancelledCallback = requestPending ? pendingCallback : activeCallback;
                activeCallback = null;
                generation++;
                clearPendingLocked();
                cancelActiveLocked();
                unregister = true;
                emit("weather_disabled", "reason", "settings");
            } else if (requestPending) {
                schedulePrerequisiteRecheckLocked();
            } else if (started || changed) {
                long dueAt = lastSuccessMs <= 0L ? 0L : lastSuccessMs
                        + TimeUnit.MINUTES.toMillis(interval);
                scheduleLocked(Math.max(0L, dueAt - clock.getAsLong()));
                emit("weather_enabled", "interval_minutes", interval);
            }
        }
        if (unregister) unregisterPrerequisiteCallbacks();
        deliver(cancelledCallback, false, "Weather updates are off.");
    }

    public boolean requestNow(String reason) {
        return requestNow(reason, null);
    }

    /** Queues one refresh at a time while weather updates are enabled. */
    public boolean requestNow(String reason, ResultCallback callback) {
        synchronized (stateLock) {
            if (!started || !enabled || inFlight || requestPending) return false;
            cancelScheduledLocked();
            callbackLocation = null;
            locationRetryAtElapsedMs = 0L;
            requestPending = true;
            pendingGeneration = generation;
            pendingReason = reason == null ? "manual" : reason;
            pendingCallback = callback;
            queuePrerequisiteCheckLocked("request");
            return true;
        }
    }

    public void shutdown() {
        ResultCallback cancelledCallback;
        synchronized (stateLock) {
            if (!started && executor.isShutdown()) return;
            cancelledCallback = requestPending ? pendingCallback : activeCallback;
            activeCallback = null;
            started = false;
            enabled = false;
            generation++;
            cancelScheduledLocked();
            clearPendingLocked();
            cancelActiveLocked();
        }
        unregisterPrerequisiteCallbacks();
        executor.shutdownNow();
        deliver(cancelledCallback, false, "weather shutdown");
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRequestInFlight() {
        return inFlight || requestPending;
    }

    public long lastSuccessMs() {
        return lastSuccessMs;
    }

    public int intervalMinutes() {
        return clampIntervalMinutes(preferences.getInt(
                PREF_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES));
    }

    static long delayAfterResultMs(boolean success, int intervalMinutes) {
        return TimeUnit.MINUTES.toMillis(success
                ? clampIntervalMinutes(intervalMinutes) : RETRY_INTERVAL_MINUTES);
    }

    static int clampIntervalMinutes(int minutes) {
        return Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, minutes));
    }

    private void runRequest(
            long requestGeneration, String reason, ResultCallback callback, Location location) {
        boolean success = false;
        String error = "";
        WeatherErrors.Stage stage = WeatherErrors.Stage.FORECAST;
        try {
            emitCurrent(requestGeneration, "weather_request", "reason", reason);
            if (!isCurrent(requestGeneration)) throw new InterruptedException("weather disabled");
            reportProgress(requestGeneration, "Looking up the city name…");
            City city = geocode(location);
            MetWeatherClient.Result weather = weatherClient.load(location.getLatitude(),
                    location.getLongitude(), city.countryCode, ZoneId.systemDefault(),
                    clock.getAsLong(), message -> reportProgress(requestGeneration, message));
            String notices = city.notice;
            for (String notice : weather.notices) {
                notices += (notices.isEmpty() ? "" : " ") + notice;
            }
            stage = WeatherErrors.Stage.RESPONSE;
            String payload = WeatherMapping.toBydJson(weather, city.localName, city.englishName);
            String locationName = new JSONObject(payload).getJSONObject("data")
                    .getJSONObject("city").getString("name");
            if (!WeatherMapping.isComplete(payload)) throw new IllegalStateException("incomplete BYD payload");
            if (!isCurrent(requestGeneration)) throw new InterruptedException("weather disabled");
            stage = WeatherErrors.Stage.WIDGET;
            reportProgress(requestGeneration, "Updating the BYD weather widget…");
            if (!writeProvider(payload, requestGeneration)) {
                throw new IllegalStateException("Stock widget provider write/readback failed");
            }
            synchronized (stateLock) {
                if (!isCurrent(requestGeneration)) throw new InterruptedException("weather disabled");
                success = true;
                lastSuccessMs = clock.getAsLong();
                preferences.edit().putLong(PREF_LAST_SUCCESS_MS, lastSuccessMs)
                        .putString(PREF_LAST_LOCATION_NAME, locationName)
                        .putString(PREF_LAST_LOCATION_METHOD, locationMethod(location))
                        .putString(PREF_AIR_QUALITY, weather.airQuality == null ? "Unavailable"
                                : weather.airQuality.description() + " health risk · Norwegian index "
                                        + weather.airQuality.formattedIndex())
                        .putString(PREF_LAST_UPDATE_NOTICES, notices).apply();
            }
            emitCurrent(requestGeneration, "weather_success", "last_success_ms", lastSuccessMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            error = "cancelled";
        } catch (Throwable failure) {
            error = WeatherErrors.describe(stage, failure);
            emitCurrent(requestGeneration, "weather_failure", "error", error);
        } finally {
            boolean networkValidated = hasValidatedDefaultNetwork();
            boolean deliverCallback;
            synchronized (stateLock) {
                deliverCallback = ownsGeneration(requestGeneration, generation);
                if (activeCallback == callback) activeCallback = null;
                if (deliverCallback) {
                    inFlight = false;
                    activeFuture = null;
                    if (started && enabled && !executor.isShutdown()) {
                        if (success) {
                            scheduleLocked(delayAfterResultMs(true, intervalMinutes()));
                        } else if (shouldUseFailureTimer(false, networkValidated)) {
                            scheduleLocked(delayAfterResultMs(false, intervalMinutes()));
                        } else {
                            requestPending = true;
                            pendingGeneration = generation;
                            pendingReason = "network_recovery";
                            pendingCallback = null;
                            queuePrerequisiteCheckLocked("fetch_failure");
                        }
                    }
                }
            }
            if (deliverCallback) deliver(callback, success, error);
        }
    }

    private boolean writeProvider(String payload, long requestGeneration) {
        if (!isCurrent(requestGeneration)) return false;
        ContentResolver resolver = context.getContentResolver();
        android.net.Uri uri = android.net.Uri.parse(WEATHER_URI);
        ProviderRow old = null;
        ProviderRow written = null;
        ContentValues values = new ContentValues();
        values.put("name", payload);
        boolean inserted = false;
        try {
            old = readProvider(resolver, uri, null, null);
            if (old != null) {
                String oldRowSelection = old.name == null
                        ? "_id=? AND name IS NULL" : "_id=? AND name=?";
                String[] oldRowArgs = old.name == null
                        ? new String[]{String.valueOf(old.id)}
                        : new String[]{String.valueOf(old.id), old.name};
                int updated = resolver.update(uri, values, oldRowSelection, oldRowArgs);
                if (updated != 1) return false;
                written = new ProviderRow(old.id, payload);
            } else {
                android.net.Uri result = resolver.insert(uri, values);
                if (result == null) return false;
                inserted = true;
                written = readProvider(resolver, uri, "name=?", new String[]{payload});
                if (written == null || !payload.equals(written.name)) {
                    restoreProvider(resolver, uri, old, written, inserted, payload);
                    return false;
                }
            }
            if (!isCurrent(requestGeneration)) {
                restoreProvider(resolver, uri, old, written, inserted, payload);
                return false;
            }
            ProviderRow readback = readProvider(resolver, uri, null, null);
            if (readback == null || readback.id != written.id || !payload.equals(readback.name)) {
                restoreProvider(resolver, uri, old, written, inserted, payload);
                return false;
            }
            synchronized (stateLock) {
                if (!isCurrent(requestGeneration)) {
                    restoreProvider(resolver, uri, old, written, inserted, payload);
                    return false;
                }
                context.sendBroadcast(new android.content.Intent(THIRD_REFRESH_ACTION));
                try {
                    resolver.notifyChange(Settings.System.getUriFor("time_12_24"), null);
                } catch (Throwable failure) {
                    emit("weather_widget_refresh_failure", "error", summary(failure));
                }
            }
            return true;
        } catch (Throwable failure) {
            restoreProvider(resolver, uri, old, written, inserted, payload);
            return false;
        }
    }

    private ProviderRow readProvider(ContentResolver resolver, android.net.Uri uri,
            String selection, String[] selectionArgs) {
        // Match native WeatherQueryUtil's projection and unsorted moveToFirst().
        try (Cursor cursor = resolver.query(uri, null, selection, selectionArgs, null)) {
            if (cursor == null) throw new IllegalStateException("weather provider query failed");
            if (!cursor.moveToFirst()) return null;
            return new ProviderRow(cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
    }

    private void restoreProvider(ContentResolver resolver, android.net.Uri uri,
            ProviderRow old, ProviderRow written, boolean inserted, String payload) {
        try {
            if (old != null) {
                ContentValues restore = new ContentValues();
                restore.put("name", old.name);
                resolver.update(uri, restore, "_id=? AND name=?",
                        new String[]{String.valueOf(old.id), payload});
            }
            if (inserted) {
                if (written == null) {
                    written = readProvider(resolver, uri, "name=?", new String[]{payload});
                }
                if (written != null && payload.equals(written.name)) {
                    resolver.delete(uri, "_id=? AND name=?",
                            new String[]{String.valueOf(written.id), payload});
                }
            }
        } catch (Throwable ignored) {
            // Ignore errors while restoring the previous weather data.
        }
    }

    static PrerequisiteState prerequisiteState(
            boolean locationAuthorized, boolean locationReady, boolean networkReady) {
        if (!locationAuthorized) return PrerequisiteState.LOCATION_PERMISSION_DENIED;
        if (locationReady && networkReady) return PrerequisiteState.READY;
        if (!locationReady && !networkReady) return PrerequisiteState.WAIT_BOTH;
        return locationReady
                ? PrerequisiteState.WAIT_NETWORK : PrerequisiteState.WAIT_LOCATION;
    }

    static boolean shouldUseFailureTimer(boolean success, boolean networkValidated) {
        return !success && networkValidated;
    }

    private void prerequisiteChanged(String trigger, Location location) {
        synchronized (stateLock) {
            if (!started || !enabled || !requestPending || executor.isShutdown()) return;
            if (location != null) callbackLocation = location;
            if (prerequisiteCheckQueued) {
                prerequisiteRecheckRequested = true;
                return;
            }
            queuePrerequisiteCheckLocked(trigger);
        }
    }

    private void queuePrerequisiteCheckLocked(String trigger) {
        if (!started || !enabled || !requestPending || prerequisiteCheckQueued
                || executor.isShutdown()) return;
        prerequisiteCheckQueued = true;
        FutureTask<Void> task = new FutureTask<>(() -> {
            runPrerequisiteCheck(trigger);
            return null;
        });
        activeFuture = task;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            prerequisiteCheckQueued = false;
            activeFuture = null;
        }
    }

    private void runPrerequisiteCheck(String trigger) {
        long requestGeneration;
        synchronized (stateLock) {
            if (!started || !enabled || !requestPending) {
                prerequisiteCheckQueued = false;
                activeFuture = null;
                return;
            }
            requestGeneration = pendingGeneration;
        }

        LocationSnapshot location = currentLocation();
        boolean networkReady = hasValidatedDefaultNetwork();
        PrerequisiteState state = prerequisiteState(
                location.authorized, location.value != null, networkReady);
        String reason = null;
        ResultCallback callback = null;
        boolean startRequest = false;
        boolean authorizationDenied = false;
        boolean readinessRetry = false;
        synchronized (stateLock) {
            if (!started || !enabled || !requestPending
                    || pendingGeneration != requestGeneration) {
                prerequisiteCheckQueued = false;
                activeFuture = null;
                return;
            }
            PrerequisiteState previous = waitState;
            if (state == PrerequisiteState.LOCATION_PERMISSION_DENIED) {
                callback = pendingCallback;
                authorizationDenied = true;
                cancelScheduledLocked();
                clearPendingLocked();
                activeFuture = null;
                unregisterPrerequisiteCallbacksLocked();
            } else if (state != PrerequisiteState.READY) {
                boolean transition = state != previous;
                waitState = state;
                prerequisiteCheckQueued = false;
                activeFuture = null;
                updatePrerequisiteCallbacksLocked(state);
                schedulePrerequisiteRecheckLocked();
                if (transition) {
                    emit("weather_prerequisite_wait", "missing", waitLabel(state));
                }
                if (state == PrerequisiteState.WAIT_NETWORK || state == PrerequisiteState.WAIT_BOTH) {
                    String message = internetWaitMessage();
                    reportProgress(requestGeneration, message);
                    ResultCallback unavailable = pendingCallback;
                    pendingCallback = null;
                    deliver(unavailable, false, message);
                }
                if (prerequisiteRecheckRequested) {
                    prerequisiteRecheckRequested = false;
                    queuePrerequisiteCheckLocked("callback");
                }
                return;
            } else {
                reason = pendingReason;
                callback = pendingCallback;
                readinessRetry = previous != PrerequisiteState.READY;
                requestPending = false;
                pendingReason = null;
                pendingCallback = null;
                pendingGeneration = 0L;
                prerequisiteCheckQueued = false;
                prerequisiteRecheckRequested = false;
                waitState = PrerequisiteState.READY;
                inFlight = true;
                activeCallback = callback;
                callbackLocation = null;
                cancelScheduledLocked();
                unregisterPrerequisiteCallbacksLocked();
                startRequest = true;
            }
        }
        if (authorizationDenied) {
            emit("weather_authorization_required", "permission", "location");
            deliver(callback, false, "Location permission denied. Allow location access in app settings.");
        } else if (startRequest) {
            if (readinessRetry) {
                emit("weather_prerequisite_ready", "trigger", trigger);
            }
            runRequest(requestGeneration, reason, callback, location.value);
        }
    }

    private LocationSnapshot currentLocation() {
        boolean authorized = WeatherLocationSource.hasLocationPermission(context);
        if (!authorized) return new LocationSnapshot(false, null);
        synchronized (stateLock) {
            return new LocationSnapshot(true, callbackLocation != null
                    && SystemClock.elapsedRealtime() <= locationExpiresAtElapsedMs ? callbackLocation : null);
        }
    }

    private boolean hasValidatedDefaultNetwork() {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (SecurityException denied) {
            return false;
        }
    }

    private void updatePrerequisiteCallbacksLocked(PrerequisiteState state) {
        // Start location acquisition only when the internet connection is ready.
        boolean needLocation = state == PrerequisiteState.WAIT_LOCATION;
        boolean needNetwork = state == PrerequisiteState.WAIT_NETWORK
                || state == PrerequisiteState.WAIT_BOTH;
        if (needLocation) registerLocationCallbackLocked();
        else unregisterLocationCallbackLocked();
        if (needNetwork) registerNetworkCallbackLocked();
        else unregisterNetworkCallbackLocked();
    }

    private void registerLocationCallbackLocked() {
        if (locationCallbackRegistered || SystemClock.elapsedRealtime() < locationRetryAtElapsedMs) return;
        locationCallbackRegistered = true;
        long token = pendingGeneration;
        locationSource.acquire(message -> reportProgress(token, message), fix -> {
            synchronized (stateLock) {
                if (!started || !enabled || !requestPending || token != pendingGeneration
                        || !locationCallbackRegistered) return;
                locationCallbackRegistered = false;
                Location coordinates = new Location(fix.provider);
                coordinates.setLatitude(fix.latitude);
                coordinates.setLongitude(fix.longitude);
                if (Float.isFinite(fix.accuracy)) coordinates.setAccuracy(fix.accuracy);
                android.os.Bundle extras = new android.os.Bundle();
                extras.putString("weather_location_method", fix.sourceName);
                coordinates.setExtras(extras);
                locationExpiresAtElapsedMs = fix.expiresAtElapsedMs;
                prerequisiteChanged("location", coordinates);
            }
        }, error -> {
            ResultCallback callback;
            synchronized (stateLock) {
                if (!started || !enabled || !requestPending || token != pendingGeneration
                        || !locationCallbackRegistered) return;
                locationCallbackRegistered = false;
                callbackLocation = null;
                locationRetryAtElapsedMs = SystemClock.elapsedRealtime()
                        + TimeUnit.MINUTES.toMillis(RETRY_INTERVAL_MINUTES);
                callback = pendingCallback;
                pendingCallback = null;
                schedulePrerequisiteRecheckLocked();
            }
            emit("weather_location_unavailable", "error", error);
            deliver(callback, false, error + " Previous weather kept; retrying in 5 minutes.");
        });
    }

    private String internetWaitMessage() {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities capabilities = manager == null ? null
                    : manager.getNetworkCapabilities(manager.getActiveNetwork());
            if (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return "Internet check pending: Android has not confirmed access. Check Wi-Fi/mobile data or the connection's sign-in screen.";
            }
        } catch (SecurityException ignored) { }
        return "No internet connection. Connect the car to Wi-Fi or mobile data; weather will retry when connected.";
    }

    private static String locationMethod(Location location) {
        android.os.Bundle extras = location.getExtras();
        return extras == null ? "GPS" : extras.getString("weather_location_method", "GPS");
    }

    private void reportProgress(long token, String message) {
        synchronized (stateLock) {
            if (!started || !enabled || generation != token) return;
            ResultCallback callback = requestPending ? pendingCallback : activeCallback;
            emit("weather_progress", "message", message);
            if (callback != null) {
                try { callback.onProgress(message); }
                catch (Throwable ignored) { }
            }
        }
    }

    private void emitCurrent(long token, String name, Object... fields) {
        synchronized (stateLock) {
            if (started && enabled && generation == token) emit(name, fields);
        }
    }

    private void registerNetworkCallbackLocked() {
        if (networkCallbackRegistered) return;
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return;
        try {
            manager.registerDefaultNetworkCallback(networkWaitCallback);
            waitingConnectivityManager = manager;
            networkCallbackRegistered = true;
        } catch (RuntimeException ignored) {
        }
    }

    private void unregisterPrerequisiteCallbacks() {
        synchronized (stateLock) {
            unregisterPrerequisiteCallbacksLocked();
        }
    }

    private void unregisterPrerequisiteCallbacksLocked() {
        unregisterLocationCallbackLocked();
        unregisterNetworkCallbackLocked();
    }

    private void unregisterLocationCallbackLocked() {
        if (!locationCallbackRegistered) return;
        locationSource.cancel();
        locationCallbackRegistered = false;
    }

    private void unregisterNetworkCallbackLocked() {
        if (!networkCallbackRegistered) return;
        try {
            waitingConnectivityManager.unregisterNetworkCallback(networkWaitCallback);
        } catch (IllegalArgumentException ignored) {
        }
        waitingConnectivityManager = null;
        networkCallbackRegistered = false;
    }

    private void schedulePrerequisiteRecheckLocked() {
        cancelScheduledLocked();
        try {
            scheduled = executor.schedule(() -> {
                synchronized (stateLock) {
                    scheduled = null;
                    if (requestPending) queuePrerequisiteCheckLocked("safety_recheck");
                }
            }, TimeUnit.MINUTES.toMillis(RETRY_INTERVAL_MINUTES), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            scheduled = null;
        }
    }

    private static String waitLabel(PrerequisiteState state) {
        if (state == PrerequisiteState.WAIT_LOCATION) return "location";
        if (state == PrerequisiteState.WAIT_NETWORK) return "network";
        return "both";
    }

    private void deliver(ResultCallback callback, boolean success, String error) {
        if (callback == null) return;
        try {
            callback.onResult(success, error);
        } catch (Throwable callbackFailure) {
            emit("weather_callback_failure", "error", summary(callbackFailure));
        }
    }

    private City geocode(Location location) {
        String language = Locale.getDefault().getLanguage();
        String fallback = friendlyLocationFallback();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        String failureReason = null;
        try {
            if (SystemClock.elapsedRealtime() > locationExpiresAtElapsedMs) {
                failureReason = "fix_expired";
            } else if (!Geocoder.isPresent()) {
                failureReason = "provider_unavailable";
            }
        } catch (Throwable failure) {
            failureReason = "availability_check_failed:" + summary(failure);
        }
        if (failureReason == null) {
            try {
                List<Address> addresses = new Geocoder(context).getFromLocation(
                        latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String city = firstNonEmpty(address.getLocality(), address.getSubAdminArea(),
                            address.getAdminArea());
                    if (city != null) return new City(city, city, address.getCountryCode(), "");
                    failureReason = "no_usable_name";
                } else {
                    failureReason = "no_result";
                }
            } catch (Throwable failure) {
                failureReason = summary(failure);
            }
        }
        emit("weather_geocoder_fallback", "ui_language", normalizedLanguage(language),
                "latitude", latitude, "longitude", longitude, "reason", failureReason);
        return new City(fallback, fallback, null, "City name unavailable; showing Current location.");
    }

    static String normalizedLanguage(String language) {
        return language == null || language.isEmpty() ? "en" : language;
    }

    private String friendlyLocationFallback() {
        return context.getString(R.string.current_location);
    }

    static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }

    private boolean isCurrent(long requestGeneration) {
        return started && enabled && generation == requestGeneration && !Thread.currentThread().isInterrupted();
    }

    private void scheduleLocked(long delayMs) {
        if (!canScheduleNormal(started, enabled, inFlight, requestPending,
                executor.isShutdown())) return;
        cancelScheduledLocked();
        scheduled = executor.schedule(() -> requestNow("scheduled"), Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
        emit("weather_scheduled", "delay_ms", Math.max(0L, delayMs));
    }

    static boolean canScheduleNormal(boolean started, boolean enabled, boolean inFlight,
            boolean requestPending, boolean executorShutdown) {
        return started && enabled && !inFlight && !requestPending && !executorShutdown;
    }

    private void cancelScheduledLocked() {
        if (scheduled != null) scheduled.cancel(false);
        scheduled = null;
    }

    private void cancelActiveLocked() {
        Future<?> future = activeFuture;
        activeFuture = null;
        inFlight = false;
        if (future != null) future.cancel(true);
    }

    private void clearPendingLocked() {
        callbackLocation = null;
        locationRetryAtElapsedMs = 0L;
        requestPending = false;
        prerequisiteCheckQueued = false;
        prerequisiteRecheckRequested = false;
        pendingGeneration = 0L;
        pendingReason = null;
        pendingCallback = null;
        waitState = PrerequisiteState.READY;
    }

    static boolean ownsGeneration(long requestGeneration, long currentGeneration) {
        return requestGeneration == currentGeneration;
    }

    private void emit(String name, Object... fields) {
        if (eventSink != null) {
            try {
                eventSink.accept(name, fields);
            } catch (Throwable ignored) {
                // Ignore errors from the optional event handler.
            }
        }
    }

    private static String summary(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    private static final class ProviderRow {
        final long id;
        final String name;
        ProviderRow(long id, String name) { this.id = id; this.name = name; }
    }

    private static final class LocationSnapshot {
        final boolean authorized;
        final Location value;

        LocationSnapshot(boolean authorized, Location value) {
            this.authorized = authorized;
            this.value = value;
        }
    }

    private static final class City {
        final String localName;
        final String englishName;
        final String countryCode;
        final String notice;
        City(String localName, String englishName, String countryCode, String notice) {
            this.localName = localName;
            this.englishName = englishName;
            this.countryCode = countryCode;
            this.notice = notice;
        }
    }

}
