package com.example.expensemate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.expensemate.AppServices;
import com.example.expensemate.data.Budget;
import com.example.expensemate.data.BudgetProgress;
import com.example.expensemate.domain.BudgetService;

import java.util.Calendar;
import java.util.List;

/** Budgets for one month at a time, the current one to start with. */
public class BudgetViewModel extends AndroidViewModel {
    private static final String TAG = "BudgetViewModel";
    private final BudgetService budgets;
    private final MutableLiveData<Long> month = new MutableLiveData<>(System.currentTimeMillis());
    private final LiveData<List<BudgetProgress>> progress;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        budgets = AppServices.get(application).budgets();
        progress = Transformations.switchMap(month, budgets::progress);
    }

    public LiveData<List<BudgetProgress>> getProgress() {
        return progress;
    }

    public LiveData<Long> getMonth() {
        return month;
    }

    /** Moves the month shown by this many months; never past the current one. */
    public void shiftMonth(int months) {
        Calendar shown = Calendar.getInstance();
        shown.setTimeInMillis(month.getValue());
        shown.add(Calendar.MONTH, months);
        if (shown.getTimeInMillis() > System.currentTimeMillis()) {
            shown.setTimeInMillis(System.currentTimeMillis());
        }
        month.setValue(shown.getTimeInMillis());
    }

    public boolean isCurrentMonth() {
        Calendar shown = Calendar.getInstance();
        shown.setTimeInMillis(month.getValue());
        Calendar now = Calendar.getInstance();
        return shown.get(Calendar.YEAR) == now.get(Calendar.YEAR) && shown.get(Calendar.MONTH) == now.get(Calendar.MONTH);
    }

    public void add(Budget budget, List<String> categories) {
        Writes.log(TAG, "adding budget", budgets.add(budget, categories));
    }

    public void update(Budget budget, List<String> categories) {
        Writes.log(TAG, "updating budget", budgets.update(budget, categories));
    }

    public void delete(Budget budget) {
        Writes.log(TAG, "deleting budget", budgets.delete(budget));
    }

    public void saveOrder(List<Long> ids) {
        Writes.log(TAG, "reordering budgets", budgets.reorder(ids));
    }
}
