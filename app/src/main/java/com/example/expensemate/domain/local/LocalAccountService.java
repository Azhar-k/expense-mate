package com.example.expensemate.domain.local;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.data.AccountBalance;
import com.example.expensemate.data.store.AccountStore;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.domain.AccountService;

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
    public LiveData<List<AccountBalance>> balances() {
        return transactions.observeAccountBalances();
    }

    @Override
    public CompletableFuture<Void> add(Account account) {
        return writer.run(() -> {
            account.setCurrency(account.isReal() ? currencyOrMain(account.getCurrency()) : null);
            int last = store.lastSortOrder(account.getType());
            account.setSortOrder(last == 0 ? 0 : last + 1);
            store.insert(account);
        });
    }

    @Override
    public CompletableFuture<Void> update(Account account) {
        return writer.run(() -> {
            Account before = store.findById(account.getId());
            account.setCurrency(account.isReal() ? currencyOrMain(account.getCurrency()) : null);
            store.update(account);
            // Relabels, never converts: this is for fixing what an account was set up with
            if (account.isReal() && before != null && !account.getCurrency().equals(before.getCurrency())) {
                transactions.setCurrencyForRealAccount(account.getId(), account.getCurrency());
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
            store.clearDefault(account.getType());
            store.setDefault(account.getId());
        });
    }

    // A transaction always keeps both accounts, so the deleted account's rows go to the default of its type.
    @Override
    public CompletableFuture<Void> delete(Account account) {
        return writer.run(() -> {
            if (account.isDefault()) {
                return;
            }
            long replacement = store.findDefault(account.getType()).getId();
            if (account.isReal()) {
                transactions.moveRealAccount(account.getId(), replacement);
            } else {
                transactions.moveAccount(account.getId(), replacement);
            }
            store.delete(account);
        });
    }
}
