package com.example.expensemate.ui.summary;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.data.CategorySum;
import com.example.expensemate.databinding.ItemCategorySumBinding;

public class CategorySumAdapter extends ListAdapter<CategorySum, CategorySumAdapter.CategorySumViewHolder> {

    public CategorySumAdapter() {
        super(new CategorySumDiffCallback());
    }

    @NonNull
    @Override
    public CategorySumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategorySumBinding binding = ItemCategorySumBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CategorySumViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategorySumViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class CategorySumViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategorySumBinding binding;

        public CategorySumViewHolder(ItemCategorySumBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CategorySum categorySum) {
            binding.tvCategory.setText(categorySum.getCategory());
            binding.tvAmount.setText(String.format("₹%.2f", categorySum.getTotal()));
        }
    }

    private static class CategorySumDiffCallback extends DiffUtil.ItemCallback<CategorySum> {
        @Override
        public boolean areItemsTheSame(@NonNull CategorySum oldItem, @NonNull CategorySum newItem) {
            return oldItem.getCategory().equals(newItem.getCategory());
        }

        @Override
        public boolean areContentsTheSame(@NonNull CategorySum oldItem, @NonNull CategorySum newItem) {
            return oldItem.getCategory().equals(newItem.getCategory()) &&
                   oldItem.getTotal() == newItem.getTotal();
        }
    }
} 