package com.ibramaswadeh.bydweather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Iterator;

/** Converts Open-Meteo forecasts to BYD weather JSON. */
public final class WeatherMapping {
    static final int REQUIRED_HOURLY_COUNT = 8;
    static final int REQUIRED_DAILY_COUNT = 16;
    static final int CURRENT_DAY_INDEX = 1;

    private WeatherMapping() {
    }

    public static final String ATTRIBUTION = "Weather data by Open-Meteo.com";

    /** Maps a BigDataCloud location along with the forecast into the widget payload. */
    public static String toBydJson(JSONObject forecast, JSONObject airQuality,
            JSONObject location, String fallbackName, long nowMs) throws JSONException {
        String city = locationText(location, "city");
        String district = districtName(locationText(location, "locality"));
        if (city != null && city.equalsIgnoreCase(district)) district = null;
        if (district == null) district = districtName(locationText(location, "localityName"));
        if (city != null && city.equalsIgnoreCase(district)) district = null;
        if (district == null) district = districtName(administrativeDistrict(location, city));
        if (city != null && city.equalsIgnoreCase(district)) district = null;
        String name = district == null ? city : city == null ? district : district + ", " + city;
        if (name == null) name = fallbackName;
        JSONObject result = new JSONObject(toBydJson(forecast, airQuality, name, name, nowMs));
        JSONObject metadata = result.getJSONObject("data").getJSONObject("city");
        String country = locationText(location, "countryName");
        String countryCode = locationText(location, "countryCode");
        String province = locationText(location, "principalSubdivision");
        if (country != null) metadata.put("countryname", country);
        if (countryCode != null) metadata.put("countryCode", countryCode);
        if (province != null) {
            metadata.put("provincename", province)
                    .put("administrativearea", new JSONObject().put("localizedname", province))
                    .put("supplementalAdminAreas", new JSONArray()
                            .put(new JSONObject().put("localizedName", province)));
        }
        if (city != null) metadata.put("parentcity", city);
        return result.toString();
    }

    private static String districtName(String name) {
        if (name == null) return null;
        String label = name.replaceFirst("(?i)(?:^|\\s+)sub(?:-|\\s*)district\\s*$", "").trim();
        return label.isEmpty() ? null : label;
    }

    private static String locationText(JSONObject location, String key) {
        Object value = location == null ? null : location.opt(key);
        if (!(value instanceof String)) return null;
        String name = ((String) value).trim();
        return name.isEmpty() ? null : name;
    }

    private static String administrativeDistrict(JSONObject location, String city) {
        JSONObject info = location == null ? null : location.optJSONObject("localityInfo");
        JSONArray areas = info == null ? null : info.optJSONArray("administrative");
        if (areas == null) return null;
        int cityOrder = -1;
        for (int i = 0; i < areas.length(); i++) {
            JSONObject area = areas.optJSONObject(i);
            if (city != null && (city.equalsIgnoreCase(locationText(area, "name"))
                    || city.equalsIgnoreCase(locationText(area, "isoName")))) {
                cityOrder = Math.max(cityOrder, administrativeOrder(area));
            }
        }
        String district = null;
        int bestOrder = -1;
        for (int i = 0; i < areas.length(); i++) {
            JSONObject area = areas.optJSONObject(i);
            String name = locationText(area, "name");
            String description = locationText(area, "description");
            String type = description == null ? "" : description.toLowerCase(Locale.ROOT);
            if (name == null || name.equalsIgnoreCase(city)
                    || name.equalsIgnoreCase(locationText(location, "countryName"))
                    || name.equalsIgnoreCase(locationText(location, "principalSubdivision"))
                    || broadAdministrativeType(type)) continue;
            int order = administrativeOrder(area);
            // Provider order is broad-to-specific; no country-specific admin level is assumed.
            if (cityOrder >= 0 ? order <= cityOrder : !districtType(type)) continue;
            if (district == null || order > bestOrder) {
                district = name;
                bestOrder = order;
            }
        }
        return district;
    }

    private static int administrativeOrder(JSONObject area) {
        Object value = area == null ? null : area.opt("order");
        if (!(value instanceof Number)) return -1;
        double order = ((Number) value).doubleValue();
        return order >= 0 && order <= Integer.MAX_VALUE && order == Math.rint(order) ? (int) order : -1;
    }

