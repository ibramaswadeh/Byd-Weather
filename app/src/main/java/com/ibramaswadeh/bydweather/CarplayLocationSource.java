package com.ibramaswadeh.bydweather;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Collects one fresh GPS fix from location callbacks or validated NMEA messages. */
final class CarplayLocationSource {
    static final class Fix {
        final double latitude;
        final double longitude;
        final long expiresAtElapsedMs;

        private Fix(double latitude, double longitude, long expiresAtElapsedMs) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }
    }
    static final long ACQUISITION_TIMEOUT_MS = 60_000L;
    private static final long FUTURE_TOLERANCE_NANOS = 2_000_000_000L;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private OnNmeaMessageListener nmea;
    private LocationListener gps;
    private Runnable timeout;

    CarplayLocationSource(Context context) {
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    void acquire(Consumer<Fix> success, Consumer<String> failure) {
        long token = generation.incrementAndGet();
        handler.post(() -> {
            if (token != generation.get()) return;
            release();
            if (manager == null) {
                fail(token, "GPS service unavailable", failure);
                return;
            }
            try {
                if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    fail(token, "GPS is off", failure);
                    return;
                }
                nmea = (sentence, timestamp) -> {
                    if (token != generation.get()) return;
                    NmeaCoordinates coordinates = NmeaCoordinates.parse(sentence, timestamp,
                            System.currentTimeMillis());
                    if (coordinates == null) return;
                    complete(token, new Fix(coordinates.latitude, coordinates.longitude,
                            SystemClock.elapsedRealtime() + NmeaCoordinates.MAX_AGE_MS
                                    - coordinates.ageAtReceiptMs), success);
                };
                // NMEA registration is optional and must not gate GPS updates.
                // Some head units expose GPS callbacks but deny raw-message access.
                try {
                    manager.addNmeaListener(nmea, handler);
                } catch (SecurityException denied) {
                    // GPS acquisition can still succeed without NMEA permission.
                } catch (RuntimeException unavailable) {
                    // GPS acquisition can still succeed without NMEA access.
                }
                if (token != generation.get()) return;
                gps = new LocationListener() {
                    @Override public void onLocationChanged(Location location) {
                        if (token != generation.get()) return;
                        Fix fix = fromGps(location);
                        if (fix != null) complete(token, fix, success);
                    }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) {
                        if (LocationManager.GPS_PROVIDER.equals(provider)) {
                            fail(token, "GPS is off", failure);
                        }
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                };
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f,
                        gps, Looper.getMainLooper());
                if (token != generation.get()) return;
                timeout = () -> fail(token, "No fresh GPS fix within 60 seconds", failure);
                handler.postDelayed(timeout, ACQUISITION_TIMEOUT_MS);
            } catch (SecurityException denied) {
                fail(token, "GPS permission or provider unavailable", failure);
            } catch (RuntimeException unavailable) {
                fail(token, "GPS provider unavailable", failure);
            }
        });
    }

    private static Fix fromGps(Location location) {
        if (location == null || !LocationManager.GPS_PROVIDER.equals(location.getProvider())) return null;
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        long nowNanos = SystemClock.elapsedRealtimeNanos();
        long fixNanos = location.getElapsedRealtimeNanos();
        // Use the original monotonic fix time; never retimestamp an old coordinate.
        if (fixNanos <= 0 || fixNanos - nowNanos > FUTURE_TOLERANCE_NANOS) return null;
        long ageNanos = Math.max(0L, nowNanos - fixNanos);
        if (ageNanos > NmeaCoordinates.MAX_AGE_MS * 1_000_000L) return null;
        return new Fix(latitude, longitude, SystemClock.elapsedRealtime()
                + NmeaCoordinates.MAX_AGE_MS - ageNanos / 1_000_000L);
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
        handler.post(() -> {
            if (token == generation.get()) release();
        });
    }

    private void release() {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
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
}
