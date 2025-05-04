package com.example.expensemate.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Transaction.class, Category.class}, version = 6, exportSchema = false)
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

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
} 