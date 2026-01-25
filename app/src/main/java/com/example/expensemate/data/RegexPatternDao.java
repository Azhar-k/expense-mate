package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RegexPatternDao {
    @Query("SELECT * FROM regex_patterns ORDER BY id ASC")
    LiveData<List<RegexPattern>> getAllPatterns();

    @Query("SELECT * FROM regex_patterns")
    List<RegexPattern> getAllPatternsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RegexPattern pattern);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RegexPattern> patterns);

    @Update
    void update(RegexPattern pattern);

    @Delete
    void delete(RegexPattern pattern);

    @Query("SELECT COUNT(*) FROM regex_patterns")
    int getCount();
}
