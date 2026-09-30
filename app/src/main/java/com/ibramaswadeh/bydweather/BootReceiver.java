package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

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
        boolean locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (locationGranted) WeatherService.command(context, WeatherService.ACTION_START);
    }
}
