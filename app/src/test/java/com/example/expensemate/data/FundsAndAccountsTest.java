package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Funds and accounts are separate tables, so their ids can be equal and must never be mixed up. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FundsAndAccountsTest {
    private static final long T0 = 1793447463000L;

    private AppDatabase database;
    private TransactionDao dao;
    private long daily;
    private long trip;
    private long bank;
    private long wallet;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.transactionDao();
        daily = addFund("Daily", true);
        trip = addFund("Trip", false);
        bank = addAccount("Bank", true);
        wallet = addAccount("Wallet", false);
    }

    @After
    public void tearDown() {
        database.close();
    }

    private long addFund(String name, boolean isDefault) {
        Fund fund = new Fund(name, "");
        fund.setDefault(isDefault);
        return database.fundDao().insert(fund);
    }

    private long addAccount(String name, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setCurrency("INR");
        account.setDefault(isDefault);
        return database.accountDao().insert(account);
    }

    private long insert(double amount, String type, long fundId, long accountId) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType(type);
        row.setDate(new Date(T0));
        row.setFundId(fundId);
        row.setAccountId(accountId);
        dao.insertTransaction(row);
        List<Transaction> rows = dao.getAllTransactionsSyncOrderByDateAsc();
        long newest = 0;
        for (Transaction saved : rows) {
            newest = Math.max(newest, saved.getId());
        }
        return newest;
    }

    private static <T> T value(LiveData<T> liveData) throws InterruptedException {
        @SuppressWarnings("unchecked")
        final T[] value = (T[]) new Object[1];
        androidx.lifecycle.Observer<T> observer = v -> value[0] = v;
        liveData.observeForever(observer);
        for (int i = 0; i < 200 && value[0] == null; i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            if (value[0] == null) {
                Thread.sleep(10);
            }
        }
        liveData.removeObserver(observer);
        return value[0];
    }

    private static Map<Long, Double> byId(List<Balance> balances) {
        Map<Long, Double> byId = new HashMap<>();
        for (Balance balance : balances) {
            byId.merge(balance.id, balance.balance, Double::sum);
        }
        return byId;
    }

    @Test
    public void idsStartOverInEachTable() {
        assertEquals(daily, bank);
        assertEquals(trip, wallet);
    }

    @Test
    public void eachTableKeepsItsOwnDefault() {
        TestServices.over(database).accounts().makeDefault(database.accountDao().getAccountByIdSync(wallet)).join();

        assertEquals(wallet, database.accountDao().getDefaultAccountSync().getId());
        assertEquals(daily, database.fundDao().getDefaultFundSync().getId());
        assertFalse(database.accountDao().getAccountByIdSync(bank).isDefault());
    }

    @Test
    public void aRowCountsTowardsItsFundAndItsAccount() throws Exception {
        insert(100, "CREDIT", daily, bank);
        insert(30, "DEBIT", trip, bank);
        insert(5, "DEBIT", daily, wallet);

        Map<Long, Double> funds = byId(value(dao.getFundBalances()));
        assertEquals(95, funds.get(daily), 0.001);
        assertEquals(-30, funds.get(trip), 0.001);
        Map<Long, Double> accounts = byId(value(dao.getAccountBalances()));
        assertEquals(70, accounts.get(bank), 0.001);
        assertEquals(-5, accounts.get(wallet), 0.001);
        assertEquals(65, value(dao.getNetBalances()).get(0).balance, 0.001);
    }

    @Test
    public void listsAndTotalsFindARowByItsFundOrItsAccount() throws Exception {
        // Trip and Wallet share an id, as do Daily and Bank
        insert(30, "DEBIT", trip, bank);
        insert(5, "DEBIT", daily, wallet);
        Date from = new Date(T0 - 1000);
        Date to = new Date(T0 + 1000);

        assertEquals(30, value(dao.getTransactionsForFund(from, to, trip)).get(0).getAmount(), 0.001);
        assertEquals(5, value(dao.getTransactionsForAccount(from, to, wallet)).get(0).getAmount(), 0.001);
        assertEquals(1, value(dao.getTransactionsForFund(from, to, trip)).size());
        assertEquals(1, value(dao.getTransactionsForAccount(from, to, wallet)).size());
        assertEquals(30, value(dao.getSummaryTotals(from, to, trip, null)).get(0).expense, 0.001);
        assertEquals(30, value(dao.getSummaryTotals(from, to, null, bank)).get(0).expense, 0.001);
        assertEquals(5, value(dao.getSummaryTotals(from, to, daily, null)).get(0).expense, 0.001);
        assertEquals(5, value(dao.getSummaryTotals(from, to, null, wallet)).get(0).expense, 0.001);
    }

    @Test
    public void movingRowsOffAnAccountTouchesOnlyTheAccount() {
        insert(30, "DEBIT", trip, wallet);

        dao.moveToAccount(wallet, bank);

        Transaction row = dao.getAllTransactionsSyncOrderByDateAsc().get(0);
        assertEquals(Long.valueOf(bank), row.getAccountId());
        assertEquals(Long.valueOf(trip), row.getFundId());
    }

    @Test
    public void movingRowsOffAFundTouchesOnlyTheFund() {
        insert(30, "DEBIT", trip, wallet);

        dao.moveToFund(trip, daily);

        Transaction row = dao.getAllTransactionsSyncOrderByDateAsc().get(0);
        assertEquals(Long.valueOf(daily), row.getFundId());
        assertEquals(Long.valueOf(wallet), row.getAccountId());
    }

    @Test
    public void choosingAFundKeepsTheAccount() {
        long id = insert(10, "DEBIT", daily, wallet);

        TestServices.over(database).transactions().setFund(id, trip).join();

        Transaction saved = dao.getTransactionSync(id);
        assertEquals(Long.valueOf(trip), saved.getFundId());
        assertEquals(Long.valueOf(wallet), saved.getAccountId());
    }

    /** From before account types, through the real accounts of version 11, to separate tables. */
    @Test
    public void migrationFromVersion10MakesEveryOldAccountAFundAndAddsADefaultAccount() {
        database.close();
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-10-18.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion10(file);

        database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
                        AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16,
                        AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19)
                .allowMainThreadQueries()
                .build();

        List<Fund> funds = database.fundDao().getAllFundsSync();
        assertEquals(2, funds.size());
        assertEquals(1L, database.fundDao().getDefaultFundSync().getId());
        assertEquals("Trip", database.fundDao().getFundByIdSync(2).getName());

        List<Account> accounts = database.accountDao().getAllAccountsSync();
        assertEquals(1, accounts.size());
        Account account = database.accountDao().getDefaultAccountSync();
        assertNotNull(account);
        assertEquals(Account.DEFAULT_NAME, account.getName());
        assertEquals("INR", account.getCurrency());

        // Every row ends up with a fund and an account; one whose fund was deleted keeps pointing at it
        dao = database.transactionDao();
        assertEquals(3, dao.getAllTransactionsSyncOrderByDateAsc().size());
        assertEquals(Long.valueOf(1), dao.getTransactionSync(1).getFundId());
        assertEquals(Long.valueOf(1), dao.getTransactionSync(2).getFundId());
        assertEquals(Long.valueOf(99), dao.getTransactionSync(3).getFundId());
        for (long id = 1; id <= 3; id++) {
            assertEquals(Long.valueOf(account.getId()), dao.getTransactionSync(id).getAccountId());
        }
    }

    private static void createVersion10(File file) {
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
                + "`type` TEXT NOT NULL, `isDefault` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE `recurring_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`amount` REAL NOT NULL, `dueDay` INTEGER NOT NULL, `expiryDate` INTEGER, "
                + "`isCompleted` INTEGER NOT NULL, `lastCompletedDate` INTEGER, fromAccountId INTEGER, toAccountId INTEGER)");
        db.execSQL("CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, "
                + "`accountNumber` TEXT, `bank` TEXT, `expiryDate` INTEGER, `description` TEXT, `isDefault` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE `counterparty_categories` (`counterparty` TEXT NOT NULL, `transactionType` TEXT NOT NULL, "
                + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`counterparty`, `transactionType`))");
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");

        db.execSQL("INSERT INTO accounts (id, name, isDefault) VALUES (1, 'Daily', 1), (2, 'Trip', 0)");
        String insert = "INSERT INTO transactions (id, amount, date, transactionType, accountId, "
                + "isExcludedFromSummary, needsReview) VALUES (?,?,?,?,?,0,0)";
        db.execSQL(insert, new Object[] { 1, 10.0, T0, "DEBIT", 1 });
        db.execSQL(insert, new Object[] { 2, 10.0, T0 + 1000, "DEBIT", null });
        db.execSQL(insert, new Object[] { 3, 10.0, T0 + 2000, "DEBIT", 99 });
        db.setVersion(10);
        db.close();
    }
}
