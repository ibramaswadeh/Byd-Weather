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
    private Button locationButton;
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
        scroll.setBackgroundColor(getColor(R.color.weather_background));
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        scroll.addView(frame);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(24), dp(24), dp(32));
        int width = getResources().getConfiguration().screenWidthDp;
        android.widget.FrameLayout.LayoutParams content = new android.widget.FrameLayout.LayoutParams(
                width > 1040 ? dp(1040) : -1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(root, content);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_weather);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = column();
        heading.setPadding(dp(16), 0, 0, 0);
        TextView title = label(getString(R.string.app_title), 26, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        heading.addView(title);
        heading.addView(label(getString(R.string.app_subtitle), 16, MUTED));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        addSpace(root, 28);

        boolean wide = width >= 840;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(body);
        LinearLayout updates = column();
        LinearLayout settings = column();
        if (wide) {
            body.addView(updates, new LinearLayout.LayoutParams(0, -2, 1));
            settings.setPadding(dp(36), 0, 0, 0);
            body.addView(settings, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            body.addView(updates);
            addSpace(body, 28);
            body.addView(settings);
        }

        addHeading(updates, R.string.updates_heading);
        weatherSwitch = new Switch(this);
        weatherSwitch.setText(R.string.keep_updated);
        weatherSwitch.setTextSize(18);
        weatherSwitch.setTextColor(TEXT);
        weatherSwitch.setMinHeight(dp(56));
        weatherSwitch.setChecked(preferences.getBoolean(WeatherRuntime.PREF_ENABLED, false));
        updates.addView(weatherSwitch, new LinearLayout.LayoutParams(-1, -2));
        weatherSwitch.setOnCheckedChangeListener((button, enabled) -> setWeatherEnabled(enabled));
        addSpace(updates, 8);
        refreshButton = button(getString(R.string.update_now));
        refreshButton.setMinHeight(dp(56));
        refreshButton.setBackgroundTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}, new int[]{}},
                new int[]{getColor(R.color.weather_accent), getColor(R.color.weather_surface)}));
        refreshButton.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_enabled}, new int[]{}},
                new int[]{getColor(R.color.weather_background), MUTED}));
        refreshButton.setOnClickListener(v -> manualRefresh());
        updates.addView(refreshButton, new LinearLayout.LayoutParams(-1, -2));
        statusText = label("", 16, TEXT);
        statusText.setLineSpacing(dp(4), 1);
        statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusText.setTextIsSelectable(true);
        statusText.setPadding(0, dp(12), 0, dp(12));
        updates.addView(statusText);

        addHeading(settings, R.string.location_heading);
        settings.addView(label(getString(R.string.location_description), 14, MUTED));
        addSpace(settings, 12);
        permissionText = label("", 14, MUTED);
        settings.addView(permissionText);
        locationButton = button(getString(R.string.grant_location));
        locationButton.setOnClickListener(v -> requestLocationPermissions());
        settings.addView(locationButton, new LinearLayout.LayoutParams(-1, -2));

        addSpace(settings, 24);
        addHeading(settings, R.string.schedule_heading);
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView intervalLabel = label(getString(R.string.refresh_interval), 16, TEXT);
        intervalRow.addView(intervalLabel, new LinearLayout.LayoutParams(0, -2, 1));
        intervalInput = new EditText(this);
        intervalInput.setId(View.generateViewId());
        intervalLabel.setLabelFor(intervalInput.getId());
        intervalInput.setText(getString(R.string.interval_value, preferences.getInt(
                WeatherRuntime.PREF_INTERVAL_MINUTES, WeatherRuntime.DEFAULT_INTERVAL_MINUTES)));
        intervalInput.setTextColor(TEXT);
        intervalInput.setTextSize(18);
        intervalInput.setGravity(Gravity.CENTER);
        intervalInput.setSelectAllOnFocus(true);
        intervalInput.setSingleLine(true);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        intervalRow.addView(intervalInput, new LinearLayout.LayoutParams(dp(88), dp(56)));
        settings.addView(intervalRow);
        intervalInput.setOnEditorActionListener((v, id, event) -> {
            if (id != EditorInfo.IME_ACTION_DONE) return false;
            saveInterval();
            return true;
        });
        intervalInput.setOnFocusChangeListener((v, focused) -> { if (!focused) saveInterval(); });
        autoStartSwitch = new Switch(this);
        autoStartSwitch.setText(R.string.auto_start);
        autoStartSwitch.setTextSize(16);
        autoStartSwitch.setTextColor(TEXT);
        autoStartSwitch.setMinHeight(dp(56));
        autoStartSwitch.setChecked(preferences.getBoolean(WeatherService.PREF_AUTO_START, true));
        settings.addView(autoStartSwitch, new LinearLayout.LayoutParams(-1, -2));
        autoStartSwitch.setOnCheckedChangeListener((button, enabled) -> {
            preferences.edit().putBoolean(WeatherService.PREF_AUTO_START, enabled).apply();
            if (enabled) promptForBydStartupIfNeeded();
        });

        addSpace(root, 24);
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.weather_surface));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        addSpace(root, 8);
        Button setupButton = button(getString(R.string.show_setup));
        root.addView(setupButton, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout setup = column();
        setup.setVisibility(View.GONE);
        root.addView(setup);
        setupButton.setOnClickListener(v -> {
            boolean show = setup.getVisibility() != View.VISIBLE;
            setup.setVisibility(show ? View.VISIBLE : View.GONE);
            setupButton.setText(show ? R.string.hide_setup : R.string.show_setup);
        });
        Button bydSettings = button("Open BYD background-start settings");
        bydSettings.setOnClickListener(v -> openBydStartupSettings());
        setup.addView(bydSettings, new LinearLayout.LayoutParams(-1, -2));
        setup.addView(label(getString(R.string.background_hint), 14, MUTED));
        Button accessibility = button("Enable stock weather refresh button");
        accessibility.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Optional Accessibility service")
                .setMessage(getString(R.string.accessibility_setup))
                .setPositiveButton("Open settings", (dialog, which) -> openAccessibilitySettings())
                .setNegativeButton("Later", null).show());
        setup.addView(accessibility, new LinearLayout.LayoutParams(-1, -2));
        addSpace(setup, 16);
        addHeading(setup, R.string.coverage_heading);
        setup.addView(label(getString(R.string.data_coverage), 14, MUTED));

        TextView credit = label(WeatherMapping.ATTRIBUTION, 14, getColor(R.color.weather_accent));
        credit.setClickable(true);
        credit.setFocusable(true);
        credit.setMinHeight(dp(48));
        credit.setGravity(Gravity.CENTER_VERTICAL);
        credit.setOnClickListener(v -> {
            if (!tryOpen(new Intent(Intent.ACTION_VIEW, Uri.parse(WeatherMapping.SOURCE_URL)))) {
                Toast.makeText(this, "No browser available. Visit " + WeatherMapping.SOURCE_URL,
                        Toast.LENGTH_LONG).show();
            }
        });
        root.addView(credit);
        setContentView(scroll);
        updateStatus();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }

    private void addHeading(LinearLayout parent, int resource) {
        TextView heading = label(getString(resource), 20, TEXT);
        heading.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        heading.setPadding(0, 0, 0, dp(8));
        parent.addView(heading);
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
        statusText.setText(R.string.updating_weather);
        WeatherService.command(this, WeatherService.ACTION_REFRESH,
                new ResultReceiver(handler) {
                    @Override protected void onReceiveResult(int resultCode, Bundle data) {
                        String message = data == null ? "Update failed" :
                                data.getString("message", "Update failed");
                        if (resultCode == WeatherService.RESULT_PROGRESS) {
                            setStatusMessage(message);
                            return;
                        }
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        manualRefreshing = false;
                        refreshButton.setEnabled(weatherSwitch.isChecked());
                        setStatusMessage(message);
                    }
                });
    }

    private void saveInterval() {
        if (intervalInput == null) return;
        int current = preferences.getInt(WeatherRuntime.PREF_INTERVAL_MINUTES, 15);
        int parsed = current;
        try { parsed = Integer.parseInt(intervalInput.getText().toString().trim()); }
        catch (NumberFormatException ignored) { }
        int clamped = WeatherRuntime.clampIntervalMinutes(parsed);
        intervalInput.setText(getString(R.string.interval_value, clamped));
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
        return WeatherLocationSource.hasLocationPermission(this);
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
                Toast.makeText(this, "Location permission is required. Allow location access in app settings.", Toast.LENGTH_LONG).show();
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
        if (!manualRefreshing) {
            String method = preferences.getString(WeatherRuntime.PREF_LAST_LOCATION_METHOD, "");
            String text = state + "\n" + stamp
                    + (writtenLocation.isEmpty() ? "" : "\nLocation: " + writtenLocation)
                    + (method.isEmpty() ? "" : "\nLocation source: " + method)
                    + (last == 0 ? "" : "\nAir quality: " + preferences.getString(
                            WeatherRuntime.PREF_AIR_QUALITY, "Unavailable"));
            if (!text.contentEquals(statusText.getText())) statusText.setText(text);
        }
        if (permissionText != null) {
            String location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ? "Precise location granted"
                    : hasLocation() ? "Approximate location granted"
                    : "Location permission needed";
            String background = Build.VERSION.SDK_INT < 29 ||
                    checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                    ? "background location granted" : "background location needed after reboot";
            permissionText.setText(getString(R.string.location_permission_summary, location, background));
            boolean backgroundGranted = Build.VERSION.SDK_INT < 29 || checkSelfPermission(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            locationButton.setVisibility(hasLocation() && backgroundGranted ? View.GONE : View.VISIBLE);
        }
    }

    private void setStatusMessage(String message) {
        if (!message.contentEquals(statusText.getText())) statusText.setText(message);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(dp(48));
        button.setTextColor(TEXT);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.weather_surface)));
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
