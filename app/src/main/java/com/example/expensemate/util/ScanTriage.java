package com.example.expensemate.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A scan reads the whole inbox, so most unmatched messages are not about money at all.
 * This keeps the "not understood" list down to messages that look like a real transaction.
 */
public final class ScanTriage {
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY_MOVED = Pattern.compile(
            "\\b(debited|credited|spent|paid|sent|received|withdrawn|transferred|txn|transaction|purchase)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_A_PAYMENT = Pattern.compile(
            "\\b(otp|one time password|verification code|offer|cashback|win|won|loan of|pre-approved|preapproved|"
                    + "due on|due date|minimum due|will be debited|recharge now)\\b",
            Pattern.CASE_INSENSITIVE);

    private ScanTriage() {
    }

    public static boolean looksLikeTransaction(String body) {
        if (body == null) {
            return false;
        }
        String text = body.toLowerCase(Locale.ROOT);
        return AMOUNT.matcher(text).find() && MONEY_MOVED.matcher(text).find() && !NOT_A_PAYMENT.matcher(text).find();
    }

    /** @return the first amount in the message, or null */
    public static Double amountIn(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = AMOUNT.matcher(body);
        return matcher.find() ? Money.parse(matcher.group(1)) : null;
    }
}
