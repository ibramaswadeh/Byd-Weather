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

/** Collects one fresh GPS fix from NMEA messages. */
final class RawGnssSource {
    static final class Fix {
        final NmeaCoordinates coordinates;
        final long expiresAtElapsedMs;
        Fix(NmeaCoordinates coordinates, long receivedElapsedMs) {
            this.coordinates = coordinates;
            expiresAtElapsedMs = receivedElapsedMs + NmeaCoordinates.MAX_AGE_MS
                    - coordinates.ageAtReceiptMs;
        }
    }
    static final long ACQUISITION_TIMEOUT_MS = 60_000L;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private OnNmeaMessageListener nmea;
    private LocationListener activation;
    private Runnable timeout;

    RawGnssSource(Context context) {
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
                    NmeaCoordinates fix = NmeaCoordinates.parse(sentence, timestamp,
                            System.currentTimeMillis());
                    if (fix == null || !generation.compareAndSet(token, token + 1)) return;
                    Fix snapshot = new Fix(fix, SystemClock.elapsedRealtime());
                    release();
                    success.accept(snapshot);
                };
                if (!manager.addNmeaListener(nmea, handler)) {
                    fail(token, "Raw GPS messages unavailable", failure);
                    return;
                }
                activation = new LocationListener() {
                    // Keeps GPS running while NMEA messages are collected.
                    @Override public void onLocationChanged(Location ignored) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                };
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f,
                        activation, Looper.getMainLooper());
                timeout = () -> {
                    fail(token, "No fresh raw GPS fix within 60 seconds", failure);
                };
                handler.postDelayed(timeout, ACQUISITION_TIMEOUT_MS);
            } catch (SecurityException | IllegalArgumentException unavailable) {
                fail(token, "GPS permission or provider unavailable", failure);
            }
        });
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
                catch (SecurityException | IllegalArgumentException ignored) { }
            }
            if (activation != null) {
                try { manager.removeUpdates(activation); }
                catch (SecurityException | IllegalArgumentException ignored) { }
            }
        }
        nmea = null;
        activation = null;
    }
}
