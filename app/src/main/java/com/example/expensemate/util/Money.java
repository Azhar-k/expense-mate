package com.example.expensemate.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Formats and reads amounts in a currency: ₹1,67,976, −€984.50, AED 1,200. The minor unit shows only
 * when it is not zero, with as many digits as the currency uses (none for JPY, three for KWD).
 */
public final class Money {
    private static final char MINUS = '−';
    private static final String SEPARATOR = " · ";

    // Signs that stand for one currency only, used when the phone's language has just the code for them
    private static final Map<String, String> UNIQUE_SIGNS = new HashMap<>();
    // Grouped the South Asian way: the last three digits, then pairs
    private static final Set<String> LAKH_GROUPING = new HashSet<>(Arrays.asList("INR", "NPR", "PKR", "BDT"));

    static {
        String[][] signs = { { "INR", "₹" }, { "EUR", "€" }, { "GBP", "£" }, { "KRW", "₩" }, { "NGN", "₦" },
                { "PHP", "₱" }, { "ILS", "₪" }, { "VND", "₫" }, { "THB", "฿" }, { "TRY", "₺" }, { "UAH", "₴" },
                { "RUB", "₽" }, { "BDT", "৳" }, { "KZT", "₸" }, { "PYG", "₲" }, { "GHS", "₵" }, { "CRC", "₡" },
                { "MNT", "₮" } };
        for (String[] sign : signs) {
            UNIQUE_SIGNS.put(sign[0], sign[1]);
        }
    }

    private Money() {
    }

    public static String format(double amount, String currency) {
        String code = Currencies.isValid(currency) ? currency : Currencies.LEGACY;
        int digits = fractionDigits(code);
        BigDecimal rounded = BigDecimal.valueOf(amount).setScale(digits, RoundingMode.HALF_UP);
        boolean negative = rounded.signum() < 0;
        rounded = rounded.abs();
        String[] parts = rounded.toPlainString().split("\\.");
        StringBuilder text = new StringBuilder();
        if (negative) {
            text.append(MINUS);
        }
        String symbol = symbol(code);
        text.append(symbol);
        // A sign made of letters, such as AED or CHF, would run into the digits
        if (Character.isLetter(symbol.charAt(symbol.length() - 1))) {
            text.append(' ');
        }
        text.append(LAKH_GROUPING.contains(code) ? groupInLakhs(parts[0]) : groupInThousands(parts[0]));
        if (parts.length > 1 && !parts[1].matches("0+")) {
            text.append('.').append(parts[1]);
        }
        return text.toString();
    }

    /** For money going out, shown as a positive number in the data. */
    public static String debit(double amount, String currency) {
        return MINUS + format(Math.abs(amount), currency);
    }

    /** For money coming in, shown as a positive number in the data. */
    public static String credit(double amount, String currency) {
        return "+" + format(Math.abs(amount), currency);
    }

    /** A running total that can go either way. */
    public static String signed(double amount, String currency) {
        return amount > 0 ? credit(amount, currency) : format(amount, currency);
    }

    /** One amount per currency, in the map's order: "₹1,200 · AED 40". Empty when there is nothing. */
    public static String formatAll(Map<String, Double> amounts) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : amounts.entrySet()) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            text.append(format(entry.getValue(), entry.getKey()));
        }
        return text.toString();
    }

    /** The same as formatAll, for totals that can go either way. */
    public static String signedAll(Map<String, Double> amounts) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : amounts.entrySet()) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            text.append(signed(entry.getValue(), entry.getKey()));
        }
        return text.toString();
    }

    /** What goes before the digits: ₹, $, AED. */
    public static String symbol(String currency) {
        String code = Currencies.isValid(currency) ? currency : Currencies.LEGACY;
        String symbol = java.util.Currency.getInstance(code).getSymbol(Locale.getDefault());
        if (symbol.equals(code) && UNIQUE_SIGNS.containsKey(code)) {
            return UNIQUE_SIGNS.get(code);
        }
        return symbol;
    }

    static int fractionDigits(String code) {
        int digits = java.util.Currency.getInstance(code).getDefaultFractionDigits();
        // Gold, special drawing rights and the like have none defined
        return digits < 0 ? 2 : digits;
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

    private static String groupInLakhs(String digits) {
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

    private static String groupInThousands(String digits) {
        StringBuilder grouped = new StringBuilder();
        int length = digits.length();
        for (int i = 0; i < length; i++) {
            if (i > 0 && (length - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
