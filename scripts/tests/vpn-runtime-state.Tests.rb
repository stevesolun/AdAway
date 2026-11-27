#!/usr/bin/env ruby
require 'fileutils'
require 'open3'

REPO = File.expand_path('../..', __dir__)
JAVA_HOME = ENV.fetch('JAVA_HOME', '/opt/homebrew/opt/openjdk@21')
BUILD = File.join(REPO, 'build/vpn-runtime-state-test')
MAIN = 'app/src/main/java/'
abort 'Usage: ruby scripts/tests/vpn-runtime-state.Tests.rb' unless ARGV.empty?

jars = [
  ['junit/junit/4.13.2', 'junit-4.13.2.jar'],
  ['org.hamcrest/hamcrest-core/1.3', 'hamcrest-core-1.3.jar']
].map do |directory, name|
  Dir[File.expand_path("~/.gradle/caches/modules-2/files-2.1/#{directory}/*/#{name}")].first ||
    abort("Missing cached #{name}; no dependency downloads are performed.")
end

FileUtils.rm_rf(BUILD)
FileUtils.mkdir_p(BUILD)
write = lambda do |path, content|
  target = File.join(BUILD, 'src', path)
  FileUtils.mkdir_p(File.dirname(target))
  File.write(target, content)
end

production = %w[
  org/adaway/vpn/VpnServiceControls.java
  org/adaway/vpn/VpnStatus.java
  org/adaway/model/vpn/VpnModel.java
  org/adaway/model/OperationStatus.java
  org/adaway/model/adblocking/AdBlockModel.java
  org/adaway/model/adblocking/AdBlockMethod.java
  org/adaway/model/adblocking/UndefinedBlockModel.java
  org/adaway/model/error/HostError.java
  org/adaway/model/error/HostErrorException.java
  org/adaway/broadcast/Command.java
]
production.each { |path| write.call(path, File.read(File.join(REPO, MAIN, path))) }
resources = production.flat_map do |path|
  File.read(File.join(REPO, MAIN, path)).scan(/R\.string\.(\w+)/).flatten
end.uniq
write.call('org/adaway/R.java', <<~JAVA)
  package org.adaway;
  public final class R {
    public static final class string {
      #{resources.each_with_index.map { |name, index| "public static final int #{name} = #{index + 1};" }.join("\n")}
    }
  }
JAVA

