package org.adaway.ui.prefs;

import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.util.Constants.PREFS_NAME;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import android.widget.CompoundButton;

import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.vpn.VpnService;
import org.adaway.vpn.VpnServiceControls;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

@RunWith(AndroidJUnit4.class)
public class PrefsLiveConfigurationInstrumentedTest {
    private static final long TIMEOUT_MS = 20_000;

    @Test(timeout = 120_000)
    public void monitorSwitchReplacesWorkerInBothDirectionsAcrossRecreation() throws Exception {
        verifyLiveReconfiguration(true);
    }

    @Test(timeout = 120_000)
    public void ipv6SwitchReplacesTunnelInBothDirectionsAcrossRecreation() throws Exception {
        verifyLiveReconfiguration(false);
    }

    @Test(timeout = 60_000)
    public void actualSwitchesDoNotStartStoppedVpnOrRootMode() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assumeIdle(context);
        PreferenceSnapshot snapshot = new PreferenceSnapshot(context);
        LifecycleTree lifecycle = new LifecycleTree();
        Timber.plant(lifecycle);
        try {
            for (AdBlockMethod method : new AdBlockMethod[]{VPN, ROOT}) {
                PreferenceHelper.setAbBlockMethod(context, method);
                try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
                    clickSwitch(scenario, context.getString(R.string.pref_enable_ipv6_key));
                    showVpnPreferences(scenario);
                    clickSwitch(scenario, context.getString(R.string.pref_vpn_watchdog_enabled_key));
                    scenario.recreate();
                    clickSwitch(scenario, context.getString(R.string.pref_vpn_watchdog_enabled_key));
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(STOPPED, PreferenceHelper.getVpnServiceStatus(context));
                assertFalse(servicePresent(context));
                assertEquals(0, lifecycle.launches.get());
            }
        } finally {
            Timber.uproot(lifecycle);
            snapshot.restore();
        }
    }

    private void verifyLiveReconfiguration(boolean monitor) throws Exception {
        assumeTrue("Live VPN smoke is opt-in.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveVpnSmoke")));
        Context context = ApplicationProvider.getApplicationContext();
        assumeIdle(context);
        assumeTrue("VPN consent must already be prepared.",
                android.net.VpnService.prepare(context) == null);
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        assertNotNull(connectivity);
        boolean dualStackDns = hasDualStackUpstreamDns(connectivity);
        PreferenceSnapshot snapshot = new PreferenceSnapshot(context);
        String key = context.getString(monitor
                ? R.string.pref_vpn_watchdog_enabled_key : R.string.pref_enable_ipv6_key);
        LifecycleTree lifecycle = new LifecycleTree();
        Timber.plant(lifecycle);
        try {
            snapshot.preferences.edit().putBoolean(key, false).apply();
            PreferenceHelper.setAbBlockMethod(context, VPN);
            assertTrue(VpnServiceControls.start(context));
            Network initial = waitForTunnel(context, connectivity, null);
            try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
                if (monitor) showVpnPreferences(scenario);
                int starts = lifecycle.starts.get();
                int disabled = lifecycle.disabledWatchdogs.get();
                int initializations = lifecycle.watchdogInitializations.get();
                int destructions = lifecycle.destructions.get();
                clickSwitch(scenario, key);
                Network enabled = waitForTunnel(context, connectivity, initial);
                assertTrue(lifecycle.starts.get() > starts);
                assertTrue(lifecycle.watchdogInitializations.get() > initializations);
                assertTrue(snapshot.preferences.getBoolean(key, false));
                if (monitor) {
                    // The actual new worker initialized its watchdog without the disabled branch.
                    assertEquals(disabled, lifecycle.disabledWatchdogs.get());
                } else if (dualStackDns) {
                    assertTrue(hasIpv6Dns(connectivity, enabled));
                }
                int afterEnableStarts = lifecycle.starts.get();
                scenario.recreate();
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals("Recreation must not issue a duplicate restart.",
                        afterEnableStarts, lifecycle.starts.get());

                clickSwitch(scenario, key);
                Network disabledNetwork = waitForTunnel(context, connectivity, enabled);
                assertTrue(lifecycle.starts.get() > afterEnableStarts);
                assertFalse(snapshot.preferences.getBoolean(key, true));
                if (monitor) {
                    assertTrue(lifecycle.disabledWatchdogs.get() > disabled);
                } else if (dualStackDns) {
                    assertFalse(hasIpv6Dns(connectivity, disabledNetwork));
                }
                assertEquals("Settings replace workers without destroying the service.",
                        destructions, lifecycle.destructions.get());
            }
        } finally {
            try {
                if (servicePresent(context)) VpnServiceControls.stop(context);
                waitForStopped(context, connectivity);
                WorkManager.getInstance(context).cancelUniqueWork("vpnHeartbeat")
                        .getResult().get(5, TimeUnit.SECONDS);
            } finally {
                Timber.uproot(lifecycle);
                snapshot.restore();
            }
        }
    }

    private static void showVpnPreferences(ActivityScenario<PrefsActivity> scenario) {
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsVpnFragment()).commitNow());
    }

    private static void clickSwitch(ActivityScenario<PrefsActivity> scenario, String key) {
        AtomicBoolean clicked = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        while (!clicked.get() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity(activity -> {
                PreferenceFragmentCompat fragment = (PreferenceFragmentCompat)
                        activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                if (fragment == null || fragment.getView() == null) return;
                RecyclerView list = fragment.getListView();
                if (!(list.getAdapter() instanceof PreferenceGroupAdapter)) return;
                int position = ((PreferenceGroupAdapter) list.getAdapter())
                        .getPreferenceAdapterPosition(key);
                assertTrue("Missing switch " + key, position >= 0);
                list.scrollToPosition(position);
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder == null) return;
                CompoundButton button = holder.itemView.findViewById(
                        androidx.preference.R.id.switchWidget);
                if (button == null || !button.isShown()) return;
                boolean before = button.isChecked();
                button.performClick();
                assertEquals(!before, button.isChecked());
                clicked.set(true);
            });
            if (!clicked.get()) SystemClock.sleep(50);
        }
        assertTrue("Switch was not reachable: " + key, clicked.get());
    }

    private static void assumeIdle(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        assertNotNull(connectivity);
        assumeTrue("Do not interrupt another VPN.", vpnNetwork(connectivity) == null
                && !servicePresent(context) && PreferenceHelper.getVpnServiceStatus(context) == STOPPED);
    }

    private static Network waitForTunnel(Context context, ConnectivityManager connectivity,
                                        Network previous) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            Network network = vpnNetwork(connectivity);
            LinkProperties link = network == null ? null : connectivity.getLinkProperties(network);
            if (network != null && !network.equals(previous) && link != null
                    && link.getInterfaceName() != null && !link.getDnsServers().isEmpty()
                    && VpnServiceControls.isTunnelEstablished(context)) return network;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Expected a replacement VPN tunnel after the switch changed.");
    }

    private static void waitForStopped(Context context, ConnectivityManager connectivity) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        do {
            if (!servicePresent(context) && vpnNetwork(connectivity) == null
                    && PreferenceHelper.getVpnServiceStatus(context) == STOPPED) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("VPN fixture did not stop.");
    }

    private static Network vpnNetwork(ConnectivityManager connectivity) {
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(TRANSPORT_VPN)) return network;
        }
        return null;
    }

    private static boolean hasDualStackUpstreamDns(ConnectivityManager connectivity) {
        LinkProperties link = connectivity.getLinkProperties(connectivity.getActiveNetwork());
        return link != null && link.getDnsServers().stream().anyMatch(Inet4Address.class::isInstance)
                && link.getDnsServers().stream().anyMatch(Inet6Address.class::isInstance);
    }

    private static boolean hasIpv6Dns(ConnectivityManager connectivity, Network network) {
        LinkProperties link = connectivity.getLinkProperties(network);
        assertNotNull(link);
        return link.getDnsServers().stream().anyMatch(Inet6Address.class::isInstance);
    }

    private static boolean servicePresent(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        assertNotNull(manager);
        return manager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(
                service -> VpnService.class.getName().equals(service.service.getClassName()));
    }

    private static final class PreferenceSnapshot {
        final Context context;
        final SharedPreferences preferences;
        final AdBlockMethod method;
        final String[] keys;
        final boolean[] present = new boolean[2];
        final boolean[] values = new boolean[2];

        PreferenceSnapshot(Context context) {
            this.context = context;
            this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            this.method = PreferenceHelper.getAdBlockMethod(context);
            this.keys = new String[]{context.getString(R.string.pref_enable_ipv6_key),
                    context.getString(R.string.pref_vpn_watchdog_enabled_key)};
            for (int i = 0; i < keys.length; i++) {
                present[i] = preferences.contains(keys[i]);
                values[i] = preferences.getBoolean(keys[i], false);
            }
        }

        void restore() {
            SharedPreferences.Editor editor = preferences.edit();
            for (int i = 0; i < keys.length; i++) {
                if (present[i]) editor.putBoolean(keys[i], values[i]);
                else editor.remove(keys[i]);
            }
            editor.apply();
            PreferenceHelper.setAbBlockMethod(context, method);
        }
    }

    private static final class LifecycleTree extends Timber.Tree {
        final AtomicInteger launches = new AtomicInteger();
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger destructions = new AtomicInteger();
        final AtomicInteger watchdogInitializations = new AtomicInteger();
        final AtomicInteger disabledWatchdogs = new AtomicInteger();

        @Override
        protected void log(int priority, String tag, String message, Throwable throwable) {
            if (message.startsWith("Attempting to launch service intent")) launches.incrementAndGet();
            if (message.startsWith("Starting VPN thread")) starts.incrementAndGet();
            if (message.startsWith("Destroying VPN service")) destructions.incrementAndGet();
            if (message.equals("initialize: Initializing watchdog")) watchdogInitializations.incrementAndGet();
            if (message.equals("initialize: Disabled.")) disabledWatchdogs.incrementAndGet();
        }
    }
}
