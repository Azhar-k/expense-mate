package com.example.expensemate.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.example.expensemate.databinding.SheetFormBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Wraps one of the small forms in a bottom sheet with a title and a save button. */
public final class FormSheet {
    private final BottomSheetDialog dialog;
    private final SheetFormBinding binding;

    private FormSheet(Context context, String title, View form) {
        binding = SheetFormBinding.inflate(LayoutInflater.from(context));
        binding.tvTitle.setText(title);
        binding.content.addView(form);
        dialog = new BottomSheetDialog(context);
        dialog.setContentView(binding.getRoot());
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    public static FormSheet create(Context context, String title, View form) {
        return new FormSheet(context, title, form);
    }

    /** Save runs the check itself and calls dismiss once the form is good. */
    public FormSheet onSave(Runnable save) {
        binding.btnSave.setOnClickListener(v -> save.run());
        return this;
    }

    public FormSheet onDelete(Runnable delete) {
        binding.btnDelete.setVisibility(View.VISIBLE);
        binding.btnDelete.setOnClickListener(v -> {
            dialog.dismiss();
            delete.run();
        });
        return this;
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }
}
