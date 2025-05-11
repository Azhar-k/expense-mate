package com.example.expensemate.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Transaction.class, Category.class, RecurringPayment.class}, version = 10, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    // Migration from version 1 to 2
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the total_expense table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `total_expense` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY, " +
                "`amount` REAL NOT NULL DEFAULT 0.0" +
                ")");
            
            // Insert initial total expense
            database.execSQL("INSERT INTO total_expense (id, amount) VALUES (1, 0.0)");
        }
    };

    // Migration from version 2 to 3
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add category column with default value 'Others' but allow NULL
            database.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT DEFAULT 'Others'");
        }
    };

    // Migration from version 3 to 4
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the total_income table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `total_income` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY, " +
                "`amount` REAL NOT NULL DEFAULT 0.0" +
                ")");
            
            // Insert initial total income
            database.execSQL("INSERT INTO total_income (id, amount) VALUES (1, 0.0)");
        }
    };

    // Migration from version 4 to 5
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the categories table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL" +
                ")");

            // Insert default categories
            String[] defaultExpenseCategories = {
                "Food", "Household", "Movies", "Fuel", "Home Food",
                "Home Household", "Family", "Home Entertainment",
                "Vehicle", "Transport", "Entertainment", "Others"
            };

            for (String category : defaultExpenseCategories) {
                database.execSQL(
                    "INSERT INTO categories (name, type) VALUES (?, ?)",
                    new Object[]{category, "EXPENSE"}
                );
            }

            // Insert default income categories
            String[] defaultIncomeCategories = {
                "Salary", "Business", "Investment", "Gift", "Others"
            };

            for (String category : defaultIncomeCategories) {
                database.execSQL(
                    "INSERT INTO categories (name, type) VALUES (?, ?)",
                    new Object[]{category, "INCOME"}
                );
            }
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the total_income table
            database.execSQL(
                    "DROP TABLE IF EXISTS `total_income`");
            database.execSQL(
                    "DROP TABLE IF EXISTS `total_expense`");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create the recurring_payments table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_payments` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT, " +
                "`amount` REAL NOT NULL, " +
                "`dueDate` INTEGER, " +
                "`expiryDate` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL DEFAULT 0, " +
                "`lastCompletedDate` INTEGER" +
                ")");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create temporary table with new schema
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_payments_temp` (" +
                "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "`name` TEXT, " +
                "`amount` REAL NOT NULL, " +
                "`dueDay` INTEGER NOT NULL, " +
                "`expiryDate` INTEGER, " +
                "`isCompleted` INTEGER NOT NULL DEFAULT 0, " +
                "`lastCompletedDate` INTEGER" +
                ")");

            // Copy data from old table to new table, converting dueDate to dueDay
            database.execSQL(
                "INSERT INTO recurring_payments_temp (id, name, amount, dueDay, expiryDate, isCompleted, lastCompletedDate) " +
                "SELECT id, name, amount, " +
                "CAST(strftime('%d', datetime(dueDate/1000, 'unixepoch')) AS INTEGER), " +
                "expiryDate, isCompleted, lastCompletedDate " +
                "FROM recurring_payments");

            // Drop old table
            database.execSQL("DROP TABLE recurring_payments");

            // Rename new table to original name
            database.execSQL("ALTER TABLE recurring_payments_temp RENAME TO recurring_payments");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add linkedRecurringPaymentId column to transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN linkedRecurringPaymentId INTEGER DEFAULT NULL");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create index for getCategorySumsByMonthYear
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_category_month_year` " +
                "ON `transactions` (`category`, `transactionType`, `linkedRecurringPaymentId`, `date`)");

            // Create index for getExpenseByMonthYearSync
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_expense_month_year` " +
                "ON `transactions` (`transactionType`, `linkedRecurringPaymentId`, `date`)");

            // Create index for getIncomeByMonthYearSync
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_income_month_year` " +
                "ON `transactions` (`transactionType`, `date`)");

            // Create index for getTransactionsByMonthYearSync
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transactions_date` " +
                "ON `transactions` (`date`)");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create index for getAllCategories
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_categories_name` " +
                "ON `categories` (`name`)");

            // Create index for getCategoriesByType
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_categories_type` " +
                "ON `categories` (`type`)");

            // Create index for getAllRecurringPayments
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_payments_isCompleted_dueDay` " +
                "ON `recurring_payments` (`isCompleted`, `dueDay`)");

            // Create index for getTotalAmount and getRemainingAmount
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_payments_amount` " +
                "ON `recurring_payments` (`amount`)");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`amount` REAL NOT NULL, " +
                            "`description` TEXT, " +
                            "`date` INTEGER, " +
                            "`accountNumber` TEXT, " +
                            "`accountType` TEXT, " +
                            "`transactionType` TEXT, " +
                            "`receiverName` TEXT, " +
                            "`smsBody` TEXT, " +
                            "`smsSender` TEXT, " +
                            "`category` TEXT, " + // No default value
                            "`linkedRecurringPaymentId` INTEGER)"
            );
            database.execSQL(
                    "INSERT INTO transactions_new (id, amount, description, date, accountNumber, accountType, transactionType, receiverName, smsBody, smsSender, category, linkedRecurringPaymentId) " +
                            "SELECT id, amount, description, date, accountNumber, accountType, transactionType, receiverName, smsBody, smsSender, category, linkedRecurringPaymentId FROM transactions"
            );
            database.execSQL("DROP TABLE transactions");
            database.execSQL("ALTER TABLE transactions_new RENAME TO transactions");
        }
    };

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RecurringPaymentDao recurringPaymentDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, 
                                         MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                                         MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
} 