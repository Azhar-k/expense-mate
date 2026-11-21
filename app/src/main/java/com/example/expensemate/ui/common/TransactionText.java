package com.example.expensemate.ui.common;

import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.Transfers;
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

    /** Names from the accounts table only: fund and account ids can be equal. */
    public static String accountName(java.util.Map<Long, String> accountNames, Long accountId) {
        String name = accountId != null ? accountNames.get(accountId) : null;
        return name != null ? name : "No account";
    }

    /** Names from the funds table only. */
    public static String fundName(java.util.Map<Long, String> fundNames, Long fundId) {
        String name = fundId != null ? fundNames.get(fundId) : null;
        return name != null ? name : "No fund";
    }

    /**
     * What a transfer moved: one amount, or what left and what arrived when the two accounts hold
     * different currencies, such as "₹2,270 → AED 100".
     */
    public static String transferAmount(Transaction shown, Transaction debit, Transaction credit) {
        if (debit != null && credit != null && !debit.getCurrency().equals(credit.getCurrency())) {
            return Money.format(debit.getAmount(), debit.getCurrency()) + " → "
                    + Money.format(credit.getAmount(), credit.getCurrency());
        }
        return Money.format(shown.getAmount(), shown.getCurrency());
    }

    /** A move names the funds; a transfer names the accounts, since that is what changed. */
    public static String transferRoute(java.util.Map<Long, String> fundNames, java.util.Map<Long, String> accountNames,
            Transaction debit, Transaction credit) {
        if (debit == null || credit == null) {
            return (debit != null ? fundName(fundNames, debit.getFundId()) : "Unknown") + " → "
                    + (credit != null ? fundName(fundNames, credit.getFundId()) : "Unknown");
        }
        if (Transfers.isMove(debit, credit)) {
            return fundName(fundNames, debit.getFundId()) + " → " + fundName(fundNames, credit.getFundId());
        }
        return accountName(accountNames, debit.getAccountId()) + " → "
                + accountName(accountNames, credit.getAccountId());
    }

    /** "Move" or "Transfer". A transfer that also changed fund says which, as nothing else on the row does. */
    public static String transferKind(java.util.Map<Long, String> fundNames, Transaction debit, Transaction credit) {
        if (Transfers.isMove(debit, credit)) {
            return "Move";
        }
        if (debit != null && credit != null && !Transfers.staysInFund(debit, credit)) {
            return "Transfer · " + fundName(fundNames, debit.getFundId()) + " → "
                    + fundName(fundNames, credit.getFundId());
        }
        return "Transfer";
    }

    /** The word for a pair on its own, as in "Move deleted". */
    public static String transferNoun(Transaction debit, Transaction credit) {
        return Transfers.isMove(debit, credit) ? "Move" : "Transfer";
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
