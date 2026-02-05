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
        // Setup scan button
        binding.btnScanSms.setOnClickListener(v -> scanSms());
        binding.btnPasteSms.setOnClickListener(v -> showPasteSmsDialog());

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
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void scanSms() {
        if (binding.etFromDate.getText().toString().isEmpty() ||
                binding.etToDate.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Please select both dates", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if we have the required permissions
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(),
                    "SMS read permission is required to scan messages. Please grant the permission in Settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        binding.btnScanSms.setEnabled(false);
        binding.tvScanStatus.setText("Scanning SMS...");

        executorService.execute(() -> {
            try {
                Log.d("SmsScanFragment", "=== Starting SMS Scan ===");
                Log.d("SmsScanFragment", "From Date: " + fromDate.getTime() + " (" + fromDate.getTimeInMillis() + ")");
                Log.d("SmsScanFragment", "To Date: " + toDate.getTime() + " (" + toDate.getTimeInMillis() + ")");

                ContentResolver contentResolver = requireContext().getContentResolver();
                // Query content://sms/ instead of content://sms/inbox to include RCS messages
                Uri uri = Uri.parse("content://sms/");
                Log.d("SmsScanFragment", "Query URI: " + uri);

                String[] projection = {
                        Telephony.Sms._ID,
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.TYPE
                };

                // Filter for inbox messages (type = 1) within the date range
                // This includes both regular SMS and RCS Business Messaging
                String selection = Telephony.Sms.TYPE + " = ? AND " +
                        Telephony.Sms.DATE + " >= ? AND " +
                        Telephony.Sms.DATE + " <= ?";
                String[] selectionArgs = {
                        String.valueOf(Telephony.Sms.MESSAGE_TYPE_INBOX),
                        String.valueOf(fromDate.getTimeInMillis()),
                        String.valueOf(toDate.getTimeInMillis())
                };

                Log.d("SmsScanFragment", "Selection: " + selection);
                Log.d("SmsScanFragment", "Selection Args: TYPE=" + selectionArgs[0] +
                        ", FROM=" + selectionArgs[1] + ", TO=" + selectionArgs[2]);

                // Diagnostic query: Check all message types available
                try {
                    Cursor diagnosticCursor = contentResolver.query(
                            uri,
                            new String[] { Telephony.Sms.TYPE, "COUNT(*) as count" },
                            null,
                            null,
                            null);
                    if (diagnosticCursor != null) {
                        Log.d("SmsScanFragment", "=== Diagnostic: All Message Types in Database ===");
                        while (diagnosticCursor.moveToNext()) {
                            int type = diagnosticCursor.getInt(0);
                            String typeDesc = "";
                            switch (type) {
                                case Telephony.Sms.MESSAGE_TYPE_INBOX:
                                    typeDesc = "INBOX";
                                    break;
                                case Telephony.Sms.MESSAGE_TYPE_SENT:
                                    typeDesc = "SENT";
                                    break;
                                case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                                    typeDesc = "DRAFT";
                                    break;
                                case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                                    typeDesc = "OUTBOX";
                                    break;
                                case Telephony.Sms.MESSAGE_TYPE_FAILED:
                                    typeDesc = "FAILED";
                                    break;
                                case Telephony.Sms.MESSAGE_TYPE_QUEUED:
                                    typeDesc = "QUEUED";
                                    break;
                                default:
                                    typeDesc = "UNKNOWN_TYPE_" + type;
                                    break;
                            }
                            Log.d("SmsScanFragment", "Type: " + type + " (" + typeDesc + ")");
                        }
                        diagnosticCursor.close();
                        Log.d("SmsScanFragment", "=== End Diagnostic ===");
                    }
                } catch (Exception e) {
                    Log.e("SmsScanFragment", "Diagnostic query failed: " + e.getMessage());
                }

                Cursor cursor = contentResolver.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        Telephony.Sms.DATE + " DESC");

                if (cursor == null) {
                    Log.e("SmsScanFragment", "Cursor is NULL! Query failed.");
                } else {
                    Log.d("SmsScanFragment", "Cursor created successfully. Count: " + cursor.getCount());
                }

                // Initialize counters and lists for both SMS and RCS messages
                int processedCount = 0;
                int createdCount = 0;
                List<String> unmatchedSms = new ArrayList<>();
                List<String> duplicateSms = new ArrayList<>();
                List<String> errorSms = new ArrayList<>();
                List<String> success = new ArrayList<>();
                List<String> allSms = new ArrayList<>();

                // Query MMS provider for RCS Business Messaging chats
                Log.d("SmsScanFragment", "=== Querying MMS provider for RCS messages ===");
                Uri mmsUri = Uri.parse("content://mms");

                // Query for RCS messages (m_type = 132) within date range
                String mmsSelection = "m_type = ? AND msg_box = ? AND date >= ? AND date <= ?";
                String[] mmsSelectionArgs = {
                        "132", // RCS message type
                        "1", // Inbox
                        String.valueOf(fromDate.getTimeInMillis() / 1000), // MMS uses seconds, not milliseconds
                        String.valueOf(toDate.getTimeInMillis() / 1000)
                };

                Log.d("SmsScanFragment", "MMS Selection: " + mmsSelection);
                Log.d("SmsScanFragment", "MMS Selection Args: " + java.util.Arrays.toString(mmsSelectionArgs));

                Cursor mmsCursor = null;
                try {
                    mmsCursor = contentResolver.query(
                            mmsUri,
                            new String[] { "_id", "date", "thread_id" },
                            mmsSelection,
                            mmsSelectionArgs,
                            "date DESC");

                    if (mmsCursor == null) {
                        Log.e("SmsScanFragment", "MMS Cursor is NULL!");
                    } else {
                        int mmsCount = mmsCursor.getCount();
                        Log.d("SmsScanFragment", "Found " + mmsCount + " RCS messages");

                        while (mmsCursor.moveToNext()) {
                            long mmsId = mmsCursor.getLong(mmsCursor.getColumnIndexOrThrow("_id"));
                            long mmsDate = mmsCursor.getLong(mmsCursor.getColumnIndexOrThrow("date"));
                            long threadId = mmsCursor.getLong(mmsCursor.getColumnIndexOrThrow("thread_id"));

                            Log.d("SmsScanFragment",
                                    "Processing RCS message ID: " + mmsId + ", Date: " + new Date(mmsDate * 1000));

                            // Get the message body from MMS parts table
                            String messageBody = getMmsText(contentResolver, mmsId);

                            // Get sender from thread (RCS messages don't have direct address)
                            String sender = getThreadAddress(contentResolver, threadId);

                            if (messageBody != null && !messageBody.isEmpty()) {
                                processedCount++;

                                String bodyPreview = messageBody.length() > 50 ? messageBody.substring(0, 50) + "..."
                                        : messageBody;
                                Log.d("SmsScanFragment", String.format(
                                        "RCS Message #%d - ID: %d, Sender: %s, Date: %s, Body: %s",
                                        processedCount, mmsId, sender, new Date(mmsDate * 1000), bodyPreview));

                                allSms.add(messageBody);

                                SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(
                                        messageBody, sender, viewModel, new Date(mmsDate * 1000));

                                if (result.success) {
                                    success.add(messageBody);
                                    createdCount++;
                                    Log.d("SmsScanFragment", "✓ RCS Transaction created successfully");
                                } else {
                                    Log.d("SmsScanFragment", "✗ RCS Failed: " + result.reason);
                                    switch (result.reason) {
                                        case "No transaction pattern matched in SMS":
                                            unmatchedSms.add(messageBody);
                                            break;
                                        case "Duplicate transaction detected":
                                            duplicateSms.add(messageBody);
                                            break;
                                        default:
                                            errorSms.add(messageBody + " (Error: " + result.reason + ")");
                                            break;
                                    }
                                }
                            }
                        }
                        Log.d("SmsScanFragment", "Finished processing RCS messages");
                    }
                } catch (Exception e) {
                    Log.e("SmsScanFragment", "Error querying MMS provider: " + e.getMessage(), e);
                } finally {
                    if (mmsCursor != null) {
                        mmsCursor.close();
                    }
                }

                // Process regular SMS messages

                if (cursor != null) {
                    try {
                        Log.d("SmsScanFragment", "Starting to iterate through " + cursor.getCount() + " messages");
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));
                            String sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                            String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                            long date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                            int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));

                            processedCount++;

                            // Log details of each message
                            String bodyPreview = body != null && body.length() > 50 ? body.substring(0, 50) + "..."
                                    : body;
                            Log.d("SmsScanFragment", String.format(
                                    "Message #%d - ID: %d, Type: %d, Sender: %s, Date: %s, Body: %s",
                                    processedCount, id, type, sender, new Date(date), bodyPreview));

                            Log.d("SmsScanFragment", "Processing the scanned SMS: " + body);
                            allSms.add(body);

                            SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(body,
                                    sender, viewModel, new Date(date));
                            if (result.success) {
                                success.add(body);
                                createdCount++;
                                Log.d("SmsScanFragment", "✓ Transaction created successfully");
                            } else {
                                Log.d("SmsScanFragment", "✗ Failed: " + result.reason);
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
                        Log.d("SmsScanFragment", "Finished iterating through messages");
                    } finally {
                        cursor.close();
                        Log.d("SmsScanFragment", "Cursor closed");
                    }
                }

                final int finalProcessedCount = processedCount;
                final int finalCreatedCount = createdCount;

                // Log unmatched SMS to console
                Log.i("SmsScanFragment", "=== Scan Results ===");
                Log.i("SmsScanFragment", String.format("Processed %d SMS, Created %d transactions",
                        finalProcessedCount, finalCreatedCount));

                // if (!allSms.isEmpty()) {
                // Log.i("SmsScanFragment", "\n=== All SMS (" + allSms.size() + ") ===");
                // for (String sms : allSms) {
                // Log.i("SmsScanFragment", "\n" + sms);
                // }
                // }

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
                            finalCreatedCount));
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

    private void showPasteSmsDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Paste SMS content here...");
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        // Auto-paste from clipboard
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() &&
                clipboard.getPrimaryClipDescription()
                        .hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                input.setText(item.getText());
            }
        }

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) getResources().getDimension(R.dimen.activity_horizontal_margin);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Paste SMS")
                .setView(container)
                .setPositiveButton("Process", (dialog, which) -> {
                    String smsBody = input.getText().toString().trim();
                    if (!smsBody.isEmpty()) {
                        processPastedSms(smsBody);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void processPastedSms(String body) {
        binding.tvScanStatus.setText("Processing...");

        executorService.execute(() -> {
            Date date = new Date();
            // Use empty sender as per requirements
            SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(body, "", viewModel, date);

            requireActivity().runOnUiThread(() -> {
                if (result.success) {
                    Toast.makeText(requireContext(), "Transaction added successfully", Toast.LENGTH_SHORT).show();
                    binding.tvScanStatus.setText("Last processed: Added successfully");
                } else {
                    String message = "Failed to add transaction: " + result.reason;
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    binding.tvScanStatus.setText("Last processed: Failed (" + result.reason + ")");
                }
            });
        });
    }

    /**
     * Extract text content from an MMS message by querying the parts table
     */
    private String getMmsText(ContentResolver contentResolver, long mmsId) {
        Uri partUri = Uri.parse("content://mms/part");
        String selection = "mid = ?";
        String[] selectionArgs = { String.valueOf(mmsId) };

        Cursor partCursor = null;
        StringBuilder body = new StringBuilder();

        try {
            partCursor = contentResolver.query(
                    partUri,
                    new String[] { "_id", "ct", "text" },
                    selection,
                    selectionArgs,
                    null);

            if (partCursor != null) {
                while (partCursor.moveToNext()) {
                    String contentType = partCursor.getString(partCursor.getColumnIndexOrThrow("ct"));

                    // Only process text parts
                    if ("text/plain".equals(contentType)) {
                        String partText = partCursor.getString(partCursor.getColumnIndexOrThrow("text"));
                        if (partText != null) {
                            if (body.length() > 0) {
                                body.append(" ");
                            }
                            body.append(partText);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SmsScanFragment", "Error getting MMS text: " + e.getMessage(), e);
        } finally {
            if (partCursor != null) {
                partCursor.close();
            }
        }

        return body.toString();
    }

    /**
     * Get the address (sender) from a thread ID
     */
    private String getThreadAddress(ContentResolver contentResolver, long threadId) {
        Uri threadUri = Uri.parse("content://mms-sms/conversations/" + threadId);
        Cursor threadCursor = null;
        String address = "";

        try {
            threadCursor = contentResolver.query(
                    threadUri,
                    new String[] { "address" },
                    null,
                    null,
                    "date DESC LIMIT 1");

            if (threadCursor != null && threadCursor.moveToFirst()) {
                int addressIndex = threadCursor.getColumnIndex("address");
                if (addressIndex >= 0) {
                    address = threadCursor.getString(addressIndex);
                }
            }
        } catch (Exception e) {
            Log.e("SmsScanFragment", "Error getting thread address: " + e.getMessage(), e);
        } finally {
            if (threadCursor != null) {
                threadCursor.close();
            }
        }

        return address != null ? address : "";
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