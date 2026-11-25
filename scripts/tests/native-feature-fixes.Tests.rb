#!/usr/bin/env ruby
# Host-only proof: real wrapper classes, exact recording-method excerpts and throwing I/O doubles.
require 'fileutils'
require 'open3'
require 'tmpdir'

REPO = File.expand_path('../..', __dir__)
JAVA_HOME = ENV.fetch('JAVA_HOME', '/opt/homebrew/opt/openjdk@21')
MAIN = 'app/src/main/java/org/adaway/'
baseline = ARGV.delete('--baseline')
abort 'Usage: ruby scripts/tests/native-feature-fixes.Tests.rb [--baseline]' unless ARGV.empty?

def source(path, baseline)
  return File.read(File.join(REPO, path)) unless baseline
  output, status = Open3.capture2('git', '-C', REPO, 'show', "HEAD:#{path}")
  abort "Cannot read baseline #{path}" unless status.success?
  output
end

# These bounded Java methods contain no braces in strings or comments.
def method(source, signature)
  start = source.index(signature) or abort "Missing method #{signature}"
  brace = source.index('{', start)
  depth = 1
  finish = brace + 1
  while depth.positive? && finish < source.length
    depth += 1 if source[finish] == '{'
    depth -= 1 if source[finish] == '}'
    finish += 1
  end
  abort "Unbalanced method #{signature}" unless depth.zero?
  source[start...finish]
end

jars = [
  ['junit/junit/4.13.2', 'junit-4.13.2.jar'],
  ['org.hamcrest/hamcrest-core/1.3', 'hamcrest-core-1.3.jar']
].map do |directory, name|
  Dir[File.expand_path("~/.gradle/caches/modules-2/files-2.1/#{directory}/*/#{name}")].first ||
    abort("Missing cached #{name}; no dependency downloads are performed.")
end

