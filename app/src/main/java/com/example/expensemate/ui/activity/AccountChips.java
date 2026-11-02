package com.example.expensemate.ui.activity;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.databinding.ItemChipPickerBinding;
import com.google.android.material.chip.Chip;

import java.util.List;

/** A labelled row of account chips that scrolls sideways, holding one choice by account id. */
final class AccountChips {
    private final ItemChipPickerBinding row;
    private final String label;
    private Long selectedId;

    AccountChips(ItemChipPickerBinding row, String label) {
        this.row = row;
        this.label = label;
        row.tvLabel.setText(label);
        row.chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            Chip chip = checkedIds.isEmpty() ? null : group.findViewById(checkedIds.get(0));
            selectedId = chip != null ? (Long) chip.getTag() : null;
            setError(null);
        });
    }

    /** Takes the accounts of one kind, keeping the current choice if it is still among them. */
    void setAccounts(List<Account> accounts) {
        Context context = new ContextThemeWrapper(row.getRoot().getContext(), R.style.ThemeOverlay_ExpenseMate_FormChips);
        Long keep = selectedId;
        row.chips.removeAllViews();
        selectedId = null;
        for (Account account : accounts) {
            Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
            chip.setId(View.generateViewId());
            chip.setTag(account.getId());
            chip.setText(account.getName());
            chip.setCheckable(true);
            row.chips.addView(chip);
        }
        select(keep);
    }

    void select(@Nullable Long accountId) {
        for (int i = 0; i < row.chips.getChildCount(); i++) {
            Chip chip = (Chip) row.chips.getChildAt(i);
            if (accountId != null && accountId.equals(chip.getTag())) {
                chip.setChecked(true);
                selectedId = accountId;
                // An account far down the order would otherwise be chosen but out of sight
                row.scroll.post(() -> row.scroll.smoothScrollTo(Math.max(0, chip.getLeft() - row.chips.getPaddingStart()), 0));
                return;
            }
        }
        row.chips.clearCheck();
        selectedId = null;
    }

    @Nullable
    Long selectedId() {
        return selectedId;
    }

    /** Shows the problem in place of the label, as the category row does. */
    void setError(@Nullable String error) {
        Context context = row.getRoot().getContext();
        row.tvLabel.setText(error != null ? error : label);
        row.tvLabel.setTextColor(context.getColor(error != null ? R.color.alert : R.color.on_surface_variant));
    }
}
