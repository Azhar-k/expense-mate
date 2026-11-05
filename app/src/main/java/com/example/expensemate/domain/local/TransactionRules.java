package com.example.expensemate.domain.local;

import com.example.expensemate.data.Account;
import com.example.expensemate.data.RecurringPayment;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.data.store.AccountStore;
import com.example.expensemate.data.store.BillStore;
import com.example.expensemate.data.store.CategoryStore;
import com.example.expensemate.data.store.CounterpartyCategoryStore;

import java.util.Objects;

/** What a transaction gets filled in with on its way into the store. Call these inside a unit of work. */
final class TransactionRules {
    private final AccountStore accounts;
    private final CategoryStore categories;
    private final CounterpartyCategoryStore counterparties;
    private final BillStore bills;

    TransactionRules(AccountStore accounts, CategoryStore categories, CounterpartyCategoryStore counterparties,
            BillStore bills) {
        this.accounts = accounts;
        this.categories = categories;
        this.counterparties = counterparties;
        this.bills = bills;
    }

    static String categoryTypeOf(String transactionType) {
        return "CREDIT".equals(transactionType) ? "INCOME" : "EXPENSE";
    }

    Long defaultAccountId(String type) {
        Account account = accounts.findDefault(type);
        return account != null ? account.getId() : null;
    }

    /** A transaction always has both accounts, so whichever is missing goes to its default. */
    void fillDefaultAccounts(Transaction transaction) {
        if (transaction.getAccountId() == null) {
            transaction.setAccountId(defaultAccountId(Account.TYPE_VIRTUAL));
        }
        if (transaction.getRealAccountId() == null) {
            transaction.setRealAccountId(defaultAccountId(Account.TYPE_REAL));
        }
    }

    void applyDefaultCategory(Transaction transaction) {
        String category = categories.findDefaultName(categoryTypeOf(transaction.getTransactionType()));
        if (category != null) {
            transaction.setCategory(category);
        }
    }

    /** The last category chosen for a sender or receiver goes on their next captured payment. */
    void applyRememberedCategory(Transaction transaction) {
        String counterparty = TransactionKeys.counterpartyKey(transaction.getReceiverName());
        if (counterparty == null || transaction.getTransactionType() == null) {
            return;
        }
        String category = counterparties.find(counterparty, transaction.getTransactionType());
        if (category != null) {
            transaction.setCategory(category);
            transaction.setNeedsReview(false);
        }
    }

    void rememberCategory(Transaction row) {
        String counterparty = TransactionKeys.counterpartyKey(row.getReceiverName());
        if (counterparty == null || row.getTransactionType() == null || row.isTransfer()) {
            return;
        }
        counterparties.delete(counterparty, row.getTransactionType());
        // The default category means no choice was made, so it stays out and those payments keep asking for a category.
        String defaultCategory = categories.findDefaultName(categoryTypeOf(row.getTransactionType()));
        if (!Objects.equals(row.getCategory(), defaultCategory)) {
            counterparties.save(counterparty, row.getTransactionType(), row.getCategory());
        }
    }

    /** A bill the row was just linked to counts as paid on the row's date. */
    void markLinkedBillPaid(Transaction row, Long previousBillId) {
        Long billId = row.getLinkedRecurringPaymentId();
        if (billId == null || billId.equals(previousBillId)) {
            return;
        }
        RecurringPayment bill = bills.findById(billId);
        if (bill != null) {
            bill.setCompleted(true);
            bill.setLastCompletedDate(row.getDate());
            bills.update(bill);
        }
    }
}