{
  'androidx/annotation/StringRes.java' => 'package androidx.annotation; public @interface StringRes {}',
  'android/content/pm/ApplicationInfo.java' => <<~JAVA,
    package android.content.pm;
    public class ApplicationInfo { public int uid = 10001; }
  JAVA
  'android/content/ComponentName.java' => <<~JAVA,
    package android.content;
    public class ComponentName {
      private final String packageName;
      private final String className;
      public ComponentName(Context context, Class<?> type) {
        this(context.getPackageName(), type.getName());
      }
      public ComponentName(String packageName, String className) {
        this.packageName = packageName; this.className = className;
      }
      public String getPackageName() { return packageName; }
      public String getClassName() { return className; }
      @Override public boolean equals(Object other) {
        return other instanceof ComponentName &&
            packageName.equals(((ComponentName) other).packageName) &&
            className.equals(((ComponentName) other).className);
      }
      @Override public int hashCode() { return java.util.Objects.hash(packageName, className); }
    }
  JAVA
  'android/content/Intent.java' => <<~JAVA,
    package android.content;
    public class Intent {
      private final java.util.Map<String, String> extras = new java.util.HashMap<>();
      public Intent() {}
      public Intent(Context context, Class<?> type) {}
      public void putExtra(String key, String value) { extras.put(key, value); }
      public boolean hasExtra(String key) { return extras.containsKey(key); }
      public String getStringExtra(String key) { return extras.get(key); }
    }
  JAVA
  'android/content/Context.java' => <<~JAVA,
    package android.content;
    import android.app.ActivityManager;
    import android.net.ConnectivityManager;
    import android.content.pm.ApplicationInfo;
    import org.adaway.vpn.VpnStatus;
    public class Context {
      public static final String CONNECTIVITY_SERVICE = "connectivity";
      public static final String ACTIVITY_SERVICE = "activity";
      public ConnectivityManager connectivity = new ConnectivityManager();
      public ActivityManager activity = new ActivityManager();
      public final ApplicationInfo applicationInfo = new ApplicationInfo();
      public VpnStatus status = VpnStatus.STOPPED;
      public int statusWrites;
      public boolean consent = true;
      public int prepareCalls;
      public int launches;
      public Intent lastLaunch;
      public boolean launchAccepted = true;
      public boolean throwOnLaunch;
      public int heartbeatStarts;
      public int heartbeatStops;
      public Object getSystemService(String service) {
        if (CONNECTIVITY_SERVICE.equals(service)) return connectivity;
        if (ACTIVITY_SERVICE.equals(service)) return activity;
        throw new AssertionError("Unexpected service " + service);
      }
      public <T> T getSystemService(Class<T> service) {
        if (service == ActivityManager.class) return service.cast(activity);
        if (service == ConnectivityManager.class) return service.cast(connectivity);
        throw new AssertionError("Unexpected service " + service);
      }
      public String getPackageName() { return "org.adaway"; }
      public ApplicationInfo getApplicationInfo() { return applicationInfo; }
      public String getString(int value, Object... arguments) { return Integer.toString(value); }
      public ComponentName startForegroundService(Intent intent) {
        launches++; lastLaunch = intent;
        if (throwOnLaunch) throw new IllegalStateException("Launch rejected");
        return launchAccepted ? new ComponentName(this, org.adaway.vpn.VpnService.class) : null;
      }
      public ComponentName startService(Intent intent) {
        throw new AssertionError("Unexpected background service launch");
      }
    }
  JAVA
  'android/app/ActivityManager.java' => <<~JAVA,
    package android.app;
    import android.content.ComponentName;
    public class ActivityManager {
      public final java.util.List<RunningServiceInfo> services = new java.util.ArrayList<>();
      public java.util.List<RunningServiceInfo> getRunningServices(int maximum) { return services; }
      public static class RunningServiceInfo {
        public ComponentName service;
        public int pid;
        public int uid;
        public long restarting;
        public boolean foreground;
      }
    }
  JAVA
  'android/net/Network.java' => 'package android.net; public class Network {}',
  'android/net/NetworkCapabilities.java' => <<~JAVA,
    package android.net;
    public class NetworkCapabilities {
      public static final int TRANSPORT_VPN = 4;
      public boolean vpn;
      public int ownerUid = -1;
      public boolean hasTransport(int transport) { return transport == TRANSPORT_VPN && vpn; }
      public int getOwnerUid() { return ownerUid; }
    }
  JAVA
  'android/net/ConnectivityManager.java' => <<~JAVA,
    package android.net;
    public class ConnectivityManager {
      public final java.util.Map<Network, NetworkCapabilities> networks = new java.util.LinkedHashMap<>();
      public Network[] getAllNetworks() { return networks.keySet().toArray(new Network[0]); }
      public NetworkCapabilities getNetworkCapabilities(Network network) { return networks.get(network); }
    }
  JAVA
  'android/net/VpnService.java' => <<~JAVA,
    package android.net;
    import android.content.Context;
    import android.content.Intent;
    public class VpnService {
      public static Intent prepare(Context context) {
        context.prepareCalls++;
        return context.consent ? null : new Intent();
      }
    }
  JAVA
  'android/os/Build.java' => <<~JAVA,
    package android.os;
    public class Build {
      public static class VERSION { public static int SDK_INT = 30; }
      public static class VERSION_CODES { public static final int S = 31; }
    }
  JAVA
  'android/util/LruCache.java' => <<~JAVA,
    package android.util;
    public class LruCache<K, V> {
      private final java.util.Map<K, V> values = new java.util.HashMap<>();
      private int hits;
      private int misses;
      public LruCache(int size) {}
      protected V create(K key) { return null; }
      public V get(K key) {
        if (values.containsKey(key)) { hits++; return values.get(key); }
        misses++;
        V value = create(key);
        if (value != null) values.put(key, value);
        return value;
      }
      public int hitCount() { return hits; }
      public int missCount() { return misses; }
    }
  JAVA
  'androidx/lifecycle/LiveData.java' => <<~JAVA,
    package androidx.lifecycle;
    public class LiveData<T> {
      protected T value;
      public final java.util.List<T> published = new java.util.ArrayList<>();
      public T getValue() { return value; }
    }
  JAVA
  'androidx/lifecycle/MutableLiveData.java' => <<~JAVA,
    package androidx.lifecycle;
    public class MutableLiveData<T> extends LiveData<T> {
      public void postValue(T value) { this.value = value; published.add(value); }
    }
  JAVA
  'org/adaway/helper/PreferenceHelper.java' => <<~JAVA,
    package org.adaway.helper;
    import android.content.Context;
    import org.adaway.vpn.VpnStatus;
    public class PreferenceHelper {
      public static VpnStatus getVpnServiceStatus(Context context) { return context.status; }
      public static void setVpnServiceStatus(Context context, VpnStatus status) {
        context.statusWrites++; context.status = status;
      }
    }
  JAVA
  'org/adaway/vpn/VpnService.java' => 'package org.adaway.vpn; public class VpnService {}',
  'org/adaway/vpn/VpnServiceHeartbeat.java' => <<~JAVA,
    package org.adaway.vpn;
    import android.content.Context;
    public class VpnServiceHeartbeat {
      public static void start(Context context) { context.heartbeatStarts++; }
      public static void stop(Context context) { context.heartbeatStops++; }
    }
  JAVA
  'org/adaway/db/entity/HostEntry.java' => 'package org.adaway.db.entity; public class HostEntry {}',
  'org/adaway/db/dao/HostEntryDao.java' => <<~JAVA,
    package org.adaway.db.dao;
    import org.adaway.db.entity.HostEntry;
    public class HostEntryDao {
      public int lookups;
      public HostEntry result = new HostEntry();
      public HostEntry resolveEntry(String host) { lookups++; return result; }
    }
  JAVA
  'org/adaway/db/AppDatabase.java' => <<~JAVA,
    package org.adaway.db;
    import android.content.Context;
    import org.adaway.db.dao.HostEntryDao;
    public class AppDatabase {
      private final HostEntryDao dao = new HostEntryDao();
      public static AppDatabase getInstance(Context context) { return new AppDatabase(); }
      public HostEntryDao hostEntryDao() { return dao; }
    }
  JAVA
  'org/adaway/model/root/RootModel.java' => <<~JAVA,
    package org.adaway.model.root;
    import android.content.Context;
    import org.adaway.model.adblocking.AdBlockMethod;
    import org.adaway.model.adblocking.UndefinedBlockModel;
    public class RootModel extends UndefinedBlockModel {
      public int applyCalls;
      public RootModel(Context context) { super(context); }
      @Override public AdBlockMethod getMethod() { return AdBlockMethod.ROOT; }
      @Override public void apply() { applyCalls++; }
    }
  JAVA
  'timber/log/Timber.java' => <<~JAVA
    package timber.log;
    public class Timber {
      public static void d(Object... values) {}
      public static void w(Object... values) {}
    }
  JAVA
}.each { |path, content| write.call(path, content) }

