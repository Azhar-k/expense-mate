package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccountTypesTest {
    private static final long T0 = 1793447463000L;

    private AppDatabase database;
    private TransactionDao dao;
    private AccountDao accounts;
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
        accounts = database.accountDao();
        daily = add("Daily", Account.TYPE_VIRTUAL, true);
        trip = add("Trip", Account.TYPE_VIRTUAL, false);
        bank = add("Bank", Account.TYPE_REAL, true);
        wallet = add("Wallet", Account.TYPE_REAL, false);
    }

    @After
    public void tearDown() {
        database.close();
    }

    private long add(String name, String type, boolean isDefault) {
        Account account = new Account(name, "", "", null, "");
        account.setType(type);
        account.setDefault(isDefault);
        accounts.insert(account);
        for (Account saved : accounts.getAllAccountsSync()) {
            if (saved.getName().equals(name)) {
                return saved.getId();
            }
        }
        throw new AssertionError(name);
    }

    private void insert(double amount, String type, long virtual, long real) {
        Transaction row = new Transaction();
        row.setAmount(amount);
        row.setTransactionType(type);
        row.setDate(new Date(T0));
        row.setAccountId(virtual);
        row.setRealAccountId(real);
        dao.insertTransaction(row);
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

    @Test
    public void eachTypeKeepsItsOwnDefault() {
        Account walletAccount = find("Wallet");
        accounts.makeDefault(walletAccount);

        assertEquals(wallet, accounts.getDefaultRealAccountSync().getId());
        assertEquals(daily, accounts.getDefaultAccountSync().getId());
        assertFalse(find("Bank").isDefault());
    }

    @Test
    public void virtualAccountsAreListedFirst() {
        List<Account> all = accounts.getAllAccountsSync();
        assertFalse(all.get(0).isReal());
        assertFalse(all.get(1).isReal());
        assertTrue(all.get(2).isReal());
        assertTrue(all.get(3).isReal());
    }

    @Test
    public void aRowCountsTowardsBothOfItsAccounts() throws Exception {
        insert(100, "CREDIT", daily, bank);
        insert(30, "DEBIT", trip, bank);
        insert(5, "DEBIT", daily, wallet);

        double[] balances = new double[(int) wallet + 1];
        for (AccountBalance balance : value(dao.getAccountBalances())) {
            balances[(int) balance.accountId] = balance.balance;
        }
        assertEquals(95, balances[(int) daily], 0.001);
        assertEquals(-30, balances[(int) trip], 0.001);
        assertEquals(70, balances[(int) bank], 0.001);
        assertEquals(-5, balances[(int) wallet], 0.001);
        assertEquals(65, value(dao.getNetBalance()), 0.001);
    }

    @Test
    public void listsAndTotalsFindARowByEitherAccount() throws Exception {
        insert(30, "DEBIT", trip, bank);
        insert(5, "DEBIT", daily, wallet);
        Date from = new Date(T0 - 1000);
        Date to = new Date(T0 + 1000);

        assertEquals(1, value(dao.getTransactionsForAccountDetailsScreen(from, to, bank)).size());
        assertEquals(1, value(dao.getTransactionsForAccountDetailsScreen(from, to, trip)).size());
        assertEquals(30, value(dao.getSummaryTotals(from, to, bank)).expense, 0.001);
        assertEquals(5, value(dao.getSummaryTotals(from, to, daily)).expense, 0.001);
    }

    @Test
    public void movingARowFromADeletedAccountTouchesOnlyThatSide() {
        insert(30, "DEBIT", trip, wallet);

        dao.moveToRealAccount(wallet, bank);

        Transaction row = dao.getAllTransactionsSyncOrderByDateAsc().get(0);
        assertEquals(Long.valueOf(bank), row.getRealAccountId());
        assertEquals(Long.valueOf(trip), row.getAccountId());
    }

    @Test
    public void assigningAVirtualAccountToARowWithoutOneAlsoGivesItTheDefaultRealAccount() {
        Transaction row = new Transaction();
        row.setId(50);
        row.setAmount(10);
        row.setTransactionType("DEBIT");
        dao.insertTransaction(row);

        dao.setAccount(50, trip);

        Transaction saved = dao.getAllTransactionsSyncOrderByDateAsc().get(0);
        assertEquals(Long.valueOf(trip), saved.getAccountId());
        assertEquals(Long.valueOf(bank), saved.getRealAccountId());
    }

    @Test
    public void assigningAVirtualAccountKeepsAChosenRealAccount() {
        insert(10, "DEBIT", daily, wallet);
        long id = dao.getAllTransactionsSyncOrderByDateAsc().get(0).getId();

        dao.setAccount(id, trip);

        assertEquals(Long.valueOf(wallet), dao.getAllTransactionsSyncOrderByDateAsc().get(0).getRealAccountId());
    }

    @Test
    public void migrationMakesEveryAccountVirtualAndSkipsRowsWithoutAVirtualAccount() {
        database.close();
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath("migration-10-11.db");
        file.getParentFile().mkdirs();
        file.delete();
        createVersion10(file);

        database = Room.databaseBuilder(context, AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12)
                .allowMainThreadQueries()
                .build();
        accounts = database.accountDao();

        List<Account> all = accounts.getAllAccountsSync();
        assertEquals(3, all.size());
        assertFalse(find("Daily").isReal());
        assertFalse(find("Trip").isReal());
        assertEquals(1L, accounts.getDefaultAccountSync().getId());

        Account real = accounts.getDefaultRealAccountSync();
        assertEquals(Account.DEFAULT_REAL_NAME, real.getName());
        assertTrue(real.isReal());

        List<Transaction> rows = database.transactionDao().getAllTransactionsSyncOrderByDateAsc();
        assertEquals(3, rows.size());
        for (Transaction row : rows) {
            if (row.getId() == 1) {
                assertEquals(Long.valueOf(real.getId()), row.getRealAccountId());
            } else {
                assertEquals("No or deleted virtual account", null, row.getRealAccountId());
            }
        }
    }

    private Account find(String name) {
        for (Account account : accounts.getAllAccountsSync()) {
            if (account.getName().equals(name)) {
                return account;
            }
        }
        throw new AssertionError(name);
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
