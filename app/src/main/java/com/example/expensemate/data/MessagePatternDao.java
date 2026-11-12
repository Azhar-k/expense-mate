package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MessagePatternDao {
    @Query("SELECT * FROM message_patterns ORDER BY sortOrder ASC, id ASC")
    LiveData<List<MessagePattern>> observeAll();

    @Query("SELECT * FROM message_patterns ORDER BY sortOrder ASC, id ASC")
    List<MessagePattern> getAllSync();

    @Query("SELECT * FROM message_patterns WHERE active = 1 ORDER BY sortOrder ASC, id ASC")
    List<MessagePattern> getActiveSync();

    @Query("SELECT COALESCE(MIN(sortOrder), 0) FROM message_patterns")
    int firstSortOrder();

    @Query("UPDATE message_patterns SET sortOrder = :sortOrder WHERE id = :id")
    void setSortOrder(long id, int sortOrder);

    @Insert
    long insert(MessagePattern pattern);

    @Update
    void update(MessagePattern pattern);

    @Delete
    void delete(MessagePattern pattern);
}
