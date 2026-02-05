package com.example.expensemate.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsMonitorService extends Service {
    private static final String TAG = "SmsMonitorService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SmsMonitorChannel";
    private static final String PREFS_NAME = "SmsMonitorPrefs";
    private static final String PREF_LAST_MMS_ID = "lastMmsId";

    private SmsReceiver smsReceiver;
    private MmsContentObserver mmsObserver;
    private TransactionViewModel transactionViewModel;
    private ExecutorService executorService;
    private boolean isReceiverRegistered = false;
    private boolean isObserverRegistered = false;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        transactionViewModel = new TransactionViewModel(getApplication());
        executorService = Executors.newSingleThreadExecutor();
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        smsReceiver = new SmsReceiver((smsBody, sender) -> processSms(smsBody, sender));

        // Register MMS observer for RCS messages
        mmsObserver = new MmsContentObserver(new Handler(Looper.getMainLooper()));
        getContentResolver().registerContentObserver(
                Uri.parse("content://mms"),
                true,
                mmsObserver);
        isObserverRegistered = true;
        Log.d(TAG, "MMS observer registered for RCS monitoring");

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service starting");

        // Start as a foreground service
        startForeground(NOTIFICATION_ID, createNotification());

        // Register receiver if not already registered
        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
            registerReceiver(smsReceiver, filter);
            isReceiverRegistered = true;
            Log.d(TAG, "SMS receiver registered");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // Unregister SMS receiver
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(smsReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "SMS receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
            }
        }

        // Unregister MMS observer
        if (isObserverRegistered) {
            try {
                getContentResolver().unregisterContentObserver(mmsObserver);
                isObserverRegistered = false;
                Log.d(TAG, "MMS observer unregistered");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering observer: " + e.getMessage());
            }
        }

        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Monitor Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Monitors incoming SMS for bank transactions");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Expense Mate")
                .setContentText("Monitoring SMS for transactions")
                .setSmallIcon(R.drawable.ic_menu_transactions)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void processSms(String smsBody, String sender) {
        executorService.execute(() -> {
            Log.d(TAG, "Processing SMS automatically: " + smsBody);
            SmsTransactionHandler.handleSms(smsBody, sender, transactionViewModel, null);
        });
    }

    /**
     * ContentObserver to monitor MMS content provider for new RCS messages
     */
    private class MmsContentObserver extends ContentObserver {
        public MmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            Log.d(TAG, "MMS content changed: " + uri);

            executorService.execute(() -> {
                try {
                    // Get the last processed MMS ID
                    long lastMmsId = prefs.getLong(PREF_LAST_MMS_ID, -1);

                    // Query for the latest RCS message
                    ContentResolver resolver = getContentResolver();
                    Uri mmsUri = Uri.parse("content://mms");

                    Cursor cursor = resolver.query(
                            mmsUri,
                            new String[] { "_id", "date", "thread_id", "m_type" },
                            "m_type = ? AND msg_box = ?",
                            new String[] { "132", "1" }, // RCS inbox messages
                            "_id DESC LIMIT 1");

                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                long mmsId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));

                                // Only process if this is a new message
                                if (mmsId > lastMmsId) {
                                    long mmsDate = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                                    long threadId = cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"));

                                    Log.d(TAG, "New RCS message detected: ID=" + mmsId);

                                    // Get message content and sender
                                    String messageBody = getMmsText(resolver, mmsId);
                                    String sender = getThreadAddress(resolver, threadId);

                                    if (messageBody != null && !messageBody.isEmpty()) {
                                        processRcsMessage(messageBody, sender, new Date(mmsDate * 1000));

                                        // Update last processed ID
                                        prefs.edit().putLong(PREF_LAST_MMS_ID, mmsId).apply();
                                    }
                                }
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing MMS change: " + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Extract text content from an MMS message
     */
    private String getMmsText(ContentResolver resolver, long mmsId) {
        Uri partUri = Uri.parse("content://mms/part");
        Cursor cursor = null;
        StringBuilder body = new StringBuilder();

        try {
            cursor = resolver.query(
                    partUri,
                    new String[] { "text", "ct" },
                    "mid = ?",
                    new String[] { String.valueOf(mmsId) },
                    null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"));
                    if ("text/plain".equals(contentType)) {
                        String text = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                        if (text != null) {
                            if (body.length() > 0)
                                body.append(" ");
                            body.append(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting MMS text: " + e.getMessage());
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return body.toString();
    }

    /**
     * Get sender address from thread ID
     */
    private String getThreadAddress(ContentResolver resolver, long threadId) {
        String address = "";
        Cursor cursor = null;

        try {
            // Try SMS table first
            Uri smsUri = Uri.parse("content://sms");
            cursor = resolver.query(
                    smsUri,
                    new String[] { "address" },
                    "thread_id = ?",
                    new String[] { String.valueOf(threadId) },
                    "_id DESC LIMIT 1");

            if (cursor != null && cursor.moveToFirst()) {
                int addressIndex = cursor.getColumnIndex("address");
                if (addressIndex >= 0) {
                    address = cursor.getString(addressIndex);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Could not get address: " + e.getMessage());
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return address != null ? address : "";
    }

    /**
     * Process RCS message
     */
    private void processRcsMessage(String messageBody, String sender, Date date) {
        Log.d(TAG, "Processing RCS message automatically from: " + sender);
        SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(messageBody, sender,
                transactionViewModel, date);

        if (result.success) {
            Log.d(TAG, "RCS transaction created successfully");
        } else {
            Log.d(TAG, "RCS transaction not created: " + result.reason);
        }
    }
}