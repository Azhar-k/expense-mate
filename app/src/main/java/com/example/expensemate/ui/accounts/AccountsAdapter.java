package com.example.expensemate.ui.accounts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.Account;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AccountsAdapter extends ListAdapter<Account, AccountsAdapter.AccountViewHolder> {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private OnAccountClickListener listener;
    private Account defaultAccount;

    public AccountsAdapter(OnAccountClickListener listener) {
        super(new DiffUtil.ItemCallback<Account>() {
            @Override
            public boolean areItemsTheSame(@NonNull Account oldItem, @NonNull Account newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Account oldItem, @NonNull Account newItem) {
                boolean expiryDateEqual = (oldItem.getExpiryDate() == null && newItem.getExpiryDate() == null) ||
                        (oldItem.getExpiryDate() != null && newItem.getExpiryDate() != null &&
                         oldItem.getExpiryDate().equals(newItem.getExpiryDate()));

                return oldItem.getName().equals(newItem.getName()) &&
                       oldItem.getAccountNumber().equals(newItem.getAccountNumber()) &&
                       oldItem.getBank().equals(newItem.getBank()) &&
                       expiryDateEqual &&
                       oldItem.getDescription().equals(newItem.getDescription()) &&
                       oldItem.isDefault() == newItem.isDefault();
            }
        });
        this.listener = listener;
    }

    public void setDefaultAccount(Account account) {
        this.defaultAccount = account;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        Account account = getItem(position);
        holder.bind(account);
    }

    class AccountViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView accountNumberTextView;
        private final TextView bankTextView;
        private final TextView expiryDateTextView;
        private final TextView descriptionTextView;
        private final TextView defaultAccountIndicator;
        private final ImageButton editButton;
        private final ImageButton deleteButton;
        private final ImageButton setDefaultButton;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.account_name);
            accountNumberTextView = itemView.findViewById(R.id.account_number);
            bankTextView = itemView.findViewById(R.id.account_bank);
            expiryDateTextView = itemView.findViewById(R.id.account_expiry);
            descriptionTextView = itemView.findViewById(R.id.account_description);
            defaultAccountIndicator = itemView.findViewById(R.id.default_account_indicator);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
            setDefaultButton = itemView.findViewById(R.id.btn_set_default);

            editButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEditClick(getItem(position));
                }
            });

            deleteButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteClick(getItem(position));
                }
            });

            setDefaultButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSetDefaultClick(getItem(position));
                }
            });
        }

        public void bind(Account account) {
            nameTextView.setText(account.getName());
            
            if (account.isDefault()) {
                defaultAccountIndicator.setVisibility(View.VISIBLE);
                setDefaultButton.setImageResource(R.drawable.ic_star);
                setDefaultButton.setEnabled(false);
            } else {
                defaultAccountIndicator.setVisibility(View.GONE);
                setDefaultButton.setImageResource(R.drawable.ic_star_border);
                setDefaultButton.setEnabled(true);
            }
            
            if (account.getAccountNumber() != null && !account.getAccountNumber().isEmpty()) {
                accountNumberTextView.setVisibility(View.VISIBLE);
                accountNumberTextView.setText(account.getAccountNumber());
            } else {
                accountNumberTextView.setVisibility(View.GONE);
            }

            if (account.getBank() != null && !account.getBank().isEmpty()) {
                bankTextView.setVisibility(View.VISIBLE);
                bankTextView.setText(account.getBank());
            } else {
                bankTextView.setVisibility(View.GONE);
            }

            if (account.getExpiryDate() != null) {
                expiryDateTextView.setVisibility(View.VISIBLE);
                expiryDateTextView.setText("Expires: " + dateFormat.format(account.getExpiryDate()));
            } else {
                expiryDateTextView.setVisibility(View.GONE);
            }

            if (account.getDescription() != null && !account.getDescription().isEmpty()) {
                descriptionTextView.setVisibility(View.VISIBLE);
                descriptionTextView.setText(account.getDescription());
            } else {
                descriptionTextView.setVisibility(View.GONE);
            }
        }
    }

    public interface OnAccountClickListener {
        void onEditClick(Account account);
        void onDeleteClick(Account account);
        void onSetDefaultClick(Account account);
    }
} 