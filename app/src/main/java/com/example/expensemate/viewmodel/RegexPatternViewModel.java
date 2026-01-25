package com.example.expensemate.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.expensemate.data.AppDatabase;
import com.example.expensemate.data.RegexPattern;
import com.example.expensemate.data.RegexPatternDao;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegexPatternViewModel extends AndroidViewModel {
    private final RegexPatternDao regexPatternDao;
    private final ExecutorService executorService;

    public RegexPatternViewModel(Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        regexPatternDao = database.regexPatternDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<RegexPattern>> getAllPatterns() {
        return regexPatternDao.getAllPatterns();
    }

    public void insert(RegexPattern pattern) {
        executorService.execute(() -> regexPatternDao.insert(pattern));
    }

    public void update(RegexPattern pattern) {
        executorService.execute(() -> regexPatternDao.update(pattern));
    }

    public void delete(RegexPattern pattern) {
        executorService.execute(() -> regexPatternDao.delete(pattern));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
