package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButton;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.update.UpdateModel.InstallState;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Isolated UI/model-state wiring; never opens settings, an installer, or a network request. */
@RunWith(AndroidJUnit4.class)
public class UpdateRetryUiInstrumentedTest {
    private AdAwayApplication application;
    private UpdateModel previousModel;
    private RecordingModel model;
    private Field modelField;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(application, "set up update retry UI");
        modelField = AdAwayApplication.class.getDeclaredField("updateModel");
        modelField.setAccessible(true);
        previousModel = application.getUpdateModel();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                model = new RecordingModel(application));
        modelField.set(application, model);
    }

    @After
    public void tearDown() throws Exception {
        if (modelField != null && previousModel != null) {
            modelField.set(application, previousModel);
        }
        InstrumentedTestState.resetForPassiveRootUi(application, "tear down update retry UI");
    }

    @Test
    public void allRejectionAndUnconfirmedInstallerStatesKeepRetryVisible() throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            for (InstallState state : new InstallState[]{InstallState.DOWNLOAD_FAILED,
                    InstallState.VERIFICATION_FAILED, InstallState.PERMISSION_REQUIRED,
                    InstallState.INSTALL_FAILED, InstallState.INSTALLER_OPENED}) {
                publishState(state);
                scenario.onActivity(activity -> assertState(activity, state, VISIBLE, GONE));
            }
        }
    }

    @Test
    public void verificationAndCheckingDoNotOfferConcurrentRetry() throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            for (InstallState state : new InstallState[]{InstallState.DOWNLOADING,
                    InstallState.VERIFYING, InstallState.CHECKING}) {
                publishState(state);
                scenario.onActivity(activity -> {
                    assertState(activity, state, INVISIBLE, VISIBLE);
                    assertTrue(((ProgressBar) activity.findViewById(R.id.downloadProgressBar))
                            .isIndeterminate());
                });
            }
        }
    }

    @Test
    public void expiredAuthorizationOffersRefetchWithoutCachedInstallOrDownload() throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            publishState(InstallState.METADATA_EXPIRED);
            scenario.onActivity(activity -> {
                assertState(activity, InstallState.METADATA_EXPIRED, VISIBLE, GONE);
                MaterialButton button = activity.findViewById(R.id.update_button);
                assertEquals(activity.getString(R.string.update_check_again_button),
                        button.getText().toString());
                button.performClick();
            });
            assertTrue(model.refreshed.await(5, TimeUnit.SECONDS));
            assertEquals(1, model.refreshPreparations);
            assertEquals(0, model.downloadAttempts);
        }
    }

    @Test
    public void retrySurvivesRecreationAndResumeWithoutAutomaticInstallerLaunch() throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            publishState(InstallState.INSTALLER_OPENED);
            scenario.recreate();
            scenario.onActivity(activity ->
                    assertState(activity, InstallState.INSTALLER_OPENED, VISIBLE, GONE));
            assertEquals(0, model.downloadAttempts);
            assertEquals(0, model.resumeAttempts);
        }
    }

    @Test
    public void queuedByteProgressCannotOverwriteVerificationFailure() throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            publishState(InstallState.VERIFICATION_FAILED);
            scenario.onActivity(activity -> {
                UpdateViewModel viewModel = new ViewModelProvider(activity).get(UpdateViewModel.class);
                try {
                    Field tracked = UpdateViewModel.class.getDeclaredField("trackedDownload");
                    tracked.setAccessible(true);
                    ((AtomicLong) tracked.get(viewModel)).set(42L);
                    Method post = UpdateViewModel.class.getDeclaredMethod(
                            "postProgress", long.class, DownloadStatus.class);
                    post.setAccessible(true);
                    post.invoke(viewModel, 42L, new PendingDownloadStatus(1L, 2L));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity ->
                    assertState(activity, InstallState.VERIFICATION_FAILED, VISIBLE, GONE));
        }
    }

    private void publishState(InstallState state) throws Exception {
        Method setter = UpdateModel.class.getDeclaredMethod("setInstallState", InstallState.class);
        setter.setAccessible(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                setter.invoke(model, state);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void assertState(UpdateActivity activity, InstallState state, int button, int progress) {
        assertEquals(button, activity.findViewById(R.id.update_button).getVisibility());
        assertEquals(progress, activity.findViewById(R.id.downloadProgressBar).getVisibility());
        assertEquals(new InstallDownloadStatus(state).format(activity),
                ((TextView) activity.findViewById(R.id.progressTextView)).getText().toString());
    }

    private static final class RecordingModel extends UpdateModel {
        final CountDownLatch refreshed = new CountDownLatch(1);
        int refreshPreparations;
        int downloadAttempts;
        int resumeAttempts;

        RecordingModel(Context context) { super(context); }
        @Override public void prepareManifestRefresh() { refreshPreparations++; }
        @Override public void refreshManifest() { refreshed.countDown(); }
        @Override public long update() { downloadAttempts++; return -1; }
        @Override public void resumePendingInstall() { resumeAttempts++; }
    }
}
