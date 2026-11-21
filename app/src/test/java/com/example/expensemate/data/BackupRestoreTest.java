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
import java.util.List;
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

    private void fund(long id, String name, boolean isDefault) {
        Fund fund = new Fund(name, "");
        fund.setId(id);
        fund.setDefault(isDefault);
        source.fundDao().insert(fund);
    }

    private void account(long id, String name, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setId(id);
        account.setCurrency("INR");
        account.setDefault(isDefault);
        source.accountDao().insert(account);
    }

    private File file(String text) throws IOException {
        File file = File.createTempFile("backup", ".txt", context.getCacheDir());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
        return file;
    }

    private Transaction row(long id, String type, Long fundId, Long accountId) {
        Transaction row = new Transaction();
        row.setId(id);
        row.setAmount(100 + id);
        row.setDate(new Date(1793447463000L + id * 1000));
        row.setDescription("note " + id);
        row.setReceiverName("Shop " + id);
        row.setTransactionType(type);
        row.setFundId(fundId);
        row.setAccountId(accountId);
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

    private Fund findFund(AppDatabase database, String name) {
        for (Fund fund : database.fundDao().getAllFundsSync()) {
            if (fund.getName().equals(name)) {
                return fund;
            }
        }
        throw new AssertionError("No fund " + name);
    }

    @Test
    public void everyFlagSurvivesABackupAndRestore() throws Exception {
        TestServices.over(source).categories().saveAsDefault(new Category("Fuel", "EXPENSE")).join();
        source.categoryDao().insertCategory(new Category("Default", "EXPENSE"));
        TestServices.over(source).categories().saveAsDefault(new Category("Salary", "INCOME")).join();
        fund(1, "Daily", true);
        fund(2, "Trip", false);
        account(3, "Bank", false);
        account(4, "Wallet", true);

        Transaction waiting = row(1, "DEBIT", 2L, 3L);
        waiting.setCategory("Fuel");
        waiting.setNeedsReview(true);
        source.transactionDao().insertTransaction(waiting);
        source.transactionDao().insertTransaction(row(2, "DEBIT", null, null));

        // A transfer that only moves between accounts
        Transaction debit = row(3, "DEBIT", 1L, 4L);
        Transaction credit = row(4, "CREDIT", 1L, 3L);
        debit.setTransferGroupId("group");
        credit.setTransferGroupId("group");
        source.transactionDao().insertTransaction(debit);
        source.transactionDao().insertTransaction(credit);

        for (String section : new String[] { "CATEGORIES", "FUNDS", "ACCOUNTS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("Fuel", restored.categoryDao().getDefaultName("EXPENSE"));
        assertEquals("Salary", restored.categoryDao().getDefaultName("INCOME"));

        assertEquals(2, restored.fundDao().getAllFundsSync().size());
        assertEquals(2, restored.accountDao().getAllAccountsSync().size());
        assertEquals(1, restored.fundDao().getDefaultFundSync().getId());
        assertEquals(4, restored.accountDao().getDefaultAccountSync().getId());

        Transaction restoredWaiting = find(restored, 1);
        assertEquals(Long.valueOf(2), restoredWaiting.getFundId());
        assertEquals(Long.valueOf(3), restoredWaiting.getAccountId());
        assertTrue(restoredWaiting.isNeedsReview());
        assertEquals("Fuel", restoredWaiting.getCategory());

        // A row without either gets the defaults
        Transaction noAccount = find(restored, 2);
        assertEquals(Long.valueOf(1), noAccount.getFundId());
        assertEquals(Long.valueOf(4), noAccount.getAccountId());
        assertFalse(noAccount.isNeedsReview());

        assertEquals("group", find(restored, 3).getTransferGroupId());
        assertEquals("group", find(restored, 4).getTransferGroupId());
        assertEquals(Long.valueOf(4), find(restored, 3).getAccountId());
        assertEquals(Long.valueOf(3), find(restored, 4).getAccountId());
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

        // Before account types every row of the accounts file was a fund
        assertEquals(2, restored.fundDao().getAllFundsSync().size());
        assertEquals("Trip", restored.fundDao().getFundByIdSync(2).getName());
        assertEquals(1, restored.fundDao().getDefaultFundSync().getId());
        Account account = restored.accountDao().getDefaultAccountSync();
        assertEquals(Account.DEFAULT_NAME, account.getName());
        assertEquals(1, restored.accountDao().getAllAccountsSync().size());
        Long accountId = account.getId();

        assertEquals(Long.valueOf(2), find(restored, 1).getFundId());
        assertEquals(accountId, find(restored, 1).getAccountId());
        // As it always has, an older backup's row without an account goes to the default one
        assertEquals(Long.valueOf(1), find(restored, 2).getFundId());
        assertEquals(accountId, find(restored, 2).getAccountId());
        // Its fund was deleted; it keeps pointing there, and still gets an account
        assertEquals(Long.valueOf(99), find(restored, 3).getFundId());
        assertEquals(accountId, find(restored, 3).getAccountId());
    }

    /** Written the way format 2 wrote backups, when funds and accounts shared the accounts file. */
    @Test
    public void aFormatTwoBackupRestoresIntoFundsAndAccounts() throws Exception {
        String accounts = "Format: 2\n---\n"
                + "ID: 1\nName: Daily\nAccount Number: \nBank: \nExpiry Date: \\N\nDescription: Everyday\n"
                + "Is Default: true\nAccount Type: VIRTUAL\nSort Order: 0\nCurrency: \\N\nTarget: \\N\nTarget Date: \\N\n---\n"
                + "ID: 2\nName: Trip\nAccount Number: \nBank: Travel card\nExpiry Date: \\N\nDescription: \n"
                + "Is Default: false\nAccount Type: VIRTUAL\nSort Order: 1\nCurrency: \\N\nTarget: 50000.5\n"
                + "Target Date: 1798761600000\n---\n"
                + "ID: 3\nName: Savings\nAccount Number: 1234\nBank: SBI\nExpiry Date: \\N\nDescription: salary\n"
                + "Is Default: true\nAccount Type: REAL\nSort Order: 0\nCurrency: AED\nTarget: \\N\nTarget Date: \\N\n---\n";
        String bills = "Format: 2\n---\n"
                + "ID: 7\nName: Trip savings\nAmount: 2000.0\nDue Day: 5\nExpiry Date: \\N\nIs Completed: false\n"
                + "Last Completed Date: \\N\nFrom Account ID: 1\nTo Account ID: 2\nCurrency: AED\n---\n";
        String transactions = "Format: 2\n---\n"
                + formatTwoRow(1, 120.0, "DEBIT", "Food", 1, 3, null)
                + formatTwoRow(2, 500.0, "DEBIT", "Transfer", 1, 3, "g1")
                + formatTwoRow(3, 500.0, "CREDIT", "Transfer", 2, 3, "g1");
        Map<String, File> files = new HashMap<>();
        files.put("ACCOUNTS", file(accounts));
        files.put("RECURRING PAYMENTS", file(bills));
        files.put("TRANSACTIONS", file(transactions));

        new BackupDataLoader(context, restored).restore(files);
        for (File file : files.values()) {
            file.delete();
        }

        assertEquals(2, restored.fundDao().getAllFundsSync().size());
        Fund daily = restored.fundDao().getDefaultFundSync();
        assertEquals(1, daily.getId());
        assertEquals("Daily", daily.getName());
        assertEquals("Everyday", daily.getDescription());
        assertNull(daily.getTarget());
        Fund trip = restored.fundDao().getFundByIdSync(2);
        assertEquals("Travel card", trip.getDescription()); // Its bank was its only note
        assertFalse(trip.isDefault());
        assertEquals(1, trip.getSortOrder());
        assertEquals(50000.5, trip.getTarget(), 0);
        assertEquals(1798761600000L, trip.getTargetDate().getTime());
        assertNull(restored.fundDao().getFundByIdSync(3));

        assertEquals(1, restored.accountDao().getAllAccountsSync().size());
        Account savings = restored.accountDao().getDefaultAccountSync();
        assertEquals(3, savings.getId());
        assertEquals("Savings", savings.getName());
        assertEquals("1234", savings.getAccountNumber());
        assertEquals("SBI", savings.getBank());
        assertEquals("salary", savings.getDescription());
        assertEquals("AED", savings.getCurrency());
        assertNull(restored.accountDao().getAccountByIdSync(1));

        Transaction lunch = find(restored, 1);
        assertEquals(Long.valueOf(1), lunch.getFundId());
        assertEquals(Long.valueOf(3), lunch.getAccountId());
        assertEquals("AED", lunch.getCurrency());
        assertEquals(Long.valueOf(1), find(restored, 2).getFundId());
        assertEquals(Long.valueOf(2), find(restored, 3).getFundId());
        assertEquals("g1", find(restored, 2).getTransferGroupId());
        assertEquals("g1", find(restored, 3).getTransferGroupId());
        assertEquals(Long.valueOf(3), find(restored, 3).getAccountId());

        RecurringPayment bill = restored.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);
        assertEquals(7, bill.getId());
        assertEquals(Long.valueOf(1), bill.getFromFundId());
        assertEquals(Long.valueOf(2), bill.getToFundId());
    }

    private static String formatTwoRow(long id, double amount, String type, String category, long fundId,
            long accountId, String group) {
        return "ID: " + id + "\nAmount: " + amount + "\nDescription: note\nDate: " + (1793447463000L + id)
                + "\nTransaction Type: " + type + "\nReceiver: \nCategory: " + category
                + "\nIs excluded from summary: false\nAccount id: " + fundId + "\nReal account id: " + accountId
                + "\nLinked Payment ID: \\N\nLoan ID: \\N\nSMS Body: \\N\nSMS Sender: \\N\nSMS Hash: \\N"
                + "\nTransfer Group: " + (group != null ? group : "\\N") + "\nNeeds Review: false\nSource: \\N"
                + "\nDedup Key: \\N\nMatch Key: \\N\nMessage Time: \\N\nCurrency: AED\n---\n";
    }

    /** A current backup names a transaction's fund and account with their own keys, and funds have their own file. */
    @Test
    public void aCurrentBackupWritesFundsAndAccountsApart() {
        fund(1, "Daily", true);
        account(1, "Bank", true);
        Transaction spent = row(1, "DEBIT", 1L, 1L);
        source.transactionDao().insertTransaction(spent);
        RecurringPayment bill = new RecurringPayment("Top up", 10, 1, null);
        bill.setFromFundId(1L);
        source.recurringPaymentDao().insert(bill);

        assertTrue(BackupDataLoader.entityText("FUNDS", source).startsWith("Format: 3\n"));
        assertTrue(BackupDataLoader.entityText("FUNDS", source).contains("Name: Daily\n"));
        assertFalse(BackupDataLoader.entityText("ACCOUNTS", source).contains("Account Type"));
        String rows = BackupDataLoader.entityText("TRANSACTIONS", source);
        assertTrue(rows.contains("Fund ID: 1\n"));
        assertTrue(rows.contains("Account ID: 1\n"));
        assertFalse(rows.contains("Real account id"));
        assertTrue(BackupDataLoader.entityText("RECURRING PAYMENTS", source).contains("From Fund ID: 1\n"));
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
        fund(1, "Daily", true);
        Account noDetails = new Account("Bare", null, null, null, null);
        noDetails.setId(2);
        source.accountDao().insert(noDetails);
        account(3, "Bank", true);
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
        fund(1, "Daily", true);
        Account dirhams = new Account("Emirates NBD", "", "", null, "");
        dirhams.setId(2);
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

        for (String section : new String[] { "FUNDS", "ACCOUNTS", "RECURRING PAYMENTS", "LOANS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("AED", findAccount(restored, "Emirates NBD").getCurrency());
        assertEquals(1, restored.accountDao().getAllAccountsSync().size()); // No default added on top
        assertEquals(1, findFund(restored, "Daily").getId());
        assertEquals("AED", find(restored, 1).getCurrency());
        assertEquals("AED", restored.loanDao().getAllLoansSync().get(0).getCurrency());
        assertEquals("USD", restored.recurringPaymentDao().getAllRecurringPaymentsSync().get(0).getCurrency());
    }

    @Test
    public void aBackupWithoutCurrenciesComesBackInRupees() throws Exception {
        fund(1, "Daily", true);
        account(2, "Bank", true);
        source.transactionDao().insertTransaction(row(1, "DEBIT", 1L, 2L));
        for (String section : new String[] { "FUNDS", "ACCOUNTS", "TRANSACTIONS" }) {
            load(section, BackupDataLoader.entityText(section, source).replaceAll("(?m)^Currency: .*\\R", ""));
        }
        BackupDataLoader.finishRestore(restored);

        assertEquals("INR", findAccount(restored, "Bank").getCurrency());
        assertEquals("INR", find(restored, 1).getCurrency());
    }

    @Test
    public void loansComeBackWithTheirReturns() throws Exception {
        fund(1, "Daily", true);
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
    public void messagePatternsComeBackAsTheyWere() throws Exception {
        MessagePattern mine = new MessagePattern("My bank", "CREDIT", "(?i)got (?<amount>\\d+)\nfrom (?<merchant>\\w+)");
        mine.setCounterparty("Bank: {merchant}");
        mine.setExcludeFromSummary(true);
        mine.setSortOrder(-10);
        source.messagePatternDao().insert(mine);
        MessagePattern shipped = new MessagePattern("Off one", "DEBIT", "x(?<amount>\\d+)");
        shipped.setBuiltinKey("icici_debit");
        shipped.setActive(false);
        shipped.setSortOrder(10);
        source.messagePatternDao().insert(shipped);

        new BackupDataLoader(context, restored).restore(backupFiles());

        java.util.List<MessagePattern> back = restored.messagePatternDao().getAllSync();
        assertEquals(2, back.size()); // The shipped set is not added on top of a backup that has patterns
        assertEquals("My bank", back.get(0).getName());
        assertEquals(mine.getPattern(), back.get(0).getPattern());
        assertEquals("CREDIT", back.get(0).getTransactionType());
        assertEquals("Bank: {merchant}", back.get(0).getCounterparty());
        assertTrue(back.get(0).isExcludeFromSummary());
        assertTrue(back.get(0).isActive());
        assertNull(back.get(0).getBuiltinKey());
        assertFalse(back.get(1).isActive());
        assertEquals("icici_debit", back.get(1).getBuiltinKey());
    }

    @Test
    public void budgetsAndFundTargetsComeBack() throws Exception {
        fund(1, "Daily", true);
        Fund trip = new Fund("Trip", "");
        trip.setId(2);
        trip.setTarget(50000.5);
        trip.setTargetDate(new Date(1798761600000L));
        source.fundDao().insert(trip);
        Budget food = new Budget();
        food.setId(7);
        food.setName("Food");
        food.setAmount(6000);
        food.setCurrency("AED");
        food.setSortOrder(3);
        source.budgetDao().insert(food);
        source.budgetDao().insertCategory(new BudgetCategory(7, "Outside Food"));
        source.budgetDao().insertCategory(new BudgetCategory(7, "Food"));

        new BackupDataLoader(context, restored).restore(backupFiles());

        Fund back = restored.fundDao().getFundByIdSync(2);
        assertEquals(50000.5, back.getTarget(), 0);
        assertEquals(1798761600000L, back.getTargetDate().getTime());
        assertNull(restored.fundDao().getFundByIdSync(1).getTarget());
        Budget budget = restored.budgetDao().getAllSync().get(0);
        assertEquals(7, budget.getId());
        assertEquals("Food", budget.getName());
        assertEquals(6000, budget.getAmount(), 0);
        assertEquals("AED", budget.getCurrency());
        assertEquals(3, budget.getSortOrder());
        BudgetProgress progress = restored.budgetDao().getProgressSync(new Date(0), new Date(Long.MAX_VALUE)).get(0);
        assertEquals(Arrays.asList("Food", "Outside Food"), progress.categories());
    }

    @Test
    public void aBackupWithoutBudgetsStillRestores() throws Exception {
        fund(1, "Daily", true);
        Map<String, File> files = backupFiles();
        files.remove("BUDGETS");

        new BackupDataLoader(context, restored).restore(files);

        assertTrue(restored.budgetDao().getAllSync().isEmpty());
        assertEquals(1, restored.fundDao().getAllFundsSync().size());
    }

    @Test
    public void aBackupWithoutPatternsGetsTheShippedOnes() throws Exception {
        fund(1, "Daily", true);
        Map<String, File> files = backupFiles();
        files.remove("MESSAGE PATTERNS");

        new BackupDataLoader(context, restored).restore(files);

        assertEquals(DefaultMessagePatterns.all().size(), restored.messagePatternDao().getActiveSync().size());
    }

    @Test
    public void aBackupWithoutLoansStillRestores() throws Exception {
        fund(1, "Daily", true);
        source.transactionDao().insertTransaction(row(1, "DEBIT", 1L, null));
        Map<String, File> files = backupFiles();
        files.remove("LOANS");

        new BackupDataLoader(context, restored).restore(files);

        assertTrue(restored.loanDao().getAllLoansSync().isEmpty());
        assertNull(find(restored, 1).getLoanId());
    }

    @Test
    public void billMonthsComeBackAsTheyWere() throws Exception {
        fund(1, "Daily", true);
        RecurringPayment gym = new RecurringPayment("Gym", 900, 5, null);
        gym.setId(4);
        gym.setStartPeriod(202605);
        source.recurringPaymentDao().insert(gym);
        BillPayment may = BillPayment.paid(gym, 202605, new Date(1780000000000L), 7L);
        may.setAmount(850);
        source.recurringPaymentDao().insertPayment(may);
        source.recurringPaymentDao().insertPayment(BillPayment.skipped(gym, 202606));
        // Linked, but June was skipped on purpose: the restore must not mark it paid from the link
        Transaction june = row(7, "DEBIT", 1L, null);
        june.setLinkedRecurringPaymentId(4L);
        source.transactionDao().insertTransaction(june);

        new BackupDataLoader(context, restored).restore(backupFiles());

        RecurringPaymentDao dao = restored.recurringPaymentDao();
        assertEquals(202605, dao.getByIdSync(4).getStartPeriod());
        assertEquals(2, dao.getAllPaymentsSync().size());
        BillPayment back = dao.getPaymentSync(4, 202605);
        assertEquals(850, back.getAmount(), 0);
        assertEquals(1780000000000L, back.getPaidOn().getTime());
        assertEquals(Long.valueOf(7), back.getTransactionId());
        assertTrue(dao.getPaymentSync(4, 202606).isSkipped());
    }

    @Test
    public void aBackupFromBeforeBillMonthsGetsThemFromItsLinks() throws Exception {
        load("ACCOUNTS", "ID: 1\nName: Daily\nAccount Number: \nBank: \nExpiry Date: null\nDescription: \nIs Default: true\n---\n");
        load("RECURRING PAYMENTS", "ID: 4\nName: Rent\nAmount: 2500\nDue Day: 5\nExpiry Date: null\nIs Completed: false\n"
                + "Last Completed Date: null\nFrom Account ID: null\nTo Account ID: null\n---\n");
        load("TRANSACTIONS", oldRow(1, "1").replace("Linked Payment ID: null", "Linked Payment ID: 4"));
        BackupDataLoader.finishRestore(restored);

        List<BillPayment> months = restored.recurringPaymentDao().getAllPaymentsSync();
        assertEquals(1, months.size());
        assertEquals(Long.valueOf(1), months.get(0).getTransactionId());
        assertEquals(com.example.expensemate.util.Periods.current(),
                restored.recurringPaymentDao().getByIdSync(4).getStartPeriod());
    }

    @Test
    public void aRestoreThatFailsLeavesTheDataAsItWas() throws Exception {
        fund(1, "Daily", true);
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
        assertEquals(1, restored.fundDao().getAllFundsSync().size());
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
        assertTrue("One zip", GoogleDriveService.isComplete(new HashSet<>(Arrays.asList(
                GoogleDriveService.BACKUP_ZIP))));
        assertFalse("Empty folder", GoogleDriveService.isComplete(new HashSet<>()));
    }

    @Test
    public void aZippedBackupRestoresAndIsSmaller() throws Exception {
        fund(1, "Daily", true);
        account(3, "Bank", false);
        source.categoryDao().insertCategory(new Category("Fuel", "EXPENSE"));
        for (long id = 1; id <= 200; id++) {
            Transaction t = row(id, "DEBIT", 1L, 3L);
            t.setCategory("Fuel");
            t.setSmsBody("Dear Customer, Rs." + (100 + id) + " debited from A/c XX1234 on 12-11-26 to VPA shop"
                    + id + "@upi. Ref " + (900000 + id) + ". Not you? Call 1800-000-000\nbank");
            t.setSmsSender("AD-BANK");
            source.transactionDao().insertTransaction(t);
        }

        File zip = File.createTempFile("backup", ".zip", context.getCacheDir());
        BackupDataLoader.writeZip(source, zip);
        long plain = 0;
        for (String[] entry : BackupDataLoader.FILES) {
            plain += BackupDataLoader.entityText(entry[0], source).getBytes("UTF-8").length;
        }
        assertTrue("Zip " + zip.length() + " bytes vs " + plain + " plain", zip.length() * 4 < plain);

        Map<String, File> files = BackupDataLoader.readZip(zip, context.getCacheDir());
        assertEquals(BackupDataLoader.FILES.length, files.size());
        new BackupDataLoader(context, restored).restore(files);
        for (File file : files.values()) {
            file.delete();
        }
        zip.delete();

        assertEquals(200, restored.transactionDao().getAllTransactionsSyncOrderByDateAsc().size());
        Transaction back = find(restored, 7);
        assertEquals(find(source, 7).getSmsBody(), back.getSmsBody());
        assertEquals("Fuel", back.getCategory());
        assertEquals(Long.valueOf(3), back.getAccountId());
    }

    private static GoogleDriveService.BackupFolder folder(String id, String... names) {
        Map<String, String> files = new HashMap<>();
        for (String name : names) {
            files.put(name, name);
        }
        return new GoogleDriveService.BackupFolder(id, files);
    }

    private static java.util.List<String> ids(java.util.List<GoogleDriveService.BackupFolder> folders) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (GoogleDriveService.BackupFolder f : folders) {
            ids.add(f.id);
        }
        return ids;
    }

    @Test
    public void onlyTheNewestFinishedBackupsAreKept() {
        String zip = GoogleDriveService.BACKUP_ZIP;
        java.util.List<GoogleDriveService.BackupFolder> newestFirst = Arrays.asList(
                folder("uploading"),                 // newer than any finished one: may still be going
                folder("a", zip), folder("b", zip),
                folder("broken"),                    // a leftover older than a finished backup
                folder("c", GoogleDriveService.COMPLETE_MARKER, "accounts.txt"),
                folder("d", zip), folder("e", zip), folder("f", zip));

        assertEquals(Arrays.asList("broken", "f"), ids(GoogleDriveService.toPrune(newestFirst, 5)));
        assertEquals(Arrays.asList("b", "broken", "c", "d", "e", "f"),
                ids(GoogleDriveService.toPrune(newestFirst, 1)));
        assertTrue("Nothing finished, nothing goes",
                GoogleDriveService.toPrune(Arrays.asList(folder("x"), folder("y")), 1).isEmpty());
    }

    private static String oldRow(long id, String accountId) {
        return "ID: " + id + "\nAmount: 10.00\nDescription: note\nDate: " + OLD_DATE
                + "\nTransaction Type: DEBIT\nReceiver: Shop\nCategory: Food\nIs excluded from summary: false\n"
                + "Account id: " + accountId + "\nLinked Payment ID: null\nSMS Body: null\nSMS Sender: null\n"
                + "SMS Hash: null\n---\n";
    }
}
