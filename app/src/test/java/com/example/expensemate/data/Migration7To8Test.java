package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration7To8Test {
    private static final long T0 = 1793447463000L;
    private static final long OLD = T0 - 200L * 24 * 60 * 60 * 1000;

    /** Opening through Room also checks the migrated tables against the entities. */
    @Test
    public void keepsEveryRowAndLinksOnlyRealTransfers() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-7-8.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion7(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12)
                .allowMainThreadQueries()
                .build();
        try {
            List<Transaction> rows = database.transactionDao().getAllTransactionsSyncOrderByDateAsc();
            assertEquals(11, rows.size());

            // A self transfer: linked, with its other values untouched
            Transaction debit = find(rows, 1);
            Transaction credit = find(rows, 2);
            assertNotNull(debit.getTransferGroupId());
            assertEquals(debit.getTransferGroupId(), credit.getTransferGroupId());
            assertEquals(2000, debit.getAmount(), 0.001);
            assertEquals("Charity", debit.getCategory());
            assertEquals(Long.valueOf(10), debit.getAccountId());
            assertEquals(Long.valueOf(20), credit.getAccountId());
            assertEquals(Long.valueOf(99), debit.getLinkedRecurringPaymentId());

            // Rows restored from a backup hold the text "null" and still link
            assertNotNull(find(rows, 3).getTransferGroupId());
            assertEquals(find(rows, 3).getTransferGroupId(), find(rows, 4).getTransferGroupId());

            // Not transfers: both messages, same account, or different amounts
            assertNull(find(rows, 5).getTransferGroupId());
            assertNull(find(rows, 6).getTransferGroupId());
            assertNull(find(rows, 7).getTransferGroupId());
            assertNull(find(rows, 8).getTransferGroupId());

            // Two debits could both match one credit, so none of them is guessed at
            assertNull(find(rows, 9).getTransferGroupId());
            assertNull(find(rows, 10).getTransferGroupId());
            assertNull(find(rows, 11).getTransferGroupId());
        } finally {
            database.close();
        }
    }

    @Test
    public void reviewListStartsWithOnlyRecentCapturedRows() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-7-8-review.db");
        file.getParentFile().mkdirs();
        file.delete();
        SQLiteDatabase db = createTables(file);
        long recent = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        String insert = "INSERT INTO transactions (id, amount, date, transactionType, category, source, "
                + "isExcludedFromSummary) VALUES (?,?,?,?,?,?,?)";
        db.execSQL(insert, new Object[] { 1, 10.0, recent, "DEBIT", "Default", "SMS_BROADCAST", 0 });
        db.execSQL(insert, new Object[] { 2, 10.0, OLD, "DEBIT", "Default", "SMS_BROADCAST", 0 });
        db.execSQL(insert, new Object[] { 3, 10.0, recent, "DEBIT", "Food", "SMS_BROADCAST", 0 });
        db.execSQL(insert, new Object[] { 4, 10.0, recent, "DEBIT", "Default", null, 0 });
        db.execSQL(insert, new Object[] { 5, 10.0, recent, "DEBIT", "Default", "SMS_BROADCAST", 1 });
        db.setVersion(7);
        db.close();

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12)
                .allowMainThreadQueries()
                .build();
        try {
            List<Transaction> rows = database.transactionDao().getAllTransactionsSyncOrderByDateAsc();
            assertTrue(find(rows, 1).isNeedsReview());
            assertFalse("Too old", find(rows, 2).isNeedsReview());
            assertFalse("Has a category", find(rows, 3).isNeedsReview());
            assertFalse("Entered by hand", find(rows, 4).isNeedsReview());
            assertFalse("Left out of the summary", find(rows, 5).isNeedsReview());
        } finally {
            database.close();
        }
    }

    private static Transaction find(List<Transaction> rows, long id) {
        for (Transaction row : rows) {
            if (row.getId() == id) {
                return row;
            }
        }
        throw new AssertionError("No row with id " + id);
    }

    private static void createVersion7(File file) {
        SQLiteDatabase db = createTables(file);
        String insert = "INSERT INTO transactions (id, amount, description, date, transactionType, receiverName, "
                + "smsBody, category, accountId, linkedRecurringPaymentId, isExcludedFromSummary) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,0)";
        // A transfer, saved by the old Self Transfer screen
        db.execSQL(insert, new Object[] { 1, 2000.0, "gift", T0, "DEBIT", "", "", "Charity", 10, 99 });
        db.execSQL(insert, new Object[] { 2, 2000.0, "gift", T0, "CREDIT", "", "", "Charity", 20, null });
        // A transfer restored from a backup
        db.execSQL(insert, new Object[] { 3, 500.0, "move", T0 + 1000, "DEBIT", "", "null", "Default", 10, null });
        db.execSQL(insert, new Object[] { 4, 500.0, "move", T0 + 1000, "CREDIT", "", "null", "Default", 20, null });
        // Bank messages that happen to line up are real income and spending
        db.execSQL(insert, new Object[] { 5, 800.0, "x", T0 + 2000, "DEBIT", "", "Rs 800 debited", "Food", 10, null });
        db.execSQL(insert, new Object[] { 6, 800.0, "x", T0 + 2000, "CREDIT", "", "Rs 800 credited", "Food", 20, null });
        // Same account on both sides
        db.execSQL(insert, new Object[] { 7, 300.0, "y", T0 + 3000, "DEBIT", "", "", "Food", 10, null });
        db.execSQL(insert, new Object[] { 8, 300.0, "y", T0 + 3000, "CREDIT", "", "", "Food", 10, null });
        // Ambiguous: two debits for one credit
        db.execSQL(insert, new Object[] { 9, 100.0, "z", T0 + 4000, "DEBIT", "", "", "Food", 10, null });
        db.execSQL(insert, new Object[] { 10, 100.0, "z", T0 + 4000, "DEBIT", "", "", "Food", 30, null });
        db.execSQL(insert, new Object[] { 11, 100.0, "z", T0 + 4000, "CREDIT", "", "", "Food", 20, null });
        db.setVersion(7);
        db.close();
    }

    private static SQLiteDatabase createTables(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, "
                + "`description` TEXT, `date` INTEGER, `transactionType` TEXT, `receiverName` TEXT, `smsBody` TEXT, "
                + "`smsSender` TEXT, `category` TEXT, `linkedRecurringPaymentId` INTEGER, `smsHash` TEXT, "
                + "`accountId` INTEGER, `isExcludedFromSummary` INTEGER NOT NULL, `dedupKey` TEXT, `matchKey` TEXT, "
                + "`messageTime` INTEGER, `source` TEXT)");
        db.execSQL("CREATE INDEX `index_transactions_dedupKey_messageTime` ON `transactions` (`dedupKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_matchKey_messageTime` ON `transactions` (`matchKey`, `messageTime`)");
        db.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, "
                + "`type` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER)");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        return db;
    }
}
