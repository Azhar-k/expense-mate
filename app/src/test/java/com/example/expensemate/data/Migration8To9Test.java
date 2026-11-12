package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
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
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration8To9Test {
    private static final long T0 = 1793447463000L;

    /** Opening through Room also checks the new table against the entity. */
    @Test
    public void startsTheMappingFromExistingCategories() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-8-9.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion8(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15)
                .allowMainThreadQueries()
                .build();
        try {
            TransactionDao dao = database.transactionDao();
            assertEquals(4, dao.getAllTransactionsSyncOrderByDateAsc().size());

            assertEquals("Food", dao.getCounterpartyCategory(TransactionKeys.counterpartyKey("Swiggy"), "DEBIT"));
            assertEquals(null, dao.getCounterpartyCategory(TransactionKeys.counterpartyKey("Waiting"), "DEBIT"));
            assertEquals(null, dao.getCounterpartyCategory(TransactionKeys.counterpartyKey("Gone"), "DEBIT"));

            Transaction next = new Transaction(10, "x", new Date(T0 + 500000), "DEBIT", "SWIGGY", "x", "AX-BANK-S");
            next.setMessageTime(T0 + 500000);
            next.setDedupKey(TransactionKeys.bodyKey("x"));
            next.setNeedsReview(true);
            assertTrue(TestServices.over(database).capture().recordIfNew(next));
            assertEquals("Food", next.getCategory());
        } finally {
            database.close();
        }
    }

    private static void createVersion8(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, "
                + "`description` TEXT, `date` INTEGER, `transactionType` TEXT, `receiverName` TEXT, `smsBody` TEXT, "
                + "`smsSender` TEXT, `category` TEXT, `linkedRecurringPaymentId` INTEGER, `smsHash` TEXT, "
                + "`accountId` INTEGER, `isExcludedFromSummary` INTEGER NOT NULL, `dedupKey` TEXT, `matchKey` TEXT, "
                + "`messageTime` INTEGER, `source` TEXT, `transferGroupId` TEXT, `needsReview` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX `index_transactions_dedupKey_messageTime` ON `transactions` (`dedupKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_matchKey_messageTime` ON `transactions` (`matchKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_transferGroupId` ON `transactions` (`transferGroupId`)");
        db.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, "
                + "`type` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER)");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");

        db.execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Food', 'EXPENSE'), (2, 'Fuel', 'EXPENSE')");
        String insert = "INSERT INTO transactions (id, amount, date, transactionType, receiverName, category, "
                + "isExcludedFromSummary, needsReview) VALUES (?,?,?,?,?,?,0,?)";
        db.execSQL(insert, new Object[] { 1, 10.0, T0, "DEBIT", "Swiggy", "Fuel", 0 });
        db.execSQL(insert, new Object[] { 2, 10.0, T0 + 1000, "DEBIT", "Swiggy", "Food", 0 });
        // Still waiting for a category, and a category that no longer exists
        db.execSQL(insert, new Object[] { 3, 10.0, T0 + 2000, "DEBIT", "Waiting", "Food", 1 });
        db.execSQL(insert, new Object[] { 4, 10.0, T0 + 3000, "DEBIT", "Gone", "Charity", 0 });
        db.setVersion(8);
        db.close();
    }
}
