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

    @Test
    public void aTransferInOneCurrencyShowsOneAmount() {
        Transaction debit = leg("DEBIT", 500, "INR");
        Transaction credit = leg("CREDIT", 500, "INR");
        assertEquals("₹500", TransactionText.transferAmount(debit, debit, credit));
        assertEquals("Without its other side", "₹500", TransactionText.transferAmount(debit, debit, null));
    }
}
