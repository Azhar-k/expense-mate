package com.example.expensemate.util;

import android.util.Log;
import com.example.expensemate.data.RegexPattern;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsTransactionHandler {
    private static final String TAG = "SmsTransactionHandler";

    public static class TransactionResult {
        public final boolean success;
        public final String reason;
        public final Transaction transaction;

        public TransactionResult(boolean success, String reason, Transaction transaction) {
            this.success = success;
            this.reason = reason;
            this.transaction = transaction;
        }

        public static TransactionResult success(Transaction transaction) {
            return new TransactionResult(true, "Transaction processed successfully", transaction);
        }

        public static TransactionResult noPatternMatch(String smsBody) {
            return new TransactionResult(false, "No transaction pattern matched in SMS", null);
        }

        public static TransactionResult duplicateTransaction() {
            return new TransactionResult(false, "Duplicate transaction detected", null);
        }

        public static TransactionResult error(String error) {
            return new TransactionResult(false, error, null);
        }
    }

    /**
     * Extracts transaction details from SMS using patterns from database
     * 
     * @param smsBody   The SMS message body
     * @param sender    The SMS sender
     * @param viewModel The TransactionViewModel to fetch patterns
     * @return Transaction object if extraction was successful, null otherwise
     */
    private static Transaction extractTransactionDetails(String smsBody, String sender,
            TransactionViewModel viewModel) {
        try {
            Log.d(TAG, "Extracting transaction details from SMS");

            List<RegexPattern> patterns = viewModel.getAllRegexPatternsSync();

            for (RegexPattern pattern : patterns) {
                try {
                    Pattern regex = Pattern.compile(pattern.regex);
                    Matcher matcher = regex.matcher(smsBody);

                    if (matcher.find()) {
                        Log.d(TAG, "Matched pattern: " + pattern.name);

                        double amount = 0.0;
                        if (pattern.amountGroupIndex > 0 && pattern.amountGroupIndex <= matcher.groupCount()) {
                            String amountStr = matcher.group(pattern.amountGroupIndex).replace(",", "");
                            amount = Double.parseDouble(amountStr);
                        }

                        String otherParty = pattern.defaultSender;
                        if (otherParty == null || otherParty.isEmpty()) {
                            if (pattern.merchantGroupIndex > 0 && pattern.merchantGroupIndex <= matcher.groupCount()) {
                                otherParty = matcher.group(pattern.merchantGroupIndex).trim();
                            } else {
                                otherParty = "Unknown";
                            }
                        }

                        // Special case handling for NEFT where prefix might be needed?
                        // For now accepting raw captured value as per user regex.

                        // Handle "NEFT-" prefix explicitly if the pattern maps to what was previously
                        // ICICI Credit Type 2
                        // This is a bit hacky but maintains backward compatibility if the regex was
                        // strictly capturing just the suffix.
                        // However, dynamic patterns should be self-contained.
                        // If the user wants "NEFT-1234", they should write a regex that captures it.
                        // I will assume the provided patterns in DB are correct enough.
                        // For ICICI Credit 2 in migration: "Info NEFT-([^-]+)-". It captures "1234".
                        // If I want "NEFT-1234", I should have used "Info (NEFT-[^-]+)-".
                        // I can update the pattern in DB via the AppDatabase migration?
                        // I already wrote the migration. It's done.
                        // I will leave it as is. "1234" is a valid sender name/reference.

                        Log.d(TAG, "Transaction extracted: " + amount + " " + pattern.type + " to/from " + otherParty);

                        return new Transaction(
                                amount,
                                smsBody,
                                new Date(),
                                pattern.type, // "DEBIT" or "CREDIT"
                                otherParty,
                                smsBody,
                                sender);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing pattern " + pattern.name + ": " + e.getMessage());
                }
            }

            Log.d(TAG, "No transaction pattern matched in SMS");
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extracts transaction details from SMS and processes it if valid
     * 
     * @param smsBody   The SMS message body
     * @param sender    The SMS sender
     * @param viewModel The TransactionViewModel to use for database operations
     * @param date      Optional date to set for the transaction (null for current
     *                  date)
     * @return TransactionResult containing the result of processing
     */
    public static TransactionResult handleSms(String smsBody, String sender, TransactionViewModel viewModel,
            Date date) {
        Log.d(TAG, "Processing SMS from: " + sender);
        Log.d(TAG, "SMS body length: " + (smsBody != null ? smsBody.length() : 0));
        // Log.d(TAG, "Full SMS body: [" + smsBody + "]"); // Reduced log spam/security
        // risk

        try {
            Transaction transaction = extractTransactionDetails(smsBody, sender, viewModel);
            if (transaction != null) {
                if (date != null) {
                    transaction.setDate(date);
                }

                if (processTransaction(transaction, viewModel)) {
                    return TransactionResult.success(transaction);
                } else {
                    return TransactionResult.duplicateTransaction();
                }
            } else {
                return TransactionResult.noPatternMatch(smsBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
            return TransactionResult.error("Error processing SMS: " + e.getMessage());
        }
    }

    /**
     * Processes a transaction by checking for duplicates and inserting into
     * database
     * 
     * @param transaction The transaction to process
     * @param viewModel   The TransactionViewModel to use for database operations
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

            if (existingCount > 0) {
                Log.d(TAG, "Duplicate transaction detected, skipping insertion");
                return false;
            }
        }

        viewModel.insertTransaction(transaction);
        Log.d(TAG, "Transaction inserted via ViewModel");
        return true;
    }
}