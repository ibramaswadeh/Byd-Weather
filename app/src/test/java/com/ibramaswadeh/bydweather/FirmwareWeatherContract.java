package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Test oracle projected from REA evidence, independent of the app's mapping implementation. */
final class FirmwareWeatherContract {
    private FirmwareWeatherContract() {}

    static JSONObject descriptors() throws Exception {
        try (InputStream stream = FirmwareWeatherContract.class
                .getResourceAsStream("/byd-weatherdata-2.9.5-contract.json")) {
            if (stream == null) throw new IllegalStateException("Missing firmware contract");
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // WeatherData 2.9.5 NumberUtils.isDuringNight, inspected through REA MCP.
    // This models its actual timestamp search, including strict sunset/sunrise boundaries.
    static boolean isDuringNight(JSONObject payload) throws Exception {
        JSONObject data = payload.getJSONObject("data");
        JSONArray days = data.getJSONObject("dailys").getJSONArray("dailyweathers");
        long update = data.getJSONObject("condition").getLong("updatetime");
        if (days.length() == 0 || update <= 0) return false;
        List<Long> anchors = new ArrayList<>();
        for (int i = 0; i < days.length(); i++) anchors.add(days.getJSONObject(i).getLong("publictime"));
        int index = Collections.binarySearch(anchors, update);
        if (index < 0) index = -(index + 1) - 1;
        if (index < 0 || index >= days.length() - 1) return false;
        return update > days.getJSONObject(index).getLong("sunSet")
                && update < days.getJSONObject(index + 1).getLong("sunRise");
    }

    static String widgetBackground(JSONObject payload, boolean portrait) throws Exception {
        JSONObject contract = descriptors();
        int code = payload.getJSONObject("data").getJSONObject("condition").getInt("cnweatherid");
        if (code == 0 && isDuringNight(payload)) {
            return contract.getJSONObject("widget")
                    .getString(portrait ? "nightPortraitCard" : "nightLandscapeCard");
        }
        return contract.getJSONObject("conditions").getJSONObject(String.valueOf(code))
                .getString(portrait ? "portraitCard" : "landscapeCard");
    }

    static String widgetLabel(JSONObject payload) throws Exception {
        int code = payload.getJSONObject("data").getJSONObject("condition").getInt("cnweatherid");
        return descriptors().getJSONObject("conditions").getJSONObject(String.valueOf(code))
                .getString("englishLabel");
    }
}
