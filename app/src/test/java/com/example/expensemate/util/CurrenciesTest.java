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
    private static Account real(String currency, boolean isDefault) {
        Account account = new Account("Bank", "", "", null, "");
        account.setType(Account.TYPE_REAL);
        account.setCurrency(currency);
        account.setDefault(isDefault);
        return account;
    }

    @Test
    public void mainCurrencyIsTheDefaultRealAccounts() {
        Account virtual = new Account("Daily", "", "", null, "");
        virtual.setDefault(true);
        assertEquals("AED", Currencies.main(Arrays.asList(virtual, real("INR", false), real("AED", true))));
        assertEquals("Without a default, the first real account", "INR",
                Currencies.main(Arrays.asList(real("INR", false), real("AED", false))));
    }

    @Test
    public void onlyRealAccountsHaveACurrency() {
        Account virtual = new Account("Daily", "", "", null, "");
        virtual.setCurrency("INR");
        assertNull(Currencies.of(virtual));
        assertEquals("INR", Currencies.of(real("INR", false)));
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
