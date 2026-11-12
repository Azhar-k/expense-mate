package com.example.expensemate.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Amounts kept apart by currency, since money in different currencies is never added together.
 * Shown as "₹1,200 · AED 40", with the main currency first.
 */
public final class CurrencyTotals {
    private final Map<String, Double> amounts = new HashMap<>();

    public CurrencyTotals add(String currency, double amount) {
        String code = currency != null ? currency : Currencies.LEGACY;
        amounts.merge(code, amount, Double::sum);
        return this;
    }

    public CurrencyTotals addAll(CurrencyTotals other) {
        for (Map.Entry<String, Double> entry : other.amounts.entrySet()) {
            add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /** The amounts that are not zero, main currency first. */
    public Map<String, Double> nonZero(String main) {
        return Currencies.nonZero(amounts, main);
    }

    public boolean isZero() {
        return nonZero(null).isEmpty();
    }

    public double get(String currency) {
        Double amount = amounts.get(currency);
        return amount != null ? amount : 0;
    }

    /** Every currency that is not zero, or zero in the given currency when nothing is left. */
    public String format(String main, String whenZero) {
        Map<String, Double> shown = nonZero(main);
        return shown.isEmpty() ? Money.format(0, whenZero) : Money.formatAll(shown);
    }

    /** As format, with a sign on each amount. Empty when everything is zero. */
    public String signed(String main) {
        return Money.signedAll(nonZero(main));
    }

    /** True when there is something and all of it is below zero, so the whole line can show as a warning. */
    public boolean allNegative() {
        Map<String, Double> shown = nonZero(null);
        if (shown.isEmpty()) {
            return false;
        }
        for (double amount : shown.values()) {
            if (amount >= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CurrencyTotals && nonZero(null).equals(((CurrencyTotals) other).nonZero(null));
    }

    @Override
    public int hashCode() {
        return nonZero(null).hashCode();
    }
}
