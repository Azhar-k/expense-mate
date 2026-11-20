package com.example.expensemate.data;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL for schemas before version 18, when funds and accounts shared the accounts table as VIRTUAL and
 * REAL rows, and transactions named them accountId and realAccountId. Old migrations run against those
 * schemas, so this stays exactly as it was; nothing on the current schema may call it.
 */
final class LegacySchema {
    static final String TYPE_VIRTUAL = "VIRTUAL";
    static final String TYPE_REAL = "REAL";
    static final String DEFAULT_REAL_NAME = "Bank account";

    private LegacySchema() {
    }

    /**
     * Gives real accounts a default, and that default to any transaction without a real account.
     * A transaction whose virtual account is missing is left as it is until someone assigns one.
     */
    static void ensureDefaultRealAccount(SupportSQLiteDatabase database) {
        database.execSQL("INSERT INTO accounts (name, accountNumber, bank, description, isDefault, type) "
                + "SELECT ?, '', '', '', 0, ? WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE type = ?)",
                new Object[] { DEFAULT_REAL_NAME, TYPE_REAL, TYPE_REAL });
        database.execSQL("UPDATE accounts SET isDefault = 1 WHERE id = (SELECT MIN(id) FROM accounts WHERE type = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM accounts WHERE type = ? AND isDefault = 1)",
                new Object[] { TYPE_REAL, TYPE_REAL });
        database.execSQL("UPDATE transactions SET realAccountId = "
                + "(SELECT id FROM accounts WHERE type = ? AND isDefault = 1) WHERE realAccountId IS NULL "
                + "AND accountId IN (SELECT id FROM accounts WHERE type = ?)",
                new Object[] { TYPE_REAL, TYPE_VIRTUAL });
    }

    /** A real account always says what its money is in; one without gets this currency. */
    static void ensureRealAccountCurrencies(SupportSQLiteDatabase database, String currency) {
        database.execSQL("UPDATE accounts SET currency = ? WHERE type = ? AND currency IS NULL",
                new Object[] { currency, TYPE_REAL });
    }

    /**
     * {@link Transfers#linkLegacyPairs} as it was when transactions named their fund accountId, for
     * MIGRATION_7_8. Links manual rows that can only be two sides of one transfer: same moment,
     * amount, note and category, opposite types, different accounts, and no message behind them.
     * A row matching more than one candidate is left alone rather than guessed at.
     *
     * @return the number of transfers linked
     */
    static int linkLegacyPairs(SupportSQLiteDatabase database) {
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
