package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.util.Periods;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration18To19Test {
    // Mid-month at noon, so the month is the same in any time zone
    private static long day(int year, int month) {
        return new GregorianCalendar(year, month, 14, 12, 0).getTimeInMillis();
    }

    /** Past months come back from linked transactions and from a bill that was still ticked. */
    @Test
    public void billsGetTheirMonthsFromWhatTheyHadBefore() {
        File file = Migration17To18Test.newFile("migration-18-19.db");
        SQLiteDatabase db = Migration17To18Test.createVersion17(file);
        db.execSQL("INSERT INTO accounts (id, name, isDefault, type) VALUES (1, 'Daily', 1, 'VIRTUAL')");
        db.execSQL("INSERT INTO accounts (id, name, isDefault, type, currency) VALUES (2, 'Bank', 1, 'REAL', 'INR')");
        db.execSQL("INSERT INTO recurring_payments (id, name, amount, dueDay, isCompleted, currency) "
                + "VALUES (7, 'Rent', 15000, 1, 0, 'INR')");
        db.execSQL("INSERT INTO recurring_payments (id, name, amount, dueDay, isCompleted, lastCompletedDate, currency) "
                + "VALUES (8, 'Phone', 799, 10, 1, " + day(2026, Calendar.MAY) + ", 'INR')");
        db.execSQL("INSERT INTO recurring_payments (id, name, amount, dueDay, isCompleted, currency) "
                + "VALUES (9, 'Gym', 900, 5, 0, 'INR')");
        String insert = "INSERT INTO transactions (id, amount, date, transactionType, accountId, realAccountId, "
                + "isExcludedFromSummary, needsReview, linkedRecurringPaymentId, currency) VALUES (?,?,?,?,?,?,?,?,?,?)";
        db.execSQL(insert, new Object[] { 1, 14800.0, day(2026, Calendar.MARCH), "DEBIT", 1, 2, 0, 0, 7, "INR" });
        db.execSQL(insert, new Object[] { 2, 15000.0, day(2026, Calendar.APRIL), "DEBIT", 1, 2, 0, 0, 7, "INR" });
        // A second payment in April keeps the first one
        db.execSQL(insert, new Object[] { 3, 50.0, day(2026, Calendar.APRIL) + 1000, "DEBIT", 1, 2, 0, 0, 7, "INR" });
        // Linked to a bill that is gone
        db.execSQL(insert, new Object[] { 4, 10.0, day(2026, Calendar.APRIL), "DEBIT", 1, 2, 0, 0, 99, "INR" });
        db.setVersion(17);
        db.close();

        AppDatabase database = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class,
                        file.getPath())
                .addMigrations(AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19)
                .allowMainThreadQueries()
                .build();
        try {
            assertTrue(Migration17To18Test.columns(database.getOpenHelper().getReadableDatabase(),
                    "recurring_payments").contains("startPeriod"));
            RecurringPaymentDao dao = database.recurringPaymentDao();
            List<BillPayment> all = dao.getAllPaymentsSync();
            assertEquals(3, all.size());

            BillPayment march = dao.getPaymentSync(7, 202603);
            assertEquals(Long.valueOf(1), march.getTransactionId());
            assertEquals(14800, march.getAmount(), 0);
            assertEquals(day(2026, Calendar.MARCH), march.getPaidOn().getTime());
            assertEquals(Long.valueOf(2), dao.getPaymentSync(7, 202604).getTransactionId());

            BillPayment may = dao.getPaymentSync(8, 202605);
            assertNull(may.getTransactionId());
            assertEquals(799, may.getAmount(), 0);

            // Months ticked by hand were never kept, so none before now count as missed
            for (long id = 7; id <= 9; id++) {
                assertEquals(Periods.current(), dao.getByIdSync(id).getStartPeriod());
            }
            // Its paid months still show, though they are before it starts
            RecurringPayment rent = dao.getByIdSync(7);
            assertEquals(com.example.expensemate.util.Bills.Status.PAID, com.example.expensemate.util.Bills.statusOf(
                    rent, com.example.expensemate.util.BillLedger.of(all), 202603, java.util.Calendar.getInstance()));
            assertEquals(com.example.expensemate.util.Bills.Status.NOT_STARTED,
                    com.example.expensemate.util.Bills.statusOf(rent, com.example.expensemate.util.BillLedger.of(all),
                            202602, java.util.Calendar.getInstance()));
        } finally {
            database.close();
        }
    }
}
