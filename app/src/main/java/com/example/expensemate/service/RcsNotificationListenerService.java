package com.example.expensemate.service;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NotificationListenerService that captures RCS Business Messaging transactions which are
 * not available anywhere else. Every transaction goes through the shared duplicate check,
 * so a notification repeated or reposted later adds nothing.
 *
 * SBI card alerts mostly arrive as Truecaller business chats, which never reach the SMS
 * or MMS providers. Their notification only has the amount and merchant:
 *   android.subText  = "Business chat from SBI CARDS"   → sender
 *   android.title    = "₹1"                             → amount
 *   android.text     = "•  SBI CARDS  •  Frontparking"  → sender • merchant
 */
public class RcsNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "RcsNotifListener";

    private TransactionViewModel transactionViewModel;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "=== RcsNotificationListenerService CREATED ===");
        transactionViewModel = new TransactionViewModel(getApplication());
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "=== onListenerConnected: Service active ===");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "=== onListenerDisconnected ===");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        if (packageName == null) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        // A group summary only repeats the notifications inside it
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notification.extras;
        // Unlike the post time, this does not change when a notification is updated
        long messageTime = notification.when > 0 ? notification.when : sbn.getPostTime();

        // ── Truecaller RCS Business Chat ──────────────────────────────────────────
        if (packageName.equals("com.truecaller")) {
            String subText = getExtra(extras, "android.subText");
            if (subText != null && subText.startsWith("Business chat from ")) {
                String sender = subText.replace("Business chat from ", "").trim();
                String title  = getExtra(extras, "android.title");  // e.g. "₹1"
                String text   = getExtra(extras, "android.text");   // e.g. "•  SBI CARDS  •  Frontparking"

                Log.d(TAG, "Truecaller RCS business chat detected from: " + sender);
                executorService.execute(() -> parseTruecallerRcsNotification(sender, title, text, messageTime));
                return;
            }
        }

        // ── Standard messaging apps (Google Messages, Samsung Messages) ──────────
        if (isStandardMessagingApp(packageName)) {
            String sender  = getExtra(extras, Notification.EXTRA_TITLE);
            String bigText = getExtra(extras, Notification.EXTRA_BIG_TEXT);
            String text    = getExtra(extras, Notification.EXTRA_TEXT);
            String body    = bigText != null ? bigText : text;
            if (TransactionKeys.isSmsSender(sender)) {
                // SmsMonitorService already has this message from the SMS broadcast
                Log.d(TAG, "Skipping SMS notification from: " + sender);
                return;
            }
            if (sender != null && body != null && !body.trim().isEmpty()) {
                executorService.execute(() -> processBody(body, sender, new Date(messageTime)));
            }
        }
    }

    private void parseTruecallerRcsNotification(String sender, String title, String text, long messageTime) {
        if (title == null || text == null) {
            Log.w(TAG, "Truecaller RCS: title or text is null, cannot parse.");
            return;
        }

        // Parse amount from title — remove ₹, commas, spaces
        String amountStr = title.replaceAll("[₹,\\s]", "").trim();
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Truecaller RCS: could not parse amount from title=[" + title + "]");
            return;
        }

        // Parse merchant from text: "•  SBI CARDS  •  Frontparking"
        // Split on bullet/dot separators
        String merchant = sender; // default
        String[] parts = text.split("•");
        if (parts.length >= 2) {
            merchant = parts[parts.length - 1].replaceAll("[;,.\\s]+$", "").trim();
        }
        if (merchant.isEmpty()) merchant = sender;

        SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleTruecallerAlert(
                amount, merchant, text, sender, transactionViewModel, new Date(messageTime));
        Log.d(TAG, "Truecaller RCS | Amount=" + amount + " | Sender=[" + sender + "] | Merchant=[" + merchant
                + "] | " + result.reason);
    }

    private void processBody(String body, String sender, Date date) {
        if (body == null || body.trim().isEmpty()) return;
        Log.d(TAG, "Passing to SmsTransactionHandler | Sender=[" + sender
                + "] | Body=[" + (body.length() > 60 ? body.substring(0, 60) + "..." : body) + "]");
        SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(
                body, sender, transactionViewModel, date, TransactionKeys.SOURCE_NOTIFICATION);
        if (result.success) {
            Log.d(TAG, "✓ Transaction created from full message body");
        } else {
            Log.d(TAG, "✗ No transaction. Reason: " + result.reason);
        }
    }

    private boolean isStandardMessagingApp(String packageName) {
        return packageName.equals("com.google.android.apps.messaging")
                || packageName.equals("com.samsung.android.messaging")
                || packageName.equals("com.android.mms");
    }

    private String getExtra(Bundle extras, String key) {
        CharSequence cs = extras.getCharSequence(key);
        return cs != null ? cs.toString() : null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "=== RcsNotificationListenerService DESTROYED ===");
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
