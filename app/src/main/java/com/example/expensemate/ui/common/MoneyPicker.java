package com.example.expensemate.ui.common;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Fund;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A dialog to pick everything, one fund or one account, with the two kinds under their own headings.
 * Fund and account ids can be equal, so the answer always says which kind was picked.
 */
public final class MoneyPicker {
    public interface Listener {
        /** At most one is set; both null means everything. */
        void onPicked(@Nullable Long fundId, @Nullable Long accountId);
    }

    private MoneyPicker() {
    }

    public static void show(Context context, String title, String allLabel, List<Fund> funds, List<Account> accounts,
            @Nullable Long selectedFundId, @Nullable Long selectedAccountId, Listener listener) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(allLabel, false, null, null, selectedFundId == null && selectedAccountId == null));
        if (!funds.isEmpty()) {
            entries.add(Entry.header("Funds"));
            for (Fund fund : funds) {
                entries.add(new Entry(fund.getName(), false, fund.getId(), null,
                        selectedFundId != null && selectedFundId == fund.getId()));
            }
        }
        if (!accounts.isEmpty()) {
            entries.add(Entry.header("Accounts"));
            for (Account account : accounts) {
                entries.add(new Entry(account.getName(), false, null, account.getId(),
                        selectedAccountId != null && selectedAccountId == account.getId()));
            }
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setAdapter(new Adapter(context, entries), (dialog, which) -> {
                    Entry entry = entries.get(which);
                    if (!entry.header) {
                        listener.onPicked(entry.fundId, entry.accountId);
                    }
                })
                .show();
    }

    private static final class Entry {
        final String label;
        final boolean header;
        final Long fundId;
        final Long accountId;
        final boolean selected;

        Entry(String label, boolean header, Long fundId, Long accountId, boolean selected) {
            this.label = label;
            this.header = header;
            this.fundId = fundId;
            this.accountId = accountId;
            this.selected = selected;
        }

        static Entry header(String label) {
            return new Entry(label, true, null, null, false);
        }
    }

    private static final class Adapter extends BaseAdapter {
        private final Context context;
        private final List<Entry> entries;

        Adapter(Context context, List<Entry> entries) {
            this.context = context;
            this.entries = entries;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Object getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).header ? 0 : 1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        // Headings only group the choices; they cannot be picked
        @Override
        public boolean isEnabled(int position) {
            return !entries.get(position).header;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Entry entry = entries.get(position);
            TextView view = convertView instanceof TextView ? (TextView) convertView : new TextView(context);
            int side = dp(24);
            if (entry.header) {
                TextViewCompat.setTextAppearance(view, R.style.Text_ExpenseMate_Section);
                view.setPadding(side, dp(14), side, dp(4));
                view.setBackground(null);
            } else {
                TextViewCompat.setTextAppearance(view, R.style.Text_ExpenseMate_Row);
                view.setPadding(side, dp(12), side, dp(12));
                TypedValue ripple = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                view.setBackgroundResource(ripple.resourceId);
                if (entry.selected) {
                    view.setTextColor(context.getColor(R.color.primary));
                }
            }
            view.setText(entry.label);
            return view;
        }

        private int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
