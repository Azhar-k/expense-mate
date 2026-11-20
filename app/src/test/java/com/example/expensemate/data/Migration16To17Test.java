package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration16To17Test {
    /** Virtual accounts come through as funds with no target, and budgets start empty and work. */
    @Test
    public void virtualAccountsBecomeFundsAndBudgetsWork() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-16-17.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion16(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18)
                .allowMainThreadQueries()
                .build();
        try {
            Fund daily = database.fundDao().getDefaultFundSync();
            assertEquals("Daily", daily.getName());
            assertNull(daily.getTarget());
            assertNull(daily.getTargetDate());
            assertEquals("Bank account", database.accountDao().getDefaultAccountSync().getName());
            assertTrue(database.budgetDao().getAllSync().isEmpty());

            Fund trip = database.fundDao().getFundByIdSync(2);
            trip.setTarget(20000.0);
            database.fundDao().update(trip);
            assertEquals(20000.0, database.fundDao().getFundByIdSync(2).getTarget(), 0);

            Budget food = new Budget();
            food.setName("Food");
            food.setAmount(5000);
            food.setCurrency("INR");
            TestServices.over(database).budgets().add(food, Arrays.asList("Food")).join();
            List<BudgetProgress> progress = database.budgetDao().getProgressSync(new Date(0), new Date(Long.MAX_VALUE));
            assertEquals(1, progress.size());
            assertEquals(120, progress.get(0).spent, 0.001); // The Daily row; the Trip one was set aside
        } finally {
            database.close();
        }
    }

    // Version 16 as Migration15To16Test leaves it, with a little data
    private static void createVersion16(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, "
                + "`description` TEXT, `date` INTEGER, `transactionType` TEXT, `receiverName` TEXT, `smsBody` TEXT, "
                + "`smsSender` TEXT, `category` TEXT, `linkedRecurringPaymentId` INTEGER, `smsHash` TEXT, "
                + "`accountId` INTEGER, `isExcludedFromSummary` INTEGER NOT NULL, `dedupKey` TEXT, `matchKey` TEXT, "
                + "`messageTime` INTEGER, `source` TEXT, `transferGroupId` TEXT, `needsReview` INTEGER NOT NULL, "
                + "`realAccountId` INTEGER, `loanId` INTEGER, `currency` TEXT NOT NULL DEFAULT 'INR')");
        db.execSQL("CREATE INDEX `index_transactions_dedupKey_messageTime` ON `transactions` (`dedupKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_matchKey_messageTime` ON `transactions` (`matchKey`, `messageTime`)");
        db.execSQL("CREATE INDEX `index_transactions_transferGroupId` ON `transactions` (`transferGroupId`)");
        db.execSQL("CREATE INDEX `index_transactions_loanId` ON `transactions` (`loanId`)");
        db.execSQL("CREATE INDEX `index_transactions_date` ON `transactions` (`date`)");
        db.execSQL("CREATE INDEX `index_transactions_smsHash` ON `transactions` (`smsHash`)");
        db.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, "
                + "`type` TEXT NOT NULL, `isDefault` INTEGER NOT NULL DEFAULT 0, `sortOrder` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER, "
                + "`currency` TEXT NOT NULL DEFAULT 'INR')");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL, "
                + "`type` TEXT NOT NULL DEFAULT 'VIRTUAL', `sortOrder` INTEGER NOT NULL DEFAULT 0, `currency` TEXT)");
        db.execSQL("CREATE TABLE `counterparty_categories` (`counterparty` TEXT NOT NULL, `transactionType` TEXT NOT NULL, "
                + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`counterparty`, `transactionType`))");
        db.execSQL("CREATE TABLE `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, "
                + "`amount` REAL NOT NULL, `date` INTEGER, `note` TEXT, `returnedManually` REAL NOT NULL, "
                + "`closedManually` INTEGER NOT NULL, `closedDate` INTEGER, `currency` TEXT NOT NULL DEFAULT 'INR')");
        db.execSQL(MessagePattern.CREATE_TABLE);
        db.execSQL(MessagePattern.CREATE_INDEX);

        db.execSQL("INSERT INTO accounts (id, name, isDefault, type) VALUES (1, 'Daily', 1, 'VIRTUAL'), "
                + "(2, 'Trip', 0, 'VIRTUAL')");
        db.execSQL("INSERT INTO accounts (id, name, isDefault, type, currency) VALUES (3, 'Bank account', 1, 'REAL', 'INR')");
        long now = System.currentTimeMillis();
        db.execSQL("INSERT INTO transactions (amount, date, transactionType, category, accountId, realAccountId, "
                + "isExcludedFromSummary, needsReview) VALUES (120, " + now + ", 'DEBIT', 'Food', 1, 3, 0, 0), "
                + "(900, " + now + ", 'DEBIT', 'Food', 2, 3, 0, 0)");
        db.setVersion(16);
        db.close();
    }
}
