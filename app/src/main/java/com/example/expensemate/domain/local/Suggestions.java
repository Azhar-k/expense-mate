package com.example.expensemate.domain.local;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.PastTransaction;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.util.Bills;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what a new transaction most likely is from the accounts, bills and earlier transactions
 * already on the phone. Each answer is given only when the evidence points one way; otherwise it is
 * null and the row keeps its defaults. Nothing here reads or writes the database.
 */
final class Suggestions {
    /** How many recent transactions are looked through. */
    static final int HISTORY_LIMIT = 2000;

    // Two payee names at least this alike are taken to be the same payee
    static final double SAME_PAYEE = 0.6;
    private static final int NEIGHBOURS = 5;
    private static final double CATEGORY_AGREEMENT = 0.6;
    private static final int MIN_ACCOUNT_VOTES = 2;
    private static final double ACCOUNT_AGREEMENT = 0.8;
    private static final double BILL_THRESHOLD = 3;

    // Account and card numbers as banks mask them: XX1234, *1234, "ending 1234"
    private static final Pattern MASKED_NUMBER = Pattern.compile("[Xx*]+\\s?(\\d{3,6})\\b");
    private static final Pattern ENDING_NUMBER = Pattern.compile(
            "(?i)\\bending(?:\\s+(?:in|with))?\\s*[Xx*]*(\\d{3,6})\\b");
    // Sender IDs such as AX-ICICIT-S: the middle part names the bank whatever the prefix
    private static final Pattern SMS_SENDER = Pattern.compile("^[A-Za-z0-9]{2}-([A-Za-z0-9]{3,})(?:-[A-Za-z])?$");
    // Payment rails and filler that say nothing about who was paid
    private static final Set<String> NOISE = new HashSet<>(Arrays.asList(
            "upi", "neft", "imps", "rtgs", "pos", "ach", "nach", "ecs", "ref", "info", "txn", "to", "from", "by",
            "at", "the", "ltd", "pvt", "private", "limited", "india", "payment", "payments", "bank", "transfer"));

    private Suggestions() {
    }

