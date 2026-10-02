package com.ibramaswadeh.bydweather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders last-good weather independently of the stock renderer's global night flag. */
public final class WeatherWidgetProvider extends AppWidgetProvider {
    static final String PREF_PAYLOAD = "weather_widget_payload";
    private final Clock clock;

    public WeatherWidgetProvider() {
        this(Clock.systemUTC());
    }

    public WeatherWidgetProvider(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = render(context, clock.millis());
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, Bundle options) {
        onUpdate(context, manager, new int[]{id});
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
        if (ids.length > 0) new WeatherWidgetProvider().onUpdate(context, manager, ids);
    }

    private static RemoteViews render(Context context, long now) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather);
        Intent open = new Intent(context, MainActivity.class);
        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0,
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        String payload = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(PREF_PAYLOAD, null);
        if (!WeatherMapping.isComplete(payload)) return empty(views);
        try {
            JSONObject data = new JSONObject(payload).getJSONObject("data");
            JSONObject current = data.getJSONObject("condition");
            ZoneId zone = ZoneId.of(data.getJSONObject("city").getString("timezone"));
            views.setTextViewText(R.id.widget_city, data.getJSONObject("city").getString("name"));
            long released = current.getLong("updatetime");
            String updated = "Updated " + format(released, "dd MMM HH:mm", zone)
                    + " " + "UTC" + format(released, "xxx", zone);
            if (now - released >= 60 * 60 * 1000L) updated += " · Stale";
            views.setTextViewText(R.id.widget_updated, updated);
            views.setTextViewText(R.id.widget_temperature, current.getInt("temperature") + "°C");
            views.setTextViewText(R.id.widget_condition, current.getString("weathertext"));
            setIcon(views, R.id.widget_icon, current.getInt("cnweatherid"),
                    current.getBoolean("isdaynight"), current.getString("weathertext"));
            views.setTextViewText(R.id.widget_metrics, metrics(current, data.optJSONObject("aqi")));
            views.setViewVisibility(R.id.widget_hours_label, View.VISIBLE);
            views.setViewVisibility(R.id.widget_days_label, View.VISIBLE);
            JSONObject source = data.optJSONObject("openMeteo");
            JSONArray sourceDays = source == null ? null : source.optJSONArray("daily");
            JSONArray sourceHours = source == null ? null : source.optJSONArray("hourly");
            JSONArray days = data.getJSONObject("dailys").getJSONArray("dailyweathers");
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            views.removeAllViews(R.id.widget_hours);
            JSONArray hours = data.getJSONObject("hourlys").getJSONArray("hourlyweathers");
            long currentHour = Instant.ofEpochMilli(now).atZone(zone).withMinute(0)
                    .withSecond(0).withNano(0).toInstant().toEpochMilli();
            int count = 0;
            for (int i = 0; i < hours.length() && count < 7; i++) {
                JSONObject hour = hours.getJSONObject(i);
                long date = hour.getLong("date");
                if (date <= currentHour) continue;
                RemoteViews item = new RemoteViews(context.getPackageName(), R.layout.widget_forecast_item);
                String time = format(date, "HH:mm", zone);
                views.setTextViewText(R.id.widget_hours_label, "NEXT HOURS");
                item.setTextViewText(R.id.forecast_time, time);
                item.setTextViewText(R.id.forecast_temperature, hour.getInt("temp") + "°");
                setAmounts(item, hour.getInt("rainprobability") + "%",
                        sourceHours == null ? null : sourceHours.optJSONObject(i), "precipitation", "snowfall");
                String label = hour.optString("weathertext", "Forecast");
                setIcon(item, R.id.forecast_icon, hour.getInt("cnweatherid"),
                        hour.getBoolean("isdaynight"), time + ", " + label);
                views.addView(R.id.widget_hours, item);
                count++;
            }
            if (count == 0) views.setTextViewText(R.id.widget_hours_label, "Forecast expired · open to refresh");
            views.removeAllViews(R.id.widget_days);
            views.setViewVisibility(R.id.widget_solar, View.GONE);
            count = 0;
            for (int i = 0; i < days.length() && count < 7; i++) {
                JSONObject day = days.getJSONObject(i);
                LocalDate date = LocalDate.parse(day.getString("publictimeFmt"));
                if (date.isBefore(today)) continue;
                if (date.equals(today)) {
                    views.setViewVisibility(R.id.widget_solar, View.VISIBLE);
                    views.setTextViewText(R.id.widget_sunrise, " " + format(day.getLong("sunRise"), "HH:mm", zone));
                    views.setTextViewText(R.id.widget_sunset, " " + format(day.getLong("sunSet"), "HH:mm", zone));
                }
                JSONObject condition = day.getJSONObject("conditionDay");
                RemoteViews item = new RemoteViews(context.getPackageName(), R.layout.widget_forecast_item);
                String title = date.equals(today) ? "Today" : date.format(DateTimeFormatter.ofPattern("EEE dd", Locale.getDefault()));
                item.setTextViewText(R.id.forecast_time, title);
                item.setTextViewText(R.id.forecast_temperature, day.getInt("maxtemp") + "°/" + day.getInt("mintemp") + "°");
                JSONObject sourceDay = sourceDays == null ? null : sourceDays.optJSONObject(i);
                String probability = sourceDay != null && sourceDay.opt("precipitation_probability_max") instanceof Number
                        ? sourceDay.getInt("precipitation_probability_max") + "%" : "";
                setAmounts(item, probability, sourceDay, "precipitation_sum", "snowfall_sum");
                // A daily daytime summary does not inherit the current observation's night state.
                setIcon(item, R.id.forecast_icon, condition.getInt("cnweatherid"), true,
                        title + ", " + condition.getString("weathertext"));
                views.addView(R.id.widget_days, item);
                count++;
            }
            return views;
        } catch (Exception malformed) {
            return empty(views);
        }
    }

    private static RemoteViews empty(RemoteViews views) {
        views.setTextViewText(R.id.widget_city, "BYD Weather");
        views.setTextViewText(R.id.widget_updated, "Open BYD Weather to enable updates and grant location");
        views.setTextViewText(R.id.widget_temperature, "—");
        views.setTextViewText(R.id.widget_condition, "Waiting for weather");
        views.setContentDescription(R.id.widget_icon, "Weather unavailable");
        views.setImageViewResource(R.id.widget_icon, R.drawable.weather_no_data);
        views.setTextViewText(R.id.widget_metrics, "");
        views.removeAllViews(R.id.widget_hours);
        views.removeAllViews(R.id.widget_days);
        views.setViewVisibility(R.id.widget_solar, View.GONE);
        views.setViewVisibility(R.id.widget_hours_label, View.GONE);
        views.setViewVisibility(R.id.widget_days_label, View.GONE);
        return views;
    }

    private static void setAmounts(RemoteViews item, String probability, JSONObject source,
            String precipitationKey, String snowfallKey) throws Exception {
        String visible = probability;
        String description = probability.isEmpty() ? "" : "Precipitation probability " + probability;
        if (source != null && source.opt(precipitationKey) instanceof Number) {
            String amount = decimal(source.getDouble(precipitationKey)) + " mm";
            visible += (visible.isEmpty() ? "" : "\n") + amount;
            description += ", precipitation including snow water equivalent " + amount;
        }
        if (source != null && source.opt(snowfallKey) instanceof Number && source.getDouble(snowfallKey) > 0) {
            String amount = decimal(source.getDouble(snowfallKey)) + " cm";
            visible += (visible.isEmpty() ? "" : "\n") + amount;
            description += ", snowfall depth " + amount;
        }
        item.setTextViewText(R.id.forecast_precipitation, visible);
        item.setContentDescription(R.id.forecast_precipitation, description);
    }

    private static String metrics(JSONObject current, JSONObject air) throws Exception {
        int uv = current.getInt("uVIndex");
        String uvLabel = uv < 3 ? "Low" : uv < 6 ? "Moderate" : uv < 8 ? "High" : uv < 11 ? "Very high" : "Extreme";
        String result = "Feels " + current.getInt("realfeel") + "°C · Humidity " + current.getInt("humidity") + "%"
                + " · UV " + uv + " " + uvLabel + "\nWind " + current.getInt("windspeed") + " km/h "
                + current.getString("winddir") + " · Force " + current.getInt("windlevel")
                + " · Gust " + current.getInt("windgustspeed") + " km/h\n"
                + current.getInt("pressure") + " hPa · Visibility " + current.getInt("visibility") + " km"
                + " · Cloud " + current.getInt("cloudCover") + "% · Precip " + current.getDouble("precipitation") + " mm";
        if (air != null && air.optInt("aqivalue", -1) >= 0) {
            result += "\nEuropean AQI " + air.getInt("aqivalue") + " · " + air.getString("aqidesc");
            if (air.optInt("pm25", -1) >= 0) result += " · PM2.5 " + air.getInt("pm25") + " µg/m³";
            if (air.optInt("pm10", -1) >= 0) result += " · PM10 " + air.getInt("pm10") + " µg/m³";
        }
        return result;
    }

    private static void setIcon(RemoteViews views, int viewId, int code, boolean day, String label) {
        int resource = switch (code) {
            case 0 -> day ? R.drawable.weather_sun : R.drawable.weather_moon;
            case 1,20,29,30,31,53 -> R.drawable.weather_cloud;
            case 2 -> R.drawable.weather_overcast;
            case 3,6,7,8,9,10,11,12,19,21,22,23,24,25 -> R.drawable.weather_rain;
            case 4,5 -> R.drawable.weather_thunder;
            case 13,14,15,16,17,26,27,28 -> R.drawable.weather_snow;
            case 18 -> R.drawable.weather_fog;
            case -1 -> R.drawable.weather_sunrise;
            case -2 -> R.drawable.weather_sunset;
            default -> R.drawable.weather_no_data;
        };
        views.setImageViewResource(viewId, resource);
        views.setContentDescription(viewId, label + (day ? ", day" : ", night"));
    }

    private static String format(long time, String pattern, ZoneId zone) {
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone)
                .format(Instant.ofEpochMilli(time));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    static View preview(Context context, android.view.ViewGroup parent) {
        View view = render(context, System.currentTimeMillis()).apply(context, parent);
        view.setOnClickListener(null);
        return view;
    }
}
