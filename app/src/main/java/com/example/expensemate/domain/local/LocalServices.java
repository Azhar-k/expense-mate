package com.example.expensemate.domain.local;

import com.example.expensemate.data.store.Stores;
import com.example.expensemate.domain.AccountService;
import com.example.expensemate.domain.BillService;
import com.example.expensemate.domain.CaptureService;
import com.example.expensemate.domain.CategoryService;
import com.example.expensemate.domain.LoanService;
import com.example.expensemate.domain.Services;
import com.example.expensemate.domain.TransactionService;

import java.util.concurrent.Executor;

/** The rules run on the phone, over whichever stores they are given. */
public final class LocalServices implements Services {
    private final TransactionService transactions;
    private final CaptureService capture;
    private final AccountService accounts;
    private final CategoryService categories;
    private final LoanService loans;
    private final BillService bills;

    /** @param executor runs the writes; pass a direct executor to finish them on the calling thread */
    public LocalServices(Stores stores, Executor executor) {
        Writer writer = new Writer(stores.unitOfWork(), executor);
        TransactionRules rules = new TransactionRules(stores.accounts(), stores.categories(),
                stores.counterpartyCategories(), stores.bills());
        transactions = new LocalTransactionService(stores.transactions(), rules, writer);
        capture = new LocalCaptureService(stores.transactions(), rules, stores.unitOfWork());
        accounts = new LocalAccountService(stores.accounts(), stores.transactions(), writer);
        categories = new LocalCategoryService(stores.categories(), writer);
        loans = new LocalLoanService(stores.loans(), writer);
        bills = new LocalBillService(stores.bills(), stores.transactions(), rules, writer);
    }

    @Override
    public TransactionService transactions() {
        return transactions;
    }

    @Override
    public CaptureService capture() {
        return capture;
    }

    @Override
    public AccountService accounts() {
        return accounts;
    }

    @Override
    public CategoryService categories() {
        return categories;
    }

    @Override
    public LoanService loans() {
        return loans;
    }

    @Override
    public BillService bills() {
        return bills;
    }
}
