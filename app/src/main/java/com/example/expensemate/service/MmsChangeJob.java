package com.example.expensemate.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;

/**
 * Wakes the app when the MMS provider changes, which is where RCS messages land. Android
 * runs a content job once per change and forgets it on reboot, so it is scheduled again
 * after every run and by {@link BootReceiver}.
 */
public class MmsChangeJob extends JobService {
    private static final int JOB_ID = 1;

    public static void schedule(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, MmsChangeJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("content://mms"),
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                // A message is written as a row and then its parts, so wait for the writes to settle
                .setTriggerContentUpdateDelay(3000)
                .setTriggerContentMaxDelay(15000)
                .build();
        context.getSystemService(JobScheduler.class).schedule(job);
    }

    /** Starts listening for new messages and collects any that were missed. */
    public static void start(Context context, Runnable done) {
        schedule(context);
        MessageCapture.catchUp(context, done);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        MessageCapture.processNewMms(this, () -> {
            schedule(this);
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        schedule(this);
        return false;
    }
}
