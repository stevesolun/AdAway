package org.adaway.ui.hosts;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.ThemeHelper;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple "schedule manager" screen.
 * Lets user view and edit global schedule, per-filter-set schedules, and per-source schedules.
 */
public class SchedulesActivity extends AppCompatActivity {

    private static final String PREFS_SOURCE_SCHEDULES = "source_schedules";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_url_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_url_";
    private static final String KEY_HOUR_PREFIX = "hour_url_";
    private static final String KEY_MINUTE_PREFIX = "minute_url_";
    private static final String KEY_WEEKDAY_PREFIX = "weekday_url_";

    private static final int SCHEDULE_OFF = 0;
    private static final int SCHEDULE_DAILY = 1;
    private static final int SCHEDULE_WEEKLY = 2;

    private TextView globalStatus;
    private TextView filterSetSummary;
    private TextView sourceSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.schedules_activity);

        MaterialToolbar toolbar = findViewById(R.id.schedulesToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

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
    protected void onResume() {
        super.onResume();
        refreshSummary();
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
                : ("Scheduled sets: " + scheduledSets));

        // Source schedules summary
        // Room queries must not run on the main thread.
        AppExecutors.getInstance().diskIO().execute(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_SOURCE_SCHEDULES, MODE_PRIVATE);
            HostsSourceDao dao = AppDatabase.getInstance(this).hostsSourceDao();
            int scheduledSources = 0;
            for (HostsSource s : dao.getAll()) {
                if (s.getId() == HostsSource.USER_SOURCE_ID) continue;
                int schedule = prefs.getInt(KEY_SCHEDULE_PREFIX + s.getUrl(), SCHEDULE_OFF);
                if (schedule != SCHEDULE_OFF) scheduledSources++;
            }
            final int finalScheduledSources = scheduledSources;
            AppExecutors.getInstance().mainThread().execute(() -> sourceSummary.setText(finalScheduledSources == 0
                    ? getString(R.string.schedule_off)
                    : ("Scheduled sources: " + finalScheduledSources)));
        });
    }

    private void editGlobalSchedule() {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.schedule_global_title)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        FilterSetStore.setGlobalEnabled(this, false);
                        FilterSetStore.setGlobalSchedule(this, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
                        FilterSetUpdateService.enable(this);
                        refreshSummary();
                        return;
                    }
                    FilterSetStore.setGlobalEnabled(this, true);
                    if (which == 1) {
                        pickTime((h, m) -> {
                            FilterSetStore.setGlobalSchedule(this, FilterSetStore.SCHEDULE_DAILY, 1, h, m);
                            FilterSetUpdateService.enable(this);
                            refreshSummary();
                        });
                    } else {
                        pickDayOfWeek(dow -> pickTime((h, m) -> {
                            FilterSetStore.setGlobalSchedule(this, FilterSetStore.SCHEDULE_WEEKLY, dow, h, m);
                            FilterSetUpdateService.enable(this);
                            refreshSummary();
                        }));
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
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
        CharSequence[] options = new CharSequence[]{
                getString(R.string.filter_set_schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        FilterSetStore.setSchedule(this, name, FilterSetStore.SCHEDULE_OFF);
                        FilterSetUpdateService.enable(this);
                        refreshSummary();
                        return;
                    }
                    if (which == 1) {
                        pickTime((h, m) -> {
                            FilterSetStore.setSchedule(this, name, FilterSetStore.SCHEDULE_DAILY, 1, h, m);
                            FilterSetUpdateService.enable(this);
                            refreshSummary();
                        });
                        return;
                    }
                    pickDayOfWeek(dow -> pickTime((h, m) -> {
                        FilterSetStore.setSchedule(this, name, FilterSetStore.SCHEDULE_WEEKLY, dow, h, m);
                        FilterSetUpdateService.enable(this);
                        refreshSummary();
                    }));
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void editSourceSchedules() {
        // Room query must not run on main thread.
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostsSourceDao dao = AppDatabase.getInstance(this).hostsSourceDao();
            List<HostsSource> sources = dao.getAll();
            List<HostsSource> pickable = new ArrayList<>();
            for (HostsSource s : sources) {
                if (s.getId() == HostsSource.USER_SOURCE_ID) continue;
                pickable.add(s);
            }
            if (pickable.isEmpty()) return;
            String[] labels = new String[pickable.size()];
            for (int i = 0; i < pickable.size(); i++) labels[i] = pickable.get(i).getLabel();

            AppExecutors.getInstance().mainThread().execute(() -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.source_edit_auto_update)
                    .setItems(labels, (d, which) -> promptScheduleForUrl(pickable.get(which).getUrl()))
                    .setNegativeButton(R.string.button_cancel, null)
                    .show());
        });
    }

    private void promptScheduleForUrl(@NonNull String url) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.filter_set_schedule_off),
                getString(R.string.filter_set_schedule_daily),
                getString(R.string.filter_set_schedule_weekly)
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(url)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        saveSourceSchedule(url, SCHEDULE_OFF, 1, 3, 0);
                        FilterSetUpdateService.enable(this);
                        refreshSummary();
                        return;
                    }
                    if (which == 1) {
                        pickTime((h, m) -> {
                            saveSourceSchedule(url, SCHEDULE_DAILY, 1, h, m);
                            FilterSetUpdateService.enable(this);
                            refreshSummary();
                        });
                        return;
                    }
                    pickDayOfWeek(dow -> pickTime((h, m) -> {
                        saveSourceSchedule(url, SCHEDULE_WEEKLY, dow, h, m);
                        FilterSetUpdateService.enable(this);
                        refreshSummary();
                    }));
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
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

    private interface TimePicked {
        void onPicked(int hour24, int minute);
    }

    private void pickTime(@NonNull TimePicked picked) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int hour = c.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = c.get(java.util.Calendar.MINUTE);
        new android.app.TimePickerDialog(
                this,
                (view, hourOfDay, minuteOfHour) -> picked.onPicked(hourOfDay, minuteOfHour),
                hour,
                minute,
                DateFormat.is24HourFormat(this)
        ).show();
    }

    private interface DayPicked {
        void onPicked(int isoDay);
    }

    private void pickDayOfWeek(@NonNull DayPicked picked) {
        String[] days = new String[]{
                "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.filter_set_schedule_pick_day)
                .setItems(days, (d, which) -> picked.onPicked(which + 1))
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private static String dayName(int iso) {
        switch (iso) {
            case 2: return "Tuesday";
            case 3: return "Wednesday";
            case 4: return "Thursday";
            case 5: return "Friday";
            case 6: return "Saturday";
            case 7: return "Sunday";
            case 1:
            default: return "Monday";
        }
    }

    private String formatTime(int hour24, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour24, minute);
    }
}

