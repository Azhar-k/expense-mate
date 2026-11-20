package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Migration17To18Test {
    private static final long T0 = 1793447463000L;
    private static final long TRIP_DATE = 1798761600000L;

    /** Opening through Room also checks the rebuilt tables against the entities. */
    @Test
    public void fundsAndAccountsMoveApartAndKeepTheirIds() throws Exception {
        File file = newFile("migration-17-18.db");
        SQLiteDatabase db = createVersion17(file);
        db.execSQL("INSERT INTO accounts (id, name, accountNumber, bank, description, isDefault, type, sortOrder) "
                + "VALUES (1, 'Daily', '', '', 'Day to day', 1, 'VIRTUAL', 0)");
        // Funds once had a bank field; it becomes the note when there is none
        db.execSQL("INSERT INTO accounts (id, name, accountNumber, bank, description, isDefault, type, sortOrder, "
                + "target, targetDate) VALUES (2, 'Trip', '', 'Travel card', '', 0, 'VIRTUAL', 2, 50000.5, "
                + TRIP_DATE + ")");
        db.execSQL("INSERT INTO accounts (id, name, accountNumber, bank, expiryDate, description, isDefault, type, "
                + "sortOrder, currency) VALUES (3, 'Savings', '1234', 'SBI', 1800000000000, 'salary', 1, 'REAL', 0, 'INR')");
        db.execSQL("INSERT INTO accounts (id, name, accountNumber, bank, description, isDefault, type, sortOrder, "
                + "currency) VALUES (4, 'Wallet', '', '', '', 0, 'REAL', 1, 'AED')");

        String insert = "INSERT INTO transactions (id, amount, description, date, transactionType, receiverName, "
                + "category, accountId, realAccountId, isExcludedFromSummary, needsReview, transferGroupId, currency) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        db.execSQL(insert, new Object[] { 1, 120.0, "lunch", T0, "DEBIT", "Cafe", "Food", 1, 3, 0, 0, null, "INR" });
        db.execSQL(insert, new Object[] { 2, 900.0, "hotel food", T0, "DEBIT", "Hotel", "Food", 2, 3, 0, 0, null, "INR" });
        // A transfer from everyday money into the trip fund, inside one account
        db.execSQL(insert, new Object[] { 3, 500.0, "save", T0, "DEBIT", "", "Transfer", 1, 3, 0, 0, "g1", "INR" });
        db.execSQL(insert, new Object[] { 4, 500.0, "save", T0, "CREDIT", "", "Transfer", 2, 3, 0, 0, "g1", "INR" });
        // Neither a fund nor an account: both defaults fill in
        db.execSQL(insert, new Object[] { 6, 30.0, "fuel", T0, "DEBIT", "Pump", "Fuel", null, null, 0, 0, null, "INR" });
        db.execSQL("INSERT INTO transactions (id, amount, description, date, transactionType, receiverName, smsBody, "
                + "smsSender, category, linkedRecurringPaymentId, smsHash, accountId, realAccountId, isExcludedFromSummary, "
                + "dedupKey, matchKey, messageTime, source, transferGroupId, needsReview, loanId, currency) VALUES "
                + "(5, 1000.0, 'pay', " + T0 + ", 'CREDIT', 'Employer', 'Rs 1000 credited', 'AX-BANK', 'Salary', 7, "
                + "'hash', 1, 3, 0, 'dedup', 'match', " + (T0 - 5) + ", 'SMS_BROADCAST', NULL, 1, NULL, 'INR')");

        db.execSQL("INSERT INTO recurring_payments (id, name, amount, dueDay, isCompleted, fromAccountId, toAccountId, "
                + "currency) VALUES (7, 'Trip savings', 2000, 5, 0, 1, 2, 'INR')");
        db.execSQL("INSERT INTO budgets (id, name, amount, currency, sortOrder) VALUES (1, 'Food', 5000, 'INR', 0)");
        db.execSQL("INSERT INTO budget_categories (budgetId, category) VALUES (1, 'Food')");
        db.setVersion(17);
        db.close();

        AppDatabase database = open(file);
        try {
            SupportSQLiteDatabase sql = database.getOpenHelper().getReadableDatabase();
            assertEquals(Arrays.asList("id", "name", "accountNumber", "bank", "expiryDate", "description", "isDefault",
                    "sortOrder", "currency"), columns(sql, "accounts"));
            assertEquals(Arrays.asList("id", "name", "description", "isDefault", "sortOrder", "target", "targetDate"),
                    columns(sql, "funds"));
            List<String> transactionColumns = columns(sql, "transactions");
            assertTrue(transactionColumns.contains("fundId"));
            assertTrue(transactionColumns.contains("accountId"));
            assertFalse(transactionColumns.contains("realAccountId"));
            assertEquals(22, transactionColumns.size());
            List<String> billColumns = columns(sql, "recurring_payments");
            assertTrue(billColumns.contains("fromFundId"));
            assertTrue(billColumns.contains("toFundId"));
            assertFalse(billColumns.contains("fromAccountId"));
            assertEquals(new HashSet<>(Arrays.asList("index_transactions_dedupKey_messageTime",
                    "index_transactions_matchKey_messageTime", "index_transactions_transferGroupId",
                    "index_transactions_loanId", "index_transactions_date", "index_transactions_smsHash")),
                    indexes(sql, "transactions"));

            // Funds keep their ids and details
            List<Fund> funds = database.fundDao().getAllFundsSync();
            assertEquals(2, funds.size());
            Fund daily = database.fundDao().getDefaultFundSync();
            assertEquals(1, daily.getId());
            assertEquals("Daily", daily.getName());
            assertEquals("Day to day", daily.getDescription());
            assertNull(daily.getTarget());
            Fund trip = database.fundDao().getFundByIdSync(2);
            assertEquals("Trip", trip.getName());
            assertEquals("Travel card", trip.getDescription());
            assertFalse(trip.isDefault());
            assertEquals(2, trip.getSortOrder());
            assertEquals(50000.5, trip.getTarget(), 0);
            assertEquals(TRIP_DATE, trip.getTargetDate().getTime());

            // Accounts keep theirs
            List<Account> accounts = database.accountDao().getAllAccountsSync();
            assertEquals(2, accounts.size());
            Account savings = database.accountDao().getDefaultAccountSync();
            assertEquals(3, savings.getId());
            assertEquals("Savings", savings.getName());
            assertEquals("1234", savings.getAccountNumber());
            assertEquals("SBI", savings.getBank());
            assertEquals(1800000000000L, savings.getExpiryDate().getTime());
            assertEquals("salary", savings.getDescription());
            assertEquals("INR", savings.getCurrency());
            Account wallet = database.accountDao().getAccountByIdSync(4);
            assertEquals("AED", wallet.getCurrency());
            assertEquals(1, wallet.getSortOrder());
            assertNull("A fund is not an account", database.accountDao().getAccountByIdSync(1));
            assertNull("An account is not a fund", database.fundDao().getFundByIdSync(3));

            // Transactions point at the same fund and account as before
            TransactionDao dao = database.transactionDao();
            assertEquals(6, dao.getAllTransactionsSyncOrderByDateAsc().size());
            Transaction lunch = dao.getTransactionSync(1);
            assertEquals(Long.valueOf(1), lunch.getFundId());
            assertEquals(Long.valueOf(3), lunch.getAccountId());
            assertEquals(Long.valueOf(2), dao.getTransactionSync(2).getFundId());
            List<Transaction> legs = dao.getTransferLegsSync("g1");
            assertEquals(2, legs.size());
            for (Transaction leg : legs) {
                assertEquals(Long.valueOf("DEBIT".equals(leg.getTransactionType()) ? 1 : 2), leg.getFundId());
                assertEquals(Long.valueOf(3), leg.getAccountId());
            }
            Transaction pay = dao.getTransactionSync(5);
            assertEquals(1000, pay.getAmount(), 0);
            assertEquals("pay", pay.getDescription());
            assertEquals(T0, pay.getDate().getTime());
            assertEquals("CREDIT", pay.getTransactionType());
            assertEquals("Employer", pay.getReceiverName());
            assertEquals("Rs 1000 credited", pay.getSmsBody());
            assertEquals("AX-BANK", pay.getSmsSender());
            assertEquals("Salary", pay.getCategory());
            assertEquals(Long.valueOf(7), pay.getLinkedRecurringPaymentId());
            assertEquals("hash", pay.getSmsHash());
            assertEquals(Long.valueOf(1), pay.getFundId());
            assertEquals(Long.valueOf(3), pay.getAccountId());
            assertFalse(pay.isExcludedFromSummary());
            assertEquals("dedup", pay.getDedupKey());
            assertEquals("match", pay.getMatchKey());
            assertEquals(Long.valueOf(T0 - 5), pay.getMessageTime());
            assertEquals("SMS_BROADCAST", pay.getSource());
            assertNull(pay.getTransferGroupId());
            assertTrue(pay.isNeedsReview());
            assertNull(pay.getLoanId());
            assertEquals("INR", pay.getCurrency());
            Transaction fuel = dao.getTransactionSync(6);
            assertEquals(Long.valueOf(1), fuel.getFundId());
            assertEquals(Long.valueOf(3), fuel.getAccountId());

            // Bills top up the same funds
            RecurringPayment bill = database.recurringPaymentDao().getAllRecurringPaymentsSync().get(0);
            assertEquals(7, bill.getId());
            assertEquals("Trip savings", bill.getName());
            assertEquals(Long.valueOf(1), bill.getFromFundId());
            assertEquals(Long.valueOf(2), bill.getToFundId());
            assertEquals("INR", bill.getCurrency());

            // Balances add up per fund and per account; fund 1 and account 1 are never mixed up
            Map<Long, Double> byFund = byId(value(dao.getFundBalances()));
            assertEquals(1000 - 120 - 500 - 30, byFund.get(1L), 0.001);
            assertEquals(500 - 900, byFund.get(2L), 0.001);
            assertEquals(2, byFund.size());
            Map<Long, Double> byAccount = byId(value(dao.getAccountBalances()));
            assertEquals(1000 - 120 - 900 - 30, byAccount.get(3L), 0.001);
            assertEquals(1, byAccount.size());

            // The budget counts the default fund's food, not the trip's and not the transfer
            List<BudgetProgress> progress = database.budgetDao().getProgressSync(new Date(0), new Date(Long.MAX_VALUE));
            assertEquals(1, progress.size());
            assertEquals(120, progress.get(0).spent, 0.001);

            // New rows carry on from each table's own ids
            assertEquals(3, database.fundDao().insert(new Fund("Health", "")));
            assertEquals("Savings", database.accountDao().getAccountByIdSync(3).getName());
        } finally {
            database.close();
        }
    }

    @Test
    public void aDatabaseWithoutDefaultsGetsThem() {
        File file = newFile("migration-17-18-defaults.db");
        SQLiteDatabase db = createVersion17(file);
        db.execSQL("INSERT INTO accounts (id, name, isDefault, type) VALUES (5, 'Trip', 0, 'VIRTUAL')");
        db.execSQL("INSERT INTO transactions (id, amount, date, transactionType, isExcludedFromSummary, needsReview) "
                + "VALUES (1, 10.0, " + T0 + ", 'DEBIT', 0, 0)");
        db.setVersion(17);
        db.close();

        AppDatabase database = open(file);
        try {
            Fund fund = database.fundDao().getDefaultFundSync();
            assertEquals(5, fund.getId());
            Account account = database.accountDao().getDefaultAccountSync();
            assertNotNull(account);
            assertEquals(Account.DEFAULT_NAME, account.getName());
            assertEquals("INR", account.getCurrency());

            Transaction row = database.transactionDao().getTransactionSync(1);
            assertEquals(Long.valueOf(5), row.getFundId());
            assertEquals(Long.valueOf(account.getId()), row.getAccountId());
        } finally {
            database.close();
        }
    }

    private static File newFile(String name) {
        Context context = ApplicationProvider.getApplicationContext();
        File file = context.getDatabasePath(name);
        file.getParentFile().mkdirs();
        file.delete();
        return file;
    }

    private static AppDatabase open(File file) {
        return Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class, file.getPath())
                .addMigrations(AppDatabase.MIGRATION_17_18)
                .allowMainThreadQueries()
                .build();
    }

    private static List<String> columns(SupportSQLiteDatabase db, String table) {
        List<String> names = new java.util.ArrayList<>();
        try (Cursor cursor = db.query("PRAGMA table_info(`" + table + "`)")) {
            int name = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                names.add(cursor.getString(name));
            }
        }
        return names;
    }

    private static Set<String> indexes(SupportSQLiteDatabase db, String table) {
        Set<String> names = new HashSet<>();
        try (Cursor cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? "
                + "AND name NOT LIKE 'sqlite_%'", new Object[] { table })) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    private static Map<Long, Double> byId(List<Balance> balances) {
        Map<Long, Double> byId = new HashMap<>();
        for (Balance balance : balances) {
            byId.merge(balance.id, balance.balance, Double::sum);
        }
        return byId;
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

    // Version 17 as Migration16To17Test leaves it: funds and accounts share one table
    private static SQLiteDatabase createVersion17(File file) {
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
                + "`type` TEXT NOT NULL DEFAULT 'VIRTUAL', `sortOrder` INTEGER NOT NULL DEFAULT 0, `currency` TEXT, "
                + "`target` REAL, `targetDate` INTEGER)");
        db.execSQL("CREATE TABLE `counterparty_categories` (`counterparty` TEXT NOT NULL, `transactionType` TEXT NOT NULL, "
                + "`categoryId` INTEGER NOT NULL, PRIMARY KEY(`counterparty`, `transactionType`))");
        db.execSQL("CREATE TABLE `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, "
                + "`amount` REAL NOT NULL, `date` INTEGER, `note` TEXT, `returnedManually` REAL NOT NULL, "
                + "`closedManually` INTEGER NOT NULL, `closedDate` INTEGER, `currency` TEXT NOT NULL DEFAULT 'INR')");
        db.execSQL(MessagePattern.CREATE_TABLE);
        db.execSQL(MessagePattern.CREATE_INDEX);
        db.execSQL(Budget.CREATE_TABLE);
        db.execSQL(BudgetCategory.CREATE_TABLE);
        return db;
    }
}
