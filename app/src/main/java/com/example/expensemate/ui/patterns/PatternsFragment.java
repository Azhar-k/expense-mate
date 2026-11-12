package com.example.expensemate.ui.patterns;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.expensemate.R;
import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.DialogPatternFormBinding;
import com.example.expensemate.databinding.FragmentPatternsBinding;
import com.example.expensemate.databinding.ItemPatternBinding;
import com.example.expensemate.ui.common.FormSheet;
import com.example.expensemate.ui.common.ReorderDialog;
import com.example.expensemate.ui.common.Ui;
import com.example.expensemate.util.PatternChecks;
import com.example.expensemate.util.PatternDraft;
import com.example.expensemate.util.SmsTransactionHandler;
import com.example.expensemate.viewmodel.PatternViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Lists the patterns that read bank messages and edits them. Messages no pattern reads are not
 * kept anywhere: the user copies one from their messages app, or shares it here, to add a pattern.
 */
public class PatternsFragment extends Fragment {
    /** Text shared into the app, which opens a new pattern with it as the sample. */
    public static final String ARG_SHARED_TEXT = "sharedText";

    private FragmentPatternsBinding binding;
    private PatternViewModel viewModel;
    private List<MessagePattern> patterns = new ArrayList<>();
    @Nullable
    private Runnable recheckOpenForm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPatternsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(PatternViewModel.class);
        Ui.addReorderAction(this, this::reorder);
        binding.fabAdd.setOnClickListener(v -> showForm(null, null));
        viewModel.getPatterns().observe(getViewLifecycleOwner(), list -> {
            patterns = list;
            render();
            // A form opened from a share can be up before the list first loads
            if (recheckOpenForm != null) {
                recheckOpenForm.run();
            }
        });

