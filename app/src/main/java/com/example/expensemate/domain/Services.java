package com.example.expensemate.domain;

/**
 * Everything the app can ask of its backend. One implementation keeps the rules and the data
 * on the phone; another could hand both to a server.
 */
public interface Services {
    TransactionService transactions();

    CaptureService capture();

    AccountService accounts();

    CategoryService categories();

    LoanService loans();

    BillService bills();

    PatternService patterns();
}
