package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupRestoreTest {
    private static final String OLD_DATE = "Sun Nov 01 10:00:00 GMT 2026";

    private Context context;
    private AppDatabase source;
    private AppDatabase restored;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        source = newDatabase();
        restored = newDatabase();
    }

    @After
    public void tearDown() {
        source.close();
        restored.close();
    }

    private AppDatabase newDatabase() {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
    }

    private void load(String section, String text) throws IOException {
        File file = File.createTempFile("backup", ".txt", context.getCacheDir());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
        new BackupDataLoader(context, restored).loadSingleEntityFromFile(file, section);
        file.delete();
    }

    private void account(long id, String name, String type, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setId(id);
        account.setType(type);
        account.setDefault(isDefault);
        source.accountDao().insert(account);
    }

    private Transaction row(long id, String type, Long virtual, Long real) {
        Transaction row = new Transaction();
        row.setId(id);
        row.setAmount(100 + id);
        row.setDate(new Date(1793447463000L + id * 1000));
        row.setDescription("note " + id);
        row.setReceiverName("Shop " + id);
        row.setTransactionType(type);
        row.setAccountId(virtual);
        row.setRealAccountId(real);
        return row;
    }

    private Transaction find(AppDatabase database, long id) {
        for (Transaction row : database.transactionDao().getAllTransactionsSyncOrderByDateAsc()) {
            if (row.getId() == id) {
                return row;
            }
        }
        throw new AssertionError("No transaction " + id);
    }

    private Account findAccount(AppDatabase database, String name) {
        for (Account account : database.accountDao().getAllAccountsSync()) {
            if (account.getName().equals(name)) {
                return account;
            }
        }
        throw new AssertionError("No account " + name);
    }

    @Test
    public void everyFlagSurvivesABackupAndRestore() throws Exception {
        source.categoryDao().saveAsDefault(new Category("Fuel", "EXPENSE"));
        source.categoryDao().insertCategory(new Category("Default", "EXPENSE"));
        source.categoryDao().saveAsDefault(new Category("Salary", "INCOME"));
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        account(2, "Trip", Account.TYPE_VIRTUAL, false);
        account(3, "Bank", Account.TYPE_REAL, false);
        account(4, "Wallet", Account.TYPE_REAL, true);

        Transaction waiting = row(1, "DEBIT", 2L, 3L);
        waiting.setCategory("Fuel");
        waiting.setNeedsReview(true);
        source.transactionDao().insertTransaction(waiting);
        source.transactionDao().insertTransaction(row(2, "DEBIT", null, null));

        // A transfer that only moves between real accounts
        Transaction debit = row(3, "DEBIT", 1L, 4L);
        Transaction credit = row(4, "CREDIT", 1L, 3L);
        debit.setTransferGroupId("group");
        credit.setTransferGroupId("group");
        source.transactionDao().insertTransfer(debit, credit);

        for (String section : new String[] { "CATEGORIES", "ACCOUNTS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("Fuel", restored.transactionDao().getDefaultCategory("DEBIT"));
        assertEquals("Salary", restored.transactionDao().getDefaultCategory("CREDIT"));

        assertEquals(4, restored.accountDao().getAllAccountsSync().size());
        assertTrue(findAccount(restored, "Bank").isReal());
        assertFalse(findAccount(restored, "Trip").isReal());
        assertEquals(1, restored.accountDao().getDefaultAccountSync().getId());
        assertEquals(4, restored.accountDao().getDefaultRealAccountSync().getId());

        Transaction restoredWaiting = find(restored, 1);
        assertEquals(Long.valueOf(2), restoredWaiting.getAccountId());
        assertEquals(Long.valueOf(3), restoredWaiting.getRealAccountId());
        assertTrue(restoredWaiting.isNeedsReview());
        assertEquals("Fuel", restoredWaiting.getCategory());

        Transaction noAccount = find(restored, 2);
        assertNull(noAccount.getAccountId());
        assertNull(noAccount.getRealAccountId());
        assertFalse(noAccount.isNeedsReview());

        assertEquals("group", find(restored, 3).getTransferGroupId());
        assertEquals("group", find(restored, 4).getTransferGroupId());
        assertEquals(Long.valueOf(4), find(restored, 3).getRealAccountId());
        assertEquals(Long.valueOf(3), find(restored, 4).getRealAccountId());
    }

    /** Written the way the app wrote backups before account types and default categories. */
    @Test
    public void anOlderBackupStillRestores() throws Exception {
        load("CATEGORIES", "ID: 1\nName: Default\nType: EXPENSE\n---\nID: 2\nName: Food\nType: EXPENSE\n---\n"
                + "ID: 3\nName: Salary\nType: INCOME\n---\n");
        load("ACCOUNTS", "ID: 1\nName: Daily\nAccount Number: \nBank: \nExpiry Date: null\nDescription: \nIs Default: true\n---\n"
                + "ID: 2\nName: Trip\nAccount Number: \nBank: \nExpiry Date: null\nDescription: \nIs Default: false\n---\n");
        load("TRANSACTIONS", oldRow(1, "2") + oldRow(2, "null") + oldRow(3, "99"));
        BackupDataLoader.finishRestore(restored);

        assertEquals("Default", restored.transactionDao().getDefaultCategory("DEBIT"));
        assertEquals("Default", restored.transactionDao().getDefaultCategory("CREDIT"));

        assertFalse(findAccount(restored, "Daily").isReal());
        assertFalse(findAccount(restored, "Trip").isReal());
        assertEquals(1, restored.accountDao().getDefaultAccountSync().getId());
        Long real = restored.accountDao().getDefaultRealAccountSync().getId();

        assertEquals(Long.valueOf(2), find(restored, 1).getAccountId());
        assertEquals(real, find(restored, 1).getRealAccountId());
        // As it always has, an older backup's row without an account goes to the default one
        assertEquals(Long.valueOf(1), find(restored, 2).getAccountId());
        assertEquals(real, find(restored, 2).getRealAccountId());
        // Its account was deleted, so there is no virtual account to pair a real one with
        assertEquals(Long.valueOf(99), find(restored, 3).getAccountId());
        assertNull(find(restored, 3).getRealAccountId());
    }

    private static String oldRow(long id, String accountId) {
        return "ID: " + id + "\nAmount: 10.00\nDescription: note\nDate: " + OLD_DATE
                + "\nTransaction Type: DEBIT\nReceiver: Shop\nCategory: Food\nIs excluded from summary: false\n"
                + "Account id: " + accountId + "\nLinked Payment ID: null\nSMS Body: null\nSMS Sender: null\n"
                + "SMS Hash: null\n---\n";
    }
}
