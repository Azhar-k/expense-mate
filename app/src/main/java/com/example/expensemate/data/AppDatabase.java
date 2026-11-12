package com.example.expensemate.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import com.example.expensemate.util.Currencies;

@Database(entities = {Transaction.class, Category.class, RecurringPayment.class, Account.class, CounterpartyCategory.class, Loan.class}, version = 15, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create accounts table
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                            "`name` TEXT NOT NULL, " +
                            "`accountNumber` TEXT, " +
                            "`bank` TEXT, " +
                            "`expiryDate` INTEGER, " +
                            "`description` TEXT" +
                            ")");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add isDefault column to accounts table
            database.execSQL("ALTER TABLE accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add accountId column to transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {

            // Add isExcludedFromSummary column to transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN isExcludedFromSummary INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add fromAccountId and toAccountId columns to recurring_payments table
            database.execSQL("ALTER TABLE recurring_payments ADD COLUMN fromAccountId INTEGER");
            database.execSQL("ALTER TABLE recurring_payments ADD COLUMN toAccountId INTEGER");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the columns used to recognise one payment across entry paths
            database.execSQL("ALTER TABLE transactions ADD COLUMN dedupKey TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN matchKey TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN messageTime INTEGER");
            database.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT");

            try (Cursor cursor = database.query("SELECT id, amount, date, description, transactionType, "
                    + "receiverName, smsBody FROM transactions")) {
                while (cursor.moveToNext()) {
                    Transaction row = new Transaction();
                    row.setAmount(cursor.getDouble(1));
                    row.setDate(cursor.isNull(2) ? null : new Date(cursor.getLong(2)));
                    row.setDescription(cursor.getString(3));
                    row.setTransactionType(cursor.getString(4));
                    row.setReceiverName(cursor.getString(5));
                    row.setSmsBody(cursor.getString(6));
                    if (row.getSmsBody() == null) {
                        continue;
                    }
                    TransactionKeys.fillLegacyKeys(row);
                    database.execSQL(
                            "UPDATE transactions SET dedupKey = ?, matchKey = ?, messageTime = ?, source = ? WHERE id = ?",
                            new Object[] { row.getDedupKey(), row.getMatchKey(), row.getMessageTime(),
                                    row.getSource(), cursor.getLong(0) });
                }
            }

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dedupKey_messageTime` "
                    + "ON `transactions` (`dedupKey`, `messageTime`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_matchKey_messageTime` "
                    + "ON `transactions` (`matchKey`, `messageTime`)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transferGroupId` "
                    + "ON `transactions` (`transferGroupId`)");

            int linked = Transfers.linkLegacyPairs(database);
            Log.i("AppDatabase", "Linked " + linked + " existing transfers");

            // Recent captured rows that never got a category start in the review list. Older
            // ones stay out of it, so the first launch does not open with years of chores.
            long reviewFrom = System.currentTimeMillis() - REVIEW_BACKFILL_MS;
            database.execSQL("UPDATE transactions SET needsReview = 1 WHERE category = 'Default' "
                            + "AND source IS NOT NULL AND isExcludedFromSummary = 0 AND transferGroupId IS NULL "
                            + "AND date >= ?",
                    new Object[] { reviewFrom });
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `counterparty_categories` ("
                    + "`counterparty` TEXT NOT NULL, "
                    + "`transactionType` TEXT NOT NULL, "
                    + "`categoryId` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`counterparty`, `transactionType`))");
            CounterpartyCategory.rebuildBeforeDefaultFlag(database);
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE categories ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0");
            Category.ensureDefaults(database);
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Every account so far is virtual
            database.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'VIRTUAL'");
            database.execSQL("ALTER TABLE transactions ADD COLUMN realAccountId INTEGER");
            Account.ensureDefaultRealAccount(database);
        }
    };

    // Zero everywhere keeps the alphabetical order until someone reorders a list
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE categories ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `loans` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`person` TEXT NOT NULL, "
                    + "`amount` REAL NOT NULL, "
                    + "`date` INTEGER, "
                    + "`note` TEXT, "
                    + "`returnedManually` REAL NOT NULL, "
                    + "`closedManually` INTEGER NOT NULL, "
                    + "`closedDate` INTEGER)");
            database.execSQL("ALTER TABLE transactions ADD COLUMN loanId INTEGER");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_loanId` ON `transactions` (`loanId`)");
        }
    };

    // Date ranges and newest-first lists are on every screen, and each captured message is checked
    // against smsHash. The dropped indexes came from early migrations and no query used them.
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            for (String unused : new String[] { "index_transactions_category_month_year",
                    "index_transactions_expense_month_year", "index_transactions_income_month_year",
                    "index_categories_name", "index_categories_type", "index_recurring_payments_isCompleted_dueDay",
                    "index_recurring_payments_amount", "index_accounts_name" }) {
                database.execSQL("DROP INDEX IF EXISTS `" + unused + "`");
            }
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_smsHash` ON `transactions` (`smsHash`)");
        }
    };

    // Every amount so far was in rupees. Virtual accounts get no currency: they can hold money in any.
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN currency TEXT");
            Account.ensureRealAccountCurrencies(database, Currencies.LEGACY);
            database.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT '"
                    + Currencies.LEGACY + "'");
            database.execSQL("ALTER TABLE loans ADD COLUMN currency TEXT NOT NULL DEFAULT '"
                    + Currencies.LEGACY + "'");
            database.execSQL("ALTER TABLE recurring_payments ADD COLUMN currency TEXT NOT NULL DEFAULT '"
                    + Currencies.LEGACY + "'");
        }
    };

    private static final long REVIEW_BACKFILL_MS = 30L * 24 * 60 * 60 * 1000;

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RecurringPaymentDao recurringPaymentDao();
    public abstract AccountDao accountDao();
    public abstract LoanDao loanDao();

    private static void insertDefaultCategories(CategoryDao categoryDao) {
        // Insert default expense categories
        String[] defaultExpenseCategories = {
            "Default", "Food", "Household", "Fuel", "Entertainment", "Personal", "Others"
        };

        for (String category : defaultExpenseCategories) {
            insertDefaultCategory(categoryDao, category, "EXPENSE");
        }

        // Insert default income categories
        String[] defaultIncomeCategories = {
            "Default", "Salary", "Others"
        };

        for (String category : defaultIncomeCategories) {
            insertDefaultCategory(categoryDao, category, "INCOME");
        }
    }

    private static void insertDefaultCategory(CategoryDao categoryDao, String name, String type) {
        Category category = new Category(name, type);
        category.setDefault("Default".equals(name));
        categoryDao.insertCategory(category);
    }

    private static void insertDefaultAccount(AccountDao accountDao) {
        Account defaultAccount = new Account("Savings", "", "", null, "Default savings account");
        defaultAccount.setDefault(true);
        accountDao.insert(defaultAccount);

        Account realAccount = new Account(Account.DEFAULT_REAL_NAME, "", "", null, "");
        realAccount.setType(Account.TYPE_REAL);
        realAccount.setCurrency(Currencies.fromLocale());
        realAccount.setDefault(true);
        accountDao.insert(realAccount);
    }

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    // Insert default data when database is created
                                    new Thread(() -> {
                                        insertDefaultCategories(INSTANCE.categoryDao());
                                        insertDefaultAccount(INSTANCE.accountDao());
                                    }).start();
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // Check if default data exists and insert if they don't
                                    new Thread(() -> {
//                                        List<Category> categories = INSTANCE.categoryDao().getAllCategoriesSync();
//                                        if (categories == null || categories.isEmpty()) {
//                                            insertDefaultCategories(INSTANCE.categoryDao());
//                                        }
//
//                                        List<Account> accounts = INSTANCE.accountDao().getAllAccountsSync();
//                                        if (accounts == null || accounts.isEmpty()) {
//                                            insertDefaultAccount(INSTANCE.accountDao());
//                                        }
                                    }).start();
                                }
                            })
                            .build();
                    new Thread(()-> BackupDataLoader.deleteOldLocalExports(context)).start();
                }
            }
        }
        return INSTANCE;
    }
} 
