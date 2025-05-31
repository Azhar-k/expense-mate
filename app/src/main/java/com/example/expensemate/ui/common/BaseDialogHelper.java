package com.example.expensemate.ui.common;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.expensemate.R;

public class BaseDialogHelper {
    private final Context context;
    private final String title;
    private final View contentView;
    private final String positiveButtonText;
    private final String negativeButtonText;
    private final OnDialogButtonClickListener listener;

    public interface OnDialogButtonClickListener {
        void onPositiveButtonClick(AlertDialog dialog);
        void onNegativeButtonClick(AlertDialog dialog);
    }

    public BaseDialogHelper(
            @NonNull Context context,
            @NonNull String title,
            @NonNull View contentView,
            @NonNull String positiveButtonText,
            @NonNull String negativeButtonText,
            @Nullable OnDialogButtonClickListener listener
    ) {
        this.context = context;
        this.title = title;
        this.contentView = contentView;
        this.positiveButtonText = positiveButtonText;
        this.negativeButtonText = negativeButtonText;
        this.listener = listener;
    }

    public AlertDialog create() {
        AlertDialog dialog = new AlertDialog.Builder(context, 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setTitle(title)
                .setView(contentView)
                .setPositiveButton(positiveButtonText, null)
                .setNegativeButton(negativeButtonText, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(context.getResources().getColor(R.color.primary));
            negativeButton.setTextColor(context.getResources().getColor(R.color.primary));
            
            if (listener != null) {
                positiveButton.setOnClickListener(v -> listener.onPositiveButtonClick(dialog));
                negativeButton.setOnClickListener(v -> listener.onNegativeButtonClick(dialog));
            }
        });

        return dialog;
    }
} 