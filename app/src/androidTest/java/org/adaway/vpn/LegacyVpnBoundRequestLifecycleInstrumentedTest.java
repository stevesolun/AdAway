package org.adaway.vpn;

import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.RUNNING;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/** API30 dump-based lifecycle evidence, not a packet-routing or handoff test. */
@RunWith(AndroidJUnit4.class)
public class LegacyVpnBoundRequestLifecycleInstrumentedTest {
    private static final String TAG = "LegacyVpnBoundLifecycle";
    private static final Set<Integer> NONE = Collections.emptySet();
    private static final Pattern ROW = Pattern.compile(
            "^uid/pid:(\\d+)/(\\d+)\\s+NetworkRequest \\[ "
                    + "(REQUEST|LISTEN|TRACK_DEFAULT|BACKGROUND_REQUEST) id=(\\d+)"
                    + "(?:, legacyType=-?\\d+)?, \\[.*\\] \\]"
                    + "(?: to trigger .*)?$");
    private Context mContext;
    private ConnectivityManager mConnectivity;
    private ActivityManager mActivity;
    private File mEvidence;
    private ExecutorService mReader;
    private AdBlockMethod mOriginalMethod;
    private boolean mBound;
    private boolean mStartAttempted;
    private long mServiceBirth;
    private int mExpectedStarts;
    private final Connection mConnection = new Connection();
    private final AtomicInteger mStartAcks = new AtomicInteger();
    private Timber.Tree mTree;

