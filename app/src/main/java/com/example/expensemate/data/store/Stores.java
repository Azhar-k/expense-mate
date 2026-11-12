package com.example.expensemate.data.store;

/** Every store of one database, plus the unit of work that spans them. */
public interface Stores {
    TransactionStore transactions();

    AccountStore accounts();

    CategoryStore categories();

    CounterpartyCategoryStore counterpartyCategories();

    LoanStore loans();

    BillStore bills();

    PatternStore patterns();

    UnitOfWork unitOfWork();
}
