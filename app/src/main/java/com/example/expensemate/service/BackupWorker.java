package com.example.expensemate.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.BackupDataLoader;

/** One Google Drive backup, started by the daily schedule or by Back up now. */
public class BackupWorker extends Worker {
    private static final String TAG = "BackupWorker";
    public static final String KEY_MANUAL = "manual";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_NEEDS_CONSENT = "needs_consent";
    /** Tries before a scheduled backup gives up until its next day. */
    private static final int MAX_ATTEMPTS = 3;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        boolean manual = getInputData().getBoolean(KEY_MANUAL, false);
        try {
            String message = BackupDataLoader.backUp(context, AppDatabase.getDatabase(context));
            Log.i(TAG, (manual ? "Manual" : "Scheduled") + " backup: " + message);
            return Result.success(message(message));
        } catch (DriveAuth.NeedsConsentException e) {
            // Waiting will not help: only the user can approve access again
            BackupScheduler.recordProblem(context, "Backups are paused: sign in to Google Drive again");
            return Result.failure(new Data.Builder().putString(KEY_MESSAGE, e.getMessage())
                    .putBoolean(KEY_NEEDS_CONSENT, true).build());
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            if (!manual && getRunAttemptCount() + 1 < MAX_ATTEMPTS) {
                return Result.retry();
            }
            String message = "Backup failed: " + e.getMessage();
            if (!manual) {
                BackupScheduler.recordProblem(context, "The last automatic backup failed: " + e.getMessage());
            }
            return Result.failure(message(message));
        }
    }

    private static Data message(String message) {
        return new Data.Builder().putString(KEY_MESSAGE, message).build();
    }
}
