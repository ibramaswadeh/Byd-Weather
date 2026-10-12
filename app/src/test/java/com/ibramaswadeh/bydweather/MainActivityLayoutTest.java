package com.ibramaswadeh.bydweather;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.android.controller.ActivityController;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityLayoutTest {
    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    public void landscapeKeepsUpdateActionAndSettingsVisible() throws Exception {
        render(1280, 720, "landscape");
    }

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    public void narrowScreenWrapsSettingsWithoutClipping() throws Exception {
        render(360, 800, "portrait");
    }

    private void render(int width, int height, String name) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean(WeatherRuntime.PREF_ENABLED, true)
                .putLong(WeatherRuntime.PREF_LAST_SUCCESS_MS, MetFixtures.NOW)
                .putString(WeatherRuntime.PREF_LAST_LOCATION_NAME, "Oslo")
                .putString(WeatherRuntime.PREF_LAST_LOCATION_METHOD, "Wi-Fi")
                .putString(WeatherRuntime.PREF_AIR_QUALITY, "Low health risk · Norwegian index 1.6")
                .putString(WeatherService.KEY_STATUS, "Weather updated").apply();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            View decor = activity.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, width, height);
            View update = MainActivityNavigationTest.find(decor, "Update weather now");
            int[] position = new int[2];
            update.getLocationOnScreen(position);
            assertTrue("Primary action must fit in the first viewport", position[1] + update.getHeight() <= height);
            verifyText(decor);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(bitmap));
            File directory = new File("build/reports/ui");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            bitmap.recycle();
        }
    }

    private static void verifyText(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertEquals("Unexpected clipped text: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
                }
            }
            if (view instanceof Button) assertTrue("Touch target too small", view.getHeight() >= 48);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) verifyText(group.getChildAt(i));
        }
    }
}
