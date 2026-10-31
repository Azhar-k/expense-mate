package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MoneyTest {
    @Test
    public void groupsDigitsTheIndianWay() {
        assertEquals("₹0", Money.format(0));
        assertEquals("₹999", Money.format(999));
        assertEquals("₹1,000", Money.format(1000));
        assertEquals("₹12,345", Money.format(12345));
        assertEquals("₹1,67,976", Money.format(167976));
        assertEquals("₹12,34,567", Money.format(1234567));
        assertEquals("₹1,23,45,678", Money.format(12345678));
    }

    @Test
    public void showsPaiseOnlyWhenTheyAreNotZero() {
        assertEquals("₹166,991.50".replace("166,991", "1,66,991"), Money.format(166991.50));
        assertEquals("₹10.05", Money.format(10.05));
        assertEquals("₹5", Money.format(5.001));
    }

    @Test
    public void putsTheSignBeforeTheCurrency() {
        assertEquals("−₹984.50", Money.format(-984.50));
        assertEquals("−₹1,240", Money.debit(1240));
        assertEquals("+₹85,000", Money.credit(85000));
        assertEquals("+₹10", Money.signed(10));
        assertEquals("−₹10", Money.signed(-10));
    }

    @Test
    public void parseRejectsAnythingThatIsNotAnAmountAboveZero() {
        assertEquals(Double.valueOf(1240.5), Money.parse(" 1,240.50 "));
        assertNull(Money.parse("."));
        assertNull(Money.parse(""));
        assertNull(Money.parse(null));
        assertNull(Money.parse("0"));
        assertNull(Money.parse("-5"));
    }

    @Test
    public void plainDropsTheTrailingZero() {
        assertEquals("600", Money.plain(600.0));
        assertEquals("600.5", Money.plain(600.5));
    }
}
