package com.example.expensemate.ui.common;

import static org.junit.Assert.assertEquals;

import com.example.expensemate.data.Transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

public class TransactionTextTest {
    private Locale original;

    @Before
    public void setUp() {
        original = Locale.getDefault();
        Locale.setDefault(new Locale("en", "IN"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(original);
    }

    private static Transaction leg(String type, double amount, String currency) {
        Transaction leg = new Transaction();
        leg.setTransactionType(type);
        leg.setAmount(amount);
        leg.setCurrency(currency);
        return leg;
    }

    @Test
    public void aTransferBetweenCurrenciesShowsWhatLeftAndWhatArrived() {
        Transaction debit = leg("DEBIT", 2270, "INR");
        Transaction credit = leg("CREDIT", 100, "AED");
        // Whichever side the row was built from
        assertEquals("₹2,270 → AED 100", TransactionText.transferAmount(credit, debit, credit));
        assertEquals("₹2,270 → AED 100", TransactionText.transferAmount(debit, debit, credit));
    }

    private static Transaction side(String type, long fundId, long accountId) {
        Transaction leg = leg(type, 500, "INR");
        leg.setFundId(fundId);
        leg.setAccountId(accountId);
        return leg;
    }

    @Test
    public void aMoveNamesTheFundsAndATransferNamesTheAccounts() {
        java.util.Map<Long, String> funds = new java.util.HashMap<>();
        funds.put(1L, "Daily");
        funds.put(2L, "Savings");
        // Fund and account ids come from separate tables, so the same number names both
        java.util.Map<Long, String> accounts = new java.util.HashMap<>();
        accounts.put(1L, "Bank");
        accounts.put(2L, "Wallet");

        Transaction moveOut = side("DEBIT", 1, 1);
        Transaction moveIn = side("CREDIT", 2, 1);
        assertEquals("Daily → Savings", TransactionText.transferRoute(funds, accounts, moveOut, moveIn));
        assertEquals("Move", TransactionText.transferKind(funds, moveOut, moveIn));
        assertEquals("Move", TransactionText.transferNoun(moveOut, moveIn));

        Transaction sent = side("DEBIT", 1, 1);
        Transaction arrived = side("CREDIT", 1, 2);
        assertEquals("Bank → Wallet", TransactionText.transferRoute(funds, accounts, sent, arrived));
        assertEquals("Transfer", TransactionText.transferKind(funds, sent, arrived));
        assertEquals("Transfer", TransactionText.transferNoun(sent, arrived));

        // An older pair that changed both is a transfer, and says which funds too
        Transaction both = side("CREDIT", 2, 2);
        assertEquals("Bank → Wallet", TransactionText.transferRoute(funds, accounts, sent, both));
        assertEquals("Transfer · Daily → Savings", TransactionText.transferKind(funds, sent, both));
    }

    @Test
    public void aTransferInOneCurrencyShowsOneAmount() {
        Transaction debit = leg("DEBIT", 500, "INR");
        Transaction credit = leg("CREDIT", 500, "INR");
        assertEquals("₹500", TransactionText.transferAmount(debit, debit, credit));
        assertEquals("Without its other side", "₹500", TransactionText.transferAmount(debit, debit, null));
    }
}
