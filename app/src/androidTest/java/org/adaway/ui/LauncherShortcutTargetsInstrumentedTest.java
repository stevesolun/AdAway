package org.adaway.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LauncherShortcutTargetsInstrumentedTest {
    @Test
    public void systemPublishedShortcutsResolveToInstalledActivities() {
        Context app = ApplicationProvider.getApplicationContext();
        Map<String, Class<?>> expected = new HashMap<>();
        expected.put("preferences", PrefsActivity.class);
        expected.put("dns_requests", LogActivity.class);
        expected.put("your_lists", ListsActivity.class);
        for (ShortcutInfo shortcut : app.getSystemService(ShortcutManager.class).getManifestShortcuts()) {
            Class<?> target = expected.remove(shortcut.getId());
            assertNotNull(shortcut.getId(), target);
            Intent intent = shortcut.getIntent();
            assertNotNull(shortcut.getId(), intent);
            ComponentName component = new ComponentName(app, target);
            assertEquals(shortcut.getId(), component, intent.getComponent());
            assertNotNull(shortcut.getId(), app.getPackageManager().resolveActivity(intent, 0));
        }
        assertTrue("Missing shortcuts: " + expected.keySet(), expected.isEmpty());
    }
}
