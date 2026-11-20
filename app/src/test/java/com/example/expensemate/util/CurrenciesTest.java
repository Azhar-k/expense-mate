package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.Account;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurrenciesTest {
    private static Account account(String currency, boolean isDefault) {
        Account account = new Account("Bank", "", "", null, "");
        account.setCurrency(currency);
        account.setDefault(isDefault);
        return account;
    }

    @Test
    public void mainCurrencyIsTheDefaultAccounts() {
        assertEquals("AED", Currencies.main(Arrays.asList(account("INR", false), account("AED", true))));
        assertEquals("Without a default, the first account", "INR",
                Currencies.main(Arrays.asList(account("INR", false), account("AED", false))));
        assertEquals("An account without a currency is skipped", "AED",
                Currencies.main(Arrays.asList(account(null, true), account("AED", false))));
    }

    @Test
    public void anAccountsCurrencyIsItsOwn() {
        assertNull(Currencies.of(null));
        assertEquals("INR", Currencies.of(account("INR", false)));
    }

    @Test
    public void inUseListsEachAccountCurrencyOnce() {
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("INR", "AED")), Currencies.inUse(
                Arrays.asList(account("INR", true), account("AED", false), account("INR", false), account(null, false))));
    }

    @Test
    public void readsCodesFromPickerLabelsAndTypedText() {
        assertEquals("AED", Currencies.fromLabel(Currencies.label("AED")));
        assertEquals("AED", Currencies.fromLabel("aed"));
        assertNull(Currencies.fromLabel("dirham"));
        assertNull(Currencies.fromLabel(""));
        assertFalse(Currencies.isValid("XX"));
        assertTrue(Currencies.isValid("JPY"));
    }

    @Test
    public void pickerPutsMainThenInUseFirstWithoutRepeats() {
        List<String> codes = Currencies.pickerCodes("INR", Arrays.asList("USD", "AED", "INR"));
        assertEquals(Arrays.asList("INR", "AED", "USD"), codes.subList(0, 3));
        assertEquals(1, Collections.frequency(codes, "USD"));
        assertTrue(codes.size() > 100);
    }

    @Test
    public void nonZeroSortsMainFirstAndDropsZeros() {
        Map<String, Double> amounts = new HashMap<>();
        amounts.put("AED", 40.0);
        amounts.put("USD", 0.0);
        amounts.put("INR", 100.0);
        amounts.put("EUR", 3.0);
        assertEquals(Arrays.asList("INR", "AED", "EUR"),
                new java.util.ArrayList<>(Currencies.nonZero(amounts, "INR").keySet()));
    }
}
