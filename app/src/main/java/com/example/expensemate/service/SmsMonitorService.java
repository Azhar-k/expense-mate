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
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.util.MmsReader;
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
    private static final String PREF_LAST_SMS_TIME = "lastSmsTime";

    private SmsReceiver smsReceiver;
    private MmsContentObserver mmsObserver;
    private TransactionViewModel transactionViewModel;
    private ExecutorService executorService;
    private boolean isReceiverRegistered = false;
    private boolean isObserverRegistered = false;
    private boolean hasCaughtUp = false;
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

        // Pick up whatever arrived while the service was not running
        if (!hasCaughtUp) {
            hasCaughtUp = true;
            executorService.execute(() -> {
                catchUpOnSms();
                processNewMmsRows();
            });
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
                .setSmallIcon(R.drawable.ic_activity)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void processSms(String smsBody, String sender) {
        executorService.execute(() -> {
            Log.d(TAG, "Processing SMS automatically");
            SmsTransactionHandler.handleSms(smsBody, sender, transactionViewModel, null,
                    TransactionKeys.SOURCE_SMS_BROADCAST);
            prefs.edit().putLong(PREF_LAST_SMS_TIME, System.currentTimeMillis()).apply();
        });
    }

    /**
     * Processes inbox SMS newer than the last one seen. The first run only records the
     * current time, so older messages stay with the manual scan.
     */
    private void catchUpOnSms() {
        long now = System.currentTimeMillis();
        long lastSmsTime = prefs.getLong(PREF_LAST_SMS_TIME, -1);
        if (lastSmsTime < 0) {
            prefs.edit().putLong(PREF_LAST_SMS_TIME, now).apply();
            return;
        }

        try (Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[] { Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE },
                Telephony.Sms.TYPE + " = ? AND " + Telephony.Sms.DATE + " > ?",
                new String[] { String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX), String.valueOf(lastSmsTime) },
                Telephony.Sms.DATE + " ASC")) {
            if (cursor != null) {
                Log.d(TAG, "SMS to catch up on: " + cursor.getCount());
                while (cursor.moveToNext()) {
                    SmsTransactionHandler.handleSms(cursor.getString(1), cursor.getString(0), transactionViewModel,
                            new Date(cursor.getLong(2)), TransactionKeys.SOURCE_SMS_SCAN);
                }
            }
            prefs.edit().putLong(PREF_LAST_SMS_TIME, now).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error catching up on SMS: " + e.getMessage(), e);
        }
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
            executorService.execute(SmsMonitorService.this::processNewMmsRows);
        }
    }

    /**
     * Processes every RCS inbox row newer than the last one handled. The first run only
     * records the newest ID, so older messages stay with the manual scan.
     */
    private void processNewMmsRows() {
        ContentResolver resolver = getContentResolver();
        long lastMmsId = prefs.getLong(PREF_LAST_MMS_ID, -1);

        try (Cursor cursor = resolver.query(
                Uri.parse("content://mms"),
                new String[] { "_id", "date" },
                "m_type = ? AND msg_box = ? AND _id > ?",
                new String[] { "132", "1", String.valueOf(lastMmsId) }, // RCS inbox messages
                "_id ASC")) {
            if (cursor == null) {
                return;
            }
            if (lastMmsId < 0) {
                if (cursor.moveToLast()) {
                    prefs.edit().putLong(PREF_LAST_MMS_ID, cursor.getLong(0)).apply();
                }
                return;
            }
            while (cursor.moveToNext()) {
                long mmsId = cursor.getLong(0);
                // The parts are written after the row; the next change event retries from here.
                if (!MmsReader.hasParts(resolver, mmsId)) {
                    return;
                }
                String messageBody = MmsReader.getText(resolver, mmsId);
                if (!messageBody.isEmpty()) {
                    processRcsMessage(messageBody, MmsReader.getSender(resolver, mmsId),
                            new Date(cursor.getLong(1) * 1000));
                }
                prefs.edit().putLong(PREF_LAST_MMS_ID, mmsId).apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing MMS change: " + e.getMessage(), e);
        }
    }

    /**
     * Process RCS message
     */
    private void processRcsMessage(String messageBody, String sender, Date date) {
        Log.d(TAG, "Processing RCS message automatically from: " + sender);
        SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(messageBody, sender,
                transactionViewModel, date, TransactionKeys.SOURCE_MMS_OBSERVER);

        if (result.success) {
            Log.d(TAG, "RCS transaction created successfully");
        } else {
            Log.d(TAG, "RCS transaction not created: " + result.reason);
        }
    }
}
