package com.example.expensemate.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.expensemate.R;

import java.io.BufferedReader;
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
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing entity in section " + section, e);
        }
    }

    private void processTransaction(String data) {
        try {
            Transaction transaction = new Transaction();
            String[] lines = data.split("\n");
            for (String line : lines) {
                String[] parts = line.split(": ", 2);
                if (parts.length != 2) continue;
                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("Transaction", "key:"+key+" val:"+value);

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
                    case "Linked Payment ID":
                        if (!value.equals("null")) {
                            transaction.setLinkedRecurringPaymentId(Long.parseLong(value));
                        }
                        break;
                }
            }

            if (transaction.getTransactionType() == null) {
                Log.d("Transaction", "Transaction type is null. Not inserting the transaction. Id:"+ transaction.getId());
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
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("Category", "key:"+key+" val:"+value);
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
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();
                Log.d("RecPayment", "key:"+key+" val:"+value);

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
                //Do not insert
            } else {
                database.recurringPaymentDao().insert(payment);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing recurring payment date", e);
        }
    }
} 