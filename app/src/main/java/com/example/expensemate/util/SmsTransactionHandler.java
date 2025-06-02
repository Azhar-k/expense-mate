package com.example.expensemate.util;

import android.util.Log;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.util.Date;
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

    // Pattern for ICICI Bank debit format
    private static final Pattern ICICI_DEBIT_PATTERN = Pattern.compile(
        "(?i)ICICI Bank (?:Acct|Acc) XX(\\d+) (?:debited for |debited )Rs\\.? (\\d+(?:,\\d+)*(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}|\\d{2}/\\d{2}/\\d{4})[^;]*?;\\s*([^*]+)(?:credited|Info)"
    );

    // Pattern for ICICI Bank credit format
    private static final Pattern ICICI_CREDIT_PATTERN = Pattern.compile(
        "(?i)(?:Dear Customer, Acct XX(\\d+) is credited with Rs (\\d+(?:,\\d+)*(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}) from ([^.]+)|ICICI Bank Account XX(\\d+) credited:Rs\\.? (\\d+(?:,\\d+)*(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2})\\. Info NEFT-([^-]+)-)"
    );

    // Pattern for Kotak Bank debit format
    private static final Pattern KOTAK_DEBIT_PATTERN = Pattern.compile(
        "Sent Rs\\.?(\\d+(?:\\.\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to ([^\\s]+) on (\\d{2}-\\d{2}-\\d{2})\\.UPI Ref (\\d+)"
    );

    // Pattern for Kotak Bank credit format
    private static final Pattern KOTAK_CREDIT_PATTERN = Pattern.compile(
        "Received Rs\\.?(\\d+(?:\\.\\d{2})?) in your Kotak Bank AC ([A-Z0-9]+) from ([^\\s]+) on (\\d{2}-\\d{2}-\\d{2})\\.UPI Ref:(\\d+)"
    );

    // Pattern for SBI Credit Card debit format
    private static final Pattern SBI_DEBIT_PATTERN = Pattern.compile(
        "Rs\\.?(\\d+(?:,\\d+)*(?:\\.\\d{2})?) spent on your SBI Credit Card ending with (\\d{4}) at ([^\\s]+) on (\\d{2}-\\d{2}-\\d{2}) via UPI \\(Ref No\\. (\\d+)\\)"
    );

    // Comprehensive pattern for debit transactions (keeping as fallback)
    private static final Pattern GENERAL_DEBIT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:debited|spent|paid|sent|transferred|withdrawn)\\s*(?:from|in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:to|for|at)?\\s*([A-Za-z0-9@\\s\\.]+)"
    );

    // Comprehensive pattern for credit transactions (keeping as fallback)
    private static final Pattern GENERAL_CREDIT_PATTERN = Pattern.compile(
        "(?i)(?:Received|credited|deposited)\\s+(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:from|by)?\\s*([A-Za-z0-9@\\s\\.]+)(?:\\s+on\\s+(\\d{2}-\\d{2}-\\d{2}|\\d{2}-[A-Za-z]{3}-\\d{2}))?(?:\\.|$)"
    );

    // Pattern for Federal Bank debit format
    private static final Pattern FEDERAL_DEBIT_PATTERN = Pattern.compile(
        "Rs (\\d+(?:\\.\\d{2})?) debited via UPI on (\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}) to VPA ([^\\s]+)\\.Ref No (\\d+)\\.Small txns\\?Use UPI Lite!-Federal Bank"
    );

    // Pattern for Federal Bank credit format
    private static final Pattern FEDERAL_CREDIT_PATTERN = Pattern.compile(
        "Dear Customer, Rs\\.?(\\d+(?:\\.\\d{2})?) credited to your A/c XX(\\d+) on (\\d{2}[A-Z]{3}\\d{4} \\d{2}:\\d{2}:\\d{2})\\. BAL-Rs\\.(\\d+(?:\\.\\d{2})?)-Federal Bank"
    );

    // Pattern for Pluxee (Meal Card) debit format
    private static final Pattern PLUXEE_DEBIT_PATTERN = Pattern.compile(
        "Rs\\.? (\\d+(?:\\.\\d{2})?) spent from Pluxee\\s+Meal Card wallet, card no\\.(?:xx\\d+)? on (\\d{1,2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}) at ([^.]+)\\. Avl bal Rs\\.(\\d+(?:\\.\\d{2})?)\\. Not you call 18002106919"
    );

    // Pattern for Pluxee (Meal Card) credit format
    private static final Pattern PLUXEE_CREDIT_PATTERN = Pattern.compile(
        "Your Pluxee Card (?:xx\\d+)? has been (?:successfully )?credited with (?:Rs\\.?|INR) (\\d+(?:\\.\\d{2})?) (?:towards\\s+Meal Wallet|as a reversal against a previous transaction) on (?:[A-Za-z]{3} )?(\\d{2} [A-Za-z]{3} \\d{4} \\d{2}:\\d{2}:\\d{2})(?:as a reversal against a previous transaction on [A-Za-z]{3} \\d{2},\\d{4} \\d{2}:\\d{2}:\\d{2})?\\.(?: Your current Meal Wallet balance is Rs\\.(\\d+(?:\\.\\d{2})?)\\.)?"
    );

    private static final Pattern UPI_DEBIT_PATTERN = Pattern.compile(
            "(?i)(?:debited\\s+(?:for|Rs\\.?|INR)?\\s*)([\\d,]+\\.\\d{2}).*?;\\s*([A-Z][A-Za-z\\s\\.\\-]+)\\s+credited"
    );

    private static final Pattern UPI_CREDIT_PATTERN = Pattern.compile(
            "(?i)(?:Acct\\s+\\w+\\s+is\\s+credited\\s+with\\s+(?:Rs\\.?|INR)?\\s*([\\d,]+\\.\\d{2})\\s+from\\s+([A-Z][A-Za-z\\s\\.\\-]+))"
    );

    private static final Pattern NEFT_CREDIT_PATTERN = Pattern.compile(
            "(?i)Account\\s+\\w+\\s+credited:Rs\\.\\s*([\\d,]+\\.\\d{2}).*?NEFT[-]?([A-Z0-9]+)"
    );

    private static final Pattern CREDIT_CARD_SPEND_PATTERN = Pattern.compile(
            "(?i)Rs\\.?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)\\s+spent\\s+on\\s+(?:your\\s+)?(?:[A-Z]+\\s+)?(?:Credit\\s+)?Card(?:\\s+ending\\s+with\\s+\\d{4}|\\s+XX\\d{4})?.*?at\\s+([A-Z0-9@&\\-\\s\\.]+?)\\s+(?:on\\s+\\d{2}-\\d{2}-\\d{2}|via|Ref|\\.|$)"
    );

    private static final Pattern ICICI_CARD_SPEND_PATTERN = Pattern.compile(
            "Rs\\.?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)\\s+spent on ICICI Bank Card XX\\d{4} on (\\d{2}-[A-Za-z]{3}-\\d{2}) at ([A-Z0-9\\s\\.\\-&]+)"
    );

    private static final Pattern ICICI_ALT_DEBIT_PATTERN = Pattern.compile(
            "Rs\\.?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2}))\\s+debited from ICICI Bank Acc XX\\d+ on (\\d{2}-[A-Za-z]{3}-\\d{2})\\s+([A-Z0-9\\*\\s\\.]+)\\s+Bal"
    );

    private static final Pattern NEFT_CREDIT_BY_PATTERN = Pattern.compile(
            "(?:INR|Rs\\.?)[ ]?(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2}))\\s+credited to your A/c No XX\\d+ on \\d{2}/\\d{2}/\\d{4}.*?by ([A-Z][A-Za-z\\s\\.]+)"
    );

    private static final Pattern ICICI_INFOBIL_NEFT_DEBIT_PATTERN = Pattern.compile(
            "ICICI Bank Acc XX\\d+ debited Rs\\.?\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})) on (\\d{2}-[A-Za-z]{3}-\\d{2}) InfoBIL\\*NEFT\\*([A-Z0-9\\.]+)"
    );


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
                // Remove commas from amount before parsing
                String amountStr = iciciDebitMatcher.group(2).replace(",", "");
                double amount = Double.parseDouble(amountStr);
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
                // Handle both credit patterns
                String amountStr;
                String senderName;
                if (iciciCreditMatcher.group(1) != null) {
                    // First pattern matched (UPI credit)
                    amountStr = iciciCreditMatcher.group(2).replace(",", "");
                    senderName = iciciCreditMatcher.group(4).trim();
                } else {
                    // Second pattern matched (NEFT credit)
                    amountStr = iciciCreditMatcher.group(6).replace(",", "");
                    senderName = "NEFT-" + iciciCreditMatcher.group(8).trim();
                }
                double amount = Double.parseDouble(amountStr);
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

            // Try SBI Credit Card pattern
            var sbiDebitMatcher = SBI_DEBIT_PATTERN.matcher(smsBody);
            if (sbiDebitMatcher.find()) {
                String amountStr = sbiDebitMatcher.group(1).replace(",", "");
                double amount = Double.parseDouble(amountStr);
                String merchantName = sbiDebitMatcher.group(3).trim();
                Log.d(TAG, "Found SBI Credit Card debit transaction: " + amount + " at " + merchantName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "DEBIT",
                    merchantName,
                    smsBody,
                    sender
                );
            }

            // Try general patterns as fallback
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

            // Try Federal Bank patterns
            var federalDebitMatcher = FEDERAL_DEBIT_PATTERN.matcher(smsBody);
            if (federalDebitMatcher.find()) {
                double amount = Double.parseDouble(federalDebitMatcher.group(1));
                String receiverName = federalDebitMatcher.group(3).trim();
                Log.d(TAG, "Found Federal Bank debit transaction: " + amount + " to " + receiverName);

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

            var federalCreditMatcher = FEDERAL_CREDIT_PATTERN.matcher(smsBody);
            if (federalCreditMatcher.find()) {
                double amount = Double.parseDouble(federalCreditMatcher.group(1));
                String senderName = "Federal Bank";
                Log.d(TAG, "Found Federal Bank credit transaction: " + amount + " from " + senderName);

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

            // Try Pluxee patterns
            var pluxeeDebitMatcher = PLUXEE_DEBIT_PATTERN.matcher(smsBody);
            if (pluxeeDebitMatcher.find()) {
                double amount = Double.parseDouble(pluxeeDebitMatcher.group(1));
                String merchantName = pluxeeDebitMatcher.group(3).trim();
                Log.d(TAG, "Found Pluxee debit transaction: " + amount + " at " + merchantName);

                return new Transaction(
                    amount,
                    smsBody,
                    new Date(),
                    "DEBIT",
                    merchantName,
                    smsBody,
                    sender
                );
            }

            var pluxeeCreditMatcher = PLUXEE_CREDIT_PATTERN.matcher(smsBody);
            if (pluxeeCreditMatcher.find()) {
                double amount = Double.parseDouble(pluxeeCreditMatcher.group(1));
                String senderName = "Pluxee";
                Log.d(TAG, "Found Pluxee credit transaction: " + amount + " from " + senderName);

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

            // Try UPI debit (e.g., ICICI generic debit format)
            var upiDebitMatcher = UPI_DEBIT_PATTERN.matcher(smsBody);
            if (upiDebitMatcher.find()) {
                double amount = Double.parseDouble(upiDebitMatcher.group(1).replace(",", ""));
                String receiverName = upiDebitMatcher.group(2).trim();
                Log.d(TAG, "Found UPI debit transaction: " + amount + " to " + receiverName);

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

// Try UPI credit
            var upiCreditMatcher = UPI_CREDIT_PATTERN.matcher(smsBody);
            if (upiCreditMatcher.find()) {
                double amount = Double.parseDouble(upiCreditMatcher.group(1).replace(",", ""));
                String senderName = upiCreditMatcher.group(2).trim();
                Log.d(TAG, "Found UPI credit transaction: " + amount + " from " + senderName);

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

// Try NEFT credit
            var neftCreditMatcher = NEFT_CREDIT_PATTERN.matcher(smsBody);
            if (neftCreditMatcher.find()) {
                double amount = Double.parseDouble(neftCreditMatcher.group(1).replace(",", ""));
                String senderName = "NEFT-" + neftCreditMatcher.group(2).trim();
                Log.d(TAG, "Found NEFT credit transaction: " + amount + " from " + senderName);

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

            var iciciCardSpendMatcher = ICICI_CARD_SPEND_PATTERN.matcher(smsBody);
            if (iciciCardSpendMatcher.find()) {
                double amount = Double.parseDouble(iciciCardSpendMatcher.group(1).replace(",", ""));
                String merchant = iciciCardSpendMatcher.group(3).trim();
                Log.d(TAG, "Found ICICI Credit Card spend: " + amount + " at " + merchant);

                return new Transaction(
                        amount,
                        smsBody,
                        new Date(),
                        "DEBIT",
                        merchant,
                        smsBody,
                        sender
                );
            }

            var ccSpendMatcher = CREDIT_CARD_SPEND_PATTERN.matcher(smsBody);
            if (ccSpendMatcher.find()) {
                double amount = Double.parseDouble(ccSpendMatcher.group(1).replace(",", ""));
                String merchant = ccSpendMatcher.group(2).trim();
                Log.d(TAG, "Found Credit Card spend: " + amount + " at " + merchant);

                return new Transaction(
                        amount,
                        smsBody,
                        new Date(),
                        "DEBIT",
                        merchant,
                        smsBody,
                        sender
                );
            }

            var iciciAltDebitMatcher = ICICI_ALT_DEBIT_PATTERN.matcher(smsBody);
            if (iciciAltDebitMatcher.find()) {
                double amount = Double.parseDouble(iciciAltDebitMatcher.group(1).replace(",", ""));
                String receiver = iciciAltDebitMatcher.group(3).trim();
                Log.d(TAG, "Found ICICI alt debit: " + amount + " to " + receiver);

                return new Transaction(
                        amount,
                        smsBody,
                        new Date(),
                        "DEBIT",
                        receiver,
                        smsBody,
                        sender
                );
            }

            var neftCreditByMatcher = NEFT_CREDIT_BY_PATTERN.matcher(smsBody);
            if (neftCreditByMatcher.find()) {
                double amount = Double.parseDouble(neftCreditByMatcher.group(1).replace(",", ""));
                String senderName = neftCreditByMatcher.group(2).trim();
                Log.d(TAG, "Found NEFT credit by sender: " + amount + " from " + senderName);

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

            var iciciNeftDebitMatcher = ICICI_INFOBIL_NEFT_DEBIT_PATTERN.matcher(smsBody);
            if (iciciNeftDebitMatcher.find()) {
                double amount = Double.parseDouble(iciciNeftDebitMatcher.group(1).replace(",", ""));
                String ref = iciciNeftDebitMatcher.group(3).trim();
                String receiver = "NEFT-" + ref;
                Log.d(TAG, "Found ICICI NEFT debit: " + amount + " to " + receiver);

                return new Transaction(
                        amount,
                        smsBody,
                        new Date(),
                        "DEBIT",
                        receiver,
                        smsBody,
                        sender
                );
            }

            Log.d(TAG, "No transaction pattern matched in SMS:"+ smsBody);
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Extracts transaction details from SMS and processes it if valid
     * @param smsBody The SMS message body
     * @param sender The SMS sender
     * @param viewModel The TransactionViewModel to use for database operations
     * @param date Optional date to set for the transaction (null for current date)
     * @return TransactionResult containing the result of processing
     */
    public static TransactionResult handleSms(String smsBody, String sender, TransactionViewModel viewModel, Date date) {
        Log.d(TAG, "Processing SMS from: " + sender);
        Log.d(TAG, "SMS body length: " + (smsBody != null ? smsBody.length() : 0));
        Log.d(TAG, "Full SMS body: [" + smsBody + "]");

        try {
            Transaction transaction = extractTransactionDetails(smsBody, sender);
            if (transaction != null) {
                if (date != null) {
                    transaction.setDate(date);
                }
                Log.d(TAG, "Transaction extracted: " + transaction.getAmount() + " " +
                        transaction.getTransactionType() + " to/from " + transaction.getReceiverName());
                Log.d(TAG, "Transaction SMS body length: " + (transaction.getSmsBody() != null ? transaction.getSmsBody().length() : 0));
                Log.d(TAG, "Transaction SMS body: [" + transaction.getSmsBody() + "]");

                if (processTransaction(transaction, viewModel)) {
                    return TransactionResult.success(transaction);
                } else {
                    return TransactionResult.duplicateTransaction();
                }
            } else {
                Log.d(TAG, "No transaction details could be extracted");
                return TransactionResult.noPatternMatch(smsBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
            return TransactionResult.error("Error processing SMS: " + e.getMessage());
        }
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