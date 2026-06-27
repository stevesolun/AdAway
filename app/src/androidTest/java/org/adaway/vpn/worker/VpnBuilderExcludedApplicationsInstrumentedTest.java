package org.adaway.vpn.worker;

import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class VpnBuilderExcludedApplicationsInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(
                this.context,
                "set up VPN builder exclusions");
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down VPN builder exclusions");
        }
    }

    @Test
    public void excludedSystemAppsNoneKeepsDeviceSystemAppsInsideVpn() {
        setSystemAppExclusion("none");
        PreferenceHelper.setVpnExcludedApps(
                this.context,
                Collections.singleton(this.context.getPackageName()));

        RecordingExcluder excluder = new RecordingExcluder();
        VpnBuilder.excludeApplicationsFromVpn(this.context, excluder);

        assertFalse("The emulator must expose at least one system app for this proof.",
                getInstalledSystemPackages().isEmpty());
        assertTrue("No system app should be disallowed when the preference is none.",
                excluder.packageNames.isEmpty());
        assertFalse("AdAway must never exclude itself from its own VPN.",
                excluder.packageNames.contains(this.context.getPackageName()));
    }

    @Test
    public void excludedSystemAppsAllDisallowsInstalledSystemAppsExceptAdAway() {
        setSystemAppExclusion("all");

        RecordingExcluder excluder = new RecordingExcluder();
        VpnBuilder.excludeApplicationsFromVpn(this.context, excluder);

        Set<String> expectedPackageNames = getInstalledSystemPackages();
        assertFalse("The emulator must expose at least one system app for this proof.",
                expectedPackageNames.isEmpty());
        assertEquals("All visible system apps except AdAway should be disallowed.",
                expectedPackageNames,
                excluder.packageNames);
        assertFalse("AdAway must never exclude itself from its own VPN.",
                excluder.packageNames.contains(this.context.getPackageName()));
    }

    @Test
    public void excludedSystemAppsAllExceptBrowsersPreservesBrowserPackages() {
        setSystemAppExclusion("allExceptBrowsers");

        RecordingExcluder excluder = new RecordingExcluder();
        VpnBuilder.excludeApplicationsFromVpn(this.context, excluder);

        Set<String> expectedPackageNames = getInstalledSystemPackages();
        Set<String> browserPackageNames = getBrowserPackageNames();
        expectedPackageNames.removeAll(browserPackageNames);

        assertFalse("The emulator must expose at least one system app for this proof.",
                getInstalledSystemPackages().isEmpty());
        assertEquals("System app exclusions should preserve browser packages.",
                expectedPackageNames,
                excluder.packageNames);
        for (String browserPackageName : browserPackageNames) {
            assertFalse("Browser package should stay inside the VPN: " + browserPackageName,
                    excluder.packageNames.contains(browserPackageName));
        }
    }

    private void setSystemAppExclusion(String value) {
        SharedPreferences.Editor editor = this.context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE).edit();
        editor.putString(this.context.getString(R.string.pref_vpn_excluded_system_apps_key), value);
        assertTrue("Failed to store system-app VPN exclusion preference.", editor.commit());
    }

    @SuppressLint("QueryPermissionsNeeded")
    private Set<String> getInstalledSystemPackages() {
        String selfPackageName = this.context.getApplicationInfo().packageName;
        Set<String> packageNames = new HashSet<>();
        for (ApplicationInfo applicationInfo : this.context.getPackageManager()
                .getInstalledApplications(0)) {
            if (!selfPackageName.equals(applicationInfo.packageName)
                    && (applicationInfo.flags & FLAG_SYSTEM) != 0) {
                packageNames.add(applicationInfo.packageName);
            }
        }
        return packageNames;
    }

    @SuppressLint("QueryPermissionsNeeded")
    private Set<String> getBrowserPackageNames() {
        PackageManager packageManager = this.context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://isabrowser.adaway.org/"));
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);
        Set<String> packageNames = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfoList) {
            packageNames.add(resolveInfo.activityInfo.packageName);
        }

        packageNames.add("com.google.android.webview");
        packageNames.add("com.android.htmlviewer");
        packageNames.add("com.google.android.backuptransport");
        packageNames.add("com.google.android.gms");
        packageNames.add("com.google.android.gsf");

        return packageNames;
    }

    private static final class RecordingExcluder implements VpnBuilder.VpnApplicationExcluder {
        private final Set<String> packageNames = new HashSet<>();

        @Override
        public void addDisallowedApplication(String packageName)
                throws PackageManager.NameNotFoundException {
            this.packageNames.add(packageName);
        }
    }
}
