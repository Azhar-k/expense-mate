package com.example.expensemate.service;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.RecurringPaymentDao;
import com.example.expensemate.util.BillLedger;
import com.example.expensemate.util.BillReminders;
import com.example.expensemate.util.Bills;
import com.example.expensemate.util.Money;
import com.example.expensemate.util.Periods;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Posts a notification for each bill coming due, with a button to mark it paid. */
public class BillReminderWorker extends Worker {
    public BillReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!BillReminderScheduler.isOn(context) || !BillReminderScheduler.canNotify(context)) {
            return Result.success();
        }
        RecurringPaymentDao dao = AppDatabase.getDatabase(context).recurringPaymentDao();
        Calendar today = Calendar.getInstance();
        BillLedger ledger = BillLedger.of(dao.getAllPaymentsSync());
        for (BillReminders.Reminder reminder : BillReminders.on(dao.getAllRecurringPaymentsSync(), ledger, today,
                BillReminderScheduler.daysAhead(context))) {
            if (BillReminderScheduler.firstTime(context, reminder.key(), Periods.of(today))) {
                post(context, reminder);
            }
        }
        return Result.success();
    }

    static int notificationId(long billId) {
        return (int) (billId ^ (billId >>> 32));
    }

    @SuppressLint("MissingPermission") // canNotify was checked before this runs
    private static void post(Context context, BillReminders.Reminder reminder) {
        long billId = reminder.bill.getId();
        int id = notificationId(billId);
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_BILLS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent paid = new Intent(context, BillReminderReceiver.class)
                .setAction(BillReminderReceiver.ACTION_MARK_PAID)
                .putExtra(BillReminderReceiver.EXTRA_BILL_ID, billId)
                .putExtra(BillReminderReceiver.EXTRA_PERIOD, reminder.period);
        PendingIntent markPaid = PendingIntent.getBroadcast(context, id, paid,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String due = new SimpleDateFormat("d MMM", Locale.getDefault())
                .format(Bills.dueDate(reminder.bill, reminder.period).getTime());
        String text = Money.format(reminder.bill.getAmount(), reminder.bill.getCurrency()) + " · due " + due;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context,
                BillReminderScheduler.CHANNEL)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle(BillReminders.title(reminder))
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check, "Mark paid", markPaid);
        NotificationManagerCompat.from(context).notify(id, notification.build());
    }
}