Dir.mktmpdir('adaway-native-features-') do |tmp|
  write = lambda do |path, content|
    target = File.join(tmp, path)
    FileUtils.mkdir_p(File.dirname(target))
    File.write(target, content)
  end
  {
    'android/content/Context.java' => <<~JAVA,
      package android.content;
      public class Context {
        public java.io.File getCacheDir() { throw new AssertionError("cache access"); }
        public String getString(int id) { return Integer.toString(id); }
      }
    JAVA
    'androidx/annotation/StringRes.java' => 'package androidx.annotation; public @interface StringRes {}',
    'androidx/annotation/WorkerThread.java' => 'package androidx.annotation; public @interface WorkerThread {}',
    'timber/log/Timber.java' => <<~JAVA,
      package timber.log;
      public class Timber {
        public static void w(Object... args) {}
        public static void e(Object... args) {}
        public static void d(Object... args) {}
        public static void i(Object... args) {}
      }
    JAVA
    'com/topjohnwu/superuser/Shell.java' => <<~JAVA,
      package com.topjohnwu.superuser;
      public class Shell {
        public static Shell cmd(String... args) { throw new AssertionError("shell command"); }
        public Result exec() { throw new AssertionError("shell execution"); }
        public static class Result {
          public int getCode() { return 1; }
          public java.util.List<String> getOut() { return java.util.Collections.emptyList(); }
        }
      }
    JAVA
    'com/topjohnwu/superuser/ShellUtils.java' => <<~JAVA,
      package com.topjohnwu.superuser;
      public class ShellUtils {
        public static String escapedString(String value) { return value; }
      }
    JAVA
    'org/adaway/model/root/ShellUtils.java' => <<~JAVA,
      package org.adaway.model.root;
      public class ShellUtils {
        public static final java.util.List<String> cleanup =
            new java.util.concurrent.CopyOnWriteArrayList<>();
        public static volatile java.util.concurrent.CountDownLatch entered, release;
        public static boolean isBundledExecutableRunning(String name) {
          throw new AssertionError("process query");
        }
        public static void killBundledExecutable(String name) {
          if (!Thread.currentThread().getName().equals("legacy-native-fixture")) {
            throw new AssertionError("process cleanup");
          }
          cleanup.add(name);
          entered.countDown();
          try {
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
              throw new AssertionError("fixture cleanup release timed out");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new AssertionError(e);
          }
        }
        public static String mergeAllLines(java.util.List<String> lines) { return ""; }
        public static boolean runBundledExecutable(android.content.Context c, String n, String p) {
          throw new AssertionError("native launch");
        }
      }
    JAVA
    'okhttp3/OkHttpClient.java' => <<~JAVA,
      package okhttp3;
      public class OkHttpClient {
        public OkHttpClient() { throw new AssertionError("network client creation"); }
        public Call newCall(Request r) { throw new AssertionError("network request"); }
        public static class Call {
          public Response execute() throws java.io.IOException { throw new AssertionError("network"); }
        }
      }
    JAVA
    'okhttp3/Request.java' => <<~JAVA,
      package okhttp3;
      public class Request {
        public static class Builder {
          public Builder url(String url) { return this; }
          public Request build() { return new Request(); }
        }
      }
    JAVA
    'okhttp3/Response.java' => <<~JAVA,
      package okhttp3;
      public class Response implements AutoCloseable {
        public boolean isSuccessful() { return false; }
        public void close() {}
      }
    JAVA
    'org/adaway/R.java' => <<~JAVA
      package org.adaway;
      public class R {
        public static class string {
          public static final int pref_webserver_state_unavailable = 1;
          public static final int pref_webserver_state_not_running = 2;
          public static final int pref_webserver_state_running_and_installed = 3;
          public static final int pref_webserver_state_running_not_installed = 4;
          public static final int pref_webserver_enabled_key = 5;
          public static final int pref_webserver_icon_key = 6;
          public static final int pref_webserver_test_key = 7;
          public static final int pref_webserver_unavailable = 8;
          public static final int pref_webserver_state_checking = 9;
        }
      }
    JAVA
  }.each { |path, content| write.call(path, content) }

  %w[model/root/TcpdumpUtils.java util/WebServerUtils.java].each do |path|
    write.call("org/adaway/#{path}", source(MAIN + path, baseline))
  end
  log = source(MAIN + 'ui/log/LogViewModel.java', baseline)
  vpn = source(MAIN + 'model/vpn/VpnModel.java', baseline)
  initialization = log.match(/this\.recording = new MutableLiveData<>\((.*?)\);/m)&.captures&.first
  abort 'Missing recording initialization' unless initialization
  write.call('NativeRecordingHostTest.java', <<~JAVA)
    import org.junit.Test;
    import static org.junit.Assert.*;
    public class NativeRecordingHostTest {
      enum AdBlockMethod { ROOT, VPN }
      static class Model {
        AdBlockMethod mode = AdBlockMethod.VPN;
        boolean state, reject;
        int calls;
        AdBlockMethod getMethod() { return mode; }
        public boolean isRecordingLogs() {
          if (mode == AdBlockMethod.ROOT) throw new AssertionError("unavailable backend query");
          return state;
        }
        public void setRecordingLogs(boolean value) { calls++; if (!reject) state = value; }
      }
      static class VpnModel extends Model {
        boolean recordingLogs;
        #{method(vpn, 'public boolean isRecordingLogs()')}
        #{method(vpn, 'public void setRecordingLogs(boolean recording)')}
      }
      static class MutableLiveData<T> {
        T value;
        MutableLiveData(T initial) { value = initial; }
        void postValue(T next) { value = next; }
      }
      static class Probe {
        final Model adBlockModel;
        final MutableLiveData<Boolean> recording;
        Probe(Model model) {
          this.adBlockModel = model;
          this.recording = new MutableLiveData<>(#{initialization});
        }
        #{method(log, 'public boolean isDnsRequestLoggingUnavailable()')}
        #{method(log, 'public void toggleRecording()')}
      }
      @Test public void rootRepeatedToggleAndRecreationNeverQueryOrStartBackend() {
        Model model = new Model(); model.mode = AdBlockMethod.ROOT; model.state = true;
        for (int recreation = 0; recreation < 2; recreation++) {
          Probe probe = new Probe(model);
          assertFalse(probe.recording.value);
          probe.recording.value = true;
          for (int tap = 0; tap < 3; tap++) {
            probe.toggleRecording(); assertFalse(probe.recording.value);
          }
        }
        assertEquals(0, model.calls);
      }
      @Test public void exactVpnAccessorsStillStartPauseAndSurviveViewModelRecreation() {
        VpnModel model = new VpnModel(); Probe probe = new Probe(model);
        assertFalse(probe.isDnsRequestLoggingUnavailable());
        probe.toggleRecording(); assertTrue(model.isRecordingLogs()); assertTrue(probe.recording.value);
        Probe recreated = new Probe(model); assertTrue(recreated.recording.value);
        recreated.toggleRecording(); assertFalse(model.isRecordingLogs());
        assertFalse(recreated.recording.value);
      }
      @Test public void rejectedStartPublishesFalseAcrossRepeatedRequests() {
        Model model = new Model(); model.reject = true; Probe probe = new Probe(model);
        probe.toggleRecording(); probe.toggleRecording();
        assertFalse(probe.recording.value); assertEquals(2, model.calls);
      }
      @Test public void rejectedStopStillPublishesTrue() {
        Model model = new Model(); model.reject = true; model.state = true;
        Probe probe = new Probe(model); probe.toggleRecording();
        assertTrue(probe.recording.value); assertEquals(1, model.calls);
      }
    }
  JAVA

  prefs = source(MAIN + 'ui/prefs/PrefsRootFragment.java', baseline)
  write.call('NativePreferencesHostTest.java', <<~JAVA)
    import android.content.Context;
    import org.adaway.R;
    import org.junit.Test;
    import static org.junit.Assert.*;
    import static org.adaway.util.WebServerUtils.*;
    public class NativePreferencesHostTest {
      static class SharedPreferences {}
      static class Preference {
        boolean enabled = true; int summary;
        void setEnabled(boolean enabled) { this.enabled = enabled; }
        void setSummary(int summary) { this.summary = summary; }
      }
      static class SwitchPreferenceCompat extends Preference {
        boolean checked = true;
        void setChecked(boolean checked) { this.checked = checked; }
      }
      static class AppExecutors {
        static AppExecutors getInstance() { throw new AssertionError("scheduled network work"); }
        java.util.concurrent.Executor networkIO() { throw new AssertionError("network executor"); }
        java.util.concurrent.Executor mainThread() { throw new AssertionError("main executor"); }
      }
      static class Probe {
        final Context context = new Context();
        final SwitchPreferenceCompat enabled = new SwitchPreferenceCompat();
        final SwitchPreferenceCompat icon = new SwitchPreferenceCompat();
        final Preference test = new Preference();
        static final String PREFERENCE_NOT_FOUND = "missing preference";
        Context getContext() { return context; }
        Context requireContext() { return context; }
        String getString(int id) { return context.getString(id); }
        @SuppressWarnings("unchecked") <T extends Preference> T findPreference(String key) {
          return (T) (key.equals(getString(R.string.pref_webserver_enabled_key)) ? enabled :
              key.equals(getString(R.string.pref_webserver_icon_key)) ? icon : test);
        }
        #{method(prefs, 'public void onSharedPreferenceChanged(')}
        #{method(prefs, 'private void configureWebServerAvailability()')}
        #{method(prefs, 'private void updateWebServerState()')}
      }
      @Test public void repeatedConfigurationAndRecreationOnlyRenderDisabledControls() {
        for (int recreation = 0; recreation < 2; recreation++) {
          Probe probe = new Probe();
          probe.configureWebServerAvailability(); probe.configureWebServerAvailability();
          assertFalse(probe.enabled.enabled); assertFalse(probe.enabled.checked);
          assertFalse(probe.icon.enabled); assertFalse(probe.icon.checked);
          assertFalse(probe.test.enabled);
          assertEquals(R.string.pref_webserver_unavailable, probe.enabled.summary);
        }
      }
      @Test public void externalIconPreferenceChangeDoesNotQueryShell() {
        Probe probe = new Probe();
        probe.onSharedPreferenceChanged(new SharedPreferences(),
            probe.getString(R.string.pref_webserver_icon_key));
      }
      @Test public void unavailableStateDoesNotScheduleHealthProbe() {
        Probe probe = new Probe(); probe.updateWebServerState();
        assertEquals(R.string.pref_webserver_state_unavailable, probe.test.summary);
      }
    }
  JAVA

  unless baseline
    root = source(MAIN + 'model/root/RootModel.java', false)
    production_paths = %w[
      model/root/RootModel.java model/root/TcpdumpUtils.java util/WebServerUtils.java
      ui/log/LogActivity.java ui/log/LogViewModel.java ui/prefs/PrefsRootFragment.java
    ].map { |path| File.join(REPO, MAIN + path).dump }.join(', ')
    write.call('NativeSourceSyntaxHostTest.java', <<~JAVA)
      import org.junit.Test;
      import javax.tools.*;
      import com.sun.source.util.JavacTask;
      import static org.junit.Assert.*;
      public class NativeSourceSyntaxHostTest {
        @Test public void allSixChangedProductionFilesParse() throws Exception {
          JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
          DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
          try (StandardJavaFileManager files = compiler.getStandardFileManager(
              diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                java.util.Arrays.asList("-proc:none", "--release", "17"), null,
                files.getJavaFileObjectsFromStrings(java.util.Arrays.asList(#{production_paths})));
            task.parse();
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
              assertNotEquals(diagnostic.toString(), Diagnostic.Kind.ERROR, diagnostic.getKind());
            }
          }
        }
      }
    JAVA
    write.call('org/adaway/model/root/NativeCleanupHostTest.java', <<~JAVA)
      package org.adaway.model.root;
      import org.adaway.util.WebServerUtils;
      import org.junit.Test;
      import java.util.concurrent.*;
      import static org.junit.Assert.*;
      public class NativeCleanupHostTest {
        #{method(root, 'private void cleanupLegacyNativeProcesses()')}
        @Test public void blockedLegacyCleanupDoesNotBlockDisabledControls() throws Exception {
          ShellUtils.cleanup.clear();
          ShellUtils.entered = new CountDownLatch(1); ShellUtils.release = new CountDownLatch(1);
          ExecutorService worker = Executors.newSingleThreadExecutor(
              r -> new Thread(r, "legacy-native-fixture"));
          Future<?> cleanup = worker.submit(this::cleanupLegacyNativeProcesses);
          try {
            assertTrue(ShellUtils.entered.await(5, TimeUnit.SECONDS));
            assertFalse(cleanup.isDone());
            assertFalse(TcpdumpUtils.startTcpdump(null)); TcpdumpUtils.stopTcpdump();
            WebServerUtils.startWebServer(null); WebServerUtils.stopWebServer();
            assertFalse(WebServerUtils.isWebServerRunning());
            assertFalse(TcpdumpUtils.isTcpdumpRunning());
            assertFalse(cleanup.isDone());
            ShellUtils.release.countDown(); cleanup.get(5, TimeUnit.SECONDS);
            assertEquals(java.util.Arrays.asList("webserver", "tcpdump"), ShellUtils.cleanup);
          } finally {
            ShellUtils.release.countDown(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
          }
        }
      }
    JAVA
  end

  tests = %w[
    app/src/test/java/org/adaway/model/root/NativeFeatureAvailabilityTest.java
    app/src/test/java/org/adaway/ui/log/NativeFeatureUiContractTest.java
  ]
  classes = File.join(tmp, 'classes')
  FileUtils.mkdir_p(classes)
  command = [File.join(JAVA_HOME, 'bin/javac'), '--release', '17', '-cp', jars.join(':'),
             '-d', classes, *Dir[File.join(tmp, '**/*.java')], *tests.map { |p| File.join(REPO, p) }]
  abort 'Fixture compilation failed' unless system(*command)
  test_names = %w[
    org.adaway.model.root.NativeFeatureAvailabilityTest NativeRecordingHostTest
    NativePreferencesHostTest
  ]
  unless baseline
    test_names += %w[
      org.adaway.ui.log.NativeFeatureUiContractTest org.adaway.model.root.NativeCleanupHostTest
      NativeSourceSyntaxHostTest
    ]
  end
  puts "Mode: #{baseline ? 'HEAD baseline (expected behavioral failures)' : 'working tree'}"
  success = Dir.chdir(REPO) do
    system(File.join(JAVA_HOME, 'bin/java'), '-cp', ([classes] + jars).join(':'),
           'org.junit.runner.JUnitCore', *test_names)
  end
  abort 'Native feature fixtures failed' unless success
end
