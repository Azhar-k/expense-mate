package com.example.expensemate.domain;

import com.example.expensemate.data.MessagePattern;
import com.example.expensemate.data.Transaction;

import java.util.List;

/** Records transactions read from messages. Parsing stays on the phone; this decides what gets saved. */
public interface CaptureService {
    /** The patterns to read messages with, in order. Blocks, so do not call it from the main thread. */
    List<MessagePattern> patterns();

    /**
     * Saves a message-derived transaction unless it is already recorded. Blocks until done,
     * so do not call it from the main thread.
     *
     * @return true if a row was saved
     */
    boolean recordIfNew(Transaction parsed);
}
