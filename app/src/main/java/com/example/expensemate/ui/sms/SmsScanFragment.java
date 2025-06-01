package com.example.expensemate.ui.sms;

import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.util.Log;
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
import com.example.expensemate.databinding.FragmentSmsScanBinding;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
                List<String> unmatchedSms = new ArrayList<>();
                List<String> duplicateSms = new ArrayList<>();
                List<String> errorSms = new ArrayList<>();
                List<String> success = new ArrayList<>();
                List<String> allSms = new ArrayList<>();

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            String sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                            String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                            long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));

                            processedCount++;
                            Log.d("SmsScanFragment", "Processing the scanned SMS: " + body);
                            allSms.add(body);
                            
                            SmsTransactionHandler.TransactionResult result = 
                                SmsTransactionHandler.handleSms(body, sender, viewModel, new Date(date));
                            if (result.success) {
                                success.add(body);
                                createdCount++;
                            } else {
                                switch (result.reason) {
                                    case "No transaction pattern matched in SMS":
                                        unmatchedSms.add(body);
                                        break;
                                    case "Duplicate transaction detected":
                                        duplicateSms.add(body);
                                        break;
                                    default:
                                        errorSms.add(body + " (Error: " + result.reason + ")");
                                        break;
                                }
                            }
                        }
                    } finally {
                        cursor.close();
                    }
                }

                final int finalProcessedCount = processedCount;
                final int finalCreatedCount = createdCount;

                // Log unmatched SMS to console
                Log.i("SmsScanFragment", "=== Scan Results ===");
                Log.i("SmsScanFragment", String.format("Processed %d SMS, Created %d transactions", 
                    finalProcessedCount, finalCreatedCount));

//                if (!allSms.isEmpty()) {
//                    Log.i("SmsScanFragment", "\n=== All SMS (" + allSms.size() + ") ===");
//                    for (String sms : allSms) {
//                        Log.i("SmsScanFragment", "\n" + sms);
//                    }
//                }

                if (!success.isEmpty()) {
                    Log.i("SmsScanFragment", "\n=== Success SMS (" + success.size() + ") ===");
                    for (String sms : success) {
                        Log.i("SmsScanFragment", "Success: " + sms);
                    }
                }

                if (!unmatchedSms.isEmpty()) {
                    Log.i("SmsScanFragment", "\n=== Unmatched SMS (" + unmatchedSms.size() + ") ===");
                    for (String sms : unmatchedSms) {
                        Log.i("", "" + sms);
                    }
                }

                if (!duplicateSms.isEmpty()) {
                    Log.i("SmsScanFragment", "\n=== Duplicate SMS (" + duplicateSms.size() + ") ===");
                    for (String sms : duplicateSms) {
                        Log.i("SmsScanFragment", "Duplicate: " + sms);
                    }
                }

                if (!errorSms.isEmpty()) {
                    Log.i("SmsScanFragment", "\n=== Error SMS (" + errorSms.size() + ") ===");
                    for (String sms : errorSms) {
                        Log.i("SmsScanFragment", "Error: " + sms);
                    }
                }
                Log.i("SmsScanFragment", "=== End of Scan Results ===\n");

                // Update UI with just the basic status
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 