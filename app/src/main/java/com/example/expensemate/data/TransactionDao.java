package com.example.expensemate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.Date;
import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSync();

    @Query("SELECT * FROM transactions WHERE transactionType = 'DEBIT' ORDER BY date DESC")
    LiveData<List<Transaction>> getDebitTransactions();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(Transaction transaction);

    @Delete
    void deleteTransaction(Transaction transaction);

    @Query("SELECT * FROM transactions WHERE accountNumber = :accountNumber ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsByAccount(String accountNumber);

    @Query("UPDATE transactions SET amount = :amount, description = :description, date = :date, " +
           "accountNumber = :accountNumber, accountType = :accountType, transactionType = :transactionType, " +
           "receiverName = :receiverName, smsBody = :smsBody, smsSender = :smsSender WHERE id = :id")
    void updateTransaction(long id, double amount, String description, Date date, String accountNumber,
                         String accountType, String transactionType, String receiverName,
                         String smsBody, String smsSender);
} 