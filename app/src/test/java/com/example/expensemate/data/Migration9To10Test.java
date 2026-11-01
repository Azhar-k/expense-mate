package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration9To10Test {
    /** Expense has a Default category to mark, income has none and gets one. */
    @Test
    public void everyTypeEndsUpWithADefault() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-9-10.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion9(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
                .allowMainThreadQueries()
                .build();
        try {
            TransactionDao dao = database.transactionDao();
            assertEquals("Default", dao.getDefaultCategory("DEBIT"));
            assertEquals("Default", dao.getDefaultCategory("CREDIT"));
            assertEquals(3, database.categoryDao().getAllCategoriesSync().size());
            assertEquals("Food", dao.getCounterpartyCategory("KEEP", "DEBIT"));
        } finally {
            database.close();
        }
    }

    private static void createVersion9(File file) {
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
        db.execSQL("CREATE TABLE `counterparty_categories` (`counterparty` TEXT NOT NULL, `transactionType` TEXT NOT NULL, "
                + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`counterparty`, `transactionType`))");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");

        db.execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Food', 'EXPENSE'), (2, 'Default', 'EXPENSE')");
        db.execSQL("INSERT INTO counterparty_categories VALUES ('KEEP', 'DEBIT', 1)");
        db.setVersion(9);
        db.close();
    }
}
