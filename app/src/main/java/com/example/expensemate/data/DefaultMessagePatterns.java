package com.example.expensemate.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The patterns the app ships with, in the order they are tried. They are copied into the
 * database once, by the migration that created the table or when a new database is made, and
 * from then on the user can change them.
 *
 * <p>Keys are stable: never rename one. To change a shipped pattern in a later version, add a
 * migration that updates the row by key only where its text is still the old one.
 */
public final class DefaultMessagePatterns {
    private static final String AMOUNT = "(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{2})?)";

    private DefaultMessagePatterns() {
    }

    public static List<MessagePattern> all() {
        List<MessagePattern> list = new ArrayList<>();
        add(list, "icici_debit", "ICICI account debit", "DEBIT",
                "(?i)ICICI Bank (?:Acct|Acc) XX(\\d+) (?:debited for |debited )Rs\\.? " + AMOUNT
                        + " on (\\d{2}-[A-Za-z]{3}-\\d{2}|\\d{2}/\\d{2}/\\d{4})[^;]*?;\\s*(?<merchant>[^*]+)(?:credited|Info)");
        add(list, "icici_upi_credit", "ICICI UPI credit", "CREDIT",
                "(?i)Dear Customer, Acct XX(\\d+) is credited with Rs " + AMOUNT
                        + " on (\\d{2}-[A-Za-z]{3}-\\d{2}) from (?<merchant>[^.]+)");
        add(list, "icici_neft_credit", "ICICI NEFT credit", "CREDIT",
                "(?i)ICICI Bank Account XX(\\d+) credited:Rs\\.? " + AMOUNT
                        + " on (\\d{2}-[A-Za-z]{3}-\\d{2})\\. Info NEFT-(?<merchant>[^-]+)-")
                .setCounterparty("NEFT-{merchant}");
        add(list, "kotak_debit", "Kotak UPI debit", "DEBIT",
                "Sent Rs\\.?(?<amount>\\d+(?:\\.\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to (?<merchant>[^\\s]+) "
                        + "on (\\d{2}-\\d{2}-\\d{2})\\.UPI Ref (\\d+)");
        add(list, "kotak_credit", "Kotak UPI credit", "CREDIT",
                "Received Rs\\.?(?<amount>\\d+(?:\\.\\d{2})?) in your Kotak Bank AC ([A-Z0-9]+) from (?<merchant>[^\\s]+) "
                        + "on (\\d{2}-\\d{2}-\\d{2})\\.UPI Ref:(\\d+)");
        // Some SBI messages use Unicode sans-serif letters instead of plain ones
        add(list, "sbi_card_debit", "SBI credit card spend", "DEBIT",
                "Rs\\.?" + AMOUNT + " (?:spent|𝗌𝗉𝖾𝗇𝗍) (?:on|𝗈𝗇) (?:your|𝗒𝗈𝗎𝗋) (?:SBI Credit Card|𝖲𝖡𝖨 𝖢𝗋𝖾𝖽𝗂𝗍 𝖢𝖺𝗋𝖽) "
                        + "(?:ending|𝖾𝗇𝖽𝗂𝗇𝗀) with (\\d{4}) at (?<merchant>[^\\s]+) on (\\d{2}-\\d{2}-\\d{2}) "
                        + "via UPI \\(Ref No\\. (\\d+)\\)");
        add(list, "hdfc_card_spend", "HDFC card spend", "DEBIT",
                "(?i)Spent\\s+(?:Rs\\.?|INR)\\s*(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+On\\s+HDFC Bank Card\\s+\\d+"
                        + "\\s+At\\s+(?<merchant>.+?)\\s+On\\s+\\d{4}-\\d{2}-\\d{2}");
        add(list, "sbi_account_debit", "SBI account debit", "DEBIT",
                "(?i)Your A/C X+\\d+ Debited INR (?<amount>\\d+(?:,\\d+)*(?:\\.\\d{1,2})?) on \\d{2}/\\d{2}/\\d{2}\\s*-?\\s*"
                        + "(?:Transferred to\\s+)?(?<merchant>.+?)\\.\\s*Avl");
        add(list, "canara_credit", "Canara credit", "CREDIT",
                "(?i)Your a/c X+\\d+ CREDITED with (?:INR|Rs\\.?)\\s*(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{1,2})?) "
                        + "on \\d{2}/\\d{2}/\\d{4}.*Canara Bank")
                .setCounterparty("Canara Bank");
        // The bank account debit already records this money, so it is not income. Banks send a
        // second message for the same payment, which no pattern reads on purpose.
        MessagePattern cardPayment = add(list, "card_payment_received", "Credit card payment received", "CREDIT",
                "(?i)Payment of\\s+(?:Rs\\.?|INR)\\s*(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s+"
                        + "(?:RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH \\d+|has been received on your ICICI Bank Credit Card)");
        cardPayment.setCounterparty("Credit card payment");
        cardPayment.setExcludeFromSummary(true);
        add(list, "pluxee_debit", "Pluxee meal card spend", "DEBIT",
                "Rs\\.? " + AMOUNT + " spent from Pluxee\\s+Meal Card wallet, card no\\.(?:xx\\d+)? "
                        + "on (\\d{1,2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}) at (?<merchant>.+?)\\.\\s*Avl bal");
        add(list, "pluxee_credit", "Pluxee meal card credit", "CREDIT",
                "Your Pluxee Card(?: xx\\d+)? has been (?:successfully )?credited with (?:Rs\\.?|INR) ?"
                        + "(?<amount>\\d+(?:\\.\\d{2})?) (?:towards\\s+Meal Wallet|as a reversal against a previous transaction) "
                        + "on (?:[A-Za-z]{3} )?((?:\\d{2} [A-Za-z]{3}|[A-Za-z]{3} \\d{2}) \\d{4} \\d{2}:\\d{2}:\\d{2})"
                        + "(?:as a reversal against a previous transaction on [A-Za-z]{3} \\d{2},\\d{4} \\d{2}:\\d{2}:\\d{2})?\\."
                        + "(?: Your current Meal Wallet balance is Rs\\.(\\d+(?:\\.\\d{2})?)\\.)?")
                .setCounterparty("Pluxee");
        add(list, "federal_debit", "Federal Bank UPI debit", "DEBIT",
                "Rs (?<amount>\\d+(?:\\.\\d{2})?) debited via UPI on (\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}) "
                        + "to VPA (?<merchant>[^\\s]+)\\.Ref No (\\d+)\\.Small txns\\?Use UPI Lite!-Federal Bank");
        add(list, "federal_credit", "Federal Bank credit", "CREDIT",
                "Dear Customer, Rs\\.?(?<amount>\\d+(?:\\.\\d{2})?) credited to your A/c XX(\\d+) "
                        + "on (\\d{2}[A-Z]{3}\\d{4} \\d{2}:\\d{2}:\\d{2})\\. BAL-Rs\\.(\\d+(?:\\.\\d{2})?)-Federal Bank")
                .setCounterparty("Federal Bank");
        add(list, "upi_debit", "UPI debit", "DEBIT",
                "(?i)(?:debited\\s+(?:for|Rs\\.?|INR)?\\s*)(?<amount>[\\d,]+\\.\\d{2}).*?;\\s*"
                        + "(?<merchant>[A-Z][A-Za-z\\s\\.\\-]+)\\s+credited");
        add(list, "upi_credit", "UPI credit", "CREDIT",
                "(?i)(?:Acct\\s+\\w+\\s+is\\s+credited\\s+with\\s+(?:Rs\\.?|INR)?\\s*(?<amount>[\\d,]+\\.\\d{2})\\s+"
                        + "from\\s+(?<merchant>[A-Z][A-Za-z\\s\\.\\-]+))");
        add(list, "neft_credit", "NEFT credit", "CREDIT",
                "(?i)Account\\s+\\w+\\s+credited:Rs\\.\\s*(?<amount>[\\d,]+\\.\\d{2}).*?NEFT[-]?(?<merchant>[A-Z0-9]+)")
                .setCounterparty("NEFT-{merchant}");
        add(list, "icici_card_spend", "ICICI card spend", "DEBIT",
                "Rs\\.?\\s*" + AMOUNT + "\\s+spent on ICICI Bank Card XX\\d{4} on (\\d{2}-[A-Za-z]{3}-\\d{2}) "
                        + "at (?<merchant>[^.]+)");
        add(list, "icici_card_spend_new", "ICICI card spend (newer wording)", "DEBIT",
                "(?i)(?:Rs\\.?|INR)\\s*" + AMOUNT + "\\s+spent\\s+using\\s+ICICI\\s+Bank\\s+Card\\s+XX(\\d{4})\\s+"
                        + "on\\s+(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+on\\s+(?<merchant>[^\\.]+)");
        add(list, "credit_card_spend", "Credit card spend", "DEBIT",
                "(?i)Rs\\.?\\s*" + AMOUNT + "\\s+spent\\s+on\\s+(?:your\\s+)?(?:[A-Z]+\\s+)?(?:Credit\\s+)?Card"
                        + "(?:\\s+ending\\s+with\\s+\\d{4}|\\s+XX\\d{4})?.*?at\\s+(?<merchant>[A-Z0-9@&\\-\\s\\.]+?)\\s+"
                        + "(?:on\\s+\\d{2}-\\d{2}-\\d{2}|via|Ref|\\.|$)");
        add(list, "icici_alt_debit", "ICICI account debit (other wording)", "DEBIT",
                "Rs\\.?\\s*(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{2}))\\s+debited from ICICI Bank Acc XX\\d+ "
                        + "on (\\d{2}-[A-Za-z]{3}-\\d{2})\\s+(?<merchant>[A-Z0-9\\*\\s\\.]+)\\s+Bal");
        add(list, "neft_credit_by", "NEFT credit with sender", "CREDIT",
                "(?:INR|Rs\\.?)[ ]?(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{2}))\\s+credited to your A/c No XX\\d+ "
                        + "on \\d{2}/\\d{2}/\\d{4}.*?by (?<merchant>[A-Z][A-Za-z\\s\\.]+)");
        add(list, "icici_neft_debit", "ICICI NEFT bill payment", "DEBIT",
                "ICICI Bank Acc XX\\d+ debited Rs\\.?\\s*(?<amount>\\d+(?:,\\d+)*(?:\\.\\d{2})) "
                        + "on (\\d{2}-[A-Za-z]{3}-\\d{2}) InfoBIL\\*(?:NEFT|INFT)\\*(?<merchant>[A-Z0-9]+)")
                .setCounterparty("NEFT-{merchant}");
        // These stay last: they also match most formats above, and would take the words after
        // the amount as the merchant.
        add(list, "general_debit", "Any debit (fallback)", "DEBIT",
                "(?i)(?:Rs\\.?|INR)\\s*(?<amount>\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*"
                        + "(?:debited|spent|paid|sent|transferred|withdrawn)\\s*(?:from|in|to|at)?\\s*(?:your|the)?\\s*"
                        + "(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:to|for|at)?\\s*(?<merchant>[A-Za-z0-9@\\s\\.]+)");
        add(list, "general_credit", "Any credit (fallback)", "CREDIT",
                "(?i)(?:Received|credited|deposited)\\s+(?:Rs\\.?|INR)\\s*(?<amount>\\d+(?:\\.\\d{2})?)\\s*"
                        + "(?:has been|is|was)?\\s*(?:in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*"
                        + "(?:[A-Z0-9]+)?\\s*(?:from|by)?\\s*(?<merchant>[A-Za-z0-9@\\s\\.]+)"
                        + "(?:\\s+on\\s+(\\d{2}-\\d{2}-\\d{2}|\\d{2}-[A-Za-z]{3}-\\d{2}))?(?:\\.|$)");
        return Collections.unmodifiableList(list);
    }

    private static MessagePattern add(List<MessagePattern> list, String key, String name, String type, String regex) {
        MessagePattern pattern = new MessagePattern(name, type, regex);
        pattern.setBuiltinKey(key);
        pattern.setSortOrder((list.size() + 1) * 10);
        list.add(pattern);
        return pattern;
    }
}
