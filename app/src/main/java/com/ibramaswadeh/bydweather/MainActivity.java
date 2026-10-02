package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** Controls weather settings, manual refresh, and permission setup. */
public final class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 100;
    private static final int REQUEST_BACKGROUND_LOCATION = 101;
    private static final String BYD_START_PACKAGE = "com.byd.appstartmanagement";
    private static final String BYD_START_ACTIVITY =
            "com.byd.appstartmanagement.frame.AppStartManagement";
    private static final int TEXT = 0xFFE7F1FA;
    private static final int MUTED = 0xFFADC3D7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 3_000L);
        }
    };
    private SharedPreferences preferences;
    private Switch weatherSwitch;
    private Switch autoStartSwitch;
    private EditText intervalInput;
    private TextView statusText;
    private TextView permissionText;
    private Button refreshButton;
    private boolean manualRefreshing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        buildScreen();
        if (preferences.getBoolean(WeatherRuntime.PREF_ENABLED, false) && hasLocation()) {
            WeatherService.command(this, WeatherService.ACTION_START);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(statusUpdater);
        handler.post(statusUpdater);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(statusUpdater);
        saveInterval();
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF10233A);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(28));
        scroll.addView(root);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_weather);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(74), dp(74));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);
        TextView title = label("BYD Weather", 28, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = label("Local weather for the stock BYD widget", 16, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        addSpace(root, 22);
        weatherSwitch = new Switch(this);
        weatherSwitch.setText("Keep weather updated");
        weatherSwitch.setTextSize(19);
        weatherSwitch.setTextColor(TEXT);
        weatherSwitch.setChecked(preferences.getBoolean(WeatherRuntime.PREF_ENABLED, false));
        root.addView(weatherSwitch, new LinearLayout.LayoutParams(-1, dp(56)));
        weatherSwitch.setOnCheckedChangeListener((button, enabled) -> setWeatherEnabled(enabled));

        autoStartSwitch = new Switch(this);
        autoStartSwitch.setText("Start automatically with the car");
        autoStartSwitch.setTextSize(18);
        autoStartSwitch.setTextColor(TEXT);
        autoStartSwitch.setChecked(preferences.getBoolean(WeatherService.PREF_AUTO_START, true));
        root.addView(autoStartSwitch, new LinearLayout.LayoutParams(-1, dp(56)));
        autoStartSwitch.setOnCheckedChangeListener((button, enabled) -> {
            preferences.edit().putBoolean(WeatherService.PREF_AUTO_START, enabled).apply();
            if (enabled) promptForBydStartupIfNeeded();
        });

        permissionText = label("", 15, MUTED);
        root.addView(permissionText);
        Button locationButton = button("Grant background location");
        locationButton.setOnClickListener(v -> requestLocationPermissions());
        root.addView(locationButton);

        addSpace(root, 14);
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView intervalLabel = label("Refresh interval (5–180 min)", 17, TEXT);
        intervalRow.addView(intervalLabel, new LinearLayout.LayoutParams(0, dp(54), 1));
        intervalInput = new EditText(this);
        intervalInput.setText(Integer.toString(preferences.getInt(
                WeatherRuntime.PREF_INTERVAL_MINUTES, WeatherRuntime.DEFAULT_INTERVAL_MINUTES)));
        intervalInput.setTextColor(TEXT);
        intervalInput.setTextSize(18);
        intervalInput.setGravity(Gravity.CENTER);
        intervalInput.setSelectAllOnFocus(true);
        intervalInput.setSingleLine(true);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        intervalRow.addView(intervalInput, new LinearLayout.LayoutParams(dp(108), dp(54)));
        root.addView(intervalRow);
        intervalInput.setOnEditorActionListener((v, id, event) -> {
            if (id != EditorInfo.IME_ACTION_DONE) return false;
            saveInterval();
            return true;
        });
        intervalInput.setOnFocusChangeListener((v, focused) -> {
            if (!focused) saveInterval();
        });

        refreshButton = button("Update weather now");
        refreshButton.setOnClickListener(v -> manualRefresh());
        root.addView(refreshButton);
        statusText = label("", 16, TEXT);
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText);

        addSpace(root, 10);
        Button bydSettings = button("Open BYD background-start settings");
        bydSettings.setOnClickListener(v -> openBydStartupSettings());
        root.addView(bydSettings);
        TextView hint = label("In DiLink, allow BYD Weather to auto-start and run in the background.",
                14, MUTED);
        root.addView(hint);
        Button accessibility = button("Enable stock weather refresh button");
        accessibility.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Optional Accessibility service")
                .setMessage("Select BYD Weather refresh button in Accessibility settings. "
                        + "It observes only taps in BYD WeatherData so its refresh button can also update Open-Meteo data.")
                .setPositiveButton("Open settings", (dialog, which) ->
                        openAccessibilitySettings())
                .setNegativeButton("Later", null).show());
        root.addView(accessibility);

        addSpace(root, 16);
        TextView credit = label("Weather data by Open-Meteo.com", 14, 0xFF64C3FF);
        credit.setClickable(true);
        credit.setFocusable(true);
        credit.setMinHeight(dp(48));
        credit.setGravity(Gravity.CENTER_VERTICAL);
        credit.setOnClickListener(v -> {
            if (!tryOpen(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))) {
                Toast.makeText(this, "No browser available. Visit https://open-meteo.com/",
                        Toast.LENGTH_LONG).show();
            }
        });
        root.addView(credit);
        setContentView(scroll);
        updateStatus();
    }

    private void setWeatherEnabled(boolean enabled) {
        if (enabled && !hasLocation()) {
            requestLocationPermissions();
            return;
        }
        preferences.edit().putBoolean(WeatherRuntime.PREF_ENABLED, enabled).apply();
        WeatherService.command(this, enabled ? WeatherService.ACTION_SETTINGS_CHANGED
                : WeatherService.ACTION_STOP);
        refreshButton.setEnabled(enabled);
        if (enabled) {
            boolean permissionPrompt = requestBackgroundLocationIfNeeded();
            if (!permissionPrompt) promptForBydStartupIfNeeded();
        }
        updateStatus();
    }

    private void manualRefresh() {
        if (manualRefreshing) return;
        saveInterval();
        if (!hasLocation()) {
            requestLocationPermissions();
            return;
        }
        manualRefreshing = true;
        refreshButton.setEnabled(false);
        statusText.setText("Updating weather…");
        WeatherService.command(this, WeatherService.ACTION_REFRESH,
                new ResultReceiver(handler) {
                    @Override protected void onReceiveResult(int resultCode, Bundle data) {
                        String message = data == null ? "Update failed" :
                                data.getString("message", "Update failed");
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        manualRefreshing = false;
                        refreshButton.setEnabled(weatherSwitch.isChecked());
                        updateStatus();
                    }
                });
    }

    private void saveInterval() {
        if (intervalInput == null) return;
        int current = preferences.getInt(WeatherRuntime.PREF_INTERVAL_MINUTES, 15);
        int parsed = current;
        try { parsed = Integer.parseInt(intervalInput.getText().toString().trim()); }
        catch (NumberFormatException ignored) { }
        int clamped = WeatherMapping.clampIntervalMinutes(parsed);
        intervalInput.setText(Integer.toString(clamped));
        if (current == clamped) return;
        preferences.edit().putInt(WeatherRuntime.PREF_INTERVAL_MINUTES, clamped).apply();
        if (weatherSwitch.isChecked()) {
            WeatherService.command(this, WeatherService.ACTION_SETTINGS_CHANGED);
        }
    }

    private void requestLocationPermissions() {
        if (!hasLocation()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        } else {
            requestBackgroundLocationIfNeeded();
        }
    }

    private boolean requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 29 && hasLocation()
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
            return true;
        }
        return false;
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == REQUEST_LOCATION) {
            if (hasLocation()) {
                if (weatherSwitch.isChecked()) {
                    preferences.edit().putBoolean(WeatherRuntime.PREF_ENABLED, true).apply();
                    WeatherService.command(this, WeatherService.ACTION_START);
                }
                boolean permissionPrompt = requestBackgroundLocationIfNeeded();
                if (!permissionPrompt) promptForBydStartupIfNeeded();
            } else {
                weatherSwitch.setChecked(false);
                Toast.makeText(this, "Precise location is required for raw GPS weather", Toast.LENGTH_LONG).show();
            }
        } else if (code == REQUEST_BACKGROUND_LOCATION) {
            if (Build.VERSION.SDK_INT >= 29
                    && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Allow location all the time for weather after a reboot",
                        Toast.LENGTH_LONG).show();
            }
            if (weatherSwitch.isChecked()) promptForBydStartupIfNeeded();
        }
        updateStatus();
    }

    private void promptForBydStartupIfNeeded() {
        if (!autoStartSwitch.isChecked()
                || preferences.getBoolean("background_prompt_seen", false)) return;
        preferences.edit().putBoolean("background_prompt_seen", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Allow background weather updates")
                .setMessage("DiLink may stop sideloaded apps in the background. In BYD app-start "
                        + "management, allow BYD Weather to start with the car and run in the background.")
                .setPositiveButton("Open BYD settings", (dialog, which) -> openBydStartupSettings())
                .setNegativeButton("Later", null)
                .show();
    }

    private void openBydStartupSettings() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(BYD_START_PACKAGE, BYD_START_ACTIVITY));
        if (tryOpen(intent)) return;
        Toast.makeText(this, "BYD manager unavailable; check system app settings",
                Toast.LENGTH_LONG).show();
        if (!tryOpen(appSettings())) settingsUnavailable();
    }

    private void openAccessibilitySettings() {
        if (tryOpen(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) return;
        Toast.makeText(this, "Accessibility screen unavailable; look for Accessibility in system settings",
                Toast.LENGTH_LONG).show();
        if (tryOpen(new Intent(Settings.ACTION_SETTINGS))) return;
        if (!tryOpen(appSettings())) settingsUnavailable();
    }

    private Intent appSettings() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
    }

    private boolean tryOpen(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private void settingsUnavailable() {
        Toast.makeText(this, "System settings unavailable. Open them from the car launcher.",
                Toast.LENGTH_LONG).show();
    }

    private void updateStatus() {
        if (statusText == null) return;
        boolean enabled = preferences.getBoolean(WeatherRuntime.PREF_ENABLED, false);
        refreshButton.setEnabled(enabled && !manualRefreshing);
        long last = preferences.getLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, 0L);
        String stamp = last == 0L ? "Never updated" :
                "Last update: " + DateFormat.getDateTimeInstance().format(new Date(last));
        String state = enabled ? preferences.getString(WeatherService.KEY_STATUS,
                "Waiting for first update") : "Weather updates are off";
        String writtenLocation = preferences.getString(WeatherRuntime.PREF_LAST_LOCATION_NAME, "");
        statusText.setText(state + "\n" + stamp
                + (writtenLocation.isEmpty() ? "" : "\nLocation: " + writtenLocation));
        if (permissionText != null) {
            String location = hasLocation() ? "Precise GPS granted" : "Precise location permission needed";
            String background = Build.VERSION.SDK_INT < 29 ||
                    checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                    ? "background location granted" : "background location needed after reboot";
            permissionText.setText(location + " · " + background);
        }
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        return button;
    }

    private TextView label(String content, int size, int color) {
        TextView label = new TextView(this);
        label.setText(content);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private void addSpace(LinearLayout root, int size) {
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(size)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
