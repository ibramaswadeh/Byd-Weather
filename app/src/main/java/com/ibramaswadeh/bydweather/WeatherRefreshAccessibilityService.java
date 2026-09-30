package com.ibramaswadeh.bydweather;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Requests a weather update when the stock widget's refresh button is tapped. */
public final class WeatherRefreshAccessibilityService extends AccessibilityService {
    private static final String WEATHER_PACKAGE = "com.byd.weatherdata";
    private static final String REFRESH_VIEW_ID = "com.byd.weatherdata:id/iv_refresh";

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED
                || event.getPackageName() == null
                || !WEATHER_PACKAGE.contentEquals(event.getPackageName())) return;
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        try {
            if (REFRESH_VIEW_ID.equals(source.getViewIdResourceName())
                    && getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean(WeatherRuntime.PREF_ENABLED, false)) {
                WeatherService.command(this, WeatherService.ACTION_REFRESH);
            }
        } catch (RuntimeException unavailable) {
            // Keep listening for clicks if the refresh service cannot start.
        } finally {
            source.recycle();
        }
    }

    @Override public void onInterrupt() { }
}
