package com.example.expensemate.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats and reads rupee amounts: ₹1,67,976 and −₹984.50. Paise show only when they are not zero. */
public final class Money {
    private static final char MINUS = '−';

    private Money() {
    }

    public static String format(double amount) {
        BigDecimal rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        boolean negative = rounded.signum() < 0;
        rounded = rounded.abs();
        String[] parts = rounded.toPlainString().split("\\.");
        StringBuilder text = new StringBuilder();
        if (negative) {
            text.append(MINUS);
        }
        text.append('₹').append(group(parts[0]));
        if (!parts[1].equals("00")) {
            text.append('.').append(parts[1]);
        }
        return text.toString();
    }

    /** For money going out, shown as a positive number in the data. */
    public static String debit(double amount) {
        return MINUS + format(Math.abs(amount));
    }

    /** For money coming in, shown as a positive number in the data. */
    public static String credit(double amount) {
        return "+" + format(Math.abs(amount));
    }

    /** A running total that can go either way. */
    public static String signed(double amount) {
        return amount > 0 ? credit(amount) : format(amount);
    }

    /** @return the amount, or null unless the text is a number above zero */
    public static Double parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim().replace(",", ""));
            return value > 0 && !Double.isInfinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Plain digits for an edit field, with no trailing .0 */
    public static String plain(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    // Indian grouping: the last three digits, then pairs.
    private static String group(String digits) {
        int length = digits.length();
        if (length <= 3) {
            return digits;
        }
        StringBuilder grouped = new StringBuilder(digits.substring(length - 3));
        for (int end = length - 3; end > 0; end -= 2) {
            grouped.insert(0, digits.substring(Math.max(0, end - 2), end) + ",");
        }
        return grouped.toString();
    }
}
