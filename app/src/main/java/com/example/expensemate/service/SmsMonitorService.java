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
import com.example.expensemate.R;
import com.example.expensemate.MainActivity;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Transaction;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class SmsMonitorService extends Service {
    private static final String TAG = "SmsMonitorService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SmsMonitorChannel";
    
    private SmsReceiver smsReceiver;
    private AppDatabase database;
    private ExecutorService executorService;
    private boolean isReceiverRegistered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        database = AppDatabase.getDatabase(getApplicationContext());
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
        Log.d(TAG, "Processing SMS from: " + sender);
        Log.d(TAG, "SMS body: " + smsBody);
        
        // Process all SMS messages without bank pattern checking
        Transaction transaction = extractTransactionDetails(smsBody, sender);
        if (transaction != null) {
            Log.d(TAG, "Transaction extracted: " + transaction.getAmount() + " to " + transaction.getReceiverName());
            executorService.execute(() -> {
                database.transactionDao().insertTransaction(transaction);
                Log.d(TAG, "Transaction inserted into database");
            });
        } else {
            Log.d(TAG, "No transaction details could be extracted");
        }
    }

    private Transaction extractTransactionDetails(String smsBody, String sender) {
        try {
            Log.d(TAG, "Extracting transaction details from SMS");
            
            // Pattern for ICICI Bank format
            Pattern iciciPattern = Pattern.compile(
                "ICICI Bank Acct XX(\\d+) debited for Rs (\\d+(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}); ([^;]+) credited"
            );
            
            // Pattern for other bank formats
            Pattern generalPattern = Pattern.compile(
                "Rs\\.?\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:debited|spent|paid)\\s*(?:to|at)\\s*([A-Za-z0-9\\s]+)"
            );

            // Try ICICI pattern first
            var iciciMatcher = iciciPattern.matcher(smsBody);
            if (iciciMatcher.find()) {
                String accountNumber = iciciMatcher.group(1);
                double amount = Double.parseDouble(iciciMatcher.group(2));
                String receiverName = iciciMatcher.group(4).trim();
                Log.d(TAG, "Found ICICI transaction: " + amount + " to " + receiverName);
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
                    accountNumber,
                    "BANK",
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            // Try general pattern if ICICI pattern doesn't match
            var generalMatcher = generalPattern.matcher(smsBody);
            if (generalMatcher.find()) {
                double amount = Double.parseDouble(generalMatcher.group(1));
                String receiverName = generalMatcher.group(2).trim();
                Log.d(TAG, "Found general transaction: " + amount + " to " + receiverName);
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
                    extractAccountNumber(smsBody),
                    "BANK",
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }
            
            Log.d(TAG, "No transaction pattern matched in SMS");
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage(), e);
        }
        return null;
    }

    private String extractAccountNumber(String smsBody) {
        Pattern accountPattern = Pattern.compile("A/c\\s*No\\.?\\s*([A-Z0-9]+)");
        var matcher = accountPattern.matcher(smsBody);
        String accountNumber = matcher.find() ? matcher.group(1) : "Unknown";
        Log.d(TAG, "Extracted account number: " + accountNumber);
        return accountNumber;
    }
} 