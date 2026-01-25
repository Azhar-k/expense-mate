package com.example.expensemate.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;

import java.util.List;

import androidx.annotation.NonNull;

@Database(entities = { Transaction.class, Category.class, RecurringPayment.class, Account.class,
                RegexPattern.class }, version = 6, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
        private static volatile AppDatabase INSTANCE;

        // Single migration that sets up all tables and indexes
        private static final Migration INITIAL_MIGRATION = new Migration(0, 1) {
                @Override
                public void migrate(SupportSQLiteDatabase database) {
                        // Create transactions table
                        database.execSQL(
                                        "CREATE TABLE IF NOT EXISTS `transactions` (" +
                                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                                        "`amount` REAL NOT NULL, " +
                                                        "`description` TEXT, " +
                                                        "`date` INTEGER, " +
                                                        "`transactionType` TEXT, " +
                                                        "`receiverName` TEXT, " +
                                                        "`smsBody` TEXT, " +
                                                        "`smsSender` TEXT, " +
                                                        "`category` TEXT, " +
                                                        "`linkedRecurringPaymentId` INTEGER, " +
                                                        "`smsHash` TEXT" +
                                                        ")");

                        // Create categories table
                        database.execSQL(
                                        "CREATE TABLE IF NOT EXISTS `categories` (" +
                                                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                                                        "`name` TEXT NOT NULL, " +
                                                        "`type` TEXT NOT NULL" +
                                                        ")");

                        // Create recurring_payments table
                        database.execSQL(
                                        "CREATE TABLE IF NOT EXISTS `recurring_payments` (" +
                                                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                                                        "`name` TEXT, " +
                                                        "`amount` REAL NOT NULL, " +
                                                        "`dueDay` INTEGER NOT NULL, " +
                                                        "`expiryDate` INTEGER, " +
                                                        "`isCompleted` INTEGER NOT NULL DEFAULT 0, " +
                                                        "`lastCompletedDate` INTEGER" +
                                                        ")");

                        // Create all necessary indexes
                        // Transaction indexes
                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_transactions_category_month_year` " +
                                                        "ON `transactions` (`category`, `transactionType`, `linkedRecurringPaymentId`, `date`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_transactions_expense_month_year` " +
                                                        "ON `transactions` (`transactionType`, `linkedRecurringPaymentId`, `date`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_transactions_income_month_year` " +
                                                        "ON `transactions` (`transactionType`, `date`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_transactions_date` " +
                                                        "ON `transactions` (`date`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_transactions_smsHash` " +
                                                        "ON `transactions` (`smsHash`)");

                        // Category indexes
                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_categories_name` " +
                                                        "ON `categories` (`name`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_categories_type` " +
                                                        "ON `categories` (`type`)");

                        // Recurring payment indexes
                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_recurring_payments_isCompleted_dueDay` " +
                                                        "ON `recurring_payments` (`isCompleted`, `dueDay`)");

                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_recurring_payments_amount` " +
                                                        "ON `recurring_payments` (`amount`)");
                }
        };

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

                        // Account indexes
                        database.execSQL(
                                        "CREATE INDEX IF NOT EXISTS `index_accounts_name` " +
                                                        "ON `accounts` (`name`)");
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
                        database.execSQL(
                                        "ALTER TABLE transactions ADD COLUMN isExcludedFromSummary INTEGER NOT NULL DEFAULT 0");
                }
        };

        private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
                @Override
                public void migrate(SupportSQLiteDatabase database) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `regex_patterns` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`name` TEXT, " +
                                        "`regex` TEXT, " +
                                        "`type` TEXT, " +
                                        "`amount_group_index` INTEGER NOT NULL, " +
                                        "`merchant_group_index` INTEGER NOT NULL, " +
                                        "`is_system` INTEGER NOT NULL, " +
                                        "`default_sender` TEXT)");
                }
        };

        public abstract TransactionDao transactionDao();

        public abstract CategoryDao categoryDao();

        public abstract RecurringPaymentDao recurringPaymentDao();

        public abstract AccountDao accountDao();

        public abstract RegexPatternDao regexPatternDao();

        private static void insertDefaultCategories(CategoryDao categoryDao) {
                // Insert default expense categories
                String[] defaultExpenseCategories = {
                                "Default", "Food", "Household", "Fuel", "Entertainment", "Personal", "Others"
                };

                for (String category : defaultExpenseCategories) {
                        categoryDao.insertCategory(new Category(category, "EXPENSE"));
                }

                // Insert default income categories
                String[] defaultIncomeCategories = {
                                "Default", "Salary", "Others"
                };

                for (String category : defaultIncomeCategories) {
                        categoryDao.insertCategory(new Category(category, "INCOME"));
                }
        }

        private static void insertDefaultAccount(AccountDao accountDao) {
                Account defaultAccount = new Account("Savings", "", "", null, "Default savings account");
                defaultAccount.setDefault(true);
                accountDao.insert(defaultAccount);
        }

        private static void insertDefaultRegexPatterns(RegexPatternDao dao) {
                List<RegexPattern> patterns = new ArrayList<>();

                // ICICI Debit
                patterns.add(new RegexPattern(
                                "ICICI Debit",
                                "(?i)ICICI Bank (?:Acct|Acc) XX(\\\\d+) (?:debited for |debited )Rs\\\\.? (\\\\d+(?:,\\\\d+)*(?:\\\\.\\\\d{2})?) on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2}|\\\\d{2}/\\\\d{2}/\\\\d{4})[^;]*?;\\\\s*([^*]+)(?:credited|Info)",
                                "DEBIT", 2, 4, true, null));

                // ICICI Credit 1
                patterns.add(new RegexPattern(
                                "ICICI Credit 1",
                                "(?i)(?:Dear Customer, Acct XX(\\\\d+) is credited with Rs (\\\\d+(?:,\\\\d+)*(?:\\\\.\\\\d{2})?) on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2}) from ([^.]+))",
                                "CREDIT", 2, 4, true, null));

                // ICICI Credit 2
                patterns.add(new RegexPattern(
                                "ICICI Credit 2",
                                "(?i)ICICI Bank Account XX(\\\\d+) credited:Rs\\\\.? (\\\\d+(?:,\\\\d+)*(?:\\\\.\\\\d{2})?) on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2})\\\\. Info NEFT-([^-]+)-",
                                "CREDIT", 2, 4, true, null));

                // Kotak Debit
                patterns.add(new RegexPattern(
                                "Kotak Debit",
                                "Sent Rs\\\\.?(\\\\d+(?:\\\\.\\\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to ([^\\\\s]+) on (\\\\d{2}-\\\\d{2}-\\\\d{2})\\\\.UPI Ref (\\\\d+)",
                                "DEBIT", 1, 3, true, null));

                // Kotak Credit
                patterns.add(new RegexPattern(
                                "Kotak Credit",
                                "Received Rs\\\\.?(\\\\d+(?:\\\\.\\\\d{2})?) in your Kotak Bank AC ([A-Z0-9]+) from ([^\\\\s]+) on (\\\\d{2}-\\\\d{2}-\\\\d{2})\\\\.UPI Ref:(\\\\d+)",
                                "CREDIT", 1, 3, true, null));

                // SBI Credit Card
                patterns.add(new RegexPattern(
                                "SBI Credit Card",
                                "Rs\\\\.?(\\\\d+(?:,\\\\d+)*(?:\\\\.\\\\d{2})?) spent on your SBI Credit Card ending with (\\\\d{4}) at ([^\\\\s]+) on (\\\\d{2}-\\\\d{2}-\\\\d{2}) via UPI \\\\(Ref No\\\\. (\\\\d+)\\\\)",
                                "DEBIT", 1, 3, true, null));

                // Federal Debit
                patterns.add(new RegexPattern(
                                "Federal Debit",
                                "Rs (\\\\d+(?:\\\\.\\\\d{2})?) debited via UPI on (\\\\d{2}-\\\\d{2}-\\\\d{4} \\\\d{2}:\\\\d{2}:\\\\d{2}) to VPA ([^\\\\s]+)\\\\.Ref No (\\\\d+)\\\\.Small txns\\\\?Use UPI Lite!-Federal Bank",
                                "DEBIT", 1, 3, true, null));

                // Federal Credit
                patterns.add(new RegexPattern(
                                "Federal Credit",
                                "Dear Customer, Rs\\\\.?(\\\\d+(?:\\\\.\\\\d{2})?) credited to your A/c XX(\\\\d+) on (\\\\d{2}[A-Z]{3}\\\\d{4} \\\\d{2}:\\\\d{2}:\\\\d{2})\\\\. BAL-Rs\\\\.(\\\\d+(?:\\\\.\\\\d{2})?)-Federal Bank",
                                "CREDIT", 1, -1, true, "Federal Bank"));

                // Pluxee Debit
                patterns.add(new RegexPattern(
                                "Pluxee Debit",
                                "Rs\\\\.? (\\\\d+(?:\\\\.\\\\d{2})?) spent from Pluxee\\\\s+Meal Card wallet, card no\\\\.(?:xx\\\\d+)? on (\\\\d{1,2}-\\\\d{2}-\\\\d{4} \\\\d{2}:\\\\d{2}:\\\\d{2}) at ([^.]+)\\\\. Avl bal Rs\\\\.(\\\\d+(?:\\\\.\\\\d{2})?)\\\\. Not you call 18002106919",
                                "DEBIT", 1, 3, true, null));

                // Pluxee Credit
                patterns.add(new RegexPattern(
                                "Pluxee Credit",
                                "Your Pluxee Card (?:xx\\\\d+)? has been (?:successfully )?credited with (?:Rs\\\\.?|INR) (\\\\d+(?:\\\\.\\\\d{2})?) (?:towards\\\\s+Meal Wallet|as a reversal against a previous transaction) on (?:[A-Za-z]{3} )?(\\\\d{2} [A-Za-z]{3} \\\\d{4} \\\\d{2}:\\\\d{2}:\\\\d{2})(?:as a reversal against a previous transaction on [A-Za-z]{3} \\\\d{2},\\\\d{4} \\\\d{2}:\\\\d{2}:\\\\d{2})?\\\\.(?: Your current Meal Wallet balance is Rs\\\\.(\\\\d+(?:\\\\.\\\\d{2})?)\\\\.)?",
                                "CREDIT", 1, -1, true, "Pluxee"));

                // UPI Debit
                patterns.add(new RegexPattern(
                                "UPI Debit",
                                "(?i)(?:debited\\\\s+(?:for|Rs\\\\.?|INR)?\\\\s*)([\\\\d,]+\\\\.\\\\d{2}).*?;\\\\s*([A-Z][A-Za-z\\\\s\\\\.\\\\-]+)\\\\s+credited",
                                "DEBIT", 1, 2, true, null));

                // UPI Credit
                patterns.add(new RegexPattern(
                                "UPI Credit",
                                "(?i)(?:Acct\\\\s+\\\\w+\\\\s+is\\\\s+credited\\\\s+with\\\\s+(?:Rs\\\\.?|INR)?\\\\s*([\\\\d,]+\\\\.\\\\d{2})\\\\s+from\\\\s+([A-Z][A-Za-z\\\\s\\\\.\\\\-]+))",
                                "CREDIT", 1, 2, true, null));

                // NEFT Credit
                patterns.add(new RegexPattern(
                                "NEFT Credit",
                                "(?i)Account\\\\s+\\\\w+\\\\s+credited:Rs\\\\.\\\\s*([\\\\d,]+\\\\.\\\\d{2}).*?NEFT[-]?([A-Z0-9]+)",
                                "CREDIT", 1, 2, true, null));

                // Credit Card Spend
                patterns.add(new RegexPattern(
                                "Credit Card Spend",
                                "(?i)Rs\\\\.?\\\\s*(\\\\d{1,3}(?:,\\\\d{3})*(?:\\\\.\\\\d{2})?)\\\\s+spent\\\\s+on\\\\s+(?:your\\\\s+)?(?:[A-Z]+\\\\s+)?(?:Credit\\\\s+)?Card(?:\\\\s+ending\\\\s+with\\\\s+\\\\d{4}|\\\\s+XX\\\\d{4})?.*?at\\\\s+([A-Z0-9@&\\\\-\\\\s\\\\.]+?)\\\\s+(?:on\\\\s+\\\\d{2}-\\\\d{2}-\\\\d{2}|via|Ref|\\\\.|$)",
                                "DEBIT", 1, 2, true, null));

                // ICICI Card Spend
                patterns.add(new RegexPattern(
                                "ICICI Card Spend",
                                "Rs\\\\.?\\\\s*(\\\\d{1,3}(?:,\\\\d{3})*(?:\\\\.\\\\d{2})?)\\\\s+spent on ICICI Bank Card XX\\\\d{4} on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2}) at ([A-Z0-9\\\\s\\\\.\\\\-&]+)",
                                "DEBIT", 1, 3, true, null));

                // ICICI Alt Debit
                patterns.add(new RegexPattern(
                                "ICICI Alt Debit",
                                "Rs\\\\.?\\\\s*(\\\\d{1,3}(?:,\\\\d{3})*(?:\\\\.\\\\d{2}))\\\\s+debited from ICICI Bank Acc XX\\\\d+ on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2})\\\\s+([A-Z0-9\\\\*\\\\s\\\\.]+)\\\\s+Bal",
                                "DEBIT", 1, 3, true, null));

                // NEFT Credit By
                patterns.add(new RegexPattern(
                                "NEFT Credit By",
                                "(?:INR|Rs\\\\.?)[ ]?(\\\\d{1,3}(?:,\\\\d{3})*(?:\\\\.\\\\d{2}))\\\\s+credited to your A/c No XX\\\\d+ on \\\\d{2}/\\\\d{2}/\\\\d{4}.*?by ([A-Z][A-Za-z\\\\s\\\\.]+)",
                                "CREDIT", 1, 2, true, null));

                // ICICI InfoBIL NEFT Debit
                patterns.add(new RegexPattern(
                                "ICICI InfoBIL NEFT Debit",
                                "ICICI Bank Acc XX\\\\d+ debited Rs\\\\.?\\\\s*(\\\\d{1,3}(?:,\\\\d{3})*(?:\\\\.\\\\d{2})) on (\\\\d{2}-[A-Za-z]{3}-\\\\d{2}) InfoBIL\\\\*NEFT\\\\*([A-Z0-9\\\\.]+)",
                                "DEBIT", 1, 3, true, null));

                // General Debit
                patterns.add(new RegexPattern(
                                "General Debit",
                                "(?i)(?:Rs\\\\.?|INR)\\\\s*(\\\\d+(?:\\\\.\\\\d{2})?)\\\\s*(?:has been|is|was)?\\\\s*(?:debited|spent|paid|sent|transferred|withdrawn)\\\\s*(?:from|in|to|at)?\\\\s*(?:your|the)?\\\\s*(?:account|a/c|ac|bank)?\\\\s*(?:[A-Z0-9]+)?\\\\s*(?:to|for|at)?\\\\s*([A-Za-z0-9@\\\\s\\\\.]+)",
                                "DEBIT", 1, 2, true, null));

                // General Credit
                patterns.add(new RegexPattern(
                                "General Credit",
                                "(?i)(?:Received|credited|deposited)\\\\s+(?:Rs\\\\.?|INR)\\\\s*(\\\\d+(?:\\\\.\\\\d{2})?)\\\\s*(?:has been|is|was)?\\\\s*(?:in|to|at)?\\\\s*(?:your|the)?\\\\s*(?:account|a/c|ac|bank)?\\\\s*(?:[A-Z0-9]+)?\\\\s*(?:from|by)?\\\\s*([A-Za-z0-9@\\\\s\\\\.]+)(?:\\\\s+on\\\\s+(\\\\d{2}-\\\\d{2}-\\\\d{2}|\\\\d{2}-[A-Za-z]{3}-\\\\d{2}))?(?:\\\\.|$)",
                                "CREDIT", 1, 2, true, null));

                dao.insertAll(patterns);
        }

        public static AppDatabase getDatabase(final Context context) {
                if (INSTANCE == null) {
                        synchronized (AppDatabase.class) {
                                if (INSTANCE == null) {
                                        INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                                        AppDatabase.class, "expense_mate_database")
                                                        .addMigrations(INITIAL_MIGRATION, MIGRATION_1_2, MIGRATION_2_3,
                                                                        MIGRATION_3_4,
                                                                        MIGRATION_4_5, MIGRATION_5_6)
                                                        .fallbackToDestructiveMigration()
                                                        .addCallback(new RoomDatabase.Callback() {
                                                                @Override
                                                                public void onCreate(
                                                                                @NonNull SupportSQLiteDatabase db) {
                                                                        super.onCreate(db);
                                                                        // Insert default data when database is created
                                                                        new Thread(() -> {
                                                                                insertDefaultCategories(
                                                                                                INSTANCE.categoryDao());
                                                                                insertDefaultAccount(
                                                                                                INSTANCE.accountDao());
                                                                        }).start();
                                                                }

                                                                @Override
                                                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                                                        super.onOpen(db);
                                                                        // Check if default data exists and insert if
                                                                        // they don't
                                                                        new Thread(() -> {
                                                                                // List<Category> categories =
                                                                                // INSTANCE.categoryDao().getAllCategoriesSync();
                                                                                // if (categories == null ||
                                                                                // categories.isEmpty()) {
                                                                                // insertDefaultCategories(INSTANCE.categoryDao());
                                                                                // }
                                                                                //
                                                                                // List<Account> accounts =
                                                                                // INSTANCE.accountDao().getAllAccountsSync();
                                                                                // if (accounts == null ||
                                                                                // accounts.isEmpty()) {
                                                                                // insertDefaultAccount(INSTANCE.accountDao());
                                                                                // }
                                                                        }).start();

                                                                        // Check if default regex patterns exist and
                                                                        // insert if they don't
                                                                        new Thread(() -> {
                                                                                if (INSTANCE.regexPatternDao()
                                                                                                .getCount() == 0) {
                                                                                        insertDefaultRegexPatterns(
                                                                                                        INSTANCE.regexPatternDao());
                                                                                }
                                                                        }).start();
                                                                }
                                                        })
                                                        .build();
                                        new Thread(() -> {
                                                BackupDataLoader.exportDatabaseDataToLocal(context, INSTANCE);
                                        }).start();
                                }
                        }
                }
                return INSTANCE;
        }
}