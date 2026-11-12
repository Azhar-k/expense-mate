package com.example.expensemate.ui.common;

import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.util.Money;

import java.util.Locale;

/** How a transaction is named and pictured on screen. */
public final class TransactionText {
    private TransactionText() {
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("null");
    }

    /** The person or shop if known, then the note, then the category. */
    public static String title(Transaction transaction) {
        if (hasText(transaction.getReceiverName())) {
            return transaction.getReceiverName().trim();
        }
        if (hasText(transaction.getDescription())) {
            return transaction.getDescription().trim();
        }
        return hasText(transaction.getCategory()) ? transaction.getCategory() : "Transaction";
    }

    public static String accountName(java.util.Map<Long, String> names, Long accountId) {
        String name = accountId != null ? names.get(accountId) : null;
        return name != null ? name : "No account";
    }

    /**
     * What a transfer moved: one amount, or what left and what arrived when the two real accounts hold
     * different currencies, such as "₹2,270 → AED 100".
     */
    public static String transferAmount(Transaction shown, Transaction debit, Transaction credit) {
        if (debit != null && credit != null && !debit.getCurrency().equals(credit.getCurrency())) {
            return Money.format(debit.getAmount(), debit.getCurrency()) + " → "
                    + Money.format(credit.getAmount(), credit.getCurrency());
        }
        return Money.format(shown.getAmount(), shown.getCurrency());
    }

    /** Names the side the money moved on: a transfer can stay in one virtual account and change real account. */
    public static String transferRoute(java.util.Map<Long, String> names, Transaction debit, Transaction credit) {
        if (debit == null || credit == null) {
            return (debit != null ? accountName(names, debit.getAccountId()) : "Unknown") + " → "
                    + (credit != null ? accountName(names, credit.getAccountId()) : "Unknown");
        }
        boolean sameVirtual = java.util.Objects.equals(debit.getAccountId(), credit.getAccountId());
        return accountName(names, sameVirtual ? debit.getRealAccountId() : debit.getAccountId()) + " → "
                + accountName(names, sameVirtual ? credit.getRealAccountId() : credit.getAccountId());
    }

    public static int categoryIcon(String category) {
        String name = category != null ? category.toLowerCase(Locale.ROOT) : "";
        if (containsAny(name, "food", "eat", "dining", "restaurant", "cafe", "lunch", "dinner")) {
            return R.drawable.ic_food;
        }
        if (containsAny(name, "grocer", "shop", "market", "supermarket")) {
            return R.drawable.ic_cart;
        }
        if (containsAny(name, "cloth", "fashion", "personal", "bag", "gift")) {
            return R.drawable.ic_bag;
        }
        if (containsAny(name, "fuel", "petrol", "car", "transport", "travel", "metro", "cab", "bus")) {
            return R.drawable.ic_car;
        }
        if (containsAny(name, "home", "house", "rent", "maintenance")) {
            return R.drawable.ic_home;
        }
        if (containsAny(name, "bill", "electric", "utilit", "phone", "internet", "recharge")) {
            return R.drawable.ic_bolt;
        }
        if (containsAny(name, "entertain", "movie", "film", "stream", "fun")) {
            return R.drawable.ic_film;
        }
        if (containsAny(name, "charity", "donat", "love")) {
            return R.drawable.ic_heart;
        }
        if (containsAny(name, "family", "friend", "kid", "people")) {
            return R.drawable.ic_person;
        }
        if (containsAny(name, "school", "education", "tuition", "admission", "course")) {
            return R.drawable.ic_school;
        }
        if (containsAny(name, "medic", "health", "hospital", "doctor", "pharma")) {
            return R.drawable.ic_medical;
        }
        if (containsAny(name, "salary", "income", "business", "bonus", "wallet", "saving", "invest")) {
            return R.drawable.ic_wallet;
        }
        return R.drawable.ic_tag;
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
