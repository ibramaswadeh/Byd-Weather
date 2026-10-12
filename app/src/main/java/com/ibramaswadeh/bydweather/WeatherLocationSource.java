package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Collects one recent location, preferring GPS or requesting the network provider directly. */
final class WeatherLocationSource {
    enum Mode {
        AUTOMATIC("automatic"), NETWORK_ONLY("network");

        final String preference;
        Mode(String preference) { this.preference = preference; }
        static Mode fromPreference(String value) {
            return NETWORK_ONLY.preference.equals(value) ? NETWORK_ONLY : AUTOMATIC;
        }
    }

    static final class Fix {
        final double latitude;
        final double longitude;
        final long expiresAtElapsedMs;
        final String provider;
        final String sourceName;
        final float accuracy;

        private Fix(double latitude, double longitude, long expiresAtElapsedMs,
                String provider, String sourceName, float accuracy) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
            this.provider = provider;
            this.sourceName = sourceName;
            this.accuracy = accuracy;
        }
    }

    static final long ACQUISITION_TIMEOUT_MS = 60_000L;
    static final long NETWORK_FALLBACK_DELAY_MS = 10_000L;
    static final long NETWORK_MAX_AGE_MS = 60_000L;
    static final float NETWORK_MAX_ACCURACY_METERS = 25_000f;
    private static final long FUTURE_TOLERANCE_NANOS = 2_000_000_000L;
    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private OnNmeaMessageListener nmea;
    private LocationListener gps;
    private LocationListener network;
    private Runnable timeout;
    private Runnable fallback;
    private String gpsProblem;
    private String networkProblem;
    private boolean networkAttempted;

    WeatherLocationSource(Context context) {
        this.context = context;
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    void acquire(Mode mode, Consumer<String> progress, Consumer<Fix> success,
            Consumer<String> failure) {
        long token = generation.incrementAndGet();
        handler.post(() -> {
            if (token != generation.get()) return;
            release();
            gpsProblem = null;
            networkProblem = null;
            networkAttempted = false;
            if (manager == null) {
                fail(token, "Location service unavailable on this device.", failure);
                return;
            }
            if (!hasLocationPermission(context)) {
                fail(token, "Location permission denied. Allow location access in app settings.", failure);
                return;
            }
            timeout = () -> fail(token, failureMessage(mode), failure);
            handler.postDelayed(timeout, ACQUISITION_TIMEOUT_MS);
            if (mode == Mode.NETWORK_ONLY || !startGps(token, progress, success, failure)) {
                if (token == generation.get()) startNetwork(token, mode, progress, success, failure);
            } else if (token == generation.get()) {
                fallback = () -> startNetwork(token, mode, progress, success, failure);
                handler.postDelayed(fallback, NETWORK_FALLBACK_DELAY_MS);
            }
        });
    }

    static boolean hasLocationPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean startGps(long token, Consumer<String> progress, Consumer<Fix> success,
            Consumer<String> failure) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            gpsProblem = "precise GPS permission is not granted";
            return false;
        }
        try {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsProblem = "GPS is off";
                return false;
            }
            progress.accept("Getting a GPS location…");
            nmea = (sentence, timestamp) -> {
                if (token != generation.get()) return;
                NmeaCoordinates coordinates = NmeaCoordinates.parse(sentence, timestamp,
                        System.currentTimeMillis());
                if (coordinates == null) return;
                complete(token, new Fix(coordinates.latitude, coordinates.longitude,
                        SystemClock.elapsedRealtime() + NmeaCoordinates.MAX_AGE_MS
                                - coordinates.ageAtReceiptMs, LocationManager.GPS_PROVIDER,
                        "GPS", Float.NaN), success);
            };
            // Raw-message access is optional; GPS callbacks can work without it.
            try { manager.addNmeaListener(nmea, handler); }
            catch (RuntimeException unavailable) { }
            if (token != generation.get()) return true;
            gps = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    if (token != generation.get()) return;
                    Fix fix = fromGps(location);
                    if (fix != null) complete(token, fix, success);
                }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) {
                    if (token != generation.get() || !LocationManager.GPS_PROVIDER.equals(provider)) return;
                    gpsProblem = "GPS was turned off";
                    releaseGps();
                    startNetwork(token, Mode.AUTOMATIC, progress, success, failure);
                    if (token == generation.get() && network == null) {
                        fail(token, failureMessage(Mode.AUTOMATIC), failure);
                    }
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            };
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f,
                    gps, Looper.getMainLooper());
            return true;
        } catch (SecurityException denied) {
            gpsProblem = "GPS access was denied";
        } catch (RuntimeException unavailable) {
            gpsProblem = "GPS provider is unavailable";
        }
        releaseGps();
        return false;
    }

    private void startNetwork(long token, Mode mode, Consumer<String> progress,
            Consumer<Fix> success, Consumer<String> failure) {
        if (token != generation.get() || networkAttempted) return;
        networkAttempted = true;
        try {
            if (!manager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                networkProblem = "Wi-Fi/cell location provider is unavailable";
            } else if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                networkProblem = "Wi-Fi/cell location is off";
            } else {
                progress.accept(mode == Mode.NETWORK_ONLY ? "Getting a Wi-Fi/cell location…"
                        : gpsProblem == null ? "GPS has no fix yet. Trying Wi-Fi/cell location…"
                        : "GPS unavailable: " + gpsProblem + ". Trying Wi-Fi/cell location…");
                network = new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        if (token != generation.get()) return;
                        try {
                            String rejection = networkRejection(location);
                            if (rejection != null) {
                                networkProblem = rejection;
                                return;
                            }
                            Fix fix = fromLocation(location, NETWORK_MAX_AGE_MS,
                                    LocationManager.NETWORK_PROVIDER, networkSourceName(location));
                            if (fix != null) complete(token, fix, success);
                        } catch (RuntimeException invalidMetadata) {
                            networkProblem = "Wi-Fi/cell provider returned unreadable location data";
                        }
                    }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) {
                        if (token != generation.get() || !LocationManager.NETWORK_PROVIDER.equals(provider)) return;
                        networkProblem = "Wi-Fi/cell location was turned off";
                        releaseNetwork();
                        if (gps == null) fail(token, failureMessage(mode), failure);
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                };
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1_000L, 0f,
                        network, Looper.getMainLooper());
                return;
            }
        } catch (SecurityException denied) {
            networkProblem = "Wi-Fi/cell location access was denied";
        } catch (RuntimeException unavailable) {
            networkProblem = "Wi-Fi/cell location provider could not start";
        }
        releaseNetwork();
        if (gps == null) fail(token, failureMessage(mode), failure);
    }

    private String failureMessage(Mode mode) {
        String radio = networkProblem == null ? "no recent Wi-Fi/cell fix within 60 seconds" : networkProblem;
        if (mode == Mode.NETWORK_ONLY) return "Location unavailable: " + radio + ". Check Android location settings and internet access.";
        String satellite = gpsProblem == null ? "no recent GPS fix within 60 seconds" : gpsProblem;
        return "Location unavailable: " + satellite + "; " + radio + ". Check location settings.";
    }

    private static Fix fromGps(Location location) {
        if (location == null || !LocationManager.GPS_PROVIDER.equals(location.getProvider())) return null;
        return fromLocation(location, NmeaCoordinates.MAX_AGE_MS, LocationManager.GPS_PROVIDER, "GPS");
    }

    private static Fix fromLocation(Location location, long maximumAgeMs, String provider, String sourceName) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        long fixNanos = location.getElapsedRealtimeNanos();
        // Preserve the provider's monotonic fix age when calculating its lifetime.
        if (fixNanos <= 0 || fixNanos - nowNanos > FUTURE_TOLERANCE_NANOS) return null;
        long ageNanos = Math.max(0L, nowNanos - fixNanos);
        if (ageNanos > maximumAgeMs * 1_000_000L) return null;
        return new Fix(latitude, longitude, SystemClock.elapsedRealtime()
                + maximumAgeMs - ageNanos / 1_000_000L, provider, sourceName,
                location.hasAccuracy() ? location.getAccuracy() : Float.NaN);
    }

    private static String networkRejection(Location location) {
        if (location == null || !LocationManager.NETWORK_PROVIDER.equals(location.getProvider())) {
            return "Wi-Fi/cell provider returned a different location source";
        }
        if (location.isFromMockProvider()) return "Wi-Fi/cell provider returned a mock location";
        String source = radioSource(location);
        if ("gps".equals(source)) return "Wi-Fi/cell provider returned a GPS-derived location";
        if (!location.hasAccuracy() || !Float.isFinite(location.getAccuracy())
                || location.getAccuracy() <= 0 || location.getAccuracy() > NETWORK_MAX_ACCURACY_METERS) {
            return "Wi-Fi/cell location accuracy is missing or worse than 25 km";
        }
        if (fromLocation(location, NETWORK_MAX_AGE_MS, LocationManager.NETWORK_PROVIDER, "Wi-Fi/cell") == null) {
            return "Wi-Fi/cell provider returned an old or invalid location";
        }
        return null;
    }

    private static String radioSource(Location location) {
        Bundle extras = location.getExtras();
        if (extras == null) return "";
        String source = "";
        for (String key : new String[]{"netType", "networkLocationType"}) {
            Object value = extras.get(key);
            if (!(value instanceof String)) continue;
            switch (((String) value).trim().toLowerCase(Locale.ROOT)) {
                case "gps", "gnss", "ll", "satellite": return "gps";
                case "wf", "wifi", "wi-fi": source = "wifi"; break;
                case "cl", "cell", "cellular": source = "cell"; break;
                default: break;
            }
        }
        return source;
    }

    private static String networkSourceName(Location location) {
        return switch (radioSource(location)) {
            case "wifi" -> "Wi-Fi";
            case "cell" -> "Cell network";
            default -> "Network provider";
        };
    }

    private void complete(long token, Fix fix, Consumer<Fix> success) {
        if (!generation.compareAndSet(token, token + 1)) return;
        release();
        success.accept(fix);
    }

    private void fail(long token, String message, Consumer<String> failure) {
        if (!generation.compareAndSet(token, token + 1)) return;
        release();
        failure.accept(message);
    }

    void cancel() {
        long token = generation.incrementAndGet();
        handler.post(() -> { if (token == generation.get()) release(); });
    }

    private void releaseGps() {
        if (manager != null) {
            if (nmea != null) {
                try { manager.removeNmeaListener(nmea); }
                catch (RuntimeException ignored) { }
            }
            if (gps != null) {
                try { manager.removeUpdates(gps); }
                catch (RuntimeException ignored) { }
            }
        }
        nmea = null;
        gps = null;
    }

    private void releaseNetwork() {
        if (manager != null && network != null) {
            try { manager.removeUpdates(network); }
            catch (RuntimeException ignored) { }
        }
        network = null;
    }

    private void release() {
        if (timeout != null) handler.removeCallbacks(timeout);
        if (fallback != null) handler.removeCallbacks(fallback);
        timeout = null;
        fallback = null;
        releaseGps();
        releaseNetwork();
    }
}
