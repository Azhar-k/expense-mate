package com.example.expensemate.ui.sms;

import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.FragmentSmsScanBinding;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.Manifest;
import android.content.pm.PackageManager;

public class SmsScanFragment extends Fragment {
    private FragmentSmsScanBinding binding;
    private TransactionViewModel viewModel;
    private ExecutorService executorService;
    private Calendar fromDate;
    private Calendar toDate;
    private SimpleDateFormat dateFormat;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSmsScanBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        executorService = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Initialize date pickers
        fromDate = Calendar.getInstance();
        toDate = Calendar.getInstance();
        setupDatePickers();

        // Setup scan button
        binding.btnScanSms.setOnClickListener(v -> scanSms());

        return root;
    }

    private void setupDatePickers() {
        binding.etFromDate.setOnClickListener(v -> showDatePicker(true));
        binding.etToDate.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar calendar = isFromDate ? fromDate : toDate;
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            requireContext(),
            (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                if (isFromDate) {
                    // Set time to start of day (00:00:00)
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    binding.etFromDate.setText(dateFormat.format(calendar.getTime()));
                } else {
                    // Set time to end of day (23:59:59.999)
                    calendar.set(Calendar.HOUR_OF_DAY, 23);
                    calendar.set(Calendar.MINUTE, 59);
                    calendar.set(Calendar.SECOND, 59);
                    calendar.set(Calendar.MILLISECOND, 999);
                    binding.etToDate.setText(dateFormat.format(calendar.getTime()));
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void scanSms() {
        if (binding.etFromDate.getText().toString().isEmpty() || 
            binding.etToDate.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Please select both dates", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if we have the required permissions
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), 
                "SMS read permission is required to scan messages. Please grant the permission in Settings.", 
                Toast.LENGTH_LONG).show();
            return;
        }

        binding.btnScanSms.setEnabled(false);
        binding.tvScanStatus.setText("Scanning SMS...");

        executorService.execute(() -> {
            try {
                ContentResolver contentResolver = requireContext().getContentResolver();
                Uri uri = Uri.parse("content://sms/inbox");
                String[] projection = {
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                };

                String selection = Telephony.Sms.DATE + " >= ? AND " + 
                                 Telephony.Sms.DATE + " <= ?";
                String[] selectionArgs = {
                    String.valueOf(fromDate.getTimeInMillis()),
                    String.valueOf(toDate.getTimeInMillis())
                };

                Cursor cursor = contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    Telephony.Sms.DATE + " DESC"
                );

                int processedCount = 0;
                int createdCount = 0;

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            String sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                            String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                            long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));

                            processedCount++;
                            Transaction transaction = extractTransactionDetails(body, sender);
                            if (transaction != null) {
                                transaction.setDate(new Date(date));
                                String smsHash = transaction.getSmsHash();
                                if (smsHash != null && viewModel.countTransactionsBySmsHash(smsHash) == 0) {
                                    viewModel.insertTransaction(transaction);
                                    createdCount++;
                                }
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }

                final int finalProcessedCount = processedCount;
                final int finalCreatedCount = createdCount;
                requireActivity().runOnUiThread(() -> {
                    binding.btnScanSms.setEnabled(true);
                    binding.tvScanStatus.setText(String.format(
                        "Scan complete!\nProcessed %d SMS\nCreated %d transactions",
                        finalProcessedCount,
                        finalCreatedCount
                    ));
                });
            } catch (SecurityException e) {
                requireActivity().runOnUiThread(() -> {
                    binding.btnScanSms.setEnabled(true);
                    binding.tvScanStatus.setText("Permission denied. Please grant SMS read permission in Settings.");
                    Toast.makeText(requireContext(), 
                        "SMS read permission is required to scan messages. Please grant the permission in Settings.", 
                        Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    binding.btnScanSms.setEnabled(true);
                    binding.tvScanStatus.setText("Error scanning SMS: " + e.getMessage());
                });
                e.printStackTrace();
            }
        });
    }

    private Transaction extractTransactionDetails(String smsBody, String sender) {
        try {
            // Pattern for ICICI Bank format
            java.util.regex.Pattern iciciPattern = java.util.regex.Pattern.compile(
                "ICICI Bank Acct XX(\\d+) debited for Rs (\\d+(?:\\.\\d{2})?) on (\\d{2}-[A-Za-z]{3}-\\d{2}); ([^;]+) credited"
            );
            
            // Pattern for Kotak Bank format
            java.util.regex.Pattern kotakPattern = java.util.regex.Pattern.compile(
                "Sent Rs\\.?(\\d+(?:\\.\\d{2})?) from Kotak Bank AC ([A-Z0-9]+) to ([^\\s]+)"
            );
            
            // Comprehensive pattern for various bank formats
            java.util.regex.Pattern generalPattern = java.util.regex.Pattern.compile(
                "(?i)(?:Rs\\.?|INR)\\s*(\\d+(?:\\.\\d{2})?)\\s*(?:has been|is|was)?\\s*(?:debited|spent|paid|sent|transferred|withdrawn)\\s*(?:from|in|to|at)?\\s*(?:your|the)?\\s*(?:account|a/c|ac|bank)?\\s*(?:[A-Z0-9]+)?\\s*(?:to|for|at)?\\s*([A-Za-z0-9@\\s\\.]+)"
            );

            // Try ICICI pattern first
            var iciciMatcher = iciciPattern.matcher(smsBody);
            if (iciciMatcher.find()) {
                String accountNumber = iciciMatcher.group(1);
                double amount = Double.parseDouble(iciciMatcher.group(2));
                String receiverName = iciciMatcher.group(4).trim();
                
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
                String accountNumber = kotakMatcher.group(2);
                String receiverName = kotakMatcher.group(3).trim();
                
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 