package com.example.expensemate.util;

import android.util.Log;
import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.domain.CaptureService;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern MASKED = Pattern.compile("(?i)[x*]+\\d+");
    private static final Pattern DATE_LIKE = Pattern.compile("[\\d\\s:/.-]+|\\d{1,2}[-\\s]?[A-Za-z]{3}[-\\s]?\\d{2,4}.*");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Reads a message with the first pattern that matches it.
     *
     * @return the transaction, or null if no pattern matched
     */
    public static Transaction extractTransactionDetails(String smsBody, String sender, List<MessagePattern> patterns) {
        if (smsBody == null) {
            return null;
        }
        for (MessagePattern pattern : patterns) {
            Transaction transaction = readWith(pattern, smsBody, sender);
            if (transaction != null) {
                Log.d(TAG, "Matched pattern " + pattern.getName());
                return transaction;
            }
        }
        Log.d(TAG, "No transaction pattern matched in SMS");
        return null;
    }

    /**
     * Reads a message with one pattern. Named groups say what each part is: amount, which is
     * required, and merchant. A pattern without names gets its amount from the first group that
     * is a number, and its merchant from the first one that is not a number, date or card number.
     *
     * @return the transaction, or null if the pattern does not match or finds no amount
     */
    public static Transaction readWith(MessagePattern pattern, String smsBody, String sender) {
        Pattern compiled = pattern.compiled();
        if (compiled == null || smsBody == null) {
            return null;
        }
        Matcher matcher = compiled.matcher(smsBody);
        if (!matcher.find()) {
            return null;
        }
        boolean named = compiled.pattern().contains("(?<amount>");
        String amountText = named ? matcher.group("amount") : null;
        int amountGroup = 0;
        if (!named) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String value = matcher.group(i);
                if (value != null && NUMBER.matcher(value.trim()).matches()) {
                    amountText = value;
                    amountGroup = i;
                    break;
                }
            }
        }
        Double amount = parseAmount(amountText);
        if (amount == null) {
            return null;
        }

        String merchant = null;
        if (compiled.pattern().contains("(?<merchant>")) {
            merchant = matcher.group("merchant");
        } else if (!named) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String value = matcher.group(i);
                if (i != amountGroup && value != null && looksLikeName(value.trim())) {
                    merchant = value;
                    break;
                }
            }
        }
        merchant = merchant == null ? null : SPACES.matcher(merchant.trim()).replaceAll(" ");

        Transaction transaction = new Transaction(amount, smsBody, new Date(), pattern.getTransactionType(),
                counterparty(pattern.getCounterparty(), merchant), smsBody, sender);
        if (pattern.isExcludeFromSummary()) {
            transaction.setExcludedFromSummary(true);
        }
        return transaction;
    }

    private static Double parseAmount(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean looksLikeName(String value) {
        return !value.isEmpty() && !NUMBER.matcher(value).matches() && !MASKED.matcher(value).matches()
                && !DATE_LIKE.matcher(value).matches();
    }

    // The pattern's fixed counterparty, with {merchant} standing for what was captured
    private static String counterparty(String template, String merchant) {
        if (template == null || template.trim().isEmpty()) {
            return merchant == null || merchant.isEmpty() ? null : merchant;
        }
        return template.replace("{merchant}", merchant == null ? "" : merchant).trim();
    }

    /**
     * Extracts transaction details from a message and saves them unless already recorded
     *
     * @param smsBody   The SMS message body
     * @param sender    The SMS sender
     * @param capture   Saves the transaction unless it is already recorded
     * @param date      Optional date to set for the transaction (null for current
     *                  date)
     * @param source    The entry path, one of the TransactionKeys.SOURCE_ values
     * @return TransactionResult containing the result of processing
     */
    public static TransactionResult handleSms(String smsBody, String sender, CaptureService capture,
            Date date, String source) {
        Log.d(TAG, "Processing SMS from: " + sender + " via " + source);
        Log.d(TAG, "SMS body length: " + (smsBody != null ? smsBody.length() : 0));

        try {
            Transaction transaction = extractTransactionDetails(smsBody, sender, capture.patterns());
            if (transaction != null) {
                if (date != null) {
                    transaction.setDate(date);
                }
                Log.d(TAG, "Transaction extracted: " + transaction.getTransactionType());
                Log.d(TAG, "Transaction SMS body length: "
                        + (transaction.getSmsBody() != null ? transaction.getSmsBody().length() : 0));

                applyKeys(transaction, source);
                transaction.setDedupKey(TransactionKeys.bodyKey(smsBody));

                if (capture.recordIfNew(transaction)) {
                    return TransactionResult.success(transaction);
                } else {
                    Log.d(TAG, "Duplicate transaction detected, skipping insertion");
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
     * Saves a Truecaller business chat alert, which only gives the amount and merchant.
     * The alert does not say whether money came in or went out, so it is recorded as a
     * debit. That is right for SBI card spends; anything else is labelled for review.
     */
    public static TransactionResult handleTruecallerAlert(double amount, String merchant, String alertText,
            String sender, CaptureService capture, Date date) {
        try {
            boolean sbiCard = sender.toUpperCase(Locale.ROOT).contains("SBI");
            Transaction transaction = new Transaction(
                    amount,
                    (sbiCard ? "RCS: " : "RCS (check type): ") + merchant,
                    date,
                    "DEBIT",
                    merchant,
                    alertText,
                    sender);
            applyKeys(transaction, TransactionKeys.SOURCE_TRUECALLER);

            if (capture.recordIfNew(transaction)) {
                return TransactionResult.success(transaction);
            }
            return TransactionResult.duplicateTransaction();
        } catch (Exception e) {
            Log.e(TAG, "Error processing Truecaller alert: " + e.getMessage(), e);
            return TransactionResult.error("Error processing Truecaller alert: " + e.getMessage());
        }
    }

    private static void applyKeys(Transaction transaction, String source) {
        transaction.setSource(source);
        transaction.setNeedsReview(!transaction.isExcludedFromSummary());
        transaction.setMessageTime(transaction.getDate().getTime());
        transaction.setMatchKey(TransactionKeys.matchKey(transaction.getAmount(),
                transaction.getTransactionType(), transaction.getReceiverName()));
    }
}