package org.adaway.ui;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.onboarding.OnboardingActivity;
import org.adaway.ui.update.UpdateActivity;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Connected UX smoke matrix for the high-value app shells.
 *
 * <p>The full device matrix is driven by adb settings in the runner (font scale, locale, and
 * layout direction). This test stays dependency-light: it captures screenshots and catches basic
 * accessibility regressions that are easy to miss in layout-only review.</p>
 */
@RunWith(AndroidJUnit4.class)
public class UxDeviceMatrixTest {
    private static final long IDLE_WAIT_MS = 750L;
    private static final int MIN_TOUCH_TARGET_DP = 48;

    private Context context;
    private File screenshotDir;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        PreferenceHelper.setAbBlockMethod(this.context, AdBlockMethod.ROOT);
        this.screenshotDir = new File(this.context.getExternalFilesDir(null), "ux-matrix");
        deleteContents(this.screenshotDir);
        assertTrue(this.screenshotDir.mkdirs() || this.screenshotDir.isDirectory());
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
        }
    }

    @Test
    public void keyScreens_haveScreenshotsLabelsAndTouchTargets() throws IOException {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            captureAfterIdle(scenario, "home");
            navigateAndCapture(scenario, R.id.nav_discover, "discover");
            navigateAndCapture(scenario, R.id.nav_more, "more");
            captureDomainChecker(scenario);
        }

        captureActivity(new Intent(this.context, OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_SKIP_AUTO_DETECT, true), "onboarding");
        captureActivity(HostsSourcesActivity.class, "sources");
        captureActivity(UpdateActivity.class, "update");

        System.out.println("UxDeviceMatrix screenshots=" + this.screenshotDir.getAbsolutePath());
    }

    private void navigateAndCapture(ActivityScenario<HomeActivity> scenario, int navId, String name)
            throws IOException {
        scenario.onActivity(activity -> activity.navigateTo(navId));
        captureAfterIdle(scenario, name);
    }

    private void captureDomainChecker(ActivityScenario<HomeActivity> scenario) throws IOException {
        scenario.onActivity(activity -> {
            View card = activity.findViewById(R.id.domainCheckerCard);
            assertTrue("Domain checker card must be visible in More.", card != null && card.isShown());
            card.performClick();
        });
        captureAfterIdle(scenario, "domain_checker", activity -> {
            ScrollView scrollView = activity.findViewById(R.id.domainCheckerScrollView);
            assertTrue("Domain checker scroll view must be visible.",
                    scrollView != null && scrollView.isShown());
            assertTrue("Domain checker must open at the top, scrollY=" + scrollView.getScrollY(),
                    scrollView.getScrollY() == 0);
        });
    }

    private <T extends Activity> void captureActivity(Class<T> activityClass, String name)
            throws IOException {
        try (ActivityScenario<T> scenario = ActivityScenario.launch(activityClass)) {
            captureAfterIdle(scenario, name);
        }
    }

    private void captureActivity(Intent intent, String name) throws IOException {
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
            captureAfterIdle(scenario, name);
        }
    }

    private <T extends Activity> void captureAfterIdle(ActivityScenario<T> scenario, String name)
            throws IOException {
        captureAfterIdle(scenario, name, activity -> { });
    }

    private <T extends Activity> void captureAfterIdle(ActivityScenario<T> scenario, String name,
                                                       Consumer<T> afterIdleAssertion)
            throws IOException {
        drainMainThread();
        SystemClock.sleep(IDLE_WAIT_MS);
        drainMainThread();

        final List<String> failures = new ArrayList<>();
        final IOException[] writeFailure = new IOException[1];
        scenario.onActivity(activity -> {
            afterIdleAssertion.accept(activity);
            View root = activity.getWindow().getDecorView().getRootView();
            assertTrue(name + " root has no width", root.getWidth() > 0);
            assertTrue(name + " root has no height", root.getHeight() > 0);
            auditView(name, root, failures);
            try {
                writeScreenshot(name, root);
            } catch (IOException exception) {
                writeFailure[0] = exception;
            }
        });
        if (writeFailure[0] != null) {
            throw writeFailure[0];
        }
        assertTrue("UX accessibility failures for " + name + ":\n"
                + TextUtils.join("\n", failures), failures.isEmpty());
    }

    private void drainMainThread() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
    }

    private void auditView(String screenName, View view, List<String> failures) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() == 0f) {
            return;
        }

        if (isInteractiveControl(view) && view.isEnabled()) {
            if (!hasAccessibleName(view)) {
                failures.add(screenName + ": unlabeled control " + describe(view));
            }
            float density = view.getResources().getDisplayMetrics().density;
            int minPx = Math.round(MIN_TOUCH_TARGET_DP * density);
            if (!isSystemOverflowMenuButton(view)
                    && view.getWidth() > 0 && view.getHeight() > 0
                    && (view.getWidth() + 1 < minPx || view.getHeight() + 1 < minPx)) {
                failures.add(screenName + ": small touch target " + describe(view)
                        + " size=" + view.getWidth() + "x" + view.getHeight());
            }
        }
        if (view instanceof TextView && view.isShown() && view.getWidth() > 0
                && view.getHeight() > 0) {
            auditTextFits(screenName, (TextView) view, failures);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                auditView(screenName, group.getChildAt(i), failures);
            }
        }
    }

    private boolean isInteractiveControl(View view) {
        return view.isClickable()
                || view.isLongClickable()
                || view instanceof EditText
                || view instanceof Spinner;
    }

    private boolean isSystemOverflowMenuButton(View view) {
        return "OverflowMenuButton".equals(view.getClass().getSimpleName());
    }

    private boolean hasAccessibleName(View view) {
        if (!TextUtils.isEmpty(view.getContentDescription())) {
            return true;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            return !TextUtils.isEmpty(textView.getText()) || !TextUtils.isEmpty(textView.getHint());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasAccessibleName(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void auditTextFits(String screenName, TextView textView, List<String> failures) {
        if (TextUtils.isEmpty(textView.getText()) || textView.getLayout() == null) {
            return;
        }
        int lineCount = textView.getLayout().getLineCount();
        int availableTextWidth = textView.getWidth()
                - textView.getCompoundPaddingLeft()
                - textView.getCompoundPaddingRight();
        for (int line = 0; line < lineCount; line++) {
            if (textView.getLayout().getEllipsisCount(line) > 0) {
                failures.add(screenName + ": ellipsized text " + describe(textView)
                        + " text=" + summarize(textView.getText()));
                return;
            }
            int lineWidth = (int) Math.ceil(textView.getLayout().getLineRight(line)
                    - textView.getLayout().getLineLeft(line));
            if (availableTextWidth > 0 && lineWidth > availableTextWidth) {
                failures.add(screenName + ": horizontally clipped text " + describe(textView)
                        + " lineWidth=" + lineWidth
                        + " availableWidth=" + availableTextWidth
                        + " text=" + summarize(textView.getText()));
                return;
            }
        }

        int compoundPaddingHeight = textView.getCompoundPaddingTop()
                + textView.getCompoundPaddingBottom();
        int availableTextHeight = textView.getHeight() - compoundPaddingHeight;
        if (availableTextHeight > 0 && textView.getLayout().getHeight() > availableTextHeight) {
            failures.add(screenName + ": clipped text " + describe(textView)
                    + " layoutHeight=" + textView.getLayout().getHeight()
                    + " availableHeight=" + availableTextHeight
                    + " text=" + summarize(textView.getText()));
        }
    }

    private String summarize(CharSequence text) {
        String value = text.toString().replace('\n', ' ').trim();
        return value.length() <= 80 ? value : value.substring(0, 77) + "...";
    }

    private String describe(View view) {
        String idName = "no-id";
        if (view.getId() != View.NO_ID) {
            try {
                idName = view.getResources().getResourceEntryName(view.getId());
            } catch (RuntimeException ignored) {
                idName = "id-" + view.getId();
            }
        }
        return view.getClass().getSimpleName() + "(" + idName + ")";
    }

    private void writeScreenshot(String name, View root) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        root.draw(canvas);
        File target = new File(this.screenshotDir,
                String.format(Locale.ROOT, "%s.png", name));
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            assertTrue("Failed to encode screenshot " + target,
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream));
        } finally {
            bitmap.recycle();
        }
        assertTrue("Screenshot was empty: " + target, target.length() > 0);
    }

    private static void deleteContents(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteContents(file);
            }
            assertTrue(file.delete());
        }
    }
}
