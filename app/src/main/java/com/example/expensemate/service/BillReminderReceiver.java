package com.example.expensemate.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.example.expensemate.AppServices;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.RecurringPayment;

/** The Mark paid button on a bill reminder: marks that month paid and clears the reminder. */
public class BillReminderReceiver extends BroadcastReceiver {
    static final String ACTION_MARK_PAID = "com.example.expensemate.MARK_BILL_PAID";
    static final String EXTRA_BILL_ID = "bill_id";
    static final String EXTRA_PERIOD = "period";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_MARK_PAID.equals(intent.getAction())) {
            return;
        }
        long billId = intent.getLongExtra(EXTRA_BILL_ID, 0);
        int period = intent.getIntExtra(EXTRA_PERIOD, 0);
        if (billId == 0 || period == 0) {
            return;
        }
        NotificationManagerCompat.from(context).cancel(BillReminderWorker.notificationId(billId));
        Context app = context.getApplicationContext();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                RecurringPayment bill = AppDatabase.getDatabase(app).recurringPaymentDao().getByIdSync(billId);
                if (bill != null) {
                    AppServices.get(app).bills().markPaid(bill, period).join();
                }
            } finally {
                pending.finish();
            }
        }, "mark-bill-paid").start();
    }
}
