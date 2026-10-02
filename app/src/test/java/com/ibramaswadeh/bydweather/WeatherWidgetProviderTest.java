package com.ibramaswadeh.bydweather;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import org.junit.Test;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the public Android widget update and the views delivered to its host. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class WeatherWidgetProviderTest {
    @Test public void displaysLiquidPrecipitationAndSnowDepthWithTheirSourceUnits() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        JSONObject payload;
        try (InputStream stream = getClass().getResourceAsStream("/amman-pre-dawn.json")) {
            payload = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        payload.getJSONObject("data").put("openMeteo", new JSONObject()
                .put("hourly", new org.json.JSONArray().put(new JSONObject())
                        .put(new JSONObject().put("precipitation", 1.2).put("snowfall", 0.8)))
                .put("daily", new org.json.JSONArray().put(new JSONObject())
                        .put(new JSONObject().put("precipitation_probability_max", 80)
                                .put("precipitation_sum", 5.6).put("snowfall_sum", 3.4))));
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("weather_widget_payload", payload.toString()).apply();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(WeatherWidgetProvider.class, R.layout.widget_weather);
        new WeatherWidgetProvider(Clock.fixed(Instant.parse("2026-10-01T23:00:00Z"), ZoneOffset.UTC))
                .onUpdate(context, manager, new int[]{id});
        View root = shadowOf(manager).getViewFor(id);
        ViewGroup hours = root.findViewById(R.id.widget_hours);
        TextView hourlyAmounts = hours.getChildAt(0).findViewById(R.id.forecast_precipitation);
        assertTrue(hourlyAmounts.getText().toString().contains("1.2 mm"));
        assertTrue(hourlyAmounts.getText().toString().contains("0.8 cm"));
        assertTrue(hourlyAmounts.getContentDescription().toString().contains("snowfall"));
        ViewGroup days = root.findViewById(R.id.widget_days);
        TextView dailyAmounts = days.getChildAt(0).findViewById(R.id.forecast_precipitation);
        assertEquals("80%\n5.6 mm\n3.4 cm", dailyAmounts.getText().toString());
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void rendersEveryMappedIconAndFitsTheDeclaredWidgetSize() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        JSONObject base;
        try (InputStream stream = getClass().getResourceAsStream("/amman-pre-dawn.json")) {
            base = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        base.getJSONObject("data").getJSONObject("aqi").put("aqivalue", 35).put("aqidesc", "Fair")
                .put("pm25", 12).put("pm10", 25);
        base.getJSONObject("data").put("openMeteo", new JSONObject().put("daily", new org.json.JSONArray()
                .put(new JSONObject()).put(new JSONObject().put("precipitation_probability_max", 100).put("precipitation_sum", 125.5).put("snowfall_sum", 125.5))));
        org.json.JSONArray wetHours = new org.json.JSONArray();
        for (int i = 0; i < 8; i++) wetHours.put(new JSONObject().put("precipitation", 125.5).put("snowfall", 125.5));
        base.getJSONObject("data").getJSONObject("openMeteo").put("hourly", wetHours);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(WeatherWidgetProvider.class, R.layout.widget_weather);
        int[] codes = {0,1,2,3,4,5,7,8,9,13,14,15,16,18,19};
        int[] icons = {R.drawable.weather_moon,R.drawable.weather_cloud,R.drawable.weather_overcast,
                R.drawable.weather_rain,R.drawable.weather_thunder,R.drawable.weather_thunder,
                R.drawable.weather_rain,R.drawable.weather_rain,R.drawable.weather_rain,
                R.drawable.weather_snow,R.drawable.weather_snow,R.drawable.weather_snow,
                R.drawable.weather_snow,R.drawable.weather_fog,R.drawable.weather_rain};
        for (int n = 0; n < codes.length; n++) {
            JSONObject payload = new JSONObject(base.toString());
            payload.getJSONObject("data").getJSONObject("hourlys").getJSONArray("hourlyweathers")
                    .getJSONObject(1).put("cnweatherid", codes[n]);
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putString("weather_widget_payload", payload.toString()).apply();
            new WeatherWidgetProvider(Clock.fixed(Instant.parse("2026-10-01T23:00:00Z"), ZoneOffset.UTC))
                    .onUpdate(context, manager, new int[]{id});
            View root = shadowOf(manager).getViewFor(id);
            ViewGroup hours = root.findViewById(R.id.widget_hours);
            ImageView image = hours.getChildAt(0).findViewById(R.id.forecast_icon);
            assertEquals("Native category " + codes[n], icons[n], shadowOf(image.getDrawable()).getCreatedFromResId());
        }
        View root = shadowOf(manager).getViewFor(id);
        for (int width : new int[]{400, 840}) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertTrue("Widget height at " + width + "dp: " + root.getMeasuredHeight(), root.getMeasuredHeight() <= 560);
            root.layout(0, 0, width, root.getMeasuredHeight());
            String output = System.getenv("BYD_WEATHER_TRACE_DIR");
            if (output != null) {
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, root.getMeasuredHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                root.draw(new android.graphics.Canvas(bitmap));
                try (java.io.FileOutputStream file = new java.io.FileOutputStream(output + "/widget-" + width + ".png")) {
                    assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file));
                }
                bitmap.recycle();
            }
        }
    }

    @Test public void recoversFromEmptyViewsAndLabelsSourceMetricsAndStaleData() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(WeatherWidgetProvider.class, R.layout.widget_weather);
        View root = shadowOf(manager).getViewFor(id);
        assertEquals("Waiting for weather", ((TextView) root.findViewById(R.id.widget_condition)).getText().toString());
        JSONObject payload;
        try (InputStream stream = getClass().getResourceAsStream("/amman-pre-dawn.json")) {
            payload = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        payload.getJSONObject("data").getJSONObject("condition").put("uVIndex", 0).put("windlevel", 12);
        payload.getJSONObject("data").put("openMeteo", new JSONObject()
                .put("daily", new org.json.JSONArray().put(new JSONObject())
                        .put(new JSONObject().put("precipitation_probability_max", 75).put("precipitation_sum", 4.1))));
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("weather_widget_payload", payload.toString()).apply();
        new WeatherWidgetProvider(Clock.fixed(Instant.parse("2026-10-02T00:30:00Z"), ZoneOffset.UTC))
                .onUpdate(context, manager, new int[]{id});
        root = shadowOf(manager).getViewFor(id);
        assertEquals(View.VISIBLE, root.findViewById(R.id.widget_hours_label).getVisibility());
        assertTrue(((TextView) root.findViewById(R.id.widget_updated)).getText().toString().contains("Stale"));
        String metrics = ((TextView) root.findViewById(R.id.widget_metrics)).getText().toString();
        assertTrue(metrics.contains("UV 0 Low"));
        assertTrue(metrics.contains("Force 12"));
        ViewGroup days = root.findViewById(R.id.widget_days);
        assertEquals("75%\n4.1 mm", ((TextView) days.getChildAt(0).findViewById(R.id.forecast_precipitation)).getText().toString());
    }

    @Test public void rendersNightAndDayWithinTheSameHourlyForecast() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        try (InputStream stream = getClass().getResourceAsStream("/amman-pre-dawn.json")) {
            assertNotNull(stream);
            JSONObject payload = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            // A clear 07:00 worked example, retaining the captured independent day flag.
            payload.getJSONObject("data").getJSONObject("hourlys").getJSONArray("hourlyweathers")
                    .getJSONObject(5).put("cnweatherid", 0).put("weathertext", "Clear sky");
            payload.getJSONObject("data").getJSONObject("dailys").getJSONArray("dailyweathers")
                    .getJSONObject(1).getJSONObject("conditionDay").put("cnweatherid", 0);
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putString("weather_widget_payload", payload.toString())
                    .apply();
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int id = shadowOf(manager).createWidget(WeatherWidgetProvider.class, R.layout.widget_weather);
        new WeatherWidgetProvider(Clock.fixed(Instant.parse("2026-10-01T23:00:00Z"), ZoneOffset.UTC))
                .onUpdate(context, manager, new int[]{id});
        View root = shadowOf(manager).getViewFor(id);
        ViewGroup hours = root.findViewById(R.id.widget_hours);
        assertEquals(7, hours.getChildCount());
        assertEquals("03:00", ((TextView) hours.getChildAt(0).findViewById(R.id.forecast_time)).getText().toString());
        ImageView night = hours.getChildAt(0).findViewById(R.id.forecast_icon);
        assertEquals(R.drawable.weather_moon, shadowOf(night.getDrawable()).getCreatedFromResId());
        assertTrue(night.getContentDescription().toString().contains("night"));
        ImageView day = hours.getChildAt(4).findViewById(R.id.forecast_icon);
        assertEquals("07:00", ((TextView) hours.getChildAt(4).findViewById(R.id.forecast_time)).getText().toString());
        assertEquals(R.drawable.weather_sun, shadowOf(day.getDrawable()).getCreatedFromResId());
        ViewGroup days = root.findViewById(R.id.widget_days);
        assertEquals(R.drawable.weather_sun, shadowOf(((ImageView) days.getChildAt(0)
                .findViewById(R.id.forecast_icon)).getDrawable()).getCreatedFromResId());
    }
}
