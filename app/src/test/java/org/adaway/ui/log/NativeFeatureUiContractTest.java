package org.adaway.ui.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Source guards complement the executable host fixtures; they are not rendered UI tests. */
public class NativeFeatureUiContractTest {
    @Test
    public void rootRecordControlIsDisabledAndHasNoListener() throws Exception {
        String source = read("ui/log/LogActivity.java");
        assertTrue(source.contains(
                "boolean loggingAvailable = !this.mViewModel.isDnsRequestLoggingUnavailable();"));
        assertTrue(source.contains("toggleLogRecording.setEnabled(loggingAvailable);"));
        assertTrue(source.contains("if (loggingAvailable) {\n"
                + "            this.binding.toggleLogRecording.setOnClickListener"));
        assertTrue(source.contains("toggleLogRecording.setContentDescription(\n"
                + "                    getString(R.string.log_root_recording_unavailable)"));
    }

    @Test
    public void recordingInitializationAndToggleRespectAvailabilityAndBackendTruth() throws Exception {
        String source = read("ui/log/LogViewModel.java");
        assertTrue(source.contains("!isDnsRequestLoggingUnavailable() && "
                + "this.adBlockModel.isRecordingLogs()"));
        assertTrue(source.contains("public void toggleRecording() {\n"
                + "        if (isDnsRequestLoggingUnavailable()) {\n"
                + "            this.recording.postValue(false);\n"
                + "            return;\n"));
        assertTrue(source.contains("this.recording.postValue(this.adBlockModel.isRecordingLogs());"));
    }

    @Test
    public void unavailablePreferencesDoNotRequestCleanupOrQueryProcessState() throws Exception {
        String source = read("ui/prefs/PrefsRootFragment.java");
        String configure = source.substring(source.indexOf(
                "private void configureWebServerAvailability()"), source.indexOf(
                "private void updateWebServerState()"));
        assertFalse(configure.contains("stopWebServer("));
        assertFalse(configure.contains("cleanupLegacy"));
        assertTrue(configure.contains("webServerEnabledPref.setChecked(false)"));
        assertTrue(configure.contains("webServerEnabledPref.setEnabled(false)"));
        assertTrue(configure.contains("webServerTest.setEnabled(false)"));
        assertTrue(configure.contains("webServerIcon.setEnabled(false)"));
        assertTrue(configure.contains("webServerIcon.setChecked(false)"));
        String listener = source.substring(source.indexOf("public void onSharedPreferenceChanged"),
                source.indexOf("private void registerForOpenHostActivity"));
        assertTrue(listener.contains("if (context == null || !isWebServerAvailable(context)) {\n"
                + "            return;\n"));
        assertFalse(source.contains("cleanupLegacy"));
    }

    @Test
    public void legacyCleanupStaysInBackgroundRootInitialization() throws Exception {
        String source = read("model/root/RootModel.java");
        assertTrue(source.contains("Executor executor = AppExecutors.getInstance().diskIO();"));
        assertTrue(source.contains("executor.execute(this::cleanupLegacyNativeProcesses);"));
        assertTrue(source.contains("private void cleanupLegacyNativeProcesses() {\n"
                + "        WebServerUtils.cleanupLegacyWebServer();\n"
                + "        TcpdumpUtils.cleanupLegacyTcpdump();"));
        assertFalse(source.contains("WebServerUtils.startWebServer"));
        assertTrue(read("model/root/TcpdumpUtils.java").contains("TCPDUMP_CAPTURE_ENABLED = false"));
    }

    private static String read(String path) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return new String(Files.readAllBytes(repo.resolve(
                "app/src/main/java/org/adaway/" + path)), StandardCharsets.UTF_8);
    }
}
