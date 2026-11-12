package com.example.expensemate.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import com.example.expensemate.data.Transaction;
import com.example.expensemate.databinding.ItemActivityRowBinding;
import com.example.expensemate.databinding.ItemDayHeaderBinding;
import com.example.expensemate.util.Currencies;
import com.example.expensemate.util.Money;
import com.example.expensemate.viewmodel.ActivityRow;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact transaction rows grouped by day, used by Activity and by account details. */
public class ActivityAdapter extends ListAdapter<ActivityRow, RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    public interface OnRowClickListener {
        void onRowClick(ActivityRow row);
    }

    private final OnRowClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private Map<Long, String> accountNames = new HashMap<>();
    private String mainCurrency;

    public ActivityAdapter(OnRowClickListener listener) {
        super(new DiffUtil.ItemCallback<ActivityRow>() {
            @Override
            public boolean areItemsTheSame(@NonNull ActivityRow oldItem, @NonNull ActivityRow newItem) {
                return oldItem.sameItem(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ActivityRow oldItem, @NonNull ActivityRow newItem) {
                return oldItem.sameContent(newItem);
            }
        });
        this.listener = listener;
    }

    public void setAccounts(List<Account> accounts) {
        Map<Long, String> names = new HashMap<>();
        for (Account account : accounts) {
            names.put(account.getId(), account.getName());
        }
        accountNames = names;
        mainCurrency = Currencies.main(accounts);
        notifyDataSetChanged();
    }

    /**
     * Shows new rows. A row's corners and divider depend on its neighbours, which can change
     * without the row itself changing, so every row is drawn again once the list has settled.
     */
    public void submit(List<ActivityRow> rows, boolean scrollToTop, RecyclerView recyclerView) {
        submitList(rows, () -> {
            notifyItemRangeChanged(0, getItemCount());
            if (scrollToTop) {
                recyclerView.scrollToPosition(0);
            }
        });
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        // Redrawing every row must not make the whole list flash
        if (recyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) recyclerView.getItemAnimator())
                    .setSupportsChangeAnimations(false);
        }
    }

    public ActivityRow rowAt(int position) {
        return getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeader() ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false));
        }
        return new RowHolder(ItemActivityRowBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ActivityRow row = getItem(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row, mainCurrency);
        } else {
            boolean first = position == 0 || getItem(position - 1).isHeader();
            boolean last = position == getItemCount() - 1 || getItem(position + 1).isHeader();
            ((RowHolder) holder).bind(row, first, last);
        }
    }

    private static class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemDayHeaderBinding binding;

        HeaderHolder(ItemDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ActivityRow row, String mainCurrency) {
            binding.tvDay.setText(row.dayLabel);
            binding.tvNet.setText(row.dayNet.signed(mainCurrency));
        }
    }

    private class RowHolder extends RecyclerView.ViewHolder {
        private final ItemActivityRowBinding binding;

        RowHolder(ItemActivityRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRowClick(getItem(position));
                }
            });
        }

        void bind(ActivityRow row, boolean first, boolean last) {
            Context context = itemView.getContext();
            itemView.setBackgroundResource(first && last ? R.drawable.bg_row_single
                    : first ? R.drawable.bg_row_top : last ? R.drawable.bg_row_bottom : R.drawable.bg_row_middle);
            binding.divider.setVisibility(last ? View.GONE : View.VISIBLE);
            binding.tvTag.setVisibility(View.GONE);

            Transaction transaction = row.transaction;
            String time = timeFormat.format(transaction.getDate());
            int amountColor = R.color.on_surface;

            binding.tvTime.setText(time);
            binding.tvSubtitle.setVisibility(row.isTransfer() ? View.VISIBLE : View.GONE);
            binding.pills.setVisibility(row.isTransfer() ? View.GONE : View.VISIBLE);

            if (row.isTransfer()) {
                Ui.styleIconTile(binding.ivIcon, R.drawable.ic_swap, R.color.transfer_container, R.color.transfer);
                binding.tvTitle.setText(transferTitle(row));
                binding.tvSubtitle.setText("Transfer");
                binding.tvAmount.setText(TransactionText.transferAmount(transaction, row.debitLeg, row.creditLeg));
                amountColor = R.color.transfer;
            } else {
                binding.tvTitle.setText(TransactionText.title(transaction));
                boolean credit = "CREDIT".equals(transaction.getTransactionType());
                if (transaction.isNeedsReview()) {
                    Ui.styleIconTile(binding.ivIcon, R.drawable.ic_tag, R.color.warning_container, R.color.warning);
                    // The category is only a placeholder until someone picks one, so ask for it instead
                    binding.pillCategory.setText("Needs category");
                    binding.pillCategory.setBackgroundResource(R.drawable.bg_pill_warning);
                    binding.pillCategory.setTextColor(ContextCompat.getColor(context, R.color.warning));
                } else {
                    if (credit) {
                        Ui.styleIconTile(binding.ivIcon, TransactionText.categoryIcon(transaction.getCategory()),
                                R.color.primary_container, R.color.primary);
                    } else {
                        Ui.styleIconTile(binding.ivIcon, TransactionText.categoryIcon(transaction.getCategory()),
                                R.color.surface_variant, R.color.on_surface);
                    }
                    binding.pillCategory.setText(transaction.getCategory());
                    binding.pillCategory.setBackgroundResource(R.drawable.bg_pill_category);
                    binding.pillCategory.setTextColor(ContextCompat.getColor(context, R.color.on_primary_container));
                }
                binding.pillAccount.setText(TransactionText.accountName(accountNames, transaction.getAccountId()));
                Long realId = transaction.getRealAccountId();
                binding.pillReal.setVisibility(realId != null ? View.VISIBLE : View.GONE);
                binding.pillReal.setText(TransactionText.accountName(accountNames, realId));
                binding.tvAmount.setText(credit ? Money.credit(transaction.getAmount(), transaction.getCurrency())
                        : Money.debit(transaction.getAmount(), transaction.getCurrency()));
                if (credit) {
                    amountColor = R.color.credit;
                }
                if (transaction.isExcludedFromSummary()) {
                    amountColor = R.color.on_surface_variant;
                    binding.tvTag.setVisibility(View.VISIBLE);
                }
            }
            binding.tvAmount.setTextColor(ContextCompat.getColor(context, amountColor));
        }

        private String transferTitle(ActivityRow row) {
            return TransactionText.transferRoute(accountNames, row.debitLeg, row.creditLeg);
        }
    }
}
