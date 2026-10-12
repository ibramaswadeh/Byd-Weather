package com.ibramaswadeh.bydweather;

import org.json.JSONException;

/** Translates MET symbols into the condition families supported by WeatherData. */
final class MetCondition {
    final String symbol;
    final int nativeCode;
    final String description;
    final int severity;

    private MetCondition(String symbol, int nativeCode, String description, int severity) {
        this.symbol = symbol;
        this.nativeCode = nativeCode;
        this.description = description;
        this.severity = severity;
    }

    static MetCondition parse(String symbol) throws JSONException {
        String base = symbol.replaceFirst("_(day|night|polartwilight)$", "");
        switch (base) {
            case "clearsky": return new MetCondition(symbol, 0, "Clear sky", 0);
            case "fair": return new MetCondition(symbol, 1, "Mainly clear", 1);
            case "partlycloudy": return new MetCondition(symbol, 1, "Partly cloudy", 2);
            case "cloudy": return new MetCondition(symbol, 2, "Overcast", 3);
            case "fog": return new MetCondition(symbol, 18, "Fog", 4);
            default: break;
        }
        // These two spellings are part of MET's published symbol vocabulary.
        base = base.replace("lightssleet", "lightsleet").replace("lightssnow", "lightsnow");
        boolean thunder = base.endsWith("andthunder");
        if (thunder) base = base.substring(0, base.length() - "andthunder".length());
        boolean showers = base.endsWith("showers");
        if (showers) base = base.substring(0, base.length() - "showers".length());
        int intensity = base.startsWith("light") ? 0 : base.startsWith("heavy") ? 2 : 1;
        String type = base.replaceFirst("^(light|heavy)", "");
        int code;
        String description;
        switch (type) {
            case "rain":
                code = showers ? 3 : 7 + intensity;
                description = showers ? "rain showers" : "rain";
                break;
            case "snow":
                code = showers ? 13 : 14 + intensity;
                description = showers ? "snow showers" : "snow";
                break;
            case "sleet":
                code = 6;
                description = showers ? "sleet showers" : "sleet";
                break;
            default: throw new JSONException("Unsupported MET weather symbol");
        }
        String prefix = intensity == 0 ? "Light " : intensity == 2 ? "Heavy " : "Moderate ";
        return new MetCondition(symbol, thunder ? 4 : code,
                prefix + description + (thunder ? " with thunder" : ""),
                thunder ? 10 : 5 + intensity);
    }
}
