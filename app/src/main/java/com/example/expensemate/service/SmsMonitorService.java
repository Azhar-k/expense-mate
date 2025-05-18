package com.example.expensemate.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Telephony;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.expensemate.R;
import com.example.expensemate.MainActivity;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.example.expensemate.viewmodel.AccountViewModel;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class SmsMonitorService extends Service {
    private static final String TAG = "SmsMonitorService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SmsMonitorChannel";
    
    private ContentObserver smsObserver;
    private TransactionViewModel transactionViewModel;
    private AccountViewModel accountViewModel;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        transactionViewModel = new TransactionViewModel(getApplication());
        accountViewModel = new AccountViewModel(getApplication());
        executorService = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service starting");
        
        // Start as a foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        setupSmsObserver();
        
        return START_STICKY;
    }

    private void setupSmsObserver() {
        smsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                Log.d(TAG, "SMS database changed");
                processNewSms();
            }
        };

        getContentResolver().registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        );
    }

    private void processNewSms() {
        executorService.execute(() -> {
            try {
                // Get the latest SMS
                android.database.Cursor cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE
                    },
                    null,
                    null,
                    Telephony.Sms.DATE + " DESC LIMIT 1"
                );

                if (cursor != null && cursor.moveToFirst()) {
                    String sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    cursor.close();

                    Log.d(TAG, "Processing new SMS from: " + sender);
                    Log.d(TAG, "SMS body: " + body);

                    Transaction transaction = extractTransactionDetails(body, sender);
                    if (transaction != null) {
                        transaction.setDate(new Date(date));
                        String smsHash = transaction.getSmsHash();
                        if (smsHash != null && transactionViewModel.countTransactionsBySmsHash(smsHash) == 0) {
                            // Set default account if available
                            accountViewModel.getDefaultAccount().observeForever(defaultAccount -> {
                                if (defaultAccount != null) {
                                    transaction.setAccountId(defaultAccount.getId());
                                }
                                transactionViewModel.insertTransaction(transaction);
                                accountViewModel.getDefaultAccount().removeObserver(defaultAccount1 -> {});
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing SMS", e);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        if (smsObserver != null) {
            getContentResolver().unregisterContentObserver(smsObserver);
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

    private Transaction extractTransactionDetails(String smsBody, String sender) {
        try {
            Log.d(TAG, "Extracting transaction details from SMS");
            
            // Pattern for ICICI Bank format
            Pattern iciciPattern = Pattern.compile(
                "ICICI Bank Acct XX(\\d+) debited for Rs (\\d+(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}); ([^;]+) credited"
            );
            
            // Pattern for Kotak Bank format
            Pattern kotakPattern = Pattern.compile(
                "Sent Rs\\.?(\\d+(?:\\.\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to ([^\\s]+)"
            );
            
            // Comprehensive pattern for various bank formats
            Pattern generalPattern = Pattern.compile(
                "(?i)(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:debited|spent|paid|sent|transferred|withdrawn)\\s*(?:from|in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:to|for|at)?\\s*([A-Za-z0-9@\\s\\.]+)"
            );

            // Try ICICI pattern first
            var iciciMatcher = iciciPattern.matcher(smsBody);
            if (iciciMatcher.find()) {
                double amount = Double.parseDouble(iciciMatcher.group(2));
                String receiverName = iciciMatcher.group(4).trim();
                Log.d(TAG, "Found ICICI transaction: " + amount + " to " + receiverName);
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            // Try Kotak pattern
            var kotakMatcher = kotakPattern.matcher(smsBody);
            if (kotakMatcher.find()) {
                double amount = Double.parseDouble(kotakMatcher.group(1));
                String receiverName = kotakMatcher.group(3).trim();
                Log.d(TAG, "Found Kotak transaction: " + amount + " to " + receiverName);
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }

            // Try general pattern if specific patterns don't match
            var generalMatcher = generalPattern.matcher(smsBody);
            if (generalMatcher.find()) {
                double amount = Double.parseDouble(generalMatcher.group(1));
                String receiverName = generalMatcher.group(2).trim();
                Log.d(TAG, "Found general transaction: " + amount + " to " + receiverName);
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
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
} 