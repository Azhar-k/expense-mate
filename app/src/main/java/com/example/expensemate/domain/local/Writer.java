package com.example.expensemate.domain.local;

import com.example.expensemate.data.store.UnitOfWork;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Runs each write off the caller's thread as one unit of work, so it lands whole or not at all. */
final class Writer {
    private final UnitOfWork unitOfWork;
    private final Executor executor;

    Writer(UnitOfWork unitOfWork, Executor executor) {
        this.unitOfWork = unitOfWork;
        this.executor = executor;
    }

    CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(() -> unitOfWork.run(work), executor);
    }

    <T> CompletableFuture<T> call(Supplier<T> work) {
        return CompletableFuture.supplyAsync(() -> unitOfWork.call(work::get), executor);
    }
}