write.call('org/adaway/model/adblocking/PassiveModeHostTest.java', <<~JAVA)
  package org.adaway.model.adblocking;
  import android.content.Context;
  import org.adaway.model.root.RootModel;
  import org.junit.Test;
  import static org.junit.Assert.*;

  public class PassiveModeHostTest {
    @Test public void undefinedRefreshCannotAcknowledgeProtection() throws Exception {
      AdBlockModel model = AdBlockModel.build(new Context(), AdBlockMethod.UNDEFINED);
      assertFalse("No method is using the refreshed rules", model.applyAfterBackgroundRefresh());
      assertTrue(model.isApplied().published.isEmpty());
      assertTrue(model.getState().published.isEmpty());
    }

    @Test public void rootRefreshAppliesOnceBeforeAcknowledgingProtection() throws Exception {
      RootModel model = (RootModel) AdBlockModel.build(new Context(), AdBlockMethod.ROOT);
      assertTrue(model.applyAfterBackgroundRefresh());
      assertEquals(1, model.applyCalls);
    }
  }
JAVA

write.call('org/adaway/vpn/VpnRuntimeStateHostTest.java', <<~JAVA)
  package org.adaway.vpn;
  import android.app.ActivityManager.RunningServiceInfo;
  import android.content.ComponentName;
  import android.content.Context;
  import android.net.Network;
  import android.net.NetworkCapabilities;
  import android.os.Build;
  import org.adaway.broadcast.Command;
  import org.junit.Test;
  import static org.junit.Assert.*;

  public class VpnRuntimeStateHostTest {
    private Context runningContext(int api) {
      Build.VERSION.SDK_INT = api;
      Context context = new Context();
      context.status = VpnStatus.RUNNING;
      return context;
    }
    private void network(Context context, boolean vpn, int ownerUid) {
      NetworkCapabilities caps = new NetworkCapabilities();
      caps.vpn = vpn; caps.ownerUid = ownerUid;
      context.connectivity.networks.put(new Network(), caps);
    }
    private RunningServiceInfo service(Context context) {
      RunningServiceInfo service = new RunningServiceInfo();
      service.service = new ComponentName(context, VpnService.class);
      service.pid = 123; service.uid = context.getApplicationInfo().uid;
      service.foreground = true;
      context.activity.services.add(service);
      return service;
    }
    private void unchanged(Context context) {
      assertEquals(VpnStatus.RUNNING, context.status);
      assertEquals(0, context.statusWrites);
    }

    @Test public void staleRunningThenForeignVpnCannotSuppressStartOnLegacyApis() {
      for (int api : new int[]{26, 30}) {
        Context context = runningContext(api);
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        unchanged(context);
        network(context, true, 99999);
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        assertFalse(VpnServiceControls.isRunning(context));
        unchanged(context);
        assertTrue(VpnServiceControls.start(context));
        assertEquals(1, context.launches);
        assertEquals(Command.START, Command.readFromIntent(context.lastLaunch));
        assertEquals(1, context.heartbeatStarts);
        unchanged(context);
      }
    }

    @Test public void currentForegroundServiceAndVpnNetworkAreBothRequiredOnLegacyApis() {
      for (int api : new int[]{26, 30}) {
        Context context = runningContext(api);
        service(context);
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        network(context, false, context.getApplicationInfo().uid);
        context.connectivity.networks.put(new Network(), null);
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        network(context, true, -1);
        assertTrue(VpnServiceControls.isTunnelEstablished(context));
        assertTrue(VpnServiceControls.start(context));
        assertEquals(0, context.launches);
        unchanged(context);
        context.connectivity.networks.clear();
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        unchanged(context);
      }
    }

    @Test public void stoppedStatusCannotBecomeEstablishedFromServiceAndNetwork() {
      Context context = runningContext(30);
      service(context); network(context, true, -1);
      for (VpnStatus status : VpnStatus.values()) {
        if (status == VpnStatus.RUNNING) continue;
        context.status = status;
        assertFalse(status.name(), VpnServiceControls.isTunnelEstablished(context));
        assertEquals(status, context.status);
        assertEquals(0, context.statusWrites);
      }
    }

    @Test public void deadRestartingAndPassiveRecordsDoNotProveLegacyTunnelOwnership() {
      for (int api : new int[]{26, 30}) {
        Context context = runningContext(api);
        network(context, true, 99999);
        RunningServiceInfo record = service(context);
        record.pid = 0;
        assertFalse(VpnServiceControls.isVpnServiceRunning(context));
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        record.pid = 123; record.restarting = 100;
        assertFalse(VpnServiceControls.isVpnServiceRunning(context));
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        record.restarting = 0; record.foreground = false;
        assertTrue(VpnServiceControls.isVpnServiceRunning(context));
        assertFalse(VpnServiceControls.isTunnelEstablished(context));
        record.foreground = true;
        assertTrue(VpnServiceControls.isTunnelEstablished(context));
        unchanged(context);
      }
    }

    @Test public void matchingRequiresExactApplicationAndServiceComponent() {
      Context context = runningContext(30);
      network(context, true, -1);
      RunningServiceInfo record = service(context);
      record.service = new ComponentName("another.publisher", VpnService.class.getName());
      assertFalse(VpnServiceControls.isVpnServiceRunning(context));
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      record.service = new ComponentName(context.getPackageName(), "another.Service");
      assertFalse(VpnServiceControls.isVpnServiceRunning(context));
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      record.service = null;
      assertFalse(VpnServiceControls.isVpnServiceRunning(context));
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      service(context);
      assertTrue(VpnServiceControls.isTunnelEstablished(context));
      unchanged(context);
    }

    @Test public void missingPlatformManagersCannotProveTunnelOwnership() {
      Context context = runningContext(30);
      network(context, true, -1);
      context.activity = null;
      assertFalse(VpnServiceControls.isVpnServiceRunning(context));
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      context.connectivity = null;
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      unchanged(context);
    }

    @Test public void modernOwnershipUsesUidInsteadOfAcceptingForeignVpn() {
      Context context = runningContext(31);
      service(context);
      network(context, true, 99999);
      assertFalse(VpnServiceControls.isTunnelEstablished(context));
      context.connectivity.networks.clear();
      network(context, true, context.getApplicationInfo().uid);
      assertTrue(VpnServiceControls.isTunnelEstablished(context));
      context.activity.services.clear();
      assertTrue(VpnServiceControls.isTunnelEstablished(context));
      unchanged(context);
    }
  }
