package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.Layout;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.databinding.UpdateActityBinding;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.update.UpdateModel.InstallState;
import org.adaway.model.update.UpdateStore;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/** Real Activity observers and Android measurement; no network, installer or global font changes. */
@RunWith(AndroidJUnit4.class)
public class UpdateStatusLayoutInstrumentedTest {
    private static final String TAG = "UpdateStatusLayoutTest";
    private static final int WIDTH_DP = 360;
    private static final int HEIGHT_DP = 800;
    private static final float LARGE_FONT = 2.0f;
    private static final long DOWNLOAD_ID = 731L;

    private AdAwayApplication application;
    private UpdateModel previousModel;
    private RecordingModel model;
    private Field modelField;
    private float originalFontScale;

    @Before
    public void setUp() throws Exception {
        application = ApplicationProvider.getApplicationContext();
        originalFontScale = application.getResources().getConfiguration().fontScale;
        InstrumentedTestState.resetForPassiveRootUi(application, "set up update status layout");
        modelField = AdAwayApplication.class.getDeclaredField("updateModel");
        modelField.setAccessible(true);
        previousModel = application.getUpdateModel();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                model = new RecordingModel(application));
        modelField.set(application, model);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (modelField != null && previousModel != null) {
                modelField.set(application, previousModel);
            }
            assertEquals("Local font contexts must not change application configuration",
                    originalFontScale, application.getResources().getConfiguration().fontScale,
                    0.001f);
        } finally {
            InstrumentedTestState.resetForPassiveRootUi(application, "tear down update status layout");
        }
    }

    @Test
    public void everyInstallStateHasSeparatedGeometryAtNormalFont() throws Exception {
        assertInstallStates(1.0f);
    }

    @Test
    public void everyInstallStateHasSeparatedGeometryAtLargeFont() throws Exception {
        assertInstallStates(LARGE_FONT);
    }

    @Test
    public void byteProgressTransitionsHaveSeparatedGeometryAtNormalFont() throws Exception {
        assertByteProgress(1.0f);
    }

    @Test
    public void byteProgressTransitionsHaveSeparatedGeometryAtLargeFont() throws Exception {
        assertByteProgress(LARGE_FONT);
    }

    @Test
    public void retryGeometrySurvivesRecreationAtNormalFont() throws Exception {
        assertRecreation(1.0f);
    }

    @Test
    public void retryGeometrySurvivesRecreationAtLargeFont() throws Exception {
        assertRecreation(LARGE_FONT);
    }

    @Test
    public void prePatchRetryConstraintsFailMeasuredSeparationAtNormalFont() throws Exception {
        assertPrePatchOverlap(1.0f);
    }

    @Test
    public void prePatchRetryConstraintsFailMeasuredSeparationAtLargeFont() throws Exception {
        assertPrePatchOverlap(LARGE_FONT);
    }

    private void assertInstallStates(float fontScale) throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            scenario.onActivity(activity -> configureContent(activity, fontScale));
            // values() includes IDLE, all seven retry phases, and all three active phases.
            for (InstallState state : InstallState.values()) {
                publishState(state);
                scenario.onActivity(activity -> assertInstallState(activity, state, fontScale));
            }
            publishState(InstallState.IDLE);
            scenario.onActivity(activity -> assertInstallState(activity, InstallState.IDLE, fontScale));
        }
    }

    private void assertByteProgress(float fontScale) throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            scenario.onActivity(activity -> configureContent(activity, fontScale));
            publishState(InstallState.DOWNLOADING);
            for (long downloaded : new long[]{0L, 524288L, 1048576L}) {
                PendingDownloadStatus status = new PendingDownloadStatus(downloaded, 1048576L);
                scenario.onActivity(activity -> {
                    UpdateViewModel viewModel = new ViewModelProvider(activity).get(UpdateViewModel.class);
                    try {
                        Field tracked = UpdateViewModel.class.getDeclaredField("trackedDownload");
                        tracked.setAccessible(true);
                        ((AtomicLong) tracked.get(viewModel)).set(DOWNLOAD_ID);
                        Method post = UpdateViewModel.class.getDeclaredMethod(
                                "postProgress", long.class, DownloadStatus.class);
                        post.setAccessible(true);
                        post.invoke(viewModel, DOWNLOAD_ID, status);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError(exception);
                    }
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    ViewGroup root = contentRoot(activity);
                    TextView message = root.findViewById(R.id.progressTextView);
                    assertEquals(status.format(activity), message.getText().toString());
                    ProgressBar progress = root.findViewById(R.id.downloadProgressBar);
                    assertEquals(VISIBLE, progress.getVisibility());
                    assertFalse(progress.isIndeterminate());
                    assertEquals(INVISIBLE, root.findViewById(R.id.update_button).getVisibility());
                    measure(root);
                    assertSeparated(root);
                    logGeometry(root, "bytes=" + downloaded, fontScale);
                });
            }
            // Retain the existing download-status fixture's compatibility case without
            // treating byte completion as verified installation or opening an installer.
            CompleteDownloadStatus complete = new CompleteDownloadStatus();
            scenario.onActivity(activity -> new ViewModelProvider(activity)
                    .get(UpdateViewModel.class).getDownloadProgress().setValue(complete));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                ViewGroup root = contentRoot(activity);
                TextView message = root.findViewById(R.id.progressTextView);
                assertEquals(complete.format(activity), message.getText().toString());
                assertEquals(INVISIBLE, root.findViewById(R.id.update_button).getVisibility());
                ProgressBar progress = root.findViewById(R.id.downloadProgressBar);
                assertEquals(VISIBLE, progress.getVisibility());
                assertFalse(progress.isIndeterminate());
                measure(root);
                assertSeparated(root);
                logGeometry(root, "byte-complete compatibility", fontScale);
            });
            publishState(InstallState.VERIFYING);
            scenario.onActivity(activity -> assertInstallState(activity, InstallState.VERIFYING, fontScale));
            publishState(InstallState.INSTALLER_OPENED);
            scenario.onActivity(activity ->
                    assertInstallState(activity, InstallState.INSTALLER_OPENED, fontScale));
        }
    }

    private void assertRecreation(float fontScale) throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            scenario.onActivity(activity -> configureContent(activity, fontScale));
            publishState(InstallState.INSTALLER_OPENED);
            scenario.recreate();
            scenario.onActivity(activity -> {
                // The replacement Activity must already have received the retained real model state.
                TextView message = activity.findViewById(R.id.progressTextView);
                assertEquals(new InstallDownloadStatus(InstallState.INSTALLER_OPENED).format(activity),
                        message.getText().toString());
                configureContent(activity, fontScale);
            });
            publishState(InstallState.INSTALLER_OPENED);
            scenario.onActivity(activity ->
                    assertInstallState(activity, InstallState.INSTALLER_OPENED, fontScale));
            publishState(InstallState.IDLE);
            scenario.onActivity(activity -> assertInstallState(activity, InstallState.IDLE, fontScale));
        }
    }

    private void assertPrePatchOverlap(float fontScale) throws Exception {
        try (ActivityScenario<UpdateActivity> scenario = ActivityScenario.launch(UpdateActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup root = configureContent(activity, fontScale);
                // Restore exactly the three pre-fix XML attributes, leaving the observer untouched.
                View button = root.findViewById(R.id.update_button);
                ConstraintLayout.LayoutParams buttonParams =
                        (ConstraintLayout.LayoutParams) button.getLayoutParams();
                buttonParams.topToBottom = R.id.headerTextView;
                button.setLayoutParams(buttonParams);
                TextView message = root.findViewById(R.id.progressTextView);
                ConstraintLayout.LayoutParams textParams =
                        (ConstraintLayout.LayoutParams) message.getLayoutParams();
                textParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                message.setLayoutParams(textParams);
                message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            });
            publishState(InstallState.DOWNLOAD_FAILED);
            scenario.onActivity(activity -> {
                ViewGroup root = contentRoot(activity);
                assertEquals(VISIBLE, root.findViewById(R.id.update_button).getVisibility());
                assertEquals(GONE, root.findViewById(R.id.downloadProgressBar).getVisibility());
                TextView message = root.findViewById(R.id.progressTextView);
                assertEquals(new InstallDownloadStatus(InstallState.DOWNLOAD_FAILED).format(activity),
                        message.getText().toString());
                measure(root);
                Rect text = bounds(root, message);
                Rect button = bounds(root, root.findViewById(R.id.update_button));
                assertTrue("Negative control requires real nonempty measured text", text.height() > 0);
                assertTrue("Pre-patch failure text must actually intersect retry: " + text + " / "
                        + button, Rect.intersects(text, button));
                assertThrows("The same separation assertion must reject the original layout",
                        AssertionError.class, () -> assertStatusBeforeButton(root));
                logGeometry(root, "pre-patch DOWNLOAD_FAILED", fontScale);
            });
        }
    }

    private ViewGroup configureContent(UpdateActivity activity, float fontScale) {
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        configuration.screenWidthDp = WIDTH_DP;
        configuration.screenHeightDp = HEIGHT_DP;
        configuration.smallestScreenWidthDp = WIDTH_DP;
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        Context context = new ContextThemeWrapper(activity.createConfigurationContext(configuration),
                R.style.Theme_AdAway_NoActionBar_Red);
        assertEquals(fontScale, context.getResources().getConfiguration().fontScale, 0.001f);
        UpdateActityBinding binding = UpdateActityBinding.inflate(
                LayoutInflater.from(activity).cloneInContext(context));
        try {
            // Only inflation context changes. Existing Activity observers still render every state.
            Field field = UpdateActivity.class.getDeclaredField("binding");
            field.setAccessible(true);
            field.set(activity, binding);
            activity.setContentView(binding.getRoot());
            Method listeners = UpdateActivity.class.getDeclaredMethod("bindListeners");
            listeners.setAccessible(true);
            listeners.invoke(activity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        assertTrue("Real model-to-ViewModel observer must remain active",
                model.getInstallState().hasActiveObservers());
        return binding.getRoot();
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

    private static ViewGroup contentRoot(UpdateActivity activity) {
        return (ViewGroup) activity.findViewById(R.id.progressTextView).getParent();
    }

    private static void assertInstallState(UpdateActivity activity, InstallState state,
                                          float fontScale) {
        ViewGroup root = contentRoot(activity);
        TextView message = root.findViewById(R.id.progressTextView);
        ProgressBar progress = root.findViewById(R.id.downloadProgressBar);
        assertEquals(fontScale, message.getResources().getConfiguration().fontScale, 0.001f);
        if (state == InstallState.IDLE) {
            assertEquals(activity.getString(R.string.update_unavailable_header),
                    ((TextView) root.findViewById(R.id.headerTextView)).getText().toString());
            assertEquals("", message.getText().toString());
            assertEquals(GONE, root.findViewById(R.id.update_button).getVisibility());
            assertEquals(GONE, progress.getVisibility());
        } else {
            InstallDownloadStatus status = new InstallDownloadStatus(state);
            assertEquals(status.format(activity), message.getText().toString());
            assertEquals(status.canRetry() ? VISIBLE : INVISIBLE,
                    root.findViewById(R.id.update_button).getVisibility());
            assertEquals(status.canRetry() ? GONE : VISIBLE, progress.getVisibility());
            assertEquals(!status.canRetry(), progress.isIndeterminate());
            if (status.canRetry()) {
                assertEquals(activity.getString(status.actionLabel()),
                        ((TextView) root.findViewById(R.id.update_button)).getText().toString());
            }
        }
        measure(root);
        assertSeparated(root);
        logGeometry(root, state.name(), fontScale);
    }

    private static void measure(ViewGroup root) {
        float density = root.getResources().getDisplayMetrics().density;
        int width = Math.round(WIDTH_DP * density);
        int height = Math.round(HEIGHT_DP * density);
        root.forceLayout();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void assertSeparated(ViewGroup root) {
        TextView message = root.findViewById(R.id.progressTextView);
        View button = root.findViewById(R.id.update_button);
        View progress = root.findViewById(R.id.downloadProgressBar);
        View changelog = root.findViewById(R.id.changelogScrollView);
        Rect scrollBounds = bounds(root, changelog);
        assertTrue("Changelog must retain positive visible height", scrollBounds.height() > 0);
        assertInside(root, scrollBounds);
        if (message.length() > 0) {
            assertCompleteText(root, message);
            Rect textBounds = bounds(root, message);
            assertTrue("Changelog must follow the whole status message",
                    scrollBounds.top >= textBounds.bottom);
            if (progress.getVisibility() == VISIBLE) {
                assertInside(root, bounds(root, progress));
                assertTrue("Status must follow the visible progress bar",
                        textBounds.top >= bounds(root, progress).bottom);
            }
            if (button.getVisibility() == VISIBLE) {
                assertStatusBeforeButton(root);
            }
        }
        if (button.getVisibility() == VISIBLE) {
            assertCompleteText(root, (TextView) button);
            assertTrue("Changelog must follow the recovery action",
                    scrollBounds.top >= bounds(root, button).bottom);
        }
    }

    private static void assertStatusBeforeButton(ViewGroup root) {
        Rect message = bounds(root, root.findViewById(R.id.progressTextView));
        Rect button = bounds(root, root.findViewById(R.id.update_button));
        assertTrue("Recovery action must follow the complete message: " + message + " / " + button,
                button.top >= message.bottom);
    }

    private static void assertCompleteText(ViewGroup root, TextView view) {
        Layout layout = view.getLayout();
        assertNotNull("Requires actual Android text measurement", layout);
        assertTrue(layout.getLineCount() > 0);
        assertEquals(view.length(), layout.getLineEnd(layout.getLineCount() - 1));
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(0, layout.getEllipsisCount(line));
            assertTrue("Measured line must fit the text viewport", layout.getLineMax(line)
                    <= view.getWidth() - view.getCompoundPaddingLeft()
                            - view.getCompoundPaddingRight() + 1);
        }
        assertTrue("All lines must fit the measured view height", view.getHeight()
                >= layout.getHeight() + view.getCompoundPaddingTop() + view.getCompoundPaddingBottom());
        Rect rect = bounds(root, view);
        assertTrue("Text must occupy a positive measured rectangle", rect.width() > 0 && rect.height() > 0);
        assertInside(root, rect);
    }

    private static void assertInside(ViewGroup root, Rect rect) {
        assertTrue("View must remain within the 360x800dp viewport: " + rect,
                rect.left >= 0 && rect.top >= 0
                        && rect.right <= root.getWidth() && rect.bottom <= root.getHeight());
    }

    private static Rect bounds(ViewGroup root, View view) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private static void logGeometry(ViewGroup root, String state, float fontScale) {
        Log.i(TAG, state + " fontScale=" + fontScale
                + " message=" + bounds(root, root.findViewById(R.id.progressTextView))
                + " button=" + bounds(root, root.findViewById(R.id.update_button))
                + " progress=" + bounds(root, root.findViewById(R.id.downloadProgressBar))
                + " changelog=" + bounds(root, root.findViewById(R.id.changelogScrollView)));
    }

    private static final class RecordingModel extends UpdateModel {
        RecordingModel(Context context) { super(context); }
        @Override public UpdateStore getStore() { return UpdateStore.UNKNOWN; }
        @Override public void prepareManifestRefresh() {
            throw new AssertionError("Layout replay must not prepare network work");
        }
        @Override public void refreshManifest() {
            throw new AssertionError("Layout replay must not fetch a manifest");
        }
        @Override public long update() {
            throw new AssertionError("Layout replay must not download an APK");
        }
        @Override public void resumePendingInstall() {
            throw new AssertionError("Layout replay must not launch an installer");
        }
    }
}
