package com.example.expensemate.util;

import com.example.expensemate.data.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * ISO 4217 currency codes, such as INR or AED. Money is only ever added up within one currency,
 * and the main currency, the one the default real account holds, is listed first.
 */
public final class Currencies {
    /** What every amount was in before currencies existed, and what upgraded data is labelled with. */
    public static final String LEGACY = "INR";
    private static final String FALLBACK = "USD";

    private Currencies() {
    }

    /** The currency of the phone's region, for a new install. */
    public static String fromLocale() {
        try {
            java.util.Currency currency = java.util.Currency.getInstance(Locale.getDefault());
            return currency != null ? currency.getCurrencyCode() : FALLBACK;
        } catch (IllegalArgumentException e) {
            // A language without a region, such as plain "en", has no currency
            return FALLBACK;
        }
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != 3) {
            return false;
        }
        try {
            java.util.Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The code in upper case, or null if it is not a currency. */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
        return isValid(upper) ? upper : null;
    }

    /** The default real account's currency, or the phone's when there is none yet. */
    public static String main(Collection<Account> accounts) {
        String firstReal = null;
        if (accounts != null) {
            for (Account account : accounts) {
                if (!account.isReal() || account.getCurrency() == null) {
                    continue;
                }
                if (account.isDefault()) {
                    return account.getCurrency();
                }
                if (firstReal == null) {
                    firstReal = account.getCurrency();
                }
            }
        }
        return firstReal != null ? firstReal : fromLocale();
    }

    /** The currency an account's money is in: its own for a real account, none for a virtual one. */
    public static String of(Account account) {
        return account != null && account.isReal() ? account.getCurrency() : null;
    }

    /** "Indian Rupee", in the phone's language. */
    public static String displayName(String code) {
        try {
            return java.util.Currency.getInstance(code).getDisplayName(Locale.getDefault());
        } catch (IllegalArgumentException | NullPointerException e) {
            return code;
        }
    }

    /** "INR · Indian Rupee", as the currency pickers show it. */
    public static String label(String code) {
        return code + " · " + displayName(code);
    }

    /** The code a picker label starts with, or null. */
    public static String fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        return normalize(trimmed.length() >= 3 ? trimmed.substring(0, 3) : trimmed);
    }

    /** Every currency for a picker: the main one, then those already in use, then the rest by code. */
    public static List<String> pickerCodes(String main, Collection<String> inUse) {
        Set<String> ordered = new LinkedHashSet<>();
        if (isValid(main)) {
            ordered.add(main);
        }
        if (inUse != null) {
            List<String> used = new ArrayList<>();
            for (String code : inUse) {
                if (isValid(code)) {
                    used.add(code);
                }
            }
            used.sort(Comparator.naturalOrder());
            ordered.addAll(used);
        }
        List<String> rest = new ArrayList<>();
        for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {
            rest.add(currency.getCurrencyCode());
        }
        rest.sort(Comparator.naturalOrder());
        ordered.addAll(rest);
        return new ArrayList<>(ordered);
    }

    /** The currencies of these real accounts, for putting them near the top of a picker. */
    public static Set<String> inUse(Collection<Account> accounts) {
        Set<String> codes = new LinkedHashSet<>();
        if (accounts != null) {
            for (Account account : accounts) {
                if (account.isReal() && account.getCurrency() != null) {
                    codes.add(account.getCurrency());
                }
            }
        }
        return codes;
    }

    /** Orders codes with the main currency first, then alphabetically. */
    public static Comparator<String> mainFirst(String main) {
        return (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            if (a.equals(main)) {
                return -1;
            }
            if (b.equals(main)) {
                return 1;
            }
            return a.compareTo(b);
        };
    }

    /** Sorts per-currency amounts with the main currency first, leaving out currencies that come to zero. */
    public static Map<String, Double> nonZero(Map<String, Double> amounts, String main) {
        Map<String, Double> sorted = new TreeMap<>(mainFirst(main));
        for (Map.Entry<String, Double> entry : amounts.entrySet()) {
            if (entry.getKey() != null && Math.abs(entry.getValue()) >= 0.005) {
                sorted.put(entry.getKey(), entry.getValue());
            }
        }
        return sorted;
    }
}