    private static boolean broadAdministrativeType(String type) {
        for (String prefix : new String[]{"country", "state", "province", "county", "region",
                "governorate", "continent", "federal district"}) {
            if (type.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean districtType(String type) {
        return type.matches(".*\\b(district|suburb|neighbourhood|neighborhood|borough|ward)\\b.*");
    }

    public static String toBydJson(JSONObject forecast, JSONObject airQuality,
            String cityName, String englishCityName, long nowMs) throws JSONException {
        if (forecast == null || !forecast.has("current")
                || !forecast.has("hourly") || !forecast.has("daily")) {
            throw new JSONException("incomplete Open-Meteo response");
        }
        ForecastTime time = new ForecastTime(forecast.getString("timezone"));
        JSONObject current = forecast.getJSONObject("current");
        JSONObject hourly = forecast.getJSONObject("hourly");
        JSONObject daily = forecast.getJSONObject("daily");
        JSONArray hours = hourly.optJSONArray("time");
        JSONArray days = daily.optJSONArray("time");
        if (hours == null || days == null
                || !hasRequiredForecastCounts(hours.length(), days.length())) {
            throw new JSONException("incomplete forecast arrays");
        }
        // Daily period sampling can use hours beyond the displayed 48-hour window.
        for (int i = 0; i < hours.length(); i++) {
            requireWeatherCode(hourly.getJSONArray("weather_code").opt(i));
            requireDayFlagAt(hourly, i);
        }
        String city = nonEmpty(cityName, "Location");
        String english = nonEmpty(englishCityName, city);
        long update = time.parseRequiredTime(current, "time");
        int code = weatherId(requireWeatherCode(current.opt("weather_code")));
        int temperature = requireNumber(current, "temperature_2m");
        int humidity = requireNumber(current, "relative_humidity_2m");
        int pressure = requireNumber(current, "pressure_msl");
        int visibility = requireNumber(current, "visibility") / 1000;
        int windSpeed = requireNumber(current, "wind_speed_10m");
        int windDegrees = requireNumber(current, "wind_direction_10m");
        int uvIndex = requireNumber(current, "uv_index");
        if (code < 0) throw new JSONException("unsupported current weather code");
        JSONObject condition = new JSONObject()
                .put("temperature", temperature)
                .put("isdaynight", requireDayFlag(current))
                .put("realfeel", requireNumber(current, "apparent_temperature"))
                .put("cnweatherid", code)
                .put("weatherid", code)
                .put("zmweatherid", code)
                .put("weathertext", weatherText(current.optInt("weather_code", -1)))
                .put("sourceWeatherCode", current.getInt("weather_code"))
                .put("uVIndex", uvIndex)
                .put("humidity", humidity)
                .put("pressure", pressure)
                .put("visibility", visibility)
                .put("windspeed", windSpeed)
                .put("windlevel", windLevel(windSpeed))
                .put("winddegrees", windDegrees)
                .put("winddir", windDirection(windDegrees))
                .put("winddirtext", windDirection(windDegrees))
                .put("windgustspeed", requireNumber(current, "wind_gusts_10m"))
                .put("windgustlevel", windLevel(requireNumber(current, "wind_gusts_10m")))
                .put("cloudCover", requireNumber(current, "cloud_cover"))
                .put("precipitation", requireDouble(current, "precipitation"))
                .put("updatetime", update)
                .put("updatetimeFmt", time.formatTime(update))
                .put("expiretime", nowMs + 6 * 60 * 60 * 1000L)
                .put("desc", ATTRIBUTION).put("mobilelink", "https://open-meteo.com/");

        JSONObject hourlyData = new JSONObject().put("expiretime", nowMs + 6 * 60 * 60 * 1000L)
                .put("mobilelink", "https://open-meteo.com/");
        JSONArray hourlyItems = new JSONArray();
        long firstHour = Instant.ofEpochMilli(update).atZone(time.zone)
                .withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
        int hourlyStart = 0;
        while (hourlyStart < hours.length()
                && time.parseRequiredTimeAt(hours, hourlyStart, "hourly time") < firstHour) hourlyStart++;
        if (hours.length() - hourlyStart < REQUIRED_HOURLY_COUNT) {
            throw new JSONException("incomplete future hourly forecast");
        }
        int hourlyEnd = Math.min(hours.length(), hourlyStart + 48);
        for (int i = hourlyStart; i < hourlyEnd; i++) {
            long date = time.parseRequiredTimeAt(hours, i, "hourly time");
            int hourlyCode = weatherId(requireWeatherCode(hourly.getJSONArray("weather_code").opt(i)));
            if (hourlyCode < 0) throw new JSONException("unsupported hourly weather code");
            boolean isDay = requireDayFlagAt(hourly, i);
            JSONObject item = new JSONObject()
                    .put("date", date)
                    .put("temp", requireNumberAt(hourly, "temperature_2m", i))
                    .put("cnweatherid", hourlyCode)
                    .put("weatherid", hourlyCode)
                    .put("zmweatherid", hourlyCode)
                    .put("weathertext", weatherText(requireNumberAt(hourly, "weather_code", i)))
                    .put("sourceWeatherCode", requireNumberAt(hourly, "weather_code", i))
                    .put("rainprobability", requireNumberAt(hourly, "precipitation_probability", i))
                    .put("wd", windDirection(requireNumberAt(hourly, "wind_direction_10m", i)))
                    .put("wp", windLevel(requireNumberAt(hourly, "wind_speed_10m", i)))
                    .put("isdaynight", isDay)
                    .put("Isdaynight", isDay);
            Double precipitation = optionalNumberAt(hourly, "precipitation", i);
            if (precipitation != null) item.put("precipitation", precipitation);
            hourlyItems.put(item);
        }
        hourlyData.put("hourlyweathers", hourlyItems);

        long currentDayTime = time.parseRequiredTimeAt(days, CURRENT_DAY_INDEX, "daily time");
        JSONObject dailyData = new JSONObject()
                .put("publictime", currentDayTime)
                .put("publictimeFmt", time.formatDate(currentDayTime))
                .put("expiretime", nowMs + 24 * 60 * 60 * 1000L);
        JSONArray dailyItems = new JSONArray();
        for (int i = 0; i < REQUIRED_DAILY_COUNT; i++) {
            long publicTime = time.parseRequiredTimeAt(days, i, "daily time");
            int dailyCode = weatherId(requireWeatherCode(daily.getJSONArray("weather_code").opt(i)));
            if (dailyCode < 0) throw new JSONException("unsupported daily weather code");
            long sunrise = time.parseRequiredTimeAt(daily, "sunrise", i);
            long sunset = time.parseRequiredTimeAt(daily, "sunset", i);
            JSONObject dayCondition = new JSONObject()
                    .put("cnweatherid", dailyCode)
                    .put("weatherid", dailyCode)
                    .put("zmweatherid", dailyCode)
                    .put("weathertext", weatherText(requireNumberAt(daily, "weather_code", i)))
                    .put("sourceWeatherCode", requireNumberAt(daily, "weather_code", i))
                    .put("windlevel", windLevel(requireNumberAt(daily, "wind_speed_10m_max", i)))
                    .put("windspeed", requireNumberAt(daily, "wind_speed_10m_max", i))
                    .put("winddir", windDirection(requireNumberAt(daily, "wind_direction_10m_dominant", i)));
            int dailyUv = requireNumberAt(daily, "uv_index_max", i);
            int dailyAqi = dailyAirQualityValue(airQuality, publicTime, time);
            if (dailyAqi < 0 && i == CURRENT_DAY_INDEX) dailyAqi = airQualityValue(airQuality);
            JSONObject item = new JSONObject()
                    // DiLink 3.0 searches these anchors to choose sunset/next sunrise.
                    // Midnight anchors incorrectly classify pre-dawn updates as daytime.
                    .put("publictime", sunrise)
                    .put("publictimeFmt", time.formatDate(publicTime))
                    .put("mintemp", requireNumberAt(daily, "temperature_2m_min", i))
                    .put("maxtemp", requireNumberAt(daily, "temperature_2m_max", i))
                    .put("lv", 0)
                    .put("aqivalue", dailyAqi)
                    .put("aqivaluetext", dailyAqi < 0 ? "--" : String.valueOf(dailyAqi))
                    .put("source", ATTRIBUTION).put("mobilelink", "https://open-meteo.com/")
                    .put("uvIndex", dailyUv)
                    .put("uvIndexText", String.valueOf(dailyUv))
                    .put("sunRise", sunrise)
                    .put("sunRiseFmt", time.formatTime(sunrise))
                    .put("sunSet", sunset)
                    .put("sunSetFmt", time.formatTime(sunset))
                    .put("conditionDay", periodCondition(dayCondition, hourly, publicTime, true, time))
                    .put("conditionNight", periodCondition(dayCondition, hourly, publicTime, false, time));
            addDailyDetails(item, daily, i, publicTime, time);
            dailyItems.put(item);
        }
        dailyData.put("dailyweathers", dailyItems);

        int aqi = airQualityValue(airQuality);
        JSONObject aqiData = new JSONObject()
                .put("aqivalue", aqi)
                .put("aqivaluetext", aqi < 0 ? "--" : String.valueOf(aqi))
                .put("aqidesc", aqiDescription(aqi))
                .put("lv", "0")
                .put("pm25", airQualityNumber(airQuality, "pm2_5"))
                .put("pm10", airQualityNumber(airQuality, "pm10"))
                .put("updatetime", nowMs).put("mobilelink", "https://open-meteo.com/");

        JSONObject data = new JSONObject()
                .put("city", new JSONObject()
                        .put("name", city)
                        .put("englishCityName", english)
                        .put("countryname", "")
                        .put("englishCountryName", "")
                        .put("countryCode", "")
                        .put("timezone", forecast.optString("timezone", "")))
                .put("condition", condition)
                .put("hourlys", hourlyData)
                .put("dailys", dailyData)
                .put("aqi", aqiData)
                .put("aqidays", new JSONArray())
                .put("alarm", new JSONArray())
                .put("liveInfos", new JSONArray())
                .put("openMeteo", sourceData(forecast, airQuality, hourlyStart, hourlyEnd))
                .put("weatherDesc", ATTRIBUTION)
                .put("mobilelink", "https://open-meteo.com/");
        return new JSONObject()
                .put("resultcode", "0")
                .put("resultinfo", "success")
                .put("servertime", nowMs)
                .put("data", data)
                .toString();
    }

    /** Representative hourly conditions, not accumulated half-day precipitation totals. */
    private static JSONObject periodCondition(JSONObject fallback, JSONObject hourly,
            long publicTime, boolean day, ForecastTime time) throws JSONException {
        JSONObject result = new JSONObject(fallback.toString());
        JSONArray hours = hourly.getJSONArray("time");
        String date = time.formatDate(publicTime);
        long target = LocalDate.parse(date).atTime(day ? 12 : 21, 0)
                .atZone(time.zone).toInstant().toEpochMilli();
        int selected = -1;
        long nearest = Long.MAX_VALUE;
        for (int i = 0; i < hours.length(); i++) {
            long hour = time.parseTime(hours.optString(i, ""));
            Double flag = optionalNumberAt(hourly, "is_day", i);
            Double code = optionalNumberAt(hourly, "weather_code", i);
            if (hour <= 0 || (!day && Instant.ofEpochMilli(hour).atZone(time.zone).getHour() < 12)
                    || !date.equals(time.formatDate(hour)) || flag == null
                    || flag != (day ? 1 : 0) || code == null || code != Math.rint(code)
                    || weatherId(code.intValue()) < 0
                    || optionalNumberAt(hourly, "wind_speed_10m", i) == null
                    || optionalNumberAt(hourly, "wind_direction_10m", i) == null) continue;
            long distance = Math.abs(hour - target);
            if (distance < nearest) {
                selected = i;
                nearest = distance;
            }
        }
        if (selected < 0) return result;
        int wmo = requireNumberAt(hourly, "weather_code", selected);
        int code = weatherId(wmo);
        int speed = requireNumberAt(hourly, "wind_speed_10m", selected);
        result.put("cnweatherid", code).put("weatherid", code).put("zmweatherid", code)
                .put("weathertext", weatherText(wmo)).put("sourceWeatherCode", wmo).put("windspeed", speed)
                .put("windlevel", windLevel(speed))
                .put("winddir", windDirection(requireNumberAt(hourly, "wind_direction_10m", selected)));
        Double humidity = optionalNumberAt(hourly, "relative_humidity_2m", selected);
        Double cloud = optionalNumberAt(hourly, "cloud_cover", selected);
        Double probability = optionalNumberAt(hourly, "precipitation_probability", selected);
        Double gust = optionalNumberAt(hourly, "wind_gusts_10m", selected);
        if (humidity != null) result.put("humidity", Math.round(humidity));
        if (cloud != null) result.put("cloudCover", String.valueOf(Math.round(cloud)));
        if (probability != null) result.put("precProb", String.valueOf(Math.round(probability)));
        if (gust != null) result.put("windGustPow", String.valueOf(windLevel((int) Math.round(gust))));
        return result;
    }

    private static void addDailyDetails(JSONObject item, JSONObject daily, int index,
            long publicTime, ForecastTime time) throws JSONException {
        Double apparentMax = optionalNumberAt(daily, "apparent_temperature_max", index);
        Double apparentMin = optionalNumberAt(daily, "apparent_temperature_min", index);
        if (apparentMax != null) item.put("realFeelTempMax", String.valueOf(Math.round(apparentMax)));
        if (apparentMin != null) item.put("realFeelTempMin", String.valueOf(Math.round(apparentMin)));
        long moonrise = time.optionalTimeAt(daily, "moonrise", index);
        long moonset = time.optionalTimeAt(daily, "moonset", index);
        if (moonrise > 0) item.put("moonRise", moonrise).put("moonRiseFmt", time.formatOffsetTime(moonrise));
        if (moonset > 0) item.put("moonSet", moonset);
        // The stock adapter selects today's min/max by this date, even without a lunar event.
        long selectorTime = moonset > 0 && time.formatDate(moonset).equals(time.formatDate(publicTime))
                ? moonset : publicTime;
        item.put("moonSetFmt", time.formatOffsetTime(selectorTime));
    }

    /** Owned extension: original units/precision, rather than guessed proprietary native fields. */
    private static JSONObject sourceData(JSONObject forecast, JSONObject air, int firstHour, int endHour)
            throws JSONException {
        JSONObject result = new JSONObject().put("schemaVersion", 1)
                .put("units", new JSONObject()
                        .put("current", sourceObject(forecast, "current_units"))
                        .put("hourly", sourceObject(forecast, "hourly_units"))
                        .put("daily", sourceObject(forecast, "daily_units"))
                        .put("airQuality", sourceObject(air, "current_units")))
                .put("current", sourceObject(forecast, "current"))
                .put("hourly", sourceRows(forecast.getJSONObject("hourly"), firstHour, endHour))
                .put("daily", sourceRows(forecast.getJSONObject("daily"), 0, REQUIRED_DAILY_COUNT))
                .put("airQualityStandard", "European AQI")
                .put("airQuality", sourceObject(air, "current"));
        JSONArray aqiDays = new JSONArray();
        ForecastTime time = new ForecastTime(forecast.getString("timezone"));
        JSONArray dates = forecast.getJSONObject("daily").getJSONArray("time");
        for (int i = 0; i < REQUIRED_DAILY_COUNT; i++) {
            int value = dailyAirQualityValue(air, time.parseRequiredTimeAt(dates, i, "daily time"), time);
            JSONObject day = new JSONObject().put("date", dates.getString(i));
            if (value >= 0) day.put("european_aqi", value).put("description", aqiDescription(value));
            aqiDays.put(day);
        }
        return result.put("airQualityDaily", aqiDays);
    }

    private static JSONObject sourceObject(JSONObject source, String key) {
        JSONObject value = source == null ? null : source.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static JSONArray sourceRows(JSONObject source, int first, int end) throws JSONException {
        JSONArray result = new JSONArray();
        for (int i = first; i < end; i++) {
            JSONObject row = new JSONObject();
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray values = source.optJSONArray(key);
                Object value = values == null ? null : values.opt(i);
                if (value != null && value != JSONObject.NULL) row.put(key, value);
            }
            result.put(row);
        }
        return result;
    }

    public static boolean isComplete(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            JSONObject root = new JSONObject(json);
            if (!"0".equals(root.optString("resultcode"))
                    || root.optString("resultinfo").isEmpty()
                    || root.optLong("servertime", 0L) <= 0L) return false;
            JSONObject data = root.optJSONObject("data");
            JSONObject city = data == null ? null : data.optJSONObject("city");
            JSONObject condition = data == null ? null : data.optJSONObject("condition");
            JSONObject daily = data == null ? null : data.optJSONObject("dailys");
            JSONObject hourly = data == null ? null : data.optJSONObject("hourlys");
            JSONArray days = daily == null ? null : daily.optJSONArray("dailyweathers");
            JSONArray hours = hourly == null ? null : hourly.optJSONArray("hourlyweathers");
            if (city == null || text(city, "name") == null || text(city, "englishCityName") == null
                    || condition == null || !(condition.opt("isdaynight") instanceof Boolean)
                    || !numberPresent(condition, "temperature")
                    || !validNativeCode(condition) || text(condition, "weathertext") == null
                    || !numberPresent(condition, "uVIndex") || !numberPresent(condition, "windspeed")
                    || !numberPresent(condition, "windlevel") || text(condition, "winddir") == null
                    || text(condition, "winddirtext") == null || !numberPresent(condition, "visibility")
                    || !numberPresent(condition, "humidity") || !numberPresent(condition, "pressure")
                    || !numberPresent(condition, "updatetime") || text(condition, "updatetimeFmt") == null
                    || days == null || hours == null
                    || !hasRequiredForecastCounts(hours.length(), days.length())) {
                return false;
            }
            long previousAnchor = 0;
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day == null || !numberPresent(day, "publictime")
                        || day.optLong("publictime") <= previousAnchor
                        || text(day, "publictimeFmt") == null
                        || !numberPresent(day, "mintemp") || !numberPresent(day, "maxtemp")
                        || !numberPresent(day, "lv") || !numberPresent(day, "sunRise")
                        || !numberPresent(day, "sunSet") || day.optLong("sunRise") <= 0
                        || day.optLong("sunSet") <= day.optLong("sunRise")) return false;
                previousAnchor = day.getLong("publictime");
                for (String key : new String[]{"conditionDay", "conditionNight"}) {
                    JSONObject period = day.optJSONObject(key);
                    if (!validNativeCode(period) || text(period, "weathertext") == null) return false;
                }
            }
            long previousHour = 0;
            for (int i = 0; i < hours.length(); i++) {
                JSONObject item = hours.optJSONObject(i);
                if (item == null || !numberPresent(item, "date") || !numberPresent(item, "temp")
                        || item.optLong("date") <= previousHour || !validNativeCode(item)
                        || !(item.opt("isdaynight") instanceof Boolean)
                        || !(item.opt("Isdaynight") instanceof Boolean)
                        || !item.opt("isdaynight").equals(item.opt("Isdaynight"))) return false;
                previousHour = item.getLong("date");
            }
            return true;
        } catch (JSONException | RuntimeException ignored) {
            return false;
        }
    }

