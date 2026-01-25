package com.example.expensemate.ui.regex;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.R;
import com.example.expensemate.data.RegexPattern;
import com.example.expensemate.viewmodel.RegexPatternViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegexPatternsFragment extends Fragment implements RegexPatternAdapter.OnPatternActionListener {

    private RegexPatternViewModel viewModel;
    private RegexPatternAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_regex_patterns, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(RegexPatternViewModel.class);
        adapter = new RegexPatternAdapter(this);

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showEditDialog(null));

        viewModel.getAllPatterns().observe(getViewLifecycleOwner(), patterns -> {
            adapter.submitList(patterns);
        });
    }

    @Override
    public void onEdit(RegexPattern pattern) {
        showEditDialog(pattern);
    }

    @Override
    public void onDelete(RegexPattern pattern) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Pattern")
                .setMessage("Are you sure you want to delete '" + pattern.name + "'?")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.delete(pattern))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(@Nullable RegexPattern pattern) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_regex_pattern, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(view)
                .setCancelable(false)
                .create();

        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputEditText etRegex = view.findViewById(R.id.etRegex);
        RadioGroup rgType = view.findViewById(R.id.rgType);
        TextInputEditText etAmountIndex = view.findViewById(R.id.etAmountIndex);
        TextInputEditText etMerchantIndex = view.findViewById(R.id.etMerchantIndex);
        TextInputEditText etDefaultSender = view.findViewById(R.id.etDefaultSender);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        if (pattern != null) {
            etName.setText(pattern.name);
            etRegex.setText(pattern.regex);
            if ("CREDIT".equals(pattern.type)) {
                rgType.check(R.id.rbCredit);
            } else {
                rgType.check(R.id.rbDebit);
            }
            etAmountIndex.setText(String.valueOf(pattern.amountGroupIndex));
            etMerchantIndex.setText(String.valueOf(pattern.merchantGroupIndex));
            etDefaultSender.setText(pattern.defaultSender);
        } else {
            rgType.check(R.id.rbDebit);
            etAmountIndex.setText("1");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            String regex = etRegex.getText() != null ? etRegex.getText().toString().trim() : "";
            String type = rgType.getCheckedRadioButtonId() == R.id.rbCredit ? "CREDIT" : "DEBIT";
            String amountIndexStr = etAmountIndex.getText() != null ? etAmountIndex.getText().toString().trim() : "";
            String merchantIndexStr = etMerchantIndex.getText() != null ? etMerchantIndex.getText().toString().trim()
                    : "";
            String defaultSender = etDefaultSender.getText() != null ? etDefaultSender.getText().toString().trim() : "";

            if (name.isEmpty() || regex.isEmpty() || amountIndexStr.isEmpty()) {
                Toast.makeText(getContext(), "Please fill required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int amountIndex;
            int merchantIndex = -1;
            try {
                amountIndex = Integer.parseInt(amountIndexStr);
                if (!merchantIndexStr.isEmpty()) {
                    merchantIndex = Integer.parseInt(merchantIndexStr);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
                return;
            }

            if (defaultSender.isEmpty())
                defaultSender = null;

            if (pattern == null) {
                // Insert
                RegexPattern newPattern = new RegexPattern(name, regex, type, amountIndex, merchantIndex, false,
                        defaultSender);
                viewModel.insert(newPattern);
            } else {
                // Update
                pattern.name = name;
                pattern.regex = regex;
                pattern.type = type;
                pattern.amountGroupIndex = amountIndex;
                pattern.merchantGroupIndex = merchantIndex;
                pattern.defaultSender = defaultSender;
                viewModel.update(pattern);
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}
