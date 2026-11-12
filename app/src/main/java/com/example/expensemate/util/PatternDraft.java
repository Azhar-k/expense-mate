package com.example.expensemate.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a first pattern from one sample message, given where its amount and merchant are.
 * Numbers, dates and spacing are loosened so the next message from the same bank still
 * matches; the words around them stay as they are.
 */
public final class PatternDraft {
    static final String AMOUNT = "(?<amount>\\d[\\d,]*(?:\\.\\d+)?)";
    static final String MERCHANT = "(?<merchant>.+?)";
    private static final String MERCHANT_TO_END = "(?<merchant>.+)";

    // Kept from before the first marked part; earlier text such as a greeting varies more
    private static final int MAX_PREFIX = 60;

    private static final Pattern TOKEN = Pattern.compile(
            "(?<date>\\d{1,2}[-/ ]?[A-Za-z]{3}[-/ ]?\\d{2,4})|(?<number>\\d(?:[\\d,.]*\\d)?)|(?<space>\\s+)");
    private static final Pattern NEXT_WORDS = Pattern.compile("^\\S*\\s+\\S+|^\\S+");
    private static final String SPECIAL = "\\.[]{}()*+?^$|";

    private PatternDraft() {
    }

    /**
     * @param merchantStart -1 when no merchant is marked
     * @return the pattern, or null if the amount is not marked inside the message
     */
    public static String build(String message, int amountStart, int amountEnd, int merchantStart, int merchantEnd) {
        if (message == null || amountStart < 0 || amountEnd > message.length() || amountStart >= amountEnd) {
            return null;
        }
        boolean hasMerchant = merchantStart >= 0 && merchantEnd <= message.length() && merchantStart < merchantEnd
                && (merchantEnd <= amountStart || merchantStart >= amountEnd);

        int firstStart = hasMerchant ? Math.min(amountStart, merchantStart) : amountStart;
        StringBuilder regex = new StringBuilder("(?i)");
        regex.append(loosen(prefix(message.substring(0, firstStart))));

        if (!hasMerchant) {
            regex.append(AMOUNT);
            return regex.toString();
        }
        boolean amountFirst = amountStart < merchantStart;
        int firstEnd = amountFirst ? amountEnd : merchantEnd;
        int secondStart = amountFirst ? merchantStart : amountStart;
        int secondEnd = amountFirst ? merchantEnd : amountEnd;

        regex.append(amountFirst ? AMOUNT : MERCHANT);
        regex.append(loosen(message.substring(firstEnd, secondStart)));
        if (amountFirst) {
            // A lazy merchant needs the words after it to know where it stops
            Matcher next = NEXT_WORDS.matcher(message.substring(secondEnd));
            if (next.find()) {
                regex.append(MERCHANT).append(loosen(next.group()));
            } else {
                regex.append(MERCHANT_TO_END);
            }
        } else {
            regex.append(AMOUNT);
        }
        return regex.toString();
    }

    private static String prefix(String text) {
        if (text.length() <= MAX_PREFIX) {
            return text;
        }
        String tail = text.substring(text.length() - MAX_PREFIX);
        int space = tail.indexOf(' ');
        return space >= 0 ? tail.substring(space + 1) : tail;
    }

    static String loosen(String text) {
        StringBuilder out = new StringBuilder();
        Matcher token = TOKEN.matcher(text);
        int at = 0;
        while (token.find()) {
            escape(text.substring(at, token.start()), out);
            if (token.group("date") != null) {
                out.append("\\d{1,2}[-/ ]?[A-Za-z]{3}[-/ ]?\\d{2,4}");
            } else if (token.group("number") != null) {
                out.append("\\d[\\d,.]*");
            } else {
                out.append("\\s*");
            }
            at = token.end();
        }
        escape(text.substring(at), out);
        return out.toString();
    }

    private static void escape(String text, StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SPECIAL.indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
    }
}
