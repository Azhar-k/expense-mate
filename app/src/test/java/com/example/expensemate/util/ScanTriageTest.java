package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScanTriageTest {
    @Test
    public void keepsMessagesThatReportMoneyMoving() {
        assertTrue(ScanTriage.looksLikeTransaction("Sent Rs.450.00 from a/c ending 1234 to CORNER SHOP"));
        assertTrue(ScanTriage.looksLikeTransaction("INR 1,200 spent on your card at TEST STORE"));
        assertTrue(ScanTriage.looksLikeTransaction("₹500 credited to your account"));
    }

    @Test
    public void dropsTheRestOfTheInbox() {
        assertFalse(ScanTriage.looksLikeTransaction("Your OTP for a payment of Rs 500 sent to you is 123456"));
        assertFalse(ScanTriage.looksLikeTransaction("Mega offer! Get Rs 500 cashback on your next purchase"));
        assertFalse(ScanTriage.looksLikeTransaction("Your parcel has been received at the depot"));
        assertFalse(ScanTriage.looksLikeTransaction("Rs 2,000 minimum due on your card statement"));
        assertFalse(ScanTriage.looksLikeTransaction("Rs 1,000 will be debited on 5 Nov for your EMI"));
        assertFalse(ScanTriage.looksLikeTransaction(null));
    }

    @Test
    public void readsTheFirstAmount() {
        assertEquals(Double.valueOf(1200), ScanTriage.amountIn("INR 1,200 spent, balance Rs 50"));
        assertEquals(Double.valueOf(450.5), ScanTriage.amountIn("Sent Rs. 450.50 to shop"));
        assertNull(ScanTriage.amountIn("No money here"));
        assertNull(ScanTriage.amountIn(null));
    }
}
