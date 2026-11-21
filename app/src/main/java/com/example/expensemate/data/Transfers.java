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

    /**
     * A move between funds stays in one account, so the bank never sees it; a transfer changes account.
     * Both are stored the same way, as a debit and a credit, so this is read from the two sides.
     */
    public static boolean isMove(Transaction debit, Transaction credit) {
        return debit != null && credit != null && debit.getAccountId() != null
                && debit.getAccountId().equals(credit.getAccountId());
    }

    /** Both sides in one fund: only where the money is changed. */
    public static boolean staysInFund(Transaction debit, Transaction credit) {
        return debit != null && credit != null && debit.getFundId() != null
                && debit.getFundId().equals(credit.getFundId());
    }

    /**
     * Legs between two funds. Callers set each leg's account, since the funds say nothing about it.
     *
     * @return the debit and credit sides, in that order
     */
    public static Transaction[] newPair(double amount, long fromFundId, long toFundId, Date date, String note) {
        String groupId = UUID.randomUUID().toString();
        return new Transaction[] {
                leg(amount, fromFundId, date, note, "DEBIT", groupId),
                leg(amount, toFundId, date, note, "CREDIT", groupId)
        };
    }

    private static Transaction leg(double amount, long fundId, Date date, String note, String type, String groupId) {
        Transaction leg = new Transaction();
        leg.setAmount(amount);
        leg.setDate(date);
        leg.setDescription(note);
        leg.setTransactionType(type);
        leg.setReceiverName("");
        leg.setCategory(CATEGORY);
        leg.setFundId(fundId);
        leg.setTransferGroupId(groupId);
        return leg;
    }

    /**
     * Links manual rows that can only be two sides of one transfer: same moment, amount,
     * note and category, opposite types, different funds, and no message behind them.
     * A row matching more than one candidate is left alone rather than guessed at. Older backups
     * hold such rows, so a restore comes through here; MIGRATION_7_8 uses the copy in LegacySchema.
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
                + "AND d.fundId IS NOT NULL AND c.fundId IS NOT NULL AND d.fundId <> c.fundId "
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
