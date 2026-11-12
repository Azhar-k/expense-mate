package com.example.expensemate.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.expensemate.AppServices;
import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.domain.PatternService;

import java.util.List;

public class PatternViewModel extends AndroidViewModel {
    private static final String TAG = "PatternViewModel";
    private final PatternService patterns;

    public PatternViewModel(Application application) {
        super(application);
        patterns = AppServices.get(application).patterns();
    }

    public LiveData<List<MessagePattern>> getPatterns() {
        return patterns.patterns();
    }

    public void add(MessagePattern pattern) {
        Writes.log(TAG, "adding pattern", patterns.add(pattern));
    }

    public void update(MessagePattern pattern) {
        Writes.log(TAG, "updating pattern", patterns.update(pattern));
    }

    public void saveOrder(List<Long> ids) {
        Writes.log(TAG, "reordering patterns", patterns.reorder(ids));
    }

    public void delete(MessagePattern pattern) {
        Writes.log(TAG, "deleting pattern", patterns.delete(pattern));
    }
}