    public static int clampIntervalMinutes(int value) {
        return Math.max(5, Math.min(180, value));
    }

    private static boolean validNativeCode(JSONObject condition) {
        Object value = condition == null ? null : condition.opt("cnweatherid");
        if (!(value instanceof Number)) return false;
        double code = ((Number) value).doubleValue();
        if (!Double.isFinite(code) || code != Math.rint(code)) return false;
        return switch ((int) code) {
            case 0,1,2,3,4,5,7,8,9,13,14,15,16,18,19 -> true;
            default -> false;
        };
    }

    static boolean hasRequiredForecastCounts(int hourlyCount, int dailyCount) {
        return hourlyCount >= REQUIRED_HOURLY_COUNT && dailyCount >= REQUIRED_DAILY_COUNT;
    }

    public static int weatherId(int openMeteoCode) {
        if (openMeteoCode == 0) return 0;
        if (openMeteoCode == 1 || openMeteoCode == 2) return 1;
        if (openMeteoCode == 3) return 2;
        if (openMeteoCode == 45 || openMeteoCode == 48) return 18;
        if (openMeteoCode == 56 || openMeteoCode == 57
                || openMeteoCode == 66 || openMeteoCode == 67) return 19;
        if (openMeteoCode == 51 || openMeteoCode == 61) return 7;
        if (openMeteoCode == 53 || openMeteoCode == 63) return 8;
        if (openMeteoCode == 55 || openMeteoCode == 65) return 9;
        if (openMeteoCode == 71) return 14;
        if (openMeteoCode == 73) return 15;
        if (openMeteoCode == 75) return 16;
        if (openMeteoCode == 77) return 14;
        if (openMeteoCode >= 80 && openMeteoCode <= 82) return 3;
        if (openMeteoCode >= 85 && openMeteoCode <= 86) return 13;
        if (openMeteoCode == 95 || openMeteoCode == 97) return 4;
        if (openMeteoCode == 96 || openMeteoCode == 99) return 5;
        return -1;
    }

