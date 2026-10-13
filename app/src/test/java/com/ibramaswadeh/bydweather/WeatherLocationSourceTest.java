package com.ibramaswadeh.bydweather;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLocationManager;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@LooperMode(LooperMode.Mode.PAUSED)
public class WeatherLocationSourceTest {
    private WeatherLocationSource source;
    private ShadowLocationManager gps;
    private final List<WeatherLocationSource.Fix> fixes = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final List<String> progress = new ArrayList<>();

    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        gps = shadowOf((LocationManager) context.getSystemService(Context.LOCATION_SERVICE));
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION);
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        source = new WeatherLocationSource(context);
    }

    @Test public void freshGpsCallbackCompletesWithoutNmeaAndReleasesBothListeners() {
        acquire();
        gps.simulateLocation(freshGps());
        idle();
        assertEquals(1, fixes.size());
        assertEquals(31.9539, fixes.get(0).latitude, 0.000001);
        assertEquals(35.9106, fixes.get(0).longitude, 0.000001);
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(failures.isEmpty());
        sendFix();
        assertEquals(1, fixes.size());
    }

    @Test public void validNmeaCompletesWithoutAGpsCallback() {
        acquire();
        sendFix();
        assertEquals(1, fixes.size());
        assertEquals(48.1173, fixes.get(0).latitude, 0.000001);
        assertEquals(11.5166667, fixes.get(0).longitude, 0.000001);
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        gps.simulateLocation(freshGps());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertEquals(1, fixes.size());
        assertTrue(failures.isEmpty());
    }

    @Test @Config(shadows = NmeaUnavailableLocationManager.class)
    public void unavailableNmeaDoesNotBlockGps() {
        assertGpsWorksWithoutNmea();
    }

    @Test @Config(shadows = NmeaDeniedLocationManager.class)
    public void deniedNmeaDoesNotBlockGps() {
        assertGpsWorksWithoutNmea();
    }

    private void assertGpsWorksWithoutNmea() {
        acquire();
        assertEquals(1, gps.getLocationUpdateListeners().size());
        assertTrue(failures.isEmpty());
        gps.simulateLocation(freshGps());
        idle();
        assertEquals(1, fixes.size());
        assertTrue(failures.isEmpty());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void lastKnownAndStaleGpsLocationsDoNotCompleteAcquisition() {
        Location old = freshGps();
        gps.setLastKnownLocation(LocationManager.GPS_PROVIDER, old);
        acquire();
        assertTrue(fixes.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11));
        gps.simulateLocation(old);
        idle();
        assertTrue(fixes.isEmpty());
        assertTrue(failures.isEmpty());
        sendFix();
        assertEquals(1, fixes.size());
    }

    @Test public void rejectsInvalidCoordinatesProvidersAndFixTimes() {
        acquire();
        android.location.LocationListener callback = gps.getLocationUpdateListeners().get(0);
        for (int invalid = 0; invalid < 6; invalid++) {
            Location location = freshGps();
            if (invalid == 0) location.setLatitude(Double.NaN);
            if (invalid == 1) location.setLongitude(Double.POSITIVE_INFINITY);
            if (invalid == 2) location.setLatitude(91);
            if (invalid == 3) location.setElapsedRealtimeNanos(0);
            if (invalid == 4) location.setElapsedRealtimeNanos(
                    SystemClock.elapsedRealtimeNanos() + TimeUnit.SECONDS.toNanos(3));
            if (invalid == 5) location.setProvider(LocationManager.NETWORK_PROVIDER);
            callback.onLocationChanged(location);
        }
        assertTrue(fixes.isEmpty());
        gps.simulateLocation(freshGps());
        idle();
        assertEquals(1, fixes.size());
    }

    @Test public void gpsExpiryUsesOriginalMonotonicFixTime() {
        acquire();
        Location old = freshGps();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9));
        // A recent wall-clock time cannot grant this old fix a new ten-second lifetime.
        old.setTime(System.currentTimeMillis());
        gps.simulateLocation(old);
        idle();
        assertEquals(1, fixes.size());
        assertEquals(1_000L, fixes.get(0).expiresAtElapsedMs - SystemClock.elapsedRealtime());
    }

    @Test public void timeoutReleasesReceiverAndIgnoresLaterMessages() {
        acquire();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WeatherLocationSource.ACQUISITION_TIMEOUT_MS));
        assertEquals(1, failures.size());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        sendFix();
        assertTrue(fixes.isEmpty());
    }

    @Test public void cancellationReleasesAndSuppressesQueuedAndLateCallbacks() {
        acquire();
        gps.simulateLocation(freshGps());
        source.cancel();
        sendFix();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(fixes.isEmpty());
        assertTrue(failures.isEmpty());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void replacementAcquisitionDoesNotDeliverToOldCallback() {
        acquire();
        android.location.LocationListener oldCallback = gps.getLocationUpdateListeners().get(0);
        List<WeatherLocationSource.Fix> replacement = new ArrayList<>();
        source.acquire(message -> { }, replacement::add, failures::add);
        idle();
        oldCallback.onLocationChanged(freshGps());
        assertTrue(replacement.isEmpty());
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

    @Test public void disablingGpsDuringAcquisitionReleasesTheListeners() {
        acquire();
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        idle();
        assertEquals(1, failures.size());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        sendFix();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(fixes.isEmpty());
        assertEquals(1, failures.size());
    }

    @Test public void disabledGpsSkipsNmeaAndCompletesFromWifi() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        gps.simulateLocation(freshGps());
        sendFix();
        assertTrue(fixes.isEmpty());
        assertEquals(1, gps.getLocationUpdateListeners().size());
        gps.simulateLocation(freshNetwork("wf"));
        idle();
        assertEquals(1, fixes.size());
        assertEquals(LocationManager.NETWORK_PROVIDER, fixes.get(0).provider);
        assertEquals("Wi-Fi", fixes.get(0).sourceName);
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertTrue(failures.isEmpty());
    }

    @Test public void automaticTriesNetworkAfterTenSecondsAndReleasesGpsOnSuccess() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquire();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(
                WeatherLocationSource.NETWORK_FALLBACK_DELAY_MS - 1));
        gps.simulateLocation(freshNetwork("cl"));
        idle();
        assertTrue(fixes.isEmpty());
        assertEquals(1, gps.getLocationUpdateListeners().size());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(2, gps.getLocationUpdateListeners().size());
        assertTrue(progress.get(progress.size() - 1).contains("Trying Wi-Fi/cell"));
        gps.simulateLocation(freshNetwork("cl"));
        idle();
        assertEquals(1, fixes.size());
        assertEquals("Cell network", fixes.get(0).sourceName);
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
        sendFix();
        assertEquals(1, fixes.size());
    }

    @Test public void promptGpsFixDoesNotStartNetworkFallback() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquire();
        gps.simulateLocation(freshGps());
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11));
        assertEquals("GPS", fixes.get(0).sourceName);
        assertEquals(1, progress.size());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void disabledGpsStartsNetworkImmediately() {
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquire();
        assertTrue(failures.isEmpty());
        assertEquals(1, gps.getLocationUpdateListeners().size());
        gps.simulateLocation(freshNetwork("cl"));
        idle();
        assertEquals(1, fixes.size());
        assertEquals(LocationManager.NETWORK_PROVIDER, fixes.get(0).provider);
    }

    @Test public void approximatePermissionCanUseNetworkWithoutGpsPermission() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION);
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquire();
        gps.simulateLocation(freshGps());
        sendFix();
        assertTrue(fixes.isEmpty());
        gps.simulateLocation(freshNetwork("wf"));
        idle();
        assertEquals(1, fixes.size());
        assertEquals("Wi-Fi", fixes.get(0).sourceName);
    }

    @Test public void missingLocationPermissionReportsThePermissionFailure() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION);
        acquireWithoutGps();
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("permission denied"));
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void disabledProvidersReportNetworkFailure() {
        acquireWithoutGps();
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("Wi-Fi/cell location is off"));
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test @Config(shadows = NetworkProviderAbsentLocationManager.class)
    public void missingNetworkProviderIsReportedWhenGpsIsDisabled() {
        acquireWithoutGps();
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("provider is unavailable"));
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void lastKnownAndOldNetworkFixesDoNotCompleteAcquisition() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        Location old = freshNetwork("wf");
        gps.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, old);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        acquireWithoutGps();
        assertTrue(fixes.isEmpty());
        old.setTime(System.currentTimeMillis());
        gps.simulateLocation(old);
        idle();
        assertTrue(fixes.isEmpty());
        gps.simulateLocation(freshNetwork("wf"));
        idle();
        assertEquals(1, fixes.size());
    }

    @Test public void networkExpiryPreservesTheOriginalFixAge() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        Location old = freshNetwork("wf");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));
        acquireWithoutGps();
        gps.simulateLocation(old);
        idle();
        assertEquals(1, fixes.size());
        assertEquals(1_000L, fixes.get(0).expiresAtElapsedMs - SystemClock.elapsedRealtime());
    }

    @Test public void rejectsGpsTaggedNetworkResultsAndUnusableAccuracy() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        android.location.LocationListener callback = gps.getLocationUpdateListeners().get(0);
        callback.onLocationChanged(freshNetwork("ll"));
        for (int invalid = 0; invalid < 7; invalid++) {
            Location location = freshNetwork("cl");
            if (invalid == 0) location.removeAccuracy();
            if (invalid == 1) location.setAccuracy(Float.NaN);
            if (invalid == 2) location.setAccuracy(0);
            if (invalid == 3) location.setAccuracy(25_001);
            if (invalid == 4) location.setElapsedRealtimeNanos(0);
            if (invalid == 5) location.setLatitude(91);
            if (invalid == 6) location.setElapsedRealtimeNanos(
                    SystemClock.elapsedRealtimeNanos() + TimeUnit.SECONDS.toNanos(3));
            callback.onLocationChanged(location);
        }
        assertTrue(fixes.isEmpty());
        callback.onLocationChanged(freshNetwork("wf"));
        assertEquals(1, fixes.size());
    }

    @Test public void networkTimeoutReportsItsRejectedResultAndReleasesListeners() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        gps.simulateLocation(freshNetwork("gps"));
        idle();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60));
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("GPS-derived"));
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Test public void networkDoesNotClaimARadioSourceWhenTheProviderOmitsIt() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        Location fix = freshNetwork("wf");
        fix.setExtras(null);
        gps.simulateLocation(fix);
        idle();
        assertEquals(1, fixes.size());
        assertEquals("Network provider", fixes.get(0).sourceName);
    }

    @Test public void satelliteMetadataOverridesAnOtherwiseWifiTaggedResult() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        Location fix = freshNetwork("wf");
        fix.getExtras().putString("networkLocationType", "gps");
        gps.simulateLocation(fix);
        idle();
        assertTrue(fixes.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        gps.simulateLocation(freshNetwork("cl"));
        idle();
        assertEquals(1, fixes.size());
    }

    @Test public void mockNetworkCoordinatesAreRejected() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquireWithoutGps();
        Location fix = freshNetwork("wf");
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(fix, "setIsFromMockProvider",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
        gps.simulateLocation(fix);
        idle();
        assertTrue(fixes.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        gps.simulateLocation(freshNetwork("wf"));
        idle();
        assertEquals(1, fixes.size());
    }

    @Test public void cancelAfterFallbackRemovesBothProviderListenersAndIgnoresQueuedCallbacks() {
        gps.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        acquire();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        android.location.LocationListener callback = gps.getLocationUpdateListeners().get(1);
        source.cancel();
        callback.onLocationChanged(freshNetwork("cl"));
        idle();
        assertTrue(fixes.isEmpty());
        assertTrue(failures.isEmpty());
        assertTrue(gps.getLocationUpdateListeners().isEmpty());
    }

    @Implements(LocationManager.class)
    public static class NetworkProviderAbsentLocationManager extends ShadowLocationManager {
        @Implementation protected List<String> getAllProviders() {
            return List.of(LocationManager.GPS_PROVIDER);
        }
    }

    @Implements(LocationManager.class)
    public static class NmeaUnavailableLocationManager extends ShadowLocationManager {
        @Implementation protected boolean addNmeaListener(OnNmeaMessageListener listener, Handler handler) {
            return false;
        }
    }

    @Implements(LocationManager.class)
    public static class NmeaDeniedLocationManager extends ShadowLocationManager {
        @Implementation protected boolean addNmeaListener(OnNmeaMessageListener listener, Handler handler) {
            throw new SecurityException("OEM raw-message access denied");
        }
    }

    private Location freshGps() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(31.9539);
        location.setLongitude(35.9106);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return location;
    }

    private Location freshNetwork(String type) {
        Location location = freshGps();
        location.setProvider(LocationManager.NETWORK_PROVIDER);
        location.setAccuracy(100);
        android.os.Bundle extras = new android.os.Bundle();
        extras.putString("netType", type);
        location.setExtras(extras);
        return location;
    }

    private void acquireWithoutGps() {
        gps.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        source.acquire(progress::add, fixes::add, failures::add);
        idle();
    }

    private void acquire() { source.acquire(progress::add, fixes::add, failures::add); idle(); }
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
