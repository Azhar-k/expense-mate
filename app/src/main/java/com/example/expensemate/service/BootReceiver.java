package com.example.expensemate.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.expensemate.MainActivity;

/** Scheduled jobs do not survive a reboot, so capture is set up again after one or an update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (MainActivity.hasSmsPermission(context)) {
            PendingResult pending = goAsync();
            MmsChangeJob.start(context, pending::finish);
        }
    }
}
