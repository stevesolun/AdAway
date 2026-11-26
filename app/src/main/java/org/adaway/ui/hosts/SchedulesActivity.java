package org.adaway.ui.hosts;

import static org.adaway.util.Constants.PREFS_SOURCE_SCHEDULES;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.util.AppExecutors;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple "schedule manager" screen.
 * Lets user view and edit global schedule, per-filter-set schedules, and per-source schedules.
 */
public class SchedulesActivity extends AppCompatActivity {

    private static final String KEY_SCHEDULE_PREFIX = "schedule_url_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_url_";
    private static final String KEY_HOUR_PREFIX = "hour_url_";
    private static final String KEY_MINUTE_PREFIX = "minute_url_";
    private static final String KEY_WEEKDAY_PREFIX = "weekday_url_";

    private static final int SCHEDULE_OFF = 0;

    private TextView globalStatus;
    private TextView filterSetSummary;
    private TextView sourceSummary;
    private int mScreenGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.schedules_activity);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.menu_manage_schedules);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        globalStatus = findViewById(R.id.globalStatus);
        filterSetSummary = findViewById(R.id.filterSetSchedulesSummary);
        sourceSummary = findViewById(R.id.sourceSchedulesSummary);

        MaterialButton globalEdit = findViewById(R.id.globalEditButton);
        globalEdit.setOnClickListener(v -> editGlobalSchedule());

        MaterialButton editSetSchedules = findViewById(R.id.editFilterSetSchedulesButton);
        editSetSchedules.setOnClickListener(v -> editFilterSetSchedules());

        MaterialButton editSourceSchedules = findViewById(R.id.editSourceSchedulesButton);
        editSourceSchedules.setOnClickListener(v -> editSourceSchedules());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    @Override
    protected void onPause() {
        // Work queued for a screen being left must not reopen its picker on return.
        mScreenGeneration++;
        super.onPause();
    }

    private boolean canDeliverResult(int generation) {
        return generation == mScreenGeneration && !isFinishing() && !isDestroyed()
                && getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }

    private void refreshSummary() {
        // Global schedule summary
        int gType = FilterSetStore.getGlobalSchedule(this);
        boolean gEnabled = FilterSetStore.isGlobalScheduleEnabled(this);
        if (!gEnabled || gType == FilterSetStore.SCHEDULE_OFF) {
            globalStatus.setText(getString(R.string.schedule_off));
        } else if (gType == FilterSetStore.SCHEDULE_DAILY) {
            String t = formatTime(FilterSetStore.getGlobalHour(this), FilterSetStore.getGlobalMinute(this));
            globalStatus.setText(getString(R.string.schedule_daily_at, t));
        } else {
            String day = dayName(FilterSetStore.getGlobalWeekdayIso(this));
            String t = formatTime(FilterSetStore.getGlobalHour(this), FilterSetStore.getGlobalMinute(this));
            globalStatus.setText(getString(R.string.schedule_weekly_at, day, t));
        }

        // Filter set schedules summary
        java.util.Set<String> setNames = FilterSetStore.getSetNames(this);
        int scheduledSets = 0;
        for (String n : setNames) {
            if (FilterSetStore.getSchedule(this, n) != FilterSetStore.SCHEDULE_OFF) scheduledSets++;
        }
        filterSetSummary.setText(scheduledSets == 0
                ? getString(R.string.schedule_off)
                : getResources().getQuantityString(
                        R.plurals.schedule_filter_sets_count, scheduledSets, scheduledSets));

        // Source schedules summary
        // Room queries must not run on the main thread.
        Context appContext = getApplicationContext();
        int generation = mScreenGeneration;
        AppExecutors.getInstance().diskIO().execute(() -> {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences(
                    PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
            HostsSourceDao dao = AppDatabase.getInstance(appContext).hostsSourceDao();
            int scheduledSources = 0;
            for (HostsSource s : dao.getAll()) {
                if (s.getId() == HostsSource.USER_SOURCE_ID) continue;
                int schedule = prefs.getInt(KEY_SCHEDULE_PREFIX + s.getUrl(), SCHEDULE_OFF);
                if (schedule != SCHEDULE_OFF) scheduledSources++;
            }
            final int finalScheduledSources = scheduledSources;
            AppExecutors.getInstance().mainThread().execute(() -> {
                if (!canDeliverResult(generation)) return;
                sourceSummary.setText(finalScheduledSources == 0
                        ? getString(R.string.schedule_off)
                        : getResources().getQuantityString(
                                R.plurals.schedule_sources_count,
                                finalScheduledSources,
                                finalScheduledSources));
            });
        });
    }

    private void editGlobalSchedule() {
        SchedulePicker picker = new SchedulePicker(FilterSetStore.getGlobalSchedule(this),
                FilterSetStore.getGlobalWeekdayIso(this), FilterSetStore.getGlobalHour(this),
                FilterSetStore.getGlobalMinute(this));
        picker.show(this, getString(R.string.schedule_global_title), R.string.schedule_off,
                FilterSetStore.isGlobalScheduleEnabled(this), (cadence, day, hour, minute, label) -> {
                    FilterSetStore.setGlobalSchedule(this, cadence, day, hour, minute);
                    FilterSetUpdateService.syncPreferences(this);
                    refreshSummary();
                });
    }

    private void editFilterSetSchedules() {
        List<String> names = new ArrayList<>(FilterSetStore.getSetNames(this));
        if (names.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.filter_set_none)
                    .setPositiveButton(R.string.button_close, null)
                    .show();
            return;
        }
        String[] arr = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.menu_schedule_filter_set)
                .setItems(arr, (d, which) -> promptScheduleForSet(arr[which]))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void promptScheduleForSet(@NonNull String name) {
        SchedulePicker.forSet(this, name).show(this, name, R.string.filter_set_schedule_off,
                true, (cadence, day, hour, minute, label) -> {
                    if (cadence == SCHEDULE_OFF) {
                        FilterSetStore.setSchedule(this, name, cadence);
                    } else {
                        FilterSetStore.setSchedule(this, name, cadence, day, hour, minute);
                    }
                    FilterSetUpdateService.syncPreferences(this);
                    refreshSummary();
                });
    }

    private void editSourceSchedules() {
        // Room query must not run on main thread.
        Context appContext = getApplicationContext();
        int generation = mScreenGeneration;
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostsSourceDao dao = AppDatabase.getInstance(appContext).hostsSourceDao();
            List<HostsSource> sources = dao.getAll();
            List<HostsSource> pickable = new ArrayList<>();
            for (HostsSource s : sources) {
                if (s.getId() == HostsSource.USER_SOURCE_ID) continue;
                pickable.add(s);
            }
            if (pickable.isEmpty()) return;
            String[] labels = new String[pickable.size()];
            for (int i = 0; i < pickable.size(); i++) labels[i] = pickable.get(i).getLabel();

            AppExecutors.getInstance().mainThread().execute(() -> {
                if (!canDeliverResult(generation)) return;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.source_edit_auto_update)
                        .setItems(labels,
                                (d, which) -> promptScheduleForUrl(pickable.get(which).getUrl()))
                        .setNegativeButton(R.string.button_cancel, null)
                        .show();
            });
        });
    }

    private void promptScheduleForUrl(@NonNull String url) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
        SchedulePicker picker = new SchedulePicker(prefs.getInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF),
                prefs.getInt(KEY_WEEKDAY_PREFIX + url, 1), prefs.getInt(KEY_HOUR_PREFIX + url, 3),
                prefs.getInt(KEY_MINUTE_PREFIX + url, 0));
        picker.show(this, url, R.string.filter_set_schedule_off, true,
                (cadence, day, hour, minute, label) -> {
                    saveSourceSchedule(url, cadence, day, hour, minute);
                    FilterSetUpdateService.syncPreferences(this);
                    refreshSummary();
                });
    }

    private void saveSourceSchedule(String url, int schedule, int weekdayIso, int hour24, int minute) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
        android.content.SharedPreferences.Editor e = prefs.edit();
        e.putInt(KEY_SCHEDULE_PREFIX + url, schedule);
        e.putInt(KEY_WEEKDAY_PREFIX + url, Math.max(1, Math.min(7, weekdayIso)));
        e.putInt(KEY_HOUR_PREFIX + url, Math.max(0, Math.min(23, hour24)));
        e.putInt(KEY_MINUTE_PREFIX + url, Math.max(0, Math.min(59, minute)));
        e.remove(KEY_LAST_RUN_PREFIX + url);
        e.apply();
    }

    private String dayName(int iso) {
        int clamped = Math.max(1, Math.min(7, iso));
        return DayOfWeek.of(clamped).getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    private String formatTime(int hour24, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour24, minute);
    }
}
