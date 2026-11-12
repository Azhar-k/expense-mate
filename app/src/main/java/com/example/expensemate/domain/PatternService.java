package com.example.expensemate.domain;

import androidx.lifecycle.LiveData;

import com.example.expensemate.data.MessagePattern;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The patterns that read bank messages. They are tried in order and the first match wins. */
public interface PatternService {
    LiveData<List<MessagePattern>> patterns();

    /** The patterns that are on, in order. Blocks, so do not call it from the main thread. */
    List<MessagePattern> active();

    /** A new pattern goes first, so it wins over the shipped ones and the general fallbacks. */
    CompletableFuture<Void> add(MessagePattern pattern);

    CompletableFuture<Void> update(MessagePattern pattern);

    /** Numbers the patterns in the order given. */
    CompletableFuture<Void> reorder(List<Long> ids);

    CompletableFuture<Void> delete(MessagePattern pattern);
}
