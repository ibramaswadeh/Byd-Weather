package com.ibramaswadeh.bydweather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts weather after boot or app updates when weather and automatic start are enabled. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        if (!context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(WeatherRuntime.PREF_ENABLED, false)
                || !context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean(WeatherService.PREF_AUTO_START, true)) return;
        boolean locationGranted = WeatherLocationSource.hasLocationPermission(context);
        if (locationGranted) WeatherService.command(context, WeatherService.ACTION_START);
    }
}
