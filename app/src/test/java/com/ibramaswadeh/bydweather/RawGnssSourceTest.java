package com.ibramaswadeh.bydweather;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLocationManager;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@LooperMode(LooperMode.Mode.PAUSED)
public class RawGnssSourceTest {
    private RawGnssSource source;
    private ShadowLocationManager gps;
    private final List<RawGnssSource.Fix> fixes = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        gps = shadowOf((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        source = new RawGnssSource(context);
    }

    @Test public void ignoresAndroidLocationsAndCompletesOnlyWithRawCoordinates() {
        acquire();
        Location androidEstimate = new Location(LocationManager.GPS_PROVIDER);
        androidEstimate.setLatitude(1);
        androidEstimate.setLongitude(2);
        gps.simulateLocation(androidEstimate);
        idle();
        assertTrue(fixes.isEmpty());
        sendFix();
        assertEquals(1, fixes.size());
        assertEquals(48.1173, fixes.get(0).coordinates.latitude, 0.000001);
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(failures.isEmpty());
        sendFix();
        assertEquals(1, fixes.size());
    }

    @Test public void timeoutReleasesReceiverAndIgnoresLaterMessages() {
        acquire();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(RawGnssSource.ACQUISITION_TIMEOUT_MS));
        assertEquals(1, failures.size());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        sendFix();
        assertTrue(fixes.isEmpty());
    }

    @Test public void cancellationReleasesAndSuppressesQueuedAndLateCallbacks() {
        acquire();
        source.cancel();
        sendFix();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(fixes.isEmpty());
        assertTrue(failures.isEmpty());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void replacementAcquisitionDoesNotDeliverToOldCallback() {
        acquire();
        List<RawGnssSource.Fix> replacement = new ArrayList<>();
        source.acquire(replacement::add, failures::add);
        idle();
        sendFix();
        assertTrue(fixes.isEmpty());
        assertEquals(1, replacement.size());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void disabledGpsFailsWithoutActivatingReceiver() {
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        acquire();
        assertEquals(1, failures.size());
        assertTrue(fixes.isEmpty());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    private void acquire() { source.acquire(fixes::add, failures::add); idle(); }
    private void idle() { shadowOf(Looper.getMainLooper()).idle(); }
    private void sendFix() {
        long now = System.currentTimeMillis();
        String time = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochMilli(now));
        gps.simulateNmeaMessage(NmeaCoordinatesTest.sentence(
                "GNGGA," + time + ",4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,"), now);
        idle();
    }
}
