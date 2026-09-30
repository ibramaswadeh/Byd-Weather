package com.ibramaswadeh.bydweather;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class NmeaCoordinatesTest {
    private static final long NOW = Instant.parse("2026-09-30T12:35:19Z").toEpochMilli();
    private static final String GGA = "GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,";
    private static final String RMC = "GPRMC,123519,A,4807.038,N,01131.000,E,0.0,0.0,300926,,,A";

    @Test public void readsDegreesAndMinutesFromBothSentenceTypes() {
        for (String body : new String[]{GGA, RMC}) {
            NmeaCoordinates fix = parse(body);
            assertNotNull(fix);
            assertEquals(48.1173, fix.latitude, 0.000001);
            assertEquals(11.5166667, fix.longitude, 0.000001);
        }
    }
    @Test public void supportsSouthernAndWesternCoordinatesAndZero() {
        NmeaCoordinates fix = parse(GGA.replace(",N,", ",S,").replace(",E,", ",W,"));
        assertTrue(fix.latitude < 0 && fix.longitude < 0);
        fix = parse(GGA.replace("4807.038", "0000.000").replace("01131.000", "00000.000"));
        assertNotNull(fix);
        assertEquals(0, fix.latitude, 0);
        assertEquals(0, fix.longitude, 0);
    }
    @Test public void rejectsChecksumDamageAndUnboundedOrNonAsciiInput() {
        assertNull(NmeaCoordinates.parse(sentence(GGA).replace("4807", "4808"), NOW, NOW));
        assertNull(NmeaCoordinates.parse("$" + "A".repeat(257), NOW, NOW));
        assertNull(parse(GGA.replace("GNGGA", "GÑGGA")));
        assertNull(parse(GGA.replace("GNGGA", "P GGA")));
        String valid = sentence(GGA);
        String checksum = valid.substring(valid.length() - 2);
        StringBuilder unicode = new StringBuilder(valid.substring(0, valid.length() - 2));
        for (char c : checksum.toCharArray()) unicode.append((char) (c + 0xFEE0));
        assertNull(NmeaCoordinates.parse(unicode.toString(), NOW, NOW));
    }
    @Test public void preservesAgeOfAlreadyOldFix() {
        NmeaCoordinates fix = parse(GGA.replace("123519", "123510"));
        assertNotNull(fix);
        assertEquals(9_000L, fix.ageAtReceiptMs);
    }
    @Test public void rejectsNoFixSimulationDeadReckoningAndWrongHemispheres() {
        for (String quality : new String[]{"0", "6", "7", "8"}) {
            assertNull(parse(GGA.replace(",1,08,", "," + quality + ",08,")));
        }
        assertNull(parse(GGA.replace(",1,08,", ",1,00,")));
        assertNull(parse(RMC.replace(",A,", ",V,")));
        assertNull(parse(RMC.substring(0, RMC.length() - 1) + "S"));
        assertNull(parse(GGA.replace(",N,", ",E,")));
    }
    @Test public void rejectsInvalidMinutesDegreesDatesAndTimes() {
        for (String lat : new String[]{"4860.000", "9100.000", "9000.001", "NaN", "+4807.0"}) {
            assertNull(parse(GGA.replace("4807.038", lat)));
        }
        assertNull(parse(GGA.replace("01131.000", "18000.001")));
        assertNull(parse(GGA.replace("123519", "246000")));
        assertNull(parse(RMC.replace("300926", "310926")));
    }
    @Test public void rejectsStaleReceiptStaleFixAndFutureFix() {
        assertNull(NmeaCoordinates.parse(sentence(GGA), NOW - 10_001L, NOW));
        assertNull(NmeaCoordinates.parse(sentence(GGA), NOW + 2_001L, NOW));
        assertNull(parse(GGA.replace("123519", "123508")));
        assertNull(parse(GGA.replace("123519", "123522")));
        assertNull(parse(RMC.replace("300926", "290926")));
    }
    @Test public void acceptsMidnightRolloverAndCrLf() {
        long midnight = Instant.parse("2026-10-01T00:00:01Z").toEpochMilli();
        assertNotNull(NmeaCoordinates.parse(sentence(GGA.replace("123519", "235959")) + "\r\n",
                midnight, midnight));
        assertNotNull(NmeaCoordinates.parse(sentence(GGA.replace("123519", "000001")),
                midnight, midnight));
    }
    @Test public void acceptsBoundaryCoordinatesAndFractionalSeconds() {
        assertNotNull(parse(GGA.replace("4807.038", "9000.000").replace("01131.000", "18000.000")));
        assertNotNull(parse(GGA.replace("123519", "123519.125")));
        assertNull(parse(GGA.replace("123519", "123519.")));
    }
    private static NmeaCoordinates parse(String body) {
        return NmeaCoordinates.parse(sentence(body), NOW, NOW);
    }
    static String sentence(String body) {
        int checksum = 0;
        for (char c : body.toCharArray()) checksum ^= c;
        return "$" + body + "*" + String.format(java.util.Locale.US, "%02X", checksum);
    }
}
