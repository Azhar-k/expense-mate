package com.example.expensemate.data.store;

/** The last category chosen for a sender or receiver, by transaction type. */
public interface CounterpartyCategoryStore {
    /** @return the remembered category's name, or null if none is remembered or it no longer exists */
    String find(String counterparty, String transactionType);

    /** Does nothing if no category of the matching type has this name. */
    void save(String counterparty, String transactionType, String category);

    void delete(String counterparty, String transactionType);
}
