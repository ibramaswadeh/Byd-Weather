package com.ibramaswadeh.bydweather;

import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.*;

/** Parses the complete payload before constructing the factory's forecast lists. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherForecastScreenTest {
    @Test public void realMetForecastsOpenBothForecastLists() throws Exception {
        for (String resource : new String[]{"met-forecast-moscow.json", "met-forecast-norway.json"}) {
            JSONObject source = WeatherMappingTest.resource(resource);
            long now = MetForecast.parse(source).points.get(0).time + 30 * 60_000;
            String payload = WeatherMapping.toBydJson(MetFixtures.load(source, now, ZoneId.of("UTC")), "City", "City");
            assertForecastRowsDecode(payload);
        }
    }

    @Test public void fractionalFeelsLikeStillOpensBothForecastLists() throws Exception {
        assertEquals(Integer.valueOf(13),
                fractionalReading("apparent_air_temperature", 13.4, false).data.condition.realfeel);
        assertEquals(Integer.valueOf(-14),
                fractionalReading("apparent_air_temperature", -13.6, false).data.condition.realfeel);
    }

    @Test public void fractionalCloudCoverStillOpensBothForecastLists() throws Exception {
        assertEquals(Integer.valueOf(88),
                fractionalReading("cloud_area_fraction", 87.5, false).data.condition.cloudCover);
    }

    @Test public void fractionalRainProbabilityStillOpensBothForecastLists() throws Exception {
        assertEquals(Integer.valueOf(63), fractionalReading("probability_of_precipitation", 62.8, true)
                .data.hourlys.hourlyweathers.get(0).rainprobability);
    }

    @Test public void validatorRejectsValuesThatWouldHideBothForecastLists() throws Exception {
        for (String field : new String[]{"realfeel", "cloudCover", "rainprobability"}) {
            JSONObject payload = MetFixtures.payload(MetFixtures.NOW, "cloudy");
            JSONObject owner = field.equals("rainprobability")
                    ? payload.getJSONObject("data").getJSONObject("hourlys").getJSONArray("hourlyweathers").getJSONObject(0)
                    : payload.getJSONObject("data").getJSONObject("condition");
            owner.put(field, 13.4);
            assertFalse("Reject incompatible native field " + field, WeatherMapping.isComplete(payload.toString()));
        }
    }

    @Test public void validatorRejectsInvalidIntegersThroughoutTheForecast() throws Exception {
        String[] paths = {
                "condition/temperature", "condition/windlevel", "condition/cnweatherid",
                "hourlys/hourlyweathers/0/temp", "hourlys/hourlyweathers/0/wp",
                "dailys/dailyweathers/2/mintemp", "dailys/dailyweathers/2/aqivalue",
                "dailys/dailyweathers/2/conditionDay/cnweatherid",
                "dailys/dailyweathers/2/conditionNight/windlevel"
        };
        String valid = MetFixtures.payload(MetFixtures.NOW, "cloudy").toString();
        for (String path : paths) {
            for (Object invalid : new Object[]{13.4, 2147483648L, -2147483649L, "unknown"}) {
                JSONObject payload = new JSONObject(valid);
                Object owner = payload.getJSONObject("data");
                String[] keys = path.split("/");
                for (int i = 0; i < keys.length - 1; i++) {
                    owner = owner instanceof JSONArray
                            ? ((JSONArray) owner).get(Integer.parseInt(keys[i]))
                            : ((JSONObject) owner).get(keys[i]);
                }
                ((JSONObject) owner).put(keys[keys.length - 1], invalid);
                assertFalse(path + "=" + invalid, WeatherMapping.isComplete(payload.toString()));
            }
        }
    }

    @Test public void absentOptionalReadingsStayAbsentAndPrecipitationRetainsDecimals() throws Exception {
        JSONObject source = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 18);
        JSONObject first = source.getJSONObject("properties").getJSONArray("timeseries")
                .getJSONObject(0).getJSONObject("data");
        first.getJSONObject("instant").getJSONObject("details").remove("cloud_area_fraction");
        first.getJSONObject("next_1_hours").getJSONObject("details").remove("probability_of_precipitation");
        NativeWeather weather = assertForecastRowsDecode(WeatherMapping.toBydJson(
                MetFixtures.load(source, MetFixtures.NOW, MetFixtures.ZONE), "City", "City"));
        assertNull(weather.data.condition.realfeel);
        assertNull(weather.data.condition.cloudCover);
        assertNull(weather.data.hourlys.hourlyweathers.get(0).rainprobability);
        assertEquals(0.5f, weather.data.condition.precipitation, 0.0001f);
    }

    private static NativeWeather fractionalReading(String field, double value, boolean period) throws Exception {
        JSONObject source = MetFixtures.forecast(MetFixtures.NOW, "cloudy", 18);
        JSONObject first = source.getJSONObject("properties").getJSONArray("timeseries")
                .getJSONObject(0).getJSONObject("data");
        first.getJSONObject(period ? "next_1_hours" : "instant").getJSONObject("details").put(field, value);
        String payload = WeatherMapping.toBydJson(MetFixtures.load(source, MetFixtures.NOW, MetFixtures.ZONE), "City", "City");
        return assertForecastRowsDecode(payload);
    }

    static NativeWeather assertForecastRowsDecode(String payload) {
        assertTrue(WeatherMapping.isComplete(payload));
        NativeWeather weather = new Gson().fromJson(payload, NativeWeather.class);
        assertNotNull(weather.data);
        assertNotNull(weather.data.city.name);
        assertNotNull(weather.data.condition);
        assertEquals(7, weather.data.hourlys.hourlyweathers.subList(1, 8).size());
        assertEquals(7, weather.data.dailys.dailyweathers.subList(2, 9).size());
        for (NativeHour hour : weather.data.hourlys.hourlyweathers.subList(1, 8)) {
            assertNotNull(hour.date);
            assertNotNull(hour.temp);
            assertNotNull(hour.cnweatherid);
        }
        for (NativeDay day : weather.data.dailys.dailyweathers.subList(2, 9)) {
            assertNotNull(day.publictime);
            assertNotNull(day.mintemp);
            assertNotNull(day.maxtemp);
            assertNotNull(day.conditionDay.cnweatherid);
        }
        return weather;
    }

    // Field types are independent of the writer: REA WeatherInfoBean metadata.
    static final class NativeWeather {
        NativeData data;
        Long servertime;
        String resultcode, resultinfo;
    }
    static final class NativeData {
        NativeCity city;
        NativeCondition condition;
        NativeHours hourlys;
        NativeDays dailys;
    }
    static final class NativeCity { String name, englishCityName, timezone; }
    static final class NativeCondition {
        Integer temperature, cloudCover, cnweatherid, humidity, pressure, realfeel, uVIndex,
                visibility, weatherid, winddegrees, windgustlevel, windgustspeed, windlevel, windspeed, zmweatherid;
        Long expiretime, updatetime;
        Float precipitation;
        String updatetimeFmt, winddir, winddirtext, weathertext, desc, mobilelink, uvIndexDesc;
    }
    static final class NativeHours { List<NativeHour> hourlyweathers; }
    static final class NativeHour {
        Integer cnweatherid, rainprobability, temp, weatherid, wp, zmweatherid;
        Long date;
        Boolean isdaynight;
        String wd;
    }
    static final class NativeDays {
        List<NativeDay> dailyweathers;
        Long publictime;
        String publictimeFmt, mobilelink;
    }
    static final class NativeDay {
        Integer aqivalue, lv, maxtemp, mintemp;
        Long publictime, sunRise, sunSet;
        String publictimeFmt, moonSetFmt, sunRiseFmt, sunSetFmt, aqivaluetext, mobilelink, source;
        NativeDayCondition conditionDay, conditionNight;
    }
    static final class NativeDayCondition {
        Integer cnweatherid, humidity, weatherid, windlevel, windspeed, zmweatherid;
        String cloudCover, weathertext, winddir;
    }
}
