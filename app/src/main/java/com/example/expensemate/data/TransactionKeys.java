package com.example.expensemate.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the keys used to recognise one payment arriving through several channels.
 * Sender and time are left out on purpose: banks resend from other sender IDs, and
 * the channels report different times for the same message.
 */
public final class TransactionKeys {
    public static final String SOURCE_SMS_BROADCAST = "SMS_BROADCAST";
    public static final String SOURCE_SMS_SCAN = "SMS_SCAN";
    public static final String SOURCE_MMS_OBSERVER = "MMS_OBSERVER";
    public static final String SOURCE_MMS_SCAN = "MMS_SCAN";
    public static final String SOURCE_NOTIFICATION = "NOTIFICATION";
    public static final String SOURCE_TRUECALLER = "TRUECALLER";
    public static final String SOURCE_PASTE = "PASTE";
    public static final String SOURCE_LEGACY = "LEGACY";

    // Truecaller alerts carry no full text, and the same alert can show up in
    // content://mms over five minutes later.
    public static final long DUPLICATE_WINDOW_MS = 15 * 60 * 1000;

    private static final String TRUECALLER_DESCRIPTION_PREFIX = "RCS";

    // Sender IDs such as AX-ICICIT-S, and plain phone numbers.
    private static final Pattern SMS_SENDER = Pattern.compile("^(?:[A-Z0-9]{2}-[A-Za-z0-9]{3,}(?:-[A-Z])?|\\+?\\d+)$");

    private TransactionKeys() {
    }

    /** Hash of the normalized text, or null when there is no message text. */
    public static String bodyKey(String body) {
        if (!hasMessageText(body)) {
            return null;
        }
        return sha256(normalize(body));
    }

    /** Amount, direction and merchant, or null when the merchant is unknown. */
    public static String matchKey(double amount, String transactionType, String merchant) {
        if (merchant == null || transactionType == null) {
            return null;
        }
        String normalizedMerchant = normalize(merchant).replaceAll("[^\\p{L}\\p{N}]", "");
        if (normalizedMerchant.isEmpty()) {
            return null;
        }
        return Math.round(amount * 100) + "|" + transactionType + "|" + normalizedMerchant;
    }

    /** True for notification titles that mean the message already came in as an SMS. */
    public static boolean isSmsSender(String title) {
        return title != null && SMS_SENDER.matcher(title.trim()).matches();
    }

    /** Backups restore "null" as text, so that does not count as a message. */
    public static boolean hasMessageText(String body) {
        return body != null && !body.trim().isEmpty() && !body.trim().equals("null");
    }

    public static boolean isTruecallerRow(String description) {
        return description != null && description.startsWith(TRUECALLER_DESCRIPTION_PREFIX);
    }

    /** Fills the keys for a row whose original message time is no longer known. */
    public static void fillLegacyKeys(Transaction transaction) {
        if (!hasMessageText(transaction.getSmsBody())) {
            return;
        }
        boolean truecaller = isTruecallerRow(transaction.getDescription());
        transaction.setSource(truecaller ? SOURCE_TRUECALLER : SOURCE_LEGACY);
        transaction.setDedupKey(truecaller ? null : bodyKey(transaction.getSmsBody()));
        transaction.setMatchKey(matchKey(transaction.getAmount(), transaction.getTransactionType(),
                transaction.getReceiverName()));
        transaction.setMessageTime(transaction.getDate() != null ? transaction.getDate().getTime() : null);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on Android", e);
        }
    }
}
