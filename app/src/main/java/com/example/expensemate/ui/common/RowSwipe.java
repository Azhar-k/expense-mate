package com.example.expensemate.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensemate.R;
import com.example.expensemate.viewmodel.ActivityRow;

/** Swipe left to delete and right to open the details sheet, where the category is one tap away. */
public final class RowSwipe {
    public interface Actions {
        void onDelete(ActivityRow row);

        void onOpen(ActivityRow row);
    }

    private RowSwipe() {
    }

    public static void attach(RecyclerView recyclerView, ActivityAdapter adapter, Actions actions) {
        Context context = recyclerView.getContext();
        Paint deletePaint = new Paint();
        deletePaint.setColor(ContextCompat.getColor(context, R.color.alert_container));
        Paint openPaint = new Paint();
        openPaint.setColor(ContextCompat.getColor(context, R.color.primary_container));
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setTextSize(14 * context.getResources().getDisplayMetrics().scaledDensity);
        float margin = 20 * context.getResources().getDisplayMetrics().density;

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                int position = holder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || adapter.rowAt(position).isHeader()) {
                    return 0;
                }
                return super.getSwipeDirs(rv, holder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                    @NonNull RecyclerView.ViewHolder to) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getAdapterPosition();
                ActivityRow row = adapter.rowAt(position);
                if (direction == ItemTouchHelper.LEFT) {
                    actions.onDelete(row);
                } else {
                    adapter.notifyItemChanged(position);
                    actions.onOpen(row);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView rv,
                    @NonNull RecyclerView.ViewHolder holder, float dX, float dY, int actionState,
                    boolean isCurrentlyActive) {
                View item = holder.itemView;
                float baseline = item.getTop() + item.getHeight() / 2f + label.getTextSize() / 3f;
                if (dX < 0) {
                    canvas.drawRect(item.getRight() + dX, item.getTop(), item.getRight(), item.getBottom(), deletePaint);
                    label.setColor(ContextCompat.getColor(context, R.color.alert));
                    label.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText("Delete", item.getRight() - margin, baseline, label);
                } else if (dX > 0) {
                    canvas.drawRect(item.getLeft(), item.getTop(), item.getLeft() + dX, item.getBottom(), openPaint);
                    label.setColor(ContextCompat.getColor(context, R.color.on_primary_container));
                    label.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText("Details", item.getLeft() + margin, baseline, label);
                }
                super.onChildDraw(canvas, rv, holder, dX, dY, actionState, isCurrentlyActive);
            }
        }).attachToRecyclerView(recyclerView);
    }
}
