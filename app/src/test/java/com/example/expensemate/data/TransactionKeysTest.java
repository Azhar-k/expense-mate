package com.example.expensemate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TransactionKeysTest {

    @Test
    public void bodyKey_ignoresCaseAndSpacing() {
        assertEquals(TransactionKeys.bodyKey("Rs 10 spent  at Shop"), TransactionKeys.bodyKey(" rs 10 SPENT at shop "));
    }

    @Test
    public void bodyKey_treatsStyledUnicodeAsPlainText() {
        assertEquals(TransactionKeys.bodyKey("Rs.60.00 spent on your SBI Credit Card"),
                TransactionKeys.bodyKey("Rs.60.00 𝗌𝗉𝖾𝗇𝗍 𝗈𝗇 𝗒𝗈𝗎𝗋 𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽"));
    }

    @Test
    public void bodyKey_differsForDifferentMessages() {
        assertNotEquals(TransactionKeys.bodyKey("Rs 10 spent"), TransactionKeys.bodyKey("Rs 11 spent"));
    }

    @Test
    public void bodyKey_isNullWithoutMessageText() {
        assertNull(TransactionKeys.bodyKey(null));
        assertNull(TransactionKeys.bodyKey("  "));
        assertNull(TransactionKeys.bodyKey("null"));
    }

    @Test
    public void matchKey_ignoresMerchantCaseAndPunctuation() {
        assertEquals(TransactionKeys.matchKey(55, "DEBIT", "CornerCafe"), TransactionKeys.matchKey(55.0, "DEBIT", "Cornercafe;"));
    }

    @Test
    public void matchKey_includesAmountAndType() {
        String key = TransactionKeys.matchKey(55, "DEBIT", "Shop");
        assertNotEquals(key, TransactionKeys.matchKey(56, "DEBIT", "Shop"));
        assertNotEquals(key, TransactionKeys.matchKey(55, "CREDIT", "Shop"));
    }

    @Test
    public void matchKey_isNullWithoutMerchant() {
        assertNull(TransactionKeys.matchKey(55, "DEBIT", null));
        assertNull(TransactionKeys.matchKey(55, "DEBIT", " ;; "));
    }

    @Test
    public void isSmsSender_recognisesSenderIdsAndNumbers() {
        assertTrue(TransactionKeys.isSmsSender("AX-ICICIT-S"));
        assertTrue(TransactionKeys.isSmsSender("JM-Pluxee-S"));
        assertTrue(TransactionKeys.isSmsSender("VM-HDFCBK"));
        assertTrue(TransactionKeys.isSmsSender("+919876543210"));
    }

    @Test
    public void isSmsSender_rejectsChatNames() {
        assertFalse(TransactionKeys.isSmsSender("SBI CARDS"));
        assertFalse(TransactionKeys.isSmsSender("IMAGE MOBILES AND COMPUTERS"));
        assertFalse(TransactionKeys.isSmsSender(null));
    }
}
