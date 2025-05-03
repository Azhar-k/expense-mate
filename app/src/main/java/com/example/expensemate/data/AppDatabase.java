package com.example.expensemate.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Transaction.class, TotalExpense.class}, version = 3, exportSchema = false)
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

    public abstract TransactionDao transactionDao();
    public abstract TotalExpenseDao totalExpenseDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
} 