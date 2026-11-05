package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.AccountBalance;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AccountService {
    LiveData<List<Account>> accounts();

    /** The default virtual account. */
    LiveData<Account> defaultAccount();

    LiveData<List<AccountBalance>> balances();

    /** A new account goes to the end of a list the user has ordered, or into the alphabetical one. */
    CompletableFuture<Void> add(Account account);

    CompletableFuture<Void> update(Account account);

    /** Numbers the accounts from one in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);

    /** Replaces the default of this account's type only. */
    CompletableFuture<Void> makeDefault(Account account);

    /** A default account stays. Any other's transactions move to the default of its type. */
    CompletableFuture<Void> delete(Account account);
}
