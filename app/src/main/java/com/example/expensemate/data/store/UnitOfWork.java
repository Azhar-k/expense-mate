package com.example.expensemate.data.store;

import java.util.concurrent.Callable;

/** Runs several store calls as one all-or-nothing change. */
public interface UnitOfWork {
    void run(Runnable work);

    <T> T call(Callable<T> work);
}
