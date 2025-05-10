package com.example.expensemate.ui.recurringpayment;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.R;
import com.example.expensemate.data.RecurringPayment;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RecurringPaymentsAdapter extends ListAdapter<RecurringPayment, RecurringPaymentsAdapter.PaymentViewHolder> {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private OnPaymentClickListener listener;
    private RecurringPaymentsFragment fragment;

    public RecurringPaymentsAdapter() {
        super(new DiffUtil.ItemCallback<RecurringPayment>() {
            @Override
            public boolean areItemsTheSame(@NonNull RecurringPayment oldItem, @NonNull RecurringPayment newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull RecurringPayment oldItem, @NonNull RecurringPayment newItem) {
                return oldItem.getName().equals(newItem.getName()) &&
                       oldItem.getAmount() == newItem.getAmount() &&
                       oldItem.getDueDay() == newItem.getDueDay() &&
                       oldItem.getExpiryDate().equals(newItem.getExpiryDate()) &&
                       oldItem.isCompleted() == newItem.isCompleted();
            }
        });
    }

    @NonNull
    @Override
    public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recurring_payment, parent, false);
        return new PaymentViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
        RecurringPayment payment = getItem(position);
        holder.bind(payment);
    }

    public void setOnPaymentClickListener(OnPaymentClickListener listener) {
        this.listener = listener;
    }

    public void setFragment(RecurringPaymentsFragment fragment) {
        this.fragment = fragment;
    }


    public boolean areAllCompleted() {
        for (RecurringPayment payment : getCurrentList()) {
            if (!payment.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    class PaymentViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView amountTextView;
        private final TextView dueDateTextView;
        private final TextView expiryDateTextView;
        private final TextView expiredNoteTextView;
        private final CheckBox completedCheckBox;
        private final ImageButton editButton;
        private final ImageButton deleteButton;
        private final View container;

        public PaymentViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.payment_name);
            amountTextView = itemView.findViewById(R.id.payment_amount);
            dueDateTextView = itemView.findViewById(R.id.payment_due_date);
            expiryDateTextView = itemView.findViewById(R.id.payment_expiry_date);
            expiredNoteTextView = itemView.findViewById(R.id.tv_expired_note);
            completedCheckBox = itemView.findViewById(R.id.payment_completed);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
            container = itemView.findViewById(R.id.payment_container);

            editButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPaymentClick(getItem(position));
                }
            });

            deleteButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && fragment != null) {
                    fragment.showDeleteConfirmationDialog(getItem(position));
                }
            });

            completedCheckBox.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPaymentStatusChanged(getItem(position), completedCheckBox.isChecked());
                }
            });
        }

        public void bind(RecurringPayment payment) {
            nameTextView.setText(payment.getName());
            amountTextView.setText(String.format(Locale.getDefault(), "₹%.2f", payment.getAmount()));
            dueDateTextView.setText(String.format(Locale.getDefault(), "Day %d of every month", payment.getDueDay()));
            expiryDateTextView.setText(dateFormat.format(payment.getExpiryDate()));
            completedCheckBox.setChecked(payment.isCompleted());

            // Check if payment is expired
            Calendar calendar = Calendar.getInstance();
            Date today = calendar.getTime();
            boolean isExpired = payment.getExpiryDate().before(today);
            
            if (isExpired) {
                container.setBackgroundColor(Color.parseColor("#FFFDE7")); // Light yellow
                expiryDateTextView.setTextColor(Color.RED);
                expiredNoteTextView.setVisibility(View.VISIBLE);
                // Disable edit and completion for expired payments
                editButton.setEnabled(false);
                editButton.setAlpha(0.5f);
                completedCheckBox.setEnabled(false);
            } else {
                container.setBackgroundColor(Color.WHITE);
                expiryDateTextView.setTextColor(Color.BLACK);
                expiredNoteTextView.setVisibility(View.GONE);
                editButton.setEnabled(true);
                editButton.setAlpha(1.0f);
                completedCheckBox.setEnabled(true);
            }
        }
    }

    public interface OnPaymentClickListener {
        void onPaymentClick(RecurringPayment payment);
        void onPaymentStatusChanged(RecurringPayment payment, boolean isCompleted);
    }
} 