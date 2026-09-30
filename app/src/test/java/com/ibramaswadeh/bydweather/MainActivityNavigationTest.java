package com.ibramaswadeh.bydweather;

import android.app.AlertDialog;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Tests navigation when settings or browser apps are unavailable. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class MainActivityNavigationTest {
    @Test public void missingAccessibilitySettingsDoesNotCrashOrFinish() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        find(activity.getWindow().getDecorView(), "Enable stock weather refresh button").performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.isFinishing());
        assertNotNull(ShadowToast.getTextOfLatestToast());
    }

    @Test public void missingBydManagerAndAppSettingsDoNotCrash() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        find(activity.getWindow().getDecorView(), "Open BYD background-start settings").performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.isFinishing());
        assertNotNull(ShadowToast.getTextOfLatestToast());
    }

    @Test public void accessibilityButtonOpensCorrectSystemScreenWhenAvailable() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        find(activity.getWindow().getDecorView(), "Enable stock weather refresh button").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                shadowOf(activity).getNextStartedActivity().getAction());
        assertFalse(activity.isFinishing());
    }

    @Test public void missingBrowserDoesNotCrashOrFinish() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true);
        find(activity.getWindow().getDecorView(), "Weather data by Open-Meteo.com").performClick();
        assertFalse(activity.isFinishing());
        assertNotNull(ShadowToast.getTextOfLatestToast());
    }

    static View find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
