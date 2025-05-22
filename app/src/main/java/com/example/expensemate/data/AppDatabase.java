package com.example.expensemate.data;

import android.content.Context;
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

@Database(entities = {Transaction.class, Category.class, RecurringPayment.class, Account.class}, version = 4, exportSchema = false)
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

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RecurringPaymentDao recurringPaymentDao();
    public abstract AccountDao accountDao();

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

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(INITIAL_MIGRATION, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
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
                    new Thread(()->{
                        BackupDataLoader.exportDatabaseData(context, INSTANCE);
                    }).start();
                }
            }
        }
        return INSTANCE;
    }
} 