package com.example.expensemate.ui.common;

import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;

import com.example.expensemate.viewmodel.TransactionFilter;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.BiConsumer;

/** Material date and time pickers. They work in UTC, so these helpers keep the local calendar day. */
public final class Pickers {
    private Pickers() {
    }

    public static void pickDate(FragmentManager manager, String title, Calendar target, Runnable onPicked) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title)
                .setSelection(utcMillis(target))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar picked = fromUtc(selection);
            target.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH));
            onPicked.run();
        });
        picker.show(manager, "date_picker");
    }

    public static void pickDateTime(FragmentManager manager, Calendar target, Runnable onPicked) {
        pickDate(manager, "Select date", target, () -> {
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(target.get(Calendar.HOUR_OF_DAY))
                    .setMinute(target.get(Calendar.MINUTE))
                    .setTitleText("Select time")
                    .build();
            timePicker.addOnPositiveButtonClickListener(v -> {
                target.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                target.set(Calendar.MINUTE, timePicker.getMinute());
                onPicked.run();
            });
            // Cancelling the time keeps the date that was just chosen
            timePicker.addOnNegativeButtonClickListener(v -> onPicked.run());
            timePicker.show(manager, "time_picker");
        });
    }

    /** Reports the start of the first day and the end of the last day, in local time. */
    public static void pickRange(FragmentManager manager, BiConsumer<Long, Long> onPicked) {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select dates")
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar start = localDay(fromUtc(selection.first));
            TransactionFilter.startOfDay(start);
            Calendar end = localDay(fromUtc(selection.second));
            TransactionFilter.endOfDay(end);
            onPicked.accept(start.getTimeInMillis(), end.getTimeInMillis());
        });
        picker.show(manager, "range_picker");
    }

    private static Calendar localDay(Calendar utc) {
        Calendar local = Calendar.getInstance();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local;
    }

    private static Calendar fromUtc(long millis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(millis);
        return utc;
    }

    private static long utcMillis(Calendar local) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }
}
