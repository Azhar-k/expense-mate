package com.example.expensemate.ui.regex;

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
import com.example.expensemate.data.RegexPattern;

public class RegexPatternAdapter extends ListAdapter<RegexPattern, RegexPatternAdapter.ViewHolder> {

    private final OnPatternActionListener listener;

    public interface OnPatternActionListener {
        void onEdit(RegexPattern pattern);

        void onDelete(RegexPattern pattern);
    }

    public RegexPatternAdapter(OnPatternActionListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<RegexPattern> DIFF_CALLBACK = new DiffUtil.ItemCallback<RegexPattern>() {
        @Override
        public boolean areItemsTheSame(@NonNull RegexPattern oldItem, @NonNull RegexPattern newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull RegexPattern oldItem, @NonNull RegexPattern newItem) {
            return oldItem.name.equals(newItem.name) &&
                    oldItem.regex.equals(newItem.regex) &&
                    oldItem.type.equals(newItem.type) &&
                    oldItem.isSystem == newItem.isSystem;
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_regex_pattern, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RegexPattern pattern = getItem(position);
        holder.bind(pattern, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvType;
        private final TextView tvRegex;
        private final TextView tvSystemBadge;
        private final ImageButton btnEdit;
        private final ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPatternName);
            tvType = itemView.findViewById(R.id.tvPatternType);
            tvRegex = itemView.findViewById(R.id.tvRegex);
            tvSystemBadge = itemView.findViewById(R.id.tvSystemBadge);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(RegexPattern pattern, OnPatternActionListener listener) {
            tvName.setText(pattern.name);
            tvType.setText(pattern.type);
            tvRegex.setText(pattern.regex);

            if (pattern.isSystem) {
                tvSystemBadge.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.GONE);
                // Prompt said "Do not allow the system regex to be edited or deleted"
                btnEdit.setVisibility(View.GONE);
            } else {
                tvSystemBadge.setVisibility(View.GONE);
                btnDelete.setVisibility(View.VISIBLE);
                btnEdit.setVisibility(View.VISIBLE);
            }

            btnEdit.setOnClickListener(v -> listener.onEdit(pattern));
            btnDelete.setOnClickListener(v -> listener.onDelete(pattern));
        }
    }
}
