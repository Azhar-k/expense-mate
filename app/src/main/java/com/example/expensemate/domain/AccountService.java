package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.Balance;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Where money is: banks, cards, wallets, cash. Each holds one currency. */
public interface AccountService {
    LiveData<List<Account>> accounts();

    LiveData<Account> defaultAccount();

    /** One per account, keyed by account id. */
    LiveData<List<Balance>> balances();

    /** A new account goes to the end of a list the user has ordered, or into the alphabetical one. */
    CompletableFuture<Void> add(Account account);

    /** Changing an account's currency relabels its transactions; amounts are not converted. */
    CompletableFuture<Void> update(Account account);

    /** Numbers the accounts from one in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);

    CompletableFuture<Void> makeDefault(Account account);

    /** The default account stays. Any other's transactions move to the default and take on its currency. */
    CompletableFuture<Void> delete(Account account);
}
