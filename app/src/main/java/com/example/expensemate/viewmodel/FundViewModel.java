package com.example.expensemate.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import com.example.expensemate.AppServices;
import com.example.expensemate.data.Balance;
import com.example.expensemate.data.Fund;
import com.example.expensemate.domain.FundService;
import java.util.List;

/** What money is for. Accounts, where it is, are in {@link AccountViewModel}. */
public class FundViewModel extends AndroidViewModel {
    private static final String TAG = "FundViewModel";
    private final FundService funds;

    public FundViewModel(Application application) {
        super(application);
        funds = AppServices.get(application).funds();
    }

    public LiveData<List<Fund>> getAllFunds() {
        return funds.funds();
    }

    /** Everyday money, the fund budgets keep track of. */
    public LiveData<Fund> getDefaultFund() {
        return funds.defaultFund();
    }

    /** One per fund and currency, keyed by fund id. */
    public LiveData<List<Balance>> getBalances() {
        return funds.balances();
    }

    public void setDefaultFund(Fund fund) {
        Writes.log(TAG, "changing default fund", funds.makeDefault(fund));
    }

    public void insert(Fund fund) {
        Writes.log(TAG, "adding fund", funds.add(fund));
    }

    public void update(Fund fund) {
        Writes.log(TAG, "updating fund", funds.update(fund));
    }

    public void saveOrder(List<Long> ids) {
        Writes.log(TAG, "reordering funds", funds.reorder(ids));
    }

    public void delete(Fund fund) {
        Writes.log(TAG, "deleting fund", funds.delete(fund));
    }
}
