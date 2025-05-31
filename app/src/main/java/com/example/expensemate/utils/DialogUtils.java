package com.example.expensemate.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.expensemate.R;
import com.example.expensemate.databinding.DialogInputFieldsBinding;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Map;

public class DialogUtils {
    
    public interface DialogCallback {
        void onPositiveClick(Map<String, String> inputValues);
        void onNegativeClick();
    }

    public static void showInputDialog(
            @NonNull Context context,
            @NonNull String title,
            @NonNull List<InputField> inputFields,
            @NonNull DialogCallback callback
    ) {
        DialogInputFieldsBinding binding = DialogInputFieldsBinding.inflate(LayoutInflater.from(context));
        
        // Set up the dialog title
        binding.tvDialogTitle.setText(title);
        
        // Add input fields dynamically
        for (InputField field : inputFields) {
            TextInputLayout inputLayout = new TextInputLayout(context);
            inputLayout.setLayoutParams(new TextInputLayout.LayoutParams(
                    TextInputLayout.LayoutParams.MATCH_PARENT,
                    TextInputLayout.LayoutParams.WRAP_CONTENT
            ));
            inputLayout.setHint(field.getHint());
            inputLayout.setBoxBackgroundColorResource(R.color.white);
            inputLayout.setHintTextColor(context.getResources().getColorStateList(R.color.primary));
            
            if (field.isDropdown()) {
                AutoCompleteTextView autoComplete = new AutoCompleteTextView(context);
                autoComplete.setLayoutParams(new TextInputLayout.LayoutParams(
                        TextInputLayout.LayoutParams.MATCH_PARENT,
                        TextInputLayout.LayoutParams.WRAP_CONTENT
                ));
                autoComplete.setId(View.generateViewId());
                autoComplete.setInputType(field.getInputType());
                autoComplete.setTextColor(context.getResources().getColor(R.color.black));
                autoComplete.setTextSize(18);
                autoComplete.setPadding(48, 48, 48, 48);
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        field.getDropdownItems()
                );
                autoComplete.setAdapter(adapter);
                autoComplete.setOnClickListener(v -> autoComplete.showDropDown());
                
                inputLayout.addView(autoComplete);
            } else {
                TextInputEditText editText = new TextInputEditText(context);
                editText.setLayoutParams(new TextInputLayout.LayoutParams(
                        TextInputLayout.LayoutParams.MATCH_PARENT,
                        TextInputLayout.LayoutParams.WRAP_CONTENT
                ));
                editText.setId(View.generateViewId());
                editText.setInputType(field.getInputType());
                editText.setTextColor(context.getResources().getColor(R.color.black));
                editText.setTextSize(18);
                editText.setPadding(48, 48, 48, 48);
                
                inputLayout.addView(editText);
            }
            
            binding.inputContainer.addView(inputLayout);
        }
        
        AlertDialog dialog = new AlertDialog.Builder(context, 
                com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog)
                .setView(binding.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            
            positiveButton.setTextColor(context.getResources().getColor(R.color.primary));
            negativeButton.setTextColor(context.getResources().getColor(R.color.primary));
            
            positiveButton.setOnClickListener(v -> {
                Map<String, String> inputValues = new java.util.HashMap<>();
                boolean hasError = false;
                
                for (int i = 0; i < binding.inputContainer.getChildCount(); i++) {
                    TextInputLayout inputLayout = (TextInputLayout) binding.inputContainer.getChildAt(i);
                    InputField field = inputFields.get(i);
                    
                    String value;
                    if (field.isDropdown()) {
                        AutoCompleteTextView autoComplete = (AutoCompleteTextView) inputLayout.getEditText();
                        value = autoComplete.getText().toString().trim();
                    } else {
                        TextInputEditText editText = (TextInputEditText) inputLayout.getEditText();
                        value = editText.getText().toString().trim();
                    }
                    
                    if (field.isRequired() && value.isEmpty()) {
                        inputLayout.setError(field.getHint() + " is required");
                        hasError = true;
                    } else {
                        inputLayout.setError(null);
                        inputValues.put(field.getKey(), value);
                    }
                }
                
                if (!hasError) {
                    callback.onPositiveClick(inputValues);
                    dialog.dismiss();
                }
            });
            
            negativeButton.setOnClickListener(v -> {
                callback.onNegativeClick();
                dialog.dismiss();
            });
        });
        
        dialog.show();
    }
    
    public static class InputField {
        private final String key;
        private final String hint;
        private final int inputType;
        private final boolean required;
        private final boolean isDropdown;
        private final List<String> dropdownItems;
        
        public InputField(String key, String hint, int inputType, boolean required) {
            this(key, hint, inputType, required, false, null);
        }
        
        public InputField(String key, String hint, int inputType, boolean required, boolean isDropdown, List<String> dropdownItems) {
            this.key = key;
            this.hint = hint;
            this.inputType = inputType;
            this.required = required;
            this.isDropdown = isDropdown;
            this.dropdownItems = dropdownItems;
        }
        
        public String getKey() { return key; }
        public String getHint() { return hint; }
        public int getInputType() { return inputType; }
        public boolean isRequired() { return required; }
        public boolean isDropdown() { return isDropdown; }
        public List<String> getDropdownItems() { return dropdownItems; }
    }
} 