JAVA

write.call('org/adaway/model/vpn/VpnApplyConsentHostTest.java', <<~JAVA)
  package org.adaway.model.vpn;
  import android.content.Context;
  import android.net.Network;
  import android.net.NetworkCapabilities;
  import android.os.Build;
  import org.adaway.R;
  import org.adaway.db.dao.HostEntryDao;
  import org.adaway.db.entity.HostEntry;
  import org.adaway.model.error.HostErrorException;
  import org.adaway.vpn.VpnStatus;
  import org.junit.Test;
  import static org.adaway.model.error.HostError.ENABLE_VPN_FAIL;
  import static org.junit.Assert.*;

  public class VpnApplyConsentHostTest {
    private void rejected(VpnModel model) {
      try {
        model.apply();
        fail("Rejected VPN apply must report an error");
      } catch (HostErrorException error) {
        assertEquals(ENABLE_VPN_FAIL, error.getError());
      }
      assertEquals(Boolean.FALSE, model.isApplied().getValue());
    }

    @Test public void revokedConsentClearsPreviouslyAppliedStateWithoutLaunchingService() throws Exception {
      Build.VERSION.SDK_INT = 30;
      Context context = new Context();
      VpnModel model = new VpnModel(context, new HostEntryDao());
      model.apply();
      assertEquals(Boolean.TRUE, model.isApplied().getValue());
      assertEquals(1, context.launches);
      int states = model.getState().published.size();
      context.consent = false;
      rejected(model);
      rejected(model);
      assertEquals(1, context.launches);
      assertEquals(1, context.heartbeatStarts);
      assertEquals(states, model.getState().published.size());
      assertEquals(java.util.Arrays.asList(false, true, false, false), model.isApplied().published);
      context.consent = true;
      model.apply();
      assertEquals(Boolean.TRUE, model.isApplied().getValue());
      assertEquals(2, context.launches);
      assertEquals(4, context.prepareCalls);
    }

    @Test public void revokedConsentCannotUseAlreadyRunningShortcutAsApplySuccess() {
      Build.VERSION.SDK_INT = 31;
      Context context = new Context();
      context.status = VpnStatus.RUNNING;
      NetworkCapabilities caps = new NetworkCapabilities();
      caps.vpn = true; caps.ownerUid = context.getApplicationInfo().uid;
      context.connectivity.networks.put(new Network(), caps);
      VpnModel model = new VpnModel(context, new HostEntryDao());
      assertEquals(Boolean.TRUE, model.isApplied().getValue());
      context.consent = false;
      rejected(model);
      assertEquals(0, context.launches);
      assertEquals(0, context.heartbeatStarts);
      assertNull(model.getState().getValue());
    }

    @Test public void authorizedApplyPublishesSuccessAndInvalidatesRulesCache() throws Exception {
      Build.VERSION.SDK_INT = 30;
      Context context = new Context();
      HostEntryDao dao = new HostEntryDao();
      VpnModel model = new VpnModel(context, dao);
      model.getEntry("example.test"); model.getEntry("example.test");
      assertEquals(1, dao.lookups);
      model.apply();
      model.getEntry("example.test");
      assertEquals(2, dao.lookups);
      assertEquals(Boolean.TRUE, model.isApplied().getValue());
      assertEquals(Integer.toString(R.string.status_vpn_configuration_updated),
          model.getState().getValue().resolve(context));
      assertEquals(1, context.prepareCalls);
      assertEquals(1, context.launches);
      assertEquals(1, context.heartbeatStarts);
    }

    @Test public void passiveRefreshRetiresCachedRulesWithoutPreparingOrStartingVpn() throws Exception {
      Build.VERSION.SDK_INT = 31;
      for (VpnStatus status : new VpnStatus[]{VpnStatus.STOPPED, VpnStatus.WAITING_FOR_NETWORK,
          VpnStatus.RUNNING}) {
        for (boolean ownVpn : new boolean[]{false, true}) {
          Context context = new Context();
          context.status = status;
          context.consent = false;
          NetworkCapabilities caps = new NetworkCapabilities();
          caps.vpn = true;
          caps.ownerUid = ownVpn ? context.getApplicationInfo().uid : 99999;
          context.connectivity.networks.put(new Network(), caps);
          HostEntryDao dao = new HostEntryDao();
          VpnModel model = new VpnModel(context, dao);
          HostEntry disabledSourceResult = model.getEntry("reenabled-source.test");
          dao.result = new HostEntry();
          assertSame(disabledSourceResult, model.getEntry("reenabled-source.test"));
          assertEquals(1, dao.lookups);
          int appliedPublications = model.isApplied().published.size();

          assertEquals(status == VpnStatus.RUNNING && ownVpn, model.applyAfterBackgroundRefresh());

          assertSame(dao.result, model.getEntry("reenabled-source.test"));
          assertSame(dao.result, model.getEntry("reenabled-source.test"));
          assertEquals(2, dao.lookups);
          assertEquals(Integer.toString(R.string.status_filter_rules_updated),
              model.getState().getValue().resolve(context));
          assertEquals(appliedPublications, model.isApplied().published.size());
          assertEquals(status, context.status);
          assertEquals(0, context.statusWrites);
          assertEquals(0, context.prepareCalls);
          assertEquals(0, context.launches);
          assertEquals(0, context.heartbeatStarts);
          assertEquals(0, context.heartbeatStops);
        }
      }
    }

    @Test public void authorizedButRejectedLaunchDoesNotPublishAppliedOrSuccessState() {
      Build.VERSION.SDK_INT = 30;
      for (boolean throwsException : new boolean[]{false, true}) {
        Context context = new Context();
        context.launchAccepted = false; context.throwOnLaunch = throwsException;
        VpnModel model = new VpnModel(context, new HostEntryDao());
        rejected(model);
        assertEquals(1, context.prepareCalls);
        assertEquals(1, context.launches);
        assertEquals(0, context.heartbeatStarts);
        assertNull(model.getState().getValue());
      }
    }
  }
JAVA

classes = File.join(BUILD, 'classes')
FileUtils.mkdir_p(classes)
compile = [File.join(JAVA_HOME, 'bin/javac'), '--release', '21', '-cp', jars.join(':'),
           '-d', classes, *Dir[File.join(BUILD, 'src/**/*.java')]]
output, status = Open3.capture2e(*compile)
File.write(File.join(BUILD, 'compile.txt'), output)
puts output unless output.empty?
abort 'VPN runtime fixture compilation failed' unless status.success?
output, status = Open3.capture2e(File.join(JAVA_HOME, 'bin/java'), '-cp', ([classes] + jars).join(':'),
                               'org.junit.runner.JUnitCore',
                               'org.adaway.model.adblocking.PassiveModeHostTest',
                               'org.adaway.vpn.VpnRuntimeStateHostTest',
                               'org.adaway.model.vpn.VpnApplyConsentHostTest')
File.write(File.join(BUILD, 'results.txt'), output)
puts 'Host regression fixtures with platform doubles; no Android lifecycle/device execution.'
puts output
abort 'VPN runtime fixtures failed' unless status.success?
