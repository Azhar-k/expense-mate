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

@Database(entities = {Transaction.class, Category.class, RecurringPayment.class}, version = 1, exportSchema = false)
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

            // Insert default expense categories
            String[] defaultExpenseCategories = {
                "Default","Food", "Household", "Fuel", "Entertainment", "Personal", "Others"
            };

            for (String category : defaultExpenseCategories) {
                database.execSQL(
                    "INSERT INTO categories (name, type) VALUES (?, ?)",
                    new Object[]{category, "EXPENSE"}
                );
            }

            // Insert default income categories
            String[] defaultIncomeCategories = {
                "Default","Salary", "Business", "Investment", "Gift", "Others"
            };

            for (String category : defaultIncomeCategories) {
                database.execSQL(
                    "INSERT INTO categories (name, type) VALUES (?, ?)",
                    new Object[]{category, "INCOME"}
                );
            }

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

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RecurringPaymentDao recurringPaymentDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "expense_mate_database")
                            .addMigrations(INITIAL_MIGRATION)
                            .build();
                    new Thread(()->{
                        exportDatabaseData(context, INSTANCE);
                    }).start();
                }
            }
        }
        return INSTANCE;
    }

    private static void exportDatabaseData(Context context, AppDatabase database) {
        try {
            StringBuilder data = new StringBuilder();
            data.append("=== Database Export ").append(new Date()).append(" ===\n\n");

            // Export transactions
            data.append("=== TRANSACTIONS ===\n");
            List<Transaction> transactions = database.transactionDao().getAllTransactionsSync();
            for (Transaction t : transactions) {
                data.append(String.format("ID: %d\n", t.getId()));
                data.append(String.format("Amount: %.2f\n", t.getAmount()));
                data.append(String.format("Description: %s\n", t.getDescription()));
                data.append(String.format("Date: %s\n", t.getDate()));
                data.append(String.format("Transaction Type: %s\n", t.getTransactionType()));
                data.append(String.format("Receiver: %s\n", t.getReceiverName()));
                data.append(String.format("Category: %s\n", t.getCategory()));
                data.append(String.format("Linked Payment ID: %s\n", t.getLinkedRecurringPaymentId()));
                data.append("---\n");
            }

            // Export categories
            data.append("\n=== CATEGORIES ===\n");
            List<Category> categories = database.categoryDao().getAllCategoriesSync();
            for (Category c : categories) {
                data.append(String.format("ID: %d\n", c.getId()));
                data.append(String.format("Name: %s\n", c.getName()));
                data.append(String.format("Type: %s\n", c.getType()));
                data.append("---\n");
            }

            // Export recurring payments
            data.append("\n=== RECURRING PAYMENTS ===\n");
            List<RecurringPayment> payments = database.recurringPaymentDao().getAllRecurringPaymentsSync();
            for (RecurringPayment p : payments) {
                data.append(String.format("ID: %d\n", p.getId()));
                data.append(String.format("Name: %s\n", p.getName()));
                data.append(String.format("Amount: %.2f\n", p.getAmount()));
                data.append(String.format("Due Day: %d\n", p.getDueDay()));
                data.append(String.format("Expiry Date: %s\n", p.getExpiryDate()));
                data.append(String.format("Is Completed: %b\n", p.isCompleted()));
                data.append(String.format("Last Completed Date: %s\n", p.getLastCompletedDate()));
                data.append("---\n");
            }

            // Log the data
            Log.d("DatabaseExport data:", data.toString());

            // Save to file
            File exportDir = new File(context.getFilesDir(), "database_exports");
            if (!exportDir.exists()) {
                Log.d("DatabaseExport", "directory do not exist. Creting it");
                exportDir.mkdirs();
            }

            String fileName = "database_export_" + System.currentTimeMillis() + ".txt";
            File exportFile = new File(exportDir, fileName);

            try (FileWriter writer = new FileWriter(exportFile)) {
                writer.write(data.toString());
            }

            if (exportFile.exists()) {
                Log.d("DatabaseExport", "File exists after writing: " + exportFile.getAbsolutePath());
            } else {
                Log.d("DatabaseExport", "File does NOT exist after writing!");
            }
            Log.d("DatabaseExport", "Data exported to: " + exportFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("DatabaseExport", "Error exporting data", e);
        }
    }
} 