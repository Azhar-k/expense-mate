package com.example.expensemate.util;

import android.util.Log;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.util.Date;
import java.util.regex.Pattern;

public class SmsTransactionHandler {
    private static final String TAG = "SmsTransactionHandler";

    // Pattern for ICICI Bank debit format
    private static final Pattern ICICI_DEBIT_PATTERN = Pattern.compile(
        "ICICI Bank Acct XX(\\d+) debited for Rs (\\d+(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}); ([^;]+) credited"
    );

    // Pattern for ICICI Bank credit format
    private static final Pattern ICICI_CREDIT_PATTERN = Pattern.compile(
        "(?i)Dear Customer, Acct XX(\\d+) is credited with Rs (\\d+(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}) from ([^.]+)(?:\\.|$)"
    );

    // Pattern for Kotak Bank debit format
    private static final Pattern KOTAK_DEBIT_PATTERN = Pattern.compile(
        "Sent Rs\\.?(\\d+(?:\\.\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to ([^\\s]+)"
    );

    // Pattern for Kotak Bank credit format
    private static final Pattern KOTAK_CREDIT_PATTERN = Pattern.compile(
        "(?i)Received Rs\\.?(\\d+(?:\\.\\d{2})?) in (?:your )?Kotak Bank AC ([A-Z0-9]+) from ([^\\s]+)(?: on (\\d{2}-\\d{2}-\\d{2}))?(?:\\.|$)"
    );

    // Comprehensive pattern for debit transactions
    private static final Pattern GENERAL_DEBIT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:debited|spent|paid|sent|transferred|withdrawn)\\s*(?:from|in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:to|for|at)?\\s*([A-Za-z0-9@\\s\\.]+)"
    );

    // Comprehensive pattern for credit transactions
    private static final Pattern GENERAL_CREDIT_PATTERN = Pattern.compile(
        "(?i)(?:Received|credited|deposited)\\s+(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:from|by)?\\s*([A-Za-z0-9@\\s\\.]+)(?:\\s+on\\s+(\\d{2}-\\d{2}-\\d{2}|\\d{2}-[A-Za-z]{3}-\\d{2}))?(?:\\.|$)"
    );

    /**
     * Extracts transaction details from SMS and processes it if valid
     * @param smsBody The SMS message body
     * @param sender The SMS sender
     * @param viewModel The TransactionViewModel to use for database operations
     * @param date Optional date to set for the transaction (null for current date)
     * @return true if a transaction was successfully processed, false otherwise
     */
    public static boolean handleSms(String smsBody, String sender, TransactionViewModel viewModel, Date date) {
        Log.d(TAG, "Processing SMS from: " + sender);
        Log.d(TAG, "SMS body: " + smsBody);

        Transaction transaction = extractTransactionDetails(smsBody, sender);
        if (transaction != null) {
            if (date != null) {
                transaction.setDate(date);
            }
            Log.d(TAG, "Transaction extracted: " + transaction.getAmount() + " " + 
                  transaction.getTransactionType() + " to/from " + transaction.getReceiverName());
            return processTransaction(transaction, viewModel);
        } else {
            Log.d(TAG, "No transaction details could be extracted");
            return false;
        }
    }

    /**
     * Extracts transaction details from SMS
     * @param smsBody The SMS message body
     * @param sender The SMS sender
     * @return Transaction object if extraction was successful, null otherwise
     */
    private static Transaction extractTransactionDetails(String smsBody, String sender) {
        try {
            Log.d(TAG, "Extracting transaction details from SMS");

            // Try ICICI patterns
            var iciciDebitMatcher = ICICI_DEBIT_PATTERN.matcher(smsBody);
            if (iciciDebitMatcher.find()) {
                double amount = Double.parseDouble(iciciDebitMatcher.group(2));
                String receiverName = iciciDebitMatcher.group(4).trim();
                Log.d(TAG, "Found ICICI debit transaction: " + amount + " to " + receiverName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            var iciciCreditMatcher = ICICI_CREDIT_PATTERN.matcher(smsBody);
            if (iciciCreditMatcher.find()) {
                double amount = Double.parseDouble(iciciCreditMatcher.group(2));
                String senderName = iciciCreditMatcher.group(4).trim();
                Log.d(TAG, "Found ICICI credit transaction: " + amount + " from " + senderName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "CREDIT",
                    senderName,
                    smsBody,
                    sender
                );
            }

            // Try Kotak patterns
            var kotakDebitMatcher = KOTAK_DEBIT_PATTERN.matcher(smsBody);
            if (kotakDebitMatcher.find()) {
                double amount = Double.parseDouble(kotakDebitMatcher.group(1));
                String receiverName = kotakDebitMatcher.group(3).trim();
                Log.d(TAG, "Found Kotak debit transaction: " + amount + " to " + receiverName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            var kotakCreditMatcher = KOTAK_CREDIT_PATTERN.matcher(smsBody);
            if (kotakCreditMatcher.find()) {
                double amount = Double.parseDouble(kotakCreditMatcher.group(1));
                String senderName = kotakCreditMatcher.group(3).trim();
                Log.d(TAG, "Found Kotak credit transaction: " + amount + " from " + senderName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "CREDIT",
                    senderName,
                    smsBody,
                    sender
                );
            }

            // Try general patterns
            var generalDebitMatcher = GENERAL_DEBIT_PATTERN.matcher(smsBody);
            if (generalDebitMatcher.find()) {
                double amount = Double.parseDouble(generalDebitMatcher.group(1));
                String receiverName = generalDebitMatcher.group(2).trim();
                Log.d(TAG, "Found general debit transaction: " + amount + " to " + receiverName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            var generalCreditMatcher = GENERAL_CREDIT_PATTERN.matcher(smsBody);
            if (generalCreditMatcher.find()) {
                double amount = Double.parseDouble(generalCreditMatcher.group(1));
                String senderName = generalCreditMatcher.group(2).trim();
                Log.d(TAG, "Found general credit transaction: " + amount + " from " + senderName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "CREDIT",
                    senderName,
                    smsBody,
                    sender
                );
            }

            Log.d(TAG, "No transaction pattern matched in SMS");
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Processes a transaction by checking for duplicates and inserting into database
     * @param transaction The transaction to process
     * @param viewModel The TransactionViewModel to use for database operations
     * @return true if transaction was successfully processed, false otherwise
     */
    private static boolean processTransaction(Transaction transaction, TransactionViewModel viewModel) {
        if (transaction == null) {
            Log.d(TAG, "No transaction to process");
            return false;
        }

        String smsHash = transaction.getSmsHash();
        Log.d(TAG, "Checking for duplicate transaction with hash: " + smsHash);
        
        if (smsHash != null) {
            int existingCount = viewModel.countTransactionsBySmsHash(smsHash);
            Log.d(TAG, "Found " + existingCount + " existing transactions with same hash");
            
            if (existingCount > 0) {
                Log.d(TAG, "Duplicate transaction detected, skipping insertion");
                return false;
            }
        } else {
            Log.d(TAG, "No SMS hash generated for transaction");
        }

        viewModel.insertTransaction(transaction);
        Log.d(TAG, "Transaction inserted via ViewModel");
        return true;
    }
} 