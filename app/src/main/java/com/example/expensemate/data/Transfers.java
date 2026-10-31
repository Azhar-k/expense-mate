package com.example.expensemate.data;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Creates transfers and links the older rows that were saved as two unrelated transactions. */
public final class Transfers {
    public static final String CATEGORY = "Transfer";

    private Transfers() {
    }

    /** @return the debit and credit sides, in that order */
    public static Transaction[] newPair(double amount, long fromAccountId, long toAccountId, Date date, String note) {
        String groupId = UUID.randomUUID().toString();
        return new Transaction[] {
                leg(amount, fromAccountId, date, note, "DEBIT", groupId),
                leg(amount, toAccountId, date, note, "CREDIT", groupId)
        };
    }

    private static Transaction leg(double amount, long accountId, Date date, String note, String type, String groupId) {
        Transaction leg = new Transaction();
        leg.setAmount(amount);
        leg.setDate(date);
        leg.setDescription(note);
        leg.setTransactionType(type);
        leg.setReceiverName("");
        leg.setCategory(CATEGORY);
        leg.setAccountId(accountId);
        leg.setTransferGroupId(groupId);
        return leg;
    }

    /**
     * Links manual rows that can only be two sides of one transfer: same moment, amount,
     * note and category, opposite types, different accounts, and no message behind them.
     * A row matching more than one candidate is left alone rather than guessed at.
     *
     * @return the number of transfers linked
     */
    public static int linkLegacyPairs(SupportSQLiteDatabase database) {
        String manual = "COALESCE(%1$s.smsBody, '') IN ('', 'null')";
        String sql = "SELECT d.id, c.id FROM transactions d JOIN transactions c "
                + "ON d.date = c.date AND d.amount = c.amount "
                + "AND COALESCE(d.description, '') = COALESCE(c.description, '') "
                + "AND COALESCE(d.category, '') = COALESCE(c.category, '') "
                + "WHERE d.transactionType = 'DEBIT' AND c.transactionType = 'CREDIT' "
                + "AND d.transferGroupId IS NULL AND c.transferGroupId IS NULL "
                + "AND d.accountId IS NOT NULL AND c.accountId IS NOT NULL AND d.accountId <> c.accountId "
                + "AND " + String.format(manual, "d") + " AND " + String.format(manual, "c");

        Map<Long, Integer> debitMatches = new HashMap<>();
        Map<Long, Integer> creditMatches = new HashMap<>();
        Map<Long, Long> creditForDebit = new HashMap<>();
        try (Cursor cursor = database.query(sql)) {
            while (cursor.moveToNext()) {
                long debitId = cursor.getLong(0);
                long creditId = cursor.getLong(1);
                debitMatches.merge(debitId, 1, Integer::sum);
                creditMatches.merge(creditId, 1, Integer::sum);
                creditForDebit.put(debitId, creditId);
            }
        }

        int linked = 0;
        for (Map.Entry<Long, Long> pair : creditForDebit.entrySet()) {
            long debitId = pair.getKey();
            long creditId = pair.getValue();
            if (debitMatches.get(debitId) == 1 && creditMatches.get(creditId) == 1) {
                database.execSQL("UPDATE transactions SET transferGroupId = ?, needsReview = 0 WHERE id IN (?, ?)",
                        new Object[] { "legacy-" + debitId, debitId, creditId });
                linked++;
            }
        }
        return linked;
    }
}
