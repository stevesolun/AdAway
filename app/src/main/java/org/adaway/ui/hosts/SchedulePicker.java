package org.adaway.ui.hosts;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.adaway.R;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

/** The values shown by one edit; the store is only called after the final choice. */
record SchedulePicker(int cadence, int weekday, int hour, int minute) {
    static SchedulePicker forSet(Context context, String name) {
        return new SchedulePicker(FilterSetStore.getSchedule(context, name),
                FilterSetStore.getScheduleWeekdayIso(context, name),
                FilterSetStore.getScheduleHour(context, name),
                FilterSetStore.getScheduleMinute(context, name));
    }

    static SchedulePicker unsaved() {
        return new SchedulePicker(-1, 1, 3, 0);
    }

    interface OnChange {
        void save(int cadence, int weekday, int hour, int minute, CharSequence label);
    }

    void show(Context context, CharSequence title, @StringRes int offLabel,
              boolean enabled, OnChange change) {
        String[] choices = {context.getString(offLabel),
                context.getString(R.string.filter_set_schedule_daily),
                context.getString(R.string.filter_set_schedule_weekly)};
        int checked = enabled ? cadence : FilterSetStore.SCHEDULE_OFF;
        new AlertDialog.Builder(context).setTitle(title)
                .setSingleChoiceItems(choices, checked, (dialog, next) -> {
                    dialog.dismiss();
                    if (next == FilterSetStore.SCHEDULE_OFF) {
                        if (checked != next) change.save(next, 1, 3, 0, choices[next]);
                    } else if (next == FilterSetStore.SCHEDULE_DAILY) {
                        pickTime(context, checked, next, weekday, choices[next], change);
                    } else {
                        String[] days = new String[7];
                        for (DayOfWeek day : DayOfWeek.values()) {
                            days[day.getValue() - 1] = day.getDisplayName(TextStyle.FULL,
                                    Locale.getDefault());
                        }
                        new AlertDialog.Builder(context).setTitle(R.string.filter_set_schedule_pick_day)
                                .setSingleChoiceItems(days, cadence == FilterSetStore.SCHEDULE_WEEKLY
                                        ? weekday - 1 : -1, (dayDialog, day) -> {
                                    dayDialog.dismiss();
                                    pickTime(context, checked, next, day + 1, choices[next], change);
                                })
                                .setNegativeButton(R.string.button_cancel, null).show();
                    }
                })
                .setNegativeButton(R.string.button_cancel, null).show();
    }

    private void pickTime(Context context, int checked, int next, int day, String label,
                          OnChange change) {
        boolean scheduled = cadence == FilterSetStore.SCHEDULE_DAILY
                || cadence == FilterSetStore.SCHEDULE_WEEKLY;
        LocalTime now = LocalTime.now();
        new TimePickerDialog(context, (view, h, m) -> {
            // An unchanged confirmation must not reset global or per-source last-run clocks.
            if (checked != next || day != weekday || h != hour || m != minute) {
                change.save(next, day, h, m, label);
            }
        }, scheduled ? hour : now.getHour(), scheduled ? minute : now.getMinute(),
                DateFormat.is24HourFormat(context)).show();
    }
}