    /** The one real account whose number ends in digits the message shows, or null if none or several match. */
    static Long realAccountFromMessage(String body, List<Account> accounts) {
        if (body == null) {
            return null;
        }
        Set<String> shown = new LinkedHashSet<>();
        collectDigits(MASKED_NUMBER.matcher(body), shown);
        collectDigits(ENDING_NUMBER.matcher(body), shown);
        Set<Long> matches = new LinkedHashSet<>();
        for (String digits : shown) {
            for (Account account : accounts) {
                if (Account.TYPE_REAL.equals(account.getType()) && sameNumber(account.getAccountNumber(), digits)) {
                    matches.add(account.getId());
                }
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    /** The real account that reviewed messages from the same bank sender all went to. */
    static Long realAccountFromSender(String sender, List<PastTransaction> history) {
        String key = senderKey(sender);
        if (key == null) {
            return null;
        }
        List<Long> votes = new ArrayList<>();
        for (PastTransaction row : history) {
            if (!row.needsReview && key.equals(senderKey(row.smsSender))) {
                votes.add(row.realAccountId);
            }
        }
        return agreed(votes);
    }

    /** The account that reviewed payments to or from the same payee all went to. */
    static Long accountFromPayee(String receiverName, List<PastTransaction> history) {
        String payee = payeeName(receiverName);
        if (payee == null) {
            return null;
        }
        List<Long> votes = new ArrayList<>();
        for (PastTransaction row : history) {
            if (!row.needsReview && payee.equals(payeeName(row.receiverName))) {
                votes.add(row.accountId);
            }
        }
        return agreed(votes);
    }

    /**
     * The category most reviewed rows with a similar payee were given, weighted by how similar
     * they are. Only rows whose category is still among the choosable ones vote, so a deleted
     * category or the default one, which nobody chose, is never suggested.
     */
    static String category(String receiverName, String transactionType, Set<String> choosable,
            List<PastTransaction> history) {
        String payee = payeeName(receiverName);
        if (payee == null || transactionType == null) {
            return null;
        }
        List<double[]> scores = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        for (PastTransaction row : history) {
            if (row.needsReview || !transactionType.equals(row.transactionType)
                    || !choosable.contains(row.category)) {
                continue;
            }
            double similarity = similarity(payee, payeeName(row.receiverName));
            if (similarity >= SAME_PAYEE) {
                scores.add(new double[] { similarity, categories.size() });
                categories.add(row.category);
            }
        }
        scores.sort((a, b) -> Double.compare(b[0], a[0]));
        Map<String, Double> weights = new LinkedHashMap<>();
        double total = 0;
        for (int i = 0; i < Math.min(NEIGHBOURS, scores.size()); i++) {
            String category = categories.get((int) scores.get(i)[1]);
            weights.merge(category, scores.get(i)[0], Double::sum);
            total += scores.get(i)[0];
        }
        String best = null;
        double bestWeight = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() > bestWeight) {
                best = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return best != null && bestWeight / total >= CATEGORY_AGREEMENT ? best : null;
    }

    /**
     * The unpaid bill a spend most likely pays. Amount, due date, the payee having paid the bill
     * before and the payee's name matching the bill's each add to a score; the best bill is
     * suggested only when it scores high enough and no other bill scores the same.
     */
    static Long bill(Transaction transaction, List<RecurringPayment> bills, List<PastTransaction> history) {
        if (!"DEBIT".equals(transaction.getTransactionType()) || transaction.isTransfer()
                || transaction.getDate() == null) {
            return null;
        }
        Calendar day = Calendar.getInstance();
        day.setTime(transaction.getDate());
        String payee = payeeName(transaction.getReceiverName());

        Set<Long> paidBefore = new HashSet<>();
        if (payee != null) {
            for (PastTransaction row : history) {
                if (row.linkedRecurringPaymentId != null && row.id != transaction.getId()
                        && similarity(payee, payeeName(row.receiverName)) >= SAME_PAYEE) {
                    paidBefore.add(row.linkedRecurringPaymentId);
                }
            }
        }

        Long best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondScore = Double.NEGATIVE_INFINITY;
        for (RecurringPayment bill : bills) {
            if (Bills.isTransfer(bill) || Bills.hasEnded(bill, day) || Bills.isPaid(bill, day)) {
                continue;
            }
            double score = amountScore(transaction.getAmount(), bill.getAmount());
            int daysFromDue = Math.abs(Bills.daysUntilDue(bill, day));
            if (daysFromDue <= 3) {
                score += 1;
            } else if (daysFromDue <= Bills.DUE_SOON_DAYS) {
                score += 0.5;
            }
            if (paidBefore.contains(bill.getId())) {
                score += 2;
            } else if (payee != null && similarity(payee, payeeName(bill.getName())) >= SAME_PAYEE) {
                score += 1.5;
            }
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = bill.getId();
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        return bestScore >= BILL_THRESHOLD && bestScore > secondScore ? best : null;
    }

    /**
     * A payee's name without payment-rail words, UPI handles and reference numbers, or null when
     * nothing that names someone is left. "rahul.sharma@okhdfc" becomes "rahul sharma".
     */
    static String payeeName(String raw) {
        if (!TransactionKeys.hasMessageText(raw)) {
            return null;
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int at = text.indexOf('@');
        if (at > 0) {
            text = text.substring(0, at);
        }
        StringBuilder name = new StringBuilder();
        for (String word : text.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty() || NOISE.contains(word) || word.matches(".*\\d.*")) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(word);
        }
        return name.length() >= 3 ? name.toString() : null;
    }

    /**
     * How alike two payee names are, from 0 to 1. It averages how many three-letter pieces the
     * names share with how many words of the shorter name turn up in the longer one, so "swiggy"
     * still comes close to "swiggy instamart" while two people with one shared first name do not.
     */
    static double similarity(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        String[] aWords = a.split(" ");
        String[] bWords = b.split(" ");
        String[] fewer = aWords.length <= bWords.length ? aWords : bWords;
        String[] more = fewer == aWords ? bWords : aWords;
        int found = 0;
        for (String word : fewer) {
            for (String other : more) {
                // Close spellings count, as banks shorten names differently
                if (dice(word, other) >= 0.75) {
                    found++;
                    break;
                }
            }
        }
        return (dice(a, b) + (double) found / fewer.length) / 2;
    }

    private static double dice(String a, String b) {
        Set<String> left = trigrams(a);
        Set<String> right = trigrams(b);
        int shared = 0;
        for (String piece : left) {
            if (right.contains(piece)) {
                shared++;
            }
        }
        return 2.0 * shared / (left.size() + right.size());
    }

    private static Set<String> trigrams(String name) {
        String padded = " " + name + " ";
        Set<String> pieces = new HashSet<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            pieces.add(padded.substring(i, i + 3));
        }
        return pieces;
    }

    // Bills like electricity vary month to month, so a loose amount costs little and a far one counts against
    private static double amountScore(double paid, double billed) {
        if (billed <= 0) {
            return 0;
        }
        double gap = Math.abs(paid - billed) / billed;
        if (gap <= 0.01) {
            return 2;
        }
        if (gap <= 0.1) {
            return 1;
        }
        return gap <= 0.5 ? 0 : -1;
    }

    private static Long agreed(List<Long> votes) {
        Map<Long, Integer> counts = new HashMap<>();
        int total = 0;
        for (Long vote : votes) {
            if (vote != null) {
                counts.merge(vote, 1, Integer::sum);
                total++;
            }
        }
        if (total < MIN_ACCOUNT_VOTES) {
            return null;
        }
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= ACCOUNT_AGREEMENT * total) {
                return entry.getKey();
            }
        }
        return null;
    }

    static String senderKey(String sender) {
        if (sender == null || sender.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = SMS_SENDER.matcher(sender.trim());
        return (matcher.matches() ? matcher.group(1) : sender.trim()).toUpperCase(Locale.ROOT);
    }

    private static void collectDigits(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    // An account saved with its full number or only its last digits matches the masked number either way
    private static boolean sameNumber(String accountNumber, String shown) {
        if (accountNumber == null) {
            return false;
        }
        String digits = accountNumber.replaceAll("\\D", "");
        return digits.length() >= 3 && (digits.endsWith(shown) || shown.endsWith(digits));
    }
}
