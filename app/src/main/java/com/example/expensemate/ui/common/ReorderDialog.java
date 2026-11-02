package com.example.expensemate.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.databinding.ItemReorderBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** A list the user drags into order, for the lists and pickers that show accounts or categories. */
public final class ReorderDialog {
    private ReorderDialog() {
    }

    /** Calls onSave with the original positions of the names, in their new order. */
    public static void show(Context context, String title, List<String> names, Consumer<List<Integer>> onSave) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            order.add(i);
        }

        // A long list scrolls inside the dialog, so its buttons stay on screen
        int maxHeight = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.5f);
        RecyclerView list = new RecyclerView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        list.setLayoutManager(new LinearLayoutManager(context));
        ItemTouchHelper[] helper = new ItemTouchHelper[1];
        RecyclerView.Adapter<Row> adapter = new RecyclerView.Adapter<Row>() {
            @NonNull
            @Override
            public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Row(ItemReorderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void onBindViewHolder(@NonNull Row row, int position) {
                row.binding.tvName.setText(names.get(order.get(position)));
                // The handle moves a row at once; holding anywhere else on it works too
                row.binding.ivHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        helper[0].startDrag(row);
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return order.size();
            }
        };
        list.setAdapter(adapter);
        helper[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                    @NonNull RecyclerView.ViewHolder to) {
                Collections.swap(order, from.getAdapterPosition(), to.getAdapterPosition());
                adapter.notifyItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        helper[0].attachToRecyclerView(list);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage("Drag to move. The top ones come first wherever you pick from this list.")
                .setView(list)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> onSave.accept(order))
                .show();
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final ItemReorderBinding binding;

        Row(ItemReorderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
