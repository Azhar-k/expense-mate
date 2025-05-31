package com.example.expensemate.ui.common;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.expensemate.R;
import com.google.android.material.button.MaterialButton;

public class BaseDialogHelper {
    private final Context context;
    private final String title;
    private final View contentView;
    private final String positiveButtonText;
    private final String negativeButtonText;
    private final OnDialogButtonClickListener listener;
    private String message;

    public interface OnDialogButtonClickListener {
        void onPositiveButtonClick(AlertDialog dialog);
        void onNegativeButtonClick(AlertDialog dialog);
    }

    public BaseDialogHelper(
            @NonNull Context context,
            @NonNull String title,
            @Nullable View contentView,
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

    public void setMessage(String message) {
        this.message = message;
    }

    public AlertDialog create() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog);

        if (message != null) {
            // Use confirmation dialog layout
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirmation, null);
            TextView titleView = dialogView.findViewById(R.id.tvDialogTitle);
            TextView messageView = dialogView.findViewById(R.id.tvDialogMessage);
            
            titleView.setText(title);
            messageView.setText(message);
            
            builder.setView(dialogView);
        } else if (contentView != null) {
            // Use provided content view
            builder.setView(contentView);
        }

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.setOnShowListener(dialogInterface -> {
            View view;
            if (message != null) {
                view = dialog.findViewById(R.id.tvDialogTitle).getRootView();
            } else {
                view = contentView;
            }

            if (view != null) {
                MaterialButton positiveButton = view.findViewById(R.id.btnPositive);
                MaterialButton negativeButton = view.findViewById(R.id.btnNegative);
                
                if (positiveButton != null && negativeButton != null) {
                    positiveButton.setText(positiveButtonText);
                    negativeButton.setText(negativeButtonText);
                    
                    positiveButton.setTextColor(context.getResources().getColor(R.color.primary));
                    negativeButton.setTextColor(context.getResources().getColor(R.color.primary));
                    
                    if (listener != null) {
                        positiveButton.setOnClickListener(v -> listener.onPositiveButtonClick(dialog));
                        negativeButton.setOnClickListener(v -> listener.onNegativeButtonClick(dialog));
                    }
                }
            }
        });

        return dialog;
    }
} 