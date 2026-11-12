package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.example.expensemate.service.GoogleDriveService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
        TestServices.over(source).categories().saveAsDefault(new Category("Fuel", "EXPENSE")).join();
        source.categoryDao().insertCategory(new Category("Default", "EXPENSE"));
        TestServices.over(source).categories().saveAsDefault(new Category("Salary", "INCOME")).join();
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
        source.transactionDao().insertTransaction(debit);
        source.transactionDao().insertTransaction(credit);

        for (String section : new String[] { "CATEGORIES", "ACCOUNTS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("Fuel", restored.categoryDao().getDefaultName("EXPENSE"));
        assertEquals("Salary", restored.categoryDao().getDefaultName("INCOME"));

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

        assertEquals("Default", restored.categoryDao().getDefaultName("EXPENSE"));
        assertEquals("Default", restored.categoryDao().getDefaultName("INCOME"));

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

    private Map<String, File> backupFiles() throws IOException {
        Map<String, File> files = new HashMap<>();
        for (String[] entry : BackupDataLoader.FILES) {
            File file = File.createTempFile("backup", ".txt", context.getCacheDir());
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(BackupDataLoader.entityText(entry[0], source));
            }
            files.put(entry[0], file);
        }
        return files;
    }

    @Test
    public void everyValueComesBackExactly() throws Exception {
        TestServices.over(source).categories().saveAsDefault(new Category("Default", "EXPENSE")).join();
        source.categoryDao().insertCategory(new Category("Fuel", "EXPENSE"));
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        Account noDetails = new Account("Bare", null, null, null, null);
        noDetails.setId(2);
        source.accountDao().insert(noDetails);
        account(3, "Bank", Account.TYPE_REAL, true);
        source.recurringPaymentDao().insert(new RecurringPayment("No end", 99.5, 3, null));

        // Lines that look like the backup's own keys and separators, inside a message
        String body = "Rs 10 debited\nAmount: 5000\n---\nC:\\path ends here\\n";
        Transaction captured = row(7, "DEBIT", 1L, 3L);
        captured.setAmount(1234.5678);
        captured.setDate(new Date(1793447463123L));
        captured.setSmsBody(body);
        captured.setSmsSender("AX-HDFC");
        captured.setReceiverName("Fuel Stop");
        captured.setCategory("Fuel");
        captured.setDescription("  padded  ");
        captured.setSource(TransactionKeys.SOURCE_MMS_OBSERVER);
        captured.setDedupKey("dedup");
        captured.setMatchKey("match");
        captured.setMessageTime(1793447460000L);
        source.transactionDao().insertTransaction(captured);
        Transaction manual = row(8, "CREDIT", 1L, 3L);
        manual.setDescription(null);
        manual.setReceiverName(null);
        source.transactionDao().insertTransaction(manual);
        source.transactionDao().saveCounterpartyCategory("someone", "DEBIT", "Fuel");

        new BackupDataLoader(context, restored).restore(backupFiles());

        Transaction back = find(restored, 7);
        assertEquals(body, back.getSmsBody());
        assertEquals(1234.5678, back.getAmount(), 0);
        assertEquals(1793447463123L, back.getDate().getTime());
        assertEquals("  padded  ", back.getDescription());
        assertEquals(TransactionKeys.SOURCE_MMS_OBSERVER, back.getSource());
        assertEquals("dedup", back.getDedupKey());
        assertEquals("match", back.getMatchKey());
        assertEquals(Long.valueOf(1793447460000L), back.getMessageTime());
        assertNull(find(restored, 8).getDescription());
        assertNull(find(restored, 8).getReceiverName());
        assertNull(find(restored, 8).getSmsBody());

        assertNull(findAccount(restored, "Bare").getDescription());
        RecurringPayment bill = restored.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);
        assertEquals("No end", bill.getName());
        assertNull(bill.getExpiryDate());
        assertEquals("Fuel", restored.transactionDao().getCounterpartyCategory("someone", "DEBIT"));
    }

    @Test
    public void currenciesSurviveABackupAndOlderBackupsAreRupees() throws Exception {
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        Account dirhams = new Account("Emirates NBD", "", "", null, "");
        dirhams.setId(2);
        dirhams.setType(Account.TYPE_REAL);
        dirhams.setDefault(true);
        dirhams.setCurrency("AED");
        source.accountDao().insert(dirhams);
        Transaction spent = row(1, "DEBIT", 1L, 2L);
        spent.setCurrency("AED");
        source.transactionDao().insertTransaction(spent);
        Loan loan = new Loan();
        loan.setId(1);
        loan.setPerson("Ravi");
        loan.setAmount(50);
        loan.setCurrency("AED");
        source.loanDao().insert(loan);
        RecurringPayment rent = new RecurringPayment("Rent", 900, 5, null);
        rent.setCurrency("USD");
        source.recurringPaymentDao().insert(rent);

        for (String section : new String[] { "RECURRING PAYMENTS", "ACCOUNTS", "LOANS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("AED", findAccount(restored, "Emirates NBD").getCurrency());
        assertNull(findAccount(restored, "Daily").getCurrency());
        assertEquals("AED", find(restored, 1).getCurrency());
        assertEquals("AED", restored.loanDao().getAllLoansSync().get(0).getCurrency());
        assertEquals("USD", restored.recurringPaymentDao().getAllRecurringPaymentsSync().get(0).getCurrency());
    }

    @Test
    public void aBackupWithoutCurrenciesComesBackInRupees() throws Exception {
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        account(2, "Bank", Account.TYPE_REAL, true);
        source.transactionDao().insertTransaction(row(1, "DEBIT", 1L, 2L));
        for (String section : new String[] { "ACCOUNTS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source).replaceAll("(?m)^Currency: .*\\R", ""));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("INR", findAccount(restored, "Bank").getCurrency());
        assertEquals("INR", find(restored, 1).getCurrency());
    }

    @Test
    public void loansComeBackWithTheirReturns() throws Exception {
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        Loan loan = new Loan();
        loan.setId(5);
        loan.setPerson("Ravi");
        loan.setAmount(5000);
        loan.setDate(new Date(1793447463000L));
        loan.setNote("for the trip\nback next month");
        loan.setReturnedManually(500);
        loan.setClosedManually(true);
        loan.setClosedDate(new Date(1793447469000L));
        source.loanDao().insert(loan);
        Transaction lent = row(1, "DEBIT", 1L, null);
        lent.setLoanId(5L);
        source.transactionDao().insertTransaction(lent);
        Transaction back = row(2, "CREDIT", 1L, null);
        back.setLoanId(5L);
        source.transactionDao().insertTransaction(back);

        new BackupDataLoader(context, restored).restore(backupFiles());

        Loan restoredLoan = restored.loanDao().getAllLoansSync().get(0);
        assertEquals(5, restoredLoan.getId());
        assertEquals("Ravi", restoredLoan.getPerson());
        assertEquals(5000, restoredLoan.getAmount(), 0);
        assertEquals(1793447463000L, restoredLoan.getDate().getTime());
        assertEquals("for the trip\nback next month", restoredLoan.getNote());
        assertEquals(500, restoredLoan.getReturnedManually(), 0);
        assertTrue(restoredLoan.isClosedManually());
        assertEquals(1793447469000L, restoredLoan.getClosedDate().getTime());
        assertEquals(Long.valueOf(5), find(restored, 1).getLoanId());
        assertEquals(Long.valueOf(5), find(restored, 2).getLoanId());
        assertEquals(500 + 102, restored.loanDao().getLoanSummariesSync().get(0).returned, 0.001);
    }

    @Test
    public void aBackupWithoutLoansStillRestores() throws Exception {
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        source.transactionDao().insertTransaction(row(1, "DEBIT", 1L, null));
        Map<String, File> files = backupFiles();
        files.remove("LOANS");

        new BackupDataLoader(context, restored).restore(files);

        assertTrue(restored.loanDao().getAllLoansSync().isEmpty());
        assertNull(find(restored, 1).getLoanId());
    }

    @Test
    public void aRestoreThatFailsLeavesTheDataAsItWas() throws Exception {
        account(1, "Daily", Account.TYPE_VIRTUAL, true);
        source.transactionDao().insertTransaction(row(1, "DEBIT", 1L, null));
        new BackupDataLoader(context, restored).restore(backupFiles());

        Map<String, File> broken = backupFiles();
        broken.put("TRANSACTIONS", new File(context.getCacheDir(), "missing.txt"));
        try {
            new BackupDataLoader(context, restored).restore(broken);
            throw new AssertionError("Expected the restore to fail");
        } catch (RuntimeException expected) {
            // The transactions file could not be read
        }
        assertEquals(1, restored.transactionDao().getAllTransactionsSyncOrderByDateAsc().size());
        assertEquals(1, restored.accountDao().getAllAccountsSync().stream().filter(a -> !a.isReal()).count());
    }

    @Test
    public void anOlderBackupWithCommaDecimalsAndNoBillEndStillRestores() throws Exception {
        load("ACCOUNTS", "ID: 1\nName: Daily\nAccount Number: \nBank: \nExpiry Date: null\nDescription: \nIs Default: true\n---\n");
        load("RECURRING PAYMENTS", "ID: 4\nName: Rent\nAmount: 2500,00\nDue Day: 5\nExpiry Date: null\nIs Completed: false\n"
                + "Last Completed Date: null\nFrom Account ID: null\nTo Account ID: null\n---\n");
        load("TRANSACTIONS", oldRow(1, "1").replace("Amount: 10.00", "Amount: 10,50"));
        BackupDataLoader.finishRestore(restored);

        assertEquals(10.5, find(restored, 1).getAmount(), 0);
        assertNull("The word null in an older backup is no longer kept as text", find(restored, 1).getSmsBody());
        RecurringPayment rent = restored.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);
        assertEquals(2500, rent.getAmount(), 0);
        assertNull(rent.getExpiryDate());
    }

    @Test
    public void onlyAFinishedBackupIsChosen() {
        assertTrue(GoogleDriveService.isComplete(new HashSet<>(Arrays.asList(
                "categories.txt", "accounts.txt", GoogleDriveService.COMPLETE_MARKER))));
        assertTrue("Made before the marker existed", GoogleDriveService.isComplete(new HashSet<>(Arrays.asList(
                "categories.txt", "recurring_payments.txt", "accounts.txt", "transactions.txt"))));
        assertFalse("Stopped partway", GoogleDriveService.isComplete(new HashSet<>(Arrays.asList(
                "categories.txt", "recurring_payments.txt", "accounts.txt"))));
    }

    private static String oldRow(long id, String accountId) {
        return "ID: " + id + "\nAmount: 10.00\nDescription: note\nDate: " + OLD_DATE
                + "\nTransaction Type: DEBIT\nReceiver: Shop\nCategory: Food\nIs excluded from summary: false\n"
                + "Account id: " + accountId + "\nLinked Payment ID: null\nSMS Body: null\nSMS Sender: null\n"
                + "SMS Hash: null\n---\n";
    }
}
