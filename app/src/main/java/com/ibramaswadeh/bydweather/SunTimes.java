package com.ibramaswadeh.bydweather;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;

/** Solar events for one local date, including dates without a sunrise or sunset. */
final class SunTimes {
    final LocalDate date;
    final Long sunrise, sunset;
    final boolean sunAboveHorizon;
    final long dayStart;

    private SunTimes(LocalDate date, Long sunrise, Long sunset, boolean sunAboveHorizon, long dayStart) {
        this.date = date;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.sunAboveHorizon = sunAboveHorizon;
        this.dayStart = dayStart;
    }

    static SunTimes parse(JSONObject response, LocalDate date, ZoneId zone) throws JSONException {
        JSONObject properties = response.getJSONObject("properties");
        if (!"Sun".equals(properties.getString("body"))) throw new JSONException("Expected solar events");
        Long rise = event(properties, "sunrise");
        Long set = event(properties, "sunset");
        if (rise != null && set != null && set <= rise) throw new JSONException("Invalid solar event order");
        boolean visible = properties.getJSONObject("solarnoon").getBoolean("visible");
        return new SunTimes(date, rise, set, visible, date.atStartOfDay(zone).toInstant().toEpochMilli());
    }

    private static Long event(JSONObject properties, String name) throws JSONException {
        if (!properties.has(name)) throw new JSONException("Missing " + name);
        if (properties.isNull(name)) return null;
        JSONObject event = properties.getJSONObject(name);
        if (!event.has("time")) throw new JSONException("Missing solar event time");
        return event.isNull("time") ? null : MetForecast.timestamp(event.getString("time"));
    }

    boolean isDay(long time) {
        return time >= nativeSunrise() && time < nativeSunset();
    }

    long nativeAnchor() {
        return sunrise == null ? dayStart : sunrise;
    }

    // Out-of-range sentinels keep absent polar events out of the stock hourly strip.
    // They also preserve its sunset/next-sunrise comparison without invented event times.
    long nativeSunrise() {
        return sunrise != null ? sunrise : sunset != null || sunAboveHorizon ? 0 : Long.MAX_VALUE;
    }

    long nativeSunset() {
        return sunset != null ? sunset : sunrise != null || sunAboveHorizon ? Long.MAX_VALUE : 0;
    }
}
