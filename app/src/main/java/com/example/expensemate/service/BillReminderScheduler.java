package com.example.expensemate.service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.expensemate.util.Periods;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Checks bills once a day in the morning and reminds about the ones coming due. */
public final class BillReminderScheduler {
    static final String DAILY_WORK = "bill-reminders";
    static final String NOW_WORK = "bill-reminders-now";
    static final String CHANNEL = "bill_reminders";
    public static final int[] DAYS_AHEAD_CHOICES = { 0, 1, 2, 3, 5, 7 };
    /** The hour of the morning reminders go out. */
    static final int HOUR = 9;

    private static final String PREFS = "bill_reminders";
    private static final String PREF_ON = "on";
    private static final String PREF_DAYS_AHEAD = "days_ahead";
    private static final String PREF_SENT = "sent";
    private static final String PREF_ASKED = "asked_permission";

    private BillReminderScheduler() {
    }

    public static boolean isOn(Context context) {
        return prefs(context).getBoolean(PREF_ON, true);
    }

    public static void setOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(PREF_ON, on).apply();
        sync(context);
        if (on) {
            checkNow(context);
        }
    }

    public static int daysAhead(Context context) {
        return prefs(context).getInt(PREF_DAYS_AHEAD, 3);
    }

    public static void setDaysAhead(Context context, int days) {
        prefs(context).edit().putInt(PREF_DAYS_AHEAD, days).apply();
        checkNow(context);
    }

    /** "3 days before, on the day and the day after". */
    public static String describe(int daysAhead) {
        String before = daysAhead == 0 ? "" : daysAhead == 1 ? "The day before, " : daysAhead + " days before, ";
        return (before.isEmpty() ? "On the due day" : before + "on the due day") + " and the day after";
    }

    /** Keeps the daily check in step with the setting; safe to call on every start. */
    public static void sync(Context context) {
        WorkManager work = WorkManager.getInstance(context);
        if (!isOn(context)) {
            work.cancelUniqueWork(DAILY_WORK);
            return;
        }
        createChannel(context);
        PeriodicWorkRequest daily = new PeriodicWorkRequest.Builder(BillReminderWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextRun(Calendar.getInstance()), TimeUnit.MILLISECONDS)
                .build();
        work.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, daily);
    }

    /** Runs the check straight away, so turning reminders on covers today too. */
    public static void checkNow(Context context) {
        if (!isOn(context)) {
            return;
        }
        createChannel(context);
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(BillReminderWorker.class).build());
    }

    static long millisUntilNextRun(Calendar now) {
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    public static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Asking once is enough; after that the switch in Settings asks again. */
    public static boolean shouldAskForPermission(Context context) {
        return isOn(context) && !canNotify(context) && !prefs(context).getBoolean(PREF_ASKED, false);
    }

    public static void markAskedForPermission(Context context) {
        prefs(context).edit().putBoolean(PREF_ASKED, true).apply();
    }

    /** True the first time a reminder is offered; later the same one is skipped. */
    static boolean firstTime(Context context, String key, int currentPeriod) {
        SharedPreferences prefs = prefs(context);
        Set<String> sent = new HashSet<>(prefs.getStringSet(PREF_SENT, new HashSet<>()));
        if (sent.contains(key)) {
            return false;
        }
        // Months well past cannot come round again, so their keys go
        int oldest = Periods.plus(currentPeriod, -2);
        sent.removeIf(each -> {
            String[] parts = each.split(":");
            return parts.length < 2 || Integer.parseInt(parts[1]) < oldest;
        });
        sent.add(key);
        prefs.edit().putStringSet(PREF_SENT, sent).apply();
        return true;
    }

    private static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Bill reminders",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Bills coming due, due today, or a day late");
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
