package com.example.expensemate.ui.sms;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.TransactionKeys;
import com.example.expensemate.databinding.FragmentSmsScanBinding;
import com.example.expensemate.databinding.ItemScanMessageBinding;
import com.example.expensemate.ui.activity.TransactionFormSheet;
import com.example.expensemate.ui.common.Pickers;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.ScanTriage;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.TransactionFilter;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsScanFragment extends Fragment {
    private FragmentSmsScanBinding binding;
    private TransactionViewModel viewModel;
    private ExecutorService executorService;
    private long customFrom;
    private long customTo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentSmsScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);
        executorService = Executors.newSingleThreadExecutor();

        binding.rangeCustom.setOnClickListener(v -> Pickers.pickRange(getChildFragmentManager(), (from, to) -> {
            customFrom = from;
            customTo = to;
            SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.getDefault());
            binding.rangeCustom.setText(format.format(new Date(from)) + " – " + format.format(new Date(to)));
        }));
        binding.btnScan.setOnClickListener(v -> scan());
        binding.btnPaste.setOnClickListener(v -> showPasteDialog());
    }

    private void scan() {
        if (!MainActivity.hasSmsPermission(requireContext())) {
            showStatus("Turn on SMS reading in Settings to scan messages.");
            return;
        }
        long[] range = selectedRange();
        if (range == null) {
            showStatus("Choose your dates first.");
            return;
        }

        binding.btnScan.setEnabled(false);
        binding.progress.setVisibility(View.VISIBLE);
        showStatus("Scanning…");
        binding.groupStats.setVisibility(View.GONE);
        binding.headerUnknown.setVisibility(View.GONE);
        binding.listUnknown.removeAllViews();

        android.content.ContentResolver resolver = requireContext().getContentResolver();
        executorService.execute(() -> {
            SmsScanner.Result result = null;
            String error = null;
            try {
                result = SmsScanner.scan(resolver, range[0], range[1], viewModel);
            } catch (SecurityException e) {
                error = "Turn on SMS reading in Settings to scan messages.";
            } catch (Exception e) {
                error = "The scan stopped early. Please try again.";
            }
            SmsScanner.Result finalResult = result;
            String finalError = error;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> showResult(finalResult, finalError));
            }
        });
    }

    private long[] selectedRange() {
        int checked = binding.chipsRange.getCheckedChipId();
        if (checked == R.id.rangeCustom) {
            return customFrom == 0 ? null : new long[] { customFrom, customTo };
        }
        Calendar end = Calendar.getInstance();
        TransactionFilter.endOfDay(end);
        Calendar start = (Calendar) end.clone();
        if (checked == R.id.range7) {
            start.add(Calendar.DAY_OF_MONTH, -7);
        } else if (checked == R.id.range30) {
            start.add(Calendar.DAY_OF_MONTH, -30);
        }
        TransactionFilter.startOfDay(start);
        return new long[] { start.getTimeInMillis(), end.getTimeInMillis() };
    }

    private void showStatus(String message) {
        binding.tvStatus.setText(message);
        binding.tvStatus.setVisibility(View.VISIBLE);
    }

    private void showResult(@Nullable SmsScanner.Result result, @Nullable String error) {
        if (binding == null) {
            return;
        }
        binding.btnScan.setEnabled(true);
        binding.progress.setVisibility(View.GONE);
        if (result == null) {
            showStatus(error);
            return;
        }
        binding.tvStatus.setVisibility(View.GONE);
        binding.groupStats.setVisibility(View.VISIBLE);
        binding.tvAdded.setText(String.valueOf(result.added));
        binding.tvExisting.setText(String.valueOf(result.existing));
        binding.tvUnknown.setText(String.valueOf(result.notUnderstood.size()));
        binding.tvUnknown.setTextColor(requireContext().getColor(
                result.notUnderstood.isEmpty() ? R.color.on_surface : R.color.alert));
        renderNotUnderstood(result.notUnderstood);
    }

    private void renderNotUnderstood(List<SmsScanner.Message> messages) {
        binding.headerUnknown.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
        SimpleDateFormat format = new SimpleDateFormat("d MMM", Locale.getDefault());
        for (int i = 0; i < messages.size(); i++) {
            SmsScanner.Message message = messages.get(i);
            boolean first = i == 0;
            boolean last = i == messages.size() - 1;
            ItemScanMessageBinding row = ItemScanMessageBinding.inflate(getLayoutInflater(), binding.listUnknown, false);
            row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                    : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
            row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
            Ui.styleIconTile(row.ivIcon, R.drawable.ic_sms, R.color.warning_container, R.color.warning);
            row.tvBody.setText(message.body);
            row.tvMeta.setText((message.sender != null ? message.sender + " · " : "") + format.format(message.date));
            Ui.styleTag(row.tvAdd, "Add", R.color.primary_container, R.color.on_primary_container);
            row.getRoot().setOnClickListener(v -> {
                TransactionFormSheet.showAddFromMessage(requireActivity(), ScanTriage.amountIn(message.body),
                        message.body, message.sender);
                row.tvAdd.setVisibility(View.GONE);
            });
            binding.listUnknown.addView(row.getRoot());
        }
    }

    private void showPasteDialog() {
        Context context = requireContext();
        EditText input = new EditText(context);
        input.setHint("Paste the message here");
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                input.setText(item.getText());
            }
        }

        FrameLayout container = new FrameLayout(context);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 3, padding, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Paste a message")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String body = input.getText().toString().trim();
                    if (!body.isEmpty()) {
                        processPasted(body);
                    }
                })
                .show();
    }

    private void processPasted(String body) {
        showStatus("Reading the message…");
        executorService.execute(() -> {
            SmsTransactionHandler.TransactionResult result = SmsTransactionHandler.handleSms(body, "", viewModel,
                    new Date(), TransactionKeys.SOURCE_PASTE);
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }
                if (result.success) {
                    showStatus("Added to Activity.");
                } else if ("Duplicate transaction detected".equals(result.reason)) {
                    showStatus("That message is already recorded.");
                } else {
                    binding.tvStatus.setVisibility(View.GONE);
                    TransactionFormSheet.showAddFromMessage(requireActivity(), ScanTriage.amountIn(body), body, "");
                }
            });
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
