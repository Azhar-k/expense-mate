package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
public class Migration14To15Test {
    /** Everything so far was in rupees; virtual accounts get no currency, and the schema matches the entities. */
    @Test
    public void labelsEverythingAsRupeesExceptVirtualAccounts() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-14-15.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion14(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_14_15)
                .allowMainThreadQueries()
                .build();
        try {
            List<Account> accounts = database.accountDao().getAllAccountsSync();
            for (Account account : accounts) {
                if (account.isReal()) {
                    assertEquals("INR", account.getCurrency());
                } else {
                    assertNull(account.getCurrency());
                }
            }
            assertEquals(2, accounts.size());
            assertEquals("INR", database.transactionDao().getTransactionSync(1).getCurrency());
            assertEquals("INR", database.loanDao().getAllLoansSync().get(0).getCurrency());
            assertEquals("INR", database.recurringPaymentDao().getAllRecurringPaymentsSync().get(0).getCurrency());
        } finally {
            database.close();
        }
    }

    private static void createVersion14(File file) {
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
        db.execSQL("CREATE INDEX `index_transactions_date` ON `transactions` (`date`)");
        db.execSQL("CREATE INDEX `index_transactions_smsHash` ON `transactions` (`smsHash`)");
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

        db.execSQL("INSERT INTO accounts (id, name, isDefault, type) VALUES (1, 'Daily', 1, 'VIRTUAL')");
        db.execSQL("INSERT INTO accounts (id, name, isDefault, type) VALUES (2, 'Bank account', 1, 'REAL')");
        db.execSQL("INSERT INTO transactions (id, amount, date, transactionType, isExcludedFromSummary, needsReview, "
                + "accountId, realAccountId) VALUES (1, 10.0, 1000, 'DEBIT', 0, 0, 1, 2)");
        db.execSQL("INSERT INTO loans (person, amount, returnedManually, closedManually) VALUES ('Ravi', 500, 0, 0)");
        db.execSQL("INSERT INTO recurring_payments (name, amount, dueDay, isCompleted) VALUES ('Rent', 900, 5, 0)");
        db.setVersion(14);
        db.close();
    }
}
