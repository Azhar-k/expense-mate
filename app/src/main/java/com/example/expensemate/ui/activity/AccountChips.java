package com.example.expensemate.ui.activity;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Fund;
import com.example.expensemate.databinding.ItemChipPickerBinding;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * A labelled row of chips that scrolls sideways, holding one choice by id. One row holds either funds
 * or accounts, never both, since their ids can be equal.
 */
final class AccountChips {
    private final ItemChipPickerBinding row;
    private final String label;
    private Long selectedId;
    private Runnable onChange = () -> { };

    AccountChips(ItemChipPickerBinding row, String label) {
        this.row = row;
        this.label = label;
        row.tvLabel.setText(label);
        row.chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            Chip chip = checkedIds.isEmpty() ? null : group.findViewById(checkedIds.get(0));
            selectedId = chip != null ? (Long) chip.getTag() : null;
            setError(null);
            onChange.run();
        });
    }

    /** Takes the funds, keeping the current choice if it is still among them. */
    void setFunds(List<Fund> funds) {
        Context context = themed();
        Long keep = selectedId;
        row.chips.removeAllViews();
        selectedId = null;
        for (Fund fund : funds) {
            row.chips.addView(chip(context, fund.getId(), fund.getName()));
        }
        select(keep);
    }

    /** Takes the accounts, keeping the current choice if it is still among them. */
    void setAccounts(List<Account> accounts) {
        Context context = themed();
        Long keep = selectedId;
        row.chips.removeAllViews();
        selectedId = null;
        for (Account account : accounts) {
            row.chips.addView(chip(context, account.getId(), account.getName()));
        }
        select(keep);
    }

    private Context themed() {
        return new ContextThemeWrapper(row.getRoot().getContext(), R.style.ThemeOverlay_ExpenseMate_FormChips);
    }

    private static Chip chip(Context context, long id, String name) {
        Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
        chip.setId(View.generateViewId());
        chip.setTag(id);
        chip.setText(name);
        chip.setCheckable(true);
        return chip;
    }

    void select(@Nullable Long id) {
        for (int i = 0; i < row.chips.getChildCount(); i++) {
            Chip chip = (Chip) row.chips.getChildAt(i);
            if (id != null && id.equals(chip.getTag())) {
                chip.setChecked(true);
                selectedId = id;
                // A choice far down the order would otherwise be chosen but out of sight
                row.scroll.post(() -> row.scroll.smoothScrollTo(Math.max(0, chip.getLeft() - row.chips.getPaddingStart()), 0));
                return;
            }
        }
        row.chips.clearCheck();
        selectedId = null;
    }

    /** Runs whenever the choice changes, whether tapped or set from code. */
    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
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
