package com.example.expensemate.ui.common;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.Nullable;

import com.example.expensemate.util.Currencies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A currency dropdown that can also be typed in: "aed" or "dirham" both find AED. The main currency
 * and those already in use come first.
 */
public final class CurrencyField {
    private CurrencyField() {
    }

    public static void setUp(AutoCompleteTextView view, String selected, String main, Collection<String> inUse) {
        List<String> labels = new ArrayList<>();
        for (String code : Currencies.pickerCodes(main, inUse)) {
            labels.add(Currencies.label(code));
        }
        view.setAdapter(new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, labels));
        view.setThreshold(1);
        String code = Currencies.isValid(selected) ? selected : main;
        view.setText(Currencies.label(code), false);
        // Typing something that is not a currency and moving on puts the starting choice back
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && read(view) == null) {
                view.setText(Currencies.label(code), false);
            }
        });
    }

    /** The code picked or typed, or null if the field does not name a currency. */
    @Nullable
    public static String read(AutoCompleteTextView view) {
        return Currencies.fromLabel(view.getText().toString());
    }
}
