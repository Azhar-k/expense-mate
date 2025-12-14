package com.example.expensemate.data;

import android.content.Context;
import android.util.Log;

import com.example.expensemate.R;
import com.example.expensemate.service.GoogleDriveService;
import com.example.expensemate.service.GoogleSignInHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupDataLoader {
    private static final String TAG = "BackupDataLoader";
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final SimpleDateFormat dateFormat;
    private final Context context;

    public BackupDataLoader(Context context) {
        this.context = context;
        this.database = AppDatabase.getDatabase(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
    }

    public void loadBackupData() {
        executorService.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getResources().openRawResource(R.raw.local_backup_data)))) {
                String line;
                String currentSection = "";
                StringBuilder currentEntity = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("=== ")) {
                        // Process previous entity if exists
                        if (currentEntity.length() > 0) {
                            processEntity(currentSection, currentEntity.toString());
                            currentEntity = new StringBuilder();
                        }
                        currentSection = line.substring(4, line.length() - 4);
                        continue;
                    }

                    if (line.equals("---")) {
                        // Process current entity
                        processEntity(currentSection, currentEntity.toString());
                        currentEntity = new StringBuilder();
                    } else {
                        currentEntity.append(line).append("\n");
                    }
                }

                // Process last entity if exists
                if (currentEntity.length() > 0) {
                    processEntity(currentSection, currentEntity.toString());
                }

            } catch (IOException e) {
                Log.e(TAG, "Error reading backup file from resources", e);
            }
        });
    }

    private void processEntity(String section, String entityData) {
        try {
            switch (section) {
                case "TRANSACTIONS":
                    processTransaction(entityData);
                    break;
                case "CATEGORIES":
                    processCategory(entityData);
                    break;
                case "RECURRING PAYMENTS":
                    processRecurringPayment(entityData);
                    break;
                case "ACCOUNTS":
                    processAccount(entityData);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing entity in section " + section, e);
        }
    }

    private void processTransaction(String data) {
        try {
            Transaction transaction = new Transaction();
            transaction.setAccountId(database.accountDao().getDefaultAccountSync().getId());
            String[] lines = data.split("\n");
            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2)
                    continue;
                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("Transaction", "key:" + key + " val:" + value);
                Account defaultAccount = database.accountDao().getDefaultAccountSync();
                if (defaultAccount != null) {
                    Log.d("Transaction", "default account exist. Id:" + defaultAccount.getId());
                    transaction.setAccountId(defaultAccount.getId());
                } else {
                    Log.d("Transaction", "default account do not exist");
                }

                switch (key) {
                    case "ID":
                        transaction.setId(Long.parseLong(value));
                        break;
                    case "Amount":
                        transaction.setAmount(Double.parseDouble(value));
                        break;
                    case "Description":
                        transaction.setDescription(value);
                        break;
                    case "Date":
                        transaction.setDate(dateFormat.parse(value));
                        break;
                    case "Transaction Type":
                        if (value.trim().isEmpty()) {
                            Log.d("Transaction", "Setting value as DEBIT for transaction type as it is empty");
                            value = "DEBIT";
                        }
                        transaction.setTransactionType(value);
                        break;
                    case "Receiver":
                        transaction.setReceiverName(value);
                        break;
                    case "Category":
                        transaction.setCategory(value);
                        break;
                    case "Account id":
                        if (!value.equalsIgnoreCase("null")) {
                            transaction.setAccountId(Long.parseLong(value));
                        }
                        break;
                    case "Is excluded from summary":
                        if (!value.equals("null")) {
                            transaction.setExcludedFromSummary(Boolean.parseBoolean(value));
                        }
                        break;
                    case "Linked Payment ID":
                        if (!value.equals("null")) {
                            transaction.setLinkedRecurringPaymentId(Long.parseLong(value));
                        }
                        break;
                }
            }

            if (transaction.getTransactionType() == null) {
                Log.d("Transaction",
                        "Transaction type is null. Not inserting the transaction. Id:" + transaction.getId());
            } else {
                if (transaction.getReceiverName() == null) {
                    transaction.setReceiverName("");
                }
                if (transaction.getDescription() == null) {
                    transaction.setDescription("");
                }
                database.transactionDao().insertTransaction(transaction);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing transaction date", e);
        }
    }

    private void processCategory(String data) {
        try {
            String[] lines = data.split("\n");
            String name = "";
            String type = "";

            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2)
                    continue;

                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("Category", "key:" + key + " val:" + value);
                switch (key) {
                    case "Name":
                        name = value;
                        break;
                    case "Type":
                        type = value;
                        break;
                }
            }

            if (!name.isEmpty() && !type.isEmpty()) {
                Category category = new Category(name, type);
                List<Category> categoriesByName = database.categoryDao().getCategoriesByName(name);
                if (categoriesByName == null || categoriesByName.isEmpty()) {
                    database.categoryDao().insertCategory(category);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing category", e);
        }
    }

    private void processRecurringPayment(String data) {
        try {
            RecurringPayment payment = new RecurringPayment("", 0.0, 1, new Date());

            String[] lines = data.split("\n");
            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2)
                    continue;

                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("RecPayment", "key:" + key + " val:" + value);

                switch (key) {
                    case "ID":
                        payment.setId(Long.parseLong(value));
                        break;
                    case "Name":
                        payment.setName(value);
                        break;
                    case "Amount":
                        payment.setAmount(Double.parseDouble(value));
                        break;
                    case "Due Day":
                        payment.setDueDay(Integer.parseInt(value));
                        break;
                    case "Expiry Date":
                        payment.setExpiryDate(dateFormat.parse(value));
                        break;
                    case "Is Completed":
                        payment.setCompleted(Boolean.parseBoolean(value));
                        break;
                    case "Last Completed Date":
                        if (!value.equals("null")) {
                            payment.setLastCompletedDate(dateFormat.parse(value));
                        }
                        break;
                }
            }

            if (payment.getName() == null || payment.getName().trim().isEmpty()) {
                // Do not insert
            } else {
                database.recurringPaymentDao().insert(payment);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing recurring payment date", e);
        }
    }

    private void processAccount(String data) {
        try {
            String[] lines = data.split("\n");
            long originalId = -1;
            String name = "";
            String accountNumber = "";
            String bank = "";
            Date expiryDate = null;
            String description = "";
            boolean isDefault = false;

            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2)
                    continue;

                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("Account", "key:" + key + " val:" + value);

                switch (key) {
                    case "ID":
                        originalId = Long.parseLong(value);
                        break;
                    case "Name":
                        name = value;
                        break;
                    case "Account Number":
                        accountNumber = value;
                        break;
                    case "Bank":
                        bank = value;
                        break;
                    case "Expiry Date":
                        if (!value.equalsIgnoreCase("null")) {
                            expiryDate = dateFormat.parse(value);
                        }
                        break;
                    case "Description":
                        description = value;
                        break;
                }
            }

            if (!name.isEmpty()) {
                Account account = new Account(name, accountNumber, bank, expiryDate, description);

                // Set the original ID to preserve referential integrity
                if (originalId != -1) {
                    account.setId(originalId);
                }

                // Check if account with same ID already exists
                List<Account> existingAccounts = database.accountDao().getAllAccountsSync();
                boolean accountExists = false;
                if (existingAccounts != null) {
                    for (Account existingAccount : existingAccounts) {
                        if (existingAccount.getId() == originalId) {
                            accountExists = true;
                            break;
                        }
                    }
                }

                if (!accountExists) {
                    database.accountDao().insert(account);
                    Log.d("Account", "Inserted account: " + name + " with ID: " + originalId);
                } else {
                    Log.d("Account", "Account with ID " + originalId + " already exists: " + name);
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing account date", e);
        } catch (Exception e) {
            Log.e(TAG, "Error processing account", e);
        }
    }

    public static void exportDatabaseData(Context context, AppDatabase database) {
//        exportDatabaseDataToLocal(context, database);
    }

    public static void exportDatabaseDataToLocal(Context context, AppDatabase database) {
        try {
            StringBuilder data = new StringBuilder();
            data.append("=== Database Export ").append(new Date()).append(" ===\n\n");

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

            // Export accounts
            data.append("\n=== ACCOUNTS ===\n");
            List<Account> accounts = database.accountDao().getAllAccountsSync();
            if (accounts != null) {
                for (Account a : accounts) {
                    data.append(String.format("ID: %d\n", a.getId()));
                    data.append(String.format("Name: %s\n", a.getName()));
                    data.append(String.format("Account Number: %s\n", a.getAccountNumber()));
                    data.append(String.format("Bank: %s\n", a.getBank()));
                    data.append(String.format("Expiry Date: %s\n", a.getExpiryDate()));
                    data.append(String.format("Description: %s\n", a.getDescription()));
                    data.append("---\n");
                }
            }

            // Export transactions (last two months only)
            data.append("=== TRANSACTIONS ===\n");
            Date twoMonthsAgo = new Date(System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000 * 60)); // 60 days ago
            List<Transaction> transactions = database.transactionDao().getFilteredTransactions(
                    twoMonthsAgo, new Date(), null, null, null, null, null, null, null, null);
            for (Transaction t : transactions) {
                data.append(String.format("ID: %d\n", t.getId()));
                data.append(String.format("Amount: %.2f\n", t.getAmount()));
                data.append(String.format("Description: %s\n", t.getDescription()));
                data.append(String.format("Date: %s\n", t.getDate()));
                data.append(String.format("Transaction Type: %s\n", t.getTransactionType()));
                data.append(String.format("Receiver: %s\n", t.getReceiverName()));
                data.append(String.format("Category: %s\n", t.getCategory()));
                data.append(String.format("Is excluded from summary: %s\n", t.isExcludedFromSummary()));
                data.append(String.format("Account id: %s\n", t.getAccountId()));
                data.append(String.format("Linked Payment ID: %s\n", t.getLinkedRecurringPaymentId()));
                data.append("---\n");
            }

            // Save to file
            File exportDir = new File(context.getFilesDir(), "database_exports");
            if (!exportDir.exists()) {
                Log.d("DatabaseExport", "directory do not exist. Creating it");
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

    public static void exportDatabaseDataToGoogleDrive(Context context, AppDatabase database,
            GoogleDriveService.DriveCallback callback) {
        try {
            StringBuilder data = new StringBuilder();
            data.append("=== Database Export ").append(new Date()).append(" ===\n\n");

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

            // Export accounts
            data.append("\n=== ACCOUNTS ===\n");
            List<Account> accounts = database.accountDao().getAllAccountsSync();
            if (accounts != null) {
                for (Account a : accounts) {
                    data.append(String.format("ID: %d\n", a.getId()));
                    data.append(String.format("Name: %s\n", a.getName()));
                    data.append(String.format("Account Number: %s\n", a.getAccountNumber()));
                    data.append(String.format("Bank: %s\n", a.getBank()));
                    data.append(String.format("Expiry Date: %s\n", a.getExpiryDate()));
                    data.append(String.format("Description: %s\n", a.getDescription()));
                    data.append("---\n");
                }
            }

            // Export transactions (last two months only)
            data.append("=== TRANSACTIONS ===\n");
            Date twoMonthsAgo = new Date(System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000 * 60)); // 60 days ago
            List<Transaction> transactions = database.transactionDao().getFilteredTransactions(
                    twoMonthsAgo, new Date(), null, null, null, null, null, null, null, null);
            for (Transaction t : transactions) {
                data.append(String.format("ID: %d\n", t.getId()));
                data.append(String.format("Amount: %.2f\n", t.getAmount()));
                data.append(String.format("Description: %s\n", t.getDescription()));
                data.append(String.format("Date: %s\n", t.getDate()));
                data.append(String.format("Transaction Type: %s\n", t.getTransactionType()));
                data.append(String.format("Receiver: %s\n", t.getReceiverName()));
                data.append(String.format("Category: %s\n", t.getCategory()));
                data.append(String.format("Is excluded from summary: %s\n", t.isExcludedFromSummary()));
                data.append(String.format("Account id: %s\n", t.getAccountId()));
                data.append(String.format("Linked Payment ID: %s\n", t.getLinkedRecurringPaymentId()));
                data.append("---\n");
            }

            // Create temporary file
            File tempFile = new File(context.getCacheDir(), "temp_backup_" + System.currentTimeMillis() + ".txt");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(data.toString());
            }

            // Upload to Google Drive
            GoogleDriveService driveService = new GoogleDriveService(context);
            GoogleSignInHelper signInHelper = new GoogleSignInHelper(context);

            if (signInHelper.isSignedIn()) {
                driveService.initializeDriveService(signInHelper.getAccessToken());
                driveService.uploadBackupToDrive(tempFile, new GoogleDriveService.DriveCallback() {
                    @Override
                    public void onSuccess(String message) {
                        // Clean up temp file
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        callback.onSuccess(message);
                    }

                    @Override
                    public void onError(String error) {
                        // Clean up temp file
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        callback.onError(error);
                    }
                });
            } else {
                callback.onError("User not signed in to Google Drive");
            }

        } catch (Exception e) {
            Log.e("DatabaseExport", "Error exporting data to Google Drive", e);
            callback.onError("Error exporting data: " + e.getMessage());
        }
    }

    public static void loadBackupDataFromGoogleDrive(Context context, GoogleDriveService.DriveCallback callback) {
        try {
            // Create temporary file for download
            File tempFile = new File(context.getCacheDir(), "temp_restore_" + System.currentTimeMillis() + ".txt");

            GoogleDriveService driveService = new GoogleDriveService(context);
            GoogleSignInHelper signInHelper = new GoogleSignInHelper(context);

            if (signInHelper.isSignedIn()) {
                driveService.initializeDriveService(signInHelper.getAccessToken());
                driveService.downloadBackupFromDrive(tempFile, new GoogleDriveService.DriveCallback() {
                    @Override
                    public void onSuccess(String message) {
                        // Process the downloaded file
                        BackupDataLoader loader = new BackupDataLoader(context);
                        loader.loadBackupDataFromFile(tempFile, () -> {
                            // Clean up temp file
                            if (tempFile.exists()) {
                                tempFile.delete();
                            }
                            callback.onSuccess("Backup restored from Google Drive successfully");
                        });
                    }

                    @Override
                    public void onError(String error) {
                        // Clean up temp file
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        callback.onError(error);
                    }
                });
            } else {
                callback.onError("User not signed in to Google Drive");
            }

        } catch (Exception e) {
            Log.e("BackupDataLoader", "Error loading backup from Google Drive", e);
            callback.onError("Error loading backup: " + e.getMessage());
        }
    }

    public void loadBackupDataFromFile(File backupFile, Runnable onCompletion) {
        executorService.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new java.io.FileReader(backupFile))) {
                String line;
                String currentSection = "";
                StringBuilder currentEntity = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("=== ")) {
                        // Process previous entity if exists
                        if (currentEntity.length() > 0) {
                            processEntity(currentSection, currentEntity.toString());
                            currentEntity = new StringBuilder();
                        }
                        currentSection = line.substring(4, line.length() - 4);
                        continue;
                    }

                    if (line.equals("---")) {
                        // Process current entity
                        processEntity(currentSection, currentEntity.toString());
                        currentEntity = new StringBuilder();
                    } else {
                        currentEntity.append(line).append("\n");
                    }
                }

                // Process last entity if exists
                if (currentEntity.length() > 0) {
                    processEntity(currentSection, currentEntity.toString());
                }

            } catch (IOException e) {
                Log.e(TAG, "Error reading backup file", e);
            } finally {
                if (onCompletion != null) {
                    onCompletion.run();
                }
            }
        });
    }
}