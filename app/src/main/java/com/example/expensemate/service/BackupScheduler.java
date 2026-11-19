package com.example.expensemate.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Runs Google Drive backups through WorkManager, so one finishes even if the app is closed or
 * the phone restarts, and keeps what the backup card shows: the last backup and any problem.
 */
public final class BackupScheduler {
    public static final String AUTO_WORK = "auto-backup";
    public static final String MANUAL_WORK = "backup-now";

    private static final String PREFS = "backup";
    private static final String PREF_LAST_BACKUP = "last_backup";
    private static final String PREF_AUTO = "auto_backup";
    private static final String PREF_PROBLEM = "backup_problem";

    private BackupScheduler() {
    }

    static Constraints constraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
    }

    /** Backs up once a day while there is a connection and the battery is not low. */
    static PeriodicWorkRequest dailyRequest() {
        return new PeriodicWorkRequest.Builder(BackupWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build();
    }

    public static boolean isAutomatic(Context context) {
        return prefs(context).getBoolean(PREF_AUTO, false);
    }

    public static void setAutomatic(Context context, boolean on) {
        prefs(context).edit().putBoolean(PREF_AUTO, on).apply();
        WorkManager work = WorkManager.getInstance(context);
        if (on) {
            work.enqueueUniquePeriodicWork(AUTO_WORK, ExistingPeriodicWorkPolicy.KEEP, dailyRequest());
        } else {
            work.cancelUniqueWork(AUTO_WORK);
        }
    }

    /** Starts a backup now; a second tap while one is waiting or running does nothing. */
    public static void backUpNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(new Data.Builder().putBoolean(BackupWorker.KEY_MANUAL, true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.KEEP, request);
    }

    /** Stops automatic backups; the account is gone, so they could only fail. */
    public static void onSignedOut(Context context) {
        setAutomatic(context, false);
        prefs(context).edit().remove(PREF_PROBLEM).apply();
    }

    public static long lastBackup(Context context) {
        return prefs(context).getLong(PREF_LAST_BACKUP, 0);
    }

    /** Remembers a finished backup, made here or found on Drive; it also clears an old problem. */
    public static void recordBackup(Context context, long time) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putLong(PREF_LAST_BACKUP, Math.max(time, prefs.getLong(PREF_LAST_BACKUP, 0)))
                .remove(PREF_PROBLEM)
                .apply();
    }

    /** Why the last backup did not happen, or null. */
    public static String problem(Context context) {
        return prefs(context).getString(PREF_PROBLEM, null);
    }

    static void recordProblem(Context context, String problem) {
        prefs(context).edit().putString(PREF_PROBLEM, problem).apply();
    }

    public static void clearProblem(Context context) {
        prefs(context).edit().remove(PREF_PROBLEM).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
