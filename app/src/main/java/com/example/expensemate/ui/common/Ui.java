package com.example.expensemate.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.example.expensemate.MainActivity;
import com.example.expensemate.R;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.viewmodel.TransactionViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Small view helpers shared by the screens. */
public final class Ui {
    private Ui() {
    }

    public static <T> void observeOnce(LiveData<T> liveData, LifecycleOwner owner, Observer<T> observer) {
        liveData.observe(owner, new Observer<T>() {
            @Override
            public void onChanged(T value) {
                liveData.removeObserver(this);
                observer.onChanged(value);
            }
        });
    }

    public static void setDropdownItems(AutoCompleteTextView view, List<String> items) {
        view.setAdapter(new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, items));
    }

    public static void styleTag(TextView tag, String text, @ColorRes int background, @ColorRes int foreground) {
        Context context = tag.getContext();
        tag.setText(text);
        tag.setTextColor(ContextCompat.getColor(context, foreground));
        tag.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, background)));
        tag.setVisibility(View.VISIBLE);
    }

    public static void styleIconTile(ImageView icon, int drawable, @ColorRes int background, @ColorRes int foreground) {
        Context context = icon.getContext();
        icon.setImageResource(drawable);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, foreground)));
        icon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, background)));
    }

    public static void tintImage(ImageView image, int drawable, @ColorRes int color) {
        image.setImageResource(drawable);
        image.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(image.getContext(), color)));
    }

    public static void addKeyValue(@NonNull ViewGroup parent, String key, String value) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_key_value, parent, false);
        ((TextView) row.findViewById(R.id.tvKey)).setText(key);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        parent.addView(row);
    }

    public static void snackbar(FragmentActivity activity, String message) {
        Snackbar snackbar = Snackbar.make(activity.findViewById(R.id.root), message, Snackbar.LENGTH_SHORT);
        anchor(activity, snackbar);
        snackbar.show();
    }

    /** Deletes straight away and offers Undo, which puts the same rows back. */
    public static void deleteWithUndo(FragmentActivity activity, TransactionViewModel viewModel,
            Transaction transaction) {
        viewModel.delete(transaction, removed -> {
            String message = transaction.isTransfer() ? "Transfer deleted" : "Transaction deleted";
            Snackbar snackbar = Snackbar.make(activity.findViewById(R.id.root), message, Snackbar.LENGTH_LONG)
                    .setAction("Undo", v -> viewModel.restore(removed));
            anchor(activity, snackbar);
            snackbar.show();
        });
    }

    private static void anchor(FragmentActivity activity, Snackbar snackbar) {
        if (activity instanceof MainActivity) {
            snackbar.setAnchorView(((MainActivity) activity).snackbarAnchor());
        }
    }
}
