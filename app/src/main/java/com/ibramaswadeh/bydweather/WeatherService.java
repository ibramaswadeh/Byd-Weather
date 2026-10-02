package com.ibramaswadeh.bydweather;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;

/** Runs background weather refreshes with a foreground notification. */
public final class WeatherService extends Service {
    static final String ACTION_START = "com.ibramaswadeh.bydweather.START";
    static final String ACTION_REFRESH = "com.ibramaswadeh.bydweather.REFRESH";
    static final String ACTION_SETTINGS_CHANGED = "com.ibramaswadeh.bydweather.SETTINGS_CHANGED";
    static final String ACTION_STOP = "com.ibramaswadeh.bydweather.STOP";
    static final String EXTRA_RECEIVER = "receiver";
    static final String KEY_STATUS = "weather_status";
    static final String PREF_AUTO_START = "auto_start_enabled";
    private static final String CHANNEL = "byd_weather_service";
    private static final int NOTIFICATION_ID = 17;

    private SharedPreferences preferences;
    private WeatherRuntime runtime;

    static void command(Context context, String action) {
        command(context, action, null);
    }

    static void command(Context context, String action, ResultReceiver receiver) {
        Intent intent = new Intent(context, WeatherService.class).setAction(action);
        if (receiver != null) intent.putExtra(EXTRA_RECEIVER, receiver);
        if (!ACTION_STOP.equals(action)) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        runtime = new WeatherRuntime(this, preferences, this::weatherEvent);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "BYD Weather",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the BYD weather widget updated");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        startForeground(NOTIFICATION_ID, notification("Starting weather service"));
        if (ACTION_STOP.equals(action) || !preferences.getBoolean(WeatherRuntime.PREF_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        runtime.start();
        if (ACTION_SETTINGS_CHANGED.equals(action)) runtime.settingsChanged();
        if (ACTION_REFRESH.equals(action)) {
            ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
            boolean accepted = runtime.requestNow(receiver == null ? "oem_button" : "app_button",
                    (success, error) -> {
                        if (receiver == null) return;
                        Bundle result = new Bundle();
                        result.putString("message", success ? (error.isEmpty() ? "Weather updated" : "Forecast updated; " + error) :
                                "Weather update failed: " + error);
                        receiver.send(success ? 0 : 1, result);
                    });
            if (!accepted && receiver != null) {
                Bundle result = new Bundle();
                result.putString("message", runtime.isRequestInFlight() ?
                        "Weather update is already running" : "Weather service is off");
                receiver.send(1, result);
            }
        }
        return START_STICKY;
    }

    private void weatherEvent(String name, Object[] fields) {
        String status = null;
        if ("weather_success".equals(name)) status = "Forecast updated; stock widget synced";
        else if ("weather_forecast_success".equals(name)) status = "Forecast updated";
        else if ("weather_native_sync_unavailable".equals(name)) status = "Forecast updated; stock widget sync unavailable";
        else if ("weather_request".equals(name)) status = "Updating weather";
        else if ("weather_prerequisite_wait".equals(name)) {
            status = "Waiting for GPS location and/or validated internet";
        } else if ("weather_authorization_required".equals(name)) {
            status = "Precise GPS permission is required";
        } else if ("weather_gps_unavailable".equals(name)) {
            status = "Raw GPS unavailable; keeping last weather, retrying in 5 minutes";
        } else if ("weather_failure".equals(name)) {
            Object reason = fields.length >= 2 ? fields[1] : "unknown error";
            status = "Update failed: " + reason;
        }
        if (status == null) return;
        preferences.edit().putString(KEY_STATUS, status).apply();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(status));
    }

    private Notification notification(String message) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_weather_notification)
                .setContentTitle("BYD Weather")
                .setContentText(message)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        runtime.shutdown();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
