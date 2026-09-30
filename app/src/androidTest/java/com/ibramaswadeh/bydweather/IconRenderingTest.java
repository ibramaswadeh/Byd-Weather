package com.ibramaswadeh.bydweather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Renders compiled launcher and monochrome icons at small sizes. */
@RunWith(AndroidJUnit4.class)
public class IconRenderingTest {
    @Test public void renderLauncherMasksAndMonochromeAtSmallSizes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Drawable loaded = context.getDrawable(R.mipmap.ic_launcher);
        assertTrue(loaded instanceof AdaptiveIconDrawable);
        AdaptiveIconDrawable icon = (AdaptiveIconDrawable) loaded;
        Bitmap sheet = Bitmap.createBitmap(780, 440, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(225, 233, 240));
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.rgb(16, 45, 75));
        label.setTextSize(18);
        String[] names = {"Circle", "Rounded square", "Squircle", "Themed light", "Themed dark"};
        int[] sizes = {32, 48, 96};
        for (int column = 0; column < names.length; column++) {
            canvas.drawText(names[column], column * 156 + 9, 30, label);
            for (int row = 0; row < sizes.length; row++) {
                int size = sizes[row];
                int x = column * 156 + (156 - size) / 2;
                int y = 55 + row * 120;
                canvas.save();
                canvas.translate(x, y);
                Path mask = mask(column, size);
                canvas.clipPath(mask);
                icon.setBounds(0, 0, size, size);
                if (column < 3) {
                    icon.getBackground().draw(canvas);
                    icon.getForeground().draw(canvas);
                } else {
                    canvas.drawColor(column == 3 ? Color.rgb(202, 225, 247) : Color.rgb(22, 42, 63));
                    Drawable monochrome = context.getDrawable(R.drawable.ic_launcher_monochrome);
                    monochrome.setBounds(icon.getForeground().getBounds());
                    monochrome.setTint(column == 3 ? Color.rgb(16, 45, 75) : Color.rgb(202, 225, 247));
                    monochrome.draw(canvas);
                }
                canvas.restore();
                canvas.drawText(size + "px", column * 156 + 55, y + size + 25, label);
            }
        }
        File output = new File(context.getExternalFilesDir(null), "icon-masks.png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        assertTrue(output.length() > 0);
        // Copy the rendered image to shared storage so it survives app cleanup.
        try (ParcelFileDescriptor copied = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("cp " + output.getAbsolutePath()
                        + " /sdcard/Download/weather-icon-masks.png");
             java.io.InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(copied)) {
            while (stream.read() != -1) { }
        }
    }

    private Path mask(int column, int size) {
        Path path = new Path();
        if (column == 0 || column >= 3) {
            path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        } else if (column == 1) {
            path.addRoundRect(new RectF(0, 0, size, size), size * .2f, size * .2f, Path.Direction.CW);
        } else {
            // Draw the squircle test mask.
            for (int i = 0; i <= 120; i++) {
                double angle = i * Math.PI * 2 / 120;
                float x = (float) (Math.signum(Math.cos(angle)) * Math.sqrt(Math.abs(Math.cos(angle))));
                float y = (float) (Math.signum(Math.sin(angle)) * Math.sqrt(Math.abs(Math.sin(angle))));
                if (i == 0) path.moveTo(size * (x + 1) / 2, size * (y + 1) / 2);
                else path.lineTo(size * (x + 1) / 2, size * (y + 1) / 2);
            }
            path.close();
        }
        return path;
    }
}
