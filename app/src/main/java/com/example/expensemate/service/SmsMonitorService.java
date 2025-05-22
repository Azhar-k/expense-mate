package com.example.expensemate.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsMonitorService extends Service {
    private static final String TAG = "SmsMonitorService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SmsMonitorChannel";

    private SmsReceiver smsReceiver;
    private TransactionViewModel transactionViewModel;
    private ExecutorService executorService;
    private boolean isReceiverRegistered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        transactionViewModel = new TransactionViewModel(getApplication());
        executorService = Executors.newSingleThreadExecutor();
        smsReceiver = new SmsReceiver((smsBody, sender) -> processSms(smsBody, sender));
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

        // Only unregister if registered
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(smsReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "SMS receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
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
                    NotificationManager.IMPORTANCE_LOW
            );
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
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Expense Mate")
                .setContentText("Monitoring SMS for transactions")
                .setSmallIcon(R.drawable.ic_menu_transactions)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void processSms(String smsBody, String sender) {
        executorService.execute(() -> {
            SmsTransactionHandler.handleSms(smsBody, sender, transactionViewModel, null);
        });
    }
} 