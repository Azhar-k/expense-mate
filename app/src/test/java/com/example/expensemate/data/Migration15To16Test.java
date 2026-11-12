package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration15To16Test {
    /** The upgrade copies the shipped patterns in once, in order, and the schema matches the entity. */
    @Test
    public void seedsTheShippedPatternsInOrder() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-15-16.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion15(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_15_16)
                .allowMainThreadQueries()
                .build();
        try {
            List<MessagePattern> stored = database.messagePatternDao().getAllSync();
            List<MessagePattern> shipped = DefaultMessagePatterns.all();
            assertEquals(shipped.size(), stored.size());
            for (int i = 0; i < shipped.size(); i++) {
                assertEquals(shipped.get(i).getBuiltinKey(), stored.get(i).getBuiltinKey());
                assertEquals(shipped.get(i).getPattern(), stored.get(i).getPattern());
                assertTrue(stored.get(i).isActive());
                assertNotNull("Does not compile: " + stored.get(i).getName(), stored.get(i).compiled());
            }

            // Running the seed again, as a restore does, adds nothing and keeps the user's edits
            MessagePattern edited = stored.get(0);
            edited.setPattern("edited (?<amount>\\d+)");
            database.messagePatternDao().update(edited);
            SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
            MessagePattern.ensureBuiltIns(db);
            assertEquals(shipped.size(), database.messagePatternDao().getAllSync().size());
            assertEquals("edited (?<amount>\\d+)", database.messagePatternDao().getAllSync().get(0).getPattern());
        } finally {
            database.close();
        }
    }

    // Version 15 as Migration14To15Test leaves it
    private static void createVersion15(File file) {
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
        db.setVersion(15);
        db.close();
    }
}
