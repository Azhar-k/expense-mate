package com.example.expensemate.ui.categories;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensemate.data.Category;
import com.example.expensemate.databinding.ItemCategoryBinding;

public class CategoryAdapter extends ListAdapter<Category, CategoryAdapter.CategoryViewHolder> {
    private final OnCategoryClickListener listener;
    private final CategoriesFragment fragment;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(@NonNull DiffUtil.ItemCallback<Category> diffCallback, 
                         OnCategoryClickListener listener,
                         CategoriesFragment fragment) {
        super(diffCallback);
        this.listener = listener;
        this.fragment = fragment;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryBinding binding = ItemCategoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = getItem(position);
        holder.bind(category);
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryBinding binding;

        public CategoryViewHolder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Set up click listeners for edit and delete
            binding.btnEdit.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onCategoryClick(getItem(position));
                }
            });

            binding.btnDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    fragment.showDeleteConfirmationDialog(getItem(position));
                }
            });
        }

        public void bind(Category category) {
            binding.tvCategoryName.setText(category.getName());
            binding.tvCategoryType.setText(String.format("Type: %s", category.getType()));
        }
    }

    static class CategoryDiffCallback extends DiffUtil.ItemCallback<Category> {
        @Override
        public boolean areItemsTheSame(@NonNull Category oldItem, @NonNull Category newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Category oldItem, @NonNull Category newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                   oldItem.getType().equals(newItem.getType());
        }
    }
} 