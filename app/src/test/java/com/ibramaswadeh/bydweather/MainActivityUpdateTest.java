package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.ResultReceiver;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityUpdateTest {
    @Before public void setupPermissionsAndSettings() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean(WeatherRuntime.PREF_ENABLED, true)
                .putBoolean("background_prompt_seen", true).apply();
    }

    @Test public void progressStaysInFlightAndFinalFailureRemainsVisible() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = activity.getWindow().getDecorView();
        Button update = (Button) MainActivityNavigationTest.find(root, "Update weather now");
        update.performClick();
        assertFalse(update.isEnabled());
        Intent refresh = null;
        for (Intent next; (next = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService()) != null;) {
            if (WeatherService.ACTION_REFRESH.equals(next.getAction())) refresh = next;
        }
        assertNotNull(refresh);
        ResultReceiver receiver = refresh.getParcelableExtra(WeatherService.EXTRA_RECEIVER);
        Bundle progress = new Bundle();
        progress.putString("message", "Getting a Wi-Fi/cell location…");
        receiver.send(WeatherService.RESULT_PROGRESS, progress);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(update.isEnabled());
        assertNotNull(MainActivityNavigationTest.find(root, progress.getString("message")));

        String error = "Weather download failed: api.met.no could not be resolved (DNS). Check the car's internet connection.";
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(WeatherService.KEY_STATUS, error).apply();
        Bundle failure = new Bundle();
        failure.putString("message", error);
        receiver.send(1, failure);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(update.isEnabled());
        assertNotNull(MainActivityNavigationTest.find(root, error));
    }

    @Test public void networkOnlyChoiceSurvivesActivityRecreation() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        RadioButton network = (RadioButton) MainActivityNavigationTest.find(
                activity.getWindow().getDecorView(), "Wi-Fi/cell only");
        network.performClick();
        MainActivity restored = Robolectric.buildActivity(MainActivity.class).setup().get();
        RadioButton selected = (RadioButton) MainActivityNavigationTest.find(
                restored.getWindow().getDecorView(), "Wi-Fi/cell only");
        assertTrue(selected.isChecked());
    }
}
