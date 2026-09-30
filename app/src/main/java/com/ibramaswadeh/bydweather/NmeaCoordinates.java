package com.ibramaswadeh.bydweather;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/** Reads coordinates from valid, recent GPS messages. */
final class NmeaCoordinates {
    static final long MAX_AGE_MS = 10_000L;
    private static final long FUTURE_TOLERANCE_MS = 2_000L;
    final double latitude;
    final double longitude;
    final long ageAtReceiptMs;

    private NmeaCoordinates(double latitude, double longitude, long ageAtReceiptMs) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.ageAtReceiptMs = ageAtReceiptMs;
    }

    static NmeaCoordinates parse(String message, long timestampMs, long nowMs) {
        if (!recent(timestampMs, nowMs) || message == null || message.length() > 256) return null;
        int end = message.length();
        while (end > 0 && (message.charAt(end - 1) == '\r' || message.charAt(end - 1) == '\n')) end--;
        String sentence = message.substring(0, end);
        int star = sentence.indexOf('*');
        if (star < 7 || star != sentence.length() - 3 || sentence.charAt(0) != '$') return null;
        int checksum = 0;
        for (int i = 1; i < star; i++) {
            char c = sentence.charAt(i);
            if (c < 0x20 || c > 0x7e) return null;
            checksum ^= c;
        }
        int high = asciiHex(sentence.charAt(star + 1));
        int low = asciiHex(sentence.charAt(star + 2));
        if (high < 0 || low < 0 || checksum != (high << 4 | low)) return null;
        String[] fields = sentence.substring(1, star).split(",", -1);
        if (fields.length > 32 || fields[0].length() != 5) return null;
        String talker = fields[0].substring(0, 2);
        if (!talker.matches("GP|GN|GL|GA|GB|BD|GQ|QZ")) return null;
        try {
            LocalTime time = time(fields[1]);
            LocalDate date = Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.UTC).toLocalDate();
            String type = fields[0].substring(2);
            String lat, ns, lon, ew;
            if ("RMC".equals(type)) {
                if (fields.length < 10 || !"A".equals(fields[2])) return null;
                if (fields.length > 12 && !fields[12].isEmpty()
                        && !fields[12].matches("[ADPRF]")) return null;
                date = date(fields[9]);
                lat = fields[3]; ns = fields[4]; lon = fields[5]; ew = fields[6];
            } else if ("GGA".equals(type)) {
                if (fields.length < 10 || !fields[6].matches("[12345]")) return null;
                if (!fields[7].matches("[0-9]{1,2}") || Integer.parseInt(fields[7]) == 0) return null;
                lat = fields[2]; ns = fields[3]; lon = fields[4]; ew = fields[5];
            } else return null;
            long fixMs = date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli();
            if ("GGA".equals(type)) {
                // Use the nearest UTC day for GGA messages, which contain only a time.
                if (fixMs - nowMs > 43_200_000L) fixMs -= 86_400_000L;
                else if (nowMs - fixMs > 43_200_000L) fixMs += 86_400_000L;
            }
            if (!recent(fixMs, nowMs)) return null;
            return new NmeaCoordinates(coordinate(lat, ns, 2, 90),
                    coordinate(lon, ew, 3, 180), Math.max(0L, nowMs - fixMs));
        } catch (IllegalArgumentException | DateTimeException | IndexOutOfBoundsException invalid) {
            return null;
        }
    }

    private static int asciiHex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    private static boolean recent(long stamp, long now) {
        return stamp > 0 && stamp >= now - MAX_AGE_MS && stamp <= now + FUTURE_TOLERANCE_MS;
    }

    private static LocalTime time(String value) {
        if (!value.matches("[0-9]{6}(\\.[0-9]{1,9})?")) throw new IllegalArgumentException();
        int nanos = value.length() == 6 ? 0
                : Integer.parseInt((value.substring(7) + "000000000").substring(0, 9));
        return LocalTime.of(Integer.parseInt(value.substring(0, 2)),
                Integer.parseInt(value.substring(2, 4)), Integer.parseInt(value.substring(4, 6)), nanos);
    }

    private static LocalDate date(String value) {
        if (!value.matches("[0-9]{6}")) throw new IllegalArgumentException();
        int year = Integer.parseInt(value.substring(4));
        return LocalDate.of(year >= 80 ? 1900 + year : 2000 + year,
                Integer.parseInt(value.substring(2, 4)), Integer.parseInt(value.substring(0, 2)));
    }

    private static double coordinate(String value, String hemisphere, int digits, int limit) {
        if (!value.matches("[0-9]{" + (digits + 2) + "}(\\.[0-9]{1,9})?"))
            throw new IllegalArgumentException();
        boolean negative;
        if (limit == 90 && ("N".equals(hemisphere) || "S".equals(hemisphere))) {
            negative = "S".equals(hemisphere);
        } else if (limit == 180 && ("E".equals(hemisphere) || "W".equals(hemisphere))) {
            negative = "W".equals(hemisphere);
        } else throw new IllegalArgumentException();
        int degrees = Integer.parseInt(value.substring(0, digits));
        double minutes = Double.parseDouble(value.substring(digits));
        if (minutes >= 60 || degrees > limit || degrees == limit && minutes != 0)
            throw new IllegalArgumentException();
        double decimal = degrees + minutes / 60.0;
        return negative ? -decimal : decimal;
    }
}
