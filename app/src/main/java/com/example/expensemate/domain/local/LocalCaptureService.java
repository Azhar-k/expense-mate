package com.example.expensemate.domain.local;

import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.data.store.UnitOfWork;
import com.example.expensemate.domain.CaptureService;

final class LocalCaptureService implements CaptureService {
    private final TransactionStore store;
    private final TransactionRules rules;
    private final UnitOfWork unitOfWork;

    LocalCaptureService(TransactionStore store, TransactionRules rules, UnitOfWork unitOfWork) {
        this.store = store;
        this.rules = rules;
        this.unitOfWork = unitOfWork;
    }

    // The check and the insert share one unit of work, so two entry paths cannot both pass.
    @Override
    public boolean recordIfNew(Transaction parsed) {
        return unitOfWork.call(() -> {
            if (isDuplicate(parsed)) {
                return false;
            }
            rules.fillDefaultAccounts(parsed);
            rules.applyDefaultCategory(parsed);
            rules.applyRememberedCategory(parsed);
            store.insert(parsed);
            return true;
        });
    }

    private boolean isDuplicate(Transaction transaction) {
        long fromTime = transaction.getMessageTime() - TransactionKeys.DUPLICATE_WINDOW_MS;
        long toTime = transaction.getMessageTime() + TransactionKeys.DUPLICATE_WINDOW_MS;
        String matchKey = transaction.getMatchKey();

        // A Truecaller alert has no text to compare, so amount and merchant decide.
        if (TransactionKeys.SOURCE_TRUECALLER.equals(transaction.getSource())) {
            return matchKey != null && store.countByMatchKey(matchKey, fromTime, toTime) > 0;
        }

        String dedupKey = transaction.getDedupKey();
        if (dedupKey != null && store.countByDedupKey(dedupKey, fromTime, toTime) > 0) {
            return true;
        }
        // Keeps recognising older rows whose date was edited after they were saved.
        String smsHash = transaction.getSmsHash();
        if (smsHash != null && store.countBySmsHash(smsHash) > 0) {
            return true;
        }
        // Different full-text messages can be genuine repeats, so only a Truecaller
        // alert for the same payment counts here.
        return matchKey != null && store.countByMatchKeyAndSource(matchKey,
                TransactionKeys.SOURCE_TRUECALLER, fromTime, toTime) > 0;
    }
}
