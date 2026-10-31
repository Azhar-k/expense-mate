package com.example.expensemate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.expensemate.data.Transaction;

import org.junit.Test;

/** Message shapes come from real bank messages, with the personal details changed. */
public class SmsTransactionHandlerParserTest {

    private static void assertParsed(String body, double amount, String type, String receiver) {
        Transaction transaction = SmsTransactionHandler.extractTransactionDetails(body, "AX-TESTBK-S");
        assertNotNull("No parser matched: " + body, transaction);
        assertEquals(amount, transaction.getAmount(), 0.001);
        assertEquals(type, transaction.getTransactionType());
        assertEquals(receiver, transaction.getReceiverName());
    }

    private static void assertNotParsed(String body) {
        assertNull("Should not create a transaction: " + body,
                SmsTransactionHandler.extractTransactionDetails(body, "AX-TESTBK-S"));
    }

    @Test
    public void iciciCardSpend_withLakhGrouping() {
        assertParsed("Rs 1,23,000.00 spent on ICICI Bank Card XX3333 on 03-Oct-26 at TEST HOSPITAL. "
                + "Avl Lmt: Rs 2,00,000.00. To dispute, call 18002662/SMS BLOCK 3333 to 9215676766.",
                123000.00, "DEBIT", "TEST HOSPITAL");
    }

    @Test
    public void iciciInftBillPayment_withLakhGrouping() {
        assertParsed("ICICI Bank Acc XX444 debited Rs. 1,25,500.75 on 20-Oct-26 InfoBIL*INFT*FJO9.Avl Bal Rs. 10,000.00.",
                125500.75, "DEBIT", "NEFT-FJO9");
    }

    @Test
    public void hdfcCardSpend() {
        assertParsed("Spent Rs.250.50 On HDFC Bank Card 1111 At TESTSHOP10580 On 2026-10-31:09:35:42."
                + "Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1111 to 7308080808",
                250.50, "DEBIT", "TESTSHOP10580");
    }

    @Test
    public void sbiAccountDebit() {
        assertParsed("Your A/C XXXXX555555 Debited INR 12,000.00 on 02/10/26 -Transferred to Mr. TEST PERSON. "
                + "Avl Balance INR 500.00-SBI", 12000.00, "DEBIT", "Mr. TEST PERSON");
    }

    @Test
    public void canaraCredit() {
        assertParsed("Your a/c XXXX6666 CREDITED with INR 2,000.00 on 30/10/2026. Avail.bal INR 1,000.00. - Canara Bank",
                2000.00, "CREDIT", "Canara Bank");
    }

    @Test
    public void pluxeeCredit_withoutSpaceAfterRs() {
        assertParsed("Your Pluxee Card has been successfully credited with Rs.5000 towards Meal Wallet on "
                + "Wed Oct 07 2026 14:20:46. Your current Meal Wallet balance is Rs.6000.00.",
                5000.00, "CREDIT", "Pluxee");
    }

    @Test
    public void hdfcCardPaymentReceived_isExcludedFromSummary() {
        String body = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 4000.00 RECEIVED TOWARDS YOUR CREDIT CARD "
                + "ENDING WITH 1111 ON 1-10-2026.YOUR AVAILABLE LIMIT IS RS. 50000.00";
        assertParsed(body, 4000.00, "CREDIT", "Credit card payment");
        assertTrue(SmsTransactionHandler.extractTransactionDetails(body, "JM-HDFCBK-S").isExcludedFromSummary());
    }

    @Test
    public void iciciCardPaymentReceived_isExcludedFromSummary() {
        String body = "Dear Customer, Payment of INR 1,25,500.75 has been received on your ICICI Bank Credit "
                + "Card Account 4xxx3333 on 20-OCT-26.Thank you.";
        assertParsed(body, 125500.75, "CREDIT", "Credit card payment");
        assertTrue(SmsTransactionHandler.extractTransactionDetails(body, "JD-ICICIT-S").isExcludedFromSummary());
    }

    @Test
    public void existingFormatsStillParse() {
        assertParsed("ICICI Bank Acct XX444 debited for Rs 250.00 on 12-Oct-26; TEST SHOP credited. UPI:123456789012.",
                250.00, "DEBIT", "TEST SHOP");
        assertParsed("Rs.55.00 spent on your SBI Credit Card ending with 2222 at TestShop on 09-10-26 via UPI "
                + "(Ref No. 123456789012). Trxn. not done by you? Report at https://sbicard.com/Dispute",
                55.00, "DEBIT", "TestShop");
        assertParsed("INR 2,300.00 spent using ICICI Bank Card XX3333 on 20-May-26 on TEST STORE. Avl Limit: INR 1,00,000.00.",
                2300.00, "DEBIT", "TEST STORE");
        assertParsed("ICICI Bank Account XX444 credited:Rs. 1,20,000.50 on 30-Oct-26. Info NEFT-TESTREF123-TEST EMPLOYER.",
                120000.50, "CREDIT", "NEFT-TESTREF123");
        assertFalse(SmsTransactionHandler.extractTransactionDetails(
                "ICICI Bank Acct XX444 debited for Rs 250.00 on 12-Oct-26; TEST SHOP credited.", "AX-ICICIT-S")
                .isExcludedFromSummary());
    }

    @Test
    public void messagesThatWouldDoubleCount_stayUnparsed() {
        // Same fuel payment as the ICICI debit
        assertNotParsed("Transaction of Rs.3200.00 for purchase of Petrol is successful . Regards, HP PAY Team");
        // Second message for one HDFC card payment
        assertNotParsed("HDFC Bank Cardmember, Online Payment of Rs.4000 vide Ref# 274ABCDEF was credited to your "
                + "card ending 1111 On 01/OCT/2026_value Date 01/OCT/2026");
        // Second message for one Canara credit
        assertNotParsed("Dear Customer, an amount of Rs. 2000/- is credited to A/c XXXXXXXXX6666 towards a scheme- Canara Bank");
    }
}
