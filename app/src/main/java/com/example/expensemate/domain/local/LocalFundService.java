package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Balance;
import com.example.expensemate.data.Fund;
import com.example.expensemate.data.store.BillStore;
import com.example.expensemate.data.store.FundStore;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.FundService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalFundService implements FundService {
    private final FundStore store;
    private final TransactionStore transactions;
    private final BillStore bills;
    private final Writer writer;

    LocalFundService(FundStore store, TransactionStore transactions, BillStore bills, Writer writer) {
        this.store = store;
        this.transactions = transactions;
        this.bills = bills;
        this.writer = writer;
    }

    @Override
    public LiveData<List<Fund>> funds() {
        return store.observeAll();
    }

    @Override
    public LiveData<Fund> defaultFund() {
        return store.observeDefault();
    }

    @Override
    public LiveData<List<Balance>> balances() {
        return transactions.observeFundBalances();
    }

    @Override
    public CompletableFuture<Void> add(Fund fund) {
        return writer.run(() -> {
            int last = store.lastSortOrder();
            fund.setSortOrder(last == 0 ? 0 : last + 1);
            store.insert(fund);
        });
    }

    @Override
    public CompletableFuture<Void> update(Fund fund) {
        return writer.run(() -> store.update(fund));
    }

    @Override
    public CompletableFuture<Void> reorder(List<Long> ids) {
        return writer.run(() -> {
            for (int i = 0; i < ids.size(); i++) {
                store.setSortOrder(ids.get(i), i + 1);
            }
        });
    }

    @Override
    public CompletableFuture<Void> makeDefault(Fund fund) {
        return writer.run(() -> {
            store.clearDefault();
            store.setDefault(fund.getId());
        });
    }

    // A transaction always has a fund, and a top-up needs both of its own, so they all go to the default.
    @Override
    public CompletableFuture<Void> delete(Fund fund) {
        return writer.run(() -> {
            Fund fallback = store.findDefault();
            if (fund.isDefault() || fallback == null || fallback.getId() == fund.getId()) {
                return;
            }
            transactions.moveFund(fund.getId(), fallback.getId());
            bills.moveFund(fund.getId(), fallback.getId());
            store.delete(fund);
        });
    }
}
