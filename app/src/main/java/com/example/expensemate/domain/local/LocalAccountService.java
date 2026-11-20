package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.Balance;
import com.example.expensemate.data.store.AccountStore;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.AccountService;
import com.example.expensemate.util.Currencies;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LocalAccountService implements AccountService {
    private final AccountStore store;
    private final TransactionStore transactions;
    private final Writer writer;

    LocalAccountService(AccountStore store, TransactionStore transactions, Writer writer) {
        this.store = store;
        this.transactions = transactions;
        this.writer = writer;
    }

    @Override
    public LiveData<List<Account>> accounts() {
        return store.observeAll();
    }

    @Override
    public LiveData<Account> defaultAccount() {
        return store.observeDefault();
    }

    @Override
    public LiveData<List<Balance>> balances() {
        return transactions.observeAccountBalances();
    }

    @Override
    public CompletableFuture<Void> add(Account account) {
        return writer.run(() -> {
            account.setCurrency(currencyOrMain(account.getCurrency()));
            int last = store.lastSortOrder();
            account.setSortOrder(last == 0 ? 0 : last + 1);
            store.insert(account);
        });
    }

    @Override
    public CompletableFuture<Void> update(Account account) {
        return writer.run(() -> {
            Account before = store.findById(account.getId());
            account.setCurrency(currencyOrMain(account.getCurrency()));
            store.update(account);
            // Relabels, never converts: this is for fixing what an account was set up with
            if (before != null && !account.getCurrency().equals(before.getCurrency())) {
                transactions.setCurrencyForAccount(account.getId(), account.getCurrency());
            }
        });
    }

    private String currencyOrMain(String currency) {
        String code = Currencies.normalize(currency);
        return code != null ? code : Currencies.main(store.findAll());
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
    public CompletableFuture<Void> makeDefault(Account account) {
        return writer.run(() -> {
            store.clearDefault();
            store.setDefault(account.getId());
        });
    }

    // A transaction always has an account, so the deleted account's rows go to the default one.
    @Override
    public CompletableFuture<Void> delete(Account account) {
        return writer.run(() -> {
            Account fallback = store.findDefault();
            if (account.isDefault() || fallback == null || fallback.getId() == account.getId()) {
                return;
            }
            transactions.moveAccount(account.getId(), fallback.getId());
            store.delete(account);
        });
    }
}
