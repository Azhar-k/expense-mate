package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration13To14Test {
    private static final long T0 = 1_700_000_000_000L;

    /** Old unused indexes go, date and smsHash get one, and the schema still matches the entities. */
    @Test
    public void replacesUnusedIndexesWithTheOnesQueriesNeed() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-13-14.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion13(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_13_14)
                .allowMainThreadQueries()
                .build();
        try {
            SupportSQLiteDatabase db = database.getOpenHelper().getReadableDatabase();
            Set<String> indexes = new HashSet<>();
            try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'")) {
                while (cursor.moveToNext()) {
                    indexes.add(cursor.getString(0));
                }
            }
            assertTrue(indexes.contains("index_transactions_date"));
            assertTrue(indexes.contains("index_transactions_smsHash"));
            assertFalse(indexes.contains("index_categories_name"));
            assertFalse(indexes.contains("index_accounts_name"));
            assertFalse(indexes.contains("index_recurring_payments_amount"));

            assertTrue(plan(db, "SELECT * FROM transactions WHERE date BETWEEN 0 AND 1 ORDER BY date DESC")
                    .contains("index_transactions_date"));
            assertTrue(plan(db, "SELECT COUNT(*) FROM transactions WHERE smsHash = 'x'")
                    .contains("index_transactions_smsHash"));

            assertEquals(1, database.transactionDao().getTransactionsBetweenSync(new Date(0), new Date(T0)).size());
        } finally {
            database.close();
        }
    }

    private static String plan(SupportSQLiteDatabase db, String sql) {
        StringBuilder plan = new StringBuilder();
        try (Cursor cursor = db.query("EXPLAIN QUERY PLAN " + sql)) {
            while (cursor.moveToNext()) {
                plan.append(cursor.getString(cursor.getColumnIndexOrThrow("detail"))).append('\n');
            }
        }
        return plan.toString();
    }

    private static void createVersion13(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, "
                + "`description` TEXT, `date` INTEGER, `transactionType` TEXT, `receiverName` TEXT, `smsBody` TEXT, "
                + "`smsSender` TEXT, `category` TEXT, `linkedRecurringPaymentId` INTEGER, `smsHash` TEXT, "
                + "`accountId` INTEGER, `isExcludedFromSummary` INTEGER NOT NULL, `dedupKey` TEXT, `matchKey` TEXT, "
                + "`messageTime` INTEGER, `source` TEXT, `transferGroupId` TEXT, `needsReview` INTEGER NOT NULL, "
                + "`realAccountId` INTEGER, `loanId` INTEGER)");
        db.execSQL("CREATE INDEX `index_transactions_dedupKey_messageTime` ON `transactions` (`dedupKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_matchKey_messageTime` ON `transactions` (`matchKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_transferGroupId` ON `transactions` (`transferGroupId`)");
        db.execSQL("CREATE INDEX `index_transactions_loanId` ON `transactions` (`loanId`)");
        db.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, "
                + "`type` TEXT NOT NULL, `isDefault` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER)");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL, "
                + "`type` TEXT NOT NULL DEFAULT 'VIRTUAL', `sortOrder` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE `counterparty_categories` (`counterparty` TEXT NOT NULL, `transactionType` TEXT NOT NULL, "
                + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`counterparty`, `transactionType`))");
        db.execSQL("CREATE TABLE `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, "
                + "`amount` REAL NOT NULL, `date` INTEGER, `note` TEXT, `returnedManually` REAL NOT NULL, "
                + "`closedManually` INTEGER NOT NULL, `closedDate` INTEGER)");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");

        // Left behind by the version 1 to 2 migration; Room never knew about it
        db.execSQL("CREATE INDEX `index_accounts_name` ON `accounts` (`name`)");
        db.execSQL("CREATE INDEX `index_categories_name` ON `categories` (`name`)");
        db.execSQL("CREATE INDEX `index_recurring_payments_amount` ON `recurring_payments` (`amount`)");

        db.execSQL("INSERT INTO transactions (id, amount, date, transactionType, isExcludedFromSummary, needsReview) "
                + "VALUES (1, 10.0, ?, 'DEBIT', 0, 0)", new Object[] { T0 - 1000 });
        db.setVersion(13);
        db.close();
    }
}
