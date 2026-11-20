package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.Balance;
import com.example.expensemate.data.Fund;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** What money is for. The default fund is everyday money, which budgets keep track of. */
public interface FundService {
    LiveData<List<Fund>> funds();

    LiveData<Fund> defaultFund();

    /** One per fund and currency, keyed by fund id. */
    LiveData<List<Balance>> balances();

    /** A new fund goes to the end of a list the user has ordered, or into the alphabetical one. */
    CompletableFuture<Void> add(Fund fund);

    CompletableFuture<Void> update(Fund fund);

    /** Numbers the funds from one in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);

    CompletableFuture<Void> makeDefault(Fund fund);

    /** The default fund stays. Any other's transactions, and the bills that top it up, move to the default. */
    CompletableFuture<Void> delete(Fund fund);
}