    @Test(timeout = 240_000)
    public void bindStartRepeatStopWhileBoundRestartAndUnbindOwnExactlyOneRequest()
            throws Exception {
        assumeTrue("Opt in with adawayLiveBoundNetworkLifecycle=true.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveBoundNetworkLifecycle")));
        assumeTrue("Request dump contract is verified only for API30/Android11.",
                Build.VERSION.SDK_INT == 30);
        mContext = ApplicationProvider.getApplicationContext();
        mConnectivity = mContext.getSystemService(ConnectivityManager.class);
        mActivity = mContext.getSystemService(ActivityManager.class);
        assertNotNull(mConnectivity);
        assertNotNull(mActivity);
        assertEquals("Instrumentation must run as the target application's UID.",
                mContext.getApplicationInfo().uid, Process.myUid());
        assumeTrue("Start with AdAway stopped and no VPN or service owned by another run.",
                PreferenceHelper.getVpnServiceStatus(mContext) == STOPPED
                        && service() == null && !hasVpn());
        assumeTrue("VPN consent must already be prepared; this test does not grant it.",
                android.net.VpnService.prepare(mContext) == null);
        assumeTrue("Cancel leftover VPN heartbeat work before this isolated test.",
                WorkManager.getInstance(mContext).getWorkInfosForUniqueWork("vpnHeartbeat")
                        .get(5, TimeUnit.SECONDS).stream().allMatch(work -> work.getState().isFinished()));
        File external = mContext.getExternalFilesDir(null);
        assertNotNull("Evidence storage unavailable; cannot claim request-count proof.", external);
        mEvidence = new File(external, "legacy-bound-network-" + System.currentTimeMillis());
        assertTrue(mEvidence.mkdirs());
        mReader = Executors.newSingleThreadExecutor();
        Log.i(TAG, "Evidence: " + mEvidence + "; UID=" + Process.myUid());
        mTree = new Timber.Tree() {
            @Override
            protected void log(int priority, String tag, String message, Throwable throwable) {
                // Acknowledges command delivery only; request ownership comes from the system dump.
                if ("VPN service started.".equals(message)) mStartAcks.incrementAndGet();
            }
        };
        Timber.plant(mTree);

        awaitPhase("baseline", Phase.ABSENT, NONE);
        mOriginalMethod = PreferenceHelper.getAdBlockMethod(mContext);
        PreferenceHelper.setAbBlockMethod(mContext, VPN);
        Intent bind = new Intent(android.net.VpnService.SERVICE_INTERFACE)
                .setComponent(new ComponentName(mContext, VpnService.class));
        mBound = mContext.bindService(bind, mConnection, Context.BIND_AUTO_CREATE);
        assertTrue("Self-UID bind must be accepted.", mBound);
        assertTrue("Framework did not deliver the service binder.",
                mConnection.connected.await(10, TimeUnit.SECONDS));
        assertNotNull(mConnection.binder);
        ActivityManager.RunningServiceInfo created = service();
        assertNotNull(created);
        mServiceBirth = created.activeSince;
        awaitPhase("bind-only", Phase.BOUND_STOPPED, NONE);

        start(false);
        Set<Integer> first = awaitPhase("started", Phase.RUNNING, null).ids();
        start(true);
        awaitPhase("repeated-start", Phase.RUNNING, first);

        VpnServiceControls.stop(mContext);
        awaitPhase("stopped-still-bound", Phase.BOUND_STOPPED, NONE);
        start(false);
        Set<Integer> second = awaitPhase("restarted-still-bound", Phase.RUNNING, null).ids();
        assertNotEquals("Restart must own a fresh framework request ID.", first, second);
        VpnServiceControls.stop(mContext);
        awaitPhase("final-stop-still-bound", Phase.BOUND_STOPPED, NONE);
        mContext.unbindService(mConnection);
        mBound = false;
        awaitPhase("final-unbound", Phase.ABSENT, NONE);
        Log.i(TAG, "PASS sampled request sequence 0/0/1/1/0/1/0/0, first=" + first
                + ", fresh=" + second + "; evidence=" + mEvidence);
    }

    private void start(boolean repeat) {
        mStartAttempted = true;
        mExpectedStarts++;
        assertTrue("START must be delivered, not short-circuited by the already-running check.",
                repeat ? VpnServiceControls.restart(mContext) : VpnServiceControls.start(mContext));
    }

    private Snapshot awaitPhase(String phase, Phase expected, Set<Integer> expectedIds)
            throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 20_000;
        long stableSince = 0;
        int stableSamples = 0;
        Set<Integer> previous = null;
        String last = "No sample";
        while (SystemClock.elapsedRealtime() < deadline) {
            Snapshot snapshot = snapshot(phase);
            ActivityManager.RunningServiceInfo info = service();
            if (info != null) {
                assertEquals(Process.myUid(), info.uid);
                assertEquals(Process.myPid(), info.pid);
                if (mServiceBirth != 0) {
                    assertEquals("Service was recreated during the held binding.",
                            mServiceBirth, info.activeSince);
                }
            }
            if (mBound) {
                assertEquals("Binding was disconnected or replaced.", 1, mConnection.calls.get());
                assertFalse(mConnection.disconnected);
                assertTrue(mConnection.binder != null && mConnection.binder.isBinderAlive());
            }
            VpnStatus status = PreferenceHelper.getVpnServiceStatus(mContext);
            boolean vpn = hasVpn();
            boolean stateMatches = expected == Phase.ABSENT
                    ? info == null && status == STOPPED && !vpn
                    : expected == Phase.RUNNING
                    ? info != null && info.started && info.foreground && status == RUNNING && vpn
                            && mStartAcks.get() >= mExpectedStarts
                    : info != null && !info.started && !info.foreground && status == STOPPED && !vpn;
            boolean requestsMatch = expectedIds == null
                    ? snapshot.requests.size() == 1 : snapshot.ids().equals(expectedIds);
            if (expected == Phase.RUNNING && requestsMatch) {
                String row = snapshot.requests.values().iterator().next();
                assertTrue("Winner must be INTERNET and NOT_VPN: " + row,
                        Pattern.compile("\\bINTERNET\\b").matcher(row).find()
                                && Pattern.compile("\\bNOT_VPN\\b").matcher(row).find());
                assertTrue("Unexpected request owner package: " + row,
                        Pattern.compile("RequestorPackageName: "
                                + Pattern.quote(mContext.getPackageName()) + "(?:\\s|\\])")
                                .matcher(row).find());
            }
            last = phase + " own REQUEST ids=" + snapshot.ids() + " status=" + status
                    + " service=" + (info == null ? "absent" : "started=" + info.started
                    + ",foreground=" + info.foreground + ",birth=" + info.activeSince)
                    + " bound=" + mBound + " startAcks=" + mStartAcks.get();
            appendEvidence(last);
            if (stateMatches && requestsMatch && snapshot.ids().equals(previous)) {
                stableSamples++;
                if (stableSamples >= 3 && SystemClock.elapsedRealtime() - stableSince >= 1_000) {
                    Log.i(TAG, last);
                    return snapshot;
                }
            } else {
                stableSince = SystemClock.elapsedRealtime();
                stableSamples = stateMatches && requestsMatch ? 1 : 0;
            }
            previous = snapshot.ids();
            Thread.sleep(400);
        }
        throw new AssertionError("Lifecycle/request evidence did not converge: " + last
                + "; inspect " + mEvidence + ". This is not a passing lifecycle proof.");
    }

    private Snapshot snapshot(String phase) throws Exception {
        String dump;
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(
                             InstrumentationRegistry.getInstrumentation().getUiAutomation()
                                     .executeShellCommand("dumpsys -t 5 connectivity requests"))) {
            Future<String> read = mReader.submit(() -> {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (buffer.size() + count > 1_048_576) throw new IOException("Dump too large");
                    buffer.write(chunk, 0, count);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            });
            try {
                dump = read.get(8, TimeUnit.SECONDS);
            } finally {
                read.cancel(true);
            }
        }
        Files.write(new File(mEvidence, phase + ".txt").toPath(),
                dump.getBytes(StandardCharsets.UTF_8));
        try {
            return parse(dump, Process.myUid(), Process.myPid());
        } catch (IllegalArgumentException exception) {
            throw new AssertionError("Cannot reliably count framework requests; inspect "
                    + new File(mEvidence, phase + ".txt") + ". No proof claimed.", exception);
        }
    }

    private void appendEvidence(String line) throws IOException {
        Files.write(new File(mEvidence, "summary.txt").toPath(),
                (line + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private ActivityManager.RunningServiceInfo service() {
        ComponentName component = new ComponentName(mContext, VpnService.class);
        for (ActivityManager.RunningServiceInfo info : mActivity.getRunningServices(Integer.MAX_VALUE)) {
            if (component.equals(info.service)) return info;
        }
        return null;
    }

    private boolean hasVpn() {
        for (Network network : mConnectivity.getAllNetworks()) {
            NetworkCapabilities caps = mConnectivity.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(TRANSPORT_VPN)) return true;
        }
        return false;
    }

    @After
    public void releaseOwnedLifecycle() throws Exception {
        try {
            try {
                try {
                    if (mStartAttempted && service() != null) VpnServiceControls.stop(mContext);
                } finally {
                    if (mBound) {
                        mContext.unbindService(mConnection);
                        mBound = false;
                    }
                }
            } finally {
                try {
                    if (mStartAttempted) {
                        WorkManager.getInstance(mContext).cancelUniqueWork("vpnHeartbeat")
                                .getResult().get(5, TimeUnit.SECONDS);
                    }
                } finally {
                    if (mOriginalMethod != null) {
                        long deadline = SystemClock.elapsedRealtime() + 20_000;
                        while (service() != null && SystemClock.elapsedRealtime() < deadline) {
                            Thread.sleep(100);
                        }
                        assertTrue("Owned VPN service did not stop after cleanup.", service() == null);
                    }
                }
            }
        } finally {
            try {
                if (mOriginalMethod != null) PreferenceHelper.setAbBlockMethod(mContext, mOriginalMethod);
            } finally {
                try {
                    if (mTree != null) Timber.uproot(mTree);
                } finally {
                    if (mReader != null) {
                        mReader.shutdownNow();
                        assertTrue("Shell reader did not finish.",
                                mReader.awaitTermination(2, TimeUnit.SECONDS));
                    }
                }
            }
        }
    }

    // Parse the API30 current-request-only dump, never per-network copies or historical logs.
    private static Snapshot parse(String dump, int uid, int pid) {
        Map<Integer, String> requests = new TreeMap<>();
        Set<Integer> allIds = new TreeSet<>();
        boolean systemControl = false;
        for (String line : dump.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            Matcher matcher = ROW.matcher(line.trim());
            if (!matcher.matches()) throw new IllegalArgumentException("Unknown dump row: " + line);
            int owner = Integer.parseInt(matcher.group(1));
            int ownerPid = Integer.parseInt(matcher.group(2));
            String type = matcher.group(3);
            int id = Integer.parseInt(matcher.group(4));
            if (!allIds.add(id)) throw new IllegalArgumentException("Duplicate request ID: " + id);
            systemControl |= owner == 1000 && "REQUEST".equals(type);
            if (owner == uid && "REQUEST".equals(type)) {
                if (ownerPid != pid) throw new IllegalArgumentException("Other process owns UID request");
                requests.put(id, line.trim());
            }
        }
        if (!systemControl) throw new IllegalArgumentException("Missing system REQUEST positive control");
        return new Snapshot(requests);
    }

    @Test
    public void parserCountsOwnerRequestNotListenersBackgroundOrUidMentions() {
        String system = row(1000, 42, "REQUEST", 1, "INTERNET&NOT_VPN");
        String dump = system + row(12345, 99, "LISTEN", 2, "INTERNET&NOT_VPN")
                + row(12345, 99, "TRACK_DEFAULT", 3, "INTERNET")
                + row(12345, 99, "BACKGROUND_REQUEST", 4, "INTERNET")
                + row(99999, 77, "REQUEST", 5, "INTERNET Uid: 12345")
                        .replace("id=5,", "id=5, legacyType=0,")
                + row(12345, 99, "REQUEST", 6, "INTERNET&NOT_VPN");
        assertEquals(Collections.singleton(6), parse(dump, 12345, 99).ids());
        assertTrue(parse(system, 12345, 99).ids().isEmpty());
    }

    @Test
    public void parserRejectsMissingTruncatedAmbiguousAndDuplicateEvidence() {
        String system = row(1000, 42, "REQUEST", 1, "INTERNET&NOT_VPN");
        for (String dump : new String[]{"", "Permission Denial", "Network Requests:\n",
                system + "uid/pid:12345/99 NetworkRequest [ REQ", system + system,
                system + "uid/pid:12345/99 NetworkRequest [ REQUEST id=2, truncated ]\n",
                system + row(12345, 88, "REQUEST", 2, "INTERNET")}) {
            try {
                parse(dump, 12345, 99);
                fail("Unreliable evidence must never become a zero-request pass.");
            } catch (IllegalArgumentException expected) {
                // The live test preserves raw output and fails with an evidence path.
            }
        }
    }

    private static String row(int uid, int pid, String type, int id, String capabilities) {
        return "uid/pid:" + uid + "/" + pid + " NetworkRequest [ " + type + " id=" + id
                + ", [ Capabilities: " + capabilities + " ] ]\n";
    }

    private enum Phase { ABSENT, BOUND_STOPPED, RUNNING }

    private static final class Snapshot {
        final Map<Integer, String> requests;

        Snapshot(Map<Integer, String> requests) {
            this.requests = Collections.unmodifiableMap(requests);
        }

        Set<Integer> ids() {
            return new TreeSet<>(requests.keySet());
        }
    }

    private static final class Connection implements ServiceConnection {
        final CountDownLatch connected = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        volatile IBinder binder;
        volatile boolean disconnected;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            calls.incrementAndGet();
            connected.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            disconnected = true;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            connected.countDown();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            disconnected = true;
            connected.countDown();
        }
    }
}
