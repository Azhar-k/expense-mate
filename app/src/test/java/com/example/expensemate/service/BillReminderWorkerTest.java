package com.example.expensemate.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.example.expensemate.AppServices;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.util.Periods;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BillReminderWorkerTest {
    private Application app;
    private AppDatabase database;

    @Before
    public void setUp() {
        app = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        app.getSharedPreferences("bill_reminders", Context.MODE_PRIVATE).edit().clear().commit();
        database = AppDatabase.getDatabase(app);
    }

    @After
    public void tearDown() throws Exception {
        database.close();
        for (Class<?> holder : new Class<?>[] { AppDatabase.class, AppServices.class }) {
            Field instance = holder.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            instance.set(null, null);
        }
    }

    private long bill(String name, int dueDay, boolean topUp) {
        RecurringPayment bill = new RecurringPayment(name, 799, dueDay, new Date(Long.MAX_VALUE / 2));
        bill.setCurrency("INR");
        bill.setStartPeriod(Periods.current());
        if (topUp) {
            bill.setFromFundId(1L);
            bill.setToFundId(2L);
        }
        return off(() -> database.recurringPaymentDao().insert(bill));
    }

    private void runWorker() {
        off(() -> TestWorkerBuilder.from(app, BillReminderWorker.class, Executors.newSingleThreadExecutor()).build()
                .doWork());
    }

    // Room refuses the main thread, which is the test's own thread here
    private static <T> T off(java.util.concurrent.Callable<T> work) {
        try {
            return Executors.newSingleThreadExecutor().submit(work).get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<Notification> posted() {
        return Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getAllNotifications();
    }

    @Test
    public void aBillDueTodayIsRemindedOnceAndATopUpNotAtAll() {
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        long phone = bill("Phone bill", today, false);
        bill("Savings", today, true);
        BillReminderScheduler.sync(app); // Makes the channel, as the app does on start

        runWorker();
        List<Notification> notifications = posted();
        assertEquals(1, notifications.size());
        assertEquals("Phone bill is due today",
                notifications.get(0).extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Mark paid", notifications.get(0).actions[0].title.toString());

        runWorker();
        assertEquals("Not sent again the same day", 1, posted().size());

        // The Mark paid button marks this month and clears the reminder
        Intent markPaid = new Intent(app, BillReminderReceiver.class)
                .setAction(BillReminderReceiver.ACTION_MARK_PAID)
                .putExtra(BillReminderReceiver.EXTRA_BILL_ID, phone)
                .putExtra(BillReminderReceiver.EXTRA_PERIOD, Periods.current());
        new BillReminderReceiver().onReceive(app, markPaid);
        for (int i = 0; i < 100 && off(() -> database.recurringPaymentDao().getPaymentSync(phone, Periods.current())) == null;
                i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertTrue(off(() -> database.recurringPaymentDao().getPaymentSync(phone, Periods.current())) != null);
        assertTrue(posted().isEmpty());
    }

    @Test
    public void nothingIsSentWhenRemindersAreOff() {
        bill("Phone bill", Calendar.getInstance().get(Calendar.DAY_OF_MONTH), false);
        BillReminderScheduler.setOn(app, false);
        runWorker();
        assertTrue(posted().isEmpty());
    }

    @Test
    public void theDailyCheckRunsNextAtNine() {
        Calendar morning = new GregorianCalendar(2026, Calendar.NOVEMBER, 21, 7, 30);
        assertEquals(90 * 60 * 1000L, BillReminderScheduler.millisUntilNextRun(morning));
        Calendar evening = new GregorianCalendar(2026, Calendar.NOVEMBER, 21, 18, 0);
        assertEquals(15 * 60 * 60 * 1000L, BillReminderScheduler.millisUntilNextRun(evening));
    }
}
