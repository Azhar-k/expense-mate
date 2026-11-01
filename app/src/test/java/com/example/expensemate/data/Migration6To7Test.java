package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration6To7Test {
    private static final long T0 = 1793447463000L;
    private static final String BODY = "ICICI Bank Acct XX444 debited for Rs 600.00 on 31-Oct-26; TEST SHOP credited.";

    /** Opening through Room also checks the migrated tables against the entities. */
    @Test
    public void migratesRowsAndKeepsEveryValue() {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-test.db");
        file.getParentFile().mkdirs();
        createVersion6(file);

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
                .allowMainThreadQueries()
                .build();
        try {
            List<Transaction> rows = database.transactionDao().getAllTransactionsSyncOrderByDateAsc();
            assertEquals(4, rows.size());

            Transaction sms = find(rows, 1);
            assertEquals(600, sms.getAmount(), 0.001);
            assertEquals("Food", sms.getCategory());
            assertEquals("AX-ICICIT-S", sms.getSmsSender());
            assertEquals("old-hash", sms.getSmsHash());
            assertEquals(TransactionKeys.bodyKey(BODY), sms.getDedupKey());
            assertEquals(TransactionKeys.matchKey(600, "DEBIT", "TEST SHOP"), sms.getMatchKey());
            assertEquals(Long.valueOf(T0), sms.getMessageTime());
            assertEquals(TransactionKeys.SOURCE_LEGACY, sms.getSource());

            Transaction truecaller = find(rows, 2);
            assertNull(truecaller.getDedupKey());
            assertEquals(TransactionKeys.matchKey(55, "DEBIT", "Shop"), truecaller.getMatchKey());
            assertEquals(TransactionKeys.SOURCE_TRUECALLER, truecaller.getSource());

            for (long manualId : new long[] { 3, 4 }) {
                Transaction manual = find(rows, manualId);
                assertNull(manual.getDedupKey());
                assertNull(manual.getMatchKey());
                assertNull(manual.getMessageTime());
                assertNull(manual.getSource());
            }

            // A scan of the same message finds the migrated row
            Transaction scanned = new Transaction(600, BODY, new Date(T0 + 1000), "DEBIT", "TEST SHOP", BODY, "AX-ICICIT-S");
            scanned.setMessageTime(T0 + 1000);
            scanned.setSource(TransactionKeys.SOURCE_SMS_SCAN);
            scanned.setDedupKey(TransactionKeys.bodyKey(BODY));
            assertFalse(database.transactionDao().insertIfNew(scanned));
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

    private static void createVersion6(File file) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, "
                + "`description` TEXT, `date` INTEGER, `transactionType` TEXT, `receiverName` TEXT, `smsBody` TEXT, "
                + "`smsSender` TEXT, `category` TEXT, `linkedRecurringPaymentId` INTEGER, `smsHash` TEXT, "
                + "`accountId` INTEGER, `isExcludedFromSummary` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, "
                + "`type` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER)");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");

        String insert = "INSERT INTO transactions (id, amount, description, date, transactionType, receiverName, smsBody, "
                + "smsSender, category, smsHash, isExcludedFromSummary) VALUES (?,?,?,?,?,?,?,?,?,?,0)";
        db.execSQL(insert, new Object[] { 1, 600.0, BODY, T0, "DEBIT", "TEST SHOP", BODY, "AX-ICICIT-S", "Food", "old-hash" });
        db.execSQL(insert, new Object[] { 2, 55.0, "RCS: Shop", T0, "DEBIT", "Shop", "•  SBI CARDS  •  Shop", "SBI CARDS",
                "Default", "text-hash" });
        db.execSQL(insert, new Object[] { 3, 100.0, "tea", T0, "DEBIT", "cafe", "", "", "Food", "empty-hash" });
        db.execSQL(insert, new Object[] { 4, 200.0, "restored", T0, "DEBIT", "x", "null", "null", "Food", "null" });
        db.setVersion(6);
        db.close();
        assertNotNull(file);
    }
}