    private static String weatherText(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Dense freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snowfall";
            case 73 -> "Moderate snowfall";
            case 75 -> "Heavy snowfall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 97 -> "Heavy thunderstorm";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Unknown";
        };
    }

    private static boolean requireDayFlag(JSONObject current) throws JSONException {
        Object value = current.opt("is_day");
        if (!(value instanceof Number)) throw new JSONException("missing or invalid is_day");
        double flag = ((Number) value).doubleValue();
        if (flag != 0 && flag != 1) throw new JSONException("invalid is_day");
        return flag == 1;
    }

    private static int requireWeatherCode(Object value) throws JSONException {
        if (!(value instanceof Number)) throw new JSONException("missing weather code");
        double code = ((Number) value).doubleValue();
        if (!Double.isFinite(code) || code != Math.rint(code)
                || code < 0 || code > 99 || weatherId((int) code) < 0) {
            throw new JSONException("unsupported weather code");
        }
        return (int) code;
    }

    private static boolean requireDayFlagAt(JSONObject hourly, int index) throws JSONException {
        Double flag = optionalNumberAt(hourly, "is_day", index);
        if (flag == null || (flag != 0 && flag != 1)) throw new JSONException("invalid hourly is_day");
        return flag == 1;
    }

    private static Double optionalNumberAt(JSONObject object, String key, int index) {
        JSONArray values = object == null ? null : object.optJSONArray(key);
        Object value = values == null || index < 0 || index >= values.length() ? null : values.opt(index);
        if (!(value instanceof Number)) return null;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) ? number : null;
    }

    private static int number(JSONObject object, String key) {
        return number(object, key, 0);
    }

    private static int number(JSONObject object, String key, int fallback) {
        if (!object.has(key) || object.isNull(key)) return fallback;
        double value = object.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? fallback : (int) Math.round(value);
    }

    private static int requireNumber(JSONObject object, String key) throws JSONException {
        if (object == null || !object.has(key) || object.isNull(key)) {
            throw new JSONException("missing " + key);
        }
        double value = object.optDouble(key, Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new JSONException("invalid " + key);
        return (int) Math.round(value);
    }

    private static double requireDouble(JSONObject object, String key) throws JSONException {
        if (object == null || !object.has(key) || object.isNull(key)) {
            throw new JSONException("missing " + key);
        }
        double value = object.optDouble(key, Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new JSONException("invalid " + key);
        return value;
    }

    private static int requireNumberAt(JSONObject object, String key, int index) throws JSONException {
        JSONArray values = object == null ? null : object.optJSONArray(key);
        if (values == null || index < 0 || index >= values.length() || values.isNull(index)) {
            throw new JSONException("missing " + key + "[" + index + "]");
        }
        double value = values.optDouble(index, Double.NaN);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new JSONException("invalid " + key + "[" + index + "]");
        }
        return (int) Math.round(value);
    }

    private static int numberAt(JSONObject object, String key, int index, int fallback) {
        JSONArray array = object.optJSONArray(key);
        return array == null || index < 0 || index >= array.length() || array.isNull(index)
                ? fallback : (int) Math.round(array.optDouble(index, fallback));
    }

    private static String text(JSONObject object, String key) {
        String value = object.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static boolean numberPresent(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return false;
        double value = object.optDouble(key, Double.NaN);
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /** Parses and formats provider timestamps in the forecast location's time zone. */
    private static final class ForecastTime {
        final ZoneId zone;

        ForecastTime(String zoneName) throws JSONException {
            try {
                zone = ZoneId.of(zoneName);
            } catch (RuntimeException invalid) {
                throw new JSONException("invalid forecast timezone");
            }
        }

        private long parseTime(String value) {
            if (value == null || value.isEmpty()) return -1L;
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (RuntimeException ignored) {
            }
            try {
                return LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
            }
            try {
                return LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli();
            } catch (RuntimeException ignored) {
                return -1L;
            }
        }

        long parseRequiredTime(JSONObject object, String key) throws JSONException {
            String value = object == null ? "" : object.optString(key, "");
            long parsed = parseTime(value);
            if (parsed <= 0L) throw new JSONException("missing " + key);
            return parsed;
        }

        long parseRequiredTimeAt(JSONArray values, int index, String name) throws JSONException {
            if (values == null || index < 0 || index >= values.length()) throw new JSONException("missing " + name);
            long parsed = parseTime(values.optString(index, ""));
            if (parsed <= 0L) throw new JSONException("missing " + name);
            return parsed;
        }

        long parseRequiredTimeAt(JSONObject object, String key, int index) throws JSONException {
            JSONArray values = object == null ? null : object.optJSONArray(key);
            return parseRequiredTimeAt(values, index, key);
        }

        long optionalTimeAt(JSONObject object, String key, int index) {
            JSONArray values = object.optJSONArray(key);
            Object value = values == null ? null : values.opt(index);
            return value instanceof String ? parseTime((String) value) : -1L;
        }

        String formatOffsetTime(long value) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone).format(Instant.ofEpochMilli(value));
        }

        String formatTime(long value) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                    .withZone(zone).format(Instant.ofEpochMilli(value));
        }

        String formatDate(long value) {
            return DateTimeFormatter.ISO_LOCAL_DATE.withZone(zone).format(Instant.ofEpochMilli(value));
        }
    }

    private static int windLevel(int kmh) {
        // Standard Beaufort upper bounds in km/h; native speeds remain km/h.
        int[] upperBounds = {1, 5, 11, 19, 28, 38, 49, 61, 74, 88, 102, 117};
        for (int force = 0; force < upperBounds.length; force++) {
            if (kmh <= upperBounds[force]) return force;
        }
        return 12;
    }

    private static String windDirection(int degrees) {
        String[] labels = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return labels[((degrees % 360 + 360) % 360 + 22) / 45 % labels.length];
    }

    private static int dailyAirQualityValue(JSONObject airQuality, long date, ForecastTime time) {
        JSONObject hourly = airQuality == null ? null : airQuality.optJSONObject("hourly");
        JSONArray hours = hourly == null ? null : hourly.optJSONArray("time");
        if (hours == null) return -1;
        ForecastTime sourceTime;
        try {
            sourceTime = new ForecastTime(airQuality.optString("timezone", time.zone.getId()));
        } catch (JSONException invalid) {
            return -1;
        }
        String localDate = time.formatDate(date);
        int worst = -1;
        for (int i = 0; i < hours.length(); i++) {
            long hour = sourceTime.parseTime(hours.optString(i, ""));
            Double aqi = optionalNumberAt(hourly, "european_aqi", i);
            if (hour > 0 && localDate.equals(time.formatDate(hour)) && aqi != null && aqi >= 0) {
                worst = Math.max(worst, (int) Math.round(aqi));
            }
        }
        return worst;
    }

    private static int airQualityValue(JSONObject airQuality) {
        if (airQuality == null) return -1;
        JSONObject current = airQuality.optJSONObject("current");
        if (current != null && current.has("european_aqi")) return number(current, "european_aqi", -1);
        return numberAt(airQuality, "european_aqi", 0, -1);
    }

    private static int airQualityNumber(JSONObject airQuality, String key) {
        if (airQuality == null) return -1;
        JSONObject current = airQuality.optJSONObject("current");
        if (current != null && current.has(key)) return number(current, key, -1);
        return numberAt(airQuality, key, 0, -1);
    }

    private static String aqiDescription(int value) {
        return value < 0 ? "Unknown" : value <= 20 ? "Good" : value <= 40 ? "Fair"
                : value <= 60 ? "Moderate" : value <= 80 ? "Poor" : value <= 100 ? "Very poor" : "Extremely poor";
    }

}
