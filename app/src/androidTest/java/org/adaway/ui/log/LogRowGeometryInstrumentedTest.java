package org.adaway.ui.log;

import static android.view.View.MeasureSpec.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.databinding.LogEntryBinding;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class LogRowGeometryInstrumentedTest {
    @Test public void englishNormal() { check("en-US", 1f); }
    @Test public void englishLarge() { check("en-US", 2f); }
    @Test public void hebrewNormal() { check("he-IL", 1f); }
    @Test public void hebrewLarge() { check("he-IL", 2f); }

    private static void check(String language, float fontScale) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context base = ApplicationProvider.getApplicationContext();
            Configuration config = new Configuration(base.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag(language));
            config.fontScale = fontScale;
            Context ctx = new ContextThemeWrapper(base.createConfigurationContext(config),
                    R.style.Theme_AdAway);
            FrameLayout parent = new FrameLayout(ctx);
            parent.setLayoutDirection(config.getLayoutDirection());
            boolean rtl = config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            LogEntryBinding row = LogEntryBinding.inflate(LayoutInflater.from(ctx), parent, true);
            float density = ctx.getResources().getDisplayMetrics().density;
            for (int widthDp : new int[]{320, 360}) {
                int width = Math.round(widthDp * density);
                for (String host : new String[]{"www.ietf.org",
                        "healthcheck-backup.region-one.tests.example.org"}) {
                    row.hostnameTextView.setText(host);
                    parent.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(0, UNSPECIFIED));
                    parent.layout(0, 0, width, parent.getMeasuredHeight());
                    int start = Math.round(16 * density);
                    for (View icon : new View[]{row.blockImageView, row.allowImageView, row.redirectionImageView}) {
                        assertEquals("Icon offset in reading order", start,
                                rtl ? width - icon.getRight() : icon.getLeft(), 1);
                        assertEquals(Math.round(24 * density), icon.getWidth(), 1);
                        assertTrue(icon.getTop() >= 0 && icon.getBottom() <= row.getRoot().getHeight());
                        start += icon.getWidth() + Math.round(8 * density);
                    }
                    assertEquals(start, rtl ? width - row.hostnameTextView.getRight()
                            : row.hostnameTextView.getLeft(), 1);
                    assertEquals(Math.round((widthDp - 128) * density), row.hostnameTextView.getWidth(), 2);
                    Layout text = row.hostnameTextView.getLayout();
                    assertNotNull(text);
                    assertEquals(host.length(), text.getLineEnd(text.getLineCount() - 1));
                    for (int i = 0; i < text.getLineCount(); i++) {
                        assertEquals(0, text.getEllipsisCount(i));
                        assertTrue("Line fits allotted width", text.getLineWidth(i) <= text.getWidth() + 1);
                    }
                    assertTrue(text.getHeight() <= row.hostnameTextView.getHeight());
                    assertTrue(row.hostnameTextView.getTop() >= 0
                            && row.hostnameTextView.getBottom() <= row.getRoot().getHeight());
                    if (host.startsWith("healthcheck")) assertTrue(text.getLineCount() > 1);
                }
            }
        });
    }
}
