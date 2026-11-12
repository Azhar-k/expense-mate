package com.example.expensemate.data.room;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.store.AccountStore;
import com.example.expensemate.data.store.BillStore;
import com.example.expensemate.data.store.CategoryStore;
import com.example.expensemate.data.store.CounterpartyCategoryStore;
import com.example.expensemate.data.store.LoanStore;
import com.example.expensemate.data.store.PatternStore;
import com.example.expensemate.data.store.Stores;
import com.example.expensemate.data.store.TransactionStore;
import com.example.expensemate.data.store.UnitOfWork;

import java.util.concurrent.Callable;

/** The stores backed by the local Room database. */
public final class RoomStores implements Stores {
    private final TransactionStore transactions;
    private final AccountStore accounts;
    private final CategoryStore categories;
    private final CounterpartyCategoryStore counterpartyCategories;
    private final LoanStore loans;
    private final BillStore bills;
    private final PatternStore patterns;
    private final UnitOfWork unitOfWork;

    public RoomStores(AppDatabase database) {
        transactions = new RoomTransactionStore(database.transactionDao());
        accounts = new RoomAccountStore(database.accountDao());
        categories = new RoomCategoryStore(database.categoryDao());
        counterpartyCategories = new RoomCounterpartyCategoryStore(database.transactionDao());
        loans = new RoomLoanStore(database.loanDao());
        bills = new RoomBillStore(database.recurringPaymentDao());
        patterns = new RoomPatternStore(database.messagePatternDao());
        unitOfWork = new UnitOfWork() {
            @Override
            public void run(Runnable work) {
                database.runInTransaction(work);
            }

            @Override
            public <T> T call(Callable<T> work) {
                return database.runInTransaction(work);
            }
        };
    }

    @Override
    public TransactionStore transactions() {
        return transactions;
    }

    @Override
    public AccountStore accounts() {
        return accounts;
    }

    @Override
    public CategoryStore categories() {
        return categories;
    }

    @Override
    public CounterpartyCategoryStore counterpartyCategories() {
        return counterpartyCategories;
    }

    @Override
    public LoanStore loans() {
        return loans;
    }

    @Override
    public BillStore bills() {
        return bills;
    }

    @Override
    public PatternStore patterns() {
        return patterns;
    }

    @Override
    public UnitOfWork unitOfWork() {
        return unitOfWork;
    }
}