        Bundle args = getArguments();
        if (savedInstanceState == null && args != null && args.getString(ARG_SHARED_TEXT) != null) {
            String shared = args.getString(ARG_SHARED_TEXT);
            args.remove(ARG_SHARED_TEXT);
            showForm(null, shared);
        }
    }

    private void render() {
        binding.list.removeAllViews();
        for (int i = 0; i < patterns.size(); i++) {
            MessagePattern pattern = patterns.get(i);
            boolean first = i == 0;
            boolean last = i == patterns.size() - 1;
            ItemPatternBinding row = ItemPatternBinding.inflate(getLayoutInflater(), binding.list, false);
            row.getRoot().setBackgroundResource(first && last ? R.drawable.bg_row_single
                    : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
            row.divider.setVisibility(last ? View.GONE : View.VISIBLE);
            Ui.styleIconTile(row.ivIcon, R.drawable.ic_sms, R.color.surface_variant, R.color.on_surface);
            row.tvName.setText(pattern.getName());
            row.tvSubtitle.setText(("CREDIT".equals(pattern.getTransactionType()) ? "Money in" : "Money out")
                    + (pattern.isBuiltin() ? " · Built in" : " · Yours"));
            if (!pattern.isActive()) {
                Ui.styleTag(row.tvTag, "Off", R.color.surface_variant, R.color.on_surface);
            } else if (pattern.compiled() == null) {
                Ui.styleTag(row.tvTag, "Invalid", R.color.warning_container, R.color.warning);
            }
            row.getRoot().setOnClickListener(v -> showForm(pattern, null));
            binding.list.addView(row.getRoot());
        }
    }

    private void reorder() {
        List<String> names = new ArrayList<>();
        for (MessagePattern pattern : patterns) {
            names.add(pattern.getName());
        }
        List<MessagePattern> shown = new ArrayList<>(patterns);
        ReorderDialog.show(requireContext(), "Order patterns", names, order -> {
            List<Long> ids = new ArrayList<>();
            for (int index : order) {
                ids.add(shown.get(index).getId());
            }
            viewModel.saveOrder(ids);
        });
    }

    private void showForm(@Nullable MessagePattern pattern, @Nullable String sample) {
        DialogPatternFormBinding form = DialogPatternFormBinding.inflate(getLayoutInflater());
        int[] amount = { -1, -1 };
        int[] merchant = { -1, -1 };

        form.typeToggle.check(pattern != null && "CREDIT".equals(pattern.getTransactionType())
                ? R.id.typeCredit : R.id.typeDebit);
        if (pattern != null) {
            form.etName.setText(pattern.getName());
            form.etPattern.setText(pattern.getPattern());
            form.etCounterparty.setText(pattern.getCounterparty());
            form.switchExclude.setChecked(pattern.isExcludeFromSummary());
            form.switchActive.setChecked(pattern.isActive());
        } else {
            form.switchActive.setChecked(true);
        }
        if (sample != null) {
            form.etSample.setText(sample.trim());
        }

        long selfId = pattern != null ? pattern.getId() : 0;
        Runnable test = () -> showResult(form, selfId);
        recheckOpenForm = test;
        TextWatcher retest = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                test.run();
            }
        };
        form.etSample.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Marks point into the old text
                amount[0] = amount[1] = merchant[0] = merchant[1] = -1;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        form.etSample.addTextChangedListener(retest);
        form.etName.addTextChangedListener(retest);
        form.etPattern.addTextChangedListener(retest);
        form.etCounterparty.addTextChangedListener(retest);
        form.typeToggle.addOnButtonCheckedListener((group, id, checked) -> test.run());

        form.btnPaste.setOnClickListener(v -> {
            String clip = clipboardText();
            if (clip == null) {
                Ui.snackbar(requireActivity(), "Nothing to paste. Copy the message first.");
            } else {
                form.etSample.setText(clip.trim());
            }
        });
        form.btnMarkAmount.setOnClickListener(v -> {
            if (mark(form, amount)) {
                draft(form, amount, merchant);
            }
        });
        form.btnMarkMerchant.setOnClickListener(v -> {
            if (mark(form, merchant)) {
                draft(form, amount, merchant);
            }
        });
        test.run();

        FormSheet sheet = FormSheet.create(requireContext(), pattern == null ? "Add a pattern" : "Edit pattern",
                form.getRoot());
        if (pattern != null) {
            sheet.onDelete(() -> confirmDelete(pattern));
        }
        sheet.onSave(() -> {
            String name = text(form.etName.getText());
            String regex = text(form.etPattern.getText());
            form.tilName.setError(name.isEmpty() ? "Enter a name" : null);
            String regexError = validate(regex);
            MessagePattern same = PatternChecks.sameExpression(patterns, regex, selfId);
            if (regexError == null && same != null) {
                regexError = "Same as " + same.getName();
            }
            form.tilPattern.setError(regexError);
            if (name.isEmpty() || regexError != null) {
                return;
            }
            MessagePattern saved = pattern != null ? pattern : new MessagePattern();
            fill(saved, form);
            if (pattern == null) {
                viewModel.add(saved);
            } else {
                viewModel.update(saved);
            }
            sheet.dismiss();
        });
        sheet.show();
    }

    // Records the selected part of the sample message
    private boolean mark(DialogPatternFormBinding form, int[] range) {
        int start = Math.min(form.etSample.getSelectionStart(), form.etSample.getSelectionEnd());
        int end = Math.max(form.etSample.getSelectionStart(), form.etSample.getSelectionEnd());
        if (start < 0 || start == end) {
            Ui.snackbar(requireActivity(), "Select that part of the message first");
            return false;
        }
        range[0] = start;
        range[1] = end;
        return true;
    }

    private void draft(DialogPatternFormBinding form, int[] amount, int[] merchant) {
        if (amount[0] < 0) {
            return;
        }
        String regex = PatternDraft.build(text(form.etSample.getText()), amount[0], amount[1], merchant[0], merchant[1]);
        if (regex != null) {
            form.etPattern.setText(regex);
        }
    }

    private void showResult(DialogPatternFormBinding form, long selfId) {
        MessagePattern sameName = PatternChecks.sameName(patterns, text(form.etName.getText()), selfId);
        form.tilName.setHelperText(sameName == null ? null : "Another pattern is also called this");

        String sample = text(form.etSample.getText());
        String regex = text(form.etPattern.getText());
        MessagePattern sameRegex = regex.isEmpty() ? null : PatternChecks.sameExpression(patterns, regex, selfId);
        PatternChecks.Overlap overlap = PatternChecks.firstOtherReader(patterns, sample, selfId);

        StringBuilder result = new StringBuilder();
        String error = regex.isEmpty() ? null : validate(regex);
        if (sameRegex != null) {
            result.append("This is the same pattern as ").append(sameRegex.getName()).append('.');
        } else if (error != null) {
            result.append(error);
        } else if (!regex.isEmpty() && !sample.isEmpty()) {
            MessagePattern trial = new MessagePattern();
            fill(trial, form);
            Transaction read = SmsTransactionHandler.readWith(trial, sample, "");
            if (read == null) {
                result.append("This pattern does not match the sample message.");
            } else {
                result.append(describe(read));
                if (read.isExcludedFromSummary()) {
                    result.append("\nLeft out of totals");
                }
            }
        }

        // Another pattern reading the same message is the duplicate that matters, however it is written
        if (overlap != null && sameRegex == null) {
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append("Already read by ").append(overlap.pattern.getName()).append(": ")
                    .append(describe(overlap.reading)).append('\n')
                    .append(overlap.winsOverThis
                            ? "It comes first, so this pattern will not be used for this message. Reorder to change that."
                            : "A new pattern goes first, so yours will be used instead.");
        }

        form.tvResult.setVisibility(result.length() == 0 ? View.GONE : View.VISIBLE);
        form.tvResult.setText(result);
    }

    private static String describe(Transaction read) {
        return ("CREDIT".equals(read.getTransactionType()) ? "Money in " : "Money out ")
                + BigDecimal.valueOf(read.getAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " · " + (read.getReceiverName() == null ? "no merchant" : read.getReceiverName());
    }

    private static void fill(MessagePattern pattern, DialogPatternFormBinding form) {
        pattern.setName(text(form.etName.getText()));
        pattern.setPattern(text(form.etPattern.getText()));
        pattern.setTransactionType(form.typeToggle.getCheckedButtonId() == R.id.typeCredit ? "CREDIT" : "DEBIT");
        String counterparty = text(form.etCounterparty.getText());
        pattern.setCounterparty(counterparty.isEmpty() ? null : counterparty);
        pattern.setExcludeFromSummary(form.switchExclude.isChecked());
        pattern.setActive(form.switchActive.isChecked());
    }

    /** @return what is wrong with the expression, or null if it can be used */
    @Nullable
    static String validate(String regex) {
        if (regex.isEmpty()) {
            return "Enter a pattern";
        }
        try {
            Pattern compiled = Pattern.compile(regex);
            if (!regex.contains("(?<amount>") && compiled.matcher("").groupCount() == 0) {
                return "Capture the amount: (?<amount>…)";
            }
            return null;
        } catch (PatternSyntaxException e) {
            return "Not a valid pattern: " + e.getDescription();
        }
    }

    private static String text(@Nullable Editable editable) {
        return editable == null ? "" : editable.toString().trim();
    }

    @Nullable
    private String clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) {
            return null;
        }
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        CharSequence text = item == null ? null : item.coerceToText(requireContext());
        return text == null || text.toString().trim().isEmpty() ? null : text.toString();
    }

    private void confirmDelete(MessagePattern pattern) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete " + pattern.getName() + "?")
                .setMessage(pattern.isBuiltin()
                        ? "Messages in this format will no longer be recorded. Turning it off keeps it for later."
                        : "Messages in this format will no longer be recorded.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.delete(pattern);
                    Ui.snackbar(requireActivity(), pattern.getName() + " deleted");
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        recheckOpenForm = null;
    }
}
