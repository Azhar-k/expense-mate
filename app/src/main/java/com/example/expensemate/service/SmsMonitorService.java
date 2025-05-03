package com.example.expensemate.service;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.provider.Telephony;
import android.util.Log;
import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.Transaction;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class SmsMonitorService extends Service {
    private SmsReceiver smsReceiver;
    private AppDatabase database;
    private ExecutorService executorService;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getDatabase(getApplicationContext());
        executorService = Executors.newSingleThreadExecutor();
        smsReceiver = new SmsReceiver((smsBody, sender) -> processSms(smsBody, sender));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        IntentFilter filter = new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        registerReceiver(smsReceiver, filter);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(smsReceiver);
        executorService.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void processSms(String smsBody, String sender) {
        if (isBankSms(sender)) {
            Transaction transaction = extractTransactionDetails(smsBody, sender);
            if (transaction != null) {
                executorService.execute(() -> 
                    database.transactionDao().insertTransaction(transaction)
                );
            }
        }
    }

    private boolean isBankSms(String sender) {
        List<String> bankPatterns = Arrays.asList(
            "HDFCBK",
            "ICICIB",
            "SBIBNK",
            "AXISBK",
            "KOTAKB"
        );
        return bankPatterns.stream().anyMatch(pattern -> 
            sender.toLowerCase().contains(pattern.toLowerCase())
        );
    }

    private Transaction extractTransactionDetails(String smsBody, String sender) {
        try {
            Pattern debitPattern = Pattern.compile(
                "Rs\\.?\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:debited|spent|paid)\\s*(?:to|at)\\s*([A-Za-z0-9\\s]+)"
            );
            var matcher = debitPattern.matcher(smsBody);

            if (matcher.find()) {
                double amount = Double.parseDouble(matcher.group(1));
                String receiverName = matcher.group(2).trim();
                
                return new Transaction(
                    amount,
                    "Debit transaction",
                    new Date(),
                    extractAccountNumber(smsBody),
                    determineAccountType(sender),
                    "DEBIT",
                    receiverName,
                    smsBody,
                    sender
                );
            }
        } catch (Exception e) {
            Log.e("SmsMonitorService", "Error processing SMS: " + e.getMessage());
        }
        return null;
    }

    private String extractAccountNumber(String smsBody) {
        Pattern accountPattern = Pattern.compile("A/c\\s*No\\.?\\s*([A-Z0-9]+)");
        var matcher = accountPattern.matcher(smsBody);
        return matcher.find() ? matcher.group(1) : "Unknown";
    }

    private String determineAccountType(String sender) {
        return sender.toLowerCase().contains("card") ? "CREDIT_CARD" : "BANK";
    }
} 