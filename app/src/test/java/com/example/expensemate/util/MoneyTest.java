package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MoneyTest {
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

    @Test
    public void groupsRupeesTheIndianWay() {
        assertEquals("₹0", Money.format(0, "INR"));
        assertEquals("₹999", Money.format(999, "INR"));
        assertEquals("₹1,000", Money.format(1000, "INR"));
        assertEquals("₹12,345", Money.format(12345, "INR"));
        assertEquals("₹1,67,976", Money.format(167976, "INR"));
        assertEquals("₹12,34,567", Money.format(1234567, "INR"));
        assertEquals("₹1,23,45,678", Money.format(12345678, "INR"));
    }

    @Test
    public void groupsOtherCurrenciesInThousands() {
        assertEquals("€1,234,567", Money.format(1234567, "EUR"));
        assertEquals("€999", Money.format(999, "EUR"));
    }

    @Test
    public void showsTheMinorUnitOnlyWhenItIsNotZero() {
        assertEquals("₹1,66,991.50", Money.format(166991.50, "INR"));
        assertEquals("₹10.05", Money.format(10.05, "INR"));
        assertEquals("₹5", Money.format(5.001, "INR"));
    }

    @Test
    public void usesAsManyDigitsAsTheCurrencyHas() {
        assertEquals("JPY rounds to whole yen", "1,235", digitsOf(Money.format(1234.5, "JPY")));
        assertEquals("KWD has three", "12.345", digitsOf(Money.format(12.345, "KWD")));
        assertEquals("12.3", digitsOf(Money.format(12.3, "USD")).substring(0, 4));
    }

    @Test
    public void lettersAsASignAreKeptApartFromTheDigits() {
        assertEquals("AED 1,200", Money.format(1200, "AED"));
    }

    @Test
    public void putsTheSignBeforeTheCurrency() {
        assertEquals("−₹984.50", Money.format(-984.50, "INR"));
        assertEquals("−₹1,240", Money.debit(1240, "INR"));
        assertEquals("+₹85,000", Money.credit(85000, "INR"));
        assertEquals("+₹10", Money.signed(10, "INR"));
        assertEquals("−₹10", Money.signed(-10, "INR"));
        assertEquals("−€10", Money.debit(10, "EUR"));
    }

    @Test
    public void keepsTheRupeeSignWhateverThePhoneLanguage() {
        Locale.setDefault(Locale.GERMANY);
        assertEquals("₹1,67,976", Money.format(167976, "INR"));
        assertEquals("€5", Money.format(5, "EUR"));
    }

    @Test
    public void unknownCurrencyFallsBackToRupees() {
        assertEquals("₹5", Money.format(5, null));
        assertEquals("₹5", Money.format(5, "XYZ1"));
    }

    @Test
    public void formatsOneAmountPerCurrencyInOrder() {
        Map<String, Double> amounts = new LinkedHashMap<>();
        amounts.put("INR", 1200.0);
        amounts.put("AED", -40.0);
        assertEquals("₹1,200 · −AED 40", Money.formatAll(amounts));
        assertEquals("+₹1,200 · −AED 40", Money.signedAll(amounts));
        assertEquals("", Money.formatAll(new LinkedHashMap<>()));
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

    private static String digitsOf(String formatted) {
        return formatted.replaceAll("[^0-9.,]", "");
    }
}
